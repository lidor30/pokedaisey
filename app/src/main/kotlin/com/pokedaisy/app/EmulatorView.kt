package com.pokedaisy.app

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Draws the mGBA RGBA framebuffer as an aspect-fit (or, with [stretch], view-filling)
 * nearest-filtered quad, optionally through [gbaColors] and a screen effect.
 * Continuous render mode; the emu thread writes the shared buffer concurrently.
 */
class EmulatorView(context: Context) : GLSurfaceView(context) {

    private val renderer = FrameRenderer()

    /** Polled by the GL thread each frame: while true the screen keeps its last frame. */
    var holdFrame: () -> Boolean
        get() = renderer.holdFrame
        set(v) { renderer.holdFrame = v }

    /** Fill the whole view instead of keeping the GBA's 3:2 ([Prefs.stretchGame]). */
    var stretch: Boolean = false
        set(v) {
            if (field == v) return
            field = v
            renderer.setStretch(v)
        }

    /** The GBA LCD's colours ([Prefs.gbaColors], [ScreenShaders.GBA_COLOR]). */
    var gbaColors: Boolean = false
        set(v) {
            if (field == v) return
            field = v
            renderer.setGbaColors(v)
        }

    /** SHADERS > FILTER's effect ([Prefs.screenFilter], [ScreenShaders.effectFor]); null = none. */
    internal var screenEffect: ScreenShaders.Effect? = null
        set(v) {
            if (field == v) return
            field = v
            renderer.setEffect(v)
        }

    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    // GLSurfaceView composites via its own hardware layer, "punching a hole"
    // in the window - the existing plain-View overlays (touch controls, HUD)
    // coexist with that fine, but a ComposeView sibling (PokeDaisyActivity's
    // debug-only companion-UI mirror) disrupted it, rendering this view
    // blank. setZOrderMediaOverlay is the standard fix for "SurfaceView with
    // overlay UI on top of it" - called from there (only when the mirror is
    // actually enabled) rather than unconditionally here.

    // The frame buffer is handed to the renderer under its lock, not through
    // queueEvent: SWAP SCREENS moves this view between the activity's window and
    // the second screen's, and a detached GLSurfaceView's queue belongs to a GL
    // thread that has already exited - a bind or unbind sent then was lost (and
    // the next GL thread could read a freed buffer). See PokeDaisyActivity.arrangeScreens.

    /** Called once after the core is up. */
    fun bindCore(buffer: ByteBuffer, width: Int, height: Int) {
        renderer.bind(buffer, width, height)
    }

    /**
     * Drops the renderer's reference to the current framebuffer and blocks
     * (briefly, bounded) until the GL thread has actually applied that —
     * call this BEFORE tearing down/restarting the emulator core. Render
     * mode is continuous, so the GL thread reads [buffer] on its own clock;
     * freeing the native memory it points at (core restart tears down and
     * re-`pkInit`s) without this first is a use-after-free race — the GL
     * thread can still be mid-`glTexSubImage2D` on the old, now-freed
     * buffer, which crashes the whole process (seen as the app abruptly
     * closing back to ROM selection when "Restart Game" was added).
     */
    fun unbindCoreBlocking() {
        // Takes the lock a frame's upload holds: once this returns, no upload is reading it.
        renderer.unbind()
    }

    /**
     * Draws the frame in up to three passes: the GBA colour correction ([gbaColors]) at the
     * GBA's own size, a prescaled screen effect ([setEffect]) at the largest whole multiple of
     * it that fits, then the result onto the view - nearest-filtered, or smoothly once an
     * effect has scaled it up (from a whole multiple, so its grid stays even). An effect that
     * isn't prescaled is that last pass itself. With neither, it's the one plain pass it always was.
     *
     * Every texture keeps the frame's own row order (top row first), so `vUv.y` runs down the
     * game in every shader; only the pass onto the view flips it to GL's bottom-up.
     */
    private class FrameRenderer : Renderer {
        private var buffer: ByteBuffer? = null
        private var texW = 0
        private var texH = 0
        private var surfaceW = 0
        private var surfaceH = 0
        private var dirtyGeometry = true
        private var stretch = false
        private var gbaColors = false
        private var effect: ScreenShaders.Effect? = null

        private var plain: Program? = null
        private var colorProgram: Program? = null
        private var effectProgram: Program? = null
        private var effectProgramFor: ScreenShaders.Effect? = null
        private var texId = 0
        private var texAllocated = false
        private var colorTarget = RenderTarget()
        private var effectTarget = RenderTarget()
        @Volatile var holdFrame: () -> Boolean = { false }

        /** Onto the view: textures start with the game's top row, the view with its bottom one. */
        private val viewUv: FloatBuffer = floats(
            0f, 1f,
            1f, 1f,
            0f, 0f,
            1f, 0f,
        )
        /** Into a pass's texture: kept in the input's order. */
        private val passUv: FloatBuffer = floats(
            0f, 0f,
            1f, 0f,
            0f, 1f,
            1f, 1f,
        )
        private val fullQuad: FloatBuffer = floats(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
        private var pos: FloatBuffer = fullQuad
        /** The game's size on the view, in pixels. */
        private var quadW = 0f
        private var quadH = 0f

        /** Guards [buffer] and its size: held by [bind] / [unbind] and by a frame's upload. */
        private val lock = Any()

        fun bind(buf: ByteBuffer, w: Int, h: Int) = synchronized(lock) {
            buffer = buf.also { it.order(ByteOrder.nativeOrder()) }
            texW = w
            texH = h
            texAllocated = false
            dirtyGeometry = true
        }

        fun setStretch(on: Boolean) = synchronized(lock) {
            stretch = on
            dirtyGeometry = true
        }

        fun setGbaColors(on: Boolean) = synchronized(lock) { gbaColors = on }

        fun setEffect(e: ScreenShaders.Effect?) = synchronized(lock) { effect = e }

        /** Drops the buffer reference; [onDrawFrame] just clears until the next [bind]. */
        fun unbind() = synchronized(lock) {
            buffer = null
        }

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) = synchronized(lock) {
            // A new context: everything made in the old one is gone with it.
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            plain = Program(ScreenShaders.PLAIN)
            colorProgram = Program(ScreenShaders.GBA_COLOR)
            effectProgram = null
            effectProgramFor = null
            colorTarget = RenderTarget()
            effectTarget = RenderTarget()
            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            texId = ids[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            texAllocated = false
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) = synchronized(lock) {
            surfaceW = width
            surfaceH = height
            dirtyGeometry = true
        }

        override fun onDrawFrame(gl: GL10?) = synchronized(lock) { draw() }

        private fun draw() {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            GLES20.glViewport(0, 0, surfaceW, surfaceH)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            val buf = buffer ?: return
            val plain = plain ?: return
            if (texW == 0 || surfaceW == 0) return

            if (dirtyGeometry) {
                recomputeQuad()
                dirtyGeometry = false
            }

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
            buf.position(0)
            if (texAllocated && holdFrame()) {
                // Keep the texture as is: the last frame shown stays up.
            } else if (!texAllocated) {
                GLES20.glTexImage2D(
                    GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, texW, texH, 0,
                    GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf,
                )
                texAllocated = true
            } else {
                GLES20.glTexSubImage2D(
                    GLES20.GL_TEXTURE_2D, 0, 0, 0, texW, texH,
                    GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf,
                )
            }

            var input = texId
            var last = plain
            var smooth = false
            val color = colorProgram
            if (gbaColors && color != null && colorTarget.ensure(texW, texH)) {
                pass(color, input, colorTarget)
                input = colorTarget.tex
            }
            val fx = effectProgram()
            if (fx != null && effect?.prescale == true) {
                // The largest whole multiple that fits, per axis (STRETCH scales them apart).
                val kx = maxOf(1, (quadW / texW).toInt())
                val ky = maxOf(1, (quadH / texH).toInt())
                if (effectTarget.ensure(texW * kx, texH * ky)) {
                    pass(fx, input, effectTarget)
                    input = effectTarget.tex
                    smooth = true
                }
            } else if (fx != null) {
                last = fx
            }

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            GLES20.glViewport(0, 0, surfaceW, surfaceH)
            drawQuad(last, input, viewUv, pos, smooth)
        }

        /** Draws [input] through [program] into all of [target]. */
        private fun pass(program: Program, input: Int, target: RenderTarget) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, target.fbo)
            GLES20.glViewport(0, 0, target.w, target.h)
            drawQuad(program, input, passUv, fullQuad, smooth = false)
        }

        private fun drawQuad(program: Program, input: Int, uv: FloatBuffer, quad: FloatBuffer, smooth: Boolean) {
            val filter = if (smooth) GLES20.GL_LINEAR else GLES20.GL_NEAREST
            GLES20.glUseProgram(program.id)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, input)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, filter)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, filter)
            GLES20.glUniform1i(program.uTex, 0)
            GLES20.glUniform2f(program.uTexSize, texW.toFloat(), texH.toFloat())

            GLES20.glEnableVertexAttribArray(program.aPos)
            GLES20.glVertexAttribPointer(program.aPos, 2, GLES20.GL_FLOAT, false, 0, quad)
            GLES20.glEnableVertexAttribArray(program.aUv)
            GLES20.glVertexAttribPointer(program.aUv, 2, GLES20.GL_FLOAT, false, 0, uv)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisableVertexAttribArray(program.aPos)
            GLES20.glDisableVertexAttribArray(program.aUv)
        }

        /** The effect's program, (re)built on the GL thread when [setEffect] changed it. */
        private fun effectProgram(): Program? {
            if (effect !== effectProgramFor) {
                effectProgram?.let { GLES20.glDeleteProgram(it.id) }
                effectProgram = effect?.let { Program(it.frag) }
                effectProgramFor = effect
            }
            return effectProgram
        }

        private fun recomputeQuad() {
            val texAspect = texW.toFloat() / texH
            val surfAspect = surfaceW.toFloat() / surfaceH
            var sx = 1f
            var sy = 1f
            if (stretch) {
                // Fill the view; the layout already sized it.
            } else if (surfAspect > texAspect) {
                sx = texAspect / surfAspect   // pillarbox
            } else {
                sy = surfAspect / texAspect   // letterbox
            }
            pos = floats(-sx, -sy, sx, -sy, -sx, sy, sx, sy)
            quadW = sx * surfaceW
            quadH = sy * surfaceH
        }

        /** A linked shader program over the shared quad ([VERT] + a [ScreenShaders] fragment shader). */
        private class Program(frag: String) {
            val id = buildProgram(VERT, frag)
            val aPos = GLES20.glGetAttribLocation(id, "aPos")
            val aUv = GLES20.glGetAttribLocation(id, "aUv")
            val uTex = GLES20.glGetUniformLocation(id, "uTex")
            val uTexSize = GLES20.glGetUniformLocation(id, "uTexSize")
        }

        /** An offscreen texture a pass draws into; lives and dies with the GL context. */
        private class RenderTarget {
            var fbo = 0
            var tex = 0
            var w = 0
            var h = 0
            private var unsupported = false

            /** Sizes it to [width] x [height]; false if the GPU can't draw into it (the pass is then skipped). */
            fun ensure(width: Int, height: Int): Boolean {
                if (unsupported) return false
                if (w == width && h == height) return true
                if (fbo == 0) {
                    val ids = IntArray(1)
                    GLES20.glGenTextures(1, ids, 0)
                    tex = ids[0]
                    GLES20.glGenFramebuffers(1, ids, 0)
                    fbo = ids[0]
                }
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
                GLES20.glTexImage2D(
                    GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0,
                    GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null,
                )
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
                GLES20.glFramebufferTexture2D(
                    GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, tex, 0,
                )
                val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
                if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
                    Log.w("pokedaisy", "screen filter: framebuffer incomplete (0x${status.toString(16)}), filter off")
                    unsupported = true
                    return false
                }
                w = width
                h = height
                return true
            }
        }

        companion object {
            private val VERT = """
                attribute vec2 aPos;
                attribute vec2 aUv;
                varying vec2 vUv;
                void main() {
                    vUv = aUv;
                    gl_Position = vec4(aPos, 0.0, 1.0);
                }
            """.trimIndent()

            private fun floats(vararg v: Float): FloatBuffer =
                ByteBuffer.allocateDirect(v.size * 4).order(ByteOrder.nativeOrder())
                    .asFloatBuffer().apply { put(v); position(0) }

            private fun buildProgram(vsrc: String, fsrc: String): Int {
                val vs = compile(GLES20.GL_VERTEX_SHADER, vsrc)
                val fs = compile(GLES20.GL_FRAGMENT_SHADER, fsrc)
                val p = GLES20.glCreateProgram()
                GLES20.glAttachShader(p, vs)
                GLES20.glAttachShader(p, fs)
                GLES20.glLinkProgram(p)
                val status = IntArray(1)
                GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, status, 0)
                check(status[0] != 0) { "program link failed: " + GLES20.glGetProgramInfoLog(p) }
                return p
            }

            private fun compile(type: Int, src: String): Int {
                val s = GLES20.glCreateShader(type)
                GLES20.glShaderSource(s, src)
                GLES20.glCompileShader(s)
                val status = IntArray(1)
                GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, status, 0)
                check(status[0] != 0) { "shader compile failed: " + GLES20.glGetShaderInfoLog(s) }
                return s
            }
        }
    }
}

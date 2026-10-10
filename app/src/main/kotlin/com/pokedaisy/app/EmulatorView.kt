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
 * Continuous render mode. The GL thread never reads the core's own buffer, which the emu
 * thread draws into line by line: each finished frame is copied out ([publishFrame]) and
 * that copy is what's uploaded. Uploading the live buffer showed a frame half drawn - the
 * top of the new one over the bottom of the old - a tear across the screen while scrolling.
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
            reportGamePixel()
        }

    /** Called (UI thread) with one GBA pixel's height on screen, in view px, whenever it changes -
     * SHADERS on the companion draw their grid at that size ([CompanionColors]). */
    var onGamePixel: ((Float) -> Unit)? = null
    private var frameW = 0
    private var frameH = 0

    private fun reportGamePixel() {
        if (frameW == 0 || width == 0 || height == 0) return
        // As FrameRenderer.recomputeQuad: the game's height on screen, letterboxed or stretched.
        val h = if (stretch) height.toFloat() else minOf(height.toFloat(), width * frameH.toFloat() / frameW)
        onGamePixel?.invoke(h / frameH)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        reportGamePixel()
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

    /** The effect's `uGrid` (SHADERS > GRID, [ScreenShaders.gridFor]): a uniform, so no rebuild. */
    var screenGrid: FloatArray = floatArrayOf(0f, 0f)
        set(v) {
            field = v
            renderer.setGrid(v[0], v[1])
        }

    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    override fun surfaceCreated(holder: android.view.SurfaceHolder) {
        super.surfaceCreated(holder)
        // The game makes 60 frames a second: ask for a 60 Hz (or a multiple) refresh for this surface. A
        // 120 Hz screen then can drop to 60 while the game is up - half the draws and composition (heat) -
        // and every refresh shows a new frame. A hint: the system may keep its rate.
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            runCatching { holder.surface.setFrameRate(60f, android.view.Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE) }
        }
    }

    // The window reports its first draw (and drops Android 12's splash screen) only once every
    // surfaceRedrawNeededAsync it asked for has called back. GLSurfaceView keeps just the latest
    // finishDrawing: a view resized twice before its first frame (STATUS BAR over a portrait
    // game: laid out before the bar had a height, then again with it) lost the first one, and the
    // splash stayed over the game. Every pending one runs with the next frame instead.
    private val pendingRedraws = ArrayList<Runnable>()

    override fun surfaceRedrawNeededAsync(holder: android.view.SurfaceHolder, finishDrawing: Runnable) {
        synchronized(pendingRedraws) { pendingRedraws += finishDrawing }
        super.surfaceRedrawNeededAsync(holder) {
            val all = synchronized(pendingRedraws) { pendingRedraws.toList().also { pendingRedraws.clear() } }
            all.forEach(Runnable::run)
        }
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
        frameW = width
        frameH = height
        post { reportGamePixel() }
    }

    /** Emu thread, right after a frame has run: hands the finished frame to the GL thread. */
    fun publishFrame() = renderer.publish()

    /**
     * GL thread, once per draw: in RENDERMODE_CONTINUOUSLY the previous swap has just
     * returned, so this ticks with the refresh of the display the game is on - what
     * EmulatorEngine paces 1x frames to ([EmulatorEngine.onVsync]).
     */
    var onVsync: (() -> Long)?
        get() = renderer.onVsync
        set(v) { renderer.onVsync = v }

    /** New game frames put on screen so far, and refreshes whose due frame came too late to wait for
     * (it showed a refresh later) - for FPS / CPU's performance panel. */
    val framesShown: Long get() = renderer.framesShown
    val lateFrames: Long get() = renderer.lateFrames

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
        /** The core's own frame buffer, read only by [publish] (the emu thread). */
        private var buffer: ByteBuffer? = null
        /** The last finished frame, copied out of [buffer]; what the GL thread uploads. */
        private var ready: ByteBuffer? = null
        /** [ready] holds a frame the texture doesn't have yet. */
        private var fresh = false
        private var texW = 0
        private var texH = 0
        private var surfaceW = 0
        private var surfaceH = 0
        private var dirtyGeometry = true
        private var stretch = false
        private var gbaColors = false
        private var effect: ScreenShaders.Effect? = null
        private var gridX = 0f
        private var gridY = 0f

        private var plain: Program? = null
        private var colorProgram: Program? = null
        private var effectProgram: Program? = null
        private var effectProgramFor: ScreenShaders.Effect? = null
        private var texId = 0
        private var texAllocated = false
        private var colorTarget = RenderTarget()
        private var effectTarget = RenderTarget()
        @Volatile var holdFrame: () -> Boolean = { false }
        @Volatile var framesShown = 0L
        @Volatile var lateFrames = 0L

        /** Ticks the engine; returns how long this draw may wait for the game frame that tick starts (0 = none due). */
        @Volatile var onVsync: (() -> Long)? = null

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

        /** Guards [buffer] and its size: held by [bind] / [unbind] and by a frame's draw. */
        private val lock = Any()
        /** Guards [ready] / [fresh] only, so a copy never waits on a whole draw (taken after [lock]). */
        private val frameLock = Any()

        fun bind(buf: ByteBuffer, w: Int, h: Int) = synchronized(lock) {
            synchronized(frameLock) {
                buffer = buf.also { it.order(ByteOrder.nativeOrder()) }
                // A new one each time: a restarted core mustn't show the last game's frame first.
                ready = ByteBuffer.allocateDirect(buf.capacity()).order(ByteOrder.nativeOrder())
                fresh = false
            }
            texW = w
            texH = h
            texAllocated = false
            dirtyGeometry = true
        }

        /** Emu thread, between frames (so [buffer] is whole): copies it into [ready]. */
        fun publish() = synchronized(frameLock) {
            val src = buffer ?: return
            val dst = ready ?: return
            src.position(0)
            dst.position(0)
            dst.put(src)
            fresh = true
            (frameLock as Object).notifyAll()
        }

        /** GL thread: the frame this refresh started is on its way - wait for it (at most [nanos]), so it
         * shows on this refresh. Drawn without waiting, a frame finishing just after the draw showed one
         * refresh late and the next one on time: two frames on one refresh, then one held for two. */
        private fun awaitFresh(nanos: Long) = synchronized(frameLock) {
            val end = System.nanoTime() + nanos
            while (!fresh) {
                val left = end - System.nanoTime()
                if (left <= 0) { lateFrames++; break }
                (frameLock as Object).wait(left / 1_000_000L, (left % 1_000_000L).toInt())
            }
        }

        fun setStretch(on: Boolean) = synchronized(lock) {
            stretch = on
            dirtyGeometry = true
        }

        fun setGbaColors(on: Boolean) = synchronized(lock) { gbaColors = on }

        fun setEffect(e: ScreenShaders.Effect?) = synchronized(lock) { effect = e }

        fun setGrid(x: Float, y: Float) = synchronized(lock) { gridX = x; gridY = y }

        /** Drops the buffer reference; [onDrawFrame] just clears until the next [bind]. */
        fun unbind() = synchronized(lock) {
            synchronized(frameLock) {
                buffer = null
                fresh = false
            }
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

        override fun onDrawFrame(gl: GL10?) {
            val wait = onVsync?.invoke() ?: 0L
            if (wait > 0) awaitFresh(wait)
            synchronized(lock) { draw() }
        }

        private fun draw() {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            GLES20.glViewport(0, 0, surfaceW, surfaceH)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            if (buffer == null) return
            val plain = plain ?: return
            if (texW == 0 || surfaceW == 0) return

            if (dirtyGeometry) {
                recomputeQuad()
                dirtyGeometry = false
            }

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
            // glTex(Sub)Image2D has taken the pixels by the time it returns, so frameLock
            // is held only for the copy into GL, not the passes below.
            synchronized(frameLock) {
                val buf = ready ?: return  // set with buffer by bind
                if (!texAllocated) {
                    // The last frame published (a new GL context after SWAP SCREENS has none yet).
                    buf.position(0)
                    GLES20.glTexImage2D(
                        GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, texW, texH, 0,
                        GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf,
                    )
                    texAllocated = true
                    fresh = false
                } else if (fresh && !holdFrame()) {
                    // A held frame keeps the texture as is: the last frame shown stays up.
                    buf.position(0)
                    GLES20.glTexSubImage2D(
                        GLES20.GL_TEXTURE_2D, 0, 0, 0, texW, texH,
                        GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf,
                    )
                    fresh = false
                    framesShown++
                }
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
            GLES20.glUniform2f(program.uGrid, gridX, gridY)

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
            val uGrid = GLES20.glGetUniformLocation(id, "uGrid")
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

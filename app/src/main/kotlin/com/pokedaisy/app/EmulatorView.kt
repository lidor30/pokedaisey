package com.pokedaisy.app

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Draws the mGBA RGBA framebuffer as an aspect-fit (or, with [stretch], view-filling)
 * nearest-filtered quad.
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
            queueEvent { renderer.setStretch(v) }
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

    /** Called once after the core is up. */
    fun bindCore(buffer: ByteBuffer, width: Int, height: Int) {
        queueEvent { renderer.bind(buffer, width, height) }
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
        val latch = CountDownLatch(1)
        queueEvent { renderer.unbind(); latch.countDown() }
        runCatching { latch.await(500, TimeUnit.MILLISECONDS) }
    }

    private class FrameRenderer : Renderer {
        private var buffer: ByteBuffer? = null
        private var texW = 0
        private var texH = 0
        private var surfaceW = 0
        private var surfaceH = 0
        private var dirtyGeometry = true
        private var stretch = false

        private var program = 0
        private var texId = 0
        private var aPos = 0
        private var aUv = 0
        private var uTex = 0
        private var texAllocated = false
        @Volatile var holdFrame: () -> Boolean = { false }

        private val uv: FloatBuffer = floats(
            0f, 1f,
            1f, 1f,
            0f, 0f,
            1f, 0f,
        )
        private var pos: FloatBuffer = floats(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)

        fun bind(buf: ByteBuffer, w: Int, h: Int) {
            buffer = buf.also { it.order(ByteOrder.nativeOrder()) }
            texW = w
            texH = h
            texAllocated = false
            dirtyGeometry = true
        }

        fun setStretch(on: Boolean) {
            stretch = on
            dirtyGeometry = true
        }

        /** Drops the buffer reference; [onDrawFrame] just clears until the next [bind]. */
        fun unbind() {
            buffer = null
        }

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            program = buildProgram(VERT, FRAG)
            aPos = GLES20.glGetAttribLocation(program, "aPos")
            aUv = GLES20.glGetAttribLocation(program, "aUv")
            uTex = GLES20.glGetUniformLocation(program, "uTex")
            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            texId = ids[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            texAllocated = false
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            surfaceW = width
            surfaceH = height
            GLES20.glViewport(0, 0, width, height)
            dirtyGeometry = true
        }

        override fun onDrawFrame(gl: GL10?) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            val buf = buffer ?: return
            if (texW == 0 || surfaceW == 0) return

            if (dirtyGeometry) {
                recomputeQuad()
                dirtyGeometry = false
            }

            GLES20.glUseProgram(program)
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
            GLES20.glUniform1i(uTex, 0)

            GLES20.glEnableVertexAttribArray(aPos)
            GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, pos)
            GLES20.glEnableVertexAttribArray(aUv)
            GLES20.glVertexAttribPointer(aUv, 2, GLES20.GL_FLOAT, false, 0, uv)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisableVertexAttribArray(aPos)
            GLES20.glDisableVertexAttribArray(aUv)
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

            private val FRAG = """
                precision mediump float;
                varying vec2 vUv;
                uniform sampler2D uTex;
                void main() {
                    gl_FragColor = vec4(texture2D(uTex, vUv).rgb, 1.0);
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

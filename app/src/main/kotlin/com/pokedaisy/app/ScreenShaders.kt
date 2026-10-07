package com.pokedaisy.app

import com.pokedaisy.app.companion.ScreenFilter

/**
 * Fragment shaders for [EmulatorView]'s passes. Each reads `uTex` at `vUv`; `uTexSize` is
 * the GBA frame's size in pixels, so `vUv * uTexSize` is the position in GBA pixels (y
 * running down the game's rows, like the frame's own).
 */
internal object ScreenShaders {

    /** A screen effect ([ScreenFilter]): [prescale] draws it at a whole multiple of the GBA's
     * size and scales that smoothly onto the view (hard-edged grids and lines, which would come
     * out uneven at the Thor's 6.75x); without it, it draws at the view's own pixels. */
    class Effect(val frag: String, val prescale: Boolean)

    fun effectFor(filter: ScreenFilter): Effect? = when (filter) {
        ScreenFilter.NONE -> null
        ScreenFilter.LCD -> LCD
        ScreenFilter.SCANLINES -> SCANLINES
        ScreenFilter.CRT -> CRT
    }

    private const val HEADER = """
        #ifdef GL_FRAGMENT_PRECISION_HIGH
        precision highp float;
        #else
        precision mediump float;
        #endif
        varying vec2 vUv;
        uniform sampler2D uTex;
        uniform vec2 uTexSize;
    """

    /** The texture as is. */
    val PLAIN = """
        $HEADER
        void main() {
            gl_FragColor = vec4(texture2D(uTex, vUv).rgb, 1.0);
        }
    """.trimIndent()

    /**
     * SHADERS > GBA COLORS ([Prefs.gbaColors]): the colours as the GBA's own LCD showed
     * them - darker, less saturated, with the colour bleed the game's palettes were made for.
     * mGBA's `res/shaders/gba-color.shader` (Pokefan531 and hunterk, MPL-2.0 like the rest
     * of third_party/mgba) at its defaults (darken_screen 0.5, sat / contrast 1, lum 0.99),
     * with its identity saturation and contrast steps folded away.
     */
    val GBA_COLOR = """
        $HEADER
        const float darken = 0.5;
        const float targetGamma = 2.2;
        const float displayGamma = 2.5;
        const float lum = 0.99;
        const mat3 lcd = mat3(
            0.84, 0.09, 0.15,
            0.18, 0.67, 0.10,
            0.00, 0.26, 0.73);
        void main() {
            vec3 c = pow(texture2D(uTex, vUv).rgb, vec3(targetGamma + darken));
            c = lcd * clamp(c * lum, 0.0, 1.0);
            gl_FragColor = vec4(pow(c, vec3(1.0 / displayGamma + darken * 0.125)), 1.0);
        }
    """.trimIndent()

    /**
     * LCD: a dark grid line along the top and left third of every GBA pixel. mGBA's
     * `res/shaders/lcd.shader` (Copyright (C) 2017 Dominus Iniquitatis, MIT - see NOTICE),
     * a little darker than its 0.9 default so the grid reads at handheld distance.
     */
    private val LCD = Effect(
        """
        $HEADER
        const float boundBrightness = 0.8;
        void main() {
            vec3 c = texture2D(uTex, vUv).rgb;
            vec2 sub = vUv * uTexSize * 3.0;
            if (int(mod(sub.x, 3.0)) == 0 || int(mod(sub.y, 3.0)) == 0) c *= boundBrightness;
            gl_FragColor = vec4(c, 1.0);
        }
        """.trimIndent(),
        prescale = true,
    )

    /**
     * SCANLINES: the top half of every GBA row darkened. mGBA's `res/shaders/scanlines.shader`
     * (Copyright (C) 2017 Dominus Iniquitatis, MIT - see NOTICE) at its 0.5 default.
     */
    private val SCANLINES = Effect(
        """
        $HEADER
        const float lineBrightness = 0.5;
        void main() {
            vec3 c = texture2D(uTex, vUv).rgb;
            if (int(mod(vUv.y * uTexSize.y * 2.0, 2.0)) == 0) c *= lineBrightness;
            gl_FragColor = vec4(c, 1.0);
        }
        """.trimIndent(),
        prescale = true,
    )

    /**
     * CRT: each GBA row a horizontal beam with a soft gaussian profile that widens as it gets
     * brighter (so bright rows bloom into the gaps), neighbouring pixels blended a little along
     * the row, and an aperture-grille mask of R / G / B stripes one view pixel wide. Worked out
     * in linear light, then brightened back to about the plain picture's level. Drawn at the
     * view's own pixels: the beams are smooth, so the non-whole scale doesn't show, and the mask
     * needs real view pixels.
     */
    private val CRT = Effect(
        """
        $HEADER
        const float gamma = 2.2;
        const float maskDim = 0.6;
        const float boost = 1.5;
        vec3 fetch(vec2 px) {
            return pow(texture2D(uTex, (px + 0.5) / uTexSize).rgb, vec3(gamma));
        }
        vec3 beam(float dist, vec3 c) {
            vec3 w = mix(vec3(0.22), vec3(0.32), c);
            return exp(-dist * dist / (2.0 * w * w));
        }
        void main() {
            vec2 pos = vUv * uTexSize - 0.5;
            vec2 base = floor(pos);
            vec2 f = pos - base;
            float fx = smoothstep(0.2, 0.8, f.x);
            vec3 row0 = mix(fetch(base), fetch(base + vec2(1.0, 0.0)), fx);
            vec3 row1 = mix(fetch(base + vec2(0.0, 1.0)), fetch(base + vec2(1.0, 1.0)), fx);
            vec3 c = row0 * beam(f.y, row0) + row1 * beam(1.0 - f.y, row1);
            float stripe = mod(floor(gl_FragCoord.x), 3.0);
            vec3 mask = vec3(maskDim);
            if (stripe < 0.5) mask.r = 1.0;
            else if (stripe < 1.5) mask.g = 1.0;
            else mask.b = 1.0;
            c = clamp(c * mask * boost, 0.0, 1.0);
            gl_FragColor = vec4(pow(c, vec3(1.0 / gamma)), 1.0);
        }
        """.trimIndent(),
        prescale = false,
    )
}

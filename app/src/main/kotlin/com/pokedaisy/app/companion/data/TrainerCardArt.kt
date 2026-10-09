package com.pokedaisy.app.companion.data

import java.io.File

/**
 * Draws the game's own TRAINER CARD, pixel for pixel, from the player's ROM:
 * the card's tiles, front / back / backdrop tilemaps, the palette for its
 * star count, badges, the player's trainer pic and FONT_NORMAL, all found by
 * [RomArt] and cached as raw blobs ([RomArt.rawPath]). Layers, coordinates
 * and strings follow both decomps' src/trainer_card.c:
 *
 *  - BG2 (bottom): the backdrop tilemap; BG0: the card's front or back.
 *  - BG3: stars (tile 143, palette 4) and badges (from tile 192, palette 3)
 *    on the front, plus the trainer pic window (tile 19,5, palette 8);
 *    FireRed's stickers (from tile 320, palettes 11-14) on the back.
 *  - BG1 (top): the text window at tile 1,1 - every x / y below is relative
 *    to it, exactly as AddTextPrinterParameterized3 gets them.
 *
 * Palettes: the card's three at 0-2 by star count, the female backdrop
 * palette over 1, badges at 3, the star at 4. Text colours are the standard
 * window palette's (dark grey on light grey, red on orange for stats).
 */
object TrainerCardArt {
    private class Blobs(
        val gfx: RomBlob, val front: RomBlob, val back: RomBlob, val bg: RomBlob, val pals: List<RomBlob>,
        val femalePal: RomBlob?, val badgesPal: RomBlob?, val badgesGfx: RomBlob?,
        val picMale: RomBlob, val picMalePal: RomBlob, val picFemale: RomBlob, val picFemalePal: RomBlob,
        val font: RomBlob, val widths: RomBlob,
        /** FireRed's Sticker Man stickers: their tiles, then a palette per brag level. */
        val stickers: List<RomBlob> = emptyList(),
        /** Applied to the card's three palettes; null = as stored. */
        val recolor: ((Int) -> Int)? = null,
    ) {
        val all get() = (listOf(gfx, front, back, bg) + pals + listOfNotNull(
            femalePal, badgesPal, badgesGfx, RomBlob.CARD_STAR_PAL, picMale, picMalePal, picFemale, picFemalePal, font, widths,
        ) + stickers).distinct()
    }

    private val KANTO = Blobs(
        RomBlob.FR_CARD_GFX, RomBlob.FR_CARD_FRONT, RomBlob.FR_CARD_BACK, RomBlob.FR_CARD_BG,
        listOf(RomBlob.FR_CARD_PAL0, RomBlob.FR_CARD_PAL1, RomBlob.FR_CARD_PAL2, RomBlob.FR_CARD_PAL3, RomBlob.FR_CARD_PAL4),
        RomBlob.FR_CARD_FEMALE_PAL, RomBlob.FR_CARD_BADGES_PAL, RomBlob.FR_CARD_BADGES_GFX,
        RomBlob.FR_PIC_RED, RomBlob.FR_PIC_RED_PAL, RomBlob.FR_PIC_LEAF, RomBlob.FR_PIC_LEAF_PAL,
        RomBlob.FR_FONT_NORMAL, RomBlob.FR_FONT_NORMAL_WIDTHS,
        listOf(
            RomBlob.FR_CARD_STICKERS_GFX, RomBlob.FR_CARD_STICKER_PAL1, RomBlob.FR_CARD_STICKER_PAL2,
            RomBlob.FR_CARD_STICKER_PAL3, RomBlob.FR_CARD_STICKER_PAL4,
        ),
    )

    private val HOENN = Blobs(
        RomBlob.EM_CARD_GFX, RomBlob.EM_CARD_FRONT, RomBlob.EM_CARD_BACK, RomBlob.EM_CARD_BG,
        listOf(RomBlob.EM_CARD_PAL0, RomBlob.EM_CARD_PAL1, RomBlob.EM_CARD_PAL2, RomBlob.EM_CARD_PAL3, RomBlob.EM_CARD_PAL4),
        RomBlob.EM_CARD_FEMALE_PAL, RomBlob.EM_CARD_BADGES_PAL, RomBlob.EM_CARD_BADGES_GFX,
        RomBlob.EM_PIC_BRENDAN, RomBlob.EM_PIC_BRENDAN_PAL, RomBlob.EM_PIC_MAY, RomBlob.EM_PIC_MAY_PAL,
        RomBlob.EM_FONT_NORMAL, RomBlob.EM_FONT_NORMAL_WIDTHS,
    )

    /**
     * Unbound has no card of this kind to copy (its own is a different screen),
     * so it gets FireRed's - which its ROM still holds - turned to the purple
     * of its own card ([unboundPurple]), with its player's front pic
     * (Unbound's protagonists replace Red / Leaf at the same pic ids). Its ROM
     * dropped FireRed's 0-star palette, so every star count starts from the
     * 1-star one; its badges aren't FireRed's, so none are drawn.
     */
    private val UNBOUND = Blobs(
        RomBlob.FR_CARD_GFX, RomBlob.FR_CARD_FRONT, RomBlob.FR_CARD_BACK, RomBlob.FR_CARD_BG,
        List(5) { RomBlob.FR_CARD_PAL1 },
        null, null, null,
        RomBlob.UB_PIC_MALE, RomBlob.UB_PIC_MALE_PAL, RomBlob.UB_PIC_FEMALE, RomBlob.UB_PIC_FEMALE_PAL,
        RomBlob.FR_FONT_NORMAL, RomBlob.FR_FONT_NORMAL_WIDTHS,
        recolor = ::unboundPurple,
    )

    /**
     * Glazed: retail Emerald's card, with its own Tunod badges (repointed tiles, the palette
     * edited in place), its own player pics (the front-pic table's 71 / 72 repointed, their
     * palettes edited in place) and FONT_NORMAL edited in place (its word on the back's
     * "POKéBLOCKS" line). The widths are retail's.
     */
    private val GLAZED = Blobs(
        RomBlob.EM_CARD_GFX, RomBlob.EM_CARD_FRONT, RomBlob.EM_CARD_BACK, RomBlob.EM_CARD_BG,
        listOf(RomBlob.EM_CARD_PAL0, RomBlob.EM_CARD_PAL1, RomBlob.EM_CARD_PAL2, RomBlob.EM_CARD_PAL3, RomBlob.EM_CARD_PAL4),
        RomBlob.EM_CARD_FEMALE_PAL, RomBlob.GZ_CARD_BADGES_PAL, RomBlob.GZ_CARD_BADGES_GFX,
        RomBlob.GZ_PIC_MALE, RomBlob.GZ_PIC_MALE_PAL, RomBlob.GZ_PIC_FEMALE, RomBlob.GZ_PIC_FEMALE_PAL,
        RomBlob.GZ_FONT_NORMAL, RomBlob.EM_FONT_NORMAL_WIDTHS,
    )

    /** Emerald Imperium: retail Emerald's card and pics in its own FONT_NORMAL (and widths). */
    private val IMPERIUM = Blobs(
        RomBlob.EM_CARD_GFX, RomBlob.EM_CARD_FRONT, RomBlob.EM_CARD_BACK, RomBlob.EM_CARD_BG,
        listOf(RomBlob.EM_CARD_PAL0, RomBlob.EM_CARD_PAL1, RomBlob.EM_CARD_PAL2, RomBlob.EM_CARD_PAL3, RomBlob.EM_CARD_PAL4),
        RomBlob.EM_CARD_FEMALE_PAL, RomBlob.EM_CARD_BADGES_PAL, RomBlob.EM_CARD_BADGES_GFX,
        RomBlob.EM_PIC_BRENDAN, RomBlob.EM_PIC_BRENDAN_PAL, RomBlob.EM_PIC_MAY, RomBlob.EM_PIC_MAY_PAL,
        RomBlob.IMP_FONT_NORMAL, RomBlob.IMP_FONT_NORMAL_WIDTHS,
    )

    private fun blobs(style: CardStyle) = when (style) {
        CardStyle.KANTO -> KANTO
        CardStyle.HOENN -> HOENN
        CardStyle.UNBOUND -> UNBOUND
        CardStyle.GLAZED -> GLAZED
        CardStyle.IMPERIUM -> IMPERIUM
    }

    /** Every blob a card needs, every style. */
    val BLOBS: List<RomBlob> = (KANTO.all + HOENN.all + UNBOUND.all + GLAZED.all + IMPERIUM.all).distinct()

    /** One style's decoded blobs. */
    class Art internal constructor(val style: CardStyle, internal val d: Map<RomBlob, ByteArray>)

    /** [style]'s card from [get], or null unless every blob is there. */
    fun art(style: CardStyle, get: (RomBlob) -> ByteArray?): Art? {
        val d = blobs(style).all.associateWith { get(it) ?: return null }
        return Art(style, d)
    }

    private val loaded = java.util.concurrent.ConcurrentHashMap<CardStyle, Art>()

    /** [style]'s card from the cached blobs under [dir] ([RomArt.dir]); kept for [cached]. */
    fun load(dir: File, style: CardStyle): Art? =
        art(style) { b -> File(dir, RomArt.rawPath(b)).takeIf { it.isFile }?.readBytes() }?.also { loaded[style] = it }

    /** The last [load]ed card of [style], so a screen can start drawing it without waiting. */
    fun cached(style: CardStyle): Art? = loaded[style]

    // Per style: the text window's width, FONT_NORMAL's letter spacing and
    // glyph height (FireRed 14 rows, Emerald 16; RenderText adds the spacing
    // only to Japanese text, so it is 0 for both), the digit pad of
    // STR_CONV_MODE_RIGHT_ALIGN (FireRed a space, Emerald CHAR_SPACER), the
    // stars' / badges' tile rows and the trainer pic's offset in its window.
    private class Layout(val textW: Int, val spacing: Int, val glyphH: Int, val pad: Int, val starY: Int, val badgeY: Int, val picX: Int, val picY: Int)

    private val KANTO_LAYOUT = Layout(textW = 27 * 8, spacing = 0, glyphH = 14, pad = 0x00, starY = 7, badgeY = 16, picX = 13, picY = 4)
    private val HOENN_LAYOUT = Layout(textW = 28 * 8, spacing = 0, glyphH = 16, pad = 0x77, starY = 7, badgeY = 15, picX = 1, picY = 0)

    private const val TEXT = 0xFF636363.toInt()
    private const val TEXT_SHADOW = 0xFFD6D6CE.toInt()
    private const val STAT = 0xFFE70808.toInt()
    private const val STAT_SHADOW = 0xFFFFBD73.toInt()

    /**
     * The card as the game shows it: 240x160, front or [back]; [colon] = the
     * play time's blinking colon. [backdrop] false leaves out the screen
     * behind the card (BG2), transparent instead - see [cardBounds].
     */
    fun render(art: Art, card: TrainerCardInfo, back: Boolean, colon: Boolean = true, backdrop: Boolean = true): RomArt.Image {
        val b = blobs(art.style)
        val kanto = !art.style.hoenn
        val lay = if (kanto) KANTO_LAYOUT else HOENN_LAYOUT
        fun d(r: RomBlob) = art.d.getValue(r)
        val pal = IntArray(256)
        RomArt.palette(d(b.pals[card.stars.coerceIn(0, 4)])).copyInto(pal, 0)
        if (card.female) b.femalePal?.let { RomArt.palette(d(it)).copyInto(pal, 16) }
        b.recolor?.let { f -> for (i in 0 until 48) pal[i] = f(pal[i]) }
        b.badgesPal?.let { RomArt.palette(d(it)).copyInto(pal, 48) }
        RomArt.palette(d(RomBlob.CARD_STAR_PAL)).copyInto(pal, 64)
        val tiles = d(b.gfx)
        val img = RomArt.Image(240, 160)

        // BG2 then BG0: colour 0 is see-through, down to the backdrop (palette 0's first colour).
        if (backdrop) img.argb.fill(pal[0])
        for (map in listOfNotNull(d(b.bg).takeIf { backdrop }, d(if (back) b.back else b.front))) {
            for (ty in 0 until 20) for (tx in 0 until 30) tile(img, tiles, pal, u16(map, ty * 30 + tx), tx, ty)
        }
        // BG3.
        val bg3 = ByteArray(0x1800 + 0x400).also { tiles.copyInto(it); b.badgesGfx?.let { g -> d(g).copyInto(it, 192 * 32) } }
        if (!back) {
            for (i in 0 until card.stars.coerceIn(0, 4)) tile(img, bg3, pal, (4 shl 12) or 143, 15 + i, lay.starY)
            for (i in 0 until 8) if (b.badgesGfx != null && card.badges and (1 shl i) != 0) {
                val t = 192 + 2 * i
                val x = 4 + 3 * i
                tile(img, bg3, pal, (3 shl 12) or t, x, lay.badgeY)
                tile(img, bg3, pal, (3 shl 12) or (t + 1), x + 1, lay.badgeY)
                tile(img, bg3, pal, (3 shl 12) or (t + 16), x, lay.badgeY + 1)
                tile(img, bg3, pal, (3 shl 12) or (t + 17), x + 1, lay.badgeY + 1)
            }
            pic(img, d(if (card.female) b.picFemale else b.picMale), RomArt.palette(d(if (card.female) b.picFemalePal else b.picMalePal)), lay)
        } else if (kanto) {
            // DrawCardBackStats: a little marker beside each FireRed stat line that has a count.
            fun mark(x: Int, y: Int) {
                tile(img, bg3, pal, (1 shl 12) or 141, x, y)
                tile(img, bg3, pal, (1 shl 12) or 157, x, y + 1)
            }
            if (card.trades != 0) mark(26, 9)
            if (card.berryCrush != 0) mark(21, 13)
            if (card.unionRoom != 0) mark(27, 11)
            // PrintStickersOnCard: sticker i (HoF, eggs, link wins) at tile (2 + 3i, 2), coloured by its brag level.
            val stickerTiles = b.stickers.firstOrNull()?.let(::d)
            card.stickers.forEachIndexed { i, level ->
                if (level !in 1..4 || stickerTiles == null) return@forEachIndexed
                val sp = IntArray(256).also { RomArt.palette(d(b.stickers[level])).copyInto(it) }
                for (k in 0 until 4) tile(img, stickerTiles, sp, i * 4 + k, 2 + 3 * i + k % 2, 2 + k / 2)
            }
        } else {
            // Emerald's DrawCardBackStats (palette 0).
            fun mark(x: Int, y: Int) {
                tile(img, bg3, pal, 141, x, y)
                tile(img, bg3, pal, 157, x, y + 1)
            }
            if (card.trades != 0) mark(27, 9)
            if (card.linkContests != 0) mark(27, 13)
        }
        // BG1.
        val t = Text(img, d(b.font), d(b.widths), lay)
        if (kanto) kantoText(t, card, back, colon) else hoennText(t, card, back, colon, art.style)
        return img
    }

    /**
     * FireRed's card colour turned to Unbound's card: its purple hue (266),
     * the pale greens and teals onto Unbound's lavender ramp (sampled headless:
     * CEADF7 / AD7BE7 / 7B52B5 - about 1.8x the saturation, a little brighter).
     * Greys and the gold accents (hue 25-65) stay.
     */
    private fun unboundPurple(argb: Int): Int {
        val r = (argb shr 16 and 0xFF) / 255f
        val g = (argb shr 8 and 0xFF) / 255f
        val bl = (argb and 0xFF) / 255f
        val max = maxOf(r, g, bl)
        val min = minOf(r, g, bl)
        val d = max - min
        val s = if (max == 0f) 0f else d / max
        if (s < 0.08f) return argb
        val hue = when {
            max == r -> 60f * (((g - bl) / d) % 6f)
            max == g -> 60f * ((bl - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }.let { if (it < 0f) it + 360f else it }
        if (hue in 25f..65f) return argb
        return hsv(266f, minOf(s * 1.8f, 0.6f), minOf(max * 1.15f + 0.05f, 1f), argb ushr 24)
    }

    private fun hsv(hue: Float, s: Float, v: Float, alpha: Int): Int {
        val h = hue / 60f
        val c = v * s
        val x = c * (1 - kotlin.math.abs(h % 2 - 1))
        val (r1, g1, b1) = when (h.toInt()) {
            0 -> Triple(c, x, 0f); 1 -> Triple(x, c, 0f); 2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c); 4 -> Triple(x, 0f, c); else -> Triple(c, 0f, x)
        }
        val m = v - c
        fun ch(f: Float) = ((f + m) * 255f + 0.5f).toInt().coerceIn(0, 255)
        return (alpha shl 24) or (ch(r1) shl 16) or (ch(g1) shl 8) or ch(b1)
    }


    private fun kantoText(t: Text, c: TrainerCardInfo, back: Boolean, colon: Boolean) {
        if (!back) {
            t.print(20, 29, enc("NAME: ") + c.name)
            t.print(142, 10, enc("IDNo.") + digits(c.trainerId, 5, LEADING_ZEROS, 0))
            val money = enc("¥") + digits(c.money.toInt(), 6, LEFT, 0)
            t.print(20, 56, enc("MONEY"))
            t.print((134 - 6 * money.size) and 0xFF, 56, money)
            c.dexCaught?.let { n ->
                val s = digits(n, 3, LEFT, 0)
                t.print(20, 72, enc("POKéDEX"))
                t.print((136 - 6 * s.size) and 0xFF, 72, s)
            }
            t.print(20, 88, enc("TIME"))
            t.print(101, 88, digits(c.hours, 3, RIGHT, 0x00))
            if (colon) t.print(119, 88, enc(":"))
            t.print(124, 88, digits(c.minutes, 2, LEADING_ZEROS, 0))
            return
        }
        t.print(138, 11, c.name)
        if (c.hofDebut != 0) {
            t.print(10, 35, enc("HALL OF FAME DEBUT  "))
            t.print(164, 35, hofTime(c.hofDebut, 0x00), STAT, STAT_SHADOW)
        }
        if (c.linkWins != 0 || c.linkLosses != 0) {
            t.print(10, 51, enc("LINK BATTLES"))
            t.print(130, 51, enc("W:"))
            t.print(130 + 0x30, 51, enc("L:"))
            t.print(144, 51, digits(c.linkWins, 4, RIGHT, 0x00), STAT, STAT_SHADOW)
            t.print(192, 51, digits(c.linkLosses, 4, RIGHT, 0x00), STAT, STAT_SHADOW)
        }
        if (c.trades != 0) {
            t.print(10, 67, enc("POKéMON TRADES"))
            t.print(186, 67, digits(c.trades, 5, RIGHT, 0x00), STAT, STAT_SHADOW)
        }
        if (c.unionRoom != 0) {
            t.print(10, 83, enc("UNION TRADES & BATTLES"))
            t.print(186, 83, digits(c.unionRoom, 5, RIGHT, 0x00), STAT, STAT_SHADOW)
        }
        if (c.berryCrush != 0) {
            t.print(10, 99, enc("BERRY CRUSH"))
            t.print(186, 99, digits(c.berryCrush, 5, RIGHT, 0x00), STAT, STAT_SHADOW)
        }
    }

    private fun hoennText(t: Text, c: TrainerCardInfo, back: Boolean, colon: Boolean, style: CardStyle = CardStyle.HOENN) {
        val pad = 0x77
        // Imperium words three labels its own way; Glazed prints 7 money digits (its cap is 9,999,999).
        val imperium = style == CardStyle.IMPERIUM
        if (!back) {
            t.print(16, 33, enc("NAME: ") + c.name)
            val id = enc("IDNo.") + digits(c.trainerId, 5, LEADING_ZEROS, pad)
            t.print((96 - t.width(id)) / 2 + 120, 9, id)
            val money = enc("¥") + digits(c.money.toInt(), if (style == CardStyle.GLAZED) 7 else 6, LEFT, pad)
            t.print(16, 57, enc("MONEY"))
            t.print(128 - t.width(money), 57, money)
            c.dexCaught?.let { n ->
                val s = digits(n, 3, LEFT, pad)
                t.print(16, 73, enc(if (imperium) "Pokédex" else "POKéDEX"))
                t.print(128 - t.width(s), 73, s)
            }
            t.print(16, 89, enc(if (imperium) "Time" else "TIME"))
            val colonW = t.width(enc(":"))
            val x = 128 - (colonW + 30)
            t.print(x, 89, digits(c.hours, 3, RIGHT, pad))
            if (colon) t.print(x + 18, 89, enc(":"))
            t.print(x + 18 + colonW, 89, digits(c.minutes, 2, LEADING_ZEROS, pad))
            return
        }
        val name = c.name + enc("'s TRAINER CARD")
        t.print(216 - t.width(name), 9, name)
        // PrintStatOnBackOfCard: the label at x 16, the value right-aligned to 216, a row every 16 px.
        fun stat(row: Int, label: List<Int>, value: List<Int>, red: Boolean = true) {
            t.print(16, row * 16 + 33, label)
            t.print(216 - t.width(value), row * 16 + 33, value, if (red) STAT else TEXT, if (red) STAT_SHADOW else TEXT_SHADOW)
        }
        if (c.hofDebut != 0) stat(0, enc("HALL OF FAME DEBUT  "), hofTime(c.hofDebut, pad))
        if (c.linkWins != 0 || c.linkLosses != 0) {
            // "W:{red}wins{grey}  L:{red}losses": one string, colours switching inside it.
            val w = enc("W:")
            val wins = digits(c.linkWins, 4, LEFT, pad)
            val l = enc("  L:")
            val losses = digits(c.linkLosses, 4, LEFT, pad)
            t.print(16, 49, enc("LINK BATTLES"))
            var x = 216 - t.width(w + wins + l + losses)
            for ((part, red) in listOf(w to false, wins to true, l to false, losses to true)) {
                t.print(x, 49, part, if (red) STAT else TEXT, if (red) STAT_SHADOW else TEXT_SHADOW)
                x += t.width(part)
            }
        }
        if (c.trades != 0) stat(2, enc(if (imperium) "Pokémon TRADES" else "POKéMON TRADES"), digits(c.trades, 5, RIGHT, pad))
        if (c.linkPokeblocks != 0) stat(3, listOf(0x55, 0x56, 0x57, 0x58, 0x59) + enc("S W/FRIENDS"), digits(c.linkPokeblocks, 5, RIGHT, pad))
        if (c.linkContests != 0) stat(4, enc("WON CONTESTS W/FRIENDS"), digits(c.linkContests, 5, RIGHT, pad))
        if (c.battlePoints != 0) {
            val n = digits(c.battlePoints, 5, RIGHT, pad)
            val bp = enc("BP")
            val x = 216 - t.width(n + bp)
            t.print(16, 5 * 16 + 33, enc("BATTLE POINTS WON"))
            t.print(x, 5 * 16 + 33, n, STAT, STAT_SHADOW)
            t.print(x + t.width(n), 5 * 16 + 33, bp)
        }
    }

    /** The card's own pixels in a [render] without the backdrop: left, top, right, bottom (exclusive). */
    fun cardBounds(img: RomArt.Image): IntArray {
        var l = img.width; var t = img.height; var r = 0; var b = 0
        for (y in 0 until img.height) for (x in 0 until img.width) if (img.argb[y * img.width + x] != 0) {
            l = minOf(l, x); t = minOf(t, y); r = maxOf(r, x + 1); b = maxOf(b, y + 1)
        }
        return if (r > l) intArrayOf(l, t, r, b) else intArrayOf(0, 0, img.width, img.height)
    }

    private fun hofTime(hof: Int, pad: Int) =
        digits(hof ushr 16, 3, RIGHT, pad) + enc(":") + digits((hof shr 8) and 0xFF, 2, LEADING_ZEROS, pad) + enc(":") + digits(hof and 0xFF, 2, LEADING_ZEROS, pad)

    private const val LEFT = 0
    private const val RIGHT = 1
    private const val LEADING_ZEROS = 2

    /** ConvertIntToDecimalStringN: [n] digits at most; RIGHT pads with [pad], LEFT doesn't. */
    private fun digits(value: Int, n: Int, mode: Int, pad: Int): List<Int> {
        val s = value.coerceAtLeast(0).toString().takeLast(n)
        val out = s.map { 0xA1 + (it - '0') }
        return when (mode) {
            LEADING_ZEROS -> List(n - out.size) { 0xA1 } + out
            RIGHT -> List(n - out.size) { pad } + out
            else -> out
        }
    }

    /** ASCII (and é / ¥) in the games' character encoding. */
    fun enc(s: String): List<Int> = s.map { c ->
        when (c) {
            in 'A'..'Z' -> 0xBB + (c - 'A')
            in 'a'..'z' -> 0xD5 + (c - 'a')
            in '0'..'9' -> 0xA1 + (c - '0')
            ' ' -> 0x00
            'é' -> 0x1B
            '¥' -> 0xB7
            ':' -> 0xF0
            '.' -> 0xAD
            '\'' -> 0xB4
            '&' -> 0x2D
            '/' -> 0xBA
            '-' -> 0xAE
            else -> 0xAC // '?'
        }
    }

    private fun u16(b: ByteArray, i: Int) = (b[i * 2].toInt() and 0xFF) or ((b[i * 2 + 1].toInt() and 0xFF) shl 8)

    /** One 8x8 4bpp BG tile through tilemap entry [e] at tile ([tx],[ty]); colour 0 left alone. */
    private fun tile(img: RomArt.Image, tiles: ByteArray, pal: IntArray, e: Int, tx: Int, ty: Int) {
        if (tx !in 0 until 30 || ty !in 0 until 20) return
        val t = e and 0x3FF
        val pn = e shr 12
        for (y in 0 until 8) for (x in 0 until 8) {
            val sx = if (e and 0x400 != 0) 7 - x else x
            val sy = if (e and 0x800 != 0) 7 - y else y
            val byte = tiles.getOrElse(t * 32 + sy * 4 + sx / 2) { 0 }.toInt()
            val v = if (sx and 1 != 0) (byte shr 4) and 15 else byte and 15
            if (v != 0) img.argb[(ty * 8 + y) * 240 + tx * 8 + x] = pal[pn * 16 + v]
        }
    }

    /** The 64x64 front pic, blitted into its 72x80 window at tile (19,5), clipped to it. */
    private fun pic(img: RomArt.Image, tiles: ByteArray, pal: IntArray, lay: Layout) {
        val wx = 19 * 8
        val wy = 5 * 8
        for (y in 0 until 64) for (x in 0 until 64) {
            val px = lay.picX + x
            val py = lay.picY + y
            if (px >= 72 || py >= 80) continue
            val byte = tiles[((y / 8) * 8 + x / 8) * 32 + (y % 8) * 4 + (x % 8) / 2].toInt()
            val v = if (x and 1 != 0) (byte shr 4) and 15 else byte and 15
            if (v != 0) img.argb[(wy + py) * 240 + wx + px] = pal[v]
        }
    }

    /** FONT_NORMAL into the text window (tile 1,1): 16x16 2bpp glyphs, 1 = text, 2 = shadow. */
    private class Text(val img: RomArt.Image, val font: ByteArray, val widths: ByteArray, val lay: Layout) {
        fun width(s: List<Int>) = s.sumOf { (widths[it].toInt() and 0xFF) + lay.spacing }

        fun print(x0: Int, y: Int, s: List<Int>, fg: Int = TEXT, shadow: Int = TEXT_SHADOW) {
            var x = x0
            for (g in s) {
                for (py in 0 until lay.glyphH) for (px in 0 until 16) {
                    val wx = x + px
                    val wy = y + py
                    if (wx !in 0 until lay.textW || wy !in 0 until 144) continue
                    val base = g * 64 + ((py / 8) * 2 + px / 8) * 16 + (py % 8) * 2
                    val row = (font[base].toInt() and 0xFF) or ((font[base + 1].toInt() and 0xFF) shl 8)
                    val c = when ((row shr (14 - 2 * (px % 8))) and 3) {
                        1 -> fg
                        2 -> shadow
                        else -> continue
                    }
                    img.argb[(8 + wy) * 240 + 8 + wx] = c
                }
                x += (widths[g].toInt() and 0xFF) + lay.spacing
            }
        }
    }
}

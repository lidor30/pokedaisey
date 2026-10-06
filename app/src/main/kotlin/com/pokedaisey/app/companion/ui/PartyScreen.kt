package com.pokedaisey.app.companion.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import com.pokedaisey.app.companion.data.GENDER_SYMBOL_FEMALE
import com.pokedaisey.app.companion.data.GENDER_SYMBOL_MALE
import com.pokedaisey.app.companion.data.GameKind
import com.pokedaisey.app.companion.data.MonView
import com.pokedaisey.app.companion.data.activeGame
import com.pokedaisey.app.companion.ui.theme.QolColors
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val PARTY_MAX = 6

/** The PARTY tab: the lead under the cursor, a tap opens that Pokémon's summary. */
@Composable
fun PartyScreen(party: List<MonView>, onMonClick: (MonView) -> Unit) {
    PartyGrid(party, selected = 0, onSlotClick = onMonClick)
}

/**
 * Six slots, in-game style: filled first, then empties. Deliberately NOT a
 * `LazyVerticalGrid` (which scrolls once its 96dp-tall cells don't all fit) —
 * a plain weighted Column/Row of 3 rows x 2 columns always fills exactly the
 * tab's height with no scroll, and each card's height adapts to whatever
 * space that leaves per row instead of a fixed dp.
 *
 * Shared by the PARTY tab and the battle POKéMON pane: [selected] is the slot
 * under the game's cursor (-1 = none), [badge] draws over a filled slot's
 * top-right corner, and [sound] false leaves taps silent for the pane, whose
 * picks press the game's own buttons (the game sounds those itself).
 */
@Composable
fun PartyGrid(
    party: List<MonView>,
    selected: Int,
    onSlotClick: (MonView) -> Unit,
    modifier: Modifier = Modifier.fillMaxSize(),
    sound: Boolean = true,
    badge: @Composable BoxScope.(MonView) -> Unit = {},
) {
    val slots: List<MonView?> = (0 until PARTY_MAX).map { party.getOrNull(it) }
    // FireRed/Emerald, Heart and Soul and the CFRU hacks get the game's own party-slot look
    // (drawn from the game's own data); every other game - or one whose art no
    // ROM has supplied yet (RomArt) - gets a FireRed-like slot drawn from a [PartyPalette].
    val gameStyle = partySlotStyleFor(activeGame)?.takeIf { rememberPartySlotAssets(it) != null }
    val palette = partyPaletteFor(activeGame)
    // Both sit edge to edge like in the game: each slot carries its own Poke
    // Ball overhang zone above/left of its frame (the CFRU boxes are only ~2px
    // apart there too) - extra spacing would just be dead margin.
    Column(modifier = modifier) {
        slots.chunked(2).forEachIndexed { rowIdx, row ->
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                row.forEachIndexed { colIdx, mon ->
                    val i = rowIdx * 2 + colIdx
                    val isSelected = i == selected && mon != null
                    val onClick = { mon?.let(onSlotClick); Unit }
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        if (gameStyle != null) {
                            GbaPartySlot(gameStyle, mon, selected = isSelected, onClick = onClick, modifier = Modifier.fillMaxSize(), sound = sound)
                        } else {
                            PartySlot(palette, mon, selected = isSelected, onClick = onClick, modifier = Modifier.fillMaxSize(), sound = sound)
                        }
                        if (mon != null) badge(mon)
                    }
                }
            }
        }
    }
}

/** A slot's tap: with the companion's click, or silent when the game makes its own sound. */
internal fun Modifier.slotClickable(sound: Boolean, enabled: Boolean, onClick: () -> Unit): Modifier =
    if (sound) soundClickable(enabled = enabled, onClick = onClick) else clickable(enabled = enabled, onClick = onClick)

/** One slot state's frame colors: [outline] 2px, a 1px [light] inner edge,
 * the upper [fill], the HP [band] below it (FireRed's is the light edge's
 * color; flat slots like Gaia's are the fill's), a 1px bottom [shade]. [text]
 * / [textShadow] override the palette's for this state (Lazarus colors its
 * text per state); a transparent shadow = none. */
data class PartySlotColors(
    val outline: Color, val light: Color, val fill: Color, val shade: Color,
    val band: Color = light,
    val text: Color? = null, val textShadow: Color? = null,
)

/** HP bar fill: a darker [top] row over the [main] color. */
data class HpBarColors(val top: Color, val main: Color)

/**
 * The generic party slot's colors - the party-screen counterpart of the Items
 * tab's BagPalette: the layout is FireRed's MAIN party slot for every game
 * without a [PartySlotStyle] of its own, and a game's look is just a palette
 * picked in [partyPaletteFor].
 */
data class PartyPalette(
    val normal: PartySlotColors,
    val selected: PartySlotColors,
    val fainted: PartySlotColors,
    val selectedFainted: PartySlotColors = fainted.copy(outline = selected.outline),
    val text: Color,
    val textShadow: Color,
    val male: Color, val maleShadow: Color,
    val female: Color, val femaleShadow: Color,
    /** The HP bar's dark outline (also the "HP" label box) and its white inner edge. */
    val hpFrame: Color, val hpInner: Color,
    /** "HP" letters: top rows / bottom rows. */
    val hpLabel: Color, val hpLabelShade: Color,
    val hpGreen: HpBarColors, val hpYellow: HpBarColors, val hpRed: HpBarColors, val hpEmpty: HpBarColors,
    /** The Poke Ball overhanging each slot's top-left corner, by [PartyBall]'s letters. */
    val ball: Map<Char, Color>,
    /** How faintly empty slots show their (no-HP) box. */
    val emptyAlpha: Float = 0.35f,
    /** The game's own empty-slot box, drawn solid instead of a faint [normal] one; null = faint. */
    val empty: PartySlotColors? = null,
)

/** FireRed's party menu (partyfr/slot_*.png, pokeball.png; Emerald's is identical). */
val FireRedPartyPalette = PartyPalette(
    normal = PartySlotColors(Color(0xFF4A4A63), Color(0xFF84C6DE), Color(0xFF3994DE), Color(0xFF297BB5)),
    selected = PartySlotColors(Color(0xFFFF7331), Color(0xFFADEFFF), Color(0xFF7BD6EF), Color(0xFF4AADCE)),
    fainted = PartySlotColors(Color(0xFF4A4A63), Color(0xFFD6A521), Color(0xFFC66B10), Color(0xFFA54A00)),
    text = Color.White, textShadow = Color(0xFF737373),
    male = Color(0xFF42CEFF), maleShadow = Color(0xFF006394),
    female = Color(0xFFFF9C94), femaleShadow = Color(0xFF9C4239),
    hpFrame = Color(0xFF525252), hpInner = Color.White,
    hpLabel = Color(0xFFFFD652), hpLabelShade = Color(0xFFFFB542),
    hpGreen = HpBarColors(Color(0xFF5AD684), Color(0xFF73FFAD)),
    hpYellow = HpBarColors(Color(0xFFCEAD08), Color(0xFFFFE739)),
    hpRed = HpBarColors(Color(0xFFC63900), Color(0xFFFF7331)),
    hpEmpty = HpBarColors(Color(0xFF525252), Color(0xFF737373)),
    ball = mapOf('K' to Color(0xFF4A4A63), 'R' to Color(0xFFB54200), 'H' to Color(0xFFC65200), 'W' to Color(0xFFB5BDCE)),
)

/*
 * The games below use FireRed's party layout (or near it) in their own colors,
 * sampled from each game's party menu with headless mGBA: the save's first mon
 * copied into slots 2-3, slot 3 at 0 HP, so normal / selected / fainted all
 * show (native-capture/menu-shots/<game>/party.png has the real screen).
 */

/** Emerald Rogue: FireRed's slots in purple-blue, fainted and the rest unchanged. */
val RoguePartyPalette = FireRedPartyPalette.copy(
    normal = PartySlotColors(Color(0xFF4A4A63), Color(0xFF7B6BFF), Color(0xFF6352F7), Color(0xFF4A42C6)),
    selected = PartySlotColors(Color(0xFFFF7331), Color(0xFF7B6BFF), Color(0xFF6352F7), Color(0xFF635ACE)),
)

/** Gaia: flat brown slots (no lighter HP band) with a light edge, a red cursor, grey fainted slots, a yellow "HP". */
val GaiaPartyPalette = FireRedPartyPalette.copy(
    normal = PartySlotColors(Color(0xFF4A4A63), Color(0xFFB5945A), Color(0xFF8C6B31), Color(0xFFB5945A), band = Color(0xFF8C6B31)),
    selected = PartySlotColors(Color(0xFFFF2121), Color(0xFFE7C69C), Color(0xFFBD9C63), Color(0xFFE7C69C), band = Color(0xFFBD9C63)),
    fainted = PartySlotColors(Color(0xFF4A4A63), Color(0xFFC6C6C6), Color(0xFF949494), Color(0xFFC6C6C6), band = Color(0xFF949494)),
    hpLabel = Color(0xFFFFDE00), hpLabelShade = Color(0xFFFFDE00),
    hpGreen = HpBarColors(Color(0xFF00A500), Color(0xFF18D621)),
)

/** Lazarus: flat boxes - light blue / orange edge under the cursor, dark blue / cyan otherwise - text in the edge's color, no shadow. */
val LazarusPartyPalette = FireRedPartyPalette.copy(
    normal = flatSlot(Color(0xFF4AF7FF), Color(0xFF187BB5), text = Color(0xFF4AF7FF)),
    selected = flatSlot(Color(0xFFFF7331), Color(0xFF7BD6EF), text = Color(0xFFFF7331)),
    fainted = flatSlot(Color(0xFF4A4A63), Color(0xFFC66B10), text = Color(0xFF4A4A63)),
    empty = flatSlot(Color(0xFF737373), Color.White),
)

/** Emerald Seaglass: plain white boxes on slate, orange text under the cursor, dark otherwise; empty slots slate in white lines. */
val SeaglassPartyPalette = FireRedPartyPalette.copy(
    normal = flatSlot(Color.White, Color.White, text = Color(0xFF4A4A63)),
    selected = flatSlot(Color(0xFF293131), Color.White, text = Color(0xFFFF7331)),
    fainted = flatSlot(Color.White, Color.White, text = Color(0xFF4A4A63)),
    empty = flatSlot(Color.White, Color(0xFF4A4A63)),
)

/** R.O.W.E.: charcoal boxes, white-outlined, red under the cursor, white text with a grey shadow, a bright green bar. */
val RowePartyPalette = FireRedPartyPalette.copy(
    normal = flatSlot(Color.White, Color(0xFF292929)),
    selected = flatSlot(Color(0xFFC60000), Color(0xFF292929)),
    fainted = flatSlot(Color(0xFFA5A5A5), Color(0xFF181818)),
    empty = flatSlot(Color.White, Color(0xFF292929)),
    text = Color(0xFFEFEFEF), textShadow = Color(0xFFA5A5A5),
    hpGreen = HpBarColors(Color(0xFF08FF5A), Color(0xFF08FF5A)),
)

/** A slot in one [fill] inside an [outline]: no separate edge, band or shade. */
private fun flatSlot(outline: Color, fill: Color, text: Color? = null) =
    PartySlotColors(outline, fill, fill, fill, text = text, textShadow = if (text != null) Color.Transparent else null)

/** The generic slot's palette for [game] - add a palette here to restyle a game. */
fun partyPaletteFor(game: GameKind): PartyPalette = when (game) {
    GameKind.EMERALD_ROGUE -> RoguePartyPalette
    GameKind.GAIA -> GaiaPartyPalette
    GameKind.LAZARUS -> LazarusPartyPalette
    GameKind.EMERALD_SEAGLASS -> SeaglassPartyPalette
    GameKind.ROWE -> RowePartyPalette
    // Celia's and Too Many Types 2's slots are FireRed's own colors (only TMT2's backdrop differs).
    else -> FireRedPartyPalette
}

/** The in-game party-slot look for [game], or null for games that don't have one yet. */
fun partySlotStyleFor(game: GameKind): PartySlotStyle? = when (game) {
    GameKind.FIRERED -> FireRedPartyStyle
    GameKind.EMERALD -> EmeraldPartyStyle
    GameKind.HEART_AND_SOUL -> HeartAndSoulPartyStyle
    GameKind.UNBOUND -> UnboundPartyStyle
    GameKind.RADICAL_RED -> RadicalRedPartyStyle
    GameKind.ODYSSEY -> OdysseyPartyStyle
    GameKind.AMETHYST -> AmethystPartyStyle
    else -> null
}

// FireRed's closed party Poke Ball (partyfr/pokeball.png, 20x20 of its 32x32 cell).
private val PartyBall = listOf(
    ".......KKKKKK.......",
    ".....KKKKKKKKKK.....",
    "....KKKRRRRRRKKK....",
    "...KKRRRRRRRRRRKK...",
    "..KKRRRRRRRRRRRRKK..",
    ".KKRRRRRRRRRRRRRRKK.",
    ".KKRRRRRRRRRRRRRRKK.",
    "KKRRRRRRKKKKRRRRRRKK",
    "KKHHHHHKKWWKKRRRRRKK",
    "KKKKKKKKWWWWKKKKKKKK",
    "KKKKKKKKWWWWKKKKKKKK",
    "KKWWWWWKKWWKKWWWWWKK",
    "KKWWWWWWKKKKWWWWWWKK",
    ".KKWWWWWWWWWWWWWWKK.",
    ".KKWWWWWWWWWWWWWWKK.",
    "..KKWWWWWWWWWWWWKK..",
    "...KKWWWWWWWWWWKK...",
    "....KKKWWWWWWKKK....",
    ".....KKKKKKKKKK.....",
    ".......KKKKKK.......",
)

// The "HP" label's letters, 5 rows (the top 3 in hpLabel, the rest in hpLabelShade).
private val HpLetters = listOf(
    "1001.1110",
    "1001.1001",
    "1111.1110",
    "1001.1000",
    "1001.1000",
)

/**
 * Where everything sits, in whole slot pixels [u] (screen px): FireRed's MAIN
 * slot (80x56 window, 49px-tall frame with the Poke Ball overhanging 8px left
 * and 6px up) scaled by a whole number and stretched to the cell, the way
 * [GbaPartySlot] stretches the real one.
 */
private class SlotGeometry(w: Float, h: Float) {
    val u = max(2, floor(min(w / 86f, h / 57f)).toInt())
    val fx = 8f * u
    val fy = 6f * u
    val fw = floor((w - fx - 2 * u) / u) * u
    val fh = floor((h - fy - 2 * u) / u) * u
    val bottom = fy + fh
    /** The light row splitting the name area from the HP band. */
    val dividerY = bottom - 19 * u
    val barY = dividerY + u
    val labelX = fx + 20 * u
    val barX = labelX + 11 * u
    val barRight = fx + fw - 4 * u
    val textX = fx + 30 * u
    /** Name and level line centres, spread over the upper area however tall it got. */
    val nameMid = fy + 3 * u + (dividerY - fy - 3 * u) * 0.3f
    val levelMid = fy + 3 * u + (dividerY - fy - 3 * u) * 0.74f
    val hpTextMid = barY + 7 * u + 3 * u
}

@Composable
private fun PartySlot(palette: PartyPalette, mon: MonView?, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, sound: Boolean = true) {
    val density = LocalDensity.current
    // The selected icon's bob (SpriteCB_BouncePartyMonIcon): up / down every 10 frames.
    val bobDown by rememberBlink(gbaFramesMs(10), enabled = selected, label = "party-bob")
    BoxWithConstraints(modifier.slotClickable(sound, enabled = mon != null, onClick = onClick)) {
        val g = with(density) { SlotGeometry(maxWidth.toPx(), maxHeight.toPx()) }
        val u = g.u
        val colors = when {
            mon == null -> palette.empty ?: if (selected) palette.selected else palette.normal
            mon.isEgg -> if (selected) palette.selected else palette.normal
            mon.hp == 0 -> if (selected) palette.selectedFainted else palette.fainted
            selected -> palette.selected
            else -> palette.normal
        }
        val showHp = mon != null && !mon.isEgg
        Canvas(
            Modifier.fillMaxSize().then(
                if (mon == null && palette.empty == null) Modifier.graphicsLayer { alpha = palette.emptyAlpha; compositingStrategy = CompositingStrategy.Offscreen }
                else Modifier,
            ),
        ) {
            drawSlotFrame(g, colors)
            if (showHp) drawHpBar(g, palette, mon!!.hp, mon.maxHp)
        }
        if (mon == null) return@BoxWithConstraints

        // Pixel Operator's caps are 9 font pixels to the game font's 7: 3/4 of a slot pixel keeps them in proportion.
        val m = slotTextMetrics(density, max(1, (u * 0.75f).roundToInt()))
        val chipText = slotTextMetrics(density, max(1, (u * 0.6f).roundToInt()))
        fun Density.at(x: Float, midY: Float) = Modifier.offset { IntOffset(x.toInt(), (midY - 8 * m.px).toInt()) }
        val text = colors.text ?: palette.text
        val textShadow = colors.textShadow ?: palette.textShadow

        // Poke Ball, then the icon over it: resting 4px up, bobbing -3/+1 while selected.
        Canvas(Modifier.fillMaxSize()) { drawPixelBitmap(PartyBall, palette.ball, g.fx - 6 * u, g.fy - 4 * u, u.toFloat()) }
        val iconDy = if (!selected) -4 else if (!bobDown) -3 else 1
        SpeciesIcon(
            mon.iconAsset, size = with(density) { (32 * u).toDp() },
            modifier = Modifier.offset { IntOffset((g.fx - 8 * u).toInt(), (g.fy + iconDy * u).toInt()) },
        )

        val textWidth = with(density) { (g.fx + g.fw - 4 * u - g.textX).toDp() }
        GbaText(mon.name, text, textShadow, m, density.at(g.textX, g.nameMid).width(textWidth))
        if (!showHp) return@BoxWithConstraints

        val status = if (mon.hp == 0) "FNT" else mon.status
        if (status.isNotEmpty()) {
            StatusChip(status, u, chipText, Modifier.offset { IntOffset((g.textX + 6 * u).toInt(), (g.levelMid - 5 * u).toInt()) })
        } else {
            GbaText("Lv${mon.level}", text, textShadow, m, density.at(g.textX + 6 * u, g.levelMid))
        }
        val gender = when (mon.genderSymbol) {
            GENDER_SYMBOL_MALE -> Triple(PixelIcons.male, palette.male, palette.maleShadow)
            GENDER_SYMBOL_FEMALE -> Triple(PixelIcons.female, palette.female, palette.femaleShadow)
            else -> null
        }
        gender?.let { (bitmap, color, shadow) ->
            Canvas(Modifier.fillMaxSize()) {
                val x = g.fx + g.fw - 14 * u
                val y = g.levelMid - 4 * u
                drawPixelBitmap(bitmap, mapOf('1' to shadow), x + u, y + u, u.toFloat())
                drawPixelBitmap(bitmap, mapOf('1' to color), x, y, u.toFloat())
            }
        }
        Box(density.at(0f, g.hpTextMid).width(with(density) { g.barRight.toDp() }), contentAlignment = Alignment.TopEnd) {
            GbaText("${mon.hp}/${mon.maxHp}", text, textShadow, m)
        }
    }
}

/** Pixel-font metrics at [fp] whole screen pixels per font pixel. */
private fun slotTextMetrics(density: Density, fp: Int) = with(density) {
    GbaTextMetrics(fp, fp.toDp(), (16f * fp).toSp(), fp.toFloat(), (16f * fp).toDp(), (20f * fp).toDp())
}

@Composable
private fun StatusChip(status: String, u: Int, m: GbaTextMetrics, modifier: Modifier) {
    val color = if (status == "FNT") Color(0xFF6B6B6B) else QolColors.statusColor(status)
    val dp = with(LocalDensity.current) { u.toDp() }
    Box(
        modifier
            .clip(PixelRoundedShape(dp * 2))
            .background(lerp(color, Color.Black, 0.35f))
            .padding(dp)
            .clip(PixelRoundedShape(dp))
            .background(color)
            .height(dp * 8)
            .padding(horizontal = dp * 3),
        contentAlignment = Alignment.Center,
    ) {
        GbaText(status, Color.White, lerp(color, Color.Black, 0.5f), m)
    }
}

private fun DrawScope.drawSlotFrame(g: SlotGeometry, c: PartySlotColors) {
    val u = g.u.toFloat()
    drawPixelRoundRect(c.outline, Offset(g.fx, g.fy), Size(g.fw, g.fh), radius = 3 * u, step = u)
    // Light inner edge, then the HP band below the divider (the edge's color on FireRed).
    drawPixelRoundRect(c.light, Offset(g.fx + 2 * u, g.fy + 2 * u), Size(g.fw - 4 * u, g.fh - 4 * u), radius = u, step = u)
    if (c.band != c.light) drawRect(c.band, Offset(g.fx + 3 * u, g.dividerY), Size(g.fw - 6 * u, g.bottom - 3 * u - g.dividerY))
    drawRect(c.fill, Offset(g.fx + 3 * u, g.fy + 3 * u), Size(g.fw - 6 * u, g.dividerY - g.fy - 3 * u))
    drawRect(c.shade, Offset(g.fx + 2 * u, g.bottom - 3 * u), Size(g.fw - 4 * u, u))
}

private fun DrawScope.drawHpBar(g: SlotGeometry, p: PartyPalette, hp: Int, maxHp: Int) {
    val u = g.u.toFloat()
    // "HP" label box, sharing its right edge with the bar's outline.
    drawPixelRoundRect(p.hpFrame, Offset(g.labelX, g.barY), Size(g.barX - g.labelX + u, 7 * u), radius = u, step = u)
    HpLetters.forEachIndexed { row, line ->
        line.forEachIndexed { col, ch ->
            if (ch == '1') drawRect(
                if (row < 3) p.hpLabel else p.hpLabelShade,
                Offset(g.labelX + (1 + col) * u, g.barY + (1 + row) * u), Size(u, u),
            )
        }
    }
    val w = g.barRight - g.barX
    drawPixelRoundRect(p.hpFrame, Offset(g.barX, g.barY), Size(w, 7 * u), radius = 2 * u, step = u)
    drawPixelRoundRect(p.hpInner, Offset(g.barX + u, g.barY + u), Size(w - 2 * u, 5 * u), radius = u, step = u)
    val inner = w - 4 * u
    val filled = if (maxHp > 0) (floor(inner / u * hp.coerceIn(0, maxHp) / maxHp) * u).coerceAtLeast(if (hp > 0) u else 0f) else 0f
    val color = when {
        maxHp <= 0 || hp * 2 > maxHp -> p.hpGreen
        hp * 5 > maxHp -> p.hpYellow
        else -> p.hpRed
    }
    fun band(c: HpBarColors, x: Float, width: Float) {
        if (width <= 0f) return
        drawRect(c.top, Offset(x, g.barY + 2 * u), Size(width, u))
        drawRect(c.main, Offset(x, g.barY + 3 * u), Size(width, 2 * u))
    }
    band(p.hpEmpty, g.barX + 2 * u, inner)
    band(color, g.barX + 2 * u, filled)
}

/** [bitmap] rows with each character colored by [palette] (others transparent), [cell] px per pixel. */
private fun DrawScope.drawPixelBitmap(bitmap: List<String>, palette: Map<Char, Color>, x: Float, y: Float, cell: Float) {
    bitmap.forEachIndexed { row, line ->
        line.forEachIndexed { col, ch ->
            palette[ch]?.let { drawRect(it, Offset(x + col * cell, y + row * cell), Size(cell, cell)) }
        }
    }
}

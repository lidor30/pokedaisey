package com.pokedaisey.app.companion.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokedaisey.app.companion.data.GENDER_SYMBOL_FEMALE
import com.pokedaisey.app.companion.data.GENDER_SYMBOL_MALE
import com.pokedaisey.app.companion.data.MonView
import com.pokedaisey.app.companion.data.MoveView
import com.pokedaisey.app.companion.data.TypeMatchup
import com.pokedaisey.app.companion.ui.theme.QolColors

/**
 * A party member's summary, filling the PARTY tab (opened by tapping a slot):
 * name / level / gender / types / HP top-left, type matchups bottom-left,
 * the four moves on the right - in the app's OPTION-screen windows. Up/down
 * cycle through the party (wrapping), back returns to the slots; the buttons
 * are the battle screen's [PlatinumButton]s with pixel-art icons.
 *
 * Takes the whole party plus an index (not one [MonView]) so it keeps
 * following live telemetry while open. No EXP bar: the telemetry doesn't
 * export experience.
 */
@Composable
fun MonDetailScreen(
    party: List<MonView>,
    index: Int,
    onIndexChange: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val m = rememberGbaTextMetrics()
    val small = rememberGbaTextMetrics(1f)
    val gap = m.u * 3
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AnimatedContent(
            targetState = index,
            transitionSpec = {
                // Down the party slides the next one up from below, like a list.
                val down = targetState > initialState
                (fadeIn(tween(180)) + slideInVertically(tween(200)) { h -> if (down) h / 8 else -h / 8 }) togetherWith
                    (fadeOut(tween(120)) + slideOutVertically(tween(160)) { h -> if (down) -h / 8 else h / 8 })
            },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            label = "mon-detail",
        ) { i ->
            // (No early return in here: a `return@AnimatedContent` breaks
            // Compose's slot table mid-transition.)
            val mon = party.getOrNull(i)
            if (mon != null) {
                // One white window, split by separators: info over matchups
                // on the left, the moves on the right.
                OptionListWindow(m, Modifier.fillMaxSize(), fill = OptionColors.titleFill) {
                    Row(Modifier.fillMaxSize()) {
                        Column(Modifier.weight(0.5f).fillMaxHeight().padding(end = gap)) {
                            InfoSection(mon, m, small, Modifier.fillMaxWidth())
                            Separator(m, Modifier.fillMaxWidth().padding(vertical = gap))
                            MatchupSection(mon, small, Modifier.fillMaxWidth().weight(1f))
                        }
                        Separator(m, Modifier.fillMaxHeight(), vertical = true)
                        MovesSection(mon, m, small, Modifier.weight(0.5f).fillMaxHeight().padding(start = gap))
                    }
                }
            }
        }

        CompanionBackHandler(onBack = onBack)
        val canCycle = party.size > 1
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NavButton(PixelIcons.arrowUp, "Previous Pokémon", MonGreen, MonGreenDark, canCycle) {
                onIndexChange((index - 1 + party.size) % party.size)
            }
            NavButton(PixelIcons.arrowDown, "Next Pokémon", MonGreen, MonGreenDark, canCycle) {
                onIndexChange((index + 1) % party.size)
            }
            PartyStrip(party, index, onIndexChange, Modifier.weight(1f))
            NavButton(PixelIcons.back, "Back", RunBlue, RunBlueDark, enabled = true, onClick = onBack)
        }
    }
}

internal val NavButtonSize = 60.dp

@Composable
internal fun NavButton(
    icon: List<String>,
    description: String,
    fill: Color,
    frame: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    PlatinumButton(
        fill = fill, frame = frame,
        modifier = Modifier.size(NavButtonSize).semantics { contentDescription = description },
        enabled = enabled,
        contentPadding = PaddingValues(0.dp),
        onClick = onClick,
    ) { PixelIcon(icon, Color.White, Modifier.size(26.dp)) }
}

/** The party's icons between the buttons - where you are, and a tap jumps
 * there. As big as the space left between the buttons allows (up to the
 * buttons' own height). */
@Composable
private fun PartyStrip(party: List<MonView>, index: Int, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val cell = minOf(NavButtonSize, maxWidth / party.size.coerceAtLeast(1))
        Row(verticalAlignment = Alignment.CenterVertically) {
            party.forEachIndexed { i, mon ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(cell)
                        .clip(PixelRoundedShape(8.dp))
                        .background(if (i == index) OptionColors.rowSelected.copy(alpha = 0.85f) else Color.Transparent)
                        .soundClickable { onPick(i) },
                ) { SpeciesIcon(mon.iconAsset, size = cell * 0.92f) }
            }
        }
    }
}

/** A 1-GBA-pixel rule between the summary's sections, in the window frame's light grey. */
@Composable
internal fun Separator(m: GbaTextMetrics, modifier: Modifier = Modifier, vertical: Boolean = false) {
    Box(
        modifier
            .then(if (vertical) Modifier.width(m.u * 2) else Modifier.height(m.u * 2))
            .drawBehind {
                val px = m.u.toPx()
                // Dark line + light line, like the window's own inner frame.
                if (vertical) {
                    drawRect(SeparatorDark, Offset.Zero, Size(px, size.height))
                    drawRect(SeparatorLight, Offset(px, 0f), Size(px, size.height))
                } else {
                    drawRect(SeparatorDark, Offset.Zero, Size(size.width, px))
                    drawRect(SeparatorLight, Offset(0f, px), Size(size.width, px))
                }
            },
    )
}

private val SeparatorDark = Color(0xFFB8B8C0)
/** The summary screen's EXP bar blue. */
private val ExpBlue = Color(0xFF40B8F8)
private val SeparatorLight = Color(0xFFE8E8EC)

/** Name + gender, icon, level, types, status, HP - the summary's top-left block. */
@Composable
internal fun InfoSection(mon: MonView, m: GbaTextMetrics, small: GbaTextMetrics, modifier: Modifier = Modifier) {
    val u = m.u
    Box(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(u * 3)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GbaText(mon.name, OptionColors.label, OptionColors.labelShadow, m, modifier = Modifier.weight(1f, fill = false))
                GenderMark(mon.genderSymbol, m, Modifier.padding(start = u * 3))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The 32px icon at a whole multiple of the frame's GBA pixel, so it stays crisp.
                Box(
                    Modifier
                        .size(u * 36)
                        .clip(PixelRoundedShape(u * 4))
                        .background(OptionColors.listFill),
                    contentAlignment = Alignment.Center,
                ) { SpeciesIcon(mon.iconAsset, size = u * 32) }
                Spacer(Modifier.width(u * 5))
                if (!mon.isEgg) {
                    Column(verticalArrangement = Arrangement.spacedBy(u * 3)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            GbaText("Lv", OptionColors.label, OptionColors.labelShadow, small)
                            GbaText("${mon.level}", OptionColors.value, OptionColors.valueShadow, m, modifier = Modifier.padding(start = u * 2))
                        }
                        if (mon.status.isNotEmpty()) StatusBadge(mon.status)
                    }
                }
            }
            // Own row, side by side: the block keeps one height for single and dual types.
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                mon.types.filter { it.isNotEmpty() }.distinct().forEach { TypeBadge(it) }
            }
            if (!mon.isEgg) {
                SummaryBar("HP", "${mon.hp}/${mon.maxHp}", mon.hp, mon.maxHp, QolColors.hpColor(mon.hp, mon.maxHp), small)
                // Progress through the current level; the text is what's left to the next.
                mon.exp?.let { xp ->
                    val span = (xp.nextLevel - xp.levelStart).coerceAtLeast(1)
                    SummaryBar(
                        "EXP", if (mon.level >= 100) "MAX" else "NEXT ${xp.toNext}",
                        (xp.fraction * span).toInt(), span.toInt(), ExpBlue, small,
                    )
                }
            }
        }
    }
}

@Composable
internal fun GenderMark(symbol: Int, m: GbaTextMetrics, modifier: Modifier = Modifier) {
    val mark = when (symbol) {
        GENDER_SYMBOL_MALE -> PixelIcons.male to Color(0xFF3984F7)
        GENDER_SYMBOL_FEMALE -> PixelIcons.female to Color(0xFFF75A5A)
        else -> null
    }
    // About the pixel font's cap height, so it sits beside the name.
    if (mark != null) PixelIcon(mark.first, mark.second, modifier.size(m.lineHeight * 0.55f))
}

/** "HP [=====    ] 30/35" - the summary screen's labelled bar, drawn at GBA-pixel steps. */
@Composable
internal fun SummaryBar(label: String, text: String, value: Int, max: Int, color: Color, small: GbaTextMetrics) {
    val frac = if (max > 0) (value.toFloat() / max).coerceIn(0f, 1f) else 0f
    val u = small.u
    Row(verticalAlignment = Alignment.CenterVertically) {
        GbaText(label, OptionColors.value, OptionColors.valueShadow, small)
        Spacer(Modifier.width(u * 3))
        Box(
            Modifier
                .weight(1f)
                .height(u * 6)
                .drawBehind {
                    val px = u.toPx()
                    drawPixelRoundRect(OptionColors.frameDark, radius = 2 * px, step = px)
                    val inner = Size(size.width - 2 * px, size.height - 2 * px)
                    drawPixelRoundRect(Color(0xFF506858), Offset(px, px), inner, radius = px, step = px)
                    val fill = kotlin.math.floor(inner.width * frac / px) * px
                    if (fill > 0) drawPixelRoundRect(color, Offset(px, px), Size(fill, inner.height), radius = px, step = px)
                },
        )
        Spacer(Modifier.width(u * 3))
        GbaText(text, OptionColors.label, OptionColors.labelShadow, small)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MatchupSection(mon: MonView, small: GbaTextMetrics, modifier: Modifier = Modifier) {
    val sections = listOf("WEAK TO" to mon.weaknesses, "RESISTS" to mon.resistances, "IMMUNE TO" to mon.immunities)
        .filter { it.second.isNotEmpty() }
    Box(modifier) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(small.u * 3),
        ) {
            if (sections.isEmpty()) {
                GbaText("NO MATCHUP DATA", OptionColors.muted, OptionColors.mutedShadow, small)
            }
            sections.forEach { (heading, list) -> MatchupGroup(heading, list, small) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MatchupGroup(heading: String, list: List<TypeMatchup>, small: GbaTextMetrics) {
    Column(verticalArrangement = Arrangement.spacedBy(small.u * 2)) {
        GbaText(heading, OptionColors.label, OptionColors.labelShadow, small)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            list.forEach { TypeBadgeWithLabel(it) }
        }
    }
}

/** The four moves, sharing out the column's height, separated by rules. */
@Composable
private fun MovesSection(mon: MonView, m: GbaTextMetrics, small: GbaTextMetrics, modifier: Modifier = Modifier) {
    Column(modifier) {
        for (i in 0 until 4) {
            if (i > 0) Separator(m, Modifier.fillMaxWidth())
            MoveRow(mon.moves.getOrNull(i), m, small, Modifier.weight(1f).fillMaxWidth())
        }
    }
}

/** One move: a type-colored stripe, then name, type badge, power and PP. */
@Composable
private fun MoveRow(mv: MoveView?, m: GbaTextMetrics, small: GbaTextMetrics, modifier: Modifier = Modifier) {
    val u = m.u
    val edge = mv?.let { QolColors.typeColors[it.type] } ?: OptionColors.muted
    Box(
        modifier
            .drawBehind {
                val px = u.toPx()
                if (mv != null) {
                    // The type stripe down the left.
                    drawPixelRoundRect(edge, Offset(0f, 4 * px), Size(3 * px, size.height - 8 * px), radius = px, step = px)
                }
            }
            .padding(start = u * 8, top = u * 3, bottom = u * 3),
    ) {
        if (mv == null) {
            GbaText("—", OptionColors.muted, OptionColors.mutedShadow, m, modifier = Modifier.align(Alignment.CenterStart))
        } else Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
            GbaText(mv.name, OptionColors.label, OptionColors.labelShadow, m)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TypeBadge(mv.type)
                Spacer(Modifier.weight(1f))
                Stat("PWR", powerLabel(mv.power), small)
                Spacer(Modifier.width(u * 6))
                Stat("PP", "${mv.pp}", small)
            }
            // In battle: how the move fares against each foe.
            if (mv.vs.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    mv.vs.forEach { MultiplierChip(it.label, it.pct) }
                }
            }
        }
    }
}

@Composable
internal fun Stat(label: String, value: String, small: GbaTextMetrics) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        GbaText(label, OptionColors.label, OptionColors.labelShadow, small)
        Spacer(Modifier.width(small.u * 3))
        GbaText(value, OptionColors.value, OptionColors.valueShadow, small)
    }
}

/** A labelled button for the summary screens' bottom row, as tall as [NavButton]. */
@Composable
internal fun TextNavButton(label: String, fill: Color, frame: Color, onClick: () -> Unit) {
    PlatinumButton(
        fill = fill, frame = frame,
        modifier = Modifier.height(NavButtonSize),
        contentPadding = PaddingValues(horizontal = 20.dp),
        onClick = onClick,
    ) { ButtonLabel(label, 24.sp, bold = true) }
}

/** The summary screens' Back button: bottom-right, blue, a U-turn arrow. */
@Composable
internal fun BackNavButton(onBack: () -> Unit) {
    NavButton(PixelIcons.back, "Back", RunBlue, RunBlueDark, enabled = true, onClick = onBack)
}

/**
 * The summary screens' frame (Pokémon details, battle INFO, SUGGESTIONS):
 * one white OPTION window filling the tab, over a bottom row of square
 * battle-style buttons - [buttons] on the left, Back (when [onBack] is set)
 * on the right.
 */
@Composable
internal fun SummaryFrame(
    m: GbaTextMetrics,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    buttons: @Composable RowScope.() -> Unit = {},
    /** Fills the space between [buttons] and Back (e.g. a strip of icons). */
    middle: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    if (onBack != null) CompanionBackHandler(onBack = onBack)
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OptionListWindow(m, Modifier.weight(1f).fillMaxWidth(), fill = OptionColors.titleFill) { content() }
        Row(
            Modifier.fillMaxWidth().height(NavButtonSize),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            buttons()
            if (middle != null) Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) { middle() }
            else Spacer(Modifier.weight(1f))
            if (onBack != null) BackNavButton(onBack)
        }
    }
}

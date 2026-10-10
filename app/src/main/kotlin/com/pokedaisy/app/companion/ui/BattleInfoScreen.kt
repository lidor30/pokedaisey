package com.pokedaisy.app.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokedaisy.app.companion.data.MonView
import com.pokedaisy.app.companion.data.MoveView
import com.pokedaisy.app.companion.data.TYPE_NONE
import com.pokedaisy.app.companion.data.TypeMatchup
import com.pokedaisy.app.companion.data.formatMultiplier
import com.pokedaisy.app.companion.data.typeIdOf
import com.pokedaisy.app.companion.data.typeMultiplierPct
import com.pokedaisy.app.companion.i18n.tk
import com.pokedaisy.app.companion.i18n.tr
import com.pokedaisy.app.companion.ui.theme.LocalGameFont
import com.pokedaisy.app.companion.ui.theme.QolColors
import com.pokedaisy.app.companion.ui.theme.pixelFontFamily

/**
 * The BATTLE tab's INFO pane, laid out around the questions a player has
 * mid-battle, most important first:
 *
 *  1. Who's out, and how healthy? - a compact card per battler across the top
 *     (YOU left, FOE right): icon, name, level, status, types, HP.
 *  2. Which of my moves should I use? - YOUR MOVES, bottom-left, in the
 *     Pokémon summary's move-row look, each with a plain-words verdict against
 *     the foe (SUPER 2x / RESISTED ½x / NEUTRAL 1x / IMMUNE 0x ...).
 *  3. What hits the foe hard, and what does it threaten me with? - bottom-right:
 *     the foe's WEAK TO / RESISTS / IMMUNE TO, then how its own types land on
 *     my Pokémon.
 *
 * Everything fits the window without scrolling in a single battle (doubles
 * scroll). With hints off (SETTINGS), the verdicts and the matchup column
 * are hidden and the moves take the full width. Touch control is
 * [BattleControlsScreen]; [onBack] null = no way back (it isn't the player's
 * turn, so there are no controls to go back to).
 *
 * In a trainer battle the bottom bar carries the FOE TEAM ([foeTeam], the
 * trainer's whole party in order) between SUGGESTIONS and Back, the way the
 * Pokémon summary carries the party strip: Pokémon already sent out or
 * announced ([seenFoes], party indexes) show, the rest are Poké Balls until
 * tapped; fainted ones are faded. The foe the screen is about ([shownFoe],
 * framed in red) is the one out, the one about to come in, or whichever slot
 * was tapped ([onSelectFoe]; tapping it again goes back). [foeHeading] says which.
 * [onShowStats] (SETTINGS > FOE IVS, when the foe's IVs were read) adds STATS
 * beside SUGGESTIONS: [BattleStatsScreen].
 */
@Composable
fun BattleInfoScreen(
    player: List<MonView>,
    opponent: List<MonView>,
    isDouble: Boolean,
    showHints: Boolean,
    onBack: (() -> Unit)?,
    onShowSuggestions: () -> Unit,
    modifier: Modifier = Modifier,
    foeTeam: List<MonView> = emptyList(),
    seenFoes: Set<Int> = emptySet(),
    shownFoe: Int = -1,
    foeHeading: String = "FOE",
    onSelectFoe: (Int) -> Unit = {},
    onShowStats: (() -> Unit)? = null,
) {
    // Sized to fit a whole battle: a game font keeps Pixel Operator's metrics here.
    val m = rememberGbaTextMetrics(gameScaled = false)
    val small = rememberGbaTextMetrics(1f, gameScaled = false)
    val gap = m.u * 3
    SummaryFrame(
        m, onBack, modifier,
        buttons = {
            TextNavButton(tr("SUGGESTIONS"), SuggestGold, SuggestGoldDark, onShowSuggestions)
            if (onShowStats != null) TextNavButton(tr("STATS"), StatsPurple, StatsPurpleDark, onShowStats)
        },
        middle = if (foeTeam.size > 1) ({ FoeTeamStrip(foeTeam, seenFoes, shownFoe, small, onSelectFoe) }) else null,
    ) {
        Column(Modifier.fillMaxSize()) {
            // 1. Who's out.
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                BattlerCards(tk("YOU"), player, m, small, Modifier.weight(1f).padding(end = gap),
                    threats = if (showHints) opponent else emptyList())
                Separator(m, Modifier.fillMaxHeight(), vertical = true)
                BattlerCards(foeHeading, opponent, m, small, Modifier.weight(1f).padding(start = gap))
            }
            Separator(m, Modifier.fillMaxWidth().padding(vertical = m.u * 2))
            // 2 + 3. What to do about it.
            Row(Modifier.fillMaxWidth().weight(1f)) {
                MovesColumn(player, opponent.size, showHints, m, small, Modifier.weight(1f).fillMaxHeight().padding(end = gap))
                if (showHints) {
                    Separator(m, Modifier.fillMaxHeight(), vertical = true)
                    FoeMatchupColumn(opponent, m, small, Modifier.weight(1f).fillMaxHeight().padding(start = gap))
                }
            }
        }
    }
}

/**
 * A card per battler (two in a double battle). [heading] is "SIDE" or
 * "SIDE · STATE" (English ids, translated here): the side goes above the
 * first icon, the state (e.g. NEXT) in a pill beside the name - so neither
 * costs a line.
 */
@Composable
private fun BattlerCards(
    heading: String,
    mons: List<MonView>,
    m: GbaTextMetrics,
    small: GbaTextMetrics,
    modifier: Modifier,
    /** The other side's Pokémon, for the [ThreatPill] (player side, hints on). */
    threats: List<MonView> = emptyList(),
) {
    val side = heading.substringBefore(" · ")
    val state = heading.substringAfter(" · ", "")
    Column(modifier, verticalArrangement = Arrangement.spacedBy(m.u * 3)) {
        if (mons.isEmpty()) GbaText(tr(side), OptionColors.value, OptionColors.valueShadow, small)
        mons.forEachIndexed { i, mon ->
            BattlerCard(mon, compact = mons.size > 1, if (i == 0) side else null, state, m, small, threats)
        }
    }
}

/** [side] over the icon; name / gender / status, then types / level / a pill, then HP.
 * [side] and [state] are English ids ("FOE", "1ST", "NEXT", "OUT"), translated here. */
@Composable
internal fun BattlerCard(
    mon: MonView,
    compact: Boolean,
    side: String?,
    state: String,
    m: GbaTextMetrics,
    small: GbaTextMetrics,
    threats: List<MonView>,
) {
    val u = m.u
    val icon = if (compact) 22 else 36
    Row(verticalAlignment = Alignment.Bottom) {
        // At least the icon's width; wider when the side's word is (GEGNER, ENEMIGO).
        Column(Modifier.widthIn(min = u * icon)) {
            if (side != null) GbaText(tr(side), OptionColors.value, OptionColors.valueShadow, small, maxLines = 1)
            Box(
                Modifier.size(u * icon).clip(PixelRoundedShape(u * 4)).background(OptionColors.listFill),
                contentAlignment = Alignment.Center,
            ) { SpeciesIcon(mon.iconAsset, size = u * (icon - 4)) }
        }
        Spacer(Modifier.width(u * 5))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(u * 2)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The name takes what the state pill leaves.
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    GbaText(mon.name, OptionColors.label, OptionColors.labelShadow, if (compact) small else m, modifier = Modifier.weight(1f, fill = false))
                    GenderMark(mon.genderSymbol, if (compact) small else m, Modifier.padding(start = u * 2))
                    if (compact) {
                        Spacer(Modifier.width(u * 4))
                        Stat(tr("Lv"), "${mon.level}", small)
                    }
                    if (mon.status.isNotEmpty()) StatusBadge(mon.status, Modifier.padding(start = u * 4))
                }
                if (!compact && state.isNotEmpty()) VerdictPill(tr(state), VerdictGrey)
            }
            if (!compact) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    mon.types.filter { it.isNotEmpty() }.distinct().forEach { TypeBadge(it) }
                    Spacer(Modifier.width(u * 2))
                    Stat(tr("Lv"), "${mon.level}", small)
                    Spacer(Modifier.weight(1f))
                    ThreatPill(mon, threats)
                }
            }
            SummaryBar(tr("HP"), "${mon.hp}/${mon.maxHp}", mon.hp, mon.maxHp, QolColors.hpColor(mon.hp, mon.maxHp), small)
        }
    }
}

/** YOUR MOVES: the summary's move rows, one per move, sharing out the height. */
@Composable
private fun MovesColumn(player: List<MonView>, foeCount: Int, showHints: Boolean, m: GbaTextMetrics, small: GbaTextMetrics, modifier: Modifier) {
    val single = player.size <= 1
    Column(if (single) modifier else modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(if (single) 0.dp else m.u * 2)) {
        if (player.isEmpty()) GbaText("-", OptionColors.muted, OptionColors.mutedShadow, small)
        player.forEach { mon ->
            // (A single battle's moves need no heading - the verdicts say whose they are.)
            if (!single) GbaText(tr("{0}'S MOVES", mon.name), OptionColors.value, OptionColors.valueShadow, small)
            if (mon.moves.isEmpty()) GbaText(tr("NO MOVE DATA"), OptionColors.muted, OptionColors.mutedShadow, small)
            val rowMod = if (single) Modifier.weight(1f).fillMaxWidth() else Modifier.fillMaxWidth().height(m.u * 56)
            for (i in 0 until if (single) 4 else mon.moves.size) {
                BattleMoveRow(mon.moves.getOrNull(i), showHints, foeCount, m, small, rowMod)
            }
        }
    }
}

/** A type-colored stripe, the name with its verdict vs the foe, then type / power / PP. */
@Composable
internal fun BattleMoveRow(mv: MoveView?, showHints: Boolean, foeCount: Int, m: GbaTextMetrics, small: GbaTextMetrics, modifier: Modifier) {
    val u = m.u
    val edge = mv?.let { QolColors.typeColors[it.type] } ?: OptionColors.muted
    // Out of PP: still listed, but faded - it can't be picked.
    Box(
        modifier
            .alpha(if (mv != null && mv.pp == 0) 0.45f else 1f)
            .drawBehind {
                val px = u.toPx()
                if (mv != null) drawPixelRoundRect(edge, Offset(0f, 2 * px), Size(3 * px, size.height - 4 * px), radius = px, step = px)
            }
            .padding(start = u * 8),
    ) {
        if (mv == null) {
            GbaText("—", OptionColors.muted, OptionColors.mutedShadow, small, modifier = Modifier.align(Alignment.CenterStart))
        } else Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
            // A fixed-width game font (Gen 1's 8px letters: THUNDERBOLT is 11 of them) gets
            // the whole line for the name; the verdict moves down beside the type.
            val wideFont = LocalGameFont.current != null
            Row(verticalAlignment = Alignment.CenterVertically) {
                GbaText(mv.name, OptionColors.label, OptionColors.labelShadow, m, Modifier.weight(1f))
                if (!wideFont && showHints && foeCount <= 1) MoveVerdict(mv, foeCount)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TypeBadge(mv.type)
                if (wideFont && showHints && foeCount <= 1) {
                    Spacer(Modifier.width(u * 4))
                    MoveVerdict(mv, foeCount, short = true)
                }
                Spacer(Modifier.weight(1f))
                Stat(tr("PWR"), powerLabel(mv.power), small)
                Spacer(Modifier.width(u * 6))
                Stat(tr("PP"), "${mv.pp}", small)
            }
            // Doubles: a chip per foe needs a line of its own.
            if (showHints && foeCount > 1) Row { Spacer(Modifier.weight(1f)); MoveVerdict(mv, foeCount) }
        }
    }
}

/** How [mv] fares: one plain-words pill against a single foe, a "NAME 2x"
 * chip per foe in a double battle. Status moves ignore the type chart. */
@Composable
private fun MoveVerdict(mv: MoveView, foeCount: Int, short: Boolean = false) {
    when {
        mv.pp == 0 -> VerdictPill(tr("NO PP"), VerdictGrey)
        mv.power == 0 -> if (!short) VerdictPill(tr("STATUS"), VerdictGrey)   // short: its "PWR -" says it
        mv.vs.isEmpty() -> {}
        foeCount > 1 || mv.vs.size > 1 -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            mv.vs.forEach { v -> VerdictPill("${v.vsName} ${v.label}", verdictColor(v.pct)) }
        }
        // Just the multiplier, where the line is short (its colour still says which).
        short -> mv.vs[0].let { v -> VerdictPill(v.label, verdictColor(v.pct)) }
        else -> mv.vs[0].let { v ->
            val words = when {
                v.pct == 0 -> tr("IMMUNE {0}", v.label)
                v.pct > 100 -> tr("SUPER {0}", v.label)
                v.pct < 100 -> tr("RESISTED {0}", v.label)
                else -> tr("NEUTRAL {0}", v.label)
            }
            VerdictPill(words, verdictColor(v.pct))
        }
    }
}

@Composable
internal fun VerdictPill(text: String, color: Color) {
    Box(Modifier.clip(PixelCornerShape(3.dp)).background(color).padding(horizontal = 6.dp, vertical = 3.dp)) {
        Text(text, color = Color.White, fontSize = 12.sp, fontFamily = pixelFontFamily(), maxLines = 1, softWrap = false)
    }
}

/** Darker than [QolColors.multiplierColor]'s bar colors, so white text reads on them. */
private fun verdictColor(pct: Int): Color = when {
    pct == 0 -> Color(0xFF585048)
    pct > 100 -> Color(0xFF389830)
    pct < 100 -> Color(0xFFD04030)
    else -> VerdictGrey
}

internal val VerdictGrey = Color(0xFF8C8C94)
/** Battle INFO's STATS button. */
internal val StatsPurple = Color(0xFF9070D0)
internal val StatsPurpleDark = Color(0xFF5C4490)

/** The foe's defensive chart: what to hit it with (strongest first), and what
 * not to. Scrolls when it's long (e.g. a Steel type's resistances), with a fade
 * at the bottom edge while there's more below. */
@Composable
private fun FoeMatchupColumn(opponent: List<MonView>, m: GbaTextMetrics, small: GbaTextMetrics, modifier: Modifier) {
    val scroll = rememberScrollState()
    Box(modifier) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(m.u * 3)) {
            opponent.forEachIndexed { i, foe ->
                if (i > 0) Separator(m, Modifier.fillMaxWidth())
                GbaText(tr("{0} IS", foe.name), OptionColors.value, OptionColors.valueShadow, small)
                val groups = listOf(
                    tr("WEAK TO") to foe.weaknesses.sortedByDescending { it.pct },
                    tr("RESISTS") to foe.resistances.sortedBy { it.pct },
                    tr("IMMUNE TO") to foe.immunities,
                ).filter { it.second.isNotEmpty() }
                if (groups.isEmpty()) GbaText(tr("NO MATCHUP DATA"), OptionColors.muted, OptionColors.mutedShadow, small)
                groups.forEach { (label, list) -> MatchupGroup(label, list, small) }
            }
        }
        if (scroll.canScrollForward) {
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(m.u * 12)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, OptionColors.titleFill))),
            )
        }
    }
}

/**
 * What the foe's own types do to [mon] - foe moves aren't exported, so its
 * types (its likely same-type attacks) are the best guess: "ROCK 2x" in
 * red when one lands super effectively, "RESISTS" in green when
 * every one is resisted, nothing when it's neutral.
 */
@Composable
internal fun ThreatPill(mon: MonView, threats: List<MonView>) {
    val hits = remember(mon.types, threats) { threats.flatMap { typesVs(it.types, mon.types) } }
    if (hits.isEmpty()) return
    val worst = hits.maxBy { it.pct }
    when {
        worst.pct > 100 -> VerdictPill("${com.pokedaisy.app.companion.data.typeLabel(worst.type)} ${worst.label}", verdictColor(50))
        worst.pct < 100 -> VerdictPill(tr("RESISTS"), verdictColor(200))
    }
}

/** Each of [attackers]' types against a Pokémon of [defenders]' types. */
private fun typesVs(attackers: List<String>, defenders: List<String>): List<TypeMatchup> {
    val d = defenders.map(::typeIdOf)
    val d1 = d.getOrElse(0) { TYPE_NONE }
    if (d1 == TYPE_NONE) return emptyList()
    val d2 = d.getOrElse(1) { TYPE_NONE }
    return attackers.filter { it.isNotEmpty() }.distinct().mapNotNull { name ->
        val atk = typeIdOf(name).takeIf { it != TYPE_NONE } ?: return@mapNotNull null
        val pct = typeMultiplierPct(atk, d1, d2)
        TypeMatchup(name, formatMultiplier(pct), pct)
    }
}

/**
 * The foe's party in the bottom bar, as big as the space between the buttons
 * allows (up to the buttons' own height) - like the summary's party strip.
 */
@Composable
internal fun FoeTeamStrip(
    team: List<MonView>,
    seen: Set<Int>,
    shown: Int,
    small: GbaTextMetrics,
    onSelect: (Int) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val cell = minOf(NavButtonSize, (maxWidth - 4.dp * (team.size - 1)) / team.size)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            team.forEachIndexed { i, mon ->
                FoeSlot(mon, i in seen || i == shown, i == shown, small, cell, { onSelect(i) })
            }
        }
    }
}

/** One of the foe's party: its icon and level once seen (faded when fainted), else a
 * Poké Ball; [current] (the one the screen shows) gets a red frame. */
@Composable
private fun FoeSlot(mon: MonView, shown: Boolean, current: Boolean, small: GbaTextMetrics, cell: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    val u = small.u
    val noRipple = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(cell)
            .drawBehind {
                val px = u.toPx()
                val frame = if (current) listOf(OptionColors.value to 2 * px, OptionColors.valueShadow to px) else OptionColors.titleLayers.inPx(px)
                drawLayeredBox(frame, if (shown) OptionColors.titleFill else OptionColors.listFill, radius = 3 * px)
            }
            .soundClickable(interactionSource = noRipple, indication = null, onClick = onClick)
            .semantics { contentDescription = if (shown) tr("{0} Lv {1}", mon.name, mon.level) else tr("Unknown foe Pokémon") },
    ) {
        if (shown) {
            SpeciesIcon(mon.iconAsset, size = cell * 0.85f, modifier = Modifier.alpha(if (mon.hp == 0) 0.35f else 1f))
        } else {
            PixelArt(PixelIcons.pokeBall, caughtBall, Modifier.size(cell * 0.5f))
        }
    }
}

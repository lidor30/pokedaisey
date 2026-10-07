package com.pokedaisy.app.companion.ui

import com.pokedaisy.app.companion.i18n.tk
import com.pokedaisy.app.companion.i18n.tr
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.EnterTransition
import androidx.compose.ui.platform.testTag
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.key
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import kotlinx.coroutines.delay
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokedaisy.app.companion.data.BATTLE_INPUT_ACTION_SELECT
import com.pokedaisy.app.companion.data.BATTLE_INPUT_BAG_OPEN
import com.pokedaisy.app.companion.data.BATTLE_INPUT_MOVE_SELECT
import com.pokedaisy.app.companion.data.BATTLE_INPUT_PARTY_OPEN
import com.pokedaisy.app.companion.data.BATTLE_INPUT_TARGET_SELECT
import com.pokedaisy.app.companion.data.GameKind
import com.pokedaisy.app.companion.data.SnapshotView
import com.pokedaisy.app.companion.data.showsBattle
import com.pokedaisy.app.companion.data.activeGame
import com.pokedaisy.app.companion.data.withMovesVs
import com.pokedaisy.app.companion.COMPANION_TABS
import com.pokedaisy.app.companion.DEFAULT_COMPANION_TABS
import com.pokedaisy.app.companion.MAX_BAR_TABS
import com.pokedaisy.app.companion.ui.theme.QolColors
import com.pokedaisy.app.companion.ui.theme.QolTheme
import com.pokedaisy.app.companion.ui.theme.LocalGameFont
import com.pokedaisy.app.companion.ui.theme.rememberGameFont

/** The whole bottom-screen companion, driven by a single [SnapshotView]. */
@Composable
fun CompanionScreen(
    snapshot: SnapshotView,
    slots: com.pokedaisy.app.companion.StateSlots? = null,
    settings: com.pokedaisy.app.companion.CompanionSettings? = null,
    battleInput: com.pokedaisy.app.companion.BattleInput? = null,
    /** Tab shown first ("PARTY", "DEX", "MAP", "ITEMS", "CARD", "STATES", "SETTINGS") - for
     * screenshot tests, which can't tap the tab bar. */
    initialTab: String = "PARTY",
    /** Party index whose summary is open on the PARTY tab - for screenshot tests too. */
    initialMonIndex: Int? = null,
    /** The CARD tab opens on the card's back - for screenshot tests too. */
    initialCardBack: Boolean = false,
    /** The device's BACK, fed by the game's activity (see [CompanionBack]). */
    back: CompanionBack? = null,
    /** Plays the game's click, for every companion button (see [LocalClickSound]). */
    clickSound: () -> Unit = {},
    /** RetroAchievements: the ACHIEVEMENTS tab and the unlock popups. Null = no such tab. */
    achievements: com.pokedaisy.app.companion.CompanionAchievements? = null,
    /** A popup shown from the start, without timers - for screenshot tests. */
    initialPopup: com.pokedaisy.app.companion.AchievementPopup? = null,
) {
    // Everything below is keyed on the snapshot's game: the per-game look
    // (backdrops, party slots, bag) reads the plain `activeGame` global, which
    // Compose can't observe - without the key a freshly launched game kept the
    // previous game's backdrop until something else happened to recompose.
    CompositionLocalProvider(
        LocalCompanionBack provides back, LocalClickSound provides clickSound, LocalGameFont provides rememberGameFont(snapshot.game),
    ) { QolTheme { key(snapshot.game) {
        val game = snapshot.game
        // Until the first real data arrives (the ROM takes a few seconds to
        // boot / be detected) the data tabs show a loading animation.
        val ready = snapshot.connected && game != null &&
            (snapshot.party.isNotEmpty() || snapshot.location.mapSecName.isNotEmpty())
        var loaded by remember { mutableStateOf(ready) }
        LaunchedEffect(ready) { if (ready) loaded = true }
        // The PARTY tab's open summary (MonDetailScreen), by party index so it
        // follows live data; null = the party slots.
        var detailIndex by remember { mutableStateOf(initialMonIndex) }
        // Selection is tracked by label, not index: the BATTLE tab appears/disappears
        // and would otherwise shift every other tab's index under the selection.
        var selectedLabel by remember { mutableStateOf(initialTab) }
        // BATTLE tab: touch Controls (Gen 4 style) vs. info-dense Panel — see
        // BattleControlsScreen.kt. Resets to Controls each time the BATTLE tab
        // becomes available (a fresh battle), matching selectedLabel's own reset.
        var battleShowControls by remember { mutableStateOf(true) }
        // BATTLE tab's SUGGESTIONS pane - see SuggestionsScreen.kt. A plain
        // flag rather than another battleInputState-driven sub-screen since
        // it's pure information (ranks the whole party, not just whoever's
        // currently sent out) and isn't gated by whose turn it is the way
        // Controls/Info are.
        var showSuggestions by remember { mutableStateOf(false) }
        // BATTLE INFO's STATS page (FOE IVS on): IVs / EVs of the battlers.
        var showBattleStats by remember { mutableStateOf(false) }
        // BATTLE INFO's FOE TEAM: the party indexes sent out or announced so
        // far this battle, and the slot tapped (null = follow the battle).
        val seenFoes = remember { androidx.compose.runtime.mutableStateListOf<Int>() }
        var selectedFoe by remember { mutableStateOf<Int?>(null) }
        LaunchedEffect(snapshot.enemyActive, snapshot.enemyNext) {
            listOf(snapshot.enemyActive, snapshot.enemyNext).filter { it >= 0 && it !in seenFoes }.forEach { seenFoes.add(it) }
            // The trainer picked its next Pokémon: show that one.
            if (snapshot.enemyNext >= 0) selectedFoe = null
        }
        // The foe INFO and SUGGESTIONS are about: the tapped one, else the one
        // about to come in, else the one that's out.
        val shownFoe = selectedFoe ?: snapshot.enemyNext.takeIf { it >= 0 } ?: snapshot.enemyActive
        val shownFoeMon = snapshot.enemyParty.getOrNull(shownFoe)?.takeIf { shownFoe != snapshot.enemyActive }
        // English ids ("SIDE" or "SIDE · STATE"): the panes split it on " · " and
        // translate each half where they draw it.
        val foeHeading = when {
            shownFoeMon == null -> tk("FOE")
            shownFoe == snapshot.enemyNext -> "FOE · " + tk("NEXT")
            shownFoeMon.hp == 0 -> "FOE · " + tk("FAINTED")
            shownFoe in seenFoes -> "FOE · " + tk("RESERVE")
            else -> "FOE · " + tk("UNSEEN")
        }
        val infoFoes = shownFoeMon?.let { listOf(it) } ?: snapshot.battleOpponent
        val infoPlayer = shownFoeMon?.let { foe -> snapshot.battlePlayer.map { it.withMovesVs(foe) } } ?: snapshot.battlePlayer
        // DEX tab: filter / dex / scroll position / open entry, kept across tab switches.
        val dexListState = androidx.compose.foundation.lazy.rememberLazyListState()
        val dexUi = remember { DexUiState(dexListState) }
        val dex = snapshot.pokedex
        // GUIDE tab: page, reveals and scroll position, kept across tab switches.
        val guideUi = remember { GuideUiState() }
        val guide = rememberGuide(game, dex, snapshot.guideTables)
        // The GUIDE's AI notice, once per game; GO BACK returns to [backTab].
        var noticeAccepted by remember { mutableStateOf(game == null || settings?.guideNoticeAccepted(game.name) != false) }
        var backTab by remember { mutableStateOf("PARTY") }

        // The player's pick of tabs (SETTINGS > TAB BAR). Settings aren't
        // Compose state, so the SETTINGS tab reports changes back here.
        var chosenTabs by remember { mutableStateOf(settings?.companionTabs ?: DEFAULT_COMPANION_TABS) }
        // CARD: the save's card data and the card's art from the ROM.
        val card = snapshot.trainerCard
        val cardArt = rememberTrainerCardArt(card?.style)
        // Tabs this game has: DEX needs the ROM's dex tables, GUIDE a guide or evolutions.
        val available = COMPANION_TABS.filter { id ->
            when (id) {
                "DEX" -> dex != null
                "GUIDE" -> guide != null
                "CARD" -> card != null && cardArt?.style == card.style
                "ACHIEVEMENTS" -> achievements != null
                else -> true
            }
        }
        // At most MAX_BAR_TABS next to SETTINGS: in battle BATTLE leads the
        // list and takes the last chosen tab's place. (The pick is shared by
        // every game, so one with more of the chosen tabs can overflow too.)
        // No BATTLE tab for a battle that read nothing (a game whose battle memory isn't mapped).
        val battle = snapshot.showsBattle
        val chosen = available.filter { it in chosenTabs }
        val bar = chosen.take(if (battle) MAX_BAR_TABS - 1 else MAX_BAR_TABS)
        val tabs = buildList {
            if (battle) add("BATTLE")
            addAll(bar)
            add("SETTINGS")
        }
        // The rest open from SETTINGS (still tabs, just without a chip).
        // Tabs go by id ("ITEMS"); the bar shows each game's own word (companionTabLabel).
        val hiddenTabs = available.filter { it !in bar }

        // Jump to the battle helper when a battle starts, back to the tab that
        // was open before it when it ends. Not on first composition when out of
        // battle, so [initialTab] sticks.
        var seenInBattle by remember { mutableStateOf<Boolean?>(null) }
        var preBattleTab by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(battle) {
            if (seenInBattle != null || battle) {
                if (battle) {
                    if (selectedLabel != "BATTLE") preBattleTab = selectedLabel
                    selectedLabel = "BATTLE"
                } else {
                    selectedLabel = preBattleTab ?: "PARTY"
                    preBattleTab = null
                }
                if (battle) battleShowControls = true
                showSuggestions = false; showBattleStats = false
                seenFoes.clear(); selectedFoe = null
            }
            seenInBattle = battle
        }
        // Doubles' battler-id-to-position mapping isn't handled yet (see
        // PLAN.md) - touch control is single-battle only for v1.
        // "My turn" = there's something for the player to actually do right
        // now via touch - the action/move menus, or the Bag/Party screen
        // that was opened from them (so BACK stays reachable) - not the
        // busy/animation/opponent's-turn states in between.
        val isMyTurn = when (snapshot.battleInputState) {
            BATTLE_INPUT_ACTION_SELECT, BATTLE_INPUT_MOVE_SELECT,
            BATTLE_INPUT_BAG_OPEN, BATTLE_INPUT_PARTY_OPEN, BATTLE_INPUT_TARGET_SELECT -> true
            else -> false
        }
        val canUseControls = battleInput != null && !snapshot.isDoubleBattle && isMyTurn
        val showHints = settings?.showHints ?: true
        val showFoeIvs = settings?.showFoeIvs ?: false
        val current = if (selectedLabel in tabs || selectedLabel in hiddenTabs) selectedLabel else tabs[0]
        // A hidden tab was opened from SETTINGS: the gear stays lit.
        val selectedIdx = tabs.indexOf(if (current in hiddenTabs) "SETTINGS" else current)
        // BACK from a tab opened from SETTINGS returns there. Registered before
        // the tabs' own handlers, so an open summary / overlay closes first.
        CompanionBackHandler(enabled = current in hiddenTabs) {
            backTab = current
            selectedLabel = "SETTINGS"; detailIndex = null; showSuggestions = false; showBattleStats = false; dexUi.open = null
        }

        Box(modifier = Modifier.fillMaxSize().background(QolColors.bgBottom)) {
            // Per-game party-menu background, pixel-scaled to fill - behind
            // every tab (SETTINGS and STATES' OPTION-style windows included),
            // except behind a bag screen with its own backdrop (Emerald),
            // faded in while ITEMS is showing.
            // No game detected yet: the app's own stripes, not a guess.
            val backdrop = when {
                game == null -> "app"
                current == "ITEMS" && hasItemsBackdrop(game) -> "items"
                else -> "party"
            }
            Crossfade(targetState = backdrop, animationSpec = tween(200), label = "tab-backdrop") { show ->
                when {
                    game == null || show == "app" -> AppBackdrop()
                    show == "items" -> ItemsBackdrop(game, Modifier.fillMaxSize())
                    else -> GameBackdrop(game)
                }
            }

            Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                if (loaded && !snapshot.connected) {
                    ErrorBanner(snapshot.error ?: tk("no data yet"))
                    Spacer(Modifier.height(8.dp))
                }

                // One AnimatedContent for the whole tab area (not just a
                // plain `when`) so switching tabs - the most common
                // navigation in this screen - slides/crossfades instead of
                // cutting instantly. Direction follows `tabs`' own order
                // (forward = moving right along the tab bar) so it reads as
                // a coherent strip rather than a random left/right flicker.
                // An open summary is its own pane of the PARTY tab, so opening
                // and closing it animates too (a zoom rather than a slide).
                val openDetail = detailIndex?.takeIf { it in snapshot.party.indices }
                // Kept past closing, for the summary's exit animation.
                val lastDetail = remember { mutableStateOf(0) }
                openDetail?.let { lastDetail.value = it }
                val lastDexEntry = remember { mutableStateOf(1) }
                dexUi.open?.let { lastDexEntry.value = it }
                val pane = when {
                    // A ROM the companion can't read: only SETTINGS stays usable.
                    snapshot.unsupported && current != "SETTINGS" && current != "ACHIEVEMENTS" -> "UNSUPPORTED"
                    // STATES / SETTINGS / ACHIEVEMENTS work before the game's data does.
                    !loaded && current != "STATES" && current != "SETTINGS" && current != "ACHIEVEMENTS" -> "LOADING"
                    current == "PARTY" && openDetail != null -> "PARTY/DETAIL"
                    current == "DEX" && dexUi.open != null -> "DEX/DETAIL"
                    else -> current
                }
                // When the last switch was moments ago (a player tapping through tabs), cut
                // instead of animating: every pane still sliding out stays composed, and
                // a run of quick taps stacked up several full tabs at once.
                val lastSwitchNanos = remember { longArrayOf(0L) }
                // The tab area, with RetroAchievements' live indicators in its corner.
                Box(Modifier.weight(1f).fillMaxSize()) {
                    AnimatedContent(
                        targetState = pane,
                        transitionSpec = {
                            val now = System.nanoTime()
                            val rapid = now - lastSwitchNanos[0] < RAPID_SWITCH_NANOS
                            lastSwitchNanos[0] = now
                            if (rapid) {
                                EnterTransition.None togetherWith ExitTransition.None
                            } else if (targetState == "LOADING" || initialState == "LOADING") {
                                fadeIn(tween(250)) togetherWith fadeOut(tween(200))
                            } else if (targetState.substringBefore('/') == initialState.substringBefore('/')) {
                                // A tab's own summary opening / closing: a zoom rather than a slide.
                                (fadeIn(tween(200)) + scaleIn(tween(220), initialScale = 0.94f)) togetherWith
                                    (fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.94f))
                            } else {
                                val forward = tabs.indexOf(targetState.substringBefore('/')) >= tabs.indexOf(initialState.substringBefore('/'))
                                (fadeIn(tween(200)) + slideInHorizontally(tween(220)) { w -> if (forward) w / 6 else -w / 6 }) togetherWith
                                    (fadeOut(tween(150)) + slideOutHorizontally(tween(180)) { w -> if (forward) -w / 6 else w / 6 })
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        label = "tab-content",
                    ) { paneKey ->
                        val tab = paneKey.substringBefore('/')
                        // MAP and BATTLE both bypass the GbaWindow card entirely -
                        // MAP wants the full tab area for the map image itself
                        // (see MapScreen's own semi-transparent label instead of
                        // a bordered/padded frame), and BATTLE's own chunky
                        // colored buttons/cards already carry their own borders,
                        // so the cream window behind them just read as a
                        // mismatched extra layer.
                        if (paneKey == "UNSUPPORTED") {
                            UnsupportedView(snapshot.error, Modifier.fillMaxSize())
                        } else if (paneKey == "LOADING") {
                            LoadingView(snapshot.error, Modifier.fillMaxSize())
                        } else if (paneKey == "PARTY/DETAIL") {
                            // A mon's summary takes over the whole tab (back returns to the slots).
                            MonDetailScreen(
                                snapshot.party, lastDetail.value.coerceIn(0, (snapshot.party.size - 1).coerceAtLeast(0)),
                                onIndexChange = { detailIndex = it },
                                onBack = { detailIndex = null },
                            )
                        } else if (tab == "DEX" && dex != null) {
                            if (paneKey == "DEX/DETAIL") PokedexEntryScreen(dex, dexUi, lastDexEntry.value)
                            else PokedexScreen(dex, dexUi)
                        } else if (tab == "GUIDE" && guide != null) {
                            if (noticeAccepted) GuideScreen(guide, guideUi, snapshot)
                            else GuideNotice(
                                verified = guide.text?.verified != false,
                                onAccept = { game?.let { settings?.acceptGuideNotice(it.name) }; noticeAccepted = true },
                                onBack = { selectedLabel = backTab },
                            )
                        } else if (tab == "CARD" && card != null && cardArt?.style == card.style) {
                            // The game's own card fills the tab, over the backdrop.
                            TrainerCardScreen(card, cardArt, Modifier.fillMaxSize(), initialBack = initialCardBack)
                        } else if (tab == "MAP") {
                            MapScreen(snapshot, modifier = Modifier.fillMaxSize())
                        } else if (tab == "PARTY") {
                            // Party slots are the game's own boxes (or FireRed-like
                            // ones) over the party-menu backdrop - no extra window
                            // behind them, and the full tab area for bigger slots.
                            PartyScreen(snapshot.party, onMonClick = { detailIndex = snapshot.party.indexOf(it) })
                        } else if (tab == "SETTINGS") {
                            // Draws its own OPTION-screen windows, no cream card behind them.
                            CompanionSettingsScreen(
                                settings, hiddenTabs, available,
                                barChips = tabs.count { it != "SETTINGS" },
                                onOpenTab = { backTab = "SETTINGS"; selectedLabel = it },
                                onTabsChanged = { chosenTabs = it },
                            )
                        } else if (tab == "ACHIEVEMENTS") {
                            AchievementsScreen(achievements)
                        } else if (tab == "STATES") {
                            // Same: its own OPTION-style windows over the backdrop.
                            StatesScreen(slots, snapshot.frameCounter)
                        } else if (tab == "ITEMS") {
                            // Draws its own bag-screen panels (folder tabs + list
                            // window) straight over the game backdrop, like PARTY.
                            ItemsScreen(snapshot.items, modifier = Modifier.fillMaxSize())
                        } else if (tab == "BATTLE") {
                            // Three full-tab panes: the touch controls, and the
                            // INFO / SUGGESTIONS summaries they open (each with
                            // its own Back). Controls come and go with every turn
                            // change, so the crossfade is also the "battle screen
                            // showing and hiding" animation.
                            val battlePane = when {
                                showSuggestions -> "SUGGESTIONS"
                                showBattleStats -> "STATS"
                                canUseControls && battleShowControls -> "CONTROLS"
                                else -> "INFO"
                            }
                            AnimatedContent(
                                targetState = battlePane,
                                transitionSpec = {
                                    (fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.96f)) togetherWith
                                        fadeOut(tween(140))
                                },
                                modifier = Modifier.fillMaxSize(),
                                label = "battle-pane",
                            ) { p ->
                                when (p) {
                                    "CONTROLS" -> BattleControlsScreen(
                                        activeMon = snapshot.battlePlayer.getOrNull(0),
                                        party = snapshot.party,
                                        battleInput = battleInput,
                                        battleInputState = snapshot.battleInputState,
                                        modifier = Modifier.fillMaxSize(),
                                        showHints = showHints,
                                        onShowInfo = { battleShowControls = false },
                                        onShowSuggestions = { showSuggestions = true },
                                        foeParty = snapshot.enemyParty,
                                        foe = infoFoes.getOrNull(0),
                                        active = snapshot.battlePlayer,
                                    )
                                    "SUGGESTIONS" -> SuggestionsScreen(
                                        party = snapshot.party,
                                        foe = infoFoes.getOrNull(0),
                                        // Back = the battle controls, whichever pane led here.
                                        onBack = { showSuggestions = false; battleShowControls = true },
                                        onShowInfo = { showSuggestions = false; battleShowControls = false },
                                        active = snapshot.battlePlayer,
                                        foeHeading = foeHeading,
                                        foeTeam = snapshot.enemyParty,
                                        seenFoes = seenFoes.toSet(),
                                        shownFoe = shownFoe,
                                        onSelectFoe = { selectedFoe = if (it == shownFoe && selectedFoe != null) null else it },
                                    )
                                    "STATS" -> BattleStatsScreen(
                                        infoPlayer, infoFoes, foeHeading,
                                        onBack = { showBattleStats = false },
                                    )
                                    // Back only while there are controls to go back to.
                                    else -> BattleInfoScreen(
                                        infoPlayer, infoFoes, snapshot.isDoubleBattle,
                                        showHints = showHints,
                                        onBack = if (canUseControls) ({ battleShowControls = true }) else null,
                                        onShowSuggestions = { showSuggestions = true },
                                        onShowStats = if (showFoeIvs && infoFoes.any { it.stats != null }) ({ showBattleStats = true }) else null,
                                        foeTeam = snapshot.enemyParty,
                                        seenFoes = seenFoes.toSet(),
                                        shownFoe = shownFoe,
                                        foeHeading = foeHeading,
                                        // Tapping the one shown goes back to following the battle.
                                        onSelectFoe = { selectedFoe = if (it == shownFoe && selectedFoe != null) null else it },
                                    )
                                }
                            }
                        }
                    }
                    AchievementIndicators(achievements, Modifier.align(Alignment.BottomEnd).padding(8.dp))
                }

                // IntrinsicSize.Min + fillMaxHeight on every chip: the icon-only
                // SETTINGS chip stretches to the text chips' height.
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tabs.forEachIndexed { i, title ->
                        // Any tab tap (PARTY's own included) closes an open summary.
                        // BATTLE's own chip also returns to the controls.
                        val open = {
                            if (title != current) backTab = current
                            selectedLabel = title; detailIndex = null; showSuggestions = false; showBattleStats = false; dexUi.open = null
                            if (title == "BATTLE") battleShowControls = true
                        }
                        if (title == "SETTINGS") {
                            SettingsTabChip(selectedIdx == i, open)
                        } else {
                            val usable = !snapshot.unsupported || title == "ACHIEVEMENTS"
                            TabChip(companionTabLabel(title), selectedIdx == i && usable, Modifier.weight(1f).testTag("tab-$title"), enabled = usable, open)
                        }
                    }
                }
            }

            // Unlocks and the like, over every tab.
            AchievementPopupHost(achievements, Modifier.padding(top = 12.dp), initial = initialPopup)
        }
    } } }
}

/**
 * A tab id's label: the bag tab takes the game's own word from its START menu
 * (every ROM's checked headless, native-capture/menu-shots/<rom>/start.png) -
 * BAG nearly everywhere, Unbound's CUBE, R.O.W.E.'s INVENTORY. ACHIEVEMENTS
 * doesn't fit a chip: CHEEVOS, RetroAchievements' own nickname for them.
 */
internal fun companionTabLabel(id: String): String = when (id) {
    "ACHIEVEMENTS" -> tr("CHEEVOS")
    "PARTY" -> tr("PARTY")
    "DEX" -> tr("DEX")
    "MAP" -> tr("MAP")
    "GUIDE" -> tr("GUIDE")
    "CARD" -> tr("CARD")
    "STATES" -> tr("STATES")
    "BATTLE" -> tr("BATTLE")
    "SETTINGS" -> tr("SETTINGS")
    // The hacks' own words for the bag stay theirs.
    "ITEMS" -> when (activeGame) {
        GameKind.UNBOUND -> "CUBE"
        GameKind.ROWE -> "INVENTORY"
        else -> tr("BAG")
    }
    else -> id
}

/**
 * Tab text: between the list and caption sizes. The tab bar's height also
 * decides the party slots' integer pixel scale (a FireRed/Emerald slot needs
 * 57 GBA px per row for 5x), so keep it lean - a taller bar once dropped the
 * slots to 4x on the Thor.
 */
@Composable
private fun rememberTabMetrics() = rememberGbaTextMetrics(1.15f, wholePixels = false)

/** The gear chip's fixed width; SETTINGS' TOOLS row leaves the same gap so its chips line up with the bar's. */
internal val SETTINGS_CHIP_WIDTH = 52.dp

/** Tab switches closer together than this cut instead of sliding (see the tab AnimatedContent). */
private const val RAPID_SWITCH_NANOS = 300_000_000L

/** The gap between tab chips, and above the tab bar. */
internal val TAB_GAP = 4.dp

/**
 * A tab in the OPTION-screen look (see GbaMenu): framed like [OptionButton],
 * grey when idle, white with the value-red label when selected.
 */
@Composable
internal fun TabChip(label: String, selected: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val m = rememberTabMetrics()
    TabChipShell(selected, m, modifier.fillMaxHeight().alpha(if (enabled) 1f else 0.4f), onClick, enabled) {
        GbaText(
            label,
            if (selected) OptionColors.value else OptionColors.label,
            if (selected) OptionColors.valueShadow else OptionColors.labelShadow,
            m,
        )
    }
}

/** The SETTINGS tab: a fixed-width chip (narrower than the weighted text tabs)
 * showing only a pixel-art gear rather than the word "SETTINGS". Same height
 * as the text tabs (the tab row stretches it). */
@Composable
private fun SettingsTabChip(selected: Boolean, onClick: () -> Unit) {
    val m = rememberTabMetrics()
    TabChipShell(selected, m, Modifier.width(SETTINGS_CHIP_WIDTH).fillMaxHeight().semantics { contentDescription = tr("Settings") }, onClick) {
        PixelIcon(
            PixelIcons.gear,
            color = if (selected) OptionColors.value else OptionColors.label,
            modifier = Modifier.size(m.u * 20),
        )
    }
}

@Composable
private fun TabChipShell(
    selected: Boolean,
    m: GbaTextMetrics,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val u = m.u
    val noRipple = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .drawBehind {
                // Idle tabs take the list window's frame + grey; the selected
                // one the title window's white, like the OPTION screen's cursor row.
                val px = u.toPx()
                drawLayeredBox(OptionColors.titleLayers.inPx(px), if (selected) OptionColors.tabSelectedFill else OptionColors.tabIdleFill, radius = 3 * px)
            }
            .soundClickable(interactionSource = noRipple, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = u * 2, vertical = u * 6),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Every tab but SETTINGS while the running ROM is one the companion can't
 * read ([SnapshotView.unsupported]); [detail] is the reader's reason. */
@Composable
private fun UnsupportedView(detail: String?, modifier: Modifier = Modifier) {
    val m = rememberGbaTextMetrics()
    val big = rememberGbaTextMetrics(1.6f)
    val small = rememberGbaTextMetrics(1f)
    Column(modifier) {
        OptionTitleWindow(tr("NOT SUPPORTED"), m, Modifier.fillMaxWidth())
        Spacer(Modifier.height(m.u * 4))
        OptionListWindow(m, Modifier.fillMaxWidth().weight(1f)) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = m.u * 16, vertical = m.u * 8),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                GbaText(tr("THIS ROM ISN'T SUPPORTED"), OptionColors.value, OptionColors.valueShadow, big)
                Spacer(Modifier.height(m.u * 8))
                GbaText(
                    tr("The game plays normally on the top screen, but the second screen can't read its party, map, items or battles."),
                    OptionColors.label, OptionColors.labelShadow, m, maxLines = 4,
                )
                Spacer(Modifier.height(m.u * 6))
                GbaText(
                    tr("SETTINGS (the gear below) still works."),
                    OptionColors.label, OptionColors.labelShadow, m, maxLines = 2,
                )
                if (!detail.isNullOrBlank()) {
                    Spacer(Modifier.height(m.u * 10))
                    GbaText(detail, OptionColors.label.copy(alpha = 0.7f), OptionColors.labelShadow, small, maxLines = 3)
                }
            }
        }
    }
}

/** The data tabs until the first telemetry arrives: a rocking Poké Ball and
 * "LOADING...". [status] (the reader's own message) only shows up if it
 * drags on, so a normal boot stays quiet. */
@Composable
private fun LoadingView(status: String?, modifier: Modifier = Modifier) {
    val m = rememberGbaTextMetrics()
    val small = rememberGbaTextMetrics(1f)
    // The dots count up with the ball's rock (same 1.4s cycle).
    val transition = rememberInfiniteTransition(label = "loading")
    val phase by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "loading-dots",
    )
    val dots = (phase * 4).toInt().coerceAtMost(3)
    var showStatus by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(10_000); showStatus = true }
    Column(modifier, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        RockingPokeBall(Modifier.size(m.u * 36))
        Spacer(Modifier.height(m.u * 6))
        // Fixed-width dots so the word doesn't shift as they appear.
        Box {
            BackdropText(tr("LOADING") + "...", m, Modifier.alpha(0f))
            BackdropText(tr("LOADING") + ".".repeat(dots), m)
        }
        if (showStatus && !status.isNullOrBlank()) {
            Spacer(Modifier.height(m.u * 4))
            GbaText(tr(status), OptionColors.onBackdrop.copy(alpha = 0.8f), OptionColors.onBackdropShadow, small, maxLines = 3)
        }
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PixelRoundedShape(6.dp))
            .background(QolColors.windowFrame)
            .padding(2.dp)
            .clip(PixelRoundedShape(4.dp))
            .background(Color(0xFFFCEFD8))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(tr(message), color = Color(0xFF9A4A28), fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

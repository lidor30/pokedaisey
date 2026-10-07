package com.pokedaisy.app.companion.ui

import com.pokedaisy.app.companion.i18n.L10n
import com.pokedaisy.app.companion.i18n.tk
import com.pokedaisy.app.companion.i18n.tr
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.Dp
import com.pokedaisy.app.GbaControls
import com.pokedaisy.app.Hotkeys
import com.pokedaisy.app.companion.COMPANION_TABS
import com.pokedaisy.app.companion.CompanionSettings
import com.pokedaisy.app.companion.FfMode
import com.pokedaisy.app.companion.FfMusicMode
import com.pokedaisy.app.companion.MAX_BAR_TABS

private val FF_RATES = floatArrayOf(0f, 2f, 3f, 4f, 5f, 6f, 8f, 10f)
private val TOUCH_NAMES = listOf(tk("AUTO"), tk("ALWAYS"), tk("NEVER"))

// Names selectable in the tap-a-name rebind picker — every entry must resolve
// via KeyEvent.keyCodeFromString("KEYCODE_" + name), i.e. match the suffix of
// a real KEYCODE_* constant. Covers the gamepad buttons GbaControls/Hotkeys
// default to, plus the common keyboard fallbacks.
private val PICKABLE_KEYS = listOf(
    "BUTTON_A", "BUTTON_B", "BUTTON_X", "BUTTON_Y",
    "BUTTON_L1", "BUTTON_R1", "BUTTON_L2", "BUTTON_R2",
    "BUTTON_THUMBL", "BUTTON_THUMBR", "BUTTON_START", "BUTTON_SELECT",
    "DPAD_UP", "DPAD_DOWN", "DPAD_LEFT", "DPAD_RIGHT",
    "SPACE", "ENTER", "TAB", "ESCAPE", "SHIFT_LEFT", "SHIFT_RIGHT", "BACKSLASH",
    "LEFT_BRACKET", "RIGHT_BRACKET", "EQUALS", "MINUS",
    "F1", "F2", "F3", "F4",
    "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M",
    "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z",
)

private enum class Page(val title: String) { HOME(tk("OPTION")), TABS(tk("TAB BAR")), BUTTONS(tk("GAME BUTTONS")), HOTKEYS(tk("HOTKEYS")) }

/**
 * The bottom-screen SETTINGS tab, laid out like FireRed's OPTION screen over
 * the game's own party-menu backdrop: every setting is a `LABEL  VALUE` row.
 * Tapping an on/off row flips it; rows with more choices (FF SPEED, FF MODE,
 * TOUCH PAD) open a pick-one list ([OptionSelector]) instead of stepping
 * through every value. Key bindings are two sub-pages ending in CANCEL, like
 * the game's own list; rebinding is tap-a-name (see [PICKABLE_KEYS]) since
 * Screen-2 never gets physical key events to capture — chords stay a
 * top-screen-only feature.
 *
 * The tabs left out of the tab bar ([hiddenTabs], tab ids) are the TOOLS:
 * extra tab chips under the options, in the tab bar's own columns, so they
 * read as a second row of tabs that only SETTINGS has ([onOpenTab]); TAB BAR
 * picks which ones those are and reports each change through [onTabsChanged].
 * CLOSE GAME / RESTART GAME end the scrolling list. Only the tabs this
 * game has ([availableTabs], ids) count towards the bar's [MAX_BAR_TABS].
 * The options come in titled groups (fast-forward, controls, companion, screen).
 */
@Composable
fun CompanionSettingsScreen(
    settings: CompanionSettings?,
    hiddenTabs: List<String> = emptyList(),
    availableTabs: List<String> = COMPANION_TABS,
    /** How many text chips the tab bar under this shows, so TOOLS' chips match their width. */
    barChips: Int = MAX_BAR_TABS,
    onOpenTab: (String) -> Unit = {},
    onTabsChanged: (List<String>) -> Unit = {},
) {
    val m = rememberGbaTextMetrics()
    var page by remember { mutableStateOf(Page.HOME) }
    // Settings aren't Compose state: bump this after each change to redraw the values.
    var tick by remember { mutableStateOf(0) }
    // HOME starts with a group title, so its first row is index 1.
    var cursor by remember(page) { mutableStateOf(if (page == Page.HOME) 1 else 0) }
    var picking by remember { mutableStateOf<Pair<String, (String) -> Unit>?>(null) }
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    var selector by remember { mutableStateOf<Selector?>(null) }

    // The games' own spelling: "POKéMON", not "POKÉMON".
    val gameName = settings?.gameName?.uppercase()?.replace('É', 'é') ?: ""
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            val trailing = if (page == Page.TABS && settings != null) "${barCount(settings, availableTabs)}/$MAX_BAR_TABS" else gameName.ifEmpty { null }
            // A sub-page's arrow (and BACK, through it) returns to the options.
            OptionTitleWindow(page.title, m, trailing = trailing, onBack = if (page != Page.HOME) ({ page = Page.HOME }) else null) {
                Spacer(Modifier.width(m.u * 8))
                BatteryIndicator(m)
            }
            Spacer(Modifier.height(m.u * 4))
            val rows = remember(settings, page, tick, hiddenTabs, availableTabs) {
                if (settings == null) return@remember listOf(SettingRow(tk("NO GAME RUNNING"), null) {})
                when (page) {
                    Page.HOME -> homeRows(settings, barCount(settings, availableTabs), { tick++ }, { page = it }, { selector = it })
                    Page.TABS -> COMPANION_TABS.map { id ->
                        val shown = id in settings.companionTabs
                        // A tab this game doesn't have (DEX, GUIDE) keeps its setting for the games that do.
                        if (id !in availableTabs) return@map SettingRow(companionTabLabel(id), tk("N/A")) {}
                        SettingRow(companionTabLabel(id), if (shown) tk("SHOWN") else tk("HIDDEN")) {
                            val now = settings.companionTabs
                            when {
                                shown -> setTabs(settings, now - id, onTabsChanged)
                                barCount(settings, availableTabs) >= MAX_BAR_TABS -> confirm = Confirm(
                                    tk("TAB BAR IS FULL"),
                                    tr("Up to {0} tabs fit next to SETTINGS. Hide one first - hidden tabs open from SETTINGS.", MAX_BAR_TABS),
                                    tk("OK"), {},
                                )
                                else -> setTabs(settings, now + id, onTabsChanged)
                            }
                            tick++
                        }
                    } + SettingRow(tk("CANCEL"), null) { page = Page.HOME }
                    Page.BUTTONS -> GbaControls.Btn.entries.map { btn ->
                        val title = "GBA ${btn.label}"
                        SettingRow(title, keysLabel(settings.gbaControlBindings()[btn])) {
                            picking = title to { name -> settings.setGbaControlBinding(btn, name); tick++ }
                        }
                    } + SettingRow(tk("CANCEL"), null) { page = Page.HOME }
                    // Off, the binds stay as they are, greyed out, and every key goes to the game.
                    Page.HOTKEYS -> listOf(
                        SettingRow(tk("HOTKEYS"), if (settings.hotkeysEnabled) tk("ON") else tk("OFF")) {
                            settings.setHotkeysEnabled(!settings.hotkeysEnabled); tick++
                        },
                    ) + Hotkeys.Action.entries.map { action ->
                        val title = action.title
                        SettingRow(title, keysLabel(settings.hotkeyBindings()[action]), enabled = settings.hotkeysEnabled) {
                            picking = title to { name -> settings.setHotkeyBinding(action, name); tick++ }
                        }
                    } + SettingRow(tk("CANCEL"), null) { page = Page.HOME }
                }
            }
            OptionListWindow(m, Modifier.fillMaxWidth().weight(1f)) {
                // Rows share out the window's height (big touch targets) down
                // to a floor, past which the list scrolls instead.
                if (rows.any { it.header }) {
                    GroupedRows(rows, m, cursor, onClick = { i -> cursor = i; rows[i].onClick() }) {
                        // Actions, not settings: the list's last line, out of the way of the options.
                        if (page == Page.HOME && settings != null) {
                            // A summary-window line sets them apart from the last group.
                            Separator(m, Modifier.fillMaxWidth().padding(start = m.u * 4, end = m.u * 4, top = m.u * 8))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(m.u * 4),
                                modifier = Modifier.padding(start = m.u * 4, end = m.u * 4, top = m.u * 6, bottom = m.u * 4),
                            ) {
                                OptionButton(tk("CLOSE GAME"), m, modifier = Modifier.weight(1f), onClick = {
                                    confirm = Confirm(tk("CLOSE GAME?"), tr("Returns to the ROM list. Progress is saved automatically."), tk("CLOSE"), settings::closeGame)
                                })
                                OptionButton(tk("RESTART GAME"), m, emphasis = true, modifier = Modifier.weight(1f), onClick = {
                                    confirm = Confirm(
                                        tk("RESTART GAME?"),
                                        tr("Reboots the game and reloads its save from disk. Unsaved progress is lost."),
                                        tk("RESTART"), settings::restartGame,
                                    )
                                })
                            }
                        }
                    }
                } else {
                    OptionRows(
                        rows.mapIndexed { i, row -> Triple(row.label, row.value) { cursor = i; row.onClick() } },
                        m, Modifier.fillMaxSize(), selected = cursor, minRow = m.rowHeight * 1.2f,
                        valueBadge = { rows[it].badge },
                        enabled = { rows[it].enabled },
                    )
                }
            }
            // TOOLS: every tab that isn't in the tab bar, as more tab chips right
            // above it - in its columns ([barChips] a row, the gear's gap at
            // the end), so they line up as a second row of tabs.
            if (page == Page.HOME && settings != null && hiddenTabs.isNotEmpty()) {
                val columns = barChips.coerceIn(1, MAX_BAR_TABS)
                hiddenTabs.chunked(columns).forEach { line ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(TAB_GAP),
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(top = TAB_GAP * 2),
                    ) {
                        line.forEach { id -> TabChip(companionTabLabel(id), selected = false, Modifier.weight(1f)) { onOpenTab(id) } }
                        repeat(columns - line.size) { Spacer(Modifier.weight(1f)) }
                        Spacer(Modifier.width(SETTINGS_CHIP_WIDTH))
                    }
                }
            }
        }

        picking?.let { (title, onPick) ->
            KeyPicker(
                title, m,
                note = if (page == Page.HOTKEYS) tr("REPLACES THE WHOLE BINDING. CHORDS: LIBRARY SETTINGS.") else null,
                onPick = { onPick(it); picking = null },
                onDismiss = { picking = null },
            )
        }
        selector?.let { sel ->
            OptionSelector(
                sel.title, sel.options, sel.current, { it }, m,
                onPick = { sel.onPick(it); selector = null; tick++ },
                onDismiss = { selector = null },
                badge = sel.badge,
            )
        }
        confirm?.let { c ->
            OptionConfirm(
                c.title, c.message, c.confirmLabel, m,
                onConfirm = { confirm = null; c.action() }, onDismiss = { confirm = null },
            )
        }
    }
}

/** How many chips the player's pick puts in this game's tab bar. */
private fun barCount(s: CompanionSettings, available: List<String>) = s.companionTabs.count { it in available }

/** Saves [tabs] in [COMPANION_TABS] order and tells the tab bar. */
private fun setTabs(s: CompanionSettings, tabs: List<String>, changed: (List<String>) -> Unit) {
    val ordered = COMPANION_TABS.filter { it in tabs }
    s.setCompanionTabs(ordered)
    changed(ordered)
}

/** A pick-one list for a multi-choice setting; [options] are display labels. */
private class Selector(
    val title: String,
    val options: List<String>,
    val current: String,
    val badge: (String) -> String? = { null },
    val onPick: (String) -> Unit,
)

private class Confirm(val title: String, val message: String, val confirmLabel: String, val action: () -> Unit)

private fun homeRows(
    s: CompanionSettings,
    barTabs: Int,
    changed: () -> Unit,
    navigate: (Page) -> Unit,
    select: (Selector) -> Unit,
): List<SettingRow> {
    fun onOff(on: Boolean) = if (on) tk("ON") else tk("OFF")
    val rateLabels = FF_RATES.map(::rateLabel)
    return listOfNotNull(
        groupTitle(tk("FAST-FORWARD")),
        // On / off - the group's title already says fast-forward.
        SettingRow("FF", onOff(s.ffToggled)) { s.setFfToggled(!s.ffToggled); changed() },
        SettingRow(tk("FF SPEED"), rateLabel(s.ffMaxSpeed)) {
            select(Selector(tk("FF SPEED"), rateLabels, rateLabel(s.ffMaxSpeed)) { s.setFfMaxSpeed(FF_RATES[rateLabels.indexOf(it)]) })
        },
        // SMART: menus (party, bag, ... in battle too) and the region map at 1x; NORMAL: fast everywhere.
        SettingRow(tk("FF MODE"), s.ffMode.label) {
            val modes = FfMode.entries
            select(Selector(tk("FF MODE"), modes.map { it.label }, s.ffMode.label) { l -> s.setFfMode(modes.first { it.label == l }) })
        },
        // STEADY / SPED-UP / OFF (FfMusicMode's order), unfinished ones tagged ALPHA.
        SettingRow(tk("FF MUSIC"), s.ffMusicMode.label, alphaBadge(s.ffMusicMode)) {
            val modes = FfMusicMode.entries
            select(
                Selector(
                    tk("FF MUSIC"), modes.map { it.label }, s.ffMusicMode.label,
                    badge = { l -> alphaBadge(modes.first { it.label == l }) },
                ) { l -> s.setFfMusicMode(modes.first { it.label == l }) },
            )
        },
        groupTitle(tk("CONTROLS")),
        // Takes effect the next time a game is opened.
        SettingRow(tk("TOUCH PAD"), TOUCH_NAMES[s.touchControlsMode]) {
            select(Selector(tk("TOUCH PAD"), TOUCH_NAMES, TOUCH_NAMES[s.touchControlsMode]) { s.setTouchControlsMode(TOUCH_NAMES.indexOf(it)) })
        },
        SettingRow(tk("GAME BUTTONS"), null) { navigate(Page.BUTTONS) },
        SettingRow(tk("HOTKEYS"), onOff(s.hotkeysEnabled)) { navigate(Page.HOTKEYS) },
        groupTitle(tk("COMPANION")),
        // AUTO: the ROM's own language. Applies at once, both screens.
        SettingRow("LANGUAGE", L10n.settingLabel(s.appLanguage)) {
            val opts = L10n.options
            select(Selector("LANGUAGE", opts.map { it.second }, opts.first { it.first == s.appLanguage }.second) { l ->
                s.setAppLanguage(opts.first { it.second == l }.first)
            })
        },
        SettingRow(tk("TAB BAR"), tr("{0} TABS", barTabs)) { navigate(Page.TABS) },
        // Move effectiveness and the foe's weak-to/resists during battle.
        SettingRow(tk("BATTLE HINTS"), onOff(s.showHints)) { s.setShowHints(!s.showHints); changed() },
        // A STATS page in battle INFO with the foe's IVs / EVs / nature.
        SettingRow(tk("FOE IVS"), onOff(s.showFoeIvs)) { s.setShowFoeIvs(!s.showFoeIvs); changed() },
        // The game's menu click on every companion button.
        SettingRow(tk("CLICK SOUND"), onOff(s.clickSound)) { s.setClickSound(!s.clickSound); changed() },
        groupTitle(tk("SCREEN")),
        // Game, location, money, clock and battery above the game.
        SettingRow(tk("STATUS BAR"), onOff(s.statusBar)) { s.setStatusBar(!s.statusBar); changed() },
        // The game at the GBA's 3:2, or stretched to fill the top screen; flips in place.
        SettingRow(tk("ASPECT"), aspectLabel(s.stretchGame)) { s.setStretchGame(!s.stretchGame); changed() },
        // Game and companion trade screens (this companion moves with them).
        SettingRow(tk("SWAP SCREENS"), onOff(s.swapScreens)) { s.setSwapScreens(!s.swapScreens); changed() }
            .takeIf { s.hasSecondScreen },
    )
}


private fun alphaBadge(mode: FfMusicMode) = if (mode.alpha) tk("ALPHA") else null

private fun rateLabel(v: Float) = if (v <= 0f) tk("INFINITE") else "${v.toInt()}×"

private fun keyLabel(name: String) = name.replace("BUTTON_", "").replace('_', ' ')

private fun keysLabel(keys: List<String>?) =
    keys.orEmpty().joinToString(" / ") { keyLabel(it) }.ifEmpty { "-" }

@Composable
private fun KeyPicker(title: String, m: GbaTextMetrics, note: String?, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    OptionOverlay(onDismiss) {
        Column(Modifier.fillMaxSize()) {
            OptionTitleWindow(tr("BIND {0}", tr(title)), m, onBack = onDismiss)
            Spacer(Modifier.height(m.u * 4))
            OptionListWindow(m, Modifier.fillMaxWidth().weight(1f)) {
                Column {
                    note?.let {
                        GbaText(
                            it, OptionColors.label, OptionColors.labelShadow, m, maxLines = Int.MAX_VALUE,
                            modifier = Modifier.padding(horizontal = m.u * 8, vertical = m.u * 2),
                        )
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(Dp.Hairline),
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    ) {
                        items(PICKABLE_KEYS) { name ->
                            OptionLine(keyLabel(name), null, selected = false, m, height = m.rowHeight * 1.3f, divider = true) { onPick(name) }
                        }
                    }
                    OptionLine(tk("CANCEL"), null, selected = true, m, height = m.rowHeight * 1.3f, onClick = onDismiss)
                }
            }
        }
    }
}

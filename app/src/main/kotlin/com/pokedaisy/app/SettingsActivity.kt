package com.pokedaisy.app

import com.pokedaisy.app.companion.i18n.L10n
import com.pokedaisy.app.companion.i18n.tk
import com.pokedaisy.app.companion.i18n.tr
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pokedaisy.app.achievements.RetroAchievements
import com.pokedaisy.app.companion.ui.AppBackdrop
import com.pokedaisy.app.companion.ui.AspectPicker
import com.pokedaisy.app.companion.ui.aspectLabel
import com.pokedaisy.app.companion.ui.CoffeeCup
import com.pokedaisy.app.companion.ui.GbaText
import com.pokedaisy.app.companion.ui.GitHubMark
import com.pokedaisy.app.companion.ui.GoldTrophy
import com.pokedaisy.app.companion.ui.GroupedRows
import com.pokedaisy.app.companion.ui.SettingRow
import com.pokedaisy.app.companion.ui.groupTitle
import com.pokedaisy.app.companion.ui.GbaTextMetrics
import com.pokedaisy.app.companion.ui.OptionButton
import com.pokedaisy.app.companion.ui.OptionColors
import com.pokedaisy.app.companion.ui.OptionConfirm
import com.pokedaisy.app.companion.ui.OptionLine
import com.pokedaisy.app.companion.ui.OptionListWindow
import com.pokedaisy.app.companion.ui.OptionRows
import com.pokedaisy.app.companion.ui.OptionSelector
import com.pokedaisy.app.companion.FfMode
import com.pokedaisy.app.companion.ScreenFilter
import com.pokedaisy.app.companion.FfMusicMode
import com.pokedaisy.app.companion.ui.OptionTextField
import com.pokedaisy.app.companion.ui.OptionTitleWindow
import com.pokedaisy.app.companion.ui.drawLayeredBox
import com.pokedaisy.app.companion.ui.inPx
import com.pokedaisy.app.companion.ui.rememberGbaTextMetrics
import com.pokedaisy.app.companion.ui.theme.APP_THEMES
import com.pokedaisy.app.companion.ui.theme.QolColors
import com.pokedaisy.app.companion.ui.theme.QolTheme
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.pokedaisy.app.companion.ui.PixelRoundedShape
import com.pokedaisy.app.companion.ui.drawPixelRoundRect

/**
 * Settings, in the companion SETTINGS tab's OPTION-screen look: a hub of
 * `LABEL  VALUE` rows (FF speed / touch pad / theme open a pick-one list,
 * on/off rows flip in place) plus one page each for Hotkeys, Game buttons,
 * Folders and Cover art. BACK pops back to the hub; BACK on the hub leaves.
 */
class SettingsActivity : ComponentActivity() {

    private enum class Screen { HOME, HOTKEYS, CONTROLS, SHADERS, FOLDERS, COVER_ART, HIDDEN, ACHIEVEMENTS }

    private lateinit var prefs: Prefs
    private var screen by mutableStateOf(Screen.HOME)
    private var deletingFile by mutableStateOf<File?>(null)
    private var folderTick by mutableIntStateOf(0)

    private var capturing by mutableStateOf<Hotkeys.Action?>(null)
    private var capturingCtrl by mutableStateOf<GbaControls.Btn?>(null)
    private var captured by mutableStateOf<List<Int>>(emptyList())
    private val stillHeld = LinkedHashSet<Int>()
    private var revision by mutableIntStateOf(0)
    private var aspectPicker by mutableStateOf(false)
    /** This screen's width / height - the top screen's, for the ASPECT preview. */
    private var screenAspect by mutableFloatStateOf(16f / 9f)

    private val filesRoot get() = getExternalFilesDir(null) ?: filesDir

    /** The game's screen's width / height: this one, or the second screen with SWAP SCREENS on. */
    private fun gameScreenAspect(): Float =
        if (prefs.swapScreens) Screens.secondAspect(this) ?: screenAspect else screenAspect

    /** SWAP SCREENS only means something with two. */
    private val hasSecondScreen by lazy { Screens.hasSecond(this) }

    private val updates by lazy { AppUpdateFlow(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        com.pokedaisy.app.companion.i18n.L10n.apply(prefs.appLanguage, null)
        RetroAchievements.init(this)
        setContent { QolTheme { Root() } }
    }

    override fun onResume() {
        super.onResume()
        updates.onResume()
    }

    // ---- physical-key capture (Hotkeys / Game buttons screens) ----------------

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (capturing == null && capturingCtrl == null) return super.onKeyDown(keyCode, event)
        if (keyCode == KeyEvent.KEYCODE_BACK) { cancelCapture(); return true }
        if (keyCode !in captured) captured = captured + keyCode
        stillHeld.add(keyCode)
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (capturing == null && capturingCtrl == null) return super.onKeyUp(keyCode, event)
        stillHeld.remove(keyCode)
        if (stillHeld.isEmpty() && captured.isNotEmpty()) {
            capturing?.let { Hotkeys.setBinding(filesRoot, it, captured) }
            capturingCtrl?.let { GbaControls.setBinding(filesRoot, it, captured.first()) }
            revision++
            cancelCapture()
        }
        return true
    }

    private fun cancelCapture() {
        capturing = null; capturingCtrl = null; captured = emptyList(); stillHeld.clear()
    }

    // ---- shell --------------------------------------------------------------

    @Composable
    private fun Root() {
        BackHandler(enabled = screen != Screen.HOME) { cancelCapture(); screen = Screen.HOME }
        BackHandler(enabled = aspectPicker) { aspectPicker = false }
        val m = rememberGbaTextMetrics()
        val small = rememberGbaTextMetrics(textScale = 1f)

        Box(modifier = Modifier.fillMaxSize().onSizeChanged {
            if (it.width > 0 && it.height > 0) screenAspect = it.width.toFloat() / it.height
        }) {
            AppBackdrop()
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                OptionTitleWindow(
                    title = when (screen) {
                        Screen.HOME -> tk("SETTINGS")
                        Screen.HOTKEYS -> tk("HOTKEYS")
                        Screen.CONTROLS -> tk("GAME BUTTONS")
                        Screen.SHADERS -> tk("SHADERS")
                        Screen.FOLDERS -> tk("FOLDERS")
                        Screen.COVER_ART -> tk("COVER ART")
                        Screen.HIDDEN -> tk("HIDDEN GAMES")
                        Screen.ACHIEVEMENTS -> "RetroAchievements"
                    },
                    m = m,
                    onBack = {
                        cancelCapture()
                        if (screen == Screen.HOME) finish() else screen = Screen.HOME
                    },
                )
                Spacer(Modifier.height(m.u * 4))
                when (screen) {
                    Screen.HOME -> HomeScreen(m)
                    Screen.HOTKEYS -> HotkeysScreen(m, small)
                    Screen.CONTROLS -> ControlsScreen(m, small)
                    Screen.SHADERS -> ShadersScreen(m)
                    Screen.FOLDERS -> FoldersScreen(m, small)
                    Screen.COVER_ART -> CoverArtScreen(m, small)
                    Screen.HIDDEN -> HiddenScreen(m, small)
                    Screen.ACHIEVEMENTS -> AchievementsScreen(m, small)
                }
            }

            selector?.let { sel ->
                OptionSelector(
                    sel.title, sel.options, sel.current, { it }, m,
                    onPick = { sel.onPick(it); selector = null; revision++ },
                    onDismiss = { selector = null },
                    badge = sel.badge,
                )
            }

            if (aspectPicker) {
                val shot by produceState<ImageBitmap?>(null) {
                    value = withContext(Dispatchers.IO) {
                        runCatching { newestStateThumb()?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() } }.getOrNull()
                    }
                }
                AspectPicker(
                    prefs.stretchGame, gameScreenAspect(), prefs.statusBar, shot, m,
                    onPick = { prefs.stretchGame = it; aspectPicker = false; revision++ },
                    onDismiss = { aspectPicker = false },
                )
            }

            if (confirmReplaceAll) {
                OptionConfirm(
                    title = tk("REPLACE ALL COVERS?"),
                    message = tr("Fetches new art from SteamGridDB for every ROM, replacing current covers (manually set ones too). A ROM with no art found keeps its cover."),
                    confirmLabel = tk("REPLACE"),
                    m = m,
                    onDismiss = { confirmReplaceAll = false },
                    onConfirm = { confirmReplaceAll = false; startCoverSync(replace = true) },
                )
            }

            UpdateDialog(updates, m, small)

            deletingFile?.let { f ->
                OptionConfirm(
                    title = tk("DELETE FILE?"),
                    message = f.name,
                    confirmLabel = tk("DELETE"),
                    m = m,
                    onDismiss = { deletingFile = null },
                    onConfirm = { f.delete(); deletingFile = null; folderTick++ },
                )
            }
        }
    }

    /** The newest savestate thumbnail of any game (a real frame for the ASPECT preview), or null. */
    private fun newestStateThumb(): File? =
        File(filesRoot, "states").listFiles().orEmpty()
            .flatMap { it.listFiles().orEmpty().asList() }
            .filter { it.isFile && it.name.matches(Regex("ss\\d+\\.png")) }
            .maxByOrNull { it.lastModified() }

    /** A pick-one list for a multi-choice setting; [options] are display labels. */
    private class Selector(
        val title: String,
        val options: List<String>,
        val current: String,
        val badge: (String) -> String? = { null },
        val onPick: (String) -> Unit,
    )

    private var selector by mutableStateOf<Selector?>(null)

    // ---- hub --------------------------------------------------------------

    /** Like the companion's SETTINGS tab: `LABEL  VALUE` rows; on/off rows
     * flip on tap, multi-choice rows open a pick-one list, the rest open
     * their own page. */
    @Composable
    private fun HomeScreen(m: GbaTextMetrics) {
        @Suppress("UNUSED_EXPRESSION") revision
        val rateLabels = RATES.map(::rateLabel)
        val themeLabels = APP_THEMES.map { it.label.uppercase() }
        val themeIdx = APP_THEMES.indexOfFirst { it.id == prefs.appTheme }.coerceAtLeast(0)
        fun onOff(on: Boolean) = if (on) tk("ON") else tk("OFF")
        // The same groups and order as the bottom screen's SETTINGS where they overlap.
        val rows = listOfNotNull(
            groupTitle(tk("FAST-FORWARD")),
            SettingRow(tk("FF SPEED"), rateLabel(prefs.ffMaxSpeed)) {
                selector = Selector(tk("FF SPEED"), rateLabels, rateLabel(prefs.ffMaxSpeed)) {
                    prefs.ffMaxSpeed = RATES[rateLabels.indexOf(it)]
                }
            },
            // SMART: menus and the region map at 1x; NORMAL: fast everywhere.
            SettingRow(tk("FF MODE"), prefs.ffMode.label) {
                val modes = FfMode.entries
                selector = Selector(tk("FF MODE"), modes.map { it.label }, prefs.ffMode.label) { l -> prefs.ffMode = modes.first { it.label == l } }
            },
            // STEADY / SPED-UP / OFF (FfMusicMode's order), unfinished ones tagged ALPHA.
            SettingRow(tk("FF MUSIC"), prefs.ffMusicMode.label, badge = tk("ALPHA").takeIf { prefs.ffMusicMode.alpha }) {
                val modes = FfMusicMode.entries
                selector = Selector(
                    tk("FF MUSIC"), modes.map { it.label }, prefs.ffMusicMode.label,
                    badge = { l -> tk("ALPHA").takeIf { modes.first { it.label == l }.alpha } },
                ) { l -> prefs.ffMusicMode = modes.first { it.label == l } }
            },
            groupTitle(tk("CONTROLS")),
            // Takes effect the next time a game is opened.
            SettingRow(tk("TOUCH PAD"), TOUCH_NAMES[prefs.touchControlsMode]) {
                selector = Selector(tk("TOUCH PAD"), TOUCH_NAMES, TOUCH_NAMES[prefs.touchControlsMode]) {
                    prefs.touchControlsMode = TOUCH_NAMES.indexOf(it)
                }
            },
            SettingRow(tk("GAME BUTTONS"), null) { screen = Screen.CONTROLS },
            SettingRow(tk("HOTKEYS"), onOff(prefs.hotkeysEnabled)) { screen = Screen.HOTKEYS },
            groupTitle(tk("SCREEN")),
            // Game, location, money, clock and battery above the game.
            SettingRow(tk("STATUS BAR"), onOff(prefs.statusBar)) {
                prefs.statusBar = !prefs.statusBar
                revision++
            },
            // The game at 3:2 or stretched to fill the screen; picked by preview.
            SettingRow(tk("ASPECT"), aspectLabel(prefs.stretchGame)) { aspectPicker = true },
            // FILTER (LCD / SCANLINES / CRT) and GBA COLORS, on their own page.
            SettingRow(tk("SHADERS"), prefs.screenFilter.label) { screen = Screen.SHADERS },
            // Game and companion trade screens; the game picks it up on resume.
            SettingRow(tk("SWAP SCREENS"), onOff(prefs.swapScreens)) {
                prefs.swapScreens = !prefs.swapScreens
                revision++
            }.takeIf { hasSecondScreen },
            // Changes the colors immediately, everywhere — the companion screen too.
            SettingRow(tk("THEME"), themeLabels[themeIdx]) {
                selector = Selector(tk("THEME"), themeLabels, themeLabels[themeIdx]) {
                    val spec = APP_THEMES[themeLabels.indexOf(it)]
                    prefs.appTheme = spec.id
                    QolColors.applyTheme(spec)
                }
            },
            groupTitle(tk("LIBRARY")),
            SettingRow(tk("FOLDERS"), null) { screen = Screen.FOLDERS },
            SettingRow(tk("COVER ART"), onOff(prefs.steamGridDbApiKey != null || prefs.raWebApiKey != null)) { screen = Screen.COVER_ART },
            SettingRow(tk("HIDDEN GAMES"), RomFolder.hiddenRoms(this@SettingsActivity, prefs).size.takeIf { it > 0 }?.toString() ?: tk("NONE")) { screen = Screen.HIDDEN },
            groupTitle(tk("ONLINE")),
            // Signed in = on; the account's name, else OFF.
            SettingRow(
                "RetroAchievements", prefs.raUsername?.uppercase() ?: tk("OFF"), labelBadge = tk("BETA"),
                labelIcon = { GoldTrophy() },
            ) { screen = Screen.ACHIEVEMENTS },
            groupTitle(tk("APP")),
            // AUTO: the ROM's language in game, the device's here. Applies at once, both screens.
            SettingRow("LANGUAGE", L10n.settingLabel(prefs.appLanguage)) {
                val opts = L10n.options
                selector = Selector("LANGUAGE", opts.map { it.second }, opts.first { it.first == prefs.appLanguage }.second) { l ->
                    prefs.appLanguage = opts.first { it.second == l }.first
                    L10n.apply(prefs.appLanguage, null)
                }
            },
            // Tap: look for a newer release on GitHub now.
            SettingRow(tk("VERSION"), if (updates.checking) tk("CHECKING…") else BuildConfig.VERSION_NAME) { updates.check(manual = true) },
            // Back to the library, which opens the first-time setup again.
            SettingRow(tk("RUN SETUP"), null) { prefs.setupRequested = true; finish() },
        )
        Column(Modifier.fillMaxSize()) {
            OptionListWindow(m, Modifier.fillMaxWidth().weight(1f)) {
                GroupedRows(rows, m, cursor = -1, onClick = { rows[it].onClick() })
            }
            Spacer(Modifier.height(m.u * 4))
            AboutFooter(m)
        }
    }

    /** SHADERS: what the game is drawn through, like the companion's page; the game picks it up on resume. */
    @Composable
    private fun ShadersScreen(m: GbaTextMetrics) {
        @Suppress("UNUSED_EXPRESSION") revision
        val filters = ScreenFilter.entries
        val rows = listOf(
            // NONE / LCD grid / SCANLINES / CRT.
            SettingRow(tk("FILTER"), prefs.screenFilter.label) {
                selector = Selector(tk("FILTER"), filters.map { it.label }, prefs.screenFilter.label) { l ->
                    prefs.screenFilter = filters.first { it.label == l }
                }
            },
            // The colours as the GBA's own LCD showed them; stacks with any filter.
            SettingRow(tk("GBA COLORS"), if (prefs.gbaColors) tk("ON") else tk("OFF")) {
                prefs.gbaColors = !prefs.gbaColors
                revision++
            },
        )
        Column(Modifier.fillMaxWidth()) {
            OptionListWindow(m, Modifier.fillMaxWidth().weight(1f, fill = false)) {
                GroupedRows(rows, m, cursor = -1, onClick = { rows[it].onClick() })
            }
        }
    }

    /** Version, author, Buy Me a Coffee and the repo, under the settings list. */
    @Composable
    private fun AboutFooter(m: GbaTextMetrics) {
        val small = rememberGbaTextMetrics(textScale = 1f)
        OptionListWindow(m, Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = m.u * 8, vertical = m.u * 3),
            ) {
                GbaText(
                    tr("POKéDAISY {0}  ·  by {1}", BuildConfig.VERSION_NAME, "Lidor Itzhari (@lidor30)"),
                    OptionColors.label, OptionColors.labelShadow, small, modifier = Modifier.weight(1f),
                )
                FooterLink(tr("Buy me a coffee"), m, small, Modifier.padding(start = m.u * 6), { CoffeeCup() }) {
                    openUrl(COFFEE_URL)
                }
                GbaText("  ·  ", OptionColors.label, OptionColors.labelShadow, small)
                FooterLink("GitHub", m, small, icon = { GitHubMark() }) { openUrl(REPO_URL) }
            }
        }
    }

    /** A pixel [icon] then [text] in the value red, the whole of it one tap target. */
    @Composable
    private fun FooterLink(
        text: String, m: GbaTextMetrics, small: GbaTextMetrics, modifier: Modifier = Modifier,
        icon: @Composable () -> Unit, onClick: () -> Unit,
    ) {
        Row(modifier.clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(small.lineHeight)) { icon() }
            Spacer(Modifier.width(m.u * 3))
            GbaText(text, OptionColors.value, OptionColors.valueShadow, small)
        }
    }

    private fun openUrl(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { Toast.makeText(this, tr("No browser to open {0}", url), Toast.LENGTH_LONG).show() }
    }

    /** Explanatory text in its own small window, above a page's list. */
    @Composable
    private fun Hint(text: String, m: GbaTextMetrics, small: GbaTextMetrics) {
        OptionListWindow(m, Modifier.fillMaxWidth().padding(bottom = m.u * 4)) {
            GbaText(
                text, OptionColors.label, OptionColors.labelShadow, small, maxLines = Int.MAX_VALUE,
                modifier = Modifier.padding(horizontal = m.u * 6, vertical = m.u * 2),
            )
        }
    }

    // ---- RetroAchievements --------------------------------------------------

    /** Sign in / out of RetroAchievements. Only the token the server returns is
     * kept (Prefs); the password goes straight to the login call. */
    @Composable
    private fun AchievementsScreen(m: GbaTextMetrics, small: GbaTextMetrics) {
        val user by RetroAchievements.user.collectAsState()
        val signingIn by RetroAchievements.signingIn.collectAsState()
        val state by RetroAchievements.state.collectAsState()
        var name by remember { mutableStateOf(prefs.raUsername ?: "") }
        var password by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        val savedName = prefs.raUsername.takeIf { prefs.raToken != null }

        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Hint(
                tr("Earn RetroAchievements (retroachievements.org) as you play. Unlocks pop up on the second screen, and its CHEEVOS tab (under SETTINGS > TOOLS there) lists the game's set. Only ROMs RetroAchievements knows by their exact hash have achievements. Softcore only for now. Your password isn't stored: the app keeps the login token RetroAchievements sends back."),
                m, small,
            )
            OptionListWindow(m, Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = m.u * 4)) {
                    val u = user
                    if (u != null || savedName != null) {
                        GbaText(tr("SIGNED IN AS"), OptionColors.label, OptionColors.labelShadow, m)
                        GbaText((u?.displayName ?: savedName!!).uppercase(), OptionColors.value, OptionColors.valueShadow, m)
                        GbaText(
                            when {
                                u != null -> tr("{0} SOFTCORE POINTS · {1} HARDCORE POINTS", u.softcoreScore, u.score)
                                state.offline -> tr("CAN'T REACH RetroAchievements RIGHT NOW · TRIED AGAIN BY ITSELF")
                                else -> tr("CONNECTING…")
                            },
                            OptionColors.muted, OptionColors.mutedShadow, small, maxLines = 2,
                        )
                        Row(Modifier.padding(vertical = m.u * 4), horizontalArrangement = Arrangement.spacedBy(m.u * 4)) {
                            if (u == null && state.offline) OptionButton(tk("TRY AGAIN"), m, onClick = { RetroAchievements.retry() })
                            OptionButton(tk("SIGN OUT"), m, onClick = { RetroAchievements.logout(); password = ""; revision++ })
                        }
                    } else {
                        GbaText(tr("USERNAME"), OptionColors.label, OptionColors.labelShadow, m, Modifier.padding(vertical = m.u * 2))
                        OptionTextField(name, { name = it; error = null }, small, placeholder = tr("retroachievements.org username"))
                        Spacer(Modifier.height(m.u * 4))
                        GbaText(tr("PASSWORD"), OptionColors.label, OptionColors.labelShadow, m, Modifier.padding(vertical = m.u * 2))
                        OptionTextField(password, { password = it; error = null }, small, placeholder = tr("password"), password = true)
                        error?.let {
                            GbaText(it.uppercase(), OptionColors.value, OptionColors.valueShadow, small, Modifier.padding(top = m.u * 4), maxLines = 3)
                        }
                        Row(Modifier.padding(vertical = m.u * 4), horizontalArrangement = Arrangement.spacedBy(m.u * 4)) {
                            OptionButton(
                                if (signingIn) tk("SIGNING IN…") else tk("SIGN IN"), m, emphasis = true,
                                enabled = !signingIn && name.isNotBlank() && password.isNotEmpty(),
                                onClick = {
                                    error = null
                                    RetroAchievements.login(name, password) { ok, message ->
                                        runOnUiThread {
                                            if (ok) { password = ""; revision++ }
                                            else error = message ?: tr("Couldn't sign in")
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    // ---- hidden games -------------------------------------------------------

    /** Games hidden from the library (its menu's HIDE): tap one to show it again. */
    @Composable
    private fun HiddenScreen(m: GbaTextMetrics, small: GbaTextMetrics) {
        @Suppress("UNUSED_EXPRESSION") revision
        val hidden = remember(revision) { RomFolder.hiddenRoms(this@SettingsActivity, prefs) }
        Column(modifier = Modifier.fillMaxWidth()) {
            Hint(tr("Games hidden from the library with HIDE in their menu. Tap one to show it in the library again."), m, small)
            OptionListWindow(m, Modifier.fillMaxWidth().weight(1f, fill = false)) {
                if (hidden.isEmpty()) {
                    GbaText(
                        tr("NO HIDDEN GAMES"), OptionColors.muted, OptionColors.mutedShadow, m,
                        modifier = Modifier.padding(horizontal = m.u * 8, vertical = m.u * 4),
                    )
                } else {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        hidden.forEachIndexed { i, rom ->
                            OptionLine(
                                GameTitles.label(this@SettingsActivity, prefs, rom), tk("SHOW"), selected = false, m,
                                height = m.rowHeight * 1.2f, labelWeight = 0.8f, divider = i < hidden.lastIndex,
                            ) {
                                prefs.hiddenRoms = prefs.hiddenRoms - rom.absolutePath
                                revision++
                            }
                        }
                    }
                }
            }
            if (hidden.size > 1) {
                Spacer(Modifier.height(m.u * 4))
                Row {
                    Spacer(Modifier.weight(1f))
                    OptionButton(tk("SHOW ALL"), m, onClick = { prefs.hiddenRoms = emptySet(); revision++ })
                }
            }
        }
    }

    // ---- cover art (SteamGridDB) --------------------------------------------

    @Composable
    private fun CoverArtScreen(m: GbaTextMetrics, small: GbaTextMetrics) {
        var text by remember { mutableStateOf(prefs.steamGridDbApiKey ?: "") }
        var raText by remember { mutableStateOf(prefs.raWebApiKey ?: "") }
        val saved = prefs.steamGridDbApiKey != null || prefs.raWebApiKey != null

        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Hint(
                tr("Library covers are fetched once per ROM and kept on-device — nothing is bundled in the app. RetroAchievements has box art for every game with a set, SteamGridDB icons for FireRed, Emerald and the supported hacks; box art wins where there are both. Each needs your own free key: tap GET KEY, copy it from that page, paste it below and tap SAVE. Long-press a game for REPLACE COVER to pick another."),
                m, small,
            )
            OptionListWindow(m, Modifier.fillMaxWidth()) {
                Column {
                    KeyLabel(tr("{0} WEB API KEY", "RetroAchievements"), m, small) { openUrl(CoverArtSync.RA_KEY_PAGE) }
                    OptionTextField(raText, { raText = it }, small, placeholder = tr("paste key here"))
                    Spacer(Modifier.height(m.u * 4))
                    KeyLabel(tr("{0} API KEY", "SteamGridDB"), m, small) { openUrl(CoverArtSync.STEAMGRIDDB_KEY_PAGE) }
                    OptionTextField(text, { text = it }, small, placeholder = tr("paste key here"))
                    Spacer(Modifier.height(m.u * 4))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(m.u * 4)) {
                        OptionButton(
                            if (coverSync.running) tk("FETCHING…") else tk("SAVE"), m, emphasis = true, enabled = !coverSync.running,
                            onClick = {
                                prefs.steamGridDbApiKey = text
                                prefs.raWebApiKey = raText
                                revision++
                                startCoverSync(replace = false)
                            },
                        )
                        if (saved) {
                            OptionButton(
                                tk("CLEAR"), m, enabled = !coverSync.running,
                                onClick = { text = ""; raText = ""; prefs.steamGridDbApiKey = null; prefs.raWebApiKey = null; revision++ },
                            )
                            Spacer(Modifier.weight(1f))
                            OptionButton(tk("REPLACE ALL"), m, enabled = !coverSync.running, onClick = { confirmReplaceAll = true })
                        }
                    }
                }
            }

            Spacer(Modifier.height(m.u * 4))
            CoverSyncPanel(coverSync, m, small)
        }
    }

    /** A key field's title with GET KEY beside it, opening the site's page for that key. */
    @Composable
    private fun KeyLabel(label: String, m: GbaTextMetrics, small: GbaTextMetrics, onGetKey: () -> Unit) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = m.u * 4, vertical = m.u * 2),
        ) {
            GbaText(label, OptionColors.label, OptionColors.labelShadow, m)
            Spacer(Modifier.width(m.u * 6))
            OptionButton(tr("GET KEY"), small, onClick = onGetKey)
        }
    }

    // On the activity so the REPLACE ALL confirm (drawn over the whole screen from Root) can start it.
    private val coverSync = CoverSync()
    private var confirmReplaceAll by mutableStateOf(false)

    private fun startCoverSync(replace: Boolean) = coverSync.start(this, prefs, replace)

    // ---- hotkeys / game buttons -------------------------------------------

    /** One rebindable row: tap to capture (the row turns white and the value
     * reads PRESS KEYS…), tap again to cancel. */
    @Composable
    private fun CaptureRow(
        label: String, keys: List<String>, isCapturing: Boolean, prompt: String, m: GbaTextMetrics,
        divider: Boolean, enabled: Boolean = true, onClick: () -> Unit,
    ) {
        val value = when {
            isCapturing && captured.isEmpty() -> prompt
            isCapturing -> captured.joinToString(" + ") { keyLabel(Hotkeys.keyName(it)) }
            else -> keys.joinToString(" / ") { keyLabel(it) }.ifEmpty { "-" }
        }
        OptionLine(label, value, selected = isCapturing, m, height = m.rowHeight * 1.2f, labelWeight = 0.5f, divider = divider, enabled = enabled, onClick = onClick)
    }

    @Composable
    private fun HotkeysScreen(m: GbaTextMetrics, small: GbaTextMetrics) {
        val hotkeys = remember(revision) { Hotkeys.load(filesRoot) }
        Column(modifier = Modifier.fillMaxWidth()) {
            Hint(
                tr("Tap a hotkey, then press one or more keys/buttons together and release — e.g. hold Select and tap R1 for a chord. BACK cancels."),
                m, small,
            )
            OptionListWindow(m, Modifier.fillMaxWidth().weight(1f, fill = false)) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    // Off, the binds stay as they are, greyed out, and every key goes to the game.
                    val on = prefs.hotkeysEnabled
                    OptionLine(
                        tk("HOTKEYS"), if (on) tk("ON") else tk("OFF"), selected = false, m, height = m.rowHeight * 1.2f,
                        labelWeight = 0.5f, divider = true,
                    ) { cancelCapture(); prefs.hotkeysEnabled = !on; revision++ }
                    Hotkeys.Action.entries.forEachIndexed { i, action ->
                        CaptureRow(
                            action.title, hotkeys.rawBindings[action].orEmpty(),
                            isCapturing = capturing == action, prompt = tk("PRESS KEYS…"), m = m,
                            divider = i < Hotkeys.Action.entries.lastIndex, enabled = on,
                        ) {
                            if (capturing == action) cancelCapture() else { cancelCapture(); capturing = action }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ControlsScreen(m: GbaTextMetrics, small: GbaTextMetrics) {
        val ctrls = remember(revision) { GbaControls.rawBindings(filesRoot) }
        Column(modifier = Modifier.fillMaxWidth()) {
            Hint(tr("Which physical button is each GBA button. The D-pad and analog stick are fixed."), m, small)
            OptionListWindow(m, Modifier.fillMaxWidth().weight(1f, fill = false)) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    GbaControls.Btn.entries.forEachIndexed { i, btn ->
                        CaptureRow(
                            "GBA ${btn.label}", ctrls[btn].orEmpty(),
                            isCapturing = capturingCtrl == btn, prompt = tk("PRESS A BUTTON…"), m = m,
                            divider = i < GbaControls.Btn.entries.lastIndex,
                        ) {
                            if (capturingCtrl == btn) cancelCapture() else { cancelCapture(); capturingCtrl = btn }
                        }
                    }
                }
            }
        }
    }

    // ---- folders --------------------------------------------------------

    @Composable
    private fun FoldersScreen(m: GbaTextMetrics, small: GbaTextMetrics) {
        val tick = folderTick
        val savesDir = remember(tick) { SavesLocation.dir(this@SettingsActivity, prefs) }
        val statesDir = remember { File(getExternalFilesDir(null), "states").apply { mkdirs() } }
        val pickSavesDir = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            val path = pickedFolder(uri) ?: return@rememberLauncherForActivityResult
            prefs.savesDirOverride = path
            folderTick++
        }
        val pickRomsDir = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            val path = pickedFolder(uri) ?: return@rememberLauncherForActivityResult
            if (path != prefs.romsFolder) {
                prefs.romsFolder = path
                RomFolder.clearCache(this)
            }
            rescanRomsFolder()
        }

        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            RomsFolderSection(m, small, onChoose = { pickFolder(pickRomsDir) })
            Spacer(Modifier.height(m.u * 4))
            FolderSection(tr("GAME SAVES (.SAV / .SRM)"), savesDir, tick, m, small) {
                OptionButton(tk("CHANGE FOLDER"), small, onClick = { pickFolder(pickSavesDir) })
                if (prefs.savesDirOverride != null) {
                    OptionButton(tk("USE DEFAULT"), small, onClick = { prefs.savesDirOverride = null; folderTick++ })
                }
            }
            Spacer(Modifier.height(m.u * 4))
            FolderSection(tr("SAVE STATES"), statesDir, tick, m, small)
        }
    }

    /** The linked ROMs folder: no file list or delete buttons here - those are
     * the player's own files, not ours. */
    @Composable
    private fun RomsFolderSection(m: GbaTextMetrics, small: GbaTextMetrics, onChoose: () -> Unit) {
        val tick = folderTick
        val folder = prefs.romsFolder
        val found = remember(tick, folder) { RomFolder.found(this@SettingsActivity, prefs).size }
        OptionListWindow(m, Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = m.u * 4)) {
                GbaText(tr("ROMS FOLDER"), OptionColors.label, OptionColors.labelShadow, m)
                GbaText(folder ?: tr("NOT LINKED"), OptionColors.muted, OptionColors.mutedShadow, small, maxLines = 3)
                GbaText(
                    when {
                        folder == null -> tr("Link the folder you keep your games in: its supported ROMs join the library and play from there.")
                        romsScanning -> tr("LOOKING FOR GAMES…")
                        else -> tr("{0} GAME(S) IN THE LIBRARY · CHECKED FOR NEW ONES EACH TIME THE APP OPENS", found)
                    },
                    OptionColors.muted, OptionColors.mutedShadow, small, maxLines = Int.MAX_VALUE,
                )
                Row(
                    modifier = Modifier.padding(vertical = m.u * 4),
                    horizontalArrangement = Arrangement.spacedBy(m.u * 4),
                ) {
                    OptionButton(if (folder == null) tk("CHOOSE FOLDER") else tk("CHANGE FOLDER"), small, onClick = onChoose)
                    if (folder != null) {
                        OptionButton(tk("RESCAN"), small, enabled = !romsScanning, onClick = { rescanRomsFolder() })
                        OptionButton(tk("UNLINK"), small, onClick = { prefs.romsFolder = null; RomFolder.clearCache(this@SettingsActivity); folderTick++ })
                    }
                }
            }
        }
    }

    private var romsScanning by mutableStateOf(false)

    private fun rescanRomsFolder() {
        if (romsScanning) return
        romsScanning = true
        folderTick++
        Thread({
            RomFolder.scan(this, prefs)
            runOnUiThread { romsScanning = false; folderTick++ }
        }, "pokedaisy-rom-scan").apply { isDaemon = true; start() }
    }

    /** The picker's folder as a real path (persisting its grant), or null with a toast. */
    private fun pickedFolder(uri: Uri?): String? {
        if (uri == null) return null
        val path = StorageAccess.treePath(uri)
        if (path == null) {
            Toast.makeText(this, tr("Only folders on this device's own storage are supported"), Toast.LENGTH_LONG).show()
            return null
        }
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        return path
    }

    /** Needs "All files access" first (games and saves are opened by raw path,
     * which scoped storage otherwise blocks outside this app's own sandbox). */
    private fun pickFolder(launcher: androidx.activity.result.ActivityResultLauncher<Uri?>) {
        if (!StorageAccess.hasAllFilesAccess()) {
            StorageAccess.requestAllFilesAccess(this)
            Toast.makeText(this, tr("Grant \"All files access\", then tap the button again"), Toast.LENGTH_LONG).show()
            return
        }
        launcher.launch(null)
    }

    @Composable
    private fun FolderSection(
        title: String,
        dir: File,
        tick: Int,
        m: GbaTextMetrics,
        small: GbaTextMetrics,
        extraButtons: @Composable () -> Unit = {},
    ) {
        val ctx = LocalContext.current
        val files = remember(tick, dir) {
            dir.walkTopDown().filter { it.isFile }.sortedBy { it.relativeToOrSelf(dir).path }.toList()
        }
        val total = files.sumOf { it.length() }
        OptionListWindow(m, Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = m.u * 4)) {
                GbaText(title, OptionColors.label, OptionColors.labelShadow, m)
                GbaText(dir.absolutePath, OptionColors.muted, OptionColors.mutedShadow, small, maxLines = 3)
                GbaText(tr("{0} FILE(S) · {1}", files.size, humanSize(total)), OptionColors.muted, OptionColors.mutedShadow, small)
                Row(
                    modifier = Modifier.padding(vertical = m.u * 4),
                    horizontalArrangement = Arrangement.spacedBy(m.u * 4),
                ) {
                    OptionButton(tk("COPY PATH"), small, onClick = { copyPath(ctx, dir.absolutePath) })
                    extraButtons()
                }

                if (files.isEmpty()) {
                    GbaText(tr("(EMPTY)"), OptionColors.muted, OptionColors.mutedShadow, small)
                } else {
                    files.forEach { f ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = m.u * 2),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            GbaText(f.relativeToOrSelf(dir).path, OptionColors.label, OptionColors.labelShadow, small, Modifier.weight(1f))
                            GbaText(humanSize(f.length()), OptionColors.muted, OptionColors.mutedShadow, small)
                            Box(
                                modifier = Modifier.clip(PixelRoundedShape(4.dp))
                                    .clickable { deletingFile = f }.padding(6.dp),
                            ) {
                                Icon(Icons.Filled.Delete, tr("Delete {0}", f.name), tint = OptionColors.value, modifier = Modifier.size(small.lineHeight))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun copyPath(ctx: Context, path: String) {
        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("folder", path))
        Toast.makeText(ctx, tr("Path copied"), Toast.LENGTH_SHORT).show()
    }

    private companion object {
        val RATES = floatArrayOf(0f, 2f, 3f, 4f, 5f, 6f, 8f, 10f)
        val TOUCH_NAMES = listOf(tk("AUTO"), tk("ALWAYS"), tk("NEVER"))
        const val REPO_URL = "https://github.com/lidor30/pokedaisy"
        const val COFFEE_URL = "https://buymeacoffee.com/lidor30g"

        fun rateLabel(v: Float) = if (v <= 0f) tk("INFINITE") else "${v.toInt()}×"

        fun keyLabel(name: String) = name.replace("BUTTON_", "").replace('_', ' ')

        fun humanSize(bytes: Long): String = when {
            bytes >= 1 shl 20 -> "%.1f MB".format(bytes / 1048576.0)
            bytes >= 1 shl 10 -> "%.1f KB".format(bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}

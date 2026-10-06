package com.pokedaisey.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pokedaisey.app.companion.ui.AppBackdrop
import com.pokedaisey.app.companion.ui.GbaText
import com.pokedaisey.app.companion.ui.GbaTextMetrics
import com.pokedaisey.app.companion.ui.OptionButton
import com.pokedaisey.app.companion.ui.OptionColors
import com.pokedaisey.app.companion.ui.OptionConfirm
import com.pokedaisey.app.companion.ui.OptionLine
import com.pokedaisey.app.companion.ui.OptionListWindow
import com.pokedaisey.app.companion.ui.OptionRows
import com.pokedaisey.app.companion.ui.OptionSelector
import com.pokedaisey.app.companion.FfMode
import com.pokedaisey.app.companion.FfMusicMode
import com.pokedaisey.app.companion.ui.OptionTextField
import com.pokedaisey.app.companion.ui.OptionTitleWindow
import com.pokedaisey.app.companion.ui.drawLayeredBox
import com.pokedaisey.app.companion.ui.inPx
import com.pokedaisey.app.companion.ui.rememberGbaTextMetrics
import com.pokedaisey.app.companion.ui.theme.APP_THEMES
import com.pokedaisey.app.companion.ui.theme.QolColors
import com.pokedaisey.app.companion.ui.theme.QolTheme
import java.io.File
import com.pokedaisey.app.companion.ui.PixelRoundedShape
import com.pokedaisey.app.companion.ui.drawPixelRoundRect

/**
 * Settings, in the companion SETTINGS tab's OPTION-screen look: a hub of
 * `LABEL  VALUE` rows (FF speed / touch pad / theme open a pick-one list,
 * on/off rows flip in place) plus one page each for Hotkeys, Game buttons,
 * Folders and Cover art. BACK pops back to the hub; BACK on the hub leaves.
 */
class SettingsActivity : ComponentActivity() {

    private enum class Screen { HOME, HOTKEYS, CONTROLS, FOLDERS, COVER_ART, HIDDEN }

    private lateinit var prefs: Prefs
    private var screen by mutableStateOf(Screen.HOME)
    private var deletingFile by mutableStateOf<File?>(null)
    private var folderTick by mutableIntStateOf(0)

    private var capturing by mutableStateOf<Hotkeys.Action?>(null)
    private var capturingCtrl by mutableStateOf<GbaControls.Btn?>(null)
    private var captured by mutableStateOf<List<Int>>(emptyList())
    private val stillHeld = LinkedHashSet<Int>()
    private var revision by mutableIntStateOf(0)

    private val filesRoot get() = getExternalFilesDir(null) ?: filesDir

    private val updates by lazy { AppUpdateFlow(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
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
        val m = rememberGbaTextMetrics()
        val small = rememberGbaTextMetrics(textScale = 1f)

        Box(modifier = Modifier.fillMaxSize()) {
            AppBackdrop()
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                OptionTitleWindow(
                    title = when (screen) {
                        Screen.HOME -> "SETTINGS"
                        Screen.HOTKEYS -> "HOTKEYS"
                        Screen.CONTROLS -> "GAME BUTTONS"
                        Screen.FOLDERS -> "FOLDERS"
                        Screen.COVER_ART -> "COVER ART"
                        Screen.HIDDEN -> "HIDDEN GAMES"
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
                    Screen.FOLDERS -> FoldersScreen(m, small)
                    Screen.COVER_ART -> CoverArtScreen(m, small)
                    Screen.HIDDEN -> HiddenScreen(m, small)
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

            if (confirmReplaceAll) {
                OptionConfirm(
                    title = "REPLACE ALL COVERS?",
                    message = "Fetches new art from SteamGridDB for every ROM, replacing current covers (manually set " +
                        "ones too). A ROM with no art found keeps its cover.",
                    confirmLabel = "REPLACE",
                    m = m,
                    onDismiss = { confirmReplaceAll = false },
                    onConfirm = { confirmReplaceAll = false; startCoverSync(replace = true) },
                )
            }

            UpdateDialog(updates, m, small)

            deletingFile?.let { f ->
                OptionConfirm(
                    title = "DELETE FILE?",
                    message = f.name,
                    confirmLabel = "DELETE",
                    m = m,
                    onDismiss = { deletingFile = null },
                    onConfirm = { f.delete(); deletingFile = null; folderTick++ },
                )
            }
        }
    }

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
        val rows: List<Triple<String, String?, () -> Unit>> = listOf(
            Triple("FF SPEED", rateLabel(prefs.ffMaxSpeed)) {
                selector = Selector("FF SPEED", rateLabels, rateLabel(prefs.ffMaxSpeed)) {
                    prefs.ffMaxSpeed = RATES[rateLabels.indexOf(it)]
                }
            },
            // SMART: menus and the region map at 1x; NORMAL: fast everywhere.
            Triple("FF MODE", prefs.ffMode.label) {
                val modes = FfMode.entries
                selector = Selector("FF MODE", modes.map { it.label }, prefs.ffMode.label) { l -> prefs.ffMode = modes.first { it.label == l } }
            },
            // STEADY / SPED-UP / OFF (FfMusicMode's order), unfinished ones tagged ALPHA.
            Triple("FF MUSIC", prefs.ffMusicMode.label) {
                val modes = FfMusicMode.entries
                selector = Selector(
                    "FF MUSIC", modes.map { it.label }, prefs.ffMusicMode.label,
                    badge = { l -> "ALPHA".takeIf { modes.first { it.label == l }.alpha } },
                ) { l -> prefs.ffMusicMode = modes.first { it.label == l } }
            },
            // Takes effect the next time a game is opened.
            Triple("TOUCH PAD", TOUCH_NAMES[prefs.touchControlsMode]) {
                selector = Selector("TOUCH PAD", TOUCH_NAMES, TOUCH_NAMES[prefs.touchControlsMode]) {
                    prefs.touchControlsMode = TOUCH_NAMES.indexOf(it)
                }
            },
            // Game, location, money, clock and battery above the game.
            Triple("STATUS BAR", if (prefs.statusBar) "ON" else "OFF") {
                prefs.statusBar = !prefs.statusBar
                revision++
            },
            // Changes the colors immediately, everywhere — the companion screen too.
            Triple("THEME", themeLabels[themeIdx]) {
                selector = Selector("THEME", themeLabels, themeLabels[themeIdx]) {
                    val spec = APP_THEMES[themeLabels.indexOf(it)]
                    prefs.appTheme = spec.id
                    QolColors.applyTheme(spec)
                }
            },
            Triple("HOTKEYS", null) { screen = Screen.HOTKEYS },
            Triple("GAME BUTTONS", null) { screen = Screen.CONTROLS },
            Triple("FOLDERS", null) { screen = Screen.FOLDERS },
            Triple("COVER ART", if (prefs.steamGridDbApiKey != null) "ON" else "OFF") { screen = Screen.COVER_ART },
            Triple("HIDDEN GAMES", RomFolder.hiddenRoms(prefs).size.takeIf { it > 0 }?.toString() ?: "NONE") { screen = Screen.HIDDEN },
            // Tap: look for a newer release on GitHub now.
            Triple("VERSION", if (updates.checking) "CHECKING…" else BuildConfig.VERSION_NAME) { updates.check(manual = true) },
            // Back to the library, which opens the first-time setup again.
            Triple("RUN SETUP", null) { prefs.setupRequested = true; finish() },
        )
        OptionListWindow(m, Modifier.fillMaxSize()) {
            OptionRows(rows, m, Modifier.fillMaxSize(), valueBadge = { i -> "ALPHA".takeIf { rows[i].first == "FF MUSIC" && prefs.ffMusicMode.alpha } })
        }
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

    // ---- hidden games -------------------------------------------------------

    /** Games hidden from the library (its menu's HIDE): tap one to show it again. */
    @Composable
    private fun HiddenScreen(m: GbaTextMetrics, small: GbaTextMetrics) {
        @Suppress("UNUSED_EXPRESSION") revision
        val hidden = remember(revision) { RomFolder.hiddenRoms(prefs) }
        Column(modifier = Modifier.fillMaxWidth()) {
            Hint("Games hidden from the library with HIDE in their menu. Tap one to show it in the library again.", m, small)
            OptionListWindow(m, Modifier.fillMaxWidth().weight(1f, fill = false)) {
                if (hidden.isEmpty()) {
                    GbaText(
                        "NO HIDDEN GAMES", OptionColors.muted, OptionColors.mutedShadow, m,
                        modifier = Modifier.padding(horizontal = m.u * 8, vertical = m.u * 4),
                    )
                } else {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        hidden.forEachIndexed { i, rom ->
                            OptionLine(
                                prefs.romDisplayName(rom) ?: rom.nameWithoutExtension, "SHOW", selected = false, m,
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
                    OptionButton("SHOW ALL", m, onClick = { prefs.hiddenRoms = emptySet(); revision++ })
                }
            }
        }
    }

    // ---- cover art (SteamGridDB) --------------------------------------------

    @Composable
    private fun CoverArtScreen(m: GbaTextMetrics, small: GbaTextMetrics) {
        var text by remember { mutableStateOf(prefs.steamGridDbApiKey ?: "") }
        val saved = prefs.steamGridDbApiKey

        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Hint(
                "Library covers for FireRed, Emerald and supported hacks (Unbound, Gaia, " +
                    "Radical Red, Odyssey, Heart & Soul, Lazarus, R.O.W.E., Emerald Rogue, " +
                    "Seaglass) are fetched from SteamGridDB, once per ROM, and cached " +
                    "on-device — nothing is bundled in the app. Long-press a game for " +
                    "CHANGE COVER to pick another. Get a free API key at steamgriddb.com " +
                    "(account menu > Preferences > API), paste it below and tap SAVE.",
                m, small,
            )
            OptionListWindow(m, Modifier.fillMaxWidth()) {
                Column {
                    GbaText(
                        "STEAMGRIDDB API KEY", OptionColors.label, OptionColors.labelShadow, m,
                        modifier = Modifier.padding(horizontal = m.u * 4, vertical = m.u * 2),
                    )
                    OptionTextField(text, { text = it }, small, placeholder = "paste key here")
                    Spacer(Modifier.height(m.u * 4))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(m.u * 4)) {
                        OptionButton(
                            if (coverSync.running) "FETCHING…" else "SAVE", m, emphasis = true, enabled = !coverSync.running,
                            onClick = {
                                prefs.steamGridDbApiKey = text
                                revision++
                                startCoverSync(replace = false)
                            },
                        )
                        if (saved != null) {
                            OptionButton(
                                "CLEAR", m, enabled = !coverSync.running,
                                onClick = { text = ""; prefs.steamGridDbApiKey = null; revision++ },
                            )
                            Spacer(Modifier.weight(1f))
                            OptionButton("REPLACE ALL", m, enabled = !coverSync.running, onClick = { confirmReplaceAll = true })
                        }
                    }
                }
            }

            Spacer(Modifier.height(m.u * 4))
            CoverSyncPanel(coverSync, m, small)
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
        divider: Boolean, onClick: () -> Unit,
    ) {
        val value = when {
            isCapturing && captured.isEmpty() -> prompt
            isCapturing -> captured.joinToString(" + ") { keyLabel(Hotkeys.keyName(it)) }
            else -> keys.joinToString(" / ") { keyLabel(it) }.ifEmpty { "-" }
        }
        OptionLine(label, value, selected = isCapturing, m, height = m.rowHeight * 1.2f, labelWeight = 0.5f, divider = divider, onClick = onClick)
    }

    @Composable
    private fun HotkeysScreen(m: GbaTextMetrics, small: GbaTextMetrics) {
        val hotkeys = remember(revision) { Hotkeys.load(filesRoot) }
        Column(modifier = Modifier.fillMaxWidth()) {
            Hint(
                "Tap a hotkey, then press one or more keys/buttons together and release — " +
                    "e.g. hold Select and tap R1 for a chord. BACK cancels.",
                m, small,
            )
            OptionListWindow(m, Modifier.fillMaxWidth().weight(1f, fill = false)) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Hotkeys.Action.entries.forEachIndexed { i, action ->
                        CaptureRow(
                            action.label.uppercase(), hotkeys.rawBindings[action].orEmpty(),
                            isCapturing = capturing == action, prompt = "PRESS KEYS…", m = m,
                            divider = i < Hotkeys.Action.entries.lastIndex,
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
            Hint("Which physical button is each GBA button. The D-pad and analog stick are fixed.", m, small)
            OptionListWindow(m, Modifier.fillMaxWidth().weight(1f, fill = false)) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    GbaControls.Btn.entries.forEachIndexed { i, btn ->
                        CaptureRow(
                            "GBA ${btn.name}", ctrls[btn].orEmpty(),
                            isCapturing = capturingCtrl == btn, prompt = "PRESS A BUTTON…", m = m,
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
            FolderSection("GAME SAVES (.SAV / .SRM)", savesDir, tick, m, small) {
                OptionButton("CHANGE FOLDER", small, onClick = { pickFolder(pickSavesDir) })
                if (prefs.savesDirOverride != null) {
                    OptionButton("USE DEFAULT", small, onClick = { prefs.savesDirOverride = null; folderTick++ })
                }
            }
            Spacer(Modifier.height(m.u * 4))
            FolderSection("SAVE STATES", statesDir, tick, m, small)
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
                GbaText("ROMS FOLDER", OptionColors.label, OptionColors.labelShadow, m)
                GbaText(folder ?: "NOT LINKED", OptionColors.muted, OptionColors.mutedShadow, small, maxLines = 3)
                GbaText(
                    when {
                        folder == null -> "Link the folder you keep your games in: its supported ROMs join the library and play from there."
                        romsScanning -> "LOOKING FOR GAMES…"
                        else -> "$found GAME(S) IN THE LIBRARY · CHECKED FOR NEW ONES EACH TIME THE APP OPENS"
                    },
                    OptionColors.muted, OptionColors.mutedShadow, small, maxLines = Int.MAX_VALUE,
                )
                Row(
                    modifier = Modifier.padding(vertical = m.u * 4),
                    horizontalArrangement = Arrangement.spacedBy(m.u * 4),
                ) {
                    OptionButton(if (folder == null) "CHOOSE FOLDER" else "CHANGE FOLDER", small, onClick = onChoose)
                    if (folder != null) {
                        OptionButton("RESCAN", small, enabled = !romsScanning, onClick = { rescanRomsFolder() })
                        OptionButton("UNLINK", small, onClick = { prefs.romsFolder = null; RomFolder.clearCache(this@SettingsActivity); folderTick++ })
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
        }, "pokedaisey-rom-scan").apply { isDaemon = true; start() }
    }

    /** The picker's folder as a real path (persisting its grant), or null with a toast. */
    private fun pickedFolder(uri: Uri?): String? {
        if (uri == null) return null
        val path = StorageAccess.treePath(uri)
        if (path == null) {
            Toast.makeText(this, "Only folders on this device's own storage are supported", Toast.LENGTH_LONG).show()
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
            Toast.makeText(this, "Grant \"All files access\", then tap the button again", Toast.LENGTH_LONG).show()
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
                GbaText("${files.size} FILE(S) · ${humanSize(total)}", OptionColors.muted, OptionColors.mutedShadow, small)
                Row(
                    modifier = Modifier.padding(vertical = m.u * 4),
                    horizontalArrangement = Arrangement.spacedBy(m.u * 4),
                ) {
                    OptionButton("COPY PATH", small, onClick = { copyPath(ctx, dir.absolutePath) })
                    extraButtons()
                }

                if (files.isEmpty()) {
                    GbaText("(EMPTY)", OptionColors.muted, OptionColors.mutedShadow, small)
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
                                Icon(Icons.Filled.Delete, "Delete ${f.name}", tint = OptionColors.value, modifier = Modifier.size(small.lineHeight))
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
        Toast.makeText(ctx, "Path copied", Toast.LENGTH_SHORT).show()
    }

    private companion object {
        val RATES = floatArrayOf(0f, 2f, 3f, 4f, 5f, 6f, 8f, 10f)
        val TOUCH_NAMES = listOf("AUTO", "ALWAYS", "NEVER")

        fun rateLabel(v: Float) = if (v <= 0f) "INFINITE" else "${v.toInt()}×"

        fun keyLabel(name: String) = name.replace("BUTTON_", "").replace('_', ' ')

        fun humanSize(bytes: Long): String = when {
            bytes >= 1 shl 20 -> "%.1f MB".format(bytes / 1048576.0)
            bytes >= 1 shl 10 -> "%.1f KB".format(bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}

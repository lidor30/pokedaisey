package com.pokedaisey.app

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Display
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.pokedaisey.app.companion.BatteryStatus
import com.pokedaisey.app.companion.DeviceBattery
import com.pokedaisey.app.companion.FfMode
import com.pokedaisey.app.companion.FfMusicMode
import com.pokedaisey.app.companion.data.FfMenuWatch
import com.pokedaisey.app.companion.TelemetryStore
import com.pokedaisey.app.companion.data.RomArt
import com.pokedaisey.app.companion.data.RegionMapWatch
import com.pokedaisey.app.companion.data.RomRegionMap
import com.pokedaisey.app.companion.data.displayName
import com.pokedaisey.app.companion.data.TELEMETRY_SIZE
import com.pokedaisey.app.companion.data.TelemetryDecodeException
import com.pokedaisey.app.companion.data.decodeTelemetry
import com.pokedaisey.app.companion.ui.CompanionScreen
import com.pokedaisey.app.companion.ui.GameStatusBar
import java.io.File
import kotlinx.coroutines.delay

/**
 * Phase 1 shell: full-screen GBA emulation with RetroArch-style savestate slots
 * on configurable hotkeys (see [Hotkeys]). Backgrounding suspends to a state and
 * resumes exactly where you left off.
 *
 * Drop one `.gba` into `Android/data/com.pokedaisey.app/files/roms/`.
 */
class PokeDaiseyActivity : Activity() {

    private val input = GbaInput()
    private lateinit var hotkeys: Hotkeys
    private lateinit var engine: EmulatorEngine
    private lateinit var view: EmulatorView
    private lateinit var hud: TextView
    private lateinit var touchControls: TouchControlsView
    private lateinit var statusBar: ComposeView
    private lateinit var inputManager: android.hardware.input.InputManager
    private val ffMusicPlayer = FfMusicPlayer()
    private var ffMusicRenderer: FfMusicRenderer? = null
    private var clickSound: GameClickSound? = null
    /** The companion's button click (the game's own; see GameClickSound). */
    private val playClick: () -> Unit = { if (clickSoundOn) clickSound?.play() }
    @Volatile private var clickSoundOn = true

    private val inputDeviceListener = object : android.hardware.input.InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(id: Int) = syncTouchControls()
        override fun onInputDeviceRemoved(id: Int) = syncTouchControls()
        override fun onInputDeviceChanged(id: Int) = syncTouchControls()
    }

    private var rom: File? = null
    /** Opened by a frontend through [LaunchActivity] (see [exitGame]). */
    private var fromFrontend = false
    private var romKey: String = ""      // ROM CRC32, for per-ROM prefs
    private var states: SaveStates? = null
    private lateinit var saveDir: File

    private val telemetry = TelemetryStore()
    private lateinit var displayManager: DisplayManager
    private var presentation: DualScreenPresentation? = null
    // BACK for the companion: one per companion copy (bottom screen, debug mirror).
    private val companionBack = com.pokedaisey.app.companion.ui.CompanionBack()
    private val mirrorBack = com.pokedaisey.app.companion.ui.CompanionBack()

    // scripts/capture_fixture.sh support: if the EXTRA_DUMP_FIXTURE extra is a
    // directory path, the next successful (connected) telemetry sample dumps
    // raw EWRAM+IWRAM there for app/src/test's FakeMemoryReader fixtures - see
    // dumpFixtureIfPending()'s comment for why full RAM regions, not just the
    // fields a given decoder happens to read.
    private var pendingFixtureDump: File? = null
    // EXTRA_DUMP_FIXTURE_FORCE bypasses the party.isNotEmpty() readiness gate
    // below - for diagnosing a hack whose party/bag addresses are THEMSELVES
    // wrong (the exact case this exists for: party never populates because
    // the addresses are broken, so the normal gate can never fire either -
    // a chicken-and-egg problem the normal gate can't solve for itself).
    private var forceFixtureDump = false

    /** Backs the bottom-screen "States" tab. */
    private val stateSlots = object : com.pokedaisey.app.companion.StateSlots {
        override fun list() = states?.allSlots()?.map {
            com.pokedaisey.app.companion.StateSlots.Slot(
                it.slot, it.present, it.savedAt, it.thumb?.absolutePath,
            )
        } ?: emptyList()
        override val currentIndex get() = if (::engine.isInitialized) engine.currentSlot else 0
        override fun requestSave(index: Int) { if (::engine.isInitialized) engine.requestSaveState(index) }
        override fun requestLoad(index: Int) { if (::engine.isInitialized) engine.requestLoadState(index) }
        override fun requestUndoSave() { if (::engine.isInitialized) engine.requestUndoSave() }
        override fun requestUndoLoad() { if (::engine.isInitialized) engine.requestUndoLoad() }
    }

    /** Backs the bottom-screen touch battle control (see PLAN.md
     * Phase 5) — Tier A: firered-qol/emerald-qol only. */
    private val battleInput = object : com.pokedaisey.app.companion.BattleInput {
        override val busy get() = if (::engine.isInitialized) engine.battleInputBusy else false
        override fun selectAction(actionIndex: Int) { if (::engine.isInitialized) engine.battleSelectAction(actionIndex) }
        override fun selectMove(moveIndex: Int) { if (::engine.isInitialized) engine.battleSelectMove(moveIndex) }
        override fun back() { if (::engine.isInitialized) engine.battleBack() }
        override val canSwitch get() = ::engine.isInitialized && engine.battleSwitchAddrs != null
        override fun switchTo(personality: Long) { if (::engine.isInitialized) engine.battleSwitchTo(personality) }
    }

    /** The loaded ROM's header code / revision byte, for [switchAddrsFor]. */
    @Volatile private var romCode = ""
    @Volatile private var romRev = -1

    /**
     * Where the battle POKéMON pane's switch finds the party menu: retail
     * FireRed rev 1 / Emerald and their QoL builds only (same EWRAM addresses
     * in both - gPartyMenu, gPlayerParty from the decomp ELFs, checked against
     * each ROM's literal pools). Keyed on the detected game too, so LeafGreen,
     * FireRed rev 0, Ruby/Sapphire and hacks that share a header code (Seaglass)
     * stay out.
     */
    private fun switchAddrsFor(game: com.pokedaisey.app.companion.data.GameKind?): BattleInputController.SwitchAddrs? = when {
        game == com.pokedaisey.app.companion.data.GameKind.FIRERED && romCode == "BPRE" && romRev == 1 ->
            BattleInputController.SwitchAddrs(partyMenu = 0x0203B0A0L, party = 0x02024284L)
        game == com.pokedaisey.app.companion.data.GameKind.EMERALD && romCode == "BPEE" ->
            BattleInputController.SwitchAddrs(partyMenu = 0x0203CEC8L, party = 0x020244ECL)
        else -> null
    }

    /** Backs the bottom-screen "Settings" tab. Unlike SettingsActivity (which
     * only writes Prefs, picked up next launch), this also live-applies to the
     * already-running engine/touch controls since the game keeps running. */
    private val companionSettings = object : com.pokedaisey.app.companion.CompanionSettings {
        override val ffMaxSpeed get() = Prefs(this@PokeDaiseyActivity).ffMaxSpeed
        override fun setFfMaxSpeed(v: Float) {
            Prefs(this@PokeDaiseyActivity).ffMaxSpeed = v
            if (::engine.isInitialized) engine.ffMaxSpeed = v
        }
        override val ffToggled get() = if (::engine.isInitialized) engine.fastForwardToggled else false
        override fun setFfToggled(on: Boolean) {
            if (::engine.isInitialized) engine.setFastForwardToggled(on)
        }
        override val ffMusicMode get() = Prefs(this@PokeDaiseyActivity).ffMusicMode
        override fun setFfMusicMode(mode: FfMusicMode) {
            Prefs(this@PokeDaiseyActivity).ffMusicMode = mode
            if (::engine.isInitialized) engine.ffMusicMode = mode
        }
        override val ffMode get() = Prefs(this@PokeDaiseyActivity).ffMode
        override fun setFfMode(mode: FfMode) {
            Prefs(this@PokeDaiseyActivity).ffMode = mode
            if (::engine.isInitialized) engine.ffMode = mode
        }
        override val touchControlsMode get() = Prefs(this@PokeDaiseyActivity).touchControlsMode
        override fun setTouchControlsMode(v: Int) {
            Prefs(this@PokeDaiseyActivity).touchControlsMode = v
            syncTouchControls()
        }
        override fun gbaControlBindings() = GbaControls.rawBindings(getExternalFilesDir(null) ?: filesDir)
        override fun setGbaControlBinding(btn: GbaControls.Btn, keyName: String) {
            val code = keyCodeForName(keyName) ?: return
            GbaControls.setBinding(getExternalFilesDir(null) ?: filesDir, btn, code)
            input.setControls(GbaControls.load(getExternalFilesDir(null) ?: filesDir))
        }
        override fun hotkeyBindings() = Hotkeys.load(getExternalFilesDir(null) ?: filesDir).rawBindings
        override fun setHotkeyBinding(action: Hotkeys.Action, keyName: String) {
            val code = keyCodeForName(keyName) ?: return
            Hotkeys.setBinding(getExternalFilesDir(null) ?: filesDir, action, listOf(code))
            hotkeys = Hotkeys.load(getExternalFilesDir(null) ?: filesDir)
        }
        override fun restartGame() {
            val r = rom ?: return
            if (!::engine.isInitialized) return
            // Unbind the GL view from the current framebuffer FIRST and wait for
            // it to take — engine.stop() frees the native buffer the GL thread
            // (continuous render mode, its own clock) may still be reading; skip
            // this and it's a use-after-free race that crashes the process. See
            // EmulatorView.unbindCoreBlocking().
            view.unbindCoreBlocking()
            engine.stop()
            states?.resumeFile?.let { if (it.exists()) it.delete() }
            saveDir = SavesLocation.dir(this@PokeDaiseyActivity)
            val save = SavesLocation.resolve(saveDir, r.nameWithoutExtension)
            engine.start(r, save, null)
            showHud("Game restarted")
        }
        override fun closeGame() {
            exitGame()   // drives the normal onPause()/onStop() lifecycle, which already suspends+saves
        }
        override val clickSound get() = clickSoundOn
        override fun setClickSound(on: Boolean) {
            Prefs(this@PokeDaiseyActivity).clickSound = on
            clickSoundOn = on
        }
        override val statusBar get() = Prefs(this@PokeDaiseyActivity).statusBar
        override fun setStatusBar(on: Boolean) {
            Prefs(this@PokeDaiseyActivity).statusBar = on
            runOnUiThread { syncStatusBar() }
        }
        override val showHints get() = Prefs(this@PokeDaiseyActivity).showHints
        override fun setShowHints(on: Boolean) {
            Prefs(this@PokeDaiseyActivity).showHints = on
        }
        override val gameName get() = com.pokedaisey.app.companion.data.activeGame.displayName()
        override val romFileName get() = rom?.name ?: "(none)"
        override val companionTabs get() = Prefs(this@PokeDaiseyActivity).companionTabs
        override fun setCompanionTabs(tabs: List<String>) {
            Prefs(this@PokeDaiseyActivity).companionTabs = tabs
        }
        override fun guideNoticeAccepted(game: String) = Prefs(this@PokeDaiseyActivity).guideNoticeAccepted(game)
        override fun acceptGuideNotice(game: String) = Prefs(this@PokeDaiseyActivity).acceptGuideNotice(game)
    }

    /** Reverses [Hotkeys.keyName] — "BUTTON_A"/"Z"/etc. back to a keyCode, for the
     * companion's tap-a-name rebind picker (see [CompanionSettings]). */
    private fun keyCodeForName(name: String): Int? {
        val code = KeyEvent.keyCodeFromString("KEYCODE_$name")
        return code.takeIf { it != KeyEvent.KEYCODE_UNKNOWN }
    }

    private val hideHud = Runnable { hud.visibility = View.GONE }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = syncPresentation()
        override fun onDisplayRemoved(displayId: Int) = syncPresentation()
        override fun onDisplayChanged(displayId: Int) = syncPresentation()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        goImmersive()

        // Debug-only mirror of the companion UI onto the main display, for
        // Claude's own testing use (screenshotting/tapping over adb): the
        // Thor's second screen (where this normally lives, via
        // DualScreenPresentation) can't be screencap'd - FLAG_SECURE is set at
        // the hardware/display-device level for that panel specifically,
        // confirmed via `dumpsys display` (not something this app requests;
        // there is no FLAG_SECURE call anywhere in this codebase). The main
        // display doesn't have that restriction, so mirroring here makes the
        // companion UI screenshot-able.
        //
        // Gated on a marker file, not just BuildConfig.DEBUG, so a debug build
        // installed for actual play still looks like a normal single-screen
        // app - `adb shell touch <files-dir>/debug_mirror` turns it on,
        // deleting that file turns it back off; nothing the user has to do
        // either way. Never present in a release build regardless.
        val debugMirror = isDebugMirrorEnabled()

        view = EmulatorView(this)
        view.holdFrame = { ::engine.isInitialized && engine.holdFrame }
        if (debugMirror) view.setZOrderMediaOverlay(true) // see EmulatorView's z-order note
        hud = TextView(this).apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(0xA0000000.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            val p = dp(10)
            setPadding(p, dp(6), p, dp(6))
            visibility = View.GONE
        }
        touchControls = TouchControlsView(this).apply {
            onMask = { bits -> input.setTouchBits(bits) }
            visibility = View.GONE
        }
        statusBar = buildStatusBar()
        val root = GameStageLayout(this, view, statusBar, hud).apply {
            setBackgroundColor(Color.BLACK)
            // Shrink the game view to the left portion (instead of full-screen
            // underneath the mirror) so the whole GBA frame is actually
            // visible, aspect-fit within that narrower space, rather than half
            // of it sitting hidden behind the companion panel.
            val gameParams = if (debugMirror) {
                FrameLayout.LayoutParams(-1, -1).apply { rightMargin = dp(DEBUG_MIRROR_WIDTH_DP) }
            } else {
                FrameLayout.LayoutParams(-1, -1)
            }
            addView(view, gameParams)
            addView(statusBar, FrameLayout.LayoutParams(-2, -2))
            addView(touchControls, FrameLayout.LayoutParams(-1, -1))
            addView(hud, FrameLayout.LayoutParams(-2, -2).apply {
                gravity = Gravity.TOP or Gravity.START
                topMargin = dp(12); leftMargin = dp(12)
            })
            if (debugMirror) {
                addView(buildDebugCompanionMirror(), debugMirrorLayoutParams())
                addView(
                    TextView(this@PokeDaiseyActivity).apply {
                        text = "DEBUG MIRROR"
                        setTextColor(Color.WHITE)
                        setBackgroundColor(0xC0C02020.toInt())
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                        val p = dp(6)
                        setPadding(p, dp(3), p, dp(3))
                    },
                    FrameLayout.LayoutParams(-2, -2).apply {
                        gravity = Gravity.BOTTOM or Gravity.START
                        bottomMargin = dp(8); leftMargin = dp(8)
                    },
                )
            }
        }
        setContentView(root)
        // Compose's window-level recomposer looks for a ViewTreeLifecycleOwner
        // starting from the window's root view, not just the individual
        // ComposeViews added above (the status bar, the debug mirror) -
        // DualScreenPresentation doesn't need this because its ComposeView
        // *is* the Presentation's own content root; here it's nested inside
        // this Activity's own (plain, non-Compose) root, so the root itself
        // needs the owner too.
        val owner = ComposeHostOwner().apply { create(); resume() }
        root.setViewTreeLifecycleOwner(owner)
        root.setViewTreeSavedStateRegistryOwner(owner)
        root.setViewTreeViewModelStoreOwner(owner)
        syncStatusBar()

        saveDir = SavesLocation.dir(this)
        hotkeys = Hotkeys.load(getExternalFilesDir(null) ?: filesDir)
        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        inputManager = getSystemService(Context.INPUT_SERVICE) as android.hardware.input.InputManager

        rom = resolveRom(intent)
        val r = rom
        if (r == null) {
            toastNoRom()
            return
        }
        fromFrontend = intent?.getBooleanExtra(EXTRA_FROM_FRONTEND, false) ?: false
        pendingFixtureDump = intent?.getStringExtra(EXTRA_DUMP_FIXTURE)?.let { File(it).apply { mkdirs() } }
        forceFixtureDump = intent?.getBooleanExtra(EXTRA_DUMP_FIXTURE_FORCE, false) ?: false
        loadRom(r)
    }

    /**
     * Everything tied to the loaded ROM: its savestates, FF music, art, menu
     * watchers and the emulator engine. From onCreate, and again from
     * onNewIntent when a frontend opens a different game while this one is
     * alive - the activity is always paused before a new intent, so the old
     * engine has already stopped (and suspended its game) by then.
     */
    private fun loadRom(r: File) {
        // ~50-150ms for a 16-32 MiB ROM; one-time, at launch, on the black screen.
        val crc = SaveStates.crc32(r)
        romKey = crc
        states = SaveStates(getExternalFilesDir(null) ?: filesDir, crc)
        val ffMusicCache = FfMusicCache(getExternalFilesDir(null) ?: filesDir, crc)
        // STEADY FF music: songs rendered on their own from this ROM, in the
        // background, as the game starts them (see FfMusicRenderer).
        // The companion's button click is rendered from this ROM the same way.
        clickSound?.release()
        clickSound = GameClickSound(filesDir, crc)
        clickSoundOn = Prefs(this).clickSound
        ffMusicRenderer?.stop()
        ffMusicRenderer = FfMusicRenderer(r, crc, ffMusicCache, clickSound).also { it.start() }
        // FireRed / Emerald party-menu art and region maps come from the ROM
        // itself, once per ROM (see RomArt) - nothing of the game is bundled.
        RomArt.prefetch(filesDir, crc, r)
        // A FireRed-engine hack's own region map (Unbound, Odyssey, ...), read
        // from the ROM on every launch - a few KB of reads (see RomRegionMap).
        RomRegionMap.load(filesDir, crc, r)
        // SMART FF steps aside while the game's region map or a menu is up (see EmulatorEngine).
        RegionMapWatch.load(r)
        val (menuField, menuBattle) = Prefs(this).ffMenuCallbacks(crc)
        val header = runCatching { java.io.RandomAccessFile(r, "r").use { f -> f.seek(0xAC); ByteArray(0x11).also { f.readFully(it) } } }.getOrNull()
        val gameCode = header?.let { String(it, 0, 4, Charsets.US_ASCII) }.orEmpty()
        romCode = gameCode
        romRev = header?.let { it[0x10].toInt() and 0xFF } ?: -1
        FfMenuWatch.load(gameCode, menuField, menuBattle)
        val appContext = applicationContext
        FfMenuWatch.onLearned = { field, battle -> Prefs(appContext).setFfMenuCallbacks(crc, field, battle) }

        engine = EmulatorEngine(input, states!!, ffMusicCache).apply {
            onCoreReady = { w, h ->
                MgbaCore.pkVideoBuffer()?.let { buf -> runOnUiThread { view.bindCore(buf, w, h) } }
            }
            onStateResult = { action, slot, ok ->
                val msg = when (action) {
                    Hotkeys.Action.SAVE_STATE -> if (ok) "Saved slot $slot" else "Slot $slot: save failed"
                    Hotkeys.Action.LOAD_STATE -> if (ok) "Loaded slot $slot" else "Slot $slot: nothing to load"
                    Hotkeys.Action.UNDO_SAVE -> if (ok) "Undid save (slot $slot)" else "Nothing to undo"
                    Hotkeys.Action.UNDO_LOAD -> if (ok) "Undid load" else "Nothing to undo"
                    else -> ""
                }
                if (msg.isNotEmpty()) runOnUiThread { showHud(msg) }
            }
            onSlotChanged = { slot ->
                val tag = if (states?.exists(slot) == true) " (used)" else " (empty)"
                runOnUiThread { showHud("Slot $slot$tag") }
            }
            onSpeedChanged = { label -> runOnUiThread { showHud(label) } }
            onFastForwardToggledChanged = { on -> Prefs(this@PokeDaiseyActivity).ffToggled = on }
            onFfMusicChanged = { clip -> runOnUiThread { ffMusicPlayer.setClip(clip) } }
            onFfMusicWanted = { key -> ffMusicRenderer?.request(key) }
            onSample = {   // runs on the emu thread
                val snap = telemetry.refresh()
                // SMART FF reads the game's own gMain once the game is known (no scan
                // guesswork), and learns the field's main callback from the player moving.
                telemetry.knownGMain()?.let { (addr, inBattleOff) -> FfMenuWatch.useKnownGMain(addr, inBattleOff) }
                if (snap.connected) FfMenuWatch.notePosition(snap.x, snap.y, snap.mapGroup, snap.mapNum, snap.inBattle)
                if (snap.connected) battleSwitchAddrs = switchAddrsFor(snap.game)
                // party.isNotEmpty(), not just connected: `connected` flips true as
                // soon as the QOLT struct/native addresses are found, which can be
                // well before the save's party has actually loaded into RAM (e.g.
                // still on the title/continue screen just after boot) - a dump
                // right then faithfully captures a real but useless "partyCount=0"
                // moment. Bit the first fixture capture this existed for.
                if (snap.connected && (snap.party.isNotEmpty() || forceFixtureDump)) dumpFixtureIfPending()
            }
            onBattleInputSample = {   // runs on the emu thread, ~15x/sec
                telemetry.refreshBattleInputFast()?.let { (battler, state) ->
                    setBattleMenuState(battler, state)
                }
            }
            ffMaxSpeed = Prefs(this@PokeDaiseyActivity).ffMaxSpeed
            ffMusicMode = Prefs(this@PokeDaiseyActivity).ffMusicMode
            ffMode = Prefs(this@PokeDaiseyActivity).ffMode
            restoreFastForwardToggled(Prefs(this@PokeDaiseyActivity).ffToggled)
            restoreSpeedIndex(Prefs(this@PokeDaiseyActivity).speedIndexFor(romKey))
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Lets scripts/capture_fixture.sh dump an ALREADY-RUNNING, already-past-
        // the-title-screen session (singleTask means this fires instead of a
        // fresh onCreate when the activity is retargeted while alive) instead of
        // force-stopping + cold-relaunching, which loses party/save state and
        // re-lands on the title screen - fine for a ROM that auto-skips straight
        // to the overworld, but stalls capture indefinitely for one that doesn't.
        // pendingFixtureDump/forceFixtureDump are plain instance vars read fresh
        // every onSample tick (see dumpFixtureIfPending()), so setting them here
        // is picked up by the already-running emu thread on its next tick.
        intent.getStringExtra(EXTRA_DUMP_FIXTURE)?.let { pendingFixtureDump = File(it).apply { mkdirs() } }
        if (intent.hasExtra(EXTRA_DUMP_FIXTURE_FORCE)) {
            forceFixtureDump = intent.getBooleanExtra(EXTRA_DUMP_FIXTURE_FORCE, false)
        }
        // A frontend (LaunchActivity) or the library asking for a game while
        // one is open: the same ROM just comes back to the front; a different
        // one replaces it. Normally we're paused by now, so the old game is
        // already suspended and onResume starts the new one; delivered while
        // resumed (Android 10+ may), suspend and start here instead.
        if (intent.hasExtra(LibraryActivity.EXTRA_ROM)) {
            fromFrontend = intent.getBooleanExtra(EXTRA_FROM_FRONTEND, false)
            val next = intent.getStringExtra(LibraryActivity.EXTRA_ROM)?.let(::File)?.takeIf { it.isFile }
            if (next != null && next.absolutePath != rom?.absolutePath) {
                val running = ::engine.isInitialized && engine.running
                if (running) {
                    view.unbindCoreBlocking()   // see restartGame
                    states?.let { engine.stopWithSuspend(it.resumeFile) } ?: engine.stop()
                }
                rom = next
                telemetry.reset()
                loadRom(next)
                if (running) startGame()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        goImmersive()
        view.onResume()
        // Pick up any Settings changes made since launch.
        hotkeys = Hotkeys.load(getExternalFilesDir(null) ?: filesDir)
        input.setControls(GbaControls.load(getExternalFilesDir(null) ?: filesDir))
        if (::engine.isInitialized) {
            engine.ffMaxSpeed = Prefs(this).ffMaxSpeed
            engine.ffMusicMode = Prefs(this).ffMusicMode
            engine.ffMode = Prefs(this).ffMode
        }
        saveDir = SavesLocation.dir(this)
        if (!startGame()) return
        displayManager.registerDisplayListener(displayListener, null)
        syncPresentation()
        inputManager.registerInputDeviceListener(inputDeviceListener, null)
        syncTouchControls()
        syncStatusBar()
        // Sticky: the current reading arrives right away, then every change.
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let(::onBattery)
    }

    /** Starts the loaded ROM's engine, resuming where it was left; false with no ROM. */
    private fun startGame(): Boolean {
        val r = rom ?: return false
        val st = states ?: return false
        val save = SavesLocation.resolve(saveDir, r.nameWithoutExtension)
        // Auto-resume from wherever was written most recently: normally that's
        // the auto-suspend snapshot from the last close, but if that session
        // ended in a crash instead of a clean close (stale/missing resumeFile),
        // fall back to the newest manual save slot instead. Stage the winner
        // into resumeFile itself so the one-shot "load then delete" below only
        // ever touches that dedicated file, never a numbered slot.
        // A save file was just loaded from the library: boot from it, once.
        if (st.freshBootFile.exists()) {
            st.freshBootFile.delete()
            st.resumeFile.delete()
            engine.start(r, save, null)
            view.postDelayed({ engine.lastError?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show() } }, 1500)
            return true
        }
        st.latestResumeSource()?.let { src -> if (src != st.resumeFile) runCatching { src.copyTo(st.resumeFile, overwrite = true) } }
        engine.start(r, save, st.resumeFile.takeIf { it.isFile && it.length() > 0 })
        view.postDelayed({ engine.lastError?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show() } }, 1500)
        return true
    }

    override fun onDestroy() {
        ffMusicRenderer?.stop()
        ffMusicRenderer = null
        clickSound?.release()
        clickSound = null
        super.onDestroy()
    }

    override fun onPause() {
        super.onPause()
        runCatching { displayManager.unregisterDisplayListener(displayListener) }
        runCatching { inputManager.unregisterInputDeviceListener(inputDeviceListener) }
        runCatching { unregisterReceiver(batteryReceiver) }
        presentation?.dismiss()
        presentation = null
        ffMusicPlayer.release()
        if (::engine.isInitialized && romKey.isNotEmpty()) {
            Prefs(this).setSpeedIndexFor(romKey, engine.speedIndex)
        }
        states?.let { engine.stopWithSuspend(it.resumeFile) } ?: engine.stop()
        view.onPause()
    }

    /** Battery level for the companion's SETTINGS title and the status bar (see DeviceBattery). */
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = onBattery(intent)
    }

    private fun onBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level < 0 || scale <= 0) return
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        DeviceBattery.status.value = BatteryStatus(level * 100 / scale, charging)
    }

    /** Show the companion on a secondary display if there is one; drop it if not. */
    private fun syncPresentation() {
        if (!::engine.isInitialized) return
        // The Thor's bottom screen is a presentation display. Other dual-screen
        // handhelds (Retroid Pocket Duo / Duo Lite) may expose theirs as a plain
        // secondary display without that flag, so fall back to any valid
        // public display other than the built-in main one.
        val target: Display? = displayManager
            .getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            .firstOrNull { it.isValid }
            ?: displayManager.displays.firstOrNull {
                it.isValid && it.displayId != Display.DEFAULT_DISPLAY && (it.flags and Display.FLAG_PRIVATE) == 0
            }
        val current = presentation
        if (target == null) {
            current?.dismiss()
            presentation = null
            return
        }
        if (current != null && current.display.displayId == target.displayId && current.isShowing) return
        current?.dismiss()
        presentation = runCatching {
            DualScreenPresentation(this, target, telemetry, stateSlots, companionSettings, battleInput, companionBack, playClick).also { it.show() }
        }.onFailure { Log.w("pokedaisey", "presentation failed", it) }.getOrNull()
    }

    /** Show the status bar above the game per SETTINGS > STATUS BAR. */
    private fun syncStatusBar() {
        if (!::statusBar.isInitialized) return
        statusBar.visibility = if (Prefs(this).statusBar) View.VISIBLE else View.GONE
    }

    /**
     * The status bar ([com.pokedaisey.app.companion.ui.GameStatusBar]):
     * the ROM's name as the Library shows it, the map section and money from
     * telemetry, the clock (the system's 12/24-hour setting) and the battery.
     */
    private fun buildStatusBar(): ComposeView = ComposeView(this).apply {
        setContent {
            val snap by telemetry.snapshot.collectAsState()
            val time by produceState(clockText()) {
                while (true) {
                    delay(60_000L - System.currentTimeMillis() % 60_000L)
                    value = clockText()
                }
            }
            val name = rom?.let { Prefs(this@PokeDaiseyActivity).romDisplayName(it) ?: it.nameWithoutExtension }.orEmpty()
            GameStatusBar(
                gameName = name,
                location = snap.location.mapSecName.takeIf { snap.connected },
                money = snap.money.takeIf { snap.connected },
                time = time,
            )
        }
    }

    private fun clockText(): String =
        android.text.format.DateFormat.getTimeFormat(this).format(java.util.Date())

    /** Show the on-screen controls per the Settings mode (default: only with no gamepad). */
    private fun syncTouchControls() {
        if (!::touchControls.isInitialized) return
        val show = when (Prefs(this).touchControlsMode) {
            1 -> true
            2 -> false
            else -> InputDevice.getDeviceIds().none { id ->
                val d = InputDevice.getDevice(id) ?: return@none false
                !d.isVirtual && d.sources and
                    (InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK) != 0
            }
        }
        touchControls.visibility = if (show) View.VISIBLE else View.GONE
        if (!show) input.setTouchBits(0)
    }

    // --- input ---------------------------------------------------------------

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN && event.action != KeyEvent.ACTION_UP) {
            return super.dispatchKeyEvent(event)
        }
        val down = event.action == KeyEvent.ACTION_DOWN
        if (::hotkeys.isInitialized) {
            for (e in hotkeys.onKey(event.keyCode, down)) handleHotkey(e)
            if (hotkeys.consumes(event.keyCode)) return true   // key is completing a chord
        }
        if (input.onKey(event.keyCode, down)) return true
        if (down && event.repeatCount == 0 && event.keyCode != KeyEvent.KEYCODE_BACK) {
            // Helps discover a device's real keycodes (e.g. L2/R2) for rebinding.
            Log.i("pokedaisey", "unmapped key ${event.keyCode} (${KeyEvent.keyCodeToString(event.keyCode)})")
        }
        return super.dispatchKeyEvent(event)
    }

    // BACK: a tap is the companion's back (closes what's open there, else
    // nothing - no accidental exit mid-game); hold to return to the ROM library.
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) { event.startTracking(); return true }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) { exitGame(); return true }
        return super.onKeyLongPress(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // Not after a hold (that's canceled) - the long press already left.
            if (event.isTracking && !event.isCanceled) { companionBack.back(); mirrorBack.back() }
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    // Many Android handhelds report L2/R2 as analog axes, not BUTTON_L2/R2 keys.
    // RetroArch-style: right trigger = hold fast-forward, left trigger = hold slow-mo.
    private var rtDown = false
    private var ltDown = false

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (::engine.isInitialized) {
            val rt = maxOf(
                event.getAxisValue(MotionEvent.AXIS_RTRIGGER),
                event.getAxisValue(MotionEvent.AXIS_GAS),
                event.getAxisValue(MotionEvent.AXIS_THROTTLE),
            )
            val lt = maxOf(
                event.getAxisValue(MotionEvent.AXIS_LTRIGGER),
                event.getAxisValue(MotionEvent.AXIS_BRAKE),
            )
            if ((rt > 0.5f) != rtDown) { rtDown = rt > 0.5f; engine.setFastForwardHeld(rtDown) }
            if ((lt > 0.5f) != ltDown) { ltDown = lt > 0.5f; engine.setSlowmoHeld(ltDown) }
        }
        if (input.onMotion(event)) return true
        return super.onGenericMotionEvent(event)
    }

    private fun handleHotkey(e: Hotkeys.Event) {
        if (!::engine.isInitialized) return
        when (e.action) {
            Hotkeys.Action.SAVE_STATE -> if (e.pressed) engine.requestSaveState(engine.currentSlot)
            Hotkeys.Action.LOAD_STATE -> if (e.pressed) engine.requestLoadState(engine.currentSlot)
            Hotkeys.Action.UNDO_SAVE -> if (e.pressed) engine.requestUndoSave()
            Hotkeys.Action.UNDO_LOAD -> if (e.pressed) engine.requestUndoLoad()
            Hotkeys.Action.SLOT_NEXT -> if (e.pressed) engine.cycleSlot(+1)
            Hotkeys.Action.SLOT_PREV -> if (e.pressed) engine.cycleSlot(-1)
            Hotkeys.Action.FF_HOLD -> engine.setFastForwardHeld(e.pressed)
            Hotkeys.Action.FF_TOGGLE -> if (e.pressed) engine.toggleFastForward()
            Hotkeys.Action.SPEED_CYCLE -> if (e.pressed) engine.cycleSpeed()
            Hotkeys.Action.SLOWMO_HOLD -> engine.setSlowmoHeld(e.pressed)
            Hotkeys.Action.EXIT_GAME -> if (e.pressed) exitGame()
        }
    }

    // --- helpers -----------------------------------------------------------------

    private fun showHud(text: String) {
        hud.text = text
        hud.visibility = View.VISIBLE
        hud.removeCallbacks(hideHud)
        hud.postDelayed(hideHud, 1800)
    }

    private fun resolveRom(intent: Intent?): File? {
        // 1. Explicit choice from LibraryActivity.
        intent?.getStringExtra(LibraryActivity.EXTRA_ROM)?.let { p ->
            File(p).takeIf { it.isFile }?.let { return it }
        }
        // 2. VIEW intent (open a .gba with the app).
        intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data?.let { uri ->
            copyIncoming(uri)?.let { return it }
        }
        // 3. Last played.
        Prefs(this).lastRomPath?.let { p -> File(p).takeIf { it.isFile }?.let { return it } }
        // 4. First ROM in the folder.
        val romsDir = File(getExternalFilesDir(null), "roms").apply { mkdirs() }
        return romsDir.listFiles { f ->
            f.isFile && f.name.substringAfterLast('.', "").lowercase() in GBA_EXT
        }?.sortedBy { it.name.lowercase() }?.firstOrNull()
    }

    /**
     * scripts/capture_fixture.sh's counterpart: dumps the ENTIRE EWRAM+IWRAM
     * address space (not just whatever fields the current decoder happens to
     * read) to [pendingFixtureDump], once. Full-region rather than a
     * per-field capture so app/src/test's FakeMemoryReader can serve ANY
     * address a decoder asks for - including pointer chases (gSaveBlock1Ptr
     * -> bagPocket_X, gBagPockets[p].itemSlots, ...) that land at a
     * per-boot-randomized offset (SetSaveBlocksPointers' ASLR-style offset -
     * see the FireRed rev0/rev1 gSaveBlock2Ptr bug write-up in this project's
     * memory) a fixed small capture could easily miss. Runs on the emu
     * thread (called from onSample) so the bus reads are safe.
     */
    private fun dumpFixtureIfPending() {
        val dir = pendingFixtureDump ?: return
        try {
            val ewram = MgbaCore.pkReadBytes(EWRAM_BASE, EWRAM_SIZE)
            val iwram = MgbaCore.pkReadBytes(IWRAM_BASE, IWRAM_SIZE)
            if (ewram == null || iwram == null) {
                Log.e("pokedaisey", "fixture dump: pkReadBytes returned null")
                pendingFixtureDump = null
                return
            }
            // QolTelemetry_Update() rewrites the WHOLE struct roughly once a
            // second (struct-path games only); if this sample's two separate
            // reads land mid-rewrite, the result is a torn snapshot - not
            // corruption, just unlucky timing. Retry next tick instead of
            // freezing a garbage fixture. Bit real capture attempts before
            // this check existed (item slots past itemCount weren't actually
            // all-zero - see the emerald_qol capture writeup this guards
            // against). Native-RAM games have no "QOLT" magic, so they always
            // pass through untouched.
            if (!isStructSnapshotConsistent(iwram) || !isStructSnapshotConsistent(ewram)) {
                Log.w("pokedaisey", "fixture dump: torn struct read, retrying next tick")
                return
            }
            pendingFixtureDump = null // one-shot, only once a snapshot is actually accepted
            File(dir, "ewram.bin").writeBytes(ewram)
            File(dir, "iwram.bin").writeBytes(iwram)
            Log.i("pokedaisey", "fixture dump: wrote ${ewram.size}B EWRAM + ${iwram.size}B IWRAM to $dir")
        } catch (t: Throwable) {
            Log.e("pokedaisey", "fixture dump failed", t)
            pendingFixtureDump = null
        }
    }

    /** True if [buf] has no "QOLT" magic (a native-RAM game - nothing to
     * check) or if it does and the struct there decodes cleanly with every
     * item slot from itemCount onward genuinely zeroed, matching
     * QolTelemetry_Update()'s own zero-fill loop invariant. False means a
     * torn read caught mid-rewrite. */
    private fun isStructSnapshotConsistent(buf: ByteArray): Boolean {
        val magic = "QOLT".toByteArray(Charsets.US_ASCII)
        var idx = -1
        outer@ for (i in 0..buf.size - magic.size) {
            for (j in magic.indices) {
                if (buf[i + j] != magic[j]) continue@outer
            }
            idx = i
            break
        }
        if (idx < 0) return true // no struct here - native-RAM path, nothing to validate
        if (idx + TELEMETRY_SIZE > buf.size) return false
        return try {
            val t = decodeTelemetry(buf.copyOfRange(idx, idx + TELEMETRY_SIZE))
            var seenPadding = false
            for (item in t.items) {
                val isZero = item.itemId == 0 && item.quantity == 0 && item.pocket == 0
                if (seenPadding && !isZero) return false // non-zero after padding started
                if (isZero) seenPadding = true
            }
            true
        } catch (e: TelemetryDecodeException) {
            false
        }
    }

    private fun copyIncoming(uri: Uri): File? = try {
        val out = File(cacheDir, "incoming.gba")
        contentResolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { input.copyTo(it) }
        }
        out.takeIf { it.length() > 0 }
    } catch (t: Throwable) {
        Log.e("pokedaisey", "copyIncoming failed", t)
        null
    }

    /**
     * Leaves the game. Opened by a frontend, the whole task goes, so the
     * frontend comes back rather than a library left underneath from earlier.
     */
    private fun exitGame() {
        if (fromFrontend) finishAndRemoveTask() else finish()
    }

    private fun toastNoRom() {
        Toast.makeText(this, "No ROM found. Put a .gba in Android/data/$packageName/files/roms/", Toast.LENGTH_LONG).show()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    /** See the debug-mirror comment at the `addView` call site. The
     * ViewTreeLifecycleOwner this needs is set on the root content view in
     * onCreate, not here - Compose's window recomposer looks it up starting
     * from the window's root, and one set on the root is inherited by every
     * descendant (including this ComposeView) via the normal parent walk. */
    private fun buildDebugCompanionMirror(): ComposeView {
        return ComposeView(this).apply {
            setContent {
                val snap by telemetry.snapshot.collectAsState()
                CompanionScreen(snap, stateSlots, companionSettings, battleInput, back = mirrorBack, clickSound = playClick)
            }
        }
    }

    private fun debugMirrorLayoutParams() = FrameLayout.LayoutParams(dp(DEBUG_MIRROR_WIDTH_DP), -1).apply {
        gravity = Gravity.END
    }

    /** `adb shell touch <files-dir>/debug_mirror` (any content, or none) turns
     * this on for the next launch; `adb shell rm <files-dir>/debug_mirror`
     * turns it back off. Always false in a release build regardless. */
    private fun isDebugMirrorEnabled(): Boolean =
        BuildConfig.DEBUG && File(getExternalFilesDir(null) ?: filesDir, "debug_mirror").isFile

    private fun goImmersive() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
    }

    companion object {
        /** Set by [LaunchActivity]: leaving the game returns to the frontend. */
        const val EXTRA_FROM_FRONTEND = "fromFrontend"
        private val GBA_EXT = setOf("gba", "bin", "agb")
        private const val DEBUG_MIRROR_WIDTH_DP = 420
        private const val EXTRA_DUMP_FIXTURE = "dumpFixture"
        private const val EXTRA_DUMP_FIXTURE_FORCE = "dumpFixtureForce"
        private const val EWRAM_BASE = 0x02000000L
        private const val EWRAM_SIZE = 0x40000
        private const val IWRAM_BASE = 0x03000000L
        private const val IWRAM_SIZE = 0x8000
    }
}

package com.pokedaisey.app

import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.pokedaisey.app.companion.FfMode
import com.pokedaisey.app.companion.FfMusicMode
import com.pokedaisey.app.companion.smartSlows
import com.pokedaisey.app.companion.data.FfMenuWatch
import com.pokedaisey.app.companion.data.FfMusicKey
import com.pokedaisey.app.companion.data.InProcessReader
import com.pokedaisey.app.companion.data.RegionMapWatch
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.roundToInt

/**
 * Owns the single emulator thread: run a frame, push its audio (blocking write,
 * which paces us to real time), service any pending savestate request, repeat.
 *
 * Savestate/loadstate/suspend are always performed on this thread, between
 * frames, so the core is never touched mid-frame.
 */
class EmulatorEngine(
    private val input: GbaInput,
    private val states: SaveStates,
    private val ffMusicCache: FfMusicCache,
) {
    private var thread: Thread? = null
    @Volatile var running = false
        private set
    @Volatile private var loadError: String? = null

    @Volatile private var pendingSave = -1
    @Volatile private var pendingLoad = -1
    @Volatile private var pendingUndoSave = false
    @Volatile private var pendingUndoLoad = false
    @Volatile private var pendingSuspend: File? = null

    @Volatile private var ffHeld = false
    @Volatile private var ffToggled = false
    @Volatile private var slowmoHeld = false
    @Volatile private var speedIdx = 0          // index into SPEED_STEPS

    /** Cap for hold/toggle fast-forward. 0 = unlimited. (Phase 4: make configurable.) */
    @Volatile var ffMaxSpeed = 6f

    /** What fast-forward sounds like (Settings: STEADY / SPED-UP / OFF). */
    @Volatile var ffMusicMode = FfMusicMode.STEADY
    /** SMART: 1x while a menu screen or the region map is up ([FfMenuWatch], [RegionMapWatch]). */
    @Volatile var ffMode = FfMode.SMART

    @Volatile var currentSlot = 0
        private set

    val lastError: String? get() = loadError

    /** Fired from the emu thread; wrap UI work in the callback yourself. */
    var onCoreReady: ((width: Int, height: Int) -> Unit)? = null
    var onStateResult: ((action: Hotkeys.Action, slot: Int, ok: Boolean) -> Unit)? = null
    var onSlotChanged: ((slot: Int) -> Unit)? = null
    var onSpeedChanged: ((label: String) -> Unit)? = null

    /** Fired (emu thread) only when the FF music that should be playing
     * actually changes — a cached clip file to loop, or null to stop
     * (nothing cached yet for this state, or FF isn't active). Wrap
     * MediaPlayer work in the callback yourself (see FfMusicPlayer). */
    var onFfMusicChanged: ((file: File?) -> Unit)? = null

    /** Fired (emu thread) in STEADY when the game starts a song with no clip
     * yet ([FfMusicKey]) - hand it to [FfMusicRenderer.request]. */
    var onFfMusicWanted: ((key: String) -> Unit)? = null

    /** Invoked on the emu thread ~1×/sec (between frames) — do core memory reads here. */
    var onSample: (() -> Unit)? = null

    /** Bottom-screen touch battle control (see PLAN.md Phase 5):
     * drives the real battle menu via synthetic button presses through the
     * same touch-overlay bitmask the on-screen D-pad uses. */
    private val battleInput = BattleInputController(input)

    /** True while a queued battle-menu button sequence hasn't fully drained —
     * the touch battle UI should disable further taps while this is true. */
    val battleInputBusy: Boolean get() = battleInput.isBusy

    /** Keep showing the last frame: an automated switch is in the game's party menu (see [BattleInputController.holdFrame]). */
    val holdFrame: Boolean get() = battleInput.holdFrame

    /** actionIndex: 0=FIGHT, 1=BAG, 2=POKEMON, 3=RUN. Call from the UI thread. */
    fun battleSelectAction(actionIndex: Int) = battleInput.selectAction(actionIndex)

    /** moveIndex 0-3. Call from the UI thread; opens FIGHT first if needed. */
    fun battleSelectMove(moveIndex: Int) = battleInput.selectMove(moveIndex)

    /** Backs out of move-select to action-select (a single B press). */
    fun battleBack() = battleInput.back()

    fun battleCancelInput() = battleInput.cancel()

    /** Where [battleSwitchTo] finds the party menu; null = not supported for this game. */
    var battleSwitchAddrs: BattleInputController.SwitchAddrs?
        get() = battleInput.switchAddrs
        set(v) { battleInput.switchAddrs = v }

    /** Switch to the party mon with this personality. Call from the UI thread. */
    fun battleSwitchTo(personality: Long) = battleInput.switchTo(personality)

    /** Invoked on the emu thread at a faster, fixed cadence than [onSample]
     * (not gated by its ~1 Hz throttle) — wire this to a fast, 2-byte
     * telemetry peek (TelemetrySampler.sampleBattleInputFast) and feed the
     * result back via [setBattleMenuState], so the touch battle UI reacts to
     * a menu transition within a fraction of a second, not up to a second
     * late. */
    var onBattleInputSample: (() -> Unit)? = null

    /** Feed the latest (battleActiveBattler, battleInputState) from the fast
     * poll above. */
    fun setBattleMenuState(activeBattler: Int, state: Int) = battleInput.onState(activeBattler, state)

    fun setFastForwardHeld(on: Boolean) {
        if (ffHeld == on) return
        ffHeld = on
        announceSpeed()
    }

    fun toggleFastForward() {
        ffToggled = !ffToggled
        onFastForwardToggledChanged?.invoke(ffToggled)
        announceSpeed()
    }

    /** Explicit set (vs. [toggleFastForward]'s flip) — for a UI switch that needs
     * to show/drive an exact on/off state, like the companion Settings tab. */
    fun setFastForwardToggled(on: Boolean) {
        if (ffToggled == on) return
        ffToggled = on
        onFastForwardToggledChanged?.invoke(ffToggled)
        announceSpeed()
    }

    /** Restore a persisted on/off state without flashing the HUD or re-firing
     * [onFastForwardToggledChanged] (same pattern as [restoreSpeedIndex]). */
    fun restoreFastForwardToggled(on: Boolean) { ffToggled = on }

    val fastForwardToggled: Boolean get() = ffToggled

    /** Fired (any thread) whenever the on/off state actually changes, from
     * either [toggleFastForward] (hotkey) or [setFastForwardToggled] (the
     * companion Settings switch) — not from [restoreFastForwardToggled]. Wire
     * this to persist the choice (see Prefs.ffToggled). */
    var onFastForwardToggledChanged: ((Boolean) -> Unit)? = null

    fun setSlowmoHeld(on: Boolean) {
        if (slowmoHeld == on) return
        slowmoHeld = on
        announceSpeed()
    }

    fun cycleSpeed() {
        speedIdx = (speedIdx + 1) % SPEED_STEPS.size
        announceSpeed()
    }

    /** Current speed-cycle position, for persisting a per-ROM default. */
    val speedIndex: Int get() = speedIdx

    /** Restore a persisted speed index without flashing the HUD. */
    fun restoreSpeedIndex(i: Int) { speedIdx = i.coerceIn(0, SPEED_STEPS.size - 1) }

    /** Emu thread: the game's region map / a menu screen is up ([RegionMapWatch], [FfMenuWatch]). */
    private var mapOpen = false
    private var menuOpen = false

    /** The speed the player asked for (FF, slow-mo, the speed steps); Float.POSITIVE_INFINITY = "run uncapped". */
    private fun requestedSpeed(): Float = when {
        ffHeld || ffToggled -> if (ffMaxSpeed > 0f) ffMaxSpeed else Float.POSITIVE_INFINITY
        slowmoHeld -> 0.5f
        else -> SPEED_STEPS[speedIdx]
    }

    /** What the emulator actually runs at: [requestedSpeed], unless SMART is holding it at 1x for now. */
    private fun effectiveSpeed(): Float {
        val s = requestedSpeed()
        // SMART: menus and the region map's cursor can't be steered sped up -
        // 1x while one is up, and FF (still on) picks up again when it closes.
        // A battle stays fast; only its bag and party screens slow down.
        return if (ffMode == FfMode.SMART && s > 1f && smartSlows(battleInput.gameState, menuOpen, mapOpen)) 1f else s
    }

    private fun speedTag(s: Float): String = if (s.isInfinite()) "ff" else "${s}x"

    private fun announceSpeed() {
        val label = when {
            ffHeld || ffToggled -> "Fast-forward"
            slowmoHeld -> "Slow-motion ½×"
            speedIdx == 0 -> "Normal speed"
            else -> SPEED_STEPS[speedIdx].let { if (it % 1f == 0f) "Speed ${it.toInt()}×" else "Speed $it×" }
        }
        onSpeedChanged?.invoke(label)
    }

    fun start(rom: File, save: File, resume: File?) {
        if (running) return
        running = true
        loadError = null
        val r = resume
        thread = Thread({ loop(rom, save, r) }, "pokedaisey-emu").apply { start() }
    }

    /** Hard stop, no state written. */
    fun stop() {
        running = false
        thread?.join(3000)
        thread = null
    }

    /** Save to [target] on the next frame boundary, then stop the loop. */
    fun stopWithSuspend(target: File) {
        if (!running) return
        pendingSuspend = target
        thread?.join(3000)
        thread = null
        running = false
    }

    fun requestSaveState(slot: Int) { pendingSave = slot.coerceIn(SaveStates.SLOT_MIN, SaveStates.SLOT_MAX) }
    fun requestLoadState(slot: Int) { pendingLoad = slot.coerceIn(SaveStates.SLOT_MIN, SaveStates.SLOT_MAX) }
    fun requestUndoSave() { pendingUndoSave = true }
    fun requestUndoLoad() { pendingUndoLoad = true }

    fun cycleSlot(delta: Int) {
        val n = (currentSlot + delta).mod(SaveStates.SLOT_MAX - SaveStates.SLOT_MIN + 1)
        currentSlot = n
        onSlotChanged?.invoke(n)
    }

    private var videoBuf: ByteBuffer? = null
    private var vw = 0
    private var vh = 0

    private fun loop(rom: File, save: File, resume: File?) {
        if (!MgbaCore.pkInit(rom.absolutePath, save.absolutePath)) {
            loadError = "mGBA could not load ${rom.name}"
            running = false
            return
        }
        vw = MgbaCore.pkVideoWidth()
        vh = MgbaCore.pkVideoHeight()
        videoBuf = MgbaCore.pkVideoBuffer()
        onCoreReady?.invoke(vw, vh)

        if (resume != null && resume.isFile && resume.length() > 0) {
            val ok = MgbaCore.pkLoadState(resume.absolutePath)
            Log.i("pokedaisey", "resume state load: $ok")
            resume.delete()
        }

        val sampleRate = MgbaCore.pkSampleRate()
        val track = buildAudioTrack(sampleRate)
        track.play()

        val scratch = ShortArray(4096)
        val frameNanos = 1_000_000_000L / 60L
        var next = System.nanoTime()
        var fpsFrames = 0
        var fpsSince = System.nanoTime()
        var prevSpeed = 1f
        var paused = false
        var lastSample = 0L
        var lastBattleInputSample = 0L
        var lastFfMusicFile: File? = null
        var requestedKey: String? = null
        // SPED-UP: the core's audio averaged down to real time (a box
        // filter, so it's the game's own sound pitched and tempo'd up).
        val spedUpOut = ShortArray(scratch.size)
        var decimPhase = 0f
        var decimL = 0
        var decimR = 0
        var decimN = 0
        var measuredSpeed = 4f // uncapped FF's real rate, from the fps log below
        try {
            while (running) {
                battleInput.tick()
                MgbaCore.pkSetKeys(input.mask)
                MgbaCore.pkRunFrame()

                servicePending()

                val nowMs = System.currentTimeMillis()
                if (nowMs - lastSample >= 1000L) {
                    lastSample = nowMs
                    runCatching { onSample?.invoke() }
                }
                if (nowMs - lastBattleInputSample >= 66L) {
                    lastBattleInputSample = nowMs
                    runCatching { onBattleInputSample?.invoke() }
                }

                mapOpen = RegionMapWatch.isOpen(InProcessReader)
                menuOpen = FfMenuWatch.tick(InProcessReader)
                val speed = effectiveSpeed()
                // FF the player still has on - SMART only holding it at 1x for a menu.
                val ffWanted = requestedSpeed() > 1f
                val mode = ffMusicMode
                // The song the game is playing right now (FfMusicKey): read
                // every frame, so a clip starts and stops exactly with it.
                val ffMusicKey = FfMusicKey.current(InProcessReader)
                // STEADY: have each new song rendered on its own, at any
                // speed, so its clip is usually ready before FF needs it.
                if (mode != FfMusicMode.STEADY) requestedKey = null
                else if (ffMusicKey != null && ffMusicKey != requestedKey) {
                    requestedKey = ffMusicKey
                    if (!ffMusicCache.has(ffMusicKey)) runCatching { onFfMusicWanted?.invoke(ffMusicKey) }
                }
                // The clip follows the FF the player asked for, not SMART's 1x stretches: it
                // plays straight through a menu instead of handing over to the game's music
                // (at another spot in the song) and starting over when FF picks up again.
                val clip = if (ffWanted && mode == FfMusicMode.STEADY && ffMusicKey != null) {
                    ffMusicCache.fileIfCached(ffMusicKey)
                } else null
                if (clip != lastFfMusicFile) {
                    lastFfMusicFile = clip
                    onFfMusicChanged?.invoke(clip)
                }
                // Heard at 1x (unless a clip is playing through SMART's 1x); during FF
                // only as SPED-UP (which STEADY falls back to until its clip is ready,
                // or on a ROM it can't render). Slow-mo stays muted.
                val spedUp = speed > 1f && clip == null && mode != FfMusicMode.OFF
                val wantPaused = !((speed == 1f && clip == null) || spedUp)
                if (wantPaused != paused) {
                    paused = wantPaused
                    if (paused) runCatching { track.pause(); track.flush() }
                    else runCatching { track.play() }
                }

                val n = MgbaCore.pkReadAudio(scratch)
                if (n > 0 && !paused) {
                    if (speed == 1f) {
                        track.write(scratch, 0, n)   // blocking → real-time pacing at 1x
                    } else {
                        val factor = if (speed.isInfinite()) measuredSpeed else speed
                        var m = 0
                        for (i in 0 until n / 2) {
                            decimL += scratch[i * 2]; decimR += scratch[i * 2 + 1]; decimN++
                            decimPhase += 1f
                            if (decimPhase >= factor) {
                                decimPhase -= factor
                                spedUpOut[m++] = (decimL / decimN).toShort()
                                spedUpOut[m++] = (decimR / decimN).toShort()
                                decimL = 0; decimR = 0; decimN = 0
                            }
                        }
                        // Never block: FF mustn't be paced by the audio device.
                        if (m > 0) track.write(spedUpOut, 0, m, AudioTrack.WRITE_NON_BLOCKING)
                    }
                }

                if (++fpsFrames >= 120) {
                    val now = System.nanoTime()
                    val fps = fpsFrames * 1e9 / (now - fpsSince)
                    Log.i("pokedaisey", "fps=%.1f (%s)".format(fps, speedTag(speed)))
                    if (speed.isInfinite()) measuredSpeed = (fps / 60.0).toFloat().coerceAtLeast(1f)
                    fpsFrames = 0
                    fpsSince = now
                }

                if (speed.isInfinite()) {
                    next = System.nanoTime()                 // uncapped: no wait
                    if (fpsFrames and 63 == 0) Thread.yield()
                    continue
                }

                val interval = (frameNanos / speed).toLong()
                if (speed != prevSpeed) next = System.nanoTime() + interval else next += interval
                prevSpeed = speed
                val sleep = next - System.nanoTime()
                if (sleep > 1_000_000L) {
                    try {
                        Thread.sleep(sleep / 1_000_000L)
                    } catch (_: InterruptedException) {
                    }
                } else if (sleep < -interval * 4) {
                    next = System.nanoTime()
                }
            }
        } catch (t: Throwable) {
            Log.e("pokedaisey", "emu loop crashed", t)
            loadError = t.message ?: t.toString()
        } finally {
            runCatching { track.stop() }
            runCatching { track.release() }
            MgbaCore.pkDeinit()
            videoBuf = null
        }
    }

    private fun servicePending() {
        pendingSuspend?.let { target ->
            pendingSuspend = null
            val ok = MgbaCore.pkSaveState(target.absolutePath)
            Log.i("pokedaisey", "suspend state save: $ok -> ${target.name}")
            running = false
            return
        }
        val save = pendingSave
        if (save >= 0) {
            pendingSave = -1
            // Keep one level of undo: stash the slot's previous contents first.
            runCatching {
                val cur = states.stateFile(save)
                if (cur.isFile) cur.copyTo(states.bakFile(save), overwrite = true)
                states.thumbFile(save).let { if (it.isFile) it.copyTo(states.bakThumbFile(save), overwrite = true) }
            }
            val ok = MgbaCore.pkSaveState(states.stateFile(save).absolutePath)
            if (ok) writeThumb(states.thumbFile(save))
            onStateResult?.invoke(Hotkeys.Action.SAVE_STATE, save, ok)
        }
        val load = pendingLoad
        if (load >= 0) {
            pendingLoad = -1
            if (states.exists(load)) {
                // Snapshot the live state so the load can be undone.
                runCatching { MgbaCore.pkSaveState(states.undoLoadFile.absolutePath) }
                val ok = MgbaCore.pkLoadState(states.stateFile(load).absolutePath)
                onStateResult?.invoke(Hotkeys.Action.LOAD_STATE, load, ok)
            } else {
                onStateResult?.invoke(Hotkeys.Action.LOAD_STATE, load, false)
            }
        }
        if (pendingUndoSave) {
            pendingUndoSave = false
            val slot = currentSlot
            val ok = runCatching {
                val bak = states.bakFile(slot)
                if (!bak.isFile) return@runCatching false
                bak.copyTo(states.stateFile(slot), overwrite = true)
                states.bakThumbFile(slot).let { if (it.isFile) it.copyTo(states.thumbFile(slot), overwrite = true) }
                true
            }.getOrDefault(false)
            onStateResult?.invoke(Hotkeys.Action.UNDO_SAVE, slot, ok)
        }
        if (pendingUndoLoad) {
            pendingUndoLoad = false
            val ok = states.undoLoadFile.let { it.isFile && it.length() > 0 } &&
                MgbaCore.pkLoadState(states.undoLoadFile.absolutePath)
            onStateResult?.invoke(Hotkeys.Action.UNDO_LOAD, currentSlot, ok)
        }
    }

    private fun writeThumb(dest: File) {
        val buf = videoBuf ?: return
        try {
            // Build the pixels by hand from the raw RGBX bytes (the same byte
            // order EmulatorView uploads as GL_RGBA) instead of
            // copyPixelsFromBuffer(): mGBA leaves the 4th byte as junk, not an
            // opaque alpha, and an ARGB_8888 Bitmap is *premultiplied*, so
            // copying the raw bytes in and reading them back via getPixels()
            // un-premultiplied every pixel by that junk alpha - zero-alpha
            // pixels came back black and the rest scaled - leaving thumbnails
            // that looked like faint, patchy "shades" of the screen. Forcing
            // alpha to 0xFF afterwards (the previous fix) was too late: the
            // colors were already destroyed.
            // Absolute gets: they leave the buffer's position alone (the GL
            // thread uploads from this same buffer).
            val px = IntArray(vw * vh)
            for (i in px.indices) {
                val r = buf.get(i * 4).toInt() and 0xFF
                val g = buf.get(i * 4 + 1).toInt() and 0xFF
                val b = buf.get(i * 4 + 2).toInt() and 0xFF
                px[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
            val full = Bitmap.createBitmap(px, vw, vh, Bitmap.Config.ARGB_8888)
            val tw = 160
            val th = (tw * vh / vw)
            val thumb = Bitmap.createScaledBitmap(full, tw, th, true)
            dest.outputStream().use { thumb.compress(Bitmap.CompressFormat.PNG, 90, it) }
            full.recycle()
            thumb.recycle()
        } catch (t: Throwable) {
            Log.w("pokedaisey", "thumb write failed", t)
        }
    }

    private fun buildAudioTrack(sampleRate: Int): AudioTrack {
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(4096)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build(),
            )
            .setBufferSizeInBytes(minBuf * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
    }

    private companion object {
        /** SPEED_CYCLE steps. Index 0 (1×) is the normal, audio-on state. */
        val SPEED_STEPS = floatArrayOf(1f, 1.5f, 2f, 3f, 4f)
    }
}

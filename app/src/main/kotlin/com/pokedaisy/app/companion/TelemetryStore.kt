package com.pokedaisy.app.companion

import com.pokedaisy.app.companion.i18n.tk
import android.util.Log
import com.pokedaisy.app.companion.data.DecompIconSource
import com.pokedaisy.app.companion.data.InProcessReader
import com.pokedaisy.app.companion.data.SnapshotView
import com.pokedaisy.app.companion.data.TelemetrySampler
import com.pokedaisy.app.companion.data.activeGame
import com.pokedaisy.app.companion.data.hasData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Holds the latest [SnapshotView] for the bottom-screen UI to observe.
 * [refresh] is driven by the emulator thread (EmulatorEngine.onSample) ~1×/sec,
 * so every core memory read happens between frames.
 */
class TelemetryStore {
    // The sampler remembers the game it detected, so a new ROM gets a new one (see [reset]).
    @Volatile private var sampler = TelemetrySampler()

    private val _snapshot = MutableStateFlow(SnapshotView(connected = false, error = tk("starting…")))
    val snapshot: StateFlow<SnapshotView> = _snapshot

    /** A different ROM is about to run in this activity: forget the detected
     * game. Call while the emulator is stopped. */
    /** NOT SUPPORTED's TRY BEST EFFORT ([com.pokedaisy.app.companion.data.BestEffort]), on the next sample. */
    fun requestBestEffort() = sampler.requestBestEffort()

    fun reset() {
        sampler = TelemetrySampler()
        cached = null
        _snapshot.value = SnapshotView(connected = false, error = tk("starting…"))
    }

    // The launch's saved copy ([com.pokedaisy.app.companion.data.SnapshotCache]) while it's shown.
    @Volatile private var cached: SnapshotView? = null
    private var cachedSamples = 0

    /** Shows [v] (the copy saved beside the state this launch resumes) until the live data is in.
     * Call before the engine starts. */
    fun showCached(v: SnapshotView) {
        val game = v.game ?: return
        // The per-game look reads activeGame; another game's icon tables would read this ROM wrong.
        if (game != activeGame) DecompIconSource.tables = com.pokedaisy.app.companion.data.IconTables()
        activeGame = game
        cachedSamples = 0
        cached = v
        _snapshot.value = v
    }

    /** The resume state didn't load after all (the save changed since): its copy is stale. */
    fun dropCached() {
        if (cached == null) return
        cached = null
        _snapshot.value = SnapshotView(connected = false, error = tk("starting…"))
    }

    private var logCount = 0

    /** Call on the emulator thread. Returns the fresh sample so the caller
     * can cheaply reuse fields from it (e.g. [SnapshotView.inBattle] /
     * [SnapshotView.regionMapSectionId] for the FF music player) without a
     * second read. */
    fun refresh(): SnapshotView {
        val s = sampler.sample(InProcessReader)
        val c = cached
        if (c != null) {
            // Live replaces the saved copy once it has data (or won't get any); until then the copy stays.
            if (s.hasData || s.unsupported || ++cachedSamples > CACHED_MAX_SAMPLES) {
                cached = null
                // Icons the copy couldn't read yet (the core wasn't up) can load now.
                DecompIconSource.iconsChanged()
            } else {
                // The sampler sets FireRed while it's still detecting: keep the copy's look.
                activeGame = c.game ?: activeGame
                return s
            }
        }
        _snapshot.value = s
        if (logCount++ % 5 == 0) {
            // pockets=<pocket id>:<count>,... - lets scripts/smoke_test.sh (and
            // manual debugging) verify item CATEGORIZATION specifically, not
            // just that items exist. A regression that tags everything as one
            // pocket (see the 2026-09-21 native-RAM bug) still shows items>0
            // here but only ever prints a single pocket id.
            val pockets = s.items.groupingBy { it.pocket }.eachCount().toSortedMap()
                .entries.joinToString(",") { "${it.key}:${it.value}" }
            Log.i(
                "pokedaisy",
                "telemetry game=$activeGame connected=${s.connected} err=${s.error} " +
                    "party=${s.party.size} inBattle=${s.inBattle} loc=${s.location.mapSecName} (${s.x},${s.y}) " +
                    "items=${s.items.size} pockets=$pockets iconTables=${com.pokedaisy.app.companion.data.DecompIconSource.tables.present}",
            )
        }
        return s
    }

    /** What a savestate made now should carry beside it: the live data, else the saved copy still showing. */
    fun forState(): SnapshotView? = _snapshot.value.takeIf { it.hasData }

    /** Fast, non-1Hz-throttled peek at (battleActiveBattler, battleInputState)
     * — see [TelemetrySampler.sampleBattleInputFast]. Call on the emu thread. */
    fun refreshBattleInputFast(): Pair<Int, Int>? = sampler.sampleBattleInputFast(InProcessReader)

    /** The battle menu's cursor from that poll, for a Gen 1 game (else -1). */
    fun battleMenuCursor(): Int = sampler.battleMenuCursor

    /** The detected game's own gMain + inBattle offset, when it has a native config. */
    fun knownGMain(): Pair<Long, Long>? = sampler.knownGMain

    /** The detected game's gPartyMenu + gPlayerParty, when its config has them. */
    fun knownPartyMenu(): Pair<Long, Long>? = sampler.knownPartyMenu

    private companion object {
        /** ~1 sample a second: a game whose data never comes in drops the copy after this long. */
        const val CACHED_MAX_SAMPLES = 20
    }
}

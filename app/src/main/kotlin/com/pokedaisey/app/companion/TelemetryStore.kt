package com.pokedaisey.app.companion

import android.util.Log
import com.pokedaisey.app.companion.data.InProcessReader
import com.pokedaisey.app.companion.data.SnapshotView
import com.pokedaisey.app.companion.data.TelemetrySampler
import com.pokedaisey.app.companion.data.activeGame
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

    private val _snapshot = MutableStateFlow(SnapshotView(connected = false, error = "starting…"))
    val snapshot: StateFlow<SnapshotView> = _snapshot

    /** A different ROM is about to run in this activity: forget the detected
     * game. Call while the emulator is stopped. */
    fun reset() {
        sampler = TelemetrySampler()
        _snapshot.value = SnapshotView(connected = false, error = "starting…")
    }

    private var logCount = 0

    /** Call on the emulator thread. Returns the fresh sample so the caller
     * can cheaply reuse fields from it (e.g. [SnapshotView.inBattle] /
     * [SnapshotView.regionMapSectionId] for the FF music player) without a
     * second read. */
    fun refresh(): SnapshotView {
        val s = sampler.sample(InProcessReader)
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
                "pokedaisey",
                "telemetry game=$activeGame connected=${s.connected} err=${s.error} " +
                    "party=${s.party.size} inBattle=${s.inBattle} loc=${s.location.mapSecName} (${s.x},${s.y}) " +
                    "items=${s.items.size} pockets=$pockets iconTables=${com.pokedaisey.app.companion.data.DecompIconSource.tables.present}",
            )
        }
        return s
    }

    /** Fast, non-1Hz-throttled peek at (battleActiveBattler, battleInputState)
     * — see [TelemetrySampler.sampleBattleInputFast]. Call on the emu thread. */
    fun refreshBattleInputFast(): Pair<Int, Int>? = sampler.sampleBattleInputFast(InProcessReader)

    /** The detected game's own gMain + inBattle offset, when it has a native config. */
    fun knownGMain(): Pair<Long, Long>? = sampler.knownGMain
}

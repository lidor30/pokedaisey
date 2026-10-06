# PokeDaisy — plan

A single Android app for the AYN Thor (and other dual-screen Android handhelds): it
**embeds an emulator core** (mGBA / `libmgba`) on the top screen and runs the
`tools/android-companion` live-data UI on the bottom screen, in the same process.
Adds RetroArch-style **savestates** and **speed control** on user-configurable
shortcuts.

Lives in ``. Package / `applicationId` `com.pokedaisey.app`.

**Target ROMs**: our `firered-qol.gba` and `emerald-qol` (both export the
`gQolTelemetry` struct), plus **vanilla FireRed**, **vanilla Emerald**, and
**Unbound** via the native-RAM readers — and arbitrary future GBA ROMs/hacks.

## Why this instead of what we have

Today: a patched ROM runs in **RetroArch** on the Thor's top screen, and
`tools/android-companion` reads game state from it over UDP (`127.0.0.1:55355`,
RetroArch's Network Control Interface) and draws Party/Map/Items/Battle on the bottom
screen.

That split is blocked on the Thor: RetroArch's official Android build ships with
`HAVE_COMMAND` off — `READ_CORE_MEMORY` and friends are **not compiled into the
binary at all** (proven last session by symbol inspection + a verbose startup log).
The config toggle and menu entry are inert. See the `android-companion-thor-blocker`
memory.

PokeDaisy removes RetroArch from the picture. We embed `libmgba` and read game
memory via its C API **in-process** (`mCoreGetMemoryBlock`, `core->busRead*`). No
network interface, no `HAVE_COMMAND`, no second app. Savestates and speed become the
core's own native features rather than something we bolt onto a decomp port.

This is _not_ the Goldoire `pokeemerald-dualscreen` approach (native decompilation,
no emulator). That path can never run Unbound or any closed-source hack — no source
to compile. We deliberately chose the emulator path so FireRed + Emerald + **Unbound**

- arbitrary future GBA hacks all work through one code path. Goldoire's repo is still
  the blueprint for the _Android dual-screen shell_ (Presentation API, touch controls,
  ROM gate) — see `android/app/src/main/java/com/pokeemerald/experimental/`
  (`DualScreenPresentation`, `DualScreenBridge`, `GbaControlsView`, `RomGateActivity`).

## Architecture

```
┌─ Android app (com.pokedaisey.app) ──────────────────────────┐
│                                                                       │
│  EmulatorActivity (top screen)          DualScreenPresentation        │
│   ├─ GLSurfaceView: textured quad,       (bottom screen, via          │
│   │   integer-scaled 240x160             DisplayManager)              │
│   ├─ Choreographer vsync → blit          └─ Compose UI: the           │
│   └─ physical buttons + touch overlay        android-companion        │
│        → core->setKeys()                     screens, near-verbatim   │
│                                                                       │
│  ┌─ JNI bridge (libpokedaisey.so) ──────────────────────────────┐     │
│  │  libmgba (static)                                            │     │
│  │   mCore create/loadROM/loadSave/runFrame                     │     │
│  │   mCoreSaveStateNamed / mCoreLoadStateNamed  ← savestates    │     │
│  │   mCoreGetMemoryBlock / busRead*             ← data bridge   │     │
│  │   sync.audioWait / videoFrameWait, frame interval ← speed    │     │
│  └─────────────────────────────────────────────────────────────┘     │
│                                                                       │
│  InProcessReader : replaces RetroArchClient — same GameState out      │
└───────────────────────────────────────────────────────────────────────┘
```

### Emulator core — `libmgba` directly (not libretro, not RetroArch)

We already drive `libmgba-dev` from the headless test harness (`mgba_headless.c`), so
the API is known. Direct use beats the libretro wrapper here: fewer layers, direct
memory-block access, full control of frame pacing.

| Need                     | libmgba primitive                                                                                                                                                                                                                                                                                                                                              |
| ------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Run                      | `mCore` (`mCoreFind` → `init` → `loadROM` → `loadSave`), `core->runFrame()`; core on its own thread (`mCoreThread`) with `mCoreThreadRunFunction` to marshal state ops onto it                                                                                                                                                                                 |
| Video                    | `core->getPixelBuffer` → GL texture upload (240×160, BGRX)                                                                                                                                                                                                                                                                                                     |
| Audio                    | `blip_t` L/R → `AudioTrack`; `mCoreSyncProduceAudio`                                                                                                                                                                                                                                                                                                           |
| Input                    | `core->setKeys(core, bitmask)` each frame                                                                                                                                                                                                                                                                                                                      |
| **Savestate**            | `mCoreSaveStateNamed(core, vf, flags)` / `mCoreLoadStateNamed`; `flags = SAVESTATE_SCREENSHOT \| SAVESTATE_SAVEDATA \| SAVESTATE_RTC \| SAVESTATE_METADATA`. Slot files `<rom>.ss0`..`.ss9` — **byte-compatible with desktop mGBA**, since RetroArch's mgba core and desktop mGBA use this same serializer. Screenshot flag → slot-picker thumbnails for free. |
| **Speed (fast-forward)** | uncapped: `thread->impl->sync.audioWait = false; sync.videoFrameWait = false`. Fixed multiplier (1.5/2/3/4×): target frame interval `= (1s / 59.7275) / multiplier` in the pacing loop; drop audio while active (resample later). Slowdown (0.5×): larger interval, keep audio.                                                                                |
| Rewind                   | `mCoreRewindContextInit` (mGBA built-in ring buffer) — Phase 4                                                                                                                                                                                                                                                                                                 |

JNI wrappers: crib from mGBA's own `src/platform/android/` in the mgba tree.

### Bottom screen — the companion UI, reused

`DualScreenPresentation` (Android `Presentation` on the display from
`DisplayManager.getDisplays(DISPLAY_CATEGORY_PRESENTATION)`) hosts a Compose tree that
is the current `tools/android-companion` UI with the transport swapped.

Reused **near-verbatim** from `tools/android-companion/` (source currently in package
`com.fireredqol.companion`; all of it repackaged under `com.pokedaisey.*` on the way in):

| File(s)                                                                                                                                                                                                                        | Change                                                                                              |
| ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------- |
| `ui/PartyScreen.kt`, `ui/MapScreen.kt`, `ui/ItemsScreen.kt`, `ui/BattleInfoScreen.kt` (was `BattlePanel.kt`), `ui/MonDetailScreen.kt` (was `MonDetailDialog.kt`), `ui/Components.kt`, `ui/AssetImages.kt`, `ui/theme/Theme.kt` | none (repackage)                                                                                    |
| `data/Telemetry.kt`, `data/Gen3Mon.kt`, `data/SnapshotView.kt`                                                                                                                                                                 | none                                                                                                |
| `data/ActiveTables.kt`, `data/SpeciesNames*.kt`, `data/SpeciesTypes*.kt`, `data/MoveData*.kt`, `data/ItemNames*.kt`, `data/MapSecData*.kt`, `data/TypeChart*.kt`, `data/TypeChartUtil.kt`                                      | none — generated tables carry over unchanged                                                        |
| `data/UnboundIconSource.kt`                                                                                                                                                                                                    | none (still reads Unbound icon tables out of ROM RAM — now via `InProcessReader`)                   |
| `assets/pokemon/*.png`, region-map assets                                                                                                                                                                                      | copied as-is                                                                                        |
| `data/RetroArchClient.kt`, `data/NativeReader.kt`, `data/Poller.kt`                                                                                                                                                            | **replaced** by `InProcessReader` (below)                                                           |
| `data/Settings.kt`, `data/Config.kt`                                                                                                                                                                                           | fold into the app's unified settings (keybinds + emulator + data-source)                            |
| `MainActivity.kt`, `TelemetryViewModel.kt`                                                                                                                                                                                     | `MainActivity` → `EmulatorActivity`; ViewModel keeps its `GameState` flow, fed by `InProcessReader` |

### The data bridge — `InProcessReader`

Replaces `RetroArchClient` (UDP) + `NativeReader` (its byte-address math is kept). Same
output contract: a `GameState` snapshot per tick.

- `readBytes(addr, len)`: JNI → `mCoreGetMemoryBlock` for a whole region (WRAM
  `0x02000000`, IWRAM `0x03000000`) with one copy, then slice in Kotlin; or
  `core->busRead8/16/32` for scattered reads. No UDP short-reads, no datagram
  mis-pairing — delete `retroarch.go`-style drain/retry logic.
- Source auto-detect is unchanged: probe the resolved address for the `"QOLT"` magic →
  `gQolTelemetry` struct (`firered-qol` / `emerald-qol`); else native-RAM readers
  (`cfgFireRedRev0` / `cfgFireRedRev1` / `cfgEmerald` / `cfgUnbound` tables already in
  `android-companion`) for **vanilla FireRed / vanilla Emerald / Unbound**.
- Game detect from ROM header `0x080000AC` (`BPRE`/`BPEE`) + rev byte `0x080000BC`
  (FireRed rev0 vs rev1) + size/magic for Unbound — same logic as
  `tools/telemetry-viewer`.
- Throttle the full refresh to ~1 Hz (companion display, not a HUD) — same reason as
  the ROM-side `QolTelemetry_Update()` skip-counter.

### Input & configurable shortcuts

- Physical buttons / gamepads → `KeyEvent` → GBA bitmask → `core->setKeys`.
- A **Keybinds** settings screen maps buttons **and chords** to actions:
  `SAVE_STATE(slot)`, `LOAD_STATE(slot)`, `NEXT_SLOT` / `PREV_SLOT`,
  `FF_HOLD`, `FF_TOGGLE`, `SPEED_CYCLE`, `SLOWMO_HOLD`, `SCREENSHOT`, `RESET`.
- Persisted in DataStore. Defaults chosen to avoid the Thor's face buttons
  (e.g. `L+R+A` save, `L+R+B` load, `R+DPad` speed).
- On-screen toast + optional bottom-screen slot picker (thumbnails from the
  savestate screenshot flag).

### ROM & save management

- SAF picker or drop into `Android/data/com.pokedaisey.app/files/roms/`.
  Every target is just a `.gba` file — `firered-qol` / `emerald-qol` carry their QoL
  patch baked in; vanilla FireRed / Emerald / Unbound need nothing special.
- Per-game `.srm` next to the ROM; `mCore` loads/saves it. Same 128 KB flash format
  emulators and cart dumpers use — bring saves in and out freely.
- Savestates in `files/states/<rom-hash>/ss0..ss9` + a `slot.json` index.

## Build / toolchain

- **`libmgba`** for `arm64-v8a` (and `armeabi-v7a` if the Thor needs it): NDK +
  CMake, `-DBUILD_QT=OFF -DBUILD_SDL=OFF -DBUILD_LIBRETRO=OFF -DBUILD_STATIC=ON
-DBUILD_SHARED=OFF -DUSE_FFMPEG=OFF -DUSE_DISCORD_RPC=OFF`. Pin an mGBA release
  tag. Vendor as a submodule or a fixed tarball under `third_party/`.
- **JNI** (`libpokedaisey.so`): thin C over `mCore`; `externalNativeBuild { cmake }`
  in Gradle links `libmgba` static.
- **App**: Gradle/Kotlin/Compose, mirroring `tools/android-companion`'s setup
  (`compileSdk 34`, `minSdk 26`, Compose BOM). `./gradlew assembleDebug` →
  `adb install`.
- A `Makefile` target (`make apk` / `make install`) to match the
  repo's wrapper convention.

## Phased plan

**Phase 0 — spike (de-risks everything).** NDK-build `libmgba` arm64; minimal
`EmulatorActivity` that loads a ROM, runs `mCoreThread`, blits `getPixelBuffer` to a
`GLSurfaceView`, maps the Thor's buttons, plays audio. **Done when** `firered-qol`,
`emerald-qol`, vanilla FireRed, vanilla Emerald, and Unbound all boot and play at full
speed with sound on the Thor.

> **Phase 0 COMPLETE (2026-09-08).** mGBA `0.10.5` vendored as a submodule; NDK build of
> the bare core links clean (`app/src/main/cpp/CMakeLists.txt`); JNI bridge + GLES2 blit
>
> - audio-paced emu thread + input mapping done
>   (`app/src/main/kotlin/com/pokedaisey/app/`). Core runs on a plain thread, not
>   `mCoreThread`. **Verified on the Thor (device `<adb-serial>`):** all five target ROMs
>   (`firered-qol`, `emerald-qol`, vanilla FireRed, vanilla Emerald, Unbound — the last a
>   32 MiB ROM) boot, render with correct colour/aspect, take gamepad input, and hold a
>   steady **59.4–60.3 fps** (`Log.i("pokedaisey","fps=…")`). Audio device active; sound
>   pacing gates the loop cleanly. No crashes. Next: Phase 1 (savestates).

**Phase 1 — savestates.** `mCoreSaveStateNamed`/`LoadStateNamed` slots 0–9 into
`files/states/<hash>/`, screenshot flag on. Configurable save/load/next-slot buttons.
Toast + bottom-screen slot picker with thumbnails. Verify a state made here loads in
desktop mGBA and vice-versa.

> **Phase 1 COMPLETE (2026-09-08), verified on the Thor.** Slots 0–9 in
> `files/states/<rom-crc32>/ss<N>` (mGBA extended `.ss`) + `ss<N>.png` thumbnails
> captured Kotlin-side from the framebuffer (mGBA build stays png/zlib-free).
> Configurable hotkeys via `files/hotkeys.properties` (`Hotkeys.kt`) — defaults on
> free buttons (Y=save, X=load, stick-clicks=slot ±), edge-triggered, masked from the
> game. On-screen HUD instead of Toast for save/load/slot feedback. Suspend/resume
> replaces fresh-boot: `onPause` writes a `resume` state + stops the loop, `onResume`
> boots + loads + deletes it. **Proven:** load reverts to the exact saved frame
> (screenshot A ≡ screenshot-after-load, ≠ 4s-later); `resume` file created on
> background, consumed on foreground; 60fps held; no crashes. Bottom-screen slot
> picker deferred to Phase 3 (no bottom screen yet). Desktop-mGBA cross-load not
> checked (no desktop mGBA on the dev Mac) — manual check later.

**Phase 2 — speed control.** `FF_HOLD` / `FF_TOGGLE` (uncapped, audio muted),
`SPEED_CYCLE` through 1×/1.5×/2×/3×/4×, `SLOWMO_HOLD` 0.5×. All rebindable. Optional
per-game persisted default multiplier.

> **Phase 2 COMPLETE (2026-09-08), verified on the Thor.** `EmulatorEngine` computes
> `effectiveSpeed()` per frame: FF hold/toggle → `ffMaxSpeed` (default 6×, 0 = uncapped),
> `SPEED_CYCLE` → 1/1.5/2/3/4×, `SLOWMO_HOLD` → 0.5×. Any speed ≠ 1× mutes audio
> (`track.pause()/flush()`, `play()` on return) — resampled FF audio is Phase 4. Pacing:
> 1× stays audio-paced (blocking write); other speeds wall-clock paced to
> `frameNanos / speed`; uncapped skips the wait. New `Hotkeys.Action`s FF_HOLD/FF_TOGGLE/
> SPEED_CYCLE/SLOWMO_HOLD with press+release events; `Hotkeys.Event(action, pressed)`.
> Right/left analog triggers wired straight to FF/slow-mo in `onGenericMotionEvent`
> (RetroArch-style) since L2/R2 are often axes not buttons; an `unmapped key` logcat line
> helps discover device keycodes. **Measured on device:** cycle steps hit exactly
> 90/120/180/240 fps, FF pinned at 360 fps (6×), rapid toggle stress = no audio crash,
> 60 fps baseline intact. `BUTTON_L2/R2` didn't inject via adb (works via real
> hardware/axes + keyboard). Per-game persisted default multiplier: deferred (optional).

**Phase 3 — bottom screen.** Stand up `DualScreenPresentation`; port the
`android-companion` Compose screens + data tables in; implement `InProcessReader` and
delete the UDP/datagram-retry code; wire game + source auto-detect. Party / Map /
Items / Battle panel all live from in-process reads.

> **Phase 3 done (2026-09-08); data + presentation verified on the Thor, UI render is
> eyes-on-device pending.** Whole `com/fireredqol/companion/` tree copied to
> `com/pokedaisey/app/companion/`, repackaged; `RetroArchClient` +
> `MainActivity` + `SettingsDialog` + `TelemetryViewModel` dropped. New: `MemoryReader`
> interface + `InProcessReader` (→ `MgbaCore.pkReadBytes`, a `busRead32` loop in the
> JNI); `TelemetrySampler` (replaces the coroutine socket `telemetryFlow` — synchronous,
> emu-thread); `TelemetryStore` (`StateFlow<SnapshotView>`); `CompanionScreen`
> composable; `DualScreenPresentation` (Presentation + hand-rolled ViewTree
> lifecycle/savedstate/viewmodel owners for Compose). JNI adds `pkReadBytes`,
> `pkFindMagic` (scans IWRAM then EWRAM for `QOLT` — no hardcoded telemetry address),
> `pkRomCode`, `pkRomSize`. `EmulatorEngine.onSample` fires ~1×/sec between frames →
> `TelemetryStore.refresh()`. `PokeDaiseyActivity` uses `DisplayManager` +
> `DisplayListener` to show/dismiss the Presentation on the
> `DISPLAY_CATEGORY_PRESENTATION` display. `pkInit` now falls back to `O_RDONLY` if the
> save file isn't writable (adb-pushed imports). **Verified:** Thor exposes Screen-2 as
> display 4 with `FLAG_PRESENTATION`; `companion presentation shown on display 4`
> logged; with the user's real `.srm` imported, `telemetry game=FIRERED connected=true
party=6 (18,7)` — struct found + decoded in-process, no UDP, no crash, 60fps held.
> Both Thor displays are `FLAG_SECURE` so `adb screencap` can't grab Screen-2 — the
> companion UI _rendering_ needs a look on the physical device. Only the FireRed-struct
> path was exercised; Emerald/Unbound/vanilla-FireRed paths are coded but untested.
> `regionMapSectionId` read as 0 (player in an unnamed interior) → "Unknown area" — real
> data, resolves on named routes.
>
> **Two crashes found + fixed after the user ran it (2026-09-08):** (1) `AssetBitmapCache`
> did `ConcurrentHashMap.getOrPut { … null }` — CHM rejects null values, so a missing
> sprite PNG NPE'd the whole app; fixed with a separate "missing paths" set. (2) The
> mon-detail popup used Compose `Dialog`, which opens a new window and throws "Window
> type mismatch (2037 vs 2)" inside a `Presentation`; rewrote `MonDetailDialog` as an
> in-composition overlay `Box` (no `Dialog`/`Popup` anywhere in the Presentation). After
> the fixes: 90-second soak walking in-game + tapping the bottom screen → 0 crashes,
> `loc=Five Island`, `party=6`, `items=42`, coords updating live.

**Phase 4 — polish.** Unified settings (all keybinds, emulator options, data source),
ROM library UI, rewind (`mCoreRewindContextInit`), touch control overlay, per-game
config, launcher icon. FF audio resampling instead of mute. Cheats (`mCheatDevice`) if
wanted.

> **Phase 4 started (2026-09-08).** Dynamic mon/item icons DONE + device-verified:
> `gQolTelemetry` v2 (460 B) exports the icon-table ROM addresses; `DecompIconSource` +
> `Gfx.kt` decode party/bag sprites live from the ROM with a bundled-PNG fallback. Both
> QoL ROMs rebuilt (`firered-qol` sha1 `221d3aca…`, `emerald-qol` sha1 `f96fa970…` —
> **re-apply them**). `TelemetrySampler.detect()` now waits ~6 s for the `QOLT` magic
> before falling back to native RAM (was mis-detecting QoL ROMs as native at boot).
> Verified on Thor: all 6 `firered-qol` party icons decode from `gMonIconTable`.
>
> **ROM library DONE + verified (2026-09-08):** `LibraryActivity` (Compose) is the new
> launcher — lists `files/roms/*.gba`, SAF import, tap to play, remembers last played;
> hold BACK in-game returns to it. `emerald-qol` v2 confirmed on device in passing.
>
> **In-app Settings DONE + verified (2026-09-08):** `SettingsActivity` from the Library
> — fast-forward cap stepper (0=unlimited…10×, `Prefs.ffMaxSpeed`) and per-action hotkey
> Rebind (captures the next key/button into `hotkeys.properties`). `PokeDaiseyActivity`
> reloads both on resume. Analog triggers stay hardcoded (no keycode).
>
> **Rewind: tried 2026-09-09, reverted.** `mCoreRewindAppend` every frame (non-threaded)
> white-screened the emulator on boot; backed out cleanly. Revisit only with
> `mCoreThread`/`onThread=true` or a coarse snapshot interval.
>
> Still to do: retail icon addresses so the PNG bundle can actually be deleted; live
> region-map compositing; everything else below.

Also folded into Phase 4 (added after Phase 3 review):

- **Dynamic assets — drop the ~3.1 MB bundled PNGs, read sprites/map from the running
  game** (extends the `UnboundIconSource` pattern to FireRed/Emerald decomp + retail):
  - ROM side: `patches-firered/0009` + `patches-emerald/0001` add pointer fields to
    `gQolTelemetry` — `gMonIconTable`, `gMonIconPaletteTable`, `gMonIconPaletteIndices`,
    `gItemIconTable`, and the region-map gfx/tilemap/palette symbols — bump
    `TELEMETRY_SIZE`, rebuild both ROMs, update the decoders (`Telemetry.kt`,
    `telemetry.go`, and the STATIC_ASSERT).
  - App side: a `DecompIconSource` mirroring `UnboundIconSource` — mon icons are
    uncompressed 32×32 4bpp in the decomp (simpler than Unbound's LZ77), item icons are
    LZ77 `{tiles,pal}` (reuse the existing decoder), region map = LZ77 tiles + per-region
    LZ77 tilemap composited to 240×160 (the "done offline" routine from
    `docs/telemetry.md`, run live). Retail FireRed / vanilla Emerald / Unbound (no
    struct) keep fixed ROM addresses in per-game config, like `NativeReader`.
  - Keeps only the pixel font bundled (~90 KB — not extractable as a usable file).
  - Payoff: every ROM (incl. QoL rebuilds and future hacks with custom sprites)
    illustrates itself with no regeneration step.
- **Region-map fallback**: nicer "indoors / unnamed area" state than "Unknown area (N)".

**Phase 5 — bottom-screen touch battle control.** Goldoire's `pokeemerald-dualscreen`
lets you pick FIGHT/BAG/POKEMON/RUN and a move by tapping the bottom screen, but only
because it's a native from-source build with the touch handling written straight into
the battle menu's own C code — impossible for Unbound and beside the point for
vanilla FireRed/Emerald (see "Why this design" above: we deliberately don't compile
from source). We can reach the same _result_ through the mechanism PokeDaisy already
has instead: read the live battle-menu state out of RAM and **inject synthetic
button presses** into the emulated core (`GbaInput.setTouchBits`, already wired for the
existing D-pad/A/B touch overlay) to drive the real menu, exactly as if the user had
pressed the buttons themselves. No ROM patch is required for this to work in principle
— it's the same category of thing telemetry reads and the touch overlay already do —
though Tier A below adds a small telemetry export anyway because it's next to free for
the two ROMs we already build, and removes all guesswork there.

Traced this session in `build/pokefirered/src/battle_controller_player.c` (identical
shape in pokeemerald): `HandleInputChooseAction` reads/writes `gActionSelectionCursor
[gActiveBattler]` and `HandleInputChooseMove` reads/writes `gMoveSelectionCursor
[gActiveBattler]`, each a 2-bit cursor over a 2×2 grid (bit0 = LEFT/RIGHT, bit1 =
UP/DOWN — FIGHT/ITEM/POKEMON/RUN = 0/1/2/3 in the action grid, moves 0-3 the same way
in the move grid) toggled by `JOY_NEW(DPAD_*)`, each guarded so a press that doesn't
apply to the current cursor is a no-op (e.g. DPAD_LEFT only fires `^= 1` when the
cursor is already in the odd column). **That guard means a fixed script — DPAD_LEFT,
DPAD_UP, then DPAD_RIGHT/DPAD_DOWN only if the target cell needs them, then A — always
lands on the right cell no matter where the cursor currently sits.** We never need to
read the live cursor position at all, only whether the expected menu is open right now
(see the state field below) — this removes the biggest source of fragility from a
naive "read cursor, compute delta" approach.

- **Tier A — `firered-qol` / `emerald-qol` (ship first).** Add to `gQolTelemetry`
  (bump `QOL_TELEMETRY_VERSION` 2→3, append fields per the existing convention, update
  the `STATIC_ASSERT`/`structSize` in both `patches-firered/0009-*` and
  `patches-emerald/0001-*`): `u8 battleActiveBattler` (raw `gActiveBattler`, needed to
  index `battleMons[]` back to whichever side is actually choosing — matters in doubles)
  and `u8 battleInputState` (0 = not applicable, 1 = busy/other, 2 = action-select,
  3 = move-select). Compute `battleInputState` by comparing
  `gBattlerControllerFuncs[gActiveBattler]` against `&HandleInputChooseAction` /
  `&HandleInputChooseMove` (drop `static` off `HandleInputChooseAction` — same pattern
  already used for `sItemIconTable` in the v2 icon-table export). **Critical:** compute
  this at the very top of `QolTelemetry_Update()` in `src/qol_telemetry.c`, _before_ the
  existing `if (++sSkipCounter < 60) return;` early-out — that throttle exists for the
  expensive per-mon `GetMonData()` decrypt loop and is fine to keep for the rest of the
  struct, but a menu-state field gated to ~1 Hz would make the touch UI feel laggy and
  risks acting on stale state; this one comparison is two pointer reads, cheap enough to
  run unconditionally every real frame like `frameCounter` already does.
  - Host side: `Telemetry.kt` decodes the two new fields; a new fast poll path in
    `EmulatorEngine`'s per-frame loop (not the existing ~1 Hz `onSample`) reads just
    those bytes via `MgbaCore.pkReadBytes` at a fixed offset and exposes them as their
    own `StateFlow`, independent of the throttled full-struct `TelemetryStore.refresh()`
    — the touch UI's enabled/disabled state needs to track the real menu, not a
    second-old snapshot.
  - New `BattleInputSequencer` (Kotlin): given a target action index (0-3) or move index
    (0-3, only for slots with PP > 0 / within `gNumberOfMovesToChoose` — both already
    derivable from the existing `battleMons[]` export), emits the normalize-then-walk
    button script above as a small FIFO of `(bits, holdFrames, releaseFrames)` steps.
    Drained **one step per real emulated frame** from `EmulatorEngine`'s existing
    `while (running) { MgbaCore.pkSetKeys(input.mask); ... }` loop
    (`EmulatorEngine.kt:216`), calling `input.setTouchBits(...)` — each queued button
    needs an explicit release frame before the next press, or the ROM's `JOY_NEW` edge
    detection (true only on the frame a bit transitions from unset to set) will silently
    swallow back-to-back presses. This drains correctly regardless of PokeDaisy's own
    GAME SPEED / fast-forward multiplier from Phase 2, since FF only changes wall-clock
    pacing between real `runFrame()` calls, not how many of them happen — worth a
    one-line check at 2-4× during verification anyway, not because the design is
    theoretically unsound.
  - UI: extend `BattlePanel.kt`'s existing per-move `Row` (already renders name/type/PP/
    matchup chips) with a tap target, enabled only while `battleInputState` says
    ACTION*SELECT/MOVE_SELECT for `battleActiveBattler` and no sequence is already in
    flight. Tapping a move queues [walk-to-FIGHT if not already there] → [walk-to-that-
    move] → A. Tapping BAG/POKEMON/RUN queues a walk-to-that-cell → A (opens the real
    screen; navigating \_inside* Bag/Party stays out of scope for v1 — finish via the
    existing touch D-pad overlay or physical input once inside).
  - Verify with the headless-mGBA harness first (script a real fight, screenshot around
    the injected sequence, confirm PP/HP/move landed as intended), then on-device against
    the user's real `firered-qol`/`emerald-qol` saves.
  - **Non-goals for v1**: driving Bag-item selection, Party-switch mon selection, and
    doubles' target-select screen — all reachable by opening them via the state machine
    above and finishing with existing touch/physical input. Matches Goldoire's headline
    feature ("select between options and moves"), not full touch coverage of every
    battle screen.

  > **Tier A COMPLETE, on-device confirmed working (2026-09-17).** Shipped as described
  > above — `battleActiveBattler`/`battleInputState` in `gQolTelemetry` v3 (464 bytes),
  > `EmulatorEngine`'s fast ~15 Hz poll path, `BattleInputController` (the sequencer +
  > the "wait for MOVE_SELECT before continuing" state machine, merged into one class),
  > `BattlePanel` tap targets. User confirmed it works on a real fight.
  >
  > **Real bug hit and fixed post-ship: `gBattlerControllerFuncs[gActiveBattler]` is
  > wrong — `gActiveBattler` is not "which battler is choosing".** It's
  > `battle_main.c`'s own per-frame controller-dispatch loop variable:
  > `for (gActiveBattler = 0; gActiveBattler < gBattlersCount; gActiveBattler++)
gBattlerControllerFuncs[gActiveBattler]();`. By the time `QolTelemetry_Update()` runs
  > (from `AgbMain()`, after that loop has already dispatched every battler for the
  > frame), `gActiveBattler == gBattlersCount` — one past the last valid battler, not any
  > particular slot. Reading `gBattlerControllerFuncs[gActiveBattler]` there is reading
  > garbage, which is exactly why the first build shipped with `battleInputState` stuck
  > at BUSY forever (confirmed by the user: "I don't get battle controls").
  > **Fix**: scan `gBattlerControllerFuncs[i]` for every `i` in `[0, gBattlersCount)`
  > directly and match against `HandleInputChooseAction`/`HandleInputChooseMove` — those
  > two functions are the "Player" controller's own (`battle_controller_player.c`), never
  > assigned to an AI/link opponent's slot, so the scan is unambiguous and doesn't need
  > `gActiveBattler` at all. **This changes what Tier B/C need**: no `gActiveBattler`
  > address required — just the `gBattlerControllerFuncs` array's base address plus the
  > two function addresses, then scan host-side the same way. See
  > [[gactivebattler-loop-var-gotcha]].

- **Tier B — vanilla FireRed rev0/rev1 + vanilla Emerald (no ROM patch).** Same
  `BattleInputController` completely unchanged (game-agnostic) — only the memory-read
  side differs: instead of one fixed struct offset, read the whole
  `gBattlerControllerFuncs[MAX_BATTLERS_COUNT]` array (a handful of 4-byte function
  pointers) from native RAM and scan host-side against that build's own
  `HandleInputChooseAction`/`HandleInputChooseMove` addresses — same scan the ROM-side
  fix above does, just done in Kotlin instead of C since there's no telemetry export to
  compute it for us. Three addresses needed per game (not four — no `gActiveBattler`):
  `gBattlerControllerFuncs` array base, `HandleInputChooseAction`, `HandleInputChooseMove`.
  _(Superseded 2026-09-17: originally scoped around `gActiveBattler` +
  `gBattlerControllerFuncs[gActiveBattler]`, same bug as Tier A's first ship — corrected
  above before implementation started, so no rework needed here.)_

  > **Tier B implemented 2026-09-18, not yet device-verified.** Addresses sourced by
  > building genuinely vanilla (unpatched) ROMs in the `firered-qol-build` Docker image
  > and reading `arm-none-eabi-nm` on the resulting `.elf` — **the linker `.map` file
  > turned out to NOT list `static` symbols at all** in this toolchain (contrary to the
  > original assumption above), so `nm` was the only way to find `HandleInputChooseAction`,
  > which is `static` and, being a generic name, exists identically-named in **four**
  > different FireRed source files (the Wally/Safari/Recorded-battle controllers each
  > define their own local copy). Disambiguated by cross-referencing each candidate
  > address against `battle_controller_player.o`'s `.text` range from the `.map` file
  > (which _does_ list per-object-file section ranges, just not the local symbols inside
  > them). FireRed rev1 build verified byte-identical to retail (sha1 `dd5945db…`);
  > Emerald verified byte-identical too (sha1 `f3ae0881…`); rev0 built but **not**
  > cross-checked against a real retail 1.0 hash (none on file) — same caveat the rest of
  > `NATIVE_FIRERED_REV0` already carries.
  >
  > - FireRed: `gBattlerControllerFuncs` `0x03004fe0` (identical rev0/rev1, matching the
  >   established pattern for this config); `HandleInputChooseAction`/`HandleInputChooseMove`
  >   rev0 `0x0802e438`/`0x0802ea10`, rev1 `0x0802e44c`/`0x0802ea24` (code addresses, so
  >   they do shift slightly between revisions unlike the RAM globals).
  > - Emerald: `gBattlerControllerFuncs` `0x03005d60`, `HandleInputChooseAction`
  >   `0x08057588`, `HandleInputChooseMove` `0x08057bfc`.
  > - `NativeConfig` gained the three fields + `hasBattleInputAddrs`; new
  >   `readNativeBattleInputFast()` in `NativeReader.kt` does the exact same
  >   `gBattlerControllerFuncs[]` scan as the ROM-side fix (reads all
  >   `MAX_BATTLERS_COUNT` slots, matches by address, gated on `gMain`'s existing
  >   inBattle bit) — shared, not duplicated, by both `readNativeTelemetry` (the ~1 Hz
  >   snapshot) and `TelemetrySampler.sampleBattleInputFast` (the fast poll
  >   `BattleInputController` needs). `BattlePanel`/`BattleInputController` needed zero
  >   changes — both were already written against the `(battler, state)` pair
  >   game-agnostically. **`NATIVE_UNBOUND` explicitly zeroes these three fields**
  >   (rather than silently inheriting rev0's, which are almost certainly wrong code
  >   addresses for CFRU's different binary) so Tier C stays honestly unimplemented.
  > - Compiles clean, `assembleDebug` succeeds.
  >
  > **Real bug hit and fixed on first on-device try (2026-09-18): the hardcoded function
  > addresses never matched — ARM/Thumb interworking bit.** User reported "no battle
  > controls" on a real vanilla-ROM fight. Root-caused (not guessed) by rebuilding vanilla
  > `firered_rev1` again and disassembling the exact site that does
  > `gBattlerControllerFuncs[i] = HandleInputChooseAction;`
  > (`HandleChooseActionAfterDma3`, found the same way as `HandleInputChooseAction`
  > itself — 4 static instances, disambiguated by `.text` range): the literal pool holds
  > `.word 0x0802e44d` — **odd**, bit 0 set for Thumb-mode interworking (`BX`/indirect
  > calls require it or the CPU would decode Thumb bytes as ARM and crash) — while
  > `arm-none-eabi-nm` reported the plain even code address `0802e44c` for that same
  > symbol. This toolchain's `nm` doesn't reflect the ELF Thumb-symbol convention, so
  > every hardcoded function address above was off by exactly this one bit and the scan
  > never matched anything, permanently reading BUSY. Data symbols
  > (`gBattlerControllerFuncs` itself) aren't affected — only the two function addresses.
  > **Fix**: mask bit 0 off both sides of the comparison in `readNativeBattleInputFast()`
  > rather than hand-correct each hardcoded constant — robust regardless of which
  > convention either side follows, and doesn't require re-deriving every address by hand.
  > Not yet re-verified on-device after the fix.

- **Tier C — Unbound.** Same five addresses needed (the three from Tier B plus
  `CompleteWhenChoseItem`/`WaitForMonSelection` added for the Bag/Party BACK button —
  see the Tier A status note below), but Unbound is closed-source — no `.map`/`nm`
  output to read them from. _(Superseded — see the implemented note below; this turned
  out not to need a disassembler pass or a live probe at all.)_

  > **Tier C implemented 2026-09-18, not yet device-verified.** The user has the actual
  > pinned v2.1.1.1 ROM locally (sha1 `b4776b82…`, matches `unbound-telemetry`'s pin) —
  > rather than reasoning about it or live-probing, compared raw ROM bytes directly
  > against a freshly-built genuinely-vanilla `make firered` (rev 0, sha1 `41cb23d8…`):
  > `WaitForMonSelection`/`CompleteWhenChoseItem` are **byte-for-byte identical at the
  > same address** in both ROMs. `HandleInputChooseAction`/`HandleInputChooseMove`
  > themselves differ (CFRU visibly redesigns the move-select screen) — but the glue
  > functions that store their addresses into `gBattlerControllerFuncs[]`
  > (`HandleChooseActionAfterDma3`/`HandleChooseMoveAfterDma3`) are _also_ byte-identical
  > and store the _same target addresses_ in both ROMs — CFRU patched those two
  > functions in place rather than relocating them. `gBattlerControllerFuncs`'s own
  > address (`0x03004fe0`) is a literal embedded in that same matching byte range, so
  > it's directly confirmed too. Net result: **`NATIVE_UNBOUND` now simply equals
  > `NATIVE_FIRERED_REV0`** — no separate address table needed at all, and no code
  > changes to `BattlePanel`/`BattleInputController`/`BattleControlsScreen`, which were
  > already fully game-agnostic. This also explains _why_ CFRU is documented elsewhere
  > (`unbound-telemetry`) to keep so many stock rev-0 RAM addresses — it's an ASM-hook
  > patch on the retail binary, not a full rebuild-with-insertions, so untouched regions
  > never move. Static-analysis-verified, not yet confirmed in an actual Unbound battle —
  > Tiers A and B both shipped with equally careful reasoning and still had a real bug
  > each only caught by actual play (see [[gactivebattler-loop-var-gotcha]],
  > [[arm-thumb-bit-nm-gotcha]]), so treat this the same way until tested.

## Open questions / risks (need the Thor to answer)

- **Presentation on the Thor**: does the second screen show up in
  `DisplayManager` as a `DISPLAY_CATEGORY_PRESENTATION` display? Goldoire and
  samyost1's `zelda3-android` / `tmc-android` got Presentation working on it, so
  there's precedent — but confirm on the actual unit. If not, fall back to the
  "normal multi-window" placement `android-companion` already assumes.
- **`libmgba` NDK build**: minor CMake flag iteration expected; 32-bit `v7a` may need
  extra care.
- **FF audio**: mute-while-active first (an afternoon); proper resampling is a few
  days — Phase 4.
- **Frame pacing on Android**: drive the blit from `Choreographer`, run the core on
  its own thread, single-frame queue to bound latency.
- **Retail FR/Emerald native-RAM reads** are still not live-verified (addresses from
  linker maps only) — same caveat as `android-companion`; Unbound path is verified.

## Relationship to existing tools

- `tools/telemetry-viewer` (Go, macOS): unchanged. Still the desktop viewer; still
  works against RetroArch on the Mac.
- `tools/android-companion`: its UI + data tables become PokeDaisy's bottom screen.
  Keep the directory as the source of the generated Kotlin tables (or move generation
  under `` and retire it — decide at Phase 3).
- ROM patches (`patches-firered/`, `patches-emerald/`): unchanged. PokeDaisy runs
  the built ROMs; it doesn't build them.

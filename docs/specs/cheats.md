# Spec: cheat support (.cht files + Action Replay / GameShark codes)

Status: implemented 2026-10-08 (see docs/DEVELOPMENT.md's "Cheats" for what was built and where it
differs from this plan: file formats parsed in Kotlin, Kotlin owns the list, only enabled cheats reach
the core). Open: the manual Thor pass below, README line on release.
Written for a fresh session; read CLAUDE.md first.
Origin: a Reddit user asked "Is there .cht / action replay code support?" PokeDaisy has none today
(grep for cheat/GameShark finds nothing in `app/`).

## Goal

Let a player turn cheats on while playing, from a list per game:
1. Import a cheat file (`.cht`, RetroArch/libretro format; also mGBA's own `.cheats` format).
2. Type in a code by hand (Action Replay v1/v2, GameShark, Code Breaker, PARv3; GBA only).
3. Toggle each cheat on/off, remembered per ROM.

Non-goals (v1): a built-in code database (no bundled codes, repo is public and codes are
community content), Game Boy / Color codes (GB support is switched off anyway, see
`GAME_BOY_SUPPORT`), searching memory for new cheats.

## What mGBA already gives us (third_party/mgba 0.10.5)

`include/mgba/core/cheats.h`, `src/core/cheats.c`, `src/gba/cheats.c`, `src/gba/cheats/*.c`:
- `core->cheatDevice(core)` -> `struct mCheatDevice*` (one per core).
- `device->createSet(device, name)` -> `mCheatSet*`; `mCheatAddSet`, `mCheatRemoveSet`.
- `mCheatAddLine(set, line, type)` parses one code line. GBA types (`gba/cheats.h`):
  `GBA_CHEAT_AUTODETECT`, `GBA_CHEAT_GAMESHARK`, `GBA_CHEAT_PRO_ACTION_REPLAY`,
  `GBA_CHEAT_CODEBREAKER`. `GBA_CHEAT_AUTODETECT` guesses; offer it as the default.
- `mCheatParseFile` (mGBA `.cheats`), `mCheatParseLibretroFile` (libretro `.cht`),
  `mCheatParseEZFChtFile`, `mCheatSaveFile`, `mCheatAutosave`.
- Per-set `enabled` flag; `mCheatRefresh(device, set)` after changing it.
- Encrypted AR/GameShark v1-v3 codes need the right seeds/version; mGBA handles it through
  `GBACheatSetGameSharkVersion` and autodetect. Codes that mGBA can't parse must be reported, not
  silently dropped (`mCheatAddLine` returns false).

## Risks to check FIRST (spike before building UI)

1. **Does the cheat engine work in our build?** `app/src/main/cpp/CMakeLists.txt` sets
   `USE_DEBUGGERS OFF`. GBA cheat sets hook the game with `GBASetBreakpoint` (`src/gba/cheats.c`
   ~line 24). Confirm cheats actually fire with debuggers off; if not, either turn `USE_DEBUGGERS`
   on (check size / speed impact, FF must stay fast) or apply the codes ourselves once per frame.
   Quick test: headless via `native-capture/mgba_dump` with a known code (e.g. infinite money) and
   read the value with the existing memory dump.
2. **Savestates**: a state made with a cheat on carries the cheated memory. Loading it with the
   cheat off keeps the cheated values. That's normal emulator behaviour, but see the save-safety
   section below.
3. **Threading**: all core calls go through the emu thread / `pk_coreLock` like `pkRunFrame`.
   Add/toggle cheats under the same lock (`pokedaisy_jni.c`), never from the UI thread directly.
4. **The second core `rg`** (FF-music renderer, `FfMusicRenderer`) must never get cheats. Only
   `pkMainCore()` does.

## Native side (`app/src/main/cpp/pokedaisy_jni.c`, `MgbaCore.kt`)

New JNI functions, same style as the existing `pk*` ones (`Java_com_pokedaisy_app_MgbaCore_...`):
- `pkCheatsLoadFile(path) -> int` (count added; -1 on parse error): tries libretro `.cht`
  first, then mGBA `.cheats`.
- `pkCheatsAddCode(name, code, type) -> boolean` (one set per user entry, multi-line codes in a
  single string split on newline / `+`).
- `pkCheatsList() -> String` (one record per set: index, name, enabled, line count; use the
  same record/field-separator approach as `raAchievementList`, built from real UTF-8).
- `pkCheatsSetEnabled(index, enabled)`, `pkCheatsRemove(index)`, `pkCheatsClear()`.
- Apply on core start: after `core->loadROM` / `reset` in `pkInit`, before the first frame.

## Kotlin side

- Storage: `filesDir/cheats/<rom crc>.cheats` in mGBA's own format (`mCheatSaveFile`), keyed by ROM
  CRC like the other per-ROM caches (not file name: renames and archives must still match). Enabled
  flags live in the same file. Back it up? No: it's not a save. Never write inside the saves folder.
- Prefs (`Prefs.kt`): `cheatsEnabled` master switch (default OFF), so existing players see no change.
- Load the ROM's file into the core where the engine starts the game (`EmulatorEngine.kt`,
  next to the save-backup / resume logic). The master switch off = no cheat loaded at all.
- Import: a file picker in the top-screen Settings (like the ROM-folder pickers in
  `LibraryActivity` / `StorageAccess`; needs All files access on Android 11+). Copy the picked
  file's cheats into the per-ROM file, don't keep a path.
- Typing a code: the keyboard needs the **top screen** (the Presentation can't host one well, same
  reason RetroAchievements sign-in lives there). Fields: NAME, CODE, TYPE (AUTO / GAMESHARK /
  ACTION REPLAY / CODEBREAKER via `OptionSelector`; never cycle more than two values).

## UI (follow CLAUDE.md "UI work" strictly)

- Top-screen Settings: new sub-page CHEATS (built from `GroupedRows` / `SettingRow`,
  `SettingRows.kt`): master toggle, ADD CODE, IMPORT FILE, then a list with a row per cheat
  (`LABEL  ON/OFF`), tap toggles, long-press / second button REMOVE with `OptionConfirm`.
- Companion SETTINGS: only the toggles (no keyboard needed): list of the current game's cheats
  with ON/OFF. In a tab-free place, e.g. a TOOLS entry; do not add to the 5-tab bar.
- Use `tr("...")` for every string and add EN/JA/FR/DE/IT/ES lines in the matching `Tr<Area>.kt`
  (`TranslationsTest` fails otherwise). Pixel corners, `soundClickable`, no new vector icons.
- Add Paparazzi + ui-preview shots (`WideCompanionScreenshotTest` too) and LOOK at the PNGs.
- Library INFO could show "CHEATS: 3 ON" for the game (optional).

## Interactions that must be handled

- **RetroAchievements** (`achievements/RetroAchievements.kt`, `pokedaisy_ra.c`): RA's rule is no
  cheats with achievements. PokeDaisy is softcore-only, which RA allows to use savestates, but the
  convention (RetroArch and others) is to **not award achievements while cheats are active**.
  Decision for the owner (default proposal): when a cheat is ON and the player is signed in, pause
  achievement processing (stop calling `rc_client_do_frame`, or `rc_client_set_...` pause API) and
  show an `AchievementPopupHost` notice "CHEATS ON: ACHIEVEMENTS PAUSED". Re-enable when all are
  off. Check rcheevos 12.5 for the proper call before inventing one.
- **Companion data**: cheats that write party/bag memory just make the companion show the cheated
  values; fine. Cheats that corrupt the game can crash the Poller's reads; reads must stay
  bounds-checked (they already return null on bad data; add a test fixture if something breaks).
- **Save files are sacred** (CLAUDE.md): a cheat can write junk into SRAM-backed memory and the
  game then saves it. Mitigations: `SaveBackups` already copies the save at every start (keep it
  before cheats load), and the CHEATS page should say so in one line. Do not add any save-writing
  path.
- **FF / SMART FF / battle automation** (`BattleInputController`): unaffected, but test one
  battle with a cheat on.
- **Auto-resume**: loading the resume state restores memory as it was; cheats re-apply on the
  next frame. No special handling, but verify toggling a cheat OFF then resuming doesn't leave the
  hook installed (a stale breakpoint patch is the failure to look for).

## Tests

- JVM/unit: parsing of a sample `.cht` (libretro format: `cheats = N`, `cheatN_desc`,
  `cheatN_code`, `cheatN_enable`) and a typed AR code through the JNI or a Kotlin port; list/
  toggle/remove round trip. Prefer testing on the host via `native-capture/mgba_dump`: add a
  `cheat <code>` command, run 60 frames, dump, assert the byte changed (this is the "spike" test
  for risk 1).
- Translation test passes; Paparazzi screenshots for the CHEATS page (empty, 3 cheats, import
  error).
- Manual on the Thor: FireRed, infinite-money code, toggle on/off mid-game, then quit and resume.

## Docs

- README feature list: one line, only after it ships.
- `docs/DEVELOPMENT.md`: short "Cheats" section (file location, format, RA behaviour).
- CLAUDE.md: add a **CHEATS** paragraph in the same style as the others (storage key, RA rule,
  the debugger-build finding from risk 1).

## Suggested order

1. Spike risk 1 (headless, one hard-coded code). Decide: `USE_DEBUGGERS` on, or per-frame apply.
2. JNI + Kotlin storage, master switch, load on start. Verify on the Thor with a typed code.
3. Top-screen CHEATS page (add / import / toggle / remove), translations, screenshots.
4. RA pause behaviour + popup.
5. Companion toggle list, docs, release notes.

## Open questions for the owner

- RA: pause achievements while cheats are on (proposed), or just warn?
- Ship a small set of well-known codes per supported game? (Proposed: no, import/type only.)
- Game Boy codes later, if GB support is brought back.

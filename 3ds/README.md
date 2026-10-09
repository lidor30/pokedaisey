# PokeDaisy for the Nintendo 3DS (early preview)

A homebrew port of PokeDaisy: the game runs on the top screen (libmgba, the
same mGBA 0.10.5 submodule the Android app uses) and the companion lives on
the touch screen. It is a rewrite, not a recompile - the Android app is Kotlin
and Jetpack Compose, which the 3DS can't run - so this folder holds a small,
portable C core that re-implements the companion's reading and drawing, plus a
3DS host and a desktop preview tool around it.

**Status: early preview.** It builds, boots and works in the Azahar emulator
(below); it has not been run on a real 3DS yet, so speed on hardware is
unknown. Target the **New 3DS / New 3DS XL / New 2DS XL** first; the original
3DS / 3DS XL / 2DS may not hold full speed (see "Hardware").

## What works

- Game list (`sdmc:/pokedaisy/roms/*.gba`), D-pad or stylus.
- The game on the top screen, with sound (ndsp), in three SCREEN modes:
  **PIXEL 1x** (pixel for pixel, centred), **SHARP 1.5x** (fills the height;
  the default) and **STRETCH** (fills the screen). The smooth two use mGBA's
  own "sharp bilinear" look: the frame at 2x nearest, sampled linearly.
- **Fast-forward**: the ▶▶ chip on the companion toggles it, ZR (New 3DS) holds
  it; 2x / 3x / 4x in SETTINGS. The game is muted meanwhile.
- **Save state**: one slot per game (`sdmc:/pokedaisy/states/`). A state never
  carries the save data, so loading one can't write an old copy over the save
  file; the save is backed up before every load anyway.
- Saves next to the ROM (`<rom>.sav`, like mGBA's own 3DS build), and a backup
  of the save before every start in `sdmc:/pokedaisy/roms/pokedaisy-backups/`
  (newest 10 per game) - the repo's "save files are sacred" rule.
- Settings kept in `sdmc:/pokedaisy/settings.ini`.
- The companion, in the app's FireRed OPTION-screen look:
  - **PARTY**: the six slots (name with its ♂ / ♀, level, HP bar, status) in the
    app's FireRed party palette; tap one for its summary (moves, type, PP).
  - **BATTLE**: opens by itself in a battle and goes back after it - the foe's
    and your cards, your moves with the app's verdicts (SUPER 2x / NEUTRAL 1x /
    RESISTED 1/2x / IMMUNE 0x / STATUS).
  - **BAG**: one pocket at a time in the game's own order and colours (FireRed's
    or Emerald's bag), item names and counts as the game prints them, a list a
    drag scrolls, the tapped item's description.
  - **SETTINGS** (the gear): place, money, file and speed, then SCREEN and
    FAST-FORWARD (pick-lists), SAVE STATE / LOAD STATE / LEAVE GAME (confirmed).
- Games: retail English **FireRed / LeafGreen (rev 0 and 1)** and **Emerald**.
  ROM hacks, other languages, Ruby / Sapphire, Game Boy and the QoL builds'
  telemetry struct aren't ported yet; they show "not supported".

## Controls

| | |
|---|---|
| A / B / START / SELECT / L / R / D-pad or circle pad | the GBA's buttons |
| Touch screen | the companion |
| X | next companion tab |
| Y | companion back (close a summary, a pick-list, a confirm) |
| ZR (New 3DS) | fast-forward while held |
| Hold X + Y (1 s) | save and return to the game list (or SETTINGS > LEAVE GAME) |
| START on the game list | quit to the Homebrew Launcher |

## Installing on a 3DS

Needs a 3DS with custom firmware (Luma3DS) and the Homebrew Launcher.

1. Copy `pokedaisy.3dsx` to `sdmc:/3ds/`.
2. Put your own legally dumped `.gba` files (and any `.sav` with the same name)
   in `sdmc:/pokedaisy/roms/`.
3. For sound, the DSP firmware must be dumped once (`sdmc:/3ds/dspfirm.cdc`, the
   DSP1 homebrew does it) - without it the game runs silent.

## Building and testing

Everything runs in Docker, so it works the same on a Mac or a cloud session.

```sh
cd 3ds
make preview       # the companion from the RAM fixtures -> build/shots/*.png
make 3ds           # ctr/pokedaisy.3dsx (devkitpro/devkitarm image)
make fixture-roms  # build/fixture-*.gba test ROMs (below)
make azahar-test   # the .3dsx in the Azahar 3DS emulator -> build/azahar/*.png
make tables        # regenerate core/*_gen.c and ctr/icon.png from the app
```

- **`make preview`** is this port's ui-preview: `desktop/pd_preview.c` reads a
  fixture's `ewram.bin` / `iwram.bin` (`app/src/test/resources/fixtures/`) and
  drives the companion with taps found by hit id - a pressed slot, a summary,
  BATTLE, BAG (an item, a drag, the next pocket), SETTINGS, a pick-list, a
  confirm - drawing each as the 3DS's two screens. No ROM, no emulator: look at
  every UI change here.
- **Fixture ROMs** (`test/fixture_rom.c`) are a few lines of GBA homebrew that
  copy a fixture's RAM into the GBA and idle, with FireRed's or Emerald's game
  code in the header. The companion can't tell them from the real game, so the
  whole app can be tested without a Pokémon ROM. They hold no game code or
  data, only the fixture's save state.
- **`make azahar-test`** boots the real `.3dsx` in Azahar (the
  `linuxserver/azahar` image, on Xvfb) on the FireRed fixture ROM and taps
  through it like a player: game list, PARTY, BAG, SETTINGS, each SCREEN mode,
  fast-forward, a save state and loading it - screenshots in `build/azahar/`,
  and the emulated SD card (states, backups, settings) in `build/azahar/sd/`.
  Azahar shows that it works, not how fast: speed has to be measured on a
  console.

## Layout

| Path | What |
|---|---|
| `core/` | Portable C, no platform code: game detection (`pd_game`, the app's `NativeConfig` addresses), the snapshot reader (`pd_snapshot`: party, battle, bag, money, place - ports `Gen3Mon.kt` / `readNativeTelemetry` / `readNativeBag`), the canvas (`pd_canvas`: pixel-stepped corners, layered OPTION frames, GBA-shadowed and wrapped text, drawn cursors), the companion (`pd_ui.c` frame + `pd_ui_party` / `_battle` / `_bag` / `_settings` / `_widgets`), the game list (`pd_menu`) and the settings file (`pd_settings`) |
| `core/*_gen.c` | Generated: names / moves / type chart / map sections / item text / gender ratios from the app's Kotlin tables (`tools/gen_tables.py`), Pixel Operator as a 1-bit font plus drawn ♂ ♀ (`tools/gen_font.py`) |
| `ctr/` | The 3DS host: `main.c` (game loop, ndsp audio, HID, files, states) and `gpu.c` (citro2d: the game and canvases written into textures in the GPU's tiled order, drawn as quads), and the CMake build against the mGBA submodule |
| `desktop/` | The preview tool and its small PNG writer |
| `test/` | The fixture ROM and the Azahar script |
| `tools/` | The generators |

Keep in sync with the app: `core/pd_game.c`'s addresses come from
`NativeReader.kt`, `pd_snapshot.c` mirrors `Gen3Mon.kt` / `readNativeTelemetry`,
and the tables are regenerated (`make tables`) whenever the app's change.

GPU notes (found in Azahar): textures are written by the CPU straight into
linear memory in the tiled order, top row first (v = 1). Display transfers into
textures came out upside down, and into VRAM textures rotated, so they're
not used; nor is render-to-texture.

## Hardware

- **New 3DS / New 3DS XL / New 2DS XL** (804 MHz, 256 MB): the target; the app
  turns on the faster clock (`osSetSpeedupEnable`).
- **3DS / 3DS XL / 2DS** (268 MHz, 128 MB): mGBA alone is close to its limit
  there, and the companion's drawing shares the CPU. Expect slowdowns; PIXEL 1x
  is the cheapest screen mode, and fast-forward may not get past 1x.

## Next steps

- Run it on a New 3DS: measure the frame time per screen mode, check the
  texture orientation, sound and save writes on hardware.
- Port more of the app: the region MAP, GUIDE, POKéDEX, the battle's FOE TEAM,
  the rest of `NativeConfig` (hacks, other languages, Ruby / Sapphire), ROM art
  (`RomArt`) for the party icons.
- More state slots with thumbnails, and resuming where the player left off (with
  the app's `pkStateMatchesSave` check first).

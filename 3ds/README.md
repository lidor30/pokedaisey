# PokeDaisy for the Nintendo 3DS (early preview)

A homebrew port of PokeDaisy: the game runs on the top screen (libmgba, the
same mGBA 0.10.5 submodule the Android app uses) and the companion lives on
the touch screen. It is a rewrite, not a recompile - the Android app is Kotlin
and Jetpack Compose, which the 3DS can't run - so this folder holds a small,
portable C core that re-implements the companion's reading and drawing, plus a
3DS host and a desktop preview tool around it.

**Status: first slice.** It builds, boots and works in the Azahar emulator
(below); it has not been run on a real 3DS yet, so speed on hardware is
unknown. Target the **New 3DS / New 3DS XL / New 2DS XL** first; the original
3DS / 3DS XL / 2DS may not hold full speed (see "Hardware").

## What works

- Game list (`sdmc:/pokedaisy/roms/*.gba`), D-pad or stylus.
- The game at 1x (pixel-perfect, centred) on the top screen, with sound (ndsp).
- Saves next to the ROM (`<rom>.sav`, like mGBA's own 3DS build), and a backup
  of the save before every start in `sdmc:/pokedaisy/roms/pokedaisy-backups/`
  (newest 10 per game) - the repo's "save files are sacred" rule.
- The companion, in the app's FireRed OPTION-screen look:
  - **PARTY**: the six slots (name, level, HP bar, status) in the app's FireRed
    party palette; tap one for its summary (moves, type, PP).
  - **BATTLE**: opens by itself in a battle and goes back after it - the foe's
    and your cards, your moves with the app's verdicts (SUPER 2x / NEUTRAL 1x /
    RESISTED 1/2x / IMMUNE 0x / STATUS).
  - **INFO**: game, place, money, party size, speed.
- Games: retail English **FireRed / LeafGreen (rev 0 and 1)** and **Emerald**.
  ROM hacks, other languages, Ruby / Sapphire, Game Boy and the QoL builds'
  telemetry struct aren't ported yet; they show "not supported".

## Controls

| | |
|---|---|
| A / B / START / SELECT / L / R / D-pad or circle pad | the GBA's buttons |
| Touch screen | the companion |
| X | next companion tab |
| Y | companion back (close a summary) |
| Hold X + Y (1 s) | save and return to the game list |
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
  renders every companion tab - including a pressed slot and an open summary -
  as the 3DS's two screens. No ROM, no emulator: look at every UI change here.
- **Fixture ROMs** (`test/fixture_rom.c`) are a few lines of GBA homebrew that
  copy a fixture's RAM into the GBA and idle, with FireRed's or Emerald's game
  code in the header. The companion can't tell them from the real game, so the
  whole app can be tested without a Pokémon ROM. They hold no game code or
  data, only the fixture's save state.
- **`make azahar-test`** boots the real `.3dsx` in Azahar (the
  `linuxserver/azahar` image, on Xvfb) on the FireRed fixture ROM, taps a party
  slot and switches tabs with key presses, and saves screenshots. Azahar shows
  that it works, not how fast: speed has to be measured on a console.

## Layout

| Path | What |
|---|---|
| `core/` | Portable C, no platform code: game detection (`pd_game`, the app's `NativeConfig` addresses), the snapshot reader (`pd_snapshot`, ports `Gen3Mon.kt` / `readNativeTelemetry`), the canvas (`pd_canvas`: pixel-stepped corners, layered OPTION frames, GBA-shadowed text), the companion (`pd_ui`) and the game list (`pd_menu`) |
| `core/*_gen.c` | Generated: names / moves / type chart / map sections from the app's Kotlin tables (`tools/gen_tables.py`), Pixel Operator as a 1-bit font (`tools/gen_font.py`) |
| `ctr/` | The 3DS host (`main.c`: libctru framebuffers, ndsp audio, HID, files) and its CMake build against the mGBA submodule |
| `desktop/` | The preview tool and its small PNG writer |
| `test/` | The fixture ROM and the Azahar script |
| `tools/` | The generators |

Keep in sync with the app: `core/pd_game.c`'s addresses come from
`NativeReader.kt`, `pd_snapshot.c` mirrors `Gen3Mon.kt` / `readNativeTelemetry`,
and the tables are regenerated (`make tables`) whenever the app's change.

## Hardware

- **New 3DS / New 3DS XL / New 2DS XL** (804 MHz, 256 MB): the target; the app
  turns on the faster clock (`osSetSpeedupEnable`).
- **3DS / 3DS XL / 2DS** (268 MHz, 128 MB): mGBA alone is close to its limit
  there, and the companion's drawing shares the CPU. Expect slowdowns; a lighter
  mode (fewer companion redraws, no extras) may be needed.

## Next steps

- Run it on a New 3DS and measure the frame time; move the screen copies to the
  GPU (citro2d/citro3d) if the software blits cost too much, and add scaling
  options for the top screen.
- Port more of the app: ITEMS, the region MAP, GUIDE, POKéDEX, the rest of
  `NativeConfig` (hacks, other languages, Ruby / Sapphire), ROM art (`RomArt`).
- Savestates (with the app's `pkStateMatchesSave` check before any resume),
  fast-forward, settings.

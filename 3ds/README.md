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
  - **PARTY**: the six slots (the game's own icon from your ROM, name with its
    ♂ / ♀, level, HP bar, status) in the app's FireRed party palette; tap one
    for its summary (moves, type, PP).
  - **BATTLE**: opens by itself in a battle and goes back after it. **INFO**:
    the foe and you (with the game's own party icons, read from your ROM),
    your moves with the app's verdicts (SUPER 2x / NEUTRAL 1x / RESISTED 1/2x /
    IMMUNE 0x / STATUS). **SUGGESTIONS**: your party's three best picks against
    the foe, each with its best move and verdict (OUT on the one already in
    battle) - the app's ranking. In a trainer battle the bar under both carries
    the **FOE TEAM**: the trainer's party in order, Poké Balls until sent out
    or announced, fainted ones faded; tap one to see it (NEXT / FAINTED /
    RESERVE / UNSEEN) and have INFO and SUGGESTIONS work against it, tap it
    again to follow the battle.
  - **BAG**: one pocket at a time in the game's own order and colours (FireRed's
    or Emerald's bag), item names and counts as the game prints them, a list a
    drag scrolls, the tapped item's description.
  - **MAP**: the game's own region map, rebuilt from your ROM (the app's
    `RomArt`: found by fingerprint, nothing bundled) - Kanto and the Sevii
    pages, or Hoenn - with the player's head where the game puts it, the place
    name in the game's own label, and FireRed's blinking cursor on a tapped
    tile. PLACES lists every town, route and place to jump to.
  - **GUIDE**: the app's hand-written pages (TIPS / WHERE IS / STUCK?, with the
    save's progress checked off), hint first, then the answer. HERE is the
    current area: its to-dos, wild POKéMON (from the ROM, with odds), gifts,
    trades and items (a ball = done). BOSS is the next unbeaten gym leader /
    Elite Four / champion with their team from the ROM. A first-open notice
    says the guide is AI-written and may be wrong, as in the app.
  - **POKéDEX**: seen / caught from the save, in Kanto / Hoenn or National
    order (the save's own, or the other with a tap); an entry has the game's
    picture, category, types, height / weight, text, base stats and ability.
  - **SETTINGS** (the gear): place, money, file and speed, then SCREEN and
    FAST-FORWARD (pick-lists), SAVE STATE / LOAD STATE / LEAVE GAME (confirmed).
  - **TRAINER CARD** (the button in SETTINGS' title, like the app's TOOLS): the
    game's own card, drawn from your ROM's art pixel for pixel - name, ID,
    money, POKéDEX, play time with its blinking colon, badges, stars, the
    trainer pic; a tap flips it to the back (Hall of Fame debut, link battles,
    trades, FireRed's stickers ...) the way the game does. FireRed rev 1,
    LeafGreen and Emerald, as in the app.
- Games: retail English **FireRed / LeafGreen (rev 0 and 1)** and **Emerald**.
  MAP, POKéDEX and GUIDE's wild / BOSS parts read ROM tables mapped for
  FireRed rev 1 and Emerald only so far: on FireRed rev 0 and LeafGreen MAP
  works, POKéDEX says it's not available yet, and GUIDE has its pages and
  HERE's people / items. The FOE TEAM's addresses are known for FireRed /
  LeafGreen rev 1 and Emerald (as in the app); the icons for all five. ROM hacks, other languages, Ruby / Sapphire, Game Boy
  and the QoL builds' telemetry struct aren't ported yet; they show "not
  supported".

## Controls

| | |
|---|---|
| A / B / START / SELECT / L / R / D-pad or circle pad | the GBA's buttons |
| Touch screen | the companion |
| X | next companion tab |
| Y | companion back (close a summary, an entry, a pick-list, a confirm) |
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

## Running it without a 3DS (Azahar)

[Azahar](https://azahar-emu.org) (Citra's successor) runs `.3dsx` homebrew on
a Mac, Windows or Linux.

1. Install Azahar (macOS: the `.dmg` from its releases page). In its settings,
   turn on **Enable New 3DS mode** (System) - the port targets the New 3DS.
2. Build the app: `cd 3ds && make 3ds` (Docker Desktop running) gives
   `ctr/pokedaisy.3dsx`.
3. **File > Open Azahar Folder**, then in `sdmc/` make `pokedaisy/roms/` and
   put your `.gba` files there (a `.sav` beside one with the same name is used).
   No ROM handy? `make decomps fixture-roms` makes test ROMs: the
   `build/fixture-*-rom.gba` ones open every tab (the trainer battle is
   `fixture-emerald_trainer-rom.gba`).
4. **File > Load File...** and pick `pokedaisy.3dsx`. The mouse is the stylus
   on the bottom screen.

Azahar's default keys (Emulation > Configure > Controls to change them): A =
`A`, B = `S`, X = `Z` (next companion tab), Y = `X` (companion back), START =
`M`, SELECT = `N`, L / R = `Q` / `W`, ZR = `2` (fast-forward), D-pad =
`T` `F` `G` `H`, circle pad = the arrow keys. Azahar plays sound without the
DSP firmware. It shows that things work, not how fast they'll run on a console.

## Building and testing

Everything runs in Docker, so it works the same on a Mac or a cloud session.

```sh
cd 3ds
make decomps       # pret's FireRed rev 1 / Emerald, = retail (below; optional)
make preview       # the companion from the RAM fixtures -> build/shots/*.png
make 3ds           # ctr/pokedaisy.3dsx (devkitpro/devkitarm image)
make fixture-roms  # build/fixture-*.gba test ROMs (below)
make azahar-test   # the .3dsx in the Azahar 3DS emulator -> build/azahar/*.png
make azahar-battle-test  # BATTLE in Azahar, a trainer battle -> build/azahar-battle/
make tables        # regenerate core/*_gen.c and ctr/icon.png from the app
```

- **`make preview`** is this port's ui-preview: `desktop/pd_preview.c` reads a
  fixture's `ewram.bin` / `iwram.bin` (`app/src/test/resources/fixtures/`) and
  drives the companion with taps found by hit id - a pressed slot, a summary,
  BATTLE, BAG (an item, a drag, the next pocket), SETTINGS, a pick-list, a
  confirm, MAP (a tapped tile, the blink, PLACES), POKéDEX (scrolled, an
  entry, the next, the other dex), GUIDE (the notice, every page, a hint and
  its answer), BATTLE's INFO and SUGGESTIONS, the TRAINER CARD (blinked, mid-flip,
  the back) - drawing each as the 3DS's two screens. It also renders each card
  whole and prints the CRC32 `TrainerCardTest` pins: FireRed's `d62339db` /
  `045996a2` and Emerald's `f3e14960` / `74484c21`, the cards that matched the
  games' own screens pixel for pixel, come out the same here. There's no retail trainer-battle fixture, so the preview makes one
  from the wild battle (`emerald_de_battle`): three of the player's Pokémon
  copied into gEnemyParty beside the wild one, the trainer flag set; then the
  first faints and the trainer's next pick is set, and the last slot is
  tapped (`-trainer*` shots). That RAM is kept as
  `build/fixtures/emerald_trainer` for `make fixture-roms`. No emulator: look at
  every UI change here. `PD_PREVIEW_HITS=1` also prints each shot's tap
  targets, for writing the Azahar steps.
- **`make decomps`** (`test/build_decomps.sh`; git, a C compiler, libpng and
  devkitARM) builds pret's pokefirered (rev 1) and pokeemerald at pinned
  commits into `build/decomps/` and checks they match the retail SHA1s. The
  tabs that read the ROM need one; the preview passes them in when they're
  there (or `make preview FR_ROM=... EM_ROM=...`). Never commit them.
- **Fixture ROMs** (`test/fixture_rom.c`) are a few lines of GBA homebrew that
  copy a fixture's RAM into the GBA and idle, with FireRed's or Emerald's game
  code in the header. The companion can't tell them from the real game, so the
  whole app can be tested without a Pokémon ROM. They hold no game code or
  data, only the fixture's save state. With the decomps built there's also
  `build/fixture-*-rom.gba`: the same written over the decomp's ROM (its
  first ~300 KB, game code the loader never runs), so MAP / POKéDEX / GUIDE
  find the game's tables and art - derived from a game ROM, so build/ only.
- **`make azahar-test`** boots the real `.3dsx` in Azahar (the
  `linuxserver/azahar` image, on Xvfb) on the FireRed fixture ROM and taps
  through it like a player: game list, PARTY, BAG, SETTINGS, each SCREEN mode,
  fast-forward, a save state and loading it, MAP, PLACES, POKéDEX, GUIDE (on
  the `-rom` fixture when it's built) - screenshots in `build/azahar/`,
  and the emulated SD card (states, backups, settings) in `build/azahar/sd/`.
  Azahar shows that it works, not how fast: speed has to be measured on a
  console. **`make azahar-battle-test`** does BATTLE the same way on the
  trainer battle: INFO with the FOE TEAM, SUGGESTIONS, foes tapped.

## Layout

| Path | What |
|---|---|
| `core/` | Portable C, no platform code: game detection (`pd_game`, the app's `NativeConfig` addresses), the snapshot reader (`pd_snapshot`: party, battle, bag, money, place - ports `Gen3Mon.kt` / `readNativeTelemetry` / `readNativeBag`), the canvas (`pd_canvas`: pixel-stepped corners, layered OPTION frames, GBA-shadowed and wrapped text, drawn cursors), the companion (`pd_ui.c` frame + `pd_ui_party` / `_battle` (INFO, SUGGESTIONS, FOE TEAM) / `_bag` / `_map` / `_guide` / `_dex` / `_settings` / `_widgets`), the ROM readers (`pd_romart`: `RomArt`'s scan - every blob of the game in one pass; `pd_map`: the region map and heads, `RegionMapModel`'s pick, `PlayerMapTile`; `pd_dex`: the dex tables and front pics; `pd_guide`: `GuideRom.kt`'s bosses, teams and wild tables), the party icons (`pd_icon`: `DecompIconSource`'s tables), the TRAINER CARD (`pd_card`: `readTrainerCard` + `TrainerCardArt`; `pd_ui_card`), the game list (`pd_menu`) and the settings file (`pd_settings`) |
| `core/*_gen.c` | Generated: names / moves / type chart / map sections / item text / gender ratios from the app's Kotlin tables (`tools/gen_tables.py`), the ROM art's fingerprints (map and card) and the cursor grids (`tools/gen_map_tables.py`), the guides and area data (`tools/gen_guide.py`), Pixel Operator as a 1-bit font plus drawn ♂ ♀ (`tools/gen_font.py`) |
| `ctr/` | The 3DS host: `main.c` (game loop, ndsp audio, HID, files, states) and `gpu.c` (citro2d: the game and canvases written into textures in the GPU's tiled order, drawn as quads), and the CMake build against the mGBA submodule |
| `desktop/` | The preview tool and its small PNG writer |
| `test/` | The fixture ROM, the decomp build and the Azahar script |
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
- Port more of the app: the rest of `NativeConfig` (hacks, other languages,
  Ruby / Sapphire, ROM tables for FireRed rev 0 / LeafGreen - the app has
  LeafGreen's), the battle's touch controls (`BattleInputController`), the
  party icons' two-frame animation.
- More state slots with thumbnails, and resuming where the player left off (with
  the app's `pkStateMatchesSave` check first).

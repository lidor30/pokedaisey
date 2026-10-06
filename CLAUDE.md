# PokeDaisey — Project Notes for Claude

Dual-screen Android app (AYN Thor, Retroid Pocket Duo): an embedded libmgba core on the
top screen and a live companion for Gen 3 Pokémon games and ROM hacks on the bottom
screen. Read `README.md` for the user-facing feature list and `docs/DEVELOPMENT.md` for the
per-ROM support details, how each game was mapped, frontend setup and releasing; this file is
process context that isn't obvious from the code.

**This repo is public.** Never commit ROMs, saves or secrets. The SteamGridDB API key is
entered by the user at runtime and must stay that way. FireRed / Emerald art is rebuilt
from the player's own ROM (`RomArt`), not bundled. Keep it that way.

## Relationship to the FireRed QoL repo

PokeDaisey was split out of the user's **private** FireRed QoL ROM-hack repo (local
checkout `~/Projects/tests/my-rom-hacks`, where it lived as `tools/pokedaisey`
under the name pokedaisey) on 2026-10-06, without git history. That repo still owns:

- **The `gQolTelemetry` struct** the FireRed / Emerald QoL builds export
  (`include/qol_telemetry.h` via its `patches-firered/0009-*` / `patches-emerald/0001-*`,
  documented in its `docs/telemetry.md`). `companion/data/Telemetry.kt` decodes it byte
  for byte, so a struct change there needs the matching change here.
- **The Go `tools/telemetry-viewer`**. Several Kotlin files here are ports of its readers
  (`native.go`, `gen3mon.go`, `unbound.go`, ...) and say "keep in sync".
- **The pinned, built decomps** (`build/pokefirered`, `build/pokeemerald`, `build/pokeruby`,
  `build/pokehns`, ...) that the generator scripts and ui-preview read via `$DECOMPS`.

Public readers can't see that repo, so user-facing docs call it "the FireRed QoL project"
and don't link to it.

## Local setup on this machine

- `local.mk` (untracked, see `local.mk.example`): `DECOMPS` →
  `~/Projects/tests/my-rom-hacks/build`, `MON_ICONS` → that repo's
  `tools/telemetry-viewer/assets/pokemon`. `make` exports both; outside make, export them
  yourself for `python3 scripts/gen_*.py` or `gradle render`.
- `local.properties` (untracked): Android `sdk.dir`.
- `third_party/mgba`: git submodule at mGBA `0.10.5` (`make submodules`).
- ROMs/saves for headless captures: `scripts/host_roms.conf`. On-device paths:
  `scripts/roms.conf` (`/sdcard/Android/data/com.pokedaisey.app/files/...`).
- The package / applicationId was renamed from `com.pokedaisey.PokeDaiseyApp` to
  `com.pokedaisey.app`, so Android treats it as a new app. An old install's data (`roms/`,
  `saves/`, states, prefs) sits under the old id's `Android/data/` folder and doesn't
  carry over by itself.

## Headless captures

`native-capture/mgba_dump` (see its README) runs in the `pokedaisey-capture` Docker image
(`native-capture/Dockerfile`; the capture scripts build it on first use, or run
`make capture-image`). The image's `ENTRYPOINT` is `/bin/bash`, so always run
`docker run <image> -c "<full command>"`. Bare args make bash treat the command as a
script and fail with a misleading `cannot execute binary file`. Never reuse a `.ss`
savestate against a rebuilt ROM (stored code pointers go stale). Boot from a `.srm`
instead.

## UI work

**Look at every UI change before calling it done** — render it and read the PNG, don't
reason about Compose layout blind. Two ways, same screens:

1. **Paparazzi** (needs the Android SDK + Google Maven, i.e. the user's Mac):
   `./gradlew :app:recordPaparazziDebug --tests '*ScreenshotTest'`,
   then Read the PNGs in `app/src/test/snapshots/images/` (gitignored — a viewing tool,
   not a regression gate). Tests live in
   `app/src/test/kotlin/.../companion/ui/CompanionScreenshotTest.kt`: one per companion
   tab (via `CompanionScreen(initialTab = …)` — Paparazzi can't tap the tab bar) plus the
   shared `OptionSelector`/`OptionConfirm`, at the Thor bottom screen's 1240x1080 (landscape). Add a
   test when adding a screen. Paparazzi is pinned to **1.3.4** — the last release on
   Kotlin 1.9.24; newer ones need a Kotlin 2 bump first. Library/Settings (top screen)
   are private composables inside their Activities, so Paparazzi can't reach them; use
   the preview harness below for those.
2. **`ui-preview/`** (Maven Central only — works in cloud sessions
   where `dl.google.com` is blocked, which also blocks AGP, so the real app can't even
   compile there). A Compose **Desktop** project that compiles the real app sources
   (`companion/**`, `LibraryActivity`, `SettingsActivity`, …) against small hand-written
   Android shims and renders PNGs of every companion tab _and_ the top-screen
   Library/Settings, including tapped states (selectors, confirms, sub-pages):
   `cd ui-preview && gradle render` → `build/shots/*.png`
   (`-Ponly=settings` filters by name, `-Pgame=EMERALD` switches game). It's also the
   only compile check available in such sessions, so run it after any UI edit there. See
   its README for the shims and quirks (CMP 1.5 / Kotlin 1.9.22, `Icons.AutoMirrored`
   rewritten to `Icons.Filled`, a stale incremental cache once faked "Unresolved
   reference" errors — delete `build/kotlin` if errors look impossible).

**The app's one look is FireRed's OPTION screen** (`companion/ui/GbaMenu.kt`): white
title window, grey multi-line list window, `LABEL  VALUE` rows with grey labels / red
values and a white row as the cursor, white framed buttons, black-out overlays. Text is
`GbaText` sized by `GbaTextMetrics` (whole screen pixels per font pixel, so the pixel
font stays crisp; `rememberGbaTextMetrics(1f)` for denser secondary text). Companion
SETTINGS/STATES and the top-screen Library/Settings are all built from these pieces over
a game backdrop (`GameBackdrop` on the companion, `AppBackdrop` — FireRed's party-menu
stripes — on the top screen); ITEMS (`ItemsScreen.kt`) is the per-game bag screen in the
same idiom. PARTY uses the game's own slot (`GbaPartySlot`, generated `PartySlotStyle`s) where one exists; every other game gets a FireRed-like slot drawn from a `PartyPalette` (`PartyScreen.kt`, `partyPaletteFor`) — restyle a game by adding a palette there, like ITEMS' `BagPalette`. Rogue, Gaia, Lazarus, Seaglass and R.O.W.E. have palettes sampled from their party menus (headless: the save's first mon copied into slots 2-3, slot 3 at 0 HP, so normal / selected / fainted all show; `PartySlotColors.band` / per-state `text`, `PartyPalette.empty` cover flat and white-box slots); their bags have `BagPalette`s (TMT2's is `EmeraldBag`; Celia and Gaia match FireRed's). Their backdrop is FireRed's party backdrop recoloured (`backdropColors` in `theme/Theme.kt`: its 3 colours mapped to the game's), or plain stripes until a FireRed ROM has supplied the art. Multi-choice settings open an `OptionSelector` pick-list — never make a row
cycle through more than two values. New screens should reuse these rather than
`GbaWindow`/`GbaButton` (the older cream-window look). Full-tab detail views (the Pokémon
summary, battle INFO / SUGGESTIONS) use `SummaryFrame` (`MonDetailScreen.kt`): one white
window split by `Separator`s over a row of square `PlatinumButton`s with Back bottom-right.

**Corners are pixel art, never smooth arcs**: use `PixelRoundedShape(r)` / `PixelPillShape`
(`companion/ui/PixelShapes.kt`) instead of `RoundedCornerShape`/`CircleShape`, and
`drawPixelRoundRect`/`drawPixelRoundFrame` instead of `drawRoundRect` — they step in
whole GBA pixels (`gbaPixelPx()`, the same unit as `GbaTextMetrics.px`). Icons are
pixel bitmaps too (`PixelIcons` + `PixelIcon`/`PixelArt`), not vector glyphs; Pixel
Operator has no ◀/▶, so draw cursors rather than typing them (the fallback font's
glyph is taller than a line). Names follow the game's own casing: `speciesName`/
`lookupMove`/`itemName` pass through `gameCase()`, which capitalises for FireRed/Emerald
(the shared tables are title-cased; ROM-extracted hack tables already carry their casing)
— don't `.uppercase()` names in the UI. The per-game look reads the `activeGame` global,
which Compose can't observe, so `CompanionScreen` is keyed on `SnapshotView.game`.

**Tab bar + GUIDE**: at most 5 tabs sit next to the SETTINGS gear (`MAX_BAR_TABS`); the
player picks them in SETTINGS > TAB BAR (`Prefs.companionTabs`, shared by every game — only
tabs the running game has count). The rest open from OPEN rows at the top of SETTINGS; in a
battle BATTLE takes the last chosen tab's place, and when it ends the tab that was open before
it comes back. Tabs go by id (`"ITEMS"`); only the chip / OPEN row shows the game's own word
(`companionTabLabel`: BAG, Unbound's CUBE, R.O.W.E.'s INVENTORY - read off each ROM's START menu
in `native-capture/menu-shots/*/start.png`). The GUIDE tab (`GuideScreen.kt`) shows
hand-written pages per game (`companion/data/Guide*.kt`: TIPS / WHERE IS / STUCK?, with
`Have` checks marking what the save already has) plus pages read live from ROM + save:
HERE (the current map's wild encounters), NEXT BOSS (`GameGuide.bosses`, first one whose
badge/`FLAG_DEFEATED_*` flag is unset; teams from `gTrainers`, default moves from the
learnsets) and EVOLUTIONS (`Evolutions.kt`). ROM/save addresses live in `GuideTables`
(`GuideRom.kt`) on the native path only — the QoL struct path gets the hand-written pages
alone. The battle INFO pane (`BattleInfoScreen.kt`) is ordered by what a player asks
mid-battle, split 50/50 YOU/FOE: battler cards on top, your moves with a plain-words verdict
(SUPER 2x / RESISTED ½x / NEUTRAL 1x / IMMUNE 0x) bottom-left, the foe's WEAK TO / RESISTS
bottom-right — sized to fit a single battle without scrolling. The FOE TEAM
(`gEnemyParty`) sits in its bottom bar like the summary's party strip; unsent foes are
Poké Balls until tapped (a tapped foe gets a NEXT / FAINTED / RESERVE / UNSEEN pill by its name). SUGGESTIONS (`SuggestionsScreen.kt`) is built from INFO's pieces
(`BattlerCard`, `BattleMoveRow`, `FoeTeamStrip`): a VS line for the foe, then the top three
picks, each split 50/50 like INFO — the Pokémon's card (1ST/2ND/3RD, OUT on the one already
in battle), then its best move with the verdict. Change the shared pieces, and both panes follow. Everything is hint-first (title, tap = hint, tap = answer) and only one entry
is open at a time. The first GUIDE open per game shows an "AI-written, may be wrong" notice.
Guide facts come from the game's own data (the decomp's map scripts for FireRed/Emerald),
written in our own words — never copied from community docs; mark a guide
`verified = false` until it's been checked against the game. Emerald's guide (`GuideEmerald.kt`,
`verified = false`) and its live tables (`GUIDE_TABLES_EMERALD`, plus the FOE TEAM addresses on
`NATIVE_EMERALD_RETAIL`) came from the pinned pokeemerald built unpatched with agbcc — sha1
`f3ae0881…`, byte-identical to retail — and its ELF; struct offsets via `offsetof` compiled with
agbcc. That built ROM stands in for retail in tests/previews: `gradle render -Pgame=EMERALD
-Prom=<pokeemerald.gba>` feeds HERE / NEXT BOSS from it plus the `emerald_vanilla` save fixture.
`guideTablesMatchRom` checks each game's probe trainer (BROCK 414 / ROXANNE 265).
**Which guide a game gets is a `GuideId` on its `GuideTables`** (FIRERED / LEAFGREEN / EMERALD /
RUBY / SAPPHIRE / HEART_AND_SOUL / UNBOUND / RADICAL_RED / ODYSSEY / GAIA / AMETHYST / CELIA), not its `GameKind`: LeafGreen runs as FIRERED and Ruby/Sapphire as EMERALD, but
their guides differ; with no live tables (the QoL builds) it falls back to the kind. Ruby/Sapphire
(`GuideRubySapphire.kt`, one text built per version: MAGMA vs AQUA, GROUDON vs KYOGRE, LATIOS vs
LATIAS; `verified = false`) come from pret/pokeruby cloned into `$DECOMPS/pokeruby` (its
`ruby_rev1`/`sapphire_rev1` builds byte-match the user's ROMs; build with `make CPP=cpp` after
`/opt/agbcc/install.sh`). LeafGreen shares FireRed's text built with `leafGreen = true` (Game
Corner prizes) and its own area data (trades). Version branches in decomp sources (`.ifdef
LEAFGREEN`, `#if defined(FIRERED)`) are resolved by `gen_guide_areas.py`'s `preprocess()`.
**Hack guides** (Heart and Soul, Unbound, Radical Red, Odyssey, Gaia, Amethyst, Celia: `Guide<Hack>.kt`,
`verified = false`) are NEXT BOSS + a few TIPS; WHERE IS is generated from area data
(`generatedWhereIs`: HMs, KEY items, gift mons). Heart and Soul's area data comes from its source
(`$DECOMPS/pokehns`, `gen_guide_areas.py hns`); the closed hacks' from `scripts/gen_guide_areas_rom.py`
reading the ROM's maps + script bytecode (see docs/DEVELOPMENT.md's GUIDE section). `Boss.variants` lists
several teams (Unbound's per difficulty) and `Boss.doneIf` overrides the flag check (HnS's League
var); `Boss.variantsFor` lets the teams follow the save (Amethyst's badge-scaled gyms). Expansion
trainers need `TrainerMonLayout` on the `GuideTables`; `altTrainers` reads a hack's extra trainer
tables. No guide yet for the expansion hacks without source (Lazarus, Seaglass, TMT2): the ROM
reader only knows the FireRed engine's maps and script commands.
**HERE covers the whole area, for every game with a guide (the user's standing rule)**: not just
the wild Pokémon but TO DO (hand-written entries tagged `areas = listOf("ROUTE 104", …)`, matched
to the current map section's name via `areaKey()`), PEOPLE (gifts, gift Pokémon/eggs, in-game
trades) and ITEMS (item balls, hidden items). PEOPLE/ITEMS are generated per region map section
(so a city's buildings count with it) by `scripts/gen_guide_areas.py` from the
pinned decomp's map.json events + scripts.inc → `GuideAreas{FireRed,LeafGreen,Emerald,RubySapphire}Gen.kt`, each with the
flag that marks it done (a ball in the UI; needs save flags, i.e. the native path — the QoL struct
path shows the lists unmarked). When adding a guide for another game, generate its area data too
and tag its hand-written entries with areas. HERE shows whenever a game has area data, even
without the ROM tables (then no wild list).

**ROM art - nothing of FireRed / Emerald is bundled**: their party-menu art (`partyfr/`,
`partyem/`: slot frames, Poke Ball, status icons, small font), party backdrops
(`partybg/firered.png`, `emerald.png`) and region maps (`regionmap/*.png`) are rebuilt from the
player's ROM by `RomArt` (`companion/data/RomArt.kt`): one background scan per ROM on its first
launch (~0.9 s for 32 MB on the Thor), written to `filesDir/rom-art/` under those same paths and
shared by every game (CFRU hacks use FireRed's font from there; hacks without a backdrop of their
own use FireRed's, else Emerald's). Blobs are found by fingerprint, not address -
`RomArtSigsGen.kt` from `scripts/gen_rom_art_sigs.py`, hashes/CRCs only - so retail, the QoL
builds, LeafGreen and hacks that kept the art all match. Load art through `GameArt.get`
(`AssetImages.kt`; rom-art, then bundled assets) keyed on `rememberArtGeneration()`, so it shows up
once a scan lands; with no art the party tab falls back to the `PartyPalette` slot and the map to
text. `gen_party_assets.py` still generates `PartySlotStylesGen.kt` but writes PNGs only for
bundled games (Heart and Soul). `RomArtTest` pins the pixels; ui-preview rebuilds the art from
the decomp builds (or `-PartRoms`), Paparazzi from the retail ROMs.
**TRAINER CARD (the CARD tab, off the bar by default, so under SETTINGS > TOOLS)**: retail FireRed /
LeafGreen / Emerald and both QoL builds only (`NativeConfig.trainerCard`; the hacks copy the retail
configs, so it is set on the retail ones alone, and on `findStructMoney`'s candidates for the QoL
path). `TrainerCard.kt` reads what `SetPlayerCardData` gathers (name, ID, play time, money, caught
count regional until the National Dex, badge flags, stars, game stats XOR the encryption key, FireRed's
Sticker Man vars); `TrainerCardArt.kt` draws the game's own card from ROM blobs `RomArt` caches raw
(`rom-art/trainercard/*.bin`: card tiles, tilemaps, a palette per star count, badges, trainer pics,
FONT*NORMAL + widths) with trainer_card.c's layers and coordinates. It matched headless screenshots of
the real card for both saves, front and back, pixel for pixel (`TrainerCardTest` pins those CRCs).
Font gotcha: FireRed's FONT_NORMAL letterSpacing is 1, but RenderText only adds it to Japanese text.
**Unbound gets the same card, not a copy of its own** (the user's call: hacks borrow the look, matched
in colour): `CardStyle.UNBOUND` = FireRed's card from its ROM (Unbound kept those blobs) through
`unboundPurple` (hue 266, sampled from Unbound's card headless), with Unbound's protagonists, which
replace Red / Leaf at pic ids 135/136 in its own front-pic tables (`gen_rom_art_sigs.py` `UB*\*`). Its
ROM changed FireRed's badge strip and dropped the 0-star palette, so no badges are drawn and every star
count starts from the 1-star palette. Save data: FireRed's layout + CFRU dex flags (`TRAINER*CARD_UNBOUND`on`NATIVE_UNBOUND_WITH_DEX`only). Other hacks: no card yet.
The tab shows the card cropped to itself (no BG2 backdrop) at the largest whole scale (5x on the
Thor); a tap flips it (squash, like the game), and the time colon blinks via an infinite transition
(a`delay`loop never lets Compose tests go idle, which hung ui-preview once).
**FireRed-engine hacks' own region maps** (Unbound, Odyssey, Gaia, Amethyst, Radical Red) come
from`RomRegionMap` (`companion/data/RomRegionMap.kt`), not fingerprints: those hacks keep
FireRed 1.0's `region_map.c`code and only repoint its data, so it follows that code's literal
pools (tiles, palette, 4 tilemaps, names, corner/size tables, the Sevii list + the`cmp r0, #SEVII_MAPSEC_START-1`hacks move) on every launch, caches the screens (backdrop border
cropped) as`rom-art/regionmap/rom-<crc>-v<N>-<i>.png`, and `lookupLocation`prefers it for every
game but FireRed. Needs a BPRE rev 0 ROM whose pointers land on valid data, else nothing
(Celia's hack, LeafGreen).`RomRegionMapTest`pins it; ui-preview:`-Pgame=UNBOUND -Prom=<rom>`.
The Map tab (`MapScreen.kt`) draws the region map like the game: the place name in the game's
own label (`MapLabelStyle`, sampled headless: FireRed family = a darkening strip top-left, white
text, plus a second strip for a dungeon on that tile; Emerald family = the framed window
bottom-right), the player's head (`regionmap/player*{red,leaf,brendan,may}.png`, from the ROM by
`RomArt`, picked by `SaveBlock2.playerGender`) on the tile the game puts it (`PlayerMapTile`=
region_map.c's GetPlayerPositionOnRegionMap, from`gMapHeader`'s layout size + mapType; indoors
it keeps the last outdoor tile), and FireRed's map cursor (four white 2px corners that swap
between two sizes every 20 frames, from `graphics/region_map/cursor.png`), for every game.
Tapping a tile names it via the game's own cursor grid (`RegionMapModel.pick`; FireRed/Emerald
grids are `RegionMapLayoutsGen.kt`from`scripts/gen_region_map_layouts.py`, FireRed-engine
hacks' come from the ROM by `RomRegionMap`, other games fall back to the smallest section rect);
PLACES lists every town / route / place to jump to (the "where is X?" answer), the region
button flips Kanto / Sevii maps, ME returns to the player. `MapSecData.kt`'s dungeons and other
"not on the map" sections are 0,0,0,0 (they used to sit at a fake (4,4)); the grid's dungeon
layer places them (`RegionMapModel.tilesOf`). ui-preview: `-Ponly=map` renders the tapped /
PLACES / region states too.
**Battle POKéMON pane + automated input** (`BattleControlsScreen.PartyPicker`, `BattleInputController`):
on FireRed rev 1 / Emerald and their QoL builds (`switchAddrsFor`in`PokeDaiseyActivity`; gPartyMenu
FR `0x0203B0A0`/ EM`0x0203CEC8`, slotId at +9, same in retail and QoL), POKéMON opens the PARTY tab's own slots
(`PartyGrid`, shared with `PartyScreen`; taps silent since the game sounds them) - the game's cursor and a BEST tag on
`recommendedSwitch`= SUGGESTIONS' top pick with BATTLE HINTS on, else the cursor on the battler (tagged OUT), and a
"Choose a POKéMON." window with CANCEL below, full height (no ball strips or INFO / SUGGESTIONS over it); it also replaces the game's party screen whenever that's open (after a faint). A tap runs`switchTo(personality)`:
POKéMON on the action menu, then a closed loop on the game's memory - `OpenPartyMenuInBattle` really reorders
gPlayerParty to battle order while the menu is open, so the mon is found there by personality and DOWN is pressed
until slotId matches (Emerald drops presses for ~35 frames after the menu opens, so it re-reads after each), A,
A on SHIFT. While a switch runs the top screen keeps its last frame (`BattleInputController.holdFrame`->`EmulatorView.holdFrame`skips the texture upload), so the game's fade to black and its party menu never show;
let go 12 frames after the switch ends (WaitForMonSelection only returns once the battle has faded back in).
Verified headless on both games (scripted wild battle via CreateScriptedWildMon +
StartScriptedWildBattle). RUN presses B to close "Got away safely!" (every 30 frames until the battle ends or the
action menu returns). All synthetic presses go through`GbaInput.setScript(bits, exclusive)`: the player's own
keys are ignored while a sequence runs (they used to share `touchBits`, which also wiped the touch pad's presses) -
only while presses play or a closed loop steers (`exclusiveLocked`), never during the passive target-confirm /
pending-move watches: those once held the lock through the whole turn, so a level-up or "learn a move?" message
waiting on A left the player stuck. Watches expire after 300 frames; a 1200-frame watchdog cancels any lockout.
`BattleInputControllerTest`simulates the party menu.
**FF MODE** (SETTINGS,`FfMode`: SMART default / NORMAL). NORMAL = fast everywhere. SMART = 1x while a
menu screen or the region map is up, FF (still on) resuming when it closes. **A battle stays fast** (the user's
rule): in battle the game's own battle input state decides (`smartSlows`, `SmartFf.kt`, fed by
`BattleInputController.gameState`) - only PARTY_OPEN / BAG_OPEN slow down, never the battle itself, whatever the
menu watch below thinks (it once slowed whole battles); it and the map watch only decide outside battles, or
in a game that doesn't report a battle state. Menus are found by `FfMenuWatch`with no per-game table: the field and battles each run one fixed`gMain.callback2`, every standalone screen
(party, bag, summary, PC, Pokédex..., in battle too) its own - the START menu and dialogue are overlays that keep
the field's (checked headless on FireRed QoL, Emerald, Rogue). gMain is found by scanning IWRAM twice 16 frames
apart (vblankCounter1 at +0x20 moving exactly that much; FireRed's is a pointer, so counter2 at +0x24 then); the
field's callback2 is learned from the player moving (snapshot positions), the battle's from frames in battle,
both persisted per ROM CRC (`Prefs.ffMenuCallbacks`). Ruby/Sapphire's inBattle byte is +0x43D.
**SETTINGS layout**: the options come in titled groups (FAST-FORWARD / CONTROLS / COMPANION / SCREEN) in ONE
scrolling column at the normal text size (a two-column, denser try was too small to tap - the user's call),
ending in CLOSE GAME / RESTART GAME. TOOLS (every tab not in the tab bar) sit under the list as `TabChip`s in
the bar's own columns (`barChips`wide, the gear's gap at the end), a second row of tabs only SETTINGS has.
**FF steps aside on the game's region map** (SMART only):`RegionMapWatch`finds the map screens' EWRAM
pointers per ROM (FireRed family:`sRegionMap` `0x020399D4`, found beside its `0x4796`struct
offset in the literal pools - Town Map, Fly map, wall maps; retail/QoL Emerald:`sFieldRegionMapHandler`+`sFlyMap`+ the PokéNav's HOENN MAP - Emerald has no Town Map item -
via`gPokenavResources` `0x0203CF40`->`substructPtrs[POKENAV_SUBSTRUCT_REGION_MAP_STATE]`(+0x1C, set only while that page is up; verified headless); Emerald hacks and Ruby/Sapphire:
none yet - R/S's PokéNav map has no clean "open" flag), and`EmulatorEngine` caps the
speed at 1x while one is non-null - FF stays on and resumes on close. Verified headless
(`call ShowTownMap`: 0 on the field, set while open, 0 after B).

**FF MUSIC** (SETTINGS: STEADY [ALPHA] / SPED-UP / OFF, `FfMusicMode`, in that order - OFF last).
SPED-UP plays the core's own audio box-filtered down to real time, written non-blocking so FF is
never paced by the audio device. STEADY loops a clean clip of the song playing (`FfMusicPlayer`,
`FfMusicCache` per ROM CRC), keyed by the song itself: `FfMusicKey` reads the BGM player's
`songHeader` from the m4a engine every frame (`0x03007FF0` -> SoundInfo -> player chain, BGM is
the tail) - no per-game song table (a hand-picked FireRed "battle song" was MUS_RS_VS_TRAINER, i.e.
Emerald's theme). `M4aSongs` matches agbcc's `m4aSongNumStart` and, masked, Emerald Rogue's gcc build of it; the render core boots 300 frames, then waits (up to 30 s) for the game's own music, since Rogue keeps the sound engine paused through ~15 s of splash screens and a forced song never starts there. Clips are **never captured from the live game** (that mix carried menu clicks
and battle sounds): `FfMusicRenderer` renders each song alone on the second core (`rg`) as the
game starts it, calling the ROM's `m4aSongNumStart` - found by code signature (`M4aSongs`; every
agbcc-built game and binary hack, not pokeemerald-expansion) - via `pk_call`, which runs the
call to its return and restores the CPU (a bare PC hijack corrupted Unbound mid-frame), after
parking the booted game on a `b .` so its intro can't switch songs (only once the VBlank IRQ is
fully on - Unbound froze otherwise). Until a clip exists (or on an unsupported ROM) STEADY falls
back to SPED-UP, so every ROM pre-renders its music once in the background: FireRed/LeafGreen/Emerald-sized
ROMs (retail + QoL) their `MapMusic*.kt` list, any other ROM every `gSongTable` entry on the BGM player
(`M4aSongs.bgmSongIds`, `ms` 0); a song being heard pre-empts that pass mid-recording, songs that end by
themselves (fanfares) count as done. **Clips loop where the song loops** (`M4aLoop.kt`, cache v6): the BGM's
first track jumping back through its GOTO (0xB2) marks the loop end, the frame its main stream passed the
GOTO target the start (no intro: two GOTOs apart); `M4aLoopSplice` cuts one period starting 1 s into the
loop, tuned to the sample. The old audio-envelope `LoopDetector` cut battle themes to ~3 s and looped back
to the intro. **The clip follows the FF the player asked for** (`EmulatorEngine.requestedSpeed`), not SMART's
temporary 1x: it plays straight through a menu (game audio muted meanwhile, menu SFX included) instead of
handing over to the game's music and restarting from 0:00 when FF resumed; and `FfMusicPlayer` resumes a clip
that comes back within 15 s where it would be by now. Debug the
render core headless with `native-capture/mgba_dump`'s `call` / `park` / `bgm` / `wav`. The
SETTINGS title also shows the device battery (`BatteryIndicator`, fed by the activity through
`DeviceBattery`).

**STATUS BAR** (SETTINGS on either screen, `Prefs.statusBar`, off by default, live-toggled): a
white title-window strip on top of the game, exactly as wide as it (`GameStatusBar`, laid out by
`GameStageLayout`, which shrinks the game to 3:2 under it and moves the HUD down) - the ROM's
Library name, map section, money, clock (system 12/24h) and battery. Money is
SaveBlock1.money ^ SaveBlock2.encryptionKey via `NativeConfig.moneyOff` (FireRed family `0x290`,
Emerald family / R/S `0x490`, Heart and Soul `0x494`, Emerald Rogue `0x4A8` (its party mons are 104
bytes), -1 = unknown); values over 999,999 are dropped. The QoL struct has no money: the
Poller's `findStructMoney` tries the FireRed QoL build's own save pointers (`0x03005018`, not
retail's) then retail's, trusting one only once its SaveBlock1.location matches the struct's map.
`MoneyTest` pins every fixture.

**Button clicks, no haptics**: companion buttons play the game's own menu click (SE_SELECT = song 5
in every Gen 3 song table), rendered once per ROM by `FfMusicRenderer` (after MUS_DUMMY silences the
title music) into `filesDir/sfx/<crc>-select.wav` and played by `GameClickSound` (SoundPool); a ROM
the renderer can't drive borrows the newest other game's click. In Compose, use
`Modifier.soundClickable` (`companion/ui/ClickSound.kt`, `LocalClickSound`, provided by
`CompanionScreen(clickSound = …)`) instead of `clickable` - except buttons that press the game's
own buttons (battle FIGHT / BAG / moves / BACK: `PlatinumButton(pressesGame = true)`), which the
game already sounds, and tap-swallowing scrims. The top-screen Library / Settings stay silent.

**Device BACK**: a BACK tap (from either screen - the Presentation forwards every key to
`PokeDaiseyActivity`) is the companion's back; a hold still leaves the game. Anything that
opens over / inside a tab registers `CompanionBackHandler` (`companion/ui/CompanionBack.kt`,
newest wins, like androidx's `BackHandler`, which the Presentation / Paparazzi / ui-preview
can't host). `OptionOverlay`, `SummaryFrame` and `OptionTitleWindow(onBack)` already do, so
selectors, confirms and summary panes get it for free; a new sub-page or overlay built another
way needs its own. ui-preview's `*-back-*` shots press it (`-Ponly=back`).

**Emerald Rogue v2.2.1-EX** (`NATIVE_EMERALD_ROGUE`): source is public (Pokabbie/pokeemerald-rogue,
`expansion` branch = v2.2.1, cloned into `$DECOMPS/pokerogue`) for struct layouts; addresses come from headless
captures of the user's save (pulled from the Thor's SD card: `/storage/XXXX-XXXX/RetroArch/saves/mGBA/`) and
the ROM. 104-byte party mons (`NativeConfig.monStride`); one sorted 450-slot bag behind 9 `gBagPockets` views;
SaveBlock2 key `+0x4C`; the hub (mapsec 0) is named by `SaveBlock2.pokemonHubName` (`hubNameOff` ->
`Telemetry.mapSecName`); icons from `gSpeciesInfo` / `gItemIconTable` plus `gRogueItems` for its own items
(`IconTables.extraItemIconTable`); item names/descriptions and Rogue's mapsec table + grid on Emerald's Hoenn
map from `scripts/gen_rogue_tables.py`, which also writes Rogue's moves (Mainline table - its Revised mode's
isn't read), species types and Gen 6+ type chart (vanilla type ids + Fairy 18). Battle globals came from live
`dumpFixture` dumps mid-battle on the Thor (`emerald_rogue_battle` fixture): battle_main.c's EWRAM_DATA keep
their declaration order, and `BattlePokemon` (0x60) / `BattleStruct.monToSwitchIntoId` (0x3C) offsets were
confirmed by compiling Rogue's headers with host `clang --target=arm-none-eabi` (stub `string.h`, empty
`generated/*`). Touch battle input stays off.

**Frontend launch (Cocoon / iiSU / ES-DE)**: `LaunchActivity` (exported, translucent, no intent
filter, `taskAffinity=""`) takes the ROM as intent data or a `rom`/`ROM`/`path`/`file`/`uri` extra,
plays a readable real path in place (`RomUris.originalPath`; needs All files access on 11+), else
copies it into `roms/` once (skipped while the bytes match), then starts `PokeDaiseyActivity` with
`EXTRA_FROM_FRONTEND` - exit is then `finishAndRemoveTask()`, back to the frontend. A different ROM
arriving while a game is open is switched in `onNewIntent` via `loadRom()` (the activity is paused,
so the engine already stopped/suspended) + `TelemetryStore.reset()` (the sampler caches the
detected game). `LibraryActivity` forwards a non-VIEW launch carrying a ROM there. Setup table in
docs/DEVELOPMENT.md's "Launch from a frontend" section. Not yet tried from a real frontend.

**First-time setup + linked ROMs folder**: `SetupScreen.kt` (state + UI; `LibraryActivity` owns the
pickers and threads) shows over the library while `Prefs.setupDone` is false and the library is empty,
or after Settings' RUN SETUP (`Prefs.setupRequested`): ROMs folder → saves folder (offers folders that
already hold `.sav`/`.srm`: the ROMs folder, RetroArch's per volume - `SavesLocation.suggestions`) →
SteamGridDB key (the shared `CoverSync` runner, also behind Settings > Cover Art). Every step is
skippable. The library is `RomFolder.libraryRoms`: imported `files/roms/` plus the linked folder's
(`Prefs.romsFolder`) supported ROMs, played in place (never copied), rescanned off the UI thread on
every library resume, with `CompanionSupport` verdicts cached by path+size+mtime in
`filesDir/rom-folder-scan.tsv`. Any game can be HIDDEN from its library menu (`Prefs.hiddenRoms`, by path;
name, cover and saves kept; Settings > HIDDEN GAMES shows them again); linked ROMs have no DELETE (the
player's own files). Same-named imported ROMs win (covers/names are keyed by file name). Setup opened by
RUN SETUP has a BACK (bottom-left) that returns to Settings from the first step. Folder picks
need All files access first (`StorageAccess`); setup resumes the pick in `onResume` on return.

**Library menu: LOAD SAVE / INFO / HIDE**: LOAD SAVE (`GameSaves.load`) renames the current save to
`<rom>.backup-<yyyyMMdd-HHmmss>.<ext>` beside it and writes the picked file under the name the game reads,
then drops `SaveStates.freshBootFile` so the next start boots from the save instead of resuming (a resume
or manual state holds the old progress and would write it back). INFO is `GameInfo` (quick pass, then a
second with CRC32/SHA1/companion check). **Releases + updater**: `versionName` is semver (`1.0.3`),
tags are `v<versionName>`, GitHub releases with the APK attached; `AppUpdater` picks the highest-versioned
release with an `.apk`, skipping drafts and pre-releases (publish a test build as a pre-release to keep it from users) and `AppUpdateFlow` downloads it into `cache/updates/` and opens the installer via the
`${applicationId}.updates` FileProvider. Release builds check on every library open, debug builds only from
Settings > VERSION. Release signing: untracked `keystore.properties` → `~/.android/pokedaisey-release.jks`
on this Mac (same key for every release, or updates won't install).

**Unsupported ROMs**: `CompanionSupport.isSupported(file)` (FireRed/Emerald game code, ≤16 MB,
retail LeafGreen rev 0/1 / Ruby / Sapphire rev 1/2 — `TelemetrySampler.OTHER_RETAIL_CODES`,
read as FireRed / Emerald — or a >16 MB hack whose SHA1 is in `TelemetrySampler.SUPPORTED_HACK_SHA1S`) mirrors the
Poller's live `detect()`. The library asks "add anyway?" before importing a ROM that fails
it; in game, `SnapshotView.unsupported` swaps every companion tab but SETTINGS for a
"not supported" notice. Adding a hack to `detect()` means adding its hash to that set too.

**Library covers (SteamGridDB)**: `SteamGridDbGames.forRom` matches known hacks by SHA1, then
≤16 MB FireRed/Emerald ROMs by game code (so retail and the QoL builds get art). The
automatic pick is the preferred uploader's icon (`PREFERRED_AUTHOR_STEAM64`, the user's
choice) before the top-voted one; the long-press REPLACE COVER window (`CoverPicker.kt`)
lists every icon and can search other games. steamgriddb.com is unreachable from cloud
sessions — use `FakeCoverSource` in ui-preview to look at the picker.

The app's only font is **Pixel Operator** (`assets/fonts/PixelOperator.ttf`, CC0,
`pixelFontFamily()`). Its caps are ~0.56em (Press Start 2P, which it replaced, was
~0.88em), so plain `Text` sizes are ~1.5x what the old font needed.

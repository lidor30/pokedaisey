# mgba_dump — headless test-fixture capture

A standalone C program linked directly against `libmgba` — no Android, no
JNI, no adb, no physical device or emulator. Loads a ROM (+ optional save),
runs a scripted boot sequence, and dumps EWRAM+IWRAM to the same
`ewram.bin`/`iwram.bin` format `PokeDaisyActivity`'s on-device capture
(`scripts/capture_fixture.sh`) produces — so it's a drop-in alternative data
source for the JVM decode-logic tests under `app/src/test/`.

Runs identically on a Mac (via Docker Desktop) or a Linux CI runner (e.g.
GitHub Actions) — it's just a Docker container + `libmgba-dev`, nothing
platform- or Android-specific. See `pokedaisy_jni.c` (the Android/JNI
bridge this mirrors) for why each `mCore` API call is there; this file's own
comments cover what's specific to headless capture.

## Usage

Normally you don't invoke this directly — use
`scripts/capture_fixture_headless.sh <key>`, which handles compiling it,
copying the ROM/save (never touches your real save file — always works on a
disposable copy, since the emulator can write to it), running the boot
sequence, and writing the result into
`app/src/test/resources/fixtures/<key>/`.

To run it by hand (e.g. to iterate on a new boot sequence):

```sh
docker run --rm -v "$(pwd):/work" -w /work pokedaisy-capture \
    -c "gcc -O2 -Wall -o mgba_dump mgba_dump.c -I/usr/include -L/usr/lib/aarch64-linux-gnu -lmgba -lm"
docker run --rm -v "$(pwd):/work" -w /work pokedaisy-capture \
    -c "./mgba_dump rom.gba save.sav < boot/default.txt"
```

(Build the image once with `make capture-image` - the scripts also build it on
first use. Its `ENTRYPOINT` is `/bin/bash`, so always invoke it as
`-c "<full command>"`, not bare args.)

## stdin command language

One command per line:

| Command | Effect |
|---|---|
| `wait N` | Advance N frames with no keys held |
| `press KEYS` | Hold KEYS for one frame, release | `KEYS` is comma-separated: `A,B,SELECT,START,UP,DOWN,LEFT,RIGHT,L,R` |
| `hold KEYS N` | Hold KEYS for N frames, release |
| `dump DIR` | Write `DIR/ewram.bin` + `DIR/iwram.bin` (DIR must already exist) |
| `shot FILE` | Write the current frame to `FILE` as a binary PPM (convert with PIL) — for checking what the game actually shows (e.g. party-menu icons) |
| `vdump DIR` | Write `DIR/{vram,pal,oam,io}.bin` — VRAM, palette RAM, OAM and I/O registers, i.e. what the PPU draws from (how the CFRU party-slot graphics were traced back to the ROM) |
| `poke8/poke16/poke32 ADDR VAL` | Write RAM (hex or decimal) — e.g. force a party mon fainted/asleep/an egg before opening the party menu |
| `peek32 ADDR` | Print a word to stdout |
| `poke8p PTR OFF VAL` / `peek8p PTR OFF` | Write / print the byte at `*(u32 *)PTR + OFF` — save-block fields (Emerald moves its save blocks on every load), e.g. `poke8p 0x030057D8 0x2951 0` clears a caught flag in TMT2 |
| `call ADDR ARG0 [ARG1]` | Run Thumb function ADDR with r0 = ARG0 (r1 = ARG1) to its return, CPU state restored after (the app's `pk_call`) — e.g. `call 0x081dd164 297` starts FireRed's MUS_VS_TRAINER |
| `park ADDR` | Park the main loop on a Thumb `b .` at ADDR once the VBlank IRQ is fully on (the app's `pkRenderPark`) |
| `bgm` | Print the m4a BGM player: address, status, song header (what `FfMusicKey` reads) |
| `wav FILE N` | Run N frames and write what the APU played to `FILE` as a 16-bit stereo WAV — e.g. `call <m4aSongNumStart> 0`, `wait 20`, `call <m4aSongNumStart> 5`, `wav click.wav 45` renders SE_SELECT alone, the app's click sound |

Lines starting with `#`, and blank lines, are ignored.

## Gotchas already hit once (don't repeat)

- **Open the save file `O_RDWR`, not `O_RDONLY`.** The game writes to its own
  flash/SRAM during normal play; mGBA's flash-write path segfaults on the
  second in-game write if the backing VFile can't be written to. Always pass
  a **disposable copy** of a save, never the original — this tool WILL mutate
  it.
- **A save file's party/bag load as soon as "Continue" is selected**, well
  before any "quest log recap" dialogue has been clicked through — you don't
  need to mash through the recap pages to get valid data, just clear the
  title screen and pick Continue. `boot/default.txt` is deliberately more
  generous than that (extra presses to reach a stable "on the field" state
  for other purposes, e.g. screenshots), but if you only need the struct
  populated, a much shorter script suffices.
- **Extra button presses after actually reaching the field are not free.**
  If the save point happens to be near an NPC, shop counter, or PC, a spammed
  `press A` can trigger a real in-game interaction and change save state
  (seen once: a save's bag contents differed between two captures of nominally
  the same state, traced to exactly this). Prefer a boot script tuned to stop
  once the struct is populated over "press A a lot more, just in case."
- **mGBA's default logger is very noisy** (every unmapped I/O register, DMA,
  BIOS SWI call, straight to stdout/stderr) — this tool installs a no-op
  logger by default; pass `--verbose` (before the ROM path) to get the
  default logger back when debugging a new boot sequence.

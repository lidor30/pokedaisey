#!/usr/bin/env bash
# Screenshots the in-game POKeMON (party), POKeDEX and BAG (items) screens of
# every ROM in host_roms.conf via headless libmgba (native-capture/mgba_dump.c,
# built and run inside the pokedaisy-capture image, like capture_fixture_headless.sh).
# No Android, no device. Output: <out>/<key>/{party,pokedex,items}.png (+ start.png,
# the START menu as it first opens, to sanity-check the menu layouts below).
#
# Each screen gets a FRESH boot (boot script -> START -> menu keys -> wait -> shot):
# the START menu remembers its cursor between openings and some screens return to the
# field on B, so a fixed key script over one session would land on the wrong row
# (CLAUDE.md "Testing"). A fresh boot starts the cursor where the save left it - the
# top row for every game here except ROWE, which saves it (see menu_for).
#
# Usage: scripts/screenshot_menus.sh [-f] [-o OUT_DIR] [-s SCALE] [key ...]
#   no keys = every ROM in host_roms.conf whose ROM file exists (missing ones are skipped).
#   Shots already in OUT_DIR are kept and not re-taken; delete one to redo just it.
#   -f   force: re-take every shot, overwriting existing ones
#   -o   output dir (default: native-capture/menu-shots, gitignored)
#   -s   integer nearest-neighbour upscale of the 240x160 frame (default 3)
# Env:
#   NO_DEX="key1 key2"   extra saves without a Pokedex yet (see menu_for): the START menu
#                        then starts at POKeMON and there is no pokedex shot.
#   WAIT_OPEN=N          frames to wait after opening a screen before the shot (default 150;
#                        the Pokedex fades in slowest).
# If a shot shows the wrong screen, check start.png and fix that key in menu_for.
# Like every capture here it works on a disposable COPY of the save, never the original.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CAPTURE_DIR="$SCRIPT_DIR/../native-capture"
BOOT_DIR="$CAPTURE_DIR/boot"
CONF="$SCRIPT_DIR/host_roms.conf"
IMAGE="pokedaisy-capture"
OUT_DIR="$CAPTURE_DIR/menu-shots"
SCALE=3
WAIT_OPEN="${WAIT_OPEN:-150}"
NO_DEX="${NO_DEX:-}"
FORCE=0

while getopts "fo:s:" opt; do
    case "$opt" in
        f) FORCE=1 ;;
        o) OUT_DIR="$OPTARG" ;;
        s) SCALE="$OPTARG" ;;
        *) sed -n '2,/^set -e/p' "$0" | sed '$d' >&2; exit 2 ;;
    esac
done
shift $((OPTIND - 1))

# Same boot-script choice the fixture captures use (see host_roms.conf / boot/*.txt).
boot_for() {
    case "$1" in
        leafgreen_*)              echo "$BOOT_DIR/leafgreen.txt" ;;
        ruby_*|sapphire_*)        echo "$BOOT_DIR/ruby_sapphire.txt" ;;
        heart_and_soul*|lazarus)  echo "$BOOT_DIR/rhh_splash.txt" ;;
        *)                        echo "$BOOT_DIR/default.txt" ;;
    esac
}

# START-menu keys to each screen, as "screen:KEY,KEY,..." (KEY*N = N presses).
# Default: POKeDEX, POKeMON, BAG on rows 0-1-2 (FRLG, RSE and most hacks; Emerald/Ruby
# add POKeNAV only below BAG).
menu_for() {
    for k in $NO_DEX; do [[ "$k" == "$1" ]] && { echo "party:A items:DOWN,A"; return; }; done
    case "$1" in
        # These saves are early-game with no Pokedex yet, so the menu starts at POKeMON
        # (a property of the save, not the hack - move a key out once its save has one).
        emerald_seaglass|celia|radical_red|amethyst|heart_and_soul|lazarus|gaia)
                 echo "party:A items:DOWN,A" ;;
        # 2-column grid: POKeDEX POKeMON / BAG STATUS / SAVE QUESTS.
        odyssey) echo "pokedex:A party:RIGHT,A items:DOWN,A" ;;
        # A wrapping list with no Pokedex: Pokemon, Lidor, Inventory, Trainer Skills,
        # Achievements, DexNav, PokeNav, Settings, Information, Save, Exit - and the
        # cursor opens where the save left it (Save, in this save).
        rowe)    echo "party:UP*9,A items:UP*7,A" ;;
        *)       echo "pokedex:A party:DOWN,A items:DOWN*2,A" ;;
    esac
}

all_keys() { grep -v '^#' "$CONF" | grep -v '^$' | cut -d'|' -f1; }
# No mapfile: macOS's /bin/bash is 3.2.
KEYS=()
if [[ $# -gt 0 ]]; then KEYS=("$@"); else while IFS= read -r k; do KEYS+=("$k"); done < <(all_keys); fi
if [[ ${#KEYS[@]} -eq 0 ]]; then echo "No ROM keys (empty $CONF?)" >&2; exit 2; fi

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
mkdir -p "$OUT_DIR"

echo "Building mgba_dump..."
docker image inspect "$IMAGE" >/dev/null 2>&1 || docker build -q -t "$IMAGE" "$CAPTURE_DIR"
docker run --rm -v "$CAPTURE_DIR:/build" -v "$work:/work" -w /build "$IMAGE" \
    -c 'gcc -O2 -Wall -o /work/mgba_dump mgba_dump.c -I/usr/include -L/usr/lib/$(gcc -print-multiarch) -lmgba -lm'

ppm_to_png() { # in.ppm out.png scale - stdlib-only (zlib), nearest-neighbour upscale
    python3 - "$@" <<'PY'
import sys, zlib, struct
src, dst, k = sys.argv[1], sys.argv[2], int(sys.argv[3])
d = open(src, "rb").read()
parts = d.split(b"\n", 3)          # P6 / W H / 255 / data
w, h = map(int, parts[1].split())
px = parts[3]
rows = []
for y in range(h):
    row = px[y * w * 3:(y + 1) * w * 3]
    big = b"".join(row[x * 3:x * 3 + 3] * k for x in range(w))
    rows.extend([b"\x00" + big] * k)
def chunk(t, b):
    c = struct.pack(">I", len(b)) + t + b
    return c + struct.pack(">I", zlib.crc32(t + b) & 0xFFFFFFFF)
png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w * k, h * k, 8, 2, 0, 0, 0)) \
    + chunk(b"IDAT", zlib.compress(b"".join(rows), 9)) + chunk(b"IEND", b"")
open(dst, "wb").write(png)
PY
}

ok=0; skipped=0; existing=0; failed=0
for key in "${KEYS[@]}"; do
    line="$(grep -v '^#' "$CONF" | awk -F'|' -v k="$key" '$1==k')"
    if [[ -z "$line" ]]; then echo "[$key] not in $CONF" >&2; failed=$((failed + 1)); continue; fi
    label="$(cut -d'|' -f2 <<<"$line")"; rom="$(cut -d'|' -f3 <<<"$line")"; save="$(cut -d'|' -f4 <<<"$line")"
    rom="${rom/#\~/$HOME}"; save="${save/#\~/$HOME}"
    if [[ ! -f "$rom" ]]; then echo "[$key] ROM not found, skipping: $rom"; skipped=$((skipped + 1)); continue; fi

    # start.png: the START menu as it opens; then one fresh boot per screen.
    todo=()
    read -r -a entries <<<"start: $(menu_for "$key")"   # read, not $(...) splitting: no globbing of UP*9
    for t in "${entries[@]}"; do
        if (( ! FORCE )) && [[ -f "$OUT_DIR/$key/${t%%:*}.png" ]]; then existing=$((existing + 1)); continue; fi
        todo+=("$t")
    done
    if [[ ${#todo[@]} -eq 0 ]]; then echo "[$key] all shots exist, skipping (-f to redo)"; continue; fi

    echo "[$key] $label"
    run="$work/$key"; mkdir -p "$run"
    cp "$rom" "$run/rom.gba"
    save_arg=""
    if [[ -n "$save" && -f "$save" ]]; then cp "$save" "$run/save.sav"; save_arg="save.sav"
    else echo "[$key] no save - fresh new game (menus will be mostly empty)" >&2; fi

    boot="$(boot_for "$key")"
    for t in "${todo[@]}"; do
        name="${t%%:*}"; keys="${t#*:}"
        {
            cat "$boot"
            # FireRed's "Previously on your quest..." recap can still be up when the boot
            # script ends - it looks like the field but swallows START. B closes its last
            # page and is harmless on the field.
            for ((i = 0; i < 6; i++)); do echo "press B"; echo "wait 30"; done
            echo "wait 120"
            # Menu keys are held 4 frames, not pressed for 1: Amethyst's START menu misses
            # 1-frame presses, and ROWE's misses presses 10 frames apart.
            echo "press START"; echo "wait 60"
            if [[ -n "$keys" ]]; then
                IFS=, read -r -a presses <<<"$keys"
                for k in "${presses[@]}"; do
                    n=1; [[ "$k" == *"*"* ]] && n="${k#*\*}"
                    for ((i = 0; i < n; i++)); do echo "hold ${k%%\**} 4"; echo "wait 20"; done
                done
            fi
            echo "wait $WAIT_OPEN"
            echo "shot /work/$key/$name.ppm"
        } >"$run/$name.txt"
        # fresh save copy per boot: the game can write to it
        if [[ -n "$save_arg" ]]; then cp "$save" "$run/save.sav"; fi
        docker run --rm -v "$work:/work" -w "/work/$key" "$IMAGE" \
            -c "/work/mgba_dump rom.gba $save_arg < $name.txt" >/dev/null 2>&1 || true
        if [[ -f "$run/$name.ppm" ]]; then
            mkdir -p "$OUT_DIR/$key"
            ppm_to_png "$run/$name.ppm" "$OUT_DIR/$key/$name.png" "$SCALE"
            echo "    $OUT_DIR/$key/$name.png"
        else
            echo "    FAILED: $name (re-run the boot by hand with --verbose)" >&2; failed=$((failed + 1))
        fi
    done
    ok=$((ok + 1))
done

echo "Done: $ok ROM(s) processed, $skipped skipped (no ROM file), $existing existing shot(s) kept, $failed failed shot(s). Output: $OUT_DIR"
(( failed == 0 ))

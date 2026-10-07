#!/usr/bin/env bash
# Device regression smoke test: launches PokeDaisy against every ROM listed
# in roms.conf (in turn, on the connected device/emulator) and checks the
# telemetry log line PokeDaisy already prints ~1x/sec
# (TelemetryStore.refresh()) looks healthy - connected, a non-empty party,
# and (new 2026-09-22) more than one distinct item POCKET represented, so a
# regression that silently collapses categorization back to one bucket (the
# exact bug fixed this session) fails loudly here instead of only showing up
# as "the sidebar only has one category" on a human's next manual pass.
#
# This does NOT replace a human looking at the actual screen - it can't see
# rendering bugs (the width-order Compose bug earlier this session would have
# passed this script fine, since the DATA was always correct - only the
# LAYOUT was broken). It catches address/detection/decode regressions: wrong
# RAM addresses, a broken SHA1 detection constant, a decode-logic bug that
# zeroes out the party, pocket tagging regressing to one bucket, etc.
#
# Usage: scripts/smoke_test.sh [device-serial] [wait-seconds]
#   device-serial defaults to whatever `adb` picks with no -s (fails loudly
#   if more than one device is attached and none is given).
#   wait-seconds (default 10) is how long to let each ROM boot + run before
#   reading its telemetry line - bump it if a slow-to-detect big hack (the
#   BPRE-hack SHA1 scan reads up to 32 MB) needs more time.
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONF="$SCRIPT_DIR/roms.conf"
SERIAL="${1:-}"
WAIT_SECS="${2:-10}"
PKG="com.pokedaisy.app"

ADB=(adb)
if [[ -n "$SERIAL" ]]; then
    ADB=(adb -s "$SERIAL")
fi

if ! "${ADB[@]}" get-state >/dev/null 2>&1; then
    echo "No device reachable via '${ADB[*]}'. Pass a serial: scripts/smoke_test.sh <serial>" >&2
    echo "Connected devices:" >&2
    adb devices -l >&2
    exit 2
fi

pass=0
fail=0
declare -a fail_labels=()

# Read into an array first, not a `while read ... < "$CONF"` loop: the adb
# commands below read from stdin too (even with no args, adb can consume it),
# which silently truncates a `while` loop fed from the same file descriptor
# after just the first iteration. (Not using `mapfile`/`readarray` - not
# available on macOS's stock bash 3.2.)
conf_lines=()
while IFS= read -r conf_line || [[ -n "$conf_line" ]]; do
    conf_lines+=("$conf_line")
done <"$CONF"
for conf_line in "${conf_lines[@]}"; do
    IFS='|' read -r key label path <<<"$conf_line"
    [[ -z "$key" || "$key" == \#* ]] && continue

    "${ADB[@]}" shell am force-stop "$PKG" >/dev/null 2>&1 </dev/null
    "${ADB[@]}" logcat -c </dev/null
    # `adb shell` reassembles multi-arg invocations into one command line for
    # the DEVICE's /system/bin/sh, which chokes on the parens/spaces most of
    # these ROM filenames have unless the path is quoted for THAT shell (not
    # just the local one) - single-quote it inside one shell string.
    if ! "${ADB[@]}" shell "am start -n '$PKG/.PokeDaisyActivity' --es rom '$path'" >/dev/null 2>&1 </dev/null; then
        echo "FAIL  $label — am start failed (is the debug APK installed? scripts/roms.conf path exist on device?)"
        fail=$((fail + 1))
        fail_labels+=("$label: am start failed")
        continue
    fi

    sleep "$WAIT_SECS"

    line="$("${ADB[@]}" logcat -d 2>/dev/null | grep 'pokedaisy: telemetry' | tail -1)"
    if [[ -z "$line" ]]; then
        echo "FAIL  $label — no telemetry line within ${WAIT_SECS}s (ROM crashed, hung, or isn't detected at all)"
        fail=$((fail + 1))
        fail_labels+=("$label: no telemetry output")
        continue
    fi

    connected="$(sed -n 's/.*connected=\([a-z]*\).*/\1/p' <<<"$line")"
    party="$(sed -n 's/.*party=\([0-9]*\).*/\1/p' <<<"$line")"
    pockets="$(sed -n 's/.*pockets=\([^ ]*\).*/\1/p' <<<"$line")"
    pocket_kinds="$(tr ',' '\n' <<<"$pockets" | grep -c ':' || true)"

    ok=1
    reasons=()
    if [[ "$connected" != "true" ]]; then
        ok=0; reasons+=("not connected")
    fi
    if [[ -z "$party" || "$party" -lt 1 ]]; then
        ok=0; reasons+=("empty party")
    fi
    # Only enforced when the line actually reports items at all - a fresh
    # save with zero items everywhere is legitimate, not a categorization bug.
    if [[ -n "$pockets" && "$pocket_kinds" -eq 1 ]]; then
        echo "WARN  $label — items present but only ONE pocket represented ($pockets); could be a real single-pocket save, or the categorization regression this script exists to catch. Eyeball it."
    fi

    if [[ "$ok" -eq 1 ]]; then
        echo "PASS  $label — $line"
        pass=$((pass + 1))
    else
        echo "FAIL  $label — ${reasons[*]} — $line"
        fail=$((fail + 1))
        fail_labels+=("$label: ${reasons[*]}")
    fi
done

echo
echo "$pass passed, $fail failed"
if [[ $fail -gt 0 ]]; then
    echo "Failed:"
    printf '  - %s\n' "${fail_labels[@]}"
    exit 1
fi

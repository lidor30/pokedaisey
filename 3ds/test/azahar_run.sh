#!/bin/sh
# Boots pokedaisy.3dsx in the Azahar 3DS emulator on a virtual display and
# saves screenshots - run inside the linuxserver/azahar image with the repo at
# /repo and an output directory at /out (`make azahar-test` in 3ds/).
#
#   azahar_run.sh <fixture rom> <step>...  each step is an xdotool key name
#                                          (Azahar's keys: a = A, z = X, x = Y,
#                                          m = START), "shot:<name>",
#                                          "wait:<seconds>" or "click:<x>,<y>"
set -eu
ROM="$1"
shift
# The emulated SD card (linuxserver images set HOME=/config).
SD="$HOME/.local/share/azahar-emu/sdmc"
mkdir -p "$SD/pokedaisy/roms" /out/fb
cp "/repo/3ds/build/$ROM" "$SD/pokedaisy/roms/"
# Leave a copy of the SD card for checking what the app wrote (saves, backups)
# and Azahar's own log.
trap 'cp -r "$SD/pokedaisy" /out/sd 2>/dev/null || true; cp "$HOME"/.local/share/azahar-emu/log/*.txt /out/ 2>/dev/null || true' EXIT

Xvfb :1 -screen 0 1280x960x24 -fbdir /out/fb > /out/xvfb.log 2>&1 &
sleep 2
export DISPLAY=:1
azahar /repo/3ds/ctr/pokedaisy.3dsx > /out/azahar.log 2>&1 &
sleep 25

for step in "$@"; do
    case "$step" in
    shot:*) cp /out/fb/Xvfb_screen0 "/out/${step#shot:}.xwd" ;;
    wait:*) sleep "${step#wait:}" ;;
    click:*)
        # Window coordinates: the mouse is the stylus on the bottom screen.
        xy="${step#click:}"
        xdotool mousemove "${xy%,*}" "${xy#*,}" mousedown 1
        sleep 0.3
        xdotool mouseup 1
        sleep 1
        ;;
    *)
        WIN=$(xdotool search --name "Azahar" | tail -1)
        xdotool windowactivate --sync "$WIN" 2>/dev/null || true
        xdotool keydown "$step"
        sleep 0.3
        xdotool keyup "$step"
        sleep 1
        ;;
    esac
done

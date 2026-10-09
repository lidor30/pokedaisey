#!/bin/bash
# Builds the fixture test ROMs (see fixture_rom.c) into 3ds/build/: run inside
# the devkitARM image with the repo mounted at /repo - `make fixture-roms`.
set -euo pipefail
cd /repo/3ds
mkdir -p build
FIXTURES=/repo/app/src/test/resources/fixtures
GCC="$DEVKITARM/bin/arm-none-eabi-gcc"
OBJCOPY="$DEVKITARM/bin/arm-none-eabi-objcopy"
GBAFIX=/opt/devkitpro/tools/bin/gbafix

# fixture key, game code, revision
for spec in "firered_vanilla BPRE 1" "emerald_vanilla BPEE 0" "emerald_de_battle BPEE 0"; do
    set -- $spec
    out="build/fixture-$1"
    "$GCC" -mthumb -mcpu=arm7tdmi -O2 -specs=gba.specs \
        test/fixture_rom.c \
        -x assembler-with-cpp \
        -DFIXTURE_EWRAM="\"$FIXTURES/$1/ewram.bin\"" -DFIXTURE_IWRAM="\"$FIXTURES/$1/iwram.bin\"" \
        test/fixture_data.s \
        -o "$out.elf"
    "$OBJCOPY" -O binary "$out.elf" "$out.gba"
    "$GBAFIX" "$out.gba" -tPOKEDAISYTST -c"$2" -m01 -r"$3" > /dev/null
    rm "$out.elf"
    echo "$out.gba ($2 rev $3)"
done

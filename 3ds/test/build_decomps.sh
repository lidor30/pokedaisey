#!/bin/bash
# Builds pret's pokefirered (rev 1) and pokeemerald from source into
# 3ds/build/decomps/ - byte-identical to the retail ROMs - so the preview and
# the Azahar test have a ROM for the tabs that read one (MAP, POKéDEX, GUIDE).
# Never committed. `make decomps`; needs git, a host C compiler, libpng and
# devkitARM ($DEVKITARM, or /opt/devkitpro/devkitARM).
set -euo pipefail
cd "$(dirname "$0")/.."
D=build/decomps
mkdir -p "$D"
export DEVKITARM="${DEVKITARM:-/opt/devkitpro/devkitARM}"
export PATH="$DEVKITARM/bin:$PATH"

# The commits these were checked at.
clone() {
    if [ ! -d "$D/$1" ]; then
        git clone -q "https://github.com/pret/$1" "$D/$1"
        (cd "$D/$1" && git -c advice.detachedHead=false checkout -q "$2")
    fi
}
clone agbcc da598c1d918402c42c0c0d7128ba14567f3175e9
clone pokefirered 037335f4c725d7c9aecdac87066f2002b4bd7e14
clone pokeemerald 731ad5bfd6e6f265508d0efcca0ba42f9dcf5881

(cd "$D/agbcc" && ./build.sh > build.log 2>&1 && ./install.sh ../pokefirered > /dev/null &&
    ./install.sh ../pokeemerald > /dev/null)
make -C "$D/pokefirered" firered_rev1 -j"$(nproc)" > "$D/pokefirered.log" 2>&1
make -C "$D/pokeemerald" -j"$(nproc)" > "$D/pokeemerald.log" 2>&1

# Retail FireRed rev 1 and Emerald.
cat > "$D/sha1" <<EOF
dd5945db9b930750cb39d00c84da8571feebf417  $D/pokefirered/pokefirered_rev1.gba
f3ae088181bf583e55daf962a92bb46f4f1d07b7  $D/pokeemerald/pokeemerald.gba
EOF
sha1sum -c "$D/sha1"

# PokeDaisy for the Nintendo 3DS (homebrew) - see README.md.
#
#   make preview   render the companion from the RAM fixtures into build/shots/
#   make 3ds       build ctr/pokedaisy.3dsx (devkitARM, in Docker)
#   make fixture-roms  build/fixture-*.gba: test ROMs holding a RAM fixture
#   make azahar-test   run the .3dsx in the Azahar emulator, screenshots in build/azahar/
#   make tables    regenerate core/*_gen.c (the app's Kotlin tables + font) and ctr/icon.png

REPO := $(abspath ..)
FIXTURES := $(REPO)/app/src/test/resources/fixtures
BUILD := build
CC ?= cc
CFLAGS ?= -std=c11 -O2 -Wall -Wextra -Werror
CORE_SRC := $(wildcard core/*.c)
DEVKITARM_IMAGE := devkitpro/devkitarm:latest

.PHONY: preview 3ds fixture-roms azahar-test tables clean

$(BUILD)/pd_preview: $(CORE_SRC) $(wildcard core/*.h) desktop/pd_preview.c desktop/pd_png.c desktop/pd_png.h
	@mkdir -p $(BUILD)
	$(CC) $(CFLAGS) -o $@ $(CORE_SRC) desktop/pd_preview.c desktop/pd_png.c -lm

preview: $(BUILD)/pd_preview
	@mkdir -p $(BUILD)/shots
	$(BUILD)/pd_preview $(FIXTURES)/firered_vanilla BPRE $(BUILD)/shots firered
	$(BUILD)/pd_preview $(FIXTURES)/emerald_vanilla BPEE $(BUILD)/shots emerald
	# German Emerald keeps English's RAM layout: the only Emerald battle fixture.
	$(BUILD)/pd_preview $(FIXTURES)/emerald_de_battle BPEE $(BUILD)/shots emerald-battle

tables:
	python3 tools/gen_tables.py
	python3 tools/gen_font.py
	python3 tools/gen_icon.py

# The repo is mounted whole: the build needs third_party/mgba next to 3ds/.
3ds:
	docker run --rm -v "$(REPO):/repo" -w /repo/3ds/ctr $(DEVKITARM_IMAGE) \
		bash -c "cmake -S . -B build -DCMAKE_BUILD_TYPE=Release \
			-DCMAKE_TOOLCHAIN_FILE=/repo/third_party/mgba/src/platform/3ds/CMakeToolchain.txt \
			&& cmake --build build -j$$(nproc) && cp build/pokedaisy.3dsx ."

# Test ROMs that load a RAM fixture (test/fixture_rom.c): the 3DS app run on
# them, in Azahar or on a console, shows the fixtures' party with no Pokémon ROM.
fixture-roms:
	docker run --rm -v "$(REPO):/repo" $(DEVKITARM_IMAGE) bash /repo/3ds/test/build_fixture_roms.sh

# The real .3dsx in the Azahar emulator (linuxserver/azahar image, Xvfb inside)
# on the FireRed fixture ROM, tapped through like a player would: the game
# list, PARTY (SHARP 1.5x, the default), BAG, SETTINGS, the SCREEN pick-list,
# STRETCH, PIXEL, fast-forward on, a saved state and loading it - screenshots in
# build/azahar/*.png (needs ImageMagick's convert). Taps are window
# coordinates: the bottom screen's (x, y) is (480 + x, 478 + y).
AZAHAR_IMAGE := linuxserver/azahar:latest
AZAHAR_SHOTS := 01-menu 02-party 03-bag 04-settings 05-pick 06-stretch 07-pixel 08-ff 09-saved 10-loaded
azahar-test: 3ds fixture-roms
	rm -rf $(BUILD)/azahar && mkdir -p $(BUILD)/azahar
	docker run --rm -v "$(REPO):/repo" -v "$(abspath $(BUILD))/azahar:/out" --entrypoint /bin/sh $(AZAHAR_IMAGE) \
		/repo/3ds/test/azahar_run.sh fixture-firered_vanilla.gba \
		shot:01-menu a wait:6 shot:02-party \
		click:692,491 wait:1 shot:03-bag \
		click:783,491 wait:1 shot:04-settings \
		click:580,626 wait:1 shot:05-pick click:580,596 wait:2 shot:06-stretch \
		click:580,626 wait:1 click:580,560 wait:2 shot:07-pixel \
		click:751,491 wait:3 shot:08-ff click:751,491 \
		click:580,662 wait:1 click:586,628 wait:2 shot:09-saved \
		click:580,680 wait:1 click:586,628 wait:2 shot:10-loaded
	for s in $(AZAHAR_SHOTS); do convert xwd:$(BUILD)/azahar/$$s.xwd -crop 640x500+320+230 $(BUILD)/azahar/$$s.png; done

clean:
	rm -rf $(BUILD) ctr/build ctr/pokedaisy.3dsx

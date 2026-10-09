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
# on the FireRed fixture ROM: screenshots of the game list, PARTY, a tapped
# summary, BATTLE and INFO in build/azahar/*.png (needs ImageMagick's convert).
AZAHAR_IMAGE := linuxserver/azahar:latest
AZAHAR_SHOTS := 1-menu 2-party 3-summary 4-battle 5-info
azahar-test: 3ds fixture-roms
	rm -rf $(BUILD)/azahar && mkdir -p $(BUILD)/azahar
	docker run --rm -v "$(REPO):/repo" -v "$(abspath $(BUILD))/azahar:/out" --entrypoint /bin/sh $(AZAHAR_IMAGE) \
		/repo/3ds/test/azahar_run.sh fixture-firered_vanilla.gba \
		shot:1-menu a wait:6 shot:2-party click:720,518 wait:1 shot:3-summary z wait:1 shot:4-battle z wait:2 shot:5-info
	for s in $(AZAHAR_SHOTS); do convert xwd:$(BUILD)/azahar/$$s.xwd -crop 640x500+320+230 $(BUILD)/azahar/$$s.png; done

clean:
	rm -rf $(BUILD) ctr/build ctr/pokedaisy.3dsx

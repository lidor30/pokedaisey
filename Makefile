# Convenience wrappers around PokeDaisey's workflows (`make help`). The real
# logic lives in Gradle, scripts/ and ui-preview/.
#
# Machine-local paths (decomp builds, mon icons) go in an untracked local.mk -
# copy local.mk.example. Variables can also be passed per run:
#   make preview MON_ICONS=/path/to/icons

SHELL := /bin/bash
-include local.mk

PKG           := com.pokedaisey.app
CAPTURE_IMAGE := pokedaisey-capture

# Read by scripts/decomps.py and ui-preview (see docs/DEVELOPMENT.md "Build").
export DECOMPS
export MON_ICONS

.DEFAULT_GOAL := help

.PHONY: help submodules apk install run test clean shots preview menu-shots capture-image

help: ## Show this help
	@awk 'BEGIN{FS=":.*## "} \
	     /^##@ / {printf "\n\033[1m%s\033[0m\n", substr($$0,5); next} \
	     /^[a-zA-Z0-9_-]+:.*## / {printf "  \033[36m%-16s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST)

##@ App

submodules: ## Fetch the mGBA submodule (first build only)
	git submodule update --init third_party/mgba

apk: ## Build the debug APK
	./gradlew :app:assembleDebug
	@echo ">> app/build/outputs/apk/debug/app-debug.apk"

install: ## Build + install on a connected Android device (adb)
	./gradlew :app:installDebug

run: install ## Build + install + launch the library screen on the connected device
	adb shell am start -n $(PKG)/.LibraryActivity

test: ## JVM unit tests (fixture decode tests, UI logic)
	./gradlew :app:testDebugUnitTest

clean: ## gradle clean
	./gradlew clean

##@ Looking at the UI

shots: ## Paparazzi screenshots of the companion tabs -> app/src/test/snapshots/images/
	./gradlew :app:recordPaparazziDebug --tests '*ScreenshotTest'

preview: ## Render the UI without the Android SDK (Compose Desktop) -> ui-preview/build/shots/
	./gradlew -p ui-preview render

##@ Headless captures (Docker + libmgba)

capture-image: ## Build the Docker image the capture scripts use
	docker build -t $(CAPTURE_IMAGE) native-capture

menu-shots: ## In-game party/pokedex/items screenshots of every ROM in host_roms.conf (KEYS="a b" to pick) -> native-capture/menu-shots/
	scripts/screenshot_menus.sh $(KEYS)

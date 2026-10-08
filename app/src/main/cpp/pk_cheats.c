// PokeDaisy — cheats (GameShark / Action Replay / CodeBreaker / VBA codes) on
// mGBA's own cheat engine. Plain C over `struct mCore*`, no JNI, so
// native-capture/mgba_dump.c compiles this very file in and tests it headless.
//
// Kotlin owns the cheat list (CheatStore: filesDir/cheats/<crc>.cheats, mGBA's
// .cheats format) and hands the core the enabled ones as one mGBA-format text;
// pk_cheats_apply swaps them in. Only enabled cheats ever reach the core, so with
// none on the ROM is byte-for-byte the file again (hooks and ROM patches undone).
//
// Cheats work with USE_DEBUGGERS off: GBA cheats run at frame end
// (GBAFrameEnded) or from a ROM hook, a BKPT that lands on the cheat device's
// CPU component (GBABreakpoint's CPU_COMPONENT_CHEAT_DEVICE case, outside the
// debugger #ifdef) - checked with mgba_dump's `cheat` command.
//
// A hook or a ROM-patch code writes into the ROM mGBA runs from, and the ROM is
// also what identifies the game: the Poller hashes it over the bus (hack SHA1s)
// and RetroAchievements hashes gba->memory.rom. So the original bytes under every
// patch are kept here (sFixes): pk_cheats_overlay puts them back into a bus read,
// pk_cheats_swap_rom swaps them in and out around RA's hash.

#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <mgba/core/core.h>
#include <mgba/core/cheats.h>
#include <mgba/internal/gba/gba.h>
#include <mgba/internal/gba/cheats.h>
#include <mgba-util/vfs.h>

#include "pk_cheats.h"

// Code types, = mGBA's enum GBACheatType (and CheatType.native on the Kotlin side).
#define PK_CHEAT_AUTO 0          // GBA_CHEAT_AUTODETECT
#define PK_CHEAT_CODEBREAKER 1   // GBA_CHEAT_CODEBREAKER
#define PK_CHEAT_GAMESHARK 2     // GBA_CHEAT_GAMESHARK (GameShark / Action Replay v1-v2)
#define PK_CHEAT_ACTION_REPLAY 3 // GBA_CHEAT_PRO_ACTION_REPLAY (Action Replay v3)

#define PK_MAX_FIXES 512

// The original ROM bytes under a hook or a ROM patch. `orig` is what the file
// holds; pk_cheats_swap_rom trades it with the live bytes and back.
struct pk_fix {
    uint32_t addr;
    int width;
    uint8_t orig[4];
};

static struct pk_fix sFixes[PK_MAX_FIXES];
static int sFixCount;

static int pk_cheats_is_gba(struct mCore* core) {
    return core && core->platform(core) == mPLATFORM_GBA;
}

static uint8_t* pk_rom_byte(struct mCore* core, uint32_t addr) {
    struct GBA* gba = core->board;
    uint32_t off = addr & (SIZE_CART0 - 1);
    if (!gba->memory.rom || off >= gba->memory.romSize) {
        return NULL;
    }
    return (uint8_t*) gba->memory.rom + off;
}

static int pk_fix_known(uint32_t addr) {
    for (int i = 0; i < sFixCount; i++) {
        if (sFixes[i].addr == addr) {
            return 1;
        }
    }
    return 0;
}

static void pk_fix_add(uint32_t addr, int width, uint32_t orig) {
    // Two hooks on one address: the first holds the real opcode, the second only
    // the first one's BKPT.
    if (sFixCount >= PK_MAX_FIXES || pk_fix_known(addr)) {
        return;
    }
    struct pk_fix* f = &sFixes[sFixCount++];
    f->addr = addr;
    f->width = width;
    for (int i = 0; i < 4; i++) {
        f->orig[i] = (uint8_t) (orig >> (8 * i));
    }
}

// Takes every cheat out of the core, undoing what it did to the ROM: a disabled
// refresh unpatches its ROM patches, removing the set takes its hook's BKPT out.
// mCheatDeviceClear alone would leave both behind. Last first, so two sets on one
// hook put the real opcode back last.
void pk_cheats_clear(struct mCore* core) {
    sFixCount = 0;
    if (!pk_cheats_is_gba(core)) {
        return;
    }
    struct mCheatDevice* device = core->cheatDevice(core);
    if (!device) {
        return;
    }
    while (mCheatSetsSize(&device->cheats)) {
        struct mCheatSet* set = *mCheatSetsGetPointer(&device->cheats, mCheatSetsSize(&device->cheats) - 1);
        set->enabled = false;
        mCheatRefresh(device, set);
        mCheatRemoveSet(device, set);
        mCheatSetDeinit(set);
    }
}

// Replaces the core's cheats with those in `text` (mGBA .cheats format, the
// enabled ones only). Returns how many sets were loaded, -1 if it didn't parse.
int pk_cheats_apply(struct mCore* core, const char* text, size_t len) {
    pk_cheats_clear(core);
    if (!pk_cheats_is_gba(core) || !text || !len) {
        return 0;
    }
    struct mCheatDevice* device = core->cheatDevice(core);
    if (!device) {
        return -1;
    }
    struct VFile* vf = VFileFromConstMemory(text, len);
    if (!vf) {
        return -1;
    }
    bool ok = mCheatParseFile(device, vf);
    vf->close(vf);
    // Hooks are in already (mCheatAddSet), holding the opcode they replaced; ROM
    // patches only go in at the set's first refresh, so the ROM still has the
    // original under them now.
    size_t n = mCheatSetsSize(&device->cheats);
    for (size_t i = 0; i < n; i++) {
        struct GBACheatSet* set = (struct GBACheatSet*) *mCheatSetsGetPointer(&device->cheats, i);
        if (set->hook && set->hook->reentries > 0) {
            pk_fix_add(set->hook->address, set->hook->mode == MODE_ARM ? 4 : 2, set->hook->patchedOpcode);
        }
        for (size_t p = 0; p < mCheatPatchListSize(&set->d.romPatches); p++) {
            struct mCheatPatch* patch = mCheatPatchListGetPointer(&set->d.romPatches, p);
            if (patch->address < BASE_CART0 || patch->address >= BASE_CART_SRAM || pk_fix_known(patch->address)) {
                continue;
            }
            uint32_t orig = 0;
            for (int b = 0; b < patch->width; b++) {
                uint8_t* rom = pk_rom_byte(core, patch->address + b);
                orig |= (uint32_t) (rom ? *rom : 0xFF) << (8 * b);
            }
            pk_fix_add(patch->address, patch->width, orig);
        }
    }
    return ok ? (int) n : -1;
}

// Puts the file's own bytes back into `dst`, a bus read of [addr, addr + len)
// from the cheated core, wherever a cheat patched the ROM.
void pk_cheats_overlay(uint32_t addr, uint8_t* dst, int len) {
    for (int i = 0; i < sFixCount; i++) {
        const struct pk_fix* f = &sFixes[i];
        for (int b = 0; b < f->width; b++) {
            uint32_t a = f->addr + (uint32_t) b;
            // Mirrors of the cart (wait states 1 and 2) read the same ROM.
            for (uint32_t base = BASE_CART0; base < BASE_CART_SRAM; base += SIZE_CART0) {
                uint32_t m = base | (a & (SIZE_CART0 - 1));
                if (m >= addr && m - addr < (uint32_t) len) {
                    dst[m - addr] = f->orig[b];
                }
            }
        }
    }
}

// Swaps the patched ROM bytes with the originals; twice is a no-op. For hashing
// the ROM the file holds (RetroAchievements) while cheats are in, on the emu
// thread so no frame runs in between.
void pk_cheats_swap_rom(struct mCore* core) {
    if (!pk_cheats_is_gba(core)) {
        return;
    }
    for (int i = 0; i < sFixCount; i++) {
        struct pk_fix* f = &sFixes[i];
        for (int b = 0; b < f->width; b++) {
            uint8_t* rom = pk_rom_byte(core, f->addr + b);
            if (rom) {
                uint8_t live = *rom;
                *rom = f->orig[b];
                f->orig[b] = live;
            }
        }
    }
}

int pk_cheats_rom_patched(void) {
    return sFixCount > 0;
}

// Checks one cheat's code lines ('\n'-separated) on a scratch cheat device - no
// core needed, so the top-screen Settings can check a code with no game running.
// `directive` (a .cheats file's "GSAv1", "PARv3 raw", ...; may be empty) goes in
// first, as mCheatParseFile would.
// Returns a malloc'd "<directive>\n<one '1' or '0' per line>": the directive is
// what mGBA detected the code as ("GSAv1", "PARv3", ... or empty for
// CodeBreaker / VBA codes, which don't need one), stored with the cheat so it
// loads the same way every time; a '0' line is one mGBA can't read.
char* pk_cheats_check(const char* code, int type, const char* directive) {
    struct mCheatDevice* device = GBACheatDeviceCreate();
    if (!device) {
        return NULL;
    }
    struct mCheatSet* set = device->createSet(device, NULL);
    size_t codeLen = code ? strlen(code) : 0;
    char* out = malloc(codeLen + 64);
    char* lines = malloc(codeLen + 1);
    if (!set || !out || !lines) {
        free(out);
        free(lines);
        if (set) {
            mCheatSetDeinit(set);
        }
        mCheatDeviceDestroy(device);
        return NULL;
    }
    if (directive && directive[0]) {
        struct StringList given;
        StringListInit(&given, 1);
        *StringListAppend(&given) = strdup(directive);
        set->parseDirectives(set, &given);
        free(*StringListGetPointer(&given, 0));
        StringListDeinit(&given);
    }
    char* results = lines;
    const char* cur = code ? code : "";
    char line[128];
    while (*cur) {
        const char* nl = strchr(cur, '\n');
        size_t n = nl ? (size_t) (nl - cur) : strlen(cur);
        size_t copy = n < sizeof(line) - 1 ? n : sizeof(line) - 1;
        memcpy(line, cur, copy);
        line[copy] = '\0';
        *results++ = (n < sizeof(line) && mCheatAddLine(set, line, type)) ? '1' : '0';
        cur += n;
        if (*cur == '\n') {
            cur++;
        }
    }
    *results = '\0';

    struct StringList directives;
    StringListInit(&directives, 2);
    set->dumpDirectives(set, &directives);
    const char* detected = StringListSize(&directives) ? *StringListGetPointer(&directives, 0) : "";
    snprintf(out, codeLen + 64, "%s\n%s", detected, lines);
    for (size_t d = 0; d < StringListSize(&directives); d++) {
        free(*StringListGetPointer(&directives, d));
    }
    StringListDeinit(&directives);
    free(lines);
    mCheatSetDeinit(set);
    mCheatDeviceDestroy(device);
    return out;
}

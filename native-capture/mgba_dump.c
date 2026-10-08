// mgba_dump — headless libmgba memory-capture tool, no Android/JNI/adb
// involved at all. Loads a ROM (+ optional save), runs a scripted boot
// sequence read from stdin, and dumps EWRAM+IWRAM to the exact same
// ewram.bin/iwram.bin format PokeDaisyActivity's dumpFixtureIfPending()
// produces on-device — so it's a drop-in replacement for
// scripts/capture_fixture.sh's data source, consumable by the SAME JVM
// FixtureMemoryReader tests with zero changes there.
//
// Mirrors app/src/main/cpp/pokedaisy_jni.c's proven
// libmgba API usage (core creation, key bits, bus reads) — see that file's
// comments for why each call is there. The one thing this tool gets for
// free that the Android/JNI path had to work hard for: since this process
// alone drives every frame, there is no other thread that could ever catch
// a struct mid-rewrite — no torn-read risk exists here by construction
// (contrast dumpFixtureIfPending()'s isStructSnapshotConsistent() check,
// needed there specifically because the emu thread and Kotlin's periodic
// sampling are two different execution contexts).
//
// stdin commands, one per line:
//   wait N            - advance N frames with no keys held
//   press KEYS        - press KEYS for one frame then release (comma-separated:
//                        A,B,SELECT,START,UP,DOWN,LEFT,RIGHT,L,R)
//   hold KEYS N        - hold KEYS for N frames then release
//   dump DIR           - write DIR/ewram.bin and DIR/iwram.bin (DIR must exist);
//                        a GB/GBC ROM writes DIR/wram.bin (C000-DFFF) + hram.bin (FF80-FFFE)
//   vdump DIR          - write DIR/{vram,pal,oam,io}.bin (video memory, palette
//                        RAM, OAM and the first 0x400 I/O registers) - what the
//                        PPU is drawing from, for pulling a screen's graphics
//   poke8/poke16/poke32 ADDR VAL - write RAM (hex or decimal), e.g. to put a
//                        party mon into a state worth screenshotting
//   shot FILE          - write the current frame to FILE as a binary PPM (P6) -
//                        no PNG dependency; `python3 -c 'from PIL import Image;
//                        Image.open("x.ppm").save("x.png")'` converts it
//   cheat TYPE CODE    - add a cheat (TYPE auto/cb/gs/ar, CODE's lines joined by
//                        '+') and load the list into the core the way the app
//                        does (app/src/main/cpp/pk_cheats.c, compiled in below)
//   cheatclear         - take every cheat out again
//   gbpark SPIN        - GB: park the main loop on SPIN (a `jr @`) once interrupts are on
//                        (the app's pkRenderGbPark)
//   gbcall FN A C RET  - GB: call FN with A / C until it returns to RET (pkRenderGbCall)
//   gbloop N           - GB: run N frames and report where Gen 1's music channel state
//                        (Gen1LoopWatch: Yellow's addresses) first repeats
//   romcheck ROMFILE   - compare the ROM as the companion reads it (bus +
//                        pk_cheats_overlay) and as RA hashes it (swapped) with
//                        the file
//
// Usage: mgba_dump <rom.gba> [save.sav] < commands.txt

#include <fcntl.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <stdarg.h>

#include <mgba/core/core.h>
#include <mgba/core/config.h>
#include <mgba/core/blip_buf.h>
#include <mgba/core/log.h>
#include <mgba-util/vfs.h>
#include <mgba/internal/arm/arm.h>
#include <mgba/internal/arm/isa-inlines.h>
#include <mgba/internal/gb/gb.h>
#include <mgba/internal/gb/io.h>
#include <mgba/internal/sm83/sm83.h>

// The app's own cheat code, so `cheat` tests exactly what ships - when the
// repo is there to compile it from (the capture scripts mount native-capture/
// alone; mount the repo root and build from native-capture/ to get it).
#if defined(__has_include)
#if __has_include("../app/src/main/cpp/pk_cheats.c")
#include "../app/src/main/cpp/pk_cheats.c"
#define HAVE_PK_CHEATS 1
#endif
#endif

// GBA key bit positions (GBA_KEY_* in mgba/gba/input.h) — kept local, same
// as pokedaisy_jni.c, so this file doesn't depend on the GBA-specific
// header. Matches this project's MgbaCore.Key (app/.../MgbaCore.kt).
enum {
    KEY_A = 1 << 0, KEY_B = 1 << 1, KEY_SELECT = 1 << 2, KEY_START = 1 << 3,
    KEY_RIGHT = 1 << 4, KEY_LEFT = 1 << 5, KEY_UP = 1 << 6, KEY_DOWN = 1 << 7,
    KEY_R = 1 << 8, KEY_L = 1 << 9,
};

#define SAMPLE_RATE 48000

static struct mCore* core;
static color_t* video;
static unsigned videoW, videoH;

// mGBA's default logger is noisy (every unmapped I/O register write, DMA,
// etc, straight to stdout/stderr) - fine for interactive debugging, useless
// noise for a scripted capture tool. A no-op logger silences it; pass
// --verbose to keep the default logger instead, for debugging a boot script.
static void noopLog(struct mLogger* log, int category, enum mLogLevel level, const char* format, va_list args) {
    (void) log; (void) category; (void) level; (void) format; (void) args;
}
static struct mLogger silentLogger = { .log = noopLog, .filter = NULL };

static int keyBitFor(const char* name) {
    if (!strcmp(name, "A")) return KEY_A;
    if (!strcmp(name, "B")) return KEY_B;
    if (!strcmp(name, "SELECT")) return KEY_SELECT;
    if (!strcmp(name, "START")) return KEY_START;
    if (!strcmp(name, "RIGHT")) return KEY_RIGHT;
    if (!strcmp(name, "LEFT")) return KEY_LEFT;
    if (!strcmp(name, "UP")) return KEY_UP;
    if (!strcmp(name, "DOWN")) return KEY_DOWN;
    if (!strcmp(name, "R")) return KEY_R;
    if (!strcmp(name, "L")) return KEY_L;
    fprintf(stderr, "mgba_dump: unknown key %s\n", name);
    return 0;
}

static uint32_t parseKeys(char* s) {
    uint32_t mask = 0;
    char* tok = strtok(s, ",");
    while (tok) {
        mask |= keyBitFor(tok);
        tok = strtok(NULL, ",");
    }
    return mask;
}

static void dumpRegion(uint32_t addr, int len, const char* path) {
    FILE* f = fopen(path, "wb");
    if (!f) {
        fprintf(stderr, "mgba_dump: cannot open %s for writing\n", path);
        return;
    }
    uint8_t buf[4096];
    int off = 0;
    while (off < len) {
        int chunk = len - off;
        if (chunk > (int) sizeof(buf)) chunk = sizeof(buf);
        for (int i = 0; i < chunk; i++) {
            buf[i] = (uint8_t) core->busRead8(core, addr + off + i);
        }
        fwrite(buf, 1, chunk, f);
        off += chunk;
    }
    fclose(f);
}

// Built without mGBA's PNG helpers on purpose (they pull libpng into the
// link); color_t is 32-bit XBGR8888 in the Debian libmgba build.
static void cmdShot(const char* path) {
    FILE* f = fopen(path, "wb");
    if (!f) {
        fprintf(stderr, "mgba_dump: cannot open %s for writing\n", path);
        return;
    }
    // The buffer is sized for the largest frame (a GB core: the SGB border's 256x224);
    // the frame itself (160x144 without a border) sits top-left at stride videoW
    // (this libmgba predates currentVideoSize).
    unsigned w = videoW, h = videoH;
    if (core->platform(core) == mPLATFORM_GB) { w = 160; h = 144; }
    fprintf(f, "P6\n%u %u\n255\n", w, h);
    for (unsigned i = 0; i < w * h; i++) {
        uint32_t c = (uint32_t) video[(i / w) * videoW + i % w];
        uint8_t rgb[3] = { (uint8_t) c, (uint8_t) (c >> 8), (uint8_t) (c >> 16) };
        fwrite(rgb, 1, 3, f);
    }
    fclose(f);
    fprintf(stderr, "mgba_dump: shot %s (frame %u)\n", path, core->frameCounter(core));
}

// Runs `frames` frames and writes what the APU played meanwhile as a 16-bit
// stereo WAV (what the app's render core records, e.g. for the click sound).
static void cmdWav(const char* path, int frames) {
    struct blip_t* l = core->getAudioChannel(core, 0);
    struct blip_t* r = core->getAudioChannel(core, 1);
    blip_clear(l);
    blip_clear(r);
    size_t cap = (size_t) frames * (SAMPLE_RATE / 50) * 2, pos = 0;
    int16_t* pcm = calloc(cap, sizeof(int16_t));
    for (int i = 0; i < frames; i++) {
        core->runFrame(core);
        int n = blip_samples_avail(l);
        if (pos + (size_t) n * 2 > cap) n = (int) ((cap - pos) / 2);
        blip_read_samples(l, pcm + pos, n, true);
        blip_read_samples(r, pcm + pos + 1, n, true);
        pos += (size_t) n * 2;
    }
    FILE* f = fopen(path, "wb");
    if (!f) {
        fprintf(stderr, "mgba_dump: cannot open %s for writing\n", path);
        free(pcm);
        return;
    }
    uint32_t data = (uint32_t) (pos * 2), riff = 36 + data, fmt = 16, rate = SAMPLE_RATE, bps = SAMPLE_RATE * 4;
    uint16_t pcmFmt = 1, ch = 2, align = 4, bits = 16;
    fwrite("RIFF", 1, 4, f); fwrite(&riff, 4, 1, f); fwrite("WAVEfmt ", 1, 8, f);
    fwrite(&fmt, 4, 1, f); fwrite(&pcmFmt, 2, 1, f); fwrite(&ch, 2, 1, f); fwrite(&rate, 4, 1, f);
    fwrite(&bps, 4, 1, f); fwrite(&align, 2, 1, f); fwrite(&bits, 2, 1, f);
    fwrite("data", 1, 4, f); fwrite(&data, 4, 1, f); fwrite(pcm, 2, pos, f);
    fclose(f);
    free(pcm);
    fprintf(stderr, "mgba_dump: wav %s (%zu frames of audio)\n", path, pos / 2);
}

static void cmdDump(const char* outDir) {
    char path[1024];
    if (core->platform(core) == mPLATFORM_GB) {
        // Game Boy / Color: WRAM C000-DFFF (the bank SVBK has in D000) and HRAM FF80-FFFE.
        snprintf(path, sizeof(path), "%s/wram.bin", outDir);
        dumpRegion(0xC000, 0x2000, path);
        snprintf(path, sizeof(path), "%s/hram.bin", outDir);
        dumpRegion(0xFF80, 0x7F, path);
        fprintf(stderr, "mgba_dump: dumped wram+hram to %s (frame %u)\n", outDir, core->frameCounter(core));
        return;
    }
    snprintf(path, sizeof(path), "%s/ewram.bin", outDir);
    dumpRegion(0x02000000, 0x40000, path);
    snprintf(path, sizeof(path), "%s/iwram.bin", outDir);
    dumpRegion(0x03000000, 0x8000, path);
    fprintf(stderr, "mgba_dump: dumped ewram+iwram to %s (frame %u)\n", outDir, core->frameCounter(core));
}

static void cmdVdump(const char* outDir) {
    char path[1024];
    snprintf(path, sizeof(path), "%s/vram.bin", outDir);
    dumpRegion(0x06000000, 0x18000, path);
    snprintf(path, sizeof(path), "%s/pal.bin", outDir);
    dumpRegion(0x05000000, 0x400, path);
    snprintf(path, sizeof(path), "%s/oam.bin", outDir);
    dumpRegion(0x07000000, 0x400, path);
    snprintf(path, sizeof(path), "%s/io.bin", outDir);
    dumpRegion(0x04000000, 0x400, path);
    fprintf(stderr, "mgba_dump: dumped vram+pal+oam+io to %s (frame %u)\n", outDir, core->frameCounter(core));
}


// Calls Thumb function `fn` with r0 = `arg0` and returns once it has, leaving
// the CPU exactly as it was (registers, pipeline, halt): the frame can end
// anywhere - mid-way through the game's own code, whose live registers a bare
// PC hijack clobbered (Unbound reset its sound). Runs instruction by
// instruction with IRQs masked until the call returns to SENTINEL, an address
// in the ROM header that is never executed. Mirrors pokedaisy_jni.c's pk_call.
#define PK_CALL_SENTINEL 0x080000C0u

// --- Game Boy: the app's pkRenderGbPark / pkRenderGbCall, for Gen 1's music ---
static bool gb_park(struct mCore* core, uint16_t spin) {
    struct GB* gb = (struct GB*) core->board;
    struct SM83Core* cpu = (struct SM83Core*) core->cpu;
    for (int i = 0; i < 5000000; i++) {
        core->step(core);
        if (gb->memory.ime && (gb->memory.ie & 1) && (gb->memory.io[GB_REG_LCDC] & 0x80) && !cpu->halted && !cpu->irqPending) {
            cpu->pc = spin;
            cpu->memory.setActiveRegion(cpu, cpu->pc);
            return true;
        }
    }
    return false;
}

static bool gb_call(struct mCore* core, uint16_t fn, uint8_t a, uint8_t c, uint16_t ret) {
    struct GB* gb = (struct GB*) core->board;
    struct SM83Core* cpu = (struct SM83Core*) core->cpu;
    bool ime = gb->memory.ime, pending = cpu->irqPending;
    gb->memory.ime = false;
    cpu->irqPending = false;
    cpu->sp -= 2;
    core->busWrite8(core, cpu->sp, ret & 0xFF);
    core->busWrite8(core, (uint16_t) (cpu->sp + 1), ret >> 8);
    cpu->a = a;
    cpu->c = c;
    cpu->halted = false;
    cpu->pc = fn;
    cpu->memory.setActiveRegion(cpu, cpu->pc);
    bool ok = false;
    for (int i = 0; i < 2000000 && !ok; i++) {
        core->step(core);
        ok = cpu->pc == ret;
    }
    gb->memory.ime = ime;
    cpu->irqPending = pending;
    return ok;
}

// Gen1LoopWatch's state: Yellow's wChannelCommandPointers, ReturnAddresses (CHAN1-4),
// SoundIDs, NoteDelayCounters, LoopCounters.
static void gb_music_state(struct mCore* core, uint8_t* out) {
    static const struct { uint16_t a; int n; } R[] = {{0xC006, 8}, {0xC016, 8}, {0xC026, 4}, {0xC0B6, 4}, {0xC0BE, 4}};
    int k = 0;
    for (int r = 0; r < 5; r++) for (int i = 0; i < R[r].n; i++) out[k++] = core->busRead8(core, R[r].a + i);
}

static void gb_loop(struct mCore* core, int frames) {
    enum { W = 28 };
    uint8_t* states = malloc((size_t) frames * W);
    int still = 0;
    for (int f = 0; f < frames; f++) {
        core->runFrame(core);
        uint8_t* st = states + (size_t) f * W;
        gb_music_state(core, st);
        if (f > 0 && !memcmp(st, st - W, W)) {
            if (++still >= 90) { printf("gbloop: still from frame %d - ended\n", f - 90); free(states); return; }
        } else still = 0;
        for (int g = 0; g <= f - 120; g++) {
            if (!memcmp(states + (size_t) g * W, st, W)) {
                printf("gbloop: state after frame %d repeats after frame %d: loop [%d, %d), %.2f s, intro %.2f s\n",
                       g, f, g + 1, f + 1, (f - g) / 59.7275, (g + 1) / 59.7275);
                free(states);
                return;
            }
        }
    }
    printf("gbloop: no repeat in %d frames\n", frames);
    free(states);
}

static bool pk_call(struct mCore* core, uint32_t fn, uint32_t arg0, uint32_t arg1) {
    struct ARMCore* cpu = (struct ARMCore*) core->cpu;
    struct ARMRegisterFile saved = cpu->regs;
    uint32_t prefetch0 = cpu->prefetch[0], prefetch1 = cpu->prefetch[1];
    enum ExecutionMode exec = cpu->executionMode;
    int halted = cpu->halted;
    cpu->halted = 0;
    cpu->cpsr.i = 1;
    cpu->gprs[0] = (int32_t) arg0;
    cpu->gprs[1] = (int32_t) arg1;
    cpu->gprs[ARM_LR] = (int32_t) (PK_CALL_SENTINEL | 1);
    cpu->gprs[ARM_PC] = (int32_t) (fn & ~1u);
    _ARMSetMode(cpu, MODE_THUMB);
    ThumbWritePC(cpu);
    bool ok = false;
    for (int i = 0; i < 2000000; i++) {
        if (cpu->executionMode == MODE_THUMB && (uint32_t) cpu->gprs[ARM_PC] - WORD_SIZE_THUMB == PK_CALL_SENTINEL) {
            ok = true;
            break;
        }
        core->step(core);
    }
    cpu->regs = saved;
    cpu->executionMode = exec == MODE_ARM ? MODE_THUMB : MODE_ARM; // force _ARMSetMode to apply
    _ARMSetMode(cpu, exec);
    cpu->prefetch[0] = prefetch0;
    cpu->prefetch[1] = prefetch1;
    cpu->halted = halted;
    return ok;
}


// Parks the game's main loop on `spin` (a Thumb `b .`) so it can't change the
// music any more, while the VBlank interrupt keeps the sound engine playing.
// Steps first until the CPU is in the main context (System/User mode, IRQs
// on) - parking inside an interrupt handler would leave IRQs off for good.
// Mirrors pokedaisy_jni.c's pk_park.
static bool pk_park(struct mCore* core, uint32_t spin) {
    struct ARMCore* cpu = (struct ARMCore*) core->cpu;
    for (int i = 0; i < 5000000; i++) {
        // ...and the VBlank IRQ fully on (IME, IE bit 0, DISPSTAT bit 3): games
        // switch it off around loads, and a parked loop never turns it back on.
        if ((cpu->privilegeMode == MODE_SYSTEM || cpu->privilegeMode == MODE_USER) && !cpu->cpsr.i && !cpu->halted
            && (core->busRead16(core, 0x04000208) & 1) && (core->busRead16(core, 0x04000200) & 1)
            && (core->busRead16(core, 0x04000004) & 8)) {
            cpu->gprs[ARM_PC] = (int32_t) (spin & ~1u);
            _ARMSetMode(cpu, MODE_THUMB);
            ThumbWritePC(cpu);
            return true;
        }
        core->step(core);
    }
    return false;
}

#ifdef HAVE_PK_CHEATS
// `cheat`: the cheats added so far as one mGBA .cheats text, rebuilt into the
// core on every change - what CheatStore + pkCheatsApply do in the app.
static char cheatText[16384];

static int cheatTypeFor(const char* name) {
    if (!strcmp(name, "cb")) return PK_CHEAT_CODEBREAKER;
    if (!strcmp(name, "gs")) return PK_CHEAT_GAMESHARK;
    if (!strcmp(name, "ar")) return PK_CHEAT_ACTION_REPLAY;
    return PK_CHEAT_AUTO;
}

static void cmdCheat(const char* type, const char* code) {
    char lines[1024];
    snprintf(lines, sizeof(lines), "%s", code);
    for (char* c = lines; *c; c++) {
        if (*c == '+') *c = '\n';
    }
    char* check = pk_cheats_check(lines, cheatTypeFor(type), "");
    if (!check) {
        printf("cheat: check failed\n");
        return;
    }
    char* nl = strchr(check, '\n');
    *nl = '\0';
    printf("cheat %s: directive '%s', lines ok %s\n", type, check, nl + 1);
    size_t len = strlen(cheatText);
    len += snprintf(cheatText + len, sizeof(cheatText) - len, "!reset\n");
    if (check[0]) {
        len += snprintf(cheatText + len, sizeof(cheatText) - len, "!%s\n", check);
    }
    snprintf(cheatText + len, sizeof(cheatText) - len, "# %s\n%s\n", code, lines);
    free(check);
    printf("cheat: %d set(s) loaded, rom patched: %s\n",
           pk_cheats_apply(core, cheatText, strlen(cheatText)), pk_cheats_rom_patched() ? "yes" : "no");
}

static int romDiffs(const uint8_t* file, size_t size, bool overlay) {
    int diffs = 0;
    uint8_t buf[4096];
    for (size_t off = 0; off < size; off += sizeof(buf)) {
        int n = size - off < sizeof(buf) ? (int) (size - off) : (int) sizeof(buf);
        for (int i = 0; i < n; i++) buf[i] = (uint8_t) core->busRead8(core, 0x08000000u + off + i);
        if (overlay) pk_cheats_overlay(0x08000000u + off, buf, n);
        for (int i = 0; i < n; i++) {
            // The RTC's GPIO registers read back live (Poller's GPIO hole).
            uint32_t a = off + i;
            if (a >= 0xC4 && a < 0xCA) continue;
            if (buf[i] != file[a]) {
                if (diffs < 4) printf("  %08x: bus %02x file %02x\n", 0x08000000u + a, buf[i], file[a]);
                diffs++;
            }
        }
    }
    return diffs;
}

static void cmdRomCheck(const char* path) {
    FILE* f = fopen(path, "rb");
    if (!f) {
        printf("romcheck: cannot open %s\n", path);
        return;
    }
    fseek(f, 0, SEEK_END);
    size_t size = (size_t) ftell(f);
    fseek(f, 0, SEEK_SET);
    uint8_t* file = malloc(size);
    if (fread(file, 1, size, f) != size) size = 0;
    fclose(f);
    printf("romcheck raw bus: %d byte(s) differ\n", romDiffs(file, size, false));
    printf("romcheck overlay: %d byte(s) differ\n", romDiffs(file, size, true));
    pk_cheats_swap_rom(core);
    printf("romcheck swapped: %d byte(s) differ\n", romDiffs(file, size, false));
    pk_cheats_swap_rom(core);
    printf("romcheck swapped back: %d byte(s) differ\n", romDiffs(file, size, false));
    free(file);
}
#endif

int main(int argc, char** argv) {
    bool verbose = false;
    const char* args[8];
    int nargs = 0;
    for (int i = 1; i < argc; i++) {
        if (!strcmp(argv[i], "--verbose")) {
            verbose = true;
        } else if (nargs < (int) (sizeof(args) / sizeof(args[0]))) {
            args[nargs++] = argv[i];
        }
    }
    if (nargs < 1) {
        fprintf(stderr, "usage: %s [--verbose] <rom.gba> [save.sav] < commands.txt\n", argv[0]);
        return 2;
    }
    if (!verbose) {
        mLogSetDefaultLogger(&silentLogger);
    }
    const char* romPath = args[0];
    const char* savePath = nargs > 1 ? args[1] : NULL;

    struct VFile* rom = VFileOpen(romPath, O_RDONLY);
    if (!rom) {
        fprintf(stderr, "mgba_dump: cannot open ROM %s\n", romPath);
        return 1;
    }
    core = mCoreFindVF(rom);
    if (!core) {
        fprintf(stderr, "mgba_dump: unrecognized ROM %s\n", romPath);
        return 1;
    }
    if (!core->init(core)) {
        fprintf(stderr, "mgba_dump: core->init failed\n");
        return 1;
    }
    mCoreInitConfig(core, NULL);
    if (core->platform(core) == mPLATFORM_GB) {
        // As the app (pokedaisy_jni.c's pk_gb_config): no SGB border, the 160x144 screen.
        mCoreConfigSetIntValue(&core->config, "sgb.borders", 0);
        core->reloadConfigOption(core, "sgb.borders", &core->config);
    }

    unsigned w, h;
    core->desiredVideoDimensions(core, &w, &h);
    videoW = w;
    videoH = h;
    video = calloc((size_t) w * h, sizeof(color_t));
    core->setVideoBuffer(core, video, w);
    core->setAudioBufferSize(core, 2048);
    blip_set_rates(core->getAudioChannel(core, 0), core->frequency(core), SAMPLE_RATE);
    blip_set_rates(core->getAudioChannel(core, 1), core->frequency(core), SAMPLE_RATE);

    if (!core->loadROM(core, rom)) {
        fprintf(stderr, "mgba_dump: core->loadROM failed\n");
        return 1;
    }

    struct VFile* saveVf = NULL;
    if (savePath) {
        // O_RDWR, not O_RDONLY: the game can write to its own flash/SRAM
        // during normal play (autosave, etc), and mGBA's flash-write path
        // doesn't tolerate a read-only backing VFile gracefully (segfaults on
        // the second in-game write in testing here) - always pass a
        // disposable COPY of a save, never the user's real one, since this
        // will genuinely mutate it.
        saveVf = VFileOpen(savePath, O_RDWR);
        if (saveVf) {
            core->loadSave(core, saveVf);
        } else {
            fprintf(stderr, "mgba_dump: warning: cannot open save %s, continuing without\n", savePath);
        }
    }

    core->reset(core);
    fprintf(stderr, "mgba_dump: core ready (%ux%u, %d Hz)\n", w, h, core->frequency(core));

    char line[512];
    while (fgets(line, sizeof(line), stdin)) {
        char* nl = strchr(line, '\n');
        if (nl) *nl = 0;
        if (line[0] == '#' || line[0] == '\0') continue;

        char cmd[32] = {0};
        char arg1[256] = {0};
        int n = 0;
        if (sscanf(line, "%31s", cmd) != 1) continue;

        if (!strcmp(cmd, "wait")) {
            sscanf(line, "%*s %d", &n);
            for (int i = 0; i < n; i++) core->runFrame(core);
        } else if (!strcmp(cmd, "press")) {
            sscanf(line, "%*s %255s", arg1);
            uint32_t mask = parseKeys(arg1);
            core->setKeys(core, mask);
            core->runFrame(core);
            core->setKeys(core, 0);
            core->runFrame(core);
        } else if (!strcmp(cmd, "hold")) {
            sscanf(line, "%*s %255s %d", arg1, &n);
            uint32_t mask = parseKeys(arg1);
            core->setKeys(core, mask);
            for (int i = 0; i < n; i++) core->runFrame(core);
            core->setKeys(core, 0);
            core->runFrame(core);
        } else if (!strcmp(cmd, "dump")) {
            sscanf(line, "%*s %255s", arg1);
            cmdDump(arg1);
        } else if (!strcmp(cmd, "vdump")) {
            sscanf(line, "%*s %255s", arg1);
            cmdVdump(arg1);
        } else if (!strcmp(cmd, "poke8p") || !strcmp(cmd, "peek8p")) {
            // poke8p PTR OFF VAL / peek8p PTR OFF: the byte at *(u32 *)PTR + OFF -
            // for save-block fields, since Emerald moves its save blocks on every load.
            char p[64] = {0}, o[64] = {0}, v[64] = {0};
            sscanf(line, "%*s %63s %63s %63s", p, o, v);
            uint32_t addr = core->busRead32(core, (uint32_t) strtoul(p, NULL, 0)) + (uint32_t) strtoul(o, NULL, 0);
            if (cmd[1] == 'o') core->busWrite8(core, addr, (uint8_t) strtoul(v, NULL, 0));
            else printf("%08x: %02x\n", addr, core->busRead8(core, addr));
        } else if (!strncmp(cmd, "poke", 4)) {
            char a[64] = {0}, v[64] = {0};
            sscanf(line, "%*s %63s %63s", a, v);
            uint32_t addr = (uint32_t) strtoul(a, NULL, 0), val = (uint32_t) strtoul(v, NULL, 0);
            if (!strcmp(cmd, "poke8")) core->busWrite8(core, addr, (uint8_t) val);
            else if (!strcmp(cmd, "poke16")) core->busWrite16(core, addr, (uint16_t) val);
            else if (!strcmp(cmd, "poke32")) core->busWrite32(core, addr, val);
            else fprintf(stderr, "mgba_dump: unknown command '%s'\n", cmd);
        } else if (!strcmp(cmd, "call")) {
            // call ADDR ARG0 [ARG1]: run Thumb function ADDR with r0 = ARG0
            // (and r1 = ARG1) to its return, then resume exactly where the CPU
            // was - the app's pkRenderForceSong (e.g. `call 0x081dd164 297` =
            // m4aSongNumStart), or a game's VarSet(id, value).
            char a[64] = {0}, v[64] = {0}, w[64] = {0};
            sscanf(line, "%*s %63s %63s %63s", a, v, w);
            bool ok = pk_call(core, (uint32_t) strtoul(a, NULL, 0), (uint32_t) strtoul(v, NULL, 0), (uint32_t) strtoul(w, NULL, 0));
            printf("call %s(%s): %s\n", a, v, ok ? "returned" : "DID NOT RETURN");
        } else if (!strcmp(cmd, "park")) {
            char a[64] = {0};
            sscanf(line, "%*s %63s", a);
            printf("park %s: %s\n", a, pk_park(core, (uint32_t) strtoul(a, NULL, 0)) ? "parked" : "FAILED");
        } else if (!strcmp(cmd, "gbpark")) {
            char a[64] = {0};
            sscanf(line, "%*s %63s", a);
            printf("gbpark %s: %s\n", a, gb_park(core, (uint16_t) strtoul(a, NULL, 0)) ? "parked" : "FAILED");
        } else if (!strcmp(cmd, "gbcall")) {
            char f[64] = {0}, a[64] = {0}, c[64] = {0}, r[64] = {0};
            sscanf(line, "%*s %63s %63s %63s %63s", f, a, c, r);
            bool ok = gb_call(core, (uint16_t) strtoul(f, NULL, 0), (uint8_t) strtoul(a, NULL, 0), (uint8_t) strtoul(c, NULL, 0), (uint16_t) strtoul(r, NULL, 0));
            printf("gbcall %s(a=%s, c=%s): %s\n", f, a, c, ok ? "returned" : "DID NOT RETURN");
        } else if (!strcmp(cmd, "gbloop")) {
            sscanf(line, "%*s %d", &n);
            gb_loop(core, n);
        } else if (!strcmp(cmd, "peek32")) {
            char a[64] = {0};
            sscanf(line, "%*s %63s", a);
            uint32_t addr = (uint32_t) strtoul(a, NULL, 0);
            printf("%08x: %08x\n", addr, core->busRead32(core, addr));
        } else if (!strcmp(cmd, "bgm")) {
            // The m4a BGM player (FfMusicKey): tail of SoundInfo's player chain.
            uint32_t info = core->busRead32(core, 0x03007FF0), p = core->busRead32(core, info + 0x24), bgm = 0;
            for (int i = 0; p && i < 12; i++) { bgm = p; p = core->busRead32(core, p + 0x3C); }
            printf("bgm soundInfo=%08x player=%08x status=%08x header=%08x ident=%08x\n", info, bgm,
                   bgm ? core->busRead32(core, bgm + 4) : 0, bgm ? core->busRead32(core, bgm) : 0,
                   bgm ? core->busRead32(core, bgm + 0x34) : 0);
        } else if (!strcmp(cmd, "wav")) {
            sscanf(line, "%*s %255s %d", arg1, &n);
            cmdWav(arg1, n);
#ifdef HAVE_PK_CHEATS
        } else if (!strcmp(cmd, "cheat")) {
            char t[16] = {0};
            sscanf(line, "%*s %15s %255[^\n]", t, arg1);
            cmdCheat(t, arg1);
        } else if (!strcmp(cmd, "cheatclear")) {
            cheatText[0] = '\0';
            pk_cheats_clear(core);
            printf("cheatclear: rom patched: %s\n", pk_cheats_rom_patched() ? "yes" : "no");
        } else if (!strcmp(cmd, "romcheck")) {
            sscanf(line, "%*s %255[^\n]", arg1);
            cmdRomCheck(arg1);
#endif
        } else if (!strcmp(cmd, "shot")) {
            sscanf(line, "%*s %255s", arg1);
            cmdShot(arg1);
        } else {
            fprintf(stderr, "mgba_dump: unknown command '%s'\n", cmd);
        }
        fflush(stderr);
    }

    core->deinit(core);
    free(video);
    return 0;
}

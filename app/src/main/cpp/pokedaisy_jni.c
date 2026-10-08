// PokeDaisy — JNI bridge over the mGBA core (libmgba, vendored at 0.10.5).
//
// Phase 0 scope: bring up a GBA core, run frames, expose the RGBA framebuffer
// and interleaved s16 stereo audio, take key input, and do raw save/load state.
// Everything here is called from a single Kotlin "emu thread", which also copies
// pkVideoBuffer()'s frames out for the GL thread (EmulatorView.publishFrame).

#include <jni.h>
#include <android/log.h>
#include <pthread.h>
#include <stdlib.h>
#include <string.h>

#include <mgba/core/core.h>
#include <mgba/core/config.h>
#include <mgba/core/blip_buf.h>
#include <mgba/core/serialize.h>
#include <mgba-util/vfs.h>
#include <mgba/internal/arm/arm.h>
#include <mgba/internal/arm/isa-inlines.h>
#include <mgba/internal/gb/gb.h>
#include <mgba/internal/gb/io.h>
#include <mgba/internal/sm83/sm83.h>

#include "pk_cheats.h"

#define LOG_TAG "pokedaisy/jni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// GBA hardware key bit positions (GBA_KEY_* in mgba/gba/input.h). Kept local so
// this file doesn't depend on the GBA-specific header.
enum {
    PK_KEY_A = 0, PK_KEY_B = 1, PK_KEY_SELECT = 2, PK_KEY_START = 3,
    PK_KEY_RIGHT = 4, PK_KEY_LEFT = 5, PK_KEY_UP = 6, PK_KEY_DOWN = 7,
    PK_KEY_R = 8, PK_KEY_L = 9
};

#define PK_SAMPLE_RATE 48000

// Game Boy / Color ROMs run on mGBA's GB core too (M_CORE_GB). Red/Blue/Yellow
// are SGB-enhanced, so the core would frame them in a 256x224 Super Game Boy
// border: turn borders off, which keeps every GB game at its 160x144 screen.
static void pk_gb_config(struct mCore* core) {
    if (core->platform(core) != mPLATFORM_GB) {
        return;
    }
    // Just this one option: mCoreLoadForeignConfig maps the whole config, and with
    // no "volume" key in it the GB core's master volume went to 0 (Yellow was silent).
    mCoreConfigSetIntValue(&core->config, "sgb.borders", 0);
    core->reloadConfigOption(core, "sgb.borders", &core->config);
}

static int pk_is_gba(const struct mCore* core) {
    return core && core->platform(core) == mPLATFORM_GBA;
}
#define PK_AUDIO_SCRATCH_SAMPLES 8192   // per-channel; * 2 shorts for interleave

static struct {
    struct mCore* core;
    color_t* video;
    unsigned vw, vh;
    struct VFile* saveVf;
    bool saveReadOnly;                  // nothing we load can reach the file
    int16_t audio[PK_AUDIO_SCRATCH_SAMPLES * 2];
    int audioAvail;                     // interleaved shorts ready in audio[]
} g;

// A second, fully independent core used only to prerecord FF background-
// music clips (see FfMusicRenderer.kt / EmulatorEngine's "FF music"
// section) — completely separate from `g` (the player's actual running
// game). Never loads a save file, never shown on screen, never touched by
// player input. Isolating it this way means even a mistake in the song-
// injection below (pkRenderForceSong) can only break this disposable core,
// never the player's real session.
static struct {
    struct mCore* core;
    unsigned vw, vh;                    // unused (no video output) but core->init needs a buffer set
    color_t* video;
    int16_t audio[PK_AUDIO_SCRATCH_SAMPLES * 2];
    int audioAvail;
} rg;

static void pkDrainAudio(void) {
    struct blip_t* left = g.core->getAudioChannel(g.core, 0);
    struct blip_t* right = g.core->getAudioChannel(g.core, 1);
    int avail = blip_samples_avail(left);
    if (avail > PK_AUDIO_SCRATCH_SAMPLES) {
        avail = PK_AUDIO_SCRATCH_SAMPLES;
    }
    if (avail > 0) {
        blip_read_samples(left, g.audio, avail, 1);
        blip_read_samples(right, g.audio + 1, avail, 1);
        g.audioAvail = avail * 2;
    } else {
        g.audioAvail = 0;
    }
}

// Guards g.core's lifetime, not its state: the emu thread owns frames, saves and
// teardown, but the companion's ROM readers (Pokédex, GUIDE, icons) call
// pkReadBytes from IO threads, and one in flight while the game closed read a
// core pkTeardown had just freed. Readers share it; init / teardown take it alone.
static pthread_rwlock_t pk_coreLock = PTHREAD_RWLOCK_INITIALIZER;

static void pkTeardown(void) {
    pthread_rwlock_wrlock(&pk_coreLock);
    if (g.core) {
        pk_cheats_clear(g.core);        // and forgets the ROM bytes it kept
        g.core->deinit(g.core);         // unloads ROM, flushes save VFile
        g.core = NULL;
    }
    if (g.saveVf) {
        g.saveVf->close(g.saveVf);
        g.saveVf = NULL;
    }
    g.saveReadOnly = false;
    free(g.video);
    g.video = NULL;
    g.vw = g.vh = 0;
    g.audioAvail = 0;
    pthread_rwlock_unlock(&pk_coreLock);
}

// The player's core, for pokedaisy_ra.c (RetroAchievements' memory reads and ROM hash).
struct mCore* pkMainCore(void) {
    return g.core;
}

static void pkRenderDrainAudio(void) {
    struct blip_t* left = rg.core->getAudioChannel(rg.core, 0);
    struct blip_t* right = rg.core->getAudioChannel(rg.core, 1);
    int avail = blip_samples_avail(left);
    if (avail > PK_AUDIO_SCRATCH_SAMPLES) {
        avail = PK_AUDIO_SCRATCH_SAMPLES;
    }
    if (avail > 0) {
        blip_read_samples(left, rg.audio, avail, 1);
        blip_read_samples(right, rg.audio + 1, avail, 1);
        rg.audioAvail = avail * 2;
    } else {
        rg.audioAvail = 0;
    }
}

static void pkRenderTeardown(void) {
    if (rg.core) {
        rg.core->deinit(rg.core);       // no save VFile ever attached — nothing to flush
        rg.core = NULL;
    }
    free(rg.video);
    rg.video = NULL;
    rg.vw = rg.vh = 0;
    rg.audioAvail = 0;
}

JNIEXPORT jboolean JNICALL
Java_com_pokedaisy_app_MgbaCore_pkInit(JNIEnv* env, jobject thiz,
                                                  jstring jRomPath, jstring jSavePath) {
    if (g.core) {
        pkTeardown();
    }

    const char* romPath = (*env)->GetStringUTFChars(env, jRomPath, NULL);
    struct VFile* rom = VFileOpen(romPath, O_RDONLY);
    (*env)->ReleaseStringUTFChars(env, jRomPath, romPath);
    if (!rom) {
        LOGE("cannot open ROM");
        return JNI_FALSE;
    }

    struct mCore* core = mCoreFindVF(rom);
    if (!core) {
        LOGE("mCoreFindVF: unrecognized ROM");
        rom->close(rom);
        return JNI_FALSE;
    }
    if (!core->init(core)) {
        LOGE("core->init failed");
        rom->close(rom);
        core->deinit(core);
        return JNI_FALSE;
    }
    mCoreInitConfig(core, NULL);
    pk_gb_config(core);

    unsigned w, h;
    core->desiredVideoDimensions(core, &w, &h);
    color_t* video = calloc((size_t) w * h, sizeof(color_t));
    core->setVideoBuffer(core, video, w);

    core->setAudioBufferSize(core, 2048);
    blip_set_rates(core->getAudioChannel(core, 0), core->frequency(core), PK_SAMPLE_RATE);
    blip_set_rates(core->getAudioChannel(core, 1), core->frequency(core), PK_SAMPLE_RATE);

    if (!core->loadROM(core, rom)) {
        LOGE("core->loadROM failed");
        rom->close(rom);
        core->deinit(core);
        free(video);
        return JNI_FALSE;
    }

    struct VFile* saveVf = NULL;
    g.saveReadOnly = false;
    if (jSavePath) {
        const char* savePath = (*env)->GetStringUTFChars(env, jSavePath, NULL);
        // O_CREAT so a fresh game starts with an empty SRAM file we own; fall
        // back to read-only if the file exists but isn't writable (e.g. an
        // adb-pushed import) so its save data still loads.
        saveVf = VFileOpen(savePath, O_RDWR | O_CREAT);
        if (!saveVf) {
            saveVf = VFileOpen(savePath, O_RDONLY);
            g.saveReadOnly = saveVf != NULL;
            if (saveVf) {
                LOGI("save opened read-only (new saves won't persist): %s", savePath);
            }
        }
        (*env)->ReleaseStringUTFChars(env, jSavePath, savePath);
        if (saveVf) {
            core->loadSave(core, saveVf);
        } else {
            LOGE("cannot open save path (continuing without persistent SRAM)");
        }
    }

    core->reset(core);

    pthread_rwlock_wrlock(&pk_coreLock);
    g.core = core;
    g.video = video;
    g.vw = w;
    g.vh = h;
    g.saveVf = saveVf;
    g.audioAvail = 0;
    pthread_rwlock_unlock(&pk_coreLock);

    LOGI("core up: %ux%u, freq %d Hz, sample rate %d", w, h, core->frequency(core), PK_SAMPLE_RATE);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_pokedaisy_app_MgbaCore_pkDeinit(JNIEnv* env, jobject thiz) {
    pkTeardown();
}

JNIEXPORT jobject JNICALL
Java_com_pokedaisy_app_MgbaCore_pkVideoBuffer(JNIEnv* env, jobject thiz) {
    if (!g.video) {
        return NULL;
    }
    return (*env)->NewDirectByteBuffer(env, g.video, (jlong) g.vw * g.vh * sizeof(color_t));
}

JNIEXPORT jint JNICALL
Java_com_pokedaisy_app_MgbaCore_pkVideoWidth(JNIEnv* env, jobject thiz) {
    return (jint) g.vw;
}

JNIEXPORT jint JNICALL
Java_com_pokedaisy_app_MgbaCore_pkVideoHeight(JNIEnv* env, jobject thiz) {
    return (jint) g.vh;
}

JNIEXPORT jint JNICALL
Java_com_pokedaisy_app_MgbaCore_pkSampleRate(JNIEnv* env, jobject thiz) {
    return PK_SAMPLE_RATE;
}

JNIEXPORT void JNICALL
Java_com_pokedaisy_app_MgbaCore_pkSetKeys(JNIEnv* env, jobject thiz, jint mask) {
    if (g.core) {
        g.core->setKeys(g.core, (uint32_t) mask);
    }
}

JNIEXPORT void JNICALL
Java_com_pokedaisy_app_MgbaCore_pkRunFrame(JNIEnv* env, jobject thiz) {
    if (!g.core) {
        return;
    }
    g.core->runFrame(g.core);
    pkDrainAudio();
}

// --- FF-music prerecording: a second, disposable core (see `rg` above) ------
// Same one-frame-then-drain contract as pkRunFrame/pkReadAudio, driven from
// Kotlin the same way (FfMusicRenderer), just on its own separate handle.

JNIEXPORT jboolean JNICALL
Java_com_pokedaisy_app_MgbaCore_pkRenderInit(JNIEnv* env, jobject thiz, jstring jRomPath) {
    if (rg.core) {
        pkRenderTeardown();
    }
    const char* romPath = (*env)->GetStringUTFChars(env, jRomPath, NULL);
    struct VFile* rom = VFileOpen(romPath, O_RDONLY);
    (*env)->ReleaseStringUTFChars(env, jRomPath, romPath);
    if (!rom) {
        LOGE("pkRenderInit: cannot open ROM");
        return JNI_FALSE;
    }

    struct mCore* core = mCoreFindVF(rom);
    if (!core) {
        LOGE("pkRenderInit: mCoreFindVF failed");
        rom->close(rom);
        return JNI_FALSE;
    }
    if (!core->init(core)) {
        LOGE("pkRenderInit: core->init failed");
        rom->close(rom);
        core->deinit(core);
        return JNI_FALSE;
    }
    mCoreInitConfig(core, NULL);
    pk_gb_config(core);

    unsigned w, h;
    core->desiredVideoDimensions(core, &w, &h);
    // core->init's contract expects a video buffer even though this core's
    // frames are never displayed or read back.
    color_t* video = calloc((size_t) w * h, sizeof(color_t));
    core->setVideoBuffer(core, video, w);

    core->setAudioBufferSize(core, 2048);
    blip_set_rates(core->getAudioChannel(core, 0), core->frequency(core), PK_SAMPLE_RATE);
    blip_set_rates(core->getAudioChannel(core, 1), core->frequency(core), PK_SAMPLE_RATE);

    if (!core->loadROM(core, rom)) {
        LOGE("pkRenderInit: core->loadROM failed");
        rom->close(rom);
        core->deinit(core);
        free(video);
        return JNI_FALSE;
    }

    // Deliberately no core->loadSave call — this core never reads or writes
    // the player's actual save file.
    core->reset(core);

    rg.core = core;
    rg.video = video;
    rg.vw = w;
    rg.vh = h;
    rg.audioAvail = 0;
    LOGI("render core up: %ux%u", w, h);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_pokedaisy_app_MgbaCore_pkRenderDeinit(JNIEnv* env, jobject thiz) {
    pkRenderTeardown();
}

JNIEXPORT void JNICALL
Java_com_pokedaisy_app_MgbaCore_pkRenderRunFrame(JNIEnv* env, jobject thiz) {
    if (!rg.core) {
        return;
    }
    rg.core->runFrame(rg.core);
    pkRenderDrainAudio();
}

JNIEXPORT jint JNICALL
Java_com_pokedaisy_app_MgbaCore_pkRenderSampleRate(JNIEnv* env, jobject thiz) {
    return PK_SAMPLE_RATE;
}

JNIEXPORT jint JNICALL
Java_com_pokedaisy_app_MgbaCore_pkRenderReadAudio(JNIEnv* env, jobject thiz, jshortArray out) {
    if (rg.audioAvail <= 0) {
        return 0;
    }
    jsize cap = (*env)->GetArrayLength(env, out);
    int n = rg.audioAvail;
    if (n > cap) {
        n = cap;
    }
    (*env)->SetShortArrayRegion(env, out, 0, n, rg.audio);
    return n;
}

// Calls Thumb function `fn` with r0 = `arg0`, r1 = `arg1` and returns once it has, leaving
// the CPU exactly as it was (registers, pipeline, halt). A frame can end
// anywhere - mid-way through the game's own code - and a bare PC hijack
// clobbered its live registers (Unbound then reset its sound engine). So:
// run instruction by instruction with IRQs masked until the call returns to
// PK_CALL_SENTINEL (an address in the ROM header, never executed), then put
// everything back. Tested headless with native-capture/mgba_dump's `call`.
#define PK_CALL_SENTINEL 0x080000C0u
static int pk_call(struct mCore* core, uint32_t fn, uint32_t arg0, uint32_t arg1) {
    if (!pk_is_gba(core)) {
        return 0;   // an ARM-only trick: the GB core's CPU is an SM83
    }
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
    int ok = 0;
    for (int i = 0; i < 2000000; i++) {
        if (cpu->executionMode == MODE_THUMB && (uint32_t) cpu->gprs[ARM_PC] - WORD_SIZE_THUMB == PK_CALL_SENTINEL) {
            ok = 1;
            break;
        }
        core->step(core);
    }
    cpu->regs = saved;
    cpu->executionMode = exec == MODE_ARM ? MODE_THUMB : MODE_ARM; // so _ARMSetMode applies
    _ARMSetMode(cpu, exec);
    cpu->prefetch[0] = prefetch0;
    cpu->prefetch[1] = prefetch1;
    cpu->halted = halted;
    return ok;
}

// Calls the ROM's own m4aSongNumStart(songId, alt) at `addr` (FfMusicRenderer
// finds it with M4aSongs) on the render core, cleanly (pk_call). `alt` picks
// Heart and Soul's alternate soundtrack; other games' ignore r1.
JNIEXPORT jboolean JNICALL
Java_com_pokedaisy_app_MgbaCore_pkRenderForceSong(JNIEnv* env, jobject thiz, jlong addr, jint songId, jint alt) {
    if (!rg.core || !rg.core->cpu) {
        return JNI_FALSE;
    }
    return pk_call(rg.core, (uint32_t) addr, (uint32_t) songId, (uint32_t) alt) ? JNI_TRUE : JNI_FALSE;
}

// Parks the render core's main loop on `spin` (a Thumb `b .`) so the booted
// game can't change the music while a song records; the VBlank interrupt
// keeps the sound engine running. Steps first until the CPU is in the main
// context (System/User mode, IRQs on) with the VBlank IRQ fully enabled (IME,
// IE bit 0, DISPSTAT bit 3) - games switch it off around loads, and parking
// then left the music frozen (Unbound). Tested with mgba_dump's `park`.
JNIEXPORT jboolean JNICALL
Java_com_pokedaisy_app_MgbaCore_pkRenderPark(JNIEnv* env, jobject thiz, jlong spin) {
    if (!rg.core || !rg.core->cpu || !pk_is_gba(rg.core)) {
        return JNI_FALSE;
    }
    struct mCore* core = rg.core;
    struct ARMCore* cpu = (struct ARMCore*) core->cpu;
    for (int i = 0; i < 5000000; i++) {
        if ((cpu->privilegeMode == MODE_SYSTEM || cpu->privilegeMode == MODE_USER) && !cpu->cpsr.i && !cpu->halted
            && (core->busRead16(core, 0x04000208) & 1) && (core->busRead16(core, 0x04000200) & 1)
            && (core->busRead16(core, 0x04000004) & 8)) {
            cpu->gprs[ARM_PC] = (int32_t) ((uint32_t) spin & ~1u);
            _ARMSetMode(cpu, MODE_THUMB);
            ThumbWritePC(cpu);
            return JNI_TRUE;
        }
        core->step(core);
    }
    return JNI_FALSE;
}

// The Game Boy render core's twins of pkRenderPark / pkRenderForceSong (Gen 1's
// music: FfMusicRenderer's Game Boy path). Park: once the main loop runs with
// interrupts on (IME, IE's VBlank bit, the LCD on), its PC goes to `spin` - a
// `jr @` (18 FE) in ROM bank 0 - and only the VBlank handler, which runs the
// sound engine every frame, keeps going. Tested with mgba_dump's `gbpark`.
JNIEXPORT jboolean JNICALL
Java_com_pokedaisy_app_MgbaCore_pkRenderGbPark(JNIEnv* env, jobject thiz, jint spin) {
    if (!rg.core || pk_is_gba(rg.core)) {
        return JNI_FALSE;
    }
    struct mCore* core = rg.core;
    struct GB* gb = (struct GB*) core->board;
    struct SM83Core* cpu = (struct SM83Core*) core->cpu;
    for (int i = 0; i < 5000000; i++) {
        core->step(core);   // ends on an instruction boundary (SM83_CORE_FETCH)
        if (gb->memory.ime && (gb->memory.ie & 1) && (gb->memory.io[GB_REG_LCDC] & 0x80) && !cpu->halted && !cpu->irqPending) {
            cpu->pc = (uint16_t) spin;
            cpu->memory.setActiveRegion(cpu, cpu->pc);
            return JNI_TRUE;
        }
    }
    return JNI_FALSE;
}

// Calls the game's `fn` (bank 0) with A = `a`, C = `c` on the parked GB render core
// and returns once it comes back to `ret` (the park loop, pushed as the return
// address), interrupts held off meanwhile so the VBlank handler can't run halfway
// through it. Gen 1: PlayMusic(a = song id, c = its audio bank).
JNIEXPORT jboolean JNICALL
Java_com_pokedaisy_app_MgbaCore_pkRenderGbCall(JNIEnv* env, jobject thiz, jint fn, jint a, jint c, jint ret) {
    if (!rg.core || pk_is_gba(rg.core)) {
        return JNI_FALSE;
    }
    struct mCore* core = rg.core;
    struct GB* gb = (struct GB*) core->board;
    struct SM83Core* cpu = (struct SM83Core*) core->cpu;
    bool ime = gb->memory.ime;
    bool pending = cpu->irqPending;
    gb->memory.ime = false;
    cpu->irqPending = false;
    cpu->sp -= 2;
    core->busWrite8(core, cpu->sp, (uint8_t) (ret & 0xFF));
    core->busWrite8(core, (uint16_t) (cpu->sp + 1), (uint8_t) ((ret >> 8) & 0xFF));
    cpu->a = (uint8_t) a;
    cpu->c = (uint8_t) c;
    cpu->halted = false;
    cpu->pc = (uint16_t) fn;
    cpu->memory.setActiveRegion(cpu, cpu->pc);
    int ok = 0;
    for (int i = 0; i < 2000000; i++) {
        core->step(core);
        if (cpu->pc == (uint16_t) ret) {
            ok = 1;
            break;
        }
    }
    gb->memory.ime = ime;
    cpu->irqPending = pending;
    return ok ? JNI_TRUE : JNI_FALSE;
}

// Copies up to out.length interleaved shorts of the most recent frame's audio
// into `out`; returns the count written.
JNIEXPORT jint JNICALL
Java_com_pokedaisy_app_MgbaCore_pkReadAudio(JNIEnv* env, jobject thiz, jshortArray out) {
    if (g.audioAvail <= 0) {
        return 0;
    }
    jsize cap = (*env)->GetArrayLength(env, out);
    int n = g.audioAvail;
    if (n > cap) {
        n = cap;
    }
    (*env)->SetShortArrayRegion(env, out, 0, n, g.audio);
    return n;
}

JNIEXPORT jboolean JNICALL
Java_com_pokedaisy_app_MgbaCore_pkSaveState(JNIEnv* env, jobject thiz, jstring jPath) {
    if (!g.core) {
        return JNI_FALSE;
    }
    const char* path = (*env)->GetStringUTFChars(env, jPath, NULL);
    // O_RDWR: the extended (mpack) state writer reads back parts of the VFile.
    struct VFile* vf = VFileOpen(path, O_RDWR | O_CREAT | O_TRUNC);
    (*env)->ReleaseStringUTFChars(env, jPath, path);
    if (!vf) {
        return JNI_FALSE;
    }
    bool ok = mCoreSaveStateNamed(g.core, vf, SAVESTATE_SAVEDATA | SAVESTATE_RTC | SAVESTATE_METADATA);
    vf->close(vf);
    return ok ? JNI_TRUE : JNI_FALSE;
}

// True if the save file holds exactly [data] (bytes it doesn't have yet count as
// 0xFF, erased flash: mGBA fills a short file with that). Only the first
// [size] bytes count, so mGBA's RTC footer on a .sav doesn't.
static bool pk_save_file_equals(const uint8_t* data, size_t size) {
    struct VFile* vf = g.saveVf;
    if (!vf || g.saveReadOnly) {
        return true;                    // no file a state load could overwrite
    }
    uint8_t buf[4096];
    size_t off = 0;
    vf->seek(vf, 0, SEEK_SET);
    while (off < size) {
        size_t want = size - off < sizeof(buf) ? size - off : sizeof(buf);
        ssize_t n = vf->read(vf, buf, want);
        if (n <= 0) {
            break;
        }
        if (memcmp(buf, data + off, (size_t) n) != 0) {
            return false;
        }
        off += (size_t) n;
    }
    for (; off < size; ++off) {
        if (data[off] != 0xFF) {
            return false;
        }
    }
    return true;
}

// Does the save inside the state at [path] match the save file the game just
// opened? A state carries the save as it was when the state was made, and
// pkLoadState writes that copy over the file. Run before the auto-resume, so a
// save changed since (another emulator, another saves folder, a file manager)
// is never replaced by an older or empty one. 1 = same, 0 = different,
// -1 = the state holds no save (loading it leaves the file alone) or can't be read.
JNIEXPORT jint JNICALL
Java_com_pokedaisy_app_MgbaCore_pkStateMatchesSave(JNIEnv* env, jobject thiz, jstring jPath) {
    if (!g.core) {
        return -1;
    }
    const char* path = (*env)->GetStringUTFChars(env, jPath, NULL);
    struct VFile* vf = VFileOpen(path, O_RDONLY);
    (*env)->ReleaseStringUTFChars(env, jPath, path);
    if (!vf) {
        return -1;
    }
    int result = -1;
    struct mStateExtdata extdata;
    mStateExtdataInit(&extdata);
    if (mCoreExtractExtdata(g.core, vf, &extdata)) {
        struct mStateExtdataItem item;
        if (mStateExtdataGet(&extdata, EXTDATA_SAVEDATA, &item) && item.data && item.size > 0) {
            result = pk_save_file_equals(item.data, (size_t) item.size) ? 1 : 0;
        }
    }
    mStateExtdataDeinit(&extdata);
    vf->close(vf);
    return result;
}

JNIEXPORT jboolean JNICALL
Java_com_pokedaisy_app_MgbaCore_pkLoadState(JNIEnv* env, jobject thiz, jstring jPath) {
    if (!g.core) {
        return JNI_FALSE;
    }
    const char* path = (*env)->GetStringUTFChars(env, jPath, NULL);
    struct VFile* vf = VFileOpen(path, O_RDONLY);
    (*env)->ReleaseStringUTFChars(env, jPath, path);
    if (!vf) {
        return JNI_FALSE;
    }
    bool ok = mCoreLoadStateNamed(g.core, vf, SAVESTATE_SAVEDATA | SAVESTATE_RTC);
    vf->close(vf);
    return ok ? JNI_TRUE : JNI_FALSE;
}

// --- memory / ROM introspection (for the companion / telemetry read) --------
// Called from the emu thread only (same as pkRunFrame), so bus reads never race
// a running frame.

static void pk_read_range(struct mCore* core, uint32_t addr, uint8_t* dst, int len) {
    int i = 0;
    // Bytes up to a word boundary first: busRead32 behaves like the CPU's own
    // LDR, which rounds an unaligned address down and rotates the word, so a
    // range starting at an odd address came back with every 4th byte taken
    // from 4 bytes earlier (Unbound's owned-dex flags start at ...8B9, which
    // turned GIBLE's bit into GALLADE's).
    for (; i < len && ((addr + i) & 3); i++) {
        dst[i] = (uint8_t) core->busRead8(core, addr + i);
    }
    for (; i + 4 <= len; i += 4) {
        uint32_t v = core->busRead32(core, addr + i);
        dst[i] = (uint8_t) v;
        dst[i + 1] = (uint8_t) (v >> 8);
        dst[i + 2] = (uint8_t) (v >> 16);
        dst[i + 3] = (uint8_t) (v >> 24);
    }
    for (; i < len; i++) {
        dst[i] = (uint8_t) core->busRead8(core, addr + i);
    }
}

JNIEXPORT jbyteArray JNICALL
Java_com_pokedaisy_app_MgbaCore_pkReadBytes(JNIEnv* env, jobject thiz, jlong addr, jint len) {
    if (len <= 0 || len > (1 << 24)) {
        return NULL;
    }
    uint8_t* tmp = malloc((size_t) len);
    if (!tmp) {
        return NULL;
    }
    pthread_rwlock_rdlock(&pk_coreLock);
    bool ok = g.core != NULL;
    if (ok) {
        pk_read_range(g.core, (uint32_t) addr, tmp, len);
        // The ROM as the file has it, under any cheat's hook or ROM patch: the
        // Poller identifies hacks by hashing it (see pk_cheats.c).
        pk_cheats_overlay((uint32_t) addr, tmp, len);
    }
    pthread_rwlock_unlock(&pk_coreLock);
    jbyteArray out = ok ? (*env)->NewByteArray(env, len) : NULL;
    if (out) {
        (*env)->SetByteArrayRegion(env, out, 0, len, (const jbyte*) tmp);
    }
    free(tmp);
    return out;
}

// Same as pkReadBytes, from the FF-music render core (rg): FfMusicRenderer
// reads which song header it actually started, like the live game's key.
JNIEXPORT jbyteArray JNICALL
Java_com_pokedaisy_app_MgbaCore_pkRenderReadBytes(JNIEnv* env, jobject thiz, jlong addr, jint len) {
    if (!rg.core || len <= 0 || len > (1 << 24)) {
        return NULL;
    }
    jbyteArray out = (*env)->NewByteArray(env, len);
    if (!out) {
        return NULL;
    }
    uint8_t* tmp = malloc((size_t) len);
    if (!tmp) {
        return out;
    }
    pk_read_range(rg.core, (uint32_t) addr, tmp, len);
    (*env)->SetByteArrayRegion(env, out, 0, len, (const jbyte*) tmp);
    free(tmp);
    return out;
}

static jlong pk_find_magic(const jbyte* m4);

// Scans IWRAM then EWRAM for a 4-byte magic; returns its GBA address or -1.
JNIEXPORT jlong JNICALL
Java_com_pokedaisy_app_MgbaCore_pkFindMagic(JNIEnv* env, jobject thiz, jbyteArray jMagic) {
    jbyte m4[4];
    if ((*env)->GetArrayLength(env, jMagic) < 4) {
        return -1;
    }
    (*env)->GetByteArrayRegion(env, jMagic, 0, 4, m4);
    pthread_rwlock_rdlock(&pk_coreLock);
    jlong found = pk_find_magic(m4);
    pthread_rwlock_unlock(&pk_coreLock);
    return found;
}

static jlong pk_find_magic(const jbyte* m4) {
    if (!pk_is_gba(g.core)) {
        return -1;
    }
    const uint32_t regions[][2] = {
        { 0x03000000u, 0x8000u },     // IWRAM (32 KiB) — struct lives here
        { 0x02000000u, 0x40000u },    // EWRAM (256 KiB)
    };
    uint8_t buf[4096 + 3];
    for (int r = 0; r < 2; r++) {
        uint32_t base = regions[r][0];
        uint32_t size = regions[r][1];
        for (uint32_t off = 0; off < size; off += 4096) {
            uint32_t chunk = (size - off < 4096) ? (size - off) : 4096;
            uint32_t read = chunk + 3;
            if (off + read > size) read = chunk;   // don't overrun region tail
            pk_read_range(g.core, base + off, buf, (int) read);
            for (uint32_t i = 0; i + 4 <= read; i++) {
                if (buf[i] == (uint8_t) m4[0] && buf[i + 1] == (uint8_t) m4[1] &&
                    buf[i + 2] == (uint8_t) m4[2] && buf[i + 3] == (uint8_t) m4[3]) {
                    return (jlong) (base + off + i);
                }
            }
        }
    }
    return -1;
}

JNIEXPORT jstring JNICALL
Java_com_pokedaisy_app_MgbaCore_pkRomCode(JNIEnv* env, jobject thiz) {
    if (!g.core) {
        return NULL;
    }
    if (!pk_is_gba(g.core)) {
        return (*env)->NewStringUTF(env, "");   // no GBA header (0xAC would be GB ROM bytes)
    }
    char code[5] = {0};
    for (int i = 0; i < 4; i++) {
        code[i] = (char) g.core->busRead8(g.core, 0x080000ACu + i);
    }
    return (*env)->NewStringUTF(env, code);
}

// The cartridge's own bytes [off, off + len) - a Game Boy cart is bank-switched,
// so its ROM can't be read whole over the bus (the Poller hashes it this way).
JNIEXPORT jbyteArray JNICALL
Java_com_pokedaisy_app_MgbaCore_pkRomRead(JNIEnv* env, jobject thiz, jlong off, jint len) {
    if (off < 0 || len <= 0) {
        return NULL;
    }
    jbyteArray out = NULL;
    pthread_rwlock_rdlock(&pk_coreLock);
    if (g.core && g.core->platform(g.core) == mPLATFORM_GB) {
        struct GB* gb = g.core->board;
        if (gb->memory.rom && (size_t) off + (size_t) len <= gb->pristineRomSize) {
            out = (*env)->NewByteArray(env, len);
            if (out) {
                (*env)->SetByteArrayRegion(env, out, 0, len, (const jbyte*) gb->memory.rom + off);
            }
        }
    }
    pthread_rwlock_unlock(&pk_coreLock);
    return out;
}

// 0 = GBA, 1 = Game Boy / Color, -1 = no core.
JNIEXPORT jint JNICALL
Java_com_pokedaisy_app_MgbaCore_pkPlatform(JNIEnv* env, jobject thiz) {
    if (!g.core) {
        return -1;
    }
    return pk_is_gba(g.core) ? 0 : 1;
}

JNIEXPORT jlong JNICALL
Java_com_pokedaisy_app_MgbaCore_pkRomSize(JNIEnv* env, jobject thiz) {
    return g.core ? (jlong) g.core->romSize(g.core) : 0;
}

// --- cheats (pk_cheats.c) ----------------------------------------------------

// Checks a cheat's code lines without a core (Settings can run with no game).
// Returns "<directive>\n<'1' / '0' per line>", null on failure.
JNIEXPORT jstring JNICALL
Java_com_pokedaisy_app_MgbaCore_pkCheatsCheck(JNIEnv* env, jobject thiz, jstring jCode, jint type, jstring jDirective) {
    const char* code = (*env)->GetStringUTFChars(env, jCode, NULL);
    const char* directive = (*env)->GetStringUTFChars(env, jDirective, NULL);
    char* result = pk_cheats_check(code, (int) type, directive);
    (*env)->ReleaseStringUTFChars(env, jDirective, directive);
    (*env)->ReleaseStringUTFChars(env, jCode, code);
    if (!result) {
        return NULL;
    }
    // Directive names and '0'/'1' only: plain ASCII, safe for NewStringUTF.
    jstring out = (*env)->NewStringUTF(env, result);
    free(result);
    return out;
}

// Emu thread: replaces the player's core's cheats with `text` (mGBA .cheats
// format, enabled cheats only; empty = none). Returns the sets loaded, -1 if it
// didn't parse. Under the core lock, so a ROM read never sees half a swap.
JNIEXPORT jint JNICALL
Java_com_pokedaisy_app_MgbaCore_pkCheatsApply(JNIEnv* env, jobject thiz, jstring jText) {
    const char* text = (*env)->GetStringUTFChars(env, jText, NULL);
    pthread_rwlock_wrlock(&pk_coreLock);
    int n = g.core ? pk_cheats_apply(g.core, text, strlen(text)) : 0;
    pthread_rwlock_unlock(&pk_coreLock);
    (*env)->ReleaseStringUTFChars(env, jText, text);
    return n;
}

// For pokedaisy_ra.c: the file's ROM bytes in and out around RA's hash (emu thread).
void pkRomSwapCheats(void) {
    pthread_rwlock_wrlock(&pk_coreLock);
    if (g.core && pk_cheats_rom_patched()) {
        pk_cheats_swap_rom(g.core);
    }
    pthread_rwlock_unlock(&pk_coreLock);
}

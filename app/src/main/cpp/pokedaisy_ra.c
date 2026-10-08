// PokeDaisy — RetroAchievements (rcheevos rc_client) over the main mGBA core.
//
// One rc_client for the app's lifetime. Kotlin (achievements/RaNative.kt) does
// the HTTP: rc_client hands us a request in raServerCall, we pass it up as
// RaNative.serverCall(id, ...), and the answer comes back through
// raQueueResponse(id, ...). Answers are *queued*, not handed straight to
// rc_client, and raPump() delivers them: rc_client reads game memory while
// finishing a game load (address validation), so while a game runs the emu
// thread pumps between frames, the same as every other core memory read here.
// With no game running Kotlin pumps from its HTTP thread (login only).
//
// Only ever reads the player's core (pkMainCore) — never the FF-music render core.

#include <jni.h>
#include <android/log.h>
#include <pthread.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#include <mgba/core/core.h>
#include <mgba/internal/gba/gba.h>
#include <mgba/internal/gba/memory.h>
#include <mgba/internal/gb/gb.h>
#include <mgba/internal/gb/memory.h>

#include "rc_client.h"
#include "rc_consoles.h"

#include "pk_cheats.h"

#define LOG_TAG "pokedaisy/ra"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  LOG_TAG, __VA_ARGS__)

struct mCore* pkMainCore(void);         // pokedaisy_jni.c

static JavaVM* ra_vm;
static jclass ra_class;                 // com.pokedaisy.app.achievements.RaNative
static jmethodID ra_serverCall, ra_onLogin, ra_onGameLoaded, ra_onEvent, ra_onLog, ra_onLeaderboardEntries, ra_onAllProgress;
static jclass ra_stringClass;
static jmethodID ra_stringFromBytes;    // String(byte[], String charsetName)
static jstring ra_utf8;
static rc_client_t* ra_client;

// --- pending server calls ----------------------------------------------------

#define RA_MAX_CALLS 64

static struct {
    int id;                             // 0 = free slot
    rc_client_server_callback_t callback;
    void* callback_data;
    int ready;                          // response arrived, waiting for raPump
    int status;
    char* body;
    size_t bodyLen;
} ra_calls[RA_MAX_CALLS];
static int ra_nextId = 1;
static volatile int ra_readyCount;
static pthread_mutex_t ra_callsLock = PTHREAD_MUTEX_INITIALIZER;
// raPump runs on whichever thread owns delivery right now; never two at once.
static pthread_mutex_t ra_pumpLock = PTHREAD_MUTEX_INITIALIZER;

static JNIEnv* raEnv(void) {
    JNIEnv* env = NULL;
    if ((*ra_vm)->GetEnv(ra_vm, (void**) &env, JNI_VERSION_1_6) == JNI_EDETACHED) {
        (*ra_vm)->AttachCurrentThread(ra_vm, &env, NULL);
    }
    return env;
}

// Real UTF-8 -> String. Not NewStringUTF: that takes modified UTF-8, and an
// emoji (4-byte UTF-8) in a title aborts the app under CheckJNI.
static jstring raStr(JNIEnv* env, const char* s) {
    if (!s) {
        return NULL;
    }
    jsize len = (jsize) strlen(s);
    jbyteArray bytes = (*env)->NewByteArray(env, len);
    (*env)->SetByteArrayRegion(env, bytes, 0, len, (const jbyte*) s);
    jstring out = (*env)->NewObject(env, ra_stringClass, ra_stringFromBytes, bytes, ra_utf8);
    (*env)->DeleteLocalRef(env, bytes);
    return out;
}

// Calls a RaNative static callback and clears any exception it threw: a Java
// exception left pending makes the next JNI call abort the process (CheckJNI),
// or surfaces in whatever Kotlin frame called into rc_client.
static void raCallback(JNIEnv* env, jmethodID method, ...) {
    va_list args;
    va_start(args, method);
    (*env)->CallStaticVoidMethodV(env, ra_class, method, args);
    va_end(args);
    if ((*env)->ExceptionCheck(env)) {
        LOGW("RaNative callback threw");
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
    }
}

static void raDeleteLocal(JNIEnv* env, jobject o) {
    if (o) {
        (*env)->DeleteLocalRef(env, o);
    }
}

// --- rc_client callbacks -----------------------------------------------------

// Debug counters for raDebugStatus: reads that came back short, and the last such address.
static volatile uint32_t ra_shortReads, ra_lastShortAddr, ra_reads;

// RA's GBA map (rcheevos consoleinfo.c): $000000 IWRAM, $008000 EWRAM, $048000 cart save RAM.
static uint32_t raReadMemoryImpl(uint32_t address, uint8_t* buffer, uint32_t num_bytes);

static uint32_t raReadMemory(uint32_t address, uint8_t* buffer, uint32_t num_bytes, rc_client_t* client) {
    (void) client;
    uint32_t n = raReadMemoryImpl(address, buffer, num_bytes);
    ra_reads++;
    if (n < num_bytes) {
        ra_shortReads++;
        ra_lastShortAddr = address;
    }
    return n;
}

// RA's Game Boy / Color map: $0000-$FFFF the CPU bus (WRAM bank 1 fixed at $D000),
// then $10000-$15FFF the Color's WRAM banks 2-7.
static uint32_t raReadMemoryGb(struct mCore* core, uint32_t address, uint8_t* buffer, uint32_t num_bytes) {
    struct GB* gb = core->board;
    uint32_t done = 0;
    for (; done < num_bytes; done++) {
        uint32_t a = address + done;
        if (a >= 0xD000 && a < 0xE000) {
            buffer[done] = gb->memory.wram[0x1000 + (a - 0xD000)];
        } else if (a < 0x10000) {
            buffer[done] = (uint8_t) core->busRead8(core, a);
        } else if (a < 0x16000) {
            buffer[done] = gb->memory.wram[0x2000 + (a - 0x10000)];
        } else {
            break;
        }
    }
    return done;
}

static uint32_t raReadMemoryImpl(uint32_t address, uint8_t* buffer, uint32_t num_bytes) {
    struct mCore* core = pkMainCore();
    if (!core) {
        return 0;
    }
    if (core->platform(core) == mPLATFORM_GB) {
        return raReadMemoryGb(core, address, buffer, num_bytes);
    }
    uint32_t done = 0;
    while (done < num_bytes) {
        uint32_t a = address + done;
        size_t id, base;
        if (a < 0x8000) {
            id = REGION_WORKING_IRAM; base = 0;
        } else if (a < 0x48000) {
            id = REGION_WORKING_RAM; base = 0x8000;
        } else if (a < 0x58000) {
            id = REGION_CART_SRAM; base = 0x48000;
        } else {
            break;
        }
        size_t size = 0;
        const uint8_t* block = core->getMemoryBlock(core, id, &size);
        size_t off = a - base;
        if (!block || off >= size) {
            break;
        }
        size_t n = size - off;
        if (n > num_bytes - done) {
            n = num_bytes - done;
        }
        memcpy(buffer + done, block + off, n);
        done += (uint32_t) n;
    }
    return done;
}

static void raServerCall(const rc_api_request_t* request, rc_client_server_callback_t callback,
                         void* callback_data, rc_client_t* client) {
    (void) client;
    pthread_mutex_lock(&ra_callsLock);
    int slot = -1;
    for (int i = 0; i < RA_MAX_CALLS; i++) {
        if (!ra_calls[i].id) {
            slot = i;
            break;
        }
    }
    int id = 0;
    if (slot >= 0) {
        id = ra_nextId++;
        if (ra_nextId <= 0) {
            ra_nextId = 1;
        }
        ra_calls[slot].id = id;
        ra_calls[slot].callback = callback;
        ra_calls[slot].callback_data = callback_data;
        ra_calls[slot].ready = 0;
    }
    pthread_mutex_unlock(&ra_callsLock);

    if (!id) {
        // Out of slots: fail it now (retryable, so rc_client tries again later).
        LOGW("server call dropped: too many in flight");
        rc_api_server_response_t r = { NULL, 0, RC_API_SERVER_RESPONSE_RETRYABLE_CLIENT_ERROR };
        callback(&r, callback_data);
        return;
    }
    JNIEnv* env = raEnv();
    jstring url = raStr(env, request->url);
    jstring post = raStr(env, request->post_data);
    jstring type = raStr(env, request->content_type);
    raCallback(env, ra_serverCall, (jint) id, url, post, type);
    raDeleteLocal(env, url);
    raDeleteLocal(env, post);
    raDeleteLocal(env, type);
}

static void raLog(const char* message, const rc_client_t* client) {
    (void) client;
    JNIEnv* env = raEnv();
    jstring m = raStr(env, message);
    raCallback(env, ra_onLog, m);
    raDeleteLocal(env, m);
}

static void raEvent(const rc_client_event_t* event, rc_client_t* client) {
    (void) client;
    JNIEnv* env = raEnv();
    jint id = 0, points = 0;
    const char *title = NULL, *description = NULL, *badge = NULL, *extra = NULL;
    char buf[64];
    if (event->achievement) {
        const rc_client_achievement_t* a = event->achievement;
        id = (jint) a->id;
        points = (jint) a->points;
        title = a->title;
        description = a->description;
        badge = a->badge_url;
        extra = a->measured_progress;
    } else if (event->leaderboard) {
        const rc_client_leaderboard_t* l = event->leaderboard;
        id = (jint) l->id;
        title = l->title;
        description = l->description;
        extra = l->tracker_value;
    } else if (event->leaderboard_tracker) {
        id = (jint) event->leaderboard_tracker->id;
        extra = event->leaderboard_tracker->display;
    } else if (event->leaderboard_scoreboard) {
        const rc_client_leaderboard_scoreboard_t* s = event->leaderboard_scoreboard;
        id = (jint) s->leaderboard_id;
        points = (jint) s->new_rank;
        extra = s->submitted_score;
    } else if (event->server_error) {
        id = (jint) event->server_error->related_id;
        title = event->server_error->api;
        description = event->server_error->error_message;
    } else if (event->subset) {
        id = (jint) event->subset->id;
        title = event->subset->title;
        badge = event->subset->badge_url;
    }
    if (event->type == RC_CLIENT_EVENT_GAME_COMPLETED) {
        const rc_client_game_t* g = rc_client_get_game_info(ra_client);
        if (g) {
            id = (jint) g->id;
            title = g->title;
            badge = g->badge_url;
        }
    }
    if (!extra) {
        buf[0] = '\0';
        extra = buf;
    }
    jstring jt = raStr(env, title), jd = raStr(env, description), jb = raStr(env, badge), je = raStr(env, extra);
    raCallback(env, ra_onEvent, (jint) event->type, id, jt, jd, jb, je, points);
    raDeleteLocal(env, jt);
    raDeleteLocal(env, jd);
    raDeleteLocal(env, jb);
    raDeleteLocal(env, je);
}

static void raLoginDone(int result, const char* error_message, rc_client_t* client, void* userdata) {
    (void) userdata;
    JNIEnv* env = raEnv();
    const rc_client_user_t* u = result == RC_OK ? rc_client_get_user_info(client) : NULL;
    jstring err = raStr(env, error_message);
    jstring user = raStr(env, u ? u->username : NULL);
    jstring display = raStr(env, u ? u->display_name : NULL);
    jstring token = raStr(env, u ? u->token : NULL);
    jstring avatar = raStr(env, u ? u->avatar_url : NULL);
    raCallback(env, ra_onLogin, (jint) result, err, user, display, token, avatar,
                                 (jint) (u ? u->score : 0), (jint) (u ? u->score_softcore : 0));
    raDeleteLocal(env, err);
    raDeleteLocal(env, user);
    raDeleteLocal(env, display);
    raDeleteLocal(env, token);
    raDeleteLocal(env, avatar);
}

static void raGameLoaded(int result, const char* error_message, rc_client_t* client, void* userdata) {
    (void) userdata;
    JNIEnv* env = raEnv();
    const rc_client_game_t* g = rc_client_get_game_info(client);
    rc_client_user_game_summary_t s;
    memset(&s, 0, sizeof(s));
    if (result == RC_OK) {
        rc_client_get_user_game_summary(client, &s);
    }
    jstring err = raStr(env, error_message);
    jstring title = raStr(env, g ? g->title : NULL);
    jstring badge = raStr(env, g ? g->badge_url : NULL);
    jstring hash = raStr(env, g ? g->hash : NULL);
    raCallback(env, ra_onGameLoaded, (jint) result, err, (jint) (g ? g->id : 0),
                                 title, badge, hash,
                                 (jint) s.num_core_achievements, (jint) s.num_unlocked_achievements,
                                 (jint) s.points_core, (jint) s.points_unlocked);
    raDeleteLocal(env, err);
    raDeleteLocal(env, title);
    raDeleteLocal(env, badge);
    raDeleteLocal(env, hash);
}

// --- JNI ---------------------------------------------------------------------

#define RA_FN(name) Java_com_pokedaisy_app_achievements_RaNative_##name

// rc_client.c's RC_CLIENT_ACHIEVEMENT_WARNING_ID: ids from here up are its placeholders.
#define RA_WARNING_ACHIEVEMENT_ID 101000001u

JNIEXPORT jboolean JNICALL
RA_FN(raInit)(JNIEnv* env, jobject thiz) {
    if (ra_client) {
        return JNI_TRUE;
    }
    (*env)->GetJavaVM(env, &ra_vm);
    jclass cls = (*env)->GetObjectClass(env, thiz);
    ra_class = (*env)->NewGlobalRef(env, cls);
    ra_serverCall = (*env)->GetStaticMethodID(env, cls, "serverCall",
        "(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");
    ra_onLogin = (*env)->GetStaticMethodID(env, cls, "onLogin",
        "(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;II)V");
    ra_onGameLoaded = (*env)->GetStaticMethodID(env, cls, "onGameLoaded",
        "(ILjava/lang/String;ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;IIII)V");
    ra_onEvent = (*env)->GetStaticMethodID(env, cls, "onEvent",
        "(IILjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;I)V");
    ra_onLog = (*env)->GetStaticMethodID(env, cls, "onLog", "(Ljava/lang/String;)V");
    ra_onLeaderboardEntries = (*env)->GetStaticMethodID(env, cls, "onLeaderboardEntries", "(IZILjava/lang/String;[BII)V");
    ra_onAllProgress = (*env)->GetStaticMethodID(env, cls, "onAllProgress", "(II[B)V");
    jclass str = (*env)->FindClass(env, "java/lang/String");
    ra_stringClass = (*env)->NewGlobalRef(env, str);
    ra_stringFromBytes = (*env)->GetMethodID(env, str, "<init>", "([BLjava/lang/String;)V");
    ra_utf8 = (*env)->NewGlobalRef(env, (*env)->NewStringUTF(env, "UTF-8"));
    if (!ra_serverCall || !ra_onLogin || !ra_onGameLoaded || !ra_onEvent || !ra_onLog || !ra_onLeaderboardEntries || !ra_onAllProgress) {
        return JNI_FALSE;               // NoSuchMethodError is pending for Kotlin
    }
    ra_client = rc_client_create(raReadMemory, raServerCall);
    if (!ra_client) {
        return JNI_FALSE;
    }
    rc_client_enable_logging(ra_client, RC_CLIENT_LOG_LEVEL_INFO, raLog);
    rc_client_set_event_handler(ra_client, raEvent);
    // Softcore until PokeDaisy is a recognised hardcore client: loading states,
    // rewinds and the automated battle input aren't blocked yet.
    rc_client_set_hardcore_enabled(ra_client, 0);
    return JNI_TRUE;
}

JNIEXPORT jstring JNICALL
RA_FN(raUserAgentClause)(JNIEnv* env, jobject thiz) {
    char buf[64];
    buf[0] = '\0';
    if (ra_client) {
        rc_client_get_user_agent_clause(ra_client, buf, sizeof(buf));
    }
    return (*env)->NewStringUTF(env, buf);
}

JNIEXPORT void JNICALL
RA_FN(raQueueResponse)(JNIEnv* env, jobject thiz, jint id, jint status, jbyteArray jBody) {
    char* body = NULL;
    size_t len = 0;
    if (jBody) {
        len = (size_t) (*env)->GetArrayLength(env, jBody);
        body = malloc(len + 1);
        (*env)->GetByteArrayRegion(env, jBody, 0, (jsize) len, (jbyte*) body);
        body[len] = '\0';
    }
    pthread_mutex_lock(&ra_callsLock);
    for (int i = 0; i < RA_MAX_CALLS; i++) {
        if (ra_calls[i].id == id && !ra_calls[i].ready) {
            ra_calls[i].ready = 1;
            ra_calls[i].status = status;
            ra_calls[i].body = body;
            ra_calls[i].bodyLen = len;
            body = NULL;
            ra_readyCount++;
            break;
        }
    }
    pthread_mutex_unlock(&ra_callsLock);
    free(body);                         // unknown id (aborted): drop it
}

JNIEXPORT void JNICALL
RA_FN(raPump)(JNIEnv* env, jobject thiz) {
    if (!ra_readyCount) {
        return;
    }
    pthread_mutex_lock(&ra_pumpLock);
    for (;;) {
        rc_client_server_callback_t callback = NULL;
        void* data = NULL;
        rc_api_server_response_t r;
        char* body = NULL;
        pthread_mutex_lock(&ra_callsLock);
        for (int i = 0; i < RA_MAX_CALLS; i++) {
            if (ra_calls[i].id && ra_calls[i].ready) {
                callback = ra_calls[i].callback;
                data = ra_calls[i].callback_data;
                body = ra_calls[i].body;
                r.body = body;
                r.body_length = ra_calls[i].bodyLen;
                r.http_status_code = ra_calls[i].status;
                memset(&ra_calls[i], 0, sizeof(ra_calls[i]));
                ra_readyCount--;
                break;
            }
        }
        pthread_mutex_unlock(&ra_callsLock);
        if (!callback) {
            break;
        }
        // Outside the lock: the callback may start the next request (raServerCall).
        callback(&r, data);
        free(body);
    }
    pthread_mutex_unlock(&ra_pumpLock);
}

JNIEXPORT void JNICALL
RA_FN(raLoginWithPassword)(JNIEnv* env, jobject thiz, jstring jUser, jstring jPassword) {
    const char* user = (*env)->GetStringUTFChars(env, jUser, NULL);
    const char* pass = (*env)->GetStringUTFChars(env, jPassword, NULL);
    rc_client_begin_login_with_password(ra_client, user, pass, raLoginDone, NULL);
    (*env)->ReleaseStringUTFChars(env, jUser, user);
    (*env)->ReleaseStringUTFChars(env, jPassword, pass);
}

JNIEXPORT void JNICALL
RA_FN(raLoginWithToken)(JNIEnv* env, jobject thiz, jstring jUser, jstring jToken) {
    const char* user = (*env)->GetStringUTFChars(env, jUser, NULL);
    const char* token = (*env)->GetStringUTFChars(env, jToken, NULL);
    rc_client_begin_login_with_token(ra_client, user, token, raLoginDone, NULL);
    (*env)->ReleaseStringUTFChars(env, jUser, user);
    (*env)->ReleaseStringUTFChars(env, jToken, token);
}

JNIEXPORT void JNICALL
RA_FN(raLogout)(JNIEnv* env, jobject thiz) {
    rc_client_logout(ra_client);
}

// Emu thread, after pkInit: hashes the ROM the core has loaded (for GBA, the
// MD5 of the whole file) and asks the server which game that is.
JNIEXPORT jboolean JNICALL
RA_FN(raLoadGame)(JNIEnv* env, jobject thiz) {
    struct mCore* core = pkMainCore();
    if (!core || !ra_client) {
        return JNI_FALSE;
    }
    if (core->platform(core) == mPLATFORM_GB) {
        // Hashed the same way (MD5 of the file); a Color-only cart (0x143 = 0xC0) is a
        // GBC game to RA, a dual-mode one (Yellow, Gold/Silver: 0x80) a Game Boy one.
        struct GB* gb = core->board;
        if (!gb->memory.rom || gb->pristineRomSize < 0x150) {
            return JNI_FALSE;
        }
        uint32_t console = gb->memory.rom[0x143] == 0xC0 ? RC_CONSOLE_GAMEBOY_COLOR : RC_CONSOLE_GAMEBOY;
        rc_client_begin_identify_and_load_game(ra_client, console, NULL,
            (const uint8_t*) gb->memory.rom, gb->pristineRomSize, raGameLoaded, NULL);
        return JNI_TRUE;
    }
    struct GBA* gba = core->board;
    if (!gba->memory.rom || !gba->pristineRomSize) {
        return JNI_FALSE;
    }
    // A cheat's hook or ROM patch changes the ROM mGBA runs: hash the file's bytes
    // (rc_client hashes right here, before returning).
    pkRomSwapCheats();
    rc_client_begin_identify_and_load_game(ra_client, RC_CONSOLE_GAMEBOY_ADVANCE, NULL,
        (const uint8_t*) gba->memory.rom, gba->pristineRomSize, raGameLoaded, NULL);
    pkRomSwapCheats();
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
RA_FN(raUnloadGame)(JNIEnv* env, jobject thiz) {
    rc_client_unload_game(ra_client);
}

JNIEXPORT void JNICALL
RA_FN(raDoFrame)(JNIEnv* env, jobject thiz) {
    rc_client_do_frame(ra_client);
}

JNIEXPORT void JNICALL
RA_FN(raIdle)(JNIEnv* env, jobject thiz) {
    rc_client_idle(ra_client);
}

JNIEXPORT void JNICALL
RA_FN(raReset)(JNIEnv* env, jobject thiz) {
    rc_client_reset(ra_client);
}

/** Achievement / leaderboard progress to keep beside a savestate, or null (no game). */
JNIEXPORT jbyteArray JNICALL
RA_FN(raSerializeProgress)(JNIEnv* env, jobject thiz) {
    if (!rc_client_is_game_loaded(ra_client)) {
        return NULL;
    }
    size_t size = rc_client_progress_size(ra_client);
    if (!size) {
        return NULL;
    }
    uint8_t* buf = malloc(size);
    jbyteArray out = NULL;
    if (rc_client_serialize_progress_sized(ra_client, buf, size) == RC_OK) {
        out = (*env)->NewByteArray(env, (jsize) size);
        (*env)->SetByteArrayRegion(env, out, 0, (jsize) size, (const jbyte*) buf);
    }
    free(buf);
    return out;
}

/** After a state load: restores [jData]'s progress, or resets it when null (a state from before). */
JNIEXPORT void JNICALL
RA_FN(raDeserializeProgress)(JNIEnv* env, jobject thiz, jbyteArray jData) {
    if (!jData) {
        rc_client_deserialize_progress_sized(ra_client, NULL, 0);
        return;
    }
    jsize len = (*env)->GetArrayLength(env, jData);
    jbyte* data = (*env)->GetByteArrayElements(env, jData, NULL);
    rc_client_deserialize_progress_sized(ra_client, (const uint8_t*) data, (size_t) len);
    (*env)->ReleaseByteArrayElements(env, jData, data, JNI_ABORT);
}

// The loaded game's achievements as UTF-8 text for RaNative to parse: records
// split by 0x1E, fields by 0x1F - bucket type, bucket label, id, title,
// description, points, unlocked, badge URL, locked badge URL, measured progress,
// measured percent, rarity, type. In rc_client's PROGRESS grouping order
// (recently unlocked, active challenges, almost there, locked, ..., unlocked).
JNIEXPORT jbyteArray JNICALL
RA_FN(raAchievementList)(JNIEnv* env, jobject thiz) {
    if (!ra_client || !rc_client_has_achievements(ra_client)) {
        return NULL;
    }
    rc_client_achievement_list_t* list = rc_client_create_achievement_list(ra_client,
        RC_CLIENT_ACHIEVEMENT_CATEGORY_CORE, RC_CLIENT_ACHIEVEMENT_LIST_GROUPING_PROGRESS);
    if (!list) {
        return NULL;
    }
    size_t cap = 4096, len = 0;
    char* out = malloc(cap);
    for (uint32_t b = 0; b < list->num_buckets; b++) {
        const rc_client_achievement_bucket_t* bucket = &list->buckets[b];
        for (uint32_t i = 0; i < bucket->num_achievements; i++) {
            const rc_client_achievement_t* a = bucket->achievements[i];
            // rc_client's own placeholders ("Warning: Unknown Emulator" for a client the
            // server doesn't recognise yet) - its game summary skips them too.
            if (a->id >= RA_WARNING_ACHIEVEMENT_ID) {
                continue;
            }
            for (;;) {
                int n = snprintf(out + len, cap - len, "%u\x1F%s\x1F%u\x1F%s\x1F%s\x1F%u\x1F%u\x1F%s\x1F%s\x1F%s\x1F%.3f\x1F%.3f\x1F%u\x1E",
                    (unsigned) bucket->bucket_type, bucket->label ? bucket->label : "",
                    (unsigned) a->id, a->title ? a->title : "", a->description ? a->description : "",
                    (unsigned) a->points, (unsigned) a->unlocked,
                    a->badge_url ? a->badge_url : "", a->badge_locked_url ? a->badge_locked_url : "",
                    a->measured_progress, a->measured_percent, a->rarity, (unsigned) a->type);
                if (n >= 0 && (size_t) n < cap - len) {
                    len += (size_t) n;
                    break;
                }
                cap *= 2;
                out = realloc(out, cap);
            }
        }
    }
    rc_client_destroy_achievement_list(list);
    jbyteArray arr = (*env)->NewByteArray(env, (jsize) len);
    (*env)->SetByteArrayRegion(env, arr, 0, (jsize) len, (const jbyte*) out);
    free(out);
    return arr;
}

// Debug: one line on what rc_client sees - rich presence (built from game memory, so
// it shows whether reads land), memory reads so far / short ones, and achievements by
// state (active / unlocked / disabled / inactive), naming every one that isn't active.
JNIEXPORT jstring JNICALL
RA_FN(raDebugStatus)(JNIEnv* env, jobject thiz) {
    char out[2048];
    char rp[256];
    rp[0] = '\0';
    if (!ra_client || !rc_client_is_game_loaded(ra_client)) {
        return raStr(env, "no game");
    }
    rc_client_get_rich_presence_message(ra_client, rp, sizeof(rp));
    int counts[4] = { 0, 0, 0, 0 };
    char odd[1024];
    size_t oddLen = 0;
    odd[0] = '\0';
    rc_client_achievement_list_t* list = rc_client_create_achievement_list(ra_client,
        RC_CLIENT_ACHIEVEMENT_CATEGORY_CORE_AND_UNOFFICIAL, RC_CLIENT_ACHIEVEMENT_LIST_GROUPING_LOCK_STATE);
    if (list) {
        for (uint32_t b = 0; b < list->num_buckets; b++) {
            for (uint32_t i = 0; i < list->buckets[b].num_achievements; i++) {
                const rc_client_achievement_t* a = list->buckets[b].achievements[i];
                if (a->state < 4) {
                    counts[a->state]++;
                }
                if (a->state != RC_CLIENT_ACHIEVEMENT_STATE_ACTIVE && oddLen < sizeof(odd) - 64) {
                    int n = snprintf(odd + oddLen, sizeof(odd) - oddLen, " [%u s%u %s]",
                        (unsigned) a->id, (unsigned) a->state, a->title ? a->title : "");
                    if (n > 0) {
                        oddLen += (size_t) n;
                    }
                }
            }
        }
        rc_client_destroy_achievement_list(list);
    }
    snprintf(out, sizeof(out), "rp=\"%s\" reads=%u short=%u last_short=$%06X active=%d unlocked=%d disabled=%d inactive=%d%s",
        rp, (unsigned) ra_reads, (unsigned) ra_shortReads, (unsigned) ra_lastShortAddr,
        counts[RC_CLIENT_ACHIEVEMENT_STATE_ACTIVE], counts[RC_CLIENT_ACHIEVEMENT_STATE_UNLOCKED],
        counts[RC_CLIENT_ACHIEVEMENT_STATE_DISABLED], counts[RC_CLIENT_ACHIEVEMENT_STATE_INACTIVE], odd);
    return raStr(env, out);
}

// A growable text buffer for the record lists below.
typedef struct { char* p; size_t len, cap; } ra_text;

static void raTextAppend(ra_text* t, const char* fmt, ...) __attribute__((format(printf, 2, 3)));
static void raTextAppend(ra_text* t, const char* fmt, ...) {
    for (;;) {
        va_list ap;
        va_start(ap, fmt);
        int n = vsnprintf(t->p + t->len, t->cap - t->len, fmt, ap);
        va_end(ap);
        if (n >= 0 && (size_t) n < t->cap - t->len) {
            t->len += (size_t) n;
            return;
        }
        t->cap *= 2;
        t->p = realloc(t->p, t->cap);
    }
}

static jbyteArray raTextToBytes(JNIEnv* env, ra_text* t) {
    jbyteArray arr = (*env)->NewByteArray(env, (jsize) t->len);
    (*env)->SetByteArrayRegion(env, arr, 0, (jsize) t->len, (const jbyte*) t->p);
    free(t->p);
    return arr;
}

// The loaded game's leaderboards, records split by 0x1E, fields by 0x1F: bucket label,
// id, title, description, lower is better.
JNIEXPORT jbyteArray JNICALL
RA_FN(raLeaderboardList)(JNIEnv* env, jobject thiz) {
    if (!ra_client || !rc_client_has_leaderboards(ra_client)) {
        return NULL;
    }
    rc_client_leaderboard_list_t* list = rc_client_create_leaderboard_list(ra_client, RC_CLIENT_LEADERBOARD_LIST_GROUPING_NONE);
    if (!list) {
        return NULL;
    }
    ra_text t = { malloc(4096), 0, 4096 };
    for (uint32_t b = 0; b < list->num_buckets; b++) {
        const rc_client_leaderboard_bucket_t* bucket = &list->buckets[b];
        for (uint32_t i = 0; i < bucket->num_leaderboards; i++) {
            const rc_client_leaderboard_t* l = bucket->leaderboards[i];
            raTextAppend(&t, "%s\x1F%u\x1F%s\x1F%s\x1F%u\x1E", bucket->label ? bucket->label : "", (unsigned) l->id,
                l->title ? l->title : "", l->description ? l->description : "", (unsigned) l->lower_is_better);
        }
    }
    rc_client_destroy_leaderboard_list(list);
    return raTextToBytes(env, &t);
}

// userdata for a fetch: Kotlin's request token, and which half of the page it is.
typedef struct { int token; int aroundUser; } ra_fetch;

static void raEntriesFetched(int result, const char* error_message, rc_client_leaderboard_entry_list_t* list,
                             rc_client_t* client, void* userdata) {
    (void) client;
    ra_fetch* f = userdata;
    JNIEnv* env = raEnv();
    jbyteArray bytes = NULL;
    jint total = 0, userIndex = -1;
    if (list) {
        // Records (0x1E) of fields (0x1F): rank, user, score as the game shows it.
        ra_text t = { malloc(1024), 0, 1024 };
        for (uint32_t i = 0; i < list->num_entries; i++) {
            const rc_client_leaderboard_entry_t* e = &list->entries[i];
            raTextAppend(&t, "%u\x1F%s\x1F%s\x1E", (unsigned) e->rank, e->user ? e->user : "", e->display);
        }
        bytes = raTextToBytes(env, &t);
        total = (jint) list->total_entries;
        userIndex = (jint) list->user_index;
        rc_client_destroy_leaderboard_entry_list(list);
    }
    jstring err = raStr(env, error_message);
    raCallback(env, ra_onLeaderboardEntries, (jint) f->token, (jboolean) (f->aroundUser != 0),
                                 (jint) result, err, bytes, total, userIndex);
    raDeleteLocal(env, err);
    raDeleteLocal(env, bytes);
    free(f);
}

// Asks the server for [count] entries of leaderboard [id]: from rank [first], or
// (aroundUser) centred on the signed-in player. Answers in RaNative.onLeaderboardEntries.
JNIEXPORT void JNICALL
RA_FN(raFetchLeaderboard)(JNIEnv* env, jobject thiz, jint token, jint id, jboolean aroundUser, jint first, jint count) {
    if (!ra_client) {
        return;
    }
    ra_fetch* f = malloc(sizeof(*f));
    f->token = token;
    f->aroundUser = aroundUser;
    if (aroundUser) {
        rc_client_begin_fetch_leaderboard_entries_around_user(ra_client, (uint32_t) id, (uint32_t) count, raEntriesFetched, f);
    } else {
        rc_client_begin_fetch_leaderboard_entries(ra_client, (uint32_t) id, (uint32_t) first, (uint32_t) count, raEntriesFetched, f);
    }
}

static void raAllProgressFetched(int result, const char* error_message, rc_client_all_user_progress_t* list,
                                 rc_client_t* client, void* userdata) {
    (void) error_message;
    (void) client;
    JNIEnv* env = raEnv();
    jbyteArray bytes = NULL;
    if (list) {
        // Records (0x1E) of fields (0x1F): game id, achievements, unlocked (softcore), unlocked (hardcore).
        ra_text t = { malloc(4096), 0, 4096 };
        for (uint32_t i = 0; i < list->num_entries; i++) {
            const rc_client_all_user_progress_entry_t* e = &list->entries[i];
            raTextAppend(&t, "%u\x1F%u\x1F%u\x1F%u\x1E", (unsigned) e->game_id, (unsigned) e->num_achievements,
                (unsigned) e->num_unlocked_achievements, (unsigned) e->num_unlocked_achievements_hardcore);
        }
        bytes = raTextToBytes(env, &t);
        rc_client_destroy_all_user_progress(list);
    }
    raCallback(env, ra_onAllProgress, (jint) (intptr_t) userdata, (jint) result, bytes);
    raDeleteLocal(env, bytes);
}

// The signed-in player's unlock counts for every GBA game they've played; answers in
// RaNative.onAllProgress (the library's INFO).
JNIEXPORT void JNICALL
RA_FN(raFetchAllProgress)(JNIEnv* env, jobject thiz, jint token) {
    if (ra_client) {
        rc_client_begin_fetch_all_user_progress(ra_client, RC_CONSOLE_GAMEBOY_ADVANCE, raAllProgressFetched, (void*) (intptr_t) token);
    }
}

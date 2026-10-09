// One read of the running game: party, battle, place and money - a slice of
// the app's Telemetry, read the way readNativeTelemetry() does
// (NativeReader.kt) with the mon decoding of Gen3Mon.kt.
#ifndef PD_SNAPSHOT_H
#define PD_SNAPSHOT_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "pd_game.h"

// Reads len bytes of the GBA bus at addr into out; false if it can't.
typedef bool (*pd_read_fn)(void* ctx, uint32_t addr, void* out, size_t len);

#define PD_PARTY_SIZE 6
#define PD_NUM_MOVES 4

enum pd_gender {
    PD_GENDER_NONE,
    PD_GENDER_MALE,
    PD_GENDER_FEMALE,
};

struct pd_mon {
    int species;      // internal species id; PD_SPECIES_EGG for an egg
    char nickname[32]; // UTF-8, decoded from the game's charset
    enum pd_gender gender; // the party menu's mark (none for eggs, genderless, "NIDORAN♀")
    int level, hp, maxHp;
    uint32_t status;
    int moves[PD_NUM_MOVES];
    int pp[PD_NUM_MOVES];
    uint32_t personality;
};

struct pd_battle_mon {
    int species;
    int level, hp, maxHp;
    int type1, type2;
    uint32_t status;
    int moves[PD_NUM_MOVES];
    int pp[PD_NUM_MOVES];
};

#define PD_POS_PLAYER_LEFT 0
#define PD_POS_OPPONENT_LEFT 1

// A pocket's slots: every pocket of FireRed's and Emerald's bag holds at most 64.
#define PD_POCKET_SLOTS 64

struct pd_item {
    uint16_t id, quantity;
};

struct pd_pocket_items {
    int count;
    struct pd_item items[PD_POCKET_SLOTS];
};

struct pd_snapshot {
    bool valid;
    uint32_t frame;  // gMain's vblank counter
    int partyCount;
    struct pd_mon party[PD_PARTY_SIZE];
    bool inBattle, isDouble, isTrainer;
    struct pd_battle_mon battlers[4]; // by battler position; species 0 = none
    int mapsec;
    long money; // -1 = unknown
    // By PD_POCKET_*; bagOk = the pockets read (not just empty).
    bool bagOk;
    struct pd_pocket_items bag[PD_POCKET_COUNT];
};

void pd_snapshot_read(struct pd_snapshot* s, const struct pd_game* g, pd_read_fn read, void* ctx);

// Decodes one struct Pokemon (100 bytes); false for an empty slot.
bool pd_decode_party_mon(const uint8_t* raw, struct pd_mon* out);

// The item's name / bag description in the game's own words ("" if unknown).
const char* pd_item_name(const struct pd_game* g, int item);
const char* pd_item_description(const struct pd_game* g, int item);
// The game's title for a pocket ("POKé BALLS").
const char* pd_pocket_name(int pocket);

// The map section's name ("" if unknown).
const char* pd_mapsec_name(const struct pd_game* g, int mapsec);
// "PSN", "SLP", ... or "" (the app's statusLabel).
const char* pd_status_label(uint32_t status);

#endif

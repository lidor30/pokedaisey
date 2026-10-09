// Which game is running and where its RAM globals live - the C twin of the
// app's NativeConfig (app/.../companion/data/NativeReader.kt). Only the
// fields this port reads so far; the addresses are copied from there (each
// comes from a pret decomp build byte-identical to retail), keep in sync.
#ifndef PD_GAME_H
#define PD_GAME_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

enum pd_game_kind {
    PD_GAME_NONE = 0,
    PD_GAME_FIRERED,  // FireRed and LeafGreen (LeafGreen runs as FireRed, like the app)
    PD_GAME_EMERALD,
};

struct pd_config {
    uint32_t playerParty;
    uint32_t playerPartyCount;
    uint32_t battleMons;
    uint32_t battlerPositions;
    uint32_t battlersCount;
    uint32_t battleTypeFlags;
    uint32_t gMain;
    uint32_t saveBlock1Ptr;
    uint32_t saveBlock2Ptr;
    uint32_t mapHeader;
    uint32_t encryptionKeyOff; // in SaveBlock2
    uint32_t moneyOff;         // in SaveBlock1
    uint32_t enemyParty;       // 0 = unknown
};

struct pd_game {
    enum pd_game_kind kind;
    char code[5];       // ROM header game code, e.g. "BPRE"
    int revision;       // ROM header 0xBC
    const char* title;  // "Pokémon FireRed"
    const struct pd_config* cfg; // NULL = detected but not supported
    const char* unsupported;     // why, when cfg is NULL
};

// Reads the ROM header (the first 0xC0 bytes of the ROM) and its size.
void pd_game_detect(struct pd_game* g, const uint8_t* header, size_t romSize);

#endif

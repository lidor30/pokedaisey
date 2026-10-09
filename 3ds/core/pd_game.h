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
    // The battle's FOE TEAM: gEnemyParty (6 struct Pokemon), gBattlerPartyIndexes
    // (u16 per battler: which one is out) and gBattleStruct (a pointer) ->
    // monToSwitchIntoId[battler] (the trainer's next pick). 0 = unknown.
    uint32_t enemyParty;
    uint32_t battlerPartyIndexes;
    uint32_t battleStructPtr;
    uint32_t monToSwitchIntoOff;
    // The party menu's icons in the ROM: gMonIconTable (a tiles pointer per
    // species), gMonIconPaletteIndices (u8) and gMonIconPaletteTable
    // ({data, tag}, 8 bytes). 0 = none.
    uint32_t monIconTable, monIconPaletteIndices, monIconPaletteTable;
    // gBagPockets: five {slots pointer, capacity} pairs, 8 bytes apart;
    // bagOrder[i] is the pocket (PD_POCKET_*) the game keeps at index i.
    uint32_t bagPockets;
    uint8_t bagOrder[5];

    // POKéDEX (the app's PokedexTables): ROM tables ...
    uint32_t dexEntries;       // gPokedexEntries, by national number
    uint32_t dexEntryStride;
    uint32_t speciesInfo;      // gBaseStats, 28 bytes per species
    uint32_t speciesToNational; // u16 per species from species 1
    uint32_t abilityNames;     // 13 bytes each
    uint32_t frontPics;        // {LZ77 pointer, size, tag} per species
    uint32_t palettes;         // {LZ77 pointer, tag} per species
    uint32_t regionalOrder;    // u16 national numbers; 0 = national 1..regionalCount
    int regionalCount;
    const char* regionName;
    // ... and the save: SaveBlock2's owned / seen flags, SaveBlock1's two
    // copies of seen (all must agree), the National Dex byte.
    uint32_t dexSeenCopy1, dexSeenCopy2; // in SaveBlock1
    uint8_t nationalMagicOff, nationalMagic; // SaveBlock2 + 0x18 + off == magic

    // GUIDE (the app's GuideTables): ROM tables and SaveBlock1 offsets.
    uint32_t trainers;    // gTrainers, 0x28 bytes each
    uint32_t learnsets;   // gLevelUpLearnsets, a pointer per species
    uint32_t wildHeaders; // gWildMonHeaders, 20 bytes each
    uint32_t flagsOff, varsOff, flagBytes; // in SaveBlock1
    int probeTrainer;     // checks the tables match the ROM ...
    const char* probeName; // ... by this trainer's name

    // TRAINER CARD (the app's TrainerCardSave): which card (PD_CARD_*, 0 =
    // none), SaveBlock1.gameStats, the badge / POKéDEX flags, what makes the
    // National Dex count (with nationalMagic above), FireRed's sticker vars and
    // Emerald's extra stars (Battle Frontier symbols, the museum's paintings)
    // and battle points. 0 = not in this game.
    int cardStyle;
    uint32_t gameStatsOff;
    int badgeFlag, pokedexFlag;
    int nationalVar, nationalVarValue, nationalFlag;
    int stickerVar;
    int frontierSymbolFlag;
    uint32_t museumWinnersOff, frontierBpOff; // in SaveBlock1 / SaveBlock2
};

enum pd_card_style { PD_CARD_NONE, PD_CARD_KANTO, PD_CARD_HOENN };

// The bag's pockets, in a fixed order of our own (the app's QOL_POCKET_* ids).
enum pd_pocket {
    PD_POCKET_ITEMS,
    PD_POCKET_BALLS,
    PD_POCKET_TMHM,
    PD_POCKET_BERRIES,
    PD_POCKET_KEY,
    PD_POCKET_COUNT,
};

struct pd_game {
    enum pd_game_kind kind;
    char code[5];       // ROM header game code, e.g. "BPRE"
    int revision;       // ROM header 0xBC
    const char* title;  // "Pokémon FireRed"
    const struct pd_config* cfg; // NULL = detected but not supported
    const char* unsupported;     // why, when cfg is NULL
    // The ROM's bytes (0x08000000 on the bus), for the tabs that read its
    // tables and art; the host sets them once the ROM is loaded (NULL = none).
    const uint8_t* rom;
    size_t romSize;
    unsigned loadId; // new on every pd_game_detect (see pd_rom_id)
};

// Which ROM the tabs' caches were built from: 0 = none, else new for every
// game loaded. Not the ROM pointer - mGBA's 3DS build loads every game into
// the same fixed buffer.
unsigned pd_rom_id(const struct pd_game* g);

// Reads from the ROM at a bus address (0x08xxxxxx); false if out of range.
bool pd_rom_read(const struct pd_game* g, uint32_t addr, void* out, size_t len);
// A pointer into the ROM at a bus address, with at least len bytes after it.
const uint8_t* pd_rom_at(const struct pd_game* g, uint32_t addr, size_t len);

// Reads the ROM header (the first 0xC0 bytes of the ROM) and its size.
void pd_game_detect(struct pd_game* g, const uint8_t* header, size_t romSize);

#endif

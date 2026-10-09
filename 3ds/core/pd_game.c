#include "pd_game.h"

#include <string.h>

// NATIVE_FIRERED_REV0 / _REV1 share every RAM address and save offset below,
// and LeafGreen's maps put each RAM global at FireRed's address for the same
// revision (NATIVE_LEAFGREEN_REV0/1). gEnemyParty is from the rev 1 map.
#define FIRERED_RAM                                                                                     \
    .playerParty = 0x02024284,                                                                          \
    .playerPartyCount = 0x02024029,                                                                     \
    .battleMons = 0x02023BE4,                                                                           \
    .battlerPositions = 0x02023BD6,                                                                     \
    .battlersCount = 0x02023BCC,                                                                        \
    .battleTypeFlags = 0x02022B4C,                                                                      \
    .gMain = 0x030030F0,                                                                                \
    .saveBlock1Ptr = 0x03005008,                                                                        \
    .saveBlock2Ptr = 0x0300500C,                                                                        \
    .mapHeader = 0x02036DFC,                                                                            \
    .encryptionKeyOff = 0xF20,                                                                          \
    .moneyOff = 0x290,                                                                                  \
    .enemyParty = 0x0202402C,                                                                           \
    /* FireRed's item.c: Items, Key Items, Poke Balls, TM Case, Berry Pouch. */                         \
    .bagPockets = 0x0203988C,                                                                           \
    .bagOrder = { PD_POCKET_ITEMS, PD_POCKET_KEY, PD_POCKET_BALLS, PD_POCKET_TMHM, PD_POCKET_BERRIES }, \
    /* FIRERED_DEX_FLAGS (Pokedex.kt) and GuideTables' SaveBlock1 offsets. */                            \
    .dexSeenCopy1 = 0x5F8,                                                                              \
    .dexSeenCopy2 = 0x3A18,                                                                             \
    .nationalMagicOff = 3,                                                                              \
    .nationalMagic = 0xB9,                                                                              \
    .flagsOff = 0xEE0,                                                                                  \
    .varsOff = 0x1000,                                                                                  \
    .flagBytes = 0x120

// FireRed rev 1 (sha1 dd5945db..., what pret's firered_rev1 builds): its ROM
// tables too - POKEDEX_FIRERED_REV1 and GUIDE_TABLES_FIRERED_REV1.
static const struct pd_config FIRERED_REV1 = {
    FIRERED_RAM,
    .dexEntries = 0x0844E8B0,
    .dexEntryStride = 0x24,
    .speciesInfo = 0x082547F4,
    .speciesToNational = 0x0825205E,
    .abilityNames = 0x0824FCB0,
    .frontPics = 0x0823511C,
    .palettes = 0x0823737C,
    .regionalOrder = 0,
    .regionalCount = 151,
    .regionName = "KANTO",
    .trainers = 0x0823EB38,
    .learnsets = 0x0825D824,
    .wildHeaders = 0x083C9D28,
    .probeTrainer = 414,
    .probeName = "BROCK",
};

// FireRed rev 0 and LeafGreen: the tabs that read RAM (and the MAP, whose art
// is found by fingerprint); their ROM tables for the POKéDEX and the GUIDE's
// live pages aren't ported yet.
static const struct pd_config FIRERED_RAM_ONLY = {
    FIRERED_RAM,
};

// NATIVE_EMERALD_RETAIL (English, BPEE; sha1 f3ae0881..., what pret's
// pokeemerald builds), with POKEDEX_EMERALD and GUIDE_TABLES_EMERALD.
static const struct pd_config EMERALD = {
    .playerParty = 0x020244EC,
    .playerPartyCount = 0x020244E9,
    .battleMons = 0x02024084,
    .battlerPositions = 0x02024076,
    .battlersCount = 0x0202406C,
    .battleTypeFlags = 0x02022FEC,
    .gMain = 0x030022C0,
    .saveBlock1Ptr = 0x03005D8C,
    .saveBlock2Ptr = 0x03005D90,
    .mapHeader = 0x02037318,
    .encryptionKeyOff = 0xAC,
    .moneyOff = 0x490,
    .enemyParty = 0x02024744,
    // Emerald's item.c: Items, Poke Balls, TMs & HMs, Berries, Key Items.
    .bagPockets = 0x02039DD8,
    .bagOrder = { PD_POCKET_ITEMS, PD_POCKET_BALLS, PD_POCKET_TMHM, PD_POCKET_BERRIES, PD_POCKET_KEY },
    .dexEntries = 0x0856B5B0,
    .dexEntryStride = 0x20,
    .speciesInfo = 0x083203CC,
    .speciesToNational = 0x0831DC82,
    .abilityNames = 0x0831B6DB,
    .frontPics = 0x0830A18C,
    .palettes = 0x08303678,
    .regionalOrder = 0x0831DFB8,
    .regionalCount = 202,
    .regionName = "HOENN",
    .dexSeenCopy1 = 0x988,
    .dexSeenCopy2 = 0x3B24,
    .nationalMagicOff = 2,
    .nationalMagic = 0xDA,
    .trainers = 0x08310030,
    .learnsets = 0x0832937C,
    .wildHeaders = 0x08552D48,
    .flagsOff = 0x1270,
    .varsOff = 0x139C,
    .flagBytes = 0x12C,
    .probeTrainer = 265,
    .probeName = "ROXANNE",
};

const uint8_t* pd_rom_at(const struct pd_game* g, uint32_t addr, size_t len) {
    if (!g->rom || addr < 0x08000000) return NULL;
    size_t off = addr - 0x08000000;
    if (off > g->romSize || len > g->romSize - off) return NULL;
    return g->rom + off;
}

unsigned pd_rom_id(const struct pd_game* g) {
    return g->rom ? g->loadId : 0;
}

bool pd_rom_read(const struct pd_game* g, uint32_t addr, void* out, size_t len) {
    const uint8_t* p = pd_rom_at(g, addr, len);
    if (!p) return false;
    memcpy(out, p, len);
    return true;
}

void pd_game_detect(struct pd_game* g, const uint8_t* header, size_t romSize) {
    static unsigned loads;
    memset(g, 0, sizeof(*g));
    g->loadId = ++loads ? loads : ++loads;
    memcpy(g->code, header + 0xAC, 4);
    g->code[4] = 0;
    g->revision = header[0xBC];

    if (!strcmp(g->code, "BPRE") || !strcmp(g->code, "BPGE")) {
        g->kind = PD_GAME_FIRERED;
        g->title = g->code[2] == 'R' ? "Pokémon FireRed" : "Pokémon LeafGreen";
        g->cfg = g->code[2] == 'R' && g->revision == 1 ? &FIRERED_REV1 : &FIRERED_RAM_ONLY;
    } else if (!strcmp(g->code, "BPEE")) {
        g->kind = PD_GAME_EMERALD;
        g->title = "Pokémon Emerald";
        g->cfg = &EMERALD;
    } else {
        g->title = "Unknown game";
        g->unsupported = "Not a game the 3DS companion knows yet";
        return;
    }
    if (g->revision > 1) {
        g->cfg = NULL;
        g->unsupported = "This revision isn't supported yet";
    } else if (romSize > 0x1000000) {
        // A >16 MB FireRed / Emerald is a ROM hack: the app tells them apart
        // by SHA1 (Poller.detect) - not ported yet.
        g->cfg = NULL;
        g->title = g->kind == PD_GAME_FIRERED ? "FireRed ROM hack" : "Emerald ROM hack";
        g->unsupported = "ROM hacks aren't supported on 3DS yet";
    }
}

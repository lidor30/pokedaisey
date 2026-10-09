#include "pd_game.h"

#include <string.h>

// NATIVE_FIRERED_REV0 / _REV1 share every RAM address below, and LeafGreen's
// maps put each RAM global at FireRed's address for the same revision
// (NATIVE_LEAFGREEN_REV0/1). gEnemyParty is from the rev 1 map.
static const struct pd_config FIRERED = {
    .playerParty = 0x02024284,
    .playerPartyCount = 0x02024029,
    .battleMons = 0x02023BE4,
    .battlerPositions = 0x02023BD6,
    .battlersCount = 0x02023BCC,
    .battleTypeFlags = 0x02022B4C,
    .gMain = 0x030030F0,
    .saveBlock1Ptr = 0x03005008,
    .saveBlock2Ptr = 0x0300500C,
    .mapHeader = 0x02036DFC,
    .encryptionKeyOff = 0xF20,
    .moneyOff = 0x290,
    .enemyParty = 0x0202402C,
};

// NATIVE_EMERALD_RETAIL (English, BPEE).
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
};

void pd_game_detect(struct pd_game* g, const uint8_t* header, size_t romSize) {
    memset(g, 0, sizeof(*g));
    memcpy(g->code, header + 0xAC, 4);
    g->code[4] = 0;
    g->revision = header[0xBC];

    if (!strcmp(g->code, "BPRE") || !strcmp(g->code, "BPGE")) {
        g->kind = PD_GAME_FIRERED;
        g->title = g->code[2] == 'R' ? "Pokémon FireRed" : "Pokémon LeafGreen";
        g->cfg = &FIRERED;
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

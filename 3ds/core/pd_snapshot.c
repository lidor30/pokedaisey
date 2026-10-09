#include "pd_snapshot.h"

#include <stdio.h>
#include <string.h>

#include "pd_tables.h"

#define MON_SIZE 100
#define BATTLE_MON_SIZE 0x58
#define MAP_HEADER_MAPSEC_OFF 0x14
#define BATTLE_TYPE_DOUBLE 0x0001
#define BATTLE_TYPE_TRAINER 0x0008
#define MAX_MONEY 999999

static uint16_t u16(const uint8_t* b) {
    return (uint16_t) (b[0] | (b[1] << 8));
}

static uint32_t u32(const uint8_t* b) {
    return (uint32_t) b[0] | ((uint32_t) b[1] << 8) | ((uint32_t) b[2] << 16) | ((uint32_t) b[3] << 24);
}

static bool in_ram(uint32_t a) {
    return a >= 0x02000000 && a < 0x04000000;
}

// gen3SubstructOrder[personality % 24][slot] = logical substruct
// (0 Growth, 1 Attacks, 2 EVs, 3 Misc) - the "GAEM" permutation table.
static const uint8_t SUBSTRUCT_ORDER[24][4] = {
    {0, 1, 2, 3}, {0, 1, 3, 2}, {0, 2, 1, 3}, {0, 2, 3, 1}, {0, 3, 1, 2}, {0, 3, 2, 1},
    {1, 0, 2, 3}, {1, 0, 3, 2}, {1, 2, 0, 3}, {1, 2, 3, 0}, {1, 3, 0, 2}, {1, 3, 2, 0},
    {2, 0, 1, 3}, {2, 0, 3, 1}, {2, 1, 0, 3}, {2, 1, 3, 0}, {2, 3, 0, 1}, {2, 3, 1, 0},
    {3, 0, 1, 2}, {3, 0, 2, 1}, {3, 1, 0, 2}, {3, 1, 2, 0}, {3, 2, 0, 1}, {3, 2, 1, 0},
};

// The English games' charset (charmap.txt in the pret decomps), the part a
// nickname uses.
static const char* gen3_char(uint8_t c) {
    static char one[2];
    if (c >= 0xBB && c <= 0xD4) { one[0] = (char) ('A' + c - 0xBB); one[1] = 0; return one; }
    if (c >= 0xD5 && c <= 0xEE) { one[0] = (char) ('a' + c - 0xD5); one[1] = 0; return one; }
    if (c >= 0xA1 && c <= 0xAA) { one[0] = (char) ('0' + c - 0xA1); one[1] = 0; return one; }
    switch (c) {
    case 0x00: return " ";
    case 0x1B: return "é";
    case 0xAB: return "!";
    case 0xAC: return "?";
    case 0xAD: return ".";
    case 0xAE: return "-";
    case 0xB0: return "...";
    case 0xB1: case 0xB2: return "\"";
    case 0xB3: case 0xB4: return "'";
    case 0xB5: return "M"; // ♂ - not in Pixel Operator
    case 0xB6: return "F"; // ♀
    case 0xB8: return ",";
    case 0xBA: return "/";
    default: return "?";
    }
}

static void decode_name(const uint8_t* src, int maxLen, char* out, size_t outLen) {
    size_t n = 0;
    out[0] = 0;
    for (int i = 0; i < maxLen && src[i] != 0xFF; i++) {
        const char* s = gen3_char(src[i]);
        size_t l = strlen(s);
        if (n + l + 1 > outLen) break;
        memcpy(out + n, s, l);
        n += l;
    }
    out[n] = 0;
    while (n && out[n - 1] == ' ') out[--n] = 0;
}

bool pd_decode_party_mon(const uint8_t* raw, struct pd_mon* m) {
    memset(m, 0, sizeof(*m));
    uint32_t personality = u32(raw);
    uint32_t key = personality ^ u32(raw + 4);
    uint16_t stored = u16(raw + 28);

    uint8_t block[48];
    uint16_t sum = 0;
    for (int i = 0; i < 12; i++) {
        uint32_t w = u32(raw + 0x20 + i * 4) ^ key;
        block[i * 4] = (uint8_t) w;
        block[i * 4 + 1] = (uint8_t) (w >> 8);
        block[i * 4 + 2] = (uint8_t) (w >> 16);
        block[i * 4 + 3] = (uint8_t) (w >> 24);
        sum = (uint16_t) (sum + (w & 0xFFFF) + (w >> 16));
    }
    // Retail boxes are encrypted and checksummed; an empty slot fails here.
    if (sum != stored || stored == 0) return false;

    int off[4] = {0};
    for (int slot = 0; slot < 4; slot++) off[SUBSTRUCT_ORDER[personality % 24][slot]] = slot * 12;
    m->species = u16(block + off[0]);
    if (m->species == 0) return false;
    for (int i = 0; i < PD_NUM_MOVES; i++) {
        m->moves[i] = u16(block + off[1] + i * 2);
        m->pp[i] = block[off[1] + 8 + i];
    }
    m->personality = personality;
    m->level = raw[0x54];
    m->hp = u16(raw + 0x56);
    m->maxHp = u16(raw + 0x58);
    m->status = u32(raw + 0x50);
    // BoxPokemon +0x13 flags (isBadEgg, hasSpecies, isEgg): an egg shows as
    // SPECIES_EGG, like the party menu.
    if (raw[0x13] & 0x04) m->species = PD_SPECIES_EGG;
    decode_name(raw + 0x08, 10, m->nickname, sizeof(m->nickname));
    return true;
}

static void decode_battle_mon(const uint8_t* raw, struct pd_battle_mon* b) {
    memset(b, 0, sizeof(*b));
    b->species = u16(raw);
    if (!b->species) return;
    for (int i = 0; i < PD_NUM_MOVES; i++) {
        b->moves[i] = u16(raw + 0x0C + i * 2);
        b->pp[i] = raw[0x24 + i];
    }
    b->type1 = raw[0x21];
    b->type2 = raw[0x22];
    b->hp = u16(raw + 0x28);
    b->level = raw[0x2A];
    b->maxHp = u16(raw + 0x2C);
    b->status = u32(raw + 0x4C);
}

static bool read_u32(pd_read_fn read, void* ctx, uint32_t addr, uint32_t* out) {
    uint8_t b[4];
    if (!read(ctx, addr, b, 4)) return false;
    *out = u32(b);
    return true;
}

void pd_snapshot_read(struct pd_snapshot* s, const struct pd_game* g, pd_read_fn read, void* ctx) {
    memset(s, 0, sizeof(*s));
    s->money = -1;
    const struct pd_config* cfg = g->cfg;
    if (!cfg) return;
    s->valid = true;
    read_u32(read, ctx, cfg->gMain + 0x24, &s->frame);

    // --- party ---
    uint8_t count = 0;
    read(ctx, cfg->playerPartyCount, &count, 1);
    if (count > PD_PARTY_SIZE) count = PD_PARTY_SIZE;
    uint8_t raw[PD_PARTY_SIZE * MON_SIZE];
    if (count && read(ctx, cfg->playerParty, raw, count * MON_SIZE)) {
        for (int i = 0; i < count; i++) {
            if (pd_decode_party_mon(raw + i * MON_SIZE, &s->party[s->partyCount])) s->partyCount++;
        }
    }

    // --- battle --- (gMain.inBattle is bit 1 of the byte at +0x439)
    uint8_t flags = 0;
    read(ctx, cfg->gMain + 0x439, &flags, 1);
    s->inBattle = (flags & 0x02) != 0;
    if (s->inBattle) {
        uint32_t type = 0;
        read_u32(read, ctx, cfg->battleTypeFlags, &type);
        s->isDouble = (type & BATTLE_TYPE_DOUBLE) != 0;
        s->isTrainer = (type & BATTLE_TYPE_TRAINER) != 0;
        uint8_t n = 4, pos[4] = {0, 1, 2, 3};
        read(ctx, cfg->battlersCount, &n, 1);
        if (n < 1 || n > 4) n = 4;
        read(ctx, cfg->battlerPositions, pos, 4);
        uint8_t mons[4 * BATTLE_MON_SIZE];
        if (read(ctx, cfg->battleMons, mons, sizeof(mons))) {
            for (int b = 0; b < n; b++) {
                int p = pos[b] < 4 ? pos[b] : b;
                decode_battle_mon(mons + b * BATTLE_MON_SIZE, &s->battlers[p]);
            }
        }
    }

    // --- location ---
    uint8_t sec = 0;
    read(ctx, cfg->mapHeader + MAP_HEADER_MAPSEC_OFF, &sec, 1);
    s->mapsec = sec;

    // --- money: SaveBlock1.money XOR SaveBlock2.encryptionKey ---
    uint32_t sb1 = 0, sb2 = 0, money = 0, key = 0;
    if (read_u32(read, ctx, cfg->saveBlock1Ptr, &sb1) && in_ram(sb1) &&
        read_u32(read, ctx, cfg->saveBlock2Ptr, &sb2) && in_ram(sb2) &&
        read_u32(read, ctx, sb1 + cfg->moneyOff, &money) &&
        read_u32(read, ctx, sb2 + cfg->encryptionKeyOff, &key)) {
        uint32_t v = money ^ key;
        if (v <= MAX_MONEY) s->money = (long) v;
    }
}

const char* pd_mapsec_name(const struct pd_game* g, int mapsec) {
    // Emerald's map sections start at 0 (Littleroot); FireRed's Kanto ones at 88.
    const char* const* t = g->kind == PD_GAME_EMERALD ? pd_mapsec_emerald : pd_mapsec_firered;
    int n = g->kind == PD_GAME_EMERALD ? pd_mapsec_emerald_count : pd_mapsec_firered_count;
    if (mapsec >= 0 && mapsec < n && t[mapsec]) return t[mapsec];
    return "";
}

const char* pd_status_label(uint32_t status) {
    if (status & 0x7) return "SLP";
    if (status & (1 << 5)) return "FRZ";
    if (status & (1 << 4)) return "BRN";
    if (status & (1 << 6)) return "PAR";
    if (status & (1 << 7)) return "TOX";
    if (status & (1 << 3)) return "PSN";
    return "";
}

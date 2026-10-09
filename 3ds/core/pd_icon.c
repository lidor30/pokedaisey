#include "pd_icon.h"

#include <string.h>

#include "pd_tables.h"

#define CLEAR 0xFF000000u
#define CACHE 24 // a party, a foe team and a few more

struct icon {
    unsigned rom;
    int species; // 0 = empty slot
    bool ok;
    uint32_t px[PD_ICON * PD_ICON];
};

static struct icon cache[CACHE];
static int nextSlot;

static uint32_t u32le(const uint8_t* p) {
    return (uint32_t) p[0] | (uint32_t) p[1] << 8 | (uint32_t) p[2] << 16 | (uint32_t) p[3] << 24;
}

static uint32_t bgr555(uint16_t c) {
    uint32_t r = c & 0x1F, g = (c >> 5) & 0x1F, b = (c >> 10) & 0x1F;
    return (r << 3 | r >> 2) << 16 | (g << 3 | g >> 2) << 8 | (b << 3 | b >> 2);
}

static bool decode(const struct pd_game* g, int species, uint32_t* out) {
    const struct pd_config* cfg = g->cfg;
    if (!cfg || !cfg->monIconTable || species <= 0 || species > PD_SPECIES_EGG) return false;
    uint8_t w[4], idx;
    if (!pd_rom_read(g, cfg->monIconTable + (uint32_t) species * 4, w, 4)) return false;
    const uint8_t* tiles = pd_rom_at(g, u32le(w), 16 * 32);
    if (!tiles || !pd_rom_read(g, cfg->monIconPaletteIndices + (uint32_t) species, &idx, 1) || idx > 15) return false;
    if (!pd_rom_read(g, cfg->monIconPaletteTable + (uint32_t) idx * 8, w, 4)) return false;
    const uint8_t* pal = pd_rom_at(g, u32le(w), 32);
    if (!pal) return false;
    uint32_t colours[16];
    for (int i = 0; i < 16; i++) colours[i] = bgr555((uint16_t) (pal[i * 2] | pal[i * 2 + 1] << 8));
    // 4x4 tiles of 8x8, row by row; a byte holds two pixels, the left one low.
    for (int t = 0; t < 16; t++) {
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                uint8_t b = tiles[t * 32 + y * 4 + x / 2];
                int v = x & 1 ? b >> 4 : b & 0x0F;
                out[((t / 4) * 8 + y) * PD_ICON + (t % 4) * 8 + x] = v ? colours[v] : CLEAR;
            }
        }
    }
    return true;
}

static const struct icon* icon_for(const struct pd_game* g, int species) {
    unsigned rom = pd_rom_id(g);
    for (int i = 0; i < CACHE; i++) {
        if (cache[i].species == species && cache[i].rom == rom) return &cache[i];
    }
    struct icon* e = &cache[nextSlot];
    nextSlot = (nextSlot + 1) % CACHE;
    e->rom = rom;
    e->species = species;
    e->ok = decode(g, species, e->px);
    return e;
}

bool pd_draw_mon_icon(struct pd_canvas* c, const struct pd_game* g, int species, int x, int y, bool faded) {
    if (!pd_rom_id(g)) return false;
    const struct icon* e = icon_for(g, species);
    if (!e->ok) return false;
    for (int py = 0; py < PD_ICON; py++) {
        int sy = y + py;
        if (sy < c->cy0 || sy >= c->cy1) continue;
        for (int px = 0; px < PD_ICON; px++) {
            int sx = x + px;
            uint32_t v = e->px[py * PD_ICON + px];
            if (v == CLEAR || sx < c->cx0 || sx >= c->cx1) continue;
            uint32_t* d = &c->px[sy * c->w + sx];
            if (faded) {
                uint32_t r = (((v >> 16) & 0xFF) * 35 + ((*d >> 16) & 0xFF) * 65) / 100;
                uint32_t gg = (((v >> 8) & 0xFF) * 35 + ((*d >> 8) & 0xFF) * 65) / 100;
                uint32_t b = ((v & 0xFF) * 35 + (*d & 0xFF) * 65) / 100;
                v = r << 16 | gg << 8 | b;
            }
            *d = v;
        }
    }
    return true;
}

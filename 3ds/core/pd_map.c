#include "pd_map.h"

#include <stdlib.h>
#include <string.h>

// --- finding blobs (RomArt.find) ---

static uint64_t u64le(const uint8_t* p) {
    uint64_t v = 0;
    for (int i = 7; i >= 0; i--) v = v << 8 | p[i];
    return v;
}

static uint64_t fmix(uint64_t k) {
    k ^= k >> 33;
    k *= 0xFF51AFD7ED558CCDull;
    k ^= k >> 33;
    k *= 0xC4CEB9FE1A85EC53ull;
    k ^= k >> 33;
    return k;
}

static uint64_t head_hash(const uint8_t* p) {
    return fmix(u64le(p) ^ fmix(u64le(p + 8)));
}

static uint16_t prefilter(uint64_t a) {
    return (uint16_t) ((a * 0x9E3779B97F4A7C15ull) >> 48);
}

uint32_t pd_crc32(const uint8_t* data, size_t len) {
    static uint32_t table[256];
    if (!table[1]) {
        for (uint32_t n = 0; n < 256; n++) {
            uint32_t c = n;
            for (int k = 0; k < 8; k++) c = (c & 1) ? 0xEDB88320u ^ (c >> 1) : c >> 1;
            table[n] = c;
        }
    }
    uint32_t c = 0xFFFFFFFFu;
    for (size_t i = 0; i < len; i++) c = table[(c ^ data[i]) & 0xFF] ^ (c >> 8);
    return c ^ 0xFFFFFFFFu;
}

size_t pd_lz77(const uint8_t* src, size_t srcLen, uint8_t* dst, size_t dstLen) {
    if (srcLen < 4 || src[0] != 0x10) return 0;
    size_t size = src[1] | (size_t) src[2] << 8 | (size_t) src[3] << 16;
    if (size != dstLen) return 0;
    size_t in = 4, out = 0;
    while (out < size) {
        if (in >= srcLen) return 0;
        uint8_t flags = src[in++];
        for (int bit = 7; bit >= 0 && out < size; bit--) {
            if (flags >> bit & 1) {
                if (in + 1 >= srcLen) return 0;
                unsigned d = (unsigned) src[in] << 8 | src[in + 1];
                in += 2;
                size_t disp = (d & 0xFFF) + 1, len = (d >> 12) + 3;
                if (disp > out) return 0;
                for (size_t k = 0; k < len && out < size; k++, out++) dst[out] = dst[out - disp];
            } else {
                if (in >= srcLen) return 0;
                dst[out++] = src[in++];
            }
        }
    }
    return size;
}

// Decodes the blob that would start at off; true if its CRC matches.
static bool decode(const uint8_t* rom, size_t romSize, size_t off, const struct pd_blob_sig* sig, uint8_t* out) {
    if (sig->lz) {
        return pd_lz77(rom + off, romSize - off, out, sig->size) == sig->size && pd_crc32(out, sig->size) == sig->crc;
    }
    if (off + sig->size > romSize) return false;
    memcpy(out, rom + off, sig->size);
    return pd_crc32(out, sig->size) == sig->crc;
}

// Scans the ROM once for every wanted blob; each found one is decoded into
// out[blob] (malloc'd, sig.size bytes), the rest left NULL.
static void find_blobs(const uint8_t* rom, size_t size, const int* wanted, int n, uint8_t** out) {
    static uint64_t bits[1024];
    memset(bits, 0, sizeof(bits));
    for (int i = 0; i < n; i++) {
        uint16_t p = pd_blob_sigs[wanted[i]].pre;
        bits[p >> 6] |= 1ull << (p & 63);
        out[wanted[i]] = NULL;
    }
    int left = n;
    for (size_t i = 0; i + 16 <= size && left > 0; i++) {
        uint16_t p = prefilter(u64le(rom + i));
        if (!(bits[p >> 6] >> (p & 63) & 1)) continue;
        uint64_t h = head_hash(rom + i);
        for (int k = 0; k < n; k++) {
            int b = wanted[k];
            const struct pd_blob_sig* sig = &pd_blob_sigs[b];
            if (out[b] || sig->head != h || i < sig->headOff) continue;
            uint8_t* buf = malloc(sig->size);
            if (buf && decode(rom, size, i - sig->headOff, sig, buf)) {
                out[b] = buf;
                left--;
            } else {
                free(buf);
            }
        }
    }
}

// --- drawing (RomArt's screen / emeraldRegionMap / sprite) ---

static uint32_t bgr555(uint16_t c) {
    uint32_t r = c & 0x1F, g = (c >> 5) & 0x1F, b = (c >> 10) & 0x1F;
    r = r << 3 | r >> 2;
    g = g << 3 | g >> 2;
    b = b << 3 | b >> 2;
    return r << 16 | g << 8 | b;
}

static void palette(const uint8_t* raw, int colours, uint32_t* out) {
    for (int i = 0; i < colours; i++) out[i] = bgr555((uint16_t) (raw[i * 2] | raw[i * 2 + 1] << 8));
}

// FireRed: 4bpp tiles, a 30x20 u16 tilemap (tile, flips, palette).
static void screen_4bpp(const uint8_t* tiles, size_t tilesSize, const uint32_t* pal, int palCount, const uint8_t* map,
                        uint32_t* out) {
    for (int ty = 0; ty < 20; ty++) {
        for (int tx = 0; tx < 30; tx++) {
            uint16_t e = (uint16_t) (map[(ty * 30 + tx) * 2] | map[(ty * 30 + tx) * 2 + 1] << 8);
            int t = e & 0x3FF, pn = e >> 12;
            bool hf = e & 0x400, vf = e & 0x800;
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    int sx = hf ? 7 - x : x, sy = vf ? 7 - y : y;
                    size_t at = (size_t) t * 32 + sy * 4 + sx / 2;
                    int v = at < tilesSize ? (sx & 1 ? tiles[at] >> 4 : tiles[at] & 0xF) : 0;
                    int ci = pn * 16 + v;
                    out[(ty * 8 + y) * PD_MAP_W + tx * 8 + x] = ci < palCount ? pal[ci] : pal[0];
                }
            }
        }
    }
}

// Emerald: 8bpp tiles, a 64-wide u8 affine map, palette loaded at slot 7 (112).
static void screen_8bpp(const uint8_t* tiles, size_t tilesSize, const uint32_t* pal, int palCount, const uint8_t* map,
                        uint32_t* out) {
    for (int ty = 0; ty < 20; ty++) {
        for (int tx = 0; tx < 30; tx++) {
            int t = map[ty * 64 + tx];
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    size_t at = (size_t) t * 64 + y * 8 + x;
                    int ci = (at < tilesSize ? tiles[at] : 0) - 112;
                    out[(ty * 8 + y) * PD_MAP_W + tx * 8 + x] = ci >= 0 && ci < palCount ? pal[ci] : pal[0];
                }
            }
        }
    }
}

// A 16x16 sprite from 2x2 4bpp tiles; index 0 is see-through.
static void sprite16(const uint8_t* gfx, const uint32_t* pal, uint32_t* out) {
    for (int y = 0; y < 16; y++) {
        for (int x = 0; x < 16; x++) {
            int tile = (y / 8) * 2 + x / 8;
            uint8_t b = gfx[tile * 32 + (y % 8) * 4 + (x % 8) / 2];
            int v = x & 1 ? b >> 4 : b & 0xF;
            out[y * 16 + x] = v ? pal[v] : PD_MAP_CLEAR;
        }
    }
}

static struct pd_map_art* cached;
static unsigned cachedRom;

const struct pd_map_art* pd_map_art(const struct pd_game* g) {
    if (!g->rom || (g->kind != PD_GAME_FIRERED && g->kind != PD_GAME_EMERALD)) return NULL;
    if (cachedRom == pd_rom_id(g)) return cached && cached->ok ? cached : NULL;
    free(cached);
    cached = calloc(1, sizeof(*cached));
    cachedRom = pd_rom_id(g);
    if (!cached) return NULL;

    uint8_t* blob[PD_BLOB_COUNT] = { 0 };
    uint32_t pal[256];
    if (g->kind == PD_GAME_FIRERED) {
        static const int WANT[] = {
            PD_FR_REGION_GFX, PD_FR_REGION_PAL, PD_FR_KANTO_MAP, PD_FR_SEVII123_MAP, PD_FR_SEVII45_MAP,
            PD_FR_SEVII67_MAP, PD_FR_PLAYER_RED_GFX, PD_FR_PLAYER_LEAF_GFX, PD_FR_PLAYER_PAL,
        };
        find_blobs(g->rom, g->romSize, WANT, (int) (sizeof(WANT) / sizeof(WANT[0])), blob);
        if (blob[PD_FR_REGION_GFX] && blob[PD_FR_REGION_PAL] && blob[PD_FR_KANTO_MAP]) {
            int colours = (int) pd_blob_sigs[PD_FR_REGION_PAL].size / 2;
            palette(blob[PD_FR_REGION_PAL], colours, pal);
            static const int MAPS[PD_MAP_PAGES] = {
                PD_FR_KANTO_MAP, PD_FR_SEVII123_MAP, PD_FR_SEVII45_MAP, PD_FR_SEVII67_MAP,
            };
            for (int p = 0; p < PD_MAP_PAGES && blob[MAPS[p]]; p++) {
                screen_4bpp(blob[PD_FR_REGION_GFX], pd_blob_sigs[PD_FR_REGION_GFX].size, pal, colours,
                            blob[MAPS[p]], cached->page[p]);
                cached->pages = p + 1;
            }
            cached->ok = true;
        }
        if (blob[PD_FR_PLAYER_PAL]) {
            // Red's palette serves Leaf too (the game does the same).
            palette(blob[PD_FR_PLAYER_PAL], 16, pal);
            for (int s = 0; s < 2; s++) {
                uint8_t* gfx = blob[s ? PD_FR_PLAYER_LEAF_GFX : PD_FR_PLAYER_RED_GFX];
                if (!gfx) continue;
                sprite16(gfx, pal, cached->head[s]);
                cached->hasHead[s] = true;
            }
        }
    } else {
        static const int WANT[] = {
            PD_EM_REGION_GFX, PD_EM_REGION_PAL, PD_EM_REGION_MAP, PD_EM_PLAYER_BRENDAN_GFX,
            PD_EM_PLAYER_BRENDAN_PAL, PD_EM_PLAYER_MAY_GFX, PD_EM_PLAYER_MAY_PAL,
        };
        find_blobs(g->rom, g->romSize, WANT, (int) (sizeof(WANT) / sizeof(WANT[0])), blob);
        if (blob[PD_EM_REGION_GFX] && blob[PD_EM_REGION_PAL] && blob[PD_EM_REGION_MAP]) {
            int colours = (int) pd_blob_sigs[PD_EM_REGION_PAL].size / 2;
            palette(blob[PD_EM_REGION_PAL], colours, pal);
            screen_8bpp(blob[PD_EM_REGION_GFX], pd_blob_sigs[PD_EM_REGION_GFX].size, pal, colours,
                        blob[PD_EM_REGION_MAP], cached->page[0]);
            cached->pages = 1;
            cached->ok = true;
        }
        static const int HEADS[2][2] = {
            { PD_EM_PLAYER_BRENDAN_GFX, PD_EM_PLAYER_BRENDAN_PAL }, { PD_EM_PLAYER_MAY_GFX, PD_EM_PLAYER_MAY_PAL },
        };
        for (int s = 0; s < 2; s++) {
            if (!blob[HEADS[s][0]] || !blob[HEADS[s][1]]) continue;
            palette(blob[HEADS[s][1]], 16, pal);
            sprite16(blob[HEADS[s][0]], pal, cached->head[s]);
            cached->hasHead[s] = true;
        }
    }
    for (int i = 0; i < PD_BLOB_COUNT; i++) free(blob[i]);
    return cached->ok ? cached : NULL;
}

// --- sections, the cursor grid, the player (RegionMapModel) ---

static const struct pd_region_layout* layouts(const struct pd_game* g, int* n) {
    if (g->kind == PD_GAME_EMERALD) {
        *n = pd_layouts_emerald_count;
        return pd_layouts_emerald;
    }
    *n = pd_layouts_firered_count;
    return pd_layouts_firered;
}

static const struct pd_mapsec_rect* rect_of(const struct pd_game* g, int mapsec) {
    if (g->kind == PD_GAME_EMERALD) {
        return mapsec >= 0 && mapsec < pd_mapsec_rects_emerald_count ? &pd_mapsec_rects_emerald[mapsec] : NULL;
    }
    return mapsec >= 0 && mapsec < pd_mapsec_rects_firered_count ? &pd_mapsec_rects_firered[mapsec] : NULL;
}

static int layout_at(const struct pd_region_layout* l, int layer, int tx, int ty) {
    if (!l->layers[layer]) return -1;
    int x = tx - l->offX, y = ty - l->offY;
    if (x < 0 || y < 0 || x >= l->w || y >= l->h) return -1;
    int v = l->layers[layer][y * l->w + x];
    return v == l->none ? -1 : v;
}

int pd_map_tiles(const struct pd_game* g, int mapsec, int* page, struct pd_tile_rect* out, int max) {
    const struct pd_mapsec_rect* r = rect_of(g, mapsec);
    if (!r || r->page < 0 || max < 1) return 0;
    *page = r->page;
    if (r->w > 0 && r->h > 0) {
        out[0] = (struct pd_tile_rect) { r->x, r->y, r->w, r->h };
        return 1;
    }
    // Only on the grid (a dungeon): every cell naming it, on its page.
    int n = 0, count;
    const struct pd_region_layout* ls = layouts(g, &count);
    if (r->page >= count) return 0;
    const struct pd_region_layout* l = &ls[r->page];
    for (int layer = 0; layer < 2; layer++) {
        for (int ty = l->offY; ty < l->offY + l->h; ty++) {
            for (int tx = l->offX; tx < l->offX + l->w; tx++) {
                if (layout_at(l, layer, tx, ty) != mapsec) continue;
                bool dup = false;
                for (int k = 0; k < n; k++) dup |= out[k].x == tx && out[k].y == ty;
                if (!dup && n < max) out[n++] = (struct pd_tile_rect) { tx, ty, 1, 1 };
            }
        }
    }
    return n;
}

void pd_map_pick(const struct pd_game* g, int page, int tx, int ty, int* mapsec, int* dungeon) {
    int count;
    const struct pd_region_layout* ls = layouts(g, &count);
    *mapsec = *dungeon = -1;
    if (page < 0 || page >= count) return;
    *mapsec = layout_at(&ls[page], 0, tx, ty);
    *dungeon = layout_at(&ls[page], 1, tx, ty);
    // Only sections the game names count.
    if (*mapsec >= 0 && !rect_of(g, *mapsec)) *mapsec = -1;
    if (*dungeon >= 0 && !rect_of(g, *dungeon)) *dungeon = -1;
}

// TOWN, CITY, ROUTE, UNDERWATER, OCEAN_ROUTE: the maps the region map places
// the player inside of; indoors the head stays on the last outdoor tile.
static bool outdoor(int mapType) {
    return mapType == 1 || mapType == 2 || mapType == 3 || mapType == 5 || mapType == 6;
}

bool pd_map_player_tile(const struct pd_game* g, const struct pd_snapshot* s, int* page, int* tx, int* ty) {
    static int lastSec = -1, lastX, lastY;
    struct pd_tile_rect r;
    if (!s->valid || pd_map_tiles(g, s->mapsec, page, &r, 1) < 1) return false;
    if (outdoor(s->mapType) && s->posOk && s->mapW > 0 && s->mapH > 0) {
        int sw = s->mapW / r.w, sh = s->mapH / r.h;
        int dx = s->x / (sw > 1 ? sw : 1), dy = s->y / (sh > 1 ? sh : 1);
        if (dx > r.w - 1) dx = r.w - 1;
        if (dy > r.h - 1) dy = r.h - 1;
        if (dx < 0) dx = 0;
        if (dy < 0) dy = 0;
        *tx = r.x + dx;
        *ty = r.y + dy;
        lastSec = s->mapsec;
        lastX = *tx;
        lastY = *ty;
    } else if (lastSec == s->mapsec && lastX >= r.x && lastX < r.x + r.w && lastY >= r.y && lastY < r.y + r.h) {
        *tx = lastX;
        *ty = lastY;
    } else {
        *tx = r.x;
        *ty = r.y;
    }
    return true;
}

const char* pd_map_page_name(const struct pd_game* g, int page) {
    if (g->kind == PD_GAME_EMERALD) return "HOENN";
    static const char* const NAMES[PD_MAP_PAGES] = { "KANTO", "SEVII 1-3", "SEVII 4-5", "SEVII 6-7" };
    return page >= 0 && page < PD_MAP_PAGES ? NAMES[page] : "";
}

// BAG: one pocket at a time in the game's own pocket order, its items in a
// list a drag scrolls, and the tapped item's description - in each game's bag
// colours (FireRedBag / EmeraldBag in the app's ItemsScreen.kt).
#include <stdio.h>

#include "pd_ui_internal.h"

struct bag_palette {
    struct pd_layer panel[4];
    int panelLayers;
    int tabLayers; // the header reuses this many panel layers, filled with the next one's colour
    uint32_t fill, divider; // divider 0 = none
    uint32_t text, textShadow;
    uint32_t tabText, tabTextShadow;
    uint32_t cursor;
    struct pd_layer desc[2];
    int descLayers;
    uint32_t descFill, descText, descTextShadow;
    uint32_t backdropA, backdropB; // 0 = the companion's own backdrop
};

static const struct bag_palette FIRERED_BAG = {
    .panel = { { 0x686868, 2 }, { 0xE8E0A8, 1 }, { 0xF0C870, 2 }, { 0xD0B050, 2 } },
    .panelLayers = 4,
    .tabLayers = 2,
    .fill = 0xF8F8C8, .divider = 0xE8E0A8,
    .text = 0x606060, .textShadow = 0xD0D0C8,
    .tabText = 0xF8F8F8, .tabTextShadow = 0x606060,
    .cursor = 0xD88848,
    .desc = { { 0x005070, 2 }, { 0x10A8D8, 1 } },
    .descLayers = 2,
    .descFill = 0x0078C0, .descText = 0xF8F8F8, .descTextShadow = 0x606060,
};

static const struct bag_palette EMERALD_BAG = {
    .panel = { { 0x335170, 2 }, { 0x2C3941, 1 }, { 0x636372, 1 }, { 0xFBE898, 2 } },
    .panelLayers = 4,
    .tabLayers = 3,
    .fill = 0xFFFFD3, .divider = 0,
    .text = 0x000000, .textShadow = 0xD6D6CF,
    .tabText = 0x000000, .tabTextShadow = 0xB89D63,
    .cursor = 0xEC5F2A,
    .desc = { { 0x7B7B7B, 1 } },
    .descLayers = 1,
    .descFill = 0xFFFFFF, .descText = 0x000000, .descTextShadow = 0xD6D6CF,
    .backdropA = 0xC871F7, .backdropB = 0x5882F7,
};

#define HEADER_H 24
#define LIST_Y (CONTENT_Y + HEADER_H + 4)
#define LIST_H 118
#define BAG_ROW 17
#define DESC_Y (LIST_Y + LIST_H + 4)
#define FIRST_HM 339 // ITEM_HM01..ITEM_HM08: no quantity, like the game
#define LAST_HM 346

static void header(struct pd_ui* ui, struct pd_canvas* c, const struct bag_palette* p, int pocket) {
    int x = MARGIN, w = c->w - 2 * MARGIN;
    pd_layered_box(c, x, CONTENT_Y, w, HEADER_H, 3, p->panel, p->tabLayers, p->panel[p->tabLayers].color);
    const char* name = pd_pocket_name(pocket);
    int ty = pd_text_y(CONTENT_Y, HEADER_H);
    pd_text(c, x + (w - pd_text_width(name)) / 2, ty, name, p->tabText, p->tabTextShadow);
    // The game's pocket arrows, drawn (the font has no ◀ / ▶).
    int ay = CONTENT_Y + HEADER_H / 2 - 5;
    uint32_t prev = ui->pressedId == HIT_POCKET_PREV ? p->cursor : p->tabText;
    uint32_t next = ui->pressedId == HIT_POCKET_NEXT ? p->cursor : p->tabText;
    pd_triangle(c, x + 10, ay, 6, PD_LEFT, prev);
    pd_triangle(c, x + w - 16, ay, 6, PD_RIGHT, next);
    ui_add_hit(ui, x, CONTENT_Y, 48, HEADER_H, HIT_POCKET_PREV);
    ui_add_hit(ui, x + w - 48, CONTENT_Y, 48, HEADER_H, HIT_POCKET_NEXT);
}

static void list(struct pd_ui* ui, struct pd_canvas* c, const struct pd_game* g, const struct bag_palette* p,
                 const struct pd_pocket_items* items, int pocket) {
    int x = MARGIN, w = c->w - 2 * MARGIN;
    int f = pd_layered_box(c, x, LIST_Y, w, LIST_H, 3, p->panel, p->panelLayers, p->fill);
    int ix = x + f, iy = LIST_Y + f + 1, iw = w - 2 * f, ih = LIST_H - 2 * f - 2;
    ui_add_scroll_hit(ui, ix, iy, iw, ih, HIT_BAG_LIST);
    if (items->count == 0) {
        pd_text(c, ix + 14, pd_text_y(iy, BAG_ROW), "The pocket is empty.", p->text, p->textShadow);
        return;
    }
    int maxScroll = items->count * BAG_ROW - ih;
    if (maxScroll < 0) maxScroll = 0;
    if (ui->bagScroll > maxScroll) ui->bagScroll = maxScroll;
    if (ui->bagScroll < 0) ui->bagScroll = 0;

    pd_clip(c, ix, iy, iw, ih);
    int first = ui->bagScroll / BAG_ROW;
    for (int i = first; i < items->count; i++) {
        int ry = iy + i * BAG_ROW - ui->bagScroll;
        if (ry >= iy + ih) break;
        const struct pd_item* it = &items->items[i];
        bool cursor = i == ui->bagSelected || ui->pressedId == HIT_BAG_ROW + i;
        if (cursor) pd_triangle(c, ix + 4, ry + 3, 6, PD_RIGHT, p->cursor);
        const char* name = pd_item_name(g, it->id);
        char fallback[16];
        if (!*name) {
            snprintf(fallback, sizeof(fallback), "ITEM %d", it->id);
            name = fallback;
        }
        pd_text_fit(c, ix + 14, pd_text_y(ry, BAG_ROW), iw - 64, name, p->text, p->textShadow);
        if (pocket != PD_POCKET_KEY && !(it->id >= FIRST_HM && it->id <= LAST_HM)) {
            char qty[12];
            snprintf(qty, sizeof(qty), "×%d", it->quantity);
            pd_text_right(c, ix + iw - 10, pd_text_y(ry, BAG_ROW), qty, p->text, p->textShadow);
        }
        if (p->divider) pd_fill(c, ix + 4, ry + BAG_ROW - 1, iw - 8, 1, p->divider);
        // Rows cut off at the window's edge still take a tap on what shows.
        int top = ry < iy ? iy : ry;
        int bottom = ry + BAG_ROW > iy + ih ? iy + ih : ry + BAG_ROW;
        ui_add_scroll_hit(ui, ix, top, iw, bottom - top, HIT_BAG_ROW + i);
    }
    pd_unclip(c);
    // A scroll bar when the pocket runs past the window.
    if (maxScroll > 0) {
        int total = items->count * BAG_ROW;
        int th = ih * ih / total;
        if (th < 8) th = 8;
        int ty = iy + (ih - th) * ui->bagScroll / maxScroll;
        pd_fill(c, ix + iw - 4, ty, 2, th, p->cursor);
    }
}

static void description(struct pd_canvas* c, const struct pd_game* g, const struct bag_palette* p,
                        const struct pd_pocket_items* items, int selected) {
    int x = MARGIN, w = c->w - 2 * MARGIN, h = c->h - DESC_Y - MARGIN;
    int f = pd_layered_box(c, x, DESC_Y, w, h, 3, p->desc, p->descLayers, p->descFill);
    const char* text = "Tap an item to read about it.";
    if (selected >= 0 && selected < items->count) {
        const char* d = pd_item_description(g, items->items[selected].id);
        if (*d) text = d;
    }
    pd_text_wrap(c, x + f + 6, DESC_Y + f + 1, w - 2 * (f + 6), 16, (h - 2 * f) / 16, text, p->descText,
                 p->descTextShadow);
}

void ui_bag_tab(struct pd_ui* ui, struct pd_canvas* c, const struct pd_game* g, const struct pd_snapshot* s) {
    if (!s->bagOk) {
        ui_notice(c, "NO BAG YET", "The bag shows once the game", "has loaded a save.");
        return;
    }
    const struct bag_palette* p = g->kind == PD_GAME_EMERALD ? &EMERALD_BAG : &FIRERED_BAG;
    if (p->backdropA) {
        for (int y = CONTENT_Y - 2; y < c->h; y += 8) {
            pd_fill(c, 0, y, c->w, 8, ((y - CONTENT_Y + 2) / 8) % 2 ? p->backdropB : p->backdropA);
        }
    }
    if (ui->bagPocket < 0 || ui->bagPocket >= PD_POCKET_COUNT) ui->bagPocket = 0;
    int pocket = g->cfg->bagOrder[ui->bagPocket];
    const struct pd_pocket_items* items = &s->bag[pocket];
    if (ui->bagSelected >= items->count) ui->bagSelected = -1;
    header(ui, c, p, pocket);
    list(ui, c, g, p, items, pocket);
    description(c, g, p, items, ui->bagSelected);
}

#include "pd_ui.h"

#include <stdio.h>
#include <string.h>

#include "pd_style.h"
#include "pd_tables.h"

// FireRedPartyPalette (PartyScreen.kt).
struct slot_colors {
    uint32_t outline, light, fill, shade;
};
static const struct slot_colors SLOT_NORMAL = { 0x4A4A63, 0x84C6DE, 0x3994DE, 0x297BB5 };
static const struct slot_colors SLOT_SELECTED = { 0xFF7331, 0xADEFFF, 0x7BD6EF, 0x4AADCE };
static const struct slot_colors SLOT_FAINTED = { 0x4A4A63, 0xD6A521, 0xC66B10, 0xA54A00 };
#define SLOT_TEXT 0xFFFFFF
#define SLOT_TEXT_SHADOW 0x737373
#define HP_FRAME 0x525252
#define HP_LABEL 0xFFD652
struct hp_colors {
    uint32_t top, main;
};
static const struct hp_colors HP_GREEN = { 0x5AD684, 0x73FFAD };
static const struct hp_colors HP_YELLOW = { 0xCEAD08, 0xFFE739 };
static const struct hp_colors HP_RED = { 0xC63900, 0xFF7331 };
#define HP_EMPTY 0x737373

#define STATUS_FILL_PSN 0xA040A0
#define STATUS_FILL_PAR 0xB8B818
#define STATUS_FILL_SLP 0x8C8C94
#define STATUS_FILL_BRN 0xE05030
#define STATUS_FILL_FRZ 0x58A8D8

// Hit ids.
#define HIT_TAB 100
#define HIT_SLOT 200
#define HIT_BACK 300

#define CONTENT_Y 28
#define MARGIN 4

static const char* const TAB_LABELS[PD_TAB_COUNT] = { "PARTY", "BATTLE", "INFO" };

void pd_ui_init(struct pd_ui* ui) {
    memset(ui, 0, sizeof(*ui));
    ui->tab = PD_TAB_PARTY;
    ui->summarySlot = -1;
    ui->pressedId = -1;
}

void pd_ui_update(struct pd_ui* ui, const struct pd_snapshot* s) {
    bool on = s->valid && s->inBattle && s->battlers[PD_POS_PLAYER_LEFT].species;
    if (on && !ui->battleWasOn) {
        if (ui->tab != PD_TAB_BATTLE) ui->tabBeforeBattle = ui->tab;
        ui->tab = PD_TAB_BATTLE;
        ui->summarySlot = -1;
    } else if (!on && ui->battleWasOn && ui->tab == PD_TAB_BATTLE) {
        ui->tab = ui->tabBeforeBattle;
    }
    ui->battleWasOn = on;
    if (ui->summarySlot >= s->partyCount) ui->summarySlot = -1;
}

static void add_hit(struct pd_ui* ui, int x, int y, int w, int h, int id) {
    if (ui->hitCount >= PD_UI_MAX_HITS) return;
    ui->hits[ui->hitCount++] = (struct pd_hit) { x, y, w, h, id };
}

static int hit_at(const struct pd_ui* ui, int x, int y) {
    for (int i = ui->hitCount - 1; i >= 0; i--) {
        const struct pd_hit* h = &ui->hits[i];
        if (x >= h->x && x < h->x + h->w && y >= h->y && y < h->y + h->h) return h->id;
    }
    return -1;
}

// --- pieces ---

// A white framed button; pressed = the cursor's white over grey idle.
static void button(struct pd_ui* ui, struct pd_canvas* c, int x, int y, int w, int h, const char* label, int id) {
    bool pressed = ui->pressedId == id;
    pd_title_box(c, x, y, w, h, pressed ? PD_LIST_FILL : PD_TITLE_FILL);
    int tw = pd_text_width(label);
    pd_text(c, x + (w - tw) / 2, pd_text_y(y, h), label, PD_TITLE_TEXT, PD_TITLE_SHADOW);
    add_hit(ui, x, y, w, h, id);
}

static const struct hp_colors* hp_colors_for(int hp, int maxHp, int barW) {
    if (hp >= maxHp) return &HP_GREEN;
    int f = maxHp > 0 ? hp * barW / maxHp : 0;
    if (f == 0 && hp > 0) f = 1;
    if (f > barW * 50 / 100) return &HP_GREEN;
    if (f > barW * 20 / 100) return &HP_YELLOW;
    return &HP_RED;
}

// The party menu's HP bar: a dark frame, the "HP" tag, a two-tone fill.
static void hp_bar(struct pd_canvas* c, int x, int y, int barW, int hp, int maxHp) {
    pd_fill(c, x, y, 16 + barW + 2, 7, HP_FRAME);
    // "HP" in 3x5 pixel letters, like the game's tag.
    static const uint8_t H[5] = { 5, 5, 7, 5, 5 }, P[5] = { 6, 5, 6, 4, 4 };
    for (int r = 0; r < 5; r++) {
        for (int b = 0; b < 3; b++) {
            if (H[r] & (4 >> b)) pd_fill(c, x + 2 + b, y + 1 + r, 1, 1, HP_LABEL);
            if (P[r] & (4 >> b)) pd_fill(c, x + 6 + b, y + 1 + r, 1, 1, HP_LABEL);
        }
    }
    int bx = x + 15, by = y + 1;
    pd_fill(c, bx, by, barW + 2, 5, 0xFFFFFF);
    pd_fill(c, bx + 1, by + 1, barW, 3, HP_EMPTY);
    int f = maxHp > 0 ? hp * barW / maxHp : 0;
    if (f == 0 && hp > 0) f = 1;
    if (f > barW) f = barW;
    const struct hp_colors* col = hp_colors_for(hp, maxHp, barW);
    pd_fill(c, bx + 1, by + 1, f, 1, col->top);
    pd_fill(c, bx + 1, by + 2, f, 2, col->main);
}

static void status_badge(struct pd_canvas* c, int x, int y, uint32_t status) {
    const char* label = pd_status_label(status);
    if (!*label) return;
    uint32_t fill = STATUS_FILL_SLP;
    if (!strcmp(label, "PSN") || !strcmp(label, "TOX")) fill = STATUS_FILL_PSN;
    else if (!strcmp(label, "PAR")) fill = STATUS_FILL_PAR;
    else if (!strcmp(label, "BRN")) fill = STATUS_FILL_BRN;
    else if (!strcmp(label, "FRZ")) fill = STATUS_FILL_FRZ;
    int w = pd_text_width(label) + 6;
    pd_round_rect(c, x, y, w, 14, 2, fill);
    pd_text(c, x + 3, y - 2, label, 0xFFFFFF, PD_NO_SHADOW);
}

static const char* mon_display_name(const struct pd_mon* m, char* buf, size_t len) {
    if (m->species == PD_SPECIES_EGG) return "EGG";
    if (m->nickname[0]) return m->nickname;
    return pd_species_name(m->species, buf, len);
}

static void format_money(long v, char* buf, size_t len) {
    if (v < 0) {
        snprintf(buf, len, "-");
    } else if (v >= 1000) {
        snprintf(buf, len, "%ld,%03ld", v / 1000, v % 1000);
    } else {
        snprintf(buf, len, "%ld", v);
    }
}

// --- tab bar ---

static void tab_bar(struct pd_ui* ui, struct pd_canvas* c) {
    int w = (c->w - MARGIN * (PD_TAB_COUNT + 1)) / PD_TAB_COUNT;
    for (int t = 0; t < PD_TAB_COUNT; t++) {
        int x = MARGIN + t * (w + MARGIN);
        bool open = ui->tab == (enum pd_tab) t;
        pd_title_box(c, x, 2, w, 22, open || ui->pressedId == HIT_TAB + t ? PD_TITLE_FILL : PD_LIST_FILL);
        int tw = pd_text_width(TAB_LABELS[t]);
        pd_text(c, x + (w - tw) / 2, pd_text_y(2, 22), TAB_LABELS[t], open ? PD_VALUE : PD_TITLE_TEXT,
                open ? PD_VALUE_SHADOW : PD_TITLE_SHADOW);
        add_hit(ui, x, 2, w, 22, HIT_TAB + t);
    }
}

// A message in a list window across the content area.
static void notice(struct pd_canvas* c, const char* title, const char* line1, const char* line2) {
    int y = CONTENT_Y + 30;
    int f = pd_list_box(c, MARGIN, y, c->w - 2 * MARGIN, 76);
    pd_text(c, MARGIN + f + 6, y + f + 2, title, PD_VALUE, PD_VALUE_SHADOW);
    if (line1) pd_text_fit(c, MARGIN + f + 6, y + f + 22, c->w - 2 * (MARGIN + f + 6), line1, PD_LABEL, PD_LABEL_SHADOW);
    if (line2) pd_text_fit(c, MARGIN + f + 6, y + f + 40, c->w - 2 * (MARGIN + f + 6), line2, PD_LABEL, PD_LABEL_SHADOW);
}

// --- PARTY ---

static void party_slot(struct pd_ui* ui, struct pd_canvas* c, int x, int y, int w, int h,
                       const struct pd_mon* m, int slot) {
    char buf[40];
    if (!m) {
        // An empty slot: just its outline over the backdrop.
        pd_round_rect(c, x, y, w, h, 5, SLOT_NORMAL.outline);
        pd_round_rect(c, x + 1, y + 1, w - 2, h - 2, 4, PD_BACKDROP_B);
        return;
    }
    bool egg = m->species == PD_SPECIES_EGG;
    bool fainted = !egg && m->hp == 0;
    const struct slot_colors* col = ui->pressedId == HIT_SLOT + slot ? &SLOT_SELECTED
        : fainted ? &SLOT_FAINTED : &SLOT_NORMAL;
    pd_round_rect(c, x, y, w, h, 5, col->outline);
    pd_round_rect(c, x + 1, y + 1, w - 2, h - 2, 4, col->light);
    pd_round_rect(c, x + 2, y + 2, w - 4, h - 4, 3, col->fill);
    pd_fill(c, x + 3, y + h - 6, w - 6, 3, col->shade);

    const char* name = mon_display_name(m, buf, sizeof(buf));
    pd_text_fit(c, x + 8, y + 3, w - 56, name, SLOT_TEXT, SLOT_TEXT_SHADOW);
    if (!egg) {
        char lv[12];
        snprintf(lv, sizeof(lv), "Lv%d", m->level);
        pd_text_right(c, x + w - 8, y + 3, lv, SLOT_TEXT, SLOT_TEXT_SHADOW);
        status_badge(c, x + 8, y + 24, m->status);
        char hp[16];
        snprintf(hp, sizeof(hp), "%d/%d", m->hp, m->maxHp);
        pd_text_right(c, x + w - 8, y + 21, hp, SLOT_TEXT, SLOT_TEXT_SHADOW);
        hp_bar(c, x + 8, y + h - 18, w - 34, m->hp, m->maxHp);
    }
    add_hit(ui, x, y, w, h, HIT_SLOT + slot);
}

static void summary(struct pd_ui* ui, struct pd_canvas* c, const struct pd_mon* m) {
    char buf[40], buf2[40];
    int x = MARGIN, y = CONTENT_Y, w = c->w - 2 * MARGIN, h = c->h - CONTENT_Y - MARGIN;
    int f = pd_title_box(c, x, y, w, h, PD_TITLE_FILL);
    int ix = x + f + 6, iw = w - 2 * (f + 6);
    int ty = y + f + 2;

    bool egg = m->species == PD_SPECIES_EGG;
    pd_text(c, ix, ty, mon_display_name(m, buf, sizeof(buf)), PD_LABEL, PD_LABEL_SHADOW);
    if (!egg) {
        char lv[12];
        snprintf(lv, sizeof(lv), "Lv%d", m->level);
        pd_text_right(c, ix + iw, ty, lv, PD_VALUE, PD_VALUE_SHADOW);
        const char* species = pd_species_name(m->species, buf2, sizeof(buf2));
        if (strcmp(species, m->nickname)) {
            int nw = pd_text_width(mon_display_name(m, buf, sizeof(buf)));
            pd_text(c, ix + nw + 8, ty, species, PD_MUTED, PD_MUTED_SHADOW);
        }
    }
    ty += 20;
    if (!egg) {
        hp_bar(c, ix, ty + 4, 96, m->hp, m->maxHp);
        char hp[16];
        snprintf(hp, sizeof(hp), "%d/%d", m->hp, m->maxHp);
        pd_text(c, ix + 120, ty, hp, PD_LABEL, PD_LABEL_SHADOW);
        status_badge(c, ix + iw - 30, ty + 2, m->status);
        ty += 20;
        pd_fill(c, ix, ty, iw, 1, PD_DIVIDER);
        ty += 4;
        for (int i = 0; i < PD_NUM_MOVES; i++) {
            if (!m->moves[i]) continue;
            pd_text_fit(c, ix, ty, iw - 120, pd_move_name(m->moves[i], buf, sizeof(buf)), PD_LABEL, PD_LABEL_SHADOW);
            pd_text(c, ix + iw - 116, ty, pd_type_name(pd_move_type(m->moves[i])), PD_MUTED, PD_MUTED_SHADOW);
            char pp[12];
            snprintf(pp, sizeof(pp), "PP %d", m->pp[i]);
            pd_text_right(c, ix + iw, ty, pp, PD_VALUE, PD_VALUE_SHADOW);
            ty += 20;
        }
    } else {
        pd_text(c, ix, ty, "It's an egg. It will hatch in time.", PD_MUTED, PD_MUTED_SHADOW);
    }
    button(ui, c, x + w - f - 70, y + h - f - 26, 66, 22, "BACK", HIT_BACK);
}

static void party_tab(struct pd_ui* ui, struct pd_canvas* c, const struct pd_snapshot* s) {
    if (ui->summarySlot >= 0) {
        summary(ui, c, &s->party[ui->summarySlot]);
        return;
    }
    int gap = 4;
    int w = (c->w - 2 * MARGIN - gap) / 2;
    int h = (c->h - CONTENT_Y - MARGIN - 2 * gap) / 3;
    for (int i = 0; i < PD_PARTY_SIZE; i++) {
        int x = MARGIN + (i % 2) * (w + gap);
        int y = CONTENT_Y + (i / 2) * (h + gap);
        party_slot(ui, c, x, y, w, h, i < s->partyCount ? &s->party[i] : NULL, i);
    }
}

// --- BATTLE ---

static void battler_card(struct pd_canvas* c, int x, int y, int w, int h, const struct pd_battle_mon* b,
                         const char* side) {
    char buf[40], types[40];
    int f = pd_list_box(c, x, y, w, h);
    int ix = x + f + 4, iw = w - 2 * (f + 4);
    int ty = y + f;
    pd_text(c, ix, ty, side, PD_MUTED, PD_MUTED_SHADOW);
    int sw = pd_text_width(side) + 6;
    pd_text_fit(c, ix + sw, ty, iw - sw - 50, pd_species_name(b->species, buf, sizeof(buf)), PD_LABEL, PD_LABEL_SHADOW);
    char lv[12];
    snprintf(lv, sizeof(lv), "Lv%d", b->level);
    pd_text_right(c, ix + iw, ty, lv, PD_VALUE, PD_VALUE_SHADOW);
    ty += 18;
    if (b->type2 != b->type1 && b->type2 != PD_TYPE_NONE) {
        snprintf(types, sizeof(types), "%s/%s", pd_type_name(b->type1), pd_type_name(b->type2));
    } else {
        snprintf(types, sizeof(types), "%s", pd_type_name(b->type1));
    }
    pd_text(c, ix, ty, types, PD_LABEL, PD_LABEL_SHADOW);
    char hp[16];
    snprintf(hp, sizeof(hp), "%d/%d", b->hp, b->maxHp);
    pd_text_right(c, ix + iw, ty, hp, PD_LABEL, PD_LABEL_SHADOW);
    int barX = ix + 124;
    hp_bar(c, barX, ty + 5, ix + iw - 58 - barX - 16, b->hp, b->maxHp);
    status_badge(c, ix + iw - 52 - 34, ty + 2, b->status);
}

// The plain-words verdict of BattleInfoScreen: SUPER 2x / RESISTED ½x / ...
static void verdict(int pct, const char** text, uint32_t* color, uint32_t* shadow) {
    if (pct == 0) { *text = "IMMUNE 0x"; *color = PD_MUTED; *shadow = PD_MUTED_SHADOW; }
    else if (pct >= 400) { *text = "SUPER 4x"; *color = PD_VALUE; *shadow = PD_VALUE_SHADOW; }
    else if (pct >= 200) { *text = "SUPER 2x"; *color = PD_VALUE; *shadow = PD_VALUE_SHADOW; }
    else if (pct <= 25) { *text = "RESISTED 1/4x"; *color = PD_MUTED; *shadow = PD_MUTED_SHADOW; }
    else if (pct < 100) { *text = "RESISTED 1/2x"; *color = PD_MUTED; *shadow = PD_MUTED_SHADOW; }
    else { *text = "NEUTRAL 1x"; *color = PD_LABEL; *shadow = PD_LABEL_SHADOW; }
}

static void battle_tab(struct pd_ui* ui, struct pd_canvas* c, const struct pd_snapshot* s) {
    (void) ui;
    const struct pd_battle_mon* you = &s->battlers[PD_POS_PLAYER_LEFT];
    const struct pd_battle_mon* foe = &s->battlers[PD_POS_OPPONENT_LEFT];
    if (!s->inBattle || !you->species) {
        notice(c, "NO BATTLE", "A battle opens this tab by itself,", "and its end takes you back.");
        return;
    }
    char buf[40];
    int w = c->w - 2 * MARGIN;
    if (foe->species) battler_card(c, MARGIN, CONTENT_Y, w, 50, foe, s->isTrainer ? "FOE" : "WILD");
    battler_card(c, MARGIN, CONTENT_Y + 54, w, 50, you, "YOU");

    int gap = 4;
    int bw = (w - gap) / 2, bh = (c->h - CONTENT_Y - 108 - MARGIN - gap) / 2;
    for (int i = 0; i < PD_NUM_MOVES; i++) {
        int x = MARGIN + (i % 2) * (bw + gap);
        int y = CONTENT_Y + 108 + (i / 2) * (bh + gap);
        int move = you->moves[i];
        int f = pd_title_box(c, x, y, bw, bh, move ? PD_TITLE_FILL : PD_LIST_FILL);
        if (!move) continue;
        int ix = x + f + 4, iw = bw - 2 * (f + 4);
        pd_text_fit(c, ix, y + f, iw, pd_move_name(move, buf, sizeof(buf)), PD_LABEL, PD_LABEL_SHADOW);
        const char* text;
        uint32_t color, shadow;
        if (pd_move_power(move) == 0) {
            text = "STATUS";
            color = PD_MUTED;
            shadow = PD_MUTED_SHADOW;
        } else if (foe->species) {
            verdict(pd_type_multiplier_pct(pd_move_type(move), foe->type1, foe->type2), &text, &color, &shadow);
        } else {
            text = "";
            color = shadow = PD_LABEL;
        }
        pd_text(c, ix, y + f + 18, text, color, shadow);
        char pp[12];
        snprintf(pp, sizeof(pp), "PP %d", you->pp[i]);
        pd_text_right(c, ix + iw, y + f + 18, pp, PD_MUTED, PD_MUTED_SHADOW);
    }
}

// --- INFO ---

static void info_row(struct pd_canvas* c, int x, int y, int w, const char* label, const char* value) {
    pd_text(c, x, y, label, PD_LABEL, PD_LABEL_SHADOW);
    pd_text_fit(c, x + w * 36 / 100, y, w - w * 36 / 100, value, PD_VALUE, PD_VALUE_SHADOW);
}

static void info_tab(struct pd_canvas* c, const struct pd_game* g, const struct pd_snapshot* s,
                     const struct pd_host_info* host) {
    int x = MARGIN, w = c->w - 2 * MARGIN;
    int f = pd_title_box(c, x, CONTENT_Y, w, 24, PD_TITLE_FILL);
    pd_text(c, x + f + 6, pd_text_y(CONTENT_Y, 24), "POKéDAISY 3DS", PD_TITLE_TEXT, PD_TITLE_SHADOW);
    pd_text_right(c, x + w - f - 6, pd_text_y(CONTENT_Y, 24), "PREVIEW", PD_TITLE_TEXT, PD_TITLE_SHADOW);

    int y = CONTENT_Y + 28;
    int lf = pd_list_box(c, x, y, w, c->h - y - MARGIN);
    int ix = x + lf + 4, iw = w - 2 * (lf + 4);
    int ty = y + lf;
    char buf[40];
    info_row(c, ix, ty, iw, "GAME", g->title ? g->title : "-");
    ty += 18;
    if (s->valid) {
        const char* place = pd_mapsec_name(g, s->mapsec);
        info_row(c, ix, ty, iw, "PLACE", *place ? place : "-");
        ty += 18;
        format_money(s->money, buf, sizeof(buf));
        info_row(c, ix, ty, iw, "MONEY", buf);
        ty += 18;
        snprintf(buf, sizeof(buf), "%d", s->partyCount);
        info_row(c, ix, ty, iw, "PARTY", buf);
        ty += 18;
    }
    if (host) {
        pd_fill(c, ix, ty + 1, iw, 1, PD_DIVIDER);
        ty += 4;
        for (int i = 0; i < host->count && i < PD_UI_HOST_LINES; i++) {
            info_row(c, ix, ty, iw, host->labels[i], host->values[i]);
            ty += 18;
        }
    }
}

// --- the screen ---

void pd_ui_draw(struct pd_ui* ui, struct pd_canvas* c, const struct pd_game* g,
                const struct pd_snapshot* s, const struct pd_host_info* host) {
    ui->hitCount = 0;
    pd_backdrop(c);
    tab_bar(ui, c);
    if (ui->tab == PD_TAB_INFO) {
        info_tab(c, g, s, host);
    } else if (!g->cfg) {
        notice(c, "NOT SUPPORTED", g->unsupported ? g->unsupported : "", "INFO has what was detected.");
    } else if (ui->tab == PD_TAB_PARTY) {
        party_tab(ui, c, s);
    } else {
        battle_tab(ui, c, s);
    }
}

static bool act(struct pd_ui* ui, int id) {
    if (id >= HIT_TAB && id < HIT_TAB + PD_TAB_COUNT) {
        ui->tab = (enum pd_tab) (id - HIT_TAB);
        ui->summarySlot = -1;
        return true;
    }
    if (id >= HIT_SLOT && id < HIT_SLOT + PD_PARTY_SIZE) {
        ui->summarySlot = id - HIT_SLOT;
        return true;
    }
    if (id == HIT_BACK) return pd_ui_back(ui);
    return false;
}

bool pd_ui_touch(struct pd_ui* ui, int x, int y, bool down) {
    if (down) {
        int id = hit_at(ui, x, y);
        if (!ui->touching) {
            // A press shows on the element it starts on ...
            ui->touching = true;
            ui->pressedId = id;
            return id != -1;
        }
        // ... and sliding off it cancels; another element is never picked up.
        if (ui->pressedId != -1 && id != ui->pressedId) {
            ui->pressedId = -1;
            return true;
        }
        return false;
    }
    if (!ui->touching) return false;
    ui->touching = false;
    int id = ui->pressedId;
    ui->pressedId = -1;
    if (id == -1) return false;
    act(ui, id);
    return true;
}

void pd_ui_next_tab(struct pd_ui* ui, int dir) {
    ui->tab = (enum pd_tab) ((ui->tab + PD_TAB_COUNT + dir) % PD_TAB_COUNT);
    ui->summarySlot = -1;
}

bool pd_ui_back(struct pd_ui* ui) {
    if (ui->summarySlot >= 0) {
        ui->summarySlot = -1;
        return true;
    }
    return false;
}

// PARTY: the six slots in FireRed's party colours, and a summary on tap.
#include <stdio.h>
#include <string.h>

#include "pd_tables.h"
#include "pd_ui_internal.h"

// FireRedPartyPalette (PartyScreen.kt).
struct slot_colors {
    uint32_t outline, light, fill, shade;
};
static const struct slot_colors SLOT_NORMAL = { 0x4A4A63, 0x84C6DE, 0x3994DE, 0x297BB5 };
static const struct slot_colors SLOT_SELECTED = { 0xFF7331, 0xADEFFF, 0x7BD6EF, 0x4AADCE };
static const struct slot_colors SLOT_FAINTED = { 0x4A4A63, 0xD6A521, 0xC66B10, 0xA54A00 };
#define SLOT_TEXT 0xFFFFFF
#define SLOT_TEXT_SHADOW 0x737373
#define MALE 0x42CEFF
#define MALE_SHADOW 0x006394
#define FEMALE 0xFF9C94
#define FEMALE_SHADOW 0x9C4239

static const char* mon_display_name(const struct pd_mon* m, char* buf, size_t len) {
    if (m->species == PD_SPECIES_EGG) return "EGG";
    if (m->nickname[0]) return m->nickname;
    return pd_species_name(m->species, buf, len);
}

// The party menu's ♂ / ♀ after the name, in their own colours.
static void gender_mark(struct pd_canvas* c, int x, int y, enum pd_gender g) {
    if (g == PD_GENDER_MALE) pd_text(c, x, y, "♂", MALE, MALE_SHADOW);
    if (g == PD_GENDER_FEMALE) pd_text(c, x, y, "♀", FEMALE, FEMALE_SHADOW);
}

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
    int nameW = pd_text_fit(c, x + 8, y + 3, w - 66, name, SLOT_TEXT, SLOT_TEXT_SHADOW);
    if (!egg) {
        gender_mark(c, x + 11 + nameW, y + 3, m->gender);
        char lv[12];
        snprintf(lv, sizeof(lv), "Lv%d", m->level);
        pd_text_right(c, x + w - 8, y + 3, lv, SLOT_TEXT, SLOT_TEXT_SHADOW);
        ui_status_badge(c, x + 8, y + 24, m->status);
        char hp[16];
        snprintf(hp, sizeof(hp), "%d/%d", m->hp, m->maxHp);
        pd_text_right(c, x + w - 8, y + 21, hp, SLOT_TEXT, SLOT_TEXT_SHADOW);
        ui_hp_bar(c, x + 8, y + h - 18, w - 34, m->hp, m->maxHp);
    }
    ui_add_hit(ui, x, y, w, h, HIT_SLOT + slot);
}

static void summary(struct pd_ui* ui, struct pd_canvas* c, const struct pd_mon* m) {
    char buf[40], buf2[40];
    int x = MARGIN, y = CONTENT_Y, w = c->w - 2 * MARGIN, h = c->h - CONTENT_Y - MARGIN;
    int f = pd_title_box(c, x, y, w, h, PD_TITLE_FILL);
    int ix = x + f + 6, iw = w - 2 * (f + 6);
    int ty = y + f + 2;

    bool egg = m->species == PD_SPECIES_EGG;
    const char* name = mon_display_name(m, buf, sizeof(buf));
    int nameW = pd_text(c, ix, ty, name, PD_LABEL, PD_LABEL_SHADOW);
    if (!egg) {
        gender_mark(c, ix + nameW + 3, ty, m->gender);
        char lv[12];
        snprintf(lv, sizeof(lv), "Lv%d", m->level);
        pd_text_right(c, ix + iw, ty, lv, PD_VALUE, PD_VALUE_SHADOW);
        const char* species = pd_species_name(m->species, buf2, sizeof(buf2));
        if (strcmp(species, name)) pd_text(c, ix + nameW + 14, ty, species, PD_MUTED, PD_MUTED_SHADOW);
    }
    ty += 20;
    if (!egg) {
        ui_hp_bar(c, ix, ty + 4, 96, m->hp, m->maxHp);
        char hp[16];
        snprintf(hp, sizeof(hp), "%d/%d", m->hp, m->maxHp);
        pd_text(c, ix + 120, ty, hp, PD_LABEL, PD_LABEL_SHADOW);
        ui_status_badge(c, ix + iw - 30, ty + 2, m->status);
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
    ui_button(ui, c, x + w - f - 70, y + h - f - 26, 66, 22, "BACK", HIT_BACK);
}

void ui_party_tab(struct pd_ui* ui, struct pd_canvas* c, const struct pd_snapshot* s) {
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

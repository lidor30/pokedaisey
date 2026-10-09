// BATTLE: the foe's and your cards, your moves with BattleInfoScreen's verdicts.
#include <stdio.h>

#include "pd_tables.h"
#include "pd_ui_internal.h"

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
    ui_hp_bar(c, barX, ty + 5, ix + iw - 58 - barX - 16, b->hp, b->maxHp);
    ui_status_badge(c, ix + iw - 52 - 34, ty + 2, b->status);
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

void ui_battle_tab(struct pd_ui* ui, struct pd_canvas* c, const struct pd_snapshot* s) {
    (void) ui;
    const struct pd_battle_mon* you = &s->battlers[PD_POS_PLAYER_LEFT];
    const struct pd_battle_mon* foe = &s->battlers[PD_POS_OPPONENT_LEFT];
    if (!s->inBattle || !you->species) {
        ui_notice(c, "NO BATTLE", "A battle opens this tab by itself,", "and its end takes you back.");
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
        const char* text = "";
        uint32_t color = PD_LABEL, shadow = PD_LABEL_SHADOW;
        if (pd_move_power(move) == 0) {
            text = "STATUS";
            color = PD_MUTED;
            shadow = PD_MUTED_SHADOW;
        } else if (foe->species) {
            verdict(pd_type_multiplier_pct(pd_move_type(move), foe->type1, foe->type2), &text, &color, &shadow);
        }
        pd_text(c, ix, y + f + 18, text, color, shadow);
        char pp[12];
        snprintf(pp, sizeof(pp), "PP %d", you->pp[i]);
        pd_text_right(c, ix + iw, y + f + 18, pp, PD_MUTED, PD_MUTED_SHADOW);
    }
}

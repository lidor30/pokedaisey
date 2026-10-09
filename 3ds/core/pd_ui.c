// The companion's frame: the tab bar, the open tab, overlays, and the stylus.
// Each tab draws itself in its own file (pd_ui_party.c, _battle, _bag,
// _settings); pd_ui_internal.h has what they share.
#include <stdlib.h>
#include <string.h>

#include "pd_ui_internal.h"

#define CHIP_H 22
#define FF_W 30
#define GEAR_W 26
// How far the stylus moves on a list before it scrolls instead of tapping.
#define DRAG_SLOP 6

static const char* const TAB_LABELS[] = { "PARTY", "BATTLE", "BAG" };

// The SETTINGS gear, 11x11 (bit 0 = left):
//   ....###....   .##.###.##.   .#########.   ..###.###..
//   ####...####   ###.....###   ####...####   ...and back up.
static const uint16_t GEAR[11] = {
    0x070, 0x376, 0x3FE, 0x1DC, 0x78F, 0x707, 0x78F, 0x1DC, 0x3FE, 0x376, 0x070,
};

void pd_ui_init(struct pd_ui* ui, struct pd_settings* settings) {
    memset(ui, 0, sizeof(*ui));
    ui->tab = PD_TAB_PARTY;
    ui->summarySlot = -1;
    ui->bagSelected = -1;
    ui->pressedId = -1;
    ui->settings = settings;
}

void pd_ui_update(struct pd_ui* ui, const struct pd_snapshot* s) {
    bool on = s->valid && s->inBattle && s->battlers[PD_POS_PLAYER_LEFT].species;
    if (on && !ui->battleWasOn) {
        if (ui->tab != PD_TAB_BATTLE) ui->tabBeforeBattle = ui->tab;
        ui->tab = PD_TAB_BATTLE;
        ui->summarySlot = -1;
        ui->overlay = PD_OVERLAY_NONE;
    } else if (!on && ui->battleWasOn && ui->tab == PD_TAB_BATTLE) {
        ui->tab = ui->tabBeforeBattle;
    }
    ui->battleWasOn = on;
    if (ui->summarySlot >= s->partyCount) ui->summarySlot = -1;
}

static const struct pd_hit* hit_at(const struct pd_ui* ui, int x, int y) {
    for (int i = ui->hitCount - 1; i >= 0; i--) {
        const struct pd_hit* h = &ui->hits[i];
        if (x >= h->x && x < h->x + h->w && y >= h->y && y < h->y + h->h) return h;
    }
    return NULL;
}

static void chip(struct pd_ui* ui, struct pd_canvas* c, int x, int w, bool open, int id) {
    pd_title_box(c, x, 2, w, CHIP_H, open || ui->pressedId == id ? PD_TITLE_FILL : PD_LIST_FILL);
    ui_add_hit(ui, x, 2, w, CHIP_H, id);
}

static void tab_bar(struct pd_ui* ui, struct pd_canvas* c, const struct pd_host_info* host) {
    int tabs = 3;
    int w = (c->w - MARGIN * (tabs + 3) - FF_W - GEAR_W) / tabs;
    int x = MARGIN;
    for (int t = 0; t < tabs; t++, x += w + MARGIN) {
        bool open = ui->tab == (enum pd_tab) t;
        chip(ui, c, x, w, open, HIT_TAB + t);
        int tw = pd_text_width(TAB_LABELS[t]);
        pd_text(c, x + (w - tw) / 2, pd_text_y(2, CHIP_H), TAB_LABELS[t], open ? PD_VALUE : PD_TITLE_TEXT,
                open ? PD_VALUE_SHADOW : PD_TITLE_SHADOW);
    }
    // Fast-forward: lit while on.
    bool ff = host && host->ffOn;
    chip(ui, c, x, FF_W, ff, HIT_FF);
    uint32_t col = ff ? PD_VALUE : PD_TITLE_TEXT;
    pd_triangle(c, x + 7, 2 + CHIP_H / 2 - 4, 5, PD_RIGHT, col);
    pd_triangle(c, x + 13, 2 + CHIP_H / 2 - 4, 5, PD_RIGHT, col);
    x += FF_W + MARGIN;
    bool open = ui->tab == PD_TAB_SETTINGS;
    chip(ui, c, x, GEAR_W, open, HIT_TAB + PD_TAB_SETTINGS);
    pd_bitmap(c, x + (GEAR_W - 11) / 2, 2 + (CHIP_H - 11) / 2, GEAR, 11, open ? PD_VALUE : PD_TITLE_TEXT);
}

void pd_ui_draw(struct pd_ui* ui, struct pd_canvas* c, const struct pd_game* g,
                const struct pd_snapshot* s, const struct pd_host_info* host) {
    ui->hitCount = 0;
    pd_backdrop(c);
    tab_bar(ui, c, host);
    if (ui->tab == PD_TAB_SETTINGS) {
        ui_settings_tab(ui, c, g, s, host);
    } else if (!g->cfg) {
        ui_notice(c, "NOT SUPPORTED", g->unsupported ? g->unsupported : "", "SETTINGS has what was detected.");
    } else if (ui->tab == PD_TAB_PARTY) {
        ui_party_tab(ui, c, s);
    } else if (ui->tab == PD_TAB_BAG) {
        ui_bag_tab(ui, c, g, s);
    } else {
        ui_battle_tab(ui, c, s);
    }
    ui_overlay(ui, c);
}

static void close_overlay(struct pd_ui* ui, enum pd_action action) {
    ui->overlay = PD_OVERLAY_NONE;
    if (action != PD_ACTION_NONE) ui->action = action;
}

static void act(struct pd_ui* ui, int id) {
    if (ui->overlay != PD_OVERLAY_NONE) {
        if (id >= HIT_OPTION && id < HIT_OPTION + 8) {
            int i = id - HIT_OPTION;
            if (ui->overlay == PD_OVERLAY_PICK_SCREEN) ui->settings->screenMode = i;
            if (ui->overlay == PD_OVERLAY_PICK_FF) ui->settings->ffSpeed = PD_FF_MIN + i;
            close_overlay(ui, PD_ACTION_SETTINGS_CHANGED);
        } else if (id == HIT_YES) {
            enum pd_action a = ui->overlay == PD_OVERLAY_CONFIRM_SAVE ? PD_ACTION_SAVE_STATE
                : ui->overlay == PD_OVERLAY_CONFIRM_LOAD ? PD_ACTION_LOAD_STATE
                : ui->overlay == PD_OVERLAY_CONFIRM_LEAVE ? PD_ACTION_LEAVE_GAME : PD_ACTION_NONE;
            close_overlay(ui, a);
        } else if (id == HIT_NO || id == HIT_SCRIM) {
            close_overlay(ui, PD_ACTION_NONE);
        }
        return;
    }
    if (id >= HIT_TAB && id < HIT_TAB + PD_TAB_COUNT) {
        ui->tab = (enum pd_tab) (id - HIT_TAB);
        ui->summarySlot = -1;
    } else if (id == HIT_FF) {
        ui->action = PD_ACTION_TOGGLE_FF;
    } else if (id >= HIT_SLOT && id < HIT_SLOT + PD_PARTY_SIZE) {
        ui->summarySlot = id - HIT_SLOT;
    } else if (id == HIT_BACK) {
        pd_ui_back(ui);
    } else if (id == HIT_POCKET_PREV || id == HIT_POCKET_NEXT) {
        int dir = id == HIT_POCKET_NEXT ? 1 : -1;
        ui->bagPocket = (ui->bagPocket + PD_POCKET_COUNT + dir) % PD_POCKET_COUNT;
        ui->bagSelected = -1;
        ui->bagScroll = 0;
    } else if (id >= HIT_BAG_ROW && id < HIT_BAG_ROW + PD_POCKET_SLOTS) {
        ui->bagSelected = id - HIT_BAG_ROW;
    } else if (id >= HIT_SETTING && id <= HIT_SETTING + SETTING_LEAVE) {
        static const enum pd_overlay OPENS[] = {
            [SETTING_SCREEN] = PD_OVERLAY_PICK_SCREEN,
            [SETTING_FF] = PD_OVERLAY_PICK_FF,
            [SETTING_SAVE_STATE] = PD_OVERLAY_CONFIRM_SAVE,
            [SETTING_LOAD_STATE] = PD_OVERLAY_CONFIRM_LOAD,
            [SETTING_LEAVE] = PD_OVERLAY_CONFIRM_LEAVE,
        };
        ui->overlay = OPENS[id - HIT_SETTING];
    }
}

bool pd_ui_touch(struct pd_ui* ui, int x, int y, bool down) {
    if (down) {
        const struct pd_hit* h = hit_at(ui, x, y);
        int id = h ? h->id : -1;
        if (!ui->touching) {
            // A press shows on the element it starts on ...
            ui->touching = true;
            ui->dragging = false;
            ui->touchScroll = h && h->scroll;
            ui->touchX0 = x;
            ui->touchY0 = y;
            ui->touchLastY = y;
            ui->pressedId = id;
            return id != -1;
        }
        // ... a list scrolls under a drag ...
        if (ui->touchScroll && !ui->dragging && abs(y - ui->touchY0) > DRAG_SLOP) {
            ui->dragging = true;
            ui->pressedId = -1;
        }
        if (ui->dragging) {
            ui->bagScroll -= y - ui->touchLastY;
            ui->touchLastY = y;
            return true;
        }
        ui->touchLastY = y;
        // ... and sliding off an element cancels it; another is never picked up.
        if (ui->pressedId != -1 && id != ui->pressedId) {
            ui->pressedId = -1;
            return true;
        }
        return false;
    }
    if (!ui->touching) return false;
    ui->touching = false;
    if (ui->dragging) {
        ui->dragging = false;
        return true;
    }
    int id = ui->pressedId;
    ui->pressedId = -1;
    if (id == -1) return false;
    act(ui, id);
    return true;
}

void pd_ui_next_tab(struct pd_ui* ui, int dir) {
    if (ui->overlay != PD_OVERLAY_NONE) return;
    ui->tab = (enum pd_tab) ((ui->tab + PD_TAB_COUNT + dir) % PD_TAB_COUNT);
    ui->summarySlot = -1;
}

bool pd_ui_back(struct pd_ui* ui) {
    if (ui->overlay != PD_OVERLAY_NONE) {
        ui->overlay = PD_OVERLAY_NONE;
        return true;
    }
    if (ui->summarySlot >= 0) {
        ui->summarySlot = -1;
        return true;
    }
    if (ui->tab == PD_TAB_BAG && ui->bagSelected >= 0) {
        ui->bagSelected = -1;
        return true;
    }
    return false;
}

enum pd_action pd_ui_take_action(struct pd_ui* ui) {
    enum pd_action a = ui->action;
    ui->action = PD_ACTION_NONE;
    return a;
}

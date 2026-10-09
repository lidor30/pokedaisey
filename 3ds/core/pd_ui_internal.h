// Shared by the companion's tab files (pd_ui_*.c): layout, hit ids and the
// small widgets every tab draws with.
#ifndef PD_UI_INTERNAL_H
#define PD_UI_INTERNAL_H

#include <stddef.h>

#include "pd_style.h"
#include "pd_ui.h"

#define CONTENT_Y 28
#define MARGIN 4
#define ROW_H 18

// Hit ids.
enum {
    HIT_TAB = 100,         // + tab
    HIT_FF = 110,
    HIT_SLOT = 200,        // + party slot
    HIT_BACK = 300,
    HIT_POCKET_PREV = 400,
    HIT_POCKET_NEXT = 401,
    HIT_BAG_LIST = 410,
    HIT_BAG_ROW = 500,     // + item index
    HIT_SETTING = 600,     // + enum setting_row
    HIT_OPTION = 700,      // + pick-list option
    HIT_YES = 800,
    HIT_NO = 801,
    HIT_SCRIM = 802,
};

enum setting_row {
    SETTING_SCREEN,
    SETTING_FF,
    SETTING_SAVE_STATE,
    SETTING_LOAD_STATE,
    SETTING_LEAVE,
};

void ui_add_hit(struct pd_ui* ui, int x, int y, int w, int h, int id);
void ui_add_scroll_hit(struct pd_ui* ui, int x, int y, int w, int h, int id);

// A white framed button; pressed = grey, like the tab chips.
void ui_button(struct pd_ui* ui, struct pd_canvas* c, int x, int y, int w, int h, const char* label, int id);
// The party menu's HP bar: a dark frame, the "HP" tag, a two-tone fill.
void ui_hp_bar(struct pd_canvas* c, int x, int y, int barW, int hp, int maxHp);
void ui_status_badge(struct pd_canvas* c, int x, int y, uint32_t status);
// A message in a list window across the content area.
void ui_notice(struct pd_canvas* c, const char* title, const char* line1, const char* line2);
// A LABEL  VALUE row, the value from ~42% across (OptionLine).
void ui_row(struct pd_canvas* c, int x, int y, int w, const char* label, const char* value, bool selected);
void ui_format_money(long v, char* buf, size_t len);

void ui_party_tab(struct pd_ui* ui, struct pd_canvas* c, const struct pd_snapshot* s);
void ui_battle_tab(struct pd_ui* ui, struct pd_canvas* c, const struct pd_snapshot* s);
void ui_bag_tab(struct pd_ui* ui, struct pd_canvas* c, const struct pd_game* g, const struct pd_snapshot* s);
void ui_settings_tab(struct pd_ui* ui, struct pd_canvas* c, const struct pd_game* g, const struct pd_snapshot* s,
                     const struct pd_host_info* host);
void ui_overlay(struct pd_ui* ui, struct pd_canvas* c);

#endif

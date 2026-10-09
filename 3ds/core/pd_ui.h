// The companion on the 3DS's 320x240 bottom screen: PARTY (with a summary on
// tap), BATTLE (cards + your moves' verdicts) and INFO, in the app's FireRed
// OPTION-screen look (app/.../companion/ui/GbaMenu.kt). Immediate mode: every
// pd_ui_draw records where its tappable parts went, and pd_ui_touch looks a
// tap up there - no layout pass to keep in sync.
#ifndef PD_UI_H
#define PD_UI_H

#include <stdbool.h>

#include "pd_canvas.h"
#include "pd_game.h"
#include "pd_snapshot.h"

#define PD_UI_WIDTH 320
#define PD_UI_HEIGHT 240

enum pd_tab {
    PD_TAB_PARTY,
    PD_TAB_BATTLE,
    PD_TAB_INFO,
    PD_TAB_COUNT,
};

// Extra INFO rows the host fills in (frame rate, ROM file, save state ...).
#define PD_UI_HOST_LINES 4
struct pd_host_info {
    const char* labels[PD_UI_HOST_LINES];
    char values[PD_UI_HOST_LINES][40];
    int count;
};

#define PD_UI_MAX_HITS 24
struct pd_hit {
    int x, y, w, h;
    int id;
};

struct pd_ui {
    enum pd_tab tab;
    enum pd_tab tabBeforeBattle; // the tab a battle took over from
    bool battleWasOn;
    int summarySlot; // party slot whose summary is open, -1 = none
    bool touching;
    int pressedId;   // what the stylus went down on (-1 = nothing)
    struct pd_hit hits[PD_UI_MAX_HITS];
    int hitCount;
};

void pd_ui_init(struct pd_ui* ui);
// Follows the game: a battle opens BATTLE, its end goes back.
void pd_ui_update(struct pd_ui* ui, const struct pd_snapshot* s);
void pd_ui_draw(struct pd_ui* ui, struct pd_canvas* c, const struct pd_game* g,
                const struct pd_snapshot* s, const struct pd_host_info* host);
// Stylus: down / held / up at (x, y). Returns true if the screen needs a redraw.
bool pd_ui_touch(struct pd_ui* ui, int x, int y, bool down);
// Buttons the GBA doesn't have (the 3DS's X / Y): next / previous tab, BACK.
void pd_ui_next_tab(struct pd_ui* ui, int dir);
bool pd_ui_back(struct pd_ui* ui);

#endif

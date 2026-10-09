// The game list on the bottom screen (an OPTION-style pick-list, D-pad or
// stylus) and the top screen's splash while no game runs.
#ifndef PD_MENU_H
#define PD_MENU_H

#include <stdbool.h>

#include "pd_canvas.h"

struct pd_menu {
    const char* title;
    const char* const* items;
    int count;
    int selected;
    int scroll;
    bool touching;
    int pressed; // row the stylus went down on, -1 = none
};

void pd_menu_init(struct pd_menu* m, const char* title, const char* const* items, int count);
void pd_menu_draw(struct pd_menu* m, struct pd_canvas* c);
void pd_menu_move(struct pd_menu* m, int delta);
// Stylus down / up; returns the row a tap picked (on release), else -1.
int pd_menu_touch(struct pd_menu* m, int x, int y, bool down);

// The top screen: the name, then up to three lines of help.
void pd_splash_draw(struct pd_canvas* c, const char* line1, const char* line2, const char* line3);

#endif

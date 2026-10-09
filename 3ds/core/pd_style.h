// The FireRed OPTION screen's look, shared by the companion and the ROM list:
// OptionColors (app/.../companion/ui/GbaMenu.kt) and the window boxes built
// from them.
#ifndef PD_STYLE_H
#define PD_STYLE_H

#include "pd_canvas.h"
#include "pd_font.h"

#define PD_TITLE_FILL 0xFFFFFF
#define PD_TITLE_TEXT 0x636363
#define PD_TITLE_SHADOW 0xD6D6CE
#define PD_LIST_FILL 0xE0DFDF
#define PD_ROW_SELECTED 0xFFFFFF
#define PD_LABEL 0x575656
#define PD_LABEL_SHADOW 0xBCBBB4
#define PD_VALUE 0xCB0707
#define PD_VALUE_SHADOW 0xE0A564
#define PD_MUTED 0x8C8C94
#define PD_MUTED_SHADOW 0xD6D6D6
#define PD_DIVIDER 0xC6C5C5
// Text straight on the backdrop: white with the hint bar's grey shadow.
#define PD_ON_BACKDROP 0xFFFFFF
#define PD_ON_BACKDROP_SHADOW 0x404850
// FireRed's party backdrop stripes (FIRERED_BACKDROP in theme/Theme.kt) -
// the plain stripes the app shows until a FireRed ROM supplied the art.
#define PD_BACKDROP_A 0x4AADA5
#define PD_BACKDROP_B 0x398C8C
#define PD_BACKDROP_BAND 0x216B63

// The white title window and the grey list window; each returns its frame's width.
int pd_title_box(struct pd_canvas* c, int x, int y, int w, int h, uint32_t fill);
int pd_list_box(struct pd_canvas* c, int x, int y, int w, int h);
void pd_backdrop(struct pd_canvas* c);
// Text centred in a row of height h at y (the cell is 16 px, caps at rows 4-12).
static inline int pd_text_y(int y, int h) {
    return y + (h - PD_FONT_HEIGHT) / 2 + 1;
}

#endif

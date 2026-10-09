#include "pd_style.h"

static const struct pd_layer TITLE_LAYERS[] = { { 0x63737B, 2 }, { 0xCED6D6, 1 } };
static const struct pd_layer LIST_LAYERS[] = {
    { 0x293131, 1 }, { 0x8C8CCE, 1 }, { 0x736B84, 2 }, { 0xDED6DE, 1 }, { 0xFFFFFF, 2 },
};
#define N_LAYERS(a) ((int) (sizeof(a) / sizeof((a)[0])))

int pd_title_box(struct pd_canvas* c, int x, int y, int w, int h, uint32_t fill) {
    return pd_layered_box(c, x, y, w, h, 3, TITLE_LAYERS, N_LAYERS(TITLE_LAYERS), fill);
}

int pd_list_box(struct pd_canvas* c, int x, int y, int w, int h) {
    return pd_layered_box(c, x, y, w, h, 3, LIST_LAYERS, N_LAYERS(LIST_LAYERS), PD_LIST_FILL);
}

void pd_backdrop(struct pd_canvas* c) {
    for (int y = 0; y < c->h; y += 8) pd_fill(c, 0, y, c->w, 8, (y / 8) % 2 ? PD_BACKDROP_B : PD_BACKDROP_A);
    pd_fill(c, 0, c->h - 4, c->w, 4, PD_BACKDROP_BAND);
}

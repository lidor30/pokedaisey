// pd_preview - renders the 3DS companion from a RAM fixture (the ewram.bin /
// iwram.bin dumps under app/src/test/resources/fixtures/) into PNGs laid out
// like the 3DS: the 400x240 top screen over the 320x240 bottom one. No ROM, no
// emulator: the 3DS port's ui-preview, for looking at every UI change.
//
//   pd_preview <fixture dir> <BPRE|BPGE|BPEE> <out dir> <name>
//
// writes <name>-party.png, -pressed, -summary, -battle, -bag, -bag-item,
// -bag-scrolled, -bag-pocket, -settings, -pick-screen and -confirm-save.
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "../core/pd_canvas.h"
#include "../core/pd_game.h"
#include "../core/pd_snapshot.h"
#include "../core/pd_ui.h"
#include "../core/pd_ui_internal.h"
#include "pd_png.h"

#define TOP_W 400
#define TOP_H 240

static uint8_t ewram[0x40000];
static uint8_t iwram[0x8000];

static bool load(const char* dir, const char* name, uint8_t* buf, size_t len) {
    char path[1024];
    snprintf(path, sizeof(path), "%s/%s", dir, name);
    FILE* f = fopen(path, "rb");
    if (!f) return false;
    size_t n = fread(buf, 1, len, f);
    fclose(f);
    return n == len;
}

static bool fixture_read(void* ctx, uint32_t addr, void* out, size_t len) {
    (void) ctx;
    if (addr >= 0x02000000 && addr + len <= 0x02040000) {
        memcpy(out, ewram + (addr - 0x02000000), len);
        return true;
    }
    if (addr >= 0x03000000 && addr + len <= 0x03008000) {
        memcpy(out, iwram + (addr - 0x03000000), len);
        return true;
    }
    return false;
}

// Both screens in one picture: the top screen (here a stand-in for the game
// at 1x, centred) over the bottom one, centred like on the console.
static void shot(const char* dir, const char* name, const char* what, const uint32_t* bottom) {
    static uint32_t img[TOP_W * TOP_H * 2];
    for (int i = 0; i < TOP_W * TOP_H * 2; i++) img[i] = 0x101010;
    struct pd_canvas top;
    pd_canvas_init(&top, TOP_W, TOP_H, img);
    pd_fill(&top, 0, 0, TOP_W, TOP_H, 0x202020);
    int gx = (TOP_W - 240) / 2, gy = (TOP_H - 160) / 2;
    pd_fill(&top, gx, gy, 240, 160, 0x404850);
    pd_text(&top, gx + 8, gy + 4, "GAME (fixture: no emulator)", 0xFFFFFF, 0x202020);
    for (int y = 0; y < PD_UI_HEIGHT; y++) {
        memcpy(img + (TOP_H + y) * TOP_W + (TOP_W - PD_UI_WIDTH) / 2, bottom + y * PD_UI_WIDTH, PD_UI_WIDTH * sizeof(uint32_t));
    }
    char path[1024];
    snprintf(path, sizeof(path), "%s/%s-%s.png", dir, name, what);
    if (!pd_png_write(path, TOP_W, TOP_H * 2, img)) {
        fprintf(stderr, "pd_preview: can't write %s\n", path);
        exit(1);
    }
    printf("%s\n", path);
}

// The stylus, driven by what the last draw recorded: each step finds its
// target by hit id, so the shots go through the same hit-testing a tap does.
struct preview {
    struct pd_ui* ui;
    struct pd_canvas* c;
    const struct pd_game* g;
    const struct pd_snapshot* s;
    const struct pd_host_info* host;
    const char* out;
    const char* name;
};

static void draw(struct preview* p) {
    pd_ui_draw(p->ui, p->c, p->g, p->s, p->host);
}

static void centre_of(struct preview* p, int id, int* x, int* y) {
    for (int i = p->ui->hitCount - 1; i >= 0; i--) {
        const struct pd_hit* h = &p->ui->hits[i];
        if (h->id == id) {
            *x = h->x + h->w / 2;
            *y = h->y + h->h / 2;
            return;
        }
    }
    fprintf(stderr, "pd_preview (%s): nothing to tap with id %d\n", p->name, id);
    exit(1);
}

static void press(struct preview* p, int id, int dy) {
    int x, y;
    centre_of(p, id, &x, &y);
    pd_ui_touch(p->ui, x, y + dy, true);
    draw(p);
}

static void release(struct preview* p) {
    pd_ui_touch(p->ui, 0, 0, false);
    draw(p);
}

static void tap(struct preview* p, int id, int dy) {
    press(p, id, dy);
    release(p);
}

// Down on id, then moved by dy in a few steps, then up.
static void drag(struct preview* p, int id, int dy) {
    int x, y;
    centre_of(p, id, &x, &y);
    pd_ui_touch(p->ui, x, y, true);
    for (int i = 1; i <= 4; i++) pd_ui_touch(p->ui, x, y + dy * i / 4, true);
    release(p);
}

int main(int argc, char** argv) {
    if (argc != 5) {
        fprintf(stderr, "usage: pd_preview <fixture dir> <BPRE|BPGE|BPEE> <out dir> <name>\n");
        return 2;
    }
    const char *dir = argv[1], *code = argv[2], *out = argv[3], *name = argv[4];
    if (!load(dir, "ewram.bin", ewram, sizeof(ewram)) || !load(dir, "iwram.bin", iwram, sizeof(iwram))) {
        fprintf(stderr, "pd_preview: %s needs ewram.bin (256 KB) and iwram.bin (32 KB)\n", dir);
        return 1;
    }
    uint8_t header[0xC0] = { 0 };
    memcpy(header + 0xAC, code, 4);
    header[0xBC] = strcmp(code, "BPEE") ? 1 : 0;
    struct pd_game game;
    pd_game_detect(&game, header, 0x1000000);

    struct pd_snapshot snap;
    pd_snapshot_read(&snap, &game, fixture_read, NULL);

    static uint32_t px[PD_UI_WIDTH * PD_UI_HEIGHT];
    struct pd_canvas c;
    pd_canvas_init(&c, PD_UI_WIDTH, PD_UI_HEIGHT, px);
    struct pd_settings settings = { .screenMode = PD_SCREEN_SHARP, .ffSpeed = 2 };
    struct pd_ui ui;
    pd_ui_init(&ui, &settings);
    pd_ui_update(&ui, &snap);

    struct pd_host_info host = { .count = 2, .hasState = true };
    host.labels[0] = "SOURCE";
    snprintf(host.values[0], sizeof(host.values[0]), "%s", name);
    host.labels[1] = "SPEED";
    snprintf(host.values[1], sizeof(host.values[1]), "100%% (60 FPS)");
    snprintf(host.stateWhen, sizeof(host.stateWhen), "10-09 12:30");

    struct preview p = { &ui, &c, &game, &snap, &host, out, name };
    draw(&p);
    // A battle fixture opens on BATTLE by itself.
    if (ui.tab != PD_TAB_PARTY) tap(&p, HIT_TAB + PD_TAB_PARTY, 0);
    shot(out, name, "party", px);

    // A stylus held on the second slot (the cursor's colours), then let go:
    // its summary opens.
    press(&p, HIT_SLOT + 1, 0);
    shot(out, name, "pressed", px);
    release(&p);
    shot(out, name, "summary", px);
    tap(&p, HIT_BACK, 0);

    tap(&p, HIT_TAB + PD_TAB_BATTLE, 0);
    shot(out, name, "battle", px);

    // BAG: a tapped item shows its description; a drag scrolls the list; the
    // arrow opens the next pocket.
    tap(&p, HIT_TAB + PD_TAB_BAG, 0);
    shot(out, name, "bag", px);
    tap(&p, HIT_BAG_ROW + 2, 0);
    shot(out, name, "bag-item", px);
    drag(&p, HIT_BAG_LIST, -60);
    shot(out, name, "bag-scrolled", px);
    tap(&p, HIT_POCKET_NEXT, 0);
    shot(out, name, "bag-pocket", px);

    // SETTINGS, its pick-list and a confirm. FF lit, as while it runs.
    host.ffOn = true;
    tap(&p, HIT_TAB + PD_TAB_SETTINGS, 0);
    shot(out, name, "settings", px);
    tap(&p, HIT_SETTING + SETTING_SCREEN, 0);
    shot(out, name, "pick-screen", px);
    tap(&p, HIT_OPTION + PD_SCREEN_STRETCH, 0);
    printf("action %d, screen mode %d\n", pd_ui_take_action(&ui), settings.screenMode);
    tap(&p, HIT_SETTING + SETTING_SAVE_STATE, 0);
    shot(out, name, "confirm-save", px);
    tap(&p, HIT_YES, 0);
    printf("action %d\n", pd_ui_take_action(&ui));

    // The decoded party, for checking against the app's tests.
    for (int i = 0; i < snap.partyCount; i++) {
        printf("party[%d] species=%d \"%s\" Lv%d %d/%d\n", i, snap.party[i].species, snap.party[i].nickname,
               snap.party[i].level, snap.party[i].hp, snap.party[i].maxHp);
    }
    printf("inBattle=%d mapsec=%d money=%ld\n", snap.inBattle, snap.mapsec, snap.money);
    for (int p = 0; p < PD_POCKET_COUNT; p++) {
        printf("%s:", pd_pocket_name(p));
        for (int i = 0; i < snap.bag[p].count; i++) {
            printf(" %s x%d,", pd_item_name(&game, snap.bag[p].items[i].id), snap.bag[p].items[i].quantity);
        }
        printf("\n");
    }
    return 0;
}

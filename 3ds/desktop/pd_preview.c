// pd_preview - renders the 3DS companion from a RAM fixture (the ewram.bin /
// iwram.bin dumps under app/src/test/resources/fixtures/) into PNGs laid out
// like the 3DS: the 400x240 top screen over the 320x240 bottom one. No ROM, no
// emulator: the 3DS port's ui-preview, for looking at every UI change.
//
//   pd_preview <fixture dir> <BPRE|BPGE|BPEE> <out dir> <name>
//
// writes <name>-party.png, -pressed.png, -summary.png, -battle.png, -info.png.
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "../core/pd_canvas.h"
#include "../core/pd_game.h"
#include "../core/pd_snapshot.h"
#include "../core/pd_ui.h"
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
    struct pd_ui ui;
    pd_ui_init(&ui);
    pd_ui_update(&ui, &snap);

    struct pd_host_info host = { .count = 2 };
    host.labels[0] = "SOURCE";
    snprintf(host.values[0], sizeof(host.values[0]), "%s", name);
    host.labels[1] = "FRAME";
    snprintf(host.values[1], sizeof(host.values[1]), "%u", (unsigned) snap.frame);

    ui.tab = PD_TAB_PARTY;
    pd_ui_draw(&ui, &c, &game, &snap, &host);
    shot(out, name, "party", px);

    // A stylus held on the second slot (the cursor's colours), then let go:
    // its summary opens.
    pd_ui_touch(&ui, 240, 40, true);
    pd_ui_draw(&ui, &c, &game, &snap, &host);
    shot(out, name, "pressed", px);
    pd_ui_touch(&ui, 240, 40, false);
    pd_ui_draw(&ui, &c, &game, &snap, &host);
    shot(out, name, "summary", px);

    ui.summarySlot = -1;
    ui.tab = PD_TAB_BATTLE;
    pd_ui_draw(&ui, &c, &game, &snap, &host);
    shot(out, name, "battle", px);

    ui.tab = PD_TAB_INFO;
    pd_ui_draw(&ui, &c, &game, &snap, &host);
    shot(out, name, "info", px);

    // The decoded party, for checking against the app's tests.
    for (int i = 0; i < snap.partyCount; i++) {
        printf("party[%d] species=%d \"%s\" Lv%d %d/%d\n", i, snap.party[i].species, snap.party[i].nickname,
               snap.party[i].level, snap.party[i].hp, snap.party[i].maxHp);
    }
    printf("inBattle=%d mapsec=%d money=%ld\n", snap.inBattle, snap.mapsec, snap.money);
    return 0;
}

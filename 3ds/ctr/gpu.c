#include "gpu.h"

#include <3ds.h>
#include <citro2d.h>
#include <string.h>

#include "../core/pd_ui.h"

#define GBA_W 240
#define GBA_H 160
#define TOP_W 400
#define TOP_H 240

static C3D_RenderTarget* topTarget;
static C3D_RenderTarget* bottomTarget;

// mGBA renders into gameBuf; each frame it's written into the texture the
// screen mode needs: gameTex at 1x (nearest), or game2xTex at 2x for the
// smooth modes - mGBA's "sharp bilinear", the 2x nearest copy sampled
// linearly. Everything is written by the CPU in the GPU's tiled order: no
// display transfers, no render-to-texture.
static uint16_t* gameBuf;
static C3D_Tex gameTex, game2xTex;
static C3D_Tex bottomTex, topTex;

// Texel (x, y) of a w-wide texture in the GPU's layout: 8x8 tiles, row by
// row, Morton (Z) order inside a tile. The first row is the image's top
// (v = 1) - checked in Azahar, where writing rows bottom-up drew everything
// upside down.
static inline u32 tiled(u32 x, u32 y, u32 w) {
    return ((((y >> 3) * (w >> 3)) + (x >> 3)) << 6) |
        ((x & 1) | ((y & 1) << 1) | ((x & 2) << 1) | ((y & 2) << 2) | ((x & 4) << 2) | ((y & 4) << 3));
}

// A w x h image in the texture's first w x h texels, its top row at v = 1.
static C2D_Image image(C3D_Tex* tex, Tex3DS_SubTexture* sub, int w, int h) {
    sub->width = (u16) w;
    sub->height = (u16) h;
    sub->left = 0.0f;
    sub->top = 1.0f;
    sub->right = (float) w / tex->width;
    sub->bottom = 1.0f - (float) h / tex->height;
    return (C2D_Image) { tex, sub };
}

static bool tex_init(C3D_Tex* tex, int w, int h, GPU_TEXTURE_FILTER_PARAM filter) {
    if (!C3D_TexInit(tex, (u16) w, (u16) h, GPU_RGB565)) return false;
    C3D_TexSetFilter(tex, filter, filter);
    C3D_TexSetWrap(tex, GPU_CLAMP_TO_EDGE, GPU_CLAMP_TO_EDGE);
    memset(tex->data, 0, tex->size);
    GSPGPU_FlushDataCache(tex->data, tex->size);
    return true;
}

bool gpu_init(void) {
    gfxInitDefault();
    if (!C3D_Init(C3D_DEFAULT_CMDBUF_SIZE)) return false;
    if (!C2D_Init(C2D_DEFAULT_MAX_OBJECTS)) return false;
    C2D_Prepare();
    topTarget = C2D_CreateScreenTarget(GFX_TOP, GFX_LEFT);
    bottomTarget = C2D_CreateScreenTarget(GFX_BOTTOM, GFX_LEFT);

    gameBuf = linearMemAlign(GPU_GAME_STRIDE * GBA_H * sizeof(uint16_t), 0x80);
    if (!gameBuf) return false;
    memset(gameBuf, 0, GPU_GAME_STRIDE * GBA_H * sizeof(uint16_t));
    return tex_init(&gameTex, 256, 256, GPU_NEAREST) && tex_init(&game2xTex, 512, 512, GPU_LINEAR) &&
        tex_init(&bottomTex, 512, 256, GPU_NEAREST) && tex_init(&topTex, 512, 256, GPU_NEAREST);
}

void gpu_exit(void) {
    C3D_TexDelete(&gameTex);
    C3D_TexDelete(&game2xTex);
    C3D_TexDelete(&bottomTex);
    C3D_TexDelete(&topTex);
    if (gameBuf) linearFree(gameBuf);
    C2D_Fini();
    C3D_Fini();
    gfxExit();
}

uint16_t* gpu_game_buffer(void) {
    return gameBuf;
}

// A 0xRRGGBB canvas into an RGB565 texture.
static void write_canvas(C3D_Tex* tex, const uint32_t* px, int w, int h) {
    uint16_t* t = tex->data;
    for (int y = 0; y < h; y++) {
        const uint32_t* row = px + y * w;
        for (int x = 0; x < w; x++) {
            uint32_t p = row[x];
            t[tiled((u32) x, (u32) y, tex->width)] =
                (uint16_t) (((p >> 19) & 0x1F) << 11 | ((p >> 10) & 0x3F) << 5 | ((p >> 3) & 0x1F));
        }
    }
    GSPGPU_FlushDataCache(tex->data, tex->size);
}

// Canvases are written when they change; C3D_FrameBegin (SYNCDRAW) has waited
// for the GPU to finish the frame that read them.
static const uint32_t* pendingBottom;
static const uint32_t* pendingTop;

void gpu_set_bottom(const uint32_t* px) {
    pendingBottom = px;
}

void gpu_set_top(const uint32_t* px) {
    pendingTop = px;
}

static void write_game_1x(void) {
    uint16_t* t = gameTex.data;
    for (int y = 0; y < GBA_H; y++) {
        const uint16_t* row = gameBuf + y * GPU_GAME_STRIDE;
        for (int x = 0; x < GBA_W; x++) t[tiled((u32) x, (u32) y, gameTex.width)] = row[x];
    }
    GSPGPU_FlushDataCache(gameTex.data, gameTex.size);
}

// Each game pixel becomes a 2x2 block - in the tiled order an aligned 2x2
// block is 4 consecutive texels, so one 64-bit store.
static void write_game_2x(void) {
    uint64_t* t = game2xTex.data;
    for (int y = 0; y < GBA_H; y++) {
        const uint16_t* row = gameBuf + y * GPU_GAME_STRIDE;
        for (int x = 0; x < GBA_W; x++) {
            uint64_t c = row[x];
            t[tiled((u32) (2 * x), (u32) (2 * y), game2xTex.width) >> 2] = c | c << 16 | c << 32 | c << 48;
        }
    }
    GSPGPU_FlushDataCache(game2xTex.data, game2xTex.size);
}

void gpu_draw(bool game, int screenMode) {
    C3D_FrameBegin(C3D_FRAME_SYNCDRAW);
    if (pendingBottom) {
        write_canvas(&bottomTex, pendingBottom, PD_UI_WIDTH, PD_UI_HEIGHT);
        pendingBottom = NULL;
    }
    if (pendingTop) {
        write_canvas(&topTex, pendingTop, TOP_W, TOP_H);
        pendingTop = NULL;
    }
    Tex3DS_SubTexture sub;
    u32 black = C2D_Color32(0, 0, 0, 0xFF);
    C2D_TargetClear(topTarget, black);
    C2D_SceneBegin(topTarget);
    if (!game) {
        C2D_DrawImageAt(image(&topTex, &sub, TOP_W, TOP_H), 0, 0, 0.5f, NULL, 1.0f, 1.0f);
    } else if (screenMode == PD_SCREEN_PIXEL) {
        write_game_1x();
        C2D_DrawImageAt(image(&gameTex, &sub, GBA_W, GBA_H), (TOP_W - GBA_W) / 2, (TOP_H - GBA_H) / 2, 0.5f, NULL,
                        1.0f, 1.0f);
    } else {
        write_game_2x();
        C2D_Image up = image(&game2xTex, &sub, 2 * GBA_W, 2 * GBA_H);
        if (screenMode == PD_SCREEN_STRETCH) {
            C2D_DrawImageAt(up, 0, 0, 0.5f, NULL, (float) TOP_W / (2 * GBA_W), (float) TOP_H / (2 * GBA_H));
        } else {
            float s = (float) TOP_H / (2 * GBA_H); // 240 / 320: the game at 1.5x
            C2D_DrawImageAt(up, (TOP_W - 2 * GBA_W * s) / 2, 0, 0.5f, NULL, s, s);
        }
    }

    Tex3DS_SubTexture bottomSub;
    C2D_TargetClear(bottomTarget, black);
    C2D_SceneBegin(bottomTarget);
    C2D_DrawImageAt(image(&bottomTex, &bottomSub, PD_UI_WIDTH, PD_UI_HEIGHT), 0, 0, 0.5f, NULL, 1.0f, 1.0f);
    C3D_FrameEnd(0);
}

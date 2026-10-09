// The screens, drawn by the GPU (citro3d / citro2d): the game frame and the
// two canvases are written into textures by the CPU (in the GPU's tiled
// order) and drawn as quads. The smooth screen modes use mGBA's own "sharp
// bilinear" look: the frame at 2x nearest, sampled linearly down to size.
#ifndef GPU_H
#define GPU_H

#include <stdbool.h>
#include <stdint.h>

#define GPU_GAME_STRIDE 256 // the game buffer's row length in pixels

bool gpu_init(void);
void gpu_exit(void);

// mGBA's video buffer: RGB565, GPU_GAME_STRIDE x 160, in linear memory.
uint16_t* gpu_game_buffer(void);
// New canvases (0xRRGGBB): the bottom screen's 320x240, the top's 400x240.
void gpu_set_bottom(const uint32_t* px);
void gpu_set_top(const uint32_t* px);

// One frame: the game (in screenMode, enum pd_screen_mode) or the top canvas
// on the top screen, the bottom canvas below. Waits for the screen's refresh.
void gpu_draw(bool game, int screenMode);

#endif

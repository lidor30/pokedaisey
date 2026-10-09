// A minimal PNG writer (stored deflate blocks, no zlib needed) for preview shots.
#ifndef PD_PNG_H
#define PD_PNG_H

#include <stdbool.h>
#include <stdint.h>

// px: w*h pixels, 0xRRGGBB.
bool pd_png_write(const char* path, int w, int h, const uint32_t* px);

#endif

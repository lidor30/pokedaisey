#include "pd_png.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static uint32_t crc_table[256];

static void crc_init(void) {
    for (uint32_t n = 0; n < 256; n++) {
        uint32_t c = n;
        for (int k = 0; k < 8; k++) c = (c & 1) ? 0xEDB88320u ^ (c >> 1) : c >> 1;
        crc_table[n] = c;
    }
}

static uint32_t crc(uint32_t c, const uint8_t* b, size_t n) {
    c ^= 0xFFFFFFFFu;
    for (size_t i = 0; i < n; i++) c = crc_table[(c ^ b[i]) & 0xFF] ^ (c >> 8);
    return c ^ 0xFFFFFFFFu;
}

static void be32(uint8_t* p, uint32_t v) {
    p[0] = (uint8_t) (v >> 24);
    p[1] = (uint8_t) (v >> 16);
    p[2] = (uint8_t) (v >> 8);
    p[3] = (uint8_t) v;
}

static void chunk(FILE* f, const char* type, const uint8_t* data, uint32_t len) {
    uint8_t hdr[8];
    be32(hdr, len);
    memcpy(hdr + 4, type, 4);
    fwrite(hdr, 1, 8, f);
    if (len) fwrite(data, 1, len, f);
    uint32_t c = crc(0, hdr + 4, 4);
    c = crc(c, data, len);
    uint8_t t[4];
    be32(t, c);
    fwrite(t, 1, 4, f);
}

bool pd_png_write(const char* path, int w, int h, const uint32_t* px) {
    crc_init();
    FILE* f = fopen(path, "wb");
    if (!f) return false;
    static const uint8_t sig[8] = { 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' };
    fwrite(sig, 1, 8, f);
    uint8_t ihdr[13];
    be32(ihdr, (uint32_t) w);
    be32(ihdr + 4, (uint32_t) h);
    ihdr[8] = 8;  // bit depth
    ihdr[9] = 2;  // RGB
    ihdr[10] = ihdr[11] = ihdr[12] = 0;
    chunk(f, "IHDR", ihdr, 13);

    // Raw scanlines (filter 0), then zlib with stored blocks.
    size_t rowLen = (size_t) w * 3 + 1;
    size_t rawLen = rowLen * (size_t) h;
    uint8_t* raw = malloc(rawLen);
    for (int y = 0; y < h; y++) {
        uint8_t* r = raw + rowLen * (size_t) y;
        r[0] = 0;
        for (int x = 0; x < w; x++) {
            uint32_t p = px[y * w + x];
            r[1 + x * 3] = (uint8_t) (p >> 16);
            r[2 + x * 3] = (uint8_t) (p >> 8);
            r[3 + x * 3] = (uint8_t) p;
        }
    }
    size_t blocks = (rawLen + 65534) / 65535;
    size_t zLen = 2 + rawLen + blocks * 5 + 4;
    uint8_t* z = malloc(zLen);
    size_t o = 0;
    z[o++] = 0x78;
    z[o++] = 0x01;
    uint32_t a = 1, b = 0;
    for (size_t i = 0; i < rawLen; i += 65535) {
        size_t n = rawLen - i < 65535 ? rawLen - i : 65535;
        z[o++] = (uint8_t) (i + n == rawLen);
        z[o++] = (uint8_t) n;
        z[o++] = (uint8_t) (n >> 8);
        z[o++] = (uint8_t) ~n;
        z[o++] = (uint8_t) (~n >> 8);
        memcpy(z + o, raw + i, n);
        o += n;
        for (size_t k = 0; k < n; k++) {
            a = (a + raw[i + k]) % 65521;
            b = (b + a) % 65521;
        }
    }
    be32(z + o, (b << 16) | a);
    o += 4;
    chunk(f, "IDAT", z, (uint32_t) o);
    chunk(f, "IEND", NULL, 0);
    free(raw);
    free(z);
    return fclose(f) == 0;
}

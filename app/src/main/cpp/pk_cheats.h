// PokeDaisy — cheats on mGBA's cheat engine (pk_cheats.c).
#pragma once

#include <stddef.h>
#include <stdint.h>

struct mCore;

void pk_cheats_clear(struct mCore* core);
int pk_cheats_apply(struct mCore* core, const char* text, size_t len);
void pk_cheats_overlay(uint32_t addr, uint8_t* dst, int len);
void pk_cheats_swap_rom(struct mCore* core);
int pk_cheats_rom_patched(void);
char* pk_cheats_check(const char* code, int type, const char* directive);

// pokedaisy_jni.c: swaps the cheated ROM bytes with the file's, under the core lock.
void pkRomSwapCheats(void);

#!/usr/bin/env python3
"""Fingerprints of the FireRed / Emerald graphics the companion rebuilds from
the player's own ROM (companion/data/RomArt.kt) - the party-menu slot, Poke
Ball, status icons, small font, party backdrop, region maps, the region
map's player icons and the trainer card's pieces (kept raw for
TrainerCardArt.kt). Nothing of the
ROM is bundled: per blob this stores only

  size    decoded length (LZ77 blobs: the decompressed length)
  crc     CRC32 of the decoded bytes
  headOff / head   where a distinctive 16-byte window sits inside the blob's
          raw ROM bytes, and a 64-bit hash of it (RomArt.head) - what the
          one-pass ROM scan looks for before checking size + crc.
  pre     a 16-bit hash of the window's first 8 bytes (RomArt.pre): the scan's
          cheap per-byte filter, before it computes the full head.

Retail, QoL builds and hacks that kept the art all match, wherever the linker
put it. Source: the pinned decomp builds (see CLAUDE.md) and their ELFs.

Usage: scripts/gen_rom_art_sigs.py   (needs $DECOMPS/pokefirered and
$DECOMPS/pokeemerald built once, and the Unbound ROM at UNBOUND_ROM; re-run only
if a pin changes)
"""
import os
import subprocess
import zlib

from decomps import decomp

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = decomp()
KOTLIN = os.path.join(HERE, "../app/src/main/kotlin/com/pokedaisey/app/companion/data/RomArtSigsGen.kt")

M64 = (1 << 64) - 1


def fmix(k):
    k ^= k >> 33
    k = (k * 0xFF51AFD7ED558CCD) & M64
    k ^= k >> 33
    k = (k * 0xC4CEB9FE1A85EC53) & M64
    k ^= k >> 33
    return k


def pre(b):
    """RomArt.pre: the first 8 bytes as a little-endian long, times an odd constant, top 16 bits."""
    return ((int.from_bytes(b[0:8], "little") * 0x9E3779B97F4A7C15) & M64) >> 48


def head(b):
    """RomArt.head: two little-endian longs, mixed."""
    a, c = int.from_bytes(b[0:8], "little"), int.from_bytes(b[8:16], "little")
    return fmix(a ^ fmix(c))


def lz77(rom, off):
    """-> (decoded, raw length consumed)."""
    assert rom[off] == 0x10
    size = int.from_bytes(rom[off + 1:off + 4], "little")
    out, i = bytearray(), off + 4
    while len(out) < size:
        flags = rom[i]; i += 1
        for b in range(8):
            if len(out) >= size:
                break
            if flags & (0x80 >> b):
                d = rom[i] << 8 | rom[i + 1]; i += 2
                for _ in range((d >> 12) + 3):
                    out.append(out[-((d & 0xFFF) + 1)])
            else:
                out.append(rom[i]); i += 1
    return bytes(out), i - off


# Pokemon Unbound v2.1.1.1 (sha1 b4776b82...): a binary hack with no ELF. Its
# player's front pics replace Red / Leaf at the same pic ids (TRAINER_PIC_RED
# 135, _LEAF 136) in its own gTrainerFrontPicTable / PaletteTable (found by
# shape: 148 {ptr, size, tag = index} records, 18 / 20 literal-pool refs).
UNBOUND_ROM = os.path.expanduser("~/Downloads/Game ROMs & Emulation/gba/Pokemon - Unbound (v2.1.1.1).gba")
UNBOUND_PIC_TABLE = 0x23957C
UNBOUND_PAL_TABLE = 0x239A1C


def unbound_symbols(rom):
    """The pics' ROM offsets by made-up symbol name, read through Unbound's tables."""
    def entry(table, i):
        return int.from_bytes(rom[table + 8 * i:table + 8 * i + 4], "little") - 0x08000000, 0
    return {
        "PicMale": entry(UNBOUND_PIC_TABLE, 135), "PalMale": entry(UNBOUND_PAL_TABLE, 135),
        "PicFemale": entry(UNBOUND_PIC_TABLE, 136), "PalFemale": entry(UNBOUND_PAL_TABLE, 136),
    }


# (Kotlin name, symbol, LZ77?) per game.
GAMES = {
    "FR": ("pokefirered/pokefirered_rev1.gba", "pokefirered/pokefirered_rev1.elf", [
        ("FR_PARTY_BG_GFX", "gPartyMenuBg_Gfx", True),
        ("FR_PARTY_BG_PAL", "gPartyMenuBg_Pal", True),
        ("FR_PARTY_BG_MAP", "gPartyMenuBg_Tilemap", True),
        ("FR_SLOT_MAIN", "sSlotTilemap_Main", False),
        ("FR_SLOT_NO_HP", "sSlotTilemap_MainNoHP", False),
        ("FR_BALL_GFX", "gPartyMenuPokeball_Gfx", True),
        ("FR_BALL_PAL", "gPartyMenuPokeball_Pal", True),
        ("FR_STATUS_GFX", "gStatusGfx_Icons", True),
        ("FR_STATUS_PAL", "gStatusPal_Icons", True),
        ("FR_FONT_SMALL", "sFontSmallLatinGlyphs", False),
        ("FR_REGION_GFX", "sRegionMap_Gfx", True),
        ("FR_REGION_PAL", "sRegionMap_Pal", False),
        ("FR_KANTO_MAP", "sKanto_Tilemap", True),
        ("FR_SEVII123_MAP", "sSevii123_Tilemap", True),
        ("FR_SEVII45_MAP", "sSevii45_Tilemap", True),
        ("FR_SEVII67_MAP", "sSevii67_Tilemap", True),
        ("FR_PLAYER_RED_GFX", "sPlayerIcon_Red", True),
        ("FR_PLAYER_PAL", "sPlayerIcon_RedPal", False), # sPlayerIcon_LeafPal is the same colours
        ("FR_PLAYER_LEAF_GFX", "sPlayerIcon_Leaf", True),
        # The trainer card (src/trainer_card.c): card tiles + front/back/backdrop
        # tilemaps, a palette per star count, the female backdrop palette,
        # badges, the player's front pic, and FONT_NORMAL to print on it.
        ("FR_CARD_GFX", "gKantoTrainerCard_Gfx", True),
        ("FR_CARD_FRONT", "sKantoTrainerCardFront_Tilemap", True),
        ("FR_CARD_BACK", "sKantoTrainerCardBack_Tilemap", True),
        ("FR_CARD_BG", "sKantoTrainerCardBg_Tilemap", True),
        ("FR_CARD_PAL0", "gKantoTrainerCardBlue_Pal", False),
        ("FR_CARD_PAL1", "sKantoTrainerCardGreen_Pal", False),
        ("FR_CARD_PAL2", "sKantoTrainerCardBronze_Pal", False),
        ("FR_CARD_PAL3", "sKantoTrainerCardSilver_Pal", False),
        ("FR_CARD_PAL4", "sKantoTrainerCardGold_Pal", False),
        ("FR_CARD_FEMALE_PAL", "sKantoTrainerCardFemaleBg_Pal", False),
        ("FR_CARD_BADGES_PAL", "sKantoTrainerCardBadges_Pal", False),
        ("FR_CARD_BADGES_GFX", "sKantoTrainerCardBadges_Gfx", True),
        ("CARD_STAR_PAL", "sTrainerCardStar_Pal", False), # the same in Emerald
        ("FR_CARD_STICKERS_GFX", "sTrainerCardStickers_Gfx", True),
        ("FR_CARD_STICKER_PAL1", "sTrainerCardStickerPal1", False),
        ("FR_CARD_STICKER_PAL2", "sTrainerCardStickerPal2", False),
        ("FR_CARD_STICKER_PAL3", "sTrainerCardStickerPal3", False),
        ("FR_CARD_STICKER_PAL4", "sTrainerCardStickerPal4", False),
        ("FR_PIC_RED", "gTrainerFrontPic_Red", True),
        ("FR_PIC_RED_PAL", "gTrainerPalette_Red", True),
        ("FR_PIC_LEAF", "gTrainerFrontPic_Leaf", True),
        ("FR_PIC_LEAF_PAL", "gTrainerPalette_Leaf", True),
        ("FR_FONT_NORMAL", "sFontNormalLatinGlyphs", False),
        ("FR_FONT_NORMAL_WIDTHS", "sFontNormalLatinGlyphWidths", False),
    ]),
    "EM": ("pokeemerald/pokeemerald.gba", "pokeemerald/pokeemerald.elf", [
        ("EM_PARTY_BG_GFX", "gPartyMenuBg_Gfx", True),
        ("EM_PARTY_BG_PAL", "gPartyMenuBg_Pal", True),
        ("EM_PARTY_BG_MAP", "gPartyMenuBg_Tilemap", True),
        ("EM_SLOT_MAIN", "sSlotTilemap_Main", False),
        ("EM_SLOT_NO_HP", "sSlotTilemap_MainNoHP", False),
        ("EM_BALL_GFX", "gPartyMenuPokeball_Gfx", True),
        ("EM_BALL_PAL", "gPartyMenuPokeball_Pal", True),
        ("EM_STATUS_GFX", "gStatusGfx_Icons", True),
        ("EM_STATUS_PAL", "gStatusPal_Icons", True),
        ("EM_FONT_SMALL", "gFontSmallLatinGlyphs", False),
        ("EM_REGION_GFX", "sRegionMapBg_GfxLZ", True),
        ("EM_REGION_PAL", "sRegionMapBg_Pal", False),
        ("EM_REGION_MAP", "sRegionMapBg_TilemapLZ", True),
        ("EM_PLAYER_BRENDAN_GFX", "sRegionMapPlayerIcon_BrendanGfx", False),
        ("EM_PLAYER_BRENDAN_PAL", "sRegionMapPlayerIcon_BrendanPal", False),
        ("EM_PLAYER_MAY_GFX", "sRegionMapPlayerIcon_MayGfx", False),
        ("EM_PLAYER_MAY_PAL", "sRegionMapPlayerIcon_MayPal", False),
        ("EM_CARD_GFX", "gHoennTrainerCard_Gfx", True),
        ("EM_CARD_FRONT", "gHoennTrainerCardFront_Tilemap", True),
        ("EM_CARD_BACK", "gHoennTrainerCardBack_Tilemap", True),
        ("EM_CARD_BG", "gHoennTrainerCardBg_Tilemap", True),
        ("EM_CARD_PAL0", "gHoennTrainerCardGreen_Pal", False),
        ("EM_CARD_PAL1", "sHoennTrainerCardBronze_Pal", False),
        ("EM_CARD_PAL2", "sHoennTrainerCardCopper_Pal", False),
        ("EM_CARD_PAL3", "sHoennTrainerCardSilver_Pal", False),
        ("EM_CARD_PAL4", "sHoennTrainerCardGold_Pal", False),
        ("EM_CARD_FEMALE_PAL", "sHoennTrainerCardFemaleBg_Pal", False),
        ("EM_CARD_BADGES_PAL", "sHoennTrainerCardBadges_Pal", False),
        ("EM_CARD_BADGES_GFX", "sHoennTrainerCardBadges_Gfx", True),
        ("EM_PIC_BRENDAN", "gTrainerFrontPic_Brendan", True),
        ("EM_PIC_BRENDAN_PAL", "gTrainerPalette_Brendan", True),
        ("EM_PIC_MAY", "gTrainerFrontPic_May", True),
        ("EM_PIC_MAY_PAL", "gTrainerPalette_May", True),
        ("EM_FONT_NORMAL", "gFontNormalLatinGlyphs", False),
        ("EM_FONT_NORMAL_WIDTHS", "gFontNormalLatinGlyphWidths", False),
    ]),
    "UB": (UNBOUND_ROM, None, [
        ("UB_PIC_MALE", "PicMale", True),
        ("UB_PIC_MALE_PAL", "PalMale", True),
        ("UB_PIC_FEMALE", "PicFemale", True),
        ("UB_PIC_FEMALE_PAL", "PalFemale", True),
    ]),
}


def symbols(elf):
    out = {}
    for line in subprocess.run(["nm", "-S", elf], capture_output=True, text=True, check=True).stdout.splitlines():
        p = line.split()
        if len(p) == 4:
            out[p[3]] = (int(p[0], 16) - 0x08000000, int(p[1], 16))
    return out


def main():
    rows = []
    for game, (rom_path, elf, blobs) in GAMES.items():
        rom = open(os.path.join(ROOT, rom_path), "rb").read()
        syms = symbols(os.path.join(ROOT, elf)) if elf else unbound_symbols(rom)
        for name, sym, lz in blobs:
            off, size = syms[sym]
            data, raw_len = lz77(rom, off) if lz else (rom[off:off + size], size)
            # The first window (2-byte steps) seen no more often than the blob
            # itself (some are stored twice): an all-blank font glyph would
            # otherwise match everywhere.
            copies = rom.count(rom[off:off + raw_len])
            # The most varied window first; glyph-width tables and mostly-blank
            # palettes hold few distinct values, so settle for 4 there.
            for distinct in (6, 4):
                w = next((w for w in range(0, raw_len - 15, 2)
                          if len(set(rom[off + w:off + w + 16])) >= distinct
                          and rom.count(rom[off + w:off + w + 16]) == copies), None)
                if w is not None:
                    break
            else:
                raise SystemExit(f"{name}: no unique window")
            win = rom[off + w:off + w + 16]
            h = head(win)
            h = h - (1 << 64) if h >= 1 << 63 else h
            rows.append(f'    {name}({str(lz).lower()}, {len(data)}, 0x{zlib.crc32(data):08X}L, {w}, {h}L, 0x{pre(win):04X}),')
            print(f"{name}: {sym} @{off:#x} raw {raw_len} decoded {len(data)} window +{w}")
    kt = """package com.pokedaisey.app.companion.data

// GENERATED by scripts/gen_rom_art_sigs.py from the pinned pokefirered /
// pokeemerald builds - do not hand-edit; rerun the script instead.

/** A graphics blob [RomArt] looks for in the loaded ROM - see the script for the fields. */
enum class RomBlob(val lz: Boolean, val size: Int, val crc: Long, val headOff: Int, val head: Long, val pre: Int) {
""" + "\n".join(rows) + "\n}\n"
    # Long literals: 0x... > Long.MAX_VALUE don't compile as Long, so the CRCs stay
    # under 2^32 (fine) and the heads are emitted signed (above).
    open(KOTLIN, "w").write(kt)
    print("wrote", KOTLIN)


if __name__ == "__main__":
    main()

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
$DECOMPS/pokeemerald built once, and the Unbound / Lazarus / Seaglass / SoulGold / Glazed /
Imperium / Quetzal ROMs at UNBOUND_ROM / LAZARUS_ROM / SEAGLASS_ROM / SOULGOLD_ROM / GLAZED_ROM / IMPERIUM_ROM /
QUETZAL_ROM; re-run only
if a pin changes)
"""
import os
import subprocess
import zlib

import smol
from decomps import decomp

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = decomp()
KOTLIN = os.path.join(HERE, "../app/src/main/kotlin/com/pokedaisy/app/companion/data/RomArtSigsGen.kt")

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


# Pokemon Lazarus v2.0 (sha1 7dcdc7e2...): a pokeemerald build (gcc) with its own
# region map in Emerald's region_map.c. LoadRegionMapGfx's literal pool (ROM
# 0x1FC6A0) holds sRegionMapBg_GfxLZ, _TilemapLZ, BG_CHAR_ADDR(2),
# BG_SCREEN_ADDR(28), sRegionMapBg_Pal - only 2 palettes' worth (0x40) before
# the tiles, and the tiles only use colours 1-28.
LAZARUS_ROM = os.path.expanduser("~/Downloads/Game ROMs & Emulation/gba/Pokemon Lazarus (v2.0).gba")


def lazarus_symbols(rom):
    return {"RegionGfx": (0xCE1D20, 0), "RegionMap": (0xCE3248, 0), "RegionPal": (0xCE1CE0, 0x40)}


# Pokemon SoulGold v1.1.4 (sha1 ea5d369c...): a newer pokeemerald-expansion
# build whose bag screen draws a night sky of stars on BG3 (headless VRAM dump
# with the bag open, matched byte for byte). The literal pool next to the bag's
# graphics loads (ROM 0x0A3444) holds its smol tiles (mode 6, 80 tiles), its
# smol tilemap (mode 8, 32x32) and the raw palette (BG palette 0).
SOULGOLD_ROM = os.path.expanduser("~/Downloads/Game ROMs & Emulation/gba/Pokemon-SoulGold-v1.1.4.gba")


# Its Johto region map is Emerald's region_map.c with smol art: 8bpp tiles
# (mode 5), the 64x64 one-byte tilemap (mode 8) and 3 palettes loaded at BG
# palette 7 (raw, 0x60), checked by rendering them (sRegionMapBg_*).
def soulgold_symbols(rom):
    return {
        "BagStarsGfx": (0x6AED68, 0), "BagStarsMap": (0x6AE9F4, 0), "BagStarsPal": (0x6AF0D8, 0x20),
        "RegionGfx": (0xF39DA0, 0), "RegionMap": (0xF39AC4, 0), "RegionPal": (0xF37148, 0x60),
    }


# Emerald Seaglass v3.0 (sha1 b9f4d332...) redrew the region map's tiles but
# kept Emerald's tilemap and palette (EM_REGION_MAP / _PAL): LoadRegionMapGfx's
# literal pool (ROM 0x1DFFC4) holds the tiles at 0x089534A0, LZ77.
SEAGLASS_ROM = os.path.expanduser("~/Downloads/Game ROMs & Emulation/gba/Pokemon Emerald Seaglass (v3.0).gba")


def seaglass_symbols(rom):
    return {"RegionGfx": (0x9534A0, 0)}


# Pokemon Glazed 9.2.0 (sha1 e10105d8...), an in-place edit of retail Emerald: its own
# region map (Tunod and Johto side by side) written over retail's own blobs, so
# LoadRegionMapGfx's literal pool still names retail's addresses - the 8bpp tiles
# (LZ77), the 64x32 one-byte tilemap (LZ77; retail's is 64x64) and the palette
# (two palettes' worth: its tiles use colours 1-24 at BG palette 7).
GLAZED_ROM = os.path.expanduser("~/Downloads/Game ROMs & Emulation/gba/Glazed (9.2.0).gba")


# Pokemon Emerald Imperium v1.3.1 (sha1 1d20091c...), pokeemerald-expansion 1.10: Emerald's
# region map with markers for its five new places - 7 tiles added to the 8bpp tiles (LZ77) and
# 4 cells changed in the 64x64 tilemap (LZ77); the palette is Emerald's own (EM_REGION_PAL).
IMPERIUM_ROM = os.path.expanduser("~/Downloads/Game ROMs & Emulation/gba/Emerald Imperium (v1.3.1).gba")


def imperium_symbols(rom):
    # Its trainer card is Emerald's in its own FONT_NORMAL (glyphs 0x38 / 0x39 redrawn) and widths.
    return {"RegionGfx": (0xDD84CC, 0), "RegionMap": (0xDD92D0, 0), "FontNormal": (0x664350, 32768),
            "FontNormalWidths": (0x66C350, 512)}


# Pokemon Quetzal English Alpha 9 v0 (sha1 d0658315...): FireRed's Kanto / Sevii maps (pixel for
# pixel, 3 tiles left and 2 up of FireRed's screen) redrawn for Emerald's region_map.c - 8bpp
# tiles and a 64x64 affine tilemap per map (LZ77), FireRed's palette loaded at BG palette 7. Its
# map code's literal pool (ROM 0x1221B54) lists the four tile blobs, then the four tilemaps.
QUETZAL_ROM = os.path.expanduser("~/Downloads/Game ROMs & Emulation/gba/PokemonQuetzalEnglishAlpha9v0.gba")


def quetzal_symbols(rom):
    return {"RegionPal": (0x121EA1C, 0x40),
            "KantoGfx": (0x121EA5C, 0), "Sevii123Gfx": (0x121F84C, 0), "Sevii45Gfx": (0x1220428, 0), "Sevii67Gfx": (0x1220F38, 0),
            "KantoMap": (0x121F51C, 0), "Sevii123Map": (0x12201EC, 0), "Sevii45Map": (0x1220D14, 0), "Sevii67Map": (0x1221920, 0)}


# Pokemon Heart and Soul v2.0.6 (sha1 79ee6df0...): Emerald's region_map.c with smol art and seven
# maps (gRegionMapInfos 0x08D4D330); the Johto + Kanto one ([6], REGION_MAP_JK): mode-5 tiles and a
# mode-8 tilemap (equal to VRAM with the Pokegear map up, byte for byte), Emerald's palette.
HNS_ROM = os.path.expanduser("~/Downloads/Game ROMs & Emulation/gba/Pokémon Heart and Soul (v2.0.6).gba")


def hns_symbols(rom):
    return {"RegionGfx": (0xD4D630, 0), "RegionMap": (0xD4D3F4, 0)}


# Pokemon R.O.W.E. v2.1.9.1 Experimental (sha1 81bd0f4b...): Emerald's region_map.c with three maps
# picked by gMapHeader.region - Hoenn, Kanto, Sevii: 8bpp tiles and a 64x64 affine tilemap each
# (LZ77), one palette (sRegionMapBg_Pal, at BG palette 7). Its own symbol table names them.
ROWE_ROM = os.path.expanduser("~/Downloads/Game ROMs & Emulation/gba/Pokémon R.O.W.E. (v2.1.9.1 Experimental).gba")


def rowe_symbols(rom):
    return {"RegionPal": (0xFCE754, 0x40), "RegionGfx": (0xFCE794, 0), "RegionMap": (0xFCF604, 0),
            "KantoGfx": (0xFCFB34, 0), "KantoMap": (0xFD069C, 0), "SeviiGfx": (0xFD0B9C, 0), "SeviiMap": (0xFD17C4, 0)}


def glazed_symbols(rom):
    # Its trainer card: Tunod badges (tiles repointed, the palette edited in place), its player
    # pics (front-pic table entries 71 / 72 repointed, palettes edited in place) and FONT_NORMAL
    # edited in place - each found through retail's tables and literal pools, rendered and checked.
    return {"RegionGfx": (0x59F77C, 0), "RegionMap": (0x5A04E0, 0), "RegionPal": (0x59F73C, 0x40),
            "BadgesPal": (0x56F4EC, 32), "BadgesGfx": (0x452334, 0), "PicMale": (0x1202798, 0),
            "PicMalePal": (0xD61A30, 0), "PicFemale": (0x12045DC, 0), "PicFemalePal": (0xD61D58, 0),
            "FontNormal": (0x64C2E4, 32768)}


# The European Emeralds (Spanish, German, French, Italian) redrew three party-menu
# pieces: the background tiles (the slot's HP label: PS / KP / PV; the CANCEL
# button), the status icons (localized abbreviations) and FONT_SMALL (a few
# glyphs). Everything else the party menu uses is byte for byte English's. Found
# by scanning near English's offset for a blob of the same size (the palette,
# tilemaps and Poke Ball beside them are identical), FONT_SMALL through
# English's literal pool. RomArt writes them to partyem_<lang>/.
EMERALD_MULTILANG = os.path.expanduser("~/Downloads/Game ROMs & Emulation/gba/emerald-multilang")
EMERALD_EU_ROMS = {
    "ES": os.path.join(EMERALD_MULTILANG, "Pokemon - Edicion Esmeralda (Spain).gba"),
    "DE": os.path.join(EMERALD_MULTILANG, "Pokemon - Smaragd-Edition (Germany).gba"),
    "FR": os.path.join(EMERALD_MULTILANG, "Pokemon - Version Emeraude (France).gba"),
    "IT": os.path.join(EMERALD_MULTILANG, "Pokemon - Versione Smeraldo (Italy).gba"),
}
EMERALD_EU_OFFSETS = {  # gPartyMenuBg_Gfx, gStatusGfx_Icons, gFontSmallLatinGlyphs
    "ES": (0xD96728, 0xD96ECC, 0x636218),
    "DE": (0xD967E8, 0xD96F8C, 0x644D88),
    "FR": (0xD967E0, 0xD96F84, 0x637C18),
    "IT": (0xD96764, 0xD96F08, 0x630338),
}


def emerald_eu_symbols(lang):
    gfx, status, font = EMERALD_EU_OFFSETS[lang]
    return lambda rom: {"PartyBgGfx": (gfx, 0), "StatusGfx": (status, 0), "FontSmall": (font, 0x8000)}


# (Kotlin name, symbol, LZ77? - True / False / "smol") per game.
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
    "LZ": (LAZARUS_ROM, None, [
        ("LZ_REGION_GFX", "RegionGfx", True),
        ("LZ_REGION_PAL", "RegionPal", False),
        ("LZ_REGION_MAP", "RegionMap", True),
    ]),
    "SGL": (SEAGLASS_ROM, None, [
        ("SGL_REGION_GFX", "RegionGfx", True),
    ]),
    "IMP": (IMPERIUM_ROM, None, [
        ("IMP_REGION_GFX", "RegionGfx", True),
        ("IMP_REGION_MAP", "RegionMap", True),
        ("IMP_FONT_NORMAL", "FontNormal", False),
        ("IMP_FONT_NORMAL_WIDTHS", "FontNormalWidths", False),
    ]),
    "RW": (ROWE_ROM, None, [
        ("RW_REGION_PAL", "RegionPal", False),
        ("RW_REGION_GFX", "RegionGfx", True),
        ("RW_REGION_MAP", "RegionMap", True),
        ("RW_KANTO_GFX", "KantoGfx", True),
        ("RW_KANTO_MAP", "KantoMap", True),
        ("RW_SEVII_GFX", "SeviiGfx", True),
        ("RW_SEVII_MAP", "SeviiMap", True),
    ]),
    "HNS": (HNS_ROM, None, [
        ("HNS_REGION_GFX", "RegionGfx", "smol"),
        ("HNS_REGION_MAP", "RegionMap", "smol"),
    ]),
    "QTZ": (QUETZAL_ROM, None, [
        ("QTZ_REGION_PAL", "RegionPal", False),
        ("QTZ_KANTO_GFX", "KantoGfx", True),
        ("QTZ_KANTO_MAP", "KantoMap", True),
        ("QTZ_SEVII123_GFX", "Sevii123Gfx", True),
        ("QTZ_SEVII123_MAP", "Sevii123Map", True),
        ("QTZ_SEVII45_GFX", "Sevii45Gfx", True),
        ("QTZ_SEVII45_MAP", "Sevii45Map", True),
        ("QTZ_SEVII67_GFX", "Sevii67Gfx", True),
        ("QTZ_SEVII67_MAP", "Sevii67Map", True),
    ]),
    "GZ": (GLAZED_ROM, None, [
        ("GZ_REGION_GFX", "RegionGfx", True),
        ("GZ_REGION_PAL", "RegionPal", False),
        ("GZ_REGION_MAP", "RegionMap", True),
        ("GZ_CARD_BADGES_PAL", "BadgesPal", False),
        ("GZ_CARD_BADGES_GFX", "BadgesGfx", True),
        ("GZ_PIC_MALE", "PicMale", True),
        ("GZ_PIC_MALE_PAL", "PicMalePal", True),
        ("GZ_PIC_FEMALE", "PicFemale", True),
        ("GZ_PIC_FEMALE_PAL", "PicFemalePal", True),
        ("GZ_FONT_NORMAL", "FontNormal", False),
    ]),
    "SG": (SOULGOLD_ROM, None, [
        ("SG_BAG_STARS_GFX", "BagStarsGfx", "smol"),
        ("SG_BAG_STARS_MAP", "BagStarsMap", "smol"),
        ("SG_BAG_STARS_PAL", "BagStarsPal", False),
        ("SG_REGION_GFX", "RegionGfx", "smol"),
        ("SG_REGION_MAP", "RegionMap", "smol"),
        ("SG_REGION_PAL", "RegionPal", False),
    ]),
}

for _lang, _rom in EMERALD_EU_ROMS.items():
    GAMES["EM" + _lang] = (_rom, None, [
        (f"EM_{_lang}_PARTY_BG_GFX", "PartyBgGfx", True),
        (f"EM_{_lang}_STATUS_GFX", "StatusGfx", True),
        (f"EM_{_lang}_FONT_SMALL", "FontSmall", False),
    ])

# The European FireReds redrew the same three party-menu pieces (the HP label PS / KP / PV, the
# status abbreviations, a few glyphs): scripts/port_retail.py finds them (build/ports/ports.json,
# "art") and RomArt writes them to partyfr_<lang>/. LeafGreen's are byte for byte FireRed's.
PORTS_JSON = os.path.join(HERE, "..", "build", "ports", "ports.json")
FR_LANGS = {"S": "ES", "D": "DE", "F": "FR", "I": "IT"}


def fireRed_ports():
    if not os.path.exists(PORTS_JSON):
        return {}
    import json
    out = {}
    for p in json.load(open(PORTS_JSON, encoding="utf-8")):
        if p["code"][:3] == "BPR" and p["code"][3] in FR_LANGS and p.get("art"):
            out[FR_LANGS[p["code"][3]]] = p
    return out


for _lang, _p in fireRed_ports().items():
    GAMES["FR" + _lang] = (_p["path"], None, [
        (f"FR_{_lang}_PARTY_BG_GFX", "PartyBgGfx", True),
        (f"FR_{_lang}_STATUS_GFX", "StatusGfx", True),
        (f"FR_{_lang}_FONT_SMALL", "FontSmall", False),
    ])


def fireRed_symbols(p):
    a = p["art"]
    return lambda rom: {"PartyBgGfx": (a["party_bg"][0] - 0x08000000, 0), "StatusGfx": (a["status"][0] - 0x08000000, 0),
                        "FontSmall": (a["font"][0] - 0x08000000, 0x4000)}


# The binary hacks' made-up symbols (no ELF).
ROM_SYMBOLS = {"UB": unbound_symbols, "LZ": lazarus_symbols, "SGL": seaglass_symbols, "SG": soulgold_symbols,
               "GZ": glazed_symbols, "IMP": imperium_symbols, "QTZ": quetzal_symbols, "HNS": hns_symbols, "RW": rowe_symbols,
               **{"EM" + lang: emerald_eu_symbols(lang) for lang in EMERALD_EU_ROMS},
               **{"FR" + lang: fireRed_symbols(p) for lang, p in fireRed_ports().items()}}


def symbols(elf):
    out = {}
    for line in subprocess.run(["nm", "-S", elf], capture_output=True, text=True, check=True).stdout.splitlines():
        p = line.split()
        if len(p) == 4:
            out[p[3]] = (int(p[0], 16) - 0x08000000, int(p[1], 16))
    return out


def existing_rows():
    """The rows already in RomArtSigsGen.kt, by blob name."""
    if not os.path.exists(KOTLIN):
        return {}
    return {line.strip().split("(")[0]: line for line in open(KOTLIN) if line.startswith("    ") and "(" in line
            and line.strip().split("(")[0].isupper()}


def main():
    rows = []
    kept = existing_rows()
    if not fireRed_ports():  # no ports.json here: the European FireReds' rows stay as they were
        GAMES.update({"FR" + l: ("/nonexistent", None, [(n, "", False) for n in kept if n.startswith(f"FR_{l}_")])
                      for l in FR_LANGS.values() if any(n.startswith(f"FR_{l}_") for n in kept)})
    for game, (rom_path, elf, blobs) in GAMES.items():
        path = os.path.join(ROOT, rom_path)
        if not elf and not os.path.exists(path):
            # A hack's ROM that isn't on this machine: keep its fingerprints as they were.
            missing = [name for name, _, _ in blobs if name not in kept]
            if missing:
                raise SystemExit(f"{path} missing and no earlier rows for {missing}")
            rows += [kept[name].rstrip("\n") for name, _, _ in blobs]
            print(f"{game}: ROM not here, kept its {len(blobs)} rows")
            continue
        rom = open(path, "rb").read()
        syms = symbols(os.path.join(ROOT, elf)) if elf else ROM_SYMBOLS[game](rom)
        for name, sym, lz in blobs:
            off, size = syms[sym]
            if lz == "smol":
                data, raw_len = smol.decode(rom[off:off + 0x10000])
            else:
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
            rows.append(f'    {name}({"false" if lz == "smol" else str(lz).lower()}, {len(data)}, 0x{zlib.crc32(data):08X}L, {w}, {h}L, 0x{pre(win):04X}'
                        f'{", smol = true" if lz == "smol" else ""}),')
            print(f"{name}: {sym} @{off:#x} raw {raw_len} decoded {len(data)} window +{w}")
    kt = """package com.pokedaisy.app.companion.data

// GENERATED by scripts/gen_rom_art_sigs.py from the pinned pokefirered /
// pokeemerald builds - do not hand-edit; rerun the script instead.

/** A graphics blob [RomArt] looks for in the loaded ROM - see the script for the fields. */
enum class RomBlob(val lz: Boolean, val size: Int, val crc: Long, val headOff: Int, val head: Long, val pre: Int, val smol: Boolean = false) {
""" + "\n".join(rows) + "\n}\n"
    # Long literals: 0x... > Long.MAX_VALUE don't compile as Long, so the CRCs stay
    # under 2^32 (fine) and the heads are emitted signed (above).
    open(KOTLIN, "w").write(kt)
    print("wrote", KOTLIN)


if __name__ == "__main__":
    main()

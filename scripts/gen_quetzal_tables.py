#!/usr/bin/env python3
"""Generates Pokémon Quetzal's display tables from the ROM the user owns
(closed hack of Emerald by TenmaRH, no source; nothing is downloaded).

    scripts/gen_quetzal_tables.py <rom.gba> [out_dir]

Writes {SpeciesNames,SpeciesTypes,GenderRatios,ItemNames,ItemDescriptions,
ItemPockets,MoveData,TypeChart,MapSecData}Quetzal.kt (default: the app's data package).

Quetzal English Alpha 9 v0 (BPEE, 32 MB, header title "PKM QUETZAL") keeps
vanilla pokeemerald's table *shapes* (names in their own arrays, not inline in
gSpeciesInfo / gItemsInfo like pokeemerald-expansion), with grown structs and
expansion-sized contents. Every address below is also published in the ROM's
own GF header at 0x08000100 (pokemon quetzal version: monSpeciesNames,
moveNames, speciesInfo, items, moves - the pointers are current, its save
offsets are not):
  - gSpeciesNames 0x08504670: 13-byte names (12 chars + EOS), title case,
    1529 entries (0..1528); National Dex ids through 898 (Calyrex), then forms
    (899 Venusaur = Mega Venusaur, ...), Gen 9 from 1244 (Lechonk). A second,
    Spanish-named copy at 0x08509415 differs in 21 names (paradoxes, Type: Null).
  - gSpeciesInfo 0x0853E560: 0x24-byte entries - base stats +0..+5, types +6/+7,
    catchRate +8, expYield u16 +0xA, evYield +0xC, items +0xE/+0x10,
    genderRatio +0x12, eggCycles +0x13, friendship +0x14, growthRate +0x15,
    eggGroups +0x16/+0x17, abilities u16 +0x18/+0x1A/+0x1C (hidden).
    Bulbasaur 45/49/49/45/65/65 Grass/Poison 31, Pikachu Electric 127.
  - Type ids are vanilla's (0-17, 9 = "???") plus Fairy 18 and Stellar 19
    (gTypeNames 0x0851F1F4, 10-byte names). gTypeEffectiveness 0x0852DA8C is
    [20][20] u32 uq4.12 (0x1000 = 1x), Gen 6+ rules.
  - gMoveNames 0x0850E1BA: 17-byte names ("Pound" 1, "Tackle" 33).
    gBattleMoves 0x08530690: 0x18-byte entries {u16 effect; u8 power; u8 type;
    u8 accuracy; u8 pp; ...} (Tackle 40 Normal 35 PP, Ember 40 Fire).
  - gItemNames 0x091E66EC: 20-byte names, 890 ids (0..889, expansion's order:
    1 Poké Ball, 28 Potion). gItems 0x091E0594: 0x1C-byte {u16 itemId; u32
    price +4; u32 holdEffect/param +8; description* +0xC; u8 importance +0x10;
    u8 +0x11; u8 pocket +0x12; u8 type +0x13; fieldUseFunc +0x14; ...}.
  - gRegionMapEntries 0x09229558: 8-byte {names*, x, y, w, h} by mapsec id,
    0xD5 of them (u8 ids, MAPSEC_NONE 0xD5); names* points at 4 string
    pointers {English, Spanish, Spanish, Portuguese}. Emerald's mapsec ids
    (0 Littleroot .. 0x57, Kanto 0x58.. e.g. 0x65 ROUTE 1, the save's map,
    shown by the game's Town Map), caps -> title case. The game's region map is
    FireRed's (Kanto / ISLES): the app places these on it with FireRed's rects
    (ActiveTables' mapSecDataQuetzalOnMap), so here they're names only (region -1).
"""
import hashlib
import os
import struct
import sys

OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "kotlin",
                       "com", "pokedaisy", "app", "companion", "data")

SHA1 = "d0658315da1e8827f66f15c3d3a000fe747e163e"
LABEL = "Pokemon Quetzal English Alpha 9 v0"

SPECIES_NAMES = 0x08504670   # 13-byte names
SPECIES_INFO = 0x0853E560    # 0x24-byte entries
SPECIES_COUNT = 1529
MOVE_NAMES = 0x0850E1BA      # 17-byte names
BATTLE_MOVES = 0x08530690    # 0x18-byte entries: power +2, type +3
ITEM_NAMES = 0x091E66EC      # 20-byte names
ITEMS = 0x091E0594           # 0x1C-byte entries: description ptr +0xC
ITEM_COUNT = 890
TYPE_CHART = 0x0852DA8C      # [20][20] u32 uq4.12
TYPE_COUNT = 20
REGION_MAP_ENTRIES = 0x09229558  # {names**, x, y, w, h}
JOHTO_MAP_ENTRIES = 0x0922A7E8   # Johto's maps' own: {x, y, w, h, names**}
JOHTO_MAPSEC_BASE = 0x100        # where the app keeps them (NATIVE_QUETZAL.altMapSecGroups)
MAPSEC_COUNT = 0xD5

TYPE_NAMES = {0: "Normal", 1: "Fighting", 2: "Flying", 3: "Poison", 4: "Ground", 5: "Rock", 6: "Bug", 7: "Ghost",
              8: "Steel", 10: "Fire", 11: "Water", 12: "Grass", 13: "Electric", 14: "Psychic", 15: "Ice",
              16: "Dragon", 17: "Dark", 18: "Fairy"}  # 9 "???" and 19 Stellar: no species / move uses them

CHARS = {0x00: " ", 0x1B: "é", 0xAB: "!", 0xAC: "?", 0xAD: ".", 0xAE: "-", 0xB0: "…", 0xB1: "“", 0xB2: "”",
         0xB3: "‘", 0xB4: "'", 0xB5: "♂", 0xB6: "♀", 0xB8: ",", 0xBA: "/", 0xF0: ":", 0x2D: "&", 0x5C: "(",
         0x5D: ")", 0x5B: "%", 0x35: "=", 0x36: ";", 0x53: "PK", 0x54: "MN"}
# Line / paragraph breaks: descriptions are wrapped for the game's box, the app rewraps.
CHARS.update({0xFA: " ", 0xFB: " ", 0xFE: " "})
CHARS.update({0xA1 + i: str(i) for i in range(10)})
CHARS.update({0xBB + i: chr(65 + i) for i in range(26)})
CHARS.update({0xD5 + i: chr(97 + i) for i in range(26)})

# Spot checks against what the game shows (the user's save: Charmander with
# Tackle / Growl / Ember, Tackle at 56 PP (35 + 3 PP Ups); the bag's items; the
# Town Map's ROUTE 1).
CHECKS = dict(species={1: "Bulbasaur", 4: "Charmander", 16: "Pidgey", 25: "Pikachu", 403: "Shinx", 1244: "Lechonk"},
              types={1: (12, 3), 4: (10, 10), 25: (13, 13), 35: (18, 18), 403: (13, 13)},
              gender={1: 31, 25: 127, 35: 191},
              moves={1: ("Pound", 0, 40), 33: ("Tackle", 0, 40), 45: ("Growl", 0, 0), 52: ("Ember", 10, 40),
                     57: ("Surf", 11, 90), 89: ("Earthquake", 4, 100)},
              items={1: "Poké Ball", 5: "Premier Ball", 28: "Potion", 44: "Paralyze Heal", 45: "Burn Heal",
                     114: "Repel", 120: "Escape Rope", 129: "Poké Doll", 461: "Exp. Share", 703: "Mega Ring",
                     704: "Z-Power Ring", 713: "Town Map"},
              mapsecs={0x00: "Littleroot Town", 0x10: "Route 101", 0x58: "Pallet Town", 0x65: "Route 1"})


def text(rom, a, maxlen):
    out = []
    for b in rom[a - 0x08000000:a - 0x08000000 + maxlen]:
        if b == 0xFF:
            break
        if b not in CHARS:
            return None
        out.append(CHARS[b])
    s = " ".join("".join(out).split())
    return s if s and set(s) != {"?"} and s != "-" else None


def u8(rom, a):
    return rom[a - 0x08000000]


def u32(rom, a):
    return struct.unpack_from("<I", rom, a - 0x08000000)[0]


def is_ptr(rom, v):
    return 0x08000000 <= v < 0x08000000 + len(rom)


def kstr(s):
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"') + '"'


def title(s):
    words = []
    for w in s.split(" "):
        words.append(w if w.count(".") >= 2 else w[:1] + w[1:].lower())
    return " ".join(words)


def header(what):
    return (f"package com.pokedaisy.app.companion.data\n\n"
            f"// GENERATED by scripts/gen_quetzal_tables.py from the {LABEL} ROM\n"
            f"// (sha1 {SHA1[:8]}...) - do not hand-edit. {what}\n")


def write(name, body):
    path = os.path.join(OUT_DIR, name)
    with open(path, "w") as f:
        f.write(body)
    print("wrote", os.path.relpath(path))


def main():
    global OUT_DIR
    if len(sys.argv) not in (2, 3):
        sys.exit(f"usage: {sys.argv[0]} <rom.gba> [out_dir]")
    if len(sys.argv) == 3:
        OUT_DIR = sys.argv[2]
    rom = open(sys.argv[1], "rb").read()
    if hashlib.sha1(rom).hexdigest() != SHA1:
        sys.exit(f"not {LABEL} (sha1 {SHA1})")

    names, types, gender, skipped = {}, {}, {}, []
    for i in range(1, SPECIES_COUNT):
        n = text(rom, SPECIES_NAMES + 13 * i, 13)
        if not n:
            if rom[SPECIES_NAMES + 13 * i - 0x08000000] != 0xAC:
                skipped.append(i)
            continue
        names[i] = n
        b = SPECIES_INFO + 0x24 * i
        types[i] = (u8(rom, b + 6), u8(rom, b + 7))
        gender[i] = u8(rom, b + 0x12)
    assert all(names.get(i) == n for i, n in CHECKS["species"].items()), "species names are wrong"
    assert all(types.get(i) == t for i, t in CHECKS["types"].items()), "species types are wrong"
    assert all(gender.get(i) == g for i, g in CHECKS["gender"].items()), "species gender ratios are wrong"
    write("SpeciesNamesQuetzal.kt", header("gSpeciesNames, keyed by the game's own species ids\n"
          "// (National Dex order through 898, then forms, Gen 9 from 1244).\n") +
          "val speciesNamesQuetzal: Map<Int, String> = mapOf(\n" +
          "".join(f"    {i} to {kstr(n)},\n" for i, n in sorted(names.items())) + ")\n")
    write("SpeciesTypesQuetzal.kt", header("gSpeciesInfo types, ids per typeNamesQuetzal.\n") +
          "val speciesTypeDataQuetzal: Map<Int, SpeciesTypes> = mapOf(\n" +
          "".join(f"    {i} to SpeciesTypes({a}, {b}),\n" for i, (a, b) in sorted(types.items())) + ")\n")
    hexs = "".join("%02x" % gender.get(i, 255) for i in range(max(gender) + 1))
    write("GenderRatiosQuetzal.kt", header("gSpeciesInfo genderRatio by species id (0 always\n"
          "// male, 254 always female, 255 genderless, else female when (personality & 0xFF) < ratio).\n") +
          f"val genderRatiosQuetzal: IntArray by lazy {{ unhexRatios(\"{hexs}\") }}\n")

    moves = []
    for i in range(1, 2000):
        n = text(rom, MOVE_NAMES + 17 * i, 17)
        if not n:
            if all(text(rom, MOVE_NAMES + 17 * (i + k), 17) is None for k in range(4)):
                break
            continue
        b = BATTLE_MOVES + 0x18 * i
        moves.append((i, n, u8(rom, b + 3), u8(rom, b + 2)))
    info = {i: (n, t, p) for i, n, t, p in moves}
    assert all(info.get(i) == v for i, v in CHECKS["moves"].items()), "moves are wrong"
    write("MoveDataQuetzal.kt", header("gMoveNames + gBattleMoves type / power (type ids per\n"
          "// typeNamesQuetzal).\n") +
          "val moveDataQuetzal: Map<Int, MoveInfo> = mapOf(\n" +
          "".join(f"    {i} to MoveInfo({kstr(n)}, {t}, {p}),\n" for i, n, t, p in moves) + ")\n")

    items, descs = {}, {}
    for i in range(1, ITEM_COUNT):
        n = text(rom, ITEM_NAMES + 20 * i, 20)
        if not n:
            continue
        a = ITEMS + 0x1C * i
        # gItems[i].itemId == i for every named item but Enigma Berry (581 reads 574).
        items[i] = n
        p = u32(rom, a + 0xC)
        d = text(rom, p, 200) if is_ptr(rom, p) else None
        if d:
            descs[i] = d
    assert all(items.get(i) == n for i, n in CHECKS["items"].items()), "item names are wrong"
    assert "20 points" in descs.get(28, ""), "item description offset is wrong"
    write("ItemNamesQuetzal.kt", header("gItemNames, keyed by item id.\n") +
          "val itemNamesQuetzal: Map<Int, String> = mapOf(\n" +
          "".join(f"    {i} to {kstr(n)},\n" for i, n in sorted(items.items())) + ")\n")
    # The bag screen's 11 tabs (gItems +0x12): ITEMS, MEDICINE, POKé BALLS, BERRIES, TRAINING,
    # EVO/FORMS, BATTLE ITEM, MEGA STONES, Z CRYSTALS, TERA SHARDS, KEY ITEMS; TMs are 100
    # (not in the bag). The app's pockets: balls, berries and key items; the rest are items.
    app_pocket = {3: "POCKET_POKE_BALLS", 4: "POCKET_BERRIES", 11: "POCKET_KEY_ITEMS", 100: "POCKET_TM_HM"}
    pockets = {i: app_pocket[u8(rom, ITEMS + 0x1C * i + 0x12)] for i in items
               if u8(rom, ITEMS + 0x1C * i + 0x12) in app_pocket}
    assert pockets.get(1) == "POCKET_POKE_BALLS" and 28 not in pockets and pockets.get(713) == "POCKET_KEY_ITEMS"
    write("ItemPocketsQuetzal.kt", header("The app's pocket for each item gItems doesn't file\n"
          "// under ITEMS (its +0x12 pocket: 3 balls, 4 berries, 11 key items, 100 TMs).\n") +
          "val itemPocketsQuetzal: Map<Int, Int> = mapOf(\n" +
          "".join(f"    {i} to {p},\n" for i, p in sorted(pockets.items())) + ")\n")
    write("ItemDescriptionsQuetzal.kt", header("gItems descriptions, keyed by item id.\n") +
          "val itemDescriptionsQuetzal: Map<Int, String> = mapOf(\n" +
          "".join(f"    {i} to {kstr(d)},\n" for i, d in sorted(descs.items())) + ")\n")

    chart = {}
    for atk in TYPE_NAMES:
        for dfn in TYPE_NAMES:
            v = u32(rom, TYPE_CHART + 4 * (atk * TYPE_COUNT + dfn))
            if v != 0x1000:
                chart[atk * 100 + dfn] = v * 100 // 0x1000
    assert chart[0 * 100 + 7] == 0 and chart[10 * 100 + 12] == 200 and chart[13 * 100 + 4] == 0 \
        and chart[16 * 100 + 18] == 0 and chart[18 * 100 + 16] == 200, "chart is wrong"
    write("TypeChartQuetzal.kt", header("Vanilla type ids plus Fairy 18 (19 Stellar unused).\n"
          "// gTypeEffectiveness [20][20] u32 uq4.12; key = atkType*100+defType -> percent, pairs not\n"
          "// listed are 100.\n") +
          "val typeNamesQuetzal: Map<Int, String> = mapOf(\n" +
          "".join(f"    {i} to {kstr(s)},\n" for i, s in sorted(TYPE_NAMES.items())) + ")\n\n" +
          "val typeEffectivenessQuetzal: Map<Int, Int> = mapOf(\n" +
          "".join(f"    {k} to {v},\n" for k, v in sorted(chart.items())) + ")\n")

    mapsecs = {}
    for i in range(MAPSEC_COUNT):
        p = u32(rom, REGION_MAP_ENTRIES + 8 * i)
        if not is_ptr(rom, p):
            continue
        en = u32(rom, p)  # names[0] = English
        s = text(rom, en, 30) if is_ptr(rom, en) else None
        if s:
            mapsecs[i] = title(s)
    assert all(mapsecs.get(i) == n for i, n in CHECKS["mapsecs"].items()), "map sections are wrong"
    # Johto's map groups (34-35) name their sections from a table of their own (Violet City is 2 there).
    for i in range(MAPSEC_COUNT):
        p = u32(rom, JOHTO_MAP_ENTRIES + 8 * i + 4)
        if not is_ptr(rom, p):
            continue
        en = u32(rom, p)
        s = text(rom, en, 30) if is_ptr(rom, en) else None
        if s:
            mapsecs[JOHTO_MAPSEC_BASE + i] = title(s)
    assert mapsecs[JOHTO_MAPSEC_BASE + 2] == "Violet City" and mapsecs[JOHTO_MAPSEC_BASE + 0x58] == "Route 29"
    write("MapSecDataQuetzal.kt", header("gRegionMapEntries' English names by mapsec id, region -1\n"
          "// here: ActiveTables puts them on its Kanto / Sevii maps with FireRed's rects. 0x100+: Johto's own\n"
          "// table's (its map groups 34-35 read their sections as 0x100 + id).\n") +
          "val regionMapImagesQuetzal = arrayOf<String>()\n\n" +
          "val mapSecDataQuetzal: Map<Int, MapSecInfo> = mapOf(\n" +
          "".join(f"    {i} to MapSecInfo({kstr(s)}, -1, 0, 0, 0, 0),\n" for i, s in sorted(mapsecs.items())) + ")\n")
    print(f"{len(names)} species ({len(skipped)} unreadable: {skipped[:20]}), {len(moves)} moves, {len(items)} items, "
          f"{len(descs)} descriptions, {len(chart)} chart pairs, {len(mapsecs)} map sections")


if __name__ == "__main__":
    main()

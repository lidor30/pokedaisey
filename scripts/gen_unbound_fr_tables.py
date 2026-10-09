#!/usr/bin/env python3
"""Generates the names of Pokémon Unbound v2.1.1.1's French translation (a
fan translation of the same release: sha1 0ce2a880..., 32 MB BPRE) from the
ROM the user owns (nothing is downloaded), so the companion shows its names in
French, like the other-language Emeralds.

    scripts/gen_unbound_fr_tables.py <french rom.gba> [<english rom.gba>]

Writes app/src/main/kotlin/.../companion/data/UnboundTextFrGen.kt: species,
move, item, nature and map section names by Unbound's own ids (the translation keeps every
id, table and RAM address of the English release - it rewrote text in place or
repointed it). Item descriptions aren't written: the app reads them from the
player's ROM (gItems, NativeConfig.itemDescs - the same address as English's).
The MAP reads its names from the ROM too (RomRegionMap); the ones here are for
everything else (the status bar before that's read, the GUIDE). Types stay
English (the app colours them by name).

Where each table is: the literal-pool words FireRed 1.0 loads it from (CFRU
repoints them), at the same place in both releases -
  - gSpeciesNames 0x0966A98C (11-byte names, translated in place);
  - move names: English loads 0x08A40A10, French 0x081B2980 (13-byte names,
    ending at 893 - past that, Unbound's Max Moves keep their English names);
  - gItems 0x08876200 (44-byte struct Item, name inline, .itemId at +14);
  - gNatureNamePointers 0x08463E60;
  - sMapNames 0x083F1CAC (pointers, from MAPSEC 0x58; ALL-CAPS names kept from
    FireRed are title-cased as RomRegionMap does).
Given the English ROM too, it checks that only text changed in those tables
(the item ids, the move count) and that the French names cover the same ids.
"""
import hashlib
import os
import struct
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_emerald_lang_tables import CHARS, LANG_CHARS  # noqa: E402

OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "kotlin",
                       "com", "pokedaisy", "app", "companion", "data")
SHA1 = "0ce2a880aa097f1dce4e1db8ee513d0e82d15859"
EN_SHA1 = "b4776b82a4c7915d0fadeaa27e013523f99dfd94"
B = 0x08000000
SPECIES = 0x0966A98C
SPECIES_COUNT = 1294
MOVES_FR, MOVES_EN = 0x081B2980, 0x08A40A10
MOVES_FR_LAST = 893
ITEMS = 0x08876200
ITEMS_LAST_SLOT = 728  # English's table ends here (0xFF fill); the translation's text runs on after it
NATURES = 0x08463E60
MAP_NAMES, FIRST_MAPSEC, SECTIONS = 0x083F1CAC, 0x58, 109
CH = {**CHARS, **LANG_CHARS["fr"]}


def text(d, a, maxlen):
    out = []
    for b in d[a - B:a - B + maxlen]:
        if b == 0xFF:
            break
        if b in (0xFA, 0xFB, 0xFE):
            out.append(" ")
            continue
        if b not in CH:
            return None
        out.append(CH[b])
    s = " ".join("".join(out).split())
    return s if s and set(s) != {"?"} and s != "-" else None


def u16(d, a):
    return struct.unpack_from("<H", d, a - B)[0]


def u32(d, a):
    return struct.unpack_from("<I", d, a - B)[0]


def items(d):
    """gItems names by .itemId. A few records hold a pointer to a longer name instead of the
    name (Wise Glasses, ...): read it there."""
    out = {}
    for slot in range(1, ITEMS_LAST_SLOT + 1):
        a = ITEMS + 44 * slot
        iid = u16(d, a + 14)
        p = u32(d, a)
        n = text(d, p, 20) if B <= p < B + len(d) else text(d, a, 14)
        if n and 0 < iid < 4000 and iid not in out:
            out[iid] = n
    return out


def title_case(s):
    """RomRegionMap.titleCase: ALL-CAPS names in title case, others as they are."""
    if any("a" <= c <= "z" for c in s):
        return s
    out, start = [], True
    for c in s:
        out.append(c.upper() if start else c.lower())
        start = not c.isalpha() and c != "’"
    return "".join(out)


def kstr(s):
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$") + '"'


def kmap(name, m):
    return (f"internal val {name}: Map<Int, String> by lazy {{\n    mapOf(\n" +
            "".join(f"        {i} to {kstr(n)},\n" for i, n in sorted(m.items())) + "    )\n}\n")


def main():
    if len(sys.argv) not in (2, 3):
        sys.exit(f"usage: {sys.argv[0]} <french rom.gba> [<english rom.gba>]")
    d = open(sys.argv[1], "rb").read()
    if hashlib.sha1(d).hexdigest() != SHA1:
        sys.exit(f"{sys.argv[1]} isn't Unbound v2.1.1.1 FR (sha1 {SHA1})")
    species = {i: n for i in range(1, SPECIES_COUNT) if (n := text(d, SPECIES + 11 * i, 11)) and not n.startswith("?")}
    moves = {i: n for i in range(1, MOVES_FR_LAST + 1) if (n := text(d, MOVES_FR + 13 * i, 13))}
    item_names = items(d)
    natures = [text(d, u32(d, NATURES + 4 * i), 14) for i in range(25)]
    mapsecs = {FIRST_MAPSEC + i: title_case(n) for i in range(SECTIONS) if (n := text(d, u32(d, MAP_NAMES + 4 * i), 32))}
    assert species[1] == "Bulbizarre" and species[4] == "Salamèche" and species[246] == "Embrylex"
    assert moves[1] == "Écras'Face" and moves[33] == "Charge" and moves[45] == "Rugissement"
    assert item_names[13] == "Potion" and item_names[86] == "Repousse" and item_names[348] == "Boîte Costume"
    assert natures[0] == "Hardi" and natures[24] == "Bizarre"
    assert mapsecs[0x59] == "Bélenbourg" and mapsecs[0x61] == "Plateau Indigo"
    if len(sys.argv) == 3:
        e = open(sys.argv[2], "rb").read()
        if hashlib.sha1(e).hexdigest() != EN_SHA1:
            sys.exit(f"{sys.argv[2]} isn't Unbound v2.1.1.1 (sha1 {EN_SHA1})")
        en_species = {i for i in range(1, SPECIES_COUNT) if (n := text(e, SPECIES + 11 * i, 11)) and not n.startswith("?")}
        en_items = items(e)
        print("species missing in French:", sorted(en_species - set(species))[:20])
        print("items missing in French:", sorted(set(en_items) - set(item_names))[:20],
              "extra:", sorted(set(item_names) - set(en_items))[:20])
        # Every record keeps its item id but slot 87, which neither release uses (no name in either).
        moved = [s for s in range(1, ITEMS_LAST_SLOT + 1) if u16(e, ITEMS + 44 * s + 14) != u16(d, ITEMS + 44 * s + 14)]
        assert moved == [87], moved
    kt = (f"package com.pokedaisy.app.companion.data\n\n"
          f"// Generated by scripts/gen_unbound_fr_tables.py from Pokémon Unbound v2.1.1.1 FR\n"
          f"// (sha1 {SHA1[:8]}...) - do not hand-edit. The French translation's own names, by Unbound's ids\n"
          f"// (moves past {MOVES_FR_LAST} keep their English names: the translation's table ends there).\n\n" +
          kmap("speciesNamesUnboundFr", species) + "\n" +
          kmap("moveNamesUnboundFr", moves) + "\n" +
          kmap("itemNamesUnboundFr", item_names) + "\n" +
          "internal val natureNamesUnboundFr: List<String> = listOf(\n" +
          "".join(f"    {kstr(n)},\n" for n in natures) + ")\n\n" +
          kmap("mapSecNamesUnboundFr", mapsecs))
    path = os.path.join(OUT_DIR, "UnboundTextFrGen.kt")
    with open(path, "w", encoding="utf-8") as f:
        f.write(kt)
    print(f"wrote {os.path.relpath(path)}: {len(species)} species, {len(moves)} moves, {len(item_names)} items")


if __name__ == "__main__":
    main()

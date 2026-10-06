#!/usr/bin/env python3
"""Generates PokeDaisey's per-game display tables for a pokeemerald-expansion
ROM hack, straight from the ROM the user owns (nothing is downloaded).

    scripts/gen_expansion_tables.py <game-key> <rom.gba>

Writes app/src/main/kotlin/.../companion/data/{SpeciesNames,ItemNames,MoveData,
SpeciesTypes,TypeChart,MapSecData,GenderRatios}<Suffix>.kt.

Every expansion release moves these tables and changes the struct sizes, so
each game gets its own GAMES entry with addresses found by hand (see the
comments there for how). Adding another expansion hack is a new entry, not new
code, as long as its tables have the same shape:
  - gSpeciesInfo: fixed-stride entries with the name inline; base stats,
    types and genderRatio at fixed offsets before the name.
  - gItemsInfo / gMovesInfo: fixed-stride entries starting with a name
    pointer (or, for items in some builds, the name inline). Move type/power
    are a u16 bitfield (type:5, category:2, power:9).
  - gTypeEffectiveness: [n][n] u32 uq4.12 multipliers (0x1000 = 1x).
  - gRegionMapEntries: 8-byte entries indexed by mapsec id, name pointer at
    +0 (newer) or +4 (older {x, y, w, h, name}).
The sha1 check stops a table being generated from the wrong release.
"""
import hashlib
import os
import struct
import sys

OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "kotlin",
                       "com", "pokedaisey", "PokeDaiseyApp", "companion", "data")

# Newer expansion type ids (include/constants/pokemon.h): 0 None, 10 Mystery,
# 20 Stellar are left out on purpose - no species/move uses them, and
# typeMatchups() iterates the name map's keys.
EXPANSION_TYPE_NAMES = {
    1: "Normal", 2: "Fighting", 3: "Flying", 4: "Poison", 5: "Ground", 6: "Rock", 7: "Bug", 8: "Ghost",
    9: "Steel", 11: "Fire", 12: "Water", 13: "Grass", 14: "Electric", 15: "Psychic", 16: "Ice",
    17: "Dragon", 18: "Dark", 19: "Fairy",
}

GAMES = {
    # Pokemon Heart and Soul v2.0.6. Found from the ROM + headless captures:
    #  - species: "BULBASAUR" at 0x087E667C, "IVYSAUR" 0x10C later and
    #    "CYNDAQUIL" exactly at species 155 -> 0x10C-byte entries. Base stats
    #    45/49/49/45/65/65 at name-0x2C, types (13 Grass, 4 Poison) at
    #    name-0x26, genderRatio 31 at name-0x1A. Gen 9 sits past the forms;
    #    disabled species are zeroed entries.
    #  - items: the only pointer to the "POKé BALL" string is item 1's name
    #    (0x0878F000); the next name pointer is 0x2C later. Item 28 = POTION
    #    matches the user's bag (2 Potions).
    #  - moves: the only pointer to a "TACKLE" string is move 33's name; BODY
    #    SLAM (34) is 0x44 later. +0xA u16: type = low 5 bits (Ember 11 Fire,
    #    Surf 12 Water, Earthquake 5 Ground), power = bits 7-15 (Tackle 40,
    #    Surf 90, Earthquake 100, Leer 0).
    #  - chart: the unique [21][21] u32 run whose Normal row is 1x except
    #    Rock/Steel 0.5x and Ghost 0x; the Fire row checks out too.
    #  - mapsecs: 8-byte {name ptr, x, y, w, h} entries; the live gMapHeader in New
    #    Bark Town reads mapsec 0x3F, and 0x08D553D8 + 0x3F*8 names it. (A
    #    second table at 0x08D55DF0 has the same names with other coords.)
    "hns": dict(
        suffix="Hns",
        label="Pokemon Heart and Soul v2.0.6",
        sha1="79ee6df0869c1773c8c6a5f764afc1f8d833d8bb",
        species_name1=0x087E667C, species_stride=0x10C, species_max=1600,
        species_types_off=-0x26, species_gender_off=-0x1A,
        items_base=0x0878EFD4, items_stride=0x2C, items_desc_off=-8,
        moves_base=0x087B652C - 33 * 0x44, moves_stride=0x44, move_bits_off=0x0A,
        chart=0x08475D1C, chart_n=21,
        mapsec_base=0x08D553D8,
        checks=dict(species={1: "BULBASAUR", 155: "CYNDAQUIL"}, types={1: (13, 4)}, gender={1: 31},
                    items={1: "POKé BALL", 28: "POTION"}, moves={33: (1, 40), 57: (12, 90), 89: (5, 100)}),
    ),
    # Pokemon Lazarus v2.0 (splash: "PRET x RHH"). Same gSpeciesInfo field
    # offsets as HnS, 0xD4-byte entries: "Fennekin" -> "Braixen" spacing, and
    # "Pikachu" lands exactly at 25 (no Bulbasaur - the dex is regional, with
    # unused species zeroed). Items have INLINE names, 0x50 apart ("Great
    # Ball" 0x50 after "Poké Ball", "Potion" at 28 = the user's bag item).
    # Moves: name pointers, 0x34 stride ("Tackle" 33 -> "Body Slam" 34), same
    # +0xA type/power bitfield. Chart: [21][21] u32 like HnS. Mapsecs: the
    # older {x, y, w, h, name ptr} layout, name at +4; the live map header
    # reads 0x5A = "Acrisia City" (the save's town). Hoenn ids 0-85 are empty.
    "lazarus": dict(
        suffix="Lazarus",
        label="Pokemon Lazarus v2.0",
        sha1="7dcdc7e280bc4631487e13dd37e6e0cea04adea6",
        species_name1=0x08C7A438, species_stride=0xD4, species_max=1600,
        species_types_off=-0x26, species_gender_off=-0x1A,
        items_base=0x088685AC - 0x50, items_stride=0x50, items_name_inline=True, items_desc_off=-8,
        moves_base=0x088D017C - 33 * 0x34, moves_stride=0x34, move_bits_off=0x0A,
        chart=0x08571B78, chart_n=21,
        mapsec_base=0x08CE4100 - 4 - 0x5A * 8, mapsec_name_off=4,
        checks=dict(species={25: "Pikachu", 653: "Fennekin"}, types={25: (14, 14), 653: (11, 11)},
                    gender={25: 127}, items={1: "Poké Ball", 28: "Potion"},
                    moves={33: (1, 40), 57: (12, 90), 89: (5, 100)}),
    ),
}

CHARS = {0x00: " ", 0x1B: "é", 0xAB: "!", 0xAC: "?", 0xAD: ".", 0xAE: "-", 0xB0: "…", 0xB1: "“", 0xB2: "”",
         0xB3: "‘", 0xB4: "'", 0xB5: "♂", 0xB6: "♀", 0xB8: ",", 0xBA: "/", 0xF0: ":", 0x2D: "&", 0x5C: "(",
         0x5D: ")", 0x35: "=", 0x36: ";", 0x53: "PK", 0x54: "MN"}
# Line / paragraph breaks: descriptions are wrapped for the game's box, the app rewraps.
CHARS.update({0xFA: " ", 0xFB: " ", 0xFE: " "})
CHARS.update({0xA1 + i: str(i) for i in range(10)})
CHARS.update({0xBB + i: chr(65 + i) for i in range(26)})
CHARS.update({0xD5 + i: chr(97 + i) for i in range(26)})


class Rom:
    def __init__(self, data):
        self.d = data

    def off(self, a):
        return a - 0x08000000

    def u8(self, a):
        return self.d[self.off(a)]

    def u16(self, a):
        return struct.unpack_from("<H", self.d, self.off(a))[0]

    def u32(self, a):
        return struct.unpack_from("<I", self.d, self.off(a))[0]

    def is_ptr(self, v):
        return 0x08000000 <= v < 0x08000000 + len(self.d)

    def text(self, a, maxlen=40):
        """Gen 3 string at a, or None if it isn't clean text."""
        out = []
        for b in self.d[self.off(a):self.off(a) + maxlen]:
            if b == 0xFF:
                s = " ".join("".join(out).split())
                return s if s else None
            if b not in CHARS:
                return None
            out.append(CHARS[b])
        return None


def kstr(s):
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"') + '"'


def title(s):
    words = []
    for w in s.split(" "):
        # "S.S." / "MT." / "DIGLETT'S" -> "S.S." / "Mt." / "Diglett's"
        words.append(w if w.count(".") >= 2 else w[:1] + w[1:].lower())
    return " ".join(words)


def header(g, what):
    return (f"package com.pokedaisey.app.companion.data\n\n"
            f"// GENERATED by scripts/gen_expansion_tables.py from the {g['label']} ROM\n"
            f"// (sha1 {g['sha1'][:8]}...) - do not hand-edit. {what}\n")


def write(name, body):
    path = os.path.join(OUT_DIR, name)
    with open(path, "w") as f:
        f.write(body)
    print("wrote", os.path.relpath(path))


def species(rom, g):
    names, types, gender = {}, {}, {}
    for i in range(1, g["species_max"]):
        a = g["species_name1"] + (i - 1) * g["species_stride"]
        if rom.off(a) + g["species_stride"] > len(rom.d):
            break
        n = rom.text(a, 13)
        if not n or n.startswith("?"):
            continue
        names[i] = n
        types[i] = (rom.u8(a + g["species_types_off"]), rom.u8(a + g["species_types_off"] + 1))
        gender[i] = rom.u8(a + g["species_gender_off"])
    c = g["checks"]
    assert all(names.get(i) == n for i, n in c["species"].items()), "species table address is wrong"
    assert all(types.get(i) == t for i, t in c["types"].items()), "species type offset is wrong"
    assert all(gender.get(i) == r for i, r in c["gender"].items()), "species gender offset is wrong"
    return names, types, gender


def named_table(rom, base, stride, inline=False, limit=4000):
    """Entries 1.. of a fixed-stride table whose name is a pointer at +0, or
    (inline) the entry itself starting with the name."""
    out = {}
    miss = 0
    for i in range(1, limit):
        a = base + i * stride
        if rom.off(a) + stride > len(rom.d):
            break
        if inline:
            n = rom.text(a, 21)
        else:
            p = rom.u32(a)
            n = rom.text(p) if rom.is_ptr(p) else None
        if n is None:
            miss += 1
            if miss > 8:
                break
            continue
        miss = 0
        out[i] = n
    return out


def main():
    if len(sys.argv) != 3 or sys.argv[1] not in GAMES:
        sys.exit(f"usage: {sys.argv[0]} <{'|'.join(GAMES)}> <rom.gba>")
    g = GAMES[sys.argv[1]]
    data = open(sys.argv[2], "rb").read()
    sha1 = hashlib.sha1(data).hexdigest()
    if sha1 != g["sha1"]:
        sys.exit(f"sha1 {sha1} is not {g['label']} ({g['sha1']})")
    rom = Rom(data)
    sfx = g["suffix"]
    low = sfx[0].lower() + sfx[1:]

    names, types, gender = species(rom, g)
    write(f"SpeciesNames{sfx}.kt", header(g, "gSpeciesInfo inline names, keyed by the\n"
          "// game's own species ids (National Dex order, then forms/customs).\n") +
          f"val speciesNames{sfx}: Map<Int, String> = mapOf(\n" +
          "".join(f"    {i} to {kstr(n)},\n" for i, n in sorted(names.items())) + ")\n")
    write(f"SpeciesTypes{sfx}.kt", header(g, "gSpeciesInfo types[2], ids per typeNames" + sfx + ".\n") +
          f"val speciesTypeData{sfx}: Map<Int, SpeciesTypes> = mapOf(\n" +
          "".join(f"    {i} to SpeciesTypes({a}, {b}),\n" for i, (a, b) in sorted(types.items())) + ")\n")
    top = max(gender)
    hexs = "".join("%02x" % gender.get(i, 255) for i in range(top + 1))
    write(f"GenderRatios{sfx}.kt", header(g, "gSpeciesInfo genderRatio by species id (0 always\n"
          "// male, 254 always female, 255 genderless, else female when (personality & 0xFF) < ratio).\n") +
          f"val genderRatios{sfx}: IntArray by lazy {{ unhexRatios(\"{hexs}\") }}\n")

    items = named_table(rom, g["items_base"], g["items_stride"], g.get("items_name_inline", False))
    assert all(items.get(i) == n for i, n in g["checks"]["items"].items()), "item table address is wrong"
    write(f"ItemNames{sfx}.kt", header(g, "gItemsInfo names, keyed by item id.\n") +
          f"val itemNames{sfx}: Map<Int, String> = mapOf(\n" +
          "".join(f"    {i} to {kstr(n)},\n" for i, n in sorted(items.items())) + ")\n")
    descs = {}
    for i in items:
        # The description pointer sits items_desc_off bytes before the name
        # (pointer or inline); line breaks come out as spaces.
        p = rom.u32(g["items_base"] + i * g["items_stride"] + g["items_desc_off"])
        d = rom.text(p, 200) if rom.is_ptr(p) else None
        if d:
            descs[i] = d
    assert "20 points" in descs.get(28, ""), "item description offset is wrong"
    write(f"ItemDescriptions{sfx}.kt", header(g, "gItemsInfo descriptions, keyed by item id.\n") +
          f"val itemDescriptions{sfx}: Map<Int, String> = mapOf(\n" +
          "".join(f"    {i} to {kstr(n)},\n" for i, n in sorted(descs.items())) + ")\n")

    moves = named_table(rom, g["moves_base"], g["moves_stride"])
    rows = []
    for i, n in sorted(moves.items()):
        bits = rom.u16(g["moves_base"] + i * g["moves_stride"] + g["move_bits_off"])
        rows.append((i, n, bits & 0x1F, bits >> 7))
    info = {i: (t, p) for i, _, t, p in rows}
    assert all(info.get(i) == v for i, v in g["checks"]["moves"].items()), "move fields are wrong"
    write(f"MoveData{sfx}.kt", header(g, "gMovesInfo name pointer + type/power bitfield.\n") +
          f"val moveData{sfx}: Map<Int, MoveInfo> = mapOf(\n" +
          "".join(f"    {i} to MoveInfo({kstr(n)}, {t}, {p}),\n" for i, n, t, p in rows) + ")\n")

    n = g["chart_n"]
    chart = {}
    for atk in range(n):
        for dfn in range(n):
            v = rom.u32(g["chart"] + 4 * (atk * n + dfn))
            if atk in EXPANSION_TYPE_NAMES and dfn in EXPANSION_TYPE_NAMES and v != 0x1000:
                chart[atk * 100 + dfn] = v * 100 // 0x1000
    assert chart[1 * 100 + 8] == 0 and chart[11 * 100 + 13] == 200 and chart[14 * 100 + 5] == 0, "chart is wrong"
    write(f"TypeChart{sfx}.kt", header(g, "Newer pokeemerald-expansion type ids (NOT vanilla's):\n"
          "// 1-9 Normal..Steel, 11-19 Fire..Fairy. The chart is gTypeEffectiveness [n][n] u32 uq4.12;\n"
          "// key = atkType*100+defType -> percent, pairs not listed are 100.\n") +
          f"val typeNames{sfx}: Map<Int, String> = mapOf(\n" +
          "".join(f"    {i} to {kstr(s)},\n" for i, s in sorted(EXPANSION_TYPE_NAMES.items())) + ")\n\n" +
          f"val typeEffectiveness{sfx}: Map<Int, Int> = mapOf(\n" +
          "".join(f"    {k} to {v},\n" for k, v in sorted(chart.items())) + ")\n")

    mapsecs = {}
    for i in range(0, 256):
        a = g["mapsec_base"] + 8 * i
        p = rom.u32(a + g.get("mapsec_name_off", 0))
        if p == 0:
            continue
        if not rom.is_ptr(p):
            break
        s = rom.text(p)
        if s is not None:  # some slots point at an empty string
            mapsecs[i] = title(s)
    write(f"MapSecData{sfx}.kt", header(g, "gRegionMapEntries names by mapsec id. No region-map\n"
          "// image yet, so every entry is region -1 (text-only Map tab, like Unbound).\n") +
          f"val regionMapImages{sfx} = arrayOf<String>()\n\n" +
          f"val mapSecData{sfx}: Map<Int, MapSecInfo> = mapOf(\n" +
          "".join(f"    {i} to MapSecInfo({kstr(s)}, -1, 0, 0, 0, 0),\n" for i, s in sorted(mapsecs.items())) + ")\n")
    print(f"{len(names)} species, {len(items)} items, {len(moves)} moves, {len(mapsecs)} mapsecs")


if __name__ == "__main__":
    main()

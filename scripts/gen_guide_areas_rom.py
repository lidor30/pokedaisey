#!/usr/bin/env python3
"""
The GUIDE's per-area data (HERE's ITEMS and PEOPLE) for a FireRed-engine ROM
with no source - read from the ROM's own map tables and script bytecode,
in the same line format gen_guide_areas.py writes from a decomp:

  - gMapGroups is found by shape (pointers to lists of pointers to 0x1C-byte
    MapHeaders whose layout / events pointers are in ROM);
  - each map's object events (item balls: `finditem`, i.e. setorcopyvar
    0x8000 ITEM; setorcopyvar 0x8001 N; callstd STD_FIND_ITEM - the object's
    own flag marks it picked up), hidden items (bg events of kind 7, the item
    and flag packed in one word) and every script reachable from the map's
    events (giveitem / givemon / giveegg / trainerbattle, each with the
    setflag after it or the goto_if_set before it);
  - script commands are decoded with their sizes taken from pokefirered's
    asm/macros/event.inc, so a hack that added commands stops that script
    path where an unknown one appears (logged), rather than misreading it.

Validated against the decomp: run on retail FireRed rev 1, it must agree with
GuideAreasFireRedGen.kt (see --compare).

    python3 scripts/gen_guide_areas_rom.py <rom.gba> <NAME> [--compare GuideAreasFireRedGen.kt]
"""
import collections
import re
import struct
import sys

from decomps import decomp

ROM_PATH, NAME = sys.argv[1], sys.argv[2]
COMPARE = sys.argv[sys.argv.index("--compare") + 1] if "--compare" in sys.argv else None
# gMapGroups when the shape scan can't find it (a hack that relocated it):
# the literal in Overworld_GetMapHeaderByGroupAndId.
GROUPS = int(sys.argv[sys.argv.index("--groups") + 1], 16) if "--groups" in sys.argv else None
# Unbound XORs every group pointer with a key (its hooked
# Overworld_GetMapHeaderByGroupAndId: `ldr r3, =key; eors r3, r2`).
GROUPS_XOR = int(sys.argv[sys.argv.index("--groups-xor") + 1], 16) if "--groups-xor" in sys.argv else 0
EVENT_INC = decomp("pokefirered", "asm/macros/event.inc")
R = open(ROM_PATH, "rb").read()
BASE = 0x08000000


def u8(a): return R[a - BASE]
def u16(a): return struct.unpack_from("<H", R, a - BASE)[0]
def u32(a): return struct.unpack_from("<I", R, a - BASE)[0]
def isrom(p): return BASE <= p <= BASE + len(R) - 4


# --- script command sizes, from the decomp's macros -------------------------------
def parse_macros():
    src = open(EVENT_INC, encoding="utf-8").read()
    out = {}
    for m in re.finditer(r"\.macro\s+(\w+)[^\n]*\n(.*?)\.endm", src, re.S):
        body = [l.split("@")[0].strip() for l in m.group(2).splitlines()]
        out[m.group(1)] = [l for l in body if l]
    # asm/macros/map.inc's `map` (a map id as group, num) is used by the warp / object commands.
    out.setdefault("map", [".byte 0", ".byte 0"])
    return out


MACROS = parse_macros()


def paths(lines, depth=0):
    """Every way through [lines]' .if/.elseif/.else branches, as lists of emitted
    items: ("op", n) for a literal `.byte 0xNN`, else ("b", size)."""
    if depth > 8:
        return [[]]
    results = [[]]
    i = 0
    while i < len(lines):
        l = lines[i]
        if re.match(r"\.if", l):
            # Collect the branches up to the matching .endif.
            branches, cur, nest = [], [], 0
            i += 1
            while i < len(lines):
                t = lines[i]
                if re.match(r"\.if", t):
                    nest += 1
                elif t == ".endif":
                    if nest == 0:
                        break
                    nest -= 1
                elif nest == 0 and (t == ".else" or t.startswith(".elseif")):
                    branches.append(cur)
                    cur = []
                    i += 1
                    continue
                cur.append(t)
                i += 1
            branches.append(cur)
            results = [r + p for r in results for b in branches for p in paths(b, depth + 1)]
        else:
            d = re.match(r"\.(byte|2byte|4byte)\s+(\S+)", l)
            if d:
                n = {"byte": 1, "2byte": 2, "4byte": 4}[d.group(1)]
                lit = re.match(r"0x[0-9a-fA-F]+$", d.group(2))
                item = ("op", int(d.group(2), 16)) if n == 1 and lit else ("b", n)
                results = [r + [item] for r in results]
            else:
                name = l.split()[0]
                if name in MACROS:
                    sub = paths(MACROS[name], depth + 1)
                    results = [r + [("b", sum(1 for _ in [0]) * size(p))] for r in results for p in sub[:1]]
                elif not name.startswith("."):
                    results = [r + [("?", 0)] for r in results]
        i += 1
    return results


def size(path):
    return sum(1 if k == "op" else n for k, n in path)


def opcode_sizes():
    sizes = collections.defaultdict(collections.Counter)
    for name, body in MACROS.items():
        for p in paths(body):
            if p and p[0][0] == "op" and all(k != "?" for k, _ in p):
                sizes[p[0][1]][size(p)] += 1
    return {op: c.most_common(1)[0][0] for op, c in sizes.items()}


SIZES = opcode_sizes()
# trainerbattle: type, trainer, local id, then 1-4 pointers by type (event.inc).
TB_PTRS = {0: 2, 1: 3, 2: 3, 3: 1, 4: 3, 5: 2, 6: 4, 7: 3, 8: 4, 9: 2}
END, RETURN, CALL, GOTO, GOTO_IF, CALL_IF, GOTOSTD, CALLSTD = 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09
SETFLAG, GOTO_IF_SET_CHECK, SETORCOPYVAR, GIVEMON, GIVEEGG, TRAINERBATTLE = 0x29, 0x2B, 0x1A, 0x79, 0x7A, 0x5C
ADDITEM = 0x44  # giveitem_msg's first half
MAX_ITEM = 2000  # bigger is something else (hidden coins carry item 0)
MAX_GIFTS_PER_SCRIPT = 5
OBJ_EVENT_GFX_ITEM_BALL = 92


def var_pair(cmds, i):
    """(item, qty) when cmds[i-2:i] are setorcopyvar VAR_0x8000 / VAR_0x8001 - the
    finditem / giveitem preamble; else None."""
    if i < 2 or cmds[i - 2][1] != SETORCOPYVAR or cmds[i - 1][1] != SETORCOPYVAR:
        return None
    a, b = cmds[i - 2][2], cmds[i - 1][2]
    if struct.unpack_from("<H", a, 1)[0] != 0x8000 or struct.unpack_from("<H", b, 1)[0] != 0x8001:
        return None
    return struct.unpack_from("<H", a, 3)[0], struct.unpack_from("<H", b, 3)[0]
STD_OBTAIN_ITEM, STD_FIND_ITEM, STD_RECEIVED_ITEM = 0, 1, 9
unknown_ops = collections.Counter()
unknown_at = {}  # op -> where it was met (--unknown prints the bytes there)


def decode(start):
    """[(addr, op, raw bytes)] of every command reachable from start (each once)."""
    out, todo, seen = [], [start], set()
    while todo:
        a = todo.pop()
        while isrom(a) and a not in seen:
            seen.add(a)
            op = u8(a)
            if op == TRAINERBATTLE:
                kind = u8(a + 1)
                n = 6 + 4 * TB_PTRS.get(kind, 2)
                ptrs = [u32(a + 6 + 4 * i) for i in range(TB_PTRS.get(kind, 2))]
                out.append((a, op, R[a - BASE:a - BASE + n]))
                if kind in (1, 2, 6, 8):  # continue-script variants run their event script
                    todo.append(ptrs[-1])
                a += n
                continue
            n = SIZES.get(op)
            if n is None:
                unknown_ops[op] += 1
                unknown_at.setdefault(op, []).append(a)
                break
            raw = R[a - BASE:a - BASE + n]
            out.append((a, op, raw))
            if op in (CALL, GOTO):
                todo.append(struct.unpack_from("<I", raw, 1)[0])
            elif op in (GOTO_IF, CALL_IF):
                todo.append(struct.unpack_from("<I", raw, 2)[0])
            elif op == 0x21 + 0 and False:
                pass
            if op in (END, RETURN, GOTO, GOTOSTD):
                break
            a += n
    return sorted(out)


def find_map_groups():
    def header_ok(h):
        if not isrom(h) or h % 4:
            return False
        layout, events = u32(h), u32(h + 4)
        return isrom(layout) and isrom(events) and u8(events) < 64
    best = None
    for o in range(0, len(R) - 4 * 40, 4):
        g = BASE + o
        n = 0
        while n < 60:
            lst = u32(g + 4 * n)
            if not isrom(lst) or lst % 4 or not header_ok(u32(lst)):
                break
            n += 1
        if n >= 30 and (best is None or n > best[1]):
            best = (g, n)
    return best


def maps(groups, count):
    """(group, num, header address) of every map."""
    out = []
    for gi in range(count):
        lst = u32(groups + 4 * gi) ^ GROUPS_XOR
        nxt = (u32(groups + 4 * (gi + 1)) ^ GROUPS_XOR) if gi + 1 < count else None
        mi = 0
        while mi < 256:
            p = lst + 4 * mi
            if nxt is not None and p >= nxt and nxt > lst:
                break
            h = u32(p)
            if not isrom(h) or h % 4:
                break  # past the group's list
            # A map slot a hack left empty (no layout / events): skip it, the
            # group's later maps still count (Amethyst has such holes).
            if isrom(u32(h)) and isrom(u32(h + 4)):
                out.append((gi, mi, h))
            mi += 1
    return out


CH = {0xBB + i: chr(65 + i) for i in range(26)}
CH.update({0xD5 + i: chr(97 + i) for i in range(26)})
CH.update({0x00: " ", 0xAD: ".", 0xAE: "-", 0x1B: "é"})
POCKET_KEY_ITEMS = 2  # FireRed's pocket ids (item.h): 1 ITEMS, 2 KEY ITEMS, 3 POKé BALLS, ...


def key_items():
    """Item ids whose gItems record says POCKET_KEY_ITEMS. gItems is found by
    MASTER BALL in slot 1 (struct Item: name[14], itemId @14, pocket @0x1A,
    44 bytes); hacks renumber items, so each record's own itemId is used."""
    def enc(t):
        return bytes(next(k for k, v in CH.items() if v == c) for c in t)
    for name in ("MASTER BALL", "Master Ball"):
        o = R.find(enc(name) + b"\xff")
        while o >= 0:
            base = o - 44
            if base >= 0 and struct.unpack_from("<H", R, o + 14)[0] == 1 and R[base + 14] == 0 and R[base + 15] == 0:
                out, miss, slot = set(), 0, 1
                while miss < 64 and base + 44 * (slot + 1) <= len(R):
                    rec = base + 44 * slot
                    item, pocket = struct.unpack_from("<H", R, rec + 14)[0], R[rec + 0x1A]
                    if 0 < pocket <= 8:
                        miss = 0
                        if pocket == POCKET_KEY_ITEMS:
                            out.add(item)
                    else:
                        miss += 1
                    slot += 1
                print("gItems at %#x, %d key items" % (BASE + base, len(out)), file=sys.stderr)
                return out
            o = R.find(enc(name) + b"\xff", o + 1)
    # Renamed items (Celia's hack): by shape - 44-byte records whose itemId
    # counts 1, 2, 3 ... and whose pocket is a real one.
    for base in range(0, len(R) - 44 * 40, 4):
        if all(struct.unpack_from("<H", R, base + 44 * k + 14)[0] == k and 0 < R[base + 44 * k + 0x1A] <= 8
               for k in range(1, 30)):
            out, miss = set(), 0
            for k in range(1, 2000):
                rec = base + 44 * k
                if rec + 44 > len(R) or miss > 64:
                    break
                if struct.unpack_from("<H", R, rec + 14)[0] != k:
                    miss += 1
                    continue
                miss = 0
                if R[rec + 0x1A] == POCKET_KEY_ITEMS:
                    out.add(k)
            print("gItems at %#x (by shape), %d key items" % (BASE + base, len(out)), file=sys.stderr)
            return out
    return set()


def flag_near(cmds, i):
    """The flag a gift at cmds[i] is marked with: the next setflag, else a goto_if_set... before it."""
    for a, op, raw in cmds[i + 1:i + 12]:
        if op == SETFLAG:
            return struct.unpack_from("<H", raw, 1)[0]
        if op in (END, RETURN):
            break
    for a, op, raw in reversed(cmds[max(0, i - 12):i]):
        if op == GOTO_IF_SET_CHECK:  # checkflag, followed by goto_if
            return struct.unpack_from("<H", raw, 1)[0]
    return 0


def main():
    if GROUPS:
        groups, count = GROUPS, 0
        while isrom(u32(groups + 4 * count) ^ GROUPS_XOR) and count < 255:
            count += 1
    else:
        groups, count = find_map_groups()
    print("gMapGroups at %#x, %d groups" % (groups, count), file=sys.stderr)
    keys = key_items()
    rows = set()
    for gi, mi, h in maps(groups, count):
        sec = u8(h + 0x14)
        ev = u32(h + 4)
        n_obj, n_warp, n_coord, n_bg = R[ev - BASE:ev - BASE + 4]
        objs, coords, bgs = u32(ev + 4), u32(ev + 12), u32(ev + 16)
        where = ""  # the ROM has no map names: everything counts as "around" its area
        here = "%d.%d" % (gi, mi)
        scripts = []
        for k in range(n_obj if isrom(objs) else 0):
            o = objs + 0x18 * k
            s, flag = u32(o + 0x10), u16(o + 0x14)
            if not isrom(s):
                continue
            cmds = decode(s)
            # An item ball: finditem straight in its own script; its flag is the object's.
            for j, (_, op, raw) in enumerate(cmds):
                pair = var_pair(cmds, j) if op == CALLSTD and raw[1] == STD_FIND_ITEM else None
                if pair and 0 < pair[0] < MAX_ITEM:
                    rows.add((sec, "ITEM", pair[0], flag, pair[1], 0, where))
            scripts.append((cmds, u8(o + 1) == OBJ_EVENT_GFX_ITEM_BALL))
        for k in range(n_coord if isrom(coords) else 0):
            s = u32(coords + 0x10 * k + 0xC)
            if isrom(s):
                scripts.append((decode(s), False))
        for k in range(n_bg if isrom(bgs) else 0):
            b = bgs + 0xC * k
            kind, data = u8(b + 5), u32(b + 8)
            if kind == 7:
                item, flag, qty = data & 0xFFFF, (data >> 16) & 0xFF, (data >> 24) & 0x7F
                if 0 < item < MAX_ITEM:
                    rows.add((sec, "HIDDEN", item, 1000 + flag, max(qty, 1), 0, where))
            elif isrom(data):
                scripts.append((decode(data), False))
        for cmds, ball in scripts:
            found = set()
            before = set(rows)
            if "--bosses" in sys.argv:
                battles = [struct.unpack_from("<H", raw, 2)[0] for _, op, raw in cmds if op == TRAINERBATTLE]
                flags = [struct.unpack_from("<H", raw, 1)[0] for _, op, raw in cmds if op == SETFLAG]
                key = [f for f in flags if 0x820 <= f <= 0x827 or 0x4B0 <= f <= 0x4C0]
                if battles and key:
                    print("BOSS mapsec %d map %s trainers %s flags %s" % (sec, here, battles, [hex(f) for f in key]), file=sys.stderr)
            for i, (a, op, raw) in enumerate(cmds):
                pair = var_pair(cmds, i) if op == CALLSTD and raw[1] == STD_OBTAIN_ITEM else None
                if op == ADDITEM:
                    pair = struct.unpack_from("<HH", raw, 1)
                if pair and 0 < pair[0] < MAX_ITEM:
                    # An item ball whose own script hands the item over (Odyssey's
                    # balls run giveitem, not finditem) is still an item lying there.
                    kind = "ITEM" if ball else "KEY" if pair[0] in keys else "GIFT"
                    rows.add((sec, kind, pair[0], flag_near(cmds, i), pair[1], 0, where))
                elif op in (GIVEMON, GIVEEGG):
                    species = struct.unpack_from("<H", raw, 1)[0]
                    if 0 < species < 0x4000:
                        rows.add((sec, "MON" if op == GIVEMON else "EGG", species, flag_near(cmds, i), 1, 0, where))
            # One script handing out many different things is a shop, a menu or a
            # debug list (Radical Red has thousands), not gifts: drop them all.
            new = rows - before
            if len({(r[1], r[2]) for r in new}) > MAX_GIFTS_PER_SCRIPT:
                rows.difference_update(new)
    if unknown_ops:
        print("unknown script commands (paths stopped there): %s" % dict(unknown_ops.most_common(12)), file=sys.stderr)
        if "--unknown" in sys.argv:
            for op, at in sorted(unknown_at.items(), key=lambda kv: -len(kv[1])):
                for a in sorted(set(at))[:6]:
                    print("  %#04x @%#x: %s" % (op, a, R[a - BASE:a - BASE + 16].hex(" ")), file=sys.stderr)
    lines = sorted("%d|%s|%d|%d|%d|%d|%s" % r for r in rows)
    if COMPARE:
        ref = set()
        for l in open(COMPARE, encoding="utf-8"):
            f = l.strip().split("|")
            if len(f) == 7 and f[0].isdigit():
                ref.add(tuple(f[:6]))
        # The decomp file has no KEY kind (FireRed's generator doesn't tag key items).
        got = {tuple(f[:1] + ["GIFT" if f[1] == "KEY" else f[1]] + f[2:6]) for f in (l.split("|") for l in lines)}
        print("decomp rows %d, ROM rows %d, both %d" % (len(ref), len(got), len(ref & got)), file=sys.stderr)
        for kind in ("ITEM", "HIDDEN", "GIFT", "MON", "EGG", "TRADE"):
            a = {r for r in ref if r[1] == kind}
            b = {r for r in got if r[1] == kind}
            print("  %-6s decomp %3d  rom %3d  missing %3d  extra %3d" % (kind, len(a), len(b), len(a - b), len(b - a)), file=sys.stderr)
            for r in sorted(a - b)[:4]:
                print("     missing", r, file=sys.stderr)
            for r in sorted(b - a)[:4]:
                print("     extra  ", r, file=sys.stderr)
    if "--kotlin" in sys.argv:
        print("package com.pokedaisey.app.companion.data")
        print()
        print("// Generated by scripts/gen_guide_areas_rom.py from the %s ROM - don't edit by hand." % NAME)
        print("// %d things in %d areas. Line format: mapsec|kind|id|flag|qty|wants|where (see GuideAreas.kt)."
              % (len(lines), len({l.split('|')[0] for l in lines})))
        print("internal val GUIDE_AREAS_%s_RAW: List<String> = listOf(" % NAME)
        for k in range(0, len(lines), 800):
            print('    """')
            for l in lines[k:k + 800]:
                print(l)
            print('""",')
        print(")")
    else:
        for l in lines:
            print(l)


main()

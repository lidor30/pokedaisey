#!/usr/bin/env python3
"""Generates the in-game party-slot look for the companion's Party tab,
straight from each pinned decomp ($DECOMPS/pokefirered, $DECOMPS/pokeemerald - see
CLAUDE.md) - no hand-drawn approximations. For each game, everything the game
itself uses for the MAIN party slot (the big left box of its party menu):

  assets/<dir>/slot_<state>.png  80x56 slot frame, rendered from
      graphics/party_menu/slot_main.bin (or slot_main_no_hp.bin for the
      nohp_* states - what an EGG uses) + bg.png tiles, in window palette 3
      with each state's LoadPartyBoxPalette overrides. Index 0 -> transparent.
  assets/<dir>/pokeball.png      32x64, closed (top) / open (bottom).
  assets/<dir>/status_icons.png  normalised to one row of 32x8 icons: PSN,
      PAR, SLP, FRZ, BRN, PKRS, FNT (FireRed's sheet is already a row,
      Emerald's is a column).
  assets/<dir>/font_small.png    FONT_SMALL, normalised to 8x16 cells, 32 per
      row, indexed by Gen3 char code (+0x100 for 0xF9 extra symbols); glyph
      value 1 (text) -> red, 2 (shadow) -> blue, for runtime tinting.
  app/.../companion/ui/PartySlotStylesGen.kt  one PartySlotStyle per game:
      glyph widths, colours, layout numbers, stretch lines.

FireRed's and Emerald's PNGs are NOT bundled any more: the app rebuilds the
same files from the player's ROM (companion/data/RomArt.kt, same layouts), so
for those two this script only checks the art and writes the Kotlin style.

Frame + text + HP bar were checked pixel-for-pixel against real screenshots
(mgba_dump `shot`) for both games.

Usage: scripts/gen_party_assets.py   (needs both decomps built once, so the
FireRed .gbapal files exist; Emerald's palettes come from its PNGs). Heart and
Soul's art comes from a sparse checkout of its source at the release tag:
  git clone --depth 1 --branch Release-v2.0.6 --filter=blob:none --sparse \
    https://github.com/PokemonHnS-Development/pokehns-expansion $DECOMPS/pokehns
  git -C $DECOMPS/pokehns sparse-checkout set --no-cone /graphics/party_menu/ \
    /graphics/interface/status_icons.png /graphics/fonts/ /src/fonts.c
"""
import os
import re
import struct

from PIL import Image

from decomps import decomp

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = decomp()
ASSETS = os.path.join(HERE, "../app/src/main/assets")
KOTLIN = os.path.join(HERE, "../app/src/main/kotlin/com/pokedaisey/app/companion/ui/PartySlotStylesGen.kt")

# Per-game facts, each read off that game's own decomp source (cited).
GAMES = {
    "FireRed": dict(
        decomp="pokefirered", asset_dir="partyfr", from_rom=True,
        bg_pal="graphics/party_menu/bg.gbapal",
        ball_pal="graphics/party_menu/pokeball.gbapal",
        status_png="graphics/interface/status_icons.png", status_pal="graphics/interface/status_icons.gbapal",
        status_layout="row",
        font_png="graphics/fonts/latin_small.png", font_cell=(8, 16),
        widths=("src/text.c", "sFontSmallLatinGlyphWidths"),
        lv_glyph=0x105,  # gText_Lv = "{LV_2}" = F9 05
        pad_glyph=0x00,  # ConvertIntToDecimalStringN right-align pads with CHAR_SPACE
        # sPartyBoxInfoRects[PARTY_BOX_LEFT_COLUMN] / sPartyMenuSpriteCoords[SINGLE][0]
        hp_y=36, status_x=56,
        # Stretch lines: columns/rows identical to their neighbour in every
        # state (asserted below). Bottom row sits under the HP digits.
        cols=(8, 49), rows=(7, 31, 47),
    ),
    "Emerald": dict(
        decomp="pokeemerald", asset_dir="partyem", from_rom=True,
        bg_pal=None, ball_pal=None,  # palettes embedded in the PNGs (INCGFX)
        status_png="graphics/interface/status_icons.png", status_pal=None,
        status_layout="column",
        font_png="graphics/fonts/latin_small.png", font_cell=(16, 16),  # glyphs <= 8px wide: left half
        widths=("src/fonts.c", "gFontSmallLatinGlyphWidths"),
        lv_glyph=0x34,  # gText_LevelSymbol = "{LV}" = 34
        pad_glyph=0x77,  # ... pads with CHAR_SPACER here (CHAR_SPACE is only 3px wide)
        hp_y=37, status_x=50,
        cols=(8, 49), rows=(7, 31, 47),  # digits end on row 48; 47 is still under them + uniform
    ),
    # Heart and Soul v2.0.6 (pokeemerald-expansion): Emerald's party_menu.c
    # unchanged (same sPartyBoxInfoRects, sprite coords and palette-ID
    # tables), re-skinned by graphics/party_menu/hns/bg.png (tiles + palette)
    # under the stock slot tilemaps. Its font and status icons are the
    # expansion's own. Checked against a real HnS party-menu screenshot.
    "HeartAndSoul": dict(
        decomp="pokehns", asset_dir="partyhns",
        bg_png="graphics/party_menu/hns/bg.png",
        bg_pal=None, ball_pal=None,
        status_png="graphics/interface/status_icons.png", status_pal=None,
        status_layout="column",
        font_png="graphics/fonts/latin_small.png", font_cell=(16, 16),
        widths=("src/fonts.c", "gFontSmallLatinGlyphWidths"),
        lv_glyph=0x34, pad_glyph=0x77,
        hp_y=37, status_x=50,
        cols=(8, 49), rows=(7, 31, 47),
        # The whole party-menu screen minus its slots: hns/bg.bin, 30x20 tiles,
        # with the SEL-ORDER hint and the CANCEL button's base swapped for the
        # plain striped panel tile (the app has neither).
        backdrop=("graphics/party_menu/hns/bg.bin", "partybg/hns.png"),
        backdrop_blank=(0x100E, [(tx, 15) for tx in range(3, 8)] + [(tx, ty) for ty in (17, 18) for tx in range(23, 30)]),
    ),
}

# Shared by both games (identical in both decomps' party_menu.h / party_menu.c).
WIN_PAL = 3  # sSinglePartyMenuWindowTemplate[0].paletteNum
STATES = {  # (idx 4,5,6 <- ids1), (idx 1,7,8 <- ids2)
    "normal": ((52, 53, 54), (49, 55, 56)),             # sPartyBoxEmptySlotPalIds1/2
    "selected": ((116, 117, 118), (97, 103, 104)),      # sPartyBoxCurrSelectionPalIds1/2
    "fainted": ((84, 85, 86), (81, 87, 88)),            # sPartyBoxFaintedPalIds1/2
    "selected_fainted": ((148, 149, 150), (97, 103, 104)),  # CurrSelectionFainted + CurrSelection2
}


def expand(v5):
    # mGBA's own 5->8 bit expansion - what the running game actually shows
    # (v * 255 / 31 is off by one on most values).
    return (v5 << 3) | (v5 >> 2)


def gba_rgb(c):
    return (expand(c & 31), expand((c >> 5) & 31), expand((c >> 10) & 31))


def load_pal(decomp, gbapal, png):
    if gbapal:
        b = open(os.path.join(decomp, gbapal), "rb").read()
        return [gba_rgb(struct.unpack_from("<H", b, i)[0]) for i in range(0, len(b), 2)]
    p = Image.open(os.path.join(decomp, png)).getpalette()
    # gbagfx stores 5-bit colours as v << 3 in the PNG; re-expand like mGBA.
    return [tuple(expand(p[i + k] >> 3) for k in range(3)) for i in range(0, len(p), 3)]


def indexed_to_rgba(im, pal):
    out = Image.new("RGBA", im.size, (0, 0, 0, 0))
    ip, op = im.load(), out.load()
    for y in range(im.height):
        for x in range(im.width):
            v = ip[x, y] & 15
            if v:
                op[x, y] = pal[v] + (255,)
    return out


def uniform_lines(imgs, axis):
    """Indices identical to the previous line in EVERY image (axis 0 = columns, 1 = rows)."""
    res = None
    for im in imgs:
        W, H = im.size
        if axis == 0:
            line = lambda i: [im.getpixel((i, y)) for y in range(H)]; n = W
        else:
            line = lambda i: [im.getpixel((x, i)) for x in range(W)]; n = H
        s = {i for i in range(1, n) if line(i) == line(i - 1)}
        res = s if res is None else res & s
    return res


def build(name, cfg):
    d = os.path.join(ROOT, cfg["decomp"])
    g = lambda p: os.path.join(d, p)
    out_dir = os.path.join(ASSETS, cfg["asset_dir"])

    def save(im, path):
        if not cfg.get("from_rom"):  # RomArt makes these at runtime
            os.makedirs(os.path.dirname(path), exist_ok=True)
            im.save(path)
    bg_png = cfg.get("bg_png", "graphics/party_menu/bg.png")
    pal = load_pal(d, cfg["bg_pal"], bg_png)
    base = pal[WIN_PAL * 16:WIN_PAL * 16 + 16]

    # --- slot frames ---
    tiles = Image.open(g(bg_png))
    tpx, tcols = tiles.load(), tiles.width // 8
    maps = {"main": open(g("graphics/party_menu/slot_main.bin"), "rb").read(),
            "nohp": open(g("graphics/party_menu/slot_main_no_hp.bin"), "rb").read()}
    states = dict(STATES)
    states["nohp_normal"] = STATES["normal"]
    states["nohp_selected"] = STATES["selected"]
    frames = {}
    colors = {}
    for st, (ids1, ids2) in states.items():
        cols = list(base)
        for idx, pid in zip((4, 5, 6), ids1): cols[idx] = pal[pid]
        for idx, pid in zip((1, 7, 8), ids2): cols[idx] = pal[pid]
        colors[st] = cols
        tmap = maps["nohp" if st.startswith("nohp") else "main"]
        img = Image.new("RGBA", (80, 56), (0, 0, 0, 0))
        out = img.load()
        for ty in range(7):
            for tx in range(10):
                t = tmap[ty * 10 + tx]
                sx, sy = (t % tcols) * 8, (t // tcols) * 8
                for y in range(8):
                    for x in range(8):
                        v = tpx[sx + x, sy + y] & 15  # 4bpp: low nibble = colour index
                        if v:
                            out[tx * 8 + x, ty * 8 + y] = cols[v] + (255,)
        save(img, os.path.join(out_dir, f"slot_{st}.png"))
        frames[st] = img
    ucols, urows = uniform_lines(frames.values(), 0), uniform_lines(frames.values(), 1)
    assert set(cfg["cols"]) <= ucols, (name, "stretch cols not uniform", sorted(ucols))
    assert set(cfg["rows"]) <= urows, (name, "stretch rows not uniform", sorted(urows))
    bbox = frames["normal"].getbbox()
    assert all(f.getbbox() == bbox for f in frames.values()), (name, "states differ in extent")

    # --- backdrop: the screen's own BG tilemap (u16 entries: tile | hflip
    # 0x400 | vflip 0x800 | palette << 12), opaque ---
    if "backdrop" in cfg:
        bin_path, png = cfg["backdrop"]
        tm = open(g(bin_path), "rb").read()
        bd = Image.new("RGBA", (240, 160))
        bo = bd.load()
        for ty in range(20):
            for tx in range(30):
                e = struct.unpack_from("<H", tm, (ty * 32 + tx) * 2)[0]
                if (tx, ty) in cfg.get("backdrop_blank", (0, []))[1]:
                    e = cfg["backdrop_blank"][0]
                t, pn = e & 0x3FF, e >> 12
                sx, sy = (t % tcols) * 8, (t // tcols) * 8
                for y in range(8):
                    for x in range(8):
                        v = tpx[sx + (7 - x if e & 0x400 else x), sy + (7 - y if e & 0x800 else y)] & 15
                        bo[tx * 8 + x, ty * 8 + y] = pal[pn * 16 + v] + (255,)
        save(bd, os.path.join(ASSETS, png))

    # --- sprites ---
    ball = Image.open(g("graphics/party_menu/pokeball.png"))
    save(indexed_to_rgba(ball, load_pal(d, cfg["ball_pal"], "graphics/party_menu/pokeball.png")), os.path.join(out_dir, "pokeball.png"))
    st_img = Image.open(g(cfg["status_png"]))
    st_rgba = indexed_to_rgba(st_img, load_pal(d, cfg["status_pal"], cfg["status_png"]))
    if cfg["status_layout"] == "column":  # 32x8 icons stacked -> one row
        n = st_rgba.height // 8
        row = Image.new("RGBA", (32 * n, 8), (0, 0, 0, 0))
        for k in range(n):
            row.paste(st_rgba.crop((0, k * 8, 32, k * 8 + 8)), (k * 32, 0))
        st_rgba = row
    save(st_rgba, os.path.join(out_dir, "status_icons.png"))

    # --- FONT_SMALL, normalised to 8x16 cells, 32 per row ---
    f = Image.open(g(cfg["font_png"]))
    fp = f.load()
    cw, ch = cfg["font_cell"]
    per_row = f.width // cw
    n_glyphs = per_row * (f.height // ch)
    fa = Image.new("RGBA", (256, 16 * ((n_glyphs + 31) // 32)), (0, 0, 0, 0))
    fo = fa.load()
    for gid in range(n_glyphs):
        sx, sy = (gid % per_row) * cw, (gid // per_row) * ch
        dx, dy = (gid % 32) * 8, (gid // 32) * 16
        for y in range(16):
            for x in range(8):
                v = fp[sx + x, sy + y]
                if v == 1:
                    fo[dx + x, dy + y] = (255, 0, 0, 255)
                elif v == 2:
                    fo[dx + x, dy + y] = (0, 0, 255, 255)
    save(fa, os.path.join(out_dir, "font_small.png"))
    src, sym = cfg["widths"]
    widths = [int(x) for x in re.findall(r"\d+", re.search(
        sym + r"\[\] =\s*\{([^}]*)\}", open(g(src)).read()).group(1))]
    assert len(widths) >= 512, (name, len(widths))

    n = colors["normal"]
    argb = lambda c: "0xFF%02X%02X%02X" % c
    return f'''val {name}PartyStyle = PartySlotStyle(
    frameDir = "{cfg["asset_dir"]}",
    fontAsset = "{cfg["asset_dir"]}/font_small.png",
    statusAsset = "{cfg["asset_dir"]}/status_icons.png",
    pokeballAsset = "{cfg["asset_dir"]}/pokeball.png",
    glyphWidths = intArrayOf({", ".join(map(str, widths[:512]))}),
    lvGlyph = 0x{cfg["lv_glyph"]:X},
    padGlyph = 0x{cfg["pad_glyph"]:X},
    text = {argb(n[3])}, textShadow = {argb(n[2])},
    male = {argb(pal[59])}, maleShadow = {argb(pal[60])},
    female = {argb(pal[75])}, femaleShadow = {argb(pal[76])},
    hpGreenTop = {argb(pal[58])}, hpGreen = {argb(pal[57])},
    hpYellowTop = {argb(pal[74])}, hpYellow = {argb(pal[73])},
    hpRedTop = {argb(pal[90])}, hpRed = {argb(pal[89])},
    hpEmptyTop = {argb(n[13])}, hpEmpty = {argb(n[2])},
    slotW = 80, slotH = 56,
    nameX = 24, nameY = 11,
    levelX = 32, levelY = 20,
    genderX = 64, genderY = 20,
    hpX = 38, maxHpX = 53, hpY = {cfg["hp_y"]},
    barX = 24, barY = 35, barW = 48,
    // sPartyMenuSpriteCoords[SINGLE][0] centres (32x32 sprites) -> top-left,
    // relative to the slot window (screen 8,24).
    iconX = 16 - 16 - 8, iconY = 40 - 16 - 24,
    // AnimateSelectedPartyIcon: the icon at x 16 rests 4px UP when not selected.
    iconRestDx = 0, iconRestDy = -4,
    ballX = 16 - 16 - 8, ballY = 34 - 16 - 24,
    statusX = {cfg["status_x"]} - 16 - 8, statusY = 52 - 4 - 24,
    leftCol = {cfg["cols"][0]}, midCol = {cfg["cols"][1]},
    topRow = {cfg["rows"][0]}, midRow = {cfg["rows"][1]}, bottomRow = {cfg["rows"][2]}, stretchRows = true,
    visibleW = {bbox[2]}, visibleH = {bbox[3]},
    regionX0 = 16 - 16 - 8, regionY0 = 34 - 16 - 24,
    eggName = "EGG",
    upperCaseNames = true,
    hasEmptySlot = false,
)
'''


def main():
    parts = [build(name, cfg) for name, cfg in GAMES.items()]
    kt = """package com.pokedaisey.app.companion.ui

// GENERATED by scripts/gen_party_assets.py from the pinned pokefirered /
// pokeemerald decomps and Heart and Soul's source - do not hand-edit; rerun
// the script instead.

""" + "\n".join(parts)
    open(KOTLIN, "w").write(kt)
    print("wrote", KOTLIN)


if __name__ == "__main__":
    main()

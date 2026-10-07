#!/usr/bin/env python3
"""Builds assets/fonts/PixelMplusJP.ttf, the Japanese fallback behind Pixel Operator.

Input: PixelMplus10-Regular.ttf from https://github.com/itouhiro/PixelMplus
(release PixelMplus-20130602.zip; M+ FONT LICENSE - free to use, modify and
redistribute, copied beside the output as LICENSE-PixelMplus.txt).

PixelMplus10 draws on a 10-pixel grid of 100 units (unitsPerEm 1000). Pixel
Operator's pixel is also 100 units, at unitsPerEm 1600 - so re-declaring this
font's em as 1600 (outlines untouched) puts both on the same pixel grid: the
kana / kanji come out as tall as Pixel Operator's caps, crisp at every whole
GbaTextMetrics scale. Line metrics are copied from Pixel Operator so a line
holding Japanese is no taller than one without. Only Japanese is kept (CJK
punctuation, kana, kanji, full-width forms): any other glyph Pixel Operator
lacks (½, ◀) keeps falling through to the system font, at its usual size.
Needs fonttools.

    python3 scripts/gen_jp_font.py PixelMplus10-Regular.ttf
"""
import sys
from pathlib import Path

from fontTools import subset
from fontTools.ttLib import TTFont

ROOT = Path(__file__).resolve().parent.parent
FONTS = ROOT / "app/src/main/assets/fonts"
FAMILY = "PokeDaisy PixelMplus JP"

src, ref = TTFont(sys.argv[1]), TTFont(FONTS / "PixelOperator.ttf")
assert src["head"].unitsPerEm == 1000
src["head"].unitsPerEm = ref["head"].unitsPerEm
src["hhea"].ascent, src["hhea"].descent, src["hhea"].lineGap = ref["hhea"].ascent, ref["hhea"].descent, ref["hhea"].lineGap
o, r = src["OS/2"], ref["OS/2"]
o.sTypoAscender, o.sTypoDescender, o.sTypoLineGap = r.sTypoAscender, r.sTypoDescender, r.sTypoLineGap
o.usWinAscent, o.usWinDescent = r.usWinAscent, r.usWinDescent
# A modified copy gets its own name (M+ allows any change; this keeps it from passing for the original).
for rec in src["name"].names:
    if rec.nameID in (1, 4, 16):
        rec.string = FAMILY
    elif rec.nameID == 6:
        rec.string = FAMILY.replace(" ", "") + "-Regular"
JAPANESE = [(0x3000, 0x30FF), (0x3400, 0x4DBF), (0x4E00, 0x9FFF), (0xFF00, 0xFFEF)]
keep = [c for c in src.getBestCmap() if any(a <= c <= b for a, b in JAPANESE)]
sub = subset.Subsetter(subset.Options(name_IDs=["*"], name_languages=["*"], notdef_outline=True))
sub.populate(unicodes=keep)
sub.subset(src)
src.save(FONTS / "PixelMplusJP.ttf")
print("wrote", FONTS / "PixelMplusJP.ttf")

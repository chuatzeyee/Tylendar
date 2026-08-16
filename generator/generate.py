"""Tylendar renderer.

Renders a minimalist Chinese almanac (huangli) page for a
Good Display GDEM102F91 e-paper panel: 960x640, four colors
(black, white, yellow, red), SSD2677 controller.

Outputs:
  output/preview.png   what the panel will show, portrait 640x960
  output/tylendar.bin  packed panel data, 153600 bytes, 2 bits per pixel,
                       4 pixels per byte MSB first, native 960x640 rows,
                       00 black, 01 white, 10 yellow, 11 red
"""

import sys
from datetime import date, datetime
from pathlib import Path
from zoneinfo import ZoneInfo

from PIL import Image, ImageDraw, ImageFont
from lunar_python import Solar

ROOT = Path(__file__).resolve().parent
OUT = ROOT.parent / "output"

W, H = 640, 960
MARGIN = 52

BLACK = (12, 12, 12)
WHITE = (255, 255, 255)
RED = (186, 32, 41)
YELLOW = (236, 183, 15)
PALETTE = {BLACK: 0b00, WHITE: 0b01, YELLOW: 0b10, RED: 0b11}

SERIF = str(ROOT / "fonts" / "ChironSungHK.ttf")
SANS_SC = str(ROOT / "fonts" / "NotoSansSC.ttf")
LATIN = str(ROOT / "fonts" / "Fraunces.ttf")

# Licensed fonts dropped into fonts/private/ (gitignored) are picked up
# automatically for local rendering. GitHub Actions never has them and
# renders with the open fonts above.
PRIVATE = ROOT / "fonts" / "private"
MTR_SUNG = PRIVATE / "mtr-sung.ttf"
CANELA_BY_WEIGHT = [
    (250, PRIVATE / "canelaweb-thin.ttf"),
    (450, PRIVATE / "canelaweb-regular.ttf"),
    (650, PRIVATE / "canelaweb-medium.ttf"),
    (800, PRIVATE / "canelaweb-bold.ttf"),
    (10_000, PRIVATE / "canelaweb-black.ttf"),
]


def _mtr_codepoints():
    from fontTools.ttLib import TTFont

    return set(TTFont(str(MTR_SUNG)).getBestCmap())


MTR_COVER = _mtr_codepoints() if MTR_SUNG.exists() else None


class DuoFont:
    """Primary font with per-character fallback for uncovered glyphs.
    MTR Sung is traditional only, so simplified characters fall back to
    Chiron Sung HK, which shares its Hong Kong Song style."""

    def __init__(self, primary, fallback, cover):
        self.primary = primary
        self.fallback = fallback
        self.cover = cover

    def pick(self, c):
        return self.primary if ord(c) in self.cover else self.fallback

    def getlength(self, text):
        return sum(self.pick(c).getlength(c) for c in text)

TIMEZONE = "Asia/Singapore"
YI_JI_MAX = 4

# Set to False if the image shows up rotated 180 degrees on your panel.
FPC_AT_BOTTOM = True

WEEKDAY_EN = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY",
              "FRIDAY", "SATURDAY", "SUNDAY"]
MONTH_EN = ["JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE", "JULY",
            "AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER"]
CN_NUM = "一二三四五六七八九十"


def serif(size, weight=400):
    font = ImageFont.truetype(SERIF, size)
    font.set_variation_by_axes([weight, 0])
    if MTR_COVER is None:
        return font
    return DuoFont(ImageFont.truetype(str(MTR_SUNG), size), font, MTR_COVER)


def sans_sc(size, weight=400):
    font = ImageFont.truetype(SANS_SC, size)
    font.set_variation_by_axes([weight])
    return font


def latin(size, weight=400, opsz=32, soft=0):
    for max_weight, path in CANELA_BY_WEIGHT:
        if path.exists() and weight <= max_weight:
            return ImageFont.truetype(str(path), size)
    font = ImageFont.truetype(LATIN, size)
    font.set_variation_by_axes([opsz, weight, soft, 0])
    return font


def draw_text(img, xy, text, font, fill, anchor="la", tracking=0):
    """Draw text through a thresholded mask so every pixel is an exact
    palette color. Anti-aliased edge pixels would otherwise quantize into
    speckle on the panel."""
    layer = Image.new("L", img.size, 0)
    d = ImageDraw.Draw(layer)
    duo = isinstance(font, DuoFont)
    if tracking or duo:
        total = sum(font.getlength(c) for c in text) + tracking * (len(text) - 1)
        x, y = xy
        if anchor[0] == "m":
            x -= total / 2
        elif anchor[0] == "r":
            x -= total
        for c in text:
            f = font.pick(c) if duo else font
            d.text((x, y), c, font=f, fill=255, anchor="l" + anchor[1])
            x += font.getlength(c) + tracking
    else:
        d.text(xy, text, font=font, fill=255, anchor=anchor)
    mask = layer.point(lambda p: 255 if p >= 128 else 0)
    img.paste(fill, (0, 0), mask)


def text_width(text, font, tracking=0):
    return sum(font.getlength(c) for c in text) + tracking * (len(text) - 1)


def huangli(d):
    """Collect everything the layout needs for one Gregorian date."""
    solar = Solar.fromYmd(d.year, d.month, d.day)
    lunar = solar.getLunar()
    prev_jq = lunar.getPrevJieQi(True)
    next_jq = lunar.getNextJieQi(True)
    jq_today = prev_jq.getSolar().toYmd() == d.isoformat()
    days_into = (d - date.fromisoformat(prev_jq.getSolar().toYmd())).days
    festivals = list(lunar.getFestivals()) + list(solar.getFestivals())
    return {
        "solar": solar,
        "day": d.day,
        "month_en": MONTH_EN[d.month - 1],
        "year": d.year,
        "weekday_cn": "星期" + solar.getWeekInChinese(),
        "weekday_en": WEEKDAY_EN[d.weekday()],
        "is_sunday": d.weekday() == 6,
        "lunar_md": lunar.getMonthInChinese() + "月" + lunar.getDayInChinese(),
        "ganzhi_year": lunar.getYearInGanZhi(),
        "ganzhi_month": lunar.getMonthInGanZhi(),
        "ganzhi_day": lunar.getDayInGanZhi(),
        "zodiac": lunar.getYearShengXiao(),
        "yi": list(lunar.getDayYi())[:YI_JI_MAX],
        "ji": list(lunar.getDayJi())[:YI_JI_MAX],
        "jieqi_today": prev_jq.getName() if jq_today else None,
        "jieqi_line": (prev_jq.getName() + " 第" + cn_number(days_into + 1) + "天"
                       if not jq_today else None),
        "next_jieqi": next_jq.getName(),
        "chong": "冲" + lunar.getDayChongShengXiao(),
        "sha": "煞" + lunar.getDaySha(),
        "nayin": lunar.getDayNaYin(),
        "festivals": festivals,
    }


def cn_number(n):
    if n <= 10:
        return CN_NUM[n - 1]
    if n < 20:
        return "十" + CN_NUM[n - 11]
    return CN_NUM[n // 10 - 1] + "十" + (CN_NUM[n % 10 - 1] if n % 10 else "")


def render(d):
    img = Image.new("RGB", (W, H), WHITE)
    draw = ImageDraw.Draw(img)
    hl = huangli(d)
    accent = RED if (hl["is_sunday"] or hl["festivals"] or hl["jieqi_today"]) else BLACK

    # Header: lunar month top left, ganzhi seal top right
    draw_text(img, (MARGIN, 64), f"{hl['year']}", latin(30, 550), BLACK)
    draw_text(img, (MARGIN, 104), hl["month_en"], latin(21, 600), BLACK, tracking=6)

    seal = 78
    sx, sy = W - MARGIN - seal, 56
    draw.rectangle([sx, sy, sx + seal, sy + seal], fill=RED)
    f_seal = serif(30, 700)
    draw_text(img, (sx + seal / 2, sy + 21), hl["ganzhi_year"][0], f_seal, WHITE, anchor="mm")
    draw_text(img, (sx + seal / 2, sy + 57), hl["ganzhi_year"][1], f_seal, WHITE, anchor="mm")

    # Hairline under header
    draw.rectangle([MARGIN, 168, W - MARGIN, 169], fill=BLACK)

    # The day
    draw_text(img, (W / 2, 340), str(hl["day"]), latin(330, 200, opsz=144), accent, anchor="mm")
    draw_text(img, (W / 2, 532), hl["weekday_cn"], sans_sc(30, 500), BLACK, anchor="mm", tracking=8)
    draw_text(img, (W / 2, 574), hl["weekday_en"], latin(17, 550), BLACK, anchor="mm", tracking=7)

    # Divider with yellow diamond
    dy = 624
    draw.rectangle([MARGIN, dy, W / 2 - 26, dy + 1], fill=BLACK)
    draw.rectangle([W / 2 + 26, dy, W - MARGIN, dy + 1], fill=BLACK)
    draw.polygon([(W / 2, dy - 7), (W / 2 + 8, dy), (W / 2, dy + 8), (W / 2 - 8, dy)], fill=YELLOW)

    # Lunar date
    draw_text(img, (W / 2, 682), hl["lunar_md"], serif(54, 600), BLACK, anchor="mm", tracking=6)
    sub = f"{hl['ganzhi_year']}{hl['zodiac']}年  {hl['ganzhi_month']}月  {hl['ganzhi_day']}日"
    draw_text(img, (W / 2, 738), sub, serif(22, 400), BLACK, anchor="mm", tracking=2)

    # Festival (red tag) or solar term (yellow tag)
    line3, tag_bg, tag_fg = None, YELLOW, BLACK
    if hl["jieqi_today"]:
        line3 = hl["jieqi_today"]
    if hl["festivals"]:
        line3, tag_bg, tag_fg = hl["festivals"][0], RED, WHITE
    if line3:
        f_tag = serif(24, 600)
        tw = text_width(line3, f_tag, tracking=4)
        pad = 14
        y0, y1 = 766, 806
        draw.rectangle([W / 2 - tw / 2 - pad, y0, W / 2 + tw / 2 + pad, y1], fill=tag_bg)
        draw_text(img, (W / 2, (y0 + y1) / 2 + 1), line3, f_tag, tag_fg, anchor="mm", tracking=4)
    elif hl["jieqi_line"]:
        draw_text(img, (W / 2, 784), hl["jieqi_line"], serif(20, 400), BLACK, anchor="mm", tracking=3)

    # Yi and Ji rows
    f_chip = sans_sc(21, 700)
    f_items = serif(21, 400)
    rows = [("宜", hl["yi"], RED), ("忌", hl["ji"], BLACK)]
    for i, (label, items, color) in enumerate(rows):
        ry = 832 + i * 46
        chip = 30
        draw.rectangle([MARGIN, ry, MARGIN + chip, ry + chip], fill=color)
        draw_text(img, (MARGIN + chip / 2, ry + chip / 2 + 1), label, f_chip, WHITE, anchor="mm")
        draw_text(img, (MARGIN + chip + 18, ry + chip / 2 + 1), "  ".join(items), f_items, BLACK, anchor="lm")

    # Footer
    fy = 936
    draw_text(img, (MARGIN, fy), f"{hl['chong']} {hl['sha']}", serif(16, 400), BLACK, anchor="lm")
    draw_text(img, (W - MARGIN, fy), hl["nayin"], serif(16, 400), BLACK, anchor="rm")
    return img


def pack(img):
    """Portrait 640x960 to native 960x640 2bpp stream."""
    native = img.transpose(Image.ROTATE_90 if FPC_AT_BOTTOM else Image.ROTATE_270)
    px = native.load()
    data = bytearray(960 * 640 // 4)
    i = 0
    for y in range(640):
        for x in range(0, 960, 4):
            b = 0
            for k in range(4):
                p = px[x + k, y]
                if p not in PALETTE:
                    raise ValueError(f"non palette pixel {p} at {x + k},{y}")
                b = (b << 2) | PALETTE[p]
            data[i] = b
            i += 1
    return bytes(data)


def main():
    if len(sys.argv) > 1:
        d = date.fromisoformat(sys.argv[1])
    else:
        d = datetime.now(ZoneInfo(TIMEZONE)).date()
    img = render(d)
    OUT.mkdir(exist_ok=True)
    img.save(OUT / "preview.png")
    (OUT / "tylendar.bin").write_bytes(pack(img))
    print(f"rendered {d} -> {OUT / 'tylendar.bin'} ({(OUT / 'tylendar.bin').stat().st_size} bytes)")


if __name__ == "__main__":
    main()

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
from opencc import OpenCC

# lunar_python emits simplified Chinese; the calendar renders in
# traditional with Hong Kong conventions. The zodiac clash character is
# fixed up because almanacs write it 沖, not OpenCC's standalone 衝.
CC = OpenCC("s2hk")


def to_traditional(value):
    if isinstance(value, str):
        return CC.convert(value).replace("衝", "沖")
    if isinstance(value, list):
        return [to_traditional(v) for v in value]
    return value

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
MONTH_ABBR = ["Jan", "Feb", "Mar", "Apr", "May", "Jun",
              "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"]
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
    data = {
        "solar": solar,
        "day": d.day,
        "month_abbr": MONTH_ABBR[d.month - 1],
        "year": d.year,
        "weekday_cn": "星期" + solar.getWeekInChinese(),
        "weekday_en": WEEKDAY_EN[d.weekday()].capitalize(),
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
    return {k: to_traditional(v) for k, v in data.items()}


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
    left, right = 120, W - 40

    # Header: Aug 2026 left, horizontal ganzhi seal right
    draw_text(img, (left, 78), f"{hl['month_abbr']} {hl['year']}", latin(48, 800), BLACK, anchor="lm")
    sw, sh = 88, 40
    draw.rectangle([right - sw, 50, right, 50 + sh], fill=RED)
    draw_text(img, (right - sw / 2, 50 + sh / 2 + 1), hl["ganzhi_year"], serif(26, 600), WHITE,
              anchor="mm", tracking=6)

    # Hairline under header
    draw.rectangle([left, 118, right, 119], fill=BLACK)

    # The day: huge, flush left
    size = 430
    f_day = latin(size, 200, opsz=144)
    avail = right - left + 10
    w = text_width(str(hl["day"]), f_day)
    if w > avail:
        size = int(size * avail / w)
        f_day = latin(size, 200, opsz=144)
    draw_text(img, (left - 6, 448), str(hl["day"]), f_day, accent, anchor="ls")

    # Hairline above the weekday row
    draw.rectangle([left, 552, right, 553], fill=BLACK)

    # Weekday row: English left in accent color, Chinese right
    draw_text(img, (left, 600), hl["weekday_en"], latin(46, 800), accent, anchor="lm")
    draw_text(img, (right, 600), hl["weekday_cn"], serif(28, 500), BLACK, anchor="rm", tracking=8)

    # Lunar date and ganzhi pillars
    draw_text(img, (left, 690), hl["lunar_md"], serif(58, 600), BLACK, anchor="lm", tracking=6)
    sub = f"{hl['ganzhi_year']}{hl['zodiac']}年  {hl['ganzhi_month']}月  {hl['ganzhi_day']}日"
    draw_text(img, (left, 758), sub, serif(25, 400), BLACK, anchor="lm", tracking=2)

    # Festival (red tag) or solar term (yellow tag) or day count line
    line3, tag_bg, tag_fg = None, YELLOW, BLACK
    if hl["jieqi_today"]:
        line3 = hl["jieqi_today"]
    if hl["festivals"]:
        line3, tag_bg, tag_fg = hl["festivals"][0], RED, WHITE
    if line3:
        f_tag = serif(24, 600)
        tw = text_width(line3, f_tag, tracking=4)
        pad = 12
        y0, y1 = 788, 826
        draw.rectangle([left, y0, left + tw + 2 * pad, y1], fill=tag_bg)
        draw_text(img, (left + pad, (y0 + y1) / 2 + 1), line3, f_tag, tag_fg, anchor="lm", tracking=4)
    elif hl["jieqi_line"]:
        draw_text(img, (left, 807), hl["jieqi_line"], serif(24, 400), BLACK, anchor="lm", tracking=3)

    # Yi and Ji rows left, chong sha and nayin right
    f_chip = sans_sc(21, 700)
    f_items = serif(23, 400)
    rows = [("宜", hl["yi"], RED, f"{hl['chong']} {hl['sha']}"),
            ("忌", hl["ji"], BLACK, hl["nayin"])]
    for i, (label, items, color, aside) in enumerate(rows):
        ry = 862 + i * 56
        chip = 32
        draw.rectangle([left, ry, left + chip, ry + chip], fill=color)
        draw_text(img, (left + chip / 2, ry + chip / 2 + 1), label, f_chip, WHITE, anchor="mm")
        draw_text(img, (left + chip + 18, ry + chip / 2 + 1), "  ".join(items), f_items, BLACK, anchor="lm")
        draw_text(img, (right, ry + chip / 2 + 1), aside, serif(18, 400), BLACK, anchor="rm")
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

"""Quote page: one dry remark a day (yi ri yi feng).

A single public-domain sarcastic quote set large in the Latin face on
an otherwise empty sheet: black on white, wide margins, the red seal
the only color. Picked by day ordinal like the poems and jokes, so it
changes daily and never repeats two days in a row. No sub-setting.
"""

import json
from pathlib import Path

from PIL import Image, ImageDraw

from generate import (W, H, BLACK, WHITE, RED, draw_text, latin, serif,
                      text_width)

QUOTES = json.loads((Path(__file__).resolve().parent.parent / "data" /
                     "quotes.json").read_text(encoding="utf-8"))

LEFT, RIGHT = 60, W - 60
RULE, FOOT = 886, 932   # footer hairline and baseline, joke-page grid
SEAL = 64
ZONE_TOP, ZONE_BOT = 150, 740   # the quote block centers between these
MARK_ALLOC = 90                 # room reserved above the block for the mark


def wrap(text, font, max_w):
    words = text.split()
    lines, line = [], words[0]
    for w in words[1:]:
        if text_width(line + " " + w, font) <= max_w:
            line += " " + w
        else:
            lines.append(line)
            line = w
    lines.append(line)
    return lines


def fit_quote(text):
    """Largest Latin size (floor 24) whose wrapped block fits the zone."""
    size = 52
    while True:
        font = latin(size, 450)
        lines = wrap(text, font, RIGHT - LEFT)
        leading = round(size * 1.42)
        budget = ZONE_BOT - ZONE_TOP - MARK_ALLOC
        if (len(lines) - 1) * leading + size <= budget or size <= 24:
            return lines, font, leading
        size -= 2


def render(d, hl, settings):
    idx = d.toordinal() % len(QUOTES)
    q = QUOTES[idx]
    img = Image.new("RGB", (W, H), WHITE)
    draw = ImageDraw.Draw(img)

    # Header: series title left, date right, hairline under.
    draw_text(img, (LEFT, 78), "一日一諷", serif(28, 600), BLACK,
              anchor="lm", tracking=8)
    when = f"{hl['day']} {hl['month_abbr'].upper()}"
    draw_text(img, (RIGHT, 80), when, latin(30, 650), BLACK,
              anchor="rm", tracking=2)
    draw.rectangle([LEFT, 118, RIGHT, 119], fill=BLACK)

    # The quote, wrapped and vertically centered. Canela has no ASCII
    # apostrophe, only U+2019; data stays ASCII.
    lines, font, leading = fit_quote(q["text"].replace("'", "\u2019"))
    span = (len(lines) - 1) * leading
    y0 = ZONE_TOP + MARK_ALLOC + (ZONE_BOT - ZONE_TOP - MARK_ALLOC - span) / 2
    # Oversized opening mark hanging above the block, the one flourish.
    # Its ink sits far above its baseline, hence the odd-looking +28.
    draw_text(img, (LEFT - 8, y0 + 28), "\u201c",
              latin(130, 250), BLACK, anchor="ls")
    for k, line in enumerate(lines):
        draw_text(img, (LEFT, y0 + k * leading), line, font, BLACK,
                  anchor="lm")

    # Attribution: short rule, name in caps, then the dates.
    ay = y0 + span + leading * 0.5 + 56
    draw.rectangle([LEFT, ay - 7, LEFT + 30, ay - 5], fill=BLACK)
    name = q["author"].upper()
    f_name = latin(18, 800)
    draw_text(img, (LEFT + 46, ay), name, f_name, BLACK, anchor="ls", tracking=3)
    draw_text(img, (LEFT + 46 + text_width(name, f_name, 3) + 18, ay),
              q["dates"], latin(16, 700), BLACK, anchor="ls", tracking=1)

    # The red moment: a small seal above the footer rule.
    sx0, sy0 = RIGHT - SEAL, RULE - 26 - SEAL
    draw.rectangle([sx0, sy0, sx0 + SEAL, sy0 + SEAL], fill=RED)
    draw_text(img, (sx0 + SEAL / 2, sy0 + 19), "泰", serif(26, 600), WHITE, anchor="mm")
    draw_text(img, (sx0 + SEAL / 2, sy0 + 46), "曆", serif(26, 600), WHITE, anchor="mm")

    # Footer: lunar date left, quote number right.
    draw.rectangle([LEFT, RULE, RIGHT, RULE + 1], fill=BLACK)
    draw_text(img, (LEFT, FOOT), hl["lunar_md"], serif(22, 500), BLACK,
              anchor="ls", tracking=2)
    en = f"NO. {idx + 1} OF {len(QUOTES)}"
    draw_text(img, (RIGHT, FOOT), en, latin(16, 700), BLACK, anchor="rs", tracking=2)
    return img

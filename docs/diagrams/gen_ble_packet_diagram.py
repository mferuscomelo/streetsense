#!/usr/bin/env python3
"""Generates ble-packet-layout.excalidraw.png: a byte map of the BLE wire
format described in prose in the README's Communication protocol section.

Same approach as gen_diagram.py: drawn programmatically with Pillow so it's
editable as code and matches the architecture diagram's visual style
(Excalidraw's default palette, Arial, rounded boxes).

Run after changing this file:

    python3 docs/diagrams/gen_ble_packet_diagram.py
"""
import math
import os
import subprocess
import tempfile

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ICONS = os.path.join(ROOT, "docs", "icons")
OUT = os.path.join(ROOT, "docs", "diagrams", "ble-packet-layout.excalidraw.png")

BLACK = (30, 30, 30)
BLUE = (25, 113, 194)
GRAY = (134, 142, 150)
DGRAY = (73, 80, 87)
WHITE = (255, 255, 255)
FILL_ORANGE = (255, 243, 224)
FILL_BLUE = (231, 245, 255)
FILL_GRAYBOX = (241, 243, 245)
FILL_GREEN = (235, 251, 236)
BORDER_GREEN = (47, 158, 68)

FONT_DIR = "/usr/share/fonts/truetype/msttcorefonts"
F_BOLD = os.path.join(FONT_DIR, "Arial_Bold.ttf")
F_REG = os.path.join(FONT_DIR, "Arial.ttf")

SCALE = 3


def font(path, size):
    return ImageFont.truetype(path, size * SCALE)


def rasterize_icon(name, px, color):
    svg = os.path.join(ICONS, f"{name}-light-full.svg")
    with tempfile.NamedTemporaryFile(suffix=".png", delete=False) as f:
        tmp = f.name
    try:
        subprocess.run(
            ["convert", "-background", "none", "-density", "384",
             "-resize", f"{px}x{px}", svg, tmp],
            check=True, capture_output=True,
        )
        im = Image.open(tmp).convert("RGBA")
    finally:
        os.unlink(tmp)
    arr = np.array(im)
    out = np.zeros_like(arr)
    out[..., 0] = color[0]
    out[..., 1] = color[1]
    out[..., 2] = color[2]
    out[..., 3] = arr[..., 3]
    return Image.fromarray(out, "RGBA")


def centered_text(draw, cx, y, text, f, fill):
    w = draw.textlength(text, font=f)
    draw.text((cx - w / 2, y), text, font=f, fill=fill)


def left_text(draw, x, y, text, f, fill):
    draw.text((x, y), text, font=f, fill=fill)


# (short label, byte width, type, scale note, fill, border)
FIELDS = [
    [
        ("VER", 1, "u8", "", WHITE, BLACK),
        ("FLAGS", 1, "u8", "", WHITE, BLACK),
        ("SEQ", 2, "u16", "", WHITE, BLACK),
        ("PM1", 2, "u16", "÷10", FILL_ORANGE, (237, 139, 0)),
        ("PM2.5", 2, "u16", "÷10", FILL_ORANGE, (237, 139, 0)),
        ("PM4", 2, "u16", "÷10", FILL_ORANGE, (237, 139, 0)),
        ("PM10", 2, "u16", "÷10", FILL_ORANGE, (237, 139, 0)),
        ("VOC", 2, "u16", "÷10", FILL_BLUE, BLUE),
    ],
    [
        ("TEMP", 2, "i16", "÷100", FILL_BLUE, BLUE),
        ("HUM", 2, "u16", "÷100", FILL_BLUE, BLUE),
        ("NOISE", 2, "u16", "÷10", FILL_BLUE, BLUE),
        ("BATT mV", 2, "u16", "", FILL_GREEN, BORDER_GREEN),
        ("BATT %", 2, "u16", "÷10", FILL_GREEN, BORDER_GREEN),
        ("BATT rate", 2, "i16", "÷10", FILL_GREEN, BORDER_GREEN),
    ],
]


def build():
    BYTE_W = 62
    BOX_H = 88
    MARGIN = 40
    ROW_GAP = 56

    row_widths = [sum(w for _, w, *_ in row) * BYTE_W for row in FIELDS]
    W = max(row_widths) + MARGIN * 2
    H = 555
    canvas = Image.new("RGBA", (W * SCALE, H * SCALE), WHITE + (255,))
    draw = ImageDraw.Draw(canvas)

    def S(v):
        return int(v * SCALE)

    # title
    paste_y = S(28)
    icon = rasterize_icon("bluetooth", 30 * SCALE, BLUE)
    canvas.alpha_composite(icon, (S(MARGIN), paste_y))
    draw.text((S(MARGIN) + S(38), paste_y + S(4)), "BLE PACKET LAYOUT",
              font=font(F_BOLD, 20), fill=BLACK)
    draw.text((S(MARGIN) + S(38), paste_y + S(30)),
              "Notify characteristic payload, sent once a second · 26 bytes · little endian",
              font=font(F_REG, 12), fill=DGRAY)

    y = S(100)
    offset = 0
    for row in FIELDS:
        x = S(MARGIN)
        row_y0 = y
        row_y1 = y + S(BOX_H)
        for label, nbytes, dtype, scale, fill, border in row:
            box_w = S(nbytes * BYTE_W)
            box = (x, row_y0, x + box_w, row_y1)
            draw.rounded_rectangle(box, radius=S(6), fill=fill, outline=border, width=S(2))
            cx = (box[0] + box[2]) // 2
            lf = font(F_BOLD, 12 if nbytes > 1 else 10)
            centered_text(draw, cx, row_y0 + S(14), label, lf, BLACK)
            sub = dtype + (" " + scale if scale else "")
            centered_text(draw, cx, row_y0 + S(38), sub, font(F_REG, 9), DGRAY)
            # offset tick at the left edge of this box
            draw.line([(x, row_y1), (x, row_y1 + S(6))], fill=GRAY, width=S(1))
            centered_text(draw, x, row_y1 + S(10), str(offset), font(F_REG, 10), GRAY)
            offset += nbytes
            x += box_w
        # closing tick at the right edge of the row
        draw.line([(x, row_y1), (x, row_y1 + S(6))], fill=GRAY, width=S(1))
        centered_text(draw, x, row_y1 + S(10), str(offset), font(F_REG, 10), GRAY)
        y = row_y1 + S(ROW_GAP)

    # legend
    ly = y + S(6)
    draw.line([(S(MARGIN), ly), (W * SCALE - S(MARGIN), ly)], fill=FILL_GRAYBOX, width=S(2))
    ly += S(16)
    draw.text((S(MARGIN), ly), "u8 / u16 / i16 = 1 or 2 byte field, i = signed",
              font=font(F_REG, 11), fill=DGRAY)
    ly += S(20)
    draw.text((S(MARGIN), ly), "÷10 / ÷100 = divide the raw integer by this to get the real value",
              font=font(F_REG, 11), fill=DGRAY)
    ly += S(20)
    draw.text((S(MARGIN), ly), "an earlier, shorter 20 byte version (no battery fields) is still accepted for backward compatibility",
              font=font(F_REG, 11), fill=DGRAY)

    # swatch legend
    ly += S(34)
    swatches = [
        (FILL_ORANGE, (237, 139, 0), "particulate matter"),
        (FILL_BLUE, BLUE, "gas / climate / noise"),
        (FILL_GREEN, BORDER_GREEN, "battery"),
        (WHITE, BLACK, "packet header"),
    ]
    sx = S(MARGIN)
    for fill, border, label in swatches:
        draw.rounded_rectangle((sx, ly, sx + S(18), ly + S(18)), radius=S(3), fill=fill, outline=border, width=S(2))
        draw.text((sx + S(24), ly + S(1)), label, font=font(F_REG, 11), fill=DGRAY)
        sx += S(24) + int(draw.textlength(label, font=font(F_REG, 11))) + S(24)

    canvas = canvas.resize((W, H), Image.LANCZOS)
    canvas.convert("RGB").save(OUT, optimize=True)
    print(f"wrote {OUT} ({W}x{H})")


if __name__ == "__main__":
    build()

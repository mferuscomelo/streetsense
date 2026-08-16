#!/usr/bin/env python3
"""Regenerates architecture-diagram.excalidraw.png from docs/icons/*.svg.

Not a hand-drawn Excalidraw file — this script draws the diagram
programmatically so the sensor-node hardware breakout can be edited as code
instead of by hand. Run after changing this file:

    python3 docs/diagrams/gen_diagram.py

Requires Pillow, numpy, and ImageMagick's `convert` (for SVG rasterization).
"""
import os
import subprocess
import tempfile

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ICONS = os.path.join(ROOT, "docs", "icons")
OUT = os.path.join(ROOT, "docs", "diagrams", "architecture-diagram.excalidraw.png")

# Excalidraw's default palette, sampled from the original diagram.
BLACK = (30, 30, 30)
BLUE = (25, 113, 194)
GRAY = (134, 142, 150)
DGRAY = (73, 80, 87)
WHITE = (255, 255, 255)
FILL_ORANGE = (255, 243, 224)
FILL_BLUE = (231, 245, 255)
FILL_GRAYBG = (248, 249, 250)
FILL_GRAYBOX = (241, 243, 245)

FONT_DIR = "/usr/share/fonts/truetype/msttcorefonts"
F_BOLD = os.path.join(FONT_DIR, "Arial_Bold.ttf")
F_REG = os.path.join(FONT_DIR, "Arial.ttf")
F_ITAL = os.path.join(FONT_DIR, "Arial_Italic.ttf")

SCALE = 3  # supersample, then downscale for antialiasing


def font(path, size):
    return ImageFont.truetype(path, size * SCALE)


def rasterize_icon(name, px, color):
    """Rasterize docs/icons/<name>-light-full.svg to `px` size, recolored."""
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


class Placeholder:
    """Drawn in place of an icon that hasn't been downloaded yet."""

    def __init__(self, label):
        self.label = label


def paste_icon(canvas, icon_or_placeholder, cx, cy, px, color, draw):
    if isinstance(icon_or_placeholder, Placeholder):
        # dashed circle = "icon not downloaded yet", title text below still names the part
        r = px // 3
        dash, gap = 6 * SCALE, 5 * SCALE
        import math
        circumference = 2 * math.pi * r
        n = int(circumference // (dash + gap))
        for i in range(n):
            a0 = 2 * math.pi * (i * (dash + gap)) / circumference
            a1 = a0 + (dash / r)
            draw.arc([cx - r, cy - r, cx + r, cy + r], math.degrees(a0), math.degrees(a1),
                     fill=color, width=2 * SCALE)
        return
    icon = rasterize_icon(icon_or_placeholder, px, color)
    canvas.alpha_composite(icon, (cx - icon.width // 2, cy - icon.height // 2))


def rounded_box(draw, box, fill, outline, dashed=False, width=2, radius=18):
    x0, y0, x1, y1 = box
    if not dashed:
        draw.rounded_rectangle(box, radius=radius * SCALE, fill=fill, outline=outline, width=width * SCALE)
        return
    # dashed rounded rect: draw solid fill, then a dashed border by segments
    draw.rounded_rectangle(box, radius=radius * SCALE, fill=fill)
    dash, gap = 10 * SCALE, 7 * SCALE
    perim_pts = []
    # approximate with straight segments on the four sides (radius ignored for dashing)
    steps = [
        ((x0, y0), (x1, y0)),
        ((x1, y0), (x1, y1)),
        ((x1, y1), (x0, y1)),
        ((x0, y1), (x0, y0)),
    ]
    for (sx, sy), (ex, ey) in steps:
        length = ((ex - sx) ** 2 + (ey - sy) ** 2) ** 0.5
        n = max(1, int(length // (dash + gap)))
        for i in range(n + 1):
            t0 = (i * (dash + gap)) / length
            t1 = min(1.0, t0 + dash / length)
            if t0 >= 1.0:
                break
            px0 = sx + (ex - sx) * t0
            py0 = sy + (ey - sy) * t0
            px1 = sx + (ex - sx) * t1
            py1 = sy + (ey - sy) * t1
            draw.line([(px0, py0), (px1, py1)], fill=outline, width=width * SCALE)


def centered_text(draw, cx, y, text, f, fill):
    w = draw.textlength(text, font=f)
    draw.text((cx - w / 2, y), text, font=f, fill=fill)
    return draw.textbbox((cx - w / 2, y), text, font=f)


def node_box(canvas, draw, box, icon, icon_color, title, subtitle_lines,
             fill, border, title_color=BLACK, sub_color=DGRAY, dashed=False):
    x0, y0, x1, y1 = box
    cx = (x0 + x1) // 2
    rounded_box(draw, box, fill, border, dashed=dashed)
    icon_cy = y0 + int((y1 - y0) * 0.32)
    paste_icon(canvas, icon, cx, icon_cy, 44 * SCALE, icon_color, draw)
    title_f = font(F_BOLD, 17)
    title_y = y0 + int((y1 - y0) * 0.56)
    centered_text(draw, cx, title_y, title, title_f, title_color)
    sub_f = font(F_REG, 11)
    sub_y = title_y + 26 * SCALE
    for line in subtitle_lines:
        centered_text(draw, cx, sub_y, line, sub_f, sub_color)
        sub_y += 15 * SCALE


def arrow(draw, p0, p1, color=BLACK, width=2, dashed=False, head=12):
    x0, y0 = p0
    x1, y1 = p1
    if dashed:
        length = ((x1 - x0) ** 2 + (y1 - y0) ** 2) ** 0.5
        dash, gap = 9 * SCALE, 6 * SCALE
        n = max(1, int(length // (dash + gap)))
        for i in range(n + 1):
            t0 = (i * (dash + gap)) / length
            t1 = min(1.0, t0 + dash / length)
            if t0 >= 1.0:
                break
            draw.line([
                (x0 + (x1 - x0) * t0, y0 + (y1 - y0) * t0),
                (x0 + (x1 - x0) * t1, y0 + (y1 - y0) * t1),
            ], fill=color, width=width * SCALE)
    else:
        draw.line([p0, p1], fill=color, width=width * SCALE)
    # open arrowhead (excalidraw style: two strokes, not a filled triangle)
    import math
    ang = math.atan2(y1 - y0, x1 - x0)
    for da in (0.5, -0.5):
        hx = x1 - head * SCALE * math.cos(ang - da)
        hy = y1 - head * SCALE * math.sin(ang - da)
        draw.line([(x1, y1), (hx, hy)], fill=color, width=width * SCALE)


def build(sensor_icons):
    """sensor_icons: dict of 'sen54'/'mic'/'battery' -> icon name or Placeholder"""
    W, H = 1331, 812
    canvas = Image.new("RGBA", (W * SCALE, H * SCALE), WHITE + (255,))
    draw = ImageDraw.Draw(canvas)

    Y_SHIFT = 210  # push the original diagram down to make room for the breakout

    def S(v):
        return v * SCALE

    def box(x0, y0, x1, y1):
        return (S(x0), S(y0 + Y_SHIFT), S(x1), S(y1 + Y_SHIFT))

    # ---- original four nodes (unchanged layout, shifted down) ----
    sensor_box = box(11, 425, 228, 589)
    phone_box = box(424, 426, 640, 590)
    cloud_box = box(718, 8, 1321, 309)
    backend_box = box(760, 101, 976, 266)
    db_box = box(1052, 101, 1272, 270)
    dash_box = box(965, 425, 1181, 590)

    node_box(canvas, draw, sensor_box, "microchip", BLACK, "SENSOR NODE",
              ["Measures air quality", "and noise"], FILL_ORANGE, BLACK)
    node_box(canvas, draw, phone_box, "mobile", BLACK, "PHONE",
              ["Records your session", "and your location"], FILL_BLUE, BLACK)

    rounded_box(draw, cloud_box, FILL_GRAYBG, BLUE, dashed=True, width=2, radius=24)
    paste_icon(canvas, "cloud", S(760) - S(718) + cloud_box[0], cloud_box[1] + S(30),
               22 * SCALE, BLUE, draw)
    draw.text((cloud_box[0] + S(60), cloud_box[1] + S(18)), "CLOUD",
               font=font(F_BOLD, 15), fill=BLUE)

    node_box(canvas, draw, backend_box, "server", BLACK, "BACKEND",
              ["Calculates dose and", "finds unusual events"], WHITE, BLACK)
    node_box(canvas, draw, db_box, "database", GRAY, "DATABASE",
              ["Stores past readings", "(planned, not built yet)"], FILL_GRAYBOX, GRAY,
              title_color=GRAY, sub_color=GRAY, dashed=True)
    node_box(canvas, draw, dash_box, "map-location-dot", BLACK, "DASHBOARD",
              ["Shows every grid cell", "on a live map"], WHITE, BLACK)

    # arrows
    def cy(b):
        return (b[1] + b[3]) // 2

    arrow(draw, (sensor_box[2], cy(sensor_box)), (phone_box[0] - S(4), cy(phone_box)))
    paste_icon(canvas, "bluetooth", (sensor_box[2] + phone_box[0]) // 2, cy(sensor_box) - S(28), 20 * SCALE, BLACK, draw)
    centered_text(draw, (sensor_box[2] + phone_box[0]) // 2, cy(sensor_box) - S(6), "Bluetooth", font(F_REG, 11), BLACK)

    upload_start = ((phone_box[0] + phone_box[2]) // 2, phone_box[1])
    upload_end = (backend_box[0] - S(30), backend_box[1] + S(50))
    arrow(draw, upload_start, upload_end)
    umx = (upload_start[0] + upload_end[0]) // 2 - S(45)
    umy = (upload_start[1] + upload_end[1]) // 2
    paste_icon(canvas, "arrow-up-from-bracket", umx, umy - S(14), 18 * SCALE, BLACK, draw)
    centered_text(draw, umx, umy + S(6), "Upload", font(F_REG, 11), BLACK)

    arrow(draw, (backend_box[2], cy(backend_box)), (db_box[0] - S(4), cy(db_box)), dashed=True)
    arrow(draw, ((backend_box[0] + backend_box[2]) // 2, backend_box[3]), ((dash_box[0] + dash_box[2]) // 2 - S(30), dash_box[1] - S(4)))

    # ---- sensor-node hardware breakout (new) ----
    bx0, by0, bx1, by1 = S(11), S(20), S(700), S(196)
    rounded_box(draw, (bx0, by0, bx1, by1), FILL_GRAYBG, FILL_ORANGE if False else (237, 139, 0), dashed=True, width=2, radius=20)
    paste_icon(canvas, "microchip", bx0 + S(34), by0 + S(26), 20 * SCALE, (237, 139, 0), draw)
    draw.text((bx0 + S(58), by0 + S(15)), "SENSOR NODE HARDWARE", font=font(F_BOLD, 14), fill=(237, 139, 0))

    chips = [
        ("microchip", "nRF52840", ["MCU + BLE radio", "onboard accelerometer"]),
        (sensor_icons.get("sen54", Placeholder("SEN54")), "SEN54", ["PM · VOC · T/RH", "I2C 0x69"]),
        (sensor_icons.get("mic", Placeholder("MIC")), "PDM mic", ["Noise (onboard)", "PDM peripheral"]),
        (sensor_icons.get("battery", Placeholder("BATT")), "MAX17048", ["Battery gauge", "I2C 0x36"]),
    ]
    chip_w, chip_h, gap = S(160), S(120), S(15)
    cx0 = bx0 + S(15)
    cy0 = by0 + S(50)
    for i, (icon, title, sub) in enumerate(chips):
        cb = (cx0 + i * (chip_w + gap), cy0, cx0 + i * (chip_w + gap) + chip_w, cy0 + chip_h)
        rounded_box(draw, cb, WHITE, BLACK, width=2, radius=12)
        ccx = (cb[0] + cb[2]) // 2
        paste_icon(canvas, icon, ccx, cb[1] + S(28), 26 * SCALE, BLACK, draw)
        centered_text(draw, ccx, cb[1] + S(48), title, font(F_BOLD, 13), BLACK)
        sy = cb[1] + S(68)
        for line in sub:
            centered_text(draw, ccx, sy, line, font(F_REG, 9), DGRAY)
            sy += S(13)

    # connector from breakout down into the sensor node box — routed under the
    # first chip so it doesn't cross the SEN54/mic/battery chips or the Upload arrow
    sensor_cx = (sensor_box[0] + sensor_box[2]) // 2
    arrow(draw, (sensor_cx, by1), (sensor_cx, sensor_box[1] - S(2)), width=2)

    canvas = canvas.resize((W, H), Image.LANCZOS)
    canvas.convert("RGB").save(OUT, optimize=True)
    print(f"wrote {OUT} ({W}x{H})")


if __name__ == "__main__":
    sensor_icons = {
        "sen54": "sensor",
        "mic": "microphone",
        "battery": "battery-three-quarters",
    }
    build(sensor_icons)

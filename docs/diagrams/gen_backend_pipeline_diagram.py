#!/usr/bin/env python3
"""Generates backend-pipeline.excalidraw.png: what happens to a reading in
the backend, from decode through the Structured Concurrency fork to the
session level dose and the shared crowd layer. Violet tags mark exactly
which Java 26 feature is doing the work at each step.

Same approach as the other two generated diagrams: drawn with Pillow so
it's editable as code and matches their visual style.

Run after changing this file:

    python3 docs/diagrams/gen_backend_pipeline_diagram.py
"""
import math
import os
import subprocess
import tempfile

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ICONS = os.path.join(ROOT, "docs", "icons")
OUT = os.path.join(ROOT, "docs", "diagrams", "backend-pipeline.excalidraw.png")

BLACK = (30, 30, 30)
BLUE = (25, 113, 194)
GRAY = (134, 142, 150)
DGRAY = (73, 80, 87)
WHITE = (255, 255, 255)
FILL_GRAYBG = (248, 249, 250)
VIOLET = (108, 63, 211)

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


def wrapped_centered(draw, cx, y, text, f, fill, max_w, line_gap):
    words = text.split()
    lines, cur = [], ""
    for w in words:
        trial = (cur + " " + w).strip()
        if draw.textlength(trial, font=f) > max_w and cur:
            lines.append(cur)
            cur = w
        else:
            cur = trial
    if cur:
        lines.append(cur)
    for line in lines:
        centered_text(draw, cx, y, line, f, fill)
        y += line_gap
    return y


def badge(draw, cx, y, text, fsize=10):
    f = font(F_BOLD, fsize)
    w = draw.textlength(text, font=f)
    pad_x, pad_y = S_(8), S_(4)
    h = fsize * SCALE + 2 * pad_y
    box = (cx - w / 2 - pad_x, y, cx + w / 2 + pad_x, y + h)
    draw.rounded_rectangle(box, radius=h // 2, fill=VIOLET)
    draw.text((cx - w / 2, y + pad_y - S_(1)), text, font=f, fill=WHITE)
    return box[3]


def S_(v):
    return int(v * SCALE)


def rounded_box(draw, box, fill, outline, dashed=False, width=2, radius=14):
    x0, y0, x1, y1 = box
    if not dashed:
        draw.rounded_rectangle(box, radius=radius * SCALE, fill=fill, outline=outline, width=width * SCALE)
        return
    draw.rounded_rectangle(box, radius=radius * SCALE, fill=fill)
    dash, gap = 10 * SCALE, 7 * SCALE
    steps = [((x0, y0), (x1, y0)), ((x1, y0), (x1, y1)), ((x1, y1), (x0, y1)), ((x0, y1), (x0, y0))]
    for (sx, sy), (ex, ey) in steps:
        length = ((ex - sx) ** 2 + (ey - sy) ** 2) ** 0.5
        n = max(1, int(length // (dash + gap)))
        for i in range(n + 1):
            t0 = (i * (dash + gap)) / length
            t1 = min(1.0, t0 + dash / length)
            if t0 >= 1.0:
                break
            draw.line([
                (sx + (ex - sx) * t0, sy + (ey - sy) * t0),
                (sx + (ex - sx) * t1, sy + (ey - sy) * t1),
            ], fill=outline, width=width * SCALE)


def arrow(draw, p0, p1, color=BLACK, width=2, head=10, dashed=False):
    if dashed:
        length = ((p1[0] - p0[0]) ** 2 + (p1[1] - p0[1]) ** 2) ** 0.5
        dash, gap = 9 * SCALE, 6 * SCALE
        n = max(1, int(length // (dash + gap)))
        for i in range(n + 1):
            t0 = (i * (dash + gap)) / length
            t1 = min(1.0, t0 + dash / length)
            if t0 >= 1.0:
                break
            draw.line([
                (p0[0] + (p1[0] - p0[0]) * t0, p0[1] + (p1[1] - p0[1]) * t0),
                (p0[0] + (p1[0] - p0[0]) * t1, p0[1] + (p1[1] - p0[1]) * t1),
            ], fill=color, width=width * SCALE)
    else:
        draw.line([p0, p1], fill=color, width=width * SCALE)
    ang = math.atan2(p1[1] - p0[1], p1[0] - p0[0])
    for da in (0.5, -0.5):
        hx = p1[0] - head * SCALE * math.cos(ang - da)
        hy = p1[1] - head * SCALE * math.sin(ang - da)
        draw.line([p1, (hx, hy)], fill=color, width=width * SCALE)


def build():
    W, H = 1640, 640
    canvas = Image.new("RGBA", (W * SCALE, H * SCALE), WHITE + (255,))
    draw = ImageDraw.Draw(canvas)

    def S(v):
        return int(v * SCALE)

    # title
    icon = rasterize_icon("server", 26 * SCALE, BLACK)
    canvas.alpha_composite(icon, (S(40), S(26)))
    draw.text((S(78), S(30)), "BACKEND: WHAT HAPPENS TO A READING", font=font(F_BOLD, 19), fill=BLACK)
    draw.text((S(78), S(56)), "violet tags mark the Java 26 feature doing the work at that step",
              font=font(F_REG, 11), fill=DGRAY)

    # decode box
    dec = (S(30), S(240), S(250), S(410))
    rounded_box(draw, dec, WHITE, BLACK)
    dcx = (dec[0] + dec[2]) // 2
    centered_text(draw, dcx, dec[1] + S(14), "DECODE", font(F_BOLD, 16), BLACK)
    yy = wrapped_centered(draw, dcx, dec[1] + S(40), "reads the raw packet field by field",
                           font(F_REG, 10), DGRAY, S(190), S(14))
    yy = badge(draw, dcx, yy + S(8), "FFM API")
    badge(draw, dcx, yy + S(6), "PRIMITIVE PATTERNS")

    # structured concurrency bracket
    br = (S(320), S(95), S(1000), S(600))
    rounded_box(draw, br, FILL_GRAYBG, BLUE, dashed=True, radius=20)
    draw.text((S(342), S(112)), "STRUCTURED CONCURRENCY", font=font(F_BOLD, 14), fill=BLUE)
    draw.text((S(342), S(134)), "one unit of work: all three succeed, or all three fail, together",
              font=font(F_REG, 11), fill=BLUE)
    badge(draw, S(342) + S(46), S(560), "SCOPED VALUES", fsize=9)
    badge(draw, S(342) + S(200), S(560), "VIRTUAL THREADS", fsize=9)

    jobs = [
        ("SAVE", "stores the reading as-is", None),
        ("UPDATE BASELINE", "rolling per-cell average", "STREAM GATHERERS"),
        ("CHECK FOR ANOMALY", "traffic, smoke, solvent, or normal", "SEALED INTERFACE"),
    ]
    box_x0, box_x1 = S(360), S(960)
    ys = [S(160), S(300), S(440)]
    box_h = S(120)
    job_boxes = []
    for (title, caption, feat), y0 in zip(jobs, ys):
        box = (box_x0, y0, box_x1, y0 + box_h)
        job_boxes.append(box)
        rounded_box(draw, box, WHITE, BLACK)
        cx = (box[0] + box[2]) // 2
        centered_text(draw, cx, y0 + S(14), title, font(F_BOLD, 15), BLACK)
        yy = wrapped_centered(draw, cx, y0 + S(40), caption, font(F_REG, 10), DGRAY, S(420), S(15))
        if feat:
            badge(draw, cx, yy + S(6), feat)
        arrow(draw, (dec[2] + S(6), (dec[1] + dec[3]) // 2), (box_x0 - S(6), (box[1] + box[3]) // 2))

    # second tier: dose and crowd layer, built later from many saved readings
    draw.text((S(1040), S(112)), "LATER: PER SESSION, ACROSS CONTRIBUTORS",
              font=font(F_BOLD, 13), fill=DGRAY)
    draw.text((S(1040), S(134)), "built from many saved readings, not from one reading alone",
              font=font(F_REG, 11), fill=DGRAY)

    dose = (S(1040), S(160), S(1600), S(320))
    rounded_box(draw, dose, WHITE, BLACK)
    dcx2 = (dose[0] + dose[2]) // 2
    centered_text(draw, dcx2, dose[1] + S(18), "DOSE", font(F_BOLD, 16), BLACK)
    yy = wrapped_centered(draw, dcx2, dose[1] + S(48),
                           "a session's readings, weighted by how hard that activity makes you breathe",
                           font(F_REG, 11), DGRAY, S(480), S(16))
    badge(draw, dcx2, yy + S(10), "STREAM GATHERERS")

    crowd = (S(1040), S(400), S(1600), S(600))
    rounded_box(draw, crowd, WHITE, BLACK)
    icon2 = rasterize_icon("map-location-dot", 24 * SCALE, BLACK)
    canvas.alpha_composite(icon2, (dcx2 - icon2.width // 2, crowd[1] + S(16)))
    centered_text(draw, dcx2, crowd[1] + S(48), "CROWD LAYER", font(F_BOLD, 16), BLACK)
    wrapped_centered(draw, dcx2, crowd[1] + S(76),
                      "every contributor's readings pooled by grid cell, so one sample looks different from a dozen that agree",
                      font(F_REG, 11), DGRAY, S(480), S(16))

    save_box = job_boxes[0]
    anomaly_box = job_boxes[2]
    arrow(draw, (save_box[2] + S(4), save_box[3] - S(10)), (dose[0] - S(6), (dose[1] + dose[3]) // 2), dashed=True, color=GRAY)
    arrow(draw, (anomaly_box[2] + S(4), anomaly_box[3] - S(10)), (crowd[0] - S(6), (crowd[1] + crowd[3]) // 2), dashed=True, color=GRAY)

    canvas = canvas.resize((W, H), Image.LANCZOS)
    canvas.convert("RGB").save(OUT, optimize=True)
    print(f"wrote {OUT} ({W}x{H})")


if __name__ == "__main__":
    build()

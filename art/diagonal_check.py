"""Visual check for the diagonal rail gap fix.

Draws a diagonal line of half-track sprites twice:
  - "before": the half-tile sprite is only rotated (it reaches the edge midpoint);
  - "after": the sprite is also stretched along its local x by sqrt(2), so it
    reaches the shared tile corner.

Run with the bundled Python (Pillow):
    /Users/anton/.dsh/dsh-runtimes/dsh-primary-runtime/dependencies/python/bin/python3 \
        art/diagonal_check.py
"""

import math
import os

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SPRITES = os.path.join(ROOT, "assets", "sprites")
OUT = os.path.join(ROOT, "art", "renders", "test", "diagonal_check.png")

TILE = 32
N = 6
ZOOM = 4
SQRT2 = math.sqrt(2.0)

FONT_PATH = "/System/Library/Fonts/Supplemental/Arial.ttf"
BOLD_PATH = "/System/Library/Fonts/Supplemental/Arial Bold.ttf"
try:
    FONT = ImageFont.truetype(FONT_PATH, 15)
    FONT_BOLD = ImageFont.truetype(BOLD_PATH, 17)
    FONT_SMALL = ImageFont.truetype(FONT_PATH, 12)
except OSError:
    FONT = FONT_BOLD = FONT_SMALL = ImageFont.load_default()


def load(name):
    return Image.open(os.path.join(SPRITES, name + ".png")).convert("RGBA")


def rotated_half(half, angle, stretch):
    img = half
    if stretch:
        img = img.resize((max(1, int(round(img.width * SQRT2))), img.height), Image.BICUBIC)
    return img.rotate(angle, resample=Image.BICUBIC, expand=True)


def panel(stretch):
    ground = load("grass").resize((TILE, TILE), Image.BILINEAR)
    half = load("rail_half_e").resize((TILE, TILE), Image.BILINEAR)
    scene = Image.new("RGBA", (N * TILE, N * TILE), (0, 0, 0, 255))
    for y in range(N):
        for x in range(N):
            scene.alpha_composite(ground, (x * TILE, y * TILE))

    # Diagonal line from bottom-left (0, N-1) to top-right (N-1, 0).
    for i in range(N):
        x = i
        y = N - 1 - i
        cx = x * TILE + TILE // 2
        cy = y * TILE + TILE // 2
        if i < N - 1:
            im = rotated_half(half, 45, stretch)
            scene.alpha_composite(im, (cx - im.width // 2, cy - im.height // 2))
        if i > 0:
            im = rotated_half(half, 225, stretch)
            scene.alpha_composite(im, (cx - im.width // 2, cy - im.height // 2))

    return scene.convert("RGB").resize((N * TILE * ZOOM, N * TILE * ZOOM), Image.NEAREST)


def main():
    before = panel(False)
    after = panel(True)

    margin = 20
    label_h = 34
    note_h = 44
    width = max(before.width, after.width) + margin * 2
    height = margin + label_h + before.height + label_h + after.height + note_h
    sheet = Image.new("RGB", (width, height), (30, 32, 36))
    draw = ImageDraw.Draw(sheet)

    y = margin
    draw.text((margin, y), "Before: rotated half-tile (gap at the shared corner)",
              font=FONT_BOLD, fill=(235, 235, 235))
    y += label_h
    sheet.paste(before, (margin, y))
    y += before.height

    draw.text((margin, y), "After: diagonal halves stretched by sqrt(2) to the shared corner",
              font=FONT_BOLD, fill=(235, 235, 235))
    y += label_h
    sheet.paste(after, (margin, y))
    y += after.height + 10

    draw.text((margin, y),
              "6x6 Blender grass tiles, diagonal line, 32 px tiles shown at 4x nearest. "
              "The game now uses the 'after' scaling for d=1,3,5,7.",
              font=FONT_SMALL, fill=(170, 172, 178))

    sheet.save(OUT)
    print("WROTE", OUT, sheet.size)


if __name__ == "__main__":
    main()

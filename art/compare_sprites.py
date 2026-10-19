"""Build a side-by-side comparison of the current sprites and the Blender test set.

Run with the bundled Python that has Pillow:

    /Users/anton/.dsh/dsh-runtimes/dsh-primary-runtime/dependencies/python/bin/python3 \
        art/compare_sprites.py

Output: art/renders/test/compare.png
"""

import os

from PIL import Image, ImageChops, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CURRENT = os.path.join(ROOT, "assets", "sprites")
NEW = os.path.join(ROOT, "art", "renders", "test")
OUT = os.path.join(NEW, "compare.png")

ASSETS = [
    ("grass", "Grass"),
    ("trees", "Forest"),
    ("water", "Water"),
    ("rocks", "Rocks"),
    ("rail_half_e", "Track (half)"),
    ("loco_ew", "Locomotive"),
    ("car_ew", "Carriage"),
    ("town", "Town"),
]
TERRAIN = {"grass", "trees", "water", "rocks"}
TINTED = {"loco_ew", "car_ew", "town"}
TINT = (220, 60, 50, 255)

GAME = 32
CELL = 128
LABEL_W = 120
GAP = 12
MARGIN = 16
HEADER_H = 42
ROW_H = CELL + 30
NOTE_H = 64

FONT_PATH = "/System/Library/Fonts/Supplemental/Arial.ttf"
BOLD_PATH = "/System/Library/Fonts/Supplemental/Arial Bold.ttf"
try:
    FONT = ImageFont.truetype(FONT_PATH, 15)
    FONT_SMALL = ImageFont.truetype(FONT_PATH, 12)
    FONT_BOLD = ImageFont.truetype(BOLD_PATH, 15)
except OSError:
    FONT = FONT_SMALL = FONT_BOLD = ImageFont.load_default()


def checker(size):
    img = Image.new("RGB", (size, size), (58, 60, 64))
    draw = ImageDraw.Draw(img)
    step = 16
    for y in range(0, size, step):
        for x in range(0, size, step):
            if (x // step + y // step) % 2 == 0:
                draw.rectangle([x, y, x + step - 1, y + step - 1], fill=(86, 88, 92))
    return img.convert("RGBA")


def over(img, background):
    out = background.copy()
    out.alpha_composite(img)
    return out


def game_size(img, size=GAME):
    """Simulate the game: source -> size px (bilinear), then nearest for inspection."""
    return img.resize((size, size), Image.BILINEAR).resize((CELL, CELL), Image.NEAREST)


def source_size(img):
    return img.resize((CELL, CELL), Image.LANCZOS)


def tinted(img):
    return ImageChops.multiply(img, Image.new("RGBA", img.size, TINT))


def load(path):
    return Image.open(path).convert("RGBA")


def main():
    width = MARGIN * 2 + LABEL_W + GAP + CELL * 4 + GAP * 3
    height = MARGIN + HEADER_H + ROW_H * len(ASSETS) + NOTE_H + MARGIN
    sheet = Image.new("RGB", (width, height), (30, 32, 36))
    draw = ImageDraw.Draw(sheet)

    # Header
    draw.text((MARGIN, MARGIN + 10), "Asset", font=FONT_BOLD, fill=(235, 235, 235))
    x0 = MARGIN + LABEL_W + GAP
    headers = ["Current (32 px)", "Blender (32 px)", "Blender (64 px)", "Blender source"]
    for i, text in enumerate(headers):
        cls = FONT
        # Arial Bold only for the header
        try:
            cls = ImageFont.truetype(BOLD_PATH, 13)
        except OSError:
            pass
        draw.text((x0 + i * (CELL + GAP), MARGIN + 10), text, font=cls, fill=(235, 235, 235))

    board = checker(CELL)

    for row, (name, label) in enumerate(ASSETS):
        y = MARGIN + HEADER_H + row * ROW_H
        draw.text((MARGIN, y + CELL // 2 - 8), label, font=FONT, fill=(220, 220, 220))

        cur = load(os.path.join(CURRENT, name + ".png"))
        new = load(os.path.join(NEW, name + ".png"))
        cur_game = game_size(cur)
        new_game = game_size(new)
        new_game64 = game_size(new, 64)
        new_src = source_size(new)

        if name in TINTED:
            cur_game = tinted(cur_game)
            new_game = tinted(new_game)
            new_game64 = tinted(new_game64)

        if name in TERRAIN:
            cur_cell = cur_game
            new_cell = new_game
            new64_cell = new_game64
        else:
            cur_cell = over(cur_game, board)
            new_cell = over(new_game, board)
            new64_cell = over(new_game64, board)
        src_cell = over(new_src, board)

        for i, cell in enumerate((cur_cell, new_cell, new64_cell, src_cell)):
            x = x0 + i * (CELL + GAP)
            sheet.paste(cell.convert("RGB"), (x, y))
            draw.rectangle([x - 1, y - 1, x + CELL, y + CELL], outline=(100, 102, 108))

        # A small tag under the in-game previews when a tint is applied.
        if name in TINTED:
            draw.text((x0, y + CELL + 4), "tinted red to match in-game town colour",
                      font=FONT_SMALL, fill=(150, 152, 158))

    note_y = MARGIN + HEADER_H + ROW_H * len(ASSETS) + 8
    draw.text((MARGIN, note_y),
              "32 px columns simulate the game: source downscaled to the 32 px tile, then 4x nearest for inspection.",
              font=FONT_SMALL, fill=(170, 172, 178))
    draw.text((MARGIN, note_y + 18),
              "The 64 px column simulates a larger tile size / zoom; the current Config.TILE is 32 px.",
              font=FONT_SMALL, fill=(170, 172, 178))
    draw.text((MARGIN, note_y + 36),
              "Source is the 256 px Blender render. Transparent sprites use a checkerboard; loco/carriage/town are tinted red.",
              font=FONT_SMALL, fill=(170, 172, 178))

    sheet.save(OUT)
    print("WROTE", OUT, sheet.size)


if __name__ == "__main__":
    main()

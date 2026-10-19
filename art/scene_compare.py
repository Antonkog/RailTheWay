"""Render a small in-game mock-up with the current sprites vs the Blender test set.

Run with the bundled Python that has Pillow:

    /Users/anton/.dsh/dsh-runtimes/dsh-primary-runtime/dependencies/python/bin/python3 \
        art/scene_compare.py

Output: art/renders/test/scene_compare.png
"""

import os

from PIL import Image, ImageChops, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CURRENT = os.path.join(ROOT, "assets", "sprites")
NEW = os.path.join(ROOT, "art", "renders", "test")
OUT = os.path.join(NEW, "scene_compare.png")

TILE = 32
ZOOM = 4
COLS, ROWS = 6, 4
TINT = (220, 60, 50, 255)

FONT_PATH = "/System/Library/Fonts/Supplemental/Arial.ttf"
BOLD_PATH = "/System/Library/Fonts/Supplemental/Arial Bold.ttf"
try:
    FONT = ImageFont.truetype(FONT_PATH, 16)
    FONT_BOLD = ImageFont.truetype(BOLD_PATH, 17)
    FONT_SMALL = ImageFont.truetype(FONT_PATH, 12)
except OSError:
    FONT = FONT_BOLD = FONT_SMALL = ImageFont.load_default()

# Terrain layout (terrain name per tile), then a horizontal track at row 1.
TERRAIN = [
    ["water", "water", "grass", "trees", "grass", "rocks"],
    ["grass", "grass", "grass", "grass", "grass", "grass"],
    ["grass", "rocks", "grass", "water", "trees", "grass"],
    ["trees", "grass", "rocks", "grass", "water", "trees"],
]
TRACK_ROW = 1


def load(base, name):
    return Image.open(os.path.join(base, name + ".png")).convert("RGBA")


def at_game_size(img):
    return img.resize((TILE, TILE), Image.BILINEAR)


def tinted(img):
    return ImageChops.multiply(img, Image.new("RGBA", img.size, TINT))


def tile_scene(base):
    scene = Image.new("RGBA", (COLS * TILE, ROWS * TILE), (0, 0, 0, 255))
    # Terrain
    for y, row in enumerate(TERRAIN):
        for x, name in enumerate(row):
            scene.alpha_composite(at_game_size(load(base, name)), (x * TILE, y * TILE))

    # Track: the game draws a rotated half-sprite for every connected direction.
    half = at_game_size(load(base, "rail_half_e"))
    for x in range(COLS):
        px, py = x * TILE, TRACK_ROW * TILE
        scene.alpha_composite(half, (px, py))
        scene.alpha_composite(half.rotate(180), (px, py))

    # Town on the left end of the line, tinted like the game does.
    town = tinted(at_game_size(load(base, "town")))
    scene.alpha_composite(town, (0, TRACK_ROW * TILE))

    # Carriage + locomotive heading east, drawn at the game's 22/26 px sizes.
    car = tinted(load(base, "car_ew").resize((22, 22), Image.BILINEAR))
    loco = tinted(load(base, "loco_ew").resize((26, 26), Image.BILINEAR))
    cx = 1 * TILE + TILE // 2 - 11
    cy = TRACK_ROW * TILE + TILE // 2 - 11
    scene.alpha_composite(car, (cx, cy))
    lx = 2 * TILE + TILE // 2 - 13
    ly = TRACK_ROW * TILE + TILE // 2 - 13
    scene.alpha_composite(loco, (lx, ly))
    return scene


def panel(scene):
    return scene.convert("RGB").resize((scene.width * ZOOM, scene.height * ZOOM), Image.NEAREST)


def main():
    current = panel(tile_scene(CURRENT))
    blender = panel(tile_scene(NEW))

    margin = 20
    label_h = 34
    note_h = 52
    width = max(current.width, blender.width) + margin * 2
    height = margin + label_h + current.height + label_h + blender.height + note_h
    sheet = Image.new("RGB", (width, height), (30, 32, 36))
    draw = ImageDraw.Draw(sheet)

    y = margin
    draw.text((margin, y), "Current sprites (32 px tiles, 4x nearest)", font=FONT_BOLD, fill=(235, 235, 235))
    y += label_h
    sheet.paste(current, (margin, y))
    y += current.height

    draw.text((margin, y), "Blender test set (32 px tiles, 4x nearest)", font=FONT_BOLD, fill=(235, 235, 235))
    y += label_h
    sheet.paste(blender, (margin, y))
    y += blender.height + 10

    draw.text((margin, y),
              "Same 6x4 layout, same 32 px tile size, same red town tint. The Blender set is not installed in assets/sprites.",
              font=FONT_SMALL, fill=(170, 172, 178))
    draw.text((margin, y + 18),
              "Try the 64 px column in compare.png to see the detail the 256 px sources can provide at a larger tile/zoom.",
              font=FONT_SMALL, fill=(170, 172, 178))

    sheet.save(OUT)
    print("WROTE", OUT, sheet.size)


if __name__ == "__main__":
    main()

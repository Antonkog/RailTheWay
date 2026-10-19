"""Post-process the raw Blender renders into the final test sprites.

Run with the bundled Python that has Pillow:

    /Users/anton/.dsh/dsh-runtimes/dsh-primary-runtime/dependencies/python/bin/python3 \
        art/postprocess_sprites.py

- Adds a dark silhouette outline to loco/carriage/town/tree icons so they stay
  readable at 32 px (the current hand-drawn sprites rely on outlines).
- Composites the 3/4 pine icon over the grass tile with a soft contact shadow,
  producing trees.png (the game's forest tile).
- Copies terrain/track renders unchanged.
"""

import os

from PIL import Image, ImageChops, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RAW = os.path.join(ROOT, "art", "renders", "raw")
OUT = os.path.join(ROOT, "art", "renders", "test")
os.makedirs(OUT, exist_ok=True)


def load(name):
    return Image.open(os.path.join(RAW, name + ".png")).convert("RGBA")


def save(name, img):
    path = os.path.join(OUT, name + ".png")
    img.save(path, optimize=True)
    print("WROTE", path, img.size)


def outline(img, width=9, color=(8, 8, 10)):
    alpha = img.getchannel("A")
    grown = alpha.filter(ImageFilter.MaxFilter(width))
    border = ImageChops.subtract(grown, alpha)
    ring = Image.new("RGBA", img.size, (*color, 0))
    ring.putalpha(border)
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    out.alpha_composite(ring)
    out.alpha_composite(img)
    return out


def soft_shadow(img, offset=(-10, 10), blur=10, strength=0.30, grow=7):
    alpha = img.getchannel("A")
    alpha = alpha.filter(ImageFilter.MaxFilter(grow))
    alpha = alpha.filter(ImageFilter.GaussianBlur(blur))
    alpha = alpha.point(lambda a: int(a * strength))
    shadow = Image.new("RGBA", img.size, (0, 0, 0, 0))
    shadow.putalpha(alpha)
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    layer.paste(shadow, offset, shadow)
    return layer


def composite(base, *layers):
    out = base.copy()
    for layer in layers:
        out.alpha_composite(layer)
    return out


def main():
    # Ground/terrain and track are already tile-shaped; copy them as-is.
    for name in ("grass", "water", "rocks", "rail_half_e"):
        save(name, load(name))

    # Transparent props get a dark silhouette so the 32 px version keeps its shape.
    loco = outline(load("loco_ew"), width=9)
    save("loco_ew", loco)
    car = outline(load("car_ew"), width=9)
    save("car_ew", car)
    town = outline(load("town"), width=9)
    save("town", composite(soft_shadow(town, offset=(-10, 10), blur=12, strength=0.24, grow=9), town))

    # Forest: a 3/4 pine icon over the grass tile, with a soft contact shadow.
    grass = load("grass")
    tree = outline(load("tree_icon"), width=7)
    save("trees", composite(grass, soft_shadow(tree, offset=(-12, 12), blur=12, strength=0.24, grow=9), tree))

    print("POSTPROCESS DONE ->", OUT)


if __name__ == "__main__":
    main()

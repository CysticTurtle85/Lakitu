"""The Spiny Egg recipe picture for the Modrinth page: a crafting-table grid (vanilla GUI and item textures from the
26.3 client jar) with Egg + Cactus + Red Dye -> 2 Spiny Eggs, scaled x4 with hard pixels. Needs Pillow and NumPy.

    python art/recipe/make_recipe.py <minecraft client jar> art/recipe/spiny_egg_recipe.png
"""
import io
import sys
import zipfile
from pathlib import Path

import numpy as np
from PIL import Image

MOD = Path(__file__).resolve().parents[2]
SCALE = 4


def tex(jar, path):
    return Image.open(io.BytesIO(jar.read(path))).convert("RGBA")


def iso_block(top, side, size=16):
    """A vanilla-style inventory block: top face lit 1.0, left 0.8, right 0.6 (supersampled, then hard alpha)."""
    S = 8
    n = size * S
    acc = np.zeros((n, n, 4), float)
    ys, xs = np.mgrid[0:n, 0:n] + 0.5
    faces = [  # origin, u edge, v edge (canvas px), texture, light
        ((0, n / 4), (n / 2, -n / 4), (n / 2, n / 4), top, 1.0),
        ((0, n / 4), (n / 2, n / 4), (0, n / 2), side, 0.8),
        ((n / 2, n / 2), (n / 2, -n / 4), (0, n / 2), side, 0.6),
    ]
    for (ox, oy), (ax, ay), (bx, by), t, light in faces:
        det = ax * by - ay * bx
        px, py = xs - ox, ys - oy
        u = (px * by - py * bx) / det
        v = (ax * py - ay * px) / det
        inside = (u >= 0) & (u < 1) & (v >= 0) & (v < 1)
        tt = np.array(t).astype(float)
        c = tt[np.clip((v * 16).astype(int), 0, 15), np.clip((u * 16).astype(int), 0, 15)]
        c[..., :3] *= light
        acc[inside] = c[inside]
    small = acc.reshape(size, S, size, S, 4).mean(axis=(1, 3))
    out = small.astype(np.uint8)
    out[..., 3] = np.where(small[..., 3] > 110, 255, 0)
    return Image.fromarray(out, "RGBA")


DIGITS = {"2": ["0111", "1001", "0001", "0010", "0100", "1000", "1111"]}


def count(img, x, y, text):
    """Minecraft-style item count: white pixel digits with a dark shadow, bottom right of a slot."""
    for k, ch in enumerate(text):
        rows = DIGITS[ch]
        for j, row in enumerate(rows):
            for i, bit in enumerate(row):
                if bit == "1":
                    img.putpixel((x + k * 5 + i + 1, y + j + 1), (62, 62, 62, 255))
        for j, row in enumerate(rows):
            for i, bit in enumerate(row):
                if bit == "1":
                    img.putpixel((x + k * 5 + i, y + j), (255, 255, 255, 255))


def main(jar_path, out):
    jar = zipfile.ZipFile(jar_path)
    gui = tex(jar, "assets/minecraft/textures/gui/container/crafting_table.png")
    # The grid, arrow and result slot of the 176x166 crafting table screen.
    panel = gui.crop((22, 9, 158, 79)).copy()
    egg = tex(jar, "assets/minecraft/textures/item/egg.png")
    dye = tex(jar, "assets/minecraft/textures/item/red_dye.png")
    cactus = iso_block(tex(jar, "assets/minecraft/textures/block/cactus_top.png"),
                       tex(jar, "assets/minecraft/textures/block/cactus_side.png"))
    spiny = Image.open(MOD / "src/main/resources/assets/lakitu/textures/item/spiny_egg.png").convert("RGBA")
    # Slots: grid starts at (30, 17) on the screen, 18 px apart; result at (124, 35).
    slot = lambda col, row: (30 + col * 18 - 22, 17 + row * 18 - 9)
    for item, (col, row) in ((egg, (0, 1)), (cactus, (1, 1)), (dye, (2, 1))):
        panel.alpha_composite(item, slot(col, row))
    rx, ry = 124 - 22, 35 - 9
    panel.alpha_composite(spiny, (rx, ry))
    count(panel, rx + 12, ry + 9, "2")
    panel.resize((panel.width * SCALE, panel.height * SCALE), Image.NEAREST).save(out)
    print("wrote", out)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])

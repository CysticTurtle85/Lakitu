"""Pixelates a generated icon onto a true pixel grid: quantise to a palette, then each block takes its most common
colour, with dark outline colours winning when they cover >=22% of the block. Writes p2_<n>.png at 64/48/40/32 px
and a comparison sheet. The mod icon is the 64 px one (art/icon/icon_source.png, 28 colours).

    python art/icon/pixelate.py art/icon/icon_source.png
"""
import sys
import numpy as np
from PIL import Image, ImageDraw, ImageFont

def pixelate(src, n, colors=28, dark_share=0.22):
    q = src.quantize(colors=colors, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE)
    pal = np.array(q.getpalette()[:colors * 3]).reshape(-1, 3)
    idx = np.array(q)
    lum = pal @ [0.3, 0.59, 0.11]
    dark = lum < 70
    H = idx.shape[0]; b = H / n
    out = np.zeros((n, n), int)
    for j in range(n):
        for i in range(n):
            blk = idx[int(j*b):int((j+1)*b), int(i*b):int((i+1)*b)].ravel()
            counts = np.bincount(blk, minlength=colors)
            dk = counts * dark
            out[j, i] = dk.argmax() if dk.sum() >= dark_share * blk.size else counts.argmax()
    return Image.fromarray(pal[out].astype(np.uint8), 'RGB')

src = Image.open(sys.argv[1]).convert('RGB')
font = ImageFont.truetype('arial.ttf', 22)
sizes = [64, 48, 40, 32]
sheet = Image.new('RGB', (len(sizes) * 300, 340), (40, 42, 48)); d = ImageDraw.Draw(sheet)
for i, n in enumerate(sizes):
    small = pixelate(src, n)
    k = 256 // n
    small.resize((n * k, n * k), Image.NEAREST).save(f'p2_{n}.png')
    sheet.paste(small.resize((280, 280), Image.NEAREST), (i * 300 + 10, 45)); d.text((i * 300 + 10, 10), f'{n} px', fill=(240, 240, 240), font=font)
sheet.save('p2_options.png')

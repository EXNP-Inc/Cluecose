#!/usr/bin/env python3
"""Generate Cluecose app icon: liquid teal-green drop on a deep glass gradient."""
from PIL import Image, ImageDraw, ImageFilter
import math, os

OUT = "/Users/dsikic/Source/Cluecose/source/Cluecose/Assets.xcassets/AppIcon.appiconset/Icon.png"
os.makedirs(os.path.dirname(OUT), exist_ok=True)

S = 1024
img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
d = ImageDraw.Draw(img)

# Rounded-square background (iOS 26 icons are not pre-masked; draw the squircle-ish square).
def rounded_mask(size, radius):
    m = Image.new("L", (size, size), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, size - 1, size - 1), radius=radius, fill=255)
    return m

R = int(S * 0.2237)
bg = Image.new("RGBA", (S, S), (0, 0, 0, 0))
db = ImageDraw.Draw(bg)
# Vertical deep gradient
grad = Image.new("RGBA", (S, S))
dg = ImageDraw.Draw(grad)
top = (6, 22, 30)
bot = (24, 76, 80)
for y in range(S):
    t = y / S
    c = tuple(int(top[i] + (bot[i] - top[i]) * t) for i in range(3))
    dg.line([(0, y), (S, y)], fill=c + (255,))
bg = Image.composite(bg, grad, rounded_mask(S, R))
img = Image.alpha_composite(img, bg)

# Soft diagonal light sweep (glass sheen)
sheen = Image.new("RGBA", (S, S), (0, 0, 0, 0))
ds = ImageDraw.Draw(sheen)
for i in range(S):
    a = int(26 * (0.5 + 0.5 * math.sin(i / 90.0)))
    ds.line([(i, 0), (i + S // 3, S)], fill=(255, 255, 255, a), width=26)
sheen = sheen.filter(ImageFilter.GaussianBlur(30))
img = Image.alpha_composite(img, Image.composite(sheen, Image.new("RGBA", (S, S), (0, 0, 0, 0)), rounded_mask(S, R)))

# Glow drop in the center
cx, cy, r = S // 2, int(S * 0.52), int(S * 0.27)
glow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
dg2 = ImageDraw.Draw(glow)
dg2.ellipse((cx - r - 60, cy - r - 60, cx + r + 60, cy + r + 60), fill=(40, 220, 150, 90))
glow = glow.filter(ImageFilter.GaussianBlur(70))
img = Image.alpha_composite(img, glow)

# The drop itself (teal -> green gradient, glass)
drop = Image.new("RGBA", (S, S), (0, 0, 0, 0))
dd = ImageDraw.Draw(drop)
for t in range(200):
    y0 = cy - r + int(2 * r * t / 200)
    a = 255
    rr = int(46 + (76 - 46) * t / 200)
    gg = int(214 + (232 - 214) * t / 200)
    bb = int(236 + (180 - 236) * t / 200)
    dd.line([(cx, y0), (cx + r, y0)], fill=(rr, gg, bb, a))
    dd.line([(cx - r, y0), (cx, y0)], fill=(rr, gg, bb, a))
# Mask to a teardrop: circle bottom + cone top
circle = Image.new("L", (S, S), 0)
ImageDraw.Draw(circle).ellipse((cx - r, cy - r, cx + r, cy + r), fill=255)
cone = Image.new("L", (S, S), 0)
ImageDraw.Draw(cone).polygon([(cx, cy - int(r * 2.05)), (cx - int(r * 0.94), cy + int(r * 0.2)),
                              (cx + int(r * 0.94), cy + int(r * 0.2))], fill=255)
mask = Image.eval(Image.composite(circle, cone, cone), lambda v: v)
drop.putalpha(mask)
img = Image.alpha_composite(img, drop)

# Inner highlight (glass reflection)
hl = Image.new("RGBA", (S, S), (0, 0, 0, 0))
dh = ImageDraw.Draw(hl)
dw, dhgt = int(r * 0.52), int(r * 0.78)
dh.ellipse((cx - r // 2 - dw // 2, cy - int(r * 0.55) - dhgt // 2,
            cx - r // 2 + dw // 2, cy - int(r * 0.55) + dhgt // 2), fill=(255, 255, 255, 170))
hl = hl.filter(ImageFilter.GaussianBlur(14))
img = Image.alpha_composite(img, Image.composite(hl, Image.new("RGBA", (S, S), (0, 0, 0, 0)), rounded_mask(S, R)))

# Outer rim light
rim = Image.new("RGBA", (S, S), (0, 0, 0, 0))
dr = ImageDraw.Draw(rim)
dr.rounded_rectangle((10, 10, S - 11, S - 11), radius=R, outline=(255, 255, 255, 70), width=14)
rim = rim.filter(ImageFilter.GaussianBlur(10))
img = Image.alpha_composite(img, Image.composite(rim, Image.new("RGBA", (S, S), (0, 0, 0, 0)), rounded_mask(S, R)))

img.save(OUT)
print("saved", OUT)

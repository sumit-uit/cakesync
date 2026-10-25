"""Deletes near-identical copies (dHash distance <= 4); keeps the first by name."""
import sys, glob, os
from PIL import Image
def dhash(f):
    im = Image.open(f).convert("L").resize((9, 8)); p = list(im.getdata())
    return sum(1 << i for i in range(64) if p[(i // 8) * 9 + i % 8] > p[(i // 8) * 9 + i % 8 + 1])
kept = []
removed = 0
for f in sorted(glob.glob(sys.argv[1] + "/*.jpg")):
    h = dhash(f)
    if any(bin(h ^ k).count("1") <= 4 for k in kept):
        os.remove(f); removed += 1
    else:
        kept.append(h)
print("kept", len(kept), "removed", removed)

import sys, glob, os
from PIL import Image, ImageOps
src, dst = sys.argv[1], sys.argv[2]
for f in sorted(p for p in glob.glob(src + "/**/*", recursive=True) if p.lower().endswith((".jpg", ".jpeg"))):
    date = os.path.basename(os.path.dirname(f))
    im = ImageOps.exif_transpose(Image.open(f)).convert("RGB"); im.thumbnail((1024, 1024))
    im.save(f"{dst}/{sys.argv[3] if len(sys.argv) > 3 else 'own'}_{date}_{os.path.splitext(os.path.basename(f))[0]}.jpg", quality=88)

"""Review pages: 6 photos each with numbered proposal boxes. Writes pages/pNNN.jpg + pages/index.json."""
import json, os, sys
from PIL import Image, ImageDraw, ImageFont
data = sys.argv[1]; per, cell = 6, 520
props = json.load(open(f"{data}/proposals.json"))
done = json.load(open(f"{data}/labels.json")) if os.path.exists(f"{data}/labels.json") else {}
files = sorted(f for f in props if f not in done and os.path.exists(f"{data}/images/{f}") and f.startswith(sys.argv[3] if len(sys.argv) > 3 else ""))[: int(sys.argv[2]) if len(sys.argv) > 2 else None]
import shutil; shutil.rmtree(f"{data}/pages", ignore_errors=True)
os.makedirs(f"{data}/pages", exist_ok=True)
font = ImageFont.load_default(size=22)
index = {}
for p in range(0, len(files), per):
    chunk = files[p:p + per]
    page = Image.new("RGB", (3 * cell, 2 * (cell + 30)), "white"); d = ImageDraw.Draw(page)
    for i, f in enumerate(chunk):
        im = Image.open(f"{data}/images/{f}").convert("RGB"); w, h = im.size
        s = min(cell / w, cell / h); im = im.resize((int(w * s), int(h * s)))
        x, y = (i % 3) * cell, (i // 3) * (cell + 30)
        page.paste(im, (x, y + 30)); dd = ImageDraw.Draw(page)
        dd.text((x + 4, y + 2), f"[{i}] {f[:34]}", fill="black", font=font)
        for k, b in enumerate(props[f]["boxes"]):
            x0, y0, x1, y1 = [v * s for v in b["box"]]
            col = "red" if b["cls"] == "topper" else "blue"
            dd.rectangle([x + x0, y + 30 + y0, x + x1, y + 30 + y1], outline=col, width=3)
            dd.rectangle([x + x0, y + 30 + y0, x + x0 + 26, y + 30 + y0 + 24], fill=col)
            dd.text((x + x0 + 4, y + 30 + y0), str(k), fill="white", font=font)
    name = f"p{p // per:03d}"
    page.save(f"{data}/pages/{name}.jpg", quality=80); index[name] = chunk
json.dump(index, open(f"{data}/pages/index.json", "w"))
print(len(index), "pages")

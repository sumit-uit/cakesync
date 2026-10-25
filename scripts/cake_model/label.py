"""Saves review labels. Usage: label.py DATA PAGE "0=3 1=1,2c,5s 3=0 ..."
cell=tiers (0 = skip photo), then box number + s (simple topper) / c (complex) / p (edible print); others rejected."""
import json, os, re, sys
data, page, spec = sys.argv[1:4]
index = json.load(open(f"{data}/pages/index.json"))[page]
path = f"{data}/labels.json"; labels = json.load(open(path)) if os.path.exists(path) else {}
cells = dict(tok.split("=", 1) for tok in spec.split())
assert set(map(int, cells)) == set(range(len(index))), f"need cells 0..{len(index) - 1}"
for i, v in cells.items():
    parts = v.split(",")
    boxes = {m[1]: m[2] for m in (re.fullmatch(r"(\d+)([scph])", p) for p in parts[1:]) if m}
    labels[index[int(i)]] = {"t": int(parts[0]), "b": boxes, "print": "P" in parts or "p" in boxes.values()}
json.dump(labels, open(path, "w"), indent=0)
print(page, "saved;", len(labels), "labelled")

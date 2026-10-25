"""Downloads openly licensed (commercial use OK) cake photos from Openverse, with credits."""
import csv, io, json, os, sys, time, urllib.parse, urllib.request
from PIL import Image, ImageOps

out = sys.argv[1]; per_query = int(sys.argv[2])
queries = ["birthday cake topper", "fondant cake", "two tier cake", "three tier cake", "tiered birthday cake",
           "cartoon cake", "character cake", "superhero cake", "unicorn cake", "princess cake", "number cake",
           "photo cake", "edible image cake", "rasmalai cake", "gulab jamun cake", "fruit cake decorated",
           "strawberry cake", "chocolate drip cake", "wedding cake", "baby shower cake", "figurine cake",
           "buttercream cake", "cake decorating", "kids birthday cake"]
if len(sys.argv) > 3: queries = sys.argv[3].split("|")
UA = {"User-Agent": "CakeSync-dataset/1.0 (training an on-device model)"}
os.makedirs(f"{out}/images", exist_ok=True)
seen = set(); rows = []
creds = f"{out}/credits.csv"
if os.path.exists(creds):
    rows = list(csv.DictReader(open(creds))); seen = {r["id"] for r in rows}
for q in queries:
    got, page = 0, 1
    while got < per_query and page <= 5:
        url = "https://api.openverse.org/v1/images/?" + urllib.parse.urlencode(
            {"q": q, "license_type": "commercial", "page_size": 20, "page": page, "mature": "false"})
        try:
            data = json.load(urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=30))
        except Exception as e:
            print("search failed", q, e); time.sleep(10); break
        results = data.get("results", [])
        if not results: break
        for r in results:
            if got >= per_query: break
            if r["id"] in seen: continue
            seen.add(r["id"])
            try:
                raw = urllib.request.urlopen(urllib.request.Request(r["url"], headers=UA), timeout=30).read()
                im = ImageOps.exif_transpose(Image.open(io.BytesIO(raw))).convert("RGB")
            except Exception:
                continue
            if min(im.size) < 300: continue
            im.thumbnail((1024, 1024))
            name = f"ov_{r['id'][:12]}.jpg"
            im.save(f"{out}/images/{name}", quality=88)
            rows.append({"id": r["id"], "file": name, "query": q, "title": r.get("title", ""), "creator": r.get("creator", ""),
                         "license": f"{r['license']} {r.get('license_version', '')}", "source": r.get("foreign_landing_url", "")})
            got += 1
        page += 1; time.sleep(3)
    print(f"{q}: {got}", flush=True)
    with open(creds, "w", newline="") as f:
        w = csv.DictWriter(f, fieldnames=["id", "file", "query", "title", "creator", "license", "source"]); w.writeheader(); w.writerows(rows)
print("total", len(rows))

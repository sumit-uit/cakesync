"""Proposes boxes for cake tiers, toppers and edible prints with OWLv2 (open-vocabulary detector)."""
import json, sys, glob, os
import torch
from torchvision.ops import nms
from PIL import Image
from transformers import Owlv2Processor, Owlv2ForObjectDetection

# Prompt → candidate class; I review every box afterwards.
PROMPTS = {
    "a single tier of a layered cake": "tier", "a round cake layer": "tier",
    "a figurine on a cake": "topper", "a toy figure": "topper", "a fondant decoration": "topper",
    "a cake topper": "topper", "a small decorative object on a cake": "topper",
    "a printed picture on a cake": "edible_print", "a photo printed on a cake": "edible_print",
}
THRESH = {"topper": 0.12, "edible_print": 0.2}
dev = "mps" if torch.backends.mps.is_available() else "cpu"
name = "google/owlv2-base-patch16-ensemble"
proc = Owlv2Processor.from_pretrained(name); model = Owlv2ForObjectDetection.from_pretrained(name).to(dev).eval()
texts = list(PROMPTS)
files = sys.argv[2:] if len(sys.argv) > 2 else sorted(glob.glob(sys.argv[1] + "/images/*.jpg"))
out_path = sys.argv[1] + "/proposals.json"
props = json.load(open(out_path)) if os.path.exists(out_path) else {}
for f in files:
    key = os.path.basename(f)
    if key in props: continue
    im = Image.open(f).convert("RGB"); w, h = im.size; side = max(w, h)
    inputs = proc(text=[texts], images=im, return_tensors="pt").to(dev)
    with torch.no_grad(): outputs = model(**inputs)
    outputs.logits = outputs.logits.cpu(); outputs.pred_boxes = outputs.pred_boxes.cpu()
    res = proc.post_process_grounded_object_detection(outputs, threshold=0.1, target_sizes=torch.tensor([[side, side]]))[0]
    boxes = []
    for cls in THRESH:
        idx = [i for i, l in enumerate(res["labels"].tolist()) if PROMPTS[texts[l]] == cls and res["scores"][i] >= THRESH[cls]]
        if not idx: continue
        b, s = res["boxes"][idx], res["scores"][idx]
        for k in nms(b, s, 0.3).tolist():
            x0, y0, x1, y1 = [round(v) for v in b[k].tolist()]
            x0, y0, x1, y1 = max(0, x0), max(0, y0), min(w, x1), min(h, y1)
            if (x1 - x0) * (y1 - y0) < 0.0015 * w * h or (x1 - x0) * (y1 - y0) > 0.9 * w * h: continue
            boxes.append({"cls": cls, "score": round(float(s[k]), 3), "box": [x0, y0, x1, y1]})
    props[key] = {"size": [w, h], "boxes": boxes}
    print(key, len(boxes), flush=True)
    json.dump(props, open(out_path, "w"))

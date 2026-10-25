"""Trains the two on-device models from labels.json (run inside the cake-trainer container).

cake_parts.tflite  - object detector: topper_simple, topper_complex, topper_super (3D characters), edible_print
cake_tiers.tflite  - image classifier: 1, 2, 3 (3 = three or more tiers)
cake_print.tflite  - image classifier: yes / no edible print
"""
import hashlib, json, os, shutil, sys
from mediapipe_model_maker import image_classifier, object_detector, quantization

data, out = sys.argv[1], sys.argv[2]
props = json.load(open(f"{data}/proposals.json")); labels = json.load(open(f"{data}/labels.json"))
CLS = {"s": "topper_simple", "c": "topper_complex", "h": "topper_super", "p": "edible_print"}
CATS = ["topper_simple", "topper_complex", "topper_super", "edible_print"]

def is_val(f):
    # Photos of one cake share a date: keep a whole date (or a public photo) on one side.
    group = "_".join(f.split("_")[:2]) if f.startswith("own") else f
    return int(hashlib.md5(group.encode()).hexdigest(), 16) % 100 < 15

work = "/tmp/ds"; shutil.rmtree(work, ignore_errors=True)
coco = {s: {"images": [], "annotations": [], "categories": [{"id": i + 1, "name": c} for i, c in enumerate(CATS)]} for s in ("train", "val")}
for f, lab in labels.items():
    if not os.path.exists(f"{data}/images/{f}"): continue  # removed by the cake filter after labelling
    split = "val" if is_val(f) else "train"
    if lab.get("t", 0) > 0:  # 0 = not a whole cake: no tier label, but its topper boxes still count
        tiers = str(min(lab["t"], 3))
        os.makedirs(f"{work}/tiers_{split}/{tiers}", exist_ok=True)
        shutil.copy(f"{data}/images/{f}", f"{work}/tiers_{split}/{tiers}/{f}")
        printed = "yes" if lab.get("print") or "p" in lab.get("b", {}).values() else "no"
        os.makedirs(f"{work}/print_{split}/{printed}", exist_ok=True)
        shutil.copy(f"{data}/images/{f}", f"{work}/print_{split}/{printed}/{f}")
    boxes = [(CLS[v], props[f]["boxes"][int(k)]["box"]) for k, v in lab.get("b", {}).items() if v in CLS]
    boxes += [(CLS[v], box) for v, box in lab.get("add", [])]
    if not boxes: continue
    c = coco[split]; img_id = len(c["images"]) + 1
    w, h = props[f]["size"]
    os.makedirs(f"{work}/det_{split}/images", exist_ok=True)
    shutil.copy(f"{data}/images/{f}", f"{work}/det_{split}/images/{f}")
    c["images"].append({"id": img_id, "file_name": f, "width": w, "height": h})
    for name, (x0, y0, x1, y1) in boxes:
        c["annotations"].append({"id": len(c["annotations"]) + 1, "image_id": img_id, "category_id": CATS.index(name) + 1,
                                 "bbox": [x0, y0, x1 - x0, y1 - y0], "area": (x1 - x0) * (y1 - y0), "iscrowd": 0})
for s in coco:
    json.dump(coco[s], open(f"{work}/det_{s}/labels.json", "w"))
    print(s, len(coco[s]["images"]), "detector images,", len(coco[s]["annotations"]), "boxes")
os.makedirs(out, exist_ok=True)

# Tier classifier
tr = image_classifier.Dataset.from_folder(f"{work}/tiers_train"); va = image_classifier.Dataset.from_folder(f"{work}/tiers_val")
clf = image_classifier.ImageClassifier.create(train_data=tr, validation_data=va, options=image_classifier.ImageClassifierOptions(
    supported_model=image_classifier.SupportedModels.EFFICIENTNET_LITE0,
    hparams=image_classifier.HParams(export_dir=f"{out}/clf", epochs=int(os.environ.get("EPOCHS", 20)), batch_size=16, shuffle=True),
    model_options=image_classifier.ModelOptions(dropout_rate=0.2)))
print("tiers val (loss, accuracy):", clf.evaluate(va))
clf.export_model("cake_tiers.tflite", quantization_config=quantization.QuantizationConfig.for_int8(tr)); shutil.copy(f"{out}/clf/cake_tiers.tflite", f"{out}/cake_tiers.tflite")

# Edible print yes/no classifier
tr = image_classifier.Dataset.from_folder(f"{work}/print_train"); va = image_classifier.Dataset.from_folder(f"{work}/print_val")
pc = image_classifier.ImageClassifier.create(train_data=tr, validation_data=va, options=image_classifier.ImageClassifierOptions(
    supported_model=image_classifier.SupportedModels.EFFICIENTNET_LITE0,
    hparams=image_classifier.HParams(export_dir=f"{out}/print", epochs=int(os.environ.get("EPOCHS", 20)), batch_size=16, shuffle=True),
    model_options=image_classifier.ModelOptions(dropout_rate=0.2)))
print("print val (loss, accuracy):", pc.evaluate(va))
pc.export_model("cake_print.tflite", quantization_config=quantization.QuantizationConfig.for_int8(tr)); shutil.copy(f"{out}/print/cake_print.tflite", f"{out}/cake_print.tflite")

# Topper / print detector
tr = object_detector.Dataset.from_coco_folder(f"{work}/det_train", cache_dir="/tmp/cache_tr")
va = object_detector.Dataset.from_coco_folder(f"{work}/det_val", cache_dir="/tmp/cache_va")
det = object_detector.ObjectDetector.create(train_data=tr, validation_data=va, options=object_detector.ObjectDetectorOptions(
    supported_model=object_detector.SupportedModels.MOBILENET_MULTI_AVG,
    hparams=object_detector.HParams(export_dir=f"{out}/det", epochs=int(os.environ.get("EPOCHS", 60)), batch_size=8, learning_rate=0.3)))
loss, metrics = det.evaluate(va, batch_size=4)
print("detector val:", {k: round(float(v), 3) for k, v in metrics.items()})
# Post-training int8 isn't supported for the detector; float16 halves it.
det.export_model("cake_parts.tflite", quantization_config=quantization.QuantizationConfig.for_float16()); shutil.copy(f"{out}/det/cake_parts.tflite", f"{out}/cake_parts.tflite")

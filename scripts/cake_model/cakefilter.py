"""Moves public photos that aren't a whole decorated cake out of the dataset (CLIP zero-shot)."""
import glob, os, shutil, sys, torch
from PIL import Image
from transformers import CLIPModel, CLIPProcessor
data = sys.argv[1]
name = "openai/clip-vit-base-patch32"
model, proc = CLIPModel.from_pretrained(name).eval(), CLIPProcessor.from_pretrained(name)
labels = ["a photo of a whole decorated cake", "a photo of a tiered cake", "a slice of cake", "cupcakes", "cookies",
          "a plate of food", "people", "a building or street", "a toy", "a drawing or cartoon", "a book or magazine page"]
keep = {0, 1}
os.makedirs(f"{data}/rejected", exist_ok=True)
files = sorted(glob.glob(f"{data}/images/ov_*.jpg")); moved = 0
for i in range(0, len(files), 16):
    batch = files[i:i + 16]
    inputs = proc(text=labels, images=[Image.open(f).convert("RGB") for f in batch], return_tensors="pt", padding=True)
    with torch.no_grad(): probs = model(**inputs).logits_per_image.softmax(-1)
    for f, p in zip(batch, probs):
        if p[list(keep)].sum() < 0.5:
            shutil.move(f, f"{data}/rejected/"); moved += 1
print("kept", len(files) - moved, "rejected", moved)

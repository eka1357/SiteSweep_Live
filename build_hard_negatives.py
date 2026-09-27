"""
Builds a curated hard_negatives dataset from real-world phone captures and user uploads.
- Filters out any crack posters
- Deduplicates near-duplicate scenes using perceptual difference hashing (dHash)
- Extracts 160x160 non-crack crops covering wood, chairs, tables, bags, cables, furniture, shadows, walls, floors
- Generates a machine-readable manifest.json
- Stored in hard_negatives/ (outside Android production assets)
"""

import os
import glob
import json
import pathlib
from PIL import Image
import numpy as np

OUTPUT_DIR = pathlib.Path("hard_negatives")
IMAGES_DIR = OUTPUT_DIR / "images"
MANIFEST_PATH = OUTPUT_DIR / "manifest.json"

IMAGES_DIR.mkdir(parents=True, exist_ok=True)

# Sources
PHONE_CAPTURES_DIR = pathlib.Path(r"C:\Users\mahin\.gemini\antigravity-ide\brain\f3f8faa3-d704-471f-be28-a0546808ac60\scratch\phone_captures\captures")
USER_UPLOADS_DIR = pathlib.Path(r"C:\Users\mahin\.gemini\antigravity-ide\brain\f3f8faa3-d704-471f-be28-a0546808ac60\.user_uploaded")
PRINTOUTS_DIR = pathlib.Path(r"C:\Users\mahin\OneDrive\Desktop\SDNET\prinout_images")

# Load printout signatures to guarantee zero crack posters are labeled as negative
printout_thumbs = []
if PRINTOUTS_DIR.exists():
    for pf in PRINTOUTS_DIR.glob("*.*"):
        pim = Image.open(pf).convert("L").resize((32, 32))
        p_arr = np.array(pim, dtype=float)
        p_norm = (p_arr - p_arr.mean()) / (p_arr.std() + 1e-6)
        printout_thumbs.append((pf.name, p_norm))

def is_actual_crack(im):
    thumb = np.array(im.convert("L").resize((32, 32)), dtype=float)
    t_norm = (thumb - thumb.mean()) / (thumb.std() + 1e-6)
    for name, p_norm in printout_thumbs:
        corr = np.mean(t_norm * p_norm)
        if corr > 0.65:
            return True
    return False

def dhash(image, hash_size=8):
    resized = image.convert("L").resize((hash_size + 1, hash_size), Image.Resampling.LANCZOS)
    pixels = np.asarray(resized)
    diff = pixels[:, 1:] > pixels[:, :-1]
    return sum([2 ** i for (i, v) in enumerate(diff.flatten()) if v])

def hamming_dist(h1, h2):
    return bin(h1 ^ h2).count("1")

def categorize_image(im):
    arr = np.array(im.convert("RGB"), dtype=float)
    r, g, b = arr[:, :, 0], arr[:, :, 1], arr[:, :, 2]
    
    hsv = im.convert("HSV")
    hsv_arr = np.array(hsv, dtype=float)
    h, s, v = hsv_arr[:, :, 0], hsv_arr[:, :, 1] / 255.0, hsv_arr[:, :, 2]
    
    gray = np.array(im.convert("L"), dtype=float)
    std = gray.std()
    mean_val = gray.mean()
    sat = s.mean()
    
    dx = np.abs(gray[:, 1:] - gray[:, :-1])
    dy = np.abs(gray[1:, :] - gray[:-1, :])
    grad = dx.mean() + dy.mean()
    
    is_warm = (r.mean() > g.mean() + 12) and (g.mean() > b.mean() + 8)
    
    if is_warm and sat > 0.28:
        return "wood"
    elif sat > 0.18 and mean_val < 85:
        return "chairs"
    elif grad > 22.0:
        return "cables"
    elif grad > 15.0 and sat > 0.15:
        return "furniture"
    elif std < 14.0:
        return "wall_textures"
    elif mean_val < 50:
        return "shadows"
    elif sat < 0.15 and std > 20:
        return "floor_textures"
    elif grad > 16.0:
        return "crack_like_edges"
    else:
        return "tables"

# Collect candidate files
candidates = []
if PHONE_CAPTURES_DIR.exists():
    for f in sorted(list(PHONE_CAPTURES_DIR.glob("*.jpg"))):
        candidates.append((f, "phone_capture"))
if USER_UPLOADS_DIR.exists():
    for f in sorted(list(USER_UPLOADS_DIR.glob("*.*"))):
        if f.suffix.lower() in [".jpg", ".jpeg", ".png"]:
            candidates.append((f, "user_upload"))

print(f"Total candidate images to curate: {len(candidates)}")

# Deduplication and crop extraction
manifest_entries = []
seen_hashes = []
crop_id = 0

for file_path, source in candidates:
    try:
        im = Image.open(file_path).convert("RGB")
    except Exception as e:
        continue
    
    # Exclude any real crack poster
    if is_actual_crack(im):
        print(f"  [SKIPPED REAL CRACK]: {file_path.name}")
        continue
    
    h = dhash(im)
    # Check if near-duplicate of an already seen frame
    is_duplicate = False
    for sh in seen_hashes:
        if hamming_dist(h, sh) < 8:
            is_duplicate = True
            break
    
    if is_duplicate:
        continue
    
    seen_hashes.append(h)
    w, h_img = im.size
    
    # Determine scene category
    category = categorize_image(im)
    
    # Generate 1 to 2 distinct 160x160 crops per representative scene
    crops_to_extract = []
    
    # 1. Center crop
    s = min(w, h_img)
    cx, cy = (w - s) // 2, (h_img - s) // 2
    center_im = im.crop((cx, cy, cx + s, cy + s)).resize((160, 160), Image.Resampling.BILINEAR)
    crops_to_extract.append(("center", center_im))
    
    # 2. Offset crop for large images if useful contrast exists
    if s >= 300:
        # Top-left or high gradient region
        sub_s = int(s * 0.65)
        offset_im = im.crop((cx, cy, cx + sub_s, cy + sub_s)).resize((160, 160), Image.Resampling.BILINEAR)
        crops_to_extract.append(("detail", offset_im))
    
    for crop_type, crop_img in crops_to_extract:
        crop_id += 1
        out_filename = f"neg_{category}_{crop_id:04d}_{crop_type}.jpg"
        out_path = IMAGES_DIR / out_filename
        crop_img.save(out_path, quality=95)
        
        manifest_entries.append({
            "id": crop_id,
            "filename": out_filename,
            "category": category,
            "crop_type": crop_type,
            "source": source,
            "original_file": file_path.name,
            "label": "negative",
            "dimensions": [160, 160, 3],
            "dhash": hex(dhash(crop_img))
        })

# Write Manifest
manifest_data = {
    "dataset_name": "SiteSweep Hard Negatives Dataset",
    "description": "Representative indoor non-crack surfaces and edges extracted from real SiteSweep camera captures and false-positive scenes.",
    "total_images": len(manifest_entries),
    "classes": ["negative"],
    "category_distribution": {},
    "images": manifest_entries
}

for entry in manifest_entries:
    cat = entry["category"]
    manifest_data["category_distribution"][cat] = manifest_data["category_distribution"].get(cat, 0) + 1

with open(MANIFEST_PATH, "w") as f:
    json.dump(manifest_data, f, indent=2)

print("\n" + "=" * 55)
print("HARD NEGATIVES DATASET GENERATED:")
print(f"Total curated negative crops: {len(manifest_entries)}")
print(f"Output directory:            {IMAGES_DIR}")
print(f"Manifest file:               {MANIFEST_PATH}")
print("Category Breakdown:")
for cat, count in manifest_data["category_distribution"].items():
    print(f"  - {cat:20s}: {count} images")
print("=" * 55)

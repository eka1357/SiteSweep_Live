"""
Fine-tunes the crack classifier using SDNET (Decks, Pavements, Walls)
and real-world hard_negatives (wood, chairs, tables, cables, furniture, floors)
with genuine data augmentation, leak-free Train/Val/Test splits, and full 11-category evaluation.
Exports crack_model_v2.tflite without overwriting V1.
"""

import json
import os
import pathlib
import random
import numpy as np
import tensorflow as tf
from PIL import Image

# 1. Reproducible Random Seeds
RANDOM_SEED = 42
random.seed(RANDOM_SEED)
np.random.seed(RANDOM_SEED)
tf.random.set_seed(RANDOM_SEED)

# Paths
DATASET_ROOT = pathlib.Path(r"C:\Users\mahin\OneDrive\Desktop\SDNET")
CHECKPOINT_PATH = DATASET_ROOT / "crack_model_checkpoint.keras"
HARD_NEGATIVES_DIR = pathlib.Path("hard_negatives/images")
HARD_NEGATIVES_MANIFEST = pathlib.Path("hard_negatives/manifest.json")

# Model Export Destinations (V1 untouched)
OUTPUT_TFLITE_DIR = pathlib.Path("models")
OUTPUT_TFLITE_DIR.mkdir(parents=True, exist_ok=True)
OUTPUT_TFLITE = OUTPUT_TFLITE_DIR / "crack_model_v2.tflite"
ROOT_TFLITE = pathlib.Path("crack_model_v2.tflite")
EVAL_REPORT_PATH = pathlib.Path("evaluation_report_v2.json")

# Hyperparameters
IMG_SIZE = (160, 160)
BATCH_SIZE = 32
EPOCHS = 6

print("=== 1. PREPARING DATASET WITH BALANCED HARD NEGATIVES ===")

# Gather SDNET Cracked (Positive) - 600 per category
sdnet_cracked = []
for sub in ["Decks/Cracked", "Pavements/Cracked", "Walls/Cracked"]:
    p = DATASET_ROOT / sub
    imgs = sorted(list(p.glob("*.jpg")) + list(p.glob("*.JPG")) + list(p.glob("*.png")))
    rng = random.Random(RANDOM_SEED)
    sampled = rng.sample(imgs, min(600, len(imgs)))
    sdnet_cracked.extend(sampled)
    print(f"Sampled {len(sampled)} unique cracked from {sub}")

# Gather Printout Images (Positive targets) - single occurrence
printout_dir = DATASET_ROOT / "prinout_images"
printout_imgs = sorted(list(printout_dir.glob("*.*"))) if printout_dir.exists() else []
print(f"Added {len(printout_imgs)} real printout poster targets: {[p.name for p in printout_imgs]}")

positive_paths = list(set([str(p) for p in sdnet_cracked + printout_imgs]))
positive_labels = [1.0] * len(positive_paths)

# Gather SDNET Non-Cracked (Negative - uncracked concrete) - 550 per category
sdnet_noncracked = []
for sub in ["Decks/Non-cracked", "Pavements/Non-cracked", "Walls/Non-cracked"]:
    p = DATASET_ROOT / sub
    imgs = sorted(list(p.glob("*.jpg")) + list(p.glob("*.JPG")) + list(p.glob("*.png")))
    rng = random.Random(RANDOM_SEED)
    sampled = rng.sample(imgs, min(550, len(imgs)))
    sdnet_noncracked.extend(sampled)
    print(f"Sampled {len(sampled)} unique non-cracked concrete from {sub}")

# Gather Hard Negatives (Negative - wood, chairs, tables, cables, furniture, floors)
hard_negative_files = sorted(list(HARD_NEGATIVES_DIR.glob("*.jpg"))) if HARD_NEGATIVES_DIR.exists() else []
print(f"Loaded {len(hard_negative_files)} curated hard negative crops from {HARD_NEGATIVES_DIR}")

negative_paths = list(set([str(p) for p in sdnet_noncracked + hard_negative_files]))
negative_labels = [0.0] * len(negative_paths)

# Stratified Split: Train 70%, Validation 15%, Test 15%
def stratified_split(paths, labels, train_ratio=0.70, val_ratio=0.15):
    paired = list(zip(paths, labels))
    rng = random.Random(RANDOM_SEED)
    rng.shuffle(paired)
    
    n_total = len(paired)
    n_train = int(n_total * train_ratio)
    n_val = int(n_total * val_ratio)
    
    train_part = paired[:n_train]
    val_part = paired[n_train:n_train + n_val]
    test_part = paired[n_train + n_val:]
    
    return train_part, val_part, test_part

pos_train, pos_val, pos_test = stratified_split(positive_paths, positive_labels)
neg_train, neg_val, neg_test = stratified_split(negative_paths, negative_labels)

train_data = pos_train + neg_train
val_data = pos_val + neg_val
test_data = pos_test + neg_test

rng = random.Random(RANDOM_SEED)
rng.shuffle(train_data)
rng.shuffle(val_data)
rng.shuffle(test_data)

train_paths, train_labels = zip(*train_data)
val_paths, val_labels = zip(*val_data)
test_paths, test_labels = zip(*test_data)

train_paths, train_labels = list(train_paths), list(train_labels)
val_paths, val_labels = list(val_paths), list(val_labels)
test_paths, test_labels = list(test_paths), list(test_labels)

# Verify zero train/val/test leakage
assert len(set(train_paths).intersection(set(val_paths))) == 0, "Leakage between train and val!"
assert len(set(train_paths).intersection(set(test_paths))) == 0, "Leakage between train and test!"
assert len(set(val_paths).intersection(set(test_paths))) == 0, "Leakage between val and test!"

print("\n" + "=" * 55)
print("DATASET SPLIT SUMMARY:")
print(f"- Positive samples:   {len(positive_paths)}")
print(f"- Negative samples:   {len(negative_paths)} (Concrete: {len(sdnet_noncracked)}, Hard Negatives: {len(hard_negative_files)})")
print(f"- Total samples:      {len(positive_paths) + len(negative_paths)}")
print(f"- Training samples:   {len(train_paths)} (Pos: {sum(train_labels)}, Neg: {len(train_labels) - sum(train_labels)})")
print(f"- Validation samples: {len(val_paths)} (Pos: {sum(val_labels)}, Neg: {len(val_labels) - sum(val_labels)})")
print(f"- Test samples:       {len(test_paths)} (Pos: {sum(test_labels)}, Neg: {len(test_labels) - sum(test_labels)})")
print("=" * 55 + "\n")

# 2. Data Pipelines
def load_and_preprocess(path, label):
    img = tf.io.read_file(path)
    img = tf.image.decode_jpeg(img, channels=3)
    img = tf.image.resize(img, IMG_SIZE)
    img = tf.cast(img, tf.float32) / 255.0
    return img, label

def augment(img, label):
    img = tf.image.random_flip_left_right(img)
    img = tf.image.random_flip_up_down(img)
    k = tf.random.uniform(shape=[], minval=0, maxval=4, dtype=tf.int32)
    img = tf.image.rot90(img, k=k)
    img = tf.image.random_brightness(img, max_delta=0.12)
    img = tf.image.random_contrast(img, lower=0.85, upper=1.15)
    crop_size = tf.random.uniform([], minval=144, maxval=160, dtype=tf.int32)
    img = tf.image.random_crop(img, size=[crop_size, crop_size, 3])
    img = tf.image.resize(img, IMG_SIZE)
    img = tf.clip_by_value(img, 0.0, 1.0)
    return img, label

train_ds = tf.data.Dataset.from_tensor_slices((train_paths, train_labels))
train_ds = train_ds.shuffle(len(train_paths), seed=RANDOM_SEED)
train_ds = train_ds.map(load_and_preprocess, num_parallel_calls=tf.data.AUTOTUNE)
train_ds = train_ds.map(augment, num_parallel_calls=tf.data.AUTOTUNE)
train_ds = train_ds.batch(BATCH_SIZE).prefetch(tf.data.AUTOTUNE)

val_ds = tf.data.Dataset.from_tensor_slices((val_paths, val_labels))
val_ds = val_ds.map(load_and_preprocess, num_parallel_calls=tf.data.AUTOTUNE)
val_ds = val_ds.batch(BATCH_SIZE).prefetch(tf.data.AUTOTUNE)

test_ds = tf.data.Dataset.from_tensor_slices((test_paths, test_labels))
test_ds = test_ds.map(load_and_preprocess, num_parallel_calls=tf.data.AUTOTUNE)
test_ds = test_ds.batch(BATCH_SIZE).prefetch(tf.data.AUTOTUNE)

# 3. Model Architecture & Fine-Tuning
print("=== 2. LOADING MODEL CHECKPOINT & CONFIGURING FINE-TUNING ===")
model = tf.keras.models.load_model(CHECKPOINT_PATH)

mobilenet = model.layers[0]
mobilenet.trainable = True
# Unfreeze last 25 layers for richer texture adaptation
for layer in mobilenet.layers[:-25]:
    layer.trainable = False

print(f"MobileNet total layers: {len(mobilenet.layers)}, Trainable layers: 25 + top Dense")

lr_schedule = tf.keras.optimizers.schedules.CosineDecay(
    initial_learning_rate=6e-5,
    decay_steps=EPOCHS * (len(train_paths) // BATCH_SIZE),
    alpha=0.2
)

model.compile(
    optimizer=tf.keras.optimizers.Adam(learning_rate=lr_schedule),
    loss="binary_crossentropy",
    metrics=["accuracy", tf.keras.metrics.Precision(name="precision"), tf.keras.metrics.Recall(name="recall")]
)

print("\n=== 3. RUNNING FINE-TUNING (CPU) ===")
history = model.fit(
    train_ds,
    validation_data=val_ds,
    epochs=EPOCHS,
    verbose=1
)

# 4. INT8 Quantization
print("\n=== 4. QUANTIZING TO INT8 TFLITE (V2) ===")
converter = tf.lite.TFLiteConverter.from_keras_model(model)
converter.optimizations = [tf.lite.Optimize.DEFAULT]

def representative_dataset():
    for images, _ in train_ds.take(45):
        for i in range(images.shape[0]):
            yield [tf.expand_dims(images[i], axis=0)]

converter.representative_dataset = representative_dataset
converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS_INT8]
converter.inference_input_type = tf.uint8
converter.inference_output_type = tf.uint8

tflite_model_v2 = converter.convert()

with open(OUTPUT_TFLITE, "wb") as f:
    f.write(tflite_model_v2)
with open(ROOT_TFLITE, "wb") as f:
    f.write(tflite_model_v2)

print(f"Exported INT8 TFLite V2 ({len(tflite_model_v2)/1024:.1f} KB) to:")
print(f" - {OUTPUT_TFLITE}")
print(f" - {ROOT_TFLITE}")

# 5. Full Evaluation Across the 11 Required Categories
print("\n=== 5. COMPREHENSIVE 11-CATEGORY EVALUATION ===")
interpreter = tf.lite.Interpreter(model_content=tflite_model_v2)
interpreter.allocate_tensors()
input_details = interpreter.get_input_details()[0]
output_details = interpreter.get_output_details()[0]
scale, zero_point = output_details["quantization"]
if scale == 0:
    scale = 0.00390625

def run_tflite_single(img_path_or_im):
    if isinstance(img_path_or_im, str) or isinstance(img_path_or_im, pathlib.Path):
        im = Image.open(str(img_path_or_im)).convert("RGB")
    else:
        im = img_path_or_im.convert("RGB")
    w, h = im.size
    s = min(w, h)
    im_crop = im.crop(((w - s) // 2, (h - s) // 2, (w + s) // 2, (h + s) // 2)).resize(IMG_SIZE, Image.Resampling.BILINEAR)
    arr = np.expand_dims(np.array(im_crop, dtype=np.uint8), axis=0)
    interpreter.set_tensor(input_details["index"], arr)
    interpreter.invoke()
    raw_val = interpreter.get_tensor(output_details["index"])[0][0]
    return (float(raw_val) - zero_point) * scale

def evaluate_category(name, file_list, expected_label, pass_threshold=0.65):
    """
    Evaluates a specific category.
    expected_label: 1 for crack, 0 for non-crack
    pass_threshold: for crack, passes if score >= pass_threshold. For non-crack, passes if score < pass_threshold.
    """
    scores = [run_tflite_single(f) for f in file_list]
    scores = np.array(scores) if scores else np.array([0.0])
    mean_score = float(np.mean(scores))
    median_score = float(np.median(scores))
    min_score = float(np.min(scores))
    max_score = float(np.max(scores))
    
    if expected_label == 1:
        passed = (mean_score >= pass_threshold) and (median_score >= pass_threshold)
        expected_str = "Crack (>= 0.65)"
    else:
        passed = (mean_score < pass_threshold) and (max_score < 0.85)
        expected_str = "Non-Crack (< 0.65)"
    
    pass_str = "PASS" if passed else "FAIL"
    return {
        "category": name,
        "sample_count": len(file_list),
        "expected": expected_str,
        "mean_probability": round(mean_score, 4),
        "median_probability": round(median_score, 4),
        "min_probability": round(min_score, 4),
        "max_probability": round(max_score, 4),
        "status": pass_str,
        "all_scores": [round(float(s), 4) for s in scores]
    }

# Load manifest for hard negative categorization
with open(HARD_NEGATIVES_MANIFEST) as f:
    manifest = json.load(f)

hard_neg_by_cat = {}
for entry in manifest["images"]:
    c = entry["category"]
    hard_neg_by_cat.setdefault(c, []).append(HARD_NEGATIVES_DIR / entry["filename"])

# Prepare test sample pools
# 1. Actual crack poster
cat1_imgs = printout_imgs

# 2. SDNET cracked samples (from test split to guarantee independence)
cat2_imgs = [p for p, l in zip(test_paths, test_labels) if l == 1.0 and "prinout_images" not in p][:40]

# 3. Plain concrete (from test split)
cat3_imgs = [p for p, l in zip(test_paths, test_labels) if l == 0.0 and "hard_negatives" not in p][:40]

# 4. Plain painted wall
cat4_imgs = hard_neg_by_cat.get("wall_textures", []) + [p for p in hard_negative_files if "wall" in p.name.lower()]

# 5. Wood
cat5_imgs = hard_neg_by_cat.get("wood", [])[:35]

# 6. Chair
cat6_imgs = hard_neg_by_cat.get("chairs", [])

# 7. Table
cat7_imgs = hard_neg_by_cat.get("tables", [])[:35]

# 8. Furniture
cat8_imgs = hard_neg_by_cat.get("furniture", [])[:35]

# 9. Cable / strap-like objects
cat9_imgs = hard_neg_by_cat.get("cables", [])

# 10. Shadows / textures
cat10_imgs = hard_neg_by_cat.get("floor_textures", []) + hard_neg_by_cat.get("shadows", [])

# 11. Hard-negative samples (comprehensive test slice)
cat11_imgs = [p for p, l in zip(test_paths, test_labels) if l == 0.0 and "hard_negatives" in p]

# Run Category Evaluations
cat_results = []
cat_results.append(evaluate_category("1. Actual crack poster", cat1_imgs, expected_label=1, pass_threshold=0.65))
cat_results.append(evaluate_category("2. SDNET cracked samples", cat2_imgs, expected_label=1, pass_threshold=0.65))
cat_results.append(evaluate_category("3. Plain concrete", cat3_imgs, expected_label=0, pass_threshold=0.65))
cat_results.append(evaluate_category("4. Plain painted wall", cat4_imgs, expected_label=0, pass_threshold=0.65))
cat_results.append(evaluate_category("5. Wood", cat5_imgs, expected_label=0, pass_threshold=0.65))
cat_results.append(evaluate_category("6. Chair", cat6_imgs, expected_label=0, pass_threshold=0.65))
cat_results.append(evaluate_category("7. Table", cat7_imgs, expected_label=0, pass_threshold=0.65))
cat_results.append(evaluate_category("8. Furniture", cat8_imgs, expected_label=0, pass_threshold=0.65))
cat_results.append(evaluate_category("9. Cable/strap-like objects", cat9_imgs, expected_label=0, pass_threshold=0.65))
cat_results.append(evaluate_category("10. Shadows/textures", cat10_imgs, expected_label=0, pass_threshold=0.65))
cat_results.append(evaluate_category("11. Hard-negative samples", cat11_imgs, expected_label=0, pass_threshold=0.65))

# 6. Overall Test Set Statistics & Probability Separation
all_test_preds = np.array([run_tflite_single(p) for p in test_paths])
y_test = np.array(test_labels, dtype=int)

crack_preds = all_test_preds[y_test == 1]
noncrack_preds = all_test_preds[y_test == 0]

min_crack_prob = float(np.min(crack_preds))
median_crack_prob = float(np.median(crack_preds))
max_crack_prob = float(np.max(crack_preds))

min_noncrack_prob = float(np.min(noncrack_preds))
median_noncrack_prob = float(np.median(noncrack_preds))
max_noncrack_prob = float(np.max(noncrack_preds))

prob_separation = median_crack_prob - median_noncrack_prob

# Confusion Matrix and Rates at Operating Threshold (0.65 - EXIT_CRACK boundary)
threshold_eval = 0.65
bin_preds = (all_test_preds >= threshold_eval).astype(int)

tp = int(np.sum((bin_preds == 1) & (y_test == 1)))
fp = int(np.sum((bin_preds == 1) & (y_test == 0)))
tn = int(np.sum((bin_preds == 0) & (y_test == 0)))
fn = int(np.sum((bin_preds == 0) & (y_test == 1)))

total_pos = int(np.sum(y_test == 1))
total_neg = int(np.sum(y_test == 0))

crack_recall = tp / total_pos if total_pos > 0 else 0.0
noncrack_specificity = tn / total_neg if total_neg > 0 else 0.0
overall_precision = tp / (tp + fp) if (tp + fp) > 0 else 0.0
overall_f1 = (2 * overall_precision * crack_recall) / (overall_precision + crack_recall) if (overall_precision + crack_recall) > 0 else 0.0

print("\n" + "=" * 80)
print(f"{'Category':30s} | {'Expected':18s} | {'Median Prob':12s} | {'Pass/Fail':10s}")
print("-" * 80)
for r in cat_results:
    print(f"{r['category']:30s} | {r['expected']:18s} | {r['median_probability']:<12.4f} | {r['status']:10s}")
print("=" * 80)

print("\n=== DISTRIBUTION METRICS ===")
print(f"- Minimum Crack Probability:        {min_crack_prob:.4f}")
print(f"- Median Crack Probability:         {median_crack_prob:.4f}")
print(f"- Maximum Crack Probability:        {max_crack_prob:.4f}")
print(f"- Median Non-Crack Probability:     {median_noncrack_prob:.4f}")
print(f"- Maximum Non-Crack Probability:     {max_noncrack_prob:.4f}")
print(f"- Class Probability Separation:     {prob_separation:.4f}")

print("\n=== OPERATING METRICS (Threshold = 0.65) ===")
print(f"- Crack Recall (Sensitivity):       {crack_recall * 100:.2f}% ({tp}/{total_pos})")
print(f"- Non-Crack Specificity:            {noncrack_specificity * 100:.2f}% ({tn}/{total_neg})")
print(f"- Precision:                        {overall_precision * 100:.2f}%")
print(f"- F1-Score:                         {overall_f1 * 100:.2f}%")
print(f"- False Positive Count:             {fp} / {total_neg}")
print(f"- False Negative Count:             {fn} / {total_pos}")

# 7. Save Machine-Readable Evaluation Report
eval_summary = {
    "model_name": "crack_model_v2.tflite",
    "evaluation_threshold": threshold_eval,
    "categories_evaluated": cat_results,
    "distribution_metrics": {
        "min_crack_probability": round(min_crack_prob, 4),
        "median_crack_probability": round(median_crack_prob, 4),
        "max_crack_probability": round(max_crack_prob, 4),
        "min_noncrack_probability": round(min_noncrack_prob, 4),
        "median_noncrack_probability": round(median_noncrack_prob, 4),
        "max_noncrack_probability": round(max_noncrack_prob, 4),
        "class_probability_separation": round(prob_separation, 4)
    },
    "operating_metrics": {
        "crack_recall": round(crack_recall, 4),
        "noncrack_specificity": round(noncrack_specificity, 4),
        "precision": round(overall_precision, 4),
        "f1_score": round(overall_f1, 4),
        "tp": tp,
        "fp": fp,
        "tn": tn,
        "fn": fn
    }
}

with open(EVAL_REPORT_PATH, "w") as f:
    json.dump(eval_summary, f, indent=2)

print(f"\nComprehensive report written to {EVAL_REPORT_PATH}")

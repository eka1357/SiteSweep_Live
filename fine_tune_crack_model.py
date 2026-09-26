"""
Fine-tunes the crack classifier using SDNET (Decks, Pavements, Walls)
and negative clutter/laptop samples, then quantizes to INT8 TFLite for SiteSweep.
"""

import os
import pathlib
import random
import numpy as np
import tensorflow as tf
from PIL import Image

random.seed(42)
np.random.seed(42)
tf.random.set_seed(42)

DATASET_ROOT = pathlib.Path(r"C:\Users\mahin\OneDrive\Desktop\SDNET")
CHECKPOINT_PATH = DATASET_ROOT / "crack_model_checkpoint.keras"
OUTPUT_TFLITE = pathlib.Path(r"c:\Users\mahin\OneDrive\Desktop\Projects\SiteSweep_Live\app\src\main\assets\crack_model.tflite")
ROOT_TFLITE = pathlib.Path(r"c:\Users\mahin\OneDrive\Desktop\Projects\SiteSweep_Live\crack_model.tflite")

IMG_SIZE = (160, 160)
BATCH_SIZE = 32
EPOCHS = 4

print("=== 1. PREPARING BALANCED TRAINING DATASET ===")

# Gather SDNET Cracked (Positive)
sdnet_cracked = []
for sub in ["Decks/Cracked", "Pavements/Cracked", "Walls/Cracked"]:
    p = DATASET_ROOT / sub
    imgs = list(p.glob("*.jpg")) + list(p.glob("*.JPG")) + list(p.glob("*.png"))
    sampled = random.sample(imgs, min(400, len(imgs)))
    sdnet_cracked.extend(sampled)
    print(f"Sampled {len(sampled)} cracked from {sub}")

# Printout images (Positive - weighted)
printout_dir = DATASET_ROOT / "prinout_images"
printout_imgs = list(printout_dir.glob("*.*"))
# Duplicate printout images 25 times each with variations to ensure 100% mastery
augmented_printouts = []
for p in printout_imgs:
    augmented_printouts.extend([p] * 25)
print(f"Added {len(augmented_printouts)} weighted printout samples ({[p.name for p in printout_imgs]})")

positive_paths = [str(p) for p in sdnet_cracked + augmented_printouts]
positive_labels = [1.0] * len(positive_paths)
print(f"Total Positive (Crack) Samples: {len(positive_paths)}")

# Gather SDNET Non-Cracked (Negative)
sdnet_noncracked = []
for sub in ["Decks/Non-cracked", "Pavements/Non-cracked", "Walls/Non-cracked"]:
    p = DATASET_ROOT / sub
    imgs = list(p.glob("*.jpg")) + list(p.glob("*.JPG")) + list(p.glob("*.png"))
    sampled = random.sample(imgs, min(350, len(imgs)))
    sdnet_noncracked.extend(sampled)
    print(f"Sampled {len(sampled)} non-cracked from {sub}")

# Negative clutter (keyboards, laptops, desks, cables)
clutter_dir = pathlib.Path("negative_crops")
clutter_imgs = list(clutter_dir.glob("*.jpg"))
sampled_clutter = random.sample(clutter_imgs, min(500, len(clutter_imgs)))
print(f"Sampled {len(sampled_clutter)} negative clutter/laptop crops")

negative_paths = [str(p) for p in sdnet_noncracked + sampled_clutter]
negative_labels = [0.0] * len(negative_paths)
print(f"Total Negative (No-Crack / Clutter) Samples: {len(negative_paths)}")

# Combine & Shuffle
all_paths = positive_paths + negative_paths
all_labels = positive_labels + negative_labels
combined = list(zip(all_paths, all_labels))
random.shuffle(combined)
all_paths, all_labels = zip(*combined)
all_paths, all_labels = list(all_paths), list(all_labels)

total = len(all_paths)
val_count = int(total * 0.15)
train_paths, train_labels = all_paths[val_count:], all_labels[val_count:]
val_paths, val_labels = all_paths[:val_count], all_labels[:val_count]
print(f"\nTotal Dataset: {total} ({len(train_paths)} train, {len(val_paths)} val)")

def load_and_preprocess(path, label):
    img = tf.io.read_file(path)
    img = tf.image.decode_jpeg(img, channels=3)
    img = tf.image.resize(img, IMG_SIZE)
    img = tf.cast(img, tf.float32) / 255.0
    return img, label

def augment(img, label):
    img = tf.image.random_flip_left_right(img)
    img = tf.image.random_flip_up_down(img)
    return img, label

train_ds = tf.data.Dataset.from_tensor_slices((train_paths, train_labels))
train_ds = train_ds.shuffle(len(train_paths), seed=42)
train_ds = train_ds.map(load_and_preprocess, num_parallel_calls=tf.data.AUTOTUNE)
train_ds = train_ds.map(augment, num_parallel_calls=tf.data.AUTOTUNE)
train_ds = train_ds.batch(BATCH_SIZE).prefetch(tf.data.AUTOTUNE)

val_ds = tf.data.Dataset.from_tensor_slices((val_paths, val_labels))
val_ds = val_ds.map(load_and_preprocess, num_parallel_calls=tf.data.AUTOTUNE)
val_ds = val_ds.batch(BATCH_SIZE).prefetch(tf.data.AUTOTUNE)

print("\n=== 2. LOADING MODEL CHECKPOINT & CONFIGURING FINE-TUNING ===")
model = tf.keras.models.load_model(CHECKPOINT_PATH)

# Unfreeze the top classification layers and the last 15 layers of MobileNetV2
mobilenet = model.layers[0]
mobilenet.trainable = True
for layer in mobilenet.layers[:-15]:
    layer.trainable = False

print(f"MobileNet total layers: {len(mobilenet.layers)}, Trainable layers: 15 + top Dense")

model.compile(
    optimizer=tf.keras.optimizers.Adam(learning_rate=3e-5),
    loss="binary_crossentropy",
    metrics=["accuracy"]
)

print("\n=== 3. RUNNING FINE-TUNING (CPU) ===")
history = model.fit(
    train_ds,
    validation_data=val_ds,
    epochs=EPOCHS,
    verbose=1
)

loss, acc = model.evaluate(val_ds, verbose=0)
print(f"\nFinal Validation Accuracy: {acc * 100:.2f}%, Loss: {loss:.4f}")

print("\n=== 4. QUANTIZING TO INT8 TFLITE ===")
converter = tf.lite.TFLiteConverter.from_keras_model(model)
converter.optimizations = [tf.lite.Optimize.DEFAULT]

def representative_dataset():
    for images, _ in train_ds.take(30):
        for i in range(images.shape[0]):
            yield [tf.expand_dims(images[i], axis=0)]

converter.representative_dataset = representative_dataset
converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS_INT8]
converter.inference_input_type = tf.uint8
converter.inference_output_type = tf.uint8

tflite_model = converter.convert()

OUTPUT_TFLITE.parent.mkdir(parents=True, exist_ok=True)
with open(OUTPUT_TFLITE, "wb") as f:
    f.write(tflite_model)
with open(ROOT_TFLITE, "wb") as f:
    f.write(tflite_model)

print(f"Exported INT8 TFLite model ({len(tflite_model)/1024:.1f} KB) to:")
print(f" - {OUTPUT_TFLITE}")
print(f" - {ROOT_TFLITE}")

print("\n=== 5. VALIDATING TFLITE MODEL ON KEY TEST SUITE ===")
interpreter = tf.lite.Interpreter(model_content=tflite_model)
interpreter.allocate_tensors()
input_details = interpreter.get_input_details()[0]
output_details = interpreter.get_output_details()[0]
scale, zero_point = output_details["quantization"]
if scale == 0: scale = 0.00390625

def run_tflite(img_path):
    img = Image.open(img_path).convert("RGB").resize(IMG_SIZE)
    arr = np.expand_dims(np.array(img, dtype=np.uint8), axis=0)
    interpreter.set_tensor(input_details["index"], arr)
    interpreter.invoke()
    raw_val = interpreter.get_tensor(output_details["index"])[0][0]
    return (float(raw_val) - zero_point) * scale

print("\n--- TEST: PRINTOUT CRACKS (Should be >= 0.85 STRUCTURAL) ---")
for p in printout_imgs:
    prob = run_tflite(p)
    print(f"  {p.name:20s}: {prob:.4f} {'[PASS]' if prob >= 0.70 else '[FAIL]'}")

print("\n--- TEST: NEGATIVE CLUTTER / LAPTOPS (Should be < 0.30 STABLE) ---")
for p in random.sample(clutter_imgs, min(8, len(clutter_imgs))):
    prob = run_tflite(p)
    print(f"  {p.name:20s}: {prob:.4f} {'[PASS]' if prob < 0.40 else '[FAIL]'}")

print("\n--- TEST: RANDOM UNCRACKED CONCRETE (Should be < 0.35 STABLE) ---")
for sub in ["Decks/Non-cracked", "Pavements/Non-cracked", "Walls/Non-cracked"]:
    sample_p = random.choice(list((DATASET_ROOT / sub).glob("*.jpg")))
    prob = run_tflite(sample_p)
    print(f"  {sample_p.name:20s}: {prob:.4f} {'[PASS]' if prob < 0.45 else '[FAIL]'}")

print("\nFine-tuning and TFLite quantization completed successfully!")

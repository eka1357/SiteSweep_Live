import json
import numpy as np

with open('evaluation_report_v2.json') as f:
    data = json.load(f)

print("=== DETAILED CATEGORY PROBABILITY PROFILES ===")
cats = data['categories_evaluated']
for c in cats:
    scores = np.array(c['all_scores'])
    p10 = np.percentile(scores, 10)
    p25 = np.percentile(scores, 25)
    p50 = np.percentile(scores, 50)
    p75 = np.percentile(scores, 75)
    p90 = np.percentile(scores, 90)
    p95 = np.percentile(scores, 95)
    mx = np.max(scores)
    mn = np.min(scores)
    print(f"{c['category']:28s} (N={len(scores):2d}) | min={mn:.3f} | p25={p25:.3f} | median={p50:.3f} | p75={p75:.3f} | p90={p90:.3f} | p95={p95:.3f} | max={mx:.3f}")

print("\n=== THRESHOLD SWEEP SIMULATION ===")
# Candidate ENTER_CRACK thresholds: 0.65, 0.70, 0.72, 0.75, 0.78, 0.80
# Candidate ENTER_STRUCTURAL thresholds: 0.85, 0.88, 0.90, 0.92, 0.95

# Let's gather all crack scores and non-crack scores
# Category 1 (Posters) & 2 (SDNET Cracked)
crack_scores = []
for c in [cats[0], cats[1]]:
    crack_scores.extend(c['all_scores'])
crack_scores = np.array(crack_scores)

# Hard negatives: Wood, Chair, Table, Furniture, Cables, Shadows, Walls
hard_neg_scores = []
for c in cats[3:]: # categories 4 through 11
    hard_neg_scores.extend(c['all_scores'])
hard_neg_scores = np.array(hard_neg_scores)

# Plain concrete (SDNET non-cracked)
concrete_neg_scores = np.array(cats[2]['all_scores'])

# Combined non-crack
all_neg_scores = np.concatenate([hard_neg_scores, concrete_neg_scores])

print(f"Total Crack Samples: {len(crack_scores)}")
print(f"Total Hard Negative Samples: {len(hard_neg_scores)}")
print(f"Total Plain Concrete Samples: {len(concrete_neg_scores)}")
print(f"Total All Non-Crack Samples: {len(all_neg_scores)}")

print("\n--- THRESHOLD SENSITIVITY TABLE ---")
print(f"{'Threshold':10s} | {'Crack Recall':14s} | {'Hard Neg Reject':17s} | {'Concrete Reject':17s} | {'All Neg Reject':16s}")
print("-" * 80)
for th in [0.60, 0.65, 0.70, 0.72, 0.75, 0.78, 0.80, 0.82, 0.85, 0.88, 0.90, 0.92, 0.95]:
    c_rec = np.mean(crack_scores >= th) * 100
    hn_rej = np.mean(hard_neg_scores < th) * 100
    conc_rej = np.mean(concrete_neg_scores < th) * 100
    all_rej = np.mean(all_neg_scores < th) * 100
    print(f"{th:<10.2f} | {c_rec:>12.1f}% | {hn_rej:>15.1f}% | {conc_rej:>15.1f}% | {all_rej:>14.1f}%")

print("\n--- INDIVIDUAL CATEGORY REJECTION RATES AT KEY THRESHOLDS ---")
key_thresholds = [0.65, 0.70, 0.75, 0.78, 0.82, 0.90]
for th in key_thresholds:
    print(f"\n>> At Threshold = {th:.2f}:")
    for c in cats:
        s = np.array(c['all_scores'])
        if "crack" in c['category'].lower() and "non" not in c['category'].lower() and "plain" not in c['category'].lower():
            # Crack category: rate of detection
            rate = np.mean(s >= th) * 100
            print(f"   {c['category']:28s}: {rate:5.1f}% detected as distress")
        else:
            # Non-crack category: rate of false positives
            fp_rate = np.mean(s >= th) * 100
            fp_count = np.sum(s >= th)
            print(f"   {c['category']:28s}: {fp_rate:5.1f}% FALSE ALARMS ({fp_count}/{len(s)})")

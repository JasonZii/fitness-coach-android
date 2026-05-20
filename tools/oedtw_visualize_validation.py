import csv
import json
from pathlib import Path

import matplotlib.pyplot as plt
import numpy as np


OE_DTW_MIN_FRAMES = 15
SCORE_Z_WEIGHT = 0.1

LEFT_SHOULDER = 11
RIGHT_SHOULDER = 12
LEFT_HIP = 23
RIGHT_HIP = 24


REFERENCE_CASES = [
    ("bicep_curl", "bicep_curl.json", 2),
    ("dumbbell_lateral_raise", "dumbbell_lateral_raise.json", 1),
    ("right_leg_lunge_to_knee_raise", "right_leg_lunge_to_knee_raise.json", 2),
    ("squat", "squat.json", 2),
    ("standing_dumbbell_shoulder_press", "standing_dumbbell_shoulder_press.json", 2),
]


def parse_reference_pose_json(json_path: Path, step: int):
    with json_path.open("r", encoding="utf-8") as f:
        data = json.load(f)
    frames = data["frames"]
    return [
        [
            (float(lm["x"]), float(lm["y"]), float(lm["z"]))
            for lm in frames[i]["landmarks"]
        ]
        for i in range(0, len(frames), step)
    ]


def normalize_landmarks(frame):
    hip_mid_x = (frame[LEFT_HIP][0] + frame[RIGHT_HIP][0]) / 2.0
    hip_mid_y = (frame[LEFT_HIP][1] + frame[RIGHT_HIP][1]) / 2.0
    hip_mid_z = (frame[LEFT_HIP][2] + frame[RIGHT_HIP][2]) / 2.0

    shoulder_mid_x = (frame[LEFT_SHOULDER][0] + frame[RIGHT_SHOULDER][0]) / 2.0
    shoulder_mid_y = (frame[LEFT_SHOULDER][1] + frame[RIGHT_SHOULDER][1]) / 2.0

    torso_length = np.sqrt((shoulder_mid_x - hip_mid_x) ** 2 + (shoulder_mid_y - hip_mid_y) ** 2)
    return [
        (
            (x - hip_mid_x) / torso_length,
            (y - hip_mid_y) / torso_length,
            (z - hip_mid_z) / torso_length,
        )
        for x, y, z in frame
    ]


def frame_dist(a, b):
    total = 0.0
    for p, q in zip(a, b):
        dx = p[0] - q[0]
        dy = p[1] - q[1]
        dz = (p[2] - q[2]) * SCORE_Z_WEIGHT
        total += np.sqrt(dx * dx + dy * dy + dz * dz)
    return total / len(a)


def align_oe_dtw(user_sequence, reference_sequence):
    if len(user_sequence) < OE_DTW_MIN_FRAMES:
        return -1

    m = len(reference_sequence)
    dp = np.zeros(m, dtype=np.float32)
    dp[0] = frame_dist(user_sequence[0], reference_sequence[0])
    for j in range(1, m):
        dp[j] = dp[j - 1] + frame_dist(user_sequence[0], reference_sequence[j])

    for i in range(1, len(user_sequence)):
        new_dp = np.zeros(m, dtype=np.float32)
        for j in range(m):
            best = dp[0] if j == 0 else min(dp[j - 1], dp[j], new_dp[j - 1])
            new_dp[j] = frame_dist(user_sequence[i], reference_sequence[j]) + best
        dp = new_dp

    return int(np.argmin(dp))


def align_oe_dtw_with_cost(user_sequence, reference_sequence):
    if len(user_sequence) < OE_DTW_MIN_FRAMES:
        return -1, np.nan

    m = len(reference_sequence)
    dp = np.zeros(m, dtype=np.float32)
    dp[0] = frame_dist(user_sequence[0], reference_sequence[0])
    for j in range(1, m):
        dp[j] = dp[j - 1] + frame_dist(user_sequence[0], reference_sequence[j])

    for i in range(1, len(user_sequence)):
        new_dp = np.zeros(m, dtype=np.float32)
        for j in range(m):
            best = dp[0] if j == 0 else min(dp[j - 1], dp[j], new_dp[j - 1])
            new_dp[j] = frame_dist(user_sequence[i], reference_sequence[j]) + best
        dp = new_dp

    index = int(np.argmin(dp))
    return index, float(dp[index] / len(user_sequence))


def drop_every_nth_interior_frame(sequence, n):
    return [
        frame
        for i, frame in enumerate(sequence)
        if i == 0 or i == len(sequence) - 1 or i % n != 0
    ]


def load_sequences(landmark_dir: Path):
    result = {}
    for exercise_id, file_name, step in REFERENCE_CASES:
        raw = parse_reference_pose_json(landmark_dir / file_name, step)
        result[exercise_id] = [normalize_landmarks(frame) for frame in raw]
    return result


def plot_prefix_tracking(exercise_id, sequence, out_dir: Path):
    lengths = list(range(1, len(sequence) + 1))
    actual = [align_oe_dtw(sequence[:length], sequence) for length in lengths]
    ideal = [-1 if length < OE_DTW_MIN_FRAMES else length - 1 for length in lengths]

    fig, ax = plt.subplots(figsize=(12, 7))
    ax.plot(lengths, ideal, "r--", linewidth=2, label="Ideal index = user length - 1")
    ax.plot(lengths, actual, "bo-", markersize=3, linewidth=1.8, label="Actual alignOeDtw output")
    ax.axhline(-1, color="0.65", linestyle=":", linewidth=1.5, label="Warm-up output (-1)")
    ax.axvspan(0, OE_DTW_MIN_FRAMES, color="0.90", label="Warm-up period")
    ax.set_title(f"OE-DTW prefix tracking - {exercise_id} ({len(sequence)} frames)")
    ax.set_xlabel("User sequence length")
    ax.set_ylabel("Matched reference index")
    ax.grid(alpha=0.3)
    ax.legend(loc="upper left")
    fig.tight_layout()

    out_path = out_dir / f"{exercise_id}_prefix_tracking.png"
    fig.savefig(out_path, dpi=180)
    plt.close(fig)
    return out_path


def plot_dropped_frame_tracking(exercise_id, sequence, out_dir: Path):
    chunk = max(10, len(sequence) // 6)
    expected = []
    clean = []
    dropped = []
    dropped_sizes = []

    for end_exclusive in range(OE_DTW_MIN_FRAMES + 5, len(sequence) + 1, chunk):
        clean_clip = sequence[:end_exclusive]
        dropped_clip = drop_every_nth_interior_frame(clean_clip, 5)
        expected_end = end_exclusive - 1

        expected.append(expected_end)
        clean.append(align_oe_dtw(clean_clip, sequence))
        dropped.append(align_oe_dtw(dropped_clip, sequence))
        dropped_sizes.append(len(dropped_clip))

    fig, ax = plt.subplots(figsize=(12, 7))
    ax.plot(expected, expected, "r--", linewidth=2, label="Ideal matched index")
    ax.plot(expected, clean, "go-", linewidth=1.8, label="Clean clip")
    ax.plot(expected, dropped, "bo-", linewidth=1.8, label="Clip after dropping every 5th interior frame")
    for x, y, size in zip(expected, dropped, dropped_sizes):
        ax.annotate(f"{size}f", (x, y), textcoords="offset points", xytext=(4, -12), fontsize=8)
    ax.set_title(f"OE-DTW dropped-frame clip tracking - {exercise_id}")
    ax.set_xlabel("Original clip end frame")
    ax.set_ylabel("Matched reference index")
    ax.grid(alpha=0.3)
    ax.legend(loc="upper left")
    fig.tight_layout()

    out_path = out_dir / f"{exercise_id}_dropped_frame_tracking.png"
    fig.savefig(out_path, dpi=180)
    plt.close(fig)
    return out_path


def plot_cost_matrix(sequences, out_dir: Path):
    names = list(sequences.keys())
    matrix = np.zeros((len(names), len(names)), dtype=np.float32)

    for i, user_name in enumerate(names):
        for j, ref_name in enumerate(names):
            _, avg_cost = align_oe_dtw_with_cost(sequences[user_name], sequences[ref_name])
            matrix[i, j] = avg_cost

    fig, ax = plt.subplots(figsize=(12, 9))
    im = ax.imshow(matrix, cmap="viridis")
    ax.set_title("OE-DTW cross-reference average cost matrix")
    ax.set_xlabel("Reference sequence")
    ax.set_ylabel("User sequence")
    ax.set_xticks(range(len(names)), labels=names, rotation=35, ha="right")
    ax.set_yticks(range(len(names)), labels=names)

    for i in range(len(names)):
        for j in range(len(names)):
            color = "white" if matrix[i, j] > matrix.max() * 0.55 else "black"
            ax.text(j, i, f"{matrix[i, j]:.3f}", ha="center", va="center", color=color, fontsize=9)

    fig.colorbar(im, ax=ax, label="Average OE-DTW cost")
    fig.tight_layout()

    out_path = out_dir / "cross_reference_cost_matrix.png"
    fig.savefig(out_path, dpi=180)
    plt.close(fig)

    csv_path = out_dir / "cross_reference_cost_matrix.csv"
    with csv_path.open("w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["user\\reference", *names])
        for name, row in zip(names, matrix):
            writer.writerow([name, *[f"{value:.6f}" for value in row]])

    return out_path, csv_path


def main():
    repo = Path(__file__).resolve().parents[1]
    landmark_dir = repo / "app" / "src" / "main" / "assets" / "landmarks"
    out_dir = repo / "build" / "oedtw-visualization"
    out_dir.mkdir(parents=True, exist_ok=True)

    sequences = load_sequences(landmark_dir)
    generated = []
    for exercise_id, sequence in sequences.items():
        generated.append(plot_prefix_tracking(exercise_id, sequence, out_dir))
        generated.append(plot_dropped_frame_tracking(exercise_id, sequence, out_dir))

    cost_png, cost_csv = plot_cost_matrix(sequences, out_dir)
    generated.append(cost_png)

    print(f"output_dir={out_dir}")
    for path in generated:
        print(path)
    print(cost_csv)


if __name__ == "__main__":
    main()

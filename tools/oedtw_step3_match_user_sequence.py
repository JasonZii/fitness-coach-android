import argparse
import csv
import json
from pathlib import Path

import numpy as np


OE_DTW_MIN_FRAMES = 15
SCORE_Z_WEIGHT = 0.1

LEFT_SHOULDER = 11
RIGHT_SHOULDER = 12
LEFT_HIP = 23
RIGHT_HIP = 24


def parse_reference_sequence(json_path: Path, step: int):
    with json_path.open("r", encoding="utf-8") as f:
        frames = json.load(f)["frames"]
    sequence = []
    for i in range(0, len(frames), step):
        sequence.append(
            [
                (float(lm["x"]), float(lm["y"]), float(lm["z"]))
                for lm in frames[i]["landmarks"]
            ]
        )
    return [normalize_landmarks(frame) for frame in sequence]


def parse_user_sequence(json_path: Path):
    with json_path.open("r", encoding="utf-8") as f:
        frames = json.load(f)["frames"]
    return [
        normalize_landmarks(
            [
                (float(lm["x"]), float(lm["y"]), float(lm["z"]))
                for lm in frame["landmarks"]
            ]
        )
        for frame in frames
    ]


def normalize_landmarks(frame):
    hip_mid_x = (frame[LEFT_HIP][0] + frame[RIGHT_HIP][0]) / 2.0
    hip_mid_y = (frame[LEFT_HIP][1] + frame[RIGHT_HIP][1]) / 2.0
    hip_mid_z = (frame[LEFT_HIP][2] + frame[RIGHT_HIP][2]) / 2.0

    shoulder_mid_x = (frame[LEFT_SHOULDER][0] + frame[RIGHT_SHOULDER][0]) / 2.0
    shoulder_mid_y = (frame[LEFT_SHOULDER][1] + frame[RIGHT_SHOULDER][1]) / 2.0
    torso_length = np.sqrt((shoulder_mid_x - hip_mid_x) ** 2 + (shoulder_mid_y - hip_mid_y) ** 2)

    if torso_length < 1e-6:
        raise ValueError("Invalid pose frame: torso length is near zero")

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


def align_oe_dtw_with_cost(user_sequence, reference_sequence):
    if len(user_sequence) < OE_DTW_MIN_FRAMES:
        return -1, float("nan")

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
    average_cost = float(dp[index] / len(user_sequence))
    return index, average_cost


def main():
    parser = argparse.ArgumentParser(description="Step 3: match MediaPipe-generated user sequences against full references with OE-DTW.")
    parser.add_argument("--repo", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--out", type=Path, default=None)
    parser.add_argument("--tolerance", type=int, default=3)
    args = parser.parse_args()

    repo = args.repo.resolve()
    out_dir = args.out or (repo / "build" / "oedtw-report-validation")
    step2_summary = out_dir / "step2_mediapipe_extraction_summary.csv"
    step3_summary = out_dir / "step3_oedtw_match_summary.csv"

    with step2_summary.open("r", encoding="utf-8") as f:
        rows = list(csv.DictReader(f))

    output_rows = []
    for row in rows:
        reference_sequence = parse_reference_sequence(Path(row["reference_json"]), int(row["downsample_step"]))
        user_sequence = parse_user_sequence(Path(row["user_json"]))
        matched_index, average_cost = align_oe_dtw_with_cost(user_sequence, reference_sequence)
        expected_index = int(row["expected_reference_index"])
        index_error = matched_index - expected_index if matched_index != -1 else ""
        passed = matched_index != -1 and abs(index_error) <= args.tolerance

        output = dict(row)
        output.update(
            {
                "reference_sequence_frames": len(reference_sequence),
                "user_sequence_frames": len(user_sequence),
                "matched_reference_index": matched_index,
                "index_error": index_error,
                "average_cost": f"{average_cost:.6f}",
                "tolerance_frames": args.tolerance,
                "pass_within_tolerance": passed,
            }
        )
        output_rows.append(output)
        print(
            f"{row['exercise_id']} {row['segment_id']}: "
            f"expected={expected_index}, matched={matched_index}, "
            f"error={index_error}, cost={average_cost:.4f}, pass={passed}"
        )

    with step3_summary.open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=list(output_rows[0].keys()))
        writer.writeheader()
        writer.writerows(output_rows)

    passed_count = sum(1 for row in output_rows if row["pass_within_tolerance"])
    print(f"summary={step3_summary}")
    print(f"passed={passed_count}/{len(output_rows)}")


if __name__ == "__main__":
    main()

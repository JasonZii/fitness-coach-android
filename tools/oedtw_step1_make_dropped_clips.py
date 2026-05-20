import argparse
import csv
import json
from pathlib import Path

import cv2


CASES = [
    ("bicep_curl", "bicep_curl.mp4", "bicep_curl.json", 2),
    ("dumbbell_lateral_raise", "dumbbell_lateral_raise.mp4", "dumbbell_lateral_raise.json", 1),
    ("right_leg_lunge_to_knee_raise", "right_leg_lunge_to_knee_raise.mp4", "right_leg_lunge_to_knee_raise.json", 2),
    ("squat", "squat.mp4", "squat.json", 2),
    ("standing_dumbbell_shoulder_press", "standing_dumbbell_shoulder_press.mp4", "standing_dumbbell_shoulder_press.json", 2),
]


def json_frame_indices(json_path: Path, step: int) -> list[int]:
    with json_path.open("r", encoding="utf-8") as f:
        frames = json.load(f)["frames"]
    indices = []
    for i in range(0, len(frames), step):
        indices.append(int(frames[i].get("frame_index", i)))
    return indices


def prefix_ends(reference_length: int) -> list[int]:
    candidates = [
        max(20, reference_length // 3),
        max(20, (reference_length * 2) // 3),
        reference_length,
    ]
    result = []
    for value in candidates:
        value = min(reference_length, value)
        if value >= 15 and value not in result:
            result.append(value)
    return result


def drop_every_nth_interior(indices: list[int], n: int) -> list[int]:
    kept = []
    for position, value in enumerate(indices):
        keep = position == 0 or position == len(indices) - 1 or position % n != 0
        if keep:
            kept.append(value)
    return kept


def read_frame(cap: cv2.VideoCapture, frame_index: int):
    cap.set(cv2.CAP_PROP_POS_FRAMES, frame_index)
    ok, frame = cap.read()
    if not ok:
        raise RuntimeError(f"Could not read frame {frame_index}")
    return frame


def write_clip(source_video: Path, output_video: Path, source_frame_indices: list[int], fps: float):
    cap = cv2.VideoCapture(str(source_video))
    if not cap.isOpened():
        raise RuntimeError(f"Cannot open video: {source_video}")

    width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    output_video.parent.mkdir(parents=True, exist_ok=True)
    writer = cv2.VideoWriter(
        str(output_video),
        cv2.VideoWriter_fourcc(*"mp4v"),
        fps,
        (width, height),
    )

    for frame_index in source_frame_indices:
        writer.write(read_frame(cap, frame_index))

    writer.release()
    cap.release()


def main():
    parser = argparse.ArgumentParser(description="Step 1: create dropped-frame reference clips.")
    parser.add_argument("--repo", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--out", type=Path, default=None)
    parser.add_argument("--reference-json-dir", type=Path, default=None)
    parser.add_argument("--drop-n", type=int, default=5)
    args = parser.parse_args()

    repo = args.repo.resolve()
    out_dir = args.out or (repo / "build" / "oedtw-report-validation")
    reference_json_dir = args.reference_json_dir or (repo / "app" / "src" / "main" / "assets" / "landmarks")
    clip_dir = out_dir / "step1_dropped_clips"
    manifest_path = out_dir / "step1_dropped_clip_manifest.csv"
    out_dir.mkdir(parents=True, exist_ok=True)

    rows = []
    for exercise_id, video_name, json_name, downsample_step in CASES:
        video_path = repo / "app" / "src" / "main" / "res" / "raw" / video_name
        json_path = reference_json_dir / json_name
        source_indices = json_frame_indices(json_path, downsample_step)

        cap = cv2.VideoCapture(str(video_path))
        if not cap.isOpened():
            raise RuntimeError(f"Cannot open video: {video_path}")
        source_fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
        cap.release()
        clip_fps = source_fps / downsample_step

        for end_exclusive in prefix_ends(len(source_indices)):
            clean_algorithm_indices = list(range(end_exclusive))
            kept_algorithm_indices = drop_every_nth_interior(clean_algorithm_indices, args.drop_n)
            kept_source_indices = [source_indices[i] for i in kept_algorithm_indices]
            expected_reference_index = kept_algorithm_indices[-1]
            segment_id = f"prefix_{end_exclusive:04d}_drop_every_{args.drop_n}"
            clip_path = clip_dir / exercise_id / f"{exercise_id}_{segment_id}.mp4"

            write_clip(video_path, clip_path, kept_source_indices, clip_fps)

            rows.append(
                {
                    "exercise_id": exercise_id,
                    "segment_id": segment_id,
                    "source_video": str(video_path),
                    "reference_json": str(json_path),
                    "clip_video": str(clip_path),
                    "downsample_step": downsample_step,
                    "source_fps": f"{source_fps:.6f}",
                    "clip_fps": f"{clip_fps:.6f}",
                    "clean_algorithm_frame_count": len(clean_algorithm_indices),
                    "kept_algorithm_frame_count": len(kept_algorithm_indices),
                    "expected_reference_index": expected_reference_index,
                    "drop_rule": f"drop every {args.drop_n}th interior algorithm frame; keep first and last",
                    "kept_algorithm_indices": ";".join(map(str, kept_algorithm_indices)),
                    "kept_source_frame_indices": ";".join(map(str, kept_source_indices)),
                }
            )

    with manifest_path.open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        writer.writeheader()
        writer.writerows(rows)

    print(f"manifest={manifest_path}")
    print(f"clips={clip_dir}")
    print(f"created_clips={len(rows)}")


if __name__ == "__main__":
    main()

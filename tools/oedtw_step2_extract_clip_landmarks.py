import argparse
import csv
import json
from pathlib import Path

import cv2
import mediapipe as mp
from mediapipe.tasks import python as mp_python
from mediapipe.tasks.python import vision


def create_landmarker(model_path: Path):
    options = vision.PoseLandmarkerOptions(
        base_options=mp_python.BaseOptions(model_asset_path=str(model_path)),
        running_mode=vision.RunningMode.VIDEO,
        num_poses=1,
        min_pose_detection_confidence=0.5,
        min_pose_presence_confidence=0.5,
        min_tracking_confidence=0.6,
    )
    return vision.PoseLandmarker.create_from_options(options)


def extract_clip(clip_video: Path, output_json: Path, landmarker):
    cap = cv2.VideoCapture(str(clip_video))
    if not cap.isOpened():
        raise RuntimeError(f"Cannot open clip: {clip_video}")

    fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
    frames = []
    total_frames = 0
    detected_frames = 0

    while True:
        ok, frame = cap.read()
        if not ok:
            break

        rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb)
        timestamp_ms = int((total_frames / fps) * 1000)
        result = landmarker.detect_for_video(mp_image, timestamp_ms)

        if result.pose_landmarks:
            landmarks = []
            visibilities = []
            for lm in result.pose_landmarks[0]:
                landmarks.append({"x": lm.x, "y": lm.y, "z": lm.z})
                visibility = getattr(lm, "visibility", 0.0)
                visibilities.append(float(visibility or 0.0))
            frames.append(
                {
                    "frame_index": total_frames,
                    "landmarks": landmarks,
                    "visibilities": visibilities,
                }
            )
            detected_frames += 1

        total_frames += 1

    cap.release()
    output_json.parent.mkdir(parents=True, exist_ok=True)
    with output_json.open("w", encoding="utf-8") as f:
        json.dump({"frames": frames}, f, indent=2)

    return total_frames, detected_frames, fps


def main():
    parser = argparse.ArgumentParser(description="Step 2: extract MediaPipe landmarks from dropped-frame clips.")
    parser.add_argument("--repo", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--out", type=Path, default=None)
    parser.add_argument("--model", type=Path, default=None)
    args = parser.parse_args()

    repo = args.repo.resolve()
    out_dir = args.out or (repo / "build" / "oedtw-report-validation")
    model_path = args.model or (repo / "app" / "src" / "main" / "assets" / "pose_landmarker_full.task")
    manifest_path = out_dir / "step1_dropped_clip_manifest.csv"
    summary_path = out_dir / "step2_mediapipe_extraction_summary.csv"
    user_json_dir = out_dir / "step2_user_landmarks"

    with manifest_path.open("r", encoding="utf-8") as f:
        manifest_rows = list(csv.DictReader(f))

    rows = []
    for manifest in manifest_rows:
        clip_video = Path(manifest["clip_video"])
        output_json = (
            user_json_dir
            / manifest["exercise_id"]
            / f"{Path(manifest['clip_video']).stem}_user_landmarks.json"
        )
        # Each clip is treated as an independent user video, so its VIDEO-mode
        # timestamps start at 0. Create a fresh landmarker per clip to avoid
        # MediaPipe's monotonic timestamp requirement across separate clips.
        with create_landmarker(model_path) as landmarker:
            total_frames, detected_frames, fps = extract_clip(clip_video, output_json, landmarker)
        row = dict(manifest)
        row.update(
            {
                "user_json": str(output_json),
                "clip_total_frames": total_frames,
                "mediapipe_detected_frames": detected_frames,
                "clip_decode_fps": f"{fps:.6f}",
                "all_clip_frames_detected": detected_frames == total_frames,
            }
        )
        rows.append(row)
        print(
            f"{manifest['exercise_id']} {manifest['segment_id']}: "
            f"detected={detected_frames}/{total_frames}, user_json={output_json}"
        )

    with summary_path.open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        writer.writeheader()
        writer.writerows(rows)

    print(f"summary={summary_path}")
    print(f"user_json_dir={user_json_dir}")


if __name__ == "__main__":
    main()

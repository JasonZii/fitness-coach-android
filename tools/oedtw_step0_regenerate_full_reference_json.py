import argparse
import csv
import json
from pathlib import Path

import cv2
import mediapipe as mp
from mediapipe.tasks import python as mp_python
from mediapipe.tasks.python import vision


CASES = [
    ("bicep_curl", "bicep_curl.mp4", "bicep_curl.json"),
    ("dumbbell_lateral_raise", "dumbbell_lateral_raise.mp4", "dumbbell_lateral_raise.json"),
    ("right_leg_lunge_to_knee_raise", "right_leg_lunge_to_knee_raise.mp4", "right_leg_lunge_to_knee_raise.json"),
    ("squat", "squat.mp4", "squat.json"),
    ("standing_dumbbell_shoulder_press", "standing_dumbbell_shoulder_press.mp4", "standing_dumbbell_shoulder_press.json"),
]


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


def extract_video(video_path: Path, output_json: Path, model_path: Path):
    cap = cv2.VideoCapture(str(video_path))
    if not cap.isOpened():
        raise RuntimeError(f"Cannot open video: {video_path}")

    fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
    frames = []
    total_frames = 0
    detected_frames = 0

    with create_landmarker(model_path) as landmarker:
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
    parser = argparse.ArgumentParser(description="Step 0: regenerate reference landmark JSON files using the app's full MediaPipe model.")
    parser.add_argument("--repo", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--out", type=Path, default=None)
    parser.add_argument("--model", type=Path, default=None)
    args = parser.parse_args()

    repo = args.repo.resolve()
    out_dir = args.out or (repo / "build" / "oedtw-full-reference-json")
    model_path = args.model or (repo / "app" / "src" / "main" / "assets" / "pose_landmarker_full.task")
    output_json_dir = out_dir / "landmarks"
    summary_path = out_dir / "step0_full_reference_generation_summary.csv"
    rows = []

    for exercise_id, video_name, json_name in CASES:
        video_path = repo / "app" / "src" / "main" / "res" / "raw" / video_name
        output_json = output_json_dir / json_name
        total_frames, detected_frames, fps = extract_video(video_path, output_json, model_path)
        rows.append(
            {
                "exercise_id": exercise_id,
                "source_video": str(video_path),
                "output_json": str(output_json),
                "model": str(model_path),
                "fps": f"{fps:.6f}",
                "total_video_frames": total_frames,
                "mediapipe_detected_frames": detected_frames,
                "all_frames_detected": detected_frames == total_frames,
            }
        )
        print(
            f"{exercise_id}: detected={detected_frames}/{total_frames}, "
            f"output={output_json}"
        )

    out_dir.mkdir(parents=True, exist_ok=True)
    with summary_path.open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        writer.writeheader()
        writer.writerows(rows)

    print(f"summary={summary_path}")
    print(f"generated_json_dir={output_json_dir}")


if __name__ == "__main__":
    main()

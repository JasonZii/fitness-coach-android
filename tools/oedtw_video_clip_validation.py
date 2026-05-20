import argparse
import csv
import json
from pathlib import Path

import cv2
import numpy as np


def load_json_frame_indices(json_path: Path) -> list[int]:
    with json_path.open("r", encoding="utf-8") as f:
        data = json.load(f)
    return [int(frame.get("frame_index", i)) for i, frame in enumerate(data["frames"])]


def read_frame(cap: cv2.VideoCapture, frame_index: int):
    cap.set(cv2.CAP_PROP_POS_FRAMES, frame_index)
    ok, frame = cap.read()
    if not ok:
        raise RuntimeError(f"Could not read frame {frame_index}")
    return frame


def write_clip(video_path: Path, out_path: Path, start_frame: int, end_frame: int, fps: float, size: tuple[int, int]) -> int:
    cap = cv2.VideoCapture(str(video_path))
    if not cap.isOpened():
        raise RuntimeError(f"Cannot open video: {video_path}")

    out_path.parent.mkdir(parents=True, exist_ok=True)
    writer = cv2.VideoWriter(
        str(out_path),
        cv2.VideoWriter_fourcc(*"mp4v"),
        fps,
        size,
    )

    written = 0
    cap.set(cv2.CAP_PROP_POS_FRAMES, start_frame)
    for _ in range(start_frame, end_frame + 1):
        ok, frame = cap.read()
        if not ok:
            break
        writer.write(frame)
        written += 1

    writer.release()
    cap.release()
    return written


def mae(a: np.ndarray, b: np.ndarray) -> float:
    return float(np.mean(np.abs(a.astype(np.float32) - b.astype(np.float32))))


def max_abs(a: np.ndarray, b: np.ndarray) -> float:
    return float(np.max(np.abs(a.astype(np.float32) - b.astype(np.float32))))


def validate_clip_frames(
    source_video: Path,
    clip_video: Path,
    start_frame: int,
    clip_frame_count: int,
    sample_offsets: list[int],
    frames_dir: Path,
):
    source_cap = cv2.VideoCapture(str(source_video))
    clip_cap = cv2.VideoCapture(str(clip_video))
    rows = []

    for offset in sample_offsets:
        if offset < 0 or offset >= clip_frame_count:
            continue

        source_index = start_frame + offset
        source_frame = read_frame(source_cap, source_index)
        clip_frame = read_frame(clip_cap, offset)

        if source_frame.shape != clip_frame.shape:
            clip_frame = cv2.resize(clip_frame, (source_frame.shape[1], source_frame.shape[0]))

        frames_dir.mkdir(parents=True, exist_ok=True)
        source_png = frames_dir / f"source_frame_{source_index:04d}.png"
        clip_png = frames_dir / f"clip_offset_{offset:04d}.png"
        cv2.imwrite(str(source_png), source_frame)
        cv2.imwrite(str(clip_png), clip_frame)

        rows.append(
            {
                "source_frame": source_index,
                "clip_offset": offset,
                "mean_abs_pixel_diff": mae(source_frame, clip_frame),
                "max_abs_pixel_diff": max_abs(source_frame, clip_frame),
                "source_png": str(source_png),
                "clip_png": str(clip_png),
            }
        )

    source_cap.release()
    clip_cap.release()
    return rows


def choose_segments(frame_indices: list[int], clip_len: int) -> list[tuple[str, int, int]]:
    first = min(frame_indices)
    last = max(frame_indices)
    mid = (first + last) // 2
    starts = [
        ("start", first),
        ("middle", max(first, mid - clip_len // 2)),
        ("end", max(first, last - clip_len + 1)),
    ]

    segments = []
    seen = set()
    for name, start in starts:
        end = min(start + clip_len - 1, last)
        if (start, end) not in seen and end >= start:
            segments.append((name, start, end))
            seen.add((start, end))
    return segments


def main():
    parser = argparse.ArgumentParser(description="Cut reference video clips, extract frames, and compare clip frames to source frames.")
    parser.add_argument("--video", required=True, type=Path)
    parser.add_argument("--json", required=True, type=Path)
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument("--clip-len", type=int, default=15)
    args = parser.parse_args()

    cap = cv2.VideoCapture(str(args.video))
    if not cap.isOpened():
        raise RuntimeError(f"Cannot open video: {args.video}")

    fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
    width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    cap.release()

    frame_indices = load_json_frame_indices(args.json)
    segments = choose_segments(frame_indices, args.clip_len)
    args.out.mkdir(parents=True, exist_ok=True)

    summary_rows = []
    for name, start, end in segments:
        clip_path = args.out / "clips" / f"{args.video.stem}_{name}_{start:04d}_{end:04d}.mp4"
        written = write_clip(args.video, clip_path, start, end, fps, (width, height))
        sample_offsets = sorted(set([0, written // 2, written - 1]))
        frame_rows = validate_clip_frames(
            args.video,
            clip_path,
            start,
            written,
            sample_offsets,
            args.out / "frames" / name,
        )

        for row in frame_rows:
            row.update(
                {
                    "segment": name,
                    "clip_path": str(clip_path),
                    "clip_start_frame": start,
                    "clip_end_frame": end,
                    "clip_frames_written": written,
                }
            )
            summary_rows.append(row)

    csv_path = args.out / "clip_frame_comparison.csv"
    with csv_path.open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=list(summary_rows[0].keys()))
        writer.writeheader()
        writer.writerows(summary_rows)

    print(f"video={args.video}")
    print(f"fps={fps:.3f}, size={width}x{height}, total_frames={total_frames}")
    print(f"json_detected_frames={len(frame_indices)}, json_frame_range={min(frame_indices)}..{max(frame_indices)}")
    print(f"segments={segments}")
    print(f"summary_csv={csv_path}")
    for row in summary_rows:
        print(
            f"{row['segment']} source_frame={row['source_frame']} clip_offset={row['clip_offset']} "
            f"mae={row['mean_abs_pixel_diff']:.3f} max={row['max_abs_pixel_diff']:.1f}"
        )


if __name__ == "__main__":
    main()

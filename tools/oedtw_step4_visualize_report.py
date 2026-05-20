import argparse
import csv
from pathlib import Path

import matplotlib.pyplot as plt
import numpy as np


def read_rows(csv_path: Path):
    with csv_path.open("r", encoding="utf-8") as f:
        return list(csv.DictReader(f))


def as_bool(value: str) -> bool:
    return value.lower() == "true"


def plot_expected_vs_matched(rows, out_dir: Path):
    fig, ax = plt.subplots(figsize=(11, 8))
    exercises = sorted({row["exercise_id"] for row in rows})
    colors = plt.cm.tab10(np.linspace(0, 1, len(exercises)))
    color_map = dict(zip(exercises, colors))

    max_index = max(
        max(int(row["expected_reference_index"]), int(row["matched_reference_index"]))
        for row in rows
    )
    ax.plot([0, max_index], [0, max_index], "r--", linewidth=2, label="Ideal: matched = expected")

    for exercise in exercises:
        group = [row for row in rows if row["exercise_id"] == exercise]
        x = [int(row["expected_reference_index"]) for row in group]
        y = [int(row["matched_reference_index"]) for row in group]
        ax.scatter(x, y, s=80, label=exercise, color=color_map[exercise])
        for row in group:
            ax.annotate(
                row["segment_id"].replace("prefix_", "p").replace("_drop_every_", "_d"),
                (int(row["expected_reference_index"]), int(row["matched_reference_index"])),
                textcoords="offset points",
                xytext=(5, 5),
                fontsize=7,
            )

    ax.set_title("Step 4: OE-DTW matched index from dropped-frame MediaPipe clips")
    ax.set_xlabel("Expected reference index from original clip position")
    ax.set_ylabel("Matched reference index returned by OE-DTW")
    ax.grid(alpha=0.3)
    ax.legend(loc="best", fontsize=8)
    fig.tight_layout()

    out_path = out_dir / "step4_expected_vs_matched.png"
    fig.savefig(out_path, dpi=180)
    plt.close(fig)
    return out_path


def plot_index_error(rows, out_dir: Path):
    labels = [f"{row['exercise_id']}\n{row['segment_id']}" for row in rows]
    errors = [int(row["index_error"]) for row in rows]
    passed = [as_bool(row["pass_within_tolerance"]) for row in rows]
    colors = ["#2ca02c" if ok else "#d62728" for ok in passed]

    fig, ax = plt.subplots(figsize=(14, 7))
    ax.bar(range(len(rows)), errors, color=colors)
    tolerance = int(rows[0]["tolerance_frames"])
    ax.axhline(tolerance, color="r", linestyle="--", linewidth=1.5, label=f"+/-{tolerance} frame tolerance")
    ax.axhline(-tolerance, color="r", linestyle="--", linewidth=1.5)
    ax.axhline(0, color="0.25", linewidth=1)
    ax.set_title("Step 4: OE-DTW index error after MediaPipe re-extraction")
    ax.set_ylabel("matchedReferenceIndex - expectedReferenceIndex")
    ax.set_xticks(range(len(rows)), labels=labels, rotation=70, ha="right", fontsize=7)
    ax.legend()
    ax.grid(axis="y", alpha=0.25)
    fig.tight_layout()

    out_path = out_dir / "step4_index_error_by_clip.png"
    fig.savefig(out_path, dpi=180)
    plt.close(fig)
    return out_path


def write_markdown_summary(rows, out_dir: Path, generated_images):
    passed = sum(1 for row in rows if as_bool(row["pass_within_tolerance"]))
    total = len(rows)
    max_abs_error = max(abs(int(row["index_error"])) for row in rows)
    mean_abs_error = sum(abs(int(row["index_error"])) for row in rows) / total

    lines = [
        "# OE-DTW Dropped-Frame Clip Validation Summary",
        "",
        "This report validates the full pipeline:",
        "",
        "1. Full reference video is converted into dropped-frame clips.",
        "2. MediaPipe extracts a new user landmark sequence from each dropped-frame clip.",
        "3. OE-DTW matches the generated user sequence against the full reference sequence.",
        "4. The matched reference index is compared with the clip's expected original position.",
        "",
        f"- Total clips tested: {total}",
        f"- Passed within tolerance: {passed}/{total}",
        f"- Mean absolute index error: {mean_abs_error:.2f} frames",
        f"- Max absolute index error: {max_abs_error} frames",
        f"- Tolerance: +/-{rows[0]['tolerance_frames']} frames",
        "",
        "Generated figures:",
        "",
    ]
    for image in generated_images:
        lines.append(f"- {image.name}")

    out_path = out_dir / "step4_validation_summary.md"
    out_path.write_text("\n".join(lines), encoding="utf-8")
    return out_path


def main():
    parser = argparse.ArgumentParser(description="Step 4: visualize OE-DTW dropped-frame clip validation results.")
    parser.add_argument("--repo", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--out", type=Path, default=None)
    args = parser.parse_args()

    repo = args.repo.resolve()
    out_dir = args.out or (repo / "build" / "oedtw-report-validation")
    step3_summary = out_dir / "step3_oedtw_match_summary.csv"

    rows = read_rows(step3_summary)
    figures_dir = out_dir / "step4_figures"
    figures_dir.mkdir(parents=True, exist_ok=True)

    generated = [
        plot_expected_vs_matched(rows, figures_dir),
        plot_index_error(rows, figures_dir),
    ]
    summary = write_markdown_summary(rows, out_dir, generated)

    print(f"figures_dir={figures_dir}")
    for path in generated:
        print(path)
    print(f"summary={summary}")


if __name__ == "__main__":
    main()

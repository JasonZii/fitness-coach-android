package com.example.fitnesscoach.algorithm

import com.example.fitnesscoach.core.util.Constants.OE_DTW_MIN_FRAMES
import com.example.fitnesscoach.training.data.parseReferencePoseJson
import com.example.fitnesscoach.training.domain.EvaluateExerciseUseCase
import com.example.fitnesscoach.training.pose.alignOeDtw
import com.example.fitnesscoach.training.pose.normalizeLandmarks
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Regression tests for the reference-video alignment algorithm.
 *
 * The source videos live in res/raw:
 *   squat.mp4
 *   bicep_curl.mp4
 *   lateral_raise.mp4
 *   lunge_knee_raise.mp4
 *   shoulder_press.mp4
 *
 * JVM unit tests cannot reliably decode Android raw video resources, so this
 * test uses the MediaPipe landmark JSON extracted from those videos under
 * assets/landmarks. Each test simulates a live sequence from the start of the
 * action, removes frames inside an early/middle/late clip, then OE-DTW aligns
 * the damaged user sequence back against the full reference sequence.
 *
 * Reports are written to:
 *   app/build/reports/algorithm/reference_clip_frame_drop/
 */
class ReferenceClipFrameDropAlgorithmTest {

    private data class ExerciseFixture(
        val exerciseId: String,
        val sourceVideo: String,
        val landmarkJson: String,
        val parseStep: Int,
        val upperBodyOnly: Boolean,
    )

    private data class ClipPlan(
        val label: String,
        val startFraction: Float,
        val lengthFraction: Float,
        val dropEveryNthFrame: Int,
    )

    private data class ChartRow(
        val exerciseId: String,
        val sourceVideo: String,
        val clipLabel: String,
        val referenceFrameCount: Int,
        val clipStartIndex: Int,
        val clipEndIndex: Int,
        val userFrameCount: Int,
        val droppedFrameCount: Int,
        val expectedReferenceIndex: Int,
        val matchedReferenceIndex: Int,
        val alignmentError: Int,
        val score: Float,
    )

    private val fixtures = listOf(
        ExerciseFixture("squat", "squat.mp4", "squat.json", parseStep = 2, upperBodyOnly = false),
        ExerciseFixture("bicep_curl", "bicep_curl.mp4", "bicep_curl.json", parseStep = 2, upperBodyOnly = true),
        ExerciseFixture("lateral_raise", "lateral_raise.mp4", "lateral_raise.json", parseStep = 1, upperBodyOnly = true),
        ExerciseFixture("lunge_knee_raise", "lunge_knee_raise.mp4", "lunge_knee_raise.json", parseStep = 2, upperBodyOnly = false),
        ExerciseFixture("shoulder_press", "shoulder_press.mp4", "shoulder_press.json", parseStep = 2, upperBodyOnly = true),
    )

    private val clipPlans = listOf(
        ClipPlan("early_drop_every_5th", startFraction = 0.10f, lengthFraction = 0.34f, dropEveryNthFrame = 5),
        ClipPlan("middle_drop_every_4th", startFraction = 0.33f, lengthFraction = 0.34f, dropEveryNthFrame = 4),
        ClipPlan("late_drop_every_3rd", startFraction = 0.56f, lengthFraction = 0.34f, dropEveryNthFrame = 3),
    )

    @Test
    fun clipsWithRemovedFramesAlignToTheCompleteReferenceSequence() {
        val rows = fixtures.flatMap { fixture ->
            val reference = loadNormalizedReference(fixture)
            clipPlans.map { plan ->
                runClipCase(fixture, reference, plan)
            }
        }

        writeReports(rows)

        rows.forEach { row ->
            val allowedError = max(4, (row.referenceFrameCount * 0.08f).roundToInt())
            assertTrue(
                "${row.exerciseId}/${row.clipLabel}: matched frame ${row.matchedReferenceIndex} " +
                    "should be within $allowedError frames of expected ${row.expectedReferenceIndex}; " +
                    "error=${row.alignmentError}",
                row.alignmentError <= allowedError
            )
            assertTrue(
                "${row.exerciseId}/${row.clipLabel}: score should stay useful after deleting frames; " +
                    "sf=${row.score}",
                row.score >= 70f
            )
        }
    }

    private fun runClipCase(
        fixture: ExerciseFixture,
        reference: List<List<Triple<Float, Float, Float>>>,
        plan: ClipPlan,
    ): ChartRow {
        val clipLength = max(OE_DTW_MIN_FRAMES + 8, (reference.size * plan.lengthFraction).roundToInt())
            .coerceAtMost(reference.size - 1)
        val start = ((reference.size - clipLength) * plan.startFraction).roundToInt()
            .coerceIn(0, reference.size - clipLength)
        val clipIndices = start until (start + clipLength)
        val clipIndexSet = clipIndices.toSet()
        val endIndex = clipIndices.last

        val keptIndices = (0..endIndex).filter { referenceIndex ->
            val localIndex = referenceIndex - start
            referenceIndex !in clipIndexSet ||
                localIndex == 0 ||
                referenceIndex == endIndex ||
                (localIndex + 1) % plan.dropEveryNthFrame != 0
        }
        val userSequence = keptIndices.map { reference[it] }
        val matchedIndex = alignOeDtw(userSequence, reference)
        val expectedIndex = endIndex
        val score = EvaluateExerciseUseCase().evaluate(
            matchedReferenceIndex = matchedIndex,
            userLandmarks = userSequence.last(),
            referenceSequence = reference,
            upperBodyOnly = fixture.upperBodyOnly,
            exerciseId = fixture.exerciseId,
        ).sf

        assertTrue(
            "${fixture.exerciseId}/${plan.label}: damaged clip must still contain at least " +
                "$OE_DTW_MIN_FRAMES frames, actual=${userSequence.size}",
            userSequence.size >= OE_DTW_MIN_FRAMES
        )
        assertTrue(
            "${fixture.exerciseId}/${plan.label}: OE-DTW returned warm-up index after enough frames",
            matchedIndex >= 0
        )

        return ChartRow(
            exerciseId = fixture.exerciseId,
            sourceVideo = fixture.sourceVideo,
            clipLabel = plan.label,
            referenceFrameCount = reference.size,
            clipStartIndex = clipIndices.first,
            clipEndIndex = endIndex,
            userFrameCount = userSequence.size,
            droppedFrameCount = endIndex + 1 - userSequence.size,
            expectedReferenceIndex = expectedIndex,
            matchedReferenceIndex = matchedIndex,
            alignmentError = abs(matchedIndex - expectedIndex),
            score = score,
        )
    }

    private fun loadNormalizedReference(
        fixture: ExerciseFixture,
    ): List<List<Triple<Float, Float, Float>>> {
        val json = javaClass.classLoader!!
            .getResourceAsStream("landmarks/${fixture.landmarkJson}")!!
            .bufferedReader()
            .readText()
        return parseReferencePoseJson(json, step = fixture.parseStep).map { normalizeLandmarks(it) }
    }

    private fun writeReports(rows: List<ChartRow>) {
        val reportDir = File("build/reports/algorithm/reference_clip_frame_drop")
        reportDir.mkdirs()

        File(reportDir, "alignment_results.csv").writeText(buildCsv(rows))
        File(reportDir, "alignment_error.svg").writeText(buildAlignmentErrorSvg(rows))
        File(reportDir, "score.svg").writeText(buildScoreSvg(rows))
        File(reportDir, "index.html").writeText(buildHtml())
    }

    private fun buildCsv(rows: List<ChartRow>): String {
        val header = listOf(
            "exercise_id",
            "source_video",
            "clip_label",
            "reference_frame_count",
            "clip_start_index",
            "clip_end_index",
            "user_frame_count_after_drop",
            "dropped_frame_count",
            "expected_reference_index",
            "matched_reference_index",
            "alignment_error",
            "score",
        ).joinToString(",")
        val body = rows.joinToString("\n") { row ->
            listOf(
                row.exerciseId,
                row.sourceVideo,
                row.clipLabel,
                row.referenceFrameCount,
                row.clipStartIndex,
                row.clipEndIndex,
                row.userFrameCount,
                row.droppedFrameCount,
                row.expectedReferenceIndex,
                row.matchedReferenceIndex,
                row.alignmentError,
                "%.2f".format(Locale.US, row.score),
            ).joinToString(",")
        }
        return "$header\n$body\n"
    }

    private fun buildAlignmentErrorSvg(rows: List<ChartRow>): String =
        buildBarChart(
            title = "OE-DTW alignment error after frame deletion",
            yLabel = "Absolute frame error",
            rows = rows,
            value = { it.alignmentError.toFloat() },
            maxValue = max(1f, rows.maxOf { it.alignmentError }.toFloat()),
            fill = "#2f6fed",
        )

    private fun buildScoreSvg(rows: List<ChartRow>): String =
        buildBarChart(
            title = "Pose score after aligning damaged clips",
            yLabel = "Score",
            rows = rows,
            value = { it.score },
            maxValue = 100f,
            fill = "#2f9e44",
        )

    private fun buildBarChart(
        title: String,
        yLabel: String,
        rows: List<ChartRow>,
        value: (ChartRow) -> Float,
        maxValue: Float,
        fill: String,
    ): String {
        val width = 1320
        val height = 560
        val left = 78
        val top = 54
        val bottom = 150
        val plotHeight = height - top - bottom
        val plotWidth = width - left - 42
        val slot = plotWidth.toFloat() / rows.size
        val barWidth = slot * 0.62f
        val bars = rows.mapIndexed { index, row ->
            val barHeight = (value(row) / maxValue).coerceIn(0f, 1f) * plotHeight
            val x = left + index * slot + (slot - barWidth) / 2f
            val y = top + plotHeight - barHeight
            val label = "${row.exerciseId}\\n${row.clipLabel.substringBefore("_drop")}"
            """
            <rect x="${x.fmt()}" y="${y.fmt()}" width="${barWidth.fmt()}" height="${barHeight.fmt()}" fill="$fill" rx="2">
              <title>${row.exerciseId} ${row.clipLabel}: ${value(row).fmt()}</title>
            </rect>
            <text x="${(x + barWidth / 2f).fmt()}" y="${(height - bottom + 24).toFloat().fmt()}" text-anchor="middle" class="tick">${label.substringBefore("\\n")}</text>
            <text x="${(x + barWidth / 2f).fmt()}" y="${(height - bottom + 40).toFloat().fmt()}" text-anchor="middle" class="tick">${label.substringAfter("\\n")}</text>
            <text x="${(x + barWidth / 2f).fmt()}" y="${(y - 6).fmt()}" text-anchor="middle" class="value">${value(row).fmt()}</text>
            """.trimIndent()
        }.joinToString("\n")

        val grid = (0..4).joinToString("\n") { tick ->
            val ratio = tick / 4f
            val y = top + plotHeight - ratio * plotHeight
            val tickValue = maxValue * ratio
            """<line x1="$left" y1="${y.fmt()}" x2="${left + plotWidth}" y2="${y.fmt()}" class="grid"/><text x="${left - 10}" y="${(y + 4).fmt()}" text-anchor="end" class="axis">${tickValue.fmt()}</text>"""
        }

        return """
            <svg xmlns="http://www.w3.org/2000/svg" width="$width" height="$height" viewBox="0 0 $width $height">
              <style>
                text { font-family: Arial, sans-serif; fill: #1f2933; }
                .title { font-size: 22px; font-weight: 700; }
                .axis { font-size: 12px; }
                .tick { font-size: 10px; }
                .value { font-size: 11px; font-weight: 700; }
                .grid { stroke: #d9e2ec; stroke-width: 1; }
                .domain { stroke: #334e68; stroke-width: 1.5; }
              </style>
              <rect width="100%" height="100%" fill="#ffffff"/>
              <text x="${width / 2}" y="30" text-anchor="middle" class="title">$title</text>
              <text x="22" y="${(top + plotHeight / 2).toFloat().fmt()}" transform="rotate(-90 22 ${(top + plotHeight / 2).toFloat().fmt()})" text-anchor="middle" class="axis">$yLabel</text>
              $grid
              <line x1="$left" y1="$top" x2="$left" y2="${top + plotHeight}" class="domain"/>
              <line x1="$left" y1="${top + plotHeight}" x2="${left + plotWidth}" y2="${top + plotHeight}" class="domain"/>
              $bars
            </svg>
        """.trimIndent()
    }

    private fun buildHtml(): String =
        """
        <!doctype html>
        <html lang="en">
        <head>
          <meta charset="utf-8">
          <title>Reference clip frame-drop algorithm report</title>
          <style>
            body { font-family: Arial, sans-serif; margin: 32px; color: #1f2933; }
            h1 { font-size: 24px; }
            img { display: block; max-width: 100%; margin: 24px 0; border: 1px solid #d9e2ec; }
            a { color: #2f6fed; }
          </style>
        </head>
        <body>
          <h1>Reference clip frame-drop algorithm report</h1>
          <p><a href="alignment_results.csv">CSV data</a></p>
          <img src="alignment_error.svg" alt="Alignment error chart">
          <img src="score.svg" alt="Score chart">
        </body>
        </html>
        """.trimIndent()

    private fun Float.fmt(): String = "%.2f".format(Locale.US, this)
}

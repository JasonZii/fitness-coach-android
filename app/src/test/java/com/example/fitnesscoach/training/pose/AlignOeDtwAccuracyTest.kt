package com.example.fitnesscoach.training.pose

import com.example.fitnesscoach.core.util.Constants.OE_DTW_MIN_FRAMES
import com.example.fitnesscoach.training.data.parseReferencePoseJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Accuracy tests for [advanceOeDtw] — the incremental one-row-at-a-time algorithm
 * used in production (TrainingViewModel).
 *
 * Each test calls [advanceOeDtw] frame-by-frame with a persistent DP row, exactly
 * mirroring the production path:
 *
 *   var row: FloatArray? = null
 *   for each frame k:
 *       val advance = advanceOeDtw(frame, refSeq, row, k + 1)
 *       row = advance.nextRow
 *
 * Self-tracking guarantee: when user[k] == ref[k] for all k, frameDist along the
 * diagonal is 0, so OE-DTW must return matchedIdx == k for every k ≥ OE_DTW_MIN_FRAMES.
 *
 * CSV output (build/dtw_*.csv) can be plotted with Python:
 *
 *   import pandas as pd, matplotlib.pyplot as plt
 *   df = pd.read_csv("build/dtw_self_tracking_squat.csv")
 *   plt.plot(df.frame_idx, df.matched_idx, label="matched")
 *   plt.plot(df.frame_idx, df.expected_idx, "--", label="expected (diagonal)")
 *   plt.xlabel("frame_idx"); plt.ylabel("matched_idx"); plt.legend(); plt.show()
 */
class AlignOeDtwAccuracyTest {

    private fun loadJson(filename: String): String =
        javaClass.classLoader!!
            .getResourceAsStream("landmarks/$filename")!!
            .bufferedReader()
            .readText()

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Run incremental self-tracking on [jsonFile] and write results to [csvName].
     * Asserts matchedIdx == k for every frame k ≥ OE_DTW_MIN_FRAMES.
     */
    private fun runSelfTracking(
        jsonFile: String,
        csvName: String,
        cameraAngle: CameraAngle = CameraAngle.AMBIGUOUS,
        requiresFullBody: Boolean = false,
    ) {
        val rawFrames = parseReferencePoseJson(loadJson(jsonFile))
        val refSeq = rawFrames.mapNotNull { normalizeLandmarks(it, cameraAngle, requiresFullBody) }

        val csv = File("build/$csvName")
        csv.parentFile?.mkdirs()

        var row: FloatArray? = null

        csv.bufferedWriter().use { w ->
            w.write("frame_idx,matched_idx,expected_idx,dp_cost_at_match\n")

            refSeq.forEachIndexed { k, frame ->
                val advance  = advanceOeDtw(frame, refSeq, row, k + 1)
                row = advance.nextRow

                val matched  = advance.matchedReferenceIndex
                val expected = if (k + 1 < OE_DTW_MIN_FRAMES) -1 else k
                val cost     = if (matched >= 0) "%.6f".format(advance.nextRow[matched]) else "-1"

                w.write("$k,$matched,$expected,$cost\n")

                assertEquals(
                    "$jsonFile frame $k: expected=$expected got=$matched",
                    expected, matched
                )
            }
        }

        println("$jsonFile: ${refSeq.size} frames → ${csv.absolutePath}")
    }

    // ── Test 1: self-tracking on squat ────────────────────────────────────────

    /**
     * Self-tracking with squat.json (side view, full body).
     * CSV: build/dtw_self_tracking_squat.csv
     */
    @Test
    fun incrementalSelfTracking_squatJson_matchedIdxEqualsFrameIdx() {
        runSelfTracking(
            jsonFile        = "squat.json",
            csvName         = "dtw_self_tracking_squat.csv",
            cameraAngle     = CameraAngle.SIDE,
            requiresFullBody = true,
        )
    }

    // ── Test 2: self-tracking on bicep curl ───────────────────────────────────

    /**
     * Self-tracking with bicep_curl.json (side view, upper body only).
     * Confirms the guarantee is not exercise-specific.
     * CSV: build/dtw_self_tracking_bicep_curl.csv
     */
    @Test
    fun incrementalSelfTracking_bicepCurlJson_matchedIdxEqualsFrameIdx() {
        runSelfTracking(
            jsonFile    = "bicep_curl.json",
            csvName     = "dtw_self_tracking_bicep_curl.csv",
            cameraAngle = CameraAngle.SIDE,
        )
    }

    // ── Test 3: self-tracking on dumbbell lateral raise ───────────────────────

    /**
     * Self-tracking with dumbbell_lateral_raise.json (front view, upper body only).
     * CSV: build/dtw_self_tracking_lateral_raise.csv
     */
    @Test
    fun incrementalSelfTracking_lateralRaiseJson_matchedIdxEqualsFrameIdx() {
        runSelfTracking(
            jsonFile    = "dumbbell_lateral_raise.json",
            csvName     = "dtw_self_tracking_lateral_raise.csv",
            cameraAngle = CameraAngle.FRONT,
        )
    }

    // ── Test 4: scale invariance ──────────────────────────────────────────────

    /**
     * Scaling every (x,y,z) by factor s simulates different body sizes (taller /
     * shorter users). Because normalizeLandmarks divides by torsoLength (which also
     * scales by s), the scale cancels and matchedIdx must be identical across all
     * scale factors.
     *
     * CSV: build/dtw_scale_invariance.csv
     * Python: group-by scale, overlay curves — all should coincide.
     */
    @Test
    fun scaleInvariance_squatJson_matchedIdxUnchangedAcrossScales() {
        val rawFrames = parseReferencePoseJson(loadJson("squat.json"))
        val refSeq    = rawFrames.mapNotNull {
            normalizeLandmarks(it, CameraAngle.SIDE, requiresFullBody = true)
        }

        val csv = File("build/dtw_scale_invariance.csv")
        csv.parentFile?.mkdirs()

        val scales = listOf(0.5f, 0.75f, 1.0f, 1.5f, 2.0f)

        csv.bufferedWriter().use { w ->
            w.write("scale,frame_idx,matched_idx,expected_idx\n")

            for (scale in scales) {
                var row: FloatArray? = null

                rawFrames.forEachIndexed { k, rawFrame ->
                    val scaled = rawFrame.map { (x, y, z) -> Triple(x * scale, y * scale, z * scale) }
                    val norm   = normalizeLandmarks(scaled, CameraAngle.SIDE, requiresFullBody = true)
                        ?: return@forEachIndexed

                    val advance  = advanceOeDtw(norm, refSeq, row, k + 1)
                    row = advance.nextRow

                    val matched  = advance.matchedReferenceIndex
                    val expected = if (k + 1 < OE_DTW_MIN_FRAMES) -1 else k

                    w.write("$scale,$k,$matched,$expected\n")

                    assertEquals(
                        "scale=$scale frame=$k: expected=$expected got=$matched",
                        expected, matched
                    )
                }
            }
        }

        println("Scale invariance (${scales}): ${refSeq.size} frames → ${csv.absolutePath}")
    }

    // ── Test 5: time-warp tolerance ───────────────────────────────────────────

    /**
     * Each reference frame is repeated [factor] times, simulating a user performing
     * the exercise at 1/factor speed. OE-DTW is designed to handle tempo differences;
     * the matched index sequence must be monotonically non-decreasing.
     *
     * CSV: build/dtw_time_warp.csv
     * Python: plot user_frame_idx vs matched_idx per repeat factor — should be a
     *         staircase that covers the full reference range.
     */
    @Test
    fun timeWarp_squatJson_matchedIdxMonotonicallyNonDecreasing() {
        val rawFrames = parseReferencePoseJson(loadJson("squat.json"))
        val refSeq    = rawFrames.mapNotNull {
            normalizeLandmarks(it, CameraAngle.SIDE, requiresFullBody = true)
        }

        val csv = File("build/dtw_time_warp.csv")
        csv.parentFile?.mkdirs()

        csv.bufferedWriter().use { w ->
            w.write("repeat_factor,user_frame_idx,matched_idx,ref_source_idx\n")

            for (factor in listOf(2, 3)) {
                var row: FloatArray? = null
                var prevMatched = -1
                var userIdx = 0

                refSeq.forEachIndexed { k, frame ->
                    repeat(factor) {
                        val advance = advanceOeDtw(frame, refSeq, row, userIdx + 1)
                        row = advance.nextRow

                        val matched = advance.matchedReferenceIndex
                        w.write("$factor,$userIdx,$matched,$k\n")

                        if (matched >= 0 && prevMatched >= 0) {
                            assertTrue(
                                "factor=$factor userFrame=$userIdx: matchedIdx must not decrease " +
                                "(prev=$prevMatched got=$matched)",
                                matched >= prevMatched
                            )
                        }
                        if (matched >= 0) prevMatched = matched
                        userIdx++
                    }
                }

                println("Time-warp ×$factor: $userIdx user frames → ${refSeq.size} ref frames")
            }
        }

        println("Time-warp CSV → ${csv.absolutePath}")
    }
}

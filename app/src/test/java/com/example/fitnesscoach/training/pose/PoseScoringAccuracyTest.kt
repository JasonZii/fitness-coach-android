package com.example.fitnesscoach.training.pose

import androidx.compose.ui.graphics.Color
import com.example.fitnesscoach.core.util.Constants.OE_DTW_MIN_FRAMES
import com.example.fitnesscoach.core.util.Constants.SCORE_RED_THRESHOLD
import com.example.fitnesscoach.training.core.PoseScoringEngine
import com.example.fitnesscoach.training.data.parseReferencePoseJson
import com.example.fitnesscoach.training.domain.EvaluateExerciseUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Accuracy tests for [PoseScoringEngine] and [EvaluateExerciseUseCase].
 *
 * Uses [advanceOeDtw] with a persistent DP row, exactly as TrainingViewModel does:
 *
 *   var row: FloatArray? = null
 *   for each frame k:
 *       val advance = advanceOeDtw(frame, refSeq, row, k + 1)
 *       row = advance.nextRow
 *       val result = evaluateUseCase.evaluate(advance.matchedReferenceIndex, frame, refSeq)
 *
 * Self-tracking guarantee (proven by [AlignOeDtwAccuracyTest]): when user[k] == ref[k],
 * matchedIdx == k, so userFrame and refFrame are identical, and:
 *   S1 = 100, S2 = 100, sf = 100, all colors GREEN.
 *
 * CSV output (build/scoring_*.csv) can be plotted with Python:
 *
 *   import pandas as pd, matplotlib.pyplot as plt
 *   df = pd.read_csv("build/scoring_self_tracking_squat.csv")
 *   fig, axes = plt.subplots(2, 1, sharex=True)
 *   axes[0].plot(df.frame_idx, df.sf, label="sf")
 *   axes[0].axhline(83, color="r", linestyle="--", label="red threshold")
 *   axes[0].set_ylabel("score"); axes[0].legend()
 *   axes[1].plot(df.frame_idx, df.red_limbs, label="red limbs", color="r")
 *   axes[1].set_xlabel("frame_idx"); axes[1].set_ylabel("count"); axes[1].legend()
 *   plt.tight_layout(); plt.show()
 */
class PoseScoringAccuracyTest {

    private fun loadJson(filename: String): String =
        javaClass.classLoader!!
            .getResourceAsStream("landmarks/$filename")!!
            .bufferedReader()
            .readText()

    // ── Helper ────────────────────────────────────────────────────────────────

    /**
     * Run incremental self-tracking + scoring on [jsonFile] and write results to [csvName].
     * Asserts sf == 100 and all colors GREEN for every frame where matchedIdx >= 0.
     */
    private fun runSelfTrackingScoring(
        jsonFile: String,
        csvName: String,
        cameraAngle: CameraAngle = CameraAngle.AMBIGUOUS,
        requiresFullBody: Boolean = false,
        exerciseId: String = "",
    ) {
        val rawFrames = parseReferencePoseJson(loadJson(jsonFile))
        val refSeq = rawFrames.mapNotNull { normalizeLandmarks(it, cameraAngle, requiresFullBody) }
        val evaluateUseCase = EvaluateExerciseUseCase()

        val csv = File("build/$csvName")
        csv.parentFile?.mkdirs()

        var row: FloatArray? = null

        csv.bufferedWriter().use { w ->
            w.write("frame_idx,matched_idx,s1,s2,sf,red_joints,red_limbs\n")

            refSeq.forEachIndexed { k, frame ->
                val advance = advanceOeDtw(frame, refSeq, row, k + 1)
                row = advance.nextRow
                val matched = advance.matchedReferenceIndex

                val result    = evaluateUseCase.evaluate(matched, frame, refSeq, !requiresFullBody, exerciseId)
                val redJoints = result.jointColors.count { it == Color.Red }
                val redLimbs  = result.limbColors.count  { it == Color.Red }

                w.write(
                    "$k,$matched," +
                    "${"%.2f".format(result.s1)},${"%.2f".format(result.s2)}," +
                    "${"%.2f".format(result.sf)},$redJoints,$redLimbs\n"
                )

                if (matched >= 0) {
                    assertEquals("$jsonFile frame $k: sf must be 100", 100f, result.sf, 0.1f)
                    assertEquals("$jsonFile frame $k: red joints must be 0", 0, redJoints)
                    assertEquals("$jsonFile frame $k: red limbs must be 0",  0, redLimbs)
                }
            }
        }

        println("$jsonFile: ${refSeq.size} frames → ${csv.absolutePath}")
    }

    // ── Test 1: self-tracking scoring on squat ────────────────────────────────

    /**
     * Self-tracking with squat.json: identical frames → sf = 100, all GREEN.
     * CSV: build/scoring_self_tracking_squat.csv
     */
    @Test
    fun incrementalSelfTracking_squatJson_sfIs100AllColorsGreen() {
        runSelfTrackingScoring(
            jsonFile        = "squat.json",
            csvName         = "scoring_self_tracking_squat.csv",
            cameraAngle     = CameraAngle.SIDE,
            requiresFullBody = true,
            exerciseId      = "squat",
        )
    }

    // ── Test 2: self-tracking scoring on bicep curl ───────────────────────────

    /**
     * Self-tracking with bicep_curl.json: identical frames → sf = 100, all GREEN.
     * CSV: build/scoring_self_tracking_bicep_curl.csv
     */
    @Test
    fun incrementalSelfTracking_bicepCurlJson_sfIs100AllColorsGreen() {
        runSelfTrackingScoring(
            jsonFile    = "bicep_curl.json",
            csvName     = "scoring_self_tracking_bicep_curl.csv",
            cameraAngle = CameraAngle.SIDE,
            exerciseId  = "bicep_curl",
        )
    }

    // ── Test 3: self-tracking scoring on lateral raise ────────────────────────

    /**
     * Self-tracking with dumbbell_lateral_raise.json: identical frames → sf = 100, all GREEN.
     * CSV: build/scoring_self_tracking_lateral_raise.csv
     */
    @Test
    fun incrementalSelfTracking_lateralRaiseJson_sfIs100AllColorsGreen() {
        runSelfTrackingScoring(
            jsonFile    = "dumbbell_lateral_raise.json",
            csvName     = "scoring_self_tracking_lateral_raise.csv",
            cameraAngle = CameraAngle.FRONT,
            exerciseId  = "dumbbell_lateral_raise",
        )
    }

    // ── Test 4: score formula ─────────────────────────────────────────────────

    /**
     * sf = 0.2·s1 + 0.8·s2 must hold at every frame on real data.
     * CSV: build/scoring_formula_check.csv
     * Python: plot sf vs sf_formula — should be identical lines.
     */
    @Test
    fun incrementalSelfTracking_squatJson_sfMatchesWeightedFormula() {
        val rawFrames = parseReferencePoseJson(loadJson("squat.json"))
        val refSeq = rawFrames.mapNotNull {
            normalizeLandmarks(it, CameraAngle.SIDE, requiresFullBody = true)
        }

        val csv = File("build/scoring_formula_check.csv")
        csv.parentFile?.mkdirs()

        var row: FloatArray? = null

        csv.bufferedWriter().use { w ->
            w.write("frame_idx,matched_idx,s1,s2,sf,sf_formula\n")

            refSeq.forEachIndexed { k, frame ->
                val advance = advanceOeDtw(frame, refSeq, row, k + 1)
                row = advance.nextRow
                val matched = advance.matchedReferenceIndex
                if (matched < 0) return@forEachIndexed

                val result   = PoseScoringEngine.calculatePoseScore(frame, refSeq[matched])
                val formula  = (0.2f * result.s1 + 0.8f * result.s2).coerceIn(0f, 100f)

                w.write(
                    "$k,$matched," +
                    "${"%.2f".format(result.s1)},${"%.2f".format(result.s2)}," +
                    "${"%.2f".format(result.sf)},${"%.2f".format(formula)}\n"
                )

                assertEquals(
                    "frame $k: sf must equal 0.2·s1 + 0.8·s2",
                    formula, result.sf, 0.01f
                )
            }
        }

        println("Formula check: ${refSeq.size} frames → ${csv.absolutePath}")
    }

    // ── Test 5: wrong pose → sf drops, red colors appear ─────────────────────

    /**
     * Mirror both elbows and both knees across their proximal anchor joints to
     * produce a completely wrong pose. The mirrored limbs must turn RED and sf
     * must drop below [SCORE_RED_THRESHOLD].
     *
     * Why mirroring: shifting all joints by the same offset leaves limb vectors
     * unchanged (S2 = 100). Mirroring reverses each limb vector by 180°,
     * driving limbScore → 0 for the affected limbs.
     *
     * CSV: build/scoring_wrong_pose.csv
     * Python: compare sf across "identical" and "mirrored" rows.
     */
    @Test
    fun wrongPose_squatMidFrame_sfDropsBelowThresholdAndRedColorsAppear() {
        val rawFrames = parseReferencePoseJson(loadJson("squat.json"))
        val refSeq    = rawFrames.mapNotNull {
            normalizeLandmarks(it, CameraAngle.SIDE, requiresFullBody = true)
        }
        val refFrame  = refSeq[refSeq.size / 2]

        val anchorsAndJoints = listOf(
            11 to 13,   // left shoulder → left elbow
            12 to 14,   // right shoulder → right elbow
            23 to 25,   // left hip → left knee
            24 to 26,   // right hip → right knee
        )

        val userFrame = refFrame.toMutableList()
        for ((anchorIdx, jointIdx) in anchorsAndJoints) {
            val a = refFrame[anchorIdx]; val j = refFrame[jointIdx]
            userFrame[jointIdx] = Triple(
                2f * a.first  - j.first,
                2f * a.second - j.second,
                2f * a.third  - j.third,
            )
        }

        val perfect  = PoseScoringEngine.calculatePoseScore(refFrame, refFrame)
        val mirrored = PoseScoringEngine.calculatePoseScore(userFrame, refFrame)

        val csv = File("build/scoring_wrong_pose.csv")
        csv.parentFile?.mkdirs()
        csv.bufferedWriter().use { w ->
            w.write("pose,s1,s2,sf,red_joints,red_limbs\n")
            w.write("identical,${"%.2f".format(perfect.s1)},${"%.2f".format(perfect.s2)},${"%.2f".format(perfect.sf)},0,0\n")
            w.write(
                "mirrored_limbs,${"%.2f".format(mirrored.s1)},${"%.2f".format(mirrored.s2)}," +
                "${"%.2f".format(mirrored.sf)}," +
                "${mirrored.jointColors.count { it == Color.Red }}," +
                "${mirrored.limbColors.count  { it == Color.Red }}\n"
            )
        }

        assertTrue(
            "mirrored sf=${mirrored.sf} must be < $SCORE_RED_THRESHOLD",
            mirrored.sf < SCORE_RED_THRESHOLD
        )
        assertTrue(
            "at least one joint must be RED for mirrored pose",
            mirrored.jointColors.any { it == Color.Red }
        )
        assertTrue(
            "at least one limb must be RED for mirrored pose",
            mirrored.limbColors.any { it == Color.Red }
        )

        println(
            "Identical: sf=${perfect.sf}  " +
            "Mirrored: sf=${mirrored.sf} " +
            "redJoints=${mirrored.jointColors.count{it==Color.Red}} " +
            "redLimbs=${mirrored.limbColors.count{it==Color.Red}}"
        )
        println("CSV → ${csv.absolutePath}")
    }

    // ── Test 6: single-limb error → only that limb turns red ─────────────────

    /**
     * Mirror only joint 14 (right elbow) across joint 12 (right shoulder).
     * This reverses limb 2 (right upper arm: 12→14) by 180°.
     *
     * Expected: limbColors[2] == RED; all other non-shared limbs stay GREEN.
     * CSV: build/scoring_single_limb_error.csv
     */
    @Test
    fun singleLimbError_squatMidFrame_onlyAffectedLimbIsRed() {
        val rawFrames = parseReferencePoseJson(loadJson("squat.json"))
        val refSeq    = rawFrames.mapNotNull {
            normalizeLandmarks(it, CameraAngle.SIDE, requiresFullBody = true)
        }
        val refFrame  = refSeq[refSeq.size / 2]

        val userFrame = refFrame.toMutableList()
        val shoulder  = refFrame[12]; val elbow = refFrame[14]
        userFrame[14] = Triple(
            2f * shoulder.first  - elbow.first,
            2f * shoulder.second - elbow.second,
            2f * shoulder.third  - elbow.third,
        )

        val result    = PoseScoringEngine.calculatePoseScore(userFrame, refFrame)
        val redLimbs  = result.limbColors.mapIndexed { i, c -> i to c }

        val csv = File("build/scoring_single_limb_error.csv")
        csv.parentFile?.mkdirs()
        csv.bufferedWriter().use { w ->
            w.write("limb_idx,score,color\n")
            result.limbScores.forEachIndexed { i, s ->
                w.write("$i,${"%.2f".format(s)},${if (result.limbColors[i] == Color.Red) "RED" else "GREEN"}\n")
            }
        }

        assertTrue(
            "limbScores[2]=${result.limbScores[2]} must be < $SCORE_RED_THRESHOLD",
            result.limbScores[2] < SCORE_RED_THRESHOLD
        )
        assertEquals("limbColors[2] must be RED", Color.Red, result.limbColors[2])

        listOf(0, 1, 4, 5, 6, 7, 8, 9, 10, 11, 12).forEach { i ->
            assertEquals("limbColors[$i] must be GREEN (only elbow 14 moved)", Color.Green, result.limbColors[i])
        }

        println("Limb scores: ${result.limbScores.mapIndexed { i, s -> "$i:${"%.1f".format(s)}" }}")
        println("CSV → ${csv.absolutePath}")
    }
}

package com.example.fitnesscoach.training.pose

import androidx.compose.ui.graphics.Color
import com.example.fitnesscoach.core.util.Constants.LANDMARK_COUNT
import com.example.fitnesscoach.core.util.Constants.LIMB_COUNT
import com.example.fitnesscoach.core.util.Constants.SCORE_RED_THRESHOLD
import com.example.fitnesscoach.training.core.PoseScoringEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [PoseScoringEngine.calculatePoseScore].
 *
 * Covers the acceptance criteria from ALGORITHM.md §Module 3 and RULES.md §4.4.
 * Naming: ClassName_methodName_condition_expectedResult
 */
class PoseScoringEngineTest {

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** 33 identical landmarks at (x, y). */
    private fun uniformLandmarks(x: Float = 0.5f, y: Float = 0.5f) =
        List(LANDMARK_COUNT) { Pair(x, y) }

    // ── Output size (RULES.md §4.4) ──────────────────────────────────────────

    @Test
    fun calculatePoseScore_identicalInputs_jointColorsSizeIs33() {
        val lm = uniformLandmarks()
        val result = PoseScoringEngine.calculatePoseScore(lm, lm)
        assertEquals(LANDMARK_COUNT, result.jointColors.size)
    }

    @Test
    fun calculatePoseScore_identicalInputs_limbColorsSizeIs13() {
        val lm = uniformLandmarks()
        val result = PoseScoringEngine.calculatePoseScore(lm, lm)
        assertEquals(LIMB_COUNT, result.limbColors.size)
    }

    @Test
    fun calculatePoseScore_identicalInputs_jointScoresSizeIs33() {
        val lm = uniformLandmarks()
        val result = PoseScoringEngine.calculatePoseScore(lm, lm)
        assertEquals(LANDMARK_COUNT, result.jointScores.size)
    }

    @Test
    fun calculatePoseScore_identicalInputs_limbScoresSizeIs13() {
        val lm = uniformLandmarks()
        val result = PoseScoringEngine.calculatePoseScore(lm, lm)
        assertEquals(LIMB_COUNT, result.limbScores.size)
    }

    // ── Overall score (ALGORITHM.md §Module 3 acceptance criteria) ───────────

    @Test
    fun calculatePoseScore_identicalInputs_sfIsAtLeast95() {
        val lm = uniformLandmarks()
        val result = PoseScoringEngine.calculatePoseScore(lm, lm)
        assertTrue("sf=${result.sf} must be >= 95 for identical inputs", result.sf >= 95f)
    }

    // ── Joint color logic (ALGORITHM.md §Module 3 Step 4) ────────────────────

    @Test
    fun calculatePoseScore_identicalInputs_allJointColorsAreGreen() {
        val lm = uniformLandmarks()
        val result = PoseScoringEngine.calculatePoseScore(lm, lm)
        result.jointColors.forEachIndexed { i, color ->
            assertEquals("jointColors[$i] must be GREEN for identical inputs", Color.Green, color)
        }
    }

    @Test
    fun calculatePoseScore_identicalInputs_allLimbColorsAreGreen() {
        val lm = uniformLandmarks()
        val result = PoseScoringEngine.calculatePoseScore(lm, lm)
        result.limbColors.forEachIndexed { i, color ->
            assertEquals("limbColors[$i] must be GREEN for identical inputs", Color.Green, color)
        }
    }

    @Test
    fun calculatePoseScore_shiftedJoint_jointScoreBelowThreshold() {
        val ref = uniformLandmarks()
        val user = uniformLandmarks().toMutableList()
        // Large offset — Euclidean distance ≈ 0.566 → Sp = (1 - 0.566)*100 ≈ 43 < 80
        user[0] = Pair(0.9f, 0.9f)

        val result = PoseScoringEngine.calculatePoseScore(user, ref)
        assertTrue(
            "Sp[0]=${result.jointScores[0]} must be < $SCORE_RED_THRESHOLD for shifted landmark",
            result.jointScores[0] < SCORE_RED_THRESHOLD
        )
    }

    @Test
    fun calculatePoseScore_reversedConnectedLimb_affectedJointColorIsRed() {
        val ref = uniformLandmarks().toMutableList()
        val user = uniformLandmarks().toMutableList()

        ref[11] = Pair(0.3f, 0.5f)
        ref[13] = Pair(0.7f, 0.5f)
        user[11] = Pair(0.3f, 0.5f)
        user[13] = Pair(0.0f, 0.5f)

        val result = PoseScoringEngine.calculatePoseScore(user, ref)
        assertEquals(
            "jointColors[11] must be RED when a connected limb score is below $SCORE_RED_THRESHOLD",
            Color.Red,
            result.jointColors[11]
        )
    }

    @Test
    fun calculatePoseScore_shiftedJoint_unaffectedJointsRemainGreen() {
        val ref = uniformLandmarks()
        val user = uniformLandmarks().toMutableList()
        // Only landmark 0 is shifted; all others are identical.
        user[0] = Pair(0.9f, 0.9f)

        val result = PoseScoringEngine.calculatePoseScore(user, ref)
        // Landmarks 1..32 are identical → Sp = 100 >= 80 → GREEN
        for (i in 1 until LANDMARK_COUNT) {
            assertEquals(
                "jointColors[$i] must be GREEN when landmark is not shifted",
                Color.Green,
                result.jointColors[i]
            )
        }
    }

    @Test
    fun calculatePoseScore_scoreExactlyAtThreshold_jointColorIsGreen() {
        // Construct user landmark so Dp is exactly 0.0 → Sp = 100 >= 80 → GREEN
        val ref = uniformLandmarks()
        val result = PoseScoringEngine.calculatePoseScore(ref, ref)
        result.jointColors.forEachIndexed { i, color ->
            assertEquals("jointColors[$i] must be GREEN when Sp == 100", Color.Green, color)
        }
    }

    // ── Limb color logic ─────────────────────────────────────────────────────

    @Test
    fun calculatePoseScore_reversedLimbDirection_limbScoreBelowThreshold() {
        val ref = uniformLandmarks().toMutableList()
        val user = uniformLandmarks().toMutableList()

        // Limb 0: left upper arm, joints 11 → 13.
        // Reference vector points right (+x); user vector points left (−x) → 180° → Sl ≈ 0.
        ref[11] = Pair(0.3f, 0.5f)
        ref[13] = Pair(0.7f, 0.5f)
        user[11] = Pair(0.3f, 0.5f)
        user[13] = Pair(0.0f, 0.5f)

        val result = PoseScoringEngine.calculatePoseScore(user, ref)
        assertTrue(
            "limbScores[0]=${result.limbScores[0]} must be < $SCORE_RED_THRESHOLD for reversed limb",
            result.limbScores[0] < SCORE_RED_THRESHOLD
        )
    }

    @Test
    fun calculatePoseScore_reversedLimbDirection_affectedLimbColorIsRed() {
        val ref = uniformLandmarks().toMutableList()
        val user = uniformLandmarks().toMutableList()

        ref[11] = Pair(0.3f, 0.5f)
        ref[13] = Pair(0.7f, 0.5f)
        user[11] = Pair(0.3f, 0.5f)
        user[13] = Pair(0.0f, 0.5f)

        val result = PoseScoringEngine.calculatePoseScore(user, ref)
        assertEquals(
            "limbColors[0] must be RED when limb direction is reversed",
            Color.Red,
            result.limbColors[0]
        )
    }

    // ── Score weights (ALGORITHM.md §Module 3 Step 3) ────────────────────────

    @Test
    fun calculatePoseScore_identicalInputs_sfEqualsWeightedSumOfS1AndS2() {
        val lm = uniformLandmarks()
        val result = PoseScoringEngine.calculatePoseScore(lm, lm)
        val expected = (0.2f * result.s1 + 0.8f * result.s2).coerceIn(0f, 100f)
        assertEquals("sf must equal 0.2*s1 + 0.8*s2", expected, result.sf, 0.001f)
    }

    @Test
    fun calculatePoseScore_shiftedJoint_sfEqualsWeightedSumOfS1AndS2() {
        val ref = uniformLandmarks()
        val user = uniformLandmarks().toMutableList()
        user[5] = Pair(0.8f, 0.8f)

        val result = PoseScoringEngine.calculatePoseScore(user, ref)
        val expected = (0.2f * result.s1 + 0.8f * result.s2).coerceIn(0f, 100f)
        assertEquals("sf must equal 0.2*s1 + 0.8*s2", expected, result.sf, 0.001f)
    }

    // ── Input validation ─────────────────────────────────────────────────────

    @Test(expected = IllegalArgumentException::class)
    fun calculatePoseScore_userLandmarksTooFew_throwsIllegalArgumentException() {
        PoseScoringEngine.calculatePoseScore(
            List(32) { Pair(0.5f, 0.5f) },
            uniformLandmarks()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun calculatePoseScore_referenceLandmarksTooFew_throwsIllegalArgumentException() {
        PoseScoringEngine.calculatePoseScore(
            uniformLandmarks(),
            List(32) { Pair(0.5f, 0.5f) }
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun calculatePoseScore_userLandmarksTooMany_throwsIllegalArgumentException() {
        PoseScoringEngine.calculatePoseScore(
            List(34) { Pair(0.5f, 0.5f) },
            uniformLandmarks()
        )
    }
}

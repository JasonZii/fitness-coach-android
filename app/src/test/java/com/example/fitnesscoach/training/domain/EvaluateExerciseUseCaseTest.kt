package com.example.fitnesscoach.training.domain

import androidx.compose.ui.graphics.Color
import com.example.fitnesscoach.core.util.Constants.LANDMARK_COUNT
import com.example.fitnesscoach.core.util.Constants.LIMB_COUNT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [EvaluateExerciseUseCase].
 *
 * Naming: ClassName_methodName_condition_expectedResult
 */
class EvaluateExerciseUseCaseTest {

    private lateinit var useCase: EvaluateExerciseUseCase

    @Before
    fun setUp() {
        useCase = EvaluateExerciseUseCase()
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun uniformLandmarks(x: Float = 0.5f, y: Float = 0.5f) =
        List(LANDMARK_COUNT) { Triple(x, y, 0f) }

    /** A one-frame reference sequence using uniform landmarks. */
    private fun singleFrameSequence(x: Float = 0.5f, y: Float = 0.5f) =
        listOf(uniformLandmarks(x, y))

    // ── matchedReferenceIndex == -1 (OE-DTW warm-up period) ─────────────────

    @Test
    fun evaluate_matchedIndexIsMinusOne_returnsAllGreenJointColors() {
        val result = useCase.evaluate(
            matchedReferenceIndex = -1,
            userLandmarks = uniformLandmarks(),
            referenceSequence = singleFrameSequence()
        )
        result.jointColors.forEachIndexed { i, color ->
            assertEquals("jointColors[$i] must be GREEN during warm-up", Color.Green, color)
        }
    }

    @Test
    fun evaluate_matchedIndexIsMinusOne_returnsAllGreenLimbColors() {
        val result = useCase.evaluate(
            matchedReferenceIndex = -1,
            userLandmarks = uniformLandmarks(),
            referenceSequence = singleFrameSequence()
        )
        result.limbColors.forEachIndexed { i, color ->
            assertEquals("limbColors[$i] must be GREEN during warm-up", Color.Green, color)
        }
    }

    @Test
    fun evaluate_matchedIndexIsMinusOne_jointColorsSizeIs33() {
        val result = useCase.evaluate(-1, uniformLandmarks(), singleFrameSequence())
        assertEquals(LANDMARK_COUNT, result.jointColors.size)
    }

    @Test
    fun evaluate_matchedIndexIsMinusOne_limbColorsSizeIs13() {
        val result = useCase.evaluate(-1, uniformLandmarks(), singleFrameSequence())
        assertEquals(LIMB_COUNT, result.limbColors.size)
    }

    @Test
    fun evaluate_matchedIndexIsMinusOne_sfIs100() {
        val result = useCase.evaluate(-1, uniformLandmarks(), singleFrameSequence())
        assertEquals(100f, result.sf, 0.001f)
    }

    // ── Valid matchedReferenceIndex — delegates to PoseScoringEngine ─────────

    @Test
    fun evaluate_identicalLandmarks_sfAtLeast95() {
        val lm = uniformLandmarks()
        val result = useCase.evaluate(
            matchedReferenceIndex = 0,
            userLandmarks = lm,
            referenceSequence = listOf(lm)
        )
        assertTrue("sf=${result.sf} must be >= 95 for identical inputs", result.sf >= 95f)
    }

    @Test
    fun evaluate_identicalLandmarks_jointColorsSizeIs33() {
        val lm = uniformLandmarks()
        val result = useCase.evaluate(0, lm, listOf(lm))
        assertEquals(LANDMARK_COUNT, result.jointColors.size)
    }

    @Test
    fun evaluate_identicalLandmarks_limbColorsSizeIs13() {
        val lm = uniformLandmarks()
        val result = useCase.evaluate(0, lm, listOf(lm))
        assertEquals(LIMB_COUNT, result.limbColors.size)
    }

    @Test
    fun evaluate_reversedConnectedLimb_affectedJointIsRed() {
        val ref = uniformLandmarks().toMutableList()
        val user = uniformLandmarks().toMutableList()

        ref[11] = Triple(0.3f, 0.5f, 0f)
        ref[13] = Triple(0.7f, 0.5f, 0f)
        user[11] = Triple(0.3f, 0.5f, 0f)
        user[13] = Triple(0.0f, 0.5f, 0f)

        val result = useCase.evaluate(
            matchedReferenceIndex = 0,
            userLandmarks = user,
            referenceSequence = listOf(ref)
        )
        assertEquals(
            "jointColors[11] must be RED when a connected limb is reversed",
            Color.Red,
            result.jointColors[11]
        )
    }

    @Test
    fun evaluate_shiftedJoint_s1IsReducedFromPerfect() {
        val ref = uniformLandmarks()
        val user = uniformLandmarks().toMutableList()
        user[0] = Triple(0.9f, 0.9f, 0f)

        val result = useCase.evaluate(0, user, listOf(ref))
        assertTrue("s1=${result.s1} must be < 100 when one landmark is shifted", result.s1 < 100f)
    }

    @Test
    fun evaluate_validIndex_picksCorrectReferenceFrame() {
        // Reference sequence has two frames: frame 0 is perfect match, frame 1 has shifted landmark 0.
        val lm = uniformLandmarks()
        val shiftedFrame = uniformLandmarks().toMutableList().also { it[0] = Triple(0.9f, 0.9f, 0f) }
        val refSeq = listOf(lm, shiftedFrame)

        // When matchedReferenceIndex == 0 (perfect match), sf must be high.
        val resultFrame0 = useCase.evaluate(0, lm, refSeq)
        assertTrue(resultFrame0.sf >= 95f)

        // When matchedReferenceIndex == 1 (shifted reference), comparing identical user to shifted ref
        // produces a different (lower) sf.
        val resultFrame1 = useCase.evaluate(1, lm, refSeq)
        assertTrue(
            "Frame 1 sf=${resultFrame1.sf} must be lower than frame 0 sf=${resultFrame0.sf}",
            resultFrame1.sf < resultFrame0.sf
        )
    }
}

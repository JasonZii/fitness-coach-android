package com.example.fitnesscoach.training.domain

import com.example.fitnesscoach.core.util.Constants.LANDMARK_COUNT
import com.example.fitnesscoach.core.util.Constants.LANDMARK_LEFT_ELBOW
import com.example.fitnesscoach.core.util.Constants.LANDMARK_LEFT_SHOULDER
import com.example.fitnesscoach.core.util.Constants.LANDMARK_LEFT_WRIST
import com.example.fitnesscoach.core.util.Constants.LANDMARK_RIGHT_ANKLE
import com.example.fitnesscoach.core.util.Constants.LANDMARK_RIGHT_ELBOW
import com.example.fitnesscoach.core.util.Constants.LANDMARK_RIGHT_HIP
import com.example.fitnesscoach.core.util.Constants.LANDMARK_RIGHT_KNEE
import com.example.fitnesscoach.core.util.Constants.LANDMARK_RIGHT_SHOULDER
import com.example.fitnesscoach.core.util.Constants.LANDMARK_RIGHT_WRIST
import com.example.fitnesscoach.core.util.Constants.SHOULDER_PRESS_S1_ANGLE
import com.example.fitnesscoach.core.util.Constants.SHOULDER_PRESS_S3_ANGLE
import com.example.fitnesscoach.core.util.Constants.SQUAT_S1_ANGLE
import com.example.fitnesscoach.core.util.Constants.SQUAT_S3_ANGLE
import com.example.fitnesscoach.core.util.Constants.VISIBILITY_IN_FRAME_MIN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

// ═══════════════════════════════════════════════════════════════════════════════
// LANDMARK GEOMETRY — shared by all test classes
// ═══════════════════════════════════════════════════════════════════════════════
//
// For any joint angle θ at a vertex, we place three landmarks as:
//
//   p2     (base)  = (offsetX,          0,          0)
//   vertex         = (offsetX,          1,          0)   [1 unit "below" base]
//   p1     (tip)   = (offsetX + sin θ,  1 − cos θ,  0)   [unit circle at vertex]
//
// Verification:
//   v_base = p2 − vertex = (0, −1)
//   v_tip  = p1 − vertex = (sin θ, −cos θ)
//   cos(angle) = dot / (|v_base| · |v_tip|) = cos θ   ⟹   angle = θ  ✓
//
// offsetX is used to keep left- and right-side landmarks spatially separated so
// they never share the same (x,y) coordinates when both sides are populated.

private fun buildLandmarks(
    vararg slots: Pair<Int, Triple<Float, Float, Float>>,
): List<Triple<Float, Float, Float>> {
    val lm = Array(LANDMARK_COUNT) { Triple(0f, 0f, 0f) }
    for ((idx, pt) in slots) lm[idx] = pt
    return lm.toList()
}

/** Computes the (base, vertex, tip) triple for angle [angleDeg] at [offsetX]. */
private fun angleTriple(angleDeg: Float, offsetX: Float = 0f): Triple<
        Triple<Float, Float, Float>,   // base (p2)
        Triple<Float, Float, Float>,   // vertex
        Triple<Float, Float, Float>,   // tip (p1)
        > {
    val θ      = Math.toRadians(angleDeg.toDouble())
    val base   = Triple(offsetX, 0f, 0f)
    val vertex = Triple(offsetX, 1f, 0f)
    val tip    = Triple(offsetX + sin(θ).toFloat(), (1 - cos(θ)).toFloat(), 0f)
    return Triple(base, vertex, tip)
}

// ═══════════════════════════════════════════════════════════════════════════════
// SQUAT — complete tests (reference implementation)
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Unit tests for the **squat** exercise (default constructor).
 *
 * These tests cover [CountRepsUseCase] with exerciseId = "squat". Because squat
 * is the reference implementation, these tests are intentionally exhaustive.
 * All other exercises should be tested following this pattern once their
 * thresholds have been calibrated in Sprint 4.
 *
 * Joint under test: right knee — hip (R24) → knee (R26) ← ankle (R28).
 * Angle DECREASES from S1 (~160°) to S3 (~90°).
 */
class SquatTests {

    private lateinit var useCase: CountRepsUseCase

    @Before
    fun setUp() {
        useCase = CountRepsUseCase("squat")
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Landmarks with the right knee set to [angleDeg]; all others at origin. */
    private fun makeLandmarks(angleDeg: Float): List<Triple<Float, Float, Float>> {
        val (hip, knee, ankle) = angleTriple(angleDeg)
        return buildLandmarks(
            LANDMARK_RIGHT_HIP   to hip,
            LANDMARK_RIGHT_KNEE  to knee,
            LANDMARK_RIGHT_ANKLE to ankle,
        )
    }

    /** 33-element visibility list; right-leg landmarks at [keyVis], rest 1.0. */
    private fun makeVisibilities(keyVis: Float = 1f): List<Float> =
        List(LANDMARK_COUNT) { idx ->
            if (idx == LANDMARK_RIGHT_HIP || idx == LANDMARK_RIGHT_KNEE || idx == LANDMARK_RIGHT_ANKLE)
                keyVis else 1f
        }

    // ── jointAngle unit tests ─────────────────────────────────────────────────

    @Test
    fun jointAngle_180degrees_fullyExtended() {
        val lm = makeLandmarks(180f)
        val angle = useCase.jointAngle(lm, LANDMARK_RIGHT_HIP, LANDMARK_RIGHT_KNEE, LANDMARK_RIGHT_ANKLE)
        assertNotNull(angle)
        assertEquals(180f, angle!!, 0.01f)
    }

    @Test
    fun jointAngle_90degrees_deepKneeBend() {
        val lm = makeLandmarks(90f)
        val angle = useCase.jointAngle(lm, LANDMARK_RIGHT_HIP, LANDMARK_RIGHT_KNEE, LANDMARK_RIGHT_ANKLE)
        assertNotNull(angle)
        assertEquals(90f, angle!!, 0.01f)
    }

    @Test
    fun jointAngle_degenerateZeroLength_returnsNull() {
        // All landmarks at origin → both limb vectors are zero → degenerate
        val lm = List(LANDMARK_COUNT) { Triple(0f, 0f, 0f) }
        assertNull(useCase.jointAngle(lm, LANDMARK_RIGHT_HIP, LANDMARK_RIGHT_KNEE, LANDMARK_RIGHT_ANKLE))
    }

    // ── State-machine happy path ───────────────────────────────────────────────

    @Test
    fun completeRep_S1toS2toS3toS2toS1_returnsTrue() {
        val vis = makeVisibilities()

        // In S1: deep inside standing territory
        assertFalse(useCase.update(makeLandmarks(SQUAT_S1_ANGLE + 5f), vis))

        // S1 → S2: angle drops below s1Angle
        assertFalse(useCase.update(makeLandmarks(SQUAT_S1_ANGLE - 5f), vis))

        // S2 → S3: angle reaches s3Angle territory
        assertFalse(useCase.update(makeLandmarks(SQUAT_S3_ANGLE - 5f), vis))

        // S3 → S2: angle rises back above s3Angle
        assertFalse(useCase.update(makeLandmarks(SQUAT_S3_ANGLE + 5f), vis))

        // S2 → S1: angle rises above s1Angle → rep counted
        assertTrue(useCase.update(makeLandmarks(SQUAT_S1_ANGLE + 5f), vis))
    }

    @Test
    fun shallowDip_neverReachesS3_notCounted() {
        val vis = makeVisibilities()

        // Enter S2 but stay above s3Angle
        assertFalse(useCase.update(makeLandmarks(SQUAT_S1_ANGLE - 5f), vis))
        assertFalse(useCase.update(makeLandmarks(SQUAT_S3_ANGLE + 10f), vis))

        // Return to S1 without visiting S3 → must NOT count
        assertFalse(useCase.update(makeLandmarks(SQUAT_S1_ANGLE + 5f), vis))
    }

    @Test
    fun multipleConsecutiveReps_allCounted() {
        val vis = makeVisibilities()
        val s1  = makeLandmarks(SQUAT_S1_ANGLE + 5f)
        val s2  = makeLandmarks((SQUAT_S1_ANGLE + SQUAT_S3_ANGLE) / 2f)
        val s3  = makeLandmarks(SQUAT_S3_ANGLE - 5f)

        var reps = 0
        repeat(3) {
            useCase.update(s1, vis)
            useCase.update(s2, vis)
            useCase.update(s3, vis)
            useCase.update(s2, vis)
            if (useCase.update(s1, vis)) reps++
        }
        assertEquals(3, reps)
    }

    // ── Reset ─────────────────────────────────────────────────────────────────

    @Test
    fun reset_midRep_noSpuriousCount() {
        val vis = makeVisibilities()

        // Descend into S2, reach S3
        useCase.update(makeLandmarks(SQUAT_S1_ANGLE - 5f), vis)
        useCase.update(makeLandmarks(SQUAT_S3_ANGLE - 5f), vis)

        // Reset while at the bottom
        useCase.reset()

        // Ascend past S1 — must NOT count because reset cleared hasVisitedS3
        assertFalse(useCase.update(makeLandmarks(SQUAT_S1_ANGLE + 5f), vis))
    }

    // ── Visibility filtering ───────────────────────────────────────────────────

    @Test
    fun lowVisibility_frameSkipped_stateUnchanged() {
        val vis    = makeVisibilities()
        val lowVis = makeVisibilities(VISIBILITY_IN_FRAME_MIN - 0.01f)

        // Enter S2 with good visibility
        useCase.update(makeLandmarks(SQUAT_S1_ANGLE - 5f), vis)

        // Frame at S3 depth but low visibility — must be skipped
        assertFalse(useCase.update(makeLandmarks(SQUAT_S3_ANGLE - 5f), lowVis))

        // Return to S1 without having visited S3 → no rep
        assertFalse(useCase.update(makeLandmarks(SQUAT_S1_ANGLE + 5f), vis))
    }

    @Test
    fun exactVisibilityThreshold_frameAccepted() {
        // Exactly VISIBILITY_IN_FRAME_MIN must NOT be filtered
        val vis = makeVisibilities(VISIBILITY_IN_FRAME_MIN)

        useCase.update(makeLandmarks(SQUAT_S1_ANGLE + 5f), vis)
        assertFalse(useCase.update(makeLandmarks(SQUAT_S1_ANGLE - 5f), vis))   // S1 → S2
        assertFalse(useCase.update(makeLandmarks(SQUAT_S3_ANGLE - 5f), vis))   // S2 → S3
        assertFalse(useCase.update(makeLandmarks(SQUAT_S3_ANGLE + 5f), vis))   // S3 → S2
        assertTrue( useCase.update(makeLandmarks(SQUAT_S1_ANGLE + 5f), vis))   // S2 → S1 → rep
    }

    // ── Degenerate / malformed input ──────────────────────────────────────────

    @Test
    fun degenerateLandmarks_allAtOrigin_frameSkipped() {
        val vis        = makeVisibilities()
        val degenerate = List(LANDMARK_COUNT) { Triple(0f, 0f, 0f) }
        assertFalse(useCase.update(degenerate, vis))
    }

    @Test
    fun tooFewLandmarks_returnsFalse() {
        assertFalse(useCase.update(emptyList(), emptyList()))
        assertFalse(useCase.update(List(32) { Triple(0f, 0f, 0f) }, List(32) { 1f }))
    }

    // ── Boundary conditions ────────────────────────────────────────────────────

    @Test
    fun boundary_justAboveS1Angle_staysInS1() {
        val vis = makeVisibilities()
        // 161° is above s1Angle (160°) → still in S1
        assertFalse(useCase.update(makeLandmarks(SQUAT_S1_ANGLE + 1f), vis))
        assertFalse(useCase.update(makeLandmarks(SQUAT_S1_ANGLE + 1f), vis))
    }

    @Test
    fun boundary_justBelowS1Angle_entersS2_noCountWithoutS3() {
        val vis = makeVisibilities()
        useCase.update(makeLandmarks(SQUAT_S1_ANGLE + 1f), vis)
        assertFalse(useCase.update(makeLandmarks(SQUAT_S1_ANGLE - 1f), vis))  // enters S2
        // Return to S1 without visiting S3 → no count
        assertFalse(useCase.update(makeLandmarks(SQUAT_S1_ANGLE + 1f), vis))
    }

    @Test
    fun boundary_exactlyAtS3Angle_entersS3() {
        val vis = makeVisibilities()
        useCase.update(makeLandmarks(SQUAT_S1_ANGLE + 1f), vis)
        useCase.update(makeLandmarks(SQUAT_S1_ANGLE - 1f), vis)
        // Exactly s3Angle: condition is angle <= s3Angle → enters S3
        assertFalse(useCase.update(makeLandmarks(SQUAT_S3_ANGLE), vis))
        assertFalse(useCase.update(makeLandmarks(SQUAT_S3_ANGLE + 1f), vis))  // S3 → S2
        assertTrue( useCase.update(makeLandmarks(SQUAT_S1_ANGLE + 1f), vis))  // S2 → S1 → rep
    }

    @Test
    fun boundary_justAboveS3Angle_staysInS2_noCount() {
        val vis = makeVisibilities()
        useCase.update(makeLandmarks(SQUAT_S1_ANGLE + 1f), vis)
        useCase.update(makeLandmarks(SQUAT_S1_ANGLE - 1f), vis)
        // 91° > s3Angle (90°) → stays in S2, does NOT enter S3
        assertFalse(useCase.update(makeLandmarks(SQUAT_S3_ANGLE + 1f), vis))
        assertFalse(useCase.update(makeLandmarks(SQUAT_S1_ANGLE + 1f), vis))  // back to S1, no S3 visited
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SHOULDER PRESS — bilateral tests (framework)
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Unit tests for **shoulder_press** (bilateral, front view).
 *
 * The bilateral logic — both sides must complete a cycle before a rep is counted
 * — is exercised here. The angle geometry is the same formula as squats but
 * applied to right/left elbow joints.
 *
 * Joint under test (both sides): wrist → elbow ← shoulder.
 * Angle INCREASES from S1 (~90°) to S3 (~160°).
 *
 * TODO (Sprint 4): Replace S1/S3 angle stubs with calibrated values and expand
 * these tests to cover 10-rep accuracy, per RULES.md §4.4.
 */
class ShoulderPressTests {

    private lateinit var useCase: CountRepsUseCase

    @Before
    fun setUp() {
        useCase = CountRepsUseCase("shoulder_press")
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Builds landmarks with the right elbow at [rightAngleDeg] and the left
     * elbow at [leftAngleDeg]. Sides are offset in X so their (x,y) values
     * do not interfere with each other.
     *
     * Geometry: for each side, shoulder (p2) is placed at (offsetX, 0),
     * elbow (vertex) at (offsetX, 1), and wrist (p1) is derived so that the
     * elbow interior angle equals the requested value.
     */
    private fun makeLandmarks(
        rightAngleDeg: Float,
        leftAngleDeg:  Float = rightAngleDeg,
    ): List<Triple<Float, Float, Float>> {
        val (rShoulder, rElbow, rWrist) = angleTriple(rightAngleDeg, offsetX =  10f)
        val (lShoulder, lElbow, lWrist) = angleTriple(leftAngleDeg,  offsetX = -10f)
        return buildLandmarks(
            LANDMARK_RIGHT_SHOULDER to rShoulder,
            LANDMARK_RIGHT_ELBOW    to rElbow,
            LANDMARK_RIGHT_WRIST    to rWrist,
            LANDMARK_LEFT_SHOULDER  to lShoulder,
            LANDMARK_LEFT_ELBOW     to lElbow,
            LANDMARK_LEFT_WRIST     to lWrist,
        )
    }

    private fun makeVisibilities(keyVis: Float = 1f): List<Float> =
        List(LANDMARK_COUNT) { idx ->
            if (idx in listOf(
                    LANDMARK_RIGHT_WRIST, LANDMARK_RIGHT_ELBOW, LANDMARK_RIGHT_SHOULDER,
                    LANDMARK_LEFT_WRIST,  LANDMARK_LEFT_ELBOW,  LANDMARK_LEFT_SHOULDER,
                )) keyVis else 1f
        }

    // ── Bilateral happy path ──────────────────────────────────────────────────

    @Test
    fun bothSidesSync_completeRep_returnsTrue() {
        val vis = makeVisibilities()

        // S1: both arms at start position (low angle)
        assertFalse(useCase.update(makeLandmarks(SHOULDER_PRESS_S1_ANGLE - 5f), vis))
        // S1 → S2: both arms begin rising
        assertFalse(useCase.update(makeLandmarks(SHOULDER_PRESS_S1_ANGLE + 5f), vis))
        // S2 → S3: both arms fully pressed
        assertFalse(useCase.update(makeLandmarks(SHOULDER_PRESS_S3_ANGLE + 5f), vis))
        // S3 → S2: both arms lowering
        assertFalse(useCase.update(makeLandmarks(SHOULDER_PRESS_S3_ANGLE - 5f), vis))
        // S2 → S1: both arms back to start → rep counted
        assertTrue(useCase.update(makeLandmarks(SHOULDER_PRESS_S1_ANGLE - 5f), vis))
    }

    @Test
    fun rightSideFinishesFirst_repCountedWhenLeftCatchesUp() {
        // The state machine does ONE transition per frame.
        // S3 → S1 therefore requires two frames: (S3→S2) then (S2→S1).
        // This test verifies that bilateral counting correctly waits for the
        // lagging side even when the lead side has already returned to S1.

        val vis  = makeVisibilities()
        val down = SHOULDER_PRESS_S1_ANGLE - 5f   // S1 territory  (angle ≤ s1Angle)
        val rise = SHOULDER_PRESS_S1_ANGLE + 5f   // S2 territory
        val top  = SHOULDER_PRESS_S3_ANGLE + 5f   // S3 territory
        val mid  = (SHOULDER_PRESS_S1_ANGLE + SHOULDER_PRESS_S3_ANGLE) / 2f  // S2 territory

        // Both sides start at S1
        useCase.update(makeLandmarks(down, down), vis)

        // Right S1→S2; left stays S1
        useCase.update(makeLandmarks(rise, down), vis)

        // Right S2→S3; left S1→S2
        useCase.update(makeLandmarks(top, mid), vis)

        // Right S3→S2 (first descent frame, NOT yet at S1); left stays S2
        // Right has not completed yet — no rep
        val noRepOnRightDescent = useCase.update(makeLandmarks(down, mid), vis)
        assertFalse("No rep while right is descending from S3→S2", noRepOnRightDescent)

        // Right S2→S1 (hasCompleted=true); left stays in S2
        // Right has completed its cycle; left has not — still no rep
        val noRepOnRightComplete = useCase.update(makeLandmarks(down, mid), vis)
        assertFalse("Rep must not count before left side completes", noRepOnRightComplete)

        // Right stays S1; left S2→S3
        useCase.update(makeLandmarks(down, top), vis)

        // Right stays S1; left S3→S2 (first descent frame)
        useCase.update(makeLandmarks(down, down), vis)

        // Right stays S1; left S2→S1 (hasCompleted=true) → BOTH complete → rep
        val repCounted = useCase.update(makeLandmarks(down, down), vis)
        assertTrue("Rep must be counted when lagging side catches up", repCounted)
    }

    @Test
    fun onlyOneSideCompletes_noRep() {
        val vis = makeVisibilities()

        // Right side completes a full cycle; left stays in S1 the whole time
        useCase.update(makeLandmarks(rightAngleDeg = SHOULDER_PRESS_S1_ANGLE - 5f, leftAngleDeg = SHOULDER_PRESS_S1_ANGLE - 5f), vis)
        useCase.update(makeLandmarks(rightAngleDeg = SHOULDER_PRESS_S1_ANGLE + 5f, leftAngleDeg = SHOULDER_PRESS_S1_ANGLE - 5f), vis)
        useCase.update(makeLandmarks(rightAngleDeg = SHOULDER_PRESS_S3_ANGLE + 5f, leftAngleDeg = SHOULDER_PRESS_S1_ANGLE - 5f), vis)
        useCase.update(makeLandmarks(rightAngleDeg = SHOULDER_PRESS_S3_ANGLE - 5f, leftAngleDeg = SHOULDER_PRESS_S1_ANGLE - 5f), vis)
        val result = useCase.update(makeLandmarks(rightAngleDeg = SHOULDER_PRESS_S1_ANGLE - 5f, leftAngleDeg = SHOULDER_PRESS_S1_ANGLE - 5f), vis)

        assertFalse("Rep must not count when only one side completes", result)
    }

    @Test
    fun reset_clearsBothSides_noSpuriousCount() {
        val vis = makeVisibilities()

        // Both sides reach S3
        useCase.update(makeLandmarks(SHOULDER_PRESS_S1_ANGLE + 5f), vis)
        useCase.update(makeLandmarks(SHOULDER_PRESS_S3_ANGLE + 5f), vis)

        useCase.reset()

        // Return to S1 — must NOT count because reset cleared hasVisitedS3 on both sides
        assertFalse(useCase.update(makeLandmarks(SHOULDER_PRESS_S1_ANGLE - 5f), vis))
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// OTHER EXERCISES — stubs (to be filled in after Sprint 4 calibration)
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Stub test class for **lunge_knee_raise**.
 *
 * Follow [SquatTests] exactly — joint is the same (right knee), thresholds may
 * differ after Sprint 4. Key additional concern: verify the knee-raise phase
 * cycles back to S1 correctly (see calibration notes in CountRepsUseCase).
 *
 * TODO (Sprint 4): implement after calibrating LUNGE_S1/S3_ANGLE.
 */
class LungeTests {
    // TODO: mirror SquatTests using CountRepsUseCase("lunge_knee_raise")
    //       and LUNGE_S1_ANGLE / LUNGE_S3_ANGLE constants.
}

/**
 * Stub test class for **bicep_curl**.
 *
 * Joint: right elbow (wrist→elbow←shoulder). Same unilateral pattern as squat
 * but angle DECREASES to a lower S3 (~50°). Use the same geometry helper
 * [angleTriple] with the right wrist/elbow/shoulder landmark indices.
 *
 * TODO (Sprint 4): implement after calibrating BICEP_CURL_S3_ANGLE.
 */
class BicepCurlTests {
    // TODO: mirror SquatTests using CountRepsUseCase("bicep_curl")
    //       and BICEP_CURL_S1_ANGLE / BICEP_CURL_S3_ANGLE constants.
    //       Pay attention to the narrower S3 window (arm fully curled).
}

/**
 * Stub test class for **lateral_raise**.
 *
 * Joint: shoulder abduction angle on each side (hip→shoulder←wrist). Bilateral,
 * front view — the bilateral async tests in [ShoulderPressTests] are a good
 * template. Angle INCREASES from S1 (~30°) to S3 (~80°).
 *
 * TODO (Sprint 4): implement after calibrating LATERAL_RAISE_S1/S3_ANGLE.
 *                  Also add a hip-stability guard test if one is added to the impl.
 */
class LateralRaiseTests {
    // TODO: mirror ShoulderPressTests using CountRepsUseCase("lateral_raise")
    //       and LATERAL_RAISE_S1_ANGLE / LATERAL_RAISE_S3_ANGLE constants.
    //       Use left hip/shoulder/wrist indices for the left side landmarks.
}

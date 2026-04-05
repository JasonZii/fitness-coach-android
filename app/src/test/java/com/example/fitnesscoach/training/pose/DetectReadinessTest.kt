package com.example.fitnesscoach.training.pose

import com.example.fitnesscoach.core.util.Constants.CAMERA_ANGLE_FRONT_MIN
import com.example.fitnesscoach.core.util.Constants.CAMERA_ANGLE_SIDE_MAX
import com.example.fitnesscoach.core.util.Constants.LANDMARK_COUNT
import com.example.fitnesscoach.core.util.Constants.LANDMARK_LEFT_ANKLE
import com.example.fitnesscoach.core.util.Constants.LANDMARK_LEFT_HIP
import com.example.fitnesscoach.core.util.Constants.LANDMARK_LEFT_KNEE
import com.example.fitnesscoach.core.util.Constants.LANDMARK_LEFT_SHOULDER
import com.example.fitnesscoach.core.util.Constants.LANDMARK_NOSE
import com.example.fitnesscoach.core.util.Constants.LANDMARK_RIGHT_ANKLE
import com.example.fitnesscoach.core.util.Constants.LANDMARK_RIGHT_HIP
import com.example.fitnesscoach.core.util.Constants.LANDMARK_RIGHT_KNEE
import com.example.fitnesscoach.core.util.Constants.LANDMARK_RIGHT_SHOULDER
import com.example.fitnesscoach.core.util.Constants.VISIBILITY_IN_FRAME_MIN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Unit tests for [isFullBodyInFrame] and [detectCameraAngle].
 *
 * Acceptance criteria (ALGORITHM.md Module 5):
 *   AC-1  All nine required landmarks visible (> 0.5) → isFullBodyInFrame returns true
 *   AC-2  Any required landmark at exactly 0.5 → false (must be strictly greater)
 *   AC-3  Any required landmark below 0.5 → false
 *   AC-4  Non-required landmarks below 0.5 do not affect the result
 *   AC-5  Nose–shoulder angle theta > 150° → FRONT
 *   AC-6  Nose–shoulder angle theta < 60°  → SIDE
 *   AC-7  60° ≤ theta ≤ 150°              → AMBIGUOUS
 *   AC-8  theta exactly equal to 150°      → AMBIGUOUS (not strictly > 150°)
 *   AC-9  theta exactly equal to 60°       → AMBIGUOUS (not strictly < 60°)
 */
class DetectReadinessTest {

    // ── helpers ──────────────────────────────────────────────────────────────

    /**
     * Builds a visibility list of size [LANDMARK_COUNT] with all landmarks set to
     * [defaultVisibility], then overrides specific indices with [overrides].
     */
    private fun buildVisibilities(
        defaultVisibility: Float = 1.0f,
        overrides: Map<Int, Float> = emptyMap(),
    ): List<Float> = List(LANDMARK_COUNT) { i -> overrides[i] ?: defaultVisibility }

    /**
     * Builds a raw-landmark list of size [LANDMARK_COUNT] with all entries at the
     * origin, then overrides specific indices with [overrides].
     */
    private fun buildLandmarks(
        overrides: Map<Int, Triple<Float, Float, Float>> = emptyMap(),
    ): List<Triple<Float, Float, Float>> =
        List(LANDMARK_COUNT) { i -> overrides[i] ?: Triple(0f, 0f, 0f) }

    /**
     * Computes the 2-D angle at [vertex] formed by vectors to [p1] and [p2], in degrees.
     * Used in test documentation to verify expected theta values.
     */
    private fun angleDeg(
        vertex: Pair<Float, Float>,
        p1: Pair<Float, Float>,
        p2: Pair<Float, Float>,
    ): Float {
        val v1x = p1.first - vertex.first
        val v1y = p1.second - vertex.second
        val v2x = p2.first - vertex.first
        val v2y = p2.second - vertex.second
        val cosine = ((v1x * v2x + v1y * v2y) /
            (sqrt(v1x * v1x + v1y * v1y) * sqrt(v2x * v2x + v2y * v2y))).coerceIn(-1f, 1f)
        return Math.toDegrees(kotlin.math.acos(cosine.toDouble())).toFloat()
    }

    // ── AC-1: all required landmarks visible → true ──────────────────────────

    @Test
    fun isFullBodyInFrame_allRequiredLandmarksAboveThreshold_returnsTrue() {
        val visibilities = buildVisibilities(defaultVisibility = 0.6f)
        assertTrue(isFullBodyInFrame(visibilities))
    }

    @Test
    fun isFullBodyInFrame_allRequiredLandmarksJustAboveThreshold_returnsTrue() {
        // 0.51 is just above the threshold of 0.5
        val visibilities = buildVisibilities(defaultVisibility = 0.51f)
        assertTrue(isFullBodyInFrame(visibilities))
    }

    // ── AC-2: required landmark at exactly 0.5 → false ──────────────────────

    @Test
    fun isFullBodyInFrame_noseVisibilityExactlyAtThreshold_returnsFalse() {
        val visibilities = buildVisibilities(
            defaultVisibility = 1.0f,
            overrides = mapOf(LANDMARK_NOSE to VISIBILITY_IN_FRAME_MIN),
        )
        assertFalse(isFullBodyInFrame(visibilities))
    }

    @Test
    fun isFullBodyInFrame_leftAnkleVisibilityExactlyAtThreshold_returnsFalse() {
        val visibilities = buildVisibilities(
            defaultVisibility = 1.0f,
            overrides = mapOf(LANDMARK_LEFT_ANKLE to VISIBILITY_IN_FRAME_MIN),
        )
        assertFalse(isFullBodyInFrame(visibilities))
    }

    // ── AC-3: any required landmark below threshold → false ─────────────────

    @Test
    fun isFullBodyInFrame_noseNotVisible_returnsFalse() {
        val visibilities = buildVisibilities(
            defaultVisibility = 1.0f,
            overrides = mapOf(LANDMARK_NOSE to 0.3f),
        )
        assertFalse(isFullBodyInFrame(visibilities))
    }

    @Test
    fun isFullBodyInFrame_leftShoulderNotVisible_returnsFalse() {
        val visibilities = buildVisibilities(
            defaultVisibility = 1.0f,
            overrides = mapOf(LANDMARK_LEFT_SHOULDER to 0.1f),
        )
        assertFalse(isFullBodyInFrame(visibilities))
    }

    @Test
    fun isFullBodyInFrame_rightShoulderNotVisible_returnsFalse() {
        val visibilities = buildVisibilities(
            defaultVisibility = 1.0f,
            overrides = mapOf(LANDMARK_RIGHT_SHOULDER to 0.0f),
        )
        assertFalse(isFullBodyInFrame(visibilities))
    }

    @Test
    fun isFullBodyInFrame_leftHipNotVisible_returnsFalse() {
        val visibilities = buildVisibilities(
            defaultVisibility = 1.0f,
            overrides = mapOf(LANDMARK_LEFT_HIP to 0.2f),
        )
        assertFalse(isFullBodyInFrame(visibilities))
    }

    @Test
    fun isFullBodyInFrame_rightHipNotVisible_returnsFalse() {
        val visibilities = buildVisibilities(
            defaultVisibility = 1.0f,
            overrides = mapOf(LANDMARK_RIGHT_HIP to 0.49f),
        )
        assertFalse(isFullBodyInFrame(visibilities))
    }

    @Test
    fun isFullBodyInFrame_leftKneeNotVisible_returnsFalse() {
        val visibilities = buildVisibilities(
            defaultVisibility = 1.0f,
            overrides = mapOf(LANDMARK_LEFT_KNEE to 0.0f),
        )
        assertFalse(isFullBodyInFrame(visibilities))
    }

    @Test
    fun isFullBodyInFrame_rightKneeNotVisible_returnsFalse() {
        val visibilities = buildVisibilities(
            defaultVisibility = 1.0f,
            overrides = mapOf(LANDMARK_RIGHT_KNEE to 0.4f),
        )
        assertFalse(isFullBodyInFrame(visibilities))
    }

    @Test
    fun isFullBodyInFrame_leftAnkleNotVisible_returnsFalse() {
        val visibilities = buildVisibilities(
            defaultVisibility = 1.0f,
            overrides = mapOf(LANDMARK_LEFT_ANKLE to 0.3f),
        )
        assertFalse(isFullBodyInFrame(visibilities))
    }

    @Test
    fun isFullBodyInFrame_rightAnkleNotVisible_returnsFalse() {
        val visibilities = buildVisibilities(
            defaultVisibility = 1.0f,
            overrides = mapOf(LANDMARK_RIGHT_ANKLE to 0.2f),
        )
        assertFalse(isFullBodyInFrame(visibilities))
    }

    // ── AC-4: non-required landmark below threshold does not affect result ───

    @Test
    fun isFullBodyInFrame_nonRequiredLandmarkLow_returnsTrue() {
        // Landmark index 1 (left eye inner) is not required; its visibility is 0.0
        val visibilities = buildVisibilities(
            defaultVisibility = 1.0f,
            overrides = mapOf(1 to 0.0f, 2 to 0.0f, 3 to 0.0f),
        )
        assertTrue(isFullBodyInFrame(visibilities))
    }

    // ── AC-5: theta > 150° → FRONT ───────────────────────────────────────────
    //
    // Geometry: nose at (0.5, 0.40), shoulders at (0.30, 0.41) and (0.70, 0.41).
    // v1 = (-0.20, 0.01), v2 = (0.20, 0.01)
    // cosine = (-0.04 + 0.0001) / (sqrt(0.0401))^2 ≈ -0.9950 → theta ≈ 174°

    @Test
    fun detectCameraAngle_frontView_returnsFront() {
        val landmarks = buildLandmarks(
            mapOf(
                LANDMARK_NOSE            to Triple(0.50f, 0.40f, 0f),
                LANDMARK_LEFT_SHOULDER   to Triple(0.30f, 0.41f, 0f),
                LANDMARK_RIGHT_SHOULDER  to Triple(0.70f, 0.41f, 0f),
            )
        )
        val theta = angleDeg(
            Pair(0.50f, 0.40f), Pair(0.30f, 0.41f), Pair(0.70f, 0.41f)
        )
        assertTrue("Test setup: theta ($theta) should be > $CAMERA_ANGLE_FRONT_MIN", theta > CAMERA_ANGLE_FRONT_MIN)
        assertEquals(CameraAngle.FRONT, detectCameraAngle(landmarks))
    }

    @Test
    fun detectCameraAngle_nearlyCollinearFrontView_returnsFront() {
        // Nose exactly on the horizontal line between shoulders → theta = 180°
        val landmarks = buildLandmarks(
            mapOf(
                LANDMARK_NOSE            to Triple(0.50f, 0.40f, 0f),
                LANDMARK_LEFT_SHOULDER   to Triple(0.30f, 0.40f, 0f),
                LANDMARK_RIGHT_SHOULDER  to Triple(0.70f, 0.40f, 0f),
            )
        )
        assertEquals(CameraAngle.FRONT, detectCameraAngle(landmarks))
    }

    // ── AC-6: theta < 60° → SIDE ─────────────────────────────────────────────
    //
    // Geometry: person facing left, shoulders appear stacked.
    // Nose at (0.30, 0.20), left shoulder at (0.45, 0.45), right shoulder at (0.50, 0.45).
    // v1 = (0.15, 0.25), v2 = (0.20, 0.25)
    // cosine ≈ 0.991 → theta ≈ 7.7°

    @Test
    fun detectCameraAngle_sideView_returnsSide() {
        val landmarks = buildLandmarks(
            mapOf(
                LANDMARK_NOSE            to Triple(0.30f, 0.20f, 0f),
                LANDMARK_LEFT_SHOULDER   to Triple(0.45f, 0.45f, 0f),
                LANDMARK_RIGHT_SHOULDER  to Triple(0.50f, 0.45f, 0f),
            )
        )
        val theta = angleDeg(
            Pair(0.30f, 0.20f), Pair(0.45f, 0.45f), Pair(0.50f, 0.45f)
        )
        assertTrue("Test setup: theta ($theta) should be < $CAMERA_ANGLE_SIDE_MAX", theta < CAMERA_ANGLE_SIDE_MAX)
        assertEquals(CameraAngle.SIDE, detectCameraAngle(landmarks))
    }

    @Test
    fun detectCameraAngle_nearlyIdenticalShoulders_returnsSide() {
        // Both shoulders almost at same (x, y) from camera → tiny angle
        val landmarks = buildLandmarks(
            mapOf(
                LANDMARK_NOSE            to Triple(0.50f, 0.20f, 0f),
                LANDMARK_LEFT_SHOULDER   to Triple(0.50f, 0.45f, 0f),
                LANDMARK_RIGHT_SHOULDER  to Triple(0.51f, 0.45f, 0f),
            )
        )
        assertEquals(CameraAngle.SIDE, detectCameraAngle(landmarks))
    }

    // ── AC-7: 60° ≤ theta ≤ 150° → AMBIGUOUS ────────────────────────────────
    //
    // Geometry: standard standing front pose, nose clearly above shoulders.
    // Nose at (0.50, 0.10), shoulders at (0.30, 0.40) and (0.70, 0.40).
    // v1 = (-0.20, 0.30), v2 = (0.20, 0.30)
    // cosine = (-0.04 + 0.09) / 0.13 = 0.385 → theta ≈ 67°

    @Test
    fun detectCameraAngle_ambiguousView_returnsAmbiguous() {
        val landmarks = buildLandmarks(
            mapOf(
                LANDMARK_NOSE            to Triple(0.50f, 0.10f, 0f),
                LANDMARK_LEFT_SHOULDER   to Triple(0.30f, 0.40f, 0f),
                LANDMARK_RIGHT_SHOULDER  to Triple(0.70f, 0.40f, 0f),
            )
        )
        val theta = angleDeg(
            Pair(0.50f, 0.10f), Pair(0.30f, 0.40f), Pair(0.70f, 0.40f)
        )
        assertTrue(
            "Test setup: theta ($theta) should be in [$CAMERA_ANGLE_SIDE_MAX, $CAMERA_ANGLE_FRONT_MIN]",
            theta in CAMERA_ANGLE_SIDE_MAX..CAMERA_ANGLE_FRONT_MIN
        )
        assertEquals(CameraAngle.AMBIGUOUS, detectCameraAngle(landmarks))
    }

    // ── AC-8: theta exactly 150° → AMBIGUOUS (boundary, not strictly > 150°) ──
    //
    // To achieve exactly 150°, we place:
    //   nose at origin (0, 0)
    //   left shoulder at (1, 0)  → v1 = (1, 0), |v1| = 1
    //   right shoulder at (cos 150°, sin 150°) = (-√3/2, 0.5) ≈ (-0.8660, 0.5)
    // v1·v2 = 1·(-0.8660) + 0·0.5 = -0.8660 = cos(150°) → theta = 150° exactly

    @Test
    fun detectCameraAngle_thetaExactly150_returnsAmbiguous() {
        val cos150 = cos(Math.toRadians(150.0)).toFloat()   // ≈ -0.86603
        val sin150 = kotlin.math.sin(Math.toRadians(150.0)).toFloat() // ≈ 0.5

        val landmarks = buildLandmarks(
            mapOf(
                LANDMARK_NOSE            to Triple(0f,     0f,     0f),
                LANDMARK_LEFT_SHOULDER   to Triple(1f,     0f,     0f),
                LANDMARK_RIGHT_SHOULDER  to Triple(cos150, sin150, 0f),
            )
        )
        // theta should be 150° → not strictly > 150°, so AMBIGUOUS
        assertEquals(CameraAngle.AMBIGUOUS, detectCameraAngle(landmarks))
    }

    // ── AC-9: theta exactly 60° → AMBIGUOUS (boundary, not strictly < 60°) ───
    //
    // nose at origin, left shoulder at (1, 0), right shoulder at (cos 60°, sin 60°)
    // = (0.5, √3/2) ≈ (0.5, 0.8660)
    // v1·v2 = 1·0.5 + 0·0.8660 = 0.5 = cos(60°) → theta = 60° exactly

    @Test
    fun detectCameraAngle_thetaExactly60_returnsAmbiguous() {
        val cos60 = cos(Math.toRadians(60.0)).toFloat()    // 0.5
        val sin60 = kotlin.math.sin(Math.toRadians(60.0)).toFloat() // ≈ 0.8660

        val landmarks = buildLandmarks(
            mapOf(
                LANDMARK_NOSE            to Triple(0f,    0f,    0f),
                LANDMARK_LEFT_SHOULDER   to Triple(1f,    0f,    0f),
                LANDMARK_RIGHT_SHOULDER  to Triple(cos60, sin60, 0f),
            )
        )
        // theta should be 60° → not strictly < 60°, so AMBIGUOUS
        assertEquals(CameraAngle.AMBIGUOUS, detectCameraAngle(landmarks))
    }

    // ── Degenerate: coincident landmarks → AMBIGUOUS ─────────────────────────

    @Test
    fun detectCameraAngle_shoulderCoincidentWithNose_returnsAmbiguous() {
        val landmarks = buildLandmarks(
            mapOf(
                LANDMARK_NOSE            to Triple(0.5f, 0.5f, 0f),
                LANDMARK_LEFT_SHOULDER   to Triple(0.5f, 0.5f, 0f),
                LANDMARK_RIGHT_SHOULDER  to Triple(0.7f, 0.5f, 0f),
            )
        )
        assertEquals(CameraAngle.AMBIGUOUS, detectCameraAngle(landmarks))
    }

    // ── z-coordinate is ignored ───────────────────────────────────────────────

    @Test
    fun detectCameraAngle_differentZValues_doNotAffectResult() {
        // Same x, y geometry (front view); z values differ between the two calls
        val landmarksLowZ = buildLandmarks(
            mapOf(
                LANDMARK_NOSE            to Triple(0.50f, 0.40f, 0.0f),
                LANDMARK_LEFT_SHOULDER   to Triple(0.30f, 0.41f, 0.0f),
                LANDMARK_RIGHT_SHOULDER  to Triple(0.70f, 0.41f, 0.0f),
            )
        )
        val landmarksHighZ = buildLandmarks(
            mapOf(
                LANDMARK_NOSE            to Triple(0.50f, 0.40f, 0.99f),
                LANDMARK_LEFT_SHOULDER   to Triple(0.30f, 0.41f, 0.99f),
                LANDMARK_RIGHT_SHOULDER  to Triple(0.70f, 0.41f, 0.99f),
            )
        )
        assertEquals(
            "z values must not influence the result",
            detectCameraAngle(landmarksLowZ),
            detectCameraAngle(landmarksHighZ),
        )
    }
}

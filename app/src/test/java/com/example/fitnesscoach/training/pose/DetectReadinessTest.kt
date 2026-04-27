package com.example.fitnesscoach.training.pose

import com.example.fitnesscoach.core.util.Constants.CAMERA_ANGLE_FRONT_SPREAD_RATIO_MIN
import com.example.fitnesscoach.core.util.Constants.CAMERA_ANGLE_SIDE_SPREAD_RATIO_MAX
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
import kotlin.math.abs

class DetectReadinessTest {

    private fun isLenientVisibilityReady(visibilities: List<Float>): Boolean =
        isFullBodyInFrame(visibilities, ReadinessVisibilityMode.ANY_VISIBLE_SIDE)

    private fun isStrictVisibilityReady(visibilities: List<Float>): Boolean =
        isFullBodyInFrame(visibilities, ReadinessVisibilityMode.BOTH_VISIBLE_SIDES)

    private fun buildVisibilities(
        defaultVisibility: Float = 1.0f,
        overrides: Map<Int, Float> = emptyMap(),
    ): List<Float> = List(LANDMARK_COUNT) { i -> overrides[i] ?: defaultVisibility }

    private fun buildLandmarks(
        overrides: Map<Int, Triple<Float, Float, Float>> = emptyMap(),
    ): List<Triple<Float, Float, Float>> =
        List(LANDMARK_COUNT) { i -> overrides[i] ?: Triple(0f, 0f, 0f) }

    private fun torsoSpreadRatio(
        leftShoulder: Pair<Float, Float>,
        rightShoulder: Pair<Float, Float>,
        leftHip: Pair<Float, Float>,
        rightHip: Pair<Float, Float>,
    ): Float {
        val shoulderWidth = abs(leftShoulder.first - rightShoulder.first)
        val hipWidth = abs(leftHip.first - rightHip.first)
        val shoulderMidY = (leftShoulder.second + rightShoulder.second) / 2f
        val hipMidY = (leftHip.second + rightHip.second) / 2f
        val torsoHeight = abs(hipMidY - shoulderMidY)
        return ((shoulderWidth + hipWidth) / 2f) / torsoHeight
    }

    @Test
    fun isFullBodyInFrame_allRequiredLandmarksAboveThreshold_returnsTrue() {
        val visibilities = buildVisibilities(defaultVisibility = 0.6f)
        assertTrue(isStrictVisibilityReady(visibilities))
    }

    @Test
    fun isFullBodyInFrame_allRequiredLandmarksJustAboveThreshold_returnsTrue() {
        val visibilities = buildVisibilities(defaultVisibility = 0.51f)
        assertTrue(isStrictVisibilityReady(visibilities))
    }

    @Test
    fun isFullBodyInFrame_noseVisibilityExactlyAtThreshold_returnsFalse() {
        val visibilities = buildVisibilities(overrides = mapOf(LANDMARK_NOSE to VISIBILITY_IN_FRAME_MIN))
        assertFalse(isStrictVisibilityReady(visibilities))
    }

    @Test
    fun isFullBodyInFrame_leftAnkleVisibilityExactlyAtThreshold_returnsFalse() {
        val visibilities = buildVisibilities(overrides = mapOf(LANDMARK_LEFT_ANKLE to VISIBILITY_IN_FRAME_MIN))
        assertFalse(isStrictVisibilityReady(visibilities))
    }

    @Test
    fun isFullBodyInFrame_noseNotVisible_returnsFalse() {
        val visibilities = buildVisibilities(overrides = mapOf(LANDMARK_NOSE to 0.0f))
        assertFalse(isStrictVisibilityReady(visibilities))
    }

    @Test
    fun isFullBodyInFrame_leftShoulderNotVisible_returnsFalse() {
        val visibilities = buildVisibilities(overrides = mapOf(LANDMARK_LEFT_SHOULDER to 0.0f))
        assertFalse(isStrictVisibilityReady(visibilities))
    }

    @Test
    fun isFullBodyInFrame_rightShoulderNotVisible_returnsFalse() {
        val visibilities = buildVisibilities(overrides = mapOf(LANDMARK_RIGHT_SHOULDER to 0.0f))
        assertFalse(isStrictVisibilityReady(visibilities))
    }

    @Test
    fun isFullBodyInFrame_leftHipNotVisible_returnsFalse() {
        val visibilities = buildVisibilities(overrides = mapOf(LANDMARK_LEFT_HIP to 0.2f))
        assertFalse(isStrictVisibilityReady(visibilities))
    }

    @Test
    fun isFullBodyInFrame_rightHipNotVisible_returnsFalse() {
        val visibilities = buildVisibilities(overrides = mapOf(LANDMARK_RIGHT_HIP to 0.49f))
        assertFalse(isStrictVisibilityReady(visibilities))
    }

    @Test
    fun isFullBodyInFrame_leftKneeNotVisible_returnsFalse() {
        val visibilities = buildVisibilities(overrides = mapOf(LANDMARK_LEFT_KNEE to 0.0f))
        assertFalse(isStrictVisibilityReady(visibilities))
    }

    @Test
    fun isFullBodyInFrame_rightKneeNotVisible_returnsFalse() {
        val visibilities = buildVisibilities(overrides = mapOf(LANDMARK_RIGHT_KNEE to 0.4f))
        assertFalse(isStrictVisibilityReady(visibilities))
    }

    @Test
    fun isFullBodyInFrame_leftAnkleNotVisible_returnsFalse() {
        val visibilities = buildVisibilities(overrides = mapOf(LANDMARK_LEFT_ANKLE to 0.3f))
        assertFalse(isStrictVisibilityReady(visibilities))
    }

    @Test
    fun isFullBodyInFrame_rightAnkleNotVisible_returnsFalse() {
        val visibilities = buildVisibilities(overrides = mapOf(LANDMARK_RIGHT_ANKLE to 0.2f))
        assertFalse(isStrictVisibilityReady(visibilities))
    }

    @Test
    fun isFullBodyInFrame_nonRequiredLandmarkLow_returnsTrue() {
        val visibilities = buildVisibilities(overrides = mapOf(1 to 0.0f, 2 to 0.0f, 3 to 0.0f))
        assertTrue(isStrictVisibilityReady(visibilities))
    }

    @Test
    fun isFullBodyInFrame_lenientMode_allowsOneSideOfEachPairToBeMissing() {
        val visibilities = buildVisibilities(
            overrides = mapOf(
                LANDMARK_LEFT_SHOULDER to 0.0f,
                LANDMARK_LEFT_HIP to 0.0f,
                LANDMARK_LEFT_KNEE to 0.0f,
                LANDMARK_LEFT_ANKLE to 0.0f,
            ),
        )
        assertTrue(isLenientVisibilityReady(visibilities))
    }

    @Test
    fun isFullBodyInFrame_lenientMode_stillFailsWhenBothSidesOfAPairAreMissing() {
        val visibilities = buildVisibilities(
            overrides = mapOf(
                LANDMARK_LEFT_SHOULDER to 0.0f,
                LANDMARK_RIGHT_SHOULDER to 0.0f,
            ),
        )
        assertFalse(isLenientVisibilityReady(visibilities))
    }

    @Test
    fun detectCameraAngle_frontView_returnsFront() {
        val landmarks = buildLandmarks(
            overrides = mapOf(
                LANDMARK_LEFT_SHOULDER to Triple(0.30f, 0.30f, 0f),
                LANDMARK_RIGHT_SHOULDER to Triple(0.70f, 0.30f, 0f),
                LANDMARK_LEFT_HIP to Triple(0.34f, 0.60f, 0f),
                LANDMARK_RIGHT_HIP to Triple(0.66f, 0.60f, 0f),
            ),
        )
        val spreadRatio = torsoSpreadRatio(
            Pair(0.30f, 0.30f),
            Pair(0.70f, 0.30f),
            Pair(0.34f, 0.60f),
            Pair(0.66f, 0.60f),
        )
        assertTrue(spreadRatio >= CAMERA_ANGLE_FRONT_SPREAD_RATIO_MIN)
        assertEquals(CameraAngle.FRONT, detectCameraAngle(landmarks))
    }

    @Test
    fun detectCameraAngle_farFrontView_withoutReliableFace_stillReturnsFront() {
        val landmarks = buildLandmarks(
            overrides = mapOf(
                LANDMARK_NOSE to Triple(0.50f, 0.02f, 0f),
                LANDMARK_LEFT_SHOULDER to Triple(0.31f, 0.32f, 0f),
                LANDMARK_RIGHT_SHOULDER to Triple(0.69f, 0.32f, 0f),
                LANDMARK_LEFT_HIP to Triple(0.35f, 0.63f, 0f),
                LANDMARK_RIGHT_HIP to Triple(0.65f, 0.63f, 0f),
            ),
        )
        assertEquals(CameraAngle.FRONT, detectCameraAngle(landmarks))
    }

    @Test
    fun detectCameraAngle_sideView_returnsSide() {
        val landmarks = buildLandmarks(
            overrides = mapOf(
                LANDMARK_LEFT_SHOULDER to Triple(0.48f, 0.30f, 0f),
                LANDMARK_RIGHT_SHOULDER to Triple(0.52f, 0.30f, 0f),
                LANDMARK_LEFT_HIP to Triple(0.49f, 0.62f, 0f),
                LANDMARK_RIGHT_HIP to Triple(0.51f, 0.62f, 0f),
            ),
        )
        val spreadRatio = torsoSpreadRatio(
            Pair(0.48f, 0.30f),
            Pair(0.52f, 0.30f),
            Pair(0.49f, 0.62f),
            Pair(0.51f, 0.62f),
        )
        assertTrue(spreadRatio <= CAMERA_ANGLE_SIDE_SPREAD_RATIO_MAX)
        assertEquals(CameraAngle.SIDE, detectCameraAngle(landmarks))
    }

    @Test
    fun detectCameraAngle_nearlyIdenticalShoulders_returnsSide() {
        val landmarks = buildLandmarks(
            overrides = mapOf(
                LANDMARK_LEFT_SHOULDER to Triple(0.50f, 0.33f, 0f),
                LANDMARK_RIGHT_SHOULDER to Triple(0.51f, 0.33f, 0f),
                LANDMARK_LEFT_HIP to Triple(0.50f, 0.64f, 0f),
                LANDMARK_RIGHT_HIP to Triple(0.51f, 0.64f, 0f),
            ),
        )
        assertEquals(CameraAngle.SIDE, detectCameraAngle(landmarks))
    }

    @Test
    fun detectCameraAngle_ambiguousView_returnsAmbiguous() {
        val landmarks = buildLandmarks(
            overrides = mapOf(
                LANDMARK_LEFT_SHOULDER to Triple(0.42f, 0.30f, 0f),
                LANDMARK_RIGHT_SHOULDER to Triple(0.58f, 0.30f, 0f),
                LANDMARK_LEFT_HIP to Triple(0.44f, 0.60f, 0f),
                LANDMARK_RIGHT_HIP to Triple(0.56f, 0.60f, 0f),
            ),
        )
        val spreadRatio = torsoSpreadRatio(
            Pair(0.42f, 0.30f),
            Pair(0.58f, 0.30f),
            Pair(0.44f, 0.60f),
            Pair(0.56f, 0.60f),
        )
        assertTrue(
            spreadRatio > CAMERA_ANGLE_SIDE_SPREAD_RATIO_MAX &&
                spreadRatio < CAMERA_ANGLE_FRONT_SPREAD_RATIO_MIN,
        )
        assertEquals(CameraAngle.AMBIGUOUS, detectCameraAngle(landmarks))
    }

    @Test
    fun detectCameraAngle_degenerateTorsoHeight_returnsAmbiguous() {
        val landmarks = buildLandmarks(
            overrides = mapOf(
                LANDMARK_LEFT_SHOULDER to Triple(0.30f, 0.50f, 0f),
                LANDMARK_RIGHT_SHOULDER to Triple(0.70f, 0.50f, 0f),
                LANDMARK_LEFT_HIP to Triple(0.35f, 0.50f, 0f),
                LANDMARK_RIGHT_HIP to Triple(0.65f, 0.50f, 0f),
            ),
        )
        assertEquals(CameraAngle.AMBIGUOUS, detectCameraAngle(landmarks))
    }

    @Test
    fun detectCameraAngle_partialTurn_returnsAmbiguous() {
        val landmarks = buildLandmarks(
            overrides = mapOf(
                LANDMARK_LEFT_SHOULDER to Triple(0.41f, 0.30f, 0f),
                LANDMARK_RIGHT_SHOULDER to Triple(0.59f, 0.30f, 0f),
                LANDMARK_LEFT_HIP to Triple(0.445f, 0.62f, 0f),
                LANDMARK_RIGHT_HIP to Triple(0.555f, 0.62f, 0f),
            ),
        )
        assertEquals(CameraAngle.AMBIGUOUS, detectCameraAngle(landmarks))
    }

    @Test
    fun detectCameraAngle_differentZValues_doNotAffectResult() {
        val landmarksLowZ = buildLandmarks(
            overrides = mapOf(
                LANDMARK_LEFT_SHOULDER to Triple(0.30f, 0.30f, 0.0f),
                LANDMARK_RIGHT_SHOULDER to Triple(0.70f, 0.30f, 0.0f),
                LANDMARK_LEFT_HIP to Triple(0.34f, 0.60f, 0.0f),
                LANDMARK_RIGHT_HIP to Triple(0.66f, 0.60f, 0.0f),
            ),
        )
        val landmarksHighZ = buildLandmarks(
            overrides = mapOf(
                LANDMARK_LEFT_SHOULDER to Triple(0.30f, 0.30f, 0.99f),
                LANDMARK_RIGHT_SHOULDER to Triple(0.70f, 0.30f, 0.99f),
                LANDMARK_LEFT_HIP to Triple(0.34f, 0.60f, 0.99f),
                LANDMARK_RIGHT_HIP to Triple(0.66f, 0.60f, 0.99f),
            ),
        )
        assertEquals(detectCameraAngle(landmarksLowZ), detectCameraAngle(landmarksHighZ))
    }
}

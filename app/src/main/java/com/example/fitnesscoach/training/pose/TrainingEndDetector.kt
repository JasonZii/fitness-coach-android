package com.example.fitnesscoach.training.pose

import com.example.fitnesscoach.core.util.Constants.END_CLOSE_HOLD_MS
import com.example.fitnesscoach.core.util.Constants.END_SHOULDER_WIDTH_RATIO
import com.example.fitnesscoach.core.util.Constants.LANDMARK_LEFT_SHOULDER
import com.example.fitnesscoach.core.util.Constants.LANDMARK_RIGHT_SHOULDER
import com.example.fitnesscoach.core.util.Constants.VISIBILITY_IN_FRAME_MIN
import kotlin.math.sqrt

enum class TrainingEndState {
    /** Normal: training data should be accumulated. */
    ACTIVE,
    /** User is too close to the camera — skip algorithm, await manual stop. */
    DETECTED,
}

/**
 * Detects when the user has walked close to the camera, indicating intent to end the session.
 *
 * **Condition – too close**: The Euclidean distance between the two shoulders exceeds
 * [END_SHOULDER_WIDTH_RATIO] (1.5×) times the baseline shoulder width captured at
 * [onTrainingStart]. The condition must hold continuously for [END_CLOSE_HOLD_MS] (500 ms)
 * before [DETECTED] is returned; a single noisy frame will not trigger it.
 * Frames where either shoulder landmark has visibility below [VISIBILITY_IN_FRAME_MIN] are
 * treated as "not close" and reset the hold timer, preventing MediaPipe extrapolation
 * artifacts from causing false detections.
 * Side-view exercises are exempt because the side-on baseline shoulder width is near-zero,
 * which would cause false positives on any movement.
 *
 * When [DETECTED], the caller should silently skip the training algorithm for that frame.
 * No UI pause banner is shown; the user ends the session by pressing Stop.
 */
class TrainingEndDetector {

    private var baselineShoulderWidth: Float = 0f
    private var requiredCameraAngle: CameraAngle = CameraAngle.FRONT
    private var tooCloseStartedAt: Long? = null

    /**
     * Records the baseline shoulder width from the first training frame.
     * Must be called exactly once when the session transitions from READINESS to TRAINING.
     */
    fun onTrainingStart(
        landmarks: List<Triple<Float, Float, Float>>,
        requiredCameraAngle: CameraAngle,
    ) {
        baselineShoulderWidth = shoulderWidth(landmarks)
        this.requiredCameraAngle = requiredCameraAngle
    }

    /**
     * Evaluates the current frame for the too-close condition.
     *
     * [visibilities] filters frames where either shoulder landmark is unreliable (below
     * [VISIBILITY_IN_FRAME_MIN]); such frames reset the hold timer and return [ACTIVE].
     * [frameTimestamp] drives the [END_CLOSE_HOLD_MS] hold period: [DETECTED] is only
     * returned after the condition has been continuously true for 500 ms.
     */
    fun update(
        landmarks: List<Triple<Float, Float, Float>>,
        visibilities: List<Float>,
        frameTimestamp: Long,
    ): TrainingEndState {
        if (!isTooClose(landmarks, visibilities)) {
            tooCloseStartedAt = null
            return TrainingEndState.ACTIVE
        }
        val startedAt = tooCloseStartedAt ?: frameTimestamp.also { tooCloseStartedAt = it }
        return if (frameTimestamp - startedAt >= END_CLOSE_HOLD_MS)
            TrainingEndState.DETECTED
        else
            TrainingEndState.ACTIVE
    }

    /** Resets all state. Call when the user stops training or switches exercise. */
    fun reset() {
        baselineShoulderWidth = 0f
        requiredCameraAngle = CameraAngle.FRONT
        tooCloseStartedAt = null
    }

    private fun isTooClose(
        landmarks: List<Triple<Float, Float, Float>>,
        visibilities: List<Float>,
    ): Boolean {
        if (requiredCameraAngle == CameraAngle.SIDE) return false
        if (baselineShoulderWidth < 1e-6f) return false
        if (visibilities[LANDMARK_LEFT_SHOULDER]  < VISIBILITY_IN_FRAME_MIN) return false
        if (visibilities[LANDMARK_RIGHT_SHOULDER] < VISIBILITY_IN_FRAME_MIN) return false
        return shoulderWidth(landmarks) > baselineShoulderWidth * END_SHOULDER_WIDTH_RATIO
    }

    private fun shoulderWidth(landmarks: List<Triple<Float, Float, Float>>): Float {
        val left  = landmarks[LANDMARK_LEFT_SHOULDER]
        val right = landmarks[LANDMARK_RIGHT_SHOULDER]
        val dx = left.first  - right.first
        val dy = left.second - right.second
        return sqrt(dx * dx + dy * dy)
    }
}
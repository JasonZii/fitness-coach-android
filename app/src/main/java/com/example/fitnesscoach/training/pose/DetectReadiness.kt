package com.example.fitnesscoach.training.pose

import com.example.fitnesscoach.core.util.Constants.CAMERA_ANGLE_FRONT_MIN
import com.example.fitnesscoach.core.util.Constants.CAMERA_ANGLE_SIDE_MAX
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
import kotlin.math.acos
import kotlin.math.sqrt

/**
 * Landmark indices required for the full-body-in-frame check (ALGORITHM.md Module 5).
 * All nine must have visibility > VISIBILITY_IN_FRAME_MIN to pass.
 */
private val FULL_BODY_LANDMARK_INDICES = intArrayOf(
    LANDMARK_NOSE,
    LANDMARK_LEFT_SHOULDER,
    LANDMARK_RIGHT_SHOULDER,
    LANDMARK_LEFT_HIP,
    LANDMARK_RIGHT_HIP,
    LANDMARK_LEFT_KNEE,
    LANDMARK_RIGHT_KNEE,
    LANDMARK_LEFT_ANKLE,
    LANDMARK_RIGHT_ANKLE,
)

/**
 * Camera viewing angle as classified by [detectCameraAngle].
 * See ALGORITHM.md Module 5 for threshold definitions.
 */
enum class CameraAngle {
    /** Nose–shoulder angle theta > 150°: person faces the camera. */
    FRONT,
    /** Nose–shoulder angle theta < 60°: person stands sideways. */
    SIDE,
    /** 60° ≤ theta ≤ 150°: angle is unclear; prompt user to adjust. */
    AMBIGUOUS,
}

/**
 * Returns true when all key body landmarks are visible in the frame.
 *
 * Checks MediaPipe visibility scores for landmark indices 0, 11, 12, 23, 24, 25, 26, 27, 28.
 * Every checked landmark must have a visibility score strictly greater than
 * [VISIBILITY_IN_FRAME_MIN] (0.5). See ALGORITHM.md Module 5.
 *
 * @param visibilities Per-landmark visibility scores as output by MediaPipe.
 *                     Index matches the MediaPipe BlazePose landmark index (0–32).
 *                     The list must contain at least 29 elements (up to index 28).
 * @return true if every required landmark has visibility > 0.5; false otherwise.
 */
fun isFullBodyInFrame(visibilities: List<Float>): Boolean {
    return FULL_BODY_LANDMARK_INDICES.all { idx -> visibilities[idx] > VISIBILITY_IN_FRAME_MIN }
}

/**
 * Classifies the camera viewing angle from the nose–shoulder geometry.
 *
 * Computes the angle theta at the nose (landmark 0) formed by the vectors to the
 * left shoulder (landmark 11) and right shoulder (landmark 12), using the same
 * formula as Module 4 (cosine similarity on 2-D (x, y) coordinates).
 *
 * Classification thresholds (ALGORITHM.md Module 5):
 * - theta > [CAMERA_ANGLE_FRONT_MIN] (150°) → [CameraAngle.FRONT]
 * - theta < [CAMERA_ANGLE_SIDE_MAX]  (60°)  → [CameraAngle.SIDE]
 * - otherwise                               → [CameraAngle.AMBIGUOUS]
 *
 * If either shoulder vector has near-zero length (degenerate landmarks),
 * [CameraAngle.AMBIGUOUS] is returned.
 *
 * @param landmarks Raw MediaPipe landmarks for one frame as (x, y, z) triples.
 *                  The list must contain at least 13 elements (up to index 12).
 *                  Only the x and y components are used; z is ignored.
 * @return The detected [CameraAngle].
 */
fun detectCameraAngle(landmarks: List<Triple<Float, Float, Float>>): CameraAngle {
    val nose    = landmarks[LANDMARK_NOSE]
    val leftSh  = landmarks[LANDMARK_LEFT_SHOULDER]
    val rightSh = landmarks[LANDMARK_RIGHT_SHOULDER]

    // Vectors from nose to each shoulder (z ignored per ALGORITHM.md)
    val toLeftShX = leftSh.first  - nose.first
    val toLeftShY = leftSh.second - nose.second
    val toRightShX = rightSh.first  - nose.first
    val toRightShY = rightSh.second - nose.second

    val leftShLen  = sqrt(toLeftShX  * toLeftShX  + toLeftShY  * toLeftShY)
    val rightShLen = sqrt(toRightShX * toRightShX + toRightShY * toRightShY)

    if (leftShLen < 1e-6f || rightShLen < 1e-6f) return CameraAngle.AMBIGUOUS

    // Clamp to [-1, 1] to guard against floating-point rounding before acos
    val cosine = ((toLeftShX * toRightShX + toLeftShY * toRightShY) / (leftShLen * rightShLen)).coerceIn(-1f, 1f)
    val thetaDeg = Math.toDegrees(acos(cosine.toDouble())).toFloat()

    return when {
        thetaDeg > CAMERA_ANGLE_FRONT_MIN -> CameraAngle.FRONT
        thetaDeg < CAMERA_ANGLE_SIDE_MAX  -> CameraAngle.SIDE
        else                              -> CameraAngle.AMBIGUOUS
    }
}

package com.example.fitnesscoach.training.pose

import com.example.fitnesscoach.core.util.Constants.CAMERA_ANGLE_FRONT_SPREAD_RATIO_MIN
import com.example.fitnesscoach.core.util.Constants.CAMERA_ANGLE_SIDE_SPREAD_RATIO_MAX
import com.example.fitnesscoach.core.util.Constants.LANDMARK_LEFT_ANKLE
import com.example.fitnesscoach.core.util.Constants.LANDMARK_LEFT_ELBOW
import com.example.fitnesscoach.core.util.Constants.LANDMARK_LEFT_HIP
import com.example.fitnesscoach.core.util.Constants.LANDMARK_LEFT_KNEE
import com.example.fitnesscoach.core.util.Constants.LANDMARK_LEFT_SHOULDER
import com.example.fitnesscoach.core.util.Constants.LANDMARK_LEFT_WRIST
import com.example.fitnesscoach.core.util.Constants.LANDMARK_NOSE
import com.example.fitnesscoach.core.util.Constants.LANDMARK_RIGHT_ANKLE
import com.example.fitnesscoach.core.util.Constants.LANDMARK_RIGHT_ELBOW
import com.example.fitnesscoach.core.util.Constants.LANDMARK_RIGHT_HIP
import com.example.fitnesscoach.core.util.Constants.LANDMARK_RIGHT_KNEE
import com.example.fitnesscoach.core.util.Constants.LANDMARK_RIGHT_SHOULDER
import com.example.fitnesscoach.core.util.Constants.LANDMARK_RIGHT_WRIST
import com.example.fitnesscoach.core.util.Constants.VISIBILITY_IN_FRAME_MIN
import kotlin.math.abs

// Bilateral landmark pairs (left, right) - shoulder / hip / knee / ankle
private val BILATERAL_PAIRS = listOf(
    LANDMARK_LEFT_SHOULDER to LANDMARK_RIGHT_SHOULDER,
    LANDMARK_LEFT_HIP      to LANDMARK_RIGHT_HIP,
    LANDMARK_LEFT_KNEE     to LANDMARK_RIGHT_KNEE,
    LANDMARK_LEFT_ANKLE    to LANDMARK_RIGHT_ANKLE,
    LANDMARK_LEFT_ELBOW to LANDMARK_RIGHT_ELBOW,
    LANDMARK_LEFT_WRIST to LANDMARK_RIGHT_WRIST,
)

// Upper-body bilateral pairs (left, right) - shoulder / elbow / wrist / hip
private val UPPER_BODY_BILATERAL_PAIRS = listOf(
    LANDMARK_LEFT_SHOULDER to LANDMARK_RIGHT_SHOULDER,
    LANDMARK_LEFT_ELBOW    to LANDMARK_RIGHT_ELBOW,
    LANDMARK_LEFT_WRIST    to LANDMARK_RIGHT_WRIST,
    LANDMARK_LEFT_HIP      to LANDMARK_RIGHT_HIP,
)

/**
 * Camera viewing angle as classified by [detectCameraAngle].
 */
enum class CameraAngle {
    FRONT,
    SIDE,
    AMBIGUOUS,
}

enum class SideViewDirection {
    LEFT,
    RIGHT,
    UNKNOWN,
    NONE,
}

/**
 * Visibility rule used by the readiness gate before recording starts.
 */
enum class ReadinessVisibilityMode {
    /** Nose + at least one landmark from each left/right pair must be visible. */
    ANY_VISIBLE_SIDE,

    /** Nose + both landmarks from every left/right pair must be visible. */
    BOTH_VISIBLE_SIDES,
}

/**
 * Returns true when key body landmarks are visible in the frame.
 *
 * The check is adapted to the exercise's configured readiness visibility mode:
 * - [ReadinessVisibilityMode.ANY_VISIBLE_SIDE]: nose + at least one landmark of each
 *   bilateral pair (shoulder/hip/knee/ankle).
 * - [ReadinessVisibilityMode.BOTH_VISIBLE_SIDES]: nose + both landmarks of every bilateral
 *   pair must exceed the threshold.
 */
fun isFullBodyInFrame(
    visibilities: List<Float>,
    visibilityMode: ReadinessVisibilityMode,
): Boolean {
    if (visibilities[LANDMARK_NOSE] <= VISIBILITY_IN_FRAME_MIN) return false
    return when (visibilityMode) {
        ReadinessVisibilityMode.ANY_VISIBLE_SIDE -> {
            BILATERAL_PAIRS.all { (left, right) ->
                visibilities[left] > VISIBILITY_IN_FRAME_MIN || visibilities[right] > VISIBILITY_IN_FRAME_MIN
            }
        }
        ReadinessVisibilityMode.BOTH_VISIBLE_SIDES -> {
            BILATERAL_PAIRS.all { (left, right) ->
                visibilities[left] > VISIBILITY_IN_FRAME_MIN && visibilities[right] > VISIBILITY_IN_FRAME_MIN
            }
        }
    }
}

/**
 * Returns true when upper-body landmarks are visible in the frame.
 *
 * Used when [requiresFullBody] is false (e.g. shoulder press, lateral raise, bicep curl).
 * Checks nose + shoulder / elbow / wrist / hip bilateral pairs, applying the same
 * [ReadinessVisibilityMode] logic as [isFullBodyInFrame].
 */
fun isUpperBodyInFrame(
    visibilities: List<Float>,
    visibilityMode: ReadinessVisibilityMode,
): Boolean {
    if (visibilities[LANDMARK_NOSE] <= VISIBILITY_IN_FRAME_MIN) return false
    return when (visibilityMode) {
        ReadinessVisibilityMode.ANY_VISIBLE_SIDE -> {
            UPPER_BODY_BILATERAL_PAIRS.all { (left, right) ->
                visibilities[left] > VISIBILITY_IN_FRAME_MIN || visibilities[right] > VISIBILITY_IN_FRAME_MIN
            }
        }
        ReadinessVisibilityMode.BOTH_VISIBLE_SIDES -> {
            UPPER_BODY_BILATERAL_PAIRS.all { (left, right) ->
                visibilities[left] > VISIBILITY_IN_FRAME_MIN && visibilities[right] > VISIBILITY_IN_FRAME_MIN
            }
        }
    }
}

/**
 * Classifies the camera viewing angle from torso spread instead of facial geometry.
 *
 * This is intentionally more tolerant than the old nose-based heuristic. When the user
 * steps back so the full body fits in frame, facial landmarks become small and unstable,
 * but the shoulder/hip silhouette is still reliable. We therefore infer the view angle
 * from how wide the torso appears relative to torso height.
 *
 * 1. Measure shoulder width and hip width in the x-axis.
 * 2. Measure torso height using the shoulder and hip midpoints.
 * 3. Divide the average horizontal spread by torso height.
 *
 * A front-facing torso has a large horizontal spread; a side-facing torso collapses into
 * a narrow profile. The ratio is scale-invariant with camera distance.
 */
fun detectCameraAngle(landmarks: List<Triple<Float, Float, Float>>): CameraAngle {
    val leftShoulder = landmarks[LANDMARK_LEFT_SHOULDER]
    val rightShoulder = landmarks[LANDMARK_RIGHT_SHOULDER]
    val leftHip = landmarks[LANDMARK_LEFT_HIP]
    val rightHip = landmarks[LANDMARK_RIGHT_HIP]

    val shoulderWidth = abs(leftShoulder.first - rightShoulder.first)
    val hipWidth = abs(leftHip.first - rightHip.first)
    val shoulderMidY = (leftShoulder.second + rightShoulder.second) / 2f
    val hipMidY = (leftHip.second + rightHip.second) / 2f
    val torsoHeight = abs(hipMidY - shoulderMidY)

    if (torsoHeight < 1e-6f) return CameraAngle.AMBIGUOUS

    val spreadRatio = ((shoulderWidth + hipWidth) / 2f) / torsoHeight

    return when {
        spreadRatio >= CAMERA_ANGLE_FRONT_SPREAD_RATIO_MIN -> CameraAngle.FRONT
        spreadRatio <= CAMERA_ANGLE_SIDE_SPREAD_RATIO_MAX -> CameraAngle.SIDE
        else -> CameraAngle.AMBIGUOUS
    }
}

fun detectSideViewDirection(landmarks: List<Triple<Float, Float, Float>>): SideViewDirection {
    val nose = landmarks[LANDMARK_NOSE]
    val leftShoulder = landmarks[LANDMARK_LEFT_SHOULDER]
    val rightShoulder = landmarks[LANDMARK_RIGHT_SHOULDER]
    val leftHip = landmarks[LANDMARK_LEFT_HIP]
    val rightHip = landmarks[LANDMARK_RIGHT_HIP]

    val torsoCenterX = (
        leftShoulder.first +
            rightShoulder.first +
            leftHip.first +
            rightHip.first
        ) / 4f
    val noseOffsetX = nose.first - torsoCenterX

    return when {
        noseOffsetX <= -0.04f -> SideViewDirection.LEFT
        noseOffsetX >= 0.04f -> SideViewDirection.RIGHT
        else -> SideViewDirection.UNKNOWN
    }
}

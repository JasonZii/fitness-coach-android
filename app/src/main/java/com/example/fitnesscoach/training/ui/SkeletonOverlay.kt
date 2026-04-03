package com.example.fitnesscoach.training.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.fitnesscoach.core.util.Constants.LANDMARK_COUNT
import com.example.fitnesscoach.core.util.Constants.LIMB_COUNT

// Joint radius and limb stroke width in dp — centralised here so tests can reference them.
internal const val SKELETON_JOINT_RADIUS_DP = 6f
internal const val SKELETON_LIMB_STROKE_DP = 4f

/**
 * 13 limb connections matching ALGORITHM.md Module 3.
 *
 * Each entry is (startLandmarkIndex, endLandmarkIndex).
 * Limb 12 (spine) is a special case: (-1, -1) signals that both endpoints
 * are midpoints — start = midpoint(landmark[11], landmark[12]),
 * end = midpoint(landmark[23], landmark[24]).
 */
internal val LIMB_CONNECTIONS: List<Pair<Int, Int>> = listOf(
    Pair(11, 13), // 0  Left upper arm
    Pair(13, 15), // 1  Left forearm
    Pair(12, 14), // 2  Right upper arm
    Pair(14, 16), // 3  Right forearm
    Pair(11, 23), // 4  Left torso
    Pair(12, 24), // 5  Right torso
    Pair(23, 25), // 6  Left thigh
    Pair(24, 26), // 7  Right thigh
    Pair(25, 27), // 8  Left shin
    Pair(26, 28), // 9  Right shin
    Pair(11, 12), // 10 Shoulder line
    Pair(23, 24), // 11 Hip line
    Pair(-1, -1)  // 12 Spine (midpoint endpoints — see above)
)

/**
 * Project a single landmark from MediaPipe normalised coordinates ([0,1]) to
 * canvas pixel coordinates given [canvasWidth] × [canvasHeight].
 *
 * z is ignored per ALGORITHM.md §MediaPipe Output Format.
 */
internal fun projectLandmark(
    landmark: Triple<Float, Float, Float>,
    canvasWidth: Float,
    canvasHeight: Float
): Pair<Float, Float> = Pair(landmark.first * canvasWidth, landmark.second * canvasHeight)

/**
 * Compute the two endpoints of the spine limb in canvas pixel coordinates.
 *
 * Spine start = midpoint(landmark[11], landmark[12])
 * Spine end   = midpoint(landmark[23], landmark[24])
 */
internal fun computeSpineEndpoints(
    landmarks: List<Triple<Float, Float, Float>>,
    canvasWidth: Float,
    canvasHeight: Float
): Pair<Pair<Float, Float>, Pair<Float, Float>> {
    val shoulderMidX = (landmarks[11].first + landmarks[12].first) / 2f
    val shoulderMidY = (landmarks[11].second + landmarks[12].second) / 2f
    val hipMidX = (landmarks[23].first + landmarks[24].first) / 2f
    val hipMidY = (landmarks[23].second + landmarks[24].second) / 2f
    return Pair(
        Pair(shoulderMidX * canvasWidth, shoulderMidY * canvasHeight),
        Pair(hipMidX * canvasWidth, hipMidY * canvasHeight)
    )
}

/**
 * Overlays 33 joint circles and 13 limb lines onto a Compose [Canvas].
 *
 * @param landmarks   Raw MediaPipe output: 33 points, each Triple is (x, y, z)
 *                    with x and y normalised to [0, 1].
 * @param jointColors Per-joint colour list of size 33. Defaults to all-green.
 *                    Supplied by the scoring module once alignment is stable.
 * @param limbColors  Per-limb colour list of size 13.  Defaults to all-green.
 * @param modifier    Applied to the underlying [Canvas]. Use [Modifier.fillMaxSize]
 *                    to fill the camera preview area.
 */
@Composable
fun SkeletonOverlay(
    landmarks: List<Triple<Float, Float, Float>>,
    jointColors: List<Color> = List(LANDMARK_COUNT) { Color.Green },
    limbColors: List<Color> = List(LIMB_COUNT) { Color.Green },
    modifier: Modifier = Modifier
) {
    require(landmarks.size == LANDMARK_COUNT) {
        "landmarks must contain exactly $LANDMARK_COUNT elements (got ${landmarks.size})"
    }
    require(jointColors.size == LANDMARK_COUNT) {
        "jointColors must contain exactly $LANDMARK_COUNT elements (got ${jointColors.size})"
    }
    require(limbColors.size == LIMB_COUNT) {
        "limbColors must contain exactly $LIMB_COUNT elements (got ${limbColors.size})"
    }

    val density = LocalDensity.current
    val jointRadiusPx = with(density) { SKELETON_JOINT_RADIUS_DP.dp.toPx() }
    val strokeWidthPx = with(density) { SKELETON_LIMB_STROKE_DP.dp.toPx() }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Draw limbs first so joints render on top.
        LIMB_CONNECTIONS.forEachIndexed { index, (start, end) ->
            val color = limbColors[index]
            val (startPx, endPx) = if (start == -1) {
                computeSpineEndpoints(landmarks, w, h)
            } else {
                Pair(
                    projectLandmark(landmarks[start], w, h),
                    projectLandmark(landmarks[end], w, h)
                )
            }
            drawLine(
                color = color,
                start = Offset(startPx.first, startPx.second),
                end = Offset(endPx.first, endPx.second),
                strokeWidth = strokeWidthPx,
                cap = StrokeCap.Round
            )
        }

        // Draw joint circles.
        landmarks.forEachIndexed { index, landmark ->
            val (px, py) = projectLandmark(landmark, w, h)
            drawCircle(
                color = jointColors[index],
                radius = jointRadiusPx,
                center = Offset(px, py)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SkeletonOverlayPreview() {
    // Dummy T-pose landmarks: all joints at centre except key ones
    val landmarks = List(LANDMARK_COUNT) { Triple(0.5f, 0.5f, 0f) }
        .toMutableList()
        .also { lm ->
            // Shoulders
            lm[11] = Triple(0.35f, 0.3f, 0f)
            lm[12] = Triple(0.65f, 0.3f, 0f)
            // Elbows
            lm[13] = Triple(0.2f, 0.3f, 0f)
            lm[14] = Triple(0.8f, 0.3f, 0f)
            // Wrists
            lm[15] = Triple(0.1f, 0.3f, 0f)
            lm[16] = Triple(0.9f, 0.3f, 0f)
            // Hips
            lm[23] = Triple(0.4f, 0.6f, 0f)
            lm[24] = Triple(0.6f, 0.6f, 0f)
            // Knees
            lm[25] = Triple(0.4f, 0.75f, 0f)
            lm[26] = Triple(0.6f, 0.75f, 0f)
            // Ankles
            lm[27] = Triple(0.4f, 0.9f, 0f)
            lm[28] = Triple(0.6f, 0.9f, 0f)
        }
    SkeletonOverlay(
        landmarks = landmarks,
        modifier = Modifier.fillMaxSize()
    )
}

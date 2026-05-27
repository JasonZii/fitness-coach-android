package com.example.fitnesscoach.training.pose

import com.example.fitnesscoach.core.util.Constants.LANDMARK_LEFT_ANKLE
import com.example.fitnesscoach.core.util.Constants.LANDMARK_LEFT_ELBOW
import com.example.fitnesscoach.core.util.Constants.LANDMARK_LEFT_HIP
import com.example.fitnesscoach.core.util.Constants.LANDMARK_LEFT_KNEE
import com.example.fitnesscoach.core.util.Constants.LANDMARK_LEFT_SHOULDER
import com.example.fitnesscoach.core.util.Constants.LANDMARK_LEFT_WRIST
import com.example.fitnesscoach.core.util.Constants.LANDMARK_RIGHT_ANKLE
import com.example.fitnesscoach.core.util.Constants.LANDMARK_RIGHT_ELBOW
import com.example.fitnesscoach.core.util.Constants.LANDMARK_RIGHT_HIP
import com.example.fitnesscoach.core.util.Constants.LANDMARK_RIGHT_KNEE
import com.example.fitnesscoach.core.util.Constants.LANDMARK_RIGHT_SHOULDER
import com.example.fitnesscoach.core.util.Constants.LANDMARK_RIGHT_WRIST
import kotlin.math.sqrt

// Universal canonical body proportions (relative to torso length T = 1.0).
// Both user frames and reference frames are normalised to these constants,
// so DTW and scoring measure only joint angle/direction differences.
private const val CANONICAL_UPPER_ARM     = 0.85f
private const val CANONICAL_FOREARM       = 0.73f
private const val CANONICAL_THIGH         = 1.05f
private const val CANONICAL_LOWER_LEG     = 0.90f
private const val CANONICAL_SHOULDER_HALF = 0.33f  // front view: shoulder midpoint → single shoulder
private const val CANONICAL_HIP_HALF      = 0.20f  // front view: hip midpoint → single hip
// Guards against scale explosion on degenerate or extreme-proportion frames.
private const val MAX_SEGMENT_SCALE       = 4.0f

/**
 * Normalises a single frame of raw MediaPipe BlazePose landmarks.
 *
 * Three-step processing (see ALGORITHM.md Module 1):
 *   1. Translation  — shift all points so the hip midpoint becomes (0, 0, 0).
 *   2. Scaling      — divide all translated points by the 2D torso length T,
 *                     so T equals 1.0 after normalisation.
 *   3. Proportion   — rescale each body segment to a universal canonical ratio,
 *                     eliminating individual body-proportion differences so that
 *                     DTW and scoring measure only joint angle/direction.
 *                     Which segments are rescaled depends on [cameraAngle] and
 *                     [requiresFullBody]:
 *                       • Always: upper arm + forearm (both sides)
 *                       • FRONT view only: shoulder half-width, hip half-width
 *                         (side-view depth axis makes these unreliable in 2D)
 *                       • requiresFullBody only: thigh + lower leg (both sides)
 *
 * @param landmarks       33 raw landmark triples (x, y, z) as output by MediaPipe.
 * @param cameraAngle     Required camera angle for the current exercise.
 *                        Default [CameraAngle.AMBIGUOUS] skips width normalisation.
 * @param requiresFullBody Whether the exercise requires full-body visibility.
 *                        Default false skips leg normalisation.
 * @return 33 normalised (x, y, z) triples, or null if the frame is degenerate
 *         (torso length < 1e-6, e.g. shoulders and hips coincide). Callers must
 *         skip degenerate frames rather than feeding them to OE-DTW.
 */
fun normalizeLandmarks(
    landmarks: List<Triple<Float, Float, Float>>,
    cameraAngle: CameraAngle = CameraAngle.AMBIGUOUS,
    requiresFullBody: Boolean = false,
): List<Triple<Float, Float, Float>>? {
    // ── Step 1: compute hip midpoint using raw coordinates ─────────────────
    val hipMidX = (landmarks[LANDMARK_LEFT_HIP].first + landmarks[LANDMARK_RIGHT_HIP].first) / 2f
    val hipMidY = (landmarks[LANDMARK_LEFT_HIP].second + landmarks[LANDMARK_RIGHT_HIP].second) / 2f
    val hipMidZ = (landmarks[LANDMARK_LEFT_HIP].third + landmarks[LANDMARK_RIGHT_HIP].third) / 2f

    // ── Step 2: compute torso length T using raw x/y coordinates ───────────
    val shoulderMidX =
        (landmarks[LANDMARK_LEFT_SHOULDER].first + landmarks[LANDMARK_RIGHT_SHOULDER].first) / 2f
    val shoulderMidY =
        (landmarks[LANDMARK_LEFT_SHOULDER].second + landmarks[LANDMARK_RIGHT_SHOULDER].second) / 2f

    val torsoLength = sqrt(
        (shoulderMidX - hipMidX) * (shoulderMidX - hipMidX) +
        (shoulderMidY - hipMidY) * (shoulderMidY - hipMidY)
    )

    if (torsoLength < 1e-6f) return null

    // ── Apply translation then scaling ─────────────────────────────────────
    val scaled = landmarks.map { (x, y, z) ->
        Triple(
            (x - hipMidX) / torsoLength,
            (y - hipMidY) / torsoLength,
            (z - hipMidZ) / torsoLength,
        )
    }.toMutableList()

    // ── Step 3: proportion normalisation ──────────────────────────────────
    // Keep an immutable torso-normalised frame as the source of all directions.
    // The output frame may move anchors and joints to canonical body proportions; deriving
    // later limb directions from that mutated output would corrupt joint angles.
    val base = scaled.toList()

    // Front view only: shoulder/hip width (depth axis makes these unreliable in side view)
    if (cameraAngle == CameraAngle.FRONT) {
        rescaleFromMidpoint(
            source = base,
            output = scaled,
            leftIdx = LANDMARK_LEFT_SHOULDER,
            rightIdx = LANDMARK_RIGHT_SHOULDER,
            targetHalfWidth = CANONICAL_SHOULDER_HALF,
        )
        rescaleFromMidpoint(
            source = base,
            output = scaled,
            leftIdx = LANDMARK_LEFT_HIP,
            rightIdx = LANDMARK_RIGHT_HIP,
            targetHalfWidth = CANONICAL_HIP_HALF,
        )
    }

    // Arms: always, proximal before distal
    rebuildSegment(base, scaled, LANDMARK_LEFT_SHOULDER,  LANDMARK_LEFT_ELBOW,  CANONICAL_UPPER_ARM)
    rebuildSegment(base, scaled, LANDMARK_LEFT_ELBOW,     LANDMARK_LEFT_WRIST,  CANONICAL_FOREARM)
    rebuildSegment(base, scaled, LANDMARK_RIGHT_SHOULDER, LANDMARK_RIGHT_ELBOW, CANONICAL_UPPER_ARM)
    rebuildSegment(base, scaled, LANDMARK_RIGHT_ELBOW,    LANDMARK_RIGHT_WRIST, CANONICAL_FOREARM)

    // Legs: full-body exercises only, proximal before distal
    if (requiresFullBody) {
        rebuildSegment(base, scaled, LANDMARK_LEFT_HIP,   LANDMARK_LEFT_KNEE,   CANONICAL_THIGH)
        rebuildSegment(base, scaled, LANDMARK_LEFT_KNEE,  LANDMARK_LEFT_ANKLE,  CANONICAL_LOWER_LEG)
        rebuildSegment(base, scaled, LANDMARK_RIGHT_HIP,  LANDMARK_RIGHT_KNEE,  CANONICAL_THIGH)
        rebuildSegment(base, scaled, LANDMARK_RIGHT_KNEE, LANDMARK_RIGHT_ANKLE, CANONICAL_LOWER_LEG)
    }

    return scaled
}

/**
 * Rebuilds [childIdx] from the already-normalised output parent, using the original
 * parent→child direction from [source] and a canonical 3D segment length.
 *
 * This removes body-proportion differences without chaining mutations into later limb
 * directions. For example, after the elbow is moved to canonical upper-arm length, the
 * wrist is still reconstructed from the original elbow→wrist direction, not from the
 * moved elbow to the old wrist.
 */
private fun rebuildSegment(
    source: List<Triple<Float, Float, Float>>,
    output: MutableList<Triple<Float, Float, Float>>,
    parentIdx: Int,
    childIdx: Int,
    targetLength: Float,
) {
    val sourceParent = source[parentIdx]
    val child  = source[childIdx]
    val dx = child.first  - sourceParent.first
    val dy = child.second - sourceParent.second
    val dz = child.third  - sourceParent.third
    val len3D = sqrt(dx * dx + dy * dy + dz * dz)
    if (len3D < 1e-6f) return
    val scale = (targetLength / len3D).coerceAtMost(MAX_SEGMENT_SCALE)
    val outputParent = output[parentIdx]
    output[childIdx] = Triple(
        outputParent.first  + dx * scale,
        outputParent.second + dy * scale,
        outputParent.third  + dz * scale,
    )
}

/**
 * Scales both joints of a bilateral pair (left/right shoulder or hip)
 * symmetrically from their midpoint so each ends up at [targetHalfWidth]
 * from the midpoint in the 2D image plane.
 * Shoulder/hip width is an image-plane concept (front view only), so the
 * scale factor is derived from the 2D half-width and only x/y are modified.
 * The z left-right asymmetry is a depth measurement artefact and is not amplified.
 */
private fun rescaleFromMidpoint(
    source: List<Triple<Float, Float, Float>>,
    output: MutableList<Triple<Float, Float, Float>>,
    leftIdx: Int,
    rightIdx: Int,
    targetHalfWidth: Float,
) {
    val left  = source[leftIdx]
    val right = source[rightIdx]
    val midX = (left.first  + right.first)  / 2f
    val midY = (left.second + right.second) / 2f
    val dxL = left.first  - midX
    val dyL = left.second - midY
    val halfW2D = sqrt(dxL * dxL + dyL * dyL)
    if (halfW2D < 1e-6f) return
    val scale = (targetHalfWidth / halfW2D).coerceAtMost(MAX_SEGMENT_SCALE)
    output[leftIdx]  = Triple(midX + dxL * scale, midY + dyL * scale, left.third)
    output[rightIdx] = Triple(midX - dxL * scale, midY - dyL * scale, right.third)
}

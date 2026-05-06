package com.example.fitnesscoach.training.pose

import com.example.fitnesscoach.core.util.Constants.LANDMARK_LEFT_HIP
import com.example.fitnesscoach.core.util.Constants.LANDMARK_LEFT_SHOULDER
import com.example.fitnesscoach.core.util.Constants.LANDMARK_RIGHT_HIP
import com.example.fitnesscoach.core.util.Constants.LANDMARK_RIGHT_SHOULDER
import kotlin.math.sqrt

/**
 * Normalises a single frame of raw MediaPipe BlazePose landmarks.
 *
 * Two-step processing (see ALGORITHM.md Module 1):
 *   1. Translation  — shift all points so the hip midpoint becomes (0, 0).
 *   2. Scaling      — divide all translated points by the torso length T,
 *                     so T equals 1.0 after normalisation.
 *
 * The z coordinate is preserved and normalised relative to the hip midpoint.
 *
 * @param landmarks 33 raw landmark triples (x, y, z) as output by MediaPipe.
 * @return 33 normalised (x, y, z) triples. Index matches MediaPipe landmark index.
 */
fun normalizeLandmarks(
    landmarks: List<Triple<Float, Float, Float>>
): List<Triple<Float, Float, Float>> {
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

    // ── Apply translation then scaling; preserve z ─────────────────────────
    return landmarks.map { (x, y, z) ->
        Triple(
            (x - hipMidX) / torsoLength,
            (y - hipMidY) / torsoLength,
            (z - hipMidZ) / torsoLength
        )
    }
}

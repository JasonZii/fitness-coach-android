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
 * The z coordinate is discarded; only (x, y) pairs are returned.
 *
 * @param landmarks 33 raw landmark triples (x, y, z) as output by MediaPipe.
 * @return 33 normalised (x, y) pairs. Index matches MediaPipe landmark index.
 */
fun normalizeLandmarks(
    landmarks: List<Triple<Float, Float, Float>>
): List<Pair<Float, Float>> {
    // ── Step 1: compute hip midpoint using raw coordinates ─────────────────
    val hipMidX = (landmarks[LANDMARK_LEFT_HIP].first + landmarks[LANDMARK_RIGHT_HIP].first) / 2f
    val hipMidY = (landmarks[LANDMARK_LEFT_HIP].second + landmarks[LANDMARK_RIGHT_HIP].second) / 2f

    // ── Step 2: compute torso length T using raw coordinates ───────────────
    val shoulderMidX =
        (landmarks[LANDMARK_LEFT_SHOULDER].first + landmarks[LANDMARK_RIGHT_SHOULDER].first) / 2f
    val shoulderMidY =
        (landmarks[LANDMARK_LEFT_SHOULDER].second + landmarks[LANDMARK_RIGHT_SHOULDER].second) / 2f

    val torsoLength = sqrt(
        (shoulderMidX - hipMidX) * (shoulderMidX - hipMidX) +
        (shoulderMidY - hipMidY) * (shoulderMidY - hipMidY)
    )

    // ── Apply translation then scaling; drop z ─────────────────────────────
    return landmarks.map { (x, y, _) ->
        val tx = x - hipMidX
        val ty = y - hipMidY
        Pair(tx / torsoLength, ty / torsoLength)
    }
}

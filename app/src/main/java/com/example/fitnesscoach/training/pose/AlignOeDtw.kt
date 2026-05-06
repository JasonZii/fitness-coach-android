package com.example.fitnesscoach.training.pose

import com.example.fitnesscoach.core.util.Constants.OE_DTW_MIN_FRAMES
import com.example.fitnesscoach.core.util.Constants.SCORE_Z_WEIGHT
import kotlin.math.sqrt

/**
 * OE-DTW (Open-End Dynamic Time Warping) real-time alignment.
 *
 * For each incoming user frame, finds which frame in [referenceSequence] best
 * matches the user's current position in the exercise. The reference end is
 * left free (open-end), so the alignment path can terminate at any reference
 * frame rather than being forced to the last one.
 *
 * Algorithm overview (see ALGORITHM.md Module 2):
 *   - Standard DTW cost matrix, initialised to force alignment to start at
 *     reference frame 0 (user begins the exercise from the start).
 *   - DP is computed one row at a time (incremental), keeping only the
 *     previous row in memory — O(m) space, O(n·m) time total.
 *   - The matched reference index is argmin over the final row.
 *
 * @param userSequence      Normalised frames from training start to now.
 *                          Each frame is a list of 33 (x, y, z) triples.
 * @param referenceSequence Complete normalised standard-action sequence.
 *                          Each frame is a list of 33 (x, y, z) triples.
 * @return Index in [referenceSequence] that best matches the last user frame,
 *         or -1 if [userSequence] has fewer than [OE_DTW_MIN_FRAMES] frames.
 */
fun alignOeDtw(
    userSequence: List<List<Triple<Float, Float, Float>>>,
    referenceSequence: List<List<Triple<Float, Float, Float>>>
): Int {
    if (userSequence.size < OE_DTW_MIN_FRAMES) return -1

    val m = referenceSequence.size

    // ── Row 0: standard DTW initialisation (cumulative from reference start) ──
    var dp = FloatArray(m)
    dp[0] = frameDist(userSequence[0], referenceSequence[0])
    for (j in 1 until m) {
        dp[j] = dp[j - 1] + frameDist(userSequence[0], referenceSequence[j])
    }

    // ── Rows 1..n-1: incremental row-by-row DP update ────────────────────────
    for (i in 1 until userSequence.size) {
        val newDp = FloatArray(m)
        for (j in 0 until m) {
            val d = frameDist(userSequence[i], referenceSequence[j])
            // Three allowed predecessor steps: diagonal, up, left
            val best = if (j == 0) {
                dp[0]                              // can only come from above
            } else {
                minOf(dp[j - 1], dp[j], newDp[j - 1])
            }
            newDp[j] = d + best
        }
        dp = newDp
    }

    // ── OE result: reference frame with minimum accumulated cost ──────────────
    return dp.indices.minByOrNull { dp[it] } ?: 0
}

/**
 * Mean per-landmark Euclidean distance between two normalised frames.
 * Both lists must have the same size (33 landmarks).
 */
internal fun frameDist(
    a: List<Triple<Float, Float, Float>>,
    b: List<Triple<Float, Float, Float>>
): Float {
    var sum = 0f
    for (k in a.indices) {
        val dx = a[k].first - b[k].first
        val dy = a[k].second - b[k].second
        val dz = (a[k].third - b[k].third) * SCORE_Z_WEIGHT
        sum += sqrt(dx * dx + dy * dy + dz * dz)
    }
    return sum / a.size
}

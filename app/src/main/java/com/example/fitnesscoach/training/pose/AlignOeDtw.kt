package com.example.fitnesscoach.training.pose

import com.example.fitnesscoach.core.util.Constants.OE_DTW_MIN_FRAMES
import com.example.fitnesscoach.core.util.Constants.SCORE_Z_WEIGHT
import kotlin.math.sqrt

/** Carries the new DP row forward so the next call can continue incrementally. */
class OeDtwAdvance(val matchedReferenceIndex: Int, val nextRow: FloatArray)

/**
 * Incremental OE-DTW: compute one new DP row for [newFrame].
 *
 * O(m) per call. Mathematically identical to running the full matrix from frame 0.
 * Only the last DP row is stored between calls — O(m) space, O(m) time per frame.
 *
 * @param newFrame          Latest normalised frame (33 landmarks).
 * @param referenceSequence Complete normalised standard-action sequence.
 * @param prevRow           DP row from the previous call, or null on the first frame
 *                          after a rep reset (triggers row-0 initialisation).
 * @param frameCount        Frames seen since last reset (pass 1 on the first call).
 *                          Controls the [OE_DTW_MIN_FRAMES] warm-up guard.
 * @return [OeDtwAdvance] with the matched index (-1 during warm-up) and the next row.
 */
fun advanceOeDtw(
    newFrame: List<Triple<Float, Float, Float>>,
    referenceSequence: List<List<Triple<Float, Float, Float>>>,
    prevRow: FloatArray?,
    frameCount: Int,
): OeDtwAdvance {
    val m = referenceSequence.size
    if (m == 0) return OeDtwAdvance(-1, FloatArray(0))

    val row = FloatArray(m)

    if (prevRow == null) {
        // Row 0: cumulative cost forces alignment to start at reference frame 0.
        row[0] = frameDist(newFrame, referenceSequence[0])
        for (j in 1 until m) {
            row[j] = row[j - 1] + frameDist(newFrame, referenceSequence[j])
        }
    } else {
        // Row i: standard DTW recurrence — diagonal / up / left predecessors.
        for (j in 0 until m) {
            val d = frameDist(newFrame, referenceSequence[j])
            val best = if (j == 0) prevRow[0]
                       else minOf(prevRow[j - 1], prevRow[j], row[j - 1])
            row[j] = d + best
        }
    }

    val matchedIdx = if (frameCount < OE_DTW_MIN_FRAMES) -1
                     else row.indices.minByOrNull { row[it] } ?: 0
    return OeDtwAdvance(matchedIdx, row)
}

/**
 * Batch OE-DTW over a complete [userSequence]. Delegates to [advanceOeDtw] frame
 * by frame so the two implementations are guaranteed to be identical.
 *
 * @return Index in [referenceSequence] that best matches the last user frame,
 *         or -1 if [userSequence] has fewer than [OE_DTW_MIN_FRAMES] frames.
 */
fun alignOeDtw(
    userSequence: List<List<Triple<Float, Float, Float>>>,
    referenceSequence: List<List<Triple<Float, Float, Float>>>
): Int {
    var row: FloatArray? = null
    var lastMatchedIdx = -1
    for ((i, frame) in userSequence.withIndex()) {
        val result = advanceOeDtw(frame, referenceSequence, row, i + 1)
        row = result.nextRow
        lastMatchedIdx = result.matchedReferenceIndex
    }
    return lastMatchedIdx
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

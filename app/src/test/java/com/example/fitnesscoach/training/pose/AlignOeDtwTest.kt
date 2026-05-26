package com.example.fitnesscoach.training.pose

import com.example.fitnesscoach.core.util.Constants.OE_DTW_MIN_FRAMES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

// ── advanceOeDtw helpers ──────────────────────────────────────────────────────

/** Feed [userSeq] into [advanceOeDtw] one frame at a time; return final matchedReferenceIndex. */
private fun runIncremental(
    userSeq: List<List<Triple<Float, Float, Float>>>,
    refSeq: List<List<Triple<Float, Float, Float>>>,
): Int {
    var row: FloatArray? = null
    var matched = -1
    for ((i, frame) in userSeq.withIndex()) {
        val r = advanceOeDtw(frame, refSeq, row, i + 1)
        row = r.nextRow
        matched = r.matchedReferenceIndex
    }
    return matched
}

/**
 * Unit tests for [alignOeDtw].
 *
 * Acceptance criteria (ALGORITHM.md Module 2):
 *   AC-1  Two identical sequences → matchedReferenceIndex = userSequence.size - 1
 *   AC-2  userSequence.size < 20  → returns -1
 */
class AlignOeDtwTest {

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Build a single normalised frame with [landmarks] landmarks, all at [x],[y]. */
    private fun uniformFrame(x: Float, y: Float, landmarks: Int = 33): List<Triple<Float, Float, Float>> =
        List(landmarks) { Triple(x, y, 0f) }

    /**
     * Build a reference sequence of [length] frames.
     * Each frame has 33 landmarks. The joint positions vary per frame so that
     * consecutive frames are distinguishable and the DTW alignment is non-trivial.
     */
    private fun buildSequence(length: Int, seed: Long = 42L): List<List<Triple<Float, Float, Float>>> {
        val rng = Random(seed)
        return List(length) {
            List(33) { Triple(rng.nextFloat(), rng.nextFloat(), 0f) }
        }
    }

    // ── AC-2: return -1 when fewer than OE_DTW_MIN_FRAMES frames ─────────────

    @Test
    fun returnsMinusOneWhenUserSequenceIsEmpty() {
        val ref = buildSequence(30)
        assertEquals(-1, alignOeDtw(emptyList(), ref))
    }

    @Test
    fun returnsMinusOneWhenUserSequenceHasOnlyOneFrame() {
        val ref = buildSequence(30)
        val user = buildSequence(1, seed = 1L)
        assertEquals(-1, alignOeDtw(user, ref))
    }

    @Test
    fun returnsMinusOneWhenUserSequenceHasFewerThanMinFrames() {
        val ref = buildSequence(50)
        // Test every size from 1 to OE_DTW_MIN_FRAMES - 1
        for (size in 1 until OE_DTW_MIN_FRAMES) {
            val user = buildSequence(size, seed = size.toLong())
            assertEquals(
                "Expected -1 for userSequence.size=$size (< $OE_DTW_MIN_FRAMES)",
                -1,
                alignOeDtw(user, ref)
            )
        }
    }

    @Test
    fun returnsNonNegativeWhenUserSequenceHasExactlyMinFrames() {
        val seq = buildSequence(OE_DTW_MIN_FRAMES)
        val result = alignOeDtw(seq, seq)
        assertTrue(
            "Expected non-negative result for userSequence.size=$OE_DTW_MIN_FRAMES, got $result",
            result >= 0
        )
    }

    // ── AC-1: identical sequences → matchedReferenceIndex = userSequence.size - 1 ──

    @Test
    fun identicalSequencesMatchAtLastIndex() {
        val seq = buildSequence(30)
        val result = alignOeDtw(seq, seq)
        assertEquals(
            "Identical sequences (length 30): expected matchedReferenceIndex=${seq.size - 1}",
            seq.size - 1,
            result
        )
    }

    @Test
    fun identicalSequencesMatchAtLastIndexMinLength() {
        val seq = buildSequence(OE_DTW_MIN_FRAMES)
        val result = alignOeDtw(seq, seq)
        assertEquals(
            "Identical sequences (length $OE_DTW_MIN_FRAMES): expected matchedReferenceIndex=${seq.size - 1}",
            seq.size - 1,
            result
        )
    }

    @Test
    fun identicalSequencesMatchAtLastIndexLonger() {
        val seq = buildSequence(60)
        val result = alignOeDtw(seq, seq)
        assertEquals(
            "Identical sequences (length 60): expected matchedReferenceIndex=${seq.size - 1}",
            seq.size - 1,
            result
        )
    }

    /**
     * User sequence = first k frames of reference sequence (k >= OE_DTW_MIN_FRAMES).
     * Expected: matchedReferenceIndex = k - 1 (user has progressed to frame k-1).
     */
    @Test
    fun userPrefixMatchesCorrectReferenceFrame() {
        val ref = buildSequence(80)
        for (k in listOf(OE_DTW_MIN_FRAMES, 25, 40, 60)) {
            val user = ref.subList(0, k)
            val result = alignOeDtw(user, ref)
            assertEquals(
                "User = first $k frames of ref: expected matchedReferenceIndex=${k - 1}, got $result",
                k - 1,
                result
            )
        }
    }

    // ── frameDist helper ─────────────────────────────────────────────────────

    @Test
    fun frameDistIsZeroForIdenticalFrames() {
        val frame = List(33) { Triple(0.5f, 0.5f, 0f) }
        val d = frameDist(frame, frame)
        assertTrue("frameDist of identical frames must be 0, got $d", abs(d) < 1e-6f)
    }

    @Test
    fun frameDistIsPositiveForDifferentFrames() {
        val a = List(33) { Triple(0.0f, 0.0f, 0f) }
        val b = List(33) { Triple(1.0f, 1.0f, 0f) }
        val d = frameDist(a, b)
        assertTrue("frameDist of different frames must be > 0, got $d", d > 0f)
    }

    @Test
    fun frameDistIsSymmetric() {
        val a = List(33) { i -> Triple(i * 0.01f, i * 0.02f, 0f) }
        val b = List(33) { i -> Triple(i * 0.03f, i * 0.01f, 0f) }
        val dAB = frameDist(a, b)
        val dBA = frameDist(b, a)
        assertEquals("frameDist must be symmetric", dAB, dBA, 1e-6f)
    }

    // ── Monotonicity: matched index should advance as user progresses ─────────

    @Test
    fun matchedIndexAdvancesAsUserSequenceGrows() {
        val ref = buildSequence(80)
        var prevIndex = -1
        // Sample a few lengths to verify generally increasing trend
        for (size in listOf(OE_DTW_MIN_FRAMES, 30, 50, 70)) {
            val user = ref.subList(0, size)
            val idx = alignOeDtw(user, ref)
            assertTrue(
                "matchedReferenceIndex ($idx) should be >= previous ($prevIndex) as user grows",
                idx >= prevIndex
            )
            prevIndex = idx
        }
    }

    // ── Result must always be a valid reference index ─────────────────────────

    @Test
    fun resultIsAlwaysValidReferenceIndex() {
        val ref = buildSequence(50)
        val user = buildSequence(25, seed = 99L)
        val result = alignOeDtw(user, ref)
        assertTrue("matchedReferenceIndex must be >= 0, got $result", result >= 0)
        assertTrue(
            "matchedReferenceIndex must be < referenceSequence.size, got $result",
            result < ref.size
        )
    }

    // ══════════════════════════════════════════════════════════════════════════
    // advanceOeDtw() — incremental API tests
    // ══════════════════════════════════════════════════════════════════════════

    // ── AC-2 incremental: warm-up guard ──────────────────────────────────────

    @Test
    fun incremental_returnsMinusOneDuringWarmup() {
        val ref = buildSequence(30)
        for (size in 1 until OE_DTW_MIN_FRAMES) {
            val result = runIncremental(buildSequence(size, seed = size.toLong()), ref)
            assertEquals(
                "Expected -1 for frameCount=$size (< $OE_DTW_MIN_FRAMES), got $result",
                -1, result
            )
        }
    }

    @Test
    fun incremental_returnsNonNegativeAtMinFrames() {
        val seq = buildSequence(OE_DTW_MIN_FRAMES)
        val result = runIncremental(seq, seq)
        assertTrue("Expected >= 0 at frameCount=$OE_DTW_MIN_FRAMES, got $result", result >= 0)
    }

    // ── AC-1 incremental: identical sequences ─────────────────────────────────

    @Test
    fun incremental_identicalSequencesMatchAtLastIndex() {
        val seq = buildSequence(30)
        val result = runIncremental(seq, seq)
        assertEquals(
            "Identical sequences (length 30): expected matchedReferenceIndex=${seq.size - 1}",
            seq.size - 1, result
        )
    }

    @Test
    fun incremental_identicalSequencesMatchAtLastIndexMinLength() {
        val seq = buildSequence(OE_DTW_MIN_FRAMES)
        val result = runIncremental(seq, seq)
        assertEquals(
            "Identical sequences (length $OE_DTW_MIN_FRAMES): expected matchedReferenceIndex=${seq.size - 1}",
            seq.size - 1, result
        )
    }

    // ── Equivalence: incremental == batch for arbitrary inputs ───────────────

    @Test
    fun incremental_matchesBatchForArbitraryInputs() {
        val ref = buildSequence(60)
        for ((size, seed) in listOf(OE_DTW_MIN_FRAMES to 7L, 25 to 13L, 40 to 99L, 55 to 42L)) {
            val user = buildSequence(size, seed)
            val batch = alignOeDtw(user, ref)
            val incr  = runIncremental(user, ref)
            assertEquals(
                "Batch and incremental must agree for size=$size seed=$seed",
                batch, incr
            )
        }
    }

    // ── Reset semantics: null prevRow re-initialises correctly ────────────────

    @Test
    fun incremental_resetStartsFresh() {
        val ref = buildSequence(40)
        val seq = buildSequence(OE_DTW_MIN_FRAMES + 5)

        // First pass
        val firstResult = runIncremental(seq, ref)

        // Simulate rep reset: feed the SAME frames again from null
        val secondResult = runIncremental(seq, ref)

        assertEquals(
            "Two identical runs after reset must produce identical results",
            firstResult, secondResult
        )
    }

    // ── Valid index bounds for incremental ────────────────────────────────────

    @Test
    fun incremental_resultIsAlwaysValidReferenceIndex() {
        val ref = buildSequence(50)
        val user = buildSequence(25, seed = 77L)
        val result = runIncremental(user, ref)
        assertTrue("matchedReferenceIndex must be >= 0, got $result", result >= 0)
        assertTrue("matchedReferenceIndex must be < ref.size, got $result", result < ref.size)
    }
}

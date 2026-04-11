package com.example.fitnesscoach.training.pose

import com.example.fitnesscoach.core.util.Constants.END_SHOULDER_WIDTH_RATIO
import com.example.fitnesscoach.core.util.Constants.END_VISIBILITY_HOLD_SECONDS
import com.example.fitnesscoach.core.util.Constants.END_VISIBILITY_THRESHOLD
import com.example.fitnesscoach.core.util.Constants.LANDMARK_COUNT
import com.example.fitnesscoach.core.util.Constants.LANDMARK_LEFT_SHOULDER
import com.example.fitnesscoach.core.util.Constants.LANDMARK_RIGHT_SHOULDER
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [TrainingEndDetector] (ALGORITHM.md Module 6).
 *
 * All tests use synthetic landmarks and visibilities — no Android dependencies.
 */
class TrainingEndDetectorTest {

    private lateinit var detector: TrainingEndDetector

    // ── Test fixture helpers ──────────────────────────────────────────────────

    /**
     * Builds a list of 33 zero-filled (x, y, z) triples, then sets
     * left shoulder at (0, 0, 0) and right shoulder at (width, 0, 0).
     */
    private fun landmarks(shoulderWidthPx: Float = 0.1f): List<Triple<Float, Float, Float>> {
        val pts = MutableList(LANDMARK_COUNT) { Triple(0f, 0f, 0f) }
        pts[LANDMARK_LEFT_SHOULDER]  = Triple(0f,              0f, 0f)
        pts[LANDMARK_RIGHT_SHOULDER] = Triple(shoulderWidthPx, 0f, 0f)
        return pts
    }

    /**
     * Builds a visibility list of [LANDMARK_COUNT] entries, all set to [defaultVis].
     * Override individual indices as needed.
     */
    private fun visibilities(
        defaultVis: Float = 0.9f,
        overrides: Map<Int, Float> = emptyMap()
    ): List<Float> {
        return MutableList(LANDMARK_COUNT) { defaultVis }.also { list ->
            overrides.forEach { (idx, v) -> list[idx] = v }
        }
    }

    @Before fun setUp() {
        detector = TrainingEndDetector()
    }

    // ── onTrainingStart / basic initial state ─────────────────────────────────

    @Test
    fun `before onTrainingStart update returns ACTIVE`() {
        val result = detector.update(landmarks(), visibilities(), nowMs = 0L)
        assertEquals(TrainingPauseState.ACTIVE, result)
    }

    @Test
    fun `after onTrainingStart with normal conditions returns ACTIVE`() {
        detector.onTrainingStart(landmarks(shoulderWidthPx = 0.1f))
        val result = detector.update(landmarks(0.1f), visibilities(), nowMs = 1_000L)
        assertEquals(TrainingPauseState.ACTIVE, result)
    }

    // ── Condition 2: shoulder width (too close) ───────────────────────────────

    @Test
    fun `shoulder width exactly at threshold returns ACTIVE`() {
        val baseline = 0.1f
        detector.onTrainingStart(landmarks(baseline))
        // width == baseline * ratio → not strictly greater → ACTIVE
        val currentWidth = baseline * END_SHOULDER_WIDTH_RATIO
        val result = detector.update(landmarks(currentWidth), visibilities(), nowMs = 1_000L)
        assertEquals(TrainingPauseState.ACTIVE, result)
    }

    @Test
    fun `shoulder width just above threshold fires immediately (PAUSED)`() {
        val baseline = 0.1f
        detector.onTrainingStart(landmarks(baseline))
        // Slightly above ratio threshold → immediate PAUSED, no hold period
        val tooWide = baseline * END_SHOULDER_WIDTH_RATIO + 0.001f
        val result = detector.update(landmarks(tooWide), visibilities(), nowMs = 1_000L)
        assertEquals(TrainingPauseState.PAUSED, result)
    }

    @Test
    fun `shoulder width pause recovers when width drops back below threshold`() {
        val baseline = 0.1f
        detector.onTrainingStart(landmarks(baseline))

        val tooWide = baseline * END_SHOULDER_WIDTH_RATIO + 0.01f
        // First frame: paused
        detector.update(landmarks(tooWide), visibilities(), nowMs = 1_000L)

        // Second frame: width back to normal → ACTIVE
        val result = detector.update(landmarks(baseline), visibilities(), nowMs = 2_000L)
        assertEquals(TrainingPauseState.ACTIVE, result)
    }

    @Test
    fun `double baseline shoulder width triggers pause`() {
        val baseline = 0.2f
        detector.onTrainingStart(landmarks(baseline))
        // 2× baseline is > 1.5× baseline
        val result = detector.update(landmarks(baseline * 2f), visibilities(), nowMs = 500L)
        assertEquals(TrainingPauseState.PAUSED, result)
    }

    // ── Condition 1: low visibility (hold period) ─────────────────────────────

    /**
     * Indices checked by the detector: 0 (nose), 11 (left shoulder), 12 (right shoulder),
     * 23 (left hip), 24 (right hip). Set all five to a low value via overrides.
     */
    private fun lowVisOverrides(v: Float = 0f) = mapOf(0 to v, 11 to v, 12 to v, 23 to v, 24 to v)

    @Test
    fun `low visibility does not pause before hold period elapses`() {
        detector.onTrainingStart(landmarks())
        val vis = visibilities(overrides = lowVisOverrides(END_VISIBILITY_THRESHOLD - 0.01f))

        // Still within hold window
        val holdMs = END_VISIBILITY_HOLD_SECONDS * 1_000L - 1L
        val result = detector.update(landmarks(), vis, nowMs = holdMs)
        assertEquals(TrainingPauseState.ACTIVE, result)
    }

    @Test
    fun `low visibility pauses exactly at hold period boundary`() {
        detector.onTrainingStart(landmarks())
        val vis = visibilities(overrides = lowVisOverrides(0f))

        // t=0: starts the timer
        detector.update(landmarks(), vis, nowMs = 0L)
        // t = holdMs: elapsed == holdMs → PAUSED
        val holdMs = END_VISIBILITY_HOLD_SECONDS * 1_000L
        val result = detector.update(landmarks(), vis, nowMs = holdMs)
        assertEquals(TrainingPauseState.PAUSED, result)
    }

    @Test
    fun `visibility above threshold keeps state ACTIVE`() {
        detector.onTrainingStart(landmarks())
        val vis = visibilities(defaultVis = END_VISIBILITY_THRESHOLD + 0.1f)
        val result = detector.update(landmarks(), vis, nowMs = 10_000L)
        assertEquals(TrainingPauseState.ACTIVE, result)
    }

    @Test
    fun `visibility recovery resets hold timer (ACTIVE again before re-pausing)`() {
        detector.onTrainingStart(landmarks())
        val lowVis  = visibilities(overrides = lowVisOverrides(0f))
        val goodVis = visibilities(defaultVis = 0.9f)

        // Drop visibility for 2 seconds (not yet at hold limit of 3 s)
        detector.update(landmarks(), lowVis, nowMs = 0L)
        detector.update(landmarks(), lowVis, nowMs = 2_000L)

        // Visibility recovers → timer resets
        detector.update(landmarks(), goodVis, nowMs = 2_500L)

        // Visibility drops again — timer starts fresh at 2_500 ms
        detector.update(landmarks(), lowVis, nowMs = 2_500L)
        // Only 1 s since reset (2_500 → 3_500), still under 3 s hold
        val resultBefore = detector.update(landmarks(), lowVis, nowMs = 3_499L)
        assertEquals(TrainingPauseState.ACTIVE, resultBefore)

        // Now at 3 s after reset (2_500 + 3_000 = 5_500)
        val resultAfter = detector.update(landmarks(), lowVis, nowMs = 5_500L)
        assertEquals(TrainingPauseState.PAUSED, resultAfter)
    }

    @Test
    fun `visibility pause recovers once visibility returns above threshold`() {
        detector.onTrainingStart(landmarks())
        val lowVis  = visibilities(overrides = lowVisOverrides(0f))
        val goodVis = visibilities(defaultVis = 0.9f)

        // Trigger a pause
        detector.update(landmarks(), lowVis, nowMs = 0L)
        detector.update(landmarks(), lowVis, nowMs = 3_000L)
        assertEquals(
            TrainingPauseState.PAUSED,
            detector.update(landmarks(), lowVis, nowMs = 4_000L)
        )

        // Visibility recovers → ACTIVE
        val result = detector.update(landmarks(), goodVis, nowMs = 5_000L)
        assertEquals(TrainingPauseState.ACTIVE, result)
    }

    // ── reset ─────────────────────────────────────────────────────────────────

    @Test
    fun `reset clears baseline so shoulder-width check is disabled`() {
        val baseline = 0.1f
        detector.onTrainingStart(landmarks(baseline))
        detector.reset()

        // Without a baseline, too-close check should not fire
        val tooWide = landmarks(baseline * 10f)
        val result = detector.update(tooWide, visibilities(), nowMs = 0L)
        assertEquals(TrainingPauseState.ACTIVE, result)
    }

    @Test
    fun `reset clears visibility timer so 3 s hold starts fresh`() {
        detector.onTrainingStart(landmarks())
        val lowVis = visibilities(overrides = lowVisOverrides(0f))

        // Run for 2.5 s below threshold
        detector.update(landmarks(), lowVis, nowMs = 0L)
        detector.update(landmarks(), lowVis, nowMs = 2_500L)

        detector.reset()

        // Re-initialise and drop visibility; clock starts over
        detector.onTrainingStart(landmarks())
        detector.update(landmarks(), lowVis, nowMs = 0L)
        // Only 1 s elapsed since reset — not yet at 3 s hold
        val result = detector.update(landmarks(), lowVis, nowMs = 1_000L)
        assertEquals(TrainingPauseState.ACTIVE, result)
    }

    // ── Both conditions fire simultaneously ───────────────────────────────────

    @Test
    fun `both conditions active returns PAUSED`() {
        val baseline = 0.1f
        detector.onTrainingStart(landmarks(baseline))

        val tooWide = baseline * END_SHOULDER_WIDTH_RATIO + 0.01f
        val lowVis  = visibilities(overrides = lowVisOverrides(0f))

        // Immediately paused by shoulder width; low visibility also active
        val result = detector.update(landmarks(tooWide), lowVis, nowMs = 0L)
        assertEquals(TrainingPauseState.PAUSED, result)
    }
}

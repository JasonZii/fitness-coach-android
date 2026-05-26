package com.example.fitnesscoach.training.pose

import com.example.fitnesscoach.core.util.Constants.END_CLOSE_HOLD_MS
import com.example.fitnesscoach.core.util.Constants.LANDMARK_COUNT
import com.example.fitnesscoach.core.util.Constants.LANDMARK_LEFT_SHOULDER
import com.example.fitnesscoach.core.util.Constants.LANDMARK_RIGHT_SHOULDER
import com.example.fitnesscoach.core.util.Constants.VISIBILITY_IN_FRAME_MIN
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class TrainingEndDetectorTest {

    private lateinit var detector: TrainingEndDetector

    // Baseline: left (0.3, 0.3), right (0.7, 0.3) → width = 0.4
    private val baselineLandmarks = buildLandmarks(
        LANDMARK_LEFT_SHOULDER  to Triple(0.3f, 0.3f, 0f),
        LANDMARK_RIGHT_SHOULDER to Triple(0.7f, 0.3f, 0f),
    )

    // Too-close: left (0.1, 0.3), right (0.9, 0.3) → width = 0.8 = 2× baseline (> 1.5×)
    private val tooCloseLandmarks = buildLandmarks(
        LANDMARK_LEFT_SHOULDER  to Triple(0.1f, 0.3f, 0f),
        LANDMARK_RIGHT_SHOULDER to Triple(0.9f, 0.3f, 0f),
    )

    private val fullVisibility = buildVisibilities()

    @Before
    fun setUp() {
        detector = TrainingEndDetector()
        detector.onTrainingStart(baselineLandmarks, CameraAngle.FRONT)
    }

    @Test
    fun conditionNotMet_returnsActive() {
        val result = detector.update(baselineLandmarks, fullVisibility, 0L)
        assertEquals(TrainingEndState.ACTIVE, result)
    }

    @Test
    fun conditionMet_holdNotElapsed_returnsActive() {
        detector.update(tooCloseLandmarks, fullVisibility, 0L)
        val result = detector.update(tooCloseLandmarks, fullVisibility, END_CLOSE_HOLD_MS - 1L)
        assertEquals(TrainingEndState.ACTIVE, result)
    }

    @Test
    fun conditionMet_holdElapsed_returnsDetected() {
        detector.update(tooCloseLandmarks, fullVisibility, 0L)
        val result = detector.update(tooCloseLandmarks, fullVisibility, END_CLOSE_HOLD_MS)
        assertEquals(TrainingEndState.DETECTED, result)
    }

    @Test
    fun conditionBreaks_timerResets_returnsActive() {
        detector.update(tooCloseLandmarks, fullVisibility, 0L)
        detector.update(baselineLandmarks, fullVisibility, 200L)   // condition breaks, timer reset
        detector.update(tooCloseLandmarks, fullVisibility, 400L)   // timer re-latched at t=400
        val result = detector.update(tooCloseLandmarks, fullVisibility, 400L + END_CLOSE_HOLD_MS - 1L)
        assertEquals(TrainingEndState.ACTIVE, result)
    }

    @Test
    fun conditionBreaksThenResumesFull_returnsDetected() {
        detector.update(tooCloseLandmarks, fullVisibility, 0L)
        detector.update(baselineLandmarks, fullVisibility, 200L)
        detector.update(tooCloseLandmarks, fullVisibility, 400L)
        val result = detector.update(tooCloseLandmarks, fullVisibility, 400L + END_CLOSE_HOLD_MS)
        assertEquals(TrainingEndState.DETECTED, result)
    }

    @Test
    fun leftShoulderLowVisibility_resetsTimer_returnsActive() {
        detector.update(tooCloseLandmarks, fullVisibility, 0L)
        val lowLeft = buildVisibilities(overrides = mapOf(LANDMARK_LEFT_SHOULDER to VISIBILITY_IN_FRAME_MIN - 0.01f))
        val result = detector.update(tooCloseLandmarks, lowLeft, END_CLOSE_HOLD_MS)
        assertEquals(TrainingEndState.ACTIVE, result)
    }

    @Test
    fun rightShoulderLowVisibility_resetsTimer_returnsActive() {
        detector.update(tooCloseLandmarks, fullVisibility, 0L)
        val lowRight = buildVisibilities(overrides = mapOf(LANDMARK_RIGHT_SHOULDER to VISIBILITY_IN_FRAME_MIN - 0.01f))
        val result = detector.update(tooCloseLandmarks, lowRight, END_CLOSE_HOLD_MS)
        assertEquals(TrainingEndState.ACTIVE, result)
    }

    @Test
    fun shoulderVisibilityExactlyAtThreshold_notFiltered_returnsDetected() {
        // Guard is `< VISIBILITY_IN_FRAME_MIN`; exactly at threshold must not be filtered.
        val atThreshold = buildVisibilities(default = VISIBILITY_IN_FRAME_MIN)
        detector.update(tooCloseLandmarks, atThreshold, 0L)
        val result = detector.update(tooCloseLandmarks, atThreshold, END_CLOSE_HOLD_MS)
        assertEquals(TrainingEndState.DETECTED, result)
    }

    @Test
    fun sideExercise_alwaysReturnsActive() {
        val sideDetector = TrainingEndDetector()
        sideDetector.onTrainingStart(baselineLandmarks, CameraAngle.SIDE)
        sideDetector.update(tooCloseLandmarks, fullVisibility, 0L)
        val result = sideDetector.update(tooCloseLandmarks, fullVisibility, END_CLOSE_HOLD_MS)
        assertEquals(TrainingEndState.ACTIVE, result)
    }

    @Test
    fun zeroBaseline_alwaysReturnsActive() {
        val freshDetector = TrainingEndDetector()  // onTrainingStart never called
        freshDetector.update(tooCloseLandmarks, fullVisibility, 0L)
        val result = freshDetector.update(tooCloseLandmarks, fullVisibility, END_CLOSE_HOLD_MS)
        assertEquals(TrainingEndState.ACTIVE, result)
    }

    @Test
    fun reset_clearsTooCloseTimer() {
        detector.update(tooCloseLandmarks, fullVisibility, 0L)  // latch timer
        detector.reset()
        detector.onTrainingStart(baselineLandmarks, CameraAngle.FRONT)
        // Timer was cleared by reset; this single frame re-latches at t=END_CLOSE_HOLD_MS,
        // so hold has elapsed 0 ms — must not fire DETECTED.
        val result = detector.update(tooCloseLandmarks, fullVisibility, END_CLOSE_HOLD_MS)
        assertEquals(TrainingEndState.ACTIVE, result)
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun buildLandmarks(
        vararg overrides: Pair<Int, Triple<Float, Float, Float>>,
    ): List<Triple<Float, Float, Float>> {
        val list = MutableList(LANDMARK_COUNT) { Triple(0f, 0f, 0f) }
        overrides.forEach { (idx, value) -> list[idx] = value }
        return list
    }

    private fun buildVisibilities(
        default: Float = 1f,
        overrides: Map<Int, Float> = emptyMap(),
    ): List<Float> {
        val list = MutableList(LANDMARK_COUNT) { default }
        overrides.forEach { (idx, value) -> list[idx] = value }
        return list
    }
}

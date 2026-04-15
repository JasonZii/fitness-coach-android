package com.example.fitnesscoach.training.core

import com.example.fitnesscoach.core.util.Constants.MAX_CONSECUTIVE_RED_FRAMES
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class RepScoreTrackerTest {

    private lateinit var tracker: RepScoreTracker

    @Before
    fun setUp() {
        tracker = RepScoreTracker()
    }

    // ── averageScore ──────────────────────────────────────────────────────────

    @Test
    fun `averageScore_noRepsFinished_returnsZero`() {
        assertEquals(0f, tracker.averageScore, 0f)
    }

    @Test
    fun `averageScore_oneRepFinished_equalsRepScore`() {
        repeat(4) { tracker.addFrameScore(80f, false) }
        tracker.finishRep()
        assertEquals(80f, tracker.averageScore, 0.01f)
    }

    @Test
    fun `averageScore_multipleReps_returnsCorrectMean`() {
        // Rep 1: avg 60
        repeat(2) { tracker.addFrameScore(60f, false) }
        tracker.finishRep()
        // Rep 2: avg 100
        repeat(2) { tracker.addFrameScore(100f, false) }
        tracker.finishRep()
        // Mean of [60, 100] = 80
        assertEquals(80f, tracker.averageScore, 0.01f)
    }

    // ── correct / incorrect classification ───────────────────────────────────

    @Test
    fun `finishRep_noRedFrames_countsAsCorrect`() {
        repeat(10) { tracker.addFrameScore(90f, false) }
        tracker.finishRep()

        assertEquals(1, tracker.correctReps)
        assertEquals(0, tracker.incorrectReps)
    }

    @Test
    fun `finishRep_consecutiveRedAtBoundary_countsAsCorrect`() {
        // Exactly MAX_CONSECUTIVE_RED_FRAMES consecutive red → still correct
        repeat(MAX_CONSECUTIVE_RED_FRAMES) { tracker.addFrameScore(50f, true) }
        repeat(5) { tracker.addFrameScore(90f, false) }
        tracker.finishRep()

        assertEquals(1, tracker.correctReps)
        assertEquals(0, tracker.incorrectReps)
    }

    @Test
    fun `finishRep_consecutiveRedExceedsBoundary_countsAsIncorrect`() {
        // MAX_CONSECUTIVE_RED_FRAMES + 1 consecutive red → incorrect
        repeat(MAX_CONSECUTIVE_RED_FRAMES + 1) { tracker.addFrameScore(50f, true) }
        repeat(5) { tracker.addFrameScore(90f, false) }
        tracker.finishRep()

        assertEquals(0, tracker.correctReps)
        assertEquals(1, tracker.incorrectReps)
    }

    @Test
    fun `finishRep_redFramesInterrupted_longestRunUsedForClassification`() {
        // Run of 3 red, gap, run of MAX red → max is MAX → correct
        repeat(3) { tracker.addFrameScore(50f, true) }
        tracker.addFrameScore(90f, false)
        repeat(MAX_CONSECUTIVE_RED_FRAMES) { tracker.addFrameScore(50f, true) }
        tracker.finishRep()

        assertEquals(1, tracker.correctReps)
        assertEquals(0, tracker.incorrectReps)
    }

    @Test
    fun `finishRep_emptyRepScores_returnsZeroAndDoesNotRecordRep`() {
        val score = tracker.finishRep()

        assertEquals(0f, score, 0f)
        assertEquals(0, tracker.correctReps)
        assertEquals(0, tracker.incorrectReps)
    }

    @Test
    fun `finishRep_mixedReps_countsBothCorrectAndIncorrect`() {
        // Rep 1 — correct (no red)
        repeat(5) { tracker.addFrameScore(85f, false) }
        tracker.finishRep()

        // Rep 2 — incorrect (long red run)
        repeat(MAX_CONSECUTIVE_RED_FRAMES + 2) { tracker.addFrameScore(40f, true) }
        tracker.finishRep()

        // Rep 3 — correct (exactly boundary)
        repeat(MAX_CONSECUTIVE_RED_FRAMES) { tracker.addFrameScore(50f, true) }
        repeat(5) { tracker.addFrameScore(90f, false) }
        tracker.finishRep()

        assertEquals(2, tracker.correctReps)
        assertEquals(1, tracker.incorrectReps)
    }

    // ── discardCurrentRep ────────────────────────────────────────────────────

    @Test
    fun `discardCurrentRep_pendingFrames_doesNotRecordRep`() {
        repeat(10) { tracker.addFrameScore(70f, false) }
        tracker.discardCurrentRep()
        tracker.finishRep()   // called with empty buffer → no-op

        assertEquals(0, tracker.correctReps)
        assertEquals(0, tracker.incorrectReps)
        assertEquals(0f, tracker.averageScore, 0f)
    }

    @Test
    fun `discardCurrentRep_resetsRedTracking_subsequentRepUnaffected`() {
        // Accumulate a long red run, then discard
        repeat(MAX_CONSECUTIVE_RED_FRAMES + 3) { tracker.addFrameScore(30f, true) }
        tracker.discardCurrentRep()

        // Fresh rep with no red should be correct
        repeat(5) { tracker.addFrameScore(90f, false) }
        tracker.finishRep()

        assertEquals(1, tracker.correctReps)
        assertEquals(0, tracker.incorrectReps)
    }

    // ── reset ─────────────────────────────────────────────────────────────────

    @Test
    fun `reset_afterMultipleReps_clearsAllState`() {
        repeat(5) { tracker.addFrameScore(80f, false) }
        tracker.finishRep()
        repeat(MAX_CONSECUTIVE_RED_FRAMES + 1) { tracker.addFrameScore(40f, true) }
        tracker.finishRep()

        tracker.reset()

        assertEquals(0, tracker.correctReps)
        assertEquals(0, tracker.incorrectReps)
        assertEquals(0f, tracker.averageScore, 0f)
        assertEquals(emptyList<Float>(), tracker.getCompletedRepScores())
    }

    @Test
    fun `reset_thenAddNewRep_tracksFromCleanState`() {
        repeat(5) { tracker.addFrameScore(50f, false) }
        tracker.finishRep()

        tracker.reset()

        repeat(3) { tracker.addFrameScore(100f, false) }
        tracker.finishRep()

        assertEquals(1, tracker.correctReps)
        assertEquals(0, tracker.incorrectReps)
        assertEquals(100f, tracker.averageScore, 0.01f)
    }

    // ── getCompletedRepScores ─────────────────────────────────────────────────

    @Test
    fun `getCompletedRepScores_returnsScoresInOrder`() {
        listOf(60f, 80f, 100f).forEach { score ->
            tracker.addFrameScore(score, false)
            tracker.finishRep()
        }

        assertEquals(listOf(60f, 80f, 100f), tracker.getCompletedRepScores())
    }
}

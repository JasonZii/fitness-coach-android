package com.example.fitnesscoach.training.pose

import com.example.fitnesscoach.core.util.Constants.READINESS_HOLD_SECONDS
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [ReadinessStateMachine].
 *
 * Acceptance criteria (ALGORITHM.md Module 5 / PRD TR-03):
 *   AC-1  Conditions not met → NOT_READY
 *   AC-2  Conditions first met → COUNTDOWN(3)
 *   AC-3  Within first second → COUNTDOWN(3) still
 *   AC-4  After 1 second → COUNTDOWN(2)
 *   AC-5  After 2 seconds → COUNTDOWN(1)
 *   AC-6  After 3 seconds → TRAINING_STARTED
 *   AC-7  Conditions break mid-countdown → NOT_READY; restart from 3 on next met
 *   AC-8  TRAINING_STARTED is sticky (persists even if conditions later break)
 *   AC-9  reset() fully clears state; machine behaves as new afterwards
 *   AC-10 countdownTick is 0 for NOT_READY and TRAINING_STARTED phases
 *   AC-11 Interruption mid-countdown restarts hold duration from the interruption point
 */
class ReadinessStateMachineTest {

    private lateinit var machine: ReadinessStateMachine

    @Before
    fun setUp() {
        machine = ReadinessStateMachine()
    }

    // ── AC-1: conditions not met ─────────────────────────────────────────────

    @Test
    fun update_conditionsNotMet_returnsNotReady() {
        val state = machine.update(conditionsMet = false, nowMs = 0L)
        assertEquals(ReadinessPhase.NOT_READY, state.phase)
    }

    @Test
    fun update_conditionsNotMet_countdownTickIsZero() {
        val state = machine.update(conditionsMet = false, nowMs = 0L)
        assertEquals(0, state.countdownTick)
    }

    // ── AC-2: conditions just became true → COUNTDOWN(3) ────────────────────

    @Test
    fun update_conditionsJustMet_returnsCountdown3() {
        val state = machine.update(conditionsMet = true, nowMs = 0L)
        assertEquals(ReadinessPhase.COUNTDOWN, state.phase)
        assertEquals(3, state.countdownTick)
    }

    // ── AC-3: conditions met but < 1 second elapsed → still COUNTDOWN(3) ────

    @Test
    fun update_conditionsMetLessThan1Second_staysAt3() {
        machine.update(conditionsMet = true, nowMs = 0L)
        val state = machine.update(conditionsMet = true, nowMs = 999L)
        assertEquals(ReadinessPhase.COUNTDOWN, state.phase)
        assertEquals(3, state.countdownTick)
    }

    @Test
    fun update_conditionsMetAt1msBeforeFirstTick_staysAt3() {
        machine.update(conditionsMet = true, nowMs = 1_000L)
        val state = machine.update(conditionsMet = true, nowMs = 1_999L)
        assertEquals(3, state.countdownTick)
    }

    // ── AC-4: ≥ 1 second elapsed → COUNTDOWN(2) ─────────────────────────────

    @Test
    fun update_conditionsMet1000ms_returnsCountdown2() {
        machine.update(conditionsMet = true, nowMs = 0L)
        val state = machine.update(conditionsMet = true, nowMs = 1_000L)
        assertEquals(ReadinessPhase.COUNTDOWN, state.phase)
        assertEquals(2, state.countdownTick)
    }

    @Test
    fun update_conditionsMet1500ms_returnsCountdown2() {
        machine.update(conditionsMet = true, nowMs = 0L)
        val state = machine.update(conditionsMet = true, nowMs = 1_500L)
        assertEquals(2, state.countdownTick)
    }

    @Test
    fun update_conditionsMet1999ms_returnsCountdown2() {
        machine.update(conditionsMet = true, nowMs = 0L)
        val state = machine.update(conditionsMet = true, nowMs = 1_999L)
        assertEquals(2, state.countdownTick)
    }

    // ── AC-5: ≥ 2 seconds elapsed → COUNTDOWN(1) ────────────────────────────

    @Test
    fun update_conditionsMet2000ms_returnsCountdown1() {
        machine.update(conditionsMet = true, nowMs = 0L)
        val state = machine.update(conditionsMet = true, nowMs = 2_000L)
        assertEquals(ReadinessPhase.COUNTDOWN, state.phase)
        assertEquals(1, state.countdownTick)
    }

    @Test
    fun update_conditionsMet2999ms_returnsCountdown1() {
        machine.update(conditionsMet = true, nowMs = 0L)
        val state = machine.update(conditionsMet = true, nowMs = 2_999L)
        assertEquals(1, state.countdownTick)
    }

    // ── AC-6: ≥ 3 seconds elapsed → TRAINING_STARTED ────────────────────────

    @Test
    fun update_conditionsMet3000ms_returnsTrainingStarted() {
        val holdMs = READINESS_HOLD_SECONDS * 1_000L
        machine.update(conditionsMet = true, nowMs = 0L)
        val state = machine.update(conditionsMet = true, nowMs = holdMs)
        assertEquals(ReadinessPhase.TRAINING_STARTED, state.phase)
    }

    @Test
    fun update_conditionsMetMoreThan3000ms_returnsTrainingStarted() {
        machine.update(conditionsMet = true, nowMs = 0L)
        val state = machine.update(conditionsMet = true, nowMs = 5_000L)
        assertEquals(ReadinessPhase.TRAINING_STARTED, state.phase)
    }

    @Test
    fun update_trainingStarted_countdownTickIsZero() {
        machine.update(conditionsMet = true, nowMs = 0L)
        val state = machine.update(conditionsMet = true, nowMs = 3_000L)
        assertEquals(0, state.countdownTick)
    }

    // ── AC-7: conditions break mid-countdown → NOT_READY; tick resets to 3 ──

    @Test
    fun update_conditionsBreakMidCountdown_returnsNotReady() {
        machine.update(conditionsMet = true, nowMs = 0L)
        machine.update(conditionsMet = true, nowMs = 1_500L)  // was at tick 2
        val state = machine.update(conditionsMet = false, nowMs = 2_000L)
        assertEquals(ReadinessPhase.NOT_READY, state.phase)
    }

    @Test
    fun update_conditionsRemeetAfterBreak_restartsCountdownAt3() {
        machine.update(conditionsMet = true, nowMs = 0L)
        machine.update(conditionsMet = true, nowMs = 1_500L)  // tick was 2
        machine.update(conditionsMet = false, nowMs = 2_000L) // reset
        val state = machine.update(conditionsMet = true, nowMs = 3_000L) // fresh start
        assertEquals(ReadinessPhase.COUNTDOWN, state.phase)
        assertEquals(3, state.countdownTick)
    }

    @Test
    fun update_conditionsBreakAndRejoin_holdDurationResetsFromRejoinTime() {
        // Start at t=0, break at t=1500, rejoin at t=3000
        machine.update(conditionsMet = true, nowMs = 0L)
        machine.update(conditionsMet = false, nowMs = 1_500L)
        machine.update(conditionsMet = true, nowMs = 3_000L)
        // 999ms after rejoin → still tick 3
        val stateMid = machine.update(conditionsMet = true, nowMs = 3_999L)
        assertEquals(3, stateMid.countdownTick)
        // 2000ms after rejoin → tick 1
        val stateLate = machine.update(conditionsMet = true, nowMs = 5_000L)
        assertEquals(1, stateLate.countdownTick)
    }

    // ── AC-8: TRAINING_STARTED is sticky ────────────────────────────────────

    @Test
    fun update_afterTrainingStarted_conditionsBreakStillReturnsTrainingStarted() {
        machine.update(conditionsMet = true, nowMs = 0L)
        machine.update(conditionsMet = true, nowMs = 3_000L) // started
        val state = machine.update(conditionsMet = false, nowMs = 4_000L)
        assertEquals(ReadinessPhase.TRAINING_STARTED, state.phase)
    }

    @Test
    fun update_afterTrainingStarted_repeatedCallsAlwaysReturnTrainingStarted() {
        machine.update(conditionsMet = true, nowMs = 0L)
        machine.update(conditionsMet = true, nowMs = 3_000L)
        repeat(5) { i ->
            val state = machine.update(conditionsMet = (i % 2 == 0), nowMs = 4_000L + i * 100L)
            assertEquals(
                "Call $i after training started must still return TRAINING_STARTED",
                ReadinessPhase.TRAINING_STARTED, state.phase
            )
        }
    }

    // ── AC-9: reset() clears all state ──────────────────────────────────────

    @Test
    fun reset_afterTrainingStarted_allowsNewSession() {
        machine.update(conditionsMet = true, nowMs = 0L)
        machine.update(conditionsMet = true, nowMs = 3_000L) // training started
        machine.reset()
        val state = machine.update(conditionsMet = false, nowMs = 5_000L)
        assertEquals(ReadinessPhase.NOT_READY, state.phase)
    }

    @Test
    fun reset_midCountdown_restartsHoldFromNextMet() {
        machine.update(conditionsMet = true, nowMs = 0L)
        machine.update(conditionsMet = true, nowMs = 2_500L) // tick was 1
        machine.reset()
        val state = machine.update(conditionsMet = true, nowMs = 5_000L)
        assertEquals(ReadinessPhase.COUNTDOWN, state.phase)
        assertEquals(3, state.countdownTick) // fresh hold starts at t=5000
    }

    // ── AC-10: countdownTick is 0 when not in COUNTDOWN phase ───────────────

    @Test
    fun notReadyState_countdownTickIsZero() {
        assertEquals(0, machine.update(conditionsMet = false, nowMs = 0L).countdownTick)
    }

    @Test
    fun trainingStartedState_countdownTickIsZero() {
        machine.update(conditionsMet = true, nowMs = 0L)
        val state = machine.update(conditionsMet = true, nowMs = 3_000L)
        assertEquals(0, state.countdownTick)
    }

    // ── AC-11: full 3-2-1 sequence with correct tick progression ─────────────

    @Test
    fun update_fullCountdownProgression_ticksDecrement3to1ThenTrainingStarts() {
        val t0 = 10_000L

        val s0 = machine.update(true, t0)
        assertEquals(ReadinessPhase.COUNTDOWN, s0.phase)
        assertEquals(3, s0.countdownTick)

        val s1 = machine.update(true, t0 + 1_000L)
        assertEquals(ReadinessPhase.COUNTDOWN, s1.phase)
        assertEquals(2, s1.countdownTick)

        val s2 = machine.update(true, t0 + 2_000L)
        assertEquals(ReadinessPhase.COUNTDOWN, s2.phase)
        assertEquals(1, s2.countdownTick)

        val s3 = machine.update(true, t0 + 3_000L)
        assertEquals(ReadinessPhase.TRAINING_STARTED, s3.phase)
        assertEquals(0, s3.countdownTick)
    }

    // ── Edge case: update called with nowMs equal to conditionsMetSinceMs ────

    @Test
    fun update_sameTimestampForFirstAndSecondCall_returnsCountdown3() {
        machine.update(conditionsMet = true, nowMs = 500L)
        val state = machine.update(conditionsMet = true, nowMs = 500L)
        assertEquals(ReadinessPhase.COUNTDOWN, state.phase)
        assertEquals(3, state.countdownTick)
    }
}

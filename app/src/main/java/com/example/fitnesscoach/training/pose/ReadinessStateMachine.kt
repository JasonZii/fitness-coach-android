package com.example.fitnesscoach.training.pose

import com.example.fitnesscoach.core.util.Constants.READINESS_HOLD_SECONDS

/**
 * Phase of the training-readiness state machine (ALGORITHM.md Module 5).
 */
enum class ReadinessPhase {
    /** One or both readiness conditions are not met; show a prompt to the user. */
    NOT_READY,

    /**
     * Both conditions (full-body in frame + correct camera angle) have been met
     * continuously. [ReadinessState.countdownTick] contains the digit (3, 2, or 1)
     * currently displayed on screen.
     */
    COUNTDOWN,

    /**
     * The countdown completed. Training should begin.
     * This phase is sticky: once reached it persists until [ReadinessStateMachine.reset]
     * is called.
     */
    TRAINING_STARTED,
}

/**
 * Snapshot of the readiness state returned by [ReadinessStateMachine.update].
 *
 * @property phase          Current phase of the readiness flow.
 * @property countdownTick  Digit shown on screen during [ReadinessPhase.COUNTDOWN]
 *                          (3, 2, or 1). Zero for all other phases.
 */
data class ReadinessState(
    val phase: ReadinessPhase,
    val countdownTick: Int = 0,
)

/**
 * Stateful machine that tracks whether readiness conditions have been held
 * long enough to trigger training, and derives the 3-2-1 countdown.
 *
 * Usage per camera frame:
 * ```
 * val state = machine.update(conditionsMet = bothReady, nowMs = System.currentTimeMillis())
 * ```
 *
 * Time is injected as a [Long] parameter (milliseconds) so the class stays
 * pure Kotlin and is directly unit-testable without any mocking.
 *
 * State transitions (ALGORITHM.md Module 5):
 * ```
 * NOT_READY ──(conditions met)──► COUNTDOWN(3)
 *                                    │ 1 s elapsed
 *                                    ▼
 *                                 COUNTDOWN(2)
 *                                    │ 1 s elapsed
 *                                    ▼
 *                                 COUNTDOWN(1)
 *                                    │ 1 s elapsed
 *                                    ▼
 *                              TRAINING_STARTED  (sticky)
 *
 * COUNTDOWN(any) ──(conditions break)──► NOT_READY  (full reset)
 * ```
 */
class ReadinessStateMachine {

    /** Timestamp (ms) at which both conditions first became true in the current run. */
    private var conditionsMetSinceMs: Long? = null

    /** Latched to true once the countdown finishes; cleared only by [reset]. */
    private var trainingStarted: Boolean = false

    /**
     * Advance the state machine by one frame.
     *
     * @param conditionsMet True when both [isFullBodyInFrame] and [detectCameraAngle]
     *                      report that the user is correctly positioned.
     * @param nowMs         Current wall-clock time in milliseconds.
     *                      Pass [System.currentTimeMillis()] in production;
     *                      pass a synthetic value in tests.
     * @return The current [ReadinessState].
     */
    fun update(conditionsMet: Boolean, nowMs: Long): ReadinessState {
        if (trainingStarted) return ReadinessState(ReadinessPhase.TRAINING_STARTED)

        if (!conditionsMet) {
            conditionsMetSinceMs = null
            return ReadinessState(ReadinessPhase.NOT_READY)
        }

        // Record when the hold started
        if (conditionsMetSinceMs == null) {
            conditionsMetSinceMs = nowMs
        }

        val elapsedMs = nowMs - conditionsMetSinceMs!!
        val holdMs = READINESS_HOLD_SECONDS * 1_000L

        return if (elapsedMs >= holdMs) {
            trainingStarted = true
            ReadinessState(ReadinessPhase.TRAINING_STARTED)
        } else {
            // tick = 3, 2, or 1 depending on which second we are in
            val tick = READINESS_HOLD_SECONDS - (elapsedMs / 1_000L).toInt()
            ReadinessState(ReadinessPhase.COUNTDOWN, tick.coerceAtLeast(1))
        }
    }

    /**
     * Resets all internal state so the machine can be reused for a new session.
     * Call this when the user explicitly stops training or navigates away.
     */
    fun reset() {
        conditionsMetSinceMs = null
        trainingStarted = false
    }
}

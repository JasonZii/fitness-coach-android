package com.example.fitnesscoach.training.pose

import com.example.fitnesscoach.core.util.Constants.READINESS_HOLD_SECONDS
enum class ReadinessPhase {
    NOT_READY,
    COUNTDOWN,
    TRAINING_STARTED,
}
data class ReadinessState(
    val phase: ReadinessPhase,
    val countdownTick: Int = 0,
)

class ReadinessStateMachine {
    private var holdStartTimestamp: Long? = null
    private var trainingStarted: Boolean = false
    fun update(conditionsMet: Boolean, currentTimestamp: Long): ReadinessState {
        if (trainingStarted) return ReadinessState(ReadinessPhase.TRAINING_STARTED)

        if (!conditionsMet) {
            holdStartTimestamp = null
            return ReadinessState(ReadinessPhase.NOT_READY)
        }

        // Record when the hold started
        if (holdStartTimestamp == null) {
            holdStartTimestamp = currentTimestamp
        }

        val currentHoldDuration = currentTimestamp - holdStartTimestamp!!
        val requiredHoldDuration = READINESS_HOLD_SECONDS * 1_000L

        return if (currentHoldDuration >= requiredHoldDuration) {
            trainingStarted = true
            ReadinessState(ReadinessPhase.TRAINING_STARTED)
        } else {
            // tick = 3, 2, or 1 depending on which second we are in
            val tick = READINESS_HOLD_SECONDS - (currentHoldDuration / 1_000L).toInt()
            ReadinessState(ReadinessPhase.COUNTDOWN, tick.coerceAtLeast(1))
        }
    }

    /**
     * Resets all internal state so the machine can be reused for a new session.
     * Call this when the user explicitly stops training or navigates away.
     */
    fun reset() {
        holdStartTimestamp = null
        trainingStarted = false
    }
}

package com.example.fitnesscoach.training.pose

import com.example.fitnesscoach.core.util.Constants.END_SHOULDER_WIDTH_RATIO
import com.example.fitnesscoach.core.util.Constants.END_VISIBILITY_HOLD_SECONDS
import com.example.fitnesscoach.core.util.Constants.END_VISIBILITY_THRESHOLD
import com.example.fitnesscoach.core.util.Constants.LANDMARK_LEFT_SHOULDER
import com.example.fitnesscoach.core.util.Constants.LANDMARK_NOSE
import com.example.fitnesscoach.core.util.Constants.LANDMARK_RIGHT_HIP
import com.example.fitnesscoach.core.util.Constants.LANDMARK_RIGHT_SHOULDER
import com.example.fitnesscoach.core.util.Constants.LANDMARK_LEFT_HIP
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Landmark indices checked for the low-visibility pause condition (ALGORITHM.md Module 6).
 * Average visibility of these five landmarks below [END_VISIBILITY_THRESHOLD] for
 * [END_VISIBILITY_HOLD_SECONDS] seconds triggers a pause.
 */
private val PAUSE_VISIBILITY_INDICES = intArrayOf(
    LANDMARK_NOSE,
    LANDMARK_LEFT_SHOULDER,
    LANDMARK_RIGHT_SHOULDER,
    LANDMARK_LEFT_HIP,
    LANDMARK_RIGHT_HIP,
)

/**
 * Training-session pause state returned by [TrainingEndDetector.update].
 */
enum class TrainingPauseState {
    /** Normal: training data should be accumulated. */
    ACTIVE,
    /** Paused: the current rep should be discarded and accumulation halted. */
    PAUSED,
}

/**
 * Detects two conditions that should pause (and later resume) the training session
 * (ALGORITHM.md Module 6):
 *
 * **Condition 1 – low visibility**: The average visibility of the five key landmarks
 * (nose, both shoulders, both hips) falls below [END_VISIBILITY_THRESHOLD] (0.3) for
 * at least [END_VISIBILITY_HOLD_SECONDS] (3) consecutive seconds.
 * Resume: the average visibility returns to or above the threshold.
 *
 * **Condition 2 – too close**: The pixel distance between the two shoulders exceeds
 * [END_SHOULDER_WIDTH_RATIO] (1.5×) times the baseline shoulder width captured at
 * [onTrainingStart]. This fires immediately (no hold period) and indicates the user
 * has stepped toward the camera.
 * Resume: the shoulder width drops back to or below the threshold.
 *
 * Time is injected as [nowMs] so the class is pure Kotlin and directly unit-testable.
 *
 * Usage:
 * ```
 * val detector = TrainingEndDetector()
 * // On READINESS → TRAINING transition:
 * detector.onTrainingStart(poseResult.landmarks)
 * // Each training frame:
 * val state = detector.update(poseResult.landmarks, poseResult.visibilities, nowMs)
 * ```
 */
class TrainingEndDetector {

    /** Shoulder pixel distance recorded at the moment training starts. */
    private var baselineShoulderWidth: Float = 0f

    /**
     * Timestamp (ms) when the average landmark visibility first dropped below threshold.
     * Null when visibility is at or above the threshold.
     */
    private var visibilityDropSinceMs: Long? = null

    /** Whether the detector is currently in a paused state. */
    private var currentState: TrainingPauseState = TrainingPauseState.ACTIVE

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Records the baseline shoulder width from the first training frame.
     * Must be called exactly once when the session transitions from READINESS to TRAINING.
     *
     * @param landmarks Raw MediaPipe landmarks for the starting frame.
     */
    fun onTrainingStart(landmarks: List<Triple<Float, Float, Float>>) {
        baselineShoulderWidth = shoulderWidth(landmarks)
        visibilityDropSinceMs = null
        currentState = TrainingPauseState.ACTIVE
    }

    /**
     * Evaluates the current frame for pause conditions.
     *
     * @param landmarks   Raw MediaPipe (x, y, z) landmarks for this frame.
     * @param visibilities Per-landmark visibility scores from MediaPipe (index 0–32).
     * @param nowMs        Current wall-clock time in milliseconds.
     * @return [TrainingPauseState.PAUSED] if either condition fires; [TrainingPauseState.ACTIVE] otherwise.
     */
    fun update(
        landmarks: List<Triple<Float, Float, Float>>,
        visibilities: List<Float>,
        nowMs: Long,
    ): TrainingPauseState {
        val tooClose      = isTooClose(landmarks)
        val lowVisibility = isLowVisibility(visibilities, nowMs)

        currentState = if (tooClose || lowVisibility) TrainingPauseState.PAUSED else TrainingPauseState.ACTIVE
        return currentState
    }

    /**
     * Resets all state. Call when the user explicitly stops training or the session ends.
     */
    fun reset() {
        baselineShoulderWidth = 0f
        visibilityDropSinceMs = null
        currentState = TrainingPauseState.ACTIVE
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Condition 2: returns true when the current shoulder width exceeds
     * baselineShoulderWidth × [END_SHOULDER_WIDTH_RATIO].
     * Returns false before [onTrainingStart] is called (baseline == 0).
     */
    private fun isTooClose(landmarks: List<Triple<Float, Float, Float>>): Boolean {
        if (baselineShoulderWidth < 1e-6f) return false
        val current = shoulderWidth(landmarks)
        return current > baselineShoulderWidth * END_SHOULDER_WIDTH_RATIO
    }

    /**
     * Condition 1: returns true when the key-landmark average visibility has been
     * below [END_VISIBILITY_THRESHOLD] for at least [END_VISIBILITY_HOLD_SECONDS] seconds.
     * Clears the timer when visibility recovers.
     */
    private fun isLowVisibility(visibilities: List<Float>, nowMs: Long): Boolean {
        val avgVisibility = PAUSE_VISIBILITY_INDICES
            .map { visibilities[it] }
            .average()
            .toFloat()

        return if (avgVisibility < END_VISIBILITY_THRESHOLD) {
            if (visibilityDropSinceMs == null) visibilityDropSinceMs = nowMs
            val elapsedMs = nowMs - visibilityDropSinceMs!!
            elapsedMs >= END_VISIBILITY_HOLD_SECONDS * 1_000L
        } else {
            visibilityDropSinceMs = null   // visibility recovered — reset timer
            false
        }
    }

    /**
     * Euclidean distance between left and right shoulder landmarks (x, y only).
     */
    private fun shoulderWidth(landmarks: List<Triple<Float, Float, Float>>): Float {
        val left  = landmarks[LANDMARK_LEFT_SHOULDER]
        val right = landmarks[LANDMARK_RIGHT_SHOULDER]
        val dx = left.first  - right.first
        val dy = left.second - right.second
        return sqrt(dx * dx + dy * dy)
    }
}

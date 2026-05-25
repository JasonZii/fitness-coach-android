package com.example.fitnesscoach.training.core

import androidx.compose.ui.graphics.Color
import com.example.fitnesscoach.core.util.Constants.LIMB_COUNT
import com.example.fitnesscoach.core.util.Constants.MAX_CONSECUTIVE_RED_FRAMES

class RepScoreTracker {
    private val currentRepFrameScores = mutableListOf<Float>()
    private val completedRepScores = mutableListOf<Float>()

    // ── Red Light Tracking (Correct/Incorrect Logic) ──
    // Per-limb counters: a limb is considered persistently wrong only when the same
    // limb has been red for more than MAX_CONSECUTIVE_RED_FRAMES consecutive frames.
    private val currentConsecutiveRedPerLimb = IntArray(LIMB_COUNT)
    private val maxConsecutiveRedPerLimb = IntArray(LIMB_COUNT)

    // Supplied to TrainingViewModel each frame to drive per-limb visual color output.
    val visualLimbIsRed: BooleanArray
        get() = BooleanArray(LIMB_COUNT) { i ->
            currentConsecutiveRedPerLimb[i] > MAX_CONSECUTIVE_RED_FRAMES
        }

    // ── Final Rep Counts ──
    var correctReps = 0
        private set
    var incorrectReps = 0
        private set

    /** Average Sf score across all completed reps; 0 when no rep has been finished yet. */
    val averageScore: Float
        get() = if (completedRepScores.isEmpty()) 0f
                else completedRepScores.average().toFloat()

    /**
     * Adds the current frame's overall score [sf] and per-limb colors [limbColors].
     * Each limb's consecutive-red counter increments independently; only a limb that
     * stays red for more than MAX_CONSECUTIVE_RED_FRAMES frames triggers visual feedback.
     */
    fun addFrameScore(sf: Float, limbColors: List<Color>) {
        currentRepFrameScores.add(sf)
        for (i in 0 until LIMB_COUNT) {
            if (i < limbColors.size && limbColors[i] == Color.Red) {
                currentConsecutiveRedPerLimb[i]++
                if (currentConsecutiveRedPerLimb[i] > maxConsecutiveRedPerLimb[i]) {
                    maxConsecutiveRedPerLimb[i] = currentConsecutiveRedPerLimb[i]
                }
            } else {
                currentConsecutiveRedPerLimb[i] = 0
            }
        }
    }

    /**
     * Seals the current rep, calculates the average score, updates correct/incorrect counts.
     * A rep is INCORRECT when any single limb's max consecutive red run exceeds the threshold.
     */
    fun finishRep(): Float {
        if (currentRepFrameScores.isEmpty()) return 0f

        val repScore = currentRepFrameScores.average().toFloat()
        completedRepScores.add(repScore)

        if (maxConsecutiveRedPerLimb.none { it > MAX_CONSECUTIVE_RED_FRAMES }) {
            correctReps++
        } else {
            incorrectReps++
        }

        // Reset per-rep counters
        currentRepFrameScores.clear()
        currentConsecutiveRedPerLimb.fill(0)
        maxConsecutiveRedPerLimb.fill(0)

        return repScore
    }

    fun getCompletedRepScores(): List<Float> = completedRepScores.toList()

    /**
     * Discards the in-progress rep's accumulated frame scores without recording a rep.
     * Call when training is interrupted (Module 6 pause) so a partial rep is not
     * included in the session summary.
     */
    fun discardCurrentRep() {
        currentRepFrameScores.clear()
        currentConsecutiveRedPerLimb.fill(0)
        maxConsecutiveRedPerLimb.fill(0)
    }

    /**
     * Resets all state: clears both in-progress and completed rep scores, and correct/incorrect counts.
     * Call when starting a new training session.
     */
    fun reset() {
        currentRepFrameScores.clear()
        completedRepScores.clear()
        currentConsecutiveRedPerLimb.fill(0)
        maxConsecutiveRedPerLimb.fill(0)
        correctReps = 0
        incorrectReps = 0
    }
}

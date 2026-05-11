package com.example.fitnesscoach.training.core

import com.example.fitnesscoach.core.util.Constants.MAX_CONSECUTIVE_RED_FRAMES

class RepScoreTracker {
    private val currentRepFrameScores = mutableListOf<Float>()
    private val completedRepScores = mutableListOf<Float>()

    // ── Red Light Tracking (Correct/Incorrect Logic) ──
    private var currentConsecutiveRed = 0
    private var maxConsecutiveRed = 0

    val currentConsecutiveRedFrames: Int
        get() = currentConsecutiveRed
    
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
     * Adds the current frame's overall score [sf] and whether any joint/limb is red [hasRedPart].
     */
    fun addFrameScore(sf: Float, hasRedPart: Boolean) {
        currentRepFrameScores.add(sf)
        
        if (hasRedPart) {
            currentConsecutiveRed++
            if (currentConsecutiveRed > maxConsecutiveRed) {
                maxConsecutiveRed = currentConsecutiveRed
            }
        } else {
            currentConsecutiveRed = 0
        }
    }

    /**
     * Seals the current rep, calculates the average score, updates correct/incorrect counts.
     */
    fun finishRep(): Float {
        if (currentRepFrameScores.isEmpty()) return 0f

        val repScore = currentRepFrameScores.average().toFloat()
        completedRepScores.add(repScore)
        
        // If max continuous red frames across this rep is <= MAX_CONSECUTIVE_RED_FRAMES (~0.25s), it's considered CORRECT
        if (maxConsecutiveRed <= MAX_CONSECUTIVE_RED_FRAMES) {
            correctReps++
        } else {
            incorrectReps++
        }

        // Reset per-rep counters
        currentRepFrameScores.clear()
        currentConsecutiveRed = 0
        maxConsecutiveRed = 0
        
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
        currentConsecutiveRed = 0
        maxConsecutiveRed = 0
    }

    /**
     * Resets all state: clears both in-progress and completed rep scores, and correct/incorrect counts.
     * Call when starting a new training session.
     */
    fun reset() {
        currentRepFrameScores.clear()
        completedRepScores.clear()
        currentConsecutiveRed = 0
        maxConsecutiveRed = 0
        correctReps = 0
        incorrectReps = 0
    }
}

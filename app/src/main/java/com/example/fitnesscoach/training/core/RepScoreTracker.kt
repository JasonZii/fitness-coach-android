package com.example.fitnesscoach.training.core

class RepScoreTracker {
    private val currentRepFrameScores = mutableListOf<Float>()
    private val completedRepScores = mutableListOf<Float>()

    fun addFrameScore(sf: Float) {
        currentRepFrameScores.add(sf)
    }

    fun finishRep(): Float {
        if (currentRepFrameScores.isEmpty()) return 0f

        val repScore = currentRepFrameScores.average().toFloat()
        completedRepScores.add(repScore)
        currentRepFrameScores.clear()
        return repScore
    }

    fun getCompletedRepScores(): List<Float> = completedRepScores.toList()
}
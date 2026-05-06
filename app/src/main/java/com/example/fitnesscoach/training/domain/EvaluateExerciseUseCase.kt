package com.example.fitnesscoach.training.domain

import androidx.compose.ui.graphics.Color
import com.example.fitnesscoach.core.util.Constants.LANDMARK_COUNT
import com.example.fitnesscoach.core.util.Constants.LIMB_COUNT
import com.example.fitnesscoach.training.core.PoseScoreResult
import com.example.fitnesscoach.training.core.PoseScoringEngine

/**
 * Evaluates a single live frame against the matched standard frame.
 *
 * Wraps [PoseScoringEngine.calculatePoseScore] and handles the OE-DTW unstable
 * period: when [matchedReferenceIndex] is -1 (fewer than 20 user frames), all
 * joints and limbs are returned as green per ALGORITHM.md §Module 2.
 */
class EvaluateExerciseUseCase {

    private val allGreenResult = PoseScoreResult(
        jointColors = List(LANDMARK_COUNT) { Color.Green },
        limbColors = List(LIMB_COUNT) { Color.Green },
        s1 = 100f,
        s2 = 100f,
        sf = 100f,
        jointScores = List(LANDMARK_COUNT) { 100f },
        limbScores = List(LIMB_COUNT) { 100f }
    )

    /**
     * @param matchedReferenceIndex  Index returned by OE-DTW. Pass -1 during the
     *                               warm-up period (< 20 frames); all colors will
     *                               be green and sf will be 100.
     * @param userLandmarks          33 normalised (x, y, z) triples for the current frame.
     * @param referenceSequence      Full normalised standard action sequence.
     * @param upperBodyOnly          Pass `true` for exercises whose [ExerciseInfo.requiresFullBody]
     *                               is false. Lower-body joints and limbs are excluded from scoring
     *                               and forced green so they never pollute the score or UI.
     * @return [PoseScoreResult] with per-joint/limb colors, scores, and overall sf.
     */
    fun evaluate(
        matchedReferenceIndex: Int,
        userLandmarks: List<Triple<Float, Float, Float>>,
        referenceSequence: List<List<Triple<Float, Float, Float>>>,
        upperBodyOnly: Boolean = false,
        exerciseId: String = "",
    ): PoseScoreResult {
        if (matchedReferenceIndex == -1) return allGreenResult
        val referenceLandmarks = referenceSequence[matchedReferenceIndex]
        return PoseScoringEngine.calculatePoseScore(
            userLandmarks.map { it.first to it.second },
            referenceLandmarks.map { it.first to it.second },
            upperBodyOnly,
            bicepCurlOnly = exerciseId == "bicep_curl",
            squatOnly = exerciseId == "squat",
            lungeOnly = exerciseId == "right_leg_lunge_to_knee_raise"
        )
    }
}

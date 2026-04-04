package com.example.fitnesscoach.core.util

object Constants {

    // MediaPipe landmark indices
    const val LANDMARK_NOSE = 0
    const val LANDMARK_LEFT_SHOULDER = 11
    const val LANDMARK_RIGHT_SHOULDER = 12
    const val LANDMARK_LEFT_ELBOW = 13
    const val LANDMARK_RIGHT_ELBOW = 14
    const val LANDMARK_LEFT_WRIST = 15
    const val LANDMARK_RIGHT_WRIST = 16
    const val LANDMARK_LEFT_HIP = 23
    const val LANDMARK_RIGHT_HIP = 24
    const val LANDMARK_LEFT_KNEE = 25
    const val LANDMARK_RIGHT_KNEE = 26
    const val LANDMARK_LEFT_ANKLE = 27
    const val LANDMARK_RIGHT_ANKLE = 28

    // Total number of MediaPipe BlazePose landmarks per frame
    const val LANDMARK_COUNT = 33
    // Total number of limb connections used for skeleton overlay and scoring
    const val LIMB_COUNT = 13

    // Module 1: Skeleton normalisation
    // Acceptable error tolerance for acceptance-criteria checks
    const val NORMALISE_EPSILON = 0.001f

    // Module 2: OE-DTW real-time alignment
    // Minimum user sequence length before alignment is considered stable
    const val OE_DTW_MIN_FRAMES = 20

    // Module 3: Per-landmark scoring
    // Joint/limb score below this threshold → RED; at or above → GREEN
    const val SCORE_RED_THRESHOLD = 80f
    // Weight of joint-position score S1 in the overall frame score Sf
    const val SCORE_WEIGHT_S1 = 0.2f
    // Weight of limb-angle score S2 in the overall frame score Sf
    const val SCORE_WEIGHT_S2 = 0.8f
}
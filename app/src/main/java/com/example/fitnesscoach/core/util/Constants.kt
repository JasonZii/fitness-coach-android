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

    // Module 1: Skeleton normalisation
    // Acceptable error tolerance for acceptance-criteria checks
    const val NORMALISE_EPSILON = 0.001f
}
package com.example.fitnesscoach.core.mediapipe

/**
 * Raw MediaPipe output for a single frame.
 *
 * Keeps the camera/MediaPipe boundary aligned with ALGORITHM.md:
 * - landmarks: 33 raw (x, y, z) triples
 * - visibilities: per-landmark visibility scores, kept separate from coordinates
 */
data class PoseResult(
    val landmarks: List<Triple<Float, Float, Float>>,
    val visibilities: List<Float>,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
)

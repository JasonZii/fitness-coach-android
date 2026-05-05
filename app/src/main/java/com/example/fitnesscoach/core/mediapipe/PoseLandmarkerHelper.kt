package com.example.fitnesscoach.core.mediapipe

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

class PoseLandmarkerHelper(context: Context) {

    private val poseLandmarker: PoseLandmarker

    init {
        // Try GPU first for lower latency; fall back to CPU if the device
        // does not support the required OpenGL ES version or driver features.
        poseLandmarker =
            tryCreateWithDelegate(context.applicationContext, Delegate.GPU)
                ?: tryCreateWithDelegate(context.applicationContext, Delegate.CPU)
                ?: error("PoseLandmarker: both GPU and CPU init failed")
    }

    private fun tryCreateWithDelegate(context: Context, delegate: Delegate): PoseLandmarker? =
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("pose_landmarker_full.task")
                .setDelegate(delegate)
                .build()
            val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.VIDEO)
                .setNumPoses(1)
                .setMinPoseDetectionConfidence(0.5f)
                .setMinPosePresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.6f)
                .build()
            PoseLandmarker.createFromOptions(context, options).also {
                Log.d("POSE", "PoseLandmarker initialised with $delegate")
            }
        } catch (e: Exception) {
            Log.w("POSE", "Cannot init PoseLandmarker with $delegate: ${e.message}")
            null
        }

    /**
     * Synchronously detects pose landmarks from [bitmap].
     * Blocks the caller's thread (analysisExecutor) until inference completes.
     * VIDEO mode guarantees no async callback, no frame-queue build-up.
     */
    fun detectSync(bitmap: Bitmap, timestampMs: Long): PoseResult =
        try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            mapResult(
                result = poseLandmarker.detectForVideo(mpImage, timestampMs),
                imageWidth = bitmap.width,
                imageHeight = bitmap.height,
            )
        } catch (e: Exception) {
            Log.e("POSE", "Pose detection failed", e)
            PoseResult(emptyList(), emptyList(), bitmap.width, bitmap.height)
        }

    fun close() {
        poseLandmarker.close()
    }

    private fun mapResult(
        result: PoseLandmarkerResult,
        imageWidth: Int,
        imageHeight: Int,
    ): PoseResult {
        val landmarks = result.landmarks()
        if (landmarks.isEmpty()) return PoseResult(emptyList(), emptyList(), imageWidth, imageHeight)
        val firstPose = landmarks[0]
        return PoseResult(
            landmarks    = firstPose.map { Triple(it.x(), it.y(), it.z()) },
            visibilities = firstPose.map { it.visibility().orElse(0f) },
            imageWidth = imageWidth,
            imageHeight = imageHeight,
        )
    }
}

package com.example.fitnesscoach.core.mediapipe

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

class PoseLandmarkerHelper(
    context: Context,
    private val onResult: (PoseResult) -> Unit
) {

    private val poseLandmarker: PoseLandmarker
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        val appContext = context.applicationContext

        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("pose_landmarker_full.task")
            .setDelegate(Delegate.CPU)
            .build()

        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumPoses(1)
            .setResultListener { result: PoseLandmarkerResult, _: com.google.mediapipe.framework.image.MPImage ->
                deliverResult(mapResult(result))
            }
            .setErrorListener { error ->
                Log.e("POSE", "Pose detection failed", error)
                deliverResult(
                    PoseResult(
                        landmarks = emptyList(),
                        visibilities = emptyList()
                    )
                )
            }
            .build()

        poseLandmarker = PoseLandmarker.createFromOptions(appContext, options)
        Log.d("POSE", "PoseLandmarker initialized")
    }

    fun detectAsync(bitmap: Bitmap, timestampMs: Long) {
        try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            poseLandmarker.detectAsync(mpImage, timestampMs)
        } catch (e: Exception) {
            Log.e("POSE", "Pose detection submission failed", e)
            deliverResult(
                PoseResult(
                    landmarks = emptyList(),
                    visibilities = emptyList()
                )
            )
        }
    }

    fun close() {
        poseLandmarker.close()
    }

    private fun mapResult(result: PoseLandmarkerResult): PoseResult {
        val landmarks = result.landmarks()
        if (landmarks.isEmpty()) {
            Log.d("POSE", "Landmarks count: 0")
            return PoseResult(
                landmarks = emptyList(),
                visibilities = emptyList()
            )
        }

        val firstPose = landmarks[0]
        Log.d("POSE", "Landmarks count: ${firstPose.size}")

        return PoseResult(
            landmarks = firstPose.map { landmark ->
                Triple(
                    landmark.x(),
                    landmark.y(),
                    landmark.z()
                )
            },
            visibilities = firstPose.map { landmark ->
                landmark.visibility().orElse(0f)
            }
        )
    }

    private fun deliverResult(result: PoseResult) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            onResult(result)
        } else {
            mainHandler.post {
                onResult(result)
            }
        }
    }
}

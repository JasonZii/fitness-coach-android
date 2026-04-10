package com.example.fitnesscoach.core.mediapipe

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker

class PoseLandmarkerHelper(context: Context) {

    private val poseLandmarker: PoseLandmarker

    init {
        val appContext = context.applicationContext

        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("pose_landmarker_full.task")
            .setDelegate(Delegate.CPU)
            .build()

        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.IMAGE)
            .setNumPoses(1)
            .build()

        poseLandmarker = PoseLandmarker.createFromOptions(appContext, options)
        Log.d("POSE", "PoseLandmarker initialized")
    }

    fun detect(bitmap: Bitmap): PoseResult {
        return try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            val result = poseLandmarker.detect(mpImage)

            val landmarks = result.landmarks()
            if (landmarks.isEmpty()) {
                Log.d("POSE", "Landmarks count: 0")
                PoseResult(
                    landmarks = emptyList(),
                    visibilities = emptyList()
                )
            } else {
                val firstPose = landmarks[0]
                Log.d("POSE", "Landmarks count: ${firstPose.size}")

                PoseResult(
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
        } catch (e: Exception) {
            Log.e("POSE", "Pose detection failed", e)
            PoseResult(
                landmarks = emptyList(),
                visibilities = emptyList()
            )
        }
    }

    fun close() {
        poseLandmarker.close()
    }
}

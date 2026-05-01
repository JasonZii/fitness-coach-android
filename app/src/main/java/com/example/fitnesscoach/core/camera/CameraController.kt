package com.example.fitnesscoach.core.camera

import android.content.Context
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraController(private val context: Context) {

    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    /**
     * Starts the camera in analysis-only mode (no Preview use case).
     * Each captured frame is delivered to [onFrameAvailable] on a background thread.
     * The caller is responsible for displaying the processed result (e.g., drawing
     * skeleton on the bitmap and rendering to a SurfaceView).
     */
    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        onFrameAvailable: (ImageProxy) -> Unit
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(360, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                        onFrameAvailable(imageProxy)
                    }
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    imageAnalysis
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun release() {
        analysisExecutor.shutdown()
    }
}

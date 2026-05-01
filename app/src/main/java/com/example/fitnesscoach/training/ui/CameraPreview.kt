package com.example.fitnesscoach.training.ui

import android.content.Context
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.fitnesscoach.core.camera.CameraController
import com.example.fitnesscoach.core.mediapipe.PoseResult
import com.example.fitnesscoach.training.pose.PoseFrameProcessor

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    context: Context,
    onPoseDetected: (PoseResult) -> Unit = {}
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraController = remember { CameraController(context) }
    val frameProcessor = remember { PoseFrameProcessor(context) }

    DisposableEffect(Unit) {
        onDispose {
            cameraController.release()
            frameProcessor.close()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PreviewView(ctx).apply {
//                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                scaleType = PreviewView.ScaleType.FILL_CENTER

                cameraController.startCamera(
                    previewView = this,
                    lifecycleOwner = lifecycleOwner,
                    onFrameAvailable = { imageProxy ->
                        frameProcessor.processFrame(
                            imageProxy = imageProxy,
                            timestampMs = System.currentTimeMillis(),
                            onResult = { poseResult ->
                                onPoseDetected(poseResult)
                            }
                        )
                    }
                )
            }
        }
    )
}

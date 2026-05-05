package com.example.fitnesscoach.training.ui

import android.content.Context
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.fitnesscoach.core.camera.CameraController
import com.example.fitnesscoach.core.mediapipe.PoseResult
import com.example.fitnesscoach.training.pose.PoseFrameProcessor
import java.util.concurrent.atomic.AtomicReference

/**
 * Fully synchronous camera display composable.
 *
 * - No Preview use case: the camera stream is consumed entirely by ImageAnalysis.
 * - Each frame is: YUV → Bitmap → MediaPipe inference → skeleton drawn on Bitmap →
 *   Bitmap rendered to [SurfaceView] → [onPoseDetected] called with the PoseResult.
 * - Because the skeleton is burned into the same Bitmap that is displayed, position
 *   is always frame-perfect — no PreviewView / overlay desync.
 *
 * [frameProcessor] is owned by the ViewModel; this composable does NOT close it.
 */
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    context: Context,
    frameProcessor: PoseFrameProcessor,
    onPoseDetected: (PoseResult) -> Unit = {}
) {
    val lifecycleOwner   = LocalLifecycleOwner.current
    val cameraController = remember { CameraController(context) }

    // Shared reference to the SurfaceHolder; written on main thread,
    // read on analysisExecutor — AtomicReference ensures safe visibility.
    val holderRef = remember { AtomicReference<SurfaceHolder?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            cameraController.release()
            // frameProcessor is owned by the ViewModel — do not close here.
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            SurfaceView(ctx).apply {
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        holderRef.set(holder)
                    }
                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        holderRef.set(null)
                    }
                    override fun surfaceChanged(
                        holder: SurfaceHolder, format: Int, width: Int, height: Int
                    ) {}
                })

                cameraController.startCamera(
                    lifecycleOwner  = lifecycleOwner,
                    onFrameAvailable = { imageProxy ->
                        frameProcessor.processFrame(imageProxy) { bitmap, poseResult ->
                            // Render composited bitmap onto the SurfaceView surface.
                            // This runs on analysisExecutor — SurfaceView allows it.
                            val holder = holderRef.get()
                            if (holder != null) {
                                try {
                                    val canvas = holder.lockCanvas()
                                    if (canvas != null) {
                                        // Scale with FILL_CENTER to match display.
                                        val scaleX = canvas.width.toFloat()  / bitmap.width
                                        val scaleY = canvas.height.toFloat() / bitmap.height
                                        val scale  = maxOf(scaleX, scaleY)
                                        val matrix = android.graphics.Matrix().apply {
                                            setScale(scale, scale)
                                            postTranslate(
                                                (canvas.width  - bitmap.width  * scale) / 2f,
                                                (canvas.height - bitmap.height * scale) / 2f
                                            )
                                        }
                                        canvas.drawColor(android.graphics.Color.BLACK)
                                        canvas.drawBitmap(bitmap, matrix, null)
                                        holder.unlockCanvasAndPost(canvas)
                                    }
                                } catch (e: Exception) {
                                    Log.w("CAMERA", "Surface draw skipped: ${e.message}")
                                }
                            }
                            // Notify ViewModel for algorithm processing.
                            onPoseDetected(poseResult)
                        }
                    }
                )
            }
        }
    )
}

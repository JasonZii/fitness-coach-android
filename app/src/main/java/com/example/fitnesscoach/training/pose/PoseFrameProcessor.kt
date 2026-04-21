package com.example.fitnesscoach.training.pose

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import androidx.camera.core.ImageProxy
import com.example.fitnesscoach.core.mediapipe.PoseLandmarkerHelper
import com.example.fitnesscoach.core.mediapipe.PoseResult
import java.io.ByteArrayOutputStream

class PoseFrameProcessor(context: Context) {

    private val appContext = context.applicationContext
    private var poseLandmarkerHelper: PoseLandmarkerHelper? = null
    private var onResultListener: ((PoseResult) -> Unit)? = null

    fun processFrame(
        imageProxy: ImageProxy,
        timestampMs: Long,
        onResult: (PoseResult) -> Unit
    ) {
        try {
            onResultListener = onResult

            if (poseLandmarkerHelper == null) {
                poseLandmarkerHelper = PoseLandmarkerHelper(appContext) { result ->
                    onResultListener?.invoke(result)
                }
            }

            val bitmap = imageProxyToBitmap(imageProxy)
            poseLandmarkerHelper?.detectAsync(bitmap, timestampMs)
        } catch (e: Exception) {
            Log.e("POSE", "Processing failed", e)
            onResult(
                PoseResult(
                    landmarks = emptyList(),
                    visibilities = emptyList()
                )
            )
        } finally {
            imageProxy.close()
        }
    }

    fun close() {
        try {
            poseLandmarkerHelper?.close()
            poseLandmarkerHelper = null
        } catch (e: Exception) {
            Log.e("POSE", "Close failed", e)
        }
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
        val yBuffer = imageProxy.planes[0].buffer
        val uBuffer = imageProxy.planes[1].buffer
        val vBuffer = imageProxy.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(
            nv21,
            ImageFormat.NV21,
            imageProxy.width,
            imageProxy.height,
            null
        )

        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(
            Rect(0, 0, imageProxy.width, imageProxy.height),
            60,
            out
        )

        val imageBytes = out.toByteArray()
        var bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

        val matrix = Matrix()
        matrix.postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
        // Front camera sensor output is unmirrored; flip horizontally so landmark
        // coordinates match the mirrored PreviewView shown to the user.
        matrix.postScale(-1f, 1f)

        bitmap = Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true
        )

        return bitmap
    }
}

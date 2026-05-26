package com.example.fitnesscoach.training.pose

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.util.Log
import androidx.camera.core.ImageProxy
import com.example.fitnesscoach.core.mediapipe.PoseLandmarkerHelper
import com.example.fitnesscoach.core.mediapipe.PoseResult
import com.example.fitnesscoach.core.util.Constants.LANDMARK_COUNT

// Joints to draw (upper-body + lower-body key joints).
private val DRAWN_JOINT_INDICES = setOf(11, 12, 13, 14, 15, 16, 23, 24, 25, 26, 27, 28)

// (start, end) index pairs; (-1, -1) means spine via shoulder/hip midpoints.
private val LIMB_PAIRS = listOf(
    11 to 13, 13 to 15,   // left arm
    12 to 14, 14 to 16,   // right arm
    11 to 23, 12 to 24,   // torso sides
    23 to 25, 24 to 26,   // thighs
    25 to 27, 26 to 28,   // shins
    11 to 12,             // shoulder line
    23 to 24,             // hip line
    -1 to -1              // spine (midpoints)
)

private const val VISIBILITY_THRESHOLD = 0.5f
private const val JOINT_RADIUS_PX = 6f
private const val LIMB_STROKE_PX  = 3.5f
private const val REFERENCE_BLUE_ARGB = 0xE80D47A1.toInt()

class PoseFrameProcessor(context: Context) {

    private val appContext = context.applicationContext
    private var poseLandmarkerHelper: PoseLandmarkerHelper? = null

    // Latest scoring colours and reference landmarks from the algorithm dispatcher (previous frame).
    // Written by TrainingViewModel; read here when drawing on the bitmap.
    // Replacing the reference atomically with @Volatile is sufficient because we never mutate in-place.
    @Volatile private var latestJointArgbColors: IntArray =
        IntArray(33) { android.graphics.Color.GREEN }
    @Volatile private var latestLimbArgbColors: IntArray =
        IntArray(13) { android.graphics.Color.GREEN }
    @Volatile private var latestReferenceLandmarks: List<Triple<Float, Float, Float>> = emptyList()
    @Volatile private var isReferenceSkeletonVisible: Boolean = true

    /**
     * Called by TrainingViewModel (on algorithmDispatcher) after each scoring frame.
     * Updates the reference landmarks and colours used the NEXT time drawSkeletonOnBitmap() runs.
     * Pass [refLandmarks] as emptyList() to suppress the blue skeleton for that frame.
     */
    fun updateReferenceAndColors(
        refLandmarks: List<Triple<Float, Float, Float>>,
        jointArgbColors: IntArray,
        limbArgbColors: IntArray,
    ) {
        latestReferenceLandmarks = refLandmarks
        latestJointArgbColors = jointArgbColors
        latestLimbArgbColors  = limbArgbColors
    }

    /**
     * Called by TrainingViewModel after DTW finds the matching reference frame.
     * The coordinates are already repositioned onto the user's body in normalised
     * image space, so they can be drawn directly on the next composited bitmap.
     */
    fun updateReferenceSkeleton(landmarks: List<Triple<Float, Float, Float>>) {
        latestReferenceLandmarks = landmarks
    }

    fun setReferenceSkeletonVisible(visible: Boolean) {
        isReferenceSkeletonVisible = visible
    }

    /**
     * Synchronous: converts [imageProxy] → Bitmap, runs MediaPipe inference,
     * draws the skeleton onto the Bitmap, then delivers both via [onResult].
     *
     * Joints with visibility < [VISIBILITY_THRESHOLD] are skipped so that
     * occluded side-view joints don't jitter across the screen.
     *
     * The Bitmap is recycled automatically after [onResult] returns — do not
     * store a reference to it beyond the callback.
     */
    fun processFrame(
        imageProxy: ImageProxy,
        onResult: (Bitmap, PoseResult) -> Unit
    ) {
        try {
            if (poseLandmarkerHelper == null) {
                poseLandmarkerHelper = PoseLandmarkerHelper(appContext)
            }
            val bitmap      = imageProxyToBitmap(imageProxy)
            val timestampMs = System.currentTimeMillis()
            val poseResult  = poseLandmarkerHelper!!.detectSync(bitmap, timestampMs)

            if (poseResult.landmarks.size == 33) {
                drawSkeletonOnBitmap(bitmap, poseResult)
            }

            onResult(bitmap, poseResult)
            bitmap.recycle()
        } catch (e: Exception) {
            Log.e("POSE", "Processing failed", e)
            onResult(
                Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888),
                PoseResult(emptyList(), emptyList(), imageWidth = 1, imageHeight = 1)
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

    // ── Skeleton drawing ───────────────────────────────────────────────────────

    private fun drawSkeletonOnBitmap(bitmap: Bitmap, poseResult: PoseResult) {
        val canvas       = Canvas(bitmap)
        val landmarks    = poseResult.landmarks
        val visibilities = poseResult.visibilities
        val jointColors  = latestJointArgbColors
        val limbColors   = latestLimbArgbColors
        val referenceLandmarks = latestReferenceLandmarks
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()

        val limbPaint = Paint().apply {
            isAntiAlias  = true
            style        = Paint.Style.STROKE
            strokeWidth  = LIMB_STROKE_PX
            strokeCap    = Paint.Cap.ROUND
        }
        val jointPaint = Paint().apply {
            isAntiAlias = true
            style       = Paint.Style.FILL
        }

        fun vis(idx: Int) = visibilities.getOrElse(idx) { 0f }
        fun px(idx: Int)  = landmarks[idx].first  * w
        fun py(idx: Int)  = landmarks[idx].second * h

        fun refPx(idx: Int) = referenceLandmarks[idx].first * w
        fun refPy(idx: Int) = referenceLandmarks[idx].second * h

        if (isReferenceSkeletonVisible && referenceLandmarks.size == 33) {
            limbPaint.color = REFERENCE_BLUE_ARGB
            LIMB_PAIRS.forEach { (s, e) ->
                if (s == -1) {
                    if (vis(11) >= VISIBILITY_THRESHOLD && vis(12) >= VISIBILITY_THRESHOLD &&
                        vis(23) >= VISIBILITY_THRESHOLD && vis(24) >= VISIBILITY_THRESHOLD) {
                        canvas.drawLine(
                            (refPx(11) + refPx(12)) / 2f, (refPy(11) + refPy(12)) / 2f,
                            (refPx(23) + refPx(24)) / 2f, (refPy(23) + refPy(24)) / 2f,
                            limbPaint
                        )
                    }
                } else {
                    if (vis(s) >= VISIBILITY_THRESHOLD && vis(e) >= VISIBILITY_THRESHOLD) {
                        canvas.drawLine(refPx(s), refPy(s), refPx(e), refPy(e), limbPaint)
                    }
                }
            }

            jointPaint.color = REFERENCE_BLUE_ARGB
            DRAWN_JOINT_INDICES.forEach { idx ->
                if (vis(idx) >= VISIBILITY_THRESHOLD) {
                    canvas.drawCircle(refPx(idx), refPy(idx), JOINT_RADIUS_PX, jointPaint)
                }
            }
        }

        // Draw limbs first so joints render on top.
        LIMB_PAIRS.forEachIndexed { i, (s, e) ->
            limbPaint.color = limbColors.getOrElse(i) { android.graphics.Color.GREEN }
            if (s == -1) {
                // Spine: midpoint(11,12) → midpoint(23,24)
                if (vis(11) >= VISIBILITY_THRESHOLD && vis(12) >= VISIBILITY_THRESHOLD &&
                    vis(23) >= VISIBILITY_THRESHOLD && vis(24) >= VISIBILITY_THRESHOLD) {
                    canvas.drawLine(
                        (px(11) + px(12)) / 2f, (py(11) + py(12)) / 2f,
                        (px(23) + px(24)) / 2f, (py(23) + py(24)) / 2f,
                        limbPaint
                    )
                }
            } else {
                if (vis(s) >= VISIBILITY_THRESHOLD && vis(e) >= VISIBILITY_THRESHOLD) {
                    canvas.drawLine(px(s), py(s), px(e), py(e), limbPaint)
                }
            }
        }

        // Draw joints.
        DRAWN_JOINT_INDICES.forEach { idx ->
            if (vis(idx) >= VISIBILITY_THRESHOLD) {
                jointPaint.color = jointColors.getOrElse(idx) { android.graphics.Color.GREEN }
                canvas.drawCircle(px(idx), py(idx), JOINT_RADIUS_PX, jointPaint)
            }
        }
    }

    // ── YUV → Bitmap 转换 ────────────────────────────────────────────────

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
        // 【新实现】使用 CameraX 内置扩展函数 toBitmap() 直接将 ImageProxy 转为 ARGB_8888 Bitmap。
        // 无需经过 JPEG 压缩/解码，避免 80% 质量 JPEG 引入的块状量化误差（约 2–4 px），
        // 从而消除 MediaPipe 关键点检测在帧间产生的 1–3 px 级别抖动，同时减少 CPU 和 GC 开销。
        val bitmap = imageProxy.toBitmap()

        // 【旧实现 — 保留备用，如遇设备兼容问题可取消注释以下代码并删除上方 toBitmap() 调用来恢复】
        // 原方案通过 YuvImage + ByteArrayOutputStream 将 YUV 帧压缩为 80% 质量的 JPEG，
        // 再用 BitmapFactory 解码回 Bitmap。这会引入二次有损压缩误差（约 2–4 px 块状伪影），
        // 导致 MediaPipe 关键点检测在帧间产生 1–3 px 级别的抖动。
        // 恢复时还需在文件顶部重新添加以下 import：
        //   import android.graphics.BitmapFactory
        //   import android.graphics.ImageFormat
        //   import android.graphics.Rect
        //   import android.graphics.YuvImage
        //   import java.io.ByteArrayOutputStream
        //
        // val yBuffer = imageProxy.planes[0].buffer
        // val uBuffer = imageProxy.planes[1].buffer
        // val vBuffer = imageProxy.planes[2].buffer
        // val ySize = yBuffer.remaining()
        // val uSize = uBuffer.remaining()
        // val vSize = vBuffer.remaining()
        // val nv21 = ByteArray(ySize + uSize + vSize)
        // yBuffer.get(nv21, 0, ySize)
        // vBuffer.get(nv21, ySize, vSize)
        // uBuffer.get(nv21, ySize + vSize, uSize)
        // val yuvImage = YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
        // val out = ByteArrayOutputStream()
        // yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 80, out)
        // val bitmap = BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())

        val matrix = Matrix()
        matrix.postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
        // 前置摄像头：水平翻转，使坐标与屏幕显示方向一致。
        matrix.postScale(-1f, 1f)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            .also { bitmap.recycle() }
    }
}

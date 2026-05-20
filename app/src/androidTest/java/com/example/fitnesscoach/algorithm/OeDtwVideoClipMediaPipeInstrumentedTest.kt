package com.example.fitnesscoach.algorithm

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import com.example.fitnesscoach.core.mediapipe.PoseLandmarkerHelper
import com.example.fitnesscoach.training.data.parseReferencePoseJson
import com.example.fitnesscoach.training.pose.alignOeDtw
import com.example.fitnesscoach.training.pose.normalizeLandmarks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.runner.RunWith
import kotlin.math.roundToInt

/**
 * End-to-end OE-DTW validation inside the Android app runtime.
 *
 * This test follows the report validation protocol:
 *   full reference video
 *   -> prefix clips at 1/3, 2/3, and full length
 *   -> remove every 5th interior frame from each clip
 *   -> treat the damaged clip frames as a user video
 *   -> re-extract user landmarks with the app's PoseLandmarkerHelper
 *   -> run the app's normalizeLandmarks() and alignOeDtw()
 *   -> compare matchedIndex with the clip's original reference end index.
 *
 * It intentionally does not use the Python OE-DTW implementation. The final
 * matching call is the production Kotlin function used by TrainingViewModel.
 */
@RunWith(AndroidJUnit4::class)
class OeDtwVideoClipMediaPipeInstrumentedTest {

    private data class ExerciseCase(
        val exerciseId: String,
        val rawVideoName: String,
        val landmarkJson: String,
        val downsampleStep: Int,
    )

    private data class ClipResult(
        val exerciseId: String,
        val clipLabel: String,
        val clipAlgorithmRange: String,
        val sourceVideoRange: String,
        val deletedAlgorithmFrames: String,
        val userFrameCount: Int,
        val detectedFrameCount: Int,
        val expectedReferenceIndex: Int,
        val matchedReferenceIndex: Int,
        val indexError: Int,
    )

    private val exerciseCases = listOf(
        ExerciseCase(
            exerciseId = "bicep_curl",
            rawVideoName = "bicep_curl",
            landmarkJson = "bicep_curl.json",
            downsampleStep = 2,
        ),
        ExerciseCase(
            exerciseId = "dumbbell_lateral_raise",
            rawVideoName = "dumbbell_lateral_raise",
            landmarkJson = "dumbbell_lateral_raise.json",
            downsampleStep = 1,
        ),
        ExerciseCase(
            exerciseId = "right_leg_lunge_to_knee_raise",
            rawVideoName = "right_leg_lunge_to_knee_raise",
            landmarkJson = "right_leg_lunge_to_knee_raise.json",
            downsampleStep = 2,
        ),
        ExerciseCase(
            exerciseId = "squat",
            rawVideoName = "squat",
            landmarkJson = "squat.json",
            downsampleStep = 2,
        ),
        ExerciseCase(
            exerciseId = "standing_dumbbell_shoulder_press",
            rawVideoName = "standing_dumbbell_shoulder_press",
            landmarkJson = "standing_dumbbell_shoulder_press.json",
            downsampleStep = 2,
        ),
    )

    @Test
    fun droppedReferenceVideoClipsExtractedByAppMediaPipeAlignWithAppOeDtw() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val results = mutableListOf<ClipResult>()
        printReportHeader()

        exerciseCases.forEach { exercise ->
            val referenceJson = context.assets
                .open("landmarks/${exercise.landmarkJson}")
                .bufferedReader()
                .readText()
            val referenceSequence = parseReferencePoseJson(
                json = referenceJson,
                step = exercise.downsampleStep,
            ).map { normalizeLandmarks(it) }
            val sourceFrameIndices = referenceSourceFrameIndices(
                json = referenceJson,
                step = exercise.downsampleStep,
            )

            prefixEnds(referenceSequence.size).forEach { endExclusive ->
                val result = runClipCase(
                    context = context,
                    exercise = exercise,
                    referenceSequence = referenceSequence,
                    sourceFrameIndices = sourceFrameIndices,
                    endExclusive = endExclusive,
                )
                results += result
                printReportRow(result)
            }
        }

        results.forEach { result ->
            assertEquals(
                "${result.exerciseId}/${result.clipLabel}: every decoded clip frame should produce landmarks",
                result.userFrameCount,
                result.detectedFrameCount,
            )
            assertTrue(
                "${result.exerciseId}/${result.clipLabel}: matchedIndex=${result.matchedReferenceIndex}, " +
                    "expected=${result.expectedReferenceIndex}, error=${result.indexError}",
                kotlin.math.abs(result.indexError) <= MAX_ALLOWED_ERROR_FRAMES,
            )
        }
    }

    private fun runClipCase(
        context: Context,
        exercise: ExerciseCase,
        referenceSequence: List<List<Triple<Float, Float, Float>>>,
        sourceFrameIndices: List<Int>,
        endExclusive: Int,
    ): ClipResult {
        val cleanAlgorithmIndices = 0 until endExclusive
        val keptAlgorithmIndices = cleanAlgorithmIndices.filter { algorithmIndex ->
            algorithmIndex == 0 ||
                algorithmIndex == endExclusive - 1 ||
                algorithmIndex % DROP_EVERY_NTH_FRAME != 0
        }
        val deletedAlgorithmIndices = cleanAlgorithmIndices.filterNot { it in keptAlgorithmIndices }
        val keptSourceFrameIndices = keptAlgorithmIndices.map { sourceFrameIndices[it] }
        val expectedIndex = keptAlgorithmIndices.last()
        val userSequence = mutableListOf<List<Triple<Float, Float, Float>>>()

        val poseLandmarker = PoseLandmarkerHelper(context)
        try {
            withVideoRetriever(context, exercise.rawVideoName) { retriever, fps ->
                val clipFps = fps / exercise.downsampleStep
                keptSourceFrameIndices.forEachIndexed { localFrameIndex, sourceFrameIndex ->
                    val bitmap = retriever.frameAtSourceIndex(sourceFrameIndex, fps)
                    val timestampMs = ((localFrameIndex * 1000f) / clipFps).roundToInt().toLong()
                    val poseResult = poseLandmarker.detectSync(bitmap, timestampMs)
                    if (poseResult.landmarks.isNotEmpty()) {
                        userSequence += normalizeLandmarks(poseResult.landmarks)
                    }
                    bitmap.recycle()
                }
            }
        } finally {
            poseLandmarker.close()
        }

        val matchedIndex = alignOeDtw(userSequence, referenceSequence)

        return ClipResult(
            exerciseId = exercise.exerciseId,
            clipLabel = "prefix_${endExclusive.toString().padStart(4, '0')}_drop_every_$DROP_EVERY_NTH_FRAME",
            clipAlgorithmRange = "0-${endExclusive - 1}",
            sourceVideoRange = "0-${keptSourceFrameIndices.last()}",
            deletedAlgorithmFrames = deletedAlgorithmIndices.joinToString(";"),
            userFrameCount = keptAlgorithmIndices.size,
            detectedFrameCount = userSequence.size,
            expectedReferenceIndex = expectedIndex,
            matchedReferenceIndex = matchedIndex,
            indexError = matchedIndex - expectedIndex,
        )
    }

    private fun referenceSourceFrameIndices(json: String, step: Int): List<Int> {
        val frames = org.json.JSONObject(json).getJSONArray("frames")
        return (0 until frames.length() step step).map { index ->
            frames.getJSONObject(index).optInt("frame_index", index)
        }
    }

    private fun prefixEnds(referenceLength: Int): List<Int> =
        listOf(
            maxOf(20, referenceLength / 3),
            maxOf(20, referenceLength * 2 / 3),
            referenceLength,
        ).map { it.coerceAtMost(referenceLength) }
            .filter { it >= 15 }
            .distinct()

    private fun withVideoRetriever(
        context: Context,
        rawVideoName: String,
        block: (MediaMetadataRetriever, Float) -> Unit,
    ) {
        val retriever = MediaMetadataRetriever()
        val rawVideoResId = context.resources.getIdentifier(rawVideoName, "raw", context.packageName)
        require(rawVideoResId != 0) { "Missing raw video resource: $rawVideoName" }
        val afd = context.resources.openRawResourceFd(rawVideoResId)
        try {
            retriever.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            val fps = retriever.estimatedFrameRate()
            block(retriever, fps)
        } finally {
            afd.close()
            retriever.release()
        }
    }

    private fun MediaMetadataRetriever.estimatedFrameRate(): Float {
        val frameCount = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)
            ?.toFloatOrNull()
            ?.takeIf { it > 0f }
        val durationMs = extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toFloatOrNull()
            ?.takeIf { it > 0f }
        if (frameCount != null && durationMs != null) {
            return frameCount * 1000f / durationMs
        }

        return extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
            ?.toFloatOrNull()
            ?.takeIf { it > 0f }
            ?: DEFAULT_SOURCE_FPS
    }

    private fun MediaMetadataRetriever.frameAtSourceIndex(sourceFrameIndex: Int, fps: Float): Bitmap {
        val timeUs = ((sourceFrameIndex * 1_000_000f) / fps).roundToInt().toLong()
        return getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
            ?: error("Could not decode source frame $sourceFrameIndex at ${timeUs}us")
    }

    private fun printReportHeader() {
        println(
            listOf(
                "exercise",
                "clip",
                "clip_algorithm_range",
                "source_video_range",
                "deleted_algorithm_frames",
                "user_frames",
                "mediapipe_detected",
                "expected_index",
                "matched_index",
                "index_error",
                "result",
            ).joinToString("|"),
        )
    }

    private fun printReportRow(result: ClipResult) {
        println(
            listOf(
                result.exerciseId,
                result.clipLabel,
                result.clipAlgorithmRange,
                result.sourceVideoRange,
                result.deletedAlgorithmFrames,
                result.userFrameCount,
                "${result.detectedFrameCount}/${result.userFrameCount}",
                result.expectedReferenceIndex,
                result.matchedReferenceIndex,
                "%+d".format(result.indexError),
                if (kotlin.math.abs(result.indexError) <= MAX_ALLOWED_ERROR_FRAMES) "PASS" else "FAIL",
            ).joinToString("|"),
        )
    }

    private companion object {
        private const val DROP_EVERY_NTH_FRAME = 5
        private const val MAX_ALLOWED_ERROR_FRAMES = 4
        private const val DEFAULT_SOURCE_FPS = 30f
    }
}

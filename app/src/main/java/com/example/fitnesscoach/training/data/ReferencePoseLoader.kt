package com.example.fitnesscoach.training.data

import android.content.Context
import com.example.fitnesscoach.core.util.Constants.REFERENCE_POSE_DOWNSAMPLE_STEP
import org.json.JSONObject

/**
 * Parses a raw JSON string (landmarks file from assets/) into a downsampled
 * sequence of raw MediaPipe frames.
 *
 * JSON format (ALGORITHM.md §Standard Action Data):
 * ```json
 * { "frames": [ { "landmarks": [ {"x":…, "y":…, "z":…}, … ] }, … ] }
 * ```
 *
 * Reference files are recorded at 60 FPS; the device analyses at ~15–30 FPS.
 * [step] sub-samples the reference by keeping only every Nth frame, bringing it
 * to ~30 FPS and preventing the "one-to-many" DTW stretch that wastes CPU and
 * causes blue-skeleton lag on screen.
 *
 * @param json Full text of a landmarks JSON file.
 * @param step Keep 1 frame every [step] frames (default [REFERENCE_POSE_DOWNSAMPLE_STEP] = 2).
 * @return Downsampled list of frames; each frame is a list of 33 raw (x, y, z)
 *         triples. Index matches the MediaPipe BlazePose landmark index (0–32).
 */
fun parseReferencePoseJson(
    json: String,
    step: Int = REFERENCE_POSE_DOWNSAMPLE_STEP,
): List<List<Triple<Float, Float, Float>>> {
    val framesArray = JSONObject(json).getJSONArray("frames")
    return (0 until framesArray.length() step step).map { fi ->
        val landmarksArray = framesArray.getJSONObject(fi).getJSONArray("landmarks")
        List(landmarksArray.length()) { li ->
            val lm = landmarksArray.getJSONObject(li)
            Triple(
                lm.getDouble("x").toFloat(),
                lm.getDouble("y").toFloat(),
                lm.getDouble("z").toFloat()
            )
        }
    }
}

/**
 * Files already recorded at ~30 FPS that must NOT be downsampled.
 * All other files are assumed to be 60 FPS and use [REFERENCE_POSE_DOWNSAMPLE_STEP].
 */
private val ALREADY_30FPS = setOf("dumbbell_lateral_raise.json")

private fun stepFor(assetFileName: String): Int =
    if (assetFileName in ALREADY_30FPS) 1 else REFERENCE_POSE_DOWNSAMPLE_STEP

/**
 * Loads a standard-action landmarks JSON from assets/ and returns the raw,
 * unnormalised downsampled frame sequence.
 *
 * Use this when you need the original MediaPipe coordinates, e.g. to render a
 * "ghost skeleton" overlay showing the target pose on the camera preview.
 *
 * @param context       Android Context used to open the asset file.
 * @param assetFileName File name inside assets/landmarks/, e.g. "squat.json".
 * @return Raw reference sequence: outer index = frame, inner index = landmark
 *         (0–32), value = raw (x, y, z) triple as output by MediaPipe.
 */
fun loadRawReferenceSequence(
    context: Context,
    assetFileName: String,
): List<List<Triple<Float, Float, Float>>> {
    val json = context.assets
        .open("landmarks/$assetFileName")
        .bufferedReader()
        .readText()
    return parseReferencePoseJson(json, stepFor(assetFileName))
}


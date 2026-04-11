package com.example.fitnesscoach.training.data

import android.content.Context
import com.example.fitnesscoach.training.pose.normalizeLandmarks
import org.json.JSONObject

/**
 * Parses a raw JSON string (landmarks file from assets/) into a sequence of
 * raw MediaPipe frames. Pure function — no Android dependencies.
 *
 * JSON format (ALGORITHM.md §Standard Action Data):
 * ```json
 * { "frames": [ { "landmarks": [ {"x":…, "y":…, "z":…}, … ] }, … ] }
 * ```
 *
 * @param json Full text of a landmarks JSON file.
 * @return List of frames; each frame is a list of 33 raw (x, y, z) triples.
 *         Index matches the MediaPipe BlazePose landmark index (0–32).
 */
fun parseReferencePoseJson(json: String): List<List<Triple<Float, Float, Float>>> {
    val framesArray = JSONObject(json).getJSONArray("frames")
    return List(framesArray.length()) { fi ->
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
 * Loads a standard-action landmarks JSON from assets/ and returns the raw,
 * unnormalised frame sequence.
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
    assetFileName: String
): List<List<Triple<Float, Float, Float>>> {
    val json = context.assets
        .open("landmarks/$assetFileName")
        .bufferedReader()
        .readText()
    return parseReferencePoseJson(json)
}

/**
 * Loads a standard-action landmarks JSON from assets/, normalises every frame
 * with [normalizeLandmarks], and returns the result ready for [alignOeDtw].
 *
 * Called once at training startup per ALGORITHM.md §Module 1 (Context 1).
 *
 * @param context       Android Context used to open the asset file.
 * @param assetFileName File name inside assets/landmarks/, e.g. "squat.json".
 * @return Normalised reference sequence: outer index = frame, inner index =
 *         landmark (0–32), value = normalised (x, y) pair.
 */
fun loadReferenceSequence(
    context: Context,
    assetFileName: String
): List<List<Pair<Float, Float>>> {
    val json = context.assets
        .open("landmarks/$assetFileName")
        .bufferedReader()
        .readText()
    return parseReferencePoseJson(json).map { normalizeLandmarks(it) }
}

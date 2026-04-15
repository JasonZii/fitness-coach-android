package com.example.fitnesscoach.training.data

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.acos
import kotlin.math.sqrt

/**
 * Diagnostic test: compute the right knee angle (right hip → right knee → right ankle)
 * for every frame in squat.json and print the results.
 *
 * Angle definition: the interior angle at the vertex joint, formed by the two limb
 * vectors (hip→knee) and (ankle→knee), computed in 2-D (x, y) coordinates.
 * 180° = fully extended leg; smaller values indicate greater knee flexion.
 *
 * Runs on the JVM without any Android dependencies:
 *   - JSON loaded via ClassLoader.getResourceAsStream() (assets/ is on the test classpath
 *     because build.gradle.kts sets resources.srcDirs("src/main/assets"))
 *   - Parsing via parseReferencePoseJson() which uses org.json:json (testImplementation)
 */
class SquatKneeAngleTest {

    // MediaPipe BlazePose landmark indices (ALGORITHM.md / Constants.kt)
    private val RIGHT_HIP   = 24
    private val RIGHT_KNEE  = 26
    private val RIGHT_ANKLE = 28

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun loadSquatJson(): String =
        javaClass.classLoader!!
            .getResourceAsStream("landmarks/squat.json")!!
            .bufferedReader()
            .readText()

    /**
     * Interior angle (degrees) at [vertex], formed by the vectors
     * ([start] → [vertex]) and ([end] → [vertex]), using only the x and y
     * components (z is discarded, consistent with the rest of the pipeline).
     *
     * Returns null when either limb vector has near-zero length.
     */
    private fun kneeAngle2d(
        start:  Triple<Float, Float, Float>,
        vertex: Triple<Float, Float, Float>,
        end:    Triple<Float, Float, Float>,
    ): Float? {
        val v1x = start.first  - vertex.first
        val v1y = start.second - vertex.second
        val v2x = end.first    - vertex.first
        val v2y = end.second   - vertex.second

        val mag1 = sqrt(v1x * v1x + v1y * v1y)
        val mag2 = sqrt(v2x * v2x + v2y * v2y)
        if (mag1 < 1e-6f || mag2 < 1e-6f) return null

        val cosine = ((v1x * v2x + v1y * v2y) / (mag1 * mag2)).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(cosine.toDouble())).toFloat()
    }

    // ── Test ──────────────────────────────────────────────────────────────────

    @Test
    fun squatJson_rightKneeAngle_perFrame() {
        val frames = parseReferencePoseJson(loadSquatJson())

        println()
        println("=== squat.json — right knee angle (hip→knee←ankle, 2-D) ===")
        println("%-8s  %s".format("Frame", "Right knee angle (°)"))
        println("-".repeat(38))

        var minAngle = Float.MAX_VALUE
        var maxAngle = -Float.MAX_VALUE
        var degenerateCount = 0

        frames.forEachIndexed { frameIndex, landmarks ->
            val angle = kneeAngle2d(
                start  = landmarks[RIGHT_HIP],
                vertex = landmarks[RIGHT_KNEE],
                end    = landmarks[RIGHT_ANKLE],
            )
            if (angle != null) {
                println("%-8d  %6.1f°".format(frameIndex, angle))
                if (angle < minAngle) minAngle = angle
                if (angle > maxAngle) maxAngle = angle
            } else {
                println("%-8d  (degenerate — zero-length limb vector)".format(frameIndex))
                degenerateCount++
            }
        }

        println("-".repeat(38))
        if (minAngle <= maxAngle) {
            println("Min : %6.1f°  (maximum knee flexion)".format(minAngle))
            println("Max : %6.1f°  (leg most extended)".format(maxAngle))
            println("Range: %.1f°".format(maxAngle - minAngle))
        }
        println("Degenerate frames: $degenerateCount / ${frames.size}")
        println()

        // ── Sanity assertions ─────────────────────────────────────────────────
        // All valid angles must be in [0°, 180°].
        assertTrue(
            "All right knee angles must be ≥ 0°  (got min $minAngle°)",
            minAngle >= 0f,
        )
        assertTrue(
            "All right knee angles must be ≤ 180° (got max $maxAngle°)",
            maxAngle <= 180f,
        )
        // A squat recording should show meaningful flexion: peak flexion below 130°
        // and standing extension above 150°.
        assertTrue(
            "Squat data should contain at least one frame with knee angle < 130° " +
            "(actual min: $minAngle°)",
            minAngle < 130f,
        )
        assertTrue(
            "Squat data should contain at least one frame with knee angle > 150° " +
            "(actual max: $maxAngle°)",
            maxAngle > 150f,
        )
    }
}

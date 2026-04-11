package com.example.fitnesscoach.training.data

import com.example.fitnesscoach.core.util.Constants.LANDMARK_COUNT
import com.example.fitnesscoach.core.util.Constants.NORMALISE_EPSILON
import com.example.fitnesscoach.training.pose.normalizeLandmarks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Unit tests for [parseReferencePoseJson] and the parse → normalise pipeline.
 *
 * Tests covering:
 *  1. Frame count matches JSON
 *  2. Landmark count is always 33 per frame
 *  3. x/y/z values are parsed without loss
 *  4. After normalization: hip midpoint == (0,0) within NORMALISE_EPSILON
 *  5. After normalization: torso length == 1.0 within NORMALISE_EPSILON
 *  6. Output has exactly 33 Pair<Float,Float> elements per frame (DRY with NormalizeLandmarksTest)
 *  7. squat.json: correct total frame count (159)
 *  8. squat.json: every frame has exactly 33 landmarks
 *  9. squat.json: every frame passes normalization invariants
 * 10. squat.json: z values are discarded (output is List<Pair>, not List<Triple>)
 */
class ReferencePoseLoaderTest {

    // ── Minimal inline JSON used by tests 1–6 ────────────────────────────────

    /**
     * Builds a minimal valid JSON string with [frameCount] frames.
     * Each landmark in each frame is set to the given (x, y, z) values
     * EXCEPT landmarks 11, 12, 23, 24 which are given distinct positions so
     * that normalizeLandmarks() can compute a non-zero torso length.
     */
    private fun buildMinimalJson(
        frameCount: Int,
        defaultX: Float = 0.5f,
        defaultY: Float = 0.5f,
        defaultZ: Float = 0.0f
    ): String {
        val frames = (0 until frameCount).joinToString(",") { fi ->
            val landmarks = (0 until 33).joinToString(",") { li ->
                // Give shoulder and hip landmarks distinct positions so T > 0
                val (x, y, z) = when (li) {
                    11 -> Triple(0.4f, 0.3f, 0.0f)   // left shoulder
                    12 -> Triple(0.6f, 0.3f, 0.0f)   // right shoulder
                    23 -> Triple(0.4f, 0.6f, 0.0f)   // left hip
                    24 -> Triple(0.6f, 0.6f, 0.0f)   // right hip
                    else -> Triple(defaultX, defaultY, defaultZ)
                }
                """{"x":$x,"y":$y,"z":$z}"""
            }
            """{"frame_index":$fi,"landmarks":[$landmarks]}"""
        }
        return """{"frames":[$frames]}"""
    }

    // ── Test 1: Frame count ───────────────────────────────────────────────────

    @Test
    fun parseReferencePoseJson_threeFrames_returnsThreeFrames() {
        val json = buildMinimalJson(frameCount = 3)
        val result = parseReferencePoseJson(json)
        assertEquals(3, result.size)
    }

    @Test
    fun parseReferencePoseJson_oneFrame_returnsOneFrame() {
        val json = buildMinimalJson(frameCount = 1)
        val result = parseReferencePoseJson(json)
        assertEquals(1, result.size)
    }

    // ── Test 2: Landmark count per frame ─────────────────────────────────────

    @Test
    fun parseReferencePoseJson_eachFrame_has33Landmarks() {
        val json = buildMinimalJson(frameCount = 5)
        val result = parseReferencePoseJson(json)
        result.forEachIndexed { fi, frame ->
            assertEquals("Frame $fi should have 33 landmarks", LANDMARK_COUNT, frame.size)
        }
    }

    // ── Test 3: Value fidelity ────────────────────────────────────────────────

    @Test
    fun parseReferencePoseJson_landmarkValues_matchJson() {
        val json = buildMinimalJson(frameCount = 1)
        val frame = parseReferencePoseJson(json)[0]

        // Shoulder 11 was set to (0.4, 0.3, 0.0)
        assertEquals(0.4f, frame[11].first,  0.0001f)
        assertEquals(0.3f, frame[11].second, 0.0001f)
        assertEquals(0.0f, frame[11].third,  0.0001f)

        // Default landmark 0 was set to (0.5, 0.5, 0.0)
        assertEquals(0.5f, frame[0].first,  0.0001f)
        assertEquals(0.5f, frame[0].second, 0.0001f)
    }

    // ── Tests 4–6: Normalisation invariants ──────────────────────────────────

    @Test
    fun parseAndNormalize_hipMidpoint_isOrigin() {
        val json = buildMinimalJson(frameCount = 1)
        val raw = parseReferencePoseJson(json)[0]
        val normalized = normalizeLandmarks(raw)

        val hipMidX = (normalized[23].first  + normalized[24].first)  / 2f
        val hipMidY = (normalized[23].second + normalized[24].second) / 2f

        assertTrue(
            "Hip midpoint X should be 0.0 ± $NORMALISE_EPSILON (was $hipMidX)",
            abs(hipMidX) < NORMALISE_EPSILON
        )
        assertTrue(
            "Hip midpoint Y should be 0.0 ± $NORMALISE_EPSILON (was $hipMidY)",
            abs(hipMidY) < NORMALISE_EPSILON
        )
    }

    @Test
    fun parseAndNormalize_torsoLength_isOne() {
        val json = buildMinimalJson(frameCount = 1)
        val raw = parseReferencePoseJson(json)[0]
        val normalized = normalizeLandmarks(raw)

        val shMidX = (normalized[11].first  + normalized[12].first)  / 2f
        val shMidY = (normalized[11].second + normalized[12].second) / 2f
        val hipMidX = (normalized[23].first  + normalized[24].first)  / 2f
        val hipMidY = (normalized[23].second + normalized[24].second) / 2f
        val torsoLength = sqrt(
            (shMidX - hipMidX) * (shMidX - hipMidX) +
            (shMidY - hipMidY) * (shMidY - hipMidY)
        )

        assertTrue(
            "Torso length should be 1.0 ± $NORMALISE_EPSILON (was $torsoLength)",
            abs(torsoLength - 1f) < NORMALISE_EPSILON
        )
    }

    @Test
    fun parseAndNormalize_outputSize_is33Pairs() {
        val json = buildMinimalJson(frameCount = 1)
        val raw = parseReferencePoseJson(json)[0]
        val normalized = normalizeLandmarks(raw)
        assertEquals(LANDMARK_COUNT, normalized.size)
    }

    // ── Tests 7–10: squat.json integration ───────────────────────────────────

    private fun loadSquatJson(): String {
        return javaClass.classLoader!!
            .getResourceAsStream("landmarks/squat.json")!!
            .bufferedReader()
            .readText()
    }

    @Test
    fun squatJson_parse_correctFrameCount() {
        val result = parseReferencePoseJson(loadSquatJson())
        assertEquals("squat.json should have 159 frames", 159, result.size)
    }

    @Test
    fun squatJson_parse_everyFrameHas33Landmarks() {
        val result = parseReferencePoseJson(loadSquatJson())
        result.forEachIndexed { fi, frame ->
            assertEquals(
                "squat.json frame $fi should have 33 landmarks",
                LANDMARK_COUNT,
                frame.size
            )
        }
    }

    @Test
    fun squatJson_normalize_everyFrame_hipMidpointIsOrigin() {
        val frames = parseReferencePoseJson(loadSquatJson())
        frames.forEachIndexed { fi, raw ->
            val normalized = normalizeLandmarks(raw)
            val hipMidX = (normalized[23].first  + normalized[24].first)  / 2f
            val hipMidY = (normalized[23].second + normalized[24].second) / 2f
            assertTrue(
                "squat.json frame $fi hip midpoint X should be ~0 (was $hipMidX)",
                abs(hipMidX) < NORMALISE_EPSILON
            )
            assertTrue(
                "squat.json frame $fi hip midpoint Y should be ~0 (was $hipMidY)",
                abs(hipMidY) < NORMALISE_EPSILON
            )
        }
    }

    @Test
    fun squatJson_normalize_everyFrame_torsoLengthIsOne() {
        val frames = parseReferencePoseJson(loadSquatJson())
        frames.forEachIndexed { fi, raw ->
            val normalized = normalizeLandmarks(raw)
            val shMidX = (normalized[11].first  + normalized[12].first)  / 2f
            val shMidY = (normalized[11].second + normalized[12].second) / 2f
            val hipMidX = (normalized[23].first  + normalized[24].first)  / 2f
            val hipMidY = (normalized[23].second + normalized[24].second) / 2f
            val torsoLen = sqrt(
                (shMidX - hipMidX) * (shMidX - hipMidX) +
                (shMidY - hipMidY) * (shMidY - hipMidY)
            )
            assertTrue(
                "squat.json frame $fi torso length should be ~1.0 (was $torsoLen)",
                abs(torsoLen - 1f) < NORMALISE_EPSILON
            )
        }
    }

    @Test
    fun squatJson_normalize_outputIsListOfPairs_notTriples() {
        val frames = parseReferencePoseJson(loadSquatJson())
        // normalizeLandmarks returns List<Pair<Float,Float>> — z is dropped
        val normalized = normalizeLandmarks(frames[0])
        assertEquals(LANDMARK_COUNT, normalized.size)
        // Compile-time check: type is Pair, confirming z is discarded
        val first: Pair<Float, Float> = normalized[0]
        assertEquals(2, listOf(first.first, first.second).size)
    }
}

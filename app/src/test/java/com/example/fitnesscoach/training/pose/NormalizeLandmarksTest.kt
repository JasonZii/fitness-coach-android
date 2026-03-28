package com.example.fitnesscoach.training.pose

import com.example.fitnesscoach.core.util.Constants.LANDMARK_COUNT
import com.example.fitnesscoach.core.util.Constants.LANDMARK_LEFT_HIP
import com.example.fitnesscoach.core.util.Constants.LANDMARK_LEFT_SHOULDER
import com.example.fitnesscoach.core.util.Constants.LANDMARK_RIGHT_HIP
import com.example.fitnesscoach.core.util.Constants.LANDMARK_RIGHT_SHOULDER
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Unit tests for [normalizeLandmarks].
 *
 * Acceptance criteria (ALGORITHM.md Module 1):
 *   AC-1  Hip midpoint of the output equals (0.0, 0.0), error < 0.001
 *   AC-2  Torso length of the output equals 1.0, error < 0.001
 *   AC-3  Output has exactly 33 Pairs
 */
class NormalizeLandmarksTest {

    // ── helpers ──────────────────────────────────────────────────────────────

    /**
     * Build a minimal 33-landmark frame.
     * All points are at the origin by default; callers override specific indices.
     */
    private fun buildFrame(
        overrides: Map<Int, Triple<Float, Float, Float>> = emptyMap()
    ): List<Triple<Float, Float, Float>> =
        List(LANDMARK_COUNT) { i -> overrides[i] ?: Triple(0f, 0f, 0f) }

    /** Euclidean distance helper for 2-D pairs. */
    private fun dist(a: Pair<Float, Float>, b: Pair<Float, Float>): Float {
        val dx = a.first - b.first
        val dy = a.second - b.second
        return sqrt(dx * dx + dy * dy)
    }

    private val eps = 0.001f

    // ── AC-3: output size ────────────────────────────────────────────────────

    @Test
    fun outputHasExactly33Pairs() {
        val frame = buildFrame(
            mapOf(
                LANDMARK_LEFT_SHOULDER  to Triple(0.3f, 0.2f, 0f),
                LANDMARK_RIGHT_SHOULDER to Triple(0.7f, 0.2f, 0f),
                LANDMARK_LEFT_HIP       to Triple(0.3f, 0.6f, 0f),
                LANDMARK_RIGHT_HIP      to Triple(0.7f, 0.6f, 0f),
            )
        )
        val result = normalizeLandmarks(frame)
        assertEquals("Output must contain exactly 33 pairs", LANDMARK_COUNT, result.size)
    }

    // ── AC-1: hip midpoint = (0, 0) ──────────────────────────────────────────

    @Test
    fun hipMidpointIsOriginAfterNormalisation() {
        val frame = buildFrame(
            mapOf(
                LANDMARK_LEFT_SHOULDER  to Triple(0.4f, 0.1f, 0.0f),
                LANDMARK_RIGHT_SHOULDER to Triple(0.6f, 0.1f, 0.0f),
                LANDMARK_LEFT_HIP       to Triple(0.4f, 0.5f, 0.0f),
                LANDMARK_RIGHT_HIP      to Triple(0.6f, 0.5f, 0.0f),
            )
        )
        val result = normalizeLandmarks(frame)

        val hipMidX = (result[LANDMARK_LEFT_HIP].first  + result[LANDMARK_RIGHT_HIP].first)  / 2f
        val hipMidY = (result[LANDMARK_LEFT_HIP].second + result[LANDMARK_RIGHT_HIP].second) / 2f

        assertTrue(
            "Normalised hip-mid x must be ~0.0 (got $hipMidX)",
            abs(hipMidX) < eps
        )
        assertTrue(
            "Normalised hip-mid y must be ~0.0 (got $hipMidY)",
            abs(hipMidY) < eps
        )
    }

    @Test
    fun hipMidpointIsOriginWithAsymmetricPose() {
        // Person is not centred in the frame
        val frame = buildFrame(
            mapOf(
                LANDMARK_LEFT_SHOULDER  to Triple(0.1f, 0.05f, 0f),
                LANDMARK_RIGHT_SHOULDER to Triple(0.3f, 0.05f, 0f),
                LANDMARK_LEFT_HIP       to Triple(0.1f, 0.35f, 0f),
                LANDMARK_RIGHT_HIP      to Triple(0.3f, 0.35f, 0f),
            )
        )
        val result = normalizeLandmarks(frame)

        val hipMidX = (result[LANDMARK_LEFT_HIP].first  + result[LANDMARK_RIGHT_HIP].first)  / 2f
        val hipMidY = (result[LANDMARK_LEFT_HIP].second + result[LANDMARK_RIGHT_HIP].second) / 2f

        assertTrue("Hip-mid x must be ~0 (got $hipMidX)", abs(hipMidX) < eps)
        assertTrue("Hip-mid y must be ~0 (got $hipMidY)", abs(hipMidY) < eps)
    }

    // ── AC-2: torso length = 1.0 ─────────────────────────────────────────────

    @Test
    fun torsoLengthIsOneAfterNormalisation() {
        val frame = buildFrame(
            mapOf(
                LANDMARK_LEFT_SHOULDER  to Triple(0.4f, 0.1f, 0.0f),
                LANDMARK_RIGHT_SHOULDER to Triple(0.6f, 0.1f, 0.0f),
                LANDMARK_LEFT_HIP       to Triple(0.4f, 0.5f, 0.0f),
                LANDMARK_RIGHT_HIP      to Triple(0.6f, 0.5f, 0.0f),
            )
        )
        val result = normalizeLandmarks(frame)

        val shoulderMidX = (result[LANDMARK_LEFT_SHOULDER].first  + result[LANDMARK_RIGHT_SHOULDER].first)  / 2f
        val shoulderMidY = (result[LANDMARK_LEFT_SHOULDER].second + result[LANDMARK_RIGHT_SHOULDER].second) / 2f
        val hipMidX      = (result[LANDMARK_LEFT_HIP].first       + result[LANDMARK_RIGHT_HIP].first)       / 2f
        val hipMidY      = (result[LANDMARK_LEFT_HIP].second      + result[LANDMARK_RIGHT_HIP].second)      / 2f

        val torsoLength = dist(
            Pair(shoulderMidX, shoulderMidY),
            Pair(hipMidX,      hipMidY)
        )
        assertTrue(
            "Normalised torso length must be ~1.0 (got $torsoLength)",
            abs(torsoLength - 1f) < eps
        )
    }

    @Test
    fun torsoLengthIsOneWithDifferentBodyProportions() {
        // Simulate a taller person (longer torso) — result should still be 1.0
        val frame = buildFrame(
            mapOf(
                LANDMARK_LEFT_SHOULDER  to Triple(0.45f, 0.10f, 0f),
                LANDMARK_RIGHT_SHOULDER to Triple(0.55f, 0.10f, 0f),
                LANDMARK_LEFT_HIP       to Triple(0.45f, 0.70f, 0f),  // long torso
                LANDMARK_RIGHT_HIP      to Triple(0.55f, 0.70f, 0f),
            )
        )
        val result = normalizeLandmarks(frame)

        val shoulderMidX = (result[LANDMARK_LEFT_SHOULDER].first  + result[LANDMARK_RIGHT_SHOULDER].first)  / 2f
        val shoulderMidY = (result[LANDMARK_LEFT_SHOULDER].second + result[LANDMARK_RIGHT_SHOULDER].second) / 2f
        val hipMidX      = (result[LANDMARK_LEFT_HIP].first       + result[LANDMARK_RIGHT_HIP].first)       / 2f
        val hipMidY      = (result[LANDMARK_LEFT_HIP].second      + result[LANDMARK_RIGHT_HIP].second)      / 2f

        val torsoLength = dist(
            Pair(shoulderMidX, shoulderMidY),
            Pair(hipMidX,      hipMidY)
        )
        assertTrue(
            "Torso length must be ~1.0 regardless of body size (got $torsoLength)",
            abs(torsoLength - 1f) < eps
        )
    }

    // ── All three criteria together ──────────────────────────────────────────

    @Test
    fun allAcceptanceCriteriaPassSimultaneously() {
        val frame = buildFrame(
            mapOf(
                LANDMARK_LEFT_SHOULDER  to Triple(0.35f, 0.15f, 0.02f),
                LANDMARK_RIGHT_SHOULDER to Triple(0.65f, 0.15f, 0.02f),
                LANDMARK_LEFT_HIP       to Triple(0.38f, 0.55f, 0.01f),
                LANDMARK_RIGHT_HIP      to Triple(0.62f, 0.55f, 0.01f),
            )
        )
        val result = normalizeLandmarks(frame)

        // AC-3
        assertEquals(LANDMARK_COUNT, result.size)

        // AC-1
        val hipMidX = (result[LANDMARK_LEFT_HIP].first  + result[LANDMARK_RIGHT_HIP].first)  / 2f
        val hipMidY = (result[LANDMARK_LEFT_HIP].second + result[LANDMARK_RIGHT_HIP].second) / 2f
        assertTrue(abs(hipMidX) < eps)
        assertTrue(abs(hipMidY) < eps)

        // AC-2
        val shoulderMidX = (result[LANDMARK_LEFT_SHOULDER].first  + result[LANDMARK_RIGHT_SHOULDER].first)  / 2f
        val shoulderMidY = (result[LANDMARK_LEFT_SHOULDER].second + result[LANDMARK_RIGHT_SHOULDER].second) / 2f
        val torsoLength  = dist(
            Pair(shoulderMidX, shoulderMidY),
            Pair(hipMidX,      hipMidY)
        )
        assertTrue(abs(torsoLength - 1f) < eps)
    }

    // ── z is dropped (not preserved in output) ───────────────────────────────

    @Test
    fun zCoordinateIsDiscarded() {
        // Two frames identical in (x, y) but with different z values must produce identical output
        val frameA = buildFrame(
            mapOf(
                LANDMARK_LEFT_SHOULDER  to Triple(0.4f, 0.1f, 0.99f),
                LANDMARK_RIGHT_SHOULDER to Triple(0.6f, 0.1f, 0.99f),
                LANDMARK_LEFT_HIP       to Triple(0.4f, 0.5f, 0.99f),
                LANDMARK_RIGHT_HIP      to Triple(0.6f, 0.5f, 0.99f),
            )
        )
        val frameB = buildFrame(
            mapOf(
                LANDMARK_LEFT_SHOULDER  to Triple(0.4f, 0.1f, -0.99f),
                LANDMARK_RIGHT_SHOULDER to Triple(0.6f, 0.1f, -0.99f),
                LANDMARK_LEFT_HIP       to Triple(0.4f, 0.5f, -0.99f),
                LANDMARK_RIGHT_HIP      to Triple(0.6f, 0.5f, -0.99f),
            )
        )
        val resultA = normalizeLandmarks(frameA)
        val resultB = normalizeLandmarks(frameB)

        resultA.zip(resultB).forEachIndexed { i, (a, b) ->
            assertEquals("x at index $i should be equal", a.first,  b.first,  0f)
            assertEquals("y at index $i should be equal", a.second, b.second, 0f)
        }
    }
}

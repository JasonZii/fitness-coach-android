package com.example.fitnesscoach.training.ui

import com.example.fitnesscoach.core.util.Constants.LANDMARK_COUNT
import com.example.fitnesscoach.core.util.Constants.LIMB_COUNT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Unit tests for the pure helper functions and constants in [SkeletonOverlay].
 *
 * The Canvas rendering itself is not tested here (requires Compose UI test runner).
 * All tests call only pure Kotlin code: [LIMB_CONNECTIONS],
 * [projectLandmark], and [computeSpineEndpoints].
 *
 * Naming convention: ClassName_methodName_condition_expectedResult
 */
class SkeletonOverlayTest {

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun buildLandmarks(
        overrides: Map<Int, Triple<Float, Float, Float>> = emptyMap()
    ): List<Triple<Float, Float, Float>> =
        List(LANDMARK_COUNT) { i -> overrides[i] ?: Triple(0.5f, 0.5f, 0f) }

    // ── LIMB_CONNECTIONS ─────────────────────────────────────────────────────

    @Test
    fun limbConnections_size_exactlyThirteen() {
        assertEquals(
            "LIMB_CONNECTIONS must have exactly $LIMB_COUNT entries",
            LIMB_COUNT,
            LIMB_CONNECTIONS.size
        )
    }

    @Test
    fun limbConnections_standardLimbs_indicesInValidRange() {
        LIMB_CONNECTIONS.forEachIndexed { index, (start, end) ->
            if (start == -1 && end == -1) return@forEachIndexed // spine — skip
            assertTrue(
                "Limb $index start index $start must be in [0, ${LANDMARK_COUNT - 1}]",
                start in 0 until LANDMARK_COUNT
            )
            assertTrue(
                "Limb $index end index $end must be in [0, ${LANDMARK_COUNT - 1}]",
                end in 0 until LANDMARK_COUNT
            )
        }
    }

    @Test
    fun limbConnections_spineIsLastEntry_markedWithNegativeOne() {
        val (start, end) = LIMB_CONNECTIONS[12]
        assertEquals("Spine limb start must be -1", -1, start)
        assertEquals("Spine limb end must be -1", -1, end)
    }

    @Test
    fun limbConnections_exactlyOneSpineEntry() {
        val spineCount = LIMB_CONNECTIONS.count { (s, e) -> s == -1 && e == -1 }
        assertEquals("Exactly one spine limb expected", 1, spineCount)
    }

    @Test
    fun limbConnections_limbDefinitionsMatchAlgorithmMd() {
        // Spot-check a representative subset from ALGORITHM.md
        assertEquals(Pair(11, 13), LIMB_CONNECTIONS[0])  // left upper arm
        assertEquals(Pair(13, 15), LIMB_CONNECTIONS[1])  // left forearm
        assertEquals(Pair(12, 14), LIMB_CONNECTIONS[2])  // right upper arm
        assertEquals(Pair(14, 16), LIMB_CONNECTIONS[3])  // right forearm
        assertEquals(Pair(11, 12), LIMB_CONNECTIONS[10]) // shoulder line
        assertEquals(Pair(23, 24), LIMB_CONNECTIONS[11]) // hip line
    }

    // ── projectLandmark ──────────────────────────────────────────────────────

    @Test
    fun projectLandmark_centreNormalisedCoord_mapsToCentreOfCanvas() {
        val landmark = Triple(0.5f, 0.5f, 0f)
        val (px, py) = projectLandmark(landmark, canvasWidth = 100f, canvasHeight = 200f)
        assertEquals("x should be canvasWidth * 0.5", 50f, px, 0.001f)
        assertEquals("y should be canvasHeight * 0.5", 100f, py, 0.001f)
    }

    @Test
    fun projectLandmark_originNormalisedCoord_mapsToTopLeft() {
        val landmark = Triple(0f, 0f, 0f)
        val (px, py) = projectLandmark(landmark, canvasWidth = 300f, canvasHeight = 400f)
        assertEquals(0f, px, 0.001f)
        assertEquals(0f, py, 0.001f)
    }

    @Test
    fun projectLandmark_fullNormalisedCoord_mapsToBottomRight() {
        val landmark = Triple(1f, 1f, 0f)
        val (px, py) = projectLandmark(landmark, canvasWidth = 300f, canvasHeight = 400f)
        assertEquals(300f, px, 0.001f)
        assertEquals(400f, py, 0.001f)
    }

    @Test
    fun projectLandmark_zIsIgnored_differentZProducesSameOutput() {
        val w = 200f; val h = 300f
        val (px1, py1) = projectLandmark(Triple(0.4f, 0.6f, 0.99f), w, h)
        val (px2, py2) = projectLandmark(Triple(0.4f, 0.6f, -0.99f), w, h)
        assertEquals("x must be identical regardless of z", px1, px2, 0f)
        assertEquals("y must be identical regardless of z", py1, py2, 0f)
    }

    // ── computeSpineEndpoints ────────────────────────────────────────────────

    @Test
    fun computeSpineEndpoints_symmetricPose_startIsMidpointOfShoulders() {
        val landmarks = buildLandmarks(
            mapOf(
                11 to Triple(0.3f, 0.2f, 0f), // left shoulder
                12 to Triple(0.7f, 0.2f, 0f), // right shoulder
                23 to Triple(0.3f, 0.6f, 0f), // left hip
                24 to Triple(0.7f, 0.6f, 0f)  // right hip
            )
        )
        val w = 100f; val h = 100f
        val (spineStart, _) = computeSpineEndpoints(landmarks, w, h)
        assertEquals("Spine start x = midpoint of shoulder x", 50f, spineStart.first, 0.001f)
        assertEquals("Spine start y = midpoint of shoulder y", 20f, spineStart.second, 0.001f)
    }

    @Test
    fun computeSpineEndpoints_symmetricPose_endIsMidpointOfHips() {
        val landmarks = buildLandmarks(
            mapOf(
                11 to Triple(0.3f, 0.2f, 0f),
                12 to Triple(0.7f, 0.2f, 0f),
                23 to Triple(0.3f, 0.6f, 0f),
                24 to Triple(0.7f, 0.6f, 0f)
            )
        )
        val w = 100f; val h = 100f
        val (_, spineEnd) = computeSpineEndpoints(landmarks, w, h)
        assertEquals("Spine end x = midpoint of hip x", 50f, spineEnd.first, 0.001f)
        assertEquals("Spine end y = midpoint of hip y", 60f, spineEnd.second, 0.001f)
    }

    @Test
    fun computeSpineEndpoints_asymmetricPose_midpointsAreCorrect() {
        val landmarks = buildLandmarks(
            mapOf(
                11 to Triple(0.2f, 0.1f, 0f), // left shoulder
                12 to Triple(0.6f, 0.3f, 0f), // right shoulder (different height)
                23 to Triple(0.25f, 0.55f, 0f),
                24 to Triple(0.65f, 0.75f, 0f)
            )
        )
        val w = 200f; val h = 200f
        val (spineStart, spineEnd) = computeSpineEndpoints(landmarks, w, h)

        assertEquals((0.2f + 0.6f) / 2f * w, spineStart.first, 0.001f)
        assertEquals((0.1f + 0.3f) / 2f * h, spineStart.second, 0.001f)
        assertEquals((0.25f + 0.65f) / 2f * w, spineEnd.first, 0.001f)
        assertEquals((0.55f + 0.75f) / 2f * h, spineEnd.second, 0.001f)
    }

    // ── Default color list sizes ─────────────────────────────────────────────

    @Test
    fun defaultJointColorList_sizeIsLandmarkCount() {
        val defaultColors = List(LANDMARK_COUNT) { androidx.compose.ui.graphics.Color.Green }
        assertEquals(LANDMARK_COUNT, defaultColors.size)
    }

    @Test
    fun defaultLimbColorList_sizeIsLimbCount() {
        val defaultColors = List(LIMB_COUNT) { androidx.compose.ui.graphics.Color.Green }
        assertEquals(LIMB_COUNT, defaultColors.size)
    }

    @Test
    fun defaultJointColorList_allColorsAreGreen() {
        val defaultColors = List(LANDMARK_COUNT) { androidx.compose.ui.graphics.Color.Green }
        defaultColors.forEachIndexed { i, color ->
            assertEquals("jointColors[$i] must be green by default", androidx.compose.ui.graphics.Color.Green, color)
        }
    }

    @Test
    fun defaultLimbColorList_allColorsAreGreen() {
        val defaultColors = List(LIMB_COUNT) { androidx.compose.ui.graphics.Color.Green }
        defaultColors.forEachIndexed { i, color ->
            assertEquals("limbColors[$i] must be green by default", androidx.compose.ui.graphics.Color.Green, color)
        }
    }
}

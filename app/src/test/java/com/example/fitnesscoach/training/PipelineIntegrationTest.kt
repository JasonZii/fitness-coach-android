package com.example.fitnesscoach.training

import com.example.fitnesscoach.core.util.Constants.OE_DTW_MIN_FRAMES
import com.example.fitnesscoach.training.data.parseReferencePoseJson
import com.example.fitnesscoach.training.domain.EvaluateExerciseUseCase
import com.example.fitnesscoach.training.pose.alignOeDtw
import com.example.fitnesscoach.training.pose.normalizeLandmarks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Integration test for the full per-frame algorithm chain:
 *   parseReferencePoseJson → normalizeLandmarks → alignOeDtw → EvaluateExerciseUseCase
 *
 * Uses squat.json as both the reference sequence and the simulated user input
 * (identical data) so the pipeline can be exercised without any device or camera.
 *
 * Assertions (ALGORITHM.md §Module 2 & 3):
 *   - Frames 0 .. OE_DTW_MIN_FRAMES-1 : matchedIdx == -1  (warm-up period)
 *   - Frames OE_DTW_MIN_FRAMES .. end  : matchedIdx >= 0
 *   - All frames                       : sf in [0f, 100f]
 */
class PipelineIntegrationTest {

    private fun loadSquatJson(): String =
        javaClass.classLoader!!
            .getResourceAsStream("landmarks/squat.json")!!
            .bufferedReader()
            .readText()

    @Test
    fun fullPipeline_squatJson_warmupAndAlignmentAndScoring() {
        // ── Build reference sequence ──────────────────────────────────────────
        val rawFrames        = parseReferencePoseJson(loadSquatJson())
        val referenceSequence = rawFrames.map { normalizeLandmarks(it) }

        val evaluateUseCase  = EvaluateExerciseUseCase()
        val userSequence     = mutableListOf<List<Pair<Float, Float>>>()

        println("%-6s  %-14s  %s".format("frame", "matchedIdx", "sf"))
        println("-".repeat(36))

        // ── Simulate per-frame processing ─────────────────────────────────────
        rawFrames.forEachIndexed { frameIdx, rawFrame ->
            val normalized = normalizeLandmarks(rawFrame)
            userSequence.add(normalized)

            val matchedIdx  = alignOeDtw(userSequence, referenceSequence)
            val scoreResult = evaluateUseCase.evaluate(matchedIdx, normalized, referenceSequence)
            val sf          = scoreResult.sf

            println("%-6d  %-14d  %.2f".format(frameIdx, matchedIdx, sf))

            // ── Assertions ────────────────────────────────────────────────────
            // After adding frame[frameIdx], userSequence.size == frameIdx + 1.
            // alignOeDtw returns -1 when size < OE_DTW_MIN_FRAMES (i.e. frameIdx + 1 < 20),
            // so the last warm-up frame is index 18; frame 19 is the first real alignment.
            if (frameIdx + 1 < OE_DTW_MIN_FRAMES) {
                assertEquals(
                    "Frame $frameIdx (userSeq size ${frameIdx + 1}) should be in warm-up",
                    -1, matchedIdx
                )
            } else {
                assertTrue(
                    "Frame $frameIdx matchedIdx should be >= 0 (was $matchedIdx)",
                    matchedIdx >= 0
                )
            }

            assertTrue(
                "Frame $frameIdx sf should be in [0, 100] (was $sf)",
                sf in 0f..100f
            )
        }

        println("-".repeat(36))
        println("Processed ${rawFrames.size} frames. All assertions passed.")
    }
}
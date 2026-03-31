package com.example.fitnesscoach.training.pose

import com.example.fitnesscoach.training.core.PoseScoringEngine
import junit.framework.TestCase.assertEquals
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class PoseScoringEngineTest {

    @Test
    fun testSample() {
        assertEquals(2, 1 + 1)
    }

    @Test
    fun perfectMatch_shouldReturnHighScores() {
        val ref = List(33) { Pair(0.5f, 0.5f) }
        val user = List(33) { Pair(0.5f, 0.5f) }

        val result = PoseScoringEngine.calculatePoseScore(user, ref)

        assertEquals(33, result.jointScores.size)
        assertEquals(13, result.limbScores.size)
        assertEquals(33, result.jointColors.size)
        assertEquals(13, result.limbColors.size)

        assertTrue(result.s1 >= 99f)
        assertTrue(result.s2 >= 99f)
        assertTrue(result.sf >= 99f)
    }

    @Test
    fun shiftedJoint_shouldReduceJointScore() {
        val ref = List(33) { Pair(0.5f, 0.5f) }
        val user = List(33) { Pair(0.5f, 0.5f) }.toMutableList()

        user[0] = Pair(0.9f, 0.9f)

        val result = PoseScoringEngine.calculatePoseScore(user, ref)

        assertTrue(result.jointScores[0] < 80f)
        assertTrue(result.s1 < 100f)
        assertTrue(result.sf < 100f)

        user[0] = Pair(0.9f, 0.9f)
        assertTrue(result.jointScores[0] < 80f)
        assertTrue(result.s1 < 100f)
        assertTrue(result.sf < 100f)
    }

    @Test
    fun wrongLimbDirection_shouldReduceLimbScore() {
        val ref = MutableList(33) { Pair(0.5f, 0.5f) }
        val user = MutableList(33) { Pair(0.5f, 0.5f) }

        // 构造参考左上臂: 11 -> 13 向右
        ref[11] = Pair(0.4f, 0.5f)
        ref[13] = Pair(0.6f, 0.5f)

        // 构造用户左上臂: 11 -> 13 向左（方向相反）
        user[11] = Pair(0.4f, 0.5f)
        user[13] = Pair(0.2f, 0.5f)

        val result = PoseScoringEngine.calculatePoseScore(user, ref)

        // limb 0 = Left upper arm
        assertTrue(result.limbScores[0] < 80f)
        assertTrue(result.s2 < 100f)
        assertTrue(result.sf < 100f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidUserLandmarkSize_shouldThrowException() {
        val ref = List(33) { Pair(0.5f, 0.5f) }
        val user = List(32) { Pair(0.5f, 0.5f) }

        PoseScoringEngine.calculatePoseScore(user, ref)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidReferenceLandmarkSize_shouldThrowException() {
        val ref = List(32) { Pair(0.5f, 0.5f) }
        val user = List(33) { Pair(0.5f, 0.5f) }

        PoseScoringEngine.calculatePoseScore(user, ref)
    }
}
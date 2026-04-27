package com.example.fitnesscoach.exercise.data

import com.example.fitnesscoach.training.pose.CameraAngle
import com.example.fitnesscoach.training.pose.ReadinessVisibilityMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ExerciseDataTest {

    @Test
    fun `standing dumbbell shoulder press keeps front angle but uses bicep style visibility gate`() {
        val bicepCurl = exerciseList.find { it.id == "bicep_curl" }
        val shoulderPress = exerciseList.find { it.id == "standing_dumbbell_shoulder_press" }

        assertNotNull("bicep_curl should exist in exerciseList", bicepCurl)
        assertNotNull("standing_dumbbell_shoulder_press should exist in exerciseList", shoulderPress)
        assertEquals(
            CameraAngle.FRONT,
            shoulderPress!!.requiredCameraAngle,
        )
        assertEquals(
            "shoulder press should use the same readiness visibility rule as bicep curl",
            bicepCurl!!.readinessVisibilityMode,
            shoulderPress.readinessVisibilityMode,
        )
        assertEquals(
            ReadinessVisibilityMode.ANY_VISIBLE_SIDE,
            shoulderPress.readinessVisibilityMode,
        )
    }
}

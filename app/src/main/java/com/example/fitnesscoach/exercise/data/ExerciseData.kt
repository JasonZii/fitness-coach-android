package com.example.fitnesscoach.exercise.data

import com.example.fitnesscoach.R
import com.example.fitnesscoach.training.pose.CameraAngle
import com.example.fitnesscoach.training.pose.ReadinessVisibilityMode
import com.example.fitnesscoach.training.pose.SideViewDirection

enum class KeyBodyPart {
    SHOULDERS,
    ELBOWS,
    WRISTS,
    HIPS,
    KNEES,
    ANKLES,
}

data class ExerciseInfo(
    val id: String,
    val title: String,
    val description: String,
    val imageRes: Int,
    val videoRes: Int,
    /** Filename inside assets/landmarks/ used to load the reference pose sequence. */
    val jsonFileName: String,
    /** Camera angle the user must stand at before the readiness countdown begins. */
    val requiredCameraAngle: CameraAngle,
    /** Whether readiness requires the configured full-body visibility check. */
    val requiresFullBody: Boolean,
    /** Expected left/right facing direction for side-view reference videos. */
    val requiredSideViewDirection: SideViewDirection = SideViewDirection.NONE,
    /** Body-part groups that should be prioritised by exercise-specific scoring/alignment. */
    val keyBodyParts: Set<KeyBodyPart>,
    /** Landmark-visibility rule used by the readiness gate before recording starts. */
    val readinessVisibilityMode: ReadinessVisibilityMode,
)

val exerciseList = listOf(
    ExerciseInfo(
        id = "squat",
        title = "Squat",
        description = """
        Stand with your feet shoulder-width apart, chest lifted, and core engaged.
        Push your hips back, bend your knees, then drive through your heels to stand.
        Keep your knees aligned with your toes throughout the movement.
        """.trimIndent(),
        imageRes = R.drawable.squat,
        videoRes = R.raw.squat,
        jsonFileName = "squat.json",
        requiredCameraAngle = CameraAngle.SIDE,
        requiresFullBody = true,
        requiredSideViewDirection = SideViewDirection.RIGHT,
        keyBodyParts = setOf(KeyBodyPart.HIPS, KeyBodyPart.KNEES, KeyBodyPart.ANKLES),
        readinessVisibilityMode = ReadinessVisibilityMode.ANY_VISIBLE_SIDE,
    ),
    ExerciseInfo(
        id = "lateral_raise",
        title = "Lateral Raise",
        description = """
        Stand tall with dumbbells at your sides and a slight bend in your elbows.
        Raise both arms to shoulder height, then lower them with control.
        Avoid swinging your torso so the shoulders do the work.
        """.trimIndent(),
        imageRes = R.drawable.lateral_raise,
        videoRes = R.raw.lateral_raise,
        jsonFileName = "lateral_raise.json",
        requiredCameraAngle = CameraAngle.FRONT,
        requiresFullBody = false,
        requiredSideViewDirection = SideViewDirection.NONE,
        keyBodyParts = setOf(KeyBodyPart.SHOULDERS, KeyBodyPart.ELBOWS, KeyBodyPart.WRISTS),
        readinessVisibilityMode = ReadinessVisibilityMode.BOTH_VISIBLE_SIDES,
    ),
    ExerciseInfo(
        id = "bicep_curl",
        title = "Bicep Curl",
        description = """
        Stand upright with your elbows close to your sides and palms facing forward.
        Curl the dumbbells toward your shoulders, then lower them slowly.
        Keep your upper arms still and avoid using body momentum.
        """.trimIndent(),
        imageRes = R.drawable.bicep_curl,
        videoRes = R.raw.bicep_curl,
        jsonFileName = "bicep_curl.json",
        requiredCameraAngle = CameraAngle.SIDE,
        requiresFullBody = false,
        requiredSideViewDirection = SideViewDirection.RIGHT,
        keyBodyParts = setOf(KeyBodyPart.SHOULDERS, KeyBodyPart.ELBOWS, KeyBodyPart.WRISTS),
        readinessVisibilityMode = ReadinessVisibilityMode.ANY_VISIBLE_SIDE,
    ),
    ExerciseInfo(
        id = "lunge_knee_raise",
        title = "Lunge Knee Raise",
        description = """
        Step forward into a lunge, keeping your torso tall and core steady.
        Push through the front heel to stand and lift the working knee upward.
        Move with control so your balance stays stable.
        """.trimIndent(),
        imageRes = R.drawable.lunge_knee_raise,
        videoRes = R.raw.lunge_knee_raise,
        jsonFileName = "lunge_knee_raise.json",
        requiredCameraAngle = CameraAngle.SIDE,
        requiresFullBody = true,
        requiredSideViewDirection = SideViewDirection.RIGHT,
        keyBodyParts = setOf(KeyBodyPart.HIPS, KeyBodyPart.KNEES, KeyBodyPart.ANKLES),
        readinessVisibilityMode = ReadinessVisibilityMode.ANY_VISIBLE_SIDE,
    ),
    ExerciseInfo(
        id = "shoulder_press",
        title = "Shoulder Press",
        description = """
        Hold dumbbells at shoulder height with your core braced.
        Press upward until your arms extend, then lower back to shoulder level.
        Keep your ribs down and avoid arching your lower back.
        """.trimIndent(),
        imageRes = R.drawable.shoulder_press,
        videoRes = R.raw.shoulder_press,
        jsonFileName = "shoulder_press.json",
        requiredCameraAngle = CameraAngle.FRONT,
        requiresFullBody = false,
        requiredSideViewDirection = SideViewDirection.NONE,
        keyBodyParts = setOf(KeyBodyPart.SHOULDERS, KeyBodyPart.ELBOWS, KeyBodyPart.WRISTS),
        readinessVisibilityMode = ReadinessVisibilityMode.BOTH_VISIBLE_SIDES,
    ),
)

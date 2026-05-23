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
        How to Perform a Proper Squat:
        Stand with your feet shoulder-width apart and your toes pointing slightly outward.
        Keep your chest up and your back straight.
        Slowly bend your knees and push your hips back as if you are sitting on a chair.
        Lower yourself until your thighs are parallel to the floor, or as low as you can go comfortably.
        Keep your knees aligned with your toes and avoid letting them cave inward.
        Push through your heels to return to the starting position.
        Breathe in as you go down, and exhale as you come up.
        Tip:
        Don't lean forward too much, and keep your weight on your heels to protect your knees.
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
        How to Perform a Lateral Raise:
        Stand upright with a dumbbell in each hand at your sides.
        Keep your back straight and core engaged.
        Raise both arms out to the sides until they reach shoulder height.
        Keep a slight bend in your elbows.
        Pause briefly at the top.
        Slowly lower the dumbbells back to the starting position.

        Tip:
        Avoid swinging your body. Use controlled movement to target your shoulders.
        """.trimIndent(),
        imageRes = R.drawable.lateral_raise,
        videoRes = R.raw.lateral_raise,
        jsonFileName = "lateral_raise.json",
        requiredCameraAngle = CameraAngle.FRONT,
        requiresFullBody = false,
        requiredSideViewDirection = SideViewDirection.NONE,
        keyBodyParts = setOf(KeyBodyPart.SHOULDERS, KeyBodyPart.ELBOWS, KeyBodyPart.WRISTS),
        readinessVisibilityMode = ReadinessVisibilityMode.ANY_VISIBLE_SIDE,
    ),
    ExerciseInfo(
        id = "bicep_curl",
        title = "Bicep Curl",
        description = """
        How to Perform a Bicep Curl:
        Stand upright with a dumbbell in each hand, arms fully extended at your sides.
        Keep your elbows close to your torso and your palms facing forward.
        Slowly curl the dumbbells upward by contracting your biceps.
        Continue raising until the dumbbells are at shoulder level.
        Pause briefly at the top, then slowly lower the dumbbells back to the starting position.

        Tip:
        Keep your upper arms stationary throughout the movement. Only your forearms should move.
        Avoid swinging your body to lift the weight.
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
        How to Perform a Lunge Knee Raise:
        Stand upright with feet together.
        Step forward with your right leg into a lunge position.
        Lower your body until both knees are bent at about 90 degrees.
        Push through your right heel to stand up.
        As you rise, bring your right knee up toward your chest.
        Return to the starting position and repeat.

        Tip:
        Keep your balance and engage your core throughout the movement.
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
        How to Perform a Shoulder Press:
        Stand with a dumbbell in each hand at shoulder height.
        Keep your palms facing forward.
        Press the dumbbells upward until your arms are fully extended.
        Pause briefly at the top.
        Slowly lower the dumbbells back to shoulder level.

        Tip:
        Avoid arching your back. Keep your core tight and stable.
        """.trimIndent(),
        imageRes = R.drawable.shoulder_press,
        videoRes = R.raw.shoulder_press,
        jsonFileName = "shoulder_press.json",
        requiredCameraAngle = CameraAngle.FRONT,
        requiresFullBody = false,
        requiredSideViewDirection = SideViewDirection.NONE,
        keyBodyParts = setOf(KeyBodyPart.SHOULDERS, KeyBodyPart.ELBOWS, KeyBodyPart.WRISTS),
        readinessVisibilityMode = ReadinessVisibilityMode.ANY_VISIBLE_SIDE,
    ),
)

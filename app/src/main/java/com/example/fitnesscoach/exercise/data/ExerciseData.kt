package com.example.fitnesscoach.exercise.data

import com.example.fitnesscoach.R
data class ExerciseInfo(
    val id: String,
    val title: String,
    val description: String,
    val imageRes: Int,
    val videoRes: Int
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
        Don’t lean forward too much, and keep your weight on your heels to protect your knees.
                """.trimIndent(),
        imageRes = R.drawable.squat,
        videoRes = R.raw.squat
    ),
    ExerciseInfo(
        id = "dumbbell_lateral_raise",
        title = "Dumbbell Lateral Raise",
        description = """
        How to Perform a Dumbbell Lateral Raise:
        Stand upright with a dumbbell in each hand at your sides.
        Keep your back straight and core engaged.
        Raise both arms out to the sides until they reach shoulder height.
        Keep a slight bend in your elbows.
        Pause briefly at the top.
        Slowly lower the dumbbells back to the starting position.
        
        Tip:
        Avoid swinging your body. Use controlled movement to target your shoulders.
                """.trimIndent(),
        imageRes = R.drawable.dumbbell_lateral_raise,
        videoRes = R.raw.dumbbell_lateral_raise
        ),
    ExerciseInfo(
        id = "dumbbell_overhead_triceps_extension",
        title = "Dumbbell Overhead Triceps Extension",
        description = """
        How to Perform a Dumbbell Overhead Triceps Extension:
        Stand or sit with a dumbbell held by both hands overhead.
        Keep your upper arms close to your head.
        Slowly bend your elbows to lower the dumbbell behind your head.
        Pause when you feel a stretch in your triceps.
        Extend your arms back up to the starting position.
        
        Tip:
        Keep your elbows steady and avoid flaring them out.
                """.trimIndent(),
        imageRes = R.drawable.dumbbell_overhead_triceps_extension,
        videoRes = R.raw.dumbbell_overhead_triceps_extension
        ),

    ExerciseInfo(
        id = "right_leg_lunge_to_knee_raise",
        title = "Right Leg Lunge To Knee Raise",
        description = """
        How to Perform a Right Leg Lunge to Knee Raise:
        Stand upright with feet together.
        Step forward with your right leg into a lunge position.
        Lower your body until both knees are bent at about 90 degrees.
        Push through your right heel to stand up.
        As you rise, bring your right knee up toward your chest.
        Return to the starting position and repeat.
        
        Tip:
        Keep your balance and engage your core throughout the movement.
                """.trimIndent(),
        imageRes = R.drawable.right_leg_lunge_to_knee_raise,
        videoRes = R.raw.right_leg_lunge_to_knee_raise
        ),

    ExerciseInfo(
        id = "standing_dumbbell_shoulder_press",
        title = "Standing Dumbbell Shoulder Press",
        description = """
        How to Perform a Standing Dumbbell Shoulder Press:
        Stand with a dumbbell in each hand at shoulder height.
        Keep your palms facing forward.
        Press the dumbbells upward until your arms are fully extended.
        Pause briefly at the top.
        Slowly lower the dumbbells back to shoulder level.
        
        Tip:
        Avoid arching your back. Keep your core tight and stable.
                """.trimIndent(),
        imageRes = R.drawable.standing_dumbbell_shoulder_press,
        videoRes = R.raw.standing_dumbbell_shoulder_press
        )
)
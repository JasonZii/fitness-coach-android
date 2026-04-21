package com.example.fitnesscoach.core.util

object Constants {

    // MediaPipe landmark indices
    const val LANDMARK_NOSE = 0
    const val LANDMARK_LEFT_SHOULDER = 11
    const val LANDMARK_RIGHT_SHOULDER = 12
    const val LANDMARK_LEFT_ELBOW = 13
    const val LANDMARK_RIGHT_ELBOW = 14
    const val LANDMARK_LEFT_WRIST = 15
    const val LANDMARK_RIGHT_WRIST = 16
    const val LANDMARK_LEFT_HIP = 23
    const val LANDMARK_RIGHT_HIP = 24
    const val LANDMARK_LEFT_KNEE = 25
    const val LANDMARK_RIGHT_KNEE = 26
    const val LANDMARK_LEFT_ANKLE = 27
    const val LANDMARK_RIGHT_ANKLE = 28

    // Total number of MediaPipe BlazePose landmarks per frame
    const val LANDMARK_COUNT = 33
    // Total number of limb connections used for skeleton overlay and scoring
    const val LIMB_COUNT = 13

    // Module 1: Skeleton normalisation
    // Acceptable error tolerance for acceptance-criteria checks
    const val NORMALISE_EPSILON = 0.001f

    // Module 2: OE-DTW real-time alignment
    // Minimum user sequence length before alignment is considered stable
    const val OE_DTW_MIN_FRAMES = 20

    // Module 3: Per-landmark scoring
    // Joint/limb score below this threshold → RED; at or above → GREEN
    const val SCORE_RED_THRESHOLD = 80f
    // Weight of joint-position score S1 in the overall frame score Sf
    const val SCORE_WEIGHT_S1 = 0.2f
    // Weight of limb-angle score S2 in the overall frame score Sf
    const val SCORE_WEIGHT_S2 = 0.8f

    // Module 5: Camera readiness detection
    // Minimum per-landmark visibility score for full-body-in-frame check
    const val VISIBILITY_IN_FRAME_MIN = 0.5f
    // theta (nose–shoulder–shoulder angle) above this value → front view
    const val CAMERA_ANGLE_FRONT_MIN = 150f
    // theta below this value → side view
    const val CAMERA_ANGLE_SIDE_MAX = 60f
    // Both readiness conditions must hold this many seconds before countdown
    const val READINESS_HOLD_SECONDS = 3
    // Grace period (ms) before a broken condition resets the countdown — absorbs MediaPipe noise
    const val READINESS_GRACE_MS = 400L

    // State machine thresholds — Squat (squat, ALGORITHM.md Module 4)
    const val SQUAT_S1_ANGLE = 160f   // standing straight;    angle ≥ this → S1 territory
    const val SQUAT_S3_ANGLE = 90f    // fully squatted;       angle ≤ this → S3 territory

    // State machine thresholds — Right Leg Lunge to Knee Raise (right_leg_lunge_to_knee_raise)
    const val LUNGE_S1_ANGLE = 160f   // standing straight;    angle ≥ this → S1 territory
    const val LUNGE_S3_ANGLE = 90f    // lunge bottom;         angle ≤ this → S3 territory

    // State machine thresholds — Bicep Curl (bicep_curl)
    const val BICEP_CURL_S1_ANGLE = 160f   // arm fully extended;  angle ≥ this → S1 territory
    const val BICEP_CURL_S3_ANGLE = 50f    // curl peak;           angle ≤ this → S3 territory

    // State machine thresholds — Standing Dumbbell Shoulder Press (standing_dumbbell_shoulder_press)
    const val SHOULDER_PRESS_S1_ANGLE = 90f    // arms at shoulder height; angle ≤ this → S1 territory
    const val SHOULDER_PRESS_S3_ANGLE = 160f   // fully pressed overhead;  angle ≥ this → S3 territory

    // State machine thresholds — Dumbbell Lateral Raise (dumbbell_lateral_raise)
    const val LATERAL_RAISE_S1_ANGLE = 30f   // arm at side;            angle ≤ this → S1 territory
    const val LATERAL_RAISE_S3_ANGLE = 80f   // arm raised to shoulder; angle ≥ this → S3 territory

    // Module 4: Rep correctness classification
    // A rep is CORRECT when the longest consecutive-red-frame run is ≤ this value (~0.25 s at 20 fps)
    const val MAX_CONSECUTIVE_RED_FRAMES = 5

    // Module 6: Training end detection
    // Average visibility of key landmarks below this triggers pause
    const val END_VISIBILITY_THRESHOLD = 0.3f
    // Seconds below threshold required to confirm training end
    const val END_VISIBILITY_HOLD_SECONDS = 3
    // Shoulder-width ratio multiplier to detect user walking toward camera
    const val END_SHOULDER_WIDTH_RATIO = 1.5f
}
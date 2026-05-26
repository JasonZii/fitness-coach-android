package com.example.fitnesscoach.training.domain

import android.util.Log
import com.example.fitnesscoach.core.util.Constants.BICEP_CURL_S1_ANGLE
import com.example.fitnesscoach.core.util.Constants.BICEP_CURL_S3_ANGLE
import com.example.fitnesscoach.core.util.Constants.LANDMARK_LEFT_ELBOW
import com.example.fitnesscoach.core.util.Constants.LANDMARK_LEFT_HIP
import com.example.fitnesscoach.core.util.Constants.LANDMARK_LEFT_SHOULDER
import com.example.fitnesscoach.core.util.Constants.LANDMARK_LEFT_WRIST
import com.example.fitnesscoach.core.util.Constants.LANDMARK_RIGHT_ANKLE
import com.example.fitnesscoach.core.util.Constants.LANDMARK_RIGHT_ELBOW
import com.example.fitnesscoach.core.util.Constants.LANDMARK_RIGHT_HIP
import com.example.fitnesscoach.core.util.Constants.LANDMARK_RIGHT_KNEE
import com.example.fitnesscoach.core.util.Constants.LANDMARK_RIGHT_SHOULDER
import com.example.fitnesscoach.core.util.Constants.LANDMARK_RIGHT_WRIST
import com.example.fitnesscoach.core.util.Constants.LATERAL_RAISE_S1_ANGLE
import com.example.fitnesscoach.core.util.Constants.LATERAL_RAISE_S3_ANGLE
import com.example.fitnesscoach.core.util.Constants.LUNGE_S1_ANGLE
import com.example.fitnesscoach.core.util.Constants.LUNGE_S3_ANGLE
import com.example.fitnesscoach.core.util.Constants.SHOULDER_PRESS_S1_ANGLE
import com.example.fitnesscoach.core.util.Constants.SHOULDER_PRESS_S3_ANGLE
import com.example.fitnesscoach.core.util.Constants.SQUAT_S1_ANGLE
import com.example.fitnesscoach.core.util.Constants.SQUAT_S3_ANGLE
import com.example.fitnesscoach.core.util.Constants.VISIBILITY_IN_FRAME_MIN
import kotlin.math.acos
import kotlin.math.sqrt

private const val BICEP_CURL_ID = "bicep_curl"
private const val BICEP_CURL_MOVE_ENTER_ANGLE = 150f
private const val BICEP_CURL_TOP_LEAVE_ANGLE = 90f
private const val BICEP_CURL_COOLDOWN_FRAMES = 8
private const val REP_COUNTER_LOG_TAG = "RepCounter"

/**
 * Module 4 — Rep-counting state machine supporting all 5 exercises.
 *
 * Three-state cycle per side:
 *
 *   S1 (start) ──► S2 (transition) ──► S3 (peak) ──► S2 (return) ──► S1
 *                                                                      ↑
 *                                                          rep counted here
 *
 * A rep is counted only when a full S1→S2→S3→S2→S1 cycle completes.
 * Shallow movements that never reach S3 are silently discarded.
 *
 * ── Side-view exercises (right side only) ────────────────────────────────
 *   squat, lunge_knee_raise, bicep_curl
 *   Only right-side joints are tracked; one [SideState] instance is used.
 *
 * ── Front-view exercises (both sides) ────────────────────────────────────
 *   shoulder_press, lateral_raise
 *   Both sides are tracked independently via separate [SideState] instances.
 *   A rep is counted only when BOTH sides have each completed a full cycle.
 *   Strategy: each side raises its own [SideState.hasCompleted] flag when it
 *   finishes; the rep is credited once both flags are set, then both are cleared.
 *   This tolerates natural bilateral asynchrony: if one arm finishes a few
 *   frames ahead of the other, it waits. If one arm consistently lags a full
 *   rep behind, reps from the fast arm are not credited until the slow arm
 *   catches up — this signals bad form rather than inflating the rep count.
 *
 * @param exerciseId One of the five canonical exercise IDs. Defaults to "squat".
 *                   Unknown IDs fall back to the squat configuration.
 */
class CountRepsUseCase(private val exerciseId: String = "squat") {

    private enum class RepState { S1, S2, S3 }

    // ── Per-side state machine ─────────────────────────────────────────────────

    /**
     * Tracks one side's progress through the S1→S2→S3→S2→S1 rep cycle.
     *
     * [hasCompleted] is set to `true` when a full cycle finishes and is cleared
     * externally by [CountRepsUseCase] once the bilateral (or unilateral)
     * condition triggers a rep count. This decoupling lets bilateral exercises
     * handle asynchrony: side A can finish and raise [hasCompleted] while side B
     * is still mid-rep, without losing the signal.
     *
     * @param s1Angle     Angle boundary between S1 and S2.
     * @param s3Angle     Angle boundary between S2 and S3.
     * @param s3HasLarger `true`  → S3 reached by angle INCREASING (shoulder press, lateral raise).
     *                   `false` → S3 reached by angle DECREASING (squat, lunge, bicep curl).
     * @param s1ExitAngle Optional hysteresis threshold for leaving S1.
     * @param s3ExitAngle Optional hysteresis threshold for leaving S3.
     * @param cooldownFrames Number of frames to ignore after a completed rep.
     */
    private class SideState(
        private val s1Angle:     Float,
        private val s3Angle:     Float,
        private val s3HasLarger: Boolean,
        private val s1ExitAngle: Float = s1Angle,
        private val s3ExitAngle: Float = s3Angle,
        private val cooldownFrames: Int = 0,
    ) {
        var repState:     RepState = RepState.S1; private set
        var hasVisitedS3: Boolean  = false;       private set
        var cooldownRemaining: Int = 0; private set

        /**
         * A full S1→S2→S3→S2→S1 cycle has completed.
         * Cleared externally by [CountRepsUseCase] after the rep is counted.
         */
        var hasCompleted: Boolean = false

        /** Advances the state machine by one frame with [angle] (degrees). */
        fun advance(angle: Float) {
            if (cooldownRemaining > 0) {
                cooldownRemaining--
                return
            }
            if (s3HasLarger) advanceIncreasing(angle) else advanceDecreasing(angle)
        }

        /**
         * S3 reached by angle DECREASING (squat, lunge, bicep curl).
         * S1 territory: angle ≥ s1Angle.  S3 territory: angle ≤ s3Angle.
         */
        private fun advanceDecreasing(angle: Float) {
            when (repState) {
                RepState.S1 -> {
                    if (angle < s1ExitAngle) {
                        repState = RepState.S2
                    }
                }
                RepState.S2 -> when {
                    angle <= s3Angle -> { repState = RepState.S3; hasVisitedS3 = true }
                    angle >= s1Angle -> {
                        if (hasVisitedS3) hasCompleted = true
                        repState = RepState.S1
                    }
                }
                RepState.S3 -> {
                    if (angle > s3ExitAngle) repState = RepState.S2
                }
            }
        }

        /**
         * S3 reached by angle INCREASING (shoulder press, lateral raise).
         * S1 territory: angle ≤ s1Angle.  S3 territory: angle ≥ s3Angle.
         */
        private fun advanceIncreasing(angle: Float) {
            when (repState) {
                RepState.S1 -> {
                    if (angle > s1Angle) {
                        repState = RepState.S2
                    }
                }
                RepState.S2 -> when {
                    angle >= s3Angle -> { repState = RepState.S3; hasVisitedS3 = true }
                    angle <= s1Angle -> {
                        if (hasVisitedS3) hasCompleted = true
                        repState = RepState.S1
                    }
                }
                RepState.S3 -> {
                    if (angle < s3ExitAngle) repState = RepState.S2
                }
            }
        }

        fun consumeCompleted() {
            hasCompleted = false
            hasVisitedS3 = false
            cooldownRemaining = cooldownFrames
        }

        fun reset() {
            repState     = RepState.S1
            hasVisitedS3 = false
            hasCompleted = false
            cooldownRemaining = 0
        }
    }

    // ── Exercise configuration ─────────────────────────────────────────────────

    /**
     * Per-exercise joint selection and threshold configuration.
     *
     * For unilateral exercises [isBilateral] = false; left-side fields are
     * never read and are set to placeholder values matching the right side.
     *
     * Thresholds are shared between both sides of a bilateral exercise
     * (the left arm mirrors the right arm geometry).
     */
    private data class ExerciseConfig(
        // ── Right side ────────────────────────────────────────────────────────
        val rightP1Idx:        Int,
        val rightVertexIdx:    Int,
        val rightP2Idx:        Int,
        val rightVisibilityLandmarks: List<Int>,
        // ── Left side (only used when isBilateral = true) ─────────────────────
        val leftP1Idx:         Int,
        val leftVertexIdx:     Int,
        val leftP2Idx:         Int,
        val leftVisibilityLandmarks:  List<Int>,
        // ── Angle thresholds (same for both sides) ────────────────────────────
        val s1Angle:     Float,
        val s3Angle:     Float,
        /** true  → S3 is the HIGH angle; false → S3 is the LOW angle. */
        val s3HasLarger: Boolean,
        val s1ExitAngle: Float = s1Angle,
        val s3ExitAngle: Float = s3Angle,
        val cooldownFrames: Int = 0,
        // ── Mode ─────────────────────────────────────────────────────────────
        /** true for front-view exercises that require both arms to move. */
        val isBilateral: Boolean,
    )

    private val config    = buildConfig(exerciseId)
    private val rightSide = SideState(
        config.s1Angle,
        config.s3Angle,
        config.s3HasLarger,
        config.s1ExitAngle,
        config.s3ExitAngle,
        config.cooldownFrames,
    )
    private val leftSide  = SideState(
        config.s1Angle,
        config.s3Angle,
        config.s3HasLarger,
        config.s1ExitAngle,
        config.s3ExitAngle,
        config.cooldownFrames,
    )

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Advances the state machine by one camera frame.
     *
     * @param landmarks    Raw MediaPipe landmarks (33 triples, x/y in [0,1]).
     * @param visibilities Per-landmark visibility scores (33 floats, [0,1]).
     * @return `true` exactly when a full rep cycle just completed.
     *         For bilateral exercises, requires BOTH sides to have completed.
     */
    fun update(
        landmarks:    List<Triple<Float, Float, Float>>,
        visibilities: List<Float>,
    ): Boolean {
        if (landmarks.size < 33 || visibilities.size < 33) return false
        return if (config.isBilateral) updateBilateral(landmarks, visibilities)
               else updateUnilateral(landmarks, visibilities)
    }

    /**
     * Resets all state to S1. Call when the training session pauses or restarts.
     */
    fun reset() {
        rightSide.reset()
        leftSide.reset()
    }

    // ── Unilateral update (side-view exercises) ───────────────────────────────

    private fun updateUnilateral(
        landmarks:    List<Triple<Float, Float, Float>>,
        visibilities: List<Float>,
    ): Boolean {
        if (config.rightVisibilityLandmarks.any { visibilities[it] < VISIBILITY_IN_FRAME_MIN }) return false
        val angle = jointAngle(landmarks, config.rightP1Idx, config.rightVertexIdx, config.rightP2Idx)
            ?: return false

        rightSide.advance(angle)

        val repCompleted = rightSide.hasCompleted
        if (repCompleted) rightSide.consumeCompleted()
        logBicepCurlFrame(angle, repCompleted)
        return repCompleted
    }

    // ── Bilateral update (front-view exercises) ───────────────────────────────

    private fun updateBilateral(
        landmarks:    List<Triple<Float, Float, Float>>,
        visibilities: List<Float>,
    ): Boolean {
        // [Bug A 修复前] 原始逻辑：双侧所有关键关节（含手腕）均需可见才推进状态机。
        // 问题：肩上推举/侧平举到达动作顶点时，手腕常超出画面边缘（visibility→0），
        // 导致双侧状态机在 S3 阈值附近全部冻结，hasVisitedS3 永远不会被置 true，rep 无法计数。
        // 如需回滚，取消注释下方两行并删除 Bug A 修复块。
        // if (config.rightVisibilityLandmarks.any { visibilities[it] < VISIBILITY_IN_FRAME_MIN }) return false
        // if (config.leftVisibilityLandmarks.any  { visibilities[it] < VISIBILITY_IN_FRAME_MIN }) return false

        // [Bug A 修复] 仅要求双侧 VERTEX 关节（肩上推举的肘、侧平举的肩）可见即可推进。
        // 手腕离框时 MediaPipe 仍会外推其坐标位置，用于角度计算精度足够（误差 <5°），
        // 不应因手腕离框而冻结整个状态机。
        // 如需回滚，删除这4行并恢复上方注释掉的两行。
        val rightVertexVisible = visibilities[config.rightVertexIdx] >= VISIBILITY_IN_FRAME_MIN
        val leftVertexVisible  = visibilities[config.leftVertexIdx]  >= VISIBILITY_IN_FRAME_MIN
        if (!rightVertexVisible || !leftVertexVisible) return false

        val rightAngle = jointAngle(landmarks, config.rightP1Idx, config.rightVertexIdx, config.rightP2Idx)
            ?: return false
        val leftAngle  = jointAngle(landmarks, config.leftP1Idx,  config.leftVertexIdx,  config.leftP2Idx)
            ?: return false

        rightSide.advance(rightAngle)
        leftSide.advance(leftAngle)

        // Count a rep only when both sides have independently completed a cycle.
        return if (rightSide.hasCompleted && leftSide.hasCompleted) {
            rightSide.consumeCompleted()
            leftSide.consumeCompleted()
            true
        } else false
    }

    private fun logBicepCurlFrame(angle: Float, repCompleted: Boolean) {
        if (exerciseId != BICEP_CURL_ID) return
        Log.d(
            REP_COUNTER_LOG_TAG,
            "bicep_curl angle=$angle state=${rightSide.repState} " +
                "hasVisitedS3=${rightSide.hasVisitedS3} repCompleted=$repCompleted " +
                "cooldownRemaining=${rightSide.cooldownRemaining}"
        )
    }

    // ── Angle calculation ─────────────────────────────────────────────────────

    /**
     * Interior angle (degrees) at [vertexIdx], formed by vectors
     * ([p1Idx] → [vertexIdx]) and ([p2Idx] → [vertexIdx]).
     *
     * Only x and y are used; z is discarded (consistent with normalisation).
     * Returns null when either limb vector has near-zero length (degenerate pose).
     */
    internal fun jointAngle(
        landmarks: List<Triple<Float, Float, Float>>,
        p1Idx:     Int,
        vertexIdx: Int,
        p2Idx:     Int,
    ): Float? {
        val p1     = landmarks[p1Idx]
        val vertex = landmarks[vertexIdx]
        val p2     = landmarks[p2Idx]

        val v1x = p1.first  - vertex.first;  val v1y = p1.second - vertex.second
        val v2x = p2.first  - vertex.first;  val v2y = p2.second - vertex.second

        val mag1 = sqrt(v1x * v1x + v1y * v1y)
        val mag2 = sqrt(v2x * v2x + v2y * v2y)
        if (mag1 < 1e-6f || mag2 < 1e-6f) return null

        val cosine = ((v1x * v2x + v1y * v2y) / (mag1 * mag2)).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(cosine.toDouble())).toFloat()
    }

    // ── Configuration builder ─────────────────────────────────────────────────

    companion object {

        /**
         * Returns the [ExerciseConfig] for [exerciseId].
         * Unknown IDs fall back to squat.
         *
         * HOW TO ADD A NEW EXERCISE
         * ─────────────────────────
         * 1. Add a `when` branch below — copy the squat block as a template.
         * 2. Add S1/S3 angle constants to Constants.kt.
         * 3. Set [isBilateral] = true for front-view exercises and fill in the
         *    left-side landmark fields.
         * 4. Write unit tests following the SquatTests class in CountRepsUseCaseTest.kt.
         * 5. Calibrate thresholds with real motion-capture data in Sprint 4
         *    and update Constants.kt.
         */
        private fun buildConfig(exerciseId: String): ExerciseConfig = when (exerciseId) {

            // ── squat ──────────────────────────────────────────────────────────────────
            // CAMERA : side view → right side only.
            // JOINT  : right knee — p1 = hip (R24), vertex = knee (R26), p2 = ankle (R28).
            //          The knee is the primary hinge joint; its interior angle gives the
            //          cleanest signal for squat depth from a side camera.
            // LOGIC  : standing ≈ 160° (S1); full squat ≈ 90° (S3); angle DECREASES.
            // STATUS : reference implementation — thresholds validated.
            "squat" -> ExerciseConfig(
                rightP1Idx        = LANDMARK_RIGHT_HIP,
                rightVertexIdx    = LANDMARK_RIGHT_KNEE,
                rightP2Idx        = LANDMARK_RIGHT_ANKLE,
                rightVisibilityLandmarks = listOf(LANDMARK_RIGHT_HIP, LANDMARK_RIGHT_KNEE, LANDMARK_RIGHT_ANKLE),
                leftP1Idx         = LANDMARK_RIGHT_HIP,     // unused (unilateral)
                leftVertexIdx     = LANDMARK_RIGHT_KNEE,    // unused (unilateral)
                leftP2Idx         = LANDMARK_RIGHT_ANKLE,   // unused (unilateral)
                leftVisibilityLandmarks  = emptyList(),
                s1Angle           = SQUAT_S1_ANGLE,
                s3Angle           = SQUAT_S3_ANGLE,
                s3HasLarger       = false,
                isBilateral       = false,
            )

            // ── lunge_knee_raise ──────────────────────────────────────────
            // CAMERA : side view → right side only.
            // JOINT  : right knee — same as squat (hip→knee←ankle).
            //          The right leg is the working leg. Its knee angle drops at the
            //          lunge bottom and rises on the way back, matching the S1→S3→S1 cycle.
            // LOGIC  : standing ≈ 160° (S1); lunge bottom ≈ 90° (S3); angle DECREASES.
            // CALIBRATION NEEDED: LUNGE thresholds default to squat values.
            //   - Verify actual knee angle at lunge depth (may differ by step length).
            //   - Confirm that the knee-raise phase at the top of the movement cycles
            //     S3→S2→S1 cleanly. If the knee stays bent during the raise, a 4th
            //     state or a separate "knee-up" detection may be needed.
            "lunge_knee_raise" -> ExerciseConfig(
                rightP1Idx        = LANDMARK_RIGHT_HIP,
                rightVertexIdx    = LANDMARK_RIGHT_KNEE,
                rightP2Idx        = LANDMARK_RIGHT_ANKLE,
                rightVisibilityLandmarks = listOf(LANDMARK_RIGHT_HIP, LANDMARK_RIGHT_KNEE, LANDMARK_RIGHT_ANKLE),
                leftP1Idx         = LANDMARK_RIGHT_HIP,     // unused (unilateral)
                leftVertexIdx     = LANDMARK_RIGHT_KNEE,    // unused (unilateral)
                leftP2Idx         = LANDMARK_RIGHT_ANKLE,   // unused (unilateral)
                leftVisibilityLandmarks  = emptyList(),
                s1Angle           = LUNGE_S1_ANGLE,
                s3Angle           = LUNGE_S3_ANGLE,
                s3HasLarger       = false,
                isBilateral       = false,
            )

            // ── bicep_curl ─────────────────────────────────────────────────────────────
            // CAMERA : side view → right side only.
            // JOINT  : right elbow — p1 = wrist (R16), vertex = elbow (R14), p2 = shoulder (R12).
            //          Elbow flexion is the only movement in a strict curl. Using wrist
            //          (not hand) as p1 gives better visibility when holding a dumbbell.
            // LOGIC  : arm fully extended ≈ 160° (S1); curl peak ≈ 50° (S3); angle DECREASES.
            // CALIBRATION NEEDED:
            //   - BICEP_CURL_S3_ANGLE = 50° is a rough estimate; measure actual peak
            //     elbow angle from side-view curl recordings in Sprint 4.
            //   - If the shoulder rotates forward during the curl, the angle may become
            //     noisy. Consider a shoulder-stability check or using z-depth.
            BICEP_CURL_ID -> ExerciseConfig(
                rightP1Idx        = LANDMARK_RIGHT_WRIST,
                rightVertexIdx    = LANDMARK_RIGHT_ELBOW,
                rightP2Idx        = LANDMARK_RIGHT_SHOULDER,
                rightVisibilityLandmarks = listOf(LANDMARK_RIGHT_WRIST, LANDMARK_RIGHT_ELBOW, LANDMARK_RIGHT_SHOULDER),
                leftP1Idx         = LANDMARK_RIGHT_WRIST,     // unused (unilateral)
                leftVertexIdx     = LANDMARK_RIGHT_ELBOW,     // unused (unilateral)
                leftP2Idx         = LANDMARK_RIGHT_SHOULDER,  // unused (unilateral)
                leftVisibilityLandmarks  = emptyList(),
                s1Angle           = BICEP_CURL_S1_ANGLE,
                s3Angle           = BICEP_CURL_S3_ANGLE,
                s3HasLarger       = false,
                s1ExitAngle        = BICEP_CURL_MOVE_ENTER_ANGLE,
                s3ExitAngle        = BICEP_CURL_TOP_LEAVE_ANGLE,
                cooldownFrames     = BICEP_CURL_COOLDOWN_FRAMES,
                isBilateral       = false,
            )

            // ── shoulder_press ───────────────────────────────────────
            // CAMERA : front view → BOTH sides tracked.
            // JOINT  : elbow flexion angle on each side — wrist → elbow ← shoulder.
            //   Right: wrist (R16) → elbow (R14) ← shoulder (R12)
            //   Left:  wrist (L15) → elbow (L13) ← shoulder (L11)
            //   When the arm is abducted (raised sideways) for a press, the elbow
            //   angle changes clearly from ~90° (start) to ~160° (overhead), and
            //   this transition is visible from a front camera.
            // LOGIC  : dumbbell at shoulder height → elbow ≈ 90° (S1); arms fully
            //          overhead → elbow ≈ 160° (S3); angle INCREASES.
            // CALIBRATION NEEDED:
            //   - SHOULDER_PRESS_S1/S3_ANGLE are rough estimates. Camera projection from
            //     the front reduces the apparent elbow angle — measure real values in Sprint 4.
            //   - If signal quality is poor (arm close to body at start), consider using
            //     shoulder-abduction angle (elbow vs. torso midline) as an alternative.
            "shoulder_press" -> ExerciseConfig(
                rightP1Idx        = LANDMARK_RIGHT_WRIST,
                rightVertexIdx    = LANDMARK_RIGHT_ELBOW,
                rightP2Idx        = LANDMARK_RIGHT_SHOULDER,
                rightVisibilityLandmarks = listOf(LANDMARK_RIGHT_WRIST, LANDMARK_RIGHT_ELBOW, LANDMARK_RIGHT_SHOULDER),
                leftP1Idx         = LANDMARK_LEFT_WRIST,
                leftVertexIdx     = LANDMARK_LEFT_ELBOW,
                leftP2Idx         = LANDMARK_LEFT_SHOULDER,
                leftVisibilityLandmarks  = listOf(LANDMARK_LEFT_WRIST, LANDMARK_LEFT_ELBOW, LANDMARK_LEFT_SHOULDER),
                s1Angle           = SHOULDER_PRESS_S1_ANGLE,
                s3Angle           = SHOULDER_PRESS_S3_ANGLE,
                s3HasLarger       = true,
                isBilateral       = true,
            )

            // ── lateral_raise ─────────────────────────────────────────────────
            // CAMERA : front view → BOTH sides tracked.
            // JOINT  : shoulder-abduction angle on each side — hip → shoulder ← wrist.
            //   Right: hip (R24) → shoulder (R12) ← wrist (R16)
            //   Left:  hip (L23) → shoulder (L11) ← wrist (L15)
            //   This hip–shoulder–wrist angle measures arm-raise height relative to the
            //   torso. Using hip (not spine midpoint) avoids a synthetic landmark.
            // LOGIC  : arm resting at side → angle ≈ 30° (S1); arm at shoulder height
            //          → angle ≈ 80° (S3); angle INCREASES.
            // CALIBRATION NEEDED:
            //   - LATERAL_RAISE_S1/S3_ANGLE are rough estimates; record front-view data
            //     in Sprint 4.
            //   - This angle is sensitive to lateral hip sway. Ensure the user stands
            //     still, or add a hip-stability guard before counting the rep.
            "lateral_raise" -> ExerciseConfig(
                rightP1Idx        = LANDMARK_RIGHT_HIP,
                rightVertexIdx    = LANDMARK_RIGHT_SHOULDER,
                rightP2Idx        = LANDMARK_RIGHT_WRIST,
                rightVisibilityLandmarks = listOf(LANDMARK_RIGHT_HIP, LANDMARK_RIGHT_SHOULDER, LANDMARK_RIGHT_WRIST),
                leftP1Idx         = LANDMARK_LEFT_HIP,
                leftVertexIdx     = LANDMARK_LEFT_SHOULDER,
                leftP2Idx         = LANDMARK_LEFT_WRIST,
                leftVisibilityLandmarks  = listOf(LANDMARK_LEFT_HIP, LANDMARK_LEFT_SHOULDER, LANDMARK_LEFT_WRIST),
                s1Angle           = LATERAL_RAISE_S1_ANGLE,
                s3Angle           = LATERAL_RAISE_S3_ANGLE,
                s3HasLarger       = true,
                isBilateral       = true,
            )

            // ── fallback (unknown exerciseId) ──────────────────────────────────────────
            else -> ExerciseConfig(
                rightP1Idx        = LANDMARK_RIGHT_HIP,
                rightVertexIdx    = LANDMARK_RIGHT_KNEE,
                rightP2Idx        = LANDMARK_RIGHT_ANKLE,
                rightVisibilityLandmarks = listOf(LANDMARK_RIGHT_HIP, LANDMARK_RIGHT_KNEE, LANDMARK_RIGHT_ANKLE),
                leftP1Idx         = LANDMARK_RIGHT_HIP,
                leftVertexIdx     = LANDMARK_RIGHT_KNEE,
                leftP2Idx         = LANDMARK_RIGHT_ANKLE,
                leftVisibilityLandmarks  = emptyList(),
                s1Angle           = SQUAT_S1_ANGLE,
                s3Angle           = SQUAT_S3_ANGLE,
                s3HasLarger       = false,
                isBilateral       = false,
            )
        }
    }
}

package com.example.fitnesscoach.training.viewmodel

import android.app.Application
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitnesscoach.core.mediapipe.PoseResult
import com.example.fitnesscoach.exercise.data.exerciseList
import com.example.fitnesscoach.core.util.Constants.LANDMARK_COUNT
import com.example.fitnesscoach.core.util.Constants.LANDMARK_LEFT_HIP
import com.example.fitnesscoach.core.util.Constants.LANDMARK_LEFT_SHOULDER
import com.example.fitnesscoach.core.util.Constants.LANDMARK_RIGHT_HIP
import com.example.fitnesscoach.core.util.Constants.LANDMARK_RIGHT_SHOULDER
import com.example.fitnesscoach.core.util.Constants.LIMB_COUNT
import kotlin.math.sqrt
import com.example.fitnesscoach.training.core.RepScoreTracker
import com.example.fitnesscoach.training.domain.CountRepsUseCase
import com.example.fitnesscoach.training.data.loadRawReferenceSequence
import com.example.fitnesscoach.training.domain.EvaluateExerciseUseCase
import com.example.fitnesscoach.training.pose.CameraAngle
import com.example.fitnesscoach.training.pose.ReadinessPhase
import com.example.fitnesscoach.training.pose.ReadinessState
import com.example.fitnesscoach.training.pose.ReadinessStateMachine
import com.example.fitnesscoach.training.pose.ReadinessVisibilityMode
import com.example.fitnesscoach.training.pose.SideViewDirection
import com.example.fitnesscoach.training.pose.TrainingEndDetector
import com.example.fitnesscoach.training.pose.TrainingPauseState
import com.example.fitnesscoach.training.pose.alignOeDtw
import com.example.fitnesscoach.training.pose.detectCameraAngle
import com.example.fitnesscoach.training.pose.detectSideViewDirection
import com.example.fitnesscoach.training.pose.isFullBodyInFrame
import com.example.fitnesscoach.training.pose.normalizeLandmarks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay


private const val CAMERA_DIRECTION_WARNING_GRACE_MS = 1500L
private const val DIRECTION_DEBUG_TAG = "DIRECTION_DEBUG"

// ── Session phase ─────────────────────────────────────────────────────────────

enum class SessionPhase {
    /** Waiting for user to stand in frame at the correct angle. */
    READINESS,
    /** Countdown finished; exercise reps are being recorded. */
    TRAINING,
    /** User pressed Stop or auto-end was triggered. */
    FINISHED,
}

// ── UI state ──────────────────────────────────────────────────────────────────

/**
 * Single source of truth consumed by TrainingScreen.
 * All fields have safe defaults so the screen can render before loading finishes.
 */
data class TrainingUiState(
    /** Controls which UI section is visible. */
    val phase: SessionPhase = SessionPhase.READINESS,

    /** False until the exercise reference JSON has been parsed and normalised. */
    val isReferenceLoaded: Boolean = false,

    // ── Readiness ─────────────────────────────────────────────────────────────
    /** ReadinessStateMachine snapshot; drives countdown digit display. */
    val readiness: ReadinessState = ReadinessState(ReadinessPhase.NOT_READY),
    /** True when the configured readiness visibility rule is satisfied. */
    val isFullBodyInFrame: Boolean = false,
    /** Camera angle as classified by detectCameraAngle(); shown as a user hint. */
    val cameraAngle: CameraAngle = CameraAngle.AMBIGUOUS,
    /** Camera angle required by the selected exercise (SIDE for squat/lunge, FRONT otherwise). */
    val requiredCameraAngle: CameraAngle = CameraAngle.SIDE,
    /** Whether readiness requires the configured full-body visibility check. */
    val requiresFullBody: Boolean = true,
    /** True after the camera angle has been wrong continuously during training. */
    val isCameraDirectionWarningVisible: Boolean = false,

    // ── Skeleton overlay (valid in both phases once landmarks arrive) ──────────
    /** Raw MediaPipe (x, y, z) triples passed directly to SkeletonOverlay. */
    val landmarks: List<Triple<Float, Float, Float>> = emptyList(),

    // ── Training scoring ───────────────────────────────────────────────────────
    /** Per-joint display colours from EvaluateExerciseUseCase (size 33). */
    val jointColors: List<Color> = List(LANDMARK_COUNT) { Color.Green },
    /** Per-limb display colours from EvaluateExerciseUseCase (size 13). */
    val limbColors: List<Color> = List(LIMB_COUNT) { Color.Green },
    /** Overall frame score 0–100 for the most recent frame. */
    val currentFrameScore: Float = 0f,

    // ── Reference ghost skeleton ───────────────────────────────────────────────
    /**
     * Raw (x, y, z) landmarks of the matched standard frame.
     * Non-empty only when at least one joint is red in the current frame,
     * i.e. posture correction is needed. Pass to a secondary SkeletonOverlay
     * to render the target "ghost" pose alongside the user's live skeleton.
     * Empty list when all joints are green or during the OE-DTW warm-up period.
     */
    val matchedReferenceRawLandmarks: List<Triple<Float, Float, Float>> = emptyList(),

    val dynamicReferenceLandmarks: List<Triple<Float, Float, Float>> = emptyList(),

    // ── Rep tracking ──────────────────────────────────────────────────────────
    /** Number of fully completed reps this session. */
    val repCount: Int = 0,
    /** Average Sf score for each completed rep (index 0 = first rep). */
    val repScores: List<Float> = emptyList(),
    /** Reps whose max consecutive red-frame run did not exceed the threshold. */
    val correctReps: Int = 0,
    /** Reps whose max consecutive red-frame run exceeded the threshold. */
    val incorrectReps: Int = 0,

    // ── Training interruption (Module 6) ──────────────────────────────────────
    /**
     * True when the session is auto-paused (user walked too close or key landmarks
     * disappeared). UI should show a "Move back / ensure full body is visible" hint.
     * False during normal training.
     */
    val isTrainingPaused: Boolean = false,

)

// ── ViewModel ─────────────────────────────────────────────────────────────────

/**
 * Drives the full per-frame training pipeline and exposes [uiState] to
 * TrainingScreen via a single StateFlow.
 *
 * Per-frame data flow (see PRD §6.2 / ALGORITHM.md):
 * ```
 * PoseResult
 *   ├─ [READINESS] isFullBodyInFrame + detectCameraAngle
 *   │              → ReadinessStateMachine → transition to TRAINING
 *   └─ [TRAINING]  normalizeLandmarks
 *                  → userSequence.add → alignOeDtw
 *                  → EvaluateExerciseUseCase (colors + score)
 *                  → RepScoreTracker (per-frame accumulation)
 *                  → CountRepsUseCase (Module 4, exercise-specific rep detection)
 * ```
 */
class TrainingViewModel(application: Application) : AndroidViewModel(application) {

    // ── Public state ──────────────────────────────────────────────────────────
    private val _uiState = MutableStateFlow(TrainingUiState())
    val uiState: StateFlow<TrainingUiState> = _uiState.asStateFlow()

    // ── Algorithm modules ─────────────────────────────────────────────────────
    private val readinessMachine = ReadinessStateMachine()
    private val evaluateUseCase  = EvaluateExerciseUseCase()
    private val repScoreTracker  = RepScoreTracker()
    private val endDetector      = TrainingEndDetector()
    // CountRepsUseCase is exercise-specific; reassigned in loadExercise().
    private var countRepsUseCase = CountRepsUseCase()
    private var currentExerciseId: String = "squat"

    /** Tracks the previous frame's pause state to detect ACTIVE→PAUSED transitions. */
    private var wasTrainingPaused = false
    private var cameraDirectionMismatchSinceMs: Long? = null

    // ── Reference sequences (set once from IO thread, read on main thread) ──────
    @Volatile private var referenceSequence:    List<List<Pair<Float, Float>>>          = emptyList()
    @Volatile private var rawReferenceSequence: List<List<Triple<Float, Float, Float>>> = emptyList()

    // ── OE-DTW input: normalised frames since training started ────────────────
    private val userSequence = mutableListOf<List<Pair<Float, Float>>>()

    // ── Required camera angle for the current exercise ─────────────────────────
    @Volatile private var requiredCameraAngle: CameraAngle = CameraAngle.SIDE
    @Volatile private var requiresFullBody: Boolean = true
    @Volatile private var requiredSideViewDirection: SideViewDirection = SideViewDirection.NONE
    @Volatile private var readinessVisibilityMode: ReadinessVisibilityMode =
        ReadinessVisibilityMode.ANY_VISIBLE_SIDE

    private var referenceFrameIndex = 0
    private var referenceJob: Job? = null
    private var referenceFrames: List<List<Triple<Float, Float, Float>>> = emptyList()

    private fun startDynamicReferenceSkeleton() {
        referenceJob?.cancel()

        referenceJob = viewModelScope.launch {
            while (true) {
                if (referenceFrames.isNotEmpty()) {

                    referenceFrameIndex =
                        (referenceFrameIndex + 1) % referenceFrames.size

                    _uiState.value = _uiState.value.copy(
                        dynamicReferenceLandmarks =
                            referenceFrames[referenceFrameIndex]
                    )
                }

                delay(100L) // 10 FPS
            }
        }
    }

    private fun stopDynamicReferenceSkeleton() {
        referenceJob?.cancel()
        referenceJob = null
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Called once per camera frame by CameraPreview's onPoseDetected callback.
     *
     * Ignored silently when [PoseResult.landmarks] does not contain exactly
     * [LANDMARK_COUNT] points (e.g. no person detected).
     */
    fun onFrame(poseResult: PoseResult) {
        if (poseResult.landmarks.size != LANDMARK_COUNT) return

        when (_uiState.value.phase) {
            SessionPhase.READINESS -> processReadinessFrame(poseResult)
            SessionPhase.TRAINING  -> processTrainingFrame(poseResult)
            SessionPhase.FINISHED  -> Unit
        }
    }

    /**
     * Loads reference data for [exerciseId] and fully resets all session state.
     *
     * Safe to call multiple times (e.g. "Try Again" from the result screen).
     * Each call discards the previous session's rep count, scores, OE-DTW
     * sequence, and rep-counting state, then reloads the reference JSON on a
     * background thread.
     *
     * [jsonFileName] and [requiredCameraAngle] are read directly from [ExerciseInfo]
     * in [exerciseList] — no internal mapping table.
     */
    fun loadExercise(exerciseId: String) {
        val info = exerciseList.find { it.id == exerciseId } ?: exerciseList.first()

        // Reset all per-session state before (re-)loading the reference data.
        readinessMachine.reset()
        endDetector.reset()
        repScoreTracker.reset()
        userSequence.clear()
        countRepsUseCase = CountRepsUseCase(info.id)
        currentExerciseId = info.id
        wasTrainingPaused = false
        cameraDirectionMismatchSinceMs = null
        requiredCameraAngle = info.requiredCameraAngle
        requiresFullBody = info.requiresFullBody
        requiredSideViewDirection = info.requiredSideViewDirection
        readinessVisibilityMode = info.readinessVisibilityMode

        _uiState.value = TrainingUiState(
            requiredCameraAngle = info.requiredCameraAngle,
            requiresFullBody    = info.requiresFullBody,
            isReferenceLoaded   = false,
        )

        viewModelScope.launch(Dispatchers.IO) {
            val rawSeq = loadRawReferenceSequence(getApplication(), info.jsonFileName)
            val normalizedSeq = rawSeq.map { normalizeLandmarks(it) }
            withContext(Dispatchers.Main) {
                rawReferenceSequence = rawSeq
                referenceSequence    = normalizedSeq
//                _uiState.update { it.copy(isReferenceLoaded = true) }

                referenceFrames = rawSeq
                referenceFrameIndex = 0
                _uiState.update {
                    it.copy(
                        isReferenceLoaded = true,
                        dynamicReferenceLandmarks = referenceFrames.firstOrNull() ?: emptyList()
                    )
                }

                startDynamicReferenceSkeleton()
            }
        }
    }

    /**
     * Ends the session and resets all training state.
     * Call when the user taps Stop. The phase transitions to FINISHED so the
     * caller can navigate to the result screen before the state is wiped.
     */
    fun stopTraining() {
        stopDynamicReferenceSkeleton()

        _uiState.update { it.copy(phase = SessionPhase.FINISHED) }
        readinessMachine.reset()
        endDetector.reset()
        repScoreTracker.reset()
        userSequence.clear()
        countRepsUseCase.reset()
        wasTrainingPaused = false
        cameraDirectionMismatchSinceMs = null
    }

    // ── Readiness phase ───────────────────────────────────────────────────────

    private fun processReadinessFrame(poseResult: PoseResult) {
        val fullBody = !requiresFullBody ||
            isFullBodyInFrame(poseResult.visibilities, readinessVisibilityMode)
        val angle    = detectCameraAngle(poseResult.landmarks)

        val conditionsMet = fullBody && angle == requiredCameraAngle

        val readiness = readinessMachine.update(conditionsMet, System.currentTimeMillis())

        val nextPhase = if (readiness.phase == ReadinessPhase.TRAINING_STARTED)
            SessionPhase.TRAINING else SessionPhase.READINESS

        // Initialise the end-detector baseline on the first TRAINING frame.
        if (nextPhase == SessionPhase.TRAINING) {
            endDetector.onTrainingStart(poseResult.landmarks, requiredCameraAngle)
            //开始标准蓝色估计
//            startDynamicReferenceSkeleton()
        }

        _uiState.update { state ->
            state.copy(
                isFullBodyInFrame = fullBody,
                cameraAngle       = angle,
                readiness         = readiness,
                landmarks         = poseResult.landmarks,
                phase             = nextPhase,
            )
        }
    }

    // ── Training phase ────────────────────────────────────────────────────────

    private fun processTrainingFrame(poseResult: PoseResult) {
        // Guard: skip frames that arrive before the reference sequence is ready.
        if (!_uiState.value.isReferenceLoaded) return

        val angle = detectCameraAngle(poseResult.landmarks)
        val sideViewDirection = if (angle == CameraAngle.SIDE) {
            detectSideViewDirection(poseResult.landmarks)
        } else {
            SideViewDirection.UNKNOWN
        }
        val nowMs = System.currentTimeMillis()
        val directionMismatch = requiredCameraAngle == CameraAngle.SIDE && (
            angle == CameraAngle.FRONT ||
                (
                    angle == CameraAngle.SIDE &&
                        requiredSideViewDirection != SideViewDirection.NONE &&
                        sideViewDirection != SideViewDirection.UNKNOWN &&
                        sideViewDirection != requiredSideViewDirection
                    )
            )
        val showDirectionWarning = updateCameraDirectionWarning(directionMismatch, nowMs)
        Log.d(
            DIRECTION_DEBUG_TAG,
            "exerciseId=$currentExerciseId, " +
                "requiredCameraAngle=$requiredCameraAngle, " +
                "cameraAngle=$angle, " +
                "requiredSideViewDirection=$requiredSideViewDirection, " +
                "detectedSideViewDirection=$sideViewDirection, " +
                "warningMismatch=$directionMismatch, " +
                "showDirectionWarning=$showDirectionWarning"
        )

        // Step 0 — Module 6: check for auto-pause conditions (too close / low visibility).
        val pauseState = endDetector.update(
            poseResult.landmarks,
            poseResult.visibilities,
            nowMs,
        )
        val isPaused = pauseState == TrainingPauseState.PAUSED

        // On ACTIVE→PAUSED transition: discard the in-progress rep and reset OE-DTW.
        if (isPaused && !wasTrainingPaused) {
            repScoreTracker.discardCurrentRep()
            userSequence.clear()
            countRepsUseCase.reset()
        }
        wasTrainingPaused = isPaused

        if (isPaused) {
            // While paused, keep updating the skeleton overlay but skip scoring.
            _uiState.update { state ->
                state.copy(
                    landmarks        = poseResult.landmarks,
                    cameraAngle      = angle,
                    isCameraDirectionWarningVisible = showDirectionWarning,
                    isTrainingPaused = true,
                )
            }
            return
        }

        // Step 1 — normalise the live frame (ALGORITHM.md Module 1, Context 2)
        if (directionMismatch) {
            _uiState.update { state ->
                state.copy(
                    landmarks = poseResult.landmarks,
                    cameraAngle = angle,
                    isCameraDirectionWarningVisible = showDirectionWarning,
                    isTrainingPaused = false,
                )
            }
            return
        }

        val normalized = normalizeLandmarks(poseResult.landmarks)
        userSequence.add(normalized)

        // Step 2 — find the matching reference frame (Module 2)
        // Returns -1 for the first OE_DTW_MIN_FRAMES frames (warm-up period).
        val matchedIdx = alignOeDtw(userSequence, referenceSequence)

        // Step 3 — score the frame and derive joint/limb colors (Module 3)
        // EvaluateExerciseUseCase returns all-green with sf=100 when matchedIdx == -1.
        val scoreResult = evaluateUseCase.evaluate(
            matchedIdx, normalized, referenceSequence,
            upperBodyOnly = !requiresFullBody,
        )

        // Step 4 — accumulate this frame's score for the current rep.
        // hasRedJoint is computed here so it can be forwarded to RepScoreTracker for
        // the correct/incorrect rep classification (continuous red-frame threshold).
        val hasRedLimb = scoreResult.limbColors.any { it == Color.Red }
        repScoreTracker.addFrameScore(scoreResult.sf, hasRedLimb)

        // Step 5 — rep detection (Module 4)
        val repCompleted = countRepsUseCase.update(poseResult.landmarks, poseResult.visibilities)

        val updatedRepScores = if (repCompleted) {
            repScoreTracker.finishRep()          // seals this rep's average score
            userSequence.clear()                 // reset OE-DTW input for the next rep
            repScoreTracker.getCompletedRepScores()
        } else {
            _uiState.value.repScores             // no change
        }

        // Expose the matched standard frame's landmarks only when at least one limb is red.
        // Reposition the reference frame onto the live user's body centre and scale so the
        // ghost skeleton overlays the user regardless of recording distance or position.
        val matchedRaw = if (hasRedLimb && matchedIdx != -1)
            repositionReference(rawReferenceSequence[matchedIdx], poseResult.landmarks)
        else emptyList()

        _uiState.update { state ->
            state.copy(
                landmarks                    = poseResult.landmarks,
                cameraAngle                  = angle,
                isCameraDirectionWarningVisible = showDirectionWarning,
                jointColors                  = scoreResult.jointColors,
                limbColors                   = scoreResult.limbColors,
                currentFrameScore            = scoreResult.sf,
                matchedReferenceRawLandmarks = matchedRaw,
                repCount                     = updatedRepScores.size,
                repScores                    = updatedRepScores,
                correctReps                  = repScoreTracker.correctReps,
                incorrectReps                = repScoreTracker.incorrectReps,
                isTrainingPaused             = false,
            )
        }
    }

    private fun updateCameraDirectionWarning(directionMismatch: Boolean, nowMs: Long): Boolean {
        if (!directionMismatch) {
            cameraDirectionMismatchSinceMs = null
            return false
        }

        val mismatchSince = cameraDirectionMismatchSinceMs ?: nowMs.also {
            cameraDirectionMismatchSinceMs = it
        }
        return nowMs - mismatchSince >= CAMERA_DIRECTION_WARNING_GRACE_MS
    }

    /**
     * Repositions raw reference landmarks onto the live user's body centre and scale.
     *
     * The reference JSON was recorded at an arbitrary camera distance and position.
     * Displaying those absolute coordinates directly would place the ghost skeleton
     * at the wrong height and scale relative to the live user.
     *
     * Steps:
     *   1. Normalise the reference frame (hip-centred, torso-scaled) — same as M1.
     *   2. Denormalise using the live user's hip midpoint and torso length so the
     *      ghost skeleton is anchored to the user's body on screen.
     */
    private fun repositionReference(
        refRaw: List<Triple<Float, Float, Float>>,
        userRaw: List<Triple<Float, Float, Float>>,
    ): List<Triple<Float, Float, Float>> {
        val normalizedRef = normalizeLandmarks(refRaw)

        val userHipMidX = (userRaw[LANDMARK_LEFT_HIP].first  + userRaw[LANDMARK_RIGHT_HIP].first)  / 2f
        val userHipMidY = (userRaw[LANDMARK_LEFT_HIP].second + userRaw[LANDMARK_RIGHT_HIP].second) / 2f
        val userShoulderMidX = (userRaw[LANDMARK_LEFT_SHOULDER].first  + userRaw[LANDMARK_RIGHT_SHOULDER].first)  / 2f
        val userShoulderMidY = (userRaw[LANDMARK_LEFT_SHOULDER].second + userRaw[LANDMARK_RIGHT_SHOULDER].second) / 2f

        val userTorsoLen = sqrt(
            (userShoulderMidX - userHipMidX) * (userShoulderMidX - userHipMidX) +
            (userShoulderMidY - userHipMidY) * (userShoulderMidY - userHipMidY)
        )

        if (userTorsoLen < 1e-6f) return refRaw

        return normalizedRef.map { (nx, ny) ->
            Triple(
                nx * userTorsoLen + userHipMidX,
                ny * userTorsoLen + userHipMidY,
                0f,
            )
        }
    }

}

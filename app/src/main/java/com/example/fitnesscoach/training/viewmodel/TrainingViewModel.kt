package com.example.fitnesscoach.training.viewmodel

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitnesscoach.core.mediapipe.PoseResult
import com.example.fitnesscoach.core.util.Constants.LANDMARK_COUNT
import com.example.fitnesscoach.core.util.Constants.LANDMARK_LEFT_ANKLE
import com.example.fitnesscoach.core.util.Constants.LANDMARK_LEFT_HIP
import com.example.fitnesscoach.core.util.Constants.LANDMARK_LEFT_KNEE
import com.example.fitnesscoach.core.util.Constants.LIMB_COUNT
import com.example.fitnesscoach.core.util.Constants.SQUAT_S1_ANGLE
import com.example.fitnesscoach.core.util.Constants.SQUAT_S3_ANGLE
import com.example.fitnesscoach.training.core.RepScoreTracker
import com.example.fitnesscoach.training.data.loadRawReferenceSequence
import com.example.fitnesscoach.training.domain.EvaluateExerciseUseCase
import com.example.fitnesscoach.training.pose.CameraAngle
import com.example.fitnesscoach.training.pose.ReadinessPhase
import com.example.fitnesscoach.training.pose.ReadinessState
import com.example.fitnesscoach.training.pose.ReadinessStateMachine
import com.example.fitnesscoach.training.pose.TrainingEndDetector
import com.example.fitnesscoach.training.pose.TrainingPauseState
import com.example.fitnesscoach.training.pose.alignOeDtw
import com.example.fitnesscoach.training.pose.detectCameraAngle
import com.example.fitnesscoach.training.pose.isFullBodyInFrame
import com.example.fitnesscoach.training.pose.normalizeLandmarks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.acos
import kotlin.math.sqrt

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

    /** False until squat.json has been parsed and normalised. */
    val isReferenceLoaded: Boolean = false,

    // ── Readiness ─────────────────────────────────────────────────────────────
    /** ReadinessStateMachine snapshot; drives countdown digit display. */
    val readiness: ReadinessState = ReadinessState(ReadinessPhase.NOT_READY),
    /** True when all 9 key landmarks exceed the visibility threshold. */
    val isFullBodyInFrame: Boolean = false,
    /** Camera angle as classified by detectCameraAngle(); shown as a user hint. */
    val cameraAngle: CameraAngle = CameraAngle.AMBIGUOUS,

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

    // ── Rep tracking ──────────────────────────────────────────────────────────
    /** Number of fully completed reps this session. */
    val repCount: Int = 0,
    /** Average Sf score for each completed rep (index 0 = first rep). */
    val repScores: List<Float> = emptyList(),

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
 *                  → rep detection stub  ← replace with CountRepsUseCase
 * ```
 *
 * TODO: Accept an exerciseId parameter so the correct reference JSON and
 *       required camera angle are loaded per exercise instead of being
 *       hardcoded to squat.
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

    /** Tracks the previous frame's pause state to detect ACTIVE→PAUSED transitions. */
    private var wasTrainingPaused = false

    // ── Reference sequences (set once from IO thread, read on main thread) ──────
    @Volatile private var referenceSequence:    List<List<Pair<Float, Float>>>          = emptyList()
    @Volatile private var rawReferenceSequence: List<List<Triple<Float, Float, Float>>> = emptyList()

    // ── OE-DTW input: normalised frames since training started ────────────────
    private val userSequence = mutableListOf<List<Pair<Float, Float>>>()

    // ── Stub rep-detection state ───────────────────────────────────────────────
    // TODO: delete StubRepState and stubRepState once CountRepsUseCase is ready.
    private enum class StubRepState { STANDING, SQUATTING }
    private var stubRepState = StubRepState.STANDING

    // ── Init: load reference sequence ─────────────────────────────────────────

    init {
        // Read squat.json once; derive both raw and normalised sequences.
        // TODO: replace "squat.json" with the filename derived from exerciseId.
        viewModelScope.launch(Dispatchers.IO) {
            val rawSeq = loadRawReferenceSequence(application, "squat.json")
            val normalizedSeq = rawSeq.map { normalizeLandmarks(it) }
            withContext(Dispatchers.Main) {
                rawReferenceSequence = rawSeq
                referenceSequence    = normalizedSeq
                _uiState.update { it.copy(isReferenceLoaded = true) }
            }
        }
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
     * Ends the session. Call when the user taps Stop or when auto-end fires.
     * Resets the readiness machine so the ViewModel can be reused if needed.
     */
    fun stopTraining() {
        readinessMachine.reset()
        endDetector.reset()
        wasTrainingPaused = false
        _uiState.update { it.copy(phase = SessionPhase.FINISHED) }
    }

    // ── Readiness phase ───────────────────────────────────────────────────────

    private fun processReadinessFrame(poseResult: PoseResult) {
        val fullBody = isFullBodyInFrame(poseResult.visibilities)
        val angle    = detectCameraAngle(poseResult.landmarks)

        // Squat requires side view (ALGORITHM.md Module 5 / PRD §3.2).
        // TODO: generalise — check exercise.requiredView instead of hardcoding SIDE.
        val conditionsMet = fullBody && angle == CameraAngle.SIDE

        val readiness = readinessMachine.update(conditionsMet, System.currentTimeMillis())

        val nextPhase = if (readiness.phase == ReadinessPhase.TRAINING_STARTED)
            SessionPhase.TRAINING else SessionPhase.READINESS

        // Initialise the end-detector baseline on the first TRAINING frame.
        if (nextPhase == SessionPhase.TRAINING) {
            endDetector.onTrainingStart(poseResult.landmarks)
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

        // Step 0 — Module 6: check for auto-pause conditions (too close / low visibility).
        val pauseState = endDetector.update(
            poseResult.landmarks,
            poseResult.visibilities,
            System.currentTimeMillis(),
        )
        val isPaused = pauseState == TrainingPauseState.PAUSED

        // On ACTIVE→PAUSED transition: discard the in-progress rep and reset OE-DTW.
        if (isPaused && !wasTrainingPaused) {
            repScoreTracker.discardCurrentRep()
            userSequence.clear()
            stubRepState = StubRepState.STANDING
        }
        wasTrainingPaused = isPaused

        if (isPaused) {
            // While paused, keep updating the skeleton overlay but skip scoring.
            _uiState.update { state ->
                state.copy(
                    landmarks        = poseResult.landmarks,
                    isTrainingPaused = true,
                )
            }
            return
        }

        // Step 1 — normalise the live frame (ALGORITHM.md Module 1, Context 2)
        val normalized = normalizeLandmarks(poseResult.landmarks)
        userSequence.add(normalized)

        // Step 2 — find the matching reference frame (Module 2)
        // Returns -1 for the first OE_DTW_MIN_FRAMES frames (warm-up period).
        val matchedIdx = alignOeDtw(userSequence, referenceSequence)

        // Step 3 — score the frame and derive joint/limb colors (Module 3)
        // EvaluateExerciseUseCase returns all-green with sf=100 when matchedIdx == -1.
        val scoreResult = evaluateUseCase.evaluate(matchedIdx, normalized, referenceSequence)

        // Step 4 — accumulate this frame's score for the current rep
        repScoreTracker.addFrameScore(scoreResult.sf)

        // Step 5 — rep detection
        // ── STUB ─────────────────────────────────────────────────────────────
        // TODO: Replace the two lines below with:
        //   val repCompleted = countRepsUseCase.update(poseResult.landmarks, poseResult.visibilities)
        // once CountRepsUseCase (Module 4, owner: Lee) is implemented.
        // The stub implements a simplified 2-state knee-angle check for squat only;
        // it does not handle the full S1→S2→S3→S2→S1 cycle or side selection.
        val repCompleted = checkRepCompletedStub(normalized)
        // ── END STUB ─────────────────────────────────────────────────────────

        val updatedRepScores = if (repCompleted) {
            repScoreTracker.finishRep()          // seals this rep's average score
            userSequence.clear()                 // reset OE-DTW input for the next rep
            repScoreTracker.getCompletedRepScores()
        } else {
            _uiState.value.repScores             // no change
        }

        // Expose the matched standard frame's raw landmarks only when at least one
        // joint is red — the UI can use this to render a "ghost" target skeleton.
        val hasRedJoint = scoreResult.jointColors.any { it == Color.Red }
        val matchedRaw  = if (hasRedJoint && matchedIdx != -1)
            rawReferenceSequence[matchedIdx] else emptyList()

        _uiState.update { state ->
            state.copy(
                landmarks                    = poseResult.landmarks,
                jointColors                  = scoreResult.jointColors,
                limbColors                   = scoreResult.limbColors,
                currentFrameScore            = scoreResult.sf,
                matchedReferenceRawLandmarks = matchedRaw,
                repCount                     = updatedRepScores.size,
                repScores                    = updatedRepScores,
                isTrainingPaused             = false,
            )
        }
    }

    // ── Stub: 2-state squat rep detector ─────────────────────────────────────
    // TODO: delete this function once CountRepsUseCase is implemented.
    //
    // Mirrors ALGORITHM.md Module 4 (squat) but collapses the 3-state machine
    // to 2 states for simplicity:
    //   STANDING  → knee angle < SQUAT_S3_ANGLE (90°)  → SQUATTING
    //   SQUATTING → knee angle > SQUAT_S1_ANGLE (160°) → STANDING  (rep counted)
    //
    // Uses left-side landmarks only. CountRepsUseCase will select the more-visible
    // side and implement the complete S1→S2→S3→S2→S1 cycle.
    private fun checkRepCompletedStub(normalized: List<Pair<Float, Float>>): Boolean {
        val angle = computeKneeAngle(normalized) ?: return false
        return when {
            stubRepState == StubRepState.STANDING && angle < SQUAT_S3_ANGLE -> {
                stubRepState = StubRepState.SQUATTING
                false
            }
            stubRepState == StubRepState.SQUATTING && angle > SQUAT_S1_ANGLE -> {
                stubRepState = StubRepState.STANDING
                true   // rep complete
            }
            else -> false
        }
    }

    /**
     * Angle at the left knee (hip → knee ← ankle), in degrees.
     * Returns null when either limb vector is degenerate.
     */
    private fun computeKneeAngle(normalized: List<Pair<Float, Float>>): Float? {
        val hip   = normalized[LANDMARK_LEFT_HIP]
        val knee  = normalized[LANDMARK_LEFT_KNEE]
        val ankle = normalized[LANDMARK_LEFT_ANKLE]

        val v1x = hip.first   - knee.first;   val v1y = hip.second   - knee.second
        val v2x = ankle.first - knee.first;   val v2y = ankle.second - knee.second

        val mag1 = sqrt(v1x * v1x + v1y * v1y)
        val mag2 = sqrt(v2x * v2x + v2y * v2y)
        if (mag1 < 1e-6f || mag2 < 1e-6f) return null

        val cosine = ((v1x * v2x + v1y * v2y) / (mag1 * mag2)).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(cosine.toDouble())).toFloat()
    }
}

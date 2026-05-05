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
import com.example.fitnesscoach.training.pose.frameDist
import com.example.fitnesscoach.training.pose.isFullBodyInFrame
import com.example.fitnesscoach.training.pose.normalizeLandmarks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import com.example.fitnesscoach.training.pose.PoseFrameProcessor

private const val CAMERA_DIRECTION_WARNING_GRACE_MS = 1500L

// OE-DTW sliding window: cap userSequence to avoid O(n) growth over time.
private const val MAX_USER_SEQUENCE_FRAMES = 18
private const val REFERENCE_SEARCH_BACK_FRAMES = 4
private const val REFERENCE_SEARCH_FORWARD_FRAMES = 10
private const val MAX_REFERENCE_BACKTRACK_FRAMES = 2
private const val MAX_REFERENCE_FORWARD_JUMP_FRAMES = 6

// Turn this on only when debugging camera direction.
private const val ENABLE_DIRECTION_DEBUG_LOG = false

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
 * Algorithm results consumed by TrainingScreen.
 * Skeleton position is delivered separately via [TrainingViewModel.skeletonState]
 * so that it can update immediately after MediaPipe inference without waiting
 * for the full algorithm pipeline to complete.
 */
data class TrainingUiState(
    val phase: SessionPhase = SessionPhase.READINESS,
    val isReferenceLoaded: Boolean = false,

    // ── Readiness ─────────────────────────────────────────────────────────────
    val readiness: ReadinessState = ReadinessState(ReadinessPhase.NOT_READY),
    val isFullBodyInFrame: Boolean = false,
    val cameraAngle: CameraAngle = CameraAngle.AMBIGUOUS,
    val requiredCameraAngle: CameraAngle = CameraAngle.SIDE,
    val requiresFullBody: Boolean = true,
    val isCameraDirectionWarningVisible: Boolean = false,

    // ── Training scoring ───────────────────────────────────────────────────────
    val jointColors: List<Color> = List(LANDMARK_COUNT) { Color.Green },
    val limbColors: List<Color> = List(LIMB_COUNT) { Color.Green },
    val currentFrameScore: Float = 0f,

    // ── Reference ghost skeleton (blue) ────────────────────────────────────────
    val matchedReferenceRawLandmarks: List<Triple<Float, Float, Float>> = emptyList(),
    val dynamicReferenceLandmarks: List<Triple<Float, Float, Float>> = emptyList(),
    val cameraFrameWidth: Int = 0,
    val cameraFrameHeight: Int = 0,

    // ── Rep tracking ──────────────────────────────────────────────────────────
    val repCount: Int = 0,
    val repScores: List<Float> = emptyList(),
    val correctReps: Int = 0,
    val incorrectReps: Int = 0,

    // ── Training interruption (Module 6) ──────────────────────────────────────
    val isTrainingPaused: Boolean = false,
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

class TrainingViewModel(application: Application) : AndroidViewModel(application) {

    // ── Public state ──────────────────────────────────────────────────────────

    private val _uiState = MutableStateFlow(TrainingUiState())
    val uiState: StateFlow<TrainingUiState> = _uiState.asStateFlow()

    private val _skeletonFlow = MutableStateFlow<List<Triple<Float, Float, Float>>>(emptyList())
    val skeletonState: StateFlow<List<Triple<Float, Float, Float>>> = _skeletonFlow.asStateFlow()

    // Owned here so its lifecycle matches the ViewModel and it can receive
    // colour updates from the algorithm dispatcher.
    val poseFrameProcessor = PoseFrameProcessor(application)

    // ── Algorithm dispatcher ───────────────────────────────────────────────────
    private val algorithmDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    // ── Algorithm modules ─────────────────────────────────────────────────────
    private val readinessMachine = ReadinessStateMachine()
    private val evaluateUseCase  = EvaluateExerciseUseCase()
    private val repScoreTracker  = RepScoreTracker()
    private val endDetector      = TrainingEndDetector()
    private var countRepsUseCase = CountRepsUseCase()
    private var currentExerciseId: String = "squat"

    private var wasTrainingPaused = false
    private var cameraDirectionMismatchSinceMs: Long? = null
    private var lastMatchedReferenceIndex = -1

    // ── Reference sequences (@Volatile: written on IO thread, read on algorithmDispatcher) ──
    @Volatile private var referenceSequence:    List<List<Pair<Float, Float>>>          = emptyList()
    @Volatile private var rawReferenceSequence: List<List<Triple<Float, Float, Float>>> = emptyList()

    // OE-DTW input — only accessed from algorithmDispatcher (single-threaded, no lock needed).
    private val userSequence = mutableListOf<List<Pair<Float, Float>>>()

    // ── Exercise config (@Volatile: written on main thread, read on algorithmDispatcher) ──
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
                    referenceFrameIndex = (referenceFrameIndex + 1) % referenceFrames.size
                    _uiState.value = _uiState.value.copy(
                        dynamicReferenceLandmarks = referenceFrames[referenceFrameIndex]
                    )
                }
                delay(100L)
            }
        }
    }

    private fun stopDynamicReferenceSkeleton() {
        referenceJob?.cancel()
        referenceJob = null
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Called from CameraX's analysisExecutor background thread after each
     * synchronous MediaPipe inference completes.
     *
     * Fast path: immediately publishes raw landmark coordinates to [skeletonState]
     * so the green skeleton redraws without delay.
     *
     * Slow path: dispatches all algorithm computation (DTW, scoring, rep counting)
     * to [algorithmDispatcher] so the main thread is never blocked.
     */
    fun onFrame(poseResult: PoseResult) {
        if (poseResult.landmarks.size != LANDMARK_COUNT) {
            viewModelScope.launch(algorithmDispatcher) {
                if (_uiState.value.phase == SessionPhase.READINESS) {
                    val readiness = readinessMachine.update(
                        conditionsMet = false,
                        nowMs = System.currentTimeMillis(),
                    )
                    _skeletonFlow.value = emptyList()
                    _uiState.update { state ->
                        state.copy(
                            isFullBodyInFrame = false,
                            cameraAngle = CameraAngle.AMBIGUOUS,
                            readiness = readiness,
                            phase = SessionPhase.READINESS,
                            cameraFrameWidth = poseResult.imageWidth,
                            cameraFrameHeight = poseResult.imageHeight,
                        )
                    }
                }
            }
            return
        }

        // Fast path — atomic write, visible to Compose immediately.
        _skeletonFlow.value = poseResult.landmarks

        // Slow path — all algorithm work on a dedicated background thread.
        viewModelScope.launch(algorithmDispatcher) {
            when (_uiState.value.phase) {
                SessionPhase.READINESS -> processReadinessFrame(poseResult)
                SessionPhase.TRAINING  -> processTrainingFrame(poseResult)
                SessionPhase.FINISHED  -> Unit
            }
        }
    }

    /**
     * Loads reference data for [exerciseId] and fully resets session state.
     * Safe to call multiple times (e.g. "Try Again").
     */
    fun loadExercise(exerciseId: String) {
        val info = exerciseList.find { it.id == exerciseId } ?: exerciseList.first()

        // Publish config immediately so @Volatile reads on algorithmDispatcher see new values.
        requiredCameraAngle      = info.requiredCameraAngle
        requiresFullBody         = info.requiresFullBody
        requiredSideViewDirection = info.requiredSideViewDirection
        readinessVisibilityMode  = info.readinessVisibilityMode
        currentExerciseId        = info.id

        _uiState.value = TrainingUiState(
            requiredCameraAngle = info.requiredCameraAngle,
            requiresFullBody    = info.requiresFullBody,
            isReferenceLoaded   = false,
        )
        _skeletonFlow.value = emptyList()

        // Reset all algorithm state on algorithmDispatcher to serialise with any
        // in-flight frame processing coroutine that might still be running.
        viewModelScope.launch(algorithmDispatcher) {
            readinessMachine.reset()
            endDetector.reset()
            repScoreTracker.reset()
            userSequence.clear()
            countRepsUseCase     = CountRepsUseCase(info.id)
            wasTrainingPaused    = false
            lastMatchedReferenceIndex = -1
            cameraDirectionMismatchSinceMs = null
        }

        // Load reference JSON on IO thread; publish results back to main thread.
        viewModelScope.launch(Dispatchers.IO) {
            val rawSeq        = loadRawReferenceSequence(getApplication(), info.jsonFileName)
            val normalizedSeq = rawSeq.map { normalizeLandmarks(it) }
            withContext(Dispatchers.Main) {
                rawReferenceSequence = rawSeq
                referenceSequence    = normalizedSeq
                referenceFrames      = rawSeq
                referenceFrameIndex  = 0
                _uiState.update {
                    it.copy(
                        isReferenceLoaded         = true,
                        dynamicReferenceLandmarks = referenceFrames.firstOrNull() ?: emptyList()
                    )
                }
            }
        }
    }

    /**
     * Ends the session. Phase transitions to FINISHED so the caller can navigate
     * away before state is wiped.
     */
    fun stopTraining() {
        stopDynamicReferenceSkeleton()
        _uiState.update { it.copy(phase = SessionPhase.FINISHED) }
        _skeletonFlow.value = emptyList()

        viewModelScope.launch(algorithmDispatcher) {
            readinessMachine.reset()
            endDetector.reset()
            repScoreTracker.reset()
            userSequence.clear()
            countRepsUseCase.reset()
            wasTrainingPaused              = false
            lastMatchedReferenceIndex      = -1
            cameraDirectionMismatchSinceMs = null
        }
    }

    override fun onCleared() {
        super.onCleared()
        poseFrameProcessor.close()
        algorithmDispatcher.close()
    }

    // ── Readiness phase ───────────────────────────────────────────────────────

    // Runs on algorithmDispatcher. No landmark update here — handled by fast path.
    private fun processReadinessFrame(poseResult: PoseResult) {
        val fullBody = !requiresFullBody ||
            isFullBodyInFrame(poseResult.visibilities, readinessVisibilityMode)
        val angle    = detectCameraAngle(poseResult.landmarks)

        val conditionsMet = fullBody && angle == requiredCameraAngle
        val readiness     = readinessMachine.update(conditionsMet, System.currentTimeMillis())

        val nextPhase = if (readiness.phase == ReadinessPhase.TRAINING_STARTED)
            SessionPhase.TRAINING else SessionPhase.READINESS

        if (nextPhase == SessionPhase.TRAINING) {
            endDetector.onTrainingStart(poseResult.landmarks, requiredCameraAngle)
        }

        _uiState.update { state ->
            state.copy(
                isFullBodyInFrame = fullBody,
                cameraAngle       = angle,
                readiness         = readiness,
                phase             = nextPhase,
                cameraFrameWidth  = poseResult.imageWidth,
                cameraFrameHeight = poseResult.imageHeight,
            )
        }
    }

    // ── Training phase ────────────────────────────────────────────────────────

    // Runs on algorithmDispatcher. No landmark update here — handled by fast path.
    private fun processTrainingFrame(poseResult: PoseResult) {
        if (!_uiState.value.isReferenceLoaded) return

        val angle = detectCameraAngle(poseResult.landmarks)
        val fullBody = isFullBodyInFrame(poseResult.visibilities, readinessVisibilityMode)
        val userIsValid = fullBody && angle == requiredCameraAngle

        if (!userIsValid) {
            _uiState.update { state ->
                state.copy(
                    isFullBodyInFrame = fullBody,
                    cameraAngle       = angle,
                    isTrainingPaused  = true,
                    cameraFrameWidth  = poseResult.imageWidth,
                    cameraFrameHeight = poseResult.imageHeight,
                )
            }
            return
        }

        val sideViewDirection = if (angle == CameraAngle.SIDE)
            detectSideViewDirection(poseResult.landmarks)
        else SideViewDirection.UNKNOWN

        val nowMs = System.currentTimeMillis()
        val directionMismatch = requiredCameraAngle == CameraAngle.SIDE && (
            angle == CameraAngle.FRONT || (
                angle == CameraAngle.SIDE &&
                requiredSideViewDirection != SideViewDirection.NONE &&
                sideViewDirection != SideViewDirection.UNKNOWN &&
                sideViewDirection != requiredSideViewDirection
            )
        )
        val showDirectionWarning = updateCameraDirectionWarning(directionMismatch, nowMs)

        if (ENABLE_DIRECTION_DEBUG_LOG) {
            Log.d("DIRECTION_DEBUG",
                "exerciseId=$currentExerciseId, requiredAngle=$requiredCameraAngle, " +
                "angle=$angle, reqDir=$requiredSideViewDirection, " +
                "detectedDir=$sideViewDirection, mismatch=$directionMismatch, " +
                "warning=$showDirectionWarning")
        }

        // Module 6: auto-pause check.
        val pauseState = endDetector.update(poseResult.landmarks, poseResult.visibilities, nowMs)
        val isPaused   = pauseState == TrainingPauseState.PAUSED

        if (isPaused && !wasTrainingPaused) {
            repScoreTracker.discardCurrentRep()
            userSequence.clear()
            countRepsUseCase.reset()
            lastMatchedReferenceIndex = -1
        }
        wasTrainingPaused = isPaused

        if (isPaused) {
            _uiState.update { state ->
                state.copy(
                    isFullBodyInFrame              = fullBody,
                    cameraAngle                     = angle,
                    isCameraDirectionWarningVisible = showDirectionWarning,
                    isTrainingPaused                = true,
                    cameraFrameWidth                = poseResult.imageWidth,
                    cameraFrameHeight               = poseResult.imageHeight,
                )
            }
            return
        }

        if (directionMismatch) {
            _uiState.update { state ->
                state.copy(
                    isFullBodyInFrame              = fullBody,
                    cameraAngle                     = angle,
                    isCameraDirectionWarningVisible = showDirectionWarning,
                    isTrainingPaused                = false,
                    cameraFrameWidth                = poseResult.imageWidth,
                    cameraFrameHeight               = poseResult.imageHeight,
                )
            }
            return
        }

        // Module 1: normalise.
        val normalized = normalizeLandmarks(poseResult.landmarks)
        userSequence.add(normalized)
        // Sliding window: keep OE-DTW cost O(MAX_USER_SEQUENCE_FRAMES × m).
        if (userSequence.size > MAX_USER_SEQUENCE_FRAMES) {
            userSequence.removeAt(0)
        }

        // Module 2: find matching reference frame.
        val dtwMatchedIdx = alignOeDtw(userSequence, referenceSequence)
        val matchedIdx = stabiliseMatchedReferenceIndex(dtwMatchedIdx, normalized)

        // Module 3: score and colour.
        val scoreResult = evaluateUseCase.evaluate(
            matchedIdx, normalized, referenceSequence,
            upperBodyOnly = !requiresFullBody,
        )

        // Module 4: accumulate per-frame score.
        val hasRedLimb = scoreResult.limbColors.any { it == Color.Red }
        repScoreTracker.addFrameScore(scoreResult.sf, hasRedLimb)

        // Module 4: rep detection.
        val repCompleted = countRepsUseCase.update(poseResult.landmarks, poseResult.visibilities)
        val updatedRepScores = if (repCompleted) {
            repScoreTracker.finishRep()
            userSequence.clear()
            lastMatchedReferenceIndex = -1
            repScoreTracker.getCompletedRepScores()
        } else {
            _uiState.value.repScores
        }

        // Blue ghost skeleton: always shown once DTW warms up (matchedIdx != -1),
        // regardless of whether form is correct. Repositioned to user's body centre.
        val matchedRaw = if (matchedIdx != -1)
            repositionReference(rawReferenceSequence[matchedIdx], poseResult.landmarks)
        else emptyList()

        // Push latest scoring colours to PoseFrameProcessor so the NEXT frame's
        // bitmap is drawn with up-to-date red/green highlighting.
        // The scoring engine only produces Color.Red or Color.Green, so we map
        // directly to Android ARGB ints — no Compose toArgb() import needed.
        poseFrameProcessor.updateColors(
            jointArgbColors = IntArray(scoreResult.jointColors.size) { i ->
                if (scoreResult.jointColors[i] == Color.Red) android.graphics.Color.RED
                else android.graphics.Color.GREEN
            },
            limbArgbColors = IntArray(scoreResult.limbColors.size) { i ->
                if (scoreResult.limbColors[i] == Color.Red) android.graphics.Color.RED
                else android.graphics.Color.GREEN
            }
        )

        _uiState.update { state ->
            state.copy(
                isFullBodyInFrame              = fullBody,
                cameraAngle                     = angle,
                isCameraDirectionWarningVisible = showDirectionWarning,
                jointColors                     = scoreResult.jointColors,
                limbColors                      = scoreResult.limbColors,
                currentFrameScore               = scoreResult.sf,
                matchedReferenceRawLandmarks    = matchedRaw,
                cameraFrameWidth                = poseResult.imageWidth,
                cameraFrameHeight               = poseResult.imageHeight,
                repCount                        = updatedRepScores.size,
                repScores                       = updatedRepScores,
                correctReps                     = repScoreTracker.correctReps,
                incorrectReps                   = repScoreTracker.incorrectReps,
                isTrainingPaused                = false,
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

    private fun stabiliseMatchedReferenceIndex(
        dtwMatchedIdx: Int,
        currentNormalized: List<Pair<Float, Float>>,
    ): Int {
        if (dtwMatchedIdx == -1 || referenceSequence.isEmpty()) return -1

        val lastIdx = lastMatchedReferenceIndex
        if (lastIdx == -1) {
            lastMatchedReferenceIndex = dtwMatchedIdx
            return dtwMatchedIdx
        }

        val searchStart = maxOf(0, lastIdx - REFERENCE_SEARCH_BACK_FRAMES)
        val searchEnd = minOf(
            referenceSequence.lastIndex,
            lastIdx + REFERENCE_SEARCH_FORWARD_FRAMES
        )
        val localBestIdx = (searchStart..searchEnd).minByOrNull { idx ->
            frameDist(currentNormalized, referenceSequence[idx])
        } ?: dtwMatchedIdx

        val candidate = if (dtwMatchedIdx in searchStart..searchEnd) {
            if (localBestIdx >= lastIdx - MAX_REFERENCE_BACKTRACK_FRAMES) localBestIdx else dtwMatchedIdx
        } else {
            localBestIdx
        }

        val smoothed = candidate.coerceIn(
            minimumValue = maxOf(0, lastIdx - MAX_REFERENCE_BACKTRACK_FRAMES),
            maximumValue = minOf(referenceSequence.lastIndex, lastIdx + MAX_REFERENCE_FORWARD_JUMP_FRAMES)
        )
        lastMatchedReferenceIndex = smoothed
        return smoothed
    }

    /**
     * Repositions raw reference landmarks onto the live user's body centre, scale,
     * and torso direction.
     */
    private fun repositionReference(
        refRaw: List<Triple<Float, Float, Float>>,
        userRaw: List<Triple<Float, Float, Float>>,
    ): List<Triple<Float, Float, Float>> {
        val normalizedRef = normalizeLandmarks(refRaw)

        val userHipMidX      = (userRaw[LANDMARK_LEFT_HIP].first      + userRaw[LANDMARK_RIGHT_HIP].first)      / 2f
        val userHipMidY      = (userRaw[LANDMARK_LEFT_HIP].second     + userRaw[LANDMARK_RIGHT_HIP].second)     / 2f
        val userShoulderMidX = (userRaw[LANDMARK_LEFT_SHOULDER].first  + userRaw[LANDMARK_RIGHT_SHOULDER].first)  / 2f
        val userShoulderMidY = (userRaw[LANDMARK_LEFT_SHOULDER].second + userRaw[LANDMARK_RIGHT_SHOULDER].second) / 2f
        val refShoulderMidX =
            (normalizedRef[LANDMARK_LEFT_SHOULDER].first + normalizedRef[LANDMARK_RIGHT_SHOULDER].first) / 2f
        val refShoulderMidY =
            (normalizedRef[LANDMARK_LEFT_SHOULDER].second + normalizedRef[LANDMARK_RIGHT_SHOULDER].second) / 2f

        val userTorsoLen = sqrt(
            (userShoulderMidX - userHipMidX) * (userShoulderMidX - userHipMidX) +
            (userShoulderMidY - userHipMidY) * (userShoulderMidY - userHipMidY)
        )
        val refTorsoLen = sqrt(
            refShoulderMidX * refShoulderMidX +
            refShoulderMidY * refShoulderMidY
        )

        if (userTorsoLen < 1e-6f || refTorsoLen < 1e-6f) return refRaw

        val refUnitX = refShoulderMidX / refTorsoLen
        val refUnitY = refShoulderMidY / refTorsoLen
        val userUnitX = (userShoulderMidX - userHipMidX) / userTorsoLen
        val userUnitY = (userShoulderMidY - userHipMidY) / userTorsoLen
        val cos = refUnitX * userUnitX + refUnitY * userUnitY
        val sin = refUnitX * userUnitY - refUnitY * userUnitX

        return normalizedRef.map { (nx, ny) ->
            val rotatedX = nx * cos - ny * sin
            val rotatedY = nx * sin + ny * cos
            Triple(
                rotatedX * userTorsoLen + userHipMidX,
                rotatedY * userTorsoLen + userHipMidY,
                0f,
            )
        }
    }
}

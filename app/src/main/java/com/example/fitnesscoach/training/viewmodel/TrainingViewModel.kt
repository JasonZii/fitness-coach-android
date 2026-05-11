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
import com.example.fitnesscoach.core.util.Constants.MAX_CONSECUTIVE_RED_FRAMES
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
import com.example.fitnesscoach.training.pose.isUpperBodyInFrame
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

// ═════════════════════════════════════════════════════════════════════════════
// 常量
// ═════════════════════════════════════════════════════════════════════════════

// 朝向不匹配多少毫秒后才显示橙色警告（宽限期，过滤瞬间转身）
private const val CAMERA_DIRECTION_WARNING_GRACE_MS = 1500L

// OE-DTW 滑动窗口：限制 userSequence 长度，避免计算量随时间线性增长
private const val MAX_USER_SEQUENCE_FRAMES = 18
// stabiliseMatchedReferenceIndex() 本地搜索范围
private const val REFERENCE_SEARCH_BACK_FRAMES = 4
private const val REFERENCE_SEARCH_FORWARD_FRAMES = 10
// stabiliseMatchedReferenceIndex() 最终夹紧范围
private const val MAX_REFERENCE_BACKTRACK_FRAMES = 2
private const val MAX_REFERENCE_FORWARD_JUMP_FRAMES = 6

// 朝向调试日志开关，正式使用时保持 false
private const val ENABLE_DIRECTION_DEBUG_LOG = false

// ═════════════════════════════════════════════════════════════════════════════
// 训练阶段枚举
// 被：TrainingUiState、TrainingScreen、onFrame()、processReadinessFrame()、
//     processTrainingFrame() 使用
// ═════════════════════════════════════════════════════════════════════════════

enum class SessionPhase {
    /** Waiting for user to stand in frame at the correct angle. */
    READINESS,
    /** Countdown finished; exercise reps are being recorded. */
    TRAINING,
    /** User pressed Stop or auto-end was triggered. */
    FINISHED,
}

// ═════════════════════════════════════════════════════════════════════════════
// UI 状态快照
// 写入方：processReadinessFrame()、processTrainingFrame()（均在 algorithmDispatcher）
// 读取方：TrainingScreen（主线程，通过 collectAsState() 订阅）
// ═════════════════════════════════════════════════════════════════════════════

/**
 * Algorithm results consumed by TrainingScreen.
 * Skeleton position is delivered separately via [TrainingViewModel.skeletonState]
 * so that it can update immediately after MediaPipe inference without waiting
 * for the full algorithm pipeline to complete.
 */
data class TrainingUiState(
    val phase: SessionPhase = SessionPhase.READINESS,
    val isReferenceLoaded: Boolean = false,

    // ── 准备阶段 ──────────────────────────────────────────────────────────────
    val readiness: ReadinessState = ReadinessState(ReadinessPhase.NOT_READY),
    val isFullBodyInFrame: Boolean = false,
    val cameraAngle: CameraAngle = CameraAngle.AMBIGUOUS,
    val requiredCameraAngle: CameraAngle = CameraAngle.SIDE,
    val requiresFullBody: Boolean = true,
    val isCameraDirectionWarningVisible: Boolean = false,

    // ── 训练评分 ──────────────────────────────────────────────────────────────
    // 注意：骨架颜色通过 poseFrameProcessor.updateColors() 直接烧入相机 Bitmap 显示；
    // 这里的 jointColors/limbColors 同时写入 uiState，供 TrainingScreen 读取
    val jointColors: List<Color> = List(LANDMARK_COUNT) { Color.Green },
    val limbColors: List<Color> = List(LIMB_COUNT) { Color.Green },
    val currentFrameScore: Float = 0f,

    // ── 蓝色参考骨架 ──────────────────────────────────────────────────────────
    val matchedReferenceRawLandmarks: List<Triple<Float, Float, Float>> = emptyList(),
    val dynamicReferenceLandmarks: List<Triple<Float, Float, Float>> = emptyList(), // 见 Block E（未启用）
    val cameraFrameWidth: Int = 0,
    val cameraFrameHeight: Int = 0,

    // ── Rep 计数 ───────────────────────────────────────────────────────────────
    val repCount: Int = 0,
    val repScores: List<Float> = emptyList(),
    val correctReps: Int = 0,
    val incorrectReps: Int = 0,

    // ── 训练中断 ──────────────────────────────────────────────────────────────
    val isTrainingPaused: Boolean = false,
)

// ═════════════════════════════════════════════════════════════════════════════
// ViewModel
// ═════════════════════════════════════════════════════════════════════════════

class TrainingViewModel(application: Application) : AndroidViewModel(application) {

    // ══════════════════════════════════════════════════════════════════════════
    // A1：公开状态流
    // 读取方：TrainingScreen（通过 collectAsState() 或直接访问）
    // ══════════════════════════════════════════════════════════════════════════

    private val _uiState = MutableStateFlow(TrainingUiState())
    val uiState: StateFlow<TrainingUiState> = _uiState.asStateFlow()

    // 骨架坐标快速路径：每帧 MediaPipe 推理后立刻写入，不等算法计算
    private val _skeletonFlow = MutableStateFlow<List<Triple<Float, Float, Float>>>(emptyList())
    val skeletonState: StateFlow<List<Triple<Float, Float, Float>>> = _skeletonFlow.asStateFlow()

    // 由 TrainingScreen 传给 CameraPreview 使用；
    // 由 processTrainingFrame() 通过 updateColors() 写入评分颜色
    val poseFrameProcessor = PoseFrameProcessor(application)

    // ══════════════════════════════════════════════════════════════════════════
    // A2：线程管理
    // ══════════════════════════════════════════════════════════════════════════

    // 单线程执行器：所有算法计算在此串行执行，不阻塞主线程或相机线程
    private val algorithmDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    // ══════════════════════════════════════════════════════════════════════════
    // A3：算法模块实例（生命周期与 ViewModel 一致）
    // ══════════════════════════════════════════════════════════════════════════

    private val readinessMachine = ReadinessStateMachine()   // 准备阶段：NOT_READY → COUNTDOWN → TRAINING_STARTED
    private val evaluateUseCase  = EvaluateExerciseUseCase() // 姿态评分：S1/S2/Sf + 红绿颜色
    private val repScoreTracker  = RepScoreTracker()          // Rep 得分追踪：正确/不正确分类
    private val endDetector      = TrainingEndDetector()      // 训练中断检测（Module 6）：低可见度 / 太近
    private var countRepsUseCase = CountRepsUseCase()         // Rep 计数状态机（var：切换运动时重新创建）
    private var currentExerciseId: String = "squat"

    // ══════════════════════════════════════════════════════════════════════════
    // A4：训练运行时状态（仅 algorithmDispatcher 单线程访问，无需 @Volatile）
    // ══════════════════════════════════════════════════════════════════════════

    private var wasTrainingPaused = false                    // 上一帧是否暂停，用于检测"刚进入暂停的第一帧"
    private var cameraDirectionMismatchSinceMs: Long? = null // 朝向不匹配开始时间戳（null = 当前无不匹配）
    private var lastMatchedReferenceIndex = -1               // 上一帧 DTW 匹配索引，用于 stabilise 平滑

    // ══════════════════════════════════════════════════════════════════════════
    // A5：参考序列数据
    // @Volatile：loadExercise() 的 IO 线程写入 / algorithmDispatcher 读取
    // ══════════════════════════════════════════════════════════════════════════

    @Volatile private var referenceSequence:    List<List<Triple<Float, Float, Float>>> = emptyList() // 归一化，供 DTW 和评分使用
    @Volatile private var rawReferenceSequence: List<List<Triple<Float, Float, Float>>> = emptyList() // 原始坐标，供蓝色骨架使用

    // 仅 algorithmDispatcher 单线程访问，无需 @Volatile
    private val userSequence = mutableListOf<List<Triple<Float, Float, Float>>>()

    // ══════════════════════════════════════════════════════════════════════════
    // A6：运动配置
    // @Volatile：loadExercise() 在主线程写入 / algorithmDispatcher 读取
    // ══════════════════════════════════════════════════════════════════════════

    @Volatile private var requiredCameraAngle: CameraAngle = CameraAngle.SIDE
    @Volatile private var requiresFullBody: Boolean = true
    @Volatile private var requiredSideViewDirection: SideViewDirection = SideViewDirection.NONE
    @Volatile private var readinessVisibilityMode: ReadinessVisibilityMode =
        ReadinessVisibilityMode.ANY_VISIBLE_SIDE

    // ══════════════════════════════════════════════════════════════════════════
    // A7：未启用功能的字段（动态参考骨架动画，见 Block E）
    // ══════════════════════════════════════════════════════════════════════════

    private var referenceFrameIndex = 0
    private var referenceJob: Job? = null
    private var referenceFrames: List<List<Triple<Float, Float, Float>>> = emptyList()

    // ══════════════════════════════════════════════════════════════════════════
    // B1：loadExercise()
    // 调用方：TrainingScreen.LaunchedEffect（主线程，页面打开或切换运动时）
    // 调用：algorithmDispatcher 协程重置所有模块；IO 协程读取参考 JSON
    // 作用：用户选定运动后初始化所有状态；并行启动算法重置和数据加载两个协程
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Loads reference data for [exerciseId] and fully resets session state.
     * Safe to call multiple times (e.g. "Try Again").
     */
    fun loadExercise(exerciseId: String) {
        val info = exerciseList.find { it.id == exerciseId } ?: exerciseList.first()

        // @Volatile 字段立刻写入，确保 algorithmDispatcher 下一帧就能读到新值
        requiredCameraAngle       = info.requiredCameraAngle
        requiresFullBody          = info.requiresFullBody
        requiredSideViewDirection = info.requiredSideViewDirection
        readinessVisibilityMode   = info.readinessVisibilityMode
        currentExerciseId         = info.id

        _uiState.value = TrainingUiState(
            requiredCameraAngle = info.requiredCameraAngle,
            requiresFullBody    = info.requiresFullBody,
            isReferenceLoaded   = false,
        )
        _skeletonFlow.value = emptyList()

        // 在 algorithmDispatcher 上重置，保证与飞行中的帧处理协程串行执行
        viewModelScope.launch(algorithmDispatcher) {
            readinessMachine.reset()
            endDetector.reset()
            repScoreTracker.reset()
            userSequence.clear()
            countRepsUseCase               = CountRepsUseCase(info.id)
            wasTrainingPaused              = false
            lastMatchedReferenceIndex      = -1
            cameraDirectionMismatchSinceMs = null
        }

        // IO 线程读取 JSON 并归一化，完成后切回主线程写入 @Volatile 变量
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

    // ══════════════════════════════════════════════════════════════════════════
    // B2：onFrame()
    // 调用方：CameraPreview.onPoseDetected（CameraX analysisExecutor 线程，每帧调用）
    // 调用：processReadinessFrame()、processTrainingFrame()（均在 algorithmDispatcher）
    // 作用：每帧算法入口；快速路径立刻更新骨架坐标，慢速路径异步执行所有算法
    // ══════════════════════════════════════════════════════════════════════════

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
        // MediaPipe 未检测到人体（输出为空）：仅在准备阶段重置状态机
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
                            matchedReferenceRawLandmarks = emptyList(),
                            cameraFrameWidth = poseResult.imageWidth,
                            cameraFrameHeight = poseResult.imageHeight,
                        )
                    }
                }
            }
            return
        }

        // 快速路径：原子写入，Compose 立刻感知，骨架位置零延迟更新
        _skeletonFlow.value = poseResult.landmarks

        // 慢速路径：所有算法计算在专用后台线程，不阻塞相机帧采集
        viewModelScope.launch(algorithmDispatcher) {
            when (_uiState.value.phase) {
                SessionPhase.READINESS -> processReadinessFrame(poseResult)
                SessionPhase.TRAINING  -> processTrainingFrame(poseResult)
                SessionPhase.FINISHED  -> Unit
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // B3：stopTraining()
    // 调用方：TrainingScreen.onStop（主线程，用户点击 Stop Training 按钮）
    // 调用：stopDynamicReferenceSkeleton()；algorithmDispatcher 协程重置所有模块
    // 作用：阶段切换到 FINISHED，清空所有算法状态
    //        注意：TrainingScreen 在调用此函数前已快照 uiState 数据用于存库
    // ══════════════════════════════════════════════════════════════════════════

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

    // ══════════════════════════════════════════════════════════════════════════
    // B4：onCleared()
    // 调用方：Android 系统（ViewModel 即将销毁时自动调用）
    // 作用：释放 MediaPipe GPU/CPU 资源；关闭算法线程执行器
    // ══════════════════════════════════════════════════════════════════════════

    override fun onCleared() {
        super.onCleared()
        poseFrameProcessor.close()
        algorithmDispatcher.close()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // C1：processReadinessFrame()
    // 调用方：onFrame() 慢速路径，仅在 READINESS 阶段，运行于 algorithmDispatcher
    // 调用：isCameraAngleValidForReadiness()、detectCameraAngle()、
    //        readinessMachine.update()、endDetector.onTrainingStart()（阶段转换时）
    // 作用：检查入画 + 视角，驱动准备状态机；倒计时结束后触发训练开始
    // ══════════════════════════════════════════════════════════════════════════

    // 运行于 algorithmDispatcher。骨架坐标更新由 onFrame() 快速路径处理，此处不重复。
    private fun processReadinessFrame(poseResult: PoseResult) {
        val fullBody = if (requiresFullBody)
            isFullBodyInFrame(poseResult.visibilities, readinessVisibilityMode)
        else
            isUpperBodyInFrame(poseResult.visibilities, readinessVisibilityMode)
        val angle    = detectCameraAngle(poseResult.landmarks)

        val conditionsMet = fullBody && isCameraAngleValidForReadiness(angle)
        val readiness     = readinessMachine.update(conditionsMet, System.currentTimeMillis())

        val nextPhase = if (readiness.phase == ReadinessPhase.TRAINING_STARTED)
            SessionPhase.TRAINING else SessionPhase.READINESS

        // 阶段转换时通知 endDetector 记录基准肩宽（用于"太近"检测）
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

    // ══════════════════════════════════════════════════════════════════════════
    // C2：isCameraAngleValidForReadiness()
    // 调用方：processReadinessFrame()、processTrainingFrame()
    // 作用：判断当前视角是否满足运动要求
    //        特殊情况：上半身正面运动（如肩推、肱二头肌弯举）不严格检查视角
    // ══════════════════════════════════════════════════════════════════════════

    private fun isCameraAngleValidForReadiness(angle: CameraAngle): Boolean {
        if (!requiresFullBody && requiredCameraAngle == CameraAngle.FRONT) return true
        return angle == requiredCameraAngle
    }

    // ══════════════════════════════════════════════════════════════════════════
    // D1：processTrainingFrame()
    // 调用方：onFrame() 慢速路径，仅在 TRAINING 阶段，运行于 algorithmDispatcher
    // 调用（按执行顺序）：
    //   isCameraAngleValidForReadiness()
    //   → detectSideViewDirection()
    //   → updateCameraDirectionWarning()          [D2]
    //   → endDetector.update()
    //   → normalizeLandmarks()                    [Module 1]
    //   → alignOeDtw()                            [Module 2]
    //   → stabiliseMatchedReferenceIndex()        [Module 2, D3]
    //   → evaluateUseCase.evaluate()              [Module 3]
    //   → repScoreTracker + countRepsUseCase      [Module 4]
    //   → repositionReference()                   [D4]
    //   → poseFrameProcessor.updateColors()
    // 作用：训练阶段每帧总调度；先检查四种中断条件，通过后执行完整算法流水线
    // ══════════════════════════════════════════════════════════════════════════

    // 运行于 algorithmDispatcher。骨架坐标更新由 onFrame() 快速路径处理，此处不重复。
    private fun processTrainingFrame(poseResult: PoseResult) {
        if (!_uiState.value.isReferenceLoaded) return

        // 中断时用于重置 UI 颜色和相机 Bitmap 骨架颜色为全绿
        val allGreenJointColors = List(LANDMARK_COUNT) { Color.Green }
        val allGreenLimbColors = List(LIMB_COUNT) { Color.Green }

        fun clearCachedSkeletonColors() {
            poseFrameProcessor.updateColors(
                jointArgbColors = IntArray(LANDMARK_COUNT) { android.graphics.Color.GREEN },
                limbArgbColors = IntArray(LIMB_COUNT) { android.graphics.Color.GREEN },
            )
        }

        val angle = detectCameraAngle(poseResult.landmarks)
        val fullBody = if (requiresFullBody)
            isFullBodyInFrame(poseResult.visibilities, readinessVisibilityMode)
        else
            isUpperBodyInFrame(poseResult.visibilities, readinessVisibilityMode)
        val userIsValid = fullBody && isCameraAngleValidForReadiness(angle)

        // ── 中断2：身体不在画面 / 视角不对 → 暂停；userSequence 保留不清空 ──────
        if (!userIsValid) {
            _uiState.update { state ->
                state.copy(
                    isFullBodyInFrame            = fullBody,
                    cameraAngle                  = angle,
                    isTrainingPaused             = true,
                    jointColors                  = allGreenJointColors,
                    limbColors                   = allGreenLimbColors,
                    matchedReferenceRawLandmarks = emptyList(),
                    cameraFrameWidth             = poseResult.imageWidth,
                    cameraFrameHeight            = poseResult.imageHeight,
                )
            }
            clearCachedSkeletonColors()
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
        val showDirectionWarning = updateCameraDirectionWarning(directionMismatch, nowMs) // [D2]

        if (ENABLE_DIRECTION_DEBUG_LOG) {
            Log.d("DIRECTION_DEBUG",
                "exerciseId=$currentExerciseId, requiredAngle=$requiredCameraAngle, " +
                "angle=$angle, reqDir=$requiredSideViewDirection, " +
                "detectedDir=$sideViewDirection, mismatch=$directionMismatch, " +
                "warning=$showDirectionWarning")
        }

        // ── 中断3：TrainingEndDetector 判定暂停（低可见度 / 太近）─────────────────
        // 刚进入暂停的第一帧：清空 DTW 状态，恢复后需重新热身
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
                    isFullBodyInFrame               = fullBody,
                    cameraAngle                     = angle,
                    isCameraDirectionWarningVisible = showDirectionWarning,
                    isTrainingPaused                = true,
                    jointColors                     = allGreenJointColors,
                    limbColors                      = allGreenLimbColors,
                    matchedReferenceRawLandmarks    = emptyList(),
                    cameraFrameWidth                = poseResult.imageWidth,
                    cameraFrameHeight               = poseResult.imageHeight,
                )
            }
            clearCachedSkeletonColors()
            return
        }

        // ── 中断4：朝向不匹配 → 跳过算法；userSequence 保留不清空 ────────────────
        if (directionMismatch) {
            _uiState.update { state ->
                state.copy(
                    isFullBodyInFrame               = fullBody,
                    cameraAngle                     = angle,
                    isCameraDirectionWarningVisible = showDirectionWarning,
                    isTrainingPaused                = false,
                    jointColors                     = allGreenJointColors,
                    limbColors                      = allGreenLimbColors,
                    matchedReferenceRawLandmarks    = emptyList(),
                    cameraFrameWidth                = poseResult.imageWidth,
                    cameraFrameHeight               = poseResult.imageHeight,
                )
            }
            clearCachedSkeletonColors()
            return
        }

        // ── Module 1：归一化 ─────────────────────────────────────────────────────
        val normalized = normalizeLandmarks(poseResult.landmarks)
        userSequence.add(normalized)
        // 滑动窗口：保持 DTW 计算量为 O(MAX_USER_SEQUENCE_FRAMES × 参考序列长度)
        if (userSequence.size > MAX_USER_SEQUENCE_FRAMES) {
            userSequence.removeAt(0)
        }

        // ── Module 2：OE-DTW 对齐 + 平滑 [D3] ──────────────────────────────────
        val dtwMatchedIdx = alignOeDtw(userSequence, referenceSequence)
        val matchedIdx    = stabiliseMatchedReferenceIndex(dtwMatchedIdx, normalized)

        // ── Module 3：姿态评分 + 红绿颜色 ───────────────────────────────────────
        val scoreResult = evaluateUseCase.evaluate(
            matchedIdx, normalized, referenceSequence,
            upperBodyOnly = !requiresFullBody,
            exerciseId    = currentExerciseId,
        )

        // ── Module 4：Rep 得分累计 ────────────────────────────────────────────────
        val hasRedLimb = scoreResult.limbColors.any { it == Color.Red }
        repScoreTracker.addFrameScore(scoreResult.sf, hasRedLimb)
        // 延迟显示红色反馈：连续红帧数超过阈值才对用户展示红色，避免瞬间误判闪烁
        val shouldShowRed =
            repScoreTracker.currentConsecutiveRedFrames > MAX_CONSECUTIVE_RED_FRAMES
        val visualJointColors =
            if (shouldShowRed) scoreResult.jointColors
            else allGreenJointColors
        val visualLimbColors =
            if (shouldShowRed) scoreResult.limbColors
            else allGreenLimbColors

        // ── Module 4：Rep 计数检测 ────────────────────────────────────────────────
        val repCompleted = countRepsUseCase.update(poseResult.landmarks, poseResult.visibilities)
        val updatedRepScores = if (repCompleted) {
            repScoreTracker.finishRep()
            userSequence.clear()        // Rep 完成后清空，让 DTW 重新定位到参考序列起点
            lastMatchedReferenceIndex = -1
            repScoreTracker.getCompletedRepScores()
        } else {
            _uiState.value.repScores
        }

        // ── 蓝色参考骨架坐标 [D4] ────────────────────────────────────────────────
        // DTW 热身完成（matchedIdx != -1）后才显示，坐标重新定位到用户身体位置
        val matchedRaw = if (matchedIdx != -1)
            repositionReference(rawReferenceSequence[matchedIdx], poseResult.landmarks)
        else emptyList()

        // ── 把本帧评分颜色推给 PoseFrameProcessor，供下一帧绘制骨架时使用 ──────────
        // 颜色在下一帧生效（一帧延迟），这是避免阻塞相机线程的刻意设计
        poseFrameProcessor.updateColors(
            jointArgbColors = IntArray(visualJointColors.size) { i ->
                if (visualJointColors[i] == Color.Red) android.graphics.Color.RED
                else android.graphics.Color.GREEN
            },
            limbArgbColors = IntArray(visualLimbColors.size) { i ->
                if (visualLimbColors[i] == Color.Red) android.graphics.Color.RED
                else android.graphics.Color.GREEN
            }
        )

        _uiState.update { state ->
            state.copy(
                isFullBodyInFrame               = fullBody,
                cameraAngle                     = angle,
                isCameraDirectionWarningVisible = showDirectionWarning,
                jointColors                     = visualJointColors,
                limbColors                      = visualLimbColors,
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

    // ══════════════════════════════════════════════════════════════════════════
    // D2：updateCameraDirectionWarning()
    // 调用方：processTrainingFrame() [D1]
    // 作用：判断朝向不匹配是否已持续超过 1500ms 宽限期
    //        不匹配时开始计时；恢复正确朝向时重置计时器；超过宽限期才返回 true
    // ══════════════════════════════════════════════════════════════════════════

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

    // ══════════════════════════════════════════════════════════════════════════
    // D3：stabiliseMatchedReferenceIndex()
    // 调用方：processTrainingFrame() [D1]，Module 2 alignOeDtw() 之后
    // 调用：frameDist()（来自 AlignOeDtw.kt）
    // 作用：对 DTW 原始结果做本地窗口搜索 + 硬夹紧，防止蓝色骨架帧间跳变
    // ══════════════════════════════════════════════════════════════════════════

    private fun stabiliseMatchedReferenceIndex(
        dtwMatchedIdx: Int,
        currentNormalized: List<Triple<Float, Float, Float>>,
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

    // ══════════════════════════════════════════════════════════════════════════
    // D4：repositionReference()
    // 调用方：processTrainingFrame() [D1]，matchedIdx 确定后，生成蓝色骨架坐标
    // 调用：normalizeLandmarks()
    // 作用：把参考帧坐标缩放到用户躯干尺寸、旋转到用户躯干方向、平移到用户髋部位置
    //        输出即蓝色参考骨架在屏幕上的实际绘制坐标（归一化 [0,1] 坐标系）
    // ══════════════════════════════════════════════════════════════════════════

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
        val refShoulderMidX  =
            (normalizedRef[LANDMARK_LEFT_SHOULDER].first  + normalizedRef[LANDMARK_RIGHT_SHOULDER].first)  / 2f
        val refShoulderMidY  =
            (normalizedRef[LANDMARK_LEFT_SHOULDER].second + normalizedRef[LANDMARK_RIGHT_SHOULDER].second) / 2f

        val userTorsoLen = sqrt(
            (userShoulderMidX - userHipMidX) * (userShoulderMidX - userHipMidX) +
            (userShoulderMidY - userHipMidY) * (userShoulderMidY - userHipMidY)
        )
        val refTorsoLen = sqrt(
            refShoulderMidX * refShoulderMidX +
            refShoulderMidY * refShoulderMidY
        )

        // 躯干长度接近零时（极端异常），直接返回原始坐标避免除以零
        if (userTorsoLen < 1e-6f || refTorsoLen < 1e-6f) return refRaw

        val refUnitX  = refShoulderMidX / refTorsoLen
        val refUnitY  = refShoulderMidY / refTorsoLen
        val userUnitX = (userShoulderMidX - userHipMidX) / userTorsoLen
        val userUnitY = (userShoulderMidY - userHipMidY) / userTorsoLen
        val cos = refUnitX * userUnitX + refUnitY * userUnitY
        val sin = refUnitX * userUnitY - refUnitY * userUnitX

        return normalizedRef.map { (nx, ny, _) ->
            val rotatedX = nx * cos - ny * sin
            val rotatedY = nx * sin + ny * cos
            Triple(
                rotatedX * userTorsoLen + userHipMidX,
                rotatedY * userTorsoLen + userHipMidY,
                0f,
            )
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // E：动态参考骨架动画（当前未启用）
    // ──────────────────────────────────────────────────────────────────────────
    // 设计意图：在准备阶段循环播放参考动作预览（10fps），让用户提前了解动作
    // 当前状态：startDynamicReferenceSkeleton() 从未被调用
    //           stopDynamicReferenceSkeleton() 在 stopTraining() 中调用（无实际效果）
    //           uiState.dynamicReferenceLandmarks 写入但 TrainingScreen 未读取
    // 相关字段：referenceFrameIndex、referenceJob、referenceFrames（见 A7）
    // ══════════════════════════════════════════════════════════════════════════

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
}

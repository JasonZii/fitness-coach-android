package com.example.fitnesscoach.training.viewmodel

import android.app.Application
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitnesscoach.core.mediapipe.PoseResult
import com.example.fitnesscoach.exercise.data.exerciseList
import com.example.fitnesscoach.core.util.Constants.LANDMARK_COUNT
import com.example.fitnesscoach.core.util.Constants.LANDMARK_LEFT_ANKLE
import com.example.fitnesscoach.core.util.Constants.LANDMARK_LEFT_ELBOW
import com.example.fitnesscoach.core.util.Constants.LANDMARK_LEFT_HIP
import com.example.fitnesscoach.core.util.Constants.LANDMARK_LEFT_KNEE
import com.example.fitnesscoach.core.util.Constants.LANDMARK_LEFT_SHOULDER
import com.example.fitnesscoach.core.util.Constants.LANDMARK_LEFT_WRIST
import com.example.fitnesscoach.core.util.Constants.LANDMARK_RIGHT_ANKLE
import com.example.fitnesscoach.core.util.Constants.LANDMARK_RIGHT_ELBOW
import com.example.fitnesscoach.core.util.Constants.LANDMARK_RIGHT_HIP
import com.example.fitnesscoach.core.util.Constants.LANDMARK_RIGHT_KNEE
import com.example.fitnesscoach.core.util.Constants.LANDMARK_RIGHT_SHOULDER
import com.example.fitnesscoach.core.util.Constants.LANDMARK_RIGHT_WRIST
import com.example.fitnesscoach.core.util.Constants.LIMB_COUNT
import com.example.fitnesscoach.core.util.Constants.VISIBILITY_IN_FRAME_MIN
import kotlin.math.sqrt
import com.example.fitnesscoach.training.core.PoseScoringEngine
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
import com.example.fitnesscoach.training.pose.TrainingEndState
import com.example.fitnesscoach.training.pose.advanceOeDtw
import com.example.fitnesscoach.training.pose.detectCameraAngle
import com.example.fitnesscoach.training.pose.detectSideViewDirection
import com.example.fitnesscoach.training.pose.frameDist
import com.example.fitnesscoach.training.pose.isFullBodyInFrame
import com.example.fitnesscoach.training.pose.isUpperBodyInFrame
import com.example.fitnesscoach.training.pose.normalizeLandmarks
import android.os.SystemClock
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
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import com.example.fitnesscoach.training.pose.PoseFrameProcessor

// 朝向不匹配多少毫秒后才显示橙色警告（宽限期，过滤瞬间转身）
private const val CAMERA_DIRECTION_WARNING_GRACE_MS = 1500L
// 用户不满足入画/视角要求多少毫秒后才显示暂停横幅（宽限期，过滤瞬间误判）
private const val PAUSE_GRACE_MS = 1500L

// stabiliseMatchedReferenceIndex() 本地搜索范围
private const val REFERENCE_SEARCH_BACK_FRAMES = 4
private const val REFERENCE_SEARCH_FORWARD_FRAMES = 10
// stabiliseMatchedReferenceIndex() 夹紧范围
private const val MAX_REFERENCE_BACKTRACK_FRAMES = 2
private const val MAX_REFERENCE_FORWARD_JUMP_FRAMES = 6
private const val BODY_LENGTH_EMA_ALPHA = 0.15f

enum class SessionPhase {
    /** Waiting for user to stand in frame at the correct angle. */
    READINESS,

    /** Countdown finished; exercise reps are being recorded. */
    TRAINING,

    /** User pressed Stop or auto-end was triggered. */
    FINISHED,
}

data class TrainingUiState(
    val phase: SessionPhase = SessionPhase.READINESS,
    val isReferenceLoaded: Boolean = false,

    // 准备阶段
    val readiness: ReadinessState = ReadinessState(ReadinessPhase.NOT_READY),
    val isBodyInFrame: Boolean = false,
    val cameraAngle: CameraAngle = CameraAngle.AMBIGUOUS,
    val requiredCameraAngle: CameraAngle = CameraAngle.SIDE,
    val requiresFullBody: Boolean = true,
    val isCameraDirectionWarningVisible: Boolean = false,
    val sideViewDirection: SideViewDirection = SideViewDirection.UNKNOWN,
    val requiredSideViewDirection: SideViewDirection = SideViewDirection.NONE,

    // 训练评分
    val currentFrameScore: Float = 0f,

    // 蓝色参考骨架
    val matchedReferenceRawLandmarks: List<Triple<Float, Float, Float>> = emptyList(),
    val cameraFrameWidth: Int = 0,
    val cameraFrameHeight: Int = 0,

    // Rep 计数
    val repCount: Int = 0,
    val repScores: List<Float> = emptyList(),
    val correctReps: Int = 0,
    val incorrectReps: Int = 0,

    // 训练中断
    val isTrainingPaused: Boolean = false,
)


class TrainingViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(TrainingUiState())
    val uiState: StateFlow<TrainingUiState> = _uiState.asStateFlow()

    // 骨架坐标快速路径：每帧 MediaPipe 推理后立刻写入，不等算法计算
    private val _skeletonFlow = MutableStateFlow<List<Triple<Float, Float, Float>>>(emptyList())
    val skeletonState: StateFlow<List<Triple<Float, Float, Float>>> = _skeletonFlow.asStateFlow()

    // 由 TrainingScreen 传给 CameraPreview 使用；
    // 由 processTrainingFrame() 通过 updateColors() 写入评分颜色
    val poseFrameProcessor = PoseFrameProcessor(application)

    // 单线程执行器：所有算法计算在此串行执行，不阻塞主线程或相机线程
    private val algorithmDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()


    // 帧守卫：算法线程仍在处理上一帧时丢弃新帧，防止队列积压导致蓝骨架严重滞后。
    // compareAndSet(false, true) 是原子操作；finally 块保证无论正常/异常都会归零。
    private val isAlgorithmFrameInFlight = AtomicBoolean(false)

    private val readinessMachine =
        ReadinessStateMachine()   // 准备阶段：NOT_READY → COUNTDOWN → TRAINING_STARTED
    private val evaluateUseCase = EvaluateExerciseUseCase() // 姿态评分：S1/S2/Sf + 红绿颜色
    private val repScoreTracker = RepScoreTracker()          // Rep 得分追踪：正确/不正确分类
    private val endDetector = TrainingEndDetector()      // 训练中断检测（Module 6）：低可见度 / 太近
    private var countRepsUseCase = CountRepsUseCase()         // Rep 计数状态机（var：切换运动时重新创建）
    // [Bug B 修复] countRepsUseCase 的专属锁：保护来自相机线程（onFrame）和算法线程
    // （loadExercise/stopTraining）的并发访问。不能用 synchronized(countRepsUseCase)，
    // 因为 loadExercise() 会把 countRepsUseCase 替换成新实例，锁对象会变，导致锁失效。
    private val repCountLock = Any()
    private var currentExerciseId: String = "squat"

    private var directionMismatchStartedAt: Long? = null // 朝向不匹配开始时间戳（null = 当前无不匹配）
    private var invalidUserStartedAt: Long? = null       // 用户首次"不满足入画/视角"时间戳（null = 当前有效）
    private var lastMatchedReferenceIndex = -1               // 上一帧 DTW 匹配索引，用于 stabilise 平滑
    private var oeDtwRow: FloatArray? = null                 // 增量 OE-DTW 持久化 DP 行；null = 本 rep 首帧
    private var oeDtwFrameCount = 0                         // 当前 rep 已处理帧数，用于热身期保护
    private val smoothedSegmentLengths = mutableMapOf<Pair<Int, Int>, Float>() // EMA 平滑后的各肢段长度

    @Volatile
    private var referenceSequence: List<List<Triple<Float, Float, Float>>> =
        emptyList() // 归一化，供 DTW 和评分使用
    @Volatile
    private var rawReferenceSequence: List<List<Triple<Float, Float, Float>>> =
        emptyList() // 原始坐标，供蓝色骨架使用
    @Volatile private var trainingStartedAtMs: Long? = null  // 训练开始时间戳，用于计算训练时长


    // Written by TrainingScreen when the user toggles the "Blue skeleton" switch.
    @Volatile var showRefSkeleton: Boolean = true

    @Volatile
    private var requiredCameraAngle: CameraAngle = CameraAngle.SIDE
    @Volatile
    private var requiresFullBody: Boolean = true
    @Volatile
    private var requiredSideViewDirection: SideViewDirection = SideViewDirection.NONE
    @Volatile
    private var readinessVisibilityMode: ReadinessVisibilityMode =
        ReadinessVisibilityMode.ANY_VISIBLE_SIDE


    fun loadExercise(exerciseId: String) {
        val info = exerciseList.find { it.id == exerciseId } ?: exerciseList.first()

        // @Volatile 字段立刻写入，确保 algorithmDispatcher 下一帧就能读到新值
        requiredCameraAngle = info.requiredCameraAngle
        requiresFullBody = info.requiresFullBody
        requiredSideViewDirection = info.requiredSideViewDirection
        readinessVisibilityMode = info.readinessVisibilityMode
        currentExerciseId = info.id

        _uiState.value = TrainingUiState(
            requiredCameraAngle = info.requiredCameraAngle,
            requiresFullBody = info.requiresFullBody,
            isReferenceLoaded = false,
            requiredSideViewDirection = info.requiredSideViewDirection,
        )
        _skeletonFlow.value = emptyList()
        poseFrameProcessor.updateReferenceSkeleton(emptyList())

        // 在 algorithmDispatcher 上重置，保证与飞行中的帧处理协程串行执行
        viewModelScope.launch(algorithmDispatcher) {
            readinessMachine.reset()
            endDetector.reset()
            repScoreTracker.reset()
            oeDtwRow = null
            oeDtwFrameCount = 0
            synchronized(smoothedSegmentLengths) {
                smoothedSegmentLengths.clear()
            }
            // [Bug B 修复] 用 repCountLock 保护赋值，防止相机线程同时在 onFrame() 中读取旧实例。
            // 如需回滚，删除 synchronized 块并恢复: countRepsUseCase = CountRepsUseCase(info.id)
            synchronized(repCountLock) {
                countRepsUseCase = CountRepsUseCase(info.id)
            }
            lastMatchedReferenceIndex  = -1
            directionMismatchStartedAt = null
        }

        // IO 线程读取 JSON 并归一化，完成后切回主线程写入 @Volatile 变量
        viewModelScope.launch(Dispatchers.IO) {
            val rawSeq = loadRawReferenceSequence(getApplication(), info.jsonFileName)
            // Filter degenerate frames from both lists together to keep indices aligned.
            val frames = rawSeq.mapNotNull { raw ->
                normalizeLandmarks(raw, requiredCameraAngle, requiresFullBody)?.let { raw to it }
            }
            withContext(Dispatchers.Main) {
                rawReferenceSequence = frames.map { it.first }
                referenceSequence = frames.map { it.second }
                _uiState.update {
                    it.copy(isReferenceLoaded = true)
                }
            }
        }
    }
    
    fun onFrame(poseResult: PoseResult) {
        // MediaPipe 未检测到人体（输出为空）：仅在准备阶段重置状态机
        if (poseResult.landmarks.size != LANDMARK_COUNT) {
            viewModelScope.launch(algorithmDispatcher) {
                if (_uiState.value.phase == SessionPhase.READINESS) {
                    val readiness = readinessMachine.update(
                        conditionsMet = false,
                        currentTimestamp = SystemClock.elapsedRealtime(),
                    )
                    _skeletonFlow.value = emptyList()
                    poseFrameProcessor.updateReferenceSkeleton(emptyList())
                    _uiState.update { state ->
                        state.copy(
                            isBodyInFrame = false,
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
        updateReferenceSkeletonForCurrentBody(poseResult)

        // [Bug B 修复] Rep 计数提前到帧守卫之前，确保每帧都推进状态机，不受丢帧影响。
        // 背景：原来 countRepsUseCase.update() 位于 processTrainingFrame() 内，当算法线程
        // 仍在处理上一帧时（isAlgorithmFrameInFlight=true），新帧整体被丢弃，状态机不推进。
        // 若 S3 峰值帧（如 bicep_curl 卷曲顶点）被丢弃，hasVisitedS3 永远不会被置 true，
        // 导致 rep 不计数。update() 本身仅做能见度检查+角度计算(<0.1ms)，适合在相机线程执行。
        // 如需完整回滚 Bug B 修复：删除此块，并在 processTrainingFrame() 中恢复被注释的代码。
        if (_uiState.value.phase == SessionPhase.TRAINING) {
            val repCompleted: Boolean
            // repCountLock 保证与 algorithmDispatcher 上的 loadExercise()/stopTraining() 互斥
            synchronized(repCountLock) {
                repCompleted = countRepsUseCase.update(poseResult.landmarks, poseResult.visibilities)
            }
            if (repCompleted) {
                // rep 完成的收尾（finishRep、DTW 重置、UI 更新）必须在 algorithmDispatcher 上
                // 串行执行，保证与 processTrainingFrame() 不竞争 repScoreTracker/oeDtwRow。
                viewModelScope.launch(algorithmDispatcher) {
                    repScoreTracker.finishRep()
                    oeDtwRow = null             // 让 DTW 下一 rep 从参考序列起点重新对齐
                    oeDtwFrameCount = 0
                    lastMatchedReferenceIndex = -1
                    _uiState.update { s: TrainingUiState ->
                        s.copy(
                            repCount      = repScoreTracker.getCompletedRepScores().size,
                            repScores     = repScoreTracker.getCompletedRepScores(),
                            correctReps   = repScoreTracker.correctReps,
                            incorrectReps = repScoreTracker.incorrectReps,
                        )
                    }
                }
            }
        }

        // 慢速路径：所有算法计算在专用后台线程，不阻塞相机帧采集
        if (!isAlgorithmFrameInFlight.compareAndSet(false, true)) return
        viewModelScope.launch(algorithmDispatcher) {
            try {
                when (_uiState.value.phase) {
                    SessionPhase.READINESS -> processReadinessFrame(poseResult)
                    SessionPhase.TRAINING  -> processTrainingFrame(poseResult)
                    SessionPhase.FINISHED  -> Unit
                }
            } finally {
                isAlgorithmFrameInFlight.set(false)
            }
        }
    }

    fun stopTraining() {
        _uiState.update { it.copy(phase = SessionPhase.FINISHED) }
        _skeletonFlow.value = emptyList()
        poseFrameProcessor.updateReferenceSkeleton(emptyList())

        viewModelScope.launch(algorithmDispatcher) {
            readinessMachine.reset()
            endDetector.reset()
            repScoreTracker.reset()
            oeDtwRow = null
            oeDtwFrameCount = 0
            synchronized(smoothedSegmentLengths) {
                smoothedSegmentLengths.clear()
            }
            // [Bug B 修复] 用 repCountLock 保护 reset()，防止相机线程同时在 onFrame() 中调用 update()。
            // 如需回滚，删除 synchronized 块并恢复: countRepsUseCase.reset()
            synchronized(repCountLock) {
                countRepsUseCase.reset()
            }
            lastMatchedReferenceIndex  = -1
            directionMismatchStartedAt = null
            trainingStartedAtMs        = null
        }
    }

    fun getTrainingDurationSeconds(nowMs: Long = System.currentTimeMillis()): Long {
        val startedAt = trainingStartedAtMs ?: return 0L
        return max(0L, (nowMs - startedAt) / 1000L)
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

    // 运行于 algorithmDispatcher。骨架坐标更新由 onFrame() 快速路径处理，此处不重复。
    private fun processReadinessFrame(poseResult: PoseResult) {
        val bodyInFrame = if (requiresFullBody)
            isFullBodyInFrame(poseResult.visibilities, readinessVisibilityMode)
        else
            isUpperBodyInFrame(poseResult.visibilities, readinessVisibilityMode)
        val angle = detectCameraAngle(poseResult.landmarks)

        val sideViewDirection = if (angle == CameraAngle.SIDE)
            detectSideViewDirection(poseResult.landmarks)
        else SideViewDirection.UNKNOWN

        val sideViewDirectionMatched = requiredSideViewDirection == SideViewDirection.NONE ||
                sideViewDirection == SideViewDirection.UNKNOWN ||
                sideViewDirection == requiredSideViewDirection

        val conditionsMet = bodyInFrame && isCameraAngleValid(angle) && sideViewDirectionMatched
        val readiness = readinessMachine.update(conditionsMet, SystemClock.elapsedRealtime())

        val nextPhase = if (readiness.phase == ReadinessPhase.TRAINING_STARTED)
            SessionPhase.TRAINING else SessionPhase.READINESS

        // 阶段转换时通知 endDetector 记录基准肩宽（用于"太近"检测）
        if (nextPhase == SessionPhase.TRAINING) {
            if (trainingStartedAtMs == null) {
                trainingStartedAtMs = System.currentTimeMillis()
            }
            endDetector.onTrainingStart(poseResult.landmarks, requiredCameraAngle)
        }

        _uiState.update { state ->
            state.copy(
                isBodyInFrame = bodyInFrame,
                cameraAngle = angle,
                sideViewDirection = sideViewDirection,
                readiness = readiness,
                phase = nextPhase,
                cameraFrameWidth = poseResult.imageWidth,
                cameraFrameHeight = poseResult.imageHeight,
            )
        }
    }

    private fun isCameraAngleValid(angle: CameraAngle): Boolean {
        if (!requiresFullBody && requiredCameraAngle == CameraAngle.FRONT) return true
        return angle == requiredCameraAngle
    }

    // 运行于 algorithmDispatcher。骨架坐标更新由 onFrame() 快速路径处理，此处不重复。
    private fun processTrainingFrame(poseResult: PoseResult) {
        if (!_uiState.value.isReferenceLoaded) return

        fun clearCachedSkeletonColors() {
            poseFrameProcessor.updateReferenceAndColors(
                refLandmarks    = emptyList(),
                jointArgbColors = IntArray(LANDMARK_COUNT) { android.graphics.Color.GREEN },
                limbArgbColors  = IntArray(LIMB_COUNT) { android.graphics.Color.GREEN },
            )
            poseFrameProcessor.updateReferenceSkeleton(emptyList())
        }

        val frameTimestamp = SystemClock.elapsedRealtime()
        val angle = detectCameraAngle(poseResult.landmarks)
        val bodyInFrame = if (requiresFullBody)
            isFullBodyInFrame(poseResult.visibilities, readinessVisibilityMode)
        else
            isUpperBodyInFrame(poseResult.visibilities, readinessVisibilityMode)
        val userIsValid = bodyInFrame && isCameraAngleValid(angle)

        // 暂停：身体不在画面 / 视角不对；超过宽限期才显示横幅，过滤瞬间误判
        val showPause = updatePauseGrace(userIsValid, frameTimestamp)
        if (!userIsValid) {
            _uiState.update { state ->
                state.copy(
                    isBodyInFrame = bodyInFrame,
                    cameraAngle = angle,
                    isTrainingPaused = showPause,
                    matchedReferenceRawLandmarks = emptyList(),
                    cameraFrameWidth = poseResult.imageWidth,
                    cameraFrameHeight = poseResult.imageHeight,
                )
            }
            clearCachedSkeletonColors()
            resetRepAndDtwState()
            return
        }

        val sideViewDirection = if (angle == CameraAngle.SIDE)
            detectSideViewDirection(poseResult.landmarks)
        else SideViewDirection.UNKNOWN

        val directionMismatch = requiredCameraAngle == CameraAngle.SIDE && (
                angle == CameraAngle.FRONT || (
                        angle == CameraAngle.SIDE &&
                                requiredSideViewDirection != SideViewDirection.NONE &&
                                sideViewDirection != SideViewDirection.UNKNOWN &&
                                sideViewDirection != requiredSideViewDirection
                        )
                )
        val showDirectionWarning =
            updateCameraDirectionWarning(directionMismatch, frameTimestamp) // [D2]

        if (directionMismatch) {
            _uiState.update { state ->
                state.copy(
                    isBodyInFrame = bodyInFrame,
                    cameraAngle = angle,
                    isCameraDirectionWarningVisible = showDirectionWarning,
                    isTrainingPaused = false,
                    matchedReferenceRawLandmarks = emptyList(),
                    cameraFrameWidth = poseResult.imageWidth,
                    cameraFrameHeight = poseResult.imageHeight,
                )
            }
            clearCachedSkeletonColors()
            return
        }

        // 会话结束检测：用户走近手机 → 静默跳过本帧，等待手动停止
        if (endDetector.update(poseResult.landmarks, poseResult.visibilities, frameTimestamp)
            == TrainingEndState.DETECTED
        ) {
            resetRepAndDtwState()
            return
        }

        // 归一化；退化帧（torsoLength ≈ 0）直接跳过
        val normalized = normalizeLandmarks(poseResult.landmarks, requiredCameraAngle, requiresFullBody) ?: return

        // 增量 OE-DTW：每帧只计算一行 O(m)，与全矩阵重算数学等价
        oeDtwFrameCount++
        val t0 = System.nanoTime()
        val advance = advanceOeDtw(normalized, referenceSequence, oeDtwRow, oeDtwFrameCount)
        oeDtwRow = advance.nextRow
        val t1 = System.nanoTime()
//        Log.d("动态时间规整性能", "增量DTW耗时: ${(t1 - t0) / 1_000}µs  帧数=$oeDtwFrameCount  参考长度=${referenceSequence.size}  匹配帧=${advance.matchedReferenceIndex}")
        val matchedIdx = stabiliseMatchedReferenceIndex(advance.matchedReferenceIndex, normalized)

        // 姿态评分 + 红绿颜色
        val scoreResult = evaluateUseCase.evaluate(
            matchedIdx, normalized, referenceSequence,
            upperBodyOnly = !requiresFullBody,
            exerciseId = currentExerciseId,
        )

        // Rep 得分累计：热身期（matchedIdx == -1）跳过，避免 allGreenResult 的 sf=100 虚高均分
        // 按肢体单独追踪连续红帧：只有同一条肢体持续红色超过阈值才显示红色并影响分类
        if (matchedIdx != -1) repScoreTracker.addFrameScore(scoreResult.sf, scoreResult.limbColors)
        val visualLimbIsRed = repScoreTracker.visualLimbIsRed
        val visualLimbColors = List(LIMB_COUNT) { i -> if (visualLimbIsRed[i]) Color.Red else Color.Green }
        val visualJointColors = PoseScoringEngine.computeVisualJointColors(visualLimbIsRed)

        // [Bug B 修复] Rep 计数已移至 onFrame() 快速路径（帧守卫之前），此处不再调用。
        // 原有代码保留供回滚参考，如需恢复请取消注释并删除下方 updatedRepScores 替换行。
        // --- 原代码开始 ---
        // // Rep 计数检测
        // val repCompleted = countRepsUseCase.update(poseResult.landmarks, poseResult.visibilities)
        // val updatedRepScores = if (repCompleted) {
        //     repScoreTracker.finishRep()
        //     oeDtwRow = null             // Rep 完成后重置，让 DTW 重新定位到参考序列起点
        //     oeDtwFrameCount = 0
        //     lastMatchedReferenceIndex = -1
        //     repScoreTracker.getCompletedRepScores()
        // } else {
        //     _uiState.value.repScores
        // }
        // --- 原代码结束 ---
        // [Bug B 修复] rep 计数由 onFrame() 异步更新 UI；此处直接读取当前状态，避免重复计数。
        val updatedRepScores = _uiState.value.repScores

        // ── 蓝色参考骨架坐标 [D4] ────────────────────────────────────────────────
        // #B2：热身期（matchedIdx == -1）锚定到参考序列起始帧，避免蓝骨架突然弹出。
        // DTW 热身完成后无缝切换到实际匹配帧；showRefSkeleton=false 时传 emptyList() 隐藏骨架。
        val matchedRaw = if (!showRefSkeleton) emptyList() else when {
            matchedIdx != -1   -> repositionReference(rawReferenceSequence[matchedIdx], poseResult.landmarks, poseResult.visibilities)
            oeDtwFrameCount > 0 -> repositionReference(rawReferenceSequence[0], poseResult.landmarks, poseResult.visibilities)
            else               -> emptyList()
        }

        // ── 把本帧评分颜色和参考骨架坐标推给 PoseFrameProcessor，供下一帧绘制时使用 ──
        // 颜色在下一帧生效（一帧延迟），这是避免阻塞相机线程的刻意设计
        poseFrameProcessor.updateReferenceAndColors(
            refLandmarks    = matchedRaw,
            jointArgbColors = IntArray(visualJointColors.size) { i ->
                if (visualJointColors[i] == Color.Red) android.graphics.Color.RED
                else android.graphics.Color.GREEN
            },
            limbArgbColors  = IntArray(visualLimbColors.size) { i ->
                if (visualLimbColors[i] == Color.Red) android.graphics.Color.RED
                else android.graphics.Color.GREEN
            },
        )

        _uiState.update { state ->
            state.copy(
                isBodyInFrame = bodyInFrame,
                cameraAngle = angle,
                isCameraDirectionWarningVisible = showDirectionWarning,
                currentFrameScore = scoreResult.sf,
                matchedReferenceRawLandmarks = matchedRaw,
                cameraFrameWidth = poseResult.imageWidth,
                cameraFrameHeight = poseResult.imageHeight,
                repCount = updatedRepScores.size,
                repScores = updatedRepScores,
                correctReps = repScoreTracker.correctReps,
                incorrectReps = repScoreTracker.incorrectReps,
                isTrainingPaused = false,
            )
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // D0：updateReferenceSkeletonForCurrentBody()
    // 调用方：onFrame() 快速路径
    // 作用：完整 DTW 尚未跑完时，也用上一匹配参考帧 + 当前身体坐标即时重定位蓝骨架
    // ══════════════════════════════════════════════════════════════════════════

    private fun updateReferenceSkeletonForCurrentBody(poseResult: PoseResult) {
        val state = _uiState.value
        if (state.phase != SessionPhase.TRAINING || state.isTrainingPaused) return

        val idx = lastMatchedReferenceIndex
        val rawSeq = rawReferenceSequence
        if (idx !in rawSeq.indices) return

        poseFrameProcessor.updateReferenceSkeleton(
            repositionReference(rawSeq[idx], poseResult.landmarks, poseResult.visibilities)
        )
    }

    // ══════════════════════════════════════════════════════════════════════════
    // D2：updateCameraDirectionWarning()
    // 调用方：processTrainingFrame() [D1]
    // 作用：判断朝向不匹配是否已持续超过 1500ms 宽限期
    //        不匹配时开始计时；恢复正确朝向时重置计时器；超过宽限期才返回 true
    // ══════════════════════════════════════════════════════════════════════════

    // 用户不满足入画/视角要求超过 PAUSE_GRACE_MS 才返回 true；有效时重置计时器
    private fun updatePauseGrace(userIsValid: Boolean, frameTimestamp: Long): Boolean {
        if (userIsValid) {
            invalidUserStartedAt = null
            return false
        }
        val invalidStartedAt = invalidUserStartedAt ?: frameTimestamp.also {
            invalidUserStartedAt = it
        }
        return frameTimestamp - invalidStartedAt >= PAUSE_GRACE_MS
    }

    private fun updateCameraDirectionWarning(directionMismatch: Boolean, frameTimestamp: Long): Boolean {
        if (!directionMismatch) {
            directionMismatchStartedAt = null
            return false
        }
        val mismatchStartedAt = directionMismatchStartedAt ?: frameTimestamp.also {
            directionMismatchStartedAt = it
        }
        return frameTimestamp - mismatchStartedAt >= CAMERA_DIRECTION_WARNING_GRACE_MS
    }

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
        val searchEnd   = minOf(referenceSequence.lastIndex, lastIdx + REFERENCE_SEARCH_FORWARD_FRAMES)
        val localBestIdx = (searchStart..searchEnd).minByOrNull { idx ->
            frameDist(currentNormalized, referenceSequence[idx])
        } ?: dtwMatchedIdx

        val smoothed = localBestIdx.coerceIn(
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

    private fun repositionReference(
        refRaw: List<Triple<Float, Float, Float>>,
        userRaw: List<Triple<Float, Float, Float>>,
        userVisibilities: List<Float>,
    ): List<Triple<Float, Float, Float>> {
        val normalizedRef = normalizeLandmarks(refRaw, requiredCameraAngle, requiresFullBody) ?: return emptyList()

        val userHipMidX      = (userRaw[LANDMARK_LEFT_HIP].first      + userRaw[LANDMARK_RIGHT_HIP].first)      / 2f
        val userHipMidY      = (userRaw[LANDMARK_LEFT_HIP].second     + userRaw[LANDMARK_RIGHT_HIP].second)     / 2f
        val userShoulderMidX = (userRaw[LANDMARK_LEFT_SHOULDER].first  + userRaw[LANDMARK_RIGHT_SHOULDER].first)  / 2f
        val userShoulderMidY = (userRaw[LANDMARK_LEFT_SHOULDER].second + userRaw[LANDMARK_RIGHT_SHOULDER].second) / 2f
        val refShoulderMidX  = (normalizedRef[LANDMARK_LEFT_SHOULDER].first  + normalizedRef[LANDMARK_RIGHT_SHOULDER].first)  / 2f
        val refShoulderMidY  = (normalizedRef[LANDMARK_LEFT_SHOULDER].second + normalizedRef[LANDMARK_RIGHT_SHOULDER].second) / 2f

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

        val refUnitX = refShoulderMidX / refTorsoLen
        val refUnitY = refShoulderMidY / refTorsoLen
        val userUnitX = (userShoulderMidX - userHipMidX) / userTorsoLen
        val userUnitY = (userShoulderMidY - userHipMidY) / userTorsoLen
        val cos = refUnitX * userUnitX + refUnitY * userUnitY
        val sin = refUnitX * userUnitY - refUnitY * userUnitX

        // 第一步：全局旋转 + 躯干等比缩放 + 平移，使参考躯干轴与用户躯干对齐
        val transformed = normalizedRef.map { (nx, ny, _) ->
            val rotatedX = nx * cos - ny * sin
            val rotatedY = nx * sin + ny * cos
            Triple(
                rotatedX * userTorsoLen + userHipMidX,
                rotatedY * userTorsoLen + userHipMidY,
                0f,
            )
        }.toMutableList()

        // 第二步：四肢重定向——根关节固定到用户实际位置，沿参考方向延伸但使用用户的肢段长度。
        // 肢段长度用 EMA 平滑，避免 MediaPipe 抖动造成蓝骨架颤抖。
        fun distance(a: Triple<Float, Float, Float>, b: Triple<Float, Float, Float>): Float {
            val dx = a.first - b.first; val dy = a.second - b.second
            return sqrt(dx * dx + dy * dy)
        }

        fun segmentLength(parentIdx: Int, childIdx: Int): Float {
            val current = distance(userRaw[parentIdx], userRaw[childIdx])
            val key = parentIdx to childIdx
            val visible =
                userVisibilities.getOrElse(parentIdx) { 0f } >= VISIBILITY_IN_FRAME_MIN &&
                    userVisibilities.getOrElse(childIdx) { 0f } >= VISIBILITY_IN_FRAME_MIN
            return synchronized(smoothedSegmentLengths) {
                val cached = smoothedSegmentLengths[key]
                if (!visible || current < 1e-6f) return@synchronized cached ?: current
                val smoothed = if (cached == null) current
                               else cached * (1f - BODY_LENGTH_EMA_ALPHA) + current * BODY_LENGTH_EMA_ALPHA
                smoothedSegmentLengths[key] = smoothed
                smoothed
            }
        }

        fun rotatedReferenceDirection(parentIdx: Int, childIdx: Int): Pair<Float, Float>? {
            val refParent = refRaw[parentIdx]
            val refChild  = refRaw[childIdx]
            val dx = refChild.first - refParent.first
            val dy = refChild.second - refParent.second
            val rotatedX = dx * cos - dy * sin
            val rotatedY = dx * sin + dy * cos
            val length = sqrt(rotatedX * rotatedX + rotatedY * rotatedY)
            if (length < 1e-6f) return null
            return rotatedX / length to rotatedY / length
        }

        fun retargetSegment(parentIdx: Int, childIdx: Int) {
            val parent    = transformed[parentIdx]
            val direction = rotatedReferenceDirection(parentIdx, childIdx) ?: return
            val userLength = segmentLength(parentIdx, childIdx)
            if (userLength < 1e-6f) return
            transformed[childIdx] = Triple(
                parent.first  + direction.first  * userLength,
                parent.second + direction.second * userLength,
                0f,
            )
        }

        fun retargetChain(rootIdx: Int, midIdx: Int, endIdx: Int) {
            transformed[rootIdx] = userRaw[rootIdx]
            retargetSegment(rootIdx, midIdx)
            retargetSegment(midIdx, endIdx)
        }

        // Use the user's current body proportions while keeping the matched
        // reference frame's limb directions. This keeps distal joints attached
        // to the live body instead of drifting with the reference actor's proportions.
        retargetChain(LANDMARK_LEFT_SHOULDER, LANDMARK_LEFT_ELBOW, LANDMARK_LEFT_WRIST)
        retargetChain(LANDMARK_RIGHT_SHOULDER, LANDMARK_RIGHT_ELBOW, LANDMARK_RIGHT_WRIST)
        retargetChain(LANDMARK_LEFT_HIP, LANDMARK_LEFT_KNEE, LANDMARK_LEFT_ANKLE)
        retargetChain(LANDMARK_RIGHT_HIP, LANDMARK_RIGHT_KNEE, LANDMARK_RIGHT_ANKLE)

        return transformed

    }

    private fun resetRepAndDtwState() {
        repScoreTracker.discardCurrentRep()
        oeDtwRow = null
        oeDtwFrameCount = 0
        lastMatchedReferenceIndex = -1
    }

}

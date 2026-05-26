package com.example.fitnesscoach.training.core

import androidx.compose.ui.graphics.Color
import com.example.fitnesscoach.core.util.Constants.LANDMARK_COUNT
import com.example.fitnesscoach.core.util.Constants.LIMB_COUNT
import com.example.fitnesscoach.core.util.Constants.SCORE_RED_THRESHOLD
import com.example.fitnesscoach.core.util.Constants.SCORE_WEIGHT_S1
import com.example.fitnesscoach.core.util.Constants.SCORE_WEIGHT_S2
import kotlin.math.acos
import kotlin.math.sqrt

data class PoseScoreResult(
    /*长度 33。对应 33 个关键点的颜色：
    分数低于阈值 → 红色
    分数达到阈值 → 绿色*/
    val jointColors: List<Color>,
    /*长度 13。
    对应 13 条肢体的颜色。*/
    val limbColors: List<Color>,
    /*关节位置总分。
    由 33 个关节位置分数求平均得到。*/
    val s1: Float,
    /*肢体角度总分。
    由 13 条肢体方向分数求平均得到。*/
    val s2: Float,
    /*最终综合分。
    公式是：sf = 0.2 * s1 + 0.8 * s2 */
    val sf: Float,
    //33 个关节各自的分数。
    val jointScores: List<Float>,
    //13 条肢体各自的分数。
    val limbScores: List<Float>
)

object PoseScoringEngine {
    private const val EPS = 1e-6f
    private const val Z_WEIGHT_S1 = 0.1f
    private const val Z_WEIGHT_S2 = 0.1f

    private val RED = Color.Red
    private val GREEN = Color.Green

    data class Point(val x: Float, val y: Float, val z: Float)
    //这个类表示“一条肢体”.
    /*
       start 起点关节编号
       end 终点关节编号

        useMidpoint 是否使用中点逻辑。
        这是专门给脊柱 Spine 用的，因为脊柱不是单个关键点连单个关键点，而是：
        肩膀中点
        髋部中点
    */
    data class Limb(val start: Int, val end: Int, val useMidpoint: Boolean = false)

    // Limb indices that belong to the upper body (arms, torso lines, shoulder line, spine).
    // When upperBodyOnly = true, only these are included in S2 and colour decisions.
    private val UPPER_BODY_LIMB_INDICES = setOf(0, 1, 2, 3, 4, 5, 10, 12)
    private val BICEP_CURL_LIMB_INDICES = setOf(2, 3)

    // Joint indices included in S1 when upperBodyOnly = true.
    // Hips (23, 24) are included because they anchor the torso limbs and spine.
    private val UPPER_BODY_JOINT_INDICES = setOf(11, 12, 13, 14, 15, 16, 23, 24)
    private val BICEP_CURL_JOINT_INDICES = setOf(12, 14, 16)

    // Maps each joint index to the limb indices it belongs to.
    // Joints not listed here (e.g. face, hands, feet) have no connected scored limb → always green.
    // Spine (limb 12) treats joints 11, 12, 23, 24 as its endpoints via midpoint logic.
    private val JOINT_LIMB_MAP: Map<Int, List<Int>> = mapOf(
        11 to listOf(0, 4, 10, 12),  // left shoulder
        12 to listOf(2, 5, 10, 12),  // right shoulder
        13 to listOf(0, 1),           // left elbow
        14 to listOf(2, 3),           // right elbow
        15 to listOf(1),              // left wrist
        16 to listOf(3),              // right wrist
        23 to listOf(4, 6, 11, 12),  // left hip
        24 to listOf(5, 7, 11, 12),  // right hip
        25 to listOf(6, 8),           // left knee
        26 to listOf(7, 9),           // right knee
        27 to listOf(8),              // left ankle
        28 to listOf(9),              // right ankle
    )

    // 13条肢体定义
    private val limbs = listOf(
        Limb(11, 13), // 0 Left upper arm 左上臂：左肩 → 左肘
        Limb(13, 15), // 1 Left forearm 左前臂：左肘 → 左手腕
        Limb(12, 14), // 2 Right upper arm 右上臂：右肩 → 右肘
        Limb(14, 16), // 3 Right forearm 右前臂：右肘 → 右手腕
        Limb(11, 23), // 4 Left torso 左躯干：左肩 → 左髋
        Limb(12, 24), // 5 Right torso 右躯干：右肩 → 右髋
        Limb(23, 25), // 6 Left thigh 左大腿：左髋 → 左膝
        Limb(24, 26), // 7 Right thigh 右大腿：右髋 → 右膝
        Limb(25, 27), // 8 Left shin 左小腿：左膝 → 左踝
        Limb(26, 28), // 9 Right shin 右小腿：右膝 → 右踝
        Limb(11, 12), // 10 Shoulder line 肩线：左肩 → 右肩
        Limb(23, 24), // 11 Hip line 髋线：左髋 → 右髋
        //脊柱：特殊处理，用中点方式计算
        Limb(-1, -1, true) // 12 Spine: midpoint(11,12) -> midpoint(23,24)
    )

    //核心函数
    /**
     * @param upperBodyOnly When true (exercises where [requiresFullBody] = false), S1 is
     *   averaged over [UPPER_BODY_JOINT_INDICES] only, S2 over [UPPER_BODY_LIMB_INDICES] only,
     *   and lower-body limbs/joints are forced green so they never penalise the score or
     *   confuse the user with irrelevant red highlights.
     */
    fun calculatePoseScore(
        userLandmarks: List<Triple<Float, Float, Float>>,
        referenceLandmarks: List<Triple<Float, Float, Float>>,
        upperBodyOnly: Boolean = false,
        bicepCurlOnly: Boolean = false,
    ): PoseScoreResult {
        require(userLandmarks.size == LANDMARK_COUNT) {
            "userLandmarks size must be $LANDMARK_COUNT"
        }
        require(referenceLandmarks.size == LANDMARK_COUNT) {
            "referenceLandmarks size must be $LANDMARK_COUNT"
        }

        val user = userLandmarks.map { Point(it.first, it.second, it.third) }
        val ref  = referenceLandmarks.map { Point(it.first, it.second, it.third) }
        val activeLimbIndices = when {
            bicepCurlOnly -> BICEP_CURL_LIMB_INDICES
            upperBodyOnly -> UPPER_BODY_LIMB_INDICES
            else -> limbs.indices.toSet()
        }
        val activeJointIndices = when {
            bicepCurlOnly -> BICEP_CURL_JOINT_INDICES
            upperBodyOnly -> UPPER_BODY_JOINT_INDICES
            else -> 0 until LANDMARK_COUNT
        }

        // Step 1: joint position scores (all 33 computed; S1 averaged over active set only)
        val jointScores = (0 until LANDMARK_COUNT).map { p ->
            val dp = distance(user[p], ref[p])
            ((1f - dp) * 100f).coerceIn(0f, 100f)
        }
        val s1 = activeJointIndices.map { jointScores[it] }.average().toFloat()

        // Step 2: limb angle scores (all 13 computed; S2 averaged over active set only)
        val limbScores = limbs.mapIndexed { _, limb ->
            val userVector = getLimbVector(user, limb)
            val refVector = getLimbVector(ref, limb)
            val angleDiff = angleBetween(userVector, refVector)
            ((1f - angleDiff / 180f) * 100f).coerceIn(0f, 100f)
        }
        val s2 = activeLimbIndices.map { limbScores[it] }.average().toFloat()

        // Step 3: overall score
        val sf = (SCORE_WEIGHT_S1 * s1 + SCORE_WEIGHT_S2 * s2).coerceIn(0f, 100f)

        // Step 4: colors — limb scores drive both limb and joint colors.
        // When upperBodyOnly, lower-body limbs are always green and joint colours only
        // consider their upper-body connected limbs, preventing misleading red highlights.
        val limbColors = limbScores.mapIndexed { idx, score ->
            if (idx in activeLimbIndices && score < SCORE_RED_THRESHOLD) RED else GREEN
        }

        val jointColors = (0 until LANDMARK_COUNT).map { jointIdx ->
            val connectedLimbs = JOINT_LIMB_MAP[jointIdx]
            if (connectedLimbs != null && connectedLimbs.any { limbIdx ->
                    limbIdx in activeLimbIndices && limbScores[limbIdx] < SCORE_RED_THRESHOLD
                }) RED else GREEN
        }

        return PoseScoreResult(
            jointColors = jointColors,
            limbColors = limbColors,
            s1 = s1,
            s2 = s2,
            sf = sf,
            jointScores = jointScores,
            limbScores = limbScores
        )
    }

    private fun distance(a: Point, b: Point): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        val dz = (a.z - b.z) * Z_WEIGHT_S1
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun midpoint(a: Point, b: Point): Point = Point(
        x = (a.x + b.x) / 2f,
        y = (a.y + b.y) / 2f,
        z = (a.z + b.z) / 2f,
    )

    private fun getLimbVector(points: List<Point>, limb: Limb): Point {
        return if (limb.useMidpoint) {
            val shoulderMid = midpoint(points[11], points[12])
            val hipMid      = midpoint(points[23], points[24])
            Point(
                x = hipMid.x - shoulderMid.x,
                y = hipMid.y - shoulderMid.y,
                z = hipMid.z - shoulderMid.z,
            )
        } else {
            val start = points[limb.start]
            val end   = points[limb.end]
            Point(
                x = end.x - start.x,
                y = end.y - start.y,
                z = end.z - start.z,
            )
        }
    }

    private fun angleBetween(a: Point, b: Point): Float {
        val az = a.z * Z_WEIGHT_S2
        val bz = b.z * Z_WEIGHT_S2
        val magA = sqrt(a.x * a.x + a.y * a.y + az * az)
        val magB = sqrt(b.x * b.x + b.y * b.y + bz * bz)
        if (magA < EPS || magB < EPS) return 0f
        val dot    = a.x * b.x + a.y * b.y + az * bz
        val cosine = (dot / (magA * magB)).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(cosine).toDouble()).toFloat()
    }

    /**
     * Computes visual joint colors driven by [visualLimbIsRed] (per-limb display flags from
     * RepScoreTracker) rather than raw limb scores. A joint is red only when at least one of
     * its connected limbs has been persistently red beyond the display threshold.
     */
    fun computeVisualJointColors(
        visualLimbIsRed: BooleanArray,
    ): List<Color> = (0 until LANDMARK_COUNT).map { jointIdx ->
        val connectedLimbs = JOINT_LIMB_MAP[jointIdx]
        if (connectedLimbs != null && connectedLimbs.any { limbIdx ->
                limbIdx < visualLimbIsRed.size && visualLimbIsRed[limbIdx]
            }) RED else GREEN
    }
}

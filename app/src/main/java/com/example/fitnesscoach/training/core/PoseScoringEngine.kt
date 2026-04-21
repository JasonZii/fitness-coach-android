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
    //用来避免除以 0。
    private const val EPS = 1e-6f

    private val RED = Color.Red
    private val GREEN = Color.Green

    //内部使用的二维点
    data class Point(val x: Float, val y: Float)
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
    fun calculatePoseScore(
        userLandmarks: List<Pair<Float, Float>>, //用户当前帧的 33 个关键点
        referenceLandmarks: List<Pair<Float, Float>>  //参考标准帧的 33 个关键点
    ): PoseScoreResult {
        //校验
        require(userLandmarks.size == LANDMARK_COUNT) {
            "userLandmarks size must be $LANDMARK_COUNT"
        }
        //校验
        require(referenceLandmarks.size == LANDMARK_COUNT) {
            "referenceLandmarks size must be $LANDMARK_COUNT"
        }

        //把 Pair 转成 Point
        val user = userLandmarks.map { Point(it.first, it.second) }
        val ref = referenceLandmarks.map { Point(it.first, it.second) }

        // 计算关节得分, 先算欧氏距离 dp = sqrt((x1-x2)^2 + (y1-y2)^2) 也就是用户点和参考点之间的距离。
        // 再转成分数 距离越小 → 分数越高, 距离越大 → 分数越低
        // Step 1: joint scores
        val jointScores = (0 until LANDMARK_COUNT).map { p ->
            val dp = distance(user[p], ref[p])
            ((1f - dp) * 100f).coerceIn(0f, 100f)  //把分数强制限制在 0~100 范围内
        }

        //把 LANDMARK_COUNT 个关节分数求平均。
        val s1 = jointScores.average().toFloat()

        // Step 2: limb angle scores
        val limbScores = limbs.mapIndexed { index, limb ->
            val userVector = getLimbVector(user, limb) //1. 得到用户肢体向量
            val refVector = getLimbVector(ref, limb) //2. 得到参考肢体向量

            val angleDiff = angleBetween(userVector, refVector) // 0..180 //3. 算两个向量之间的夹角
            ((1f - angleDiff / 180f) * 100f).coerceIn(0f, 100f) //角度转分数 夹角越小 → 动作越接近 → 分数越高; 夹角越大 → 动作差异越大 → 分数越低
        }

        //肢体平均分,13 条 limb 的平均分
        val s2 = limbScores.average().toFloat()

        // Step 3: overall score  综合分 sf = SCORE_WEIGHT_S1 * s1 + SCORE_WEIGHT_S2 * s2
        val sf = (SCORE_WEIGHT_S1 * s1 + SCORE_WEIGHT_S2 * s2).coerceIn(0f, 100f)

        // Step 4: colors — limb scores drive both limb and joint colors.
        // A joint is red when any of its connected limbs scores below the threshold,
        // so users see a continuous red segment rather than isolated red dots.
        // Joints with no connected scored limb (face, hands, feet) default to green.
        val limbColors = limbScores.map { score ->
            if (score < SCORE_RED_THRESHOLD) RED else GREEN
        }

        val jointColors = (0 until LANDMARK_COUNT).map { jointIdx ->
            val connectedLimbs = JOINT_LIMB_MAP[jointIdx]
            if (connectedLimbs != null && connectedLimbs.any { limbIdx -> limbScores[limbIdx] < SCORE_RED_THRESHOLD }) RED else GREEN
        }

        //返回最终结果
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

    //计算两点距离 distance = sqrt((x1-x2)^2 + (y1-y2)^2)
    //二维欧氏距离。 用于 Step 1 的关节位置评分
    private fun distance(a: Point, b: Point): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }

    //计算中点
    //求两个点的中心位置。
    //这里主要用于脊柱计算： 肩膀中点 髋部中点
    private fun midpoint(a: Point, b: Point): Point {
        return Point(
            x = (a.x + b.x) / 2f,
            y = (a.y + b.y) / 2f
        )
    }

    //获得肢体向量 根据一条 limb，返回它的方向向量
    private fun getLimbVector(points: List<Point>, limb: Limb): Point {
        return if (limb.useMidpoint) {
            //脊柱的特殊情况
//因为脊柱不是某两个单点直接连，而是：起点：左右肩中点 终点：左右髋中点 所以要用 midpoint()。
            val shoulderMid = midpoint(points[11], points[12])
            val hipMid = midpoint(points[23], points[24])
            Point(
                x = hipMid.x - shoulderMid.x,
                y = hipMid.y - shoulderMid.y
            )
        } else { //普通 limb 的情况 vector = end - start
            val start = points[limb.start]
            val end = points[limb.end]
            Point(
                x = end.x - start.x,
                y = end.y - start.y
            )
        }
    }

    //计算两个向量夹角
    private fun angleBetween(a: Point, b: Point): Float {
        //先算两个向量长度 。向量长度公式：|A| = sqrt(x² + y²)
        val magA = sqrt(a.x * a.x + a.y * a.y)
        val magB = sqrt(b.x * b.x + b.y * b.y)

        //防止除零
        if (magA < EPS || magB < EPS) return 0f

        //算点积 。A·B = AxBx + AyBy
        val dot = a.x * b.x + a.y * b.y
        //算余弦值 。cosθ = (A·B) / (|A||B|) coerceIn(-1f, 1f) 是为了防止浮点误差导致 acos() 出现非法值。
        val cosine = (dot / (magA * magB)).coerceIn(-1f, 1f)
        //最后转成角度 0° ~ 180°
        return Math.toDegrees(acos(cosine).toDouble()).toFloat()
    }

    //test
    fun testPoseScoring() {
        val ref = List(33) { Pair(0.5f, 0.5f) }
        val user = List(33) { Pair(0.5f, 0.5f) }

        val result = PoseScoringEngine.calculatePoseScore(user, ref)

        println("S1 = ${result.s1}")
        println("S2 = ${result.s2}")
        println("Sf = ${result.sf}")
    }
}
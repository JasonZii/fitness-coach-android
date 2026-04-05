# 算法实现指南
# Algorithm Implementation Guide

本文档描述Android健身教练app项目所有算法模块的接口约定和业务需求。
Claude Code生成Kotlin代码时必须严格遵循本文档。
所有算法用Kotlin实现，不使用Python运行时。

This document describes the interface agreements and business requirements
for all algorithm modules in the Android fitness-coach project. Claude Code must follow
this document strictly when generating Kotlin code.
All algorithms are implemented in Kotlin. No Python runtime is used.

---

## MediaPipe输出格式
## MediaPipe Output Format

MediaPipe BlazePose每帧输出33个关键点，每个关键点有三个坐标(x, y, z)：
- x：水平位置，相对于画面宽度归一化到[0, 1]
- y：垂直位置，相对于画面高度归一化到[0, 1]
- z：相对深度估计，单摄像头场景下精度不可靠，本系统忽略z

MediaPipe BlazePose outputs 33 landmark points per frame.
Each landmark has three coordinates (x, y, z):
- x: horizontal position, normalized to [0, 1] relative to frame width
- y: vertical position, normalized to [0, 1] relative to frame height
- z: relative depth, not reliable for single-camera. Ignored in this system.

关键关节点编号（完整列表见MediaPipe官方文档）：
Key landmark indices (full list: https://developers.google.com/mediapipe/solutions/vision/pose_landmarker):
```
0:  鼻子 nose
11: 左肩 left shoulder       12: 右肩 right shoulder
13: 左肘 left elbow          14: 右肘 right elbow
15: 左腕 left wrist          16: 右腕 right wrist
23: 左髋 left hip            24: 右髋 right hip
25: 左膝 left knee           26: 右膝 right knee
27: 左踝 left ankle          28: 右踝 right ankle
```

---

## 标准动作数据（JSON格式）
## Standard Action Data (JSON Format)

Member A使用Python脚本从标准动作视频中提取关键点，
保存为JSON文件存放在app的assets文件夹里。
Member A不做任何归一化处理，直接保存MediaPipe原始输出。
归一化由Member D的归一化函数在运行时统一处理，
确保标准数据和用户实时数据经过完全相同的处理流程。

Member A extracts landmarks from standard action videos using a Python
script and saves raw JSON files in the app assets folder.
Member A does NOT normalize. Normalization is handled by Member D
at runtime to ensure standard data and user data go through
exactly the same processing pipeline.

JSON文件存放位置 / JSON file location:
```
app/src/main/assets/landmarks/squat.json
app/src/main/assets/landmarks/lunge.json
app/src/main/assets/landmarks/bicep_curl.json
app/src/main/assets/landmarks/shoulder_press.json
app/src/main/assets/landmarks/lateral_raise.json
```

示范视频存放位置 / Demo video location:
```
app/src/main/res/raw/squat_demo.mp4
app/src/main/res/raw/lunge_demo.mp4
app/src/main/res/raw/bicep_curl_demo.mp4
app/src/main/res/raw/shoulder_press_demo.mp4
app/src/main/res/raw/lateral_raise_demo.mp4
```

JSON文件格式 / JSON file format:
frames数组按时间顺序排列，frames[0]是第0帧。
每帧内landmarks数组按关节编号排列，landmarks[0]对应关节0（鼻子）。

The frames array is ordered chronologically. frames[0] is the first frame.
Within each frame, landmarks[0] corresponds to landmark index 0 (nose).

```json
{
  "frames": [
    {
      "landmarks": [
        {"x": 0.45, "y": 0.12, "z": 0.01},
        {"x": 0.46, "y": 0.13, "z": 0.00},
        ...共33个关节 / 33 landmarks total, index 0 to 32
      ]
    },
    {
      "landmarks": [
        {"x": 0.45, "y": 0.15, "z": 0.01},
        ...
      ]
    }
  ]
}
```

---

## 数据类型与接口约定
## Data Types and Interface Agreement

归一化前的单帧关键点（MediaPipe原始输出）：
Single frame landmarks before normalization (raw MediaPipe output):
```kotlin
List<Triple<Float, Float, Float>>
// index对应关节编号0-32，Triple是(x, y, z)
// index = landmark index 0-32, Triple is (x, y, z)
```

归一化后的单帧关键点：
Single frame landmarks after normalization:
```kotlin
List<Pair<Float, Float>>
// index对应关节编号0-32，Pair是(x, y)，z已丢弃
// index = landmark index 0-32, Pair is (x, y), z is dropped
```

完整序列（多帧）：
Full sequence (multiple frames):
```kotlin
List<List<Pair<Float, Float>>>
// 外层index是帧编号，内层index是关节编号
// outer index = frame index, inner index = landmark index
```

使用List不使用Array：MediaPipe Kotlin API原生返回List，避免额外类型转换。
Use List not Array: MediaPipe Kotlin API natively returns List,
avoiding unnecessary type conversion.

Member B和Member D的接口约定：
Member B每帧输出List<Triple<Float, Float, Float>>，
传给Member D的归一化函数。

Interface agreement between Member B and Member D:
Member B outputs List<Triple<Float, Float, Float>> per frame,
passes to Member D's normalization function.

---

## 模块1：骨架归一化
## Module 1: Skeleton Normalization

负责人 / Owner: Member D

目的：消除不同用户身高和站位不同带来的坐标差异，
让所有人的骨架数据可以和标准数据直接比较。

Purpose: Remove differences caused by different user heights and positions,
so all users' skeleton data can be compared directly with standard data.

此函数在两种场景下调用，两种场景用同一个函数：
This function is called in two contexts, using the same function:

场景1：app启动时读取标准JSON数据
Context 1 - Loading standard JSON data at app startup:
```
读取assets里的JSON文件
        ↓
解析成List<List<Triple<Float, Float, Float>>>
        ↓
对每帧调用归一化函数
        ↓
存入内存备用 List<List<Pair<Float, Float>>>
```

场景2：每帧处理用户实时数据
Context 2 - Processing user real-time data each frame:
```
Member B传来List<Triple<Float, Float, Float>>
        ↓
调用归一化函数
        ↓
返回List<Pair<Float, Float>>
        ↓
追加到用户序列，传给OE-DTW
```

函数签名 / Function signature:
```kotlin
// Input:  List<Triple<Float, Float, Float>>  (33个原始关键点)
// Output: List<Pair<Float, Float>>            (33个归一化关键点，z已丢弃)
```

两步处理 / Two-step processing:

第一步：平移，消除站位差异
Step 1: Translation, remove position difference:
```
使用原始坐标计算髋关节中点
hipMidX = (landmarks[23].x + landmarks[24].x) / 2
hipMidY = (landmarks[23].y + landmarks[24].y) / 2

所有33个关节坐标减去髋关节中点
translatedX = landmark.x - hipMidX
translatedY = landmark.y - hipMidY

处理后髋关节中点在原点(0, 0)
```

第二步：缩放，消除身高差异
Step 2: Scaling, remove height difference:
```
使用原始坐标（平移之前）计算肩膀中点和躯干长度T
shoulderMidX = (landmarks[11].x + landmarks[12].x) / 2
shoulderMidY = (landmarks[11].y + landmarks[12].y) / 2
T = sqrt((shoulderMidX - hipMidX)^2 + (shoulderMidY - hipMidY)^2)

所有平移后的坐标除以T
finalX = translatedX / T
finalY = translatedY / T

处理后躯干长度等于1.0
```

丢弃z，只输出(x, y)。
Drop z. Output only (x, y) pairs.

验收标准 / Acceptance criteria:
- 归一化后髋关节中点为(0.0, 0.0)，误差 < 0.001
  Hip midpoint equals (0.0, 0.0), error < 0.001
- 归一化后躯干长度为1.0，误差 < 0.001
  Torso length equals 1.0, error < 0.001
- 输出恰好33个Pair / Output has exactly 33 pairs

---

## 模块2：OE-DTW实时对齐
## Module 2: OE-DTW Real-time Alignment

负责人 / Owner: Member D

目的：训练过程中，每来一帧，找到用户当前帧对应
标准动作序列的第几帧，供模块3评分使用。

Purpose: During training, for each incoming frame, find which frame
in the standard action sequence best matches the user's current frame.
This matched frame index is used by Module 3 for scoring.

为什么用OE-DTW而不是普通DTW：
普通DTW需要两段完整序列才能对齐，不适合实时场景。
OE-DTW支持不完整序列，每来一帧增量更新结果，
实现时注意使用增量更新方式以保证实时性能。

Why OE-DTW instead of classic DTW:
Classic DTW requires both complete sequences before alignment.
OE-DTW supports incomplete sequences with incremental updates.
Implementation must use incremental update approach for real-time performance.

函数签名 / Function signature:
```kotlin
// Input:
//   userSequence:      List<List<Pair<Float, Float>>>
//                      用户从训练开始到当前帧的归一化序列
//                      User normalized frames from training start to now
//
//   referenceSequence: List<List<Pair<Float, Float>>>
//                      标准动作完整归一化序列
//                      Complete normalized standard action sequence
//
// Output:
//   matchedReferenceIndex: Int
//                      对应标准序列的帧编号
//                      Index in referenceSequence best matching last user frame
//                      用户序列帧数<20时返回-1
//                      Returns -1 if userSequence has fewer than 20 frames
```

不稳定期处理 / Unstable period handling:
```
前20帧序列太短，对齐不稳定，返回-1
First 20 frames: sequence too short, return -1
调用方收到-1时所有关节显示绿色
Caller displays all landmarks as green when receiving -1
```

验收标准 / Acceptance criteria:
- 两段完全相同的序列输入，matchedReferenceIndex = userSequence.size - 1
  Two identical sequences: matchedReferenceIndex = userSequence.size - 1
- userSequence.size < 20 时返回-1
  Returns -1 when userSequence.size < 20

---

## 模块3：逐关节评分（S1 / S2 / Sf）
## Module 3: Per-landmark Scoring (S1 / S2 / Sf)

负责人 / Owner: Member C

目的：根据用户当前帧和OE-DTW找到的对应标准帧，
计算每个关节和每条肢体的偏差得分，确定骨架显示颜色和整体得分。

Purpose: Given the user's current frame and the matched standard frame
from OE-DTW, calculate deviation scores, determine display colors,
and calculate the overall frame score.

函数签名 / Function signature:
```kotlin
// Input:
//   userLandmarks:      List<Pair<Float, Float>>  33个归一化关节坐标
//   referenceLandmarks: List<Pair<Float, Float>>  33个归一化关节坐标
//
// Output:
//   jointColors: List<Color>  大小33，index=关节编号 / size 33
//   limbColors:  List<Color>  大小13，index=肢体编号 / size 13
//   sf:          Float        当前帧整体得分，范围0-100 / range 0-100
```

第一步：关节位置得分S1
Step 1: Landmark position score S1:
```
对每个关节p（0到32，共33个）：
Dp = 用户关节p和标准关节p之间的欧氏距离
     Euclidean distance between userLandmarks[p] and referenceLandmarks[p]

Sp = max(0.0f, (1 - Dp) * 100)
注意：归一化后T=1，所以不需要除以T
Note: After normalization T=1, no need to divide by T

S1 = 所有33个Sp的平均值 / average of all 33 Sp values
```

第二步：肢体角度得分S2
Step 2: Limb angle score S2:
```
对每条肢体l（0到12，共13条）：
分别计算用户肢体向量A和标准肢体向量B，
用余弦相似度计算两向量夹角作为角度偏差Dl：

For each limb l (0 to 12, total 13):
Calculate user limb vector A and reference limb vector B,
use cosine similarity to get angle between them as Dl:

A = (userLandmarks[end] - userLandmarks[start])
B = (referenceLandmarks[end] - referenceLandmarks[start])
cosine = (A·B) / (|A| × |B|)
Dl = acos(cosine) * (180 / PI)，范围0到180度 / range 0 to 180

注意：计算前检查|A|和|B|，避免除以零，若接近0则Dl=0
Note: Check |A| and |B| before calculation. If near 0, set Dl = 0.

Sl = (1 - Dl / 180) * 100
S2 = 所有13条Sl的平均值 / average of all 13 Sl values
```

13条肢体定义（起点 -> 终点关节编号）：
13 limbs definition (start -> end landmark index):
```
0:  左上臂 Left upper arm:   11 -> 13
1:  左前臂 Left forearm:     13 -> 15
2:  右上臂 Right upper arm:  12 -> 14
3:  右前臂 Right forearm:    14 -> 16
4:  左躯干 Left torso:       11 -> 23
5:  右躯干 Right torso:      12 -> 24
6:  左大腿 Left thigh:       23 -> 25
7:  右大腿 Right thigh:      24 -> 26
8:  左小腿 Left shin:        25 -> 27
9:  右小腿 Right shin:       26 -> 28
10: 肩连线 Shoulder line:    11 -> 12
11: 髋连线 Hip line:         23 -> 24
12: 脊柱   Spine:            midpoint(11,12) -> midpoint(23,24)
```
脊柱起点 = landmarks[11]和[12]各自坐标取平均
脊柱终点 = landmarks[23]和[24]各自坐标取平均
Spine start = average of landmarks[11] and landmarks[12]
Spine end   = average of landmarks[23] and landmarks[24]

第三步：整体得分Sf
Step 3: Overall score Sf:
```
Sf = 0.2 * S1 + 0.8 * S2
角度权重更高，因为动作质量主要体现在关节角度是否到位
Angle weight is higher because exercise quality depends mainly on angles
```

第四步：颜色判断
Step 4: Color decision:
```
Sp < 80  -> jointColors[p] = RED
Sp >= 80 -> jointColors[p] = GREEN
Sl < 80  -> limbColors[l] = RED
Sl >= 80 -> limbColors[l] = GREEN
```
阈值80是初始经验值，第四周测试时调整，调整后更新本文档。
Threshold 80 is initial empirical value. Adjust in Sprint 4
and update this document.

rep得分 / Rep score:
每次状态机通知rep完成时，把该次所有帧的Sf取平均作为本次rep得分。
Each time state machine signals rep completion, average all Sf values
of that rep as the rep score.

验收标准 / Acceptance criteria:
- userLandmarks和referenceLandmarks完全相同时，Sf >= 95
  Identical inputs: Sf >= 95
- 某个关节有明显偏移时，该关节Sp < 80，jointColors显示RED
  One landmark significantly shifted: Sp < 80, jointColors shows RED
- jointColors大小为33，limbColors大小为13
  jointColors size = 33, limbColors size = 13

---

## 模块4：状态机计次数
## Module 4: State Machine Rep Counting

负责人 / Owner: Member B

目的：通过监测关键角度变化检测完整动作循环，计次数，
并在rep完成时通知模块3计算rep得分。

Purpose: Detect completed exercise repetitions by monitoring key joint
angles, count reps, and signal Module 3 to calculate rep score.

三个状态 / Three states:
- S1：起始位置（动作开始和结束）/ starting position
- S2：中间过渡 / transition
- S3：终止位置（最大幅度）/ peak position

只有完整走过S1->S2->S3->S2->S1才计一次。
A rep is counted only when the full cycle S1->S2->S3->S2->S1 completes.

角度计算：选三个关键点P1、Pref（顶点）、P2，
计算向量(Pref->P1)和向量(Pref->P2)之间的夹角，范围0到180度。
Angle calculation: select P1, Pref (vertex), P2,
calculate angle between vectors (Pref->P1) and (Pref->P2), range 0-180°.

5个动作配置（初始阈值，Sprint 4测试后更新本文档）：
Exercise configurations (initial thresholds, update after Sprint 4):

深蹲 Squat（侧面 / side view）:
```
关键点：髋(23/24) - 膝(25/26) - 踝(27/28)，顶点：膝
使用可见度更高的一侧
S1（站直）: angle > 160°
S3（蹲下）: angle < 90°
```

弓步 Lunge（侧面 / side view）:
```
关键点：前腿髋 - 前腿膝 - 前腿踝，顶点：膝
S1（站直）: angle > 160°
S3（最低点）: angle < 90°
```

二头弯举 Bicep Curl（侧面 / side view）:
```
关键点：腕(15/16) - 肘(13/14) - 肩(11/12)，顶点：肘
使用可见度更高的一侧
S1（手臂放下）: angle > 160°
S3（弯举顶部）: angle < 50°
```

肩上推举 Shoulder Press（侧面 / side view）:
```
关键点：腕(15/16) - 肘(13/14) - 肩(11/12)，顶点：肘
使用可见度更高的一侧
S1（起始位置）: angle < 90°
S3（推起到顶）: angle > 160°
```

侧平举 Lateral Raise（正面 / front view）:
```
关键点：髋(23) - 肩(11) - 腕(15)，顶点：肩
S1（手臂放下）: angle < 30°
S3（抬起到位）: angle > 80°
```

验收标准 / Acceptance criteria:
- 每个动作各做10次，计数误差<=1次
  Each exercise: 10 reps, counting error <= 1

---

## 模块5：拍摄角度检测
## Module 5: Camera Angle Detection

负责人 / Owner: Member D

目的：训练开始前验证用户站立角度是否符合当前动作要求。
Purpose: Before training starts, verify user is at the correct angle.

正面/侧面判断：
以鼻子(0)为顶点，左肩(11)和右肩(12)为两端点，
使用模块4相同的角度公式计算夹角theta。

Front vs side detection:
Calculate theta at nose(0) with left shoulder(11) and right shoulder(12),
using the same angle formula as Module 4.
```
theta > 150° -> 正面 front view
theta < 60°  -> 侧面 side view
60° <= theta <= 150° -> 角度不明确，提示用户调整 ambiguous, prompt user
```

各动作所需角度 / Required view per exercise:
```
深蹲、弓步、二头弯举、肩上推举 -> 侧面 side view
侧平举 -> 正面 front view
```

全身入画检测 / Full body in frame:
检测以下关节MediaPipe可见度分数，全部 > 0.5才算入画：
Check visibility score of these landmarks, all must be > 0.5:
```
鼻子(0)、左肩(11)、右肩(12)、左髋(23)、右髋(24)
左膝(25)、右膝(26)、左踝(27)、右踝(28)
```

接口约定（Member B / Lee 负责调用）：
MediaPipe的可见度分数不在Triple坐标里，需要单独提取后传入isFullBodyInFrame。
提取方式如下：

Interface convention (called by Member B / Lee):
MediaPipe visibility is not stored in the (x, y, z) Triple.
It must be extracted separately before calling isFullBodyInFrame:
```kotlin
val visibilities = poseLandmarks.map { it.inFrameLikelihood() }
isFullBodyInFrame(visibilities)
```

触发训练开始 / Training start trigger:
两个条件同时满足并持续3秒 -> 显示倒计时3、2、1 -> 训练开始
Both conditions satisfied for 3 seconds -> countdown 3,2,1 -> training starts

---

## 模块6：训练结束自动检测
## Module 6: Training End Auto-detection

负责人 / Owner: Member D

目的：用户走向手机时自动暂停记录。
Purpose: Automatically pause recording when user walks toward phone.

触发条件（满足任意一个）/ Trigger conditions (either one):

条件一：关键关节可见度下降
Condition 1: Key landmarks disappear:
```
监测鼻子(0)、左肩(11)、右肩(12)、左髋(23)、右髋(24)的平均可见度
平均可见度 < 0.3 并持续超过3秒 -> 触发暂停
Average visibility < 0.3 for more than 3 seconds -> trigger pause
```

条件二：用户走近摄像头
Condition 2: User walks toward camera:
```
训练开始时记录肩膀宽度
startShoulderWidth = abs(landmarks[11].x - landmarks[12].x)

每帧检测
currentShoulderWidth = abs(landmarks[11].x - landmarks[12].x)
currentShoulderWidth > startShoulderWidth * 1.5 -> 触发暂停 trigger pause
```

触发后行为 / Behavior after trigger:
- 停止记录新帧 / Stop recording
- rep进行中途触发则丢弃该次未完成的rep
  Discard incomplete rep if triggered mid-rep
- 等待用户按停止按钮 / Wait for stop button
- 跳转训练总结页面 / Navigate to summary screen

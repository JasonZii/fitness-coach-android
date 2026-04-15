# Fitness Coach App — Product Requirements Document (PRD)
# Fitness Coach App — 产品需求文档（PRD）

> **Version / 版本**: 1.0
> **Date / 日期**: 2026-03-24
> **Project / 项目**: CSIT998 — Android Fitness Coach
> **Reference docs / 参考文档**: README.md, ALGORITHM.md

---

## 1. Product Overview / 产品概述

### English
Fitness Coach is an Android mobile application that uses the device's front-facing camera and MediaPipe Pose to deliver real-time exercise coaching. As users perform exercises, the app overlays a colour-coded skeleton on the live video feed: joints and limbs that are within acceptable deviation are displayed in **green**, while those that need correction are displayed in **red**. The app supports five exercises, provides automatic repetition counting, stores training history locally, and works fully offline.

### 中文
Fitness Coach 是一款基于 Android 平台的智能健身教练应用。使用手机前置摄像头配合 MediaPipe Pose，在用户训练时将实时骨架叠加在画面上：姿势正确的关节/肢体显示为**绿色**，存在偏差的显示为**红色**。应用支持5个健身动作，提供自动计次功能，本地存储训练记录，完全离线运行。

---

## 2. User Roles / 用户角色

### 2.1 Registered User / 注册用户

| Attribute / 属性 | Description / 说明 |
|---|---|
| Registration fields / 注册字段 | Username, password, email, height (cm), weight (kg) / 用户名、密码、邮箱、身高（cm）、体重（kg） |
| Login / 登录 | Username + password / 用户名 + 密码 |
| Data persistence / 数据持久化 | Training records saved to local Room database / 训练记录存入本地 Room 数据库 |
| Profile / 个人资料 | Can view and edit profile information / 可查看和编辑个人资料 |
| Training history / 训练历史 | Can view all past training sessions with scores / 可查看所有历史训练记录及得分 |

### 2.2 Guest User / 游客用户

| Attribute / 属性 | Description / 说明 |
|---|---|
| Access / 访问权限 | Can use all exercise and training features / 可使用所有动作和训练功能 |
| Data persistence / 数据持久化 | Training records saved locally but not linked to an account / 记录保存在本地，不关联账号 |
| Restrictions / 限制 | Cannot sync to cloud / 无法同步到云端（未来功能） |

---

## 3. Functional Requirements / 功能需求

### 3.1 Authentication Module / 认证模块

| ID | Feature / 功能 | Description / 描述 |
|---|---|---|
| AUTH-01 | Registration / 注册 | User can create an account by entering username, password, confirm password, email, height, and weight. / 用户填写用户名、密码、确认密码、邮箱、身高和体重来创建账号。 |
| AUTH-02 | Login / 登录 | User can log in with username and password. Invalid credentials display an error message. / 用户使用用户名和密码登录，凭据错误时显示提示。 |
| AUTH-03 | Guest Mode / 游客模式 | User can skip login and enter the app as a guest. / 用户可跳过登录以游客身份进入 app。 |
| AUTH-04 | Profile View / 资料查看 | Logged-in user can view their username, height, and weight. / 已登录用户可查看用户名、身高和体重。 |
| AUTH-05 | Profile Delete / 删除账号 | User can delete their account and all associated local data. / 用户可删除账号及所有本地数据。 |

### 3.2 Exercise Library Module / 动作库模块

| ID | Feature / 功能 | Description / 描述 |
|---|---|---|
| EX-01 | Exercise List / 动作列表 | User can browse a list of all 5 supported exercises. / 用户可浏览全部5个支持动作的列表。 |
| EX-02 | Exercise Detail / 动作详情 | User can tap an exercise to view its name, description, target muscles, required camera angle (side/front view), and a standard demonstration video. / 用户点击动作可查看名称、说明、目标肌群、所需拍摄角度及标准示范视频。 |
| EX-03 | Demo Video Playback / 示范视频播放 | Standard demo video plays inline on the exercise detail screen. / 标准示范视频在动作详情页内嵌播放。 |
| EX-04 | Start Training / 进入训练 | User can start a training session for the selected exercise directly from the detail page. / 用户可在详情页直接进入该动作的训练模式。 |

**Supported exercises / 支持动作**:

| Exercise ID | Display Name / 显示名 | Required View / 拍摄角度 |
|---|---|---|
| `squat` | Squat / 深蹲 | Side / 侧面 |
| `right_leg_lunge_to_knee_raise` | Right Leg Lunge to Knee Raise / 弓步膝举 | Side / 侧面 |
| `bicep_curl` | Bicep Curl / 二头弯举 | Side / 侧面 |
| `standing_dumbbell_shoulder_press` | Standing Dumbbell Shoulder Press / 肩上推举 | Front / 正面 |
| `dumbbell_lateral_raise` | Dumbbell Lateral Raise / 侧平举 | Front / 正面 |

### 3.3 Training Module / 训练模块

| ID | Feature / 功能 | Description / 描述 |
|---|---|---|
| TR-01 | Camera Preview / 摄像头预览 | The front camera feed is displayed full-screen with the skeleton overlaid in real time. / 前置摄像头画面全屏显示，实时叠加骨架。 |
| TR-02 | Readiness Detection / 就位检测 | The system automatically checks that the user's full body is in frame and that the camera angle matches the exercise requirement (side or front view). / 系统自动检测用户全身是否入画，以及拍摄角度是否符合当前动作要求（侧面/正面）。 |
| TR-03 | Countdown / 倒计时 | When both readiness conditions are satisfied for 3 consecutive seconds, a 3-2-1 countdown is displayed before training begins. / 两个就位条件同时满足且持续3秒后，屏幕显示3-2-1倒计时，倒计完毕训练开始。 |
| TR-04 | Skeleton Overlay / 骨架叠加 | During training, 33 key landmarks and 13 limb segments are overlaid on the camera feed; each is coloured green or red. / 训练中，33个关节点和13条肢体线段叠加在画面上，根据得分实时显示绿色或红色。 |
| TR-05 | Rep Counting / 计次 | The app automatically counts completed repetitions using a state machine that monitors key joint angles. / 应用通过监测关键关节角度变化的状态机自动计算完成次数。 |
| TR-06 | Per-rep Score / 单次得分 | Each completed repetition receives a score (0–100) based on the average per-frame score during that rep. / 每次动作结束后，系统对该次所有帧得分取平均值作为单次得分（0–100）。 |
| TR-07 | Auto Pause / 自动暂停 | When the user walks toward the phone (shoulder width increases > 1.5×) or key landmarks disappear (average visibility < 0.3 for > 3 s), recording automatically pauses. / 用户走近手机（肩宽增大超1.5倍）或关键关节消失（平均可见度 < 0.3 持续 > 3秒）时，系统自动暂停记录。 |
| TR-08 | Stop Training / 停止训练 | User can tap Stop to end the session and navigate to the summary screen. / 用户点击停止按钮结束训练，跳转到训练总结页面。 |

### 3.4 Training Summary Module / 训练总结模块

| ID | Feature / 功能 | Description / 描述 |
|---|---|---|
| SUM-01 | Total Reps / 总次数 | Displays the total number of completed repetitions in this session. / 显示本次训练完成的总次数。 |
| SUM-02 | Correct / Incorrect Rep Count / 正确与不正确次数 | Displays the number of correct reps and incorrect reps. / 显示本次训练中正确次数和不正确次数。 |
| SUM-03 | Average Score / 平均分 | Displays the average score across all repetitions. / 显示所有次数的平均分。 |
| SUM-04 | High-frequency Deviation Joints / 高频偏差关节 | Lists the joints that were most frequently scored below 80 during the session, to guide improvement. / 列出本次训练中最频繁低于80分的关节，用于指导改进。 |
| SUM-05 | Save Record / 保存记录 | The session data is automatically saved to the local Room database. / 训练数据自动保存到本地 Room 数据库。 |

### 3.5 Training Records Module / 训练记录模块

| ID | Feature / 功能 | Description / 描述 |
|---|---|---|
| REC-01 | Record List / 记录列表 | User can view a list of all past training sessions; each row shows the date and exercise type. / 用户可查看所有历史训练记录列表，每条记录显示日期和动作类型。 |
| REC-02 | Record Detail / 记录详情 | User can tap any row in the record list to open the detail page for that session. The detail page displays the full training summary: exercise name, total reps, correct rep count, incorrect rep count, average score, and high-frequency deviation joints. / 用户点击列表中任意一条记录，进入该训练记录的详情页。详情页显示完整训练总结：动作名称、总次数、正确次数、不正确次数、平均得分、高频偏差关节。 |

---

## 4. User Flow / 用户流程

### 4.1 Complete User Journey / 完整用户流程

```
App Launch / 启动 app
        │
        ▼
Login Screen / 登录页
  ├─ Register → Registration Screen → Profile Screen / 注册 → 注册页 → 个人资料页
  ├─ Login → Profile Screen / 登录 → 个人资料页
  └─ Guest → Home Screen / 游客 → 主页
                          │
                          ▼
                    Home Screen / 主页
                      ├─ Browse Exercises / 浏览动作库
                      │       │
                      │       ▼
                      │  Exercise List Screen / 动作列表页
                      │       │
                      │       ▼
                      │  Exercise Detail Screen / 动作详情页
                      │   (watch demo video / 观看示范视频)
                      │       │
                      │       └─ Start Training / 开始训练 ─────┐
                      │                                          │
                      └─ Training Records / 训练记录             │
                              │                                  │
                              ▼                                  ▼
                        Record List Screen          Training Screen / 训练页
                        / 记录列表页                       │
                              │
                    (tap a record / 点击记录)
                              │
                              ▼
                        Record Detail Screen
                        / 记录详情页
                                                   [Place phone, step back]
                                                   [放好手机，走到训练位置]
                                                           │
                                                 Readiness Detection Loop / 就位检测
                                                 ├─ Full body in frame? / 全身入画?
                                                 └─ Correct camera angle? / 角度正确?
                                                           │ Both met for 3s / 同时满足3秒
                                                           ▼
                                                    Countdown 3-2-1 / 倒计时
                                                           │
                                                           ▼
                                                  Training in Progress / 训练中
                                                  • Real-time skeleton overlay
                                                    实时骨架叠加（绿/红）
                                                  • Rep auto-counting / 自动计次
                                                  • Per-frame scoring / 逐帧评分
                                                           │
                                             ┌─────────────┴─────────────┐
                                     Auto Pause / 自动暂停         User stops / 用户按停止
                                     (user walks near /                    │
                                      用户走近手机)                        │
                                             │                             │
                                             └─────────────┬───────────────┘
                                                           ▼
                                                  Training Summary / 训练总结
                                                  • Total reps / 总次数
                                                  • Correct / incorrect reps
                                                    正确次数 / 不正确次数
                                                  • Average score / 平均分
                                                  • High-freq deviation joints
                                                    高频偏差关节
                                                  • Record auto-saved / 自动保存
                                                           │
                                                           ▼
                                                      Home Screen / 主页
```

### 4.2 Training Flow Detail / 训练流程详细说明

1. **Position phone / 放置手机**: User places phone on a stable surface facing forward.
   用户将手机放置在稳定位置，镜头朝向训练区域。

2. **Step back / 走到训练位置**: User steps back into the camera's field of view.
   用户走到镜头前的训练位置。

3. **Readiness check / 就位检测**: System checks two conditions simultaneously:
   系统同时检测两个条件：
   - All 9 key landmarks (nose, both shoulders, both hips, both knees, both ankles) have visibility > 0.5 / 9个关键关节可见度均 > 0.5
   - Camera angle matches the exercise requirement (side or front view) / 拍摄角度符合动作要求

4. **Countdown / 倒计时**: Both conditions satisfied for 3 s → countdown 3, 2, 1.
   两个条件同时满足并持续3秒 → 屏幕显示3、2、1倒计时。

5. **Training / 训练**: Each camera frame is processed by the full algorithm pipeline:
   每一帧画面经过完整算法流程处理：
   MediaPipe → Normalization → OE-DTW → Scoring → Skeleton colouring + rep counting
   MediaPipe → 归一化 → OE-DTW → 评分 → 骨架着色 + 计次

6. **Auto-end detection / 自动结束检测**: System detects user approaching phone and pauses.
   系统检测到用户走近手机时自动暂停记录。

7. **Stop / 停止**: User taps Stop → navigate to Training Summary.
   用户按停止 → 跳转训练总结页。

---

## 5. Core Algorithm Overview / 核心算法概述

> This section provides a high-level summary. Full implementation specifications are in **ALGORITHM.md**.
> 本节为概要说明，完整实现规范见 **ALGORITHM.md**。

### 5.1 Skeleton Normalization / 骨架归一化

**Purpose / 目的**: Remove differences due to user height and position so that user pose data is directly comparable with the standard reference data.
消除用户身高和站位差异，使用户骨架数据可与标准数据直接比较。

**Two-step process / 两步处理**:
1. **Translation / 平移**: Subtract the hip midpoint from all 33 landmarks, centering the skeleton at the origin.
   以髋关节中点为原点，所有关节坐标减去髋关节中点，消除位置差异。
2. **Scaling / 缩放**: Divide all translated coordinates by the torso length T (distance from shoulder midpoint to hip midpoint), normalizing the torso length to 1.0.
   除以躯干长度 T（肩膀中点到髋关节中点的距离），使躯干长度归一化为1.0，消除身高差异。

The z-axis (depth) is discarded; output is `List<Pair<Float, Float>>` (33 pairs).
丢弃 z 轴深度信息，输出 `List<Pair<Float, Float>>`（33对坐标）。

### 5.2 OE-DTW Real-time Alignment / OE-DTW 实时对齐

**Purpose / 目的**: For each incoming camera frame, find the index of the frame in the standard reference sequence that best matches the user's current pose.
对每帧输入，找到标准动作序列中与用户当前帧最匹配的帧编号，供后续评分使用。

**Why OE-DTW / 为什么用 OE-DTW**: Classic DTW requires two complete sequences; OE-DTW supports incomplete sequences and updates incrementally per frame, making it suitable for real-time use.
普通 DTW 需要完整序列才能对齐；OE-DTW 支持不完整序列并逐帧增量更新，适合实时场景。

**Warm-up period / 热身期**: Returns -1 for the first 20 frames (sequence too short for stable alignment); the caller displays all joints in green during this period.
前20帧序列过短，返回-1；调用方在此期间将所有关节显示为绿色。

### 5.3 Per-landmark Scoring / 逐关节评分

Each frame is scored in three steps:
每帧评分分三步：

**Step 1 — Joint position score S1 / 关节位置得分 S1**
For each of 33 joints, calculate the Euclidean distance Dp between the user's normalised joint and the reference joint. Convert to a 0–100 score:
对每个关节计算欧氏距离 Dp，转化为0–100分：
```
Sp = max(0, (1 − Dp) × 100)
S1 = average of all 33 Sp values
```

**Step 2 — Limb angle score S2 / 肢体角度得分 S2**
For each of 13 defined limbs, compute the angle Dl (degrees) between the user's limb vector and the reference limb vector using cosine similarity. Convert to a 0–100 score:
对13条肢体用余弦相似度计算用户肢体与标准肢体夹角 Dl（度），转化为0–100分：
```
Sl = (1 − Dl / 180) × 100
S2 = average of all 13 Sl values
```

**Step 3 — Overall frame score Sf / 整体帧得分 Sf**
```
Sf = 0.2 × S1 + 0.8 × S2
```
Angle quality accounts for 80% of the score because exercise form is primarily measured by joint angles.
角度质量占80%权重，因为动作质量主要体现在关节角度是否到位。

**Colour thresholds / 颜色阈值**:
- Score ≥ 80 → **GREEN / 绿色** (acceptable)
- Score < 80 → **RED / 红色** (needs correction)

**Rep score / 单次得分**: When the state machine signals rep completion, all Sf values during that rep are averaged to produce the rep score.
状态机通知本次动作完成时，对该次所有帧的 Sf 取平均值作为单次得分。

### 5.4 State Machine Rep Counting / 状态机计次

**Purpose / 目的**: Detect complete exercise repetitions by monitoring key joint angles.
通过监测关键关节角度变化，识别完整动作循环并计次。

**Three states / 三种状态**:
- **S1**: Starting position / 起始位置
- **S2**: Transition phase / 过渡阶段
- **S3**: Peak position (maximum range of motion) / 终止位置（最大幅度）

A repetition is only counted when the full cycle **S1 → S2 → S3 → S2 → S1** completes.
只有完整走过 S1→S2→S3→S2→S1 才计一次。

**Exercise-specific key angles / 各动作关键角度**:

| Exercise / 动作 | Key joints / 关键关节 | S1 threshold / S1 阈值 | S3 threshold / S3 阈值 |
|---|---|---|---|
| `squat` | Hip–Knee–Ankle | ≥ 160° (standing) | ≤ 90° (squat) |
| `right_leg_lunge_to_knee_raise` | Hip–Knee–Ankle (right leg) | ≥ 160° (standing) | ≤ 90° (lunge) |
| `bicep_curl` | Wrist–Elbow–Shoulder | ≥ 160° (arm down) | ≤ 50° (curl top) |
| `standing_dumbbell_shoulder_press` | Wrist–Elbow–Shoulder | ≤ 90° (start) | ≥ 160° (press top) |
| `dumbbell_lateral_raise` | Hip–Shoulder–Wrist | ≤ 30° (arm down) | ≥ 80° (raise top) |

### 5.5 Camera Angle & Readiness Detection / 拍摄角度与就位检测

**Angle detection / 角度检测**: Calculate angle θ at the nose landmark (index 0) between the left shoulder (11) and right shoulder (12) vectors:
以鼻子为顶点，计算左肩和右肩之间的夹角 θ：
- θ > 150° → **Front view / 正面**
- θ < 60° → **Side view / 侧面**
- 60° ≤ θ ≤ 150° → **Ambiguous / 角度不明确** — prompt user to adjust / 提示用户调整

**Full-body-in-frame check / 全身入画检测**: All 9 key landmarks (nose, left/right shoulder, left/right hip, left/right knee, left/right ankle) must have MediaPipe visibility score > 0.5.
9个关键关节（鼻子、左右肩、左右髋、左右膝、左右踝）的 MediaPipe 可见度均须 > 0.5。

### 5.6 Training End Auto-detection / 训练结束自动检测

Triggered by either condition:
满足任一条件即触发暂停：

1. **Landmark disappearance / 关节消失**: Average visibility of nose, both shoulders, both hips drops below 0.3 for more than 3 seconds.
   鼻子、左右肩、左右髋的平均可见度 < 0.3 持续超过3秒。
2. **User approaching / 用户走近**: Current shoulder width > 1.5 × shoulder width at training start.
   当前肩宽 > 训练开始时肩宽 × 1.5。

Any incomplete repetition at the time of trigger is discarded.
触发时正在进行中的未完成次数被丢弃。

---

## 6. Technical Architecture / 技术架构

### 6.1 Module Responsibilities / 模块职责

```
┌─────────────────────────────────────────────────────────────────┐
│                        UI Layer / 界面层                          │
│  LoginScreen  RegisterScreen  ProfileScreen  HomeScreen          │
│  ExerciseListScreen  ExerciseDetailScreen                        │
│  TrainingScreen  TrainingSummaryScreen                           │
│  RecordListScreen  RecordDetailScreen                            │
└──────────────────────────┬──────────────────────────────────────┘
                           │ observes state / 观察状态
┌──────────────────────────▼──────────────────────────────────────┐
│                     ViewModel Layer / ViewModel层                 │
│  UserViewModel       ExerciseViewModel                           │
│  TrainingViewModel   RecordViewModel                             │
└────────┬─────────────────────┬────────────────────────────────── ┘
         │                     │ calls use cases / 调用用例
┌────────▼──────────┐ ┌────────▼──────────────────────────────────┐
│  Domain Layer     │ │          Algorithm Layer / 算法层           │
│  /领域层           │ │  Normalization  OE-DTW  Scoring            │
│  UserUseCase      │ │  StateMachine  AngleDetection  EndDetection│
│  GetExercisesUC   │ └────────────────────────┬───────────────────┘
│  DetectPoseUC     │                          │ receives pose data
│  CountRepsUC      │ ┌────────────────────────▼───────────────────┐
│  EvaluateExercUC  │ │       Perception Layer / 感知层             │
│  SaveRecordUC     │ │  CameraX (front camera preview)            │
└────────┬──────────┘ │  MediaPipe Pose (33 landmarks per frame)   │
         │            │  PoseLandmarkerHelper / PoseResult          │
┌────────▼──────────────────────────────────────────────────────── ┐
│                     Data Layer / 数据层                           │
│  UserRepository  ExerciseRepository  TrainingRepository          │
│  RecordRepository                                                │
│  Room Database (AppDatabase) — local only / 纯本地存储            │
│  Assets: landmarks/squat.json … (raw standard pose data)        │
│  res/raw: squat_demo.mp4 … (demo videos)                        │
└──────────────────────────────────────────────────────────────────┘
```

### 6.2 Data Flow — One Training Frame / 单帧数据流

```
CameraX frame
      │
      ▼
PoseLandmarkerHelper (MediaPipe)
      │  List<Triple<Float,Float,Float>>  (33 raw landmarks)
      ▼
Normalization (Module 1)
      │  List<Pair<Float,Float>>  (33 normalised landmarks, z dropped)
      ▼
OE-DTW (Module 2)
      │  matchedReferenceIndex: Int
      ▼
Scoring (Module 3)
      │  jointColors: List<Color> [33]
      │  limbColors:  List<Color> [13]
      │  sf:          Float (0–100)
      ├──────────────────────────────────────────────────────►
      │                                            Skeleton overlay on canvas
      ▼                                            骨架叠加显示
State Machine (Module 4)
      │  repCompleted: Boolean
      │  repCount: Int
      ▼
TrainingViewModel
      │  updates UI state (rep count, scores, overlay)
      ▼
TrainingScreen (Compose)
```

### 6.3 Standard Data Loading / 标准数据加载

```
App startup / app启动
      │
      ▼
Load JSON from assets/ (e.g. squat.json)
      │  List<List<Triple<Float,Float,Float>>>
      ▼
Normalization (same function as real-time pipeline / 与实时流程相同函数)
      │  List<List<Pair<Float,Float>>>
      ▼
Cached in memory per exercise / 按动作缓存在内存中
      │
      └──► Used by OE-DTW as referenceSequence
```

### 6.4 Navigation Routes / 导航路由

| Route / 路由 | Screen / 页面 | Notes / 备注 |
|---|---|---|
| `home` | HomeScreen | Start destination / 起始页 |
| `user` | LoginScreen | Auth gate / 认证入口 |
| `register` | RegisterScreen | — |
| `profile` | ProfileScreen | Requires login / 需登录 |
| `exercise_list` | ExerciseListScreen | — |
| `exercise_detail/{id}` | ExerciseDetailScreen | With exercise ID param |
| `training/{exerciseId}` | TrainingScreen | Active training session |
| `training_result/{exerciseId}/{repCount}/{avgScore}` | ResultScreen | Post-session summary |
| `record_list` | RecordListScreen | Training history list |
| `record_detail/{id}` | RecordDetailScreen | Detail view of one record |

### 6.5 Local Database Schema / 本地数据库 Schema

**Users table / 用户表**

| Column / 列 | Type / 类型 | Notes / 备注 |
|---|---|---|
| id | INTEGER PK | Auto-generated |
| username | TEXT | Unique |
| password | TEXT | SHA-256 hash |
| email | TEXT | — |
| height | REAL | cm |
| weight | REAL | kg |

**Exercises table / 动作表** (pre-seeded / 预置数据)

| Column / 列 | Type / 类型 | Notes / 备注 |
|---|---|---|
| id | INTEGER PK | — |
| name | TEXT | Exercise name |
| description | TEXT | — |
| requiredView | TEXT | "side" or "front" |
| demoVideoRes | TEXT | res/raw resource name |
| landmarksAsset | TEXT | assets/ file name |

**TrainingRecords table / 训练记录表**

| Column / 列 | Type / 类型 | Notes / 备注 |
|---|---|---|
| id | INTEGER PK | Auto-generated |
| userId | INTEGER FK | Null for guest |
| exerciseId | INTEGER FK | — |
| timestamp | INTEGER | Unix epoch ms |
| totalReps | INTEGER | — |
| correctReps | INTEGER | Reps with no visible red joints / 单次动作看到骨架全绿为正确 |
| incorrectReps | INTEGER | Reps with visible red joints / 单次动作看到有部位标红算错误 |
| averageScore | REAL | — |
| highFreqDeviationJoints | TEXT | JSON array of Int (landmark indices) |

---

## 7. Non-functional Requirements / 非功能需求

### 7.1 Performance / 性能

| Requirement / 需求 | Target / 目标 |
|---|---|
| Camera frame processing rate / 摄像头处理帧率 | ≥ 20 FPS during live training / 训练时 ≥ 20 FPS |
| OE-DTW incremental update latency / OE-DTW增量更新延迟 | < 50 ms per frame / 每帧 < 50 ms |
| Skeleton overlay rendering / 骨架叠加渲染 | No visible lag relative to live video / 相对实时画面无明显延迟 |
| App cold start / 冷启动 | < 3 s to reach Home screen / < 3秒到达主页 |
| Standard data loading / 标准数据加载 | JSON parsed and normalised before training screen opens / 进入训练页前完成解析和归一化 |

### 7.2 Storage / 存储

| Requirement / 需求 | Specification / 说明 |
|---|---|
| Local database / 本地数据库 | Room (SQLite), stored on device, no cloud sync / Room (SQLite)，本地存储，无云端同步 |
| Standard pose data / 标准姿态数据 | JSON files bundled in `assets/landmarks/` / 打包在 `assets/landmarks/` |
| Demo videos / 示范视频 | MP4 files bundled in `res/raw/` / 打包在 `res/raw/` |
| User data / 用户数据 | Stored entirely on-device / 全部存储在设备本地 |

### 7.3 Offline Operation / 离线运行

The app must run fully offline. All pose detection (MediaPipe), standard reference data, and demo videos are bundled in the APK. No network permission is required.
应用必须完全离线运行。所有姿态检测（MediaPipe）、标准参考数据和示范视频均打包在 APK 中，无需网络权限。

### 7.4 Device Compatibility / 设备兼容性

| Requirement / 需求 | Specification / 说明 |
|---|---|
| Minimum Android version / 最低 Android 版本 | API 26 (Android 8.0) |
| Target Android version / 目标 Android 版本 | API 36 |
| Camera / 摄像头 | Front-facing camera required / 需要前置摄像头 |
| Architecture / CPU 架构 | arm64-v8a (MediaPipe requirement) |

### 7.5 Accuracy / 准确性

| Requirement / 需求 | Target / 目标 |
|---|---|
| Rep counting accuracy / 计次准确率 | Error ≤ 1 rep per 10 reps for each exercise / 每10次误差 ≤ 1次 |
| Normalization precision / 归一化精度 | Hip midpoint error < 0.001; torso length error < 0.001 / 髋关节中点误差 < 0.001；躯干长度误差 < 0.001 |
| OE-DTW sanity check / OE-DTW 一致性校验 | Identical input sequences → matched index = userSequence.size − 1 / 相同序列输入 → 匹配帧编号 = userSequence.size − 1 |
| Scoring sanity check / 评分一致性校验 | Identical user and reference landmarks → Sf ≥ 95 / 用户与参考关节完全相同 → Sf ≥ 95 |

### 7.6 Security & Privacy / 安全与隐私

- Camera data is processed in-memory only and never written to disk or transmitted.
  摄像头数据仅在内存中处理，不写入磁盘，不对外传输。
- User passwords are stored locally as SHA-256 hashes.
  用户密码以 SHA-256 哈希值存储在本地。
- No analytics, crash reporting, or network calls are made.
  应用不发送任何统计、崩溃报告或网络请求。

---

## 8. Out of Scope / 超出范围

The following features are **not** included in this version:
以下功能**不**包含在本版本中：

- Cloud synchronisation of training records / 训练记录的云端同步
- Social features (sharing, leaderboards) / 社交功能（分享、排行榜）
- Personalised training plan recommendations / 个性化训练计划推荐
- Additional exercises beyond the 5 listed / 5个动作以外的其他动作
- Real-time voice coaching / 实时语音教练
- Wearable device integration / 可穿戴设备集成

---

*For algorithm implementation details, refer to **ALGORITHM.md**.*
*算法实现详情请参见 **ALGORITHM.md**。*

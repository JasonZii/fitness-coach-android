![Kotlin](https://img.shields.io/badge/Kotlin-2.0-blue)
![Platform](https://img.shields.io/badge/Platform-Android-green)
![Architecture](https://img.shields.io/badge/Architecture-Clean%20Architecture-orange)
![Min SDK](https://img.shields.io/badge/Min%20SDK-26-yellow)
![License](https://img.shields.io/badge/License-Academic-lightgrey)

# Fitness Coach App

## Overview / 产品概述

Fitness Coach is an Android application that uses the device's front-facing camera and **MediaPipe Pose** to deliver real-time exercise coaching. During a workout, the app overlays a colour-coded skeleton on the live video feed — joints and limbs within acceptable deviation are shown in **green**, while those needing correction are shown in **red**. The app supports five exercises, automatically counts repetitions, stores training history locally, and runs fully offline.

Fitness Coach 是一款 Android 健身教练应用，利用前置摄像头和 **MediaPipe Pose** 提供实时训练指导。训练时，应用将彩色骨架叠加在实时画面上：姿势正确的关节/肢体显示为**绿色**，需要纠正的显示为**红色**。应用支持5个健身动作，自动计次，本地存储训练记录，完全离线运行。

---

## Supported Exercises / 支持的动作

| Exercise / 动作 | Required View / 拍摄角度 |
|---|---|
| Squat / 深蹲 | Side / 侧面 |
| Lunge / 弓步 | Side / 侧面 |
| Bicep Curl / 二头弯举 | Side / 侧面 |
| Shoulder Press / 肩上推举 | Side / 侧面 |
| Lateral Raise / 侧平举 | Front / 正面 |

---

## User Roles / 用户角色

**Registered User / 注册用户**: Creates an account with username, password, email, height, and weight. Training records are saved locally and linked to the account.
注册用户填写用户名、密码、邮箱、身高、体重创建账号。训练记录保存在本地并关联账号。

**Guest User / 游客**: Skips login and accesses all exercise and training features. Records are saved locally but not linked to any account.
游客跳过登录，可使用所有动作和训练功能。记录保存在本地，不关联账号。

---

## Key Features / 核心功能

- **Real-time skeleton overlay / 实时骨架叠加**: 33 key joints and 13 limb segments are drawn over the camera feed each frame, coloured green or red based on deviation from the reference pose.
  每帧在摄像头画面上绘制33个关节点和13条肢体线段，根据与标准姿势的偏差实时显示绿色或红色。

- **Automatic readiness detection / 自动就位检测**: Before training begins, the system verifies that the user's full body is in frame and the camera angle matches the exercise requirement (side or front view). Both conditions must hold for 3 seconds before the 3-2-1 countdown starts.
  训练开始前，系统验证全身是否入画及拍摄角度是否正确，两个条件同时满足3秒后启动3-2-1倒计时。

- **Automatic rep counting / 自动计次**: A three-state machine (start → transition → peak) monitors key joint angles to detect complete repetitions.
  三状态机（起始→过渡→终止）监测关键关节角度变化，自动检测完整动作次数。

- **Per-rep scoring / 单次得分**: Each repetition receives a score (0–100) based on joint position accuracy (20%) and limb angle accuracy (80%).
  每次动作按关节位置准确度（20%）和肢体角度准确度（80%）综合评分（0–100）。

- **Training summary / 训练总结**: After each session, the app shows total reps, per-rep scores, average score, and the most frequently incorrect joints.
  每次训练结束后显示总次数、每次得分、平均分及高频偏差关节。

- **Local history / 本地记录**: All session data is stored in a Room (SQLite) database on the device. No network connection is required.
  所有训练数据存储在设备本地的 Room 数据库，无需网络连接。

---

## Algorithm Pipeline / 算法流程

Each camera frame passes through the following pipeline during training:
每帧训练数据经过以下流程处理：

```
CameraX frame
    ↓
MediaPipe Pose  →  33 raw landmarks per frame / 每帧33个原始关节坐标
    ↓
Normalization (Module 1)
    Translate to hip midpoint origin, scale by torso length
    平移至髋关节中点为原点，除以躯干长度归一化
    ↓
OE-DTW Alignment (Module 2)
    Find matching frame in standard reference sequence
    在标准动作序列中找到对应帧
    ↓
Per-landmark Scoring (Module 3)
    Sf = 0.2 × S1 (position) + 0.8 × S2 (angle)
    → joint colors (33) + limb colors (13) + frame score
    ↓
State Machine (Module 4)
    Detect completed rep cycle: S1 → S2 → S3 → S2 → S1
    检测完整动作循环并计次
```

Standard reference data (JSON files in `assets/landmarks/`) is loaded at startup and normalised with the same function used for live frames. Full algorithm specifications are in **`ALGORITHM.md`**.

标准参考数据（`assets/landmarks/` 中的 JSON 文件）在启动时加载，使用与实时帧相同的归一化函数处理。完整算法规范见 **`ALGORITHM.md`**。

---

## Module Description / 模块说明

### App
- Entry activity: `MainActivity.kt`
- Hilt application class: `FitnessApp.kt`
- Navigation graph and route constants: `NavGraph.kt`, `BottomNavItem.kt`

### Core
- Shared data models: `core/model/`
- CameraX wrapper: `core/camera/`
- MediaPipe pose helper: `core/mediapipe/`
- Reusable Compose components: `core/ui/`
- App-wide constants and extensions: `core/util/`

### User
- Login, registration, guest mode, and profile screens: `user/ui/`
- `UserViewModel` — manages auth state with `mutableStateOf`
- `UserRepository` (interface) + `UserLocalDataSource` — local credential storage with SHA-256 password hashing

### Exercise
- Exercise list and detail screens with inline demo video playback: `exercise/ui/`
- `ExerciseViewModel` + `ExerciseRepository`
- Static exercise data pre-seeded into the Room database

### Training
- Live camera preview with real-time skeleton overlay: `training/ui/`
- `training/pose/` — normalization (Module 1), OE-DTW alignment (Module 2), readiness detection (Module 5), training-end detection (Module 6)
- `training/evaluation/` — per-landmark scoring and skeleton colour logic (Module 3)
- `training/domain/CountRepsUseCase` — state machine rep counting (Module 4)
- `TrainingViewModel` drives all UI state updates each frame

### Record
- Training history list: `record/ui/`
- Training summary screen shown immediately after each session
- `RecordViewModel` + `RecordRepository` read/write `TrainingRecords` in Room

### ui.theme
- Material3 colour scheme, typography, and theme composable

---

## Project Structure / 项目结构

```
app/src/main/
├── java/com/example/fitnesscoach/
│   ├── app/
│   │   └── navigation/         # NavGraph, BottomNavItem, FitnessBottomBar
│   ├── core/
│   │   ├── camera/             # CameraX wrapper
│   │   ├── mediapipe/          # PoseLandmarkerHelper, PoseResult
│   │   ├── model/              # User, Exercise, TrainingRecord
│   │   └── ui/                 # Shared composables
│   │   └── util/               # Constants, Extensions
│   ├── user/
│   │   ├── ui/                 # LoginScreen, RegisterScreen, ProfileScreen
│   │   ├── viewmodel/
│   │   ├── data/
│   │   └── domain/
│   ├── exercise/
│   │   ├── ui/                 # ExerciseListScreen, ExerciseDetailScreen
│   │   ├── viewmodel/
│   │   ├── data/
│   │   └── domain/
│   ├── training/
│   │   ├── ui/                 # TrainingScreen, TrainingSummaryScreen
│   │   ├── viewmodel/
│   │   ├── pose/               # Normalization, OE-DTW, readiness, end-detection
│   │   ├── evaluation/         # Scoring, skeleton colour
│   │   ├── data/
│   │   └── domain/             # DetectPoseUseCase, CountRepsUseCase, EvaluateExerciseUseCase
│   ├── record/
│   │   ├── ui/                 # RecordScreen
│   │   ├── viewmodel/
│   │   ├── data/
│   │   └── domain/
│   ├── home/
│   │   └── ui/                 # HomeScreen
│   ├── data/local/             # AppDatabase (Room)
│   └── ui/theme/               # Color, Type, Theme
└── assets/
    └── landmarks/              # squat.json, lunge.json, bicep_curl.json,
                                # shoulder_press.json, lateral_raise.json
res/
└── raw/                        # squat_demo.mp4, lunge_demo.mp4,
                                # bicep_curl_demo.mp4, shoulder_press_demo.mp4,
                                # lateral_raise_demo.mp4
```

---

## Tech Stack / 技术栈

| Technology | Purpose / 用途 |
|---|---|
| Kotlin 2.0 | Primary language / 主要语言 |
| Jetpack Compose + Material3 | Declarative UI / 声明式 UI |
| Clean Architecture (MVVM) | Layer separation / 分层架构 |
| Hilt (Dagger) | Dependency injection / 依赖注入 |
| Navigation Compose | In-app navigation / 应用内导航 |
| CameraX | Front-camera feed / 前置摄像头 |
| MediaPipe Pose (BlazePose) | 33-landmark pose detection / 33关节点姿态检测 |
| Room (SQLite) | Local training history / 本地训练记录 |

---

## Build & Run / 构建与运行

### Prerequisites / 前提条件
- Android Studio Hedgehog or later / Android Studio Hedgehog 或更新版本
- JDK 17
- Android device or emulator with API 26+ / API 26+ 的真机或模拟器

### Common Commands / 常用命令

```bash
# Build debug APK / 构建调试 APK
./gradlew assembleDebug

# Install on connected device / 安装到设备
./gradlew installDebug

# Run unit tests / 运行单元测试
./gradlew test

# Run a single test class / 运行单个测试类
./gradlew :app:test --tests "com.example.fitnesscoach.NormalizeLandmarksTest"

# Run instrumented tests / 运行仪器化测试
./gradlew connectedAndroidTest

# Lint check / 代码检查
./gradlew lint

# Clean and rebuild / 清理并重新构建
./gradlew clean assembleDebug
```

### Running on Emulator / 在模拟器上运行
1. Verify `AndroidManifest.xml` has the launcher `<activity>` correctly configured.
   确认 `AndroidManifest.xml` 中 launcher `<activity>` 配置正确。
2. Verify `res/mipmap/ic_launcher` exists.
   确认 `res/mipmap/ic_launcher` 图标文件存在。
3. In Android Studio: **Run → Cold Boot Now** on the emulator, then click Run.
   在 Android Studio 中：对模拟器执行 **Run → Cold Boot Now**，然后点击运行。

---

## Team & Module Ownership / 团队与模块分工

| Module / 模块 | Owner / 负责人 | Responsibility / 职责 |
|---|---|---|
| `core/mediapipe/`, `CountRepsUseCase` | Lee | CameraX, MediaPipe integration, state machine rep counting |
| `training/pose/` | Daniel | Normalization, OE-DTW, readiness & end detection |
| `training/evaluation/` | Jason | Scoring, skeleton overlay UI |
| `user/`, `exercise/` UI & data | Jason | Auth, exercise library, Room database |
| `record/` UI, training summary | Helen | History screen, training summary screen |
| Standard data & report | Helen | JSON extraction from reference videos, capstone report |

---

## Reference Documents / 参考文档

| Document | Contents / 内容 |
|---|---|
| `PRD.md` | Full product requirements, user flows, non-functional requirements / 完整产品需求、用户流程、非功能需求 |
| `ALGORITHM.md` | Algorithm module specs: function signatures, data types, acceptance criteria / 算法模块规范：函数签名、数据类型、验收标准 |
| `RULES.md` | Coding conventions: SOLID/DRY/KISS, naming, Compose patterns, constants, testing / 编码规范：SOLID/DRY/KISS、命名、Compose 写法、常量管理、测试要求 |

---

## License / 许可证

This project is developed for **academic and research purposes** (CSIT998 Capstone Project, University of Wollongong).
本项目为**学术和研究用途**开发（卧龙岗大学 CSIT998 毕业课题）。

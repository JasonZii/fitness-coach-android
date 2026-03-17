![Kotlin](https://img.shields.io/badge/Kotlin-Android-blue)
![Platform](https://img.shields.io/badge/Platform-Android-green)
![Architecture](https://img.shields.io/badge/Architecture-MVVM-orange)

# Fitness Coach App

A modern Android fitness coaching application built with **Kotlin and Jetpack Compose**.  
The app helps users learn exercises, perform workouts, and track training records. Future versions will integrate **computer vision (MediaPipe Pose)** to provide real-time posture analysis and automatic repetition counting.

---

## Main Module Description

### 1. App
- Startup Activity: `MainActivity.kt`  
- Compose App entry point: `FitnessCoachApp.kt`  
- Manages global navigation: `NavGraph.kt`

### 2. Core
- Shared utilities and UI components  
- CameraX wrapper / MediaPipe Pose helpers / data models  

### 3. User
- Login, registration, and user profile screens  
- `UserViewModel` manages user state  
- Repository / LocalDataSource handle user data  

### 4. Exercise
- Displays exercise list and exercise details  
- `ExerciseViewModel` manages exercise data  
- Repository retrieves exercise data  

### 5. Training
- Core training mode, with future integration of Camera + MediaPipe  
- `DetectPose` / `EvaluateExercise` / `CountReps` implement pose detection and repetition counting  

### 6. Record
- Saves and displays training records  
- `RecordViewModel` + Repository manage record data  

### 7. ui.theme
- Compose styling and theme configuration  

---

## Project Structure
app/
├── core/
│ ├── camera/
│ ├── mediapipe/
│ ├── model/
│ └── ui/
│
├── user/
│ ├── data/
│ ├── repository/
│ ├── viewmodel/
│ └── ui/
│
├── exercise/
│ ├── repository/
│ ├── viewmodel/
│ └── ui/
│
├── training/
│ ├── pose/
│ ├── evaluation/
│ └── ui/
│
├── record/
│ ├── repository/
│ ├── viewmodel/
│ └── ui/
│
└── ui/theme/


---

## Running Instructions

1. Ensure the **Launcher Activity** is correctly configured in `AndroidManifest`
2. Ensure the `res/mipmap` directory contains the `ic_launcher` icon
3. Run → Cold Boot the emulator → The app will install successfully
4. The app icon appears on the home screen; open it to launch the Compose interface

---

## Tech Stack

- **Kotlin**
- **Jetpack Compose**
- **MVVM Architecture**
- **CameraX** (planned integration)
- **MediaPipe Pose** (planned integration)
- **Room Database** (optional future extension)

---

## Future Improvements

- Real-time pose detection using MediaPipe
- AI-based exercise evaluation
- Automatic repetition counting
- Cloud-based workout record synchronization
- Personalized training recommendations

---

## License

This project is developed for **learning and research purposes**.



---

## 主要模块说明

### 1. App
- 启动 Activity：`MainActivity.kt`  
- Compose App 入口：`FitnessCoachApp.kt`  
- 管理全局 Navigation：`NavGraph.kt`  

### 2. Core
- 公共工具和 UI 组件  
- CameraX 封装 / MediaPipe Pose 辅助 / 数据模型  

### 3. User
- 登录、注册、用户资料界面  
- UserViewModel 管理用户状态  
- Repository / LocalDataSource 处理数据  

### 4. Exercise
- 展示训练动作列表及详情  
- ExerciseViewModel 管理 Exercise 数据  
- Repository 获取动作数据  

### 5. Training
- 核心训练模式，未来接入 Camera + MediaPipe  
- DetectPose / EvaluateExercise / CountReps 实现动作识别与计数  

### 6. Record
- 保存与展示训练记录  
- RecordViewModel + Repository 管理数据  

### 7. ui.theme
- Compose 样式与主题  

---

## 运行说明

1. 确认 AndroidManifest 中 Launcher Activity 设置正确  
2. 确认 `res/mipmap` 下有 `ic_launcher` 图标  
3. Run → 模拟器 Cold Boot → App 安装成功  
4. 主屏幕显示 App 图标，打开后显示 Compose 界面  

---

## 技术栈

- Kotlin + Jetpack Compose  
- CameraX (后续接入)  
- MediaPipe Pose (后续接入)  
- Android Architecture: ViewModel + UseCase + Repository  
- Room / Optional Backend (后续扩展)  

---


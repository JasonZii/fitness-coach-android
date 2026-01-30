
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


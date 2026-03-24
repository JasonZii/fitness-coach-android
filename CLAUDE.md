# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

## Build & Test Commands

```bash
./gradlew assembleDebug                  # build debug APK
./gradlew installDebug                   # install on connected device/emulator
./gradlew test                           # run all unit tests
./gradlew :app:test --tests "com.example.fitnesscoach.NormalizeLandmarksTest"  # single test class
./gradlew connectedAndroidTest           # instrumented tests (requires device)
./gradlew lint                           # lint check
./gradlew clean assembleDebug            # clean rebuild
```

**Note**: MediaPipe and Room are not yet added to `app/build.gradle.kts`. Add them before implementing the training pipeline or database layer.

---

## Architecture

Feature-first Clean Architecture. Each feature (`user`, `exercise`, `training`, `record`, `home`) contains `ui/`, `viewmodel/`, `data/`, and `domain/` sub-packages. Shared code lives in `core/`.

**Dependency injection**: Hilt (`@HiltAndroidApp` on `FitnessApp`). KAPT is used for code generation — do not switch to KSP without updating the plugin.

**State management**: ViewModels use `var x by mutableStateOf("")` (delegated Compose state), not `StateFlow` or `LiveData`. Screen-local ephemeral state uses `by remember { mutableStateOf(...) }` inside composables.

**Navigation**: All route strings are `const val` in `object Routes` inside `NavGraph.kt`. Always reference `Routes.X` — never write raw route strings. The start destination is `Routes.HOME`.

---

## Training Pipeline (most complex part)

The per-frame data flow during a live training session:

```
CameraX → PoseLandmarkerHelper (MediaPipe)
        → normalizeLandmarks()       [training/pose/]
        → alignOeDtw()               [training/pose/]
        → scoreFrame()               [training/evaluation/]
        → CountRepsUseCase           [training/domain/]
        → TrainingViewModel → TrainingScreen
```

Standard reference poses are stored as raw JSON in `assets/landmarks/` (one file per exercise). They are loaded at startup and normalised with the **same** `normalizeLandmarks()` function used for live frames — never duplicate or inline this logic.

**Read `ALGORITHM.md` before touching anything in `training/pose/` or `training/evaluation/`.** It defines exact function signatures, data types (`List<Triple<Float,Float,Float>>` before normalisation, `List<Pair<Float,Float>>` after), acceptance criteria, and thresholds for all six algorithm modules. All numeric thresholds must be defined as named constants in `core/util/Constants.kt` — see `RULES.md` §4.5.

---

## Module Ownership

| Package | Owner |
|---|---|
| `core/mediapipe/`, `training/domain/CountRepsUseCase` | Lee |
| `training/pose/` (normalisation, OE-DTW, readiness, end-detection) | Daniel |
| `training/evaluation/` (scoring, skeleton overlay) | Jason |
| `user/`, `exercise/` UI & data, Room database | Jason |
| `record/` UI, training summary screen | Helen |

Do not edit files outside your assigned module without coordinating with the owner.

---

## Key Constraints from PRD / RULES

- **Offline only**: no network calls, no cloud sync. All assets bundled in APK.
- **Algorithm layer** (`training/pose/`, `training/evaluation/`) must not import any Compose or Android UI classes — pure Kotlin only.
- **UI layer** (`*/ui/`) must not contain business logic or algorithm math.
- **Material3 only**: do not mix `androidx.compose.material3` with `androidx.compose.material`.
- Passwords stored as SHA-256 hashes.
- Every public function requires at least one unit test (see `RULES.md` §4.4 for required acceptance-criteria tests).

---

## Current Progress

### Completed today (2026-03-24) / 今日已完成

- [x] `ALGORITHM.md` — full interface contracts for all algorithm modules / 算法模块完整接口约定
- [x] `PRD.md` — product requirements document / 产品需求文档
- [x] `RULES.md` — coding conventions / 编码规范
- [x] `README.md` — project overview and module description / 项目概述和模块说明
- [x] `CLAUDE.md` — Claude Code configuration / Claude Code 配置文件

### Sprint 1 — Pending / Sprint 1 待完成

- [ ] Skeleton normalisation function — Kotlin implementation + unit tests / 骨架归一化函数（Kotlin实现，含单元测试）
- [ ] OE-DTW core logic — Kotlin implementation + unit tests / OE-DTW核心逻辑（Kotlin实现，含单元测试）

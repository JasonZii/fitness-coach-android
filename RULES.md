# Fitness Coach — Coding Rules & Conventions

> All contributors must follow these rules. They apply to every new file and every modification to existing files.
> Reference docs: `PRD.md`, `ALGORITHM.md`

---

## 1. SOLID Principles

### Single Responsibility Principle (SRP)
Each class or function has exactly one reason to change.

**Project example**: `EvaluateExerciseUseCase` calculates per-frame scores and joint colors only. It does not count reps, render the skeleton, or touch the database. Rep counting belongs solely to `CountRepsUseCase`.

---

### Open/Closed Principle (OCP)
Classes are open for extension but closed for modification.

**Project example**: `ExerciseConfig` (state-machine thresholds per exercise) should be modeled as a sealed class or data class so that adding a sixth exercise requires adding a new subclass — not editing existing switch/when branches in `CountRepsUseCase`.

---

### Liskov Substitution Principle (LSP)
Subtypes must behave correctly wherever their parent type is expected.

**Project example**: `UserRepository` is defined as an interface. Any concrete implementation (`UserLocalDataSource`, or a future cloud implementation) must return the same types and honor the same contracts (e.g., `login()` returns `Boolean`) without surprising callers.

---

### Interface Segregation Principle (ISP)
Clients should not depend on interfaces they do not use.

**Project example**: The algorithm layer exposes three separate function signatures (`normalize`, `alignOeDtw`, `scoreFrame`) rather than one monolithic `PoseProcessor` interface. `TrainingViewModel` only depends on the functions it actually calls.

---

### Dependency Inversion Principle (DIP)
High-level modules depend on abstractions, not concrete implementations.

**Project example**: `TrainingViewModel` depends on `TrainingRepository` (interface), not on `AppDatabase` or `PoseAnalyzer` directly. The concrete class is supplied by Hilt at runtime, keeping the ViewModel testable.

---

## 2. DRY — Don't Repeat Yourself

Write a piece of logic once. If the same logic is needed elsewhere, extract it into a shared function, constant, or composable.

**Project example — normalization**: The skeleton normalization function is called in two contexts: loading standard JSON data at startup, and processing each live camera frame. Both contexts call the **same** `normalizeLandmarks()` function (ALGORITHM.md Module 1). Never copy-paste the hip-midpoint or torso-scaling math into a second place.

**Project example — UI**: If a labeled `OutlinedTextField` appears in both `LoginScreen` and `RegisterScreen`, extract it into a shared composable (e.g., `core/ui/LabeledTextField.kt`) rather than duplicating the layout code.

**Project example — route strings**: All navigation route strings are defined once in `object Routes` inside `NavGraph.kt`. Never write a raw string like `"training"` in a `navController.navigate()` call; always reference `Routes.TRAINING`.

---

## 3. KISS — Keep It Simple

Prefer the simplest solution that correctly satisfies the requirement. Avoid speculative abstractions.

**Project example — OE-DTW return value**: The `alignOeDtw()` function returns a single `Int` (`matchedReferenceIndex`). It does not return a complex object with DTW cost matrices, path details, or confidence scores. Callers only need the index; everything else is implementation detail.

**Project example — state machine**: The rep-counting state machine has exactly three states (`S1`, `S2`, `S3`). Do not add intermediate states or sub-states unless a Sprint 4 test shows they are needed. A `when` expression over a three-value enum is enough.

**Project example — UI state**: Screen state in `UserViewModel` uses `var x by mutableStateOf("")` — plain Compose state, not `StateFlow` or `LiveData`. Do not introduce additional reactive wrappers unless the project explicitly requires cold/hot stream semantics.

---

## 4. Project-Specific Coding Conventions

### 4.1 Kotlin Code Style

#### Naming

| Element | Convention | Example |
|---|---|---|
| Class / Object / Interface | PascalCase | `TrainingViewModel`, `UserRepository`, `Routes` |
| Composable function | PascalCase | `LoginScreen()`, `FitnessBottomBar()` |
| Regular function / method | camelCase | `registerUser()`, `normalizeLandmarks()` |
| Variable / parameter | camelCase | `username`, `navController`, `matchedReferenceIndex` |
| Boolean variable | camelCase, `is`/`has` prefix for properties | `isInFrame`, `hasStarted` |
| Compile-time constant | UPPER_SNAKE_CASE inside `object` or `companion object` | `const val SCORE_RED_THRESHOLD = 80f` |
| Package | lowercase, no underscores | `com.example.fitnesscoach.training.domain` |
| File | Match the primary class/composable name | `TrainingScreen.kt`, `EvaluateExerciseUseCase.kt` |

#### Formatting

- **Indentation**: 4 spaces. No tabs.
- **Opening brace**: on the same line as the declaration — `fun foo() {`.
- **Modifier chains**: each modifier on its own line, chained with `.`:
  ```kotlin
  Modifier
      .fillMaxSize()
      .padding(16.dp)
      .background(MaterialTheme.colorScheme.surface)
  ```
- **Lambda bodies**: inline for single expressions; multi-line for anything longer:
  ```kotlin
  // single expression — inline
  onClick = { navController.navigate(Routes.TRAINING) }

  // multi-line — separate lines
  onClick = {
      val ok = userViewModel.login(username, password)
      if (ok) navController.navigate(Routes.PROFILE)
      else loginError = "Invalid credentials"
  }
  ```
- **Imports**: wildcard imports are allowed for Compose packages (`androidx.compose.foundation.layout.*`, `androidx.compose.material3.*`). Avoid wildcard imports outside Compose.
- **`@file:OptIn`**: place at the very top of the file, before `package`, when opting into experimental APIs (e.g., `@file:OptIn(ExperimentalMaterial3Api::class)`).
- **Comments**: use comments only where the logic is not self-evident. Do not leave test credentials or TODO comments in committed code.

---

### 4.2 Jetpack Compose Conventions

**Composable declaration**
```kotlin
@Composable
fun TrainingScreen(navController: NavHostController, viewModel: TrainingViewModel) {
    // ...
}
```
- All dependencies (navController, ViewModel) are passed as parameters — never retrieved inside the composable via a global or singleton.
- Every public composable that renders a meaningful UI must have a `@Preview` function.

**Preview functions**
```kotlin
@Preview(showBackground = true)
@Composable
private fun TrainingScreenPreview() {
    FitnessCoachTheme {
        TrainingScreen(navController = rememberNavController(), viewModel = TrainingViewModel())
    }
}
```

**Local UI state**
Use `by remember { mutableStateOf(...) }` for ephemeral, screen-local state (text field input, error messages):
```kotlin
var username by remember { mutableStateOf("") }
var loginError by remember { mutableStateOf("") }
```
Do not hoist this state into the ViewModel unless it needs to survive configuration changes or be shared across screens.

**ViewModel-owned state**
Use `var x by mutableStateOf(...)` (delegated, not `_x`/`x` backing field) in ViewModels that drive Compose UI directly:
```kotlin
class UserViewModel : ViewModel() {
    var username by mutableStateOf("")
        private set
}
```

**Layout structure**
- Wrap each screen in a `Scaffold` when a top app bar or bottom bar is needed.
- Use `Column(modifier = Modifier.fillMaxSize().verticalScroll(...).padding(...))` as the primary layout container for scrollable screens.
- Use `Spacer(modifier = Modifier.height(Xdp))` for vertical rhythm between sections. Keep spacing consistent by referencing constants (see Section 4.5).

**Material3 components only**
Use Material3 (`androidx.compose.material3.*`) exclusively. Do not mix with Material2 (`androidx.compose.material.*`).

---

### 4.3 Module Boundaries

Each module is owned by a specific team member (see ALGORITHM.md). The boundaries are:

| Module / Package | Owner | Responsibility |
|---|---|---|
| `core/mediapipe/` | Lee | CameraX integration, MediaPipe integration, raw landmark output |
| `training/domain/CountRepsUseCase` | Lee | State machine rep counting (Module 4 per ALGORITHM.md) |
| `training/pose/` (normalization, OE-DTW) | Daniel | Normalization, OE-DTW alignment, readiness detection, training end detection (Modules 1, 2, 5, 6 per ALGORITHM.md) |
| `training/evaluation/` (scoring) | Jason | Per-landmark scoring, skeleton overlay UI (Module 3 per ALGORITHM.md) |
| `user/`, `exercise/` UI and data | Jason | Login, registration, guest mode, exercise library, Room database |
| `record/` UI | Helen | Training summary screen, training history screen |
| Report & standard data | Helen | Capstone report, standard action JSON extraction |

**Rules:**
- Do not edit files outside your assigned module without first discussing with the owner.
- Cross-module communication happens only through the interfaces and function signatures defined in ALGORITHM.md — never by importing internal implementation classes from another module.
- The algorithm layer (`training/pose/`, `training/evaluation/`) must not import any Compose or Android UI classes. It depends only on Kotlin standard library.
- The UI layer (`*/ui/`) must not contain business logic or algorithm math. All logic lives in ViewModels, UseCases, or the algorithm layer.

---

### 4.4 Unit Tests

Every public function must have at least one unit test. Tests live in `app/src/test/`.

**Naming convention**: `ClassName_methodName_condition_expectedResult`
```kotlin
class NormalizeLandmarksTest {
    @Test
    fun normalizeLandmarks_validInput_hipMidpointIsOrigin() { ... }

    @Test
    fun normalizeLandmarks_validInput_torsoLengthIsOne() { ... }
}
```

**Acceptance criteria from ALGORITHM.md must be expressed as tests:**

| Module | Required test |
|---|---|
| Normalization | Hip midpoint == (0.0, 0.0) within 0.001 after normalization |
| Normalization | Torso length == 1.0 within 0.001 after normalization |
| Normalization | Output has exactly 33 `Pair<Float, Float>` elements |
| OE-DTW | Two identical sequences → `matchedReferenceIndex == userSequence.size - 1` |
| OE-DTW | `userSequence.size < 20` → returns `-1` |
| Scoring | Identical inputs → `sf >= 95` |
| Scoring | One significantly shifted landmark → affected `Sp < 80`, joint color = RED |
| Scoring | `jointColors.size == 33`, `limbColors.size == 13` |
| State machine | Each of the 5 exercises: 10 reps → counting error ≤ 1 |

**Do not use mocks for algorithm unit tests.** The normalization, scoring, and state-machine functions are pure (input → output with no side effects); test them with direct function calls and assertion libraries only.

---

### 4.5 Constants Management

All key parameters and thresholds must be defined as named constants. Never write a magic number or magic string inline.

Constants belong in `core/util/Constants.kt` (shared) or in a `companion object` / top-level `object` inside the relevant module file.

**Required constants (do not hardcode these values anywhere else):**

```kotlin
// Scoring
const val SCORE_RED_THRESHOLD = 80f          // joint/limb color threshold
const val SCORE_WEIGHT_S1 = 0.2f             // position score weight in Sf
const val SCORE_WEIGHT_S2 = 0.8f             // angle score weight in Sf

// OE-DTW
const val OEDTW_WARMUP_FRAMES = 20           // frames before alignment is stable

// Camera / readiness detection
const val VISIBILITY_IN_FRAME_MIN = 0.5f     // min landmark visibility for full-body check
const val READINESS_HOLD_SECONDS = 3         // seconds both conditions must hold before countdown
const val CAMERA_ANGLE_FRONT_MIN = 150f      // theta > this → front view
const val CAMERA_ANGLE_SIDE_MAX = 60f        // theta < this → side view

// Training end detection
const val END_VISIBILITY_THRESHOLD = 0.3f    // avg visibility below this triggers pause
const val END_VISIBILITY_HOLD_SECONDS = 3    // seconds below threshold to confirm end
const val END_SHOULDER_WIDTH_RATIO = 1.5f    // shoulder width multiplier to detect approach

// State machine — squat
const val SQUAT_S1_ANGLE = 160f
const val SQUAT_S3_ANGLE = 90f

// State machine — right_leg_lunge_to_knee_raise
const val LUNGE_S1_ANGLE = 160f
const val LUNGE_S3_ANGLE = 90f

// State machine — bicep_curl
const val BICEP_CURL_S1_ANGLE = 160f
const val BICEP_CURL_S3_ANGLE = 50f

// State machine — standing_dumbbell_shoulder_press
const val SHOULDER_PRESS_S1_ANGLE = 90f
const val SHOULDER_PRESS_S3_ANGLE = 160f

// State machine — dumbbell_lateral_raise
const val LATERAL_RAISE_S1_ANGLE = 30f
const val LATERAL_RAISE_S3_ANGLE = 80f

// MediaPipe landmark indices
const val LANDMARK_NOSE = 0
const val LANDMARK_LEFT_SHOULDER = 11
const val LANDMARK_RIGHT_SHOULDER = 12
const val LANDMARK_LEFT_ELBOW = 13
const val LANDMARK_RIGHT_ELBOW = 14
const val LANDMARK_LEFT_WRIST = 15
const val LANDMARK_RIGHT_WRIST = 16
const val LANDMARK_LEFT_HIP = 23
const val LANDMARK_RIGHT_HIP = 24
const val LANDMARK_LEFT_KNEE = 25
const val LANDMARK_RIGHT_KNEE = 26
const val LANDMARK_LEFT_ANKLE = 27
const val LANDMARK_RIGHT_ANKLE = 28
const val LANDMARK_COUNT = 33
const val LIMB_COUNT = 13
```

**UI spacing** (in `core/util/Constants.kt` or the theme file):
```kotlin
val PaddingScreen = 24.dp      // outer screen padding
val PaddingSection = 16.dp     // between sections
val SpacerSmall = 8.dp
val SpacerMedium = 16.dp
val SpacerLarge = 24.dp
```

If a threshold is adjusted after Sprint 4 testing (as noted in ALGORITHM.md), update the constant definition and update ALGORITHM.md — do not scatter the new value across multiple files.

---

*Last updated: 2026-03-24*

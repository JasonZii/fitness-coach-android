package com.example.fitnesscoach.training.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.fitnesscoach.app.navigation.Routes
import com.example.fitnesscoach.core.util.Constants.LANDMARK_COUNT
import com.example.fitnesscoach.core.util.Constants.LIMB_COUNT
import com.example.fitnesscoach.training.pose.CameraAngle
import com.example.fitnesscoach.training.pose.ReadinessPhase
import com.example.fitnesscoach.training.viewmodel.SessionPhase
import com.example.fitnesscoach.training.viewmodel.TrainingUiState
import com.example.fitnesscoach.training.viewmodel.TrainingViewModel
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.fitnesscoach.data.local.AppDatabase
import com.example.fitnesscoach.data.local.TrainingRecordEntity
import kotlinx.coroutines.withContext
import com.example.fitnesscoach.exercise.data.exerciseList

@Composable
fun TrainingScreen(
    navController: NavHostController,
    exerciseId: String = "squat",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: TrainingViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    var showReferenceSkeleton by remember { mutableStateOf(true) }
    val database = remember {
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "fitnesscoach_db"
        ).build()
    }
    val dao = database.trainingRecordDao()
    val scope = rememberCoroutineScope()

    // ── Camera permission ────────────────────────────────────────────────────
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Load the correct exercise reference data once when the screen opens.
    LaunchedEffect(exerciseId) {
        viewModel.loadExercise(exerciseId)
    }

    Box(modifier = modifier.fillMaxSize()) {

        // ── Layer 1: live camera feed ─────────────────────────────────────────
        if (hasCameraPermission) {
            CameraPreview(
                modifier = Modifier.fillMaxSize(),
                context = context,
                onPoseDetected = { poseResult -> viewModel.onFrame(poseResult) }
            )
        }

        // ── Layer 2: user skeleton overlay ───────────────────────────────────
        if (uiState.landmarks.size == LANDMARK_COUNT) {
            SkeletonOverlay(
                landmarks   = uiState.landmarks,
                jointColors = uiState.jointColors,
                limbColors  = uiState.limbColors,
                modifier    = Modifier.fillMaxSize()
            )
        }

        // ── Layer 3: reference skeleton (blue, semi-transparent) ─────
        // Shown only when at least one joint is red (posture needs correction).
//        if (uiState.matchedReferenceRawLandmarks.size == LANDMARK_COUNT) {
//            SkeletonOverlay(
//                landmarks   = uiState.matchedReferenceRawLandmarks,
//                jointColors = List(LANDMARK_COUNT) { Color.Blue.copy(alpha = 0.55f) },
//                limbColors  = List(LIMB_COUNT) { Color.Blue.copy(alpha = 0.55f) },
//                modifier    = Modifier.fillMaxSize()
//            )
//        }

        if (
            showReferenceSkeleton &&
            uiState.matchedReferenceRawLandmarks.size == LANDMARK_COUNT
        ) {
            SkeletonOverlay(
                landmarks = uiState.matchedReferenceRawLandmarks,
                jointColors = List(LANDMARK_COUNT) { Color.Blue.copy(alpha = 0.55f) },
                limbColors = List(LIMB_COUNT) { Color.Blue.copy(alpha = 0.55f) },
                modifier = Modifier.fillMaxSize()
            )
        }

        // ── Layer 4: phase-specific UI ────────────────────────────────────────
        when (uiState.phase) {
            SessionPhase.READINESS -> ReadinessOverlay(
                uiState  = uiState,
                onCancel = {
                    // Pop back to Library so the back stack is [HOME, exercise_library].
                    // If EXERCISE_LIBRARY is not in the back stack (e.g. deep-link entry),
                    // fall back to Home to avoid a silent no-op.
//                    val popped = navController.popBackStack(
//                        route     = Routes.EXERCISE_LIBRARY,
//                        inclusive = false,
//                    )
                    val popped = navController.popBackStack()
                    if (!popped) {
                        navController.navigate(Routes.HOME) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            )
            SessionPhase.TRAINING  -> TrainingOverlay(
                uiState = uiState,
                onStop  = {
                    val repCount      = uiState.repCount
                    val avgScore      = if (uiState.repScores.isNotEmpty())
                        uiState.repScores.average().toInt() else 0
                    val correctReps   = uiState.correctReps
                    val incorrectReps = uiState.incorrectReps

                    viewModel.stopTraining()

//                    val exerciseName = when (exerciseId) {
//                        "squat" -> "Squat"
//                        "dumbbell_lateral_raise" -> "Dumbbell Lateral Raise"
//                        "dumbbell_overhead_triceps_extension" -> "Dumbbell Overhead Triceps Extension"
//                        "right_leg_lunge_to_knee_raise" -> "Right Leg Lunge To Knee Raise"
//                        "standing_dumbbell_shoulder_press" -> "Standing Dumbbell Shoulder Press"
//                        "bicep_curl" -> "Bicep Curl"
//                        else -> "Unknown"
//                    }

                    val exerciseName = exerciseList
                        .find { it.id == exerciseId }
                        ?.title ?: "Unknown"

                    scope.launch(Dispatchers.IO) {
                        val newRecordId = dao.insertRecord(
                            TrainingRecordEntity(
                                exerciseId = exerciseId,
                                exerciseName = exerciseName,
                                repCount = repCount,
                                avgScore = avgScore,
                                correctReps = correctReps,
                                incorrectReps = incorrectReps
                            )
                        ).toInt()

                        withContext(Dispatchers.Main) {
                            navController.navigate(Routes.recordDetail(newRecordId)) {
                                popUpTo(Routes.training(exerciseId)) { inclusive = true }
                            }
                        }
                    }
                }
            )
            // FINISHED state is transient; navigation away happens in the stop handler above.
            SessionPhase.FINISHED  -> Unit
        }

        ReferenceSkeletonSwitch(
            checked = showReferenceSkeleton,
            onCheckedChange = { showReferenceSkeleton = it },
            modifier = Modifier.align(Alignment.TopEnd)
        )

        // ── Layer 5: pause banner (on top of everything) ──────────────────────
        if (uiState.isTrainingPaused) {
            PauseBanner(modifier = Modifier.align(Alignment.TopCenter))
        }
    }
}

@Composable
private fun ReferenceSkeletonSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .padding(16.dp)
            .background(
                Color.Black.copy(alpha = 0.55f),
                RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Blue",
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.width(8.dp))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

// ── Readiness overlay ─────────────────────────────────────────────────────────

@Composable
private fun ReadinessOverlay(uiState: TrainingUiState, onCancel: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.65f))
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (uiState.readiness.phase) {
                ReadinessPhase.NOT_READY -> {
                    Text(
                        text = "Get Ready",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(Modifier.height(12.dp))

                    // ── Camera-angle status ───────────────────────────────────
                    val angleInstruction = if (uiState.requiredCameraAngle == CameraAngle.SIDE)
                        "Stand sideways to the camera"
                    else
                        "Face the camera"
                    val angleMatched = uiState.cameraAngle == uiState.requiredCameraAngle
                    ReadinessCheckRow(
                        satisfied = angleMatched,
                        satisfiedText  = "Angle correct — $angleInstruction",
                        unsatisfiedText = angleInstruction,
                    )

                    Spacer(Modifier.height(6.dp))

                    // ── Full-body visibility status ───────────────────────────
                    ReadinessCheckRow(
                        satisfied       = uiState.isFullBodyInFrame,
                        satisfiedText   = "Full body visible",
                        unsatisfiedText = "Ensure your full body is visible",
                        unsatisfiedColor = Color(0xFFFF6B6B),   // red — more urgent
                    )
                }

                ReadinessPhase.COUNTDOWN -> {
                    Text(
                        text = "${uiState.readiness.countdownTick}",
                        color = Color.White,
                        fontSize = 72.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Hold position…",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                ReadinessPhase.TRAINING_STARTED -> {
                    // Transition to TRAINING phase is handled by ViewModel; nothing to show.
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onCancel,
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .height(46.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF616161))
            ) {
                Text("Cancel", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ── Training overlay ──────────────────────────────────────────────────────────

@Composable
private fun TrainingOverlay(uiState: TrainingUiState, onStop: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {

        // Top-left HUD: rep count
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = "Reps: ${uiState.repCount}",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        // Bottom-centre: stop button
        Button(
            onClick = onStop,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 36.dp)
                .fillMaxWidth(0.55f)
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
        ) {
            Text("Stop Training", color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ── Pause banner ──────────────────────────────────────────────────────────────

@Composable
private fun PauseBanner(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFFFF6F00).copy(alpha = 0.9f))
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Paused — move back or ensure full body is visible",
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

// ── Readiness check row ───────────────────────────────────────────────────────

/**
 * A single status row used in [ReadinessOverlay].
 *
 * Shows a green "✓" prefix and [satisfiedText] when [satisfied];
 * shows an amber "○" prefix and [unsatisfiedText] (red for visibility,
 * yellow for angle) when not.
 */
@Composable
private fun ReadinessCheckRow(
    satisfied: Boolean,
    satisfiedText: String,
    unsatisfiedText: String,
    unsatisfiedColor: Color = Color(0xFFFFB300),  // amber by default; red for visibility
) {
    val prefix = if (satisfied) "✓" else "○"
    val text   = if (satisfied) satisfiedText else unsatisfiedText
    val color  = if (satisfied) Color(0xFF4CAF50) else unsatisfiedColor
    Text(
        text       = "$prefix  $text",
        color      = color,
        fontSize   = 14.sp,
        fontWeight = if (satisfied) FontWeight.Normal else FontWeight.Medium,
    )
}

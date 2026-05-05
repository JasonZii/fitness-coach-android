package com.example.fitnesscoach.training.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@Composable
fun TrainingScreenOld(
    navController: NavHostController,
    exerciseId: String = "squat",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: TrainingViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val landmarks by viewModel.skeletonState.collectAsState()

    // Load the correct exercise reference data once when the screen opens.
    LaunchedEffect(exerciseId) {
        viewModel.loadExercise(exerciseId)
    }

    Box(modifier = modifier.fillMaxSize()) {

        // ── Layer 1: live camera feed ─────────────────────────────────────────
        CameraPreview(
            modifier       = Modifier.fillMaxSize(),
            context        = context,
            frameProcessor = viewModel.poseFrameProcessor,
            onPoseDetected = { poseResult -> viewModel.onFrame(poseResult) }
        )

        // ── Layer 2: user skeleton overlay ───────────────────────────────────
        if (landmarks.size == LANDMARK_COUNT) {
            SkeletonOverlay(
                landmarks   = landmarks,
                jointColors = uiState.jointColors,
                limbColors  = uiState.limbColors,
                modifier    = Modifier.fillMaxSize()
            )
        }

        // ── Layer 3: reference skeleton (blue, semi-transparent) ─────
        // Shown only when at least one joint is red (posture needs correction).
        if (uiState.matchedReferenceRawLandmarks.size == LANDMARK_COUNT) {
            SkeletonOverlay(
                landmarks   = uiState.matchedReferenceRawLandmarks,
                jointColors = List(LANDMARK_COUNT) { Color.Blue.copy(alpha = 0.55f) },
                limbColors  = List(LIMB_COUNT) { Color.Blue.copy(alpha = 0.55f) },
                modifier    = Modifier.fillMaxSize()
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
                    val popped = navController.popBackStack(
                        route     = Routes.EXERCISE_LIBRARY,
                        inclusive = false,
                    )
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
                    navController.navigate(
                        Routes.trainingResult(exerciseId, repCount, avgScore, correctReps, incorrectReps)
                    ) {
                        // Remove the training screen from the back stack so the user
                        // can navigate back normally to Library / Home from ResultScreen.
                        popUpTo(Routes.training(exerciseId)) { inclusive = true }
                    }
                }
            )
            // FINISHED state is transient; navigation away happens in the stop handler above.
            SessionPhase.FINISHED  -> Unit
        }

        // ── Layer 5: pause banner (on top of everything) ──────────────────────
        if (uiState.isTrainingPaused) {
            PauseBanner(modifier = Modifier.align(Alignment.TopCenter))
        }
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
                        satisfiedText   = "Body landmarks visible",
                        unsatisfiedText = "Ensure your body landmarks stay visible",
                        unsatisfiedColor = Color(0xFFFF6B6B),   // red – more urgent
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

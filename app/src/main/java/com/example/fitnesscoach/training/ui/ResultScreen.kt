package com.example.fitnesscoach.training.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.fitnesscoach.app.navigation.Routes
import com.example.fitnesscoach.exercise.data.exerciseList

/**
 * Post-training summary screen.
 *
 * Displayed immediately after the user taps "Stop Training". Shows rep count,
 * correct/incorrect rep breakdown, average form score, and navigation options.
 * The training screen is removed from the back stack before arriving here, so
 * pressing Back goes to ExerciseDetailScreen (or wherever the user came from).
 *
 * @param exerciseId   ID of the completed exercise (used to look up its display name).
 * @param repCount     Total number of fully completed reps in the session.
 * @param avgScore     Session average form score, rounded to an Int (0–100).
 * @param correctReps  Reps with acceptable form (max consecutive red-frame run ≤ threshold).
 * @param incorrectReps Reps with poor form (max consecutive red-frame run > threshold).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    navController: NavHostController,
    exerciseId: String,
    repCount: Int,
    avgScore: Int,
    correctReps: Int = 0,
    incorrectReps: Int = 0,
) {
    val exerciseName = exerciseList.find { it.id == exerciseId }?.title ?: exerciseId

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Training Summary") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ── Exercise name card ────────────────────────────────────────────
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = exerciseName,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Stats card ────────────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Session Results",
                        style = MaterialTheme.typography.titleLarge
                    )
                    ResultInfoRow(label = "Exercise",        value = exerciseName)
                    ResultInfoRow(label = "Duration",        value = "--")
                    ResultInfoRow(label = "Repetitions",     value = "$repCount")
                    ResultInfoRow(label = "Correct",         value = "$correctReps")
                    ResultInfoRow(label = "Incorrect",       value = "$incorrectReps")
                    ResultInfoRow(label = "Avg Form Score",  value = if (repCount > 0) "$avgScore / 100" else "--")
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // ── Action buttons ────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Go back to Home (clears back stack up to Home so nav is clean)
                Button(
                    onClick = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.HOME) { inclusive = false }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Home")
                }

                // Restart the same exercise
                Button(
                    onClick = {
                        navController.navigate(Routes.training(exerciseId)) {
                            popUpTo(Routes.EXERCISE_LIBRARY) { inclusive = false }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Try Again")
                }

                // Browse training history
                Button(
                    onClick = {
                        navController.navigate(Routes.RECORD_LIST) {
                            popUpTo(Routes.HOME) { inclusive = false }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("History")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ResultInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 14.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.titleMedium)
        Text(text = value,  style = MaterialTheme.typography.bodyLarge)
    }
}

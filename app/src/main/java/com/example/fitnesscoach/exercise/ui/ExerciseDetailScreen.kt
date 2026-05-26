package com.example.fitnesscoach.exercise.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.fitnesscoach.app.navigation.Routes
import com.example.fitnesscoach.core.ui.ExerciseVideoPlayer
import com.example.fitnesscoach.exercise.data.ExerciseInfo
import com.example.fitnesscoach.exercise.data.exerciseList

@Composable
fun ExerciseDetailScreen(
    navController: NavHostController,
    exerciseId: String
) {
    val exercise = exerciseList.find { it.id == exerciseId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F8FC))
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        IconButton(
            onClick = { navController.popBackStack() },
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.White)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color(0xFF141418)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = exercise?.title ?: "Unknown Exercise",
            color = Color(0xFF141418),
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Review the reference video and start guided training.",
            color = Color(0xFF747681),
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(18.dp))

        if (exercise != null) {
            ExerciseVideoPanel(exercise = exercise)

            Spacer(modifier = Modifier.height(16.dp))

            ExerciseSummary(exercise = exercise)

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { navController.navigate(Routes.training(exercise.id)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF111114),
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(21.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Start Training",
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else {
            UnknownExerciseState()
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun ExerciseVideoPanel(exercise: ExerciseInfo) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(230.dp)
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        ExerciseVideoPlayer(
            videoResId = exercise.videoRes,
            active = true,
            showController = false,
            useTextureView = true,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun ExerciseSummary(exercise: ExerciseInfo) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            InfoTag(
                text = exercise.requiredCameraAngle.name.lowercase().replaceFirstChar {
                    it.uppercase()
                },
                icon = Icons.Default.CameraAlt,
                containerColor = Color(0xFFEAF1FF),
                contentColor = Color(0xFF3384FF)
            )
            InfoTag(
                text = if (exercise.requiresFullBody) "Full Body" else "Upper Body",
                icon = Icons.Default.FitnessCenter,
                containerColor = Color(0xFFF0ECFF),
                contentColor = Color(0xFF745CFF)
            )
        }

        Text(
            text = "Overview",
            color = Color(0xFF141418),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = exercise.description,
            color = Color(0xFF747681),
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
    }
}

@Composable
private fun InfoTag(
    text: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        shape = CircleShape,
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = text,
                color = contentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun UnknownExerciseState() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Exercise not found",
            color = Color(0xFF141418),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Please return to the library and choose another exercise.",
            color = Color(0xFF747681),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

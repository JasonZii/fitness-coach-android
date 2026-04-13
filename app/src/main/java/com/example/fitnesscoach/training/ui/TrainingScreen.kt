package com.example.fitnesscoach.training.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import com.example.fitnesscoach.core.mediapipe.PoseResult

@Composable
fun TrainingScreen(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var landmarkCount by remember { mutableStateOf(0) }
    var latestPoseResult by remember {
        mutableStateOf(
            PoseResult(
                landmarks = emptyList(),
                visibilities = emptyList()
            )
        )
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        CameraPreview(
            modifier = Modifier.fillMaxSize(),
            context = context,
            onPoseDetected = { poseResult ->
                latestPoseResult = poseResult
                landmarkCount = poseResult.landmarks.size
            }
        )

        Text(
            text = "Landmarks detected: $landmarkCount, visibilities: ${latestPoseResult.visibilities.size}",
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 32.dp)
        )
    }
}
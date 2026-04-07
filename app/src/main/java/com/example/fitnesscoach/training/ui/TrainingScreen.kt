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

@Composable
fun TrainingScreen() {
    val context = LocalContext.current
    var landmarkCount by remember { mutableStateOf(0) }

    Box(modifier = Modifier.fillMaxSize()) {

        CameraPreview(
            modifier = Modifier.fillMaxSize(),
            context = context,
            onLandmarksDetected = { count ->
                landmarkCount = count
            }
        )

        Text(
            text = "Landmarks detected: $landmarkCount",
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 32.dp)
        )
    }
}
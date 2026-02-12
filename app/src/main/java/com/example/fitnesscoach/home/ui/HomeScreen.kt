package com.example.fitnesscoach.home.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.fitnesscoach.app.navigation.Routes


@Composable
fun HomeScreen(navController: NavHostController) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("AI Fitness Coach", fontSize = 26.sp)
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = { navController.navigate(Routes.TRAINING) }) {
                Text("Start Training")
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = { navController.navigate(Routes.RECORD) }) {
                Text("Training Records")
            }
        }
    }
}
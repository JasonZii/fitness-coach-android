package com.example.fitnesscoach.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.fitnesscoach.record.ui.RecordScreen
import com.example.fitnesscoach.user.ui.HomeScreen
import com.example.fitnesscoach.training.ui.TrainingScreen

object Routes {
    const val HOME = "home"
    const val TRAINING = "training"
    const val RECORD = "record"
}


@Composable
fun AppNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME
    ) {
        composable(Routes.HOME) {
            HomeScreen(navController)
        }
        composable(Routes.TRAINING) {
            TrainingScreen(navController)
        }
        composable(Routes.RECORD) {
            RecordScreen(navController)
        }
    }
}

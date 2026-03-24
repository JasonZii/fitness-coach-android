package com.example.fitnesscoach.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.fitnesscoach.record.ui.RecordScreen
import com.example.fitnesscoach.home.ui.HomeScreen
import com.example.fitnesscoach.training.ui.TrainingScreen
import com.example.fitnesscoach.user.ui.LoginScreen
import com.example.fitnesscoach.user.ui.RegisterScreen
import com.example.fitnesscoach.user.ui.ProfileScreen
import com.example.fitnesscoach.user.viewmodel.UserViewModel

object Routes {
    const val HOME = "home"
    const val USER = "user"
    const val TRAINING = "training"
    const val RECORD = "record"
    const val REGISTER = "register"
    const val PROFILE = "profile"
}

@Composable
fun AppNavGraph(
    navController: NavHostController,
    userViewModel: UserViewModel,
    modifier: Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME
    ) {
        composable(Routes.HOME) {
            HomeScreen(navController)
        }
        composable(Routes.USER) {
//            UserScreen(navController)
            LoginScreen(navController, userViewModel)
        }
        composable(Routes.TRAINING) {
            TrainingScreen(navController)
        }
        composable(Routes.RECORD) {
            RecordScreen(navController)
        }
        composable(Routes.REGISTER) {
            RegisterScreen(navController, userViewModel)
        }
        composable(Routes.PROFILE) {
            ProfileScreen(navController, userViewModel)
        }

    }
}

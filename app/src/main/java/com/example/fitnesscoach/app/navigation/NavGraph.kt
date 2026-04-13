package com.example.fitnesscoach.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.fitnesscoach.exercise.ui.ExerciseDetailScreen
import com.example.fitnesscoach.exercise.ui.ExerciseLibraryScreen
import com.example.fitnesscoach.record.ui.RecordScreen
import com.example.fitnesscoach.home.ui.HomeScreen
import com.example.fitnesscoach.training.ui.TrainingScreen
import com.example.fitnesscoach.user.ui.LoginScreen
import com.example.fitnesscoach.user.ui.RegisterScreen
import com.example.fitnesscoach.user.ui.ProfileScreen
import com.example.fitnesscoach.user.viewmodel.UserViewModel
import com.example.fitnesscoach.record.ui.RecordListScreen
import com.example.fitnesscoach.record.ui.RecordDetailScreen

// 页面跳转注册

object Routes {
    const val HOME = "home"
    const val USER = "user"
    const val TRAINING = "training"
    const val RECORD = "record"

    const val REGISTER = "register"
    const val PROFILE = "profile"

    const val EXERCISE_LIBRARY = "exercise_library"
    const val EXERCISE_DETAIL = "exercise_detail"

    const val RECORD_LIST = "record_list"
    const val RECORD_DETAIL = "record_detail"

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
            if (userViewModel.isLoggedIn) {
                ProfileScreen(navController, userViewModel)
            } else {
                LoginScreen(navController, userViewModel)
            }
        }
        composable(Routes.TRAINING) {
            TrainingScreen(
                navController = navController,
                modifier = modifier
            )
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
        composable(Routes.EXERCISE_LIBRARY) {
            ExerciseLibraryScreen(navController)
        }
        composable("exercise_detail/{exerciseId}") { backStackEntry ->
            val exerciseId = backStackEntry.arguments?.getString("exerciseId") ?: ""
            ExerciseDetailScreen(navController, exerciseId)
        }

        composable(Routes.RECORD_LIST) {
            RecordListScreen(navController)
        }

        composable("${Routes.RECORD_DETAIL}/{recordId}") { backStackEntry ->
            val recordId = backStackEntry.arguments?.getString("recordId") ?: ""
            RecordDetailScreen(navController, recordId)
        }


    }
}

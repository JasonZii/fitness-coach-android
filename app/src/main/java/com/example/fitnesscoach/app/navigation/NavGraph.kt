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
import com.example.fitnesscoach.training.ui.ResultScreen
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
    const val RECORD = "record"

    const val REGISTER = "register"
    const val PROFILE = "profile"

    const val EXERCISE_LIBRARY = "exercise_library"
    /** Route template registered in NavHost for exercise detail. */
    const val EXERCISE_DETAIL_TEMPLATE = "exercise_detail/{exerciseId}"

    /** Navigate to the detail screen for [exerciseId]. */
    fun exerciseDetail(exerciseId: String) = "exercise_detail/$exerciseId"

    const val RECORD_LIST = "record_list"
    const val RECORD_DETAIL = "record_detail"

    // ── Training routes ───────────────────────────────────────────────────────

    /** Route template registered in NavHost. Used for bottom-bar hiding checks. */
    const val TRAINING_TEMPLATE = "training/{exerciseId}"
    /** Route template for the post-training result screen. */
    const val TRAINING_RESULT_TEMPLATE =
        "training_result/{exerciseId}/{repCount}/{avgScore}/{correctReps}/{incorrectReps}"

    /** Navigate to the training screen for [exerciseId]. */
    fun training(exerciseId: String) = "training/$exerciseId"

    /** Navigate to the training result screen. [avgScore] is rounded to an Int. */
    fun trainingResult(
        exerciseId: String,
        repCount: Int,
        avgScore: Int,
        correctReps: Int,
        incorrectReps: Int,
    ) = "training_result/$exerciseId/$repCount/$avgScore/$correctReps/$incorrectReps"
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
        composable(Routes.TRAINING_TEMPLATE) { backStackEntry ->
            val exerciseId = backStackEntry.arguments?.getString("exerciseId") ?: "squat"
            TrainingScreen(
                navController = navController,
                exerciseId    = exerciseId,
                modifier      = modifier
            )
        }
        composable(Routes.TRAINING_RESULT_TEMPLATE) { backStackEntry ->
            val exerciseId    = backStackEntry.arguments?.getString("exerciseId") ?: "squat"
            val repCount      = backStackEntry.arguments?.getString("repCount")?.toIntOrNull() ?: 0
            val avgScore      = backStackEntry.arguments?.getString("avgScore")?.toIntOrNull() ?: 0
            val correctReps   = backStackEntry.arguments?.getString("correctReps")?.toIntOrNull() ?: 0
            val incorrectReps = backStackEntry.arguments?.getString("incorrectReps")?.toIntOrNull() ?: 0
            ResultScreen(
                navController = navController,
                exerciseId    = exerciseId,
                repCount      = repCount,
                avgScore      = avgScore,
                correctReps   = correctReps,
                incorrectReps = incorrectReps,
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
        composable(Routes.EXERCISE_DETAIL_TEMPLATE) { backStackEntry ->
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

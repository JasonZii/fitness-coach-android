package com.example.fitnesscoach.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
// You might need to add a dependency for this import:
// implementation "androidx.navigation:navigation-compose:2.7.7"

// A placeholder for your first screen
@Composable
fun HomeScreen() {
    // This would be the content of your home screen
}

@Composable
fun FitnessCoachNavGraph() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen()
        }
        // Add other composable screens here, for example:
        // composable("profile") { ProfileScreen() }
    }
}

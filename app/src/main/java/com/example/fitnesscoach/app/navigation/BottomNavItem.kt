package com.example.fitnesscoach.app.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person


sealed class BottomNavItem(
    val title: String,
    val route: String,
    val icon: ImageVector
) {
    object Home : BottomNavItem("Home", Routes.HOME, Icons.Default.Home)
    object Training : BottomNavItem("Training", Routes.TRAINING, Icons.Default.FitnessCenter)
    object Record : BottomNavItem("Record", Routes.RECORD, Icons.Default.History)
    object User : BottomNavItem("Profile", Routes.USER, Icons.Default.Person)
}

val bottomNavItems = listOf(
    BottomNavItem.Home,
    BottomNavItem.Training,
    BottomNavItem.Record,
    BottomNavItem.User
)
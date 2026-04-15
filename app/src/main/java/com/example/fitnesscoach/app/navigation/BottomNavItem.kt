package com.example.fitnesscoach.app.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person

// 定义菜单（数据）
sealed class BottomNavItem(
    val title: String,
    val route: String,
    val icon: ImageVector
) {
    object Home    : BottomNavItem("Home",    Routes.HOME,             Icons.Default.Home)
    object Library : BottomNavItem("Library", Routes.EXERCISE_LIBRARY, Icons.Default.FitnessCenter)
    object History : BottomNavItem("History", Routes.RECORD_LIST,      Icons.Default.History)
    object User    : BottomNavItem("Profile", Routes.USER,             Icons.Default.Person)
}

val bottomNavItems = listOf(
    BottomNavItem.Home,
    BottomNavItem.Library,
    BottomNavItem.History,
    BottomNavItem.User
)
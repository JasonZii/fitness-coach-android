package com.example.fitnesscoach

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.rememberNavController
import com.example.fitnesscoach.app.navigation.AppNavGraph
import com.example.fitnesscoach.app.navigation.FitnessBottomBar
import com.example.fitnesscoach.ui.theme.FitnessCoachTheme
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fitnesscoach.user.viewmodel.UserViewModel

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FitnessCoachTheme {
                // 1. 创建控制中心
                val navController = rememberNavController()
                val userViewModel: UserViewModel = viewModel()

                // 2 使用 Scaffold 来管理布局（方便后续加底部栏）
                Scaffold(
                    // 注入底部栏
                    bottomBar = { FitnessBottomBar(navController) }
                ){ innerPadding ->
                    // 核心：把 Padding 传递给 NavGraph，避免内容被底部栏遮挡
                    AppNavGraph(
                        navController = navController,
                        userViewModel = userViewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }

    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "FitnessCoach $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    FitnessCoachTheme {
        Greeting("Android")
    }
}
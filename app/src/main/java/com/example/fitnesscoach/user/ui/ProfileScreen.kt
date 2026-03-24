package com.example.fitnesscoach.user.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.fitnesscoach.app.navigation.Routes
import com.example.fitnesscoach.user.viewmodel.UserViewModel

@Composable
fun ProfileScreen(
    navController: NavHostController,
    userViewModel: UserViewModel  //不再依赖路由参数 而是 直接从 UserViewModel 读取数据
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "My Profile",
            fontSize = 28.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Text("• User Account: ${userViewModel.username}")
            Text("• Height: ${userViewModel.height}")
            Text("• Weight: ${userViewModel.weight}")
            Text("• Training Days")
            Text("• Total Minutes")
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            TextButton(onClick = { }) {
                Text("Edit")
            }

            TextButton(onClick = { }) {
                Text("Delete")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { navController.navigate(Routes.HOME) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Home")
        }
    }
}
package com.example.fitnesscoach.user.ui
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.fitnesscoach.app.navigation.Routes
import com.example.fitnesscoach.user.viewmodel.UserViewModel


@Composable
fun LoginScreen(navController: NavHostController,userViewModel: UserViewModel) {

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loginError by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {

        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = "Mobile Exercise Coach",
            fontSize = 26.sp
        )

        Spacer(modifier = Modifier.height(40.dp))

        Text("User Account")
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text("Password")
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(40.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {

//            Button(onClick = { }) {
//                Text("Register")
//            }

            Button(onClick = { navController.navigate(Routes.REGISTER) }) {
                Text("Register")
            }

            Button(
                onClick = {
                    val success = userViewModel.login(username, password)
                    if (success) {
                        loginError = ""
                        navController.navigate(Routes.PROFILE)
                    } else {
                        loginError = "Invalid username or password"
                    }
                }
            ) {
                Text("Login")
            }

            Button(onClick = { navController.navigate(Routes.PROFILE) }) {
                Text("Guest")
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (loginError.isNotEmpty()) {
                Text(
                    text = loginError,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
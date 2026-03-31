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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.platform.LocalContext
import com.example.fitnesscoach.data.local.UserPreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


@Composable
fun LoginScreen(navController: NavHostController,userViewModel: UserViewModel) {

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
//    var loginError by remember { mutableStateOf("") }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    val context = LocalContext.current
    val userPrefs = remember { UserPreferencesManager(context) }

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
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(40.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
//            horizontalArrangement = Arrangement.SpaceEvenly
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = { navController.navigate(Routes.REGISTER) },
                modifier = Modifier.weight(1f)
            ) {
                Text("Register")
            }

            Button(
                onClick = {
                    if (userViewModel.username.isBlank() || userViewModel.password.isBlank()) {
                        errorMessage = "No registered user found."
                        showErrorDialog = true
                    } else {
                        val success = userViewModel.login(username, password)
                        if (success) {
                            CoroutineScope(Dispatchers.IO).launch {
                                userPrefs.saveLoginStatus(true)
                            }
                            navController.navigate(Routes.USER)
                        } else {
                            errorMessage = "Invalid username or password."
                            showErrorDialog = true
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Login")
            }

//            Button(onClick = { navController.navigate(Routes.PROFILE) }) {
//                Text("Guest")
//            }

        }
        Spacer(modifier = Modifier.height(16.dp))

        if (showErrorDialog) {
            AlertDialog(
                onDismissRequest = { showErrorDialog = false },
                confirmButton = {
                    TextButton(onClick = { showErrorDialog = false }) {
                        Text("OK")
                    }
                },
                title = {
                    Text("Login Failed")
                },
                text = {
                    Text(errorMessage)
                }
            )
        }
    }
}
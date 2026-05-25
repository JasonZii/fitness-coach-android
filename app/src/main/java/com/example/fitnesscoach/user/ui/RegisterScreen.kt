package com.example.fitnesscoach.user.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.fitnesscoach.app.navigation.Routes
import com.example.fitnesscoach.user.viewmodel.UserViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import com.example.fitnesscoach.data.local.UserPreferencesManager
import androidx.compose.runtime.remember
import androidx.compose.ui.text.input.PasswordVisualTransformation

@Composable
fun RegisterScreen(navController: NavHostController, userViewModel: UserViewModel) {

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    val context = LocalContext.current
    val userPrefs = remember { UserPreferencesManager(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 120.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Mobile Exercise Coach",
            fontSize = 26.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Account",
            fontSize = 22.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("User Account")
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("Password")
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("Confirm Password")
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("Email")
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Profile",
            fontSize = 22.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("Height (cm)")
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = height,
            onValueChange = { height = it },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("Weight (kg)")
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = weight,
            onValueChange = { weight = it },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = { navController.popBackStack() },
                modifier = Modifier.weight(1f)
            ) {
                Text("Back")
            }

            Button(
                onClick = {
                    when {
                        username.isBlank() -> {
                            errorMessage = "Username cannot be empty."
                            showErrorDialog = true
                        }

                        password.isBlank() -> {
                            errorMessage = "Password cannot be empty."
                            showErrorDialog = true
                        }

                        confirmPassword.isBlank() -> {
                            errorMessage = "Please confirm your password."
                            showErrorDialog = true
                        }

                        password != confirmPassword -> {
                            errorMessage = "Passwords do not match."
                            showErrorDialog = true
                        }

                        email.isBlank() -> {
                            errorMessage = "Email cannot be empty."
                            showErrorDialog = true
                        }

                        height.isBlank() -> {
                            errorMessage = "Height cannot be empty."
                            showErrorDialog = true
                        }

                        weight.isBlank() -> {
                            errorMessage = "Weight cannot be empty."
                            showErrorDialog = true
                        }

                        userViewModel.isUsernameTaken(username) -> {
                            errorMessage = "This username already exists."
                            showErrorDialog = true
                        }

                        else -> {
                            userViewModel.registerUser(
                                username = username,
                                password = password,
                                email = email,
                                height = height,
                                weight = weight
                            )

                            CoroutineScope(Dispatchers.IO).launch {
                                userPrefs.saveUser(
                                    username = username,
                                    password = password,
                                    email = email,
                                    height = height,
                                    weight = weight
                                )
                                userPrefs.saveLoginStatus(true)
                            }

                            navController.navigate(Routes.USER)
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Create")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showErrorDialog) {
        AlertDialog(
            onDismissRequest = { showErrorDialog = false },
            confirmButton = {
                TextButton(onClick = { showErrorDialog = false }) {
                    Text("OK")
                }
            },
            title = {
                Text("Registration Error")
            },
            text = {
                Text(errorMessage)
            }
        )
    }
}
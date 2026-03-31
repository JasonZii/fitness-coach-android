package com.example.fitnesscoach.user.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

//using ViewModel to manage shared UI state across screens
class UserViewModel : ViewModel() {

    var username by mutableStateOf("")
    var password by mutableStateOf("")
    var email by mutableStateOf("")
    var height by mutableStateOf("")
    var weight by mutableStateOf("")
    var isLoggedIn by mutableStateOf(false)

    //username = jason
    //password = 123
    //email = a@a.com
    //height = 190
    //weight = 100
    //把注册页输入的数据存起来
    fun registerUser(
        username: String,
        password: String,
        email: String,
        height: String,
        weight: String
    ) {
        this.username = username
        this.password = password
        this.email = email
        this.height = height
        this.weight = weight
        this.isLoggedIn = true
    }

    fun loadUser(
        username: String,
        password: String,
        email: String,
        height: String,
        weight: String
    ) {
        this.username = username
        this.password = password
        this.email = email
        this.height = height
        this.weight = weight
        this.isLoggedIn = username.isNotBlank()
    }

    fun login(inputUsername: String, inputPassword: String): Boolean {
        val success = username == inputUsername && password == inputPassword
        if (success) {
            isLoggedIn = true
        }
        return success
    }

    fun logout() {
        isLoggedIn = false
    }

    //清空用户信息
    fun deleteUser() {
        username = ""
        password = ""
        email = ""
        height = ""
        weight = ""
        isLoggedIn = false
    }

    fun isUsernameTaken(inputUsername: String): Boolean {
        return username.isNotBlank() && username == inputUsername
    }
}
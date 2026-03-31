package com.example.fitnesscoach.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import androidx.datastore.preferences.core.booleanPreferencesKey

private val Context.dataStore by preferencesDataStore(name = "user_prefs")

class UserPreferencesManager(private val context: Context) {

    companion object {
        val USERNAME = stringPreferencesKey("username")
        val PASSWORD = stringPreferencesKey("password")
        val EMAIL = stringPreferencesKey("email")
        val HEIGHT = stringPreferencesKey("height")
        val WEIGHT = stringPreferencesKey("weight")
        val IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
    }

    suspend fun saveUser(
        username: String,
        password: String,
        email: String,
        height: String,
        weight: String
    ) {
        context.dataStore.edit { prefs ->
            prefs[USERNAME] = username
            prefs[PASSWORD] = password
            prefs[EMAIL] = email
            prefs[HEIGHT] = height
            prefs[WEIGHT] = weight
        }
    }

    suspend fun getUser(): Map<String, String> {
        val prefs = context.dataStore.data.first()
        return mapOf(
            "username" to (prefs[USERNAME] ?: ""),
            "password" to (prefs[PASSWORD] ?: ""),
            "email" to (prefs[EMAIL] ?: ""),
            "height" to (prefs[HEIGHT] ?: ""),
            "weight" to (prefs[WEIGHT] ?: "")
        )
    }

    suspend fun clearUser() {
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
    }

    suspend fun saveLoginStatus(isLoggedIn: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[IS_LOGGED_IN] = isLoggedIn
        }
    }

    suspend fun getLoginStatus(): Boolean {
        val prefs = context.dataStore.data.first()
        return prefs[IS_LOGGED_IN] ?: false
    }
}
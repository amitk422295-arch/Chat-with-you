package com.chatarena

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.chatarena.ui.auth.AuthScreen
import com.chatarena.ui.main.MainAppScreen

// Dummy User Data class (Baad me API se aayega)
data class User(val userCode: String, val fullName: String)

@Composable
fun AppRoot() {
    // App State
    var currentUser by remember { mutableStateOf<User?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        // ==========================================
        // 🚦 ROUTING LOGIC (Auth vs Main App)
        // ==========================================
        if (currentUser == null) {
            AuthScreen(
                onLoginSuccess = {
                    // Jab login ho jaye, toh dummy user set karo (baad me API se real user aayega)
                    currentUser = User("user_123", "Amit")
                }
            )
        } else {
            MainAppScreen()
        }
    }
}

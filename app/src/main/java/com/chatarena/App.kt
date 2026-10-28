package com.chatarena

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.chatarena.ui.auth.AuthScreen
import com.chatarena.ui.main.MainAppScreen
import com.chatarena.utils.TokenManager

// User Data class
data class User(val userCode: String, val fullName: String)

@Composable
fun AppRoot() {
    val context = LocalContext.current
    
    // App start hote hi check karo ki token saved hai ya nahi
    var currentUser by remember {
        mutableStateOf(
            if (TokenManager.isLoggedIn(context)) {
                User(
                    userCode = TokenManager.getUserCode(context) ?: "user",
                    fullName = TokenManager.getFullName(context) ?: "User"
                )
            } else {
                null
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // ==========================================
        // 🚦 ROUTING LOGIC (Auth vs Main App)
        // ==========================================
        if (currentUser == null) {
            AuthScreen(
                onLoginSuccess = {
                    // Jab login ho jaye, toh TokenManager se data utha kar state update karo
                    currentUser = User(
                        userCode = TokenManager.getUserCode(context) ?: "user",
                        fullName = TokenManager.getFullName(context) ?: "User"
                    )
                }
            )
        } else {
            MainAppScreen(
                // Logout function pass kiya hai (Agle step me isko MainAppScreen me jodenge)
                onLogout = {
                    TokenManager.logout(context) // Token clear karo
                    currentUser = null // Wapas login screen par bhejo
                }
            )
        }
    }
}

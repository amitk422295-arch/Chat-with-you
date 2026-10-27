package com.chatarena

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatarena.ui.auth.AuthScreen
import com.chatarena.ui.main.MainAppScreen

// Dummy User Data class (Baad me API se aayega)
data class User(val userCode: String, val fullName: String)

@Composable
fun AppRoot() {
    // App State
    var currentUser by remember { mutableStateOf<User?>(null) }
    
    // Notification States
    var showNotifBtn by remember { mutableStateOf(true) }
    var notifBtnLoading by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf("") }

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

        // ==========================================
        // 🔔 NOTIFICATION BUTTON OVERLAY
        // ==========================================
        if (showNotifBtn && currentUser != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp)
                    .fillMaxWidth(0.9f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Main Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFF007AFF), Color(0xFF00C6FF))
                            ),
                            shape = RoundedCornerShape(24.dp)
                        )
                        .clickable(enabled = !notifBtnLoading) {
                            notifBtnLoading = true
                            statusMsg = "Step 1: Requesting permission..."
                            
                            // TODO: Android FCM Logic yahan aayega
                            // Yahan hum FirebaseMessaging.getInstance().token use karenge
                            
                            // Dummy UI update for now
                            notifBtnLoading = false
                            statusMsg = "✅ Notifications Enabled!"
                            showNotifBtn = false
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (notifBtnLoading) "⏳ Please wait..." else "🔔 Enable Notifications",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                // Status Message Box
                if (statusMsg.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = statusMsg,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

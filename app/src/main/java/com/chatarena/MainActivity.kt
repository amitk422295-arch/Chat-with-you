package com.chatarena

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Edge-to-Edge UI Setup
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // 🔥 YEH NAYI LINE ADD KAREIN 🔥
        // App start hote hi WebSocket connect hoga aur background me messages receive hone lagenge
        GlobalChatManager.init(this) 
        
        setContent {
            AppRoot()
        }
    }
}

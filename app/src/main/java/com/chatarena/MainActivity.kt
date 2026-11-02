package com.chatarena

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Edge-to-Edge UI Setup
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // 1. Background Service Start karo (Yeh WebSocket ko zinda rakhegi)
        startBackgroundService()

        // 2. Battery Optimization se chhutkaara maango (WhatsApp VIP Pass)
        requestIgnoreBatteryOptimizations()
        
        setContent {
            AppRoot()
        }
    }

    private fun startBackgroundService() {
        val serviceIntent = Intent(this, ChatBackgroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    @SuppressLint("BatteryLife")
    private fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent()
            val packageName = packageName
            val powerManager = getSystemService(POWER_SERVICE) as android.os.PowerManager
            
            // Agar app pehle se exempt nahi hai, toh user ko popup dikhao
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
        }
    }
}

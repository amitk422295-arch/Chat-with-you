package com.chatarena

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // यह लाइन जादुई है: यह सिस्टम को बताती है कि ऐप फुल स्क्रीन (Edge-to-Edge) लेगा 
        // और Insets (जैसे कीबोर्ड और स्टेटस बार) को Compose खुद हैंडल करेगा।
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        setContent {
            AppRoot()
        }
    }
}

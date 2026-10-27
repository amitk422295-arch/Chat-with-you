package com.chatarena.ui.call

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// States for the Call Screen
enum class CallState { IDLE, INCOMING, OUTGOING, ACTIVE }

data class CallerInfo(
    val userCode: String,
    val name: String,
    val avatarUrl: String? = null
)

@Composable
fun CallScreen(
    callState: CallState,
    callerInfo: CallerInfo?,
    isVideoCall: Boolean,
    onAccept: () -> Unit = {},
    onReject: () -> Unit = {},
    onEndCall: () -> Unit = {},
    onToggleMic: () -> Unit = {},
    onToggleCam: () -> Unit = {}
) {
    if (callState == CallState.IDLE) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0C12)) // Dark background
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        when (callState) {
            CallState.INCOMING -> {
                IncomingCallUI(callerInfo, isVideoCall, onAccept, onReject)
            }
            CallState.OUTGOING, CallState.ACTIVE -> {
                ActiveCallUI(callerInfo, isVideoCall, callState == CallState.OUTGOING, onToggleMic, onToggleCam, onEndCall)
            }
            else -> {}
        }
    }
}

@Composable
fun IncomingCallUI(
    callerInfo: CallerInfo?,
    isVideoCall: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(120.dp).background(Color.DarkGray, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isVideoCall) Icons.Default.Videocam else Icons.Default.Call,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(60.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        Text(callerInfo?.name ?: "Unknown", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Incoming ${if (isVideoCall) "Video" else "Audio"} Call...", color = Color.Gray, fontSize = 16.sp)

        Spacer(modifier = Modifier.height(60.dp))
        
        Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
            // Reject Button
            FloatingActionButton(
                onClick = onReject,
                containerColor = Color(0xFFFF3B5C),
                modifier = Modifier.size(70.dp),
                shape = CircleShape
            ) {
                Icon(Icons.Default.CallEnd, contentDescription = "Reject", tint = Color.White, modifier = Modifier.size(32.dp))
            }
            
            // Accept Button
            FloatingActionButton(
                onClick = onAccept,
                containerColor = Color(0xFF00C853),
                modifier = Modifier.size(70.dp),
                shape = CircleShape
            ) {
                Icon(Icons.Default.Call, contentDescription = "Accept", tint = Color.White, modifier = Modifier.size(32.dp))
            }
        }
    }
}

@Composable
fun ActiveCallUI(
    callerInfo: CallerInfo?,
    isVideoCall: Boolean,
    isOutgoing: Boolean,
    onToggleMic: () -> Unit,
    onToggleCam: () -> Unit,
    onEndCall: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        
        // 🚨 DUMMY UI FOR VIDEO: (Yahan baad me WebRTC ka View aayega)
        if (isVideoCall && !isOutgoing) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                Text("Remote Video Feed (WebRTC)", color = Color.Gray)
            }
        } else {
            // Audio call UI ya Outgoing UI
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(100.dp).background(Color.DarkGray, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(callerInfo?.name?.take(1)?.uppercase() ?: "U", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(callerInfo?.name ?: "Unknown", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(if (isOutgoing) "Calling..." else "Connected", color = if (isOutgoing) Color.Gray else Color(0xFF00FF88), fontSize = 16.sp)
            }
        }

        // 🚨 DUMMY UI FOR LOCAL VIDEO PIP: (Yahan baad me apna camera feed aayega)
        if (isVideoCall) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(20.dp)
                    .size(120.dp, 160.dp)
                    .background(Color.DarkGray, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("Local Cam", color = Color.White)
            }
        }

        // Bottom Controls
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mute Button
            FloatingActionButton(
                onClick = onToggleMic,
                containerColor = Color.White.copy(alpha = 0.2f),
                modifier = Modifier.size(55.dp),
                shape = CircleShape
            ) {
                Icon(Icons.Default.Mic, contentDescription = "Mute", tint = Color.White)
            }

            // End Call Button
            FloatingActionButton(
                onClick = onEndCall,
                containerColor = Color(0xFFFF3B5C),
                modifier = Modifier.size(65.dp),
                shape = CircleShape
            ) {
                Icon(Icons.Default.CallEnd, contentDescription = "End Call", tint = Color.White, modifier = Modifier.size(30.dp))
            }

            // Camera Toggle (Only for Video Calls)
            if (isVideoCall) {
                FloatingActionButton(
                    onClick = onToggleCam,
                    containerColor = Color.White.copy(alpha = 0.2f),
                    modifier = Modifier.size(55.dp),
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Videocam, contentDescription = "Camera", tint = Color.White)
                }
            }
        }
    }
}

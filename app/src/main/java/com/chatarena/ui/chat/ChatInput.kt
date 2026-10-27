package com.chatarena.ui.chat

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatarena.ui.auth.*
import kotlinx.coroutines.delay

@Composable
fun ChatInput(
    messageText: String,
    onMessageChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onFileSelect: () -> Unit,
    onTypingStatusChange: (Boolean) -> Unit,
    isVoiceRecording: Boolean = false,
    voiceOverlay: Boolean = false,
    voiceSlideState: String = "cancel",
    onVoiceStart: () -> Unit = {},
    onVoiceCancel: () -> Unit = {},
    onVoiceSend: () -> Unit = {}
) {
    // Typing Indicator Timer Logic
    var typingActive by remember { mutableStateOf(false) }

    LaunchedEffect(messageText) {
        if (messageText.isNotBlank() && !isVoiceRecording) {
            if (!typingActive) {
                typingActive = true
                onTypingStatusChange(true)
            }
            delay(2000) // 2 seconds of inactivity -> stop typing
            typingActive = false
            onTypingStatusChange(false)
        } else if (messageText.isBlank() && typingActive) {
            typingActive = false
            onTypingStatusChange(false)
        }
    }

    Box(modifier = Modifier.fillMaxWidth().background(CardColor)) {
        // Main Input Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            // Text Input Area
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(20.dp),
                color = BgColor,
                shadowElevation = 2.dp
            ) {
                BasicTextField(
                    value = messageText,
                    onValueChange = onMessageChange,
                    textStyle = TextStyle(color = TextColor, fontSize = 15.sp),
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .pointerInput(Unit) {
                            // Voice recording gestures (Mockup for Android setup)
                            detectTapGestures(
                                onPress = {
                                    if (messageText.isEmpty()) {
                                        onVoiceStart()
                                        tryAwaitRelease()
                                        // Logic to check if slid up to send or cancel
                                        onVoiceCancel() // Example default
                                    }
                                }
                            )
                        },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = { onSendMessage() }
                    ),
                    maxLines = 8, // Max lines before scrolling
                    decorationBox = { innerTextField ->
                        if (messageText.isEmpty()) {
                            Text("Type a message...", color = SubTextColor, fontSize = 15.sp)
                        }
                        innerTextField()
                    }
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Attachment Button
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clickable { onFileSelect() },
                contentAlignment = Alignment.Center
            ) {
                Text("📎", fontSize = 22.sp)
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Send Button
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(PrimaryBlue, CircleShape)
                    .clickable { onSendMessage() },
                contentAlignment = Alignment.Center
            ) {
                Text("➤", color = Color.White, fontSize = 20.sp)
            }
        }

        // Voice Recording Overlay
        if (voiceOverlay) {
            VoiceOverlay(
                isSendMode = voiceSlideState == "send"
            )
        }
    }
}

@Composable
fun VoiceOverlay(isSendMode: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .background(if (isSendMode) PrimaryBlue.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.8f))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (isSendMode) "Release to send" else "Slide up to send",
                color = Color.White,
                fontSize = 16.sp
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Animated Wave
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val infiniteTransition = rememberInfiniteTransition()
                repeat(15) { index ->
                    val height by infiniteTransition.animateFloat(
                        initialValue = 10f,
                        targetValue = 40f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(400, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        )
                    )
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(height.dp)
                            .background(Color.White, CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = if (isSendMode) "Release to send" else "Slide up ↑ to send · Release to cancel",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp
            )
        }
    }
}

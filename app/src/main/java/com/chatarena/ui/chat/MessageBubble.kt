package com.chatarena.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatarena.network.Message
import com.chatarena.ui.auth.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MessageBubble(
    message: Message,
    isMine: Boolean,
    showSender: Boolean,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onLongPress: () -> Unit,
    onReplySwipe: () -> Unit,
    onToggleSelection: () -> Unit,
    onJumpToReply: () -> Unit,
    onMediaTap: () -> Unit
) {
    var swipeOffsetX by remember { mutableStateOf(0f) }
    var showReplyIcon by remember { mutableStateOf(false) }

    val isDeleted = (message.isDeleted == 1) || message.text == "This message was deleted"
    val isTextUnavailable = message.text == "🔒 Message Unavailable" || message.text == "🔒 Message Error"

    // Swipe to reply logic
    val dragModifier = Modifier.pointerInput(Unit) {
        detectHorizontalDragGestures(
            onDragEnd = {
                if (swipeOffsetX > 40f) {
                    onReplySwipe()
                }
                swipeOffsetX = 0f
                showReplyIcon = false
            },
            onHorizontalDrag = { change, dragAmount ->
                change.consume()
                if (!isDeleted && !isSelectionMode) {
                    swipeOffsetX = (swipeOffsetX + dragAmount).coerceIn(0f, 80f)
                    showReplyIcon = swipeOffsetX > 40f
                }
            }
        )
    }

    val clickModifier = Modifier.combinedClickable(
        onClick = {
            if (isSelectionMode) onToggleSelection()
        },
        onLongClick = {
            if (!isDeleted) onLongPress()
        }
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        if (showReplyIcon) {
            Text(
                text = "↩",
                fontSize = 20.sp,
                color = PrimaryBlue,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 16.dp)
            )
        }

        Surface(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .graphicsLayer { translationX = swipeOffsetX }
                .then(dragModifier)
                .then(clickModifier),
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (isMine) 16.dp else 4.dp,
                bottomEnd = if (isMine) 4.dp else 16.dp
            ),
            color = when {
                isSelected -> PrimaryBlue.copy(alpha = 0.3f)
                isDeleted -> Color.Transparent
                isMine -> PrimaryBlue
                else -> CardColor
            },
            shadowElevation = if (isDeleted) 0.dp else 2.dp
        ) {
            Column(
                modifier = Modifier
                    .then(if (isDeleted) Modifier.border(1.dp, SubTextColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp)) else Modifier)
                    .padding(12.dp)
            ) {
                if (showSender && !isMine && !isDeleted) {
                    Text(
                        text = message.senderName ?: "Unknown",
                        color = PrimaryBlue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                if (message.replyTo != null && !isDeleted) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                            .clickable { onJumpToReply() }
                    ) {
                        Row {
                            Box(modifier = Modifier.width(3.dp).height(30.dp).background(PrimaryBlue))
                            Column(modifier = Modifier.padding(6.dp)) {
                                Text("Replying...", color = PrimaryBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("Original message", color = SubTextColor, fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }
                }

                if (isDeleted) {
                    Text(
                        text = "This message was deleted",
                        color = SubTextColor,
                        fontSize = 14.sp,
                        fontStyle = FontStyle.Italic
                    )
                } else if (message.messageType == "image") {
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .background(Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .clickable { onMediaTap() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("📷 Tap to view", color = TextColor)
                    }
                } else if (isTextUnavailable) {
                    Text(
                        text = "Waiting for this message...",
                        color = SubTextColor,
                        fontSize = 13.sp,
                        fontStyle = FontStyle.Italic
                    )
                } else {
                    Text(
                        text = message.text,
                        color = if (isMine) Color.White else TextColor,
                        fontSize = 15.sp
                    )
                }

                // ==========================================
                // 🔥 TIME & STATUS TICKS (WhatsApp Style) 🔥
                // ==========================================
                Row(
                    modifier = Modifier.align(Alignment.End).padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTime(message.createdAt),
                        color = if (isMine) Color.White.copy(alpha = 0.7f) else SubTextColor,
                        fontSize = 10.sp
                    )
                    
                    if (isMine) {
                        Spacer(modifier = Modifier.width(4.dp))
                        
                        // Status logic based on message.status
                        when (message.status) {
                            "pending" -> {
                                // Ghadi (Clock) - Server tak nahi pahuncha
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = "Pending",
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            "sent" -> {
                                // Single Tick - Server tak pahunch gaya
                                Icon(
                                    imageVector = Icons.Default.Done,
                                    contentDescription = "Sent",
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            "delivered" -> {
                                // Double Tick - Device tak pahunch gaya
                                Icon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = "Delivered",
                                    tint = Color.White.copy(alpha = 0.9f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            "read" -> {
                                // Blue Glowing Double Tick - Padh liya
                                Icon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = "Read",
                                    tint = Color(0xFF00E5FF), // Bright Cyan/Blue color for glow effect
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            else -> {
                                // Fallback
                                Icon(
                                    imageVector = Icons.Default.Done,
                                    contentDescription = "Sent",
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(timestamp: Long): String {
    return try {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        sdf.format(Date(timestamp))
    } catch (e: Exception) {
        "12:00 PM"
    }
}

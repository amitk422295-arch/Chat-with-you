package com.chatarena.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatarena.ui.auth.*

data class Message(
    val id: String,
    val text: String,
    val isMine: Boolean,
    val senderName: String,
    val type: String = "text", // "text", "image", "video"
    val status: String = "sent", // sent, delivered, read
    val replyToId: String? = null
)

@Composable
fun ChatRoomScreen(
    chatName: String = "User",
    isGroupChat: Boolean = false,
    messages: List<Message> = emptyList(),
    onBackClick: () -> Unit = {},
    onSendMessage: (String) -> Unit = {}
) {
    var messageText by remember { mutableStateOf("") }
    var selectedMessages by remember { mutableStateOf<List<Message>>(emptyList()) }
    var replyToMessage by remember { mutableStateOf<Message?>(null) }
    
    var showDeleteModal by remember { mutableStateOf(false) }
    var showEditModal by remember { mutableStateOf(false) }
    var editingText by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().background(BgColor)) {
        // Header
        ChatHeader(chatName, isGroupChat, onBackClick)
        
        // Selected Messages Action Bar
        if (selectedMessages.isNotEmpty()) {
            FloatingActionBar(
                count = selectedMessages.size,
                onClear = { selectedMessages = emptyList() },
                onReply = { 
                    replyToMessage = selectedMessages.first()
                    selectedMessages = emptyList() 
                },
                onDelete = { showDeleteModal = true }
            )
        }

        // Message List
        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            reverseLayout = false
        ) {
            items(messages, key = { it.id }) { msg ->
                MessageBubble(
                    message = msg,
                    isSelected = selectedMessages.contains(msg),
                    onLongClick = { 
                        if (!selectedMessages.contains(msg)) {
                            selectedMessages = selectedMessages + msg 
                        }
                    }
                )
            }
        }

        // Reply Bar
        if (replyToMessage != null) {
            Row(
                modifier = Modifier.fillMaxWidth().background(CardColor).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                    Text("Replying to ${replyToMessage?.senderName}", color = PrimaryBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(replyToMessage?.text ?: "Media", color = SubTextColor, fontSize = 13.sp, maxLines = 1)
                }
                Text("✕", color = SubTextColor, fontSize = 20.sp, modifier = Modifier.clickable { replyToMessage = null }.padding(8.dp))
            }
        }

        // Input Area
        Row(
            modifier = Modifier.fillMaxWidth().background(CardColor).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("📎", fontSize = 24.sp, modifier = Modifier.padding(horizontal = 8.dp))
            
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(20.dp),
                color = BgColor, shadowElevation = 2.dp
            ) {
                BasicTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    modifier = Modifier.padding(12.dp),
                    decorationBox = { inner ->
                        if (messageText.isEmpty()) Text("Type a message...", color = SubTextColor)
                        inner()
                    }
                )
            }
            
            Box(
                modifier = Modifier.size(48.dp).background(PrimaryBlue, CircleShape).clickable {
                    if (messageText.isNotBlank()) {
                        onSendMessage(messageText)
                        messageText = ""
                        replyToMessage = null
                    }
                },
                contentAlignment = Alignment.Center
            ) {
                Text("➤", color = Color.White, fontSize = 20.sp)
            }
        }
    }

    // Modals
    if (showDeleteModal) {
        AlertDialog(
            onDismissRequest = { showDeleteModal = false },
            containerColor = CardColor,
            title = { Text("Delete Message?") },
            text = { Text("Are you sure you want to delete this message?") },
            confirmButton = {
                TextButton(onClick = { showDeleteModal = false; selectedMessages = emptyList() }) {
                    Text("Delete for Me", color = DangerRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteModal = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun ChatHeader(name: String, isGroup: Boolean, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(CardColor).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("⬅", fontSize = 24.sp, color = PrimaryBlue, modifier = Modifier.clickable { onBack() })
        Spacer(modifier = Modifier.width(16.dp))
        Box(modifier = Modifier.size(40.dp).background(BgColor, CircleShape), contentAlignment = Alignment.Center) {
            Text(name.take(1).uppercase(), color = PrimaryBlue, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, color = TextColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            if (isGroup) Text("Group", color = SubTextColor, fontSize = 12.sp)
        }
        Text("📞", fontSize = 20.sp, modifier = Modifier.padding(8.dp))
        Text("📹", fontSize = 20.sp, modifier = Modifier.padding(8.dp))
    }
}

@Composable
fun MessageBubble(message: Message, isSelected: Boolean, onLongClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        contentAlignment = if (message.isMine) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            color = if (isSelected) PrimaryBlue.copy(alpha = 0.3f) else if (message.isMine) PrimaryBlue else CardColor,
            shape = RoundedCornerShape(12.dp),
            shadowElevation = 1.dp,
            modifier = Modifier.widthIn(max = 280.dp).clickable { onLongClick() }
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (!message.isMine) {
                    Text(message.senderName, color = PrimaryBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                }
                Text(
                    text = message.text,
                    color = if (message.isMine) Color.White else TextColor,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
fun FloatingActionBar(count: Int, onClear: () -> Unit, onReply: () -> Unit, onDelete: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        shape = RoundedCornerShape(16.dp),
        color = CardColor, shadowElevation = 8.dp
    ) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⬅", color = PrimaryBlue, modifier = Modifier.clickable { onClear() })
                Spacer(modifier = Modifier.width(12.dp))
                Text("$count selected", fontWeight = FontWeight.Bold, color = TextColor)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (count == 1) Text("↩", fontSize = 20.sp, color = PrimaryBlue, modifier = Modifier.clickable { onReply() })
                Text("🗑", fontSize = 20.sp, color = DangerRed, modifier = Modifier.clickable { onDelete() })
            }
        }
    }
}

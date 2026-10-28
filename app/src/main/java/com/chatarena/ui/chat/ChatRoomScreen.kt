package com.chatarena.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatarena.ChatItem
import com.chatarena.network.ChatWebSocketClient
import com.chatarena.network.Message
import com.chatarena.network.SendMessagePayload
import com.chatarena.ui.auth.BgColor
import com.chatarena.ui.auth.CardColor
import com.chatarena.ui.auth.PrimaryBlue
import com.chatarena.ui.auth.SubTextColor
import com.chatarena.ui.auth.TextColor
import com.chatarena.ui.camera.CameraScreen
import com.chatarena.utils.TokenManager
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun ChatRoomScreen(
    chatItem: ChatItem,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    
    val currentUserCode = TokenManager.getUserCode(context) ?: ""
    val token = TokenManager.getToken(context) ?: ""
    
    var messageText by remember { mutableStateOf("") }
    var showCamera by remember { mutableStateOf(false) }
    
    // Messages ki state
    val messages = remember { mutableStateListOf<Message>() }

    // 1. WebSocket Connect & Listen
    LaunchedEffect(Unit) {
        ChatWebSocketClient.connect(currentUserCode, token)

        ChatWebSocketClient.socketEvents.collect { response ->
            if (response.type == "receive-message" && response.payload?.message != null) {
                val newMsg = response.payload.message
                if (newMsg.senderCode == chatItem.id || newMsg.senderCode == currentUserCode) {
                    messages.add(newMsg)
                    if (messages.isNotEmpty()) {
                        listState.animateScrollToItem(messages.size - 1)
                    }
                }
            }
        }
    }

    // 2. Message Send Logic
    fun sendMessage() {
        if (messageText.isBlank()) return
        
        val clientMsgId = UUID.randomUUID().toString()
        val payload = SendMessagePayload(
            receiverCode = chatItem.id,
            text = messageText.trim(),
            clientMessageId = clientMsgId
        )

        val localMsg = Message(
            clientMessageId = clientMsgId,
            text = messageText.trim(),
            senderCode = currentUserCode,
            mine = true,
            status = "pending",
            createdAt = System.currentTimeMillis()
        )
        messages.add(localMsg)
        
        ChatWebSocketClient.sendMessage(payload)
        
        messageText = ""
        
        scope.launch {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Camera Screen Overlay
    if (showCamera) {
        CameraScreen(
            onPhotoCaptured = { uri ->
                showCamera = false
            },
            onClose = { showCamera = false }
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BgColor)
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            // --- Top Bar ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardColor)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = PrimaryBlue,
                    modifier = Modifier
                        .size(28.dp)
                        .clickable { onBack() }
                )
                Spacer(modifier = Modifier.width(16.dp))
                
                Box(
                    modifier = Modifier.size(40.dp).background(BgColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(chatItem.name.take(1).uppercase(), fontWeight = FontWeight.Bold, color = PrimaryBlue)
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(chatItem.name, color = TextColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Tap here for contact info", color = SubTextColor, fontSize = 12.sp)
                }
            }

            // --- Messages List ---
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(messages) { msg ->
                    MessageBubbleItem(msg)
                }
            }

            // --- Input Area ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardColor)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showCamera = true }) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = "Camera", tint = PrimaryBlue)
                }

                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    color = BgColor,
                    shadowElevation = 4.dp
                ) {
                    BasicTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        textStyle = TextStyle(color = TextColor, fontSize = 15.sp),
                        decorationBox = { inner ->
                            if (messageText.isEmpty()) Text("Message", color = SubTextColor)
                            inner()
                        }
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = { sendMessage() },
                    modifier = Modifier.background(PrimaryBlue, CircleShape)
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.White)
                }
            }
        }
    }
}

@Composable
fun MessageBubbleItem(msg: Message) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (msg.mine) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            color = if (msg.mine) PrimaryBlue else CardColor,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (msg.mine) 16.dp else 4.dp,
                bottomEnd = if (msg.mine) 4.dp else 16.dp
            ),
            shadowElevation = 2.dp,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = msg.text,
                    color = if (msg.mine) Color.White else TextColor,
                    fontSize = 15.sp
                )
            }
        }
    }
}

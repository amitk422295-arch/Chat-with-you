package com.chatarena.ui.main

import android.widget.Toast
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
import com.chatarena.ui.chat.MessageBubble
import com.chatarena.utils.CryptoManager
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
    
    val messages = remember { mutableStateListOf<Message>() }
    
    // States for Encryption Key
    var targetPublicKey by remember { mutableStateOf<String?>(null) }
    var isKeyLoaded by remember { mutableStateOf(false) }
    var isKeyLoading by remember { mutableStateOf(true) } // NAYA: Loading state

    // Function to fetch key (Retry ke liye alag nikal diya)
    fun fetchPublicKey() {
        isKeyLoading = true
        ChatWebSocketClient.getPublicKey(chatItem.id) { pubKey ->
            targetPublicKey = pubKey
            isKeyLoaded = pubKey != null
            isKeyLoading = false
        }
    }

    LaunchedEffect(Unit) {
        ChatWebSocketClient.connect(currentUserCode, token)
        
        // Pehli baar key fetch karo
        fetchPublicKey()

        ChatWebSocketClient.socketEvents.collect { response ->
            if (response.type == "new-message" && response.payload?.message != null) {
                val newMsg = response.payload.message
                
                if (newMsg.senderCode == chatItem.id) {
                    val senderPubKey = targetPublicKey 
                    if (senderPubKey != null) {
                        val decryptedText = CryptoManager.decryptText(
                            context = context,
                            cipherText = newMsg.text,
                            targetCode = newMsg.senderCode,
                            targetPubKeyBase64 = senderPubKey
                        )
                        
                        val decryptedMsg = newMsg.copy(text = decryptedText, mine = false)
                        
                        if (messages.none { it.messageId == newMsg.messageId || it.clientMessageId == newMsg.clientMessageId }) {
                            messages.add(decryptedMsg)
                            if (messages.isNotEmpty()) {
                                listState.animateScrollToItem(messages.size - 1)
                            }
                        }
                    }
                }
            }
        }
    }

    fun sendMessage() {
        if (messageText.isBlank()) return
        
        // Agar key load ho rahi hai toh wait karo
        if (isKeyLoading) {
            Toast.makeText(context, "Fetching encryption keys, please wait...", Toast.LENGTH_SHORT).show()
            return
        }

        // Agar key nahi mili, toh retry karo
        if (!isKeyLoaded || targetPublicKey == null) {
            Toast.makeText(context, "Key not found. Retrying...", Toast.LENGTH_SHORT).show()
            fetchPublicKey()
            return
        }

        val clientMsgId = UUID.randomUUID().toString()
        val plainText = messageText.trim()

        val encryptedText = try {
            CryptoManager.encryptText(
                context = context,
                text = plainText,
                targetCode = chatItem.id, 
                targetPubKeyBase64 = targetPublicKey!!
            )
        } catch (e: Exception) {
            Toast.makeText(context, "Encryption Failed!", Toast.LENGTH_SHORT).show()
            return
        }

        val localMsg = Message(
            clientMessageId = clientMsgId,
            text = plainText, 
            senderCode = currentUserCode,
            receiverCode = chatItem.id,
            mine = true,
            status = "pending",
            createdAt = System.currentTimeMillis()
        )
        messages.add(localMsg)
        messageText = ""
        
        scope.launch {
            listState.animateScrollToItem(messages.size - 1)
        }

        val payload = SendMessagePayload(
            targetCode = chatItem.id, 
            text = encryptedText, 
            clientMessageId = clientMsgId,
            messageType = "text",
            senderName = TokenManager.getFullName(context),
            senderAvatar = "",
            senderMobile = TokenManager.getEmail(context)
        )

        ChatWebSocketClient.sendMessage(payload) { response ->
            if (response != null && response.has("success") && response.get("success").asBoolean) {
                val index = messages.indexOfFirst { it.clientMessageId == clientMsgId }
                if (index != -1) {
                    messages[index] = messages[index].copy(status = "sent")
                }
            } else {
                val index = messages.indexOfFirst { it.clientMessageId == clientMsgId }
                if (index != -1) {
                    messages[index] = messages[index].copy(status = "failed")
                }
            }
        }
    }

    if (showCamera) {
        CameraScreen(
            onPhotoCaptured = { uri -> showCamera = false },
            onClose = { showCamera = false }
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BgColor)
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
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

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(messages) { msg ->
                    MessageBubble(
                        message = msg,
                        isMine = msg.mine,
                        showSender = false,
                        isSelected = false,
                        isSelectionMode = false,
                        onLongPress = {},
                        onReplySwipe = {},
                        onToggleSelection = {},
                        onJumpToReply = {},
                        onMediaTap = {}
                    )
                }
            }

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

                // NAYA: Button me loading spinner aur retry logic
                IconButton(
                    onClick = { sendMessage() },
                    modifier = Modifier.background(
                        if (isKeyLoaded) PrimaryBlue else Color.Gray,
                        CircleShape
                    )
                ) {
                    if (isKeyLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.White)
                    }
                }
            }
        }
    }
}

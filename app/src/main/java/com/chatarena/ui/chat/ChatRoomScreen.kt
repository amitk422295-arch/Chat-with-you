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
import com.chatarena.database.AppDatabase
import com.chatarena.database.MessageEntity
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
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
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
    
    var messageText by remember { mutableStateOf("") }
    var showCamera by remember { mutableStateOf(false) }
    
    // 1. Database Initialize
    val db = AppDatabase.getDatabase(context)
    val messageDao = db.messageDao()

    // 2. Observe messages from DB (UI auto-update hoga)
    val messagesEntity by messageDao.getMessagesForChat(chatItem.id).collectAsState(initial = emptyList())

    // 3. Convert Entity to UI Model for MessageBubble
    val messages = messagesEntity.map { entity ->
        Message(
            messageId = entity.messageId,
            clientMessageId = entity.clientMessageId,
            text = entity.decryptedText,
            messageType = entity.messageType,
            senderCode = entity.senderCode,
            receiverCode = entity.receiverCode,
            mine = entity.senderCode == currentUserCode,
            status = entity.status,
            createdAt = entity.createdAt,
            replyTo = entity.replyTo,
            isDeleted = entity.isDeleted
        )
    }

    // States for Encryption Key
    var targetPublicKey by remember { mutableStateOf<String?>(null) }
    var isKeyLoaded by remember { mutableStateOf(false) }
    var isKeyLoading by remember { mutableStateOf(true) }

    // Auto Scroll to bottom when new message arrives
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Fetch Public Key
    fun fetchPublicKey(onKeyFetched: () -> Unit) {
        isKeyLoading = true
        ChatWebSocketClient.getPublicKey(chatItem.id) { pubKey ->
            targetPublicKey = pubKey
            isKeyLoaded = pubKey != null
            isKeyLoading = false
            
            if (pubKey != null) {
                CryptoManager.savePublicKeyToDisk(context, chatItem.id, pubKey)
            }
            onKeyFetched()
        }
    }

    // Fetch History
    fun fetchMessageHistory(pubKey: String?) {
        ChatWebSocketClient.getMessages(chatItem.id) { response ->
            if (response != null && response.has("success") && response.get("success").asBoolean) {
                val messagesArray = response.getAsJsonArray("messages")
                if (messagesArray != null) {
                    val gson = Gson()
                    scope.launch(Dispatchers.IO) {
                        for (element in messagesArray) {
                            try {
                                val msgObj = element.asJsonObject
                                val msg = gson.fromJson(msgObj, Message::class.java)
                                
                                val decryptedText = if (msg.messageType == "text" && msg.text.startsWith("E2E:")) {
                                    try {
                                        val keyToUse = pubKey ?: CryptoManager.getPublicKeyFromDisk(context, msg.senderCode ?: "")
                                        if (keyToUse != null) {
                                            CryptoManager.decryptText(context, msg.text, msg.senderCode ?: "", keyToUse)
                                        } else {
                                            "🔒 Key Missing"
                                        }
                                    } catch (e: Exception) {
                                        "🔒 Decryption Failed"
                                    }
                                } else {
                                    msg.text
                                }

                                val entity = MessageEntity(
                                    messageId = msg.messageId ?: msg.clientMessageId ?: UUID.randomUUID().toString(),
                                    clientMessageId = msg.clientMessageId,
                                    chatId = chatItem.id,
                                    senderCode = msg.senderCode ?: "",
                                    receiverCode = msg.receiverCode,
                                    encryptedText = msg.text,
                                    decryptedText = decryptedText,
                                    messageType = msg.messageType ?: "text",
                                    status = msg.status ?: "sent",
                                    createdAt = msg.createdAt ?: System.currentTimeMillis(),
                                    replyTo = msg.replyTo,
                                    isDeleted = msg.isDeleted ?: 0
                                )
                                
                                messageDao.insertMessage(entity)

                                // Agar dusre ka message hai, toh Delivered aur Read mark karo
                                if (msg.senderCode != currentUserCode && msg.messageId != null) {
                                    ChatWebSocketClient.sendMessageReceived(msg.messageId, msg.senderCode)
                                    ChatWebSocketClient.markMessageRead(msg.messageId, msg.senderCode)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        // Note: Socket connect() ab GlobalChatManager karta hai, isliye yahan nahi hai.
        val cachedKey = CryptoManager.getPublicKeyFromDisk(context, chatItem.id)
        
        if (cachedKey != null) {
            targetPublicKey = cachedKey
            isKeyLoaded = true
            isKeyLoading = false
            
            scope.launch {
                ChatWebSocketClient.connectionState.collect { isConnected ->
                    if (isConnected) {
                        fetchMessageHistory(cachedKey)
                    }
                }
            }
        } else {
            scope.launch {
                ChatWebSocketClient.connectionState.collect { isConnected ->
                    if (isConnected && targetPublicKey == null && isKeyLoading) {
                        fetchPublicKey {
                            if (targetPublicKey != null) {
                                fetchMessageHistory(targetPublicKey)
                            }
                        }
                    }
                }
            }
        }
    }

    fun sendMessage() {
        if (messageText.isBlank()) return
        
        if (isKeyLoading) {
            Toast.makeText(context, "Fetching encryption keys, please wait...", Toast.LENGTH_SHORT).show()
            return
        }

        if (!isKeyLoaded || targetPublicKey == null) {
            Toast.makeText(context, "Key not found. Retrying...", Toast.LENGTH_SHORT).show()
            fetchPublicKey {}
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

        // Backend requires 'E2E:' prefix
        val finalEncryptedText = if (encryptedText.startsWith("E2E:")) encryptedText else "E2E:$encryptedText"

        // 1. Save locally as PENDING (Clock Icon)
        val localEntity = MessageEntity(
            messageId = clientMsgId, // Temporarily using client ID as primary key
            clientMessageId = clientMsgId,
            chatId = chatItem.id,
            senderCode = currentUserCode,
            receiverCode = chatItem.id,
            encryptedText = finalEncryptedText,
            decryptedText = plainText, 
            status = "pending",
            createdAt = System.currentTimeMillis(),
            isDeleted = 0
        )
        
        scope.launch(Dispatchers.IO) {
            messageDao.insertMessage(localEntity)
        }
        
        messageText = ""

        // 2. Send to Server
        val payload = SendMessagePayload(
            targetCode = chatItem.id, 
            text = finalEncryptedText,
            clientMessageId = clientMsgId,
            messageType = "text",
            senderName = TokenManager.getFullName(context),
            senderAvatar = "",
            senderMobile = TokenManager.getEmail(context)
        )

        ChatWebSocketClient.sendMessage(payload) { response ->
            val isSuccess = response != null && response.has("success") && response.get("success").asBoolean
            
            scope.launch(Dispatchers.IO) {
                if (isSuccess && response!!.has("message")) {
                    // 🔥 FIX: Server ka asli messageId aur time nikaal kar DB update karein
                    val msgObj = response.getAsJsonObject("message")
                    val serverId = msgObj.get("messageId").asString
                    val serverTime = msgObj.get("createdAt").asLong
                    
                    // DB me Client ID ko Server ID se replace karo aur status 'sent' karo
                    messageDao.updateMessageWithServerDetails(clientMsgId, serverId, serverTime)
                } else {
                    // Agar server fail ho jaye toh status 'failed' karo
                    messageDao.updateMessageStatusByClientId(clientMsgId, "failed")
                }
            }
        }
    }

    // ================= UI PART =================
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
            // Top Bar
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

            // Chat Messages List
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

            // Bottom Input Area
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

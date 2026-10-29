package com.chatarena.ui.main

import kotlinx.coroutines.delay
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.chatarena.ChatItem
import com.chatarena.GlobalChatManager // 🔥 IMPORT ADD KIYA
import com.chatarena.database.AppDatabase
import com.chatarena.network.AIRequest
import com.chatarena.network.RetrofitClient
import com.chatarena.network.ChatWebSocketClient
import com.chatarena.ui.auth.*
import com.chatarena.utils.TokenManager
import kotlinx.coroutines.launch
import kotlin.random.Random

@Composable
fun MainAppScreen(onLogout: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 3 })

    var showAIModal by remember { mutableStateOf(false) }
    var showSettingsModal by remember { mutableStateOf(false) }
    var showCreateGroup by remember { mutableStateOf(false) }
    
    var activeChat by remember { mutableStateOf<ChatItem?>(null) }
    val currentUserCode = TokenManager.getUserCode(context) ?: ""

    // 1. DATABASE OBSERVE (Single Source of Truth)
    val db = remember { AppDatabase.getDatabase(context) }
    val messageDao = remember { db.messageDao() }
    val recentMessagesEntity by messageDao.getRecentChats().collectAsState(initial = emptyList())

    // 2. SERVER METADATA (Names, Avatars)
    val serverMetadata = remember { mutableStateMapOf<String, ChatItem>() }

    // 3. FINAL LIST MERGE
    val chatList = remember(recentMessagesEntity, serverMetadata.values.toList()) {
        val finalChatList = mutableListOf<ChatItem>()
        val dbChatIds = mutableSetOf<String>()

        for (entity in recentMessagesEntity) {
            dbChatIds.add(entity.chatId)
            val metadata = serverMetadata[entity.chatId]
            
            var subtitle = entity.decryptedText
            if (subtitle.startsWith("E2E:")) subtitle = "🔒 Message"

            finalChatList.add(
                ChatItem(
                    type = "user",
                    id = entity.chatId,
                    name = metadata?.name ?: entity.chatId,
                    avatarUrl = metadata?.avatarUrl,
                    subtitle = subtitle,
                    unreadCount = metadata?.unreadCount ?: 0
                )
            )
        }

        for (metadata in serverMetadata.values) {
            if (!dbChatIds.contains(metadata.id)) {
                var subtitle = metadata.subtitle
                if (subtitle.startsWith("E2E:")) subtitle = "🔒 Message"
                finalChatList.add(metadata.copy(subtitle = subtitle))
            }
        }
        finalChatList
    }

    LaunchedEffect(Unit) {
        // 🔥 GLOBAL MANAGER INIT (Yeh background me sab kuch handle karega)
        GlobalChatManager.init(context)

        // Server se sirf Names/Avatars fetch karne ke liye ek baar call karo
        ChatWebSocketClient.getRecentChats { response: com.google.gson.JsonObject? ->
            if (response != null && response.has("success") && response.get("success").asBoolean) {
                val chatsArray = response.getAsJsonArray("chats")
                if (chatsArray != null) {
                    for (i in 0 until chatsArray.size()) {
                        val element = chatsArray.get(i)
                        try {
                            val chatObj = element.asJsonObject
                            val id = chatObj.get("targetCode")?.asString ?: chatObj.get("userCode")?.asString ?: ""
                            val name = chatObj.get("name")?.asString ?: id
                            val subtitle = chatObj.get("lastMessage")?.asString ?: ""
                            val unreadCount = chatObj.get("unreadCount")?.asInt ?: 0
                            
                            if (id.isNotEmpty()) {
                                serverMetadata[id] = ChatItem(
                                    type = "user", id = id, name = name, subtitle = subtitle, unreadCount = unreadCount
                                )
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
        // ❌ YAHAN SE SOCKET EVENTS COLLECT WALA PURA BLOCK HATA DIYA GAYA HAI
    }

    Box(modifier = Modifier.fillMaxSize().background(BgColor)) {
        
        if (!showAIModal) {
            Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) { 
                Column(modifier = Modifier.background(CardColor)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("ChatArena", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = PrimaryBlue)
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            if (pagerState.currentPage == 0) {
                                Icon(Icons.Default.Groups, contentDescription = "New Group", tint = PrimaryBlue, 
                                    modifier = Modifier.size(24.dp).clickable { showCreateGroup = true })
                            }
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = SubTextColor, 
                                modifier = Modifier.size(24.dp).clickable { showSettingsModal = true })
                        }
                    }
                    
                    TabRow(
                        selectedTabIndex = pagerState.currentPage,
                        containerColor = CardColor,
                        contentColor = PrimaryBlue,
                        indicator = { tabPositions: List<androidx.compose.material3.TabPosition> ->
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                                color = PrimaryBlue
                            )
                        }
                    ) {
                        listOf("CHATS", "REELS", "CALLS").forEachIndexed { index: Int, title: String ->
                            Tab(
                                selected = pagerState.currentPage == index,
                                onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                                text = { Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                            )
                        }
                    }
                }

                HorizontalPager(
                    state = pagerState, 
                    modifier = Modifier.weight(1f),
                    beyondBoundsPageCount = 1 
                ) { page: Int ->
                    when (page) {
                        0 -> ChatListScreen(
                            chatList = chatList,
                            onItemClick = { clickedChat: ChatItem -> activeChat = clickedChat }
                        )
                        1 -> ReelsWebView(url = "https://reel-center-2.onrender.com")
                        2 -> CallsPlaceholder()
                    }
                }
            }

            if (pagerState.currentPage != 1) {
                Box(modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp).navigationBarsPadding()) {
                    Surface(
                        shape = CircleShape,
                        color = PrimaryBlue,
                        shadowElevation = 8.dp,
                        modifier = Modifier.size(60.dp).clickable { showAIModal = true }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("✨", fontSize = 24.sp)
                        }
                    }
                }
            }
        }

        if (activeChat != null) {
            ChatRoomScreen(
                chatItem = activeChat!!,
                onBack = { activeChat = null }
            )
        }

        if (showAIModal) {
            AIModalScreen(onClose = { showAIModal = false })
        }

        if (showSettingsModal) {
            SettingsModal(
                email = TokenManager.getEmail(context) ?: "user@example.com",
                onClose = { showSettingsModal = false },
                onLogout = { 
                    showSettingsModal = false
                    onLogout()
                }
            )
        }

        if (showCreateGroup) {
            PlaceholderModal(title = "Create Group", onClose = { showCreateGroup = false })
        }
    }
}

@Composable
fun CallsPlaceholder() {
    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Text("Call history will appear here", color = SubTextColor, textAlign = TextAlign.Center)
    }
}

@Composable
fun ReelsWebView(url: String) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                webViewClient = WebViewClient()
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadsImagesAutomatically = true
                settings.mediaPlaybackRequiresUserGesture = false
                setLayerType(WebView.LAYER_TYPE_HARDWARE, null) 
                loadUrl(url)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

data class AIMessage(val id: String, val text: String, val isUser: Boolean)

@Composable
fun AIModalScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var promptText by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf(AIMessage("0", "Hi there! I am ChatArena AI. How can I help you today?", false)) }
    val focusManager = LocalFocusManager.current
    
    var isAiThinking by remember { mutableStateOf(false) }
    var isTyping by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, messages.lastOrNull()?.text?.length) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    fun sendMessage() {
        val text = promptText.trim()
        if (text.isEmpty() || isAiThinking || isTyping) return 

        messages.add(AIMessage(Random.nextInt().toString(), text, true))
        promptText = "" 
        isAiThinking = true

        val token = TokenManager.getToken(context) ?: ""

        scope.launch {
            try {
                val response = RetrofitClient.apiService.aiChat(
                    token = "Bearer $token",
                    request = AIRequest(message = text)
                )

                isAiThinking = false

                if (response.isSuccessful && response.body()?.success == true) {
                    val reply = response.body()?.reply ?: "No response from AI."
                    
                    val newMsgId = Random.nextInt().toString()
                    messages.add(AIMessage(newMsgId, "", false))
                    
                    isTyping = true
                    for (char in reply) {
                        val currentMsg = messages.find { it.id == newMsgId }
                        if (currentMsg != null) {
                            val index = messages.indexOf(currentMsg)
                            messages[index] = currentMsg.copy(text = currentMsg.text + char)
                        }
                        delay(15L)
                    }
                    isTyping = false
                } else {
                    messages.add(AIMessage(Random.nextInt().toString(), "Error: ${response.body()?.error ?: "Unknown error"}", false))
                }
            } catch (e: Exception) {
                isAiThinking = false
                messages.add(AIMessage(Random.nextInt().toString(), "Network Error: ${e.message}", false))
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = BgColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing) 
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().background(CardColor).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("⬅", fontSize = 24.sp, color = PrimaryBlue, modifier = Modifier.clickable { onClose() })
                Spacer(modifier = Modifier.width(16.dp))
                Box(
                    modifier = Modifier.size(40.dp).background(
                        Brush.linearGradient(listOf(PrimaryBlue, Color(0xFF00C6FF))),
                        CircleShape
                    ),
                    contentAlignment = Alignment.Center
                ) { Text("✨", fontSize = 20.sp) }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("ChatArena AI", color = PrimaryBlue, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(if (isAiThinking) "Thinking..." else "Smart Assistant", color = SubTextColor, fontSize = 12.sp)
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
                    .pointerInput(Unit) { detectTapGestures(onTap = { offset: androidx.compose.ui.geometry.Offset -> focusManager.clearFocus() }) },
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(messages) { msg: AIMessage ->
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = if (msg.isUser) Alignment.CenterEnd else Alignment.CenterStart) {
                        Surface(
                            color = if (msg.isUser) PrimaryBlue else CardColor,
                            shape = RoundedCornerShape(12.dp),
                            shadowElevation = 2.dp,
                            modifier = Modifier.widthIn(max = 280.dp)
                        ) {
                            Text(
                                text = msg.text,
                                color = if (msg.isUser) Color.White else TextColor,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }

                if (isAiThinking) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                            Surface(color = CardColor, shape = RoundedCornerShape(12.dp), shadowElevation = 2.dp) {
                                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    TypingDotsAnimation()
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().background(BgColor).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    color = CardColor,
                    shadowElevation = 4.dp
                ) {
                    BasicTextField(
                        value = promptText,
                        onValueChange = { promptText = it },
                        modifier = Modifier.padding(16.dp),
                        decorationBox = { inner -> 
                            if (promptText.isEmpty()) Text("Ask ChatArena AI...", color = SubTextColor)
                            inner()
                        }
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Surface(
                    shape = CircleShape,
                    color = if (promptText.isNotBlank()) PrimaryBlue else SubTextColor.copy(alpha = 0.5f),
                    modifier = Modifier.size(48.dp).clickable(enabled = promptText.isNotBlank()) {
                        if (!isAiThinking && !isTyping) sendMessage()
                    }
                ) {
                    Box(contentAlignment = Alignment.Center) { Text("➤", color = Color.White) }
                }
            }
        }
    }
}

@Composable
fun TypingDotsAnimation() {
    var dotCount by remember { mutableStateOf(1) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(400)
            dotCount = if (dotCount == 3) 1 else dotCount + 1
        }
    }
    Text(text = ".".repeat(dotCount), color = TextColor, fontSize = 20.sp, fontWeight = FontWeight.Bold)
}

@Composable
fun SettingsModal(email: String, onClose: () -> Unit, onLogout: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        Surface(shape = RoundedCornerShape(16.dp), color = CardColor, shadowElevation = 8.dp) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Settings", color = TextColor, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Logged in as:\n$email", color = SubTextColor, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onLogout, colors = ButtonDefaults.buttonColors(containerColor = DangerRed), modifier = Modifier.fillMaxWidth()) {
                    Text("🚪 Log Out", color = Color.White)
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onClose, modifier = Modifier.align(Alignment.End)) {
                    Text("Close", color = PrimaryBlue)
                }
            }
        }
    }
}

@Composable
fun PlaceholderModal(title: String, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        Surface(shape = RoundedCornerShape(16.dp), color = CardColor, shadowElevation = 8.dp) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(title, color = TextColor, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Text("This feature is under construction.", color = SubTextColor, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(24.dp))
                TextButton(onClick = onClose, modifier = Modifier.align(Alignment.End)) {
                    Text("OK", color = PrimaryBlue)
                }
            }
        }
    }
}

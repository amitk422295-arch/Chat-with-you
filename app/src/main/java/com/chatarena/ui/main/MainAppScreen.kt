package com.chatarena.ui.main

import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.chatarena.ui.auth.*
import kotlinx.coroutines.launch
import kotlin.random.Random

@Composable
fun MainAppScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 3 })

    var showAIModal by remember { mutableStateOf(false) }
    var showSettingsModal by remember { mutableStateOf(false) }
    var showCreateGroup by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(BgColor).systemBarsPadding()) {
        
        // Asli App UI (Agar AI open nahi hai tabhi ye dikhega)
        if (!showAIModal) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Navbar
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
                    
                    // Tabs
                    TabRow(
                        selectedTabIndex = pagerState.currentPage,
                        containerColor = CardColor,
                        contentColor = PrimaryBlue,
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                                color = PrimaryBlue
                            )
                        }
                    ) {
                        listOf("CHATS", "REELS", "CALLS").forEachIndexed { index, title ->
                            Tab(
                                selected = pagerState.currentPage == index,
                                onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                                text = { Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                            )
                        }
                    }
                }

                // Panes Content
                HorizontalPager(
                    state = pagerState, 
                    modifier = Modifier.weight(1f),
                    beyondBoundsPageCount = 1 
                ) { page ->
                    when (page) {
                        0 -> ChatListScreen(chatList = emptyList())
                        1 -> ReelsWebView(url = "https://reel-center-2.onrender.com")
                        2 -> CallsPlaceholder()
                    }
                }
            }

            // AI FAB
            if (pagerState.currentPage != 1) {
                Box(modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp)) {
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

        // AI Chat Screen (Direct overlay, no dialog)
        if (showAIModal) {
            AIModalScreen(onClose = { showAIModal = false })
        }

        // Settings Modal
        if (showSettingsModal) {
            SettingsModal(
                email = "user@example.com",
                onClose = { showSettingsModal = false },
                onLogout = { 
                    showSettingsModal = false
                    Toast.makeText(context, "Logged out", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Create Group Modal
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
    var promptText by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf(AIMessage("0", "Hi there! I am ChatArena AI. How can I help you today?", false)) }
    val focusManager = LocalFocusManager.current

    // Yahan Dialog hata kar direct Surface use kiya hai taaki WhatsApp jaisa full screen lage
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = BgColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding() // Keyboard aane par input box upar aayega
        ) {
            // AI Top Bar (Header)
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
                    Text("Smart Assistant", color = SubTextColor, fontSize = 12.sp)
                }
            }

            // Chat Area
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = {
                            focusManager.clearFocus() // Bahar click karne par keyboard band
                        })
                    },
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { msg ->
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
            }

            // Input Area
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
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
                    color = PrimaryBlue,
                    modifier = Modifier.size(48.dp).clickable {
                        if (promptText.isNotBlank()) {
                            messages.add(AIMessage(Random.nextInt().toString(), promptText, true))
                            promptText = ""
                        }
                    }
                ) {
                    Box(contentAlignment = Alignment.Center) { Text("➤", color = Color.White) }
                }
            }
        }
    }
}

@Composable
fun SettingsModal(email: String, onClose: () -> Unit, onLogout: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = CardColor,
            shadowElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Settings", color = TextColor, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Logged in as:\n$email", color = SubTextColor, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onLogout,
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🚪 Log Out", color = Color.White)
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onClose,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Close", color = PrimaryBlue)
                }
            }
        }
    }
}

@Composable
fun PlaceholderModal(title: String, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = CardColor,
            shadowElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(title, color = TextColor, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Text("This feature is under construction.", color = SubTextColor, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(24.dp))
                TextButton(
                    onClick = onClose,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("OK", color = PrimaryBlue)
                }
            }
        }
    }
}

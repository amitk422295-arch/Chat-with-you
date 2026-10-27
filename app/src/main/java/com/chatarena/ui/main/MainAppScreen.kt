package com.chatarena.ui.main

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chatarena.ui.auth.* // Colors aur Neumorphic designs import kiye
import kotlinx.coroutines.launch

@Composable
fun MainAppScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 3 })

    var showAIModal by remember { mutableStateOf(false) }
    var showSettingsModal by remember { mutableStateOf(false) }
    var showCreateGroup by remember { mutableStateOf(false) }

    // Placeholder for activeChatTarget
    val activeChatTarget = null

    Box(modifier = Modifier.fillMaxSize().background(BgColor)) {
        if (activeChatTarget == null) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Navbar
                Column(modifier = Modifier.background(CardColor).padding(top = 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("ChatArena", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = PrimaryBlue)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (pagerState.currentPage == 0) {
                                Text("👥", fontSize = 20.sp, modifier = Modifier.clickable { showCreateGroup = true })
                            }
                            Text("⚙️", fontSize = 20.sp, modifier = Modifier.clickable { showSettingsModal = true })
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    
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
                HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                    when (page) {
                        0 -> ChatListPlaceholder()
                        1 -> ReelsPlaceholder()
                        2 -> CallsPlaceholder()
                    }
                }
            }

            // AI FAB
            if (pagerState.currentPage != 1) {
                Box(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp)
                ) {
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
        } else {
            // Active Chat Target View
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Chat Room Open (Placeholder)", color = TextColor)
            }
        }

        // AI Modal
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

        // Create Group Placeholder
        if (showCreateGroup) {
            PlaceholderModal(title = "Create Group", onClose = { showCreateGroup = false })
        }
    }
}

@Composable
fun ChatListPlaceholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Chat List Here", color = SubTextColor)
    }
}

@Composable
fun ReelsPlaceholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Reels View (WebView required)", color = SubTextColor)
    }
}

@Composable
fun CallsPlaceholder() {
    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Text("Call history will appear here", color = SubTextColor, textAlign = TextAlign.Center)
    }
}

@Composable
fun AIModalScreen(onClose: () -> Unit) {
    var promptText by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf("Hi there! I am ChatArena AI. How can I help you today?") }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = BgColor) {
            Column(modifier = Modifier.fillMaxSize()) {
                // AI Top Bar
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
                    modifier = Modifier.weight(1f).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages) { msg ->
                        Surface(
                            color = if (msg.startsWith("Hi")) CardColor else PrimaryBlue,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(0.8f)
                        ) {
                            Text(
                                text = msg,
                                color = if (msg.startsWith("Hi")) TextColor else Color.White,
                                modifier = Modifier.padding(12.dp)
                            )
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
                                messages.add(promptText)
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
}

@Composable
fun SettingsModal(email: String, onClose: () -> Unit, onLogout: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = CardColor,
        title = { Text("Settings", color = TextColor) },
        text = {
            Column {
                Text("Logged in as:\n$email", color = SubTextColor, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onLogout,
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🚪 Log Out", color = Color.White)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text("Close", color = PrimaryBlue) }
        }
    )
}

@Composable
fun PlaceholderModal(title: String, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = CardColor,
        title = { Text(title, color = TextColor) },
        text = { Text("This feature is under construction.", color = SubTextColor) },
        confirmButton = { TextButton(onClick = onClose) { Text("OK", color = PrimaryBlue) } }
    )
}

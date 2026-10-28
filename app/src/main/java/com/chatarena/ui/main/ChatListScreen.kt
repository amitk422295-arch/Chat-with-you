package com.chatarena.ui.main

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.chatarena.ChatItem
import com.chatarena.network.RetrofitClient
import com.chatarena.ui.auth.BgColor
import com.chatarena.ui.auth.CardColor
import com.chatarena.ui.auth.DangerRed
import com.chatarena.ui.auth.PrimaryBlue
import com.chatarena.ui.auth.SubTextColor
import com.chatarena.ui.auth.TextColor
import com.chatarena.utils.ContactHelper
import com.chatarena.utils.LocalContact
import com.chatarena.utils.TokenManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ChatListScreen(
    chatList: List<ChatItem> = emptyList(),
    onItemClick: (ChatItem) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val token = TokenManager.getToken(context) ?: ""

    var searchQuery by remember { mutableStateOf("") }
    var localContacts by remember { mutableStateOf<List<LocalContact>>(emptyList()) }
    var backendUser by remember { mutableStateOf<ChatItem?>(null) }
    var isBackendSearching by remember { mutableStateOf(false) }
    var hasContactPermission by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasContactPermission = isGranted
        if (isGranted) {
            scope.launch { localContacts = ContactHelper.getLocalContacts(context) }
        }
    }

    LaunchedEffect(Unit) {
        val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        if (permissionCheck) {
            hasContactPermission = true
            localContacts = ContactHelper.getLocalContacts(context)
        } else {
            permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    LaunchedEffect(searchQuery) {
        backendUser = null
        if (searchQuery.length == 10 && searchQuery.all { it.isDigit() }) {
            isBackendSearching = true
            delay(500) 
            try {
                val response = RetrofitClient.apiService.searchUser(
                    token = "Bearer $token",
                    mobile = searchQuery
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val user = response.body()?.user
                    if (user != null) {
                        backendUser = ChatItem(
                            type = "user",
                            id = user.userCode,
                            name = user.fullName,
                            avatarUrl = user.avatar,
                            subtitle = "On ChatArena"
                        )
                    }
                }
            } catch (e: Exception) {
                // Ignore
            } finally {
                isBackendSearching = false
            }
        }
    }

    val filteredContacts = if (searchQuery.isNotBlank()) {
        localContacts.filter { it.name.contains(searchQuery, ignoreCase = true) || it.mobile.contains(searchQuery) }
    } else emptyList()

    val isSearchMode = searchQuery.isNotEmpty()

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isSearchMode) {
                    Text(
                        "⬅", color = PrimaryBlue, fontSize = 24.sp,
                        modifier = Modifier.padding(end = 8.dp).clickable { searchQuery = "" }
                    )
                }
                Surface(
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp),
                    color = CardColor, shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = searchQuery, onValueChange = { searchQuery = it },
                            modifier = Modifier.weight(1f),
                            textStyle = TextStyle(color = TextColor, fontSize = 15.sp),
                            decorationBox = { inner ->
                                if (searchQuery.isEmpty()) Text("Search name or mobile number...", color = SubTextColor, fontSize = 15.sp)
                                inner()
                            }
                        )
                        if (searchQuery.isNotEmpty()) {
                            Text("✕", color = SubTextColor, modifier = Modifier.clickable { searchQuery = "" }.padding(start = 8.dp))
                        }
                    }
                }
            }
        }

        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 20.dp)) {
            if (isSearchMode) {
                if (isBackendSearching) {
                    item {
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
                            CircularProgressIndicator(color = PrimaryBlue, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    }
                } else if (backendUser != null) {
                    item {
                        Text("CHATARENA USER", color = PrimaryBlue, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
                        ChatListItemRow(item = backendUser!!, onClick = { onItemClick(it) })
                    }
                }

                if (filteredContacts.isNotEmpty()) {
                    item {
                        Text("LOCAL CONTACTS", color = SubTextColor, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
                    }
                    items(filteredContacts) { contact ->
                        val chatItem = ChatItem(
                            type = "user",
                            id = contact.mobile,
                            name = contact.name,
                            avatarUrl = null,
                            subtitle = "Hey there! I am using ChatArena."
                        )
                        ChatListItemRow(item = chatItem, onClick = { onItemClick(it) })
                    }
                }

                if (backendUser == null && filteredContacts.isEmpty() && !isBackendSearching) {
                    item {
                        Text("No user found.", color = SubTextColor, modifier = Modifier.padding(20.dp).fillMaxWidth(), textAlign = TextAlign.Center)
                    }
                }

            } else {
                if (chatList.isEmpty()) {
                    item {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 100.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(modifier = Modifier.size(80.dp).background(CardColor, CircleShape), contentAlignment = Alignment.Center) {
                                Text("💬", fontSize = 36.sp)
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("No chats yet", color = TextColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Start a new conversation to see it here.", color = SubTextColor, fontSize = 14.sp)
                        }
                    }
                } else {
                    items(chatList) { item -> ChatListItemRow(item, onItemClick) }
                }
            }
        }
    }
}

@Composable
fun ChatListItemRow(item: ChatItem, onClick: (ChatItem) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick(item) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(50.dp).background(BgColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (item.type == "group") {
                Text("👥", fontSize = 24.sp, color = PrimaryBlue)
            } else {
                Text(item.name.take(1).uppercase(), color = PrimaryBlue, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.name, color = TextColor, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
                if (item.type == "group") {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("GROUP", color = PrimaryBlue, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                }
            }
            Text(item.subtitle, color = SubTextColor, fontSize = 12.sp, maxLines = 1)
        }
        
        if (item.unreadCount > 0) {
            Box(modifier = Modifier.background(DangerRed, CircleShape).padding(horizontal = 6.dp, vertical = 2.dp)) {
                Text(if (item.unreadCount > 99) "99+" else item.unreadCount.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

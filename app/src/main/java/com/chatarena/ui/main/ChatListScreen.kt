package com.chatarena.ui.main

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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatarena.ChatItem
import com.chatarena.ui.auth.BgColor
import com.chatarena.ui.auth.CardColor
import com.chatarena.ui.auth.DangerRed
import com.chatarena.ui.auth.PrimaryBlue
import com.chatarena.ui.auth.SubTextColor
import com.chatarena.ui.auth.TextColor

@Composable
fun ChatListScreen(
    chatList: List<ChatItem> = emptyList(),
    onItemClick: (ChatItem) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var userNotFound by remember { mutableStateOf(false) }

    val isSearchMode = searchQuery.isNotEmpty()

    val filteredList = if (searchQuery.isNotBlank()) {
        chatList.filter { it.name.contains(searchQuery, ignoreCase = true) || it.subtitle.contains(searchQuery) }
    } else chatList

    Column(modifier = Modifier.fillMaxSize().imePadding()) { // imePadding se keyboard screen ko cover nahi karega
        // SEARCH BAR HEADER
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isSearchMode) {
                    Text(
                        "⬅", color = PrimaryBlue, fontSize = 24.sp,
                        modifier = Modifier.padding(end = 8.dp).clickable { 
                            searchQuery = ""
                            userNotFound = false
                        }
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

        // LIST AREA
        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 20.dp)) {
            if (isSearchMode) {
                if (searchQuery.isBlank()) {
                    item { Text("Type a name or number to search chats...", color = SubTextColor, modifier = Modifier.padding(20.dp).fillMaxWidth(), textAlign = TextAlign.Center) }
                } else if (filteredList.isNotEmpty()) {
                    item {
                        Text("MATCHING (${filteredList.size})", color = PrimaryBlue, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
                    }
                    items(filteredList) { item -> ChatListItemRow(item, onItemClick) }
                } else if (!isSearching && !userNotFound) {
                    item { Text("No local contact found.", color = SubTextColor, modifier = Modifier.padding(20.dp).fillMaxWidth(), textAlign = TextAlign.Center) }
                }
            } else {
                if (chatList.isEmpty()) {
                    // NATIVE EMPTY STATE (Jab koi chat na ho)
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = 100.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier.size(80.dp).background(CardColor, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
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
        // Native ripple effect ke liye clip(RoundedCornerShape) add kiya gaya hai
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
            Box(
                modifier = Modifier.background(DangerRed, CircleShape).padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(if (item.unreadCount > 99) "99+" else item.unreadCount.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

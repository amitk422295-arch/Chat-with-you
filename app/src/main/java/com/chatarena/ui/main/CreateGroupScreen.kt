package com.chatarena.ui.main

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatarena.ui.auth.*

data class Contact(
    val userCode: String,
    val name: String,
    val avatarUrl: String? = null,
    val status: String = ""
)

@Composable
fun CreateGroupScreen(
    currentUserCode: String,
    contactsList: List<Contact>,
    onClose: () -> Unit,
    onGroupCreated: (String, List<String>, Uri?) -> Unit
) {
    var groupName by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedMembers by remember { mutableStateOf(setOf<String>()) }
    var avatarUri by remember { mutableStateOf<Uri?>(null) }
    var creating by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }

    // Image Picker Setup
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) avatarUri = uri
    }

    // Filter Contacts (Exclude current user & apply search)
    val filteredContacts = remember(searchQuery, contactsList, currentUserCode) {
        contactsList.filter {
            it.userCode != currentUserCode && 
            (searchQuery.isBlank() || it.name.contains(searchQuery, true) || it.userCode.contains(searchQuery, true))
        }
    }

    fun toggleMember(userCode: String) {
        selectedMembers = if (selectedMembers.contains(userCode)) {
            selectedMembers - userCode
        } else {
            selectedMembers + userCode
        }
    }

    fun handleCreate() {
        errorMsg = ""
        if (groupName.isBlank()) {
            errorMsg = "Please enter a group name."
            return
        }
        if (selectedMembers.isEmpty()) {
            errorMsg = "Please select at least one member."
            return
        }
        if (selectedMembers.size > 100) {
            errorMsg = "Maximum 100 members allowed."
            return
        }

        creating = true
        // API Call would go here
        onGroupCreated(groupName, selectedMembers.toList(), avatarUri)
        creating = false
        onClose()
    }

    val isCreateEnabled = groupName.isNotBlank() && selectedMembers.isNotEmpty() && !creating

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
            .systemBarsPadding() // Status bar ke liye
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardColor)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back Button
            Surface(
                shape = CircleShape,
                color = BgColor,
                shadowElevation = 4.dp,
                modifier = Modifier.size(38.dp).clickable { onClose() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("⬅", color = PrimaryBlue, fontSize = 18.sp)
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Titles
            Column(modifier = Modifier.weight(1f)) {
                Text("New Group", color = PrimaryBlue, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(
                    text = if (selectedMembers.isEmpty()) "Select members" else "${selectedMembers.size} member${if (selectedMembers.size > 1) "s" else ""} selected",
                    color = SubTextColor, fontSize = 11.sp
                )
            }
            
            // Create Button
            Button(
                onClick = { handleCreate() },
                enabled = isCreateEnabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryBlue,
                    disabledContainerColor = Color(0xFFC3C9D1)
                ),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 9.dp)
            ) {
                Text(if (creating) "Creating..." else "Create", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        // Body
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 20.dp)
        ) {
            // Avatar & Name Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Avatar Picker
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(CardColor)
                            .clickable { imagePickerLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("📷", fontSize = 32.sp)
                        if (avatarUri != null) {
                            // Yahan Coil library se image load karenge, abhi ke liye red dot dikha rahe hain indication ke liye
                            Box(modifier = Modifier.size(20.dp).background(DangerRed, CircleShape).align(Alignment.TopEnd))
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    // Name Input
                    Column(modifier = Modifier.weight(1f)) {
                        BasicTextField(
                            value = groupName,
                            onValueChange = { if (it.length <= 50) groupName = it },
                            textStyle = TextStyle(color = TextColor, fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                            decorationBox = { inner ->
                                if (groupName.isEmpty()) Text("Group name...", color = SubTextColor, fontSize = 17.sp)
                                inner()
                            }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(PrimaryBlue))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("${groupName.length}/50 characters", color = SubTextColor, fontSize = 11.sp)
                    }
                }
            }

            // Error Message
            if (errorMsg.isNotEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).background(DangerRed.copy(alpha = 0.1f), RoundedCornerShape(10.dp)).padding(10.dp)
                    ) {
                        Text("⚠️ $errorMsg", color = DangerRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Search Box
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = BgColor,
                    shadowElevation = 4.dp
                ) {
                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("🔍", color = SubTextColor, fontSize = 16.sp, modifier = Modifier.padding(end = 8.dp))
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.weight(1f),
                            textStyle = TextStyle(color = TextColor, fontSize = 14.sp),
                            decorationBox = { inner ->
                                if (searchQuery.isEmpty()) Text("Search contacts...", color = SubTextColor, fontSize = 14.sp)
                                inner()
                            }
                        )
                    }
                }
            }

            // Contacts Header
            item {
                Text(
                    text = "CONTACTS ${if (filteredContacts.isNotEmpty()) "(${filteredContacts.size})" else ""}",
                    color = SubTextColor, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                )
            }

            // Contacts List
            if (filteredContacts.isEmpty()) {
                item {
                    Text("No contacts match your search.", color = SubTextColor, modifier = Modifier.fillMaxWidth().padding(40.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            } else {
                items(filteredContacts, key = { it.userCode }) { contact ->
                    val isSelected = selectedMembers.contains(contact.userCode)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 6.dp)
                            .background(if (isSelected) PrimaryBlue.copy(alpha = 0.08f) else CardColor, RoundedCornerShape(14.dp))
                            .clickable { toggleMember(contact.userCode) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // User Avatar
                        Box(modifier = Modifier.size(44.dp).background(BgColor, CircleShape), contentAlignment = Alignment.Center) {
                            Text(contact.name.take(1).uppercase(), color = PrimaryBlue, fontWeight = FontWeight.Bold)
                        }
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(contact.name, color = TextColor, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(contact.status.ifEmpty { contact.userCode }, color = SubTextColor, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        
                        // Checkbox Circle
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(if (isSelected) PrimaryBlue else Color.Transparent, CircleShape)
                                .border(2.dp, if (isSelected) PrimaryBlue else Color(0xFFC3C9D1), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) Text("✓", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

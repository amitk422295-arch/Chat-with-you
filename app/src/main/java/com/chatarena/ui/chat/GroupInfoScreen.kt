package com.chatarena.ui.chat

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.chatarena.ui.auth.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GroupInfoScreen(
    group: GroupData,
    currentUserCode: String,
    contactsList: List<Contact>,
    onClose: () -> Unit,
    onGroupUpdated: (GroupData) -> Unit
) {
    val context = LocalContext.current
    var currentGroup by remember { mutableStateOf(group) }
    
    var showAddMember by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    
    var editingName by remember { mutableStateOf(false) }
    var editedName by remember { mutableStateOf(group.name) }
    
    var showLeaveDialog by remember { mutableStateOf(false) }
    var showTransferModal by remember { mutableStateOf(false) }
    var selectedTransferCode by remember { mutableStateOf<String?>(null) }

    // Derived state (Safe toString conversion)
    val adminsList = remember(currentGroup) {
        if (currentGroup.admins.isNotEmpty()) currentGroup.admins.map { it.toString() } 
        else if (currentGroup.adminCode != null) listOf(currentGroup.adminCode.toString()) 
        else emptyList()
    }
    val isAdmin = adminsList.contains(currentUserCode)
    val isLastAdmin = isAdmin && adminsList.size == 1
    val members = currentGroup.members

    // Filter contacts explicitly
    val availableContacts = remember(searchQuery, contactsList, members) {
        val memberStrList = members.map { it.toString() }
        contactsList.filter { contact ->
            contact.userCode != currentUserCode &&
            !memberStrList.contains(contact.userCode) &&
            (searchQuery.isBlank() || contact.name.contains(searchQuery, true) || contact.userCode.contains(searchQuery, true))
        }
    }

    fun getMemberInfo(code: String): Contact {
        if (code == currentUserCode) return Contact(code, "You", null, "Online")
        return contactsList.find { it.userCode == code } ?: Contact(code, code, null, "Hey there!")
    }

    fun formatDate(ts: Long): String {
        return try {
            SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(ts))
        } catch (e: Exception) { "" }
    }

    Column(modifier = Modifier.fillMaxSize().background(BgColor).systemBarsPadding()) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = CircleShape, color = CardColor, shadowElevation = 4.dp, modifier = Modifier.size(38.dp).clickable { onClose() }) {
                Box(contentAlignment = Alignment.Center) { 
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = PrimaryBlue) 
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text("Group Info", color = PrimaryBlue, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            // Group Avatar & Name
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier.size(100.dp).background(CardColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Groups, contentDescription = "Group", tint = PrimaryBlue, modifier = Modifier.size(50.dp))
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    if (editingName) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            BasicTextField(
                                value = editedName,
                                onValueChange = { if (it.length <= 50) editedName = it },
                                textStyle = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = TextColor),
                                modifier = Modifier.weight(1f, fill = false).padding(bottom = 4.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(onClick = {
                                if (editedName.isNotBlank()) {
                                    currentGroup = currentGroup.copy(name = editedName)
                                    onGroupUpdated(currentGroup)
                                    editingName = false
                                }
                            }, colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)) {
                                Text("Save")
                            }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(currentGroup.name, color = TextColor, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            if (isAdmin) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    Icons.Default.Edit, contentDescription = "Edit", tint = PrimaryBlue,
                                    modifier = Modifier.size(20.dp).clickable { editingName = true }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "${members.size} members • ${adminsList.size} admins • Created ${formatDate(currentGroup.createdAt)}",
                        color = SubTextColor, fontSize = 12.sp
                    )
                }
                Divider(color = CardColor, thickness = 1.dp)
            }

            // Members Section Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("MEMBERS (${members.size})", color = SubTextColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    if (isAdmin) {
                        Button(
                            onClick = { showAddMember = !showAddMember },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Add Member Panel
            if (showAddMember) {
                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).background(CardColor, RoundedCornerShape(14.dp)).padding(12.dp)) {
                        Surface(shape = RoundedCornerShape(16.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
                                Icon(Icons.Default.Search, contentDescription = "Search", tint = SubTextColor, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    modifier = Modifier.padding(vertical = 12.dp).weight(1f),
                                    decorationBox = { inner ->
                                        if (searchQuery.isEmpty()) Text("Search contacts...", color = SubTextColor, fontSize = 13.sp)
                                        inner()
                                    }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Column(modifier = Modifier.heightIn(max = 200.dp)) {
                            if (availableContacts.isEmpty()) {
                                Text("No match found.", color = SubTextColor, fontSize = 12.sp, modifier = Modifier.padding(10.dp))
                            } else {
                                LazyColumn {
                                    items(availableContacts) { contact ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().clickable {
                                                currentGroup = currentGroup.copy(members = members + contact.userCode)
                                                onGroupUpdated(currentGroup)
                                                showAddMember = false
                                            }.padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(modifier = Modifier.size(36.dp).background(BgColor, CircleShape), contentAlignment = Alignment.Center) {
                                                Text(contact.name.take(1), fontWeight = FontWeight.Bold, color = PrimaryBlue)
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(contact.name, color = TextColor, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                            Icon(Icons.Default.Add, contentDescription = "Add", tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Members List
            items(members) { rawMemberCode ->
                val memberCode = rawMemberCode.toString()
                val info = getMemberInfo(memberCode)
                val isMemberAdmin = adminsList.contains(memberCode)
                
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp).background(CardColor, RoundedCornerShape(14.dp)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(42.dp).background(BgColor, CircleShape), contentAlignment = Alignment.Center) {
                        Text(info.name.take(1).uppercase(), color = PrimaryBlue, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(if (memberCode == currentUserCode) "${info.name} (You)" else info.name, color = TextColor, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(if (isMemberAdmin) "Admin" else info.status, color = SubTextColor, fontSize = 11.sp)
                    }

                    if (isMemberAdmin) {
                        Surface(color = PrimaryBlue.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp)) {
                            Text("ADMIN", color = PrimaryBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    // Action Buttons (Make Admin, Remove, etc)
                    if (isAdmin && memberCode != currentUserCode) {
                        if (!isMemberAdmin) {
                            Icon(
                                Icons.Default.Star, contentDescription = "Make Admin", tint = Color(0xFFFFB300), 
                                modifier = Modifier.size(22.dp).clickable { Toast.makeText(context, "Promoted", Toast.LENGTH_SHORT).show() }.padding(2.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                Icons.Default.Close, contentDescription = "Remove", tint = DangerRed, 
                                modifier = Modifier.size(22.dp).clickable { 
                                    currentGroup = currentGroup.copy(members = members - rawMemberCode)
                                    onGroupUpdated(currentGroup)
                                }.padding(2.dp)
                            )
                        } else if (adminsList.size > 1) {
                            Icon(
                                Icons.Default.RemoveCircleOutline, contentDescription = "Demote Admin", tint = DangerRed, 
                                modifier = Modifier.size(22.dp).clickable { Toast.makeText(context, "Demoted", Toast.LENGTH_SHORT).show() }.padding(2.dp)
                            )
                        }
                    }
                }
            }

            // Leave Group Button
            item {
                Column(modifier = Modifier.padding(18.dp)) {
                    Divider(color = CardColor, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(18.dp))
                    Button(
                        onClick = { 
                            if (isLastAdmin) showTransferModal = true else showLeaveDialog = true 
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CardColor),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Leave", tint = DangerRed)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isLastAdmin) "Transfer & Leave Group" else "Leave Group", color = DangerRed, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    // Leave Dialog
    if (showLeaveDialog) {
        Dialog(onDismissRequest = { showLeaveDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CardColor,
                shadowElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Leave Group?", color = TextColor, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Are you sure you want to leave this group? You will not be able to read new messages.", color = SubTextColor, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { showLeaveDialog = false }) { Text("Cancel", color = PrimaryBlue) }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = { showLeaveDialog = false; onClose() }) { Text("Leave", color = DangerRed) }
                    }
                }
            }
        }
    }

    // Transfer Admin Dialog (Bulletproof toString conversion)
    if (showTransferModal) {
        Dialog(onDismissRequest = { showTransferModal = false }) {
            Surface(shape = RoundedCornerShape(20.dp), color = CardColor) {
                Column(modifier = Modifier.padding(22.dp)) {
                    Text("Transfer Admin & Leave", color = PrimaryBlue, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("You are the last admin. Please choose the next admin before leaving.", color = SubTextColor, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                    
                    LazyColumn(modifier = Modifier.weight(1f, fill = false).heightIn(max = 250.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(members.filter { it.toString() != currentUserCode }) { rawCode ->
                            val code = rawCode.toString() // Force String conversion
                            val info = getMemberInfo(code)
                            Row(
                                modifier = Modifier.fillMaxWidth().background(if (selectedTransferCode == code) PrimaryBlue.copy(alpha = 0.1f) else BgColor, RoundedCornerShape(12.dp)).clickable { selectedTransferCode = code }.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.size(36.dp).background(CardColor, CircleShape), contentAlignment = Alignment.Center) {
                                    Text(info.name.take(1), fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(info.name, color = TextColor, modifier = Modifier.weight(1f))
                                if (selectedTransferCode == code) {
                                    Icon(Icons.Default.Check, contentDescription = "Selected", tint = PrimaryBlue)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { showTransferModal = false }, colors = ButtonDefaults.buttonColors(containerColor = BgColor), modifier = Modifier.weight(1f)) {
                            Text("Cancel", color = TextColor)
                        }
                        Button(onClick = { 
                            showTransferModal = false
                            onClose()
                        }, enabled = selectedTransferCode != null, modifier = Modifier.weight(1f)) {
                            Text("Confirm")
                        }
                    }
                }
            }
        }
    }
}

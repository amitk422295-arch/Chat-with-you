package com.chatarena

data class Contact(
    val userCode: String,
    val name: String,
    val avatarUrl: String? = null,
    val status: String = ""
)

data class GroupData(
    val groupId: String,
    val name: String,
    val avatarUrl: String? = null,
    val members: List<String> = emptyList(),
    val admins: List<String> = emptyList(),
    val adminCode: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class ChatItem(
    val type: String, // "user" or "group"
    val id: String,
    val name: String,
    val avatarUrl: String? = null,
    val subtitle: String,
    val unreadCount: Int = 0
)

package com.chatarena.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey
    val messageId: String, // Server ka ID ya Client ID
    
    val clientMessageId: String? = null,
    val chatId: String, // Jiske sath chat ho rahi hai (uska userCode)
    val senderCode: String,
    val receiverCode: String?,
    
    val encryptedText: String = "", // Jo server ko jata hai
    val decryptedText: String = "", // Jo screen par dikhta hai
    
    val messageType: String = "text",
    val mediaLocalPath: String? = null, // Photo/Video ka phone me save path
    val mediaMimeType: String? = null,
    
    val status: String = "pending",
    val createdAt: Long = System.currentTimeMillis(),
    val replyTo: String? = null,
    val isDeleted: Boolean = false
)

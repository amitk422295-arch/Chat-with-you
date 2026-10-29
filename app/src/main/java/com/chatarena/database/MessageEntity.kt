package com.chatarena.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    // 🔥 ADVANCE FIX: Indexes lagaye hain taaki hazaron messages hone par bhi app fast rahe
    indices = [
        Index(value = ["chatId"]), 
        Index(value = ["clientMessageId"]),
        Index(value = ["createdAt"])
    ]
)
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
    
    // 🔥 MAIN FIX: Boolean se Int kar diya, taaki Network model se match kare
    val isDeleted: Int = 0 
)

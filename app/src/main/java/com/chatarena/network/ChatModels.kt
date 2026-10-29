package com.chatarena.network

import com.google.gson.annotations.SerializedName

// 1. Message Status
enum class MessageStatus {
    @SerializedName("pending") PENDING,
    @SerializedName("failed") FAILED,
    @SerializedName("sent") SENT,
    @SerializedName("delivered") DELIVERED,
    @SerializedName("read") READ
}

// 2. Message Type
enum class MessageType {
    @SerializedName("text") TEXT,
    @SerializedName("image") IMAGE,
    @SerializedName("video") VIDEO
}

// 3. Message Model
data class Message(
    @SerializedName("messageId") val messageId: String? = null,
    @SerializedName("clientMessageId") val clientMessageId: String? = null,
    @SerializedName("text") val text: String = "",
    @SerializedName("messageType") val messageType: String = "text",
    @SerializedName("senderCode") val senderCode: String = "",
    @SerializedName("receiverCode") val receiverCode: String? = null,
    @SerializedName("senderName") val senderName: String? = null,
    @SerializedName("mine") var mine: Boolean = false, 
    @SerializedName("createdAt") val createdAt: Long = System.currentTimeMillis(),
    @SerializedName("status") val status: String = "pending",
    @SerializedName("replyTo") val replyTo: String? = null,
    @SerializedName("reactions") val reactions: Map<String, String>? = null,
    
    // Ye pehle se fix hai
    @SerializedName("isDeleted") val isDeleted: Int = 0 
)

// 4. WebSocket Event Wrapper
data class SocketEvent(
    @SerializedName("type") val type: String,
    @SerializedName("payload") val payload: Any? = null,
    @SerializedName("cbId") val cbId: Int? = null
)

// 5. Send Message Payload
data class SendMessagePayload(
    @SerializedName("targetCode") val targetCode: String, 
    @SerializedName("text") val text: String,
    @SerializedName("clientMessageId") val clientMessageId: String,
    @SerializedName("messageType") val messageType: String = "text",
    @SerializedName("replyTo") val replyTo: String? = null,
    @SerializedName("senderName") val senderName: String? = null,
    @SerializedName("senderAvatar") val senderAvatar: String? = null,
    @SerializedName("senderMobile") val senderMobile: String? = null
)

// 6. WebSocket Response Wrapper
data class SocketResponse(
    @SerializedName("type") val type: String,
    @SerializedName("payload") val payload: SocketPayload? = null
)

// 7. Socket Payload (🔥 Yahan FIX kiya gaya hai)
data class SocketPayload(
    @SerializedName("message") val message: Message? = null,
    @SerializedName("senderCode") val senderCode: String? = null,
    @SerializedName("receiverCode") val receiverCode: String? = null,
    @SerializedName("isTyping") val isTyping: Boolean? = null,
    
    // 🔥 YEH 2 NAYI LINES ADD KI HAIN (Ticks update karne ke liye zaroori hain) 🔥
    @SerializedName("messageId") val messageId: String? = null, 
    @SerializedName("status") val status: String? = null
)

package com.chatarena.network

import com.google.gson.annotations.SerializedName

// 1. Message Status (String ki jagah Enum use kiya, jo 100% Native practice hai)
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

// 3. Message Model (Database aur UI dono ke liye)
data class Message(
    @SerializedName("messageId") val messageId: String? = null,
    @SerializedName("clientMessageId") val clientMessageId: String? = null,
    @SerializedName("text") val text: String = "",
    @SerializedName("messageType") val messageType: String = "text",
    @SerializedName("senderCode") val senderCode: String = "",
    @SerializedName("receiverCode") val receiverCode: String? = null, // Ye line add kar di hai
    @SerializedName("senderName") val senderName: String? = null,
    @SerializedName("mine") val mine: Boolean = false,
    @SerializedName("createdAt") val createdAt: Long = System.currentTimeMillis(),
    @SerializedName("status") val status: String = "pending",
    @SerializedName("replyTo") val replyTo: String? = null,
    @SerializedName("reactions") val reactions: Map<String, String>? = null,
    @SerializedName("isDeleted") val isDeleted: Boolean = false
)

// 4. WebSocket Event Wrapper (Jo socket.js me tha)
data class SocketEvent(
    @SerializedName("type") val type: String,
    @SerializedName("payload") val payload: Any? = null,
    @SerializedName("cbId") val cbId: Int? = null
)

// 5. Send Message Payload (Jab hum message bhejenge)
data class SendMessagePayload(
    @SerializedName("receiverCode") val receiverCode: String,
    @SerializedName("text") val text: String,
    @SerializedName("clientMessageId") val clientMessageId: String,
    @SerializedName("messageType") val messageType: String = "text",
    @SerializedName("replyTo") val replyTo: String? = null
)

// 6. WebSocket Response Wrapper (Jab server message bhejega)
data class SocketResponse(
    @SerializedName("type") val type: String,
    @SerializedName("payload") val payload: SocketPayload? = null
)

data class SocketPayload(
    @SerializedName("message") val message: Message? = null,
    @SerializedName("senderCode") val senderCode: String? = null,
    @SerializedName("receiverCode") val receiverCode: String? = null,
    @SerializedName("isTyping") val isTyping: Boolean? = null
)

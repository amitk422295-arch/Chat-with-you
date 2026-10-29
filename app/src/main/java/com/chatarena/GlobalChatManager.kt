package com.chatarena

import android.content.Context
import com.chatarena.database.AppDatabase
import com.chatarena.database.MessageEntity
import com.chatarena.network.ChatWebSocketClient
import com.chatarena.utils.CryptoManager
import com.chatarena.utils.TokenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID

object GlobalChatManager {
    private var isInitialized = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true

        val currentUserCode = TokenManager.getUserCode(context) ?: return
        val token = TokenManager.getToken(context) ?: return
        val db = AppDatabase.getDatabase(context)
        val messageDao = db.messageDao()

        // 1. Socket Connect karo
        ChatWebSocketClient.connect(currentUserCode, token)

        // 2. GLOBAL LISTENER
        scope.launch {
            ChatWebSocketClient.socketEvents.collect { response ->
                
                // 🔥 PART 1: NAYA MESSAGE AAYE TOH SAVE KARO
                if (response.type == "new-message" && response.payload?.message != null) {
                    val msg = response.payload.message
                    val isMine = msg.senderCode == currentUserCode
                    val otherUserCode = if (isMine) msg.receiverCode ?: "" else msg.senderCode ?: ""

                    if (otherUserCode.isNotEmpty()) {
                        // Decryption Process
                        val senderPubKey = CryptoManager.getPublicKeyFromDisk(context, msg.senderCode ?: "")
                        var decryptedText = msg.text
                        
                        if (msg.messageType == "text" && senderPubKey != null && msg.text.startsWith("E2E:")) {
                            try {
                                decryptedText = CryptoManager.decryptText(context, msg.text, msg.senderCode ?: "", senderPubKey)
                            } catch (e: Exception) {
                                decryptedText = "🔒 Encrypted Message"
                            }
                        }

                        // Database Entity banao
                        val entity = MessageEntity(
                            messageId = msg.messageId ?: msg.clientMessageId ?: UUID.randomUUID().toString(),
                            clientMessageId = msg.clientMessageId,
                            chatId = otherUserCode,
                            senderCode = msg.senderCode ?: "",
                            receiverCode = msg.receiverCode,
                            encryptedText = msg.text,
                            decryptedText = decryptedText,
                            messageType = msg.messageType ?: "text",
                            status = msg.status ?: "sent",
                            createdAt = msg.createdAt ?: System.currentTimeMillis(),
                            replyTo = msg.replyTo,
                            isDeleted = msg.isDeleted ?: 0
                        )

                        // DB me save karo (UI auto-update hoga)
                        messageDao.insertMessage(entity)

                        // Server ko Delivery Receipt bhejo (Double Tick)
                        if (!isMine && msg.messageId != null) {
                            ChatWebSocketClient.sendMessageReceived(msg.messageId, msg.senderCode ?: "")
                        }
                    }
                }
                
                // 🔥 PART 2: TICK UPDATE (SENT -> DELIVERED -> READ)
                if (response.type == "message-status-update" && response.payload?.messageId != null) {
                    val msgId = response.payload.messageId
                    val newStatus = response.payload.status ?: "delivered"
                    
                    // Database mein status update karo
                    messageDao.updateMessageStatus(msgId, newStatus)
                }
            }
        }
    }
}

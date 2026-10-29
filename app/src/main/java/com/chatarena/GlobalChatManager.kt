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

    // 🔥 Is function ko App start hote hi (MainActivity ya AppRoot me) ek baar call karna hai
    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true

        val currentUserCode = TokenManager.getUserCode(context) ?: return
        val token = TokenManager.getToken(context) ?: return
        val db = AppDatabase.getDatabase(context)
        val messageDao = db.messageDao()

        // 1. Socket Connect karo
        ChatWebSocketClient.connect(currentUserCode, token)

        // 2. GLOBAL LISTENER: Har naya message yahan aayega, chahe user kisi bhi screen par ho
        scope.launch {
            ChatWebSocketClient.socketEvents.collect { response ->
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
                            isDeleted = msg.isDeleted ?: 0 // Ab yeh Int hai
                        )

                        // DB me save karo (UI automatic update ho jayega Flow ki wajah se)
                        messageDao.insertMessage(entity)

                        // Server ko Delivery Receipt bhejo (agar message mere liye aaya hai)
                        if (!isMine && msg.messageId != null) {
                            ChatWebSocketClient.sendMessageReceived(msg.messageId, msg.senderCode ?: "")
                        }
                    }
                }
            }
        }
    }
}

package com.chatarena

import android.content.Context
import android.util.Log
import com.chatarena.database.AppDatabase
import com.chatarena.database.MessageEntity
import com.chatarena.network.ChatWebSocketClient
import com.chatarena.utils.CryptoManager
import com.chatarena.utils.TokenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import kotlin.coroutines.resume

object GlobalChatManager {
    private const val TAG = "GlobalChatManager"
    private const val KEY_FETCH_TIMEOUT_MS = 5000L

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

                // ============================================
                // 🔥 PART 1: NAYA MESSAGE AAYE TOH SAVE KARO
                // ============================================
                if (response.type == "new-message" && response.payload?.message != null) {
                    val msg = response.payload.message
                    val isMine = msg.senderCode == currentUserCode
                    val otherUserCode = if (isMine) msg.receiverCode ?: "" else msg.senderCode ?: ""

                    if (otherUserCode.isNotEmpty()) {
                        // 🔥 FIX 1: Apna hi message echo aaye — decrypt SKIP karo
                        //           Kyunki jab bheja tha tabhi plaintext save ho chuka hai.
                        //           Sirf server details (server ID + timestamp) update karo.
                        if (isMine) {
                            if (msg.clientMessageId != null && msg.messageId != null) {
                                messageDao.updateMessageWithServerDetails(
                                    msg.clientMessageId,
                                    msg.messageId,
                                    msg.createdAt ?: System.currentTimeMillis()
                                )
                            }
                        } else {
                            // ====================================
                            // DOOSRE KA MESSAGE — decrypt + save
                            // ====================================
                            val senderCode = msg.senderCode ?: ""

                            // 🔥 FIX 2: Pehle cache check, na mile to server se fetch (5s timeout)
                            var senderPubKey = CryptoManager.getPublicKeyFromDisk(context, senderCode)
                            if (senderPubKey == null) {
                                senderPubKey = withTimeoutOrNull(KEY_FETCH_TIMEOUT_MS) {
                                    suspendCancellableCoroutine { cont ->
                                        ChatWebSocketClient.getPublicKey(senderCode) { pubKey ->
                                            if (cont.isActive) cont.resume(pubKey)
                                        }
                                    }
                                }
                                if (senderPubKey != null) {
                                    CryptoManager.savePublicKeyToDisk(context, senderCode, senderPubKey)
                                } else {
                                    Log.e(TAG, "Public key fetch failed/timed out for $senderCode")
                                }
                            }

                            // 🔥 FIX 3 + 4: messageType ke bajaye startsWith("E2E:") check
                            val encryptedText = msg.text ?: ""
                            var decryptedText = encryptedText

                            if (encryptedText.startsWith("E2E:")) {
                                decryptedText = if (senderPubKey != null) {
                                    try {
                                        CryptoManager.decryptText(
                                            context,
                                            encryptedText,
                                            senderCode,
                                            senderPubKey
                                        )
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Decrypt exception: ${e.message}")
                                        "🔒 Message Unavailable"
                                    }
                                } else {
                                    "🔒 Key Unavailable"
                                }
                            }

                            // Database Entity banao
                            val entity = MessageEntity(
                                messageId = msg.messageId ?: msg.clientMessageId ?: UUID.randomUUID().toString(),
                                clientMessageId = msg.clientMessageId,
                                chatId = otherUserCode,
                                senderCode = senderCode,
                                receiverCode = msg.receiverCode,
                                encryptedText = encryptedText,
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
                            if (msg.messageId != null) {
                                ChatWebSocketClient.sendMessageReceived(msg.messageId, senderCode)
                            }
                        }
                    }
                }

                // ============================================
                // 🔥 PART 2: TICK UPDATE (SENT -> DELIVERED -> READ)
                // ============================================
                if (response.type == "message-status-update" && response.payload?.messageId != null) {
                    val msgId = response.payload.messageId
                    val newStatus = response.payload.status ?: "delivered"
                    messageDao.updateMessageStatus(msgId, newStatus)
                }
            }
        }
    }
}

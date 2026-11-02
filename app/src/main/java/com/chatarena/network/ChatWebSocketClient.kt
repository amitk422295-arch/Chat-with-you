package com.chatarena.network

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

object ChatWebSocketClient {

    private const val BASE_WS_URL = "wss://chatarena-chat.onrender.com/ws"
    private const val TAG = "ChatSocket"
    private const val CALLBACK_TIMEOUT_MS = 30000L

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private var isConnected = false // 🔥 FIX: कनेक्शन स्टेटस ट्रैक करने के लिए

    private val _connectionState = MutableStateFlow(false)
    val connectionState: StateFlow<Boolean> = _connectionState

    private val _socketEvents = MutableSharedFlow<SocketResponse>(extraBufferCapacity = 500)
    val socketEvents: SharedFlow<SocketResponse> = _socketEvents

    private var userCode: String = ""
    private var token: String = ""
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isIntentionalDisconnect = false

    private val cbIdCounter = AtomicInteger(1)
    private val callbacks = ConcurrentHashMap<Int, CallbackData>()

    // 🔥 FIX: जब तक कनेक्शन नहीं जुड़ता, मैसेज इसमें सेव रहेंगे
    private data class PendingEvent(
        val json: String,
        val cbId: Int? = null,
        val onResult: ((JsonObject?) -> Unit)? = null
    )
    private val pendingEvents = ConcurrentLinkedQueue<PendingEvent>()

    private data class CallbackData(
        val onResult: (JsonObject?) -> Unit,
        val timeoutJob: Job
    )

    fun connect(uCode: String, tkn: String) {
        this.userCode = uCode
        this.token = tkn
        this.isIntentionalDisconnect = false

        if (webSocket != null) {
            Log.d(TAG, "Already connected or connecting.")
            return
        }

        val url = "$BASE_WS_URL?userCode=$userCode&token=$token"
        val request = Request.Builder().url(url).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket Connected!")
                isConnected = true
                _connectionState.value = true
                startHeartbeat()

                // 🔥 FIX: कनेक्शन जुड़ते ही पेंडिंग मैसेजेस भेज दो और टाइमर चालू करो
                while (pendingEvents.isNotEmpty()) {
                    val pending = pendingEvents.poll() ?: break
                    Log.d(TAG, "Flushing queued message...")
                    if (pending.cbId != null && pending.onResult != null) {
                        startCallbackTimeout(pending.cbId, pending.onResult)
                    }
                    webSocket.send(pending.json)
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val jsonObject = gson.fromJson(text, JsonObject::class.java)
                    val type = jsonObject.get("type")?.asString ?: return

                    if (type == "ping" || type == "pong") return

                    if (jsonObject.has("cbId")) {
                        val cbId = jsonObject.get("cbId").asInt
                        val payloadElement = jsonObject.get("payload")
                        val payload = if (payloadElement != null && payloadElement.isJsonObject) {
                            payloadElement.asJsonObject
                        } else {
                            jsonObject
                        }
                        finishCallback(cbId, payload)
                        return
                    }

                    if (type == "message-sent-callback") {
                        _socketEvents.tryEmit(SocketResponse(type, gson.fromJson(jsonObject.get("payload"), SocketPayload::class.java)))
                        return
                    }

                    val payload = jsonObject.getAsJsonObject("payload")
                    var socketPayload = gson.fromJson(payload, SocketPayload::class.java)

                    if (socketPayload.message == null && (payload.has("messageId") || payload.has("text") || payload.has("clientMessageId"))) {
                        val directMessage = gson.fromJson(payload, Message::class.java)
                        socketPayload = SocketPayload(
                            message = directMessage,
                            senderCode = socketPayload.senderCode ?: directMessage.senderCode,
                            receiverCode = socketPayload.receiverCode ?: directMessage.receiverCode,
                            isTyping = socketPayload.isTyping
                        )
                    }

                    _socketEvents.tryEmit(SocketResponse(type, socketPayload))

                } catch (e: Exception) {
                    Log.e(TAG, "Parse error: ${e.message}")
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Closing: $code / $reason")
                webSocket.close(1000, null)
                isConnected = false
                _connectionState.value = false
                this@ChatWebSocketClient.webSocket = null
                if (!isIntentionalDisconnect) scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.d(TAG, "Error: ${t.message}")
                isConnected = false
                _connectionState.value = false
                this@ChatWebSocketClient.webSocket = null
                heartbeatJob?.cancel()
                if (!isIntentionalDisconnect) {
                    scheduleReconnect()
                }
            }
        })
    }

    fun disconnect() {
        isIntentionalDisconnect = true
        heartbeatJob?.cancel()
        reconnectJob?.cancel()
        webSocket?.close(1000, "User logged out")
        webSocket = null
        isConnected = false
        _connectionState.value = false
        pendingEvents.clear() // 🔥 क्यू साफ करो

        callbacks.forEach { (_, data) -> data.timeoutJob.cancel() }
        callbacks.clear()
    }

    private fun finishCallback(cbId: Int, payload: JsonObject?) {
        val callbackData = callbacks.remove(cbId) ?: return
        callbackData.timeoutJob.cancel()
        try {
            callbackData.onResult(payload)
        } catch (e: Exception) {
            Log.e(TAG, "Callback execution failed: ${e.message}")
        }
    }

    // 🔥 मुख्य फंक्शन: यह चेक करेगा कि कनेक्शन है या नहीं
    private fun sendEvent(event: Any, cbId: Int? = null, onResult: ((JsonObject?) -> Unit)? = null) {
        val json = gson.toJson(event)
        if (isConnected && webSocket != null) {
            if (cbId != null && onResult != null) {
                startCallbackTimeout(cbId, onResult)
            }
            webSocket?.send(json)
        } else {
            Log.d(TAG, "Socket not connected. Queuing event: ${json.take(30)}...")
            pendingEvents.add(PendingEvent(json, cbId, onResult))
        }
    }

    fun registerPublicKey(publicKey: String, onResult: ((Boolean) -> Unit)? = null) {
        val cbId = cbIdCounter.getAndIncrement()
        val payload = JsonObject().apply { addProperty("publicKey", publicKey) }
        val event = mapOf("type" to "register-public-key", "payload" to payload, "cbId" to cbId)

        val callback: (JsonObject?) -> Unit = { json ->
            val success = json?.get("success")?.takeIf { !it.isJsonNull }?.asBoolean ?: false
            onResult?.invoke(success)
        }
        sendEvent(event, cbId, callback)
    }

    fun getMessages(targetCode: String, after: Long? = null, onResult: (JsonObject?) -> Unit) {
        val cbId = cbIdCounter.getAndIncrement()
        val payload = JsonObject().apply {
            addProperty("targetCode", targetCode)
            if (after != null) addProperty("after", after) else add("after", JsonNull.INSTANCE)
        }
        val event = mapOf("type" to "get-messages", "payload" to payload, "cbId" to cbId)
        sendEvent(event, cbId, onResult)
    }

    fun getRecentChats(onResult: (JsonObject?) -> Unit) {
        val cbId = cbIdCounter.getAndIncrement()
        val payload = JsonObject()
        val event = mapOf("type" to "get-recent-chats", "payload" to payload, "cbId" to cbId)
        sendEvent(event, cbId, onResult)
    }

    fun getPublicKey(targetCode: String, onResult: (String?) -> Unit) {
        val cbId = cbIdCounter.getAndIncrement()
        val payload = JsonObject().apply { addProperty("targetCode", targetCode) }
        val event = mapOf("type" to "get-public-key", "payload" to payload, "cbId" to cbId)

        val callback: (JsonObject?) -> Unit = { json ->
            var pubKey: String? = null
            if (json != null) {
                if (json.has("publicKey") && !json.get("publicKey").isJsonNull) {
                    pubKey = json.get("publicKey").asString
                } else if (json.has("public_key") && !json.get("public_key").isJsonNull) {
                    pubKey = json.get("public_key").asString
                }
            }
            onResult(pubKey)
        }
        sendEvent(event, cbId, callback)
    }

    fun sendMessage(payload: SendMessagePayload, onResult: (JsonObject?) -> Unit) {
        val cbId = cbIdCounter.getAndIncrement()
        val event = mapOf("type" to "send-message", "payload" to payload, "cbId" to cbId)
        sendEvent(event, cbId, onResult)
    }

    fun sendTyping(targetCode: String) {
        val event = mapOf("type" to "typing", "payload" to mapOf("targetCode" to targetCode))
        sendEvent(event)
    }

    fun sendStopTyping(targetCode: String) {
        val event = mapOf("type" to "stop-typing", "payload" to mapOf("targetCode" to targetCode))
        sendEvent(event)
    }

    fun markMessageRead(messageId: String, senderCode: String) {
        val event = mapOf("type" to "mark-message-read", "payload" to mapOf("messageId" to messageId, "senderCode" to senderCode))
        sendEvent(event)
    }

    fun sendMessageReceived(messageId: String, senderCode: String) {
        val event = mapOf("type" to "message-received", "payload" to mapOf("messageId" to messageId, "senderCode" to senderCode))
        sendEvent(event)
    }

    private fun startCallbackTimeout(cbId: Int, onResult: (JsonObject?) -> Unit) {
        val timeoutJob = scope.launch {
            delay(CALLBACK_TIMEOUT_MS)
            if (callbacks.containsKey(cbId)) {
                callbacks.remove(cbId)
                Log.w(TAG, "Callback $cbId timed out.")
                withContext(Dispatchers.Main) { onResult(null) }
            }
        }
        val callbackData = CallbackData(
            onResult = { payload -> scope.launch { withContext(Dispatchers.Main) { onResult(payload) } } },
            timeoutJob = timeoutJob
        )
        callbacks[cbId] = callbackData
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (true) {
                delay(30000)
                val ping = gson.toJson(mapOf("type" to "ping"))
                webSocket?.send(ping)
            }
        }
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            Log.d(TAG, "Reconnecting in 5 seconds...")
            delay(5000)
            if (!isIntentionalDisconnect && webSocket == null) {
                connect(userCode, token)
            }
        }
    }
}

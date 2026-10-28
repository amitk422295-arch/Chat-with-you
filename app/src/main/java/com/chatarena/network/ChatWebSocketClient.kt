package com.chatarena.network

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.*
import java.util.UUID
import java.util.concurrent.TimeUnit

object ChatWebSocketClient {

    // Tera Elixir Backend URL (socket.js se liya hai)
    private const val BASE_WS_URL = "wss://chatarena-chat.onrender.com/ws"
    private const val TAG = "ChatSocket"

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // WebSocket ke liye timeout nahi hona chahiye
        .build()

    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    
    // State Management (Native Tarika)
    private val _connectionState = MutableStateFlow(false)
    val connectionState: StateFlow<Boolean> = _connectionState

    // Events (Messages, Typing, etc.) receive karne ke liye Flow
    private val _socketEvents = MutableSharedFlow<SocketResponse>(extraBufferCapacity = 50)
    val socketEvents: SharedFlow<SocketResponse> = _socketEvents

    private var userCode: String = ""
    private var token: String = ""
    private val scope = CoroutineScope(Dispatchers.IO)
    private var isIntentionalDisconnect = false

    // 1. Connect Function (Jab app khule ya login ho)
    fun connect(userCode: String, token: String) {
        this.userCode = userCode
        this.token = token
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
                _connectionState.value = true
                startHeartbeat()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val response = gson.fromJson(text, SocketResponse::class.java)
                    if (response.type == "ping" || response.type == "pong") return
                    
                    // Event ko Flow me emit karo
                    _socketEvents.tryEmit(response)
                } catch (e: Exception) {
                    Log.e(TAG, "Parse error: ${e.message}")
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Closing: $code / $reason")
                webSocket.close(1000, null)
                _connectionState.value = false
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Error: ${t.message}")
                _connectionState.value = false
                if (!isIntentionalDisconnect) {
                    scheduleReconnect()
                }
            }
        })
    }

    // 2. Disconnect (Jab user logout kare)
    fun disconnect() {
        isIntentionalDisconnect = true
        heartbeatJob?.cancel()
        reconnectJob?.cancel()
        webSocket?.close(1000, "User logged out")
        webSocket = null
        _connectionState.value = false
    }

    // 3. Send Message
    fun sendMessage(payload: SendMessagePayload): Boolean {
        val event = SocketEvent(
            type = "send-message",
            payload = payload
        )
        val json = gson.toJson(event)
        return webSocket?.send(json) ?: false
    }

    // 4. Heartbeat (Har 30 second me ping bhejna)
    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (true) {
                delay(30000)
                val ping = gson.toJson(SocketEvent(type = "ping"))
                webSocket?.send(ping)
            }
        }
    }

    // 5. Reconnect Logic (Agar net chala jaye)
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

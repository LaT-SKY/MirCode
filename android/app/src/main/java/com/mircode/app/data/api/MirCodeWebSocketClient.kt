package com.mircode.app.data.api

import android.util.Log
import com.google.gson.Gson
import com.mircode.app.data.model.ConnectionProfile
import com.mircode.app.data.model.WSMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import okhttp3.*
import java.util.concurrent.TimeUnit

class MirCodeWebSocketClient {
    private val tag = "MirCodeWS"
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val _events = MutableSharedFlow<WSMessage>(extraBufferCapacity = 64)
    val events: SharedFlow<WSMessage> = _events.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.IO)

    fun connect(profile: ConnectionProfile) {
        disconnect()

        val request = Request.Builder()
            .url(profile.wsUrl)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(tag, "WebSocket connection opened to ${profile.wsUrl}")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val msg = gson.fromJson(text, WSMessage::class.java)
                    scope.launch {
                        _events.emit(msg)
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Failed to parse WS message: $text", e)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                Log.d(tag, "WebSocket closing: $reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(tag, "WebSocket failure: ${t.message}", t)
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
    }
}

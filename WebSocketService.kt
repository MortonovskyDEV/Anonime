package com.goth.messenger.data.websocket

import android.util.Log
import com.goth.messenger.data.models.WebSocketMessage
import com.tinder.scarlet.Scarlet
import com.tinder.scarlet.WebSocket
import com.tinder.scarlet.lifecycle.Lifecycle
import com.tinder.scarlet.messageadapter.gson.GsonMessageAdapter
import com.tinder.scarlet.streamadapter.rxjava2.RxJava2StreamAdapter
import com.tinder.scarlet.websocket.okhttp.newWebSocketFactory
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import okhttp3.OkHttpClient

class WebSocketService(
    private val baseUrl: String,
    private val token: String
) {
    private val scarlet: Scarlet
    private val webSocketApi: MessengerWebSocketApi
    
    interface MessengerWebSocketApi {
        @WebSocket.Event
        fun observeWebSocketEvent(): Flowable<WebSocket.Event>
        
        fun sendMessage(message: WebSocketMessage)
        
        @WebSocket.Receive
        fun observeMessages(): Flowable<WebSocketMessage>
    }
    
    init {
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
                chain.proceed(request)
            }
            .build()
        
        scarlet = Scarlet.Builder()
            .webSocketFactory(okHttpClient.newWebSocketFactory("$baseUrl/ws"))
            .addMessageAdapterFactory(GsonMessageAdapter.Factory())
            .addStreamAdapterFactory(RxJava2StreamAdapter.Factory())
            .lifecycle(Lifecycle.of())
            .build()
        
        webSocketApi = scarlet.create(MessengerWebSocketApi::class.java)
    }
    
    fun connect(): Flowable<WebSocket.Event> {
        return webSocketApi.observeWebSocketEvent()
            .doOnNext { event ->
                when (event) {
                    is WebSocket.Event.OnConnectionOpened<*> -> {
                        Log.d("WebSocket", "Connected")
                        authenticate()
                    }
                    is WebSocket.Event.OnConnectionFailed -> {
                        Log.e("WebSocket", "Connection failed", event.throwable)
                    }
                    is WebSocket.Event.OnConnectionClosing -> {
                        Log.d("WebSocket", "Connection closing")
                    }
                    is WebSocket.Event.OnConnectionClosed -> {
                        Log.d("WebSocket", "Connection closed")
                    }
                    else -> {}
                }
            }
    }
    
    fun observeMessages(): Flowable<WebSocketMessage> {
        return webSocketApi.observeMessages()
    }
    
    fun sendMessage(message: WebSocketMessage) {
        webSocketApi.sendMessage(message)
    }
    
    private fun authenticate() {
        sendMessage(WebSocketMessage(
            type = "authenticate",
            chatId = null,
            message = null
        ))
    }
    
    fun sendTextMessage(chatId: Long, content: String) {
        sendMessage(WebSocketMessage(
            type = "message",
            chatId = chatId,
            message = Message(
                id = 0,
                chatId = chatId,
                senderId = 0,
                senderName = "",
                senderAvatar = null,
                content = content,
                timestamp = Date()
            )
        ))
    }
    
    fun sendTypingEvent(chatId: Long, isTyping: Boolean) {
        sendMessage(WebSocketMessage(
            type = "typing",
            typing = TypingEvent(
                chatId = chatId,
                userId = 0, // Будет установлено в ViewModel
                isTyping = isTyping
            )
        ))
    }
}
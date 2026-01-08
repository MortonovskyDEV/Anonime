package com.goth.messenger.data.models

import com.google.gson.annotations.SerializedName
import java.util.Date

data class User(
    val id: Long,
    val username: String,
    val email: String?,
    val avatarUrl: String?,
    val status: String,
    val lastSeen: Date?,
    val publicKey: String?,
    val theme: String = "dark"
)

data class Chat(
    val id: Long,
    val type: String, // "private", "group", "channel"
    val name: String?,
    val avatarUrl: String?,
    val createdBy: Long,
    val lastMessage: Message?,
    val unreadCount: Int = 0,
    val updatedAt: Date,
    val participants: List<User> = emptyList()
)

data class Message(
    val id: Long,
    val chatId: Long,
    val senderId: Long,
    val senderName: String,
    val senderAvatar: String?,
    val content: String,
    val type: String = "text", // "text", "image", "file", "audio"
    val fileUrl: String? = null,
    val fileName: String? = null,
    val fileSize: Long? = null,
    val repliedTo: Message? = null,
    val reactions: Map<String, List<Long>> = emptyMap(),
    val readBy: List<Long> = emptyList(),
    val encrypted: Boolean = true,
    val iv: String? = null,
    val timestamp: Date,
    val edited: Boolean = false
)

data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: String? = null
)

data class LoginRequest(
    val username: String,
    val password: String
)

data class LoginResponse(
    val token: String,
    val user: User,
    val expiresAt: Date
)

data class WebSocketMessage(
    val type: String,
    val chatId: Long? = null,
    val message: Message? = null,
    val typing: TypingEvent? = null,
    val call: CallEvent? = null
)

data class TypingEvent(
    val chatId: Long,
    val userId: Long,
    val isTyping: Boolean
)

data class CallEvent(
    val callId: String,
    val chatId: Long,
    val initiatorId: Long,
    val type: String, // "audio", "video"
    val timestamp: Date
)
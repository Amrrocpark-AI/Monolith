package com.monolith

import java.util.UUID

enum class MessageType { TEXT, IMAGE, FILE, AUDIO, SYSTEM }
enum class MessageRole { USER, AI, SYSTEM }

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val type: MessageType = MessageType.TEXT,
    val content: String,
    // دعم قائمة صور بدلاً من صورة واحدة
    val attachmentUris: List<String>? = null, 
    val timestamp: Long = System.currentTimeMillis(),
    val isError: Boolean = false,
    val isStarred: Boolean = false,
    val tokensUsed: Int = 0,
    val colorHex: String = "#00FF00" 
)

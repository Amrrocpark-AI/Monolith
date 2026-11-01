package com.monolith

import java.util.UUID

// 1. تحديد نوع الرسالة
enum class MessageType {
    TEXT,
    IMAGE,
    FILE,
    AUDIO,
    SYSTEM
}

// 2. تحديد من أرسل الرسالة
enum class MessageRole {
    USER,
    AI,
    SYSTEM
}

// 3. القالب الاحترافي الشامل للرسالة
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val type: MessageType = MessageType.TEXT,
    val content: String,
    val attachmentUri: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isError: Boolean = false,
    val isStarred: Boolean = false,
    val tokensUsed: Int = 0,
    
    // الحقل الجديد: لحفظ لون كل رسالة على حدة (حتى لا يتغير عند إعادة فتح التطبيق)
    val colorHex: String = "#00FF00" 
)

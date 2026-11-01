package com.monolith

import java.util.UUID

// 1. تحديد نوع الرسالة (هل هي نص، صورة، ملف، إلخ)
enum class MessageType {
    TEXT,
    IMAGE,
    FILE,
    AUDIO,
    SYSTEM
}

// 2. تحديد من أرسل الرسالة (أنت، الذكاء الاصطناعي، أو نظام التطبيق)
enum class MessageRole {
    USER,
    AI,
    SYSTEM
}

// 3. القالب الاحترافي الشامل للرسالة
data class ChatMessage(
    // معرف فريد لكل رسالة (يولد تلقائياً)
    val id: String = UUID.randomUUID().toString(),
    
    // من أرسلها؟
    val role: MessageRole,
    
    // ما نوعها؟
    val type: MessageType = MessageType.TEXT,
    
    // النص أو المحتوى
    val content: String,
    
    // مسار المرفق (إذا كانت صورة أو ملف)
    val attachmentUri: String? = null,
    
    // وقت الإرسال
    val timestamp: Long = System.currentTimeMillis(),
    
    // حالة الرسالة (هل هي في طور الإرسال؟ هل فشلت؟)
    val isError: Boolean = false,
    
    // لتمييز الرسائل كـ "مفضلة"
    val isStarred: Boolean = false,
    
    // لعدّ التوكنز التي يستهلكها الذكاء الاصطناعي (لرسومنا البيانية لاحقاً)
    val tokensUsed: Int = 0
)

package com.monolith

// هذا الملف يمثل "الطلب" الذي نرسله للذكاء الاصطناعي
data class ChatRequest(
    val model: String,                  // اسم النموذج (مثل: llama3-8b-8192)
    val messages: List<Message>,        // قائمة الرسائل (تاريخ المحادثة)
    val temperature: Float = 0.7f,      // درجة الإبداعية (0.0 دقيق، 1.0 مبدع)
    val max_tokens: Int = 1024          // الحد الأقصى لطول الرد
)

data class Message(
    val role: String,                   // "user" للمستخدم، "assistant" للذكاء الاصطناعي، "system" للتعليمات
    val content: String                 // نص الرسالة
)

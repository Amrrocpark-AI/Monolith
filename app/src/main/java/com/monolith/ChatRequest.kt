package com.monolith

import com.google.gson.annotations.SerializedName

// هذا الملف يمثل "الطلب" المتقدم الذي نرسله للذكاء الاصطناعي
data class ChatRequest(
    // اسم النموذج (مثل: llama3-8b-8192)
    @SerializedName("model") val model: String,
    
    // قائمة الرسائل (تاريخ المحادثة)
    @SerializedName("messages") val messages: List<Message>,
    
    // درجة الإبداعية (0.0 دقيق جداً، 1.0 مبدع جداً)
    @SerializedName("temperature") val temperature: Float = 0.7f,
    
    // التحكم الدقيق في اختيار الكلمات (يفضل تركه 1.0 مع النماذج الصغيرة)
    @SerializedName("top_p") val topP: Float = 1.0f,
    
    // الحد الأقصى لطول الرد (بالرمز/التوكنز)
    @SerializedName("max_tokens") val maxTokens: Int = 2048,
    
    // عقوبة التكرار: تمنع النموذج من تكرار نفس الكلمة أو الجملة
    @SerializedName("frequency_penalty") val frequencyPenalty: Float = 0.5f,
    
    // عقوبة التواجد: تشجع النموذج على طرح مواضيع جديدة
    @SerializedName("presence_penalty") val presencePenalty: Float = 0.5f,
    
    // إيقاف فوري عند الوصول لكلمة معينة
    @SerializedName("stop") val stop: List<String>? = null,
    
    // دعم البث المباشر (مستقبلاً)
    @SerializedName("stream") val stream: Boolean = false
)

// نموذج الرسالة الواحدة
data class Message(
    // الدور: "system" للتعليمات، "user" للمستخدم، "assistant" للذكاء الاصطناعي
    @SerializedName("role") val role: String,
    
    // محتوى الرسالة (النص)
    @SerializedName("content") val content: String
)

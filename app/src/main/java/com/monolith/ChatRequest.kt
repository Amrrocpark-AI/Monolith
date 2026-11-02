package com.monolith

import com.google.gson.annotations.SerializedName

// ==========================================
// 1. نموذج الطلب الشامل (The Ultimate Request)
// ==========================================
data class ChatRequest(
    @SerializedName("model") val model: String,
    @SerializedName("messages") val messages: List<Message>,
    
    // التحكم في الإبداعية
    @SerializedName("temperature") val temperature: Float = 0.7f,
    @SerializedName("top_p") val topP: Float = 1.0f,
    
    // طول الرد
    @SerializedName("max_tokens") val maxTokens: Int = 2048,
    
    // منع التكرار
    @SerializedName("frequency_penalty") val frequencyPenalty: Float = 0.5f,
    @SerializedName("presence_penalty") val presencePenalty: Float = 0.5f,
    
    // إيقاف فوري عند كلمات معينة
    @SerializedName("stop") val stop: List<String>? = null,
    
    // البث المباشر
    @SerializedName("stream") val stream: Boolean = false,
    
    // (جديد) إجبار النموذج على الرد بصيغة JSON
    @SerializedName("response_format") val responseFormat: ResponseFormat? = null,
    
    // (جديد) الأدوات التي يمكن للنموذج استخدامها (Function Calling)
    @SerializedName("tools") val tools: List<Tool>? = null,
    
    // (جديد) استراتيجية التعامل مع المحادثات الطويلة
    @SerializedName("context_strategy") val contextStrategy: String? = null // "truncate" أو "summarize"
)

// ==========================================
// 2. نموذج الرسالة (The Ultimate Message)
// ==========================================
data class Message(
    @SerializedName("role") val role: String,
    
    // (جديد) المحتوى أصبح (Any) ليقبل نصاً عادياً أو قائمة من النصوص والصور
    @SerializedName("content") val content: Any 
)

// ==========================================
// 3. نموذج الصور والنصوص (Multimodal Support)
// ==========================================
data class ContentPart(
    @SerializedName("type") val type: String, // "text" أو "image_url"
    @SerializedName("text") val text: String? = null,
    @SerializedName("image_url") val imageUrl: ImageUrl? = null
)

data class ImageUrl(
    @SerializedName("url") val url: String // رابط الصورة أو Base64
)

// ==========================================
// 4. إجبار النموذج على الرد بـ JSON
// ==========================================
data class ResponseFormat(
    @SerializedName("type") val type: String = "json_object"
)

// ==========================================
// 5. نظام الأدوات (Function Calling)
// ==========================================
data class Tool(
    @SerializedName("type") val type: String = "function",
    @SerializedName("function") val function: FunctionDefinition
)

data class FunctionDefinition(
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String,
    @SerializedName("parameters") val parameters: ToolParameters
)

data class ToolParameters(
    @SerializedName("type") val type: String = "object",
    @SerializedName("properties") val properties: Map<String, Any>
)

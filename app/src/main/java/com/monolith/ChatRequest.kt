package com.monolith

import com.google.gson.annotations.SerializedName

// ==========================================
// 1. نموذج الطلب الشامل (The Ultimate Request)
// ==========================================
data class ChatRequest(
    @SerializedName("model") val model: String,
    @SerializedName("messages") val messages: List<Message>,
    @SerializedName("temperature") val temperature: Float = 0.7f,
    @SerializedName("top_p") val topP: Float = 1.0f,
    @SerializedName("max_tokens") val maxTokens: Int = 2048,
    @SerializedName("frequency_penalty") val frequencyPenalty: Float = 0.5f,
    @SerializedName("presence_penalty") val presencePenalty: Float = 0.5f,
    @SerializedName("stop") val stop: List<String>? = null,
    @SerializedName("stream") val stream: Boolean = false,
    @SerializedName("response_format") val responseFormat: ResponseFormat? = null,
    @SerializedName("tools") val tools: List<Tool>? = null,
    @SerializedName("context_strategy") val contextStrategy: String? = null
)

// ==========================================
// 2. نموذج الرسالة (The Ultimate Message)
// ==========================================
data class Message(
    @SerializedName("role") val role: String,
    @SerializedName("content") val content: Any 
)

// ==========================================
// 3. نموذج الصور والنصوص (Multimodal Support)
// ==========================================
data class ContentPart(
    @SerializedName("type") val type: String,
    @SerializedName("text") val text: String? = null,
    @SerializedName("image_url") val imageUrl: ImageUrl? = null
)

data class ImageUrl(
    @SerializedName("url") val url: String
)

// ==========================================
// 4. إجبار النموذج على الرد بـ JSON
// ==========================================
data class ResponseFormat(
    @SerializedName("type") val type: String = "json_object"
)

// ==========================================
// 5. نظام الأدوات (Function Calling) - أدوات Monolith الخاصة
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

// ==========================================
// 6. تعريف أدوات Monolith (هنا نضع "العقد اللونية" وغيرها)
// ==========================================
object MonolithTools {
    
    // أداة 1: ربط الرسائل بألوان (الخيوط الذكية)
    val colorNodeTool = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "link_messages_with_color",
            description = "تربط هذه الأداة بين رسالتين أو أكثر باستخدام خط ملون (عقدة لونية) لتنظيم الأفكار أو إظهار العلاقة بينهما.",
            parameters = ToolParameters(
                type = "object",
                properties = mapOf(
                    "source_message_id" to mapOf("type" to "string", "description" to "معرف الرسالة الأولى"),
                    "target_message_id" to mapOf("type" to "string", "description" to "معرف الرسالة الثانية"),
                    "color_hex" to mapOf("type" to "string", "description" to "كود اللون بصيغة HEX، مثل #FF0000"),
                    "label" to mapOf("type" to "string", "description" to "وصف العقدة اللونية (مثلاً: فكرة مرتبطة)"),
                    "line_style" to mapOf("type" to "string", "enum" to listOf("solid", "dashed", "dotted"))
                ),
                // الحقول المطلوبة
                // يمكن إضافتها حسب الحاجة
            )
        )
    )
}

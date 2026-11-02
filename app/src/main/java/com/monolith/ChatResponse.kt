package com.monolith

import com.google.gson.annotations.SerializedName

// ==========================================
// 1. النموذج الرئيسي للاستجابة (The Ultimate Response)
// ==========================================
data class ChatResponse(
    @SerializedName("id") val id: String,
    @SerializedName("object") val objectType: String? = null,
    @SerializedName("created") val created: Long? = null,
    @SerializedName("model") val model: String? = null,
    
    // قائمة الردود (عادة يكون هناك رد واحد)
    @SerializedName("choices") val choices: List<Choice>,
    
    // (جديد) معلومات استهلاك التوكنز (مفيد للرسوم البيانية لاحقاً)
    @SerializedName("usage") val usage: Usage? = null
)

// ==========================================
// 2. الرد الفردي (The Choice)
// ==========================================
data class Choice(
    @SerializedName("index") val index: Int,
    @SerializedName("message") val message: ResponseMessage,
    
    // سبب التوقف: "stop" (انتهى طبيعياً)، "length" (وصل للحد الأقصى)، "tool_calls" (يريد استخدام أداة)
    @SerializedName("finish_reason") val finishReason: String? = null
)

// ==========================================
// 3. رسالة الاستجابة (Response Message)
// ==========================================
data class ResponseMessage(
    @SerializedName("role") val role: String,
    
    // النص العادي (قد يكون فارغاً إذا كان النموذج يريد استخدام أداة فقط)
    @SerializedName("content") val content: String? = null,
    
    // (جديد) إذا قرر النموذج استخدام أداة، سيضع طلبه هنا
    @SerializedName("tool_calls") val toolCalls: List<ToolCall>? = null
)

// ==========================================
// 4. طلب استخدام الأداة (Tool Call)
// ==========================================
data class ToolCall(
    @SerializedName("id") val id: String,
    @SerializedName("type") val type: String, // "function"
    @SerializedName("function") val function: FunctionCall
)

data class FunctionCall(
    @SerializedName("name") val name: String, // اسم الأداة (مثل: link_messages_with_color)
    @SerializedName("arguments") val arguments: String // الوسائط بصيغة JSON (مثل: {"color_hex": "#FF0000"})
)

// ==========================================
// 5. إحصائيات الاستهلاك (Usage)
// ==========================================
data class Usage(
    @SerializedName("prompt_tokens") val promptTokens: Int,
    @SerializedName("completion_tokens") val completionTokens: Int,
    @SerializedName("total_tokens") val totalTokens: Int
)

package com.monolith

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ModelManager(private val context: Context) {

    private var llamaModel: LlamaModel? = null

    fun loadModel(modelPath: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        try {
            val file = File(modelPath)
            if (!file.exists()) {
                onError("الملف غير موجود: $modelPath")
                return
            }
            
            // تحميل النموذج (يجب أن يكون الملف بصيغة .gguf)
            llamaModel = LlamaModel(
                modelPath = file.absolutePath,
                context = context,
                nThreads = 4,
                nCtx = 2048
            )
            onSuccess()
        } catch (e: Exception) {
            onError("فشل تحميل النموذج: ${e.message}")
        }
    }

    suspend fun generateResponse(prompt: String): String {
        return withContext(Dispatchers.IO) {
            try {
                if (llamaModel == null) {
                    return@withContext "النموذج غير محمل. يرجى تحميل نموذج أولاً."
                }
                // توليد الرد
                val response = llamaModel?.generate(prompt, maxTokens = 512)
                response ?: "فشل التوليد."
            } catch (e: Exception) {
                "فشل التوليد: ${e.message}"
            }
        }
    }

    fun unloadModel() {
        llamaModel?.close()
        llamaModel = null
    }
}

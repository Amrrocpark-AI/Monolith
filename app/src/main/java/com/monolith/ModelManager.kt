package com.monolith

import android.content.Context
import com.arm.aichat.InferenceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ModelManager(private val context: Context) {

    private var inferenceEngine: InferenceEngine? = null

    fun loadModel(modelPath: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        try {
            val file = File(modelPath)
            if (!file.exists()) {
                onError("الملف غير موجود: $modelPath")
                return
            }
            
            inferenceEngine = InferenceEngine(context, file.absolutePath)
            onSuccess()
        } catch (e: Exception) {
            onError("فشل تحميل النموذج: ${e.message}")
        }
    }

    suspend fun generateResponse(prompt: String): String {
        return withContext(Dispatchers.IO) {
            try {
                if (inferenceEngine == null) {
                    return@withContext "النموذج غير محمل."
                }
                inferenceEngine?.generate(prompt) ?: "فشل التوليد."
            } catch (e: Exception) {
                "فشل التوليد: ${e.message}"
            }
        }
    }

    fun unloadModel() {
        inferenceEngine?.close()
        inferenceEngine = null
    }
}

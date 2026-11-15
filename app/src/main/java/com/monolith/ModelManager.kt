package com.monolith

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ModelManager(private val context: Context) {

    init {
        System.loadLibrary("monolith")
    }

    private external fun nativeLoadModel(modelPath: String): String
    private external fun nativeGenerate(prompt: String, agentId: Int): String
    private external fun nativeUnloadModel()
    private external fun nativeCreateSubAgent(): Int

    fun loadModel(modelPath: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        try {
            val file = File(modelPath)
            if (!file.exists()) {
                onError("الملف غير موجود: $modelPath")
                return
            }
            
            val result = nativeLoadModel(file.absolutePath)
            
            if (result.contains("بنجاح")) {
                onSuccess()
            } else {
                onError(result)
            }
        } catch (e: Exception) {
            onError("فشل تحميل النموذج: ${e.message}")
        }
    }

    suspend fun generateResponse(prompt: String, agentId: Int = 0): String {
        return withContext(Dispatchers.IO) {
            try {
                nativeGenerate(prompt, agentId)
            } catch (e: Exception) {
                "فشل التوليد: ${e.message}"
            }
        }
    }

    fun createSubAgent(): Int {
        return try {
            nativeCreateSubAgent()
        } catch (e: Exception) {
            -1
        }
    }

    fun unloadModel() {
        try {
            nativeUnloadModel()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

package com.monolith

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ModelManager(private val context: Context) {

    // تحميل المكتبة الأصلية C++
    init {
        System.loadLibrary("monolith")
    }

    // ربط دوال C++ التي تحمل نفس الأسماء
    private external fun nativeLoadModel(modelPath: String): String
    private external fun nativeGenerate(prompt: String): String
    private external fun nativeUnloadModel()

    fun loadModel(modelPath: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        try {
            val file = File(modelPath)
            if (!file.exists()) {
                onError("الملف غير موجود: $modelPath")
                return
            }
            
            // استدعاء دالة C++ لتحميل النموذج
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

    suspend fun generateResponse(prompt: String): String {
        return withContext(Dispatchers.IO) {
            try {
                // استدعاء دالة C++ لتوليد النص
                nativeGenerate(prompt)
            } catch (e: Exception) {
                "فشل التوليد: ${e.message}"
            }
        }
    }

    fun unloadModel() {
        try {
            // استدعاء دالة C++ لتحرير الذاكرة
            nativeUnloadModel()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

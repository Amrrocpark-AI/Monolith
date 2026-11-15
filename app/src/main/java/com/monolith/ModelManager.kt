package com.monolith

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

class ModelManager(private val context: Context) {

    init {
        System.loadLibrary("monolith")
    }

    // ==========================================
    // دوال النموذج النصي (كما هي)
    // ==========================================
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

    // ==========================================
    // دوال الرؤية الحاسوبية (VLM) - جديدة
    // ==========================================

    /**
     * تحميل محرك الرؤية (نموذج GGUF + ملف mmproj)
     * يعيد true إذا نجح التحميل
     */
    fun loadVisionModel(visionModelPath: String, mmprojPath: String, onResult: (Boolean) -> Unit) {
        try {
            val modelFile = File(visionModelPath)
            val mmprojFile = File(mmprojPath)
            
            if (!modelFile.exists()) {
                onResult(false)
                return
            }
            if (!mmprojFile.exists()) {
                onResult(false)
                return
            }
            
            val success = VisionProcessor.nativeInitVision(modelFile.absolutePath, mmprojFile.absolutePath)
            onResult(success)
        } catch (e: Exception) {
            e.printStackTrace()
            onResult(false)
        }
    }

    /**
     * تحليل إطار من الشاشة باستخدام نموذج الرؤية
     * @param buffer: ByteBuffer يحتوي على بيانات RGBA
     * @param width: عرض الإطار
     * @param height: ارتفاع الإطار
     * @param prompt: السؤال المطروح على الصورة (مثلاً: "ماذا ترى على الشاشة؟")
     * @return: الوصف النصي من النموذج
     */
    suspend fun analyzeScreenFrame(buffer: ByteBuffer, width: Int, height: Int, prompt: String): String {
        return withContext(Dispatchers.IO) {
            try {
                VisionProcessor.nativeAnalyzeFrame(buffer, width, height, prompt)
            } catch (e: Exception) {
                "{\"error\":\"${e.message}\"}"
            }
        }
    }

    /**
     * تحرير ذاكرة محرك الرؤية
     */
    fun unloadVisionModel() {
        try {
            VisionProcessor.nativeFreeVision()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

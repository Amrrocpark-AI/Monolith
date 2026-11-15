package com.monolith

import java.nio.ByteBuffer

object VisionProcessor {
    init {
        System.loadLibrary("monolith")
    }

    // 1. كاشف التغيرات (الرؤية المحيطية) - مستخدم في ScreenCaptureService
    external fun processFrame(buffer: ByteBuffer, width: Int, height: Int): String

    // 2. تهيئة محرك الرؤية العميق (VLM) - نموذج + mmproj
    external fun nativeInitVision(textModelPath: String, mmprojPath: String): Boolean

    // 3. تحليل الإطار (الرؤية المركزية) وإرجاع وصف نصي
    external fun nativeAnalyzeFrame(buffer: ByteBuffer, width: Int, height: Int, prompt: String): String

    // 4. تحرير محرك الرؤية
    external fun nativeFreeVision()
}

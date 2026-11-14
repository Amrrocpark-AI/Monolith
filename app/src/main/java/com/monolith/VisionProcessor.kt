package com.monolith

import java.nio.ByteBuffer

object VisionProcessor {
    init {
        System.loadLibrary("monolith")
    }

    // استدعاء دالة C++ لمعالجة الإطار وإرجاع التغييرات
    external fun processFrame(buffer: ByteBuffer, width: Int, height: Int): String
}

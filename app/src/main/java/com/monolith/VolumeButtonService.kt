package com.monolith

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

class VolumeButtonService : AccessibilityService() {

    private var handler: Handler? = null
    private var isLongPress = false

    private val longPressRunnable = Runnable {
        isLongPress = true
        triggerScreenshot()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        handler = Handler(Looper.getMainLooper())
        Toast.makeText(this, "مونوليث: زر الصوت جاهز للسكرين شوت", Toast.LENGTH_SHORT).show()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        // نتحقق من زر خفض الصوت
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    // بدء مؤقت الضغط المطول (500 مللي ثانية)
                    isLongPress = false
                    handler?.postDelayed(longPressRunnable, 500)
                    return true // نستهلك الحدث عشان الصوت ميطاطاش
                }
                KeyEvent.ACTION_UP -> {
                    // لو المستخدم شال صباعه قبل الـ 500 مللي، نلغي العملية
                    handler?.removeCallbacks(longPressRunnable)
                    return isLongPress // لو كانت ضغطة مطولة، نستهلك الحدث
                }
            }
        }
        return super.onKeyEvent(event)
    }

    private fun triggerScreenshot() {
        // إرسال أمر لخدمة تصوير الشاشة
        try {
            val intent = Intent(this, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_TAKE_SCREENSHOT
            }
            startService(intent)
            Toast.makeText(this, "جاري التقاط الشاشة...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // لا نحتاج لمراقبة الأحداث النصية، فقط الأزرار
    }

    override fun onInterrupt() {
        // يتم استدعاؤها لو النظام أوقف الخدمة
    }

    override fun onDestroy() {
        super.onDestroy()
        handler?.removeCallbacks(longPressRunnable)
    }
}

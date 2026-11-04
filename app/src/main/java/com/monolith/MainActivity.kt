package com.monolith

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView

class MainActivity : AppCompatActivity() {

    private var lastClickTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // ربط العناصر بالواجهة
        val btnGalleryTop = findViewById<MaterialCardView>(R.id.btnGalleryTop)
        val btnAIChat = findViewById<MaterialCardView>(R.id.btnAIChat)
        val btnImageStudio = findViewById<View>(R.id.btnImageStudio)
        val btnTextEditor = findViewById<View>(R.id.btnTextEditor)
        val btnSettings = findViewById<View>(R.id.btnSettings)

        // إعداد المستمعين للنقرات العادية
        btnGalleryTop.setOnClickListener { navigateTo(FileManagerActivity::class.java) }
        btnAIChat.setOnClickListener { navigateTo(ChatActivity::class.java) }
        btnImageStudio.setOnClickListener { navigateTo(ImageEditorActivity::class.java) }
        btnTextEditor.setOnClickListener { 
            Toast.makeText(this, "محرر النصوص قيد التطوير...", Toast.LENGTH_SHORT).show()
        }
        btnSettings.setOnClickListener { navigateTo(SettingsActivity::class.java) }

        // *** إضافة جديدة: تشغيل الفقاعة العائمة بالضغط المطول على زر الدردشة ***
        btnAIChat.setOnLongClickListener {
            Toast.makeText(this, "جاري تشغيل الفقاعة العائمة...", Toast.LENGTH_SHORT).show()
            startFloatingBubble()
            true
        }
    }

    // دالة الانتقال الآمن بين الشاشات
    private fun navigateTo(targetActivity: Class<*>) {
        val currentTime = SystemClock.elapsedRealtime()
        if (currentTime - lastClickTime < 1500) return
        lastClickTime = currentTime

        window.decorView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

        startActivity(Intent(this, targetActivity))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    // ==========================================
    // كود تشغيل الفقاعة العائمة (الوحدة الأولى)
    // ==========================================
    private fun startFloatingBubble() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                // طلب الصلاحية
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivityForResult(intent, 1001)
                Toast.makeText(this, "يرجى السماح للتطبيق بالرسم فوق التطبيقات الأخرى", Toast.LENGTH_LONG).show()
            } else {
                // تشغيل الخدمة
                val serviceIntent = Intent(this, FloatingService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
                Toast.makeText(this, "تم تشغيل الفقاعة العائمة", Toast.LENGTH_SHORT).show()
            }
        } else {
            // لأجهزة أندرويد القديمة
            val serviceIntent = Intent(this, FloatingService::class.java)
            startService(serviceIntent)
            Toast.makeText(this, "تم تشغيل الفقاعة العائمة", Toast.LENGTH_SHORT).show()
        }
    }

    // استقبال نتيجة الصلاحية
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                startFloatingBubble() // إعادة المحاولة بعد أخذ الصلاحية
            } else {
                Toast.makeText(this, "لم يتم منح الصلاحية، الفقاعة لن تعمل", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

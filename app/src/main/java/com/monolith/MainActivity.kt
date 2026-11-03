package com.monolith

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
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

        // ربط العناصر بالواجهة الجديدة
        // لاحظ: btnGalleryTop أصبح MaterialCardView ليتطابق مع الواجهة
        val btnGalleryTop = findViewById<MaterialCardView>(R.id.btnGalleryTop)
        val btnAIChat = findViewById<MaterialCardView>(R.id.btnAIChat)
        val btnImageStudio = findViewById<View>(R.id.btnImageStudio)
        val btnTextEditor = findViewById<View>(R.id.btnTextEditor)
        val btnSettings = findViewById<View>(R.id.btnSettings)

        // إعداد المستمعين للنقرات
        // 1. زر المعرض (أعلى اليسار)
        btnGalleryTop.setOnClickListener { navigateTo(FileManagerActivity::class.java) }
        
        // 2. زر الدردشة الذكية (أعلى اليمين)
        btnAIChat.setOnClickListener { navigateTo(ChatActivity::class.java) }
        
        // 3. استوديو الصور
        btnImageStudio.setOnClickListener { navigateTo(ImageEditorActivity::class.java) }
        
        // 4. محرر النصوص (قيد التطوير)
        btnTextEditor.setOnClickListener { 
            Toast.makeText(this, "محرر النصوص قيد التطوير...", Toast.LENGTH_SHORT).show()
        }
        
        // 5. الإعدادات
        btnSettings.setOnClickListener { navigateTo(SettingsActivity::class.java) }
    }

    // دالة الانتقال الآمن بين الشاشات
    private fun navigateTo(targetActivity: Class<*>) {
        // 1. منع النقر المزدوج (1.5 ثانية)
        val currentTime = SystemClock.elapsedRealtime()
        if (currentTime - lastClickTime < 1500) return
        lastClickTime = currentTime

        // 2. اهتزاز خفيف (Haptic Feedback)
        window.decorView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

        // 3. الانتقال مع تأثير التلاشي (Fade)
        startActivity(Intent(this, targetActivity))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}

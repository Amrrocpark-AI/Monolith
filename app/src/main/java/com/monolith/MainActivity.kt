package com.monolith

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView

class MainActivity : AppCompatActivity() {

    private var lastClickTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // ربط العناصر الجديدة
        val btnGalleryTop = findViewById<ImageButton>(R.id.btnGalleryTop)
        val btnAIChat = findViewById<MaterialCardView>(R.id.btnAIChat)
        val btnImageStudio = findViewById<View>(R.id.btnImageStudio)
        val btnTextEditor = findViewById<View>(R.id.btnTextEditor)
        val btnSettings = findViewById<View>(R.id.btnSettings)

        // الانتقالات
        // 1. زر المعرض (في الأعلى)
        btnGalleryTop.setOnClickListener { navigateTo(FileManagerActivity::class.java) }
        
        // 2. زر الدردشة (في الأعلى)
        btnAIChat.setOnClickListener { navigateTo(ChatActivity::class.java) }
        
        // 3. استوديو الصور (في الشبكة)
        btnImageStudio.setOnClickListener { navigateTo(ImageEditorActivity::class.java) }
        
        // 4. محرر النصوص (في الشبكة)
        btnTextEditor.setOnClickListener { 
            Toast.makeText(this, "محرر النصوص قيد التطوير...", Toast.LENGTH_SHORT).show()
        }
        
        // 5. الإعدادات (في الشبكة)
        btnSettings.setOnClickListener { navigateTo(SettingsActivity::class.java) }
    }

    private fun navigateTo(targetActivity: Class<*>) {
        // منع النقر المزدوج
        val currentTime = SystemClock.elapsedRealtime()
        if (currentTime - lastClickTime < 1500) return
        lastClickTime = currentTime

        // اهتزاز خفيف
        window.decorView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

        // الانتقال مع تأثير Fade
        startActivity(Intent(this, targetActivity))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}

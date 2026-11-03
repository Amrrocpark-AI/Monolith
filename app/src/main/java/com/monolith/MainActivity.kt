package com.monolith

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView

class MainActivity : AppCompatActivity() {

    private var lastClickTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnSettings = findViewById<ImageButton>(R.id.btnSettings)
        val btnAIChat = findViewById<MaterialCardView>(R.id.btnAIChat)
        val btnFileManager = findViewById<android.view.View>(R.id.btnFileManager)
        val btnImageStudio = findViewById<android.view.View>(R.id.btnImageStudio)
        val btnTextEditor = findViewById<android.view.View>(R.id.btnTextEditor)

        // الانتقالات مع الحماية
        btnSettings.setOnClickListener { navigateTo(SettingsActivity::class.java) }
        btnAIChat.setOnClickListener { navigateTo(ChatActivity::class.java) }
        btnFileManager.setOnClickListener { navigateTo(FileManagerActivity::class.java) }
        
        // استوديو الصور (فوتوشوب) - سنقوم بتطويره لاحقاً، حالياً يفتح محرر الألوان
        btnImageStudio.setOnClickListener { navigateTo(ImageEditorActivity::class.java) }
        
        // محرر النصوص - سنقوم بإنشائه لاحقاً، حالياً يعرض رسالة
        btnTextEditor.setOnClickListener { 
            android.widget.Toast.makeText(this, "محرر النصوص قيد التطوير...", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateTo(targetActivity: Class<*>) {
        // 1. منع النقر المزدوج
        val currentTime = SystemClock.elapsedRealtime()
        if (currentTime - lastClickTime < 1500) return
        lastClickTime = currentTime

        // 2. اهتزاز خفيف
        window.decorView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

        // 3. الانتقال مع تأثير Fade
        startActivity(Intent(this, targetActivity))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}

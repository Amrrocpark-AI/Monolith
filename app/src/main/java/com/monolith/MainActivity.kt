package com.monolith

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView

class MainActivity : AppCompatActivity() {

    // درع منع النقر المزدوج
    private var lastClickTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val cardChat = findViewById<MaterialCardView>(R.id.cardChat)
        val cardFileManager = findViewById<MaterialCardView>(R.id.cardFileManager)
        val cardImageEditor = findViewById<MaterialCardView>(R.id.cardImageEditor)
        val cardDispersion = findViewById<MaterialCardView>(R.id.cardDispersion)
        val cardSettings = findViewById<MaterialCardView>(R.id.cardSettings)

        cardChat.setOnClickListener { navigateTo(ChatActivity::class.java) }
        cardFileManager.setOnClickListener { navigateTo(FileManagerActivity::class.java) }
        cardImageEditor.setOnClickListener { navigateTo(ImageEditorActivity::class.java) }
        cardDispersion.setOnClickListener { navigateTo(DispersionEditorActivity::class.java) }
        cardSettings.setOnClickListener { navigateTo(SettingsActivity::class.java) }
    }

    // الدالة السيادية للانتقال الآمن بين الشاشات
    private fun navigateTo(targetActivity: Class<*>) {
        // 1. منع النقر المزدوج (نسمح بنقرة واحدة كل 1.5 ثانية)
        val currentTime = SystemClock.elapsedRealtime()
        if (currentTime - lastClickTime < 1500) {
            return // تجاهل النقرة السريعة
        }
        lastClickTime = currentTime

        // 2. الاهتزاز الخفيف (Haptic Feedback)
        window.decorView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

        // 3. الانتقال مع تأثير سينمائي
        startActivity(Intent(this, targetActivity))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}

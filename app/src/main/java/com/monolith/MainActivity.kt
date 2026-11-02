package com.monolith

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val cardChat = findViewById<MaterialCardView>(R.id.cardChat)
        val cardFileManager = findViewById<MaterialCardView>(R.id.cardFileManager)
        val cardImageEditor = findViewById<MaterialCardView>(R.id.cardImageEditor)
        val cardSettings = findViewById<MaterialCardView>(R.id.cardSettings)

        cardChat.setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java))
        }

        cardFileManager.setOnClickListener {
            startActivity(Intent(this, FileManagerActivity::class.java))
        }

        // *** الانتقال إلى محرر الصور ***
        cardImageEditor.setOnClickListener {
            startActivity(Intent(this, ImageEditorActivity::class.java))
        }

        cardSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }
}

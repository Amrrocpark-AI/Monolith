package com.monolith

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<MaterialCardView>(R.id.cardChat).setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java))
        }

        findViewById<MaterialCardView>(R.id.cardFileManager).setOnClickListener {
            startActivity(Intent(this, FileManagerActivity::class.java))
        }

        findViewById<MaterialCardView>(R.id.cardImageEditor).setOnClickListener {
            startActivity(Intent(this, ImageEditorActivity::class.java))
        }

        findViewById<MaterialCardView>(R.id.cardDispersion).setOnClickListener {
            startActivity(Intent(this, DispersionEditorActivity::class.java))
        }

        findViewById<MaterialCardView>(R.id.cardSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // زر العدسة الجراحية (بدون أي رسائل خطأ)
        findViewById<MaterialCardView>(R.id.cardSurgicalLens).setOnClickListener {
            startActivity(Intent(this, SurgicalLensActivity::class.java))
        }
    }
}

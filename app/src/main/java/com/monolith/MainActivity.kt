package com.monolith

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. بطاقة الدردشة (إذا كان اسم شاشتك مختلفاً، غير ChatActivity لاسمها)
        findViewById<MaterialCardView>(R.id.cardChat).setOnClickListener {
            try {
                startActivity(Intent(this, ChatActivity::class.java))
            } catch (e: Exception) {
                Toast.makeText(this, "شاشة الدردشة غير متوفرة حالياً", Toast.LENGTH_SHORT).show()
            }
        }

        // 2. بطاقة مستكشف الملفات
        findViewById<MaterialCardView>(R.id.cardFileManager).setOnClickListener {
            try {
                startActivity(Intent(this, FileManagerActivity::class.java))
            } catch (e: Exception) {
                Toast.makeText(this, "شاشة الملفات غير متوفرة", Toast.LENGTH_SHORT).show()
            }
        }

        // 3. بطاقة محرر الصور (الفوتوشوب)
        findViewById<MaterialCardView>(R.id.cardImageEditor).setOnClickListener {
            try {
                startActivity(Intent(this, ImageEditorActivity::class.java))
            } catch (e: Exception) {
                Toast.makeText(this, "شاشة محرر الصور غير متوفرة", Toast.LENGTH_SHORT).show()
            }
        }

        // 4. بطاقة أداة التشتت
        findViewById<MaterialCardView>(R.id.cardDispersion).setOnClickListener {
            try {
                startActivity(Intent(this, DispersionEditorActivity::class.java))
            } catch (e: Exception) {
                Toast.makeText(this, "شاشة التشتت غير متوفرة", Toast.LENGTH_SHORT).show()
            }
        }

        // 5. بطاقة الإعدادات
        findViewById<MaterialCardView>(R.id.cardSettings).setOnClickListener {
            try {
                startActivity(Intent(this, SettingsActivity::class.java))
            } catch (e: Exception) {
                Toast.makeText(this, "شاشة الإعدادات غير متوفرة", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

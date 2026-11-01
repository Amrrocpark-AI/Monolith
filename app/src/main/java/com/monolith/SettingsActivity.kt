package com.monolith

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val spinner = findViewById<Spinner>(R.id.languageSpinner)
        val btnApplyLanguage = findViewById<Button>(R.id.btnApplyLanguage)
        val backgroundGroup = findViewById<RadioGroup>(R.id.backgroundGroup)
        val btnPickImage = findViewById<Button>(R.id.btnPickImage)

        // قائمة اللغات
        val languages = arrayOf("English", "العربية", "Français", "Español", "日本語", "فارسی", "Türkçe", "Bahasa Indonesia", "中文", "ⵜⴰⵎⴰⵣⵉⵖⵜ", "한국어")
        val languageCodes = arrayOf("en", "ar", "fr", "es", "ja", "fa", "tr", "id", "zh", "zgh", "ko")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, languages)
        spinner.adapter = adapter

        // 1. إصلاح تغيير اللغة (الطريقة الحديثة)
        btnApplyLanguage.setOnClickListener {
            val selectedPosition = spinner.selectedItemPosition
            val langCode = languageCodes[selectedPosition]
            
            // تطبيق اللغة باستخدام AppCompatDelegate
            val appLocale = LocaleListCompat.forLanguageTags(langCode)
            AppCompatDelegate.setApplicationLocales(appLocale)
            
            // إعادة تشغيل التطبيق من البداية ليتفهم اللغة
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
        }

        // 2. حفظ خيار الخلفية (لون)
        backgroundGroup.setOnCheckedChangeListener { _, checkedId ->
            val sharedPreferences = getSharedPreferences("Settings", MODE_PRIVATE)
            val editor = sharedPreferences.edit()
            when (checkedId) {
                R.id.rbDark -> editor.putString("AppBackground", "#0A0A0A")
                R.id.rbLight -> editor.putString("AppBackground", "#FFFFFF")
                R.id.rbCyber -> editor.putString("AppBackground", "#001A00")
            }
            editor.apply()
        }

        // 3. اختيار صورة من المعرض
        val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                val sharedPreferences = getSharedPreferences("Settings", MODE_PRIVATE)
                sharedPreferences.edit().putString("AppBackgroundImage", uri.toString()).apply()
                Toast.makeText(this, "تم حفظ الصورة بنجاح!", Toast.LENGTH_SHORT).show()
            }
        }

        btnPickImage.setOnClickListener {
            // هذا الأمر يفتح معرض الصور
            pickImageLauncher.launch("image/*")
        }
    }
}

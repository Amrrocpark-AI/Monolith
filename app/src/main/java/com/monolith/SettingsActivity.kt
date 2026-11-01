package com.monolith

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.RadioGroup
import android.widget.SeekBar
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

        // عناصر الألوان
        val sbRed = findViewById<SeekBar>(R.id.sbRed)
        val sbGreen = findViewById<SeekBar>(R.id.sbGreen)
        val sbBlue = findViewById<SeekBar>(R.id.sbBlue)
        val viewColorPreview = findViewById<View>(R.id.viewColorPreview)
        val btnSaveColor = findViewById<Button>(R.id.btnSaveColor)

        val sharedPreferences = getSharedPreferences("Settings", MODE_PRIVATE)

        // قائمة اللغات
        val languages = arrayOf("English", "العربية", "Français", "Español", "日本語", "فارسی", "Türkçe", "Bahasa Indonesia", "中文", "ⵜⴰⵎⴰⵣⵉⵖⵜ", "한국어")
        val languageCodes = arrayOf("en", "ar", "fr", "es", "ja", "fa", "tr", "id", "zh", "zgh", "ko")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, languages)
        spinner.adapter = adapter

        // تطبيق اللغة
        btnApplyLanguage.setOnClickListener {
            val selectedPosition = spinner.selectedItemPosition
            val langCode = languageCodes[selectedPosition]
            val appLocale = LocaleListCompat.forLanguageTags(langCode)
            AppCompatDelegate.setApplicationLocales(appLocale)
            
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
        }

        // حفظ خيار الخلفية
        backgroundGroup.setOnCheckedChangeListener { _, checkedId ->
            val editor = sharedPreferences.edit()
            when (checkedId) {
                R.id.rbDark -> editor.putString("AppBackground", "#0A0A0A")
                R.id.rbLight -> editor.putString("AppBackground", "#FFFFFF")
                R.id.rbCyber -> editor.putString("AppBackground", "#001A00")
            }
            editor.apply()
        }

        // اختيار صورة الخلفية (بإذن دائم)
        val pickImageLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                try {
                    // طلب إذن دائم للوصول للصورة
                    val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    contentResolver.takePersistableUriPermission(uri, takeFlags)
                    
                    sharedPreferences.edit().putString("AppBackgroundImage", uri.toString()).apply()
                    Toast.makeText(this, "تم حفظ الصورة بإذن دائم!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "حدث خطأ أثناء حفظ الصورة", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnPickImage.setOnClickListener {
            // استخدام المصفوفة لأن OpenDocument يتطلب ذلك
            pickImageLauncher.launch(arrayOf("image/*"))
        }

        // ==========================================
        // منطق شرائح الألوان (RGB)
        // ==========================================
        
        val updateColorPreview = {
            val r = sbRed.progress
            val g = sbGreen.progress
            val b = sbBlue.progress
            val color = Color.rgb(r, g, b)
            viewColorPreview.setBackgroundColor(color)
        }

        sbRed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { updateColorPreview() }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        sbGreen.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { updateColorPreview() }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        sbBlue.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { updateColorPreview() }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // زر حفظ اللون
        btnSaveColor.setOnClickListener {
            val r = sbRed.progress
            val g = sbGreen.progress
            val b = sbBlue.progress
            val colorHex = String.format("#%06X", 0xFFFFFF and Color.rgb(r, g, b))
            
            sharedPreferences.edit().putString("AppThemeColor", colorHex).apply()
            Toast.makeText(this, "تم حفظ اللون: $colorHex", Toast.LENGTH_SHORT).show()
        }

        // استعادة اللون المحفوظ
        val savedColorHex = sharedPreferences.getString("AppThemeColor", "#00FF00")
        if (savedColorHex != null) {
            try {
                val savedColor = Color.parseColor(savedColorHex)
                sbRed.progress = Color.red(savedColor)
                sbGreen.progress = Color.green(savedColor)
                sbBlue.progress = Color.blue(savedColor)
                viewColorPreview.setBackgroundColor(savedColor)
            } catch (e: Exception) {}
        }
    }
}

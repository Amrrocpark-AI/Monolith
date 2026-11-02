package com.monolith

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // ==========================================
        // 1. التخزين المشفر (لحماية المفتاح)
        // ==========================================
        val masterKey = MasterKey.Builder(this)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val securePrefs = EncryptedSharedPreferences.create(
            this,
            "SecureSettings",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        // ==========================================
        // 2. المحرك الذكي (المفتاح والاختبار)
        // ==========================================
        val providerSpinner = findViewById<Spinner>(R.id.providerSpinner)
        val etApiKey = findViewById<EditText>(R.id.etApiKey)
        val btnTestAndSave = findViewById<Button>(R.id.btnTestAndSave)

        val providers = arrayOf("Groq (سريع ومجاني)", "OpenAI (مدفوع)", "Custom (محلي)")
        val providerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, providers)
        providerSpinner.adapter = providerAdapter

        val savedKey = securePrefs.getString("api_key", "")
        if (!savedKey.isNullOrEmpty()) {
            etApiKey.setText(savedKey)
        }

        btnTestAndSave.setOnClickListener {
            val apiKey = etApiKey.text.toString().trim()
            if (apiKey.isEmpty()) {
                Toast.makeText(this, "الرجاء إدخال المفتاح أولاً", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnTestAndSave.isEnabled = false
            btnTestAndSave.text = "جاري الفحص..."
            btnTestAndSave.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#666666"))

            lifecycleScope.launch {
                val isSuccess = testApiKey(apiKey)
                withContext(Dispatchers.Main) {
                    val dynamicShape = GradientDrawable()
                    dynamicShape.cornerRadius = 50f

                    if (isSuccess) {
                        securePrefs.edit().putString("api_key", apiKey).apply()
                        btnTestAndSave.text = "تم الاتصال بنجاح"
                        dynamicShape.setColor(Color.parseColor("#00FF00"))
                        btnTestAndSave.background = dynamicShape
                        btnTestAndSave.setTextColor(Color.BLACK)
                        
                        val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                        } else {
                            @Suppress("DEPRECATION")
                            vibrator.vibrate(50)
                        }
                    } else {
                        btnTestAndSave.text = "فشل الاتصال، تحقق من المفتاح"
                        dynamicShape.setColor(Color.parseColor("#FF0000"))
                        btnTestAndSave.background = dynamicShape
                        btnTestAndSave.setTextColor(Color.WHITE)
                    }
                    
                    btnTestAndSave.postDelayed({
                        btnTestAndSave.isEnabled = true
                        btnTestAndSave.text = "اختبار وحفظ المفتاح"
                        btnTestAndSave.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#00FF00"))
                        btnTestAndSave.setTextColor(Color.BLACK)
                    }, 3000)
                }
            }
        }

        // ==========================================
        // 3. كودك الأصلي (اللغة، الخلفية، الألوان)
        // ==========================================
        val spinner = findViewById<Spinner>(R.id.languageSpinner)
        val btnApplyLanguage = findViewById<Button>(R.id.btnApplyLanguage)
        val backgroundGroup = findViewById<RadioGroup>(R.id.backgroundGroup)
        val btnPickImage = findViewById<Button>(R.id.btnPickImage)

        val sbRed = findViewById<SeekBar>(R.id.sbRed)
        val sbGreen = findViewById<SeekBar>(R.id.sbGreen)
        val sbBlue = findViewById<SeekBar>(R.id.sbBlue)
        val viewColorPreview = findViewById<View>(R.id.viewColorPreview)
        val btnSaveColor = findViewById<Button>(R.id.btnSaveColor)

        val sharedPreferences = getSharedPreferences("Settings", MODE_PRIVATE)

        val languages = arrayOf("English", "العربية", "Français", "Español", "日本語", "فارسی", "Türkçe", "Bahasa Indonesia", "中文", "ⵜⴰⵎⴰⵣⵉⵖⵜ", "한국어")
        val languageCodes = arrayOf("en", "ar", "fr", "es", "ja", "fa", "tr", "id", "zh", "zgh", "ko")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, languages)
        spinner.adapter = adapter

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

        backgroundGroup.setOnCheckedChangeListener { _, checkedId ->
            val editor = sharedPreferences.edit()
            when (checkedId) {
                R.id.rbDark -> editor.putString("AppBackground", "#0A0A0A")
                R.id.rbLight -> editor.putString("AppBackground", "#FFFFFF")
                R.id.rbCyber -> editor.putString("AppBackground", "#001A00")
            }
            editor.apply()
        }

        val pickImageLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                try {
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
            pickImageLauncher.launch(arrayOf("image/*"))
        }

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

        btnSaveColor.setOnClickListener {
            val r = sbRed.progress
            val g = sbGreen.progress
            val b = sbBlue.progress
            val colorHex = String.format("#%06X", 0xFFFFFF and Color.rgb(r, g, b))
            
            sharedPreferences.edit().putString("AppThemeColor", colorHex).apply()
            Toast.makeText(this, "تم حفظ اللون: $colorHex", Toast.LENGTH_SHORT).show()
        }

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

    private suspend fun testApiKey(apiKey: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient.Builder().build()
                val request = Request.Builder()
                    .url("https://api.groq.com/openai/v1/models")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .build()
                client.newCall(request).execute().isSuccessful
            } catch (e: Exception) { false }
        }
    }
}

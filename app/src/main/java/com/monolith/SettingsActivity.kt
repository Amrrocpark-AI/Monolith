package com.monolith

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class SettingsActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: android.content.SharedPreferences
    private lateinit var securePrefs: android.content.SharedPreferences
    private lateinit var switchFloatingBubble: SwitchMaterial

    private val overlayPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.canDrawOverlays(this)) {
                switchFloatingBubble.isChecked = true
                startFloatingService()
                requestBatteryOptimizationExemption()
                Toast.makeText(this, "تم تفعيل الفقاعة العائمة بنجاح", Toast.LENGTH_SHORT).show()
            } else {
                switchFloatingBubble.isChecked = false
                Toast.makeText(this, "لم يتم منح صلاحية الرسم فوق التطبيقات", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        sharedPreferences = getSharedPreferences("Settings", MODE_PRIVATE)
        val masterKey = MasterKey.Builder(this).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        securePrefs = EncryptedSharedPreferences.create(
            this, "SecureSettings", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        // 1. المحرك الذكي
        val providerSpinner = findViewById<Spinner>(R.id.providerSpinner)
        val etApiKey = findViewById<EditText>(R.id.etApiKey)
        val btnTestAndSave = findViewById<Button>(R.id.btnTestAndSave)

        val providers = arrayOf("Groq (سريع ومجاني)", "OpenAI (مدفوع)", "Custom (محلي)")
        providerSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, providers)

        val savedKey = securePrefs.getString("api_key", "")
        if (!savedKey.isNullOrEmpty()) etApiKey.setText(savedKey)

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
                    val dynamicShape = GradientDrawable().apply { cornerRadius = 50f }
                    if (isSuccess) {
                        securePrefs.edit().putString("api_key", apiKey).apply()
                        btnTestAndSave.text = "تم الاتصال بنجاح"
                        dynamicShape.setColor(Color.parseColor("#00FF00"))
                        btnTestAndSave.background = dynamicShape
                        btnTestAndSave.setTextColor(Color.BLACK)
                        val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                        else @Suppress("DEPRECATION") vibrator.vibrate(50)
                    } else {
                        btnTestAndSave.text = "فشل الاتصال"
                        dynamicShape.setColor(Color.parseColor("#FF0000"))
                        btnTestAndSave.background = dynamicShape
                        btnTestAndSave.setTextColor(Color.WHITE)
                    }
                    btnTestAndSave.postDelayed({
                        btnTestAndSave.isEnabled = true
                        btnTestAndSave.text = "اختبار وحفظ المفتاح"
                        btnTestAndSave.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#00D4FF"))
                        btnTestAndSave.setTextColor(Color.BLACK)
                    }, 3000)
                }
            }
        }

        // 2. تخصيص شريط التحديد
        val rgNeonColor = findViewById<RadioGroup>(R.id.rgNeonColor)
        val rgFrameStyle = findViewById<RadioGroup>(R.id.rgFrameStyle)
        val btnSaveSelectionStyle = findViewById<Button>(R.id.btnSaveSelectionStyle)

        when (sharedPreferences.getString("selection_bar_color", "BLUE")) {
            "BLUE" -> rgNeonColor.check(R.id.rbNeonBlue)
            "PINK" -> rgNeonColor.check(R.id.rbNeonPink)
            "GREEN" -> rgNeonColor.check(R.id.rbNeonGreen)
        }
        when (sharedPreferences.getString("selection_bar_style", "CYBER")) {
            "CYBER" -> rgFrameStyle.check(R.id.rbStyleCyber)
            "ROUNDED" -> rgFrameStyle.check(R.id.rbStyleRounded)
        }

        btnSaveSelectionStyle.setOnClickListener {
            val colorStr = when (rgNeonColor.checkedRadioButtonId) {
                R.id.rbNeonBlue -> "BLUE"
                R.id.rbNeonPink -> "PINK"
                R.id.rbNeonGreen -> "GREEN"
                else -> "BLUE"
            }
            val styleStr = when (rgFrameStyle.checkedRadioButtonId) {
                R.id.rbStyleCyber -> "CYBER"
                R.id.rbStyleRounded -> "ROUNDED"
                else -> "CYBER"
            }
            sharedPreferences.edit().putString("selection_bar_color", colorStr).putString("selection_bar_style", styleStr).apply()
            Toast.makeText(this, "تم حفظ تخصيص شريط التحديد", Toast.LENGTH_SHORT).show()
        }

        // 3. إعدادات المظهر
        val spinner = findViewById<Spinner>(R.id.languageSpinner)
        val btnApplyLanguage = findViewById<Button>(R.id.btnApplyLanguage)
        val backgroundGroup = findViewById<RadioGroup>(R.id.backgroundGroup)
        val btnPickImage = findViewById<Button>(R.id.btnPickImage)

        val languages = arrayOf("English", "العربية", "Français", "Español", "日本語", "فارسی", "Türkçe", "Bahasa Indonesia", "中文", "ⵜⴰⵎⴰⵣⵉⵖⵜ", "한국어")
        val languageCodes = arrayOf("en", "ar", "fr", "es", "ja", "fa", "tr", "id", "zh", "zgh", "ko")
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, languages)

        btnApplyLanguage.setOnClickListener {
            val langCode = languageCodes[spinner.selectedItemPosition]
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(langCode))
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
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    sharedPreferences.edit().putString("AppBackgroundImage", uri.toString()).apply()
                    Toast.makeText(this, "تم حفظ الصورة!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) { Toast.makeText(this, "حدث خطأ", Toast.LENGTH_SHORT).show() }
            }
        }
        btnPickImage.setOnClickListener { pickImageLauncher.launch(arrayOf("image/*")) }

        val sbRed = findViewById<SeekBar>(R.id.sbRed)
        val sbGreen = findViewById<SeekBar>(R.id.sbGreen)
        val sbBlue = findViewById<SeekBar>(R.id.sbBlue)
        val viewColorPreview = findViewById<View>(R.id.viewColorPreview)
        val btnSaveColor = findViewById<Button>(R.id.btnSaveColor)

        val updateColorPreview = {
            viewColorPreview.setBackgroundColor(Color.rgb(sbRed.progress, sbGreen.progress, sbBlue.progress))
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
            val colorHex = String.format("#%06X", 0xFFFFFF and Color.rgb(sbRed.progress, sbGreen.progress, sbBlue.progress))
            sharedPreferences.edit().putString("AppThemeColor", colorHex).apply()
            Toast.makeText(this, "تم حفظ اللون: $colorHex", Toast.LENGTH_SHORT).show()
        }
        val savedColorHex = sharedPreferences.getString("AppThemeColor", "#00D4FF")
        if (savedColorHex != null) {
            try {
                val savedColor = Color.parseColor(savedColorHex)
                sbRed.progress = Color.red(savedColor); sbGreen.progress = Color.green(savedColor); sbBlue.progress = Color.blue(savedColor)
                viewColorPreview.setBackgroundColor(savedColor)
            } catch (e: Exception) {}
        }

        // 4. الأدوات السيادية
        switchFloatingBubble = findViewById(R.id.switchFloatingBubble)
        val btnAllowAutoStart = findViewById<Button>(R.id.btnAllowAutoStart)
        val btnEnableVolumeService = findViewById<Button>(R.id.btnEnableVolumeService)
        
        // *** التحكم في حجم الأيقونة ***
        val seekBarBubbleSize = findViewById<SeekBar>(R.id.seekBarBubbleSize)
        val tvBubbleSizeValue = findViewById<TextView>(R.id.tvBubbleSizeValue)
        
        val savedBubbleSize = sharedPreferences.getInt("bubble_scale_int", 100)
        seekBarBubbleSize.progress = savedBubbleSize
        tvBubbleSizeValue.text = "$savedBubbleSize%"

        seekBarBubbleSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // الحد الأدنى 50%، والأقصى 150%
                val actualSize = if (progress < 50) 50 else progress
                tvBubbleSizeValue.text = "$actualSize%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val actualSize = if (seekBar?.progress ?: 100 < 50) 50 else seekBar?.progress ?: 100
                sharedPreferences.edit().putInt("bubble_scale_int", actualSize).apply()
                Toast.makeText(this@SettingsActivity, "تم حفظ حجم الأيقونة. أعد تشغيل الفقاعة لتطبيقه.", Toast.LENGTH_LONG).show()
            }
        })

        val bubbleWasEnabled = sharedPreferences.getBoolean("bubble_enabled", false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            switchFloatingBubble.isChecked = bubbleWasEnabled && Settings.canDrawOverlays(this)
        }

        switchFloatingBubble.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                    overlayPermissionLauncher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                } else {
                    startFloatingService()
                    requestBatteryOptimizationExemption()
                }
            } else {
                stopFloatingService()
            }
        }

        btnAllowAutoStart.setOnClickListener { OemUtils.openAutoStartSettings(this) }

        btnEnableVolumeService.setOnClickListener {
            Toast.makeText(this, "ابحث عن 'مونوليث' في القائمة وفعّل الخدمة", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    private fun startFloatingService() {
        val serviceIntent = Intent(this, FloatingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent) else startService(serviceIntent)
        Toast.makeText(this, "تم تشغيل الفقاعة العائمة", Toast.LENGTH_SHORT).show()
    }

    private fun stopFloatingService() {
        getSharedPreferences("Settings", MODE_PRIVATE).edit().putBoolean("bubble_enabled", false).apply()
        stopService(Intent(this, FloatingService::class.java))
        stopService(Intent(this, ScreenCaptureService::class.java))
        Toast.makeText(this, "تم إيقاف الفقاعة العائمة", Toast.LENGTH_SHORT).show()
    }

    private suspend fun testApiKey(apiKey: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                OkHttpClient.Builder().build().newCall(
                    Request.Builder().url("https://api.groq.com/openai/v1/models").addHeader("Authorization", "Bearer $apiKey").build()
                ).execute().isSuccessful
            } catch (e: Exception) { false }
        }
    }
}

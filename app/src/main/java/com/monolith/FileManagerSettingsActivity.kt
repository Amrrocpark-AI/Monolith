package com.monolith

import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout

class FileManagerSettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var rootLayout: ConstraintLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_manager_settings)

        prefs = getSharedPreferences("FileManagerPrefs", MODE_PRIVATE)
        rootLayout = findViewById(R.id.settingsRootLayout)

        // تطبيق الخلفية المحفوظة
        val savedColor = prefs.getInt("bg_color", Color.parseColor("#050505"))
        rootLayout.setBackgroundColor(savedColor)

        // زر الرجوع
        findViewById<ImageButton>(R.id.btnBackSettings).setOnClickListener {
            finish() // إغلاق الشاشة والعودة
        }

        // الوضع الداكن/الفاتح (Switch)
        val swDarkMode = findViewById<Switch>(R.id.swDarkMode)
        swDarkMode.isChecked = savedColor == Color.parseColor("#050505")
        swDarkMode.setOnCheckedChangeListener { _, isChecked ->
            val newColor = if (isChecked) Color.parseColor("#050505") else Color.parseColor("#FFFFFF")
            rootLayout.setBackgroundColor(newColor)
            prefs.edit().putInt("bg_color", newColor).apply()
        }

        // شبكة الألوان
        val gridColors = findViewById<GridLayout>(R.id.gridColors)
        val colors = arrayOf(
            Color.parseColor("#050505"), // أسود
            Color.parseColor("#FFFFFF"), // أبيض
            Color.parseColor("#1A1A1A"), // رمادي داكن
            Color.parseColor("#0D47A1"), // أزرق داكن
            Color.parseColor("#B71C1C"), // أحمر داكن
            Color.parseColor("#1B5E20"), // أخضر داكن
            Color.parseColor("#4A148C"), // بنفسجي داكن
            Color.parseColor("#E65100"), // برتقالي داكن
            Color.parseColor("#004D40"), // أخضر مزرق
            Color.parseColor("#3E2723")  // بني داكن
        )

        for (color in colors) {
            val colorView = LinearLayout(this)
            val params = GridLayout.LayoutParams()
            params.width = 60
            params.height = 60
            params.setMargins(8, 8, 8, 8)
            colorView.layoutParams = params
            colorView.setBackgroundColor(color)
            colorView.setOnClickListener {
                rootLayout.setBackgroundColor(color)
                prefs.edit().putInt("bg_color", color).apply()
            }
            gridColors.addView(colorView)
        }
    }
}

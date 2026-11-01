package com.monolith

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.ImageButton
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ChatActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        val etMessage = findViewById<EditText>(R.id.etMessage)
        val btnSend = findViewById<ImageButton>(R.id.btnSend)
        val btnSettings = findViewById<ImageButton>(R.id.btnSettings)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val btnAttach = findViewById<ImageButton>(R.id.btnAttach)
        val btnMic = findViewById<ImageButton>(R.id.btnMic)
        val btnVisionToggle = findViewById<ImageButton>(R.id.btnVisionToggle)
        
        val chatContainer = findViewById<LinearLayout>(R.id.chatContainer)
        val chatScrollView = findViewById<android.widget.ScrollView>(R.id.chatScrollView)

        // 1. زر الرجوع (إغلاق الشاشة)
        btnBack.setOnClickListener { finish() }

        // 2. زر الإعدادات
        btnSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        // 3. الأزرار الجديدة (مؤقتاً تعرض رسالة حتى نبرمجها لاحقاً)
        btnAttach.setOnClickListener { Toast.makeText(this, "ميزة إرفاق الملفات قريباً", Toast.LENGTH_SHORT).show() }
        btnMic.setOnClickListener { Toast.makeText(this, "ميزة تسجيل الصوت قريباً", Toast.LENGTH_SHORT).show() }
        btnVisionToggle.setOnClickListener { Toast.makeText(this, "تم تفعيل الرؤية الحاسوبية (محاكاة)", Toast.LENGTH_SHORT).show() }

        fun sendMessage() {
            val message = etMessage.text.toString().trim()
            if (message.isNotEmpty()) {
                addChatBubble(chatContainer, message, isUser = true)
                etMessage.text.clear()
                addChatBubble(chatContainer, "جاري الاتصال بالنموذج المحلي...", isUser = false)
                chatScrollView.post { chatScrollView.fullScroll(android.widget.ScrollView.FOCUS_DOWN) }
            }
        }

        btnSend.setOnClickListener { sendMessage() }

        etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else {
                false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val sharedPreferences = getSharedPreferences("Settings", MODE_PRIVATE)
        val rootLayout = findViewById<LinearLayout>(R.id.rootLayout)

        val imageUriString = sharedPreferences.getString("AppBackgroundImage", null)
        
        if (imageUriString != null) {
            try {
                val uri = Uri.parse(imageUriString)
                val inputStream = contentResolver.openInputStream(uri)
                
                val options = BitmapFactory.Options()
                options.inSampleSize = 2 
                val bitmap = BitmapFactory.decodeStream(inputStream, null, options)
                
                rootLayout.background = BitmapDrawable(resources, bitmap)
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    rootLayout.foreground = android.graphics.drawable.ColorDrawable(Color.parseColor("#99000000"))
                }
                
            } catch (e: Exception) {
                applyColorBackground(sharedPreferences, rootLayout)
            }
        } else {
            applyColorBackground(sharedPreferences, rootLayout)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                rootLayout.foreground = null
            }
        }

        val savedColor = sharedPreferences.getString("AppBackground", "#0A0A0A") ?: "#0A0A0A"
        val etMessage = findViewById<EditText>(R.id.etMessage)
        
        if (savedColor == "#FFFFFF" && imageUriString == null) {
            etMessage.setTextColor(Color.BLACK)
            etMessage.setHintTextColor(Color.GRAY)
        } else {
            etMessage.setTextColor(Color.WHITE)
            etMessage.setHintTextColor(Color.parseColor("#666666"))
        }
    }

    private fun applyColorBackground(sharedPreferences: android.content.SharedPreferences, rootLayout: LinearLayout) {
        val savedColor = sharedPreferences.getString("AppBackground", "#0A0A0A") ?: "#0A0A0A"
        rootLayout.setBackgroundColor(Color.parseColor(savedColor))
    }

    private fun addChatBubble(container: LinearLayout, text: String, isUser: Boolean) {
        val textView = TextView(this)
        textView.text = text
        textView.textSize = 16f
        
        textView.setTextColor(if (isUser) Color.BLACK else Color.WHITE)
        textView.setPadding(32, 24, 32, 24)
        
        textView.textDirection = if (isUser) TextView.TEXT_DIRECTION_RTL else TextView.TEXT_DIRECTION_LOCALE
        textView.textAlignment = TextView.TEXT_ALIGNMENT_CENTER

        val background = GradientDrawable()
        background.setColor(if (isUser) Color.parseColor("#00FF00") else Color.parseColor("#CC2A2A2A"))
        background.cornerRadius = 40f
        textView.background = background

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        
        if (isUser) {
            params.gravity = Gravity.END
            params.setMargins(80, 12, 0, 12)
        } else {
            params.gravity = Gravity.START
            params.setMargins(0, 12, 80, 12)
        }
        
        textView.layoutParams = params
        container.addView(textView)
    }
}

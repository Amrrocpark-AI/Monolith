package com.monolith

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ChatActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        val etMessage = findViewById<EditText>(R.id.etMessage)
        val btnSend = findViewById<Button>(R.id.btnSend)
        val chatContainer = findViewById<LinearLayout>(R.id.chatContainer)
        val chatScrollView = findViewById<android.widget.ScrollView>(R.id.chatScrollView)
        val btnSettings = findViewById<Button>(R.id.btnSettings)

        btnSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

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

        // 1. التحقق من وجود صورة محفوظة أولاً
        val imageUriString = sharedPreferences.getString("AppBackgroundImage", null)
        
        if (imageUriString != null) {
            try {
                val uri = Uri.parse(imageUriString)
                val inputStream = contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                rootLayout.background = BitmapDrawable(resources, bitmap)
            } catch (e: Exception) {
                // إذا فشل تحميل الصورة، نعود للون
                applyColorBackground(sharedPreferences, rootLayout)
            }
        } else {
            // إذا لم توجد صورة، نطبق اللون المحفوظ
            applyColorBackground(sharedPreferences, rootLayout)
        }

        // تغيير لون النصوص بناءً على الخلفية (نفترض هنا دائماً وضع النصوص البيضاء أو السوداء حسب اللون)
        val savedColor = sharedPreferences.getString("AppBackground", "#0A0A0A") ?: "#0A0A0A"
        val etMessage = findViewById<EditText>(R.id.etMessage)
        val btnSettings = findViewById<Button>(R.id.btnSettings)
        
        if (savedColor == "#FFFFFF") {
            etMessage.setTextColor(Color.BLACK)
            etMessage.setHintTextColor(Color.GRAY)
            btnSettings.setTextColor(Color.BLACK)
        } else {
            etMessage.setTextColor(Color.WHITE)
            etMessage.setHintTextColor(Color.parseColor("#666666"))
            btnSettings.setTextColor(Color.WHITE)
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
        background.setColor(if (isUser) Color.parseColor("#00FF00") else Color.parseColor("#2A2A2A"))
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

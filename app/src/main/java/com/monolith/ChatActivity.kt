package com.monolith

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
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
        val btnSettings = findViewById<Button>(R.id.btnSettings) // زر الإعدادات الجديد

        // فتح شاشة الإعدادات عند الضغط على الترس
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

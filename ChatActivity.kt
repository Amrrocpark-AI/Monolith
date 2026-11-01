package com.monolith

import android.os.Bundle
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

        btnSend.setOnClickListener {
            val message = etMessage.text.toString().trim()
            if (message.isNotEmpty()) {
                // عرض رسالة المستخدم
                val textView = TextView(this)
                textView.text = message
                textView.textSize = 16f
                textView.setTextColor(android.graphics.Color.WHITE)
                textView.setPadding(16, 16, 16, 16)
                chatContainer.addView(textView)

                etMessage.text.clear()

                // هنا سنضع لاحقاً كود الاتصال بالذكاء الاصطناعي
                // سنعرض رداً تجريبياً الآن
                val botTextView = TextView(this)
                botTextView.text = "جاري الاتصال بالنموذج المحلي..."
                botTextView.textSize = 16f
                botTextView.setTextColor(android.graphics.Color.GREEN)
                botTextView.setPadding(16, 16, 16, 16)
                chatContainer.addView(botTextView)

                chatScrollView.post { chatScrollView.fullScroll(android.widget.ScrollView.FOCUS_DOWN) }
            }
        }
    }
}

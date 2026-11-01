package com.monolith

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnStart = findViewById<Button>(R.id.btnStart)
        
        btnStart.setOnClickListener {
            // الانتقال إلى شاشة الدردشة
            val intent = Intent(this, ChatActivity::class.java)
            startActivity(intent)
        }
    }
}

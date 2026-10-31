package com.monolith

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnStart = findViewById<Button>(R.id.btnStart)
        
        btnStart.setOnClickListener {
            Toast.makeText(this, "تم تشغيل النموذج المحلي بنجاح!", Toast.LENGTH_LONG).show()
        }
    }
}

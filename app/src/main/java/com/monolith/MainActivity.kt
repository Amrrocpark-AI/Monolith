package com.monolith

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ربط الشاشة بالتصميم
        setContentView(R.layout.activity_main)
    }
}

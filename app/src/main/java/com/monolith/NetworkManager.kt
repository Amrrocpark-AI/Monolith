package com.monolith

import android.util.Log

object NetworkManager {
    private const val TAG = "MonolithNetwork"
    private var isServerRunning = false
    
    fun startP2PServer() {
        if (isServerRunning) return
        isServerRunning = true
        Log.d(TAG, "P2P Server started on port 8080. Waiting for remote agents...")
        // سيتم هنا لاحقاً إضافة كود لاستقبال الأوامر من هواتف أخرى
    }
    
    fun stopP2PServer() {
        isServerRunning = false
        Log.d(TAG, "P2P Server stopped.")
    }
    
    fun connectToAgent(ipAddress: String) {
        Log.d(TAG, "Attempting to connect to remote agent at $ipAddress")
        // سيتم هنا لاحقاً إضافة كود لإرسال الأوامر لهاتف آخر
    }
}

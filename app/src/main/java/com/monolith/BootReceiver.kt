package com.monolith

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            // تحقق: هل المستخدم مفعّل الفقاعة أصلاً؟
            val prefs = context.getSharedPreferences("Settings", Context.MODE_PRIVATE)
            val bubbleEnabled = prefs.getBoolean("bubble_enabled", false)
            
            if (bubbleEnabled && Settings.canDrawOverlays(context)) {
                val serviceIntent = Intent(context, FloatingService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}

package com.monolith

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast

object OemUtils {
    fun openAutoStartSettings(context: Context) {
        val intents = mutableListOf<Intent>()

        // 1. شاومي (Xiaomi / Redmi / POCO)
        intents.add(Intent().apply {
            component = ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
        })
        // 2. هواوي (Huawei / Honor)
        intents.add(Intent().apply {
            component = ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
        })
        // 3. أوبو (Oppo / Realme)
        intents.add(Intent().apply {
            component = ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")
        })
        // 4. فيفو (Vivo / iQOO)
        intents.add(Intent().apply {
            component = ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")
        })
        // 5. أسوس (Asus)
        intents.add(Intent().apply {
            component = ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.powersaver.PowerSaverSettings")
        })

        var success = false
        for (intent in intents) {
            try {
                if (context.packageManager.resolveActivity(intent, 0) != null) {
                    context.startActivity(intent)
                    success = true
                    break
                }
            } catch (e: Exception) {
                // لو الشركة مش موجودة، جرب اللي بعدها
            }
        }

        // لو كل المحاولات فشلت، نفتح شاشة معلومات التطبيق العادية كخطة بديلة
        if (!success) {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.parse("package:" + context.packageName)
                context.startActivity(intent)
                Toast.makeText(context, "ابحث عن 'التشغيل التلقائي' وفعّله يدوياً", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "لم نتمكن من فتح إعدادات النظام", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

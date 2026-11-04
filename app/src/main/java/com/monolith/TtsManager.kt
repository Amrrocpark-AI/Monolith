package com.monolith

import android.content.Context
import android.speech.tts.TextToSpeech
import android.widget.Toast
import java.util.Locale

class TtsManager(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val appContext = context.applicationContext

    init {
        tts = TextToSpeech(appContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // تعيين اللغة العربية افتراضياً، مع إمكانية التغيير
            val result = tts?.setLanguage(Locale("ar"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // إذا لم تتوفر العربية، استخدم لغة النظام
                tts?.setLanguage(Locale.getDefault())
            }
            isInitialized = true
        } else {
            Toast.makeText(appContext, "محرك الصوت غير مدعوم على هذا الجهاز", Toast.LENGTH_SHORT).show()
        }
    }

    fun speak(text: String) {
        if (isInitialized) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "MonolithTTS")
        } else {
            Toast.makeText(appContext, "جاري تهيئة المحرك الصوتي...", Toast.LENGTH_SHORT).show()
        }
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}

package com.monolith

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import java.util.Locale

class TtsManager(context: Context, private val onDone: () -> Unit) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val appContext = context.applicationContext

    init {
        tts = TextToSpeech(appContext, this)
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                onDone() // العودة لشكل الأيقونة الطبيعي لما الصوت يخلص
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                onDone()
            }
        })
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("ar"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.getDefault())
            }
            isInitialized = true
        }
    }

    fun speak(text: String) {
        if (isInitialized) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "MonolithTTS")
        } else {
            Toast.makeText(appContext, "جاري تهيئة المحرك الصوتي...", Toast.LENGTH_SHORT).show()
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}

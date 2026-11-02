package com.monolith

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.CircularProgressIndicator
import android.widget.TextView
import java.util.concurrent.Executor

class LockScreenActivity : AppCompatActivity() {

    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo

    private lateinit var progressIndicator: CircularProgressIndicator
    private lateinit var tvLockSubtitle: TextView
    private lateinit var cardFingerprint: MaterialCardView
    private lateinit var btnUsePassword: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lock_screen)

        // ربط عناصر الواجهة
        progressIndicator = findViewById(R.id.progressIndicator)
        tvLockSubtitle = findViewById(R.id.tvLockSubtitle)
        cardFingerprint = findViewById(R.id.cardFingerprint)
        btnUsePassword = findViewById(R.id.btnUsePassword)

        // إعداد محرك البصمة
        executor = ContextCompat.getMainExecutor(this)
        setupBiometricPrompt()

        // النقر على الدائرة يبدأ عملية الفحص
        cardFingerprint.setOnClickListener {
            vibratePhone(20) // اهتزاز خفيف جداً (Haptic Feedback)
            startBiometricAuth()
        }

        // زر كلمة المرور (مؤقتاً يعرض رسالة، وسنبنيه لاحقاً)
        btnUsePassword.setOnClickListener {
            Toast.makeText(this, "ميزة كلمة المرور قيد التطوير السيادي...", Toast.LENGTH_SHORT).show()
        }

        // التحقق من توفر البصمة عند فتح الشاشة
        checkBiometricAvailability()
    }

    private fun setupBiometricPrompt() {
        biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    // إخفاء التحميل وإظهار الخطأ
                    progressIndicator.visibility = View.INVISIBLE
                    tvLockSubtitle.text = "حدث خطأ: $errString"
                    tvLockSubtitle.setTextColor(android.graphics.Color.RED)
                    vibratePhone(100)
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    // فشل التعرف (بسبب البلل أو العرق)
                    progressIndicator.visibility = View.INVISIBLE
                    tvLockSubtitle.text = "لم نتمكن من التعرف. جرب مرة أخرى أو استخدم كلمة المرور."
                    tvLockSubtitle.setTextColor(android.graphics.Color.parseColor("#FFAA00")) // لون برتقالي لطيف
                    vibratePhone(50)
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    // نجاح! الانتقال للشاشة الرئيسية
                    progressIndicator.visibility = View.INVISIBLE
                    tvLockSubtitle.text = "تم التحقق بنجاح. جاري الدخول..."
                    tvLockSubtitle.setTextColor(android.graphics.Color.parseColor("#00FF00"))
                    vibratePhone(100)

                    // الانتقال إلى MainActivity
                    startActivity(Intent(this@LockScreenActivity, MainActivity::class.java))
                    finish() // إغلاق شاشة القفل لكي لا يعود إليها المستخدم بزر الرجوع
                }
            })
    }

    private fun startBiometricAuth() {
        // إظهار مؤشر الطاقة الدائري
        progressIndicator.visibility = View.VISIBLE
        tvLockSubtitle.text = "جاري فحص البصمة الحيوية..."
        tvLockSubtitle.setTextColor(android.graphics.Color.parseColor("#00FF00"))

        // إظهار نافذة البصمة الرسمية
        biometricPrompt.authenticate(promptInfo)
    }

    private fun checkBiometricAvailability() {
        val biometricManager = BiometricManager.from(this)
        when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                // البصمة متاحة، في انتظار المستخدم
                tvLockSubtitle.text = "المس الدائرة للتحقق من الهوية"
            }
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                tvLockSubtitle.text = "هذا الهاتف لا يدعم مستشعر البصمة."
                tvLockSubtitle.setTextColor(android.graphics.Color.RED)
            }
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                tvLockSubtitle.text = "مستشعر البصمة غير متاح حالياً."
                tvLockSubtitle.setTextColor(android.graphics.Color.RED)
            }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                tvLockSubtitle.text = "يرجى إضافة بصمة في إعدادات الهاتف أولاً."
                tvLockSubtitle.setTextColor(android.graphics.Color.RED)
            }
        }
    }

    private fun vibratePhone(duration: Long) {
        try {
            val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(duration)
            }
        } catch (e: Exception) {}
    }
}

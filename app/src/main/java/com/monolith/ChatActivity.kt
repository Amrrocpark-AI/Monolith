package com.monolith

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class ChatActivity : AppCompatActivity() {
    
    // متغير لحفظ مسار الصورة التي سيتم اختيارها
    private var selectedImageUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        val etMessage = findViewById<EditText>(R.id.etMessage)
        val btnSend = findViewById<ImageButton>(R.id.btnSend)
        val btnSettings = findViewById<ImageButton>(R.id.btnSettings)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val btnAttach = findViewById<ImageButton>(R.id.btnAttach)
        val btnMic = findViewById<ImageButton>(R.id.btnMic)
        val btnVisionToggle = findViewById<ImageButton>(R.id.btnVisionToggle)
        
        val chatContainer = findViewById<LinearLayout>(R.id.chatContainer)
        val chatScrollView = findViewById<android.widget.ScrollView>(R.id.chatScrollView)
        
        // عناصر معاينة الصورة
        val imagePreviewContainer = findViewById<LinearLayout>(R.id.imagePreviewContainer)
        val tvImagePreviewText = findViewById<TextView>(R.id.tvImagePreviewText)
        val btnRemoveImage = findViewById<ImageButton>(R.id.btnRemoveImage)

        // 1. برمجة زر الإرفاق لفتح المعرض
        val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                selectedImageUri = uri
                // إظهار شريط المعاينة
                imagePreviewContainer.visibility = LinearLayout.VISIBLE
                tvImagePreviewText.text = "📎 صورة مرفقة جاهزة للإرسال"
            }
        }

        btnAttach.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        // 2. إزالة الصورة المرفقة
        btnRemoveImage.setOnClickListener {
            selectedImageUri = null
            imagePreviewContainer.visibility = LinearLayout.GONE
        }

        // 3. زر الرجوع
        btnBack.setOnClickListener { finish() }

        // 4. زر الإعدادات
        btnSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        // 5. أزرار أخرى (مؤقتاً)
        btnMic.setOnClickListener { Toast.makeText(this, "تسجيل الصوت قريباً", Toast.LENGTH_SHORT).show() }
        btnVisionToggle.setOnClickListener { Toast.makeText(this, "الرؤية الحاسوبية قريباً", Toast.LENGTH_SHORT).show() }

        // 6. إرسال الرسالة
        fun sendMessage() {
            val message = etMessage.text.toString().trim()
            if (message.isNotEmpty() || selectedImageUri != null) {
                if (selectedImageUri != null) {
                    addChatBubble(chatContainer, "📷 [صورة مرفقة] $message", isUser = true)
                    selectedImageUri = null
                    imagePreviewContainer.visibility = LinearLayout.GONE
                } else {
                    addChatBubble(chatContainer, message, isUser = true)
                }
                
                etMessage.text.clear()
                addChatBubble(chatContainer, "جاري الاتصال بالنموذج المحلي...", isUser = false)
                chatScrollView.post { chatScrollView.fullScroll(android.widget.ScrollView.FOCUS_DOWN) }
            }
        }

        btnSend.setOnClickListener { sendMessage() }

        etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else {
                false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val sharedPreferences = getSharedPreferences("Settings", MODE_PRIVATE)
        val rootLayout = findViewById<LinearLayout>(R.id.rootLayout)

        val imageUriString = sharedPreferences.getString("AppBackgroundImage", null)
        
        if (imageUriString != null) {
            try {
                val uri = Uri.parse(imageUriString)
                val inputStream = contentResolver.openInputStream(uri)
                val options = BitmapFactory.Options()
                options.inSampleSize = 2 
                val bitmap = BitmapFactory.decodeStream(inputStream, null, options)
                rootLayout.background = BitmapDrawable(resources, bitmap)
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    rootLayout.foreground = android.graphics.drawable.ColorDrawable(Color.parseColor("#99000000"))
                }
            } catch (e: Exception) {
                applyColorBackground(sharedPreferences, rootLayout)
            }
        } else {
            applyColorBackground(sharedPreferences, rootLayout)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                rootLayout.foreground = null
            }
        }

        val savedColor = sharedPreferences.getString("AppBackground", "#0A0A0A") ?: "#0A0A0A"
        val etMessage = findViewById<EditText>(R.id.etMessage)
        
        // إصلاح ألوان الأيقونات بناءً على الخلفية
        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val btnSettings = findViewById<ImageButton>(R.id.btnSettings)
        val btnVisionToggle = findViewById<ImageButton>(R.id.btnVisionToggle)
        
        if (savedColor == "#FFFFFF" && imageUriString == null) {
            // في الوضع الفاتح: اجعل النصوص والأيقونات سوداء
            etMessage.setTextColor(Color.BLACK)
            etMessage.setHintTextColor(Color.GRAY)
            btnBack.setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN)
            btnSettings.setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN)
            btnVisionToggle.setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN)
        } else {
            // في الوضع الداكن أو مع الصورة: اجعل النصوص والأيقونات بيضاء
            etMessage.setTextColor(Color.WHITE)
            etMessage.setHintTextColor(Color.parseColor("#666666"))
            btnBack.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
            btnSettings.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
            btnVisionToggle.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
        }
    }

    private fun applyColorBackground(sharedPreferences: android.content.SharedPreferences, rootLayout: LinearLayout) {
        val savedColor = sharedPreferences.getString("AppBackground", "#0A0A0A") ?: "#0A0A0A"
        rootLayout.setBackgroundColor(Color.parseColor(savedColor))
    }

    private fun addChatBubble(container: LinearLayout, text: String, isUser: Boolean) {
        val textView = TextView(this)
        textView.text = text
        textView.textSize = 16f
        
        textView.setTextColor(if (isUser) Color.BLACK else Color.WHITE)
        textView.setPadding(32, 24, 32, 24)
        
        textView.textDirection = if (isUser) TextView.TEXT_DIRECTION_RTL else TextView.TEXT_DIRECTION_LOCALE
        textView.textAlignment = TextView.TEXT_ALIGNMENT_CENTER

        val background = GradientDrawable()
        background.setColor(if (isUser) Color.parseColor("#00FF00") else Color.parseColor("#CC2A2A2A"))
        background.cornerRadius = 40f
        textView.background = background

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        
        if (isUser) {
            params.gravity = Gravity.END
            params.setMargins(80, 12, 0, 12)
        } else {
            params.gravity = Gravity.START
            params.setMargins(0, 12, 80, 12)
        }
        
        textView.layoutParams = params
        container.addView(textView)
    }
}

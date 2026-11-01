package com.monolith

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog

class ChatActivity : AppCompatActivity() {
    
    private var selectedImageUri: Uri? = null
    private lateinit var etMessage: EditText
    private lateinit var btnSend: TextView
    private lateinit var imagePreviewContainer: LinearLayout
    private lateinit var ivPreviewImage: ImageView
    private lateinit var tvImagePreviewText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        etMessage = findViewById(R.id.etMessage)
        btnSend = findViewById(R.id.btnSend)
        val btnSettings = findViewById<TextView>(R.id.btnSettings)
        val btnBack = findViewById<TextView>(R.id.btnBack)
        val btnAttach = findViewById<TextView>(R.id.btnAttach)
        val btnMic = findViewById<TextView>(R.id.btnMic)
        val btnVisionToggle = findViewById<TextView>(R.id.btnVisionToggle)
        
        val chatContainer = findViewById<LinearLayout>(R.id.chatContainer)
        val chatScrollView = findViewById<android.widget.ScrollView>(R.id.chatScrollView)
        
        imagePreviewContainer = findViewById(R.id.imagePreviewContainer)
        ivPreviewImage = findViewById(R.id.ivPreviewImage)
        tvImagePreviewText = findViewById(R.id.tvImagePreviewText)
        val btnRemoveImage = findViewById<TextView>(R.id.btnRemoveImage)

        // استعادة الصورة إذا تم تدوير الشاشة
        if (savedInstanceState != null) {
            val savedUriString = savedInstanceState.getString("savedImageUri")
            if (savedUriString != null) {
                selectedImageUri = Uri.parse(savedUriString)
                showImagePreview(selectedImageUri!!)
            }
        }

        // تعطيل زر الإرسال في البداية
        btnSend.isEnabled = false
        btnSend.alpha = 0.5f

        // مراقبة الكتابة لتفعيل/تعطيل زر الإرسال
        etMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val hasText = !s.isNullOrEmpty()
                val hasImage = selectedImageUri != null
                btnSend.isEnabled = hasText || hasImage
                btnSend.alpha = if (hasText || hasImage) 1.0f else 0.5f
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnAttach.setOnClickListener {
            hideKeyboard()
            showAttachBottomSheet()
        }

        btnRemoveImage.setOnClickListener {
            selectedImageUri = null
            imagePreviewContainer.visibility = LinearLayout.GONE
            ivPreviewImage.setImageDrawable(null)
            btnSend.isEnabled = etMessage.text.isNotEmpty()
            btnSend.alpha = if (btnSend.isEnabled) 1.0f else 0.5f
        }

        btnBack.setOnClickListener { finish() }

        btnSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        btnMic.setOnClickListener { Toast.makeText(this, "تسجيل الصوت قريباً", Toast.LENGTH_SHORT).show() }
        btnVisionToggle.setOnClickListener { Toast.makeText(this, "الرؤية الحاسوبية قريباً", Toast.LENGTH_SHORT).show() }

        fun sendMessage() {
            val message = etMessage.text.toString().trim()
            if (message.isNotEmpty() || selectedImageUri != null) {
                vibratePhone()
                if (selectedImageUri != null) {
                    addChatBubble(chatContainer, "📷 [صورة مرفقة] $message", isUser = true)
                    selectedImageUri = null
                    imagePreviewContainer.visibility = LinearLayout.GONE
                    ivPreviewImage.setImageDrawable(null)
                } else {
                    addChatBubble(chatContainer, message, isUser = true)
                }
                
                etMessage.text.clear()
                hideKeyboard()
                
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("savedImageUri", selectedImageUri?.toString())
    }

    private fun showImagePreview(uri: Uri) {
        imagePreviewContainer.visibility = LinearLayout.VISIBLE
        ivPreviewImage.setImageURI(uri)
        tvImagePreviewText.text = "صورة جاهزة للإرسال"
        btnSend.isEnabled = true
        btnSend.alpha = 1.0f
    }

    private fun showAttachBottomSheet() {
        val bottomSheetDialog = BottomSheetDialog(this)
        val bottomSheetView = layoutInflater.inflate(R.layout.bottom_sheet_attach, null)
        bottomSheetDialog.setContentView(bottomSheetView)

        val btnOptionImage = bottomSheetView.findViewById<TextView>(R.id.btnOptionImage)
        val btnOptionDocument = bottomSheetView.findViewById<TextView>(R.id.btnOptionDocument)
        val btnOptionAudio = bottomSheetView.findViewById<TextView>(R.id.btnOptionAudio)
        val btnOptionSearch = bottomSheetView.findViewById<TextView>(R.id.btnOptionSearch)

        btnOptionImage.setOnClickListener {
            bottomSheetDialog.dismiss()
            val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
                if (uri != null) {
                    selectedImageUri = uri
                    showImagePreview(uri)
                }
            }
            pickImageLauncher.launch("image/*")
        }

        btnOptionDocument.setOnClickListener {
            bottomSheetDialog.dismiss()
            Toast.makeText(this, "قريباً: اختيار ملفات TXT و PDF", Toast.LENGTH_SHORT).show()
        }

        btnOptionAudio.setOnClickListener {
            bottomSheetDialog.dismiss()
            Toast.makeText(this, "قريباً: اختيار ملفات صوتية", Toast.LENGTH_SHORT).show()
        }

        btnOptionSearch.setOnClickListener {
            bottomSheetDialog.dismiss()
            Toast.makeText(this, "قريباً: البحث الشامل في الملفات", Toast.LENGTH_SHORT).show()
        }

        bottomSheetDialog.show()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(etMessage.windowToken, 0)
    }

    private fun vibratePhone() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(40)
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
        
        val btnBack = findViewById<TextView>(R.id.btnBack)
        val btnSettings = findViewById<TextView>(R.id.btnSettings)
        val btnVisionToggle = findViewById<TextView>(R.id.btnVisionToggle)
        
        if (savedColor == "#FFFFFF" && imageUriString == null) {
            etMessage.setTextColor(Color.BLACK)
            etMessage.setHintTextColor(Color.GRAY)
            btnBack.setTextColor(Color.BLACK)
            btnSettings.setTextColor(Color.BLACK)
            btnVisionToggle.setTextColor(Color.BLACK)
        } else {
            etMessage.setTextColor(Color.WHITE)
            etMessage.setHintTextColor(Color.parseColor("#666666"))
            btnBack.setTextColor(Color.WHITE)
            btnSettings.setTextColor(Color.WHITE)
            btnVisionToggle.setTextColor(Color.WHITE)
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

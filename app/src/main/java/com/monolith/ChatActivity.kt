package com.monolith

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PorterDuff
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
import android.view.ViewGroup
import android.view.Window
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ChatActivity : AppCompatActivity() {
    
    private val selectedImageUris = mutableListOf<Uri>()
    
    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var imagePreviewContainer: LinearLayout
    private lateinit var ivPreviewImage: ImageView
    private lateinit var chatContainer: LinearLayout
    private lateinit var chatScrollView: android.widget.ScrollView
    private lateinit var tvAppTitle: TextView
    
    private var themeColorHex = "#00FF00"

    private lateinit var sharedPreferences: SharedPreferences
    private val gson = Gson()
    private var messageList: MutableList<ChatMessage> = mutableListOf()

    private val pickImagesLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            selectedImageUris.clear()
            selectedImageUris.addAll(uris)
            showImagePreview(uris)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        etMessage = findViewById(R.id.etMessage)
        btnSend = findViewById(R.id.btnSend)
        val btnSettings = findViewById<ImageButton>(R.id.btnSettings)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val btnAttach = findViewById<ImageButton>(R.id.btnAttach)
        val btnMic = findViewById<ImageButton>(R.id.btnMic)
        val btnVisionToggle = findViewById<ImageButton>(R.id.btnVisionToggle)
        tvAppTitle = findViewById(R.id.tvAppTitle)
        
        chatContainer = findViewById(R.id.chatContainer)
        chatScrollView = findViewById(R.id.chatScrollView)
        
        imagePreviewContainer = findViewById(R.id.imagePreviewContainer)
        ivPreviewImage = findViewById(R.id.ivPreviewImage)
        val btnRemoveImage = findViewById<TextView>(R.id.btnRemoveImage)

        sharedPreferences = getSharedPreferences("ChatHistory", MODE_PRIVATE)
        messageList = loadMessages()

        for (msg in messageList) {
            addChatBubble(chatContainer, msg)
        }
        chatScrollView.post { chatScrollView.fullScroll(android.widget.ScrollView.FOCUS_DOWN) }

        btnSend.isEnabled = false
        btnSend.alpha = 0.5f

        etMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val hasText = !s.isNullOrEmpty()
                val hasImage = selectedImageUris.isNotEmpty()
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
            selectedImageUris.clear()
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
            try {
                val message = etMessage.text.toString().trim()
                if (message.isNotEmpty() || selectedImageUris.isNotEmpty()) {
                    
                    vibratePhone() 
                    
                    val userMsg = ChatMessage(
                        role = MessageRole.USER,
                        content = message, 
                        attachmentUris = selectedImageUris.map { it.toString() }, 
                        colorHex = themeColorHex 
                    )
                    messageList.add(userMsg)
                    saveMessages()
                    addChatBubble(chatContainer, userMsg)

                    selectedImageUris.clear()
                    imagePreviewContainer.visibility = LinearLayout.GONE
                    ivPreviewImage.setImageDrawable(null)
                    etMessage.text.clear()
                    hideKeyboard()
                    
                    val aiMsg = ChatMessage(role = MessageRole.AI, content = "جاري الاتصال بالنموذج المحلي...", colorHex = "#2A2A2A")
                    messageList.add(aiMsg)
                    saveMessages()
                    addChatBubble(chatContainer, aiMsg)
                    
                    chatScrollView.post { chatScrollView.fullScroll(android.widget.ScrollView.FOCUS_DOWN) }
                }
            } catch (e: Exception) {
                Toast.makeText(this, "حدث خطأ: ${e.message}", Toast.LENGTH_LONG).show()
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

    // دالة مساعدة لتحويل البكسل إلى dp (لضمان حجم مناسب على كل الشاشات)
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun showFullScreenImage(uri: Uri) {
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val imageView = ImageView(this)
        imageView.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        imageView.scaleType = ImageView.ScaleType.FIT_CENTER
        imageView.setImageURI(uri)
        imageView.setOnClickListener { dialog.dismiss() } // إغلاق عند الضغط
        dialog.setContentView(imageView)
        dialog.show()
    }

    private fun saveMessages() {
        try {
            val json = gson.toJson(messageList)
            sharedPreferences.edit().putString("messages", json).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadMessages(): MutableList<ChatMessage> {
        val json = sharedPreferences.getString("messages", null)
        return if (json != null) {
            try {
                val type = object : TypeToken<MutableList<ChatMessage>>() {}.type
                gson.fromJson<MutableList<ChatMessage>>(json, type) ?: mutableListOf()
            } catch (e: Exception) {
                mutableListOf()
            }
        } else {
            mutableListOf()
        }
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
            try {
                pickImagesLauncher.launch("image/*")
            } catch (e: Exception) {
                Toast.makeText(this, "لا يمكن فتح المعرض", Toast.LENGTH_SHORT).show()
            }
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

    private fun showImagePreview(uris: List<Uri>) {
        try {
            imagePreviewContainer.visibility = LinearLayout.VISIBLE
            ivPreviewImage.setImageURI(uris[0]) 
            btnSend.isEnabled = true
            btnSend.alpha = 1.0f
        } catch (e: Exception) {
            Toast.makeText(this, "تعذر تحميل الصورة", Toast.LENGTH_SHORT).show()
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(etMessage.windowToken, 0)
    }

    private fun vibratePhone() {
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(40)
            }
        } catch (e: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        try {
            val settingsPrefs = getSharedPreferences("Settings", MODE_PRIVATE)
            val rootLayout = findViewById<LinearLayout>(R.id.rootLayout)

            themeColorHex = settingsPrefs.getString("AppThemeColor", "#00FF00") ?: "#00FF00"
            val themeColor = Color.parseColor(themeColorHex)
            
            tvAppTitle.setTextColor(themeColor)
            btnSend.setColorFilter(themeColor, PorterDuff.Mode.SRC_IN)
            
            val btnAttach = findViewById<ImageButton>(R.id.btnAttach)
            btnAttach.setColorFilter(themeColor, PorterDuff.Mode.SRC_IN)

            val imageUriString = settingsPrefs.getString("AppBackgroundImage", null)
            
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
                    applyColorBackground(settingsPrefs, rootLayout)
                }
            } else {
                applyColorBackground(settingsPrefs, rootLayout)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    rootLayout.foreground = null
                }
            }

            val savedColor = settingsPrefs.getString("AppBackground", "#0A0A0A") ?: "#0A0A0A"
            val btnBack = findViewById<ImageButton>(R.id.btnBack)
            val btnSettings = findViewById<ImageButton>(R.id.btnSettings)
            val btnVisionToggle = findViewById<ImageButton>(R.id.btnVisionToggle)
            
            if (savedColor == "#FFFFFF" && imageUriString == null) {
                etMessage.setTextColor(Color.BLACK)
                etMessage.setHintTextColor(Color.GRAY)
                btnBack.setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN)
                btnSettings.setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN)
                btnVisionToggle.setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN)
            } else {
                etMessage.setTextColor(Color.WHITE)
                etMessage.setHintTextColor(Color.parseColor("#666666"))
                btnBack.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
                btnSettings.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
                btnVisionToggle.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun applyColorBackground(sharedPreferences: SharedPreferences, rootLayout: LinearLayout) {
        val savedColor = sharedPreferences.getString("AppBackground", "#0A0A0A") ?: "#0A0A0A"
        rootLayout.setBackgroundColor(Color.parseColor(savedColor))
    }

    private fun addChatBubble(container: LinearLayout, msg: ChatMessage) {
        val bubbleLayout = LinearLayout(this)
        bubbleLayout.orientation = LinearLayout.VERTICAL
        bubbleLayout.setPadding(32, 24, 32, 24)
        
        val background = GradientDrawable()
        background.setColor(Color.parseColor(msg.colorHex))
        background.cornerRadius = 40f
        bubbleLayout.background = background

        if (!msg.attachmentUris.isNullOrEmpty()) {
            val scrollView = android.widget.HorizontalScrollView(this)
            val imageContainer = LinearLayout(this)
            imageContainer.orientation = LinearLayout.HORIZONTAL
            
            // تحويل 150dp إلى بكسل لضمان حجم مثالي على الشاشة
            val imageSizePx = dpToPx(150)
            
            for (uriString in msg.attachmentUris) {
                val imageView = ImageView(this)
                val params = LinearLayout.LayoutParams(imageSizePx, imageSizePx) 
                params.setMargins(0, 0, 16, 16)
                imageView.layoutParams = params
                imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                
                try {
                    val imageUri = Uri.parse(uriString)
                    imageView.setImageURI(imageUri)
                    // إضافة ميزة الضغط للتكبير
                    imageView.setOnClickListener { showFullScreenImage(imageUri) }
                } catch (e: Exception) {}
                
                imageContainer.addView(imageView)
            }
            scrollView.addView(imageContainer)
            bubbleLayout.addView(scrollView)
        }

        if (msg.content.isNotEmpty()) {
            val textView = TextView(this)
            textView.text = msg.content
            textView.textSize = 16f
            textView.setTextColor(if (msg.role == MessageRole.USER) Color.BLACK else Color.WHITE)
            
            textView.textDirection = if (msg.role == MessageRole.USER) TextView.TEXT_DIRECTION_RTL else TextView.TEXT_DIRECTION_LOCALE
            textView.textAlignment = TextView.TEXT_ALIGNMENT_VIEW_START
            
            bubbleLayout.addView(textView)
        }

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        
        if (msg.role == MessageRole.USER) {
            params.gravity = Gravity.END
            params.setMargins(80, 12, 0, 12)
        } else {
            params.gravity = Gravity.START
            params.setMargins(0, 12, 80, 12)
        }
        
        bubbleLayout.layoutParams = params
        container.addView(bubbleLayout)
    }
}

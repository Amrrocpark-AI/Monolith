package com.monolith

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
import androidx.appcompat.widget.AppCompatTextView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ChatActivity : AppCompatActivity() {

    private val selectedImageUris = mutableListOf<Uri>()

    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var imagePreviewContainer: android.widget.HorizontalScrollView
    private lateinit var previewListContainer: LinearLayout
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
            updatePreviewUi()
        }
    }

    private val imageViewerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            val newImageUri = data?.getStringExtra("new_image_uri")
            val msgId = data?.getStringExtra("message_id")
            val imgIndex = data?.getIntExtra("image_index", -1) ?: -1

            if (newImageUri != null && msgId != null && imgIndex != -1) {
                val msgIndex = messageList.indexOfFirst { it.id == msgId }
                if (msgIndex != -1) {
                    val msg = messageList[msgIndex]
                    val newUris = msg.attachmentUris?.toMutableList()

                    if (newUris != null && imgIndex < newUris.size) {
                        newUris[imgIndex] = newImageUri
                        val updatedMsg = msg.copy(attachmentUris = newUris)
                        messageList[msgIndex] = updatedMsg
                        saveMessages()

                        chatContainer.removeAllViews()
                        for (m in messageList) {
                            addChatBubble(chatContainer, m)
                        }
                        chatScrollView.post { chatScrollView.fullScroll(android.widget.ScrollView.FOCUS_DOWN) }
                    }
                }
            }
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
        previewListContainer = findViewById(R.id.previewListContainer)

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
                    updatePreviewUi()
                    etMessage.text.clear()
                    hideKeyboard()

                    // ==========================================
                    // بداية الاتصال بالذكاء الاصطناعي (الإضافة الجديدة)
                    // ==========================================
                    
                    val aiMsg = ChatMessage(role = MessageRole.AI, content = "جاري التفكير...", colorHex = "#2A2A2A")
                    messageList.add(aiMsg)
                    val aiMsgIndex = messageList.size - 1 // تثبيت مكان الرسالة
                    saveMessages()
                    addChatBubble(chatContainer, aiMsg)
                    
                    chatScrollView.post { chatScrollView.fullScroll(android.widget.ScrollView.FOCUS_DOWN) }

                    // تجهيز الطلب
                    val request = ChatRequest(
                        model = "llama3-8b-8192", 
                        messages = listOf(ChatMessage(role = MessageRole.USER, content = message))
                    )

                    // إرسال الطلب (نستخدم emptyMap لأن ApiClient سيقوم بحقن المفتاح تلقائياً)
                    ApiClient.service.sendMessage(
                        url = "v1/chat/completions", 
                        headers = emptyMap(), 
                        request = request
                    ).enqueue(object : Callback<ChatResponse> {
                        override fun onResponse(call: Call<ChatResponse>, response: Response<ChatResponse>) {
                            runOnUiThread {
                                val reply = if (response.isSuccessful) {
                                    response.body()?.choices?.firstOrNull()?.message?.content ?: "لم يتم استلام رد."
                                } else {
                                    "خطأ من الخادم: ${response.code()}"
                                }
                                
                                val updatedMsg = aiMsg.copy(content = reply)
                                messageList[aiMsgIndex] = updatedMsg
                                saveMessages()
                                
                                chatContainer.removeAllViews()
                                for (m in messageList) { addChatBubble(chatContainer, m) }
                                chatScrollView.post { chatScrollView.fullScroll(android.widget.ScrollView.FOCUS_DOWN) }
                            }
                        }

                        override fun onFailure(call: Call<ChatResponse>, t: Throwable) {
                            runOnUiThread {
                                val errorMsg = aiMsg.copy(content = "فشل الاتصال: ${t.message}")
                                messageList[aiMsgIndex] = errorMsg
                                saveMessages()
                                
                                chatContainer.removeAllViews()
                                for (m in messageList) { addChatBubble(chatContainer, m) }
                            }
                        }
                    })
                    // ==========================================
                    // نهاية الإضافة
                    // ==========================================

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

    private fun updatePreviewUi() {
        previewListContainer.removeAllViews()

        if (selectedImageUris.isEmpty()) {
            imagePreviewContainer.visibility = LinearLayout.GONE
            btnSend.isEnabled = etMessage.text.isNotEmpty()
            btnSend.alpha = if (btnSend.isEnabled) 1.0f else 0.5f
        } else {
            imagePreviewContainer.visibility = LinearLayout.VISIBLE
            btnSend.isEnabled = true
            btnSend.alpha = 1.0f

            for ((index, uri) in selectedImageUris.withIndex()) {
                val cardView = MaterialCardView(this)
                val cardParams = LinearLayout.LayoutParams(dpToPx(50), dpToPx(50))
                cardParams.setMargins(0, 0, dpToPx(8), 0)
                cardView.layoutParams = cardParams
                cardView.radius = dpToPx(8).toFloat()
                cardView.setCardElevation(0f)

                val relativeLayout = android.widget.RelativeLayout(this)
                relativeLayout.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

                val imageView = ImageView(this)
                imageView.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                imageView.setImageURI(uri)
                relativeLayout.addView(imageView)

                val btnRemove = AppCompatTextView(this)
                val btnParams = android.widget.RelativeLayout.LayoutParams(dpToPx(18), dpToPx(18))
                btnParams.addRule(android.widget.RelativeLayout.ALIGN_PARENT_TOP)
                btnParams.addRule(android.widget.RelativeLayout.ALIGN_PARENT_END)
                btnParams.setMargins(0, dpToPx(2), dpToPx(2), 0)
                btnRemove.layoutParams = btnParams
                btnRemove.text = "X"
                btnRemove.setTextColor(Color.WHITE)
                btnRemove.textSize = 10f
                btnRemove.gravity = Gravity.CENTER
                btnRemove.setBackgroundResource(R.drawable.bg_circle_dark)

                btnRemove.setOnClickListener {
                    selectedImageUris.removeAt(index)
                    updatePreviewUi()
                }

                relativeLayout.addView(btnRemove)
                cardView.addView(relativeLayout)
                previewListContainer.addView(cardView)
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
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

        bottomSheetView.findViewById<LinearLayout>(R.id.btnOptionImage)?.setOnClickListener {
            bottomSheetDialog.dismiss()
            pickImagesLauncher.launch("image/*")
        }
        bottomSheetView.findViewById<LinearLayout>(R.id.btnOptionDocument)?.setOnClickListener {
            bottomSheetDialog.dismiss()
            Toast.makeText(this, "قريباً: اختيار ملفات PDF و TXT", Toast.LENGTH_SHORT).show()
        }
        bottomSheetView.findViewById<LinearLayout>(R.id.btnOptionAudio)?.setOnClickListener {
            bottomSheetDialog.dismiss()
            Toast.makeText(this, "قريباً: اختيار ملفات صوتية", Toast.LENGTH_SHORT).show()
        }
        bottomSheetView.findViewById<LinearLayout>(R.id.btnOptionSearch)?.setOnClickListener {
            bottomSheetDialog.dismiss()
            Toast.makeText(this, "قريباً: البحث الشامل في الملفات", Toast.LENGTH_SHORT).show()
        }
        bottomSheetView.findViewById<LinearLayout>(R.id.btnOptionCamera)?.setOnClickListener {
            bottomSheetDialog.dismiss()
            Toast.makeText(this, "قريباً: التقاط صورة من الكاميرا", Toast.LENGTH_SHORT).show()
        }
        bottomSheetView.findViewById<LinearLayout>(R.id.btnOptionVision)?.setOnClickListener {
            bottomSheetDialog.dismiss()
            Toast.makeText(this, "قريباً: تحليل الصور بالذكاء الاصطناعي", Toast.LENGTH_SHORT).show()
        }

        bottomSheetDialog.show()
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
        val messageLayout = LinearLayout(this)
        messageLayout.orientation = LinearLayout.VERTICAL
        messageLayout.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        if (!msg.attachmentUris.isNullOrEmpty()) {
            val scrollView = android.widget.HorizontalScrollView(this)
            val imageContainer = LinearLayout(this)
            imageContainer.orientation = LinearLayout.HORIZONTAL

            val imageSizePx = dpToPx(180)

            for ((index, uriString) in msg.attachmentUris.withIndex()) {
                val imageView = ImageView(this)
                val params = LinearLayout.LayoutParams(imageSizePx, imageSizePx)
                params.setMargins(0, 0, 16, 16)
                imageView.layoutParams = params
                imageView.scaleType = ImageView.ScaleType.CENTER_CROP

                val clip = GradientDrawable()
                clip.cornerRadius = dpToPx(16).toFloat()
                clip.setColor(Color.TRANSPARENT)
                imageView.background = clip
                imageView.clipToOutline = true

                try {
                    val imageUri = Uri.parse(uriString)
                    imageView.setImageURI(imageUri)

                    imageView.setOnClickListener {
                        val intent = Intent(this, ImageViewerActivity::class.java)
                        intent.putExtra("image_uri", uriString)
                        intent.putExtra("message_id", msg.id)
                        intent.putExtra("image_index", index)
                        imageViewerLauncher.launch(intent)
                    }
                } catch (e: Exception) {}

                imageContainer.addView(imageView)
            }
            scrollView.addView(imageContainer)
            messageLayout.addView(scrollView)
        }

        if (msg.content.isNotEmpty()) {
            val textBubble = LinearLayout(this)
            textBubble.setPadding(32, 24, 32, 24)

            val background = GradientDrawable()
            background.setColor(Color.parseColor(msg.colorHex))
            background.cornerRadius = 40f
            textBubble.background = background

            val textView = TextView(this)
            textView.text = msg.content
            textView.textSize = 16f
            textView.setTextColor(if (msg.role == MessageRole.USER) Color.BLACK else Color.WHITE)

            textView.textDirection = if (msg.role == MessageRole.USER) TextView.TEXT_DIRECTION_RTL else TextView.TEXT_DIRECTION_LOCALE
            textView.textAlignment = TextView.TEXT_ALIGNMENT_VIEW_START

            textBubble.addView(textView)
            messageLayout.addView(textBubble)
        }

        if (msg.role == MessageRole.USER) {
            messageLayout.gravity = Gravity.END
            messageLayout.setPadding(0, dpToPx(8), 0, dpToPx(8))
        } else {
            messageLayout.gravity = Gravity.START
            messageLayout.setPadding(0, dpToPx(8), 0, dpToPx(8))
        }

        container.addView(messageLayout)
    }
}

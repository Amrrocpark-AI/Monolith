package com.monolith

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.ActionMode
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatTextView
import androidx.lifecycle.lifecycleScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.InputStream

class ChatActivity : AppCompatActivity() {

    private val selectedImageUris = mutableListOf<Uri>()
    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var imagePreviewContainer: android.widget.HorizontalScrollView
    private lateinit var previewListContainer: LinearLayout
    private lateinit var chatContainer: LinearLayout
    private lateinit var chatScrollView: android.widget.ScrollView
    private lateinit var tvAppTitle: TextView

    private var themeColorHex = "#00D4FF"
    private lateinit var securePrefs: androidx.security.crypto.EncryptedSharedPreferences
    private lateinit var messageDao: MessageDao
    private var messageList: MutableList<ChatMessage> = mutableListOf()
    private val currentConversationId = "main_chat" 
    
    private lateinit var ttsManager: TtsManager
    private var currentPlayingIcon: ImageView? = null

    private val pickImagesLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            selectedImageUris.addAll(uris)
            updatePreviewUi()
        }
    }

    private val pickDocumentLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                val mimeType = contentResolver.getType(uri)
                if (mimeType != null && (mimeType.startsWith("text/") || mimeType.contains("json"))) {
                    val inputStream: InputStream? = contentResolver.openInputStream(uri)
                    val text = inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                    if (text.isNotEmpty()) {
                        etMessage.setText("محتوى الملف:\n$text")
                        Toast.makeText(this, "تم قراءة الملف النصي!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "يدعم التطبيق الملفات النصية فقط حالياً.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "حدث خطأ أثناء قراءة الملف", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        // *** هنا التعديل اللي كان ناقص (استقبال الإشارة) ***
        ttsManager = TtsManager(this) {
            runOnUiThread {
                currentPlayingIcon?.setImageResource(R.drawable.ic_volume_up)
                currentPlayingIcon?.setColorFilter(Color.parseColor("#88FFFFFF"))
                currentPlayingIcon = null
            }
        }

        ApiClient.init(applicationContext)
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(applicationContext)
                messageDao = db.messageDao()
                withContext(Dispatchers.Main) {
                    loadMessagesFromDb()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val masterKey = MasterKey.Builder(this).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        securePrefs = EncryptedSharedPreferences.create(
            this, "SecureSettings", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        ) as androidx.security.crypto.EncryptedSharedPreferences

        etMessage = findViewById(R.id.etMessage)
        btnSend = findViewById(R.id.btnSend)
        val btnSettings = findViewById<ImageButton>(R.id.btnSettings)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val btnAttach = findViewById<ImageButton>(R.id.btnAttach)
        tvAppTitle = findViewById(R.id.tvAppTitle)

        chatContainer = findViewById(R.id.chatContainer)
        chatScrollView = findViewById(R.id.chatScrollView)
        imagePreviewContainer = findViewById(R.id.imagePreviewContainer)
        previewListContainer = findViewById(R.id.previewListContainer)

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
        btnSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }

        btnSend.setOnClickListener { sendMessage() }
        etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) { sendMessage(); true } else { false }
        }
    }
    
    override fun onDestroy() {
        if (::ttsManager.isInitialized) {
            ttsManager.shutdown()
        }
        super.onDestroy()
    }

    private fun sendMessage() {
        try {
            val message = etMessage.text.toString().trim()
            if (message.isNotEmpty() || selectedImageUris.isNotEmpty()) {
                
                val userMsg = ChatMessage(role = MessageRole.USER, content = message, attachmentUris = selectedImageUris.map { it.toString() }, colorHex = themeColorHex)
                messageList.add(userMsg)
                saveMessageToDb(userMsg)
                addChatBubble(chatContainer, userMsg)

                selectedImageUris.clear()
                updatePreviewUi()
                etMessage.text.clear()
                hideKeyboard()
                
                chatScrollView.post { chatScrollView.fullScroll(android.widget.ScrollView.FOCUS_DOWN) }

                val apiKey = securePrefs.getString("api_key", null)
                if (apiKey.isNullOrEmpty()) {
                    showSetupDialog()
                    return
                }

                val aiMsg = ChatMessage(role = MessageRole.AI, content = "جاري التفكير...", colorHex = "#2A2A2A")
                messageList.add(aiMsg)
                val aiMsgIndex = messageList.size - 1 
                addChatBubble(chatContainer, aiMsg)
                
                chatScrollView.post { chatScrollView.fullScroll(android.widget.ScrollView.FOCUS_DOWN) }

                val apiMessages = mutableListOf<Message>()
                apiMessages.add(Message(role = "system", content = "أنت Monolith، ذكاء اصطناعي سيادي متقدم."))
                val recentHistory = messageList.takeLast(15)
                for (msg in recentHistory) {
                    if (msg.role == MessageRole.USER) apiMessages.add(Message(role = "user", content = msg.content))
                    else if (msg.role == MessageRole.AI && msg.content != "جاري التفكير...") apiMessages.add(Message(role = "assistant", content = msg.content))
                }

                val request = ChatRequest(model = "llama3-8b-8192", messages = apiMessages, temperature = 0.7f, maxTokens = 2048)

                ApiClient.service.sendMessage(url = "v1/chat/completions", headers = emptyMap(), request = request).enqueue(object : Callback<ChatResponse> {
                    override fun onResponse(call: Call<ChatResponse>, response: Response<ChatResponse>) {
                        runOnUiThread {
                            if (response.isSuccessful) {
                                val reply = response.body()?.choices?.firstOrNull()?.message?.content ?: "لم يتم استلام رد."
                                val updatedMsg = aiMsg.copy(content = reply)
                                messageList[aiMsgIndex] = updatedMsg
                                saveMessageToDb(updatedMsg)
                                
                                chatContainer.removeAllViews()
                                for (m in messageList) { addChatBubble(chatContainer, m) }
                                chatScrollView.post { chatScrollView.fullScroll(android.widget.ScrollView.FOCUS_DOWN) }
                            } else {
                                handleApiError(aiMsgIndex, "خطأ: ${response.code()}")
                            }
                        }
                    }
                    override fun onFailure(call: Call<ChatResponse>, t: Throwable) {
                        runOnUiThread { handleApiError(aiMsgIndex, t.message ?: "خطأ غير معروف") }
                    }
                })
            }
        } catch (e: Exception) {
            Toast.makeText(this, "حدث خطأ: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadMessagesFromDb() {
        lifecycleScope.launch {
            try {
                val entities = messageDao.getMessagesForConversation(currentConversationId)
                messageList = entities.map { it.toChatMessage() }.toMutableList()
                withContext(Dispatchers.Main) {
                    chatContainer.removeAllViews()
                    for (msg in messageList) { addChatBubble(chatContainer, msg) }
                    chatScrollView.post { chatScrollView.fullScroll(android.widget.ScrollView.FOCUS_DOWN) }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun saveMessageToDb(msg: ChatMessage) {
        lifecycleScope.launch {
            try { messageDao.insertMessage(msg.toMessageEntity(currentConversationId)) } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun MessageEntity.toChatMessage(): ChatMessage {
        return ChatMessage(id = this.id, role = if (this.role == "USER") MessageRole.USER else MessageRole.AI, content = this.content, attachmentUris = this.attachmentUris?.split(",")?.filter { it.isNotEmpty() }, colorHex = if (this.role == "USER") themeColorHex else "#2A2A2A", timestamp = this.timestamp)
    }

    private fun ChatMessage.toMessageEntity(convId: String): MessageEntity {
        return MessageEntity(id = this.id, conversationId = convId, senderId = if (this.role == MessageRole.USER) "me" else "ai", role = this.role.name, content = this.content, timestamp = this.timestamp, attachmentUris = this.attachmentUris?.joinToString(","), isEncrypted = false)
    }

    private fun showSetupDialog() {
        AlertDialog.Builder(this).setTitle("المحرك غير متصل").setMessage("مرحباً بك في Monolith. تم حفظ رسالتك محلياً، ولكن للرد عليك بالذكاء الاصطناعي يرجى تهيئة المحرك.")
            .setPositiveButton("الذهاب للإعدادات") { _, _ -> startActivity(Intent(this, SettingsActivity::class.java)) }.setNegativeButton("إلغاء", null).show()
    }

    private fun handleApiError(aiMsgIndex: Int, errorMessage: String) {
        if (aiMsgIndex < messageList.size) { messageList.removeAt(aiMsgIndex); chatContainer.removeAllViews(); for (m in messageList) { addChatBubble(chatContainer, m) } }
        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
        if (errorMessage.contains("مصادقة") || errorMessage.contains("مهيأ")) { showSetupDialog() }
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
                val imageView = ImageView(this)
                imageView.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                Glide.with(this).load(uri).into(imageView)
                relativeLayout.addView(imageView)

                val btnRemove = AppCompatTextView(this)
                val btnParams = android.widget.RelativeLayout.LayoutParams(dpToPx(18), dpToPx(18))
                btnParams.addRule(android.widget.RelativeLayout.ALIGN_PARENT_TOP)
                btnParams.addRule(android.widget.RelativeLayout.ALIGN_PARENT_END)
                btnRemove.layoutParams = btnParams
                btnRemove.text = "X"
                btnRemove.setTextColor(Color.WHITE)
                btnRemove.textSize = 10f
                btnRemove.gravity = Gravity.CENTER
                btnRemove.setBackgroundResource(R.drawable.bg_circle_dark)
                btnRemove.setOnClickListener { selectedImageUris.removeAt(index); updatePreviewUi() }
                relativeLayout.addView(btnRemove)
                cardView.addView(relativeLayout)
                previewListContainer.addView(cardView)
            }
        }
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private fun showAttachBottomSheet() {
        val bottomSheetDialog = BottomSheetDialog(this)
        val bottomSheetView = layoutInflater.inflate(R.layout.bottom_sheet_attach, null)
        bottomSheetDialog.setContentView(bottomSheetView)

        bottomSheetView.findViewById<LinearLayout>(R.id.btnOptionImage)?.setOnClickListener { bottomSheetDialog.dismiss(); pickImagesLauncher.launch("image/*") }
        bottomSheetView.findViewById<LinearLayout>(R.id.btnOptionDocument)?.setOnClickListener { bottomSheetDialog.dismiss(); pickDocumentLauncher.launch(arrayOf("text/plain", "text/*", "application/json")) }
        bottomSheetView.findViewById<LinearLayout>(R.id.btnOptionAudio)?.setOnClickListener { bottomSheetDialog.dismiss() }
        bottomSheetView.findViewById<LinearLayout>(R.id.btnOptionSearch)?.setOnClickListener { bottomSheetDialog.dismiss() }
        bottomSheetView.findViewById<LinearLayout>(R.id.btnOptionCamera)?.setOnClickListener { bottomSheetDialog.dismiss() }
        bottomSheetView.findViewById<LinearLayout>(R.id.btnOptionAI)?.setOnClickListener { bottomSheetDialog.dismiss() }

        bottomSheetDialog.show()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(etMessage.windowToken, 0)
    }

    override fun onResume() {
        super.onResume()
        try {
            val settingsPrefs = getSharedPreferences("Settings", MODE_PRIVATE)
            val rootLayout = findViewById<LinearLayout>(R.id.rootLayout)
            themeColorHex = settingsPrefs.getString("AppThemeColor", "#00D4FF") ?: "#00D4FF"
            val themeColor = Color.parseColor(themeColorHex)

            tvAppTitle.setTextColor(themeColor)
            btnSend.setColorFilter(themeColor, PorterDuff.Mode.SRC_IN)
            findViewById<ImageButton>(R.id.btnAttach).setColorFilter(themeColor, PorterDuff.Mode.SRC_IN)

            val imageUriString = settingsPrefs.getString("AppBackgroundImage", null)
            if (imageUriString != null) {
                try {
                    val uri = Uri.parse(imageUriString)
                    val inputStream = contentResolver.openInputStream(uri)
                    val options = BitmapFactory.Options().apply { inSampleSize = 2 }
                    rootLayout.background = BitmapDrawable(resources, BitmapFactory.decodeStream(inputStream, null, options))
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { rootLayout.foreground = android.graphics.drawable.ColorDrawable(Color.parseColor("#99000000")) }
                } catch (e: Exception) { applyColorBackground(settingsPrefs, rootLayout) }
            } else {
                applyColorBackground(settingsPrefs, rootLayout)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) rootLayout.foreground = null
            }

            val savedColor = settingsPrefs.getString("AppBackground", "#0A0A0A") ?: "#0A0A0A"
            if (savedColor == "#FFFFFF" && imageUriString == null) {
                etMessage.setTextColor(Color.BLACK); etMessage.setHintTextColor(Color.GRAY)
                findViewById<ImageButton>(R.id.btnBack).setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN)
                findViewById<ImageButton>(R.id.btnSettings).setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN)
            } else {
                etMessage.setTextColor(Color.WHITE); etMessage.setHintTextColor(Color.parseColor("#666666"))
                findViewById<ImageButton>(R.id.btnBack).setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
                findViewById<ImageButton>(R.id.btnSettings).setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun applyColorBackground(sharedPreferences: android.content.SharedPreferences, rootLayout: LinearLayout) {
        rootLayout.setBackgroundColor(Color.parseColor(sharedPreferences.getString("AppBackground", "#0A0A0A") ?: "#0A0A0A"))
    }

    private fun addChatBubble(container: LinearLayout, msg: ChatMessage) {
        val messageLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        if (!msg.attachmentUris.isNullOrEmpty()) {
            val scrollView = android.widget.HorizontalScrollView(this)
            val imageContainer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val imageSizePx = dpToPx(180)

            for ((index, uriString) in msg.attachmentUris.withIndex()) {
                val imageView = ImageView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(imageSizePx, imageSizePx).apply { setMargins(0, 0, 16, 16) }
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    val clip = GradientDrawable().apply { cornerRadius = dpToPx(16).toFloat(); setColor(Color.TRANSPARENT) }
                    background = clip
                    clipToOutline = true
                }
                Glide.with(this).load(uriString).into(imageView)
                imageContainer.addView(imageView)
            }
            scrollView.addView(imageContainer)
            messageLayout.addView(scrollView)
        }

        if (msg.content.isNotEmpty()) {
            val textBubble = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dpToPx(16), dpToPx(12), dpToPx(8), dpToPx(12))
                background = GradientDrawable().apply { 
                    setColor(Color.parseColor(msg.colorHex))
                    cornerRadius = dpToPx(20).toFloat() 
                }
            }
            
            val textView = TextView(this).apply {
                text = msg.content
                textSize = 15f
                setTextColor(if (msg.role == MessageRole.USER) Color.BLACK else Color.WHITE)
                textDirection = if (msg.role == MessageRole.USER) TextView.TEXT_DIRECTION_RTL else TextView.TEXT_DIRECTION_LOCALE
                textAlignment = TextView.TEXT_ALIGNMENT_VIEW_START
                setLineSpacing(dpToPx(4).toFloat(), 1.1f)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            
            textBubble.addView(textView)
            
            if (msg.content != "جاري التفكير...") {
                val btnSpeak = ImageView(this).apply {
                    setImageResource(R.drawable.ic_volume_up) 
                    setColorFilter(if (msg.role == MessageRole.USER) Color.parseColor("#55000000") else Color.parseColor("#88FFFFFF"))
                    val params = LinearLayout.LayoutParams(dpToPx(20), dpToPx(20))
                    params.marginStart = dpToPx(12)
                    layoutParams = params
                    
                    setOnClickListener {
                        currentPlayingIcon?.setImageResource(R.drawable.ic_volume_up)
                        currentPlayingIcon?.setColorFilter(if (msg.role == MessageRole.USER) Color.parseColor("#55000000") else Color.parseColor("#88FFFFFF"))
                        currentPlayingIcon = this

                        setImageResource(R.drawable.avd_audio_anim)
                        (drawable as? AnimatedVectorDrawable)?.start()
                        
                        ttsManager.speak(msg.content)
                    }
                }
                textBubble.addView(btnSpeak)
            }

            messageLayout.addView(textBubble)
        }

        if (msg.role == MessageRole.USER) { messageLayout.gravity = Gravity.END; messageLayout.setPadding(0, dpToPx(8), 0, dpToPx(8)) } 
        else { messageLayout.gravity = Gravity.START; messageLayout.setPadding(0, dpToPx(8), 0, dpToPx(8)) }
        container.addView(messageLayout)
    }
}

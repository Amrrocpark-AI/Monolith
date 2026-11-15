package com.monolith

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatTextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class ChatActivity : AppCompatActivity() {

    private val selectedImageUris = mutableListOf<Uri>()
    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var imagePreviewContainer: android.widget.HorizontalScrollView
    private lateinit var previewListContainer: LinearLayout
    private lateinit var recyclerViewChat: RecyclerView
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var tvAppTitle: TextView
    private lateinit var btnLoadModel: ImageButton
    private lateinit var btnAgents: ImageButton

    private var themeColorHex = "#00D4FF"
    private lateinit var securePrefs: androidx.security.crypto.EncryptedSharedPreferences
    private lateinit var appDatabase: AppDatabase
    private lateinit var modelManager: ModelManager
    private lateinit var agentBrain: AgentBrain
    private var messageList: MutableList<ChatMessage> = mutableListOf()
    private val currentConversationId = "main_chat" 
    
    private var ttsManager: TtsManager? = null
    private var isModelLoaded = false
    private var activeAgentId = 0 // 0 هو الوكيل الرئيسي

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

    private val pickModelLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ChatActivity, "جاري تحميل النموذج...", Toast.LENGTH_SHORT).show()
                    }

                    val inputStream = contentResolver.openInputStream(uri)
                    val modelFile = File(filesDir, "model.gguf")
                    
                    if (!modelFile.exists()) {
                        val outputStream = FileOutputStream(modelFile)
                        inputStream?.copyTo(outputStream)
                        inputStream?.close()
                        outputStream.close()
                    }

                    modelManager.loadModel(modelFile.absolutePath, 
                        onSuccess = {
                            runOnUiThread {
                                isModelLoaded = true
                                btnLoadModel.setColorFilter(Color.GREEN)
                                activeAgentId = 0 // إعادة تعيين للوكيل الرئيسي عند تحميل نموذج جديد
                                Toast.makeText(this@ChatActivity, "تم تحميل النموذج بنجاح!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onError = { error ->
                            runOnUiThread {
                                isModelLoaded = false
                                btnLoadModel.setColorFilter(Color.RED)
                                Toast.makeText(this@ChatActivity, "فشل التحميل: $error", Toast.LENGTH_LONG).show()
                            }
                        }
                    )
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ChatActivity, "فشل نسخ الملف: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        ApiClient.init(applicationContext)
        
        appDatabase = AppDatabase(applicationContext)
        modelManager = ModelManager(applicationContext)
        agentBrain = AgentBrain(modelManager)
        
        lifecycleScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                loadMessagesFromDb()
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
        val btnDashboard = findViewById<ImageButton>(R.id.btnDashboard)
        tvAppTitle = findViewById(R.id.tvAppTitle)

        recyclerViewChat = findViewById(R.id.recyclerViewChat)
        recyclerViewChat.layoutManager = LinearLayoutManager(this)
        chatAdapter = ChatAdapter(
            context = this,
            messages = messageList,
            onEditClick = { msg -> showEditDialog(msg) },
            onReactionClick = { msg -> 
                val newReaction = when (msg.reactionType) {
                    "LIKE" -> "DISLIKE"
                    "DISLIKE" -> null
                    else -> "LIKE"
                }
                msg.reactionType = newReaction
                saveMessageToDb(msg)
                chatAdapter.updateMessages(messageList)
            },
            onSpeakClick = { text -> initTtsIfNeeded(); ttsManager?.speak(text) },
            onPinClick = { msg ->
                msg.isPinned = !msg.isPinned
                saveMessageToDb(msg)
                chatAdapter.updateMessages(messageList)
                Toast.makeText(this, if (msg.isPinned) "تم التثبيت" else "تم إلغاء التثبيت", Toast.LENGTH_SHORT).show()
            },
            themeColorHex = themeColorHex
        )
        recyclerViewChat.adapter = chatAdapter

        imagePreviewContainer = findViewById(R.id.imagePreviewContainer)
        previewListContainer = findViewById(R.id.previewListContainer)

        val rootLayout = findViewById<LinearLayout>(R.id.rootLayout)
        val topBar = rootLayout.getChildAt(0) as LinearLayout

        // زر تحميل النموذج
        btnLoadModel = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_save)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.GRAY)
            layoutParams = LinearLayout.LayoutParams(dpToPx(48), dpToPx(48))
            setOnClickListener { pickModelLauncher.launch(arrayOf("*/*")) }
        }
        topBar.addView(btnLoadModel, topBar.childCount - 1)

        // زر الوكلاء المتعددين (الجديد)
        btnAgents = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_add) // يمكنك تغيير الأيقونة
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.parseColor("#FFAA00")) // لون برتقالي للتمييز
            layoutParams = LinearLayout.LayoutParams(dpToPx(48), dpToPx(48))
            setOnClickListener { showAgentsBottomSheet() }
        }
        topBar.addView(btnAgents, topBar.childCount - 1)
        
        val btnPinnedBox = ImageButton(this).apply {
            setImageResource(R.drawable.ic_pinned)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(dpToPx(48), dpToPx(48))
            setOnClickListener { showPinnedMessagesBottomSheet() }
        }
        topBar.addView(btnPinnedBox, topBar.childCount - 1)

        val btnControls = ImageButton(this).apply {
            setImageResource(R.drawable.ic_settings)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(dpToPx(48), dpToPx(48))
            setOnClickListener { showControlsBottomSheet() }
        }
        topBar.addView(btnControls, topBar.childCount - 1)

        val btnNotes = ImageButton(this).apply {
            setImageResource(R.drawable.ic_copy)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(dpToPx(48), dpToPx(48))
            setOnClickListener { startActivity(Intent(this@ChatActivity, NotesActivity::class.java)) }
        }
        topBar.addView(btnNotes, topBar.childCount - 1)

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
        btnDashboard.setOnClickListener { showDashboardBottomSheet() }
        btnSend.setOnClickListener { sendMessage() }
        etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) { sendMessage(); true } else { false }
        }
        
        checkAndLoadModelOnStartup()
    }

    private fun showAgentsBottomSheet() {
        val bottomSheetDialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(android.R.layout.list_content, null)
        val listView = view.findViewById<ListView>(android.R.id.list)
        
        val items = arrayOf(
            "الوكيل الرئيسي (نشط حالياً)",
            "إنشاء وكيل فرعي جديد (يتطلب ذاكرة كافية)",
            "بدء خادم التعاون (P2P) بين الهواتف"
        )
        
        listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, items)
        listView.setOnItemClickListener { _, _, position, _ ->
            when(position) {
                0 -> {
                    activeAgentId = 0
                    Toast.makeText(this, "تم التبديل للوكيل الرئيسي", Toast.LENGTH_SHORT).show()
                    bottomSheetDialog.dismiss()
                }
                1 -> {
                    if (!isModelLoaded) {
                        Toast.makeText(this, "يرجى تحميل النموذج أولاً", Toast.LENGTH_SHORT).show()
                        return@setOnItemClickListener
                    }
                    val newId = modelManager.createSubAgent()
                    if (newId > 0) {
                        activeAgentId = newId
                        Toast.makeText(this, "تم إنشاء الوكيل $newId وتفعيله", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "فشل إنشاء الوكيل (نفدت الذاكرة؟)", Toast.LENGTH_SHORT).show()
                    }
                    bottomSheetDialog.dismiss()
                }
                2 -> {
                    NetworkManager.startP2PServer()
                    Toast.makeText(this, "تم تفعيل خادم التعاون (قيد التطوير)", Toast.LENGTH_SHORT).show()
                    bottomSheetDialog.dismiss()
                }
            }
        }
        bottomSheetDialog.setContentView(view)
        bottomSheetDialog.show()
    }

    private fun checkAndLoadModelOnStartup() {
        val modelFile = File(filesDir, "model.gguf")
        if (modelFile.exists()) {
            Toast.makeText(this, "جاري تحميل النموذج المحفوظ...", Toast.LENGTH_SHORT).show()
            modelManager.loadModel(modelFile.absolutePath, 
                onSuccess = {
                    runOnUiThread {
                        isModelLoaded = true
                        btnLoadModel.setColorFilter(Color.GREEN)
                        Toast.makeText(this@ChatActivity, "تم التحميل التلقائي للنموذج!", Toast.LENGTH_SHORT).show()
                    }
                },
                onError = { error ->
                    runOnUiThread {
                        isModelLoaded = false
                        btnLoadModel.setColorFilter(Color.RED)
                    }
                }
            )
        }
    }
    
    override fun onDestroy() {
        ttsManager?.shutdown()
        super.onDestroy()
    }

    private fun initTtsIfNeeded() {
        if (ttsManager == null) {
            ttsManager = TtsManager(this) { runOnUiThread { } }
        }
    }

    private fun showControlsBottomSheet() {
        val bottomSheetDialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_controls, null)
        
        val sbTemperature = view.findViewById<SeekBar>(R.id.sbTemperature)
        val tvTemperatureValue = view.findViewById<TextView>(R.id.tvTemperatureValue)
        val spinnerPersona = view.findViewById<Spinner>(R.id.spinnerPersona)
        val cbThinking = view.findViewById<CheckBox>(R.id.cbThinking)
        val btnSaveControls = view.findViewById<Button>(R.id.btnSaveControls)

        val savedTemp = securePrefs.getFloat("temperature", 0.7f)
        sbTemperature.progress = (savedTemp * 100).toInt()
        tvTemperatureValue.text = String.format("%.1f", savedTemp)

        val personas = arrayOf("افتراضي", "محلل نقدي", "موضوعي", "مبتكر")
        val personaKeys = arrayOf("default", "analytical", "objective", "innovator")
        spinnerPersona.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, personas)
        val savedPersona = securePrefs.getString("persona", "default")
        val personaIndex = personaKeys.indexOf(savedPersona)
        if (personaIndex != -1) spinnerPersona.setSelection(personaIndex)

        cbThinking.isChecked = securePrefs.getBoolean("enable_thinking", true)

        sbTemperature.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvTemperatureValue.text = String.format("%.1f", progress / 100f)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnSaveControls.setOnClickListener {
            val temp = sbTemperature.progress / 100f
            val persona = personaKeys[spinnerPersona.selectedItemPosition]
            val thinking = cbThinking.isChecked
            securePrefs.edit()
                .putFloat("temperature", temp)
                .putString("persona", persona)
                .putBoolean("enable_thinking", thinking)
                .apply()
            Toast.makeText(this, "تم حفظ إعدادات العقل", Toast.LENGTH_SHORT).show()
            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.setContentView(view)
        bottomSheetDialog.show()
    }

    private fun analyzeUserMessage(message: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (message.length < 20 && message.isNotEmpty()) {
                    appDatabase.insertMemory(MemoryEntity(type = "PREFERENCE", content = "المستخدم يفضل الإجابات القصيرة", confidence = 0.6f))
                }
                if (message.contains("شكرا") || message.contains("شكراً")) {
                    appDatabase.insertMemory(MemoryEntity(type = "EMOTION", content = "المستخدم يقدر المساعدة", confidence = 0.7f))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun getMemoriesContext(): String {
        return try {
            val memories = appDatabase.getRecentMemories()
            if (memories.isEmpty()) ""
            else {
                val sb = StringBuilder()
                sb.append("ذكريات عن المستخدم:\n")
                for (mem in memories) {
                    sb.append("- ${mem.content}\n")
                }
                sb.toString()
            }
        } catch (e: Exception) {
            ""
        }
    }

    private fun sendMessage() {
        try {
            val message = etMessage.text.toString().trim()
            if (message.isNotEmpty() || selectedImageUris.isNotEmpty()) {
                
                val userMsg = ChatMessage(role = MessageRole.USER, content = message, attachmentUris = selectedImageUris.map { it.toString() }, colorHex = themeColorHex)
                messageList.add(userMsg)
                saveMessageToDb(userMsg)
                chatAdapter.updateMessages(messageList)

                selectedImageUris.clear()
                updatePreviewUi()
                etMessage.text.clear()
                hideKeyboard()
                
                scrollToBottom()

                if (message.startsWith("#")) {
                    val task = message.substring(1).trim()
                    
                    if (!isModelLoaded) {
                        Toast.makeText(this, "يرجى تحميل النموذج أولاً بالضغط على الزر العلوي", Toast.LENGTH_LONG).show()
                        return
                    }

                    if (MonolithAccessibilityService.instance == null) {
                        Toast.makeText(this, "يرجى تفعيل Monolith Agent من الإعدادات", Toast.LENGTH_LONG).show()
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        startActivity(intent)
                        return
                    }

                    lifecycleScope.launch {
                        val agentPrefix = if (activeAgentId == 0) "" else "[الوكيل $activeAgentId] "
                        val thinkingMsg = ChatMessage(role = MessageRole.AI, content = "${agentPrefix}جاري تنفيذ المهمة...", colorHex = "#1A1A1A", isThinking = true)
                        messageList.add(thinkingMsg)
                        val thinkingIndex = messageList.size - 1
                        chatAdapter.updateMessages(messageList)
                        scrollToBottom()

                        // تمرير activeAgentId إلى العقل
                        val result = agentBrain.executeTask(task, agentId = activeAgentId)
                        
                        val updatedThinking = thinkingMsg.copy(content = "${agentPrefix}النتيجة: $result", isThinking = false)
                        messageList[thinkingIndex] = updatedThinking
                        saveMessageToDb(updatedThinking)
                        chatAdapter.updateMessages(messageList)
                        scrollToBottom()
                    }
                    return
                }

                val apiKey = securePrefs.getString("api_key", null)
                val selectedModel = securePrefs.getString("selected_model", null)
                
                if (apiKey.isNullOrEmpty()) {
                    Toast.makeText(this, "تم حفظ رسالتك. يرجى تهيئة المحرك من الإعدادات للرد.", Toast.LENGTH_LONG).show()
                    return
                }
                
                if (selectedModel.isNullOrEmpty()) {
                    Toast.makeText(this, "يرجى اختيار نموذج من الإعدادات أولاً", Toast.LENGTH_LONG).show()
                    return
                }

                analyzeUserMessage(message)

                val enableThinking = securePrefs.getBoolean("enable_thinking", true)
                val temperature = securePrefs.getFloat("temperature", 0.7f)
                val persona = securePrefs.getString("persona", "default") ?: "default"
                
                val personaPrompt = when (persona) {
                    "analytical" -> "أنت Monolith، في وضع المحلل النقدي..."
                    "objective" -> "أنت Monolith، في وضع الموضوعي..."
                    "innovator" -> "أنت Monolith، في وضع المبتكر..."
                    else -> "أنت Monolith، ذكاء اصطناعي سيادي متقدم."
                }

                lifecycleScope.launch {
                    val memoriesContext = getMemoriesContext()
                    val finalPrompt = if (memoriesContext.isNotEmpty()) "$personaPrompt\n\n$memoriesContext" else personaPrompt

                    if (enableThinking) {
                        val thinkingMsg = ChatMessage(role = MessageRole.AI, content = "جاري التفكير...", colorHex = "#1A1A1A", isThinking = true)
                        messageList.add(thinkingMsg)
                        val thinkingIndex = messageList.size - 1
                        chatAdapter.updateMessages(messageList)
                        scrollToBottom()

                        val thinkingPrompt = "$finalPrompt\n\nأنت الآن في مرحلة 'التفكير الداخلي'. لا ترد على المستخدم مباشرة. بل حلل رسالته خطوة بخطوة: ماذا يريد؟ ما هي الثغرات في فكرته؟ ما هي أفضل زاوية للرد؟ اكتب خطوات تفكيرك فقط، بدون إجابة نهائية."
                        
                        val thinkingMessages = mutableListOf<Message>()
                        thinkingMessages.add(Message(role = "system", content = thinkingPrompt))
                        thinkingMessages.add(Message(role = "user", content = message))

                        val thinkingRequest = ChatRequest(model = selectedModel, messages = thinkingMessages, temperature = temperature, maxTokens = 1024)

                        ApiClient.service.sendMessage(url = "v1/chat/completions", headers = emptyMap(), request = thinkingRequest).enqueue(object : Callback<ChatResponse> {
                            override fun onResponse(call: Call<ChatResponse>, response: Response<ChatResponse>) {
                                runOnUiThread {
                                    if (response.isSuccessful) {
                                        val thought = response.body()?.choices?.firstOrNull()?.message?.content ?: "..."
                                        val updatedThinking = thinkingMsg.copy(content = thought)
                                        messageList[thinkingIndex] = updatedThinking
                                        chatAdapter.updateMessages(messageList)
                                        scrollToBottom()

                                        sendFinalResponse(message, selectedModel, apiKey, finalPrompt, temperature)
                                    } else {
                                        handleApiError(thinkingIndex, "خطأ في التفكير: ${response.code()}")
                                    }
                                }
                            }
                            override fun onFailure(call: Call<ChatResponse>, t: Throwable) {
                                runOnUiThread { handleApiError(thinkingIndex, t.message ?: "خطأ غير معروف") }
                            }
                        })
                    } else {
                        sendFinalResponse(message, selectedModel, apiKey, finalPrompt, temperature)
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "حدث خطأ: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun sendFinalResponse(originalMessage: String, selectedModel: String, apiKey: String, personaPrompt: String, temperature: Float) {
        val aiMsg = ChatMessage(role = MessageRole.AI, content = "جاري صياغة الرد...", colorHex = "#2A2A2A")
        messageList.add(aiMsg)
        val aiMsgIndex = messageList.size - 1 
        chatAdapter.updateMessages(messageList)
        scrollToBottom()

        val apiMessages = mutableListOf<Message>()
        apiMessages.add(Message(role = "system", content = "$personaPrompt\n\nلقد انتهيت من التفكير الداخلي. الآن قدم إجابتك النهائية للمستخدم بناءً على تفكيرك السابق."))
        
        val recentHistory = messageList.takeLast(15)
        for (msg in recentHistory) {
            if (msg.role == MessageRole.USER) apiMessages.add(Message(role = "user", content = msg.content))
            else if (msg.role == MessageRole.AI && !msg.isThinking && msg.content != "جاري صياغة الرد...") apiMessages.add(Message(role = "assistant", content = msg.content))
        }

        val request = ChatRequest(model = selectedModel, messages = apiMessages, temperature = temperature, maxTokens = 2048)

        ApiClient.service.sendMessage(url = "v1/chat/completions", headers = emptyMap(), request = request).enqueue(object : Callback<ChatResponse> {
            override fun onResponse(call: Call<ChatResponse>, response: Response<ChatResponse>) {
                runOnUiThread {
                    if (response.isSuccessful) {
                        val reply = response.body()?.choices?.firstOrNull()?.message?.content ?: "لم يتم استلام رد."
                        val updatedMsg = aiMsg.copy(content = reply)
                        messageList[aiMsgIndex] = updatedMsg
                        saveMessageToDb(updatedMsg)
                        chatAdapter.updateMessages(messageList)
                        scrollToBottom()
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

    private fun scrollToBottom() {
        recyclerViewChat.post {
            if (messageList.isNotEmpty()) {
                recyclerViewChat.smoothScrollToPosition(messageList.size - 1)
            }
        }
    }

    private fun loadMessagesFromDb() {
        lifecycleScope.launch {
            try {
                val entities = appDatabase.getMessagesForConversation(currentConversationId)
                messageList = entities.map { it.toChatMessage() }.toMutableList()
                withContext(Dispatchers.Main) {
                    if (::chatAdapter.isInitialized) {
                        chatAdapter.updateMessages(messageList)
                        scrollToBottom()
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun saveMessageToDb(msg: ChatMessage) {
        lifecycleScope.launch(Dispatchers.IO) {
            try { appDatabase.insertMessage(msg.toMessageEntity(currentConversationId)) } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun MessageEntity.toChatMessage(): ChatMessage {
        return ChatMessage(
            id = this.id, 
            role = if (this.role == "USER") MessageRole.USER else MessageRole.AI, 
            content = this.content, 
            attachmentUris = this.attachmentUris?.split(",")?.filter { it.isNotEmpty() }, 
            colorHex = if (this.role == "USER") themeColorHex else "#2A2A2A", 
            timestamp = this.timestamp,
            reactionType = this.reactionType,
            reactionColor = this.reactionColor,
            isPinned = this.isPinned,
            modelName = this.modelName,
            isEdited = this.isEdited,
            originalContent = this.originalContent,
            editReason = this.editReason,
            editedTimestamp = this.editedTimestamp
        )
    }

    private fun ChatMessage.toMessageEntity(convId: String): MessageEntity {
        return MessageEntity(
            id = this.id, 
            conversationId = convId, 
            senderId = if (this.role == MessageRole.USER) "me" else "ai", 
            role = this.role.name, 
            content = this.content, 
            timestamp = this.timestamp, 
            attachmentUris = this.attachmentUris?.joinToString(","), 
            isEncrypted = false,
            reactionType = this.reactionType,
            reactionColor = this.reactionColor,
            isPinned = this.isPinned,
            modelName = this.modelName,
            isEdited = this.isEdited,
            originalContent = this.originalContent,
            editReason = this.editReason,
            editedTimestamp = this.editedTimestamp
        )
    }

    private fun handleApiError(aiMsgIndex: Int, errorMessage: String) {
        if (aiMsgIndex < messageList.size) { messageList.removeAt(aiMsgIndex); chatAdapter.updateMessages(messageList) }
        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
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
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun applyColorBackground(sharedPreferences: android.content.SharedPreferences, rootLayout: LinearLayout) {
        rootLayout.setBackgroundColor(Color.parseColor(sharedPreferences.getString("AppBackground", "#0A0A0A") ?: "#0A0A0A"))
    }

    private fun showPinnedMessagesBottomSheet() {
        lifecycleScope.launch {
            val pinnedMessages = appDatabase.getPinnedMessages(currentConversationId)
            withContext(Dispatchers.Main) {
                if (pinnedMessages.isEmpty()) {
                    Toast.makeText(this@ChatActivity, "لا توجد رسائل مثبتة", Toast.LENGTH_SHORT).show()
                    return@withContext
                }
                val bottomSheetDialog = BottomSheetDialog(this@ChatActivity)
                val view = layoutInflater.inflate(android.R.layout.list_content, null)
                val listView = view.findViewById<ListView>(android.R.id.list)
                val displayList = pinnedMessages.map { it.content }
                listView.adapter = ArrayAdapter(this@ChatActivity, android.R.layout.simple_list_item_1, displayList)
                listView.setOnItemClickListener { _, _, position, _ ->
                    val msgId = pinnedMessages[position].id
                    val index = messageList.indexOfFirst { it.id == msgId }
                    if (index != -1) {
                        recyclerViewChat.smoothScrollToPosition(index)
                    }
                    bottomSheetDialog.dismiss()
                }
                bottomSheetDialog.setContentView(view)
                bottomSheetDialog.show()
            }
        }
    }

    private fun showDashboardBottomSheet() {
        val bottomSheetDialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_status, null)
        
        val apiKey = securePrefs.getString("api_key", null)
        val selectedModel = securePrefs.getString("selected_model", null) ?: "غير محدد"
        val provider = when {
            apiKey == null -> "غير متصل"
            selectedModel.contains("groq", true) -> "Groq"
            selectedModel.contains("gpt", true) -> "OpenAI"
            else -> "مخصص"
        }

        val tvConnectionStatus = view.findViewById<TextView>(R.id.tvConnectionStatus)
        val ivConnectionIcon = view.findViewById<ImageView>(R.id.ivConnectionIcon)
        val tvProviderName = view.findViewById<TextView>(R.id.tvProviderName)
        val tvModelName = view.findViewById<TextView>(R.id.tvModelName)
        val tvMessageCount = view.findViewById<TextView>(R.id.tvMessageCount)
        val btnGoToSettings = view.findViewById<Button>(R.id.btnGoToSettings)

        if (apiKey.isNullOrEmpty()) {
            tvConnectionStatus.text = "غير متصل"
            tvConnectionStatus.setTextColor(Color.RED)
            ivConnectionIcon.setColorFilter(Color.RED)
        } else {
            tvConnectionStatus.text = "متصل"
            tvConnectionStatus.setTextColor(Color.GREEN)
            ivConnectionIcon.setColorFilter(Color.GREEN)
        }

        tvProviderName.text = provider
        tvModelName.text = selectedModel
        
        lifecycleScope.launch {
            val count = messageList.size
            withContext(Dispatchers.Main) {
                tvMessageCount.text = count.toString()
            }
        }

        btnGoToSettings.setOnClickListener {
            bottomSheetDialog.dismiss()
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        bottomSheetDialog.setContentView(view)
        bottomSheetDialog.show()
    }

    private fun showEditDialog(msg: ChatMessage) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("تعديل الرسالة")

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
        }

        val etNewContent = EditText(this).apply {
            setText(msg.content)
            hint = "النص الجديد"
            setTextColor(Color.BLACK)
        }
        layout.addView(etNewContent)

        val etReason = EditText(this).apply {
            hint = "سبب التعديل (اختياري)"
            setTextColor(Color.BLACK)
        }
        layout.addView(etReason)

        builder.setView(layout)

        builder.setPositiveButton("حفظ") { _, _ ->
            val newContent = etNewContent.text.toString().trim()
            val reason = etReason.text.toString().trim()
            if (newContent.isNotEmpty()) {
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        appDatabase.updateMessageContent(
                            messageId = msg.id,
                            newContent = newContent,
                            originalContent = msg.content,
                            reason = if (reason.isEmpty()) null else reason,
                            timestamp = System.currentTimeMillis()
                        )
                        withContext(Dispatchers.Main) {
                            val index = messageList.indexOfFirst { it.id == msg.id }
                            if (index != -1) {
                                val updatedMsg = messageList[index].copy(
                                    content = newContent,
                                    isEdited = true,
                                    originalContent = msg.content,
                                    editReason = if (reason.isEmpty()) null else reason,
                                    editedTimestamp = System.currentTimeMillis()
                                )
                                messageList[index] = updatedMsg
                                chatAdapter.updateMessages(messageList)
                                scrollToBottom()
                            }
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@ChatActivity, "فشل التعديل: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        builder.setNegativeButton("إلغاء", null)
        builder.show()
    }
}

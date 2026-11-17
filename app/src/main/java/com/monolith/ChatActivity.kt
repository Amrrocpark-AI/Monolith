package com.monolith

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class ChatActivity : AppCompatActivity() {

    companion object {
        private const val CONVERSATION_ID = "main_chat"
        private const val MODEL_FILE_NAME = "model.gguf"
        private const val VISION_MODEL_FILE_NAME = "vision_model.gguf"
        private const val VISION_PROJECT_FILE_NAME = "vision_mmproj.gguf"

        private const val STATUS_LOCAL = "جاري تنفيذ الطلب..."
        private const val STATUS_CLOUD = "جاري إعداد الرد..."
    }

    // ---------------------------------------------------------
    // UI
    // ---------------------------------------------------------

    private val selectedImageUris = mutableListOf<Uri>()

    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var imagePreviewContainer: HorizontalScrollView
    private lateinit var previewListContainer: LinearLayout
    private lateinit var recyclerViewChat: RecyclerView
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var tvAppTitle: TextView

    private lateinit var btnLoadModel: ImageButton
    private lateinit var btnLoadVisionModel: ImageButton
    private lateinit var btnAgents: ImageButton
    private lateinit var btnThreads: ImageButton

    private var themeColorHex = "#00D4FF"

    // ---------------------------------------------------------
    // Core services
    // ---------------------------------------------------------

    private lateinit var securePrefs: SharedPreferences
    private lateinit var appDatabase: AppDatabase
    private lateinit var modelManager: ModelManager
    private lateinit var agentBrain: AgentBrain

    private val messageList = mutableListOf<ChatMessage>()
    private val currentConversationId = CONVERSATION_ID

    private var ttsManager: TtsManager? = null

    @Volatile
    private var isModelLoaded = false

    @Volatile
    private var isVisionLoaded = false

    private var activeAgentId = 0
    private var pendingVisionGgufPath: String? = null

    private var isActivityReady = false
    private var isLoadingMessages = false

    // ---------------------------------------------------------
    // Request and database coordination
    // ---------------------------------------------------------

    private val databaseMutex = Mutex()

    private val activeApiCalls =
        mutableMapOf<String, Call<ChatResponse>>()

    private val activeLocalJobs =
        mutableMapOf<String, Job>()

    private val requestLock = Any()

    // ---------------------------------------------------------
    // File helpers
    // ---------------------------------------------------------

    private suspend fun copyUriToFileAtomically(
        uri: Uri,
        destination: File
    ) = withContext(Dispatchers.IO) {

        val parent = destination.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }

        val temporaryFile = File(
            destination.parentFile,
            destination.name + ".tmp"
        )

        try {
            val input = contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("تعذر فتح الملف المحدد")

            input.use { source ->
                FileOutputStream(temporaryFile).use { output ->
                    source.copyTo(output)
                    output.fd.sync()
                }
            }

            if (temporaryFile.length() <= 0L) {
                throw IllegalStateException("الملف المحدد فارغ")
            }

            if (destination.exists() && !destination.delete()) {
                throw IllegalStateException("تعذر استبدال الملف السابق")
            }

            if (!temporaryFile.renameTo(destination)) {
                temporaryFile.copyTo(destination, overwrite = true)
                temporaryFile.delete()
            }

        } catch (e: Exception) {
            temporaryFile.delete()
            throw e
        }
    }

    private fun persistReadPermission(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
            // بعض مزودي الملفات لا يدعمون الصلاحية الدائمة.
        }
    }

    // ---------------------------------------------------------
    // Activity result launchers
    // ---------------------------------------------------------

    private val pickImagesLauncher =
        registerForActivityResult(
            ActivityResultContracts.OpenMultipleDocuments()
        ) { uris ->

            if (uris.isNullOrEmpty()) return@registerForActivityResult

            for (uri in uris) {
                persistReadPermission(uri)

                if (!selectedImageUris.contains(uri)) {
                    selectedImageUris.add(uri)
                }
            }

            updatePreviewUi()
        }

    private val pickDocumentLauncher =
        registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->

            if (uri == null) return@registerForActivityResult

            lifecycleScope.launch {
                try {
                    persistReadPermission(uri)

                    val text = withContext(Dispatchers.IO) {
                        contentResolver.openInputStream(uri)?.use {
                            it.bufferedReader().use { reader ->
                                reader.readText()
                            }
                        } ?: ""
                    }

                    if (text.isBlank()) {
                        showToast("الملف فارغ أو تعذرت قراءته")
                        return@launch
                    }

                    val currentText = etMessage.text.toString()

                    etMessage.setText(
                        if (currentText.isBlank()) {
                            "محتوى الملف:\n$text"
                        } else {
                            "$currentText\n\nمحتوى الملف:\n$text"
                        }
                    )

                    etMessage.setSelection(etMessage.text.length)
                    showToast("تمت قراءة الملف")

                } catch (e: Exception) {
                    showToast("تعذرت قراءة الملف: ${e.message}")
                }
            }
        }

    private val pickModelLauncher =
        registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->

            if (uri == null) return@registerForActivityResult

            lifecycleScope.launch {
                try {
                    showToast("جاري نسخ النموذج...")

                    val modelFile = File(filesDir, MODEL_FILE_NAME)

                    copyUriToFileAtomically(uri, modelFile)

                    loadLocalModel(modelFile)

                } catch (e: Exception) {
                    showToast("فشل تحميل ملف النموذج: ${e.message}")
                }
            }
        }

    private val pickVisionGgufLauncher =
        registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->

            if (uri == null) return@registerForActivityResult

            lifecycleScope.launch {
                try {
                    showToast("جاري نسخ نموذج الرؤية...")

                    val visionFile = File(
                        filesDir,
                        VISION_MODEL_FILE_NAME
                    )

                    copyUriToFileAtomically(uri, visionFile)

                    pendingVisionGgufPath = visionFile.absolutePath

                    showToast("اختر الآن ملف mmproj")

                    pickMmprojLauncher.launch(arrayOf("*/*"))

                } catch (e: Exception) {
                    showToast("فشل نسخ نموذج الرؤية: ${e.message}")
                }
            }
        }

    private val pickMmprojLauncher =
        registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->

            if (uri == null) return@registerForActivityResult

            lifecycleScope.launch {
                try {
                    val ggufPath = pendingVisionGgufPath

                    if (ggufPath.isNullOrBlank()) {
                        showToast("لم يتم تحديد نموذج الرؤية أولاً")
                        return@launch
                    }

                    showToast("جاري نسخ ملف mmproj...")

                    val mmprojFile = File(
                        filesDir,
                        VISION_PROJECT_FILE_NAME
                    )

                    copyUriToFileAtomically(uri, mmprojFile)

                    val success = withContext(Dispatchers.IO) {
                        VisionProcessor.initVision(
                            ggufPath,
                            mmprojFile.absolutePath
                        )
                    }

                    isVisionLoaded = success

                    btnLoadVisionModel.setColorFilter(
                        if (success) Color.GREEN else Color.RED
                    )

                    showToast(
                        if (success) {
                            "تمت تهيئة محرك الرؤية"
                        } else {
                            "فشلت تهيئة محرك الرؤية"
                        }
                    )

                } catch (e: Exception) {
                    isVisionLoaded = false
                    showToast("خطأ في إعداد الرؤية: ${e.message}")
                }
            }
        }

    // ---------------------------------------------------------
    // Activity lifecycle
    // ---------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_chat)

        try {
            ApiClient.init(applicationContext)

            appDatabase = AppDatabase(applicationContext)
            modelManager = ModelManager(applicationContext)
            agentBrain = AgentBrain(modelManager, applicationContext)

            initializeSecurePreferences()
            initializeViews()
            initializeAdapter()
            initializeTopBar()
            initializeListeners()

            isActivityReady = true

            loadMessagesFromDb()
            checkAndLoadModelOnStartup()

        } catch (e: Exception) {
            showToast("تعذر تهيئة المحادثة: ${e.message}")
        }
    }

    private fun initializeSecurePreferences() {
        val masterKey = MasterKey.Builder(this)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        securePrefs = EncryptedSharedPreferences.create(
            this,
            "SecureSettings",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun initializeViews() {
        etMessage = findViewById(R.id.etMessage)
        btnSend = findViewById(R.id.btnSend)

        tvAppTitle = findViewById(R.id.tvAppTitle)

        recyclerViewChat = findViewById(R.id.recyclerViewChat)

        imagePreviewContainer =
            findViewById(R.id.imagePreviewContainer)

        previewListContainer =
            findViewById(R.id.previewListContainer)

        btnLoadModel = createTopBarButton(
            android.R.drawable.ic_menu_save,
            Color.GRAY
        )

        btnLoadVisionModel = createTopBarButton(
            android.R.drawable.ic_menu_camera,
            Color.GRAY
        )

        btnAgents = createTopBarButton(
            android.R.drawable.ic_menu_add,
            Color.parseColor("#FFAA00")
        )

        btnThreads = createTopBarButton(
            android.R.drawable.ic_menu_agenda,
            Color.parseColor("#00D4FF")
        )

        recyclerViewChat.layoutManager =
            LinearLayoutManager(this).apply {
                stackFromEnd = false
            }
    }

    private fun initializeAdapter() {
        chatAdapter = ChatAdapter(
            context = this,
            messages = messageList,

            onEditClick = { msg ->
                showEditDialog(msg)
            },

            onReactionClick = { msg ->
                val newReaction = when (msg.reactionType) {
                    "LIKE" -> "DISLIKE"
                    "DISLIKE" -> null
                    else -> "LIKE"
                }

                updateMessageLocally(
                    msg.id
                ) {
                    it.copy(reactionType = newReaction)
                }

                val updated = findMessage(msg.id)
                if (updated != null) {
                    saveMessageToDb(updated)
                }
            },

            onSpeakClick = { text ->
                initTtsIfNeeded()
                ttsManager?.speak(text)
            },

            onPinClick = { msg ->
                updateMessageLocally(msg.id) {
                    it.copy(isPinned = !it.isPinned)
                }

                val updated = findMessage(msg.id)

                if (updated != null) {
                    saveMessageToDb(updated)

                    showToast(
                        if (updated.isPinned) {
                            "تم تثبيت الرسالة"
                        } else {
                            "تم إلغاء تثبيت الرسالة"
                        }
                    )
                }
            },

            themeColorHex = themeColorHex
        )

        recyclerViewChat.adapter = chatAdapter
    }

    private fun createTopBarButton(
        iconResource: Int,
        tint: Int
    ): ImageButton {
        return ImageButton(this).apply {
            setImageResource(iconResource)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(tint)

            layoutParams = LinearLayout.LayoutParams(
                dpToPx(48),
                dpToPx(48)
            )
        }
    }

    private fun initializeTopBar() {
        val rootLayout =
            findViewById<LinearLayout>(R.id.rootLayout)

        val topBar = rootLayout.getChildAt(0) as? LinearLayout
            ?: return

        fun addButton(button: ImageButton, action: () -> Unit) {
            button.setOnClickListener { action() }

            val insertIndex =
                (topBar.childCount - 1).coerceAtLeast(0)

            topBar.addView(button, insertIndex)
        }

        val btnShare = createTopBarButton(
            android.R.drawable.ic_menu_share,
            Color.WHITE
        )

        addButton(btnShare) {
            shareFullConversation()
        }

        btnLoadModel.setOnClickListener {
            pickModelLauncher.launch(arrayOf("*/*"))
        }
        topBar.addView(
            btnLoadModel,
            (topBar.childCount - 1).coerceAtLeast(0)
        )

        btnLoadVisionModel.setOnClickListener {
            pickVisionGgufLauncher.launch(arrayOf("*/*"))
        }
        topBar.addView(
            btnLoadVisionModel,
            (topBar.childCount - 1).coerceAtLeast(0)
        )

        btnAgents.setOnClickListener {
            showAgentsBottomSheet()
        }
        topBar.addView(
            btnAgents,
            (topBar.childCount - 1).coerceAtLeast(0)
        )

        btnThreads.setOnClickListener {
            showThreadsBottomSheet()
        }
        topBar.addView(
            btnThreads,
            (topBar.childCount - 1).coerceAtLeast(0)
        )

        val btnPinned = createTopBarButton(
            R.drawable.ic_pinned,
            Color.WHITE
        )

        addButton(btnPinned) {
            showPinnedMessagesBottomSheet()
        }

        val btnControls = createTopBarButton(
            R.drawable.ic_settings,
            Color.WHITE
        )

        addButton(btnControls) {
            showControlsBottomSheet()
        }

        val btnNotes = createTopBarButton(
            R.drawable.ic_copy,
            Color.WHITE
        )

        addButton(btnNotes) {
            startActivity(
                Intent(this, NotesActivity::class.java)
            )
        }
    }

    private fun initializeListeners() {
        btnSend.isEnabled = false
        btnSend.alpha = 0.5f

        etMessage.addTextChangedListener(
            object : TextWatcher {

                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) = Unit

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {
                    updateSendButtonState()
                }

                override fun afterTextChanged(s: Editable?) = Unit
            }
        )

        findViewById<ImageButton>(R.id.btnAttach)
            .setOnClickListener {
                hideKeyboard()
                showAttachBottomSheet()
            }

        findViewById<ImageButton>(R.id.btnBack)
            .setOnClickListener {
                finish()
            }

        findViewById<ImageButton>(R.id.btnSettings)
            .setOnClickListener {
                startActivity(
                    Intent(this, SettingsActivity::class.java)
                )
            }

        findViewById<ImageButton>(R.id.btnDashboard)
            .setOnClickListener {
                showDashboardBottomSheet()
            }

        btnSend.setOnClickListener {
            sendMessage()
        }

        etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else {
                false
            }
        }
    }

    private fun updateSendButtonState() {
        val enabled =
            etMessage.text.toString().isNotBlank() ||
            selectedImageUris.isNotEmpty()

        btnSend.isEnabled = enabled
        btnSend.alpha = if (enabled) 1f else 0.5f
    }

    // ---------------------------------------------------------
    // Model loading
    // ---------------------------------------------------------

    private suspend fun loadLocalModel(modelFile: File) {
        if (!modelFile.exists() || modelFile.length() <= 0L) {
            showToast("ملف النموذج غير صالح")
            return
        }

        showToast("جاري تحميل النموذج المحلي...")

        withContext(Dispatchers.IO) {
            modelManager.loadModel(
                modelFile.absolutePath,

                onSuccess = {
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread

                        isModelLoaded = true
                        activeAgentId = 0

                        btnLoadModel.setColorFilter(Color.GREEN)

                        showToast("تم تحميل النموذج المحلي بنجاح")
                    }
                },

                onError = { error ->
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread

                        isModelLoaded = false

                        btnLoadModel.setColorFilter(Color.RED)

                        showToast("فشل تحميل النموذج: $error")
                    }
                }
            )
        }
    }

    private fun checkAndLoadModelOnStartup() {
        val modelFile = File(filesDir, MODEL_FILE_NAME)

        if (!modelFile.exists() || modelFile.length() <= 0L) {
            isModelLoaded = false
            return
        }

        lifecycleScope.launch {
            try {
                loadLocalModel(modelFile)
            } catch (e: Exception) {
                isModelLoaded = false
                btnLoadModel.setColorFilter(Color.RED)
                showToast("تعذر التحميل التلقائي: ${e.message}")
            }
        }
    }

    // ---------------------------------------------------------
    // Message loading and persistence
    // ---------------------------------------------------------

    private fun loadMessagesFromDb() {
        if (isLoadingMessages) return

        isLoadingMessages = true

        lifecycleScope.launch {
            try {
                val loadedMessages = withContext(Dispatchers.IO) {
                    appDatabase
                        .getMessagesForConversation(currentConversationId)
                        .map { it.toChatMessage() }
                }

                messageList.clear()
                messageList.addAll(loadedMessages)

                chatAdapter.updateMessages(messageList)

                if (messageList.isNotEmpty()) {
                    scrollToBottom()
                }

            } catch (e: Exception) {
                showToast("تعذر تحميل سجل المحادثة: ${e.message}")
            } finally {
                isLoadingMessages = false
            }
        }
    }

    private fun saveMessageToDb(msg: ChatMessage) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                databaseMutex.withLock {
                    appDatabase.insertMessage(
                        msg.toMessageEntity(currentConversationId)
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e(
                    "Monolith",
                    "Message save failed",
                    e
                )
            }
        }
    }

    private fun updateMessageLocally(
        messageId: String,
        transform: (ChatMessage) -> ChatMessage
    ) {
        val index = messageList.indexOfFirst {
            it.id == messageId
        }

        if (index == -1) return

        messageList[index] = transform(messageList[index])

        chatAdapter.updateMessages(messageList)
    }

    private fun findMessage(messageId: String): ChatMessage? {
        return messageList.firstOrNull {
            it.id == messageId
        }
    }

    private fun MessageEntity.toChatMessage(): ChatMessage {
        return ChatMessage(
            id = id,

            role = if (role == "USER") {
                MessageRole.USER
            } else {
                MessageRole.AI
            },

            content = content,

            attachmentUris = decodeAttachmentUris(
                attachmentUris
            ),

            colorHex = if (role == "USER") {
                themeColorHex
            } else {
                "#2A2A2A"
            },

            timestamp = timestamp,
            reactionType = reactionType,
            reactionColor = reactionColor,
            isPinned = isPinned,
            modelName = modelName,
            isEdited = isEdited,
            originalContent = originalContent,
            editReason = editReason,
            editedTimestamp = editedTimestamp,
            isThinking = false
        )
    }

    private fun ChatMessage.toMessageEntity(
        conversationId: String
    ): MessageEntity {
        return MessageEntity(
            id = id,
            conversationId = conversationId,

            senderId = if (role == MessageRole.USER) {
                "me"
            } else {
                "ai"
            },

            role = role.name,
            content = content,
            timestamp = timestamp,

            attachmentUris = encodeAttachmentUris(
                attachmentUris
            ),

            isEncrypted = false,

            reactionType = reactionType,
            reactionColor = reactionColor,
            isPinned = isPinned,
            modelName = modelName,
            isEdited = isEdited,
            originalContent = originalContent,
            editReason = editReason,
            editedTimestamp = editedTimestamp
        )
    }

    private fun encodeAttachmentUris(
        uris: List<String>?
    ): String? {
        if (uris.isNullOrEmpty()) return null

        val array = JSONArray()

        uris.forEach {
            array.put(it)
        }

        return array.toString()
    }

    private fun decodeAttachmentUris(
        stored: String?
    ): List<String>? {
        if (stored.isNullOrBlank()) return null

        return try {
            val array = JSONArray(stored)
            List(array.length()) { index ->
                array.getString(index)
            }
        } catch (_: Exception) {
            // توافق مع السجلات القديمة التي استخدمت الفاصلة.
            stored.split(",").filter {
                it.isNotBlank()
            }
        }
    }

    // ---------------------------------------------------------
    // Message sending
    // ---------------------------------------------------------

    private fun sendMessage() {
        if (!isActivityReady) return

        val text = etMessage.text.toString().trim()
        val attachments = selectedImageUris.map {
            it.toString()
        }

        if (text.isBlank() && attachments.isEmpty()) {
            return
        }

        val apiKey = securePrefs.getString("api_key", null)
        val selectedModel = securePrefs.getString(
            "selected_model",
            null
        )

        // لا نحفظ رسالة يتعذر إرسالها بسبب غياب إعدادات الاتصال.
        if (!isModelLoaded &&
            (apiKey.isNullOrBlank() || selectedModel.isNullOrBlank())
        ) {
            showToast(
                "حمّل نموذجاً محلياً أو أضف إعدادات الاتصال السحابي أولاً"
            )
            return
        }

        val userMessage = ChatMessage(
            role = MessageRole.USER,
            content = text.ifBlank {
                "أرسل المستخدم صوراً مرفقة."
            },
            attachmentUris = attachments,
            colorHex = themeColorHex
        )

        messageList.add(userMessage)
        chatAdapter.updateMessages(messageList)
        saveMessageToDb(userMessage)

        selectedImageUris.clear()
        updatePreviewUi()

        etMessage.text.clear()
        hideKeyboard()
        scrollToBottom()

        if (isModelLoaded) {
            sendToLocalModel(userMessage)
        } else {
            sendToCloudModel(
                userMessage,
                selectedModel!!,
                apiKey!!
            )
        }
    }

    private fun sendToLocalModel(userMessage: ChatMessage) {
        val messageId = userMessage.id

        val statusMessage = ChatMessage(
            role = MessageRole.AI,
            content = STATUS_LOCAL,
            colorHex = "#1A1A1A",
            isThinking = true
        )

        messageList.add(statusMessage)
        chatAdapter.updateMessages(messageList)
        scrollToBottom()

        val job = lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    agentBrain.executeTask(
                        userMessage.content,
                        agentId = activeAgentId
                    )
                }

                updateRequestMessage(
                    messageId = statusMessage.id,
                    content = result,
                    isThinking = false
                )

            } catch (e: CancellationException) {
                throw e

            } catch (e: Exception) {
                updateRequestMessage(
                    messageId = statusMessage.id,
                    content = "تعذر تنفيذ الطلب: ${e.message}",
                    isThinking = false
                )
            } finally {
                synchronized(requestLock) {
                    activeLocalJobs.remove(messageId)
                }
            }
        }

        synchronized(requestLock) {
            activeLocalJobs[messageId] = job
        }
    }

    private fun sendToCloudModel(
        userMessage: ChatMessage,
        selectedModel: String,
        apiKey: String
    ) {
        val temperature = securePrefs.getFloat(
            "temperature",
            0.7f
        ).coerceIn(0f, 2f)

        val persona = securePrefs.getString(
            "persona",
            "default"
        ) ?: "default"

        val personaPrompt = when (persona) {
            "analytical" ->
                "أنت Monolith في وضع المحلل النقدي. حلل المعطيات بدقة، وميّز بين الحقائق والافتراضات."

            "objective" ->
                "أنت Monolith في الوضع الموضوعي. قدم إجابات متوازنة ودقيقة دون تحيز."

            "innovator" ->
                "أنت Monolith في وضع الابتكار. اقترح حلولاً عملية وأفكاراً جديدة مع توضيح حدودها."

            else ->
                "أنت Monolith، مساعد ذكي. أجب بوضوح ودقة، ولا تدّعِ تنفيذ شيء لم تنفذه."
        }

        val history = messageList
            .filter { !it.isThinking }
            .takeLast(16)
            .toList()

        val apiMessages = mutableListOf<Message>()

        apiMessages.add(
            Message(
                role = "system",
                content = personaPrompt
            )
        )

        for (msg in history) {
            if (msg.role == MessageRole.USER) {
                apiMessages.add(
                    Message(
                        role = "user",
                        content = msg.content
                    )
                )
            } else {
                apiMessages.add(
                    Message(
                        role = "assistant",
                        content = msg.content
                    )
                )
            }
        }

        if (userMessage.attachmentUris?.isNotEmpty() == true) {
            apiMessages.add(
                Message(
                    role = "system",
                    content = "تنبيه: توجد صور مرفقة بالرسالة، لكن واجهة API الحالية في التطبيق ترسل نصوصاً فقط؛ لا تفترض أنك شاهدت محتوى الصور."
                )
            )
        }

        val statusMessage = ChatMessage(
            role = MessageRole.AI,
            content = STATUS_CLOUD,
            colorHex = "#2A2A2A",
            isThinking = true,
            modelName = selectedModel
        )

        messageList.add(statusMessage)
        chatAdapter.updateMessages(messageList)
        scrollToBottom()

        val request = ChatRequest(
            model = selectedModel,
            messages = apiMessages,
            temperature = temperature,
            maxTokens = 2048
        )

        val headers = mapOf(
            "Authorization" to "Bearer $apiKey"
        )

        val call = ApiClient.service.sendMessage(
            url = "v1/chat/completions",
            headers = headers,
            request = request
        )

        synchronized(requestLock) {
            activeApiCalls[statusMessage.id] = call
        }

        call.enqueue(
            object : Callback<ChatResponse> {

                override fun onResponse(
                    call: Call<ChatResponse>,
                    response: Response<ChatResponse>
                ) {
                    synchronized(requestLock) {
                        activeApiCalls.remove(statusMessage.id)
                    }

                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread

                        if (response.isSuccessful) {
                            val reply = response.body()
                                ?.choices
                                ?.firstOrNull()
                                ?.message
                                ?.content
                                ?.takeIf { it.isNotBlank() }
                                ?: "لم يتم استلام رد صالح من الخادم."

                            updateRequestMessage(
                                messageId = statusMessage.id,
                                content = reply,
                                isThinking = false,
                                modelName = selectedModel
                            )

                        } else {
                            updateRequestMessage(
                                messageId = statusMessage.id,
                                content = "فشل الطلب. رمز الاستجابة: ${response.code()}",
                                isThinking = false
                            )
                        }
                    }
                }

                override fun onFailure(
                    call: Call<ChatResponse>,
                    t: Throwable
                ) {
                    synchronized(requestLock) {
                        activeApiCalls.remove(statusMessage.id)
                    }

                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread

                        updateRequestMessage(
                            messageId = statusMessage.id,
                            content = "تعذر الاتصال بالخادم: ${t.message ?: "خطأ غير معروف"}",
                            isThinking = false
                        )
                    }
                }
            }
        )
    }

    private fun updateRequestMessage(
        messageId: String,
        content: String,
        isThinking: Boolean,
        modelName: String? = null
    ) {
        if (isFinishing || isDestroyed) return

        val index = messageList.indexOfFirst {
            it.id == messageId
        }

        if (index == -1) return

        val oldMessage = messageList[index]

        val updated = oldMessage.copy(
            content = content,
            isThinking = isThinking,
            modelName = modelName ?: oldMessage.modelName
        )

        messageList[index] = updated

        chatAdapter.updateMessages(messageList)

        if (!isThinking) {
            saveMessageToDb(updated)
            scrollToBottom()
        }
    }

    // ---------------------------------------------------------
    // Scrolling and keyboard
    // ---------------------------------------------------------

    private fun scrollToBottom() {
        recyclerViewChat.post {
            if (messageList.isNotEmpty() &&
                !isFinishing &&
                !isDestroyed
            ) {
                recyclerViewChat.scrollToPosition(
                    messageList.size - 1
                )
            }
        }
    }

    private fun hideKeyboard() {
        try {
            val imm = getSystemService(
                Context.INPUT_METHOD_SERVICE
            ) as InputMethodManager

            imm.hideSoftInputFromWindow(
                etMessage.windowToken,
                0
            )
        } catch (_: Exception) {
        }
    }

    // ---------------------------------------------------------
    // Attachments preview
    // ---------------------------------------------------------

    private fun updatePreviewUi() {
        previewListContainer.removeAllViews()

        if (selectedImageUris.isEmpty()) {
            imagePreviewContainer.visibility =
                LinearLayout.GONE

            updateSendButtonState()
            return
        }

        imagePreviewContainer.visibility =
            LinearLayout.VISIBLE

        for (uri in selectedImageUris.toList()) {
            val cardView = MaterialCardView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    dpToPx(58),
                    dpToPx(58)
                ).apply {
                    setMargins(
                        0,
                        0,
                        dpToPx(8),
                        0
                    )
                }

                radius = dpToPx(8).toFloat()
                cardElevation = 0f
            }

            val container = android.widget.RelativeLayout(this)

            val imageView = ImageView(this).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                scaleType = ImageView.ScaleType.CENTER_CROP
            }

            Glide.with(this)
                .load(uri)
                .centerCrop()
                .into(imageView)

            container.addView(imageView)

            val removeButton = AppCompatTextView(this).apply {
                val params =
                    android.widget.RelativeLayout.LayoutParams(
                        dpToPx(20),
                        dpToPx(20)
                    )

                params.addRule(
                    android.widget.RelativeLayout.ALIGN_PARENT_TOP
                )

                params.addRule(
                    android.widget.RelativeLayout.ALIGN_PARENT_END
                )

                layoutParams = params

                text = "×"
                textSize = 15f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setBackgroundResource(R.drawable.bg_circle_dark)

                setOnClickListener {
                    selectedImageUris.remove(uri)
                    updatePreviewUi()
                }
            }

            container.addView(removeButton)
            cardView.addView(container)

            previewListContainer.addView(cardView)
        }

        updateSendButtonState()
    }

    // ---------------------------------------------------------
    // Share conversation
    // ---------------------------------------------------------

    private fun shareFullConversation() {
        if (messageList.isEmpty()) {
            showToast("لا توجد رسائل لمشاركتها")
            return
        }

        val builder = StringBuilder()

        builder.append("سجل محادثة Monolith\n\n")

        messageList.forEach { msg ->
            val sender = if (msg.role == MessageRole.USER) {
                "أنت"
            } else {
                "Monolith"
            }

            builder.append(sender)
            builder.append(": ")
            builder.append(msg.content)
            builder.append("\n")

            if (!msg.attachmentUris.isNullOrEmpty()) {
                builder.append(
                    "[عدد المرفقات: ${msg.attachmentUris.size}]\n"
                )
            }

            builder.append("\n")
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, builder.toString())
        }

        startActivity(
            Intent.createChooser(
                intent,
                "مشاركة سجل المحادثة"
            )
        )
    }

    // ---------------------------------------------------------
    // Thread management
    // ---------------------------------------------------------

    private fun showThreadsBottomSheet() {
        val dialog = BottomSheetDialog(this)

        val view = layoutInflater.inflate(
            android.R.layout.list_content,
            null
        )

        val listView = view.findViewById<ListView>(
            android.R.id.list
        )

        val items = arrayOf(
            "إنشاء عقدة لونية جديدة",
            "ربط رسالتين بخيط",
            "عرض العقد المتاحة"
        )

        listView.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            items
        )

        listView.setOnItemClickListener { _, _, position, _ ->
            dialog.dismiss()

            when (position) {
                0 -> showCreateNodeDialog()
                1 -> showLinkMessagesDialog()
                2 -> showNodesListDialog()
            }
        }

        dialog.setContentView(view)
        dialog.show()
    }

    private fun showCreateNodeDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL

            setPadding(
                dpToPx(16),
                dpToPx(16),
                dpToPx(16),
                dpToPx(16)
            )
        }

        val nameInput = EditText(this).apply {
            hint = "اسم العقدة"
        }

        val definitionInput = EditText(this).apply {
            hint = "تعريف العقدة"
        }

        val spinner = Spinner(this)

        val colors = arrayOf(
            "أحمر",
            "أزرق",
            "أخضر",
            "أصفر",
            "بنفسجي"
        )

        val colorHexes = arrayOf(
            "#FF0000",
            "#0000FF",
            "#00FF00",
            "#FFFF00",
            "#800080"
        )

        spinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            colors
        )

        layout.addView(nameInput)
        layout.addView(definitionInput)
        layout.addView(spinner)

        AlertDialog.Builder(this)
            .setTitle("إنشاء عقدة لونية")
            .setView(layout)
            .setPositiveButton("حفظ") { _, _ ->

                val name = nameInput.text.toString().trim()
                val definition =
                    definitionInput.text.toString().trim()

                if (name.isBlank() || definition.isBlank()) {
                    showToast("أدخل الاسم والتعريف")
                    return@setPositiveButton
                }

                val color = colorHexes[
                    spinner.selectedItemPosition.coerceIn(
                        colorHexes.indices
                    )
                ]

                lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            appDatabase.insertChromaticNode(
                                colorHex = color,
                                name = name,
                                definition = definition
                            )
                        }

                        showToast("تم إنشاء العقدة")

                    } catch (e: Exception) {
                        showToast("تعذر حفظ العقدة: ${e.message}")
                    }
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun showNodesListDialog() {
        lifecycleScope.launch {
            try {
                val nodes = withContext(Dispatchers.IO) {
                    appDatabase.getAllChromaticNodes()
                }

                if (nodes.isEmpty()) {
                    showToast("لا توجد عقد مضافة")
                    return@launch
                }

                val displayList = nodes.map {
                    "${it["name"]} (${it["color_hex"]}): ${it["definition"]}"
                }

                AlertDialog.Builder(this@ChatActivity)
                    .setTitle("العقد المتاحة")
                    .setItems(displayList.toTypedArray(), null)
                    .setPositiveButton("حسناً", null)
                    .show()

            } catch (e: Exception) {
                showToast("تعذر جلب العقد: ${e.message}")
            }
        }
    }

    private fun showLinkMessagesDialog() {
        if (messageList.size < 2) {
            showToast("تحتاج إلى رسالتين على الأقل")
            return
        }

        lifecycleScope.launch {
            try {
                val nodes = withContext(Dispatchers.IO) {
                    appDatabase.getAllChromaticNodes()
                }

                if (nodes.isEmpty()) {
                    showToast("أنشئ عقدة لونية أولاً")
                    return@launch
                }

                val messageSnapshot = messageList.toList()

                val messageOptions = messageSnapshot.map {
                    it.content.take(45)
                }.toTypedArray()

                AlertDialog.Builder(this@ChatActivity)
                    .setTitle("اختر الرسالة المصدر")
                    .setItems(messageOptions) { _, sourceIndex ->

                        AlertDialog.Builder(this@ChatActivity)
                            .setTitle("اختر الرسالة الهدف")
                            .setItems(messageOptions) { _, targetIndex ->

                                if (sourceIndex == targetIndex) {
                                    showToast("لا يمكن ربط الرسالة بنفسها")
                                    return@setItems
                                }

                                val nodeOptions = nodes.map {
                                    it["name"] ?: "عقدة"
                                }.toTypedArray()

                                AlertDialog.Builder(this@ChatActivity)
                                    .setTitle("اختر العقدة")
                                    .setItems(nodeOptions) { _, nodeIndex ->

                                        lifecycleScope.launch {
                                            try {
                                                val sourceId =
                                                    messageSnapshot[sourceIndex].id

                                                val targetId =
                                                    messageSnapshot[targetIndex].id

                                                val nodeId =
                                                    nodes[nodeIndex]["id"]
                                                        ?: return@launch

                                                withContext(Dispatchers.IO) {
                                                    appDatabase.insertMessageThread(
                                                        sourceId,
                                                        targetId,
                                                        nodeId
                                                    )
                                                }

                                                showToast("تم ربط الرسالتين")

                                                chatAdapter.notifyDataSetChanged()

                                            } catch (e: Exception) {
                                                showToast(
                                                    "تعذر إنشاء الرابط: ${e.message}"
                                                )
                                            }
                                        }
                                    }
                                    .show()
                            }
                            .show()
                    }
                    .show()

            } catch (e: Exception) {
                showToast("تعذر تحميل العقد: ${e.message}")
            }
        }
    }

    // ---------------------------------------------------------
    // Agents
    // ---------------------------------------------------------

    private fun showAgentsBottomSheet() {
        val dialog = BottomSheetDialog(this)

        val view = layoutInflater.inflate(
            android.R.layout.list_content,
            null
        )

        val listView = view.findViewById<ListView>(
            android.R.id.list
        )

        val items = arrayOf(
            "الوكيل الرئيسي",
            "إنشاء وكيل فرعي",
            "خادم التعاون P2P"
        )

        listView.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            items
        )

        listView.setOnItemClickListener { _, _, position, _ ->

            when (position) {
                0 -> {
                    activeAgentId = 0
                    showToast("تم تفعيل الوكيل الرئيسي")
                    dialog.dismiss()
                }

                1 -> {
                    if (!isModelLoaded) {
                        showToast("حمّل النموذج المحلي أولاً")
                        return@setOnItemClickListener
                    }

                    try {
                        val newId = modelManager.createSubAgent()

                        if (newId > 0) {
                            activeAgentId = newId
                            showToast("تم إنشاء الوكيل $newId")
                        } else {
                            showToast("تعذر إنشاء الوكيل")
                        }

                    } catch (e: Exception) {
                        showToast("خطأ في إنشاء الوكيل: ${e.message}")
                    }

                    dialog.dismiss()
                }

                2 -> {
                    try {
                        NetworkManager.startP2PServer()
                        showToast(
                            "تم استدعاء خدمة P2P؛ تحقق من تنفيذها الفعلي"
                        )
                    } catch (e: Exception) {
                        showToast("تعذر تشغيل P2P: ${e.message}")
                    }

                    dialog.dismiss()
                }
            }
        }

        dialog.setContentView(view)
        dialog.show()
    }

    // ---------------------------------------------------------
    // Controls
    // ---------------------------------------------------------

    private fun showControlsBottomSheet() {
        val dialog = BottomSheetDialog(this)

        val view = layoutInflater.inflate(
            R.layout.bottom_sheet_controls,
            null
        )

        val temperatureBar =
            view.findViewById<SeekBar>(R.id.sbTemperature)

        val temperatureValue =
            view.findViewById<TextView>(R.id.tvTemperatureValue)

        val personaSpinner =
            view.findViewById<Spinner>(R.id.spinnerPersona)

        val thinkingCheckbox =
            view.findViewById<CheckBox>(R.id.cbThinking)

        val saveButton =
            view.findViewById<Button>(R.id.btnSaveControls)

        val savedTemperature = securePrefs.getFloat(
            "temperature",
            0.7f
        ).coerceIn(0f, 2f)

        temperatureBar.max = 200
        temperatureBar.progress =
            (savedTemperature * 100).toInt()

        temperatureValue.text =
            String.format("%.1f", savedTemperature)

        val personas = arrayOf(
            "افتراضي",
            "محلل نقدي",
            "موضوعي",
            "مبتكر"
        )

        val personaKeys = arrayOf(
            "default",
            "analytical",
            "objective",
            "innovator"
        )

        personaSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            personas
        )

        val savedPersona = securePrefs.getString(
            "persona",
            "default"
        )

        val selectedIndex = personaKeys.indexOf(savedPersona)

        if (selectedIndex >= 0) {
            personaSpinner.setSelection(selectedIndex)
        }

        thinkingCheckbox.isChecked =
            securePrefs.getBoolean("enable_thinking", false)

        temperatureBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {

                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    temperatureValue.text =
                        String.format("%.1f", progress / 100f)
                }

                override fun onStartTrackingTouch(
                    seekBar: SeekBar?
                ) = Unit

                override fun onStopTrackingTouch(
                    seekBar: SeekBar?
                ) = Unit
            }
        )

        saveButton.setOnClickListener {
            val temperature =
                temperatureBar.progress / 100f

            val persona =
                personaKeys[personaSpinner.selectedItemPosition]

            securePrefs.edit()
                .putFloat("temperature", temperature)
                .putString("persona", persona)
                .putBoolean(
                    "enable_thinking",
                    thinkingCheckbox.isChecked
                )
                .apply()

            showToast("تم حفظ الإعدادات")
            dialog.dismiss()
        }

        dialog.setContentView(view)
        dialog.show()
    }

    // ---------------------------------------------------------
    // Memory
    // ---------------------------------------------------------

    /*
     * لا ننشئ ذكريات من طول الرسالة أو كلمات الشكر.
     * هذه إشارات غير كافية لاستنتاج تفضيلات أو صفات شخصية.
     *
     * لا توجد في الملفات المقدمة واجهة موثوقة لإدارة الذكريات
     * بموافقة المستخدم، لذلك لا نحقن ذكريات قديمة عشوائية
     * في تعليمات النموذج.
     */

    private fun analyzeUserMessage(message: String) {
        // متروكة عمداً دون استنتاجات تلقائية.
    }

    private suspend fun getMemoriesContext(): String {
        return ""
    }

    // ---------------------------------------------------------
    // Attach bottom sheet
    // ---------------------------------------------------------

    private fun showAttachBottomSheet() {
        val dialog = BottomSheetDialog(this)

        val view = layoutInflater.inflate(
            R.layout.bottom_sheet_attach,
            null
        )

        dialog.setContentView(view)

        view.findViewById<LinearLayout>(
            R.id.btnOptionImage
        )?.setOnClickListener {
            dialog.dismiss()
            pickImagesLauncher.launch(arrayOf("image/*"))
        }

        view.findViewById<LinearLayout>(
            R.id.btnOptionDocument
        )?.setOnClickListener {
            dialog.dismiss()

            pickDocumentLauncher.launch(
                arrayOf(
                    "text/plain",
                    "text/*",
                    "application/json"
                )
            )
        }

        view.findViewById<LinearLayout>(
            R.id.btnOptionAudio
        )?.setOnClickListener {
            dialog.dismiss()
            showToast("ميزة تحليل الصوت غير موصولة بعد")
        }

        view.findViewById<LinearLayout>(
            R.id.btnOptionSearch
        )?.setOnClickListener {
            dialog.dismiss()
            showToast("ميزة البحث غير موصولة بعد")
        }

        view.findViewById<LinearLayout>(
            R.id.btnOptionCamera
        )?.setOnClickListener {
            dialog.dismiss()
            showToast("ميزة الكاميرا غير موصولة بعد")
        }

        view.findViewById<LinearLayout>(
            R.id.btnOptionAI
        )?.setOnClickListener {
            dialog.dismiss()
            showToast("اختر النموذج من شريط الأدوات")
        }

        dialog.show()
    }

    // ---------------------------------------------------------
    // Pinned messages
    // ---------------------------------------------------------

    private fun showPinnedMessagesBottomSheet() {
        lifecycleScope.launch {
            try {
                val pinnedMessages = withContext(Dispatchers.IO) {
                    appDatabase.getPinnedMessages(
                        currentConversationId
                    )
                }

                if (pinnedMessages.isEmpty()) {
                    showToast("لا توجد رسائل مثبتة")
                    return@launch
                }

                val dialog = BottomSheetDialog(this@ChatActivity)

                val view = layoutInflater.inflate(
                    android.R.layout.list_content,
                    null
                )

                val listView = view.findViewById<ListView>(
                    android.R.id.list
                )

                listView.adapter = ArrayAdapter(
                    this@ChatActivity,
                    android.R.layout.simple_list_item_1,
                    pinnedMessages.map { it.content }
                )

                listView.setOnItemClickListener { _, _, position, _ ->

                    val id = pinnedMessages[position].id

                    val index = messageList.indexOfFirst {
                        it.id == id
                    }

                    if (index >= 0) {
                        recyclerViewChat.smoothScrollToPosition(index)
                    }

                    dialog.dismiss()
                }

                dialog.setContentView(view)
                dialog.show()

            } catch (e: Exception) {
                showToast("تعذر تحميل الرسائل المثبتة: ${e.message}")
            }
        }
    }

    // ---------------------------------------------------------
    // Dashboard
    // ---------------------------------------------------------

    private fun showDashboardBottomSheet() {
        val dialog = BottomSheetDialog(this)

        val view = layoutInflater.inflate(
            R.layout.bottom_sheet_status,
            null
        )

        val apiKey = securePrefs.getString("api_key", null)

        val selectedModel = securePrefs.getString(
            "selected_model",
            null
        ) ?: "غير محدد"

        val provider = when {
            apiKey.isNullOrBlank() -> "غير متصل"
            selectedModel.contains("groq", true) -> "Groq"
            selectedModel.contains("gpt", true) -> "OpenAI"
            else -> "مخصص"
        }

        val connectionStatus =
            view.findViewById<TextView>(R.id.tvConnectionStatus)

        val connectionIcon =
            view.findViewById<ImageView>(R.id.ivConnectionIcon)

        val providerName =
            view.findViewById<TextView>(R.id.tvProviderName)

        val modelName =
            view.findViewById<TextView>(R.id.tvModelName)

        val messageCount =
            view.findViewById<TextView>(R.id.tvMessageCount)

        val settingsButton =
            view.findViewById<Button>(R.id.btnGoToSettings)

        val connected = !apiKey.isNullOrBlank()

        connectionStatus.text =
            if (connected) "تم إعداد الاتصال" else "غير متصل"

        val statusColor =
            if (connected) Color.GREEN else Color.RED

        connectionStatus.setTextColor(statusColor)
        connectionIcon.setColorFilter(statusColor)

        providerName.text = provider
        modelName.text = selectedModel
        messageCount.text = messageList.size.toString()

        settingsButton.setOnClickListener {
            dialog.dismiss()
            startActivity(
                Intent(this, SettingsActivity::class.java)
            )
        }

        dialog.setContentView(view)
        dialog.show()
    }

    // ---------------------------------------------------------
    // Edit message
    // ---------------------------------------------------------

    private fun showEditDialog(msg: ChatMessage) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL

            setPadding(
                dpToPx(16),
                dpToPx(16),
                dpToPx(16),
                dpToPx(16)
            )
        }

        val newContentInput = EditText(this).apply {
            setText(msg.content)
            hint = "النص الجديد"
            setTextColor(Color.WHITE)
        }

        val reasonInput = EditText(this).apply {
            hint = "سبب التعديل (اختياري)"
            setTextColor(Color.WHITE)
        }

        layout.addView(newContentInput)
        layout.addView(reasonInput)

        AlertDialog.Builder(this)
            .setTitle("تعديل الرسالة")
            .setView(layout)

            .setPositiveButton("حفظ") { _, _ ->

                val newContent =
                    newContentInput.text.toString().trim()

                val reason =
                    reasonInput.text.toString().trim()

                if (newContent.isBlank()) {
                    showToast("لا يمكن حفظ رسالة فارغة")
                    return@setPositiveButton
                }

                val editTime = System.currentTimeMillis()

                lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            appDatabase.updateMessageContent(
                                messageId = msg.id,
                                newContent = newContent,
                                originalContent = msg.content,
                                reason = reason.ifBlank { null },
                                timestamp = editTime
                            )
                        }

                        val index = messageList.indexOfFirst {
                            it.id == msg.id
                        }

                        if (index >= 0) {
                            val updated = messageList[index].copy(
                                content = newContent,
                                isEdited = true,
                                originalContent = msg.content,
                                editReason = reason.ifBlank { null },
                                editedTimestamp = editTime
                            )

                            messageList[index] = updated

                            chatAdapter.updateMessages(messageList)

                            saveMessageToDb(updated)
                        }

                        showToast("تم تعديل الرسالة")

                    } catch (e: Exception) {
                        showToast("فشل تعديل الرسالة: ${e.message}")
                    }
                }
            }

            .setNegativeButton("إلغاء", null)
            .show()
    }

    // ---------------------------------------------------------
    // Background and theme
    // ---------------------------------------------------------

    override fun onResume() {
        super.onResume()

        if (!isActivityReady) return

        applyCurrentTheme()
    }

    private fun applyCurrentTheme() {
        val settings = getSharedPreferences(
            "Settings",
            MODE_PRIVATE
        )

        val rootLayout =
            findViewById<LinearLayout>(R.id.rootLayout)

        themeColorHex = settings.getString(
            "AppThemeColor",
            "#00D4FF"
        ) ?: "#00D4FF"

        val themeColor = try {
            Color.parseColor(themeColorHex)
        } catch (_: Exception) {
            Color.parseColor("#00D4FF")
        }

        tvAppTitle.setTextColor(themeColor)

        btnSend.setColorFilter(
            themeColor,
            PorterDuff.Mode.SRC_IN
        )

        findViewById<ImageButton>(
            R.id.btnAttach
        ).setColorFilter(
            themeColor,
            PorterDuff.Mode.SRC_IN
        )

        val backgroundUri = settings.getString(
            "AppBackgroundImage",
            null
        )

        if (backgroundUri.isNullOrBlank()) {
            applyColorBackground(settings, rootLayout)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                rootLayout.foreground = null
            }

            return
        }

        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                try {
                    contentResolver.openInputStream(
                        Uri.parse(backgroundUri)
                    )?.use { input ->
                        val options = BitmapFactory.Options().apply {
                            inSampleSize = 2
                        }

                        BitmapFactory.decodeStream(
                            input,
                            null,
                            options
                        )
                    }
                } catch (_: Exception) {
                    null
                }
            }

            if (isFinishing || isDestroyed) return@launch

            if (bitmap != null) {
                rootLayout.background =
                    BitmapDrawable(resources, bitmap)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    rootLayout.foreground =
                        ColorDrawable(
                            Color.parseColor("#55000000")
                        )
                }
            } else {
                applyColorBackground(settings, rootLayout)
            }
        }
    }

    private fun applyColorBackground(
        preferences: SharedPreferences,
        rootLayout: LinearLayout
    ) {
        val color = try {
            Color.parseColor(
                preferences.getString(
                    "AppBackground",
                    "#0A0A0A"
                ) ?: "#0A0A0A"
            )
        } catch (_: Exception) {
            Color.parseColor("#0A0A0A")
        }

        rootLayout.setBackgroundColor(color)
    }

    // ---------------------------------------------------------
    // TTS
    // ---------------------------------------------------------

    private fun initTtsIfNeeded() {
        if (ttsManager == null) {
            ttsManager = TtsManager(this) {
                runOnUiThread {
                    // لا توجد عملية واجهة مطلوبة هنا.
                }
            }
        }
    }

    // ---------------------------------------------------------
    // Utilities
    // ---------------------------------------------------------

    private fun dpToPx(dp: Int): Int {
        return (
            dp * resources.displayMetrics.density
        ).toInt()
    }

    private fun showToast(message: String) {
        if (isFinishing || isDestroyed) return

        runOnUiThread {
            if (!isFinishing && !isDestroyed) {
                Toast.makeText(
                    this,
                    message,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // ---------------------------------------------------------
    // Cleanup
    // ---------------------------------------------------------

    override fun onDestroy() {
        synchronized(requestLock) {
            activeApiCalls.values.forEach {
                try {
                    it.cancel()
                } catch (_: Exception) {
                }
            }

            activeApiCalls.clear()

            activeLocalJobs.values.forEach {
                it.cancel()
            }

            activeLocalJobs.clear()
        }

        try {
            ttsManager?.shutdown()
        } catch (_: Exception) {
        }

        ttsManager = null

        super.onDestroy()
    }
}

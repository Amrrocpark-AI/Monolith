
package com.monolith

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
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
import android.util.Base64
import android.view.Gravity
import android.view.View
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
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ChatActivity : AppCompatActivity() {

    companion object {
        private const val CONVERSATION_ID = "main_chat"

        private const val MODEL_FILE_NAME = "model.gguf"
        private const val VISION_MODEL_FILE_NAME = "vision_model.gguf"
        private const val VISION_PROJECT_FILE_NAME = "vision_mmproj.gguf"

        private const val STATUS_LOCAL = "جاري تنفيذ الطلب..."
        private const val STATUS_CLOUD = "جاري إعداد الرد..."

        private const val MAX_IMAGE_BYTES = 8 * 1024 * 1024
        private const val MAX_IMAGE_DIMENSION = 1536
    }

    // =========================================================
    // UI
    // =========================================================

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

    // =========================================================
    // Core services
    // =========================================================

    private lateinit var securePrefs: SharedPreferences
    private lateinit var appDatabase: AppDatabase
    private lateinit var modelManager: ModelManager
    private lateinit var agentBrain: AgentBrain

    private val messageList = mutableListOf<ChatMessage>()
    private val currentConversationId = CONVERSATION_ID

    private var ttsManager: TtsManager? = null

    private var isModelLoaded = false
    private var activeAgentId = 0
    private var pendingVisionGgufPath: String? = null

    private var isActivityReady = false
    private var isLoadingMessages = false

    // =========================================================
    // Concurrency
    // =========================================================

    private val databaseMutex = Mutex()
    private val requestLock = Any()

    private val activeApiCalls =
        mutableMapOf<String, Call<ChatResponse>>()

    private val activeLocalJobs =
        mutableMapOf<String, Job>()

    // =========================================================
    // Activity result launchers
    // =========================================================

    private val pickImagesLauncher =
        registerForActivityResult(
            ActivityResultContracts.OpenMultipleDocuments()
        ) { uris ->

            if (uris.isNullOrEmpty()) return@registerForActivityResult

            uris.forEach { uri ->
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

                } catch (e: CancellationException) {
                    throw e
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

                    val modelFile = File(
                        filesDir,
                        MODEL_FILE_NAME
                    )

                    copyUriToFileAtomically(uri, modelFile)
                    loadLocalModel(modelFile)

                } catch (e: CancellationException) {
                    throw e
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

                    pendingVisionGgufPath =
                        visionFile.absolutePath

                    showToast("اختر الآن ملف mmproj")

                    pickMmprojLauncher.launch(arrayOf("*/*"))

                } catch (e: CancellationException) {
                    throw e
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
                    val visionPath = pendingVisionGgufPath

                    if (visionPath.isNullOrBlank()) {
                        showToast("لم يتم تحديد نموذج الرؤية أولاً")
                        return@launch
                    }

                    showToast("جاري نسخ ملف mmproj...")

                    val mmprojFile = File(
                        filesDir,
                        VISION_PROJECT_FILE_NAME
                    )

                    copyUriToFileAtomically(uri, mmprojFile)

                    val result = withContext(Dispatchers.IO) {
                        modelManager.loadVisionModelAsync(
                            visionPath,
                            mmprojFile.absolutePath
                        )
                    }

                    result.fold(
                        onSuccess = {
                            btnLoadVisionModel.setColorFilter(
                                Color.GREEN
                            )
                            showToast("تم تحميل نموذج الرؤية بنجاح")
                        },
                        onFailure = { error ->
                            btnLoadVisionModel.setColorFilter(
                                Color.RED
                            )
                            showToast(
                                "فشل تحميل نموذج الرؤية: ${error.message}"
                            )
                        }
                    )

                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    btnLoadVisionModel.setColorFilter(Color.RED)
                    showToast("خطأ في إعداد الرؤية: ${e.message}")
                } finally {
                    pendingVisionGgufPath = null
                }
            }
        }

    // =========================================================
    // File operations
    // =========================================================

    private suspend fun copyUriToFileAtomically(
        uri: Uri,
        destination: File
    ) = withContext(Dispatchers.IO) {

        val parent = destination.parentFile ?: filesDir

        if (!parent.exists() && !parent.mkdirs()) {
            throw IllegalStateException(
                "تعذر إنشاء مجلد تخزين النموذج"
            )
        }

        val temporaryFile = File(
            parent,
            destination.name + ".tmp"
        )

        val backupFile = File(
            parent,
            destination.name + ".bak"
        )

        try {
            val input = contentResolver.openInputStream(uri)
                ?: throw IllegalStateException(
                    "تعذر فتح الملف المحدد"
                )

            input.use { source ->
                FileOutputStream(temporaryFile).use { output ->
                    source.copyTo(output)
                    output.fd.sync()
                }
            }

            if (temporaryFile.length() <= 0L) {
                throw IllegalStateException("الملف المحدد فارغ")
            }

            if (backupFile.exists()) {
                backupFile.delete()
            }

            var oldFileMoved = false

            if (destination.exists()) {
                oldFileMoved = destination.renameTo(backupFile)

                if (!oldFileMoved) {
                    throw IllegalStateException(
                        "تعذر حفظ النسخة السابقة قبل الاستبدال"
                    )
                }
            }

            if (!temporaryFile.renameTo(destination)) {
                if (oldFileMoved) {
                    backupFile.renameTo(destination)
                }

                throw IllegalStateException(
                    "تعذر نقل الملف المؤقت إلى موقعه النهائي"
                )
            }

            if (backupFile.exists()) {
                backupFile.delete()
            }

        } catch (e: CancellationException) {
            temporaryFile.delete()
            throw e
        } catch (e: Exception) {
            temporaryFile.delete()

            if (
                backupFile.exists() &&
                !destination.exists()
            ) {
                backupFile.renameTo(destination)
            }

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

    // =========================================================
    // Lifecycle
    // =========================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_chat)

        try {
            ApiClient.init(applicationContext)

            appDatabase = AppDatabase(applicationContext)

            // استخدام المحرك المشترك بدلاً من إنشاء محرك مستقل.
            modelManager = ModelManager.getInstance(
                applicationContext
            )

            // إنشاء الوكيل وفق واجهته المعمارية الحالية.
            agentBrain = AgentBrainFactory.create(
                applicationContext,
                modelManager
            )

            initializeSecurePreferences()
            loadInitialTheme()

            initializeViews()
            initializeAdapter()
            initializeTopBar()
            initializeListeners()

            isActivityReady = true

            loadMessagesFromDb()
            checkAndLoadModelOnStartup()
            updateModelIndicators()

        } catch (e: Exception) {
            android.util.Log.e(
                "Monolith",
                "Chat initialization failed",
                e
            )

            showToast(
                "تعذر تهيئة المحادثة: ${e.message}"
            )
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

    private fun loadInitialTheme() {
        val settings = getSharedPreferences(
            "Settings",
            MODE_PRIVATE
        )

        themeColorHex = settings.getString(
            "AppThemeColor",
            "#00D4FF"
        ) ?: "#00D4FF"

        try {
            Color.parseColor(themeColorHex)
        } catch (_: Exception) {
            themeColorHex = "#00D4FF"
        }
    }

    // =========================================================
    // Views and adapter
    // =========================================================

    private fun initializeViews() {
        etMessage = findViewById(R.id.etMessage)
        btnSend = findViewById(R.id.btnSend)

        tvAppTitle = findViewById(R.id.tvAppTitle)

        recyclerViewChat = findViewById(
            R.id.recyclerViewChat
        )

        imagePreviewContainer = findViewById(
            R.id.imagePreviewContainer
        )

        previewListContainer = findViewById(
            R.id.previewListContainer
        )

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

            onEditClick = { message ->
                showEditDialog(message)
            },

            onReactionClick = { message ->
                val newReaction = when (message.reactionType) {
                    "LIKE" -> "DISLIKE"
                    "DISLIKE" -> null
                    else -> "LIKE"
                }

                updateMessageLocally(message.id) {
                    it.copy(reactionType = newReaction)
                }

                findMessage(message.id)?.let {
                    saveMessageToDb(it)
                }
            },

            onSpeakClick = { text ->
                initTtsIfNeeded()
                ttsManager?.speak(text)
            },

            onPinClick = { message ->
                updateMessageLocally(message.id) {
                    it.copy(isPinned = !it.isPinned)
                }

                findMessage(message.id)?.let { updated ->
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
        chatAdapter.updateMessages(messageList)
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
        val rootLayout = findViewById<LinearLayout>(
            R.id.rootLayout
        )

        val topBar = rootLayout.getChildAt(0) as? LinearLayout
            ?: return

        fun addButton(
            button: ImageButton,
            action: () -> Unit
        ) {
            button.setOnClickListener {
                action()
            }

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

                override fun afterTextChanged(
                    s: Editable?
                ) = Unit
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

        etMessage.setOnEditorActionListener {
                _, actionId, _ ->

            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else {
                false
            }
        }
    }

    private fun updateSendButtonState() {
        if (!::btnSend.isInitialized) return

        val hasContent =
            etMessage.text.toString().isNotBlank() ||
            selectedImageUris.isNotEmpty()

        val enabled =
            hasContent &&
            isActivityReady &&
            !isLoadingMessages

        btnSend.isEnabled = enabled
        btnSend.alpha = if (enabled) 1f else 0.5f
    }

    // =========================================================
    // Model loading
    // =========================================================

    private suspend fun loadLocalModel(modelFile: File) {
        if (!modelFile.exists() || modelFile.length() <= 0L) {
            showToast("ملف النموذج غير صالح")
            return
        }

        if (!modelManager.isEngineReady) {
            showToast("محرك النماذج غير متاح")
            return
        }

        showToast("جاري تحميل النموذج المحلي...")

        val result = modelManager.loadModelAsync(
            modelFile.absolutePath
        )

        result.fold(
            onSuccess = {
                isModelLoaded = true
                activeAgentId = 0

                btnLoadModel.setColorFilter(Color.GREEN)

                showToast("تم تحميل النموذج المحلي بنجاح")
            },
            onFailure = { error ->
                isModelLoaded = false

                btnLoadModel.setColorFilter(Color.RED)

                showToast(
                    "فشل تحميل النموذج: ${error.message}"
                )
            }
        )
    }

    private fun checkAndLoadModelOnStartup() {
        // قد يكون المحرك قد حُمّل مسبقاً بواسطة خدمة أخرى.
        if (modelManager.isTextModelLoaded) {
            isModelLoaded = true
            btnLoadModel.setColorFilter(Color.GREEN)
            return
        }

        val modelFile = File(
            filesDir,
            MODEL_FILE_NAME
        )

        if (!modelFile.exists() || modelFile.length() <= 0L) {
            isModelLoaded = false
            return
        }

        lifecycleScope.launch {
            try {
                loadLocalModel(modelFile)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                isModelLoaded = false
                btnLoadModel.setColorFilter(Color.RED)

                showToast(
                    "تعذر التحميل التلقائي: ${e.message}"
                )
            }
        }
    }

    private fun updateModelIndicators() {
        isModelLoaded = modelManager.isTextModelLoaded

        btnLoadModel.setColorFilter(
            if (modelManager.isTextModelLoaded) {
                Color.GREEN
            } else {
                Color.GRAY
            }
        )

        btnLoadVisionModel.setColorFilter(
            if (modelManager.isVisionModelLoaded) {
                Color.GREEN
            } else {
                Color.GRAY
            }
        )
    }

    // =========================================================
    // Database and messages
    // =========================================================

    private fun loadMessagesFromDb() {
        if (isLoadingMessages) return

        isLoadingMessages = true
        updateSendButtonState()

        lifecycleScope.launch {
            try {
                val loadedMessages = withContext(Dispatchers.IO) {
                    appDatabase
                        .getMessagesForConversation(
                            currentConversationId
                        )
                        .map {
                            it.toChatMessage()
                        }
                }

                messageList.clear()
                messageList.addAll(loadedMessages)

                chatAdapter.updateMessages(messageList)

                if (messageList.isNotEmpty()) {
                    scrollToBottom()
                }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showToast(
                    "تعذر تحميل سجل المحادثة: ${e.message}"
                )
            } finally {
                isLoadingMessages = false
                updateSendButtonState()
            }
        }
    }

    private fun saveMessageToDb(message: ChatMessage) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                databaseMutex.withLock {
                    appDatabase.insertMessage(
                        message.toMessageEntity(
                            currentConversationId
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
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

        messageList[index] = transform(
            messageList[index]
        )

        chatAdapter.updateMessages(messageList)
    }

    private fun findMessage(
        messageId: String
    ): ChatMessage? {
        return messageList.firstOrNull {
            it.id == messageId
        }
    }

    private fun MessageEntity.toChatMessage(): ChatMessage {
        return ChatMessage(
            id = id,

            role = if (role.equals("USER", true)) {
                MessageRole.USER
            } else {
                MessageRole.AI
            },

            content = content,

            attachmentUris = decodeAttachmentUris(
                attachmentUris
            ),

            colorHex = if (
                role.equals("USER", true)
            ) {
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

    // =========================================================
    // Sending messages
    // =========================================================

    private fun sendMessage() {
        if (!isActivityReady || isLoadingMessages) return

        val text = etMessage.text.toString().trim()

        val attachments = selectedImageUris.map {
            it.toString()
        }

        if (text.isBlank() && attachments.isEmpty()) {
            return
        }

        isModelLoaded = modelManager.isTextModelLoaded

        val apiKey = securePrefs.getString(
            "api_key",
            null
        )

        val selectedModel = securePrefs.getString(
            "selected_model",
            null
        )

        if (
            !isModelLoaded &&
            (
                apiKey.isNullOrBlank() ||
                selectedModel.isNullOrBlank()
            )
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

    // =========================================================
    // Local model and local vision
    // =========================================================

    private fun sendToLocalModel(
        userMessage: ChatMessage
    ) {
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
                val enrichedPrompt = buildString {
                    append(userMessage.content)

                    val attachments =
                        userMessage.attachmentUris.orEmpty()

                    if (attachments.isNotEmpty()) {
                        append("\n\n")

                        if (modelManager.isVisionModelLoaded) {
                            append(
                                analyzeLocalImageAttachments(
                                    attachments
                                )
                            )
                        } else {
                            append(
                                "ملاحظة للنموذج: توجد صور مرفقة، " +
                                "لكن نموذج الرؤية المحلي غير محمّل. " +
                                "لا تفترض أنك شاهدت محتوى الصور."
                            )
                        }
                    }
                }

                val result = agentBrain.executeTask(
                    userGoal = enrichedPrompt,
                    agentId = activeAgentId
                )

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
                    activeLocalJobs.remove(
                        statusMessage.id
                    )
                }
            }
        }

        synchronized(requestLock) {
            activeLocalJobs[statusMessage.id] = job
        }
    }

    private suspend fun analyzeLocalImageAttachments(
        attachments: List<String>
    ): String {
        val results = StringBuilder()

        attachments.forEachIndexed { index, uriString ->
            try {
                val uri = Uri.parse(uriString)

                val frame = decodeImageToDirectBuffer(uri)

                val analysis = modelManager.analyzeScreenFrame(
                    buffer = frame.first,
                    width = frame.second,
                    height = frame.third,
                    prompt = "حلل الصورة المرفقة رقم ${index + 1}. " +
                        "صف محتواها والعناصر المهمة والعلاقات بينها."
                )

                val error = try {
                    JSONObject(analysis).optString("error")
                } catch (_: Exception) {
                    ""
                }

                if (error.isNotBlank()) {
                    throw IllegalStateException(error)
                }

                results.append(
                    "\nنتيجة تحليل الصورة ${index + 1}:\n"
                )

                results.append(analysis)
                results.append("\n")

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                results.append(
                    "\nتعذر تحليل الصورة ${index + 1}: "
                )

                results.append(
                    e.message ?: "خطأ غير معروف"
                )

                results.append("\n")
            }
        }

        return results.toString()
    }

    /**
     * يحوّل الصورة إلى RGBA داخل Direct ByteBuffer.
     * هذا هو تنسيق الإدخال الذي يتوقعه vision_processor.cpp.
     */
    private suspend fun decodeImageToDirectBuffer(
        uri: Uri
    ): Triple<ByteBuffer, Int, Int> = withContext(Dispatchers.IO) {

        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(
                it,
                null,
                bounds
            )
        } ?: throw IllegalStateException(
            "تعذر قراءة أبعاد الصورة"
        )

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IllegalArgumentException(
                "الصورة غير صالحة"
            )
        }

        var sampleSize = 1

        while (
            bounds.outWidth / sampleSize > MAX_IMAGE_DIMENSION ||
            bounds.outHeight / sampleSize > MAX_IMAGE_DIMENSION
        ) {
            sampleSize *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        var bitmap = contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(
                it,
                null,
                options
            )
        } ?: throw IllegalStateException(
            "تعذر فك ترميز الصورة"
        )

        if (
            bitmap.width > MAX_IMAGE_DIMENSION ||
            bitmap.height > MAX_IMAGE_DIMENSION
        ) {
            val ratio = minOf(
                MAX_IMAGE_DIMENSION.toFloat() / bitmap.width,
                MAX_IMAGE_DIMENSION.toFloat() / bitmap.height
            )

            val scaled = Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * ratio).toInt().coerceAtLeast(1),
                (bitmap.height * ratio).toInt().coerceAtLeast(1),
                true
            )

            if (scaled !== bitmap) {
                bitmap.recycle()
            }

            bitmap = scaled
        }

        if (bitmap.config != Bitmap.Config.ARGB_8888) {
            val converted = bitmap.copy(
                Bitmap.Config.ARGB_8888,
                false
            ) ?: run {
                bitmap.recycle()
                throw IllegalStateException(
                    "تعذر تحويل الصورة إلى ARGB_8888"
                )
            }

            bitmap.recycle()
            bitmap = converted
        }

        try {
            val width = bitmap.width
            val height = bitmap.height

            val buffer = ByteBuffer.allocateDirect(
                width * height * 4
            ).order(ByteOrder.nativeOrder())

            bitmap.copyPixelsToBuffer(buffer)
            buffer.flip()

            Triple(
                buffer,
                width,
                height
            )

        } finally {
            bitmap.recycle()
        }
    }

    // =========================================================
    // Cloud model and multimodal request
    // =========================================================

    private fun sendToCloudModel(
        userMessage: ChatMessage,
        selectedModel: String,
        apiKey: String
    ) {
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

        lifecycleScope.launch {
            try {
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
                        "أنت Monolith في وضع المحلل النقدي. " +
                        "حلل المعطيات بدقة وميّز بين الحقائق والافتراضات."

                    "objective" ->
                        "أنت Monolith في الوضع الموضوعي. " +
                        "قدم إجابات متوازنة ودقيقة دون تحيز."

                    "innovator" ->
                        "أنت Monolith في وضع الابتكار. " +
                        "اقترح حلولاً عملية وأفكاراً جديدة مع توضيح حدودها."

                    else ->
                        "أنت Monolith، مساعد ذكي. " +
                        "أجب بوضوح ودقة ولا تدّعِ تنفيذ شيء لم تنفذه."
                }

                val history = messageList
                    .filter {
                        !it.isThinking &&
                        it.id != userMessage.id
                    }
                    .takeLast(15)
                    .toList()

                val apiMessages = mutableListOf<Message>()

                apiMessages.add(
                    Message(
                        role = "system",
                        content = personaPrompt
                    )
                )

                history.forEach { message ->
                    apiMessages.add(
                        Message(
                            role = if (
                                message.role == MessageRole.USER
                            ) {
                                "user"
                            } else {
                                "assistant"
                            },
                            content = message.content
                        )
                    )
                }

                val currentContentParts =
                    mutableListOf<ContentPart>()

                if (userMessage.content.isNotBlank()) {
                    currentContentParts.add(
                        ContentPart(
                            type = "text",
                            text = userMessage.content
                        )
                    )
                }

                val imageErrors = mutableListOf<String>()

                userMessage.attachmentUris.orEmpty()
                    .forEachIndexed { index, uriString ->

                        try {
                            val dataUrl = encodeImageAsDataUrl(
                                Uri.parse(uriString)
                            )

                            currentContentParts.add(
                                ContentPart(
                                    type = "image_url",
                                    imageUrl = ImageUrl(dataUrl)
                                )
                            )

                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            imageErrors.add(
                                "تعذر تجهيز الصورة ${index + 1}: " +
                                (e.message ?: "خطأ غير معروف")
                            )
                        }
                    }

                if (imageErrors.isNotEmpty()) {
                    currentContentParts.add(
                        ContentPart(
                            type = "text",
                            text = imageErrors.joinToString("\n")
                        )
                    )
                }

                val currentContent: Any =
                    if (currentContentParts.size == 1 &&
                        currentContentParts[0].type == "text"
                    ) {
                        currentContentParts[0].text ?: ""
                    } else {
                        currentContentParts
                    }

                apiMessages.add(
                    Message(
                        role = "user",
                        content = currentContent
                    )
                )

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
                                activeApiCalls.remove(
                                    statusMessage.id
                                )
                            }

                            if (
                                isFinishing ||
                                isDestroyed ||
                                call.isCanceled
                            ) {
                                return
                            }

                            if (response.isSuccessful) {
                                val responseMessage = response.body()
                                    ?.choices
                                    ?.firstOrNull()
                                    ?.message

                                val reply = responseMessage
                                    ?.content
                                    ?.takeIf {
                                        it.isNotBlank()
                                    }
                                    ?: if (
                                        !responseMessage
                                            ?.toolCalls
                                            .isNullOrEmpty()
                                    ) {
                                        "وصل طلب لاستخدام أداة، " +
                                        "لكن تنفيذ أدوات API غير موصول " +
                                        "في مسار المحادثة الحالي."
                                    } else {
                                        "لم يتم استلام رد صالح من الخادم."
                                    }

                                updateRequestMessage(
                                    messageId = statusMessage.id,
                                    content = reply,
                                    isThinking = false,
                                    modelName = selectedModel
                                )

                            } else {
                                val errorText = try {
                                    response.errorBody()
                                        ?.string()
                                        ?.take(1000)
                                } catch (_: Exception) {
                                    null
                                }

                                updateRequestMessage(
                                    messageId = statusMessage.id,
                                    content = buildString {
                                        append(
                                            "فشل الطلب. رمز الاستجابة: "
                                        )
                                        append(response.code())

                                        if (!errorText.isNullOrBlank()) {
                                            append("\n")
                                            append(errorText)
                                        }
                                    },
                                    isThinking = false
                                )
                            }
                        }

                        override fun onFailure(
                            call: Call<ChatResponse>,
                            t: Throwable
                        ) {
                            synchronized(requestLock) {
                                activeApiCalls.remove(
                                    statusMessage.id
                                )
                            }

                            if (
                                isFinishing ||
                                isDestroyed ||
                                call.isCanceled
                            ) {
                                return
                            }

                            updateRequestMessage(
                                messageId = statusMessage.id,
                                content = "تعذر الاتصال بالخادم: " +
                                    (t.message ?: "خطأ غير معروف"),
                                isThinking = false
                            )
                        }
                    }
                )

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateRequestMessage(
                    messageId = statusMessage.id,
                    content = "تعذر إعداد الطلب السحابي: ${e.message}",
                    isThinking = false
                )
            }
        }
    }

    private suspend fun encodeImageAsDataUrl(
        uri: Uri
    ): String = withContext(Dispatchers.IO) {

        val mimeType = contentResolver.getType(uri)
            ?: "image/jpeg"

        require(mimeType.startsWith("image/")) {
            "الملف المحدد ليس صورة"
        }

        val output = ByteArrayOutputStream()

        val input = contentResolver.openInputStream(uri)
            ?: throw IllegalStateException(
                "تعذر فتح الصورة"
            )

        input.use { stream ->
            val buffer = ByteArray(8192)
            var totalBytes = 0

            while (true) {
                val count = stream.read(buffer)

                if (count < 0) break

                totalBytes += count

                require(totalBytes <= MAX_IMAGE_BYTES) {
                    "حجم الصورة أكبر من الحد المسموح للإرسال السحابي"
                }

                output.write(buffer, 0, count)
            }
        }

        val encoded = Base64.encodeToString(
            output.toByteArray(),
            Base64.NO_WRAP
        )

        "data:$mimeType;base64,$encoded"
    }

    // =========================================================
    // Updating response messages
    // =========================================================

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

    // =========================================================
    // Scrolling and keyboard
    // =========================================================

    private fun scrollToBottom() {
        recyclerViewChat.post {
            if (
                messageList.isNotEmpty() &&
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
            val inputMethodManager = getSystemService(
                Context.INPUT_METHOD_SERVICE
            ) as InputMethodManager

            inputMethodManager.hideSoftInputFromWindow(
                etMessage.windowToken,
                0
            )
        } catch (_: Exception) {
        }
    }

    // =========================================================
    // Image preview
    // =========================================================

    private fun updatePreviewUi() {
        previewListContainer.removeAllViews()

        if (selectedImageUris.isEmpty()) {
            imagePreviewContainer.visibility =
                View.GONE

            updateSendButtonState()
            return
        }

        imagePreviewContainer.visibility =
            View.VISIBLE

        selectedImageUris.toList().forEach { uri ->

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

            val container = RelativeLayout(this)

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
                val params = RelativeLayout.LayoutParams(
                    dpToPx(20),
                    dpToPx(20)
                )

                params.addRule(
                    RelativeLayout.ALIGN_PARENT_TOP
                )

                params.addRule(
                    RelativeLayout.ALIGN_PARENT_END
                )

                layoutParams = params

                text = "×"
                textSize = 15f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setBackgroundResource(
                    R.drawable.bg_circle_dark
                )

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

    // =========================================================
    // Share conversation
    // =========================================================

    private fun shareFullConversation() {
        if (messageList.isEmpty()) {
            showToast("لا توجد رسائل لمشاركتها")
            return
        }

        val builder = StringBuilder()

        builder.append("سجل محادثة Monolith\n\n")

        messageList.forEach { message ->
            val sender = if (
                message.role == MessageRole.USER
            ) {
                "أنت"
            } else {
                "Monolith"
            }

            builder.append(sender)
            builder.append(": ")
            builder.append(message.content)
            builder.append("\n")

            if (!message.attachmentUris.isNullOrEmpty()) {
                builder.append(
                    "[عدد المرفقات: " +
                    "${message.attachmentUris.size}]\n"
                )
            }

            builder.append("\n")
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_TEXT,
                builder.toString()
            )
        }

        startActivity(
            Intent.createChooser(
                intent,
                "مشاركة سجل المحادثة"
            )
        )
    }

    // =========================================================
    // Chromatic threads
    // =========================================================

    private fun showThreadsBottomSheet() {
        val dialog = BottomSheetDialog(this)

        val listView = ListView(this)

        val items = arrayOf(
            "إنشاء خيط لوني جديد",
            "ربط رسالتين بخيط",
            "عرض الخيوط اللونية",
            "إضافة ملاحظة إلى خيط",
            "عرض ملاحظات خيط"
        )

        listView.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            items
        )

        listView.setOnItemClickListener {
                _, _, position, _ ->

            dialog.dismiss()

            when (position) {
                0 -> showCreateNodeDialog()
                1 -> showLinkMessagesDialog()
                2 -> showNodesListDialog()
                3 -> showAddThreadAnnotationDialog()
                4 -> showThreadAnnotationsDialog()
            }
        }

        dialog.setContentView(listView)
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
            hint = "عنوان الخيط اللوني"
        }

        val definitionInput = EditText(this).apply {
            hint = "تعريف الخيط أو الغرض منه"
            minLines = 2
        }

        val colorInput = EditText(this).apply {
            hint = "لون HEX مثال: #00D4FF"
            setText("#00D4FF")
            singleLine = true
        }

        layout.addView(nameInput)
        layout.addView(definitionInput)
        layout.addView(colorInput)

        AlertDialog.Builder(this)
            .setTitle("إنشاء خيط لوني")
            .setView(layout)
            .setPositiveButton("حفظ") { _, _ ->

                val name = nameInput.text.toString().trim()

                val definition =
                    definitionInput.text.toString().trim()

                val color = colorInput.text.toString().trim()

                if (
                    name.isBlank() ||
                    definition.isBlank() ||
                    color.isBlank()
                ) {
                    showToast("أكمل بيانات الخيط")
                    return@setPositiveButton
                }

                try {
                    Color.parseColor(color)
                } catch (_: Exception) {
                    showToast("صيغة اللون غير صحيحة")
                    return@setPositiveButton
                }

                lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            appDatabase.insertChromaticNode(
                                colorHex = color,
                                name = name,
                                definition = definition
                            )
                        }

                        showToast("تم إنشاء الخيط اللوني")

                    } catch (e: Exception) {
                        showToast(
                            "تعذر حفظ الخيط: ${e.message}"
                        )
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
                    showToast("لا توجد خيوط لونية")
                    return@launch
                }

                val displayList = nodes.map {
                    val name = it["name"] ?: "خيط"
                    val color = it["color_hex"] ?: ""
                    val definition = it["definition"] ?: ""

                    "$name ($color)\n$definition"
                }

                AlertDialog.Builder(this@ChatActivity)
                    .setTitle("الخيوط اللونية")
                    .setItems(
                        displayList.toTypedArray(),
                        null
                    )
                    .setPositiveButton("حسناً", null)
                    .show()

            } catch (e: Exception) {
                showToast(
                    "تعذر جلب الخيوط: ${e.message}"
                )
            }
        }
    }

    private fun showLinkMessagesDialog() {
        val messageSnapshot = messageList
            .filter { !it.isThinking }
            .toList()

        if (messageSnapshot.size < 2) {
            showToast("تحتاج إلى رسالتين على الأقل")
            return
        }

        lifecycleScope.launch {
            try {
                val nodes = withContext(Dispatchers.IO) {
                    appDatabase.getAllChromaticNodes()
                }

                if (nodes.isEmpty()) {
                    showToast("أنشئ خيطاً لونياً أولاً")
                    return@launch
                }

                val messageOptions = messageSnapshot.map {
                    val sender = if (
                        it.role == MessageRole.USER
                    ) {
                        "أنت"
                    } else {
                        "Monolith"
                    }

                    "$sender: ${it.content.take(55)}"
                }.toTypedArray()

                AlertDialog.Builder(this@ChatActivity)
                    .setTitle("اختر الرسالة الأولى")
                    .setItems(messageOptions) {
                            _, sourceIndex ->

                        AlertDialog.Builder(this@ChatActivity)
                            .setTitle("اختر الرسالة الثانية")
                            .setItems(messageOptions) {
                                    _, targetIndex ->

                                if (sourceIndex == targetIndex) {
                                    showToast(
                                        "لا يمكن ربط الرسالة بنفسها"
                                    )
                                    return@setItems
                                }

                                val nodeOptions = nodes.map {
                                    val name = it["name"] ?: "خيط"
                                    val color = it["color_hex"] ?: ""
                                    "$name ($color)"
                                }.toTypedArray()

                                AlertDialog.Builder(this@ChatActivity)
                                    .setTitle("اختر الخيط اللوني")
                                    .setItems(nodeOptions) {
                                            _, nodeIndex ->

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

                                                showToast(
                                                    "تم ربط الرسالتين بالخيط"
                                                )

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
                showToast(
                    "تعذر تحميل الخيوط: ${e.message}"
                )
            }
        }
    }

    private fun showAddThreadAnnotationDialog() {
        selectThreadForMessage(
            "اختر خيطاً لإضافة ملاحظة"
        ) { threadId ->

            val input = EditText(this).apply {
                hint = "اكتب الملاحظة"
                minLines = 3
                setPadding(
                    dpToPx(12),
                    dpToPx(12),
                    dpToPx(12),
                    dpToPx(12)
                )
            }

            AlertDialog.Builder(this)
                .setTitle("ملاحظة الخيط")
                .setView(input)
                .setPositiveButton("حفظ") { _, _ ->

                    val content = input.text.toString().trim()

                    if (content.isBlank()) {
                        showToast("الملاحظة فارغة")
                        return@setPositiveButton
                    }

                    lifecycleScope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                appDatabase.insertThreadAnnotation(
                                    threadId,
                                    content
                                )
                            }

                            showToast("تم حفظ ملاحظة الخيط")

                        } catch (e: Exception) {
                            showToast(
                                "تعذر حفظ الملاحظة: ${e.message}"
                            )
                        }
                    }
                }
                .setNegativeButton("إلغاء", null)
                .show()
        }
    }

    private fun showThreadAnnotationsDialog() {
        selectThreadForMessage(
            "اختر خيطاً لعرض ملاحظاته"
        ) { threadId ->

            lifecycleScope.launch {
                try {
                    val annotations = withContext(Dispatchers.IO) {
                        appDatabase.getAnnotationsForThread(
                            threadId
                        )
                    }

                    val content = if (annotations.isEmpty()) {
                        "لا توجد ملاحظات لهذا الخيط."
                    } else {
                        annotations.mapIndexed { index, note ->
                            "${index + 1}. $note"
                        }.joinToString("\n\n")
                    }

                    AlertDialog.Builder(this@ChatActivity)
                        .setTitle("ملاحظات الخيط")
                        .setMessage(content)
                        .setPositiveButton("حسناً", null)
                        .show()

                } catch (e: Exception) {
                    showToast(
                        "تعذر جلب الملاحظات: ${e.message}"
                    )
                }
            }
        }
    }

    private fun selectThreadForMessage(
        title: String,
        onSelected: (String) -> Unit
    ) {
        val messages = messageList
            .filter { !it.isThinking }
            .toList()

        if (messages.isEmpty()) {
            showToast("لا توجد رسائل")
            return
        }

        val messageOptions = messages.map {
            "${it.content.take(55)}"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("اختر رسالة مرتبطة بالخيط")
            .setItems(messageOptions) { _, index ->

                lifecycleScope.launch {
                    try {
                        val threads = withContext(Dispatchers.IO) {
                            appDatabase.getThreadsForMessage(
                                messages[index].id
                            )
                        }

                        if (threads.isEmpty()) {
                            showToast(
                                "هذه الرسالة لا ترتبط بأي خيط"
                            )
                            return@launch
                        }

                        val nodes = withContext(Dispatchers.IO) {
                            appDatabase.getAllChromaticNodes()
                        }

                        val nodeMap = nodes.associateBy {
                            it["id"]
                        }

                        val options = threads.map { thread ->
                            val node = nodeMap[
                                thread["node_id"]
                            ]

                            val name = node?.get("name")
                                ?: "خيط غير معروف"

                            val color = node?.get("color_hex")
                                ?: ""

                            "$name ($color)"
                        }.toTypedArray()

                        AlertDialog.Builder(this@ChatActivity)
                            .setTitle(title)
                            .setItems(options) { _, threadIndex ->
                                val threadId =
                                    threads[threadIndex]["id"]

                                if (!threadId.isNullOrBlank()) {
                                    onSelected(threadId)
                                }
                            }
                            .show()

                    } catch (e: Exception) {
                        showToast(
                            "تعذر جلب الخيوط: ${e.message}"
                        )
                    }
                }
            }
            .show()
    }

    // =========================================================
    // Agents
    // =========================================================

    private fun showAgentsBottomSheet() {
        val dialog = BottomSheetDialog(this)

        val listView = ListView(this)

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

        listView.setOnItemClickListener {
                _, _, position, _ ->

            when (position) {
                0 -> {
                    activeAgentId = 0

                    showToast(
                        "تم تفعيل الوكيل الرئيسي"
                    )

                    dialog.dismiss()
                }

                1 -> {
                    if (!modelManager.isTextModelLoaded) {
                        showToast(
                            "حمّل النموذج المحلي أولاً"
                        )
                        return@setOnItemClickListener
                    }

                    try {
                        val newId =
                            modelManager.createSubAgent()

                        if (newId > 0) {
                            activeAgentId = newId

                            showToast(
                                "تم إنشاء الوكيل $newId"
                            )
                        } else {
                            showToast(
                                "تعذر إنشاء الوكيل الفرعي"
                            )
                        }

                    } catch (e: Exception) {
                        showToast(
                            "خطأ في إنشاء الوكيل: ${e.message}"
                        )
                    }

                    dialog.dismiss()
                }

                2 -> {
                    try {
                        NetworkManager.startP2PServer()

                        showToast(
                            "تم استدعاء خدمة P2P. " +
                            "تنفيذ الاتصال الفعلي بالأجهزة ما زال منفصلاً."
                        )
                    } catch (e: Exception) {
                        showToast(
                            "تعذر تشغيل P2P: ${e.message}"
                        )
                    }

                    dialog.dismiss()
                }
            }
        }

        dialog.setContentView(listView)
        dialog.show()
    }

    // =========================================================
    // Controls
    // =========================================================

    private fun showControlsBottomSheet() {
        val dialog = BottomSheetDialog(this)

        val view = layoutInflater.inflate(
            R.layout.bottom_sheet_controls,
            null
        )

        val temperatureBar = view.findViewById<SeekBar>(
            R.id.sbTemperature
        )

        val temperatureValue = view.findViewById<TextView>(
            R.id.tvTemperatureValue
        )

        val personaSpinner = view.findViewById<Spinner>(
            R.id.spinnerPersona
        )

        val thinkingCheckbox = view.findViewById<CheckBox>(
            R.id.cbThinking
        )

        val saveButton = view.findViewById<Button>(
            R.id.btnSaveControls
        )

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

        val selectedIndex =
            personaKeys.indexOf(savedPersona)

        if (selectedIndex >= 0) {
            personaSpinner.setSelection(selectedIndex)
        }

        thinkingCheckbox.isChecked =
            securePrefs.getBoolean(
                "enable_thinking",
                false
            )

        temperatureBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {

                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    temperatureValue.text =
                        String.format(
                            "%.1f",
                            progress / 100f
                        )
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
                personaKeys[
                    personaSpinner.selectedItemPosition
                        .coerceIn(personaKeys.indices)
                ]

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

    // =========================================================
    // Attachments
    // =========================================================

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

            pickImagesLauncher.launch(
                arrayOf("image/*")
            )
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

            showToast(
                "تحليل الملفات الصوتية لم يُربط بعد"
            )
        }

        view.findViewById<LinearLayout>(
            R.id.btnOptionSearch
        )?.setOnClickListener {
            dialog.dismiss()

            showToast(
                "البحث الخارجي يحتاج إلى ربط خدمة البحث"
            )
        }

        view.findViewById<LinearLayout>(
            R.id.btnOptionCamera
        )?.setOnClickListener {
            dialog.dismiss()

            showToast(
                "التقاط الكاميرا يحتاج إلى ربط مسار الكاميرا"
            )
        }

        view.findViewById<LinearLayout>(
            R.id.btnOptionAI
        )?.setOnClickListener {
            dialog.dismiss()

            showToast(
                "اختر النموذج من شريط الأدوات"
            )
        }

        dialog.show()
    }

    // =========================================================
    // Pinned messages
    // =========================================================

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

                val dialog = BottomSheetDialog(
                    this@ChatActivity
                )

                val listView = ListView(
                    this@ChatActivity
                )

                listView.adapter = ArrayAdapter(
                    this@ChatActivity,
                    android.R.layout.simple_list_item_1,
                    pinnedMessages.map {
                        it.content
                    }
                )

                listView.setOnItemClickListener {
                        _, _, position, _ ->

                    val id = pinnedMessages[position].id

                    val index = messageList.indexOfFirst {
                        it.id == id
                    }

                    if (index >= 0) {
                        recyclerViewChat.smoothScrollToPosition(
                            index
                        )
                    }

                    dialog.dismiss()
                }

                dialog.setContentView(listView)
                dialog.show()

            } catch (e: Exception) {
                showToast(
                    "تعذر تحميل الرسائل المثبتة: ${e.message}"
                )
            }
        }
    }

    // =========================================================
    // Dashboard
    // =========================================================

    private fun showDashboardBottomSheet() {
        val dialog = BottomSheetDialog(this)

        val view = layoutInflater.inflate(
            R.layout.bottom_sheet_status,
            null
        )

        val apiKey = securePrefs.getString(
            "api_key",
            null
        )

        val selectedModel = securePrefs.getString(
            "selected_model",
            null
        ) ?: "غير محدد"

        val provider = when {
            apiKey.isNullOrBlank() -> "غير مهيأ"
            selectedModel.contains("groq", true) -> "Groq"
            selectedModel.contains("gpt", true) -> "OpenAI"
            else -> "مخصص"
        }

        val connectionStatus = view.findViewById<TextView>(
            R.id.tvConnectionStatus
        )

        val connectionIcon = view.findViewById<ImageView>(
            R.id.ivConnectionIcon
        )

        val providerName = view.findViewById<TextView>(
            R.id.tvProviderName
        )

        val modelName = view.findViewById<TextView>(
            R.id.tvModelName
        )

        val messageCount = view.findViewById<TextView>(
            R.id.tvMessageCount
        )

        val settingsButton = view.findViewById<Button>(
            R.id.btnGoToSettings
        )

        val configured = !apiKey.isNullOrBlank()

        connectionStatus.text = if (configured) {
            "مفتاح الاتصال مهيأ"
        } else {
            "غير مهيأ"
        }

        val statusColor = if (configured) {
            Color.GREEN
        } else {
            Color.RED
        }

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

    // =========================================================
    // Edit messages
    // =========================================================

    private fun showEditDialog(message: ChatMessage) {
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
            setText(message.content)
            hint = "النص الجديد"
            setTextColor(Color.WHITE)
            minLines = 2
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
                    showToast(
                        "لا يمكن حفظ رسالة فارغة"
                    )
                    return@setPositiveButton
                }

                val editTime = System.currentTimeMillis()

                lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            appDatabase.updateMessageContent(
                                messageId = message.id,
                                newContent = newContent,
                                originalContent = message.content,
                                reason = reason.ifBlank {
                                    null
                                },
                                timestamp = editTime
                            )
                        }

                        val index = messageList.indexOfFirst {
                            it.id == message.id
                        }

                        if (index >= 0) {
                            val updated = messageList[index].copy(
                                content = newContent,
                                isEdited = true,
                                originalContent =
                                    message.originalContent
                                        ?: message.content,
                                editReason = reason.ifBlank {
                                    null
                                },
                                editedTimestamp = editTime
                            )

                            messageList[index] = updated

                            chatAdapter.updateMessages(
                                messageList
                            )

                            saveMessageToDb(updated)
                        }

                        showToast("تم تعديل الرسالة")

                    } catch (e: Exception) {
                        showToast(
                            "فشل تعديل الرسالة: ${e.message}"
                        )
                    }
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    // =========================================================
    // Theme and background
    // =========================================================

    override fun onResume() {
        super.onResume()

        if (!isActivityReady) return

        applyCurrentTheme()
        updateModelIndicators()
    }

    private fun applyCurrentTheme() {
        val settings = getSharedPreferences(
            "Settings",
            MODE_PRIVATE
        )

        val rootLayout = findViewById<LinearLayout>(
            R.id.rootLayout
        )

        val previousTheme = themeColorHex

        themeColorHex = settings.getString(
            "AppThemeColor",
            "#00D4FF"
        ) ?: "#00D4FF"

        val themeColor = try {
            Color.parseColor(themeColorHex)
        } catch (_: Exception) {
            themeColorHex = "#00D4FF"
            Color.parseColor(themeColorHex)
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

        // ChatAdapter يحتفظ بلون الإنشاء؛ لذلك نعيد إنشاءه
        // فقط عند تغيّر لون المستخدم، مع الحفاظ على الرسائل.
        if (
            previousTheme != themeColorHex &&
            ::chatAdapter.isInitialized
        ) {
            initializeAdapter()
        }

        val backgroundUri = settings.getString(
            "AppBackgroundImage",
            null
        )

        if (backgroundUri.isNullOrBlank()) {
            applyColorBackground(
                settings,
                rootLayout
            )

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
                            inPreferredConfig =
                                Bitmap.Config.ARGB_8888
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
                rootLayout.background = BitmapDrawable(
                    resources,
                    bitmap
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    rootLayout.foreground = ColorDrawable(
                        Color.parseColor("#55000000")
                    )
                }
            } else {
                applyColorBackground(
                    settings,
                    rootLayout
                )
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

    // =========================================================
    // Memory integration point
    // =========================================================

    /*
     * لم أضف استنتاجات تلقائية عن المستخدم هنا.
     * ربط الذاكرة طويلة الأمد يجب أن يتم عبر واجهة ذاكرة
     * فعلية ومحددة، وليس عبر تخمينات من نص الرسالة.
     */

    private fun analyzeUserMessage(message: String) {
        // نقطة ربط مستقبلية.
    }

    private suspend fun getMemoriesContext(): String {
        return ""
    }

    // =========================================================
    // TTS
    // =========================================================

    private fun initTtsIfNeeded() {
        if (ttsManager == null) {
            ttsManager = TtsManager(this) {
                runOnUiThread {
                    // لا توجد عملية واجهة مطلوبة حالياً.
                }
            }
        }
    }

    // =========================================================
    // Utilities
    // =========================================================

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

    // =========================================================
    // Cleanup
    // =========================================================

    override fun onDestroy() {
        isActivityReady = false

        synchronized(requestLock) {
            activeApiCalls.values.forEach { call ->
                try {
                    call.cancel()
                } catch (_: Exception) {
                }
            }

            activeApiCalls.clear()

            activeLocalJobs.values.forEach { job ->
                job.cancel()
            }

            activeLocalJobs.clear()
        }

        try {
            ttsManager?.shutdown()
        } catch (_: Exception) {
        }

        ttsManager = null

        // لا نغلق ModelManager هنا؛ فهو محرك مشترك
        // قد تستخدمه خدمات أخرى خارج عمر ChatActivity.

        super.onDestroy()
    }
}

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
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
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
import java.util.concurrent.ConcurrentHashMap

class ChatActivity : AppCompatActivity() {

    companion object {
        private const val CONVERSATION_ID = AppDatabase.MAIN_CONVERSATION_ID
        private const val MODEL_FILE_NAME = "model.gguf"
        private const val VISION_MODEL_FILE_NAME = "vision_model.gguf"
        private const val VISION_PROJECT_FILE_NAME = "vision_mmproj.gguf"
        private const val STATUS_LOCAL = "جاري تنفيذ الطلب..."
        private const val STATUS_CLOUD = "جاري إعداد الرد..."
        private const val MAX_IMAGE_BYTES = 8 * 1024 * 1024
        private const val MAX_IMAGE_DIMENSION = 1536

        // سجل المحادثات
        private const val DEFAULT_CONVERSATION_TITLE = "محادثة جديدة"
        private const val MAIN_CONVERSATION_TITLE = "المحادثة الرئيسية"
        private const val MAX_CONVERSATION_TITLE_LENGTH = 60
        private const val AUTO_TITLE_LENGTH = 40
        private const val HISTORY_MAX_VISIBLE_ROWS = 6
        private const val CONVERSATION_STATE_PREFS = "ConversationState"
        private const val KEY_LAST_CONVERSATION_ID = "last_conversation_id"

        // المبادرات الحوارية: مصدرها النموذج فقط عبر DialogInitiativeProtocol (أسفل الملف).
        private const val DEFAULT_INPUT_HINT = "اكتب رسالتك..."
        private const val INITIATIVES_IN_CLOUD_PROMPT = true
        private const val INITIATIVES_IN_LOCAL_PROMPT = true
        private const val EMPTY_REPLY_WITH_INITIATIVES_NOTICE =
            "لم يُرجع النموذج نصاً في ردّه، وأرسل مبادرات حوارية فقط."

        private const val PREFS_NAME = "Settings"
    }

    /**
     * طلب غير مكتمل (محلي أو سحابي). يحتفظ بمعرّف المحادثة التي بدأ فيها الطلب،
     * حتى لا يُحفظ الرد المتأخر في محادثة أخرى بعد التبديل.
     */
    private data class PendingRequest(
        val conversationId: String,
        val placeholder: ChatMessage
    )

    private val selectedImageUris = mutableListOf<Uri>()

    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var imagePreviewContainer: LinearLayout
    private lateinit var previewListContainer: LinearLayout
            private lateinit var recyclerViewChat: RecyclerView
    private lateinit var chatContentContainer: FrameLayout
    private lateinit var chatAdapter: ChatAdapter
    private var messageCardBridge: MessageCardBridge? = null
    private lateinit var tvAppTitle: TextView

    // عناصر سجل المحادثات (من activity_chat.xml)
    private lateinit var conversationHistoryPopup: LinearLayout
    private lateinit var conversationHistoryList: LinearLayout
    private lateinit var conversationHistoryScroll: ScrollView
    private lateinit var tvEmptyConversationHistory: TextView
    private lateinit var btnCreateConversation: TextView
    private lateinit var tvCurrentConversation: TextView
    private lateinit var tvHistoryLabel: TextView

    // شريط المبادرات الحوارية: يُملأ ديناميكياً من ردّ النموذج فقط، ويكون مخفياً عند عدم وجودها.
    private lateinit var suggestionsScroll: HorizontalScrollView
    private lateinit var suggestionsContainer: LinearLayout
    private var currentInitiatives: List<DialogInitiative> = emptyList()

    private var themeColorHex = "#00D4FF"

    private lateinit var securePrefs: SharedPreferences
    private lateinit var appDatabase: AppDatabase
    private lateinit var modelManager: ModelManager
    private lateinit var agentBrain: AgentBrain
    private lateinit var memoryHelper: MemoryHelper

    private val messageList = mutableListOf<ChatMessage>()

    // معرّف المحادثة الحالية: لم يعد ثابتاً، ويتبدل عبر openConversation() فقط.
    private var currentConversationId: String = CONVERSATION_ID

    private var ttsManager: TtsManager? = null
    private var isModelLoaded = false
    private var activeAgentId = 0
    private var pendingVisionGgufPath: String? = null
    private var isActivityReady = false
    private var isLoadingMessages = false
    private var loadGeneration = 0

    private val databaseMutex = Mutex()
    private val requestLock = Any()

    private val activeApiCalls = mutableMapOf<String, Call<ChatResponse>>()
    private val activeLocalJobs = mutableMapOf<String, Job>()

    // statusMessage.id -> الطلب غير المكتمل ومعرّف محادثته
    private val pendingRequests = mutableMapOf<String, PendingRequest>()

    // محادثات حُذفت في هذه الجلسة: يمنع حفظ أي رد متأخر داخلها
    private val deletedConversationIds: MutableSet<String> = ConcurrentHashMap.newKeySet<String>()

    private val historyBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            hideConversationHistory()
        }
    }

    private val pickImagesLauncher =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
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
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            lifecycleScope.launch {
                try {
                    persistReadPermission(uri)
                    val text = withContext(Dispatchers.IO) {
                        contentResolver.openInputStream(uri)?.use {
                            it.bufferedReader().use { reader -> reader.readText() }
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
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            lifecycleScope.launch {
                try {
                    showToast("جاري نسخ النموذج...")
                    val modelFile = File(filesDir, MODEL_FILE_NAME)
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
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            lifecycleScope.launch {
                try {
                    showToast("جاري نسخ نموذج الرؤية...")
                    val visionFile = File(filesDir, VISION_MODEL_FILE_NAME)
                    copyUriToFileAtomically(uri, visionFile)
                    pendingVisionGgufPath = visionFile.absolutePath
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
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            lifecycleScope.launch {
                try {
                    val visionPath = pendingVisionGgufPath
                    if (visionPath.isNullOrBlank()) {
                        showToast("لم يتم تحديد نموذج الرؤية أولاً")
                        return@launch
                    }
                    showToast("جاري نسخ ملف mmproj...")
                    val mmprojFile = File(filesDir, VISION_PROJECT_FILE_NAME)
                    copyUriToFileAtomically(uri, mmprojFile)

                    val result = withContext(Dispatchers.IO) {
                        modelManager.loadVisionModelAsync(visionPath, mmprojFile.absolutePath)
                    }

                    result.fold(
                        onSuccess = {
                            updateEngineStatus()
                            showToast("تم تحميل نموذج الرؤية بنجاح")
                        },
                        onFailure = { error ->
                            updateEngineStatus()
                            showToast("فشل تحميل نموذج الرؤية: ${error.message}")
                        }
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    updateEngineStatus()
                    showToast("خطأ في إعداد الرؤية: ${e.message}")
                } finally {
                    pendingVisionGgufPath = null
                }
            }
        }

    private suspend fun copyUriToFileAtomically(uri: Uri, destination: File) = withContext(Dispatchers.IO) {
        val parent = destination.parentFile ?: filesDir
        if (!parent.exists() && !parent.mkdirs()) {
            throw IllegalStateException("تعذر إنشاء مجلد تخزين النموذج")
        }

        val temporaryFile = File(parent, destination.name + ".tmp")
        val backupFile = File(parent, destination.name + ".bak")

        try {
            val input = contentResolver.openInputStream(uri) ?: throw IllegalStateException("تعذر فتح الملف المحدد")
            input.use { source ->
                FileOutputStream(temporaryFile).use { output ->
                    source.copyTo(output)
                    output.fd.sync()
                }
            }

            if (temporaryFile.length() <= 0L) throw IllegalStateException("الملف المحدد فارغ")
            if (backupFile.exists()) backupFile.delete()

            var oldFileMoved = false
            if (destination.exists()) {
                oldFileMoved = destination.renameTo(backupFile)
                if (!oldFileMoved) throw IllegalStateException("تعذر حفظ النسخة السابقة قبل الاستبدال")
            }

            if (!temporaryFile.renameTo(destination)) {
                if (oldFileMoved) backupFile.renameTo(destination)
                throw IllegalStateException("تعذر نقل الملف المؤقت إلى موقعه النهائي")
            }
            if (backupFile.exists()) backupFile.delete()
        } catch (e: CancellationException) {
            temporaryFile.delete()
            throw e
        } catch (e: Exception) {
            temporaryFile.delete()
            if (backupFile.exists() && !destination.exists()) {
                backupFile.renameTo(destination)
            }
            throw e
        }
    }

    private fun persistReadPermission(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) { }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        try {
            ApiClient.init(applicationContext)
            appDatabase = AppDatabase(applicationContext)
            modelManager = ModelManager.getInstance(applicationContext)
            agentBrain = AgentBrainFactory.create(applicationContext, modelManager)
            memoryHelper = MemoryHelper(appDatabase)

            initializeSecurePreferences()
            loadInitialTheme()
            initializeViews()
            initializeAdapter()
            initializeTopBar()
            initializeListeners()
            initializeConversationHistory()

            isActivityReady = true
            initializeConversationSystem()
            checkAndLoadModelOnStartup()
            updateModelIndicators()
        } catch (e: Exception) {
            android.util.Log.e("Monolith", "Chat initialization failed", e)
            showToast("تعذر تهيئة المحادثة: ${e.message}")
        }
    }

    private fun initializeSecurePreferences() {
        val masterKey = MasterKey.Builder(this).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        securePrefs = EncryptedSharedPreferences.create(
            this, "SecureSettings", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun loadInitialTheme() {
        val settings = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        themeColorHex = settings.getString("AppThemeColor", "#00D4FF") ?: "#00D4FF"
        try {
            Color.parseColor(themeColorHex)
        } catch (_: Exception) {
            themeColorHex = "#00D4FF"
        }
    }

    private fun initializeViews() {
        etMessage = findViewById(R.id.etMessage)
        btnSend = findViewById(R.id.btnSend)
        tvAppTitle = findViewById(R.id.tvAppTitle)
                chatContentContainer = findViewById(R.id.chatContentContainer)
        recyclerViewChat = findViewById(R.id.recyclerViewChat)
        imagePreviewContainer = findViewById(R.id.imagePreviewContainer)
        previewListContainer = findViewById(R.id.previewListContainer)

        // عناصر سجل المحادثات (من activity_chat.xml)
        conversationHistoryPopup = findViewById(R.id.conversationHistoryPopup)
        conversationHistoryList = findViewById(R.id.conversationHistoryList)
        conversationHistoryScroll = findViewById(R.id.conversationHistoryScroll)
        tvEmptyConversationHistory = findViewById(R.id.tvEmptyConversationHistory)
        btnCreateConversation = findViewById(R.id.btnCreateConversation)
        tvCurrentConversation = findViewById(R.id.tvCurrentConversation)
        tvHistoryLabel = findViewById(R.id.tvHistoryLabel)

        suggestionsScroll = findViewById(R.id.suggestionsScroll)
        suggestionsContainer = findViewById(R.id.suggestionsContainer)
        etMessage.hint = DEFAULT_INPUT_HINT

        recyclerViewChat.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = false
        }
    }

    private fun initializeAdapter() {
        chatAdapter = ChatAdapter(
            context = this,
            onEditClick = { message -> showEditDialog(message) },
            onReactionClick = { message ->
                val newReaction = when (message.reactionType) {
                    "LIKE" -> "DISLIKE"
                    "DISLIKE" -> null
                    else -> "LIKE"
                }
                updateMessageLocally(message.id) { it.copy(reactionType = newReaction) }
                findMessage(message.id)?.let { saveMessageToDb(it, touchConversation = false) }
            },
            onSpeakClick = { text ->
                initTtsIfNeeded()
                ttsManager?.speak(text)
            },
            onPinClick = { message ->
                updateMessageLocally(message.id) { it.copy(isPinned = !it.isPinned) }
                findMessage(message.id)?.let { updated ->
                    saveMessageToDb(updated, touchConversation = false)
                    showToast(if (updated.isPinned) "تم تثبيت الرسالة" else "تم إلغاء تثبيت الرسالة")
                }
            },
            themeColorHex = themeColorHex
        )
        applyAdapterVisualConfig()
        recyclerViewChat.adapter = chatAdapter
        chatAdapter.updateMessages(messageList)
    }

    private fun applyAdapterVisualConfig() {
        if (!::chatAdapter.isInitialized) return
        chatAdapter.applyVisualConfig(
            selfColorHex = "#f8fafc",
            peerColorHex = themeColorHex,
            aiColorHex = "#ef4444",
            bgPresetId = "default",
            cardOpacity = 0.98f,
            dynamicEnabled = true,
            dynamicState = "on",
            dynamicPattern = "pulse",
            dynamicSpeedSec = 8f,
            dynamicIntensityPercent = 20
        )
    }

    private fun createTopBarButton(iconResource: Int, tint: Int): ImageButton {
        return ImageButton(this).apply {
            setImageResource(iconResource)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(tint)
            layoutParams = LinearLayout.LayoutParams(dpToPx(48), dpToPx(48))
        }
    }

    private fun initializeTopBar() {
        findViewById<ImageButton>(R.id.btnDashboard)?.setOnClickListener { showDashboardBottomSheet() }
        findViewById<ImageButton>(R.id.btnSettings)?.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<ImageButton>(R.id.btnBack)?.setOnClickListener { finish() }
    }

    private fun initializeListeners() {
        btnSend.isEnabled = false
        btnSend.alpha = 0.5f

        etMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateSendButtonState()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        findViewById<ImageButton>(R.id.btnAttach)?.setOnClickListener {
            hideKeyboard()
            showAttachBottomSheet()
        }

        btnSend.setOnClickListener { sendMessage() }

        etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else false
        }

        findViewById<TextView>(R.id.modelSelector)?.setOnClickListener {
            showModelOptions()
        }

        clearInitiatives()

        findViewById<ImageButton>(R.id.btnMic)?.setOnClickListener {
            showToast("خدمة التعرف على الصوت تحتاج إلى ربط (لم يتم اختراع API وهمي)")
        }
    }

    // =========================================================
    // CONVERSATION HISTORY
    // =========================================================

    private fun initializeConversationHistory() {
        tvHistoryLabel.setOnClickListener { toggleConversationHistory() }
        tvCurrentConversation.setOnClickListener { toggleConversationHistory() }
        btnCreateConversation.setOnClickListener { createNewConversation() }
        onBackPressedDispatcher.addCallback(this, historyBackCallback)

        conversationHistoryPopup.visibility = View.GONE
        tvCurrentConversation.text = MAIN_CONVERSATION_TITLE
    }

    private fun initializeConversationSystem() {
        isLoadingMessages = true
        updateSendButtonState()

        lifecycleScope.launch {
            try {
                val startId = withContext(Dispatchers.IO) {
                    databaseMutex.withLock { prepareInitialConversation() }
                }
                currentConversationId = startId
                saveLastConversationId(startId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("Monolith", "Conversation init failed", e)
                showToast("تعذر تهيئة سجل المحادثات: ${e.message}")
            }

            refreshCurrentConversationHeader()
            loadMessagesFromDb(currentConversationId)
        }
    }

    private fun prepareInitialConversation(): String {
        if (appDatabase.getConversationById(CONVERSATION_ID) == null) {
            appDatabase.insertConversationIfAbsent(
                ConversationEntity(
                    id = CONVERSATION_ID,
                    title = MAIN_CONVERSATION_TITLE
                )
            )
        }

        val savedId = loadLastConversationId()
        if (!savedId.isNullOrBlank() && appDatabase.getConversationById(savedId) != null) {
            return savedId
        }

        if (appDatabase.getConversationById(CONVERSATION_ID) != null) {
            return CONVERSATION_ID
        }

        return appDatabase.getAllConversations().firstOrNull()?.id ?: CONVERSATION_ID
    }

    private fun saveLastConversationId(conversationId: String) {
        try {
            getSharedPreferences(CONVERSATION_STATE_PREFS, MODE_PRIVATE)
                .edit()
                .putString(KEY_LAST_CONVERSATION_ID, conversationId)
                .apply()
        } catch (e: Exception) {
            android.util.Log.e("Monolith", "Saving last conversation id failed", e)
        }
    }

    private fun loadLastConversationId(): String? {
        return try {
            getSharedPreferences(CONVERSATION_STATE_PREFS, MODE_PRIVATE)
                .getString(KEY_LAST_CONVERSATION_ID, null)
        } catch (_: Exception) {
            null
        }
    }

    private fun toggleConversationHistory() {
        if (conversationHistoryPopup.visibility == View.VISIBLE) {
            hideConversationHistory()
        } else {
            showConversationHistory()
        }
    }

    private fun showConversationHistory() {
        hideKeyboard()
        conversationHistoryPopup.visibility = View.VISIBLE
        historyBackCallback.isEnabled = true
        tvHistoryLabel.text = "⌃"
        refreshConversationHistory()
    }

    private fun hideConversationHistory() {
        conversationHistoryPopup.visibility = View.GONE
        historyBackCallback.isEnabled = false
        tvHistoryLabel.text = "⌄"
    }

    private fun isConversationHistoryVisible(): Boolean {
        return conversationHistoryPopup.visibility == View.VISIBLE
    }

    private fun refreshConversationHistory() {
        lifecycleScope.launch {
            try {
                val conversations = withContext(Dispatchers.IO) {
                    appDatabase.getAllConversations()
                }
                renderConversationHistory(conversations)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showToast("تعذر تحميل المحادثات: ${e.message}")
            }
        }
    }

    private fun renderConversationHistory(conversations: List<ConversationEntity>) {
        conversationHistoryList.removeAllViews()

        if (conversations.isEmpty()) {
            conversationHistoryScroll.visibility = View.GONE
            tvEmptyConversationHistory.visibility = View.VISIBLE
            return
        }

        tvEmptyConversationHistory.visibility = View.GONE
        conversationHistoryScroll.visibility = View.VISIBLE

        val scrollParams = conversationHistoryScroll.layoutParams
        scrollParams.height = if (conversations.size > HISTORY_MAX_VISIBLE_ROWS) {
            dpToPx(300)
        } else {
            ViewGroup.LayoutParams.WRAP_CONTENT
        }
        conversationHistoryScroll.layoutParams = scrollParams

        conversations.forEach { conversation ->
            conversationHistoryList.addView(buildConversationRow(conversation))
        }
    }

    private fun buildConversationRow(conversation: ConversationEntity): View {
        val isCurrent = conversation.id == currentConversationId
        val themeColor = Color.parseColor(themeColorHex)
        val primaryText = ContextCompat.getColor(this, R.color.monolith_text)
        val secondaryText = ContextCompat.getColor(this, R.color.monolith_text_secondary)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(44)
            ).apply { setMargins(0, dpToPx(2), 0, dpToPx(2)) }
            setPadding(dpToPx(10), 0, dpToPx(4), 0)
            isClickable = true
            isFocusable = true

            background = if (isCurrent) {
                GradientDrawable().apply {
                    cornerRadius = dpToPx(10).toFloat()
                    setColor((themeColor and 0x00FFFFFF) or 0x33000000)
                    setStroke(dpToPx(1), themeColor)
                }
            } else {
                ColorDrawable(Color.TRANSPARENT)
            }

            setOnClickListener { switchConversation(conversation.id) }
        }

        val displayTitle = conversation.title.ifBlank { DEFAULT_CONVERSATION_TITLE }

        val titleView = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            text = if (isCurrent) "● $displayTitle" else displayTitle
            setTextColor(if (isCurrent) themeColor else primaryText)
            textSize = 13f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        val renameButton = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(36), dpToPx(36))
            gravity = Gravity.CENTER
            text = "✎"
            textSize = 16f
            setTextColor(secondaryText)
            contentDescription = "Rename conversation"
            setOnClickListener { showRenameConversationDialog(conversation) }
        }

        val deleteButton = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(36), dpToPx(36))
            gravity = Gravity.CENTER
            text = "✕"
            textSize = 15f
            setTextColor(secondaryText)
            contentDescription = "Delete conversation"
            setOnClickListener { confirmDeleteConversation(conversation) }
        }

        row.addView(titleView)
        row.addView(renameButton)
        if (conversation.id != CONVERSATION_ID) {
            row.addView(deleteButton)
        }
        return row
    }

    private fun switchConversation(conversationId: String) {
        hideConversationHistory()
        if (conversationId.isBlank() || conversationId == currentConversationId) return
        openConversation(conversationId)
    }

    private fun openConversation(conversationId: String) {
        currentConversationId = conversationId
        saveLastConversationId(conversationId)

        messageList.clear()
        chatAdapter.updateMessages(messageList)
        hideKeyboard()
        clearInitiatives()
        resetInputHint()

        refreshCurrentConversationHeader()
        loadMessagesFromDb(conversationId)
    }

    private fun refreshCurrentConversationHeader() {
        val conversationId = currentConversationId
        lifecycleScope.launch {
            try {
                val title = withContext(Dispatchers.IO) {
                    appDatabase.getConversationById(conversationId)?.title
                }
                if (conversationId == currentConversationId) {
                    tvCurrentConversation.text =
                        if (title.isNullOrBlank()) DEFAULT_CONVERSATION_TITLE else title
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("Monolith", "Header refresh failed", e)
            }
        }
    }

    private fun onConversationTitleChanged(conversationId: String) {
        if (conversationId == currentConversationId) {
            refreshCurrentConversationHeader()
        }
        if (isConversationHistoryVisible()) {
            refreshConversationHistory()
        }
    }

    private fun createNewConversation() {
        lifecycleScope.launch {
            try {
                val conversation = ConversationEntity(title = DEFAULT_CONVERSATION_TITLE)

                val created = withContext(Dispatchers.IO) {
                    databaseMutex.withLock {
                        appDatabase.insertConversationIfAbsent(conversation)
                    }
                }

                if (!created) {
                    showToast("تعذر إنشاء المحادثة: المعرّف مستخدم مسبقاً")
                    return@launch
                }

                hideConversationHistory()
                openConversation(conversation.id)
                showToast("تم إنشاء محادثة جديدة")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showToast("تعذر إنشاء المحادثة: ${e.message}")
            }
        }
    }

    private fun showRenameConversationDialog(conversation: ConversationEntity) {
        val input = EditText(this).apply {
            setText(conversation.title)
            setSelection(text.length)
            hint = "اسم المحادثة"
            setSingleLine(true)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(4))
            addView(
                input,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        AlertDialog.Builder(this)
            .setTitle("إعادة تسمية المحادثة")
            .setView(container)
            .setPositiveButton("حفظ") { _, _ ->
                val newTitle = input.text.toString().trim().take(MAX_CONVERSATION_TITLE_LENGTH)
                if (newTitle.isBlank()) {
                    showToast("اسم المحادثة فارغ")
                    return@setPositiveButton
                }
                renameConversation(conversation.id, newTitle)
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun renameConversation(conversationId: String, newTitle: String) {
        lifecycleScope.launch {
            try {
                val updated = withContext(Dispatchers.IO) {
                    databaseMutex.withLock {
                        appDatabase.updateConversationTitle(conversationId, newTitle)
                    }
                }

                if (!updated) {
                    showToast("تعذر العثور على المحادثة")
                    return@launch
                }

                if (conversationId == currentConversationId) {
                    tvCurrentConversation.text = newTitle
                }
                if (isConversationHistoryVisible()) {
                    refreshConversationHistory()
                }
                showToast("تمت إعادة تسمية المحادثة")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showToast("تعذرت إعادة التسمية: ${e.message}")
            }
        }
    }

    private fun confirmDeleteConversation(conversation: ConversationEntity) {
        if (conversation.id == CONVERSATION_ID) {
            showToast("لا يمكن حذف المحادثة الرئيسية")
            return
        }

        val displayTitle = conversation.title.ifBlank { DEFAULT_CONVERSATION_TITLE }

        AlertDialog.Builder(this)
            .setTitle("حذف المحادثة")
            .setMessage("سيتم حذف المحادثة «$displayTitle» وجميع رسائلها وبياناتها التابعة نهائياً. لن تتأثر المحادثات الأخرى.")
            .setPositiveButton("حذف") { _, _ -> deleteConversation(conversation.id) }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun deleteConversation(conversationId: String) {
        if (conversationId.isBlank()) return

        if (conversationId == CONVERSATION_ID) {
            showToast("لا يمكن حذف المحادثة الرئيسية")
            return
        }

        val wasCurrent = conversationId == currentConversationId

        cancelPendingRequestsFor(conversationId)
        deletedConversationIds.add(conversationId)

        lifecycleScope.launch {
            try {
                val removed = withContext(Dispatchers.IO) {
                    databaseMutex.withLock {
                        appDatabase.deleteConversation(conversationId)
                    }
                }

                if (wasCurrent && conversationId == currentConversationId) {
                    val remaining = withContext(Dispatchers.IO) {
                        appDatabase.getAllConversations()
                    }

                    if (remaining.isNotEmpty()) {
                        openConversation(remaining.first().id)
                    } else {
                        withContext(Dispatchers.IO) {
                            databaseMutex.withLock {
                                appDatabase.insertConversationIfAbsent(
                                    ConversationEntity(
                                        id = CONVERSATION_ID,
                                        title = MAIN_CONVERSATION_TITLE
                                    )
                                )
                            }
                        }
                        openConversation(CONVERSATION_ID)
                    }
                }

                if (isConversationHistoryVisible()) {
                    refreshConversationHistory()
                }

                if (removed) {
                    showToast("تم حذف المحادثة")
                } else {
                    showToast("المحادثة غير موجودة في السجل")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                deletedConversationIds.remove(conversationId)
                showToast("تعذر حذف المحادثة: ${e.message}")
            }
        }
    }

    private fun cancelPendingRequestsFor(conversationId: String) {
        synchronized(requestLock) {
            val requestIds = pendingRequests
                .filterValues { it.conversationId == conversationId }
                .keys
                .toList()

            requestIds.forEach { requestId ->
                activeApiCalls.remove(requestId)?.let { call ->
                    try { call.cancel() } catch (_: Exception) { }
                }
                activeLocalJobs.remove(requestId)?.cancel()
                pendingRequests.remove(requestId)
            }
        }
    }

    private fun buildAutoTitle(content: String): String {
        val firstLine = content.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
            ?: return DEFAULT_CONVERSATION_TITLE

        return if (firstLine.length > AUTO_TITLE_LENGTH) {
            firstLine.take(AUTO_TITLE_LENGTH).trimEnd() + "…"
        } else {
            firstLine
        }
    }

    // =========================================================
    // DIALOG INITIATIVES
    // =========================================================

    private fun clearInitiatives() {
        currentInitiatives = emptyList()
        if (::suggestionsContainer.isInitialized) suggestionsContainer.removeAllViews()
        if (::suggestionsScroll.isInitialized) suggestionsScroll.visibility = View.GONE
    }

    private fun resetInputHint() {
        if (::etMessage.isInitialized) etMessage.hint = DEFAULT_INPUT_HINT
    }

    private fun showInitiatives(items: List<DialogInitiative>) {
        currentInitiatives = items
        suggestionsContainer.removeAllViews()

        if (items.isEmpty()) {
            suggestionsScroll.visibility = View.GONE
            return
        }

        val themeColor = try {
            Color.parseColor(themeColorHex)
        } catch (_: Exception) {
            Color.parseColor("#00D4FF")
        }
        val permissionColor = Color.parseColor("#FFAA00")

        items.forEachIndexed { index, item ->
            val accent = if (item.kind == DialogInitiativeKind.PERMISSION) permissionColor else themeColor

            val chip = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dpToPx(32)
                ).apply {
                    setMargins(0, 0, if (index < items.size - 1) dpToPx(8) else 0, 0)
                }
                gravity = Gravity.CENTER
                setPadding(dpToPx(12), 0, dpToPx(12), 0)
                text = item.label
                textSize = 11f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(accent)
                background = GradientDrawable().apply {
                    cornerRadius = dpToPx(16).toFloat()
                    setColor(Color.TRANSPARENT)
                    setStroke(dpToPx(1), accent)
                }
                contentDescription = item.label
                isClickable = true
                isFocusable = true
                setOnClickListener { onInitiativeClicked(item) }
            }

            suggestionsContainer.addView(chip)
        }

        suggestionsScroll.visibility = View.VISIBLE
        suggestionsScroll.scrollTo(0, 0)
    }

    private fun onInitiativeClicked(item: DialogInitiative) {
        if (!isActivityReady || isLoadingMessages) return

        when (item.kind) {
            DialogInitiativeKind.MISSING_INFO -> promptForMissingInfo(item)
            DialogInitiativeKind.PERMISSION -> confirmPermissionInitiative(item)
            DialogInitiativeKind.FOLLOW_UP,
            DialogInitiativeKind.NEXT_STEP,
            DialogInitiativeKind.CONTINUE_TASK -> sendMessage(overrideText = item.text)
        }
    }

    private fun promptForMissingInfo(item: DialogInitiative) {
        etMessage.hint = item.text
        etMessage.requestFocus()
        try {
            val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.showSoftInput(etMessage, InputMethodManager.SHOW_IMPLICIT)
        } catch (_: Exception) { }
    }

    private fun confirmPermissionInitiative(item: DialogInitiative) {
        AlertDialog.Builder(this)
            .setTitle("طلب موافقة")
            .setMessage(item.text + "\n\nعند الموافقة تُرسل موافقتك كرسالة صريحة في المحادثة.")
            .setPositiveButton("موافق") { _, _ ->
                sendMessage(overrideText = "أوافق على: ${item.text}")
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun showModelOptions() {
        val options = arrayOf("تحميل نموذج نصي", "تحميل نموذج الرؤية")
        AlertDialog.Builder(this)
            .setTitle("إدارة المحرك")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> pickModelLauncher.launch(arrayOf("*/*"))
                    1 -> pickVisionGgufLauncher.launch(arrayOf("*/*"))
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun updateSendButtonState() {
        if (!::btnSend.isInitialized) return
        val hasContent = etMessage.text.toString().isNotBlank() || selectedImageUris.isNotEmpty()
        val enabled = hasContent && isActivityReady && !isLoadingMessages
        btnSend.isEnabled = enabled
        btnSend.alpha = if (enabled) 1f else 0.5f
    }

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
        val result = modelManager.loadModelAsync(modelFile.absolutePath)

        result.fold(
            onSuccess = {
                isModelLoaded = true
                activeAgentId = 0
                updateEngineStatus()
                showToast("تم تحميل النموذج المحلي بنجاح")
            },
            onFailure = { error ->
                isModelLoaded = false
                updateEngineStatus()
                showToast("فشل تحميل النموذج: ${error.message}")
            }
        )
    }

    private fun checkAndLoadModelOnStartup() {
        if (modelManager.isTextModelLoaded) {
            isModelLoaded = true
            updateEngineStatus()
            return
        }
        val modelFile = File(filesDir, MODEL_FILE_NAME)
        if (!modelFile.exists() || modelFile.length() <= 0L) {
            isModelLoaded = false
            updateEngineStatus()
            return
        }
        lifecycleScope.launch {
            try {
                loadLocalModel(modelFile)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                isModelLoaded = false
                updateEngineStatus()
                showToast("تعذر التحميل التلقائي: ${e.message}")
            }
        }
    }

    private fun updateModelIndicators() {
        isModelLoaded = modelManager.isTextModelLoaded
        updateEngineStatus()
    }

    private fun updateEngineStatus() {
        if (!::modelManager.isInitialized) return
        val statusView = findViewById<TextView>(R.id.tvEngineStatus) ?: return

        when {
            modelManager.isTextModelLoaded -> {
                statusView.text = "TEXT READY"
                statusView.setTextColor(Color.GREEN)
            }
            modelManager.isVisionModelLoaded -> {
                statusView.text = "VISION READY"
                statusView.setTextColor(Color.CYAN)
            }
            modelManager.isEngineReady -> {
                statusView.text = "ENGINE READY"
                statusView.setTextColor(Color.parseColor("#FFAA00"))
            }
            else -> {
                statusView.text = "ENGINE OFFLINE"
                statusView.setTextColor(Color.GRAY)
            }
        }
    }

    private fun loadMessagesFromDb(conversationId: String = currentConversationId) {
        val generation = ++loadGeneration
        isLoadingMessages = true
        updateSendButtonState()

        lifecycleScope.launch {
            try {
                val loadedMessages = withContext(Dispatchers.IO) {
                    databaseMutex.withLock {
                        appDatabase.getMessagesForConversation(conversationId).map { it.toChatMessage() }
                    }
                }

                if (generation != loadGeneration || conversationId != currentConversationId) {
                    return@launch
                }

                messageList.clear()
                messageList.addAll(loadedMessages)

                val placeholders = synchronized(requestLock) {
                    pendingRequests.values
                        .filter { it.conversationId == conversationId }
                        .map { it.placeholder }
                }
                var addedPlaceholder = false
                placeholders.forEach { placeholder ->
                    if (messageList.none { it.id == placeholder.id }) {
                        messageList.add(placeholder)
                        addedPlaceholder = true
                    }
                }
                if (addedPlaceholder) messageList.sortBy { it.timestamp }

                chatAdapter.updateMessages(messageList)
                if (messageList.isNotEmpty()) scrollToBottom()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation == loadGeneration) {
                    showToast("تعذر تحميل سجل المحادثة: ${e.message}")
                }
            } finally {
                if (generation == loadGeneration) {
                    isLoadingMessages = false
                    updateSendButtonState()
                }
            }
        }
    }

    private fun saveMessageToDb(
        message: ChatMessage,
        conversationId: String = currentConversationId,
        touchConversation: Boolean = true
    ) {
        if (deletedConversationIds.contains(conversationId)) return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                var titleChanged = false

                databaseMutex.withLock {
                    if (deletedConversationIds.contains(conversationId)) return@withLock

                    appDatabase.insertMessage(message.toMessageEntity(conversationId))

                    if (touchConversation) {
                        appDatabase.updateConversationLastModified(conversationId)

                        if (message.role == MessageRole.USER) {
                            val conversation = appDatabase.getConversationById(conversationId)
                            if (conversation != null && conversation.title == DEFAULT_CONVERSATION_TITLE) {
                                appDatabase.updateConversationTitle(
                                    conversationId,
                                    buildAutoTitle(message.content)
                                )
                                titleChanged = true
                            }
                        }
                    }
                }

                if (titleChanged) {
                    withContext(Dispatchers.Main) { onConversationTitleChanged(conversationId) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("Monolith", "Message save failed", e)
            }
        }
    }

    private fun saveUserPreference(type: String, content: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                memoryHelper.savePreference(content)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("Monolith", "Preference save failed", e)
            }
        }
    }

    private fun updateMessageLocally(messageId: String, transform: (ChatMessage) -> ChatMessage) {
        val index = messageList.indexOfFirst { it.id == messageId }
        if (index == -1) return
        messageList[index] = transform(messageList[index])
        chatAdapter.updateMessages(messageList)
    }

    private fun findMessage(messageId: String): ChatMessage? {
        return messageList.firstOrNull { it.id == messageId }
    }

    private fun MessageEntity.toChatMessage(): ChatMessage {
        return ChatMessage(
            id = id,
            role = if (role.equals("USER", true)) MessageRole.USER else MessageRole.AI,
            content = content,
            attachmentUris = decodeAttachmentUris(attachmentUris),
            colorHex = if (role.equals("USER", true)) themeColorHex else "#2A2A2A",
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

    private fun ChatMessage.toMessageEntity(conversationId: String): MessageEntity {
        return MessageEntity(
            id = id,
            conversationId = conversationId,
            senderId = if (role == MessageRole.USER) "me" else "ai",
            role = role.name,
            content = content,
            timestamp = timestamp,
            attachmentUris = encodeAttachmentUris(attachmentUris),
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

    private fun encodeAttachmentUris(uris: List<String>?): String? {
        if (uris.isNullOrEmpty()) return null
        val array = JSONArray()
        uris.forEach { array.put(it) }
        return array.toString()
    }

    private fun decodeAttachmentUris(stored: String?): List<String>? {
        if (stored.isNullOrBlank()) return null
        return try {
            val array = JSONArray(stored)
            List(array.length()) { index -> array.getString(index) }
        } catch (_: Exception) {
            stored.split(",").filter { it.isNotBlank() }
        }
    }

    private fun sendMessage(overrideText: String? = null) {
        if (!isActivityReady || isLoadingMessages) return

        val fromInitiative = overrideText != null
        val text = overrideText ?: etMessage.text.toString()
        val attachments: List<String> =
            if (fromInitiative) emptyList() else selectedImageUris.map { it.toString() }
        if (text.isBlank() && attachments.isEmpty()) return

        isModelLoaded = modelManager.isTextModelLoaded
        val apiKey = securePrefs.getString("api_key", null)
        val selectedModel = securePrefs.getString("selected_model", null)

        if (!isModelLoaded && (apiKey.isNullOrBlank() || selectedModel.isNullOrBlank())) {
            showToast("حمّل نموذجاً محلياً أو أضف إعدادات الاتصال السحابي أولاً")
            return
        }

        clearInitiatives()
        resetInputHint()

        val conversationId = currentConversationId

        val userMessage = ChatMessage(
            role = MessageRole.USER,
            content = text.ifBlank { "أرسل المستخدم صوراً مرفقة." },
            attachmentUris = attachments,
            colorHex = themeColorHex
        )

        messageList.add(userMessage)
        chatAdapter.updateMessages(messageList)
        saveMessageToDb(userMessage, conversationId)

        if (!fromInitiative) {
            selectedImageUris.clear()
            updatePreviewUi()
            etMessage.text.clear()
        }
        hideKeyboard()
        scrollToBottom()

        if (isModelLoaded) {
            sendToLocalModel(userMessage, conversationId)
        } else {
            sendToCloudModel(userMessage, conversationId, selectedModel!!, apiKey!!)
        }
    }

    private fun sendToLocalModel(userMessage: ChatMessage, conversationId: String) {
        val statusMessage = ChatMessage(
            role = MessageRole.AI,
            content = STATUS_LOCAL,
            colorHex = "#1A1A1A",
            isThinking = true
        )

        synchronized(requestLock) {
            pendingRequests[statusMessage.id] = PendingRequest(conversationId, statusMessage)
        }

        messageList.add(statusMessage)
        chatAdapter.updateMessages(messageList)
        scrollToBottom()

        val job = lifecycleScope.launch {
            try {
                val memoriesContext = getMemoriesContext()
                val enrichedPrompt = buildString {
                    append(userMessage.content)

                    if (memoriesContext.isNotBlank()) {
                        append("\n\n")
                        append(memoriesContext)
                    }

                    val attachments = userMessage.attachmentUris.orEmpty()
                    if (attachments.isNotEmpty()) {
                        append("\n\n")
                        if (modelManager.isVisionModelLoaded) {
                            append(analyzeLocalImageAttachments(attachments))
                        } else {
                            append("ملاحظة للنموذج: توجد صور مرفقة، لكن نموذج الرؤية المحلي غير محمّل. لا تفترض أنك شاهدت محتوى الصور.")
                        }
                    }

                    if (INITIATIVES_IN_LOCAL_PROMPT) {
                        append("\n\n")
                        append(DialogInitiativeProtocol.INSTRUCTION)
                    }
                }

                val result = agentBrain.executeTask(userGoal = enrichedPrompt, agentId = activeAgentId)

                updateRequestMessage(
                    conversationId = conversationId,
                    requestMessage = statusMessage,
                    content = result,
                    isThinking = false,
                    parseInitiatives = true
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateRequestMessage(
                    conversationId = conversationId,
                    requestMessage = statusMessage,
                    content = "تعذر تنفيذ الطلب: ${e.message}",
                    isThinking = false
                )
            } finally {
                synchronized(requestLock) { activeLocalJobs.remove(statusMessage.id) }
            }
        }

        synchronized(requestLock) { activeLocalJobs[statusMessage.id] = job }
    }

    private suspend fun analyzeLocalImageAttachments(attachments: List<String>): String {
        val results = StringBuilder()
        attachments.forEachIndexed { index, uriString ->
            try {
                val uri = Uri.parse(uriString)
                val frame = decodeImageToDirectBuffer(uri)
                val analysis = modelManager.analyzeScreenFrame(
                    buffer = frame.first,
                    width = frame.second,
                    height = frame.third,
                    prompt = "حلل الصورة المرفقة رقم ${index + 1}. صف محتواها والعناصر المهمة والعلاقات بينها."
                )
                val error = try {
                    JSONObject(analysis).optString("error")
                } catch (_: Exception) { "" }

                if (error.isNotBlank()) throw IllegalStateException(error)

                results.append("\nنتيجة تحليل الصورة ${index + 1}:\n")
                results.append(analysis)
                results.append("\n")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                results.append("\nتعذر تحليل الصورة ${index + 1}: ")
                results.append(e.message ?: "خطأ غير معروف")
                results.append("\n")
            }
        }
        return results.toString()
    }

    private suspend fun decodeImageToDirectBuffer(uri: Uri): Triple<ByteBuffer, Int, Int> = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } ?: throw IllegalStateException("تعذر قراءة أبعاد الصورة")

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IllegalArgumentException("الصورة غير صالحة")

        var sampleSize = 1
        while (bounds.outWidth / sampleSize > MAX_IMAGE_DIMENSION || bounds.outHeight / sampleSize > MAX_IMAGE_DIMENSION) {
            sampleSize *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        var bitmap = contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: throw IllegalStateException("تعذر فك ترميز الصورة")

        if (bitmap.width > MAX_IMAGE_DIMENSION || bitmap.height > MAX_IMAGE_DIMENSION) {
            val ratio = minOf(MAX_IMAGE_DIMENSION.toFloat() / bitmap.width, MAX_IMAGE_DIMENSION.toFloat() / bitmap.height)
            val scaled = Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * ratio).toInt().coerceAtLeast(1),
                (bitmap.height * ratio).toInt().coerceAtLeast(1),
                true
            )
            if (scaled !== bitmap) bitmap.recycle()
            bitmap = scaled
        }

        if (bitmap.config != Bitmap.Config.ARGB_8888) {
            val converted = bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: run {
                bitmap.recycle()
                throw IllegalStateException("تعذر تحويل الصورة إلى ARGB_8888")
            }
            bitmap.recycle()
            bitmap = converted
        }

        try {
            val width = bitmap.width
            val height = bitmap.height
            val buffer = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
            bitmap.copyPixelsToBuffer(buffer)
            buffer.flip()
            Triple(buffer, width, height)
        } finally {
            bitmap.recycle()
        }
    }

    private fun sendToCloudModel(
        userMessage: ChatMessage,
        conversationId: String,
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

        synchronized(requestLock) {
            pendingRequests[statusMessage.id] = PendingRequest(conversationId, statusMessage)
        }

        messageList.add(statusMessage)
        chatAdapter.updateMessages(messageList)
        scrollToBottom()

        val historySnapshot = messageList
            .filter { !it.isThinking && it.id != userMessage.id }
            .takeLast(15)
            .toList()

        lifecycleScope.launch {
            try {
                val temperature = securePrefs.getFloat("temperature", 0.7f).coerceIn(0f, 2f)
                val persona = securePrefs.getString("persona", "default") ?: "default"

                val personaPrompt = when (persona) {
                    "analytical" -> "أنت Monolith في وضع المحلل النقدي. حلل المعطيات بدقة وميّز بين الحقائق والافتراضات."
                    "objective" -> "أنت Monolith في الوضع الموضوعي. قدم إجابات متوازنة ودقيقة دون تحيز."
                    "innovator" -> "أنت Monolith في وضع الابتكار. اقترح حلولاً عملية وأفكاراً جديدة مع توضيح حدودها."
                    else -> "أنت Monolith، مساعد ذكي. أجب بوضوح ودقة ولا تدّعِ تنفيذ شيء لم تنفذه."
                }

                val memoriesContext = getMemoriesContext()
                val completeSystemPrompt = buildString {
                    append(personaPrompt)
                    if (memoriesContext.isNotBlank()) {
                        append("\n\n")
                        append(memoriesContext)
                    }
                    if (INITIATIVES_IN_CLOUD_PROMPT) {
                        append("\n\n")
                        append(DialogInitiativeProtocol.INSTRUCTION)
                    }
                }

                val apiMessages = mutableListOf<Message>()
                apiMessages.add(Message(role = "system", content = completeSystemPrompt))

                historySnapshot.forEach { message ->
                    apiMessages.add(Message(
                        role = if (message.role == MessageRole.USER) "user" else "assistant",
                        content = message.content
                    ))
                }

                val currentContentParts = mutableListOf<ContentPart>()
                if (userMessage.content.isNotBlank()) {
                    currentContentParts.add(ContentPart(type = "text", text = userMessage.content))
                }

                val imageErrors = mutableListOf<String>()
                userMessage.attachmentUris.orEmpty().forEachIndexed { index, uriString ->
                    try {
                        val dataUrl = encodeImageAsDataUrl(Uri.parse(uriString))
                        currentContentParts.add(ContentPart(type = "image_url", imageUrl = ImageUrl(dataUrl)))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        imageErrors.add("تعذر تجهيز الصورة ${index + 1}: " + (e.message ?: "خطأ غير معروف"))
                    }
                }

                if (imageErrors.isNotEmpty()) {
                    currentContentParts.add(ContentPart(type = "text", text = imageErrors.joinToString("\n")))
                }

                val currentContent: Any = if (currentContentParts.size == 1 && currentContentParts[0].type == "text") {
                    currentContentParts[0].text ?: ""
                } else {
                    currentContentParts
                }

                apiMessages.add(Message(role = "user", content = currentContent))

                val request = ChatRequest(
                    model = selectedModel,
                    messages = apiMessages,
                    temperature = temperature,
                    maxTokens = 2048
                )

                val stillPending = synchronized(requestLock) { pendingRequests.containsKey(statusMessage.id) }
                if (!stillPending) return@launch

                val headers = mapOf("Authorization" to "Bearer $apiKey")
                val call = ApiClient.service.sendMessage(url = "v1/chat/completions", headers = headers, request = request)

                synchronized(requestLock) { activeApiCalls[statusMessage.id] = call }

                call.enqueue(object : Callback<ChatResponse> {
                    override fun onResponse(call: Call<ChatResponse>, response: Response<ChatResponse>) {
                        synchronized(requestLock) { activeApiCalls.remove(statusMessage.id) }
                        if (isFinishing || isDestroyed || call.isCanceled) return

                        if (response.isSuccessful) {
                            val responseMessage = response.body()?.choices?.firstOrNull()?.message
                            val reply = responseMessage?.content?.takeIf { it.isNotBlank() }
                                ?: if (!responseMessage?.toolCalls.isNullOrEmpty()) {
                                    "وصل طلب لاستخدام أداة، لكن تنفيذ أدوات API غير موصول في مسار المحادثة الحالي."
                                } else {
                                    "لم يتم استلام رد صالح من الخادم."
                                }

                            updateRequestMessage(
                                conversationId = conversationId,
                                requestMessage = statusMessage,
                                content = reply,
                                isThinking = false,
                                modelName = selectedModel,
                                parseInitiatives = true
                            )
                        } else {
                            val errorText = try {
                                response.errorBody()?.string()?.take(1000)
                            } catch (_: Exception) { null }

                            updateRequestMessage(
                                conversationId = conversationId,
                                requestMessage = statusMessage,
                                content = buildString {
                                    append("فشل الطلب. رمز الاستجابة: ")
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

                    override fun onFailure(call: Call<ChatResponse>, t: Throwable) {
                        synchronized(requestLock) { activeApiCalls.remove(statusMessage.id) }
                        if (isFinishing || isDestroyed || call.isCanceled) return

                        updateRequestMessage(
                            conversationId = conversationId,
                            requestMessage = statusMessage,
                            content = "تعذر الاتصال بالخادم: " + (t.message ?: "خطأ غير معروف"),
                            isThinking = false
                        )
                    }
                })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateRequestMessage(
                    conversationId = conversationId,
                    requestMessage = statusMessage,
                    content = "تعذر إعداد الطلب السحابي: ${e.message}",
                    isThinking = false
                )
            }
        }
    }

    private suspend fun encodeImageAsDataUrl(uri: Uri): String = withContext(Dispatchers.IO) {
        val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
        require(mimeType.startsWith("image/")) { "الملف المحدد ليس صورة" }

        val output = ByteArrayOutputStream()
        val input = contentResolver.openInputStream(uri) ?: throw IllegalStateException("تعذر فتح الصورة")

        input.use { stream ->
            val buffer = ByteArray(8192)
            var totalBytes = 0

            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                totalBytes += count
                require(totalBytes <= MAX_IMAGE_BYTES) { "حجم الصورة أكبر من الحد المسموح للإرسال السحابي" }
                output.write(buffer, 0, count)
            }
        }

        val encoded = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        "data:$mimeType;base64,$encoded"
    }

    private fun updateRequestMessage(
        conversationId: String,
        requestMessage: ChatMessage,
        content: String,
        isThinking: Boolean,
        modelName: String? = null,
        parseInitiatives: Boolean = false
    ) {
        if (isFinishing || isDestroyed) return

        if (!isThinking) {
            synchronized(requestLock) { pendingRequests.remove(requestMessage.id) }
        }

        if (deletedConversationIds.contains(conversationId)) return

        var finalContent = content
        var initiatives: List<DialogInitiative> = emptyList()
        if (!isThinking && parseInitiatives) {
            val parsed = DialogInitiativeProtocol.parse(content)
            initiatives = parsed.initiatives
            finalContent = if (parsed.content.isBlank() && parsed.initiatives.isNotEmpty()) {
                EMPTY_REPLY_WITH_INITIATIVES_NOTICE
            } else {
                parsed.content
            }
        }

        val isCurrentConversation = conversationId == currentConversationId
        val index = if (isCurrentConversation) {
            messageList.indexOfFirst { it.id == requestMessage.id }
        } else {
            -1
        }

        val baseMessage = if (index >= 0) messageList[index] else requestMessage
        val updated = baseMessage.copy(
            content = finalContent,
            isThinking = isThinking,
            modelName = modelName ?: baseMessage.modelName
        )

        if (isCurrentConversation) {
            if (index >= 0) {
                messageList[index] = updated
                chatAdapter.updateMessages(messageList)
            } else if (!isThinking && !isLoadingMessages) {
                messageList.add(updated)
                messageList.sortBy { it.timestamp }
                chatAdapter.updateMessages(messageList)
            }
        }

        if (!isThinking) {
            saveMessageToDb(updated, conversationId)
            if (isCurrentConversation) {
                scrollToBottom()
                if (initiatives.isNotEmpty() && messageList.lastOrNull()?.id == updated.id) {
                    showInitiatives(initiatives)
                } else {
                    clearInitiatives()
                }
            }
        }
    }

    private fun scrollToBottom() {
        recyclerViewChat.post {
            if (messageList.isNotEmpty() && !isFinishing && !isDestroyed) {
                recyclerViewChat.scrollToPosition(messageList.size - 1)
            }
        }
    }

    private fun hideKeyboard() {
        try {
            val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.hideSoftInputFromWindow(etMessage.windowToken, 0)
        } catch (_: Exception) { }
    }

    private fun updatePreviewUi() {
        previewListContainer.removeAllViews()
        if (selectedImageUris.isEmpty()) {
            imagePreviewContainer.visibility = View.GONE
            updateSendButtonState()
            return
        }

        imagePreviewContainer.visibility = View.VISIBLE
        selectedImageUris.toList().forEach { uri ->
            val cardView = MaterialCardView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(58), dpToPx(58)).apply {
                    setMargins(0, 0, dpToPx(8), 0)
                }
                radius = dpToPx(8).toFloat()
                cardElevation = 0f
            }

            val container = RelativeLayout(this)
            val imageView = ImageView(this).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                scaleType = ImageView.ScaleType.CENTER_CROP
            }

            Glide.with(this).load(uri).centerCrop().into(imageView)
            container.addView(imageView)

            val removeButton = AppCompatTextView(this).apply {
                val params = RelativeLayout.LayoutParams(dpToPx(20), dpToPx(20))
                params.addRule(RelativeLayout.ALIGN_PARENT_TOP)
                params.addRule(RelativeLayout.ALIGN_PARENT_END)
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

    private fun shareFullConversation() {
        if (messageList.isEmpty()) {
            showToast("لا توجد رسائل لمشاركتها")
            return
        }
        val builder = StringBuilder()
        builder.append("سجل محادثة Monolith\n\n")
        messageList.forEach { message ->
            val sender = if (message.role == MessageRole.USER) "أنت" else "Monolith"
            builder.append(sender)
            builder.append(": ")
            builder.append(message.content)
            builder.append("\n")
            if (!message.attachmentUris.isNullOrEmpty()) {
                builder.append("[عدد المرفقات: ${message.attachmentUris.size}]\n")
            }
            builder.append("\n")
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, builder.toString())
        }
        startActivity(Intent.createChooser(intent, "مشاركة سجل المحادثة"))
    }

    private fun showAgentsBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val listView = ListView(this)
        val items = arrayOf(
            "الوكيل الرئيسي",
            "إنشاء وكيل فرعي",
            "خادم التعاون P2P"
        )
        listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, items)
        listView.setOnItemClickListener { _, _, position, _ ->
            when (position) {
                0 -> {
                    activeAgentId = 0
                    showToast("تم تفعيل الوكيل الرئيسي")
                    dialog.dismiss()
                }
                1 -> {
                    if (!modelManager.isTextModelLoaded) {
                        showToast("حمّل النموذج المحلي أولاً")
                        return@setOnItemClickListener
                    }
                    try {
                        val newId = modelManager.createSubAgent()
                        if (newId > 0) {
                            activeAgentId = newId
                            showToast("تم إنشاء الوكيل $newId")
                        } else {
                            showToast("تعذر إنشاء الوكيل الفرعي")
                        }
                    } catch (e: Exception) {
                        showToast("خطأ في إنشاء الوكيل: ${e.message}")
                    }
                    dialog.dismiss()
                }
                2 -> {
                    try {
                        NetworkManager.startP2PServer()
                        showToast("تم استدعاء خدمة P2P. تنفيذ الاتصال الفعلي بالأجهزة ما زال منفصلاً.")
                    } catch (e: Exception) {
                        showToast("تعذر تشغيل P2P: ${e.message}")
                    }
                    dialog.dismiss()
                }
            }
        }
        dialog.setContentView(listView)
        dialog.show()
    }

    private fun showControlsBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_controls, null)
        val temperatureBar = view.findViewById<SeekBar>(R.id.sbTemperature)
        val temperatureValue = view.findViewById<TextView>(R.id.tvTemperatureValue)
        val personaSpinner = view.findViewById<Spinner>(R.id.spinnerPersona)
        val thinkingCheckbox = view.findViewById<CheckBox>(R.id.cbThinking)
        val saveButton = view.findViewById<Button>(R.id.btnSaveControls)

        val savedTemperature = securePrefs.getFloat("temperature", 0.7f).coerceIn(0f, 2f)
        temperatureBar.max = 200
        temperatureBar.progress = (savedTemperature * 100).toInt()
        temperatureValue.text = String.format("%.1f", savedTemperature)

        val personas = arrayOf("افتراضي", "محلل نقدي", "موضوعي", "مبتكر")
        val personaKeys = arrayOf("default", "analytical", "objective", "innovator")
        personaSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, personas)

        val savedPersona = securePrefs.getString("persona", "default")
        val selectedIndex = personaKeys.indexOf(savedPersona)
        if (selectedIndex >= 0) {
            personaSpinner.setSelection(selectedIndex)
        }

        thinkingCheckbox.isChecked = securePrefs.getBoolean("enable_thinking", false)

        temperatureBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                temperatureValue.text = String.format("%.1f", progress / 100f)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        saveButton.setOnClickListener {
            val temperature = temperatureBar.progress / 100f
            val persona = personaKeys[personaSpinner.selectedItemPosition.coerceIn(personaKeys.indices)]

            securePrefs.edit()
                .putFloat("temperature", temperature)
                .putString("persona", persona)
                .putBoolean("enable_thinking", thinkingCheckbox.isChecked)
                .apply()

            showToast("تم حفظ الإعدادات")
            dialog.dismiss()
        }

        dialog.setContentView(view)
        dialog.show()
    }

    private fun showAttachBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_attach, null)
        dialog.setContentView(view)

        view.findViewById<LinearLayout>(R.id.btnOptionImage)?.setOnClickListener {
            dialog.dismiss()
            pickImagesLauncher.launch(arrayOf("image/*"))
        }

        view.findViewById<LinearLayout>(R.id.btnOptionDocument)?.setOnClickListener {
            dialog.dismiss()
            pickDocumentLauncher.launch(arrayOf("text/plain", "text/*", "application/json"))
        }

        view.findViewById<LinearLayout>(R.id.btnOptionAudio)?.setOnClickListener {
            dialog.dismiss()
            showToast("تحليل الملفات الصوتية لم يُربط بعد")
        }

        view.findViewById<LinearLayout>(R.id.btnOptionSearch)?.setOnClickListener {
            dialog.dismiss()
            showToast("البحث الخارجي يحتاج إلى ربط خدمة البحث")
        }

        view.findViewById<LinearLayout>(R.id.btnOptionCamera)?.setOnClickListener {
            dialog.dismiss()
            showToast("التقاط الكاميرا يحتاج إلى ربط مسار الكاميرا")
        }

        view.findViewById<LinearLayout>(R.id.btnOptionAI)?.setOnClickListener {
            dialog.dismiss()
            showToast("اختر النموذج من إعدادات المحرك")
        }

        dialog.show()
    }

    private fun showPinnedMessagesBottomSheet() {
        val conversationId = currentConversationId
        lifecycleScope.launch {
            try {
                val pinnedMessages = withContext(Dispatchers.IO) {
                    appDatabase.getPinnedMessages(conversationId)
                }
                if (pinnedMessages.isEmpty()) {
                    showToast("لا توجد رسائل مثبتة")
                    return@launch
                }
                val dialog = BottomSheetDialog(this@ChatActivity)
                val listView = ListView(this@ChatActivity)
                listView.adapter = ArrayAdapter(this@ChatActivity, android.R.layout.simple_list_item_1, pinnedMessages.map { it.content })
                listView.setOnItemClickListener { _, _, position, _ ->
                    val id = pinnedMessages[position].id
                    val index = messageList.indexOfFirst { it.id == id }
                    if (index >= 0) {
                        recyclerViewChat.smoothScrollToPosition(index)
                    }
                    dialog.dismiss()
                }
                dialog.setContentView(listView)
                dialog.show()
            } catch (e: Exception) {
                showToast("تعذر تحميل الرسائل المثبتة: ${e.message}")
            }
        }
    }

    private fun showDashboardBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_status, null)

        val apiKey = securePrefs.getString("api_key", null)
        val selectedModel = securePrefs.getString("selected_model", null) ?: "غير محدد"
        val configured = !apiKey.isNullOrBlank()

        val connectionStatus = view.findViewById<TextView>(R.id.tvConnectionStatus)
        val connectionIcon = view.findViewById<ImageView>(R.id.ivConnectionIcon)
        val providerName = view.findViewById<TextView>(R.id.tvProviderName)
        val modelName = view.findViewById<TextView>(R.id.tvModelName)
        val messageCount = view.findViewById<TextView>(R.id.tvMessageCount)
        val settingsButton = view.findViewById<Button>(R.id.btnGoToSettings)

        connectionStatus.text = if (configured) "اتصال خارجي مهيأ" else "لا يوجد اتصال خارجي مهيأ"
        val statusColor = if (configured) Color.GREEN else Color.GRAY
        connectionStatus.setTextColor(statusColor)
        connectionIcon.setColorFilter(statusColor)

        providerName.text = if (configured) "اتصال مخصص" else "محلي / غير محدد"
        modelName.text = selectedModel
        messageCount.text = messageList.size.toString()

        settingsButton.setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        dialog.setContentView(view)
        dialog.show()
    }

    private fun showEditDialog(message: ChatMessage) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
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
                val newContent = newContentInput.text.toString().trim()
                val reason = reasonInput.text.toString().trim()

                if (newContent.isBlank()) {
                    showToast("لا يمكن حفظ رسالة فارغة")
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
                                reason = reason.ifBlank { null },
                                timestamp = editTime
                            )
                        }

                        val index = messageList.indexOfFirst { it.id == message.id }
                        if (index >= 0) {
                            val updated = messageList[index].copy(
                                content = newContent,
                                isEdited = true,
                                originalContent = message.originalContent ?: message.content,
                                editReason = reason.ifBlank { null },
                                editedTimestamp = editTime
                            )
                            messageList[index] = updated
                            chatAdapter.updateMessages(messageList)
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

    override fun onResume() {
        super.onResume()
        if (!isActivityReady) return
        applyCurrentTheme()
        updateModelIndicators()
    }

    override fun onPause() {
        super.onPause()
    }

    private fun applyCurrentTheme() {
        val settings = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val rootLayout = findViewById<LinearLayout>(R.id.rootLayout)
        val previousTheme = themeColorHex

        themeColorHex = settings.getString("AppThemeColor", "#00D4FF") ?: "#00D4FF"
        val themeColor = try {
            Color.parseColor(themeColorHex)
        } catch (_: Exception) {
            themeColorHex = "#00D4FF"
            Color.parseColor(themeColorHex)
        }

        tvAppTitle.setTextColor(themeColor)
        btnSend.setColorFilter(themeColor, PorterDuff.Mode.SRC_IN)
        findViewById<ImageButton>(R.id.btnAttach)?.setColorFilter(themeColor, PorterDuff.Mode.SRC_IN)
        findViewById<ImageButton>(R.id.btnDashboard)?.setColorFilter(themeColor, PorterDuff.Mode.SRC_IN)
        findViewById<ImageButton>(R.id.btnSettings)?.setColorFilter(themeColor, PorterDuff.Mode.SRC_IN)
        findViewById<ImageButton>(R.id.btnBack)?.setColorFilter(themeColor, PorterDuff.Mode.SRC_IN)
        findViewById<ImageButton>(R.id.btnMic)?.setColorFilter(themeColor, PorterDuff.Mode.SRC_IN)
        findViewById<TextView>(R.id.modelSelector)?.setTextColor(themeColor)

        if (::tvCurrentConversation.isInitialized) tvCurrentConversation.setTextColor(themeColor)
        if (::tvHistoryLabel.isInitialized) tvHistoryLabel.setTextColor(themeColor)

        if (previousTheme != themeColorHex && ::chatAdapter.isInitialized) {
            initializeAdapter()
            if (isConversationHistoryVisible()) refreshConversationHistory()
        } else if (::chatAdapter.isInitialized) {
            applyAdapterVisualConfig()
        }

        val bgImagePath = settings.getString("AppBackgroundImage", null)
        if (!bgImagePath.isNullOrBlank()) {
            val file = File(bgImagePath)
            if (file.exists() && file.isFile && file.length() > 0L) {
                lifecycleScope.launch {
                    val bitmap = withContext(Dispatchers.IO) {
                        try {
                            BitmapFactory.decodeFile(file.absolutePath)
                        } catch (_: Exception) {
                            null
                        }
                    }
                    if (isFinishing || isDestroyed) {
                        bitmap?.recycle()
                        return@launch
                    }
                    if (bitmap != null && rootLayout != null) {
                        rootLayout.background = BitmapDrawable(resources, bitmap)
                    } else if (rootLayout != null) {
                        applyColorBackground(rootLayout, themeColor)
                    }
                }
            } else if (rootLayout != null) {
                applyColorBackground(rootLayout, themeColor)
            }
        } else if (rootLayout != null) {
            applyColorBackground(rootLayout, themeColor)
        }
    }

    private fun applyColorBackground(rootLayout: LinearLayout, themeColor: Int) {
        val darkBase = Color.parseColor("#0E0E10")
        val red = (Color.red(darkBase) + Color.red(themeColor)) / 6
        val green = (Color.green(darkBase) + Color.green(themeColor)) / 6
        val blue = (Color.blue(darkBase) + Color.blue(themeColor)) / 6
        rootLayout.background = ColorDrawable(Color.rgb(red, green, blue))
    }

    private suspend fun getMemoriesContext(): String =
        withContext(Dispatchers.IO) {
            try {
                memoryHelper.getRecentMemories()
                    .asSequence()
                    .filter { it.content.isNotBlank() }
                    .sortedByDescending { it.timestamp }
                    .take(20)
                    .joinToString(separator = "\n") { memory ->
                        "[${memory.type}] ${memory.content.trim()}"
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e(
                    "Monolith",
                    "getMemoriesContext failed",
                    e
                )
                ""
            }
        }

    private fun initTtsIfNeeded() {
        if (ttsManager != null) return

        ttsManager = TtsManager(this) {
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread

                // callback اكتمال النطق.
                // لا تضف عناصر واجهة أو مراجع غير موجودة في الملف.
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun showToast(message: String) {
        if (isFinishing || isDestroyed) return
        runOnUiThread {
            try {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            } catch (_: Exception) { }
        }
    }

    override fun onDestroy() {
        isActivityReady = false

        try {
            synchronized(requestLock) {
                activeApiCalls.values.forEach { call ->
                    try { call.cancel() } catch (_: Exception) { }
                }
                activeApiCalls.clear()

                activeLocalJobs.values.forEach { job ->
                    try { job.cancel() } catch (_: Exception) { }
                }
                activeLocalJobs.clear()

                pendingRequests.clear()
            }
        } catch (_: Exception) { }

        try {
            ttsManager?.shutdown()
        } catch (_: Exception) { }
        ttsManager = null

        super.onDestroy()
    }
}

// =========================================================
// DIALOG INITIATIVES PROTOCOL
// =========================================================

enum class DialogInitiativeKind {
    PERMISSION,
    MISSING_INFO,
    FOLLOW_UP,
    NEXT_STEP,
    CONTINUE_TASK
}

data class DialogInitiative(
    val kind: DialogInitiativeKind,
    val label: String,
    val text: String
)

data class DialogInitiativeParseResult(
    val content: String,
    val initiatives: List<DialogInitiative>
)

object DialogInitiativeProtocol {

    private const val START_MARKER = "<<INITIATIVES>>"
    private const val END_MARKER = "<<END_INITIATIVES>>"
    private const val MAX_INITIATIVES = 5

    const val INSTRUCTION: String =
        "قواعد المبادرات الحوارية (اتبعها حرفياً):\n" +
        "1) إذا كانت هناك خطوة تالية واضحة، أو معلومة ناقصة تحتاجها لإكمال المهمة، أو إذن صريح قبل فعل مؤثر، فأضف في نهاية ردّك كتلة واحدة فقط بالشكل التالي بالضبط، دون أي تعديل على العلامات:\n" +
        "$START_MARKER\n" +
        "[{\"kind\":\"FOLLOW_UP\",\"label\":\"عنوان زر قصير\",\"text\":\"النص الكامل الذي سيُرسل عند الضغط\"}]\n" +
        "$END_MARKER\n" +
        "2) الأنواع المسموحة فقط: PERMISSION, MISSING_INFO, FOLLOW_UP, NEXT_STEP, CONTINUE_TASK.\n" +
        "3) لا تضع أكثر من $MAX_INITIATIVES مبادرة، ولا تكرر المبادرة نفسها، ولا تضع الكتلة إذا لم تكن هناك حاجة حقيقية.\n" +
        "4) لا تكتب أي نص بعد $END_MARKER، ولا تُعِد كتابة الكتلة مرتين.\n" +
        "5) هذه المبادرات اقتراحات للمستخدم فقط، وليست أوامر تنفيذ تلقائية.\n" +
        "6) عند عدم الحاجة لمبادرات، لا تُضمِّن الكتلة إطلاقاً."

    fun parse(content: String): DialogInitiativeParseResult {
        val openIndex = content.indexOf(START_MARKER)
        if (openIndex < 0) {
            return DialogInitiativeParseResult(content, emptyList())
        }
        val closeIndex = content.indexOf(END_MARKER, openIndex + START_MARKER.length)
        if (closeIndex < 0) {
            return DialogInitiativeParseResult(content, emptyList())
        }

        val jsonText = content
            .substring(openIndex + START_MARKER.length, closeIndex)
            .trim()

        val cleanedContent = buildString {
            append(content.substring(0, openIndex))
            append(content.substring(closeIndex + END_MARKER.length))
        }.trim()

        val initiatives = try {
            val array = JSONArray(jsonText)
            val out = ArrayList<DialogInitiative>()
            var index = 0
            while (index < array.length() && out.size < MAX_INITIATIVES) {
                val obj = array.optJSONObject(index)
                index++
                if (obj == null) continue
                val kindStr = obj.optString("kind", "").trim().uppercase()
                val label = obj.optString("label", "").trim()
                val text = obj.optString("text", "").trim()
                if (label.isBlank() || text.isBlank()) continue
                val kind = when (kindStr) {
                    "PERMISSION" -> DialogInitiativeKind.PERMISSION
                    "MISSING_INFO" -> DialogInitiativeKind.MISSING_INFO
                    "FOLLOW_UP" -> DialogInitiativeKind.FOLLOW_UP
                    "NEXT_STEP" -> DialogInitiativeKind.NEXT_STEP
                    "CONTINUE_TASK" -> DialogInitiativeKind.CONTINUE_TASK
                    else -> continue
                }
                out.add(DialogInitiative(kind = kind, label = label, text = text))
            }
            out
        } catch (_: Exception) {
            emptyList()
        }

        return DialogInitiativeParseResult(cleanedContent, initiatives)
    }
}

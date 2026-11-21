package com.monolith

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.atomic.AtomicBoolean

class CodeWorkspaceActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CODE = "code_content"
        const val EXTRA_LANGUAGE = "code_language"
        const val EXTRA_TITLE = "code_title"
        const val EXTRA_ITEM_ID = "workspace_item_id"
        const val EXTRA_FILE_NAME = "workspace_file_name"
        const val EXTRA_SOURCE_CONVERSATION_ID = "source_conversation_id"
        const val EXTRA_SOURCE_MESSAGE_ID = "source_message_id"

        private const val STATE_ITEM_ID = "state_item_id"
        private const val STATE_CURRENT_VERSION = "state_current_version"
        private const val STATE_ORIGINAL_CODE = "state_original_code"
        private const val STATE_CURRENT_CODE = "state_current_code"
        private const val STATE_CURRENT_LANGUAGE = "state_current_language"
        private const val STATE_CURRENT_TITLE = "state_current_title"
        private const val STATE_CURRENT_FILE_NAME = "state_current_file_name"
        private const val STATE_IS_NEW_ITEM = "state_is_new_item"
        private const val STATE_IS_DIRTY = "state_is_dirty"
        private const val STATE_IS_READ_ONLY_VERSION = "state_is_read_only_version"
        private const val STATE_VIEWED_VERSION_NUMBER = "state_viewed_version_number"
        private const val STATE_SELECTED_FOLDER_ID = "state_selected_folder_id"
        private const val STATE_SELECTED_TAG_ID = "state_selected_tag_id"
        private const val STATE_ACTIVE_SECTION = "state_active_section"
        private const val STATE_FONT_SIZE = "state_font_size"
        private const val STATE_SHOW_LINE_NUMBERS = "state_show_line_numbers"
        private const val STATE_IS_EXPANDED = "state_is_expanded"
    }

    private val backgroundColor = Color.rgb(8, 12, 29)
    private val panelColor = Color.rgb(18, 25, 49)
    private val borderColor = Color.rgb(48, 65, 108)
    private val accentColor = Color.rgb(0, 190, 255)
    private val purpleColor = Color.rgb(151, 74, 255)
    private val textColor = Color.rgb(235, 241, 255)
    private val mutedColor = Color.rgb(145, 160, 190)

    private lateinit var db: AppDatabase

    // Core editor & lifecycle state
    private var itemId: String? = null
    private var currentVersion: Int = 0
    private var originalCode: String = ""
    private var currentCode: String = ""
    private var currentLanguage: String = "Kotlin"
    private var currentTitle: String = "مقتطف برمجي"
    private var currentFileName: String? = null
    private var sourceConversationId: String? = null
    private var sourceMessageId: String? = null

    private var isNewItem: Boolean = true
    private var isDirty: Boolean = false
    private var isReadOnlyVersion: Boolean = false
    private var viewedVersionNumber: Int? = null

    private var selectedFolderId: String? = null
    private var selectedTagId: String? = null
    private var activeSection: String = AppDatabase.WORKSPACE_STATE_ACTIVE
    private var editorFontSize: Float = 14f
    private var showLineNumbers: Boolean = false
    private var isExpanded: Boolean = false

    private var isApplyingProgrammaticText: Boolean = false
    private val isSavingInProgress = AtomicBoolean(false)

    // UI references bound to current Activity instance
    private lateinit var editor: EditText
    private lateinit var titleField: EditText
    private lateinit var languageField: TextView
    private lateinit var statusBadge: TextView
    private lateinit var metaSummaryLabel: TextView
    private lateinit var lineNumbersView: TextView
    private lateinit var workspaceBrowserContainer: LinearLayout
    private lateinit var tagsBarContainer: LinearLayout

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        db = AppDatabase(applicationContext)

        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor

        initializeState(savedInstanceState)
        buildInterface()
        syncUiFromState()
        refreshWorkspacePanel()
        refreshTagsBar()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        captureEditorIntoState()
        super.onSaveInstanceState(outState)
        outState.putString(STATE_ITEM_ID, itemId)
        outState.putInt(STATE_CURRENT_VERSION, currentVersion)
        outState.putString(STATE_ORIGINAL_CODE, originalCode)
        outState.putString(STATE_CURRENT_CODE, currentCode)
        outState.putString(STATE_CURRENT_LANGUAGE, currentLanguage)
        outState.putString(STATE_CURRENT_TITLE, currentTitle)
        outState.putString(STATE_CURRENT_FILE_NAME, currentFileName)
        outState.putBoolean(STATE_IS_NEW_ITEM, isNewItem)
        outState.putBoolean(STATE_IS_DIRTY, isDirty)
        outState.putBoolean(STATE_IS_READ_ONLY_VERSION, isReadOnlyVersion)
        outState.putInt(STATE_VIEWED_VERSION_NUMBER, viewedVersionNumber ?: -1)
        outState.putString(STATE_SELECTED_FOLDER_ID, selectedFolderId)
        outState.putString(STATE_SELECTED_TAG_ID, selectedTagId)
        outState.putString(STATE_ACTIVE_SECTION, activeSection)
        outState.putFloat(STATE_FONT_SIZE, editorFontSize)
        outState.putBoolean(STATE_SHOW_LINE_NUMBERS, showLineNumbers)
        outState.putBoolean(STATE_IS_EXPANDED, isExpanded)
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun initializeState(savedInstanceState: Bundle?) {
        sourceConversationId = intent.getStringExtra(EXTRA_SOURCE_CONVERSATION_ID)
        sourceMessageId = intent.getStringExtra(EXTRA_SOURCE_MESSAGE_ID)

        if (savedInstanceState != null) {
            itemId = savedInstanceState.getString(STATE_ITEM_ID)
            currentVersion = savedInstanceState.getInt(STATE_CURRENT_VERSION, 0)
            originalCode = savedInstanceState.getString(STATE_ORIGINAL_CODE).orEmpty()
            currentCode = savedInstanceState.getString(STATE_CURRENT_CODE).orEmpty()
            currentLanguage = savedInstanceState.getString(STATE_CURRENT_LANGUAGE) ?: "Kotlin"
            currentTitle = savedInstanceState.getString(STATE_CURRENT_TITLE) ?: "مقتطف برمجي"
            currentFileName = savedInstanceState.getString(STATE_CURRENT_FILE_NAME)
            isNewItem = savedInstanceState.getBoolean(STATE_IS_NEW_ITEM, itemId == null)
            isDirty = savedInstanceState.getBoolean(STATE_IS_DIRTY, false)
            isReadOnlyVersion = savedInstanceState.getBoolean(STATE_IS_READ_ONLY_VERSION, false)
            val savedViewedVer = savedInstanceState.getInt(STATE_VIEWED_VERSION_NUMBER, -1)
            viewedVersionNumber = if (savedViewedVer > 0) savedViewedVer else null
            selectedFolderId = savedInstanceState.getString(STATE_SELECTED_FOLDER_ID)
            selectedTagId = savedInstanceState.getString(STATE_SELECTED_TAG_ID)
            activeSection = savedInstanceState.getString(STATE_ACTIVE_SECTION)
                ?: AppDatabase.WORKSPACE_STATE_ACTIVE
            editorFontSize = savedInstanceState.getFloat(STATE_FONT_SIZE, 14f)
            showLineNumbers = savedInstanceState.getBoolean(STATE_SHOW_LINE_NUMBERS, false)
            isExpanded = savedInstanceState.getBoolean(STATE_IS_EXPANDED, false)
            return
        }

        val incomingItemId = intent.getStringExtra(EXTRA_ITEM_ID)
        if (!incomingItemId.isNullOrBlank()) {
            loadSavedItemIntoEditor(incomingItemId)
        } else {
            val initialCode = intent.getStringExtra(EXTRA_CODE).orEmpty()
            val initialLang = intent.getStringExtra(EXTRA_LANGUAGE)?.trim().orEmpty()
                .ifEmpty { "Kotlin" }
            val initialTitle = intent.getStringExtra(EXTRA_TITLE)?.trim().orEmpty()
                .ifEmpty { "مقتطف برمجي" }

            itemId = null
            currentVersion = 0
            originalCode = initialCode
            currentCode = initialCode
            currentLanguage = initialLang
            currentTitle = initialTitle
            currentFileName = intent.getStringExtra(EXTRA_FILE_NAME)
            isNewItem = true
            isDirty = false
            isReadOnlyVersion = false
            viewedVersionNumber = null
        }
    }

    private fun loadSavedItemIntoEditor(targetItemId: String): Boolean {
        return try {
            val itemMap = db.getWorkspaceCodeItem(targetItemId) ?: return false
            val latestVerMap = db.getLatestWorkspaceCodeVersion(targetItemId)
            val versionNumFromItem = itemMap["current_version"]?.toIntOrNull()
                ?: latestVerMap?.get("version_number")?.toIntOrNull()
                ?: 1
            val versionMap = latestVerMap
                ?: db.getWorkspaceCodeVersion(targetItemId, versionNumFromItem)

            val loadedCode = versionMap?.get("content").orEmpty()
            val loadedLang = versionMap?.get("language")
                ?.takeIf { it.isNotBlank() }
                ?: itemMap["language"]?.takeIf { it.isNotBlank() }
                ?: "Kotlin"
            val loadedTitle = itemMap["title"]?.takeIf { it.isNotBlank() } ?: "مقتطف برمجي"
            val loadedFileName = itemMap["file_name"]
            val loadedFolderId = itemMap["folder_id"]

            itemId = itemMap["id"] ?: targetItemId
            currentVersion = versionMap?.get("version_number")?.toIntOrNull() ?: versionNumFromItem
            originalCode = loadedCode
            currentCode = loadedCode
            currentLanguage = loadedLang
            currentTitle = loadedTitle
            currentFileName = loadedFileName
            selectedFolderId = loadedFolderId
            isNewItem = false
            isDirty = false
            isReadOnlyVersion = false
            viewedVersionNumber = null
            true
        } catch (ex: Exception) {
            showMessage("تعذر تحميل العنصر: ${ex.message ?: "خطأ في قاعدة البيانات"}")
            false
        }
    }

    private fun captureEditorIntoState() {
        if (::editor.isInitialized) {
            currentCode = editor.text.toString()
        }
        if (::titleField.isInitialized) {
            currentTitle = titleField.text.toString()
        }
        if (::languageField.isInitialized && languageField.text.isNotBlank()) {
            currentLanguage = languageField.text.toString()
        }
    }

    private fun recomputeDirtyState() {
        if (isReadOnlyVersion) {
            isDirty = false
            return
        }
        isDirty = currentCode != originalCode
    }

    private fun setEditorTextProgrammatically(newText: String) {
        isApplyingProgrammaticText = true
        try {
            editor.setText(newText)
            currentCode = newText
            updateLineNumbers()
        } finally {
            isApplyingProgrammaticText = false
        }
    }

    private fun syncUiFromState() {
        if (!::editor.isInitialized) return

        isApplyingProgrammaticText = true
        try {
            if (titleField.text.toString() != currentTitle) {
                titleField.setText(currentTitle)
            }
            languageField.text = currentLanguage
            if (editor.text.toString() != currentCode) {
                editor.setText(currentCode)
            }
            editor.textSize = editorFontSize
            editor.minLines = if (isExpanded) 22 else 12
            val targetEditorHeight = if (isExpanded) dp(420) else dp(260)
            if (editor.layoutParams != null && editor.layoutParams.height != targetEditorHeight) {
                editor.layoutParams = LinearLayout.LayoutParams(-1, targetEditorHeight)
            }
            editor.isEnabled = !isReadOnlyVersion
            titleField.isEnabled = !isReadOnlyVersion
        } finally {
            isApplyingProgrammaticText = false
        }

        updateLineNumbers()

        val statusText = when {
            isReadOnlyVersion && viewedVersionNumber != null ->
                "● إصدار قديم v$viewedVersionNumber (للعرض فقط)"
            isNewItem && isDirty ->
                "● مسودة جديدة غير محفوظة *"
            isNewItem ->
                "● مقتطف جديد غير محفوظ"
            isDirty ->
                "● v$currentVersion — تعديلات غير محفوظة *"
            else ->
                "● v$currentVersion — محفوظ"
        }

        val statusColor = when {
            isReadOnlyVersion -> purpleColor
            isDirty -> Color.rgb(255, 195, 70)
            isNewItem -> mutedColor
            else -> accentColor
        }

        statusBadge.text = statusText
        statusBadge.setTextColor(statusColor)

        val shortId = itemId?.take(8) ?: "غير محفوظ"
        val folderInfo = if (selectedFolderId != null) {
            val folderName = findFolderName(selectedFolderId) ?: selectedFolderId
            "مجلد: $folderName"
        } else {
            "بدون مجلد"
        }
        metaSummaryLabel.text = "المعرّف: $shortId | الإصدار: ${if (currentVersion > 0) "v$currentVersion" else "-"} | $folderInfo"
    }

    private fun updateLineNumbers() {
        if (!::lineNumbersView.isInitialized || !::editor.isInitialized) return
        if (!showLineNumbers) {
            lineNumbersView.visibility = View.GONE
            return
        }
        lineNumbersView.visibility = View.VISIBLE
        lineNumbersView.textSize = editorFontSize
        val lineCount = editor.text.toString().split('\n').size.coerceAtLeast(1)
        val sb = StringBuilder()
        for (i in 1..lineCount) {
            if (i > 1) sb.append('\n')
            sb.append(i)
        }
        lineNumbersView.text = sb.toString()
    }

    private fun buildInterface() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(backgroundColor)
            isFillViewport = true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(25))
        }

        scroll.addView(root)
        setContentView(scroll)

        // Header
        val header = horizontalRow()
        header.addView(
            label("MONOLITH", 20f, textColor, true),
            weightParams()
        )
        header.addView(action("إغلاق") { finish() })
        root.addView(header)

        root.addView(label("CODE WORKSPACE", 10f, accentColor, true))
        root.addView(label("مساحة العمل البرمجة", 13f, mutedColor, false))

        metaSummaryLabel = label("", 11f, mutedColor, false)
        root.addView(metaSummaryLabel)

        // Navigation sections
        root.addView(section("مساحة العمل"))

        val navigation = horizontalRow()
        val sections = listOf(
            "المحرر" to "EDITOR",
            "مخزن الأكواد" to AppDatabase.WORKSPACE_STATE_ACTIVE,
            "الأرشيف" to AppDatabase.WORKSPACE_STATE_ARCHIVED,
            "المحذوفات" to AppDatabase.WORKSPACE_STATE_TRASHED
        )

        sections.forEach { (displayName, sectionKey) ->
            navigation.addView(
                action(displayName) {
                    captureEditorIntoState()
                    if (sectionKey == "EDITOR") {
                        refreshWorkspacePanel()
                        refreshTagsBar()
                    } else {
                        activeSection = sectionKey
                        refreshWorkspacePanel()
                    }
                },
                LinearLayout.LayoutParams(0, dp(43), 1f).apply {
                    marginEnd = dp(4)
                }
            )
        }
        root.addView(navigation)

        // Workspace items / folders / trash / archive browser panel
        workspaceBrowserContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground()
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }.apply {
            (this as View).layoutParams = LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
            }
        }
        root.addView(workspaceBrowserContainer)

        // Code information
        root.addView(section("معلومات الكود"))

        titleField = editField(currentTitle).apply {
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (isApplyingProgrammaticText) return
                    currentTitle = s?.toString().orEmpty()
                }
            })
        }
        root.addView(titleField)

        languageField = label(currentLanguage, 14f, accentColor, true).apply {
            background = roundedBackground()
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener {
                if (isReadOnlyVersion) {
                    showMessage("النسخة القديمة المعروضة للقراءة فقط؛ استعدها أولاً لتعديلها")
                    return@setOnClickListener
                }
                chooseLanguage()
            }
        }

        root.addView(section("لغة البرمجة"))
        root.addView(languageField)

        // Folders & Tags actions row
        root.addView(section("المجلدات والوسوم"))

        val folderTagActions = horizontalRow()
        folderTagActions.addView(
            action("إنشاء مجلد") { promptCreateFolder() },
            LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(4) }
        )
        folderTagActions.addView(
            action("نقل لمجلد") { promptAssignFolderForCurrentItem() },
            LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(4) }
        )
        folderTagActions.addView(
            action("إضافة وسم") { promptAttachTagToCurrentItem() },
            LinearLayout.LayoutParams(0, dp(40), 1f)
        )
        root.addView(folderTagActions)

        tagsBarContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            }
        }
        root.addView(tagsBarContainer)

        // Editor
        root.addView(section("محرر الكود"))

        val editorPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground()
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }

        val editorHeader = horizontalRow()
        editorHeader.addView(
            label("SOURCE CODE", 10f, accentColor, true),
            weightParams()
        )
        statusBadge = label("● غير محفوظ", 10f, mutedColor, true)
        editorHeader.addView(statusBadge)
        editorPanel.addView(editorHeader)

        val editorBodyRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }

        lineNumbersView = TextView(this).apply {
            setTextColor(mutedColor)
            typeface = Typeface.MONOSPACE
            textSize = editorFontSize
            setPadding(dp(6), dp(12), dp(8), dp(12))
            gravity = Gravity.TOP or Gravity.END
            visibility = View.GONE
        }
        editorBodyRow.addView(
            lineNumbersView,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )

        editor = EditText(this).apply {
            setText(currentCode)
            setTextColor(Color.rgb(205, 224, 255))
            setHintTextColor(mutedColor)
            hint = "اكتب الكود البرمجي هنا..."
            textSize = editorFontSize
            typeface = Typeface.MONOSPACE
            gravity = Gravity.TOP or Gravity.START
            minLines = if (isExpanded) 22 else 12
            setPadding(dp(10), dp(12), dp(10), dp(12))
            setBackgroundColor(Color.TRANSPARENT)

            inputType =
                android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS

            isSingleLine = false

            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (isApplyingProgrammaticText) return
                    currentCode = s?.toString().orEmpty()
                    recomputeDirtyState()
                    updateLineNumbers()
                    refreshStatusOnly()
                }
            })
        }

        editorBodyRow.addView(
            editor,
            LinearLayout.LayoutParams(0, if (isExpanded) dp(420) else dp(260), 1f)
        )

        editorPanel.addView(editorBodyRow)
        root.addView(editorPanel)

        // Main tools
        root.addView(section("أدوات الكود البرمجي"))

        val tools = listOf(
            Tool("نسخ الكود كاملاً", "copy"),
            Tool("تحرير الكود", "edit"),
            Tool("تحليل الكود", "analyze"),
            Tool("توسيع مساحة الكود", "expand"),
            Tool("تغيير لغة البرمجة", "language"),
            Tool("تخصيص الخط والألوان", "style"),
            Tool("إظهار أرقام الأسطر", "lines"),
            Tool("تحديد جزء ونسخه", "select"),
            Tool("مقارنة نسختين", "compare"),
            Tool("حفظ مقتطف مستقل", "save")
        )

        addToolGrid(root, tools)

        // AI tools (explicitly non-fictional when no AI engine is wired)
        root.addView(section("أدوات الذكاء الاصطناعي المرتبطة بالكود"))

        val aiTools = listOf(
            Tool("شرح الكود", "explain"),
            Tool("إصلاح الأخطاء", "fix"),
            Tool("تحسين وإعادة هيكلة الكود", "refactor")
        )

        addToolGrid(root, aiTools)

        // Version & lifecycle management
        root.addView(section("إدارة الكود والإصدارات"))

        addManagementButton(root, "كشف التعديلات ومقارنة الإصدارات") {
            compareVersionsDialog()
        }

        addManagementButton(root, "تاريخ جميع إصدارات الكود") {
            showVersionHistoryDialog()
        }

        addManagementButton(root, "حفظ نسخة جديدة") {
            saveCurrentState(forceNewVersionWhenSavedItem = true)
        }

        addManagementButton(root, "نقل إلى الأرشيف") {
            archiveCurrentItem()
        }

        addManagementButton(root, "نقل إلى سلة المحذوفات") {
            trashCurrentItem()
        }
    }

    private fun refreshStatusOnly() {
        syncUiFromState()
    }

    // =========================================================
    // SAVE & VERSIONING LOGIC
    // =========================================================

    private fun saveCurrentState(forceNewVersionWhenSavedItem: Boolean = true) {
        if (!isSavingInProgress.compareAndSet(false, true)) {
            showMessage("عملية الحفظ قيد التنفيذ بالفعل")
            return
        }

        try {
            captureEditorIntoState()

            if (isReadOnlyVersion) {
                showMessage("أنت تعرض نسخة قديمة للقراءة فقط؛ استعدها أولاً لحفظ التعديلات")
                return
            }

            val cleanTitle = currentTitle.trim().ifEmpty {
                currentFileName?.trim().orEmpty().ifEmpty { "مقتطف برمجي" }
            }
            currentTitle = cleanTitle
            val cleanLang = currentLanguage.trim().ifEmpty { "Kotlin" }
            currentLanguage = cleanLang

            val existingId = itemId
            if (isNewItem || existingId.isNullOrBlank()) {
                val createdId = db.createWorkspaceCodeItem(
                    title = cleanTitle,
                    content = currentCode,
                    language = cleanLang,
                    fileName = currentFileName,
                    folderId = selectedFolderId,
                    sourceConversationId = sourceConversationId,
                    sourceMessageId = sourceMessageId,
                    editSource = AppDatabase.WORKSPACE_SOURCE_MANUAL,
                    changeSummary = "Initial version"
                )

                val latestVer = db.getLatestWorkspaceCodeVersion(createdId)
                val itemRow = db.getWorkspaceCodeItem(createdId)
                val savedVerNum = latestVer?.get("version_number")?.toIntOrNull()
                    ?: itemRow?.get("current_version")?.toIntOrNull()
                    ?: 1

                itemId = createdId
                currentVersion = savedVerNum
                originalCode = currentCode
                isNewItem = false
                isDirty = false
                isReadOnlyVersion = false
                viewedVersionNumber = null

                syncUiFromState()
                refreshWorkspacePanel()
                refreshTagsBar()
                showMessage("تم حفظ المقتطف وإنشاء الإصدار v$currentVersion")
                return
            }

            // Item already exists in database
            val hasCodeChange = currentCode != originalCode
            val currentItemRow = db.getWorkspaceCodeItem(existingId)
            val titleChanged = (currentItemRow?.get("title") ?: "") != cleanTitle
            val langChanged = (currentItemRow?.get("language") ?: "") != cleanLang

            if (!hasCodeChange && !forceNewVersionWhenSavedItem && !titleChanged && !langChanged) {
                showMessage("لا توجد تغييرات جديدة للحفظ")
                return
            }

            if (!hasCodeChange && !forceNewVersionWhenSavedItem && (titleChanged || langChanged)) {
                val nextVer = db.addWorkspaceCodeVersion(
                    itemId = existingId,
                    content = currentCode,
                    language = cleanLang,
                    editSource = AppDatabase.WORKSPACE_SOURCE_MANUAL,
                    changeSummary = "تحديث البيانات الوصفية (v${currentVersion + 1})",
                    title = cleanTitle,
                    fileName = currentFileName
                )
                if (nextVer == null) {
                    showMessage("تعذر تحديث بيانات العنصر في قاعدة البيانات")
                    return
                }
                currentVersion = nextVer
                originalCode = currentCode
                isDirty = false
                syncUiFromState()
                refreshWorkspacePanel()
                showMessage("تم حفظ التحديث كإصدار v$currentVersion")
                return
            }

            val summary = if (hasCodeChange) {
                "تعديل الكود وحفظ الإصدار"
            } else {
                "حفظ نسخة جديدة من الكود"
            }

            val createdVersion = db.addWorkspaceCodeVersion(
                itemId = existingId,
                content = currentCode,
                language = cleanLang,
                editSource = AppDatabase.WORKSPACE_SOURCE_MANUAL,
                changeSummary = summary,
                title = cleanTitle,
                fileName = currentFileName
            )

            if (createdVersion == null) {
                showMessage("فشل حفظ الإصدار الجديد في قاعدة البيانات")
                return
            }

            currentVersion = createdVersion
            originalCode = currentCode
            isNewItem = false
            isDirty = false
            isReadOnlyVersion = false
            viewedVersionNumber = null

            syncUiFromState()
            refreshWorkspacePanel()
            showMessage("تم حفظ الإصدار v$currentVersion بنجاح")
        } catch (ex: Exception) {
            showMessage("خطأ أثناء الحفظ: ${ex.message ?: "تعذر إتمام العملية"}")
        } finally {
            isSavingInProgress.set(false)
        }
    }

    // =========================================================
    // VERSIONS VIEW, COMPARE & RESTORE
    // =========================================================

    private fun showVersionHistoryDialog() {
        val targetId = itemId
        if (isNewItem || targetId.isNullOrBlank()) {
            showMessage("احفظ المقتطف أولاً لعرض تاريخ إصداراته")
            return
        }

        val history = db.getWorkspaceCodeVersionHistory(targetId)
        if (history.isEmpty()) {
            showMessage("لا توجد إصدارات مسجلة لهذا العنصر")
            return
        }

        val labels = history.map { row ->
            val ver = row["version_number"] ?: "?"
            val lang = row["language"] ?: ""
            val src = row["edit_source"] ?: ""
            val sum = row["change_summary"]?.takeIf { it.isNotBlank() } ?: "بدون ملخص"
            val marker = if (ver.toIntOrNull() == currentVersion && !isReadOnlyVersion) " [الحالي]" else ""
            "v$ver$marker • $lang • $src\n$sum"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("تاريخ إصدارات الكود (${history.size})")
            .setItems(labels) { _, index ->
                val chosen = history[index]
                val verNum = chosen["version_number"]?.toIntOrNull() ?: return@setItems
                openVersionInReadOnlyMode(targetId, verNum)
            }
            .setNeutralButton("العودة لآخر إصدار") { _, _ ->
                restoreLatestVersionIntoEditor(targetId)
            }
            .setNegativeButton("إغلاق", null)
            .show()
    }

    private fun openVersionInReadOnlyMode(targetItemId: String, versionNumber: Int) {
        val versionMap = db.getWorkspaceCodeVersion(targetItemId, versionNumber)
        if (versionMap == null) {
            showMessage("تعذر العثور على الإصدار v$versionNumber")
            return
        }

        val content = versionMap["content"].orEmpty()
        val lang = versionMap["language"]?.takeIf { it.isNotBlank() } ?: currentLanguage

        currentLanguage = lang
        isReadOnlyVersion = true
        viewedVersionNumber = versionNumber
        isDirty = false

        setEditorTextProgrammatically(content)
        syncUiFromState()
        showMessage("عرض الإصدار v$versionNumber للقراءة فقط (لن يُعدّل مباشرة)")
    }

    private fun restoreLatestVersionIntoEditor(targetItemId: String) {
        val latest = db.getLatestWorkspaceCodeVersion(targetItemId)
        if (latest == null) {
            showMessage("لا يوجد إصدار أخير متاح")
            return
        }

        val verNum = latest["version_number"]?.toIntOrNull() ?: currentVersion
        val content = latest["content"].orEmpty()
        val lang = latest["language"]?.takeIf { it.isNotBlank() } ?: currentLanguage

        currentVersion = verNum
        currentLanguage = lang
        originalCode = content
        isReadOnlyVersion = false
        viewedVersionNumber = null
        isDirty = false

        setEditorTextProgrammatically(content)
        syncUiFromState()
        showMessage("تم استرجاع آخر إصدار v$verNum")
    }

    private fun restoreViewedVersionToEditor() {
        val targetId = itemId
        val viewedVer = viewedVersionNumber
        if (targetId.isNullOrBlank() || viewedVer == null) {
            showMessage("لا يوجد إصدار قديم محدد للاستعادة")
            return
        }

        val versionMap = db.getWorkspaceCodeVersion(targetId, viewedVer)
        if (versionMap == null) {
            showMessage("تعذر قراءة بيانات الإصدار v$viewedVer")
            return
        }

        val restoredContent = versionMap["content"].orEmpty()
        val restoredLang = versionMap["language"]?.takeIf { it.isNotBlank() } ?: currentLanguage

        // Exit read-only preview and load content as a pending edit against originalCode
        // so saving creates a new version while preserving history.
        currentLanguage = restoredLang
        isReadOnlyVersion = false
        viewedVersionNumber = null
        setEditorTextProgrammatically(restoredContent)
        recomputeDirtyState()
        syncUiFromState()
        showMessage("تم تحميل الإصدار v$viewedVer في المحرر؛ اضغط حفظ لإنشاء إصدار جديد")
    }

    private fun compareVersionsDialog() {
        captureEditorIntoState()
        val targetId = itemId
        if (isNewItem || targetId.isNullOrBlank()) {
            val baseLen = originalCode.length
            val currLen = currentCode.length
            val lineDiff = currentCode.split('\n').size - originalCode.split('\n').size
            AlertDialog.Builder(this)
                .setTitle("مقارنة الحالة الحالية بالأصلية")
                .setMessage(
                    "المقتطف غير محفوظ بعد.\n" +
                        "طول الكود الأصلي: $baseLen حرف\n" +
                        "طول الكود الحالي: $currLen حرف\n" +
                        "فرق عدد الأسطر: $lineDiff\n" +
                        "وجود تعديلات: ${if (currentCode != originalCode) "نعم" else "لا"}"
                )
                .setPositiveButton("موافق", null)
                .show()
            return
        }

        val history = db.getWorkspaceCodeVersionHistory(targetId)
        if (history.isEmpty()) {
            showMessage("لا توجد إصدارات للمقارنة")
            return
        }

        val versionNames = history.map { row ->
            val v = row["version_number"] ?: "?"
            val s = row["change_summary"]?.takeIf { it.isNotBlank() } ?: ""
            "الإصدار v$v ${if (s.isNotEmpty()) "- $s" else ""}"
        }.toTypedArray()

        var firstIdx = 0
        var secondIdx = if (history.size > 1) 1 else 0

        AlertDialog.Builder(this)
            .setTitle("اختر الإصدار الأول للمقارنة")
            .setSingleChoiceItems(versionNames, firstIdx) { _, which ->
                firstIdx = which
            }
            .setPositiveButton("التالي") { _, _ ->
                AlertDialog.Builder(this)
                    .setTitle("اختر الإصدار الثاني للمقارنة")
                    .setSingleChoiceItems(versionNames, secondIdx) { _, which ->
                        secondIdx = which
                    }
                    .setPositiveButton("مقارنة") { _, _ ->
                        val vA = history[firstIdx]["version_number"]?.toIntOrNull() ?: return@setPositiveButton
                        val vB = history[secondIdx]["version_number"]?.toIntOrNull() ?: return@setPositiveButton
                        val mapA = db.getWorkspaceCodeVersion(targetId, vA)
                        val mapB = db.getWorkspaceCodeVersion(targetId, vB)
                        val textA = mapA?.get("content").orEmpty()
                        val textB = mapB?.get("content").orEmpty()
                        val linesA = textA.split('\n')
                        val linesB = textB.split('\n')
                        val identical = textA == textB

                        AlertDialog.Builder(this)
                            .setTitle("نتيجة مقارنة v$vA مع v$vB")
                            .setMessage(
                                "التطابق الكامل: ${if (identical) "متطابقان" else "يوجد اختلاف"}\n" +
                                    "v$vA: ${linesA.size} سطر | ${textA.length} حرف\n" +
                                    "v$vB: ${linesB.size} سطر | ${textB.length} حرف"
                            )
                            .setPositiveButton("عرض v$vA") { _, _ ->
                                openVersionInReadOnlyMode(targetId, vA)
                            }
                            .setNeutralButton("عرض v$vB") { _, _ ->
                                openVersionInReadOnlyMode(targetId, vB)
                            }
                            .setNegativeButton("إغلاق", null)
                            .show()
                    }
                    .setNegativeButton("إلغاء", null)
                    .show()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    // =========================================================
    // WORKSPACE ITEMS, FOLDERS, TAGS & LIFECYCLE
    // =========================================================

    private fun refreshWorkspacePanel() {
        if (!::workspaceBrowserContainer.isInitialized) return
        workspaceBrowserContainer.removeAllViews()

        val sectionTitle = when (activeSection) {
            AppDatabase.WORKSPACE_STATE_ARCHIVED -> "العناصر المؤرشفة"
            AppDatabase.WORKSPACE_STATE_TRASHED -> "سلة المحذوفات"
            else -> "العناصر المحفوظة والمجلدات"
        }

        val headerRow = horizontalRow()
        headerRow.addView(label(sectionTitle, 12f, accentColor, true), weightParams())

        if (activeSection == AppDatabase.WORKSPACE_STATE_ACTIVE) {
            headerRow.addView(
                action("مقتطف جديد") { startNewBlankSnippet() },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(32))
            )
        }
        workspaceBrowserContainer.addView(headerRow)

        if (activeSection == AppDatabase.WORKSPACE_STATE_ACTIVE) {
            val folders = db.getWorkspaceFolders(null)
            val folderScroll = HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                layoutParams = LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(6)
                    bottomMargin = dp(6)
                }
            }
            val folderChipRow = horizontalRow()

            val allFoldersChip = action(
                if (selectedFolderId == null) "[الكل]" else "الكل"
            ) {
                selectedFolderId = null
                refreshWorkspacePanel()
            }
            folderChipRow.addView(
                allFoldersChip,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)).apply {
                    marginEnd = dp(6)
                }
            )

            folders.forEach { f ->
                val fId = f["id"] ?: return@forEach
                val fName = f["name"] ?: "مجلد"
                val isSelected = selectedFolderId == fId
                folderChipRow.addView(
                    action(if (isSelected) "[$fName]" else fName) {
                        selectedFolderId = if (isSelected) null else fId
                        refreshWorkspacePanel()
                    },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)).apply {
                        marginEnd = dp(6)
                    }
                )
            }

            folderScroll.addView(folderChipRow)
            workspaceBrowserContainer.addView(folderScroll)
        }

        val items: List> = if (
            activeSection == AppDatabase.WORKSPACE_STATE_ACTIVE && selectedTagId != null
        ) {
            db.getWorkspaceItemsByTag(
                tagId = selectedTagId!!,
                lifecycleState = AppDatabase.WORKSPACE_STATE_ACTIVE
            )
        } else {
            db.getWorkspaceCodeItems(
                lifecycleState = activeSection,
                folderId = if (activeSection == AppDatabase.WORKSPACE_STATE_ACTIVE) selectedFolderId else null
            )
        }

        if (items.isEmpty()) {
            workspaceBrowserContainer.addView(
                label("لا توجد عناصر في هذا القسم حالياً.", 11f, mutedColor, false).apply {
                    setPadding(dp(4), dp(8), dp(4), dp(4))
                }
            )
            return
        }

        items.forEach { row ->
            val id = row["id"] ?: return@forEach
            val title = row["title"] ?: "بدون عنوان"
            val lang = row["language"] ?: "Text"
            val ver = row["current_version"] ?: "1"
            val isCurrent = id == itemId

            val itemCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = roundedBackground()
                setPadding(dp(10), dp(8), dp(10), dp(8))
                layoutParams = LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(6)
                }
            }

            val titleLabel = label(
                "${if (isCurrent) "▶ " else ""}$title (v$ver • $lang)",
                12f,
                if (isCurrent) accentColor else textColor,
                true
            )
            itemCard.addView(titleLabel)

            val btnsRow = horizontalRow().apply {
                (this as View).layoutParams = LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(6)
                }
            }

            when (activeSection) {
                AppDatabase.WORKSPACE_STATE_ACTIVE -> {
                    btnsRow.addView(
                        action("فتح") {
                            if (loadSavedItemIntoEditor(id)) {
                                syncUiFromState()
                                refreshWorkspacePanel()
                                refreshTagsBar()
                                showMessage("تم فتح: $title")
                            }
                        },
                        LinearLayout.LayoutParams(0, dp(34), 1f).apply { marginEnd = dp(4) }
                    )
                    btnsRow.addView(
                        action("أرشفة") {
                            if (db.archiveWorkspaceCodeItem(id)) {
                                if (itemId == id) {
                                    startNewBlankSnippet()
                                }
                                refreshWorkspacePanel()
                                showMessage("تم نقل العنصر إلى الأرشيف")
                            } else {
                                showMessage("تعذر أرشفة العنصر")
                            }
                        },
                        LinearLayout.LayoutParams(0, dp(34), 1f).apply { marginEnd = dp(4) }
                    )
                    btnsRow.addView(
                        action("سلة المحذوفات") {
                            if (db.moveWorkspaceCodeItemToTrash(id)) {
                                if (itemId == id) {
                                    startNewBlankSnippet()
                                }
                                refreshWorkspacePanel()
                                showMessage("تم نقل العنصر إلى سلة المحذوفات")
                            } else {
                                showMessage("تعذر نقل العنصر إلى سلة المحذوفات")
                            }
                        },
                        LinearLayout.LayoutParams(0, dp(34), 1f)
                    )
                }

                AppDatabase.WORKSPACE_STATE_ARCHIVED -> {
                    btnsRow.addView(
                        action("فتح") {
                            if (loadSavedItemIntoEditor(id)) {
                                syncUiFromState()
                                refreshTagsBar()
                                showMessage("تم تحميل العنصر المؤرشف: $title")
                            }
                        },
                        LinearLayout.LayoutParams(0, dp(34), 1f).apply { marginEnd = dp(6) }
                    )
                    btnsRow.addView(
                        action("استعادة للنشط") {
                            if (db.restoreWorkspaceCodeItem(id)) {
                                refreshWorkspacePanel()
                                showMessage("تمت استعادة العنصر")
                            } else {
                                showMessage("تعذرت استعادة العنصر")
                            }
                        },
                        LinearLayout.LayoutParams(0, dp(34), 1f)
                    )
                }

                AppDatabase.WORKSPACE_STATE_TRASHED -> {
                    btnsRow.addView(
                        action("استعادة") {
                            if (db.restoreWorkspaceCodeItem(id)) {
                                refreshWorkspacePanel()
                                showMessage("تمت استعادة العنصر من سلة المحذوفات")
                            } else {
                                showMessage("تعذرت استعادة العنصر")
                            }
                        },
                        LinearLayout.LayoutParams(0, dp(34), 1f).apply { marginEnd = dp(6) }
                    )
                    btnsRow.addView(
                        action("حذف نهائي") {
                            confirmPermanentDelete(id, title)
                        },
                        LinearLayout.LayoutParams(0, dp(34), 1f)
                    )
                }
            }

            itemCard.addView(btnsRow)
            workspaceBrowserContainer.addView(itemCard)
        }
    }

    private fun refreshTagsBar() {
        if (!::tagsBarContainer.isInitialized) return
        tagsBarContainer.removeAllViews()

        val allTags = db.getAllWorkspaceTags()
        if (allTags.isNotEmpty()) {
            val filterScroll = HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                layoutParams = LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(4)
                }
            }
            val filterRow = horizontalRow()
            filterRow.addView(
                label("تصفية بالوسم:", 10f, mutedColor, true).apply {
                    setPadding(0, 0, dp(6), 0)
                }
            )
            filterRow.addView(
                action(if (selectedTagId == null) "[الكل]" else "الكل") {
                    selectedTagId = null
                    refreshTagsBar()
                    refreshWorkspacePanel()
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(30)).apply {
                    marginEnd = dp(4)
                }
            )
            allTags.forEach { t ->
                val tId = t["id"] ?: return@forEach
                val tName = t["name"] ?: return@forEach
                val active = selectedTagId == tId
                filterRow.addView(
                    action(if (active) "[$tName]" else "#$tName") {
                        selectedTagId = if (active) null else tId
                        refreshTagsBar()
                        refreshWorkspacePanel()
                    },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(30)).apply {
                        marginEnd = dp(4)
                    }
                )
            }
            filterScroll.addView(filterRow)
            tagsBarContainer.addView(filterScroll)
        }

        val currentSavedId = itemId
        if (isNewItem || currentSavedId.isNullOrBlank()) {
            tagsBarContainer.addView(
                label("احفظ العنصر أولاً لإدارة وسومه المرتبطة.", 10f, mutedColor, false)
            )
            return
        }

        val itemTags = db.getTagsForWorkspaceItem(currentSavedId)
        if (itemTags.isEmpty()) {
            tagsBarContainer.addView(
                label("وسوم العنصر: لا توجد وسوم مرتبطة", 10f, mutedColor, false)
            )
            return
        }

        val attachedRow = horizontalRow()
        attachedRow.addView(
            label("وسوم العنصر (انقر لإزالة):", 10f, accentColor, true).apply {
                setPadding(0, 0, dp(6), 0)
            }
        )
        itemTags.forEach { t ->
            val tId = t["id"] ?: return@forEach
            val tName = t["name"] ?: return@forEach
            attachedRow.addView(
                action("#$tName ×") {
                    if (db.detachTagFromWorkspaceItem(currentSavedId, tId)) {
                        refreshTagsBar()
                        refreshWorkspacePanel()
                        showMessage("تمت إزالة الوسم #$tName")
                    } else {
                        showMessage("تعذرت إزالة الوسم")
                    }
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(30)).apply {
                    marginEnd = dp(4)
                }
            )
        }
        tagsBarContainer.addView(attachedRow)
    }

    private fun promptCreateFolder() {
        val input = EditText(this).apply {
            hint = "اسم المجلد الجديد"
            setTextColor(textColor)
            setHintTextColor(mutedColor)
        }

        AlertDialog.Builder(this)
            .setTitle("إنشاء مجلد جديد")
            .setView(input)
            .setPositiveButton("إنشاء") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) {
                    showMessage("أدخل اسم المجلد أولاً")
                    return@setPositiveButton
                }
                try {
                    val createdFolderId = db.createWorkspaceFolder(
                        name = name,
                        parentId = selectedFolderId
                    )
                    selectedFolderId = createdFolderId
                    syncUiFromState()
                    refreshWorkspacePanel()
                    showMessage("تم إنشاء المجلد: $name")
                } catch (ex: Exception) {
                    showMessage("تعذر إنشاء المجلد: ${ex.message ?: "خطأ"}")
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun promptAssignFolderForCurrentItem() {
        val targetId = itemId
        if (isNewItem || targetId.isNullOrBlank()) {
            showMessage("احفظ العنصر أولاً قبل نقله إلى مجلد")
            return
        }

        val allFolders = db.getAllWorkspaceFolders()
        val options = mutableListOf("بدون مجلد (إزالة التعيين)")
        val folderIds = mutableListOf<String?>(null)

        allFolders.forEach { f ->
            options.add(f["name"] ?: "مجلد")
            folderIds.add(f["id"])
        }

        AlertDialog.Builder(this)
            .setTitle("نقل العنصر إلى مجلد")
            .setItems(options.toTypedArray()) { _, index ->
                val chosenFolderId = folderIds[index]
                val ok = db.assignWorkspaceItemFolder(targetId, chosenFolderId)
                if (ok) {
                    selectedFolderId = chosenFolderId
                    syncUiFromState()
                    refreshWorkspacePanel()
                    showMessage("تم تحديث مجلد العنصر")
                } else {
                    showMessage("تعذر نقل العنصر إلى المجلد المحدد")
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun promptAttachTagToCurrentItem() {
        val targetId = itemId
        if (isNewItem || targetId.isNullOrBlank()) {
            showMessage("احفظ العنصر أولاً قبل ربط وسم به")
            return
        }

        val input = EditText(this).apply {
            hint = "اسم الوسم (مثال: kotlin, db, ui)"
            setTextColor(textColor)
            setHintTextColor(mutedColor)
        }

        AlertDialog.Builder(this)
            .setTitle("إضافة أو ربط وسم")
            .setView(input)
            .setPositiveButton("ربط") { _, _ ->
                val tagName = input.text.toString().trim()
                if (tagName.isEmpty()) {
                    showMessage("أدخل اسم الوسم أولاً")
                    return@setPositiveButton
                }
                try {
                    val tagId = db.upsertWorkspaceTag(name = tagName)
                    val attached = db.attachTagToWorkspaceItem(targetId, tagId)
                    if (attached) {
                        refreshTagsBar()
                        refreshWorkspacePanel()
                        showMessage("تم ربط الوسم #$tagName")
                    } else {
                        showMessage("الوسم مرتبط بالفعل أو تعذر ربطه")
                    }
                } catch (ex: Exception) {
                    showMessage("تعذر إضافة الوسم: ${ex.message ?: "خطأ"}")
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun archiveCurrentItem() {
        val targetId = itemId
        if (isNewItem || targetId.isNullOrBlank()) {
            showMessage("احفظ العنصر أولاً قبل نقله إلى الأرشيف")
            return
        }
        if (db.archiveWorkspaceCodeItem(targetId)) {
            activeSection = AppDatabase.WORKSPACE_STATE_ARCHIVED
            refreshWorkspacePanel()
            showMessage("تم نقل العنصر إلى الأرشيف مع الحفاظ على إصداراته")
        } else {
            showMessage("تعذر نقل العنصر إلى الأرشيف")
        }
    }

    private fun trashCurrentItem() {
        val targetId = itemId
        if (isNewItem || targetId.isNullOrBlank()) {
            showMessage("احفظ العنصر أولاً قبل نقله إلى سلة المحذوفات")
            return
        }
        if (db.moveWorkspaceCodeItemToTrash(targetId)) {
            activeSection = AppDatabase.WORKSPACE_STATE_TRASHED
            refreshWorkspacePanel()
            showMessage("تم نقل العنصر إلى سلة المحذوفات مع الحفاظ على إصداراته")
        } else {
            showMessage("تعذر نقل العنصر إلى سلة المحذوفات")
        }
    }

    private fun confirmPermanentDelete(targetItemId: String, title: String) {
        AlertDialog.Builder(this)
            .setTitle("تأكيد الحذف النهائي")
            .setMessage("سيتم حذف \"$title\" وجميع إصداراته ووسومه نهائياً ولا يمكن التراجع. هل تريد المتابعة؟")
            .setPositiveButton("حذف نهائي") { _, _ ->
                val deleted = db.deleteWorkspaceCodeItemPermanently(targetItemId)
                if (deleted) {
                    if (itemId == targetItemId) {
                        startNewBlankSnippet()
                    }
                    refreshWorkspacePanel()
                    refreshTagsBar()
                    showMessage("تم الحذف النهائي")
                } else {
                    showMessage("تعذر تنفيذ الحذف النهائي")
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun startNewBlankSnippet() {
        itemId = null
        currentVersion = 0
        originalCode = ""
        currentCode = ""
        currentTitle = "مقتطف برمجي"
        currentFileName = null
        isNewItem = true
        isDirty = false
        isReadOnlyVersion = false
        viewedVersionNumber = null
        syncUiFromState()
        refreshTagsBar()
    }

    private fun findFolderName(folderId: String?): String? {
        if (folderId == null) return null
        return db.getAllWorkspaceFolders().firstOrNull { it["id"] == folderId }?.get("name")
    }

    // =========================================================
    // TOOLS & UI HELPERS
    // =========================================================

    private data class Tool(
        val title: String,
        val icon: String
    )

    private fun addToolGrid(
        parent: LinearLayout,
        tools: List<Tool>
    ) {
        tools.chunked(2).forEach { group ->
            val row = horizontalRow()

            group.forEach { tool ->
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    background = roundedBackground()
                    isClickable = true
                    isFocusable = true
                    setPadding(dp(6), dp(9), dp(6), dp(9))

                    setOnClickListener {
                        performTool(tool.icon)
                    }
                }

                card.addView(
                    ToolGlyph(this, tool.icon),
                    LinearLayout.LayoutParams(dp(27), dp(27))
                )

                card.addView(
                    label(tool.title, 11f, textColor, false).apply {
                        gravity = Gravity.CENTER
                        maxLines = 2
                    }
                )

                row.addView(
                    card,
                    LinearLayout.LayoutParams(0, dp(76), 1f).apply {
                        marginEnd = dp(6)
                        bottomMargin = dp(6)
                    }
                )
            }

            if (group.size == 1) {
                row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
            }

            parent.addView(row)
        }
    }

    private fun performTool(name: String) {
        captureEditorIntoState()

        when (name) {
            "copy" -> {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(
                    ClipData.newPlainText("MONOLITH CODE", editor.text.toString())
                )
                showMessage("تم نسخ الكود")
            }

            "edit" -> {
                if (isReadOnlyVersion) {
                    restoreViewedVersionToEditor()
                } else {
                    editor.requestFocus()
                }
            }

            "expand" -> {
                isExpanded = !isExpanded
                syncUiFromState()
                showMessage(
                    if (isExpanded) "تم توسيع مساحة التحرير" else "تمت إعادة مساحة التحرير للوضع الافتراضي"
                )
            }

            "select" -> {
                editor.requestFocus()
                editor.setSelection(0, editor.length())
            }

            "language" -> {
                if (isReadOnlyVersion) {
                    showMessage("النسخة القديمة للقراءة فقط؛ استعدها أولاً لتغيير اللغة")
                } else {
                    chooseLanguage()
                }
            }

            "style" -> chooseFontSize()

            "analyze" -> {
                val codeText = editor.text.toString()
                val lines = codeText.split('\n')
                val nonEmptyLines = lines.count { it.isNotBlank() }
                val chars = codeText.length
                AlertDialog.Builder(this)
                    .setTitle("إحصاءات الكود الحالية")
                    .setMessage(
                        "اللغة: $currentLanguage\n" +
                            "إجمالي الأسطر: ${lines.size}\n" +
                            "الأسطر غير الفارغة: $nonEmptyLines\n" +
                            "عدد الأحرف: $chars"
                    )
                    .setPositiveButton("موافق", null)
                    .show()
            }

            "compare" -> compareVersionsDialog()

            "save" -> saveCurrentState(forceNewVersionWhenSavedItem = false)

            "lines" -> {
                showLineNumbers = !showLineNumbers
                updateLineNumbers()
                showMessage(
                    if (showLineNumbers) "تم إظهار أرقام الأسطر" else "تم إخفاء أرقام الأسطر"
                )
            }

            "explain", "fix", "refactor" -> {
                showMessage("لا توجد خدمة ذكاء اصطناعي مرتبطة بهذا النشاط حالياً")
            }
        }
    }

    private fun chooseLanguage() {
        val languages = arrayOf(
            "Kotlin", "Java", "Python",
            "JavaScript", "TypeScript",
            "C", "C++", "C#", "Rust",
            "Go", "Swift", "HTML",
            "CSS", "XML", "JSON", "SQL", "Text"
        )

        AlertDialog.Builder(this)
            .setTitle("لغة البرمجة")
            .setItems(languages) { _, index ->
                currentLanguage = languages[index]
                languageField.text = currentLanguage
                syncUiFromState()
            }
            .show()
    }

    private fun chooseFontSize() {
        val sizes = arrayOf("12", "13", "14", "15", "16", "18", "20")

        AlertDialog.Builder(this)
            .setTitle("حجم خط الكود")
            .setItems(sizes) { _, index ->
                editorFontSize = sizes[index].toFloat()
                editor.textSize = editorFontSize
                updateLineNumbers()
            }
            .show()
    }

    private fun addManagementButton(
        parent: LinearLayout,
        title: String,
        onClick: () -> Unit
    ) {
        parent.addView(
            action(title, onClick),
            LinearLayout.LayoutParams(-1, dp(46)).apply {
                bottomMargin = dp(6)
            }
        )
    }

    private fun section(title: String) =
        label(title, 14f, textColor, true).apply {
            setPadding(0, dp(17), 0, dp(9))
        }

    private fun label(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean
    ) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)

        if (bold) {
            setTypeface(null, Typeface.BOLD)
        }

        setPadding(dp(3), dp(5), dp(3), dp(5))
    }

    private fun editField(value: String) =
        EditText(this).apply {
            setText(value)
            setTextColor(textColor)
            textSize = 14f
            singleLine = true
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = roundedBackground()
        }

    private fun horizontalRow() =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                -1,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

    private fun action(
        title: String,
        callback: () -> Unit
    ) = TextView(this).apply {
        text = title
        textSize = 11f
        gravity = Gravity.CENTER
        setTextColor(textColor)
        setPadding(dp(8), dp(9), dp(8), dp(9))
        background = roundedBackground()
        isClickable = true
        isFocusable = true
        setOnClickListener { callback() }
    }

    private fun roundedBackground() =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(panelColor)
            cornerRadius = dp(13).toFloat()
            setStroke(dp(1), borderColor)
        }

    private fun weightParams() =
        LinearLayout.LayoutParams(0, -2, 1f)

    private fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    /**
     * Original custom-drawn symbols.
     * No icon font, icon package or external image.
     */
    private inner class ToolGlyph(
        context: Context,
        private val type: String
    ) : View(context) {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            style = Paint.Style.STROKE
            strokeWidth = dp(1.8).toFloat()
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val sx = width / 24f
            val sy = height / 24f

            canvas.save()
            canvas.scale(sx, sy)

            when (type) {
                "copy" -> {
                    canvas.drawRoundRect(RectF(7f, 7f, 19f, 20f), 2f, 2f, paint)
                    canvas.drawRoundRect(RectF(4f, 4f, 16f, 17f), 2f, 2f, paint)
                }

                "edit" -> {
                    canvas.drawLine(4f, 12f, 9f, 7f, paint)
                    canvas.drawLine(4f, 12f, 9f, 17f, paint)
                    canvas.drawLine(20f, 12f, 15f, 7f, paint)
                    canvas.drawLine(20f, 12f, 15f, 17f, paint)
                    canvas.drawLine(13f, 4f, 11f, 20f, paint)
                }

                "analyze" -> {
                    canvas.drawCircle(10f, 10f, 6f, paint)
                    canvas.drawLine(14.5f, 14.5f, 21f, 21f, paint)
                }

                "expand" -> {
                    canvas.drawLine(4f, 9f, 4f, 4f, paint)
                    canvas.drawLine(4f, 4f, 9f, 4f, paint)
                    canvas.drawLine(15f, 4f, 20f, 4f, paint)
                    canvas.drawLine(20f, 4f, 20f, 9f, paint)
                    canvas.drawLine(4f, 15f, 4f, 20f, paint)
                    canvas.drawLine(4f, 20f, 9f, 20f, paint)
                    canvas.drawLine(15f, 20f, 20f, 20f, paint)
                    canvas.drawLine(20f, 20f, 20f, 15f, paint)
                }

                "language" -> {
                    canvas.drawOval(RectF(3f, 4f, 21f, 20f), paint)
                    canvas.drawLine(3f, 12f, 21f, 12f, paint)
                    canvas.drawOval(RectF(8f, 4f, 16f, 20f), paint)
                }

                "style" -> {
                    canvas.drawLine(4f, 5f, 20f, 5f, paint)
                    canvas.drawLine(12f, 5f, 12f, 20f, paint)
                    canvas.drawLine(7f, 20f, 17f, 20f, paint)
                }

                "lines" -> {
                    for (y in listOf(6f, 12f, 18f)) {
                        canvas.drawCircle(4f, y, 1f, paint)
                        canvas.drawLine(9f, y, 21f, y, paint)
                    }
                }

                "select" -> {
                    canvas.drawLine(5f, 4f, 5f, 20f, paint)
                    canvas.drawLine(19f, 4f, 19f, 20f, paint)
                    canvas.drawLine(5f, 8f, 19f, 8f, paint)
                    canvas.drawLine(5f, 16f, 19f, 16f, paint)
                }

                "compare" -> {
                    canvas.drawRect(3f, 5f, 10f, 19f, paint)
                    canvas.drawRect(14f, 5f, 21f, 19f, paint)
                    canvas.drawLine(10f, 12f, 14f, 12f, paint)
                }

                "save" -> {
                    val path = Path().apply {
                        moveTo(6f, 4f)
                        lineTo(18f, 4f)
                        lineTo(18f, 21f)
                        lineTo(12f, 17f)
                        lineTo(6f, 21f)
                        close()
                    }
                    canvas.drawPath(path, paint)
                }

                "explain" -> {
                    canvas.drawCircle(12f, 10f, 8f, paint)
                    canvas.drawLine(12f, 8f, 12f, 12f, paint)
                    canvas.drawCircle(12f, 15.5f, 0.6f, paint)
                }

                "fix" -> {
                    canvas.drawCircle(12f, 12f, 6f, paint)
                    canvas.drawLine(12f, 2f, 12f, 5f, paint)
                    canvas.drawLine(12f, 19f, 12f, 22f, paint)
                    canvas.drawLine(2f, 12f, 5f, 12f, paint)
                    canvas.drawLine(19f, 12f, 22f, 12f, paint)
                }

                "refactor" -> {
                    canvas.drawLine(4f, 6f, 10f, 6f, paint)
                    canvas.drawLine(4f, 12f, 8f, 12f, paint)
                    canvas.drawLine(4f, 18f, 10f, 18f, paint)
                    canvas.drawLine(14f, 4f, 20f, 10f, paint)
                    canvas.drawLine(20f, 10f, 14f, 16f, paint)
                }
            }

            canvas.restore()
        }
    }
}

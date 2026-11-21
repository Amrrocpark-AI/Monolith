
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

class CodeWorkspaceActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CODE = "code_content"
        const val EXTRA_LANGUAGE = "code_language"
        const val EXTRA_TITLE = "code_title"
    }

    private val backgroundColor = Color.rgb(8, 12, 29)
    private val panelColor = Color.rgb(18, 25, 49)
    private val borderColor = Color.rgb(48, 65, 108)
    private val accentColor = Color.rgb(0, 190, 255)
    private val purpleColor = Color.rgb(151, 74, 255)
    private val textColor = Color.rgb(235, 241, 255)
    private val mutedColor = Color.rgb(145, 160, 190)

    private lateinit var editor: EditText
    private lateinit var titleField: EditText
    private lateinit var languageField: TextView

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor

        buildInterface()
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

        root.addView(
            label("CODE WORKSPACE", 10f, accentColor, true)
        )

        root.addView(
            label(
                "مساحة العمل البرمجية",
                13f,
                mutedColor,
                false
            )
        )

        // Navigation
        root.addView(section("مساحة العمل"))

        val navigation = horizontalRow()

        listOf(
            "المحرر",
            "مخزن الأكواد",
            "الأرشيف",
            "المحذوفات"
        ).forEach { name ->
            navigation.addView(
                action(name) {
                    showMessage(
                        "واجهة $name ستُربط بنظام التخزين"
                    )
                },
                LinearLayout.LayoutParams(
                    0,
                    dp(43),
                    1f
                ).apply {
                    marginEnd = dp(4)
                }
            )
        }

        root.addView(navigation)

        // Code information
        root.addView(section("معلومات الكود"))

        titleField = editField(
            intent.getStringExtra(EXTRA_TITLE)
                ?: "مقتطف برمجي"
        )

        root.addView(titleField)

        languageField = label(
            intent.getStringExtra(EXTRA_LANGUAGE)
                ?: "Kotlin",
            14f,
            accentColor,
            true
        ).apply {
            background = roundedBackground()
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener { chooseLanguage() }
        }

        root.addView(section("لغة البرمجة"))
        root.addView(languageField)

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

        editorHeader.addView(
            label("● محفوظ محلياً لاحقاً", 9f, mutedColor, false)
        )

        editorPanel.addView(editorHeader)

        editor = EditText(this).apply {
            setText(intent.getStringExtra(EXTRA_CODE).orEmpty())
            setTextColor(Color.rgb(205, 224, 255))
            setHintTextColor(mutedColor)
            hint = "اكتب الكود البرمجي هنا..."
            textSize = 14f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.TOP or Gravity.START
            minLines = 12
            setPadding(dp(10), dp(12), dp(10), dp(12))
            setBackgroundColor(Color.TRANSPARENT)

            inputType =
                android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS

            isSingleLine = false
        }

        editorPanel.addView(
            editor,
            LinearLayout.LayoutParams(
                -1,
                dp(260)
            )
        )

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

        // AI tools
        root.addView(section("أدوات الذكاء الاصطناعي المرتبطة بالكود"))

        val aiTools = listOf(
            Tool("شرح الكود", "explain"),
            Tool("إصلاح الأخطاء", "fix"),
            Tool("تحسين وإعادة هيكلة الكود", "refactor")
        )

        addToolGrid(root, aiTools)

        // Version management
        root.addView(section("إدارة الكود والإصدارات"))

        addManagementButton(
            root,
            "كشف التعديلات ومقارنة الإصدارات"
        )

        addManagementButton(
            root,
            "تاريخ جميع إصدارات الكود"
        )

        addManagementButton(
            root,
            "حفظ نسخة جديدة"
        )

        addManagementButton(
            root,
            "نقل إلى الأرشيف"
        )

        addManagementButton(
            root,
            "نقل إلى سلة المحذوفات"
        )
    }

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
                    LinearLayout.LayoutParams(
                        dp(27),
                        dp(27)
                    )
                )

                card.addView(
                    label(
                        tool.title,
                        11f,
                        textColor,
                        false
                    ).apply {
                        gravity = Gravity.CENTER
                        maxLines = 2
                    }
                )

                row.addView(
                    card,
                    LinearLayout.LayoutParams(
                        0,
                        dp(76),
                        1f
                    ).apply {
                        marginEnd = dp(6)
                        bottomMargin = dp(6)
                    }
                )
            }

            if (group.size == 1) {
                row.addView(
                    View(this),
                    LinearLayout.LayoutParams(0, 1, 1f)
                )
            }

            parent.addView(row)
        }
    }

    private fun performTool(name: String) {

        when (name) {

            "copy" -> {
                val clipboard =
                    getSystemService(CLIPBOARD_SERVICE)
                        as ClipboardManager

                clipboard.setPrimaryClip(
                    ClipData.newPlainText(
                        "MONOLITH CODE",
                        editor.text.toString()
                    )
                )

                showMessage("تم نسخ الكود")
            }

            "edit" -> editor.requestFocus()

            "expand" -> {
                editor.minLines = 22
                showMessage("تم توسيع مساحة التحرير")
            }

            "select" -> {
                editor.requestFocus()
                editor.setSelection(0, editor.length())
            }

            "language" -> chooseLanguage()

            "style" -> chooseFontSize()

            "analyze" -> showMessage(
                "سيتم ربط محلل الكود في مرحلة التنفيذ"
            )

            "compare" -> showMessage(
                "سيتم ربط محرك مقارنة الإصدارات"
            )

            "save" -> showMessage(
                "سيتم ربط مخزن الأكواد"
            )

            "lines" -> showMessage(
                "سيتم إضافة مسطرة أرقام الأسطر"
            )

            "explain" -> showMessage(
                "إجراء شرح الكود يحتاج ربط محرك الذكاء الاصطناعي"
            )

            "fix" -> showMessage(
                "إجراء إصلاح الأخطاء يحتاج محرك الذكاء الاصطناعي"
            )

            "refactor" -> showMessage(
                "إعادة الهيكلة تحتاج محرك الذكاء الاصطناعي"
            )
        }
    }

    private fun chooseLanguage() {
        val languages = arrayOf(
            "Kotlin", "Java", "Python",
            "JavaScript", "TypeScript",
            "C", "C++", "C#", "Rust",
            "Go", "Swift", "HTML",
            "CSS", "XML", "JSON", "SQL"
        )

        AlertDialog.Builder(this)
            .setTitle("لغة البرمجة")
            .setItems(languages) { _, index ->
                languageField.text = languages[index]
            }
            .show()
    }

    private fun chooseFontSize() {
        val sizes = arrayOf(
            "12", "13", "14", "15",
            "16", "18", "20"
        )

        AlertDialog.Builder(this)
            .setTitle("حجم خط الكود")
            .setItems(sizes) { _, index ->
                editor.textSize = sizes[index].toFloat()
            }
            .show()
    }

    private fun addManagementButton(
        parent: LinearLayout,
        title: String
    ) {
        parent.addView(
            action(title) {
                showMessage(
                    "سيتم ربط $title بنظام إدارة الأكواد"
                )
            },
            LinearLayout.LayoutParams(
                -1,
                dp(46)
            ).apply {
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
        Toast.makeText(
            this,
            message,
            Toast.LENGTH_SHORT
        ).show()
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
                    canvas.drawRoundRect(
                        RectF(7f, 7f, 19f, 20f),
                        2f, 2f, paint
                    )
                    canvas.drawRoundRect(
                        RectF(4f, 4f, 16f, 17f),
                        2f, 2f, paint
                    )
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


package com.monolith

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Switch

object MonolithControlPopup {

    private const val PREFS = "monolith_control_preferences"

    private const val BG = 0xFF111116.toInt()
    private const val PANEL = 0xFF202027.toInt()
    private const val HEADER = 0xFF292932.toInt()
    private const val WHITE = 0xFFF3F3F8.toInt()
    private const val MUTED = 0xFFAAAAB5.toInt()
    private const val PURPLE = 0xFF8754F6.toInt()
    private const val CYAN = 0xFF36DDF5.toInt()
    private const val DIVIDER = 0xFF393943.toInt()

    private data class Item(
        val icon: MonolithIcon,
        val title: String,
        val description: String
    )

    private val items = listOf(
        Item(MonolithIcon.MODEL, "النماذج", "اختيار وإدارة نماذج الذكاء الاصطناعي"),
        Item(MonolithIcon.CORE, "المحرك الأساسي", "حالة المحرك وتشغيله"),
        Item(MonolithIcon.VISION, "الرؤية", "الصور وتحليل الشاشة"),
        Item(MonolithIcon.AUDIO, "الصوت", "الصوت والإخراج والمؤثرات"),
        Item(MonolithIcon.AGENTS, "الوكلاء", "إدارة الوكلاء والمهام"),
        Item(MonolithIcon.MEMORY, "الذاكرة", "الذاكرة والسياق والمعرفة"),
        Item(MonolithIcon.DEVICES, "الأجهزة", "الأجهزة المحلية المتصلة"),
        Item(MonolithIcon.TOOLS, "الأدوات", "أدوات التنفيذ والملفات"),
        Item(MonolithIcon.NETWORK, "الشبكة", "الاتصال المحلي"),
        Item(MonolithIcon.PERFORMANCE, "الأداء", "إعدادات موارد التشغيل"),
        Item(MonolithIcon.DIAGNOSTICS, "التشخيص", "فحص المكونات والأخطاء"),
        Item(MonolithIcon.CONTROL, "تخصيص الواجهة", "المظهر والألوان وسلوك اللوحات")
    )

    fun show(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        showHome(activity)
    }

    private fun showHome(activity: Activity) {
        val dialog = createDialog(activity)
        val root = createPanel(activity)

        addHeader(
            activity,
            root,
            "MONOLITH CONTROL",
            "مركز التحكم المركزي",
            dialog
        )

        val scroll = ScrollView(activity).apply {
            isFillViewport = false
            clipToPadding = false
            setPadding(dp(activity, 15), dp(activity, 14), dp(activity, 15), dp(activity, 20))
        }

        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }

        val intro = TextView(activity).apply {
            text = "اختر النظام الذي تريد التحكم فيه"
            textSize = 13f
            setTextColor(MUTED)
            setPadding(dp(activity, 5), dp(activity, 4), dp(activity, 5), dp(activity, 15))
        }
        content.addView(intro)

        val grid = GridLayout(activity).apply {
            columnCount = 2
            useDefaultMargins = false
        }

        items.forEach { item ->
            val card = createItemCard(activity, item)

            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = dp(activity, 112)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(activity, 5), dp(activity, 5), dp(activity, 5), dp(activity, 5))
            }

            grid.addView(card, params)

            card.setOnClickListener {
                dialog.dismiss()
                showDetails(activity, item)
            }
        }

        content.addView(grid)
        scroll.addView(content)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        dialog.setContentView(root)
        showCentered(dialog, activity, 0.92f, 0.84f)
    }

    private fun showDetails(activity: Activity, item: Item) {
        if (activity.isFinishing || activity.isDestroyed) return

        val dialog = createDialog(activity)
        val root = createPanel(activity)

        addHeader(
            activity,
            root,
            item.title,
            item.description,
            dialog,
            item.icon
        )

        val scroll = ScrollView(activity).apply {
            clipToPadding = false
            setPadding(dp(activity, 22), dp(activity, 18), dp(activity, 22), dp(activity, 24))
        }

        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }

        when (item.icon) {
            MonolithIcon.MODEL -> buildModelPanel(activity, content)
            MonolithIcon.CORE -> buildCorePanel(activity, content)
            MonolithIcon.VISION -> buildVisionPanel(activity, content)
            MonolithIcon.AUDIO -> buildAudioPanel(activity, content)
            MonolithIcon.AGENTS -> buildAgentsPanel(activity, content)
            MonolithIcon.MEMORY -> buildMemoryPanel(activity, content)
            MonolithIcon.DEVICES -> buildDevicesPanel(activity, content)
            MonolithIcon.TOOLS -> buildToolsPanel(activity, content)
            MonolithIcon.NETWORK -> buildNetworkPanel(activity, content)
            MonolithIcon.PERFORMANCE -> buildPerformancePanel(activity, content)
            MonolithIcon.DIAGNOSTICS -> buildDiagnosticsPanel(activity, content)
            MonolithIcon.CONTROL -> buildAppearancePanel(activity, content)
            else -> addInfo(
                activity,
                content,
                "هذه اللوحة مخصصة للتحكم في هذا القسم."
            )
        }

        scroll.addView(content)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        val footer = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(activity, 16), dp(activity, 10), dp(activity, 16), dp(activity, 16))
        }

        val back = makeButton(activity, "العودة إلى لوحة التحكم")
        footer.addView(
            back,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(activity, 48)
            )
        )

        back.setOnClickListener {
            dialog.dismiss()
            showHome(activity)
        }

        root.addView(footer)

        dialog.setContentView(root)
        showCentered(dialog, activity, 0.92f, 0.78f)
    }

    private fun buildModelPanel(context: Context, content: LinearLayout) {
        addSection(context, content, "إدارة النماذج المحلية")

        addInfo(
            context,
            content,
            "هذه اللوحة هي نقطة التحكم المخصصة للنماذج. ربط اختيار ملف النموذج وتحميله مباشرة يحتاج توصيل واجهة اختيار الملفات بمدير النماذج الحالي. لن نعرض نموذجاً على أنه محمّل قبل نجاح التحميل الفعلي."
        )

        addDivider(context, content)

        addInfo(
            context,
            content,
            "الخطوة التالية في ربط هذه اللوحة: استعراض ملفات النماذج، عرض بيانات GGUF، اختيار النموذج، ثم تحميله واختباره وإظهار نتيجة التشغيل الحقيقية."
        )
    }

    private fun buildCorePanel(context: Context, content: LinearLayout) {
        addSection(context, content, "إعدادات المحرك")

        addToggle(context, content, "السماح بالتشغيل المحلي", "local_engine_enabled", true)
        addToggle(context, content, "إظهار تفاصيل المحرك", "show_engine_details", true)

        addInfo(
            context,
            content,
            "هذه المفاتيح تحفظ تفضيلات الواجهة فقط. لا تغيّر دورة حياة محرك Native قبل ربطها بوظائفه الفعلية."
        )
    }

    private fun buildVisionPanel(context: Context, content: LinearLayout) {
        addSection(context, content, "إعدادات الرؤية")

        addToggle(context, content, "تفعيل واجهة الرؤية", "vision_ui_enabled", true)
        addToggle(context, content, "تحليل الصور عند الطلب", "vision_on_demand", true)
        addToggle(context, content, "إظهار معلومات الإطار", "vision_frame_info", false)

        addInfo(
            context,
            content,
            "تفعيل هذه الخيارات لا يعني أن JNI أو نموذج الرؤية جاهز؛ يجب أن ينجح اختبار الرؤية الفعلي أولاً."
        )
    }

    private fun buildAudioPanel(context: Context, content: LinearLayout) {
        addSection(context, content, "الصوت")

        addToggle(context, content, "الصوت", "audio_enabled", true)
        addToggle(context, content, "المؤثرات الصوتية", "sound_fx_enabled", true)
        addToggle(context, content, "الإخراج الصوتي", "tts_enabled", true)

        addSlider(context, content, "مستوى الصوت", "audio_volume", 0, 100, 75)
    }

    private fun buildAgentsPanel(context: Context, content: LinearLayout) {
        addSection(context, content, "إدارة الوكلاء")

        addToggle(context, content, "السماح بتشغيل الوكلاء", "agents_enabled", true)
        addToggle(context, content, "عرض خطوات تنفيذ المهام", "agent_trace_enabled", true)
        addToggle(context, content, "طلب تأكيد قبل تنفيذ الأدوات", "agent_confirm_tools", true)

        addInfo(
            context,
            content,
            "يجب أن تبقى صلاحيات التنفيذ الفعلية مرتبطة بنظام الوكلاء، لا بمجرد تغيير مفاتيح الواجهة."
        )
    }

    private fun buildMemoryPanel(context: Context, content: LinearLayout) {
        addSection(context, content, "الذاكرة")

        addToggle(context, content, "تفعيل حفظ الذاكرة", "memory_enabled", true)
        addToggle(context, content, "عرض حالة الذاكرة", "memory_status_visible", true)
        addToggle(context, content, "استخدام السياق المحفوظ", "memory_context_enabled", true)

        addInfo(
            context,
            content,
            "هذه تفضيلات واجهة إلى حين ربطها بمسار استرجاع الذاكرة داخل سياق النموذج."
        )
    }

    private fun buildDevicesPanel(context: Context, content: LinearLayout) {
        addSection(context, content, "الأجهزة")

        addInfo(
            context,
            content,
            "ستعرض هذه المساحة الأجهزة التي تم اكتشافها والاتصال بها فعلياً، مع نوع الاتصال وحالة كل جهاز."
        )

        addToggle(context, content, "السماح باكتشاف الأجهزة", "device_discovery_enabled", false)
    }

    private fun buildToolsPanel(context: Context, content: LinearLayout) {
        addSection(context, content, "الأدوات")

        addToggle(context, content, "إظهار الأدوات المتاحة", "tools_visible", true)
        addToggle(context, content, "تأكيد العمليات التي تغيّر الملفات", "tools_confirm_changes", true)

        addInfo(
            context,
            content,
            "لن تظهر أداة على أنها جاهزة إلا بعد التأكد من أنها تنفذ العملية فعلياً."
        )
    }

    private fun buildNetworkPanel(context: Context, content: LinearLayout) {
        addSection(context, content, "الاتصال المحلي")

        addToggle(context, content, "السماح بالاتصال المحلي", "local_network_enabled", false)
        addToggle(context, content, "إظهار حالة الاتصال", "network_status_visible", true)

        addInfo(
            context,
            content,
            "لا يوجد مزود سحابي مفروض هنا. أي اتصال خارجي يجب أن يختاره المستخدم ويضبطه بنفسه."
        )
    }

    private fun buildPerformancePanel(context: Context, content: LinearLayout) {
        addSection(context, content, "الأداء")

        addSlider(context, content, "عدد خيوط المعالجة المفضل", "preferred_threads", 1, 12, 4)
        addSlider(context, content, "حد الذاكرة المؤقتة", "cache_limit", 256, 4096, 1024)

        addInfo(
            context,
            content,
            "هذه قيم تفضيل محفوظة في الواجهة، ولا تُطبّق على المحرك حتى ربطها بإعدادات التشغيل والتحقق من حدود الجهاز."
        )
    }

    private fun buildDiagnosticsPanel(context: Context, content: LinearLayout) {
        addSection(context, content, "التشخيص")

        addToggle(context, content, "إظهار السجل التقني", "diagnostics_log_visible", true)
        addToggle(context, content, "إظهار تفاصيل الأخطاء", "diagnostics_details", true)

        addInfo(
            context,
            content,
            "ستضاف هنا اختبارات تحميل النموذج والتوليد وJNI والرؤية والذاكرة، مع نتيجة نجاح أو فشل لكل اختبار."
        )
    }

    private fun buildAppearancePanel(context: Context, content: LinearLayout) {
        addSection(context, content, "المظهر")

        addToggle(context, content, "التوهج البنفسجي", "purple_glow_enabled", true)
        addToggle(context, content, "التوهج السماوي", "cyan_glow_enabled", true)
        addToggle(context, content, "تعتيم الخلفية عند فتح اللوحات", "popup_dim_enabled", true)

        addSlider(context, content, "قوة التعتيم", "popup_dim_level", 20, 90, 76)
    }

    private fun addHeader(
        context: Context,
        root: LinearLayout,
        title: String,
        subtitle: String,
        dialog: Dialog,
        icon: MonolithIcon = MonolithIcon.CONTROL
    ) {
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 18), dp(context, 15), dp(context, 14), dp(context, 15))
            setBackgroundColor(HEADER)
        }

        val iconView = MonolithIconView(context, icon, CYAN)
        header.addView(
            iconView,
            LinearLayout.LayoutParams(dp(context, 48), dp(context, 48))
        )

        val texts = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 13), 0, dp(context, 8), 0)
        }

        val titleView = TextView(context).apply {
            text = title
            textSize = 17f
            setTextColor(WHITE)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
        }

        val subtitleView = TextView(context).apply {
            text = subtitle
            textSize = 11f
            setTextColor(MUTED)
            setPadding(0, dp(context, 4), 0, 0)
        }

        texts.addView(titleView)
        texts.addView(subtitleView)

        header.addView(
            texts,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        val close = MonolithIconView(context, MonolithIcon.CLOSE, MUTED)
        close.contentDescription = "إغلاق"
        close.setOnClickListener { dialog.dismiss() }

        header.addView(
            close,
            LinearLayout.LayoutParams(dp(context, 42), dp(context, 42))
        )

        root.addView(header)
        addDivider(context, root)
    }

    private fun createItemCard(context: Context, item: Item): LinearLayout {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 8))
            background = rounded(PANEL, 15f, DIVIDER, dp(context, 1))
            isClickable = true
            isFocusable = true
        }

        val icon = MonolithIconView(context, item.icon, CYAN)

        card.addView(
            icon,
            LinearLayout.LayoutParams(dp(context, 48), dp(context, 48))
        )

        val title = TextView(context).apply {
            text = item.title
            textSize = 13f
            setTextColor(WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, dp(context, 6), 0, 0)
        }

        card.addView(title)

        val description = TextView(context).apply {
            text = item.description
            textSize = 9f
            setTextColor(MUTED)
            gravity = Gravity.CENTER
            maxLines = 2
            setPadding(0, dp(context, 3), 0, 0)
        }

        card.addView(description)

        return card
    }

    private fun addSection(
        context: Context,
        parent: LinearLayout,
        title: String
    ) {
        val text = TextView(context).apply {
            this.text = title
            textSize = 15f
            setTextColor(CYAN)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(context, 5), 0, dp(context, 10))
        }
        parent.addView(text)
    }

    private fun addInfo(
        context: Context,
        parent: LinearLayout,
        message: String
    ) {
        val text = TextView(context).apply {
            this.text = message
            textSize = 12f
            setTextColor(MUTED)
            setLineSpacing(dp(context, 3).toFloat(), 1f)
            setPadding(dp(context, 12), dp(context, 12), dp(context, 12), dp(context, 12))
            background = rounded(0xFF191920.toInt(), 10f)
        }

        parent.addView(
            text,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(context, 12)
            }
        )
    }

    private fun addToggle(
        context: Context,
        parent: LinearLayout,
        title: String,
        key: String,
        defaultValue: Boolean
    ) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(context, 9), 0, dp(context, 9))
        }

        val label = TextView(context).apply {
            text = title
            textSize = 13f
            setTextColor(WHITE)
        }

        row.addView(
            label,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        val toggle = Switch(context).apply {
            isChecked = prefs.getBoolean(key, defaultValue)
        }

        toggle.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(key, checked).apply()
        }

        row.addView(toggle)
        parent.addView(row)
        addDivider(context, parent)
    }

    private fun addSlider(
        context: Context,
        parent: LinearLayout,
        title: String,
        key: String,
        min: Int,
        max: Int,
        defaultValue: Int
    ) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = prefs.getInt(key, defaultValue).coerceIn(min, max)

        val wrapper = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(context, 12), 0, dp(context, 12))
        }

        val labelRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val label = TextView(context).apply {
            text = title
            textSize = 13f
            setTextColor(WHITE)
        }

        val value = TextView(context).apply {
            text = saved.toString()
            textSize = 13f
            setTextColor(CYAN)
        }

        labelRow.addView(
            label,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        labelRow.addView(value)

        wrapper.addView(labelRow)

        val seek = SeekBar(context).apply {
            this.max = max - min
            progress = saved - min
            progressTintList = android.content.res.ColorStateList.valueOf(PURPLE)
            thumbTintList = android.content.res.ColorStateList.valueOf(CYAN)
        }

        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(
                seekBar: SeekBar?,
                progress: Int,
                fromUser: Boolean
            ) {
                val actual = min + progress
                value.text = actual.toString()

                if (fromUser) {
                    prefs.edit().putInt(key, actual).apply()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        wrapper.addView(seek)
        parent.addView(wrapper)
        addDivider(context, parent)
    }

    private fun addDivider(context: Context, parent: LinearLayout) {
        val line = View(context).apply {
            setBackgroundColor(DIVIDER)
        }

        parent.addView(
            line,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(context, 1)
            )
        )
    }

    private fun makeButton(context: Context, title: String): TextView {
        return TextView(context).apply {
            text = title
            gravity = Gravity.CENTER
            textSize = 13f
            setTextColor(Color.WHITE)
            background = rounded(PURPLE, 13f)
            isClickable = true
            isFocusable = true
        }
    }

    private fun createPanel(context: Context): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(BG, 23f)
            clipToOutline = true
        }
    }

    private fun createDialog(context: Context): Dialog {
        return Dialog(context).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setCancelable(true)
            setCanceledOnTouchOutside(true)
        }
    }

    private fun showCentered(
        dialog: Dialog,
        activity: Activity,
        widthPercent: Float,
        heightPercent: Float
    ) {
        dialog.show()

        val metrics = activity.resources.displayMetrics
        val width = (metrics.widthPixels * widthPercent).toInt()
        val maxHeight = (metrics.heightPixels * heightPercent).toInt()

        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setDimAmount(0.78f)
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setLayout(width, maxHeight)
            setGravity(Gravity.CENTER)
        }
    }

    private fun rounded(
        color: Int,
        radius: Float,
        strokeColor: Int? = null,
        strokeWidth: Int = 0
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius

            if (strokeColor != null && strokeWidth > 0) {
                setStroke(strokeWidth, strokeColor)
            }
        }
    }

    private fun dp(context: Context, value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }
}

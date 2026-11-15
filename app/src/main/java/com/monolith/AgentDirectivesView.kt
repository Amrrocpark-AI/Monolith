package com.monolith

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

class AgentDirectivesView(context: Context) : LinearLayout(context) {

    // تعريفات الخيارات
    private val OPTION_AUTO = 0
    private val OPTION_ASK = 1
    private val OPTION_DENY = 2

    // حالة الأذونات الحالية (يمكن حفظها في SharedPreferences لاحقاً)
    var readPermission = OPTION_AUTO
    var interactPermission = OPTION_ASK
    var sensitivePermission = OPTION_DENY

    init {
        this.orientation = VERTICAL
        this.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
        this.setBackgroundColor(Color.parseColor("#1A1A1A")) // خلفية داكنة
        
        buildUI()
    }

    private fun buildUI() {
        // العنوان الرئيسي
        val title = TextView(context).apply {
            text = "المبادئ التوجيهية للوكيل"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dpToPx(16))
        }
        addView(title)

        // 1. فئة القراءة (Read)
        addDirectiveRow(
            "قراءة الشاشة (الوعي البصري)",
            readPermission,
            { newOption -> readPermission = newOption }
        )

        // 2. فئة التفاعل (Interaction)
        addDirectiveRow(
            "النقر والتفاعل (الأيادي)",
            interactPermission,
            { newOption -> interactPermission = newOption }
        )

        // 3. فئة الإجراءات الحساسة (Sensitive)
        addDirectiveRow(
            "إجراءات حساسة (إعدادات، حذف)",
            sensitivePermission,
            { newOption -> sensitivePermission = newOption }
        )
    }

    private fun addDirectiveRow(label: String, currentOption: Int, onOptionChanged: (Int) -> Unit) {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dpToPx(8), 0, dpToPx(8))
        }

        // النص
        val textView = TextView(context).apply {
            text = label
            setTextColor(Color.parseColor("#CCFFFFFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(textView)

        // الحاوية الزجاجية للأزرار
        val segmentedContainer = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setBackgroundResource(R.drawable.bg_glass_inactive)
            setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
        }

        // إنشاء الأزرار الثلاثة
        val btnAuto = createSegmentButton("تلقائي", currentOption == OPTION_AUTO, R.drawable.bg_glow_auto, Color.parseColor("#00FF00"))
        val btnAsk = createSegmentButton("يسأل", currentOption == OPTION_ASK, R.drawable.bg_glow_ask, Color.parseColor("#FFAA00"))
        val btnDeny = createSegmentButton("مرفوض", currentOption == OPTION_DENY, R.drawable.bg_glow_deny, Color.parseColor("#FF0000"))

        // إضافة منطق التبديل
        btnAuto.setOnClickListener { 
            onOptionChanged(OPTION_AUTO)
            refreshButtons(segmentedContainer, OPTION_AUTO)
        }
        btnAsk.setOnClickListener { 
            onOptionChanged(OPTION_ASK)
            refreshButtons(segmentedContainer, OPTION_ASK)
        }
        btnDeny.setOnClickListener { 
            onOptionChanged(OPTION_DENY)
            refreshButtons(segmentedContainer, OPTION_DENY)
        }

        segmentedContainer.addView(btnAuto)
        segmentedContainer.addView(btnAsk)
        segmentedContainer.addView(btnDeny)
        row.addView(segmentedContainer)
        
        addView(row)
    }

    private fun createSegmentButton(text: String, isSelected: Boolean, selectedBgRes: Int, selectedTextColor: Int): TextView {
        return TextView(context).apply {
            this.text = text
            setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            if (isSelected) {
                setBackgroundResource(selectedBgRes)
                setTextColor(selectedTextColor)
                setTypeface(null, android.graphics.Typeface.BOLD)
            } else {
                setBackgroundColor(Color.TRANSPARENT)
                setTextColor(Color.parseColor("#88FFFFFF"))
                setTypeface(null, android.graphics.Typeface.NORMAL)
            }
        }
    }

    private fun refreshButtons(container: LinearLayout, selectedOption: Int) {
        // تحديث ألوان الأزرار عند التبديل
        for (i in 0 until container.childCount) {
            val btn = container.getChildAt(i) as TextView
            val option = when(i) {
                0 -> OPTION_AUTO
                1 -> OPTION_ASK
                else -> OPTION_DENY
            }
            
            if (option == selectedOption) {
                when(option) {
                    OPTION_AUTO -> { btn.setBackgroundResource(R.drawable.bg_glow_auto); btn.setTextColor(Color.parseColor("#00FF00")) }
                    OPTION_ASK -> { btn.setBackgroundResource(R.drawable.bg_glow_ask); btn.setTextColor(Color.parseColor("#FFAA00")) }
                    OPTION_DENY -> { btn.setBackgroundResource(R.drawable.bg_glow_deny); btn.setTextColor(Color.parseColor("#FF0000")) }
                }
                btn.setTypeface(null, android.graphics.Typeface.BOLD)
            } else {
                btn.setBackgroundColor(Color.TRANSPARENT)
                btn.setTextColor(Color.parseColor("#88FFFFFF"))
                btn.setTypeface(null, android.graphics.Typeface.NORMAL)
            }
        }
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}

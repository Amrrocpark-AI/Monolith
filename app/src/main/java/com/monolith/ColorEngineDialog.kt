package com.monolith

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

// كلاس لتخزين إعدادات كل لون
data class ColorRule(val color: Int, val isActive: Boolean, val tolerance: Int)

class ColorEngineDialog : BottomSheetDialogFragment() {

    var onColorsSelected: ((List<ColorRule>) -> Unit)? = null

    // قائمة الألوان السبعة
    private val colorsList = listOf(
        Pair("أسود", Color.parseColor("#000000")),
        Pair("بنفسجي", Color.parseColor("#800080")),
        Pair("أزرق", Color.parseColor("#0000FF")),
        Pair("أخضر", Color.parseColor("#00FF00")),
        Pair("أصفر", Color.parseColor("#FFFF00")),
        Pair("برتقالي", Color.parseColor("#FFA500")),
        Pair("أحمر", Color.parseColor("#FF0000"))
    )

    private val currentRules = mutableMapOf<Int, ColorRule>()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.layoutParams?.height = ViewGroup.LayoutParams.MATCH_PARENT
        }
        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(32, 32, 32, 32)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val title = TextView(requireContext()).apply {
            text = "محرك الألوان"
            setTextColor(Color.parseColor("#00D4FF"))
            textSize = 22f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        root.addView(title)

        val scrollView = ScrollView(requireContext())
        val listLayout = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        scrollView.addView(listLayout)
        root.addView(scrollView)

        // إنشاء صف لكل لون
        for ((name, colorValue) in colorsList) {
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 16, 0, 16)
            }

            val topRow = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            // دائرة اللون
            val colorCircle = View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(48, 48)
                val bg = android.graphics.drawable.GradientDrawable()
                bg.shape = android.graphics.drawable.GradientDrawable.OVAL
                bg.setColor(colorValue)
                bg.setStroke(2, Color.WHITE)
                background = bg
            }

            val textName = TextView(requireContext()).apply {
                text = name
                setTextColor(Color.WHITE)
                textSize = 16f
                setPadding(24, 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            // مفتاح التبديل (سماوي عند التفعيل)
            val switch = SwitchCompat(requireContext()).apply {
                isChecked = false
                thumbTintList = android.content.res.ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(Color.parseColor("#00D4FF"), Color.WHITE)
                )
                trackTintList = android.content.res.ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(Color.parseColor("#8000D4FF"), Color.parseColor("#333333"))
                )
            }

            // شريط التسامح (يظهر فقط عند التفعيل)
            val seekBar = SeekBar(requireContext()).apply {
                max = 100
                progress = 30
                visibility = View.GONE
                progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#00D4FF"))
                thumbTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#00D4FF"))
            }

            switch.setOnCheckedChangeListener { _, isChecked ->
                seekBar.visibility = if (isChecked) View.VISIBLE else View.GONE
                currentRules[colorValue] = ColorRule(colorValue, isChecked, seekBar.progress)
            }

            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (switch.isChecked) {
                        currentRules[colorValue] = ColorRule(colorValue, true, progress)
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })

            topRow.addView(colorCircle)
            topRow.addView(textName)
            topRow.addView(switch)

            row.addView(topRow)
            row.addView(seekBar)
            listLayout.addView(row)
        }

        // زر التطبيق
        val btnApply = Button(requireContext()).apply {
            text = "تطبيق الألوان"
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.parseColor("#00D4FF"))
            setOnClickListener {
                onColorsSelected?.invoke(currentRules.values.toList())
                dismiss()
            }
        }
        root.addView(btnApply)

        return root
    }
}

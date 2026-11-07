package com.monolith

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class ColorEngineDialog : BottomSheetDialogFragment() {

    var onColorsSelected: ((List<ColorRule>) -> Unit)? = null

    private val colorsList = mutableListOf(
        Pair("أسود", Color.parseColor("#000000")),
        Pair("بنفسجي", Color.parseColor("#800080")),
        Pair("أزرق", Color.parseColor("#0000FF")),
        Pair("أخضر", Color.parseColor("#00FF00")),
        Pair("أصفر", Color.parseColor("#FFFF00")),
        Pair("برتقالي", Color.parseColor("#FFA500")),
        Pair("أحمر", Color.parseColor("#FF0000")),
        Pair("أبيض", Color.parseColor("#FFFFFF"))
    )

    private val currentRules = mutableMapOf<Int, ColorRule>()
    private lateinit var listLayout: LinearLayout

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
            text = "محرك الألوان الجراحي"
            setTextColor(Color.parseColor("#00D4FF"))
            textSize = 22f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        root.addView(title)

        val addColorRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 16)
        }
        val hexInput = EditText(requireContext()).apply {
            hint = "إضافة لون (#RRGGBB)"
            setTextColor(Color.WHITE)
            hintTextColor = Color.GRAY
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnAdd = Button(requireContext()).apply {
            text = "إضافة"
            setOnClickListener {
                val hex = hexInput.text.toString().trim()
                try {
                    val color = Color.parseColor(hex)
                    if (colorsList.none { it.second == color }) {
                        colorsList.add(Pair("مخصص", color))
                        rebuildList()
                        hexInput.text.clear()
                    } else {
                        Toast.makeText(requireContext(), "اللون موجود بالفعل", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "كود لون غير صحيح", Toast.LENGTH_SHORT).show()
                }
            }
        }
        addColorRow.addView(hexInput)
        addColorRow.addView(btnAdd)
        root.addView(addColorRow)

        val scrollView = ScrollView(requireContext())
        listLayout = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        scrollView.addView(listLayout)
        root.addView(scrollView)

        rebuildList()

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

    private fun rebuildList() {
        listLayout.removeAllViews()
        for ((name, colorValue) in colorsList) {
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 16, 0, 16)
            }

            val topRow = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

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

            val switch = SwitchCompat(requireContext()).apply {
                isChecked = currentRules[colorValue]?.isActive ?: false
                thumbTintList = android.content.res.ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(Color.parseColor("#00D4FF"), Color.WHITE)
                )
                trackTintList = android.content.res.ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(Color.parseColor("#8000D4FF"), Color.parseColor("#333333"))
                )
            }

            val seekBar = SeekBar(requireContext()).apply {
                max = 100
                progress = currentRules[colorValue]?.tolerance ?: 30
                visibility = if (switch.isChecked) View.VISIBLE else View.GONE
                progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#00D4FF"))
                thumbTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#00D4FF"))
            }

            val recolorSwitch = SwitchCompat(requireContext()).apply {
                text = "تلوين بدلاً من المسح"
                setTextColor(Color.LTGRAY)
                textSize = 12f
                isChecked = currentRules[colorValue]?.isRecolor ?: false
                visibility = if (switch.isChecked) View.VISIBLE else View.GONE
            }

            val btnTargetColor = Button(requireContext()).apply {
                text = "اختر اللون البديل"
                textSize = 12f
                visibility = if (switch.isChecked && recolorSwitch.isChecked) View.VISIBLE else View.GONE
                setOnClickListener {
                    val input = EditText(requireContext())
                    input.setText(String.format("#%06X", 0xFFFFFF and (currentRules[colorValue]?.targetColor ?: Color.RED)))
                    AlertDialog.Builder(requireContext())
                        .setTitle("اللون البديل")
                        .setView(input)
                        .setPositiveButton("حفظ") { _, _ ->
                            try {
                                val target = Color.parseColor(input.text.toString())
                                currentRules[colorValue] = ColorRule(colorValue, true, seekBar.progress, true, target)
                            } catch (e: Exception) {}
                        }
                        .setNegativeButton("إلغاء", null)
                        .show()
                }
            }

            switch.setOnCheckedChangeListener { _, isChecked ->
                seekBar.visibility = if (isChecked) View.VISIBLE else View.GONE
                recolorSwitch.visibility = if (isChecked) View.VISIBLE else View.GONE
                btnTargetColor.visibility = if (isChecked && recolorSwitch.isChecked) View.VISIBLE else View.GONE
                if (isChecked) {
                    currentRules[colorValue] = ColorRule(colorValue, true, seekBar.progress, recolorSwitch.isChecked, currentRules[colorValue]?.targetColor ?: Color.RED)
                } else {
                    currentRules.remove(colorValue)
                }
            }

            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (switch.isChecked) {
                        currentRules[colorValue] = ColorRule(colorValue, true, progress, recolorSwitch.isChecked, currentRules[colorValue]?.targetColor ?: Color.RED)
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })

            recolorSwitch.setOnCheckedChangeListener { _, isChecked ->
                btnTargetColor.visibility = if (isChecked) View.VISIBLE else View.GONE
                if (switch.isChecked) {
                    currentRules[colorValue] = ColorRule(colorValue, true, seekBar.progress, isChecked, currentRules[colorValue]?.targetColor ?: Color.RED)
                }
            }

            topRow.addView(colorCircle)
            topRow.addView(textName)
            topRow.addView(switch)

            row.addView(topRow)
            row.addView(seekBar)
            row.addView(recolorSwitch)
            row.addView(btnTargetColor)
            listLayout.addView(row)
        }
    }
}

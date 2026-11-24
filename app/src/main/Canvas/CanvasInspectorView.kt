package com.monolith.canvas

import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class CanvasInspectorView(
    context: Context,
    private val onSave: (String, String) -> Unit,
    private val onDelete: () -> Unit
) : LinearLayout(context) {

    private val titleInput = EditText(context)
    private val contentInput = EditText(context)

    private val saveButton = Button(context)
    private val deleteButton = Button(context)

    init {
        orientation = VERTICAL
        setPadding(24, 24, 24, 24)
        setBackgroundColor(Color.rgb(28, 30, 40))

        addView(createLabel("تخصيص البطاقة"))

        titleInput.hint = "عنوان البطاقة"
        titleInput.setTextColor(Color.WHITE)
        titleInput.setHintTextColor(Color.LTGRAY)

        addView(
            titleInput,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
        )

        contentInput.hint = "محتوى البطاقة"
        contentInput.setTextColor(Color.WHITE)
        contentInput.setHintTextColor(Color.LTGRAY)
        contentInput.minLines = 4
        contentInput.gravity = android.view.Gravity.TOP

        addView(
            contentInput,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
        )

        saveButton.text = "حفظ التعديلات"
        saveButton.setOnClickListener {
            onSave(
                titleInput.text.toString(),
                contentInput.text.toString()
            )
        }

        addView(
            saveButton,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
        )

        deleteButton.text = "حذف البطاقة"
        deleteButton.setOnClickListener {
            onDelete()
        }

        addView(
            deleteButton,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
        )
    }

    private fun createLabel(value: String): TextView {
        return TextView(context).apply {
            text = value
            textSize = 20f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 20)
        }
    }

    fun showCard(model: CanvasCardModel) {
        titleInput.setText(model.title)
        contentInput.setText(model.content)
    }

    fun clearInspector() {
        titleInput.text.clear()
        contentInput.text.clear()
    }
}

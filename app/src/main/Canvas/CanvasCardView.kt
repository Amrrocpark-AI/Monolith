package com.monolith.canvas

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View

class CanvasCardView(
    context: Context,
    var model: CanvasCardModel
) : View(context) {

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val contentPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val cardRect = RectF()

    init {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        configurePaints()
    }

    private fun configurePaints() {
        backgroundPaint.color = model.color
        backgroundPaint.style = Paint.Style.FILL

        titlePaint.color = 0xFFFFFFFF.toInt()
        titlePaint.textSize = 42f
        titlePaint.typeface = Typeface.create(
            Typeface.DEFAULT,
            Typeface.BOLD
        )

        contentPaint.color = 0xFFE1E1E1.toInt()
        contentPaint.textSize = 30f

        borderPaint.color = 0xFF6C8CFF.toInt()
        borderPaint.strokeWidth = 3f
        borderPaint.style = Paint.Style.STROKE
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val padding = 8f

        cardRect.set(
            padding,
            padding,
            width - padding,
            height - padding
        )

        backgroundPaint.color = model.color

        canvas.drawRoundRect(
            cardRect,
            24f,
            24f,
            backgroundPaint
        )

        canvas.drawRoundRect(
            cardRect,
            24f,
            24f,
            borderPaint
        )

        val textX = 28f
        var textY = 65f

        canvas.drawText(
            model.title,
            textX,
            textY,
            titlePaint
        )

        textY += 55f

        val words = model.content.split(" ")
        val maxWidth = width - 56f
        var line = ""

        for (word in words) {
            val testLine = if (line.isEmpty()) {
                word
            } else {
                "$line $word"
            }

            if (contentPaint.measureText(testLine) > maxWidth) {
                canvas.drawText(
                    line,
                    textX,
                    textY,
                    contentPaint
                )

                line = word
                textY += 42f
            } else {
                line = testLine
            }
        }

        if (line.isNotEmpty()) {
            canvas.drawText(
                line,
                textX,
                textY,
                contentPaint
            )
        }
    }

    fun updateCard(newModel: CanvasCardModel) {
        model = newModel
        invalidate()
    }
}

package com.monolith.canvas

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import kotlin.math.max

class CanvasWorkspaceView(
    context: Context
) : FrameLayout(context) {

    private val cards = mutableListOf<CanvasCardView>()

    private val wirePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(100, 160, 255)
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }

    private var selectedCard: CanvasCardView? = null
    private var dragOffsetX = 0f
    private var dragOffsetY = 0f

    init {
        setBackgroundColor(Color.rgb(17, 19, 29))
        clipChildren = false
        clipToPadding = false
    }

    fun addCard(model: CanvasCardModel) {
        val card = CanvasCardView(context, model)

        val params = LayoutParams(
            560,
            300
        )

        params.leftMargin = 60 + cards.size * 40
        params.topMargin = 80 + cards.size * 50

        addView(card, params)
        cards.add(card)

        card.setOnTouchListener { view, event ->
            val currentCard = view as CanvasCardView

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    selectedCard = currentCard

                    dragOffsetX = event.rawX - currentCard.x
                    dragOffsetY = event.rawY - currentCard.y

                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    currentCard.x = max(
                        0f,
                        event.rawX - dragOffsetX
                    )

                    currentCard.y = max(
                        0f,
                        event.rawY - dragOffsetY
                    )

                    invalidate()
                    true
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    true
                }

                else -> false
            }
        }

        invalidate()
    }

    fun removeCard(card: CanvasCardView) {
        removeView(card)
        cards.remove(card)

        if (selectedCard == card) {
            selectedCard = null
        }

        invalidate()
    }

    fun getCards(): List<CanvasCardView> {
        return cards.toList()
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        drawWires(canvas)
    }

    private fun drawWires(canvas: Canvas) {
        if (cards.size < 2) return

        for (i in 0 until cards.size - 1) {
            val first = cards[i]
            val second = cards[i + 1]

            val startX = first.x + first.width
            val startY = first.y + first.height / 2f

            val endX = second.x
            val endY = second.y + second.height / 2f

            val path = Path().apply {
                moveTo(startX, startY)

                cubicTo(
                    startX + 100f,
                    startY,
                    endX - 100f,
                    endY,
                    endX,
                    endY
                )
            }

            canvas.drawPath(path, wirePaint)
        }
    }
}

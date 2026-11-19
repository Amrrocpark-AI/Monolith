
package com.monolith.graph.ports.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.monolith.graph.ports.models.*

class PortView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var portId: String = ""
        set(value) {
            field = value
            contentDescription = "منفذ اتصال $value"
        }

    var portSide = PortSide.RIGHT
        set(value) {
            field = value
            invalidate()
        }

    var portKind = PortKind.CONTEXT
        set(value) {
            field = value
            invalidate()
        }

    var shape = PortShape.USB_C
        set(value) {
            field = value
            invalidate()
        }

    var state = PortState.IDLE
        set(value) {
            if (field == value) return
            field = value
            updateAnimation()
            invalidate()
        }

    var onPortClick: ((PortView) -> Unit)? = null

    private val density = resources.displayMetrics.density

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val slotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val body = RectF()
    private val slot = RectF()

    private var pulse = 0f
    private var animator: ValueAnimator? = null

    private fun dp(value: Float): Float = value * density

    private val accent: Int
        get() = PortPalette.colorFor(portKind)

    init {
        isClickable = true
        isFocusable = true

        minimumWidth = dp(30f).toInt()
        minimumHeight = dp(20f).toInt()

        setLayerType(LAYER_TYPE_SOFTWARE, null)

        setOnClickListener {
            if (isEnabled && state != PortState.DISABLED) {
                onPortClick?.invoke(this)
            }
        }
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int
    ) {
        val horizontal =
            portSide == PortSide.LEFT ||
            portSide == PortSide.RIGHT

        val desiredWidth = dp(if (horizontal) 34f else 20f).toInt()
        val desiredHeight = dp(if (horizontal) 20f else 34f).toInt()

        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val horizontal =
            portSide == PortSide.LEFT ||
            portSide == PortSide.RIGHT

        val save = canvas.save()

        if (!horizontal) {
            canvas.rotate(90f, width / 2f, height / 2f)
        }

        val cx = width / 2f
        val cy = height / 2f

        val bw = minOf(width * 0.88f, dp(29f))
        val bh = minOf(height * 0.82f, dp(15f))

        body.set(
            cx - bw / 2f,
            cy - bh / 2f,
            cx + bw / 2f,
            cy + bh / 2f
        )

        val color = when (state) {
            PortState.IDLE -> 0xFF52627A.toInt()
            PortState.AVAILABLE -> accent
            PortState.SELECTED -> Color.WHITE
            PortState.CONNECTED -> accent
            PortState.ACTIVE -> accent
            PortState.DISABLED -> 0xFF414751.toInt()
        }

        if (
            state == PortState.ACTIVE ||
            state == PortState.SELECTED ||
            state == PortState.CONNECTED
        ) {
            glowPaint.color = color
            glowPaint.style = Paint.Style.STROKE
            glowPaint.strokeWidth = dp(3f + pulse * 2f)
            glowPaint.alpha = (65 + pulse * 100).toInt()

            canvas.drawRoundRect(
                body,
                dp(5f),
                dp(5f),
                glowPaint
            )
        }

        bodyPaint.style = Paint.Style.FILL
        bodyPaint.color =
            if (state == PortState.DISABLED) {
                0xFF252B35.toInt()
            } else {
                0xFF111927.toInt()
            }

        canvas.drawRoundRect(body, dp(4f), dp(4f), bodyPaint)

        borderPaint.style = Paint.Style.STROKE
        borderPaint.strokeWidth = dp(1.3f)
        borderPaint.color = color
        borderPaint.alpha = if (state == PortState.IDLE) 145 else 255

        canvas.drawRoundRect(body, dp(4f), dp(4f), borderPaint)

        val slotWidth =
            bw * if (shape == PortShape.COMPACT) 0.42f else 0.56f
        val slotHeight = bh * 0.38f

        slot.set(
            cx - slotWidth / 2f,
            cy - slotHeight / 2f,
            cx + slotWidth / 2f,
            cy + slotHeight / 2f
        )

        slotPaint.style = Paint.Style.FILL
        slotPaint.color = 0xFF030811.toInt()

        canvas.drawRoundRect(slot, dp(2f), dp(2f), slotPaint)

        detailPaint.color = color
        detailPaint.alpha = if (state == PortState.IDLE) 105 else 240

        canvas.drawRoundRect(slot, dp(2f), dp(2f), detailPaint)

        detailPaint.color = Color.WHITE
        detailPaint.alpha = if (state == PortState.ACTIVE) 240 else 110

        canvas.drawRect(
            cx - dp(3f),
            cy - dp(0.5f),
            cx + dp(3f),
            cy + dp(0.5f),
            detailPaint
        )

        canvas.restoreToCount(save)
    }

    private fun updateAnimation() {
        animator?.cancel()
        animator = null

        if (
            state == PortState.ACTIVE ||
            state == PortState.SELECTED
        ) {
            if (!isAttachedToWindow) return

            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 950L
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = LinearInterpolator()

                addUpdateListener {
                    pulse = it.animatedValue as Float
                    invalidate()
                }

                start()
            }
        } else {
            pulse = 0f
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateAnimation()
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }
}

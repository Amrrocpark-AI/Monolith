
package com.monolith.graph.ports

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.min

class PortView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var portId: String = ""
        set(value) {
            field = value
            contentDescription = "منفذ اتصال $value"
        }

    var portSide: PortSide = PortSide.RIGHT
        set(value) {
            field = value
            invalidate()
        }

    var portKind: PortKind = PortKind.CONTEXT
        set(value) {
            field = value
            invalidate()
        }

    var state: PortState = PortState.IDLE
        set(value) {
            field = value
            updateAnimation()
            invalidate()
        }

    var onPortClick: ((PortView) -> Unit)? = null

    private val density = resources.displayMetrics.density

    private val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val bodyRect = RectF()
    private val slotRect = RectF()

    private var pulse = 0f

    private var animator: ValueAnimator? = null

    private val accent: Int
        get() = PortPalette.colorFor(portKind)

    init {
        isClickable = true
        isFocusable = true

        minimumWidth = dp(28)
        minimumHeight = dp(18)

        setLayerType(View.LAYER_TYPE_SOFTWARE, null)

        setOnClickListener {
            if (state != PortState.DISABLED) {
                onPortClick?.invoke(this)
            }
        }
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int
    ) {
        val desiredWidth = dp(30)
        val desiredHeight = dp(18)

        val width = resolveSize(
            desiredWidth,
            widthMeasureSpec
        )

        val height = resolveSize(
            desiredHeight,
            heightMeasureSpec
        )

        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val save = canvas.save()

        if (
            portSide == PortSide.TOP ||
            portSide == PortSide.BOTTOM
        ) {
            canvas.rotate(
                90f,
                width / 2f,
                height / 2f
            )
        }

        val centerX = width / 2f
        val centerY = height / 2f

        val bodyWidth = min(width * 0.90f, dp(27).toFloat())
        val bodyHeight = min(height * 0.84f, dp(15).toFloat())

        bodyRect.set(
            centerX - bodyWidth / 2f,
            centerY - bodyHeight / 2f,
            centerX + bodyWidth / 2f,
            centerY + bodyHeight / 2f
        )

        val currentColor = when (state) {
            PortState.IDLE -> Color.rgb(65, 82, 108)
            PortState.AVAILABLE -> accent
            PortState.SELECTED -> Color.WHITE
            PortState.CONNECTED -> accent
            PortState.ACTIVE -> accent
            PortState.DISABLED -> Color.rgb(65, 65, 72)
        }

        // Outer light.
        if (
            state == PortState.ACTIVE ||
            state == PortState.SELECTED ||
            state == PortState.CONNECTED
        ) {
            glowPaint.color = currentColor
            glowPaint.style = Paint.Style.STROKE
            glowPaint.strokeWidth = dp(
                3f + pulse * 2f
            ).toFloat()
            glowPaint.alpha = if (
                state == PortState.ACTIVE
            ) {
                (70 + pulse * 75).toInt()
            } else {
                75
            }

            canvas.drawRoundRect(
                bodyRect,
                dp(5).toFloat(),
                dp(5).toFloat(),
                glowPaint
            )
        }

        // Metallic body.
        outerPaint.style = Paint.Style.FILL
        outerPaint.color = when (state) {
            PortState.DISABLED -> Color.rgb(36, 40, 49)
            else -> Color.rgb(17, 25, 39)
        }

        canvas.drawRoundRect(
            bodyRect,
            dp(4).toFloat(),
            dp(4).toFloat(),
            outerPaint
        )

        // Outer border.
        outerPaint.style = Paint.Style.STROKE
        outerPaint.strokeWidth = dp(1.3f).toFloat()
        outerPaint.color = currentColor
        outerPaint.alpha = if (
            state == PortState.IDLE
        ) 130 else 255

        canvas.drawRoundRect(
            bodyRect,
            dp(4).toFloat(),
            dp(4).toFloat(),
            outerPaint
        )

        // Inner USB slot.
        val slotWidth = bodyWidth * 0.53f
        val slotHeight = bodyHeight * 0.40f

        slotRect.set(
            centerX - slotWidth / 2f,
            centerY - slotHeight / 2f,
            centerX + slotWidth / 2f,
            centerY + slotHeight / 2f
        )

        innerPaint.style = Paint.Style.FILL
        innerPaint.color = Color.rgb(3, 8, 17)

        canvas.drawRoundRect(
            slotRect,
            dp(2).toFloat(),
            dp(2).toFloat(),
            innerPaint
        )

        // Inner illuminated contact.
        detailPaint.style = Paint.Style.FILL
        detailPaint.color = currentColor
        detailPaint.alpha = if (
            state == PortState.IDLE
        ) 90 else 230

        canvas.drawRoundRect(
            slotRect,
            dp(2).toFloat(),
            dp(2).toFloat(),
            detailPaint
        )

        // Small metallic contact line.
        detailPaint.color = Color.WHITE
        detailPaint.alpha = if (
            state == PortState.ACTIVE
        ) 230 else 90

        canvas.drawRect(
            centerX - dp(3),
            centerY - dp(0.5f),
            centerX + dp(3),
            centerY + dp(0.5f),
            detailPaint
        )

        canvas.restoreToCount(save)
    }

    fun setState(newState: PortState) {
        state = newState
    }

    private fun updateAnimation() {
        animator?.cancel()
        animator = null

        if (
            state == PortState.ACTIVE ||
            state == PortState.SELECTED
        ) {
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

    private fun dp(value: Float): Int {
        return (value * density).toInt()
    }

    private fun dp(value: Int): Int {
        return (value * density).toInt()
    }
}

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
            if (field == value) return
            field = value
            requestLayout()
            invalidate()
        }

    var portKind = PortKind.CONTEXT
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    var shape = PortShape.USB_C
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
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

    private fun dp(value: Float): Float =
        value * density

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bezelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val recessPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val slotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val slotBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val bodyRect = RectF()
    private val recessRect = RectF()
    private val glowRect = RectF()
    private val slots = Array(3) { RectF() }

    private var pulse = 0f
    private var animator: ValueAnimator? = null

    private val accent: Int
        get() = PortPalette.colorFor(portKind)

    private val isActive: Boolean
        get() =
            state == PortState.ACTIVE ||
                state == PortState.SELECTED ||
                state == PortState.CONNECTED

    private val isMuted: Boolean
        get() =
            state == PortState.IDLE ||
                state == PortState.DISABLED

    init {
        isClickable = true
        isFocusable = true

        minimumWidth = dp(28f).toInt()
        minimumHeight = dp(28f).toInt()

        setLayerType(LAYER_TYPE_SOFTWARE, null)

        setOnClickListener {
            if (
                isEnabled &&
                state != PortState.DISABLED
            ) {
                onPortClick?.invoke(this)
            }
        }
    }

    // ---------------------------------------------------------
    // Measurement
    // ---------------------------------------------------------

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int
    ) {
        val horizontal =
            portSide == PortSide.LEFT ||
                portSide == PortSide.RIGHT

        val desiredWidth = dp(
            if (horizontal) 28f else 36f
        ).toInt()

        val desiredHeight = dp(
            if (horizontal) 36f else 28f
        ).toInt()

        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec)
        )
    }

    // ---------------------------------------------------------
    // Drawing (Fixed Socket with 3 Reception Slots)
    // ---------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val horizontal =
            portSide == PortSide.LEFT ||
                portSide == PortSide.RIGHT

        val save = canvas.save()

        /*
         * Logical socket orientation:
         * RIGHT  = normal
         * LEFT   = mirrored
         * TOP    = rotated upward
         * BOTTOM = rotated downward
         */
        when (portSide) {
            PortSide.LEFT -> {
                canvas.scale(
                    -1f,
                    1f,
                    width / 2f,
                    height / 2f
                )
            }

            PortSide.RIGHT -> Unit

            PortSide.TOP -> {
                canvas.rotate(
                    -90f,
                    width / 2f,
                    height / 2f
                )
            }

            PortSide.BOTTOM -> {
                canvas.rotate(
                    90f,
                    width / 2f,
                    height / 2f
                )
            }
        }

        val w = if (horizontal) width.toFloat() else height.toFloat()
        val h = if (horizontal) height.toFloat() else width.toFloat()
        val cy = h / 2f

        val statusColor = when (state) {
            PortState.IDLE -> 0xFF8296AA.toInt()
            PortState.AVAILABLE,
            PortState.CONNECTED,
            PortState.ACTIVE -> accent
            PortState.SELECTED -> Color.WHITE
            PortState.DISABLED -> 0xFF485363.toInt()
        }

        // 1. Fixed socket housing on card edge
        val bodyLeft = dp(3f)
        val bodyRight = (w - dp(3f)).coerceAtLeast(bodyLeft + dp(10f))
        val bodyTop = cy - dp(13f)
        val bodyBottom = cy + dp(13f)

        bodyRect.set(bodyLeft, bodyTop, bodyRight, bodyBottom)

        bodyPaint.apply {
            style = Paint.Style.FILL
            color = when (state) {
                PortState.DISABLED -> 0xFF1A202A.toInt()
                else -> 0xFF131C29.toInt()
            }
            alpha = if (state == PortState.DISABLED) 160 else 255
        }
        canvas.drawRoundRect(bodyRect, dp(4f), dp(4f), bodyPaint)

        // 2. Active state frame indicator around fixed socket
        if (isActive) {
            glowPaint.apply {
                style = Paint.Style.STROKE
                color = statusColor
                strokeWidth = dp(1.4f + pulse * 0.9f)
                alpha = (95 + pulse * 110f).toInt().coerceIn(0, 255)
            }
            glowRect.set(
                bodyRect.left - dp(1.5f),
                bodyRect.top - dp(1.5f),
                bodyRect.right + dp(1.5f),
                bodyRect.bottom + dp(1.5f)
            )
            canvas.drawRoundRect(glowRect, dp(5f), dp(5f), glowPaint)
        }

        // 3. Structural metallic border
        bezelPaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(1.1f)
            color = if (isActive) statusColor else 0xFF5A6D82.toInt()
            alpha = if (isMuted) 140 else 230
        }
        canvas.drawRoundRect(bodyRect, dp(4f), dp(4f), bezelPaint)

        // 4. Recessed receiving cavity
        recessRect.set(
            bodyRect.left + dp(3.2f),
            bodyRect.top + dp(3.5f),
            bodyRect.right - dp(3.2f),
            bodyRect.bottom - dp(3.5f)
        )

        recessPaint.apply {
            style = Paint.Style.FILL
            color = 0xFF060A12.toInt()
            alpha = if (state == PortState.DISABLED) 140 else 255
        }
        canvas.drawRoundRect(recessRect, dp(2.5f), dp(2.5f), recessPaint)

        // 5. Three reception slots aligned with 3-pin plug contacts
        val slotOffsets = floatArrayOf(
            cy - dp(6.5f),
            cy,
            cy + dp(6.5f)
        )
        val slotHeight = dp(3.2f)
        val slotWidth = (recessRect.width() - dp(3f)).coerceAtLeast(dp(4f))
        val slotLeft = recessRect.centerX() - slotWidth / 2f

        slotPaint.apply {
            style = Paint.Style.FILL
            color = if (isActive) statusColor else 0xFF020409.toInt()
            alpha = when {
                state == PortState.DISABLED -> 90
                isActive -> 220
                else -> 255
            }
        }

        slotBorderPaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(0.75f)
            color = if (isActive) statusColor else 0xFF334155.toInt()
            alpha = if (isMuted) 130 else 210
        }

        for (slotCenterY in slotOffsets) {
            val top = slotCenterY - slotHeight / 2f
            val bottom = slotCenterY + slotHeight / 2f
            val idx = when (slotCenterY) {
                slotOffsets[0] -> 0
                slotOffsets[1] -> 1
                else -> 2
            }
            slots[idx].set(slotLeft, top, slotLeft + slotWidth, bottom)
            canvas.drawRoundRect(slots[idx], dp(1.2f), dp(1.2f), slotPaint)
            canvas.drawRoundRect(slots[idx], dp(1.2f), dp(1.2f), slotBorderPaint)
        }

        // 6. Subtle upper edge highlight
        highlightPaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(0.8f)
            color = Color.WHITE
            alpha = if (isMuted) 35 else 85
        }
        canvas.drawLine(
            bodyRect.left + dp(3f),
            bodyRect.top + dp(1.2f),
            bodyRect.right - dp(3f),
            bodyRect.top + dp(1.2f),
            highlightPaint
        )

        canvas.restoreToCount(save)
    }

    // ---------------------------------------------------------
    // State animation
    // ---------------------------------------------------------

    private fun updateAnimation() {
        animator?.cancel()
        animator = null

        if (
            state == PortState.ACTIVE ||
                state == PortState.SELECTED
        ) {
            if (!isAttachedToWindow) return

            animator = ValueAnimator.ofFloat(
                0f,
                1f
            ).apply {
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
        pulse = 0f
        super.onDetachedFromWindow()
    }
}

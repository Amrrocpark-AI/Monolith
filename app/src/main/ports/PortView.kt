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
            if (showPortIdBadge) {
                requestLayout()
                invalidate()
            }
        }

    var showPortIdBadge: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
            invalidate()
        }

    var portIdBadgeText: String? = null
        set(value) {
            if (field == value) return
            field = value
            if (showPortIdBadge) {
                requestLayout()
                invalidate()
            }
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

    private fun sp(value: Float): Float =
        value * resources.scaledDensity

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bezelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val recessPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val slotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val slotBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ledPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ledHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val badgeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    private val bodyRect = RectF()
    private val recessRect = RectF()
    private val ledRect = RectF()
    private val ledHaloRect = RectF()
    private val badgeRect = RectF()
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

    private val effectiveBadgeLabel: String
        get() = (portIdBadgeText ?: portId).trim()

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

        val baseLengthDp = when (shape) {
            PortShape.COMPACT -> 24f
            PortShape.USB -> 32f
            PortShape.USB_C -> 30f
        }

        val baseSpanDp = when (shape) {
            PortShape.COMPACT -> 32f
            PortShape.USB -> 40f
            PortShape.USB_C -> 36f
        }

        val badgeExtraDp = if (showPortIdBadge && effectiveBadgeLabel.isNotEmpty()) {
            20f
        } else {
            0f
        }

        val desiredWidth = dp(
            if (horizontal) baseLengthDp + badgeExtraDp else baseSpanDp
        ).toInt()

        val desiredHeight = dp(
            if (horizontal) baseSpanDp else baseLengthDp + badgeExtraDp
        ).toInt()

        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec)
        )
    }

    // ---------------------------------------------------------
    // Drawing (Fixed Socket with 3 Reception Slots + Side LED)
    // ---------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val horizontal =
            portSide == PortSide.LEFT ||
                portSide == PortSide.RIGHT

        val badgeLabel = if (showPortIdBadge) effectiveBadgeLabel else ""
        val hasBadge = badgeLabel.isNotEmpty()
        val badgeReserve = if (hasBadge) dp(18f) else 0f

        val save = canvas.save()

        /*
         * Logical socket orientation:
         * RIGHT  = normal (outer face on right)
         * LEFT   = mirrored (outer face on left)
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

        // Reserve inner/back space if optional Port ID badge is shown on the inner side
        val socketAreaStart = if (horizontal) badgeReserve else 0f
        val socketAreaEnd = w
        val cy = h / 2f

        val statusColor = when (state) {
            PortState.IDLE -> 0xFF8296AA.toInt()
            PortState.AVAILABLE,
            PortState.CONNECTED,
            PortState.ACTIVE -> accent
            PortState.SELECTED -> Color.WHITE
            PortState.DISABLED -> 0xFF485363.toInt()
        }

        val halfSpan = when (shape) {
            PortShape.COMPACT -> dp(11.5f)
            PortShape.USB -> dp(15f)
            PortShape.USB_C -> dp(13f)
        }

        val bodyCorner = when (shape) {
            PortShape.COMPACT -> dp(2.5f)
            PortShape.USB -> dp(2f)
            PortShape.USB_C -> dp(5.5f)
        }

        val slotCorner = when (shape) {
            PortShape.COMPACT -> dp(0.8f)
            PortShape.USB -> dp(0.6f)
            PortShape.USB_C -> dp(1.5f)
        }

        val slotHeight = when (shape) {
            PortShape.COMPACT -> dp(2.6f)
            PortShape.USB -> dp(3.0f)
            PortShape.USB_C -> dp(3.4f)
        }

        val slotSpacing = when (shape) {
            PortShape.COMPACT -> dp(5.5f)
            PortShape.USB -> dp(7.2f)
            PortShape.USB_C -> dp(6.5f)
        }

        val bodyLeft = (socketAreaStart + dp(2.5f)).coerceAtMost(socketAreaEnd - dp(8f))
        val bodyRight = (socketAreaEnd - dp(2.5f)).coerceAtLeast(bodyLeft + dp(6f))
        val bodyTop = cy - halfSpan
        val bodyBottom = cy + halfSpan

        // 1. Fixed socket body
        bodyRect.set(bodyLeft, bodyTop, bodyRight, bodyBottom)
        bodyPaint.apply {
            style = Paint.Style.FILL
            color = when (state) {
                PortState.DISABLED -> 0xFF1A202A.toInt()
                else -> 0xFF131C29.toInt()
            }
            alpha = if (state == PortState.DISABLED) 160 else 255
        }
        canvas.drawRoundRect(bodyRect, bodyCorner, bodyCorner, bodyPaint)

        // 2. Metallic structural frame
        bezelPaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = if (shape == PortShape.USB) dp(1.3f) else dp(1.05f)
            color = if (isActive) statusColor else 0xFF5A6D82.toInt()
            alpha = if (isMuted) 140 else 230
        }
        canvas.drawRoundRect(bodyRect, bodyCorner, bodyCorner, bezelPaint)

        // 3. Recessed receiving cavity
        val recessInsetX = when (shape) {
            PortShape.COMPACT -> dp(2.5f)
            PortShape.USB -> dp(3.5f)
            PortShape.USB_C -> dp(3.2f)
        }
        val recessInsetY = when (shape) {
            PortShape.COMPACT -> dp(2.8f)
            PortShape.USB -> dp(3.8f)
            PortShape.USB_C -> dp(3.5f)
        }
        recessRect.set(
            (bodyLeft + recessInsetX).coerceAtMost(bodyRight - dp(2f)),
            (bodyTop + recessInsetY).coerceAtMost(bodyBottom - dp(2f)),
            (bodyRight - recessInsetX).coerceAtLeast(bodyLeft + dp(2f)),
            (bodyBottom - recessInsetY).coerceAtLeast(bodyTop + dp(2f))
        )

        recessPaint.apply {
            style = Paint.Style.FILL
            color = 0xFF060A12.toInt()
            alpha = if (state == PortState.DISABLED) 140 else 255
        }
        canvas.drawRoundRect(
            recessRect,
            (bodyCorner - dp(1f)).coerceAtLeast(dp(1f)),
            (bodyCorner - dp(1f)).coerceAtLeast(dp(1f)),
            recessPaint
        )

        // 4. Three recessed reception slots aligned with 3-pin plug contacts
        val slotOffsets = floatArrayOf(
            cy - slotSpacing,
            cy,
            cy + slotSpacing
        )
        val slotWidth = (recessRect.width() - dp(2.5f)).coerceAtLeast(dp(3.5f))
        val slotLeft = recessRect.centerX() - slotWidth / 2f

        slotPaint.apply {
            style = Paint.Style.FILL
            color = if (isActive) statusColor else 0xFF020409.toInt()
            alpha = when {
                state == PortState.DISABLED -> 90
                isActive -> 215
                else -> 255
            }
        }

        slotBorderPaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(0.75f)
            color = if (isActive) statusColor else 0xFF334155.toInt()
            alpha = if (isMuted) 130 else 210
        }

        for (i in 0 until 3) {
            val slotCenterY = slotOffsets[i]
            slots[i].set(
                slotLeft,
                slotCenterY - slotHeight / 2f,
                slotLeft + slotWidth,
                slotCenterY + slotHeight / 2f
            )
            canvas.drawRoundRect(slots[i], slotCorner, slotCorner, slotPaint)
            canvas.drawRoundRect(slots[i], slotCorner, slotCorner, slotBorderPaint)
        }

        // 5. Side LED indicator along the socket edge
        val ledWidth = dp(2.4f)
        val ledHeight = (halfSpan * 1.15f).coerceAtLeast(dp(8f))
        val ledX = (bodyLeft - ledWidth - dp(1.2f)).coerceAtLeast(socketAreaStart + dp(0.5f))
        ledRect.set(
            ledX,
            cy - ledHeight / 2f,
            ledX + ledWidth,
            cy + ledHeight / 2f
        )

        if (isActive) {
            ledHaloPaint.apply {
                style = Paint.Style.FILL
                color = statusColor
                alpha = (65 + pulse * 95f).toInt().coerceIn(0, 255)
            }
            ledHaloRect.set(
                ledRect.left - dp(1.8f),
                ledRect.top - dp(1.8f),
                ledRect.right + dp(1.8f),
                ledRect.bottom + dp(1.8f)
            )
            canvas.drawRoundRect(ledHaloRect, dp(3f), dp(3f), ledHaloPaint)
        }

        ledPaint.apply {
            style = Paint.Style.FILL
            color = when {
                state == PortState.DISABLED -> 0xFF39424E.toInt()
                isActive -> statusColor
                else -> 0xFF5A6D82.toInt()
            }
            alpha = when {
                state == PortState.DISABLED -> 120
                isActive -> (210 + pulse * 45f).toInt().coerceIn(0, 255)
                else -> 150
            }
        }
        canvas.drawRoundRect(ledRect, dp(1.4f), dp(1.4f), ledPaint)

        // 6. Subtle upper metallic edge highlight
        highlightPaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(0.8f)
            color = Color.WHITE
            alpha = if (isMuted) 35 else 85
        }
        canvas.drawLine(
            bodyRect.left + dp(2.5f),
            bodyRect.top + dp(1.1f),
            bodyRect.right - dp(2.5f),
            bodyRect.top + dp(1.1f),
            highlightPaint
        )

        canvas.restoreToCount(save)

        // 7. Optional Port ID badge drawn cleanly in unrotated space when enabled
        if (hasBadge) {
            drawOptionalIdBadge(canvas, badgeLabel, horizontal)
        }
    }

    private fun drawOptionalIdBadge(
        canvas: Canvas,
        label: String,
        horizontal: Boolean
    ) {
        badgeTextPaint.textSize = sp(8.5f)
        badgeTextPaint.color = if (isActive) accent else 0xFFC5D2DE.toInt()

        val textW = badgeTextPaint.measureText(label)
        val padH = dp(4f)
        val badgeW = (textW + padH * 2f).coerceAtMost(width.toFloat() - dp(2f))
        val badgeH = dp(13f)

        val left: Float
        val top: Float

        if (horizontal) {
            left = if (portSide == PortSide.RIGHT) {
                dp(1f)
            } else {
                (width - badgeW - dp(1f)).coerceAtLeast(dp(1f))
            }
            top = (height - badgeH) / 2f
        } else {
            left = (width - badgeW) / 2f
            top = if (portSide == PortSide.BOTTOM) {
                dp(1f)
            } else {
                (height - badgeH - dp(1f)).coerceAtLeast(dp(1f))
            }
        }

        badgeRect.set(left, top, left + badgeW, top + badgeH)

        badgeBgPaint.color = 0xEE0B121D.toInt()
        badgeBgPaint.style = Paint.Style.FILL
        canvas.drawRoundRect(badgeRect, dp(3f), dp(3f), badgeBgPaint)

        badgeBorderPaint.color = if (isActive) accent else 0xFF475569.toInt()
        badgeBorderPaint.style = Paint.Style.STROKE
        badgeBorderPaint.strokeWidth = dp(0.8f)
        canvas.drawRoundRect(badgeRect, dp(3f), dp(3f), badgeBorderPaint)

        val fm = badgeTextPaint.fontMetrics
        val baselineY = badgeRect.centerY() - (fm.ascent + fm.descent) / 2f
        canvas.drawText(label, badgeRect.centerX(), baselineY, badgeTextPaint)
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

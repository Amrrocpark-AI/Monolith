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
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val recessPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val slotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val slotInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ledPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val badgeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    private val socketRect = RectF()
    private val recessRect = RectF()
    private val ledRect = RectF()
    private val badgeRect = RectF()
    private val slots = Array(3) { RectF() }

    private var pulse = 0f
    private var animator: ValueAnimator? = null

    private val accent: Int
        get() = PortPalette.colorFor(portKind)

    private val isLit: Boolean
        get() =
            state == PortState.AVAILABLE ||
                state == PortState.CONNECTED ||
                state == PortState.ACTIVE ||
                state == PortState.SELECTED

    private val isMuted: Boolean
        get() =
            state == PortState.IDLE ||
                state == PortState.DISABLED

    private val effectiveBadgeLabel: String
        get() = (portIdBadgeText ?: portId).trim()

    private fun measureBadgeWidth(label: String): Float {
        if (!showPortIdBadge || label.isEmpty()) return 0f
        badgeTextPaint.textSize = sp(8.5f)
        return (badgeTextPaint.measureText(label) + dp(8f)).coerceAtLeast(dp(18f))
    }

    private fun socketBaseWidthDp(): Float = when (shape) {
        PortShape.COMPACT -> 24f
        PortShape.USB -> 30f
        PortShape.USB_C -> 28f
    }

    private fun socketBaseHeightDp(): Float = when (shape) {
        PortShape.COMPACT -> 30f
        PortShape.USB -> 38f
        PortShape.USB_C -> 36f
    }

    init {
        isClickable = true
        isFocusable = true

        minimumWidth = dp(24f).toInt()
        minimumHeight = dp(24f).toInt()

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
    // Measurement (Fixed Socket footprint + optional badge space)
    // ---------------------------------------------------------

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int
    ) {
        val horizontal =
            portSide == PortSide.LEFT ||
                portSide == PortSide.RIGHT

        val baseW = dp(if (horizontal) socketBaseWidthDp() else socketBaseHeightDp())
        val baseH = dp(if (horizontal) socketBaseHeightDp() else socketBaseWidthDp())

        val label = effectiveBadgeLabel
        val badgeW = measureBadgeWidth(label)
        val badgeH = if (showPortIdBadge && label.isNotEmpty()) dp(13f) else 0f
        val gap = if (showPortIdBadge && label.isNotEmpty()) dp(3f) else 0f

        val desiredWidth = if (horizontal) {
            baseW + badgeW + gap
        } else {
            maxOf(baseW, badgeW)
        }

        val desiredHeight = if (horizontal) {
            maxOf(baseH, badgeH)
        } else {
            baseH + badgeH + gap
        }

        setMeasuredDimension(
            resolveSize(desiredWidth.toInt(), widthMeasureSpec),
            resolveSize(desiredHeight.toInt(), heightMeasureSpec)
        )
    }

    // ---------------------------------------------------------
    // Drawing (Fixed Socket with 3 Recessed Slots + Side LED)
    // ---------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val horizontal =
            portSide == PortSide.LEFT ||
                portSide == PortSide.RIGHT

        val label = effectiveBadgeLabel
        val hasBadge = showPortIdBadge && label.isNotEmpty()
        val badgeSize = if (hasBadge) measureBadgeWidth(label) else 0f
        val badgeGap = if (hasBadge) dp(3f) else 0f

        // Compute fixed socket region independent of badge width/text
        val baseW = dp(if (horizontal) socketBaseWidthDp() else socketBaseHeightDp())
        val baseH = dp(if (horizontal) socketBaseHeightDp() else socketBaseWidthDp())

        val socketLeft: Float
        val socketTop: Float

        when (portSide) {
            PortSide.RIGHT -> {
                socketLeft = 0f
                socketTop = (height - baseH) / 2f
            }
            PortSide.LEFT -> {
                socketLeft = (width - baseW).coerceAtLeast(0f)
                socketTop = (height - baseH) / 2f
            }
            PortSide.BOTTOM -> {
                socketLeft = (width - baseW) / 2f
                socketTop = 0f
            }
            PortSide.TOP -> {
                socketLeft = (width - baseW) / 2f
                socketTop = (height - baseH).coerceAtLeast(0f)
            }
        }

        val save = canvas.save()

        when (portSide) {
            PortSide.LEFT -> {
                canvas.scale(
                    -1f,
                    1f,
                    socketLeft + baseW / 2f,
                    socketTop + baseH / 2f
                )
            }
            PortSide.RIGHT -> Unit
            PortSide.TOP -> {
                canvas.rotate(
                    -90f,
                    socketLeft + baseW / 2f,
                    socketTop + baseH / 2f
                )
            }
            PortSide.BOTTOM -> {
                canvas.rotate(
                    90f,
                    socketLeft + baseW / 2f,
                    socketTop + baseH / 2f
                )
            }
        }

        val localW = if (horizontal) baseW else baseH
        val localH = if (horizontal) baseH else baseW
        val originX = if (horizontal) socketLeft else socketTop
        val originY = if (horizontal) socketTop else socketLeft
        val cy = originY + localH / 2f

        val activeColor = when (state) {
            PortState.SELECTED -> Color.WHITE
            PortState.AVAILABLE,
            PortState.CONNECTED,
            PortState.ACTIVE -> accent
            PortState.IDLE -> 0xFF8296AA.toInt()
            PortState.DISABLED -> 0xFF485363.toInt()
        }

        val cornerRadius = when (shape) {
            PortShape.COMPACT -> dp(2f)
            PortShape.USB -> dp(1.5f)
            PortShape.USB_C -> dp(6f)
        }

        val slotCorner = when (shape) {
            PortShape.COMPACT -> dp(0.6f)
            PortShape.USB -> dp(0.5f)
            PortShape.USB_C -> dp(1.6f)
        }

        val slotHeight = when (shape) {
            PortShape.COMPACT -> dp(2.4f)
            PortShape.USB -> dp(2.8f)
            PortShape.USB_C -> dp(3.4f)
        }

        val slotSpacing = when (shape) {
            PortShape.COMPACT -> dp(5.2f)
            PortShape.USB -> dp(7.4f)
            PortShape.USB_C -> dp(6.6f)
        }

        // 1. Fixed socket body on the card edge
        socketRect.set(
            originX + dp(2f),
            originY + dp(2f),
            originX + localW - dp(2f),
            originY + localH - dp(2f)
        )

        bodyPaint.apply {
            style = Paint.Style.FILL
            color = if (state == PortState.DISABLED) 0xFF1A202A.toInt() else 0xFF121A26.toInt()
            alpha = if (state == PortState.DISABLED) 160 else 255
        }
        canvas.drawRoundRect(socketRect, cornerRadius, cornerRadius, bodyPaint)

        borderPaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = if (shape == PortShape.USB) dp(1.2f) else dp(0.95f)
            color = if (isLit) activeColor else 0xFF4A5B6E.toInt()
            alpha = if (isMuted && !isLit) 140 else 220
        }
        canvas.drawRoundRect(socketRect, cornerRadius, cornerRadius, borderPaint)

        // 2. Recessed cavity inside socket
        val insetX = when (shape) {
            PortShape.COMPACT -> dp(2.5f)
            PortShape.USB -> dp(3.8f)
            PortShape.USB_C -> dp(3.2f)
        }
        val insetY = when (shape) {
            PortShape.COMPACT -> dp(2.8f)
            PortShape.USB -> dp(4.0f)
            PortShape.USB_C -> dp(3.4f)
        }

        recessRect.set(
            (socketRect.left + insetX).coerceAtMost(socketRect.right - dp(2f)),
            (socketRect.top + insetY).coerceAtMost(socketRect.bottom - dp(2f)),
            (socketRect.right - insetX).coerceAtLeast(socketRect.left + dp(2f)),
            (socketRect.bottom - insetY).coerceAtLeast(socketRect.top + dp(2f))
        )

        recessPaint.apply {
            style = Paint.Style.FILL
            color = 0xFF050911.toInt()
            alpha = if (state == PortState.DISABLED) 140 else 255
        }
        canvas.drawRoundRect(
            recessRect,
            (cornerRadius - dp(1.2f)).coerceAtLeast(dp(0.8f)),
            (cornerRadius - dp(1.2f)).coerceAtLeast(dp(0.8f)),
            recessPaint
        )

        // 3. Three recessed reception slots inside the cavity
        val slotOffsets = floatArrayOf(
            cy - slotSpacing,
            cy,
            cy + slotSpacing
        )
        val slotWidth = when (shape) {
            PortShape.COMPACT -> (recessRect.width() - dp(2.4f)).coerceAtLeast(dp(3.5f))
            PortShape.USB -> (recessRect.width() - dp(3.6f)).coerceAtLeast(dp(4.0f))
            PortShape.USB_C -> (recessRect.width() - dp(2.8f)).coerceAtLeast(dp(4.0f))
        }
        val slotLeft = recessRect.centerX() - slotWidth / 2f

        slotPaint.apply {
            style = Paint.Style.FILL
            color = 0xFF010205.toInt()
            alpha = if (state == PortState.DISABLED) 120 else 255
        }

        slotInnerPaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(0.75f)
            color = if (isLit) activeColor else 0xFF283546.toInt()
            alpha = if (isLit) 200 else 135
        }

        for (i in 0 until 3) {
            val top = slotOffsets[i] - slotHeight / 2f
            val bottom = slotOffsets[i] + slotHeight / 2f
            slots[i].set(slotLeft, top, slotLeft + slotWidth, bottom)
            canvas.drawRoundRect(slots[i], slotCorner, slotCorner, slotPaint)
            canvas.drawRoundRect(slots[i], slotCorner, slotCorner, slotInnerPaint)
        }

        // 4. Side LED indicator aligned along the socket
        val ledW = dp(2.2f)
        val ledH = (recessRect.height() * 0.78f).coerceAtLeast(dp(8f))
        val ledX = (socketRect.left - ledW - dp(1.2f)).coerceAtLeast(originX + dp(0.4f))
        ledRect.set(ledX, cy - ledH / 2f, ledX + ledW, cy + ledH / 2f)

        ledPaint.apply {
            style = Paint.Style.FILL
            color = when {
                state == PortState.DISABLED -> 0xFF39424E.toInt()
                isLit -> activeColor
                else -> 0xFF5A6D82.toInt()
            }
            alpha = when {
                state == PortState.DISABLED -> 115
                isLit -> (205 + pulse * 50f).toInt().coerceIn(0, 255)
                else -> 145
            }
        }
        canvas.drawRoundRect(ledRect, dp(1.2f), dp(1.2f), ledPaint)

        canvas.restoreToCount(save)

        // 5. Optional Port ID badge in its own separate region
        if (hasBadge) {
            drawOptionalIdBadge(canvas, label, horizontal, badgeSize, badgeGap)
        }
    }

    private fun drawOptionalIdBadge(
        canvas: Canvas,
        label: String,
        horizontal: Boolean,
        badgeSize: Float,
        badgeGap: Float
    ) {
        badgeTextPaint.textSize = sp(8.5f)
        badgeTextPaint.color = if (isLit) accent else 0xFFC5D2DE.toInt()

        val badgeH = dp(13f)
        val badgeW = badgeSize.coerceAtMost(width.toFloat())

        val left: Float
        val top: Float

        if (horizontal) {
            left = if (portSide == PortSide.RIGHT) {
                (dp(socketBaseWidthDp()) + badgeGap).coerceAtMost((width - badgeW).coerceAtLeast(0f))
            } else {
                0f
            }
            top = (height - badgeH) / 2f
        } else {
            left = (width - badgeW) / 2f
            top = if (portSide == PortSide.BOTTOM) {
                (dp(socketBaseWidthDp()) + badgeGap).coerceAtMost((height - badgeH).coerceAtLeast(0f))
            } else {
                0f
            }
        }

        badgeRect.set(left, top, left + badgeW, top + badgeH)

        badgeBgPaint.color = 0xEE0B121D.toInt()
        badgeBgPaint.style = Paint.Style.FILL
        canvas.drawRoundRect(badgeRect, dp(2.5f), dp(2.5f), badgeBgPaint)

        badgeBorderPaint.color = if (isLit) accent else 0xFF475569.toInt()
        badgeBorderPaint.style = Paint.Style.STROKE
        badgeBorderPaint.strokeWidth = dp(0.8f)
        canvas.drawRoundRect(badgeRect, dp(2.5f), dp(2.5f), badgeBorderPaint)

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

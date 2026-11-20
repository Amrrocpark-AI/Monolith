
package com.monolith.graph.ports.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
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
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val contactPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val bodyPath = Path()
    private val neckPath = Path()

    private val bodyRect = RectF()
    private val neckRect = RectF()
    private val glowRect = RectF()

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

        minimumWidth = dp(30f).toInt()
        minimumHeight = dp(30f).toInt()

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
            if (horizontal) 54f else 30f
        ).toInt()

        val desiredHeight = dp(
            if (horizontal) 30f else 54f
        ).toInt()

        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec)
        )
    }

    // ---------------------------------------------------------
    // Drawing
    // ---------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val horizontal =
            portSide == PortSide.LEFT ||
            portSide == PortSide.RIGHT

        val save = canvas.save()

        /*
         * The connector is authored in one logical
         * direction: from the card edge outward.
         *
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

        val w = if (horizontal) {
            width.toFloat()
        } else {
            height.toFloat()
        }

        val h = if (horizontal) {
            height.toFloat()
        } else {
            width.toFloat()
        }

        val cy = h / 2f

        val color = when (state) {
            PortState.IDLE ->
                0xFF8296AA.toInt()

            PortState.AVAILABLE,
            PortState.CONNECTED,
            PortState.ACTIVE ->
                accent

            PortState.SELECTED ->
                Color.WHITE

            PortState.DISABLED ->
                0xFF485363.toInt()
        }

        /*
         * The cluster currently centers the PortView
         * on the card boundary.
         *
         * The visible connector is therefore drawn
         * from the boundary region toward the outside.
         */

        // -----------------------------------------------------
        // 1. Connector glow
        // -----------------------------------------------------

        if (isActive) {
            glowPaint.apply {
                style = Paint.Style.STROKE
                color = color
                strokeWidth = dp(1.8f + pulse * 1.2f)
                alpha = (55 + pulse * 75f).toInt()
            }

            glowRect.set(
                dp(31f),
                dp(2f),
                w - dp(1f),
                h - dp(2f)
            )

            canvas.drawRoundRect(
                glowRect,
                dp(5f),
                dp(5f),
                glowPaint
            )
        }

        // -----------------------------------------------------
        // 2. Three contact pins at the card side
        // -----------------------------------------------------

        val pinStart = dp(26.5f)
        val pinEnd = dp(33.5f)

        val pinPositions = floatArrayOf(
            cy - dp(6.5f),
            cy,
            cy + dp(6.5f)
        )

        contactPaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(1.8f)
            strokeCap = Paint.Cap.SQUARE
            color = when (state) {
                PortState.DISABLED -> 0xFF56616D.toInt()
                else -> 0xFFB8C6D1.toInt()
            }
            alpha = if (isMuted) 155 else 235
        }

        pinPositions.forEach { y ->
            canvas.drawLine(
                pinStart,
                y,
                pinEnd,
                y,
                contactPaint
            )
        }

        // -----------------------------------------------------
        // 3. Main colored connector body
        // -----------------------------------------------------

        val bodyLeft = dp(33f)
        val bodyRight = dp(48f)

        val bodyTop = cy - dp(11f)
        val bodyBottom = cy + dp(11f)

        bodyPath.reset()

        /*
         * Wide plug body:
         * broad main housing with shaped shoulders.
         * The silhouette is not a simple rectangle.
         */

        bodyPath.moveTo(
            bodyLeft + dp(2f),
            bodyTop
        )

        bodyPath.lineTo(
            bodyRight - dp(4f),
            bodyTop
        )

        bodyPath.quadTo(
            bodyRight,
            bodyTop,
            bodyRight,
            bodyTop + dp(4f)
        )

        bodyPath.lineTo(
            bodyRight,
            bodyBottom - dp(4f)
        )

        bodyPath.quadTo(
            bodyRight,
            bodyBottom,
            bodyRight - dp(4f),
            bodyBottom
        )

        bodyPath.lineTo(
            bodyLeft + dp(2f),
            bodyBottom
        )

        bodyPath.quadTo(
            bodyLeft,
            bodyBottom,
            bodyLeft,
            bodyBottom - dp(3f)
        )

        bodyPath.lineTo(
            bodyLeft,
            bodyTop + dp(3f)
        )

        bodyPath.quadTo(
            bodyLeft,
            bodyTop,
            bodyLeft + dp(2f),
            bodyTop
        )

        bodyPath.close()

        bodyPaint.apply {
            style = Paint.Style.FILL
            color = when (state) {
                PortState.DISABLED -> 0xFF35414D.toInt()
                else -> color
            }
            alpha = when (state) {
                PortState.IDLE -> 185
                PortState.DISABLED -> 145
                else -> 255
            }
        }

        canvas.drawPath(
            bodyPath,
            bodyPaint
        )

        // -----------------------------------------------------
        // 4. Body edge
        // -----------------------------------------------------

        edgePaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(0.8f)
            color = if (isMuted) {
                0xFF9BAAB6.toInt()
            } else {
                Color.WHITE
            }
            alpha = if (isMuted) 95 else 115
        }

        canvas.drawPath(
            bodyPath,
            edgePaint
        )

        // -----------------------------------------------------
        // 5. Upper body highlight
        // -----------------------------------------------------

        highlightPaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(0.8f)
            strokeCap = Paint.Cap.ROUND
            color = Color.WHITE
            alpha = if (isMuted) 35 else 90
        }

        canvas.drawLine(
            bodyLeft + dp(3f),
            bodyTop + dp(1.2f),
            bodyRight - dp(5f),
            bodyTop + dp(1.2f),
            highlightPaint
        )

        // -----------------------------------------------------
        // 6. Narrow stepped connector neck
        // -----------------------------------------------------

        val neckLeft = dp(47f)
        val neckRight = dp(54f)

        val neckTop = cy - dp(4f)
        val neckBottom = cy + dp(4f)

        neckPath.reset()

        neckPath.moveTo(
            neckLeft,
            neckTop
        )

        neckPath.lineTo(
            neckRight - dp(1f),
            neckTop
        )

        neckPath.quadTo(
            neckRight,
            neckTop,
            neckRight,
            cy - dp(2.5f)
        )

        neckPath.lineTo(
            neckRight,
            cy + dp(2.5f)
        )

        neckPath.quadTo(
            neckRight,
            neckBottom,
            neckRight - dp(1f),
            neckBottom
        )

        neckPath.lineTo(
            neckLeft,
            neckBottom
        )

        neckPath.close()

        bodyPaint.apply {
            style = Paint.Style.FILL
            color = when (state) {
                PortState.DISABLED -> 0xFF35414D.toInt()
                else -> color
            }
            alpha = when (state) {
                PortState.IDLE -> 190
                PortState.DISABLED -> 145
                else -> 255
            }
        }

        canvas.drawPath(
            neckPath,
            bodyPaint
        )

        // Neck edge
        edgePaint.alpha = if (isMuted) 85 else 110

        canvas.drawPath(
            neckPath,
            edgePaint
        )

        // -----------------------------------------------------
        // 7. Neck transition highlight
        // -----------------------------------------------------

        highlightPaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(0.65f)
            color = Color.WHITE
            alpha = if (isMuted) 30 else 75
        }

        canvas.drawLine(
            neckLeft + dp(1f),
            cy - dp(2.5f),
            neckRight - dp(1.5f),
            cy - dp(2.5f),
            highlightPaint
        )

        // -----------------------------------------------------
        // 8. Cable exit point
        // -----------------------------------------------------

        /*
         * The cable itself is not drawn here.
         *
         * Its geometric exit point is the outer end
         * of the neck: x = view width, y = center.
         *
         * EndpointRegistry must use this same point.
         */

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


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
    private val shellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val contactPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val slotPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val mountRect = RectF()
    private val mountInnerRect = RectF()
    private val housingRect = RectF()
    private val neckRect = RectF()
    private val noseRect = RectF()
    private val seamRect = RectF()
    private val slotRect = RectF()
    private val ledRect = RectF()

    private var pulse = 0f
    private var animator: ValueAnimator? = null

    private val accent: Int
        get() = PortPalette.colorFor(portKind)

    private val isActive: Boolean
        get() =
            state == PortState.ACTIVE ||
            state == PortState.SELECTED ||
            state == PortState.CONNECTED

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
         * Logical connector direction is always RIGHT.
         *
         * LEFT   = mirrored
         * RIGHT  = normal
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

        val logicalWidth =
            if (horizontal) width.toFloat()
            else height.toFloat()

        val logicalHeight =
            if (horizontal) height.toFloat()
            else width.toFloat()

        val w = logicalWidth
        val h = logicalHeight

        val cy = h / 2f

        /*
         * The cluster places the center of this view
         * on the card boundary.
         *
         * Therefore:
         * - x < w/2 is the mounted inner portion.
         * - x > w/2 is the outward connector portion.
         *
         * The connector is deliberately asymmetric.
         */

        val color = when (state) {
            PortState.IDLE ->
                0xFF8296AA.toInt()

            PortState.AVAILABLE ->
                accent

            PortState.SELECTED ->
                Color.WHITE

            PortState.CONNECTED ->
                accent

            PortState.ACTIVE ->
                accent

            PortState.DISABLED ->
                0xFF485363.toInt()
        }

        val muted =
            state == PortState.IDLE ||
            state == PortState.DISABLED

        // -----------------------------------------------------
        // 1. Outer connection glow
        // -----------------------------------------------------

        if (isActive) {
            glowPaint.apply {
                style = Paint.Style.STROKE
                color = color
                strokeWidth = dp(2f + pulse * 1.5f)
                alpha = (45 + pulse * 85f).toInt()
            }

            val glowRect = RectF(
                dp(25f),
                dp(4f),
                w - dp(1f),
                h - dp(4f)
            )

            canvas.drawRoundRect(
                glowRect,
                dp(5f),
                dp(5f),
                glowPaint
            )
        }

        // -----------------------------------------------------
        // 2. Mounting base attached to the card edge
        // -----------------------------------------------------

        mountRect.set(
            dp(15f),
            dp(3f),
            dp(33f),
            h - dp(3f)
        )

        bodyPaint.apply {
            style = Paint.Style.FILL
            color = if (state == PortState.DISABLED) {
                0xFF202936.toInt()
            } else {
                0xFF101A29.toInt()
            }
            alpha = 255
        }

        canvas.drawRoundRect(
            mountRect,
            dp(4f),
            dp(4f),
            bodyPaint
        )

        // Metallic outer mounting rim
        shellPaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(1.15f)
            color = if (muted) {
                0xFF657487.toInt()
            } else {
                0xFFB0C4D6.toInt()
            }
            alpha = if (muted) 145 else 225
        }

        canvas.drawRoundRect(
            mountRect,
            dp(4f),
            dp(4f),
            shellPaint
        )

        // Inner mounting plate
        mountInnerRect.set(
            mountRect.left + dp(2f),
            mountRect.top + dp(2f),
            mountRect.right - dp(2f),
            mountRect.bottom - dp(2f)
        )

        bodyPaint.color = 0xFF18283A.toInt()

        canvas.drawRoundRect(
            mountInnerRect,
            dp(2.5f),
            dp(2.5f),
            bodyPaint
        )

        // -----------------------------------------------------
        // 3. Connector housing
        // -----------------------------------------------------

        housingRect.set(
            dp(25f),
            dp(6f),
            w - dp(1.5f),
            h - dp(6f)
        )

        bodyPaint.color = when (state) {
            PortState.DISABLED -> 0xFF27313F.toInt()
            else -> 0xFF142437.toInt()
        }

        canvas.drawRoundRect(
            housingRect,
            dp(4f),
            dp(4f),
            bodyPaint
        )

        // Colored metallic housing outline
        borderPaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(1.15f)
            color = color
            alpha = if (muted) 125 else 230
        }

        canvas.drawRoundRect(
            housingRect,
            dp(4f),
            dp(4f),
            borderPaint
        )

        // -----------------------------------------------------
        // 4. Connector neck
        // -----------------------------------------------------

        neckRect.set(
            dp(29f),
            dp(8f),
            w - dp(5f),
            h - dp(8f)
        )

        bodyPaint.color = 0xFF1C3045.toInt()

        canvas.drawRoundRect(
            neckRect,
            dp(2.5f),
            dp(2.5f),
            bodyPaint
        )

        // Upper metallic reflection
        detailPaint.apply {
            style = Paint.Style.FILL
            color = 0xFFB8D2E5.toInt()
            alpha = if (muted) 45 else 115
        }

        canvas.drawRoundRect(
            RectF(
                neckRect.left + dp(2f),
                neckRect.top + dp(1f),
                neckRect.right - dp(3f),
                neckRect.top + dp(2f)
            ),
            dp(0.7f),
            dp(0.7f),
            detailPaint
        )

        // -----------------------------------------------------
        // 5. Outer connector nose
        // -----------------------------------------------------

        noseRect.set(
            w - dp(13f),
            dp(8f),
            w,
            h - dp(8f)
        )

        bodyPaint.color = when (state) {
            PortState.DISABLED -> 0xFF344252.toInt()
            else -> 0xFF263C52.toInt()
        }

        canvas.drawRoundRect(
            noseRect,
            dp(2.5f),
            dp(2.5f),
            bodyPaint
        )

        shellPaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(0.9f)
            color = color
            alpha = if (muted) 120 else 235
        }

        canvas.drawRoundRect(
            noseRect,
            dp(2.5f),
            dp(2.5f),
            shellPaint
        )

        // Colored nose face
        detailPaint.apply {
            style = Paint.Style.FILL
            color = color
            alpha = when (state) {
                PortState.IDLE -> 155
                PortState.DISABLED -> 65
                else -> 245
            }
        }

        val noseFace = RectF(
            w - dp(5f),
            dp(10f),
            w - dp(1f),
            h - dp(10f)
        )

        canvas.drawRoundRect(
            noseFace,
            dp(1.5f),
            dp(1.5f),
            detailPaint
        )

        // -----------------------------------------------------
        // 6. Shape-specific connector opening
        // -----------------------------------------------------

        val openingWidth = when (shape) {
            PortShape.USB -> dp(5.2f)
            PortShape.USB_C -> dp(5.5f)
            PortShape.COMPACT -> dp(3.5f)
        }

        val openingHeight = when (shape) {
            PortShape.USB -> dp(8f)
            PortShape.USB_C -> dp(6f)
            PortShape.COMPACT -> dp(4.5f)
        }

        slotRect.set(
            w - openingWidth - dp(2f),
            cy - openingHeight / 2f,
            w - dp(1.5f),
            cy + openingHeight / 2f
        )

        slotPaint.apply {
            style = Paint.Style.FILL
            color = 0xFF03080F.toInt()
            alpha = 255
        }

        val openingRadius = when (shape) {
            PortShape.USB -> dp(0.8f)
            PortShape.USB_C -> dp(2.2f)
            PortShape.COMPACT -> dp(1.4f)
        }

        canvas.drawRoundRect(
            slotRect,
            openingRadius,
            openingRadius,
            slotPaint
        )

        // Inner colored contact surface
        val insertRect = RectF(
            slotRect.left + dp(1f),
            cy - openingHeight * 0.17f,
            slotRect.right - dp(1f),
            cy + openingHeight * 0.17f
        )

        detailPaint.apply {
            style = Paint.Style.FILL
            color = color
            alpha = if (muted) 105 else 235
        }

        canvas.drawRoundRect(
            insertRect,
            dp(0.7f),
            dp(0.7f),
            detailPaint
        )

        // -----------------------------------------------------
        // 7. Mounting seam
        // -----------------------------------------------------

        seamRect.set(
            dp(29f),
            dp(5f),
            dp(31f),
            h - dp(5f)
        )

        detailPaint.apply {
            style = Paint.Style.FILL
            color = 0xFF050B13.toInt()
            alpha = 210
        }

        canvas.drawRoundRect(
            seamRect,
            dp(0.8f),
            dp(0.8f),
            detailPaint
        )

        // Metallic seam highlight
        detailPaint.color = 0xFF9EB4C8.toInt()
        detailPaint.alpha = if (muted) 45 else 125

        canvas.drawRect(
            dp(31f),
            dp(7f),
            dp(31.7f),
            h - dp(7f),
            detailPaint
        )

        // -----------------------------------------------------
        // 8. Contact details
        // -----------------------------------------------------

        contactPaint.apply {
            style = Paint.Style.FILL
            color = 0xFFD8E7F3.toInt()
            alpha = if (state == PortState.DISABLED) 65 else 210
        }

        val contactRadius = dp(0.7f)

        if (shape == PortShape.USB) {
            canvas.drawCircle(
                slotRect.left + dp(1.5f),
                slotRect.top + dp(1.5f),
                contactRadius,
                contactPaint
            )

            canvas.drawCircle(
                slotRect.left + dp(1.5f),
                slotRect.bottom - dp(1.5f),
                contactRadius,
                contactPaint
            )
        } else if (shape == PortShape.USB_C) {
            canvas.drawCircle(
                slotRect.left + dp(1.1f),
                cy,
                contactRadius,
                contactPaint
            )

            canvas.drawCircle(
                slotRect.right - dp(1.1f),
                cy,
                contactRadius,
                contactPaint
            )
        }

        // -----------------------------------------------------
        // 9. Status indicator
        // -----------------------------------------------------

        ledRect.set(
            dp(34f),
            dp(8f),
            dp(37f),
            dp(11f)
        )

        detailPaint.apply {
            style = Paint.Style.FILL
            color = color
            alpha = when (state) {
                PortState.IDLE -> 115
                PortState.DISABLED -> 35
                else -> 255
            }
        }

        canvas.drawRoundRect(
            ledRect,
            dp(1.2f),
            dp(1.2f),
            detailPaint
        )

        // Small upper edge highlight
        detailPaint.color = Color.WHITE
        detailPaint.alpha = if (muted) 30 else 100

        canvas.drawRect(
            dp(17f),
            dp(4f),
            dp(28f),
            dp(4.7f),
            detailPaint
        )

        // -----------------------------------------------------
        // 10. Active connection indicator
        // -----------------------------------------------------

        if (isActive) {
            detailPaint.apply {
                style = Paint.Style.FILL
                color = Color.WHITE
                alpha = (115 + pulse * 120f).toInt()
            }

            canvas.drawCircle(
                w - dp(3f),
                cy,
                dp(1.05f + pulse * 0.35f),
                detailPaint
            )
        }

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
        pulse = 0f
        super.onDetachedFromWindow()
    }
}

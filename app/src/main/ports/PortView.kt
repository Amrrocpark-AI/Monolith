
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
            field = value
            requestLayout()
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

    private fun dp(value: Float): Float = value * density

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val slotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val contactPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val body = RectF()
    private val slot = RectF()
    private val inner = RectF()

    private val shellPath = Path()

    private var pulse = 0f
    private var animator: ValueAnimator? = null

    private val accent: Int
        get() = PortPalette.colorFor(portKind)

    init {
        isClickable = true
        isFocusable = true

        minimumWidth = dp(30f).toInt()
        minimumHeight = dp(30f).toInt()

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

        // مساحة أكبر لإظهار تفاصيل الموصل
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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val horizontal =
            portSide == PortSide.LEFT ||
            portSide == PortSide.RIGHT

        val save = canvas.save()

        // توحيد نظام الرسم للمنافذ الأربعة
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

        val cx = logicalWidth / 2f
        val cy = logicalHeight / 2f

        // أبعاد جسم الموصل
        val bw = minOf(
            logicalWidth * 0.96f,
            dp(48f)
        )

        val bh = minOf(
            logicalHeight * 0.88f,
            dp(23f)
        )

        body.set(
            cx - bw / 2f,
            cy - bh / 2f,
            cx + bw / 2f,
            cy + bh / 2f
        )

        val color = when (state) {
            PortState.IDLE ->
                0xFF8395AA.toInt()

            PortState.AVAILABLE ->
                accent

            PortState.SELECTED ->
                Color.WHITE

            PortState.CONNECTED ->
                accent

            PortState.ACTIVE ->
                accent

            PortState.DISABLED ->
                0xFF414B59.toInt()
        }

        val active =
            state == PortState.ACTIVE ||
            state == PortState.SELECTED ||
            state == PortState.CONNECTED

        // التوهج الخارجي
        if (active) {
            glowPaint.apply {
                style = Paint.Style.STROKE
                color = color
                strokeWidth = dp(2.5f + pulse * 2f)
                alpha = (55 + pulse * 100).toInt()
            }

            canvas.drawRoundRect(
                body,
                dp(6f),
                dp(6f),
                glowPaint
            )
        }

        // الهيكل الخارجي الداكن
        bodyPaint.apply {
            style = Paint.Style.FILL
            color = when (state) {
                PortState.DISABLED -> 0xFF202936.toInt()
                else -> 0xFF111B2A.toInt()
            }
            alpha = 255
        }

        canvas.drawRoundRect(
            body,
            dp(5f),
            dp(5f),
            bodyPaint
        )

        // الغلاف المعدني الخارجي
        shellPaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(1.6f)
            color = if (state == PortState.DISABLED) {
                0xFF566171.toInt()
            } else {
                0xFF9DADBF.toInt()
            }
            alpha = if (state == PortState.IDLE) 175 else 240
        }

        canvas.drawRoundRect(
            body,
            dp(5f),
            dp(5f),
            shellPaint
        )

        // إطار اللون الخاص بنوع المنفذ
        borderPaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(1.4f)
            color = color
            alpha = if (state == PortState.IDLE) 130 else 255
        }

        val inset = dp(3f)

        inner.set(
            body.left + inset,
            body.top + inset,
            body.right - inset,
            body.bottom - inset
        )

        canvas.drawRoundRect(
            inner,
            dp(3f),
            dp(3f),
            borderPaint
        )

        // فتحة الموصل الداخلية
        val slotWidth = when (shape) {
            PortShape.USB -> bw * 0.66f
            PortShape.USB_C -> bw * 0.62f
            PortShape.COMPACT -> bw * 0.43f
        }

        val slotHeight = when (shape) {
            PortShape.USB -> bh * 0.48f
            PortShape.USB_C -> bh * 0.40f
            PortShape.COMPACT -> bh * 0.36f
        }

        slot.set(
            cx - slotWidth / 2f,
            cy - slotHeight / 2f,
            cx + slotWidth / 2f,
            cy + slotHeight / 2f
        )

        val slotRadius = when (shape) {
            PortShape.USB -> dp(1.5f)
            PortShape.USB_C -> dp(4f)
            PortShape.COMPACT -> dp(2f)
        }

        slotPaint.apply {
            style = Paint.Style.FILL
            color = 0xFF03070D.toInt()
            alpha = 255
        }

        canvas.drawRoundRect(
            slot,
            slotRadius,
            slotRadius,
            slotPaint
        )

        // العازل الداخلي الملون
        val insertHeight = when (shape) {
            PortShape.USB -> slotHeight * 0.30f
            PortShape.USB_C -> slotHeight * 0.48f
            PortShape.COMPACT -> slotHeight * 0.30f
        }

        val insert = RectF(
            slot.left + dp(2f),
            cy - insertHeight / 2f,
            slot.right - dp(2f),
            cy + insertHeight / 2f
        )

        detailPaint.apply {
            style = Paint.Style.FILL
            color = color
            alpha = if (state == PortState.IDLE) 125 else 255
        }

        canvas.drawRoundRect(
            insert,
            dp(1.5f),
            dp(1.5f),
            detailPaint
        )

        // نقاط التلامس المعدنية
        if (shape != PortShape.COMPACT) {
            contactPaint.apply {
                style = Paint.Style.FILL
                color = 0xFFD5E2EF.toInt()
                alpha = if (state == PortState.DISABLED) 70 else 220
            }

            val contactRadius = dp(0.8f)

            if (shape == PortShape.USB) {
                val contactY = slot.top + dp(2f)

                canvas.drawCircle(
                    slot.left + slotWidth * 0.28f,
                    contactY,
                    contactRadius,
                    contactPaint
                )

                canvas.drawCircle(
                    slot.right - slotWidth * 0.28f,
                    contactY,
                    contactRadius,
                    contactPaint
                )
            } else {
                canvas.drawCircle(
                    slot.left + dp(2f),
                    cy,
                    contactRadius,
                    contactPaint
                )

                canvas.drawCircle(
                    slot.right - dp(2f),
                    cy,
                    contactRadius,
                    contactPaint
                )
            }
        }

        // خط إضاءة عند رأس الموصل
        detailPaint.apply {
            style = Paint.Style.FILL
            color = color
            alpha = when (state) {
                PortState.IDLE -> 100
                PortState.DISABLED -> 45
                else -> 255
            }
        }

        val tipWidth = dp(2f)

        canvas.drawRoundRect(
            RectF(
                body.right - dp(2.5f),
                body.top + dp(5f),
                body.right - dp(0.8f),
                body.bottom - dp(5f)
            ),
            tipWidth,
            tipWidth,
            detailPaint
        )

        // نقطة ضوء صغيرة لحالة الاتصال
        if (active) {
            detailPaint.apply {
                color = Color.WHITE
                alpha = (120 + pulse * 135).toInt()
            }

            canvas.drawCircle(
                body.right - dp(5f),
                body.top + dp(4f),
                dp(1.2f),
                detailPaint
            )
        }

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

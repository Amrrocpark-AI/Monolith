package com.monolith

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class LoupeView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var sourceBitmap: Bitmap? = null
    private var imageRect: RectF = RectF()
    
    private var isEnabled = false
    private var isPinned = false
    private var touchX = 0f
    private var touchY = 0f
    private val loupeRadius = 150f
    private val loupeZoom = 3f

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 6f
    }

    private val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00D4FF")
        strokeWidth = 2f
    }

    private val clipPath = Path()

    init {
        isClickable = false
        isFocusable = false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return false
    }

    fun setImage(bitmap: Bitmap?, rect: RectF) {
        sourceBitmap = bitmap
        imageRect = rect
        invalidate()
    }

    fun setLoupeEnabled(enabled: Boolean) {
        isEnabled = enabled
        if (!enabled) isPinned = false
        invalidate()
    }

    fun togglePin() {
        isPinned = !isPinned
        invalidate()
    }

    fun updateTouch(x: Float, y: Float) {
        if (isPinned) return
        touchX = x
        touchY = y
        invalidate()
    }

    fun hide() {
        if (!isPinned) {
            touchX = 0f
            touchY = 0f
            invalidate()
        }
    }

    fun isLoupePinned(): Boolean = isPinned
    fun isLoupeEnabled(): Boolean = isEnabled

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bitmap = sourceBitmap ?: return
        if (!isEnabled || (touchX == 0f && touchY == 0f && !isPinned)) return

        val cx = if (isPinned) width - loupeRadius - 20f else touchX
        val cy = if (isPinned) height - loupeRadius - 20f else touchY - 150f

        canvas.drawCircle(cx, cy, loupeRadius, borderPaint)

        clipPath.reset()
        clipPath.addCircle(cx, cy, loupeRadius, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clipPath)

        canvas.translate(cx, cy)
        canvas.scale(loupeZoom, loupeZoom)
        canvas.translate(-touchX, -touchY)

        canvas.drawBitmap(bitmap, null, imageRect, null)

        canvas.restore()

        canvas.drawLine(cx - 15f, cy, cx + 15f, cy, crossPaint)
        canvas.drawLine(cx, cy - 15f, cx, cy + 15f, crossPaint)
    }
}

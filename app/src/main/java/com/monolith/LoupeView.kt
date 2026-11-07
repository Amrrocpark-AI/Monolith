package com.monolith

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.sqrt

class LoupeView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var sourceBitmap: Bitmap? = null
    private var imageRect: RectF = RectF()
    
    private var isEnabled = false
    private var isDragging = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    
    private var loupeX = 200f
    private var loupeY = 200f
    private var loupeZoom = 3f
    
    var loupeRadius = 150f
        set(value) {
            field = value.coerceIn(80f, 300f)
            invalidate()
        }

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
        isClickable = true
    }

    fun setImage(bitmap: Bitmap?, rect: RectF) {
        sourceBitmap = bitmap
        imageRect = rect
        invalidate()
    }

    fun setLoupeEnabled(enabled: Boolean) {
        isEnabled = enabled
        invalidate()
    }

    fun isLoupeEnabled(): Boolean = isEnabled

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false

        val dx = event.x - loupeX
        val dy = event.y - loupeY
        val distance = sqrt((dx * dx + dy * dy).toDouble())

        if (distance <= loupeRadius) {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isDragging = true
                    lastTouchX = event.x
                    lastTouchY = event.y
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isDragging) {
                        loupeX += event.x - lastTouchX
                        loupeY += event.y - lastTouchY
                        
                        loupeX = loupeX.coerceIn(loupeRadius, width - loupeRadius)
                        loupeY = loupeY.coerceIn(loupeRadius, height - loupeRadius)
                        
                        lastTouchX = event.x
                        lastTouchY = event.y
                        invalidate()
                        return true
                    }
                }
                MotionEvent.ACTION_UP -> {
                    isDragging = false
                    return true
                }
            }
        }
        return false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bitmap = sourceBitmap ?: return
        if (!isEnabled) return

        canvas.drawCircle(loupeX, loupeY, loupeRadius, borderPaint)

        clipPath.reset()
        clipPath.addCircle(loupeX, loupeY, loupeRadius, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clipPath)

        canvas.translate(loupeX, loupeY)
        canvas.scale(loupeZoom, loupeZoom)
        canvas.translate(-loupeX, -loupeY)

        canvas.drawBitmap(bitmap, null, imageRect, null)

        canvas.restore()

        canvas.drawLine(loupeX - 15f, loupeY, loupeX + 15f, loupeY, crossPaint)
        canvas.drawLine(loupeX, loupeY - 15f, loupeX, loupeY + 15f, crossPaint)
    }
}

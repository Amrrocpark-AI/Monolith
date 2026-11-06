package com.monolith

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class SurgicalMaskView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paths = ArrayList<Path>()
    private val redoPaths = ArrayList<Path>()
    private var currentPath: Path? = null

    // فرشاة التحديد (أحمر شفاف)
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.parseColor("#80FF0000") // لون أحمر شفاف 50%
        strokeWidth = 50f
    }

    // الممحاة (تمسح الخطوط الحمراء)
    private val eraserPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 50f
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    var isEraser = false
        set(value) {
            field = value
            invalidate()
        }

    var brushSize = 50f
        set(value) {
            field = value
            maskPaint.strokeWidth = value
            eraserPaint.strokeWidth = value
            invalidate()
        }

    // إضافة الصلابة (Soft Edge) مثل Picsart
    fun updateHardness(hardness: Float) {
        val blurRadius = (100f - hardness) / 100f * (brushSize / 2f)
        if (blurRadius > 0f) {
            maskPaint.maskFilter = BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL)
        } else {
            maskPaint.maskFilter = null
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // نستخدم saveLayer لكي تعمل الممحاة (CLEAR) بشكل صحيح
        canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)

        for (path in paths) {
            canvas.drawPath(path, if (isEraser) eraserPaint else maskPaint)
        }
        
        currentPath?.let {
            canvas.drawPath(it, if (isEraser) eraserPaint else maskPaint)
        }

        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                redoPaths.clear()
                currentPath = Path()
                currentPath?.moveTo(event.x, event.y)
            }
            MotionEvent.ACTION_MOVE -> {
                currentPath?.lineTo(event.x, event.y)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                currentPath?.let { paths.add(it) }
                currentPath = null
            }
        }
        invalidate()
        return true
    }

    fun undo() {
        if (paths.isNotEmpty()) {
            redoPaths.add(paths.removeAt(paths.size - 1))
            invalidate()
        }
    }

    fun redo() {
        if (redoPaths.isNotEmpty()) {
            paths.add(redoPaths.removeAt(redoPaths.size - 1))
            invalidate()
        }
    }

    fun clearAll() {
        paths.clear()
        redoPaths.clear()
        currentPath = null
        invalidate()
    }
}

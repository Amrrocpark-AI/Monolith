package com.monolith

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class MaskDrawingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // أدوات الرسم
    private val drawPaint = Paint().apply {
        color = Color.parseColor("#88FF0000") // أحمر شفاف لتحديد المنطقة
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 50f // الحجم الافتراضي للفرشاة
        isAntiAlias = true
    }

    private val erasePaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) // الممحاة
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 50f
        isAntiAlias = true
    }

    private var currentPath = Path()
    private val paths = mutableListOf<Path>() // لحفظ المسارات (للتراجع)
    private val undonePaths = mutableListOf<Path>() // للإعادة
    private var isEraserMode = false

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // رسم كل المسارات المحفوظة
        for (path in paths) {
            canvas.drawPath(path, if (isEraserMode) drawPaint else drawPaint)
        }
        // رسم المسار الحالي
        canvas.drawPath(currentPath, if (isEraserMode) erasePaint else drawPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                currentPath.moveTo(x, y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                currentPath.lineTo(x, y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                paths.add(currentPath)
                undonePaths.clear() // أي رسم جديد يمسح سجل الإعادة
                currentPath = Path() // مسار جديد للخطوة التالية
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    // دوال التحكم
    fun setBrushSize(size: Float) {
        drawPaint.strokeWidth = size
        erasePaint.strokeWidth = size
    }

    fun setEraserMode(isEraser: Boolean) {
        isEraserMode = isEraser
    }

    fun undo() {
        if (paths.isNotEmpty()) {
            undonePaths.add(paths.removeAt(paths.size - 1))
            invalidate()
        }
    }

    fun redo() {
        if (undonePaths.isNotEmpty()) {
            paths.add(undonePaths.removeAt(undonePaths.size - 1))
            invalidate()
        }
    }

    fun clearMask() {
        paths.clear()
        undonePaths.clear()
        currentPath.reset()
        invalidate()
    }

    // دالة حاسمة: تحويل الرسم إلى صورة شفافة (Mask) لكي يستخدمها محرك التشتت
    fun getMaskBitmap(): Bitmap? {
        if (width == 0 || height == 0) return null
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        for (path in paths) {
            canvas.drawPath(path, drawPaint)
        }
        return bitmap
    }
}

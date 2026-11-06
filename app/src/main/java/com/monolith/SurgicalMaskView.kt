package com.monolith

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import java.util.LinkedList

class SurgicalMaskView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paths = ArrayList<Path>()
    private val redoPaths = ArrayList<Path>()
    private var currentPath: Path? = null

    // إضافات جديدة للعصا السحرية والقناع
    private var sourceBitmap: Bitmap? = null
    private var maskBitmap: Bitmap? = null
    private val canvasMask = Canvas()
    var isMagicWand = false
    var tolerance = 30 // قيمة التسامح اللوني للعصا السحرية

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

    fun updateHardness(hardness: Float) {
        val blurRadius = (100f - hardness) / 100f * (brushSize / 2f)
        if (blurRadius > 0f) {
            maskPaint.maskFilter = BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL)
        } else {
            maskPaint.maskFilter = null
        }
        invalidate()
    }

    // دالة إضافية: تهيئة القناع عند تحميل الصورة
    fun setImageBitmap(bitmap: Bitmap) {
        sourceBitmap = bitmap
        maskBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        canvasMask.setBitmap(maskBitmap!!)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)

        // رسم الخطوط العادية
        for (path in paths) {
            canvas.drawPath(path, if (isEraser) eraserPaint else maskPaint)
        }
        
        currentPath?.let {
            canvas.drawPath(it, if (isEraser) eraserPaint else maskPaint)
        }

        // رسم القناع البكسل (للعصا السحرية) إذا وجد
        maskBitmap?.let { mask ->
            val displayMask = Bitmap.createBitmap(mask.width, mask.height, Bitmap.Config.ARGB_8888)
            val c = Canvas(displayMask)
            c.drawBitmap(mask, 0f, 0f, null)
            val redPaint = Paint()
            redPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            redPaint.color = Color.parseColor("#80FF0000")
            c.drawRect(0f, 0f, mask.width.toFloat(), mask.height.toFloat(), redPaint)
            canvas.drawBitmap(displayMask, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), null)
        }

        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isMagicWand && event.action == MotionEvent.ACTION_DOWN) {
            val src = sourceBitmap ?: return false
            val scaleX = src.width.toFloat() / width.toFloat()
            val scaleY = src.height.toFloat() / height.toFloat()
            applyMagicWand((event.x * scaleX).toInt(), (event.y * scaleY).toInt())
            return true
        }

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

    // دالة إضافية: العصا السحرية
    private fun applyMagicWand(startX: Int, startY: Int) {
        val src = sourceBitmap ?: return
        val mask = maskBitmap ?: return
        if (startX < 0 || startX >= src.width || startY < 0 || startY >= src.height) return
        
        val targetColor = src.getPixel(startX, startY)
        val width = src.width
        val height = src.height
        val queue = LinkedList<Point>()
        val visited = BooleanArray(width * height)
        queue.add(Point(startX, startY))
        
        while (queue.isNotEmpty()) {
            val p = queue.removeFirst()
            val index = p.y * width + p.x
            if (visited[index]) continue
            visited[index] = true

            if (colorDistance(src.getPixel(p.x, p.y), targetColor) <= tolerance) {
                mask.setPixel(p.x, p.y, Color.WHITE)
                if (p.x > 0) queue.add(Point(p.x - 1, p.y))
                if (p.x < width - 1) queue.add(Point(p.x + 1, p.y))
                if (p.y > 0) queue.add(Point(p.x, p.y - 1))
                if (p.y < height - 1) queue.add(Point(p.x, p.y + 1))
            }
        }
        invalidate()
    }

    private fun colorDistance(c1: Int, c2: Int): Int {
        val r1 = Color.red(c1); val g1 = Color.green(c1); val b1 = Color.blue(c1)
        val r2 = Color.red(c2); val g2 = Color.green(c2); val b2 = Color.blue(c2)
        return Math.sqrt(((r1 - r2) * (r1 - r2) + (g1 - g2) * (g1 - g2) + (b1 - b2) * (b1 - b2)).toDouble()).toInt()
    }

    // دالة إضافية: تصدير PNG
    fun exportPNG(): Bitmap? {
        val src = sourceBitmap ?: return null
        val mask = maskBitmap ?: return null
        val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(src, 0f, 0f, null)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        canvas.drawBitmap(mask, 0f, 0f, paint)
        return result
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
        maskBitmap?.eraseColor(Color.TRANSPARENT) // إضافة مسح القناع
        invalidate()
    }
}

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

    private var sourceBitmap: Bitmap? = null
    private var maskBitmap: Bitmap? = null
    private val canvasMask = Canvas()
    
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val eraserPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var currentPath: Path? = null

    var isDrawingEnabled = false
    var isEraser = false
    var isMagicWand = false
    var tolerance = 30 

    var brushSize = 50f
        set(value) {
            field = value
            maskPaint.strokeWidth = value
            eraserPaint.strokeWidth = value
            invalidate()
        }

    init {
        maskPaint.style = Paint.Style.STROKE
        maskPaint.strokeCap = Paint.Cap.ROUND
        maskPaint.strokeJoin = Paint.Join.ROUND
        maskPaint.color = Color.WHITE // نرسم باللون الأبيض على القناع المخفي

        eraserPaint.style = Paint.Style.STROKE
        eraserPaint.strokeCap = Paint.Cap.ROUND
        eraserPaint.strokeJoin = Paint.Join.ROUND
        eraserPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    fun setImageBitmap(bitmap: Bitmap) {
        sourceBitmap = bitmap
        maskBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        canvasMask.setBitmap(maskBitmap!!)
        isDrawingEnabled = true
        invalidate()
    }

    fun hasMask(): Boolean {
        // التحقق من وجود أي بكسل غير شفاف في القناع
        val pixels = IntArray(maskBitmap?.width ?: 0 * maskBitmap?.height ?: 0)
        maskBitmap?.getPixels(pixels, 0, maskBitmap!!.width, 0, 0, maskBitmap!!.width, maskBitmap!!.height)
        return pixels.any { it != Color.TRANSPARENT }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val src = sourceBitmap ?: return
        val mask = maskBitmap ?: return

        val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())

        // 1. رسم خلفية الشطرنج (لرؤية الشفافية)
        drawCheckerboard(canvas, rect)

        // 2. رسم الصورة الأصلية
        canvas.drawBitmap(src, null, rect, null)

        // 3. رسم القناع (اللون الأحمر)
        val displayMask = Bitmap.createBitmap(mask.width, mask.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(displayMask)
        c.drawBitmap(mask, 0f, 0f, null)
        val redPaint = Paint()
        redPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        redPaint.color = Color.parseColor("#80FF0000")
        c.drawRect(0f, 0f, mask.width.toFloat(), mask.height.toFloat(), redPaint)
        canvas.drawBitmap(displayMask, null, rect, null)
    }

    private fun drawCheckerboard(canvas: Canvas, rect: RectF) {
        val paint = Paint()
        val size = 40f
        for (i in 0..(rect.width() / size).toInt()) {
            for (j in 0..(rect.height() / size).toInt()) {
                paint.color = if ((i + j) % 2 == 0) Color.parseColor("#CCCCCC") else Color.WHITE
                canvas.drawRect(rect.left + i * size, rect.top + j * size, rect.left + (i + 1) * size, rect.top + (j + 1) * size, paint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isDrawingEnabled) return false
        
        val src = sourceBitmap ?: return false
        val scaleX = src.width.toFloat() / width.toFloat()
        val scaleY = src.height.toFloat() / height.toFloat()
        val x = event.x * scaleX
        val y = event.y * scaleY

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (isMagicWand) {
                    applyMagicWand(x.toInt(), y.toInt())
                    return true
                }
                currentPath = Path()
                currentPath?.moveTo(x, y)
                // رسم نقطة البداية
                val p = if (isEraser) eraserPaint else maskPaint
                p.strokeWidth = brushSize * scaleX
                canvasMask.drawPoint(x, y, p)
            }
            MotionEvent.ACTION_MOVE -> {
                if (isMagicWand) return true
                currentPath?.lineTo(x, y)
                val p = if (isEraser) eraserPaint else maskPaint
                p.strokeWidth = brushSize * scaleX
                canvasMask.drawPath(currentPath!!, p)
                currentPath = Path()
                currentPath?.moveTo(x, y)
            }
            MotionEvent.ACTION_UP -> {
                currentPath = null
            }
        }
        invalidate()
        return true
    }

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
        
        val paint = Paint()
        paint.color = Color.WHITE

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

    // دالة تطبيق القص فوراً داخل الشاشة
    fun applyClip() {
        val src = sourceBitmap ?: return
        val mask = maskBitmap ?: return
        val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(src, 0f, 0f, null)
        
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        canvas.drawBitmap(mask, 0f, 0f, paint)
        
        // تحديث الصورة الأصلية لتصبح هي النتيجة المقصوصة
        sourceBitmap = result
        // مسح القناع
        maskBitmap?.eraseColor(Color.TRANSPARENT)
        invalidate()
    }

    // دالة استخراج النتيجة للحفظ
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
}

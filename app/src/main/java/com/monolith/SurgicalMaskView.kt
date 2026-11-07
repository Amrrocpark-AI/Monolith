package com.monolith

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.Magnifier
import java.io.ByteArrayOutputStream
import java.util.LinkedList
import android.util.Base64
import kotlin.math.sqrt

// [جديد] كلاس لتخزين قواعد الألوان (يوضع هنا لتجنب مشاكل الاستيراد)
data class ColorRule(val color: Int, val isActive: Boolean, val tolerance: Int)

class SurgicalMaskView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var sourceBitmap: Bitmap? = null
    private var maskBitmap: Bitmap? = null
    private var protectionBitmap: Bitmap? = null
    
    private val canvasMask = Canvas()
    private val canvasProtection = Canvas()
    
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val eraserPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val protectionPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    private var currentPath: Path? = null
    private var magnifier: Magnifier? = null
    
    // متغيرات الشعار
    private var overlayBitmap: Bitmap? = null
    private var overlayX = 0f
    private var overlayY = 0f
    private var isOverlayVisible = true
    private var isDraggingOverlay = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    // [جديد] متغيرات محرك الألوان
    private var activeColorRules: List<ColorRule> = emptyList()

    // نظام التراجع
    private data class HistoryState(
        val mask: Bitmap,
        val protection: Bitmap,
        val overlayX: Float,
        val overlayY: Float,
        val hasOverlay: Boolean
    )
    private val undoStack = ArrayList<HistoryState>()
    private val redoStack = ArrayList<HistoryState>()
    private val maxHistory = 30

    var isDrawingEnabled = false
    var isEraser = false
    var isMagicWand = false
    var isProtectionMode = false
    var tolerance = 30 

    var brushSize = 50f
        set(value) {
            field = value
            maskPaint.strokeWidth = value
            eraserPaint.strokeWidth = value
            protectionPaint.strokeWidth = value
            invalidate()
        }

    init {
        maskPaint.style = Paint.Style.STROKE
        maskPaint.strokeCap = Paint.Cap.ROUND
        maskPaint.strokeJoin = Paint.Join.ROUND
        maskPaint.color = Color.WHITE

        eraserPaint.style = Paint.Style.STROKE
        eraserPaint.strokeCap = Paint.Cap.ROUND
        eraserPaint.strokeJoin = Paint.Join.ROUND
        eraserPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)

        protectionPaint.style = Paint.Style.STROKE
        protectionPaint.strokeCap = Paint.Cap.ROUND
        protectionPaint.strokeJoin = Paint.Join.ROUND
        protectionPaint.color = Color.WHITE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            magnifier = Magnifier.Builder(context)
                .setInitialZoom(2.0f)
                .setSize(300, 300)
                .build()
        }
    }

    fun setImageBitmap(bitmap: Bitmap) {
        sourceBitmap = bitmap
        maskBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        protectionBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        
        canvasMask.setBitmap(maskBitmap!!)
        canvasProtection.setBitmap(protectionBitmap!!)
        
        undoStack.clear()
        redoStack.clear()
        saveHistoryState()
        
        isDrawingEnabled = true
        invalidate()
    }

    // [جديد] دالة استقبال قواعد الألوان من النافذة
    fun setColorRules(rules: List<ColorRule>) {
        activeColorRules = rules
        invalidate()
    }

    fun setOverlayBitmap(bitmap: Bitmap) {
        saveHistoryState()
        overlayBitmap = bitmap
        overlayX = (width - bitmap.width) / 2f
        overlayY = (height - bitmap.height) / 2f
        isOverlayVisible = true
        invalidate()
    }

    fun hasMask(): Boolean {
        val mask = maskBitmap ?: return false
        val w = mask.width
        val h = mask.height
        val pixels = IntArray(w * h)
        mask.getPixels(pixels, 0, w, 0, 0, w, h)
        return pixels.any { it != Color.TRANSPARENT }
    }

    fun undo() {
        if (undoStack.size > 1) {
            val currentState = undoStack.removeAt(undoStack.size - 1)
            redoStack.add(currentState)
            
            val previousState = undoStack.last()
            maskBitmap = previousState.mask.copy(Bitmap.Config.ARGB_8888, true)
            protectionBitmap = previousState.protection.copy(Bitmap.Config.ARGB_8888, true)
            
            canvasMask.setBitmap(maskBitmap!!)
            canvasProtection.setBitmap(protectionBitmap!!)
            
            overlayX = previousState.overlayX
            overlayY = previousState.overlayY
            isOverlayVisible = previousState.hasOverlay
            
            invalidate()
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val nextState = redoStack.removeAt(redoStack.size - 1)
            undoStack.add(nextState)
            
            maskBitmap = nextState.mask.copy(Bitmap.Config.ARGB_8888, true)
            protectionBitmap = nextState.protection.copy(Bitmap.Config.ARGB_8888, true)
            
            canvasMask.setBitmap(maskBitmap!!)
            canvasProtection.setBitmap(protectionBitmap!!)
            
            overlayX = nextState.overlayX
            overlayY = nextState.overlayY
            isOverlayVisible = nextState.hasOverlay
            
            invalidate()
        }
    }

    private fun saveHistoryState() {
        val mask = maskBitmap ?: return
        val protection = protectionBitmap ?: return
        if (undoStack.size >= maxHistory) {
            undoStack.removeAt(0)
        }
        undoStack.add(HistoryState(
            mask = mask.copy(Bitmap.Config.ARGB_8888, true),
            protection = protection.copy(Bitmap.Config.ARGB_8888, true),
            overlayX = overlayX,
            overlayY = overlayY,
            hasOverlay = isOverlayVisible
        ))
        redoStack.clear()
    }

    private fun getImageRect(): RectF {
        val src = sourceBitmap ?: return RectF(0f, 0f, width.toFloat(), height.toFloat())
        val srcRatio = src.width.toFloat() / src.height.toFloat()
        val viewRatio = width.toFloat() / height.toFloat()
        val rect = RectF()
        if (srcRatio > viewRatio) {
            val w = width.toFloat()
            val h = w / srcRatio
            rect.set(0f, (height - h) / 2f, w, (height + h) / 2f)
        } else {
            val h = height.toFloat()
            val w = h * srcRatio
            rect.set((width - w) / 2f, 0f, (width + w) / 2f, h)
        }
        return rect
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val src = sourceBitmap ?: return
        val mask = maskBitmap ?: return
        val protection = protectionBitmap ?: return

        val rect = getImageRect()

        drawCheckerboard(canvas, rect)
        canvas.drawBitmap(src, null, rect, null)

        if (isOverlayVisible) {
            overlayBitmap?.let { canvas.drawBitmap(it, overlayX, overlayY, null) }
        }

        // رسم القناع (اللون الأحمر)
        val redFilter = Paint().apply {
            colorFilter = PorterDuffColorFilter(Color.argb(100, 255, 0, 0), PorterDuff.Mode.SRC_IN)
        }
        canvas.drawBitmap(mask, null, rect, redFilter)

        // [تعديل] رسم طبقة الحماية (اللون البنفسجي/الماجنتا بدلاً من الأخضر)
        val magentaFilter = Paint().apply {
            colorFilter = PorterDuffColorFilter(Color.argb(100, 255, 0, 255), PorterDuff.Mode.SRC_IN)
        }
        canvas.drawBitmap(protection, null, rect, magentaFilter)
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
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> magnifier?.show(event.x, event.y)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> magnifier?.dismiss()
            }
        }

        val src = sourceBitmap ?: return false
        val rect = getImageRect()
        if (!rect.contains(event.x, event.y)) return false

        val scaleX = src.width.toFloat() / rect.width()
        val scaleY = src.height.toFloat() / rect.height()
        val x = (event.x - rect.left) * scaleX
        val y = (event.y - rect.top) * scaleY

        val overlay = overlayBitmap
        if (overlay != null && isOverlayVisible) {
            val isInside = event.x >= overlayX && event.x <= overlayX + overlay.width &&
                           event.y >= overlayY && event.y <= overlayY + overlay.height

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (isInside) {
                        saveHistoryState()
                        isDraggingOverlay = true
                        lastTouchX = event.x
                        lastTouchY = event.y
                        return true 
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isDraggingOverlay) {
                        overlayX += event.x - lastTouchX
                        overlayY += event.y - lastTouchY
                        lastTouchX = event.x
                        lastTouchY = event.y
                        invalidate()
                        return true
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (isDraggingOverlay) {
                        isDraggingOverlay = false
                        return true
                    }
                }
            }
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                saveHistoryState()
                if (isMagicWand) {
                    applyMagicWand(x.toInt(), y.toInt())
                    return true
                }
                val targetCanvas = if (isProtectionMode) canvasProtection else canvasMask
                val targetPaint = if (isProtectionMode) protectionPaint else if (isEraser) eraserPaint else maskPaint
                
                currentPath = Path()
                currentPath?.moveTo(x, y)
                targetPaint.strokeWidth = brushSize * scaleX
                targetCanvas.drawPoint(x, y, targetPaint)
            }
            MotionEvent.ACTION_MOVE -> {
                if (isMagicWand) return true
                val targetCanvas = if (isProtectionMode) canvasProtection else canvasMask
                val targetPaint = if (isProtectionMode) protectionPaint else if (isEraser) eraserPaint else maskPaint
                
                currentPath?.lineTo(x, y)
                targetPaint.strokeWidth = brushSize * scaleX
                targetCanvas.drawPath(currentPath!!, targetPaint)
                currentPath = Path()
                currentPath?.moveTo(x, y)
            }
            MotionEvent.ACTION_UP -> currentPath = null
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
        return sqrt(((r1 - r2) * (r1 - r2) + (g1 - g2) * (g1 - g2) + (b1 - b2) * (b1 - b2)).toDouble()).toInt()
    }

    // [جديد] محرك مسح الألوان الجراحي
    private fun applyColorRulesToBitmap(src: Bitmap): Bitmap {
        if (activeColorRules.isEmpty() || activeColorRules.none { it.isActive }) return src

        val width = src.width
        val height = src.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        val srcPixels = IntArray(width * height)
        val resultPixels = IntArray(width * height)
        src.getPixels(srcPixels, 0, width, 0, 0, width, height)

        val maskPixels = IntArray(width * height)
        maskBitmap?.getPixels(maskPixels, 0, width, 0, 0, width, height)
        
        val protPixels = IntArray(width * height)
        protectionBitmap?.getPixels(protPixels, 0, width, 0, 0, width, height)

        val maskHasPixels = hasMask() // هل رسم المستخدم أي شيء؟

        for (i in 0 until width * height) {
            val pixel = srcPixels[i]
            if (Color.alpha(pixel) == 0) {
                resultPixels[i] = pixel
                continue
            }

            // 1. التحقق من جدار الحماية (إذا كان محمياً، لا تلمسه)
            if (Color.alpha(protPixels[i]) > 0) {
                resultPixels[i] = pixel
                continue
            }

            // 2. التحقق من الرسم اليدوي (إذا رسم المستخدم، امسح فقط داخل رسمه)
            if (maskHasPixels && Color.alpha(maskPixels[i]) == 0) {
                resultPixels[i] = pixel
                continue
            }

            // 3. التحقق من الألوان المحددة
            var shouldErase = false
            for (rule in activeColorRules) {
                if (rule.isActive) {
                    val scaledTolerance = (rule.tolerance / 100f) * 441f
                    if (colorDistance(pixel, rule.color) <= scaledTolerance) {
                        shouldErase = true
                        break
                    }
                }
            }

            resultPixels[i] = if (shouldErase) Color.TRANSPARENT else pixel
        }

        result.setPixels(resultPixels, 0, width, 0, 0, width, height)
        return result
    }

    fun applyClip() {
        val src = sourceBitmap ?: return
        val mask = maskBitmap ?: return
        val protection = protectionBitmap ?: return
        saveHistoryState()
        
        // 1. تطبيق الألوان أولاً
        val colorProcessedBitmap = applyColorRulesToBitmap(src)
        
        val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(colorProcessedBitmap, 0f, 0f, null)
        
        // 2. تطبيق القناع اليدوي (الأحمر)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        canvas.drawBitmap(mask, 0f, 0f, paint)
        
        sourceBitmap = result
        maskBitmap?.eraseColor(Color.TRANSPARENT)
        protectionBitmap?.eraseColor(Color.TRANSPARENT)
        
        saveHistoryState()
        invalidate()
    }

    fun exportPNG(): Bitmap? {
        val src = sourceBitmap ?: return null
        val mask = maskBitmap ?: return null
        val protection = protectionBitmap ?: return null
        
        // 1. تطبيق الألوان
        val colorProcessedBitmap = applyColorRulesToBitmap(src)

        val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(colorProcessedBitmap, 0f, 0f, null)
        
        // 2. تطبيق القناع اليدوي
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        canvas.drawBitmap(mask, 0f, 0f, paint)

        // 3. إعادة رسم منطقة الحماية (لأننا قد نكون مسحناها باللون)
        val tempProtection = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val tempCanvas = Canvas(tempProtection)
        tempCanvas.drawBitmap(colorProcessedBitmap, 0f, 0f, null)
        val pPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        pPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        tempCanvas.drawBitmap(protection, 0f, 0f, pPaint)
        canvas.drawBitmap(tempProtection, 0f, 0f, null)

        // 4. رسم الشعار
        if (isOverlayVisible) {
            overlayBitmap?.let { overlay ->
                val rect = getImageRect()
                val scaleX = src.width.toFloat() / rect.width()
                val scaleY = src.height.toFloat() / rect.height()
                canvas.drawBitmap(overlay, (overlayX - rect.left) * scaleX, (overlayY - rect.top) * scaleY, null)
            }
        }
        
        return result
    }

    fun getMaskAsBase64(): String {
        val mask = maskBitmap ?: return ""
        val outputStream = ByteArrayOutputStream()
        mask.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT)
    }
}

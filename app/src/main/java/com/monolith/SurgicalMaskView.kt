package com.monolith

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import java.util.LinkedList
import kotlin.math.*

data class ColorRule(val color: Int, val isActive: Boolean, val tolerance: Int, val isRecolor: Boolean = false, val targetColor: Int = Color.TRANSPARENT)

class SurgicalMaskView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var sourceBitmap: Bitmap? = null
    private var maskBitmap: Bitmap? = null
    private var protectionBitmap: Bitmap? = null
    private var colorAssistBitmap: Bitmap? = null
    private var aiMaskBitmap: Bitmap? = null

    private val canvasMask = Canvas()
    private val canvasProtection = Canvas()
    private val canvasColorAssist = Canvas()
    private val canvasAiMask = Canvas()

    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val eraserPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val protectionPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val colorAssistPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var currentPath: Path? = null

    private val matrix = Matrix()
    private val inverseMatrix = Matrix()
    private var currentScale = 1f
    private val scaleDetector: ScaleGestureDetector
    private val gestureDetector: GestureDetector

    private var panX = 0f
    private var panY = 0f
    private var lastFocusX = 0f
    private var lastFocusY = 0f

    private var wasPinching = false
    private var isZoomingWithOneFinger = false
    private var lastZoomY = 0f

    private var overlayBitmap: Bitmap? = null
    private var overlayX = 0f
    private var overlayY = 0f
    private var isOverlayVisible = true
    private var isDraggingOverlay = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    private var activeColorRules: List<ColorRule> = emptyList()

    private data class HistoryState(
        val mask: Bitmap,
        val protection: Bitmap,
        val colorAssist: Bitmap,
        val aiMask: Bitmap?,
        val overlayX: Float,
        val overlayY: Float,
        val hasOverlay: Boolean
    )
    private val undoStack = ArrayList<HistoryState>()
    private val redoStack = ArrayList<HistoryState>()
    private val maxHistory = 30

    var isDrawingEnabled = false
    var isFrozen = false
    var isNavigationMode = true
    var isMaskVisible = true
    var isEraser = false
    var isMagicWand = false
    var isProtectionMode = false
    var isColorAssistMode = false
    var tolerance = 30

    var onTouchUpdate: ((Float, Float) -> Unit)? = null
    var onTouchEnd: (() -> Unit)? = null

    var brushSize = 50f
        set(value) {
            field = value
            maskPaint.strokeWidth = value
            eraserPaint.strokeWidth = value
            protectionPaint.strokeWidth = value
            colorAssistPaint.strokeWidth = value
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

        colorAssistPaint.style = Paint.Style.STROKE
        colorAssistPaint.strokeCap = Paint.Cap.ROUND
        colorAssistPaint.strokeJoin = Paint.Join.ROUND
        colorAssistPaint.color = Color.WHITE

        scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                currentScale *= detector.scaleFactor
                currentScale = currentScale.coerceIn(0.5f, 10f)
                applyClamping()
                invalidate()
                return true
            }
        })

        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                isZoomingWithOneFinger = true
                lastZoomY = e.y
                return true
            }
        })
    }

    fun setImageBitmap(bitmap: Bitmap) {
        sourceBitmap = bitmap
        maskBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        protectionBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        colorAssistBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        aiMaskBitmap = null

        canvasMask.setBitmap(maskBitmap!!)
        canvasProtection.setBitmap(protectionBitmap!!)
        canvasColorAssist.setBitmap(colorAssistBitmap!!)

        undoStack.clear()
        redoStack.clear()
        saveHistoryState()

        isDrawingEnabled = true
        invalidate()
    }

    fun setAiMask(foregroundBitmap: Bitmap) {
        val w = sourceBitmap?.width ?: foregroundBitmap.width
        val h = sourceBitmap?.height ?: foregroundBitmap.height

        aiMaskBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        canvasAiMask.setBitmap(aiMaskBitmap!!)

        val srcPixels = IntArray(foregroundBitmap.width * foregroundBitmap.height)
        foregroundBitmap.getPixels(srcPixels, 0, foregroundBitmap.width, 0, 0, foregroundBitmap.width, foregroundBitmap.height)

        val maskPixels = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (x < foregroundBitmap.width && y < foregroundBitmap.height) {
                    val alpha = Color.alpha(srcPixels[y * foregroundBitmap.width + x])
                    maskPixels[y * w + x] = if (alpha > 0) Color.WHITE else Color.TRANSPARENT
                } else {
                    maskPixels[y * w + x] = Color.TRANSPARENT
                }
            }
        }

        aiMaskBitmap?.setPixels(maskPixels, 0, w, 0, 0, w, h)
        saveHistoryState()
        invalidate()
    }

    fun getImageBounds(): RectF {
        return getImageRect()
    }

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

    private fun hasProtection(): Boolean {
        val protection = protectionBitmap ?: return false
        val w = protection.width
        val h = protection.height
        val pixels = IntArray(w * h)
        protection.getPixels(pixels, 0, w, 0, 0, w, h)
        return pixels.any { it != Color.TRANSPARENT }
    }

    fun undo() {
        if (undoStack.size > 1) {
            val currentState = undoStack.removeAt(undoStack.size - 1)
            redoStack.add(currentState)
            val previousState = undoStack.last()
            maskBitmap = previousState.mask.copy(Bitmap.Config.ARGB_8888, true)
            protectionBitmap = previousState.protection.copy(Bitmap.Config.ARGB_8888, true)
            colorAssistBitmap = previousState.colorAssist.copy(Bitmap.Config.ARGB_8888, true)
            aiMaskBitmap = previousState.aiMask?.copy(Bitmap.Config.ARGB_8888, true)

            canvasMask.setBitmap(maskBitmap!!)
            canvasProtection.setBitmap(protectionBitmap!!)
            canvasColorAssist.setBitmap(colorAssistBitmap!!)

            if (aiMaskBitmap != null) {
                canvasAiMask.setBitmap(aiMaskBitmap!!)
            }

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
            colorAssistBitmap = nextState.colorAssist.copy(Bitmap.Config.ARGB_8888, true)
            aiMaskBitmap = nextState.aiMask?.copy(Bitmap.Config.ARGB_8888, true)

            canvasMask.setBitmap(maskBitmap!!)
            canvasProtection.setBitmap(protectionBitmap!!)
            canvasColorAssist.setBitmap(colorAssistBitmap!!)

            if (aiMaskBitmap != null) {
                canvasAiMask.setBitmap(aiMaskBitmap!!)
            }

            overlayX = nextState.overlayX
            overlayY = nextState.overlayY
            isOverlayVisible = nextState.hasOverlay
            invalidate()
        }
    }

    private fun saveHistoryState() {
        val mask = maskBitmap ?: return
        val protection = protectionBitmap ?: return
        val colorAssist = colorAssistBitmap ?: return
        if (undoStack.size >= maxHistory) undoStack.removeAt(0)
        undoStack.add(HistoryState(
            mask = mask.copy(Bitmap.Config.ARGB_8888, true),
            protection = protection.copy(Bitmap.Config.ARGB_8888, true),
            colorAssist = colorAssist.copy(Bitmap.Config.ARGB_8888, true),
            aiMask = aiMaskBitmap?.copy(Bitmap.Config.ARGB_8888, true),
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

    private fun applyClamping() {
        val src = sourceBitmap ?: return
        val imgRect = getImageRect()

        matrix.reset()
        matrix.postTranslate(panX, panY)
        matrix.postScale(currentScale, currentScale, width / 2f, height / 2f)

        val mapped = RectF(imgRect)
        matrix.mapRect(mapped)

        var dx = 0f
        var dy = 0f

        if (mapped.width() > width) {
            if (mapped.left > 0) dx = -mapped.left
            if (mapped.right < width) dx = width - mapped.right
        } else {
            dx = (width - mapped.width()) / 2f - mapped.left
        }

        if (mapped.height() > height) {
            if (mapped.top > 0) dy = -mapped.top
            if (mapped.bottom < height) dy = height - mapped.bottom
        } else {
            dy = (height - mapped.height()) / 2f - mapped.top
        }

        if (dx != 0f || dy != 0f) {
            panX += dx
            panY += dy
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val src = sourceBitmap ?: return
        val mask = maskBitmap ?: return
        val protection = protectionBitmap ?: return
        val colorAssist = colorAssistBitmap ?: return

        canvas.save()
        matrix.reset()
        matrix.postTranslate(panX, panY)
        matrix.postScale(currentScale, currentScale, width / 2f, height / 2f)
        canvas.concat(matrix)

        val rect = getImageRect()

        drawCheckerboard(canvas, rect)
        canvas.drawBitmap(src, null, rect, null)

        if (isOverlayVisible) {
            overlayBitmap?.let { canvas.drawBitmap(it, overlayX, overlayY, null) }
        }

        if (isMaskVisible) {
            val redFilter = Paint().apply {
                colorFilter = PorterDuffColorFilter(Color.argb(100, 255, 0, 0), PorterDuff.Mode.SRC_IN)
            }
            canvas.drawBitmap(mask, null, rect, redFilter)

            val magentaFilter = Paint().apply {
                colorFilter = PorterDuffColorFilter(Color.argb(100, 255, 0, 255), PorterDuff.Mode.SRC_IN)
            }
            canvas.drawBitmap(protection, null, rect, magentaFilter)

            val cyanFilter = Paint().apply {
                colorFilter = PorterDuffColorFilter(Color.argb(100, 0, 255, 255), PorterDuff.Mode.SRC_IN)
            }
            canvas.drawBitmap(colorAssist, null, rect, cyanFilter)

            aiMaskBitmap?.let {
                val darkFilter = Paint().apply {
                    colorFilter = PorterDuffColorFilter(Color.argb(100, 0, 0, 0), PorterDuff.Mode.SRC_IN)
                }
                canvas.drawBitmap(it, null, rect, darkFilter)
            }
        }

        canvas.restore()
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

        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastFocusX = event.x
                lastFocusY = event.y

                if (!isNavigationMode && !wasPinching) {
                    saveHistoryState()
                    val src = sourceBitmap ?: return false
                    val rect = getImageRect()
                    matrix.invert(inverseMatrix)
                    val touchPoints = floatArrayOf(event.x, event.y)
                    inverseMatrix.mapPoints(touchPoints)
                    val touchX = touchPoints[0]
                    val touchY = touchPoints[1]

                    if (rect.contains(touchX, touchY)) {
                        val scaleX = src.width.toFloat() / rect.width()
                        val scaleY = src.height.toFloat() / rect.height()
                        val x = (touchX - rect.left) * scaleX
                        val y = (touchY - rect.top) * scaleY
                        currentPath = Path().apply { moveTo(x, y) }
                    }
                }
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                wasPinching = true
                currentPath = null

                if (undoStack.size > 1 && undoStack.size > 1) {
                    val prev = undoStack[undoStack.size - 2]
                    maskBitmap = prev.mask.copy(Bitmap.Config.ARGB_8888, true)
                    canvasMask.setBitmap(maskBitmap!!)
                    invalidate()
                }

                if (event.pointerCount >= 2) {
                    lastFocusX = (event.getX(0) + event.getX(1)) / 2f
                    lastFocusY = (event.getY(0) + event.getY(1)) / 2f
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2 || wasPinching) {
                    var focusX = 0f
                    var focusY = 0f
                    if (event.pointerCount >= 2) {
                        focusX = (event.getX(0) + event.getX(1)) / 2f
                        focusY = (event.getY(0) + event.getY(1)) / 2f
                    } else if (event.pointerCount == 1) {
                        focusX = event.getX(0)
                        focusY = event.getY(0)
                    }

                    if (isZoomingWithOneFinger && !isFrozen) {
                        val dy = event.y - lastZoomY
                        currentScale += dy * 0.005f
                        currentScale = currentScale.coerceIn(0.5f, 10f)
                        lastZoomY = event.y
                    }

                    panX += focusX - lastFocusX
                    panY += focusY - lastFocusY
                    lastFocusX = focusX
                    lastFocusY = focusY
                    applyClamping()
                    invalidate()
                } else if (currentPath != null && !isFrozen && !isMagicWand) {
                    val src = sourceBitmap ?: return false
                    val rect = getImageRect()
                    matrix.invert(inverseMatrix)
                    val touchPoints = floatArrayOf(event.x, event.y)
                    inverseMatrix.mapPoints(touchPoints)
                    val touchX = touchPoints[0]
                    val touchY = touchPoints[1]

                    onTouchUpdate?.invoke(touchX, touchY)

                    if (!rect.contains(touchX, touchY)) return false

                    val scaleX = src.width.toFloat() / rect.width()
                    val scaleY = src.height.toFloat() / rect.height()
                    val x = (touchX - rect.left) * scaleX
                    val y = (touchY - rect.top) * scaleY

                    val targetCanvas = when {
                        isProtectionMode -> canvasProtection
                        isColorAssistMode -> canvasColorAssist
                        else -> canvasMask
                    }

                    val targetPaint = when {
                        isProtectionMode -> protectionPaint
                        isColorAssistMode -> colorAssistPaint
                        isEraser -> eraserPaint
                        else -> maskPaint
                    }

                    currentPath?.lineTo(x, y)
                    targetPaint.strokeWidth = brushSize * scaleX
                    targetCanvas.drawPath(currentPath!!, targetPaint)
                    invalidate()
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                if (event.pointerCount == 2) {
                    val remainingIndex = if (event.actionIndex == 0) 1 else 0
                    lastFocusX = event.getX(remainingIndex)
                    lastFocusY = event.getY(remainingIndex)
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                wasPinching = false
                isZoomingWithOneFinger = false
                currentPath = null
                onTouchEnd?.invoke()
            }
        }
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

            if (deltaE(src.getPixel(p.x, p.y), targetColor) <= (tolerance / 100f * 100f)) {
                mask.setPixel(p.x, p.y, Color.WHITE)
                if (p.x > 0) queue.add(Point(p.x - 1, p.y))
                if (p.x < width - 1) queue.add(Point(p.x + 1, p.y))
                if (p.y > 0) queue.add(Point(p.x, p.y - 1))
                if (p.y < height - 1) queue.add(Point(p.x, p.y + 1))
            }
        }
        invalidate()
    }

    private fun deltaE(c1: Int, c2: Int): Float {
        val lab1 = rgbToLab(c1)
        val lab2 = rgbToLab(c2)
        return sqrt((lab1[0] - lab2[0]).pow(2) + (lab1[1] - lab2[1]).pow(2) + (lab1[2] - lab2[2]).pow(2))
    }

    private fun rgbToLab(color: Int): FloatArray {
        var r = Color.red(color) / 255f
        var g = Color.green(color) / 255f
        var b = Color.blue(color) / 255f

        r = if (r > 0.04045f) ((r + 0.055f) / 1.055f).pow(2.4f) else r / 12.92f
        g = if (g > 0.04045f) ((g + 0.055f) / 1.055f).pow(2.4f) else g / 12.92f
        b = if (b > 0.04045f) ((b + 0.055f) / 1.055f).pow(2.4f) else b / 12.92f

        r *= 100f
        g *= 100f
        b *= 100f

        var x = r * 0.4124f + g * 0.3576f + b * 0.1805f
        var y = r * 0.2126f + g * 0.7152f + b * 0.0722f
        var z = r * 0.0193f + g * 0.1192f + b * 0.9505f

        x /= 95.047f
        y /= 100.000f
        z /= 108.883f

        x = if (x > 0.008856f) x.pow(1f / 3f) else (7.787f * x) + (16f / 116f)
        y = if (y > 0.008856f) y.pow(1f / 3f) else (7.787f * y) + (16f / 116f)
        z = if (z > 0.008856f) z.pow(1f / 3f) else (7.787f * z) + (16f / 116f)

        val l = (116f * y) - 16f
        val a = 500f * (x - y)
        val bVal = 200f * (y - z)

        return floatArrayOf(l, a, bVal)
    }

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

        val assistPixels = IntArray(width * height)
        colorAssistBitmap?.getPixels(assistPixels, 0, width, 0, 0, width, height)

        val maskHasPixels = hasMask()
        val assistHasPixels = colorAssistBitmap?.let {
            val pixels = IntArray(it.width * it.height)
            it.getPixels(pixels, 0, it.width, 0, 0, it.width, it.height)
            pixels.any { p -> p != Color.TRANSPARENT }
        } ?: false

        for (i in 0 until width * height) {
            val pixel = srcPixels[i]
            if (Color.alpha(pixel) == 0) {
                resultPixels[i] = pixel
                continue
            }

            if (Color.alpha(protPixels[i]) > 0) {
                resultPixels[i] = pixel
                continue
            }

            if (maskHasPixels && Color.alpha(maskPixels[i]) == 0) {
                resultPixels[i] = pixel
                continue
            }

            if (assistHasPixels && Color.alpha(assistPixels[i]) == 0) {
                resultPixels[i] = pixel
                continue
            }

            var shouldErase = false
            var recolorTo: Int? = null
            for (rule in activeColorRules) {
                if (rule.isActive) {
                    val maxDeltaE = (rule.tolerance / 100f) * 100f
                    if (deltaE(pixel, rule.color) <= maxDeltaE) {
                        if (rule.isRecolor) {
                            recolorTo = rule.targetColor
                        } else {
                            shouldErase = true
                        }
                        break
                    }
                }
            }

            resultPixels[i] = when {
                recolorTo != null -> recolorTo
                shouldErase -> Color.TRANSPARENT
                else -> pixel
            }
        }

        result.setPixels(resultPixels, 0, width, 0, 0, width, height)
        return result
    }

    fun applyClip() {
        val src = sourceBitmap ?: return
        val mask = maskBitmap ?: return
        val protection = protectionBitmap ?: return
        saveHistoryState()

        val colorProcessedBitmap = applyColorRulesToBitmap(src)

        val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(colorProcessedBitmap, 0f, 0f, null)

        if (hasMask()) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            canvas.drawBitmap(mask, 0f, 0f, paint)
        }

        if (hasProtection()) {
            val tempProtection = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
            val tempCanvas = Canvas(tempProtection)
            tempCanvas.drawBitmap(colorProcessedBitmap, 0f, 0f, null)
            val pPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            pPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            tempCanvas.drawBitmap(protection, 0f, 0f, pPaint)
            canvas.drawBitmap(tempProtection, 0f, 0f, null)
        }

        aiMaskBitmap?.let {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            canvas.drawBitmap(it, 0f, 0f, paint)
        }

        sourceBitmap = result
        maskBitmap?.eraseColor(Color.TRANSPARENT)
        protectionBitmap?.eraseColor(Color.TRANSPARENT)
        colorAssistBitmap?.eraseColor(Color.TRANSPARENT)
        aiMaskBitmap = null

        saveHistoryState()
        invalidate()
    }

    fun exportPNG(): Bitmap? {
        val src = sourceBitmap ?: return null
        val mask = maskBitmap ?: return null
        val protection = protectionBitmap ?: return null

        val colorProcessedBitmap = applyColorRulesToBitmap(src)

        val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(colorProcessedBitmap, 0f, 0f, null)

        if (hasMask()) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            canvas.drawBitmap(mask, 0f, 0f, paint)
        }

        if (hasProtection()) {
            val tempProtection = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
            val tempCanvas = Canvas(tempProtection)
            tempCanvas.drawBitmap(colorProcessedBitmap, 0f, 0f, null)
            val pPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            pPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            tempCanvas.drawBitmap(protection, 0f, 0f, pPaint)
            canvas.drawBitmap(tempProtection, 0f, 0f, null)
        }

        aiMaskBitmap?.let {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            canvas.drawBitmap(it, 0f, 0f, paint)
        }

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
}

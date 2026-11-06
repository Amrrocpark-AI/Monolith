package com.monolith

import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.annotation.RequiresApi
import java.nio.ByteBuffer
import kotlin.math.*

class DispersionView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    companion object {
        const val TOOL_BRUSH = 0
        const val TOOL_SPREAD = 1
        const val TOOL_SIZE = 2
        const val TOOL_DIR = 3
        const val TOOL_FADE = 4
        const val TOOL_BLEND = 5

        val BLEND_NAMES = listOf(
            "عادي", "ضاعف", "ألوان محروقة", "تغميق", "تنوير",
            "الشاشة", "تراكب", "ضوء خافت", "ضوء ساطع", "اختلاف"
        )
        private const val PREVIEW_MAX_SIDE = 900
    }

    private class Stroke(val points: ArrayList<PointF>, val width: Float, val erase: Boolean)

    private class FloatBuf {
        var a = FloatArray(4096)
        var n = 0
        fun add(x: Float, y: Float) {
            if (n + 2 > a.size) a = a.copyOf(a.size * 2)
            a[n++] = x; a[n++] = y
        }
    }

    private var source: Bitmap? = null
    private var previewSrc: Bitmap? = null
    private var preview: Bitmap? = null
    private var dirty = true

    private val strokes = ArrayList<Stroke>()
    private val redoStack = ArrayList<Stroke>()
    private var current: Stroke? = null

    private val values = intArrayOf(50, 50, 50, 0, 25, 0)

    var eraserMode = false
    var onHistoryChanged: (() -> Unit)? = null

    val hasImage: Boolean get() = source != null
    val canUndo: Boolean get() = strokes.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    private val rect = RectF()
    private val cursor = PointF()
    private var showCursor = false
    private var brushPreviewUntil = 0L

    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.WHITE; strokeWidth = 3f
    }
    private val flashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66E53935 }
    private val checkerPaint = Paint().apply {
        val b = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint()
        p.color = 0xFF2A2A2A.toInt(); c.drawRect(0f, 0f, 32f, 32f, p)
        p.color = 0xFF3A3A3A.toInt()
        c.drawRect(0f, 0f, 16f, 16f, p); c.drawRect(16f, 16f, 32f, 32f, p)
        shader = BitmapShader(b, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    fun setImage(bmp: Bitmap) {
        source = bmp
        val s = min(1f, PREVIEW_MAX_SIDE.toFloat() / max(bmp.width, bmp.height))
        previewSrc = if (s < 1f)
            Bitmap.createScaledBitmap(bmp, max(1, (bmp.width * s).roundToInt()), max(1, (bmp.height * s).roundToInt()), true)
        else bmp
        strokes.clear(); redoStack.clear(); current = null
        dirty = true
        onHistoryChanged?.invoke()
        invalidate()
    }

    fun getValue(tool: Int): Int = values[tool]

    fun setValue(tool: Int, v: Int) {
        values[tool] = v
        if (tool == TOOL_BRUSH) {
            brushPreviewUntil = SystemClock.uptimeMillis() + 700
            postInvalidateDelayed(750)
        } else dirty = true
        invalidate()
    }

    fun undo() {
        if (strokes.isEmpty()) return
        redoStack.add(strokes.removeAt(strokes.size - 1))
        dirty = true; invalidate(); onHistoryChanged?.invoke()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        strokes.add(redoStack.removeAt(redoStack.size - 1))
        dirty = true; invalidate(); onHistoryChanged?.invoke()
    }

    fun bake(): Boolean {
        val s = source ?: return false
        if (strokes.isEmpty()) return false
        val result = renderEffect(s)
        if (result != null) {
            setImage(result)
            return true
        }
        return false
    }

    fun exportBitmap(): Bitmap? = source?.let { renderEffect(it) }

    private fun updateImageRect() {
        val s = source ?: return
        val sc = min(width.toFloat() / s.width, height.toFloat() / s.height)
        val w = s.width * sc
        val h = s.height * sc
        rect.set((width - w) / 2f, (height - h) / 2f, (width + w) / 2f, (height + h) / 2f)
    }

    private fun brushWidthNorm() = 0.03f + values[TOOL_BRUSH] / 100f * 0.17f

    override fun onDraw(canvas: Canvas) {
        val ps = previewSrc ?: return
        updateImageRect()
        canvas.drawRect(rect, checkerPaint)
        if (dirty || preview == null) {
            preview = renderEffect(ps)
            dirty = false
        }
        preview?.let { canvas.drawBitmap(it, null, rect, bmpPaint) }

        val r = brushWidthNorm() * rect.width() / 2f
        if (showCursor) canvas.drawCircle(cursor.x, cursor.y, r, cursorPaint)
        if (SystemClock.uptimeMillis() < brushPreviewUntil)
            canvas.drawCircle(rect.centerX(), rect.centerY(), r, flashPaint)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (source == null || !isEnabled) return false
        updateImageRect()
        val nx = ((e.x - rect.left) / rect.width()).coerceIn(0f, 1f)
        val ny = ((e.y - rect.top) / rect.height()).coerceIn(0f, 1f)
        cursor.set(e.x, e.y)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                redoStack.clear()
                val s = Stroke(arrayListOf(PointF(nx, ny)), brushWidthNorm(), eraserMode)
                strokes.add(s); current = s
                showCursor = true; dirty = true
                onHistoryChanged?.invoke()
            }
            MotionEvent.ACTION_MOVE -> {
                current?.points?.add(PointF(nx, ny)); dirty = true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                current = null; showCursor = false
            }
        }
        invalidate()
        return true
    }

    private fun hash(a: Int, b: Int, salt: Int): Float {
        var h = a * 374761393 + b * 668265263 + salt * 1274126177.toInt()
        h = (h xor (h ushr 13)) * 1274126177.toInt()
        h = h xor (h ushr 16)
        return (h and 0xFFFFFF) / 16777215f
    }

    private fun buildMask(w: Int, h: Int): Bitmap {
        val mask = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
        val c = Canvas(mask)
        for (s in strokes) {
            val sw = s.width * w
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                strokeWidth = sw
                color = Color.BLACK
                maskFilter = BlurMaskFilter(max(1f, sw * 0.18f), BlurMaskFilter.Blur.NORMAL)
                if (s.erase) xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            }
            val path = Path()
            val f = s.points[0]
            path.moveTo(f.x * w, f.y * h)
            if (s.points.size == 1) path.lineTo(f.x * w + 0.01f, f.y * h)
            else for (i in 1 until s.points.size) path.lineTo(s.points[i].x * w, s.points[i].y * h)
            c.drawPath(path, p)
        }
        return mask
    }

    private fun maskAt(mb: ByteArray, rowBytes: Int, w: Int, h: Int, x: Float, y: Float): Int {
        val ix = x.toInt().coerceIn(0, w - 1)
        val iy = y.toInt().coerceIn(0, h - 1)
        return mb[iy * rowBytes + ix].toInt() and 0xFF
    }

    private fun applyBlend(p: Paint) {
        val i = values[TOOL_BLEND]
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            p.blendMode = blendModeFor(i)
        } else {
            val m = when (i) {
                1 -> PorterDuff.Mode.MULTIPLY
                3 -> PorterDuff.Mode.DARKEN
                4 -> PorterDuff.Mode.LIGHTEN
                5 -> PorterDuff.Mode.SCREEN
                6 -> PorterDuff.Mode.OVERLAY
                else -> null
            }
            if (m != null) p.xfermode = PorterDuffXfermode(m)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun blendModeFor(i: Int): BlendMode = when (i) {
        1 -> BlendMode.MULTIPLY
        2 -> BlendMode.COLOR_BURN
        3 -> BlendMode.DARKEN
        4 -> BlendMode.LIGHTEN
        5 -> BlendMode.SCREEN
        6 -> BlendMode.OVERLAY
        7 -> BlendMode.SOFT_LIGHT
        8 -> BlendMode.HARD_LIGHT
        9 -> BlendMode.DIFFERENCE
        else -> BlendMode.SRC_OVER
    }

    private fun renderEffect(tex: Bitmap): Bitmap? {
        val w = tex.width
        val h = tex.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawBitmap(tex, 0f, 0f, null)
        if (strokes.isEmpty()) return out

        val mask = buildMask(w, h)
        val rowBytes = mask.rowBytes
        val mb = ByteArray(rowBytes * h)
        mask.copyPixelsToBuffer(ByteBuffer.wrap(mb))
        mask.recycle() // تحسين الذاكرة: تدمير القناع فوراً

        val cell = max(w / 180f, (0.006f + values[TOOL_SIZE] / 100f * 0.04f) * w)
        val cols = ceil(w / cell).toInt()
        val rows = ceil(h / cell).toInt()
        val rad = Math.toRadians(values[TOOL_DIR].toDouble()).toFloat()
        val dx = cos(rad)
        val dy = sin(rad)

        var minP = Float.MAX_VALUE
        var maxP = -Float.MAX_VALUE
        for (row in 0 until rows) for (col in 0 until cols) {
            val cx = (col + 0.5f) * cell
            val cy = (row + 0.5f) * cell
            if (maskAt(mb, rowBytes, w, h, cx, cy) < 13) continue
            val p = cx * dx + cy * dy
            if (p < minP) minP = p
            if (p > maxP) maxP = p
        }
        if (minP > maxP) return out
        val range = max(maxP - minP, 1f)

        val spreadPx = values[TOOL_SPREAD] / 100f * 0.35f * w
        val fadeF = values[TOOL_FADE] / 100f * 0.95f
        val dst = FloatBuf()
        val uv = FloatBuf()
        val hole = FloatBuf()
        val v = FloatArray(6)

        for (row in 0 until rows) for (col in 0 until cols) {
            val cx = (col + 0.5f) * cell
            val cy = (row + 0.5f) * cell
            val m = maskAt(mb, rowBytes, w, h, cx, cy) / 255f
            if (m < 0.05f) continue
            val t = (((cx * dx + cy * dy) - minP) / range).coerceIn(0f, 1f)
            val x0 = col * cell
            val y0 = row * cell
            val x1 = min(x0 + cell, w.toFloat())
            val y1 = min(y0 + cell, h.toFloat())
            val diag = hash(row, col, 0) > 0.5f

            for (k in 0..1) {
                val s = row * 2 + k
                val prob = (0.12f + 0.88f * t.pow(0.9f)) * m
                if (hash(s, col, 1) > prob) continue

                if (diag) {
                    if (k == 0) { v[0] = x0; v[1] = y0; v[2] = x1; v[3] = y0; v[4] = x1; v[5] = y1 }
                    else        { v[0] = x0; v[1] = y0; v[2] = x1; v[3] = y1; v[4] = x0; v[5] = y1 }
                } else {
                    if (k == 0) { v[0] = x0; v[1] = y0; v[2] = x1; v[3] = y0; v[4] = x0; v[5] = y1 }
                    else        { v[0] = x1; v[1] = y0; v[2] = x1; v[3] = y1; v[4] = x0; v[5] = y1 }
                }
                val gx = (v[0] + v[2] + v[4]) / 3f
                val gy = (v[1] + v[3] + v[5]) / 3f

                val dist = spreadPx * t.pow(1.3f) * (0.35f + 0.65f * hash(s, col, 2)) +
                        cell * 0.2f * hash(s, col, 6) * t
                val ang = rad + (hash(s, col, 3) - 0.5f) * 1.2f * (0.4f + 0.6f * t)
                val tx = cos(ang) * dist
                val ty = sin(ang) * dist
                val rot = (hash(s, col, 4) - 0.5f) * 3.14f * t
                val sc = max(0.03f, 1f - fadeF * (0.2f + 0.8f * t) * (0.4f + 0.6f * hash(s, col, 5)))
                val cr = cos(rot)
                val sr = sin(rot)

                for (i in 0 until 3) {
                    val vx = v[i * 2]
                    val vy = v[i * 2 + 1]
                    hole.add(vx, vy)
                    uv.add(vx, vy)
                    val ox = (vx - gx) * sc
                    val oy = (vy - gy) * sc
                    dst.add(gx + ox * cr - oy * sr + tx, gy + ox * sr + oy * cr + ty)
                }
            }
        }
        if (hole.n == 0) return out

        val clearPaint = Paint().apply {
            color = Color.BLACK
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
        c.drawVertices(Canvas.VertexMode.TRIANGLES, hole.n, hole.a, 0, null, 0, null, 0, null, 0, 0, clearPaint)

        val shardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(tex, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        applyBlend(shardPaint)
        c.drawVertices(Canvas.VertexMode.TRIANGLES, dst.n, dst.a, 0, uv.a, 0, null, 0, null, 0, 0, shardPaint)
        return out
    }
}

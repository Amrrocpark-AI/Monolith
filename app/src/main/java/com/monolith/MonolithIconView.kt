
package com.monolith

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import kotlin.math.min

enum class MonolithIcon {
    CORE,
    FILES,
    IMAGE,
    DISPERSION,
    CONTROL,
    VISION,
    AUDIO,
    AGENTS,
    MEMORY,
    DEVICES,
    TOOLS,
    MODEL,
    NETWORK,
    PERFORMANCE,
    DIAGNOSTICS,
    CLOSE
}

class MonolithIconView(
    context: Context,
    var icon: MonolithIcon,
    var tint: Int = Color.rgb(0, 212, 255)
) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.2f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val path = Path()
    private val rect = RectF()

    private var cx = 0f
    private var cy = 0f
    private var unit = 0f

    private fun dp(v: Float): Float =
        v * resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val side = min(width, height).toFloat()
        if (side <= 0f) return

        cx = width / 2f
        cy = height / 2f
        unit = side * 0.34f

        paint.color = tint
        fillPaint.color = tint
        paint.strokeWidth = dp(2.1f)

        canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(unit / 32f, unit / 32f)

        drawOuterFrame(canvas)

        when (icon) {
            MonolithIcon.CORE -> drawCore(canvas)
            MonolithIcon.FILES -> drawFiles(canvas)
            MonolithIcon.IMAGE -> drawImage(canvas)
            MonolithIcon.DISPERSION -> drawDispersion(canvas)
            MonolithIcon.CONTROL -> drawControl(canvas)
            MonolithIcon.VISION -> drawVision(canvas)
            MonolithIcon.AUDIO -> drawAudio(canvas)
            MonolithIcon.AGENTS -> drawAgents(canvas)
            MonolithIcon.MEMORY -> drawMemory(canvas)
            MonolithIcon.DEVICES -> drawDevices(canvas)
            MonolithIcon.TOOLS -> drawTools(canvas)
            MonolithIcon.MODEL -> drawModel(canvas)
            MonolithIcon.NETWORK -> drawNetwork(canvas)
            MonolithIcon.PERFORMANCE -> drawPerformance(canvas)
            MonolithIcon.DIAGNOSTICS -> drawDiagnostics(canvas)
            MonolithIcon.CLOSE -> drawClose(canvas)
        }

        canvas.restore()
    }

    private fun drawOuterFrame(canvas: Canvas) {
        paint.alpha = 95
        paint.strokeWidth = 1.2f

        rect.set(-29f, -29f, 29f, 29f)
        canvas.drawRoundRect(rect, 9f, 9f, paint)

        paint.alpha = 255
        paint.strokeWidth = 2.1f
    }

    private fun line(
        canvas: Canvas,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float
    ) {
        canvas.drawLine(x1, y1, x2, y2, paint)
    }

    private fun circle(
        canvas: Canvas,
        x: Float,
        y: Float,
        radius: Float,
        filled: Boolean = false
    ) {
        if (filled) {
            canvas.drawCircle(x, y, radius, fillPaint)
        } else {
            canvas.drawCircle(x, y, radius, paint)
        }
    }

    private fun box(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        radius: Float = 3f
    ) {
        rect.set(left, top, right, bottom)
        canvas.drawRoundRect(rect, radius, radius, paint)
    }

    private fun drawCore(canvas: Canvas) {
        circle(canvas, 0f, 0f, 17f)
        circle(canvas, 0f, 0f, 10f)
        circle(canvas, 0f, 0f, 3.5f, true)

        line(canvas, -23f, 0f, -17f, 0f)
        line(canvas, 17f, 0f, 23f, 0f)
        line(canvas, 0f, -23f, 0f, -17f)
        line(canvas, 0f, 17f, 0f, 23f)

        circle(canvas, -23f, -23f, 2f, true)
        circle(canvas, 23f, -23f, 2f, true)
        circle(canvas, -23f, 23f, 2f, true)
        circle(canvas, 23f, 23f, 2f, true)
    }

    private fun drawFiles(canvas: Canvas) {
        path.reset()
        path.moveTo(-20f, -14f)
        path.lineTo(-5f, -14f)
        path.lineTo(0f, -8f)
        path.lineTo(20f, -8f)
        path.lineTo(20f, 17f)
        path.lineTo(-20f, 17f)
        path.close()
        canvas.drawPath(path, paint)

        line(canvas, -12f, 0f, 12f, 0f)
        line(canvas, -12f, 7f, 6f, 7f)
    }

    private fun drawImage(canvas: Canvas) {
        box(canvas, -20f, -17f, 20f, 17f, 4f)
        circle(canvas, 9f, -8f, 3f, true)

        path.reset()
        path.moveTo(-16f, 11f)
        path.lineTo(-5f, -2f)
        path.lineTo(2f, 6f)
        path.lineTo(8f, 0f)
        path.lineTo(16f, 11f)
        canvas.drawPath(path, paint)
    }

    private fun drawDispersion(canvas: Canvas) {
        box(canvas, -17f, -17f, 17f, 17f, 4f)

        line(canvas, -7f, -8f, -2f, -2f)
        line(canvas, -2f, -2f, -8f, 4f)

        line(canvas, 4f, -11f, 12f, -17f)
        line(canvas, 7f, -1f, 17f, -1f)
        line(canvas, 4f, 9f, 13f, 16f)

        circle(canvas, -12f, 13f, 2f, true)
        circle(canvas, 16f, 12f, 2f, true)
    }

    private fun drawControl(canvas: Canvas) {
        circle(canvas, 0f, 0f, 19f)
        circle(canvas, 0f, 0f, 12f)

        line(canvas, -13f, -13f, -5f, -5f)
        line(canvas, 13f, -13f, 5f, -5f)
        line(canvas, -13f, 13f, -5f, 5f)
        line(canvas, 13f, 13f, 5f, 5f)

        circle(canvas, 0f, 0f, 4f, true)
    }

    private fun drawVision(canvas: Canvas) {
        path.reset()
        path.moveTo(-23f, 0f)
        path.quadTo(0f, -20f, 23f, 0f)
        path.quadTo(0f, 20f, -23f, 0f)
        canvas.drawPath(path, paint)

        circle(canvas, 0f, 0f, 8f)
        circle(canvas, 0f, 0f, 3f, true)

        line(canvas, 0f, -24f, 0f, -17f)
        line(canvas, 0f, 17f, 0f, 24f)
    }

    private fun drawAudio(canvas: Canvas) {
        path.reset()
        path.moveTo(-19f, -8f)
        path.lineTo(-9f, -8f)
        path.lineTo(1f, -17f)
        path.lineTo(1f, 17f)
        path.lineTo(-9f, 8f)
        path.lineTo(-19f, 8f)
        path.close()
        canvas.drawPath(path, paint)

        val arcs = listOf(12f, 19f, 25f)
        for (r in arcs) {
            rect.set(-r, -r, r, r)
            canvas.drawArc(rect, -55f, 110f, false, paint)
        }
    }

    private fun drawAgents(canvas: Canvas) {
        circle(canvas, 0f, -13f, 6f)
        circle(canvas, -16f, 10f, 5f)
        circle(canvas, 16f, 10f, 5f)

        path.reset()
        path.moveTo(-9f, -5f)
        path.lineTo(-16f, 3f)
        path.moveTo(9f, -5f)
        path.lineTo(16f, 3f)
        path.moveTo(-10f, 14f)
        path.lineTo(10f, 14f)
        canvas.drawPath(path, paint)

        box(canvas, -7f, 19f, 7f, 23f, 2f)
    }

    private fun drawMemory(canvas: Canvas) {
        box(canvas, -18f, -19f, 18f, 19f, 5f)

        line(canvas, -10f, -10f, 10f, -10f)
        line(canvas, -10f, -2f, 10f, -2f)
        line(canvas, -10f, 6f, 5f, 6f)

        circle(canvas, 10f, 13f, 3f, true)
    }

    private fun drawDevices(canvas: Canvas) {
        box(canvas, -19f, -16f, -3f, 16f, 4f)
        box(canvas, 4f, -20f, 20f, 12f, 4f)

        line(canvas, -14f, 11f, -8f, 11f)
        line(canvas, 9f, 7f, 15f, 7f)

        line(canvas, -3f, -7f, 4f, -7f)
        line(canvas, -3f, 7f, 4f, 7f)
    }

    private fun drawTools(canvas: Canvas) {
        box(canvas, -19f, -17f, 19f, 17f, 5f)

        line(canvas, -11f, -8f, 11f, -8f)
        line(canvas, -11f, 0f, 11f, 0f)
        line(canvas, -11f, 8f, 4f, 8f)

        circle(canvas, 13f, 8f, 2.5f, true)
    }

    private fun drawModel(canvas: Canvas) {
        path.reset()
        path.moveTo(0f, -21f)
        path.lineTo(18f, -10f)
        path.lineTo(18f, 10f)
        path.lineTo(0f, 21f)
        path.lineTo(-18f, 10f)
        path.lineTo(-18f, -10f)
        path.close()
        canvas.drawPath(path, paint)

        circle(canvas, 0f, 0f, 7f)
        circle(canvas, 0f, 0f, 2f, true)

        line(canvas, -18f, -10f, -7f, -4f)
        line(canvas, 18f, -10f, 7f, -4f)
        line(canvas, 0f, 21f, 0f, 8f)
    }

    private fun drawNetwork(canvas: Canvas) {
        circle(canvas, 0f, 0f, 7f)
        circle(canvas, -18f, -14f, 4f)
        circle(canvas, 18f, -14f, 4f)
        circle(canvas, -18f, 15f, 4f)
        circle(canvas, 18f, 15f, 4f)

        line(canvas, -6f, -5f, -15f, -11f)
        line(canvas, 6f, -5f, 15f, -11f)
        line(canvas, -6f, 5f, -15f, 12f)
        line(canvas, 6f, 5f, 15f, 12f)
    }

    private fun drawPerformance(canvas: Canvas) {
        val heights = floatArrayOf(8f, 15f, 22f, 12f, 27f, 18f, 10f)

        for (i in heights.indices) {
            val x = -21f + i * 7f
            val h = heights[i]
            box(canvas, x, 15f - h, x + 4f, 15f, 1.5f)
        }

        line(canvas, -24f, 19f, 24f, 19f)
    }

    private fun drawDiagnostics(canvas: Canvas) {
        path.reset()
        path.moveTo(0f, -21f)
        path.lineTo(19f, 15f)
        path.lineTo(-19f, 15f)
        path.close()
        canvas.drawPath(path, paint)

        line(canvas, 0f, -8f, 0f, 5f)
        circle(canvas, 0f, 10f, 1.8f, true)
    }

    private fun drawClose(canvas: Canvas) {
        circle(canvas, 0f, 0f, 21f)
        line(canvas, -8f, -8f, 8f, 8f)
        line(canvas, 8f, -8f, -8f, 8f)
    }
}

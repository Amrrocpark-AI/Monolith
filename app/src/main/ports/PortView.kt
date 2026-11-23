package com.monolith.graph.ports.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import com.monolith.graph.ports.models.*
import kotlin.math.sin

class PortView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class PortFxType {
        NONE,
        SNAP_RING,
        DETACH_RELEASE,
        MICRO_RECOIL,
        SIGNAL_FLASH
    }

    var portId: String = ""
        set(value) { field = value; contentDescription = "منفذ $value"; invalidate() }

    var portSide = PortSide.RIGHT
        set(value) { if (field != value) { field = value; invalidate() } }

    var portKind = PortKind.CONTEXT
        set(value) { if (field != value) { field = value; invalidate() } }

    var shape = PortShape.USB_C
        set(value) { if (field != value) { field = value; invalidate() } }

    var state = PortState.IDLE
        set(value) { if (field != value) { field = value; invalidate() } }

    // لون القابس المتصل (null = غير متصل → LED محايد)
    var plugColor: Int? = null
        set(value) { if (field != value) { field = value; invalidate() } }

    // لون شارة القفل (null = لا يوجد قفل)
    var lockTone: Int? = null
        set(value) { if (field != value) { field = value; invalidate() } }

    var showLabel: Boolean = true
        set(value) { if (field != value) { field = value; invalidate() } }

    var labelText: String = ""
        set(value) { if (field != value) { field = value; invalidate() } }

    var onPortClick: ((PortView) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private fun dp(v: Float): Float = v * density

    // أبعاد المرجع الحرفية: rim 8×22، hole 4×16، LED 4.5×18
    private val rimW = dp(8f)
    private val rimH = dp(22f)
    private val holeW = dp(4f)
    private val holeH = dp(16f)
    private val ledW = dp(4.5f)
    private val ledH = dp(18f)

    private val rimFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.rgb(8, 11, 16)
    }
    private val rimStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.2f)
        color = Color.rgb(42, 53, 72)
    }
    private val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.rgb(2, 3, 5)
    }
    private val ledPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ledStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(0.9f)
    }
    private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(9f)
        textAlign = Paint.Align.CENTER
        color = Color.rgb(100, 116, 139)
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val lockBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.rgb(15, 23, 42)
    }
    private val lockStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.35f)
    }
    private val lockShacklePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.25f)
        strokeCap = Paint.Cap.ROUND
    }
    private val lockBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val fxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
    }

    private val rimRect = RectF()
    private val holeRect = RectF()
    private val ledRect = RectF()
    private val lockBgRect = RectF()
    private val shackleArcRect = RectF()

    private var activeFxType = PortFxType.NONE
    private var fxColor: Int = Color.rgb(56, 189, 248)
    private var fxStartTimeMs: Long = 0L
    private var fxDurationMs: Long = 260L
    private var fxStrength: Float = 0.65f

    init {
        isClickable = true
        isFocusable = true
        minimumWidth = rimW.toInt()
        minimumHeight = rimH.toInt()
        setOnClickListener {
            if (isEnabled && state != PortState.DISABLED) {
                onPortClick?.invoke(this)
            }
        }
    }

    fun triggerPortEffect(
        type: PortFxType,
        color: Int = plugColor ?: Color.rgb(56, 189, 248),
        strength: Float = 0.65f,
        durationMs: Long = if (type == PortFxType.SNAP_RING) 280L else 240L
    ) {
        if (type == PortFxType.NONE) return
        activeFxType = type
        fxColor = color
        fxStrength = strength.coerceIn(0.05f, 1f)
        fxDurationMs = durationMs.coerceAtLeast(60L)
        fxStartTimeMs = android.os.SystemClock.uptimeMillis()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            resolveSize(rimW.toInt(), widthMeasureSpec),
            resolveSize(rimH.toInt(), heightMeasureSpec)
        )
    }

    fun getSocketCenter(): PointF = PointF(width / 2f, height / 2f)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        val cx = width / 2f
        val cy = height / 2f
        val isLeft = portSide == PortSide.LEFT

        var recoilOffsetX = 0f
        var fxProgress: Float? = null
        if (activeFxType != PortFxType.NONE) {
            val elapsed = android.os.SystemClock.uptimeMillis() - fxStartTimeMs
            if (elapsed >= fxDurationMs) {
                activeFxType = PortFxType.NONE
            } else {
                val p = (elapsed.toFloat() / fxDurationMs.toFloat()).coerceIn(0f, 1f)
                fxProgress = p
                if (activeFxType == PortFxType.MICRO_RECOIL) {
                    recoilOffsetX = sin(p * Math.PI).toFloat() * dp(5f) * fxStrength
                }
            }
        }

        val drawCx = cx + recoilOffsetX

        // 1. Rim
        rimRect.set(drawCx - rimW / 2f, cy - rimH / 2f, drawCx + rimW / 2f, cy + rimH / 2f)
        canvas.drawRoundRect(rimRect, dp(2f), dp(2f), rimFillPaint)
        canvas.drawRoundRect(rimRect, dp(2f), dp(2f), rimStrokePaint)

        // 2. Hole
        holeRect.set(drawCx - holeW / 2f, cy - holeH / 2f, drawCx + holeW / 2f, cy + holeH / 2f)
        canvas.drawRoundRect(holeRect, dp(1f), dp(1f), holePaint)

        // 3. LED (left +11، right -15.5)
        val ledX = if (isLeft) drawCx + dp(11f) else drawCx - dp(15.5f)
        ledRect.set(ledX, cy - ledH / 2f, ledX + ledW, cy + ledH / 2f)
        val pc = plugColor
        if (pc != null) {
            ledPaint.color = pc
            ledPaint.alpha = 255
            canvas.drawRoundRect(ledRect, dp(2f), dp(2f), ledPaint)
            ledStrokePaint.strokeWidth = dp(0.9f)
            ledStrokePaint.color = when (pc) {
                0xFF11161f.toInt() -> Color.rgb(71, 85, 105)
                0xFFf8fafc.toInt() -> Color.rgb(100, 116, 139)
                else -> Color.argb(102, 255, 255, 255)
            }
            canvas.drawRoundRect(ledRect, dp(2f), dp(2f), ledStrokePaint)
        } else {
            ledPaint.color = Color.rgb(30, 41, 59)
            ledPaint.alpha = 255
            canvas.drawRoundRect(ledRect, dp(2f), dp(2f), ledPaint)
            ledStrokePaint.strokeWidth = dp(0.8f)
            ledStrokePaint.color = Color.rgb(39, 53, 74)
            canvas.drawRoundRect(ledRect, dp(2f), dp(2f), ledStrokePaint)
        }

        // 4. Label (left +22، right -26، y+3)
        if (showLabel) {
            val label = labelText.ifEmpty { computeLabel(portId) }
            val tx = if (isLeft) drawCx + dp(22f) else drawCx - dp(26f)
            canvas.drawText(label, tx, cy + dp(3f), labelPaint)
        }

        // 5. Lock badge (left -14، right +14، y -14)
        lockTone?.let { tone -> drawLockBadge(canvas, drawCx, cy, isLeft, tone) }

        // 6. Plug / Port effect
        val p = fxProgress
        if (p != null && activeFxType != PortFxType.NONE) {
            drawPortEffect(canvas, drawCx, cy, p, activeFxType)
            postInvalidateOnAnimation()
        }
    }

    private fun drawPortEffect(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        p: Float,
        fxType: PortFxType
    ) {
        val alpha = ((1f - p) * fxStrength * 255f).toInt().coerceIn(0, 255)
        fxPaint.color = fxColor

        when (fxType) {
            PortFxType.SNAP_RING -> {
                val radius = dp(26f) * (1f - p) + dp(5f)
                fxPaint.style = Paint.Style.STROKE
                fxPaint.strokeWidth = dp(2f)
                fxPaint.alpha = alpha
                canvas.drawCircle(cx, cy, radius, fxPaint)
            }
            PortFxType.DETACH_RELEASE -> {
                val radius = dp(7f) + dp(18f) * p
                fxPaint.style = Paint.Style.STROKE
                fxPaint.strokeWidth = dp(1.6f)
                fxPaint.alpha = alpha
                canvas.drawCircle(cx, cy, radius, fxPaint)
            }
            PortFxType.MICRO_RECOIL -> {
                val offset = sin(p * Math.PI).toFloat() * dp(5f)
                fxPaint.style = Paint.Style.STROKE
                fxPaint.strokeWidth = dp(2.2f)
                fxPaint.alpha = alpha
                canvas.drawLine(cx - offset, cy - dp(8f), cx - offset, cy + dp(8f), fxPaint)
            }
            PortFxType.SIGNAL_FLASH -> {
                val r = dp(10f) + dp(6f) * sin(p * Math.PI).toFloat()
                fxPaint.style = Paint.Style.FILL
                fxPaint.alpha = (alpha * 0.45f).toInt().coerceIn(0, 255)
                canvas.drawCircle(cx, cy, r, fxPaint)
            }
            PortFxType.NONE -> Unit
        }
    }

    private fun computeLabel(portId: String): String {
        val match = Regex(":(left|right):(\\d+)$").find(portId) ?: return "?"
        val side = match.groupValues[1]
        val idx = match.groupValues[2].toIntOrNull() ?: return "?"
        val prefix = if (side == "left") "L" else "R"
        return prefix + (idx + 1)
    }

    private fun drawLockBadge(canvas: Canvas, cx: Float, cy: Float, isLeft: Boolean, tone: Int) {
        val lockCx = if (isLeft) cx - dp(14f) else cx + dp(14f)
        val lockCy = cy - dp(14f)

        lockBgRect.set(lockCx - dp(6f), lockCy - dp(6f), lockCx + dp(6f), lockCy + dp(6f))
        canvas.drawRoundRect(lockBgRect, dp(3f), dp(3f), lockBgPaint)
        lockStrokePaint.color = tone
        canvas.drawRoundRect(lockBgRect, dp(3f), dp(3f), lockStrokePaint)

        shackleArcRect.set(
            lockCx - dp(2.2f), lockCy - dp(4.8f),
            lockCx + dp(2.2f), lockCy - dp(0.4f)
        )
        val shackle = Path().apply {
            moveTo(lockCx - dp(2.2f), lockCy - dp(1f))
            lineTo(lockCx - dp(2.2f), lockCy - dp(2.6f))
            arcTo(shackleArcRect, 180f, 180f, false)
            lineTo(lockCx + dp(2.2f), lockCy - dp(1f))
        }
        lockShacklePaint.color = tone
        canvas.drawPath(shackle, lockShacklePaint)

        lockBodyPaint.color = tone
        canvas.drawRoundRect(
            lockCx - dp(2.7f), lockCy - dp(1f),
            lockCx + dp(2.7f), lockCy + dp(3f),
            dp(1f), dp(1f), lockBodyPaint
        )
    }
}

package com.monolith.graph.ports.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import com.monolith.graph.ports.models.*

class ConnectionLayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val connections = LinkedHashMap<String, WireConnection>()
    private val endpoints = HashMap<String, PointF>()
    private val paths = HashMap<String, Path>()
    private val progress = HashMap<String, Float>()
    private val animators = HashMap<String, ValueAnimator>()

    private val density = resources.displayMetrics.density

    private fun dp(value: Float) = value * density

    private val wirePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val pathMeasure = PathMeasure()

    private var selectedId: String? = null
    private var wiresVisible = true
    private var glowEnabled = true
    private var animationEnabled = true
    private var directionVisible = true
    private var controlPointsVisible = false
    private var downX = 0f
    private var downY = 0f

    var onConnectionSelected: ((WireConnection) -> Unit)? = null
    var onConnectionUpdated: ((WireConnection) -> Unit)? = null

    init {
        setWillNotDraw(false)
        isClickable = true
        clipChildren = false
    }

    fun setConnections(items: List<WireConnection>) {
        val oldIds = connections.keys.toSet()
        val newIds = items.map { it.id }.toSet()

        (oldIds - newIds).forEach { stopAnimation(it) }

        connections.clear()
        items.forEach { connections[it.id] = it }

        rebuildPaths()
        updateAnimations()
        invalidate()
    }

    fun getConnections(): List<WireConnection> =
        connections.values.toList()

    fun updateEndpoint(portId: String, x: Float, y: Float) {
        endpoints[portId] = PointF(x, y)
        rebuildPaths()
        invalidate()
    }

    fun updateEndpoints(points: Map<String, PointF>) {
        points.forEach { (id, point) ->
            endpoints[id] = PointF(point.x, point.y)
        }

        rebuildPaths()
        invalidate()
    }

    fun removeEndpoint(portId: String) {
        endpoints.remove(portId)
        rebuildPaths()
        invalidate()
    }

    fun setWiresVisible(value: Boolean) {
        wiresVisible = value
        updateAnimations()
        invalidate()
    }

    fun areWiresVisible() = wiresVisible

    fun setGlowEnabled(value: Boolean) {
        glowEnabled = value
        invalidate()
    }

    fun setAnimationEnabled(value: Boolean) {
        animationEnabled = value
        updateAnimations()
        invalidate()
    }

    fun setDirectionVisible(value: Boolean) {
        directionVisible = value
        invalidate()
    }

    fun setControlPointsVisible(value: Boolean) {
        controlPointsVisible = value
        invalidate()
    }

    fun selectConnection(id: String?) {
        selectedId =
            if (id != null && connections.containsKey(id)) id else null
        invalidate()
    }

    fun getSelectedConnection(): WireConnection? =
        selectedId?.let { connections[it] }

    fun updateControlPoints(
        id: String,
        first: PointF,
        second: PointF
    ) {
        val old = connections[id] ?: return

        val updated = old.copy(
            control1 = PointF(first.x, first.y),
            control2 = PointF(second.x, second.y)
        )

        connections[id] = updated
        rebuildPaths()
        onConnectionUpdated?.invoke(updated)
        invalidate()
    }

    fun updateConnectionColor(id: String, color: Int) {
        val old = connections[id] ?: return
        connections[id] = old.copy(color = color)
        invalidate()
    }

    fun removeConnection(id: String) {
        connections.remove(id)
        paths.remove(id)
        stopAnimation(id)

        if (selectedId == id) selectedId = null

        invalidate()
    }

    private fun rebuildPaths() {
        paths.clear()

        connections.values.forEach { connection ->
            val start = endpoints[connection.sourcePortId] ?: return@forEach
            val end = endpoints[connection.targetPortId] ?: return@forEach

            val dx = end.x - start.x
            val dy = end.y - start.y
            val distance = hypot(dx, dy)

            val offset = (distance * 0.28f).coerceIn(dp(35f), dp(170f))

            val first = connection.control1 ?: PointF(
                start.x + dx * 0.32f,
                start.y - offset
            )

            val second = connection.control2 ?: PointF(
                start.x + dx * 0.68f,
                end.y - offset
            )

            paths[connection.id] = Path().apply {
                moveTo(start.x, start.y)
                cubicTo(
                    first.x, first.y,
                    second.x, second.y,
                    end.x, end.y
                )
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!wiresVisible) return

        connections.values.forEach { connection ->
            if (!connection.visible) return@forEach

            val path = paths[connection.id] ?: return@forEach
            drawWire(canvas, connection, path)
        }
    }

    private fun drawWire(
        canvas: Canvas,
        connection: WireConnection,
        path: Path
    ) {
        val selected = selectedId == connection.id
        val width = dp(connection.strokeWidthDp)

        if (glowEnabled && connection.glowEnabled) {
            glowPaint.color = connection.color
            glowPaint.alpha = if (selected) 115 else 55
            glowPaint.strokeWidth = width * 4f
            canvas.drawPath(path, glowPaint)

            glowPaint.alpha = if (selected) 160 else 90
            glowPaint.strokeWidth = width * 2f
            canvas.drawPath(path, glowPaint)
        }

        wirePaint.color = connection.color
        wirePaint.alpha = 245
        wirePaint.strokeWidth = if (selected) width * 1.5f else width
        canvas.drawPath(path, wirePaint)

        if (animationEnabled && connection.animated) {
            drawPulse(canvas, path, connection)
        }

        if (directionVisible) {
            drawDirection(canvas, path, connection)
        }

        if (controlPointsVisible && selected) {
            drawControls(canvas, connection)
        }
    }

    private fun drawPulse(
        canvas: Canvas,
        path: Path,
        connection: WireConnection
    ) {
        pathMeasure.setPath(path, false)
        val length = pathMeasure.length
        if (length <= 0f) return

        val position = FloatArray(2)
        val fraction = progress[connection.id] ?: 0f

        if (!pathMeasure.getPosTan(length * fraction, position, null)) return

        glowPaint.style = Paint.Style.FILL
        glowPaint.color = connection.color
        glowPaint.alpha = 100

        canvas.drawCircle(
            position[0],
            position[1],
            dp(10f),
            glowPaint
        )

        pulsePaint.color = Color.WHITE
        pulsePaint.alpha = 245

        canvas.drawCircle(
            position[0],
            position[1],
            dp(2.8f),
            pulsePaint
        )

        glowPaint.style = Paint.Style.STROKE
    }

    private fun drawDirection(
        canvas: Canvas,
        path: Path,
        connection: WireConnection
    ) {
        when (connection.direction) {
            ConnectionDirection.BIDIRECTIONAL -> {
                drawArrow(canvas, path, 0.30f, connection.color)
                drawArrow(canvas, path, 0.70f, connection.color)
            }

            ConnectionDirection.OUTGOING ->
                drawArrow(canvas, path, 0.72f, connection.color)

            ConnectionDirection.INCOMING ->
                drawArrow(canvas, path, 0.28f, connection.color)
        }
    }

    private fun drawArrow(
        canvas: Canvas,
        path: Path,
        fraction: Float,
        color: Int
    ) {
        pathMeasure.setPath(path, false)

        val length = pathMeasure.length
        if (length <= 0f) return

        val pos = FloatArray(2)
        val tan = FloatArray(2)

        if (!pathMeasure.getPosTan(length * fraction, pos, tan)) return

        val angle = atan2(tan[1], tan[0])
        val size = dp(8f)
        val spread = Math.toRadians(145.0).toFloat()

        arrowPaint.color = color
        arrowPaint.alpha = 245
        arrowPaint.strokeWidth = dp(1.8f)

        val lx = pos[0] + cos(angle + spread) * size
        val ly = pos[1] + sin(angle + spread) * size
        val rx = pos[0] + cos(angle - spread) * size
        val ry = pos[1] + sin(angle - spread) * size

        canvas.drawLine(pos[0], pos[1], lx, ly, arrowPaint)
        canvas.drawLine(pos[0], pos[1], rx, ry, arrowPaint)
    }

    private fun drawControls(
        canvas: Canvas,
        connection: WireConnection
    ) {
        val start = endpoints[connection.sourcePortId] ?: return
        val end = endpoints[connection.targetPortId] ?: return

        val dx = end.x - start.x
        val dy = end.y - start.y

        val first = connection.control1 ?: PointF(
            start.x + dx * 0.32f,
            start.y - dp(50f)
        )

        val second = connection.control2 ?: PointF(
            start.x + dx * 0.68f,
            end.y - dp(50f)
        )

        arrowPaint.color = connection.color
        arrowPaint.alpha = 120
        arrowPaint.strokeWidth = dp(1f)

        canvas.drawLine(start.x, start.y, first.x, first.y, arrowPaint)
        canvas.drawLine(end.x, end.y, second.x, second.y, arrowPaint)

        pointPaint.color = connection.color
        canvas.drawCircle(first.x, first.y, dp(6f), pointPaint)
        canvas.drawCircle(second.x, second.y, dp(6f), pointPaint)

        pointPaint.color = Color.WHITE
        canvas.drawCircle(first.x, first.y, dp(2.2f), pointPaint)
        canvas.drawCircle(second.x, second.y, dp(2.2f), pointPaint)
    }

    private fun updateAnimations() {
        connections.values.forEach { connection ->
            val shouldRun =
                isAttachedToWindow &&
                wiresVisible &&
                animationEnabled &&
                connection.visible &&
                connection.animated

            if (shouldRun) {
                startAnimation(connection)
            } else {
                stopAnimation(connection.id)
            }
        }
    }

    private fun startAnimation(connection: WireConnection) {
        if (animators.containsKey(connection.id)) return

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = connection.animationDuration.coerceAtLeast(100L)
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()

            addUpdateListener {
                progress[connection.id] = it.animatedValue as Float
                invalidate()
            }
        }

        animators[connection.id] = animator
        animator.start()
    }

    private fun stopAnimation(id: String) {
        animators.remove(id)?.cancel()
        progress.remove(id)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateAnimations()
    }

    override fun onDetachedFromWindow() {
        animators.values.forEach { it.cancel() }
        animators.clear()
        super.onDetachedFromWindow()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y

                return wiresVisible &&
                    findConnectionAt(downX, downY) != null
            }

            MotionEvent.ACTION_UP -> {
                val connection = findConnectionAt(event.x, event.y)
                    ?: return false

                selectedId = connection.id
                onConnectionSelected?.invoke(connection)
                invalidate()
                performClick()
                return true
            }

            MotionEvent.ACTION_CANCEL -> return false
        }

        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun findConnectionAt(x: Float, y: Float): WireConnection? {
        val tolerance = dp(14f)

        return connections.values.toList().asReversed().firstOrNull { connection ->
            if (!connection.visible) return@firstOrNull false

            val path = paths[connection.id] ?: return@firstOrNull false

            pathMeasure.setPath(path, false)
            val length = pathMeasure.length

            if (length <= 0f) return@firstOrNull false

            val position = FloatArray(2)

            for (i in 0..60) {
                val distance = length * i / 60f

                if (!pathMeasure.getPosTan(distance, position, null)) continue

                if (hypot(x - position[0], y - position[1]) <= tolerance) {
                    return@firstOrNull true
                }
            }

            false
        }
    }
}

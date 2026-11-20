package com.monolith.graph.ports.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
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
    private val plugs = LinkedHashMap<String, PlugSpec>()
    private val cables = LinkedHashMap<String, CableSpec>()
    private val endpoints = HashMap<String, PointF>()
    private val paths = HashMap<String, Path>()
    private val progress = HashMap<String, Float>()
    private val animators = HashMap<String, ValueAnimator>()

    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

    private fun dp(value: Float): Float = value * density

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

    private val plugBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val plugRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val plugPinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.SQUARE
    }

    private val plugPath = Path()
    private val neckPath = Path()
    private val pathMeasure = PathMeasure()

    private var selectedId: String? = null
    private var pressedConnectionId: String? = null

    private var wiresVisible = true
    private var glowEnabled = true
    private var animationEnabled = true
    private var directionVisible = true
    private var controlPointsVisible = false

    private var downX = 0f
    private var downY = 0f
    private var touchMoved = false

    var onConnectionSelected: ((WireConnection) -> Unit)? = null
    var onConnectionUpdated: ((WireConnection) -> Unit)? = null

    init {
        setWillNotDraw(false)
        isClickable = true

        // clipChildren غير متاحة هنا لأن هذا الكلاس يرث من View وليس ViewGroup.
        // يجب ضبط القص على الحاوية الأب من نوع ViewGroup عند الحاجة.
    }

    // ---------------------------------------------------------
    // Plugs & Cables (Independent Plug & Unconnected End Support)
    // ---------------------------------------------------------

    fun setPlugs(items: List<PlugSpec>) {
        plugs.clear()
        items.forEach { plug ->
            plugs[plug.id] = plug
        }
        rebuildPaths()
        invalidate()
    }

    fun updatePlug(plug: PlugSpec) {
        plugs[plug.id] = plug
        rebuildPaths()
        invalidate()
    }

    fun removePlug(plugId: String) {
        plugs.remove(plugId)
        rebuildPaths()
        invalidate()
    }

    fun clearPlugs() {
        plugs.clear()
        rebuildPaths()
        invalidate()
    }

    fun setCables(items: List<CableSpec>) {
        cables.clear()
        items.forEach { cable ->
            cables[cable.id] = cable
        }
        rebuildPaths()
        invalidate()
    }

    fun getCables(): List<CableSpec> =
        cables.values.toList()

    fun clearCables() {
        cables.clear()
        rebuildPaths()
        invalidate()
    }

    // ---------------------------------------------------------
    // Connections
    // ---------------------------------------------------------

    fun setConnections(items: List<WireConnection>) {
        val newIds = items.mapTo(HashSet()) { it.id }

        connections.keys
            .filter { it !in newIds }
            .forEach { stopAnimation(it) }

        connections.clear()

        items.forEach { connection ->
            connections[connection.id] = connection
        }

        if (selectedId !in connections.keys) {
            selectedId = null
        }

        rebuildPaths()
        updateAnimations()
        invalidate()
    }

    fun getConnections(): List<WireConnection> =
        connections.values.toList()

    fun removeConnection(id: String) {
        connections.remove(id)
        paths.remove(id)
        stopAnimation(id)

        if (selectedId == id) {
            selectedId = null
        }

        if (pressedConnectionId == id) {
            pressedConnectionId = null
        }

        invalidate()
    }

    fun selectConnection(id: String?) {
        selectedId = id?.takeIf { connections.containsKey(it) }
        invalidate()
    }

    fun getSelectedConnection(): WireConnection? =
        selectedId?.let { connections[it] }

    // ---------------------------------------------------------
    // Endpoints
    // ---------------------------------------------------------

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

    /**
     * Removes all stored endpoint coordinates.
     *
     * Call this when switching conversations or clearing
     * all ports, so old endpoint positions cannot remain.
     */
    fun clearEndpoints() {
        endpoints.clear()
        rebuildPaths()
        invalidate()
    }

    // ---------------------------------------------------------
    // Settings
    // ---------------------------------------------------------

    fun setWiresVisible(value: Boolean) {
        if (wiresVisible == value) return

        wiresVisible = value
        updateAnimations()
        invalidate()
    }

    fun areWiresVisible(): Boolean = wiresVisible

    fun setGlowEnabled(value: Boolean) {
        glowEnabled = value
        invalidate()
    }

    fun setAnimationEnabled(value: Boolean) {
        if (animationEnabled == value) return

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

    // ---------------------------------------------------------
    // Connection editing
    // ---------------------------------------------------------

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

    // ---------------------------------------------------------
    // Path construction
    // ---------------------------------------------------------

    private fun resolveEndpointAnchor(id: String): PointF? {
        plugs[id]?.let { plug ->
            return PointF(plug.x, plug.y)
        }
        return endpoints[id]
    }

    private fun getControlPoints(
        connection: WireConnection,
        start: PointF,
        end: PointF
    ): Pair<PointF, PointF> {

        val dx = end.x - start.x
        val dy = end.y - start.y
        val distance = hypot(dx, dy)

        val offset = (distance * 0.28f)
            .coerceIn(dp(35f), dp(170f))

        val first = connection.control1 ?: PointF(
            start.x + dx * 0.32f,
            start.y - offset
        )

        val second = connection.control2 ?: PointF(
            start.x + dx * 0.68f,
            end.y - offset
        )

        return Pair(first, second)
    }

    private fun getCableControlPoints(
        cable: CableSpec,
        start: PointF,
        end: PointF
    ): Pair<PointF, PointF> {
        val dx = end.x - start.x
        val dy = end.y - start.y
        val distance = hypot(dx, dy)

        val offset = (distance * 0.28f)
            .coerceIn(dp(24f), dp(150f))

        val first = cable.control1 ?: PointF(
            start.x + dx * 0.32f,
            start.y - offset
        )

        val second = cable.control2 ?: PointF(
            start.x + dx * 0.68f,
            end.y + offset * 0.4f
        )

        return Pair(first, second)
    }

    private fun rebuildPaths() {
        paths.clear()

        connections.values.forEach { connection ->
            val start = resolveEndpointAnchor(connection.sourcePortId)
                ?: return@forEach

            val end = resolveEndpointAnchor(connection.targetPortId)
                ?: return@forEach

            val (first, second) = getControlPoints(
                connection,
                start,
                end
            )

            val path = Path().apply {
                moveTo(start.x, start.y)

                cubicTo(
                    first.x,
                    first.y,
                    second.x,
                    second.y,
                    end.x,
                    end.y
                )
            }

            paths[connection.id] = path
        }

        cables.values.forEach { cable ->
            val start = resolveEndpointAnchor(cable.sourcePlugId)
                ?: return@forEach

            val end = cable.targetPlugId?.let { resolveEndpointAnchor(it) }
                ?: PointF(start.x + dp(46f), start.y + dp(28f))

            val (first, second) = getCableControlPoints(
                cable,
                start,
                end
            )

            val path = Path().apply {
                moveTo(start.x, start.y)
                cubicTo(
                    first.x,
                    first.y,
                    second.x,
                    second.y,
                    end.x,
                    end.y
                )
            }

            paths["cable:${cable.id}"] = path
        }
    }

    // ---------------------------------------------------------
    // Drawing
    // ---------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!wiresVisible) return

        connections.values.forEach { connection ->
            if (!connection.visible) return@forEach

            val path = paths[connection.id]
                ?: return@forEach

            drawWire(canvas, connection, path)
        }

        cables.values.forEach { cable ->
            if (!cable.visible) return@forEach

            val path = paths["cable:${cable.id}"]
                ?: return@forEach

            drawCableSpec(canvas, cable, path)
        }

        plugs.values.forEach { plug ->
            drawPlugHead(canvas, plug)
        }
    }

    private fun drawCableSpec(
        canvas: Canvas,
        cable: CableSpec,
        path: Path
    ) {
        val width = dp(cable.strokeWidthDp)

        if (glowEnabled && cable.glowEnabled) {
            glowPaint.style = Paint.Style.STROKE
            glowPaint.color = cable.color
            glowPaint.alpha = 55
            glowPaint.strokeWidth = width * 3.5f
            canvas.drawPath(path, glowPaint)
        }

        wirePaint.color = cable.color
        wirePaint.alpha = 240
        wirePaint.strokeWidth = width
        canvas.drawPath(path, wirePaint)
    }

    private fun drawPlugHead(
        canvas: Canvas,
        plug: PlugSpec
    ) {
        val save = canvas.save()
        canvas.translate(plug.x, plug.y)
        canvas.rotate(plug.angleDeg)

        val pinLen = dp(6f)
        val pinOffsets = floatArrayOf(-dp(6f), 0f, dp(6f))

        plugPinPaint.color = 0xFFC5D2DE.toInt()
        plugPinPaint.strokeWidth = dp(1.7f)
        pinOffsets.forEach { y ->
            canvas.drawLine(-dp(15f), y, -dp(15f) + pinLen, y, plugPinPaint)
        }

        plugPath.reset()
        plugPath.moveTo(-dp(9f), -dp(10f))
        plugPath.lineTo(dp(6f), -dp(10f))
        plugPath.lineTo(dp(6f), dp(10f))
        plugPath.lineTo(-dp(9f), dp(10f))
        plugPath.close()

        plugBodyPaint.color = plug.color
        plugBodyPaint.alpha = 245
        canvas.drawPath(plugPath, plugBodyPaint)

        plugRimPaint.color = Color.WHITE
        plugRimPaint.strokeWidth = dp(0.9f)
        plugRimPaint.alpha = 110
        canvas.drawPath(plugPath, plugRimPaint)

        neckPath.reset()
        neckPath.moveTo(dp(6f), -dp(3.5f))
        neckPath.lineTo(dp(12f), -dp(3.5f))
        neckPath.lineTo(dp(12f), dp(3.5f))
        neckPath.lineTo(dp(6f), dp(3.5f))
        neckPath.close()

        canvas.drawPath(neckPath, plugBodyPaint)
        canvas.drawPath(neckPath, plugRimPaint)

        canvas.restoreToCount(save)
    }

    private fun drawWire(
        canvas: Canvas,
        connection: WireConnection,
        path: Path
    ) {
        val selected = selectedId == connection.id
        val width = dp(connection.strokeWidthDp)

        // Glow layers
        if (glowEnabled && connection.glowEnabled) {
            glowPaint.style = Paint.Style.STROKE
            glowPaint.color = connection.color

            glowPaint.alpha = if (selected) 115 else 55
            glowPaint.strokeWidth = width * 4f
            canvas.drawPath(path, glowPaint)

            glowPaint.alpha = if (selected) 160 else 90
            glowPaint.strokeWidth = width * 2f
            canvas.drawPath(path, glowPaint)
        }

        // Main wire
        wirePaint.color = connection.color
        wirePaint.alpha = 245
        wirePaint.strokeWidth =
            if (selected) width * 1.5f else width

        canvas.drawPath(path, wirePaint)

        // Animated pulse
        if (animationEnabled && connection.animated) {
            drawPulse(canvas, path, connection)
        }

        // Direction indicators
        if (directionVisible) {
            drawDirection(canvas, path, connection)
        }

        // Editable control points
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

        if (!pathMeasure.getPosTan(
                length * fraction,
                position,
                null
            )
        ) {
            return
        }

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

    // ---------------------------------------------------------
    // Direction arrows
    // ---------------------------------------------------------

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

            ConnectionDirection.OUTGOING -> {
                drawArrow(canvas, path, 0.72f, connection.color)
            }

            ConnectionDirection.INCOMING -> {
                drawArrow(canvas, path, 0.28f, connection.color)
            }
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

        val position = FloatArray(2)
        val tangent = FloatArray(2)

        if (!pathMeasure.getPosTan(
                length * fraction,
                position,
                tangent
            )
        ) {
            return
        }

        val angle = atan2(tangent[1], tangent[0])
        val size = dp(8f)
        val spread = Math.toRadians(145.0).toFloat()

        arrowPaint.color = color
        arrowPaint.alpha = 245
        arrowPaint.strokeWidth = dp(1.8f)

        val leftX = position[0] + cos(angle + spread) * size
        val leftY = position[1] + sin(angle + spread) * size

        val rightX = position[0] + cos(angle - spread) * size
        val rightY = position[1] + sin(angle - spread) * size

        canvas.drawLine(
            position[0],
            position[1],
            leftX,
            leftY,
            arrowPaint
        )

        canvas.drawLine(
            position[0],
            position[1],
            rightX,
            rightY,
            arrowPaint
        )
    }

    // ---------------------------------------------------------
    // Control points
    // ---------------------------------------------------------

    private fun drawControls(
        canvas: Canvas,
        connection: WireConnection
    ) {
        val start = endpoints[connection.sourcePortId]
            ?: return

        val end = endpoints[connection.targetPortId]
            ?: return

        val (first, second) = getControlPoints(
            connection,
            start,
            end
        )

        arrowPaint.color = connection.color
        arrowPaint.alpha = 120
        arrowPaint.strokeWidth = dp(1f)

        canvas.drawLine(
            start.x,
            start.y,
            first.x,
            first.y,
            arrowPaint
        )

        canvas.drawLine(
            end.x,
            end.y,
            second.x,
            second.y,
            arrowPaint
        )

        pointPaint.color = connection.color

        canvas.drawCircle(
            first.x,
            first.y,
            dp(6f),
            pointPaint
        )

        canvas.drawCircle(
            second.x,
            second.y,
            dp(6f),
            pointPaint
        )

        pointPaint.color = Color.WHITE

        canvas.drawCircle(
            first.x,
            first.y,
            dp(2.2f),
            pointPaint
        )

        canvas.drawCircle(
            second.x,
            second.y,
            dp(2.2f),
            pointPaint
        )
    }

    // ---------------------------------------------------------
    // Animation management
    // ---------------------------------------------------------

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
        val existing = animators[connection.id]
        val duration = connection.animationDuration.coerceAtLeast(100L)

        if (existing != null) {
            if (existing.duration == duration && existing.isRunning) {
                return
            }

            stopAnimation(connection.id)
        }

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()

            addUpdateListener { animation ->
                progress[connection.id] =
                    animation.animatedValue as Float

                invalidate()
            }
        }

        animators[connection.id] = animator
        animator.start()
    }

    private fun stopAnimation(id: String) {
        animators.remove(id)?.let { animator ->
            animator.cancel()
        }

        progress.remove(id)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateAnimations()
    }

    override fun onDetachedFromWindow() {
        animators.values.forEach { animator ->
            animator.cancel()
        }

        animators.clear()
        progress.clear()

        pressedConnectionId = null
        touchMoved = false

        super.onDetachedFromWindow()
    }

    // ---------------------------------------------------------
    // Touch interaction
    // ---------------------------------------------------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {

            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y

                touchMoved = false

                pressedConnectionId =
                    if (wiresVisible) {
                        findConnectionAt(event.x, event.y)?.id
                    } else {
                        null
                    }

                return pressedConnectionId != null
            }

            MotionEvent.ACTION_MOVE -> {
                if (pressedConnectionId == null) {
                    return false
                }

                val dx = event.x - downX
                val dy = event.y - downY

                if (hypot(dx, dy) > touchSlop) {
                    touchMoved = true
                }

                return true
            }

            MotionEvent.ACTION_UP -> {
                val pressedId = pressedConnectionId
                pressedConnectionId = null

                if (pressedId == null) {
                    return false
                }

                val dx = event.x - downX
                val dy = event.y - downY

                val movedTooFar =
                    touchMoved || hypot(dx, dy) > touchSlop

                if (movedTooFar) {
                    return true
                }

                val connection = findConnectionAt(
                    event.x,
                    event.y
                )

                if (connection == null || connection.id != pressedId) {
                    return true
                }

                selectedId = connection.id

                onConnectionSelected?.invoke(connection)

                invalidate()
                performClick()

                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                pressedConnectionId = null
                touchMoved = false
                return true
            }

            else -> {
                return pressedConnectionId != null
            }
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    // ---------------------------------------------------------
    // Connection hit testing
    // ---------------------------------------------------------

    private fun findConnectionAt(
        x: Float,
        y: Float
    ): WireConnection? {

        val tolerance = dp(14f)
        val position = FloatArray(2)

        val candidates = connections.values.toList().asReversed()

        for (connection in candidates) {
            if (!connection.visible) continue

            val path = paths[connection.id] ?: continue

            pathMeasure.setPath(path, false)

            val length = pathMeasure.length
            if (length <= 0f) continue

            // زيادة عدد نقاط الفحص حسب طول السلك.
            val steps = (length / dp(8f))
                .toInt()
                .coerceIn(24, 240)

            for (i in 0..steps) {
                val distance = length * i / steps.toFloat()

                if (!pathMeasure.getPosTan(
                        distance,
                        position,
                        null
                    )
                ) {
                    continue
                }

                val distanceToPoint = hypot(
                    x - position[0],
                    y - position[1]
                )

                if (distanceToPoint <= tolerance) {
                    return connection
                }
            }
        }

        return null
    }
}

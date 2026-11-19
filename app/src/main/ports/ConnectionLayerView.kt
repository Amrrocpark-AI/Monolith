
package com.monolith.graph.ports

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class ConnectionLayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val connections = LinkedHashMap<String, WireConnection>()

    private val endpoints = HashMap<String, PointF>()

    private val paths = HashMap<String, Path>()

    private val pathMeasure = PathMeasure()

    private val density = resources.displayMetrics.density

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
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var wiresVisible = true

    private var glowEnabled = true

    private var animationEnabled = true

    private var showDirection = true

    private var showControlPoints = false

    private var animationProgress = 0f

    private var selectedConnectionId: String? = null

    private var animator: ValueAnimator? = null

    var onConnectionSelected: ((WireConnection) -> Unit)? = null

    var onConnectionUpdated: ((WireConnection) -> Unit)? = null

    init {
        setWillNotDraw(false)

        isClickable = true

        // The wire layer must not consume ordinary empty-area touches.
        importantForAccessibility =
            IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    /**
     * Replaces the displayed connection models.
     * Relationships remain owned by the caller.
     */
    fun setConnections(items: List<WireConnection>) {
        connections.clear()

        items.forEach {
            connections[it.id] = it
        }

        rebuildPaths()
        updateAnimation()
        invalidate()
    }

    fun getConnections(): List<WireConnection> {
        return connections.values.toList()
    }

    /**
     * Update the position of a real port in overlay coordinates.
     */
    fun updateEndpoint(
        portId: String,
        x: Float,
        y: Float
    ) {
        endpoints[portId] = PointF(x, y)

        rebuildPaths()
        invalidate()
    }

    fun updateEndpoints(
        positions: Map<String, PointF>
    ) {
        positions.forEach { (id, point) ->
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
     * Hide only the rendered wires.
     * Connection models are preserved.
     */
    fun setWiresVisible(visible: Boolean) {
        wiresVisible = visible
        invalidate()
    }

    fun areWiresVisible(): Boolean {
        return wiresVisible
    }

    fun setGlowEnabled(enabled: Boolean) {
        glowEnabled = enabled
        invalidate()
    }

    fun setAnimationEnabled(enabled: Boolean) {
        animationEnabled = enabled
        updateAnimation()
        invalidate()
    }

    fun setDirectionVisible(visible: Boolean) {
        showDirection = visible
        invalidate()
    }

    fun setControlPointsVisible(visible: Boolean) {
        showControlPoints = visible
        invalidate()
    }

    /**
     * Change a wire's control points.
     * Coordinates are relative to this View.
     */
    fun updateControlPoints(
        connectionId: String,
        control1: PointF,
        control2: PointF
    ) {
        val old = connections[connectionId] ?: return

        val updated = old.copy(
            control1 = PointF(control1.x, control1.y),
            control2 = PointF(control2.x, control2.y)
        )

        connections[connectionId] = updated

        rebuildPaths()
        invalidate()

        onConnectionUpdated?.invoke(updated)
    }

    fun updateConnectionColor(
        connectionId: String,
        color: Int
    ) {
        val old = connections[connectionId] ?: return

        connections[connectionId] = old.copy(color = color)

        invalidate()
    }

    fun removeConnection(connectionId: String) {
        connections.remove(connectionId)
        paths.remove(connectionId)

        if (selectedConnectionId == connectionId) {
            selectedConnectionId = null
        }

        updateAnimation()
        invalidate()
    }

    fun getSelectedConnection(): WireConnection? {
        return selectedConnectionId?.let {
            connections[it]
        }
    }

    fun selectConnection(connectionId: String?) {
        selectedConnectionId =
            if (connections.containsKey(connectionId)) {
                connectionId
            } else {
                null
            }

        invalidate()
    }

    private fun rebuildPaths() {
        paths.clear()

        connections.values.forEach { connection ->

            val start = endpoints[connection.sourcePortId]
                ?: return@forEach

            val end = endpoints[connection.targetPortId]
                ?: return@forEach

            val path = createPath(connection, start, end)

            paths[connection.id] = path
        }
    }

    private fun createPath(
        connection: WireConnection,
        start: PointF,
        end: PointF
    ): Path {

        val dx = end.x - start.x
        val dy = end.y - start.y

        val distance = hypot(dx, dy)

        val defaultOffset = (distance * 0.28f)
            .coerceIn(dp(35f), dp(170f))

        val firstControl = connection.control1 ?: PointF(
            start.x + dx * 0.32f,
            start.y - defaultOffset
        )

        val secondControl = connection.control2 ?: PointF(
            start.x + dx * 0.68f,
            end.y - defaultOffset
        )

        return Path().apply {
            moveTo(start.x, start.y)

            cubicTo(
                firstControl.x,
                firstControl.y,
                secondControl.x,
                secondControl.y,
                end.x,
                end.y
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!wiresVisible) return

        connections.values.forEach { connection ->

            if (!connection.visible) return@forEach

            val path = paths[connection.id]
                ?: return@forEach

            val selected =
                selectedConnectionId == connection.id

            drawConnection(
                canvas,
                connection,
                path,
                selected
            )
        }
    }

    private fun drawConnection(
        canvas: Canvas,
        connection: WireConnection,
        path: Path,
        selected: Boolean
    ) {

        val color = connection.color

        val width = dp(connection.strokeWidthDp)

        // Outer glow.
        if (glowEnabled && connection.glowEnabled) {

            glowPaint.color = color
            glowPaint.alpha = if (selected) 95 else 48
            glowPaint.strokeWidth = width * 4f

            canvas.drawPath(path, glowPaint)

            glowPaint.alpha = if (selected) 125 else 75
            glowPaint.strokeWidth = width * 2f

            canvas.drawPath(path, glowPaint)
        }

        // Main wire.
        wirePaint.color = color
        wirePaint.alpha = 235
        wirePaint.strokeWidth = if (selected) {
            width * 1.5f
        } else {
            width
        }

        canvas.drawPath(path, wirePaint)

        // Moving light point.
        if (
            animationEnabled &&
            connection.animated
        ) {
            drawPulse(canvas, path, connection)
        }

        // Direction marker.
        if (showDirection) {
            drawDirectionArrow(
                canvas,
                path,
                connection
            )
        }

        // Editing control points.
        if (
            showControlPoints &&
            selected
        ) {
            drawControlPoints(
                canvas,
                connection
            )
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

        val distance = length * animationProgress

        val position = FloatArray(2)

        if (
            !pathMeasure.getPosTan(
                distance,
                position,
                null
            )
        ) return

        val pulseRadius = dp(5f)

        // Light halo.
        glowPaint.color = connection.color
        glowPaint.style = Paint.Style.FILL
        glowPaint.alpha = 95

        canvas.drawCircle(
            position[0],
            position[1],
            pulseRadius * 2f,
            glowPaint
        )

        // Bright center.
        pointPaint.color = Color.WHITE
        pointPaint.alpha = 235

        canvas.drawCircle(
            position[0],
            position[1],
            pulseRadius * 0.55f,
            pointPaint
        )

        // Restore glow style.
        glowPaint.style = Paint.Style.STROKE
    }

    private fun drawDirectionArrow(
        canvas: Canvas,
        path: Path,
        connection: WireConnection
    ) {

        if (
            connection.direction ==
            ConnectionDirection.BIDIRECTIONAL
        ) {
            drawArrowAt(canvas, path, 0.32f, connection.color)
            drawArrowAt(canvas, path, 0.68f, connection.color)
            return
        }

        val position = if (
            connection.direction ==
            ConnectionDirection.OUTGOING
        ) 0.72f else 0.28f

        drawArrowAt(
            canvas,
            path,
            position,
            connection.color
        )
    }

    private fun drawArrowAt(
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

        if (
            !pathMeasure.getPosTan(
                length * fraction,
                pos,
                tan
            )
        ) return

        val angle = atan2(tan[1], tan[0])

        val arrowLength = dp(8f)
        val spread = Math.toRadians(145.0).toFloat()

        val leftX = pos[0] +
            cos(angle + spread) * arrowLength

        val leftY = pos[1] +
            sin(angle + spread) * arrowLength

        val rightX = pos[0] +
            cos(angle - spread) * arrowLength

        val rightY = pos[1] +
            sin(angle - spread) * arrowLength

        arrowPaint.color = color
        arrowPaint.alpha = 240
        arrowPaint.strokeWidth = dp(1.8f)

        canvas.drawLine(
            pos[0],
            pos[1],
            leftX,
            leftY,
            arrowPaint
        )

        canvas.drawLine(
            pos[0],
            pos[1],
            rightX,
            rightY,
            arrowPaint
        )
    }

    private fun drawControlPoints(
        canvas: Canvas,
        connection: WireConnection
    ) {

        val start = endpoints[connection.sourcePortId]
            ?: return

        val end = endpoints[connection.targetPortId]
            ?: return

        val first = connection.control1 ?: PointF(
            start.x + (end.x - start.x) * 0.32f,
            start.y - dp(50f)
        )

        val second = connection.control2 ?: PointF(
            start.x + (end.x - start.x) * 0.68f,
            end.y - dp(50f)
        )

        pointPaint.color = connection.color
        pointPaint.alpha = 110

        canvas.drawLine(
            start.x,
            start.y,
            first.x,
            first.y,
            arrowPaint.apply {
                color = connection.color
                alpha = 100
                strokeWidth = dp(1f)
            }
        )

        canvas.drawLine(
            end.x,
            end.y,
            second.x,
            second.y,
            arrowPaint
        )

        pointPaint.alpha = 255

        canvas.drawCircle(
            first.x,
            first.y,
            dp(5f),
            pointPaint
        )

        canvas.drawCircle(
            second.x,
            second.y,
            dp(5f),
            pointPaint
        )

        pointPaint.color = Color.WHITE

        canvas.drawCircle(
            first.x,
            first.y,
            dp(2f),
            pointPaint
        )

        canvas.drawCircle(
            second.x,
            second.y,
            dp(2f),
            pointPaint
        )
    }

    private fun updateAnimation() {

        animator?.cancel()
        animator = null

        val shouldAnimate =
            wiresVisible &&
            animationEnabled &&
            connections.values.any {
                it.visible && it.animated
            }

        if (!shouldAnimate || !isAttachedToWindow) {
            return
        }

        animator = ValueAnimator.ofFloat(0f, 1f).apply {

            duration = 1700L

            repeatCount = ValueAnimator.INFINITE

            interpolator = LinearInterpolator()

            addUpdateListener {
                animationProgress =
                    it.animatedValue as Float

                invalidate()
            }

            start()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateAnimation()
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {

        when (event.actionMasked) {

            MotionEvent.ACTION_DOWN -> {
                return wiresVisible &&
                    findConnectionAt(
                        event.x,
                        event.y
                    ) != null
            }

            MotionEvent.ACTION_UP -> {

                val connection = findConnectionAt(
                    event.x,
                    event.y
                )

                if (connection != null) {
                    selectedConnectionId = connection.id
                    onConnectionSelected?.invoke(connection)
                    invalidate()
                    performClick()
                    return true
                }
            }
        }

        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun findConnectionAt(
        x: Float,
        y: Float
    ): WireConnection? {

        val tolerance = dp(14f)

        return connections.values
            .filter { it.visible }
            .asReversed()
            .firstOrNull { connection ->

                val path = paths[connection.id]
                    ?: return@firstOrNull false

                pathMeasure.setPath(path, false)

                val length = pathMeasure.length

                if (length <= 0f) {
                    return@firstOrNull false
                }

                val position = FloatArray(2)

                val steps = 30

                for (i in 0..steps) {

                    val distance =
                        length * i / steps.toFloat()

                    pathMeasure.getPosTan(
                        distance,
                        position,
                        null
                    )

                    if (
                        hypot(
                            x - position[0],
                            y - position[1]
                        ) <= tolerance
                    ) {
                        return@firstOrNull true
                    }
                }

                false
            }
    }

    private fun dp(value: Float): Float {
        return value * density
    }
}

package com.monolith.graph.ports.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.Shader
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

/**
 * ConnectionLayerView
 *
 * طبقة رسم الأسلاك والقوابس والمنافذ.
 * تم نقل الشكل البصري من MONOLITH Canvas (Gemini) مع الحفاظ الكامل
 * على التوقيعات (signatures) والواجهات البرمجية (APIs) الحالية.
 *
 * ما تم نقله:
 *  - منافذ معدنية (Sockets) مع LED ملوّن حول كل endpoint.
 *  - قابس Gemini (4 prongs + body + tail + grip ridges + rim).
 *  - سلك Bezier مع sag + sheen highlight.
 *  - FX: Snap Ring, Detach Release, Signal Flash.
 *
 * ما لم يُنقل (يحتاج ملفات أخرى):
 *  - PortSpec / PortOffsets (يأتي من الخارج).
 *  - Popover الألوان، الأصوات، Alarm، Undo (تُدار من ChatActivity).
 */
class ConnectionLayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // ---------------------------------------------------------------------
    // إعدادات FX (مكانها الحقيقي في ChatActivity، لكن قيم افتراضية هنا)
    // ---------------------------------------------------------------------
    class PlugFxConfig {
        var snapRadius: Float = 38f          // dp
        var animEffect: String = "all"       // all | snap_ring | micro_recoil | signal_flash | detach_release | none
        var attachSound: String = "crisp_click"
        var detachSound: String = "soft_unplug"
        var soundVolume: Float = 0.70f
        var showPortLabels: Boolean = true
        var useIdentityLockColor: Boolean = false
    }

    var plugFxConfig: PlugFxConfig = PlugFxConfig()

    // ---------------------------------------------------------------------
    // الحالة
    // ---------------------------------------------------------------------
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

    // ---------------------------------------------------------------------
    // Paints
    // ---------------------------------------------------------------------
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

    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    // Sockets (المنافذ)
    private val socketRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val socketHolePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val socketLedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val socketLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        textSize = dp(9f)
        textAlign = Paint.Align.CENTER
        color = Color.rgb(100, 116, 139)
    }

    // FX (snap ring, detach ring, flash)
    private val fxRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val fxFlashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val fxList = mutableListOf<FxEvent>()

    private data class FxEvent(
        val type: String,          // "snap_ring" | "detach_release" | "signal_flash"
        val cx: Float,
        val cy: Float,
        val color: Int,
        val startMs: Long,
        val durationMs: Long,
        val fromR: Float = 0f,
        val toR: Float = 0f
    )

    // ---------------------------------------------------------------------
    // Paths
    // ---------------------------------------------------------------------
    private val plugPath = Path()
    private val neckPath = Path()
    private val gripPath = Path()
    private val pathMeasure = PathMeasure()
    private val sheenPath = Path()

    // ---------------------------------------------------------------------
    // Drag / touch
    // ---------------------------------------------------------------------
    private var selectedId: String? = null
    private var pressedConnectionId: String? = null
    private var draggingPlugId: String? = null
    private var dragOffsetX = 0f
    private var dragOffsetY = 0f

    private var wiresVisible = true
    private var glowEnabled = true
    private var animationEnabled = true
    private var directionVisible = true
    private var socketsVisible = true

    private var controlPointsVisible = false

    private var downX = 0f
    private var downY = 0f
    private var touchMoved = false

    // ---------------------------------------------------------------
    // Callbacks
    // ---------------------------------------------------------------
    var onConnectionSelected: ((WireConnection) -> Unit)? = null
    var onConnectionUpdated: ((WireConnection) -> Unit)? = null
    var onPlugMoved: ((plugId: String, position: PointF) -> Unit)? = null
    var onPlugAttached: ((plugId: String, portId: String) -> Unit)? = null
    var onPlugDetached: ((plugId: String) -> Unit)? = null

    init {
        setWillNotDraw(false)
        isClickable = true
    }

    // ---------------------------------------------------------------
    // Toggles (توقيعات عامة موجودة مسبقًا)
    // ---------------------------------------------------------------
    fun setGlowEnabled(enabled: Boolean) {
        glowEnabled = enabled
        invalidate()
    }

    fun setAnimationEnabled(enabled: Boolean) {
        animationEnabled = enabled
        updateAnimations()
        invalidate()
    }

    fun setDirectionVisible(visible: Boolean) {
        directionVisible = visible
        invalidate()
    }

    fun setSocketsVisible(visible: Boolean) {
        socketsVisible = visible
        invalidate()
    }

    fun setWiresVisible(visible: Boolean) {
        wiresVisible = visible
        invalidate()
    }

    fun setControlPointsVisible(visible: Boolean) {
        controlPointsVisible = visible
        invalidate()
    }

    // ---------------------------------------------------------------
    // Plugs
    // ---------------------------------------------------------------
    fun setPlugs(items: List<PlugSpec>) {
        plugs.clear()
        items.forEach { plugs[it.id] = it }
        if (draggingPlugId !in plugs.keys) draggingPlugId = null
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
        if (draggingPlugId == plugId) draggingPlugId = null
        rebuildPaths()
        invalidate()
    }

    fun clearPlugs() {
        plugs.clear()
        draggingPlugId = null
        rebuildPaths()
        invalidate()
    }

    fun detachPlug(plugId: String) {
        val current = plugs[plugId] ?: return
        val pos = resolvePlugPosition(current)
        plugs[plugId] = current.copy(
            x = pos.x,
            y = pos.y,
            attachedPortId = null
        )
        rebuildPaths()
        invalidate()
    }

    fun attachPlugToSocket(plugId: String, portId: String) {
        val current = plugs[plugId] ?: return
        val socketCenter = endpoints[portId] ?: return
        plugs[plugId] = current.copy(
            x = socketCenter.x,
            y = socketCenter.y,
            attachedPortId = portId
        )
        rebuildPaths()
        invalidate()
    }

    // ---------------------------------------------------------------
    // Cables
    // ---------------------------------------------------------------
    fun setCables(items: List<CableSpec>) {
        cables.clear()
        items.forEach { cables[it.id] = it }
        rebuildPaths()
        invalidate()
    }

    fun getCables(): List<CableSpec> = cables.values.toList()

    fun clearCables() {
        cables.clear()
        rebuildPaths()
        invalidate()
    }

    // ---------------------------------------------------------------
    // Connections
    // ---------------------------------------------------------------
    fun setConnections(items: List<WireConnection>) {
        val newIds = items.mapTo(HashSet()) { it.id }
        connections.keys.filter { it !in newIds }.forEach { stopAnimation(it) }
        connections.clear()
        items.forEach { connections[it.id] = it }
        if (selectedId !in connections.keys) selectedId = null
        rebuildPaths()
        updateAnimations()
        invalidate()
    }

    fun getConnections(): List<WireConnection> = connections.values.toList()

    fun removeConnection(id: String) {
        connections.remove(id)
        paths.remove(id)
        stopAnimation(id)
        if (selectedId == id) selectedId = null
        if (pressedConnectionId == id) pressedConnectionId = null
        invalidate()
    }

    fun selectConnection(id: String?) {
        selectedId = id?.takeIf { connections.containsKey(it) }
        invalidate()
    }

    fun getSelectedConnection(): WireConnection? = selectedId?.let { connections[it] }

    // ---------------------------------------------------------------
    // Endpoints
    // ---------------------------------------------------------------
    fun updateEndpoint(portId: String, x: Float, y: Float) {
        val anchor = PointF(x, y)
        endpoints[portId] = anchor
        syncAttachedPlugsToSocket(portId, anchor)
        rebuildPaths()
        invalidate()
    }

    fun updateEndpoints(points: Map<String, PointF>) {
        points.forEach { (id, point) ->
            val anchor = PointF(point.x, point.y)
            endpoints[id] = anchor
            syncAttachedPlugsToSocket(id, anchor)
        }
        rebuildPaths()
        invalidate()
    }

    fun removeEndpoint(portId: String) {
        plugs.entries.forEach { (id, plug) ->
            if (plug.attachedPortId == portId) {
                val pos = resolvePlugPosition(plug)
                plugs[id] = plug.copy(
                    x = pos.x,
                    y = pos.y,
                    attachedPortId = null
                )
            }
        }
        endpoints.remove(portId)
        rebuildPaths()
        invalidate()
    }

    fun clearEndpoints() {
        endpoints.clear()
        rebuildPaths()
        invalidate()
    }

    private fun syncAttachedPlugsToSocket(portId: String, socketAnchor: PointF) {
        plugs.entries.forEach { (id, plug) ->
            if (plug.attachedPortId == portId && id != draggingPlugId) {
                plugs[id] = plug.copy(
                    x = socketAnchor.x,
                    y = socketAnchor.y
                )
            }
        }
    }

    private fun findNearestSocketAnchor(position: PointF): Pair<String, PointF>? {
        val snapRadius = dp(plugFxConfig.snapRadius)
        var best: Pair<String, PointF>? = null
        var bestDist = snapRadius

        endpoints.forEach { (portId, point) ->
            val d = hypot(position.x - point.x, position.y - point.y)
            if (d <= bestDist) {
                bestDist = d
                best = Pair(portId, PointF(point.x, point.y))
            }
        }
        return best
    }

    // ---------------------------------------------------------------
    // Plug geometry
    // ---------------------------------------------------------------
    private fun resolvePlugPosition(plug: PlugSpec): PointF {
        val attachedPortId = plug.attachedPortId
        if (attachedPortId != null) {
            val socketCenter = endpoints[attachedPortId]
            if (socketCenter != null) {
                return PointF(socketCenter.x, socketCenter.y)
            }
        }
        return PointF(plug.x, plug.y)
    }

    private fun resolveEndpointAnchor(id: String): PointF? = endpoints[id]

    private fun resolveCableEndpointAnchor(id: String): PointF? {
        val plug = plugs[id] ?: return null
        return resolvePlugPosition(plug)
    }

    // ---------------------------------------------------------------
    // Control points with sag (Gemini style)
    // ---------------------------------------------------------------
    private fun getControlPoints(
        connection: WireConnection,
        start: PointF,
        end: PointF
    ): Pair<PointF, PointF> {
        val dx = end.x - start.x
        val dy = end.y - start.y
        val distance = hypot(dx, dy)

        val offset = (distance * 0.28f).coerceIn(dp(35f), dp(170f))
        val sag = (distance * 0.15f).coerceIn(dp(10f), dp(90f))

        val first = connection.control1 ?: PointF(
            start.x + dx * 0.32f,
            start.y - offset + sag
        )

        val second = connection.control2 ?: PointF(
            start.x + dx * 0.68f,
            end.y - offset + sag
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

        val offset = (distance * 0.28f).coerceIn(dp(24f), dp(150f))
        val sag = (distance * 0.12f).coerceIn(dp(8f), dp(70f))

        val first = cable.control1 ?: PointF(
            start.x + dx * 0.32f,
            start.y - offset + sag
        )

        val second = cable.control2 ?: PointF(
            start.x + dx * 0.68f,
            end.y + offset * 0.4f + sag
        )

        return Pair(first, second)
    }

    // ---------------------------------------------------------------
    // Path rebuild
    // ---------------------------------------------------------------
    private fun rebuildPaths() {
        paths.clear()

        connections.values.forEach { connection ->
            val start = resolveEndpointAnchor(connection.sourcePortId) ?: return@forEach
            val end = resolveEndpointAnchor(connection.targetPortId) ?: return@forEach
            val (first, second) = getControlPoints(connection, start, end)

            val path = Path().apply {
                moveTo(start.x, start.y)
                cubicTo(first.x, first.y, second.x, second.y, end.x, end.y)
            }
            paths[connection.id] = path
        }

        cables.values.forEach { cable ->
            val start = resolveCableEndpointAnchor(cable.sourcePlugId) ?: return@forEach
            val end = cable.targetPlugId?.let { resolveCableEndpointAnchor(it) }
                ?: PointF(start.x + dp(46f), start.y + dp(28f))
            val (first, second) = getCableControlPoints(cable, start, end)

            val path = Path().apply {
                moveTo(start.x, start.y)
                cubicTo(first.x, first.y, second.x, second.y, end.x, end.y)
            }
            paths["cable:${cable.id}"] = path
        }
    }

    // ---------------------------------------------------------------
    // Drawing
    // ---------------------------------------------------------------
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!wiresVisible) {
            // إذا كانت الأسلاك مخفية، قد نرسم المنافذ فقط.
            if (socketsVisible) drawAllSockets(canvas)
            return
        }

        // 1. الأسلاك
        connections.values.forEach { connection ->
            if (!connection.visible) return@forEach
            val path = paths[connection.id] ?: return@forEach
            drawWire(canvas, connection, path)
        }

        // 2. الكابلات
        cables.values.forEach { cable ->
            if (!cable.visible) return@forEach
            val path = paths["cable:${cable.id}"] ?: return@forEach
            drawCableSpec(canvas, cable, path)
        }

        // 3. المنافذ
        if (socketsVisible) drawAllSockets(canvas)

        // 4. القوابس
        plugs.values.forEach { drawPlugHead(canvas, it) }

        // 5. FX
        drawFxEvents(canvas)
    }

    // ---------------------------------------------------------------
    // Sockets
    // ---------------------------------------------------------------
    private fun drawAllSockets(canvas: Canvas) {
        endpoints.forEach { (portId, point) ->
            drawSocket(canvas, point.x, point.y, portId)
        }
    }

    private fun drawSocket(canvas: Canvas, cx: Float, cy: Float, portId: String) {
        val rimW = dp(8f)
        val rimH = dp(22f)

        // Rim (إطار المنفذ)
        socketRimPaint.color = Color.rgb(26, 34, 52)
        socketRimPaint.strokeWidth = dp(1.2f)
        canvas.drawRoundRect(
            cx - rimW / 2f, cy - rimH / 2f,
            cx + rimW / 2f, cy + rimH / 2f,
            dp(2.5f), dp(2.5f),
            socketRimPaint
        )

        // Hole (فتحة داكنة)
        socketHolePaint.color = Color.rgb(2, 3, 5)
        canvas.drawRoundRect(
            cx - dp(2f), cy - dp(8f),
            cx + dp(2f), cy + dp(8f),
            dp(1.2f), dp(1.2f),
            socketHolePaint
        )

        // LED (ملوّن لو القابس موصول)
        val attachedPlug = plugs.values.firstOrNull { it.attachedPortId == portId }
        val ledColor = attachedPlug?.color ?: Color.rgb(30, 41, 59)
        socketLedPaint.color = ledColor
        canvas.drawRoundRect(
            cx - dp(2.5f), cy - dp(9f),
            cx + dp(2.5f), cy + dp(9f),
            dp(2f), dp(2f),
            socketLedPaint
        )

        // Label (L1, R2, ...)
        if (plugFxConfig.showPortLabels) {
            canvas.drawText(
                portId.takeLast(2).uppercase(),
                cx,
                cy - dp(16f),
                socketLabelPaint
            )
        }
    }

    // ---------------------------------------------------------------
    // Cables
    // ---------------------------------------------------------------
    private fun drawCableSpec(canvas: Canvas, cable: CableSpec, path: Path) {
        val width = dp(cable.strokeWidthDp)

        if (glowEnabled && cable.glowEnabled) {
            glowPaint.style = Paint.Style.STROKE
            glowPaint.color = cable.color
            glowPaint.alpha = 55
            glowPaint.strokeWidth = width * 3.5f
            canvas.drawPath(path, glowPaint)
        }

        // main
        wirePaint.color = cable.color
        wirePaint.alpha = 240
        wirePaint.strokeWidth = width
        canvas.drawPath(path, wirePaint)

        // sheen highlight
        drawSheen(canvas, path, width)
    }

    // ---------------------------------------------------------------
    // Sheen
    // ---------------------------------------------------------------
    private fun drawSheen(canvas: Canvas, path: Path, width: Float) {
        sheenPaint.color = Color.argb(90, 255, 255, 255)
        sheenPaint.strokeWidth = width * 0.28f
        sheenPaint.strokeCap = Paint.Cap.ROUND
        canvas.drawPath(path, sheenPaint)
    }

    // ---------------------------------------------------------------
    // Plug (Gemini style)
    // ---------------------------------------------------------------
    private fun drawPlugHead(canvas: Canvas, plug: PlugSpec) {
        val pos = resolvePlugPosition(plug)
        val save = canvas.save()
        canvas.translate(pos.x, pos.y)
        canvas.rotate(plug.angleDeg)

        // 1. Prongs (4 أصابع معدنية)
        val pinLen = dp(8f)
        val pinOffsets = floatArrayOf(-dp(5.2f), -dp(1.7f), dp(1.7f), dp(5.2f))
        plugPinPaint.color = Color.rgb(203, 213, 225)
        plugPinPaint.strokeWidth = dp(2f)
        pinOffsets.forEach { y ->
            canvas.drawLine(-dp(15f), y, -dp(15f) + pinLen, y, plugPinPaint)
        }

        // 2. Body (جسم مستطيل)
        plugPath.reset()
        plugPath.moveTo(-dp(9f), -dp(10f))
        plugPath.lineTo(dp(6f), -dp(10f))
        plugPath.lineTo(dp(6f), dp(10f))
        plugPath.lineTo(-dp(9f), dp(10f))
        plugPath.close()

        plugBodyPaint.color = plug.color
        plugBodyPaint.alpha = 245
        canvas.drawPath(plugPath, plugBodyPaint)

        // 3. Rim (لمعة داخلية)
        plugRimPaint.color = Color.WHITE
        plugRimPaint.strokeWidth = dp(0.9f)
        plugRimPaint.alpha = 110
        canvas.drawPath(plugPath, plugRimPaint)

        // 4. Grip ridges (خطوط طولية)
        gripPath.reset()
        val grips = floatArrayOf(-dp(6f), -dp(3f), 0f, dp(3f), dp(6f))
        grips.forEach { y ->
            gripPath.moveTo(-dp(7f), y)
            gripPath.lineTo(dp(4f), y)
        }
        plugPinPaint.color = Color.argb(70, 0, 0, 0)
        plugPinPaint.strokeWidth = dp(0.8f)
        canvas.drawPath(gripPath, plugPinPaint)

        // 5. Neck (رقبة)
        neckPath.reset()
        neckPath.moveTo(dp(6f), -dp(3.5f))
        neckPath.lineTo(dp(12f), -dp(3.5f))
        neckPath.lineTo(dp(12f), dp(3.5f))
        neckPath.lineTo(dp(6f), dp(3.5f))
        neckPath.close()
        canvas.drawPath(neckPath, plugBodyPaint)
        canvas.drawPath(neckPath, plugRimPaint)

        // 6. Tail (ذيل مدبب)
        val tailPath = Path()
        tailPath.moveTo(dp(12f), -dp(3.5f))
        tailPath.lineTo(dp(20f), -dp(1.5f))
        tailPath.lineTo(dp(20f), dp(1.5f))
        tailPath.lineTo(dp(12f), dp(3.5f))
        tailPath.close()
        canvas.drawPath(tailPath, plugBodyPaint)

        canvas.restoreToCount(save)
    }

    // ---------------------------------------------------------------
    // Wire
    // ---------------------------------------------------------------
    private fun drawWire(canvas: Canvas, connection: WireConnection, path: Path) {
        val selected = selectedId == connection.id
        val width = dp(connection.strokeWidthDp)

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

        wirePaint.color = connection.color
        wirePaint.alpha = 245
        wirePaint.strokeWidth = if (selected) width * 1.5f else width
        canvas.drawPath(path, wirePaint)

        drawSheen(canvas, path, width)

        if (animationEnabled && connection.animated) drawPulse(canvas, path, connection)
        if (directionVisible) drawDirection(canvas, path, connection)
        if (controlPointsVisible && selected) drawControls(canvas, connection)
    }

    private fun drawPulse(canvas: Canvas, path: Path, connection: WireConnection) {
        pathMeasure.setPath(path, false)
        val length = pathMeasure.length
        if (length <= 0f) return
        val position = FloatArray(2)
        val fraction = progress[connection.id] ?: 0f
        if (!pathMeasure.getPosTan(length * fraction, position, null)) return

        glowPaint.style = Paint.Style.FILL
        glowPaint.color = connection.color
        glowPaint.alpha = 100
        canvas.drawCircle(position[0], position[1], dp(10f), glowPaint)

        pulsePaint.color = Color.WHITE
        pulsePaint.alpha = 245
        canvas.drawCircle(position[0], position[1], dp(2.8f), pulsePaint)

        glowPaint.style = Paint.Style.STROKE
    }

    // ---------------------------------------------------------------
    // Direction arrows
    // ---------------------------------------------------------------
    private fun drawDirection(canvas: Canvas, path: Path, connection: WireConnection) {
        when (connection.direction) {
            ConnectionDirection.BIDIRECTIONAL -> {
                drawArrow(canvas, path, 0.30f, connection.color)
                drawArrow(canvas, path, 0.70f, connection.color)
            }
            ConnectionDirection.OUTGOING -> drawArrow(canvas, path, 0.72f, connection.color)
            ConnectionDirection.INCOMING -> drawArrow(canvas, path, 0.28f, connection.color)
        }
    }

    private fun drawArrow(canvas: Canvas, path: Path, fraction: Float, color: Int) {
        pathMeasure.setPath(path, false)
        val length = pathMeasure.length
        if (length <= 0f) return
        val position = FloatArray(2)
        val tangent = FloatArray(2)
        if (!pathMeasure.getPosTan(length * fraction, position, tangent)) return

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

        canvas.drawLine(position[0], position[1], leftX, leftY, arrowPaint)
        canvas.drawLine(position[0], position[1], rightX, rightY, arrowPaint)
    }

    // ---------------------------------------------------------------
    // Control points
    // ---------------------------------------------------------------
    private fun drawControls(canvas: Canvas, connection: WireConnection) {
        val start = resolveEndpointAnchor(connection.sourcePortId) ?: return
        val end = resolveEndpointAnchor(connection.targetPortId) ?: return
        val (first, second) = getControlPoints(connection, start, end)

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

    // ---------------------------------------------------------------
    // FX
    // ---------------------------------------------------------------
    fun triggerSnapRing(cx: Float, cy: Float, color: Int) {
        if (plugFxConfig.animEffect != "all" &&
            plugFxConfig.animEffect != "snap_ring") return
        fxList.add(
            FxEvent(
                type = "snap_ring",
                cx = cx, cy = cy, color = color,
                startMs = System.currentTimeMillis(),
                durationMs = 320L,
                fromR = dp(24f), toR = dp(5f)
            )
        )
        invalidate()
    }

    fun triggerDetachRelease(cx: Float, cy: Float, color: Int) {
        if (plugFxConfig.animEffect != "all" &&
            plugFxConfig.animEffect != "detach_release") return
        fxList.add(
            FxEvent(
                type = "detach_release",
                cx = cx, cy = cy, color = color,
                startMs = System.currentTimeMillis(),
                durationMs = 350L,
                fromR = dp(4f), toR = dp(28f)
            )
        )
        invalidate()
    }

    fun triggerSignalFlash(cx: Float, cy: Float, color: Int) {
        if (plugFxConfig.animEffect != "all" &&
            plugFxConfig.animEffect != "signal_flash") return
        fxList.add(
            FxEvent(
                type = "signal_flash",
                cx = cx, cy = cy, color = color,
                startMs = System.currentTimeMillis(),
                durationMs = 260L
            )
        )
        invalidate()
    }

    private fun drawFxEvents(canvas: Canvas) {
        if (fxList.isEmpty()) return
        val now = System.currentTimeMillis()
        val iterator = fxList.iterator()

        while (iterator.hasNext()) {
            val fx = iterator.next()
            val elapsed = now - fx.startMs
            if (elapsed >= fx.durationMs) {
                iterator.remove()
                continue
            }
            val t = elapsed.toFloat() / fx.durationMs.toFloat()

            when (fx.type) {
                "snap_ring" -> {
                    val r = fx.fromR + (fx.toR - fx.fromR) * t
                    fxRingPaint.color = fx.color
                    fxRingPaint.alpha = ((1f - t) * 255).toInt().coerceIn(0, 255)
                    fxRingPaint.strokeWidth = dp(3f - 2f * t)
                    canvas.drawCircle(fx.cx, fx.cy, r, fxRingPaint)
                }
                "detach_release" -> {
                    val r = fx.fromR + (fx.toR - fx.fromR) * t
                    fxRingPaint.color = fx.color
                    fxRingPaint.alpha = ((1f - t) * 255).toInt().coerceIn(0, 255)
                    fxRingPaint.strokeWidth = dp(3f - 2f * t)
                    canvas.drawCircle(fx.cx, fx.cy, r, fxRingPaint)
                }
                "signal_flash" -> {
                    fxFlashPaint.color = fx.color
                    fxFlashPaint.alpha = ((1f - t) * 200).toInt().coerceIn(0, 255)
                    fxFlashPaint.strokeWidth = dp(2f)
                    canvas.drawCircle(fx.cx, fx.cy, dp(20f + 20f * t), fxFlashPaint)
                }
            }
        }

        if (fxList.isNotEmpty()) postInvalidateOnAnimation()
    }

    // ---------------------------------------------------------------
    // Animation management
    // ---------------------------------------------------------------
    private fun updateAnimations() {
        connections.values.forEach { connection ->
            val shouldRun =
                isAttachedToWindow &&
                wiresVisible &&
                animationEnabled &&
                connection.visible &&
                connection.animated

            if (shouldRun) startAnimation(connection) else stopAnimation(connection.id)
        }
    }

    private fun startAnimation(connection: WireConnection) {
        val existing = animators[connection.id]
        val duration = connection.animationDuration.coerceAtLeast(100L)

        if (existing != null) {
            if (existing.duration == duration && existing.isRunning) return
            stopAnimation(connection.id)
        }

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                progress[connection.id] = animation.animatedValue as Float
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
        progress.clear()
        fxList.clear()
        clearActivePlugDrag(resetToResolvedPosition = false)
        pressedConnectionId = null
        touchMoved = false
        super.onDetachedFromWindow()
    }

    // ---------------------------------------------------------------
    // Touch
    // ---------------------------------------------------------------
    private fun findPlugAt(x: Float, y: Float): PlugSpec? {
        val hitRadius = dp(18f)
        val candidates = plugs.values.toList().asReversed()
        for (plug in candidates) {
            val pos = resolvePlugPosition(plug)
            if (hypot(x - pos.x, y - pos.y) <= hitRadius) return plug
        }
        return null
    }

    private fun clearActivePlugDrag(resetToResolvedPosition: Boolean) {
        val plugId = draggingPlugId
        draggingPlugId = null
        if (plugId != null) {
            plugs[plugId]?.let { plug ->
                if (resetToResolvedPosition) {
                    val pos = resolvePlugPosition(plug)
                    plugs[plugId] = plug.copy(x = pos.x, y = pos.y, isDragging = false)
                } else if (plug.isDragging) {
                    plugs[plugId] = plug.copy(isDragging = false)
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {

            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                touchMoved = false
                pressedConnectionId = null
                clearActivePlugDrag(resetToResolvedPosition = false)

                if (!wiresVisible) return false

                val hitPlug = findPlugAt(event.x, event.y)
                if (hitPlug != null) {
                    val pos = resolvePlugPosition(hitPlug)
                    draggingPlugId = hitPlug.id
                    dragOffsetX = pos.x - event.x
                    dragOffsetY = pos.y - event.y
                    plugs[hitPlug.id] = hitPlug.copy(isDragging = true)
                    return true
                }

                pressedConnectionId = findConnectionAt(event.x, event.y)?.id
                return pressedConnectionId != null
            }

            MotionEvent.ACTION_MOVE -> {
                val plugId = draggingPlugId
                if (plugId != null) {
                    val plug = plugs[plugId] ?: return false
                    if (hypot(event.x - downX, event.y - downY) > touchSlop) touchMoved = true

                    val currentPosition = resolvePlugPosition(plug)
                    val nextX = event.x + dragOffsetX
                    val nextY = event.y + dragOffsetY
                    val updated = plug.copy(
                        x = if (touchMoved) nextX else currentPosition.x,
                        y = if (touchMoved) nextY else currentPosition.y,
                        attachedPortId = if (touchMoved) null else plug.attachedPortId,
                        isDragging = true
                    )
                    plugs[plugId] = updated
                    rebuildPaths()
                    invalidate()
                    onPlugMoved?.invoke(plugId, PointF(updated.x, updated.y))
                    return true
                }

                if (pressedConnectionId == null) return false
                val dx = event.x - downX
                val dy = event.y - downY
                if (hypot(dx, dy) > touchSlop) touchMoved = true
                return true
            }

            MotionEvent.ACTION_UP -> {
                val plugId = draggingPlugId
                if (plugId != null) {
                    draggingPlugId = null
                    val plug = plugs[plugId]
                    if (plug != null) {
                        val currentPos = resolvePlugPosition(plug)
                        val snapped = findNearestSocketAnchor(currentPos)
                        val wasDetached = plug.attachedPortId == null
                        val updated = if (snapped != null) {
                            plug.copy(
                                x = snapped.second.x,
                                y = snapped.second.y,
                                attachedPortId = snapped.first,
                                isDragging = false
                            )
                        } else {
                            plug.copy(
                                x = currentPos.x,
                                y = currentPos.y,
                                attachedPortId = null,
                                isDragging = false
                            )
                        }
                        plugs[plugId] = updated
                        rebuildPaths()
                        invalidate()

                        if (snapped != null) {
                            triggerSnapRing(snapped.second.x, snapped.second.y, updated.color)
                            onPlugAttached?.invoke(plugId, snapped.first)
                        } else if (!wasDetached) {
                            triggerDetachRelease(currentPos.x, currentPos.y, updated.color)
                            onPlugDetached?.invoke(plugId)
                        }
                        onPlugMoved?.invoke(plugId, PointF(updated.x, updated.y))
                    }
                    if (!touchMoved) performClick()
                    return true
                }

                val pressedId = pressedConnectionId
                pressedConnectionId = null
                if (pressedId == null) return false

                val dx = event.x - downX
                val dy = event.y - downY
                val movedTooFar = touchMoved || hypot(dx, dy) > touchSlop
                if (movedTooFar) return true

                val connection = findConnectionAt(event.x, event.y)
                if (connection == null || connection.id != pressedId) return true

                selectedId = connection.id
                onConnectionSelected?.invoke(connection)
                invalidate()
                performClick()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                pressedConnectionId = null
                clearActivePlugDrag(resetToResolvedPosition = true)
                touchMoved = false
                rebuildPaths()
                invalidate()
                return true
            }

            else -> return pressedConnectionId != null || draggingPlugId != null
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    // ---------------------------------------------------------------
    // Connection hit test
    // ---------------------------------------------------------------
    private fun findConnectionAt(x: Float, y: Float): WireConnection? {
        val tolerance = dp(14f)
        val position = FloatArray(2)
        val candidates = connections.values.toList().asReversed()

        for (connection in candidates) {
            if (!connection.visible) continue
            val path = paths[connection.id] ?: continue
            pathMeasure.setPath(path, false)
            val length = pathMeasure.length
            if (length <= 0f) continue

            val steps = (length / dp(8f)).toInt().coerceIn(24, 240)
            for (i in 0..steps) {
                val distance = length * i / steps.toFloat()
                if (!pathMeasure.getPosTan(distance, position, null)) continue
                val d = hypot(x - position[0], y - position[1])
                if (d <= tolerance) return connection
            }
        }
        return null
    }
}

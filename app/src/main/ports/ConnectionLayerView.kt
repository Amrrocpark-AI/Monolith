package com.monolith.graph.ports.views

import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PointF
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
 * ConnectionLayerView — مسؤول عن:
 * - الأسلاك (connections + cables)
 * - القوابس (plugs) مع metal gradient + shadow
 * - FX (snap / detach / flash)
 * - Path session (pen / paint)
 *
 * PortView هو المسؤول عن رسم rim + hole + LED + label + lock badge.
 * لا يرسم هذا الـ View أي socket.
 */
class ConnectionLayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    class PlugFxConfig {
        var snapRadius: Float = 38f
        var animEffect: String = "all"
        var attachSound: String = "crisp_click"
        var detachSound: String = "soft_unplug"
        var soundVolume: Float = 0.70f
        var showPortLabels: Boolean = true
        var useIdentityLockColor: Boolean = false
    }

    var plugFxConfig: PlugFxConfig = PlugFxConfig()

    private data class PathSession(
        val cableId: String,
        val mode: String,
        val snapshotPoints: MutableList<PointF>,
        val draftPoints: MutableList<PointF>,
        var selectedNodeIdx: Int? = null,
        var isPainting: Boolean = false,
        val rawPaintSamples: MutableList<PointF> = mutableListOf(),
        var lastTapIdx: Int = -1,
        var lastTapTime: Long = 0L
    )

    private data class PlugPose(
        val x: Float,
        val y: Float,
        val angle: Float,
        val cosA: Float,
        val sinA: Float,
        val tailX: Float,
        val tailY: Float
    )

    private var pathSession: PathSession? = null
    private var draggingPenNodeIdx: Int = -1
    private var portsEnabled: Boolean = false

    fun setPortsEnabled(enabled: Boolean) {
        if (portsEnabled == enabled) return
        portsEnabled = enabled
        if (!enabled) {
            draggingPlugId = null
            pressedConnectionId = null
            selectedId = null
            fxList.clear()
            pathSession = null
            draggingPenNodeIdx = -1
        }
        updateAnimations()
        invalidate()
        saveState()
    }

    fun isPortsEnabled(): Boolean = portsEnabled

    private val connections = LinkedHashMap<String, WireConnection>()
    private val plugs = LinkedHashMap<String, PlugSpec>()
    private val cables = LinkedHashMap<String, CableSpec>()
    private val endpoints = HashMap<String, PointF>()
    private val paths = HashMap<String, Path>()
    private val progress = HashMap<String, Float>()
    private val animators = HashMap<String, ValueAnimator>()
    private val plugAngles = HashMap<String, Float>()

    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private fun dp(v: Float): Float = v * density

    private val prefs: SharedPreferences =
        context.getSharedPreferences("monolith_connection_layer", Context.MODE_PRIVATE)

    private fun saveState() {
        prefs.edit()
            .putBoolean("portsEnabled", portsEnabled)
            .putFloat("snapRadius", plugFxConfig.snapRadius)
            .putString("animEffect", plugFxConfig.animEffect)
            .putString("attachSound", plugFxConfig.attachSound)
            .putString("detachSound", plugFxConfig.detachSound)
            .putFloat("soundVolume", plugFxConfig.soundVolume)
            .putBoolean("showPortLabels", plugFxConfig.showPortLabels)
            .putBoolean("useIdentityLockColor", plugFxConfig.useIdentityLockColor)
            .apply()
    }

    private fun loadState() {
        portsEnabled = prefs.getBoolean("portsEnabled", false)
        plugFxConfig.snapRadius = prefs.getFloat("snapRadius", 38f)
        plugFxConfig.animEffect = prefs.getString("animEffect", "all") ?: "all"
        plugFxConfig.attachSound = prefs.getString("attachSound", "crisp_click") ?: "crisp_click"
        plugFxConfig.detachSound = prefs.getString("detachSound", "soft_unplug") ?: "soft_unplug"
        plugFxConfig.soundVolume = prefs.getFloat("soundVolume", 0.70f)
        plugFxConfig.showPortLabels = prefs.getBoolean("showPortLabels", true)
        plugFxConfig.useIdentityLockColor = prefs.getBoolean("useIdentityLockColor", false)
    }

    private val wirePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val plugBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        setShadowLayer(dp(3.5f), -dp(2f), dp(4f), Color.argb(204, 0, 0, 0))
    }
    private val plugBodyRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.4f)
        strokeJoin = Paint.Join.ROUND
    }
    private val plugPinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        strokeCap = Paint.Cap.ROUND
        shader = LinearGradient(
            0f, 0f, dp(8f), 0f,
            intArrayOf(
                Color.rgb(248, 250, 252),
                Color.rgb(203, 213, 225),
                Color.rgb(100, 116, 139)
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
    }
    private val plugCollarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.2f)
        color = Color.rgb(51, 65, 85)
    }
    private val gripRidgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.2f)
    }
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }

    private val fxRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val fxFlashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val paintPreviewPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(dp(6f), dp(5f)), 0f)
    }
    private val penNodeHandlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val penNodeStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = dp(2.5f)
    }

    private val fxList = mutableListOf<FxEvent>()
    private data class FxEvent(
        val type: String, val cx: Float, val cy: Float, val color: Int,
        val startMs: Long, val durationMs: Long, val fromR: Float = 0f, val toR: Float = 0f
    )

    private val pathMeasure = PathMeasure()

    private var selectedId: String? = null
    private var pressedConnectionId: String? = null
    private var draggingPlugId: String? = null
    private var dragOffsetX = 0f
    private var dragOffsetY = 0f

    private var wiresVisible = true
    private var glowEnabled = true
    private var animationEnabled = true
    private var directionVisible = true
    private var controlPointsVisible = false

    private var downX = 0f
    private var downY = 0f
    private var touchMoved = false

    private var plugClickLastKey: String? = null
    private var plugClickCount: Int = 0
    private var plugClickLastTime: Long = 0L

    var onConnectionSelected: ((WireConnection) -> Unit)? = null
    var onConnectionUpdated: ((WireConnection) -> Unit)? = null
    var onPlugMoved: ((plugId: String, position: PointF) -> Unit)? = null
    var onPlugAttached: ((plugId: String, portId: String) -> Unit)? = null
    var onPlugDetached: ((plugId: String) -> Unit)? = null
    var onPlugTripleClicked: ((plugId: String, x: Float, y: Float) -> Unit)? = null
    var onCablePathChanged: ((cableId: String) -> Unit)? = null
    var onPathSessionStateChanged: ((active: Boolean, cableId: String, mode: String) -> Unit)? = null

    init {
        setWillNotDraw(false)
        isClickable = true
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        loadState()
    }

    fun setGlowEnabled(e: Boolean) { glowEnabled = e; invalidate() }
    fun setAnimationEnabled(e: Boolean) { animationEnabled = e; updateAnimations(); invalidate() }
    fun setDirectionVisible(v: Boolean) { directionVisible = v; invalidate() }
    fun setControlPointsVisible(v: Boolean) { controlPointsVisible = v; invalidate() }

    fun setPlugs(items: List<PlugSpec>) {
        plugs.clear(); items.forEach { plugs[it.id] = it }
        if (draggingPlugId !in plugs.keys) draggingPlugId = null
        rebuildPaths(); invalidate()
    }
    fun updatePlug(plug: PlugSpec) { plugs[plug.id] = plug; rebuildPaths(); invalidate() }
    fun removePlug(id: String) { plugs.remove(id); if (draggingPlugId == id) draggingPlugId = null; rebuildPaths(); invalidate() }
    fun clearPlugs() { plugs.clear(); draggingPlugId = null; rebuildPaths(); invalidate() }

    fun detachPlug(plugId: String) {
        val cur = plugs[plugId] ?: return
        val pos = resolvePlugPosition(cur)
        plugs[plugId] = cur.copy(x = pos.x, y = pos.y, attachedPortId = null)
        rebuildPaths(); invalidate()
    }

    fun attachPlugToSocket(plugId: String, portId: String) {
        val cur = plugs[plugId] ?: return
        val c = endpoints[portId] ?: return
        plugs[plugId] = cur.copy(x = c.x, y = c.y, attachedPortId = portId)
        rebuildPaths(); invalidate()
    }

    fun setCables(items: List<CableSpec>) {
        cables.clear(); items.forEach { cables[it.id] = it }
        rebuildPaths(); invalidate()
    }
    fun getCables(): List<CableSpec> = cables.values.toList()
    fun clearCables() { cables.clear(); pathSession = null; draggingPenNodeIdx = -1; rebuildPaths(); invalidate() }

    fun setConnections(items: List<WireConnection>) {
        val newIds = items.mapTo(HashSet()) { it.id }
        connections.keys.filter { it !in newIds }.forEach { stopAnimation(it) }
        connections.clear(); items.forEach { connections[it.id] = it }
        if (selectedId !in connections.keys) selectedId = null
        rebuildPaths(); updateAnimations(); invalidate()
    }
    fun getConnections(): List<WireConnection> = connections.values.toList()

    fun removeConnection(id: String) {
        connections.remove(id); paths.remove(id); stopAnimation(id)
        if (selectedId == id) selectedId = null
        if (pressedConnectionId == id) pressedConnectionId = null
        invalidate()
    }
    fun selectConnection(id: String?) { selectedId = id?.takeIf { connections.containsKey(it) }; invalidate() }
    fun getSelectedConnection(): WireConnection? = selectedId?.let { connections[it] }

    fun updateEndpoint(portId: String, x: Float, y: Float) {
        val a = PointF(x, y); endpoints[portId] = a
        syncAttachedPlugsToSocket(portId, a); rebuildPaths(); invalidate()
    }
    fun updateEndpoints(points: Map<String, PointF>) {
        points.forEach { (id, p) ->
            val a = PointF(p.x, p.y); endpoints[id] = a; syncAttachedPlugsToSocket(id, a)
        }
        rebuildPaths(); invalidate()
    }
    fun removeEndpoint(portId: String) {
        plugs.entries.forEach { (id, p) ->
            if (p.attachedPortId == portId) {
                val pos = resolvePlugPosition(p)
                plugs[id] = p.copy(x = pos.x, y = pos.y, attachedPortId = null)
            }
        }
        endpoints.remove(portId); rebuildPaths(); invalidate()
    }
    fun clearEndpoints() { endpoints.clear(); rebuildPaths(); invalidate() }

    fun enterPathSession(cableId: String, mode: String) {
        val cable = cables[cableId] ?: return
        val initial = cable.customPoints.map { PointF(it.x, it.y) }.toMutableList()
        pathSession = PathSession(
            cableId = cableId,
            mode = if (mode == "paint") "paint" else "pen",
            snapshotPoints = cable.customPoints.map { PointF(it.x, it.y) }.toMutableList(),
            draftPoints = initial,
            selectedNodeIdx = if (initial.isNotEmpty()) 0 else null
        )
        draggingPenNodeIdx = -1
        onPathSessionStateChanged?.invoke(true, cableId, pathSession!!.mode)
        rebuildPaths(); invalidate()
    }
    fun getPathSessionCableId(): String? = pathSession?.cableId
    fun getPathSessionMode(): String? = pathSession?.mode
    fun isPathSessionActive(): Boolean = pathSession != null

    fun savePathSession() {
        val s = pathSession ?: return
        val cable = cables[s.cableId] ?: return
        cables[s.cableId] = cable.copy(customPoints = s.draftPoints.map { PointF(it.x, it.y) })
        pathSession = null; draggingPenNodeIdx = -1
        onPathSessionStateChanged?.invoke(false, cable.id, "")
        onCablePathChanged?.invoke(cable.id)
        rebuildPaths(); invalidate(); saveState()
    }

    fun cancelPathSession() {
        val s = pathSession ?: return
        pathSession = null; draggingPenNodeIdx = -1
        onPathSessionStateChanged?.invoke(false, s.cableId, "")
        rebuildPaths(); invalidate()
    }

    fun resetCablePath(cableId: String) {
        val cable = cables[cableId] ?: return
        cables[cableId] = cable.copy(customPoints = emptyList())
        if (pathSession?.cableId == cableId) {
            pathSession?.draftPoints?.clear(); pathSession?.snapshotPoints?.clear()
        }
        onCablePathChanged?.invoke(cableId); rebuildPaths(); invalidate(); saveState()
    }

    private fun simplifyPaintStrokeToControlPoints(samples: List<PointF>, start: PointF, end: PointF): List<PointF> {
        if (samples.size < 3) return emptyList()
        val min = dp(36f)
        val filtered = samples.filter {
            hypot(it.x - start.x, it.y - start.y) > min && hypot(it.x - end.x, it.y - end.y) > min
        }
        if (filtered.isEmpty()) return emptyList()
        val out = mutableListOf(PointF(filtered[0].x, filtered[0].y))
        var acc = 0f
        for (i in 1 until filtered.size) {
            acc += hypot(filtered[i].x - filtered[i-1].x, filtered[i].y - filtered[i-1].y)
            if (acc >= min) { out.add(PointF(filtered[i].x, filtered[i].y)); acc = 0f }
        }
        return out.take(7)
    }

    private fun buildCablePathInto(
        path: Path,
        x1: Float, y1: Float, dir1x: Float, dir1y: Float,
        x2: Float, y2: Float, dir2x: Float, dir2y: Float,
        customPoints: List<PointF>
    ) {
        if (customPoints.isEmpty()) {
            val dist = hypot(x2 - x1, y2 - y1)
            val pull = (dist * 0.45f).coerceIn(dp(50f), dp(220f))
            val sag = (dist * 0.15f).coerceIn(0f, dp(90f))
            path.moveTo(x1, y1)
            path.cubicTo(
                x1 + dir1x * pull, y1 + dir1y * pull + sag,
                x2 + dir2x * pull, y2 + dir2y * pull + sag,
                x2, y2
            )
            return
        }
        val pts = mutableListOf(PointF(x1, y1))
        pts.addAll(customPoints)
        pts.add(PointF(x2, y2))
        path.moveTo(pts[0].x, pts[0].y)
        for (i in 0 until pts.size - 1) {
            val p0 = pts[if (i == 0) i else i - 1]
            val p1 = pts[i]; val p2 = pts[i + 1]
            val p3 = pts[if (i + 2 < pts.size) i + 2 else i + 1]
            path.cubicTo(
                p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f,
                p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f,
                p2.x, p2.y
            )
        }
    }

    private fun syncAttachedPlugsToSocket(portId: String, anchor: PointF) {
        plugs.entries.forEach { (id, p) ->
            if (p.attachedPortId == portId && id != draggingPlugId) {
                plugs[id] = p.copy(x = anchor.x, y = anchor.y)
            }
        }
    }

    private fun findNearestSocketAnchor(pos: PointF): Pair<String, PointF>? {
        val r = dp(plugFxConfig.snapRadius)
        var best: Pair<String, PointF>? = null
        var bestD = r
        endpoints.forEach { (pid, pt) ->
            val d = hypot(pos.x - pt.x, pos.y - pt.y)
            if (d <= bestD) { bestD = d; best = Pair(pid, PointF(pt.x, pt.y)) }
        }
        return best
    }

    private fun resolvePlugPosition(plug: PlugSpec): PointF {
        val pid = plug.attachedPortId
        if (pid != null) { endpoints[pid]?.let { return PointF(it.x, it.y) } }
        return PointF(plug.x, plug.y)
    }

    private fun resolvePlugPose(endKey: String, plug: PlugSpec): PlugPose {
        val cable = cables.values.firstOrNull {
            (endKey == "from" && it.sourcePlugId == plug.id) ||
                (endKey == "to" && it.targetPlugId == plug.id)
        }
        val angleKey = "${cable?.id ?: plug.id}_$endKey"
        val isDragging = draggingPlugId == plug.id && touchMoved

        var x: Float
        var y: Float
        var angle: Float

        val attachedPortId = plug.attachedPortId
        if (attachedPortId != null) {
            val anchor = endpoints[attachedPortId]
            if (anchor != null) {
                x = anchor.x; y = anchor.y
                angle = if (attachedPortId.contains(":left:", true)) 0f else Math.PI.toFloat()
                plugAngles[angleKey] = angle
            } else {
                x = plug.x; y = plug.y
                angle = plugAngles[angleKey] ?: Math.toRadians(plug.angleDeg.toDouble()).toFloat()
            }
        } else if (isDragging) {
            val pos = resolvePlugPosition(plug)
            x = pos.x; y = pos.y
            var target = plugAngles[angleKey] ?: Math.toRadians(plug.angleDeg.toDouble()).toFloat()
            val nearest = findNearestSocketAnchor(pos)
            if (nearest != null) {
                target = if (nearest.first.contains(":left:", true)) 0f else Math.PI.toFloat()
            } else if (cable != null) {
                val otherKey = if (endKey == "to") "from" else "to"
                val otherPlugId = if (otherKey == "from") cable.sourcePlugId else cable.targetPlugId
                if (otherPlugId != null) {
                    val otherPlug = plugs[otherPlugId]
                    if (otherPlug != null) {
                        val otherPos = resolvePlugPosition(otherPlug)
                        target = atan2(y - otherPos.y, x - otherPos.x)
                    }
                }
            }
            val cur = plugAngles[angleKey] ?: target
            angle = cur + normalizeAngle(target - cur) * 0.28f
            plugAngles[angleKey] = angle
        } else if (cable != null && cable.detachedEnds.containsKey(endKey)) {
            val d = cable.detachedEnds[endKey]!!
            x = d.x; y = d.y
            angle = if (d.angle != 0f) d.angle else (plugAngles[angleKey] ?: 0f)
        } else {
            x = plug.x; y = plug.y
            angle = plugAngles[angleKey] ?: Math.toRadians(plug.angleDeg.toDouble()).toFloat()
        }

        val cosA = cos(angle); val sinA = sin(angle)
        val tailX = x - dp(32f) * cosA
        val tailY = y - dp(32f) * sinA
        return PlugPose(x, y, angle, cosA, sinA, tailX, tailY)
    }

    private fun normalizeAngle(a: Float): Float = atan2(sin(a), cos(a))

    private fun rebuildPaths() {
        paths.clear()
        connections.values.forEach { c ->
            val s = endpoints[c.sourcePortId] ?: return@forEach
            val e = endpoints[c.targetPortId] ?: return@forEach
            val path = Path()
            if (c.customPoints.isNotEmpty()) {
                buildCablePathInto(path, s.x, s.y, -1f, 0f, e.x, e.y, 1f, 0f, c.customPoints)
            } else {
                val dx = e.x - s.x; val dy = e.y - s.y
                val dist = hypot(dx, dy)
                val off = (dist * 0.28f).coerceIn(dp(35f), dp(170f))
                val sag = (dist * 0.15f).coerceIn(dp(10f), dp(90f))
                val c1 = c.control1 ?: PointF(s.x + dx * 0.32f, s.y - off + sag)
                val c2 = c.control2 ?: PointF(s.x + dx * 0.68f, e.y - off + sag)
                path.moveTo(s.x, s.y); path.cubicTo(c1.x, c1.y, c2.x, c2.y, e.x, e.y)
            }
            paths[c.id] = path
        }
        cables.values.forEach { cab ->
            val srcPlug = plugs[cab.sourcePlugId] ?: return@forEach
            val fromPose = resolvePlugPose("from", srcPlug)
            val tgtPlug = cab.targetPlugId?.let { plugs[it] }
            val toPose = if (tgtPlug != null) resolvePlugPose("to", tgtPlug) else null
            val effectivePts = if (pathSession?.cableId == cab.id) pathSession!!.draftPoints else cab.customPoints
            val path = Path()
            if (toPose != null) {
                buildCablePathInto(
                    path,
                    fromPose.tailX, fromPose.tailY, -fromPose.cosA, -fromPose.sinA,
                    toPose.tailX, toPose.tailY, -toPose.cosA, -toPose.sinA,
                    effectivePts
                )
            } else {
                val endX = fromPose.tailX + dp(46f) * fromPose.cosA
                val endY = fromPose.tailY + dp(28f) * fromPose.sinA
                buildCablePathInto(
                    path,
                    fromPose.tailX, fromPose.tailY, -fromPose.cosA, -fromPose.sinA,
                    endX, endY, 1f, 0f,
                    effectivePts
                )
            }
            paths["cable:${cab.id}"] = path
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!portsEnabled) return
        if (!wiresVisible) return

        connections.values.forEach { c ->
            if (!c.visible) return@forEach
            paths[c.id]?.let { drawWire(canvas, c, it) }
        }
        cables.values.forEach { cab ->
            if (!cab.visible) return@forEach
            paths["cable:${cab.id}"]?.let { drawCableSpec(canvas, cab, it) }
        }
        plugs.values.forEach { drawPlugHead(canvas, it) }
        drawPaintPreview(canvas)
        drawPenNodes(canvas)
        drawFxEvents(canvas)
    }

    private fun drawCableSpec(canvas: Canvas, cable: CableSpec, path: Path) {
        val width = dp(cable.strokeWidthDp)
        if (glowEnabled && cable.glowEnabled) {
            glowPaint.style = Paint.Style.STROKE
            glowPaint.color = cable.color; glowPaint.alpha = 55
            glowPaint.strokeWidth = width * 1.9f
            canvas.drawPath(path, glowPaint)
        }
        wirePaint.color = cable.color; wirePaint.alpha = 240; wirePaint.strokeWidth = width
        canvas.drawPath(path, wirePaint)
        drawSheen(canvas, path, width)
    }

    private fun drawSheen(canvas: Canvas, path: Path, width: Float) {
        sheenPaint.color = Color.argb(90, 255, 255, 255)
        sheenPaint.strokeWidth = width * 0.28f
        sheenPaint.strokeCap = Paint.Cap.ROUND
        canvas.drawPath(path, sheenPaint)
    }

    private fun drawPlugHead(canvas: Canvas, plug: PlugSpec) {
        val endKey = when {
            cables.values.any { it.sourcePlugId == plug.id } -> "from"
            cables.values.any { it.targetPlugId == plug.id } -> "to"
            else -> "from"
        }
        val pose = resolvePlugPose(endKey, plug)
        val save = canvas.save()
        canvas.translate(pose.x, pose.y)
        canvas.rotate(Math.toDegrees(pose.angle.toDouble()).toFloat())

        // Pins: x=0..8
        floatArrayOf(-dp(5.2f), -dp(1.7f), dp(1.7f), dp(5.2f)).forEach { y ->
            canvas.drawLine(0f, y, dp(8f), y, plugPinPaint)
        }

        // Collar at x=0
        canvas.drawLine(0f, -dp(9.5f), 0f, dp(9.5f), plugCollarPaint)

        // Body: -8..-22
        val body = Path().apply {
            moveTo(-dp(8f), -dp(8.5f)); lineTo(-dp(22f), -dp(8.5f))
            lineTo(-dp(22f), dp(8.5f)); lineTo(-dp(8f), dp(8.5f))
            close()
        }
        plugBodyPaint.color = plug.color; plugBodyPaint.alpha = 245
        canvas.drawPath(body, plugBodyPaint)
        plugBodyRimPaint.color = when (plug.color) {
            0xFF11161f.toInt() -> Color.rgb(71, 85, 105)
            0xFFf8fafc.toInt() -> Color.rgb(100, 116, 139)
            else -> Color.rgb(9, 13, 20)
        }
        canvas.drawPath(body, plugBodyRimPaint)

        // Grips -18..-12
        gripRidgePaint.color = if (plug.color == 0xFF11161f.toInt())
            Color.argb(51, 255, 255, 255) else Color.argb(71, 0, 0, 0)
        floatArrayOf(-dp(18f), -dp(15f), -dp(12f)).forEach { x ->
            canvas.drawLine(x, -dp(6f), x, dp(6f), gripRidgePaint)
        }

        // Tail -22..-32
        val tail = Path().apply {
            moveTo(-dp(22f), -dp(8.5f)); lineTo(-dp(32f), -dp(3.2f))
            lineTo(-dp(32f), dp(3.2f)); lineTo(-dp(22f), dp(8.5f))
            close()
        }
        canvas.drawPath(tail, plugBodyPaint)
        canvas.drawPath(tail, plugBodyRimPaint)

        canvas.restoreToCount(save)
    }

    private fun drawPaintPreview(canvas: Canvas) {
        val s = pathSession ?: return
        if (s.mode != "paint" || !s.isPainting || s.rawPaintSamples.size < 2) return
        val cable = cables[s.cableId] ?: return
        paintPreviewPaint.color = cable.color
        paintPreviewPaint.alpha = 217
        paintPreviewPaint.strokeWidth = dp(3f)
        val p = Path().apply {
            moveTo(s.rawPaintSamples[0].x, s.rawPaintSamples[0].y)
            for (i in 1 until s.rawPaintSamples.size) lineTo(s.rawPaintSamples[i].x, s.rawPaintSamples[i].y)
        }
        canvas.drawPath(p, paintPreviewPaint)
    }

    private fun drawPenNodes(canvas: Canvas) {
        val s = pathSession ?: return
        if (s.mode != "pen" || s.draftPoints.isEmpty()) return
        val cable = cables[s.cableId] ?: return
        val sel = s.selectedNodeIdx
        s.draftPoints.forEachIndexed { idx, pt ->
            val isSel = sel == idx
            val r = if (isSel) dp(8.5f) else dp(7f)
            penNodeHandlePaint.color = if (isSel) cable.color else Color.rgb(15, 23, 42)
            canvas.drawCircle(pt.x, pt.y, r, penNodeHandlePaint)
            penNodeStrokePaint.color = if (isSel) Color.WHITE else cable.color
            canvas.drawCircle(pt.x, pt.y, r, penNodeStrokePaint)
        }
    }

    private fun drawWire(canvas: Canvas, c: WireConnection, path: Path) {
        val selected = selectedId == c.id
        val w = dp(c.strokeWidthDp)
        if (glowEnabled && c.glowEnabled) {
            glowPaint.style = Paint.Style.STROKE
            glowPaint.color = c.color
            glowPaint.alpha = if (selected) 115 else 55
            glowPaint.strokeWidth = w * 4f
            canvas.drawPath(path, glowPaint)
            glowPaint.alpha = if (selected) 160 else 90
            glowPaint.strokeWidth = w * 2f
            canvas.drawPath(path, glowPaint)
        }
        wirePaint.color = c.color; wirePaint.alpha = 245
        wirePaint.strokeWidth = if (selected) w * 1.5f else w
        canvas.drawPath(path, wirePaint)
        drawSheen(canvas, path, w)
        if (animationEnabled && c.animated) drawPulse(canvas, path, c)
        if (directionVisible) drawDirection(canvas, path, c)
        if (controlPointsVisible && selected) drawControls(canvas, c)
    }

    private fun drawPulse(canvas: Canvas, path: Path, c: WireConnection) {
        pathMeasure.setPath(path, false)
        val len = pathMeasure.length
        if (len <= 0f) return
        val pos = FloatArray(2)
        val frac = progress[c.id] ?: 0f
        if (!pathMeasure.getPosTan(len * frac, pos, null)) return
        glowPaint.style = Paint.Style.FILL
        glowPaint.color = c.color; glowPaint.alpha = 100
        canvas.drawCircle(pos[0], pos[1], dp(10f), glowPaint)
        pulsePaint.color = Color.WHITE; pulsePaint.alpha = 245
        canvas.drawCircle(pos[0], pos[1], dp(2.8f), pulsePaint)
        glowPaint.style = Paint.Style.STROKE
    }

    private fun drawDirection(canvas: Canvas, path: Path, c: WireConnection) {
        when (c.direction) {
            ConnectionDirection.BIDIRECTIONAL -> { drawArrow(canvas, path, 0.30f, c.color); drawArrow(canvas, path, 0.70f, c.color) }
            ConnectionDirection.OUTGOING -> drawArrow(canvas, path, 0.72f, c.color)
            ConnectionDirection.INCOMING -> drawArrow(canvas, path, 0.28f, c.color)
        }
    }

    private fun drawArrow(canvas: Canvas, path: Path, frac: Float, color: Int) {
        pathMeasure.setPath(path, false)
        val len = pathMeasure.length
        if (len <= 0f) return
        val pos = FloatArray(2); val tan = FloatArray(2)
        if (!pathMeasure.getPosTan(len * frac, pos, tan)) return
        val ang = atan2(tan[1], tan[0])
        val size = dp(8f)
        val spread = Math.toRadians(145.0).toFloat()
        arrowPaint.color = color; arrowPaint.alpha = 245; arrowPaint.strokeWidth = dp(1.8f)
        canvas.drawLine(pos[0], pos[1], pos[0] + cos(ang + spread) * size, pos[1] + sin(ang + spread) * size, arrowPaint)
        canvas.drawLine(pos[0], pos[1], pos[0] + cos(ang - spread) * size, pos[1] + sin(ang - spread) * size, arrowPaint)
    }

    private fun drawControls(canvas: Canvas, c: WireConnection) {
        val s = endpoints[c.sourcePortId] ?: return
        val e = endpoints[c.targetPortId] ?: return
        val dx = e.x - s.x; val dy = e.y - s.y
        val dist = hypot(dx, dy)
        val off = (dist * 0.28f).coerceIn(dp(35f), dp(170f))
        val sag = (dist * 0.15f).coerceIn(dp(10f), dp(90f))
        val c1 = c.control1 ?: PointF(s.x + dx * 0.32f, s.y - off + sag)
        val c2 = c.control2 ?: PointF(s.x + dx * 0.68f, e.y - off + sag)
        arrowPaint.color = c.color; arrowPaint.alpha = 120; arrowPaint.strokeWidth = dp(1f)
        canvas.drawLine(s.x, s.y, c1.x, c1.y, arrowPaint)
        canvas.drawLine(e.x, e.y, c2.x, c2.y, arrowPaint)
        pointPaint.color = c.color
        canvas.drawCircle(c1.x, c1.y, dp(6f), pointPaint)
        canvas.drawCircle(c2.x, c2.y, dp(6f), pointPaint)
        pointPaint.color = Color.WHITE
        canvas.drawCircle(c1.x, c1.y, dp(2.2f), pointPaint)
        canvas.drawCircle(c2.x, c2.y, dp(2.2f), pointPaint)
    }

    fun triggerSnapRing(cx: Float, cy: Float, color: Int) {
        if (!portsEnabled) return
        if (plugFxConfig.animEffect != "all" && plugFxConfig.animEffect != "snap_ring") return
        fxList.add(FxEvent("snap_ring", cx, cy, color, System.currentTimeMillis(), 320L, dp(24f), dp(5f)))
        invalidate()
    }
    fun triggerDetachRelease(cx: Float, cy: Float, color: Int) {
        if (!portsEnabled) return
        if (plugFxConfig.animEffect != "all" && plugFxConfig.animEffect != "detach_release") return
        fxList.add(FxEvent("detach_release", cx, cy, color, System.currentTimeMillis(), 350L, dp(4f), dp(28f)))
        invalidate()
    }
    fun triggerSignalFlash(cx: Float, cy: Float, color: Int) {
        if (!portsEnabled) return
        if (plugFxConfig.animEffect != "all" && plugFxConfig.animEffect != "signal_flash") return
        fxList.add(FxEvent("signal_flash", cx, cy, color, System.currentTimeMillis(), 260L))
        invalidate()
    }

    private fun drawFxEvents(canvas: Canvas) {
        if (fxList.isEmpty()) return
        val now = System.currentTimeMillis()
        val it = fxList.iterator()
        while (it.hasNext()) {
            val fx = it.next(); val el = now - fx.startMs
            if (el >= fx.durationMs) { it.remove(); continue }
            val t = el.toFloat() / fx.durationMs
            when (fx.type) {
                "snap_ring", "detach_release" -> {
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

    private fun updateAnimations() {
        if (!portsEnabled) { animators.values.forEach { it.cancel() }; animators.clear(); progress.clear(); return }
        connections.values.forEach { c ->
            val should = isAttachedToWindow && wiresVisible && animationEnabled && c.visible && c.animated
            if (should) startAnimation(c) else stopAnimation(c.id)
        }
    }

    private fun startAnimation(c: WireConnection) {
        val ex = animators[c.id]; val dur = c.animationDuration.coerceAtLeast(100L)
        if (ex != null) { if (ex.duration == dur && ex.isRunning) return; stopAnimation(c.id) }
        val a = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = dur; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
            addUpdateListener { progress[c.id] = it.animatedValue as Float; invalidate() }
        }
        animators[c.id] = a; a.start()
    }
    private fun stopAnimation(id: String) { animators.remove(id)?.cancel(); progress.remove(id) }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); updateAnimations() }
    override fun onDetachedFromWindow() {
        animators.values.forEach { it.cancel() }; animators.clear(); progress.clear(); fxList.clear()
        pathSession = null; draggingPenNodeIdx = -1
        clearActivePlugDrag(false); pressedConnectionId = null; touchMoved = false
        super.onDetachedFromWindow()
    }

    private fun findPlugAt(x: Float, y: Float): PlugSpec? {
        val r = dp(18f)
        return plugs.values.toList().asReversed().firstOrNull {
            val p = resolvePlugPosition(it)
            hypot(x - p.x, y - p.y) <= r
        }
    }

    private fun clearActivePlugDrag(reset: Boolean) {
        val id = draggingPlugId; draggingPlugId = null
        if (id != null) plugs[id]?.let { p ->
            if (reset) {
                val pos = resolvePlugPosition(p)
                plugs[id] = p.copy(x = pos.x, y = pos.y, isDragging = false)
            } else if (p.isDragging) plugs[id] = p.copy(isDragging = false)
        }
    }

    private fun findCableAt(x: Float, y: Float, tol: Float = dp(18f)): CableSpec? {
        val pos = FloatArray(2)
        return cables.values.toList().asReversed().firstOrNull { cab ->
            if (!cab.visible) return@firstOrNull false
            val path = paths["cable:${cab.id}"] ?: return@firstOrNull false
            pathMeasure.setPath(path, false)
            val len = pathMeasure.length
            if (len <= 0f) return@firstOrNull false
            val steps = (len / dp(8f)).toInt().coerceIn(24, 240)
            for (i in 0..steps) {
                val d = len * i / steps.toFloat()
                if (!pathMeasure.getPosTan(d, pos, null)) continue
                if (hypot(x - pos[0], y - pos[1]) <= tol) return@firstOrNull true
            }
            false
        }
    }

    private fun findClosestPenNodeIdx(pts: List<PointF>, x: Float, y: Float, r: Float): Int {
        var best = -1; var bd = r
        pts.forEachIndexed { i, p -> val d = hypot(x - p.x, y - p.y); if (d <= bd) { bd = d; best = i } }
        return best
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!portsEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x; downY = event.y; touchMoved = false
                pressedConnectionId = null; clearActivePlugDrag(false)

                val s = pathSession
                if (s != null && s.mode == "paint" && findPlugAt(event.x, event.y) == null) {
                    s.isPainting = true; s.rawPaintSamples.clear()
                    s.rawPaintSamples.add(PointF(event.x, event.y)); invalidate(); return true
                }
                if (s != null && s.mode == "pen") {
                    val cableAt = findCableAt(event.x, event.y)
                    if (cableAt != null && cableAt.id == s.cableId) {
                        val idx = findClosestPenNodeIdx(s.draftPoints, event.x, event.y, dp(18f))
                        if (idx != -1) {
                            val now = System.currentTimeMillis()
                            if (s.lastTapIdx == idx && (now - s.lastTapTime) < 300L) {
                                s.draftPoints.removeAt(idx); s.selectedNodeIdx = null
                                s.lastTapIdx = -1; s.lastTapTime = 0L
                                rebuildPaths(); invalidate(); return true
                            }
                            s.lastTapIdx = idx; s.lastTapTime = now
                            s.selectedNodeIdx = idx; draggingPenNodeIdx = idx
                            invalidate(); return true
                        }
                        s.draftPoints.add(PointF(event.x, event.y))
                        s.selectedNodeIdx = s.draftPoints.size - 1
                        rebuildPaths(); invalidate(); return true
                    }
                }
                if (!wiresVisible) return false
                val plugHit = findPlugAt(event.x, event.y)
                if (plugHit != null) {
                    val pos = resolvePlugPosition(plugHit)
                    draggingPlugId = plugHit.id
                    dragOffsetX = pos.x - event.x; dragOffsetY = pos.y - event.y
                    plugs[plugHit.id] = plugHit.copy(isDragging = true); return true
                }
                pressedConnectionId = findConnectionAt(event.x, event.y)?.id
                return pressedConnectionId != null
            }
            MotionEvent.ACTION_MOVE -> {
                val s = pathSession
                if (s != null && s.mode == "paint" && s.isPainting) {
                    s.rawPaintSamples.add(PointF(event.x, event.y)); invalidate(); return true
                }
                if (s != null && s.mode == "pen" && draggingPenNodeIdx != -1) {
                    if (draggingPenNodeIdx < s.draftPoints.size) {
                        s.draftPoints[draggingPenNodeIdx] = PointF(event.x, event.y)
                        rebuildPaths(); invalidate()
                    }
                    return true
                }
                val id = draggingPlugId
                if (id != null) {
                    val p = plugs[id] ?: return false
                    if (hypot(event.x - downX, event.y - downY) > touchSlop) touchMoved = true
                    val cur = resolvePlugPosition(p)
                    val nx = event.x + dragOffsetX; val ny = event.y + dragOffsetY
                    val updated = p.copy(
                        x = if (touchMoved) nx else cur.x,
                        y = if (touchMoved) ny else cur.y,
                        attachedPortId = if (touchMoved) null else p.attachedPortId,
                        isDragging = true
                    )
                    plugs[id] = updated; rebuildPaths(); invalidate()
                    onPlugMoved?.invoke(id, PointF(updated.x, updated.y)); return true
                }
                if (pressedConnectionId == null) return false
                if (hypot(event.x - downX, event.y - downY) > touchSlop) touchMoved = true
                return true
            }
            MotionEvent.ACTION_UP -> {
                val s = pathSession
                if (s != null && s.mode == "paint" && s.isPainting) {
                    s.isPainting = false
                    val cab = cables[s.cableId]
                    if (cab != null) {
                        val startPlug = plugs[cab.sourcePlugId]
                        val endPlug = cab.targetPlugId?.let { plugs[it] }
                        if (startPlug != null && endPlug != null) {
                            val from = resolvePlugPose("from", startPlug)
                            val to = resolvePlugPose("to", endPlug)
                            val gen = simplifyPaintStrokeToControlPoints(
                                s.rawPaintSamples, PointF(from.tailX, from.tailY), PointF(to.tailX, to.tailY)
                            )
                            if (gen.isNotEmpty()) { s.draftPoints.clear(); s.draftPoints.addAll(gen) }
                        }
                    }
                    s.rawPaintSamples.clear(); rebuildPaths(); invalidate(); return true
                }
                if (s != null && s.mode == "pen" && draggingPenNodeIdx != -1) {
                    draggingPenNodeIdx = -1; rebuildPaths(); invalidate(); return true
                }
                val id = draggingPlugId
                if (id != null) {
                    draggingPlugId = null
                    val p = plugs[id]
                    if (p != null) {
                        val cur = resolvePlugPosition(p)
                        val snapped = findNearestSocketAnchor(cur)
                        val wasDetached = p.attachedPortId == null
                        val updated = if (snapped != null) {
                            p.copy(x = snapped.second.x, y = snapped.second.y, attachedPortId = snapped.first, isDragging = false)
                        } else {
                            p.copy(x = cur.x, y = cur.y, attachedPortId = null, isDragging = false)
                        }
                        plugs[id] = updated; rebuildPaths(); invalidate()
                        if (snapped != null) {
                            triggerSnapRing(snapped.second.x, snapped.second.y, updated.color)
                            onPlugAttached?.invoke(id, snapped.first)
                        } else if (!wasDetached) {
                            triggerDetachRelease(cur.x, cur.y, updated.color)
                            onPlugDetached?.invoke(id)
                        }
                        onPlugMoved?.invoke(id, PointF(updated.x, updated.y))
                        if (!touchMoved) {
                            val now = System.currentTimeMillis()
                            if (plugClickLastKey == id && (now - plugClickLastTime) < 550L) plugClickCount++
                            else { plugClickCount = 1; plugClickLastKey = id }
                            plugClickLastTime = now
                            if (plugClickCount >= 3) {
                                plugClickCount = 0; plugClickLastKey = null
                                onPlugTripleClicked?.invoke(id, event.x, event.y)
                            }
                        }
                    }
                    if (!touchMoved) performClick(); return true
                }
                val pid = pressedConnectionId; pressedConnectionId = null
                if (pid == null) return false
                if (touchMoved || hypot(event.x - downX, event.y - downY) > touchSlop) return true
                val conn = findConnectionAt(event.x, event.y)
                if (conn == null || conn.id != pid) return true
                selectedId = conn.id; onConnectionSelected?.invoke(conn); invalidate(); performClick(); return true
            }
            MotionEvent.ACTION_CANCEL -> {
                val s = pathSession
                if (s != null && s.mode == "paint" && s.isPainting) { s.isPainting = false; s.rawPaintSamples.clear(); invalidate(); return true }
                if (s != null && s.mode == "pen" && draggingPenNodeIdx != -1) { draggingPenNodeIdx = -1; return true }
                pressedConnectionId = null; clearActivePlugDrag(true); touchMoved = false
                rebuildPaths(); invalidate(); return true
            }
            else -> return pressedConnectionId != null || draggingPlugId != null || pathSession != null
        }
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    private fun findConnectionAt(x: Float, y: Float): WireConnection? {
        val tol = dp(14f); val pos = FloatArray(2)
        return connections.values.toList().asReversed().firstOrNull { c ->
            if (!c.visible) return@firstOrNull false
            val path = paths[c.id] ?: return@firstOrNull false
            pathMeasure.setPath(path, false)
            val len = pathMeasure.length
            if (len <= 0f) return@firstOrNull false
            val steps = (len / dp(8f)).toInt().coerceIn(24, 240)
            for (i in 0..steps) {
                val d = len * i / steps.toFloat()
                if (!pathMeasure.getPosTan(d, pos, null)) continue
                if (hypot(x - pos[0], y - pos[1]) <= tol) return@firstOrNull true
            }
            false
        }
    }
}

package com.monolith.graph.ports.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.PointF
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.FrameLayout
import com.monolith.graph.ports.models.*
import kotlin.math.roundToInt

class PortClusterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private data class PortEntry(
        val view: PortView,
        val label: String,
        val slotIndex: Int
    )

    private data class ContentBounds(
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int
    ) {
        val right: Int
            get() = left + width

        val bottom: Int
            get() = top + height
    }

    var ownerId: String = ""
        set(value) {
            if (field == value) return
            field = value
            refreshIds()
        }

    var config: PortLayoutConfig = PortLayoutConfig()
        set(value) {
            field = value
            updateReservedSpace()
            requestLayout()
            invalidate()
        }

    var showChassisRails: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var onPortSelected: ((PortSpec) -> Unit)? = null

    private val ports = LinkedHashMap<String, PortEntry>()
    private val nextSlotBySide = HashMap<PortSide, Int>()

    private val density = resources.displayMetrics.density

    private fun dp(value: Float): Int =
        (value * density).roundToInt()

    private fun dpF(value: Float): Float =
        value * density

    // مواضع المنافذ الرأسية المطابقة للمعاينة: 28, 56, 84, 112 dp من أعلى البطاقة
    private val referenceOffsetsDp = floatArrayOf(28f, 56f, 84f, 112f)

    private val portOutset: Int
        get() = 0

    init {
        clipChildren = false
        clipToPadding = false
        setWillNotDraw(false)
        updateReservedSpace()
    }

    private fun updateReservedSpace() {
        val inset = dp(config.edgeInsetDp.coerceAtLeast(0f))
        val reserved = portOutset + inset
        setPadding(reserved, reserved, reserved, reserved)
    }

    private fun getContentBounds(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ): ContentBounds {
        return ContentBounds(
            left = paddingLeft,
            top = paddingTop,
            width = (right - left - paddingLeft - paddingRight).coerceAtLeast(0),
            height = (bottom - top - paddingTop - paddingBottom).coerceAtLeast(0)
        )
    }

    private fun edgeInsetPx(): Float =
        dpF(config.edgeInsetDp.coerceAtLeast(0f))

    private fun leftEdgeX(content: ContentBounds): Float =
        content.left - edgeInsetPx()

    private fun rightEdgeX(content: ContentBounds): Float =
        content.right + edgeInsetPx()

    private fun topEdgeY(content: ContentBounds): Float =
        content.top - edgeInsetPx()

    private fun bottomEdgeY(content: ContentBounds): Float =
        content.bottom + edgeInsetPx()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!showChassisRails || ports.isEmpty()) return
    }

    fun addPort(
        side: PortSide,
        kind: PortKind = PortKind.CONTEXT,
        state: PortState = PortState.IDLE,
        label: String = "",
        shape: PortShape = PortShape.USB_C,
        enabled: Boolean = true
    ): PortView? {
        val sideCount = ports.values.count { it.view.portSide == side }
        val maxPorts = config.maxPortsPerSide.coerceAtLeast(0)
        if (sideCount >= maxPorts) return null

        val slotIndex = allocateNextSlot(side)
        val id = createPortId(side, slotIndex)
        ports[id]?.let { return it.view }

        val port = PortView(context).apply {
            portId = id
            portSide = side
            portKind = kind
            this.shape = shape
            this.state = state
            isEnabled = enabled
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        port.onPortClick = { selected ->
            if (selected.isEnabled && selected.state != PortState.DISABLED) {
                onPortSelected?.invoke(
                    PortSpec(
                        id = selected.portId,
                        ownerId = ownerId,
                        side = selected.portSide,
                        index = getSideIndex(selected),
                        kind = selected.portKind,
                        state = selected.state,
                        shape = selected.shape,
                        label = label,
                        enabled = selected.isEnabled
                    )
                )
            }
        }

        ports[id] = PortEntry(view = port, label = label, slotIndex = slotIndex)
        addView(port)
        requestLayout()
        invalidate()
        return port
    }

    fun createDefaultPorts() {
        if (ports.isNotEmpty()) return
        listOf(PortSide.LEFT, PortSide.RIGHT).forEach { side ->
            for (i in 0 until 4) {
                addPort(side)
            }
        }
    }

    fun removePort(portId: String) {
        val entry = ports.remove(portId) ?: return
        entry.view.onPortClick = null
        removeView(entry.view)
        requestLayout()
        invalidate()
    }

    fun clearPorts() {
        ports.values.forEach { it.view.onPortClick = null }
        ports.clear()
        nextSlotBySide.clear()
        removeAllViews()
        requestLayout()
        invalidate()
    }

    fun getPort(portId: String): PortView? = ports[portId]?.view

    fun getPorts(side: PortSide): List<PortView> =
        ports.values.filter { it.view.portSide == side }.map { it.view }

    fun getAllPorts(): List<PortView> = ports.values.map { it.view }

    fun getPortSpecs(): List<PortSpec> {
        val indexes = mutableMapOf<PortSide, Int>()
        return ports.values.map { entry ->
            val port = entry.view
            val index = indexes[port.portSide] ?: 0
            indexes[port.portSide] = index + 1
            PortSpec(
                id = port.portId,
                ownerId = ownerId,
                side = port.portSide,
                index = index,
                kind = port.portKind,
                state = port.state,
                shape = port.shape,
                label = entry.label,
                enabled = port.isEnabled
            )
        }
    }

    fun setPortState(portId: String, state: PortState) {
        ports[portId]?.view?.state = state
    }

    fun setPortKind(portId: String, kind: PortKind) {
        ports[portId]?.view?.portKind = kind
    }

    fun setPortShape(portId: String, shape: PortShape) {
        ports[portId]?.view?.shape = shape
        requestLayout()
        invalidate()
    }

    fun setPortEnabled(portId: String, enabled: Boolean) {
        ports[portId]?.view?.isEnabled = enabled
    }

    fun setAllPortsState(state: PortState) {
        ports.values.forEach { it.view.state = state }
    }

    fun setAllPortsEnabled(enabled: Boolean) {
        ports.values.forEach { it.view.isEnabled = enabled }
    }

    private fun allocateNextSlot(side: PortSide): Int {
        val next = nextSlotBySide[side] ?: 0
        nextSlotBySide[side] = next + 1
        return next
    }

    private fun refreshIds() {
        if (ports.isEmpty()) return
        val entries = ports.values.toList()
        val rebuilt = LinkedHashMap<String, PortEntry>()
        entries.forEach { entry ->
            val port = entry.view
            val newId = createPortId(port.portSide, entry.slotIndex)
            port.portId = newId
            rebuilt[newId] = entry
        }
        ports.clear()
        ports.putAll(rebuilt)
        requestLayout()
        invalidate()
    }

    private fun createPortId(side: PortSide, index: Int): String {
        return "$ownerId:${side.name.lowercase()}:$index"
    }

    private fun getSideIndex(port: PortView): Int {
        var index = 0
        for (entry in ports.values) {
            val current = entry.view
            if (current.portSide != port.portSide) continue
            if (current === port) return index
            index++
        }
        return -1
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val childSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        ports.values.forEach { entry ->
            measureChild(entry.view, childSpec, childSpec)
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val content = getContentBounds(left, top, right, bottom)
        PortSide.values().forEach { side ->
            layoutSide(side, content)
        }
    }

    private fun getSocketCenter(port: PortView): PointF = port.getSocketCenter()

    private fun calculatePortPosition(
        side: PortSide,
        port: PortView,
        content: ContentBounds,
        edgeCenter: Int
    ): Pair<Int, Int> {
        val socketCenter = getSocketCenter(port)
        val targetSocketX: Float
        val targetSocketY: Float

        when (side) {
            PortSide.LEFT -> {
                targetSocketX = leftEdgeX(content)
                targetSocketY = edgeCenter.toFloat()
            }
            PortSide.RIGHT -> {
                targetSocketX = rightEdgeX(content)
                targetSocketY = edgeCenter.toFloat()
            }
            PortSide.TOP -> {
                targetSocketX = edgeCenter.toFloat()
                targetSocketY = topEdgeY(content)
            }
            PortSide.BOTTOM -> {
                targetSocketX = edgeCenter.toFloat()
                targetSocketY = bottomEdgeY(content)
            }
        }

        val portX = (targetSocketX - socketCenter.x).roundToInt()
        val portY = (targetSocketY - socketCenter.y).roundToInt()
        return Pair(portX, portY)
    }

    private fun layoutSide(side: PortSide, content: ContentBounds) {
        val sidePorts = ports.values.map { it.view }.filter { it.portSide == side }
        if (sidePorts.isEmpty()) return

        val horizontalEdge = side == PortSide.LEFT || side == PortSide.RIGHT
        val availableSpace = if (horizontalEdge) content.height else content.width
        if (availableSpace <= 0) {
            sidePorts.forEach { it.layout(0, 0, 0, 0) }
            return
        }

        val centers = calculateCenters(
            count = sidePorts.size,
            availableSpace = availableSpace,
            horizontalEdge = horizontalEdge
        )

        val edgeOrigin = if (horizontalEdge) content.top else content.left

        sidePorts.forEachIndexed { index, port ->
            val portWidth = port.measuredWidth
            val portHeight = port.measuredHeight
            val edgeCenter = edgeOrigin + centers[index]

            val (portX, portY) = calculatePortPosition(
                side = side,
                port = port,
                content = content,
                edgeCenter = edgeCenter
            )

            port.layout(portX, portY, portX + portWidth, portY + portHeight)
        }
    }

    private fun calculateCenters(
        count: Int,
        availableSpace: Int,
        horizontalEdge: Boolean
    ): List<Int> {
        if (count <= 0) return emptyList()
        if (horizontalEdge) {
            return (0 until count).map { idx ->
                if (idx < referenceOffsetsDp.size) {
                    dp(referenceOffsetsDp[idx])
                } else {
                    val lastBase = dp(referenceOffsetsDp.last())
                    val step = dp(28f)
                    lastBase + (idx - referenceOffsetsDp.size + 1) * step
                }
            }
        }
        val stepPx = availableSpace / (count + 1f)
        return (1..count).map { (stepPx * it).roundToInt() }
    }
}

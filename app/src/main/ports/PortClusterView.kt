
package com.monolith.graph.ports.views

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.FrameLayout
import com.monolith.graph.ports.models.*
import kotlin.math.max

class PortClusterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private data class PortEntry(
        val view: PortView,
        val label: String
    )

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

    var onPortSelected: ((PortSpec) -> Unit)? = null

    private val ports = LinkedHashMap<String, PortEntry>()

    private val density = resources.displayMetrics.density

    private fun dp(value: Float): Int =
        (value * density).toInt()

    /*
     * PortView dimensions:
     * Horizontal: 54 x 30 dp
     * Vertical:   30 x 54 dp
     *
     * Reserve enough room for half of the port body.
     * The remaining half visually extends beyond the
     * card edge, producing the mounted connector effect.
     */
    private val portOutset: Int
        get() = dp(27f)

    init {
        clipChildren = false
        clipToPadding = false

        updateReservedSpace()
    }

    /*
     * The padding defines the actual card rectangle
     * inside this cluster.
     *
     * edgeInset is included in the reserved area so
     * the connector center remains symmetric on all
     * four sides.
     */
    private fun updateReservedSpace() {
        val inset = dp(
            config.edgeInsetDp.coerceAtLeast(0f)
        )

        val reserved = portOutset + inset

        setPadding(
            reserved,
            reserved,
            reserved,
            reserved
        )
    }

    // ---------------------------------------------------------
    // Port creation
    // ---------------------------------------------------------

    fun addPort(
        side: PortSide,
        kind: PortKind = PortKind.CONTEXT,
        state: PortState = PortState.IDLE,
        label: String = "",
        shape: PortShape = PortShape.USB_C,
        enabled: Boolean = true
    ): PortView? {

        val sideCount = ports.values.count {
            it.view.portSide == side
        }

        val maxPorts =
            config.maxPortsPerSide.coerceAtLeast(0)

        if (sideCount >= maxPorts) return null

        val id = createPortId(side, sideCount)

        ports[id]?.let {
            return it.view
        }

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
            if (
                selected.isEnabled &&
                selected.state != PortState.DISABLED
            ) {
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

        ports[id] = PortEntry(
            view = port,
            label = label
        )

        addView(port)
        requestLayout()
        invalidate()

        return port
    }

    fun createDefaultPorts() {
        if (ports.isNotEmpty()) return

        PortSide.values().forEach { side ->
            addPort(side)
        }
    }

    // ---------------------------------------------------------
    // Port removal
    // ---------------------------------------------------------

    fun removePort(portId: String) {
        val entry = ports.remove(portId) ?: return

        entry.view.onPortClick = null
        removeView(entry.view)

        refreshIds()

        requestLayout()
        invalidate()
    }

    fun clearPorts() {
        ports.values.forEach { entry ->
            entry.view.onPortClick = null
        }

        ports.clear()
        removeAllViews()

        requestLayout()
        invalidate()
    }

    // ---------------------------------------------------------
    // Port access
    // ---------------------------------------------------------

    fun getPort(portId: String): PortView? =
        ports[portId]?.view

    fun getPorts(side: PortSide): List<PortView> =
        ports.values
            .filter { it.view.portSide == side }
            .map { it.view }

    fun getAllPorts(): List<PortView> =
        ports.values.map { it.view }

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

    // ---------------------------------------------------------
    // Port state and appearance
    // ---------------------------------------------------------

    fun setPortState(
        portId: String,
        state: PortState
    ) {
        ports[portId]?.view?.state = state
    }

    fun setPortKind(
        portId: String,
        kind: PortKind
    ) {
        ports[portId]?.view?.portKind = kind
    }

    fun setPortShape(
        portId: String,
        shape: PortShape
    ) {
        ports[portId]?.view?.shape = shape
        requestLayout()
        invalidate()
    }

    fun setPortEnabled(
        portId: String,
        enabled: Boolean
    ) {
        ports[portId]?.view?.isEnabled = enabled
    }

    fun setAllPortsState(state: PortState) {
        ports.values.forEach {
            it.view.state = state
        }
    }

    fun setAllPortsEnabled(enabled: Boolean) {
        ports.values.forEach {
            it.view.isEnabled = enabled
        }
    }

    // ---------------------------------------------------------
    // Port IDs
    // ---------------------------------------------------------

    private fun refreshIds() {
        if (ports.isEmpty()) return

        val entries = ports.values.toList()
        val rebuilt = LinkedHashMap<String, PortEntry>()

        val indexes = mutableMapOf<PortSide, Int>()

        entries.forEach { entry ->
            val port = entry.view

            val index = indexes[port.portSide] ?: 0
            indexes[port.portSide] = index + 1

            val newId = createPortId(
                port.portSide,
                index
            )

            port.portId = newId
            rebuilt[newId] = entry
        }

        ports.clear()
        ports.putAll(rebuilt)

        requestLayout()
        invalidate()
    }

    private fun createPortId(
        side: PortSide,
        index: Int
    ): String {
        return "$ownerId:${side.name.lowercase()}:$index"
    }

    private fun getSideIndex(port: PortView): Int {
        var index = 0

        for (entry in ports.values) {
            val current = entry.view

            if (current.portSide != port.portSide) {
                continue
            }

            if (current === port) return index

            index++
        }

        return -1
    }

    // ---------------------------------------------------------
    // Measurement
    // ---------------------------------------------------------

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int
    ) {
        super.onMeasure(
            widthMeasureSpec,
            heightMeasureSpec
        )
    }

    // ---------------------------------------------------------
    // Layout
    // ---------------------------------------------------------

    override fun onLayout(
        changed: Boolean,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {
        val contentLeft = paddingLeft
        val contentTop = paddingTop

        val contentWidth = (
            right - left -
                paddingLeft -
                paddingRight
            ).coerceAtLeast(0)

        val contentHeight = (
            bottom - top -
                paddingTop -
                paddingBottom
            ).coerceAtLeast(0)

        layoutSide(
            side = PortSide.LEFT,
            width = contentWidth,
            height = contentHeight,
            offsetX = contentLeft,
            offsetY = contentTop
        )

        layoutSide(
            side = PortSide.RIGHT,
            width = contentWidth,
            height = contentHeight,
            offsetX = contentLeft,
            offsetY = contentTop
        )

        layoutSide(
            side = PortSide.TOP,
            width = contentWidth,
            height = contentHeight,
            offsetX = contentLeft,
            offsetY = contentTop
        )

        layoutSide(
            side = PortSide.BOTTOM,
            width = contentWidth,
            height = contentHeight,
            offsetX = contentLeft,
            offsetY = contentTop
        )
    }

    private fun layoutSide(
        side: PortSide,
        width: Int,
        height: Int,
        offsetX: Int,
        offsetY: Int
    ) {
        val sidePorts = ports.values
            .map { it.view }
            .filter { it.portSide == side }

        if (sidePorts.isEmpty()) return

        val horizontalEdge =
            side == PortSide.LEFT ||
            side == PortSide.RIGHT

        val availableSpace =
            if (horizontalEdge) height else width

        if (availableSpace <= 0) {
            sidePorts.forEach {
                it.layout(0, 0, 0, 0)
            }
            return
        }

        val requestedSpacing = dp(
            (
                if (horizontalEdge) {
                    config.verticalSpacingDp
                } else {
                    config.horizontalSpacingDp
                }
            ).coerceAtLeast(0f)
        )

        /*
         * The spacing must account for the actual
         * footprint of each connector along its edge.
         *
         * This prevents adjacent connectors from
         * overlapping when several ports share a side.
         */
        val portExtents = sidePorts.map { port ->
            if (horizontalEdge) {
                port.measuredHeight
            } else {
                port.measuredWidth
            }
        }

        val largestExtent =
            portExtents.maxOrNull() ?: 0

        val minimumSpacing =
            largestExtent + dp(8f)

        val effectiveSpacing = max(
            requestedSpacing,
            minimumSpacing
        )

        val centers = calculateCenters(
            extents = portExtents,
            availableSpace = availableSpace,
            requestedSpacing = effectiveSpacing
        )

        val inset = dp(
            config.edgeInsetDp.coerceAtLeast(0f)
        )

        sidePorts.forEachIndexed { index, port ->
            val portWidth = port.measuredWidth
            val portHeight = port.measuredHeight

            val center = centers[index]

            val x: Int
            val y: Int

            when (side) {
                PortSide.LEFT -> {
                    x = offsetX -
                        portWidth / 2 -
                        inset

                    y = offsetY +
                        center -
                        portHeight / 2
                }

                PortSide.RIGHT -> {
                    x = offsetX +
                        width -
                        portWidth / 2 +
                        inset

                    y = offsetY +
                        center -
                        portHeight / 2
                }

                PortSide.TOP -> {
                    x = offsetX +
                        center -
                        portWidth / 2

                    y = offsetY -
                        portHeight / 2 -
                        inset
                }

                PortSide.BOTTOM -> {
                    x = offsetX +
                        center -
                        portWidth / 2

                    y = offsetY +
                        height -
                        portHeight / 2 +
                        inset
                }
            }

            port.layout(
                x,
                y,
                x + portWidth,
                y + portHeight
            )
        }
    }

    /*
     * Calculates evenly distributed connector centers.
     *
     * Each connector keeps its own measured footprint.
     * When the requested spacing fits, it is preserved.
     * Otherwise the ports are distributed across the
     * available edge without arbitrary one-sided offsets.
     */
    private fun calculateCenters(
        extents: List<Int>,
        availableSpace: Int,
        requestedSpacing: Int
    ): List<Int> {

        val count = extents.size

        if (count == 0) return emptyList()

        if (count == 1) {
            return listOf(availableSpace / 2)
        }

        val totalExtent = extents.sum()

        val minimumRequired =
            totalExtent +
                requestedSpacing * (count - 1)

        if (minimumRequired <= availableSpace) {
            val totalSpacing =
                requestedSpacing * (count - 1)

            val totalLength =
                totalExtent + totalSpacing

            var cursor =
                (availableSpace - totalLength) / 2f

            return extents.map { extent ->
                val center = cursor + extent / 2f
                cursor += extent + requestedSpacing
                center.toInt()
            }
        }

        /*
         * When the edge is too short, distribute
         * centers proportionally while keeping the
         * first and last connectors inside the edge.
         */
        val halfFirst = extents.first() / 2f
        val halfLast = extents.last() / 2f

        val usableSpace = (
            availableSpace - halfFirst - halfLast
        ).coerceAtLeast(0f)

        return List(count) { index ->
            val fraction =
                index.toFloat() / (count - 1).toFloat()

            (
                halfFirst + usableSpace * fraction
            ).toInt()
        }
    }
}

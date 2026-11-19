
package com.monolith.graph.ports.views

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.FrameLayout
import com.monolith.graph.ports.models.*

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
            requestLayout()
            invalidate()
        }

    var onPortSelected: ((PortSpec) -> Unit)? = null

    private val ports = LinkedHashMap<String, PortEntry>()

    private val density = resources.displayMetrics.density

    private fun dp(value: Float): Int =
        (value * density).toInt()

    /**
     * Space reserved around the card so ports can extend
     * beyond its edges while remaining inside this ViewGroup.
     */
    private val portOutset: Int
        get() = dp(17f)

    init {
        clipChildren = false
        clipToPadding = false
        isChildrenDrawingOrderEnabled = false

        setPadding(
            portOutset,
            portOutset,
            portOutset,
            portOutset
        )
    }

    /**
     * Adds a port to the requested edge.
     *
     * Existing parameters remain in the same order for compatibility.
     * Shape and enabled are optional extensions.
     */
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

        val maxPorts = config.maxPortsPerSide.coerceAtLeast(0)

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
                val index = getSideIndex(selected)

                onPortSelected?.invoke(
                    PortSpec(
                        id = selected.portId,
                        ownerId = ownerId,
                        side = selected.portSide,
                        index = index,
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

        return port
    }

    /**
     * Creates one default port on every edge.
     */
    fun createDefaultPorts() {
        if (ports.isNotEmpty()) return

        PortSide.values().forEach { side ->
            addPort(side)
        }
    }

    /**
     * Removes a port using its current ID.
     */
    fun removePort(portId: String) {
        val entry = ports.remove(portId) ?: return

        entry.view.onPortClick = null
        removeView(entry.view)

        refreshIds()
        requestLayout()
    }

    /**
     * Removes every port and releases its click callback.
     */
    fun clearPorts() {
        ports.values.forEach { entry ->
            entry.view.onPortClick = null
        }

        ports.clear()
        removeAllViews()

        requestLayout()
    }

    fun getPort(portId: String): PortView? =
        ports[portId]?.view

    fun getPorts(side: PortSide): List<PortView> =
        ports.values
            .filter { it.view.portSide == side }
            .map { it.view }

    fun getAllPorts(): List<PortView> =
        ports.values.map { it.view }

    /**
     * Returns specifications with indexes calculated per edge.
     */
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
    }

    fun setPortEnabled(
        portId: String,
        enabled: Boolean
    ) {
        ports[portId]?.view?.isEnabled = enabled
    }

    fun setAllPortsState(state: PortState) {
        ports.values.forEach { entry ->
            entry.view.state = state
        }
    }

    fun setAllPortsEnabled(enabled: Boolean) {
        ports.values.forEach {
            it.view.isEnabled = enabled
        }
    }

    /**
     * Rebuilds map keys whenever ownerId changes.
     * This prevents stale IDs from remaining in the map.
     */
    private fun refreshIds() {
        if (ports.isEmpty()) return

        val entries = ports.values.toList()
        val rebuilt = LinkedHashMap<String, PortEntry>()

        val indexes = mutableMapOf<PortSide, Int>()

        entries.forEach { entry ->
            val port = entry.view

            val index = indexes[port.portSide] ?: 0
            indexes[port.portSide] = index + 1

            val newId = createPortId(port.portSide, index)

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

            if (current.portSide != port.portSide) continue

            if (current === port) return index

            index++
        }

        return -1
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int
    ) {
        super.onMeasure(
            widthMeasureSpec,
            heightMeasureSpec
        )

        ports.values.forEach { entry ->
            val port = entry.view

            val horizontal =
                port.portSide == PortSide.LEFT ||
                port.portSide == PortSide.RIGHT

            val width = dp(
                if (horizontal) 34f else 20f
            )

            val height = dp(
                if (horizontal) 20f else 34f
            )

            port.measure(
                MeasureSpec.makeMeasureSpec(
                    width,
                    MeasureSpec.EXACTLY
                ),
                MeasureSpec.makeMeasureSpec(
                    height,
                    MeasureSpec.EXACTLY
                )
            )
        }
    }

    override fun onLayout(
        changed: Boolean,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {
        val contentLeft = paddingLeft
        val contentTop = paddingTop

        val contentWidth =
            (right - left - paddingLeft - paddingRight)
                .coerceAtLeast(0)

        val contentHeight =
            (bottom - top - paddingTop - paddingBottom)
                .coerceAtLeast(0)

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

        val spacingDp =
            if (horizontalEdge) {
                config.verticalSpacingDp
            } else {
                config.horizontalSpacingDp
            }

        val centers = calculateCenters(
            count = sidePorts.size,
            availableSpace = availableSpace,
            spacing = dp(spacingDp.coerceAtLeast(0f))
        )

        val inset = dp(config.edgeInsetDp)

        sidePorts.forEachIndexed { index, port ->
            val portWidth = port.measuredWidth
            val portHeight = port.measuredHeight

            val center = centers[index]

            val x: Int
            val y: Int

            when (side) {
                PortSide.LEFT -> {
                    x = offsetX - portWidth / 2 - inset
                    y = offsetY + center - portHeight / 2
                }

                PortSide.RIGHT -> {
                    x = offsetX + width - portWidth / 2 + inset
                    y = offsetY + center - portHeight / 2
                }

                PortSide.TOP -> {
                    x = offsetX + center - portWidth / 2
                    y = offsetY - portHeight / 2 - inset
                }

                PortSide.BOTTOM -> {
                    x = offsetX + center - portWidth / 2
                    y = offsetY + height - portHeight / 2 + inset
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

    /**
     * Distributes ports evenly when the requested spacing
     * cannot fit inside the available edge.
     */
    private fun calculateCenters(
        count: Int,
        availableSpace: Int,
        spacing: Int
    ): List<Int> {
        if (count <= 0) return emptyList()

        if (count == 1) {
            return listOf(availableSpace / 2)
        }

        val requiredSpace = spacing * (count - 1)

        if (spacing > 0 && requiredSpace <= availableSpace) {
            val start = (availableSpace - requiredSpace) / 2

            return List(count) { index ->
                start + index * spacing
            }
        }

        return List(count) { index ->
            (
                (index + 1f) /
                    (count + 1f) *
                    availableSpace
                ).toInt()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
    }
}

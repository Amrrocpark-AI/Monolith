package com.monolith.graph.ports.views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import com.monolith.graph.ports.models.*

class PortClusterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    var ownerId: String = ""
        set(value) {
            field = value
            refreshIds()
        }

    var config = PortLayoutConfig()
        set(value) {
            field = value
            requestLayout()
        }

    var onPortSelected: ((PortSpec) -> Unit)? = null

    private val ports = LinkedHashMap<String, PortView>()

    private val density = resources.displayMetrics.density

    private fun dp(value: Float) = (value * density).toInt()

    init {
        clipChildren = false
        clipToPadding = false
    }

    fun addPort(
        side: PortSide,
        kind: PortKind = PortKind.CONTEXT,
        state: PortState = PortState.IDLE,
        label: String = ""
    ): PortView? {
        val sideCount = ports.values.count { it.portSide == side }

        if (sideCount >= config.maxPortsPerSide) return null

        val id = "$ownerId:${side.name.lowercase()}:$sideCount"

        if (ports.containsKey(id)) return ports[id]

        val port = PortView(context).apply {
            portId = id
            portSide = side
            portKind = kind
            this.state = state

            onPortClick = { selected ->
                onPortSelected?.invoke(
                    PortSpec(
                        id = selected.portId,
                        ownerId = ownerId,
                        side = side,
                        index = sideCount,
                        kind = selected.portKind,
                        state = selected.state,
                        label = label
                    )
                )
            }
        }

        ports[id] = port
        addView(port)
        requestLayout()

        return port
    }

    fun createDefaultPorts() {
        if (ports.isNotEmpty()) return

        PortSide.values().forEach { side ->
            addPort(side)
        }
    }

    fun removePort(portId: String) {
        val port = ports.remove(portId) ?: return
        removeView(port)
        requestLayout()
    }

    fun clearPorts() {
        ports.values.forEach { it.onPortClick = null }
        ports.clear()
        removeAllViews()
    }

    fun getPort(portId: String): PortView? = ports[portId]

    fun getPorts(side: PortSide): List<PortView> =
        ports.values.filter { it.portSide == side }

    fun getAllPorts(): List<PortView> = ports.values.toList()

    fun getPortSpecs(): List<PortSpec> =
        ports.values.mapIndexed { index, port ->
            PortSpec(
                id = port.portId,
                ownerId = ownerId,
                side = port.portSide,
                index = index,
                kind = port.portKind,
                state = port.state
            )
        }

    fun setPortState(portId: String, state: PortState) {
        ports[portId]?.setState(state)
    }

    fun setPortKind(portId: String, kind: PortKind) {
        ports[portId]?.portKind = kind
    }

    fun setAllPortsState(state: PortState) {
        ports.values.forEach { it.setState(state) }
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int
    ) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        ports.values.forEach { port ->
            val horizontal =
                port.portSide == PortSide.LEFT ||
                port.portSide == PortSide.RIGHT

            val w = dp(if (horizontal) 34f else 20f)
            val h = dp(if (horizontal) 20f else 34f)

            port.measure(
                MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY)
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
        val w = right - left
        val h = bottom - top

        PortSide.values().forEach { side ->
            val sidePorts = ports.values.filter { it.portSide == side }
            val count = sidePorts.size

            sidePorts.forEachIndexed { index, port ->
                val cw = port.measuredWidth
                val ch = port.measuredHeight

                val fraction = (index + 1f) / (count + 1f)

                val x: Int
                val y: Int

                when (side) {
                    PortSide.LEFT -> {
                        x = -dp(config.edgeInsetDp)
                        y = (h * fraction - ch / 2f).toInt()
                    }

                    PortSide.RIGHT -> {
                        x = w - cw + dp(config.edgeInsetDp)
                        y = (h * fraction - ch / 2f).toInt()
                    }

                    PortSide.TOP -> {
                        x = (w * fraction - cw / 2f).toInt()
                        y = -dp(config.edgeInsetDp)
                    }

                    PortSide.BOTTOM -> {
                        x = (w * fraction - cw / 2f).toInt()
                        y = h - ch + dp(config.edgeInsetDp)
                    }
                }

                port.layout(x, y, x + cw, y + ch)
            }
        }
    }

    private fun refreshIds() {
        ports.values.forEachIndexed { index, port ->
            port.portId =
                "$ownerId:${port.portSide.name.lowercase()}:$index"
        }
    }
}

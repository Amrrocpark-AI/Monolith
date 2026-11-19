
package com.monolith.graph.ports

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout

class PortClusterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    var ownerId: String = ""
        set(value) {
            field = value
            updatePortIds()
        }

    var onPortSelected: ((PortSpec) -> Unit)? = null

    private val ports = LinkedHashMap<PortSide, PortView>()

    init {
        clipChildren = false
        clipToPadding = false

        createPorts()
    }

    private fun createPorts() {
        PortSide.values().forEach { side ->

            val port = PortView(context).apply {
                portSide = side
                portKind = PortKind.CONTEXT
                state = PortState.IDLE

                onPortClick = { selected ->
                    onPortSelected?.invoke(
                        PortSpec(
                            id = selected.portId,
                            ownerId = ownerId,
                            side = side,
                            kind = selected.portKind,
                            state = selected.state
                        )
                    )
                }
            }

            ports[side] = port
            addView(port)
        }

        updatePortIds()
    }

    private fun updatePortIds() {
        ports.forEach { (side, port) ->
            port.portId = "$ownerId:${side.name.lowercase()}"
        }
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int
    ) {
        super.onMeasure(
            widthMeasureSpec,
            heightMeasureSpec
        )

        ports.forEach { (side, port) ->

            val horizontal =
                side == PortSide.LEFT ||
                side == PortSide.RIGHT

            val childWidth = if (horizontal) {
                dp(30)
            } else {
                dp(18)
            }

            val childHeight = if (horizontal) {
                dp(18)
            } else {
                dp(30)
            }

            port.measure(
                MeasureSpec.makeMeasureSpec(
                    childWidth,
                    MeasureSpec.EXACTLY
                ),
                MeasureSpec.makeMeasureSpec(
                    childHeight,
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
        val w = right - left
        val h = bottom - top

        ports.forEach { (side, port) ->

            val childWidth = port.measuredWidth
            val childHeight = port.measuredHeight

            val x: Int
            val y: Int

            when (side) {
                PortSide.LEFT -> {
                    x = 0
                    y = (h - childHeight) / 2
                }

                PortSide.RIGHT -> {
                    x = w - childWidth
                    y = (h - childHeight) / 2
                }

                PortSide.TOP -> {
                    x = (w - childWidth) / 2
                    y = 0
                }

                PortSide.BOTTOM -> {
                    x = (w - childWidth) / 2
                    y = h - childHeight
                }
            }

            port.layout(
                x,
                y,
                x + childWidth,
                y + childHeight
            )
        }
    }

    fun getPort(side: PortSide): PortView? {
        return ports[side]
    }

    fun getAllPorts(): List<PortView> {
        return ports.values.toList()
    }

    fun setPortState(
        side: PortSide,
        state: PortState
    ) {
        ports[side]?.setState(state)
    }

    fun setPortKind(
        side: PortSide,
        kind: PortKind
    ) {
        ports[side]?.portKind = kind
    }

    fun setAllPortsState(state: PortState) {
        ports.values.forEach {
            it.setState(state)
        }
    }

    fun setPortsEnabled(enabled: Boolean) {
        ports.values.forEach {
            it.isEnabled = enabled

            if (!enabled) {
                it.setState(PortState.DISABLED)
            } else if (it.state == PortState.DISABLED) {
                it.setState(PortState.IDLE)
            }
        }
    }

    fun getPortSpecs(): List<PortSpec> {
        return ports.map { (side, port) ->
            PortSpec(
                id = port.portId,
                ownerId = ownerId,
                side = side,
                kind = port.portKind,
                state = port.state
            )
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}

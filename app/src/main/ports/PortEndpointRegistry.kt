package com.monolith.graph.ports.layout

import android.graphics.PointF
import android.view.View
import com.monolith.graph.ports.models.PortEndpoint
import com.monolith.graph.ports.models.PortSide
import com.monolith.graph.ports.views.PortClusterView

class PortEndpointRegistry {

    private val endpoints =
        LinkedHashMap<String, PortEndpoint>()

    private fun dp(view: View, value: Float): Float =
        value * view.resources.displayMetrics.density

    /**
     * Updates all endpoints belonging to a cluster.
     *
     * Coordinates are converted into the overlay's
     * coordinate system, which is the coordinate system
     * used by ConnectionLayerView.
     *
     * The endpoint is placed at the OUTER connector tip,
     * not at the center of the PortView.
     */
    fun update(
        cluster: PortClusterView,
        overlay: View
    ) {
        if (
            cluster.width <= 0 ||
            cluster.height <= 0 ||
            overlay.width <= 0 ||
            overlay.height <= 0
        ) {
            return
        }

        val overlayLocation = IntArray(2)
        overlay.getLocationOnScreen(overlayLocation)

        cluster.getAllPorts().forEach { port ->

            if (
                port.width <= 0 ||
                port.height <= 0
            ) {
                return@forEach
            }

            val portLocation = IntArray(2)
            port.getLocationOnScreen(portLocation)

            /*
             * PortView draws its connector nose with
             * a 1dp inset from the outer view boundary.
             *
             * The endpoint must meet that nose at its
             * centerline, avoiding a visible wire gap.
             */
            val tipInset = dp(port, 1f)

            val localPoint = when (port.portSide) {

                PortSide.RIGHT -> {
                    PointF(
                        port.width.toFloat() - tipInset,
                        port.height / 2f
                    )
                }

                PortSide.LEFT -> {
                    PointF(
                        tipInset,
                        port.height / 2f
                    )
                }

                PortSide.TOP -> {
                    PointF(
                        port.width / 2f,
                        tipInset
                    )
                }

                PortSide.BOTTOM -> {
                    PointF(
                        port.width / 2f,
                        port.height.toFloat() - tipInset
                    )
                }
            }

            val x =
                portLocation[0] -
                    overlayLocation[0] +
                    localPoint.x

            val y =
                portLocation[1] -
                    overlayLocation[1] +
                    localPoint.y

            endpoints[port.portId] = PortEndpoint(
                port.portId,
                x,
                y
            )
        }
    }

    /**
     * Allows manually supplied endpoint coordinates.
     * Coordinates must already be in overlay space.
     */
    fun update(
        portId: String,
        position: PointF
    ) {
        endpoints[portId] = PortEndpoint(
            portId,
            position.x,
            position.y
        )
    }

    fun remove(portId: String) {
        endpoints.remove(portId)
    }

    fun get(portId: String): PortEndpoint? =
        endpoints[portId]

    fun getAll(): Map<String, PortEndpoint> =
        endpoints.toMap()

    fun getPoints(): Map<String, PointF> =
        endpoints.mapValues { (_, endpoint) ->
            endpoint.toPointF()
        }

    fun clear() {
        endpoints.clear()
    }
}

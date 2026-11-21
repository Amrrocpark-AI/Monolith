package com.monolith.graph.ports.layout

import android.graphics.PointF
import android.view.View
import com.monolith.graph.ports.models.PortEndpoint
import com.monolith.graph.ports.views.PortClusterView

class PortEndpointRegistry {

    private val endpoints =
        LinkedHashMap<String, PortEndpoint>()

    private val plugEndpoints =
        LinkedHashMap<String, PortEndpoint>()

    /**
     * Updates all fixed socket endpoints belonging to a cluster.
     *
     * Coordinates are converted into the overlay's
     * coordinate system, which is the coordinate system
     * used by ConnectionLayerView.
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

            val localPoint = port.getSocketCenter()

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
     * Allows manually supplied socket endpoint coordinates.
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

    /**
     * Updates an independent plug (neck/anchor) endpoint in overlay space.
     */
    fun updatePlug(
        plugId: String,
        position: PointF
    ) {
        plugEndpoints[plugId] = PortEndpoint(
            plugId,
            position.x,
            position.y
        )
    }

    fun getPlug(plugId: String): PortEndpoint? =
        plugEndpoints[plugId]

    fun getPlugPoints(): Map<String, PointF> =
        plugEndpoints.mapValues { (_, endpoint) ->
            endpoint.toPointF()
        }

    fun removePlug(plugId: String) {
        plugEndpoints.remove(plugId)
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
        plugEndpoints.clear()
    }
}

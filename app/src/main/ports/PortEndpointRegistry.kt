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

    private val overlayLocation = IntArray(2)
    private val portLocation = IntArray(2)

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

        overlay.getLocationOnScreen(overlayLocation)

        val currentPorts = cluster.getAllPorts()
        val activeIds = HashSet<String>(currentPorts.size)

        currentPorts.forEach { port ->
            val id = port.portId
            if (id.isBlank()) return@forEach

            if (
                port.width <= 0 ||
                port.height <= 0 ||
                port.visibility != View.VISIBLE
            ) {
                endpoints.remove(id)
                return@forEach
            }

            port.getLocationOnScreen(portLocation)

            val localPoint = port.getSocketCenter()

            val x =
                (portLocation[0] - overlayLocation[0]).toFloat() +
                    localPoint.x * port.scaleX

            val y =
                (portLocation[1] - overlayLocation[1]).toFloat() +
                    localPoint.y * port.scaleY

            activeIds.add(id)
            endpoints[id] = PortEndpoint(
                id,
                x,
                y
            )
        }

        val iterator = endpoints.keys.iterator()
        while (iterator.hasNext()) {
            val existingId = iterator.next()
            if (!activeIds.contains(existingId)) {
                iterator.remove()
            }
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

    fun getPlugPoints(): Map<String, PointF> {
        val result = LinkedHashMap<String, PointF>(plugEndpoints.size)
        for ((key, endpoint) in plugEndpoints) {
            result[key] = endpoint.toPointF()
        }
        return result
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
        LinkedHashMap(endpoints)

    fun getPoints(): Map<String, PointF> {
        val result = LinkedHashMap<String, PointF>(endpoints.size)
        for ((key, endpoint) in endpoints) {
            result[key] = endpoint.toPointF()
        }
        return result
    }

    fun clear() {
        endpoints.clear()
        plugEndpoints.clear()
    }
}

package com.monolith.graph.ports.layout

import android.graphics.PointF
import android.view.View
import com.monolith.graph.ports.models.PortEndpoint
import com.monolith.graph.ports.views.PortClusterView

class PortEndpointRegistry {

    private val endpoints = LinkedHashMap<String, PortEndpoint>()

    fun update(
        cluster: PortClusterView,
        overlay: View
    ) {
        val overlayLocation = IntArray(2)
        overlay.getLocationOnScreen(overlayLocation)

        cluster.getAllPorts().forEach { port ->
            val location = IntArray(2)
            port.getLocationOnScreen(location)

            val x = location[0] - overlayLocation[0] + port.width / 2f
            val y = location[1] - overlayLocation[1] + port.height / 2f

            endpoints[port.portId] =
                PortEndpoint(port.portId, x, y)
        }
    }

    fun update(
        portId: String,
        position: PointF
    ) {
        endpoints[portId] =
            PortEndpoint(portId, position.x, position.y)
    }

    fun remove(portId: String) {
        endpoints.remove(portId)
    }

    fun get(portId: String): PortEndpoint? =
        endpoints[portId]

    fun getAll(): Map<String, PortEndpoint> =
        endpoints.toMap()

    fun getPoints(): Map<String, PointF> =
        endpoints.mapValues { it.value.toPointF() }

    fun clear() {
        endpoints.clear()
    }
}

package com.monolith.graph.ports.connections

import android.graphics.PointF
import com.monolith.graph.ports.models.WireConnection

class WirePathEditor {

    private var selectedId: String? = null
    private var selectedControl = -1

    private val connections = LinkedHashMap<String, WireConnection>()

    var onConnectionChanged: ((WireConnection) -> Unit)? = null

    fun setConnections(items: List<WireConnection>) {
        connections.clear()
        items.forEach { connections[it.id] = it }
    }

    fun select(connectionId: String?) {
        selectedId =
            if (connectionId != null && connections.containsKey(connectionId)) {
                connectionId
            } else {
                null
            }

        selectedControl = -1
    }

    fun getSelected(): WireConnection? =
        selectedId?.let { connections[it] }

    fun beginControlDrag(
        x: Float,
        y: Float,
        radius: Float
    ): Boolean {
        val connection = getSelected() ?: return false

        val first = connection.control1 ?: return false
        val second = connection.control2 ?: return false

        val r2 = radius * radius

        fun distanceSquared(point: PointF): Float {
            val dx = point.x - x
            val dy = point.y - y
            return dx * dx + dy * dy
        }

        selectedControl = when {
            distanceSquared(first) <= r2 -> 1
            distanceSquared(second) <= r2 -> 2
            else -> -1
        }

        return selectedControl != -1
    }

    fun dragControl(x: Float, y: Float) {
        val connection = getSelected() ?: return

        val updated = when (selectedControl) {
            1 -> connection.copy(control1 = PointF(x, y))
            2 -> connection.copy(control2 = PointF(x, y))
            else -> return
        }

        connections[updated.id] = updated
        onConnectionChanged?.invoke(updated)
    }

    fun endDrag() {
        selectedControl = -1
    }

    fun update(
        connectionId: String,
        control1: PointF,
        control2: PointF
    ) {
        val old = connections[connectionId] ?: return

        val updated = old.copy(
            control1 = PointF(control1.x, control1.y),
            control2 = PointF(control2.x, control2.y)
        )

        connections[connectionId] = updated
        onConnectionChanged?.invoke(updated)
    }

    fun remove(connectionId: String) {
        connections.remove(connectionId)

        if (selectedId == connectionId) {
            selectedId = null
            selectedControl = -1
        }
    }

    fun getAll(): List<WireConnection> =
        connections.values.toList()
}

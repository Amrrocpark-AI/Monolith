
package com.monolith.graph.ports

import android.graphics.Color
import android.graphics.PointF

enum class PortSide {
    LEFT, RIGHT, TOP, BOTTOM
}

enum class PortState {
    IDLE,
    AVAILABLE,
    SELECTED,
    CONNECTED,
    ACTIVE,
    DISABLED
}

enum class PortKind {
    CONTEXT,
    REFERENCE,
    CAUSAL,
    DERIVATION,
    CONTRAST,
    DEPENDENCY,
    CONTINUATION,
    CUSTOM
}

enum class ConnectionDirection {
    OUTGOING,
    INCOMING,
    BIDIRECTIONAL
}

data class PortSpec(
    val id: String,
    val ownerId: String,
    val side: PortSide,
    val kind: PortKind = PortKind.CONTEXT,
    val state: PortState = PortState.IDLE,
    val label: String = ""
)

data class WireConnection(
    val id: String,
    val sourcePortId: String,
    val targetPortId: String,
    val color: Int = Color.rgb(0, 212, 255),
    val direction: ConnectionDirection =
        ConnectionDirection.OUTGOING,
    val control1: PointF? = null,
    val control2: PointF? = null,
    val visible: Boolean = true,
    val animated: Boolean = true,
    val glowEnabled: Boolean = true,
    val strokeWidthDp: Float = 2.5f,
    val label: String = ""
)

object PortPalette {

    const val CYAN: Int = 0xFF00D4FF.toInt()
    const val GREEN: Int = 0xFF35E69A.toInt()
    const val PURPLE: Int = 0xFF9875FF.toInt()
    const val BLUE: Int = 0xFF3B91FF.toInt()
    const val ORANGE: Int = 0xFFFFAD52.toInt()
    const val RED: Int = 0xFFFF607D.toInt()

    fun colorFor(kind: PortKind): Int {
        return when (kind) {
            PortKind.CONTEXT -> CYAN
            PortKind.REFERENCE -> PURPLE
            PortKind.CAUSAL -> ORANGE
            PortKind.DERIVATION -> GREEN
            PortKind.CONTRAST -> RED
            PortKind.DEPENDENCY -> BLUE
            PortKind.CONTINUATION -> CYAN
            PortKind.CUSTOM -> 0xFFB8C2D8.toInt()
        }
    }
}

/**
 * Immutable endpoint position in the connection overlay.
 */
data class PortEndpoint(
    val portId: String,
    val position: PointF
)

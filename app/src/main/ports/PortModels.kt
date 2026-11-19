package com.monolith.graph.ports.models

import android.graphics.Color
import android.graphics.PointF

enum class PortSide {
    LEFT, RIGHT, TOP, BOTTOM
}

enum class PortState {
    IDLE, AVAILABLE, SELECTED, CONNECTED, ACTIVE, DISABLED
}

enum class PortKind {
    CONTEXT, REFERENCE, CAUSAL, DERIVATION,
    CONTRAST, DEPENDENCY, CONTINUATION, CUSTOM
}

enum class ConnectionDirection {
    OUTGOING, INCOMING, BIDIRECTIONAL
}

enum class PortShape {
    USB, USB_C, COMPACT
}

data class PortSpec(
    val id: String,
    val ownerId: String,
    val side: PortSide,
    val index: Int = 0,
    val kind: PortKind = PortKind.CONTEXT,
    val state: PortState = PortState.IDLE,
    val shape: PortShape = PortShape.USB_C,
    val label: String = "",
    val enabled: Boolean = true
)

data class WireConnection(
    val id: String,
    val sourcePortId: String,
    val targetPortId: String,
    val color: Int = PortPalette.CYAN,
    val direction: ConnectionDirection = ConnectionDirection.OUTGOING,
    val control1: PointF? = null,
    val control2: PointF? = null,
    val visible: Boolean = true,
    val animated: Boolean = true,
    val glowEnabled: Boolean = true,
    val strokeWidthDp: Float = 2.5f,
    val label: String = "",
    val animationDuration: Long = 1700L
)

data class PortEndpoint(
    val portId: String,
    val x: Float,
    val y: Float
) {
    fun toPointF() = PointF(x, y)
}

data class PortLayoutConfig(
    val horizontalSpacingDp: Float = 34f,
    val verticalSpacingDp: Float = 30f,
    val edgeInsetDp: Float = 0f,
    val maxPortsPerSide: Int = 8
)

object PortPalette {
    const val CYAN = 0xFF00D4FF.toInt()
    const val GREEN = 0xFF35E69A.toInt()
    const val PURPLE = 0xFF9875FF.toInt()
    const val BLUE = 0xFF3B91FF.toInt()
    const val ORANGE = 0xFFFFAD52.toInt()
    const val RED = 0xFFFF607D.toInt()
    const val WHITE = Color.WHITE

    fun colorFor(kind: PortKind): Int = when (kind) {
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

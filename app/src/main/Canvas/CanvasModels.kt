package com.monolith.canvas

data class CanvasPoint(
    val x: Float,
    val y: Float
)

data class CanvasCardModel(
    val id: String,
    val conversationId: String? = null,
    val sourceMessageId: String? = null,
    var title: String,
    var content: String,
    var color: Int = 0xFF263247.toInt()
)

data class CanvasCableModel(
    val id: String,
    val sourceCardId: String,
    val targetCardId: String
)

data class CanvasHistoryEntry(
    val id: String,
    val cardId: String,
    val action: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class CanvasAlarm(
    val id: String,
    val cardId: String?,
    val title: String,
    val triggerAt: Long,
    var isActive: Boolean = true
)

data class CanvasSettings(
    val gridEnabled: Boolean = true,
    val snapEnabled: Boolean = true,
    val showConnections: Boolean = true,
    val backgroundColor: Int = 0xFF11131D.toInt()
)

data class CanvasWorkspaceState(
    val cards: List<CanvasCardModel> = emptyList(),
    val cables: List<CanvasCableModel> = emptyList(),
    val settings: CanvasSettings = CanvasSettings(),
    val history: List<CanvasHistoryEntry> = emptyList(),
    val alarms: List<CanvasAlarm> = emptyList()
)

package com.monolith

import java.util.UUID

enum class MessageRole { USER, AI }

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val content: String,
    val attachmentUris: List<String>? = null,
    val colorHex: String = "#2A2A2A",
    val timestamp: Long = System.currentTimeMillis(),
    var reactionType: String? = null,
    var reactionColor: String? = null,
    var isPinned: Boolean = false,
    var modelName: String? = null,
    var isEdited: Boolean = false,
    var originalContent: String? = null,
    var editReason: String? = null,
    var editedTimestamp: Long? = null
)

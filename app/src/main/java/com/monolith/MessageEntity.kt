package com.monolith

import java.util.UUID

data class MessageEntity(
    val id: String = UUID.randomUUID().toString(),
    val conversationId: String,
    val senderId: String,
    val role: String,
    val content: String,
    val timestamp: Long,
    val attachmentUris: String? = null,
    val isEncrypted: Boolean = false,
    val reactionType: String? = null,
    val reactionColor: String? = null,
    val linkedMessageIds: String? = null,
    val isPinned: Boolean = false,
    val pinCategory: String? = null,
    val iconType: String? = null,
    val pathColor: String? = null,
    val uuid: String = UUID.randomUUID().toString(),
    val modelName: String? = null,
    val isEdited: Boolean = false,
    val originalContent: String? = null,
    val editReason: String? = null,
    val editedTimestamp: Long? = null
)

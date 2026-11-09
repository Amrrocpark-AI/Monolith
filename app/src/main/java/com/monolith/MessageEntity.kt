package com.monolith

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey
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
    val uuid: String = UUID.randomUUID().toString()
)

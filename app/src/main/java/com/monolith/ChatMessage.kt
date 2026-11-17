
package com.monolith

import java.util.UUID

enum class MessageRole {
    USER,
    AI
}

data class ChatMessage(
    // Identity: generated once and preserved across every update.
    val id: String = UUID.randomUUID().toString(),

    val role: MessageRole,
    val content: String,

    // Attachments
    val attachmentUris: List<String>? = null,

    // Presentation
    val colorHex: String = "#2A2A2A",

    // Metadata
    val timestamp: Long = System.currentTimeMillis(),
    val modelName: String? = null,

    // Reactions
    val reactionType: String? = null,
    val reactionColor: String? = null,

    // Pinning
    val isPinned: Boolean = false,

    // Editing history
    val isEdited: Boolean = false,
    val originalContent: String? = null,
    val editReason: String? = null,
    val editedTimestamp: Long? = null,

    // Generation state
    val isThinking: Boolean = false
) {

    /**
     * Edit content while preserving the original message
     * and its identity.
     */
    fun withEditedContent(
        newContent: String,
        reason: String? = null
    ): ChatMessage {

        return copy(
            content = newContent,
            isEdited = true,
            originalContent = originalContent ?: content,
            editReason = reason,
            editedTimestamp = System.currentTimeMillis()
        )
    }

    /**
     * Update reaction without mutating the original object.
     */
    fun withReaction(
        type: String?,
        color: String? = null
    ): ChatMessage {

        return copy(
            reactionType = type,
            reactionColor = color
        )
    }

    /**
     * Toggle pin state.
     */
    fun withPinned(
        pinned: Boolean
    ): ChatMessage {

        return copy(
            isPinned = pinned
        )
    }

    /**
     * Update generation state.
     */
    fun withThinking(
        thinking: Boolean
    ): ChatMessage {

        return copy(
            isThinking = thinking
        )
    }

    /**
     * Update streaming content while preserving message identity.
     */
    fun withContent(
        newContent: String
    ): ChatMessage {

        return copy(
            content = newContent
        )
    }
}


package com.monolith

import java.util.UUID

/**
 * Core message model for MONOLITH.
 *
 * Keeps the original message fields and adds support for:
 * - Threads and branching
 * - Message relationships
 * - Streaming and generation states
 * - Tool execution metadata
 * - Model and token metadata
 *
 * Immutable by design: updates return a new ChatMessage instance.
 */

enum class MessageRole {
    USER,
    AI
}

enum class ChatMessageStatus {
    NORMAL,
    QUEUED,
    GENERATING,
    COMPLETED,
    FAILED,
    CANCELLED
}

enum class ChatRelationKind {
    CONTEXT,
    REFERENCE,
    CAUSE_EFFECT,
    DERIVATION,
    CONFLICT,
    DEPENDENCY,
    CONTINUATION,
    CUSTOM
}

enum class ChatToolState {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}

/**
 * Lightweight relationship reference.
 * Full relationship rendering can be resolved by the database or adapter.
 */
data class ChatMessageRelationRef(
    val id: String = UUID.randomUUID().toString(),
    val sourceMessageId: String,
    val targetMessageId: String,
    val kind: ChatRelationKind = ChatRelationKind.CONTEXT,
    val label: String? = null,
    val isDirected: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Tool execution information attached to a message.
 * This is metadata only; actual execution belongs to the engine.
 */
data class ChatToolExecution(
    val id: String = UUID.randomUUID().toString(),
    val toolName: String,
    val description: String? = null,
    val state: ChatToolState = ChatToolState.QUEUED,
    val result: String? = null,
    val error: String? = null,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val durationMs: Long? = null
)

/**
 * Main conversation message.
 *
 * Original constructor fields are preserved in their original order.
 * New fields are appended to reduce compatibility problems with
 * existing named and positional constructor calls.
 */
data class ChatMessage(

    // ---------------------------------------------------------
    // Identity
    // ---------------------------------------------------------

    val id: String = UUID.randomUUID().toString(),

    val role: MessageRole,

    val content: String,

    // ---------------------------------------------------------
    // Attachments
    // ---------------------------------------------------------

    val attachmentUris: List<String>? = null,

    // ---------------------------------------------------------
    // Presentation
    // ---------------------------------------------------------

    val colorHex: String = "#2A2A2A",

    // ---------------------------------------------------------
    // Metadata
    // ---------------------------------------------------------

    val timestamp: Long = System.currentTimeMillis(),

    val modelName: String? = null,

    // ---------------------------------------------------------
    // Reactions
    // ---------------------------------------------------------

    val reactionType: String? = null,

    val reactionColor: String? = null,

    // ---------------------------------------------------------
    // Pinning
    // ---------------------------------------------------------

    val isPinned: Boolean = false,

    // ---------------------------------------------------------
    // Editing history
    // ---------------------------------------------------------

    val isEdited: Boolean = false,

    val originalContent: String? = null,

    val editReason: String? = null,

    val editedTimestamp: Long? = null,

    // ---------------------------------------------------------
    // Original generation state
    // ---------------------------------------------------------

    val isThinking: Boolean = false,

    // ---------------------------------------------------------
    // Conversation structure
    // ---------------------------------------------------------

    val threadId: String? = null,

    val parentMessageId: String? = null,

    val replyToMessageId: String? = null,

    val branchId: String? = null,

    val branchIndex: Int = 0,

    // ---------------------------------------------------------
    // Message relationships
    // ---------------------------------------------------------

    val relationRefs: List<ChatMessageRelationRef> = emptyList(),

    // ---------------------------------------------------------
    // Generation lifecycle
    // ---------------------------------------------------------

    val status: ChatMessageStatus = ChatMessageStatus.NORMAL,

    val isStreaming: Boolean = false,

    val generationStartedAt: Long? = null,

    val generationCompletedAt: Long? = null,

    val generationDurationMs: Long? = null,

    val errorMessage: String? = null,

    // ---------------------------------------------------------
    // Model execution metadata
    // ---------------------------------------------------------

    val modelId: String? = null,

    val modelProvider: String? = null,

    val inputTokenCount: Int? = null,

    val outputTokenCount: Int? = null,

    val totalTokenCount: Int? = null,

    // ---------------------------------------------------------
    // Tool execution
    // ---------------------------------------------------------

    val toolExecutions: List<ChatToolExecution> = emptyList(),

    val isToolMessage: Boolean = false,

    // ---------------------------------------------------------
    // Additional metadata
    // ---------------------------------------------------------

    val metadata: Map<String, String> = emptyMap()

) {

    // =========================================================
    // Editing
    // =========================================================

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

    // =========================================================
    // Reactions
    // =========================================================

    fun withReaction(
        type: String?,
        color: String? = null
    ): ChatMessage {
        return copy(
            reactionType = type,
            reactionColor = color
        )
    }

    // =========================================================
    // Pinning
    // =========================================================

    fun withPinned(pinned: Boolean): ChatMessage {
        return copy(isPinned = pinned)
    }

    // =========================================================
    // Thinking state
    // =========================================================

    fun withThinking(thinking: Boolean): ChatMessage {
        return copy(
            isThinking = thinking,
            status = if (thinking) {
                ChatMessageStatus.GENERATING
            } else {
                status
            }
        )
    }

    // =========================================================
    // Content updates / streaming
    // =========================================================

    fun withContent(newContent: String): ChatMessage {
        return copy(content = newContent)
    }

    fun withStreamingContent(newContent: String): ChatMessage {
        return copy(
            content = newContent,
            isStreaming = true,
            isThinking = false,
            status = ChatMessageStatus.GENERATING
        )
    }

    fun finishStreaming(): ChatMessage {
        val now = System.currentTimeMillis()

        return copy(
            isStreaming = false,
            isThinking = false,
            status = ChatMessageStatus.COMPLETED,
            generationCompletedAt = now,
            generationDurationMs = generationStartedAt?.let {
                (now - it).coerceAtLeast(0L)
            }
        )
    }

    // =========================================================
    // Generation lifecycle
    // =========================================================

    fun startGeneration(): ChatMessage {
        val now = System.currentTimeMillis()

        return copy(
            status = ChatMessageStatus.GENERATING,
            isThinking = true,
            isStreaming = false,
            generationStartedAt = now,
            generationCompletedAt = null,
            generationDurationMs = null,
            errorMessage = null
        )
    }

    fun completeGeneration(): ChatMessage {
        val now = System.currentTimeMillis()

        return copy(
            status = ChatMessageStatus.COMPLETED,
            isThinking = false,
            isStreaming = false,
            generationCompletedAt = now,
            generationDurationMs = generationStartedAt?.let {
                (now - it).coerceAtLeast(0L)
            }
        )
    }

    fun failGeneration(error: String?): ChatMessage {
        val now = System.currentTimeMillis()

        return copy(
            status = ChatMessageStatus.FAILED,
            isThinking = false,
            isStreaming = false,
            errorMessage = error,
            generationCompletedAt = now,
            generationDurationMs = generationStartedAt?.let {
                (now - it).coerceAtLeast(0L)
            }
        )
    }

    fun cancelGeneration(): ChatMessage {
        return copy(
            status = ChatMessageStatus.CANCELLED,
            isThinking = false,
            isStreaming = false,
            generationCompletedAt = System.currentTimeMillis()
        )
    }

    // =========================================================
    // Thread and branch management
    // =========================================================

    fun withThread(
        newThreadId: String?,
        parentId: String? = parentMessageId
    ): ChatMessage {
        return copy(
            threadId = newThreadId,
            parentMessageId = parentId
        )
    }

    fun withBranch(
        newBranchId: String?,
        index: Int = 0
    ): ChatMessage {
        return copy(
            branchId = newBranchId,
            branchIndex = index.coerceAtLeast(0)
        )
    }

    fun withReplyTo(messageId: String?): ChatMessage {
        return copy(replyToMessageId = messageId)
    }

    // =========================================================
    // Relationship management
    // =========================================================

    fun withRelations(
        relations: List<ChatMessageRelationRef>
    ): ChatMessage {
        return copy(relationRefs = relations.toList())
    }

    fun addRelation(
        relation: ChatMessageRelationRef
    ): ChatMessage {
        if (relationRefs.any { it.id == relation.id }) {
            return this
        }

        return copy(
            relationRefs = relationRefs + relation
        )
    }

    fun removeRelation(relationId: String): ChatMessage {
        return copy(
            relationRefs = relationRefs.filterNot {
                it.id == relationId
            }
        )
    }

    // =========================================================
    // Tool execution management
    // =========================================================

    fun withToolExecutions(
        executions: List<ChatToolExecution>
    ): ChatMessage {
        return copy(
            toolExecutions = executions.toList(),
            isToolMessage = executions.isNotEmpty()
        )
    }

    fun addToolExecution(
        execution: ChatToolExecution
    ): ChatMessage {
        val updated = toolExecutions
            .filterNot { it.id == execution.id } + execution

        return copy(
            toolExecutions = updated,
            isToolMessage = true
        )
    }

    fun updateToolExecution(
        execution: ChatToolExecution
    ): ChatMessage {
        var found = false

        val updated = toolExecutions.map {
            if (it.id == execution.id) {
                found = true
                execution
            } else {
                it
            }
        }

        return if (found) {
            copy(toolExecutions = updated)
        } else {
            addToolExecution(execution)
        }
    }

    // =========================================================
    // Model metadata
    // =========================================================

    fun withModel(
        newModelId: String?,
        newModelName: String? = modelName,
        newProvider: String? = modelProvider
    ): ChatMessage {
        return copy(
            modelId = newModelId,
            modelName = newModelName,
            modelProvider = newProvider
        )
    }

    fun withTokenUsage(
        inputTokens: Int?,
        outputTokens: Int?
    ): ChatMessage {
        val input = inputTokens?.coerceAtLeast(0)
        val output = outputTokens?.coerceAtLeast(0)

        return copy(
            inputTokenCount = input,
            outputTokenCount = output,
            totalTokenCount = if (input != null && output != null) {
                input + output
            } else {
                null
            }
        )
    }

    // =========================================================
    // Additional metadata
    // =========================================================

    fun withMetadata(
        key: String,
        value: String
    ): ChatMessage {
        return copy(
            metadata = metadata + (key to value)
        )
    }

    fun withoutMetadata(key: String): ChatMessage {
        return copy(
            metadata = metadata - key
        )
    }

    // =========================================================
    // Convenience properties
    // =========================================================

    val isCompleted: Boolean
        get() = status == ChatMessageStatus.COMPLETED

    val hasFailed: Boolean
        get() = status == ChatMessageStatus.FAILED

    val hasRelations: Boolean
        get() = relationRefs.isNotEmpty()

    val hasTools: Boolean
        get() = toolExecutions.isNotEmpty()

    val hasAttachments: Boolean
        get() = !attachmentUris.isNullOrEmpty()

    val isInProgress: Boolean
        get() = isThinking ||
            isStreaming ||
            status == ChatMessageStatus.QUEUED ||
            status == ChatMessageStatus.GENERATING
}

package com.monolith

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID

/**
 * يُرمى عند محاولة حذف المحادثة الرئيسية عبر deleteConversation().
 * يُرمى قبل فتح أي Transaction أو لمس أي بيانات.
 */
class ProtectedConversationException(val conversationId: String) :
    IllegalStateException("المحادثة الرئيسية محمية من الحذف")

class AppDatabase(context: Context) : SQLiteOpenHelper(
    context,
    "monolith_database",
    null,
    8
) {

    companion object {
        private const val DB_VERSION = 8
) {

    companion object {
        private const val DB_VERSION = 7

        /** معرّف المحادثة الرئيسية المحمية من الحذف الاعتيادي. */
        const val MAIN_CONVERSATION_ID = "main_chat"

        const val WORKSPACE_STATE_ACTIVE = "ACTIVE"
        const val WORKSPACE_STATE_ARCHIVED = "ARCHIVED"
        const val WORKSPACE_STATE_TRASHED = "TRASHED"

        const val WORKSPACE_SOURCE_MANUAL = "MANUAL"
        const val WORKSPACE_SOURCE_AI = "AI"

        private fun createCoreTables(db: SQLiteDatabase) {

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS messages (
                    id TEXT PRIMARY KEY,
                    conversationId TEXT,
                    senderId TEXT,
                    role TEXT,
                    content TEXT,
                    timestamp INTEGER,
                    attachmentUris TEXT,
                    isEncrypted INTEGER,
                    reactionType TEXT,
                    reactionColor TEXT,
                    linkedMessageIds TEXT,
                    isPinned INTEGER,
                    pinCategory TEXT,
                    iconType TEXT,
                    pathColor TEXT,
                    uuid TEXT,
                    modelName TEXT,
                    isEdited INTEGER,
                    originalContent TEXT,
                    editReason TEXT,
                    editedTimestamp INTEGER
                )
            """)

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS memories (
                    id TEXT PRIMARY KEY,
                    type TEXT,
                    content TEXT,
                    timestamp INTEGER,
                    confidence REAL
                )
            """)

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS notes (
                    id TEXT PRIMARY KEY,
                    title TEXT,
                    content TEXT,
                    type TEXT,
                    timestamp INTEGER,
                    dueDate INTEGER,
                    reminderTime INTEGER,
                    isPinned INTEGER,
                    colorHex TEXT,
                    coverPattern TEXT,
                    coverSticker TEXT
                )
            """)

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS tags (
                    id TEXT PRIMARY KEY,
                    name TEXT
                )
            """)

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS note_tag_cross_ref (
                    noteId TEXT,
                    tagId TEXT,
                    PRIMARY KEY(noteId, tagId)
                )
            """)

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS conversations (
                    id TEXT PRIMARY KEY,
                    title TEXT,
                    createdAt INTEGER,
                    lastModified INTEGER
                )
            """)

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS user_profile (
                    id TEXT PRIMARY KEY,
                    name TEXT,
                    bio TEXT,
                    profileImageUri TEXT,
                    publicKey TEXT,
                    isCurrentUser INTEGER,
                    followersCount INTEGER,
                    followingCount INTEGER
                )
            """)

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS encrypted_files (
                    id TEXT PRIMARY KEY,
                    originalName TEXT,
                    encryptedPath TEXT,
                    uniquePasswordEncrypted TEXT,
                    isSteganography INTEGER,
                    carrierImagePath TEXT,
                    fileSize INTEGER,
                    dateEncrypted INTEGER
                )
            """)

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS virtual_albums (
                    id TEXT PRIMARY KEY,
                    name TEXT,
                    coverUri TEXT,
                    createdAt INTEGER,
                    isVault INTEGER,
                    sortOrder INTEGER
                )
            """)

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS album_media_cross_ref (
                    albumId TEXT,
                    mediaUri TEXT,
                    PRIMARY KEY(albumId, mediaUri)
                )
            """)
        }

        private fun createAgentTables(db: SQLiteDatabase) {

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS skills (
                    id TEXT PRIMARY KEY,
                    name TEXT,
                    description TEXT,
                    action_json TEXT,
                    success_count INTEGER,
                    timestamp INTEGER
                )
            """)

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS blind_spots (
                    id TEXT PRIMARY KEY,
                    x INTEGER,
                    y INTEGER,
                    width INTEGER,
                    height INTEGER,
                    context TEXT,
                    confidence REAL,
                    timestamp INTEGER
                )
            """)

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS code_vault (
                    id TEXT PRIMARY KEY,
                    file_name TEXT,
                    content TEXT,
                    version INTEGER,
                    timestamp INTEGER
                )
            """)

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS audio_memory (
                    id TEXT PRIMARY KEY,
                    file_path TEXT,
                    transcription TEXT,
                    duration INTEGER,
                    timestamp INTEGER
                )
            """)

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS file_memory (
                    id TEXT PRIMARY KEY,
                    file_path TEXT,
                    file_type TEXT,
                    description TEXT,
                    timestamp INTEGER
                )
            """)

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS chromatic_nodes (
                    id TEXT PRIMARY KEY,
                    color_hex TEXT,
                    name TEXT,
                    definition TEXT,
                    timestamp INTEGER
                )
            """)

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS message_threads (
                    id TEXT PRIMARY KEY,
                    source_msg_id TEXT,
                    target_msg_id TEXT,
                    node_id TEXT,
                    timestamp INTEGER
                )
            """)

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS thread_annotations (
                    id TEXT PRIMARY KEY,
                    thread_id TEXT,
                    content TEXT,
                    timestamp INTEGER
                )
            """)

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS agent_lifecycle_log (
                    id TEXT PRIMARY KEY,
                    event_type TEXT,
                    reason TEXT,
                    timestamp INTEGER,
                    duration_active INTEGER
                )
            """)

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS agent_performance_log (
                    id TEXT PRIMARY KEY,
                    goal TEXT,
                    status TEXT,
                    steps_taken INTEGER,
                    latency_ms INTEGER,
                    self_rating INTEGER,
                    reflection_note TEXT,
                    suggested_solution TEXT,
                    timestamp INTEGER
                )
            """)

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS agent_task_queue (
                    id TEXT PRIMARY KEY,
                    task_goal TEXT,
                    current_step INTEGER,
                    status TEXT,
                    timestamp INTEGER
                )
            """)

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS agent_tasks (
                    id TEXT PRIMARY KEY,
                    parent_task_id TEXT,
                    task_type TEXT,
                    goal TEXT,
                    status TEXT,
                    priority TEXT,
                    payload TEXT,
                    result TEXT,
                    timestamp INTEGER,
                    updated_at INTEGER
                )
            """)
        }

        private fun ensureCodeWorkspaceTables(db: SQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS workspace_folders (
                    id TEXT PRIMARY KEY,
                    parent_id TEXT,
                    name TEXT NOT NULL,
                    description TEXT,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    FOREIGN KEY(parent_id) REFERENCES workspace_folders(id)
                        ON DELETE SET NULL
                )
            """)

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS workspace_tags (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL UNIQUE,
                    color_hex TEXT,
                    created_at INTEGER NOT NULL
                )
            """)

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS workspace_code_items (
                    id TEXT PRIMARY KEY,
                    title TEXT NOT NULL,
                    file_name TEXT,
                    language TEXT NOT NULL DEFAULT 'Text',
                    folder_id TEXT,
                    source_conversation_id TEXT,
                    source_message_id TEXT,
                    current_version INTEGER NOT NULL DEFAULT 1,
                    lifecycle_state TEXT NOT NULL DEFAULT 'ACTIVE',
                    archived_at INTEGER,
                    trashed_at INTEGER,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    FOREIGN KEY(folder_id) REFERENCES workspace_folders(id)
                        ON DELETE SET NULL
                )
            """)

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS workspace_code_versions (
                    id TEXT PRIMARY KEY,
                    item_id TEXT NOT NULL,
                    version_number INTEGER NOT NULL,
                    content TEXT NOT NULL,
                    language TEXT NOT NULL DEFAULT 'Text',
                    edit_source TEXT NOT NULL DEFAULT 'MANUAL',
                    change_summary TEXT,
                    created_at INTEGER NOT NULL,
                    UNIQUE(item_id, version_number),
                    FOREIGN KEY(item_id) REFERENCES workspace_code_items(id)
                        ON DELETE CASCADE
                )
            """)

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS workspace_item_tags (
                    item_id TEXT NOT NULL,
                    tag_id TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    PRIMARY KEY(item_id, tag_id),
                    FOREIGN KEY(item_id) REFERENCES workspace_code_items(id)
                        ON DELETE CASCADE,
                    FOREIGN KEY(tag_id) REFERENCES workspace_tags(id)
                        ON DELETE CASCADE
                )
            """)

            db.execSQL("""
                CREATE INDEX IF NOT EXISTS idx_workspace_items_state_updated
                ON workspace_code_items(lifecycle_state, updated_at DESC)
            """)

            db.execSQL("""
                CREATE INDEX IF NOT EXISTS idx_workspace_items_folder
                ON workspace_code_items(folder_id)
            """)

            db.execSQL("""
                CREATE INDEX IF NOT EXISTS idx_workspace_items_conversation
                ON workspace_code_items(source_conversation_id)
            """)

            db.execSQL("""
                CREATE INDEX IF NOT EXISTS idx_workspace_versions_item_ver
                ON workspace_code_versions(item_id, version_number DESC)
            """)

            db.execSQL("""
                CREATE INDEX IF NOT EXISTS idx_workspace_folders_parent
                ON workspace_folders(parent_id)
            """)

            db.execSQL("""
                CREATE INDEX IF NOT EXISTS idx_workspace_item_tags_tag
                ON workspace_item_tags(tag_id)
            """)
        }

        private fun createMessageArchitecture(db: SQLiteDatabase) {

            // Extended message metadata without changing legacy messages.
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS message_runtime_metadata (
                    message_id TEXT PRIMARY KEY,
                    thread_id TEXT,
                    parent_message_id TEXT,
                    reply_to_message_id TEXT,
                    branch_id TEXT,
                    branch_index INTEGER DEFAULT 0,
                    status TEXT DEFAULT 'NORMAL',
                    is_streaming INTEGER DEFAULT 0,
                    generation_started_at INTEGER,
                    generation_completed_at INTEGER,
                    generation_duration_ms INTEGER,
                    error_message TEXT,
                    model_id TEXT,
                    model_provider TEXT,
                    input_tokens INTEGER,
                    output_tokens INTEGER,
                    total_tokens INTEGER,
                    metadata_json TEXT,
                    FOREIGN KEY(message_id) REFERENCES messages(id)
                        ON DELETE CASCADE
                )
            """)

            // Explicit relationships between messages.
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS message_relations (
                    id TEXT PRIMARY KEY,
                    source_message_id TEXT NOT NULL,
                    target_message_id TEXT NOT NULL,
                    relation_kind TEXT NOT NULL,
                    label TEXT,
                    is_directed INTEGER DEFAULT 1,
                    created_at INTEGER NOT NULL,
                    UNIQUE(source_message_id, target_message_id, relation_kind),
                    FOREIGN KEY(source_message_id) REFERENCES messages(id)
                        ON DELETE CASCADE,
                    FOREIGN KEY(target_message_id) REFERENCES messages(id)
                        ON DELETE CASCADE
                )
            """)

            // Conversation branches.
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS message_branches (
                    id TEXT PRIMARY KEY,
                    conversation_id TEXT,
                    parent_message_id TEXT,
                    branch_name TEXT,
                    branch_index INTEGER DEFAULT 0,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
            """)

            // Tool execution history.
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS message_tool_executions (
                    id TEXT PRIMARY KEY,
                    message_id TEXT NOT NULL,
                    tool_name TEXT NOT NULL,
                    description TEXT,
                    state TEXT NOT NULL,
                    result TEXT,
                    error TEXT,
                    started_at INTEGER,
                    completed_at INTEGER,
                    duration_ms INTEGER,
                    FOREIGN KEY(message_id) REFERENCES messages(id)
                        ON DELETE CASCADE
                )
            """)

            // Generation events and progress.
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS message_generation_events (
                    id TEXT PRIMARY KEY,
                    message_id TEXT NOT NULL,
                    event_type TEXT NOT NULL,
                    event_data TEXT,
                    timestamp INTEGER NOT NULL,
                    FOREIGN KEY(message_id) REFERENCES messages(id)
                        ON DELETE CASCADE
                )
            """)

            // Indexes for frequently accessed relationships.
            db.execSQL("""
                CREATE INDEX IF NOT EXISTS idx_messages_conversation
                ON messages(conversationId, timestamp)
            """)

            db.execSQL("""
                CREATE INDEX IF NOT EXISTS idx_message_relations_source
                ON message_relations(source_message_id)
            """)

            db.execSQL("""
                CREATE INDEX IF NOT EXISTS idx_message_relations_target
                ON message_relations(target_message_id)
            """)

            db.execSQL("""
                CREATE INDEX IF NOT EXISTS idx_message_runtime_thread
                ON message_runtime_metadata(thread_id)
            """)

            db.execSQL("""
                CREATE INDEX IF NOT EXISTS idx_tool_message
                ON message_tool_executions(message_id)
            """)

                        db.execSQL("""
                CREATE INDEX IF NOT EXISTS idx_agent_tasks_status
                ON agent_tasks(status, task_type)
            """)

            // Independent deletion review records.
            // No foreign key: records must survive permanent message deletion.
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS message_deletion_reviews (
                    message_id TEXT NOT NULL,
                    conversation_id TEXT NOT NULL,
                    review_state TEXT NOT NULL DEFAULT 'PENDING_DELETE',
                    content_state TEXT NOT NULL DEFAULT 'VISIBLE',
                    review_started_at INTEGER NOT NULL,
                    hide_at INTEGER,
                    duration_ms INTEGER,
                    updated_at INTEGER NOT NULL,
                    finalized_at INTEGER,
                    PRIMARY KEY (message_id, conversation_id)
                )
            """)

            db.execSQL("""
                CREATE INDEX IF NOT EXISTS idx_deletion_reviews_conversation
                ON message_deletion_reviews(conversation_id, review_state)
            """)
        }
    }

    // =========================================================
    // DATABASE LIFECYCLE
    // =========================================================

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.beginTransaction()
        try {
            createCoreTables(db)
            createAgentTables(db)
            createMessageArchitecture(db)
            ensureCodeWorkspaceTables(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) {
        db.beginTransaction()
        try {
                        if (oldVersion < 8) {
                createMessageArchitecture(db)
            }

            ensureCodeWorkspaceTables(db)
            }

            if (oldVersion < 3) {
                createMessageArchitecture(db)
            } else if (oldVersion < 8) {
                createMessageArchitecture(db)
            }

            ensureCodeWorkspaceTables(db)

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        db.setForeignKeyConstraintsEnabled(true)
        if (!db.isReadOnly) {
            ensureCodeWorkspaceTables(db)
        }
    }

    // =========================================================
    // INTERNAL HELPERS
    // =========================================================

    private fun insertReplace(
        table: String,
        values: ContentValues
    ): Long {
        return writableDatabase.insertWithOnConflict(
            table,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    private fun queryMaps(
        sql: String,
        args: Array<String>? = null
        ): List<Map<String, String>> {
        val result = mutableListOf<Map<String, String>>()
        val db = readableDatabase

        db.rawQuery(sql, args).use { cursor ->
            while (cursor.moveToNext()) {
                val map = mutableMapOf<String, String>()

                for (i in 0 until cursor.columnCount) {
                    if (!cursor.isNull(i)) {
                        map[cursor.getColumnName(i)] =
                            cursor.getString(i)
                    }
                }

                result.add(map)
            }
        }

        return result
    }

    private fun cursorString(
        cursor: Cursor,
        column: String
    ): String? {
        val index = cursor.getColumnIndex(column)
        return if (index >= 0 && !cursor.isNull(index)) {
            cursor.getString(index)
        } else null
    }

    private fun normalizeWorkspaceState(state: String?): String {
        return when (state?.trim()?.uppercase()) {
            WORKSPACE_STATE_ARCHIVED -> WORKSPACE_STATE_ARCHIVED
            WORKSPACE_STATE_TRASHED -> WORKSPACE_STATE_TRASHED
            else -> WORKSPACE_STATE_ACTIVE
        }
    }

    private fun normalizeEditSource(source: String?): String {
        return when (source?.trim()?.uppercase()) {
            WORKSPACE_SOURCE_AI -> WORKSPACE_SOURCE_AI
            else -> WORKSPACE_SOURCE_MANUAL
        }
    }

    // =========================================================
    // MESSAGES - LEGACY API
    // =========================================================

    fun insertMessage(message: MessageEntity) {
        val values = ContentValues().apply {
            put("id", message.id)
            put("conversationId", message.conversationId)
            put("senderId", message.senderId)
            put("role", message.role)
            put("content", message.content)
            put("timestamp", message.timestamp)
            put("attachmentUris", message.attachmentUris)
            put("isEncrypted", if (message.isEncrypted) 1 else 0)
            put("reactionType", message.reactionType)
            put("reactionColor", message.reactionColor)
            put("linkedMessageIds", message.linkedMessageIds)
            put("isPinned", if (message.isPinned) 1 else 0)
            put("pinCategory", message.pinCategory)
            put("iconType", message.iconType)
            put("pathColor", message.pathColor)
            put("uuid", message.uuid)
            put("modelName", message.modelName)
            put("isEdited", if (message.isEdited) 1 else 0)
            put("originalContent", message.originalContent)
            put("editReason", message.editReason)
            put("editedTimestamp", message.editedTimestamp)
        }

        insertReplace("messages", values)
    }

    private fun readMessage(cursor: Cursor): MessageEntity {
        return MessageEntity(
            id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
            conversationId = cursor.getString(cursor.getColumnIndexOrThrow("conversationId")),
            senderId = cursor.getString(cursor.getColumnIndexOrThrow("senderId")),
            role = cursor.getString(cursor.getColumnIndexOrThrow("role")),
            content = cursor.getString(cursor.getColumnIndexOrThrow("content")),
            timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")),
            attachmentUris = cursor.getString(cursor.getColumnIndexOrThrow("attachmentUris")),
            isEncrypted = cursor.getInt(cursor.getColumnIndexOrThrow("isEncrypted")) == 1,
            reactionType = cursor.getString(cursor.getColumnIndexOrThrow("reactionType")),
            reactionColor = cursor.getString(cursor.getColumnIndexOrThrow("reactionColor")),
            linkedMessageIds = cursor.getString(cursor.getColumnIndexOrThrow("linkedMessageIds")),
            isPinned = cursor.getInt(cursor.getColumnIndexOrThrow("isPinned")) == 1,
            pinCategory = cursor.getString(cursor.getColumnIndexOrThrow("pinCategory")),
            iconType = cursor.getString(cursor.getColumnIndexOrThrow("iconType")),
            pathColor = cursor.getString(cursor.getColumnIndexOrThrow("pathColor")),
            uuid = cursor.getString(cursor.getColumnIndexOrThrow("uuid")),
            modelName = cursor.getString(cursor.getColumnIndexOrThrow("modelName")),
            isEdited = cursor.getInt(cursor.getColumnIndexOrThrow("isEdited")) == 1,
            originalContent = cursor.getString(cursor.getColumnIndexOrThrow("originalContent")),
            editReason = cursor.getString(cursor.getColumnIndexOrThrow("editReason")),
            editedTimestamp = cursor.getLong(cursor.getColumnIndexOrThrow("editedTimestamp"))
        )
    }

    fun getMessagesForConversation(
        conversationId: String
    ): List<MessageEntity> {
        val list = mutableListOf<MessageEntity>()

        readableDatabase.rawQuery(
            "SELECT * FROM messages WHERE conversationId = ? ORDER BY timestamp ASC, rowid ASC",
            arrayOf(conversationId)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(readMessage(cursor))
            }
        }

        return list
    }

    fun countMessagesForConversation(
        conversationId: String
    ): Int {
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM messages WHERE conversationId = ?",
            arrayOf(conversationId)
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getInt(0)
            }
        }

        return 0
    }

    fun getPinnedMessages(
        conversationId: String
    ): List<MessageEntity> {
        val list = mutableListOf<MessageEntity>()

        readableDatabase.rawQuery(
            "SELECT * FROM messages WHERE conversationId = ? AND isPinned = 1 ORDER BY timestamp DESC",
            arrayOf(conversationId)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(readMessage(cursor))
            }
        }

        return list
    }

    fun updateMessageContent(
        conversationId: String,
        messageId: String,
        newContent: String,
        originalContent: String,
        reason: String?,
        timestamp: Long
    ): Int {
        val values = ContentValues().apply {
            put("content", newContent)
            put("isEdited", 1)
            put("originalContent", originalContent)
            put("editReason", reason)
            put("editedTimestamp", timestamp)
        }

        return writableDatabase.update(
            "messages",
            values,
            "id = ? AND conversationId = ?",
            arrayOf(messageId, conversationId)
        )
    }

    fun updatePinnedStatus(
        messageId: String,
        isPinned: Boolean,
        category: String?
    ) {
        val values = ContentValues().apply {
            put("isPinned", if (isPinned) 1 else 0)
            put("pinCategory", category)
        }

        writableDatabase.update(
            "messages",
            values,
            "id = ?",
            arrayOf(messageId)
        )
    }

    // =========================================================
    // MESSAGE RUNTIME METADATA
    // =========================================================

    fun saveMessageRuntimeMetadata(
        messageId: String,
        threadId: String? = null,
        parentMessageId: String? = null,
        replyToMessageId: String? = null,
        branchId: String? = null,
        branchIndex: Int = 0,
        status: String = "NORMAL",
        isStreaming: Boolean = false,
        generationStartedAt: Long? = null,
        generationCompletedAt: Long? = null,
        generationDurationMs: Long? = null,
        errorMessage: String? = null,
        modelId: String? = null,
        modelProvider: String? = null,
        inputTokens: Int? = null,
        outputTokens: Int? = null,
        totalTokens: Int? = null,
        metadataJson: String? = null
    ) {
        val values = ContentValues().apply {
            put("message_id", messageId)
            put("thread_id", threadId)
            put("parent_message_id", parentMessageId)
            put("reply_to_message_id", replyToMessageId)
            put("branch_id", branchId)
            put("branch_index", branchIndex)
            put("status", status)
            put("is_streaming", if (isStreaming) 1 else 0)
            put("generation_started_at", generationStartedAt)
            put("generation_completed_at", generationCompletedAt)
            put("generation_duration_ms", generationDurationMs)
            put("error_message", errorMessage)
            put("model_id", modelId)
            put("model_provider", modelProvider)
            put("input_tokens", inputTokens)
            put("output_tokens", outputTokens)
            put("total_tokens", totalTokens)
            put("metadata_json", metadataJson)
        }

        insertReplace("message_runtime_metadata", values)
    }

    fun getMessageRuntimeMetadata(
        messageId: String
    ): Map<String, String>? {
        return queryMaps(
            "SELECT * FROM message_runtime_metadata WHERE message_id = ?",
            arrayOf(messageId)
        ).firstOrNull()
    }

    fun deleteMessageRuntimeMetadata(messageId: String) {
        writableDatabase.delete(
            "message_runtime_metadata",
            "message_id = ?",
            arrayOf(messageId)
        )
    }

    // =========================================================
    // MESSAGE RELATIONSHIPS
    // =========================================================

    fun insertMessageRelation(
        sourceMessageId: String,
        targetMessageId: String,
        relationKind: String,
        label: String? = null,
        isDirected: Boolean = true,
        id: String = UUID.randomUUID().toString()
    ): String {
        val values = ContentValues().apply {
            put("id", id)
            put("source_message_id", sourceMessageId)
            put("target_message_id", targetMessageId)
            put("relation_kind", relationKind)
            put("label", label)
            put("is_directed", if (isDirected) 1 else 0)
            put("created_at", System.currentTimeMillis())
        }

        writableDatabase.insertWithOnConflict(
            "message_relations",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )

        return id
    }

    fun getRelationsForMessage(
        messageId: String
    ): List<Map<String, String>> {
        return queryMaps(
            """
            SELECT * FROM message_relations
            WHERE source_message_id = ? OR target_message_id = ?
            ORDER BY created_at ASC
            """.trimIndent(),
            arrayOf(messageId, messageId)
        )
    }

    fun getOutgoingRelations(
        messageId: String
    ): List<Map<String, String>> {
        return queryMaps(
            """
            SELECT * FROM message_relations
            WHERE source_message_id = ?
            ORDER BY created_at ASC
            """.trimIndent(),
            arrayOf(messageId)
        )
    }

    fun deleteMessageRelation(relationId: String) {
        writableDatabase.delete(
            "message_relations",
            "id = ?",
            arrayOf(relationId)
        )
    }

    fun deleteRelationsForMessage(messageId: String) {
        writableDatabase.delete(
            "message_relations",
            "source_message_id = ? OR target_message_id = ?",
            arrayOf(messageId, messageId)
        )
    }

    // =========================================================
    // MESSAGE BRANCHES
    // =========================================================

    fun createMessageBranch(
        conversationId: String?,
        parentMessageId: String?,
        branchName: String?,
        branchIndex: Int = 0
    ): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val values = ContentValues().apply {
            put("id", id)
            put("conversation_id", conversationId)
            put("parent_message_id", parentMessageId)
            put("branch_name", branchName)
            put("branch_index", branchIndex)
            put("created_at", now)
            put("updated_at", now)
        }

        insertReplace("message_branches", values)
        return id
    }

    fun getBranchesForConversation(
        conversationId: String
    ): List<Map<String, String>> {
        return queryMaps(
            """
            SELECT * FROM message_branches
            WHERE conversation_id = ?
            ORDER BY created_at ASC
            """.trimIndent(),
            arrayOf(conversationId)
        )
    }

    fun updateBranchName(
        branchId: String,
        name: String
    ) {
        val values = ContentValues().apply {
            put("branch_name", name)
            put("updated_at", System.currentTimeMillis())
        }

        writableDatabase.update(
            "message_branches",
            values,
            "id = ?",
            arrayOf(branchId)
        )
    }

    fun deleteMessageBranch(branchId: String) {
        writableDatabase.delete(
            "message_branches",
            "id = ?",
            arrayOf(branchId)
        )
    }

    // =========================================================
    // TOOL EXECUTIONS
    // =========================================================

    fun insertToolExecution(
        messageId: String,
        toolName: String,
        description: String? = null,
        state: String = "QUEUED",
        result: String? = null,
        error: String? = null,
        startedAt: Long? = null,
        completedAt: Long? = null,
        durationMs: Long? = null,
        id: String = UUID.randomUUID().toString()
    ): String {
        val values = ContentValues().apply {
            put("id", id)
            put("message_id", messageId)
            put("tool_name", toolName)
            put("description", description)
            put("state", state)
            put("result", result)
            put("error", error)
            put("started_at", startedAt)
            put("completed_at", completedAt)
            put("duration_ms", durationMs)
        }

        insertReplace("message_tool_executions", values)
        return id
    }

    fun updateToolExecution(
        executionId: String,
        state: String,
        result: String? = null,
        error: String? = null,
        completedAt: Long? = null,
        durationMs: Long? = null
    ) {
        val values = ContentValues().apply {
            put("state", state)
            put("result", result)
            put("error", error)
            put("completed_at", completedAt)
            put("duration_ms", durationMs)
        }

        writableDatabase.update(
            "message_tool_executions",
            values,
            "id = ?",
            arrayOf(executionId)
        )
    }

    fun getToolExecutions(
        messageId: String
    ): List<Map<String, String>> {
        return queryMaps(
            """
            SELECT * FROM message_tool_executions
            WHERE message_id = ?
            ORDER BY started_at ASC
            """.trimIndent(),
            arrayOf(messageId)
        )
    }

    fun deleteToolExecution(executionId: String) {
        writableDatabase.delete(
            "message_tool_executions",
            "id = ?",
            arrayOf(executionId)
        )
    }

    // =========================================================
    // GENERATION EVENTS
    // =========================================================

    fun insertGenerationEvent(
        messageId: String,
        eventType: String,
        eventData: String? = null
    ): String {
        val id = UUID.randomUUID().toString()

        val values = ContentValues().apply {
            put("id", id)
            put("message_id", messageId)
            put("event_type", eventType)
            put("event_data", eventData)
            put("timestamp", System.currentTimeMillis())
        }

        insertReplace("message_generation_events", values)
        return id
    }

    fun getGenerationEvents(
        messageId: String
    ): List<Map<String, String>> {
        return queryMaps(
            """
            SELECT * FROM message_generation_events
            WHERE message_id = ?
            ORDER BY timestamp ASC
            """.trimIndent(),
            arrayOf(messageId)
        )
    }

    // =========================================================
    // MEMORIES
    // =========================================================

    fun insertMemory(memory: MemoryEntity) {
        val values = ContentValues().apply {
            put("id", memory.id)
            put("type", memory.type)
            put("content", memory.content)
            put("timestamp", memory.timestamp)
            put("confidence", memory.confidence)
        }

        insertReplace("memories", values)
    }

    fun getRecentMemories(): List<MemoryEntity> {
        val list = mutableListOf<MemoryEntity>()

        readableDatabase.rawQuery(
            "SELECT * FROM memories ORDER BY timestamp DESC LIMIT 20",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    MemoryEntity(
                        id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                        type = cursor.getString(cursor.getColumnIndexOrThrow("type")),
                        content = cursor.getString(cursor.getColumnIndexOrThrow("content")),
                        timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")),
                        confidence = cursor.getFloat(cursor.getColumnIndexOrThrow("confidence"))
                    )
                )
            }
        }

        return list
    }

    // =========================================================
    // NOTES
    // =========================================================

    fun insertNote(note: NoteEntity) {
        val values = ContentValues().apply {
            put("id", note.id)
            put("title", note.title)
            put("content", note.content)
            put("type", note.type)
            put("timestamp", note.timestamp)
            put("dueDate", note.dueDate)
            put("reminderTime", note.reminderTime)
            put("isPinned", if (note.isPinned) 1 else 0)
            put("colorHex", note.colorHex)
            put("coverPattern", note.coverPattern)
            put("coverSticker", note.coverSticker)
        }

        insertReplace("notes", values)
    }

    fun getAllNotes(): List<NoteEntity> {
        val list = mutableListOf<NoteEntity>()

        readableDatabase.rawQuery(
            "SELECT * FROM notes ORDER BY timestamp DESC",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    NoteEntity(
                        id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                        title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
                        content = cursor.getString(cursor.getColumnIndexOrThrow("content")),
                        type = cursor.getString(cursor.getColumnIndexOrThrow("type")),
                        timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")),
                        dueDate = cursor.getLong(cursor.getColumnIndexOrThrow("dueDate")),
                        reminderTime = cursor.getLong(cursor.getColumnIndexOrThrow("reminderTime")),
                        isPinned = cursor.getInt(cursor.getColumnIndexOrThrow("isPinned")) == 1,
                        colorHex = cursor.getString(cursor.getColumnIndexOrThrow("colorHex")),
                        coverPattern = cursor.getString(cursor.getColumnIndexOrThrow("coverPattern")),
                        coverSticker = cursor.getString(cursor.getColumnIndexOrThrow("coverSticker"))
                    )
                )
            }
        }

        return list
    }

    fun deleteNote(id: String) {
        writableDatabase.delete("notes", "id = ?", arrayOf(id))
    }

    // =========================================================
    // ALBUMS
    // =========================================================

    fun insertAlbum(album: VirtualAlbumEntity) {
        val values = ContentValues().apply {
            put("id", album.id)
            put("name", album.name)
            put("coverUri", album.coverUri)
            put("createdAt", album.createdAt)
            put("isVault", if (album.isVault) 1 else 0)
            put("sortOrder", album.sortOrder)
        }

        insertReplace("virtual_albums", values)
    }

    fun addMediaToAlbum(albumId: String, mediaUri: String) {
        val values = ContentValues().apply {
            put("albumId", albumId)
            put("mediaUri", mediaUri)
        }

        writableDatabase.insertWithOnConflict(
            "album_media_cross_ref",
            null,
            values,
            SQLiteDatabase.CONFLICT_IGNORE
        )
    }

    fun getMediaForAlbum(albumId: String): List<String> {
        val list = mutableListOf<String>()

        readableDatabase.rawQuery(
            "SELECT mediaUri FROM album_media_cross_ref WHERE albumId = ?",
            arrayOf(albumId)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursor.getString(0))
            }
        }

        return list
    }

    fun deleteAlbum(albumId: String) {
        val db = writableDatabase
        db.beginTransaction()

        try {
            db.delete("album_media_cross_ref", "albumId = ?", arrayOf(albumId))
            db.delete("virtual_albums", "id = ?", arrayOf(albumId))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getAllVirtualAlbums(): List<VirtualAlbumEntity> {
        val list = mutableListOf<VirtualAlbumEntity>()

        readableDatabase.rawQuery(
            "SELECT * FROM virtual_albums ORDER BY sortOrder ASC",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    VirtualAlbumEntity(
                        id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                        name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                        coverUri = cursor.getString(cursor.getColumnIndexOrThrow("coverUri")),
                        createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("createdAt")),
                        isVault = cursor.getInt(cursor.getColumnIndexOrThrow("isVault")) == 1,
                        sortOrder = cursor.getInt(cursor.getColumnIndexOrThrow("sortOrder"))
                    )
                )
            }
        }

        return list
    }

    // =========================================================
    // CONVERSATIONS
    // =========================================================

    /**
     * الدالة الأصلية. تستخدم CONFLICT_REPLACE وبالتالي قد تستبدل محادثة موجودة بنفس المعرّف.
     * للإنشاء الآمن من واجهة سجل المحادثات استخدم insertConversationIfAbsent().
     */
    fun insertConversation(conversation: ConversationEntity) {
        val values = ContentValues().apply {
            put("id", conversation.id)
            put("title", conversation.title)
            put("createdAt", conversation.createdAt)
            put("lastModified", conversation.lastModified)
        }

        insertReplace("conversations", values)
    }

    /**
     * إدراج آمن: لا يستبدل أي محادثة موجودة (CONFLICT_IGNORE).
     * يعيد true فقط إذا أُنشئت المحادثة فعلاً، و false إذا كان المعرّف موجوداً مسبقاً.
     */
    fun insertConversationIfAbsent(conversation: ConversationEntity): Boolean {
        val values = ContentValues().apply {
            put("id", conversation.id)
            put("title", conversation.title)
            put("createdAt", conversation.createdAt)
            put("lastModified", conversation.lastModified)
        }

        val rowId = writableDatabase.insertWithOnConflict(
            "conversations",
            null,
            values,
            SQLiteDatabase.CONFLICT_IGNORE
        )

        return rowId != -1L
    }

    private fun readConversation(cursor: Cursor): ConversationEntity {
        return ConversationEntity(
            id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
            title = cursorString(cursor, "title") ?: "",
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("createdAt")),
            lastModified = cursor.getLong(cursor.getColumnIndexOrThrow("lastModified"))
        )
    }

    fun getAllConversations(): List<ConversationEntity> {
        val list = mutableListOf<ConversationEntity>()

        readableDatabase.rawQuery(
            "SELECT * FROM conversations ORDER BY lastModified DESC, createdAt DESC",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(readConversation(cursor))
            }
        }

        return list
    }

    fun getConversationById(id: String): ConversationEntity? {
        if (id.isBlank()) return null

        readableDatabase.rawQuery(
            "SELECT * FROM conversations WHERE id = ? LIMIT 1",
            arrayOf(id)
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return readConversation(cursor)
            }
        }

        return null
    }

    /**
     * يغيّر العنوان فقط، ولا يغيّر ترتيب المحادثة في القائمة.
     * يعيد true إذا وُجدت المحادثة وتم تحديثها.
     */
    fun updateConversationTitle(id: String, title: String): Boolean {
        if (id.isBlank()) return false

        val values = ContentValues().apply {
            put("title", title)
        }

        return writableDatabase.update(
            "conversations",
            values,
            "id = ?",
            arrayOf(id)
        ) > 0
    }

    fun updateConversationLastModified(
        id: String,
        timestamp: Long = System.currentTimeMillis()
    ): Boolean {
        if (id.isBlank()) return false

        val values = ContentValues().apply {
            put("lastModified", timestamp)
        }

        return writableDatabase.update(
            "conversations",
            values,
            "id = ?",
            arrayOf(id)
        ) > 0
    }

    /**
     * حذف كامل داخل Transaction واحدة:
     *  1) ملاحظات وروابط الخيوط (message_threads / thread_annotations) المرتبطة برسائل هذه المحادثة فقط
     *  2) الرسائل وجداولها التابعة عبر deleteConversationMessages() الموجودة (بدون تكرار منطقها)
     *  3) فروع المحادثة (message_branches)
     *  4) سجل المحادثة نفسه
     *
     * كل عمليات الحذف تعتمد على المعرّف المحدد بشرط مطابقة دقيقة (parameterized)،
     * ولا تمس أي محادثة أخرى. العقد اللونية (chromatic_nodes) عامة ولا تُحذف.
     *
     * حماية: المحادثة الرئيسية (MAIN_CONVERSATION_ID) لا تُحذف عبر هذه الدالة إطلاقاً.
     * يُرمى ProtectedConversationException قبل فتح أي Transaction، فلا يتغير شيء في البيانات.
     *
     * يعيد true إذا كان سجل المحادثة موجوداً وحُذف، و false إذا لم يكن له سجل أصلاً.
     * أي خطأ أثناء الحذف يُرمى كاستثناء وتُلغى الـ Transaction كاملة (لا حذف جزئي).
     */
    fun deleteConversation(conversationId: String): Boolean {
        if (conversationId.isBlank()) return false

        if (conversationId == MAIN_CONVERSATION_ID) {
            throw ProtectedConversationException(conversationId)
        }

        val db = writableDatabase
        db.beginTransaction()

        try {
            val messageIds = mutableListOf<String>()

            db.rawQuery(
                "SELECT id FROM messages WHERE conversationId = ?",
                arrayOf(conversationId)
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    messageIds.add(cursor.getString(0))
                }
            }

            for (messageId in messageIds) {
                db.delete(
                    "thread_annotations",
                    "thread_id IN (SELECT id FROM message_threads WHERE source_msg_id = ? OR target_msg_id = ?)",
                    arrayOf(messageId, messageId)
                )

                db.delete(
                    "message_threads",
                    "source_msg_id = ? OR target_msg_id = ?",
                    arrayOf(messageId, messageId)
                )
            }

            // Transaction متداخلة مدعومة في Android، وستُثبّت مع الخارجية.
            deleteConversationMessages(conversationId)

            db.delete(
                "message_branches",
                "conversation_id = ?",
                arrayOf(conversationId)
            )

            val removedRows = db.delete(
                "conversations",
                "id = ?",
                arrayOf(conversationId)
            )

            db.setTransactionSuccessful()
            return removedRows > 0
        } finally {
            db.endTransaction()
        }
    }

    // =========================================================
    // SKILLS
    // =========================================================

    fun insertSkill(
        id: String,
        name: String,
        description: String,
        actionJson: String,
        successCount: Int
    ) {
        val values = ContentValues().apply {
            put("id", id)
            put("name", name)
            put("description", description)
            put("action_json", actionJson)
            put("success_count", successCount)
            put("timestamp", System.currentTimeMillis())
        }

        insertReplace("skills", values)
    }

        fun getAllSkills(): List<Map<String, String>> {
        return queryMaps(
            "SELECT * FROM skills ORDER BY success_count DESC"
        )
    }

    // =========================================================
    // COMPUTER VISION MEMORY
    // =========================================================

    fun insertBlindSpot(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        context: String,
        confidence: Float
    ) {
        val values = ContentValues().apply {
            put("id", UUID.randomUUID().toString())
            put("x", x)
            put("y", y)
            put("width", width)
            put("height", height)
            put("context", context)
            put("confidence", confidence)
            put("timestamp", System.currentTimeMillis())
        }

        writableDatabase.insert("blind_spots", null, values)
    }

    // =========================================================
    // CODE VAULT
    // =========================================================

    fun insertCode(
        fileName: String,
        content: String,
        version: Int
    ) {
        val values = ContentValues().apply {
            put("id", UUID.randomUUID().toString())
            put("file_name", fileName)
            put("content", content)
            put("version", version)
            put("timestamp", System.currentTimeMillis())
        }

        writableDatabase.insert("code_vault", null, values)
    }

    fun getLatestCode(fileName: String): String? {
        readableDatabase.rawQuery(
            """
            SELECT content FROM code_vault
            WHERE file_name = ?
            ORDER BY version DESC
            LIMIT 1
            """.trimIndent(),
            arrayOf(fileName)
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getString(0)
            }
        }

        return null
    }

    // =========================================================
    // CODE WORKSPACE
    // =========================================================

    fun createWorkspaceFolder(
        name: String,
        parentId: String? = null,
        description: String? = null,
        id: String = UUID.randomUUID().toString()
    ): String {
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "اسم المجلد لا يمكن أن يكون فارغاً" }

        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("id", id)
            put("parent_id", parentId)
            put("name", cleanName)
            put("description", description)
            put("created_at", now)
            put("updated_at", now)
        }

        insertReplace("workspace_folders", values)
        return id
    }

    fun getWorkspaceFolders(parentId: String? = null    ): List<Map<String, String>> {
        return if (parentId == null) {
            queryMaps(
                """
                SELECT * FROM workspace_folders
                WHERE parent_id IS NULL
                ORDER BY name ASC
                """.trimIndent()
            )
        } else {
            queryMaps(
                """
                SELECT * FROM workspace_folders
                WHERE parent_id = ?
                ORDER BY name ASC
                """.trimIndent(),
                arrayOf(parentId)
            )
        }
    }

    fun getAllWorkspaceFolders(    ): List<Map<String, String>> {
        return queryMaps(
            """
            SELECT * FROM workspace_folders
            ORDER BY name ASC
            """.trimIndent()
        )
    }

    fun assignWorkspaceItemFolder(itemId: String, folderId: String?): Boolean {
        if (itemId.isBlank()) return false
        val values = ContentValues().apply {
            put("folder_id", folderId)
            put("updated_at", System.currentTimeMillis())
        }
        return writableDatabase.update(
            "workspace_code_items",
            values,
            "id = ?",
            arrayOf(itemId)
        ) > 0
    }

    fun upsertWorkspaceTag(
        name: String,
        colorHex: String? = null,
        id: String = UUID.randomUUID().toString()
    ): String {
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "اسم الوسم لا يمكن أن يكون فارغاً" }

        readableDatabase.rawQuery(
            "SELECT id FROM workspace_tags WHERE name = ? LIMIT 1",
            arrayOf(cleanName)
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                val existingId = cursor.getString(0)
                if (colorHex != null) {
                    val updateValues = ContentValues().apply {
                        put("color_hex", colorHex)
                    }
                    writableDatabase.update(
                        "workspace_tags",
                        updateValues,
                        "id = ?",
                        arrayOf(existingId)
                    )
                }
                return existingId
            }
        }

        val values = ContentValues().apply {
            put("id", id)
            put("name", cleanName)
            put("color_hex", colorHex)
            put("created_at", System.currentTimeMillis())
        }

        insertReplace("workspace_tags", values)
        return id
    }

    fun attachTagToWorkspaceItem(itemId: String, tagId: String): Boolean {
        if (itemId.isBlank() || tagId.isBlank()) return false
        val values = ContentValues().apply {
            put("item_id", itemId)
            put("tag_id", tagId)
            put("created_at", System.currentTimeMillis())
        }
        return writableDatabase.insertWithOnConflict(
            "workspace_item_tags",
            null,
            values,
            SQLiteDatabase.CONFLICT_IGNORE
        ) != -1L
    }

    fun detachTagFromWorkspaceItem(itemId: String, tagId: String): Boolean {
        if (itemId.isBlank() || tagId.isBlank()) return false
        return writableDatabase.delete(
            "workspace_item_tags",
            "item_id = ? AND tag_id = ?",
            arrayOf(itemId, tagId)
        ) > 0
    }

    fun getTagsForWorkspaceItem(itemId: String    ): List<Map<String, String>> {
        return queryMaps(
            """
            SELECT t.*
            FROM workspace_tags t
            INNER JOIN workspace_item_tags it ON t.id = it.tag_id
            WHERE it.item_id = ?
            ORDER BY t.name ASC
            """.trimIndent(),
            arrayOf(itemId)
        )
    }

    fun getAllWorkspaceTags(    ): List<Map<String, String>> {
        return queryMaps(
            """
            SELECT * FROM workspace_tags
            ORDER BY name ASC
            """.trimIndent()
        )
    }

    fun getWorkspaceItemsByTag(
        tagId: String,
        lifecycleState: String? = WORKSPACE_STATE_ACTIVE
        ): List<Map<String, String>> {
        return if (lifecycleState.isNullOrBlank()) {
            queryMaps(
                """
                SELECT i.*
                FROM workspace_code_items i
                INNER JOIN workspace_item_tags it ON i.id = it.item_id
                WHERE it.tag_id = ?
                ORDER BY i.updated_at DESC
                """.trimIndent(),
                arrayOf(tagId)
            )
        } else {
            val normalizedState = normalizeWorkspaceState(lifecycleState)
            queryMaps(
                """
                SELECT i.*
                FROM workspace_code_items i
                INNER JOIN workspace_item_tags it ON i.id = it.item_id
                WHERE it.tag_id = ? AND i.lifecycle_state = ?
                ORDER BY i.updated_at DESC
                """.trimIndent(),
                arrayOf(tagId, normalizedState)
            )
        }
    }

    fun createWorkspaceCodeItem(
        title: String,
        content: String,
        language: String = "Text",
        fileName: String? = null,
        folderId: String? = null,
        sourceConversationId: String? = null,
        sourceMessageId: String? = null,
        editSource: String = WORKSPACE_SOURCE_MANUAL,
        changeSummary: String? = null,
        itemId: String = UUID.randomUUID().toString()
    ): String {
        val now = System.currentTimeMillis()
        val cleanLanguage = language.trim().ifEmpty { "Text" }
        val cleanTitle = title.trim().ifEmpty { fileName?.trim().orEmpty().ifEmpty { "Untitled" } }
        val normalizedSource = normalizeEditSource(editSource)
        val versionId = UUID.randomUUID().toString()

        val db = writableDatabase
        db.beginTransaction()
        try {
            val itemValues = ContentValues().apply {
                put("id", itemId)
                put("title", cleanTitle)
                put("file_name", fileName)
                put("language", cleanLanguage)
                put("folder_id", folderId)
                put("source_conversation_id", sourceConversationId)
                put("source_message_id", sourceMessageId)
                put("current_version", 1)
                put("lifecycle_state", WORKSPACE_STATE_ACTIVE)
                putNull("archived_at")
                putNull("trashed_at")
                put("created_at", now)
                put("updated_at", now)
            }

            val insertedItemId = db.insertWithOnConflict(
                "workspace_code_items",
                null,
                itemValues,
                SQLiteDatabase.CONFLICT_ABORT
            )

            check(insertedItemId != -1L) {
                "Failed to create workspace code item"
            }

            val versionValues = ContentValues().apply {
                put("id", versionId)
                put("item_id", itemId)
                put("version_number", 1)
                put("content", content)
                put("language", cleanLanguage)
                put("edit_source", normalizedSource)
                put("change_summary", changeSummary ?: "Initial version")
                put("created_at", now)
            }

            val insertedVersionId = db.insertWithOnConflict(
                "workspace_code_versions",
                null,
                versionValues,
                SQLiteDatabase.CONFLICT_ABORT
            )

            check(insertedVersionId != -1L) {
                "Failed to create initial workspace code version"
            }

            db.setTransactionSuccessful()
            return itemId
        } finally {
            db.endTransaction()
        }
    }

    fun addWorkspaceCodeVersion(
        itemId: String,
        content: String,
        language: String? = null,
        editSource: String = WORKSPACE_SOURCE_MANUAL,
        changeSummary: String? = null,
        title: String? = null,
        fileName: String? = null
    ): Int? {
        if (itemId.isBlank()) return null

        val now = System.currentTimeMillis()
        val normalizedSource = normalizeEditSource(editSource)

        val db = writableDatabase
        db.beginTransaction()
        try {
            var currentItemLanguage = "Text"
            var maxVersion = 0
            var foundItem = false

            db.rawQuery(
                "SELECT language, current_version FROM workspace_code_items WHERE id = ? LIMIT 1",
                arrayOf(itemId)
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    foundItem = true
                    currentItemLanguage = cursor.getString(0)?.takeIf { it.isNotBlank() } ?: "Text"
                    maxVersion = cursor.getInt(1)
                }
            }

            if (!foundItem) return null

            db.rawQuery(
                "SELECT COALESCE(MAX(version_number), 0) FROM workspace_code_versions WHERE item_id = ?",
                arrayOf(itemId)
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    val tableMax = cursor.getInt(0)
                    if (tableMax > maxVersion) {
                        maxVersion = tableMax
                    }
                }
            }

            val nextVersion = maxVersion + 1
            val effectiveLanguage = language?.trim()?.ifEmpty { null } ?: currentItemLanguage
            val versionId = UUID.randomUUID().toString()

            val versionValues = ContentValues().apply {
                put("id", versionId)
                put("item_id", itemId)
                put("version_number", nextVersion)
                put("content", content)
                put("language", effectiveLanguage)
                put("edit_source", normalizedSource)
                put("change_summary", changeSummary)
                put("created_at", now)
            }

            val insertedVersionId = db.insertWithOnConflict(
                "workspace_code_versions",
                null,
                versionValues,
                SQLiteDatabase.CONFLICT_ABORT
            )

            check(insertedVersionId != -1L) {
                "Failed to insert workspace code version $nextVersion"
            }

            val itemUpdateValues = ContentValues().apply {
                put("current_version", nextVersion)
                put("language", effectiveLanguage)
                put("updated_at", now)
                if (title != null && title.isNotBlank()) {
                    put("title", title.trim())
                }
                if (fileName != null) {
                    put("file_name", fileName)
                }
            }

            db.update(
                "workspace_code_items",
                itemUpdateValues,
                "id = ?",
                arrayOf(itemId)
            )

            db.setTransactionSuccessful()
            return nextVersion
        } finally {
            db.endTransaction()
        }
    }

    fun getWorkspaceCodeItem(itemId: String): Map<String, String>? {
        if (itemId.isBlank()) return null
        return queryMaps(
            "SELECT * FROM workspace_code_items WHERE id = ? LIMIT 1",
            arrayOf(itemId)
        ).firstOrNull()
    }

    fun getWorkspaceCodeItems(
        lifecycleState: String = WORKSPACE_STATE_ACTIVE,
        folderId: String? = null
            ): List<Map<String, String>> {
        val normalizedState = normalizeWorkspaceState(lifecycleState)
        return if (folderId == null) {
            queryMaps(
                """
                SELECT * FROM workspace_code_items
                WHERE lifecycle_state = ?
                ORDER BY updated_at DESC
                """.trimIndent(),
                arrayOf(normalizedState)
            )
        } else {
            queryMaps(
                """
                SELECT * FROM workspace_code_items
                WHERE lifecycle_state = ? AND folder_id = ?
                ORDER BY updated_at DESC
                """.trimIndent(),
                arrayOf(normalizedState, folderId)
            )
        }
    }

    fun getWorkspaceCodeVersion(
        itemId: String,
        versionNumber: Int
    ): Map<String, String>? {
        if (itemId.isBlank() || versionNumber <= 0) return null
        return queryMaps(
            """
            SELECT * FROM workspace_code_versions
            WHERE item_id = ? AND version_number = ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(itemId, versionNumber.toString())
        ).firstOrNull()
    }

    fun getLatestWorkspaceCodeVersion(itemId: String): Map<String, String>? {
        if (itemId.isBlank()) return null
        return queryMaps(
            """
            SELECT * FROM workspace_code_versions
            WHERE item_id = ?
            ORDER BY version_number DESC
            LIMIT 1
            """.trimIndent(),
            arrayOf(itemId)
        ).firstOrNull()
    }

    fun getWorkspaceCodeVersionHistory(itemId: String    ): List<Map<String, String>> {
        if (itemId.isBlank()) return emptyList()
        return queryMaps(
            """
            SELECT * FROM workspace_code_versions
            WHERE item_id = ?
            ORDER BY version_number DESC
            """.trimIndent(),
            arrayOf(itemId)
        )
    }

    fun archiveWorkspaceCodeItem(itemId: String): Boolean {
        if (itemId.isBlank()) return false
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("lifecycle_state", WORKSPACE_STATE_ARCHIVED)
            put("archived_at", now)
            putNull("trashed_at")
            put("updated_at", now)
        }
        return writableDatabase.update(
            "workspace_code_items",
            values,
            "id = ?",
            arrayOf(itemId)
        ) > 0
    }

    fun moveWorkspaceCodeItemToTrash(itemId: String): Boolean {
        if (itemId.isBlank()) return false
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("lifecycle_state", WORKSPACE_STATE_TRASHED)
            put("trashed_at", now)
            put("updated_at", now)
        }
        return writableDatabase.update(
            "workspace_code_items",
            values,
            "id = ?",
            arrayOf(itemId)
        ) > 0
    }

    fun restoreWorkspaceCodeItem(itemId: String): Boolean {
        if (itemId.isBlank()) return false
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("lifecycle_state", WORKSPACE_STATE_ACTIVE)
            putNull("archived_at")
            putNull("trashed_at")
            put("updated_at", now)
        }
        return writableDatabase.update(
            "workspace_code_items",
            values,
            "id = ?",
            arrayOf(itemId)
        ) > 0
    }

    fun deleteWorkspaceCodeItemPermanently(itemId: String): Boolean {
        if (itemId.isBlank()) return false
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("workspace_item_tags", "item_id = ?", arrayOf(itemId))
            db.delete("workspace_code_versions", "item_id = ?", arrayOf(itemId))
            val deletedRows = db.delete("workspace_code_items", "id = ?", arrayOf(itemId))
            db.setTransactionSuccessful()
            return deletedRows > 0
        } finally {
            db.endTransaction()
        }
    }

    // =========================================================
    // AUDIO AND FILE MEMORY
    // =========================================================

    fun insertAudioMemory(
        filePath: String,
        transcription: String,
        duration: Int
    ) {
        val values = ContentValues().apply {
            put("id", UUID.randomUUID().toString())
            put("file_path", filePath)
            put("transcription", transcription)
            put("duration", duration)
            put("timestamp", System.currentTimeMillis())
        }

        writableDatabase.insert("audio_memory", null, values)
    }

    fun insertFileMemory(
        filePath: String,
        fileType: String,
        description: String
    ) {
        val values = ContentValues().apply {
            put("id", UUID.randomUUID().toString())
            put("file_path", filePath)
            put("file_type", fileType)
            put("description", description)
            put("timestamp", System.currentTimeMillis())
        }

        writableDatabase.insert("file_memory", null, values)
    }

    // =========================================================
    // CHROMATIC NODES
    // =========================================================

    fun insertChromaticNode(
        id: String = UUID.randomUUID().toString(),
        colorHex: String,
        name: String,
        definition: String
    ) {
        val values = ContentValues().apply {
            put("id", id)
            put("color_hex", colorHex)
            put("name", name)
            put("definition", definition)
            put("timestamp", System.currentTimeMillis())
        }

        insertReplace("chromatic_nodes", values)
    }

    fun getAllChromaticNodes(    ): List<Map<String, String>> {
        return queryMaps("SELECT * FROM chromatic_nodes")
    }

    // =========================================================
    // LEGACY MESSAGE THREADS
    // =========================================================

    fun insertMessageThread(
        sourceMsgId: String,
        targetMsgId: String,
        nodeId: String
    ) {
        val values = ContentValues().apply {
            put("id", UUID.randomUUID().toString())
            put("source_msg_id", sourceMsgId)
            put("target_msg_id", targetMsgId)
            put("node_id", nodeId)
            put("timestamp", System.currentTimeMillis())
        }

        writableDatabase.insert("message_threads", null, values)
    }

    fun getThreadsForMessage(
        messageId: String
        ): List<Map<String, String>> {
        return queryMaps(
            """
            SELECT * FROM message_threads
            WHERE source_msg_id = ? OR target_msg_id = ?
            """.trimIndent(),
            arrayOf(messageId, messageId)
        )
    }

    fun insertThreadAnnotation(
        threadId: String,
        content: String
    ) {
        val values = ContentValues().apply {
            put("id", UUID.randomUUID().toString())
            put("thread_id", threadId)
            put("content", content)
            put("timestamp", System.currentTimeMillis())
        }

        writableDatabase.insert("thread_annotations", null, values)
    }

    fun getAnnotationsForThread(threadId: String): List<String> {
        val list = mutableListOf<String>()

        readableDatabase.rawQuery(
            """
            SELECT content FROM thread_annotations
            WHERE thread_id = ?
            ORDER BY timestamp ASC
            """.trimIndent(),
            arrayOf(threadId)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursor.getString(0))
            }
        }

        return list
    }

    // =========================================================
    // AGENT LIFECYCLE
    // =========================================================

    fun insertLifecycleEvent(
        eventType: String,
        reason: String,
        durationActive: Int
    ) {
        val values = ContentValues().apply {
            put("id", UUID.randomUUID().toString())
            put("event_type", eventType)
            put("reason", reason)
            put("timestamp", System.currentTimeMillis())
            put("duration_active", durationActive)
        }

        writableDatabase.insert("agent_lifecycle_log", null, values)
    }

    fun getRecentLifecycleEvents(
        limit: Int = 10
        ): List<Map<String, String>> {
        return queryMaps(
            """
            SELECT * FROM agent_lifecycle_log
            ORDER BY timestamp DESC LIMIT ?
            """.trimIndent(),
            arrayOf(limit.coerceAtLeast(1).toString())
        )
    }

    // =========================================================
    // AGENT PERFORMANCE
    // =========================================================

    fun insertPerformanceLog(
        goal: String,
        status: String,
        steps: Int,
        latencyMs: Long,
        selfRating: Int,
        reflection: String,
        solution: String
    ) {
        val values = ContentValues().apply {
            put("id", UUID.randomUUID().toString())
            put("goal", goal)
            put("status", status)
            put("steps_taken", steps)
            put("latency_ms", latencyMs)
            put("self_rating", selfRating)
            put("reflection_note", reflection)
            put("suggested_solution", solution)
            put("timestamp", System.currentTimeMillis())
        }

        writableDatabase.insert("agent_performance_log", null, values)
    }

    fun getRecentPerformanceLogs(
        limit: Int = 10
        ): List<Map<String, String>> {
        return queryMaps(
            """
            SELECT * FROM agent_performance_log
            ORDER BY timestamp DESC LIMIT ?
            """.trimIndent(),
            arrayOf(limit.coerceAtLeast(1).toString())
        )
    }

    // =========================================================
    // LEGACY TASK QUEUE
    // =========================================================

    fun insertTaskToQueue(taskGoal: String) {
        val values = ContentValues().apply {
            put("id", UUID.randomUUID().toString())
            put("task_goal", taskGoal)
            put("current_step", 0)
            put("status", "PENDING")
            put("timestamp", System.currentTimeMillis())
        }

        writableDatabase.insert("agent_task_queue", null, values)
    }

    fun getPendingTask(): Map<String, String>? {
        return queryMaps(
            """
            SELECT * FROM agent_task_queue
            WHERE status = 'PENDING'
            ORDER BY timestamp ASC LIMIT 1
            """
        ).firstOrNull()
    }

    fun updateTaskStatus(
        taskId: String,
        status: String
    ) {
        val values = ContentValues().apply {
            put("status", status)
        }

        writableDatabase.update(
            "agent_task_queue",
            values,
            "id = ?",
            arrayOf(taskId)
        )
    }

    // =========================================================
    // MANAGED / PARALLEL TASKS
    // =========================================================

    fun insertManagedTask(
        parentTaskId: String?,
        taskType: String,
        goal: String,
        priority: String,
        payload: String = ""
    ) {
        val now = System.currentTimeMillis()

        val values = ContentValues().apply {
            put("id", UUID.randomUUID().toString())
            put("parent_task_id", parentTaskId)
            put("task_type", taskType)
            put("goal", goal)
            put("status", "PENDING")
            put("priority", priority)
            put("payload", payload)
            put("result", "")
            put("timestamp", now)
            put("updated_at", now)
        }

        writableDatabase.insert("agent_tasks", null, values)
    }

    fun getTasksByTypeAndStatus(
        taskType: String,
        status: String,
        limit: Int = 5
        ): List<Map<String, String>> {
        return queryMaps(
            """
            SELECT * FROM agent_tasks
            WHERE task_type = ? AND status = ?
            ORDER BY
                CASE priority
                    WHEN 'HIGH' THEN 0
                    WHEN 'MEDIUM' THEN 1
                    ELSE 2
                END,
                timestamp ASC
            LIMIT ?
            """.trimIndent(),
            arrayOf(
                taskType,
                status,
                limit.coerceAtLeast(1).toString()
            )
        )
    }

    fun updateManagedTaskStatus(
        taskId: String,
        status: String,
        result: String = ""
    ) {
        val values = ContentValues().apply {
            put("status", status)
            put("result", result)
            put("updated_at", System.currentTimeMillis())
        }

        writableDatabase.update(
            "agent_tasks",
            values,
            "id = ?",
            arrayOf(taskId)
        )
    }

    fun getManagedTask(
        taskId: String
        ): Map<String, String>? {
        return queryMaps(
            "SELECT * FROM agent_tasks WHERE id = ?",
            arrayOf(taskId)
        ).firstOrNull()
    }

    fun getChildTasks(
        parentTaskId: String
        ): List<Map<String, String>> {
        return queryMaps(
            """
            SELECT * FROM agent_tasks
            WHERE parent_task_id = ?
            ORDER BY timestamp ASC
            """.trimIndent(),
            arrayOf(parentTaskId)
        )
    }

    fun deleteManagedTask(taskId: String) {
        writableDatabase.delete(
            "agent_tasks",
            "id = ?",
            arrayOf(taskId)
        )
    }

    // =========================================================
    // DATABASE MAINTENANCE
    // =========================================================

        fun getDatabaseVersion(): Int {
        return readableDatabase.version
    }

    /**
     * حذف رسالة واحدة وجميع بياناتها المرتبطة داخل Transaction واحدة.
     *
     * يشترط تطابق معرّف الرسالة ومعرّف المحادثة معاً.
     * لا يحذف بيانات الرسائل الأخرى، بل ينظف مراجعها إلى الرسالة المحذوفة.
     *
     * يعيد true عند نجاح الحذف، و false إذا لم تكن الرسالة موجودة
     * ضمن المحادثة المحددة.
     */
    fun deleteMessage(
        messageId: String,
        conversationId: String
    ): Boolean {
        if (messageId.isBlank() || conversationId.isBlank()) {
            return false
        }

        val db = writableDatabase

        db.beginTransaction()

        try {
            val messageExists = db.rawQuery(
                """
                SELECT 1 FROM messages
                WHERE id = ? AND conversationId = ?
                LIMIT 1
                """.trimIndent(),
                arrayOf(messageId, conversationId)
            ).use { cursor ->
                cursor.moveToFirst()
            }

            if (!messageExists) {
                return false
            }

            // حذف ملاحظات الخيوط المرتبطة بالرسالة.
            db.delete(
                "thread_annotations",
                """
                thread_id IN (
                    SELECT id FROM message_threads
                    WHERE source_msg_id = ? OR target_msg_id = ?
                )
                """.trimIndent(),
                arrayOf(messageId, messageId)
            )

            // حذف روابط الخيوط المرتبطة بالرسالة.
            db.delete(
                "message_threads",
                "source_msg_id = ? OR target_msg_id = ?",
                arrayOf(messageId, messageId)
            )

            // حذف العلاقات الصادرة والواردة من الرسالة.
            db.delete(
                "message_relations",
                "source_message_id = ? OR target_message_id = ?",
                arrayOf(messageId, messageId)
            )

            // تنظيف مراجع الرسالة داخل بيانات الرسائل الأخرى.
            val clearedReferences = ContentValues().apply {
                putNull("parent_message_id")
                putNull("reply_to_message_id")
            }

            db.update(
                "message_runtime_metadata",
                clearedReferences,
                "parent_message_id = ? OR reply_to_message_id = ?",
                arrayOf(messageId, messageId)
            )

            // حذف البيانات الوصفية الخاصة بالرسالة نفسها.
            db.delete(
                "message_runtime_metadata",
                "message_id = ?",
                arrayOf(messageId)
            )

            // حذف سجلات تنفيذ الأدوات الخاصة بالرسالة.
            db.delete(
                "message_tool_executions",
                "message_id = ?",
                arrayOf(messageId)
            )

            // حذف أحداث التوليد الخاصة بالرسالة.
            db.delete(
                "message_generation_events",
                "message_id = ?",
                arrayOf(messageId)
            )

            // الإبقاء على الفروع وتنظيف جميع مراجع الرسالة المحذوفة.
            val clearedBranchParent = ContentValues().apply {
                putNull("parent_message_id")
            }

            db.update(
                "message_branches",
                clearedBranchParent,
                "parent_message_id = ?",
                arrayOf(messageId)
            )

            // الإبقاء على عناصر مساحة العمل مع إزالة مرجع الرسالة المصدر.
            val clearedWorkspaceSource = ContentValues().apply {
                putNull("source_message_id")
            }

            db.update(
                "workspace_code_items",
                clearedWorkspaceSource,
                "source_message_id = ?",
                arrayOf(messageId)
            )


            // الحذف النهائي مع التحقق من المحادثة مرة أخرى.
            val removedRows = db.delete(
                "messages",
                "id = ? AND conversationId = ?",
                arrayOf(messageId, conversationId)
            )

            if (removedRows != 1) {
                return false
            }

            db.setTransactionSuccessful()
            return true

        } finally {
            db.endTransaction()
        }
    }

    fun deleteConversationMessages(conversationId: String) {
        // حماية: لا تُمسح رسائل المحادثة الرئيسية بهذه الدالة. تُرمى قبل فتح القاعدة أو أي Transaction.
        if (conversationId == MAIN_CONVERSATION_ID) {
            throw ProtectedConversationException(conversationId)
        }

        val db = writableDatabase

        db.beginTransaction()
        try {
            val messageIds = mutableListOf<String>()

            db.rawQuery(
                "SELECT id FROM messages WHERE conversationId = ?",
                arrayOf(conversationId)
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    messageIds.add(cursor.getString(0))
                }
            }

            // تنظيف المراجع إلى الرسائل التي ستُحذف، مع الحفاظ على السجلات المالكة لها.
            db.execSQL(
                """
                UPDATE message_branches
                SET parent_message_id = NULL
                WHERE parent_message_id IN (
                    SELECT id FROM messages WHERE conversationId = ?
                )
                """.trimIndent(),
                arrayOf(conversationId)
            )

            db.execSQL(
                """
                UPDATE message_runtime_metadata
                SET parent_message_id = NULL
                WHERE parent_message_id IN (
                    SELECT id FROM messages WHERE conversationId = ?
                )
                """.trimIndent(),
                arrayOf(conversationId)
            )

            db.execSQL(
                """
                UPDATE message_runtime_metadata
                SET reply_to_message_id = NULL
                WHERE reply_to_message_id IN (
                    SELECT id FROM messages WHERE conversationId = ?
                )
                """.trimIndent(),
                arrayOf(conversationId)
            )

            db.execSQL(
                """
                UPDATE workspace_code_items
                SET source_message_id = NULL
                WHERE source_message_id IN (
                    SELECT id FROM messages WHERE conversationId = ?
                )
                """.trimIndent(),
                arrayOf(conversationId)
            )

            db.execSQL(
                """
                UPDATE workspace_code_items
                SET source_conversation_id = NULL
                WHERE source_conversation_id = ?
                """.trimIndent(),
                arrayOf(conversationId)
            )

            for (messageId in messageIds) {
                db.delete(
                    "message_relations",
                    "source_message_id = ? OR target_message_id = ?",
                    arrayOf(messageId, messageId)
                )

                db.delete(
                    "message_runtime_metadata",
                    "message_id = ?",
                    arrayOf(messageId)
                )

                db.delete(
                    "message_tool_executions",
                    "message_id = ?",
                    arrayOf(messageId)
                )

                db.delete(
                    "message_generation_events",
                    "message_id = ?",
                    arrayOf(messageId)
                )
            }

            db.delete(
                "messages",
                "conversationId = ?",
                arrayOf(conversationId)
            )

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}

package com.monolith

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID

class AppDatabase(context: Context) : SQLiteOpenHelper(context, "monolith_database", null, 2) { // تم رفع الإصدار إلى 2

    override fun onCreate(db: SQLiteDatabase) {
        // الجداول القديمة
        db.execSQL("CREATE TABLE IF NOT EXISTS messages (id TEXT PRIMARY KEY, conversationId TEXT, senderId TEXT, role TEXT, content TEXT, timestamp INTEGER, attachmentUris TEXT, isEncrypted INTEGER, reactionType TEXT, reactionColor TEXT, linkedMessageIds TEXT, isPinned INTEGER, pinCategory TEXT, iconType TEXT, pathColor TEXT, uuid TEXT, modelName TEXT, isEdited INTEGER, originalContent TEXT, editReason TEXT, editedTimestamp INTEGER)")
        db.execSQL("CREATE TABLE IF NOT EXISTS memories (id TEXT PRIMARY KEY, type TEXT, content TEXT, timestamp INTEGER, confidence REAL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS notes (id TEXT PRIMARY KEY, title TEXT, content TEXT, type TEXT, timestamp INTEGER, dueDate INTEGER, reminderTime INTEGER, isPinned INTEGER, colorHex TEXT, coverPattern TEXT, coverSticker TEXT)")
        db.execSQL("CREATE TABLE IF NOT EXISTS tags (id TEXT PRIMARY KEY, name TEXT)")
        db.execSQL("CREATE TABLE IF NOT EXISTS note_tag_cross_ref (noteId TEXT, tagId TEXT, PRIMARY KEY(noteId, tagId))")
        db.execSQL("CREATE TABLE IF NOT EXISTS conversations (id TEXT PRIMARY KEY, title TEXT, createdAt INTEGER, lastModified INTEGER)")
        db.execSQL("CREATE TABLE IF NOT EXISTS user_profile (id TEXT PRIMARY KEY, name TEXT, bio TEXT, profileImageUri TEXT, publicKey TEXT, isCurrentUser INTEGER, followersCount INTEGER, followingCount INTEGER)")
        db.execSQL("CREATE TABLE IF NOT EXISTS encrypted_files (id TEXT PRIMARY KEY, originalName TEXT, encryptedPath TEXT, uniquePasswordEncrypted TEXT, isSteganography INTEGER, carrierImagePath TEXT, fileSize INTEGER, dateEncrypted INTEGER)")
        db.execSQL("CREATE TABLE IF NOT EXISTS virtual_albums (id TEXT PRIMARY KEY, name TEXT, coverUri TEXT, createdAt INTEGER, isVault INTEGER, sortOrder INTEGER)")
        db.execSQL("CREATE TABLE IF NOT EXISTS album_media_cross_ref (albumId TEXT, mediaUri TEXT, PRIMARY KEY(albumId, mediaUri))")

        // الجداول الجديدة (دستور الوكيل)
        db.execSQL("CREATE TABLE IF NOT EXISTS skills (id TEXT PRIMARY KEY, name TEXT, description TEXT, action_json TEXT, success_count INTEGER, timestamp INTEGER)")
        db.execSQL("CREATE TABLE IF NOT EXISTS blind_spots (id TEXT PRIMARY KEY, x INTEGER, y INTEGER, width INTEGER, height INTEGER, context TEXT, confidence REAL, timestamp INTEGER)")
        db.execSQL("CREATE TABLE IF NOT EXISTS code_vault (id TEXT PRIMARY KEY, file_name TEXT, content TEXT, version INTEGER, timestamp INTEGER)")
        db.execSQL("CREATE TABLE IF NOT EXISTS audio_memory (id TEXT PRIMARY KEY, file_path TEXT, transcription TEXT, duration INTEGER, timestamp INTEGER)")
        db.execSQL("CREATE TABLE IF NOT EXISTS file_memory (id TEXT PRIMARY KEY, file_path TEXT, file_type TEXT, description TEXT, timestamp INTEGER)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // حذف الجداول القديمة
        db.execSQL("DROP TABLE IF EXISTS messages")
        db.execSQL("DROP TABLE IF EXISTS memories")
        db.execSQL("DROP TABLE IF EXISTS notes")
        db.execSQL("DROP TABLE IF EXISTS tags")
        db.execSQL("DROP TABLE IF EXISTS note_tag_cross_ref")
        db.execSQL("DROP TABLE IF EXISTS conversations")
        db.execSQL("DROP TABLE IF EXISTS user_profile")
        db.execSQL("DROP TABLE IF EXISTS encrypted_files")
        db.execSQL("DROP TABLE IF EXISTS virtual_albums")
        db.execSQL("DROP TABLE IF EXISTS album_media_cross_ref")
        
        // حذف الجداول الجديدة
        db.execSQL("DROP TABLE IF EXISTS skills")
        db.execSQL("DROP TABLE IF EXISTS blind_spots")
        db.execSQL("DROP TABLE IF EXISTS code_vault")
        db.execSQL("DROP TABLE IF EXISTS audio_memory")
        db.execSQL("DROP TABLE IF EXISTS file_memory")
        
        onCreate(db)
    }

    // ==========================================
    // الدوال القديمة (لم يتم تغييرها)
    // ==========================================

    fun insertMessage(message: MessageEntity) {
        val db = writableDatabase
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
        db.insertWithOnConflict("messages", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        db.close()
    }

    fun getMessagesForConversation(conversationId: String): List<MessageEntity> {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM messages WHERE conversationId = ? ORDER BY timestamp ASC", arrayOf(conversationId))
        val list = mutableListOf<MessageEntity>()
        if (cursor.moveToFirst()) {
            do {
                list.add(MessageEntity(
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
                ))
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return list
    }

    fun getPinnedMessages(conversationId: String): List<MessageEntity> {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM messages WHERE conversationId = ? AND isPinned = 1 ORDER BY timestamp DESC", arrayOf(conversationId))
        val list = mutableListOf<MessageEntity>()
        if (cursor.moveToFirst()) {
            do {
                list.add(MessageEntity(
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
                ))
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return list
    }

    fun updateMessageContent(messageId: String, newContent: String, originalContent: String, reason: String?, timestamp: Long) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("content", newContent)
            put("isEdited", 1)
            put("originalContent", originalContent)
            put("editReason", reason)
            put("editedTimestamp", timestamp)
        }
        db.update("messages", values, "id = ?", arrayOf(messageId))
        db.close()
    }

    fun updatePinnedStatus(messageId: String, isPinned: Boolean, category: String?) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("isPinned", if (isPinned) 1 else 0)
            put("pinCategory", category)
        }
        db.update("messages", values, "id = ?", arrayOf(messageId))
        db.close()
    }

    fun insertMemory(memory: MemoryEntity) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("id", memory.id)
            put("type", memory.type)
            put("content", memory.content)
            put("timestamp", memory.timestamp)
            put("confidence", memory.confidence)
        }
        db.insertWithOnConflict("memories", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        db.close()
    }

    fun getRecentMemories(): List<MemoryEntity> {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM memories ORDER BY timestamp DESC LIMIT 20", null)
        val list = mutableListOf<MemoryEntity>()
        if (cursor.moveToFirst()) {
            do {
                list.add(MemoryEntity(
                    id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                    type = cursor.getString(cursor.getColumnIndexOrThrow("type")),
                    content = cursor.getString(cursor.getColumnIndexOrThrow("content")),
                    timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")),
                    confidence = cursor.getFloat(cursor.getColumnIndexOrThrow("confidence"))
                ))
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return list
    }

    fun insertNote(note: NoteEntity) {
        val db = writableDatabase
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
        db.insertWithOnConflict("notes", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        db.close()
    }

    fun getAllNotes(): List<NoteEntity> {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM notes ORDER BY timestamp DESC", null)
        val list = mutableListOf<NoteEntity>()
        if (cursor.moveToFirst()) {
            do {
                list.add(NoteEntity(
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
                ))
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return list
    }

    fun deleteNote(id: String) {
        val db = writableDatabase
        db.delete("notes", "id = ?", arrayOf(id))
        db.close()
    }

    fun insertAlbum(album: VirtualAlbumEntity) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("id", album.id)
            put("name", album.name)
            put("coverUri", album.coverUri)
            put("createdAt", album.createdAt)
            put("isVault", if (album.isVault) 1 else 0)
            put("sortOrder", album.sortOrder)
        }
        db.insertWithOnConflict("virtual_albums", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        db.close()
    }

    fun addMediaToAlbum(albumId: String, mediaUri: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("albumId", albumId)
            put("mediaUri", mediaUri)
        }
        db.insertWithOnConflict("album_media_cross_ref", null, values, SQLiteDatabase.CONFLICT_IGNORE)
        db.close()
    }

    fun getMediaForAlbum(albumId: String): List<String> {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT mediaUri FROM album_media_cross_ref WHERE albumId = ?", arrayOf(albumId))
        val list = mutableListOf<String>()
        if (cursor.moveToFirst()) {
            do {
                list.add(cursor.getString(cursor.getColumnIndexOrThrow("mediaUri")))
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return list
    }

    fun deleteAlbum(albumId: String) {
        val db = writableDatabase
        db.delete("virtual_albums", "id = ?", arrayOf(albumId))
        db.delete("album_media_cross_ref", "albumId = ?", arrayOf(albumId))
        db.close()
    }

    fun insertConversation(conversation: ConversationEntity) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("id", conversation.id)
            put("title", conversation.title)
            put("createdAt", conversation.createdAt)
            put("lastModified", conversation.lastModified)
        }
        db.insertWithOnConflict("conversations", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        db.close()
    }

    fun getAllVirtualAlbums(): List<VirtualAlbumEntity> {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM virtual_albums", null)
        val list = mutableListOf<VirtualAlbumEntity>()
        if (cursor.moveToFirst()) {
            do {
                list.add(VirtualAlbumEntity(
                    id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                    name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                    coverUri = cursor.getString(cursor.getColumnIndexOrThrow("coverUri")),
                    createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("createdAt")),
                    isVault = cursor.getInt(cursor.getColumnIndexOrThrow("isVault")) == 1,
                    sortOrder = cursor.getInt(cursor.getColumnIndexOrThrow("sortOrder"))
                ))
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return list
    }

    // ==========================================
    // الدوال الجديدة (دستور الوكيل)
    // ==========================================

    // 1. المهارات
    fun insertSkill(id: String, name: String, description: String, actionJson: String, successCount: Int) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("id", id)
            put("name", name)
            put("description", description)
            put("action_json", actionJson)
            put("success_count", successCount)
            put("timestamp", System.currentTimeMillis())
        }
        db.insertWithOnConflict("skills", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        db.close()
    }

    fun getAllSkills(): List<Map<String, String>> {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM skills ORDER BY success_count DESC", null)
        val list = mutableListOf<Map<String, String>>()
        if (cursor.moveToFirst()) {
            do {
                val map = mutableMapOf<String, String>()
                map["id"] = cursor.getString(cursor.getColumnIndexOrThrow("id"))
                map["name"] = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                map["description"] = cursor.getString(cursor.getColumnIndexOrThrow("description"))
                map["action_json"] = cursor.getString(cursor.getColumnIndexOrThrow("action_json"))
                list.add(map)
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return list
    }

    // 2. النقاط العمياء
    fun insertBlindSpot(x: Int, y: Int, width: Int, height: Int, context: String, confidence: Float) {
        val db = writableDatabase
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
        db.insert("blind_spots", null, values)
        db.close()
    }

    // 3. خزنة الكود
    fun insertCode(fileName: String, content: String, version: Int) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("id", UUID.randomUUID().toString())
            put("file_name", fileName)
            put("content", content)
            put("version", version)
            put("timestamp", System.currentTimeMillis())
        }
        db.insert("code_vault", null, values)
        db.close()
    }

    fun getLatestCode(fileName: String): String? {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT content FROM code_vault WHERE file_name = ? ORDER BY version DESC LIMIT 1", arrayOf(fileName))
        var content: String? = null
        if (cursor.moveToFirst()) {
            content = cursor.getString(cursor.getColumnIndexOrThrow("content"))
        }
        cursor.close()
        db.close()
        return content
    }

    // 4. ذاكرة الصوت
    fun insertAudioMemory(filePath: String, transcription: String, duration: Int) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("id", UUID.randomUUID().toString())
            put("file_path", filePath)
            put("transcription", transcription)
            put("duration", duration)
            put("timestamp", System.currentTimeMillis())
        }
        db.insert("audio_memory", null, values)
        db.close()
    }

    // 5. ذاكرة الملفات
    fun insertFileMemory(filePath: String, fileType: String, description: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("id", UUID.randomUUID().toString())
            put("file_path", filePath)
            put("file_type", fileType)
            put("description", description)
            put("timestamp", System.currentTimeMillis())
        }
        db.insert("file_memory", null, values)
        db.close()
    }
}

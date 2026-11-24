
package com.monolith.canvas

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.monolith.AppDatabase

class CanvasRepository(context: Context) {

    private val appDatabase =
        AppDatabase(context.applicationContext)

    private val database: SQLiteDatabase =
        appDatabase.writableDatabase

    init {
        createTables()
    }

    private fun createTables() {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS canvas_cards (
                id TEXT PRIMARY KEY,
                conversation_id TEXT,
                source_message_id TEXT,
                title TEXT NOT NULL,
                content TEXT NOT NULL,
                color INTEGER NOT NULL,
                position_x REAL NOT NULL,
                position_y REAL NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )

        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS canvas_cables (
                id TEXT PRIMARY KEY,
                source_card_id TEXT NOT NULL,
                target_card_id TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )

        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS canvas_settings (
                id INTEGER PRIMARY KEY,
                settings_json TEXT NOT NULL
            )
            """.trimIndent()
        )

        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS canvas_thread_history (
                id TEXT PRIMARY KEY,
                card_id TEXT NOT NULL,
                action TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )

        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS canvas_alarms (
                id TEXT PRIMARY KEY,
                card_id TEXT,
                title TEXT NOT NULL,
                trigger_at INTEGER NOT NULL,
                is_active INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent()
        )
    }

    fun saveCard(
        model: CanvasCardModel,
        positionX: Float,
        positionY: Float
    ) {
        val values = ContentValues().apply {
            put("id", model.id)
            put("conversation_id", model.conversationId)
            put("source_message_id", model.sourceMessageId)
            put("title", model.title)
            put("content", model.content)
            put("color", model.color)
            put("position_x", positionX)
            put("position_y", positionY)
            put("created_at", System.currentTimeMillis())
        }

        database.insertWithOnConflict(
            "canvas_cards",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun loadCards(): List<Pair<CanvasCardModel, CanvasPoint>> {
        val cards = mutableListOf<Pair<CanvasCardModel, CanvasPoint>>()

        database.query(
            "canvas_cards",
            arrayOf(
                "id",
                "conversation_id",
                "source_message_id",
                "title",
                "content",
                "color",
                "position_x",
                "position_y"
            ),
            null,
            null,
            null,
            null,
            "created_at ASC"
        ).use { cursor ->

            while (cursor.moveToNext()) {
                val model = CanvasCardModel(
                    id = cursor.getString(0),
                    conversationId = if (cursor.isNull(1)) null else cursor.getString(1),
                    sourceMessageId = if (cursor.isNull(2)) null else cursor.getString(2),
                    title = cursor.getString(3),
                    content = cursor.getString(4),
                    color = cursor.getInt(5)
                )

                val position = CanvasPoint(
                    x = cursor.getFloat(6),
                    y = cursor.getFloat(7)
                )

                cards.add(model to position)
            }
        }

        return cards
    }

    fun deleteCard(cardId: String) {
        database.beginTransaction()

        try {
            database.delete(
                "canvas_cables",
                "source_card_id = ? OR target_card_id = ?",
                arrayOf(cardId, cardId)
            )

            database.delete(
                "canvas_thread_history",
                "card_id = ?",
                arrayOf(cardId)
            )

            database.delete(
                "canvas_alarms",
                "card_id = ?",
                arrayOf(cardId)
            )

            database.delete(
                "canvas_cards",
                "id = ?",
                arrayOf(cardId)
            )

            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    fun saveCable(
        cableId: String,
        sourceCardId: String,
        targetCardId: String
    ) {
        val values = ContentValues().apply {
            put("id", cableId)
            put("source_card_id", sourceCardId)
            put("target_card_id", targetCardId)
            put("created_at", System.currentTimeMillis())
        }

        database.insertWithOnConflict(
            "canvas_cables",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun loadCables(): List<CanvasCableModel> {
        val cables = mutableListOf<CanvasCableModel>()

        database.query(
            "canvas_cables",
            arrayOf("id", "source_card_id", "target_card_id"),
            null,
            null,
            null,
            null,
            "created_at ASC"
        ).use { cursor ->

            while (cursor.moveToNext()) {
                cables.add(
                    CanvasCableModel(
                        id = cursor.getString(0),
                        sourceCardId = cursor.getString(1),
                        targetCardId = cursor.getString(2)
                    )
                )
            }
        }

        return cables
    }

    fun deleteCable(cableId: String) {
        database.delete(
            "canvas_cables",
            "id = ?",
            arrayOf(cableId)
        )
    }

    fun saveSettings(settingsJson: String) {
        val values = ContentValues().apply {
            put("id", 1)
            put("settings_json", settingsJson)
        }

        database.insertWithOnConflict(
            "canvas_settings",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun loadSettings(): String? {
        database.rawQuery(
            "SELECT settings_json FROM canvas_settings WHERE id = 1",
            null
        ).use { cursor ->
            return if (cursor.moveToFirst()) {
                cursor.getString(0)
            } else {
                null
            }
        }
    }

    fun saveHistory(
        id: String,
        cardId: String,
        action: String
    ) {
        val values = ContentValues().apply {
            put("id", id)
            put("card_id", cardId)
            put("action", action)
            put("created_at", System.currentTimeMillis())
        }

        database.insertWithOnConflict(
            "canvas_thread_history",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun loadHistory(): List<CanvasHistoryEntry> {
        val history = mutableListOf<CanvasHistoryEntry>()

        database.query(
            "canvas_thread_history",
            arrayOf("id", "card_id", "action", "created_at"),
            null,
            null,
            null,
            null,
            "created_at ASC"
        ).use { cursor ->

            while (cursor.moveToNext()) {
                history.add(
                    CanvasHistoryEntry(
                        id = cursor.getString(0),
                        cardId = cursor.getString(1),
                        action = cursor.getString(2),
                        createdAt = cursor.getLong(3)
                    )
                )
            }
        }

        return history
    }

    fun saveAlarm(
        id: String,
        cardId: String?,
        title: String,
        triggerAt: Long
    ) {
        val values = ContentValues().apply {
            put("id", id)
            put("card_id", cardId)
            put("title", title)
            put("trigger_at", triggerAt)
            put("is_active", 1)
        }

        database.insertWithOnConflict(
            "canvas_alarms",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun loadAlarms(): List<CanvasAlarm> {
        val alarms = mutableListOf<CanvasAlarm>()

        database.query(
            "canvas_alarms",
            arrayOf(
                "id",
                "card_id",
                "title",
                "trigger_at",
                "is_active"
            ),
            null,
            null,
            null,
            null,
            "trigger_at ASC"
        ).use { cursor ->

            while (cursor.moveToNext()) {
                alarms.add(
                    CanvasAlarm(
                        id = cursor.getString(0),
                        cardId = if (cursor.isNull(1)) null else cursor.getString(1),
                        title = cursor.getString(2),
                        triggerAt = cursor.getLong(3),
                        isActive = cursor.getInt(4) != 0
                    )
                )
            }
        }

        return alarms
    }
}

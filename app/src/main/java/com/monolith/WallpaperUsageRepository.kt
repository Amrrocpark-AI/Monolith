
package com.monolith

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.util.Locale
import java.util.UUID

class WallpaperUsageRepository(context: Context) {

    private val database = AppDatabase(context.applicationContext)

    data class UsageType(
        val id: String,
        val usageKey: String,
        val name: String,
        val description: String?,
        val languageCode: String,
        val sortOrder: Int,
        val isVisible: Boolean,
        val wallpaperCount: Int
    )

    data class UsageScore(
        val usageTypeId: String,
        val usageName: String,
        val aiSuitabilityScore: Double?,
        val userSuitabilityScore: Int?,
        val aiEvaluatedAt: Long?,
        val userRatedAt: Long?
    )

    private fun normalize(value: String): String {
        return value.trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
    }

    private fun cleanLanguage(value: String): String {
        return value.trim()
            .lowercase(Locale.ROOT)
            .ifEmpty { "und" }
    }

    fun createUsageType(
        usageKey: String,
        name: String,
        languageCode: String = "en",
        description: String? = null,
        sortOrder: Int = 0
    ): String {

        val key = usageKey.trim().lowercase(Locale.ROOT)
        val cleanName = name.trim()
        val language = cleanLanguage(languageCode)

        require(
            Regex("^[a-z0-9][a-z0-9._-]{0,79}$").matches(key)
        ) { "Invalid usage key" }

        require(cleanName.isNotEmpty()) {
            "Usage name cannot be empty"
        }

        val db = database.writableDatabase
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        db.beginTransaction()

        try {
            val values = ContentValues().apply {
                put("id", id)
                put("usageKey", key)
                put("sortOrder", sortOrder)
                put("isVisible", 1)
                put("createdAt", now)
                put("updatedAt", now)
            }

            db.insertOrThrow(
                "wallpaper_usage_types",
                null,
                values
            )

            saveTranslation(
                db,
                id,
                cleanName,
                language,
                description
            )

            db.setTransactionSuccessful()
            return id

        } finally {
            db.endTransaction()
        }
    }

    fun saveTranslation(
        usageTypeId: String,
        name: String,
        languageCode: String,
        description: String? = null
    ) {
        saveTranslation(
            database.writableDatabase,
            usageTypeId,
            name,
            languageCode,
            description
        )
    }

    private fun saveTranslation(
        db: SQLiteDatabase,
        usageTypeId: String,
        name: String,
        languageCode: String,
        description: String?
    ) {

        val cleanName = name.trim()
        val language = cleanLanguage(languageCode)

        require(usageTypeId.isNotBlank())
        require(cleanName.isNotEmpty())

        val values = ContentValues().apply {
            put("usageTypeId", usageTypeId)
            put("languageCode", language)
            put("name", cleanName)
            put("normalizedName", normalize(cleanName))
            put("description", description?.trim())
        }

        db.insertWithOnConflict(
            "wallpaper_usage_type_translations",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun getUsageTypes(
        languageCode: String = "en",
        includeHidden: Boolean = false
    ): List<UsageType> {

        val language = cleanLanguage(languageCode)
        val visibility = if (includeHidden) "" else
            "WHERE u.isVisible = 1"

        val result = mutableListOf<UsageType>()

        database.readableDatabase.rawQuery(
            """
            SELECT
                u.id,
                u.usageKey,
                COALESCE(
                    (
                        SELECT tr.name
                        FROM wallpaper_usage_type_translations tr
                        WHERE tr.usageTypeId = u.id
                          AND tr.languageCode = ?
                    ),
                    (
                        SELECT tr.name
                        FROM wallpaper_usage_type_translations tr
                        WHERE tr.usageTypeId = u.id
                          AND tr.languageCode = 'en'
                    ),
                    u.usageKey
                ),
                (
                    SELECT tr.description
                    FROM wallpaper_usage_type_translations tr
                    WHERE tr.usageTypeId = u.id
                      AND tr.languageCode = ?
                ),
                u.sortOrder,
                u.isVisible,
                (
                    SELECT COUNT(*)
                    FROM wallpaper_asset_usage_scores s
                    WHERE s.usageTypeId = u.id
                )
            FROM wallpaper_usage_types u
            $visibility
            ORDER BY u.sortOrder ASC, u.usageKey ASC
            """.trimIndent(),
            arrayOf(language, language)
        ).use { cursor ->

            while (cursor.moveToNext()) {
                result.add(
                    UsageType(
                        id = cursor.getString(0),
                        usageKey = cursor.getString(1),
                        name = cursor.getString(2),
                        description = if (cursor.isNull(3)) {
                            null
                        } else cursor.getString(3),
                        languageCode = language,
                        sortOrder = cursor.getInt(4),
                        isVisible = cursor.getInt(5) == 1,
                        wallpaperCount = cursor.getInt(6)
                    )
                )
            }
        }

        return result
    }

    fun saveAiSuitabilityScore(
        wallpaperId: String,
        usageTypeId: String,
        score: Double
    ) {
        require(score.isFinite() && score in 0.0..1.0) {
            "AI score must be between 0 and 1"
        }

        saveScore(
            wallpaperId,
            usageTypeId,
            "aiSuitabilityScore",
            score
        )
    }

    fun saveUserSuitabilityScore(
        wallpaperId: String,
        usageTypeId: String,
        score: Int
    ) {
        require(score in 0..100) {
            "User score must be between 0 and 100"
        }

        saveScore(
            wallpaperId,
            usageTypeId,
            "userSuitabilityScore",
            score
        )
    }

    private fun saveScore(
        wallpaperId: String,
        usageTypeId: String,
        column: String,
        score: Number
    ) {

        require(wallpaperId.isNotBlank())
        require(usageTypeId.isNotBlank())

        val db = database.writableDatabase
        val now = System.currentTimeMillis()

        db.beginTransaction()

        try {
            val values = ContentValues().apply {
                put(column, score)
                put(
                    if (column == "aiSuitabilityScore") {
                        "aiEvaluatedAt"
                    } else {
                        "userRatedAt"
                    },
                    now
                )
                put("updatedAt", now)
            }

            val updated = db.update(
                "wallpaper_asset_usage_scores",
                values,
                "wallpaperId = ? AND usageTypeId = ?",
                arrayOf(wallpaperId, usageTypeId)
            )

            if (updated == 0) {
                values.put("wallpaperId", wallpaperId)
                values.put("usageTypeId", usageTypeId)

                db.insertOrThrow(
                    "wallpaper_asset_usage_scores",
                    null,
                    values
                )
            }

            db.setTransactionSuccessful()

        } finally {
            db.endTransaction()
        }
    }

    fun getScoresForWallpaper(
        wallpaperId: String,
        languageCode: String = "en"
    ): List<UsageScore> {

        require(wallpaperId.isNotBlank())

        val language = cleanLanguage(languageCode)
        val result = mutableListOf<UsageScore>()

        database.readableDatabase.rawQuery(
            """
            SELECT
                s.usageTypeId,
                COALESCE(
                    tr.name,
                    u.usageKey
                ),
                s.aiSuitabilityScore,
                s.userSuitabilityScore,
                s.aiEvaluatedAt,
                s.userRatedAt
            FROM wallpaper_asset_usage_scores s
            INNER JOIN wallpaper_usage_types u
                ON u.id = s.usageTypeId
            LEFT JOIN wallpaper_usage_type_translations tr
                ON tr.usageTypeId = u.id
                AND tr.languageCode = ?
            WHERE s.wallpaperId = ?
            ORDER BY u.sortOrder ASC
            """.trimIndent(),
            arrayOf(language, wallpaperId)
        ).use { cursor ->

            while (cursor.moveToNext()) {
                result.add(
                    UsageScore(
                        usageTypeId = cursor.getString(0),
                        usageName = cursor.getString(1),
                        aiSuitabilityScore = if (cursor.isNull(2)) {
                            null
                        } else cursor.getDouble(2),
                        userSuitabilityScore = if (cursor.isNull(3)) {
                            null
                        } else cursor.getInt(3),
                        aiEvaluatedAt = if (cursor.isNull(4)) {
                            null
                        } else cursor.getLong(4),
                        userRatedAt = if (cursor.isNull(5)) {
                            null
                        } else cursor.getLong(5)
                    )
                )
            }
        }

        return result
    }

    fun setVisibility(
        usageTypeId: String,
        visible: Boolean
    ) {
        require(usageTypeId.isNotBlank())

        val values = ContentValues().apply {
            put("isVisible", if (visible) 1 else 0)
            put("updatedAt", System.currentTimeMillis())
        }

        val updated = database.writableDatabase.update(
            "wallpaper_usage_types",
            values,
            "id = ?",
            arrayOf(usageTypeId)
        )

        require(updated > 0) {
            "Usage type does not exist"
        }
    }

    fun close() {
        database.close()
    }
}

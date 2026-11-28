package com.monolith

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.util.Locale
import java.util.UUID

class WallpaperKeywordRepository(context: Context) {

    private val database = AppDatabase(context.applicationContext)

        data class Keyword(
        val id: String,
        val keyword: String,
        val languageCode: String,
        val aliasCount: Int,
        val wallpaperCount: Int,
        val previewPath: String? = null
    )

    private fun normalize(value: String): String {
        return value.trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
    }

    fun createKeyword(
        keyword: String,
        languageCode: String = "und"
    ): String {

        val cleanKeyword = keyword.trim()
        val normalized = normalize(cleanKeyword)
        val cleanLanguage = languageCode.trim()
            .ifEmpty { "und" }

        require(normalized.isNotEmpty()) {
            "Keyword cannot be empty"
        }

        val db = database.writableDatabase
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()

        db.beginTransaction()

        try {
            val existing = db.rawQuery(
                """
                SELECT id, languageCode
                FROM wallpaper_keywords
                WHERE normalizedKeyword = ?
                LIMIT 1
                """.trimIndent(),
                arrayOf(normalized)
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    Pair(cursor.getString(0), cursor.getString(1))
                } else {
                    null
                }
            }

            if (existing != null) {
                require(existing.second == cleanLanguage) {
                    "This keyword already exists in another language"
                }

                db.setTransactionSuccessful()
                return existing.first
            }

            val values = ContentValues().apply {
                put("id", id)
                put("keyword", cleanKeyword)
                put("normalizedKeyword", normalized)
                put("languageCode", cleanLanguage)
                put("createdAt", now)
                put("updatedAt", now)
            }

            db.insertOrThrow(
                "wallpaper_keywords",
                null,
                values
            )

            db.setTransactionSuccessful()

        } finally {
            db.endTransaction()
        }

        return id
    }

    fun addAlias(
        keywordId: String,
        alias: String,
        languageCode: String = "und"
    ): String {

        val cleanAlias = alias.trim()
        val normalized = normalize(cleanAlias)
        val cleanLanguage = languageCode.trim()
            .ifEmpty { "und" }

        require(keywordId.isNotBlank())
        require(normalized.isNotEmpty()) {
            "Alias cannot be empty"
        }

        val db = database.writableDatabase

        require(keywordExists(db, keywordId)) {
            "Keyword does not exist"
        }
        val id = UUID.randomUUID().toString()

        val values = ContentValues().apply {
            put("id", id)
            put("keywordId", keywordId)
            put("alias", cleanAlias)
            put("normalizedAlias", normalized)
            put("languageCode", cleanLanguage)
            put("createdAt", System.currentTimeMillis())
        }

        db.insertWithOnConflict(
            "wallpaper_keyword_aliases",
            null,
            values,
            SQLiteDatabase.CONFLICT_IGNORE
        )

        return db.rawQuery(
            """
            SELECT id, keywordId
            FROM wallpaper_keyword_aliases
            WHERE normalizedAlias = ?
              AND languageCode = ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(normalized, cleanLanguage)
        ).use { cursor ->

            require(cursor.moveToFirst()) {
                "Could not save alias"
            }

            require(cursor.getString(1) == keywordId) {
                "Alias already belongs to another keyword"
            }

            cursor.getString(0)
        }
    }

    fun getAllKeywords(): List<Keyword> {
        return queryKeywords(null)
    }

    fun searchKeywords(query: String): List<Keyword> {
        val cleanQuery = normalize(query)

        if (cleanQuery.isEmpty()) {
            return getAllKeywords()
        }

        return queryKeywords(cleanQuery)
    }

    private fun queryKeywords(
        query: String?
    ): List<Keyword> {

        val keywords = linkedMapOf<String, Keyword>()
        val db = database.readableDatabase

        val selection = if (query == null) {
            ""
        } else {
            """
            WHERE
                LOWER(k.keyword) LIKE ?
                OR EXISTS (
                    SELECT 1
                    FROM wallpaper_keyword_aliases a
                    WHERE a.keywordId = k.id
                      AND LOWER(a.alias) LIKE ?
                )
            """.trimIndent()
        }

        val arguments = query?.let {
            arrayOf("%$it%", "%$it%")
        }

        db.rawQuery(
            """
            SELECT
                k.id,
                k.keyword,
                k.languageCode,
                (
                    SELECT COUNT(*)
                    FROM wallpaper_keyword_aliases a
                    WHERE a.keywordId = k.id
                ),
                (
                    SELECT COUNT(*)
                    FROM wallpaper_asset_keywords countLinks
                    WHERE countLinks.keywordId = k.id
                ),
                w.thumbnailPath,
                w.filePath
            FROM wallpaper_keywords k
            LEFT JOIN wallpaper_asset_keywords ak
                ON ak.keywordId = k.id
            LEFT JOIN wallpaper_assets w
                ON w.id = ak.wallpaperId
               AND w.mediaType = 'IMAGE'
            $selection
            ORDER BY
                k.keyword COLLATE NOCASE ASC,
                w.createdAt DESC,
                w.id ASC
            """.trimIndent(),
            arguments
        ).use { cursor ->

            while (cursor.moveToNext()) {

                val id = cursor.getString(0)

                val current = keywords[id]

                val previewPath = if (
                    current?.previewPath == null
                ) {

                    val thumbnailPath = cursor.getString(5)
                    val originalPath = cursor.getString(6)

                    val validThumbnail = thumbnailPath
                        ?.takeIf {
                            java.io.File(it).isFile &&
                                java.io.File(it).canRead()
                        }

                    validThumbnail ?: originalPath?.takeIf {
                        java.io.File(it).isFile &&
                            java.io.File(it).canRead()
                    }

                } else {
                    current.previewPath
                }

                if (current == null) {

                    keywords[id] = Keyword(
                        id = id,
                        keyword = cursor.getString(1),
                        languageCode = cursor.getString(2),
                        aliasCount = cursor.getInt(3),
                        wallpaperCount = cursor.getInt(4),
                        previewPath = previewPath
                    )

                } else if (
                    current.previewPath == null &&
                    previewPath != null
                ) {

                    keywords[id] = current.copy(
                        previewPath = previewPath
                    )
                }
            }
        }

        return keywords.values.toList()
    }

    fun getKeywordsForWallpaper(
        wallpaperId: String
    ): List<Keyword> {

        require(wallpaperId.isNotBlank())

        val keywords = mutableListOf<Keyword>()

        database.readableDatabase.rawQuery(
            """
            SELECT
                k.id,
                k.keyword,
                k.languageCode,
                0,
                0
            FROM wallpaper_keywords k
            INNER JOIN wallpaper_asset_keywords ak
                ON ak.keywordId = k.id
            WHERE ak.wallpaperId = ?
            ORDER BY k.keyword COLLATE NOCASE ASC
            """.trimIndent(),
            arrayOf(wallpaperId)
        ).use { cursor ->

            while (cursor.moveToNext()) {
                keywords.add(
                    Keyword(
                        id = cursor.getString(0),
                        keyword = cursor.getString(1),
                        languageCode = cursor.getString(2),
                        aliasCount = cursor.getInt(3),
                        wallpaperCount = cursor.getInt(4)
                    )
                )
            }
        }

        return keywords
    }

    fun setKeywordsForWallpaper(
        wallpaperId: String,
        keywordIds: List<String>
    ) {

        val selectedIds = keywordIds
            .filter { it.isNotBlank() }
            .distinct()

        val db = database.writableDatabase

        db.beginTransaction()

        try {

            require(wallpaperExists(db, wallpaperId)) {
                "Wallpaper does not exist"
            }

            selectedIds.forEach { keywordId ->
                require(keywordExists(db, keywordId)) {
                    "Keyword does not exist: $keywordId"
                }
            }

            db.delete(
                "wallpaper_asset_keywords",
                "wallpaperId = ?",
                arrayOf(wallpaperId)
            )

            val now = System.currentTimeMillis()

            selectedIds.forEach { keywordId ->

                val values = ContentValues().apply {
                    put("wallpaperId", wallpaperId)
                    put("keywordId", keywordId)
                    put("createdAt", now)
                }

                db.insertOrThrow(
                    "wallpaper_asset_keywords",
                    null,
                    values
                )
            }

            db.setTransactionSuccessful()

        } finally {
            db.endTransaction()
        }
    }

    private fun keywordExists(
        db: SQLiteDatabase,
        keywordId: String
    ): Boolean {

        return db.rawQuery(
            """
            SELECT 1
            FROM wallpaper_keywords
            WHERE id = ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(keywordId)
        ).use {
            it.moveToFirst()
        }
    }

    private fun wallpaperExists(
        db: SQLiteDatabase,
        wallpaperId: String
    ): Boolean {

        return db.rawQuery(
            """
            SELECT 1
            FROM wallpaper_assets
            WHERE id = ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(wallpaperId)
        ).use {
            it.moveToFirst()
        }
    }

    fun close() {
        database.close()
    }
}

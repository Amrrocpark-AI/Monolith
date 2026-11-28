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

        db.beginTransaction()

        try {
            val existingConceptIds = mutableListOf<String>()

            db.rawQuery(
                """
                SELECT DISTINCT t.conceptId
                FROM wallpaper_keyword_terms t
                INNER JOIN wallpaper_keyword_concepts c
                    ON c.id = t.conceptId
                WHERE t.normalizedTerm = ?
                  AND t.languageCode = ?
                ORDER BY t.conceptId ASC
                """.trimIndent(),
                arrayOf(normalized, cleanLanguage)
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    existingConceptIds.add(cursor.getString(0))
                }
            }

            if (existingConceptIds.size == 1) {
                val existingId = existingConceptIds.first()

                db.setTransactionSuccessful()
                return existingId
            }

            require(existingConceptIds.isEmpty()) {
                "Ambiguous keyword: multiple concepts use this term in the same language"
            }

            val conceptId = UUID.randomUUID().toString()
            val termId = UUID.randomUUID().toString()

            val conceptValues = ContentValues().apply {
                put("id", conceptId)
                put("createdAt", now)
                put("updatedAt", now)
            }

            db.insertOrThrow(
                "wallpaper_keyword_concepts",
                null,
                conceptValues
            )

            val termValues = ContentValues().apply {
                put("id", termId)
                put("conceptId", conceptId)
                put("term", cleanKeyword)
                put("normalizedTerm", normalized)
                put("languageCode", cleanLanguage)
                put("isPrimary", 1)
                put("normalizationVersion", 1)
                put("createdAt", now)
                put("updatedAt", now)
            }

            db.insertOrThrow(
                "wallpaper_keyword_terms",
                null,
                termValues
            )

            db.setTransactionSuccessful()
            return conceptId

        } finally {
            db.endTransaction()
        }
    }

    fun addTranslation(
        conceptId: String,
        term: String,
        languageCode: String
    ): String {

        require(conceptId.isNotBlank())
        require(languageCode.isNotBlank())

        val cleanTerm = term.trim()
        val normalized = normalize(cleanTerm)
        val cleanLanguage = languageCode.trim()

        require(normalized.isNotEmpty()) {
            "Translation cannot be empty"
        }

        val db = database.writableDatabase
        val now = System.currentTimeMillis()

        db.beginTransaction()

        try {
            val conceptExists = db.rawQuery(
                """
                SELECT 1
                FROM wallpaper_keyword_concepts
                WHERE id = ?
                LIMIT 1
                """.trimIndent(),
                arrayOf(conceptId)
            ).use { it.moveToFirst() }

            require(conceptExists) {
                "Keyword concept does not exist"
            }

            val existing = db.rawQuery(
                """
                SELECT id
                FROM wallpaper_keyword_terms
                WHERE conceptId = ?
                  AND languageCode = ?
                  AND normalizedTerm = ?
                LIMIT 1
                """.trimIndent(),
                arrayOf(
                    conceptId,
                    cleanLanguage,
                    normalized
                )
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)
                } else {
                    null
                }
            }

            if (existing != null) {
                db.setTransactionSuccessful()
                return existing
            }

            val hasPrimary = db.rawQuery(
                """
                SELECT 1
                FROM wallpaper_keyword_terms
                WHERE conceptId = ?
                  AND languageCode = ?
                  AND isPrimary = 1
                LIMIT 1
                """.trimIndent(),
                arrayOf(conceptId, cleanLanguage)
            ).use { it.moveToFirst() }

            val id = UUID.randomUUID().toString()

            val values = ContentValues().apply {
                put("id", id)
                put("conceptId", conceptId)
                put("term", cleanTerm)
                put("normalizedTerm", normalized)
                put("languageCode", cleanLanguage)
                put("isPrimary", if (hasPrimary) 0 else 1)
                put("normalizationVersion", 1)
                put("createdAt", now)
                put("updatedAt", now)
            }

            db.insertOrThrow(
                "wallpaper_keyword_terms",
                null,
                values
            )

            db.setTransactionSuccessful()
            return id

        } finally {
            db.endTransaction()
        }
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

        require(keywordId.isNotBlank()) {
            "Keyword ID cannot be empty"
        }

        require(normalized.isNotEmpty()) {
            "Alias cannot be empty"
        }

        val db = database.writableDatabase
        val now = System.currentTimeMillis()

        db.beginTransaction()

        try {
            require(keywordExists(db, keywordId)) {
                "Keyword concept does not exist"
            }

            val existingId = db.rawQuery(
                """
                SELECT id
                FROM wallpaper_keyword_concept_aliases
                WHERE conceptId = ?
                  AND languageCode = ?
                  AND normalizedAlias = ?
                LIMIT 1
                """.trimIndent(),
                arrayOf(
                    keywordId,
                    cleanLanguage,
                    normalized
                )
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)
                } else {
                    null
                }
            }

            if (existingId != null) {
                db.setTransactionSuccessful()
                return existingId
            }

            val id = UUID.randomUUID().toString()

            val values = ContentValues().apply {
                put("id", id)
                put("conceptId", keywordId)
                put("alias", cleanAlias)
                put("normalizedAlias", normalized)
                put("languageCode", cleanLanguage)
                put("createdAt", now)
            }

            db.insertWithOnConflict(
                "wallpaper_keyword_concept_aliases",
                null,
                values,
                SQLiteDatabase.CONFLICT_IGNORE
            )

            val savedId = db.rawQuery(
                """
                SELECT id
                FROM wallpaper_keyword_concept_aliases
                WHERE conceptId = ?
                  AND languageCode = ?
                  AND normalizedAlias = ?
                LIMIT 1
                """.trimIndent(),
                arrayOf(
                    keywordId,
                    cleanLanguage,
                    normalized
                )
            ).use { cursor ->
                require(cursor.moveToFirst()) {
                    "Could not save alias"
                }

                cursor.getString(0)
            }

            db.setTransactionSuccessful()
            return savedId

        } finally {
            db.endTransaction()
        }
    }

    fun getAllKeywords(
        preferredLanguageCode: String = "und"
    ): List<Keyword> {
        return queryKeywords(
            null,
            preferredLanguageCode
        )
    }

    fun searchKeywords(
        query: String,
        preferredLanguageCode: String = "und"
    ): List<Keyword> {

        val cleanQuery = normalize(query)

        if (cleanQuery.isEmpty()) {
            return getAllKeywords(preferredLanguageCode)
        }

        return queryKeywords(
            cleanQuery,
            preferredLanguageCode
        )
    }

    private fun queryKeywords(
        query: String?,
        preferredLanguageCode: String
    ): List<Keyword> {

        val keywords = linkedMapOf<String, Keyword>()
        val db = database.readableDatabase

        val cleanLanguage = preferredLanguageCode.trim()
            .ifEmpty { "und" }
            .lowercase(Locale.ROOT)

        val baseLanguage = cleanLanguage
            .substringBefore("-")
            .substringBefore("_")

        val escapedQuery = query
            ?.let { normalize(it) }
            ?.replace("\\", "\\\\")
            ?.replace("%", "\\%")
            ?.replace("_", "\\_")

        val selection = if (escapedQuery == null) {
            ""
        } else {
            """
            WHERE
                EXISTS (
                    SELECT 1
                    FROM wallpaper_keyword_terms matched
                    WHERE matched.conceptId = c.id
                      AND matched.normalizedTerm LIKE ? ESCAPE '\'
                )
                OR EXISTS (
                    SELECT 1
                    FROM wallpaper_keyword_concept_aliases a
                    WHERE a.conceptId = c.id
                      AND a.normalizedAlias LIKE ? ESCAPE '\'
                )
            """.trimIndent()
        }

        val arguments = if (escapedQuery == null) {
            arrayOf(
                cleanLanguage,
                baseLanguage,
                cleanLanguage,
                baseLanguage
            )
        } else {
            arrayOf(
                cleanLanguage,
                baseLanguage,
                cleanLanguage,
                baseLanguage,
                "%$escapedQuery%",
                "%$escapedQuery%"
            )
        }

        db.rawQuery(
            """
            SELECT
                c.id,

                (
                    SELECT t.term
                    FROM wallpaper_keyword_terms t
                    WHERE t.conceptId = c.id
                    ORDER BY
                        CASE
                            WHEN t.languageCode = ?
                            THEN 0
                            WHEN t.languageCode = ?
                            THEN 1
                            WHEN t.languageCode = 'und'
                            THEN 2
                            ELSE 3
                        END,
                        t.isPrimary DESC,
                        t.languageCode ASC,
                        t.createdAt ASC
                    LIMIT 1
                ),

                (
                    SELECT t.languageCode
                    FROM wallpaper_keyword_terms t
                    WHERE t.conceptId = c.id
                    ORDER BY
                        CASE
                            WHEN t.languageCode = ?
                            THEN 0
                            WHEN t.languageCode = ?
                            THEN 1
                            WHEN t.languageCode = 'und'
                            THEN 2
                            ELSE 3
                        END,
                        t.isPrimary DESC,
                        t.languageCode ASC,
                        t.createdAt ASC
                    LIMIT 1
                ),

                (
                    SELECT COUNT(*)
                    FROM wallpaper_keyword_concept_aliases a
                    WHERE a.conceptId = c.id
                ),

                (
                    SELECT COUNT(*)
                    FROM wallpaper_asset_concepts ac
                    WHERE ac.conceptId = c.id
                ),

                (
                    SELECT w.thumbnailPath
                    FROM wallpaper_asset_concepts ac
                    INNER JOIN wallpaper_assets w
                        ON w.id = ac.wallpaperId
                    WHERE ac.conceptId = c.id
                      AND w.mediaType = 'IMAGE'
                    ORDER BY
                        w.createdAt DESC,
                        w.id ASC
                    LIMIT 1
                ),

                (
                    SELECT w.filePath
                    FROM wallpaper_asset_concepts ac
                    INNER JOIN wallpaper_assets w
                        ON w.id = ac.wallpaperId
                    WHERE ac.conceptId = c.id
                      AND w.mediaType = 'IMAGE'
                    ORDER BY
                        w.createdAt DESC,
                        w.id ASC
                    LIMIT 1
                )

            FROM wallpaper_keyword_concepts c

            $selection

            ORDER BY 2 COLLATE NOCASE ASC
            """.trimIndent(),
            arguments
        ).use { cursor ->

            while (cursor.moveToNext()) {

                val id = cursor.getString(0)
                val term = cursor.getString(1) ?: continue
                val languageCode = cursor.getString(2) ?: "und"

                val thumbnailPath = cursor.getString(5)
                val originalPath = cursor.getString(6)

                val validThumbnail = thumbnailPath
                    ?.takeIf {
                        java.io.File(it).isFile &&
                            java.io.File(it).canRead()
                    }

                val previewPath = validThumbnail
                    ?: originalPath?.takeIf {
                        java.io.File(it).isFile &&
                            java.io.File(it).canRead()
                    }

                keywords[id] = Keyword(
                    id = id,
                    keyword = term,
                    languageCode = languageCode,
                    aliasCount = cursor.getInt(3),
                    wallpaperCount = cursor.getInt(4),
                    previewPath = previewPath
                )
            }
        }

        return keywords.values.toList()
    }

    fun getKeywordsForWallpaper(
        wallpaperId: String,
        preferredLanguageCode: String = "und"
    ): List<Keyword> {

        require(wallpaperId.isNotBlank())

        val cleanLanguage = preferredLanguageCode.trim()
            .ifEmpty { "und" }

        val keywords = mutableListOf<Keyword>()

        database.readableDatabase.rawQuery(
            """
            SELECT
                c.id,
                t.term,
                t.languageCode,
                0,
                0
            FROM wallpaper_asset_concepts ac
            INNER JOIN wallpaper_keyword_concepts c
                ON c.id = ac.conceptId
            INNER JOIN wallpaper_keyword_terms t
                ON t.id = (
                    SELECT selected.id
                    FROM wallpaper_keyword_terms selected
                    WHERE selected.conceptId = c.id
                    ORDER BY
                        CASE
                            WHEN selected.languageCode = ?
                            THEN 0
                            WHEN selected.languageCode = 'und'
                            THEN 1
                            WHEN selected.isPrimary = 1
                            THEN 2
                            ELSE 3
                        END,
                        selected.languageCode ASC,
                        selected.createdAt ASC
                    LIMIT 1
                )
            WHERE ac.wallpaperId = ?
            ORDER BY t.term COLLATE NOCASE ASC
            """.trimIndent(),
            arrayOf(cleanLanguage, wallpaperId)
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

        require(wallpaperId.isNotBlank()) {
            "Wallpaper ID cannot be empty"
        }

        val selectedIds = keywordIds
            .filter { it.isNotBlank() }
            .distinct()

        val db = database.writableDatabase

        db.beginTransaction()

        try {
            require(wallpaperExists(db, wallpaperId)) {
                "Wallpaper does not exist"
            }

            selectedIds.forEach { conceptId ->
                require(keywordExists(db, conceptId)) {
                    "Keyword concept does not exist: $conceptId"
                }
            }

            db.delete(
                "wallpaper_asset_concepts",
                "wallpaperId = ?",
                arrayOf(wallpaperId)
            )

            val now = System.currentTimeMillis()

            selectedIds.forEach { conceptId ->

                val values = ContentValues().apply {
                    put("wallpaperId", wallpaperId)
                    put("conceptId", conceptId)
                    put("createdAt", now)
                }

                db.insertOrThrow(
                    "wallpaper_asset_concepts",
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
            FROM wallpaper_keyword_concepts
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


package com.monolith

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.util.Locale
import java.util.UUID

class WallpaperTraitRepository(context: Context) {

    private val database = AppDatabase(context.applicationContext)

    data class Trait(
        val id: String,
        val traitKey: String,
        val name: String,
        val description: String?,
        val languageCode: String,
        val colorHex: String,
        val sortOrder: Int,
        val isVisible: Boolean,
        val aliasCount: Int,
        val wallpaperCount: Int
    )

    data class Alias(
        val id: String,
        val traitId: String,
        val alias: String,
        val languageCode: String
    )

    private fun normalize(value: String): String {
        return value.trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
    }

    private fun cleanLanguage(languageCode: String): String {
        return languageCode.trim()
            .lowercase(Locale.ROOT)
            .ifEmpty { "und" }
    }

    private fun validateColor(colorHex: String): String {
        val color = colorHex.trim().uppercase(Locale.ROOT)

        require(
            Regex("^#[0-9A-F]{6}$").matches(color)
        ) {
            "Color must use #RRGGBB format"
        }

        return color
    }

    fun createTrait(
        traitKey: String,
        name: String,
        languageCode: String = "en",
        description: String? = null,
        colorHex: String = "#8B5CF6",
        sortOrder: Int = 0
    ): String {

        val cleanKey = traitKey.trim().lowercase(Locale.ROOT)
        val cleanName = name.trim()
        val cleanLanguage = cleanLanguage(languageCode)
        val cleanColor = validateColor(colorHex)

        require(
            Regex("^[a-z0-9][a-z0-9._-]{0,79}$").matches(cleanKey)
        ) {
            "Invalid trait key"
        }

        require(cleanName.isNotEmpty()) {
            "Trait name cannot be empty"
        }

        val db = database.writableDatabase
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        db.beginTransaction()

        try {
            val values = ContentValues().apply {
                put("id", id)
                put("traitKey", cleanKey)
                put("colorHex", cleanColor)
                put("sortOrder", sortOrder)
                put("isVisible", 1)
                put("createdAt", now)
                put("updatedAt", now)
            }

            db.insertOrThrow(
                "wallpaper_traits",
                null,
                values
            )

            saveTranslation(
                db = db,
                traitId = id,
                name = cleanName,
                languageCode = cleanLanguage,
                description = description
            )

            db.setTransactionSuccessful()

            return id

        } finally {
            db.endTransaction()
        }
    }

    fun saveTranslation(
        traitId: String,
        name: String,
        languageCode: String,
        description: String? = null
    ) {
        val db = database.writableDatabase

        saveTranslation(
            db,
            traitId,
            name,
            languageCode,
            description
        )
    }

    private fun saveTranslation(
        db: SQLiteDatabase,
        traitId: String,
        name: String,
        languageCode: String,
        description: String?
    ) {
        val cleanName = name.trim()
        val cleanLanguage = cleanLanguage(languageCode)

        require(traitId.isNotBlank())
        require(cleanName.isNotEmpty()) {
            "Translation name cannot be empty"
        }

        require(traitExists(db, traitId)) {
            "Trait does not exist"
        }

        val values = ContentValues().apply {
            put("traitId", traitId)
            put("languageCode", cleanLanguage)
            put("name", cleanName)
            put("normalizedName", normalize(cleanName))
            put("description", description?.trim())
        }

        db.insertWithOnConflict(
            "wallpaper_trait_translations",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun getTraits(
        languageCode: String = "en",
        includeHidden: Boolean = false
    ): List<Trait> {

        val language = cleanLanguage(languageCode)
        val visibility = if (includeHidden) "" else
            "WHERE t.isVisible = 1"

        val traits = mutableListOf<Trait>()

        database.readableDatabase.rawQuery(
            """
            SELECT
                t.id,
                t.traitKey,

                COALESCE(
                    (
                        SELECT tr.name
                        FROM wallpaper_trait_translations tr
                        WHERE tr.traitId = t.id
                          AND tr.languageCode = ?
                    ),
                    (
                        SELECT tr.name
                        FROM wallpaper_trait_translations tr
                        WHERE tr.traitId = t.id
                          AND tr.languageCode = 'en'
                    ),
                    t.traitKey
                ),

                COALESCE(
                    (
                        SELECT tr.description
                        FROM wallpaper_trait_translations tr
                        WHERE tr.traitId = t.id
                          AND tr.languageCode = ?
                    ),
                    (
                        SELECT tr.description
                        FROM wallpaper_trait_translations tr
                        WHERE tr.traitId = t.id
                          AND tr.languageCode = 'en'
                    )
                ),

                t.colorHex,
                t.sortOrder,
                t.isVisible,

                (
                    SELECT COUNT(*)
                    FROM wallpaper_trait_aliases a
                    WHERE a.traitId = t.id
                ),

                (
                    SELECT COUNT(*)
                    FROM wallpaper_asset_traits at
                    WHERE at.traitId = t.id
                )

            FROM wallpaper_traits t
            $visibility

            ORDER BY
                t.sortOrder ASC,
                t.traitKey COLLATE NOCASE ASC
            """.trimIndent(),
            arrayOf(language, language)
        ).use { cursor ->

            while (cursor.moveToNext()) {
                traits.add(
                    Trait(
                        id = cursor.getString(0),
                        traitKey = cursor.getString(1),
                        name = cursor.getString(2),
                        description = if (cursor.isNull(3)) {
                            null
                        } else {
                            cursor.getString(3)
                        },
                        languageCode = language,
                        colorHex = cursor.getString(4),
                        sortOrder = cursor.getInt(5),
                        isVisible = cursor.getInt(6) == 1,
                        aliasCount = cursor.getInt(7),
                        wallpaperCount = cursor.getInt(8)
                    )
                )
            }
        }

        return traits
    }

    fun searchTraits(
        query: String,
        languageCode: String = "en",
        includeHidden: Boolean = false
    ): List<Trait> {

        val cleanQuery = normalize(query)

        if (cleanQuery.isEmpty()) {
            return getTraits(languageCode, includeHidden)
        }

        val language = cleanLanguage(languageCode)
        val pattern = "%$cleanQuery%"
        val visibility = if (includeHidden) "" else
            "AND t.isVisible = 1"

        val ids = mutableListOf<String>()

        database.readableDatabase.rawQuery(
            """
            SELECT DISTINCT t.id
            FROM wallpaper_traits t

            WHERE (
                EXISTS (
                    SELECT 1
                    FROM wallpaper_trait_translations tr
                    WHERE tr.traitId = t.id
                      AND tr.normalizedName LIKE ?
                )

                OR EXISTS (
                    SELECT 1
                    FROM wallpaper_trait_aliases a
                    WHERE a.traitId = t.id
                      AND a.normalizedAlias LIKE ?
                )

                OR t.traitKey LIKE ?
            )

            $visibility

            ORDER BY t.sortOrder ASC
            """.trimIndent(),
            arrayOf(pattern, pattern, pattern)
        ).use { cursor ->

            while (cursor.moveToNext()) {
                ids.add(cursor.getString(0))
            }
        }

        if (ids.isEmpty()) return emptyList()

        val allTraits = getTraits(language, includeHidden)
        val byId = allTraits.associateBy { it.id }

        return ids.mapNotNull { byId[it] }
    }

    fun addAlias(
        traitId: String,
        alias: String,
        languageCode: String = "und"
    ): String {

        val cleanAlias = alias.trim()
        val normalized = normalize(cleanAlias)
        val language = cleanLanguage(languageCode)

        require(traitId.isNotBlank())
        require(normalized.isNotEmpty()) {
            "Alias cannot be empty"
        }

        val db = database.writableDatabase

        require(traitExists(db, traitId)) {
            "Trait does not exist"
        }

        val id = UUID.randomUUID().toString()

        val values = ContentValues().apply {
            put("id", id)
            put("traitId", traitId)
            put("alias", cleanAlias)
            put("normalizedAlias", normalized)
            put("languageCode", language)
        }

        db.insertWithOnConflict(
            "wallpaper_trait_aliases",
            null,
            values,
            SQLiteDatabase.CONFLICT_IGNORE
        )

        return db.rawQuery(
            """
            SELECT id, traitId
            FROM wallpaper_trait_aliases
            WHERE normalizedAlias = ?
              AND languageCode = ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(normalized, language)
        ).use { cursor ->

            require(cursor.moveToFirst()) {
                "Could not save alias"
            }

            require(cursor.getString(1) == traitId) {
                "Alias already belongs to another trait"
            }

            cursor.getString(0)
        }
    }

    fun getAliases(
        traitId: String
    ): List<Alias> {

        require(traitId.isNotBlank())

        val aliases = mutableListOf<Alias>()

        database.readableDatabase.rawQuery(
            """
            SELECT id, traitId, alias, languageCode
            FROM wallpaper_trait_aliases
            WHERE traitId = ?
            ORDER BY languageCode ASC, alias COLLATE NOCASE ASC
            """.trimIndent(),
            arrayOf(traitId)
        ).use { cursor ->

            while (cursor.moveToNext()) {
                aliases.add(
                    Alias(
                        id = cursor.getString(0),
                        traitId = cursor.getString(1),
                        alias = cursor.getString(2),
                        languageCode = cursor.getString(3)
                    )
                )
            }
        }

        return aliases
    }

    fun removeAlias(aliasId: String): Boolean {
        require(aliasId.isNotBlank())

        return database.writableDatabase.delete(
            "wallpaper_trait_aliases",
            "id = ?",
            arrayOf(aliasId)
        ) > 0
    }

    fun updateColor(
        traitId: String,
        colorHex: String
    ) {
        require(traitId.isNotBlank())

        val color = validateColor(colorHex)
        val values = ContentValues().apply {
            put("colorHex", color)
            put("updatedAt", System.currentTimeMillis())
        }

        val updated = database.writableDatabase.update(
            "wallpaper_traits",
            values,
            "id = ?",
            arrayOf(traitId)
        )

        require(updated > 0) {
            "Trait does not exist"
        }
    }

    fun setVisibility(
        traitId: String,
        visible: Boolean
    ) {
        require(traitId.isNotBlank())

        val values = ContentValues().apply {
            put("isVisible", if (visible) 1 else 0)
            put("updatedAt", System.currentTimeMillis())
        }

        val updated = database.writableDatabase.update(
            "wallpaper_traits",
            values,
            "id = ?",
            arrayOf(traitId)
        )

        require(updated > 0) {
            "Trait does not exist"
        }
    }

    fun updateSortOrder(
        traitId: String,
        sortOrder: Int
    ) {
        require(traitId.isNotBlank())

        val values = ContentValues().apply {
            put("sortOrder", sortOrder)
            put("updatedAt", System.currentTimeMillis())
        }

        val updated = database.writableDatabase.update(
            "wallpaper_traits",
            values,
            "id = ?",
            arrayOf(traitId)
        )

        require(updated > 0) {
            "Trait does not exist"
        }
    }

    fun setTraitsForWallpaper(
        wallpaperId: String,
        traitIds: List<String>
    ) {

        require(wallpaperId.isNotBlank())

        val selectedIds = traitIds
            .filter { it.isNotBlank() }
            .distinct()

        val db = database.writableDatabase

        db.beginTransaction()

        try {
            require(wallpaperExists(db, wallpaperId)) {
                "Wallpaper does not exist"
            }

            selectedIds.forEach { traitId ->
                require(traitExists(db, traitId)) {
                    "Trait does not exist: $traitId"
                }
            }

            db.delete(
                "wallpaper_asset_traits",
                "wallpaperId = ?",
                arrayOf(wallpaperId)
            )

            val now = System.currentTimeMillis()

            selectedIds.forEach { traitId ->

                val values = ContentValues().apply {
                    put("wallpaperId", wallpaperId)
                    put("traitId", traitId)
                    put("createdAt", now)
                }

                db.insertOrThrow(
                    "wallpaper_asset_traits",
                    null,
                    values
                )
            }

            db.setTransactionSuccessful()

        } finally {
            db.endTransaction()
        }
    }

    fun getTraitsForWallpaper(
        wallpaperId: String,
        languageCode: String = "en"
    ): List<Trait> {

        require(wallpaperId.isNotBlank())

        val selectedIds = mutableListOf<String>()

        database.readableDatabase.rawQuery(
            """
            SELECT traitId
            FROM wallpaper_asset_traits
            WHERE wallpaperId = ?
            ORDER BY createdAt ASC
            """.trimIndent(),
            arrayOf(wallpaperId)
        ).use { cursor ->

            while (cursor.moveToNext()) {
                selectedIds.add(cursor.getString(0))
            }
        }

        if (selectedIds.isEmpty()) return emptyList()

        val traits = getTraits(languageCode, includeHidden = true)
            .associateBy { it.id }

        return selectedIds.mapNotNull { traits[it] }
    }

    private fun traitExists(
        db: SQLiteDatabase,
        traitId: String
    ): Boolean {

        return db.rawQuery(
            """
            SELECT 1
            FROM wallpaper_traits
            WHERE id = ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(traitId)
        ).use { it.moveToFirst() }
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
        ).use { it.moveToFirst() }
    }

    fun close() {
        database.close()
    }
}

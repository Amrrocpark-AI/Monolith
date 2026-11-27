
package com.monolith

import android.content.ContentValues
import android.content.Context

class WallpaperTagRepository(context: Context) {

    private val database = AppDatabase(context)

    data class Tag(
        val id: String,
        val name: String,
        val nameEn: String,
        val groupKey: String,
        val isSystem: Boolean
    )

    companion object {
        private val SYSTEM_TAGS = listOf(
            Tag("style_anime", "أنمي", "Anime", "STYLE", true),
            Tag("style_cartoon", "كارتون", "Cartoon", "STYLE", true),
            Tag("style_realistic", "واقعي", "Realistic", "STYLE", true),
            Tag("style_pixel_art", "فن البكسل", "Pixel Art", "STYLE", true),
            Tag("theme_scifi", "خيال علمي", "Sci-Fi", "THEME", true),
            Tag("theme_romantic", "رومانسي", "Romantic", "THEME", true),
            Tag("theme_fantasy", "فانتازيا", "Fantasy", "THEME", true),
            Tag("theme_space", "فضاء ومجرات", "Space", "THEME", true),
            Tag("theme_nature", "طبيعة", "Nature", "THEME", true),
            Tag("theme_gaming", "ألعاب", "Gaming", "THEME", true),
            Tag("mood_cyberpunk", "سايبر بانك", "Cyberpunk", "MOOD", true),
            Tag("mood_dark", "مظلم", "Dark", "MOOD", true),
            Tag("mood_neon", "نيون", "Neon", "MOOD", true),
            Tag("mood_abstract", "تجريدي", "Abstract", "MOOD", true)
        )
    }

    fun initializeSystemTags() {
        val db = database.writableDatabase
        db.beginTransaction()

        try {
            SYSTEM_TAGS.forEach { tag ->
                val values = ContentValues().apply {
                    put("id", tag.id)
                    put("name", tag.name)
                    put("nameEn", tag.nameEn)
                    put("groupKey", tag.groupKey)
                    put("isSystem", 1)
                    put("createdAt", 0L)
                }

                db.insertWithOnConflict(
                    "wallpaper_tags",
                    null,
                    values,
                    android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
                )
            }

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getAllTags(): List<Tag> {
        val tags = mutableListOf<Tag>()

        database.readableDatabase.rawQuery(
            """
            SELECT id, name, nameEn, groupKey, isSystem
            FROM wallpaper_tags
            ORDER BY groupKey, name
            """.trimIndent(),
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                tags.add(
                    Tag(
                        id = cursor.getString(0),
                        name = cursor.getString(1),
                        nameEn = cursor.getString(2),
                        groupKey = cursor.getString(3),
                        isSystem = cursor.getInt(4) == 1
                    )
                )
            }
        }

        return tags
    }

    fun getTagsForWallpaper(wallpaperId: String): List<Tag> {
        val tags = mutableListOf<Tag>()

        database.readableDatabase.rawQuery(
            """
            SELECT t.id, t.name, t.nameEn, t.groupKey, t.isSystem
            FROM wallpaper_tags t
            INNER JOIN wallpaper_asset_tags r
                ON r.tagId = t.id
            WHERE r.wallpaperId = ?
            ORDER BY t.groupKey, t.name
            """.trimIndent(),
            arrayOf(wallpaperId)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                tags.add(
                    Tag(
                        id = cursor.getString(0),
                        name = cursor.getString(1),
                        nameEn = cursor.getString(2),
                        groupKey = cursor.getString(3),
                        isSystem = cursor.getInt(4) == 1
                    )
                )
            }
        }

        return tags
    }

    fun setTagsForWallpaper(
        wallpaperId: String,
        tagIds: List<String>
    ) {
        val db = database.writableDatabase
        db.beginTransaction()

        try {
            db.delete(
                "wallpaper_asset_tags",
                "wallpaperId = ?",
                arrayOf(wallpaperId)
            )

            tagIds.distinct().forEach { tagId ->
                val values = ContentValues().apply {
                    put("wallpaperId", wallpaperId)
                    put("tagId", tagId)
                }

                db.insertOrThrow(
                    "wallpaper_asset_tags",
                    null,
                    values
                )
            }

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun close() {
        database.close()
    }
}

package com.monolith

import android.content.ContentValues
import android.content.Context
import java.io.File

class WallpaperFavorites(context: Context) {

    private val appContext = context.applicationContext
    private val database = AppDatabase(appContext)

    fun addToFavorites(wallpaperId: String): Boolean {
        return setFavorite(wallpaperId, true)
    }

    fun removeFromFavorites(wallpaperId: String): Boolean {
        return setFavorite(wallpaperId, false)
    }

    fun toggleFavorite(wallpaperId: String): Boolean {

        val db = database.writableDatabase
        var newState = false
        var updated = false

        db.beginTransaction()

        try {
            val cursor = db.rawQuery(
                """
                SELECT isFavorite
                FROM wallpaper_assets
                WHERE id = ?
                """.trimIndent(),
                arrayOf(wallpaperId)
            )

            val exists = cursor.use {
                if (it.moveToFirst()) {
                    newState = it.getInt(0) != 1
                    true
                } else {
                    false
                }
            }

            if (exists) {
                updated = setFavoriteInDatabase(
                    db,
                    wallpaperId,
                    newState
                )
            }

            db.setTransactionSuccessful()

        } finally {
            db.endTransaction()
        }

        return updated
    }

    private fun setFavorite(
        wallpaperId: String,
        favorite: Boolean
    ): Boolean {

        return setFavoriteInDatabase(
            database.writableDatabase,
            wallpaperId,
            favorite
        )
    }

    private fun setFavoriteInDatabase(
        db: android.database.sqlite.SQLiteDatabase,
        wallpaperId: String,
        favorite: Boolean
    ): Boolean {

        val values = ContentValues().apply {
            put("isFavorite", if (favorite) 1 else 0)
            put("updatedAt", System.currentTimeMillis())
        }

        return db.update(
            "wallpaper_assets",
            values,
            "id = ?",
            arrayOf(wallpaperId)
        ) > 0
    }

    fun getFavoriteWallpapers():
        List<WallpaperLibraryReader.WallpaperItem> {

        val items = mutableListOf<WallpaperLibraryReader.WallpaperItem>()

        database.readableDatabase.rawQuery(
            """
            SELECT
                id,
                title,
                filePath,
                thumbnailPath,
                mimeType,
                mediaType,
                isFavorite,
                isApplied,
                createdAt
            FROM wallpaper_assets
            WHERE isFavorite = 1
            ORDER BY updatedAt DESC
            """.trimIndent(),
            null
        ).use { cursor ->

            while (cursor.moveToNext()) {

                items.add(
                    WallpaperLibraryReader.WallpaperItem(
                        id = cursor.getString(0),
                        title = cursor.getString(1),
                        filePath = cursor.getString(2),
                        thumbnailPath = if (cursor.isNull(3)) {
                            null
                        } else {
                            cursor.getString(3)
                        },
                        mimeType = cursor.getString(4),
                        mediaType = cursor.getString(5),
                        isFavorite = cursor.getInt(6) == 1,
                        isApplied = cursor.getInt(7) == 1,
                        createdAt = cursor.getLong(8)
                    )
                )
            }
        }

        return items
    }

    fun getExistingFavoriteWallpapers():
        List<WallpaperLibraryReader.WallpaperItem> {

        return getFavoriteWallpapers().filter {
            File(it.filePath).exists()
        }
    }

    fun close() {
        database.close()
    }
}

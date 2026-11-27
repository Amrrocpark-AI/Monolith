package com.monolith

import android.content.Context
import java.io.File

class WallpaperLibraryReader(context: Context) {

    private val database = AppDatabase(context)

    data class WallpaperItem(
        val id: String,
        val title: String,
        val filePath: String,
        val thumbnailPath: String?,
        val mimeType: String,
        val mediaType: String,
        val isFavorite: Boolean,
        val isApplied: Boolean,
        val createdAt: Long,
        val sourceType: String = "USER"
    )

    fun getAllWallpapers(): List<WallpaperItem> {

        val items = mutableListOf<WallpaperItem>()

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
                createdAt,
                sourceType
            FROM wallpaper_assets
            ORDER BY createdAt DESC
            """.trimIndent(),
            null
        ).use { cursor ->

            while (cursor.moveToNext()) {

                items.add(
                    WallpaperItem(
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
                        createdAt = cursor.getLong(8),
                        sourceType = cursor.getString(9)
                    )
                )
            }
        }

        return items
    }

    fun getExistingWallpapers(): List<WallpaperItem> {
        return getAllWallpapers().filter {
            File(it.filePath).exists()
        }
    }

    fun close() {
        database.close()
    }
}

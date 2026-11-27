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
        val createdAt: Long
    )

    fun getAllWallpapers(): List<WallpaperItem> {

        val items = mutableListOf<WallpaperItem>()

        val cursor = database.readableDatabase.rawQuery(
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
            ORDER BY createdAt DESC
            """.trimIndent(),
            null
        )

        cursor.use {
            while (it.moveToNext()) {

                items.add(
                    WallpaperItem(
                        id = it.getString(0),
                        title = it.getString(1),
                        filePath = it.getString(2),
                        thumbnailPath = if (it.isNull(3)) {
                            null
                        } else {
                            it.getString(3)
                        },
                        mimeType = it.getString(4),
                        mediaType = it.getString(5),
                        isFavorite = it.getInt(6) == 1,
                        isApplied = it.getInt(7) == 1,
                        createdAt = it.getLong(8)
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

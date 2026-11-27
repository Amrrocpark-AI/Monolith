package com.monolith

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

class WallpaperLibrary(context: Context) {

    private val database = AppDatabase(context)
    private val storage = WallpaperStorage(context)

    data class LibraryItem(
        val id: String,
        val title: String,
        val filePath: String,
        val mimeType: String,
        val mediaType: String
    )

    fun importWallpaper(
        uri: Uri,
        categoryId: String? = null
    ): LibraryItem {

        val stored = storage.importFromUri(uri)
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val mediaType = if (
            stored.mimeType.startsWith("video/")
        ) "VIDEO" else "IMAGE"

        try {
            val values = ContentValues().apply {
                put("id", id)
                put("title", stored.displayName)
                put("categoryId", categoryId)
                put("filePath", stored.file.absolutePath)
                putNull("thumbnailPath")
                put("mimeType", stored.mimeType)
                put("mediaType", mediaType)
                put("width", 0)
                put("height", 0)
                put("durationMs", 0)
                put("isFavorite", 0)
                put("isApplied", 0)
                put("createdAt", now)
                put("updatedAt", now)
            }

            database.writableDatabase.insertOrThrow(
                "wallpaper_assets",
                null,
                values
            )

            return LibraryItem(
                id = id,
                title = stored.displayName,
                filePath = stored.file.absolutePath,
                mimeType = stored.mimeType,
                mediaType = mediaType
            )

        } catch (error: Exception) {
            stored.file.delete()
            throw error
        }
    }

    fun close() {
        database.close()
    }
}

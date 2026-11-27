package com.monolith

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File
import java.io.IOException
import java.util.UUID

class WallpaperStorage(context: Context) {

    private val appContext = context.applicationContext

    data class StoredWallpaper(
        val file: File,
        val displayName: String,
        val mimeType: String
    )

    fun importFromUri(uri: Uri): StoredWallpaper {

        val resolver = appContext.contentResolver

        val mimeType = resolver.getType(uri)
            ?: throw IOException("Unknown file type")

        if (!mimeType.startsWith("image/") &&
            !mimeType.startsWith("video/")) {
            throw IOException("Unsupported wallpaper type")
        }

        val displayName = resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(0)
            } else {
                null
            }
        } ?: "wallpaper"

        val extension =
            MimeTypeMap.getSingleton()
                .getExtensionFromMimeType(mimeType)
                ?: if (mimeType.startsWith("video/")) "mp4" else "img"

        val directory = File(
            appContext.filesDir,
            "wallpapers"
        )

        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Cannot create wallpaper directory")
        }

        val destination = File(
            directory,
            "${UUID.randomUUID()}.$extension"
        )

        try {
            val input = resolver.openInputStream(uri)
                ?: throw IOException("Cannot open selected file")

            input.use { source ->
                destination.outputStream().use { output ->
                    source.copyTo(output)
                }
            }
        } catch (error: Exception) {
            destination.delete()
            throw error
        }

        return StoredWallpaper(
            file = destination,
            displayName = displayName,
            mimeType = mimeType
        )
    }
}

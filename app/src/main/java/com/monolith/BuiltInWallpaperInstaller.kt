package com.monolith

import android.content.ContentValues
import android.content.Context
import java.io.File
import java.io.IOException

class BuiltInWallpaperInstaller(context: Context) {

    private val appContext = context.applicationContext
    private val database = AppDatabase(appContext)

    private data class BuiltInWallpaper(
        val fileName: String,
        val title: String
    )

    private val wallpapers = listOf(
        BuiltInWallpaper("twin_hearts.png", "Twin Hearts"),
        BuiltInWallpaper("neon_glitch_anime.png", "Neon Glitch Anime")
    )

    fun install() {
        val db = database.writableDatabase
        val directory = File(appContext.filesDir, "wallpapers/built_in")
        val createdFiles = mutableListOf<File>()

        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Cannot create built-in wallpaper directory")
        }

        db.beginTransaction()

        try {
            for (wallpaper in wallpapers) {
                val id = "builtin_${wallpaper.fileName.removeSuffix(".png")}"
                val destination = File(directory, wallpaper.fileName)

                val existing = db.query(
                    "wallpaper_assets",
                    arrayOf("id"),
                    "id = ?",
                    arrayOf(id),
                    null,
                    null,
                    null
                ).use { it.moveToFirst() }

                if (existing && destination.exists()) {
                    continue
                }

                if (!destination.exists()) {
                    val temporaryFile = File.createTempFile(
                        "wallpaper_",
                        ".tmp",
                        directory
                    )

                    try {
                        appContext.assets.open(
                            "wallpapers/${wallpaper.fileName}"
                        ).use { input ->
                            temporaryFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }

                        if (!temporaryFile.renameTo(destination)) {
                            throw IOException(
                                "Cannot install ${wallpaper.fileName}"
                            )
                        }

                        createdFiles.add(destination)

                    } catch (error: Exception) {
                        temporaryFile.delete()
                        throw error
                    }
                }

                val now = System.currentTimeMillis()

                if (existing) {
                    val updates = ContentValues().apply {
                        put("filePath", destination.absolutePath)
                        put("mimeType", "image/png")
                        put("mediaType", "IMAGE")
                        put("sourceType", "BUILT_IN")
                        put("updatedAt", now)
                    }

                    db.update(
                        "wallpaper_assets",
                        updates,
                        "id = ?",
                        arrayOf(id)
                    )
                } else {
                    val values = ContentValues().apply {
                        put("id", id)
                        put("title", wallpaper.title)
                        putNull("categoryId")
                        put("filePath", destination.absolutePath)
                        putNull("thumbnailPath")
                        put("mimeType", "image/png")
                        put("mediaType", "IMAGE")
                        put("width", 0)
                        put("height", 0)
                        put("durationMs", 0)
                        put("isFavorite", 0)
                        put("isApplied", 0)
                        put("createdAt", now)
                        put("updatedAt", now)
                        put("sourceType", "BUILT_IN")
                    }

                    val inserted = db.insert(
                        "wallpaper_assets",
                        null,
                        values
                    )

                    if (inserted == -1L) {
                        throw IOException(
                            "Cannot register ${wallpaper.fileName}"
                        )
                    }
                }
            }

            db.setTransactionSuccessful()

        } catch (error: Exception) {
            createdFiles.forEach { it.delete() }
            throw error
        } finally {
            db.endTransaction()
        }
    }

    fun close() {
        database.close()
    }
}

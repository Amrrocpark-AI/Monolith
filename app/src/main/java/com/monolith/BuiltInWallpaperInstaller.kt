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
        BuiltInWallpaper(
            "twin_hearts.png",
            "Twin Hearts"
        ),
        BuiltInWallpaper(
            "neon_glitch_anime.png",
            "Neon Glitch Anime"
        )
    )

    fun install() {
        val assets = appContext.assets
        val databaseInstance = database.writableDatabase

        val directory = File(
            appContext.filesDir,
            "wallpapers/built_in"
        )

        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Cannot create built-in wallpaper directory")
        }

        databaseInstance.beginTransaction()

        try {
            for (wallpaper in wallpapers) {

                val id = "builtin_${wallpaper.fileName.removeSuffix(".png")}"
                val destination = File(directory, wallpaper.fileName)

                val existing = databaseInstance.query(
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

                assets.open(
                    "wallpapers/${wallpaper.fileName}"
                ).use { input ->
                    destination.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                val now = System.currentTimeMillis()

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

                databaseInstance.insertWithOnConflict(
                    "wallpaper_assets",
                    null,
                    values,
                    android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
                )
            }

            databaseInstance.setTransactionSuccessful()

        } catch (error: Exception) {
            throw error
        } finally {
            databaseInstance.endTransaction()
        }
    }

    fun close() {
        database.close()
    }
}

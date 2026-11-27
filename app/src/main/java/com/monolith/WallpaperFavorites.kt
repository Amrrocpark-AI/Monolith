package com.monolith

import android.content.ContentValues
import android.content.Context

class WallpaperFavorites(context: Context) {

    private val database = AppDatabase(context)

    fun addToFavorites(wallpaperId: String): Boolean {
        return setFavorite(wallpaperId, true)
    }

    fun removeFromFavorites(wallpaperId: String): Boolean {
        return setFavorite(wallpaperId, false)
    }

    fun toggleFavorite(wallpaperId: String): Boolean {

        val cursor = database.readableDatabase.rawQuery(
            "SELECT isFavorite FROM wallpaper_assets WHERE id = ?",
            arrayOf(wallpaperId)
        )

        val currentState = cursor.use {
            if (it.moveToFirst()) {
                it.getInt(0) == 1
            } else {
                return false
            }
        }

        return setFavorite(wallpaperId, !currentState)
    }

    private fun setFavorite(
        wallpaperId: String,
        favorite: Boolean
    ): Boolean {

        val values = ContentValues().apply {
            put("isFavorite", if (favorite) 1 else 0)
            put("updatedAt", System.currentTimeMillis())
        }

        return database.writableDatabase.update(
            "wallpaper_assets",
            values,
            "id = ?",
            arrayOf(wallpaperId)
        ) > 0
    }

    fun getFavoriteWallpapers(): List<WallpaperLibraryReader.WallpaperItem> {

        return WallpaperLibraryReader(
            databaseContext()
        ).getAllWallpapers().filter {
            it.isFavorite
        }
    }

    private fun databaseContext(): Context {
        return context
    }

    private val context = context.applicationContext

    fun close() {
        database.close()
    }
}

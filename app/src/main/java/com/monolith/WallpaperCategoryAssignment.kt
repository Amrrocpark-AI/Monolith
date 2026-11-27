package com.monolith

import android.content.ContentValues
import android.content.Context

class WallpaperCategoryAssignment(context: Context) {

    private val database = AppDatabase(context)

    fun moveWallpaperToCategory(
        wallpaperId: String,
        categoryId: String?
    ): Boolean {

        val db = database.writableDatabase

        if (categoryId != null) {
            db.rawQuery(
                "SELECT id FROM wallpaper_categories WHERE id = ?",
                arrayOf(categoryId)
            ).use { cursor ->
                if (!cursor.moveToFirst()) {
                    return false
                }
            }
        }

        val values = ContentValues().apply {
            put("categoryId", categoryId)
            put("updatedAt", System.currentTimeMillis())
        }

        return db.update(
            "wallpaper_assets",
            values,
            "id = ?",
            arrayOf(wallpaperId)
        ) > 0
    }

    fun removeWallpaperFromCategory(
        wallpaperId: String
    ): Boolean {
        return moveWallpaperToCategory(wallpaperId, null)
    }

    fun close() {
        database.close()
    }
}

package com.monolith

import android.content.ContentValues
import android.content.Context
import java.util.UUID

class WallpaperCategoryManager(context: Context) {

    private val database = AppDatabase(context)

    data class CategoryItem(
        val id: String,
        val name: String,
        val wallpaperCount: Int,
        val createdAt: Long
    )

    fun addCategory(name: String): CategoryItem {

        val cleanName = name.trim()

        require(cleanName.isNotEmpty()) {
            "Category name cannot be empty"
        }

        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val values = ContentValues().apply {
            put("id", id)
            put("name", cleanName)
            put("createdAt", now)
        }

        database.writableDatabase.insertOrThrow(
            "wallpaper_categories",
            null,
            values
        )

        return CategoryItem(
            id = id,
            name = cleanName,
            wallpaperCount = 0,
            createdAt = now
        )
    }

    fun getCategories(): List<CategoryItem> {

        val categories = mutableListOf<CategoryItem>()

        database.readableDatabase.rawQuery(
            """
            SELECT
                c.id,
                c.name,
                COUNT(w.id) AS wallpaperCount,
                c.createdAt
            FROM wallpaper_categories c
            LEFT JOIN wallpaper_assets w
                ON w.categoryId = c.id
            GROUP BY c.id, c.name, c.createdAt
            ORDER BY c.name COLLATE NOCASE ASC
            """.trimIndent(),
            null
        ).use { cursor ->

            while (cursor.moveToNext()) {
                categories.add(
                    CategoryItem(
                        id = cursor.getString(0),
                        name = cursor.getString(1),
                        wallpaperCount = cursor.getInt(2),
                        createdAt = cursor.getLong(3)
                    )
                )
            }
        }

        return categories
    }

    fun renameCategory(
        categoryId: String,
        newName: String
    ): Boolean {

        val cleanName = newName.trim()

        require(cleanName.isNotEmpty()) {
            "Category name cannot be empty"
        }

        val values = ContentValues().apply {
            put("name", cleanName)
        }

        return database.writableDatabase.update(
            "wallpaper_categories",
            values,
            "id = ?",
            arrayOf(categoryId)
        ) > 0
    }

    fun deleteCategory(categoryId: String): Boolean {

        return database.writableDatabase.delete(
            "wallpaper_categories",
            "id = ?",
            arrayOf(categoryId)
        ) > 0
    }

    fun close() {
        database.close()
    }
}

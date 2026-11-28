package com.monolith

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.util.UUID

class WallpaperCategoryRepository(context: Context) {

    private val database = AppDatabase(context.applicationContext)

    data class Category(
        val id: String,
        val name: String,
        val parentId: String?,
        val description: String?,
        val sortOrder: Int,
        val isVisible: Boolean,
        val wallpaperCount: Int
    )

    fun createCategory(
        name: String,
        parentId: String? = null,
        description: String? = null,
        sortOrder: Int = 0
    ): String {

        val cleanName = name.trim()

        require(cleanName.isNotEmpty()) {
            "Category name cannot be empty"
        }

        val db = database.writableDatabase

        if (parentId != null) {
            require(categoryExists(db, parentId)) {
                "Parent category does not exist"
            }
        }

        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val values = ContentValues().apply {
            put("id", id)
            put("name", cleanName)
            put("parentId", parentId)
            put("description", description?.trim())
            put("sortOrder", sortOrder)
            put("isVisible", 1)
            put("createdAt", now)
        }

        db.insertOrThrow(
            "wallpaper_categories",
            null,
            values
        )

        return id
    }

    fun getAllCategories(
        includeHidden: Boolean = true
    ): List<Category> {

        val categories = mutableListOf<Category>()

        val visibilityCondition =
            if (includeHidden) "" else "WHERE c.isVisible = 1"

        database.readableDatabase.rawQuery(
            """
            SELECT
                c.id,
                c.name,
                c.parentId,
                c.description,
                c.sortOrder,
                c.isVisible,
                COUNT(DISTINCT ac.wallpaperId)
            FROM wallpaper_categories c
            LEFT JOIN wallpaper_asset_categories ac
                ON ac.categoryId = c.id
            $visibilityCondition
            GROUP BY c.id
            ORDER BY
                c.sortOrder ASC,
                c.name COLLATE NOCASE ASC
            """.trimIndent(),
            null
        ).use { cursor ->

            while (cursor.moveToNext()) {
                categories.add(
                    Category(
                        id = cursor.getString(0),
                        name = cursor.getString(1),
                        parentId = cursor.getString(2),
                        description = cursor.getString(3),
                        sortOrder = cursor.getInt(4),
                        isVisible = cursor.getInt(5) == 1,
                        wallpaperCount = cursor.getInt(6)
                    )
                )
            }
        }

        return categories
    }

    fun getRootCategories(): List<Category> {
        return getCategoriesByParent(null)
    }

    fun getChildren(parentId: String): List<Category> {
        require(parentId.isNotBlank())

        return getCategoriesByParent(parentId)
    }

    private fun getCategoriesByParent(
        parentId: String?
    ): List<Category> {

        val categories = mutableListOf<Category>()

        val condition = if (parentId == null) {
            "c.parentId IS NULL"
        } else {
            "c.parentId = ?"
        }

        val arguments = parentId?.let {
            arrayOf(it)
        }

        database.readableDatabase.rawQuery(
            """
            SELECT
                c.id,
                c.name,
                c.parentId,
                c.description,
                c.sortOrder,
                c.isVisible,
                COUNT(DISTINCT ac.wallpaperId)
            FROM wallpaper_categories c
            LEFT JOIN wallpaper_asset_categories ac
                ON ac.categoryId = c.id
            WHERE $condition
            GROUP BY c.id
            ORDER BY
                c.sortOrder ASC,
                c.name COLLATE NOCASE ASC
            """.trimIndent(),
            arguments
        ).use { cursor ->

            while (cursor.moveToNext()) {
                categories.add(
                    Category(
                        id = cursor.getString(0),
                        name = cursor.getString(1),
                        parentId = cursor.getString(2),
                        description = cursor.getString(3),
                        sortOrder = cursor.getInt(4),
                        isVisible = cursor.getInt(5) == 1,
                        wallpaperCount = cursor.getInt(6)
                    )
                )
            }
        }

        return categories
    }

    fun getCategoriesForWallpaper(
        wallpaperId: String
    ): List<Category> {

        val categories = mutableListOf<Category>()

        database.readableDatabase.rawQuery(
            """
            SELECT
                c.id,
                c.name,
                c.parentId,
                c.description,
                c.sortOrder,
                c.isVisible,
                0
            FROM wallpaper_categories c
            INNER JOIN wallpaper_asset_categories ac
                ON ac.categoryId = c.id
            WHERE ac.wallpaperId = ?
            ORDER BY c.name COLLATE NOCASE ASC
            """.trimIndent(),
            arrayOf(wallpaperId)
        ).use { cursor ->

            while (cursor.moveToNext()) {
                categories.add(
                    Category(
                        id = cursor.getString(0),
                        name = cursor.getString(1),
                        parentId = cursor.getString(2),
                        description = cursor.getString(3),
                        sortOrder = cursor.getInt(4),
                        isVisible = cursor.getInt(5) == 1,
                        wallpaperCount = cursor.getInt(6)
                    )
                )
            }
        }

        return categories
    }

    fun setCategoriesForWallpaper(
        wallpaperId: String,
        categoryIds: List<String>
    ) {

        val selectedIds = categoryIds
            .filter { it.isNotBlank() }
            .distinct()

        val db = database.writableDatabase

        db.beginTransaction()

        try {

            require(wallpaperExists(db, wallpaperId)) {
                "Wallpaper does not exist"
            }

            selectedIds.forEach { categoryId ->
                require(categoryExists(db, categoryId)) {
                    "Category does not exist: $categoryId"
                }
            }

            db.delete(
                "wallpaper_asset_categories",
                "wallpaperId = ?",
                arrayOf(wallpaperId)
            )

            val now = System.currentTimeMillis()

            selectedIds.forEach { categoryId ->

                val values = ContentValues().apply {
                    put("wallpaperId", wallpaperId)
                    put("categoryId", categoryId)
                    put("createdAt", now)
                }

                db.insertOrThrow(
                    "wallpaper_asset_categories",
                    null,
                    values
                )
            }

            val primaryCategory = selectedIds.firstOrNull()

            val assetValues = ContentValues().apply {
                if (primaryCategory == null) {
                    putNull("categoryId")
                } else {
                    put("categoryId", primaryCategory)
                }
                put("updatedAt", now)
            }

            db.update(
                "wallpaper_assets",
                assetValues,
                "id = ?",
                arrayOf(wallpaperId)
            )

            db.setTransactionSuccessful()

        } finally {
            db.endTransaction()
        }
    }

    private fun categoryExists(
        db: SQLiteDatabase,
        categoryId: String
    ): Boolean {

        return db.rawQuery(
            """
            SELECT 1
            FROM wallpaper_categories
            WHERE id = ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(categoryId)
        ).use { it.moveToFirst() }
    }

    private fun wallpaperExists(
        db: SQLiteDatabase,
        wallpaperId: String
    ): Boolean {

        return db.rawQuery(
            """
            SELECT 1
            FROM wallpaper_assets
            WHERE id = ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(wallpaperId)
        ).use { it.moveToFirst() }
    }

    fun close() {
        database.close()
    }
}

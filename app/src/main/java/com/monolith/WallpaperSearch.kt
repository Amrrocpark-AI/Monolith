package com.monolith

import android.content.Context
import java.io.File

class WallpaperSearch(context: Context) {

    private val database = AppDatabase(context.applicationContext)

    enum class MediaFilter {
        ALL,
        IMAGE,
        VIDEO
    }

    enum class SortOrder {
        NEWEST,
        OLDEST,
        NAME_ASC,
        NAME_DESC
    }

    data class SearchOptions(
        val query: String = "",
        val media: MediaFilter = MediaFilter.ALL,
        val categoryId: String? = null,
        val favoriteOnly: Boolean = false,
        val appliedOnly: Boolean = false,
        val sort: SortOrder = SortOrder.NEWEST,
        val limit: Int? = null,
        val offset: Int = 0,
        val existingFilesOnly: Boolean = false
    )

    data class SearchResult(
        val items: List<WallpaperLibraryReader.WallpaperItem>,
        val totalCount: Int
    )

    fun search(options: SearchOptions = SearchOptions()): SearchResult {

        val conditions = mutableListOf<String>()
        val arguments = mutableListOf<String>()

        val cleanQuery = options.query.trim()

        if (cleanQuery.isNotEmpty()) {
            conditions.add("title LIKE ? COLLATE NOCASE")
            arguments.add("%$cleanQuery%")
        }

        when (options.media) {
            MediaFilter.ALL -> Unit

            MediaFilter.IMAGE -> {
                conditions.add("mediaType = ?")
                arguments.add("IMAGE")
            }

            MediaFilter.VIDEO -> {
                conditions.add("mediaType = ?")
                arguments.add("VIDEO")
            }
        }

        options.categoryId
            ?.takeIf { it.isNotBlank() }
            ?.let {
                conditions.add("categoryId = ?")
                arguments.add(it)
            }

        if (options.favoriteOnly) {
            conditions.add("isFavorite = 1")
        }

        if (options.appliedOnly) {
            conditions.add("isApplied = 1")
        }

        val whereClause = if (conditions.isEmpty()) {
            ""
        } else {
            "WHERE " + conditions.joinToString(" AND ")
        }

        val orderClause = when (options.sort) {
            SortOrder.NEWEST ->
                "createdAt DESC"

            SortOrder.OLDEST ->
                "createdAt ASC"

            SortOrder.NAME_ASC ->
                "title COLLATE NOCASE ASC"

            SortOrder.NAME_DESC ->
                "title COLLATE NOCASE DESC"
        }

        val safeOffset = options.offset.coerceAtLeast(0)
        val safeLimit = options.limit?.coerceAtLeast(1)

        val pagination = when {
            safeLimit != null ->
                "LIMIT $safeLimit OFFSET $safeOffset"

            safeOffset > 0 ->
                "LIMIT -1 OFFSET $safeOffset"

            else -> ""
        }

        val items = mutableListOf<WallpaperLibraryReader.WallpaperItem>()

        val db = database.readableDatabase

        val totalCount = db.rawQuery(
            """
            SELECT COUNT(*)
            FROM wallpaper_assets
            $whereClause
            """.trimIndent(),
            arguments.toTypedArray()
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }

        db.rawQuery(
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
            $whereClause
            ORDER BY $orderClause
            $pagination
            """.trimIndent(),
            arguments.toTypedArray()
        ).use { cursor ->

            while (cursor.moveToNext()) {

                val item = WallpaperLibraryReader.WallpaperItem(
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
                    createdAt = cursor.getLong(8)
                )

                if (
                    !options.existingFilesOnly ||
                    File(item.filePath).exists()
                ) {
                    items.add(item)
                }
            }
        }

        return SearchResult(
            items = items,
            totalCount = totalCount
        )
    }

    fun close() {
        database.close()
    }
}

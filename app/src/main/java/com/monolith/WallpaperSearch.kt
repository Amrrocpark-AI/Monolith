package com.monolith

import android.content.Context
import java.io.File

class WallpaperSearch(context: Context) {

    private val database =
        AppDatabase(context.applicationContext)

    enum class MediaFilter {
        ALL,
        IMAGE,
        VIDEO
    }

    enum class SourceFilter {
        ALL,
        BUILT_IN,
        USER
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
        val tagIds: List<String> = emptyList(),
        val keywordIds: List<String> = emptyList(),
        val traitIds: List<String> = emptyList(),
        val favoriteOnly: Boolean = false,
        val appliedOnly: Boolean = false,
        val sort: SortOrder = SortOrder.NEWEST,
        val limit: Int? = null,
        val offset: Int = 0,
        val existingFilesOnly: Boolean = false,
        val source: SourceFilter = SourceFilter.ALL
    )

    data class SearchResult(
        val items: List<WallpaperLibraryReader.WallpaperItem>,
        val totalCount: Int
    )

    fun search(
        options: SearchOptions = SearchOptions()
    ): SearchResult {

        val conditions = mutableListOf<String>()
        val arguments = mutableListOf<String>()

        val cleanQuery =
            WallpaperTextNormalizer.normalize(options.query)

        val queryTokens = cleanQuery
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .distinct()

        for (token in queryTokens) {
            val escapedToken = token
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_")

            val searchPattern = "%$escapedToken%"

            conditions.add(
                """
                (
                    title LIKE ? ESCAPE '\'
                    OR EXISTS (
                        SELECT 1
                        FROM wallpaper_asset_concepts AS assetConcept
                        INNER JOIN wallpaper_keyword_terms AS keywordTerm
                            ON keywordTerm.conceptId = assetConcept.conceptId
                        WHERE assetConcept.wallpaperId = wallpaper_assets.id
                            AND keywordTerm.normalizedTerm LIKE ? ESCAPE '\'
                    )
                    OR EXISTS (
                        SELECT 1
                        FROM wallpaper_asset_concepts AS assetConcept
                        INNER JOIN wallpaper_keyword_concept_aliases AS keywordAlias
                            ON keywordAlias.conceptId = assetConcept.conceptId
                        WHERE assetConcept.wallpaperId = wallpaper_assets.id
                            AND keywordAlias.normalizedAlias LIKE ? ESCAPE '\'
                    )
                )
                """.trimIndent()
            )

            arguments.add(searchPattern)
            arguments.add(searchPattern)
            arguments.add(searchPattern)
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

        when (options.source) {
            SourceFilter.ALL -> Unit

            SourceFilter.BUILT_IN -> {
                conditions.add("sourceType = ?")
                arguments.add("BUILT_IN")
            }

            SourceFilter.USER -> {
                conditions.add("sourceType = ?")
                arguments.add("USER")
            }
        }

                options.categoryId
            ?.takeIf { it.isNotBlank() }
            ?.let {
                conditions.add(
                    """
                    id IN (
                        SELECT wallpaperId
                        FROM wallpaper_asset_categories
                        WHERE categoryId = ?
                    )
                    """.trimIndent()
                )
                arguments.add(it)
            }
        val selectedTags = options.tagIds
            .filter { it.isNotBlank() }
            .distinct()

        if (selectedTags.isNotEmpty()) {
            val placeholders =
                selectedTags.joinToString(",") { "?" }

            conditions.add(
                """
                id IN (
                    SELECT wallpaperId
                    FROM wallpaper_asset_tags
                    WHERE tagId IN ($placeholders)
                    GROUP BY wallpaperId
                    HAVING COUNT(DISTINCT tagId) = ?
                )
                """.trimIndent()
            )

            arguments.addAll(selectedTags)
            arguments.add(selectedTags.size.toString())
        }

        val selectedKeywords = options.keywordIds
            .filter { it.isNotBlank() }
            .distinct()

        if (selectedKeywords.isNotEmpty()) {
            val placeholders =
                selectedKeywords.joinToString(",") { "?" }

            conditions.add(
                """
                id IN (
                    SELECT wallpaperId
                    FROM wallpaper_asset_concepts
                    WHERE conceptId IN ($placeholders)
                    GROUP BY wallpaperId
                    HAVING COUNT(DISTINCT conceptId) = ?
                )
                """.trimIndent()
            )

            arguments.addAll(selectedKeywords)
            arguments.add(selectedKeywords.size.toString())
        }

        val selectedTraits = options.traitIds
            .filter { it.isNotBlank() }
            .distinct()

        if (selectedTraits.isNotEmpty()) {
            val placeholders =
                selectedTraits.joinToString(",") { "?" }

            conditions.add(
                """
                id IN (
                    SELECT wallpaperId
                    FROM wallpaper_asset_traits
                    WHERE traitId IN ($placeholders)
                    GROUP BY wallpaperId
                    HAVING COUNT(DISTINCT traitId) = ?
                )
                """.trimIndent()
            )

            arguments.addAll(selectedTraits)
            arguments.add(selectedTraits.size.toString())
        }

        if (options.favoriteOnly) {
            conditions.add("isFavorite = 1")
        }

        if (options.appliedOnly) {
            conditions.add("isApplied = 1")
        }

        val whereClause =
            if (conditions.isEmpty()) {
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

        val items =
            mutableListOf<WallpaperLibraryReader.WallpaperItem>()

        val db = database.readableDatabase

        val totalCount = db.rawQuery(
            """
            SELECT COUNT(*)
            FROM wallpaper_assets
            $whereClause
            """.trimIndent(),
            arguments.toTypedArray()
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getInt(0)
            } else {
                0
            }
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
                createdAt,
                sourceType
            FROM wallpaper_assets
            $whereClause
            ORDER BY $orderClause
            $pagination
            """.trimIndent(),
            arguments.toTypedArray()
        ).use { cursor ->

            while (cursor.moveToNext()) {

                val item =
                    WallpaperLibraryReader.WallpaperItem(
                        id = cursor.getString(0),
                        title = cursor.getString(1),
                        filePath = cursor.getString(2),
                        thumbnailPath =
                            if (cursor.isNull(3)) {
                                null
                            } else {
                                cursor.getString(3)
                            },
                        mimeType = cursor.getString(4),
                        mediaType = cursor.getString(5),
                        isFavorite = cursor.getInt(6) == 1,
                        isApplied = cursor.getInt(7) == 1,
                        createdAt = cursor.getLong(8),
                        sourceType = cursor.getString(9)
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

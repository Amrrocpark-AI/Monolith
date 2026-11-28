package com.monolith

import java.util.Locale

/**
 * Centralized text normalization for wallpaper concepts,
 * terms, aliases, and search queries.
 *
 * Version 1 intentionally preserves the existing database
 * normalization behavior to avoid breaking stored values.
 */
object WallpaperTextNormalizer {

    const val CURRENT_VERSION = 1

    private val whitespaceRegex = Regex("\\s+")

    fun normalize(value: String): String {
        return value.trim()
            .lowercase(Locale.ROOT)
            .replace(whitespaceRegex, " ")
    }
}

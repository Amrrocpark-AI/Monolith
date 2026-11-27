
package com.monolith

import android.content.Context
import java.io.File

class ChatBackgroundState(context: Context) {

    private val preferences = context.applicationContext
        .getSharedPreferences(
            "ChatBackgroundState",
            Context.MODE_PRIVATE
        )

    data class Background(
        val wallpaperId: String,
        val filePath: String
    )

    companion object {
        private const val KEY_WALLPAPER_ID = "wallpaper_id"
        private const val KEY_FILE_PATH = "file_path"
    }

    fun getBackground(): Background? {
        val id = preferences.getString(KEY_WALLPAPER_ID, null)
            ?: return null

        val path = preferences.getString(KEY_FILE_PATH, null)
            ?: return null

        val file = File(path)

        if (!file.exists() || !file.isFile || file.length() <= 0L) {
            return null
        }

        return Background(
            wallpaperId = id,
            filePath = path
        )
    }

    fun setBackground(
        wallpaperId: String,
        filePath: String
    ): Boolean {

        if (wallpaperId.isBlank() || filePath.isBlank()) {
            return false
        }

        val file = File(filePath)

        if (!file.exists() || !file.isFile || file.length() <= 0L) {
            return false
        }

        return preferences.edit()
            .putString(KEY_WALLPAPER_ID, wallpaperId)
            .putString(KEY_FILE_PATH, file.absolutePath)
            .commit()
    }

    fun clearBackground(): Boolean {
        return preferences.edit()
            .remove(KEY_WALLPAPER_ID)
            .remove(KEY_FILE_PATH)
            .commit()
    }
}

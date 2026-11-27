package com.monolith

import android.content.Context

class WallpaperApplicationState(context: Context) {

    private val preferences = context.applicationContext
        .getSharedPreferences(
            "WallpaperApplicationState",
            Context.MODE_PRIVATE
        )

    companion object {
        private const val HOME_ID = "home_wallpaper_id"
        private const val LOCK_ID = "lock_wallpaper_id"
    }

    fun getHomeWallpaperId(): String? {
        return preferences.getString(HOME_ID, null)
    }

    fun getLockWallpaperId(): String? {
        return preferences.getString(LOCK_ID, null)
    }

    fun recordApplied(
        wallpaperId: String,
        target: WallpaperDeviceManager.Target
    ) {
        preferences.edit().apply {

            when (target) {
                WallpaperDeviceManager.Target.HOME -> {
                    putString(HOME_ID, wallpaperId)
                }

                WallpaperDeviceManager.Target.LOCK -> {
                    putString(LOCK_ID, wallpaperId)
                }

                WallpaperDeviceManager.Target.BOTH -> {
                    putString(HOME_ID, wallpaperId)
                    putString(LOCK_ID, wallpaperId)
                }
            }

            apply()
        }
    }

    fun clearHome() {
        preferences.edit()
            .remove(HOME_ID)
            .apply()
    }

    fun clearLock() {
        preferences.edit()
            .remove(LOCK_ID)
            .apply()
    }
}

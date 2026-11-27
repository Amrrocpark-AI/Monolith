package com.monolith

import android.content.Context
import java.io.File

class WallpaperLibraryApplyManager(context: Context) {

    private val appContext = context.applicationContext

    data class ApplyResult(
        val success: Boolean,
        val message: String,
        val systemApplied: Boolean = false,
        val lockApplied: Boolean = false
    )

    fun apply(
        wallpaperId: String,
        target: WallpaperDeviceManager.Target
    ): ApplyResult {

        val wallpaper = try {
            val reader = WallpaperLibraryReader(appContext)

            try {
                reader.getAllWallpapers()
                    .firstOrNull { it.id == wallpaperId }
            } finally {
                reader.close()
            }
        } catch (error: Exception) {
            return ApplyResult(
                success = false,
                message = error.message ?: "Library read failed"
            )
        }

        if (wallpaper == null) {
            return ApplyResult(
                success = false,
                message = "Wallpaper was not found"
            )
        }

        if (wallpaper.mediaType != "IMAGE") {
            return ApplyResult(
                success = false,
                message = "Only images can be applied as static wallpapers"
            )
        }

        val file = File(wallpaper.filePath)

        if (!file.exists() || !file.isFile) {
            return ApplyResult(
                success = false,
                message = "Wallpaper file is missing"
            )
        }

        val deviceManager = WallpaperDeviceManager(appContext)

        val result = deviceManager.applyImage(
            file = file,
            target = target
        )

        val state = WallpaperApplicationState(appContext)

        if (result.systemApplied) {
            state.recordApplied(
                wallpaperId,
                WallpaperDeviceManager.Target.HOME
            )
        }

        if (result.lockApplied) {
            state.recordApplied(
                wallpaperId,
                WallpaperDeviceManager.Target.LOCK
            )
        }

        val success = when (target) {
            WallpaperDeviceManager.Target.HOME ->
                result.systemApplied

            WallpaperDeviceManager.Target.LOCK ->
                result.lockApplied

            WallpaperDeviceManager.Target.BOTH ->
                result.systemApplied && result.lockApplied
        }

        return ApplyResult(
            success = success,
            message = result.errorMessage
                ?: if (success) {
                    "Wallpaper applied successfully"
                } else {
                    "Wallpaper application failed"
                },
            systemApplied = result.systemApplied,
            lockApplied = result.lockApplied
        )
    }
}

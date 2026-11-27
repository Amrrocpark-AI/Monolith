package com.monolith

import android.app.WallpaperManager
import android.content.Context
import android.graphics.BitmapFactory
import java.io.File

class WallpaperDeviceManager(context: Context) {

    private val appContext = context.applicationContext

    enum class Target {
        HOME,
        LOCK,
        BOTH
    }

    data class ApplyResult(
        val target: Target,
        val systemApplied: Boolean,
        val lockApplied: Boolean,
        val errorMessage: String? = null
    ) {
        val success: Boolean
            get() = when (target) {
                Target.HOME -> systemApplied
                Target.LOCK -> lockApplied
                Target.BOTH -> systemApplied && lockApplied
            }
    }

    fun applyImage(
        file: File,
        target: Target
    ): ApplyResult {

        if (!file.exists() || !file.isFile || file.length() == 0L) {
            return failure(target, "Image file is missing")
        }

        val imageBounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        BitmapFactory.decodeFile(
            file.absolutePath,
            imageBounds
        )

        if (imageBounds.outWidth <= 0 || imageBounds.outHeight <= 0) {
            return failure(target, "Invalid image file")
        }

        val manager = WallpaperManager.getInstance(appContext)

        if (!manager.isWallpaperSupported()) {
            return failure(target, "Wallpaper is not supported")
        }

        if (!manager.isSetWallpaperAllowed()) {
            return failure(target, "Wallpaper changes are blocked")
        }

        var systemApplied = false
        var lockApplied = false

        val errors = mutableListOf<String>()

        if (target == Target.HOME || target == Target.BOTH) {
            try {
                file.inputStream().use { input ->
                    manager.setStream(
                        input,
                        null,
                        true,
                        WallpaperManager.FLAG_SYSTEM
                    )
                }

                systemApplied = true

            } catch (error: Exception) {
                errors.add(error.message ?: "Home wallpaper failed")
            }
        }

        if (target == Target.LOCK || target == Target.BOTH) {
            try {
                file.inputStream().use { input ->
                    manager.setStream(
                        input,
                        null,
                        true,
                        WallpaperManager.FLAG_LOCK
                    )
                }

                lockApplied = true

            } catch (error: Exception) {
                errors.add(error.message ?: "Lock wallpaper failed")
            }
        }

        return ApplyResult(
            target = target,
            systemApplied = systemApplied,
            lockApplied = lockApplied,
            errorMessage = errors.takeIf { it.isNotEmpty() }
                ?.joinToString(" | ")
        )
    }

    private fun failure(
        target: Target,
        message: String
    ): ApplyResult {
        return ApplyResult(
            target = target,
            systemApplied = false,
            lockApplied = false,
            errorMessage = message
        )
    }
}

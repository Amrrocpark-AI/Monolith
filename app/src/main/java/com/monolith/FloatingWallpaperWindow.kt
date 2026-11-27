package com.monolith

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView

object FloatingWallpaperWindow {

    private var wallpaperView: View? = null
    private var currentWindowManager: WindowManager? = null

    fun show(
        context: Context,
        windowManager: WindowManager,
        x: Int,
        y: Int
    ) {
        if (wallpaperView != null) return

        try {
            val view = LayoutInflater.from(context)
                .inflate(R.layout.floating_wallpaper_library, null)

            val windowType =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_PHONE
                }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                windowType,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            )

            params.gravity = Gravity.TOP or Gravity.START
            params.x = x
            params.y = y
            params.softInputMode =
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE

            view.findViewById<ImageView>(
                R.id.btnCloseWallpapers
            ).setOnClickListener {
                close()
            }

            windowManager.addView(view, params)

            wallpaperView = view
            currentWindowManager = windowManager

        } catch (e: Exception) {
            e.printStackTrace()
            wallpaperView = null
            currentWindowManager = null
        }
    }

    fun close() {
        try {
            wallpaperView?.let { view ->
                currentWindowManager?.removeView(view)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            wallpaperView = null
            currentWindowManager = null
        }
    }
}

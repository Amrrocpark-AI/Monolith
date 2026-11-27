package com.monolith

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.content.Context.INPUT_METHOD_SERVICE
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.app.AlertDialog
import com.bumptech.glide.Glide
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object FloatingWallpaperWindow {

    private var wallpaperView: View? = null
    private var currentWindowManager: WindowManager? = null
    private var windowContext: Context? = null

    private var scope: CoroutineScope? = null
    private var searchJob: Job? = null

    private val handler = Handler(Looper.getMainLooper())

    private var activeMedia =
        WallpaperSearch.MediaFilter.ALL

    private var favoritesOnly = false

    private var searchGeneration = 0

    private val searchRunnable = Runnable {
        reloadLibrary()
    }

    fun show(
        context: Context,
        windowManager: WindowManager,
        x: Int,
        y: Int
    ) {
        if (wallpaperView != null) return

        val appContext = context.applicationContext

        try {
            val view = LayoutInflater.from(context)
                .inflate(
                    R.layout.floating_wallpaper_library,
                    null
                )

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

            windowManager.addView(view, params)

            wallpaperView = view
            currentWindowManager = windowManager
            windowContext = appContext

            scope = CoroutineScope(
                SupervisorJob() + Dispatchers.Main.immediate
            )

            initializeLibrary(view, appContext)

        } catch (error: Exception) {
            error.printStackTrace()
            close()
        }
    }

    private fun initializeLibrary(
        root: View,
        context: Context
    ) {
        val searchInput =
            root.findViewById<EditText>(
                R.id.etWallpaperSearch
            )

        root.findViewById<ImageView>(
            R.id.btnCloseWallpapers
        ).setOnClickListener {
            close()
        }

        root.findViewById<View>(
            R.id.filterAll
        ).setOnClickListener {
            activeMedia = WallpaperSearch.MediaFilter.ALL
            favoritesOnly = false
            updateFilterAppearance(root)
            reloadLibrary()
        }

        root.findViewById<View>(
            R.id.filterImages
        ).setOnClickListener {
            activeMedia = WallpaperSearch.MediaFilter.IMAGE
            favoritesOnly = false
            updateFilterAppearance(root)
            reloadLibrary()
        }

        root.findViewById<View>(
            R.id.filterVideos
        ).setOnClickListener {
            activeMedia = WallpaperSearch.MediaFilter.VIDEO
            favoritesOnly = false
            updateFilterAppearance(root)
            reloadLibrary()
        }

        root.findViewById<View>(
            R.id.filterFavorites
        ).setOnClickListener {
            activeMedia = WallpaperSearch.MediaFilter.ALL
            favoritesOnly = true
            updateFilterAppearance(root)
            reloadLibrary()
        }

        searchInput.addTextChangedListener(
            object : TextWatcher {

                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {}

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {
                    handler.removeCallbacks(searchRunnable)

                    handler.postDelayed(
                        searchRunnable,
                        300
                    )
                }

                override fun afterTextChanged(
                    s: Editable?
                ) {}
            }
        )

        activeMedia = WallpaperSearch.MediaFilter.ALL
        favoritesOnly = false

        updateFilterAppearance(root)
        reloadLibrary()
    }

    private fun reloadLibrary() {
        val root = wallpaperView ?: return
        val context = windowContext ?: return
        val currentScope = scope ?: return

        val query = root.findViewById<EditText>(
            R.id.etWallpaperSearch
        ).text.toString()

        val selectedMedia = activeMedia
        val selectedFavorites = favoritesOnly

        searchGeneration++
        val currentGeneration = searchGeneration

        searchJob?.cancel()

        searchJob = currentScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {

                    val search = WallpaperSearch(context)

                    try {
                        search.search(
                            WallpaperSearch.SearchOptions(
                                query = query,
                                media = selectedMedia,
                                favoriteOnly = selectedFavorites,
                                existingFilesOnly = true
                            )
                        )
                    } finally {
                        search.close()
                    }
                }

                if (
                    wallpaperView !== root ||
                    currentGeneration != searchGeneration
                ) {
                    return@launch
                }

                displayResults(
                    root,
                    result.items
                )

            } catch (error: Exception) {

                error.printStackTrace()

                if (
                    wallpaperView === root &&
                    currentGeneration == searchGeneration
                ) {
                    displayError(root)
                }
            }
        }
    }

    private fun displayResults(
        root: View,
        items: List<WallpaperLibraryReader.WallpaperItem>
    ) {
        val container = root.findViewById<LinearLayout>(
            R.id.wallpaperItemsContainer
        )

        val emptyView = root.findViewById<TextView>(
            R.id.tvWallpaperEmpty
        )

        val countView = root.findViewById<TextView>(
            R.id.tvWallpaperCount
        )

        container.removeAllViews()

        countView.text =
            "LOCAL LIBRARY • ${items.size} ITEMS"

        if (items.isEmpty()) {

            emptyView.text =
                if (favoritesOnly) {
                    "NO FAVORITE WALLPAPERS"
                } else {
                    "YOUR WALLPAPER SPACE IS EMPTY"
                }

            container.addView(emptyView)
            return
        }

        items.forEach { item ->
            container.addView(
                createWallpaperCard(
                    root.context,
                    item
                )
            )
        }
    }

    private fun createWallpaperCard(
        context: Context,
        item: WallpaperLibraryReader.WallpaperItem
    ): View {

        val card = LinearLayout(context).apply {

            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL

            setPadding(
                dp(context, 9),
                dp(context, 9),
                dp(context, 9),
                dp(context, 9)
            )

            background = roundedBackground(
                context,
                Color.argb(185, 8, 24, 43),
                Color.argb(110, 0, 229, 255),
                18f
            )

            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(context, 10)
            }
        }

        val thumbnailFrame = FrameLayout(context).apply {

            layoutParams = LinearLayout.LayoutParams(
                dp(context, 104),
                dp(context, 88)
            )

            background = roundedBackground(
                context,
                Color.rgb(15, 30, 48),
                Color.argb(100, 0, 229, 255),
                12f
            )

            clipToOutline = true
        }

        val thumbnail = ImageView(context).apply {

            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = item.title
        }

        val thumbnailFile =
            item.thumbnailPath
                ?.let { File(it) }
                ?.takeIf { it.exists() }
                ?: File(item.filePath)

        val placeholder = roundedBackground(
            context,
            Color.rgb(20, 38, 58),
            Color.TRANSPARENT,
            12f
        )

        Glide.with(thumbnail)
            .load(thumbnailFile)
            .centerCrop()
            .placeholder(placeholder)
            .error(placeholder)
            .into(thumbnail)

        thumbnailFrame.addView(thumbnail)

        if (item.mediaType == "VIDEO") {

            val videoBadge = TextView(context).apply {

                text = "VIDEO"
                textSize = 9f
                setTextColor(Color.WHITE)

                setPadding(
                    dp(context, 6),
                    dp(context, 3),
                    dp(context, 6),
                    dp(context, 3)
                )

                background = roundedBackground(
                    context,
                    Color.argb(210, 0, 125, 180),
                    Color.TRANSPARENT,
                    7f
                )
            }

            val badgeParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {

                gravity = Gravity.TOP or Gravity.END

                setMargins(
                    0,
                    dp(context, 5),
                    dp(context, 5),
                    0
                )
            }

            thumbnailFrame.addView(
                videoBadge,
                badgeParams
            )
        }

        val details = LinearLayout(context).apply {

            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL

            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginStart = dp(context, 10)
            }
        }

        val title = TextView(context).apply {

            text = item.title
            textSize = 13f
            setTextColor(Color.WHITE)
            maxLines = 2

            ellipsize =
                android.text.TextUtils.TruncateAt.END
        }

        val state = WallpaperApplicationState(context)

        val homeApplied =
            state.getHomeWallpaperId() == item.id

        val lockApplied =
            state.getLockWallpaperId() == item.id

        val appliedLabel = when {
            homeApplied && lockApplied -> "HOME • LOCK"
            homeApplied -> "HOME"
            lockApplied -> "LOCK"
            else -> ""
        }

        val metadata = TextView(context).apply {

            text = buildString {
                append(item.mediaType)
                append(" • LOCAL")

                if (appliedLabel.isNotEmpty()) {
                    append(" • ")
                    append(appliedLabel)
                }
            }

            textSize = 9f

            setTextColor(
                Color.rgb(0, 210, 235)
            )

            setPadding(
                0,
                dp(context, 6),
                0,
                0
            )
        }

        details.addView(title)
        details.addView(metadata)

        val actions = LinearLayout(context).apply {

            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val applyButton = TextView(context).apply {

            text = if (item.mediaType == "IMAGE") {
                "SET"
            } else {
                "VIDEO"
            }

            textSize = 10f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)

            background = roundedBackground(
                context,
                Color.argb(190, 0, 115, 155),
                Color.argb(180, 0, 229, 255),
                12f
            )

            layoutParams = LinearLayout.LayoutParams(
                dp(context, 48),
                dp(context, 42)
            ).apply {
                marginEnd = dp(context, 6)
            }

            isEnabled = item.mediaType == "IMAGE"

            if (!isEnabled) {
                alpha = 0.45f
            }

            contentDescription = "Apply wallpaper"
        }

        applyButton.setOnClickListener {
            if (item.mediaType == "IMAGE") {
                showApplyTargetDialog(
                    context,
                    item.id
                )
            }
        }

        val favoriteButton = TextView(context).apply {

            text = if (item.isFavorite) "★" else "☆"
            textSize = 25f

            gravity = Gravity.CENTER

            setTextColor(
                if (item.isFavorite) {
                    Color.rgb(255, 205, 65)
                } else {
                    Color.LTGRAY
                }
            )

            background = roundedBackground(
                context,
                Color.argb(130, 12, 29, 48),
                Color.argb(90, 0, 229, 255),
                30f
            )

            layoutParams = LinearLayout.LayoutParams(
                dp(context, 42),
                dp(context, 42)
            )

            contentDescription = "Toggle favorite"
        }

        favoriteButton.setOnClickListener {
            toggleFavorite(item.id)
        }

        actions.addView(applyButton)
        actions.addView(favoriteButton)

        card.addView(thumbnailFrame)
        card.addView(details)
        card.addView(actions)

        return card
    }

    private fun showApplyTargetDialog(
        context: Context,
        wallpaperId: String
    ) {
        AlertDialog.Builder(context)
            .setTitle("APPLY WALLPAPER")
            .setItems(
                arrayOf(
                    "Home screen",
                    "Lock screen",
                    "Both screens"
                )
            ) { _, selected ->

                val target = when (selected) {
                    0 -> WallpaperDeviceManager.Target.HOME
                    1 -> WallpaperDeviceManager.Target.LOCK
                    else -> WallpaperDeviceManager.Target.BOTH
                }

                applyWallpaper(
                    wallpaperId,
                    target
                )
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun applyWallpaper(
        wallpaperId: String,
        target: WallpaperDeviceManager.Target
    ) {
        val currentScope = scope ?: return
        val context = windowContext ?: return

        currentScope.launch {

            try {
                val result = withContext(Dispatchers.IO) {

                    WallpaperLibraryApplyManager(context)
                        .apply(
                            wallpaperId,
                            target
                        )
                }

                Toast.makeText(
                    context,
                    result.message,
                    Toast.LENGTH_LONG
                ).show()

                if (result.success && wallpaperView != null) {
                    reloadLibrary()
                }

            } catch (error: Exception) {

                error.printStackTrace()

                Toast.makeText(
                    context,
                    error.message ?: "Wallpaper application failed",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun toggleFavorite(
        wallpaperId: String
    ) {
        val currentScope = scope ?: return
        val context = windowContext ?: return

        currentScope.launch {

            try {
                val updated = withContext(Dispatchers.IO) {

                    val favorites = WallpaperFavorites(context)

                    try {
                        favorites.toggleFavorite(wallpaperId)
                    } finally {
                        favorites.close()
                    }
                }

                if (updated && wallpaperView != null) {
                    reloadLibrary()
                }

            } catch (error: Exception) {
                error.printStackTrace()
            }
        }
    }

    private fun updateFilterAppearance(
        root: View
    ) {
        val filters = listOf(
            R.id.filterAll,
            R.id.filterImages,
            R.id.filterVideos,
            R.id.filterFavorites
        )

        val selected = when {

            favoritesOnly ->
                R.id.filterFavorites

            activeMedia == WallpaperSearch.MediaFilter.IMAGE ->
                R.id.filterImages

            activeMedia == WallpaperSearch.MediaFilter.VIDEO ->
                R.id.filterVideos

            else ->
                R.id.filterAll
        }

        filters.forEach { id ->

            val view = root.findViewById<TextView>(id)

            view.setTextColor(
                if (id == selected) {
                    Color.rgb(0, 229, 255)
                } else {
                    Color.WHITE
                }
            )

            view.alpha =
                if (id == selected) 1f else 0.68f
        }
    }

    private fun displayError(
        root: View
    ) {
        val container = root.findViewById<LinearLayout>(
            R.id.wallpaperItemsContainer
        )

        val emptyView = root.findViewById<TextView>(
            R.id.tvWallpaperEmpty
        )

        container.removeAllViews()

        emptyView.text =
            "LIBRARY TEMPORARILY UNAVAILABLE"

        container.addView(emptyView)

        root.findViewById<TextView>(
            R.id.tvWallpaperCount
        ).text = "LOCAL LIBRARY"
    }

    private fun roundedBackground(
        context: Context,
        fillColor: Int,
        strokeColor: Int,
        radius: Float
    ): GradientDrawable {

        return GradientDrawable().apply {

            setColor(fillColor)
            cornerRadius = dp(context, radius.toInt()).toFloat()

            setStroke(
                dp(context, 1),
                strokeColor
            )
        }
    }

    private fun dp(
        context: Context,
        value: Int
    ): Int {
        return (
            value * context.resources.displayMetrics.density
        ).toInt()
    }

    private fun clearImageRequests(
        view: View
    ) {
        if (view is ImageView) {

            try {
                Glide.with(view).clear(view)
            } catch (_: Exception) {
            }
        }

        if (view is ViewGroup) {

            for (index in 0 until view.childCount) {
                clearImageRequests(
                    view.getChildAt(index)
                )
            }
        }
    }

    fun close() {

        handler.removeCallbacks(searchRunnable)

        searchGeneration++

        searchJob?.cancel()
        searchJob = null

        scope?.cancel()
        scope = null

        wallpaperView?.let { view ->

            try {
                val inputManager =
                    view.context.getSystemService(
                        INPUT_METHOD_SERVICE
                    ) as? InputMethodManager

                inputManager?.hideSoftInputFromWindow(
                    view.windowToken,
                    0
                )

                clearImageRequests(view)

                currentWindowManager?.removeViewImmediate(view)

            } catch (error: Exception) {
                error.printStackTrace()
            }
        }

        wallpaperView = null
        currentWindowManager = null
        windowContext = null

        activeMedia = WallpaperSearch.MediaFilter.ALL
        favoritesOnly = false
    }
}

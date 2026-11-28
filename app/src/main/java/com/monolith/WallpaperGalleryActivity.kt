package com.monolith

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WallpaperGalleryActivity : AppCompatActivity() {

    private lateinit var galleryRecycler: RecyclerView
    private lateinit var keywordRecycler: RecyclerView
    private lateinit var emptyState: View
    private lateinit var resultCount: TextView
    private lateinit var searchInput: EditText
    private lateinit var adapter: WallpaperGalleryAdapter
    private lateinit var keywordAdapter: WallpaperKeywordCardAdapter
    private val selectedKeywordIds = mutableSetOf<String>()

    // Wallpaper dimensions and multi-value selection
    private lateinit var dimensionContainer: android.widget.LinearLayout
    private lateinit var categoryContainer: android.widget.LinearLayout

    private val selectedCategoryIds = mutableSetOf<String>()
    private var selectedDimensionId: String? = null

    private var selectedTab = GalleryTab.EXPLORE

    private enum class GalleryTab {
        EXPLORE,
        FAVORITES,
        MY_WALLPAPERS
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wallpaper_gallery)

        galleryRecycler = findViewById(R.id.wallpaperGalleryRecycler)
        keywordRecycler = findViewById(R.id.keywordCardsRecycler)
        emptyState = findViewById(R.id.wallpaperEmptyState)
        resultCount = findViewById(R.id.tvWallpaperResultCount)
        searchInput = findViewById(R.id.etGallerySearch)

        dimensionContainer =
            findViewById(R.id.wallpaperDimensionContainer)

        categoryContainer =
            findViewById(R.id.wallpaperCategoryContainer)

        val gridColumns = if (
            resources.configuration.screenWidthDp >= 600
        ) 3 else 2

        galleryRecycler.layoutManager =
            GridLayoutManager(this, gridColumns)

        galleryRecycler.setHasFixedSize(true)

        adapter = WallpaperGalleryAdapter(
            onFavoriteClick = { item ->
                toggleFavorite(item.id)
            },
            onWallpaperClick = { item ->
                showWallpaperDetails(item)
            }
        )

        galleryRecycler.adapter = adapter

        setupKeywordCards()
        setupTabs()
        setupSearch()
        loadWallpapers()
    }

    private fun setupKeywordCards() {
        keywordRecycler.layoutManager =
            LinearLayoutManager(
                this,
                LinearLayoutManager.HORIZONTAL,
                false
            )

        keywordAdapter = WallpaperKeywordCardAdapter { keyword ->

            if (!selectedKeywordIds.add(keyword.id)) {
                selectedKeywordIds.remove(keyword.id)
            }

            keywordAdapter.setSelectedIds(
                selectedKeywordIds.toSet()
            )

            loadWallpapers()
        }

        keywordRecycler.adapter = keywordAdapter

        val preferredLanguageCode =
            resources.configuration.locales[0].toLanguageTag()

        lifecycleScope.launch {
            val keywords = withContext(Dispatchers.IO) {
                val repository =
                    WallpaperKeywordRepository(applicationContext)

                try {
                    repository.getAllKeywords(preferredLanguageCode)
                } finally {
                    repository.close()
                }
            }

            keywordAdapter.submitList(keywords)
            keywordAdapter.setSelectedIds(
                selectedKeywordIds.toSet()
            )
        }
    }

    private fun setupTabs() {
        val explore = findViewById<TextView>(R.id.tabExplore)
        val favorites = findViewById<TextView>(R.id.tabFavorites)
        val myWallpapers = findViewById<TextView>(R.id.tabMyWallpapers)

        explore.setOnClickListener {
            selectedTab = GalleryTab.EXPLORE
            updateTabAppearance()
            loadWallpapers()
        }

        favorites.setOnClickListener {
            selectedTab = GalleryTab.FAVORITES
            updateTabAppearance()
            loadWallpapers()
        }

        myWallpapers.setOnClickListener {
            selectedTab = GalleryTab.MY_WALLPAPERS
            updateTabAppearance()
            loadWallpapers()
        }

        updateTabAppearance()
    }

    private fun updateTabAppearance() {
        val tabs = listOf(
            R.id.tabExplore to GalleryTab.EXPLORE,
            R.id.tabFavorites to GalleryTab.FAVORITES,
            R.id.tabMyWallpapers to GalleryTab.MY_WALLPAPERS
        )

        tabs.forEach { (viewId, tab) ->
            val view = findViewById<TextView>(viewId)
            val selected = selectedTab == tab

            view.setBackgroundResource(
                if (selected) {
                    R.drawable.bg_circle_dark
                } else {
                    android.R.color.transparent
                }
            )

            view.setTextColor(
                if (selected) {
                    android.graphics.Color.rgb(197, 138, 255)
                } else {
                    android.graphics.Color.rgb(170, 170, 170)
                }
            )
        }
    }

    private fun setupSearch() {
        searchInput.addTextChangedListener(
            object : TextWatcher {

                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) = Unit

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {
                    loadWallpapers()
                }

                override fun afterTextChanged(
                    s: Editable?
                ) = Unit
            }
        )
    }

    private fun loadWallpapers() {
        val query = searchInput.text.toString()
        val keywordIds = selectedKeywordIds.toList()
        val currentTab = selectedTab
        val preferredLanguageCode =
            resources.configuration.locales[0].toLanguageTag()

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                val search = WallpaperSearch(applicationContext)

                try {
                    val options = WallpaperSearch.SearchOptions(
                        query = query,
                        preferredLanguageCode = preferredLanguageCode,
                        keywordIds = keywordIds,
                        favoriteOnly =
                            currentTab == GalleryTab.FAVORITES,
                        existingFilesOnly = true,
                        source = when (currentTab) {
                            GalleryTab.EXPLORE ->
                                WallpaperSearch.SourceFilter.ALL

                            GalleryTab.FAVORITES ->
                                WallpaperSearch.SourceFilter.ALL

                            GalleryTab.MY_WALLPAPERS ->
                                WallpaperSearch.SourceFilter.USER
                        }
                    )

                    search.search(options)
                } finally {
                    search.close()
                }
            }

            adapter.submitList(result.items)

            resultCount.text =
                "${result.items.size} wallpapers"

            if (result.items.isEmpty()) {
                galleryRecycler.visibility = View.GONE
                emptyState.visibility = View.VISIBLE
            } else {
                galleryRecycler.visibility = View.VISIBLE
                emptyState.visibility = View.GONE
            }
        }
    }

    private fun toggleFavorite(wallpaperId: String) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val favorites = WallpaperFavorites(applicationContext)

                try {
                    favorites.toggleFavorite(wallpaperId)
                } finally {
                    favorites.close()
                }
            }

            loadWallpapers()
        }
    }

    private fun showWallpaperDetails(
        item: WallpaperLibraryReader.WallpaperItem
    ) {
        android.app.AlertDialog.Builder(this)
            .setTitle(item.title)
            .setMessage(
                "Source: ${item.sourceType}\n" +
                "Type: ${item.mediaType}\n" +
                "Favorite: ${if (item.isFavorite) "Yes" else "No"}"
            )
            .setPositiveButton("OK", null)
            .show()
    }
}


package com.monolith

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class WallpaperGalleryActivity : AppCompatActivity() {

    private lateinit var galleryRecycler: RecyclerView
    private lateinit var emptyState: View
    private lateinit var resultCount: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wallpaper_gallery)

        galleryRecycler = findViewById(R.id.wallpaperGalleryRecycler)
        emptyState = findViewById(R.id.wallpaperEmptyState)
        resultCount = findViewById(R.id.tvWallpaperResultCount)

        val gridColumns = if (
            resources.configuration.screenWidthDp >= 600
        ) 3 else 2

        galleryRecycler.layoutManager =
            GridLayoutManager(this, gridColumns)

        galleryRecycler.setHasFixedSize(true)

        showEmptyGallery()
    }

    private fun showEmptyGallery() {
        galleryRecycler.visibility = View.GONE
        emptyState.visibility = View.VISIBLE
        resultCount.text = "0 wallpapers"
    }
}

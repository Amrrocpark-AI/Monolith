package com.monolith

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.io.File

class WallpaperGalleryAdapter(
    private val onFavoriteClick: (WallpaperLibraryReader.WallpaperItem) -> Unit,
    private val onWallpaperClick: (WallpaperLibraryReader.WallpaperItem) -> Unit
) : ListAdapter<
    WallpaperLibraryReader.WallpaperItem,
    WallpaperGalleryAdapter.WallpaperViewHolder
>(WallpaperDiffCallback()) {

    class WallpaperViewHolder(view: View) :
        RecyclerView.ViewHolder(view) {

        val thumbnail: ImageView =
            view.findViewById(R.id.ivWallpaperThumbnail)

        val favorite: ImageView =
            view.findViewById(R.id.ivWallpaperFavorite)

        val title: TextView =
            view.findViewById(R.id.tvWallpaperTitle)

        val source: TextView =
            view.findViewById(R.id.tvWallpaperSource)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): WallpaperViewHolder {

        val view = LayoutInflater.from(parent.context)
            .inflate(
                R.layout.item_wallpaper_gallery,
                parent,
                false
            )

        return WallpaperViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: WallpaperViewHolder,
        position: Int
    ) {
        val item = getItem(position)

        holder.title.text = item.title

        holder.source.text = when (item.sourceType) {
            "BUILT_IN" -> "BUILT-IN"
            else -> "MY WALLPAPER"
        }

        holder.favorite.setColorFilter(
            if (item.isFavorite) {
                Color.rgb(255, 77, 141)
            } else {
                Color.rgb(130, 125, 140)
            }
        )

        holder.favorite.contentDescription =
            if (item.isFavorite) {
                "Remove from favorites"
            } else {
                "Add to favorites"
            }

        val imageFile = item.thumbnailPath
            ?.let { File(it) }
            ?.takeIf { it.isFile }
            ?: File(item.filePath)

        Glide.with(holder.thumbnail)
            .load(imageFile)
            .centerCrop()
            .into(holder.thumbnail)

        holder.favorite.setOnClickListener {
            onFavoriteClick(item)
        }

        holder.itemView.setOnClickListener {
            onWallpaperClick(item)
        }
    }

    private class WallpaperDiffCallback :
        DiffUtil.ItemCallback<WallpaperLibraryReader.WallpaperItem>() {

        override fun areItemsTheSame(
            oldItem: WallpaperLibraryReader.WallpaperItem,
            newItem: WallpaperLibraryReader.WallpaperItem
        ): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(
            oldItem: WallpaperLibraryReader.WallpaperItem,
            newItem: WallpaperLibraryReader.WallpaperItem
        ): Boolean {
            return oldItem == newItem
        }
    }
}

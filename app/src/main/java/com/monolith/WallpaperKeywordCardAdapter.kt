package com.monolith

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.io.File

class WallpaperKeywordCardAdapter(
    private val onKeywordClick: (WallpaperKeywordRepository.Keyword) -> Unit
) : ListAdapter<
    WallpaperKeywordRepository.Keyword,
    WallpaperKeywordCardAdapter.KeywordViewHolder
>(KeywordDiffCallback()) {

    private val selectedIds = mutableSetOf<String>()

    class KeywordViewHolder(view: View) :
        RecyclerView.ViewHolder(view) {

        val card: CardView = view as CardView

        val image: ImageView =
            view.findViewById(R.id.ivKeywordImage)

        val name: TextView =
            view.findViewById(R.id.tvKeywordName)

        val count: TextView =
            view.findViewById(R.id.tvKeywordCount)

        val selected: TextView =
            view.findViewById(R.id.tvKeywordSelected)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): KeywordViewHolder {

        val view = LayoutInflater.from(parent.context)
            .inflate(
                R.layout.item_wallpaper_keyword_card,
                parent,
                false
            )

        return KeywordViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: KeywordViewHolder,
        position: Int
    ) {
        val item = getItem(position)
        val isSelected = item.id in selectedIds

        holder.name.text = item.keyword
        holder.count.text =
            "${item.wallpaperCount} wallpapers"

        holder.selected.visibility =
            if (isSelected) View.VISIBLE else View.GONE

        holder.card.setCardBackgroundColor(
            if (isSelected) {
                Color.rgb(65, 35, 90)
            } else {
                Color.rgb(23, 19, 31)
            }
        )

        val imageFile = item.previewPath
            ?.let { File(it) }
            ?.takeIf { it.isFile }

        Glide.with(holder.image)
            .load(imageFile)
            .centerCrop()
            .placeholder(android.R.color.darker_gray)
            .error(android.R.color.darker_gray)
            .into(holder.image)

        holder.itemView.setOnClickListener {
            onKeywordClick(item)
        }
    }

    fun setSelectedIds(ids: Set<String>) {
        if (selectedIds == ids) return

        selectedIds.clear()
        selectedIds.addAll(ids)

        notifyDataSetChanged()
    }

    private class KeywordDiffCallback :
        DiffUtil.ItemCallback<WallpaperKeywordRepository.Keyword>() {

        override fun areItemsTheSame(
            oldItem: WallpaperKeywordRepository.Keyword,
            newItem: WallpaperKeywordRepository.Keyword
        ): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(
            oldItem: WallpaperKeywordRepository.Keyword,
            newItem: WallpaperKeywordRepository.Keyword
        ): Boolean {
            return oldItem == newItem
        }
    }
}

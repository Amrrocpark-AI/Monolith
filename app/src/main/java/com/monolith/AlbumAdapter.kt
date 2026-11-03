package com.monolith

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

data class AlbumItem(
    val bucketId: String,
    val name: String,
    val coverUri: Uri?,
    val imageCount: Int,
    val isVault: Boolean = false
)

class AlbumAdapter(
    private val onAlbumClick: (AlbumItem) -> Unit
) : RecyclerView.Adapter<AlbumAdapter.AlbumViewHolder>() {

    private var albums: List<AlbumItem> = emptyList()

    fun submitList(newAlbums: List<AlbumItem>) {
        albums = newAlbums
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlbumViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_album, parent, false)
        return AlbumViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlbumViewHolder, position: Int) {
        holder.bind(albums[position], onAlbumClick)
    }

    override fun getItemCount(): Int = albums.size

    class AlbumViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivAlbumCover: ImageView = itemView.findViewById(R.id.ivAlbumCover)
        private val tvAlbumName: TextView = itemView.findViewById(R.id.tvAlbumName)
        private val tvImageCount: TextView = itemView.findViewById(R.id.tvImageCount)

        fun bind(album: AlbumItem, onClick: (AlbumItem) -> Unit) {
            tvAlbumName.text = album.name
            tvImageCount.text = "${album.imageCount} صورة"
            
            if (album.coverUri != null) {
                Glide.with(itemView.context)
                    .load(album.coverUri)
                    .centerCrop()
                    .into(ivAlbumCover)
            } else {
                ivAlbumCover.setImageResource(android.R.drawable.ic_menu_gallery)
            }

            itemView.setOnClickListener { onClick(album) }
        }
    }
}

package com.monolith

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy

class ImageAdapter(
    private val onImageClick: (Uri) -> Unit,
    private val onImageLongClick: (Uri) -> Unit
) : PagingDataAdapter<Uri, ImageAdapter.ImageViewHolder>(DIFF_CALLBACK) {

    private var isSelectionMode = false
    private var selectedUris: Set<Uri> = emptySet()

    fun setSelectionMode(mode: Boolean) {
        if (isSelectionMode == mode) return
        isSelectionMode = mode
        if (itemCount > 0) notifyItemRangeChanged(0, itemCount)
    }

    fun updateSelection(selected: Set<Uri>) {
        selectedUris = selected.toSet()
        if (itemCount > 0) notifyItemRangeChanged(0, itemCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_image, parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        holder.bind(
            uri = getItem(position),
            isSelectionMode = isSelectionMode,
            isSelected = getItem(position)?.let(selectedUris::contains) == true,
            onImageClick = onImageClick,
            onImageLongClick = onImageLongClick
        )
    }

    override fun onViewRecycled(holder: ImageViewHolder) {
        Glide.with(holder.itemView.context).clear(holder.ivGalleryImage)
        holder.itemView.setOnClickListener(null)
        holder.itemView.setOnLongClickListener(null)
        holder.cbSelectImage.setOnClickListener(null)
        super.onViewRecycled(holder)
    }

    class ImageViewHolder(itemView: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
        val ivGalleryImage: ImageView = itemView.findViewById(R.id.ivGalleryImage)
        val cbSelectImage: CheckBox = itemView.findViewById(R.id.cbSelectImage)

        fun bind(
            uri: Uri?,
            isSelectionMode: Boolean,
            isSelected: Boolean,
            onImageClick: (Uri) -> Unit,
            onImageLongClick: (Uri) -> Unit
        ) {
            if (uri == null) {
                Glide.with(itemView.context).clear(ivGalleryImage)
                ivGalleryImage.setImageResource(android.R.drawable.ic_menu_gallery)
                cbSelectImage.visibility = View.GONE
                itemView.setOnClickListener(null)
                itemView.setOnLongClickListener(null)
                cbSelectImage.setOnClickListener(null)
                return
            }

            Glide.with(itemView.context)
                .load(uri)
                .override(300, 300)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_report_image)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .centerCrop()
                .into(ivGalleryImage)

            cbSelectImage.setOnCheckedChangeListener(null)
            cbSelectImage.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
            cbSelectImage.isChecked = isSelected

            itemView.setOnClickListener { onImageClick(uri) }
            itemView.setOnLongClickListener {
                onImageLongClick(uri)
                true
            }
            cbSelectImage.setOnClickListener { onImageClick(uri) }
        }
    }

    private companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Uri>() {
            override fun areItemsTheSame(oldItem: Uri, newItem: Uri): Boolean =
                oldItem == newItem

            override fun areContentsTheSame(oldItem: Uri, newItem: Uri): Boolean =
                oldItem == newItem
        }
    }
}

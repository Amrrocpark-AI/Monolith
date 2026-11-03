package com.monolith

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy

class ImageAdapter(
    private val onImageClick: (Uri) -> Unit,
    private val onImageLongClick: (Uri) -> Unit
) : RecyclerView.Adapter<ImageAdapter.ImageViewHolder>() {

    private var images: List<Uri> = emptyList()
    private var isSelectionMode = false
    private val selectedUris = mutableSetOf<Uri>()

    fun submitList(newImages: List<Uri>) {
        images = newImages
        notifyDataSetChanged()
    }

    fun setSelectionMode(mode: Boolean) {
        isSelectionMode = mode
        if (!mode) selectedUris.clear()
        notifyDataSetChanged()
    }

    fun updateSelection(selected: Set<Uri>) {
        selectedUris.clear()
        selectedUris.addAll(selected)
        notifyDataSetChanged()
    }

    fun getSelectedUris(): Set<Uri> = selectedUris

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_image, parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        holder.bind(images[position], isSelectionMode, selectedUris.contains(images[position]))
    }

    override fun getItemCount(): Int = images.size

    inner class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivGalleryImage: ImageView = itemView.findViewById(R.id.ivGalleryImage)
        private val cbSelectImage: CheckBox = itemView.findViewById(R.id.cbSelectImage)

        fun bind(uri: Uri, isSelectionMode: Boolean, isSelected: Boolean) {
            // حل مشكلة المربعات الزرقاء: استخدام Glide مع DiskCacheStrategy و Placeholder
            Glide.with(itemView.context)
                .load(uri)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_report_image)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .centerCrop()
                .into(ivGalleryImage)

            cbSelectImage.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
            cbSelectImage.isChecked = isSelected

            itemView.setOnClickListener {
                if (isSelectionMode) {
                    if (selectedUris.contains(uri)) selectedUris.remove(uri) else selectedUris.add(uri)
                    if (selectedUris.isEmpty()) {
                        (itemView.context as? FileManagerActivity)?.clearSelection()
                    } else {
                        onImageLongClick(uri) // سنستخدم هذا كـ toggleSelection
                        notifyDataSetChanged()
                    }
                } else {
                    onImageClick(uri)
                }
            }

            itemView.setOnLongClickListener {
                onImageLongClick(uri)
                true
            }
        }
    }
}

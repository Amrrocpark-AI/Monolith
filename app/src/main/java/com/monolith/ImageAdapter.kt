package com.monolith

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
    private var selectedUris: Set<Uri> = emptySet()

    fun submitList(newImages: List<Uri>) {
        images = newImages
        notifyDataSetChanged()
    }

    fun setSelectionMode(mode: Boolean) {
        isSelectionMode = mode
        notifyDataSetChanged()
    }

    fun updateSelection(selected: Set<Uri>) {
        selectedUris = selected
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_image, parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        try {
            val uri = images[position]
            holder.bind(uri, isSelectionMode, selectedUris.contains(uri))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun getItemCount(): Int = images.size

    inner class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivGalleryImage: ImageView = itemView.findViewById(R.id.ivGalleryImage)
        private val cbSelectImage: CheckBox = itemView.findViewById(R.id.cbSelectImage)

        fun bind(uri: Uri, isSelectionMode: Boolean, isSelected: Boolean) {
            try {
                // تحميل الصورة بجودتها الكاملة وبدون تصغير (Glide سيتكفل بتحسين الأداء تلقائياً)
                Glide.with(itemView.context)
                    .load(uri)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_report_image)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .centerCrop()
                    .into(ivGalleryImage)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            cbSelectImage.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
            cbSelectImage.isChecked = isSelected

            // إصلاح جذري: نمرر الحدث للـ Activity فقط، والـ Activity هي التي تقرر ماذا تفعل
            itemView.setOnClickListener {
                onImageClick(uri) 
            }

            itemView.setOnLongClickListener {
                onImageLongClick(uri) 
                true
            }
        }
    }
}

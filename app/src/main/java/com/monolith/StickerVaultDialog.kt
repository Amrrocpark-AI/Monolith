package com.monolith

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.io.File

class StickerVaultDialog : BottomSheetDialogFragment() {

    private lateinit var adapter: VaultAdapter
    private var isEditMode = false
    private val deletedFiles = mutableListOf<File>()
    private val newNames = mutableMapOf<File, String>()

    var onStickerSelected: ((File) -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.layoutParams?.height = ViewGroup.LayoutParams.MATCH_PARENT
        }
        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_sticker_vault, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rvVault = view.findViewById<RecyclerView>(R.id.rvVault)
        val btnEdit = view.findViewById<ImageButton>(R.id.btnVaultEditMode)
        val btnClose = view.findViewById<ImageButton>(R.id.btnVaultClose)
        val layoutActions = view.findViewById<LinearLayout>(R.id.layoutVaultActions)
        val btnUndo = view.findViewById<ImageButton>(R.id.btnVaultUndo)
        val btnConfirm = view.findViewById<ImageButton>(R.id.btnVaultConfirm)
        val btnShare = view.findViewById<ImageButton>(R.id.btnVaultShare)

        adapter = VaultAdapter(requireContext(), VaultManager.getStickers(requireContext()), isEditMode,
            onSelect = { file ->
                if (!isEditMode) {
                    onStickerSelected?.invoke(file)
                    dismiss()
                }
            },
            onDelete = { file ->
                deletedFiles.add(file)
                adapter.removeItem(file)
            },
            onRename = { file, name ->
                newNames[file] = name
            },
            onSelectionChanged = {
                // يمكن استخدامها لتحديث الحالة إذا لزم الأمر
            }
        )

        rvVault.layoutManager = GridLayoutManager(requireContext(), 3)
        rvVault.adapter = adapter

        btnEdit.setOnClickListener {
            isEditMode = !isEditMode
            layoutActions.visibility = if (isEditMode) View.VISIBLE else View.GONE
            btnEdit.setColorFilter(if (isEditMode) Color.parseColor("#00D4FF") else Color.WHITE)
            adapter.isEditMode = isEditMode
            adapter.notifyDataSetChanged()
        }

        btnUndo.setOnClickListener {
            deletedFiles.clear()
            newNames.clear()
            isEditMode = false
            layoutActions.visibility = View.GONE
            btnEdit.setColorFilter(Color.WHITE)
            adapter.isEditMode = false
            adapter.updateData(VaultManager.getStickers(requireContext()))
        }

        btnConfirm.setOnClickListener {
            deletedFiles.forEach { VaultManager.deleteSticker(requireContext(), it) }
            newNames.forEach { (file, name) -> VaultManager.renameSticker(requireContext(), file.name, name) }
            
            deletedFiles.clear()
            newNames.clear()
            isEditMode = false
            layoutActions.visibility = View.GONE
            btnEdit.setColorFilter(Color.WHITE)
            adapter.isEditMode = false
            adapter.updateData(VaultManager.getStickers(requireContext()))
            Toast.makeText(requireContext(), "تم حفظ التعديلات", Toast.LENGTH_SHORT).show()
        }

        btnShare.setOnClickListener {
            shareSelectedImages()
        }

        btnClose.setOnClickListener { checkUnsavedAndDismiss() }
    }

    private fun shareSelectedImages() {
        val selectedFiles = adapter.getSelectedFiles()
        if (selectedFiles.isEmpty()) {
            Toast.makeText(requireContext(), "الرجاء تحديد صور للمشاركة", Toast.LENGTH_SHORT).show()
            return
        }

        val uris = ArrayList<Uri>()
        for (file in selectedFiles) {
            try {
                val uri = FileProvider.getUriForFile(requireContext(), "com.monolith.fileprovider", file)
                uris.add(uri)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (uris.isEmpty()) return

        val shareIntent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uris[0])
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/png"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }
        }
        
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(shareIntent, "مشاركة الصور عبر"))
    }

    override fun onStart() {
        super.onStart()
        dialog?.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                checkUnsavedAndDismiss()
                true
            } else {
                false
            }
        }
    }

    private fun checkUnsavedAndDismiss() {
        if (deletedFiles.isNotEmpty() || newNames.isNotEmpty()) {
            AlertDialog.Builder(requireContext())
                .setTitle("تعديلات غير محفوظة")
                .setMessage("هل تريد حفظ التعديلات قبل الخروج؟")
                .setPositiveButton("حفظ وخروج") { _, _ ->
                    deletedFiles.forEach { VaultManager.deleteSticker(requireContext(), it) }
                    newNames.forEach { (file, name) -> VaultManager.renameSticker(requireContext(), file.name, name) }
                    dismiss()
                }
                .setNegativeButton("خروج بدون حفظ") { _, _ -> dismiss() }
                .show()
        } else {
            dismiss()
        }
    }
}

class VaultAdapter(
    private val context: Context,
    private var files: List<File>,
    var isEditMode: Boolean,
    private val onSelect: (File) -> Unit,
    private val onDelete: (File) -> Unit,
    private val onRename: (File, String) -> Unit,
    private val onSelectionChanged: () -> Unit
) : RecyclerView.Adapter<VaultAdapter.VaultViewHolder>() {

    private val selectedFiles = mutableSetOf<File>()

    private fun createCheckerboardDrawable(size: Int = 32): Drawable {
        val bitmap = Bitmap.createBitmap(size * 2, size * 2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        paint.color = Color.parseColor("#CCCCCC")
        canvas.drawRect(0f, 0f, size * 2f, size * 2f, paint)
        paint.color = Color.WHITE
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
        canvas.drawRect(size.toFloat(), size.toFloat(), size * 2f, size * 2f, paint)
        return BitmapDrawable(context.resources, bitmap)
    }

    fun updateData(newFiles: List<File>) {
        files = newFiles
        selectedFiles.clear()
        notifyDataSetChanged()
    }

    fun removeItem(file: File) {
        val newList = files.toMutableList()
        newList.remove(file)
        files = newList
        selectedFiles.remove(file)
        notifyDataSetChanged()
    }

    fun getSelectedFiles(): List<File> = selectedFiles.toList()

    inner class VaultViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val img: ImageView = view.findViewById(R.id.imgVaultItem)
        val tvName: TextView = view.findViewById(R.id.tvVaultName)
        val btnDel: ImageButton = view.findViewById(R.id.btnVaultDelete)
        val btnRename: ImageButton = view.findViewById(R.id.btnVaultRename)
        val imgCheckmark: ImageView = view.findViewById(R.id.imgCheckmark)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VaultViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_vault_image, parent, false)
        return VaultViewHolder(view)
    }

    override fun onBindViewHolder(holder: VaultViewHolder, position: Int) {
        val file = files[position]
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        
        holder.img.setImageBitmap(bitmap)
        holder.img.setBackground(createCheckerboardDrawable())
        
        val rawName = VaultManager.getStickerName(context, file.name)
        holder.tvName.text = rawName

        // إظهار/إخفاء أزرار التعديل والحذف
        holder.btnDel.visibility = if (isEditMode) View.VISIBLE else View.GONE
        holder.btnRename.visibility = if (isEditMode) View.VISIBLE else View.GONE
        
        // منطق التحديد (Checkmark)
        if (selectedFiles.contains(file)) {
            holder.imgCheckmark.setImageResource(R.drawable.ic_vault_check_circle)
        } else {
            holder.imgCheckmark.setImageResource(R.drawable.ic_vault_check_unselected)
        }
        
        holder.imgCheckmark.visibility = View.VISIBLE

        holder.imgCheckmark.setOnClickListener {
            if (selectedFiles.contains(file)) {
                selectedFiles.remove(file)
            } else {
                selectedFiles.add(file)
            }
            notifyItemChanged(position)
            onSelectionChanged()
        }

        holder.btnDel.setOnClickListener { onDelete(file) }
        
        holder.btnRename.setOnClickListener {
            val input = EditText(context)
            input.setText(holder.tvName.text)
            AlertDialog.Builder(context)
                .setTitle("اسم المحفوظة")
                .setView(input)
                .setPositiveButton("حفظ") { _, _ ->
                    val newName = input.text.toString()
                    holder.tvName.text = newName
                    onRename(file, newName)
                }
                .setNegativeButton("إلغاء", null)
                .show()
        }

        holder.itemView.setOnClickListener {
            if (!isEditMode) onSelect(file)
        }
    }

    override fun getItemCount() = files.size
}

package com.monolith

import android.app.Dialog
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
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
        val btnUndo = view.findViewById<Button>(R.id.btnVaultUndo)
        val btnConfirm = view.findViewById<Button>(R.id.btnVaultConfirm)

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
            }
        )

        rvVault.layoutManager = GridLayoutManager(requireContext(), 3)
        rvVault.adapter = adapter

        // تفعيل وضع التعديل
        btnEdit.setOnClickListener {
            isEditMode = !isEditMode
            layoutActions.visibility = if (isEditMode) View.VISIBLE else View.GONE
            btnEdit.setColorFilter(if (isEditMode) Color.GREEN else Color.WHITE)
            adapter.isEditMode = isEditMode
            adapter.notifyDataSetChanged()
        }

        // زر التراجع
        btnUndo.setOnClickListener {
            deletedFiles.clear()
            newNames.clear()
            isEditMode = false
            layoutActions.visibility = View.GONE
            btnEdit.setColorFilter(Color.WHITE)
            adapter.isEditMode = false
            adapter.updateData(VaultManager.getStickers(requireContext()))
        }

        // زر تأكيد التعديلات
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

        // زر الإغلاق
        btnClose.setOnClickListener { checkUnsavedAndDismiss() }
    }

    // [إصلاح] اعتراض زر الرجوع في الهاتف بطريقة آمنة
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

// محول الشبكة (Adapter)
class VaultAdapter(
    private val context: Context,
    private var files: List<File>,
    var isEditMode: Boolean,
    private val onSelect: (File) -> Unit,
    private val onDelete: (File) -> Unit,
    private val onRename: (File, String) -> Unit
) : RecyclerView.Adapter<VaultAdapter.VaultViewHolder>() {

    fun updateData(newFiles: List<File>) {
        files = newFiles
        notifyDataSetChanged()
    }

    fun removeItem(file: File) {
        val newList = files.toMutableList()
        newList.remove(file)
        files = newList
        notifyDataSetChanged()
    }

    inner class VaultViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val img: ImageView = view.findViewById(R.id.imgVaultItem)
        val tvName: TextView = view.findViewById(R.id.tvVaultName)
        val btnDel: ImageButton = view.findViewById(R.id.btnVaultDelete)
        val btnRename: ImageButton = view.findViewById(R.id.btnVaultRename)
        val checkBox: CheckBox = view.findViewById(R.id.cbVaultSelect)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VaultViewHolder {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(8, 8, 8, 8)
        }
        
        val img = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(200, 200)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.parseColor("#333333"))
        }
        
        val tvName = TextView(context).apply {
            textSize = 12f
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
            maxLines = 1
            setPadding(0, 4, 0, 0)
        }

        val btnDel = ImageButton(context).apply {
            setImageResource(android.R.drawable.ic_menu_delete)
            setBackgroundColor(Color.TRANSPARENT)
            visibility = View.GONE
        }
        
        val btnRename = ImageButton(context).apply {
            setImageResource(android.R.drawable.ic_menu_edit)
            setBackgroundColor(Color.TRANSPARENT)
            visibility = View.GONE
        }

        val checkBox = CheckBox(context).apply {
            visibility = View.GONE
        }

        layout.addView(img)
        layout.addView(tvName)
        
        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        actions.addView(btnDel)
        actions.addView(btnRename)
        layout.addView(actions)
        layout.addView(checkBox)

        img.id = R.id.imgVaultItem
        tvName.id = R.id.tvVaultName
        btnDel.id = R.id.btnVaultDelete
        btnRename.id = R.id.btnVaultRename
        checkBox.id = R.id.cbVaultSelect

        return VaultViewHolder(layout)
    }

    override fun onBindViewHolder(holder: VaultViewHolder, position: Int) {
        val file = files[position]
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        holder.img.setImageBitmap(bitmap)
        holder.tvName.text = VaultManager.getStickerName(context, file.name)

        holder.btnDel.visibility = if (isEditMode) View.VISIBLE else View.GONE
        holder.btnRename.visibility = if (isEditMode) View.VISIBLE else View.GONE
        
        holder.btnDel.setOnClickListener { onDelete(file) }
        holder.btnRename.setOnClickListener {
            val input = EditText(context)
            input.setText(holder.tvName.text)
            AlertDialog.Builder(context)
                .setTitle("اسم الشعار")
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

package com.monolith

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.CycleInterpolator
import android.view.animation.TranslateAnimation
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.EncryptionMethod
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.Collections

class FileManagerActivity : AppCompatActivity() {

    private lateinit var rvFiles: RecyclerView
    private lateinit var fileAdapter: FileAdapter
    private lateinit var etSearchFiles: EditText
    private lateinit var tabLayoutFiles: TabLayout
    private lateinit var tvPathTitle: TextView
    
    // شريط التحديد
    private lateinit var topBarFileManager: LinearLayout
    private lateinit var topBarSelection: LinearLayout
    private lateinit var tvSelectionCount: TextView
    
    private var currentDirectory: File = Environment.getExternalStorageDirectory()
    private var allFiles: MutableList<File> = mutableListOf()
    private var galleryImages: MutableList<File> = mutableListOf()
    private var displayedFiles: MutableList<File> = mutableListOf()
    private var isGalleryMode = false
    private var isSortAscending = true
    private var sortType = "NAME" // NAME, SIZE, DATE
    private var showHiddenFiles = false

    // حالة التحديد المتعدد
    private var isSelectionMode = false
    private val selectedFiles = mutableSetOf<File>()

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Environment.isExternalStorageManager()) {
            loadDirectory(currentDirectory)
        } else {
            Toast.makeText(this, "نحتاج إذن الوصول للملفات.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_manager)

        rvFiles = findViewById(R.id.rvFiles)
        etSearchFiles = findViewById(R.id.etSearchFiles)
        tabLayoutFiles = findViewById(R.id.tabLayoutFiles)
        tvPathTitle = findViewById(R.id.tvPathTitle)
        topBarFileManager = findViewById(R.id.topBarFileManager)
        topBarSelection = findViewById(R.id.topBarSelection)
        tvSelectionCount = findViewById(R.id.tvSelectionCount)

        val btnBack = findViewById<ImageButton>(R.id.btnBackFileManager)
        val btnSort = findViewById<ImageButton>(R.id.btnSort)
        val btnCancelSelection = findViewById<ImageButton>(R.id.btnCancelSelection)
        val btnDeleteSelected = findViewById<ImageButton>(R.id.btnDeleteSelected)
        val btnZipSelected = findViewById<ImageButton>(R.id.btnZipSelected)
        val fabCreateFolder = findViewById<FloatingActionButton>(R.id.fabCreateFolder)

        fileAdapter = FileAdapter(
            onFileClick = { file -> handleFileClick(file) },
            onFileLongClick = { file -> handleFileLongClick(file) }
        )
        
        rvFiles.layoutManager = LinearLayoutManager(this)
        rvFiles.adapter = fileAdapter

        tabLayoutFiles.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> { isGalleryMode = false; rvFiles.layoutManager = LinearLayoutManager(this@FileManagerActivity); applyFilterAndSort() }
                    1 -> { isGalleryMode = true; rvFiles.layoutManager = GridLayoutManager(this@FileManagerActivity, 3); loadRealGallery() }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        etSearchFiles.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { applyFilterAndSort() }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnBack.setOnClickListener { handleBackPress() }
        btnCancelSelection.setOnClickListener { clearSelection() }
        
        // الفرز
        btnSort.setOnClickListener { showSortDialog() }

        // الحذف الجماعي
        btnDeleteSelected.setOnClickListener { deleteSelectedFiles() }
        
        // الضغط الجماعي (ZIP)
        btnZipSelected.setOnClickListener { 
            if (selectedFiles.isNotEmpty()) {
                showCreateZipDialog(selectedFiles.toList()) 
            }
        }

        fabCreateFolder.setOnClickListener { showCreateFolderDialog() }

        checkAndRequestPermission()
    }

    private fun handleBackPress() {
        if (isSelectionMode) {
            clearSelection()
        } else {
            val parent = currentDirectory.parentFile
            if (parent != null) loadDirectory(parent) else finish()
        }
    }

    override fun onBackPressed() {
        handleBackPress()
    }

    // ==========================================
    // إدارة التحديد المتعدد
    // ==========================================
    private fun handleFileClick(file: File) {
        if (isSelectionMode) {
            toggleSelection(file)
        } else {
            when {
                file.isDirectory -> loadDirectory(file)
                file.name.endsWith(".apk", ignoreCase = true) -> installApk(file)
                file.name.endsWith(".zip", ignoreCase = true) -> handleZipFile(file)
                else -> Toast.makeText(this, "لا يمكن فتح هذا الملف.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleFileLongClick(file: File) {
        if (!isSelectionMode) {
            isSelectionMode = true
            topBarFileManager.visibility = View.GONE
            topBarSelection.visibility = View.VISIBLE
        }
        toggleSelection(file)
    }

    private fun toggleSelection(file: File) {
        if (selectedFiles.contains(file)) selectedFiles.remove(file) else selectedFiles.add(file)
        
        if (selectedFiles.isEmpty()) {
            clearSelection()
        } else {
            tvSelectionCount.text = "تم تحديد ${selectedFiles.size} عنصر"
            fileAdapter.updateSelection(selectedFiles)
        }
    }

    private fun clearSelection() {
        isSelectionMode = false
        selectedFiles.clear()
        topBarFileManager.visibility = View.VISIBLE
        topBarSelection.visibility = View.GONE
        fileAdapter.updateSelection(selectedFiles)
    }

    private fun deleteSelectedFiles() {
        AlertDialog.Builder(this)
            .setTitle("حذف العناصر")
            .setMessage("هل أنت متأكد من حذف ${selectedFiles.size} عنصر؟")
            .setPositiveButton("حذف") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    selectedFiles.forEach { 
                        if (it.isDirectory) it.deleteRecursively() else it.delete() 
                    }
                    withContext(Dispatchers.Main) {
                        clearSelection()
                        loadDirectory(currentDirectory)
                        Toast.makeText(this@FileManagerActivity, "تم الحذف", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    // ==========================================
    // الفرز
    // ==========================================
    private fun showSortDialog() {
        val options = arrayOf("الاسم", "الحجم", "التاريخ", "عرض الملفات المخفية")
        AlertDialog.Builder(this)
            .setTitle("ترتيب حسب")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> { sortType = "NAME"; isSortAscending = !isSortAscending }
                    1 -> { sortType = "SIZE"; isSortAscending = !isSortAscending }
                    2 -> { sortType = "DATE"; isSortAscending = !isSortAscending }
                    3 -> { showHiddenFiles = !showHiddenFiles }
                }
                applyFilterAndSort()
            }
            .show()
    }

    private fun applyFilterAndSort() {
        val query = etSearchFiles.text.toString()
        val sourceList = if (isGalleryMode) galleryImages else allFiles
        
        var filtered = if (query.isEmpty()) sourceList.toList() else sourceList.filter { it.name.contains(query, ignoreCase = true) }
        
        if (!isGalleryMode && !showHiddenFiles) {
            filtered = filtered.filter { !it.name.startsWith(".") }
        }

        val comparator = when (sortType) {
            "SIZE" -> compareBy<File> { it.length() }
            "DATE" -> compareBy { it.lastModified() }
            else -> compareBy { it.name.lowercase() }
        }
        
        displayedFiles = if (isSortAscending) filtered.sortedWith(comparator) else filtered.sortedWith(comparator).reversed()
        
        // وضع المجلدات أولاً في تبويب الملفات
        if (!isGalleryMode) {
            displayedFiles.sortBy { !it.isDirectory }
        }

        fileAdapter.submitList(displayedFiles, isGalleryMode)
        tvPathTitle.text = if (isGalleryMode) "معرض الصور" else currentDirectory.name
    }

    // ==========================================
    // العمليات الأساسية (تثبيت، إنشاء، معرض)
    // ==========================================
    private fun installApk(apkFile: File) {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(FileProvider.getUriForFile(this, "${packageName}.fileprovider", apkFile), "application/vnd.android.package-archive")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "فشل فتح المثبت: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showCreateFolderDialog() {
        val input = EditText(this).apply { hint = "اسم المجلد" }
        AlertDialog.Builder(this)
            .setTitle("إنشاء مجلد جديد").setView(input)
            .setPositiveButton("إنشاء") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val newFolder = File(currentDirectory, name)
                    if (newFolder.mkdirs()) { loadDirectory(currentDirectory); Toast.makeText(this, "تم الإنشاء", Toast.LENGTH_SHORT).show() }
                    else { Toast.makeText(this, "فشل (موجود مسبقاً)", Toast.LENGTH_SHORT).show() }
                }
            }.setNegativeButton("إلغاء", null).show()
    }

    private fun loadRealGallery() {
        lifecycleScope.launch {
            val images = withContext(Dispatchers.IO) {
                val list = mutableListOf<File>()
                val cursor = contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media.DATA), null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC")
                cursor?.use { while (it.moveToNext()) { val f = File(it.getString(it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))); if (f.exists()) list.add(f) } }
                list
            }
            withContext(Dispatchers.Main) {
                galleryImages = images
                applyFilterAndSort()
            }
        }
    }

    // ==========================================
    // محرك ZIP (الضغط والفك)
    // ==========================================
    private fun handleZipFile(zipFile: File) {
        try {
            if (ZipFile(zipFile).isEncrypted) showExtractPasswordDialog(zipFile) else executeExtraction(zipFile, null)
        } catch (e: Exception) { Toast.makeText(this, "أرشيف غير صالح", Toast.LENGTH_SHORT).show() }
    }

    private fun showExtractPasswordDialog(zipFile: File) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_extract_zip, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).setCancelable(false).create()
        val tilPassword = dialogView.findViewById<TextInputLayout>(R.id.tilExtractPassword)
        val etPassword = dialogView.findViewById<TextInputEditText>(R.id.etExtractPassword)
        val tvError = dialogView.findViewById<TextView>(R.id.tvErrorText)

        dialogView.findViewById<MaterialButton>(R.id.btnCancelExtract).setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<MaterialButton>(R.id.btnExtractZip).setOnClickListener {
            val password = etPassword.text.toString()
            if (password.isEmpty()) { tvError.text = "أدخل كلمة المرور"; tvError.visibility = View.VISIBLE; return@setOnClickListener }
            tvError.visibility = View.GONE
            if (executeExtraction(zipFile, password)) dialog.dismiss() 
            else { tvError.text = "كلمة المرور خاطئة"; tvError.visibility = View.VISIBLE; tilPassword.boxStrokeColor = android.graphics.Color.RED }
        }
        dialog.show()
    }

    private fun executeExtraction(zipFile: File, password: String?): Boolean {
        var success = false
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val dest = File(zipFile.parentFile, zipFile.nameWithoutExtension)
                    if (!dest.exists()) dest.mkdirs()
                    val zip = ZipFile(zipFile)
                    if (password != null) zip.setPassword(password.toCharArray())
                    zip.extractAll(dest.absolutePath)
                    success = true
                }
                if (success) { Toast.makeText(this@FileManagerActivity, "تم فك الضغط!", Toast.LENGTH_SHORT).show(); loadDirectory(currentDirectory) }
            } catch (e: Exception) { success = false }
        }
        return success
    }

    private fun showCreateZipDialog(filesToZip: List<File>) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_zip, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        val etZipName = dialogView.findViewById<EditText>(R.id.etZipName)
        val etZipPassword = dialogView.findViewById<TextInputEditText>(R.id.etZipPassword)
        
        etZipName.setText(if (filesToZip.size == 1) filesToZip[0].nameWithoutExtension else "Archive_${System.currentTimeMillis()}")
        
        dialogView.findViewById<MaterialButton>(R.id.btnCancelZip).setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<MaterialButton>(R.id.btnCreateZip).setOnClickListener {
            val name = etZipName.text.toString().trim()
            if (name.isEmpty()) return@setOnClickListener
            dialog.dismiss()
            executeZipCreation(filesToZip, name, etZipPassword.text.toString())
        }
        dialog.show()
    }

    private fun executeZipCreation(files: List<File>, zipName: String, password: String) {
        Toast.makeText(this, "جاري الضغط...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val dest = File(currentDirectory, "$zipName.zip")
                    val params = ZipParameters().apply {
                        if (password.isNotEmpty()) {
                            isEncryptFiles = true; encryptionMethod = EncryptionMethod.AES; aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
                        }
                    }
                    val zip = if (password.isNotEmpty()) ZipFile(dest, password.toCharArray()) else ZipFile(dest)
                    files.forEach { if (it.isDirectory) zip.addFolder(it, params) else zip.addFile(it, params) }
                }
                withContext(Dispatchers.Main) { Toast.makeText(this@FileManagerActivity, "تم الضغط!", Toast.LENGTH_SHORT).show(); loadDirectory(currentDirectory) }
            } catch (e: Exception) { withContext(Dispatchers.Main) { Toast.makeText(this@FileManagerActivity, "فشل: ${e.message}", Toast.LENGTH_LONG).show() } }
        }
    }

    // ==========================================
    // تحميل المجلدات والصلاحيات
    // ==========================================
    private fun checkAndRequestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) loadDirectory(currentDirectory)
            else {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    addCategory("android.intent.category.DEFAULT")
                    data = Uri.parse("package:$packageName")
                }
                requestPermissionLauncher.launch(intent)
            }
        } else loadDirectory(currentDirectory)
    }

    private fun loadDirectory(dir: File) {
        currentDirectory = dir
        lifecycleScope.launch {
            val files = withContext(Dispatchers.IO) { dir.listFiles()?.toMutableList() ?: mutableListOf() }
            withContext(Dispatchers.Main) {
                allFiles = files
                applyFilterAndSort()
            }
        }
    }
}

// ==========================================
// محول القائمة (المعدل لدعم التحديد)
// ==========================================
class FileAdapter(
    private val onFileClick: (File) -> Unit,
    private val onFileLongClick: (File) -> Unit
) : RecyclerView.Adapter<FileAdapter.FileViewHolder>() {
    
    private var files: List<File> = emptyList()
    private var isGalleryMode = false
    private var selectedFiles: Set<File> = emptySet()

    fun submitList(newFiles: List<File>, galleryMode: Boolean) { 
        files = newFiles; isGalleryMode = galleryMode; notifyDataSetChanged() 
    }
    fun updateSelection(selected: Set<File>) {
        selectedFiles = selected; notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(files[position], isGalleryMode, selectedFiles.contains(files[position]), onFileClick, onFileLongClick)
    }

    override fun getItemCount(): Int = files.size

    class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivFileIcon: ImageView = itemView.findViewById(R.id.ivFileIcon)
        private val tvFileName: TextView = itemView.findViewById(R.id.tvFileName)
        private val tvFileDetails: TextView = itemView.findViewById(R.id.tvFileDetails)
        private val cbSelectFile: CheckBox = itemView.findViewById(R.id.cbSelectFile)

        fun bind(file: File, isGallery: Boolean, isSelected: Boolean, onClick: (File) -> Unit, onLongClick: (File) -> Unit) {
            tvFileName.text = file.name
            val dateString = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(file.lastModified()))
            
            cbSelectFile.visibility = if (isSelected || isGallery) View.GONE else View.GONE // سيتم إظهاره برمجياً عند الحاجة لاحقاً
            cbSelectFile.isChecked = isSelected

            if (file.isDirectory) {
                ivFileIcon.setImageResource(android.R.drawable.ic_menu_set_as)
                ivFileIcon.setColorFilter(android.graphics.Color.parseColor("#00D4FF"))
                tvFileDetails.text = "مجلد • $dateString"
            } else {
                val sizeInKb = file.length() / 1024
                val sizeString = if (sizeInKb > 1024) "${sizeInKb / 1024} MB" else "$sizeInKb KB"
                val isImage = file.extension.lowercase() in listOf("jpg", "jpeg", "png", "gif", "webp")
                
                if (isImage && isGallery) {
                    Glide.with(itemView.context).load(file).centerCrop().into(ivFileIcon)
                    tvFileDetails.visibility = View.GONE
                } else {
                    tvFileDetails.visibility = View.VISIBLE
                    ivFileIcon.setColorFilter(android.graphics.Color.parseColor("#AAAAAA"))
                    if (isImage) ivFileIcon.setImageResource(android.R.drawable.ic_menu_gallery)
                    else if (file.name.endsWith(".zip", true)) { ivFileIcon.setImageResource(android.R.drawable.ic_menu_save); ivFileIcon.setColorFilter(android.graphics.Color.parseColor("#FFAA00")) }
                    else if (file.name.endsWith(".apk", true)) { ivFileIcon.setImageResource(android.R.drawable.ic_menu_manage); ivFileIcon.setColorFilter(android.graphics.Color.parseColor("#00D4FF")) }
                    else ivFileIcon.setImageResource(android.R.drawable.ic_menu_agenda)
                    tvFileDetails.text = "$sizeString • $dateString"
                }
            }
            itemView.setOnClickListener { onClick(file) }
            itemView.setOnLongClickListener { onLongClick(file); true }
        }
    }
}

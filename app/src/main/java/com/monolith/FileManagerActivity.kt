package com.monolith

import android.app.AlertDialog
import android.content.ContentUris
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
import android.webkit.MimeTypeMap
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.EncryptionMethod
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class FileManagerActivity : AppCompatActivity() {

    private lateinit var rvFiles: RecyclerView
    private lateinit var fileAdapter: FileAdapter
    private lateinit var imageAdapter: ImageAdapter
    private lateinit var albumAdapter: AlbumAdapter
    private lateinit var etSearchFiles: EditText
    private lateinit var tabLayoutFiles: TabLayout
    private lateinit var tvPathTitle: TextView
    
    // شريط التحديد
    private lateinit var topBarFileManager: LinearLayout
    private lateinit var topBarSelection: LinearLayout
    private lateinit var tvSelectionCount: TextView
    private lateinit var bottomSelectionBar: LinearLayout
    private lateinit var btnSelectAll: ImageButton
    
    // أزرار الشريط السفلي
    private lateinit var actionDelete: LinearLayout
    private lateinit var actionShare: LinearLayout
    private lateinit var actionMove: LinearLayout
    private lateinit var actionCopy: LinearLayout
    private lateinit var actionAddAlbum: LinearLayout
    private lateinit var fabCreateFolder: FloatingActionButton
    
    private var currentDirectory: File = Environment.getExternalStorageDirectory()
    private var allFiles: MutableList<File> = mutableListOf()
    private var galleryUris: MutableList<Uri> = mutableListOf()
    
    private var isGalleryMode = false
    private var isAlbumMode = false
    private var currentAlbumId: String? = null
    private var isSortAscending = true
    private var sortType = "NAME"
    private var showHiddenFiles = false

    private var isSelectionMode = false
    private val selectedFiles = mutableSetOf<File>()
    private val selectedImages = mutableSetOf<Uri>()

    private lateinit var virtualAlbumDao: VirtualAlbumDao
    private lateinit var sharedPreferences: android.content.SharedPreferences

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
        
        virtualAlbumDao = AppDatabase.getDatabase(applicationContext).virtualAlbumDao()
        sharedPreferences = getSharedPreferences("Settings", MODE_PRIVATE)

        rvFiles = findViewById(R.id.rvFiles)
        etSearchFiles = findViewById(R.id.etSearchFiles)
        tabLayoutFiles = findViewById(R.id.tabLayoutFiles)
        tvPathTitle = findViewById(R.id.tvPathTitle)
        topBarFileManager = findViewById(R.id.topBarFileManager)
        topBarSelection = findViewById(R.id.topBarSelection)
        tvSelectionCount = findViewById(R.id.tvSelectionCount)
        bottomSelectionBar = findViewById(R.id.bottomSelectionBar)
        btnSelectAll = findViewById(R.id.btnSelectAll)
        fabCreateFolder = findViewById(R.id.fabCreateFolder)

        actionDelete = findViewById(R.id.actionDelete)
        actionShare = findViewById(R.id.actionShare)
        actionMove = findViewById(R.id.actionMove)
        actionCopy = findViewById(R.id.actionCopy)
        actionAddAlbum = findViewById(R.id.actionAddAlbum)

        val btnBack = findViewById<ImageButton>(R.id.btnBackFileManager)
        val btnSort = findViewById<ImageButton>(R.id.btnSort)
        val btnCancelSelection = findViewById<ImageButton>(R.id.btnCancelSelection)
        val btnZipSelected = findViewById<ImageButton>(R.id.btnZipSelected)

        fileAdapter = FileAdapter({ file -> handleFileClick(file) }, { file -> handleFileLongClick(file) })
        imageAdapter = ImageAdapter({ uri -> handleImageClick(uri) }, { uri -> handleImageLongClick(uri) })
        albumAdapter = AlbumAdapter { album -> handleAlbumClick(album) }

        rvFiles.layoutManager = LinearLayoutManager(this)
        rvFiles.adapter = fileAdapter

        // تطبيق الألوان المخصصة على الشريط السفلي
        applyCustomColors()

        tabLayoutFiles.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                clearSelection()
                currentAlbumId = null
                when (tab?.position) {
                    0 -> { isGalleryMode = false; isAlbumMode = false; rvFiles.layoutManager = LinearLayoutManager(this@FileManagerActivity); rvFiles.adapter = fileAdapter; fabCreateFolder.visibility = View.VISIBLE; applyFilterAndSort() }
                    1 -> { isGalleryMode = true; isAlbumMode = false; rvFiles.layoutManager = GridLayoutManager(this@FileManagerActivity, 3); rvFiles.adapter = imageAdapter; fabCreateFolder.visibility = View.GONE; loadRealGallery() }
                    2 -> { isGalleryMode = false; isAlbumMode = true; rvFiles.layoutManager = GridLayoutManager(this@FileManagerActivity, 2); rvFiles.adapter = albumAdapter; fabCreateFolder.visibility = View.VISIBLE; loadAlbums() }
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
        btnSort.setOnClickListener { showSortDialog() }
        
        btnZipSelected.setOnClickListener { 
            if (selectedFiles.isNotEmpty()) showCreateZipDialog(selectedFiles.toList()) 
        }

        // أزرار الشريط السفلي
        actionDelete.setOnClickListener { deleteSelectedFiles() }
        actionShare.setOnClickListener { shareSelectedFiles() }
        actionMove.setOnClickListener { Toast.makeText(this, "ميزة النقل قيد التطوير...", Toast.LENGTH_SHORT).show() }
        actionCopy.setOnClickListener { Toast.makeText(this, "ميزة النسخ قيد التطوير...", Toast.LENGTH_SHORT).show() }
        actionAddAlbum.setOnClickListener { showAddToAlbumDialog() }

        // زر تحديد الكل
        btnSelectAll.setOnClickListener {
            if (isGalleryMode) {
                selectedImages.addAll(galleryUris)
                imageAdapter.updateSelection(selectedImages)
            } else {
                selectedFiles.addAll(allFiles)
                fileAdapter.updateSelection(selectedFiles)
            }
            updateSelectionCount()
        }

        fabCreateFolder.setOnClickListener {
            if (isAlbumMode) showCreateVirtualAlbumDialog()
            else showCreateFolderDialog()
        }

        checkAndRequestPermission()
    }

    private fun applyCustomColors() {
        val colorStr = sharedPreferences.getString("selection_bar_color", "BLUE")
        val colorRes = when (colorStr) {
            "PINK" -> android.graphics.Color.parseColor("#FF00FF")
            "GREEN" -> android.graphics.Color.parseColor("#00FF00")
            else -> android.graphics.Color.parseColor("#00D4FF")
        }
        
        // تغيير لون أيقونات الشريط السفلي
        (actionShare.getChildAt(0) as ImageView).setColorFilter(colorRes)
        (actionAddAlbum.getChildAt(0) as ImageView).setColorFilter(colorRes)
        
        // يمكنك تغيير لون الخلفية أيضاً إذا أردت
        // bottomSelectionBar.background.setTint(colorRes)
    }

    private fun handleBackPress() {
        if (isSelectionMode) {
            clearSelection()
        } else if (currentAlbumId != null) {
            currentAlbumId = null
            loadAlbums()
        } else if (isAlbumMode) {
            tabLayoutFiles.getTabAt(0)?.select()
        } else {
            val parent = currentDirectory.parentFile
            if (parent != null) loadDirectory(parent) else finish()
        }
    }

    override fun onBackPressed() { handleBackPress() }

    // ==========================================
    // إدارة التحديد
    // ==========================================
    private fun enterSelectionMode() {
        isSelectionMode = true
        topBarFileManager.visibility = View.GONE
        topBarSelection.visibility = View.VISIBLE
        bottomSelectionBar.visibility = View.VISIBLE // إظهار الشريط السفلي
        fileAdapter.setSelectionMode(true)
        imageAdapter.setSelectionMode(true)
    }

    fun clearSelection() {
        isSelectionMode = false
        selectedFiles.clear()
        selectedImages.clear()
        topBarFileManager.visibility = View.VISIBLE
        topBarSelection.visibility = View.GONE
        bottomSelectionBar.visibility = View.GONE // إخفاء الشريط السفلي
        fileAdapter.setSelectionMode(false)
        imageAdapter.setSelectionMode(false)
        updateSelectionCount()
    }

    private fun handleFileClick(file: File) {
        if (isSelectionMode) toggleFileSelection(file) else { /* ... (الفتح كالسابق) ... */ }
    }

    private fun handleImageClick(uri: Uri) {
        if (isSelectionMode) toggleImageSelection(uri) else { /* ... (الفتح كالسابق) ... */ }
    }

    private fun handleFileLongClick(file: File) {
        if (!isSelectionMode) enterSelectionMode()
        toggleFileSelection(file)
    }

    private fun handleImageLongClick(uri: Uri) {
        if (!isSelectionMode) enterSelectionMode()
        toggleImageSelection(uri)
    }

    private fun toggleFileSelection(file: File) {
        if (selectedFiles.contains(file)) selectedFiles.remove(file) else selectedFiles.add(file)
        updateSelectionCount()
        fileAdapter.updateSelection(selectedFiles)
    }

    private fun toggleImageSelection(uri: Uri) {
        if (selectedImages.contains(uri)) selectedImages.remove(uri) else selectedImages.add(uri)
        updateSelectionCount()
        imageAdapter.updateSelection(selectedImages)
    }

    private fun updateSelectionCount() {
        val count = if (isGalleryMode) selectedImages.size else selectedFiles.size
        if (count == 0) clearSelection() else tvSelectionCount.text = "تم تحديد $count عنصر"
    }

    private fun deleteSelectedFiles() {
        val count = if (isGalleryMode) selectedImages.size else selectedFiles.size
        if (count == 0) return
        AlertDialog.Builder(this).setTitle("حذف العناصر").setMessage("هل أنت متأكد من حذف $count عنصر؟")
            .setPositiveButton("حذف") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    if (isGalleryMode) {
                        selectedImages.forEach { try { contentResolver.delete(it, null, null) } catch (e: Exception) {} }
                    } else {
                        selectedFiles.forEach { if (it.isDirectory) it.deleteRecursively() else it.delete() }
                    }
                    withContext(Dispatchers.Main) {
                        clearSelection()
                        if (isGalleryMode) loadRealGallery() else loadDirectory(currentDirectory)
                        Toast.makeText(this@FileManagerActivity, "تم الحذف", Toast.LENGTH_SHORT).show()
                    }
                }
            }.setNegativeButton("إلغاء", null).show()
    }

    private fun shareSelectedFiles() {
        val urisToShare = mutableListOf<Uri>()
        if (isGalleryMode) {
            urisToShare.addAll(selectedImages)
        } else {
            selectedFiles.forEach { 
                try { urisToShare.add(FileProvider.getUriForFile(this, "${packageName}.fileprovider", it)) } catch (e: Exception) {} 
            }
        }
        if (urisToShare.isEmpty()) return

        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(urisToShare))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "مشاركة الملفات عبر"))
    }

    private fun showAddToAlbumDialog() {
        // سيتم تنفيذها لاحقاً لإضافة الصور للألبوم الافتراضي
        Toast.makeText(this, "سيتم إضافة الصور إلى الألبوم قريباً...", Toast.LENGTH_SHORT).show()
    }

    // ... (باقي الدوال: showSortDialog, applyFilterAndSort, installApk, showCreateFolderDialog, loadRealGallery, loadAlbums, handleAlbumClick, loadImagesForSystemAlbum, loadImagesForVirtualAlbum, showCreateVirtualAlbumDialog, handleZipFile, showExtractPasswordDialog, executeExtraction, shakeView, showCreateZipDialog, executeZipCreation, checkAndRequestPermission, loadDirectory) ...
    // (لقد اختصرتها هنا لتوفير المساحة، تأكد من دمجها مع الكود الكامل السابق)
}

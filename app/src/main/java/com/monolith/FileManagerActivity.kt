package com.monolith

import android.app.AlertDialog
import android.content.ContentUris
import android.content.Intent
import android.media.MediaScannerConnection
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
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

enum class ViewMode { FILES, GALLERY, SCREENSHOTS, ALBUMS, TRASH }

class FileManagerActivity : AppCompatActivity() {

    private var currentMode: ViewMode = ViewMode.FILES
    private var isTabChangeInternal = false 

    private lateinit var rvFiles: RecyclerView
    private lateinit var fileAdapter: FileAdapter
    private lateinit var imageAdapter: ImageAdapter
    private lateinit var albumAdapter: AlbumAdapter
    private lateinit var etSearchFiles: EditText
    private lateinit var tabLayoutFiles: TabLayout
    private lateinit var tvPathTitle: TextView
    
    private lateinit var topBarFileManager: LinearLayout
    private lateinit var topBarSelection: LinearLayout
    private lateinit var tvSelectionCount: TextView
    private lateinit var bottomSelectionBar: LinearLayout
    private lateinit var btnSelectAll: ImageButton
    private lateinit var actionDelete: LinearLayout
    private lateinit var actionShare: LinearLayout
    private lateinit var fabCreateFolder: FloatingActionButton
    
    private var currentDirectory: File = Environment.getExternalStorageDirectory()
    private var allFiles: MutableList<File> = mutableListOf()
    private var galleryUris: MutableList<Uri> = mutableListOf()
    
    private var isSortAscending = true
    private var sortType = "NAME"
    private var showHiddenFiles = false

    private var isSelectionMode = false
    private val selectedFiles = mutableSetOf<File>()
    private val selectedImages = mutableSetOf<Uri>()

    private lateinit var virtualAlbumDao: VirtualAlbumDao

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Environment.isExternalStorageManager()) {
            loadDataForCurrentMode()
        } else {
            Toast.makeText(this, "نحتاج إذن الوصول للملفات.", Toast.LENGTH_LONG).show()
            currentMode = ViewMode.FILES
            updateUIForMode()
        }
    }
    
    private val requestImagePermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            if (currentMode == ViewMode.SCREENSHOTS) loadScreenshots() else loadRealGallery()
        } else {
            Toast.makeText(this, "نحتاج إذن الصور.", Toast.LENGTH_SHORT).show()
            setViewMode(ViewMode.FILES)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // تحميل مكتبة التشفير الجديدة
        System.loadLibrary("sqlcipher")

        // شبكة الأمان: إذا حدث أي خطأ فادح، سيتم عرض رسالته بدلاً من إغلاق التطبيق!
        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            e.printStackTrace()
            runOnUiThread {
                AlertDialog.Builder(this)
                    .setTitle("عطل برمجي (الرجاء تصوير هذه الرسالة)")
                    .setMessage("الخطأ: ${e.message}\n\nالمكان: ${e.stackTrace.firstOrNull()}")
                    .setPositiveButton("حسناً", null)
                    .show()
            }
        }

        setContentView(R.layout.activity_file_manager)
        
        try {
            virtualAlbumDao = AppDatabase.getDatabase(applicationContext).virtualAlbumDao()
        } catch (e: Exception) {
            Toast.makeText(this, "خطأ في قاعدة البيانات", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initViews()
        setupListeners()
        checkAndRequestInitialPermission()
    }

    private fun initViews() {
        try {
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

            fileAdapter = FileAdapter({ file -> handleFileClick(file) }, { file -> handleFileLongClick(file) })
            imageAdapter = ImageAdapter({ uri -> handleImageClick(uri) }, { uri -> handleImageLongClick(uri) })
            albumAdapter = AlbumAdapter { album -> handleAlbumClick(album) }
        } catch (e: Exception) {
            Toast.makeText(this, "خطأ في تهيئة الواجهة", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupListeners() {
        tabLayoutFiles.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                if (isTabChangeInternal) return 
                try {
                    when (tab?.position) {
                        0 -> if (currentMode != ViewMode.FILES) setViewMode(ViewMode.FILES)
                        1 -> if (currentMode != ViewMode.GALLERY) setViewMode(ViewMode.GALLERY)
                        2 -> if (currentMode != ViewMode.ALBUMS) setViewMode(ViewMode.ALBUMS)
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        etSearchFiles.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { applyFilterAndSort() }
            override fun afterTextChanged(s: Editable?) {}
        })

        findViewById<ImageButton>(R.id.btnBackFileManager).setOnClickListener { handleBackPress() }
        findViewById<ImageButton>(R.id.btnCancelSelection).setOnClickListener { clearSelection() }
        findViewById<ImageButton>(R.id.btnSort).setOnClickListener { showSortDialog() }

        findViewById<ImageButton>(R.id.btnZipSelected)?.setOnClickListener { 
            if (selectedFiles.isNotEmpty()) showCreateZipDialog(selectedFiles.toList()) 
        }

        actionDelete.setOnClickListener { moveSelectedToTrash() }
        actionShare.setOnClickListener { shareSelectedFiles() }
        
        findViewById<LinearLayout>(R.id.btnPinnedNew)?.setOnClickListener { showCreateVirtualAlbumDialog() }
        findViewById<LinearLayout>(R.id.btnPinnedFavorites)?.setOnClickListener { Toast.makeText(this, "قريباً", Toast.LENGTH_SHORT).show() }
        
        findViewById<LinearLayout>(R.id.btnPinnedScreenshots)?.setOnClickListener { 
            try { setViewMode(ViewMode.SCREENSHOTS) } catch (e: Exception) { Toast.makeText(this, "لا يمكن فتح لقطات الشاشة", Toast.LENGTH_SHORT).show() }
        }
        findViewById<LinearLayout>(R.id.btnPinnedTrash)?.setOnClickListener { 
            try { setViewMode(ViewMode.TRASH) } catch (e: Exception) { Toast.makeText(this, "لا يمكن فتح المهملات", Toast.LENGTH_SHORT).show() }
        }

        btnSelectAll.setOnClickListener {
            if (currentMode == ViewMode.GALLERY || currentMode == ViewMode.SCREENSHOTS) {
                selectedImages.addAll(galleryUris); imageAdapter.updateSelection(selectedImages)
            } else {
                selectedFiles.addAll(allFiles); fileAdapter.updateSelection(selectedFiles)
            }
            updateSelectionCount()
        }

        fabCreateFolder.setOnClickListener {
            if (currentMode == ViewMode.ALBUMS) showCreateVirtualAlbumDialog() else showCreateFolderDialog()
        }
    }

    private fun setViewMode(mode: ViewMode) {
        if (mode == currentMode) return
        currentMode = mode
        clearSelection()
        
        isTabChangeInternal = true
        when (mode) {
            ViewMode.FILES, ViewMode.TRASH -> tabLayoutFiles.getTabAt(0)?.select()
            ViewMode.GALLERY, ViewMode.SCREENSHOTS -> tabLayoutFiles.getTabAt(1)?.select()
            ViewMode.ALBUMS -> tabLayoutFiles.getTabAt(2)?.select()
        }
        isTabChangeInternal = false
        
        updateUIForMode()
        loadDataForCurrentMode()
    }

    private fun updateUIForMode() {
        try {
            when (currentMode) {
                ViewMode.FILES -> {
                    rvFiles.layoutManager = LinearLayoutManager(this)
                    rvFiles.adapter = fileAdapter
                    fabCreateFolder.visibility = View.VISIBLE
                    tvPathTitle.text = currentDirectory.name
                }
                ViewMode.TRASH -> {
                    rvFiles.layoutManager = LinearLayoutManager(this)
                    rvFiles.adapter = fileAdapter
                    fabCreateFolder.visibility = View.GONE
                    tvPathTitle.text = "سلة المهملات"
                }
                ViewMode.GALLERY -> {
                    rvFiles.layoutManager = GridLayoutManager(this, 3)
                    rvFiles.adapter = imageAdapter
                    fabCreateFolder.visibility = View.GONE
                    tvPathTitle.text = "معرض الصور"
                }
                ViewMode.SCREENSHOTS -> {
                    rvFiles.layoutManager = GridLayoutManager(this, 3)
                    rvFiles.adapter = imageAdapter
                    fabCreateFolder.visibility = View.GONE
                    tvPathTitle.text = "لقطات الشاشة"
                }
                ViewMode.ALBUMS -> {
                    rvFiles.layoutManager = GridLayoutManager(this, 2)
                    rvFiles.adapter = albumAdapter
                    fabCreateFolder.visibility = View.VISIBLE
                    tvPathTitle.text = "الألبومات"
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadDataForCurrentMode() {
        try {
            when (currentMode) {
                ViewMode.FILES -> loadDirectory(currentDirectory)
                ViewMode.TRASH -> loadTrashDirectory()
                ViewMode.GALLERY -> checkImagePermissionAndLoad()
                ViewMode.SCREENSHOTS -> checkImagePermissionAndLoad()
                ViewMode.ALBUMS -> loadAlbums()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "حدث خطأ أثناء تحميل البيانات", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun checkImagePermissionAndLoad() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    loadImages()
                } else {
                    requestImagePermissionLauncher.launch(android.Manifest.permission.READ_MEDIA_IMAGES)
                }
            } else {
                loadImages()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            setViewMode(ViewMode.FILES)
        }
    }

    private fun loadImages() {
        try {
            if (currentMode == ViewMode.SCREENSHOTS) loadScreenshots() else loadRealGallery()
        } catch (e: Exception) {
            Toast.makeText(this, "لا يمكن فتح المعرض", Toast.LENGTH_SHORT).show()
            setViewMode(ViewMode.FILES)
        }
    }

    private fun handleBackPress() {
        if (isSelectionMode) {
            clearSelection()
        } else {
            try {
                when (currentMode) {
                    ViewMode.FILES -> {
                        val parent = currentDirectory.parentFile
                        if (parent != null) loadDirectory(parent) else finish()
                    }
                    ViewMode.TRASH, ViewMode.SCREENSHOTS -> setViewMode(ViewMode.GALLERY)
                    ViewMode.ALBUMS, ViewMode.GALLERY -> setViewMode(ViewMode.FILES)
                }
            } catch (e: Exception) {
                finish()
            }
        }
    }

    override fun onBackPressed() { handleBackPress() }

    private fun getTrashDir(): File {
        val trashDir = File(Environment.getExternalStorageDirectory(), ".MonolithTrash")
        if (!trashDir.exists()) {
            try {
                trashDir.mkdirs()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return trashDir
    }

    private fun getPathFromUri(uri: Uri): String? {
        var path: String? = null
        try {
            val projection = arrayOf(MediaStore.Images.Media.DATA)
            val cursor = contentResolver.query(uri, projection, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val columnIndex = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                    path = it.getString(columnIndex)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return path
    }

    private fun moveSelectedToTrash() {
        val count = if (currentMode == ViewMode.GALLERY || currentMode == ViewMode.SCREENSHOTS) selectedImages.size else selectedFiles.size
        if (count == 0) return
        AlertDialog.Builder(this).setTitle("نقل إلى سلة المهملات").setMessage("نقل $count عنصر إلى السلة؟")
            .setPositiveButton("نعم") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val trashDir = getTrashDir()
                        if (currentMode == ViewMode.GALLERY || currentMode == ViewMode.SCREENSHOTS) {
                            selectedImages.forEach { uri ->
                                try {
                                    val path = getPathFromUri(uri)
                                    if (path != null) {
                                        val originalFile = File(path)
                                        if (originalFile.exists()) {
                                            val destFile = File(trashDir, "${System.currentTimeMillis()}_${originalFile.name}")
                                            originalFile.copyTo(destFile, overwrite = true)
                                            if (originalFile.delete()) {
                                                MediaScannerConnection.scanFile(this@FileManagerActivity, arrayOf(path), null, null)
                                            }
                                        }
                                    }
                                } catch (e: Exception) { e.printStackTrace() }
                            }
                        } else {
                            selectedFiles.forEach { file ->
                                try {
                                    val destFile = File(trashDir, "${System.currentTimeMillis()}_${file.name}")
                                    if (file.renameTo(destFile)) {
                                        MediaScannerConnection.scanFile(this@FileManagerActivity, arrayOf(file.absolutePath), null, null)
                                    }
                                } catch (e: Exception) { e.printStackTrace() }
                            }
                        }
                        withContext(Dispatchers.Main) {
                            clearSelection()
                            loadDataForCurrentMode()
                            Toast.makeText(this@FileManagerActivity, "تم النقل إلى سلة المهملات", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@FileManagerActivity, "فشل نقل بعض العناصر", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }.setNegativeButton("إلغاء", null).show()
    }

    private fun loadTrashDirectory() {
        lifecycleScope.launch {
            try {
                val files = withContext(Dispatchers.IO) { 
                    try {
                        getTrashDir().listFiles()?.toMutableList() ?: mutableListOf() 
                    } catch (e: Exception) { mutableListOf<File>() }
                }
                withContext(Dispatchers.Main) { allFiles = files; applyFilterAndSort() }
            } catch (e: Exception) {
                Toast.makeText(this@FileManagerActivity, "المهملات فارغة أو غير متاحة", Toast.LENGTH_SHORT).show()
                withContext(Dispatchers.Main) { setViewMode(ViewMode.FILES) }
            }
        }
    }

    private fun enterSelectionMode() {
        isSelectionMode = true
        topBarFileManager.visibility = View.GONE
        topBarSelection.visibility = View.VISIBLE
        bottomSelectionBar.visibility = View.VISIBLE
        fileAdapter.setSelectionMode(true)
        imageAdapter.setSelectionMode(true)
    }

    fun clearSelection() {
        isSelectionMode = false
        selectedFiles.clear()
        selectedImages.clear()
        topBarFileManager.visibility = View.VISIBLE
        topBarSelection.visibility = View.GONE
        bottomSelectionBar.visibility = View.GONE
        fileAdapter.setSelectionMode(false)
        imageAdapter.setSelectionMode(false)
        updateSelectionCount()
    }

    private fun handleFileClick(file: File) {
        if (isSelectionMode) toggleFileSelection(file)
        else {
            if (currentMode == ViewMode.TRASH) {
                AlertDialog.Builder(this).setTitle(file.name).setItems(arrayOf("استعادة", "حذف نهائي")) { _, which ->
                    if (which == 0) {
                        try {
                            val originalName = file.name.substringAfter("_")
                            val isImage = originalName.endsWith(".jpg", true) || originalName.endsWith(".png", true) || originalName.endsWith(".jpeg", true) || originalName.endsWith(".webp", true)
                            val restoreDir = if (isImage) {
                                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                                val restoredDir = File(picturesDir, "MonolithRestored")
                                if (!restoredDir.exists()) restoredDir.mkdirs()
                                restoredDir
                            } else {
                                currentDirectory
                            }
                            val restoredFile = File(restoreDir, originalName)
                            if (file.renameTo(restoredFile)) {
                                MediaScannerConnection.scanFile(this@FileManagerActivity, arrayOf(restoredFile.absolutePath), null, null)
                                Toast.makeText(this, "تمت الاستعادة إلى ${restoreDir.name}", Toast.LENGTH_SHORT).show()
                                loadTrashDirectory() 
                            }
                        } catch (e: Exception) {
                            Toast.makeText(this, "فشلت الاستعادة", Toast.LENGTH_SHORT).show()
                        }
                    } else { 
                        try {
                            file.delete()
                            loadTrashDirectory()
                            Toast.makeText(this, "تم الحذف نهائياً", Toast.LENGTH_SHORT).show() 
                        } catch (e: Exception) {
                            Toast.makeText(this, "فشل الحذف", Toast.LENGTH_SHORT).show()
                        }
                    }
                }.show()
                return
            }
            when {
                file.isDirectory -> loadDirectory(file)
                file.name.endsWith(".apk", true) -> installApk(file)
                file.name.endsWith(".zip", true) -> handleZipFile(file)
                else -> openFile(file)
            }
        }
    }

    private fun openFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
            startActivity(Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, mimeType); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) })
        } catch (e: Exception) { Toast.makeText(this, "لا يوجد تطبيق لفتح الملف", Toast.LENGTH_SHORT).show() }
    }

    private fun handleImageClick(uri: Uri) {
        if (isSelectionMode) {
            toggleImageSelection(uri)
        } else {
            try {
                startActivity(Intent(this, ImageViewerActivity::class.java).putExtra("image_uri", uri.toString()))
            } catch (e: Exception) {
                Toast.makeText(this, "لا يمكن فتح الصورة: ${e.message}", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        }
    }

    private fun handleFileLongClick(file: File) { if (!isSelectionMode) enterSelectionMode(); toggleFileSelection(file) }
    private fun handleImageLongClick(uri: Uri) { if (!isSelectionMode) enterSelectionMode(); toggleImageSelection(uri) }

    private fun toggleFileSelection(file: File) {
        if (selectedFiles.contains(file)) selectedFiles.remove(file) else selectedFiles.add(file)
        updateSelectionCount(); fileAdapter.updateSelection(selectedFiles)
    }

    private fun toggleImageSelection(uri: Uri) {
        if (selectedImages.contains(uri)) selectedImages.remove(uri) else selectedImages.add(uri)
        updateSelectionCount(); imageAdapter.updateSelection(selectedImages)
    }

    private fun updateSelectionCount() {
        val count = if (currentMode == ViewMode.GALLERY || currentMode == ViewMode.SCREENSHOTS) selectedImages.size else selectedFiles.size
        if (count == 0) clearSelection() else tvSelectionCount.text = "تم تحديد $count عنصر"
    }

    private fun shareSelectedFiles() {
        try {
            val urisToShare = mutableListOf<Uri>()
            if (currentMode == ViewMode.GALLERY || currentMode == ViewMode.SCREENSHOTS) { urisToShare.addAll(selectedImages) }
            else { selectedFiles.forEach { try { urisToShare.add(FileProvider.getUriForFile(this, "${packageName}.fileprovider", it)) } catch (e: Exception) {} } }
            if (urisToShare.isEmpty()) return
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"; putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(urisToShare)); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "مشاركة عبر"))
        } catch (e: Exception) {
            Toast.makeText(this, "فشلت المشاركة", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSortDialog() {
        val options = arrayOf("الاسم", "الحجم", "التاريخ", "عرض المخفي")
        AlertDialog.Builder(this).setTitle("ترتيب").setItems(options) { _, which ->
            when (which) { 0 -> { sortType = "NAME"; isSortAscending = !isSortAscending }; 1 -> { sortType = "SIZE"; isSortAscending = !isSortAscending }; 2 -> { sortType = "DATE"; isSortAscending = !isSortAscending }; 3 -> { showHiddenFiles = !showHiddenFiles } }
            applyFilterAndSort()
        }.show()
    }

    private fun applyFilterAndSort() {
        try {
            val query = etSearchFiles.text.toString()
            if (currentMode == ViewMode.GALLERY || currentMode == ViewMode.SCREENSHOTS) {
                val filteredUris = if (query.isEmpty()) galleryUris.toList() else galleryUris.filter { (it.lastPathSegment ?: "").contains(query, ignoreCase = true) }
                imageAdapter.submitList(filteredUris)
            } else {
                var filtered = if (query.isEmpty()) allFiles.toList() else allFiles.filter { it.name.contains(query, ignoreCase = true) }
                if (!showHiddenFiles) filtered = filtered.filter { !it.name.startsWith(".") }
                val comparator = when (sortType) { "SIZE" -> compareBy<File> { it.length() }; "DATE" -> compareBy { it.lastModified() }; else -> compareBy { it.name.lowercase() } }
                var sortedList = if (isSortAscending) filtered.sortedWith(comparator) else filtered.sortedWith(comparator).reversed()
                sortedList = sortedList.sortedBy { !it.isDirectory }
                fileAdapter.submitList(sortedList.map { FileItem(file = it) }, false)
            }
        } catch (e: Exception) { 
            e.printStackTrace() 
            Toast.makeText(this, "خطأ في التصفية والترتيب", Toast.LENGTH_SHORT).show()
        }
    }

    private fun installApk(apkFile: File) {
        try { 
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", apkFile)
            startActivity(Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, "application/vnd.android.package-archive"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }) 
        } catch (e: Exception) { Toast.makeText(this, "فشل فتح المثبت", Toast.LENGTH_SHORT).show() }
    }

    private fun showCreateFolderDialog() {
        try {
            val input = EditText(this).apply { hint = "اسم المجلد" }
            AlertDialog.Builder(this).setTitle("إنشاء مجلد").setView(input).setPositiveButton("إنشاء") { _, _ -> 
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) { val newFolder = File(currentDirectory, name); if (newFolder.mkdirs()) { loadDirectory(currentDirectory) } }
            }.setNegativeButton("إلغاء", null).show()
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun loadScreenshots() {
        lifecycleScope.launch {
            try {
                val uris = withContext(Dispatchers.IO) {
                    val list = mutableListOf<Uri>()
                    val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? OR ${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} LIKE ?"
                    val selectionArgs = arrayOf("%Screenshot%", "%Screenshot%")
                    val cursor = contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media._ID), selection, selectionArgs, "${MediaStore.Images.Media.DATE_ADDED} DESC")
                    cursor?.use { val idCol = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID); while (it.moveToNext()) { list.add(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, it.getLong(idCol))) } }
                    list
                }
                withContext(Dispatchers.Main) { 
                    if (uris.isEmpty()) Toast.makeText(this@FileManagerActivity, "لا توجد لقطات شاشة", Toast.LENGTH_SHORT).show()
                    else { galleryUris = uris; applyFilterAndSort() }
                }
            } catch (e: Exception) { 
                e.printStackTrace() 
                withContext(Dispatchers.Main) { setViewMode(ViewMode.FILES); Toast.makeText(this@FileManagerActivity, "حدث خطأ في تحميل لقطات الشاشة", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun loadRealGallery() {
        lifecycleScope.launch {
            try {
                val uris = withContext(Dispatchers.IO) {
                    val list = mutableListOf<Uri>()
                    val cursor = contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media._ID), null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC")
                    cursor?.use { val idCol = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID); while (it.moveToNext()) { list.add(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, it.getLong(idCol))) } }
                    list
                }
                withContext(Dispatchers.Main) { galleryUris = uris; applyFilterAndSort() }
            } catch (e: Exception) { 
                e.printStackTrace() 
                withContext(Dispatchers.Main) { setViewMode(ViewMode.FILES); Toast.makeText(this@FileManagerActivity, "حدث خطأ في تحميل المعرض، تأكد من الصلاحيات", Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun loadAlbums() {
        lifecycleScope.launch {
            try {
                val albumsList = mutableListOf<AlbumItem>()
                withContext(Dispatchers.IO) {
                    val projection = arrayOf(MediaStore.Images.Media.BUCKET_ID, MediaStore.Images.Media.BUCKET_DISPLAY_NAME, MediaStore.Images.Media._ID)
                    val cursor = contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC")
                    val bucketMap = mutableMapOf<String, Pair<String, Uri>>()
                    val countMap = mutableMapOf<String, Int>()
                    cursor?.use {
                        val idCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
                        val nameCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                        val imgIdCol = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                        while (it.moveToNext()) {
                            val bucketId = it.getString(idCol) ?: "unknown"
                            val bucketName = it.getString(nameCol) ?: "غير معروف"
                            val imgId = it.getLong(imgIdCol)
                            val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imgId)
                            if (!bucketMap.containsKey(bucketId)) bucketMap[bucketId] = Pair(bucketName, uri)
                            countMap[bucketId] = (countMap[bucketId] ?: 0) + 1
                        }
                    }
                    bucketMap.forEach { (id, pair) -> albumsList.add(AlbumItem(id, pair.first, pair.second, countMap[id] ?: 0)) }
                }
                val virtualAlbums = virtualAlbumDao.getAllAlbums()
                for (vAlbum in virtualAlbums) {
                    val mediaUris = virtualAlbumDao.getMediaForAlbum(vAlbum.id)
                    val cover = if (mediaUris.isNotEmpty()) Uri.parse(mediaUris.first()) else null
                    albumsList.add(AlbumItem(vAlbum.id, vAlbum.name, cover, mediaUris.size, vAlbum.isVault))
                }
                withContext(Dispatchers.Main) { albumAdapter.submitList(albumsList) }
            } catch (e: Exception) { 
                e.printStackTrace() 
                withContext(Dispatchers.Main) { setViewMode(ViewMode.FILES); Toast.makeText(this@FileManagerActivity, "حدث خطأ في تحميل الألبومات", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun handleAlbumClick(album: AlbumItem) {
        currentMode = ViewMode.GALLERY
        rvFiles.layoutManager = GridLayoutManager(this, 3); rvFiles.adapter = imageAdapter
        tvPathTitle.text = album.name
        lifecycleScope.launch {
            try {
                val uris = withContext(Dispatchers.IO) {
                    val list = mutableListOf<Uri>()
                    val selection = "${MediaStore.Images.Media.BUCKET_ID} = ?"
                    val cursor = contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media._ID), selection, arrayOf(album.bucketId), "${MediaStore.Images.Media.DATE_ADDED} DESC")
                    cursor?.use { val idCol = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID); while (it.moveToNext()) { list.add(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, it.getLong(idCol))) } }
                    list
                }
                withContext(Dispatchers.Main) { imageAdapter.submitList(uris) }
            } catch (e: Exception) { 
                e.printStackTrace() 
                withContext(Dispatchers.Main) { setViewMode(ViewMode.ALBUMS); Toast.makeText(this@FileManagerActivity, "حدث خطأ في فتح الألبوم", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun showCreateVirtualAlbumDialog() {
        try {
            val input = EditText(this).apply { hint = "اسم الألبوم" }
            AlertDialog.Builder(this).setTitle("إنشاء ألبوم").setView(input).setPositiveButton("إنشاء") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) { lifecycleScope.launch { virtualAlbumDao.insertAlbum(VirtualAlbumEntity(name = name)); loadAlbums() } }
            }.setNegativeButton("إلغاء", null).show()
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun handleZipFile(zipFile: File) {
        try { if (ZipFile(zipFile).isEncrypted) showExtractPasswordDialog(zipFile) else executeExtraction(zipFile) }
        catch (e: Exception) { Toast.makeText(this, "أرشيف غير صالح", Toast.LENGTH_SHORT).show() }
    }

    private fun showExtractPasswordDialog(zipFile: File) {
        try {
            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_extract_zip, null)
            val dialog = AlertDialog.Builder(this).setView(dialogView).setCancelable(false).create()
            val tilPassword = dialogView.findViewById<TextInputLayout>(R.id.tilExtractPassword)
            val etPassword = dialogView.findViewById<TextInputEditText>(R.id.etExtractPassword)
            val tvError = dialogView.findViewById<TextView>(R.id.tvErrorText)
            val btnExtract = dialogView.findViewById<MaterialButton>(R.id.btnExtractZip)
            dialogView.findViewById<MaterialButton>(R.id.btnCancelExtract).setOnClickListener { dialog.dismiss() }
            btnExtract.setOnClickListener {
                val password = etPassword.text.toString()
                if (password.isEmpty()) { tvError.text = "أدخل كلمة المرور"; tvError.visibility = View.VISIBLE; return@setOnClickListener }
                tvError.visibility = View.GONE; btnExtract.isEnabled = false; btnExtract.text = "جاري الفك..."
                lifecycleScope.launch {
                    val success = withContext(Dispatchers.IO) {
                        try {
                            val dest = File(zipFile.parentFile, zipFile.nameWithoutExtension)
                            if (!dest.exists()) dest.mkdirs()
                            val zip = ZipFile(zipFile); if (password.isNotEmpty()) zip.setPassword(password.toCharArray())
                            zip.extractAll(dest.absolutePath)
                            true
                        } catch (e: Exception) { false }
                    }
                    if (success) { dialog.dismiss(); Toast.makeText(this@FileManagerActivity, "تم الفك!", Toast.LENGTH_SHORT).show(); loadDataForCurrentMode() }
                    else { tvError.text = "كلمة مرور خاطئة"; tvError.visibility = View.VISIBLE; tilPassword.boxStrokeColor = android.graphics.Color.RED; shakeView(tilPassword); btnExtract.isEnabled = true; btnExtract.text = "فك الضغط" }
                }
            }
            dialog.show()
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun executeExtraction(zipFile: File) {
        lifecycleScope.launch {
            try {
                val success = withContext(Dispatchers.IO) {
                    try {
                        val dest = File(zipFile.parentFile, zipFile.nameWithoutExtension)
                        if (!dest.exists()) dest.mkdirs()
                        ZipFile(zipFile).extractAll(dest.absolutePath)
                        true
                    } catch (e: Exception) { false }
                }
                if (success) { Toast.makeText(this@FileManagerActivity, "تم الفك!", Toast.LENGTH_SHORT).show(); loadDataForCurrentMode() }
                else { Toast.makeText(this@FileManagerActivity, "فشل الفك", Toast.LENGTH_SHORT).show() }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun shakeView(view: View) {
        try {
            val shake = TranslateAnimation(0f, 10f, 0f, 0f)
            shake.duration = 150; shake.interpolator = CycleInterpolator(7f); view.startAnimation(shake)
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun showCreateZipDialog(filesToZip: List<File>) {
        try {
            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_zip, null)
            val dialog = AlertDialog.Builder(this).setView(dialogView).create()
            val etZipName = dialogView.findViewById<EditText>(R.id.etZipName)
            val etZipPassword = dialogView.findViewById<TextInputEditText>(R.id.etZipPassword)
            etZipName.setText(if (filesToZip.size == 1) filesToZip[0].nameWithoutExtension else "Archive_${System.currentTimeMillis()}")
            dialogView.findViewById<MaterialButton>(R.id.btnCancelZip).setOnClickListener { dialog.dismiss() }
            dialogView.findViewById<MaterialButton>(R.id.btnCreateZip).setOnClickListener {
                val name = etZipName.text.toString().trim()
                if (name.isEmpty()) return@setOnClickListener
                dialog.dismiss(); executeZipCreation(filesToZip, name, etZipPassword.text.toString())
            }
            dialog.show()
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun executeZipCreation(files: List<File>, zipName: String, password: String) {
        Toast.makeText(this, "جاري الضغط...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val dest = File(currentDirectory, "$zipName.zip")
                    val params = ZipParameters().apply { if (password.isNotEmpty()) { isEncryptFiles = true; encryptionMethod = EncryptionMethod.AES; aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256 } }
                    val zip = if (password.isNotEmpty()) ZipFile(dest, password.toCharArray()) else ZipFile(dest)
                    files.forEach { if (it.isDirectory) zip.addFolder(it, params) else zip.addFile(it, params) }
                }
                withContext(Dispatchers.Main) { Toast.makeText(this@FileManagerActivity, "تم الضغط!", Toast.LENGTH_SHORT).show(); loadDataForCurrentMode() }
            } catch (e: Exception) { 
                withContext(Dispatchers.Main) { Toast.makeText(this@FileManagerActivity, "فشل: ${e.message}", Toast.LENGTH_LONG).show() } 
            }
        }
    }

    private fun checkAndRequestInitialPermission() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) loadDataForCurrentMode()
                else { requestPermissionLauncher.launch(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply { addCategory("android.intent.category.DEFAULT"); data = Uri.parse("package:$packageName") }) }
            } else loadDataForCurrentMode()
        } catch (e: Exception) {
            Toast.makeText(this, "حدث خطأ في الصلاحيات", Toast.LENGTH_SHORT).show()
            loadDataForCurrentMode() 
        }
    }

    private fun loadDirectory(dir: File) {
        currentDirectory = dir
        lifecycleScope.launch {
            try {
                val files = withContext(Dispatchers.IO) { dir.listFiles()?.toMutableList() ?: mutableListOf() }
                withContext(Dispatchers.Main) { allFiles = files; applyFilterAndSort() }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@FileManagerActivity, "لا يمكن الوصول للمجلد", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

data class FileItem(
    val file: File,
    val name: String = file.name,
    val isDirectory: Boolean = file.isDirectory,
    val size: Long = file.length(),
    val lastModified: Long = file.lastModified()
)

class FileAdapter(
    private val onFileClick: (File) -> Unit,
    private val onFileLongClick: (File) -> Unit
) : RecyclerView.Adapter<FileAdapter.FileViewHolder>() {
    private var files: List<FileItem> = emptyList()
    private var isSelectionMode = false
    private var selectedFiles: Set<File> = emptySet()

    fun submitList(newFiles: List<FileItem>, isGallery: Boolean) { files = newFiles; notifyDataSetChanged() }
    fun setSelectionMode(mode: Boolean) { isSelectionMode = mode; notifyDataSetChanged() }
    fun updateSelection(selected: Set<File>) { selectedFiles = selected; notifyDataSetChanged() }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val item = files[position]
        holder.bind(item, isSelectionMode, selectedFiles.contains(item.file), onFileClick, onFileLongClick)
    }

    override fun getItemCount(): Int = files.size

    class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivFileIcon: ImageView = itemView.findViewById(R.id.ivFileIcon)
        private val tvFileName: TextView = itemView.findViewById(R.id.tvFileName)
        private val tvFileDetails: TextView = itemView.findViewById(R.id.tvFileDetails)
        private val cbSelectFile: CheckBox = itemView.findViewById(R.id.cbSelectFile)

        fun bind(item: FileItem, isSelectionMode: Boolean, isSelected: Boolean, onClick: (File) -> Unit, onLongClick: (File) -> Unit) {
            val file = item.file
            tvFileName.text = item.name
            val dateString = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(item.lastModified))
            cbSelectFile.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
            cbSelectFile.isChecked = isSelected

            if (file.isDirectory) {
                ivFileIcon.setImageResource(android.R.drawable.ic_menu_set_as)
                ivFileIcon.setColorFilter(android.graphics.Color.parseColor("#00D4FF"))
                tvFileDetails.text = "مجلد • $dateString"
            } else {
                val sizeInKb = file.length() / 1024
                val sizeString = if (sizeInKb > 1024) "${sizeInKb / 1024} MB" else "$sizeInKb KB"
                tvFileDetails.visibility = View.VISIBLE
                
                if (file.name.endsWith(".jpg", true) || file.name.endsWith(".jpeg", true) || file.name.endsWith(".png", true) || file.name.endsWith(".webp", true)) {
                    Glide.with(itemView.context).load(file).centerCrop().into(ivFileIcon)
                } else if (file.name.endsWith(".zip", true)) { 
                    ivFileIcon.setImageResource(android.R.drawable.ic_menu_save); ivFileIcon.setColorFilter(android.graphics.Color.parseColor("#FFAA00")) 
                } else if (file.name.endsWith(".apk", true)) { 
                    ivFileIcon.setImageResource(android.R.drawable.ic_menu_manage); ivFileIcon.setColorFilter(android.graphics.Color.parseColor("#00D4FF")) 
                } else {
                    ivFileIcon.setImageResource(android.R.drawable.ic_menu_agenda)
                }
                tvFileDetails.text = "$sizeString • $dateString"
            }
            itemView.setOnClickListener { onClick(file) }
            itemView.setOnLongClickListener { onLongClick(file); true }
        }
    }
}

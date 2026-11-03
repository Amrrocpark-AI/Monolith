package com.monolith

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.EncryptionMethod
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileManagerActivity : AppCompatActivity() {

    private lateinit var rvFiles: RecyclerView
    private lateinit var fileAdapter: FileAdapter
    private lateinit var etSearchFiles: EditText
    private lateinit var tabLayoutFiles: TabLayout
    
    private var currentDirectory: File = Environment.getExternalStorageDirectory()
    private var allFiles: List<File> = emptyList()
    private var isGalleryMode = false

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
        
        val btnBack = findViewById<ImageButton>(R.id.btnBackFileManager)
        val fabCreateFolder = findViewById<FloatingActionButton>(R.id.fabCreateFolder)

        // هنا نضيف حدث الضغط المطول (Long Click) لفتح نافذة ZIP
        fileAdapter = FileAdapter(
            onFileClick = { file ->
                if (file.isDirectory) {
                    loadDirectory(file)
                } else {
                    Toast.makeText(this, "فتح الملف: ${file.name}", Toast.LENGTH_SHORT).show()
                }
            },
            onFileLongClick = { file ->
                showCreateZipDialog(file)
            }
        )
        
        rvFiles.layoutManager = LinearLayoutManager(this)
        rvFiles.adapter = fileAdapter

        tabLayoutFiles.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> {
                        isGalleryMode = false
                        rvFiles.layoutManager = LinearLayoutManager(this@FileManagerActivity)
                        applyFilter(etSearchFiles.text.toString())
                    }
                    1 -> {
                        isGalleryMode = true
                        rvFiles.layoutManager = GridLayoutManager(this@FileManagerActivity, 3)
                        applyFilter(etSearchFiles.text.toString())
                    }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        etSearchFiles.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applyFilter(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnBack.setOnClickListener {
            val parent = currentDirectory.parentFile
            if (parent != null) loadDirectory(parent) else finish()
        }

        fabCreateFolder.setOnClickListener {
            Toast.makeText(this, "ميزة إنشاء المجلد قريباً...", Toast.LENGTH_SHORT).show()
        }

        checkAndRequestPermission()
    }

    // ==========================================
    // نافذة إنشاء ZIP والتشفير
    // ==========================================
    private fun showCreateZipDialog(fileToZip: File) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_zip, null)
        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        val etZipName = dialogView.findViewById<EditText>(R.id.etZipName)
        val etZipPassword = dialogView.findViewById<TextInputEditText>(R.id.etZipPassword)
        val tvPasswordStrength = dialogView.findViewById<TextView>(R.id.tvPasswordStrength)
        val btnCreate = dialogView.findViewById<MaterialButton>(R.id.btnCreateZip)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnCancelZip)

        // تعيين اسم افتراضي
        etZipName.setText(fileToZip.nameWithoutExtension)

        // مؤشر قوة كلمة المرور
        etZipPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val password = s.toString()
                when {
                    password.isEmpty() -> {
                        tvPasswordStrength.text = "بدون تشفير"
                        tvPasswordStrength.setTextColor(android.graphics.Color.parseColor("#AAAAAA"))
                    }
                    password.length < 6 -> {
                        tvPasswordStrength.text = "ضعيفة جداً (Red)"
                        tvPasswordStrength.setTextColor(android.graphics.Color.RED)
                    }
                    password.length < 10 -> {
                        tvPasswordStrength.text = "متوسطة (Yellow)"
                        tvPasswordStrength.setTextColor(android.graphics.Color.YELLOW)
                    }
                    else -> {
                        tvPasswordStrength.text = "قوية (AES-256)"
                        tvPasswordStrength.setTextColor(android.graphics.Color.GREEN)
                    }
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnCancel.setOnClickListener { alertDialog.dismiss() }

        btnCreate.setOnClickListener {
            val zipName = etZipName.text.toString().trim()
            val password = etZipPassword.text.toString()

            if (zipName.isEmpty()) {
                Toast.makeText(this, "الرجاء إدخال اسم الملف", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            alertDialog.dismiss()
            executeZipCreation(fileToZip, zipName, password)
        }

        alertDialog.show()
    }

    // ==========================================
    // محرك الضغط والتشفير (في الخلفية)
    // ==========================================
    private fun executeZipCreation(sourceFile: File, zipName: String, password: String) {
        Toast.makeText(this, "جاري الضغط...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val destinationFile = File(currentDirectory, "$zipName.zip")
                    
                    // إعدادات الضغط
                    val parameters = ZipParameters()
                    if (password.isNotEmpty()) {
                        parameters.isEncryptFiles = true
                        parameters.encryptionMethod = EncryptionMethod.AES
                        parameters.aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
                    }

                    val zipFile = if (password.isNotEmpty()) {
                        ZipFile(destinationFile, password.toCharArray())
                    } else {
                        ZipFile(destinationFile)
                    }

                    // تنفيذ الضغط
                    if (sourceFile.isDirectory) {
                        zipFile.addFolder(sourceFile, parameters)
                    } else {
                        zipFile.addFile(sourceFile, parameters)
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@FileManagerActivity, "تم إنشاء الأرشيف بنجاح!", Toast.LENGTH_LONG).show()
                    loadDirectory(currentDirectory) // تحديث القائمة
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@FileManagerActivity, "فشل الضغط: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun checkAndRequestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                loadDirectory(currentDirectory)
            } else {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.addCategory("android.intent.category.DEFAULT")
                intent.data = Uri.parse(String.format("package:%s", applicationContext.packageName))
                requestPermissionLauncher.launch(intent)
            }
        } else {
            loadDirectory(currentDirectory)
        }
    }

    private fun loadDirectory(directory: File) {
        currentDirectory = directory
        lifecycleScope.launch {
            val files = try {
                withContext(Dispatchers.IO) {
                    directory.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                } ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
            withContext(Dispatchers.Main) {
                allFiles = files
                applyFilter(etSearchFiles.text.toString())
            }
        }
    }

    private fun applyFilter(query: String) {
        val filteredFiles = if (query.isEmpty()) {
            allFiles
        } else {
            allFiles.filter { it.name.contains(query, ignoreCase = true) }
        }

        if (isGalleryMode) {
            val imageFiles = filteredFiles.filter { 
                it.extension.lowercase() in listOf("jpg", "jpeg", "png", "gif", "webp") 
            }
            fileAdapter.submitList(imageFiles, isGalleryMode)
        } else {
            fileAdapter.submitList(filteredFiles, isGalleryMode)
        }
    }
}

// ==========================================
// محول القائمة (تم تحديثه لدعم الضغط المطول)
// ==========================================
class FileAdapter(
    private val onFileClick: (File) -> Unit,
    private val onFileLongClick: (File) -> Unit
) : RecyclerView.Adapter<FileAdapter.FileViewHolder>() {
    
    private var files: List<File> = emptyList()
    private var isGalleryMode = false

    fun submitList(newFiles: List<File>, galleryMode: Boolean) { 
        files = newFiles
        isGalleryMode = galleryMode
        notifyDataSetChanged() 
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(files[position], isGalleryMode, onFileClick, onFileLongClick)
    }

    override fun getItemCount(): Int = files.size

    class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivFileIcon: ImageView = itemView.findViewById(R.id.ivFileIcon)
        private val tvFileName: TextView = itemView.findViewById(R.id.tvFileName)
        private val tvFileDetails: TextView = itemView.findViewById(R.id.tvFileDetails)

        fun bind(file: File, isGallery: Boolean, onClick: (File) -> Unit, onLongClick: (File) -> Unit) {
            tvFileName.text = file.name

            val date = Date(file.lastModified())
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val dateString = format.format(date)

            if (file.isDirectory) {
                ivFileIcon.setImageResource(android.R.drawable.ic_menu_set_as)
                ivFileIcon.setColorFilter(android.graphics.Color.parseColor("#00D4FF"))
                tvFileDetails.text = "مجلد • $dateString"
            } else {
                val sizeInKb = file.length() / 1024
                val sizeString = if (sizeInKb > 1024) "${sizeInKb / 1024} MB" else "$sizeInKb KB"

                val isImage = file.extension.lowercase() in listOf("jpg", "jpeg", "png", "gif", "webp")
                
                if (isImage && isGallery) {
                    Glide.with(itemView.context)
                        .load(file)
                        .centerCrop()
                        .into(ivFileIcon)
                } else {
                    ivFileIcon.setColorFilter(android.graphics.Color.parseColor("#AAAAAA"))
                    if (isImage) {
                        ivFileIcon.setImageResource(android.R.drawable.ic_menu_gallery)
                    } else {
                        ivFileIcon.setImageResource(android.R.drawable.ic_menu_agenda)
                    }
                    tvFileDetails.text = "$sizeString • $dateString"
                }
            }

            // ربط أحداث النقر
            itemView.setOnClickListener { onClick(file) }
            itemView.setOnLongClickListener { 
                onLongClick(file)
                true 
            }
        }
    }
}

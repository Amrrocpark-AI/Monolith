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
import android.view.animation.CycleInterpolator
import android.view.animation.TranslateAnimation
import android.widget.Button
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

        fileAdapter = FileAdapter(
            onFileClick = { file ->
                if (file.isDirectory) {
                    loadDirectory(file)
                } else if (file.name.endsWith(".zip", ignoreCase = true)) {
                    // فتح نافذة فك الضغط
                    handleZipFile(file)
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
    // محرك فك الضغط الحقيقي (The Extraction Engine)
    // ==========================================
    private fun handleZipFile(zipFile: File) {
        try {
            val zip = ZipFile(zipFile)
            if (zip.isEncrypted) {
                // إذا كان مشفراً، نطلب كلمة المرور
                showExtractPasswordDialog(zipFile)
            } else {
                // إذا كان عادياً، نفك الضغط مباشرة
                executeExtraction(zipFile, null)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "الملف ليس أرشيفاً صالحاً.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showExtractPasswordDialog(zipFile: File) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_extract_zip, null)
        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false) // لا يمكن إغلاقه إلا بزر الإلغاء
            .create()

        val tilPassword = dialogView.findViewById<TextInputLayout>(R.id.tilExtractPassword)
        val etPassword = dialogView.findViewById<TextInputEditText>(R.id.etExtractPassword)
        val tvError = dialogView.findViewById<TextView>(R.id.tvErrorText)
        val btnExtract = dialogView.findViewById<MaterialButton>(R.id.btnExtractZip)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnCancelExtract)

        btnCancel.setOnClickListener { alertDialog.dismiss() }

        btnExtract.setOnClickListener {
            val password = etPassword.text.toString()
            if (password.isEmpty()) {
                tvError.text = "الرجاء إدخال كلمة المرور"
                tvError.visibility = View.VISIBLE
                shakeView(tilPassword)
                return@setOnClickListener
            }

            // إخفاء الخطأ
            tvError.visibility = View.GONE
            tilPassword.boxStrokeColor = android.graphics.Color.parseColor("#1A1A1A") // لون افتراضي

            // محاولة الفك
            val success = executeExtraction(zipFile, password)
            if (success) {
                alertDialog.dismiss()
            } else {
                // كلمة مرور خاطئة!
                tvError.text = "كلمة المرور غير صحيحة، حاول مجدداً"
                tvError.visibility = View.VISIBLE
                tilPassword.boxStrokeColor = android.graphics.Color.RED
                shakeView(tilPassword)
            }
        }

        alertDialog.show()
    }

    private fun executeExtraction(zipFile: File, password: String?): Boolean {
        var isSuccess = false
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val destinationDir = File(zipFile.parentFile, zipFile.nameWithoutExtension)
                    if (!destinationDir.exists()) destinationDir.mkdirs()

                    val zip = ZipFile(zipFile)
                    if (password != null) {
                        zip.setPassword(password.toCharArray())
                    }
                    zip.extractAll(destinationDir.absolutePath)
                    isSuccess = true
                }

                if (isSuccess) {
                    Toast.makeText(this@FileManagerActivity, "تم فك الضغط بنجاح!", Toast.LENGTH_SHORT).show()
                    loadDirectory(currentDirectory)
                }
            } catch (e: ZipException) {
                // خطأ في كلمة المرور أو الملف
                isSuccess = false
            } catch (e: Exception) {
                isSuccess = false
            }
        }
        return isSuccess
    }

    // تأثير الاهتزاز (Shake Animation)
    private fun shakeView(view: View) {
        val shake = TranslateAnimation(0f, 10f, 0f, 0f)
        shake.duration = 150
        shake.interpolator = CycleInterpolator(7f)
        view.startAnimation(shake)
    }

    // ==========================================
    // نافذة إنشاء ZIP
    // ==========================================
    private fun showCreateZipDialog(fileToZip: File) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_zip, null)
        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        val etZipName = dialogView.findViewById<EditText>(R.id.etZipName)
        val etZipPassword = dialogView.findViewById<TextInputEditText>(R.id.etZipPassword)
        val tvPasswordStrength = dialogView.findViewById<TextView>(R.id.tvPasswordStrength)
        val btnCreate = dialogView.findViewById<MaterialButton>(R.id.btnCreateZip)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnCancelZip)

        etZipName.setText(fileToZip.nameWithoutExtension)

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
                        tvPasswordStrength.text = "ضعيفة جداً"
                        tvPasswordStrength.setTextColor(android.graphics.Color.RED)
                    }
                    password.length < 10 -> {
                        tvPasswordStrength.text = "متوسطة"
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

    private fun executeZipCreation(sourceFile: File, zipName: String, password: String) {
        Toast.makeText(this, "جاري الضغط...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val destinationFile = File(currentDirectory, "$zipName.zip")
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

                    if (sourceFile.isDirectory) {
                        zipFile.addFolder(sourceFile, parameters)
                    } else {
                        zipFile.addFile(sourceFile, parameters)
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@FileManagerActivity, "تم إنشاء الأرشيف بنجاح!", Toast.LENGTH_LONG).show()
                    loadDirectory(currentDirectory)
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
// محول القائمة
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
                    } else if (file.name.endsWith(".zip", ignoreCase = true)) {
                        ivFileIcon.setImageResource(android.R.drawable.ic_menu_save) // أيقونة أرشيف
                        ivFileIcon.setColorFilter(android.graphics.Color.parseColor("#FFAA00")) // لون برتقالي للأرشيف
                    } else {
                        ivFileIcon.setImageResource(android.R.drawable.ic_menu_agenda)
                    }
                    tvFileDetails.text = "$sizeString • $dateString"
                }
            }

            itemView.setOnClickListener { onClick(file) }
            itemView.setOnLongClickListener { 
                onLongClick(file)
                true 
            }
        }
    }
}

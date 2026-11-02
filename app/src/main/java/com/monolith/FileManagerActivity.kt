package com.monolith

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
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
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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

        fileAdapter = FileAdapter { file ->
            if (file.isDirectory) {
                loadDirectory(file)
            } else {
                Toast.makeText(this, "فتح الملف: ${file.name}", Toast.LENGTH_SHORT).show()
            }
        }
        
        // الإعداد الافتراضي: عرض قائمة (List)
        rvFiles.layoutManager = LinearLayoutManager(this)
        rvFiles.adapter = fileAdapter

        // منطق التبويبات (ملفات / معرض)
        tabLayoutFiles.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> { // تبويب الملفات
                        isGalleryMode = false
                        rvFiles.layoutManager = LinearLayoutManager(this@FileManagerActivity)
                        applyFilter(etSearchFiles.text.toString())
                    }
                    1 -> { // تبويب المعرض
                        isGalleryMode = true
                        rvFiles.layoutManager = GridLayoutManager(this@FileManagerActivity, 3)
                        applyFilter(etSearchFiles.text.toString())
                    }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        // منطق البحث
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

    // دالة الفلترة (تعمل مع الملفات والصور)
    private fun applyFilter(query: String) {
        val filteredFiles = if (query.isEmpty()) {
            allFiles
        } else {
            allFiles.filter { it.name.contains(query, ignoreCase = true) }
        }

        if (isGalleryMode) {
            // فلترة الصور فقط في وضع المعرض
            val imageFiles = filteredFiles.filter { 
                it.extension.lowercase() in listOf("jpg", "jpeg", "png", "gif", "webp") 
            }
            fileAdapter.submitList(imageFiles)
        } else {
            fileAdapter.submitList(filteredFiles)
        }
    }
}

// محول القائمة (تم تعديله ليدعم الأيقونات الخطية الأنيقة)
class FileAdapter(private val onFileClick: (File) -> Unit) : RecyclerView.Adapter<FileAdapter.FileViewHolder>() {
    private var files: List<File> = emptyList()
    fun submitList(newFiles: List<File>) { files = newFiles; notifyDataSetChanged() }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val layout = android.widget.LinearLayout(parent.context).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(32, 32, 32, 32)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val icon = ImageView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(56, 56)
            setColorFilter(android.graphics.Color.parseColor("#00D4FF"))
        }
        val textLayout = android.widget.LinearLayout(parent.context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(24, 0, 0, 0)
            layoutParams = android.widget.LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val titleText = TextView(parent.context).apply {
            setTextColor(android.graphics.Color.WHITE)
            textSize = 16f
        }
        val subtitleText = TextView(parent.context).apply {
            setTextColor(android.graphics.Color.parseColor("#666666"))
            textSize = 12f
        }
        textLayout.addView(titleText)
        textLayout.addView(subtitleText)
        layout.addView(icon)
        layout.addView(textLayout)
        
        return FileViewHolder(layout, icon, titleText, subtitleText)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) = holder.bind(files[position], onFileClick)
    override fun getItemCount(): Int = files.size

    class FileViewHolder(itemView: android.view.View, private val icon: ImageView, private val title: TextView, private val subtitle: TextView) : RecyclerView.ViewHolder(itemView) {
        fun bind(file: File, onClick: (File) -> Unit) {
            title.text = file.name
            if (file.isDirectory) {
                icon.setImageResource(android.R.drawable.ic_menu_set_as)
                subtitle.text = "مجلد"
            } else {
                icon.setImageResource(android.R.drawable.ic_menu_agenda)
                // عرض حجم الملف (تقريبي)
                val sizeInKb = file.length() / 1024
                subtitle.text = if (sizeInKb > 1024) "${sizeInKb / 1024} MB" else "$sizeInKb KB"
            }
            itemView.setOnClickListener { onClick(file) }
        }
    }
}

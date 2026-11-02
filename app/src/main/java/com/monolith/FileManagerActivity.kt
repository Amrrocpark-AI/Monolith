package com.monolith

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class FileManagerActivity : AppCompatActivity() {

    private lateinit var rvFiles: RecyclerView
    private lateinit var tvCurrentPath: TextView
    private lateinit var fileAdapter: FileAdapter
    private var currentDirectory: File = Environment.getExternalStorageDirectory()

    // طلب إذن الوصول الكامل للملفات
    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Environment.isExternalStorageManager()) {
            loadDirectory(currentDirectory)
        } else {
            Toast.makeText(this, "نحتاج إذن الوصول للملفات لعمل مدير الملفات.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_manager)

        rvFiles = findViewById(R.id.rvFiles)
        tvCurrentPath = findViewById(R.id.tvCurrentPath)
        val btnBack = findViewById<ImageButton>(R.id.btnBackFileManager)
        val fabCreateFolder = findViewById<FloatingActionButton>(R.id.fabCreateFolder)

        fileAdapter = FileAdapter { file ->
            if (file.isDirectory) {
                loadDirectory(file)
            } else {
                Toast.makeText(this, "فتح الملف: ${file.name}", Toast.LENGTH_SHORT).show()
            }
        }
        rvFiles.layoutManager = LinearLayoutManager(this)
        rvFiles.adapter = fileAdapter

        btnBack.setOnClickListener {
            val parent = currentDirectory.parentFile
            if (parent != null) loadDirectory(parent) else finish()
        }

        fabCreateFolder.setOnClickListener {
            Toast.makeText(this, "ميزة إنشاء المجلد قريباً...", Toast.LENGTH_SHORT).show()
        }

        // التحقق من الإذن عند فتح الشاشة
        checkAndRequestPermission()
    }

    private fun checkAndRequestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                loadDirectory(currentDirectory)
            } else {
                // فتح شاشة الإعدادات لطلب إذن الوصول الكامل
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.addCategory("android.intent.category.DEFAULT")
                intent.data = Uri.parse(String.format("package:%s", applicationContext.packageName))
                requestPermissionLauncher.launch(intent)
            }
        } else {
            // لأندرويد 10 وما دونه، الصلاحيات القديمة تكفي
            loadDirectory(currentDirectory)
        }
    }

    private fun loadDirectory(directory: File) {
        currentDirectory = directory
        tvCurrentPath.text = directory.absolutePath

        lifecycleScope.launch {
            val files = try {
                withContext(Dispatchers.IO) {
                    directory.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                } ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
            withContext(Dispatchers.Main) {
                fileAdapter.submitList(files)
            }
        }
    }
}

// محول القائمة (كما هو، لم يتغير)
class FileAdapter(private val onFileClick: (File) -> Unit) : RecyclerView.Adapter<FileAdapter.FileViewHolder>() {
    private var files: List<File> = emptyList()
    fun submitList(newFiles: List<File>) { files = newFiles; notifyDataSetChanged() }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val layout = android.widget.LinearLayout(parent.context).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(32, 32, 32, 32)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val icon = android.widget.ImageView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(60, 60)
            setColorFilter(android.graphics.Color.parseColor("#00D4FF"))
        }
        val text = TextView(parent.context).apply {
            setTextColor(android.graphics.Color.WHITE); textSize = 16f; setPadding(32, 0, 0, 0)
        }
        layout.addView(icon); layout.addView(text)
        return FileViewHolder(layout, icon, text)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) = holder.bind(files[position], onFileClick)
    override fun getItemCount(): Int = files.size

    class FileViewHolder(itemView: View, private val icon: android.widget.ImageView, private val text: TextView) : RecyclerView.ViewHolder(itemView) {
        fun bind(file: File, onClick: (File) -> Unit) {
            text.text = file.name
            icon.setImageResource(if (file.isDirectory) android.R.drawable.ic_menu_set_as else android.R.drawable.ic_menu_agenda)
            itemView.setOnClickListener { onClick(file) }
        }
    }
}

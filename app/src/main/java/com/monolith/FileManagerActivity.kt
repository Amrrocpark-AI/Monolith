package com.monolith

import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
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
    
    // المسار الافتراضي (الذاكرة الداخلية)
    private var currentDirectory: File = Environment.getExternalStorageDirectory()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_manager)

        // ربط العناصر
        rvFiles = findViewById(R.id.rvFiles)
        tvCurrentPath = findViewById(R.id.tvCurrentPath)
        val btnBack = findViewById<ImageButton>(R.id.btnBackFileManager)
        val fabCreateFolder = findViewById<FloatingActionButton>(R.id.fabCreateFolder)

        // إعداد قائمة الملفات
        fileAdapter = FileAdapter { file ->
            if (file.isDirectory) {
                loadDirectory(file) // الدخول للمجلد
            } else {
                Toast.makeText(this, "فتح الملف: ${file.name}", Toast.LENGTH_SHORT).show()
                // هنا سنضع كود فتح الملف لاحقاً
            }
        }
        rvFiles.layoutManager = LinearLayoutManager(this)
        rvFiles.adapter = fileAdapter

        // زر الرجوع (يرجع للمجلد الأب)
        btnBack.setOnClickListener {
            val parent = currentDirectory.parentFile
            if (parent != null) {
                loadDirectory(parent)
            } else {
                finish() // إذا كنا في الجذر، أغلق الشاشة
            }
        }

        // زر إنشاء مجلد
        fabCreateFolder.setOnClickListener {
            Toast.makeText(this, "ميزة إنشاء المجلد قريباً...", Toast.LENGTH_SHORT).show()
        }

        // تحميل المجلد الأولي
        loadDirectory(currentDirectory)
    }

    private fun loadDirectory(directory: File) {
        currentDirectory = directory
        tvCurrentPath.text = directory.absolutePath

        // قراءة الملفات في الخلفية (خيط منفصل) لكي لا يتجمد التطبيق
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

// ==========================================
// محول قائمة الملفات (مع الحماية من الأخطاء)
// ==========================================
class FileAdapter(private val onFileClick: (File) -> Unit) : RecyclerView.Adapter<FileAdapter.FileViewHolder>() {

    private var files: List<File> = emptyList()

    fun submitList(newFiles: List<File>) {
        files = newFiles
        notifyDataSetChanged() // يمكن تحسينها باستخدام DiffUtil لاحقاً لسرعة أكبر
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        // هنا نستخدم تصميم بسيط مبرمجياً لتوفير الوقت (سنستبدله بتصميم XML لاحقاً)
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
            setTextColor(android.graphics.Color.WHITE)
            textSize = 16f
            setPadding(32, 0, 0, 0)
        }
        layout.addView(icon)
        layout.addView(text)
        return FileViewHolder(layout, icon, text)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val file = files[position]
        holder.bind(file, onFileClick)
    }

    override fun getItemCount(): Int = files.size

    class FileViewHolder(itemView: View, private val icon: android.widget.ImageView, private val text: TextView) : RecyclerView.ViewHolder(itemView) {
        fun bind(file: File, onClick: (File) -> Unit) {
            text.text = file.name
            if (file.isDirectory) {
                icon.setImageResource(android.R.drawable.ic_menu_set_as)
            } else {
                icon.setImageResource(android.R.drawable.ic_menu_agenda)
            }
            itemView.setOnClickListener { onClick(file) }
        }
    }
}

package com.monolith

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Button
import android.widget.ProgressBar // [إضافة جديدة]
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope // [إضافة جديدة]
import kotlinx.coroutines.Dispatchers // [إضافة جديدة]
import kotlinx.coroutines.launch // [إضافة جديدة]
import kotlinx.coroutines.withContext // [إضافة جديدة]
import java.io.IOException

class SurgicalLensActivity : AppCompatActivity() {

    private lateinit var surgicalMaskView: SurgicalMaskView
    private lateinit var ivImage: ImageView
    private lateinit var seekBar: SeekBar
    private lateinit var tvValue: TextView
    private lateinit var btnEraser: ImageButton
    private lateinit var btnMagicWand: ImageButton // [إضافة جديدة]
    private lateinit var progressBar: ProgressBar // [إضافة جديدة]

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) loadImage(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_surgical_lens)

        surgicalMaskView = findViewById(R.id.surgicalMaskView)
        ivImage = findViewById(R.id.ivSurgicalImage)
        seekBar = findViewById(R.id.seekBarSurgical)
        tvValue = findViewById(R.id.tvSurgicalValue)
        btnEraser = findViewById(R.id.btnEraserSurgical)
        btnMagicWand = findViewById(R.id.btnMagicWandSurgical) // [إضافة جديدة]
        progressBar = findViewById(R.id.progressBarSurgical) // [إضافة جديدة]

        // 1. إغلاق الشاشة
        findViewById<ImageButton>(R.id.btnCloseSurgical).setOnClickListener { finish() }

        // 2. اختيار الصورة
        findViewById<Button>(R.id.btnPickSurgicalImage).setOnClickListener {
            pickImage.launch("image/*")
        }

        // 3. التراجع والإعادة
        findViewById<ImageButton>(R.id.btnUndoSurgical).setOnClickListener {
            surgicalMaskView.undo()
        }
        findViewById<ImageButton>(R.id.btnRedoSurgical).setOnClickListener {
            surgicalMaskView.redo()
        }

        // 4. تفعيل الممحاة
        btnEraser.setOnClickListener {
            surgicalMaskView.isEraser = true
            surgicalMaskView.isMagicWand = false // [إضافة جديدة]
            btnEraser.setColorFilter(0xFF26A69A.toInt())
            btnMagicWand.setColorFilter(0xFFFFFFFF.toInt()) // [إضافة جديدة]
        }

        // [إضافة جديدة] 4.1 تفعيل العصا السحرية
        btnMagicWand.setOnClickListener {
            surgicalMaskView.isMagicWand = true
            surgicalMaskView.isEraser = false
            btnMagicWand.setColorFilter(0xFF26A69A.toInt())
            btnEraser.setColorFilter(0xFFFFFFFF.toInt())
        }

        // 5. التحكم في شريط التمرير (تم تعديله ليدعم العصا السحرية)
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                tvValue.text = progress.toString()
                if (surgicalMaskView.isMagicWand) {
                    surgicalMaskView.tolerance = progress // [إضافة جديدة]
                } else {
                    surgicalMaskView.brushSize = progress.toFloat() * 2f
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // 6. أزرار التطبيق والحفظ
        findViewById<Button>(R.id.btnApplySurgical).setOnClickListener {
            // سيتم برمجة منطق القص هنا لاحقاً
        }

        // [إضافة جديدة] 6.1 ربط زر الحفظ بدالة القص
        findViewById<Button>(R.id.btnSaveSurgical).setOnClickListener {
            savePNG()
        }
    }

    private fun loadImage(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            ivImage.visibility = View.GONE // [إضافة جديدة] إخفاء الصورة القديمة لأن المحرك سيرسمها
            surgicalMaskView.setImageBitmap(bitmap) // [إضافة جديدة] إرسال الصورة للمحرك
            findViewById<Button>(R.id.btnPickSurgicalImage).visibility = View.GONE
        } catch (e: IOException) {
            // التطبيق صامت تماماً، لا رسائل خطأ
        }
    }

    // [إضافة جديدة] دالة القص والحفظ
    private fun savePNG() {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.Default) {
            val finalBitmap = surgicalMaskView.exportPNG()
            if (finalBitmap != null) {
                writeToGallery(finalBitmap)
            }
            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                finish()
            }
        }
    }

    // [إضافة جديدة] دالة الحفظ في المعرض
    private fun writeToGallery(bmp: Bitmap) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Monolith_PNG_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Monolith")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
            try {
                resolver.openOutputStream(uri)?.use {
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                    resolver.update(uri, done, null, null)
                }
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
            }
        }
    }
}

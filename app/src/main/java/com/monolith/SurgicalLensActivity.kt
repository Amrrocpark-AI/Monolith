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
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class SurgicalLensActivity : AppCompatActivity() {

    private lateinit var surgicalMaskView: SurgicalMaskView
    private lateinit var seekBar: SeekBar
    private lateinit var tvValue: TextView
    private lateinit var btnEraser: ImageButton
    private lateinit var btnMagicWand: ImageButton
    private lateinit var progressBar: ProgressBar
    private lateinit var btnPick: Button

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) loadImage(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_surgical_lens)

        surgicalMaskView = findViewById(R.id.surgicalMaskView)
        seekBar = findViewById(R.id.seekBarSurgical)
        tvValue = findViewById(R.id.tvSurgicalValue)
        btnEraser = findViewById(R.id.btnEraserSurgical)
        btnMagicWand = findViewById(R.id.btnMagicWandSurgical)
        progressBar = findViewById(R.id.progressBarSurgical)
        btnPick = findViewById(R.id.btnPickSurgicalImage)

        findViewById<ImageButton>(R.id.btnCloseSurgical).setOnClickListener { finish() }
        
        // زر تبديل الصورة
        btnPick.setOnClickListener { pickImage.launch("image/*") }

        // تفعيل الممحاة
        btnEraser.setOnClickListener {
            surgicalMaskView.isEraser = true
            surgicalMaskView.isMagicWand = false
            btnEraser.setColorFilter(0xFF26A69A.toInt())
            btnMagicWand.setColorFilter(0xFFFFFFFF.toInt())
        }

        // تفعيل العصا السحرية
        btnMagicWand.setOnClickListener {
            surgicalMaskView.isMagicWand = true
            surgicalMaskView.isEraser = false
            btnMagicWand.setColorFilter(0xFF26A69A.toInt())
            btnEraser.setColorFilter(0xFFFFFFFF.toInt())
        }

        // شريط التمرير
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                tvValue.text = progress.toString()
                if (surgicalMaskView.isMagicWand) {
                    surgicalMaskView.tolerance = progress
                } else {
                    surgicalMaskView.brushSize = progress.toFloat() * 2f
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // زر التطبيق (Apply) - يطبق القص فوراً لتراه أمامك
        findViewById<Button>(R.id.btnApplySurgical).setOnClickListener {
            if (surgicalMaskView.hasMask()) {
                surgicalMaskView.applyClip()
            }
        }

        // زر الحفظ - يحفظ النتيجة في معرض الهاتف
        findViewById<Button>(R.id.btnSaveSurgical).setOnClickListener {
            savePNG()
        }
    }

    private fun loadImage(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            btnPick.visibility = View.GONE // إخفاء زر الاختيار بعد تحميل الصورة
            surgicalMaskView.setImageBitmap(bitmap)
        } catch (e: IOException) { }
    }

    private fun savePNG() {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.Default) {
            val finalBitmap = surgicalMaskView.exportPNG()
            if (finalBitmap != null) {
                writeToGallery(finalBitmap)
            }
            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                Toast.makeText(this@SurgicalLensActivity, "تم الحفظ في معرض Monolith", Toast.LENGTH_SHORT).show()
            }
        }
    }

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

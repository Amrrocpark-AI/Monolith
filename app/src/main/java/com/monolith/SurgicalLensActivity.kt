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
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
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
    private lateinit var btnAiCutout: ImageButton
    private lateinit var btnVault: ImageButton
    private lateinit var progressBar: ProgressBar
    private lateinit var btnPick: Button
    private var currentBitmap: Bitmap? = null

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
        btnAiCutout = findViewById(R.id.btnAiCutoutSurgical)
        btnVault = findViewById(R.id.btnVaultSurgical)
        progressBar = findViewById(R.id.progressBarSurgical)
        btnPick = findViewById(R.id.btnPickSurgicalImage)

        findViewById<ImageButton>(R.id.btnCloseSurgical).setOnClickListener { finish() }
        btnPick.setOnClickListener { pickImage.launch("image/*") }

        // إعداد ألوان الأزرار (مفعلة / غير مفعلة)
        btnEraser.setOnClickListener {
            surgicalMaskView.isEraser = true
            surgicalMaskView.isMagicWand = false
            btnEraser.setColorFilter(0xFF26A69A.toInt()) // أخضر (مفعل)
            btnMagicWand.setColorFilter(0xFFFFFFFF.toInt()) // أبيض (غير مفعل)
        }

        btnMagicWand.setOnClickListener {
            surgicalMaskView.isMagicWand = true
            surgicalMaskView.isEraser = false
            btnMagicWand.setColorFilter(0xFF26A69A.toInt()) // أخضر (مفعل)
            btnEraser.setColorFilter(0xFFFFFFFF.toInt()) // أبيض (غير مفعل)
        }

        // زر الذكاء الاصطناعي (إجراء - لا يبقى مفعل)
        btnAiCutout.setOnClickListener {
            performAiCutout()
        }

        // زر المحفوظات (إجراء - يفتح النافذة)
        btnVault.setOnClickListener {
            val vaultDialog = StickerVaultDialog()
            vaultDialog.onStickerSelected = { file ->
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                if (bitmap != null) {
                    surgicalMaskView.setOverlayBitmap(bitmap)
                    Toast.makeText(this, "تمت الإضافة. يمكنك سحبها بإصبعك.", Toast.LENGTH_SHORT).show()
                }
            }
            vaultDialog.show(supportFragmentManager, "StickerVault")
        }

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

        findViewById<Button>(R.id.btnApplySurgical).setOnClickListener {
            if (surgicalMaskView.hasMask()) {
                surgicalMaskView.applyClip()
            }
        }

        findViewById<Button>(R.id.btnSaveSurgical).setOnClickListener {
            savePNG()
        }
    }

    private fun loadImage(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            btnPick.visibility = View.GONE
            currentBitmap = bitmap
            surgicalMaskView.setImageBitmap(bitmap)
        } catch (e: IOException) { }
    }

    private fun performAiCutout() {
        val bitmap = currentBitmap ?: return
        progressBar.visibility = View.VISIBLE
        
        val image = InputImage.fromBitmap(bitmap, 0)
        val options = SubjectSegmenterOptions.Builder()
            .enableForegroundBitmap()
            .build()
        val segmenter = SubjectSegmentation.getClient(options)

        segmenter.process(image)
            .addOnSuccessListener { result ->
                val foregroundBitmap = result.foregroundBitmap
                if (foregroundBitmap != null) {
                    currentBitmap = foregroundBitmap
                    surgicalMaskView.setImageBitmap(foregroundBitmap)
                }
                progressBar.visibility = View.GONE
            }
            .addOnFailureListener {
                progressBar.visibility = View.GONE
            }
    }

    private fun savePNG() {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.Default) {
            val finalBitmap = surgicalMaskView.exportPNG() ?: currentBitmap
            if (finalBitmap != null) {
                writeToGallery(finalBitmap)
                // حفظ باسم PNG_ احترافي بدلاً من "شعار"
                VaultManager.saveSticker(this@SurgicalLensActivity, finalBitmap, "PNG_${System.currentTimeMillis()}")
            }
            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                Toast.makeText(this@SurgicalLensActivity, "تم الحفظ في المعرض والخزنة", Toast.LENGTH_SHORT).show()
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

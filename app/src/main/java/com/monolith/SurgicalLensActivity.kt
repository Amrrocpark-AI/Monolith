package com.monolith

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
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
    private lateinit var btnProtection: ImageButton
    private lateinit var btnColorEngine: ImageButton
    private lateinit var btnLoupe: ImageButton // [جديد] زر العدسة
    private lateinit var btnLoupePin: ImageButton // [جديد] زر تثبيت العدسة
    private lateinit var progressBar: ProgressBar
    private lateinit var btnPick: Button
    private var currentBitmap: Bitmap? = null

    // ألوان الواجهة (سماوي وأبيض فقط)
    private val colorActive = Color.parseColor("#00D4FF") // سماوي
    private val colorInactive = Color.parseColor("#FFFFFF") // أبيض

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
        btnProtection = findViewById(R.id.btnProtectionSurgical)
        btnColorEngine = findViewById(R.id.btnColorEngineSurgical)
        
        // [جديد] ربط أزرار العدسة
        btnLoupe = findViewById(R.id.btnLoupeSurgical) 
        btnLoupePin = findViewById(R.id.btnLoupePinSurgical)
        
        progressBar = findViewById(R.id.progressBarSurgical)
        btnPick = findViewById(R.id.btnPickSurgicalImage)

        findViewById<ImageButton>(R.id.btnCloseSurgical).setOnClickListener { finish() }
        btnPick.setOnClickListener { pickImage.launch("image/*") }

        findViewById<ImageButton>(R.id.btnUndoSurgical).setOnClickListener { surgicalMaskView.undo() }
        findViewById<ImageButton>(R.id.btnRedoSurgical).setOnClickListener { surgicalMaskView.redo() }

        // الممحاة
        btnEraser.setOnClickListener {
            surgicalMaskView.isEraser = true
            surgicalMaskView.isMagicWand = false
            surgicalMaskView.isProtectionMode = false
            btnEraser.setColorFilter(colorActive)
            btnMagicWand.setColorFilter(colorInactive)
            btnProtection.setColorFilter(colorInactive)
        }

        // العصا السحرية
        btnMagicWand.setOnClickListener {
            surgicalMaskView.isMagicWand = true
            surgicalMaskView.isEraser = false
            surgicalMaskView.isProtectionMode = false
            btnMagicWand.setColorFilter(colorActive)
            btnEraser.setColorFilter(colorInactive)
            btnProtection.setColorFilter(colorInactive)
        }

        // جدار الحماية
        btnProtection.setOnClickListener {
            surgicalMaskView.isProtectionMode = !surgicalMaskView.isProtectionMode
            surgicalMaskView.isEraser = false
            surgicalMaskView.isMagicWand = false
            
            btnProtection.setColorFilter(if (surgicalMaskView.isProtectionMode) colorActive else colorInactive)
            btnEraser.setColorFilter(colorInactive)
            btnMagicWand.setColorFilter(colorInactive)
        }

        // فتح نافذة الألوان
        btnColorEngine.setOnClickListener {
            val colorDialog = ColorEngineDialog()
            colorDialog.onColorsSelected = { rules ->
                surgicalMaskView.setColorRules(rules)
            }
            colorDialog.show(supportFragmentManager, "ColorEngine")
        }

        // [جديد] تفعيل/إلغاء العدسة
        btnLoupe.setOnClickListener {
            surgicalMaskView.isLoupeEnabled = !surgicalMaskView.isLoupeEnabled
            btnLoupe.setColorFilter(if (surgicalMaskView.isLoupeEnabled) colorActive else colorInactive)
            // إذا أطفأنا العدسة، نلغي التثبيت
            if (!surgicalMaskView.isLoupeEnabled) {
                surgicalMaskView.isLoupePinned = false
                btnLoupePin.setColorFilter(colorInactive)
            }
        }

        // [جديد] تثبيت العدسة في الزاوية
        btnLoupePin.setOnClickListener {
            if (surgicalMaskView.isLoupeEnabled) {
                surgicalMaskView.isLoupePinned = !surgicalMaskView.isLoupePinned
                btnLoupePin.setColorFilter(if (surgicalMaskView.isLoupePinned) colorActive else colorInactive)
            }
        }

        btnAiCutout.setOnClickListener { performAiCutout() }

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

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
    private lateinit var loupeView: LoupeView
    private lateinit var seekBar: SeekBar
    private lateinit var tvValue: TextView
    private lateinit var btnEraser: ImageButton
    private lateinit var btnMagicWand: ImageButton
    private lateinit var btnAiCutout: ImageButton
    private lateinit var btnVault: ImageButton
    private lateinit var btnProtection: ImageButton
    private lateinit var btnColorEngine: ImageButton
    private lateinit var btnColorAssist: ImageButton
    private lateinit var btnEyedropper: ImageButton
    private lateinit var btnLoupe: ImageButton
    private lateinit var btnFreeze: ImageButton
    private lateinit var btnMaskVisibility: ImageButton
    private lateinit var btnHand: ImageButton
    private lateinit var progressBar: ProgressBar
    private lateinit var btnPick: Button
    private var currentBitmap: Bitmap? = null

    private val colorActive = Color.parseColor("#00D4FF")
    private val colorInactive = Color.parseColor("#FFFFFF")

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) loadImage(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_surgical_lens)

        surgicalMaskView = findViewById(R.id.surgicalMaskView)
        loupeView = findViewById(R.id.loupeView)
        seekBar = findViewById(R.id.seekBarSurgical)
        tvValue = findViewById(R.id.tvSurgicalValue)
        btnEraser = findViewById(R.id.btnEraserSurgical)
        btnMagicWand = findViewById(R.id.btnMagicWandSurgical)
        btnAiCutout = findViewById(R.id.btnAiCutoutSurgical)
        btnVault = findViewById(R.id.btnVaultSurgical)
        btnProtection = findViewById(R.id.btnProtectionSurgical)
        btnColorEngine = findViewById(R.id.btnColorEngineSurgical)
        btnColorAssist = findViewById(R.id.btnColorAssistSurgical)
        btnEyedropper = findViewById(R.id.btnEyedropperSurgical)
        btnLoupe = findViewById(R.id.btnLoupeSurgical)
        btnFreeze = findViewById(R.id.btnFreezeSurgical)
        btnMaskVisibility = findViewById(R.id.btnMaskVisibilitySurgical)
        btnHand = findViewById(R.id.btnHandSurgical)
        progressBar = findViewById(R.id.progressBarSurgical)
        btnPick = findViewById(R.id.btnPickSurgicalImage)

        findViewById<ImageButton>(R.id.btnCloseSurgical).setOnClickListener { finish() }
        btnPick.setOnClickListener { pickImage.launch("image/*") }

        findViewById<ImageButton>(R.id.btnUndoSurgical).setOnClickListener { surgicalMaskView.undo() }
        findViewById<ImageButton>(R.id.btnRedoSurgical).setOnClickListener { surgicalMaskView.redo() }

        // تفعيل وضع اليد افتراضياً
        surgicalMaskView.isNavigationMode = true
        btnHand.setColorFilter(colorActive)

        btnHand.setOnClickListener {
            surgicalMaskView.isNavigationMode = true
            surgicalMaskView.isEraser = false
            surgicalMaskView.isMagicWand = false
            surgicalMaskView.isProtectionMode = false
            surgicalMaskView.isColorAssistMode = false
            
            btnHand.setColorFilter(colorActive)
            btnEraser.setColorFilter(colorInactive)
            btnMagicWand.setColorFilter(colorInactive)
            btnProtection.setColorFilter(colorInactive)
            btnColorAssist.setColorFilter(colorInactive)
        }

        btnEraser.setOnClickListener {
            surgicalMaskView.isNavigationMode = false
            surgicalMaskView.isEraser = true
            surgicalMaskView.isMagicWand = false
            surgicalMaskView.isProtectionMode = false
            surgicalMaskView.isColorAssistMode = false
            
            btnEraser.setColorFilter(colorActive)
            btnHand.setColorFilter(colorInactive)
            btnMagicWand.setColorFilter(colorInactive)
            btnProtection.setColorFilter(colorInactive)
            btnColorAssist.setColorFilter(colorInactive)
        }

        btnMagicWand.setOnClickListener {
            surgicalMaskView.isNavigationMode = false
            surgicalMaskView.isMagicWand = true
            surgicalMaskView.isEraser = false
            surgicalMaskView.isProtectionMode = false
            surgicalMaskView.isColorAssistMode = false
            
            btnMagicWand.setColorFilter(colorActive)
            btnHand.setColorFilter(colorInactive)
            btnEraser.setColorFilter(colorInactive)
            btnProtection.setColorFilter(colorInactive)
            btnColorAssist.setColorFilter(colorInactive)
        }

        btnProtection.setOnClickListener {
            surgicalMaskView.isNavigationMode = false
            surgicalMaskView.isProtectionMode = !surgicalMaskView.isProtectionMode
            surgicalMaskView.isEraser = false
            surgicalMaskView.isMagicWand = false
            surgicalMaskView.isColorAssistMode = false
            
            btnProtection.setColorFilter(if (surgicalMaskView.isProtectionMode) colorActive else colorInactive)
            btnHand.setColorFilter(colorInactive)
            btnEraser.setColorFilter(colorInactive)
            btnMagicWand.setColorFilter(colorInactive)
            btnColorAssist.setColorFilter(colorInactive)
        }

        btnColorAssist.setOnClickListener {
            surgicalMaskView.isNavigationMode = false
            surgicalMaskView.isColorAssistMode = !surgicalMaskView.isColorAssistMode
            surgicalMaskView.isEraser = false
            surgicalMaskView.isMagicWand = false
            surgicalMaskView.isProtectionMode = false
            
            btnColorAssist.setColorFilter(if (surgicalMaskView.isColorAssistMode) colorActive else colorInactive)
            btnHand.setColorFilter(colorInactive)
            btnEraser.setColorFilter(colorInactive)
            btnMagicWand.setColorFilter(colorInactive)
            btnProtection.setColorFilter(colorInactive)
        }

        btnEyedropper.setOnClickListener {
            Toast.makeText(this, "ميزة القطارة قيد التطوير", Toast.LENGTH_SHORT).show()
        }

        btnColorEngine.setOnClickListener {
            surgicalMaskView.isNavigationMode = true
            btnHand.setColorFilter(colorActive)
            btnEraser.setColorFilter(colorInactive)
            btnMagicWand.setColorFilter(colorInactive)
            btnProtection.setColorFilter(colorInactive)
            btnColorAssist.setColorFilter(colorInactive)

            val colorDialog = ColorEngineDialog()
            colorDialog.onColorsSelected = { rules ->
                surgicalMaskView.setColorRules(rules)
            }
            colorDialog.show(supportFragmentManager, "ColorEngine")
        }

        btnLoupe.setOnClickListener {
            val isEnabled = !loupeView.isLoupeEnabled()
            loupeView.setLoupeEnabled(isEnabled)
            btnLoupe.setColorFilter(if (isEnabled) colorActive else colorInactive)
        }

        btnFreeze.setOnClickListener {
            surgicalMaskView.isFrozen = !surgicalMaskView.isFrozen
            btnFreeze.setColorFilter(if (surgicalMaskView.isFrozen) colorActive else colorInactive)
        }

        btnMaskVisibility.setOnClickListener {
            surgicalMaskView.isMaskVisible = !surgicalMaskView.isMaskVisible
            btnMaskVisibility.setColorFilter(if (surgicalMaskView.isMaskVisible) colorActive else colorInactive)
        }

        btnAiCutout.setOnClickListener {
            surgicalMaskView.isNavigationMode = true
            btnHand.setColorFilter(colorActive)
            btnEraser.setColorFilter(colorInactive)
            btnMagicWand.setColorFilter(colorInactive)
            btnProtection.setColorFilter(colorInactive)
            btnColorAssist.setColorFilter(colorInactive)
            
            performAiCutout() 
        }

        btnVault.setOnClickListener {
            val vaultDialog = StickerVaultDialog()
            vaultDialog.onStickerSelected = { file ->
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                if (bitmap != null) {
                    surgicalMaskView.setOverlayBitmap(bitmap)
                    Toast.makeText(this, "تمت الإضافة", Toast.LENGTH_SHORT).show()
                }
            }
            vaultDialog.show(supportFragmentManager, "StickerVault")
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                tvValue.text = progress.toString()
                if (surgicalMaskView.isMagicWand) {
                    surgicalMaskView.tolerance = progress
                } else if (loupeView.isLoupeEnabled()) {
                    loupeView.loupeRadius = 80f + (progress * 2f)
                } else {
                    surgicalMaskView.brushSize = progress.toFloat() * 2f
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        findViewById<Button>(R.id.btnApplySurgical).setOnClickListener {
            surgicalMaskView.applyClip()
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
            loupeView.setImage(bitmap, surgicalMaskView.getImageBounds())
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
                    surgicalMaskView.setAiMask(foregroundBitmap)
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
                Toast.makeText(this@SurgicalLensActivity, "تم الحفظ", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun writeToGallery(bmp: Bitmap) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Monolith_${System.currentTimeMillis()}.png")
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

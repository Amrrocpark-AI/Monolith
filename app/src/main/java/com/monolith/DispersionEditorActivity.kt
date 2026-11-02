package com.monolith

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.io.InputStream

class DispersionEditorActivity : AppCompatActivity() {

    private lateinit var ivDispersionImage: ImageView
    private lateinit var maskDrawingView: MaskDrawingView
    private lateinit var btnBrushTool: MaterialButton
    private lateinit var btnEraserTool: MaterialButton
    private lateinit var sbParticleSize: SeekBar
    private lateinit var btnPickImageForDispersion: Button

    private var selectedBitmap: Bitmap? = null

    // اختيار الصورة من المعرض
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream: InputStream? = contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                selectedBitmap = bitmap
                ivDispersionImage.setImageBitmap(bitmap)
                btnPickImageForDispersion.visibility = View.GONE // إخفاء الزر بعد اختيار الصورة
            } catch (e: Exception) {
                Toast.makeText(this, "فشل تحميل الصورة", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dispersion_editor)

        // ربط العناصر
        ivDispersionImage = findViewById(R.id.ivDispersionImage)
        maskDrawingView = findViewById(R.id.maskDrawingView)
        btnBrushTool = findViewById(R.id.btnBrushTool)
        btnEraserTool = findViewById(R.id.btnEraserTool)
        sbParticleSize = findViewById(R.id.sbParticleSize)
        btnPickImageForDispersion = findViewById(R.id.btnPickImageForDispersion)

        val btnClose = findViewById<ImageButton>(R.id.btnCloseDispersion)
        val btnUndo = findViewById<ImageButton>(R.id.btnUndoDispersion)
        val btnRedo = findViewById<ImageButton>(R.id.btnRedoDispersion)
        val btnApply = findViewById<Button>(R.id.btnApplyDispersion)

        // 1. اختيار الصورة
        btnPickImageForDispersion.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        // 2. أدوات الرسم (فرشاة وممحاة)
        btnBrushTool.setOnClickListener {
            maskDrawingView.setEraserMode(false)
            btnBrushTool.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#00D4FF"))
            btnBrushTool.setTextColor(Color.BLACK)
            
            btnEraserTool.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#333333"))
            btnEraserTool.setTextColor(Color.WHITE)
        }

        btnEraserTool.setOnClickListener {
            maskDrawingView.setEraserMode(true)
            btnEraserTool.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#00D4FF"))
            btnEraserTool.setTextColor(Color.BLACK)
            
            btnBrushTool.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#333333"))
            btnBrushTool.setTextColor(Color.WHITE)
        }

        // 3. حجم الفرشاة
        sbParticleSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // تحويل القيمة إلى حجم مناسب للفرشاة (من 10 إلى 100)
                val brushSize = (progress + 10).toFloat()
                maskDrawingView.setBrushSize(brushSize)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 4. أزرار التراجع والإعادة
        btnUndo.setOnClickListener { maskDrawingView.undo() }
        btnRedo.setOnClickListener { maskDrawingView.redo() }

        // 5. زر الإغلاق
        btnClose.setOnClickListener { finish() }

        // 6. زر التطبيق (مؤقتاً - سنربطه بمحرك التشتت لاحقاً)
        btnApply.setOnClickListener {
            if (selectedBitmap == null) {
                Toast.makeText(this, "الرجاء اختيار صورة أولاً", Toast.LENGTH_SHORT).show()
            } else {
                // هنا سنضع كود محرك التشتت الفعلي
                Toast.makeText(this, "جاري تجهيز محرك التشتت... (قيد التطوير)", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

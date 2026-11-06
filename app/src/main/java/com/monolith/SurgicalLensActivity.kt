package com.monolith

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.IOException

class SurgicalLensActivity : AppCompatActivity() {

    private lateinit var surgicalMaskView: SurgicalMaskView
    private lateinit var ivImage: ImageView
    private lateinit var seekBar: SeekBar
    private lateinit var tvValue: TextView
    private lateinit var btnEraser: ImageButton

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

        // 4. تفعيل الممحاة (بدون أي رسائل مزعجة)
        btnEraser.setOnClickListener {
            surgicalMaskView.isEraser = !surgicalMaskView.isEraser
            btnEraser.setColorFilter(if (surgicalMaskView.isEraser) 0xFF26A69A.toInt() else 0xFFFFFFFF.toInt())
        }

        // 5. التحكم في حجم الفرشاة
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                tvValue.text = progress.toString()
                surgicalMaskView.brushSize = progress.toFloat() * 2f
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // 6. أزرار التطبيق والحفظ (صامتة تماماً استعداداً للمرحلة القادمة)
        findViewById<Button>(R.id.btnApplySurgical).setOnClickListener {
            // سيتم برمجة منطق القص هنا لاحقاً
        }

        findViewById<Button>(R.id.btnSaveSurgical).setOnClickListener {
            // سيتم برمجة منطق الحفظ هنا لاحقاً
        }
    }

    private fun loadImage(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            ivImage.setImageBitmap(bitmap)
            findViewById<Button>(R.id.btnPickSurgicalImage).visibility = View.GONE
        } catch (e: IOException) {
            // التطبيق صامت تماماً، لا رسائل خطأ
        }
    }
}

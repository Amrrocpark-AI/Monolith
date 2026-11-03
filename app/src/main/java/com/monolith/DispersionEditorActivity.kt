package com.monolith

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import java.io.InputStream

class DispersionEditorActivity : AppCompatActivity() {

    private lateinit var ivDispersionImage: ImageView
    private lateinit var maskDrawingView: MaskDrawingView
    private lateinit var particleView: SovereignParticleView
    private lateinit var tabMode: TabLayout
    private lateinit var layoutBrushTools: View
    private lateinit var btnBrushTool: MaterialButton
    private lateinit var btnEraserTool: MaterialButton
    private lateinit var sbDispersionAmount: SeekBar
    private lateinit var sbParticleSize: SeekBar
    private lateinit var btnPickImageForDispersion: Button

    private var selectedBitmap: Bitmap? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream: InputStream? = contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                selectedBitmap = bitmap
                ivDispersionImage.setImageBitmap(bitmap)
                btnPickImageForDispersion.visibility = View.GONE
                
                // إعادة تعيين المحرك لكي يستعد للصورة الجديدة
                particleView.visibility = View.GONE
                ivDispersionImage.visibility = View.VISIBLE
                maskDrawingView.clearMask()
            } catch (e: Exception) {
                Toast.makeText(this, "فشل تحميل الصورة", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dispersion_editor)

        ivDispersionImage = findViewById(R.id.ivDispersionImage)
        maskDrawingView = findViewById(R.id.maskDrawingView)
        particleView = findViewById(R.id.particleView)
        tabMode = findViewById(R.id.tabModeDispersion)
        layoutBrushTools = findViewById(R.id.layoutBrushTools)
        btnBrushTool = findViewById(R.id.btnBrushTool)
        btnEraserTool = findViewById(R.id.btnEraserTool)
        sbDispersionAmount = findViewById(R.id.sbDispersionAmount)
        sbParticleSize = findViewById(R.id.sbParticleSize)
        btnPickImageForDispersion = findViewById(R.id.btnPickImageForDispersion)

        val btnClose = findViewById<ImageButton>(R.id.btnCloseDispersion)
        val btnUndo = findViewById<ImageButton>(R.id.btnUndoDispersion)
        val btnRedo = findViewById<ImageButton>(R.id.btnRedoDispersion)
        val btnApply = findViewById<Button>(R.id.btnApplyDispersion)

        btnPickImageForDispersion.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        // إعداد أزرار الفرشاة
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

        // إعداد التبويبات (التحكم في الأوضاع)
        tabMode.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> { // وضع الفرشاة
                        layoutBrushTools.visibility = View.VISIBLE
                        maskDrawingView.visibility = View.VISIBLE
                        particleView.visibility = View.GONE
                        ivDispersionImage.visibility = View.VISIBLE
                    }
                    1 -> { // الوضع اليدوي (السحب)
                        layoutBrushTools.visibility = View.GONE
                        maskDrawingView.visibility = View.GONE
                        ivDispersionImage.visibility = View.VISIBLE
                        particleView.visibility = View.VISIBLE
                        particleView.setMode(SovereignParticleView.Mode.MANUAL_SWIPE)
                        selectedBitmap?.let { particleView.setImages(it) }
                    }
                    2 -> { // وضع النص
                        layoutBrushTools.visibility = View.GONE
                        maskDrawingView.visibility = View.GONE
                        ivDispersionImage.visibility = View.GONE
                        particleView.visibility = View.VISIBLE
                        showTextInputDialog()
                    }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        // ربط أشرطة التمرير بالمحرك
        sbDispersionAmount.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                particleView.setDispersionPower((progress + 5).toFloat())
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        sbParticleSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val size = (progress + 2).toFloat()
                particleView.setParticleSize(size)
                maskDrawingView.setBrushSize(size * 5) // حجم الفرشاة يكبر مع حجم الشظايا
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnUndo.setOnClickListener { maskDrawingView.undo() }
        btnRedo.setOnClickListener { maskDrawingView.redo() }
        btnClose.setOnClickListener { finish() }

        // زر تطبيق (لتشغيل التشتت بناءً على القناع المرسوم في وضع الفرشاة)
        btnApply.setOnClickListener {
            if (selectedBitmap == null) {
                Toast.makeText(this, "الرجاء اختيار صورة أولاً", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (tabMode.selectedTabPosition == 0) {
                // وضع الفرشاة: نأخذ القناع ونمرره للمحرك
                val mask = maskDrawingView.getMaskBitmap()
                if (mask != null) {
                    ivDispersionImage.visibility = View.GONE
                    maskDrawingView.visibility = View.GONE
                    particleView.visibility = View.VISIBLE
                    particleView.setMode(SovereignParticleView.Mode.AUTO_MASK)
                    selectedBitmap?.let { particleView.setImages(it, mask) }
                } else {
                    Toast.makeText(this, "الرجاء تحديد منطقة بالفرشاة أولاً", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // نافذة إدخال النص لوضع "Text Morph"
    private fun showTextInputDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("أدخل النص")
        
        val input = EditText(this)
        input.hint = "مثال: MONOLITH"
        input.setTextColor(Color.WHITE)
        builder.setView(input)

        builder.setPositiveButton("تحويل") { _, _ ->
            val text = input.text.toString().trim()
            if (text.isNotEmpty()) {
                particleView.setTextAndMode(text)
            }
        }
        builder.setNegativeButton("إلغاء") { dialog, _ -> 
            dialog.dismiss()
            tabMode.getTabAt(0)?.select() // العودة لوضع الفرشاة إذا ألغى
        }
        builder.show()
    }
}

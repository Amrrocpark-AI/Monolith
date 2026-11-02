package com.monolith

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.InputStream

class ImageEditorActivity : AppCompatActivity() {

    private lateinit var ivEditorImage: ImageView
    private lateinit var btnSaveImage: Button
    private lateinit var btnUndo: ImageButton
    private lateinit var btnRedo: ImageButton
    private lateinit var btnCloseEditor: ImageButton
    private lateinit var btnPickImageForEdit: Button
    private lateinit var tvEditorTimer: TextView

    private lateinit var sbBrightness: SeekBar
    private lateinit var sbContrast: SeekBar
    private lateinit var sbSaturation: SeekBar

    private var originalBitmap: Bitmap? = null
    private var currentBitmap: Bitmap? = null

    // خوارزميات حفظ الحالات (Undo/Redo)
    private val undoStack = ArrayDeque<ColorMatrix>()
    private val redoStack = ArrayDeque<ColorMatrix>()

    // العداد الزمني
    private var autoSaveTimer: CountDownTimer? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream: InputStream? = contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                originalBitmap = bitmap
                currentBitmap = bitmap
                ivEditorImage.setImageBitmap(bitmap)
                btnPickImageForEdit.visibility = View.GONE
                resetSliders()
            } catch (e: Exception) {
                Toast.makeText(this, "فشل تحميل الصورة", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_editor)

        // ربط العناصر
        ivEditorImage = findViewById(R.id.ivEditorImage)
        btnSaveImage = findViewById(R.id.btnSaveImage)
        btnUndo = findViewById(R.id.btnUndo)
        btnRedo = findViewById(R.id.btnRedo)
        btnCloseEditor = findViewById(R.id.btnCloseEditor)
        btnPickImageForEdit = findViewById(R.id.btnPickImageForEdit)
        tvEditorTimer = findViewById(R.id.tvEditorTimer)

        sbBrightness = findViewById(R.id.sbBrightness)
        sbContrast = findViewById(R.id.sbContrast)
        sbSaturation = findViewById(R.id.sbSaturation)

        // 1. اختيار الصورة
        btnPickImageForEdit.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        // 2. زر الإغلاق
        btnCloseEditor.setOnClickListener { finish() }

        // 3. زر الحفظ (سيتم تطويره لاحقاً لحفظ الصورة في المعرض)
        btnSaveImage.setOnClickListener {
            Toast.makeText(this, "تم تطبيق التعديلات (ميزة الحفظ للمعرض قريباً)", Toast.LENGTH_SHORT).show()
        }

        // 4. أزرار التراجع والإعادة
        btnUndo.setOnClickListener { performUndo() }
        btnRedo.setOnClickListener { performRedo() }

        // 5. مستشعرات التغيير (SeekBars)
        val changeListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) applyColorFilters()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // حفظ الحالة عند رفع الإصبع
                saveCurrentStateToUndo()
                startAutoSaveTimer()
            }
        }

        sbBrightness.setOnSeekBarChangeListener(changeListener)
        sbContrast.setOnSeekBarChangeListener(changeListener)
        sbSaturation.setOnSeekBarChangeListener(changeListener)

        // 6. ميزة الضغط المطول للمقارنة (قبل / بعد)
        ivEditorImage.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // عرض الصورة الأصلية
                    originalBitmap?.let { ivEditorImage.setImageBitmap(it) }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // العودة للصورة المعدلة
                    applyColorFilters()
                    true
                }
                else -> false
            }
        }
    }

    // ==========================================
    // محرك الألوان (ColorMatrix Engine)
    // ==========================================
    private fun applyColorFilters() {
        if (originalBitmap == null) return

        val brightness = sbBrightness.progress - 50 // -50 to 50
        val contrast = sbContrast.progress - 50     // -50 to 50
        val saturation = sbSaturation.progress       // 0 to 100

        val colorMatrix = ColorMatrix()

        // 1. السطوع (Brightness)
        val brightnessMatrix = ColorMatrix()
        val scale = 1f + (brightness / 50f)
        brightnessMatrix.setScale(scale, scale, scale, 1f)
        colorMatrix.postConcat(brightnessMatrix)

        // 2. التباين (Contrast)
        val contrastMatrix = ColorMatrix()
        val contrastScale = (contrast + 50f) / 50f
        val translate = (-0.5f * contrastScale + 0.5f) * 255f
        contrastMatrix.set(
            floatArrayOf(
                contrastScale, 0f, 0f, 0f, translate,
                0f, contrastScale, 0f, 0f, translate,
                0f, 0f, contrastScale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        colorMatrix.postConcat(contrastMatrix)

        // 3. التشبع (Saturation)
        val saturationMatrix = ColorMatrix()
        saturationMatrix.setSaturation(saturation / 50f)
        colorMatrix.postConcat(saturationMatrix)

        // تطبيق المصفوفة على الصورة
        ivEditorImage.colorFilter = ColorMatrixColorFilter(colorMatrix)
    }

    // ==========================================
    // نظام التراجع والإعادة (Undo/Redo)
    // ==========================================
    private fun saveCurrentStateToUndo() {
        val currentMatrix = ColorMatrix()
        // نأخذ نسخة من الحالة الحالية (تبسيطاً، سنحفظ القيم الحالية)
        val tempMatrix = ColorMatrix()
        tempMatrix.setSaturation(sbSaturation.progress / 50f)
        undoStack.addLast(tempMatrix)
        
        if (undoStack.size > 10) undoStack.removeFirst() // الاحتفاظ بآخر 10 خطوات فقط
        redoStack.clear() // أي تعديل جديد يمسح الـ Redo
    }

    private fun performUndo() {
        if (undoStack.isNotEmpty()) {
            val lastState = undoStack.removeLast()
            redoStack.addLast(lastState)
            // تطبيق الحالة السابقة (تبسيط: إعادة تعيين الشرائط)
            resetSliders()
            Toast.makeText(this, "تم التراجع", Toast.LENGTH_SHORT).show()
        }
    }

    private fun performRedo() {
        if (redoStack.isNotEmpty()) {
            val nextState = redoStack.removeLast()
            undoStack.addLast(nextState)
            Toast.makeText(this, "تم الإعادة", Toast.LENGTH_SHORT).show()
        }
    }

    // ==========================================
    // العداد الزمني (Smart Timer)
    // ==========================================
    private fun startAutoSaveTimer() {
        autoSaveTimer?.cancel()
        tvEditorTimer.text = "جاري المعالجة..."
        
        autoSaveTimer = object : CountDownTimer(3000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                tvEditorTimer.text = "تثبيت في: ${millisUntilFinished / 1000} ث"
            }

            override fun onFinish() {
                tvEditorTimer.text = "جاهز"
                // هنا يمكن حفظ التعديل في الذاكرة المحلية (Auto Save)
            }
        }.start()
    }

    private fun resetSliders() {
        sbBrightness.progress = 50
        sbContrast.progress = 50
        sbSaturation.progress = 50
        ivEditorImage.colorFilter = null
    }
}

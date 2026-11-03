package com.monolith

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.marginBottom
import androidx.core.view.marginEnd
import androidx.core.view.marginStart
import androidx.core.view.marginTop
import com.google.android.material.card.MaterialCardView
import java.io.InputStream

class ImageEditorActivity : AppCompatActivity() {

    private lateinit var ivEditorImage: ImageView
    private lateinit var btnSaveImage: Button
    private lateinit var btnUndo: ImageButton
    private lateinit var btnRedo: ImageButton
    private lateinit var btnCloseEditor: ImageButton
    private lateinit var btnPickImageForEdit: Button
    private lateinit var tvEditorTimer: TextView

    // الصندوق العائم
    private lateinit var floatingToolbox: MaterialCardView
    private lateinit var toolColors: LinearLayout
    private lateinit var toolDispersion: LinearLayout
    private lateinit var toolCrop: LinearLayout
    private lateinit var toolText: LinearLayout
    private lateinit var toolLayers: LinearLayout

    private var originalBitmap: Bitmap? = null
    private var currentBitmap: Bitmap? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream: InputStream? = contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                originalBitmap = bitmap
                currentBitmap = bitmap
                ivEditorImage.setImageBitmap(bitmap)
                btnPickImageForEdit.visibility = View.GONE
            } catch (e: Exception) {
                Toast.makeText(this, "فشل تحميل الصورة", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_editor)

        ivEditorImage = findViewById(R.id.ivEditorImage)
        btnSaveImage = findViewById(R.id.btnSaveImage)
        btnUndo = findViewById(R.id.btnUndo)
        btnRedo = findViewById(R.id.btnRedo)
        btnCloseEditor = findViewById(R.id.btnCloseEditor)
        btnPickImageForEdit = findViewById(R.id.btnPickImageForEdit)
        tvEditorTimer = findViewById(R.id.tvEditorTimer)

        // ربط عناصر الصندوق العائم
        floatingToolbox = findViewById(R.id.floatingToolboxCard)
        toolColors = findViewById(R.id.toolColors)
        toolDispersion = findViewById(R.id.toolDispersion)
        toolCrop = findViewById(R.id.toolCrop)
        toolText = findViewById(R.id.toolText)
        toolLayers = findViewById(R.id.toolLayers)

        // إعداد مستمعي النقر للأدوات
        toolColors.setOnClickListener { showToolMessage("أداة الألوان") }
        toolDispersion.setOnClickListener { showToolMessage("أداة التشتت") }
        toolCrop.setOnClickListener { showToolMessage("أداة القص") }
        toolText.setOnClickListener { showToolMessage("أداة النصوص") }
        toolLayers.setOnClickListener { showToolMessage("أداة الطبقات") }

        btnPickImageForEdit.setOnClickListener { pickImageLauncher.launch("image/*") }
        btnCloseEditor.setOnClickListener { finish() }
        btnSaveImage.setOnClickListener { Toast.makeText(this, "حفظ قيد التطوير", Toast.LENGTH_SHORT).show() }

        // تفعيل السحب للصندوق العائم
        setupFloatingToolboxDrag()
    }

    private fun showToolMessage(toolName: String) {
        Toast.makeText(this, "تم اختيار: $toolName", Toast.LENGTH_SHORT).show()
        // هنا سنقوم لاحقاً بفتح اللوحة السفلية (Bottom Panel) الخاصة بكل أداة
    }

    // ==========================================
    // محرك السحب الذكي (Smart Drag Engine)
    // ==========================================
    @SuppressLint("ClickableViewAccessibility")
    private fun setupFloatingToolboxDrag() {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        val params = floatingToolbox.layoutParams as ViewGroup.MarginLayoutParams

        floatingToolbox.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // حفظ الإحداثيات الأولية
                    initialX = params.leftMargin
                    initialY = params.topMargin
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    // حساب المسافة المقطوعة
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()

                    // تحديث الهوامش
                    params.leftMargin = initialX + dx
                    params.topMargin = initialY + dy
                    floatingToolbox.layoutParams = params
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // عند رفع الإصبع، نقوم بالالتصاق بأقرب حافة (يمين أو يسار)
                    snapToEdge()
                    true
                }
                else -> false
            }
        }
    }

    // دالة الالتصاق بالحواف
    private fun snapToEdge() {
        val screenWidth = resources.displayMetrics.widthPixels
        val viewWidth = floatingToolbox.width
        val params = floatingToolbox.layoutParams as ViewGroup.MarginLayoutParams

        // حساب المسافة للحد الأيسر والأيمن
        val distanceToLeft = params.leftMargin
        val distanceToRight = screenWidth - (params.leftMargin + viewWidth)

        // تحديد الحافة الأقرب
        val targetX = if (distanceToLeft < distanceToRight) {
            // الالتصاق باليسار
            16 // هامش صغير
        } else {
            // الالتصاق باليمين
            screenWidth - viewWidth - 16
        }

        // تحريك الصندوق بسلاسة
        val animator = android.animation.ValueAnimator.ofInt(params.leftMargin, targetX)
        animator.addUpdateListener { animation ->
            params.leftMargin = animation.animatedValue as Int
            floatingToolbox.layoutParams = params
        }
        animator.duration = 300
        animator.start()
    }
}

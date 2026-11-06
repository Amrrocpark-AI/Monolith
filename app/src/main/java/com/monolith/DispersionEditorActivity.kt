package com.monolith

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.math.max

class DispersionEditorActivity : AppCompatActivity() {

    private data class Tool(val id: Int, val label: String, val icon: Int)

    private val tools = listOf(
        Tool(DispersionView.TOOL_BRUSH, "الفرشاة", R.drawable.ic_brush),
        Tool(DispersionView.TOOL_SPREAD, "الإمتداد", R.drawable.ic_spread),
        Tool(DispersionView.TOOL_SIZE, "الحجم", R.drawable.ic_size),
        Tool(DispersionView.TOOL_DIR, "اتجاه", R.drawable.ic_direction),
        Tool(DispersionView.TOOL_FADE, "تضاؤل", R.drawable.ic_fade),
        Tool(DispersionView.TOOL_BLEND, "المزيج", R.drawable.ic_blend)
    )

    private val teal = Color.parseColor("#26A69A")
    private val maxSide = 2560

    private lateinit var editor: DispersionView
    private lateinit var btnClose: ImageButton
    private lateinit var btnUndo: ImageButton
    private lateinit var btnRedo: ImageButton
    private lateinit var btnEraser: ImageButton
    private lateinit var btnApply: Button
    private lateinit var btnSave: Button
    private lateinit var btnPick: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var sliderRow: LinearLayout
    private lateinit var tvLabel: TextView
    private lateinit var tvValue: TextView
    private lateinit var seek: SeekBar
    private lateinit var blendScroll: HorizontalScrollView
    private lateinit var blendRow: LinearLayout
    private lateinit var toolsRow: LinearLayout

    private val tabViews = HashMap<Int, LinearLayout>()
    private val blendViews = ArrayList<TextView>()
    private var currentTool = DispersionView.TOOL_SPREAD
    private var hasUnsavedChanges = false

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) loadImage(uri)
    }

    private val askStorage = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startSave() else toast("لا يمكن الحفظ بدون صلاحية التخزين")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dispersion_editor)

        editor = findViewById(R.id.dispersionView)
        btnClose = findViewById(R.id.btnClose)
        btnUndo = findViewById(R.id.btnUndo)
        btnRedo = findViewById(R.id.btnRedo)
        btnEraser = findViewById(R.id.btnEraser)
        btnApply = findViewById(R.id.btnApply)
        btnSave = findViewById(R.id.btnSave)
        btnPick = findViewById(R.id.btnPick)
        progressBar = findViewById(R.id.progressBar)
        sliderRow = findViewById(R.id.sliderRow)
        tvLabel = findViewById(R.id.tvLabel)
        tvValue = findViewById(R.id.tvValue)
        seek = findViewById(R.id.seekBar)
        blendScroll = findViewById(R.id.blendScroll)
        blendRow = findViewById(R.id.blendRow)
        toolsRow = findViewById(R.id.toolsRow)

        buildTabs()
        buildBlendRow()

        btnClose.setOnClickListener { checkUnsavedAndFinish() }
        btnUndo.setOnClickListener { editor.undo(); hasUnsavedChanges = true }
        btnRedo.setOnClickListener { editor.redo(); hasUnsavedChanges = true }
        btnEraser.setOnClickListener {
            editor.eraserMode = !editor.eraserMode
            btnEraser.setColorFilter(if (editor.eraserMode) teal else Color.WHITE)
        }
        btnApply.setOnClickListener { applyEffect() }
        btnSave.setOnClickListener { onSaveClicked() }
        btnPick.setOnClickListener { pickImage.launch("image/*") }

        editor.onHistoryChanged = { refreshHistoryButtons() }
        refreshHistoryButtons()

        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                editor.setValue(currentTool, progress)
                tvValue.text = progress.toString()
                hasUnsavedChanges = true
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        // معالجة زر الرجوع في الهاتف
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                checkUnsavedAndFinish()
            }
        })

        selectTool(DispersionView.TOOL_SPREAD)
    }

    private fun checkUnsavedAndFinish() {
        if (hasUnsavedChanges && editor.hasImage) {
            AlertDialog.Builder(this)
                .setTitle("تنبيه")
                .setMessage("لديك تعديلات غير محفوظة. هل أنت متأكد من الخروج؟")
                .setPositiveButton("خروج") { _, _ -> finish() }
                .setNegativeButton("إلغاء", null)
                .show()
        } else {
            finish()
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun buildTabs() {
        for (t in tools) {
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, dp(8))
                isClickable = true
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                setOnClickListener { selectTool(t.id) }
            }
            val iv = ImageView(this).apply {
                setImageResource(t.icon)
                layoutParams = LinearLayout.LayoutParams(dp(26), dp(26))
            }
            val tv = TextView(this).apply {
                text = t.label
                textSize = 10f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(0, dp(4), 0, 0)
            }
            col.addView(iv)
            col.addView(tv)
            toolsRow.addView(col)
            tabViews[t.id] = col
        }
    }

    private fun buildBlendRow() {
        DispersionView.BLEND_NAMES.forEachIndexed { i, name ->
            val tv = TextView(this).apply {
                text = name
                textSize = 13f
                setPadding(dp(14), dp(10), dp(14), dp(10))
                setOnClickListener { selectBlend(i) }
            }
            blendRow.addView(tv)
            blendViews.add(tv)
        }
        selectBlend(editor.getValue(DispersionView.TOOL_BLEND), apply = false)
    }

    private fun selectBlend(index: Int, apply: Boolean = true) {
        if (apply) {
            editor.setValue(DispersionView.TOOL_BLEND, index)
            hasUnsavedChanges = true
        }
        blendViews.forEachIndexed { i, tv ->
            tv.setTextColor(if (i == index) Color.WHITE else Color.parseColor("#8A8A8A"))
            tv.setTypeface(null, if (i == index) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        }
    }

    private fun selectTool(id: Int) {
        currentTool = id
        for ((tid, v) in tabViews) v.setBackgroundColor(if (tid == id) teal else Color.TRANSPARENT)
        if (id == DispersionView.TOOL_BLEND) {
            sliderRow.visibility = View.GONE
            blendScroll.visibility = View.VISIBLE
        } else {
            blendScroll.visibility = View.GONE
            sliderRow.visibility = View.VISIBLE
            seek.max = if (id == DispersionView.TOOL_DIR) 360 else 100
            seek.progress = editor.getValue(id)
            tvLabel.text = tools.first { it.id == id }.label
            tvValue.text = editor.getValue(id).toString()
        }
    }

    private fun refreshHistoryButtons() {
        btnUndo.alpha = if (editor.canUndo) 1f else 0.35f
        btnRedo.alpha = if (editor.canRedo) 1f else 0.35f
    }

    private fun setBusy(busy: Boolean) {
        val e = !busy
        editor.isEnabled = e
        btnClose.isEnabled = e; btnUndo.isEnabled = e; btnRedo.isEnabled = e
        btnEraser.isEnabled = e; btnApply.isEnabled = e; btnSave.isEnabled = e
        progressBar.visibility = if (busy) View.VISIBLE else View.GONE
    }

    private fun applyEffect() {
        if (!editor.hasImage) return
        setBusy(true)
        lifecycleScope.launch(Dispatchers.Default) {
            val success = editor.bake()
            withContext(Dispatchers.Main) {
                setBusy(false)
                if (success) {
                    toast("تم تطبيق التأثير")
                    hasUnsavedChanges = true
                } else {
                    toast("ارسم على الصورة أولاً")
                }
            }
        }
    }

    private fun loadImage(uri: Uri) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2

            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            var bmp = contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: throw IOException("decode failed")

            val degrees = readRotation(uri)
            if (degrees != 0) {
                val m = Matrix().apply { postRotate(degrees.toFloat()) }
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            }
            editor.setImage(bmp)
            btnPick.visibility = View.GONE
            hasUnsavedChanges = false
        } catch (e: Exception) {
            toast("تعذر فتح الصورة")
        }
    }

    private fun readRotation(uri: Uri): Int {
        if (Build.VERSION.SDK_INT < 24) return 0
        return try {
            contentResolver.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (e: Exception) { 0 }
    }

    private fun onSaveClicked() {
        if (!editor.hasImage) { toast("اختر صورة أولاً"); return }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            askStorage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        startSave()
    }

    private fun startSave() {
        setBusy(true)
        lifecycleScope.launch(Dispatchers.Default) {
            val bmp = editor.exportBitmap()
            val ok = bmp != null && writeToGallery(bmp)
            withContext(Dispatchers.Main) {
                setBusy(false)
                if (ok) {
                    toast("تم الحفظ في المعرض")
                    hasUnsavedChanges = false
                } else {
                    toast("فشل الحفظ")
                }
            }
        }
    }

    private fun writeToGallery(bmp: Bitmap): Boolean {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Monolith_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Monolith")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        return try {
            val ok = resolver.openOutputStream(uri)?.use {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
            } ?: false
            if (!ok) throw IOException("compress failed")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                resolver.update(uri, done, null, null)
            }
            true
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            false
        }
    }
}

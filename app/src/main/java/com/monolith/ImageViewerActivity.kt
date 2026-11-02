package com.monolith

import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.yalantis.ucrop.UCrop
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

class ImageViewerActivity : AppCompatActivity() {

    private var imageUri: Uri? = null
    private lateinit var fullImageView: ImageView
    private var messageId: String? = null
    private var imageIndex: Int = -1
    
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private lateinit var gestureDetector: GestureDetector
    private var scaleFactor = 1.0f
    private val matrix = Matrix()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_viewer)

        fullImageView = findViewById(R.id.fullImageView)
        val btnClose = findViewById<ImageButton>(R.id.btnCloseViewer)
        val btnAnalyze = findViewById<TextView>(R.id.btnAnalyze)
        val btnShare = findViewById<ImageButton>(R.id.btnShare)
        val btnCrop = findViewById<ImageButton>(R.id.btnCrop)
        val btnRotate = findViewById<ImageButton>(R.id.btnRotate)
        val btnDownload = findViewById<ImageButton>(R.id.btnDownload)
        val btnRename = findViewById<ImageButton>(R.id.btnRename)
        val btnInfo = findViewById<ImageButton>(R.id.btnInfo)

        val uriString = intent.getStringExtra("image_uri")
        messageId = intent.getStringExtra("message_id")
        imageIndex = intent.getIntExtra("image_index", -1)

        if (uriString != null) {
            imageUri = Uri.parse(uriString)
            fullImageView.setImageURI(imageUri)
        }

        setupZoomAndPan()

        btnClose.setOnClickListener { closeAndReturn() }
        btnAnalyze.setOnClickListener { Toast.makeText(this, "تحليل AI قريباً", Toast.LENGTH_SHORT).show() }
        btnShare.setOnClickListener {
            if (imageUri != null) {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_STREAM, imageUri)
                }
                startActivity(Intent.createChooser(intent, "مشاركة الصورة"))
            }
        }

        btnCrop.setOnClickListener {
            if (imageUri != null) {
                try {
                    val file = File(cacheDir, "cropped_${System.currentTimeMillis()}.jpg")
                    val destinationUri = FileProvider.getUriForFile(this, "com.monolith.fileprovider", file)
                    val uCrop = UCrop.of(imageUri!!, destinationUri).withMaxResultSize(1080, 1080)
                    uCrop.start(this)
                } catch (e: Exception) {
                    Toast.makeText(this, "خطأ: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnRotate.setOnClickListener { rotateImage() }
        btnDownload.setOnClickListener { saveImageToGallery() }
        btnRename.setOnClickListener { showRenameDialog() }
        btnInfo.setOnClickListener { showImageInfo() }
    }

    private fun setupZoomAndPan() {
        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scaleFactor *= detector.scaleFactor
                scaleFactor = Math.max(0.1f, Math.min(scaleFactor, 5.0f))
                matrix.setScale(scaleFactor, scaleFactor, detector.focusX, detector.focusY)
                fullImageView.imageMatrix = matrix
                return true
            }
        })
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                scaleFactor = if (scaleFactor > 1.0f) 1.0f else 2.0f
                matrix.setScale(scaleFactor, scaleFactor, e.x, e.y)
                fullImageView.imageMatrix = matrix
                return true
            }
        })
        fullImageView.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun rotateImage() {
        if (imageUri == null) return
        try {
            val inputStream = contentResolver.openInputStream(imageUri!!)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            val matrix = Matrix()
            matrix.postRotate(90f)
            val rotatedBitmap = Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true)
            
            val file = File(cacheDir, "rotated_${System.currentTimeMillis()}.jpg")
            val outputStream = FileOutputStream(file)
            rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            outputStream.close()
            
            imageUri = FileProvider.getUriForFile(this, "com.monolith.fileprovider", file)
            fullImageView.setImageURI(imageUri)
            closeAndReturn()
        } catch (e: Exception) {
            Toast.makeText(this, "فشل التدوير: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRenameDialog() {
        if (imageUri == null) return
        val builder = AlertDialog.Builder(this)
        builder.setTitle("إعادة تسمية الصورة")
        val input = EditText(this)
        input.setText(imageUri!!.lastPathSegment)
        builder.setView(input)
        builder.setPositiveButton("حفظ") { _, _ ->
            Toast.makeText(this, "تمت التسمية إلى: ${input.text}", Toast.LENGTH_SHORT).show()
        }
        builder.setNegativeButton("إلغاء") { dialog, _ -> dialog.cancel() }
        builder.show()
    }

    private fun showImageInfo() {
        if (imageUri == null) return
        try {
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            contentResolver.openInputStream(imageUri!!)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
            val width = options.outWidth
            val height = options.outHeight
            val info = "الأبعاد: $width x $height"
            AlertDialog.Builder(this)
                .setTitle("معلومات الصورة")
                .setMessage(info)
                .setPositiveButton("حسناً", null)
                .show()
        } catch (e: Exception) {
            Toast.makeText(this, "تعذر قراءة المعلومات", Toast.LENGTH_SHORT).show()
        }
    }

    private fun closeAndReturn() {
        val resultIntent = Intent()
        resultIntent.putExtra("new_image_uri", imageUri.toString())
        resultIntent.putExtra("message_id", messageId)
        resultIntent.putExtra("image_index", imageIndex)
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    private fun saveImageToGallery() {
        if (imageUri == null) return
        try {
            val inputStream = contentResolver.openInputStream(imageUri!!)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            val filename = "Monolith_${System.currentTimeMillis()}.jpg"
            var outputStream: OutputStream? = null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Monolith")
                }
                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) outputStream = contentResolver.openOutputStream(uri)
            } else {
                val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString() + "/Monolith"
                val dir = File(imagesDir)
                if (!dir.exists()) dir.mkdirs()
                val image = File(dir, filename)
                outputStream = FileOutputStream(image)
            }

            if (outputStream != null) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                outputStream.close()
                Toast.makeText(this, "تم الحفظ في مجلد Monolith", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "خطأ أثناء الحفظ: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && requestCode == UCrop.REQUEST_CROP) {
            val resultUri = UCrop.getOutput(data!!)
            if (resultUri != null) {
                imageUri = resultUri
                fullImageView.setImageURI(imageUri)
                closeAndReturn()
            }
        }
    }
}

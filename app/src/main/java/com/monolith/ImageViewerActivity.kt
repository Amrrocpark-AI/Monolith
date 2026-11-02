package com.monolith

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.yalantis.ucrop.UCrop
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

class ImageViewerActivity : AppCompatActivity() {

    private var imageUri: Uri? = null
    private lateinit var fullImageView: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_viewer)

        fullImageView = findViewById(R.id.fullImageView)
        val btnClose = findViewById<ImageButton>(R.id.btnCloseViewer)
        val btnCrop = findViewById<ImageButton>(R.id.btnCrop)
        val btnDownload = findViewById<ImageButton>(R.id.btnDownload)
        val btnRename = findViewById<ImageButton>(R.id.btnRename)
        val btnInfo = findViewById<ImageButton>(R.id.btnInfo)

        // استقبال مسار الصورة من الشاشة السابقة
        val uriString = intent.getStringExtra("image_uri")
        if (uriString != null) {
            imageUri = Uri.parse(uriString)
            fullImageView.setImageURI(imageUri)
        }

        // زر الإغلاق
        btnClose.setOnClickListener { finish() }

        // زر القص (uCrop)
        btnCrop.setOnClickListener {
            if (imageUri != null) {
                val destinationUri = Uri.fromFile(File(cacheDir, "cropped_${System.currentTimeMillis()}.jpg"))
                val uCrop = UCrop.of(imageUri!!, destinationUri)
                    .withAspectRatio(1f, 1f) // نسبة 1:1
                    .withMaxResultSize(1080, 1080)
                uCrop.start(this)
            }
        }

        // زر التحميل (حفظ في معرض الهاتف)
        btnDownload.setOnClickListener {
            if (imageUri != null) {
                try {
                    saveImageToGallery(imageUri!!)
                } catch (e: Exception) {
                    Toast.makeText(this, "فشل التحميل: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // زر إعادة التسمية (محاكاة مؤقتة)
        btnRename.setOnClickListener {
            Toast.makeText(this, "ميزة إعادة التسمية ستتوفر قريباً", Toast.LENGTH_SHORT).show()
        }

        // زر المعلومات
        btnInfo.setOnClickListener {
            Toast.makeText(this, "الصورة: ${imageUri?.lastPathSegment}", Toast.LENGTH_LONG).show()
        }
    }

    // دالة حفظ الصورة في المعرض (تعمل على أندرويد الحديث)
    private fun saveImageToGallery(sourceUri: Uri) {
        val inputStream: InputStream? = contentResolver.openInputStream(sourceUri)
        val bitmap = BitmapFactory.decodeStream(inputStream)

        val filename = "Monolith_${System.currentTimeMillis()}.jpg"
        var outputStream: OutputStream? = null
        val uri: Uri?

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                }
                uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    outputStream = resolver.openOutputStream(uri)
                }
            } else {
                val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString()
                val image = File(imagesDir, filename)
                outputStream = FileOutputStream(image)
                uri = Uri.fromFile(image)
            }

            if (outputStream != null) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                outputStream.close()
                Toast.makeText(this, "تم حفظ الصورة بنجاح!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "خطأ أثناء الحفظ: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // استقبال نتيجة القص من مكتبة uCrop
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && requestCode == UCrop.REQUEST_CROP) {
            val resultUri = UCrop.getOutput(data!!)
            if (resultUri != null) {
                fullImageView.setImageURI(resultUri)
                imageUri = resultUri
                Toast.makeText(this, "تم القص بنجاح!", Toast.LENGTH_SHORT).show()
            }
        } else if (resultCode == UCrop.RESULT_ERROR) {
            val cropError = UCrop.getError(data!!)
            Toast.makeText(this, "فشل القص: ${cropError?.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

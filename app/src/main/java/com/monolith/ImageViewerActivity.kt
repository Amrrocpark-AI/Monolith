package com.monolith

import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.InputType
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.yalantis.ucrop.UCrop
import com.yalantis.ucrop.UCropActivity
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ImageViewerActivity : AppCompatActivity() {

    private var imageUri: Uri? = null
    private lateinit var fullImageView: ImageView
    private lateinit var progressBar: ProgressBar
    private var messageId: String? = null
    private var imageIndex: Int = -1
    
    private var isPrivacyShieldEnabled = true

    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private lateinit var gestureDetector: GestureDetector
    private var scaleFactor = 1.0f
    private val matrix = Matrix()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_viewer)

        fullImageView = findViewById(R.id.fullImageView)
        progressBar = findViewById(R.id.progressBar)
        
        val btnClose = findViewById<ImageButton>(R.id.btnCloseViewer)
        val btnPrivacyShield = findViewById<ImageButton>(R.id.btnPrivacyShield)
        val btnAnalyze = findViewById<TextView>(R.id.btnAnalyze)
        val btnShare = findViewById<ImageButton>(R.id.btnShare)
        val btnCrop = findViewById<ImageButton>(R.id.btnCrop)
        val btnRotate = findViewById<ImageButton>(R.id.btnRotate)
        val btnRename = findViewById<ImageButton>(R.id.btnRename)
        val btnEditExif = findViewById<ImageButton>(R.id.btnEditExif)
        val btnDownload = findViewById<ImageButton>(R.id.btnDownload)
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

        btnPrivacyShield.setOnClickListener {
            isPrivacyShieldEnabled = !isPrivacyShieldEnabled
            if (isPrivacyShieldEnabled) {
                btnPrivacyShield.setColorFilter(Color.parseColor("#00FF00"))
                Toast.makeText(this, "درع الخصوصية: مفعل", Toast.LENGTH_SHORT).show()
            } else {
                btnPrivacyShield.setColorFilter(Color.WHITE)
                Toast.makeText(this, "درع الخصوصية: معطل", Toast.LENGTH_SHORT).show()
            }
        }

        btnAnalyze.setOnClickListener { Toast.makeText(this, "تحليل AI قريباً", Toast.LENGTH_SHORT).show() }
        btnShare.setOnClickListener { shareImage() }
        btnCrop.setOnClickListener { cropImage() }
        btnRotate.setOnClickListener { rotateImage() }
        btnDownload.setOnClickListener { saveImageToGallery() }
        btnInfo.setOnClickListener { showImageInfoWithExif() }
        btnRename.setOnClickListener { showRenameDialog() }
        btnEditExif.setOnClickListener { showEditExifDialog() }
    }

    private fun showEditExifDialog() {
        if (imageUri == null) return

        val builder = AlertDialog.Builder(this)
        builder.setTitle("تعديل بيانات الصورة (EXIF)")
        
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(32, 16, 32, 16)

        val etModel = EditText(this).apply { hint = "اسم الكاميرا (مثال: Canon EOS 5D)" }
        val etDate = EditText(this).apply { hint = "التاريخ (YYYY:MM:DD HH:MM:SS)" }
        val etLat = EditText(this).apply { 
            hint = "خط العرض (مثال: 34.0522)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        val etLong = EditText(this).apply { 
            hint = "خط الطول (مثال: -118.2437)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        }

        val btnSuggest = Button(this).apply {
            text = "🤖 اقتراح ذكي (تزييف واقعي)"
            setBackgroundColor(Color.parseColor("#00FF00"))
            setTextColor(Color.BLACK)
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.setMargins(0, 24, 0, 24)
            layoutParams = params
        }

        btnSuggest.setOnClickListener {
            etModel.setText("iPhone 15 Pro Max")
            etDate.setText("2024:05:12 18:30:00")
            etLat.setText("25.2048")
            etLong.setText("55.2708")
            Toast.makeText(this, "تم توليد بيانات وهمية مطابقة للواقع!", Toast.LENGTH_SHORT).show()
        }

        layout.addView(etModel)
        layout.addView(etDate)
        layout.addView(etLat)
        layout.addView(etLong)
        layout.addView(btnSuggest)
        builder.setView(layout)

        builder.setPositiveButton("حقن البيانات") { _, _ ->
            val model = etModel.text.toString()
            val date = etDate.text.toString()
            val latStr = etLat.text.toString()
            val longStr = etLong.text.toString()
            applyExifData(model, date, latStr, longStr)
        }
        builder.setNegativeButton("إلغاء") { dialog, _ -> dialog.cancel() }
        builder.show()
    }

    private fun applyExifData(model: String, date: String, latStr: String, longStr: String) {
        progressBar.visibility = android.view.View.VISIBLE
        
        Thread {
            try {
                val sourceUri = imageUri!!
                val file = File(cacheDir, "exif_edited_${System.currentTimeMillis()}.jpg")
                val inputStream = contentResolver.openInputStream(sourceUri)
                val outputStream = FileOutputStream(file)
                inputStream?.copyTo(outputStream)
                outputStream.close()
                inputStream?.close()

                val exif = ExifInterface(file.absolutePath)
                var wasCorrected = false
                
                if (model.isNotEmpty()) {
                    exif.setAttribute(ExifInterface.TAG_MODEL, model)
                    exif.setAttribute(ExifInterface.TAG_MAKE, "Monolith Studio")
                }
                
                if (date.isNotEmpty()) {
                    val validDate = if (date.length == 19 && date.contains(":")) {
                        date
                    } else if (date.matches(Regex("^\\d{4}$"))) {
                        wasCorrected = true
                        "$date:01:01 12:00:00"
                    } else {
                        wasCorrected = true
                        SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(Date())
                    }
                    exif.setAttribute(ExifInterface.TAG_DATETIME, validDate)
                }
                
                if (latStr.isNotEmpty() && longStr.isNotEmpty()) {
                    val lat = latStr.toDoubleOrNull()
                    val long = longStr.toDoubleOrNull()
                    
                    if (lat != null && lat in -90.0..90.0 && long != null && long in -180.0..180.0) {
                        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, convertToDegreeMinuteSec(lat))
                        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, if (lat >= 0) "N" else "S")
                        exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, convertToDegreeMinuteSec(long))
                        exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, if (long >= 0) "E" else "W")
                    } else if (lat != null && long != null) {
                        wasCorrected = true
                        val fixedLat = (lat % 90.0)
                        val fixedLong = (long % 180.0)
                        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, convertToDegreeMinuteSec(fixedLat))
                        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
                        exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, convertToDegreeMinuteSec(fixedLong))
                        exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, "E")
                    }
                }
                
                exif.saveAttributes()

                Handler(Looper.getMainLooper()).post {
                    imageUri = FileProvider.getUriForFile(this, "com.monolith.fileprovider", file)
                    fullImageView.setImageURI(imageUri)
                    progressBar.visibility = android.view.View.GONE
                    
                    if (wasCorrected) {
                        Toast.makeText(this, "المساعد الذكي: تم تصحيح البيانات المدخلة تلقائياً وحقنها!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, "تم تعديل البيانات وحفظها بنجاح!", Toast.LENGTH_LONG).show()
                    }
                }
                
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    progressBar.visibility = android.view.View.GONE
                    Toast.makeText(this, "فشل الحقن: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun showRenameDialog() {
        if (imageUri == null) return
        val builder = AlertDialog.Builder(this)
        builder.setTitle("إعادة تسمية الصورة")
        val input = EditText(this)
        input.setText(imageUri!!.lastPathSegment)
        builder.setView(input)
        
        builder.setPositiveButton("حفظ") { _, _ ->
            val newName = input.text.toString().trim()
            if (newName.isNotEmpty()) {
                try {
                    val newFile = File(cacheDir, if(newName.endsWith(".jpg")) newName else "$newName.jpg")
                    val inputStream = contentResolver.openInputStream(imageUri!!)
                    val outputStream = FileOutputStream(newFile)
                    inputStream?.copyTo(outputStream)
                    outputStream.close()
                    inputStream?.close()
                    
                    imageUri = FileProvider.getUriForFile(this, "com.monolith.fileprovider", newFile)
                    fullImageView.setImageURI(imageUri)
                    
                    Toast.makeText(this, "تمت إعادة التسمية بنجاح!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "فشل التسمية: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
        builder.setNegativeButton("إلغاء") { dialog, _ -> dialog.cancel() }
        builder.show()
    }

    private fun convertToDegreeMinuteSec(coord: Double): String {
        val absCoord = Math.abs(coord)
        val degree = absCoord.toInt()
        val minute = ((absCoord - degree) * 60).toInt()
        val second = ((absCoord - degree - (minute / 60.0)) * 3600)
        return "$degree/1,$minute/1,${second.toInt()}/1"
    }

    private fun shareImage() {
        if (imageUri != null) {
            val uriToShare = if (isPrivacyShieldEnabled) getSanitizedUri(imageUri!!) else imageUri!!
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, uriToShare)
            }
            startActivity(Intent.createChooser(intent, "مشاركة الصورة"))
        }
    }

    private fun cropImage() {
        if (imageUri != null) {
            try {
                val file = File(cacheDir, "cropped_${System.currentTimeMillis()}.jpg")
                val destinationUri = FileProvider.getUriForFile(this, "com.monolith.fileprovider", file)
                
                val options = UCrop.Options()
                options.setCompressionFormat(Bitmap.CompressFormat.JPEG)
                options.setCompressionQuality(100)
                options.setHideBottomControls(false)
                options.setFreeStyleCropEnabled(true)
                options.setShowCropGrid(true)
                options.setToolbarTitle("محرر مونوليث")
                options.setAllowedGestures(UCropActivity.SCALE, UCropActivity.ROTATE, UCropActivity.ALL)

                val uCrop = UCrop.of(imageUri!!, destinationUri)
                    .withOptions(options)
                    .withMaxResultSize(2048, 2048)

                uCrop.start(this)
            } catch (e: Exception) {
                Toast.makeText(this, "خطأ في الإعداد: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getSanitizedUri(sourceUri: Uri): Uri {
        return try {
            val inputStream = contentResolver.openInputStream(sourceUri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            val file = File(cacheDir, "sanitized_${System.currentTimeMillis()}.jpg")
            val outputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            outputStream.close()
            FileProvider.getUriForFile(this, "com.monolith.fileprovider", file)
        } catch (e: Exception) {
            sourceUri
        }
    }

    private fun showImageInfoWithExif() {
        if (imageUri == null) return
        try {
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            contentResolver.openInputStream(imageUri!!)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
            val width = options.outWidth
            val height = options.outHeight

            var exifInfo = "لا توجد بيانات وصفية\n"
            try {
                contentResolver.openInputStream(imageUri!!)?.use { stream ->
                    val exif = ExifInterface(stream)
                    val latLong = FloatArray(2)
                    val hasLatLong = exif.getLatLong(latLong)
                    
                    val model = exif.getAttribute(ExifInterface.TAG_MODEL) ?: "غير معروف"
                    val make = exif.getAttribute(ExifInterface.TAG_MAKE) ?: "غير معروف"
                    val dateTime = exif.getAttribute(ExifInterface.TAG_DATETIME) ?: "غير معروف"
                    
                    exifInfo = "الكاميرا: $make $model\n"
                    exifInfo += "التاريخ: $dateTime\n"
                    
                    if (hasLatLong) {
                        exifInfo += "الموقع (GPS): ${latLong[0]}, ${latLong[1]}\n"
                    } else {
                        exifInfo += "الموقع (GPS): غير متوفر\n"
                    }
                }
            } catch (e: Exception) {
                exifInfo = "تعذر قراءة البيانات الوصفية\n"
            }

            val info = "الأبعاد: $width x $height\n\n$exifInfo"
            
            AlertDialog.Builder(this)
                .setTitle("معلومات الصورة")
                .setMessage(info)
                .setPositiveButton("حسناً", null)
                .show()
        } catch (e: Exception) {
            Toast.makeText(this, "تعذر قراءة المعلومات", Toast.LENGTH_SHORT).show()
        }
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
        progressBar.visibility = android.view.View.VISIBLE
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
            progressBar.visibility = android.view.View.GONE
            Toast.makeText(this, "تم التدوير بنجاح", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            progressBar.visibility = android.view.View.GONE
            Toast.makeText(this, "فشل التدوير: ${e.message}", Toast.LENGTH_SHORT).show()
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
        progressBar.visibility = android.view.View.VISIBLE
        try {
            val uriToSave = if (isPrivacyShieldEnabled) getSanitizedUri(imageUri!!) else imageUri!!
            
            val inputStream = contentResolver.openInputStream(uriToSave)
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
                progressBar.visibility = android.view.View.GONE
                Toast.makeText(this, "تم الحفظ في مجلد Monolith", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            progressBar.visibility = android.view.View.GONE
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
        } else if (resultCode == UCrop.RESULT_ERROR) {
            val cropError = UCrop.getError(data!!)
            Toast.makeText(this, "فشل القص: ${cropError?.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

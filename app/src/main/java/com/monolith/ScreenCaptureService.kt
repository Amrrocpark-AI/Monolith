package com.monolith

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ScreenCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    companion object {
        const val ACTION_START_CAPTURE = "START_CAPTURE"
        const val ACTION_TAKE_SCREENSHOT = "TAKE_SCREENSHOT"
        const val EXTRA_RESULT_CODE = "RESULT_CODE"
        const val EXTRA_DATA_INTENT = "DATA_INTENT"
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
    }

    private fun startForegroundService() {
        val channelId = "screen_capture_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Monolith Screen Capture", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("مونوليث: جاهز لتصوير الشاشة")
            .setContentText("اضغط على الفقاعة العائمة لالتقاط لقطة شاشة")
            .setSmallIcon(R.drawable.ic_bubble_monolith)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(2, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(2, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_CAPTURE -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val dataIntent = intent.getParcelableExtra<Intent>(EXTRA_DATA_INTENT)
                if (dataIntent != null) {
                    setupMediaProjection(resultCode, dataIntent)
                }
            }
            ACTION_TAKE_SCREENSHOT -> {
                captureScreen()
            }
        }
        return START_NOT_STICKY
    }

    private fun setupMediaProjection(resultCode: Int, dataIntent: Intent) {
        if (mediaProjection != null) return

        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, dataIntent)

        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                super.onStop()
                virtualDisplay?.release()
                imageReader?.close()
                mediaProjection = null
            }
        }, Handler(Looper.getMainLooper()))

        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            metrics.widthPixels = bounds.width()
            metrics.heightPixels = bounds.height()
            metrics.densityDpi = resources.displayMetrics.densityDpi
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
        }

        imageReader = ImageReader.newInstance(
            metrics.widthPixels,
            metrics.heightPixels,
            PixelFormat.RGBA_8888,
            2
        )

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "MonolithScreenCapture",
            metrics.widthPixels,
            metrics.heightPixels,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null
        )
        
        Toast.makeText(this, "تم تفعيل كاميرا تصوير الشاشة", Toast.LENGTH_SHORT).show()
    }

    private fun captureScreen() {
        val image = imageReader?.acquireLatestImage()
        if (image == null) {
            Toast.makeText(this, "فشل التقاط الصورة", Toast.LENGTH_SHORT).show()
            return
        }

        serviceScope.launch {
            try {
                val bitmap = imageToBitmap(image)
                if (bitmap != null) {
                    saveBitmapToGallery(bitmap)
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(this@ScreenCaptureService, "تم حفظ لقطة الشاشة في المعرض", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                image.close()
            }
        }
    }

    private fun imageToBitmap(image: Image): Bitmap? {
        return try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * image.width

            val bitmap = Bitmap.createBitmap(
                image.width + rowPadding / pixelStride,
                image.height,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)

            Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun saveBitmapToGallery(bitmap: Bitmap) {
        try {
            val filename = "Monolith_Screenshot_${System.currentTimeMillis()}.jpg"
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Monolith")
                }
            }

            val uri: Uri? = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                // *** الحل هنا: استخدام ?.use لتفادي خطأ النوع ***
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

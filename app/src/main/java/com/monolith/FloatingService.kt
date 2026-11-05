package com.monolith

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Vibrator
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat

class FloatingService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var layoutParams: WindowManager.LayoutParams

    // متغيرات لوحة الصوت
    private var volumePanelView: View? = null
    private lateinit var audioManager: AudioManager
    private var currentStream = AudioManager.STREAM_MUSIC
    private lateinit var vibrator: Vibrator

    override fun onCreate() {
        super.onCreate()
        try {
            startForegroundService()
            setupFloatingBubble()
            
            audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

            getSharedPreferences("Settings", MODE_PRIVATE)
                .edit().putBoolean("bubble_enabled", true).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startForegroundService() {
        val channelId = "floating_bubble_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Monolith Floating Bubble", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Monolith يعمل في الخلفية")
            .setContentText("الفقاعة العائمة نشطة")
            .setSmallIcon(R.drawable.ic_bubble_monolith)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }

    private fun setupFloatingBubble() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_bubble, null)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        layoutParams.gravity = Gravity.TOP or Gravity.START
        layoutParams.x = 100
        layoutParams.y = 200

        val imgBubble = floatingView.findViewById<ImageView>(R.id.imgBubble)
        
        imgBubble.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                when (event?.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = layoutParams.x
                        initialY = layoutParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                        layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(floatingView, layoutParams)
                        // تحديث مكان لوحة الصوت لو مفتوحة
                        updateVolumePanelPosition()
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (Math.abs(event.rawX - initialTouchX) < 10 && Math.abs(event.rawY - initialTouchY) < 10) {
                            showBubbleMenu(imgBubble)
                        }
                        return true
                    }
                }
                return false
            }
        })

        windowManager.addView(floatingView, layoutParams)
    }

    private fun showBubbleMenu(anchorView: View) {
        val popupMenu = PopupMenu(this, anchorView)
        popupMenu.menu.add("🎛️ التحكم في الصوت") // الخيار الجديد
        popupMenu.menu.add("📸 سكرين شوت (قريباً)")
        popupMenu.menu.add("❌ إغلاق الفقاعة")
        
        popupMenu.setOnMenuItemClickListener { item ->
            when (item.title) {
                "🎛️ التحكم في الصوت" -> {
                    toggleVolumePanel()
                    true
                }
                "❌ إغلاق الفقاعة" -> {
                    getSharedPreferences("Settings", MODE_PRIVATE)
                        .edit().putBoolean("bubble_enabled", false).apply()
                    stopSelf()
                    Toast.makeText(this, "تم إغلاق الفقاعة", Toast.LENGTH_SHORT).show()
                    true
                }
                else -> {
                    Toast.makeText(this, "هذه الميزة قيد التطوير", Toast.LENGTH_SHORT).show()
                    true
                }
            }
        }
        popupMenu.show()
    }

    // ==========================================
    // كود لوحة الصوت العائمة
    // ==========================================
    private fun toggleVolumePanel() {
        if (volumePanelView != null) {
            // اللوحة مفتوحة، اقفلها
            windowManager.removeView(volumePanelView)
            volumePanelView = null
        } else {
            // اللوحة مقفولة، افتحها
            showVolumePanel()
        }
    }

    private fun showVolumePanel() {
        try {
            volumePanelView = LayoutInflater.from(this).inflate(R.layout.floating_volume_panel, null)
            
            val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, // عشان ميسرقش الكتابة
                PixelFormat.TRANSLUCENT
            )

            params.gravity = Gravity.TOP or Gravity.START
            updateVolumePanelCoordinates(params)

            val seekBar = volumePanelView?.findViewById<SeekBar>(R.id.seekBarVolume)
            val tvPercentage = volumePanelView?.findViewById<TextView>(R.id.tvVolumePercentage)
            val imgIcon = volumePanelView?.findViewById<ImageView>(R.id.imgVolumeIcon)

            // إعداد السلايدر بناءً على الصوت الحالي
            val maxVolume = audioManager.getStreamMaxVolume(currentStream)
            val currentVolume = audioManager.getStreamVolume(currentStream)
            
            seekBar?.max = 100
            seekBar?.progress = (currentVolume * 100) / maxVolume
            tvPercentage?.text = "${seekBar?.progress}%"

            seekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        tvPercentage?.text = "$progress%"
                        val targetVolume = (progress * maxVolume) / 100
                        
                        // تغيير الصوت الفعلي
                        audioManager.setStreamVolume(currentStream, targetVolume, 0)
                        
                        // اهتزاز خفيف (Haptic Feedback)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            vibrator.vibrate(android.os.VibrationEffect.createOneShot(20, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                        } else {
                            @Suppress("DEPRECATION")
                            vibrator.vibrate(20)
                        }
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })

            // زر تغيير المسار (Media / Ring / Alarm)
            val imgStreamSwitch = volumePanelView?.findViewById<ImageView>(R.id.imgStreamSwitch)
            imgStreamSwitch?.setOnClickListener {
                val streamMenu = PopupMenu(this, it)
                streamMenu.menu.add("🎵 الميديا")
                streamMenu.menu.add("🔔 الرنين")
                streamMenu.menu.add("⏰ المنبه")
                streamMenu.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.title) {
                        "🎵 الميديا" -> { currentStream = AudioManager.STREAM_MUSIC; updatePanelStream(seekBar, tvPercentage, imgIcon) }
                        "🔔 الرنين" -> { currentStream = AudioManager.STREAM_RING; updatePanelStream(seekBar, tvPercentage, imgIcon) }
                        "⏰ المنبه" -> { currentStream = AudioManager.STREAM_ALARM; updatePanelStream(seekBar, tvPercentage, imgIcon) }
                    }
                    true
                }
                streamMenu.show()
            }

            windowManager.addView(volumePanelView, params)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "فشل فتح لوحة الصوت", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updatePanelStream(seekBar: SeekBar?, tvPercentage: TextView?, imgIcon: ImageView?) {
        val maxVolume = audioManager.getStreamMaxVolume(currentStream)
        val currentVolume = audioManager.getStreamVolume(currentStream)
        
        seekBar?.progress = (currentVolume * 100) / maxVolume
        tvPercentage?.text = "${seekBar?.progress}%"
        
        // تغيير الأيقونة حسب المسار
        when (currentStream) {
            AudioManager.STREAM_RING -> imgIcon?.setImageResource(android.R.drawable.ic_lock_silent_mode_off)
            AudioManager.STREAM_ALARM -> imgIcon?.setImageResource(android.R.drawable.ic_lock_idle_alarm)
            else -> imgIcon?.setImageResource(R.drawable.ic_volume_up)
        }
    }

    private fun updateVolumePanelCoordinates(params: WindowManager.LayoutParams) {
        // بنخلي اللوحة تظهر فوق الفقاعة شوية
        params.x = layoutParams.x
        params.y = layoutParams.y - 120 
    }

    private fun updateVolumePanelPosition() {
        if (volumePanelView != null) {
            val params = volumePanelView?.layoutParams as WindowManager.LayoutParams
            updateVolumePanelCoordinates(params)
            windowManager.updateViewLayout(volumePanelView, params)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (::floatingView.isInitialized) {
                windowManager.removeView(floatingView)
            }
            if (volumePanelView != null) {
                windowManager.removeView(volumePanelView)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

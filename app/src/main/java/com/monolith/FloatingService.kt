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
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlin.math.abs

class FloatingService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingHubView: View
    private lateinit var layoutParams: WindowManager.LayoutParams

    // متغيرات لوحة الصوت
    private var volumePanelView: View? = null
    private lateinit var audioManager: AudioManager
    private var currentStream = AudioManager.STREAM_MUSIC
    private lateinit var vibrator: Vibrator

    // متغيرات السحب
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    override fun onCreate() {
        super.onCreate()
        try {
            startForegroundService()
            setupFloatingHub()
            
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
            val channel = NotificationChannel(channelId, "Monolith Floating Hub", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Monolith يعمل في الخلفية")
            .setContentText("مركز التحكم العائم نشط")
            .setSmallIcon(R.drawable.ic_bubble_monolith)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }

    private fun setupFloatingHub() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        // ربط التصميم الجديد (الشريط العائم)
        floatingHubView = LayoutInflater.from(this).inflate(R.layout.floating_hub, null)

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
        layoutParams.y = 300

        // إضافة مستمع السحب للشريط بالكامل
        floatingHubView.setOnTouchListener(object : View.OnTouchListener {
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
                        // سحب الشريط
                        val deltaX = event.rawX - initialTouchX
                        val deltaY = event.rawY - initialTouchY
                        if (abs(deltaX) > 10 || abs(deltaY) > 10) {
                            layoutParams.x = initialX + deltaX.toInt()
                            layoutParams.y = initialY + deltaY.toInt()
                            windowManager.updateViewLayout(floatingHubView, layoutParams)
                            updateVolumePanelPosition()
                            return true
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        return true
                    }
                }
                return false
            }
        })

        // إعداد أزرار الشريط
        floatingHubView.findViewById<LinearLayout>(R.id.btnCustomize)?.setOnClickListener {
            // حالياً: زر التخصيص يفتح لوحة الصوت (ممكن نغيرها بعدين)
            toggleVolumePanel()
        }

        floatingHubView.findViewById<LinearLayout>(R.id.btnHome)?.setOnClickListener {
            Toast.makeText(this, "الرئيسية", Toast.LENGTH_SHORT).show()
        }
        floatingHubView.findViewById<LinearLayout>(R.id.btnCode)?.setOnClickListener {
            Toast.makeText(this, "الأكواد", Toast.LENGTH_SHORT).show()
        }
        floatingHubView.findViewById<LinearLayout>(R.id.btnSchedule)?.setOnClickListener {
            Toast.makeText(this, "الجدولة", Toast.LENGTH_SHORT).show()
        }
        floatingHubView.findViewById<LinearLayout>(R.id.btnMessages)?.setOnClickListener {
            Toast.makeText(this, "الرسائل", Toast.LENGTH_SHORT).show()
        }

        windowManager.addView(floatingHubView, layoutParams)
    }

    // ==========================================
    // كود لوحة الصوت (كما هو بدون تغيير)
    // ==========================================
    private fun toggleVolumePanel() {
        if (volumePanelView != null) {
            windowManager.removeView(volumePanelView)
            volumePanelView = null
        } else {
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
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )

            params.gravity = Gravity.TOP or Gravity.START
            updateVolumePanelCoordinates(params)

            val seekBar = volumePanelView?.findViewById<SeekBar>(R.id.seekBarVolume)
            val tvPercentage = volumePanelView?.findViewById<TextView>(R.id.tvVolumePercentage)
            val imgIcon = volumePanelView?.findViewById<ImageView>(R.id.imgVolumeIcon)
            val btnClose = volumePanelView?.findViewById<ImageView>(R.id.btnCloseVolumePanel)

            val maxVolume = audioManager.getStreamMaxVolume(currentStream)
            val currentVolume = audioManager.getStreamVolume(currentStream)
            
            seekBar?.max = 100
            seekBar?.progress = (currentVolume * 100) / maxVolume
            tvPercentage?.text = "${seekBar?.progress}%"

            btnClose?.setOnClickListener { toggleVolumePanel() }

            seekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        tvPercentage?.text = "$progress%"
                        val targetVolume = (progress * maxVolume) / 100
                        audioManager.setStreamVolume(currentStream, targetVolume, 0)
                        
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
        
        when (currentStream) {
            AudioManager.STREAM_RING -> imgIcon?.setImageResource(android.R.drawable.ic_lock_silent_mode_off)
            AudioManager.STREAM_ALARM -> imgIcon?.setImageResource(android.R.drawable.ic_lock_idle_alarm)
            else -> imgIcon?.setImageResource(R.drawable.ic_volume_up)
        }
    }

    private fun updateVolumePanelCoordinates(params: WindowManager.LayoutParams) {
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
            if (::floatingHubView.isInitialized) {
                windowManager.removeView(floatingHubView)
            }
            if (volumePanelView != null) {
                windowManager.removeView(volumePanelView)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

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
import android.os.IBinder
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
    private lateinit var floatingBubbleView: View 
    private var floatingHubView: View? = null 
    
    private lateinit var bubbleLayoutParams: WindowManager.LayoutParams
    private var hubLayoutParams: WindowManager.LayoutParams? = null // إحداثيات الشريط الجديدة

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
            val channel = NotificationChannel(channelId, "Monolith Floating Hub", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Monolith يعمل في الخلفية")
            .setContentText("مركز التحكم العائم نشط")
            .setSmallIcon(R.drawable.ic_monolith_new)
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
        floatingBubbleView = LayoutInflater.from(this).inflate(R.layout.layout_floating_bubble, null)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        bubbleLayoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        bubbleLayoutParams.gravity = Gravity.TOP or Gravity.START
        bubbleLayoutParams.x = 100
        bubbleLayoutParams.y = 300

        val imgBubble = floatingBubbleView.findViewById<ImageView>(R.id.imgBubble)
        
        imgBubble.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isDragging = false

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                when (event?.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = bubbleLayoutParams.x
                        initialY = bubbleLayoutParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDragging = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = event.rawX - initialTouchX
                        val deltaY = event.rawY - initialTouchY
                        if (abs(deltaX) > 10 || abs(deltaY) > 10) {
                            isDragging = true
                            bubbleLayoutParams.x = initialX + deltaX.toInt()
                            bubbleLayoutParams.y = initialY + deltaY.toInt()
                            windowManager.updateViewLayout(floatingBubbleView, bubbleLayoutParams)
                            updateVolumePanelPosition()
                            hideHub() 
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!isDragging) {
                            if (floatingHubView != null) {
                                hideHub()
                            } else {
                                showHub()
                            }
                        }
                        return true
                    }
                }
                return false
            }
        })

        windowManager.addView(floatingBubbleView, bubbleLayoutParams)
    }

    private fun showHub() {
        try {
            floatingHubView = LayoutInflater.from(this).inflate(R.layout.floating_hub, null)
            
            val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            }

            hubLayoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )

            hubLayoutParams?.gravity = Gravity.TOP or Gravity.START
            // الشريط يظهر فوق الفقاعة شوية
            hubLayoutParams?.x = bubbleLayoutParams.x
            hubLayoutParams?.y = bubbleLayoutParams.y - 120 

            // ربط أزرار الشريط
            floatingHubView?.findViewById<LinearLayout>(R.id.btnVolume)?.setOnClickListener {
                toggleVolumePanel()
            }
            floatingHubView?.findViewById<LinearLayout>(R.id.btnCustomize)?.setOnClickListener {
                Toast.makeText(this, "شاشة التخصيص (قريباً)", Toast.LENGTH_SHORT).show()
            }
            floatingHubView?.findViewById<LinearLayout>(R.id.btnHome)?.setOnClickListener {
                Toast.makeText(this, "الرئيسية", Toast.LENGTH_SHORT).show()
            }
            floatingHubView?.findViewById<LinearLayout>(R.id.btnCode)?.setOnClickListener {
                Toast.makeText(this, "الأكواد", Toast.LENGTH_SHORT).show()
            }
            floatingHubView?.findViewById<LinearLayout>(R.id.btnSchedule)?.setOnClickListener {
                Toast.makeText(this, "الجدولة", Toast.LENGTH_SHORT).show()
            }
            floatingHubView?.findViewById<LinearLayout>(R.id.btnMessages)?.setOnClickListener {
                Toast.makeText(this, "الرسائل", Toast.LENGTH_SHORT).show()
            }

            // *** إضافة خاصية السحب للشريط ***
            floatingHubView?.setOnTouchListener(object : View.OnTouchListener {
                private var initialX = 0
                private var initialY = 0
                private var initialTouchX = 0f
                private var initialTouchY = 0f
                private var isDraggingHub = false

                override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                    when (event?.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initialX = hubLayoutParams?.x ?: 0
                            initialY = hubLayoutParams?.y ?: 0
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            isDraggingHub = false
                            return false // نرجع false عشان الأزرار تشتغل لو ضغطنا ضغطة عادية
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val deltaX = event.rawX - initialTouchX
                            val deltaY = event.rawY - initialTouchY
                            if (abs(deltaX) > 15 || abs(deltaY) > 15) {
                                isDraggingHub = true
                                hubLayoutParams?.x = initialX + deltaX.toInt()
                                hubLayoutParams?.y = initialY + deltaY.toInt()
                                hubLayoutParams?.let { windowManager.updateViewLayout(floatingHubView, it) }
                                updateVolumePanelPosition()
                                return true // نستهلك الحركة عشان ميتحركش التطبيق اللي وراه
                            }
                            return false
                        }
                        MotionEvent.ACTION_UP -> {
                            return isDraggingHub // لو كنا بنسحب، نستهلك الحدث
                        }
                    }
                    return false
                }
            })

            windowManager.addView(floatingHubView, hubLayoutParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun hideHub() {
        if (floatingHubView != null) {
            windowManager.removeView(floatingHubView)
            floatingHubView = null
            hubLayoutParams = null
        }
    }

    // ==========================================
    // كود لوحة الصوت
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

    // تحديث مكان لوحة الصوت بناءً على حركة الشريط (أو الفقاعة)
    private fun updateVolumePanelCoordinates(params: WindowManager.LayoutParams) {
        if (floatingHubView != null && hubLayoutParams != null) {
            // لو الشريط مفتوح، لوحة الصوت تظهر فوق الشريط
            params.x = hubLayoutParams?.x ?: bubbleLayoutParams.x
            params.y = (hubLayoutParams?.y ?: bubbleLayoutParams.y) - 120 
        } else {
            // لو الشريط مقفول، لوحة الصوت تظهر فوق الفقاعة
            params.x = bubbleLayoutParams.x
            params.y = bubbleLayoutParams.y - 120 
        }
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (::floatingBubbleView.isInitialized) {
                windowManager.removeView(floatingBubbleView)
            }
            if (floatingHubView != null) {
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

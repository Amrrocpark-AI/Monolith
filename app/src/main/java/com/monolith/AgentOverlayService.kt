package com.monolith

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager

class AgentOverlayService : Service() {

    companion object {
        var instance: AgentOverlayService? = null
            private set
    }

    private lateinit var windowManager: WindowManager
    private var overlayView: AgentOverlayView? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        setupOverlay()
    }

    private fun setupOverlay() {
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START

        overlayView = AgentOverlayView(this)
        windowManager.addView(overlayView, params)
    }

    fun showClick(x: Int, y: Int, step: Int) {
        overlayView?.addClickEvent(x.toFloat(), y.toFloat(), step)
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayView?.let { windowManager.removeView(it) }
        overlayView = null
        instance = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

class AgentOverlayView(context: Context) : View(context) {
    
    private val cursorPaint = Paint().apply {
        color = Color.parseColor("#00D4FF")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val ripplePaint = Paint().apply {
        color = Color.parseColor("#00D4FF")
        style = Paint.Style.STROKE
        strokeWidth = 5f
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 50f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        setShadowLayer(5f, 0f, 0f, Color.BLACK)
    }

    private var cursorX = -100f
    private var cursorY = -100f
    private var isCursorVisible = false

    private data class ClickEvent(val x: Float, val y: Float, val step: Int, val startTime: Long)
    private val clickEvents = mutableListOf<ClickEvent>()
    private val handler = Handler(Looper.getMainLooper())
    private val animationRunnable = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            clickEvents.removeAll { now - it.startTime > 1000 }
            if (isCursorVisible || clickEvents.isNotEmpty()) {
                invalidate()
                handler.postDelayed(this, 16)
            }
        }
    }

    fun addClickEvent(x: Float, y: Float, step: Int) {
        cursorX = x
        cursorY = y
        isCursorVisible = true
        clickEvents.add(ClickEvent(x, y, step, System.currentTimeMillis()))
        handler.removeCallbacks(animationRunnable)
        handler.post(animationRunnable)
        
        handler.postDelayed({
            isCursorVisible = false
        }, 1500)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = System.currentTimeMillis()

        if (isCursorVisible) {
            canvas.drawCircle(cursorX, cursorY, 25f, cursorPaint)
        }

        for (event in clickEvents) {
            val elapsed = now - event.startTime
            if (elapsed < 1000) {
                val progress = elapsed / 1000f
                val radius = 25f + (100f * progress)
                ripplePaint.alpha = (255 * (1f - progress)).toInt()
                
                canvas.drawCircle(event.x, event.y, radius, ripplePaint)
                
                textPaint.alpha = (255 * (1f - progress)).toInt()
                canvas.drawText(event.step.toString(), event.x, event.y - 70f, textPaint)
            }
        }
    }
}

package com.monolith

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.VelocityTracker
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class SovereignParticleView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback, Runnable {

    private var thread: Thread? = null
    @Volatile private var isRunning = false

    private val particles = mutableListOf<Particle>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var originalBitmap: Bitmap? = null
    private var maskBitmap: Bitmap? = null

    enum class Mode { AUTO_MASK, MANUAL_SWIPE, TEXT_MORPH }
    var currentMode = Mode.AUTO_MASK

    private var dispersionPower = 15f
    private var particleSize = 8f
    private var targetText = "MONOLITH"
    private var textColor = Color.parseColor("#00D4FF")

    private var velocityTracker: VelocityTracker? = null
    private var lastX = 0f
    private var lastY = 0f

    private var touchX = -1000f
    private var touchY = -1000f
    private var isTouching = false

    init {
        holder.addCallback(this)
    }

    fun setImages(original: Bitmap, mask: Bitmap? = null) {
        this.originalBitmap = original
        this.maskBitmap = mask
        resetParticles()
    }

    fun setMode(mode: Mode) {
        currentMode = mode
        particles.clear()
        if (mode == Mode.TEXT_MORPH) {
            generateTextParticles()
        }
    }

    // *** هذه هي الدالة التي كانت ناقصة ***
    fun setTextAndMode(text: String) {
        this.targetText = text
        this.currentMode = Mode.TEXT_MORPH
        particles.clear()
        generateTextParticles()
    }

    fun setDispersionPower(power: Float) { dispersionPower = power }
    fun setParticleSize(size: Float) { particleSize = size }

    private fun resetParticles() {
        particles.clear()
        val bmp = originalBitmap ?: return
        val mask = maskBitmap
        val step = 10

        for (x in 0 until bmp.width step step) {
            for (y in 0 until bmp.height step step) {
                if (mask != null) {
                    val pixel = mask.getPixel(x, y)
                    if (Color.red(pixel) > 150 && Color.alpha(pixel) > 0) {
                        createParticle(x, y, bmp.getPixel(x, y))
                    }
                } else {
                    createParticle(x, y, bmp.getPixel(x, y))
                }
            }
        }
    }

    private fun createParticle(x: Int, y: Int, color: Int) {
        val angle = Random.nextFloat() * Math.PI * 2
        val speed = Random.nextFloat() * dispersionPower + 2f
        particles.add(Particle(
            x.toFloat(), y.toFloat(),
            (cos(angle) * speed).toFloat(), (sin(angle) * speed).toFloat(),
            color
        ))
    }

    private fun generateTextParticles() {
        particles.clear()
        val width = width.takeIf { it > 0 } ?: 1080
        val height = height.takeIf { it > 0 } ?: 1920
        
        val textBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(textBitmap)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = textColor
            textSize = 150f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        
        val yPos = (height / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
        canvas.drawText(targetText, width / 2f, yPos, textPaint)

        for (x in 0 until width step 8) {
            for (y in 0 until height step 8) {
                if (Color.alpha(textBitmap.getPixel(x, y)) > 0) {
                    particles.add(Particle(
                        Random.nextFloat() * width,
                        Random.nextFloat() * height,
                        0f, 0f,
                        textColor,
                        targetX = x.toFloat(),
                        targetY = y.toFloat()
                    ))
                }
            }
        }
    }

    override fun run() {
        while (isRunning) {
            val canvas = holder.lockCanvas() ?: continue
            updatePhysics()
            drawParticles(canvas)
            holder.unlockCanvasAndPost(canvas)
            Thread.sleep(16)
        }
    }

    private fun updatePhysics() {
        for (p in particles) {
            if (currentMode == Mode.TEXT_MORPH) {
                val dx = p.targetX - p.x
                val dy = p.targetY - p.y
                p.vx += dx * 0.05f
                p.vy += dy * 0.05f
                p.vx *= 0.85f
                p.vy *= 0.85f
            } else {
                p.x += p.vx
                p.y += p.vy
                p.vx *= 0.98f
                p.vy *= 0.98f
            }

            if (isTouching && currentMode != Mode.TEXT_MORPH) {
                val dx = touchX - p.x
                val dy = touchY - p.y
                val dist = sqrt(dx * dx + dy * dy)
                if (dist < 300f) {
                    val force = (300f - dist) / 300f * 2f
                    p.vx += (dx / dist) * force
                    p.vy += (dy / dist) * force
                }
            }
        }
    }

    private fun drawParticles(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)
        for (p in particles) {
            paint.color = p.color
            canvas.drawCircle(p.x, p.y, particleSize, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (currentMode == Mode.TEXT_MORPH) return true

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isTouching = true
                touchX = event.x
                touchY = event.y
                velocityTracker = VelocityTracker.obtain()
                velocityTracker?.addMovement(event)
            }
            MotionEvent.ACTION_MOVE -> {
                touchX = event.x
                touchY = event.y
                velocityTracker?.addMovement(event)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isTouching = false
                velocityTracker?.recycle()
                velocityTracker = null
            }
        }
        return true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        isRunning = true
        thread = Thread(this)
        thread?.start()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
    override fun surfaceDestroyed(holder: SurfaceHolder) {
        isRunning = false
        thread?.join()
    }

    data class Particle(
        var x: Float, var y: Float,
        var vx: Float, var vy: Float,
        val color: Int,
        val targetX: Float = x, val targetY: Float = y
    )
}

package com.monolith

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class ParticleDissolveView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var isAnimating = false
    private var isVortexVisible = false

    // إعدادات الثقب الأسود
    private var vortexCenterX = 0f
    private var vortexCenterY = 0f
    private var vortexBitmap: Bitmap? = null

    private var originalBitmap: Bitmap? = null
    private var particles = mutableListOf<Particle>()
    private var startX = 0f
    private var startY = 0f
    private var progress = 0f
    private var onAnimationEnd: (() -> Unit)? = null

    data class Particle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var color: Int,
        var size: Float,
        var life: Float = 1f
    )

    init {
        // تحميل صورة الدوامة (تأكد من أن الاسم مطابق للموجود في مجلد drawable)
        try {
            vortexBitmap = BitmapFactory.decodeResource(resources, R.drawable.vortex_bg_edited)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun startDissolve(
        bmp: Bitmap,
        targetX: Float,
        targetY: Float,
        centerX: Float,
        centerY: Float,
        onEnd: () -> Unit
    ) {
        this.originalBitmap = bmp
        this.startX = targetX
        this.startY = targetY
        this.vortexCenterX = centerX
        this.vortexCenterY = centerY
        this.onAnimationEnd = onEnd
        this.isAnimating = true
        this.isVortexVisible = true
        this.progress = 0f
        this.particles.clear()

        // استخراج الألوان من الصورة لتكوين الجزيئات
        generateParticles(bmp, targetX, targetY)
        invalidate()
    }

    private fun generateParticles(bmp: Bitmap, x: Float, y: Float) {
        // نأخذ عينة من بكسلات الصورة السفلية لتحويلها لجزيئات
        val step = 4 // كل 4 بكسل نأخذ لوناً
        for (i in 0 until bmp.width step step) {
            for (j in (bmp.height / 2) until bmp.height step step) { // الجزء السفلي فقط
                val pixel = bmp.getPixel(i, j)
                if (Color.alpha(pixel) > 50) {
                    val angle = Random.nextFloat() * Math.PI * 2
                    val speed = Random.nextFloat() * 5f + 2f
                    particles.add(
                        Particle(
                            x = x + i,
                            y = y + j,
                            vx = (cos(angle) * speed).toFloat(),
                            vy = (sin(angle) * speed).toFloat(),
                            color = pixel,
                            size = Random.nextFloat() * 4f + 2f
                        )
                    )
                }
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. رسم الثقب الأسود (الدوامة الثابتة)
        if (isVortexVisible && vortexBitmap != null) {
            val left = vortexCenterX - (vortexBitmap!!.width / 2f)
            val top = vortexCenterY - (vortexBitmap!!.height / 2f)
            canvas.drawBitmap(vortexBitmap!!, left, top, paint)
        }

        // 2. رسم الجزيئات المتطايرة (التذويب)
        if (isAnimating) {
            progress += 0.02f
            
            for (p in particles) {
                // جذب الجزيئات نحو مركز الثقب
                val dx = vortexCenterX - p.x
                val dy = vortexCenterY - p.y
                val distance = kotlin.math.hypot(dx, dy)

                if (distance > 20f) {
                    // قوة الجاذبية
                    p.vx += (dx / distance) * 1.5f
                    p.vy += (dy / distance) * 1.5f
                } else {
                    p.life -= 0.1f // اختفاء عند الوصول للمركز
                }

                p.x += p.vx
                p.y += p.vy
                p.life -= 0.015f // تلاشي تدريجي

                if (p.life > 0) {
                    paint.color = p.color
                    paint.alpha = (p.life * 255).toInt()
                    canvas.drawCircle(p.x, p.y, p.size, paint)
                }
            }

            // إزالة الجزيئات الميتة
            particles.removeAll { it.life <= 0f }

            // التحقق من انتهاء الأنيميشن
            if (progress >= 1f && particles.isEmpty()) {
                isAnimating = false
                isVortexVisible = false
                onAnimationEnd?.invoke()
            } else {
                invalidate() // إعادة الرسم
            }
        }
    }
}

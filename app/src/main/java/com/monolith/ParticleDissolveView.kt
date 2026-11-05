package com.monolith

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.View
import android.view.animation.AccelerateInterpolator
import kotlin.math.*
import kotlin.random.Random

class ParticleDissolveView(context: Context) : View(context) {

    private var particles = mutableListOf<Particle>()
    private var animator: ValueAnimator? = null
    private var progress = 0f
    private var vortexRotation = 0f
    
    // متغيرات لرسم الثقب الأسود من الصورة
    private var vortexBitmap: Bitmap? = null
    private val vortexPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        // هذا السطر هو "السحر" الذي يجعل اللون الأسود في الصورة شفافاً
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    }

    data class Particle(
        var startX: Float, var startY: Float,
        var color: Int,
        var currentX: Float = startX, var currentY: Float = startY,
        var radius: Float = Random.nextFloat() * 2f + 1f,
        var angle: Float = Random.nextFloat() * 360f,
        var speed: Float = Random.nextFloat() * 0.5f + 0.5f
    )

    init {
        try {
            // تحميل الصورة التي قمت برفعها
            val originalBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.vortex_bg)
            if (originalBitmap != null) {
                // تصغير الصورة قليلاً لتناسب حجم الشاشة
                val targetSize = 800
                vortexBitmap = Bitmap.createScaledBitmap(originalBitmap, targetSize, (targetSize * originalBitmap.height / originalBitmap.width), true)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun startDissolve(bitmap: Bitmap, originX: Float, originY: Float, targetX: Float, targetY: Float, onComplete: () -> Unit) {
        particles.clear()
        progress = 0f

        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val step = 2 
        for (y in 0 until height step step) {
            for (x in 0 until width step step) {
                val index = y * width + x
                val color = pixels[index]
                val alpha = Color.alpha(color)
                
                if (alpha > 50) {
                    val realX = originX + x.toFloat()
                    val realY = originY + y.toFloat()
                    val fallSpeed = if (y > height / 2) 1.5f else 0.8f
                    particles.add(Particle(realX, realY, color, speed = fallSpeed))
                }
            }
        }

        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2000 
            interpolator = AccelerateInterpolator(1.5f)
            addUpdateListener { animation ->
                progress = animation.animatedValue as Float
                vortexRotation += 8f // دوران الصورة ببطء لإعطاء إيحاء بالحركة
                updateParticles(targetX, targetY)
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    onComplete()
                }
            })
            start()
        }
    }

    private fun updateParticles(targetX: Float, targetY: Float) {
        for (p in particles) {
            val dx = targetX - p.startX
            val dy = targetY - p.startY
            val fallProgress = (progress * p.speed).coerceAtMost(1f)
            val swirlProgress = (progress - 0.2f).coerceAtLeast(0f) / 0.8f
            
            val baseX = p.startX + dx * fallProgress
            val baseY = p.startY + dy * fallProgress
            
            val swirlRadius = 150f * (1f - swirlProgress) 
            p.angle += 30f * p.speed * swirlProgress
            
            val rad = Math.toRadians(p.angle.toDouble())
            p.currentX = baseX + (swirlRadius * cos(rad) * swirlProgress).toFloat()
            p.currentY = baseY + (swirlRadius * sin(rad) * swirlProgress).toFloat()
            
            p.radius = (Random.nextFloat() * 2f + 1f) * (1f - fallProgress * 0.9f)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val cx = width / 2f
        val cy = height * 0.8f

        // 1. رسم صورة الثقب الأسود وتدويرها
        vortexBitmap?.let { bmp ->
            canvas.save()
            canvas.translate(cx, cy)
            canvas.rotate(vortexRotation)
            // رسم الصورة في المنتصف
            canvas.drawBitmap(bmp, -bmp.width / 2f, -bmp.height / 2f, vortexPaint)
            canvas.restore()
        }

        // 2. رسم الجزيئات (الغبار الرقمي) المتطايرة فوق الصورة
        val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (p in particles) {
            if (p.radius > 0) {
                particlePaint.color = p.color
                particlePaint.alpha = ((1f - progress) * 255).toInt().coerceIn(0, 255)
                
                canvas.save()
                canvas.translate(p.currentX, p.currentY)
                val angleToCenter = Math.toDegrees(atan2((cy - p.currentY).toDouble(), (cx - p.currentX).toDouble())).toFloat()
                canvas.rotate(angleToCenter)
                // رسم الجزيئات كخطوط ممتدة
                canvas.drawOval(-p.radius, -p.radius * 3f, p.radius, p.radius * 3f, particlePaint)
                canvas.restore()
            }
        }
    }
}

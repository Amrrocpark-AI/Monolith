package com.monolith

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.*
import kotlin.random.Random

class ParticleDissolveView(context: Context) : View(context) {

    private var particles = mutableListOf<Particle>()
    private var animator: ValueAnimator? = null
    private var progress = 0f
    
    // ألوان الثقب الأسود (بنفسجي، أزرق، أسود)
    private val vortexPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var vortexRotation = 0f

    data class Particle(
        var startX: Float, var startY: Float,
        var color: Int,
        var currentX: Float = startX, var currentY: Float = startY,
        var radius: Float = Random.nextFloat() * 4f + 2f,
        var angle: Float = Random.nextFloat() * 360f,
        var speed: Float = Random.nextFloat() * 0.5f + 0.5f
    )

    fun startDissolve(bitmap: Bitmap, originX: Float, originY: Float, targetX: Float, targetY: Float, onComplete: () -> Unit) {
        particles.clear()
        progress = 0f

        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // استخراج الجزيئات من الصورة (الخطوة = 2 لأداء أفضل وتفاصيل أدق)
        val step = 2 
        for (y in 0 until height step step) {
            for (x in 0 until width step step) {
                val index = y * width + x
                val color = pixels[index]
                val alpha = Color.alpha(color)
                
                if (alpha > 100) {
                    val realX = originX + x.toFloat()
                    val realY = originY + y.toFloat()
                    particles.add(Particle(realX, realY, color))
                }
            }
        }

        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2000 // ثانيتان لتأثير درامي
            interpolator = DecelerateInterpolator() // يبدأ سريعاً ثم يتباطأ عند الابتلاع
            addUpdateListener { animation ->
                progress = animation.animatedValue as Float
                vortexRotation += 15f // دوران الدوامة
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
            // حساب المسافة من البداية إلى الثقب
            val dx = targetX - p.startX
            val dy = targetY - p.startY
            val distance = sqrt(dx * dx + dy * dy)
            
            // الجزيئات تتحرك في البداية بشكل خطي، ثم تلتف حول الثقب
            val linearProgress = (progress * 2.5f).coerceAtMost(1f) 
            val swirlProgress = (progress - 0.4f).coerceAtLeast(0f) / 0.6f // يبدأ الالتفاف بعد 40%

            val baseX = p.startX + dx * linearProgress
            val baseY = p.startY + dy * linearProgress
            
            // الالتفاف الحلزوني (Swirl)
            val swirlRadius = 100f * (1f - swirlProgress) // يتقلص القطر
            p.angle += 20f * p.speed * swirlProgress
            
            val rad = Math.toRadians(p.angle.toDouble())
            p.currentX = baseX + (swirlRadius * cos(rad) * swirlProgress).toFloat()
            p.currentY = baseY + (swirlRadius * sin(rad) * swirlProgress).toFloat()
            
            // تصغير الجزيئات وهي تسقط
            p.radius = (Random.nextFloat() * 4f + 2f) * (1f - progress * 0.8f)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val cx = width / 2f
        val cy = height * 0.8f

        // 1. رسم هالة التوهج (Glow)
        glowPaint.shader = RadialGradient(
            cx, cy, 300f,
            intArrayOf(Color.parseColor("#3300FFFF"), Color.parseColor("#00000000")),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, 300f, glowPaint)

        // 2. رسم الدوامة (Vortex) باستخدام SweepGradient
        val vortexShader = SweepGradient(
            cx, cy,
            intArrayOf(
                Color.parseColor("#00E5FF"), // سماوي
                Color.parseColor("#8A2BE2"), // بنفسجي
                Color.BLACK,
                Color.parseColor("#8A2BE2"),
                Color.parseColor("#00E5FF")
            ),
            floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)
        )
        vortexPaint.shader = vortexShader
        
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(vortexRotation)
        // رسم الدوامة على شكل قرص بيضاوي لتعطي إيحاء ثلاثي الأبعاد
        canvas.scale(1f, 0.6f) 
        canvas.drawCircle(0f, 0f, 150f, vortexPaint)
        canvas.restore()

        // 3. رسم الجزيئات المتطايرة
        val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (p in particles) {
            if (p.radius > 0) {
                particlePaint.color = p.color
                // تقليل الشفافية تدريجياً
                particlePaint.alpha = ((1f - progress) * 255).toInt().coerceIn(0, 255)
                canvas.drawCircle(p.currentX, p.currentY, p.radius, particlePaint)
            }
        }
    }
}

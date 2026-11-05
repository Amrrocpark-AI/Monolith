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
    
    // إعدادات رسم الثقب الأسود
    private val vortexRotation = floatArrayOf(0f, 0f) // زوايا دوران الأذرع
    private val cyanGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val purpleGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val voidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val eventHorizonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { 
        color = Color.CYAN
        style = Paint.Style.STROKE
        strokeWidth = 4f
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
        // إعداد تأثير التوهج الحقيقي (Blur)
        val blurFilter = BlurMaskFilter(50f, BlurMaskFilter.Blur.NORMAL)
        cyanGlowPaint.maskFilter = blurFilter
        purpleGlowPaint.maskFilter = blurFilter
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
                vortexRotation[0] += 25f
                vortexRotation[1] -= 35f
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
        val holeRadius = 120f

        // 1. رسم الهالة الخارجية (النيون السماوي والأرجواني)
        cyanGlowPaint.color = Color.parseColor("#AA00E5FF")
        purpleGlowPaint.color = Color.parseColor("#AA8A2BE2")
        
        canvas.drawCircle(cx, cy, holeRadius + 80f, cyanGlowPaint)
        canvas.drawCircle(cx, cy, holeRadius + 40f, purpleGlowPaint)

        // 2. رسم أذرع الدوامة (Spiral Arms) لتبدو 3D
        val spiralPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 25f
            maskFilter = BlurMaskFilter(20f, BlurMaskFilter.Blur.NORMAL)
        }

        canvas.save()
        canvas.translate(cx, cy)
        
        // الذراع الأولى (بنفسجي)
        spiralPaint.color = Color.parseColor("#8A2BE2")
        canvas.save()
        canvas.rotate(vortexRotation[0])
        drawSpiral(canvas, holeRadius + 50f, spiralPaint)
        canvas.restore()

        // الذراع الثانية (سماوي)
        spiralPaint.color = Color.parseColor("#00E5FF")
        canvas.save()
        canvas.rotate(vortexRotation[1])
        drawSpiral(canvas, holeRadius + 50f, spiralPaint)
        canvas.restore()

        canvas.restore()

        // 3. رسم مركز الثقب الأسود (الفراغ الحالك)
        canvas.drawCircle(cx, cy, holeRadius, voidPaint)
        
        // 4. رسم أفق الحدث (الخط المضيء حول الثقب)
        val horizonGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CC00FFFF")
            style = Paint.Style.STROKE
            strokeWidth = 6f
            maskFilter = BlurMaskFilter(15f, BlurMaskFilter.Blur.SOLID)
        }
        canvas.drawCircle(cx, cy, holeRadius, horizonGlow)

        // 5. رسم الجزيئات (الغبار الرقمي)
        val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (p in particles) {
            if (p.radius > 0) {
                particlePaint.color = p.color
                particlePaint.alpha = ((1f - progress) * 255).toInt().coerceIn(0, 255)
                
                // رسم الجزيئات كخطوط ممتدة باتجاه المركز
                canvas.save()
                canvas.translate(p.currentX, p.currentY)
                val angleToCenter = Math.toDegrees(atan2((cy - p.currentY).toDouble(), (cx - p.currentX).toDouble())).toFloat()
                canvas.rotate(angleToCenter)
                canvas.drawOval(-p.radius, -p.radius * 3f, p.radius, p.radius * 3f, particlePaint)
                canvas.restore()
            }
        }
    }

    // دالة مساعدة لرسم شكل حلزوني
    private fun drawSpiral(canvas: Canvas, radius: Float, paint: Paint) {
        val path = Path()
        path.moveTo(radius, 0f)
        // رسم منحنى بيزير يلتف نحو الداخل
        path.cubicTo(
            radius * 0.5f, radius * 0.5f, 
            -radius * 0.5f, radius * 0.2f, 
            radius * 0.2f, 0f
        )
        canvas.drawPath(path, paint)
    }
}

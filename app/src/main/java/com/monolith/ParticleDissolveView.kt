package com.monolith

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.*
import kotlin.random.Random

class ParticleDissolveView(context: Context) : View(context) {

    private var particles = mutableListOf<Particle>()
    private var animator: ValueAnimator? = null
    private var progress = 0f
    
    private val blackHolePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            0f, 0f, 150f,
            intArrayOf(Color.BLACK, Color.parseColor("#1A0033"), Color.parseColor("#00E5FF")),
            floatArrayOf(0f, 0.7f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    data class Particle(
        var startX: Float, var startY: Float,
        var color: Int,
        var currentX: Float = startX, var currentY: Float = startY,
        var radius: Float = Random.nextFloat() * 3f + 1f,
        var angle: Float = Random.nextFloat() * 360f
    )

    fun startDissolve(bitmap: Bitmap, originX: Float, originY: Float, targetX: Float, targetY: Float, onComplete: () -> Unit) {
        particles.clear()
        progress = 0f

        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val step = 3 
        for (y in 0 until height step step) {
            for (x in 0 until width step step) {
                val index = y * width + x
                val color = pixels[index]
                val alpha = Color.alpha(color)
                
                if (alpha > 50) {
                    val realX = originX + (x.toFloat() / width) * this.width
                    val realY = originY + (y.toFloat() / height) * this.height
                    particles.add(Particle(realX, realY, color))
                }
            }
        }

        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1500 
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                progress = animation.animatedValue as Float
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
            val distance = sqrt(dx * dx + dy * dy)
            
            val swirlRadius = distance * (1f - progress)
            p.angle += 15f 
            
            val rad = Math.toRadians(p.angle.toDouble())
            p.currentX = targetX + (swirlRadius * cos(rad)).toFloat()
            p.currentY = targetY + (swirlRadius * sin(rad)).toFloat()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        canvas.save()
        canvas.translate(width / 2f, height * 0.8f) 
        canvas.drawCircle(0f, 0f, 150f, blackHolePaint)
        canvas.restore()

        val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (p in particles) {
            particlePaint.color = p.color
            particlePaint.alpha = ((1f - progress) * 255).toInt().coerceIn(0, 255)
            val currentRadius = p.radius * (1f - progress * 0.5f)
            canvas.drawCircle(p.currentX, p.currentY, currentRadius, particlePaint)
        }
    }
}

package com.monolith // تأكد أن هذا هو اسم الحزمة الخاص بك

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.hypot

class MainActivity : AppCompatActivity() {

    private lateinit var icon: ImageView
    private lateinit var vortex: ImageView

    // متغيرات لحفظ إحداثيات السحب
    private var dX = 0f
    private var dY = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        icon = findViewById(R.id.ivDraggableIcon)
        vortex = findViewById(R.id.ivVortex)

        // 1. تفعيل السحب والإفلات (Drag & Drop) للأيقونة
        icon.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dX = view.x - event.rawX
                    dY = view.y - event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    view.animate()
                        .x(event.rawX + dX)
                        .y(event.rawY + dY)
                        .setDuration(0)
                        .start()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // 2. التحقق من المسافة عند إفلات الأيقونة
                    checkDistanceAndAbsorb()
                    true
                }
                else -> false
            }
        }
    }

    private fun checkDistanceAndAbsorb() {
        // حساب مركز الأيقونة
        val iconCenterX = icon.x + icon.width / 2f
        val iconCenterY = icon.y + icon.height / 2f

        // حساب مركز الدوامة
        val vortexCenterX = vortex.x + vortex.width / 2f
        val vortexCenterY = vortex.y + vortex.height / 2f

        // حساب المسافة بينهما (قانون فيثاغورس)
        val distance = hypot((iconCenterX - vortexCenterX).toDouble(), (iconCenterY - vortexCenterY).toDouble())

        // إذا كانت المسافة أقل من 250 بكسل (أي أنها قريبة جداً من الدوامة)
        if (distance < 250) {
            animateAbsorption(vortexCenterX, vortexCenterY)
        }
    }

    private fun animateAbsorption(targetX: Float, targetY: Float) {
        // 3. تأثير الامتصاص والذوبان
        // أ. تحريك الأيقونة نحو مركز الدوامة
        val moveToCenter = ObjectAnimator.ofFloat(icon, "x", targetX - icon.width / 2f)
        val moveToCenterY = ObjectAnimator.ofFloat(icon, "y", targetY - icon.height / 2f)

        // ب. تأثير "الذوبان" (تصغير الطول من الأعلى للأسفل)
        // نقطة الارتكاز (pivotY) في أعلى الأيقونة، لكي يذوب الجزء السفلي للأعلى
        icon.pivotY = 0f 
        val melt = ObjectAnimator.ofFloat(icon, "scaleY", 1f, 0f)

        // ج. التلاشي التدريجي
        val fadeOut = ObjectAnimator.ofFloat(icon, "alpha", 1f, 0f)

        // دمج الحركات معاً في وقت واحد
        val animatorSet = AnimatorSet()
        animatorSet.playTogether(moveToCenter, moveToCenterY, melt, fadeOut)
        animatorSet.duration = 800 // مدة الحركة (0.8 ثانية)
        animatorSet.interpolator = AccelerateInterpolator() // تسريع الحركة كلما اقتربت من الثقب
        animatorSet.start()
    }
}

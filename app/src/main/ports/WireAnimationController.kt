package com.monolith.graph.ports.connections

import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator

class WireAnimationController {

    private val animators = LinkedHashMap<String, ValueAnimator>()
    private val progress = LinkedHashMap<String, Float>()

    var onProgressChanged: ((String, Float) -> Unit)? = null

    fun start(
        connectionId: String,
        duration: Long = 1700L
    ) {
        stop(connectionId)

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration.coerceAtLeast(100L)
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()

            addUpdateListener {
                val value = it.animatedValue as Float
                progress[connectionId] = value
                onProgressChanged?.invoke(connectionId, value)
            }
        }

        animators[connectionId] = animator
        animator.start()
    }

    fun getProgress(connectionId: String): Float =
        progress[connectionId] ?: 0f

    fun stop(connectionId: String) {
        animators.remove(connectionId)?.cancel()
        progress.remove(connectionId)
    }

    fun pauseAll() {
        animators.values.forEach {
            if (it.isRunning) it.pause()
        }
    }

    fun resumeAll() {
        animators.values.forEach {
            if (it.isPaused) it.resume()
        }
    }

    fun stopAll() {
        animators.values.forEach { it.cancel() }
        animators.clear()
        progress.clear()
    }
}

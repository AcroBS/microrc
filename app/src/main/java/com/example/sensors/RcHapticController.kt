package com.example.sensors

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Tactile haptic feedback controller for RC car collisions, mine explosions,
 * power-up activations, direct track pickups, and portal warps.
 */
class RcHapticController(context: Context) {

    private val appContext = context.applicationContext ?: context

    private val vibrator: Vibrator? by lazy {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        }.getOrNull()
    }

    @Volatile
    var enabled: Boolean = true

    private var lastCollisionMs: Long = 0L
    private var lastPowerUpMs: Long = 0L
    private var lastCurbTickMs: Long = 0L

    fun onCollision(heavy: Boolean = false) {
        if (!enabled) return
        val now = SystemClock.uptimeMillis()
        val minGap = if (heavy) 90L else 145L
        if (now - lastCollisionMs < minGap) return
        lastCollisionMs = now

        if (heavy) {
            vibrateWaveform(
                timings = longArrayOf(0L, 48L, 30L, 72L),
                amplitudes = intArrayOf(0, 255, 0, 210),
                fallbackMs = 95L
            )
        } else {
            vibrateOneShot(durationMs = 34L, amplitude = 195)
        }
    }

    fun onPowerUpUsed() {
        if (!enabled) return
        val now = SystemClock.uptimeMillis()
        if (now - lastPowerUpMs < 80L) return
        lastPowerUpMs = now

        vibrateWaveform(
            timings = longArrayOf(0L, 20L, 24L, 38L),
            amplitudes = intArrayOf(0, 170, 0, 235),
            fallbackMs = 45L
        )
    }

    fun onMineDropped() {
        if (!enabled) return
        vibrateOneShot(durationMs = 28L, amplitude = 215)
    }

    fun onPortalWarp() {
        if (!enabled) return
        vibrateWaveform(
            timings = longArrayOf(0L, 18L, 18L, 26L, 18L, 42L),
            amplitudes = intArrayOf(0, 140, 0, 195, 0, 250),
            fallbackMs = 60L
        )
    }

    fun onCurbTick() {
        if (!enabled) return
        val now = SystemClock.uptimeMillis()
        if (now - lastCurbTickMs < 110L) return
        lastCurbTickMs = now
        vibrateOneShot(durationMs = 11L, amplitude = 85)
    }

    private fun vibrateOneShot(durationMs: Long, amplitude: Int) {
        val vib = vibrator ?: return
        runCatching {
            if (!vib.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(
                    VibrationEffect.createOneShot(
                        durationMs,
                        amplitude.coerceIn(1, 255)
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(durationMs)
            }
        }
    }

    private fun vibrateWaveform(timings: LongArray, amplitudes: IntArray, fallbackMs: Long) {
        val vib = vibrator ?: return
        runCatching {
            if (!vib.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (vib.hasAmplitudeControl()) {
                    vib.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                } else {
                    vib.vibrate(VibrationEffect.createOneShot(fallbackMs, VibrationEffect.DEFAULT_AMPLITUDE))
                }
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(fallbackMs)
            }
        }
    }
}

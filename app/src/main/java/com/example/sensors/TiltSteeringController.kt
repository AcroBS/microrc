package com.example.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.Surface
import android.view.WindowManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

class TiltSteeringController(private val context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val tiltSensor: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _steerValue = MutableStateFlow(0f)
    val steerValue: StateFlow<Float> = _steerValue.asStateFlow()

    private val _rawTilt = MutableStateFlow(0f)
    val rawTilt: StateFlow<Float> = _rawTilt.asStateFlow()

    val isSensorAvailable: Boolean get() = tiltSensor != null

    private var neutralOffset = 0f
    private var smoothedTilt = 0f
    private var sensitivity = 1.25f
    private var isListening = false

    fun setSensitivity(value: Float) {
        sensitivity = value.coerceIn(0.6f, 2.2f)
    }

    fun start() {
        if (isListening || tiltSensor == null) return
        sensorManager?.registerListener(this, tiltSensor, SensorManager.SENSOR_DELAY_GAME)
        isListening = true
    }

    fun stop() {
        if (!isListening) return
        sensorManager?.unregisterListener(this)
        isListening = false
        _steerValue.value = 0f
    }

    fun calibrateNeutral() {
        neutralOffset = smoothedTilt
    }

    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]

        val rotation = getDisplayRotation()
        // Map device tilt to left(-)/right(+) across portrait and landscape orientations
        val rawAxis = when (rotation) {
            Surface.ROTATION_90 -> y
            Surface.ROTATION_270 -> -y
            Surface.ROTATION_180 -> x
            else -> -x
        }

        // Normalize against standard gravity ~9.81 m/s^2 (max tilt around ~5.2 m/s^2 -> full lock)
        val normalized = (rawAxis / 5.2f).coerceIn(-1.5f, 1.5f)
        smoothedTilt = smoothedTilt * 0.72f + normalized * 0.28f
        _rawTilt.value = smoothedTilt

        val relative = (smoothedTilt - neutralOffset) * sensitivity
        val deadzone = 0.06f
        val steer = if (abs(relative) < deadzone) {
            0f
        } else {
            val sign = if (relative > 0f) 1f else -1f
            ((abs(relative) - deadzone) / (1f - deadzone) * sign).coerceIn(-1f, 1f)
        }
        _steerValue.value = steer
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    @Suppress("DEPRECATION")
    private fun getDisplayRotation(): Int {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.display?.rotation ?: Surface.ROTATION_0
            } else {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                wm?.defaultDisplay?.rotation ?: Surface.ROTATION_0
            }
        }.getOrDefault(Surface.ROTATION_0)
    }
}

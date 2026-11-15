package com.example.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Surface
import android.view.WindowManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

class TiltSteeringController(private val context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
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
    private var uiRotationOverride: Int? = null
    private var lastRotation: Int = -1

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY) {
                val rot = querySystemDisplayRotation()
                if (uiRotationOverride != null) {
                    uiRotationOverride = rot
                }
                if (rot != lastRotation) {
                    handleRotationChanged(rot, resetSmoothedTo = 0f)
                }
            }
        }
    }

    fun setSensitivity(value: Float) {
        sensitivity = value.coerceIn(0.6f, 2.2f)
    }

    /**
     * Syncs the live UI window's display rotation (e.g. Surface.ROTATION_90 / ROTATION_270 in landscape)
     * directly from Compose's LocalView so landscape orientation is always recognized accurately.
     */
    fun updateDisplayRotation(rotation: Int?) {
        uiRotationOverride = rotation
        val resolved = getDisplayRotation()
        if (resolved != lastRotation) {
            handleRotationChanged(resolved, resetSmoothedTo = 0f)
        }
    }

    fun start() {
        if (isListening || tiltSensor == null) return
        lastRotation = getDisplayRotation()
        displayManager?.registerDisplayListener(displayListener, null)
        sensorManager?.registerListener(this, tiltSensor, SensorManager.SENSOR_DELAY_GAME)
        isListening = true
    }

    fun stop() {
        if (!isListening) return
        sensorManager?.unregisterListener(this)
        runCatching { displayManager?.unregisterDisplayListener(displayListener) }
        isListening = false
        _steerValue.value = 0f
    }

    fun calibrateNeutral() {
        neutralOffset = smoothedTilt
    }

    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        processSensorAxes(x, y, getDisplayRotation())
    }

    internal fun processSensorAxes(x: Float, y: Float, rotation: Int): Float {
        val rawAxis = computeRawTiltAxis(x, y, rotation)
        val normalized = (rawAxis / 5.2f).coerceIn(-1.5f, 1.5f)

        if (rotation != lastRotation) {
            handleRotationChanged(rotation, resetSmoothedTo = normalized)
        } else {
            smoothedTilt = smoothedTilt * 0.72f + normalized * 0.28f
        }
        _rawTilt.value = smoothedTilt

        val steer = computeSteerFromSmoothedTilt(
            smoothedTilt = smoothedTilt,
            neutralOffset = neutralOffset,
            sensitivity = sensitivity
        )
        _steerValue.value = steer
        return steer
    }

    private fun handleRotationChanged(newRotation: Int, resetSmoothedTo: Float) {
        lastRotation = newRotation
        neutralOffset = 0f
        smoothedTilt = resetSmoothedTo
        _rawTilt.value = resetSmoothedTo
        _steerValue.value = 0f
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun getDisplayRotation(): Int {
        return uiRotationOverride ?: querySystemDisplayRotation()
    }

    @Suppress("DEPRECATION")
    private fun querySystemDisplayRotation(): Int {
        displayManager?.getDisplay(Display.DEFAULT_DISPLAY)?.rotation?.let { return it }
        return runCatching {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            wm?.defaultDisplay?.rotation ?: Surface.ROTATION_0
        }.getOrDefault(Surface.ROTATION_0)
    }

    companion object {
        /**
         * Maps hardware gravity/accelerometer (x, y) to steering tilt axis:
         * Negative = steer left (CCW), Positive = steer right (CW), 0 = level,
         * across portrait (ROTATION_0 / ROTATION_180) and landscape (ROTATION_90 / ROTATION_270).
         */
        fun computeRawTiltAxis(x: Float, y: Float, rotation: Int): Float {
            return when (rotation) {
                Surface.ROTATION_90 -> y
                Surface.ROTATION_270 -> -y
                Surface.ROTATION_180 -> x
                else -> -x
            }
        }

        fun computeSteerFromSmoothedTilt(
            smoothedTilt: Float,
            neutralOffset: Float = 0f,
            sensitivity: Float = 1.25f,
            deadzone: Float = 0.06f
        ): Float {
            val relative = (smoothedTilt - neutralOffset) * sensitivity
            return if (abs(relative) < deadzone) {
                0f
            } else {
                val sign = if (relative > 0f) 1f else -1f
                ((abs(relative) - deadzone) / (1f - deadzone) * sign).coerceIn(-1f, 1f)
            }
        }
    }
}

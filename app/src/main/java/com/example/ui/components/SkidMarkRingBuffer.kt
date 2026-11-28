package com.example.ui.components

import com.example.drift.DriftPhysics
import com.example.model.RacerState
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pre-allocated ring buffer for braking and drifting rear-tire skid marks.
 * Caps active segment count cleanly (default 200) and fades older marks out
 * over [fadeDurationSec] seconds with zero per-frame allocations in the draw loop.
 */
class SkidMarkRingBuffer(
    val capacity: Int = DEFAULT_CAPACITY,
    val fadeDurationSec: Float = DEFAULT_FADE_DURATION_SEC,
    val minStepDist: Float = DEFAULT_MIN_STEP_DIST
) {
    init {
        require(capacity > 0) { "SkidMarkRingBuffer capacity must be > 0" }
        require(fadeDurationSec > 0f) { "fadeDurationSec must be > 0f" }
    }

    @PublishedApi internal val x1 = FloatArray(capacity)
    @PublishedApi internal val y1 = FloatArray(capacity)
    @PublishedApi internal val x2 = FloatArray(capacity)
    @PublishedApi internal val y2 = FloatArray(capacity)
    @PublishedApi internal val createdAtSec = FloatArray(capacity)
    @PublishedApi internal val zLayer = IntArray(capacity)
    @PublishedApi internal val baseAlpha = FloatArray(capacity)

    @PublishedApi internal var head: Int = 0
    @PublishedApi internal var count: Int = 0

    // Per-racer state: [prevLeftX, prevLeftY, prevRightX, prevRightY, prevZLayer, isActive]
    private val prevRearPoseByRacer = HashMap<String, FloatArray>(8)
    private val minStepDistSq = minStepDist * minStepDist

    fun clear() {
        head = 0
        count = 0
        prevRearPoseByRacer.clear()
    }

    fun pushSegment(
        ax: Float,
        ay: Float,
        bx: Float,
        by: Float,
        timestampSec: Float,
        layer: Int,
        alpha: Float
    ) {
        val writeIdx = head
        x1[writeIdx] = ax
        y1[writeIdx] = ay
        x2[writeIdx] = bx
        y2[writeIdx] = by
        createdAtSec[writeIdx] = timestampSec
        zLayer[writeIdx] = layer
        baseAlpha[writeIdx] = alpha.coerceIn(0.05f, 0.85f)
        head = (head + 1) % capacity
        if (count < capacity) {
            count++
        }
    }

    fun recordRacers(
        racers: List<RacerState>,
        nowSec: Float,
        isDriftMode: Boolean,
        isRaceActive: Boolean
    ) {
        if (!isRaceActive || racers.isEmpty()) {
            prevRearPoseByRacer.values.forEach { it[5] = 0f }
            return
        }
        for (i in racers.indices) {
            val r = racers[i]
            val pose = prevRearPoseByRacer[r.playerId] ?: FloatArray(6).also {
                prevRearPoseByRacer[r.playerId] = it
            }

            if (r.jumpZ > 0.05f) {
                pose[5] = 0f
                continue
            }

            val cosA = cos(r.angleRad)
            val sinA = sin(r.angleRad)
            val rightX = -sinA
            val rightY = cosA
            val forwardVel = r.vx * cosA + r.vy * sinA
            val lateralVel = r.vx * rightX + r.vy * rightY

            val isBrakingForward = r.isBraking && forwardVel > 35f
            val isDriftSlide = (isDriftMode && DriftPhysics.isDrifting(r.angleRad, r.vx, r.vy, r.jumpZ)) ||
                (abs(lateralVel) > 68f && r.speed > 90f)

            if (!isBrakingForward && !isDriftSlide) {
                pose[5] = 0f
                continue
            }

            val rearCenterX = r.x - cosA * REAR_AXLE_OFFSET_PX
            val rearCenterY = r.y - sinA * REAR_AXLE_OFFSET_PX
            val leftX = rearCenterX - rightX * REAR_TRACK_HALF_PX
            val leftY = rearCenterY - rightY * REAR_TRACK_HALF_PX
            val rightWX = rearCenterX + rightX * REAR_TRACK_HALF_PX
            val rightWY = rearCenterY + rightY * REAR_TRACK_HALF_PX

            val wasActive = pose[5] > 0.5f && pose[4].toInt() == r.zLayer
            if (!wasActive) {
                pose[0] = leftX
                pose[1] = leftY
                pose[2] = rightWX
                pose[3] = rightWY
                pose[4] = r.zLayer.toFloat()
                pose[5] = 1f
                continue
            }

            val dlx = leftX - pose[0]
            val dly = leftY - pose[1]
            val drx = rightWX - pose[2]
            val dry = rightWY - pose[3]
            val dLeftSq = dlx * dlx + dly * dly
            val dRightSq = drx * drx + dry * dry

            if (dLeftSq > MAX_STEP_JUMP_SQ || dRightSq > MAX_STEP_JUMP_SQ) {
                pose[0] = leftX
                pose[1] = leftY
                pose[2] = rightWX
                pose[3] = rightWY
                pose[4] = r.zLayer.toFloat()
                pose[5] = 1f
                continue
            }

            if (dLeftSq >= minStepDistSq || dRightSq >= minStepDistSq) {
                val slipIntensity = when {
                    isBrakingForward && isDriftSlide -> 0.56f
                    isDriftSlide -> (0.44f + (abs(lateralVel) / 420f).coerceIn(0f, 0.14f))
                    else -> 0.48f
                }
                pushSegment(
                    ax = pose[0],
                    ay = pose[1],
                    bx = leftX,
                    by = leftY,
                    timestampSec = nowSec,
                    layer = r.zLayer,
                    alpha = slipIntensity
                )
                pushSegment(
                    ax = pose[2],
                    ay = pose[3],
                    bx = rightWX,
                    by = rightWY,
                    timestampSec = nowSec,
                    layer = r.zLayer,
                    alpha = slipIntensity
                )
                pose[0] = leftX
                pose[1] = leftY
                pose[2] = rightWX
                pose[3] = rightWY
                pose[4] = r.zLayer.toFloat()
                pose[5] = 1f
            }
        }
    }

    inline fun forEachActiveSegment(
        nowSec: Float,
        zLayerFilter: Int?,
        action: (x1: Float, y1: Float, x2: Float, y2: Float, alpha: Float) -> Unit
    ) {
        if (count <= 0) return
        val start = if (count == capacity) head else 0
        for (i in 0 until count) {
            val idx = (start + i) % capacity
            if (zLayerFilter != null && zLayer[idx] != zLayerFilter) continue
            val age = nowSec - createdAtSec[idx]
            if (age < 0f || age >= fadeDurationSec) continue
            val fadeFrac = 1f - (age / fadeDurationSec)
            val alpha = baseAlpha[idx] * fadeFrac * fadeFrac.coerceAtLeast(0.45f)
            if (alpha > 0.01f) {
                action(x1[idx], y1[idx], x2[idx], y2[idx], alpha)
            }
        }
    }

    fun activeSegmentCount(nowSec: Float): Int {
        var active = 0
        forEachActiveSegment(nowSec, zLayerFilter = null) { _, _, _, _, _ -> active++ }
        return active
    }

    fun totalBufferedCount(): Int = count

    companion object {
        const val DEFAULT_CAPACITY = 200
        const val DEFAULT_FADE_DURATION_SEC = 4.5f
        const val DEFAULT_MIN_STEP_DIST = 14f
        private const val REAR_AXLE_OFFSET_PX = 14f
        private const val REAR_TRACK_HALF_PX = 11.5f
        private const val MAX_STEP_JUMP_SQ = 95f * 95f
    }
}

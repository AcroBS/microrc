package com.example.drift

import com.example.data.TrackCatalog
import com.example.model.TrackDefinition
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/**
 * Isolated rear-loose drift physics used ONLY when [GameMode.DRIFT] is active.
 * Keeps standard Circuit Race, Arena, and Stunts grip physics completely untouched.
 */
object DriftPhysics {

    data class DriftIntegrationResult(
        val newAngleRad: Float,
        val vx: Float,
        val vy: Float,
        val slipAngleRad: Float
    )

    /**
     * Allows the car's rear end to swing loose when turning into a corner at speed,
     * preserving momentum along the entry vector so controlled drifts slice past
     * apex/chicane obstacles faster than slow gripping.
     */
    fun integrateDriftTurn(
        currentAngleRad: Float,
        forwardVel: Float,
        lateralVel: Float,
        steer: Float,
        throttle: Float,
        turnRateRad: Float,
        carLateralGrip: Float,
        surfaceGripMultiplier: Float,
        speedNorm: Float,
        reverseFlip: Float,
        hasOverdrive: Boolean,
        hasBoost: Boolean,
        isOffRoad: Boolean,
        weatherGripMult: Float,
        dt: Float
    ): DriftIntegrationResult {
        val oldCos = cos(currentAngleRad)
        val oldSin = sin(currentAngleRad)
        val oldRightX = -oldSin
        val oldRightY = oldCos

        // World velocity before rotating the chassis nose into the turn
        val preVx = oldCos * forwardVel + oldRightX * lateralVel
        val preVy = oldSin * forwardVel + oldRightY * lateralVel

        val absSpeed = hypot(preVx, preVy)
        val absSteer = abs(steer)
        val currentSlip = computeSlipAngleRad(currentAngleRad, preVx, preVy)
        val isCounterSteering = abs(currentSlip) > 0.18f && steer * currentSlip > 0.02f
        val initiatingDrift = forwardVel > 95f && absSteer > 0.10f

        val turnInBoost = when {
            hasOverdrive -> 1.28f
            initiatingDrift && !isCounterSteering -> 1.24f
            isCounterSteering -> 1.15f
            else -> 1.04f
        }
        val newAngle = normalizeAngle(
            currentAngleRad + steer * turnRateRad * turnInBoost * speedNorm * reverseFlip * dt
        )

        val nCos = cos(newAngle)
        val nSin = sin(newAngle)
        val nRightX = -nSin
        val nRightY = nCos

        // Project preserved momentum onto the new car orientation:
        // rotating the nose builds authentic lateral slip velocity (rear end loose!)
        var newForwardVel = preVx * nCos + preVy * nSin
        var newLateralVel = preVx * nRightX + preVy * nRightY

        // While actively drifting into a turn on tarmac/concrete, reduce lateral grip so
        // the tail steps out cleanly; when straightening or counter-steering, recover grip smoothly.
        val activeSliding = absSpeed > 125f && (absSteer > 0.14f || abs(newLateralVel) > 55f)
        val driftGripFactor = when {
            hasOverdrive -> 1.45f
            absSteer <= 0.12f -> 1.32f // Straightening out snaps rear back in line
            isCounterSteering -> 1.18f
            activeSliding -> 0.34f // Rear end loose on turn entry/mid-corner
            else -> 0.65f
        }

        val effectiveGrip = (carLateralGrip *
            surfaceGripMultiplier *
            weatherGripMult *
            (if (isOffRoad) 0.78f else 1.0f) *
            driftGripFactor).coerceIn(1.35f, 9.5f)

        val prevLat = newLateralVel
        newLateralVel *= max(0f, 1f - effectiveGrip * dt)
        val scrubbedLat = abs(prevLat - newLateralVel)

        // Powered throttle drift feeds part of forward drive through the slide so drifting around
        // corners carries high exit speed without bogging down.
        if (throttle > 0.1f && abs(newLateralVel) > 45f && forwardVel > 80f) {
            val driftPush = scrubbedLat * (if (hasBoost) 0.48f else 0.32f)
            newForwardVel += driftPush
        }

        val outVx = nCos * newForwardVel + nRightX * newLateralVel
        val outVy = nSin * newForwardVel + nRightY * newLateralVel
        val outSlip = computeSlipAngleRad(newAngle, outVx, outVy)

        return DriftIntegrationResult(
            newAngleRad = newAngle,
            vx = outVx,
            vy = outVy,
            slipAngleRad = outSlip
        )
    }

    /**
     * Signed angle between car heading and actual velocity vector (rad).
     * Magnitude > ~0.16 rad indicates visible rear-end drift slide.
     */
    fun computeSlipAngleRad(angleRad: Float, vx: Float, vy: Float): Float {
        val speed = hypot(vx, vy)
        if (speed < 40f) return 0f
        val velAngle = atan2(vy, vx)
        return normalizeAngle(velAngle - angleRad)
    }

    fun isDrifting(angleRad: Float, vx: Float, vy: Float, jumpZ: Float): Boolean {
        if (jumpZ > 0.10f) return false
        val speed = hypot(vx, vy)
        if (speed < 120f) return false
        return abs(computeSlipAngleRad(angleRad, vx, vy)) >= 0.16f
    }

    /**
     * Adjusts bot steering target to smoothly swing around solid Drift track obstacles.
     */
    fun avoidSolidObstacles(
        track: TrackDefinition,
        racerX: Float,
        racerY: Float,
        racerAngleRad: Float,
        targetX: Float,
        targetY: Float
    ): Pair<Float, Float> {
        var tx = targetX
        var ty = targetY
        val fwdX = cos(racerAngleRad)
        val fwdY = sin(racerAngleRad)
        for (prop in track.props) {
            if (!prop.isSolidObstacle) continue
            val dx = prop.x - racerX
            val dy = prop.y - racerY
            val dist = hypot(dx, dy)
            val avoidRadius = prop.radius + 95f
            if (dist < avoidRadius && dist > 1f) {
                val aheadDot = (dx * fwdX + dy * fwdY) / dist
                if (aheadDot > -0.15f) {
                    val pushStrength = ((avoidRadius - dist) / avoidRadius) * 145f
                    tx -= (dx / dist) * pushStrength
                    ty -= (dy / dist) * pushStrength
                }
            }
        }
        return tx to ty
    }

    private fun normalizeAngle(angle: Float): Float {
        var a = angle
        val twoPi = (2f * PI.toFloat())
        while (a > PI.toFloat()) a -= twoPi
        while (a < -PI.toFloat()) a += twoPi
        return a
    }
}

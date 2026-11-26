package com.example.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import com.example.model.CarVisualUpgrades
import com.example.model.RcCarType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

fun DrawScope.drawRcCar(
    x: Float,
    y: Float,
    angleRad: Float,
    carType: RcCarType,
    primaryColor: Color,
    accentColor: Color,
    steerVisual: Float = 0f,
    isBoosting: Boolean = false,
    hasShield: Boolean = false,
    isStunned: Boolean = false,
    hasOverdrive: Boolean = false,
    spinVisualOffset: Float = 0f,
    jumpZ: Float = 0f,
    scale: Float = 1f,
    animTimeSec: Float = 0f,
    isCarryingFlag: Boolean = false,
    hasElectroAura: Boolean = false,
    isElectroParalyzed: Boolean = false,
    hasRedLaser: Boolean = false,
    isBraking: Boolean = false,
    nightIntensity: Float = 0f,
    visualUpgrades: CarVisualUpgrades = CarVisualUpgrades()
) {
    val deg = ((angleRad + spinVisualOffset) * 180f / PI.toFloat())
    val headingDeg = (angleRad * 180f / PI.toFloat())
    val frontWheelAngle = steerVisual * 22f
    val airElevation = jumpZ.coerceIn(0f, 1.4f)
    val airScale = scale * (1f + airElevation * 0.44f)
    val shadowDx = airElevation * 38f * scale
    val shadowDy = airElevation * 44f * scale
    val shadowScale = scale * (1f - airElevation * 0.14f)

    // Forward-extending visible Red Laser beam from the car's front nose (340px physics range)
    if (hasRedLaser) {
        val beamPulse = 0.5f + 0.5f * sin(animTimeSec * 32f)
        withTransform({
            translate(left = x, top = y)
            scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
            rotate(degrees = headingDeg, pivot = Offset.Zero)
        }) {
            val emitterStart = Offset(20f, 0f)
            val beamEnd = Offset(360f, 0f)
            // Wide crimson atmospheric bloom
            drawLine(
                color = Color(0xFFFF1744).copy(alpha = 0.24f + 0.12f * beamPulse),
                start = emitterStart,
                end = beamEnd,
                strokeWidth = 18f,
                cap = StrokeCap.Round
            )
            // Intense scarlet outer beam
            drawLine(
                color = Color(0xFFFF1744).copy(alpha = 0.88f),
                start = emitterStart,
                end = beamEnd,
                strokeWidth = 7.5f + 1.5f * beamPulse,
                cap = StrokeCap.Round
            )
            // White-hot inner core
            drawLine(
                color = Color(0xFFFFCDD2),
                start = emitterStart,
                end = beamEnd,
                strokeWidth = 2.8f,
                cap = StrokeCap.Round
            )
            // Front bumper emitter lens & beam-tip flare
            drawCircle(
                color = Color(0xFFFF1744),
                radius = 7f + 2f * beamPulse,
                center = emitterStart
            )
            drawCircle(
                color = Color.White,
                radius = 3.5f,
                center = emitterStart
            )
            drawCircle(
                color = Color(0xFFFF5252).copy(alpha = 0.72f),
                radius = 8f + 3f * beamPulse,
                center = beamEnd
            )
        }
    }

    // Electro Shock active 155px proximity aura on the track surface
    if (hasElectroAura) {
        val auraBase = 155f * scale
        val pulse = 0.5f + 0.5f * sin(animTimeSec * 16f)
        drawCircle(
            color = Color(0xFF7C4DFF).copy(alpha = 0.14f + 0.10f * pulse),
            radius = auraBase,
            center = Offset(x, y)
        )
        drawCircle(
            color = Color(0xFF00E5FF).copy(alpha = 0.72f),
            radius = auraBase * (0.94f + 0.06f * pulse),
            center = Offset(x, y),
            style = Stroke(width = 3.2f * scale)
        )
        drawCircle(
            color = Color(0xFFB388FF).copy(alpha = 0.85f),
            radius = auraBase * 0.62f,
            center = Offset(x, y),
            style = Stroke(width = 2.0f * scale)
        )
        // Crackling radial lightning bolts from chassis out to proximity perimeter
        for (bolt in 0 until 6) {
            val baseAng = animTimeSec * 7.5f + bolt * (PI.toFloat() / 3f)
            val r1 = 24f * scale
            val r2 = 76f * scale
            val r3 = 138f * scale
            val jitter = sin(animTimeSec * 36f + bolt * 2.1f) * 0.22f
            val p1 = Offset(x + cos(baseAng) * r1, y + sin(baseAng) * r1)
            val p2 = Offset(x + cos(baseAng + jitter) * r2, y + sin(baseAng + jitter) * r2)
            val p3 = Offset(x + cos(baseAng - jitter * 0.7f) * r3, y + sin(baseAng - jitter * 0.7f) * r3)
            val boltPath = Path().apply {
                moveTo(p1.x, p1.y)
                lineTo(p2.x, p2.y)
                lineTo(p3.x, p3.y)
            }
            drawPath(
                path = boltPath,
                color = if (bolt % 2 == 0) Color(0xFF00E5FF) else Color(0xFFB388FF),
                style = Stroke(width = 2.6f * scale, cap = StrokeCap.Round)
            )
        }
    }

    // Detached ground shadow (shifts away as car launches airborne)
    withTransform({
        translate(left = x + shadowDx, top = y + shadowDy)
        scale(scaleX = shadowScale, scaleY = shadowScale, pivot = Offset.Zero)
        rotate(degrees = deg, pivot = Offset.Zero)
    }) {
        drawOval(
            color = Color.Black.copy(alpha = (0.38f - airElevation * 0.12f).coerceIn(0.14f, 0.4f)),
            topLeft = Offset(-24f, -15f),
            size = Size(48f, 34f)
        )
    }

    withTransform({
        translate(left = x, top = y)
        scale(scaleX = airScale, scaleY = airScale, pivot = Offset.Zero)
        rotate(degrees = deg, pivot = Offset.Zero)
    }) {
        // Airborne wind streaks
        if (airElevation > 0.10f) {
            val windAlpha = (airElevation * 0.55f).coerceIn(0.1f, 0.65f)
            drawLine(
                color = Color.White.copy(alpha = windAlpha),
                start = Offset(-26f, -19f),
                end = Offset(6f, -19f),
                strokeWidth = 2.2f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White.copy(alpha = windAlpha),
                start = Offset(-26f, 19f),
                end = Offset(6f, 19f),
                strokeWidth = 2.2f,
                cap = StrokeCap.Round
            )
        }

        // Boost exhaust flame (standard orange vs custom plasma/twin jet)
        if (isBoosting) {
            val flicker = 1f + 0.28f * sin(animTimeSec * 45f)
            if (visualUpgrades.hasCustomTrail) {
                val outerFlame = Path().apply {
                    moveTo(-19f, -9f)
                    lineTo(-22f - 28f * flicker, 0f)
                    lineTo(-19f, 9f)
                    close()
                }
                drawPath(outerFlame, Color(0xFF00E5FF).copy(alpha = 0.85f))
                val midFlame = Path().apply {
                    moveTo(-19f, -5.5f)
                    lineTo(-21f - 21f * flicker, 0f)
                    lineTo(-19f, 5.5f)
                    close()
                }
                drawPath(midFlame, Color(0xFFE040FB).copy(alpha = 0.92f))
                drawCircle(Color(0xFFFFFFFF), radius = 4.6f * flicker, center = Offset(-24f, 0f))
                drawCircle(Color(0xFFFFEA00).copy(alpha = 0.85f), radius = 2.8f, center = Offset(-36f - 6f * flicker, -3.5f))
                drawCircle(Color(0xFF00E5FF).copy(alpha = 0.85f), radius = 2.6f, center = Offset(-38f - 5f * flicker, 3.5f))
            } else {
                val flamePath = Path().apply {
                    moveTo(-20f, -6f)
                    lineTo(-20f - 22f * flicker, 0f)
                    lineTo(-20f, 6f)
                    close()
                }
                drawPath(flamePath, Color(0xFFFF6D00).copy(alpha = 0.9f))
                drawCircle(Color(0xFFFFEA00), radius = 5f * flicker, center = Offset(-23f, 0f))
            }
        }

        // Underglow Neon Kit (soft colored ground-reflection halo matching accentColor)
        if (visualUpgrades.hasUnderglowNeon) {
            drawUnderglowNeon(accentColor, nightIntensity, animTimeSec)
        }

        // Overdrive neon underglow
        if (hasOverdrive) {
            drawOval(
                color = Color(0xFFE040FB).copy(alpha = 0.35f),
                topLeft = Offset(-28f, -20f),
                size = Size(56f, 40f)
            )
        }

        // Racing bumpers underbody mounts (drawn before body so splitters sit naturally)
        if (visualUpgrades.hasRacingBumpers) {
            drawRacingBumpers(carType, primaryColor, accentColor)
        }

        val bigTires = visualUpgrades.hasBiggerTires
        when (carType) {
            RcCarType.VOLT_BUGGY -> drawVoltBuggy(primaryColor, accentColor, frontWheelAngle, bigTires)
            RcCarType.APEX_FORMULA -> drawApexFormula(primaryColor, accentColor, frontWheelAngle, bigTires)
            RcCarType.TUNDRA_MONSTER -> drawTundraMonster(primaryColor, accentColor, frontWheelAngle, bigTires)
            RcCarType.DRIFT_PHANTOM -> drawDriftPhantom(primaryColor, accentColor, frontWheelAngle, bigTires)
        }

        if (visualUpgrades.hasRacingLogo) {
            drawRacingLogo(carType, accentColor)
        }

        // Front headlights & rear brake/taillights
        drawCarLights(
            carType = carType,
            isBraking = isBraking,
            nightIntensity = nightIntensity
        )

        if (visualUpgrades.hasRearSpoiler) {
            drawRearSpoiler(accentColor)
        }

        if (visualUpgrades.hasRoofLedLightbar) {
            drawRoofLedLightbar(carType, nightIntensity)
        }

        if (visualUpgrades.hasWhipAntenna) {
            drawWhipAntenna(steerVisual, animTimeSec, accentColor)
        }

        // RC Shield bubble
        if (hasShield) {
            val pulse = 29f + 2.5f * sin(animTimeSec * 8f)
            drawCircle(
                color = Color(0xFFB2FF59).copy(alpha = 0.22f),
                radius = pulse,
                center = Offset.Zero
            )
            drawCircle(
                color = Color(0xFFB2FF59).copy(alpha = 0.85f),
                radius = pulse,
                center = Offset.Zero,
                style = Stroke(width = 2.5f)
            )
        }

        // Arena Flag Carrier aura + roof-mounted waving battle pennant
        if (isCarryingFlag) {
            val auraRadius = 33f + 3f * sin(animTimeSec * 9f)
            drawCircle(
                color = Color(0xFFFFD54F).copy(alpha = 0.24f),
                radius = auraRadius,
                center = Offset.Zero
            )
            drawCircle(
                color = Color(0xFFFFD54F),
                radius = auraRadius,
                center = Offset.Zero,
                style = Stroke(width = 2.8f)
            )
            val flutterY = sin(animTimeSec * 18f) * 5.5f
            val pennant = Path().apply {
                moveTo(2f, -2f)
                lineTo(-24f, -15f + flutterY)
                lineTo(-19f, -2f + flutterY * 0.5f)
                lineTo(-25f, 11f + flutterY)
                close()
            }
            drawPath(pennant, Color(0xFFFFAB00))
            drawPath(pennant, Color(0xFFFFF8E1), style = Stroke(width = 1.6f))
            drawCircle(Color.White, radius = 3.6f, center = Offset(2f, -1f))
        }

        // Electro Shock motionless paralysis lock cage
        if (isElectroParalyzed) {
            val flash = 0.5f + 0.5f * sin(animTimeSec * 28f)
            drawCircle(
                color = Color(0xFF7C4DFF).copy(alpha = 0.28f + 0.16f * flash),
                radius = 30f,
                center = Offset.Zero
            )
            drawCircle(
                color = if (flash > 0.45f) Color(0xFF00E5FF) else Color(0xFFB388FF),
                radius = 30f,
                center = Offset.Zero,
                style = Stroke(width = 3f)
            )
            // Zig-zag electric arcs across locked wheels
            val arc1 = Path().apply {
                moveTo(-18f, -18f)
                lineTo(-4f, -7f + (flash - 0.5f) * 8f)
                lineTo(6f, 8f - (flash - 0.5f) * 8f)
                lineTo(18f, 18f)
            }
            val arc2 = Path().apply {
                moveTo(-18f, 18f)
                lineTo(-5f, 6f - (flash - 0.5f) * 8f)
                lineTo(7f, -6f + (flash - 0.5f) * 8f)
                lineTo(18f, -18f)
            }
            drawPath(arc1, Color(0xFF80D8FF), style = Stroke(width = 2.6f, cap = StrokeCap.Round))
            drawPath(arc2, Color(0xFFEA80FC), style = Stroke(width = 2.4f, cap = StrokeCap.Round))
        }

        // EMP Stun sparks
        if (isStunned) {
            for (i in 0 until 4) {
                val a = animTimeSec * 14f + i * 1.57f
                val sx = cos(a) * 24f
                val sy = sin(a) * 24f
                drawLine(
                    color = Color(0xFF00E5FF),
                    start = Offset(sx * 0.4f, sy * 0.4f),
                    end = Offset(sx, sy),
                    strokeWidth = 2.5f,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

fun DrawScope.drawGhostRcCar(
    x: Float,
    y: Float,
    angleRad: Float,
    carType: RcCarType,
    primaryColor: Color,
    accentColor: Color,
    jumpZ: Float = 0f,
    scale: Float = 1f,
    animTimeSec: Float = 0f,
    visualUpgrades: CarVisualUpgrades = CarVisualUpgrades()
) {
    val deg = angleRad * 180f / PI.toFloat()
    val airElevation = jumpZ.coerceIn(0f, 1.4f)
    val airScale = scale * (1f + airElevation * 0.44f)
    val pulse = 0.5f + 0.5f * sin(animTimeSec * 7f)
    val ghostPrimary = Color(
        red = primaryColor.red * 0.45f + 0.0f,
        green = primaryColor.green * 0.45f + 0.50f,
        blue = primaryColor.blue * 0.45f + 0.55f,
        alpha = 0.44f
    )
    val ghostAccent = Color(
        red = accentColor.red * 0.35f + 0.55f,
        green = accentColor.green * 0.35f + 0.62f,
        blue = accentColor.blue * 0.35f + 0.65f,
        alpha = 0.56f
    )

    // Holographic ground projection ring
    drawCircle(
        color = Color(0xFF00E5FF).copy(alpha = 0.14f + 0.08f * pulse),
        radius = (26f + 3f * pulse) * scale,
        center = Offset(x, y)
    )
    drawCircle(
        color = Color(0xFF80D8FF).copy(alpha = 0.52f),
        radius = (26f + 3f * pulse) * scale,
        center = Offset(x, y),
        style = Stroke(width = 1.8f * scale)
    )

    withTransform({
        translate(left = x, top = y)
        scale(scaleX = airScale, scaleY = airScale, pivot = Offset.Zero)
        rotate(degrees = deg, pivot = Offset.Zero)
    }) {
        if (visualUpgrades.hasUnderglowNeon) {
            drawUnderglowNeon(ghostAccent, nightIntensity = 0.4f, animTimeSec = animTimeSec)
        }
        if (visualUpgrades.hasRacingBumpers) {
            drawRacingBumpers(carType, ghostPrimary, ghostAccent)
        }
        val bigTires = visualUpgrades.hasBiggerTires
        when (carType) {
            RcCarType.VOLT_BUGGY -> drawVoltBuggy(ghostPrimary, ghostAccent, 0f, bigTires)
            RcCarType.APEX_FORMULA -> drawApexFormula(ghostPrimary, ghostAccent, 0f, bigTires)
            RcCarType.TUNDRA_MONSTER -> drawTundraMonster(ghostPrimary, ghostAccent, 0f, bigTires)
            RcCarType.DRIFT_PHANTOM -> drawDriftPhantom(ghostPrimary, ghostAccent, 0f, bigTires)
        }
        if (visualUpgrades.hasRacingLogo) {
            drawRacingLogo(carType, ghostAccent)
        }
        if (visualUpgrades.hasRearSpoiler) {
            drawRearSpoiler(ghostAccent)
        }
        if (visualUpgrades.hasRoofLedLightbar) {
            drawRoofLedLightbar(carType, 0.35f)
        }
        if (visualUpgrades.hasWhipAntenna) {
            drawWhipAntenna(0f, animTimeSec, ghostAccent)
        }
        // Cyan holographic rim highlight
        drawOval(
            color = Color(0xFF00E5FF).copy(alpha = 0.62f),
            topLeft = Offset(-22f, -14f),
            size = Size(45f, 28f),
            style = Stroke(width = 1.8f)
        )
    }
}

private fun DrawScope.drawWheel(
    cx: Float,
    cy: Float,
    w: Float,
    h: Float,
    angleDeg: Float = 0f,
    tireColor: Color = Color(0xFF15181F),
    biggerTires: Boolean = false
) {
    val tireW = if (biggerTires) w * 1.24f else w
    val tireH = if (biggerTires) h * 1.42f else h
    val yOffset = if (biggerTires) cy + (if (cy < 0f) -2.2f else 2.2f) else cy
    rotate(degrees = angleDeg, pivot = Offset(cx, yOffset)) {
        drawRoundRect(
            color = tireColor,
            topLeft = Offset(cx - tireW / 2f, yOffset - tireH / 2f),
            size = Size(tireW, tireH),
            cornerRadius = CornerRadius(if (biggerTires) 3.2f else 2.5f, if (biggerTires) 3.2f else 2.5f)
        )
        if (biggerTires) {
            // Chunky off-road tread ribs + alloy beadlock accent
            val treadColor = Color(0xFF2B3240)
            for (step in -1..1) {
                val tx = cx + step * (tireW * 0.28f)
                drawLine(
                    color = treadColor,
                    start = Offset(tx, yOffset - tireH * 0.46f),
                    end = Offset(tx, yOffset + tireH * 0.46f),
                    strokeWidth = 1.7f
                )
            }
            drawRoundRect(
                color = Color(0xFF455A64).copy(alpha = 0.7f),
                topLeft = Offset(cx - tireW / 2f, yOffset - tireH / 2f),
                size = Size(tireW, tireH),
                cornerRadius = CornerRadius(3.2f, 3.2f),
                style = Stroke(width = 1.1f)
            )
        }
    }
}

private fun DrawScope.drawVoltBuggy(primary: Color, accent: Color, frontAngle: Float, biggerTires: Boolean) {
    // Suspension arms
    drawLine(Color(0xFF455A64), Offset(12f, -15f), Offset(12f, 15f), strokeWidth = 3f)
    drawLine(Color(0xFF455A64), Offset(-13f, -16f), Offset(-13f, 16f), strokeWidth = 3.5f)
    // Wheels
    drawWheel(13f, -16f, 11f, 6f, frontAngle, biggerTires = biggerTires)
    drawWheel(13f, 16f, 11f, 6f, frontAngle, biggerTires = biggerTires)
    drawWheel(-14f, -17f, 13f, 7f, biggerTires = biggerTires)
    drawWheel(-14f, 17f, 13f, 7f, biggerTires = biggerTires)
    // Tapered buggy body
    val body = Path().apply {
        moveTo(22f, 0f)
        lineTo(8f, -10f)
        lineTo(-16f, -11f)
        lineTo(-19f, 0f)
        lineTo(-16f, 11f)
        lineTo(8f, 10f)
        close()
    }
    drawPath(body, primary)
    // Racing stripe + cockpit
    drawRoundRect(accent, Offset(-6f, -4f), Size(18f, 8f), CornerRadius(3f, 3f))
    drawOval(Color(0xFF1A222E), Offset(-4f, -5f), Size(11f, 10f))
    // Rear buggy wing
    drawRoundRect(accent, Offset(-22f, -13f), Size(6f, 26f), CornerRadius(2f, 2f))
}

private fun DrawScope.drawApexFormula(primary: Color, accent: Color, frontAngle: Float, biggerTires: Boolean) {
    // Wide open-wheel F1 tires
    drawWheel(13f, -15f, 11f, 6f, frontAngle, biggerTires = biggerTires)
    drawWheel(13f, 15f, 11f, 6f, frontAngle, biggerTires = biggerTires)
    drawWheel(-14f, -16f, 13f, 7.5f, biggerTires = biggerTires)
    drawWheel(-14f, 16f, 13f, 7.5f, biggerTires = biggerTires)
    // Front aero wing
    drawRoundRect(accent, Offset(18f, -15f), Size(5f, 30f), CornerRadius(2f, 2f))
    // Sleek nosecone + sidepods
    val body = Path().apply {
        moveTo(23f, 0f)
        lineTo(8f, -5f)
        lineTo(2f, -11f)
        lineTo(-16f, -10f)
        lineTo(-18f, 10f)
        lineTo(2f, 11f)
        lineTo(8f, 5f)
        close()
    }
    drawPath(body, primary)
    // Halo + helmet
    drawCircle(accent, radius = 4.5f, center = Offset(-1f, 0f))
    drawCircle(Color(0xFF0E131B), radius = 2.5f, center = Offset(-1f, 0f))
    // Rear F1 wing
    drawRoundRect(accent, Offset(-23f, -14f), Size(6f, 28f), CornerRadius(1.5f, 1.5f))
}

private fun DrawScope.drawTundraMonster(primary: Color, accent: Color, frontAngle: Float, biggerTires: Boolean) {
    // Oversized monster tires
    drawWheel(13f, -18f, 14f, 8f, frontAngle, Color(0xFF101318), biggerTires = biggerTires)
    drawWheel(13f, 18f, 14f, 8f, frontAngle, Color(0xFF101318), biggerTires = biggerTires)
    drawWheel(-13f, -18f, 14f, 8f, 0f, Color(0xFF101318), biggerTires = biggerTires)
    drawWheel(-13f, 18f, 14f, 8f, 0f, Color(0xFF101318), biggerTires = biggerTires)
    // Heavy truck body
    drawRoundRect(primary, Offset(-19f, -13f), Size(39f, 26f), CornerRadius(5f, 5f))
    // Front bullbar
    drawRoundRect(accent, Offset(19f, -11f), Size(3.5f, 22f), CornerRadius(1.5f, 1.5f))
    // Cab & roll cage bars
    drawRoundRect(Color(0xFF151C26), Offset(-4f, -9f), Size(14f, 18f), CornerRadius(3f, 3f))
    drawRoundRect(accent, Offset(-16f, -8f), Size(9f, 16f), CornerRadius(2f, 2f))
    drawLine(accent, Offset(-14f, -8f), Offset(-7f, 8f), strokeWidth = 2f)
    drawLine(accent, Offset(-14f, 8f), Offset(-7f, -8f), strokeWidth = 2f)
}

private fun DrawScope.drawDriftPhantom(primary: Color, accent: Color, frontAngle: Float, biggerTires: Boolean) {
    // Stanced tuner wheels
    drawWheel(13f, -14.5f, 11f, 5.5f, frontAngle, biggerTires = biggerTires)
    drawWheel(13f, 14.5f, 11f, 5.5f, frontAngle, biggerTires = biggerTires)
    drawWheel(-13f, -14.5f, 11f, 5.5f, biggerTires = biggerTires)
    drawWheel(-13f, 14.5f, 11f, 5.5f, biggerTires = biggerTires)
    // Widebody coupe shell
    drawRoundRect(primary, Offset(-20f, -13f), Size(42f, 26f), CornerRadius(7f, 7f))
    // Twin offset racing stripe
    drawRect(accent, Offset(-19f, -4.5f), Size(40f, 3f))
    drawRect(accent, Offset(-19f, 1.5f), Size(40f, 3f))
    // Tinted windshield + roof
    drawRoundRect(Color(0xFF111722), Offset(-7f, -9.5f), Size(17f, 19f), CornerRadius(4f, 4f))
    drawRoundRect(primary, Offset(-5f, -7.5f), Size(8f, 15f), CornerRadius(2f, 2f))
    // Carbon drift spoiler
    drawRoundRect(Color(0xFF1A1F29), Offset(-22f, -13f), Size(4.5f, 26f), CornerRadius(1.5f, 1.5f))
}

private fun DrawScope.drawRacingBumpers(
    carType: RcCarType,
    primary: Color,
    accent: Color
) {
    val frontX = when (carType) {
        RcCarType.APEX_FORMULA -> 22.5f
        RcCarType.DRIFT_PHANTOM -> 21.5f
        else -> 20.5f
    }
    // Front aerodynamic splitter + tubular bash-bar with angled end-fins
    drawRoundRect(
        color = Color(0xFF263238),
        topLeft = Offset(frontX - 1.5f, -13.5f),
        size = Size(5.5f, 27f),
        cornerRadius = CornerRadius(2.2f, 2.2f)
    )
    drawRoundRect(
        color = accent,
        topLeft = Offset(frontX + 1.4f, -12.5f),
        size = Size(2.2f, 25f),
        cornerRadius = CornerRadius(1.1f, 1.1f)
    )
    // Front splitter end-fins
    drawRoundRect(primary, Offset(frontX - 2f, -14.8f), Size(5f, 2.4f), CornerRadius(1f, 1f))
    drawRoundRect(primary, Offset(frontX - 2f, 12.4f), Size(5f, 2.4f), CornerRadius(1f, 1f))

    // Rear race diffuser + strake fins
    drawRoundRect(
        color = Color(0xFF1C232E),
        topLeft = Offset(-24.2f, -12f),
        size = Size(5f, 24f),
        cornerRadius = CornerRadius(1.8f, 1.8f)
    )
    for (fy in listOf(-7.5f, -2.5f, 2.5f, 7.5f)) {
        drawLine(
            color = accent.copy(alpha = 0.9f),
            start = Offset(-24.2f, fy),
            end = Offset(-19.5f, fy),
            strokeWidth = 1.5f
        )
    }
}

private fun DrawScope.drawRearSpoiler(accent: Color) {
    // Dual swan-neck mounting struts
    drawLine(Color(0xFF263238), Offset(-18f, -6.5f), Offset(-23.5f, -6.5f), strokeWidth = 2.4f)
    drawLine(Color(0xFF263238), Offset(-18f, 6.5f), Offset(-23.5f, 6.5f), strokeWidth = 2.4f)
    // Main GT wing blade
    drawRoundRect(
        color = Color(0xFF111620),
        topLeft = Offset(-26.8f, -16.5f),
        size = Size(6.8f, 33f),
        cornerRadius = CornerRadius(2f, 2f)
    )
    // Accent trailing lip across wing
    drawRoundRect(
        color = accent,
        topLeft = Offset(-26.5f, -15.5f),
        size = Size(2.2f, 31f),
        cornerRadius = CornerRadius(1f, 1f)
    )
    // Left & right aerodynamic endplates
    drawRoundRect(accent, Offset(-27.8f, -17.5f), Size(9.2f, 2.4f), CornerRadius(1f, 1f))
    drawRoundRect(accent, Offset(-27.8f, 15.1f), Size(9.2f, 2.4f), CornerRadius(1f, 1f))
}

private fun DrawScope.drawRoofLedLightbar(carType: RcCarType, nightIntensity: Float) {
    val barX = when (carType) {
        RcCarType.APEX_FORMULA -> 5.5f
        RcCarType.VOLT_BUGGY -> 5.0f
        RcCarType.TUNDRA_MONSTER -> 8.5f
        RcCarType.DRIFT_PHANTOM -> 5.2f
    }
    val clampedNight = nightIntensity.coerceIn(0f, 1f)
    // Forward beam halo bloom
    drawOval(
        color = Color(0xFF80F5FF).copy(alpha = 0.24f + 0.34f * clampedNight),
        topLeft = Offset(barX - 1f, -13f),
        size = Size(14f, 26f)
    )
    // Aluminum lightbar rail
    drawRoundRect(
        color = Color(0xFF1A222E),
        topLeft = Offset(barX - 2f, -10.5f),
        size = Size(4.2f, 21f),
        cornerRadius = CornerRadius(1.5f, 1.5f)
    )
    // 5 high-output LED emitter pods
    for (ly in listOf(-7.6f, -3.8f, 0f, 3.8f, 7.6f)) {
        drawCircle(
            color = Color(0xFF00E5FF).copy(alpha = 0.45f + 0.45f * clampedNight),
            radius = 2.6f,
            center = Offset(barX + 0.8f, ly)
        )
        drawCircle(
            color = Color(0xFFFFFFFF),
            radius = 1.35f,
            center = Offset(barX + 0.7f, ly)
        )
    }
}

private fun DrawScope.drawRacingLogo(carType: RcCarType, accent: Color) {
    val hoodCenterX = when (carType) {
        RcCarType.APEX_FORMULA -> 10.5f
        RcCarType.TUNDRA_MONSTER -> 12f
        RcCarType.DRIFT_PHANTOM -> 13f
        RcCarType.VOLT_BUGGY -> 10.5f
    }
    // Crisp hood/roof twin accent pinstripes
    drawRect(
        color = Color.White.copy(alpha = 0.88f),
        topLeft = Offset(hoodCenterX - 5f, -3.6f),
        size = Size(11f, 1.8f)
    )
    drawRect(
        color = Color.White.copy(alpha = 0.88f),
        topLeft = Offset(hoodCenterX - 5f, 1.8f),
        size = Size(11f, 1.8f)
    )
    // Geometric diamond + chevron apex crest emblem
    val badgeX = if (carType == RcCarType.DRIFT_PHANTOM) -1.5f else hoodCenterX
    val diamond = Path().apply {
        moveTo(badgeX + 5.2f, 0f)
        lineTo(badgeX, -4.6f)
        lineTo(badgeX - 5.2f, 0f)
        lineTo(badgeX, 4.6f)
        close()
    }
    drawPath(diamond, Color(0xFF0F172A))
    drawPath(diamond, Color.White, style = Stroke(width = 1.2f))
    val chevron = Path().apply {
        moveTo(badgeX + 2.6f, 0f)
        lineTo(badgeX - 1.4f, -2.3f)
        lineTo(badgeX - 1.4f, 2.3f)
        close()
    }
    drawPath(chevron, accent)
}

private fun DrawScope.drawWhipAntenna(
    steerVisual: Float,
    animTimeSec: Float,
    accent: Color
) {
    val mount = Offset(-10f, -8.5f)
    val sway = sin(animTimeSec * 9.5f) * 2.8f + steerVisual * 5.5f
    val ctrl = Offset(-20f, -12.5f + sway * 0.45f)
    val tip = Offset(-29.5f, -15.5f + sway)
    val whip = Path().apply {
        moveTo(mount.x, mount.y)
        quadraticTo(ctrl.x, ctrl.y, tip.x, tip.y)
    }
    drawCircle(Color(0xFF263238), radius = 2.4f, center = mount)
    drawPath(whip, Color(0xFFE2E8F0), style = Stroke(width = 1.7f, cap = StrokeCap.Round))
    drawCircle(accent.copy(alpha = 0.45f), radius = 4.5f, center = tip)
    drawCircle(accent, radius = 2.6f, center = tip)
    drawCircle(Color.White, radius = 1.1f, center = tip)
}

private fun DrawScope.drawUnderglowNeon(
    accent: Color,
    nightIntensity: Float,
    animTimeSec: Float
) {
    val clampedNight = nightIntensity.coerceIn(0f, 1f)
    val pulse = 0.88f + 0.12f * sin(animTimeSec * 4.5f)
    val outerAlpha = (0.18f + 0.28f * clampedNight) * pulse
    val innerAlpha = (0.32f + 0.38f * clampedNight) * pulse
    val rimAlpha = (0.50f + 0.36f * clampedNight) * pulse
    // Wide diffuse floor reflection
    drawOval(
        color = accent.copy(alpha = outerAlpha.coerceIn(0f, 0.72f)),
        topLeft = Offset(-32f, -23f),
        size = Size(64f, 46f)
    )
    // Concentrated chassis perimeter neon glow
    drawOval(
        color = accent.copy(alpha = innerAlpha.coerceIn(0f, 0.82f)),
        topLeft = Offset(-25.5f, -17.5f),
        size = Size(51f, 35f)
    )
    // Bright neon tube rim
    drawOval(
        color = accent.copy(alpha = rimAlpha.coerceIn(0f, 0.95f)),
        topLeft = Offset(-25f, -17f),
        size = Size(50f, 34f),
        style = Stroke(width = 1.8f + 1.1f * clampedNight)
    )
}

private fun DrawScope.drawCarLights(
    carType: RcCarType,
    isBraking: Boolean,
    nightIntensity: Float
) {
    val clampedNight = nightIntensity.coerceIn(0f, 1f)
    val (frontX, frontY, rearX, rearY) = when (carType) {
        RcCarType.VOLT_BUGGY -> floatArrayOf(17.5f, 5.5f, -18.5f, 7.5f)
        RcCarType.APEX_FORMULA -> floatArrayOf(19.5f, 7.5f, -22.5f, 10.0f)
        RcCarType.TUNDRA_MONSTER -> floatArrayOf(20.5f, 7.8f, -19.2f, 8.5f)
        RcCarType.DRIFT_PHANTOM -> floatArrayOf(21.2f, 7.8f, -20.8f, 8.0f)
    }

    // Front Headlights (warm xenon/halogen emitter housings + bright bloom at night)
    for (signY in listOf(-1f, 1f)) {
        val ly = frontY * signY
        if (clampedNight > 0.02f) {
            drawCircle(
                color = Color(0xFFFFF59D).copy(alpha = 0.24f * clampedNight),
                radius = 10.5f,
                center = Offset(frontX + 2.5f, ly)
            )
            drawCircle(
                color = Color(0xFFFFFDE7).copy(alpha = 0.42f * clampedNight),
                radius = 5.6f,
                center = Offset(frontX + 1.2f, ly)
            )
        }
        // Emitter housing & bright lens core
        drawRoundRect(
            color = Color(0xFFFFF59D).copy(alpha = 0.72f + 0.28f * clampedNight),
            topLeft = Offset(frontX - 1.8f, ly - 2.6f),
            size = Size(3.6f, 5.2f),
            cornerRadius = CornerRadius(1.5f, 1.5f)
        )
        drawCircle(
            color = Color(0xFFFFFFFF).copy(alpha = 0.78f + 0.22f * clampedNight),
            radius = 1.6f + 0.5f * clampedNight,
            center = Offset(frontX + 0.4f, ly)
        )
    }

    // Roof auxiliary spotlamps for Tundra Monster at night or subtle detail in day
    if (carType == RcCarType.TUNDRA_MONSTER) {
        for (ry in listOf(-4.5f, 0f, 4.5f)) {
            if (clampedNight > 0.05f) {
                drawCircle(
                    color = Color(0xFFFFF59D).copy(alpha = 0.30f * clampedNight),
                    radius = 4.2f,
                    center = Offset(10f, ry)
                )
            }
            drawCircle(
                color = Color(0xFFFFF9C4).copy(alpha = 0.75f + 0.25f * clampedNight),
                radius = 1.4f,
                center = Offset(9.5f, ry)
            )
        }
    }

    // Rear Taillights & Brake Flare
    val tailAlpha = when {
        isBraking -> 1f
        clampedNight > 0.02f -> 0.55f + 0.35f * clampedNight
        else -> 0.65f
    }
    val glowRadius = when {
        isBraking -> 11.5f
        clampedNight > 0.02f -> 6.8f
        else -> 0f
    }
    val glowAlpha = when {
        isBraking -> 0.46f + 0.24f * clampedNight
        clampedNight > 0.02f -> 0.34f * clampedNight
        else -> 0f
    }

    for (signY in listOf(-1f, 1f)) {
        val ry = rearY * signY
        if (glowAlpha > 0.01f) {
            drawCircle(
                color = Color(0xFFFF1744).copy(alpha = glowAlpha),
                radius = glowRadius,
                center = Offset(rearX - 1.5f, ry)
            )
        }
        drawRoundRect(
            color = if (isBraking || clampedNight > 0.15f) {
                Color(0xFFFF1744).copy(alpha = tailAlpha)
            } else {
                Color(0xFFB71C1C).copy(alpha = 0.85f)
            },
            topLeft = Offset(rearX - 1.6f, ry - 2.9f),
            size = Size(3.2f, 5.8f),
            cornerRadius = CornerRadius(1.2f, 1.2f)
        )
        if (isBraking || clampedNight > 0.25f) {
            drawCircle(
                color = if (isBraking) Color(0xFFFFCDD2) else Color(0xFFFF5252),
                radius = if (isBraking) 1.9f else 1.2f,
                center = Offset(rearX - 0.3f, ry)
            )
        }
    }

    // Apex Formula central F1 rain/brake rear LED + Drift Phantom rear light bar accents
    if (carType == RcCarType.APEX_FORMULA) {
        if (isBraking || clampedNight > 0.1f) {
            drawCircle(
                color = Color(0xFFFF1744).copy(alpha = if (isBraking) 0.58f else 0.38f * clampedNight),
                radius = if (isBraking) 9.5f else 6f,
                center = Offset(rearX - 1.2f, 0f)
            )
        }
        drawRoundRect(
            color = if (isBraking || clampedNight > 0.1f) Color(0xFFFF1744) else Color(0xFFB71C1C),
            topLeft = Offset(rearX - 1.5f, -2.0f),
            size = Size(3.0f, 4.0f),
            cornerRadius = CornerRadius(1.0f, 1.0f)
        )
        if (isBraking) {
            drawCircle(Color(0xFFFFEBEE), radius = 1.4f, center = Offset(rearX - 0.2f, 0f))
        }
    }
}


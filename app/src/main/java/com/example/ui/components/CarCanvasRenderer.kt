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
    isElectroParalyzed: Boolean = false
) {
    val deg = ((angleRad + spinVisualOffset) * 180f / PI.toFloat())
    val frontWheelAngle = steerVisual * 22f
    val airElevation = jumpZ.coerceIn(0f, 1.4f)
    val airScale = scale * (1f + airElevation * 0.44f)
    val shadowDx = airElevation * 38f * scale
    val shadowDy = airElevation * 44f * scale
    val shadowScale = scale * (1f - airElevation * 0.14f)

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

        // Boost exhaust flame
        if (isBoosting) {
            val flicker = 1f + 0.25f * sin(animTimeSec * 45f)
            val flamePath = Path().apply {
                moveTo(-20f, -6f)
                lineTo(-20f - 22f * flicker, 0f)
                lineTo(-20f, 6f)
                close()
            }
            drawPath(flamePath, Color(0xFFFF6D00).copy(alpha = 0.9f))
            drawCircle(Color(0xFFFFEA00), radius = 5f * flicker, center = Offset(-23f, 0f))
        }

        // Overdrive neon underglow
        if (hasOverdrive) {
            drawOval(
                color = Color(0xFFE040FB).copy(alpha = 0.35f),
                topLeft = Offset(-28f, -20f),
                size = Size(56f, 40f)
            )
        }

        when (carType) {
            RcCarType.VOLT_BUGGY -> drawVoltBuggy(primaryColor, accentColor, frontWheelAngle)
            RcCarType.APEX_FORMULA -> drawApexFormula(primaryColor, accentColor, frontWheelAngle)
            RcCarType.TUNDRA_MONSTER -> drawTundraMonster(primaryColor, accentColor, frontWheelAngle)
            RcCarType.DRIFT_PHANTOM -> drawDriftPhantom(primaryColor, accentColor, frontWheelAngle)
        }

        // Headlights glow
        drawCircle(Color(0xFFFFF59D).copy(alpha = 0.9f), radius = 2.5f, center = Offset(20f, -7f))
        drawCircle(Color(0xFFFFF59D).copy(alpha = 0.9f), radius = 2.5f, center = Offset(20f, 7f))

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

private fun DrawScope.drawWheel(
    cx: Float,
    cy: Float,
    w: Float,
    h: Float,
    angleDeg: Float = 0f,
    tireColor: Color = Color(0xFF15181F)
) {
    rotate(degrees = angleDeg, pivot = Offset(cx, cy)) {
        drawRoundRect(
            color = tireColor,
            topLeft = Offset(cx - w / 2f, cy - h / 2f),
            size = Size(w, h),
            cornerRadius = CornerRadius(2.5f, 2.5f)
        )
    }
}

private fun DrawScope.drawVoltBuggy(primary: Color, accent: Color, frontAngle: Float) {
    // Suspension arms
    drawLine(Color(0xFF455A64), Offset(12f, -15f), Offset(12f, 15f), strokeWidth = 3f)
    drawLine(Color(0xFF455A64), Offset(-13f, -16f), Offset(-13f, 16f), strokeWidth = 3.5f)
    // Wheels
    drawWheel(13f, -16f, 11f, 6f, frontAngle)
    drawWheel(13f, 16f, 11f, 6f, frontAngle)
    drawWheel(-14f, -17f, 13f, 7f)
    drawWheel(-14f, 17f, 13f, 7f)
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

private fun DrawScope.drawApexFormula(primary: Color, accent: Color, frontAngle: Float) {
    // Wide open-wheel F1 tires
    drawWheel(13f, -15f, 11f, 6f, frontAngle)
    drawWheel(13f, 15f, 11f, 6f, frontAngle)
    drawWheel(-14f, -16f, 13f, 7.5f)
    drawWheel(-14f, 16f, 13f, 7.5f)
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

private fun DrawScope.drawTundraMonster(primary: Color, accent: Color, frontAngle: Float) {
    // Oversized monster tires
    drawWheel(13f, -18f, 14f, 8f, frontAngle, Color(0xFF101318))
    drawWheel(13f, 18f, 14f, 8f, frontAngle, Color(0xFF101318))
    drawWheel(-13f, -18f, 14f, 8f, 0f, Color(0xFF101318))
    drawWheel(-13f, 18f, 14f, 8f, 0f, Color(0xFF101318))
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

private fun DrawScope.drawDriftPhantom(primary: Color, accent: Color, frontAngle: Float) {
    // Stanced tuner wheels
    drawWheel(13f, -14.5f, 11f, 5.5f, frontAngle)
    drawWheel(13f, 14.5f, 11f, 5.5f, frontAngle)
    drawWheel(-13f, -14.5f, 11f, 5.5f)
    drawWheel(-13f, 14.5f, 11f, 5.5f)
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

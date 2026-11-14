package com.example.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import com.example.model.ItemBoxState
import com.example.model.PropShape
import com.example.model.RacerState
import com.example.model.RcCarType
import com.example.model.TrackDefinition
import com.example.model.TrackHazard
import com.example.model.TrackId
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

fun DrawScope.drawTrackWorld(
    track: TrackDefinition,
    itemBoxes: List<ItemBoxState>,
    hazards: List<TrackHazard>,
    racers: List<RacerState>,
    animTimeSec: Float
) {
    // 1. Background floor
    drawRect(
        color = Color(track.id.baseBgColorHex),
        topLeft = Offset.Zero,
        size = Size(track.worldWidth, track.worldHeight)
    )

    // 2. Subtle themed background texture
    drawEnvironmentDetails(track)

    // 3. Build closed centerline path
    val pts = track.centerline
    if (pts.isEmpty()) return
    val roadPath = Path().apply {
        moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) {
            lineTo(pts[i].x, pts[i].y)
        }
        close()
    }

    val avgHalfW = pts[0].halfWidth
    val roadWidth = avgHalfW * 2f

    // Outer kerb / shoulder
    drawPath(
        path = roadPath,
        color = Color(track.id.kerbSecondaryHex),
        style = Stroke(width = roadWidth + 24f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
    drawPath(
        path = roadPath,
        color = Color(track.id.kerbPrimaryHex),
        style = Stroke(
            width = roadWidth + 24f,
            cap = StrokeCap.Butt,
            join = StrokeJoin.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(38f, 38f), 0f)
        )
    )

    // Main track surface
    drawPath(
        path = roadPath,
        color = Color(track.id.roadColorHex),
        style = Stroke(width = roadWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    // Subtle racing groove / centerline dashes
    drawPath(
        path = roadPath,
        color = Color.White.copy(alpha = 0.13f),
        style = Stroke(
            width = 4f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(28f, 32f), 0f)
        )
    )

    // 4. Start / Finish checkered gantry
    val p0 = pts[0]
    val p1 = pts[1]
    val startAngle = atan2(p1.y - p0.y, p1.x - p0.x) * 180f / PI.toFloat()
    withTransform({
        translate(p0.x, p0.y)
        rotate(startAngle, Offset.Zero)
    }) {
        val cells = 10
        val cellSize = roadWidth / cells
        val startY = -roadWidth / 2f
        for (row in 0 until 2) {
            for (col in 0 until cells) {
                val isWhite = (row + col) % 2 == 0
                drawRect(
                    color = if (isWhite) Color.White else Color(0xFF11141B),
                    topLeft = Offset(-cellSize + row * cellSize, startY + col * cellSize),
                    size = Size(cellSize, cellSize)
                )
            }
        }
    }

    // 5. Boost pads
    for (pad in track.boostPads) {
        val deg = pad.angleRad * 180f / PI.toFloat()
        withTransform({
            translate(pad.x, pad.y)
            rotate(deg, Offset.Zero)
        }) {
            drawRoundRect(
                color = Color(0xFF00E5FF).copy(alpha = 0.25f),
                topLeft = Offset(-36f, -24f),
                size = Size(72f, 48f),
                cornerRadius = CornerRadius(10f, 10f)
            )
            val pulse = 0.5f + 0.5f * sin(animTimeSec * 8f)
            for (c in -1..1) {
                val cx = c * 18f
                val chev = Path().apply {
                    moveTo(cx - 8f, -14f)
                    lineTo(cx + 8f, 0f)
                    lineTo(cx - 8f, 14f)
                }
                drawPath(
                    path = chev,
                    color = Color(0xFF00E5FF).copy(alpha = 0.55f + 0.45f * pulse),
                    style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
    }

    // 6. Props & obstacles
    for (prop in track.props) {
        when (prop.shape) {
            PropShape.CIRCLE -> {
                drawCircle(Color(prop.colorHex), prop.radius, Offset(prop.x, prop.y))
                drawCircle(Color(prop.accentHex), prop.radius * 0.65f, Offset(prop.x, prop.y), style = Stroke(4f))
            }
            PropShape.CRATE -> {
                val r = prop.radius
                drawRoundRect(
                    Color(prop.colorHex),
                    Offset(prop.x - r, prop.y - r),
                    Size(r * 2, r * 2),
                    CornerRadius(8f, 8f)
                )
                drawRoundRect(
                    Color(prop.accentHex),
                    Offset(prop.x - r, prop.y - r),
                    Size(r * 2, r * 2),
                    CornerRadius(8f, 8f),
                    style = Stroke(3f)
                )
                drawLine(Color(prop.accentHex), Offset(prop.x - r + 6f, prop.y - r + 6f), Offset(prop.x + r - 6f, prop.y + r - 6f), 3f)
            }
            PropShape.TREE -> {
                drawCircle(Color.Black.copy(alpha = 0.25f), prop.radius, Offset(prop.x + 8f, prop.y + 8f))
                drawCircle(Color(prop.colorHex), prop.radius, Offset(prop.x, prop.y))
                drawCircle(Color(prop.accentHex).copy(alpha = 0.7f), prop.radius * 0.58f, Offset(prop.x - 6f, prop.y - 6f))
            }
            PropShape.CACTUS -> {
                drawCircle(Color.Black.copy(alpha = 0.2f), prop.radius * 0.9f, Offset(prop.x + 6f, prop.y + 6f))
                drawCircle(Color(prop.colorHex), prop.radius, Offset(prop.x, prop.y))
                drawCircle(Color(prop.accentHex), prop.radius * 0.45f, Offset(prop.x, prop.y))
            }
            PropShape.PYLON -> {
                drawCircle(Color(prop.colorHex).copy(alpha = 0.25f), prop.radius * 1.3f, Offset(prop.x, prop.y))
                drawCircle(Color(prop.colorHex), prop.radius * 0.85f, Offset(prop.x, prop.y))
                drawCircle(Color(prop.accentHex), prop.radius * 0.35f, Offset(prop.x, prop.y))
            }
            PropShape.MUD_POOL -> {
                drawOval(
                    Color(prop.colorHex).copy(alpha = 0.75f),
                    Offset(prop.x - prop.radius, prop.y - prop.radius * 0.75f),
                    Size(prop.radius * 2f, prop.radius * 1.5f)
                )
                drawOval(
                    Color(prop.accentHex).copy(alpha = 0.4f),
                    Offset(prop.x - prop.radius * 0.6f, prop.y - prop.radius * 0.4f),
                    Size(prop.radius * 1.2f, prop.radius * 0.8f)
                )
            }
        }
    }

    // 7. Dropped Glue Slick hazards
    for (h in hazards) {
        drawCircle(Color(0xFFFFD54F).copy(alpha = 0.8f), h.radius, Offset(h.x, h.y))
        drawCircle(Color(0xFFFF8F00), h.radius * 0.65f, Offset(h.x + 4f, h.y - 3f))
        drawCircle(Color(0xFFFFECB3), h.radius * 0.25f, Offset(h.x - 7f, h.y + 5f))
    }

    // 8. Mystery Power-Up Item Boxes
    for (box in itemBoxes) {
        if (!box.available) continue
        val pulse = 1f + 0.08f * sin(animTimeSec * 5f + box.id)
        val rotDeg = (animTimeSec * 45f + box.id * 30f) % 360f
        withTransform({
            translate(box.x, box.y)
            scale(pulse, pulse, Offset.Zero)
            rotate(rotDeg, Offset.Zero)
        }) {
            drawRoundRect(
                color = Color(0xFFFF5722).copy(alpha = 0.3f),
                topLeft = Offset(-22f, -22f),
                size = Size(44f, 44f),
                cornerRadius = CornerRadius(10f, 10f)
            )
            drawRoundRect(
                color = Color(0xFF1D2638),
                topLeft = Offset(-16f, -16f),
                size = Size(32f, 32f),
                cornerRadius = CornerRadius(7f, 7f)
            )
            drawRoundRect(
                color = Color(0xFF00E5FF),
                topLeft = Offset(-16f, -16f),
                size = Size(32f, 32f),
                cornerRadius = CornerRadius(7f, 7f),
                style = Stroke(width = 3f)
            )
            drawCircle(Color(0xFFFFD54F), radius = 6f, center = Offset.Zero)
        }
    }

    // 9. Draw all RC cars
    for (r in racers) {
        val carType = runCatching { RcCarType.valueOf(r.carType) }.getOrDefault(RcCarType.VOLT_BUGGY)
        drawRcCar(
            x = r.x,
            y = r.y,
            angleRad = r.angleRad,
            carType = carType,
            primaryColor = Color(r.primaryColorArgb),
            accentColor = Color(r.accentColorArgb),
            steerVisual = r.steerVisual,
            isBoosting = r.boostMs > 0L,
            hasShield = r.shieldMs > 0L,
            isStunned = r.stunnedMs > 0L,
            hasOverdrive = r.overdriveMs > 0L,
            spinVisualOffset = r.spinVisualOffset,
            scale = 1f,
            animTimeSec = animTimeSec
        )
    }
}

private fun DrawScope.drawEnvironmentDetails(track: TrackDefinition) {
    when (track.id) {
        TrackId.LIVING_ROOM_GP -> {
            // Hardwood floor planks
            val plankColor = Color.Black.copy(alpha = 0.14f)
            var y = 0f
            while (y < track.worldHeight) {
                drawLine(plankColor, Offset(0f, y), Offset(track.worldWidth, y), strokeWidth = 2f)
                y += 72f
            }
            // Decorative living room area rug in center
            drawRoundRect(
                color = Color(0xFF283593).copy(alpha = 0.35f),
                topLeft = Offset(580f, 480f),
                size = Size(440f, 360f),
                cornerRadius = CornerRadius(18f, 18f)
            )
        }
        TrackId.NEON_ROOFTOP -> {
            val gridColor = Color(0xFF00E5FF).copy(alpha = 0.08f)
            var x = 0f
            while (x <= track.worldWidth) {
                drawLine(gridColor, Offset(x, 0f), Offset(x, track.worldHeight), 1.5f)
                x += 120f
            }
            var y = 0f
            while (y <= track.worldHeight) {
                drawLine(gridColor, Offset(0f, y), Offset(track.worldWidth, y), 1.5f)
                y += 120f
            }
        }
        TrackId.WAREHOUSE_DRIFT -> {
            val jointColor = Color.White.copy(alpha = 0.07f)
            var x = 0f
            while (x <= track.worldWidth) {
                drawLine(jointColor, Offset(x, 0f), Offset(x, track.worldHeight), 2f)
                x += 220f
            }
            var y = 0f
            while (y <= track.worldHeight) {
                drawLine(jointColor, Offset(0f, y), Offset(track.worldWidth, y), 2f)
                y += 220f
            }
        }
        TrackId.BACKYARD_DIRT, TrackId.SANDBOX_DUNES -> {
            // Subtle organic terrain dots
            val dotColor = Color.Black.copy(alpha = 0.07f)
            for (i in 0 until 36) {
                val px = ((i * 397) % 2100) + 50f
                val py = ((i * 613) % 1400) + 50f
                drawCircle(dotColor, radius = (14 + (i % 5) * 6).toFloat(), center = Offset(px, py))
            }
        }
    }
}

fun DrawScope.drawTrackMiniMap(
    track: TrackDefinition,
    racers: List<RacerState> = emptyList(),
    localPlayerId: String? = null
) {
    val pad = 8.dp.toPx()
    val availW = (size.width - pad * 2f).coerceAtLeast(1f)
    val availH = (size.height - pad * 2f).coerceAtLeast(1f)
    val scale = minOf(availW / track.worldWidth, availH / track.worldHeight)
    val offsetX = (size.width - track.worldWidth * scale) / 2f
    val offsetY = (size.height - track.worldHeight * scale) / 2f

    val pts = track.centerline
    if (pts.isEmpty()) return

    val path = Path().apply {
        moveTo(offsetX + pts[0].x * scale, offsetY + pts[0].y * scale)
        for (i in 1 until pts.size) {
            lineTo(offsetX + pts[i].x * scale, offsetY + pts[i].y * scale)
        }
        close()
    }

    drawPath(
        path = path,
        color = Color(track.id.kerbPrimaryHex).copy(alpha = 0.55f),
        style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
    drawPath(
        path = path,
        color = Color(0xFF1F2736),
        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
    drawPath(
        path = path,
        color = Color.White.copy(alpha = 0.85f),
        style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    // Start/Finish tick
    val s = pts[0]
    val sNext = pts[1]
    val startAng = atan2(sNext.y - s.y, sNext.x - s.x)
    val perpX = -sin(startAng) * 4.5.dp.toPx()
    val perpY = cos(startAng) * 4.5.dp.toPx()
    val sx = offsetX + s.x * scale
    val sy = offsetY + s.y * scale
    drawLine(
        color = Color(0xFFFF5722),
        start = Offset(sx - perpX, sy - perpY),
        end = Offset(sx + perpX, sy + perpY),
        strokeWidth = 2.2.dp.toPx(),
        cap = StrokeCap.Round
    )

    // Draw rivals first, then local player on top so local car is always clearly visible
    val ordered = racers.sortedBy { it.playerId == localPlayerId }
    for (r in ordered) {
        val isLocal = r.playerId == localPlayerId
        val cx = offsetX + r.x * scale
        val cy = offsetY + r.y * scale
        val dotRadius = if (isLocal) 4.6.dp.toPx() else 3.6.dp.toPx()
        if (isLocal) {
            drawCircle(
                color = Color(0xFF00E5FF).copy(alpha = 0.4f),
                radius = 7.2.dp.toPx(),
                center = Offset(cx, cy)
            )
            drawCircle(
                color = Color.White,
                radius = dotRadius + 1.5.dp.toPx(),
                center = Offset(cx, cy)
            )
        } else {
            drawCircle(
                color = Color(0xFF0B0E14),
                radius = dotRadius + 1.2.dp.toPx(),
                center = Offset(cx, cy)
            )
        }
        drawCircle(
            color = Color(r.primaryColorArgb),
            radius = dotRadius,
            center = Offset(cx, cy)
        )
    }
}

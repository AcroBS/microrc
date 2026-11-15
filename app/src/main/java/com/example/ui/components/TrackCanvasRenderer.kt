package com.example.ui.components

import android.graphics.Paint
import android.graphics.Typeface
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.example.model.ArenaFlagState
import com.example.model.ExternalEventType
import com.example.model.ItemBoxState
import com.example.model.PropShape
import com.example.model.RacerState
import com.example.model.RcCarType
import com.example.model.TrackDefinition
import com.example.model.TrackHazard
import com.example.model.TrackId
import com.example.model.TrackPuddle
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private val racerNamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    textSize = 15f
    textAlign = Paint.Align.CENTER
    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
}

fun DrawScope.drawTrackWorld(
    track: TrackDefinition,
    itemBoxes: List<ItemBoxState>,
    hazards: List<TrackHazard>,
    racers: List<RacerState>,
    animTimeSec: Float,
    hardBoundary: Boolean = false,
    rainPuddles: List<TrackPuddle> = emptyList(),
    activeEvent: ExternalEventType = ExternalEventType.NONE,
    showPlayerNames: Boolean = false,
    localPlayerId: String? = null,
    isOverview: Boolean = false,
    arenaFlag: ArenaFlagState? = null
) {
    // 1. Background floor
    drawRect(
        color = Color(track.id.baseBgColorHex),
        topLeft = Offset.Zero,
        size = Size(track.worldWidth, track.worldHeight)
    )

    // 2. Subtle themed background texture
    drawEnvironmentDetails(track)

    // 3. Render either Open Arena Floor or Closed Circuit Road
    val pts = if (track.isArena) track.arenaBoundary.ifEmpty { track.centerline } else track.centerline
    if (pts.isEmpty()) return

    val roadPath = Path().apply {
        moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) {
            lineTo(pts[i].x, pts[i].y)
        }
        close()
    }

    if (track.isArena) {
        if (hardBoundary) {
            drawPath(
                path = roadPath,
                color = Color(0xFF0E131B),
                style = Stroke(width = 44f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            drawPath(
                path = roadPath,
                color = Color(0xFFFF6D00),
                style = Stroke(width = 34f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
        drawPath(
            path = roadPath,
            color = Color(track.id.kerbSecondaryHex),
            style = Stroke(width = 24f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawPath(
            path = roadPath,
            color = Color(track.id.kerbPrimaryHex),
            style = Stroke(
                width = 24f,
                cap = StrokeCap.Butt,
                join = StrokeJoin.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(40f, 40f), 0f)
            )
        )
        // Single large open Arena floor fill
        drawPath(
            path = roadPath,
            color = Color(track.id.roadColorHex)
        )
        if (activeEvent == ExternalEventType.SNOW) {
            drawPath(
                path = roadPath,
                color = Color(0xFFE3F2FD).copy(alpha = 0.22f)
            )
        }

        // Tactical Arena center rings & crosshairs
        val cx = track.worldWidth * 0.5f
        val cy = track.worldHeight * 0.5f
        drawCircle(
            color = Color.White.copy(alpha = 0.10f),
            radius = 270f,
            center = Offset(cx, cy),
            style = Stroke(
                width = 3.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(26f, 22f), 0f)
            )
        )
        drawCircle(
            color = Color(track.id.kerbPrimaryHex).copy(alpha = 0.22f),
            radius = 125f,
            center = Offset(cx, cy),
            style = Stroke(width = 4f)
        )
        drawLine(
            color = Color.White.copy(alpha = 0.10f),
            start = Offset(cx - 170f, cy),
            end = Offset(cx + 170f, cy),
            strokeWidth = 3f
        )
        drawLine(
            color = Color.White.copy(alpha = 0.10f),
            start = Offset(cx, cy - 170f),
            end = Offset(cx, cy + 170f),
            strokeWidth = 3f
        )
    } else {
        val avgHalfW = pts[0].halfWidth
        val roadWidth = avgHalfW * 2f

        if (hardBoundary) {
            drawPath(
                path = roadPath,
                color = Color(0xFF0E131B),
                style = Stroke(width = roadWidth + 36f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            drawPath(
                path = roadPath,
                color = Color(0xFFFF6D00),
                style = Stroke(width = roadWidth + 30f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

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

        drawPath(
            path = roadPath,
            color = Color(track.id.roadColorHex),
            style = Stroke(width = roadWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        if (activeEvent == ExternalEventType.SNOW) {
            drawPath(
                path = roadPath,
                color = Color(0xFFE3F2FD).copy(alpha = 0.24f),
                style = Stroke(width = roadWidth - 8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        drawPath(
            path = roadPath,
            color = Color.White.copy(alpha = 0.13f),
            style = Stroke(
                width = 4f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(28f, 32f), 0f)
            )
        )

        // 4. Start / Finish checkered gantry (Circuit mode only)
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
    }

    // Rain puddles on road surface (glossy blue with animated ripples)
    for (i in rainPuddles.indices) {
        val p = rainPuddles[i]
        drawOval(
            color = Color(0xFF0277BD).copy(alpha = 0.56f),
            topLeft = Offset(p.x - p.radius, p.y - p.radius * 0.78f),
            size = Size(p.radius * 2f, p.radius * 1.56f)
        )
        drawOval(
            color = Color(0xFF4FC3F7).copy(alpha = 0.45f),
            topLeft = Offset(p.x - p.radius * 0.72f, p.y - p.radius * 0.52f),
            size = Size(p.radius * 1.44f, p.radius * 1.04f)
        )
        val ripplePhase = ((animTimeSec * 1.8f + i * 0.37f) % 1f)
        val rr = p.radius * (0.25f + 0.65f * ripplePhase)
        drawCircle(
            color = Color(0xFFB3E5FC).copy(alpha = (1f - ripplePhase) * 0.6f),
            radius = rr,
            center = Offset(p.x, p.y),
            style = Stroke(width = 2.5f)
        )
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

    // 5b. Jump Ramps (top-down 2.5D sloped deck + launch shadow)
    for (ramp in track.ramps) {
        val deg = ramp.angleRad * 180f / PI.toFloat()
        withTransform({
            translate(ramp.x, ramp.y)
            rotate(deg, Offset.Zero)
        }) {
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.48f),
                topLeft = Offset(-28f, -24f),
                size = Size(82f, 54f),
                cornerRadius = CornerRadius(8f, 8f)
            )
            drawRoundRect(
                color = Color(0xFF4E342E),
                topLeft = Offset(-42f, -26f),
                size = Size(44f, 52f),
                cornerRadius = CornerRadius(6f, 6f)
            )
            drawRoundRect(
                color = Color(0xFF8D6E63),
                topLeft = Offset(-2f, -26f),
                size = Size(42f, 52f),
                cornerRadius = CornerRadius(6f, 6f)
            )
            drawRect(
                color = Color(0xFFFFAB00),
                topLeft = Offset(30f, -27f),
                size = Size(12f, 54f)
            )
            drawRect(
                color = Color(0xFFFFEA00),
                topLeft = Offset(-42f, -28f),
                size = Size(84f, 5f)
            )
            drawRect(
                color = Color(0xFFFFEA00),
                topLeft = Offset(-42f, 23f),
                size = Size(84f, 5f)
            )
            for (c in -1..1) {
                val cx = c * 18f - 4f
                val chev = Path().apply {
                    moveTo(cx - 7f, -13f)
                    lineTo(cx + 8f, 0f)
                    lineTo(cx - 7f, 13f)
                }
                drawPath(
                    path = chev,
                    color = Color(0xFFFFEA00),
                    style = Stroke(width = 4.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
    }

    // 6. Props & obstacles (including Arena Oil Slicks!)
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
                    Color(prop.colorHex).copy(alpha = if (track.isArena) 0.86f else 0.75f),
                    Offset(prop.x - prop.radius, prop.y - prop.radius * 0.78f),
                    Size(prop.radius * 2f, prop.radius * 1.56f)
                )
                drawOval(
                    Color(prop.accentHex).copy(alpha = 0.42f),
                    Offset(prop.x - prop.radius * 0.62f, prop.y - prop.radius * 0.42f),
                    Size(prop.radius * 1.24f, prop.radius * 0.84f)
                )
                if (track.isArena) {
                    // Oil-slick rainbow sheen highlight
                    drawOval(
                        Color(0xFFE040FB).copy(alpha = 0.32f),
                        Offset(prop.x - prop.radius * 0.35f, prop.y - prop.radius * 0.2f),
                        Size(prop.radius * 0.78f, prop.radius * 0.48f)
                    )
                }
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

    // 8b. Loose Arena Flag on the map (when nobody is carrying it)
    if (arenaFlag != null && arenaFlag.carrierId == null) {
        val fx = arenaFlag.x
        val fy = arenaFlag.y
        val pulse = 42f + 6f * sin(animTimeSec * 6f)
        drawCircle(
            color = Color(0xFFFFD54F).copy(alpha = 0.22f),
            radius = pulse,
            center = Offset(fx, fy)
        )
        drawCircle(
            color = Color(0xFFFFD54F),
            radius = 46f,
            center = Offset(fx, fy),
            style = Stroke(
                width = 3f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), animTimeSec * 40f)
            )
        )
        // Pedestal shadow + pole + waving flag banner
        drawCircle(Color.Black.copy(alpha = 0.45f), radius = 14f, center = Offset(fx + 4f, fy + 5f))
        drawCircle(Color(0xFF263238), radius = 12f, center = Offset(fx, fy))
        drawCircle(Color(0xFFFFD54F), radius = 8f, center = Offset(fx, fy), style = Stroke(width = 2.5f))
        drawLine(
            color = Color.White,
            start = Offset(fx, fy),
            end = Offset(fx, fy - 48f),
            strokeWidth = 4.5f,
            cap = StrokeCap.Round
        )
        val wave = sin(animTimeSec * 10f) * 6f
        val banner = Path().apply {
            moveTo(fx + 1f, fy - 47f)
            lineTo(fx + 38f, fy - 38f + wave)
            lineTo(fx + 30f, fy - 28f + wave * 0.6f)
            lineTo(fx + 39f, fy - 19f + wave)
            lineTo(fx + 1f, fy - 20f)
            close()
        }
        drawPath(banner, Color(0xFFFFAB00))
        drawPath(banner, Color(0xFFFFF8E1), style = Stroke(width = 2f))
    }

    // 9. Draw all RC cars (grounded first, airborne cars rendered on top)
    val orderedRacers = racers.sortedBy { it.jumpZ }
    for (r in orderedRacers) {
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
            jumpZ = r.jumpZ,
            scale = 1f,
            animTimeSec = animTimeSec,
            isCarryingFlag = arenaFlag?.carrierId == r.playerId
        )
    }

    // 10. Player name tags + Arena Flag Carrier live timer pill
    val textScale = if (isOverview) 1.65f else 1f
    racerNamePaint.textSize = 15f * textScale
    val fm = racerNamePaint.fontMetrics
    val textHeight = fm.descent - fm.ascent

    for (r in orderedRacers) {
        val isFlagCarrier = arenaFlag?.carrierId == r.playerId
        if (!showPlayerNames && !isFlagCarrier) continue

        val baseName = r.name.trim().take(12).ifEmpty { "Racer" }
        val carrySec = r.flagCarryMs / 1000L
        val label = when {
            isFlagCarrier && showPlayerNames -> "⚑ $baseName • ${carrySec}s"
            isFlagCarrier -> "⚑ FLAG • ${carrySec}s"
            else -> baseName
        }
        val isLocal = r.playerId == localPlayerId
        val labelX = r.x
        val labelY = r.y - (if (isFlagCarrier) 44f else 38f) * textScale - (r.jumpZ * 26f)
        val textWidth = racerNamePaint.measureText(label)
        val padX = 8f * textScale
        val padY = 3.5f * textScale
        val pillW = textWidth + padX * 2f
        val pillH = textHeight + padY * 2f
        val pillLeft = labelX - pillW / 2f
        val pillTop = labelY - pillH / 2f

        drawRoundRect(
            color = if (isFlagCarrier) {
                Color(0xFF281A00).copy(alpha = 0.92f)
            } else {
                Color(0xFF0B0E14).copy(alpha = 0.80f)
            },
            topLeft = Offset(pillLeft, pillTop),
            size = Size(pillW, pillH),
            cornerRadius = CornerRadius(7f * textScale, 7f * textScale)
        )
        val borderColor = when {
            isFlagCarrier -> Color(0xFFFFD54F)
            isLocal -> Color(0xFF00E5FF)
            else -> Color(r.primaryColorArgb).copy(alpha = 0.9f)
        }
        drawRoundRect(
            color = borderColor,
            topLeft = Offset(pillLeft, pillTop),
            size = Size(pillW, pillH),
            cornerRadius = CornerRadius(7f * textScale, 7f * textScale),
            style = Stroke(width = if (isFlagCarrier) 2.2f * textScale else 1.6f * textScale)
        )

        racerNamePaint.color = when {
            isFlagCarrier -> Color(0xFFFFD54F).toArgb()
            isLocal -> Color(0xFF00E5FF).toArgb()
            else -> Color.White.toArgb()
        }
        val baselineY = labelY - (fm.ascent + fm.descent) / 2f
        drawContext.canvas.nativeCanvas.drawText(label, labelX, baselineY, racerNamePaint)
    }
}

private fun DrawScope.drawEnvironmentDetails(track: TrackDefinition) {
    when (track.id) {
        TrackId.LIVING_ROOM_GP -> {
            val plankColor = Color.Black.copy(alpha = 0.14f)
            var y = 0f
            while (y < track.worldHeight) {
                drawLine(plankColor, Offset(0f, y), Offset(track.worldWidth, y), strokeWidth = 2f)
                y += 72f
            }
            if (!track.isArena) {
                drawRoundRect(
                    color = Color(0xFF283593).copy(alpha = 0.35f),
                    topLeft = Offset(580f, 480f),
                    size = Size(440f, 360f),
                    cornerRadius = CornerRadius(18f, 18f)
                )
            }
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
    localPlayerId: String? = null,
    arenaFlag: ArenaFlagState? = null
) {
    val pad = 8.dp.toPx()
    val availW = (size.width - pad * 2f).coerceAtLeast(1f)
    val availH = (size.height - pad * 2f).coerceAtLeast(1f)
    val scale = minOf(availW / track.worldWidth, availH / track.worldHeight)
    val offsetX = (size.width - track.worldWidth * scale) / 2f
    val offsetY = (size.height - track.worldHeight * scale) / 2f

    val pts = if (track.isArena) track.arenaBoundary.ifEmpty { track.centerline } else track.centerline
    if (pts.isEmpty()) return

    val path = Path().apply {
        moveTo(offsetX + pts[0].x * scale, offsetY + pts[0].y * scale)
        for (i in 1 until pts.size) {
            lineTo(offsetX + pts[i].x * scale, offsetY + pts[i].y * scale)
        }
        close()
    }

    if (track.isArena) {
        drawPath(
            path = path,
            color = Color(track.id.roadColorHex).copy(alpha = 0.92f)
        )
        drawPath(
            path = path,
            color = Color(track.id.kerbPrimaryHex),
            style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    } else {
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
    }

    // Loose flag beacon on mini-map
    if (arenaFlag != null && arenaFlag.carrierId == null) {
        val fx = offsetX + arenaFlag.x * scale
        val fy = offsetY + arenaFlag.y * scale
        drawCircle(
            color = Color(0xFFFFD54F).copy(alpha = 0.38f),
            radius = 6.5.dp.toPx(),
            center = Offset(fx, fy)
        )
        drawCircle(
            color = Color(0xFFFFD54F),
            radius = 3.6.dp.toPx(),
            center = Offset(fx, fy)
        )
    }

    // Draw rivals first, then local player on top so local car is always clearly visible
    val ordered = racers.sortedBy { it.playerId == localPlayerId }
    for (r in ordered) {
        val isLocal = r.playerId == localPlayerId
        val hasFlag = arenaFlag?.carrierId == r.playerId
        val cx = offsetX + r.x * scale
        val cy = offsetY + r.y * scale
        val dotRadius = if (isLocal) 4.6.dp.toPx() else 3.6.dp.toPx()
        if (hasFlag) {
            drawCircle(
                color = Color(0xFFFFD54F),
                radius = dotRadius + 3.2.dp.toPx(),
                center = Offset(cx, cy),
                style = Stroke(width = 1.8.dp.toPx())
            )
        }
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

fun DrawScope.drawWeatherScreenOverlay(
    activeEvent: ExternalEventType,
    animTimeSec: Float,
    windAngleRad: Float,
    windStrength: Float
) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return

    when (activeEvent) {
        ExternalEventType.RAIN -> {
            drawRect(color = Color(0xFF0A2442).copy(alpha = 0.16f))
            val dropColor = Color(0xFFB3E5FC).copy(alpha = 0.48f)
            for (i in 0 until 54) {
                val seedX = ((i * 197 + 43) % 1000) / 1000f
                val seedY = ((i * 353 + 17) % 1000) / 1000f
                val t = (seedY + animTimeSec * (1.25f + (i % 4) * 0.12f)) % 1f
                val x = ((seedX * w + t * 90f) % (w + 60f)) - 30f
                val y = t * (h + 60f) - 30f
                drawLine(
                    color = dropColor,
                    start = Offset(x, y),
                    end = Offset(x + 12f, y + 32f),
                    strokeWidth = 2.2f,
                    cap = StrokeCap.Round
                )
            }
        }
        ExternalEventType.SNOW -> {
            drawRect(color = Color(0xFFDCEEFB).copy(alpha = 0.12f))
            for (i in 0 until 48) {
                val seedX = ((i * 251 + 71) % 1000) / 1000f
                val seedY = ((i * 419 + 29) % 1000) / 1000f
                val fall = (seedY + animTimeSec * (0.28f + (i % 3) * 0.07f)) % 1f
                val sway = sin(animTimeSec * 2.2f + i) * 26f
                val x = ((seedX * w + sway + w) % w)
                val y = fall * h
                val radius = (3f + (i % 4) * 1.4f)
                drawCircle(
                    color = Color.White.copy(alpha = 0.76f),
                    radius = radius,
                    center = Offset(x, y)
                )
            }
        }
        ExternalEventType.WIND -> {
            val dirX = cos(windAngleRad)
            val dirY = sin(windAngleRad)
            val speedScale = 0.45f + windStrength * 0.35f
            val leafColors = listOf(
                Color(0xFF7CB342),
                Color(0xFFFFB300),
                Color(0xFFE65100),
                Color(0xFF9CCC65)
            )
            for (i in 0 until 14) {
                val baseX = ((i * 313 + 89) % 1000) / 1000f
                val baseY = ((i * 547 + 19) % 1000) / 1000f
                val prog = (animTimeSec * speedScale + baseX) % 1f
                val cx = ((baseX + dirX * prog) % 1f + 1f) % 1f * w
                val cy = ((baseY + dirY * prog) % 1f + 1f) % 1f * h
                drawLine(
                    color = Color.White.copy(alpha = 0.25f),
                    start = Offset(cx - dirX * 36f, cy - dirY * 36f),
                    end = Offset(cx + dirX * 36f, cy + dirY * 36f),
                    strokeWidth = 2.5f,
                    cap = StrokeCap.Round
                )
            }
            for (i in 0 until 26) {
                val baseX = ((i * 179 + 31) % 1000) / 1000f
                val baseY = ((i * 601 + 53) % 1000) / 1000f
                val prog = (animTimeSec * (speedScale * (0.85f + (i % 4) * 0.1f)) + i * 0.17f) % 1f
                val wobble = sin(animTimeSec * 4.5f + i) * 20f
                val perpX = -dirY
                val perpY = dirX
                val lx = (((baseX + dirX * prog) % 1f + 1f) % 1f) * w + perpX * wobble
                val ly = (((baseY + dirY * prog) % 1f + 1f) % 1f) * h + perpY * wobble
                val spinDeg = (animTimeSec * 220f + i * 47f) % 360f
                val leafColor = leafColors[i % leafColors.size].copy(alpha = 0.88f)
                withTransform({
                    translate(lx, ly)
                    rotate(spinDeg, Offset.Zero)
                }) {
                    drawOval(
                        color = leafColor,
                        topLeft = Offset(-9f, -4.5f),
                        size = Size(18f, 9f)
                    )
                    drawLine(
                        color = Color(0xFF3E2723).copy(alpha = 0.65f),
                        start = Offset(-9f, 0f),
                        end = Offset(8f, 0f),
                        strokeWidth = 1.5f
                    )
                }
            }
        }
        ExternalEventType.EARTHQUAKE -> {
            drawRect(color = Color(0xFF5D4037).copy(alpha = 0.10f))
        }
        ExternalEventType.NONE -> Unit
    }
}

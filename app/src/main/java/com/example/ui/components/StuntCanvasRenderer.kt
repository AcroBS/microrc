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
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.example.data.TrackCatalog
import com.example.model.StuntGolfHole
import com.example.model.StuntStateSnapshot
import com.example.model.StuntVariant
import com.example.model.TrackDefinition
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val stuntFieldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    textSize = 20f
    textAlign = Paint.Align.CENTER
    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
}

private val previewGolfHoles: List<StuntGolfHole> by lazy {
    TrackCatalog.generateGolfHoles(Random(42L))
}

fun DrawScope.drawStuntWorldLayer(
    track: TrackDefinition,
    stuntState: StuntStateSnapshot?,
    animTimeSec: Float
) {
    val variant = stuntState?.variantEnum ?: track.stuntVariant ?: StuntVariant.LONG_JUMP

    // 1. Spectator Paddock Box (top-left viewing deck where non-active racers watch)
    drawRoundRect(
        color = Color(0xFF101622).copy(alpha = 0.90f),
        topLeft = Offset(190f, 165f),
        size = Size(520f, 140f),
        cornerRadius = CornerRadius(20f, 20f)
    )
    drawRoundRect(
        color = Color(0xFF00E5FF).copy(alpha = 0.55f),
        topLeft = Offset(190f, 165f),
        size = Size(520f, 140f),
        cornerRadius = CornerRadius(20f, 20f),
        style = Stroke(
            width = 3.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(22f, 14f), 0f)
        )
    )
    for (slot in 0 until 3) {
        val sx = 255f + slot * 145f
        drawRoundRect(
            color = Color.White.copy(alpha = 0.08f),
            topLeft = Offset(sx - 44f, 205f),
            size = Size(88f, 60f),
            cornerRadius = CornerRadius(10f, 10f),
            style = Stroke(width = 2f)
        )
    }
    stuntFieldPaint.textSize = 18f
    stuntFieldPaint.color = Color(0xFF80D8FF).toArgb()
    drawContext.canvas.nativeCanvas.drawText("SPECTATOR PADDOCK", 450f, 192f, stuntFieldPaint)

    // 2. Shared Approach Runway (x = 145..1390, y = 545..855)
    val rLeft = 145f
    val rTop = 545f
    val rWidth = 1245f
    val rHeight = 310f

    // Outer safety guardrail + alternating kerbs
    drawRoundRect(
        color = Color(0xFF0E131B),
        topLeft = Offset(rLeft - 14f, rTop - 14f),
        size = Size(rWidth + 28f, rHeight + 28f),
        cornerRadius = CornerRadius(24f, 24f)
    )
    drawRoundRect(
        color = Color(track.id.kerbSecondaryHex),
        topLeft = Offset(rLeft - 8f, rTop - 8f),
        size = Size(rWidth + 16f, rHeight + 16f),
        cornerRadius = CornerRadius(20f, 20f)
    )
    drawRoundRect(
        color = Color(track.id.kerbPrimaryHex),
        topLeft = Offset(rLeft - 8f, rTop - 8f),
        size = Size(rWidth + 16f, rHeight + 16f),
        cornerRadius = CornerRadius(20f, 20f),
        style = Stroke(
            width = 16f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(34f, 34f), 0f)
        )
    )
    // Asphalt runway surface
    drawRoundRect(
        color = Color(track.id.roadColorHex),
        topLeft = Offset(rLeft, rTop),
        size = Size(rWidth, rHeight),
        cornerRadius = CornerRadius(16f, 16f)
    )

    // Runway centerline & slalom weave guide line
    drawLine(
        color = Color.White.copy(alpha = 0.14f),
        start = Offset(250f, 700f),
        end = Offset(1320f, 700f),
        strokeWidth = 4f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(26f, 26f), 0f)
    )

    // Start Gate Checkered Line at x = 230f
    val cells = 10
    val cellSize = rHeight / cells
    for (row in 0 until 2) {
        for (col in 0 until cells) {
            val isWhite = (row + col) % 2 == 0
            drawRect(
                color = if (isWhite) Color.White else Color(0xFF11141B),
                topLeft = Offset(230f + row * 16f, rTop + col * cellSize),
                size = Size(16f, cellSize)
            )
        }
    }

    // 3. Expanded Stunt Landing Stadium (x = 1400..3120, y = 240..1160) with bouncy rear barrier
    val sLeft = 1400f
    val sTop = 240f
    val sWidth = 1720f
    val sHeight = 920f

    // Stadium outer barrier
    drawRoundRect(
        color = Color(0xFF0E131B),
        topLeft = Offset(sLeft - 10f, sTop - 10f),
        size = Size(sWidth + 20f, sHeight + 20f),
        cornerRadius = CornerRadius(28f, 28f)
    )

    when (variant) {
        StuntVariant.LONG_JUMP -> drawLongJumpStadium(sLeft, sTop, sWidth, sHeight, stuntState, animTimeSec)
        StuntVariant.SOCCER_BALL -> drawSoccerGoalStadium(sLeft, sTop, sWidth, sHeight, stuntState, animTimeSec)
        StuntVariant.BOWLING -> drawBowlingStadium(sLeft, sTop, sWidth, sHeight, stuntState)
        StuntVariant.GOLF_HOLES -> drawGolfStadium(sLeft, sTop, sWidth, sHeight, stuntState, animTimeSec)
    }

    // Stadium perimeter neon rim + high-visibility bouncy rear bumper strip at x = 3120
    drawRoundRect(
        color = Color(track.id.kerbPrimaryHex).copy(alpha = 0.8f),
        topLeft = Offset(sLeft, sTop),
        size = Size(sWidth, sHeight),
        cornerRadius = CornerRadius(24f, 24f),
        style = Stroke(width = 6f)
    )
    drawLine(
        color = Color(0xFFFFAB00),
        start = Offset(sLeft + sWidth - 4f, sTop + 26f),
        end = Offset(sLeft + sWidth - 4f, sTop + sHeight - 26f),
        strokeWidth = 9f,
        cap = StrokeCap.Round,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(26f, 18f), 0f)
    )

    // 4. Wide Mega Launch Ramp spanning runway exit (x = 1300..1396, y = 556..844)
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.55f),
        topLeft = Offset(1312f, 562f),
        size = Size(96f, 286f),
        cornerRadius = CornerRadius(12f, 12f)
    )
    drawRoundRect(
        color = Color(0xFF3E2723),
        topLeft = Offset(1296f, 556f),
        size = Size(50f, 288f),
        cornerRadius = CornerRadius(10f, 10f)
    )
    drawRoundRect(
        color = Color(0xFF6D4C41),
        topLeft = Offset(1342f, 556f),
        size = Size(52f, 288f),
        cornerRadius = CornerRadius(10f, 10f)
    )
    // Bright launch lip
    drawRect(
        color = Color(0xFFFFAB00),
        topLeft = Offset(1380f, 556f),
        size = Size(16f, 288f)
    )
    // Animated launch chevrons along the wide ramp
    val pulse = 0.5f + 0.5f * sin(animTimeSec * 10f)
    for (row in -2..2) {
        val cy = 700f + row * 50f
        for (col in 0..1) {
            val cx = 1322f + col * 34f
            val chev = Path().apply {
                moveTo(cx - 8f, cy - 15f)
                lineTo(cx + 10f, cy)
                lineTo(cx - 8f, cy + 15f)
            }
            drawPath(
                path = chev,
                color = Color(0xFFFFEA00).copy(alpha = 0.6f + 0.4f * pulse),
                style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}

private fun DrawScope.drawLongJumpStadium(
    sLeft: Float,
    sTop: Float,
    sWidth: Float,
    sHeight: Float,
    stuntState: StuntStateSnapshot?,
    animTimeSec: Float
) {
    // High-contrast cyber sand pit
    drawRoundRect(
        color = Color(0xFF162032),
        topLeft = Offset(sLeft, sTop),
        size = Size(sWidth, sHeight),
        cornerRadius = CornerRadius(24f, 24f)
    )
    // Central landing sand strip
    drawRoundRect(
        color = Color(0xFF26354D),
        topLeft = Offset(sLeft + 10f, 430f),
        size = Size(sWidth - 24f, 540f),
        cornerRadius = CornerRadius(18f, 18f)
    )

    // Distance meter lines every 10m (9.5 px per meter starting from x = 1400f up to 170m)
    stuntFieldPaint.textSize = 20f
    for (meters in 10..170 step 10) {
        val mx = 1400f + meters * 9.5f
        if (mx > sLeft + sWidth - 18f) break
        val isMajor = meters % 20 == 0
        val lineColor = when {
            meters >= 100 -> Color(0xFFFFD54F)
            meters >= 60 -> Color(0xFF00E5FF)
            else -> Color.White.copy(alpha = 0.45f)
        }
        drawLine(
            color = lineColor.copy(alpha = if (isMajor) 0.75f else 0.35f),
            start = Offset(mx, 435f),
            end = Offset(mx, 965f),
            strokeWidth = if (isMajor) 3.5f else 2f,
            pathEffect = if (isMajor) null else PathEffect.dashPathEffect(floatArrayOf(14f, 12f), 0f)
        )
        if (isMajor) {
            stuntFieldPaint.color = lineColor.toArgb()
            drawContext.canvas.nativeCanvas.drawText("${meters}m", mx, 415f, stuntFieldPaint)
            drawContext.canvas.nativeCanvas.drawText("${meters}m", mx, 995f, stuntFieldPaint)
        }
    }

    // Best jump line marker
    val bestMeters = stuntState?.bestDistanceMeters ?: 0f
    if (bestMeters > 1f) {
        val bx = (1400f + bestMeters * 9.5f).coerceIn(sLeft + 12f, sLeft + sWidth - 12f)
        drawLine(
            color = Color(0xFFFFD54F),
            start = Offset(bx, 390f),
            end = Offset(bx, 1010f),
            strokeWidth = 4f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 10f), animTimeSec * 30f)
        )
        stuntFieldPaint.color = Color(0xFFFFD54F).toArgb()
        drawContext.canvas.nativeCanvas.drawText("BEST ${String.format("%.1fm", bestMeters)}", bx, 378f, stuntFieldPaint)
    }

    // Touchdown ring when active player lands
    if (stuntState?.hasLanded == true && stuntState.landingX > 1400f) {
        val pulse = 32f + 8f * sin(animTimeSec * 9f)
        drawCircle(
            color = Color(0xFF00E5FF).copy(alpha = 0.28f),
            radius = pulse,
            center = Offset(stuntState.landingX, stuntState.landingY)
        )
        drawCircle(
            color = Color(0xFFFFD54F),
            radius = 22f,
            center = Offset(stuntState.landingX, stuntState.landingY),
            style = Stroke(width = 4f)
        )
    }
}

private fun DrawScope.drawSoccerGoalStadium(
    sLeft: Float,
    sTop: Float,
    sWidth: Float,
    sHeight: Float,
    stuntState: StuntStateSnapshot?,
    animTimeSec: Float
) {
    // Lush striped turf pitch
    drawRoundRect(
        color = Color(0xFF1E5631),
        topLeft = Offset(sLeft, sTop),
        size = Size(sWidth, sHeight),
        cornerRadius = CornerRadius(24f, 24f)
    )
    var sx = sLeft + 40f
    var idx = 0
    while (sx < sLeft + sWidth - 80f) {
        if (idx % 2 == 0) {
            drawRect(
                color = Color(0xFF276B3E).copy(alpha = 0.55f),
                topLeft = Offset(sx, sTop + 12f),
                size = Size(90f, sHeight - 24f)
            )
        }
        sx += 90f
        idx++
    }

    // Pitch penalty arc & center spot around ball spawn (1920, 700)
    drawCircle(
        color = Color.White.copy(alpha = 0.35f),
        radius = 120f,
        center = Offset(1920f, 700f),
        style = Stroke(width = 3.5f)
    )
    drawLine(
        color = Color.White.copy(alpha = 0.30f),
        start = Offset(1920f, sTop + 20f),
        end = Offset(1920f, sTop + sHeight - 20f),
        strokeWidth = 3f
    )

    // Goal Scoring Zone at x = 2780..2948, y = 340..1060 (center highest 1000 -> sides 750 -> 500 -> 250)
    val goalX = 2780f
    val goalW = 168f
    val zones = listOf(
        Triple(340f, 1060f, Color(0xFFFF6D00) to "250"),
        Triple(445f, 955f, Color(0xFFB2FF59) to "500"),
        Triple(545f, 855f, Color(0xFF00E5FF) to "750"),
        Triple(632f, 768f, Color(0xFFFFD54F) to "1000")
    )
    for ((yMin, yMax, colorAndPts) in zones) {
        val (zColor, _) = colorAndPts
        drawRect(
            color = zColor.copy(alpha = 0.34f),
            topLeft = Offset(goalX, yMin),
            size = Size(goalW, yMax - yMin)
        )
        drawRect(
            color = zColor.copy(alpha = 0.90f),
            topLeft = Offset(goalX, yMin),
            size = Size(goalW, yMax - yMin),
            style = Stroke(width = 3f)
        )
    }

    // Goal net mesh lines
    var gx = goalX + 24f
    while (gx < goalX + goalW) {
        drawLine(Color.White.copy(alpha = 0.18f), Offset(gx, 340f), Offset(gx, 1060f), 1.6f)
        gx += 24f
    }
    var gy = 364f
    while (gy < 1060f) {
        drawLine(Color.White.copy(alpha = 0.18f), Offset(goalX, gy), Offset(goalX + goalW, gy), 1.6f)
        gy += 24f
    }

    // Goal posts & Goal Line
    drawLine(Color.White, Offset(goalX, 340f), Offset(goalX, 1060f), strokeWidth = 6f)
    drawCircle(Color.White, radius = 10f, center = Offset(goalX, 340f))
    drawCircle(Color.White, radius = 10f, center = Offset(goalX, 1060f))

    // Score tier labels inside goal bands
    stuntFieldPaint.textSize = 21f
    val labelSpecs = listOf(
        Triple(705f, "1000", Color(0xFFFFD54F)),
        Triple(598f, "750", Color(0xFF00E5FF)),
        Triple(812f, "750", Color(0xFF00E5FF)),
        Triple(502f, "500", Color(0xFFB2FF59)),
        Triple(908f, "500", Color(0xFFB2FF59)),
        Triple(398f, "250", Color(0xFFFFAB40)),
        Triple(1012f, "250", Color(0xFFFFAB40))
    )
    for ((ly, txt, col) in labelSpecs) {
        stuntFieldPaint.color = col.toArgb()
        drawContext.canvas.nativeCanvas.drawText(txt, goalX + goalW * 0.5f, ly, stuntFieldPaint)
    }

    // Giant 2.5D Soccer Ball (radius = 40f)
    val bx = stuntState?.ballX ?: 1920f
    val by = stuntState?.ballY ?: 700f
    val r = 40f
    drawCircle(Color.Black.copy(alpha = 0.42f), radius = r * 0.96f, center = Offset(bx + 7f, by + 9f))
    drawCircle(Color(0xFFF5F7FA), radius = r, center = Offset(bx, by))
    drawCircle(Color(0xFF1A222E), radius = r, center = Offset(bx, by), style = Stroke(width = 3.5f))

    // Pentagon panel pattern rotating with ball position
    val spin = ((bx + by) * 0.02f) + animTimeSec * 0.6f
    drawCircle(Color(0xFF1A222E), radius = 12f, center = Offset(bx, by))
    for (p in 0 until 5) {
        val ang = spin + p * (2f * PI.toFloat() / 5f)
        val px = bx + cos(ang) * 26f
        val py = by + sin(ang) * 26f
        drawCircle(Color(0xFF1A222E), radius = 8.5f, center = Offset(px, py))
        drawLine(Color(0xFF1A222E), Offset(bx, by), Offset(px, py), strokeWidth = 2.6f)
    }
}

private fun DrawScope.drawBowlingStadium(
    sLeft: Float,
    sTop: Float,
    sWidth: Float,
    sHeight: Float,
    stuntState: StuntStateSnapshot?
) {
    // Outer arena gutter floor
    drawRoundRect(
        color = Color(0xFF181D26),
        topLeft = Offset(sLeft, sTop),
        size = Size(sWidth, sHeight),
        cornerRadius = CornerRadius(24f, 24f)
    )

    // Polished maple hardwood bowling lane (y = 470..930)
    val laneTop = 470f
    val laneH = 460f
    drawRoundRect(
        color = Color(0xFFC98A4B),
        topLeft = Offset(sLeft + 16f, laneTop),
        size = Size(sWidth - 32f, laneH),
        cornerRadius = CornerRadius(16f, 16f)
    )
    // Hardwood plank lines
    var py = laneTop + 28f
    while (py < laneTop + laneH) {
        drawLine(
            color = Color(0xFF8D5524).copy(alpha = 0.34f),
            start = Offset(sLeft + 20f, py),
            end = Offset(sLeft + sWidth - 20f, py),
            strokeWidth = 2f
        )
        py += 28f
    }
    // Lane aim arrows
    for (row in -2..2) {
        val ay = 700f + row * 68f
        val ax = 1620f + kotlin.math.abs(row) * 26f
        val chev = Path().apply {
            moveTo(ax - 10f, ay - 10f)
            lineTo(ax + 12f, ay)
            lineTo(ax - 10f, ay + 10f)
        }
        drawPath(chev, Color(0xFF5D2E0C).copy(alpha = 0.7f), style = Stroke(width = 4f, cap = StrokeCap.Round))
    }

    // 9-Pin Bowling Rack (standing vs knocked)
    val pins = stuntState?.pins?.ifEmpty { null } ?: TrackCatalog.defaultBowlingPins()
    for (pin in pins) {
        if (pin.knocked) {
            // Knocked pin lying on its side with red neck stripes
            withTransform({
                translate(pin.x, pin.y)
                rotate(degrees = 38f + (pin.id * 29f) % 140f, pivot = Offset.Zero)
            }) {
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.36f),
                    topLeft = Offset(-20f, -6f),
                    size = Size(42f, 18f),
                    cornerRadius = CornerRadius(8f, 8f)
                )
                drawRoundRect(
                    color = Color(0xFFFDFDFD),
                    topLeft = Offset(-22f, -9f),
                    size = Size(44f, 18f),
                    cornerRadius = CornerRadius(9f, 9f)
                )
                drawRect(Color(0xFFE53935), topLeft = Offset(4f, -8f), size = Size(5f, 16f))
                drawRect(Color(0xFFE53935), topLeft = Offset(11f, -7f), size = Size(4f, 14f))
            }
        } else {
            // Standing bowling pin (top-down crown + red collar ring + shadow)
            drawCircle(Color.Black.copy(alpha = 0.42f), radius = 21f, center = Offset(pin.x + 5f, pin.y + 6f))
            drawCircle(Color(0xFFF5F5F5), radius = 21f, center = Offset(pin.x, pin.y))
            drawCircle(Color(0xFFE53935), radius = 14f, center = Offset(pin.x, pin.y), style = Stroke(width = 4.2f))
            drawCircle(Color.White, radius = 8f, center = Offset(pin.x - 2f, pin.y - 2f))
        }
    }
}

private fun DrawScope.drawGolfStadium(
    sLeft: Float,
    sTop: Float,
    sWidth: Float,
    sHeight: Float,
    stuntState: StuntStateSnapshot?,
    animTimeSec: Float
) {
    // Fairway & putting green contours
    drawRoundRect(
        color = Color(0xFF1F4E28),
        topLeft = Offset(sLeft, sTop),
        size = Size(sWidth, sHeight),
        cornerRadius = CornerRadius(24f, 24f)
    )
    drawOval(
        color = Color(0xFF2E7D32),
        topLeft = Offset(sLeft + 70f, sTop + 45f),
        size = Size(sWidth - 130f, sHeight - 90f)
    )
    // Decorative sand bunkers in corners
    drawOval(Color(0xFFE6C280).copy(alpha = 0.82f), topLeft = Offset(1470f, 280f), size = Size(185f, 105f))
    drawOval(Color(0xFFE6C280).copy(alpha = 0.82f), topLeft = Offset(2860f, 985f), size = Size(185f, 110f))

    val holes = stuntState?.golfHoles?.ifEmpty { null } ?: previewGolfHoles
    for (hole in holes) {
        val rimColor = when {
            hole.captured -> Color(0xFFFFD54F)
            hole.points >= 800 -> Color(0xFFFFD54F)
            hole.points >= 550 -> Color(0xFF00E5FF)
            else -> Color(0xFFB2FF59)
        }
        if (hole.captured) {
            val glow = hole.radius + 14f + 6f * sin(animTimeSec * 10f)
            drawCircle(Color(0xFFFFD54F).copy(alpha = 0.34f), radius = glow, center = Offset(hole.x, hole.y))
        }
        // Cup outer collar + deep dark golf hole
        drawCircle(rimColor.copy(alpha = 0.30f), radius = hole.radius + 6f, center = Offset(hole.x, hole.y))
        drawCircle(Color(0xFF090D12), radius = hole.radius, center = Offset(hole.x, hole.y))
        drawCircle(rimColor, radius = hole.radius, center = Offset(hole.x, hole.y), style = Stroke(width = 3.5f))

        // Flagstick & numbered pennant
        drawLine(
            color = Color.White,
            start = Offset(hole.x, hole.y),
            end = Offset(hole.x, hole.y - hole.radius - 26f),
            strokeWidth = 3f,
            cap = StrokeCap.Round
        )
        val flagTopY = hole.y - hole.radius - 26f
        val pennant = Path().apply {
            moveTo(hole.x + 1f, flagTopY)
            lineTo(hole.x + 30f, flagTopY + 10f)
            lineTo(hole.x + 1f, flagTopY + 20f)
            close()
        }
        drawPath(pennant, rimColor)

        // Hole number & score value inside/under cup
        stuntFieldPaint.textSize = 18f
        stuntFieldPaint.color = Color.White.toArgb()
        drawContext.canvas.nativeCanvas.drawText("#${hole.number}", hole.x, hole.y - 2f, stuntFieldPaint)
        stuntFieldPaint.textSize = 15f
        stuntFieldPaint.color = rimColor.toArgb()
        drawContext.canvas.nativeCanvas.drawText("${hole.points}", hole.x, hole.y + 16f, stuntFieldPaint)
    }
}

fun DrawScope.drawStuntMiniMap(
    track: TrackDefinition,
    offsetX: Float,
    offsetY: Float,
    scale: Float,
    stuntState: StuntStateSnapshot? = null
) {
    val variant = stuntState?.variantEnum ?: track.stuntVariant ?: StuntVariant.LONG_JUMP

    // Shared Runway
    drawRoundRect(
        color = Color(track.id.roadColorHex),
        topLeft = Offset(offsetX + 150f * scale, offsetY + 545f * scale),
        size = Size(1240f * scale, 310f * scale),
        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
    )
    drawRoundRect(
        color = Color(track.id.kerbPrimaryHex),
        topLeft = Offset(offsetX + 150f * scale, offsetY + 545f * scale),
        size = Size(1240f * scale, 310f * scale),
        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
        style = Stroke(width = 1.6.dp.toPx())
    )

    // Mega Ramp highlight
    drawRect(
        color = Color(0xFFFFAB00),
        topLeft = Offset(offsetX + 1315f * scale, offsetY + 555f * scale),
        size = Size(78f * scale, 290f * scale)
    )

    // Landing Stadium
    val stadiumColor = when (variant) {
        StuntVariant.LONG_JUMP -> Color(0xFF1E2D45)
        StuntVariant.SOCCER_BALL -> Color(0xFF1E5631)
        StuntVariant.BOWLING -> Color(0xFF7A5230)
        StuntVariant.GOLF_HOLES -> Color(0xFF2E7D32)
    }
    drawRoundRect(
        color = stadiumColor,
        topLeft = Offset(offsetX + 1400f * scale, offsetY + 240f * scale),
        size = Size(1720f * scale, 920f * scale),
        cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
    )
    drawRoundRect(
        color = Color(track.id.kerbPrimaryHex),
        topLeft = Offset(offsetX + 1400f * scale, offsetY + 240f * scale),
        size = Size(1720f * scale, 920f * scale),
        cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx()),
        style = Stroke(width = 1.8.dp.toPx())
    )

    when (variant) {
        StuntVariant.SOCCER_BALL -> {
            val bx = stuntState?.ballX ?: 1920f
            val by = stuntState?.ballY ?: 700f
            drawCircle(Color.White, radius = 3.2.dp.toPx(), center = Offset(offsetX + bx * scale, offsetY + by * scale))
            drawRect(
                color = Color(0xFFFFD54F),
                topLeft = Offset(offsetX + 2780f * scale, offsetY + 340f * scale),
                size = Size(165f * scale, 720f * scale),
                style = Stroke(width = 1.5.dp.toPx())
            )
        }
        StuntVariant.BOWLING -> {
            val pins = stuntState?.pins?.ifEmpty { null } ?: TrackCatalog.defaultBowlingPins()
            for (p in pins) {
                if (!p.knocked) {
                    drawCircle(
                        color = Color.White,
                        radius = 1.6.dp.toPx(),
                        center = Offset(offsetX + p.x * scale, offsetY + p.y * scale)
                    )
                }
            }
        }
        StuntVariant.GOLF_HOLES -> {
            val holes = stuntState?.golfHoles?.ifEmpty { null } ?: previewGolfHoles
            for (h in holes) {
                drawCircle(
                    color = Color(0xFFFFD54F),
                    radius = (h.radius * scale).coerceAtLeast(1.8.dp.toPx()),
                    center = Offset(offsetX + h.x * scale, offsetY + h.y * scale),
                    style = Stroke(width = 1.2.dp.toPx())
                )
            }
        }
        StuntVariant.LONG_JUMP -> {
            for (m in 30..150 step 30) {
                val lx = offsetX + (1400f + m * 9.5f) * scale
                drawLine(
                    color = Color(0xFF00E5FF).copy(alpha = 0.5f),
                    start = Offset(lx, offsetY + 420f * scale),
                    end = Offset(lx, offsetY + 980f * scale),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }
    }
}

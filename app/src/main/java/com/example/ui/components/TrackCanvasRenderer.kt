package com.example.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint as ComposePaint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.example.data.TrackCatalog
import com.example.model.ActiveGhostCarState
import com.example.model.ActiveRocketState
import com.example.model.ArenaFlagState
import com.example.model.CarVisualUpgrades
import com.example.model.ExternalEventType
import com.example.model.FieldPickupType
import com.example.model.HazardType
import com.example.model.ItemBoxState
import com.example.model.MeteorStrikeState
import com.example.model.PortalPairState
import com.example.model.PropShape
import com.example.model.RacerState
import com.example.model.RcCarType
import com.example.model.StuntStateSnapshot
import com.example.model.TrackCrater
import com.example.model.TrackDefinition
import com.example.model.TrackExplosion
import com.example.model.TrackFieldPickup
import com.example.model.TrackHazard
import com.example.model.TrackId
import com.example.model.TrackPoint
import com.example.model.TrackProp
import com.example.model.TrackPuddle
import com.example.model.TrackSnowPatch
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private val racerNamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    textSize = 15f
    textAlign = Paint.Align.CENTER
    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
}

private val nightLayerPaint = ComposePaint()

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
    arenaFlag: ArenaFlagState? = null,
    fieldPickups: List<TrackFieldPickup> = emptyList(),
    portalPair: PortalPairState? = null,
    explosions: List<TrackExplosion> = emptyList(),
    arenaSuddenDeathStage: Int = 0,
    stuntState: StuntStateSnapshot? = null,
    rockets: List<ActiveRocketState> = emptyList(),
    ghostCar: ActiveGhostCarState? = null,
    isDriftMode: Boolean = false,
    nightIntensity: Float = if (track.isNightTrack) 1f else 0f,
    snowPatches: List<TrackSnowPatch> = emptyList(),
    meteorStrikes: List<MeteorStrikeState> = emptyList(),
    scorchedCraters: List<TrackCrater> = emptyList()
) {
    // 1. Background floor
    drawRect(
        color = Color(track.id.baseBgColorHex),
        topLeft = Offset.Zero,
        size = Size(track.worldWidth, track.worldHeight)
    )

    // 2. Subtle themed background texture
    drawEnvironmentDetails(track)

    // 3. Render Stunt Stadium, Open Arena Floor, or Closed Circuit Road
    val pts = if (track.isArena) track.arenaBoundary.ifEmpty { track.centerline } else track.centerline
    if (pts.isEmpty()) return

    val roadPath = Path().apply {
        moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) {
            lineTo(pts[i].x, pts[i].y)
        }
        if (!track.isStunt) {
            close()
        }
    }

    if (track.isStunt) {
        drawStuntWorldLayer(track = track, stuntState = stuntState, animTimeSec = animTimeSec)
    } else if (track.isArena) {
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

        // Sudden Death Shrinking Arena collapsed danger zone & inward barrier rings
        if (arenaSuddenDeathStage > 0) {
            val activePts = TrackCatalog.scaledArenaPolygon(track, arenaSuddenDeathStage)
            if (activePts.size >= 3) {
                val activeRingPath = Path().apply {
                    moveTo(activePts[0].x, activePts[0].y)
                    for (i in 1 until activePts.size) {
                        lineTo(activePts[i].x, activePts[i].y)
                    }
                    close()
                }
                val collapsedZonePath = Path.combine(PathOperation.Difference, roadPath, activeRingPath)
                val dangerPulse = 0.5f + 0.5f * sin(animTimeSec * 9f)
                drawPath(
                    path = collapsedZonePath,
                    color = Color(0xFFD50000).copy(alpha = 0.26f + 0.14f * dangerPulse)
                )

                // Ghost outlines for previous inward waves so each 10s step is visible
                for (prevStage in 1 until arenaSuddenDeathStage) {
                    val prevPts = TrackCatalog.scaledArenaPolygon(track, prevStage)
                    if (prevPts.size >= 3) {
                        val prevPath = Path().apply {
                            moveTo(prevPts[0].x, prevPts[0].y)
                            for (i in 1 until prevPts.size) {
                                lineTo(prevPts[i].x, prevPts[i].y)
                            }
                            close()
                        }
                        drawPath(
                            path = prevPath,
                            color = Color(0xFFFF5252).copy(alpha = 0.42f),
                            style = Stroke(
                                width = 6f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(22f, 18f), 0f)
                            )
                        )
                    }
                }

                // Active Sudden Death hazard perimeter guardrail
                drawPath(
                    path = activeRingPath,
                    color = Color(0xFF141820),
                    style = Stroke(width = 22f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                drawPath(
                    path = activeRingPath,
                    color = Color(0xFFFF1744).copy(alpha = 0.85f + 0.15f * dangerPulse),
                    style = Stroke(width = 16f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                drawPath(
                    path = activeRingPath,
                    color = Color(0xFFFFEA00),
                    style = Stroke(
                        width = 14f,
                        cap = StrokeCap.Butt,
                        join = StrokeJoin.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(28f, 28f), animTimeSec * 56f)
                    )
                )
            }
        }
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

    // Prepare combined track entities and layer helpers for ground (zLayer=0) vs overpass (zLayer=1)
    val hasOverpasses = !track.isArena && !track.isStunt && pts.any { it.elevation > 0.05f || it.zLayer > 0 }
    val orderedRacers = racers.sortedBy { it.jumpZ }
    val allGravelPatches = if (!track.isStunt && track.ramps.isNotEmpty()) {
        TrackCatalog.gravelPatchesForTrack(track)
    } else {
        emptyList()
    }
    val allPuddles = if (track.puddles.isEmpty()) {
        rainPuddles
    } else if (rainPuddles.isEmpty()) {
        track.puddles
    } else {
        track.puddles + rainPuddles
    }
    val propsToRender = if (track.isStunt && stuntState != null && stuntState.runwayObstacles.isNotEmpty()) {
        stuntState.runwayObstacles.mapIndexed { idx, ob ->
            TrackProp(
                x = ob.x,
                y = ob.y,
                radius = ob.radius,
                shape = if (ob.isCrate) PropShape.CRATE else PropShape.PYLON,
                colorHex = if (ob.isCrate) 0xFF455A64 else (if (idx % 2 == 0) 0xFFFF6D00 else 0xFF00E5FF),
                accentHex = if (ob.isCrate) 0xFFFFAB00 else 0xFFFFFFFF,
                isSolidObstacle = true
            )
        }
    } else {
        track.props
    }
    fun resolveSplineZLayer(wx: Float, wy: Float): Int {
        if (!hasOverpasses || pts.isEmpty()) return 0
        val (idx, _) = TrackCatalog.nearestTrackPoint(track, wx, wy)
        return pts.getOrNull(idx)?.zLayer ?: 0
    }
    val portalEntryLayer = portalPair?.let { resolveSplineZLayer(it.entryX, it.entryY) } ?: 0
    val portalExitLayer = portalPair?.let { resolveSplineZLayer(it.exitX, it.exitY) } ?: 0
    val ghostLayer = ghostCar?.let { resolveSplineZLayer(it.x, it.y) } ?: 0

    val layersToRender = if (hasOverpasses) listOf(0, 1) else listOf(0)
    for (currentLayer in layersToRender) {
        if (currentLayer == 1) {
            drawOverpassBridgeSpans(
                pts = pts,
                track = track,
                hardBoundary = hardBoundary,
                activeEvent = activeEvent
            )
        }

        // 4b. Spline-following full-width Gravel Traps
        if (allGravelPatches.isNotEmpty()) {
            for ((patchIdx, patch) in allGravelPatches.withIndex()) {
                if (hasOverpasses && patch.zLayer != currentLayer) continue
                val left = patch.leftEdge
                val right = patch.rightEdge
                if (left.size < 2 || left.size != right.size) continue

                val outerRibbon = Path().apply {
                    moveTo(left[0].first, left[0].second)
                    for (i in 1 until left.size) {
                        lineTo(left[i].first, left[i].second)
                    }
                    for (i in right.lastIndex downTo 0) {
                        lineTo(right[i].first, right[i].second)
                    }
                    close()
                }

                val innerRibbon = Path().apply {
                    val insetFrac = 0.08f
                    val startIdx = if (left.size > 3) 1 else 0
                    val endIdx = if (left.size > 3) left.lastIndex - 1 else left.lastIndex
                    for (i in startIdx..endIdx) {
                        val ix = left[i].first + (right[i].first - left[i].first) * insetFrac
                        val iy = left[i].second + (right[i].second - left[i].second) * insetFrac
                        if (i == startIdx) moveTo(ix, iy) else lineTo(ix, iy)
                    }
                    for (i in endIdx downTo startIdx) {
                        val ix = right[i].first + (left[i].first - right[i].first) * insetFrac
                        val iy = right[i].second + (left[i].second - right[i].second) * insetFrac
                        lineTo(ix, iy)
                    }
                    close()
                }

                drawPath(
                    path = outerRibbon,
                    color = Color(0xFFB5966A).copy(alpha = 0.90f)
                )
                drawPath(
                    path = innerRibbon,
                    color = Color(0xFF9A7B52).copy(alpha = 0.54f)
                )

                val segCount = (left.size - 1).coerceAtLeast(1)
                for (pIdx in 0 until 36) {
                    val hashA = 0.06f + 0.88f * (0.5f + 0.5f * kotlin.math.sin((patchIdx * 37 + pIdx * 19 + 3).toFloat()))
                    val hashB = 0.08f + 0.84f * (0.5f + 0.5f * kotlin.math.cos((patchIdx * 23 + pIdx * 31 + 7).toFloat()))
                    val fIdx = hashA * segCount
                    val seg = fIdx.toInt().coerceIn(0, segCount - 1)
                    val segFrac = (fIdx - seg).coerceIn(0f, 1f)
                    val lx = left[seg].first + (left[seg + 1].first - left[seg].first) * segFrac
                    val ly = left[seg].second + (left[seg + 1].second - left[seg].second) * segFrac
                    val rx = right[seg].first + (right[seg + 1].first - right[seg].first) * segFrac
                    val ry = right[seg].second + (right[seg + 1].second - right[seg].second) * segFrac
                    val px = lx + (rx - lx) * hashB
                    val py = ly + (ry - ly) * hashB
                    val pebbleR = 2.4f + (pIdx % 3) * 1.1f
                    val pebbleColor = when (pIdx % 3) {
                        0 -> Color(0xFFDCC8A6).copy(alpha = 0.85f)
                        1 -> Color(0xFF6B5237).copy(alpha = 0.72f)
                        else -> Color(0xFF8C6E4A).copy(alpha = 0.80f)
                    }
                    drawCircle(
                        color = pebbleColor,
                        radius = pebbleR,
                        center = Offset(px, py)
                    )
                }

                drawPath(
                    path = outerRibbon,
                    color = Color(0xFF5D4037).copy(alpha = 0.65f),
                    style = Stroke(
                        width = 3.2f,
                        join = StrokeJoin.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                    )
                )
            }
        }

        // Rain puddles on road surface (permanent track puddles + active rainstorm puddles)
        for (i in allPuddles.indices) {
            val p = allPuddles[i]
            if (hasOverpasses && p.zLayer != currentLayer) continue
            val rx = p.radius
            val ry = p.radius * 0.78f
            drawOval(
                color = Color(0xFF01579B).copy(alpha = 0.62f),
                topLeft = Offset(p.x - rx, p.y - ry),
                size = Size(rx * 2f, ry * 2f)
            )
            drawOval(
                color = Color(0xFF4FC3F7).copy(alpha = 0.46f),
                topLeft = Offset(p.x - rx * 0.72f, p.y - ry * 0.68f),
                size = Size(rx * 1.44f, ry * 1.36f)
            )
            val edgePulse = 0.55f + 0.45f * sin(animTimeSec * 3.4f + i * 1.1f)
            drawOval(
                color = Color(0xFF81D4FA).copy(alpha = 0.52f + 0.30f * edgePulse),
                topLeft = Offset(p.x - rx, p.y - ry),
                size = Size(rx * 2f, ry * 2f),
                style = Stroke(width = 2.6f)
            )
            val ripplePhase1 = ((animTimeSec * 1.55f + i * 0.37f) % 1f)
            val ripplePhase2 = ((animTimeSec * 1.55f + i * 0.37f + 0.5f) % 1f)
            drawOval(
                color = Color(0xFFE1F5FE).copy(alpha = (1f - ripplePhase1) * 0.78f),
                topLeft = Offset(p.x - rx * (0.26f + 0.72f * ripplePhase1), p.y - ry * (0.26f + 0.72f * ripplePhase1)),
                size = Size(rx * 2f * (0.26f + 0.72f * ripplePhase1), ry * 2f * (0.26f + 0.72f * ripplePhase1)),
                style = Stroke(width = 2.4f)
            )
            drawOval(
                color = Color(0xFFB3E5FC).copy(alpha = (1f - ripplePhase2) * 0.58f),
                topLeft = Offset(p.x - rx * (0.22f + 0.70f * ripplePhase2), p.y - ry * (0.22f + 0.70f * ripplePhase2)),
                size = Size(rx * 2f * (0.22f + 0.70f * ripplePhase2), ry * 2f * (0.22f + 0.70f * ripplePhase2)),
                style = Stroke(width = 1.8f)
            )
        }

        // Daily Snow corner patches & Multiplayer Meteor Crater scorched ground
        val layerSnowPatches = if (hasOverpasses) snowPatches.filter { it.zLayer == currentLayer } else snowPatches
        if (layerSnowPatches.isNotEmpty()) {
            drawSnowPatches(layerSnowPatches)
        }
        val layerCraters = if (hasOverpasses) scorchedCraters.filter { it.zLayer == currentLayer } else scorchedCraters
        if (layerCraters.isNotEmpty()) {
            drawScorchedCraters(layerCraters)
        }

        // 5. Boost pads
        for (pad in track.boostPads) {
            if (hasOverpasses && pad.zLayer != currentLayer) continue
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

        // 5b. Narrow Jump Ramps centered on the racing line
        if (!track.isStunt) {
            val rampHalfLen = TrackCatalog.RAMP_HALF_LENGTH
            val rampHalfW = TrackCatalog.RAMP_HALF_WIDTH
            for (ramp in track.ramps) {
                if (hasOverpasses && ramp.zLayer != currentLayer) continue
                val deg = ramp.angleRad * 180f / PI.toFloat()
                withTransform({
                    translate(ramp.x, ramp.y)
                    rotate(deg, Offset.Zero)
                }) {
                    drawRoundRect(
                        color = Color.Black.copy(alpha = 0.48f),
                        topLeft = Offset(-rampHalfLen + 10f, -rampHalfW + 3f),
                        size = Size(rampHalfLen * 2f - 4f, rampHalfW * 2f + 2f),
                        cornerRadius = CornerRadius(8f, 8f)
                    )
                    drawRoundRect(
                        color = Color(0xFF4E342E),
                        topLeft = Offset(-rampHalfLen, -rampHalfW),
                        size = Size(rampHalfLen + 2f, rampHalfW * 2f),
                        cornerRadius = CornerRadius(6f, 6f)
                    )
                    drawRoundRect(
                        color = Color(0xFF8D6E63),
                        topLeft = Offset(-2f, -rampHalfW),
                        size = Size(rampHalfLen + 2f, rampHalfW * 2f),
                        cornerRadius = CornerRadius(6f, 6f)
                    )
                    drawRect(
                        color = Color(0xFFFFAB00),
                        topLeft = Offset(rampHalfLen - 10f, -rampHalfW - 1f),
                        size = Size(11f, rampHalfW * 2f + 2f)
                    )
                    drawRect(
                        color = Color(0xFFFFEA00),
                        topLeft = Offset(-rampHalfLen, -rampHalfW - 2f),
                        size = Size(rampHalfLen * 2f, 5f)
                    )
                    drawRect(
                        color = Color(0xFFFFEA00),
                        topLeft = Offset(-rampHalfLen, rampHalfW - 3f),
                        size = Size(rampHalfLen * 2f, 5f)
                    )
                    for (c in -1..1) {
                        val cx = c * 16f - 4f
                        val chev = Path().apply {
                            moveTo(cx - 7f, -15f)
                            lineTo(cx + 8f, 0f)
                            lineTo(cx - 7f, 15f)
                        }
                        drawPath(
                            path = chev,
                            color = Color(0xFFFFEA00),
                            style = Stroke(width = 4.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                }
            }
        }

        // 6. Props & obstacles
        for (prop in propsToRender) {
            if (hasOverpasses && prop.zLayer != currentLayer) continue
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
                        drawOval(
                            Color(0xFFE040FB).copy(alpha = 0.32f),
                            Offset(prop.x - prop.radius * 0.35f, prop.y - prop.radius * 0.2f),
                            Size(prop.radius * 0.78f, prop.radius * 0.48f)
                        )
                    }
                }
            }
        }

        // 6b. Paired Red & Blue Portals
        if (portalPair != null) {
            val redMain = Color(0xFFFF1744)
            val redGlow = Color(0xFFFF8A80)
            val blueMain = Color(0xFF2979FF)
            val blueGlow = Color(0xFF80D8FF)

            val entryPrimary = if (portalPair.entryColorIsRed) redMain else blueMain
            val entryAccent = if (portalPair.entryColorIsRed) redGlow else blueGlow
            val exitPrimary = if (portalPair.entryColorIsRed) blueMain else redMain
            val exitAccent = if (portalPair.entryColorIsRed) blueGlow else redGlow

            if (!hasOverpasses || portalEntryLayer == currentLayer) {
                drawPortalHole(
                    x = portalPair.entryX,
                    y = portalPair.entryY,
                    rimColor = entryPrimary,
                    glowColor = entryAccent,
                    animTimeSec = animTimeSec,
                    isExitWithArrow = false,
                    exitAngleRad = 0f
                )
            }
            if (!hasOverpasses || portalExitLayer == currentLayer) {
                drawPortalHole(
                    x = portalPair.exitX,
                    y = portalPair.exitY,
                    rimColor = exitPrimary,
                    glowColor = exitAccent,
                    animTimeSec = animTimeSec + 0.8f,
                    isExitWithArrow = !track.isArena,
                    exitAngleRad = portalPair.exitAngleRad
                )
            }
        }

        // 7. Dropped Glue Slicks, Flashing Red Mines & Sudden Death Hazard Barriers
        for (h in hazards) {
            if (hasOverpasses && h.zLayer != currentLayer) continue
            when (h.hazardTypeEnum) {
                HazardType.BARRIER -> {
                    val strobe = 0.5f + 0.5f * sin(animTimeSec * 12f + h.id * 0.9f)
                    val r = h.radius
                    drawCircle(
                        color = Color(0xFFFF1744).copy(alpha = 0.20f + 0.20f * strobe),
                        radius = r * 1.24f,
                        center = Offset(h.x, h.y)
                    )
                    drawRoundRect(
                        color = Color.Black.copy(alpha = 0.52f),
                        topLeft = Offset(h.x - r + 4f, h.y - r + 5f),
                        size = Size(r * 2f, r * 2f),
                        cornerRadius = CornerRadius(10f, 10f)
                    )
                    drawRoundRect(
                        color = Color(0xFF1A202C),
                        topLeft = Offset(h.x - r, h.y - r),
                        size = Size(r * 2f, r * 2f),
                        cornerRadius = CornerRadius(10f, 10f)
                    )
                    drawLine(
                        color = Color(0xFFFFEA00),
                        start = Offset(h.x - r * 0.62f, h.y - r * 0.62f),
                        end = Offset(h.x + r * 0.62f, h.y + r * 0.62f),
                        strokeWidth = 5.5f,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = Color(0xFFFFEA00),
                        start = Offset(h.x + r * 0.62f, h.y - r * 0.62f),
                        end = Offset(h.x - r * 0.62f, h.y + r * 0.62f),
                        strokeWidth = 5.5f,
                        cap = StrokeCap.Round
                    )
                    drawRoundRect(
                        color = if (strobe > 0.45f) Color(0xFFFF1744) else Color(0xFFFFAB00),
                        topLeft = Offset(h.x - r, h.y - r),
                        size = Size(r * 2f, r * 2f),
                        cornerRadius = CornerRadius(10f, 10f),
                        style = Stroke(width = 3.8f)
                    )
                    drawCircle(
                        color = if (strobe > 0.45f) Color(0xFFFF1744) else Color(0xFF7F0019),
                        radius = r * 0.30f,
                        center = Offset(h.x, h.y)
                    )
                }
                HazardType.MINE -> {
                    val fastBlink = 0.5f + 0.5f * sin(animTimeSec * 16f + h.id * 1.3f)
                    val sharpFlash = fastBlink > 0.42f
                    drawCircle(
                        color = Color(0xFFFF1744).copy(alpha = 0.16f + 0.28f * fastBlink),
                        radius = h.radius * (1.15f + 0.18f * fastBlink),
                        center = Offset(h.x, h.y)
                    )
                    drawCircle(
                        color = Color(0xFFFF1744).copy(alpha = 0.72f),
                        radius = h.radius,
                        center = Offset(h.x, h.y),
                        style = Stroke(width = 2.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), animTimeSec * 24f))
                    )
                    drawCircle(Color.Black.copy(alpha = 0.5f), h.radius * 0.72f, Offset(h.x + 3f, h.y + 4f))
                    drawCircle(Color(0xFF1B222E), h.radius * 0.70f, Offset(h.x, h.y))
                    drawCircle(Color(0xFF455A64), h.radius * 0.70f, Offset(h.x, h.y), style = Stroke(width = 3f))
                    for (lug in 0 until 6) {
                        val ang = lug * (PI.toFloat() / 3f)
                        drawCircle(
                            color = Color(0xFF90A4AE),
                            radius = 2.6f,
                            center = Offset(h.x + cos(ang) * (h.radius * 0.52f), h.y + sin(ang) * (h.radius * 0.52f))
                        )
                    }
                    drawCircle(
                        color = if (sharpFlash) Color(0xFFFF1744) else Color(0xFF5D0011),
                        radius = h.radius * 0.34f,
                        center = Offset(h.x, h.y)
                    )
                    if (sharpFlash) {
                        drawCircle(
                            color = Color(0xFFFFCDD2),
                            radius = h.radius * 0.15f,
                            center = Offset(h.x - 2f, h.y - 2f)
                        )
                    }
                }
                HazardType.GLUE_SLICK -> {
                    drawCircle(Color(0xFFFFD54F).copy(alpha = 0.8f), h.radius, Offset(h.x, h.y))
                    drawCircle(Color(0xFFFF8F00), h.radius * 0.65f, Offset(h.x + 4f, h.y - 3f))
                    drawCircle(Color(0xFFFFECB3), h.radius * 0.25f, Offset(h.x - 7f, h.y + 5f))
                }
            }
        }

        // 7b. Random Direct-Trigger Track Power-Ups
        for (fp in fieldPickups) {
            if (hasOverpasses && resolveSplineZLayer(fp.x, fp.y) != currentLayer) continue
            val pulse = 1f + 0.09f * sin(animTimeSec * 6.5f + fp.id)
            withTransform({
                translate(fp.x, fp.y)
                scale(pulse, pulse, Offset.Zero)
            }) {
                when (fp.pickupTypeEnum) {
                    FieldPickupType.POSITION_SWAP -> {
                        drawCircle(Color(0xFFFFD54F).copy(alpha = 0.24f), radius = 34f, center = Offset.Zero)
                        drawCircle(Color(0xFF141B29).copy(alpha = 0.92f), radius = 25f, center = Offset.Zero)
                        drawCircle(Color(0xFFFFD54F), radius = 25f, center = Offset.Zero, style = Stroke(width = 3.2f))

                        val topArrow = Path().apply {
                            moveTo(-13f, -6f)
                            lineTo(12f, -6f)
                            moveTo(5f, -13f)
                            lineTo(13f, -6f)
                            lineTo(5f, 1f)
                        }
                        drawPath(
                            path = topArrow,
                            color = Color(0xFFFFD54F),
                            style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )

                        val botArrow = Path().apply {
                            moveTo(13f, 7f)
                            lineTo(-12f, 7f)
                            moveTo(-5f, 0f)
                            lineTo(-13f, 7f)
                            lineTo(-5f, 14f)
                        }
                        drawPath(
                            path = botArrow,
                            color = Color(0xFF00E5FF),
                            style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                    FieldPickupType.STEER_INVERT -> {
                        drawCircle(Color(0xFFE040FB).copy(alpha = 0.26f), radius = 34f, center = Offset.Zero)
                        drawCircle(Color(0xFF1A1026).copy(alpha = 0.92f), radius = 25f, center = Offset.Zero)
                        drawCircle(Color(0xFFE040FB), radius = 25f, center = Offset.Zero, style = Stroke(width = 3.2f))

                        val crossArrows = Path().apply {
                            moveTo(-11f, 9f)
                            lineTo(11f, -9f)
                            moveTo(4f, -10f)
                            lineTo(12f, -9f)
                            lineTo(10f, -1f)

                            moveTo(11f, 9f)
                            lineTo(-11f, -9f)
                            moveTo(-4f, -10f)
                            lineTo(-12f, -9f)
                            lineTo(-10f, -1f)
                        }
                        drawPath(
                            path = crossArrows,
                            color = Color(0xFFFF80AB),
                            style = Stroke(width = 3.6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                }
            }
        }

        // 8. Mystery Power-Up Item Boxes
        for (box in itemBoxes) {
            if (!box.available) continue
            if (hasOverpasses && box.zLayer != currentLayer) continue
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

        // 8b. Loose Arena Flag on the map
        if (currentLayer == 0 && arenaFlag != null && arenaFlag.carrierId == null) {
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

        // 8b-ii. Telegraphed Multiplayer Meteor Target Shadow Rings
        val layerStrikes = if (hasOverpasses) meteorStrikes.filter { it.zLayer == currentLayer } else meteorStrikes
        if (layerStrikes.isNotEmpty()) {
            drawMeteorStrikes(layerStrikes, animTimeSec)
        }

        // 8c. Virtual Ghost Car
        if (ghostCar != null && (!hasOverpasses || ghostLayer == currentLayer)) {
            val ghostType = runCatching { RcCarType.valueOf(ghostCar.carType) }.getOrDefault(RcCarType.VOLT_BUGGY)
            drawGhostRcCar(
                x = ghostCar.x,
                y = ghostCar.y,
                angleRad = ghostCar.angleRad,
                carType = ghostType,
                primaryColor = Color(ghostCar.primaryColorArgb),
                accentColor = Color(ghostCar.accentColorArgb),
                jumpZ = ghostCar.jumpZ,
                scale = 1f,
                animTimeSec = animTimeSec,
                visualUpgrades = CarVisualUpgrades.fromEquippedMask(ghostCar.equippedUpgradesMask)
            )
        }

        // 9. Draw RC cars on current layer
        for (r in orderedRacers) {
            if (hasOverpasses && r.zLayer != currentLayer) continue
            val racerUpgrades = CarVisualUpgrades.fromEquippedMask(r.equippedUpgradesMask)
            val isSlidingOrDrifting = (isDriftMode && com.example.drift.DriftPhysics.isDrifting(r.angleRad, r.vx, r.vy, r.jumpZ)) ||
                (r.jumpZ <= 0.05f && kotlin.math.abs(r.vx * (-kotlin.math.sin(r.angleRad)) + r.vy * kotlin.math.cos(r.angleRad)) > 95f && r.speed > 125f)
            if (racerUpgrades.hasCustomTrail && (isSlidingOrDrifting || r.boostMs > 0L)) {
                val deg = r.angleRad * 180f / PI.toFloat()
                val trailMain = Color(r.accentColorArgb)
                val trailGlow = Color(r.primaryColorArgb)
                val trailLen = if (r.boostMs > 0L) 54f else 44f
                withTransform({
                    translate(r.x, r.y)
                    rotate(deg, Offset.Zero)
                }) {
                    drawLine(
                        color = trailGlow.copy(alpha = 0.42f),
                        start = Offset(-15f, -11.5f),
                        end = Offset(-trailLen, -13.5f),
                        strokeWidth = 8.5f,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = trailGlow.copy(alpha = 0.42f),
                        start = Offset(-15f, 11.5f),
                        end = Offset(-trailLen, 13.5f),
                        strokeWidth = 8.5f,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = trailMain.copy(alpha = 0.88f),
                        start = Offset(-15f, -11.5f),
                        end = Offset(-trailLen + 4f, -13.5f),
                        strokeWidth = 3.8f,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = trailMain.copy(alpha = 0.88f),
                        start = Offset(-15f, 11.5f),
                        end = Offset(-trailLen + 4f, 13.5f),
                        strokeWidth = 3.8f,
                        cap = StrokeCap.Round
                    )
                    val emberPulse = 4.5f + 2.5f * sin(animTimeSec * 28f + r.x * 0.06f)
                    drawCircle(
                        color = Color(0xFF00E5FF).copy(alpha = 0.48f),
                        radius = emberPulse,
                        center = Offset(-32f, -12f)
                    )
                    drawCircle(
                        color = Color(0xFFE040FB).copy(alpha = 0.48f),
                        radius = emberPulse,
                        center = Offset(-32f, 12f)
                    )
                }
            } else if (isDriftMode && com.example.drift.DriftPhysics.isDrifting(r.angleRad, r.vx, r.vy, r.jumpZ)) {
                val deg = r.angleRad * 180f / PI.toFloat()
                withTransform({
                    translate(r.x, r.y)
                    rotate(deg, Offset.Zero)
                }) {
                    drawLine(
                        color = Color(0xFF0A0C10).copy(alpha = 0.58f),
                        start = Offset(-15f, -11f),
                        end = Offset(-42f, -13f),
                        strokeWidth = 5f,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = Color(0xFF0A0C10).copy(alpha = 0.58f),
                        start = Offset(-15f, 11f),
                        end = Offset(-42f, 13f),
                        strokeWidth = 5f,
                        cap = StrokeCap.Round
                    )
                    val puff = 6f + 3f * sin(animTimeSec * 24f + r.x * 0.05f)
                    drawCircle(
                        color = Color(0xFFCFD8DC).copy(alpha = 0.34f),
                        radius = puff,
                        center = Offset(-28f, -11f)
                    )
                    drawCircle(
                        color = Color(0xFFCFD8DC).copy(alpha = 0.34f),
                        radius = puff,
                        center = Offset(-28f, 11f)
                    )
                }
            }
            if (r.jumpZ <= 0.05f && r.speed > 30f && !track.isStunt && TrackCatalog.isPointOnGravelPatch(track, r.x, r.y, r.zLayer)) {
                val deg = r.angleRad * 180f / PI.toFloat()
                val dustIntensity = ((r.speed - 30f) / 160f).coerceIn(0.38f, 1f)
                withTransform({
                    translate(r.x, r.y)
                    rotate(deg, Offset.Zero)
                }) {
                    for (dIdx in 0 until 3) {
                        val phase = ((animTimeSec * 4.2f + dIdx * 0.33f + r.x * 0.02f) % 1f)
                        val backX = -18f - phase * 30f
                        val spreadY = 10f + phase * 8f
                        val puffR = (4.5f + phase * 6.5f) * dustIntensity
                        val alpha = (1f - phase) * 0.56f * dustIntensity
                        val puffColor = if (dIdx % 2 == 0) Color(0xFFD7C09E) else Color(0xFFB5966A)
                        drawCircle(
                            color = puffColor.copy(alpha = alpha),
                            radius = puffR,
                            center = Offset(backX, -spreadY)
                        )
                        drawCircle(
                            color = puffColor.copy(alpha = alpha),
                            radius = puffR,
                            center = Offset(backX, spreadY)
                        )
                    }
                }
            }
            if (r.jumpZ <= 0.05f && r.stunnedMs == 0L && kotlin.math.abs(r.spinVisualOffset) > 0.04f) {
                val speedMag = kotlin.math.hypot(r.vx.toDouble(), r.vy.toDouble()).toFloat()
                if (speedMag > 45f) {
                    val velDeg = kotlin.math.atan2(r.vy, r.vx) * 180f / PI.toFloat()
                    val wakeLen = (32f + speedMag * 0.075f).coerceIn(32f, 64f)
                    withTransform({
                        translate(r.x, r.y)
                        rotate(velDeg, Offset.Zero)
                    }) {
                        drawLine(
                            color = Color(0xFF81D4FA).copy(alpha = 0.62f),
                            start = Offset(-8f, -12f),
                            end = Offset(-wakeLen, -20f),
                            strokeWidth = 5.5f,
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = Color(0xFF81D4FA).copy(alpha = 0.62f),
                            start = Offset(-8f, 12f),
                            end = Offset(-wakeLen, 20f),
                            strokeWidth = 5.5f,
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = Color(0xFFE1F5FE).copy(alpha = 0.72f),
                            start = Offset(-10f, -9f),
                            end = Offset(-wakeLen * 0.72f, -15f),
                            strokeWidth = 2.6f,
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = Color(0xFFE1F5FE).copy(alpha = 0.72f),
                            start = Offset(-10f, 9f),
                            end = Offset(-wakeLen * 0.72f, 15f),
                            strokeWidth = 2.6f,
                            cap = StrokeCap.Round
                        )
                        val sprayPulse = 4.2f + 2.4f * sin(animTimeSec * 30f + r.x * 0.07f)
                        drawCircle(
                            color = Color(0xFFB3E5FC).copy(alpha = 0.52f),
                            radius = sprayPulse,
                            center = Offset(-wakeLen * 0.58f, -18f)
                        )
                        drawCircle(
                            color = Color(0xFFB3E5FC).copy(alpha = 0.52f),
                            radius = sprayPulse,
                            center = Offset(-wakeLen * 0.58f, 18f)
                        )
                    }
                }
            }
            if (r.isCountdownRevving && r.jumpZ <= 0.05f) {
                val deg = r.angleRad * 180f / PI.toFloat()
                withTransform({
                    translate(r.x, r.y)
                    rotate(deg, Offset.Zero)
                }) {
                    for (sIdx in 0 until 4) {
                        val sparkPhase = ((animTimeSec * 9.5f + sIdx * 0.25f) % 1f)
                        val backX = -17f - sparkPhase * 18f
                        val spreadY = 10.5f + ((sIdx % 2) * 2 - 1) * sparkPhase * 5.5f
                        val sparkAlpha = (1f - sparkPhase) * 0.92f
                        val sparkColor = if (sIdx % 2 == 0) Color(0xFFFFD54F) else Color(0xFFFF6D00)
                        drawLine(
                            color = sparkColor.copy(alpha = sparkAlpha),
                            start = Offset(backX + 4.5f, -spreadY),
                            end = Offset(backX, -spreadY - 1.5f),
                            strokeWidth = 2.0f,
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = sparkColor.copy(alpha = sparkAlpha),
                            start = Offset(backX + 4.5f, spreadY),
                            end = Offset(backX, spreadY + 1.5f),
                            strokeWidth = 2.0f,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
            if (r.wheelspinSmokeMs > 0L && r.jumpZ <= 0.05f) {
                val deg = r.angleRad * 180f / PI.toFloat()
                val smokeIntensity = (r.wheelspinSmokeMs / 250f).coerceIn(0.35f, 1f)
                withTransform({
                    translate(r.x, r.y)
                    rotate(deg, Offset.Zero)
                }) {
                    for (wIdx in 0 until 4) {
                        val smokePhase = ((animTimeSec * 6.5f + wIdx * 0.25f) % 1f)
                        val backX = -16f - smokePhase * 26f
                        val spreadY = 10f + smokePhase * 8.5f
                        val puffR = (5.5f + smokePhase * 7.5f) * smokeIntensity
                        val alpha = (1f - smokePhase) * 0.68f * smokeIntensity
                        drawCircle(
                            color = Color(0xFFECEFF1).copy(alpha = alpha),
                            radius = puffR,
                            center = Offset(backX, -spreadY)
                        )
                        drawCircle(
                            color = Color(0xFFECEFF1).copy(alpha = alpha),
                            radius = puffR,
                            center = Offset(backX, spreadY)
                        )
                    }
                }
            }
            if (r.launchBoostMs > 0L && r.jumpZ <= 0.12f) {
                val deg = r.angleRad * 180f / PI.toFloat()
                val launchFrac = (r.launchBoostMs.toFloat() / 800f).coerceIn(0.25f, 1f)
                val flicker = 1f + 0.25f * sin(animTimeSec * 52f + r.x * 0.04f)
                val trailLen = 30f + 36f * launchFrac * flicker
                withTransform({
                    translate(r.x, r.y)
                    rotate(deg, Offset.Zero)
                }) {
                    drawLine(
                        color = Color(0xFFFF6D00).copy(alpha = 0.56f * launchFrac),
                        start = Offset(-16f, -10.5f),
                        end = Offset(-16f - trailLen, -12.5f),
                        strokeWidth = 6.5f,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = Color(0xFFFF6D00).copy(alpha = 0.56f * launchFrac),
                        start = Offset(-16f, 10.5f),
                        end = Offset(-16f - trailLen, 12.5f),
                        strokeWidth = 6.5f,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = Color(0xFFFFF176).copy(alpha = 0.90f * launchFrac),
                        start = Offset(-16f, -10.5f),
                        end = Offset(-14f - trailLen * 0.72f, -11.5f),
                        strokeWidth = 2.6f,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = Color(0xFFFFF176).copy(alpha = 0.90f * launchFrac),
                        start = Offset(-16f, 10.5f),
                        end = Offset(-14f - trailLen * 0.72f, 11.5f),
                        strokeWidth = 2.6f,
                        cap = StrokeCap.Round
                    )
                    val coreFlame = Path().apply {
                        moveTo(-18f, -7.5f)
                        lineTo(-22f - 28f * launchFrac * flicker, 0f)
                        lineTo(-18f, 7.5f)
                        close()
                    }
                    drawPath(coreFlame, Color(0xFFFFAB00).copy(alpha = 0.88f * launchFrac))
                    drawCircle(
                        color = Color(0xFFFFFFFF).copy(alpha = 0.92f * launchFrac),
                        radius = 4.2f * flicker,
                        center = Offset(-22f, 0f)
                    )
                    for (eIdx in 0 until 3) {
                        val phase = ((animTimeSec * 10f + eIdx * 0.33f) % 1f)
                        val ex = -24f - phase * 34f * launchFrac
                        val ey = ((eIdx % 2) * 2 - 1) * (4f + phase * 8f)
                        drawCircle(
                            color = Color(0xFFFFD54F).copy(alpha = (1f - phase) * 0.85f * launchFrac),
                            radius = (3.0f - phase * 1.6f).coerceAtLeast(1.0f),
                            center = Offset(ex, ey)
                        )
                    }
                }
            }
            val revJitterX = if (r.isCountdownRevving) sin(animTimeSec * 95f) * 1.4f else 0f
            val revJitterY = if (r.isCountdownRevving) cos(animTimeSec * 115f) * 1.1f else 0f
            val carType = runCatching { RcCarType.valueOf(r.carType) }.getOrDefault(RcCarType.VOLT_BUGGY)
            drawRcCar(
                x = r.x + revJitterX,
                y = r.y + revJitterY,
                angleRad = r.angleRad,
                carType = carType,
                primaryColor = Color(r.primaryColorArgb),
                accentColor = Color(r.accentColorArgb),
                steerVisual = r.steerVisual,
                isBoosting = r.boostMs > 0L || r.launchBoostMs > 0L,
                hasShield = r.shieldMs > 0L,
                isStunned = r.stunnedMs > 0L,
                hasOverdrive = r.overdriveMs > 0L,
                spinVisualOffset = r.spinVisualOffset,
                jumpZ = r.jumpZ,
                scale = 1f,
                animTimeSec = animTimeSec,
                isCarryingFlag = arenaFlag?.carrierId == r.playerId,
                hasElectroAura = r.electroShockAuraMs > 0L,
                isElectroParalyzed = r.electroParalyzedMs > 0L,
                hasRedLaser = r.laserBeamMs > 0L,
                isBraking = r.isBraking,
                nightIntensity = nightIntensity,
                visualUpgrades = racerUpgrades
            )
        }

        // 9a. Active Homing Rockets seeking rivals
        for (rk in rockets) {
            if (hasOverpasses && rk.zLayer != currentLayer) continue
            val deg = rk.angleRad * 180f / PI.toFloat()
            val thrusterFlicker = 1f + 0.30f * sin(animTimeSec * 48f + rk.id)
            withTransform({
                translate(rk.x, rk.y)
                rotate(deg, Offset.Zero)
            }) {
                drawCircle(
                    color = Color(0xFF90A4AE).copy(alpha = 0.35f),
                    radius = 7f * thrusterFlicker,
                    center = Offset(-24f, 0f)
                )
                val exhaust = Path().apply {
                    moveTo(-11f, -5f)
                    lineTo(-11f - 18f * thrusterFlicker, 0f)
                    lineTo(-11f, 5f)
                    close()
                }
                drawPath(exhaust, Color(0xFFFF6D00))
                drawCircle(Color(0xFFFFEA00), radius = 4f * thrusterFlicker, center = Offset(-14f, 0f))

                val fins = Path().apply {
                    moveTo(-4f, -4f)
                    lineTo(-13f, -11f)
                    lineTo(-11f, -4f)
                    close()
                    moveTo(-4f, 4f)
                    lineTo(-13f, 11f)
                    lineTo(-11f, 4f)
                    close()
                }
                drawPath(fins, Color(0xFFD50000))

                drawRoundRect(
                    color = Color(0xFFECEFF1),
                    topLeft = Offset(-11f, -4.5f),
                    size = Size(20f, 9f),
                    cornerRadius = CornerRadius(3f, 3f)
                )
                val warhead = Path().apply {
                    moveTo(8f, -4.5f)
                    lineTo(18f, 0f)
                    lineTo(8f, 4.5f)
                    close()
                }
                drawPath(warhead, Color(0xFFFF1744))
                drawCircle(Color.White, radius = 1.8f, center = Offset(12f, 0f))
            }
        }
    }

    // 9b. Small Mine & Rocket Blast Explosions
    for (exp in explosions) {
        val progress = (exp.ageMs.toFloat() / exp.durationMs.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
        val fade = (1f - progress).coerceIn(0f, 1f)
        val cx = exp.x
        val cy = exp.y

        // Outer shockwave ring
        drawCircle(
            color = Color(0xFFFF5252).copy(alpha = fade * 0.82f),
            radius = 18f + 64f * progress,
            center = Offset(cx, cy),
            style = Stroke(width = (6.5f * fade).coerceAtLeast(1.5f))
        )
        // Smoke ring puff
        drawCircle(
            color = Color(0xFF263238).copy(alpha = fade * 0.45f),
            radius = 14f + 46f * progress,
            center = Offset(cx, cy)
        )
        // Bright orange-red fireball core
        drawCircle(
            color = Color(0xFFFF6D00).copy(alpha = fade * 0.9f),
            radius = (1f - progress * 0.68f) * 34f,
            center = Offset(cx, cy)
        )
        drawCircle(
            color = Color(0xFFFFF59D).copy(alpha = fade),
            radius = (1f - progress * 0.82f) * 18f,
            center = Offset(cx, cy)
        )
        // Radial sparks
        for (s in 0 until 8) {
            val ang = s * (PI.toFloat() / 4f) + exp.id * 0.4f
            val rIn = 12f + 34f * progress
            val rOut = rIn + 15f * fade
            drawLine(
                color = Color(0xFFFFD54F).copy(alpha = fade),
                start = Offset(cx + cos(ang) * rIn, cy + sin(ang) * rIn),
                end = Offset(cx + cos(ang) * rOut, cy + sin(ang) * rOut),
                strokeWidth = 3f,
                cap = StrokeCap.Round
            )
        }
    }

    // 9c. Night Darkness Veil & Car Headlight Illumination Cones
    val clampedNight = nightIntensity.coerceIn(0f, 1f)
    if (clampedNight > 0.01f) {
        drawNightDarknessAndHeadlights(
            track = track,
            orderedRacers = orderedRacers,
            rockets = rockets,
            explosions = explosions,
            ghostCar = ghostCar,
            nightIntensity = clampedNight
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

    // Ghost driver floating label pill
    if (ghostCar != null) {
        val gName = ghostCar.driverName.trim().take(10).ifEmpty { "PB" }
        val gLabel = if (ghostCar.finished) "GHOST • $gName ✓" else "GHOST • $gName"
        val gx = ghostCar.x
        val gy = ghostCar.y - 38f * textScale - (ghostCar.jumpZ * 26f)
        val tw = racerNamePaint.measureText(gLabel)
        val px = 7f * textScale
        val py = 3f * textScale
        val pw = tw + px * 2f
        val ph = textHeight + py * 2f
        drawRoundRect(
            color = Color(0xFF071A24).copy(alpha = 0.72f),
            topLeft = Offset(gx - pw / 2f, gy - ph / 2f),
            size = Size(pw, ph),
            cornerRadius = CornerRadius(7f * textScale, 7f * textScale)
        )
        drawRoundRect(
            color = Color(0xFF00E5FF).copy(alpha = 0.72f),
            topLeft = Offset(gx - pw / 2f, gy - ph / 2f),
            size = Size(pw, ph),
            cornerRadius = CornerRadius(7f * textScale, 7f * textScale),
            style = Stroke(width = 1.4f * textScale)
        )
        racerNamePaint.color = Color(0xFF80D8FF).toArgb()
        val baselineY = gy - (fm.ascent + fm.descent) / 2f
        drawContext.canvas.nativeCanvas.drawText(gLabel, gx, baselineY, racerNamePaint)
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
                    color = Color(0xFF283593).copy(alpha = 0.38f),
                    topLeft = Offset(560f, 470f),
                    size = Size(480f, 400f),
                    cornerRadius = CornerRadius(20f, 20f)
                )
                drawRoundRect(
                    color = Color(0xFFFFCA28).copy(alpha = 0.28f),
                    topLeft = Offset(584f, 494f),
                    size = Size(432f, 352f),
                    cornerRadius = CornerRadius(14f, 14f),
                    style = Stroke(width = 4f)
                )
            }
        }
        TrackId.BACKYARD_DIRT -> {
            // Alternating mowed backyard lawn stripes + natural pebble patches
            val stripeColor = Color(0xFF396037).copy(alpha = 0.32f)
            var x = 0f
            var idx = 0
            while (x < track.worldWidth) {
                if (idx % 2 == 0) {
                    drawRect(stripeColor, topLeft = Offset(x, 0f), size = Size(110f, track.worldHeight))
                }
                x += 110f
                idx++
            }
            val dotColor = Color.Black.copy(alpha = 0.08f)
            for (i in 0 until 28) {
                val px = ((i * 397) % 2100) + 50f
                val py = ((i * 613) % 1400) + 50f
                drawCircle(dotColor, radius = (12 + (i % 4) * 5).toFloat(), center = Offset(px, py))
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
            if (!track.isArena) {
                // Illuminated cyberpunk rooftop helipad ring inside the delta infield
                val hx = 960f
                val hy = 820f
                drawCircle(
                    color = Color(0xFFE040FB).copy(alpha = 0.14f),
                    radius = 128f,
                    center = Offset(hx, hy),
                    style = Stroke(width = 5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(24f, 16f), 0f))
                )
                drawCircle(
                    color = Color(0xFF00E5FF).copy(alpha = 0.12f),
                    radius = 96f,
                    center = Offset(hx, hy),
                    style = Stroke(width = 3f)
                )
            }
        }
        TrackId.SANDBOX_DUNES -> {
            // Sweeping desert sand-dune contour bands + central oasis sandbank glow
            val duneBand = Color(0xFFC68B44).copy(alpha = 0.25f)
            for (i in 0 until 8) {
                val y = 130f + i * 180f
                drawOval(
                    color = duneBand,
                    topLeft = Offset(-120f + (i % 2) * 180f, y),
                    size = Size(track.worldWidth + 120f, 96f)
                )
            }
            if (!track.isArena) {
                drawCircle(
                    color = Color(0xFF81C784).copy(alpha = 0.22f),
                    radius = 96f,
                    center = Offset(1100f, 750f)
                )
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
            if (!track.isArena) {
                // Industrial loading-bay hazard zone outline in the North-East container yard
                drawRoundRect(
                    color = Color(0xFFFFAB00).copy(alpha = 0.08f),
                    topLeft = Offset(1220f, 210f),
                    size = Size(700f, 320f),
                    cornerRadius = CornerRadius(16f, 16f)
                )
                drawRoundRect(
                    color = Color(0xFFFFAB00).copy(alpha = 0.28f),
                    topLeft = Offset(1220f, 210f),
                    size = Size(700f, 320f),
                    cornerRadius = CornerRadius(16f, 16f),
                    style = Stroke(width = 3.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(22f, 16f), 0f))
                )
            }
        }
        TrackId.SKYWAY_OVERPASS -> {
            // Tabletop RC blueprint drafting mat grid + central bridge crossover indicator
            val blueprintGrid = Color(0xFF4DD0E1).copy(alpha = 0.08f)
            var x = 0f
            while (x <= track.worldWidth) {
                drawLine(blueprintGrid, Offset(x, 0f), Offset(x, track.worldHeight), 1.6f)
                x += 140f
            }
            var y = 0f
            while (y <= track.worldHeight) {
                drawLine(blueprintGrid, Offset(0f, y), Offset(track.worldWidth, y), 1.6f)
                y += 140f
            }
            if (!track.isArena) {
                val cx = track.worldWidth * 0.5f
                val cy = track.worldHeight * 0.5f
                drawCircle(
                    color = Color(0xFFFFD54F).copy(alpha = 0.14f),
                    radius = 155f,
                    center = Offset(cx, cy),
                    style = Stroke(width = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(22f, 14f), 0f))
                )
            }
        }
    }
}

private fun extractOverpassSpans(pts: List<TrackPoint>): List<List<TrackPoint>> {
    val n = pts.size
    if (n < 4) return emptyList()
    val elevated = BooleanArray(n) { i -> pts[i].elevation > 0.05f || pts[i].zLayer > 0 }
    if (elevated.all { !it }) return emptyList()
    if (elevated.all { it }) return listOf(pts + pts.first())

    val spans = mutableListOf<List<Int>>()
    var i = 0
    while (i < n) {
        if (elevated[i]) {
            val run = mutableListOf<Int>()
            while (i < n && elevated[i]) {
                run.add(i)
                i++
            }
            spans.add(run)
        } else {
            i++
        }
    }
    if (spans.size >= 2 && elevated[0] && elevated[n - 1]) {
        val first = spans.removeAt(0)
        val last = spans.removeAt(spans.lastIndex)
        spans.add(last + first)
    }
    return spans.map { run ->
        val startNeighbor = (run.first() - 1 + n) % n
        val endNeighbor = (run.last() + 1) % n
        buildList(run.size + 2) {
            add(pts[startNeighbor])
            run.forEach { add(pts[it]) }
            add(pts[endNeighbor])
        }
    }
}

private fun DrawScope.drawOverpassBridgeSpans(
    pts: List<TrackPoint>,
    track: TrackDefinition,
    hardBoundary: Boolean,
    activeEvent: ExternalEventType
) {
    val spans = extractOverpassSpans(pts)
    if (spans.isEmpty()) return

    val roadWidth = pts[0].halfWidth * 2f
    val halfW = pts[0].halfWidth
    for (spanPts in spans) {
        if (spanPts.size < 2) continue
        val bridgePath = Path().apply {
            moveTo(spanPts[0].x, spanPts[0].y)
            for (k in 1 until spanPts.size) {
                lineTo(spanPts[k].x, spanPts[k].y)
            }
        }

        // 1. Soft tabletop drop-shadow cast onto the lower underpass track beneath
        withTransform({
            translate(14f, 20f)
        }) {
            drawPath(
                path = bridgePath,
                color = Color.Black.copy(alpha = 0.22f),
                style = Stroke(width = roadWidth + 50f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            drawPath(
                path = bridgePath,
                color = Color.Black.copy(alpha = 0.44f),
                style = Stroke(width = roadWidth + 32f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        // 2. Elevated overpass road deck
        if (hardBoundary) {
            drawPath(
                path = bridgePath,
                color = Color(0xFF0E131B),
                style = Stroke(width = roadWidth + 36f, cap = StrokeCap.Butt, join = StrokeJoin.Round)
            )
            drawPath(
                path = bridgePath,
                color = Color(0xFFFF6D00),
                style = Stroke(width = roadWidth + 30f, cap = StrokeCap.Butt, join = StrokeJoin.Round)
            )
        }
        drawPath(
            path = bridgePath,
            color = Color(track.id.kerbSecondaryHex),
            style = Stroke(width = roadWidth + 24f, cap = StrokeCap.Butt, join = StrokeJoin.Round)
        )
        drawPath(
            path = bridgePath,
            color = Color(track.id.kerbPrimaryHex),
            style = Stroke(
                width = roadWidth + 24f,
                cap = StrokeCap.Butt,
                join = StrokeJoin.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(38f, 38f), 0f)
            )
        )
        drawPath(
            path = bridgePath,
            color = Color(track.id.roadColorHex),
            style = Stroke(width = roadWidth, cap = StrokeCap.Butt, join = StrokeJoin.Round)
        )
        if (activeEvent == ExternalEventType.SNOW) {
            drawPath(
                path = bridgePath,
                color = Color(0xFFE3F2FD).copy(alpha = 0.24f),
                style = Stroke(width = roadWidth - 8f, cap = StrokeCap.Butt, join = StrokeJoin.Round)
            )
        }
        drawPath(
            path = bridgePath,
            color = Color.White.copy(alpha = 0.16f),
            style = Stroke(
                width = 4f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(28f, 32f), 0f)
            )
        )

        // 3. Tabletop wooden/plastic side-rails along both outer bridge edges
        val leftRailPath = Path()
        val rightRailPath = Path()
        val railOffset = halfW + 15f
        val railPosts = mutableListOf<Offset>()
        for (k in spanPts.indices) {
            val prev = spanPts[(k - 1).coerceAtLeast(0)]
            val next = spanPts[(k + 1).coerceAtMost(spanPts.lastIndex)]
            val tx = next.x - prev.x
            val ty = next.y - prev.y
            val len = hypot(tx, ty).coerceAtLeast(1e-3f)
            val nx = -ty / len
            val ny = tx / len
            val lx = spanPts[k].x - nx * railOffset
            val ly = spanPts[k].y - ny * railOffset
            val rx = spanPts[k].x + nx * railOffset
            val ry = spanPts[k].y + ny * railOffset
            if (k == 0) {
                leftRailPath.moveTo(lx, ly)
                rightRailPath.moveTo(rx, ry)
            } else {
                leftRailPath.lineTo(lx, ly)
                rightRailPath.lineTo(rx, ry)
            }
            if (k % 3 == 1) {
                railPosts.add(Offset(lx, ly))
                railPosts.add(Offset(rx, ry))
            }
        }
        for (railPath in listOf(leftRailPath, rightRailPath)) {
            drawPath(
                path = railPath,
                color = Color(0xFF1E1612),
                style = Stroke(width = 11f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            drawPath(
                path = railPath,
                color = Color(0xFFD7CCC8),
                style = Stroke(width = 5.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
        for (post in railPosts) {
            drawCircle(color = Color(0xFF3E2723), radius = 6.2f, center = post)
            drawCircle(color = Color(0xFFFFECB3), radius = 3.2f, center = post)
        }
    }
}

private fun DrawScope.drawSnowPatches(patches: List<TrackSnowPatch>) {
    for ((idx, sp) in patches.withIndex()) {
        val rx = sp.radius
        val ry = sp.radius * 0.78f
        val rotDeg = ((idx * 37) % 50) - 25f
        withTransform({
            translate(sp.x, sp.y)
            rotate(rotDeg, Offset.Zero)
        }) {
            drawOval(
                color = Color(0xFFB3E5FC).copy(alpha = 0.36f),
                topLeft = Offset(-rx * 1.08f, -ry * 1.08f),
                size = Size(rx * 2.16f, ry * 2.16f)
            )
            drawOval(
                color = Color(0xFFEAF6FF).copy(alpha = 0.88f),
                topLeft = Offset(-rx, -ry),
                size = Size(rx * 2f, ry * 2f)
            )
            drawOval(
                color = Color.White.copy(alpha = 0.76f),
                topLeft = Offset(-rx * 0.68f, -ry * 0.62f),
                size = Size(rx * 1.36f, ry * 1.24f)
            )
            drawOval(
                color = Color(0xFF81D4FA).copy(alpha = 0.56f),
                topLeft = Offset(-rx, -ry),
                size = Size(rx * 2f, ry * 2f),
                style = Stroke(width = 2.2f)
            )
        }
    }
}

private fun DrawScope.drawScorchedCraters(craters: List<TrackCrater>) {
    for (c in craters) {
        val r = c.radius
        drawCircle(
            color = Color(0xFF1A120E).copy(alpha = 0.72f),
            radius = r * 1.12f,
            center = Offset(c.x, c.y)
        )
        drawCircle(
            color = Color(0xFF0D0F14).copy(alpha = 0.90f),
            radius = r * 0.78f,
            center = Offset(c.x, c.y)
        )
        drawCircle(
            color = Color(0xFFFF5722).copy(alpha = 0.32f),
            radius = r * 0.80f,
            center = Offset(c.x, c.y),
            style = Stroke(width = 2.6f)
        )
        for (ray in 0 until 7) {
            val ang = ray * (2f * PI.toFloat() / 7f) + (c.id * 0.61f)
            val midR = r * 0.65f
            val endR = r * (1.06f + (ray % 2) * 0.14f)
            val jitter = if (ray % 2 == 0) 0.14f else -0.12f
            drawLine(
                color = Color(0xFF050608),
                start = Offset(c.x + cos(ang) * (r * 0.2f), c.y + sin(ang) * (r * 0.2f)),
                end = Offset(c.x + cos(ang + jitter) * midR, c.y + sin(ang + jitter) * midR),
                strokeWidth = 3.2f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xFF050608),
                start = Offset(c.x + cos(ang + jitter) * midR, c.y + sin(ang + jitter) * midR),
                end = Offset(c.x + cos(ang) * endR, c.y + sin(ang) * endR),
                strokeWidth = 2.2f,
                cap = StrokeCap.Round
            )
        }
    }
}

private fun DrawScope.drawMeteorStrikes(strikes: List<MeteorStrikeState>, animTimeSec: Float) {
    for (s in strikes) {
        val total = s.totalWarningMs.coerceAtLeast(1L).toFloat()
        val prog = (1f - s.countdownMs.toFloat() / total).coerceIn(0f, 1f)
        val r = s.radius * (0.45f + 0.55f * prog)
        val pulse = 0.55f + 0.45f * sin(animTimeSec * 22f + s.id)
        // Outer growing red-orange warning target shadow on track surface
        drawCircle(
            color = Color(0xFFFF3D00).copy(alpha = 0.16f + 0.24f * prog),
            radius = s.radius,
            center = Offset(s.x, s.y)
        )
        drawCircle(
            color = Color(0xFFFF6D00).copy(alpha = 0.36f * prog),
            radius = r,
            center = Offset(s.x, s.y)
        )
        drawCircle(
            color = Color(0xFFFF1744).copy(alpha = 0.72f + 0.28f * pulse),
            radius = s.radius,
            center = Offset(s.x, s.y),
            style = Stroke(width = 4.2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f), animTimeSec * 60f))
        )
        drawCircle(
            color = Color(0xFFFFAB00).copy(alpha = 0.85f),
            radius = (s.radius * (1f - prog * 0.72f)).coerceAtLeast(12f),
            center = Offset(s.x, s.y),
            style = Stroke(width = 3f)
        )
        // Incoming meteor shadow & fiery core converging on impact center
        val offsetMag = (1f - prog) * 145f
        val meteorX = s.x - offsetMag * 0.65f
        val meteorY = s.y - offsetMag
        drawCircle(
            color = Color(0xFFFF6D00).copy(alpha = 0.75f * prog + 0.25f),
            radius = 12f + 10f * prog,
            center = Offset(meteorX, meteorY)
        )
        drawCircle(
            color = Color(0xFFFFF59D).copy(alpha = 0.90f),
            radius = 6f + 4f * prog,
            center = Offset(meteorX, meteorY)
        )
    }
}

private fun DrawScope.drawPortalHole(
    x: Float,
    y: Float,
    rimColor: Color,
    glowColor: Color,
    animTimeSec: Float,
    isExitWithArrow: Boolean,
    exitAngleRad: Float
) {
    val pulse = 0.5f + 0.5f * sin(animTimeSec * 5.5f)
    // Ground rim glow + cracked concrete lip
    drawCircle(
        color = rimColor.copy(alpha = 0.26f + 0.14f * pulse),
        radius = 48f + 4f * pulse,
        center = Offset(x, y)
    )
    drawOval(
        color = Color(0xFF141821),
        topLeft = Offset(x - 40f, y - 36f),
        size = Size(80f, 72f)
    )
    // Deep abyssal hole in the ground
    drawOval(
        color = Color(0xFF030408),
        topLeft = Offset(x - 33f, y - 29f),
        size = Size(66f, 58f)
    )
    // Swirling inner vortex rings descending into the hole
    val spinDeg = (animTimeSec * 125f) % 360f
    withTransform({
        translate(x, y)
        scale(1f, 0.88f, Offset.Zero)
        rotate(spinDeg, Offset.Zero)
    }) {
        drawCircle(
            color = rimColor,
            radius = 35f,
            center = Offset.Zero,
            style = Stroke(
                width = 4.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(24f, 12f), 0f)
            )
        )
        drawCircle(
            color = glowColor.copy(alpha = 0.85f),
            radius = 23f,
            center = Offset.Zero,
            style = Stroke(
                width = 3f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 12f)
            )
        )
        drawCircle(
            color = rimColor.copy(alpha = 0.55f),
            radius = 12f,
            center = Offset.Zero,
            style = Stroke(width = 2.2f)
        )
    }

    if (isExitWithArrow) {
        val deg = exitAngleRad * 180f / PI.toFloat()
        withTransform({
            translate(x, y)
            rotate(deg, Offset.Zero)
        }) {
            val chev = Path().apply {
                moveTo(38f, -11f)
                lineTo(50f, 0f)
                lineTo(38f, 11f)
            }
            drawPath(
                path = chev,
                color = glowColor,
                style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}

fun DrawScope.drawTrackMiniMap(
    track: TrackDefinition,
    racers: List<RacerState> = emptyList(),
    localPlayerId: String? = null,
    arenaFlag: ArenaFlagState? = null,
    portalPair: PortalPairState? = null,
    arenaSuddenDeathStage: Int = 0,
    stuntState: StuntStateSnapshot? = null,
    ghostCar: ActiveGhostCarState? = null
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
        if (!track.isStunt) {
            close()
        }
    }

    if (track.isStunt) {
        drawStuntMiniMap(
            track = track,
            offsetX = offsetX,
            offsetY = offsetY,
            scale = scale,
            stuntState = stuntState
        )
    } else if (track.isArena) {
        drawPath(
            path = path,
            color = Color(track.id.roadColorHex).copy(alpha = 0.92f)
        )
        drawPath(
            path = path,
            color = if (arenaSuddenDeathStage > 0) {
                Color(0xFFD50000).copy(alpha = 0.55f)
            } else {
                Color(track.id.kerbPrimaryHex)
            },
            style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        if (arenaSuddenDeathStage > 0) {
            val activePts = TrackCatalog.scaledArenaPolygon(track, arenaSuddenDeathStage)
            if (activePts.size >= 3) {
                val safePath = Path().apply {
                    moveTo(offsetX + activePts[0].x * scale, offsetY + activePts[0].y * scale)
                    for (i in 1 until activePts.size) {
                        lineTo(offsetX + activePts[i].x * scale, offsetY + activePts[i].y * scale)
                    }
                    close()
                }
                drawPath(
                    path = safePath,
                    color = Color(0xFFFF1744),
                    style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
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

        // Highlight any elevated overpass bridge spans clearly above lower track
        val bridgeSpans = extractOverpassSpans(pts)
        for (spanPts in bridgeSpans) {
            if (spanPts.size < 2) continue
            val spanPath = Path().apply {
                moveTo(offsetX + spanPts[0].x * scale, offsetY + spanPts[0].y * scale)
                for (k in 1 until spanPts.size) {
                    lineTo(offsetX + spanPts[k].x * scale, offsetY + spanPts[k].y * scale)
                }
            }
            drawPath(
                path = spanPath,
                color = Color.Black.copy(alpha = 0.65f),
                style = Stroke(width = 6.2.dp.toPx(), cap = StrokeCap.Butt, join = StrokeJoin.Round)
            )
            drawPath(
                path = spanPath,
                color = Color(0xFFFFD54F),
                style = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Butt, join = StrokeJoin.Round)
            )
        }

        // Boost pad dots on preview cards (when racers list is empty)
        if (racers.isEmpty()) {
            for (pad in track.boostPads) {
                drawCircle(
                    color = Color(0xFF00E5FF).copy(alpha = 0.85f),
                    radius = 2.2.dp.toPx(),
                    center = Offset(offsetX + pad.x * scale, offsetY + pad.y * scale)
                )
            }
        }

        // Start/Finish tick + directional arrow chevron
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
        val tipDist = 7.dp.toPx()
        val wingDist = 3.2.dp.toPx()
        val tipX = sx + cos(startAng) * tipDist
        val tipY = sy + sin(startAng) * tipDist
        val baseX = sx + cos(startAng) * (tipDist - wingDist)
        val baseY = sy + sin(startAng) * (tipDist - wingDist)
        val wx = -sin(startAng) * wingDist
        val wy = cos(startAng) * wingDist
        val dirArrow = Path().apply {
            moveTo(baseX - wx, baseY - wy)
            lineTo(tipX, tipY)
            lineTo(baseX + wx, baseY + wy)
        }
        drawPath(
            path = dirArrow,
            color = Color(0xFFFFD54F),
            style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
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

    // Active Red & Blue portals on mini-map
    if (portalPair != null) {
        val entryColor = if (portalPair.entryColorIsRed) Color(0xFFFF1744) else Color(0xFF2979FF)
        val exitColor = if (portalPair.entryColorIsRed) Color(0xFF2979FF) else Color(0xFFFF1744)
        drawCircle(
            color = entryColor,
            radius = 3.5.dp.toPx(),
            center = Offset(offsetX + portalPair.entryX * scale, offsetY + portalPair.entryY * scale),
            style = Stroke(width = 1.6.dp.toPx())
        )
        drawCircle(
            color = exitColor,
            radius = 3.5.dp.toPx(),
            center = Offset(offsetX + portalPair.exitX * scale, offsetY + portalPair.exitY * scale),
            style = Stroke(width = 1.6.dp.toPx())
        )
    }

    // Ghost Car hollow cyan beacon on mini-map
    if (ghostCar != null) {
        val gx = offsetX + ghostCar.x * scale
        val gy = offsetY + ghostCar.y * scale
        drawCircle(
            color = Color(0xFF00E5FF).copy(alpha = 0.25f),
            radius = 5.4.dp.toPx(),
            center = Offset(gx, gy)
        )
        drawCircle(
            color = Color(0xFF80D8FF),
            radius = 3.8.dp.toPx(),
            center = Offset(gx, gy),
            style = Stroke(width = 1.5.dp.toPx())
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
            drawRect(color = Color(0xFFDCEEFB).copy(alpha = 0.07f))
            for (i in 0 until 44) {
                val seedX = ((i * 251 + 71) % 1000) / 1000f
                val seedY = ((i * 419 + 29) % 1000) / 1000f
                val fall = (seedY + animTimeSec * (0.26f + (i % 3) * 0.06f)) % 1f
                val sway = sin(animTimeSec * 2.2f + i) * 24f
                val x = ((seedX * w + sway + w) % w)
                val y = fall * h
                val radius = (2.4f + (i % 4) * 1.1f)
                drawCircle(
                    color = Color.White.copy(alpha = 0.58f),
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
        ExternalEventType.METEOR_SHOWER -> {
            drawRect(color = Color(0xFFFF6D00).copy(alpha = 0.06f))
            for (i in 0 until 10) {
                val seedX = ((i * 227 + 61) % 1000) / 1000f
                val seedY = ((i * 389 + 13) % 1000) / 1000f
                val t = (seedY + animTimeSec * (0.85f + (i % 3) * 0.18f)) % 1f
                val x = ((seedX * w + t * 180f) % (w + 120f)) - 60f
                val y = t * (h + 120f) - 60f
                drawLine(
                    color = Color(0xFFFFAB40).copy(alpha = 0.34f),
                    start = Offset(x, y),
                    end = Offset(x + 28f, y + 48f),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round
                )
            }
        }
        ExternalEventType.DAY_NIGHT,
        ExternalEventType.NONE -> Unit
    }
}

private fun DrawScope.drawNightDarknessAndHeadlights(
    track: TrackDefinition,
    orderedRacers: List<RacerState>,
    rockets: List<ActiveRocketState>,
    explosions: List<TrackExplosion>,
    ghostCar: ActiveGhostCarState?,
    nightIntensity: Float
) {
    val margin = 600f
    val nightBounds = Rect(
        left = -margin,
        top = -margin,
        right = track.worldWidth + margin,
        bottom = track.worldHeight + margin
    )

    drawContext.canvas.saveLayer(nightBounds, nightLayerPaint)
    try {
        // 1. Deep night darkness over the entire world
        drawRect(
            color = Color(0xFF02040A).copy(alpha = (0.975f * nightIntensity).coerceIn(0f, 0.975f)),
            topLeft = Offset(-margin, -margin),
            size = Size(track.worldWidth + margin * 2f, track.worldHeight + margin * 2f)
        )

        // 2. Car headlights & localized chassis/taillight punch-outs (DstOut)
        for (r in orderedRacers) {
            val deg = ((r.angleRad + r.spinVisualOffset) * 180f / PI.toFloat())
            withTransform({
                translate(r.x, r.y)
                rotate(deg, Offset.Zero)
            }) {
                // Wide peripheral spill cone
                val wideRange = 430f
                drawArc(
                    brush = Brush.radialGradient(
                        0.0f to Color.White.copy(alpha = 0.56f),
                        0.45f to Color.White.copy(alpha = 0.28f),
                        0.82f to Color.White.copy(alpha = 0.08f),
                        1.0f to Color.Transparent,
                        center = Offset(14f, 0f),
                        radius = wideRange
                    ),
                    startAngle = -38f,
                    sweepAngle = 76f,
                    useCenter = true,
                    topLeft = Offset(14f - wideRange, -wideRange),
                    size = Size(wideRange * 2f, wideRange * 2f),
                    blendMode = BlendMode.DstOut
                )

                // Main forward headlight beam cone (fading smoothly with distance)
                val beamRange = 560f
                drawArc(
                    brush = Brush.radialGradient(
                        0.0f to Color.White,
                        0.36f to Color.White.copy(alpha = 0.94f),
                        0.64f to Color.White.copy(alpha = 0.52f),
                        0.86f to Color.White.copy(alpha = 0.16f),
                        1.0f to Color.Transparent,
                        center = Offset(16f, 0f),
                        radius = beamRange
                    ),
                    startAngle = -26f,
                    sweepAngle = 52f,
                    useCenter = true,
                    topLeft = Offset(16f - beamRange, -beamRange),
                    size = Size(beamRange * 2f, beamRange * 2f),
                    blendMode = BlendMode.DstOut
                )

                // Twin crisp lamp core cones
                val twinRange = 480f
                for (signY in listOf(-1f, 1f)) {
                    val cy = 7f * signY
                    drawArc(
                        brush = Brush.radialGradient(
                            0.0f to Color.White,
                            0.40f to Color.White.copy(alpha = 0.85f),
                            0.76f to Color.White.copy(alpha = 0.22f),
                            1.0f to Color.Transparent,
                            center = Offset(18f, cy),
                            radius = twinRange
                        ),
                        startAngle = -15f,
                        sweepAngle = 30f,
                        useCenter = true,
                        topLeft = Offset(18f - twinRange, cy - twinRange),
                        size = Size(twinRange * 2f, twinRange * 2f),
                        blendMode = BlendMode.DstOut
                    )
                }

                // Immediate car body aura so chassis stays crisp
                drawCircle(
                    brush = Brush.radialGradient(
                        0.0f to Color.White,
                        0.50f to Color.White.copy(alpha = 0.90f),
                        0.84f to Color.White.copy(alpha = 0.28f),
                        1.0f to Color.Transparent,
                        center = Offset.Zero,
                        radius = 74f
                    ),
                    radius = 74f,
                    center = Offset.Zero,
                    blendMode = BlendMode.DstOut
                )

                // Rear taillight / brake light reveal behind car
                val tailAuraRadius = if (r.isBraking) 92f else 68f
                drawCircle(
                    brush = Brush.radialGradient(
                        0.0f to Color.White.copy(alpha = if (r.isBraking) 0.96f else 0.72f),
                        0.55f to Color.White.copy(alpha = 0.30f),
                        1.0f to Color.Transparent,
                        center = Offset(-28f, 0f),
                        radius = tailAuraRadius
                    ),
                    radius = tailAuraRadius,
                    center = Offset(-28f, 0f),
                    blendMode = BlendMode.DstOut
                )

                // Active Electro Shock proximity electric glow
                if (r.electroShockAuraMs > 0L) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            0.0f to Color.White.copy(alpha = 0.88f),
                            0.65f to Color.White.copy(alpha = 0.40f),
                            1.0f to Color.Transparent,
                            center = Offset.Zero,
                            radius = 165f
                        ),
                        radius = 165f,
                        center = Offset.Zero,
                        blendMode = BlendMode.DstOut
                    )
                }
            }

            // Red Laser forward beam cuts through the dark along heading
            if (r.laserBeamMs > 0L) {
                val headingDeg = r.angleRad * 180f / PI.toFloat()
                withTransform({
                    translate(r.x, r.y)
                    rotate(headingDeg, Offset.Zero)
                }) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.85f),
                        start = Offset(20f, 0f),
                        end = Offset(360f, 0f),
                        strokeWidth = 54f,
                        cap = StrokeCap.Round,
                        blendMode = BlendMode.DstOut
                    )
                }
            }
        }

        // Faint glow around homing rockets, explosions, and ghost car
        for (rk in rockets) {
            drawCircle(
                brush = Brush.radialGradient(
                    0.0f to Color.White.copy(alpha = 0.88f),
                    0.55f to Color.White.copy(alpha = 0.36f),
                    1.0f to Color.Transparent,
                    center = Offset(rk.x, rk.y),
                    radius = 74f
                ),
                radius = 74f,
                center = Offset(rk.x, rk.y),
                blendMode = BlendMode.DstOut
            )
        }
        for (exp in explosions) {
            val progress = (exp.ageMs.toFloat() / exp.durationMs.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
            val fade = (1f - progress).coerceIn(0f, 1f)
            val blastRadius = 110f + 95f * progress
            drawCircle(
                brush = Brush.radialGradient(
                    0.0f to Color.White.copy(alpha = fade),
                    0.55f to Color.White.copy(alpha = fade * 0.45f),
                    1.0f to Color.Transparent,
                    center = Offset(exp.x, exp.y),
                    radius = blastRadius
                ),
                radius = blastRadius,
                center = Offset(exp.x, exp.y),
                blendMode = BlendMode.DstOut
            )
        }
        if (ghostCar != null) {
            drawCircle(
                brush = Brush.radialGradient(
                    0.0f to Color.White.copy(alpha = 0.72f),
                    0.60f to Color.White.copy(alpha = 0.25f),
                    1.0f to Color.Transparent,
                    center = Offset(ghostCar.x, ghostCar.y),
                    radius = 62f
                ),
                radius = 62f,
                center = Offset(ghostCar.x, ghostCar.y),
                blendMode = BlendMode.DstOut
            )
        }
    } finally {
        drawContext.canvas.restore()
    }

    // 3. Atmospheric warm-white headlight beam wash & crimson taillight spill over darkness
    for (r in orderedRacers) {
        val deg = ((r.angleRad + r.spinVisualOffset) * 180f / PI.toFloat())
        withTransform({
            translate(r.x, r.y)
            rotate(deg, Offset.Zero)
        }) {
            val sheenRange = 540f
            drawArc(
                brush = Brush.radialGradient(
                    0.0f to Color(0xFFFFF8D6).copy(alpha = 0.16f * nightIntensity),
                    0.42f to Color(0xFFFFF59D).copy(alpha = 0.08f * nightIntensity),
                    0.82f to Color(0xFFFFF59D).copy(alpha = 0.02f * nightIntensity),
                    1.0f to Color.Transparent,
                    center = Offset(16f, 0f),
                    radius = sheenRange
                ),
                startAngle = -24f,
                sweepAngle = 48f,
                useCenter = true,
                topLeft = Offset(16f - sheenRange, -sheenRange),
                size = Size(sheenRange * 2f, sheenRange * 2f)
            )

            // Red taillight / brake spill behind car
            val tailGlowRadius = if (r.isBraking) 82f else 56f
            val tailGlowAlpha = (if (r.isBraking) 0.28f else 0.13f) * nightIntensity
            drawCircle(
                brush = Brush.radialGradient(
                    0.0f to Color(0xFFFF1744).copy(alpha = tailGlowAlpha),
                    0.58f to Color(0xFFD50000).copy(alpha = tailGlowAlpha * 0.4f),
                    1.0f to Color.Transparent,
                    center = Offset(-24f, 0f),
                    radius = tailGlowRadius
                ),
                radius = tailGlowRadius,
                center = Offset(-24f, 0f)
            )
        }
    }
}


package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.TrackCatalog
import com.example.model.BoostPad
import com.example.model.CustomTrackData
import com.example.model.RacerState
import com.example.model.RcCarType
import com.example.model.TrackId
import com.example.model.TrackPoint
import com.example.model.TrackPuddle
import com.example.model.TrackRamp
import com.example.ui.components.drawTrackMiniMap
import com.example.ui.components.drawTrackWorld
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

private enum class EditorTool(val label: String) {
    MOVE_NODE("Drag Corner"),
    ADD_NODE("Add Corner"),
    TOGGLE_RAMP("Toggle Jump"),
    TOGGLE_BOOST("Toggle Boost"),
    TOGGLE_PUDDLE("Toggle Puddle")
}

@Composable
fun TrackEditorScreen(
    initialTrack: CustomTrackData?,
    savedTracks: List<CustomTrackData>,
    canApplyToLobby: Boolean,
    hasBottomNav: Boolean = false,
    onTestDrive: ((CustomTrackData) -> Unit)? = null,
    onSaveTrack: (CustomTrackData, Boolean) -> Unit,
    onDeleteTrack: (String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    var draft by remember(initialTrack?.id) {
        mutableStateOf(initialTrack ?: TrackCatalog.defaultEditorOval())
    }
    var selectedNodeIdx by remember { mutableIntStateOf(0) }
    var selectedRampIdx by remember { mutableIntStateOf(-1) }
    var selectedBoostIdx by remember { mutableIntStateOf(-1) }
    var activeTool by remember { mutableStateOf(EditorTool.MOVE_NODE) }
    var draggingNodeIdx by remember { mutableIntStateOf(-1) }
    var draggingFeatureKind by remember { mutableStateOf<String?>(null) }
    var draggingFeatureIdx by remember { mutableIntStateOf(-1) }
    var savedBannerName by remember { mutableStateOf<String?>(null) }

    val previewTrack = remember(draft) { TrackCatalog.buildCustomTrack(draft) }
    val selectedTheme = remember(draft.themeId) {
        runCatching { TrackId.valueOf(draft.themeId) }.getOrDefault(TrackId.NEON_ROOFTOP)
    }

    val latestDraft by rememberUpdatedState(draft)
    val latestTool by rememberUpdatedState(activeTool)
    val latestPreviewTrack by rememberUpdatedState(previewTrack)
    val latestSelectedRampIdx by rememberUpdatedState(selectedRampIdx)
    val latestSelectedBoostIdx by rememberUpdatedState(selectedBoostIdx)
    val scrollState = rememberScrollState()

    fun findBestInsertIndex(points: List<TrackPoint>, wx: Float, wy: Float): Int {
        if (points.size < 2) return points.size
        var bestSegment = 0
        var bestScore = Float.MAX_VALUE
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            val mx = (a.x + b.x) * 0.5f
            val my = (a.y + b.y) * 0.5f
            val d = hypot(wx - mx, wy - my)
            if (d < bestScore) {
                bestScore = d
                bestSegment = i + 1
            }
        }
        return bestSegment
    }

    val topLevelInsets = if (hasBottomNav) {
        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    } else {
        WindowInsets.safeDrawing
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(topLevelInsets),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .padding(horizontal = if (hasBottomNav) 16.dp else 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!hasBottomNav) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("editor_back_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
                Text(
                    text = "Track Studio",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = {
                        draft = TrackCatalog.defaultEditorOval(selectedTheme)
                        selectedNodeIdx = 0
                        selectedRampIdx = -1
                        selectedBoostIdx = -1
                        savedBannerName = null
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("editor_new_track_button")
                ) {
                    Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New", style = MaterialTheme.typography.labelLarge)
                }
                Spacer(modifier = Modifier.width(6.dp))
                OutlinedButton(
                    onClick = {
                        draft = TrackCatalog.generateRandomTrack(
                            seed = System.currentTimeMillis(),
                            preferredTheme = selectedTheme,
                            roadHalfWidth = draft.roadHalfWidth
                        )
                        selectedNodeIdx = 0
                        selectedRampIdx = -1
                        selectedBoostIdx = -1
                        savedBannerName = null
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("editor_random_button")
                ) {
                    Icon(Icons.Default.Casino, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Random", style = MaterialTheme.typography.labelLarge)
                }
            }

            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .weight(1f)
                    .verticalScroll(scrollState, enabled = draggingNodeIdx < 0 && draggingFeatureIdx < 0)
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val primaryColor = MaterialTheme.colorScheme.primary
                val secondaryColor = MaterialTheme.colorScheme.secondary
                val surfaceDotInner = MaterialTheme.colorScheme.surface
                val overlaySurface = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.90f)
                val overlayTextColor = MaterialTheme.colorScheme.primary
                val isAnyCanvasDragging = draggingNodeIdx >= 0 || draggingFeatureIdx >= 0

                Surface(
                    color = if (draft.isNightTrack) Color(0xFF02040A) else Color(selectedTheme.baseBgColorHex),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(
                        width = if (isAnyCanvasDragging) 2.dp else 1.5.dp,
                        color = if (isAnyCanvasDragging) primaryColor else primaryColor.copy(alpha = 0.55f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(276.dp)
                        .clip(RoundedCornerShape(18.dp))
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("track_editor_canvas")
                                .pointerInput(Unit) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        val w = size.width.toFloat().coerceAtLeast(1f)
                                        val h = size.height.toFloat().coerceAtLeast(1f)
                                        val track = latestPreviewTrack
                                        val scale = minOf(w / track.worldWidth, h / track.worldHeight)
                                            .coerceAtLeast(0.01f)
                                        val tx = (w - track.worldWidth * scale) / 2f
                                        val ty = (h - track.worldHeight * scale) / 2f

                                        fun toWorld(pos: Offset): Pair<Float, Float> {
                                            val wx = ((pos.x - tx) / scale).coerceIn(180f, track.worldWidth - 180f)
                                            val wy = ((pos.y - ty) / scale).coerceIn(180f, track.worldHeight - 180f)
                                            return wx to wy
                                        }

                                        fun findNearestNode(wx: Float, wy: Float): Int {
                                            val screenHitPx = 38.dp.toPx()
                                            val worldHitRadius = maxOf(220f, screenHitPx / scale)
                                            var best = -1
                                            var bestD = worldHitRadius
                                            latestDraft.controlPoints.forEachIndexed { idx, pt ->
                                                val d = hypot(pt.x - wx, pt.y - wy)
                                                if (d < bestD) {
                                                    bestD = d
                                                    best = idx
                                                }
                                            }
                                            return best
                                        }

                                        fun findNearestRamp(wx: Float, wy: Float): Int {
                                            val worldHitRadius = maxOf(165f, 34.dp.toPx() / scale)
                                            var best = -1
                                            var bestD = worldHitRadius
                                            latestDraft.ramps.forEachIndexed { idx, r ->
                                                val d = hypot(r.x - wx, r.y - wy)
                                                if (d < bestD) {
                                                    bestD = d
                                                    best = idx
                                                }
                                            }
                                            return best
                                        }

                                        fun findNearestBoost(wx: Float, wy: Float): Int {
                                            val worldHitRadius = maxOf(165f, 34.dp.toPx() / scale)
                                            var best = -1
                                            var bestD = worldHitRadius
                                            latestDraft.boostPads.forEachIndexed { idx, b ->
                                                val d = hypot(b.x - wx, b.y - wy)
                                                if (d < bestD) {
                                                    bestD = d
                                                    best = idx
                                                }
                                            }
                                            return best
                                        }

                                        val (startWx, startWy) = toWorld(down.position)
                                        val currentTool = latestTool
                                        val touchSlop = viewConfiguration.touchSlop

                                        if (currentTool == EditorTool.MOVE_NODE) {
                                            val hitIdx = findNearestNode(startWx, startWy)
                                            if (hitIdx >= 0 && hitIdx in latestDraft.controlPoints.indices) {
                                                // Immediately claim pointer so parent verticalScroll never steals the drag
                                                down.consume()
                                                draggingNodeIdx = hitIdx
                                                selectedNodeIdx = hitIdx
                                                selectedRampIdx = -1
                                                selectedBoostIdx = -1
                                                savedBannerName = null
                                                val anchorPt = latestDraft.controlPoints[hitIdx]
                                                val grabOffsetX = anchorPt.x - startWx
                                                val grabOffsetY = anchorPt.y - startWy

                                                try {
                                                    while (true) {
                                                        val event = awaitPointerEvent()
                                                        val change = event.changes.firstOrNull { it.id == down.id }
                                                            ?: event.changes.firstOrNull()
                                                            ?: break
                                                        if (!change.pressed) {
                                                            change.consume()
                                                            break
                                                        }
                                                        change.consume()
                                                        val (curWx, curWy) = toWorld(change.position)
                                                        val targetX = (curWx + grabOffsetX)
                                                            .coerceIn(180f, track.worldWidth - 180f)
                                                        val targetY = (curWy + grabOffsetY)
                                                            .coerceIn(180f, track.worldHeight - 180f)
                                                        val currentDraft = latestDraft
                                                        if (hitIdx in currentDraft.controlPoints.indices) {
                                                            val prev = currentDraft.controlPoints[hitIdx]
                                                            if (hypot(targetX - prev.x, targetY - prev.y) > 0.5f) {
                                                                val updated = currentDraft.controlPoints.toMutableList()
                                                                updated[hitIdx] = TrackPoint(
                                                                    x = targetX,
                                                                    y = targetY,
                                                                    halfWidth = currentDraft.roadHalfWidth
                                                                )
                                                                draft = currentDraft.copy(controlPoints = updated)
                                                            }
                                                        }
                                                    }
                                                } finally {
                                                    draggingNodeIdx = -1
                                                }
                                                return@awaitEachGesture
                                            }
                                        }

                                        // Check if user grabbed an existing Jump Ramp or Boost Pad to slide laterally or select/toggle
                                        val hitRampForSlide = if (currentTool == EditorTool.TOGGLE_RAMP || currentTool == EditorTool.MOVE_NODE) {
                                            findNearestRamp(startWx, startWy)
                                        } else -1
                                        val hitBoostForSlide = if (hitRampForSlide < 0 && (currentTool == EditorTool.TOGGLE_BOOST || currentTool == EditorTool.MOVE_NODE)) {
                                            findNearestBoost(startWx, startWy)
                                        } else -1

                                        if (hitRampForSlide >= 0 || hitBoostForSlide >= 0) {
                                            val isRamp = hitRampForSlide >= 0
                                            val featureIdx = if (isRamp) hitRampForSlide else hitBoostForSlide
                                            var startedLateralDrag = false
                                            var lastPos = down.position
                                            try {
                                                while (true) {
                                                    val event = awaitPointerEvent()
                                                    val change = event.changes.firstOrNull { it.id == down.id }
                                                        ?: event.changes.firstOrNull()
                                                        ?: break
                                                    lastPos = change.position
                                                    if (!change.pressed) {
                                                        if (startedLateralDrag) change.consume()
                                                        break
                                                    }
                                                    if (!startedLateralDrag &&
                                                        hypot(lastPos.x - down.position.x, lastPos.y - down.position.y) > touchSlop
                                                    ) {
                                                        startedLateralDrag = true
                                                        draggingFeatureKind = if (isRamp) "RAMP" else "BOOST"
                                                        draggingFeatureIdx = featureIdx
                                                        if (isRamp) {
                                                            selectedRampIdx = featureIdx
                                                            selectedBoostIdx = -1
                                                        } else {
                                                            selectedBoostIdx = featureIdx
                                                            selectedRampIdx = -1
                                                        }
                                                        savedBannerName = null
                                                    }
                                                    if (startedLateralDrag) {
                                                        change.consume()
                                                        val (curWx, curWy) = toWorld(change.position)
                                                        val curPreview = latestPreviewTrack
                                                        val curDraft = latestDraft
                                                        val proj = TrackCatalog.projectOntoCenterline(curPreview.centerline, curWx, curWy)
                                                        val newLat = (proj.lateralOffsetPx / proj.halfWidth.coerceAtLeast(1f))
                                                            .coerceIn(-TrackCatalog.MAX_LATERAL_OFFSET, TrackCatalog.MAX_LATERAL_OFFSET)
                                                        if (isRamp && featureIdx in curDraft.ramps.indices) {
                                                            val updated = curDraft.ramps.toMutableList()
                                                            updated[featureIdx] = TrackCatalog.repositionRampLaterally(
                                                                curPreview,
                                                                updated[featureIdx],
                                                                newLat
                                                            )
                                                            draft = curDraft.copy(ramps = updated)
                                                        } else if (!isRamp && featureIdx in curDraft.boostPads.indices) {
                                                            val updated = curDraft.boostPads.toMutableList()
                                                            updated[featureIdx] = TrackCatalog.repositionBoostLaterally(
                                                                curPreview,
                                                                updated[featureIdx],
                                                                newLat
                                                            )
                                                            draft = curDraft.copy(boostPads = updated)
                                                        }
                                                    }
                                                }
                                            } finally {
                                                draggingFeatureKind = null
                                                draggingFeatureIdx = -1
                                            }
                                            if (!startedLateralDrag) {
                                                val curDraft = latestDraft
                                                if (isRamp) {
                                                    if (currentTool == EditorTool.TOGGLE_RAMP && latestSelectedRampIdx == featureIdx) {
                                                        draft = curDraft.copy(
                                                            ramps = curDraft.ramps.filterIndexed { i, _ -> i != featureIdx }
                                                        )
                                                        selectedRampIdx = -1
                                                        savedBannerName = null
                                                    } else {
                                                        selectedRampIdx = featureIdx
                                                        selectedBoostIdx = -1
                                                    }
                                                } else {
                                                    if (currentTool == EditorTool.TOGGLE_BOOST && latestSelectedBoostIdx == featureIdx) {
                                                        draft = curDraft.copy(
                                                            boostPads = curDraft.boostPads.filterIndexed { i, _ -> i != featureIdx }
                                                        )
                                                        selectedBoostIdx = -1
                                                        savedBannerName = null
                                                    } else {
                                                        selectedBoostIdx = featureIdx
                                                        selectedRampIdx = -1
                                                    }
                                                }
                                            }
                                            return@awaitEachGesture
                                        }

                                        // Non-drag tap tools (ADD_NODE, TOGGLE_RAMP, TOGGLE_BOOST, TOGGLE_PUDDLE) or empty-space tap
                                        var movedTooFar = false
                                        var lastPos = down.position
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                            lastPos = change.position
                                            if (hypot(lastPos.x - down.position.x, lastPos.y - down.position.y) > touchSlop) {
                                                movedTooFar = true
                                            }
                                            if (!change.pressed) break
                                        }

                                        if (!movedTooFar) {
                                            val (wx, wy) = toWorld(lastPos)
                                            val curDraft = latestDraft
                                            val curPreview = latestPreviewTrack
                                            when (currentTool) {
                                                EditorTool.MOVE_NODE -> {
                                                    val hit = findNearestNode(wx, wy)
                                                    if (hit >= 0) {
                                                        selectedNodeIdx = hit
                                                        selectedRampIdx = -1
                                                        selectedBoostIdx = -1
                                                    }
                                                }
                                                EditorTool.ADD_NODE -> {
                                                    if (curDraft.controlPoints.size < 16) {
                                                        val insertAt = findBestInsertIndex(curDraft.controlPoints, wx, wy)
                                                        val updated = curDraft.controlPoints.toMutableList()
                                                        updated.add(insertAt, TrackPoint(wx, wy, curDraft.roadHalfWidth))
                                                        draft = curDraft.copy(controlPoints = updated)
                                                        selectedNodeIdx = insertAt
                                                        selectedRampIdx = -1
                                                        selectedBoostIdx = -1
                                                        savedBannerName = null
                                                    }
                                                }
                                                EditorTool.TOGGLE_RAMP -> {
                                                    val proj = TrackCatalog.projectOntoCenterline(curPreview.centerline, wx, wy)
                                                    val rawLat = (proj.lateralOffsetPx / proj.halfWidth.coerceAtLeast(1f))
                                                        .coerceIn(-TrackCatalog.MAX_LATERAL_OFFSET, TrackCatalog.MAX_LATERAL_OFFSET)
                                                    val lat = if (abs(rawLat) < 0.14f) 0f else rawLat
                                                    val shift = lat * proj.halfWidth
                                                    val nextRamps = curDraft.ramps + TrackRamp(
                                                        x = proj.closestX + proj.normalX * shift,
                                                        y = proj.closestY + proj.normalY * shift,
                                                        angleRad = proj.tangentAngleRad,
                                                        lateralOffset = lat
                                                    )
                                                    draft = curDraft.copy(ramps = nextRamps)
                                                    selectedRampIdx = nextRamps.lastIndex
                                                    selectedBoostIdx = -1
                                                    savedBannerName = null
                                                }
                                                EditorTool.TOGGLE_BOOST -> {
                                                    val proj = TrackCatalog.projectOntoCenterline(curPreview.centerline, wx, wy)
                                                    val rawLat = (proj.lateralOffsetPx / proj.halfWidth.coerceAtLeast(1f))
                                                        .coerceIn(-TrackCatalog.MAX_LATERAL_OFFSET, TrackCatalog.MAX_LATERAL_OFFSET)
                                                    val lat = if (abs(rawLat) < 0.14f) 0f else rawLat
                                                    val shift = lat * proj.halfWidth
                                                    val nextBoosts = curDraft.boostPads + BoostPad(
                                                        x = proj.closestX + proj.normalX * shift,
                                                        y = proj.closestY + proj.normalY * shift,
                                                        angleRad = proj.tangentAngleRad,
                                                        lateralOffset = lat
                                                    )
                                                    draft = curDraft.copy(boostPads = nextBoosts)
                                                    selectedBoostIdx = nextBoosts.lastIndex
                                                    selectedRampIdx = -1
                                                    savedBannerName = null
                                                }
                                                EditorTool.TOGGLE_PUDDLE -> {
                                                    val existingIdx = curDraft.puddles.indexOfFirst {
                                                        hypot(it.x - wx, it.y - wy) < 130f
                                                    }
                                                    if (existingIdx >= 0) {
                                                        draft = curDraft.copy(
                                                            puddles = curDraft.puddles.filterIndexed { i, _ -> i != existingIdx }
                                                        )
                                                    } else {
                                                        val (closestIdx, distToCenter) = TrackCatalog.nearestTrackPoint(curPreview, wx, wy)
                                                        val centerPt = curPreview.centerline[closestIdx]
                                                        val maxOffset = (centerPt.halfWidth * 0.65f).coerceAtLeast(24f)
                                                        val (px, py) = if (distToCenter <= maxOffset || distToCenter < 0.01f) {
                                                            wx to wy
                                                        } else {
                                                            val scaleToRoad = maxOffset / distToCenter
                                                            (centerPt.x + (wx - centerPt.x) * scaleToRoad) to
                                                                (centerPt.y + (wy - centerPt.y) * scaleToRoad)
                                                        }
                                                        draft = curDraft.copy(
                                                            puddles = curDraft.puddles + TrackPuddle(px, py, radius = 38f)
                                                        )
                                                    }
                                                    savedBannerName = null
                                                }
                                            }
                                        }
                                    }
                                }
                        ) {
                            val scale = minOf(size.width / previewTrack.worldWidth, size.height / previewTrack.worldHeight)
                            val tx = (size.width - previewTrack.worldWidth * scale) / 2f
                            val ty = (size.height - previewTrack.worldHeight * scale) / 2f

                            withTransform({
                                translate(tx, ty)
                                scale(scale, scale, Offset.Zero)
                            }) {
                                val previewRacers = if (draft.isNightTrack && previewTrack.centerline.size >= 2) {
                                    val (sx, sy, sa) = TrackCatalog.gridStartTransform(previewTrack, 0)
                                    listOf(
                                        RacerState(
                                            playerId = "preview_car",
                                            name = "Preview",
                                            carType = RcCarType.APEX_FORMULA.name,
                                            primaryColorArgb = 0xFF00E5FFL,
                                            accentColorArgb = 0xFFFFFFFFL,
                                            x = sx,
                                            y = sy,
                                            angleRad = sa
                                        )
                                    )
                                } else {
                                    emptyList()
                                }
                                drawTrackWorld(
                                    track = previewTrack,
                                    itemBoxes = emptyList(),
                                    hazards = emptyList(),
                                    racers = previewRacers,
                                    animTimeSec = 0f,
                                    nightIntensity = if (draft.isNightTrack) 0.90f else 0f
                                )

                                val ctrl = draft.controlPoints
                                if (ctrl.size >= 2) {
                                    val poly = Path().apply {
                                        moveTo(ctrl[0].x, ctrl[0].y)
                                        for (i in 1 until ctrl.size) lineTo(ctrl[i].x, ctrl[i].y)
                                        close()
                                    }
                                    drawPath(
                                        path = poly,
                                        color = secondaryColor.copy(alpha = 0.50f),
                                        style = Stroke(
                                            width = 5f,
                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 14f), 0f)
                                        )
                                    )
                                }

                                ctrl.forEachIndexed { idx, pt ->
                                    val isDragging = idx == draggingNodeIdx
                                    val isSel = idx == selectedNodeIdx
                                    if (isDragging || isSel) {
                                        drawCircle(
                                            color = primaryColor.copy(alpha = if (isDragging) 0.36f else 0.22f),
                                            radius = if (isDragging) 74f else 54f,
                                            center = Offset(pt.x, pt.y)
                                        )
                                    }
                                    drawCircle(
                                        color = if (isSel || isDragging) primaryColor else secondaryColor,
                                        radius = if (isDragging) 42f else if (isSel) 36f else 30f,
                                        center = Offset(pt.x, pt.y)
                                    )
                                    drawCircle(
                                        color = surfaceDotInner,
                                        radius = if (isDragging) 20f else if (isSel) 18f else 14f,
                                        center = Offset(pt.x, pt.y)
                                    )
                                }
                                if (selectedRampIdx in draft.ramps.indices) {
                                    val selRamp = draft.ramps[selectedRampIdx]
                                    drawCircle(
                                        color = primaryColor.copy(alpha = 0.25f),
                                        radius = 74f,
                                        center = Offset(selRamp.x, selRamp.y)
                                    )
                                    drawCircle(
                                        color = primaryColor,
                                        radius = 74f,
                                        center = Offset(selRamp.x, selRamp.y),
                                        style = Stroke(
                                            width = 7f,
                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)
                                        )
                                    )
                                }
                                if (selectedBoostIdx in draft.boostPads.indices) {
                                    val selBoost = draft.boostPads[selectedBoostIdx]
                                    drawCircle(
                                        color = secondaryColor.copy(alpha = 0.25f),
                                        radius = 68f,
                                        center = Offset(selBoost.x, selBoost.y)
                                    )
                                    drawCircle(
                                        color = secondaryColor,
                                        radius = 68f,
                                        center = Offset(selBoost.x, selBoost.y),
                                        style = Stroke(
                                            width = 7f,
                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)
                                        )
                                    )
                                }
                            }
                        }

                        Surface(
                            color = overlaySurface,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                        ) {
                            Text(
                                text = when {
                                    draggingNodeIdx >= 0 -> "Moving Corner #${draggingNodeIdx + 1}..."
                                    draggingFeatureKind == "RAMP" -> "Sliding Jump #${draggingFeatureIdx + 1} across track lanes..."
                                    draggingFeatureKind == "BOOST" -> "Sliding Boost #${draggingFeatureIdx + 1} across track lanes..."
                                    activeTool == EditorTool.MOVE_NODE -> "Drag corner dots or slide jumps/boosts laterally"
                                    activeTool == EditorTool.ADD_NODE -> "Tap anywhere to insert a new corner"
                                    activeTool == EditorTool.TOGGLE_RAMP -> "Tap track to place Jump, drag sideways or pick Lane below"
                                    activeTool == EditorTool.TOGGLE_PUDDLE -> "Tap on track to add or remove a Rain Puddle"
                                    else -> "Tap track to place Boost, drag sideways or pick Lane below"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = overlayTextColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        if (savedBannerName != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp)
                                    .testTag("editor_saved_banner")
                            ) {
                                Text(
                                    text = "Saved \"$savedBannerName\"",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(EditorTool.entries) { tool ->
                        val active = activeTool == tool
                        val icon = when (tool) {
                            EditorTool.MOVE_NODE -> Icons.Default.OpenWith
                            EditorTool.ADD_NODE -> Icons.Default.AddCircleOutline
                            EditorTool.TOGGLE_RAMP -> Icons.Default.FlightTakeoff
                            EditorTool.TOGGLE_BOOST -> Icons.Default.Bolt
                            EditorTool.TOGGLE_PUDDLE -> Icons.Default.WaterDrop
                        }
                        FilterChip(
                            selected = active,
                            onClick = {
                                activeTool = tool
                                when (tool) {
                                    EditorTool.TOGGLE_RAMP -> {
                                        selectedBoostIdx = -1
                                        if (selectedRampIdx !in draft.ramps.indices && draft.ramps.isNotEmpty()) {
                                            selectedRampIdx = draft.ramps.lastIndex
                                        }
                                    }
                                    EditorTool.TOGGLE_BOOST -> {
                                        selectedRampIdx = -1
                                        if (selectedBoostIdx !in draft.boostPads.indices && draft.boostPads.isNotEmpty()) {
                                            selectedBoostIdx = draft.boostPads.lastIndex
                                        }
                                    }
                                    else -> Unit
                                }
                            },
                            leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text(tool.label, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.testTag("editor_tool_${tool.name}")
                        )
                    }
                }

                val hasSelectedRamp = selectedRampIdx in draft.ramps.indices
                val hasSelectedBoost = !hasSelectedRamp && selectedBoostIdx in draft.boostPads.indices
                if (hasSelectedRamp || hasSelectedBoost) {
                    val activeLat = if (hasSelectedRamp) {
                        draft.ramps[selectedRampIdx].lateralOffset
                    } else {
                        draft.boostPads[selectedBoostIdx].lateralOffset
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("editor_lane_selector")
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (hasSelectedRamp) Icons.Default.FlightTakeoff else Icons.Default.Bolt,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (hasSelectedRamp) {
                                            "Jump #${selectedRampIdx + 1} Lane (Gravel spans full track)"
                                        } else {
                                            "Boost #${selectedBoostIdx + 1} Lane"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        if (hasSelectedRamp) {
                                            draft = draft.copy(
                                                ramps = draft.ramps.filterIndexed { i, _ -> i != selectedRampIdx }
                                            )
                                            selectedRampIdx = -1
                                        } else {
                                            draft = draft.copy(
                                                boostPads = draft.boostPads.filterIndexed { i, _ -> i != selectedBoostIdx }
                                            )
                                            selectedBoostIdx = -1
                                        }
                                        savedBannerName = null
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("editor_remove_selected_feature")
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Remove Selected Feature",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    Triple("Left", -0.45f, "editor_lane_left"),
                                    Triple("Center", 0f, "editor_lane_center"),
                                    Triple("Right", 0.45f, "editor_lane_right")
                                ).forEach { (label, targetLat, tag) ->
                                    val isLaneSelected = when (targetLat) {
                                        -0.45f -> activeLat < -0.18f
                                        0.45f -> activeLat > 0.18f
                                        else -> activeLat in -0.18f..0.18f
                                    }
                                    FilterChip(
                                        selected = isLaneSelected,
                                        onClick = {
                                            if (hasSelectedRamp) {
                                                val updated = draft.ramps.toMutableList()
                                                updated[selectedRampIdx] = TrackCatalog.repositionRampLaterally(
                                                    previewTrack,
                                                    updated[selectedRampIdx],
                                                    targetLat
                                                )
                                                draft = draft.copy(ramps = updated)
                                            } else {
                                                val updated = draft.boostPads.toMutableList()
                                                updated[selectedBoostIdx] = TrackCatalog.repositionBoostLaterally(
                                                    previewTrack,
                                                    updated[selectedBoostIdx],
                                                    targetLat
                                                )
                                                draft = draft.copy(boostPads = updated)
                                            }
                                            savedBannerName = null
                                        },
                                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag(tag)
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            if (draft.controlPoints.size > 5) {
                                val removeAt = selectedNodeIdx.coerceIn(0, draft.controlPoints.lastIndex)
                                val nextPts = draft.controlPoints.filterIndexed { i, _ -> i != removeAt }
                                draft = draft.copy(controlPoints = nextPts)
                                selectedNodeIdx = selectedNodeIdx.coerceAtMost(nextPts.lastIndex)
                            }
                        },
                        enabled = draft.controlPoints.size > 5,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.RemoveCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Delete Corner", style = MaterialTheme.typography.labelSmall)
                    }

                    OutlinedButton(
                        onClick = {
                            draft = TrackCatalog.defaultEditorOval(selectedTheme).copy(
                                id = draft.id,
                                name = draft.name,
                                isNightTrack = draft.isNightTrack,
                                randomConditionsEnabled = false
                            )
                            selectedNodeIdx = 0
                            selectedRampIdx = -1
                            selectedBoostIdx = -1
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reset Oval", style = MaterialTheme.typography.labelSmall)
                    }
                }

                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { if (it.length <= 24) draft = draft.copy(name = it) },
                    label = { Text("Track Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("editor_track_name_input")
                )

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Track Width", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${draft.roadHalfWidth.roundToInt()} px",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Slider(
                        value = draft.roadHalfWidth,
                        onValueChange = { w ->
                            draft = draft.copy(
                                roadHalfWidth = w,
                                controlPoints = draft.controlPoints.map { it.copy(halfWidth = w) }
                            )
                        },
                        valueRange = 92f..136f
                    )
                }

                Text("SURFACE THEME", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(TrackId.entries) { theme ->
                        val active = theme == selectedTheme
                        FilterChip(
                            selected = active,
                            onClick = { draft = draft.copy(themeId = theme.name) },
                            label = { Text(theme.displayName, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (draft.isNightTrack) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DarkMode,
                            contentDescription = "Night track lighting",
                            tint = if (draft.isNightTrack) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Night track",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Dark circuit — visibility limited to car headlights",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = draft.isNightTrack,
                            onCheckedChange = { checked ->
                                draft = draft.copy(isNightTrack = checked)
                                savedBannerName = null
                            },
                            modifier = Modifier.testTag("editor_night_track_switch")
                        )
                    }
                }

                // Deterministic lighting only — random environmental effects are strictly a Multiplayer Host lobby option.

                Text(
                    text = "Saved circuits sync automatically when signed in so friends can challenge your times & race live with you.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (savedTracks.isNotEmpty()) {
                    Text("SAVED CUSTOM TRACKS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.testTag("editor_saved_tracks_row")
                    ) {
                        items(savedTracks, key = { it.id }) { saved ->
                            val preview = remember(saved) { TrackCatalog.buildCustomTrack(saved) }
                            val isCurrent = saved.id == draft.id
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(
                                    1.5.dp,
                                    if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier
                                    .width(156.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        draft = saved.copy(randomConditionsEnabled = false)
                                        selectedNodeIdx = 0
                                        selectedRampIdx = -1
                                        selectedBoostIdx = -1
                                    }
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(54.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (saved.isNightTrack) Color(0xFF02040A) else Color(preview.id.baseBgColorHex))
                                    ) {
                                        Canvas(modifier = Modifier.fillMaxSize()) {
                                            drawTrackMiniMap(preview)
                                        }
                                        if (saved.isNightTrack) {
                                            Surface(
                                                color = Color(0xFF0D1B2A).copy(alpha = 0.9f),
                                                shape = RoundedCornerShape(6.dp),
                                                border = BorderStroke(1.dp, Color(0xFF90CAF9).copy(alpha = 0.7f)),
                                                modifier = Modifier
                                                    .align(Alignment.TopStart)
                                                    .padding(4.dp)
                                            ) {
                                                Text(
                                                    text = "NIGHT",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color(0xFF90CAF9),
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        IconButton(
                                            onClick = { onDeleteTrack(saved.id) },
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(48.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete Custom Track",
                                                tint = Color.White.copy(alpha = 0.85f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = saved.name,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val buildDraftTrack: () -> CustomTrackData = {
                        val cleanName = draft.name.trim().ifEmpty { "Custom Circuit" }
                        val fallbackId = TrackCatalog.defaultEditorOval(selectedTheme).id
                        draft.copy(
                            id = draft.id.ifBlank { fallbackId }.take(36),
                            name = cleanName,
                            randomConditionsEnabled = false
                        )
                    }
                    Row(
                        modifier = Modifier
                            .widthIn(max = 680.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onTestDrive != null) {
                            OutlinedButton(
                                onClick = {
                                    val finalTrack = buildDraftTrack()
                                    draft = finalTrack
                                    onTestDrive(finalTrack)
                                },
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .weight(0.75f)
                                    .height(52.dp)
                                    .testTag("editor_test_drive_button")
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Test Drive",
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Button(
                            onClick = {
                                val finalTrack = buildDraftTrack()
                                draft = finalTrack
                                savedBannerName = finalTrack.name
                                onSaveTrack(finalTrack, canApplyToLobby)
                            },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1.25f)
                                .height(52.dp)
                                .testTag("editor_save_button")
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (canApplyToLobby) "SAVE & SELECT IN LOBBY" else "SAVE CUSTOM TRACK",
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

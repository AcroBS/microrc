package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.model.TrackId
import com.example.model.TrackPoint
import com.example.model.TrackRamp
import com.example.ui.components.drawTrackMiniMap
import com.example.ui.components.drawTrackWorld
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

private enum class EditorTool(val label: String) {
    MOVE_NODE("Drag Corner"),
    ADD_NODE("Add Corner"),
    TOGGLE_RAMP("Toggle Jump"),
    TOGGLE_BOOST("Toggle Boost")
}

@Composable
fun TrackEditorScreen(
    initialTrack: CustomTrackData?,
    savedTracks: List<CustomTrackData>,
    canApplyToLobby: Boolean,
    onSaveTrack: (CustomTrackData, Boolean) -> Unit,
    onDeleteTrack: (String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    var draft by remember(initialTrack) {
        mutableStateOf(initialTrack ?: TrackCatalog.defaultEditorOval())
    }
    var selectedNodeIdx by remember { mutableIntStateOf(0) }
    var activeTool by remember { mutableStateOf(EditorTool.MOVE_NODE) }
    var draggingNodeIdx by remember { mutableIntStateOf(-1) }
    var canvasWidthPx by remember { mutableFloatStateOf(1f) }
    var canvasHeightPx by remember { mutableFloatStateOf(1f) }

    val previewTrack = remember(draft) { TrackCatalog.buildCustomTrack(draft) }
    val selectedTheme = remember(draft.themeId) {
        runCatching { TrackId.valueOf(draft.themeId) }.getOrDefault(TrackId.NEON_ROOFTOP)
    }

    fun toWorldCoords(offset: Offset): Pair<Float, Float> {
        val scale = minOf(canvasWidthPx / previewTrack.worldWidth, canvasHeightPx / previewTrack.worldHeight)
            .coerceAtLeast(0.01f)
        val tx = (canvasWidthPx - previewTrack.worldWidth * scale) / 2f
        val ty = (canvasHeightPx - previewTrack.worldHeight * scale) / 2f
        val wx = ((offset.x - tx) / scale).coerceIn(180f, previewTrack.worldWidth - 180f)
        val wy = ((offset.y - ty) / scale).coerceIn(180f, previewTrack.worldHeight - 180f)
        return wx to wy
    }

    fun findNearestControlNode(wx: Float, wy: Float, maxDist: Float = 155f): Int {
        var best = -1
        var bestD = maxDist
        draft.controlPoints.forEachIndexed { idx, pt ->
            val d = hypot(pt.x - wx, pt.y - wy)
            if (d < bestD) {
                bestD = d
                best = idx
            }
        }
        return best
    }

    fun findBestInsertIndex(wx: Float, wy: Float): Int {
        val pts = draft.controlPoints
        if (pts.size < 2) return pts.size
        var bestSegment = 0
        var bestScore = Float.MAX_VALUE
        for (i in pts.indices) {
            val a = pts[i]
            val b = pts[(i + 1) % pts.size]
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

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                Text(
                    text = "Track Studio",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = {
                        draft = TrackCatalog.generateRandomTrack(
                            seed = System.currentTimeMillis(),
                            preferredTheme = selectedTheme,
                            roadHalfWidth = draft.roadHalfWidth
                        )
                        selectedNodeIdx = 0
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
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val primaryColor = MaterialTheme.colorScheme.primary
                val secondaryColor = MaterialTheme.colorScheme.secondary

                Surface(
                    color = Color(selectedTheme.baseBgColorHex),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .clip(RoundedCornerShape(18.dp))
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(activeTool, draft) {
                                    detectTapGestures { pos ->
                                        canvasWidthPx = size.width.toFloat()
                                        canvasHeightPx = size.height.toFloat()
                                        val (wx, wy) = toWorldCoords(pos)
                                        when (activeTool) {
                                            EditorTool.MOVE_NODE -> {
                                                val hit = findNearestControlNode(wx, wy, 190f)
                                                if (hit >= 0) selectedNodeIdx = hit
                                            }
                                            EditorTool.ADD_NODE -> {
                                                if (draft.controlPoints.size < 16) {
                                                    val insertAt = findBestInsertIndex(wx, wy)
                                                    val updated = draft.controlPoints.toMutableList()
                                                    updated.add(insertAt, TrackPoint(wx, wy, draft.roadHalfWidth))
                                                    draft = draft.copy(controlPoints = updated)
                                                    selectedNodeIdx = insertAt
                                                }
                                            }
                                            EditorTool.TOGGLE_RAMP -> {
                                                val existingIdx = draft.ramps.indexOfFirst {
                                                    hypot(it.x - wx, it.y - wy) < 130f
                                                }
                                                if (existingIdx >= 0) {
                                                    draft = draft.copy(
                                                        ramps = draft.ramps.filterIndexed { i, _ -> i != existingIdx }
                                                    )
                                                } else {
                                                    val (closestIdx, _) = TrackCatalog.nearestTrackPoint(previewTrack, wx, wy)
                                                    val pts = previewTrack.centerline
                                                    val p = pts[closestIdx]
                                                    val next = pts[(closestIdx + 1) % pts.size]
                                                    val ang = atan2(next.y - p.y, next.x - p.x)
                                                    draft = draft.copy(
                                                        ramps = draft.ramps + TrackRamp(p.x, p.y, ang)
                                                    )
                                                }
                                            }
                                            EditorTool.TOGGLE_BOOST -> {
                                                val existingIdx = draft.boostPads.indexOfFirst {
                                                    hypot(it.x - wx, it.y - wy) < 130f
                                                }
                                                if (existingIdx >= 0) {
                                                    draft = draft.copy(
                                                        boostPads = draft.boostPads.filterIndexed { i, _ -> i != existingIdx }
                                                    )
                                                } else {
                                                    val (closestIdx, _) = TrackCatalog.nearestTrackPoint(previewTrack, wx, wy)
                                                    val pts = previewTrack.centerline
                                                    val p = pts[closestIdx]
                                                    val next = pts[(closestIdx + 1) % pts.size]
                                                    val ang = atan2(next.y - p.y, next.x - p.x)
                                                    draft = draft.copy(
                                                        boostPads = draft.boostPads + BoostPad(p.x, p.y, ang)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                .pointerInput(activeTool, draft) {
                                    detectDragGestures(
                                        onDragStart = { startOffset ->
                                            canvasWidthPx = size.width.toFloat()
                                            canvasHeightPx = size.height.toFloat()
                                            if (activeTool == EditorTool.MOVE_NODE) {
                                                val (wx, wy) = toWorldCoords(startOffset)
                                                val hit = findNearestControlNode(wx, wy, 200f)
                                                draggingNodeIdx = hit
                                                if (hit >= 0) selectedNodeIdx = hit
                                            } else {
                                                draggingNodeIdx = -1
                                            }
                                        },
                                        onDragEnd = { draggingNodeIdx = -1 },
                                        onDragCancel = { draggingNodeIdx = -1 },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            val idx = draggingNodeIdx
                                            if (idx in draft.controlPoints.indices) {
                                                val (wx, wy) = toWorldCoords(change.position)
                                                val updated = draft.controlPoints.toMutableList()
                                                updated[idx] = TrackPoint(wx, wy, draft.roadHalfWidth)
                                                draft = draft.copy(controlPoints = updated)
                                            }
                                        }
                                    )
                                }
                        ) {
                            canvasWidthPx = size.width
                            canvasHeightPx = size.height
                            val scale = minOf(size.width / previewTrack.worldWidth, size.height / previewTrack.worldHeight)
                            val tx = (size.width - previewTrack.worldWidth * scale) / 2f
                            val ty = (size.height - previewTrack.worldHeight * scale) / 2f

                            withTransform({
                                translate(tx, ty)
                                scale(scale, scale, Offset.Zero)
                            }) {
                                drawTrackWorld(
                                    track = previewTrack,
                                    itemBoxes = emptyList(),
                                    hazards = emptyList(),
                                    racers = emptyList(),
                                    animTimeSec = 0f
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
                                        color = secondaryColor.copy(alpha = 0.45f),
                                        style = Stroke(
                                            width = 5f,
                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 14f), 0f)
                                        )
                                    )
                                }

                                ctrl.forEachIndexed { idx, pt ->
                                    val isSel = idx == selectedNodeIdx
                                    drawCircle(
                                        color = if (isSel) primaryColor else secondaryColor,
                                        radius = if (isSel) 34f else 26f,
                                        center = Offset(pt.x, pt.y)
                                    )
                                    drawCircle(
                                        color = Color(0xFF0E131B),
                                        radius = if (isSel) 18f else 13f,
                                        center = Offset(pt.x, pt.y)
                                    )
                                }
                            }
                        }

                        Surface(
                            color = Color(0xFF0B0E14).copy(alpha = 0.82f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                        ) {
                            Text(
                                text = when (activeTool) {
                                    EditorTool.MOVE_NODE -> "Drag dots to reshape corners"
                                    EditorTool.ADD_NODE -> "Tap anywhere to insert a new corner"
                                    EditorTool.TOGGLE_RAMP -> "Tap on track to add or remove a Jump Ramp"
                                    EditorTool.TOGGLE_BOOST -> "Tap on track to add or remove a Boost Pad"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF80EAFF),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
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
                        }
                        FilterChip(
                            selected = active,
                            onClick = { activeTool = tool },
                            leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text(tool.label, style = MaterialTheme.typography.labelSmall) }
                        )
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
                                name = draft.name
                            )
                            selectedNodeIdx = 0
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

                if (savedTracks.isNotEmpty()) {
                    Text("SAVED CUSTOM TRACKS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                        draft = saved
                                        selectedNodeIdx = 0
                                    }
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(54.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(preview.id.baseBgColorHex))
                                    ) {
                                        Canvas(modifier = Modifier.fillMaxSize()) {
                                            drawTrackMiniMap(preview)
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
                    Button(
                        onClick = {
                            val cleanName = draft.name.trim().ifEmpty { "Custom Circuit" }
                            val finalTrack = draft.copy(
                                id = draft.id.ifBlank { "custom_${System.currentTimeMillis()}" },
                                name = cleanName
                            )
                            onSaveTrack(finalTrack, canApplyToLobby)
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .widthIn(max = 680.dp)
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("editor_save_button")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (canApplyToLobby) "SAVE & SELECT IN LOBBY" else "SAVE CUSTOM TRACK",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

package com.example.data

import com.example.model.ArenaShape
import com.example.model.BoostPad
import com.example.model.CustomTrackData
import com.example.model.GravelPatch
import com.example.model.ItemBoxSpawn
import com.example.model.PropShape
import com.example.model.StuntGolfHole
import com.example.model.StuntPinState
import com.example.model.StuntVariant
import com.example.model.TrackDefinition
import com.example.model.TrackId
import com.example.model.TrackPoint
import com.example.model.TrackProp
import com.example.model.TrackRamp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

import java.util.Collections
import java.util.WeakHashMap

object TrackCatalog {

    const val RAMP_HALF_LENGTH = 36f
    const val RAMP_HALF_WIDTH = 34f
    const val GRAVEL_START_OFFSET = 32f
    const val GRAVEL_LENGTH = 176f
    const val GRAVEL_EDGE_INSET = 4f
    const val ARENA_GRAVEL_HALF_WIDTH = 94f
    const val MAX_LATERAL_OFFSET = 0.55f
    val LANE_LATERAL_OFFSETS = listOf(-0.45f, 0f, 0.45f)

    data class SplineProjection(
        val arcS: Float,
        val lateralOffsetPx: Float,
        val halfWidth: Float,
        val closestX: Float,
        val closestY: Float,
        val tangentAngleRad: Float,
        val normalX: Float,
        val normalY: Float,
        val totalLength: Float
    )

    data class SplineSample(
        val x: Float,
        val y: Float,
        val normalX: Float,
        val normalY: Float,
        val tangentAngleRad: Float,
        val halfWidth: Float
    )

    private class SplineArcTable(
        val cumLen: FloatArray,
        val totalLength: Float
    )

    private val splineTableCache = Collections.synchronizedMap(WeakHashMap<List<TrackPoint>, SplineArcTable>())
    private val trackGravelCache = Collections.synchronizedMap(WeakHashMap<TrackDefinition, List<GravelPatch>>())

    private val tracks: Map<TrackId, TrackDefinition> = TrackId.entries.associateWith { id ->
        buildTrack(id)
    }

    private val arenas: Map<ArenaShape, TrackDefinition> = ArenaShape.entries.associateWith { shape ->
        buildArenaTrack(shape)
    }

    private val stunts: Map<StuntVariant, TrackDefinition> = StuntVariant.entries.associateWith { variant ->
        buildStuntTrack(variant)
    }

    fun getTrack(id: TrackId): TrackDefinition = tracks.getValue(id)

    fun getTrack(idName: String): TrackDefinition {
        val id = runCatching { TrackId.valueOf(idName) }.getOrDefault(TrackId.LIVING_ROOM_GP)
        return getTrack(id)
    }

    fun allTracks(): List<TrackDefinition> = TrackId.entries.map { getTrack(it) }

    fun getArenaTrack(shape: ArenaShape): TrackDefinition = arenas.getValue(shape)

    fun getArenaTrack(shapeName: String?): TrackDefinition {
        val shape = shapeName?.let { runCatching { ArenaShape.valueOf(it) }.getOrNull() } ?: ArenaShape.SQUARE
        return getArenaTrack(shape)
    }

    fun allArenas(): List<TrackDefinition> = ArenaShape.entries.map { getArenaTrack(it) }

    fun getStuntTrack(variant: StuntVariant): TrackDefinition = stunts.getValue(variant)

    fun getStuntTrack(variantName: String?): TrackDefinition {
        val variant = variantName?.let { runCatching { StuntVariant.valueOf(it) }.getOrNull() } ?: StuntVariant.LONG_JUMP
        return getStuntTrack(variant)
    }

    fun allStunts(): List<TrackDefinition> = StuntVariant.entries.map { getStuntTrack(it) }

    private fun buildTrack(id: TrackId): TrackDefinition {
        val worldW = 2200f
        val worldH = 1500f

        return when (id) {
            TrackId.LIVING_ROOM_GP -> {
                val ctrl = listOf(
                    TrackPoint(460f, 320f, 114f),
                    TrackPoint(1100f, 260f, 114f),
                    TrackPoint(1750f, 330f, 114f),
                    TrackPoint(1890f, 600f, 112f),
                    TrackPoint(1420f, 680f, 112f),
                    TrackPoint(1460f, 920f, 112f),
                    TrackPoint(1840f, 1020f, 114f),
                    TrackPoint(1800f, 1250f, 114f),
                    TrackPoint(1320f, 1310f, 114f),
                    TrackPoint(680f, 1270f, 114f),
                    TrackPoint(330f, 1060f, 114f),
                    TrackPoint(300f, 680f, 114f)
                )
                val spline = subdivideClosedSpline(ctrl, samplesPerSegment = 14)
                val boosts = listOf(
                    boostAt(spline, 14),
                    boostAt(spline, 112)
                )
                val items = listOf(
                    itemAt(spline, 26, 0),
                    itemAt(spline, 56, 1),
                    itemAt(spline, 90, 2),
                    itemAt(spline, 128, 3),
                    itemAt(spline, 150, 4)
                )
                val ramps = listOf(
                    rampAt(spline, 40),
                    rampAt(spline, 120)
                )
                val props = listOf(
                    TrackProp(760f, 600f, 62f, PropShape.CIRCLE, 0xFFE65100, 0xFFFFCC80, isSolidObstacle = false),
                    TrackProp(960f, 840f, 44f, PropShape.CRATE, 0xFF8D6E63, 0xFFD7CCC8, isSolidObstacle = true),
                    TrackProp(660f, 880f, 48f, PropShape.CRATE, 0xFF546E7A, 0xFFB0BEC5, isSolidObstacle = true),
                    TrackProp(1040f, 560f, 36f, PropShape.CIRCLE, 0xFF00897B, 0xFFB2DFDB, isSolidObstacle = true),
                    TrackProp(180f, 180f, 52f, PropShape.CRATE, 0xFF6D4C41, 0xFFBCAAA4, isSolidObstacle = false),
                    TrackProp(2040f, 1360f, 56f, PropShape.CIRCLE, 0xFFD84315, 0xFFFFAB91, isSolidObstacle = false)
                )
                TrackDefinition(id, worldW, worldH, spline, boosts, items, props, ramps = ramps)
            }

            TrackId.BACKYARD_DIRT -> {
                // Counter-clockwise wide off-road rallycross with twin northern peaks & central mud valley
                val ctrl = listOf(
                    TrackPoint(720f, 1250f, 124f),
                    TrackPoint(1380f, 1260f, 124f),
                    TrackPoint(1880f, 1120f, 124f),
                    TrackPoint(1920f, 620f, 124f),
                    TrackPoint(1640f, 260f, 124f),
                    TrackPoint(1240f, 340f, 124f),
                    TrackPoint(940f, 680f, 124f),
                    TrackPoint(620f, 320f, 124f),
                    TrackPoint(260f, 580f, 124f),
                    TrackPoint(320f, 1080f, 124f)
                )
                val spline = subdivideClosedSpline(ctrl, samplesPerSegment = 14)
                val boosts = listOf(
                    boostAt(spline, 10),
                    boostAt(spline, 52),
                    boostAt(spline, 108)
                )
                val items = listOf(
                    itemAt(spline, 20, 0),
                    itemAt(spline, 44, 1),
                    itemAt(spline, 70, 2),
                    itemAt(spline, 96, 3),
                    itemAt(spline, 122, 4)
                )
                val ramps = listOf(
                    rampAt(spline, 30, impulse = 1.12f),
                    rampAt(spline, 82, impulse = 1.15f),
                    rampAt(spline, 118, impulse = 1.08f)
                )
                val props = listOf(
                    // On-track wide-line mud slicks (non-solid: slow ground cars unless jumped or avoided)
                    TrackProp(940f, 752f, 36f, PropShape.MUD_POOL, 0xFF4E342E, 0xFF8D6E63, isSolidObstacle = false),
                    TrackProp(1815f, 870f, 36f, PropShape.MUD_POOL, 0xFF4E342E, 0xFF8D6E63, isSolidObstacle = false),
                    TrackProp(355f, 830f, 34f, PropShape.MUD_POOL, 0xFF3E2723, 0xFF795548, isSolidObstacle = false),
                    // Safe infield & perimeter garden trees
                    TrackProp(1500f, 780f, 58f, PropShape.TREE, 0xFF2E7D32, 0xFF81C784, isSolidObstacle = true),
                    TrackProp(660f, 930f, 44f, PropShape.TREE, 0xFF388E3C, 0xFFA5D6A7, isSolidObstacle = true),
                    TrackProp(140f, 180f, 56f, PropShape.TREE, 0xFF1B5E20, 0xFF66BB6A, isSolidObstacle = false),
                    TrackProp(2060f, 200f, 52f, PropShape.TREE, 0xFF2E7D32, 0xFF81C784, isSolidObstacle = false),
                    TrackProp(2060f, 1360f, 58f, PropShape.TREE, 0xFF33691E, 0xFF9CCC65, isSolidObstacle = false)
                )
                TrackDefinition(id, worldW, worldH, spline, boosts, items, props, ramps = ramps)
            }

            TrackId.NEON_ROOFTOP -> {
                // Clockwise supersonic diagonal Delta Wing / Tri-Oval speedway with 5 turbo pads
                val ctrl = listOf(
                    TrackPoint(700f, 320f, 106f),
                    TrackPoint(1240f, 440f, 106f),
                    TrackPoint(1760f, 580f, 106f),
                    TrackPoint(1920f, 860f, 106f),
                    TrackPoint(1520f, 1140f, 106f),
                    TrackPoint(960f, 1260f, 106f),
                    TrackPoint(420f, 1200f, 106f),
                    TrackPoint(280f, 780f, 106f),
                    TrackPoint(340f, 380f, 106f)
                )
                val spline = subdivideClosedSpline(ctrl, samplesPerSegment = 15)
                val boosts = listOf(
                    boostAt(spline, 8),
                    boostAt(spline, 26),
                    boostAt(spline, 62),
                    boostAt(spline, 86),
                    boostAt(spline, 112)
                )
                val items = listOf(
                    itemAt(spline, 16, 0),
                    itemAt(spline, 38, 1),
                    itemAt(spline, 56, 2),
                    itemAt(spline, 76, 3),
                    itemAt(spline, 98, 4),
                    itemAt(spline, 120, 5)
                )
                val ramps = listOf(
                    rampAt(spline, 20, impulse = 1.14f),
                    rampAt(spline, 68, impulse = 1.10f),
                    rampAt(spline, 104, impulse = 1.12f)
                )
                val props = listOf(
                    TrackProp(820f, 800f, 44f, PropShape.PYLON, 0xFF00E5FF, 0xFFE040FB, isSolidObstacle = true),
                    TrackProp(1220f, 820f, 42f, PropShape.PYLON, 0xFFE040FB, 0xFF00E5FF, isSolidObstacle = true),
                    TrackProp(640f, 880f, 38f, PropShape.PYLON, 0xFF00E5FF, 0xFFFFFFFF, isSolidObstacle = true),
                    TrackProp(1960f, 220f, 50f, PropShape.CRATE, 0xFF1A233A, 0xFFE040FB, isSolidObstacle = false),
                    TrackProp(140f, 1340f, 48f, PropShape.CRATE, 0xFF1A233A, 0xFF00E5FF, isSolidObstacle = false),
                    TrackProp(2040f, 1340f, 48f, PropShape.CRATE, 0xFF1A233A, 0xFF00E5FF, isSolidObstacle = false)
                )
                TrackDefinition(id, worldW, worldH, spline, boosts, items, props, ramps = ramps)
            }

            TrackId.SANDBOX_DUNES -> {
                // Counter-clockwise symmetric Desert Hourglass / 6-switchback canyon serpentine
                val ctrl = listOf(
                    TrackPoint(1880f, 920f, 110f),
                    TrackPoint(1880f, 420f, 110f),
                    TrackPoint(1460f, 250f, 110f),
                    TrackPoint(1120f, 540f, 110f),
                    TrackPoint(680f, 260f, 110f),
                    TrackPoint(270f, 480f, 110f),
                    TrackPoint(280f, 980f, 110f),
                    TrackPoint(720f, 1260f, 110f),
                    TrackPoint(1080f, 960f, 110f),
                    TrackPoint(1520f, 1250f, 110f)
                )
                val spline = subdivideClosedSpline(ctrl, samplesPerSegment = 14)
                val boosts = listOf(
                    boostAt(spline, 8),
                    boostAt(spline, 52),
                    boostAt(spline, 96)
                )
                val items = listOf(
                    itemAt(spline, 22, 0),
                    itemAt(spline, 44, 1),
                    itemAt(spline, 72, 2),
                    itemAt(spline, 104, 3),
                    itemAt(spline, 124, 4)
                )
                val ramps = listOf(
                    rampAt(spline, 16, impulse = 1.08f),
                    rampAt(spline, 62, impulse = 1.12f),
                    rampAt(spline, 114, impulse = 1.08f)
                )
                val props = listOf(
                    // Central desert oasis lagoon + shallow water slicks on switchback apexes
                    TrackProp(1100f, 750f, 54f, PropShape.MUD_POOL, 0xFF0288D1, 0xFF81D4FA, isSolidObstacle = false),
                    TrackProp(1120f, 608f, 33f, PropShape.MUD_POOL, 0xFF0288D1, 0xFFB3E5FC, isSolidObstacle = false),
                    TrackProp(1080f, 892f, 33f, PropShape.MUD_POOL, 0xFF0288D1, 0xFFB3E5FC, isSolidObstacle = false),
                    // West & East dune basin saguaro cacti
                    TrackProp(660f, 750f, 42f, PropShape.CACTUS, 0xFF2E7D32, 0xFFA5D6A7, isSolidObstacle = true),
                    TrackProp(1520f, 740f, 42f, PropShape.CACTUS, 0xFF388E3C, 0xFFC8E6C9, isSolidObstacle = true),
                    TrackProp(140f, 190f, 44f, PropShape.CACTUS, 0xFF2E7D32, 0xFFA5D6A7, isSolidObstacle = false),
                    TrackProp(2060f, 1340f, 48f, PropShape.CACTUS, 0xFF1B5E20, 0xFF81C784, isSolidObstacle = false)
                )
                TrackDefinition(id, worldW, worldH, spline, boosts, items, props, ramps = ramps)
            }

            TrackId.WAREHOUSE_DRIFT -> {
                // Clockwise compact L-Boot concrete gymkhana with 90° drift hairpins & loading-dock straight
                val ctrl = listOf(
                    TrackPoint(290f, 820f, 96f),
                    TrackPoint(310f, 360f, 96f),
                    TrackPoint(780f, 270f, 96f),
                    TrackPoint(920f, 720f, 96f),
                    TrackPoint(1420f, 760f, 96f),
                    TrackPoint(1890f, 800f, 96f),
                    TrackPoint(1880f, 1220f, 96f),
                    TrackPoint(1400f, 1270f, 96f),
                    TrackPoint(860f, 1260f, 96f),
                    TrackPoint(380f, 1220f, 96f)
                )
                val spline = subdivideClosedSpline(ctrl, samplesPerSegment = 14)
                val boosts = listOf(
                    boostAt(spline, 12),
                    boostAt(spline, 50),
                    boostAt(spline, 88),
                    boostAt(spline, 116)
                )
                val items = listOf(
                    itemAt(spline, 22, 0),
                    itemAt(spline, 40, 1),
                    itemAt(spline, 64, 2),
                    itemAt(spline, 80, 3),
                    itemAt(spline, 104, 4),
                    itemAt(spline, 124, 5)
                )
                val ramps = listOf(
                    rampAt(spline, 98, impulse = 1.05f)
                )
                val props = listOf(
                    // Outer clipping-zone industrial oil slicks
                    TrackProp(865f, 775f, 32f, PropShape.MUD_POOL, 0xFF1C242B, 0xFF4FC3F7, isSolidObstacle = false),
                    TrackProp(1830f, 1010f, 32f, PropShape.MUD_POOL, 0xFF1C242B, 0xFF4FC3F7, isSolidObstacle = false),
                    // Infield & North-East container yard obstacles
                    TrackProp(600f, 780f, 46f, PropShape.CRATE, 0xFF455A64, 0xFFFFAB00, isSolidObstacle = true),
                    TrackProp(1400f, 1015f, 40f, PropShape.CRATE, 0xFF37474F, 0xFFFFAB00, isSolidObstacle = true),
                    TrackProp(1360f, 340f, 58f, PropShape.CRATE, 0xFF455A64, 0xFFFFAB00, isSolidObstacle = true),
                    TrackProp(1740f, 360f, 54f, PropShape.CRATE, 0xFF37474F, 0xFFFF6D00, isSolidObstacle = true),
                    TrackProp(1550f, 440f, 38f, PropShape.PYLON, 0xFFFF6D00, 0xFFFFFFFF, isSolidObstacle = true)
                )
                TrackDefinition(id, worldW, worldH, spline, boosts, items, props, ramps = ramps)
            }
        }
    }

    fun buildCustomTrack(data: CustomTrackData): TrackDefinition {
        val worldW = 2200f
        val worldH = 1500f
        val theme = runCatching { TrackId.valueOf(data.themeId) }.getOrDefault(TrackId.NEON_ROOFTOP)
        val safeControl = if (data.controlPoints.size >= 4) {
            data.controlPoints.map { it.copy(halfWidth = data.roadHalfWidth.coerceIn(88f, 140f)) }
        } else {
            defaultEditorOval(theme).controlPoints
        }
        val spline = subdivideClosedSpline(safeControl, samplesPerSegment = 14)

        val boosts = data.boostPads.ifEmpty {
            listOf(
                boostAt(spline, (spline.size * 0.14).toInt()),
                boostAt(spline, (spline.size * 0.64).toInt())
            )
        }
        val noTrackItems = data.id.startsWith("drift_") ||
            data.id.startsWith("daily_drift_") ||
            data.id.startsWith("daily_")
        val items = if (noTrackItems) {
            data.itemSpawns
        } else {
            data.itemSpawns.ifEmpty {
                listOf(0.22f, 0.44f, 0.68f, 0.86f).mapIndexed { idx, frac ->
                    itemAt(spline, (spline.size * frac).toInt(), idx)
                }
            }
        }
        val ramps = data.ramps
        return TrackDefinition(
            id = theme,
            worldWidth = worldW,
            worldHeight = worldH,
            centerline = spline,
            boostPads = boosts,
            itemSpawns = items,
            props = data.props,
            ramps = ramps,
            puddles = data.puddles,
            customId = data.id,
            customName = data.name,
            isNightTrack = data.isNightTrack,
            randomConditionsEnabled = false
        )
    }

    fun toCustomTrackData(track: TrackDefinition): CustomTrackData {
        // Sample ~11 evenly spaced points back if coming from a built-in track
        val step = (track.centerline.size / 11).coerceAtLeast(1)
        val ctrl = track.centerline.filterIndexed { index, _ -> index % step == 0 }.take(12)
        return CustomTrackData(
            id = track.customId ?: "track_${track.id.name.lowercase()}",
            name = track.displayTitle,
            themeId = track.id.name,
            roadHalfWidth = track.centerline.firstOrNull()?.halfWidth ?: 112f,
            controlPoints = ctrl,
            boostPads = track.boostPads,
            itemSpawns = track.itemSpawns,
            ramps = track.ramps,
            props = track.props,
            puddles = track.puddles,
            isNightTrack = track.isNightTrack,
            randomConditionsEnabled = false
        )
    }

    fun withRandomJumps(
        track: TrackDefinition,
        enabled: Boolean,
        seed: Long = System.currentTimeMillis()
    ): TrackDefinition {
        if (track.isStunt) return track
        if (!enabled) return track.copy(ramps = emptyList())
        val rng = Random(seed)
        if (track.isArena) {
            val targetCount = rng.nextInt(3, 6) // 3..5 random jumps across open arena
            val chosen = mutableListOf<TrackRamp>()
            val avoid = buildList {
                track.boostPads.forEach { add(it.x to it.y) }
                track.itemSpawns.forEach { add(it.x to it.y) }
                track.props.forEach { add(it.x to it.y) }
            }
            var attempts = 0
            while (chosen.size < targetCount && attempts < 90) {
                attempts++
                val (rx, ry) = sampleArenaInteriorPoint(
                    track = track,
                    rng = rng,
                    minWallClearance = 155f,
                    avoidPoints = avoid + chosen.map { it.x to it.y },
                    minAvoidDist = 165f
                )
                val angle = rng.nextFloat() * (2f * PI.toFloat())
                chosen.add(TrackRamp(rx, ry, angle, radius = 48f, impulse = rng.nextFloat() * 0.25f + 0.95f))
            }
            return track.copy(ramps = chosen.ifEmpty { track.ramps })
        }

        val n = track.centerline.size
        if (n < 40) return track
        val targetCount = rng.nextInt(2, 5) // 2..4 random jumps per race
        val chosen = mutableListOf<TrackRamp>()
        val laneDeck = LANE_LATERAL_OFFSETS.shuffled(rng)
        val minSpacing = 240f
        var attempts = 0
        while (chosen.size < targetCount && attempts < 60) {
            attempts++
            // Avoid start/finish line neighborhood (first 10% and last 12%)
            val idx = rng.nextInt((n * 0.12f).toInt(), (n * 0.88f).toInt())
            val laneOffset = laneDeck[chosen.size % laneDeck.size]
            val cand = rampAt(
                spline = track.centerline,
                index = idx,
                impulse = rng.nextFloat() * 0.25f + 0.95f,
                lateralOffset = laneOffset
            )
            val nearBoost = track.boostPads.any { distance(it.x, it.y, cand.x, cand.y) < 150f }
            val nearItem = track.itemSpawns.any { distance(it.x, it.y, cand.x, cand.y) < 120f }
            val nearRamp = chosen.any { distance(it.x, it.y, cand.x, cand.y) < minSpacing }
            if (!nearBoost && !nearItem && !nearRamp) {
                chosen.add(cand)
            }
        }
        return track.copy(ramps = chosen.ifEmpty { track.ramps })
    }

    fun defaultEditorOval(theme: TrackId = TrackId.NEON_ROOFTOP): CustomTrackData {
        val w = 110f
        val ctrl = listOf(
            TrackPoint(520f, 340f, w),
            TrackPoint(1100f, 290f, w),
            TrackPoint(1680f, 340f, w),
            TrackPoint(1860f, 750f, w),
            TrackPoint(1680f, 1160f, w),
            TrackPoint(1100f, 1210f, w),
            TrackPoint(520f, 1160f, w),
            TrackPoint(340f, 750f, w)
        )
        val spline = subdivideClosedSpline(ctrl, samplesPerSegment = 14)
        val randSuffix = Random.nextInt(1000, 9999)
        return CustomTrackData(
            id = "custom_${System.currentTimeMillis()}_$randSuffix",
            name = "Custom Circuit",
            themeId = theme.name,
            roadHalfWidth = w,
            controlPoints = ctrl,
            boostPads = listOf(boostAt(spline, 14), boostAt(spline, 68)),
            itemSpawns = listOf(0, 1, 2, 3).map { itemAt(spline, (it * spline.size / 4) + 18, it) },
            ramps = listOf(rampAt(spline, 34), rampAt(spline, 88)),
            props = emptyList()
        )
    }

    fun generateRandomTrack(
        seed: Long = System.currentTimeMillis(),
        preferredTheme: TrackId? = null,
        roadHalfWidth: Float = 112f,
        trackIdOverride: String? = null,
        trackNameOverride: String? = null,
        forceNight: Boolean? = null,
        forceRandomConditions: Boolean? = null,
        forceIncludeJumps: Boolean? = null
    ): CustomTrackData {
        val rng = kotlin.random.Random(seed)
        val isNightTrack = forceNight ?: (rng.nextFloat() < 0.35f)
        val includeJumps = forceIncludeJumps ?: true
        val theme = preferredTheme ?: TrackId.entries[rng.nextInt(TrackId.entries.size)]
        val dayAdjectives = listOf("Hyper", "Turbo", "Apex", "Drift", "Nitro", "Volt", "Micro", "Ridge")
        val nightAdjectives = listOf("Midnight", "Nocturne", "Shadow", "Eclipse", "Starlight", "Moonlit")
        val adjectives = if (isNightTrack) nightAdjectives else dayAdjectives
        val nouns = listOf("Circuit", "Loop", "Speedway", "Chicane", "Run", "Grand Prix", "Sprint")
        val suffixNum = ((seed and 0x7FFFFFFFL) % 90L + 10L)
        val trackName = trackNameOverride ?: "${adjectives[rng.nextInt(adjectives.size)]} ${nouns[rng.nextInt(nouns.size)]} #$suffixNum"

        val pointCount = rng.nextInt(9, 13)
        val cx = 1100f
        val cy = 750f
        val rxBase = 740f
        val ryBase = 460f
        val phase1 = rng.nextFloat() * 6.28f
        val phase2 = rng.nextFloat() * 6.28f
        val freq1 = rng.nextInt(2, 4)
        val freq2 = rng.nextInt(3, 6)

        val ctrl = (0 until pointCount).map { i ->
            val theta = (i.toFloat() / pointCount.toFloat()) * (2f * Math.PI.toFloat())
            val wave = 1f +
                0.22f * sin(theta * freq1 + phase1) +
                0.15f * cos(theta * freq2 + phase2) +
                (rng.nextFloat() - 0.5f) * 0.14f
            val rScale = wave.coerceIn(0.62f, 1.18f)
            val x = (cx + cos(theta) * rxBase * rScale).coerceIn(270f, 1930f)
            val y = (cy + sin(theta) * ryBase * rScale).coerceIn(240f, 1260f)
            TrackPoint(x, y, roadHalfWidth)
        }

        val spline = subdivideClosedSpline(ctrl, samplesPerSegment = 14)
        val n = spline.size
        val boostLanes = LANE_LATERAL_OFFSETS.shuffled(rng)
        val boosts = listOf(
            boostAt(spline, (n * 0.14f).toInt(), lateralOffset = boostLanes[0]),
            boostAt(spline, (n * 0.58f).toInt(), lateralOffset = boostLanes[1])
        )
        val items = listOf(0.24f, 0.46f, 0.72f, 0.90f).mapIndexed { id, frac ->
            itemAt(spline, (n * frac).toInt(), id, lateralFrac = rng.nextFloat() * 1.44f - 0.72f)
        }
        val rampFracs = listOf(0.34f, 0.66f, 0.80f).take(rng.nextInt(2, 4))
        val rampLanes = LANE_LATERAL_OFFSETS.shuffled(rng)
        val ramps = if (includeJumps) {
            rampFracs.mapIndexed { idx, frac ->
                rampAt(
                    spline = spline,
                    index = (n * frac).toInt(),
                    impulse = 1.0f + rng.nextFloat() * 0.15f,
                    lateralOffset = rampLanes[idx % rampLanes.size]
                )
            }
        } else {
            emptyList()
        }

        // Place safe decorative props away from the track centerline
        val props = mutableListOf<TrackProp>()
        var attempts = 0
        while (props.size < 5 && attempts < 80) {
            attempts++
            val px = rng.nextFloat() * 1900f + 150f
            val py = rng.nextFloat() * 1200f + 150f
            val minRoadDist = spline.minOf { distance(px, py, it.x, it.y) }
            if (minRoadDist > roadHalfWidth + 85f) {
                val shape = when (theme) {
                    TrackId.BACKYARD_DIRT -> PropShape.TREE
                    TrackId.SANDBOX_DUNES -> PropShape.CACTUS
                    TrackId.NEON_ROOFTOP -> PropShape.PYLON
                    TrackId.WAREHOUSE_DRIFT -> PropShape.CRATE
                    TrackId.LIVING_ROOM_GP -> PropShape.CIRCLE
                }
                props.add(
                    TrackProp(
                        x = px,
                        y = py,
                        radius = rng.nextFloat() * 18f + 38f,
                        shape = shape,
                        colorHex = theme.kerbPrimaryHex,
                        accentHex = theme.kerbSecondaryHex,
                        isSolidObstacle = false
                    )
                )
            }
        }

        val defaultId = "rand_${(seed and 0x7FFFFFFFFFFFFFFFL)}_${rng.nextInt(1000, 9999)}".take(36)
        return CustomTrackData(
            id = trackIdOverride ?: defaultId,
            name = trackName,
            themeId = theme.name,
            roadHalfWidth = roadHalfWidth,
            controlPoints = ctrl,
            boostPads = boosts,
            itemSpawns = items,
            ramps = ramps,
            props = props,
            isNightTrack = isNightTrack,
            randomConditionsEnabled = false
        )
    }

    fun rampAt(
        spline: List<TrackPoint>,
        index: Int,
        impulse: Float = 1.0f,
        lateralOffset: Float = 0f
    ): TrackRamp {
        val i = index.mod(spline.size)
        val next = (index + 1).mod(spline.size)
        val p = spline[i]
        val n = spline[next]
        val angle = atan2(n.y - p.y, n.x - p.x)
        val clampedLat = lateralOffset.coerceIn(-MAX_LATERAL_OFFSET, MAX_LATERAL_OFFSET)
        val perpX = -sin(angle)
        val perpY = cos(angle)
        val shift = clampedLat * p.halfWidth
        return TrackRamp(
            x = p.x + perpX * shift,
            y = p.y + perpY * shift,
            angleRad = angle,
            radius = 48f,
            impulse = impulse,
            lateralOffset = clampedLat
        )
    }

    fun boostAt(
        spline: List<TrackPoint>,
        index: Int,
        lateralOffset: Float = 0f
    ): BoostPad {
        val i = index.mod(spline.size)
        val next = (index + 1).mod(spline.size)
        val p = spline[i]
        val n = spline[next]
        val angle = atan2(n.y - p.y, n.x - p.x)
        val clampedLat = lateralOffset.coerceIn(-MAX_LATERAL_OFFSET, MAX_LATERAL_OFFSET)
        val perpX = -sin(angle)
        val perpY = cos(angle)
        val shift = clampedLat * p.halfWidth
        return BoostPad(
            x = p.x + perpX * shift,
            y = p.y + perpY * shift,
            angleRad = angle,
            lateralOffset = clampedLat
        )
    }

    fun itemAt(
        spline: List<TrackPoint>,
        index: Int,
        id: Int,
        lateralFrac: Float? = null
    ): ItemBoxSpawn {
        val i = index.mod(spline.size)
        val next = (index + 1).mod(spline.size)
        val p = spline[i]
        val n = spline[next]
        val angle = atan2(n.y - p.y, n.x - p.x)
        val perpX = -sin(angle)
        val perpY = cos(angle)
        val frac = lateralFrac ?: when (id % 5) {
            0 -> -0.58f
            1 -> 0.56f
            2 -> -0.34f
            3 -> 0.64f
            else -> -0.46f
        }
        val maxLat = (p.halfWidth - 24f).coerceAtLeast(18f)
        val offset = frac.coerceIn(-0.78f, 0.78f) * maxLat
        return ItemBoxSpawn(
            id = id,
            x = p.x + perpX * offset,
            y = p.y + perpY * offset
        )
    }

    fun subdivideClosedSpline(
        points: List<TrackPoint>,
        samplesPerSegment: Int
    ): List<TrackPoint> {
        val result = ArrayList<TrackPoint>(points.size * samplesPerSegment)
        val n = points.size
        for (i in 0 until n) {
            val p0 = points[(i - 1 + n) % n]
            val p1 = points[i]
            val p2 = points[(i + 1) % n]
            val p3 = points[(i + 2) % n]
            for (step in 0 until samplesPerSegment) {
                val t = step.toFloat() / samplesPerSegment.toFloat()
                val x = catmullRom(p0.x, p1.x, p2.x, p3.x, t)
                val y = catmullRom(p0.y, p1.y, p2.y, p3.y, t)
                val w = p1.halfWidth + (p2.halfWidth - p1.halfWidth) * t
                result.add(TrackPoint(x, y, w))
            }
        }
        return result
    }

    private fun catmullRom(p0: Float, p1: Float, p2: Float, p3: Float, t: Float): Float {
        val t2 = t * t
        val t3 = t2 * t
        return 0.5f * (
            (2f * p1) +
                (-p0 + p2) * t +
                (2f * p0 - 5f * p1 + 4f * p2 - p3) * t2 +
                (-p0 + 3f * p1 - 3f * p2 + p3) * t3
            )
    }

    /**
     * Returns (closestIndex, distanceToCenterline) for physics & checkpoint checks.
     */
    fun nearestTrackPoint(track: TrackDefinition, x: Float, y: Float): Pair<Int, Float> {
        var bestIdx = 0
        var bestDistSq = Float.MAX_VALUE
        val pts = track.centerline
        for (i in pts.indices) {
            val dx = x - pts[i].x
            val dy = y - pts[i].y
            val d2 = dx * dx + dy * dy
            if (d2 < bestDistSq) {
                bestDistSq = d2
                bestIdx = i
            }
        }
        return bestIdx to kotlin.math.sqrt(bestDistSq)
    }

    /**
     * Local-window spline lookup around hintIdx to prevent snapping onto an adjacent
     * track section on hairpins or custom figure-8 bends. Falls back globally if far.
     */
    fun nearestTrackPointLocal(
        track: TrackDefinition,
        x: Float,
        y: Float,
        hintIdx: Int,
        windowRadius: Int = 20
    ): Pair<Int, Float> {
        val pts = track.centerline
        if (pts.isEmpty()) return 0 to 0f
        val n = pts.size
        var bestIdx = hintIdx.mod(n)
        var bestDistSq = Float.MAX_VALUE
        for (offset in -windowRadius..windowRadius) {
            val idx = (hintIdx + offset).mod(n)
            val dx = x - pts[idx].x
            val dy = y - pts[idx].y
            val d2 = dx * dx + dy * dy
            if (d2 < bestDistSq) {
                bestDistSq = d2
                bestIdx = idx
            }
        }
        val localDist = kotlin.math.sqrt(bestDistSq)
        val fallbackThresh = (pts[bestIdx].halfWidth * 2.4f).coerceAtLeast(260f)
        return if (localDist <= fallbackThresh) {
            bestIdx to localDist
        } else {
            nearestTrackPoint(track, x, y)
        }
    }

    data class SegmentProjection(
        val index: Int,
        val closestX: Float,
        val closestY: Float,
        val halfWidth: Float,
        val distance: Float
    )

    /**
     * Projects (x, y) continuously onto closed centerline spline segments in a local window around
     * hintIdx (with global fallback) so road boundaries are smooth continuous curves on every corner.
     */
    fun nearestTrackSegmentLocal(
        track: TrackDefinition,
        x: Float,
        y: Float,
        hintIdx: Int,
        windowRadius: Int = 22
    ): SegmentProjection {
        val pts = track.centerline
        if (pts.isEmpty()) return SegmentProjection(0, x, y, 112f, 0f)
        val n = pts.size

        fun searchRange(indices: IntProgression): SegmentProjection {
            var bestIdx = hintIdx.mod(n)
            var bestX = pts[bestIdx].x
            var bestY = pts[bestIdx].y
            var bestHw = pts[bestIdx].halfWidth
            var bestDistSq = Float.MAX_VALUE
            for (raw in indices) {
                val i = raw.mod(n)
                val a = pts[i]
                val b = pts[(i + 1) % n]
                val abx = b.x - a.x
                val aby = b.y - a.y
                val lenSq = abx * abx + aby * aby
                val t = if (lenSq > 1e-4f) {
                    (((x - a.x) * abx + (y - a.y) * aby) / lenSq).coerceIn(0f, 1f)
                } else {
                    0f
                }
                val px = a.x + abx * t
                val py = a.y + aby * t
                val dx = x - px
                val dy = y - py
                val d2 = dx * dx + dy * dy
                if (d2 < bestDistSq) {
                    bestDistSq = d2
                    bestIdx = i
                    bestX = px
                    bestY = py
                    bestHw = a.halfWidth + (b.halfWidth - a.halfWidth) * t
                }
            }
            return SegmentProjection(bestIdx, bestX, bestY, bestHw, kotlin.math.sqrt(bestDistSq))
        }

        val local = searchRange((hintIdx - windowRadius)..(hintIdx + windowRadius))
        val fallbackThresh = (local.halfWidth * 2.4f).coerceAtLeast(260f)
        return if (local.distance <= fallbackThresh) {
            local
        } else {
            searchRange(0 until n)
        }
    }

    /**
     * Computes grid start positions behind the start/finish line (index 0) for circuits,
     * or evenly spaced around the central arena ring facing inward for Arena mode.
     */
    fun gridStartTransform(track: TrackDefinition, slotIndex: Int): Triple<Float, Float, Float> {
        if (track.isStunt) {
            return if (slotIndex <= 0) {
                Triple(230f, 700f, 0f)
            } else {
                Triple(240f + slotIndex * 85f, 320f, 0f)
            }
        }
        if (track.isArena) {
            val cx = track.worldWidth / 2f
            val cy = track.worldHeight / 2f
            val angleFromCenter = (PI / 4.0 + (slotIndex % 4) * (PI / 2.0)).toFloat()
            val spawnRadius = 205f
            val sx = cx + cos(angleFromCenter) * spawnRadius
            val sy = cy + sin(angleFromCenter) * spawnRadius
            val faceCenterAngle = atan2(cy - sy, cx - sx)
            return Triple(sx, sy, faceCenterAngle)
        }
        val pts = track.centerline
        val startIdx = (pts.size - 4 - (slotIndex / 2) * 3).mod(pts.size)
        val nextIdx = (startIdx + 1).mod(pts.size)
        val p = pts[startIdx]
        val n = pts[nextIdx]
        val angle = atan2(n.y - p.y, n.x - p.x)
        val perpX = -sin(angle)
        val perpY = cos(angle)
        val lateralOffset = if (slotIndex % 2 == 0) -34f else 34f
        return Triple(p.x + perpX * lateralOffset, p.y + perpY * lateralOffset, angle)
    }

    private fun buildArenaTrack(shape: ArenaShape): TrackDefinition {
        val worldW = 2200f
        val worldH = 1500f
        val cx = 1100f
        val cy = 750f
        val theme = shape.themeTrackId

        val boundary: List<TrackPoint> = when (shape) {
            ArenaShape.SQUARE -> {
                // Rounded-corner large rectangle (1580 x 1100)
                val halfW = 790f
                val halfH = 550f
                val cornerR = 130f
                val pts = mutableListOf<TrackPoint>()
                val corners = listOf(
                    Triple(cx + halfW - cornerR, cy - halfH + cornerR, -PI.toFloat() / 2f),
                    Triple(cx + halfW - cornerR, cy + halfH - cornerR, 0f),
                    Triple(cx - halfW + cornerR, cy + halfH - cornerR, PI.toFloat() / 2f),
                    Triple(cx - halfW + cornerR, cy - halfH + cornerR, PI.toFloat())
                )
                for (cIdx in corners.indices) {
                    val (arcCx, arcCy, startAng) = corners[cIdx]
                    val arcSteps = 12
                    for (s in 0..arcSteps) {
                        val a = startAng + (s.toFloat() / arcSteps) * (PI.toFloat() / 2f)
                        pts.add(TrackPoint(arcCx + cos(a) * cornerR, arcCy + sin(a) * cornerR, 120f))
                    }
                    // Add straight-edge intermediate samples so nearest-point / polygon edges are uniform
                    val nextCorner = corners[(cIdx + 1) % corners.size]
                    val endA = startAng + PI.toFloat() / 2f
                    val sx = arcCx + cos(endA) * cornerR
                    val sy = arcCy + sin(endA) * cornerR
                    val ex = nextCorner.first + cos(nextCorner.third) * cornerR
                    val ey = nextCorner.second + sin(nextCorner.third) * cornerR
                    for (edgeStep in 1..10) {
                        val t = edgeStep / 11f
                        pts.add(TrackPoint(sx + (ex - sx) * t, sy + (ey - sy) * t, 120f))
                    }
                }
                pts
            }

            ArenaShape.CIRCLE -> {
                val samples = 96
                (0 until samples).map { i ->
                    val a = (i.toFloat() / samples) * (2f * PI.toFloat())
                    TrackPoint(cx + cos(a) * 760f, cy + sin(a) * 560f, 120f)
                }
            }

            ArenaShape.STAR -> {
                // Smooth 5-pointed star with generous open center & smooth rounded star tips
                val samples = 120
                (0 until samples).map { i ->
                    val a = (i.toFloat() / samples) * (2f * PI.toFloat()) - PI.toFloat() / 2f
                    val r = 535f + 190f * cos(5f * (a + PI.toFloat() / 2f))
                    TrackPoint(cx + cos(a) * r * 1.16f, cy + sin(a) * r * 0.85f, 120f)
                }
            }

            ArenaShape.HEXAGON -> {
                val hexVertices = (0 until 6).map { i ->
                    val a = (i.toFloat() / 6f) * (2f * PI.toFloat())
                    TrackPoint(cx + cos(a) * 810f, cy + sin(a) * 575f, 120f)
                }
                val withMidpoints = mutableListOf<TrackPoint>()
                for (i in hexVertices.indices) {
                    val v1 = hexVertices[i]
                    val v2 = hexVertices[(i + 1) % hexVertices.size]
                    withMidpoints.add(v1)
                    withMidpoints.add(
                        TrackPoint(
                            x = v1.x * 0.67f + v2.x * 0.33f,
                            y = v1.y * 0.67f + v2.y * 0.33f,
                            halfWidth = 120f
                        )
                    )
                    withMidpoints.add(
                        TrackPoint(
                            x = v1.x * 0.33f + v2.x * 0.67f,
                            y = v1.y * 0.33f + v2.y * 0.67f,
                            halfWidth = 120f
                        )
                    )
                }
                subdivideClosedSpline(withMidpoints, samplesPerSegment = 6)
            }

            ArenaShape.CLOVER -> {
                // 4-winged Diamond Cross open arena
                val samples = 104
                (0 until samples).map { i ->
                    val a = (i.toFloat() / samples) * (2f * PI.toFloat())
                    val r = 555f + 165f * cos(4f * a)
                    TrackPoint(cx + cos(a) * r * 1.15f, cy + sin(a) * r * 0.85f, 120f)
                }
            }
        }

        val boosts: List<BoostPad> = when (shape) {
            ArenaShape.SQUARE -> listOf(
                BoostPad(cx - 480f, cy - 280f, 0f),
                BoostPad(cx + 480f, cy - 280f, PI.toFloat() / 2f),
                BoostPad(cx + 480f, cy + 280f, PI.toFloat()),
                BoostPad(cx - 480f, cy + 280f, -PI.toFloat() / 2f)
            )
            ArenaShape.CIRCLE -> (0 until 6).map { k ->
                val a = (k.toFloat() / 6f) * (2f * PI.toFloat())
                BoostPad(cx + cos(a) * 430f, cy + sin(a) * 320f, a + PI.toFloat() / 2f)
            }
            ArenaShape.STAR -> (0 until 5).map { k ->
                val a = -PI.toFloat() / 2f + (k.toFloat() / 5f) * (2f * PI.toFloat())
                val bx = cx + cos(a) * 510f
                val by = cy + sin(a) * 370f
                BoostPad(bx, by, atan2(cy - by, cx - bx))
            }
            ArenaShape.HEXAGON -> listOf(
                BoostPad(cx - 340f, cy, -PI.toFloat() / 2f),
                BoostPad(cx + 340f, cy, PI.toFloat() / 2f),
                BoostPad(cx, cy - 260f, 0f),
                BoostPad(cx, cy + 260f, PI.toFloat())
            )
            ArenaShape.CLOVER -> listOf(
                BoostPad(cx + 520f, cy, PI.toFloat()),
                BoostPad(cx - 520f, cy, 0f),
                BoostPad(cx, cy - 370f, PI.toFloat() / 2f),
                BoostPad(cx, cy + 370f, -PI.toFloat() / 2f)
            )
        }

        val items = (0 until 6).map { idx ->
            val a = (idx.toFloat() / 6f) * (2f * PI.toFloat()) + 0.35f
            ItemBoxSpawn(
                id = idx,
                x = cx + cos(a) * 310f,
                y = cy + sin(a) * 235f
            )
        }

        val defaultRamps: List<TrackRamp> = when (shape) {
            ArenaShape.SQUARE -> listOf(
                TrackRamp(cx - 240f, cy - 160f, 0.55f, radius = 48f, impulse = 1.05f),
                TrackRamp(cx + 240f, cy + 160f, -2.55f, radius = 48f, impulse = 1.05f)
            )
            ArenaShape.CIRCLE -> listOf(
                TrackRamp(cx - 220f, cy - 140f, 0.75f, radius = 48f, impulse = 1.10f),
                TrackRamp(cx + 220f, cy - 140f, 2.35f, radius = 48f, impulse = 1.10f),
                TrackRamp(cx, cy + 190f, -1.57f, radius = 48f, impulse = 1.10f)
            )
            ArenaShape.STAR -> listOf(
                TrackRamp(cx - 210f, cy - 150f, 0.65f, radius = 48f, impulse = 1.05f),
                TrackRamp(cx + 210f, cy + 150f, -2.45f, radius = 48f, impulse = 1.05f),
                TrackRamp(cx + 220f, cy - 155f, 2.35f, radius = 48f, impulse = 1.05f),
                TrackRamp(cx - 220f, cy + 155f, -0.75f, radius = 48f, impulse = 1.05f)
            )
            ArenaShape.HEXAGON -> listOf(
                TrackRamp(cx - 250f, cy - 150f, 0.55f, radius = 48f, impulse = 1.12f),
                TrackRamp(cx + 250f, cy + 150f, -2.55f, radius = 48f, impulse = 1.12f),
                TrackRamp(cx, cy - 190f, 1.57f, radius = 48f, impulse = 1.08f)
            )
            ArenaShape.CLOVER -> listOf(
                TrackRamp(cx - 210f, cy, 0f, radius = 48f, impulse = 1.10f),
                TrackRamp(cx + 210f, cy, PI.toFloat(), radius = 48f, impulse = 1.10f),
                TrackRamp(cx, cy - 165f, PI.toFloat() / 2f, radius = 48f, impulse = 1.05f),
                TrackRamp(cx, cy + 165f, -PI.toFloat() / 2f, radius = 48f, impulse = 1.05f)
            )
        }

        // First props are always non-solid oil/mud slicks (causes flag carriers to spin & drop the flag),
        // followed by shape-specific tactical obstacles inside the central zone.
        val props: List<TrackProp> = when (shape) {
            ArenaShape.SQUARE -> listOf(
                TrackProp(cx - 270f, cy + 165f, 44f, PropShape.MUD_POOL, 0xFF263238, 0xFF4FC3F7, isSolidObstacle = false),
                TrackProp(cx + 270f, cy - 165f, 44f, PropShape.MUD_POOL, 0xFF263238, 0xFF4FC3F7, isSolidObstacle = false),
                TrackProp(cx - 190f, cy, 34f, PropShape.CRATE, 0xFF455A64, 0xFFFFAB00, isSolidObstacle = true),
                TrackProp(cx + 190f, cy, 34f, PropShape.CRATE, 0xFF455A64, 0xFFFFAB00, isSolidObstacle = true)
            )
            ArenaShape.CIRCLE -> listOf(
                TrackProp(cx - 260f, cy + 165f, 44f, PropShape.MUD_POOL, 0xFF263238, 0xFF4FC3F7, isSolidObstacle = false),
                TrackProp(cx + 260f, cy - 165f, 44f, PropShape.MUD_POOL, 0xFF263238, 0xFF4FC3F7, isSolidObstacle = false)
            )
            ArenaShape.STAR -> listOf(
                TrackProp(cx - 250f, cy + 160f, 42f, PropShape.MUD_POOL, 0xFF263238, 0xFF4FC3F7, isSolidObstacle = false),
                TrackProp(cx + 250f, cy - 160f, 42f, PropShape.MUD_POOL, 0xFF263238, 0xFF4FC3F7, isSolidObstacle = false),
                TrackProp(cx, cy - 175f, 32f, PropShape.PYLON, 0xFFFF7043, 0xFFFFF8E1, isSolidObstacle = true)
            )
            ArenaShape.HEXAGON -> listOf(
                TrackProp(cx - 270f, cy, 48f, PropShape.MUD_POOL, 0xFF4E342E, 0xFF8D6E63, isSolidObstacle = false),
                TrackProp(cx + 270f, cy, 48f, PropShape.MUD_POOL, 0xFF4E342E, 0xFF8D6E63, isSolidObstacle = false),
                TrackProp(cx, cy + 175f, 34f, PropShape.TREE, 0xFF2E7D32, 0xFF81C784, isSolidObstacle = true)
            )
            ArenaShape.CLOVER -> listOf(
                TrackProp(cx - 250f, cy - 165f, 44f, PropShape.MUD_POOL, 0xFF0288D1, 0xFF81D4FA, isSolidObstacle = false),
                TrackProp(cx + 250f, cy + 165f, 44f, PropShape.MUD_POOL, 0xFF0288D1, 0xFF81D4FA, isSolidObstacle = false),
                TrackProp(cx - 185f, cy + 110f, 30f, PropShape.CACTUS, 0xFF2E7D32, 0xFFA5D6A7, isSolidObstacle = true),
                TrackProp(cx + 185f, cy - 110f, 30f, PropShape.CACTUS, 0xFF2E7D32, 0xFFA5D6A7, isSolidObstacle = true)
            )
        }

        return TrackDefinition(
            id = theme,
            worldWidth = worldW,
            worldHeight = worldH,
            centerline = boundary,
            boostPads = boosts,
            itemSpawns = items,
            props = props,
            ramps = defaultRamps,
            customName = shape.displayName,
            isArena = true,
            arenaShape = shape,
            arenaBoundary = boundary
        )
    }

    fun isPointInPolygon(x: Float, y: Float, poly: List<TrackPoint>): Boolean {
        if (poly.size < 3) return true
        var inside = false
        var j = poly.size - 1
        for (i in poly.indices) {
            val xi = poly[i].x
            val yi = poly[i].y
            val xj = poly[j].x
            val yj = poly[j].y
            val intersects = ((yi > y) != (yj > y)) &&
                (x < (xj - xi) * (y - yi) / ((yj - yi).let { if (kotlin.math.abs(it) < 1e-6f) 1e-6f else it }) + xi)
            if (intersects) inside = !inside
            j = i
        }
        return inside
    }

    /**
     * Returns (closestX, closestY, distanceToWall) along the closed arena boundary polygon.
     */
    fun closestPointOnPolygon(x: Float, y: Float, poly: List<TrackPoint>): Triple<Float, Float, Float> {
        if (poly.isEmpty()) return Triple(x, y, 0f)
        var bestX = poly[0].x
        var bestY = poly[0].y
        var bestDistSq = Float.MAX_VALUE
        val n = poly.size
        for (i in 0 until n) {
            val a = poly[i]
            val b = poly[(i + 1) % n]
            val abx = b.x - a.x
            val aby = b.y - a.y
            val lenSq = abx * abx + aby * aby
            val t = if (lenSq > 1e-4f) {
                (((x - a.x) * abx + (y - a.y) * aby) / lenSq).coerceIn(0f, 1f)
            } else {
                0f
            }
            val px = a.x + abx * t
            val py = a.y + aby * t
            val dx = x - px
            val dy = y - py
            val d2 = dx * dx + dy * dy
            if (d2 < bestDistSq) {
                bestDistSq = d2
                bestX = px
                bestY = py
            }
        }
        return Triple(bestX, bestY, kotlin.math.sqrt(bestDistSq))
    }

    fun arenaShrinkScaleForStage(stage: Int): Float = when {
        stage <= 0 -> 1.0f
        stage == 1 -> 0.78f
        stage == 2 -> 0.60f
        else -> 0.44f
    }

    fun scaledArenaPolygon(track: TrackDefinition, stage: Int): List<TrackPoint> {
        val base = track.arenaBoundary.ifEmpty { track.centerline }
        if (stage <= 0 || base.isEmpty()) return base
        val scale = arenaShrinkScaleForStage(stage)
        val cx = track.worldWidth * 0.5f
        val cy = track.worldHeight * 0.5f
        return base.map { pt ->
            TrackPoint(
                x = cx + (pt.x - cx) * scale,
                y = cy + (pt.y - cy) * scale,
                halfWidth = pt.halfWidth
            )
        }
    }

    fun sampleArenaInteriorPoint(
        track: TrackDefinition,
        rng: Random,
        minWallClearance: Float = 130f,
        avoidPoints: List<Pair<Float, Float>> = emptyList(),
        minAvoidDist: Float = 180f,
        boundaryOverride: List<TrackPoint>? = null
    ): Pair<Float, Float> {
        val poly = boundaryOverride ?: track.arenaBoundary.ifEmpty { track.centerline }
        repeat(120) {
            val px = rng.nextFloat() * (track.worldWidth - 520f) + 260f
            val py = rng.nextFloat() * (track.worldHeight - 400f) + 200f
            if (isPointInPolygon(px, py, poly)) {
                val (_, _, wallDist) = closestPointOnPolygon(px, py, poly)
                if (wallDist >= minWallClearance) {
                    val farEnough = avoidPoints.all { (ax, ay) -> distance(px, py, ax, ay) >= minAvoidDist }
                    if (farEnough) return px to py
                }
            }
        }
        // Fallback: safe offset inside center
        val angle = rng.nextFloat() * (2f * PI.toFloat())
        return (track.worldWidth / 2f + cos(angle) * 65f) to (track.worldHeight / 2f + sin(angle) * 50f)
    }

    fun sampleRandomItemBoxPoint(
        track: TrackDefinition,
        rng: Random,
        avoidPoints: List<Pair<Float, Float>> = emptyList(),
        boundaryOverride: List<TrackPoint>? = null
    ): Pair<Float, Float> {
        if (track.isArena) {
            return sampleArenaInteriorPoint(
                track = track,
                rng = rng,
                minWallClearance = 135f,
                avoidPoints = avoidPoints,
                minAvoidDist = 115f,
                boundaryOverride = boundaryOverride
            )
        }
        val pts = track.centerline
        if (pts.size < 16) return (track.worldWidth * 0.5f) to (track.worldHeight * 0.5f)
        val staticAvoid = buildList {
            addAll(avoidPoints)
            track.boostPads.forEach { add(it.x to it.y) }
            track.ramps.forEach { add(it.x to it.y) }
            track.props.filter { it.isSolidObstacle }.forEach { add(it.x to it.y) }
        }
        repeat(64) {
            val idx = rng.nextInt(6, (pts.size - 6).coerceAtLeast(7))
            val p = pts[idx]
            val n = pts[(idx + 1) % pts.size]
            val tangAng = atan2(n.y - p.y, n.x - p.x)
            val perpX = -sin(tangAng)
            val perpY = cos(tangAng)
            val maxLat = (p.halfWidth - 22f).coerceAtLeast(20f)
            // Sample across full road width, preferring non-centerline lateral offsets so boxes aren't lined up on center
            val sign = if (rng.nextBoolean()) 1f else -1f
            val mag = if (rng.nextFloat() < 0.84f) {
                0.24f + rng.nextFloat() * 0.52f
            } else {
                rng.nextFloat() * 0.24f
            }
            val latOffset = sign * mag * maxLat
            val cx = p.x + perpX * latOffset
            val cy = p.y + perpY * latOffset
            val clear = staticAvoid.all { (ax, ay) -> distance(cx, cy, ax, ay) >= 88f }
            if (clear) return cx to cy
        }
        val fallbackIdx = rng.nextInt(6, (pts.size - 6).coerceAtLeast(7))
        val fp = pts[fallbackIdx]
        val fn = pts[(fallbackIdx + 1) % pts.size]
        val fAng = atan2(fn.y - fp.y, fn.x - fp.x)
        val side = if (rng.nextBoolean()) 1f else -1f
        val off = side * (fp.halfWidth * 0.45f)
        return (fp.x + (-sin(fAng)) * off) to (fp.y + cos(fAng) * off)
    }

    fun generateRandomRunwayObstacles(rng: Random = Random.Default): List<TrackProp> {
        val count = rng.nextInt(4, 6) // 4..5 randomized lead-in obstacles
        val startX = 385f
        val endX = 930f
        val span = (endX - startX) / count.toFloat()
        val palette = listOf(
            Triple(PropShape.PYLON, 0xFFFF6D00L, 0xFFFFFFFFL),
            Triple(PropShape.CRATE, 0xFF455A64L, 0xFFFFAB00L),
            Triple(PropShape.PYLON, 0xFF00E5FFL, 0xFFFFFFFFL),
            Triple(PropShape.CRATE, 0xFF546E7AL, 0xFFFFD54FL),
            Triple(PropShape.PYLON, 0xFFE040FBL, 0xFFFFFFFFL)
        )
        val placed = mutableListOf<TrackProp>()
        for (i in 0 until count) {
            val zoneMinX = startX + i * span + 14f
            val zoneMaxX = (startX + (i + 1) * span - 14f).coerceAtLeast(zoneMinX + 4f)
            var chosenX = (zoneMinX + zoneMaxX) * 0.5f
            var chosenY = if (i % 2 == 0) 632f else 768f
            for (attempt in 0 until 45) {
                val candX = zoneMinX + rng.nextFloat() * (zoneMaxX - zoneMinX)
                val candY = 588f + rng.nextFloat() * 224f // 588f..812f inside 545..855 runway
                val clear = placed.all { prev ->
                    distance(candX, candY, prev.x, prev.y) >= 122f
                }
                if (clear) {
                    chosenX = candX
                    chosenY = candY
                    break
                }
            }
            val (shape, col, acc) = palette[(i + rng.nextInt(palette.size)) % palette.size]
            placed.add(
                TrackProp(
                    x = chosenX,
                    y = chosenY,
                    radius = 25f,
                    shape = shape,
                    colorHex = col,
                    accentHex = acc,
                    isSolidObstacle = true
                )
            )
        }
        return placed.sortedBy { it.x }
    }

    private fun buildStuntTrack(variant: StuntVariant): TrackDefinition {
        val worldW = 3200f
        val worldH = 1400f
        val theme = variant.themeTrackId
        val runwayPoints = (0..24).map { step ->
            val x = 160f + step * 50f // 160f..1360f shared runway
            TrackPoint(x = x, y = 700f, halfWidth = 155f)
        }
        // Gentle progressive boost runway before the mega ramp
        val boosts = listOf(
            BoostPad(x = 1005f, y = 700f, angleRad = 0f, radius = 48f),
            BoostPad(x = 1130f, y = 700f, angleRad = 0f, radius = 48f),
            BoostPad(x = 1250f, y = 700f, angleRad = 0f, radius = 50f)
        )
        val initialObstacles = generateRandomRunwayObstacles(Random(variant.ordinal * 97L + 13L))
        val megaRamp = listOf(
            TrackRamp(x = 1350f, y = 700f, angleRad = 0f, radius = 78f, impulse = 1.22f)
        )
        return TrackDefinition(
            id = theme,
            worldWidth = worldW,
            worldHeight = worldH,
            centerline = runwayPoints,
            boostPads = boosts,
            itemSpawns = emptyList(),
            props = initialObstacles,
            ramps = megaRamp,
            defaultLaps = 4,
            customName = variant.displayName,
            isStunt = true,
            stuntVariant = variant,
            randomConditionsEnabled = false
        )
    }

    fun defaultBowlingPins(): List<StuntPinState> {
        // 9 bowling pins in a 1-2-3-2-1 diamond formation centered at (2060, 700)
        val coords = listOf(
            1930f to 700f,
            1995f to 662f,
            1995f to 738f,
            2060f to 624f,
            2060f to 700f,
            2060f to 776f,
            2125f to 662f,
            2125f to 738f,
            2190f to 700f
        )
        return coords.mapIndexed { idx, (px, py) ->
            StuntPinState(id = idx, x = px, y = py, knocked = false)
        }
    }

    fun generateGolfHoles(rng: Random = Random.Default): List<StuntGolfHole> {
        // 6 holes with mixed bigger & smaller radii — smaller cups award bigger scores!
        val specs = listOf(
            84f to 200,
            74f to 300,
            64f to 450,
            54f to 600,
            44f to 800,
            36f to 1000
        )
        val holeNumbers = (1..6).shuffled(rng)
        val placed = mutableListOf<StuntGolfHole>()
        for (i in specs.indices) {
            val (radius, pts) = specs[i]
            val number = holeNumbers[i]
            var chosenX = 1720f + i * 195f
            var chosenY = if (i % 2 == 0) 500f else 900f
            for (attempt in 0 until 100) {
                val candX = rng.nextFloat() * 1140f + 1690f // 1690..2830 across expanded green
                val candY = rng.nextFloat() * 720f + 340f   // 340..1060
                val clear = placed.all { existing ->
                    distance(candX, candY, existing.x, existing.y) >= (existing.radius + radius + 42f)
                }
                if (clear) {
                    chosenX = candX
                    chosenY = candY
                    break
                }
            }
            placed.add(
                StuntGolfHole(
                    number = number,
                    x = chosenX,
                    y = chosenY,
                    radius = radius,
                    points = pts,
                    captured = false
                )
            )
        }
        return placed.sortedBy { it.number }
    }

    private fun getSplineArcTable(pts: List<TrackPoint>): SplineArcTable {
        splineTableCache[pts]?.let { return it }
        val n = pts.size
        val cum = FloatArray(n + 1)
        var acc = 0f
        for (i in 0 until n) {
            cum[i] = acc
            val a = pts[i]
            val b = pts[(i + 1) % n]
            acc += hypot(b.x - a.x, b.y - a.y)
        }
        cum[n] = acc
        val built = SplineArcTable(cum, acc)
        splineTableCache[pts] = built
        return built
    }

    /**
     * Projects world point (x, y) continuously onto the closed centerline spline,
     * returning cumulative arc-length `arcS`, signed lateral offset in world px
     * (negative = Left of travel direction, positive = Right), and local halfWidth.
     */
    fun projectOntoCenterline(pts: List<TrackPoint>, x: Float, y: Float): SplineProjection {
        val n = pts.size
        if (n < 2) {
            return SplineProjection(0f, 0f, 112f, x, y, 0f, 0f, 1f, 0f)
        }
        val table = getSplineArcTable(pts)
        var bestDistSq = Float.MAX_VALUE
        var bestArcS = 0f
        var bestLat = 0f
        var bestHw = pts[0].halfWidth
        var bestPx = pts[0].x
        var bestPy = pts[0].y
        var bestTangAng = 0f
        var bestNx = 0f
        var bestNy = 1f

        for (i in 0 until n) {
            val a = pts[i]
            val b = pts[(i + 1) % n]
            val abx = b.x - a.x
            val aby = b.y - a.y
            val lenSq = abx * abx + aby * aby
            val len = kotlin.math.sqrt(lenSq)
            val u = if (lenSq > 1e-4f) {
                (((x - a.x) * abx + (y - a.y) * aby) / lenSq).coerceIn(0f, 1f)
            } else {
                0f
            }
            val px = a.x + abx * u
            val py = a.y + aby * u
            val dx = x - px
            val dy = y - py
            val d2 = dx * dx + dy * dy
            if (d2 < bestDistSq) {
                bestDistSq = d2
                bestArcS = table.cumLen[i] + u * len
                val tx = if (len > 1e-4f) abx / len else 1f
                val ty = if (len > 1e-4f) aby / len else 0f
                val nx = -ty
                val ny = tx
                bestLat = dx * nx + dy * ny
                bestHw = a.halfWidth + (b.halfWidth - a.halfWidth) * u
                bestPx = px
                bestPy = py
                bestTangAng = atan2(ty, tx)
                bestNx = nx
                bestNy = ny
            }
        }
        return SplineProjection(
            arcS = bestArcS,
            lateralOffsetPx = bestLat,
            halfWidth = bestHw,
            closestX = bestPx,
            closestY = bestPy,
            tangentAngleRad = bestTangAng,
            normalX = bestNx,
            normalY = bestNy,
            totalLength = table.totalLength
        )
    }

    private fun rawPointAtArc(pts: List<TrackPoint>, table: SplineArcTable, rawArcS: Float): Triple<Float, Float, Float> {
        val total = table.totalLength
        if (total <= 1e-3f || pts.isEmpty()) return Triple(0f, 0f, 112f)
        val s = ((rawArcS % total) + total) % total
        var lo = 0
        var hi = pts.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (table.cumLen[mid] <= s) lo = mid else hi = mid - 1
        }
        val i = lo.coerceIn(0, pts.lastIndex)
        val segStart = table.cumLen[i]
        val segLen = (table.cumLen[i + 1] - segStart).coerceAtLeast(1e-4f)
        val u = ((s - segStart) / segLen).coerceIn(0f, 1f)
        val a = pts[i]
        val b = pts[(i + 1) % pts.size]
        return Triple(
            a.x + (b.x - a.x) * u,
            a.y + (b.y - a.y) * u,
            a.halfWidth + (b.halfWidth - a.halfWidth) * u
        )
    }

    fun sampleSplineAtArc(pts: List<TrackPoint>, rawArcS: Float): SplineSample {
        val table = getSplineArcTable(pts)
        val (x, y, hw) = rawPointAtArc(pts, table, rawArcS)
        val eps = 10f.coerceAtMost(table.totalLength * 0.05f).coerceAtLeast(1f)
        val (bx, by, _) = rawPointAtArc(pts, table, rawArcS - eps)
        val (fx, fy, _) = rawPointAtArc(pts, table, rawArcS + eps)
        val dx = fx - bx
        val dy = fy - by
        val len = hypot(dx, dy).coerceAtLeast(1e-4f)
        val tx = dx / len
        val ty = dy / len
        return SplineSample(
            x = x,
            y = y,
            normalX = -ty,
            normalY = tx,
            tangentAngleRad = atan2(ty, tx),
            halfWidth = hw
        )
    }

    /**
     * Repositions a Jump Ramp laterally across the track centerline (`-0.55f..+0.55f`).
     */
    fun repositionRampLaterally(
        track: TrackDefinition,
        ramp: TrackRamp,
        newLateralOffset: Float
    ): TrackRamp {
        if (track.isArena || track.centerline.size < 4) return ramp
        val proj = projectOntoCenterline(track.centerline, ramp.x, ramp.y)
        val clampedLat = newLateralOffset.coerceIn(-MAX_LATERAL_OFFSET, MAX_LATERAL_OFFSET)
        val shift = clampedLat * proj.halfWidth
        return ramp.copy(
            x = proj.closestX + proj.normalX * shift,
            y = proj.closestY + proj.normalY * shift,
            angleRad = proj.tangentAngleRad,
            lateralOffset = clampedLat
        )
    }

    /**
     * Repositions a Boost Pad laterally across the track centerline (`-0.55f..+0.55f`).
     */
    fun repositionBoostLaterally(
        track: TrackDefinition,
        pad: BoostPad,
        newLateralOffset: Float
    ): BoostPad {
        if (track.isArena || track.centerline.size < 4) return pad
        val proj = projectOntoCenterline(track.centerline, pad.x, pad.y)
        val clampedLat = newLateralOffset.coerceIn(-MAX_LATERAL_OFFSET, MAX_LATERAL_OFFSET)
        val shift = clampedLat * proj.halfWidth
        return pad.copy(
            x = proj.closestX + proj.normalX * shift,
            y = proj.closestY + proj.normalY * shift,
            angleRad = proj.tangentAngleRad,
            lateralOffset = clampedLat
        )
    }

    /**
     * Computes the spline-following full-width gravel trap paired immediately behind a non-Stunt
     * jump ramp, inset by [GRAVEL_EDGE_INSET] so track curb borders render cleanly with zero overlap.
     * Shared single source of truth for physics slowdown, dust particles, rendering, and Track Studio.
     */
    fun gravelPatchForRamp(track: TrackDefinition, ramp: TrackRamp): GravelPatch? {
        if (track.isStunt) return null
        if (!track.isArena && track.centerline.size >= 4) {
            val proj = projectOntoCenterline(track.centerline, ramp.x, ramp.y)
            val totalLen = proj.totalLength
            if (totalLen < 24f) return null
            val startS = proj.arcS + GRAVEL_START_OFFSET
            val endS = startS + GRAVEL_LENGTH
            val steps = 12
            val leftEdge = ArrayList<Pair<Float, Float>>(steps + 1)
            val rightEdge = ArrayList<Pair<Float, Float>>(steps + 1)
            for (k in 0..steps) {
                val frac = k.toFloat() / steps.toFloat()
                val sample = sampleSplineAtArc(track.centerline, startS + frac * GRAVEL_LENGTH)
                val insetHw = (sample.halfWidth - GRAVEL_EDGE_INSET).coerceAtLeast(16f)
                leftEdge.add((sample.x - sample.normalX * insetHw) to (sample.y - sample.normalY * insetHw))
                rightEdge.add((sample.x + sample.normalX * insetHw) to (sample.y + sample.normalY * insetHw))
            }
            val mid = sampleSplineAtArc(track.centerline, startS + GRAVEL_LENGTH * 0.5f)
            val midHw = (mid.halfWidth - GRAVEL_EDGE_INSET).coerceAtLeast(16f)
            return GravelPatch(
                x = mid.x,
                y = mid.y,
                angleRad = mid.tangentAngleRad,
                length = GRAVEL_LENGTH,
                halfWidth = midHw,
                leftEdge = leftEdge,
                rightEdge = rightEdge,
                startArcS = startS,
                endArcS = endS,
                totalSplineLength = totalLen,
                isSplineRibbon = true
            )
        }

        // Open arena fallback: straight ribbon behind the arena ramp
        val c = cos(ramp.angleRad)
        val s = sin(ramp.angleRad)
        val nx = -s
        val ny = c
        val halfW = ARENA_GRAVEL_HALF_WIDTH
        val steps = 4
        val leftEdge = ArrayList<Pair<Float, Float>>(steps + 1)
        val rightEdge = ArrayList<Pair<Float, Float>>(steps + 1)
        for (k in 0..steps) {
            val fwd = GRAVEL_START_OFFSET + (k.toFloat() / steps.toFloat()) * GRAVEL_LENGTH
            val cx = ramp.x + c * fwd
            val cy = ramp.y + s * fwd
            leftEdge.add((cx - nx * halfW) to (cy - ny * halfW))
            rightEdge.add((cx + nx * halfW) to (cy + ny * halfW))
        }
        val centerOffset = GRAVEL_START_OFFSET + GRAVEL_LENGTH * 0.5f
        return GravelPatch(
            x = ramp.x + c * centerOffset,
            y = ramp.y + s * centerOffset,
            angleRad = ramp.angleRad,
            length = GRAVEL_LENGTH,
            halfWidth = halfW,
            leftEdge = leftEdge,
            rightEdge = rightEdge,
            isSplineRibbon = false
        )
    }

    fun gravelPatchesForTrack(track: TrackDefinition): List<GravelPatch> {
        if (track.isStunt || track.ramps.isEmpty()) return emptyList()
        trackGravelCache[track]?.let { return it }
        val built = track.ramps.mapNotNull { gravelPatchForRamp(track, it) }
        trackGravelCache[track] = built
        return built
    }

    /**
     * Returns true when world point (x, y) lies inside the spline-bounded gravel ribbon
     * immediately behind any non-Stunt jump ramp (exact match with rendered curb-inset polygon).
     */
    fun isPointOnGravelPatch(track: TrackDefinition, x: Float, y: Float): Boolean {
        if (track.isStunt || track.ramps.isEmpty()) return false
        val patches = gravelPatchesForTrack(track)
        if (patches.isEmpty()) return false

        if (!track.isArena && track.centerline.size >= 4) {
            val proj = projectOntoCenterline(track.centerline, x, y)
            val maxLat = (proj.halfWidth - GRAVEL_EDGE_INSET).coerceAtLeast(16f)
            if (abs(proj.lateralOffsetPx) > maxLat) return false
            for (patch in patches) {
                val totalLen = patch.totalSplineLength
                if (totalLen <= 1e-3f) continue
                val deltaS = ((proj.arcS - patch.startArcS) % totalLen + totalLen) % totalLen
                if (deltaS <= patch.length) return true
            }
            return false
        }

        // Open arena gravel rectangle hit check
        val maxCheckDist = GRAVEL_START_OFFSET + GRAVEL_LENGTH + 160f
        for (ramp in track.ramps) {
            if (abs(x - ramp.x) > maxCheckDist || abs(y - ramp.y) > maxCheckDist) continue
            val c = cos(ramp.angleRad)
            val s = sin(ramp.angleRad)
            val dx = x - ramp.x
            val dy = y - ramp.y
            val localFwd = dx * c + dy * s
            if (localFwd < GRAVEL_START_OFFSET || localFwd > GRAVEL_START_OFFSET + GRAVEL_LENGTH) continue
            val localLat = -dx * s + dy * c
            if (abs(localLat) <= ARENA_GRAVEL_HALF_WIDTH) return true
        }
        return false
    }

    fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float = hypot(x2 - x1, y2 - y1)
}

package com.example.data

import com.example.model.ArenaShape
import com.example.model.BoostPad
import com.example.model.CustomTrackData
import com.example.model.ItemBoxSpawn
import com.example.model.PropShape
import com.example.model.TrackDefinition
import com.example.model.TrackId
import com.example.model.TrackPoint
import com.example.model.TrackProp
import com.example.model.TrackRamp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

object TrackCatalog {

    private val tracks: Map<TrackId, TrackDefinition> = TrackId.entries.associateWith { id ->
        buildTrack(id)
    }

    private val arenas: Map<ArenaShape, TrackDefinition> = ArenaShape.entries.associateWith { shape ->
        buildArenaTrack(shape)
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

    private fun buildTrack(id: TrackId): TrackDefinition {
        val worldW = 2200f
        val worldH = 1500f

        return when (id) {
            TrackId.LIVING_ROOM_GP -> {
                val ctrl = listOf(
                    TrackPoint(460f, 320f, 114f),
                    TrackPoint(1100f, 260f, 114f),
                    TrackPoint(1780f, 330f, 114f),
                    TrackPoint(1900f, 640f, 112f),
                    TrackPoint(1560f, 840f, 112f),
                    TrackPoint(1220f, 750f, 110f),
                    TrackPoint(1080f, 1080f, 112f),
                    TrackPoint(1620f, 1210f, 114f),
                    TrackPoint(1100f, 1280f, 114f),
                    TrackPoint(460f, 1210f, 114f),
                    TrackPoint(300f, 790f, 114f)
                )
                val spline = subdivideClosedSpline(ctrl, samplesPerSegment = 14)
                val boosts = listOf(
                    boostAt(spline, 14),
                    boostAt(spline, 96)
                )
                val items = listOf(
                    itemAt(spline, 25, 0),
                    itemAt(spline, 52, 1),
                    itemAt(spline, 82, 2),
                    itemAt(spline, 118, 3),
                    itemAt(spline, 138, 4)
                )
                val ramps = listOf(
                    rampAt(spline, 38),
                    rampAt(spline, 108)
                )
                val props = listOf(
                    TrackProp(780f, 620f, 62f, PropShape.CIRCLE, 0xFFE65100, 0xFFFFCC80, isSolidObstacle = false),
                    TrackProp(1480f, 540f, 44f, PropShape.CRATE, 0xFF8D6E63, 0xFFD7CCC8, isSolidObstacle = true),
                    TrackProp(640f, 920f, 48f, PropShape.CRATE, 0xFF546E7A, 0xFFB0BEC5, isSolidObstacle = true),
                    TrackProp(1520f, 1040f, 36f, PropShape.CIRCLE, 0xFF00897B, 0xFFB2DFDB, isSolidObstacle = true),
                    TrackProp(200f, 200f, 52f, PropShape.CRATE, 0xFF6D4C41, 0xFFBCAAA4, isSolidObstacle = false),
                    TrackProp(2010f, 1320f, 56f, PropShape.CIRCLE, 0xFFD84315, 0xFFFFAB91, isSolidObstacle = false)
                )
                TrackDefinition(id, worldW, worldH, spline, boosts, items, props, ramps = ramps)
            }

            TrackId.BACKYARD_DIRT -> {
                val ctrl = listOf(
                    TrackPoint(540f, 340f, 122f),
                    TrackPoint(1100f, 270f, 122f),
                    TrackPoint(1700f, 350f, 122f),
                    TrackPoint(1900f, 740f, 120f),
                    TrackPoint(1680f, 1170f, 122f),
                    TrackPoint(1140f, 1230f, 120f),
                    TrackPoint(760f, 1020f, 118f),
                    TrackPoint(460f, 1180f, 120f),
                    TrackPoint(290f, 760f, 122f)
                )
                val spline = subdivideClosedSpline(ctrl, samplesPerSegment = 16)
                val boosts = listOf(
                    boostAt(spline, 12),
                    boostAt(spline, 68)
                )
                val items = listOf(
                    itemAt(spline, 22, 0),
                    itemAt(spline, 48, 1),
                    itemAt(spline, 84, 2),
                    itemAt(spline, 108, 3),
                    itemAt(spline, 128, 4)
                )
                val ramps = listOf(
                    rampAt(spline, 34),
                    rampAt(spline, 96)
                )
                val props = listOf(
                    TrackProp(1060f, 680f, 68f, PropShape.TREE, 0xFF2E7D32, 0xFF81C784, isSolidObstacle = true),
                    TrackProp(1400f, 740f, 56f, PropShape.TREE, 0xFF388E3C, 0xFFA5D6A7, isSolidObstacle = true),
                    TrackProp(680f, 660f, 52f, PropShape.MUD_POOL, 0xFF4E342E, 0xFF795548, isSolidObstacle = false),
                    TrackProp(180f, 220f, 58f, PropShape.TREE, 0xFF1B5E20, 0xFF66BB6A, isSolidObstacle = false),
                    TrackProp(2020f, 240f, 54f, PropShape.TREE, 0xFF2E7D32, 0xFF81C784, isSolidObstacle = false),
                    TrackProp(1990f, 1290f, 60f, PropShape.TREE, 0xFF33691E, 0xFF9CCC65, isSolidObstacle = false)
                )
                TrackDefinition(id, worldW, worldH, spline, boosts, items, props, ramps = ramps)
            }

            TrackId.NEON_ROOFTOP -> {
                val ctrl = listOf(
                    TrackPoint(420f, 280f, 108f),
                    TrackPoint(1150f, 250f, 108f),
                    TrackPoint(1860f, 300f, 108f),
                    TrackPoint(1900f, 720f, 106f),
                    TrackPoint(1420f, 710f, 106f),
                    TrackPoint(1380f, 1140f, 108f),
                    TrackPoint(1840f, 1250f, 108f),
                    TrackPoint(1040f, 1290f, 108f),
                    TrackPoint(410f, 1200f, 108f),
                    TrackPoint(650f, 840f, 106f),
                    TrackPoint(310f, 600f, 108f)
                )
                val spline = subdivideClosedSpline(ctrl, samplesPerSegment = 14)
                val boosts = listOf(
                    boostAt(spline, 15),
                    boostAt(spline, 62),
                    boostAt(spline, 114)
                )
                val items = listOf(
                    itemAt(spline, 24, 0),
                    itemAt(spline, 46, 1),
                    itemAt(spline, 80, 2),
                    itemAt(spline, 104, 3),
                    itemAt(spline, 134, 4)
                )
                val ramps = listOf(
                    rampAt(spline, 34),
                    rampAt(spline, 92),
                    rampAt(spline, 124)
                )
                val props = listOf(
                    TrackProp(960f, 680f, 44f, PropShape.PYLON, 0xFF00E5FF, 0xFFE040FB, isSolidObstacle = true),
                    TrackProp(1700f, 960f, 40f, PropShape.PYLON, 0xFFE040FB, 0xFF00E5FF, isSolidObstacle = true),
                    TrackProp(160f, 1020f, 46f, PropShape.CRATE, 0xFF1A233A, 0xFF00E5FF, isSolidObstacle = false),
                    TrackProp(2050f, 150f, 44f, PropShape.CRATE, 0xFF1A233A, 0xFFE040FB, isSolidObstacle = false),
                    TrackProp(820f, 1040f, 36f, PropShape.PYLON, 0xFF00E5FF, 0xFFFFFFFF, isSolidObstacle = true)
                )
                TrackDefinition(id, worldW, worldH, spline, boosts, items, props, ramps = ramps)
            }

            TrackId.SANDBOX_DUNES -> {
                val ctrl = listOf(
                    TrackPoint(390f, 390f, 116f),
                    TrackPoint(920f, 250f, 116f),
                    TrackPoint(1420f, 450f, 116f),
                    TrackPoint(1860f, 340f, 116f),
                    TrackPoint(1930f, 830f, 116f),
                    TrackPoint(1500f, 1210f, 116f),
                    TrackPoint(960f, 1010f, 114f),
                    TrackPoint(560f, 1250f, 116f),
                    TrackPoint(270f, 940f, 116f)
                )
                val spline = subdivideClosedSpline(ctrl, samplesPerSegment = 16)
                val boosts = listOf(
                    boostAt(spline, 18),
                    boostAt(spline, 76)
                )
                val items = listOf(
                    itemAt(spline, 26, 0),
                    itemAt(spline, 52, 1),
                    itemAt(spline, 88, 2),
                    itemAt(spline, 112, 3),
                    itemAt(spline, 132, 4)
                )
                val ramps = listOf(
                    rampAt(spline, 40),
                    rampAt(spline, 100)
                )
                val props = listOf(
                    TrackProp(1040f, 680f, 52f, PropShape.MUD_POOL, 0xFF0288D1, 0xFF81D4FA, isSolidObstacle = false),
                    TrackProp(640f, 660f, 42f, PropShape.CACTUS, 0xFF2E7D32, 0xFFA5D6A7, isSolidObstacle = true),
                    TrackProp(1420f, 820f, 46f, PropShape.CACTUS, 0xFF388E3C, 0xFFC8E6C9, isSolidObstacle = true),
                    TrackProp(180f, 210f, 44f, PropShape.CACTUS, 0xFF2E7D32, 0xFFA5D6A7, isSolidObstacle = false),
                    TrackProp(2020f, 1300f, 50f, PropShape.CACTUS, 0xFF1B5E20, 0xFF81C784, isSolidObstacle = false)
                )
                TrackDefinition(id, worldW, worldH, spline, boosts, items, props, ramps = ramps)
            }

            TrackId.WAREHOUSE_DRIFT -> {
                val ctrl = listOf(
                    TrackPoint(380f, 290f, 108f),
                    TrackPoint(1100f, 280f, 108f),
                    TrackPoint(1860f, 290f, 108f),
                    TrackPoint(1880f, 610f, 106f),
                    TrackPoint(1180f, 620f, 106f),
                    TrackPoint(1180f, 960f, 106f),
                    TrackPoint(1860f, 970f, 106f),
                    TrackPoint(1840f, 1270f, 108f),
                    TrackPoint(960f, 1270f, 108f),
                    TrackPoint(360f, 1240f, 108f),
                    TrackPoint(340f, 760f, 108f)
                )
                val spline = subdivideClosedSpline(ctrl, samplesPerSegment = 14)
                val boosts = listOf(
                    boostAt(spline, 14),
                    boostAt(spline, 108)
                )
                val items = listOf(
                    itemAt(spline, 25, 0),
                    itemAt(spline, 48, 1),
                    itemAt(spline, 74, 2),
                    itemAt(spline, 98, 3),
                    itemAt(spline, 136, 4)
                )
                val ramps = listOf(
                    rampAt(spline, 36),
                    rampAt(spline, 122)
                )
                val props = listOf(
                    TrackProp(760f, 760f, 64f, PropShape.CRATE, 0xFF455A64, 0xFFFFAB00, isSolidObstacle = true),
                    TrackProp(1540f, 790f, 44f, PropShape.CRATE, 0xFF37474F, 0xFFFFAB00, isSolidObstacle = true),
                    TrackProp(760f, 530f, 42f, PropShape.PYLON, 0xFFFF6D00, 0xFFFFFFFF, isSolidObstacle = true),
                    TrackProp(760f, 1000f, 42f, PropShape.PYLON, 0xFFFF6D00, 0xFFFFFFFF, isSolidObstacle = true),
                    TrackProp(160f, 180f, 48f, PropShape.CRATE, 0xFF263238, 0xFF90A4AE, isSolidObstacle = false)
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
        val items = data.itemSpawns.ifEmpty {
            listOf(0.22f, 0.44f, 0.68f, 0.86f).mapIndexed { idx, frac ->
                itemAt(spline, (spline.size * frac).toInt(), idx)
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
            customId = data.id,
            customName = data.name
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
            props = track.props
        )
    }

    fun withRandomJumps(
        track: TrackDefinition,
        enabled: Boolean,
        seed: Long = System.currentTimeMillis()
    ): TrackDefinition {
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
        val minSpacing = 240f
        var attempts = 0
        while (chosen.size < targetCount && attempts < 60) {
            attempts++
            // Avoid start/finish line neighborhood (first 10% and last 12%)
            val idx = rng.nextInt((n * 0.12f).toInt(), (n * 0.88f).toInt())
            val cand = rampAt(track.centerline, idx, impulse = rng.nextFloat() * 0.25f + 0.95f)
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
        return CustomTrackData(
            id = "custom_${System.currentTimeMillis()}",
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
        roadHalfWidth: Float = 112f
    ): CustomTrackData {
        val rng = kotlin.random.Random(seed)
        val theme = preferredTheme ?: TrackId.entries[rng.nextInt(TrackId.entries.size)]
        val adjectives = listOf("Hyper", "Turbo", "Apex", "Drift", "Nitro", "Volt", "Micro", "Ridge")
        val nouns = listOf("Circuit", "Loop", "Speedway", "Chicane", "Run", "Grand Prix", "Sprint")
        val trackName = "${adjectives[rng.nextInt(adjectives.size)]} ${nouns[rng.nextInt(nouns.size)]} #${(seed % 90 + 10)}"

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
        val boosts = listOf(
            boostAt(spline, (n * 0.14f).toInt()),
            boostAt(spline, (n * 0.58f).toInt())
        )
        val items = listOf(0.24f, 0.46f, 0.72f, 0.90f).mapIndexed { id, frac ->
            itemAt(spline, (n * frac).toInt(), id)
        }
        val rampFracs = listOf(0.34f, 0.66f, 0.80f).take(rng.nextInt(2, 4))
        val ramps = rampFracs.map { frac ->
            rampAt(spline, (n * frac).toInt(), impulse = 1.0f + rng.nextFloat() * 0.15f)
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

        return CustomTrackData(
            id = "rand_$seed",
            name = trackName,
            themeId = theme.name,
            roadHalfWidth = roadHalfWidth,
            controlPoints = ctrl,
            boostPads = boosts,
            itemSpawns = items,
            ramps = ramps,
            props = props
        )
    }

    fun rampAt(spline: List<TrackPoint>, index: Int, impulse: Float = 1.0f): TrackRamp {
        val i = index.mod(spline.size)
        val next = (index + 1).mod(spline.size)
        val p = spline[i]
        val n = spline[next]
        val angle = atan2(n.y - p.y, n.x - p.x)
        return TrackRamp(p.x, p.y, angle, radius = 48f, impulse = impulse)
    }

    fun boostAt(spline: List<TrackPoint>, index: Int): BoostPad {
        val i = index.mod(spline.size)
        val next = (index + 1).mod(spline.size)
        val p = spline[i]
        val n = spline[next]
        val angle = atan2(n.y - p.y, n.x - p.x)
        return BoostPad(p.x, p.y, angle)
    }

    fun itemAt(spline: List<TrackPoint>, index: Int, id: Int): ItemBoxSpawn {
        val p = spline[index.mod(spline.size)]
        return ItemBoxSpawn(id = id, x = p.x, y = p.y)
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

    /**
     * Computes grid start positions behind the start/finish line (index 0) for circuits,
     * or evenly spaced around the central arena ring facing inward for Arena mode.
     */
    fun gridStartTransform(track: TrackDefinition, slotIndex: Int): Triple<Float, Float, Float> {
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

        val boosts = listOf(
            BoostPad(cx - 330f, cy, -PI.toFloat() / 2f),
            BoostPad(cx + 330f, cy, PI.toFloat() / 2f),
            BoostPad(cx, cy - 250f, 0f),
            BoostPad(cx, cy + 250f, PI.toFloat())
        )

        val items = (0 until 6).map { idx ->
            val a = (idx.toFloat() / 6f) * (2f * PI.toFloat()) + 0.35f
            ItemBoxSpawn(
                id = idx,
                x = cx + cos(a) * 310f,
                y = cy + sin(a) * 235f
            )
        }

        val defaultRamps = listOf(
            TrackRamp(cx - 210f, cy - 150f, 0.65f, radius = 48f, impulse = 1.05f),
            TrackRamp(cx + 210f, cy + 150f, -2.45f, radius = 48f, impulse = 1.05f),
            TrackRamp(cx + 220f, cy - 155f, 2.35f, radius = 48f, impulse = 1.0f)
        )

        // Two oil/puddle slick patches inside the arena (causing flag carriers to spin & drop the flag!)
        val props = listOf(
            TrackProp(
                x = cx - 260f,
                y = cy + 165f,
                radius = 44f,
                shape = PropShape.MUD_POOL,
                colorHex = 0xFF263238,
                accentHex = 0xFF4FC3F7,
                isSolidObstacle = false
            ),
            TrackProp(
                x = cx + 260f,
                y = cy - 165f,
                radius = 44f,
                shape = PropShape.MUD_POOL,
                colorHex = 0xFF263238,
                accentHex = 0xFF4FC3F7,
                isSolidObstacle = false
            )
        )

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

    fun sampleArenaInteriorPoint(
        track: TrackDefinition,
        rng: Random,
        minWallClearance: Float = 130f,
        avoidPoints: List<Pair<Float, Float>> = emptyList(),
        minAvoidDist: Float = 180f
    ): Pair<Float, Float> {
        val poly = track.arenaBoundary.ifEmpty { track.centerline }
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
        return (track.worldWidth / 2f + cos(angle) * 90f) to (track.worldHeight / 2f + sin(angle) * 70f)
    }

    fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float = hypot(x2 - x1, y2 - y1)
}

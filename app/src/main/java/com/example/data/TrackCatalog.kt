package com.example.data

import com.example.model.BoostPad
import com.example.model.CustomTrackData
import com.example.model.ItemBoxSpawn
import com.example.model.PropShape
import com.example.model.TrackDefinition
import com.example.model.TrackId
import com.example.model.TrackPoint
import com.example.model.TrackProp
import com.example.model.TrackRamp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

object TrackCatalog {

    private val tracks: Map<TrackId, TrackDefinition> = TrackId.entries.associateWith { id ->
        buildTrack(id)
    }

    fun getTrack(id: TrackId): TrackDefinition = tracks.getValue(id)

    fun getTrack(idName: String): TrackDefinition {
        val id = runCatching { TrackId.valueOf(idName) }.getOrDefault(TrackId.LIVING_ROOM_GP)
        return getTrack(id)
    }

    fun allTracks(): List<TrackDefinition> = TrackId.entries.map { getTrack(it) }

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
        val rng = kotlin.random.Random(seed)
        val n = track.centerline.size
        if (n < 40) return track
        val targetCount = rng.nextInt(2, 5) // 2..4 random jumps per race
        val chosen = mutableListOf<com.example.model.TrackRamp>()
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
     * Computes grid start positions behind the start/finish line (index 0).
     */
    fun gridStartTransform(track: TrackDefinition, slotIndex: Int): Triple<Float, Float, Float> {
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

    fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float = hypot(x2 - x1, y2 - y1)
}

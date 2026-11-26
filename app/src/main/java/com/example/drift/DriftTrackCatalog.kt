package com.example.drift

import com.example.data.TrackCatalog
import com.example.model.BoostPad
import com.example.model.CustomTrackData
import com.example.model.PropShape
import com.example.model.TrackDefinition
import com.example.model.TrackId
import com.example.model.TrackPoint
import com.example.model.TrackProp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Default & procedural Drift tracks designed around sweeping corners, hairpins,
 * zero powerups, and solid apex/chicane obstacles where drifting cleanly around
 * obstacles yields the fastest lap times.
 */
object DriftTrackCatalog {

    const val HAIRPIN_YARD_ID = "drift_hairpin_yard"
    const val NEON_SWITCHBACK_ID = "drift_neon_switchback"
    const val CANYON_SLALOM_ID = "drift_canyon_slalom"

    fun defaultDriftCustomTracks(): List<CustomTrackData> {
        if (!DriftFeature.ENABLED) return emptyList()
        return listOf(
            buildHairpinYard(),
            buildNeonSwitchback(),
            buildCanyonSlalom()
        )
    }

    fun defaultDriftTrackDefinitions(): List<TrackDefinition> {
        return defaultDriftCustomTracks().map { TrackCatalog.buildCustomTrack(it) }
    }

    fun resolveCustomTrackByKey(trackKey: String?): CustomTrackData? {
        if (!DriftFeature.ENABLED || trackKey.isNullOrBlank()) return null
        defaultDriftCustomTracks().firstOrNull { it.id == trackKey }?.let { return it }
        DriftDailyChallenge.extractDateKey(trackKey)?.let { dateKey ->
            return DriftDailyChallenge.dailyDriftCustomTrack(dateKey)
        }
        return null
    }

    fun resolveTrackDisplayName(trackKey: String?): String? {
        if (!DriftFeature.ENABLED || trackKey.isNullOrBlank()) return null
        defaultDriftCustomTracks().firstOrNull { it.id == trackKey }?.let { return it.name }
        DriftDailyChallenge.extractDateKey(trackKey)?.let { dateKey ->
            return "Daily Drift (${DriftDailyChallenge.displayDateLabel(dateKey)})"
        }
        return null
    }

    /**
     * Generates a random time-based Drift circuit:
     * - Wide sweeping corners and chicanes
     * - Solid apex/chicane obstacles that reward drifting cleanly around them
     * - Zero powerup item boxes, zero jump ramps (pure drift speed), exit boost pads
     */
    fun generateRandomDriftTrack(
        seed: Long = System.currentTimeMillis(),
        preferredTheme: TrackId? = null,
        roadHalfWidth: Float = 122f,
        trackIdOverride: String? = null,
        trackNameOverride: String? = null,
        forceNight: Boolean? = null,
        forceRandomConditions: Boolean? = null,
        includeJumps: Boolean = false
    ): CustomTrackData {
        val rng = Random(seed)
        val isNightTrack = forceNight ?: (rng.nextFloat() < 0.35f)
        val randomConditionsEnabled = forceRandomConditions ?: (rng.nextFloat() < 0.45f)
        val driftThemes = listOf(
            TrackId.WAREHOUSE_DRIFT,
            TrackId.NEON_ROOFTOP,
            TrackId.SANDBOX_DUNES,
            TrackId.LIVING_ROOM_GP
        )
        val theme = preferredTheme ?: driftThemes[rng.nextInt(driftThemes.size)]
        val dayAdjectives = listOf("Drift", "Slide", "Apex", "Tandem", "Hairpin", "Gymkhana", "Touge")
        val nightAdjectives = listOf("Midnight", "Shadow", "Nocturne", "Moonlit", "Eclipse")
        val adjectives = if (isNightTrack) nightAdjectives else dayAdjectives
        val nouns = listOf("Loop", "Switchback", "Slalom", "Yard", "Circuit", "Run")
        val suffixNum = ((seed and 0x7FFFFFFFL) % 90L + 10L)
        val trackName = trackNameOverride
            ?: "${adjectives[rng.nextInt(adjectives.size)]} ${nouns[rng.nextInt(nouns.size)]} #$suffixNum"

        val pointCount = rng.nextInt(10, 13)
        val cx = 1100f
        val cy = 750f
        val rxBase = 735f
        val ryBase = 465f
        val phase1 = rng.nextFloat() * 6.28f
        val phase2 = rng.nextFloat() * 6.28f
        val freq1 = rng.nextInt(2, 5)
        val freq2 = rng.nextInt(3, 6)

        val ctrl = (0 until pointCount).map { i ->
            val theta = (i.toFloat() / pointCount.toFloat()) * (2f * PI.toFloat())
            val wave = 1f +
                0.24f * sin(theta * freq1 + phase1) +
                0.17f * cos(theta * freq2 + phase2) +
                (rng.nextFloat() - 0.5f) * 0.12f
            val rScale = wave.coerceIn(0.60f, 1.18f)
            val x = (cx + cos(theta) * rxBase * rScale).coerceIn(265f, 1935f)
            val y = (cy + sin(theta) * ryBase * rScale).coerceIn(235f, 1265f)
            TrackPoint(x, y, roadHalfWidth)
        }

        val spline = TrackCatalog.subdivideClosedSpline(ctrl, samplesPerSegment = 14)
        val n = spline.size

        // Boost pads placed on corner exits so holding a drift onto the straight maximizes speed
        val boosts = listOf(
            TrackCatalog.boostAt(spline, (n * 0.16f).toInt()),
            TrackCatalog.boostAt(spline, (n * 0.62f).toInt())
        )

        // Solid corner/chicane obstacles placed away from start grid (avoid first 10% and last 12%)
        val obstacleFracs = listOf(0.18f, 0.31f, 0.45f, 0.58f, 0.72f, 0.84f)
        val props = mutableListOf<TrackProp>()
        obstacleFracs.forEachIndexed { idx, frac ->
            val sIdx = (n * frac).toInt().coerceIn(8, n - 10)
            val p = spline[sIdx]
            val next = spline[(sIdx + 1) % n]
            val tangAngle = atan2(next.y - p.y, next.x - p.x)
            val perpX = -sin(tangAngle)
            val perpY = cos(tangAngle)
            val side = if (idx % 2 == 0) -1f else 1f
            val lateralOffset = side * (p.halfWidth * (0.34f + rng.nextFloat() * 0.14f))
            val ox = p.x + perpX * lateralOffset
            val oy = p.y + perpY * lateralOffset

            val nearBoost = boosts.any { TrackCatalog.distance(ox, oy, it.x, it.y) < 125f }
            val nearExisting = props.any { TrackCatalog.distance(ox, oy, it.x, it.y) < 130f }
            if (!nearBoost && !nearExisting) {
                val shape = when {
                    theme == TrackId.SANDBOX_DUNES && idx % 3 == 0 -> PropShape.CACTUS
                    idx % 2 == 0 -> PropShape.PYLON
                    else -> PropShape.CRATE
                }
                props.add(
                    TrackProp(
                        x = ox,
                        y = oy,
                        radius = 20f,
                        shape = shape,
                        colorHex = if (shape == PropShape.CRATE) 0xFF37474FL else theme.kerbPrimaryHex,
                        accentHex = theme.kerbSecondaryHex,
                        isSolidObstacle = true
                    )
                )
            }
        }

        // Guarantee at least 4 solid drift obstacles even if boosts conflicted
        if (props.size < 4) {
            val fallbackIdx = (n * 0.38f).toInt()
            val fp = spline[fallbackIdx]
            props.add(
                TrackProp(
                    x = fp.x,
                    y = fp.y,
                    radius = 20f,
                    shape = PropShape.PYLON,
                    colorHex = theme.kerbPrimaryHex,
                    accentHex = theme.kerbSecondaryHex,
                    isSolidObstacle = true
                )
            )
        }

        val ramps = if (includeJumps) {
            val candidateFracs = listOf(0.25f, 0.52f, 0.78f)
            candidateFracs.mapNotNull { frac ->
                val cand = TrackCatalog.rampAt(spline, (n * frac).toInt(), impulse = 1.04f)
                val nearBoost = boosts.any { TrackCatalog.distance(cand.x, cand.y, it.x, it.y) < 120f }
                val nearProp = props.any { TrackCatalog.distance(cand.x, cand.y, it.x, it.y) < 95f }
                if (!nearBoost && !nearProp) cand else null
            }.take(2).ifEmpty {
                listOf(TrackCatalog.rampAt(spline, (n * 0.50f).toInt(), impulse = 1.04f))
            }
        } else {
            emptyList()
        }

        return CustomTrackData(
            id = trackIdOverride ?: "${DriftFeature.DRIFT_TRACK_PREFIX}rand_$seed",
            name = trackName,
            themeId = theme.name,
            roadHalfWidth = roadHalfWidth,
            controlPoints = ctrl,
            boostPads = boosts,
            itemSpawns = emptyList(),
            ramps = ramps,
            props = props,
            isNightTrack = isNightTrack,
            randomConditionsEnabled = randomConditionsEnabled
        )
    }

    private fun buildHairpinYard(): CustomTrackData {
        val hw = 122f
        val ctrl = listOf(
            TrackPoint(420f, 300f, hw),
            TrackPoint(1100f, 270f, hw),
            TrackPoint(1800f, 320f, hw),
            TrackPoint(1880f, 650f, hw),
            TrackPoint(1320f, 620f, hw),
            TrackPoint(1240f, 920f, hw),
            TrackPoint(1840f, 960f, hw),
            TrackPoint(1820f, 1240f, hw),
            TrackPoint(1160f, 1260f, hw),
            TrackPoint(580f, 1230f, hw),
            TrackPoint(310f, 960f, hw),
            TrackPoint(320f, 620f, hw)
        )
        val spline = TrackCatalog.subdivideClosedSpline(ctrl, samplesPerSegment = 14)
        val boosts = listOf(
            TrackCatalog.boostAt(spline, 14),
            TrackCatalog.boostAt(spline, 118)
        )
        val props = buildOnTrackObstacles(
            spline = spline,
            placements = listOf(
                Triple(0.19f, -0.36f, PropShape.PYLON),
                Triple(0.30f, 0.34f, PropShape.CRATE),
                Triple(0.42f, -0.38f, PropShape.PYLON),
                Triple(0.56f, 0.36f, PropShape.CRATE),
                Triple(0.71f, -0.35f, PropShape.PYLON),
                Triple(0.84f, 0.32f, PropShape.PYLON)
            ),
            primaryColor = TrackId.WAREHOUSE_DRIFT.kerbPrimaryHex,
            accentColor = TrackId.WAREHOUSE_DRIFT.kerbSecondaryHex
        )
        return CustomTrackData(
            id = HAIRPIN_YARD_ID,
            name = "Drift Hairpin Yard",
            themeId = TrackId.WAREHOUSE_DRIFT.name,
            roadHalfWidth = hw,
            controlPoints = ctrl,
            boostPads = boosts,
            itemSpawns = emptyList(),
            ramps = emptyList(),
            props = props
        )
    }

    private fun buildNeonSwitchback(): CustomTrackData {
        val hw = 120f
        val ctrl = listOf(
            TrackPoint(560f, 290f, hw),
            TrackPoint(1220f, 330f, hw),
            TrackPoint(1820f, 440f, hw),
            TrackPoint(1870f, 800f, hw),
            TrackPoint(1440f, 760f, hw),
            TrackPoint(1140f, 1080f, hw),
            TrackPoint(1680f, 1230f, hw),
            TrackPoint(980f, 1270f, hw),
            TrackPoint(360f, 1150f, hw),
            TrackPoint(290f, 760f, hw),
            TrackPoint(330f, 410f, hw)
        )
        val spline = TrackCatalog.subdivideClosedSpline(ctrl, samplesPerSegment = 14)
        val boosts = listOf(
            TrackCatalog.boostAt(spline, 10),
            TrackCatalog.boostAt(spline, 96)
        )
        val props = buildOnTrackObstacles(
            spline = spline,
            placements = listOf(
                Triple(0.18f, 0.36f, PropShape.PYLON),
                Triple(0.32f, -0.34f, PropShape.PYLON),
                Triple(0.47f, 0.36f, PropShape.CRATE),
                Triple(0.61f, -0.38f, PropShape.PYLON),
                Triple(0.76f, 0.34f, PropShape.PYLON),
                Triple(0.87f, -0.30f, PropShape.CRATE)
            ),
            primaryColor = TrackId.NEON_ROOFTOP.kerbPrimaryHex,
            accentColor = TrackId.NEON_ROOFTOP.kerbSecondaryHex
        )
        return CustomTrackData(
            id = NEON_SWITCHBACK_ID,
            name = "Neon Switchback Drift",
            themeId = TrackId.NEON_ROOFTOP.name,
            roadHalfWidth = hw,
            controlPoints = ctrl,
            boostPads = boosts,
            itemSpawns = emptyList(),
            ramps = emptyList(),
            props = props
        )
    }

    private fun buildCanyonSlalom(): CustomTrackData {
        val hw = 124f
        val ctrl = listOf(
            TrackPoint(1840f, 880f, hw),
            TrackPoint(1820f, 380f, hw),
            TrackPoint(1380f, 260f, hw),
            TrackPoint(1080f, 560f, hw),
            TrackPoint(640f, 270f, hw),
            TrackPoint(280f, 500f, hw),
            TrackPoint(300f, 980f, hw),
            TrackPoint(740f, 1250f, hw),
            TrackPoint(1100f, 940f, hw),
            TrackPoint(1540f, 1230f, hw)
        )
        val spline = TrackCatalog.subdivideClosedSpline(ctrl, samplesPerSegment = 14)
        val boosts = listOf(
            TrackCatalog.boostAt(spline, 12),
            TrackCatalog.boostAt(spline, 82)
        )
        val props = buildOnTrackObstacles(
            spline = spline,
            placements = listOf(
                Triple(0.20f, -0.35f, PropShape.CACTUS),
                Triple(0.33f, 0.36f, PropShape.PYLON),
                Triple(0.47f, -0.35f, PropShape.CACTUS),
                Triple(0.60f, 0.36f, PropShape.CRATE),
                Triple(0.73f, -0.34f, PropShape.CACTUS),
                Triple(0.85f, 0.35f, PropShape.PYLON)
            ),
            primaryColor = TrackId.SANDBOX_DUNES.kerbPrimaryHex,
            accentColor = TrackId.SANDBOX_DUNES.kerbSecondaryHex
        )
        return CustomTrackData(
            id = CANYON_SLALOM_ID,
            name = "Canyon Drift Slalom",
            themeId = TrackId.SANDBOX_DUNES.name,
            roadHalfWidth = hw,
            controlPoints = ctrl,
            boostPads = boosts,
            itemSpawns = emptyList(),
            ramps = emptyList(),
            props = props
        )
    }

    private fun buildOnTrackObstacles(
        spline: List<TrackPoint>,
        placements: List<Triple<Float, Float, PropShape>>,
        primaryColor: Long,
        accentColor: Long
    ): List<TrackProp> {
        val n = spline.size
        return placements.map { (frac, lateralFrac, shape) ->
            val idx = (n * frac).toInt().coerceIn(8, n - 10)
            val p = spline[idx]
            val next = spline[(idx + 1) % n]
            val ang = atan2(next.y - p.y, next.x - p.x)
            val perpX = -sin(ang)
            val perpY = cos(ang)
            val off = lateralFrac * p.halfWidth
            TrackProp(
                x = p.x + perpX * off,
                y = p.y + perpY * off,
                radius = 20f,
                shape = shape,
                colorHex = if (shape == PropShape.CRATE) 0xFF37474FL else primaryColor,
                accentHex = accentColor,
                isSolidObstacle = true
            )
        }
    }
}

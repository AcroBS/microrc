package com.example.data

import com.example.model.BoostPad
import com.example.model.ItemBoxSpawn
import com.example.model.PropShape
import com.example.model.TrackDefinition
import com.example.model.TrackId
import com.example.model.TrackPoint
import com.example.model.TrackProp
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
                val props = listOf(
                    TrackProp(780f, 620f, 62f, PropShape.CIRCLE, 0xFFE65100, 0xFFFFCC80, isSolidObstacle = false),
                    TrackProp(1480f, 540f, 44f, PropShape.CRATE, 0xFF8D6E63, 0xFFD7CCC8, isSolidObstacle = true),
                    TrackProp(640f, 920f, 48f, PropShape.CRATE, 0xFF546E7A, 0xFFB0BEC5, isSolidObstacle = true),
                    TrackProp(1520f, 1040f, 36f, PropShape.CIRCLE, 0xFF00897B, 0xFFB2DFDB, isSolidObstacle = true),
                    TrackProp(200f, 200f, 52f, PropShape.CRATE, 0xFF6D4C41, 0xFFBCAAA4, isSolidObstacle = false),
                    TrackProp(2010f, 1320f, 56f, PropShape.CIRCLE, 0xFFD84315, 0xFFFFAB91, isSolidObstacle = false)
                )
                TrackDefinition(id, worldW, worldH, spline, boosts, items, props)
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
                val props = listOf(
                    TrackProp(1060f, 680f, 68f, PropShape.TREE, 0xFF2E7D32, 0xFF81C784, isSolidObstacle = true),
                    TrackProp(1400f, 740f, 56f, PropShape.TREE, 0xFF388E3C, 0xFFA5D6A7, isSolidObstacle = true),
                    TrackProp(680f, 660f, 52f, PropShape.MUD_POOL, 0xFF4E342E, 0xFF795548, isSolidObstacle = false),
                    TrackProp(180f, 220f, 58f, PropShape.TREE, 0xFF1B5E20, 0xFF66BB6A, isSolidObstacle = false),
                    TrackProp(2020f, 240f, 54f, PropShape.TREE, 0xFF2E7D32, 0xFF81C784, isSolidObstacle = false),
                    TrackProp(1990f, 1290f, 60f, PropShape.TREE, 0xFF33691E, 0xFF9CCC65, isSolidObstacle = false)
                )
                TrackDefinition(id, worldW, worldH, spline, boosts, items, props)
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
                val props = listOf(
                    TrackProp(960f, 680f, 44f, PropShape.PYLON, 0xFF00E5FF, 0xFFE040FB, isSolidObstacle = true),
                    TrackProp(1700f, 960f, 40f, PropShape.PYLON, 0xFFE040FB, 0xFF00E5FF, isSolidObstacle = true),
                    TrackProp(160f, 1020f, 46f, PropShape.CRATE, 0xFF1A233A, 0xFF00E5FF, isSolidObstacle = false),
                    TrackProp(2050f, 150f, 44f, PropShape.CRATE, 0xFF1A233A, 0xFFE040FB, isSolidObstacle = false),
                    TrackProp(820f, 1040f, 36f, PropShape.PYLON, 0xFF00E5FF, 0xFFFFFFFF, isSolidObstacle = true)
                )
                TrackDefinition(id, worldW, worldH, spline, boosts, items, props)
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
                val props = listOf(
                    TrackProp(1040f, 680f, 52f, PropShape.MUD_POOL, 0xFF0288D1, 0xFF81D4FA, isSolidObstacle = false),
                    TrackProp(640f, 660f, 42f, PropShape.CACTUS, 0xFF2E7D32, 0xFFA5D6A7, isSolidObstacle = true),
                    TrackProp(1420f, 820f, 46f, PropShape.CACTUS, 0xFF388E3C, 0xFFC8E6C9, isSolidObstacle = true),
                    TrackProp(180f, 210f, 44f, PropShape.CACTUS, 0xFF2E7D32, 0xFFA5D6A7, isSolidObstacle = false),
                    TrackProp(2020f, 1300f, 50f, PropShape.CACTUS, 0xFF1B5E20, 0xFF81C784, isSolidObstacle = false)
                )
                TrackDefinition(id, worldW, worldH, spline, boosts, items, props)
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
                val props = listOf(
                    TrackProp(760f, 760f, 64f, PropShape.CRATE, 0xFF455A64, 0xFFFFAB00, isSolidObstacle = true),
                    TrackProp(1540f, 790f, 44f, PropShape.CRATE, 0xFF37474F, 0xFFFFAB00, isSolidObstacle = true),
                    TrackProp(760f, 530f, 42f, PropShape.PYLON, 0xFFFF6D00, 0xFFFFFFFF, isSolidObstacle = true),
                    TrackProp(760f, 1000f, 42f, PropShape.PYLON, 0xFFFF6D00, 0xFFFFFFFF, isSolidObstacle = true),
                    TrackProp(160f, 180f, 48f, PropShape.CRATE, 0xFF263238, 0xFF90A4AE, isSolidObstacle = false)
                )
                TrackDefinition(id, worldW, worldH, spline, boosts, items, props)
            }
        }
    }

    private fun boostAt(spline: List<TrackPoint>, index: Int): BoostPad {
        val i = index.mod(spline.size)
        val next = (index + 1).mod(spline.size)
        val p = spline[i]
        val n = spline[next]
        val angle = atan2(n.y - p.y, n.x - p.x)
        return BoostPad(p.x, p.y, angle)
    }

    private fun itemAt(spline: List<TrackPoint>, index: Int, id: Int): ItemBoxSpawn {
        val p = spline[index.mod(spline.size)]
        return ItemBoxSpawn(id = id, x = p.x, y = p.y)
    }

    private fun subdivideClosedSpline(
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

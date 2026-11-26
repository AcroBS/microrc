package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import com.squareup.moshi.JsonClass
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import kotlin.math.PI

// ============================================================================
// Firestore Cloud Models (1:1 Parity with firebase-blueprint.json & firestore.rules)
// Note: No `is`-prefixed Booleans; all properties have defaults; @DocumentId `id`
// is NEVER included in raw Map write payloads.
// ============================================================================

@Keep
@IgnoreExtraProperties
data class OnlineRoomDoc(
    @DocumentId val id: String = "",
    val hostUid: String = "",
    val hostName: String = "",
    val roomName: String = "",
    val roomCode: String = "",
    val discoverable: Boolean = true,
    val passwordProtected: Boolean = false,
    val passwordSalt: String = "",
    val passwordHash: String = "",
    val status: String = "OPEN",
    val gameMode: String = GameMode.RACE.name,
    val trackId: String = TrackId.LIVING_ROOM_GP.name,
    val playerCount: Long = 1L,
    val maxPlayers: Long = 4L,
    val memberUids: List<String> = emptyList(),
    val lobbyJson: String = "{}",
    val raceStartJson: String = "",
    val telemetrySeq: Long = 0L,
    val telemetryLine: String = "",
    val rematchJson: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class OnlineRoomPlayerDoc(
    @DocumentId val id: String = "",
    val roomId: String = "",
    val userId: String = "",
    val name: String = "",
    val carType: String = RcCarType.VOLT_BUGGY.name,
    val primaryColorArgb: Long = 0xFFFF5722,
    val accentColorArgb: Long = 0xFFFFD54F,
    val equippedUpgradesMask: Int = 0,
    val ready: Boolean = false,
    val host: Boolean = false,
    val rematchVote: String = "",
    val joinToken: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class OnlineRoomInputDoc(
    @DocumentId val id: String = "",
    val roomId: String = "",
    val userId: String = "",
    val seq: Long = 0L,
    val throttle: Double = 0.0,
    val steer: Double = 0.0,
    val powerUpSeq: Long = 0L,
    val mineDropSeq: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class OnlineRoomChatDoc(
    @DocumentId val id: String = "",
    val roomId: String = "",
    val senderUid: String = "",
    val senderName: String = "",
    val text: String = "",
    val systemMessage: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class LeaderboardEntryDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val driverName: String = "",
    val carType: String = RcCarType.VOLT_BUGGY.name,
    val primaryColorArgb: Long = 0xFFFF5722,
    val accentColorArgb: Long = 0xFFFFD54F,
    val equippedUpgradesMask: Int = 0,
    val gameMode: String = GameMode.RACE.name,
    val trackKey: String = TrackId.LIVING_ROOM_GP.name,
    val boardKey: String = "RACE_LIVING_ROOM_GP_3L",
    val totalLaps: Long = 3L,
    val bestRaceTimeMs: Long = 0L,
    val bestLapTimeMs: Long = 0L,
    val bestScore: Long = 0L,
    val hasGhost: Boolean = false,
    val ghostId: String = "",
    val publicEntry: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class GhostRunDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val driverName: String = "",
    val carType: String = RcCarType.VOLT_BUGGY.name,
    val primaryColorArgb: Long = 0xFFFF5722,
    val accentColorArgb: Long = 0xFFFFD54F,
    val equippedUpgradesMask: Int = 0,
    val boardKey: String = "RACE_LIVING_ROOM_GP_3L",
    val trackId: String = TrackId.LIVING_ROOM_GP.name,
    val totalLaps: Long = 3L,
    val finishTimeMs: Long = 1L,
    val bestLapMs: Long = 0L,
    val framesData: String = "",
    val publicGhost: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CustomTrackDoc(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val ownerName: String = "",
    val trackName: String = "",
    val themeId: String = TrackId.NEON_ROOFTOP.name,
    val roadHalfWidth: Double = 112.0,
    val payloadData: String = "",
    val publicTrack: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

// ============================================================================
// Online Race Start Metadata Payload (stored in OnlineRoomDoc.raceStartJson)
// ============================================================================

@JsonClass(generateAdapter = true)
data class OnlineRaceStartPayload(
    val matchSeq: Long = 1L,
    val rules: RaceRulesSettings = RaceRulesSettings(),
    val trackId: String = TrackId.LIVING_ROOM_GP.name,
    val arenaShape: String? = null,
    val stuntVariant: String? = null,
    val stuntSequence: List<String>? = null,
    val customTrack: CustomTrackData? = null,
    val ramps: List<TrackRamp> = emptyList(),
    val initialSnapshot: RaceSnapshot? = null
)

// ============================================================================
// Offline / Local Leaderboard & Ghost Car Playback Models
// ============================================================================

@JsonClass(generateAdapter = true)
data class LocalLeaderboardRecord(
    val boardKey: String,
    val gameMode: String,
    val trackKey: String,
    val totalLaps: Int = 3,
    val driverName: String,
    val carType: String,
    val primaryColorArgb: Long,
    val accentColorArgb: Long,
    val equippedUpgradesMask: Int = 0,
    val bestRaceTimeMs: Long = 0L,
    val bestLapTimeMs: Long = 0L,
    val bestScore: Int = 0,
    val recordedAtMs: Long = 0L,
    val ghostFramesData: String = ""
)

data class GhostFrame(
    val tMs: Long,
    val x: Float,
    val y: Float,
    val angleRad: Float,
    val jumpZ: Float,
    val progress: Float = 0f
)

data class GhostRunPlayback(
    val ghostId: String,
    val driverName: String,
    val carType: String,
    val primaryColorArgb: Long,
    val accentColorArgb: Long,
    val equippedUpgradesMask: Int = 0,
    val boardKey: String,
    val trackId: String,
    val totalLaps: Int,
    val finishTimeMs: Long,
    val bestLapMs: Long,
    val frames: List<GhostFrame>
)

data class ActiveGhostCarState(
    val driverName: String,
    val carType: String,
    val primaryColorArgb: Long,
    val accentColorArgb: Long,
    val equippedUpgradesMask: Int = 0,
    val finishTimeMs: Long,
    val bestLapMs: Long,
    val x: Float,
    val y: Float,
    val angleRad: Float,
    val jumpZ: Float,
    val finished: Boolean = false,
    val splitDeltaMs: Long? = null
)

// ============================================================================
// Security Helpers: Password Hashing & Sanitized Board/Document Keys
// ============================================================================

object OnlinePasswordHasher {
    private val secureRandom = SecureRandom()
    private const val ROOM_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    fun generateSalt(): String {
        val bytes = ByteArray(8)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun hashPassword(salt: String, rawPassword: String): String {
        val normalized = "$salt:${rawPassword.trim()}"
        val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun generateRoomCode(length: Int = 6): String {
        val sb = StringBuilder(length)
        repeat(length) {
            sb.append(ROOM_CODE_ALPHABET[secureRandom.nextInt(ROOM_CODE_ALPHABET.length)])
        }
        return sb.toString()
    }
}

object LeaderboardBoardKey {
    fun sanitizeKeyToken(raw: String, maxLen: Int = 40): String {
        return raw.trim()
            .replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            .take(maxLen)
            .ifEmpty { "DEFAULT" }
    }

    fun build(
        gameMode: GameMode,
        trackKey: String,
        totalLaps: Int = 3,
        arenaMinutes: Int = 2
    ): String {
        val safeTrack = sanitizeKeyToken(trackKey, 36)
        return when (gameMode) {
            GameMode.STUNTS -> "STUNTS_GAUNTLET_4R"
            GameMode.ARENA -> "ARENA_${safeTrack}_${arenaMinutes.coerceIn(1, 10)}M"
            GameMode.DRIFT -> "DRIFT_${safeTrack}_${totalLaps.coerceIn(1, 9)}L"
            else -> "RACE_${safeTrack}_${totalLaps.coerceIn(1, 9)}L"
        }
    }

    fun buildUserBoardDocId(uid: String, boardKey: String): String {
        val safeUid = sanitizeKeyToken(uid, 56)
        val safeBoard = sanitizeKeyToken(boardKey, 64)
        return "${safeUid}_${safeBoard}".take(126)
    }
}

object GhostCodec {
    private const val MAX_ENCODED_FRAMES = 900

    fun encodeFrames(frames: List<GhostFrame>): String {
        if (frames.isEmpty()) return ""
        val sampled = if (frames.size <= MAX_ENCODED_FRAMES) {
            frames
        } else {
            val step = frames.size.toFloat() / MAX_ENCODED_FRAMES.toFloat()
            buildList(MAX_ENCODED_FRAMES + 1) {
                for (i in 0 until MAX_ENCODED_FRAMES - 1) {
                    val idx = (i * step).toInt().coerceIn(0, frames.lastIndex)
                    add(frames[idx])
                }
                add(frames.last())
            }
        }
        val sb = StringBuilder(sampled.size * 30)
        sampled.forEachIndexed { idx, f ->
            if (idx > 0) sb.append(';')
            sb.append(f.tMs).append(',')
                .append(String.format(Locale.US, "%.1f", f.x)).append(',')
                .append(String.format(Locale.US, "%.1f", f.y)).append(',')
                .append(String.format(Locale.US, "%.3f", f.angleRad)).append(',')
                .append(String.format(Locale.US, "%.2f", f.jumpZ)).append(',')
                .append(String.format(Locale.US, "%.3f", f.progress))
        }
        return sb.toString()
    }

    fun decodeFrames(raw: String): List<GhostFrame> {
        if (raw.isBlank()) return emptyList()
        return raw.split(';').mapNotNull { entry ->
            val p = entry.split(',')
            if (p.size < 5) return@mapNotNull null
            val tMs = p[0].toLongOrNull() ?: return@mapNotNull null
            val x = p[1].toFloatOrNull() ?: return@mapNotNull null
            val y = p[2].toFloatOrNull() ?: return@mapNotNull null
            val ang = p[3].toFloatOrNull() ?: 0f
            val jz = p[4].toFloatOrNull() ?: 0f
            val prog = p.getOrNull(5)?.toFloatOrNull() ?: 0f
            GhostFrame(
                tMs = tMs,
                x = x,
                y = y,
                angleRad = ang,
                jumpZ = jz,
                progress = prog
            )
        }
    }

    fun interpolateAt(frames: List<GhostFrame>, raceTimeMs: Long): GhostFrame? {
        if (frames.isEmpty()) return null
        if (raceTimeMs <= frames.first().tMs) return frames.first()
        if (raceTimeMs >= frames.last().tMs) return frames.last()

        // Binary search for surrounding keyframes
        var lo = 0
        var hi = frames.lastIndex
        while (lo + 1 < hi) {
            val mid = (lo + hi) ushr 1
            if (frames[mid].tMs <= raceTimeMs) {
                lo = mid
            } else {
                hi = mid
            }
        }
        val a = frames[lo]
        val b = frames[hi]
        val span = (b.tMs - a.tMs).coerceAtLeast(1L).toFloat()
        val frac = ((raceTimeMs - a.tMs).toFloat() / span).coerceIn(0f, 1f)

        val angDiff = normalizeAngleRad(b.angleRad - a.angleRad)
        return GhostFrame(
            tMs = raceTimeMs,
            x = a.x + (b.x - a.x) * frac,
            y = a.y + (b.y - a.y) * frac,
            angleRad = normalizeAngleRad(a.angleRad + angDiff * frac),
            jumpZ = a.jumpZ + (b.jumpZ - a.jumpZ) * frac,
            progress = a.progress + (b.progress - a.progress) * frac
        )
    }

    /**
     * Computes live split delta in milliseconds:
     * negative = local driver is AHEAD of the ghost at the same track progress;
     * positive = local driver is BEHIND the ghost.
     */
    fun computeSplitDeltaMs(
        frames: List<GhostFrame>,
        playerProgress: Float,
        playerRaceTimeMs: Long
    ): Long? {
        if (frames.size < 2 || playerProgress <= 0.03f) return null
        val maxGhostProg = frames.last().progress
        if (maxGhostProg <= 0.05f) return null
        val clampedProg = playerProgress.coerceAtMost(maxGhostProg)

        for (i in 1 until frames.size) {
            val prev = frames[i - 1]
            val cur = frames[i]
            if (cur.progress >= clampedProg && cur.progress >= prev.progress) {
                val pSpan = (cur.progress - prev.progress).coerceAtLeast(0.0001f)
                val frac = ((clampedProg - prev.progress) / pSpan).coerceIn(0f, 1f)
                val ghostTimeAtProg = prev.tMs + ((cur.tMs - prev.tMs) * frac).toLong()
                return playerRaceTimeMs - ghostTimeAtProg
            }
        }
        return null
    }

    private fun normalizeAngleRad(angle: Float): Float {
        var a = angle
        val twoPi = (2.0 * PI).toFloat()
        val pi = PI.toFloat()
        while (a > pi) a -= twoPi
        while (a < -pi) a += twoPi
        return a
    }
}

object CustomTrackCodec {
    private const val VERSION_PREFIX = "V1"
    private const val MAX_POINTS = 64
    private const val MAX_BOOSTS = 24
    private const val MAX_ITEMS = 24
    private const val MAX_RAMPS = 24
    private const val MAX_PROPS = 48
    private const val MAX_PUDDLES = 24

    fun encode(track: CustomTrackData): String {
        val hw = track.roadHalfWidth.coerceIn(80f, 150f)
        val safePoints = if (track.controlPoints.size >= 4) {
            track.controlPoints.take(MAX_POINTS)
        } else {
            listOf(
                TrackPoint(520f, 340f, hw),
                TrackPoint(1100f, 290f, hw),
                TrackPoint(1680f, 340f, hw),
                TrackPoint(1860f, 750f, hw)
            )
        }
        val ptsStr = safePoints.joinToString(";") { p ->
            String.format(Locale.US, "%.1f,%.1f,%.1f", p.x, p.y, p.halfWidth.coerceIn(80f, 150f))
        }
        val boostsStr = track.boostPads.take(MAX_BOOSTS).joinToString(";") { b ->
            String.format(
                Locale.US,
                "%.1f,%.1f,%.3f,%.1f,%.2f",
                b.x,
                b.y,
                b.angleRad,
                b.radius,
                b.lateralOffset.coerceIn(-0.55f, 0.55f)
            )
        }
        val itemsStr = track.itemSpawns.take(MAX_ITEMS).joinToString(";") { item ->
            String.format(Locale.US, "%d,%.1f,%.1f", item.id, item.x, item.y)
        }
        val rampsStr = track.ramps.take(MAX_RAMPS).joinToString(";") { r ->
            String.format(
                Locale.US,
                "%.1f,%.1f,%.3f,%.1f,%.2f,%.2f",
                r.x,
                r.y,
                r.angleRad,
                r.radius,
                r.impulse,
                r.lateralOffset.coerceIn(-0.55f, 0.55f)
            )
        }
        val propsStr = track.props.take(MAX_PROPS).joinToString(";") { pr ->
            String.format(
                Locale.US,
                "%.1f,%.1f,%.1f,%s,%d,%d,%d",
                pr.x,
                pr.y,
                pr.radius,
                pr.shape.name,
                pr.colorHex,
                pr.accentHex,
                if (pr.isSolidObstacle) 1 else 0
            )
        }
        val nightFlag = if (track.isNightTrack) "1" else "0"
        val conditionsFlag = "0"
        val puddlesStr = track.puddles.take(MAX_PUDDLES).joinToString(";") { pd ->
            String.format(Locale.US, "%.1f,%.1f,%.1f", pd.x, pd.y, pd.radius.coerceIn(18f, 76f))
        }
        return "$VERSION_PREFIX|$ptsStr|$boostsStr|$itemsStr|$rampsStr|$propsStr|$nightFlag|$conditionsFlag|$puddlesStr".take(24_500)
    }

    fun decode(
        id: String,
        name: String,
        themeId: String,
        roadHalfWidth: Float,
        creatorUid: String = "",
        creatorName: String = "",
        raw: String
    ): CustomTrackData? {
        if (id.isBlank() || raw.length < 10) return null
        val sections = raw.split('|')
        if (sections.size < 2 || sections[0] != VERSION_PREFIX) return null

        val safeHw = roadHalfWidth.coerceIn(80f, 150f)
        val safeTheme = TrackId.entries.firstOrNull { it.name == themeId }?.name ?: TrackId.NEON_ROOFTOP.name

        val points = sections.getOrNull(1).orEmpty()
            .split(';')
            .filter { it.isNotBlank() }
            .mapNotNull { token ->
                val p = token.split(',')
                if (p.size < 2) return@mapNotNull null
                val x = p[0].toFloatOrNull()?.coerceIn(80f, 2120f) ?: return@mapNotNull null
                val y = p[1].toFloatOrNull()?.coerceIn(80f, 1420f) ?: return@mapNotNull null
                val hw = p.getOrNull(2)?.toFloatOrNull()?.coerceIn(80f, 150f) ?: safeHw
                TrackPoint(x = x, y = y, halfWidth = hw)
            }
        if (points.size < 4) return null

        val boosts = sections.getOrNull(2).orEmpty()
            .split(';')
            .filter { it.isNotBlank() }
            .mapNotNull { token ->
                val p = token.split(',')
                if (p.size < 3) return@mapNotNull null
                val x = p[0].toFloatOrNull() ?: return@mapNotNull null
                val y = p[1].toFloatOrNull() ?: return@mapNotNull null
                val ang = p[2].toFloatOrNull() ?: 0f
                val rad = p.getOrNull(3)?.toFloatOrNull()?.coerceIn(20f, 90f) ?: 44f
                val lat = p.getOrNull(4)?.toFloatOrNull()?.coerceIn(-0.55f, 0.55f) ?: 0f
                BoostPad(x = x, y = y, angleRad = ang, radius = rad, lateralOffset = lat)
            }

        val items = sections.getOrNull(3).orEmpty()
            .split(';')
            .filter { it.isNotBlank() }
            .mapIndexedNotNull { idx, token ->
                val p = token.split(',')
                if (p.size < 3) return@mapIndexedNotNull null
                val itemId = p[0].toIntOrNull() ?: idx
                val x = p[1].toFloatOrNull() ?: return@mapIndexedNotNull null
                val y = p[2].toFloatOrNull() ?: return@mapIndexedNotNull null
                ItemBoxSpawn(id = itemId, x = x, y = y)
            }

        val ramps = sections.getOrNull(4).orEmpty()
            .split(';')
            .filter { it.isNotBlank() }
            .mapNotNull { token ->
                val p = token.split(',')
                if (p.size < 3) return@mapNotNull null
                val x = p[0].toFloatOrNull() ?: return@mapNotNull null
                val y = p[1].toFloatOrNull() ?: return@mapNotNull null
                val ang = p[2].toFloatOrNull() ?: 0f
                val rad = p.getOrNull(3)?.toFloatOrNull()?.coerceIn(24f, 96f) ?: 48f
                val imp = p.getOrNull(4)?.toFloatOrNull()?.coerceIn(0.5f, 1.8f) ?: 1.0f
                val lat = p.getOrNull(5)?.toFloatOrNull()?.coerceIn(-0.55f, 0.55f) ?: 0f
                TrackRamp(x = x, y = y, angleRad = ang, radius = rad, impulse = imp, lateralOffset = lat)
            }

        val props = sections.getOrNull(5).orEmpty()
            .split(';')
            .filter { it.isNotBlank() }
            .mapNotNull { token ->
                val p = token.split(',')
                if (p.size < 6) return@mapNotNull null
                val x = p[0].toFloatOrNull() ?: return@mapNotNull null
                val y = p[1].toFloatOrNull() ?: return@mapNotNull null
                val rad = p[2].toFloatOrNull()?.coerceIn(12f, 120f) ?: 42f
                val shape = PropShape.entries.firstOrNull { it.name == p[3] } ?: PropShape.CIRCLE
                val col = p[4].toLongOrNull() ?: 0xFFE65100
                val acc = p[5].toLongOrNull() ?: 0xFFFFFFFF
                val solid = (p.getOrNull(6)?.toIntOrNull() ?: 0) != 0
                TrackProp(
                    x = x,
                    y = y,
                    radius = rad,
                    shape = shape,
                    colorHex = col,
                    accentHex = acc,
                    isSolidObstacle = solid
                )
            }

        val isNightTrack = sections.getOrNull(6)?.trim() == "1"
        val puddles = sections.getOrNull(8).orEmpty()
            .split(';')
            .filter { it.isNotBlank() }
            .mapNotNull { token ->
                val p = token.split(',')
                if (p.size < 2) return@mapNotNull null
                val x = p[0].toFloatOrNull() ?: return@mapNotNull null
                val y = p[1].toFloatOrNull() ?: return@mapNotNull null
                val rad = p.getOrNull(2)?.toFloatOrNull()?.coerceIn(18f, 76f) ?: 38f
                TrackPuddle(x = x, y = y, radius = rad)
            }

        return CustomTrackData(
            id = LeaderboardBoardKey.sanitizeKeyToken(id, 36),
            name = name.trim().take(32).ifEmpty { "Custom Circuit" },
            themeId = safeTheme,
            roadHalfWidth = safeHw,
            controlPoints = points,
            boostPads = boosts,
            itemSpawns = items,
            ramps = ramps,
            props = props,
            puddles = puddles,
            creatorUid = creatorUid.trim(),
            creatorName = creatorName.trim().take(24),
            isNightTrack = isNightTrack,
            randomConditionsEnabled = false
        )
    }

    fun fromDoc(doc: CustomTrackDoc): CustomTrackData? {
        val docId = doc.id.takeIf { it.isNotBlank() } ?: return null
        return decode(
            id = docId,
            name = doc.trackName,
            themeId = doc.themeId,
            roadHalfWidth = doc.roadHalfWidth.toFloat(),
            creatorUid = doc.ownerUid,
            creatorName = doc.ownerName,
            raw = doc.payloadData
        )
    }
}

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

package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.security.MessageDigest

// ============================================================================
// Firestore Social & Invite Models (1:1 Parity with firebase-blueprint.json & firestore.rules)
// Note: No `is`-prefixed Booleans; all properties have defaults; @DocumentId `id`
// is NEVER included in raw Map write payloads.
// ============================================================================

@Keep
@IgnoreExtraProperties
data class DriverProfileDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val driverName: String = "",
    val searchName: String = "",
    val friendCode: String = "",
    val carType: String = RcCarType.VOLT_BUGGY.name,
    val primaryColorArgb: Long = 0xFFFF5722,
    val accentColorArgb: Long = 0xFFFFD54F,
    val online: Boolean = false,
    val activityStatus: String = "OFFLINE",
    val activeRoomId: String = "",
    val activeRoomCode: String = "",
    val activeRoomName: String = "",
    val publicProfile: Boolean = true,
    @ServerTimestamp val lastSeenAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FriendshipDoc(
    @DocumentId val id: String = "",
    val memberUids: List<String> = emptyList(),
    val requesterUid: String = "",
    val requesterName: String = "",
    val requesterFriendCode: String = "",
    val receiverUid: String = "",
    val receiverName: String = "",
    val receiverFriendCode: String = "",
    val status: String = "PENDING",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SessionInviteDoc(
    @DocumentId val id: String = "",
    val senderUid: String = "",
    val senderName: String = "",
    val senderCarType: String = RcCarType.VOLT_BUGGY.name,
    val senderColorArgb: Long = 0xFFFF5722,
    val recipientUid: String = "",
    val recipientName: String = "",
    val roomId: String = "",
    val roomCode: String = "",
    val roomName: String = "",
    val gameMode: String = GameMode.RACE.name,
    val joinToken: String = "",
    val status: String = "PENDING",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class LeaderboardNotificationDoc(
    @DocumentId val id: String = "",
    val friendshipId: String = "",
    val senderUid: String = "",
    val senderName: String = "",
    val senderCarType: String = RcCarType.VOLT_BUGGY.name,
    val senderColorArgb: Long = 0xFFFF5722,
    val recipientUid: String = "",
    val gameMode: String = GameMode.RACE.name,
    val trackKey: String = "",
    val trackName: String = "",
    val boardKey: String = "",
    val totalLaps: Long = 3L,
    val bestRaceTimeMs: Long = 0L,
    val bestScore: Long = 0L,
    val dailyChallenge: Boolean = false,
    val status: String = "UNREAD",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

// ============================================================================
// UI Presentation Models & Deterministic Friend Code Helper
// ============================================================================

data class FriendUiModel(
    val friendshipId: String,
    val friendUid: String,
    val displayName: String,
    val friendCode: String,
    val carType: RcCarType,
    val primaryColorArgb: Long,
    val accentColorArgb: Long,
    val effectivelyOnline: Boolean,
    val activityStatus: String,
    val activeRoomId: String,
    val activeRoomCode: String,
    val activeRoomName: String,
    val statusLabel: String
) {
    val canJoinActiveLobby: Boolean
        get() = effectivelyOnline && activeRoomCode.isNotBlank() && activityStatus == "IN_LOBBY"
}

object DriverFriendCode {
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private const val ONLINE_FRESHNESS_SECONDS = 150L

    fun fromUid(uid: String): String {
        if (uid.isBlank()) return "RC000000"
        val digest = MessageDigest.getInstance("SHA-256").digest(uid.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(8)
        sb.append("RC")
        for (i in 0 until 6) {
            val idx = (digest[i].toInt() and 0xFF) % ALPHABET.length
            sb.append(ALPHABET[idx])
        }
        return sb.toString()
    }

    fun normalizeCode(raw: String): String {
        return raw.trim().uppercase().replace(Regex("[^A-Z0-9]"), "").take(12)
    }

    fun buildCanonicalFriendshipId(uidA: String, uidB: String): String {
        val a = LeaderboardBoardKey.sanitizeKeyToken(uidA, 58)
        val b = LeaderboardBoardKey.sanitizeKeyToken(uidB, 58)
        return if (a <= b) "${a}_${b}" else "${b}_${a}"
    }

    fun toFriendUiModel(
        doc: FriendshipDoc,
        myUid: String,
        liveProfile: DriverProfileDoc?,
        nowEpochSec: Long = System.currentTimeMillis() / 1000L
    ): FriendUiModel {
        val amRequester = doc.requesterUid == myUid
        val friendUid = if (amRequester) doc.receiverUid else doc.requesterUid
        val fallbackName = if (amRequester) doc.receiverName else doc.requesterName
        val fallbackCode = if (amRequester) doc.receiverFriendCode else doc.requesterFriendCode

        val name = liveProfile?.driverName?.takeIf { it.isNotBlank() } ?: fallbackName.ifBlank { "Driver" }
        val code = liveProfile?.friendCode?.takeIf { it.isNotBlank() } ?: fallbackCode.ifBlank { fromUid(friendUid) }
        val car = runCatching {
            RcCarType.valueOf(liveProfile?.carType ?: RcCarType.VOLT_BUGGY.name)
        }.getOrDefault(RcCarType.VOLT_BUGGY)
        val primary = liveProfile?.primaryColorArgb ?: 0xFF00E5FF
        val accent = liveProfile?.accentColorArgb ?: 0xFFFFD54F

        val seenSec = liveProfile?.lastSeenAt?.seconds ?: 0L
        val fresh = seenSec > 0L && (nowEpochSec - seenSec) <= ONLINE_FRESHNESS_SECONDS
        val isOnline = liveProfile?.online == true && liveProfile.activityStatus != "OFFLINE" && fresh
        val actStatus = if (isOnline) liveProfile?.activityStatus ?: "ONLINE" else "OFFLINE"
        val roomCode = if (isOnline) liveProfile?.activeRoomCode.orEmpty() else ""
        val roomId = if (isOnline) liveProfile?.activeRoomId.orEmpty() else ""
        val roomName = if (isOnline) liveProfile?.activeRoomName.orEmpty() else ""

        val label = when {
            isOnline && actStatus == "IN_LOBBY" && roomCode.isNotBlank() -> {
                val roomSuffix = if (roomName.isNotBlank()) " ($roomName)" else ""
                "Online • In Lobby #$roomCode$roomSuffix"
            }
            isOnline && actStatus == "RACING" -> {
                val codeSuffix = if (roomCode.isNotBlank()) " #$roomCode" else ""
                "Online • Racing Live$codeSuffix"
            }
            isOnline -> "Online • Ready to Race"
            seenSec > 0L -> "Offline • Seen ${formatRelativeAgo(nowEpochSec - seenSec)}"
            else -> "Offline"
        }

        return FriendUiModel(
            friendshipId = doc.id,
            friendUid = friendUid,
            displayName = name,
            friendCode = code,
            carType = car,
            primaryColorArgb = primary,
            accentColorArgb = accent,
            effectivelyOnline = isOnline,
            activityStatus = actStatus,
            activeRoomId = roomId,
            activeRoomCode = roomCode,
            activeRoomName = roomName,
            statusLabel = label
        )
    }

    private fun formatRelativeAgo(deltaSec: Long): String {
        val clamped = deltaSec.coerceAtLeast(1L)
        return when {
            clamped < 60L -> "just now"
            clamped < 3600L -> "${clamped / 60L}m ago"
            clamped < 86_400L -> "${clamped / 3600L}h ago"
            else -> "${clamped / 86_400L}d ago"
        }
    }
}

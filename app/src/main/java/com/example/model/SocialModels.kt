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

data class FriendOnlineAlert(
    val friendUid: String,
    val displayName: String,
    val carType: RcCarType = RcCarType.VOLT_BUGGY,
    val primaryColorArgb: Long = 0xFF00E5FF,
    val accentColorArgb: Long = 0xFFFFD54F,
    val activeRoomCode: String = "",
    val activeRoomName: String = "",
    val activityStatus: String = "ONLINE",
    val detectedAtMs: Long = System.currentTimeMillis()
) {
    val hasJoinableLobby: Boolean
        get() = activeRoomCode.isNotBlank() && activityStatus == "IN_LOBBY"
}

/**
 * Tracks accepted friends' online transitions so the app only notifies when a friend
 * transitions from offline -> online (avoiding noise from friends already online on app launch)
 * and keeps any active alert synchronized with the friend's latest lobby/offline state.
 */
class FriendPresenceTransitionTracker(
    private val cooldownMs: Long = 180_000L
) {
    private val knownOnlineByUid = LinkedHashMap<String, Boolean>()
    private val lastAlertedAtMsByUid = LinkedHashMap<String, Long>()
    private val activeAlerts = mutableListOf<FriendOnlineAlert>()

    fun onFriendsUpdated(
        friends: List<FriendUiModel>,
        nowMs: Long = System.currentTimeMillis()
    ): List<FriendOnlineAlert> {
        val byUid = friends.associateBy { it.friendUid }

        // Remove alerts for friends who went offline or are no longer in the list
        activeAlerts.removeAll { alert ->
            val current = byUid[alert.friendUid]
            current == null || !current.effectivelyOnline
        }

        // Update existing alerts in-place (e.g. friend opened/entered a lobby)
        for (i in activeAlerts.indices) {
            val existing = activeAlerts[i]
            val current = byUid[existing.friendUid] ?: continue
            activeAlerts[i] = existing.copy(
                displayName = current.displayName,
                carType = current.carType,
                primaryColorArgb = current.primaryColorArgb,
                accentColorArgb = current.accentColorArgb,
                activeRoomCode = current.activeRoomCode,
                activeRoomName = current.activeRoomName,
                activityStatus = current.activityStatus
            )
        }

        for (friend in friends) {
            val uid = friend.friendUid
            if (uid.isBlank()) continue

            val previousOnline = knownOnlineByUid[uid]
            val nowOnline = friend.effectivelyOnline
            knownOnlineByUid[uid] = nowOnline

            // Only notify when a friend we already observed as offline transitions to online
            if (previousOnline == false && nowOnline) {
                val lastAt = lastAlertedAtMsByUid[uid]
                if ((lastAt == null || nowMs - lastAt >= cooldownMs) &&
                    activeAlerts.none { it.friendUid == uid }
                ) {
                    lastAlertedAtMsByUid[uid] = nowMs
                    activeAlerts.add(
                        FriendOnlineAlert(
                            friendUid = uid,
                            displayName = friend.displayName,
                            carType = friend.carType,
                            primaryColorArgb = friend.primaryColorArgb,
                            accentColorArgb = friend.accentColorArgb,
                            activeRoomCode = friend.activeRoomCode,
                            activeRoomName = friend.activeRoomName,
                            activityStatus = friend.activityStatus,
                            detectedAtMs = nowMs
                        )
                    )
                }
            }
        }

        // Drop state for removed friends so re-adding works cleanly
        knownOnlineByUid.keys.retainAll(byUid.keys)
        return activeAlerts.toList()
    }

    fun dismiss(friendUid: String): List<FriendOnlineAlert> {
        activeAlerts.removeAll { it.friendUid == friendUid }
        return activeAlerts.toList()
    }

    fun reset() {
        knownOnlineByUid.clear()
        lastAlertedAtMsByUid.clear()
        activeAlerts.clear()
    }
}

enum class ChallengeInviteResponse {
    PENDING,
    ACCEPTED,
    DECLINED,
    EXPIRED
}

data class ChallengedFriendStatus(
    val friendUid: String,
    val displayName: String,
    val carType: RcCarType = RcCarType.VOLT_BUGGY,
    val primaryColorArgb: Long = 0xFF00E5FF,
    val inviteId: String = "",
    val response: ChallengeInviteResponse = ChallengeInviteResponse.PENDING
)

enum class PendingChallengePhase {
    SENDING,
    WAITING,
    TIMED_OUT,
    ALL_DECLINED
}

data class PendingFriendChallengeState(
    val challengeId: String,
    val roomId: String,
    val roomCode: String,
    val ownsStandbyRoom: Boolean,
    val targets: List<ChallengedFriendStatus>,
    val totalSeconds: Int = 60,
    val remainingSeconds: Int = 60,
    val phase: PendingChallengePhase = PendingChallengePhase.SENDING
)

object ChallengeProgressEvaluator {
    data class EvaluationResult(
        val updatedState: PendingFriendChallengeState,
        val acceptedTarget: ChallengedFriendStatus? = null,
        val allDeclined: Boolean = false
    )

    fun applyInviteUpdates(
        state: PendingFriendChallengeState,
        invites: List<SessionInviteDoc>
    ): EvaluationResult {
        val byId = invites.associateBy { it.id }
        val updatedTargets = state.targets.map { target ->
            val doc = byId[target.inviteId] ?: return@map target
            val mapped = when (doc.status) {
                "ACCEPTED" -> ChallengeInviteResponse.ACCEPTED
                "DECLINED" -> ChallengeInviteResponse.DECLINED
                "EXPIRED" -> ChallengeInviteResponse.EXPIRED
                else -> ChallengeInviteResponse.PENDING
            }
            target.copy(response = mapped)
        }
        val accepted = updatedTargets.firstOrNull { it.response == ChallengeInviteResponse.ACCEPTED }
        val allDeclined = accepted == null &&
            updatedTargets.isNotEmpty() &&
            updatedTargets.all { it.response == ChallengeInviteResponse.DECLINED }

        val nextPhase = when {
            allDeclined -> PendingChallengePhase.ALL_DECLINED
            else -> state.phase
        }

        return EvaluationResult(
            updatedState = state.copy(targets = updatedTargets, phase = nextPhase),
            acceptedTarget = accepted,
            allDeclined = allDeclined
        )
    }
}



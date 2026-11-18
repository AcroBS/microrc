package com.example.data

import android.content.Context
import com.example.R
import com.example.model.DriverFriendCode
import com.example.model.DriverProfileDoc
import com.example.model.FriendshipDoc
import com.example.model.GameMode
import com.example.model.OnlineRoomDoc
import com.example.model.PlayerProfile
import com.example.model.SessionInviteDoc
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID

class FriendsRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        auth = Firebase.auth
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("Sign in with Google to use Friends & Session Invites.")

    private fun rethrowIfRealCancellation(e: Throwable) {
        if (e is CancellationException &&
            generateSequence(e.cause) { it.cause }.none { it is FirebaseFirestoreException }
        ) {
            throw e
        }
    }

    // ========================================================================
    // 1. Driver Directory & Live Presence (/driverProfiles/{userId})
    // ========================================================================

    suspend fun upsertPresence(
        profile: PlayerProfile,
        online: Boolean,
        activityStatus: String,
        activeRoom: OnlineRoomDoc?
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        val path = "driverProfiles/$uid"
        return try {
            val cleanName = profile.name.trim().take(24).ifEmpty { "Driver" }
            val searchName = cleanName.lowercase(Locale.US).take(24).ifEmpty { "driver" }
            val friendCode = DriverFriendCode.fromUid(uid)
            val safeStatus = if (!online) {
                "OFFLINE"
            } else if (activityStatus in setOf("ONLINE", "IN_LOBBY", "RACING", "OFFLINE")) {
                activityStatus
            } else {
                "ONLINE"
            }
            val roomId = if (online && activeRoom != null) activeRoom.id.take(128) else ""
            val roomCode = if (online && activeRoom != null) activeRoom.roomCode.take(12) else ""
            val roomName = if (online && activeRoom != null) activeRoom.roomName.take(40) else ""

            val docRef = db.collection("driverProfiles").document(uid)
            val snap = docRef.get().await()
            if (snap.exists()) {
                docRef.update(
                    mapOf(
                        "driverName" to cleanName,
                        "searchName" to searchName,
                        "carType" to profile.carType.name,
                        "primaryColorArgb" to profile.primaryColorArgb,
                        "accentColorArgb" to profile.accentColorArgb,
                        "online" to online,
                        "activityStatus" to safeStatus,
                        "activeRoomId" to roomId,
                        "activeRoomCode" to roomCode,
                        "activeRoomName" to roomName,
                        "publicProfile" to true,
                        "lastSeenAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "driverName" to cleanName,
                        "searchName" to searchName,
                        "friendCode" to friendCode,
                        "carType" to profile.carType.name,
                        "primaryColorArgb" to profile.primaryColorArgb,
                        "accentColorArgb" to profile.accentColorArgb,
                        "online" to online,
                        "activityStatus" to safeStatus,
                        "activeRoomId" to roomId,
                        "activeRoomCode" to roomCode,
                        "activeRoomName" to roomName,
                        "publicProfile" to true,
                        "lastSeenAt" to FieldValue.serverTimestamp(),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            rethrowIfRealCancellation(e)
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun setOfflinePresence(profile: PlayerProfile? = null): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        val path = "driverProfiles/$uid"
        return withContext(NonCancellable) {
            try {
                val docRef = db.collection("driverProfiles").document(uid)
                val snap = docRef.get().await()
                if (snap.exists()) {
                    val payload = mutableMapOf<String, Any>(
                        "online" to false,
                        "activityStatus" to "OFFLINE",
                        "activeRoomId" to "",
                        "activeRoomCode" to "",
                        "activeRoomName" to "",
                        "lastSeenAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    if (profile != null) {
                        val cleanName = profile.name.trim().take(24).ifEmpty { "Driver" }
                        payload["driverName"] = cleanName
                        payload["searchName"] = cleanName.lowercase(Locale.US).take(24).ifEmpty { "driver" }
                    }
                    docRef.update(payload).await()
                }
                Result.success(Unit)
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, path)
                Result.failure(e)
            }
        }
    }

    suspend fun searchDrivers(rawQuery: String): Result<List<DriverProfileDoc>> {
        return try {
            val uid = requireUserId()
            val trimmed = rawQuery.trim()
            if (trimmed.isEmpty()) return Result.success(emptyList())

            val normalizedCode = DriverFriendCode.normalizeCode(trimmed)
            val normalizedSearch = trimmed.lowercase(Locale.US)

            val snap = db.collection("driverProfiles")
                .whereEqualTo("publicProfile", true)
                .limit(60)
                .get()
                .await()

            val matches = snap.documents.mapNotNull { doc ->
                doc.toObject(
                    DriverProfileDoc::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }.filter { candidate ->
                candidate.userId.isNotBlank() &&
                    candidate.userId != uid &&
                    (
                        (normalizedCode.length >= 3 && candidate.friendCode.equals(normalizedCode, ignoreCase = true)) ||
                            candidate.searchName.contains(normalizedSearch) ||
                            candidate.driverName.lowercase(Locale.US).contains(normalizedSearch) ||
                            (normalizedCode.length >= 4 && candidate.friendCode.contains(normalizedCode, ignoreCase = true))
                        )
            }.sortedWith(
                compareByDescending<DriverProfileDoc> {
                    it.friendCode.equals(normalizedCode, ignoreCase = true)
                }.thenByDescending { it.online }
                    .thenBy { it.driverName }
            )

            Result.success(matches.take(15))
        } catch (e: Exception) {
            rethrowIfRealCancellation(e)
            handleFirestoreError(e, OperationType.LIST, "driverProfiles")
            Result.failure(e)
        }
    }

    fun observeFriendsPresence(friendUids: List<String>): Flow<Map<String, DriverProfileDoc>> {
        val cleanUids = friendUids.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(30)
        if (cleanUids.isEmpty()) return flowOf(emptyMap())

        val flows = cleanUids.map { fUid ->
            val path = "driverProfiles/$fUid"
            db.collection("driverProfiles").document(fUid)
                .snapshots()
                .map { snap ->
                    fUid to snap.toObject(
                        DriverProfileDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { e ->
                    rethrowIfRealCancellation(e)
                    handleFirestoreError(e, OperationType.GET, path)
                    emit(fUid to null)
                }
        }

        return combine(flows) { pairs ->
            val result = LinkedHashMap<String, DriverProfileDoc>(pairs.size)
            for ((fUid, doc) in pairs) {
                if (doc != null) {
                    result[fUid] = doc
                }
            }
            result
        }
    }

    // ========================================================================
    // 2. Friendships & Requests (/friendships/{friendshipId})
    // ========================================================================

    fun observeFriendships(uid: String): Flow<List<FriendshipDoc>> {
        return db.collection("friendships")
            .whereArrayContains("memberUids", uid)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    doc.toObject(
                        FriendshipDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }.sortedByDescending { it.updatedAt?.seconds ?: 0L }
            }
            .catch { e ->
                rethrowIfRealCancellation(e)
                handleFirestoreError(e, OperationType.LIST, "friendships")
                throw e
            }
    }

    suspend fun sendFriendRequest(
        myProfile: PlayerProfile,
        target: DriverProfileDoc
    ): Result<Unit> {
        var path = "friendships"
        return try {
            val uid = requireUserId()
            if (target.userId.isBlank() || target.userId == uid) {
                return Result.failure(IllegalArgumentException("You cannot add yourself as a friend."))
            }

            // Ensure sender has a driverProfile document first
            upsertPresence(
                profile = myProfile,
                online = true,
                activityStatus = "ONLINE",
                activeRoom = null
            )

            // Check existing friendships for this pair
            val existingSnap = db.collection("friendships")
                .whereArrayContains("memberUids", uid)
                .get()
                .await()
            val alreadyLinked = existingSnap.documents.mapNotNull {
                it.toObject(FriendshipDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }.firstOrNull { target.userId in it.memberUids }

            if (alreadyLinked != null) {
                return if (alreadyLinked.status == "ACCEPTED") {
                    Result.failure(IllegalStateException("${target.driverName} is already on your Friends List!"))
                } else if (alreadyLinked.receiverUid == uid) {
                    // Mutual add: accept their pending request automatically!
                    acceptFriendRequest(alreadyLinked.id, myProfile.name)
                } else {
                    Result.failure(IllegalStateException("Friend request to ${target.driverName} is already pending."))
                }
            }

            val friendshipId = DriverFriendCode.buildCanonicalFriendshipId(uid, target.userId)
            path = "friendships/$friendshipId"
            val myCleanName = myProfile.name.trim().take(24).ifEmpty { "Driver" }
            val myCode = DriverFriendCode.fromUid(uid)
            val targetCleanName = target.driverName.trim().take(24).ifEmpty { "Driver" }
            val targetCode = target.friendCode.trim().take(12).ifEmpty { DriverFriendCode.fromUid(target.userId) }

            db.collection("friendships").document(friendshipId)
                .set(
                    mapOf(
                        "memberUids" to listOf(uid, target.userId),
                        "requesterUid" to uid,
                        "requesterName" to myCleanName,
                        "requesterFriendCode" to myCode,
                        "receiverUid" to target.userId,
                        "receiverName" to targetCleanName,
                        "receiverFriendCode" to targetCode,
                        "status" to "PENDING",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            rethrowIfRealCancellation(e)
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun sendFriendRequestByCode(
        myProfile: PlayerProfile,
        rawFriendCode: String
    ): Result<String> {
        return try {
            val uid = requireUserId()
            val cleanCode = DriverFriendCode.normalizeCode(rawFriendCode)
            if (cleanCode.length < 4) {
                return Result.failure(IllegalArgumentException("Enter a valid Friend Code (e.g. RC7K9P2M)."))
            }
            if (cleanCode == DriverFriendCode.fromUid(uid)) {
                return Result.failure(IllegalArgumentException("That is your own Friend Code! Share it with a friend."))
            }

            val snap = db.collection("driverProfiles")
                .whereEqualTo("publicProfile", true)
                .whereEqualTo("friendCode", cleanCode)
                .limit(1)
                .get()
                .await()
            val target = snap.documents.firstOrNull()?.toObject(
                DriverProfileDoc::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return Result.failure(
                IllegalArgumentException("No driver found with Friend Code $cleanCode yet. Ask them to sign in to MicroRC once.")
            )

            sendFriendRequest(myProfile, target).map { target.driverName }
        } catch (e: Exception) {
            rethrowIfRealCancellation(e)
            handleFirestoreError(e, OperationType.GET, "driverProfiles")
            Result.failure(e)
        }
    }

    suspend fun sendFriendRequestByUid(
        myProfile: PlayerProfile,
        targetUid: String,
        fallbackName: String = "Driver"
    ): Result<String> {
        val path = "driverProfiles/$targetUid"
        return try {
            val uid = requireUserId()
            if (targetUid.isBlank() || targetUid == uid) {
                return Result.failure(IllegalArgumentException("Invalid player selected."))
            }
            val snap = db.collection("driverProfiles").document(targetUid).get().await()
            val doc = snap.toObject(
                DriverProfileDoc::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return Result.failure(
                IllegalStateException("$fallbackName hasn't synced their Friend Profile yet.")
            )
            sendFriendRequest(myProfile, doc).map { doc.driverName.ifBlank { fallbackName } }
        } catch (e: Exception) {
            rethrowIfRealCancellation(e)
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun acceptFriendRequest(
        friendshipId: String,
        receiverDisplayName: String
    ): Result<Unit> {
        val path = "friendships/$friendshipId"
        return try {
            requireUserId()
            val cleanName = receiverDisplayName.trim().take(24).ifEmpty { "Driver" }
            db.collection("friendships").document(friendshipId)
                .update(
                    mapOf(
                        "status" to "ACCEPTED",
                        "receiverName" to cleanName,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            rethrowIfRealCancellation(e)
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun removeOrDeclineFriendship(friendshipId: String): Result<Unit> {
        val path = "friendships/$friendshipId"
        return try {
            requireUserId()
            db.collection("friendships").document(friendshipId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            rethrowIfRealCancellation(e)
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // ========================================================================
    // 3. Real-Time Session Invites (/sessionInvites/{inviteId})
    // ========================================================================

    suspend fun sendSessionInvite(
        senderProfile: PlayerProfile,
        recipientUid: String,
        recipientName: String,
        room: OnlineRoomDoc
    ): Result<Unit> {
        val inviteId = "inv_${UUID.randomUUID().toString().replace("-", "").take(18)}"
        val path = "sessionInvites/$inviteId"
        return try {
            val uid = requireUserId()
            if (recipientUid.isBlank() || recipientUid == uid) {
                return Result.failure(IllegalArgumentException("Choose a friend to notify."))
            }
            val cleanSender = senderProfile.name.trim().take(24).ifEmpty { "Driver" }
            val cleanRecipient = recipientName.trim().take(24).ifEmpty { "Friend" }
            val validMode = runCatching { GameMode.valueOf(room.gameMode).name }
                .getOrDefault(GameMode.RACE.name)
            val token = if (room.passwordProtected) room.passwordHash.take(64) else ""

            db.collection("sessionInvites").document(inviteId)
                .set(
                    mapOf(
                        "senderUid" to uid,
                        "senderName" to cleanSender,
                        "senderCarType" to senderProfile.carType.name,
                        "senderColorArgb" to senderProfile.primaryColorArgb,
                        "recipientUid" to recipientUid,
                        "recipientName" to cleanRecipient,
                        "roomId" to room.id.take(128),
                        "roomCode" to room.roomCode.take(12),
                        "roomName" to room.roomName.take(40).ifEmpty { "Online Paddock" },
                        "gameMode" to validMode,
                        "joinToken" to token,
                        "status" to "PENDING",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            rethrowIfRealCancellation(e)
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    fun observeIncomingSessionInvites(uid: String): Flow<List<SessionInviteDoc>> {
        return db.collection("sessionInvites")
            .whereEqualTo("recipientUid", uid)
            .snapshots()
            .map { snap ->
                val nowSec = System.currentTimeMillis() / 1000L
                snap.documents.mapNotNull { doc ->
                    doc.toObject(
                        SessionInviteDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }.filter { inv ->
                    val createdSec = inv.createdAt?.seconds ?: nowSec
                    inv.status == "PENDING" && (nowSec - createdSec) <= 900L
                }.sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { e ->
                rethrowIfRealCancellation(e)
                handleFirestoreError(e, OperationType.LIST, "sessionInvites")
                throw e
            }
    }

    suspend fun respondToSessionInvite(inviteId: String, accepted: Boolean): Result<Unit> {
        if (inviteId.isBlank()) return Result.success(Unit)
        val path = "sessionInvites/$inviteId"
        return try {
            requireUserId()
            db.collection("sessionInvites").document(inviteId)
                .update(
                    mapOf(
                        "status" to if (accepted) "ACCEPTED" else "DECLINED",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            rethrowIfRealCancellation(e)
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteAllUserSocialData(): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        return withContext(NonCancellable) {
            try {
                runCatching {
                    val friendships = db.collection("friendships")
                        .whereArrayContains("memberUids", uid)
                        .get()
                        .await()
                    friendships.documents.forEach { doc -> doc.reference.delete().await() }
                }
                runCatching {
                    val sentInvites = db.collection("sessionInvites")
                        .whereEqualTo("senderUid", uid)
                        .get()
                        .await()
                    sentInvites.documents.forEach { doc -> doc.reference.delete().await() }
                }
                runCatching {
                    val receivedInvites = db.collection("sessionInvites")
                        .whereEqualTo("recipientUid", uid)
                        .get()
                        .await()
                    receivedInvites.documents.forEach { doc -> doc.reference.delete().await() }
                }
                runCatching {
                    db.collection("driverProfiles").document(uid).delete().await()
                }
                Result.success(Unit)
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.DELETE, "driverProfiles/$uid")
                Result.failure(e)
            }
        }
    }
}

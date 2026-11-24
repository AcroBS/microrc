package com.example.data

import android.content.Context
import com.example.R
import com.example.model.CustomTrackCodec
import com.example.model.CustomTrackData
import com.example.model.CustomTrackDoc
import com.example.model.GameMode
import com.example.model.GhostRunDoc
import com.example.model.GhostRunPlayback
import com.example.model.GhostCodec
import com.example.model.LeaderboardBoardKey
import com.example.model.LeaderboardEntryDoc
import com.example.model.OnlinePasswordHasher
import com.example.model.OnlineRoomChatDoc
import com.example.model.OnlineRoomDoc
import com.example.model.OnlineRoomInputDoc
import com.example.model.OnlineRoomPlayerDoc
import com.example.model.PlayerProfile
import com.example.model.TrackId
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

open class OnlineRaceRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            FirestoreDatabaseResolver.resolveDatabaseId(context)
        ),
        auth = Firebase.auth
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in before accessing cloud features.")

    private fun rethrowIfRealCancellation(e: Throwable) {
        rethrowIfScopeCancellation(e)
    }

    // ========================================================================
    // 1. Online Rooms Discovery & Lifecycle (/onlineRooms/{roomId})
    // ========================================================================

    fun observeOpenRooms(): Flow<List<OnlineRoomDoc>> {
        requireUserId()
        return db.collection("onlineRooms")
            .whereEqualTo("discoverable", true)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    doc.toObject(
                        OnlineRoomDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }.sortedByDescending { it.updatedAt?.seconds ?: 0L }
            }
            .catch { e ->
                rethrowIfRealCancellation(e)
                handleFirestoreError(e, OperationType.LIST, "onlineRooms")
                throw e
            }
    }

    open suspend fun findRoomByCode(rawRoomCode: String): Result<OnlineRoomDoc> {
        return try {
            requireUserId()
            val cleanCode = rawRoomCode.trim().uppercase()
                .replace(Regex("[^A-Z0-9]"), "")
            if (cleanCode.length !in 4..12) {
                return Result.failure(IllegalArgumentException("Enter a valid 6-character room code."))
            }

            withFirestoreTimeout(8_000L, "online lobby server") {
                val directRoomId = "room_$cleanCode"
                val directSnap = db.collection("onlineRooms")
                    .document(directRoomId)
                    .get(Source.SERVER)
                    .await()
                val directDoc = directSnap.toObject(
                    OnlineRoomDoc::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                if (directDoc != null) {
                    return@withFirestoreTimeout Result.success(directDoc)
                }

                val querySnap = db.collection("onlineRooms")
                    .whereEqualTo("discoverable", true)
                    .whereEqualTo("roomCode", cleanCode)
                    .limit(1)
                    .get(Source.SERVER)
                    .await()
                val found = querySnap.documents.firstOrNull()?.toObject(
                    OnlineRoomDoc::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                if (found != null) {
                    Result.success(found)
                } else {
                    Result.failure(IllegalArgumentException("No active online room found for code $cleanCode."))
                }
            }
        } catch (e: Exception) {
            rethrowIfRealCancellation(e)
            handleFirestoreError(e, OperationType.GET, "onlineRooms")
            Result.failure(e)
        }
    }

    open suspend fun createOnlineRoom(
        profile: PlayerProfile,
        roomName: String,
        maxPlayers: Int,
        discoverable: Boolean,
        rawPassword: String,
        gameMode: GameMode,
        trackId: String,
        initialLobbyJson: String
    ): Result<OnlineRoomDoc> {
        var targetRoomPath = "onlineRooms"
        return try {
            val uid = requireUserId()
            val cleanName = roomName.trim().take(40).ifEmpty { "${profile.name.take(14)}'s Paddock" }
            val cleanHostName = profile.name.trim().take(24).ifEmpty { "Driver" }
            val clampedMax = maxPlayers.coerceIn(2, 4).toLong()
            val trimmedPassword = rawPassword.trim()
            val hasPassword = trimmedPassword.isNotEmpty()
            val salt = if (hasPassword) OnlinePasswordHasher.generateSalt() else ""
            val hash = if (hasPassword) OnlinePasswordHasher.hashPassword(salt, trimmedPassword) else ""

            var roomCode = OnlinePasswordHasher.generateRoomCode(6)
            var roomId = "room_$roomCode"
            targetRoomPath = "onlineRooms/$roomId"

            // Fast server reachability & collision check before queueing writes
            val existingRoomSnap = withFirestoreTimeout(8_000L, "online lobby server") {
                db.collection("onlineRooms").document(roomId).get(Source.SERVER).await()
            }
            if (existingRoomSnap.exists()) {
                roomCode = OnlinePasswordHasher.generateRoomCode(6)
                roomId = "room_$roomCode"
                targetRoomPath = "onlineRooms/$roomId"
            }

            val safeTrackId = trackId.take(64).ifEmpty { "LIVING_ROOM_GP" }
            val safeLobbyJson = initialLobbyJson.take(29_500).let {
                if (it.length < 2) "{}" else it
            }

            withFirestoreTimeout(10_000L, "online lobby server") {
                val roomData = mapOf(
                    "hostUid" to uid,
                    "hostName" to cleanHostName,
                    "roomName" to cleanName,
                    "roomCode" to roomCode,
                    "discoverable" to discoverable,
                    "passwordProtected" to hasPassword,
                    "passwordSalt" to salt,
                    "passwordHash" to hash,
                    "status" to "OPEN",
                    "gameMode" to com.example.drift.DriftFeature.toFirestoreWireGameMode(gameMode),
                    "trackId" to safeTrackId,
                    "playerCount" to 1L,
                    "maxPlayers" to clampedMax,
                    "memberUids" to listOf(uid),
                    "lobbyJson" to safeLobbyJson,
                    "raceStartJson" to "",
                    "telemetrySeq" to 0L,
                    "telemetryLine" to "",
                    "rematchJson" to "",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                db.collection("onlineRooms").document(roomId).set(roomData).await()

                targetRoomPath = "onlineRooms/$roomId/players/$uid"
                val hostPlayerData = mapOf(
                    "roomId" to roomId,
                    "userId" to uid,
                    "name" to cleanHostName,
                    "carType" to profile.carType.name,
                    "primaryColorArgb" to profile.primaryColorArgb,
                    "accentColorArgb" to profile.accentColorArgb,
                    "ready" to true,
                    "host" to true,
                    "rematchVote" to "",
                    "joinToken" to hash,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                db.collection("onlineRooms").document(roomId)
                    .collection("players").document(uid)
                    .set(hostPlayerData)
                    .await()

                targetRoomPath = "onlineRooms/$roomId/inputs/$uid"
                val hostInputData = mapOf(
                    "roomId" to roomId,
                    "userId" to uid,
                    "seq" to 0L,
                    "throttle" to 0.0,
                    "steer" to 0.0,
                    "powerUpSeq" to 0L,
                    "mineDropSeq" to 0L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                db.collection("onlineRooms").document(roomId)
                    .collection("inputs").document(uid)
                    .set(hostInputData)
                    .await()
            }

            val createdDoc = OnlineRoomDoc(
                id = roomId,
                hostUid = uid,
                hostName = cleanHostName,
                roomName = cleanName,
                roomCode = roomCode,
                discoverable = discoverable,
                passwordProtected = hasPassword,
                passwordSalt = salt,
                passwordHash = hash,
                status = "OPEN",
                gameMode = com.example.drift.DriftFeature.toFirestoreWireGameMode(gameMode),
                trackId = safeTrackId,
                playerCount = 1L,
                maxPlayers = clampedMax,
                memberUids = listOf(uid),
                lobbyJson = safeLobbyJson
            )
            Result.success(createdDoc)
        } catch (e: Exception) {
            rethrowIfRealCancellation(e)
            handleFirestoreError(e, OperationType.CREATE, targetRoomPath)
            Result.failure(e)
        }
    }

    open suspend fun joinOnlineRoom(
        roomId: String,
        profile: PlayerProfile,
        rawPassword: String,
        preAuthorizedJoinToken: String = ""
    ): Result<OnlineRoomDoc> {
        var activePath = "onlineRooms/$roomId"
        return try {
            val uid = requireUserId()
            withFirestoreTimeout(10_000L, "online lobby server") {
                val roomSnap = db.collection("onlineRooms")
                    .document(roomId)
                    .get(Source.SERVER)
                    .await()
                val room = roomSnap.toObject(
                    OnlineRoomDoc::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: return@withFirestoreTimeout Result.failure(IllegalArgumentException("Room no longer exists."))

                val isAlreadyMember = uid in room.memberUids
                if (!isAlreadyMember) {
                    if (room.status != "OPEN") {
                        return@withFirestoreTimeout Result.failure(IllegalStateException("Race is already in progress."))
                    }
                    if (room.playerCount >= room.maxPlayers) {
                        return@withFirestoreTimeout Result.failure(
                            IllegalStateException("Room is full (${room.playerCount}/${room.maxPlayers}).")
                        )
                    }
                }

                val computedJoinToken = if (room.passwordProtected) {
                    val cleanToken = preAuthorizedJoinToken.trim()
                    if (cleanToken.isNotEmpty() && cleanToken == room.passwordHash) {
                        cleanToken
                    } else {
                        val trimmed = rawPassword.trim()
                        if (trimmed.isEmpty()) {
                            return@withFirestoreTimeout Result.failure(
                                IllegalArgumentException("Password required to join this group.")
                            )
                        }
                        val candidateHash = OnlinePasswordHasher.hashPassword(room.passwordSalt, trimmed)
                        if (candidateHash != room.passwordHash) {
                            return@withFirestoreTimeout Result.failure(
                                IllegalArgumentException("Incorrect room password.")
                            )
                        }
                        candidateHash
                    }
                } else {
                    ""
                }

                val cleanName = profile.name.trim().take(24).ifEmpty { "Driver" }
                val playerRef = db.collection("onlineRooms").document(roomId)
                    .collection("players").document(uid)
                activePath = playerRef.path
                val existingPlayer = playerRef.get(Source.SERVER).await()
                if (existingPlayer.exists()) {
                    playerRef.update(
                        mapOf(
                            "name" to cleanName,
                            "carType" to profile.carType.name,
                            "primaryColorArgb" to profile.primaryColorArgb,
                            "accentColorArgb" to profile.accentColorArgb,
                            "ready" to (room.hostUid == uid),
                            "rematchVote" to "",
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } else {
                    playerRef.set(
                        mapOf(
                            "roomId" to roomId,
                            "userId" to uid,
                            "name" to cleanName,
                            "carType" to profile.carType.name,
                            "primaryColorArgb" to profile.primaryColorArgb,
                            "accentColorArgb" to profile.accentColorArgb,
                            "ready" to false,
                            "host" to false,
                            "rematchVote" to "",
                            "joinToken" to computedJoinToken,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }

                activePath = "onlineRooms/$roomId/inputs/$uid"
                ensureInputDocInitialized(roomId, uid)
                Result.success(room)
            }
        } catch (e: Exception) {
            rethrowIfRealCancellation(e)
            handleFirestoreError(e, OperationType.WRITE, activePath)
            Result.failure(e)
        }
    }

    private suspend fun ensureInputDocInitialized(roomId: String, uid: String) {
        val inputRef = db.collection("onlineRooms").document(roomId)
            .collection("inputs").document(uid)
        val snap = inputRef.get(Source.SERVER).await()
        if (!snap.exists()) {
            inputRef.set(
                mapOf(
                    "roomId" to roomId,
                    "userId" to uid,
                    "seq" to 0L,
                    "throttle" to 0.0,
                    "steer" to 0.0,
                    "powerUpSeq" to 0L,
                    "mineDropSeq" to 0L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }

    open fun observeRoom(roomId: String): Flow<OnlineRoomDoc?> {
        requireUserId()
        val path = "onlineRooms/$roomId"
        return db.collection("onlineRooms").document(roomId)
            .snapshots()
            .map { snap ->
                snap.toObject(
                    OnlineRoomDoc::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { e ->
                rethrowIfRealCancellation(e)
                handleFirestoreError(e, OperationType.GET, path)
                throw e
            }
    }

    open fun observeRoomPlayers(roomId: String): Flow<List<OnlineRoomPlayerDoc>> {
        requireUserId()
        val path = "onlineRooms/$roomId/players"
        return db.collection("onlineRooms").document(roomId)
            .collection("players")
            .whereEqualTo("roomId", roomId)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    doc.toObject(
                        OnlineRoomPlayerDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }.sortedWith(
                    compareByDescending<OnlineRoomPlayerDoc> { it.host }
                        .thenBy { it.createdAt?.seconds ?: 0L }
                )
            }
            .catch { e ->
                rethrowIfRealCancellation(e)
                handleFirestoreError(e, OperationType.LIST, path)
                throw e
            }
    }

    open fun observeRoomInputs(roomId: String): Flow<List<OnlineRoomInputDoc>> {
        requireUserId()
        val path = "onlineRooms/$roomId/inputs"
        return db.collection("onlineRooms").document(roomId)
            .collection("inputs")
            .whereEqualTo("roomId", roomId)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    doc.toObject(
                        OnlineRoomInputDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
            }
            .catch { e ->
                rethrowIfRealCancellation(e)
                handleFirestoreError(e, OperationType.LIST, path)
                throw e
            }
    }

    open fun observeRoomChat(roomId: String): Flow<List<OnlineRoomChatDoc>> {
        requireUserId()
        val path = "onlineRooms/$roomId/chat"
        return db.collection("onlineRooms").document(roomId)
            .collection("chat")
            .whereEqualTo("roomId", roomId)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    doc.toObject(
                        OnlineRoomChatDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }.sortedBy { it.createdAt?.toDate()?.time ?: 0L }
                    .takeLast(50)
            }
            .catch { e ->
                rethrowIfRealCancellation(e)
                handleFirestoreError(e, OperationType.LIST, path)
                throw e
            }
    }

    open suspend fun updateHostRoomFields(roomId: String, fields: Map<String, Any>): Result<Unit> {
        val path = "onlineRooms/$roomId"
        return try {
            requireUserId()
            val payload = fields.toMutableMap().apply {
                remove("id")
                remove("createdAt")
                remove("hostUid")
                remove("roomCode")
                remove("passwordProtected")
                remove("passwordSalt")
                remove("passwordHash")
                put("updatedAt", FieldValue.serverTimestamp())
            }
            withFirestoreTimeout(6_000L, "online lobby server") {
                db.collection("onlineRooms").document(roomId).update(payload).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            rethrowIfRealCancellation(e)
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    open suspend fun updatePlayerFields(roomId: String, fields: Map<String, Any>): Result<Unit> {
        return try {
            val uid = requireUserId()
            val payload = fields.toMutableMap().apply {
                remove("id")
                remove("createdAt")
                remove("userId")
                remove("roomId")
                remove("host")
                remove("joinToken")
                put("updatedAt", FieldValue.serverTimestamp())
            }
            withFirestoreTimeout(6_000L, "online lobby server") {
                db.collection("onlineRooms").document(roomId)
                    .collection("players").document(uid)
                    .update(payload)
                    .await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            rethrowIfRealCancellation(e)
            val uid = auth.currentUser?.uid ?: "unknown"
            handleFirestoreError(e, OperationType.UPDATE, "onlineRooms/$roomId/players/$uid")
            Result.failure(e)
        }
    }

    open suspend fun pushDriverInput(
        roomId: String,
        seq: Long,
        throttle: Float,
        steer: Float,
        powerUpSeq: Long,
        mineDropSeq: Long
    ): Result<Unit> {
        return try {
            val uid = requireUserId()
            val ref = db.collection("onlineRooms").document(roomId)
                .collection("inputs").document(uid)
            val clampedThrottle = throttle.coerceIn(-1f, 1f).toDouble()
            val clampedSteer = steer.coerceIn(-1f, 1f).toDouble()
            withFirestoreTimeout(4_000L, "race telemetry server") {
                try {
                    ref.update(
                        mapOf(
                            "seq" to seq,
                            "throttle" to clampedThrottle,
                            "steer" to clampedSteer,
                            "powerUpSeq" to powerUpSeq,
                            "mineDropSeq" to mineDropSeq,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } catch (notFound: FirebaseFirestoreException) {
                    if (notFound.code == FirebaseFirestoreException.Code.NOT_FOUND) {
                        ref.set(
                            mapOf(
                                "roomId" to roomId,
                                "userId" to uid,
                                "seq" to seq,
                                "throttle" to clampedThrottle,
                                "steer" to clampedSteer,
                                "powerUpSeq" to powerUpSeq,
                                "mineDropSeq" to mineDropSeq,
                                "createdAt" to FieldValue.serverTimestamp(),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                    } else {
                        throw notFound
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            rethrowIfRealCancellation(e)
            val uid = auth.currentUser?.uid ?: "unknown"
            handleFirestoreError(e, OperationType.UPDATE, "onlineRooms/$roomId/inputs/$uid")
            Result.failure(e)
        }
    }

    open suspend fun sendRoomChat(
        roomId: String,
        senderName: String,
        text: String,
        systemMessage: Boolean = false
    ): Result<Unit> {
        val msgId = "msg_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val path = "onlineRooms/$roomId/chat/$msgId"
        return try {
            val uid = requireUserId()
            val cleanText = text.trim().take(140)
            if (cleanText.isEmpty()) return Result.success(Unit)
            val cleanSender = senderName.trim().take(24).ifEmpty { "Driver" }
            withFirestoreTimeout(6_000L, "lobby chat server") {
                db.collection("onlineRooms").document(roomId)
                    .collection("chat").document(msgId)
                    .set(
                        mapOf(
                            "roomId" to roomId,
                            "senderUid" to uid,
                            "senderName" to cleanSender,
                            "text" to cleanText,
                            "systemMessage" to systemMessage,
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            rethrowIfRealCancellation(e)
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    open suspend fun leaveOnlineRoom(roomId: String, isHost: Boolean): Result<Unit> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.success(Unit)
            val roomRef = db.collection("onlineRooms").document(roomId)
            runCatching {
                withTimeoutOrNull(3_000L) {
                    roomRef.collection("inputs").document(uid).delete().await()
                }
            }
            runCatching {
                withTimeoutOrNull(3_000L) {
                    roomRef.collection("players").document(uid).delete().await()
                }
            }
            if (isHost) {
                runCatching {
                    withTimeoutOrNull(3_000L) {
                        roomRef.delete().await()
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            rethrowIfRealCancellation(e)
            handleFirestoreError(e, OperationType.DELETE, "onlineRooms/$roomId")
            Result.failure(e)
        }
    }

    // ========================================================================
    // 2. Cloud Leaderboards & Ghost Runs (/leaderboardEntries & /ghostRuns)
    // ========================================================================

    fun observeLeaderboard(boardKey: String): Flow<List<LeaderboardEntryDoc>> {
        requireUserId()
        val isStuntBoard = boardKey.startsWith("STUNTS_")
        return db.collection("leaderboardEntries")
            .whereEqualTo("publicEntry", true)
            .whereEqualTo("boardKey", boardKey)
            .snapshots()
            .map { snap ->
                val list = snap.documents.mapNotNull { doc ->
                    doc.toObject(
                        LeaderboardEntryDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                if (isStuntBoard) {
                    list.sortedByDescending { it.bestScore }
                } else {
                    list.filter { it.bestRaceTimeMs > 0L }.sortedBy { it.bestRaceTimeMs }
                }
            }
            .catch { e ->
                rethrowIfRealCancellation(e)
                handleFirestoreError(e, OperationType.LIST, "leaderboardEntries")
                throw e
            }
    }

    open fun observeLeaderboardEntriesForUsers(uids: List<String>): Flow<List<LeaderboardEntryDoc>> {
        val cleanUids = uids.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(30)
        if (cleanUids.isEmpty()) return flowOf(emptyList())

        val flows = cleanUids.map { targetUid ->
            db.collection("leaderboardEntries")
                .whereEqualTo("publicEntry", true)
                .whereEqualTo("userId", targetUid)
                .snapshots()
                .map { snap ->
                    snap.documents.mapNotNull { doc ->
                        doc.toObject(
                            LeaderboardEntryDoc::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    }
                }
                .catch { e ->
                    rethrowIfRealCancellation(e)
                    handleFirestoreError(e, OperationType.LIST, "leaderboardEntries")
                    emit(emptyList())
                }
        }

        return combine(flows) { lists ->
            lists.flatMap { it }
        }
    }

    data class LeaderboardSubmissionResult(
        val improvedPersonalBest: Boolean,
        val isNewBoardRecord: Boolean
    )

    companion object {
        internal fun isNewOverallBoardRecord(
            existingBoardEntries: List<LeaderboardEntryDoc>,
            submitterUid: String,
            gameMode: GameMode,
            finishTimeMs: Long,
            bestScore: Int
        ): Boolean {
            val isStunts = gameMode == GameMode.STUNTS
            val others = existingBoardEntries.filter { entry ->
                entry.publicEntry && entry.userId.isNotBlank() && entry.userId != submitterUid
            }
            if (isStunts) {
                if (bestScore <= 0) return false
                val topOtherScore = others.maxOfOrNull { it.bestScore } ?: 0L
                return bestScore.toLong() > topOtherScore
            } else {
                if (finishTimeMs <= 0L) return false
                val fastestOtherTime = others
                    .filter { it.bestRaceTimeMs > 0L }
                    .minOfOrNull { it.bestRaceTimeMs }
                return fastestOtherTime == null || finishTimeMs < fastestOtherTime
            }
        }

        internal fun isNotificationStillBoardRecord(
            notif: com.example.model.LeaderboardNotificationDoc,
            boardEntries: List<LeaderboardEntryDoc>
        ): Boolean {
            if (boardEntries.isEmpty()) return true
            val isStunts = notif.gameMode == GameMode.STUNTS.name || notif.boardKey.startsWith("STUNTS_")
            val others = boardEntries.filter {
                it.publicEntry && it.boardKey == notif.boardKey && it.userId != notif.senderUid
            }
            return if (isStunts) {
                if (notif.bestScore <= 0L) return false
                val topOther = others.maxOfOrNull { it.bestScore } ?: 0L
                notif.bestScore >= topOther
            } else {
                if (notif.bestRaceTimeMs <= 0L) return false
                val fastestOther = others.filter { it.bestRaceTimeMs > 0L }.minOfOrNull { it.bestRaceTimeMs }
                fastestOther == null || notif.bestRaceTimeMs <= fastestOther
            }
        }
    }

    suspend fun submitPersonalBestAndGhost(
        profile: PlayerProfile,
        gameMode: GameMode,
        trackKey: String,
        boardKey: String,
        totalLaps: Int,
        finishTimeMs: Long,
        bestLapMs: Long,
        bestScore: Int,
        encodedGhostFrames: String
    ): Result<LeaderboardSubmissionResult> {
        var currentPath = "leaderboardEntries"
        return try {
            val uid = requireUserId()
            val entryId = LeaderboardBoardKey.buildUserBoardDocId(uid, boardKey)
            val ghostId = "ghost_${entryId}".take(126)
            val cleanDriverName = profile.name.trim().take(24).ifEmpty { "Driver" }
            val safeLaps = totalLaps.coerceIn(1, 9).toLong()
            val safeRaceMs = finishTimeMs.coerceIn(0L, 3_600_000L)
            val safeLapMs = bestLapMs.coerceIn(0L, 3_600_000L)
            val safeScore = bestScore.coerceIn(0, 1_000_000).toLong()

            val entryRef = db.collection("leaderboardEntries").document(entryId)
            currentPath = entryRef.path
            val existingSnap = entryRef.get().await()
            val existing = existingSnap.toObject(
                LeaderboardEntryDoc::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )

            val isStunts = gameMode == GameMode.STUNTS
            val improvedPrimary = if (existing == null) {
                true
            } else if (isStunts) {
                safeScore > existing.bestScore
            } else {
                safeRaceMs > 0L && (existing.bestRaceTimeMs <= 0L || safeRaceMs < existing.bestRaceTimeMs)
            }
            val improvedLap = existing != null && safeLapMs > 0L &&
                (existing.bestLapTimeMs <= 0L || safeLapMs < existing.bestLapTimeMs)

            if (!improvedPrimary && !improvedLap) {
                return Result.success(
                    LeaderboardSubmissionResult(
                        improvedPersonalBest = false,
                        isNewBoardRecord = false
                    )
                )
            }

            val canWriteGhost = improvedPrimary &&
                !isStunts &&
                safeRaceMs >= 1L &&
                encodedGhostFrames.length in 3..119_000

            var finalHasGhost = existing?.hasGhost ?: false
            var finalGhostId = existing?.ghostId ?: ""

            if (canWriteGhost) {
                val ghostRef = db.collection("ghostRuns").document(ghostId)
                currentPath = ghostRef.path
                val ghostSnap = ghostRef.get().await()
                if (ghostSnap.exists()) {
                    ghostRef.update(
                        mapOf(
                            "driverName" to cleanDriverName,
                            "carType" to profile.carType.name,
                            "primaryColorArgb" to profile.primaryColorArgb,
                            "accentColorArgb" to profile.accentColorArgb,
                            "totalLaps" to safeLaps,
                            "finishTimeMs" to safeRaceMs,
                            "bestLapMs" to safeLapMs,
                            "framesData" to encodedGhostFrames,
                            "publicGhost" to true,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } else {
                    ghostRef.set(
                        mapOf(
                            "userId" to uid,
                            "driverName" to cleanDriverName,
                            "carType" to profile.carType.name,
                            "primaryColorArgb" to profile.primaryColorArgb,
                            "accentColorArgb" to profile.accentColorArgb,
                            "boardKey" to boardKey.take(96),
                            "trackId" to trackKey.take(64),
                            "totalLaps" to safeLaps,
                            "finishTimeMs" to safeRaceMs,
                            "bestLapMs" to safeLapMs,
                            "framesData" to encodedGhostFrames,
                            "publicGhost" to true,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
                finalHasGhost = true
                finalGhostId = ghostId
            }

            currentPath = entryRef.path
            val mergedRaceMs = when {
                existing == null -> safeRaceMs
                safeRaceMs <= 0L -> existing.bestRaceTimeMs
                existing.bestRaceTimeMs <= 0L -> safeRaceMs
                else -> minOf(existing.bestRaceTimeMs, safeRaceMs)
            }
            val mergedLapMs = when {
                existing == null -> safeLapMs
                safeLapMs <= 0L -> existing.bestLapTimeMs
                existing.bestLapTimeMs <= 0L -> safeLapMs
                else -> minOf(existing.bestLapTimeMs, safeLapMs)
            }
            val mergedScore = maxOf(existing?.bestScore ?: 0L, safeScore)

            if (existingSnap.exists()) {
                entryRef.update(
                    mapOf(
                        "driverName" to cleanDriverName,
                        "carType" to profile.carType.name,
                        "primaryColorArgb" to profile.primaryColorArgb,
                        "accentColorArgb" to profile.accentColorArgb,
                        "totalLaps" to safeLaps,
                        "bestRaceTimeMs" to mergedRaceMs,
                        "bestLapTimeMs" to mergedLapMs,
                        "bestScore" to mergedScore,
                        "hasGhost" to finalHasGhost,
                        "ghostId" to finalGhostId,
                        "publicEntry" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val ruleMode = com.example.drift.DriftFeature.toFirestoreWireGameMode(gameMode)
                entryRef.set(
                    mapOf(
                        "userId" to uid,
                        "driverName" to cleanDriverName,
                        "carType" to profile.carType.name,
                        "primaryColorArgb" to profile.primaryColorArgb,
                        "accentColorArgb" to profile.accentColorArgb,
                        "gameMode" to ruleMode,
                        "trackKey" to trackKey.take(64),
                        "boardKey" to boardKey.take(96),
                        "totalLaps" to safeLaps,
                        "bestRaceTimeMs" to mergedRaceMs,
                        "bestLapTimeMs" to mergedLapMs,
                        "bestScore" to mergedScore,
                        "hasGhost" to finalHasGhost,
                        "ghostId" to finalGhostId,
                        "publicEntry" to true,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            val isNewBoardRecord = if (improvedPrimary) {
                val boardDocs = runCatching {
                    withTimeoutOrNull(6_000L) {
                        db.collection("leaderboardEntries")
                            .whereEqualTo("publicEntry", true)
                            .whereEqualTo("boardKey", boardKey)
                            .get(Source.SERVER)
                            .await()
                    } ?: db.collection("leaderboardEntries")
                        .whereEqualTo("publicEntry", true)
                        .whereEqualTo("boardKey", boardKey)
                        .get()
                        .await()
                }.getOrNull()
                val existingBoardEntries = boardDocs?.documents?.mapNotNull { doc ->
                    doc.toObject(
                        LeaderboardEntryDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }.orEmpty()
                isNewOverallBoardRecord(
                    existingBoardEntries = existingBoardEntries,
                    submitterUid = uid,
                    gameMode = gameMode,
                    finishTimeMs = safeRaceMs,
                    bestScore = safeScore.toInt()
                )
            } else {
                false
            }

            Result.success(
                LeaderboardSubmissionResult(
                    improvedPersonalBest = improvedPrimary,
                    isNewBoardRecord = isNewBoardRecord
                )
            )
        } catch (e: Exception) {
            rethrowIfRealCancellation(e)
            handleFirestoreError(e, OperationType.WRITE, currentPath)
            Result.failure(e)
        }
    }

    open suspend fun fetchGhostRunPlayback(ghostId: String): Result<GhostRunPlayback> {
        val path = "ghostRuns/$ghostId"
        return try {
            requireUserId()
            val snap = withFirestoreTimeout(8_000L, "ghost leaderboard server") {
                db.collection("ghostRuns").document(ghostId).get().await()
            }
            val doc = snap.toObject(
                GhostRunDoc::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return Result.failure(IllegalArgumentException("Ghost run data not found."))
            val frames = GhostCodec.decodeFrames(doc.framesData)
            if (frames.size < 2) {
                return Result.failure(IllegalStateException("Ghost trajectory has insufficient keyframes."))
            }
            Result.success(
                GhostRunPlayback(
                    ghostId = doc.id.ifEmpty { ghostId },
                    driverName = doc.driverName,
                    carType = doc.carType,
                    primaryColorArgb = doc.primaryColorArgb,
                    accentColorArgb = doc.accentColorArgb,
                    boardKey = doc.boardKey,
                    trackId = doc.trackId,
                    totalLaps = doc.totalLaps.toInt(),
                    finishTimeMs = doc.finishTimeMs,
                    bestLapMs = doc.bestLapMs,
                    frames = frames
                )
            )
        } catch (e: Exception) {
            rethrowIfRealCancellation(e)
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    open suspend fun deleteAllUserRaceAndLeaderboardData(): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        return try {
            runCatching {
                withTimeoutOrNull(5_000L) {
                    val entries = db.collection("leaderboardEntries")
                        .whereEqualTo("publicEntry", true)
                        .whereEqualTo("userId", uid)
                        .get()
                        .await()
                    entries.documents.forEach { doc -> doc.reference.delete().await() }
                }
            }
            runCatching {
                withTimeoutOrNull(5_000L) {
                    val ghosts = db.collection("ghostRuns")
                        .whereEqualTo("publicGhost", true)
                        .whereEqualTo("userId", uid)
                        .get()
                        .await()
                    ghosts.documents.forEach { doc -> doc.reference.delete().await() }
                }
            }
            runCatching {
                withTimeoutOrNull(5_000L) {
                    val hostedRooms = db.collection("onlineRooms")
                        .whereEqualTo("discoverable", true)
                        .whereEqualTo("hostUid", uid)
                        .get()
                        .await()
                    hostedRooms.documents.forEach { doc ->
                        runCatching { doc.reference.collection("inputs").document(uid).delete().await() }
                        runCatching { doc.reference.collection("players").document(uid).delete().await() }
                        runCatching { doc.reference.delete().await() }
                    }
                }
            }
            runCatching {
                withTimeoutOrNull(5_000L) {
                    val customTracks = db.collection("customTracks")
                        .whereEqualTo("ownerUid", uid)
                        .get()
                        .await()
                    customTracks.documents.forEach { doc -> doc.reference.delete().await() }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            rethrowIfRealCancellation(e)
            handleFirestoreError(e, OperationType.DELETE, "leaderboardEntries")
            Result.failure(e)
        }
    }

    // ========================================================================
    // 3. Self-Designed Cloud Custom Tracks (/customTracks/{trackId})
    // ========================================================================

    fun sanitizeCustomTrackId(rawId: String): String =
        LeaderboardBoardKey.sanitizeKeyToken(rawId, 36)

    open fun observeCustomTracksForUsers(uids: List<String>): Flow<List<CustomTrackData>> {
        val cleanUids = uids.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(30)
        if (cleanUids.isEmpty()) return flowOf(emptyList())

        val flows = cleanUids.map { targetUid ->
            db.collection("customTracks")
                .whereEqualTo("publicTrack", true)
                .whereEqualTo("ownerUid", targetUid)
                .snapshots()
                .map { snap ->
                    snap.documents.mapNotNull { doc ->
                        val parsed = doc.toObject(
                            CustomTrackDoc::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        ) ?: return@mapNotNull null
                        val updatedSec = parsed.updatedAt?.seconds ?: 0L
                        val decoded = CustomTrackCodec.fromDoc(parsed) ?: return@mapNotNull null
                        updatedSec to decoded
                    }
                }
                .catch { e ->
                    rethrowIfRealCancellation(e)
                    handleFirestoreError(e, OperationType.LIST, "customTracks")
                    emit(emptyList())
                }
        }

        return combine(flows) { lists ->
            lists.flatMap { it }
                .sortedByDescending { it.first }
                .map { it.second }
                .distinctBy { it.id }
        }
    }

    open suspend fun upsertCustomTrack(
        profile: PlayerProfile,
        track: CustomTrackData
    ): Result<Unit> {
        var currentPath = "customTracks"
        return try {
            val uid = requireUserId()
            // Never overwrite a track authored by a different user
            if (track.creatorUid.isNotBlank() && track.creatorUid != uid) {
                return Result.success(Unit)
            }

            val docId = sanitizeCustomTrackId(track.id)
            currentPath = "customTracks/$docId"
            val cleanOwnerName = profile.name.trim().take(24).ifEmpty { "Driver" }
            val cleanTrackName = track.name.trim().take(32).ifEmpty { "Custom Circuit" }
            val safeTheme = TrackId.entries.firstOrNull { it.name == track.themeId }?.name
                ?: TrackId.NEON_ROOFTOP.name
            val safeRoadHalfWidth = track.roadHalfWidth.toDouble().coerceIn(80.0, 150.0)
            val encodedPayload = CustomTrackCodec.encode(track)
            if (encodedPayload.length !in 10..25_000) {
                return Result.failure(IllegalArgumentException("Custom track geometry is invalid."))
            }

            val docRef = db.collection("customTracks").document(docId)
            withFirestoreTimeout(8_000L, "custom track sync server") {
                val existingSnap = docRef.get().await()
                if (existingSnap.exists()) {
                    val existingOwner = existingSnap.getString("ownerUid").orEmpty()
                    if (existingOwner.isNotBlank() && existingOwner != uid) {
                        return@withFirestoreTimeout
                    }
                    docRef.update(
                        mapOf(
                            "ownerName" to cleanOwnerName,
                            "trackName" to cleanTrackName,
                            "themeId" to safeTheme,
                            "roadHalfWidth" to safeRoadHalfWidth,
                            "payloadData" to encodedPayload,
                            "publicTrack" to true,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } else {
                    docRef.set(
                        mapOf(
                            "ownerUid" to uid,
                            "ownerName" to cleanOwnerName,
                            "trackName" to cleanTrackName,
                            "themeId" to safeTheme,
                            "roadHalfWidth" to safeRoadHalfWidth,
                            "payloadData" to encodedPayload,
                            "publicTrack" to true,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            rethrowIfRealCancellation(e)
            handleFirestoreError(e, OperationType.WRITE, currentPath)
            Result.failure(e)
        }
    }

    open suspend fun deleteCustomTrackFromCloud(trackId: String): Result<Unit> {
        val docId = sanitizeCustomTrackId(trackId)
        val path = "customTracks/$docId"
        return try {
            val uid = auth.currentUser?.uid ?: return Result.success(Unit)
            val docRef = db.collection("customTracks").document(docId)
            withTimeoutOrNull(6_000L) {
                val snap = docRef.get().await()
                if (snap.exists() && snap.getString("ownerUid") == uid) {
                    docRef.delete().await()
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            rethrowIfRealCancellation(e)
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    open suspend fun fetchCustomTrack(trackId: String): Result<CustomTrackData> {
        val docId = sanitizeCustomTrackId(trackId)
        val path = "customTracks/$docId"
        return try {
            requireUserId()
            val snap = withFirestoreTimeout(8_000L, "custom track server") {
                db.collection("customTracks").document(docId).get().await()
            }
            val doc = snap.toObject(
                CustomTrackDoc::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return Result.failure(IllegalArgumentException("Custom track not found on cloud."))
            val decoded = CustomTrackCodec.fromDoc(doc)
                ?: return Result.failure(IllegalStateException("Custom track data could not be decoded."))
            Result.success(decoded)
        } catch (e: Exception) {
            rethrowIfRealCancellation(e)
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }
}

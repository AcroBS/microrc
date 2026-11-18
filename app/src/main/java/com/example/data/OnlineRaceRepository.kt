package com.example.data

import android.content.Context
import com.example.R
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
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class OnlineRaceRepository(
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
            ?: throw IllegalStateException("User must be signed in before accessing cloud features.")

    private fun rethrowIfRealCancellation(e: Throwable) {
        if (e is CancellationException &&
            generateSequence(e.cause) { it.cause }.none { it is FirebaseFirestoreException }
        ) {
            throw e
        }
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

    suspend fun findRoomByCode(rawRoomCode: String): Result<OnlineRoomDoc> {
        return try {
            requireUserId()
            val cleanCode = rawRoomCode.trim().uppercase()
                .replace(Regex("[^A-Z0-9]"), "")
            if (cleanCode.length !in 4..12) {
                return Result.failure(IllegalArgumentException("Enter a valid 6-character room code."))
            }

            val directRoomId = "room_$cleanCode"
            val directSnap = db.collection("onlineRooms").document(directRoomId).get().await()
            val directDoc = directSnap.toObject(
                OnlineRoomDoc::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            if (directDoc != null) {
                return Result.success(directDoc)
            }

            val querySnap = db.collection("onlineRooms")
                .whereEqualTo("discoverable", true)
                .whereEqualTo("roomCode", cleanCode)
                .limit(1)
                .get()
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
        } catch (e: Exception) {
            rethrowIfRealCancellation(e)
            handleFirestoreError(e, OperationType.GET, "onlineRooms")
            Result.failure(e)
        }
    }

    suspend fun createOnlineRoom(
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

            val roomCode = OnlinePasswordHasher.generateRoomCode(6)
            val roomId = "room_$roomCode"
            targetRoomPath = "onlineRooms/$roomId"

            val safeLobbyJson = initialLobbyJson.take(29_500).let {
                if (it.length < 2) "{}" else it
            }

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
                "gameMode" to gameMode.name,
                "trackId" to trackId.take(64).ifEmpty { "LIVING_ROOM_GP" },
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

            val hostPlayerPath = "onlineRooms/$roomId/players/$uid"
            targetRoomPath = hostPlayerPath
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

            ensureInputDocInitialized(roomId, uid)

            val snap = db.collection("onlineRooms").document(roomId).get().await()
            val createdDoc = snap.toObject(
                OnlineRoomDoc::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: OnlineRoomDoc(
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
                gameMode = gameMode.name,
                trackId = trackId,
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

    suspend fun joinOnlineRoom(
        roomId: String,
        profile: PlayerProfile,
        rawPassword: String,
        preAuthorizedJoinToken: String = ""
    ): Result<OnlineRoomDoc> {
        var activePath = "onlineRooms/$roomId"
        return try {
            val uid = requireUserId()
            val roomSnap = db.collection("onlineRooms").document(roomId).get().await()
            val room = roomSnap.toObject(
                OnlineRoomDoc::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return Result.failure(IllegalArgumentException("Room no longer exists."))

            val isAlreadyMember = uid in room.memberUids
            if (!isAlreadyMember) {
                if (room.status != "OPEN") {
                    return Result.failure(IllegalStateException("Race is already in progress."))
                }
                if (room.playerCount >= room.maxPlayers) {
                    return Result.failure(IllegalStateException("Room is full (${room.playerCount}/${room.maxPlayers})."))
                }
            }

            val computedJoinToken = if (room.passwordProtected) {
                val cleanToken = preAuthorizedJoinToken.trim()
                if (cleanToken.isNotEmpty() && cleanToken == room.passwordHash) {
                    cleanToken
                } else {
                    val trimmed = rawPassword.trim()
                    if (trimmed.isEmpty()) {
                        return Result.failure(IllegalArgumentException("Password required to join this group."))
                    }
                    val candidateHash = OnlinePasswordHasher.hashPassword(room.passwordSalt, trimmed)
                    if (candidateHash != room.passwordHash) {
                        return Result.failure(IllegalArgumentException("Incorrect room password."))
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
            val existingPlayer = playerRef.get().await()
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

            ensureInputDocInitialized(roomId, uid)
            Result.success(room)
        } catch (e: Exception) {
            rethrowIfRealCancellation(e)
            handleFirestoreError(e, OperationType.WRITE, activePath)
            Result.failure(e)
        }
    }

    private suspend fun ensureInputDocInitialized(roomId: String, uid: String) {
        val inputRef = db.collection("onlineRooms").document(roomId)
            .collection("inputs").document(uid)
        val snap = inputRef.get().await()
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

    fun observeRoom(roomId: String): Flow<OnlineRoomDoc?> {
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

    fun observeRoomPlayers(roomId: String): Flow<List<OnlineRoomPlayerDoc>> {
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

    fun observeRoomInputs(roomId: String): Flow<List<OnlineRoomInputDoc>> {
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

    fun observeRoomChat(roomId: String): Flow<List<OnlineRoomChatDoc>> {
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

    suspend fun updateHostRoomFields(roomId: String, fields: Map<String, Any>): Result<Unit> {
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
            db.collection("onlineRooms").document(roomId).update(payload).await()
            Result.success(Unit)
        } catch (e: Exception) {
            rethrowIfRealCancellation(e)
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updatePlayerFields(roomId: String, fields: Map<String, Any>): Result<Unit> {
        return try {
            val uid = requireUserId()
            val path = "onlineRooms/$roomId/players/$uid"
            val payload = fields.toMutableMap().apply {
                remove("id")
                remove("createdAt")
                remove("userId")
                remove("roomId")
                remove("host")
                remove("joinToken")
                put("updatedAt", FieldValue.serverTimestamp())
            }
            db.collection("onlineRooms").document(roomId)
                .collection("players").document(uid)
                .update(payload)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            rethrowIfRealCancellation(e)
            val uid = auth.currentUser?.uid ?: "unknown"
            handleFirestoreError(e, OperationType.UPDATE, "onlineRooms/$roomId/players/$uid")
            Result.failure(e)
        }
    }

    suspend fun pushDriverInput(
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
            Result.success(Unit)
        } catch (e: Exception) {
            rethrowIfRealCancellation(e)
            val uid = auth.currentUser?.uid ?: "unknown"
            handleFirestoreError(e, OperationType.UPDATE, "onlineRooms/$roomId/inputs/$uid")
            Result.failure(e)
        }
    }

    suspend fun sendRoomChat(
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
            Result.success(Unit)
        } catch (e: Exception) {
            rethrowIfRealCancellation(e)
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun leaveOnlineRoom(roomId: String, isHost: Boolean): Result<Unit> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.success(Unit)
            val roomRef = db.collection("onlineRooms").document(roomId)
            runCatching { roomRef.collection("inputs").document(uid).delete().await() }
            runCatching { roomRef.collection("players").document(uid).delete().await() }
            if (isHost) {
                runCatching { roomRef.delete().await() }
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
    ): Result<Boolean> {
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
                return Result.success(false)
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
                val ruleMode = when (gameMode) {
                    GameMode.STUNTS -> GameMode.STUNTS.name
                    GameMode.ARENA -> GameMode.ARENA.name
                    else -> GameMode.RACE.name
                }
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
            Result.success(improvedPrimary)
        } catch (e: Exception) {
            rethrowIfRealCancellation(e)
            handleFirestoreError(e, OperationType.WRITE, currentPath)
            Result.failure(e)
        }
    }

    suspend fun fetchGhostRunPlayback(ghostId: String): Result<GhostRunPlayback> {
        val path = "ghostRuns/$ghostId"
        return try {
            requireUserId()
            val snap = db.collection("ghostRuns").document(ghostId).get().await()
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

    suspend fun deleteAllUserRaceAndLeaderboardData(): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        return try {
            runCatching {
                val entries = db.collection("leaderboardEntries")
                    .whereEqualTo("publicEntry", true)
                    .whereEqualTo("userId", uid)
                    .get()
                    .await()
                entries.documents.forEach { doc -> doc.reference.delete().await() }
            }
            runCatching {
                val ghosts = db.collection("ghostRuns")
                    .whereEqualTo("publicGhost", true)
                    .whereEqualTo("userId", uid)
                    .get()
                    .await()
                ghosts.documents.forEach { doc -> doc.reference.delete().await() }
            }
            runCatching {
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
            Result.success(Unit)
        } catch (e: Exception) {
            rethrowIfRealCancellation(e)
            handleFirestoreError(e, OperationType.DELETE, "leaderboardEntries")
            Result.failure(e)
        }
    }
}

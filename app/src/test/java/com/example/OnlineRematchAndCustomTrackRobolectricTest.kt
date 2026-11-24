package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.data.OnlineRaceRepository
import com.example.model.GameMode
import com.example.model.OnlineRoomChatDoc
import com.example.model.OnlineRoomDoc
import com.example.model.OnlineRoomInputDoc
import com.example.model.OnlineRoomPlayerDoc
import com.example.model.PlayerProfile
import com.example.model.RacePhase
import com.example.model.RaceRulesSettings
import com.example.model.RaceSnapshot
import com.example.model.RacerState
import com.example.model.RematchCountdownState
import com.example.model.RematchVoteOption
import com.example.model.TrackId
import com.example.network.OnlineRaceSessionManager
import com.example.network.RaceNavEvent
import com.example.network.RaceTelemetryCodec
import com.example.ui.auth.GoogleAuthManager
import com.google.firebase.firestore.FirebaseFirestore
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OnlineRematchAndCustomTrackRobolectricTest {

    private class FakeCloudBackend(
        private val firestore: FirebaseFirestore
    ) {
        val roomFlow = MutableStateFlow<OnlineRoomDoc?>(null)
        val playersFlow = MutableStateFlow<List<OnlineRoomPlayerDoc>>(emptyList())
        val chatFlow = MutableStateFlow<List<OnlineRoomChatDoc>>(emptyList())

        fun repoFor(uid: String): OnlineRaceRepository = object : OnlineRaceRepository(db = firestore) {
            override suspend fun createOnlineRoom(
                profile: PlayerProfile,
                roomName: String,
                maxPlayers: Int,
                discoverable: Boolean,
                rawPassword: String,
                gameMode: GameMode,
                trackId: String,
                initialLobbyJson: String
            ): Result<OnlineRoomDoc> {
                val room = OnlineRoomDoc(
                    id = "room_SYNC01",
                    hostUid = uid,
                    hostName = profile.name,
                    roomName = roomName,
                    roomCode = "SYNC01",
                    discoverable = discoverable,
                    status = "OPEN",
                    gameMode = gameMode.name,
                    trackId = trackId,
                    playerCount = 1L,
                    maxPlayers = maxPlayers.toLong(),
                    memberUids = listOf(uid),
                    lobbyJson = initialLobbyJson
                )
                roomFlow.value = room
                playersFlow.value = listOf(
                    OnlineRoomPlayerDoc(
                        id = uid,
                        roomId = room.id,
                        userId = uid,
                        name = profile.name,
                        carType = profile.carType.name,
                        primaryColorArgb = profile.primaryColorArgb,
                        accentColorArgb = profile.accentColorArgb,
                        ready = true,
                        host = true,
                        rematchVote = ""
                    )
                )
                return Result.success(room)
            }

            override suspend fun joinOnlineRoom(
                roomId: String,
                profile: PlayerProfile,
                rawPassword: String,
                preAuthorizedJoinToken: String
            ): Result<OnlineRoomDoc> {
                val curRoom = roomFlow.value ?: return Result.failure(IllegalStateException("Missing room"))
                val guestDoc = OnlineRoomPlayerDoc(
                    id = uid,
                    roomId = roomId,
                    userId = uid,
                    name = profile.name,
                    carType = profile.carType.name,
                    primaryColorArgb = profile.primaryColorArgb,
                    accentColorArgb = profile.accentColorArgb,
                    ready = true,
                    host = false,
                    rematchVote = ""
                )
                playersFlow.value = playersFlow.value.filterNot { it.userId == uid } + guestDoc
                val updated = curRoom.copy(
                    playerCount = playersFlow.value.size.toLong(),
                    memberUids = (curRoom.memberUids + uid).distinct()
                )
                roomFlow.value = updated
                return Result.success(updated)
            }

            override fun observeRoom(roomId: String): Flow<OnlineRoomDoc?> = roomFlow

            override fun observeRoomPlayers(roomId: String): Flow<List<OnlineRoomPlayerDoc>> = playersFlow

            override fun observeRoomInputs(roomId: String): Flow<List<OnlineRoomInputDoc>> = emptyFlow()

            override fun observeRoomChat(roomId: String): Flow<List<OnlineRoomChatDoc>> = chatFlow

            override suspend fun updateHostRoomFields(roomId: String, fields: Map<String, Any>): Result<Unit> {
                val cur = roomFlow.value ?: return Result.success(Unit)
                roomFlow.value = cur.copy(
                    roomName = (fields["roomName"] as? String) ?: cur.roomName,
                    gameMode = (fields["gameMode"] as? String) ?: cur.gameMode,
                    trackId = (fields["trackId"] as? String) ?: cur.trackId,
                    playerCount = (fields["playerCount"] as? Long) ?: cur.playerCount,
                    maxPlayers = (fields["maxPlayers"] as? Long) ?: cur.maxPlayers,
                    memberUids = (fields["memberUids"] as? List<*>)?.filterIsInstance<String>() ?: cur.memberUids,
                    lobbyJson = (fields["lobbyJson"] as? String) ?: cur.lobbyJson,
                    status = (fields["status"] as? String) ?: cur.status,
                    raceStartJson = (fields["raceStartJson"] as? String) ?: cur.raceStartJson,
                    telemetrySeq = (fields["telemetrySeq"] as? Long) ?: cur.telemetrySeq,
                    telemetryLine = (fields["telemetryLine"] as? String) ?: cur.telemetryLine,
                    rematchJson = (fields["rematchJson"] as? String) ?: cur.rematchJson
                )
                return Result.success(Unit)
            }

            override suspend fun updatePlayerFields(roomId: String, fields: Map<String, Any>): Result<Unit> {
                val updatedList = playersFlow.value.map { p ->
                    if (p.userId != uid) {
                        p
                    } else {
                        p.copy(
                            rematchVote = (fields["rematchVote"] as? String) ?: p.rematchVote,
                            ready = (fields["ready"] as? Boolean) ?: p.ready,
                            name = (fields["name"] as? String) ?: p.name
                        )
                    }
                }
                playersFlow.value = updatedList
                return Result.success(Unit)
            }

            override suspend fun sendRoomChat(
                roomId: String,
                senderName: String,
                text: String,
                systemMessage: Boolean
            ): Result<Unit> {
                chatFlow.value = chatFlow.value + OnlineRoomChatDoc(
                    id = "msg_${chatFlow.value.size}",
                    roomId = roomId,
                    senderUid = uid,
                    senderName = senderName,
                    text = text,
                    systemMessage = systemMessage
                )
                return Result.success(Unit)
            }

            override suspend fun leaveOnlineRoom(roomId: String, isHost: Boolean): Result<Unit> =
                Result.success(Unit)
        }
    }

    private fun awaitCondition(timeoutMs: Long = 3_000L, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!predicate() && System.currentTimeMillis() < deadline) {
            Thread.sleep(15L)
        }
        assertTrue("Condition was not met within ${timeoutMs}ms", predicate())
    }

    @Test
    fun clientReceivesRematchVotingOnFinish_andAnyPlayerReturnToLobbyReturnsHostAndClientsToLobby() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        GoogleAuthManager.ensureFirebaseInitialized(context)
        val firestore = FirebaseFirestore.getInstance("(default)")
        val backend = FakeCloudBackend(firestore)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        try {
            val hostManager = OnlineRaceSessionManager(
                repository = backend.repoFor("uid_host"),
                currentUserId = "uid_host",
                scope = scope
            )
            val clientManager = OnlineRaceSessionManager(
                repository = backend.repoFor("uid_client"),
                currentUserId = "uid_client",
                scope = scope
            )

            val hostEvents = CopyOnWriteArrayList<RaceNavEvent>()
            val clientEvents = CopyOnWriteArrayList<RaceNavEvent>()
            val hostNavJob = scope.launch { hostManager.navEvents.collect { hostEvents.add(it) } }
            val clientNavJob = scope.launch { clientManager.navEvents.collect { clientEvents.add(it) } }
            Thread.sleep(30L)

            hostManager.hostOnlineRoom(
                profile = PlayerProfile(playerId = "uid_host", name = "HostDriver"),
                roomName = "Friends Paddock",
                maxPlayers = 4,
                discoverable = true,
                rawPassword = "",
                initialRules = RaceRulesSettings()
            )
            awaitCondition { hostEvents.any { it is RaceNavEvent.EnterLobby } }

            clientManager.joinOnlineRoom(
                roomId = "room_SYNC01",
                profile = PlayerProfile(playerId = "uid_client", name = "GuestDriver"),
                rawPassword = ""
            )
            awaitCondition { clientEvents.any { it is RaceNavEvent.EnterLobby } }

            hostManager.startRace()
            awaitCondition {
                hostEvents.any { it is RaceNavEvent.StartRace } &&
                    clientEvents.any { it is RaceNavEvent.StartRace } &&
                    backend.roomFlow.value?.status == "RACING"
            }

            // Simulate host race finish carrying final telemetry + initial rematchJson atomically
            val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
            val rematchAdapter = moshi.adapter(RematchCountdownState::class.java)
            val rematchPayload = RematchCountdownState(
                secondsLeft = 10,
                totalSeconds = 10,
                options = listOf(
                    RematchVoteOption(
                        id = TrackId.WAREHOUSE_DRIFT.name,
                        displayName = "Warehouse Drift Yard",
                        subtitle = "3 Laps"
                    ),
                    RematchVoteOption(
                        id = "RANDOM",
                        displayName = "Random Track",
                        subtitle = "Random next circuit",
                        isRandom = true
                    )
                ),
                leadingOptionId = "RANDOM"
            )
            val finishedSnap = RaceSnapshot(
                raceTimeMs = 38_500L,
                phase = RacePhase.FINISHED.name,
                trackId = TrackId.LIVING_ROOM_GP.name,
                totalLaps = 3,
                racers = listOf(
                    RacerState(
                        playerId = "uid_host",
                        name = "HostDriver",
                        carType = "APEX_FORMULA",
                        primaryColorArgb = 0xFFE53935L,
                        accentColorArgb = 0xFFFFFFFFL,
                        x = 100f,
                        y = 100f,
                        angleRad = 0f,
                        lap = 3,
                        finished = true,
                        finishTimeMs = 38_500L,
                        rank = 1
                    ),
                    RacerState(
                        playerId = "uid_client",
                        name = "GuestDriver",
                        carType = "VOLT_BUGGY",
                        primaryColorArgb = 0xFF1E88E5L,
                        accentColorArgb = 0xFFFFFFFFL,
                        x = 90f,
                        y = 100f,
                        angleRad = 0f,
                        lap = 3,
                        finished = true,
                        finishTimeMs = 39_100L,
                        rank = 2
                    )
                )
            )
            val finalSeq = (backend.roomFlow.value?.telemetrySeq ?: 0L) + 10L
            val finalLine = String(RaceTelemetryCodec.encodeSnapshot(finalSeq, finishedSnap), Charsets.UTF_8)
            val curRoom = backend.roomFlow.value!!
            backend.roomFlow.value = curRoom.copy(
                status = "FINISHED",
                telemetrySeq = finalSeq,
                telemetryLine = finalLine,
                rematchJson = rematchAdapter.toJson(rematchPayload)
            )

            awaitCondition {
                clientEvents.any { it is RaceNavEvent.ShowResults } &&
                    clientManager.rematchState.value != null
            }
            val clientRematch = clientManager.rematchState.value
            assertNotNull("Client must see rematch voting state upon race finish", clientRematch)
            assertEquals(2, clientRematch!!.options.size)

            // Client votes and then triggers returnToLobby(); host must return everyone to lobby
            clientManager.voteRematchOption(TrackId.WAREHOUSE_DRIFT.name)
            awaitCondition {
                backend.playersFlow.value.firstOrNull { it.userId == "uid_client" }?.rematchVote == TrackId.WAREHOUSE_DRIFT.name
            }

            clientManager.returnToLobby()

            awaitCondition {
                backend.roomFlow.value?.status == "OPEN" &&
                    hostEvents.any { it is RaceNavEvent.BackToLobby } &&
                    clientEvents.any { it is RaceNavEvent.BackToLobby }
            }

            assertEquals("OPEN", backend.roomFlow.value?.status)
            assertEquals("", backend.roomFlow.value?.rematchJson)
            awaitCondition {
                backend.playersFlow.value.firstOrNull { it.userId == "uid_client" }?.rematchVote == ""
            }

            hostNavJob.cancel()
            clientNavJob.cancel()
            hostManager.disconnectRelay()
            clientManager.disconnectRelay()
        } finally {
            scope.cancel()
        }
    }
}

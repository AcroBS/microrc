package com.example

import com.example.data.TrackCatalog
import com.example.engine.RacePhysicsEngine
import com.example.model.ChatMessage
import com.example.model.ControlInput
import com.example.model.LobbyPlayer
import com.example.model.LobbyStatePayload
import com.example.model.NetworkMessage
import com.example.model.PacketType
import com.example.model.RacePhase
import com.example.model.RcCarType
import com.example.model.TrackId
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RaceEngineUnitTest {

    @Test
    fun allFiveTracks_haveValidSplinesAndGridPositions() {
        val tracks = TrackCatalog.allTracks()
        assertEquals(5, tracks.size)
        for (track in tracks) {
            assertTrue("Spline should be populated", track.centerline.size >= 100)
            assertTrue("Boost pads should exist", track.boostPads.isNotEmpty())
            assertTrue("Item spawns should exist", track.itemSpawns.size >= 4)
            for (slot in 0 until 4) {
                val (x, y, angle) = TrackCatalog.gridStartTransform(track, slot)
                assertTrue(x.isFinite() && y.isFinite() && angle.isFinite())
                assertTrue(x in 0f..track.worldWidth)
                assertTrue(y in 0f..track.worldHeight)
            }
        }
    }

    @Test
    fun physicsEngine_transitionsFromCountdownToRacingAndMovesCars() {
        val track = TrackCatalog.getTrack(TrackId.NEON_ROOFTOP)
        val players = listOf(
            LobbyPlayer("p1", "Alice", isHost = true, isBot = false, carType = RcCarType.APEX_FORMULA.name),
            LobbyPlayer("b1", "Bot1", isHost = false, isBot = true, carType = RcCarType.VOLT_BUGGY.name)
        )
        val engine = RacePhysicsEngine(track, totalLaps = 3, players = players)

        val initialSnap = engine.step(0.016f)
        assertEquals(RacePhase.COUNTDOWN.name, initialSnap.phase)

        // Advance past 3.2s countdown
        var snap = initialSnap
        repeat(220) {
            engine.submitInput("p1", ControlInput(throttle = 1f, steer = 0.1f))
            snap = engine.step(0.016f)
        }
        assertEquals(RacePhase.RACING.name, snap.phase)
        val alice = snap.racers.first { it.playerId == "p1" }
        assertTrue("Player car should have forward speed", alice.speed > 100f)
    }

    @Test
    fun networkMessage_serializesLobbyAndChatRoundTrip() {
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        val adapter = moshi.adapter(NetworkMessage::class.java)

        val msg = NetworkMessage(
            type = PacketType.LOBBY_STATE,
            senderId = "host_1",
            lobbyState = LobbyStatePayload(
                hostId = "host_1",
                lobbyName = "Alex's RC Lobby",
                hostIp = "192.168.43.1",
                tcpPort = 45456,
                trackId = TrackId.WAREHOUSE_DRIFT.name,
                totalLaps = 3,
                players = listOf(
                    LobbyPlayer("host_1", "Alex", isHost = true, isReady = true)
                ),
                chatHistory = listOf(
                    ChatMessage("m1", "host_1", "Alex", "Ready to drift!", 1000L)
                ),
                inRace = false
            )
        )

        val json = adapter.toJson(msg)
        val decoded = adapter.fromJson(json)
        assertNotNull(decoded)
        assertEquals("192.168.43.1", decoded?.lobbyState?.hostIp)
        assertEquals("Ready to drift!", decoded?.lobbyState?.chatHistory?.firstOrNull()?.text)
    }
}

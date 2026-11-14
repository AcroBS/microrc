package com.example

import com.example.data.TrackCatalog
import com.example.engine.RacePhysicsEngine
import com.example.model.ChatMessage
import com.example.model.ControlInput
import com.example.model.ExternalEventType
import com.example.model.LobbyPlayer
import com.example.model.LobbyStatePayload
import com.example.model.NetworkMessage
import com.example.model.PacketType
import com.example.model.RacePhase
import com.example.model.RaceRulesSettings
import com.example.model.RcCarType
import com.example.model.TrackId
import com.example.model.WinCondition
import com.example.network.RaceTelemetryCodec
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class RaceEngineUnitTest {

    @Test
    fun defaultRaceRules_haveAutoAccelBounceJumpsAndWeatherEnabled() {
        val defaults = RaceRulesSettings()
        assertTrue(defaults.autoAccelerate)
        assertTrue(defaults.hardTrackBoundary)
        assertTrue(defaults.randomJumpsEnabled)
        assertTrue(defaults.randomConditionsEnabled)
        assertEquals(WinCondition.FIRST_TO_FINISH, defaults.winConditionEnum)
    }

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

    @Test
    fun telemetryCodec_roundTripInputAndSnapshotWithEvents() {
        val encodedInput = RaceTelemetryCodec.encodeInput(
            playerId = "rc_player2",
            seq = 42L,
            input = ControlInput(throttle = 0.95f, steer = -0.375f, usePowerUp = true)
        )
        val decodedInput = RaceTelemetryCodec.decodeInput(String(encodedInput, Charsets.UTF_8))
        assertNotNull(decodedInput)
        assertEquals("rc_player2", decodedInput?.playerId)
        assertEquals(42L, decodedInput?.seq)
        assertTrue(decodedInput?.input?.usePowerUp == true)

        val track = TrackCatalog.getTrack(TrackId.LIVING_ROOM_GP)
        val roster = listOf(
            LobbyPlayer("p1", "Host", isHost = true, carType = RcCarType.VOLT_BUGGY.name),
            LobbyPlayer("p2", "Guest", isHost = false, carType = RcCarType.DRIFT_PHANTOM.name)
        )
        val engine = RacePhysicsEngine(
            track = track,
            totalLaps = 3,
            players = roster,
            rules = RaceRulesSettings(
                winCondition = WinCondition.CHAMPIONSHIP_POINTS.name,
                timeLimitSec = 120
            )
        )
        // Advance past countdown then trigger Rain puddles
        repeat(210) { engine.step(0.016f) }
        engine.triggerExternalEvent(ExternalEventType.RAIN, durationMs = 9000L)
        val snap = engine.step(0.016f)

        val encodedSnap = RaceTelemetryCodec.encodeSnapshot(seq = 12L, snap = snap)
        val decodedSnap = RaceTelemetryCodec.decodeSnapshot(
            text = String(encodedSnap, Charsets.UTF_8),
            roster = roster,
            previousSnapshot = null
        )
        assertNotNull(decodedSnap)
        assertEquals(12L, decodedSnap?.seq)
        assertEquals(2, decodedSnap?.snapshot?.racers?.size)
        assertEquals("Guest", decodedSnap?.snapshot?.racers?.get(1)?.name)
        assertEquals(RcCarType.DRIFT_PHANTOM.name, decodedSnap?.snapshot?.racers?.get(1)?.carType)
        assertEquals(ExternalEventType.RAIN.name, decodedSnap?.snapshot?.activeEvent)
        assertTrue("Rain puddles should serialize across network", decodedSnap?.snapshot?.rainPuddles?.isNotEmpty() == true)
        assertEquals(WinCondition.CHAMPIONSHIP_POINTS.name, decodedSnap?.snapshot?.winCondition)
        assertEquals(120, decodedSnap?.snapshot?.timeLimitSec)
    }

    @Test
    fun randomTrackGenerator_createsValidTrackWithJumpsAndBuildsSpline() {
        val custom = TrackCatalog.generateRandomTrack(seed = 123456L)
        assertTrue(custom.controlPoints.size >= 8)
        assertTrue(custom.ramps.isNotEmpty())
        val built = TrackCatalog.buildCustomTrack(custom)
        assertTrue(built.centerline.size >= 100)
        assertTrue(built.itemSpawns.isNotEmpty())
        assertTrue(built.boostPads.isNotEmpty())
        assertEquals(custom.ramps.size, built.ramps.size)
    }

    @Test
    fun hardTrackBoundary_keepsCarInsideKerbAndAllowsReversingOut() {
        val track = TrackCatalog.getTrack(TrackId.LIVING_ROOM_GP)
        val players = listOf(
            LobbyPlayer("p1", "Tester", isHost = true, isBot = false, carType = RcCarType.VOLT_BUGGY.name)
        )
        val engine = RacePhysicsEngine(
            track = track,
            totalLaps = 3,
            players = players,
            rules = RaceRulesSettings(hardTrackBoundary = true, randomConditionsEnabled = false)
        )
        var snap = engine.step(0.016f)
        // Drive outward into the wall
        repeat(260) {
            engine.submitInput("p1", ControlInput(throttle = 1f, steer = 1f))
            snap = engine.step(0.016f)
        }
        val r = snap.racers.first()
        val (idx, dist) = TrackCatalog.nearestTrackPoint(track, r.x, r.y)
        val maxAllowed = track.centerline[idx].halfWidth + 5f
        assertTrue("Car should stay bounded inside kerb ($dist <= $maxAllowed)", dist <= maxAllowed)

        // Now apply reverse input — car must actually gain negative forward velocity instead of getting stuck
        repeat(45) {
            engine.submitInput("p1", ControlInput(throttle = -1f, steer = 0f))
            snap = engine.step(0.016f)
        }
        val afterRev = snap.racers.first()
        val forwardVel = afterRev.vx * cos(afterRev.angleRad) + afterRev.vy * sin(afterRev.angleRad)
        assertTrue("Car must reverse cleanly away from wall (forwardVel=$forwardVel)", forwardVel < -40f)
    }

    @Test
    fun jumpRamps_launchCarAirborneAndSyncJumpZ() {
        val track = TrackCatalog.getTrack(TrackId.NEON_ROOFTOP)
        assertTrue("Track should have default ramps", track.ramps.isNotEmpty())
        val withRandJumps = TrackCatalog.withRandomJumps(track, enabled = true, seed = 777L)
        assertTrue("Randomized jumps should spawn", withRandJumps.ramps.size in 2..4)

        val players = listOf(
            LobbyPlayer("p1", "Jumper", isHost = true, isBot = false, carType = RcCarType.APEX_FORMULA.name)
        )
        val aheadIdx = (track.centerline.size - 1)
        val rampTrack = track.copy(
            ramps = listOf(TrackCatalog.rampAt(track.centerline, aheadIdx))
        )
        val engine = RacePhysicsEngine(rampTrack, totalLaps = 3, players = players)
        var maxJumpZ = 0f
        var snap = engine.step(0.016f)
        repeat(280) {
            engine.submitInput("p1", ControlInput(throttle = 1f, steer = 0f))
            snap = engine.step(0.016f)
            val z = snap.racers.first().jumpZ
            if (z > maxJumpZ) maxJumpZ = z
        }
        assertTrue("Car should become airborne on ramp ($maxJumpZ > 0.1f)", maxJumpZ > 0.1f)
    }

    @Test
    fun finishedHumanPlayer_doesNotGetHijackedByAutopilot() {
        val track = TrackCatalog.getTrack(TrackId.LIVING_ROOM_GP)
        val players = listOf(
            LobbyPlayer("p1", "Human1", isHost = true, isBot = false, carType = RcCarType.VOLT_BUGGY.name),
            LobbyPlayer("p2", "Human2", isHost = false, isBot = false, carType = RcCarType.APEX_FORMULA.name)
        )
        val engine = RacePhysicsEngine(
            track = track,
            totalLaps = 1,
            players = players,
            rules = RaceRulesSettings(autoAccelerate = true, randomConditionsEnabled = false)
        )

        // Drive p1 cleanly around 1 lap by steering toward lookahead
        repeat(210) { engine.step(0.016f) }
        var p1Finished = false
        var steps = 0
        var snap = engine.step(0.016f)
        while (!p1Finished && steps < 1800) {
            val r = snap.racers.first { it.playerId == "p1" }
            val (idx, _) = TrackCatalog.nearestTrackPoint(track, r.x, r.y)
            val target = track.centerline[(idx + 7) % track.centerline.size]
            val desired = kotlin.math.atan2(target.y - r.y, target.x - r.x)
            val diff = RacePhysicsEngine.normalizeAngle(desired - r.angleRad)
            engine.submitInput("p1", ControlInput(throttle = 1f, steer = (diff / 0.6f).coerceIn(-1f, 1f)))
            engine.submitInput("p2", ControlInput(throttle = 0f, steer = 0f))
            snap = engine.step(0.016f)
            p1Finished = snap.racers.first { it.playerId == "p1" }.finished
            steps++
        }
        assertTrue("Human p1 should complete 1 lap", p1Finished)
        assertFalse("p2 has not finished so race stays active during grace period", snap.phase == RacePhase.FINISHED.name)

        // Now even if autoAccelerate is true, finished human p1 receives throttle=1f & steer=0f and must coast down, NOT steer/accelerate autonomously
        val angleAtFinish = snap.racers.first { it.playerId == "p1" }.angleRad
        repeat(90) {
            engine.submitInput("p1", ControlInput(throttle = 1f, steer = 0f))
            snap = engine.step(0.016f)
        }
        val afterCoast = snap.racers.first { it.playerId == "p1" }
        assertTrue("Finished human under auto-gas should coast to near-stop (${afterCoast.speed})", afterCoast.speed < 80f)
        val angleDelta = kotlin.math.abs(RacePhysicsEngine.normalizeAngle(afterCoast.angleRad - angleAtFinish))
        assertTrue("Finished human angle should not be autonomously steered ($angleDelta)", angleDelta < 0.35f)
    }

    @Test
    fun timeTrialWinCondition_finishesWhenTimeLimitExpires() {
        val track = TrackCatalog.getTrack(TrackId.LIVING_ROOM_GP)
        val players = listOf(
            LobbyPlayer("p1", "TimeTester", isHost = true, isBot = false),
            LobbyPlayer("b1", "Bot1", isHost = false, isBot = true)
        )
        val engine = RacePhysicsEngine(
            track = track,
            totalLaps = 3,
            players = players,
            rules = RaceRulesSettings(
                winCondition = WinCondition.TIME_TRIAL_DISTANCE.name,
                timeLimitSec = 30,
                randomConditionsEnabled = false
            )
        )
        // Countdown (3.2s) + 30.5s racing at dt=0.05s -> 680 steps
        var snap = engine.step(0.05f)
        repeat(680) {
            engine.submitInput("p1", ControlInput(throttle = 1f, steer = 0f))
            snap = engine.step(0.05f)
        }
        assertEquals(RacePhase.FINISHED.name, snap.phase)
        assertTrue(snap.racers.all { it.finished })
    }
}

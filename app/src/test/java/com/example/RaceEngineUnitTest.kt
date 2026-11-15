package com.example

import com.example.data.TrackCatalog
import com.example.engine.RacePhysicsEngine
import com.example.model.ArenaShape
import com.example.model.ChatMessage
import com.example.model.ControlInput
import com.example.model.ExternalEventType
import com.example.model.GameMode
import com.example.model.LobbyPlayer
import com.example.model.LobbyStatePayload
import com.example.model.NetworkMessage
import com.example.model.PacketType
import com.example.model.PropShape
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
    fun defaultRaceRules_haveAutoAccelBounceJumpsWeatherAndNameTagsEnabled() {
        val defaults = RaceRulesSettings()
        assertTrue(defaults.autoAccelerate)
        assertTrue(defaults.hardTrackBoundary)
        assertTrue(defaults.randomJumpsEnabled)
        assertTrue(defaults.randomConditionsEnabled)
        assertTrue(defaults.showPlayerNames)
        assertEquals(WinCondition.FIRST_TO_FINISH, defaults.winConditionEnum)

        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        val adapter = moshi.adapter(RaceRulesSettings::class.java)
        val decoded = adapter.fromJson(adapter.toJson(defaults.copy(showPlayerNames = false)))
        assertFalse(decoded?.showPlayerNames ?: true)
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

    @Test
    fun allArenaShapes_haveValidOpenBoundariesBoostsJumpsAndOilSlicks() {
        val arenas = TrackCatalog.allArenas()
        assertEquals(5, arenas.size)
        for (arena in arenas) {
            assertTrue(arena.isArena)
            assertNotNull(arena.arenaShape)
            assertTrue("Arena boundary should be populated", arena.arenaBoundary.size >= 40)
            assertTrue("Arena should have speed-up boost pads", arena.boostPads.isNotEmpty())
            assertTrue("Arena should have jump ramps", arena.ramps.isNotEmpty())
            assertTrue("Arena should have oil slick spin hazards", arena.props.any { it.shape == PropShape.MUD_POOL })
            for (slot in 0 until 4) {
                val (sx, sy, _) = TrackCatalog.gridStartTransform(arena, slot)
                assertTrue(
                    "Start slot $slot must be inside ${arena.arenaShape} polygon",
                    TrackCatalog.isPointInPolygon(sx, sy, arena.arenaBoundary)
                )
            }
        }
    }

    @Test
    fun arenaMode_flagPickupCarryCounterSpinDropCollisionStealAndWinTarget() {
        val baseArena = TrackCatalog.getArenaTrack(ArenaShape.CIRCLE)
        // Place one deterministic oil patch dead-ahead of slot 0 for spin-drop testing
        val (p1StartX, p1StartY, p1StartAng) = TrackCatalog.gridStartTransform(baseArena, 0)
        val oilX = p1StartX + cos(p1StartAng) * 120f
        val oilY = p1StartY + sin(p1StartAng) * 120f
        val testArena = baseArena.copy(
            ramps = emptyList(),
            props = listOf(
                baseArena.props.first().copy(
                    x = oilX,
                    y = oilY,
                    radius = 46f,
                    shape = PropShape.MUD_POOL
                )
            )
        )

        val players = listOf(
            LobbyPlayer("p1", "DriverOne", isHost = true, isBot = false, carType = RcCarType.VOLT_BUGGY.name),
            LobbyPlayer("p2", "DriverTwo", isHost = false, isBot = false, carType = RcCarType.APEX_FORMULA.name)
        )
        val rules = RaceRulesSettings(
            gameMode = GameMode.ARENA.name,
            arenaTargetMinutes = 1,
            autoAccelerate = false,
            hardTrackBoundary = true,
            randomConditionsEnabled = false
        )
        val engine = RacePhysicsEngine(testArena, totalLaps = 3, players = players, rules = rules)

        // Advance past 3.2s countdown with cars stationary
        var snap = engine.step(0.05f)
        repeat(66) {
            snap = engine.step(0.05f)
        }
        assertEquals(RacePhase.RACING.name, snap.phase)
        assertEquals(GameMode.ARENA.name, snap.gameMode)
        assertNotNull(snap.arenaFlag)

        // 1. Place loose flag at p1's position -> p1 picks up flag and carry counter increments!
        val r1 = snap.racers.first { it.playerId == "p1" }
        engine.setFlagStateForTest(r1.x, r1.y, carrierId = null)
        snap = engine.step(0.05f)
        assertEquals("p1", snap.arenaFlag?.carrierId)

        repeat(20) {
            snap = engine.step(0.05f)
        }
        val carriedAfter1Sec = snap.racers.first { it.playerId == "p1" }.flagCarryMs
        assertTrue("Flag carry counter should count milliseconds carried ($carriedAfter1Sec >= 900)", carriedAfter1Sec >= 900L)

        // 2. Drive p1 straight forward into the oil slick -> p1 spins out and loses the flag!
        var droppedOnSpin = false
        repeat(40) {
            engine.submitInput("p1", ControlInput(throttle = 1f, steer = 0f))
            snap = engine.step(0.04f)
            if (snap.arenaFlag?.carrierId == null) {
                droppedOnSpin = true
            }
        }
        assertTrue("Driving onto oil/puddle patch must spin car & drop the flag", droppedOnSpin)

        // 3. Give flag to p2, then ram p1 into p2 -> flag transfers to p1 on collision!
        val cleanArena = baseArena.copy(ramps = emptyList(), props = emptyList())
        val collisionEngine = RacePhysicsEngine(cleanArena, totalLaps = 3, players = players, rules = rules)
        repeat(68) { collisionEngine.step(0.05f) }
        val cSnap0 = collisionEngine.step(0.05f)
        val cP2 = cSnap0.racers.first { it.playerId == "p2" }
        collisionEngine.setFlagStateForTest(cP2.x, cP2.y, carrierId = "p2")

        var stolenByP1 = false
        var collideSteps = 0
        var cSnap = collisionEngine.step(0.04f)
        while (!stolenByP1 && collideSteps < 120) {
            val curP1 = cSnap.racers.first { it.playerId == "p1" }
            val curP2 = cSnap.racers.first { it.playerId == "p2" }
            val desired = kotlin.math.atan2(curP2.y - curP1.y, curP2.x - curP1.x)
            val diff = RacePhysicsEngine.normalizeAngle(desired - curP1.angleRad)
            collisionEngine.submitInput("p1", ControlInput(throttle = 1f, steer = (diff / 0.5f).coerceIn(-1f, 1f)))
            collisionEngine.submitInput("p2", ControlInput(throttle = 0f, steer = 0f))
            cSnap = collisionEngine.step(0.04f)
            if (cSnap.arenaFlag?.carrierId == "p1") {
                stolenByP1 = true
            }
            collideSteps++
        }
        assertTrue("Colliding with the flag carrier must transfer the flag to opponent", stolenByP1)

        // 4. Hold the flag until the 1-minute (60,000 ms) server winning condition is reached
        var winSteps = 0
        while (cSnap.phase != RacePhase.FINISHED.name && winSteps < 1500) {
            val backAwayThrottle = if (winSteps < 12) -1f else 0f
            collisionEngine.submitInput("p1", ControlInput(throttle = backAwayThrottle, steer = 0.8f))
            collisionEngine.submitInput("p2", ControlInput(throttle = 0f, steer = 0f))
            cSnap = collisionEngine.step(0.05f)
            winSteps++
        }
        assertEquals("Match should finish once carrier reaches target minutes", RacePhase.FINISHED.name, cSnap.phase)
        val winner = cSnap.racers.first { it.rank == 1 }
        assertEquals("p1", winner.playerId)
        assertEquals(60_000L, winner.flagCarryMs)
    }

    @Test
    fun telemetryCodec_roundTripArenaFlagAndCarryTimers() {
        val arena = TrackCatalog.getArenaTrack(ArenaShape.STAR)
        val roster = listOf(
            LobbyPlayer("p1", "Host", isHost = true, carType = RcCarType.VOLT_BUGGY.name),
            LobbyPlayer("p2", "Rival", isHost = false, carType = RcCarType.TUNDRA_MONSTER.name)
        )
        val engine = RacePhysicsEngine(
            track = arena,
            totalLaps = 3,
            players = roster,
            rules = RaceRulesSettings(
                gameMode = GameMode.ARENA.name,
                arenaTargetMinutes = 3,
                randomConditionsEnabled = false
            )
        )
        repeat(70) { engine.step(0.05f) }
        val snap0 = engine.step(0.05f)
        val p2 = snap0.racers.first { it.playerId == "p2" }
        engine.setFlagStateForTest(p2.x, p2.y, carrierId = "p2")
        val snap = engine.step(0.05f)

        val bytes = RaceTelemetryCodec.encodeSnapshot(seq = 99L, snap = snap)
        val decoded = RaceTelemetryCodec.decodeSnapshot(
            text = String(bytes, Charsets.UTF_8),
            roster = roster,
            previousSnapshot = null,
            activeTrack = arena
        )
        assertNotNull(decoded)
        assertEquals(GameMode.ARENA.name, decoded?.snapshot?.gameMode)
        assertEquals(ArenaShape.STAR.name, decoded?.snapshot?.arenaShape)
        assertEquals(180_000L, decoded?.snapshot?.arenaTargetMs)
        assertEquals("p2", decoded?.snapshot?.arenaFlag?.carrierId)
        val decodedP2 = decoded?.snapshot?.racers?.first { it.playerId == "p2" }
        assertTrue((decodedP2?.flagCarryMs ?: 0L) >= 50L)
    }
}

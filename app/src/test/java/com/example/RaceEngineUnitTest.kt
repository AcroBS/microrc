package com.example

import com.example.audio.RaceAudioEngine
import com.example.data.TrackCatalog
import com.example.engine.RacePhysicsEngine
import com.example.model.ArenaShape
import com.example.model.CarSpeedPreset
import com.example.model.ChatMessage
import com.example.model.ControlInput
import com.example.model.ExternalEventType
import com.example.model.FieldPickupType
import com.example.model.GameMode
import com.example.model.HazardType
import com.example.model.LobbyPlayer
import com.example.model.LobbyStatePayload
import com.example.model.NetworkMessage
import com.example.model.PacketType
import com.example.model.PowerUpType
import com.example.model.PropShape
import com.example.model.RacePhase
import com.example.model.RaceRulesSettings
import com.example.model.RaceSnapshot
import com.example.model.RcCarType
import com.example.model.TrackId
import com.example.model.WinCondition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import com.example.network.RaceTelemetryCodec
import com.example.network.WifiP2pRaceManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import android.view.Surface
import com.example.sensors.TiltSteeringController
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

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
        val engine = RacePhysicsEngine(testArena, totalLaps = 3, players = players, rules = rules, random = Random(42L))

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
        val cleanArena = baseArena.copy(ramps = emptyList(), props = emptyList(), itemSpawns = emptyList())
        val collisionEngine = RacePhysicsEngine(cleanArena, totalLaps = 3, players = players, rules = rules, random = Random(99L))
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
            ),
            random = Random(7L)
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

    @Test
    fun arenaMode_activeShieldAllowsLooseFlagPickupAndReclaimWithoutLosingShield() {
        val arena = TrackCatalog.getArenaTrack(ArenaShape.CIRCLE).copy(
            ramps = emptyList(),
            props = emptyList(),
            itemSpawns = emptyList()
        )
        val players = listOf(
            LobbyPlayer("p1", "ShieldDriver", isHost = true, isBot = false, carType = RcCarType.VOLT_BUGGY.name),
            LobbyPlayer("p2", "Rival", isHost = false, isBot = false, carType = RcCarType.APEX_FORMULA.name)
        )
        val rules = RaceRulesSettings(
            gameMode = GameMode.ARENA.name,
            arenaTargetMinutes = 1,
            autoAccelerate = false,
            randomConditionsEnabled = false
        )
        val engine = RacePhysicsEngine(arena, totalLaps = 3, players = players, rules = rules, random = Random(17L))

        // Finish countdown
        var snap = engine.step(0.05f)
        repeat(68) { snap = engine.step(0.05f) }
        assertEquals(RacePhase.RACING.name, snap.phase)

        // Activate Shield on p1 and place loose flag 70px away (overlapping visual shield + flag ring, > old 48px limit)
        engine.givePowerUpForTest("p1", PowerUpType.SHIELD, triggerImmediately = true)
        val r1 = snap.racers.first { it.playerId == "p1" }
        engine.setFlagStateForTest(r1.x + 70f, r1.y, carrierId = null)

        snap = engine.step(0.05f)
        val p1AfterPickup = snap.racers.first { it.playerId == "p1" }
        assertEquals("Shielded car should pick up flag when shield ring touches flag zone", "p1", snap.arenaFlag?.carrierId)
        assertTrue("Shield must remain active on flag pickup", p1AfterPickup.shieldMs > 4000L)

        // Also verify reclaiming a dropped flag immediately after activating a new Shield
        val glueArena = arena.copy(
            props = listOf(
                TrackCatalog.getArenaTrack(ArenaShape.CIRCLE).props.first().copy(
                    x = p1AfterPickup.x,
                    y = p1AfterPickup.y,
                    radius = 50f,
                    shape = PropShape.MUD_POOL
                )
            )
        )
        val reclaimEngine = RacePhysicsEngine(glueArena, totalLaps = 3, players = players, rules = rules, random = Random(23L))
        repeat(68) { reclaimEngine.step(0.05f) }
        var rSnap = reclaimEngine.step(0.05f)
        val startP1 = rSnap.racers.first { it.playerId == "p1" }
        reclaimEngine.setFlagStateForTest(startP1.x, startP1.y, carrierId = "p1")
        // Trigger spin drop on oil patch (sets flagPickupImmunityMs), wait for stun to clear, then pop Shield right on flag
        rSnap = reclaimEngine.step(0.05f)
        assertEquals(null, rSnap.arenaFlag?.carrierId)

        // Simulate rival stealing from p1 (sets 950ms flagPickupImmunityMs without stunning p1), then dropping flag near p1 with Shield
        val cleanEngine = RacePhysicsEngine(arena, totalLaps = 3, players = players, rules = rules, random = Random(31L))
        repeat(68) { cleanEngine.step(0.05f) }
        var cSnap = cleanEngine.step(0.05f)
        val cp1 = cSnap.racers.first { it.playerId == "p1" }
        cleanEngine.givePowerUpForTest("p1", PowerUpType.SHIELD, triggerImmediately = true)
        cleanEngine.setFlagStateForTest(cp1.x + 58f, cp1.y, carrierId = null)
        cSnap = cleanEngine.step(0.05f)
        assertEquals("p1", cSnap.arenaFlag?.carrierId)
    }

    @Test
    fun raceAudioEngine_randomizesSoundtrackPerRunWithoutImmediateRepeats() {
        val scope = CoroutineScope(SupervisorJob())
        val audioEngine = RaceAudioEngine(scope)
        val pickedIndices = mutableListOf<Int>()
        for (run in 1..20) {
            val theme = audioEngine.selectNextRunArrangement(seed = run * 987654321L)
            if (pickedIndices.isNotEmpty()) {
                assertTrue(
                    "Consecutive race runs should switch soundtrack themes (${pickedIndices.last()} vs ${theme.index})",
                    theme.index != pickedIndices.last()
                )
            }
            pickedIndices.add(theme.index)
        }
        assertTrue(
            "Multiple distinct race soundtracks should be chosen across runs",
            pickedIndices.toSet().size >= 3
        )
        audioEngine.stop()
    }

    @Test
    fun carSpeedPresets_scaleVelocityWithFastAsDefault() {
        val defaults = RaceRulesSettings()
        assertEquals(CarSpeedPreset.FAST, defaults.carSpeedEnum)

        val arena = TrackCatalog.getArenaTrack(ArenaShape.SQUARE).copy(
            ramps = emptyList(),
            boostPads = emptyList(),
            props = emptyList(),
            itemSpawns = emptyList()
        )
        val players = listOf(
            LobbyPlayer("p1", "SpeedDriver", isHost = true, isBot = false, carType = RcCarType.APEX_FORMULA.name)
        )

        fun measureTopSpeed(preset: CarSpeedPreset): Float {
            val engine = RacePhysicsEngine(
                track = arena,
                totalLaps = 3,
                players = players,
                rules = RaceRulesSettings(
                    gameMode = GameMode.ARENA.name,
                    carSpeed = preset.name,
                    randomConditionsEnabled = false
                ),
                random = Random(101L)
            )
            repeat(68) { engine.step(0.05f) }
            var snap = engine.step(0.02f)
            repeat(85) {
                engine.submitInput("p1", ControlInput(throttle = 1f, steer = 0f))
                snap = engine.step(0.02f)
            }
            return snap.racers.first().speed
        }

        val lowSpeed = measureTopSpeed(CarSpeedPreset.LOW)
        val medSpeed = measureTopSpeed(CarSpeedPreset.MEDIUM)
        val fastSpeed = measureTopSpeed(CarSpeedPreset.FAST)

        assertTrue("Low speed ($lowSpeed) should be slower than Medium ($medSpeed)", lowSpeed < medSpeed - 25f)
        assertTrue("Medium speed ($medSpeed) should be slower than Fast ($fastSpeed)", medSpeed < fastSpeed - 25f)
    }

    @Test
    fun circuitOnlyPerLapMines_noneInLap1OnePerLapFromLap2OnwardAndDisabledInArena() {
        val track = TrackCatalog.getTrack(TrackId.LIVING_ROOM_GP).copy(
            ramps = emptyList(),
            itemSpawns = emptyList()
        )
        val players = listOf(
            LobbyPlayer("p1", "Leader", isHost = true, isBot = false, carType = RcCarType.VOLT_BUGGY.name),
            LobbyPlayer("p2", "Chaser", isHost = false, isBot = false, carType = RcCarType.APEX_FORMULA.name)
        )
        val engine = RacePhysicsEngine(
            track = track,
            totalLaps = 3,
            players = players,
            rules = RaceRulesSettings(autoAccelerate = true, randomConditionsEnabled = false),
            random = Random(55L)
        )

        repeat(68) { engine.step(0.05f) }
        var snap = engine.step(0.016f)
        assertEquals("No mine in first lap (Lap 1)", 0, snap.racers.first { it.playerId == "p1" }.minesAvailable)
        assertEquals("No mine in first lap (Lap 1)", 0, snap.racers.first { it.playerId == "p2" }.minesAvailable)

        // Pressing dropMine in Lap 1 must NOT drop any mine
        engine.submitInput("p1", ControlInput(throttle = 1f, steer = 0f, dropMine = true))
        snap = engine.step(0.016f)
        assertEquals("Zero mines may be dropped in Lap 1", 0, snap.hazards.count { it.hazardTypeEnum == HazardType.MINE })

        // Helper to drive p1 cleanly until targetLap
        fun driveP1UntilLap(targetLap: Int) {
            var steps = 0
            while (snap.racers.first { it.playerId == "p1" }.lap < targetLap && steps < 2400) {
                val r = snap.racers.first { it.playerId == "p1" }
                val proj = TrackCatalog.nearestTrackSegmentLocal(track, r.x, r.y, r.checkpointIndex)
                val target = track.centerline[(proj.index + 7) % track.centerline.size]
                val desired = kotlin.math.atan2(target.y - r.y, target.x - r.x)
                val diff = RacePhysicsEngine.normalizeAngle(desired - r.angleRad)
                engine.submitInput("p1", ControlInput(throttle = 1f, steer = (diff / 0.6f).coerceIn(-1f, 1f)))
                engine.submitInput("p2", ControlInput(throttle = 0f, steer = 0f))
                snap = engine.step(0.016f)
                steps++
            }
        }

        // Complete Lap 1 -> enter Lap 2: p1 receives 1 mine for Lap 2!
        driveP1UntilLap(2)
        val p1Lap2 = snap.racers.first { it.playerId == "p1" }
        assertEquals(2, p1Lap2.lap)
        assertEquals("Crossing into Lap 2 must grant 1 mine", 1, p1Lap2.minesAvailable)

        // Drop p1's Lap 2 mine -> minesAvailable becomes 0, second press in Lap 2 drops nothing
        engine.submitInput("p1", ControlInput(throttle = 1f, steer = 0f, dropMine = true))
        snap = engine.step(0.016f)
        assertEquals(0, snap.racers.first { it.playerId == "p1" }.minesAvailable)
        assertEquals(1, snap.hazards.count { it.hazardTypeEnum == HazardType.MINE })
        val lap2MineId = snap.hazards.first { it.hazardTypeEnum == HazardType.MINE }.id

        engine.submitInput("p1", ControlInput(throttle = 1f, steer = 0f, dropMine = true))
        snap = engine.step(0.016f)
        assertEquals("Only 1 mine allowed during Lap 2", listOf(lap2MineId), snap.hazards.filter { it.hazardTypeEnum == HazardType.MINE }.map { it.id })

        // Complete Lap 2 -> enter Lap 3: p1 receives 1 new mine for Lap 3!
        driveP1UntilLap(3)
        val p1Lap3 = snap.racers.first { it.playerId == "p1" }
        assertEquals(3, p1Lap3.lap)
        assertEquals("Crossing into Lap 3 must grant 1 new mine for Lap 3", 1, p1Lap3.minesAvailable)

        engine.submitInput("p1", ControlInput(throttle = 1f, steer = 0f, dropMine = true))
        snap = engine.step(0.016f)
        assertEquals(0, snap.racers.first { it.playerId == "p1" }.minesAvailable)
        val lap3Mines = snap.hazards.filter { it.hazardTypeEnum == HazardType.MINE }
        val newestMine = lap3Mines.maxByOrNull { it.id }!!
        assertTrue("Dropping Lap 3 mine must spawn a brand-new mine after Lap 2 mine", newestMine.id > lap2MineId && newestMine.remainingMs >= 44_900L)

        // Pressing dropMine again in Lap 3 must NOT spawn another mine
        val countAfterLap3Drop = lap3Mines.size
        engine.submitInput("p1", ControlInput(throttle = 1f, steer = 0f, dropMine = true))
        snap = engine.step(0.016f)
        assertEquals("Only 1 mine allowed during Lap 3", countAfterLap3Drop, snap.hazards.count { it.hazardTypeEnum == HazardType.MINE })

        // Verify Arena mode has NO mines at all
        val arena = TrackCatalog.getArenaTrack(ArenaShape.CIRCLE).copy(
            ramps = emptyList(),
            props = emptyList(),
            itemSpawns = emptyList()
        )
        val arenaEngine = RacePhysicsEngine(
            track = arena,
            totalLaps = 3,
            players = players,
            rules = RaceRulesSettings(gameMode = GameMode.ARENA.name, autoAccelerate = false, randomConditionsEnabled = false),
            random = Random(66L)
        )
        repeat(68) { arenaEngine.step(0.05f) }
        var aSnap = arenaEngine.step(0.02f)
        assertEquals("Arena mode must have 0 mines", 0, aSnap.racers.first { it.playerId == "p1" }.minesAvailable)
        arenaEngine.grantMineForTest("p1", count = 1)
        arenaEngine.submitInput("p1", ControlInput(throttle = 0f, steer = 0f, dropMine = true))
        aSnap = arenaEngine.step(0.02f)
        assertEquals("Arena mode must never allow dropping mines", 0, aSnap.hazards.count { it.hazardTypeEnum == HazardType.MINE })

        // Now test hitting a mine in Circuit mode: spawns explosion, spins out like oil/puddle, and increments collisionSeq
        val hitEngine = RacePhysicsEngine(
            track = track.copy(props = emptyList()),
            totalLaps = 3,
            players = players,
            rules = RaceRulesSettings(autoAccelerate = false, randomConditionsEnabled = false),
            random = Random(77L)
        )
        repeat(68) { hitEngine.step(0.05f) }
        var hSnap = hitEngine.step(0.02f)
        hitEngine.grantMineForTest("p1", count = 1)
        hitEngine.submitInput("p1", ControlInput(throttle = 0f, steer = 0f, dropMine = true))
        hSnap = hitEngine.step(0.02f)
        val mine = hSnap.hazards.first { it.hazardTypeEnum == HazardType.MINE }

        // Advance > 1.25s so self-grace expires, then back p1 onto the mine to trigger explosion deterministically
        repeat(30) { hSnap = hitEngine.step(0.05f) }
        val beforeColSeq = hSnap.racers.first { it.playerId == "p1" }.collisionSeq
        var exploded = false
        var stunned = false
        repeat(45) {
            val r1 = hSnap.racers.first { it.playerId == "p1" }
            val toMineAng = kotlin.math.atan2(mine.y - r1.y, mine.x - r1.x)
            val revDiff = RacePhysicsEngine.normalizeAngle((toMineAng + Math.PI.toFloat()) - r1.angleRad)
            hitEngine.submitInput("p1", ControlInput(throttle = -1f, steer = (-revDiff).coerceIn(-1f, 1f)))
            hSnap = hitEngine.step(0.03f)
            if (hSnap.explosions.isNotEmpty()) exploded = true
            if (hSnap.racers.first { it.playerId == "p1" }.stunnedMs > 0L) stunned = true
        }
        assertTrue("Mine hit must spawn a small explosion", exploded)
        assertTrue("Mine hit must apply puddle/oil spin-out", stunned)
        assertTrue(
            "Mine hit must increment collisionSeq for haptic feedback",
            hSnap.racers.first { it.playerId == "p1" }.collisionSeq > beforeColSeq
        )
    }

    @Test
    fun directTrackPickups_positionSwapAndSteeringInvertTriggerOnDriveOver() {
        val track = TrackCatalog.getTrack(TrackId.NEON_ROOFTOP).copy(
            ramps = emptyList(),
            props = emptyList(),
            itemSpawns = emptyList()
        )
        val players = listOf(
            LobbyPlayer("p1", "LeaderCar", isHost = true, isBot = false, carType = RcCarType.APEX_FORMULA.name),
            LobbyPlayer("p2", "TrailingCar", isHost = false, isBot = false, carType = RcCarType.VOLT_BUGGY.name)
        )
        val engine = RacePhysicsEngine(
            track = track,
            totalLaps = 3,
            players = players,
            rules = RaceRulesSettings(autoAccelerate = false, randomConditionsEnabled = false),
            random = Random(123L)
        )
        repeat(68) { engine.step(0.05f) }

        // Drive p1 ahead so p1 is clearly leading (rank 1) while p2 stays at start (rank 2)
        var snap = engine.step(0.02f)
        repeat(55) {
            val r1 = snap.racers.first { it.playerId == "p1" }
            val (idx, _) = TrackCatalog.nearestTrackPoint(track, r1.x, r1.y)
            val target = track.centerline[(idx + 7) % track.centerline.size]
            val desired = kotlin.math.atan2(target.y - r1.y, target.x - r1.x)
            val diff = RacePhysicsEngine.normalizeAngle(desired - r1.angleRad)
            engine.submitInput("p1", ControlInput(throttle = 1f, steer = (diff / 0.6f).coerceIn(-1f, 1f)))
            engine.submitInput("p2", ControlInput(throttle = 0f, steer = 0f))
            snap = engine.step(0.02f)
        }
        val leaderBefore = snap.racers.first { it.playerId == "p1" }
        val trailingBefore = snap.racers.first { it.playerId == "p2" }
        assertEquals(1, leaderBefore.rank)
        assertEquals(2, trailingBefore.rank)

        // Spawn Position Swap (double arrow) directly under/ahead of trailing p2
        engine.spawnFieldPickupForTest(trailingBefore.x, trailingBefore.y, FieldPickupType.POSITION_SWAP)
        snap = engine.step(0.02f)

        val p2AfterSwap = snap.racers.first { it.playerId == "p2" }
        val p1AfterSwap = snap.racers.first { it.playerId == "p1" }
        assertEquals("Trailing driver who drove over double arrow should now be rank 1", 1, p2AfterSwap.rank)
        assertEquals("Former leader should now be rank 2", 2, p1AfterSwap.rank)
        assertTrue(
            "p2 should now be at former leader's track position",
            TrackCatalog.distance(p2AfterSwap.x, p2AfterSwap.y, leaderBefore.x, leaderBefore.y) < 35f
        )

        // Spawn Steering Invert directly on p2 -> sets 5s invertedSteerMs and flips steering direction
        val angleBeforeInvert = p2AfterSwap.angleRad
        engine.spawnFieldPickupForTest(p2AfterSwap.x, p2AfterSwap.y, FieldPickupType.STEER_INVERT)
        snap = engine.step(0.02f)
        val p2Inverted = snap.racers.first { it.playerId == "p2" }
        assertTrue("Steering inversion should last ~5s (${p2Inverted.invertedSteerMs})", p2Inverted.invertedSteerMs in 4800L..5000L)

        repeat(10) {
            engine.submitInput("p2", ControlInput(throttle = 1f, steer = 1f))
            snap = engine.step(0.02f)
        }
        val angleAfterSteerRightWhileInverted = snap.racers.first { it.playerId == "p2" }.angleRad
        val delta = RacePhysicsEngine.normalizeAngle(angleAfterSteerRightWhileInverted - angleBeforeInvert)
        assertTrue("Positive steer input while INVERTED must turn negative ($delta < -0.05f)", delta < -0.05f)
    }

    @Test
    fun pairedPortals_teleportDriverForwardOnlyAndNeverBackward() {
        val track = TrackCatalog.getTrack(TrackId.LIVING_ROOM_GP).copy(
            ramps = emptyList(),
            props = emptyList(),
            itemSpawns = emptyList()
        )
        val players = listOf(
            LobbyPlayer("p1", "PortalDriver", isHost = true, isBot = false, carType = RcCarType.VOLT_BUGGY.name)
        )
        val engine = RacePhysicsEngine(
            track = track,
            totalLaps = 3,
            players = players,
            rules = RaceRulesSettings(autoAccelerate = false, randomConditionsEnabled = false),
            random = Random(321L)
        )
        repeat(68) { engine.step(0.05f) }
        var snap = engine.step(0.02f)
        val r0 = snap.racers.first()

        val exitIdx = 42
        val exitPt = track.centerline[exitIdx]
        val exitNext = track.centerline[exitIdx + 1]
        val exitAng = kotlin.math.atan2(exitNext.y - exitPt.y, exitNext.x - exitPt.x)

        // Place upstream entry portal right ahead of p1, and downstream exit portal far forward at index 42
        val entryX = r0.x + cos(r0.angleRad) * 24f
        val entryY = r0.y + sin(r0.angleRad) * 24f
        engine.spawnPortalsForTest(
            entryX = entryX,
            entryY = entryY,
            exitX = exitPt.x,
            exitY = exitPt.y,
            exitAngleRad = exitAng,
            entryColorIsRed = true,
            entryTrackIdx = 2,
            exitTrackIdx = exitIdx
        )

        repeat(8) {
            engine.submitInput("p1", ControlInput(throttle = 1f, steer = 0f))
            snap = engine.step(0.02f)
        }
        val warped = snap.racers.first()
        assertTrue("Portal warp should increment portalWarpSeq", warped.portalWarpSeq >= 1)
        assertTrue(
            "Driver should immediately teleport to downstream exit portal",
            TrackCatalog.distance(warped.x, warped.y, exitPt.x, exitPt.y) < 95f
        )

        // Wait for portal cooldown to expire, then place downstream exit portal right ahead of car:
        // driving over the downstream exit portal must NOT warp the driver backward to entry!
        repeat(40) { snap = engine.step(0.05f) }
        val rAfterCooldown = snap.racers.first()
        val warpsBeforeExitDrive = rAfterCooldown.portalWarpSeq
        engine.spawnPortalsForTest(
            entryX = track.centerline[8].x,
            entryY = track.centerline[8].y,
            exitX = rAfterCooldown.x + cos(rAfterCooldown.angleRad) * 20f,
            exitY = rAfterCooldown.y + sin(rAfterCooldown.angleRad) * 20f,
            exitAngleRad = rAfterCooldown.angleRad,
            entryColorIsRed = false,
            entryTrackIdx = 8,
            exitTrackIdx = 50
        )
        repeat(8) {
            engine.submitInput("p1", ControlInput(throttle = 1f, steer = 0f))
            snap = engine.step(0.02f)
        }
        val afterDrivingOverExit = snap.racers.first()
        assertEquals(
            "Driving over downstream exit portal must NOT teleport backward",
            warpsBeforeExitDrive,
            afterDrivingOverExit.portalWarpSeq
        )
    }

    @Test
    fun telemetryCodec_roundTripsMineDropPortalsFieldPickupsAndInvertedTimer() {
        val encodedInput = RaceTelemetryCodec.encodeInput(
            playerId = "rc_p1",
            seq = 77L,
            input = ControlInput(throttle = 1f, steer = -0.5f, usePowerUp = false, dropMine = true)
        )
        val decodedInput = RaceTelemetryCodec.decodeInput(String(encodedInput, Charsets.UTF_8))
        assertNotNull(decodedInput)
        assertTrue("dropMine flag must round-trip in UDP input packet", decodedInput?.input?.dropMine == true)

        val track = TrackCatalog.getTrack(TrackId.LIVING_ROOM_GP)
        val roster = listOf(
            LobbyPlayer("p1", "Host", isHost = true, carType = RcCarType.VOLT_BUGGY.name),
            LobbyPlayer("p2", "Client", isHost = false, carType = RcCarType.APEX_FORMULA.name)
        )
        val engine = RacePhysicsEngine(
            track = track,
            totalLaps = 3,
            players = roster,
            rules = RaceRulesSettings(randomConditionsEnabled = false),
            random = Random(19L)
        )
        repeat(68) { engine.step(0.05f) }
        engine.grantMineForTest("p1", count = 1)
        engine.spawnFieldPickupForTest(500f, 420f, FieldPickupType.POSITION_SWAP)
        engine.spawnFieldPickupForTest(820f, 640f, FieldPickupType.STEER_INVERT)
        engine.spawnPortalsForTest(
            entryX = 400f,
            entryY = 300f,
            exitX = 1200f,
            exitY = 900f,
            exitAngleRad = 0.75f,
            entryColorIsRed = true,
            entryTrackIdx = 10,
            exitTrackIdx = 40
        )
        val snap = engine.step(0.02f)

        val bytes = RaceTelemetryCodec.encodeSnapshot(seq = 105L, snap = snap)
        val decoded = RaceTelemetryCodec.decodeSnapshot(
            text = String(bytes, Charsets.UTF_8),
            roster = roster,
            previousSnapshot = null,
            activeTrack = track
        )
        assertNotNull(decoded)
        assertEquals(1, decoded?.snapshot?.racers?.first { it.playerId == "p1" }?.minesAvailable)
        assertEquals(2, decoded?.snapshot?.fieldPickups?.size)
        assertNotNull(decoded?.snapshot?.portalPair)
        assertEquals(true, decoded?.snapshot?.portalPair?.entryColorIsRed)
    }

    @Test
    fun arenaSuddenDeath_dropsShrinkingBarriersEvery10sAt2MinAndEnds30sFinale() {
        val arena = TrackCatalog.getArenaTrack(ArenaShape.SQUARE).copy(
            ramps = emptyList(),
            props = emptyList(),
            itemSpawns = emptyList()
        )
        val players = listOf(
            LobbyPlayer("p1", "Alpha", isHost = true, isBot = false, carType = RcCarType.VOLT_BUGGY.name),
            LobbyPlayer("p2", "Bravo", isHost = false, isBot = false, carType = RcCarType.APEX_FORMULA.name)
        )
        val rules = RaceRulesSettings(
            gameMode = GameMode.ARENA.name,
            arenaTargetMinutes = 3, // 180s target so 2:00 Sudden Death triggers when nobody hits limit
            autoAccelerate = false,
            hardTrackBoundary = true,
            randomConditionsEnabled = false
        )
        val engine = RacePhysicsEngine(
            track = arena,
            totalLaps = 3,
            players = players,
            rules = rules,
            random = Random(2026L)
        )

        // Advance past 3.2s countdown
        var snap = engine.step(0.05f)
        while (snap.phase == RacePhase.COUNTDOWN.name) {
            snap = engine.step(0.05f)
        }
        assertEquals(RacePhase.RACING.name, snap.phase)

        // Give p1 a slight flag carry lead (~2s), then drop loose flag near outer edge so nobody hits 3:00 target
        val p1Init = snap.racers.first { it.playerId == "p1" }
        engine.setFlagStateForTest(p1Init.x, p1Init.y, carrierId = "p1")
        repeat(40) { snap = engine.step(0.05f) }
        engine.setFlagStateForTest(arena.worldWidth * 0.84f, arena.worldHeight * 0.84f, carrierId = null)

        // Advance just shy of 2:00 (119.5s) -> Sudden Death must still be inactive
        while (snap.raceTimeMs < 119_500L) {
            snap = engine.step(0.05f)
        }
        assertEquals(0, snap.arenaSuddenDeathStage)
        assertTrue(snap.hazards.none { it.hazardTypeEnum == HazardType.BARRIER })

        // Cross 2:00 (120,000ms) -> Wave 1 triggers and drops perimeter hazard barriers
        while (snap.raceTimeMs < 120_200L) {
            snap = engine.step(0.05f)
        }
        assertEquals("Wave 1 should activate at 2:00", 1, snap.arenaSuddenDeathStage)
        val wave1Barriers = snap.hazards.count { it.hazardTypeEnum == HazardType.BARRIER }
        assertTrue("Wave 1 must drop hazard barriers ($wave1Barriers > 0)", wave1Barriers >= 8)
        assertTrue("Remaining finale clock should be ~29-30s", snap.arenaSuddenDeathRemainingMs in 28_000L..30_000L)

        // Verify UDP telemetry codec round-trips Sudden Death stage, timer, and BARRIER hazards
        val encoded = RaceTelemetryCodec.encodeSnapshot(seq = 500L, snap = snap)
        val decoded = RaceTelemetryCodec.decodeSnapshot(
            text = String(encoded, Charsets.UTF_8),
            roster = players,
            previousSnapshot = null,
            activeTrack = arena
        )
        assertNotNull(decoded)
        assertEquals(1, decoded?.snapshot?.arenaSuddenDeathStage)
        assertEquals(snap.arenaSuddenDeathRemainingMs, decoded?.snapshot?.arenaSuddenDeathRemainingMs)
        assertEquals(
            wave1Barriers,
            decoded?.snapshot?.hazards?.count { it.hazardTypeEnum == HazardType.BARRIER }
        )

        val cx = arena.worldWidth * 0.5f
        val cy = arena.worldHeight * 0.5f
        fun avgBarrierRadiusFromCenter(s: com.example.model.RaceSnapshot): Float {
            val bs = s.hazards.filter { it.hazardTypeEnum == HazardType.BARRIER }
            return bs.map { TrackCatalog.distance(it.x, it.y, cx, cy) }.average().toFloat()
        }
        val wave1AvgRadius = avgBarrierRadiusFromCenter(snap)

        // Cross 2:10 (130,000ms) -> Wave 2 drops further inward barriers
        while (snap.raceTimeMs < 130_200L) {
            snap = engine.step(0.05f)
        }
        assertEquals("Wave 2 should activate at 2:10", 2, snap.arenaSuddenDeathStage)
        val wave2Barriers = snap.hazards.count { it.hazardTypeEnum == HazardType.BARRIER }
        val wave2AvgRadius = avgBarrierRadiusFromCenter(snap)
        assertTrue("Wave 2 must have active hazard barriers ($wave2Barriers >= 10)", wave2Barriers >= 10)
        assertTrue(
            "Wave 2 barriers ($wave2AvgRadius) must drop further inward than Wave 1 ($wave1AvgRadius)",
            wave2AvgRadius < wave1AvgRadius - 60f
        )

        // Cross 2:20 (140,000ms) -> Wave 3 drops tightest finale barriers and pushes loose flag inward
        while (snap.raceTimeMs < 140_200L) {
            snap = engine.step(0.05f)
        }
        assertEquals("Wave 3 should activate at 2:20", 3, snap.arenaSuddenDeathStage)
        val wave3Barriers = snap.hazards.count { it.hazardTypeEnum == HazardType.BARRIER }
        val wave3AvgRadius = avgBarrierRadiusFromCenter(snap)
        assertTrue("Wave 3 must have active hazard barriers ($wave3Barriers >= 10)", wave3Barriers >= 10)
        assertTrue(
            "Wave 3 barriers ($wave3AvgRadius) must drop further inward than Wave 2 ($wave2AvgRadius)",
            wave3AvgRadius < wave2AvgRadius - 60f
        )
        val stage3Poly = TrackCatalog.scaledArenaPolygon(arena, 3)
        val flagAfterShrink = snap.arenaFlag
        assertNotNull(flagAfterShrink)
        assertTrue(
            "Loose flag must be shoved inside shrunken Wave 3 safe zone",
            TrackCatalog.isPointInPolygon(flagAfterShrink!!.x, flagAfterShrink.y, stage3Poly)
        )
        snap.racers.forEach { r ->
            assertTrue(
                "Racer ${r.playerId} must be inside Wave 3 safe zone",
                TrackCatalog.isPointInPolygon(r.x, r.y, stage3Poly)
            )
        }

        // Cross 2:30 (150,000ms) -> 30-second Sudden Death finale concludes and crowns p1
        while (snap.phase != RacePhase.FINISHED.name && snap.raceTimeMs < 152_000L) {
            snap = engine.step(0.05f)
        }
        assertEquals("Sudden Death 30s finale must end match at 2:30", RacePhase.FINISHED.name, snap.phase)
        assertEquals("p1", snap.racers.first { it.rank == 1 }.playerId)
    }

    @Test
    fun rematchVoting_buildsNonRepeatingOptionsAndResolvesVotedOrRandomWinner() {
        val raceOpts = WifiP2pRaceManager.buildRematchVoteOptions(
            gameMode = GameMode.RACE,
            currentTrackId = TrackId.LIVING_ROOM_GP,
            currentArenaShape = ArenaShape.SQUARE,
            random = Random(11L)
        )
        assertEquals(3, raceOpts.size)
        assertFalse("Current circuit should not be offered as a repeat vote option", raceOpts.any { it.id == TrackId.LIVING_ROOM_GP.name })
        assertTrue("Random option must be included", raceOpts.any { it.id == WifiP2pRaceManager.REMATCH_OPTION_RANDOM && it.isRandom })

        val arenaOpts = WifiP2pRaceManager.buildRematchVoteOptions(
            gameMode = GameMode.ARENA,
            currentTrackId = TrackId.WAREHOUSE_DRIFT,
            currentArenaShape = ArenaShape.HEXAGON,
            random = Random(22L)
        )
        assertEquals(3, arenaOpts.size)
        assertFalse("Current arena should not be offered as a repeat vote option", arenaOpts.any { it.id == ArenaShape.HEXAGON.name })
        assertTrue(arenaOpts.last().isRandom)

        // With zero votes, default winner is RANDOM
        assertEquals(
            WifiP2pRaceManager.REMATCH_OPTION_RANDOM,
            WifiP2pRaceManager.computeLeadingRematchOption(raceOpts, emptyMap())
        )
        assertEquals(
            WifiP2pRaceManager.REMATCH_OPTION_RANDOM,
            WifiP2pRaceManager.resolveRematchWinner(raceOpts, emptyMap(), Random(1L))
        )

        // Majority vote selects the voted track
        val votedTrackId = raceOpts[0].id
        val otherTrackId = raceOpts[1].id
        val votes = mapOf(
            "p1" to votedTrackId,
            "p2" to votedTrackId,
            "p3" to otherTrackId
        )
        assertEquals(votedTrackId, WifiP2pRaceManager.computeLeadingRematchOption(raceOpts, votes))
        assertEquals(votedTrackId, WifiP2pRaceManager.resolveRematchWinner(raceOpts, votes, Random(5L)))

        // Moshi serialization round-trip for REMATCH_STATE packet
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        val adapter = moshi.adapter(NetworkMessage::class.java)
        val packet = NetworkMessage(
            type = PacketType.REMATCH_STATE,
            senderId = "host_1",
            rematchState = com.example.model.RematchCountdownState(
                secondsLeft = 7,
                totalSeconds = 10,
                options = raceOpts,
                votesByPlayerId = votes,
                leadingOptionId = votedTrackId
            )
        )
        val decodedPacket = adapter.fromJson(adapter.toJson(packet))
        assertEquals(7, decodedPacket?.rematchState?.secondsLeft)
        assertEquals(votedTrackId, decodedPacket?.rematchState?.leadingOptionId)
        assertEquals(3, decodedPacket?.rematchState?.options?.size)
    }

    @Test
    fun springSliderSteer_appliesAdjustableSensitivityDeadzoneExpoAndFullLock() {
        // Neutral & inside small center deadzone -> exact 0f
        assertEquals(0f, com.example.model.computeSpringSliderSteer(0f), 0.0001f)
        assertEquals(0f, com.example.model.computeSpringSliderSteer(0.015f), 0.0001f)
        assertEquals(0f, com.example.model.computeSpringSliderSteer(-0.015f), 0.0001f)

        // Full left/right lock -> exact -1f / +1f
        assertEquals(1f, com.example.model.computeSpringSliderSteer(1f), 0.0001f)
        assertEquals(-1f, com.example.model.computeSpringSliderSteer(-1f), 0.0001f)

        // Default sensitivity (1.45f) is noticeably more responsive than 1.0f and reaches full lock around ~70% travel
        val lowSensQuarter = com.example.model.computeSpringSliderSteer(0.28f, sensitivity = 0.9f)
        val defaultSensQuarter = com.example.model.computeSpringSliderSteer(0.28f, sensitivity = 1.45f)
        val highSensQuarter = com.example.model.computeSpringSliderSteer(0.28f, sensitivity = 2.2f)
        assertTrue(
            "Higher slider sensitivity must produce stronger steering ($lowSensQuarter < $defaultSensQuarter < $highSensQuarter)",
            lowSensQuarter < defaultSensQuarter && defaultSensQuarter < highSensQuarter
        )
        assertEquals("Default 1.45x sensitivity reaches full lock by ~72% thumb travel", 1f, com.example.model.computeSpringSliderSteer(0.72f), 0.0001f)

        // Monotonicity & left/right symmetry
        val s20 = com.example.model.computeSpringSliderSteer(0.20f)
        val s45 = com.example.model.computeSpringSliderSteer(0.45f)
        val s65 = com.example.model.computeSpringSliderSteer(0.65f)
        assertTrue("Curve must be strictly monotonic ($s20 < $s45 < $s65 <= 1f)", s20 < s45 && s45 < s65 && s65 <= 1f)
        assertEquals("Left/right must be perfectly symmetric", -s45, com.example.model.computeSpringSliderSteer(-0.45f), 0.0001f)
    }

    @Test
    fun builtInTracks_haveNoOverlappingInvisibleWallsOrBlockingProps_andAreVersatileAndDrivable() {
        val allTracks = TrackCatalog.allTracks()
        var hasClockwise = false
        var hasCounterClockwise = false
        val distinctWidths = mutableSetOf<Int>()

        for (track in allTracks) {
            val pts = track.centerline
            val n = pts.size
            distinctWidths.add(pts.first().halfWidth.toInt())

            // Signed polygon area (screen Y-down: > 0 is clockwise, < 0 is counter-clockwise)
            var twiceSignedArea = 0f
            for (i in 0 until n) {
                val a = pts[i]
                val b = pts[(i + 1) % n]
                twiceSignedArea += (a.x * b.y - b.x * a.y)
            }
            if (twiceSignedArea > 0f) hasClockwise = true
            if (twiceSignedArea < 0f) hasCounterClockwise = true

            // 1. Every solid obstacle prop must stay well outside the road + kerb corridor
            for (prop in track.props.filter { it.isSolidObstacle }) {
                for (pt in pts) {
                    val d = TrackCatalog.distance(prop.x, prop.y, pt.x, pt.y)
                    val minClear = pt.halfWidth + prop.radius + 20f
                    assertTrue(
                        "Solid prop (${prop.x},${prop.y}) on ${track.id} encroaches road ($d < $minClear)",
                        d >= minClear
                    )
                }
            }

            // 2. Non-adjacent road legs must never overlap their asphalt/kerb strokes (which hid invisible walls!)
            val minIndexSeparation = 26
            for (i in 0 until n) {
                for (j in (i + 1) until n) {
                    val circDiff = min(j - i, n - (j - i))
                    if (circDiff >= minIndexSeparation) {
                        val d = TrackCatalog.distance(pts[i].x, pts[i].y, pts[j].x, pts[j].y)
                        val minLegSeparation = pts[i].halfWidth + pts[j].halfWidth + 24f
                        assertTrue(
                            "Track ${track.id} has overlapping road legs at $i and $j (dist=$d < $minLegSeparation)",
                            d >= minLegSeparation
                        )
                    }
                }
            }

            // 3. Drive every default circuit cleanly through a full lap with hard boundaries enabled
            val cleanTrack = track.copy(ramps = emptyList())
            val players = listOf(
                LobbyPlayer("p1", "Tester", isHost = true, isBot = false, carType = RcCarType.APEX_FORMULA.name)
            )
            val engine = RacePhysicsEngine(
                track = cleanTrack,
                totalLaps = 1,
                players = players,
                rules = RaceRulesSettings(hardTrackBoundary = true, randomConditionsEnabled = false)
            )
            repeat(68) { engine.step(0.05f) }
            var snap = engine.step(0.016f)
            var visitedLowerRight = false
            var minSpeedInLowerRight = Float.MAX_VALUE
            var steps = 0
            while (!snap.racers.first().finished && steps < 2000) {
                val r = snap.racers.first()
                if (track.id == TrackId.LIVING_ROOM_GP && r.x > 1450f && r.y > 1000f) {
                    visitedLowerRight = true
                    if (r.speed < minSpeedInLowerRight) minSpeedInLowerRight = r.speed
                }
                val proj = TrackCatalog.nearestTrackSegmentLocal(cleanTrack, r.x, r.y, r.checkpointIndex)
                val target = cleanTrack.centerline[(proj.index + 7) % cleanTrack.centerline.size]
                val desired = kotlin.math.atan2(target.y - r.y, target.x - r.x)
                val diff = RacePhysicsEngine.normalizeAngle(desired - r.angleRad)
                engine.submitInput("p1", ControlInput(throttle = 1f, steer = (diff / 0.6f).coerceIn(-1f, 1f)))
                snap = engine.step(0.016f)
                steps++
            }
            assertTrue("Car must finish a clean lap on ${track.id}", snap.racers.first().finished)
            if (track.id == TrackId.LIVING_ROOM_GP) {
                assertTrue("Car must traverse lower-right corner of LIVING_ROOM_GP", visitedLowerRight)
                assertTrue(
                    "Car must maintain clean speed through lower-right corner without slamming invisible wall ($minSpeedInLowerRight)",
                    minSpeedInLowerRight > 280f
                )
            }
        }

        assertTrue("Built-in tracks should include both clockwise and counter-clockwise circuits", hasClockwise && hasCounterClockwise)
        assertTrue("Built-in tracks should offer varied road widths (${distinctWidths.size} >= 4)", distinctWidths.size >= 4)
    }

    @Test
    fun tiltSteering_recognizesLandscapeAndPortraitWithoutDrivingInCircles() {
        // Portrait (ROTATION_0): level phone has x = 0, y = +9.81 -> raw tilt axis = 0
        assertEquals(0f, TiltSteeringController.computeRawTiltAxis(x = 0f, y = 9.81f, rotation = Surface.ROTATION_0), 0.001f)
        assertTrue("Portrait CW tilt (x < 0) steers right", TiltSteeringController.computeRawTiltAxis(x = -3.5f, y = 9.0f, rotation = Surface.ROTATION_0) > 0f)

        // Landscape Left (ROTATION_90): level phone has x = +9.81, y = 0 -> raw tilt axis MUST be 0 (not -9.81!)
        val rot90Level = TiltSteeringController.computeRawTiltAxis(x = 9.81f, y = 0f, rotation = Surface.ROTATION_90)
        assertEquals("Level phone in Landscape ROTATION_90 must output 0 tilt", 0f, rot90Level, 0.001f)
        assertEquals(0f, TiltSteeringController.computeSteerFromSmoothedTilt(rot90Level / 5.2f), 0.001f)
        assertTrue("Landscape ROTATION_90 CW tilt (y > 0) steers right", TiltSteeringController.computeRawTiltAxis(x = 9.0f, y = 3.5f, rotation = Surface.ROTATION_90) > 0f)
        assertTrue("Landscape ROTATION_90 CCW tilt (y < 0) steers left", TiltSteeringController.computeRawTiltAxis(x = 9.0f, y = -3.5f, rotation = Surface.ROTATION_90) < 0f)

        // Reverse Landscape (ROTATION_270): level phone has x = -9.81, y = 0 -> raw tilt axis MUST be 0 (not +9.81!)
        val rot270Level = TiltSteeringController.computeRawTiltAxis(x = -9.81f, y = 0f, rotation = Surface.ROTATION_270)
        assertEquals("Level phone in Landscape ROTATION_270 must output 0 tilt", 0f, rot270Level, 0.001f)
        assertEquals(0f, TiltSteeringController.computeSteerFromSmoothedTilt(rot270Level / 5.2f), 0.001f)
        assertTrue("Landscape ROTATION_270 CW tilt (y < 0) steers right", TiltSteeringController.computeRawTiltAxis(x = -9.0f, y = -3.5f, rotation = Surface.ROTATION_270) > 0f)
        assertTrue("Landscape ROTATION_270 CCW tilt (y > 0) steers left", TiltSteeringController.computeRawTiltAxis(x = -9.0f, y = 3.5f, rotation = Surface.ROTATION_270) < 0f)
    }

    @Test
    fun telemetryCodec_roundTripsUdpAckAndSanitizesNewlinesForTcpFallback() {
        val helloBytes = RaceTelemetryCodec.encodeHello("driver|1\ntest", ackedHostUdpSeq = 33L)
        val decodedHello = RaceTelemetryCodec.decodeHelloPacket(String(helloBytes, Charsets.UTF_8))
        assertNotNull(decodedHello)
        assertEquals("driver1test", decodedHello?.playerId)
        assertEquals(33L, decodedHello?.ackedHostUdpSeq)

        val inputLine = RaceTelemetryCodec.encodeInputLine(
            playerId = "client\n2",
            seq = 19L,
            input = ControlInput(throttle = 1f, steer = 0.42f),
            ackedHostUdpSeq = 44L
        )
        assertFalse("Compact TCP input line must never contain embedded newlines", inputLine.contains('\n'))
        val decodedInput = RaceTelemetryCodec.decodeInput(inputLine)
        assertNotNull(decodedInput)
        assertEquals(19L, decodedInput?.seq)
        assertEquals(44L, decodedInput?.ackedHostUdpSeq)
        assertEquals(0.42f, decodedInput?.input?.steer ?: 0f, 0.002f)
    }

    @Test
    fun electroShock_freezesNearbyCarsMotionlessDropsFlagAndShieldAbsorbs() {
        val arena = TrackCatalog.getArenaTrack(ArenaShape.CIRCLE).copy(
            ramps = emptyList(),
            props = emptyList(),
            itemSpawns = emptyList()
        )
        val players = listOf(
            LobbyPlayer("p1", "Shocker", isHost = true, isBot = false, carType = RcCarType.VOLT_BUGGY.name),
            LobbyPlayer("p2", "VictimOne", isHost = false, isBot = false, carType = RcCarType.APEX_FORMULA.name),
            LobbyPlayer("p3", "ShieldedCar", isHost = false, isBot = false, carType = RcCarType.DRIFT_PHANTOM.name),
            LobbyPlayer("p4", "DistantCar", isHost = false, isBot = false, carType = RcCarType.TUNDRA_MONSTER.name)
        )
        val rules = RaceRulesSettings(
            gameMode = GameMode.ARENA.name,
            arenaTargetMinutes = 3,
            autoAccelerate = false,
            randomConditionsEnabled = false
        )
        val engine = RacePhysicsEngine(arena, totalLaps = 3, players = players, rules = rules, random = Random(901L))

        var snap = engine.step(0.05f)
        while (snap.phase == RacePhase.COUNTDOWN.name) {
            snap = engine.step(0.05f)
        }

        // Steer p1 and p3 within 115px of p2 (without colliding) while p4 stays far across the arena
        var approachSteps = 0
        while (approachSteps < 140) {
            val r1 = snap.racers.first { it.playerId == "p1" }
            val r2 = snap.racers.first { it.playerId == "p2" }
            val r3 = snap.racers.first { it.playerId == "p3" }
            val d12 = TrackCatalog.distance(r1.x, r1.y, r2.x, r2.y)
            val d32 = TrackCatalog.distance(r3.x, r3.y, r2.x, r2.y)
            if (d12 <= 115f && d32 <= 115f) break

            if (d12 > 105f) {
                val desired1 = kotlin.math.atan2(r2.y - r1.y, r2.x - r1.x)
                val diff1 = RacePhysicsEngine.normalizeAngle(desired1 - r1.angleRad)
                engine.submitInput("p1", ControlInput(throttle = 0.85f, steer = (diff1 / 0.5f).coerceIn(-1f, 1f)))
            } else {
                engine.submitInput("p1", ControlInput(throttle = 0f, steer = 0f))
            }

            if (d32 > 105f) {
                val desired3 = kotlin.math.atan2(r2.y - r3.y, r2.x - r3.x)
                val diff3 = RacePhysicsEngine.normalizeAngle(desired3 - r3.angleRad)
                engine.submitInput("p3", ControlInput(throttle = 0.85f, steer = (diff3 / 0.5f).coerceIn(-1f, 1f)))
            } else {
                engine.submitInput("p3", ControlInput(throttle = 0f, steer = 0f))
            }

            engine.submitInput("p2", ControlInput(throttle = 0f, steer = 0f))
            engine.submitInput("p4", ControlInput(throttle = 0f, steer = 0f))
            snap = engine.step(0.03f)
            approachSteps++
        }

        val r1Near = snap.racers.first { it.playerId == "p1" }
        val r2Near = snap.racers.first { it.playerId == "p2" }
        val r3Near = snap.racers.first { it.playerId == "p3" }
        assertTrue(
            "p1 and p2 should be within Electro Shock 155px radius",
            TrackCatalog.distance(r1Near.x, r1Near.y, r2Near.x, r2Near.y) < 145f
        )
        assertTrue(
            "p1 and p3 should be within Electro Shock 155px radius",
            TrackCatalog.distance(r1Near.x, r1Near.y, r3Near.x, r3Near.y) < 145f
        )

        // Give p2 the Arena flag, give p3 an active Shield, and give p1 Electro Shock to trigger via input
        engine.setFlagStateForTest(r2Near.x, r2Near.y, carrierId = "p2")
        engine.givePowerUpForTest("p3", PowerUpType.SHIELD, triggerImmediately = true)
        engine.givePowerUpForTest("p1", PowerUpType.ELECTRO_SHOCK, triggerImmediately = false)
        engine.submitInput("p1", ControlInput(throttle = 0f, steer = 0f, usePowerUp = true))
        snap = engine.step(0.04f)

        val p1AfterShock = snap.racers.first { it.playerId == "p1" }
        val p2AfterShock = snap.racers.first { it.playerId == "p2" }
        val p3AfterShock = snap.racers.first { it.playerId == "p3" }
        val p4AfterShock = snap.racers.first { it.playerId == "p4" }

        assertTrue("Activating car must have ~4s Electro Shock aura (${p1AfterShock.electroShockAuraMs})", p1AfterShock.electroShockAuraMs >= 3900L)
        assertTrue("Nearby unshielded car p2 must be electro-paralyzed (${p2AfterShock.electroParalyzedMs})", p2AfterShock.electroParalyzedMs >= 2700L)
        assertEquals("Paralyzed car velocity must immediately zero out", 0f, p2AfterShock.speed, 0.01f)
        assertEquals("Paralyzed Arena flag carrier must drop the flag", null, snap.arenaFlag?.carrierId)
        assertEquals("Shielded car p3 must not be paralyzed", 0L, p3AfterShock.electroParalyzedMs)
        assertEquals("Shield on p3 must break when absorbing Electro Shock", 0L, p3AfterShock.shieldMs)
        assertEquals("Distant car p4 outside proximity must be unaffected", 0L, p4AfterShock.electroParalyzedMs)

        // Hold full throttle + steering on paralyzed p2 for 1.2s -> p2 must stay completely motionless!
        val frozenX = p2AfterShock.x
        val frozenY = p2AfterShock.y
        repeat(30) {
            engine.submitInput("p1", ControlInput(throttle = -0.8f, steer = 0f)) // back p1 away so aura doesn't re-hit
            engine.submitInput("p2", ControlInput(throttle = 1f, steer = 1f))
            snap = engine.step(0.04f)
        }
        val p2WhileParalyzed = snap.racers.first { it.playerId == "p2" }
        assertTrue("p2 should still be paralyzed after 1.2s (${p2WhileParalyzed.electroParalyzedMs})", p2WhileParalyzed.electroParalyzedMs > 1000L)
        assertEquals("Paralyzed car speed must remain 0", 0f, p2WhileParalyzed.speed, 0.01f)
        assertEquals("Paralyzed car X must remain motionless", frozenX, p2WhileParalyzed.x, 0.01f)
        assertEquals("Paralyzed car Y must remain motionless", frozenY, p2WhileParalyzed.y, 0.01f)

        // Wait for paralysis to expire (~1.8s more), then verify p2 accelerates normally again
        repeat(50) {
            engine.submitInput("p1", ControlInput(throttle = 0f, steer = 0f))
            engine.submitInput("p2", ControlInput(throttle = 1f, steer = 0f))
            snap = engine.step(0.05f)
        }
        val p2Recovered = snap.racers.first { it.playerId == "p2" }
        assertEquals(0L, p2Recovered.electroParalyzedMs)
        assertTrue("Recovered car must regain forward speed (${p2Recovered.speed} > 80f)", p2Recovered.speed > 80f)
    }

    @Test
    fun stuntsMode_providesStartingNitroParksSpectatorsAndRotatesAllFourStuntsInRandomOrder() {
        val players = listOf(
            LobbyPlayer("p1", "Alice", isHost = true, isBot = false, carType = RcCarType.APEX_FORMULA.name),
            LobbyPlayer("p2", "Bob", isHost = false, isBot = false, carType = RcCarType.VOLT_BUGGY.name),
            LobbyPlayer("b1", "BotRex", isHost = false, isBot = true, carType = RcCarType.DRIFT_PHANTOM.name)
        )
        val rules = RaceRulesSettings(
            gameMode = GameMode.STUNTS.name,
            autoAccelerate = false,
            randomConditionsEnabled = false
        )

        // Verify 4 distinct stunts are generated and shuffled across seeds
        val seqA = RacePhysicsEngine(
            track = TrackCatalog.getStuntTrack(com.example.model.StuntVariant.LONG_JUMP),
            totalLaps = 3,
            players = players,
            rules = rules,
            random = Random(10L)
        ).stuntSequence()
        val sequencesSeen = (1..8).map { seed ->
            RacePhysicsEngine(
                track = TrackCatalog.getStuntTrack(com.example.model.StuntVariant.LONG_JUMP),
                totalLaps = 3,
                players = players,
                rules = rules,
                random = Random(seed * 137L)
            ).stuntSequence()
        }.toSet()

        assertEquals(4, seqA.size)
        assertEquals(com.example.model.StuntVariant.entries.toSet(), seqA.toSet())
        assertTrue("Stunt gauntlets should play in randomized orders across runs", sequencesSeen.size >= 3)

        val engine = RacePhysicsEngine(
            track = TrackCatalog.getStuntTrack(com.example.model.StuntVariant.LONG_JUMP),
            totalLaps = 3,
            players = players,
            rules = rules,
            random = Random(42L)
        )
        var snap = engine.step(0.05f)
        while (snap.phase == RacePhase.COUNTDOWN.name) {
            snap = engine.step(0.05f)
        }
        assertEquals(RacePhase.RACING.name, snap.phase)
        assertEquals(GameMode.STUNTS.name, snap.gameMode)

        // Check initial Turn 0: p1 is active on shared runway with 1x TURBO_NITRO; p2 & b1 are spectators at y=235
        val p1Turn0 = snap.racers.first { it.playerId == "p1" }
        val p2Spec0 = snap.racers.first { it.playerId == "p2" }
        val b1Spec0 = snap.racers.first { it.playerId == "b1" }
        assertEquals("p1", snap.stuntState?.activePlayerId)
        assertEquals(PowerUpType.TURBO_NITRO.name, p1Turn0.heldPowerUp)
        assertEquals(0, p1Turn0.minesAvailable)
        assertEquals(230f, p1Turn0.x, 1f)
        assertEquals(700f, p1Turn0.y, 1f)
        assertEquals(235f, p2Spec0.y, 1f)
        assertEquals(235f, b1Spec0.y, 1f)
        assertEquals(null, p2Spec0.heldPowerUp)

        // Submitting throttle/steer for spectator p2 must NOT move p2 out of the spectator paddock!
        repeat(10) {
            engine.submitInput("p2", ControlInput(throttle = 1f, steer = 1f))
            snap = engine.step(0.05f)
        }
        val p2StillSpectator = snap.racers.first { it.playerId == "p2" }
        assertEquals("Spectator car must remain parked at y=235 during another player's turn", 235f, p2StillSpectator.y, 0.5f)
        assertEquals(0f, p2StillSpectator.speed, 0.01f)

        // Step through all 4 stunt rounds x 3 players = 12 turns and verify turn-based order & starting Nitro every turn
        val visitedTurns = mutableListOf<Triple<Int, String, String>>()
        var safetyLoops = 0
        while (snap.phase != RacePhase.FINISHED.name && safetyLoops < 12_000) {
            val st = snap.stuntState!!
            val turnKey = Triple(st.roundIndex, st.activePlayerId, st.variant)
            if (visitedTurns.lastOrNull() != turnKey) {
                visitedTurns.add(turnKey)
                val activeRacer = snap.racers.first { it.playerId == st.activePlayerId }
                assertEquals(
                    "Every player must start their stunt turn with 1x TURBO_NITRO (${turnKey})",
                    PowerUpType.TURBO_NITRO.name,
                    activeRacer.heldPowerUp
                )
            }
            // Drive active player forward (bots drive & fire Nitro automatically; humans get throttle + Nitro past slalom)
            val activeRacer = snap.racers.first { it.playerId == st.activePlayerId }
            if (!activeRacer.isBot) {
                val upcomingObs = st.runwayObstacles.firstOrNull { ob ->
                    ob.x > activeRacer.x - 18f && ob.x - activeRacer.x <= 265f
                }
                val targetY = if (upcomingObs != null) {
                    val passOffset = upcomingObs.radius + 54f
                    if (upcomingObs.y <= 700f) {
                        (upcomingObs.y + passOffset).coerceAtMost(815f)
                    } else {
                        (upcomingObs.y - passOffset).coerceAtLeast(585f)
                    }
                } else {
                    700f
                }
                val desired = kotlin.math.atan2(targetY - activeRacer.y, (activeRacer.x + 140f) - activeRacer.x)
                val diff = RacePhysicsEngine.normalizeAngle(desired - activeRacer.angleRad)
                val popNitro = activeRacer.x >= 950f && activeRacer.heldPowerUp == PowerUpType.TURBO_NITRO.name
                engine.submitInput(
                    activeRacer.playerId,
                    ControlInput(throttle = 1f, steer = (diff / 0.5f).coerceIn(-1f, 1f), usePowerUp = popNitro)
                )
            }
            snap = engine.step(0.05f)
            safetyLoops++
        }

        assertEquals("Stunt gauntlet should finish after all 4 rounds x 3 players", RacePhase.FINISHED.name, snap.phase)
        assertEquals("Exactly 12 turns (4 stunts x 3 players) must be played", 12, visitedTurns.size)
        assertEquals(
            "Each round must cycle p1 -> p2 -> b1",
            listOf("p1", "p2", "b1", "p1", "p2", "b1", "p1", "p2", "b1", "p1", "p2", "b1"),
            visitedTurns.map { it.second }
        )
        assertEquals(
            "All 4 stunt variants must be played across the 4 rounds",
            com.example.model.StuntVariant.entries.map { it.name }.toSet(),
            visitedTurns.map { it.third }.toSet()
        )
    }

    @Test
    fun stuntsMode_scoresLongJumpSoccerGoalBowlingAndGolfHoles() {
        val twoPlayers = listOf(
            LobbyPlayer("p1", "DriverA", isHost = true, isBot = false, carType = RcCarType.APEX_FORMULA.name),
            LobbyPlayer("p2", "DriverB", isHost = false, isBot = false, carType = RcCarType.VOLT_BUGGY.name)
        )
        val stuntRules = RaceRulesSettings(
            gameMode = GameMode.STUNTS.name,
            autoAccelerate = false,
            randomConditionsEnabled = false
        )

        // 1) LONG JUMP: faster ramp launch jumps farther & scores higher!
        val jumpEngine = RacePhysicsEngine(
            track = TrackCatalog.getStuntTrack(com.example.model.StuntVariant.LONG_JUMP),
            totalLaps = 1,
            players = twoPlayers,
            rules = stuntRules,
            random = Random(11L),
            stuntSequenceOverride = listOf(com.example.model.StuntVariant.LONG_JUMP)
        )
        var jSnap = jumpEngine.step(0.05f)
        while (jSnap.phase == RacePhase.COUNTDOWN.name) jSnap = jumpEngine.step(0.05f)

        // p1 moderate ramp launch
        jumpEngine.positionActiveStuntRacerForTest(x = 1340f, y = 700f, vx = 320f, vy = 0f)
        while (jSnap.stuntState?.turnPhase == "DRIVING") {
            jSnap = jumpEngine.step(0.04f)
        }
        val p1JumpScore = jSnap.stuntState?.lastAttemptScore ?: 0
        assertTrue("Moderate jump should score positive distance points ($p1JumpScore > 0)", p1JumpScore > 0)

        // Advance to p2's turn -> high-speed Nitro ramp launch
        while (jSnap.stuntState?.activePlayerId == "p1") {
            jSnap = jumpEngine.step(0.05f)
        }
        jumpEngine.positionActiveStuntRacerForTest(x = 1340f, y = 700f, vx = 640f, vy = 0f)
        while (jSnap.stuntState?.turnPhase == "DRIVING") {
            jSnap = jumpEngine.step(0.04f)
        }
        val p2JumpScore = jSnap.stuntState?.lastAttemptScore ?: 0
        assertTrue(
            "Faster ramp jump ($p2JumpScore) must jump farther and outscore slower jump ($p1JumpScore)",
            p2JumpScore > p1JumpScore + 80
        )

        // 2) SOCCER BALL GOAL: center bullseye scores 1000, side goal scores lower!
        val soccerEngine = RacePhysicsEngine(
            track = TrackCatalog.getStuntTrack(com.example.model.StuntVariant.SOCCER_BALL),
            totalLaps = 1,
            players = twoPlayers,
            rules = stuntRules,
            random = Random(22L),
            stuntSequenceOverride = listOf(com.example.model.StuntVariant.SOCCER_BALL)
        )
        var sSnap = soccerEngine.step(0.05f)
        while (sSnap.phase == RacePhase.COUNTDOWN.name) sSnap = soccerEngine.step(0.05f)

        // p1 strikes ball dead-center along y = 700 -> Bullseye 1000 pts
        soccerEngine.positionActiveStuntRacerForTest(x = 1855f, y = 700f, vx = 620f, vy = 0f)
        while (sSnap.stuntState?.turnPhase == "DRIVING") {
            sSnap = soccerEngine.step(0.03f)
        }
        assertEquals("Center goal strike must award maximum 1000 pts", 1000, sSnap.stuntState?.lastAttemptScore)

        // Advance to p2 -> angled hit sending ball into outer side of goal (lower score than center)
        while (sSnap.stuntState?.activePlayerId == "p1") {
            sSnap = soccerEngine.step(0.05f)
        }
        soccerEngine.positionActiveStuntRacerForTest(x = 1865f, y = 682f, vx = 640f, vy = 75f)
        while (sSnap.stuntState?.turnPhase == "DRIVING") {
            sSnap = soccerEngine.step(0.03f)
        }
        val sideGoalPts = sSnap.stuntState?.lastAttemptScore ?: 0
        assertTrue(
            "Off-center goal strike ($sideGoalPts) must score goal points > 100 but < 1000 center bullseye",
            sideGoalPts in listOf(250, 500, 750)
        )

        // 3) BOWLING: 9 pins in diamond formation -> knocking pins awards 100/pin + 100 strike bonus = 1000!
        val bowlEngine = RacePhysicsEngine(
            track = TrackCatalog.getStuntTrack(com.example.model.StuntVariant.BOWLING),
            totalLaps = 1,
            players = listOf(twoPlayers.first()),
            rules = stuntRules,
            random = Random(33L),
            stuntSequenceOverride = listOf(com.example.model.StuntVariant.BOWLING)
        )
        var bSnap = bowlEngine.step(0.05f)
        while (bSnap.phase == RacePhase.COUNTDOWN.name) bSnap = bowlEngine.step(0.05f)
        assertEquals("Bowling stunt must set up 9 standing pins", 9, bSnap.stuntState?.pins?.size)
        assertTrue(bSnap.stuntState?.pins?.none { it.knocked } == true)

        // Smash through the 9-pin rack; sweep any remaining standing pins so all 9 knock for a Strike!
        bowlEngine.positionActiveStuntRacerForTest(x = 1905f, y = 700f, vx = 720f, vy = 0f)
        var bTicks = 0
        while (bSnap.stuntState?.turnPhase == "DRIVING" && bTicks < 60) {
            val standing = bSnap.stuntState?.pins?.firstOrNull { !it.knocked }
            if (standing != null && bTicks > 3) {
                bowlEngine.positionActiveStuntRacerForTest(x = standing.x - 10f, y = standing.y, vx = 520f, vy = 0f)
            }
            bSnap = bowlEngine.step(0.03f)
            bTicks++
        }
        assertEquals("Knocking all 9 bowling pins must award 1000 strike points", 1000, bSnap.stuntState?.lastAttemptScore)

        // 4) GOLF HOLES: 6 random numbered holes (#1..#6) of smaller & bigger sizes with inverse point values!
        val golfEngine = RacePhysicsEngine(
            track = TrackCatalog.getStuntTrack(com.example.model.StuntVariant.GOLF_HOLES),
            totalLaps = 1,
            players = listOf(twoPlayers.first()),
            rules = stuntRules,
            random = Random(44L),
            stuntSequenceOverride = listOf(com.example.model.StuntVariant.GOLF_HOLES)
        )
        var gSnap = golfEngine.step(0.05f)
        while (gSnap.phase == RacePhase.COUNTDOWN.name) gSnap = golfEngine.step(0.05f)
        val holes = gSnap.stuntState?.golfHoles.orEmpty()
        assertEquals(listOf(1, 2, 3, 4, 5, 6), holes.map { it.number }.sorted())
        val smallestHole = holes.minByOrNull { it.radius }!!
        val biggestHole = holes.maxByOrNull { it.radius }!!
        assertTrue(
            "Smaller golf hole (${smallestHole.radius}px -> ${smallestHole.points}) must award more points than bigger hole (${biggestHole.radius}px -> ${biggestHole.points})",
            smallestHole.radius < biggestHole.radius && smallestHole.points > biggestHole.points
        )

        golfEngine.positionActiveStuntRacerForTest(
            x = smallestHole.x,
            y = smallestHole.y,
            vx = 40f,
            vy = 0f,
            jumpZ = 0f
        )
        gSnap = golfEngine.step(0.04f)
        assertEquals("SCORED", gSnap.stuntState?.turnPhase)
        assertEquals("Landing in smallest hole must award its exact high score", smallestHole.points, gSnap.stuntState?.lastAttemptScore)
        assertTrue(
            "Captured golf hole must be marked captured in snapshot",
            gSnap.stuntState?.golfHoles?.first { it.number == smallestHole.number }?.captured == true
        )
    }

    @Test
    fun telemetryCodec_roundTripsElectroShockHybridAndStuntState() {
        val roster = listOf(
            LobbyPlayer("p1", "Host", isHost = true, carType = RcCarType.VOLT_BUGGY.name),
            LobbyPlayer("p2", "Guest", isHost = false, carType = RcCarType.APEX_FORMULA.name)
        )

        // 1. Round-trip Stunt + Hybrid Tour state (Golf Holes & pins)
        val golfTrack = TrackCatalog.getStuntTrack(com.example.model.StuntVariant.GOLF_HOLES)
        val stuntEngine = RacePhysicsEngine(
            track = golfTrack,
            totalLaps = 1,
            players = roster,
            rules = RaceRulesSettings(gameMode = GameMode.STUNTS.name),
            random = Random(77L),
            stuntSequenceOverride = listOf(com.example.model.StuntVariant.GOLF_HOLES),
            isHybridTour = true,
            hybridStageNumber = 3,
            nextHybridModeName = GameMode.RACE.name
        )
        var sSnap = stuntEngine.step(0.05f)
        while (sSnap.phase == RacePhase.COUNTDOWN.name) sSnap = stuntEngine.step(0.05f)

        val encodedStuntLine = String(RaceTelemetryCodec.encodeSnapshot(seq = 301L, snap = sSnap), Charsets.UTF_8)
        assertFalse("Compact TCP/UDP snapshot line must not contain embedded newlines", encodedStuntLine.contains('\n'))
        val decodedStunt = RaceTelemetryCodec.decodeSnapshot(
            text = encodedStuntLine,
            roster = roster,
            previousSnapshot = null,
            activeTrack = golfTrack
        )
        assertNotNull(decodedStunt)
        assertEquals(GameMode.STUNTS.name, decodedStunt?.snapshot?.gameMode)
        assertEquals(true, decodedStunt?.snapshot?.isHybridTour)
        assertEquals(3, decodedStunt?.snapshot?.hybridStageNumber)
        assertEquals(GameMode.RACE.name, decodedStunt?.snapshot?.nextHybridMode)
        assertEquals(com.example.model.StuntVariant.GOLF_HOLES.name, decodedStunt?.snapshot?.stuntState?.variant)
        assertEquals(6, decodedStunt?.snapshot?.stuntState?.golfHoles?.size)
        assertEquals(PowerUpType.TURBO_NITRO.name, decodedStunt?.snapshot?.racers?.first { it.playerId == "p1" }?.heldPowerUp)

        // 2. Round-trip Electro Shock aura & paralysis timers
        val baseSnap = sSnap.copy(
            racers = sSnap.racers.mapIndexed { idx, r ->
                if (idx == 0) r.copy(electroShockAuraMs = 3650L)
                else r.copy(electroParalyzedMs = 2450L)
            }
        )
        val decodedShock = RaceTelemetryCodec.decodeSnapshot(
            text = String(RaceTelemetryCodec.encodeSnapshot(seq = 302L, snap = baseSnap), Charsets.UTF_8),
            roster = roster,
            previousSnapshot = null,
            activeTrack = golfTrack
        )
        assertEquals(3650L, decodedShock?.snapshot?.racers?.first { it.playerId == "p1" }?.electroShockAuraMs)
        assertEquals(2450L, decodedShock?.snapshot?.racers?.first { it.playerId == "p2" }?.electroParalyzedMs)
    }

    @Test
    fun hybridMode_rotatesIncludedGameTypesAndSupportsRandomOrder() {
        val allThreeRotation = RaceRulesSettings(
            gameMode = GameMode.HYBRID.name,
            hybridIncludeCircuit = true,
            hybridIncludeArena = true,
            hybridIncludeStunts = true,
            hybridRandomOrder = false
        )
        assertEquals(GameMode.RACE, WifiP2pRaceManager.resolveHybridStageMode(allThreeRotation, stageIndexZeroBased = 0))
        assertEquals(GameMode.ARENA, WifiP2pRaceManager.resolveHybridStageMode(allThreeRotation, stageIndexZeroBased = 1))
        assertEquals(GameMode.STUNTS, WifiP2pRaceManager.resolveHybridStageMode(allThreeRotation, stageIndexZeroBased = 2))
        assertEquals(GameMode.RACE, WifiP2pRaceManager.resolveHybridStageMode(allThreeRotation, stageIndexZeroBased = 3))

        val circuitAndStuntsOnly = allThreeRotation.copy(hybridIncludeArena = false)
        assertEquals(listOf(GameMode.RACE, GameMode.STUNTS), circuitAndStuntsOnly.enabledHybridModes())
        assertEquals(GameMode.RACE, WifiP2pRaceManager.resolveHybridStageMode(circuitAndStuntsOnly, stageIndexZeroBased = 0))
        assertEquals(GameMode.STUNTS, WifiP2pRaceManager.resolveHybridStageMode(circuitAndStuntsOnly, stageIndexZeroBased = 1))
        assertEquals(GameMode.RACE, WifiP2pRaceManager.resolveHybridStageMode(circuitAndStuntsOnly, stageIndexZeroBased = 2))

        val randomTwoModes = circuitAndStuntsOnly.copy(hybridRandomOrder = true)
        val rng = Random(555L)
        var prev: GameMode? = null
        repeat(10) { idx ->
            val next = WifiP2pRaceManager.resolveHybridStageMode(
                rules = randomTwoModes,
                stageIndexZeroBased = idx,
                previousMode = prev,
                random = rng
            )
            assertTrue("Random hybrid play must only choose included modes", next in setOf(GameMode.RACE, GameMode.STUNTS))
            if (prev != null) {
                assertTrue("With 2+ modes enabled, random hybrid order should alternate cleanly instead of repeating", next != prev)
            }
            prev = next
        }
    }

    @Test
    fun stuntsMode_enforces10sTimerRandomObstaclesAirBrakingPostLandingLockoutAndEndWallBounce() {
        val solo = listOf(
            LobbyPlayer("p1", "StuntDriver", isHost = true, isBot = false, carType = RcCarType.APEX_FORMULA.name)
        )
        val rules = RaceRulesSettings(
            gameMode = GameMode.STUNTS.name,
            autoAccelerate = false,
            randomConditionsEnabled = false
        )

        // 1. Verify 10-second turn timer & randomly placed lead-in runway obstacles across seeds
        val obstacleLayouts = (1..5).map { seed ->
            val eng = RacePhysicsEngine(
                track = TrackCatalog.getStuntTrack(com.example.model.StuntVariant.LONG_JUMP),
                totalLaps = 1,
                players = solo,
                rules = rules,
                random = Random(seed * 911L),
                stuntSequenceOverride = listOf(com.example.model.StuntVariant.LONG_JUMP)
            )
            var s = eng.step(0.05f)
            while (s.phase == RacePhase.COUNTDOWN.name) s = eng.step(0.05f)
            if (seed == 1) {
                assertTrue(
                    "Stunt turn timer must start at 10s (got ${s.stuntState?.turnTimeRemainingMs})",
                    (s.stuntState?.turnTimeRemainingMs ?: 0L) in 9_900L..10_000L
                )
            }
            val obs = s.stuntState?.runwayObstacles.orEmpty()
            assertTrue("Lead-in runway should have 4..5 obstacles", obs.size in 4..5)
            assertTrue(
                "All lead-in obstacles must be inside approach runway bounds",
                obs.all { it.x in 380f..940f && it.y in 580f..820f }
            )
            obs.map { (it.x.toInt() / 5) to (it.y.toInt() / 5) }
        }.toSet()
        assertTrue("Lead-in obstacles must be randomly placed across runs", obstacleLayouts.size >= 4)

        // 2. Verify mid-air braking shortens jump distance AND full-speed jump fits inside expanded stadium
        fun runAirJump(brakeInAir: Boolean): Pair<Float, RaceSnapshot> {
            val eng = RacePhysicsEngine(
                track = TrackCatalog.getStuntTrack(com.example.model.StuntVariant.LONG_JUMP),
                totalLaps = 1,
                players = solo,
                rules = rules,
                random = Random(101L),
                stuntSequenceOverride = listOf(com.example.model.StuntVariant.LONG_JUMP)
            )
            var s = eng.step(0.05f)
            while (s.phase == RacePhase.COUNTDOWN.name) s = eng.step(0.05f)
            eng.givePowerUpForTest("p1", PowerUpType.TURBO_NITRO, triggerImmediately = true)
            eng.positionActiveStuntRacerForTest(x = 1340f, y = 700f, vx = 520f, vy = 0f)
            var landedX = 0f
            var steps = 0
            while (steps < 120) {
                val r = s.racers.first()
                if (brakeInAir && r.jumpZ > 0.08f) {
                    eng.submitInput("p1", ControlInput(throttle = -1f, steer = 0f))
                } else {
                    eng.submitInput("p1", ControlInput(throttle = 0f, steer = 0f))
                }
                s = eng.step(0.03f)
                if (s.stuntState?.hasLanded == true && landedX == 0f) {
                    landedX = s.stuntState?.landingX ?: 0f
                    break
                }
                steps++
            }
            return landedX to s
        }

        val (unbrakedLandingX, landedSnap) = runAirJump(brakeInAir = false)
        val (airBrakedLandingX, _) = runAirJump(brakeInAir = true)
        assertTrue(
            "Full Nitro jump ($unbrakedLandingX) must land cleanly inside the 3120px stadium without overshooting",
            unbrakedLandingX in 1750f..2650f
        )
        assertTrue(
            "Mid-air braking ($airBrakedLandingX) must land significantly shorter than unbraked jump ($unbrakedLandingX)",
            airBrakedLandingX < unbrakedLandingX - 90f
        )
        assertEquals(
            "Once landed after mega ramp, controlsLocked must be true",
            true,
            landedSnap.stuntState?.controlsLocked
        )

        // 3. Verify NO car control is possible once landed again (throttle, brake, steer, and Nitro are locked out)
        fun simulatePostLanding(inputAfterLanding: ControlInput): Pair<Float, Float> {
            val eng = RacePhysicsEngine(
                track = TrackCatalog.getStuntTrack(com.example.model.StuntVariant.SOCCER_BALL),
                totalLaps = 1,
                players = solo,
                rules = rules,
                random = Random(202L),
                stuntSequenceOverride = listOf(com.example.model.StuntVariant.SOCCER_BALL)
            )
            var s = eng.step(0.05f)
            while (s.phase == RacePhase.COUNTDOWN.name) s = eng.step(0.05f)
            eng.positionActiveStuntRacerForTest(x = 1340f, y = 580f, vx = 440f, vy = 0f)
            while (s.stuntState?.controlsLocked != true) {
                s = eng.step(0.03f)
            }
            repeat(15) {
                eng.submitInput("p1", inputAfterLanding)
                s = eng.step(0.03f)
            }
            val r = s.racers.first()
            return r.x to r.y
        }

        val neutralCoast = simulatePostLanding(ControlInput(throttle = 0f, steer = 0f))
        val fullGasAndSteerAttempt = simulatePostLanding(ControlInput(throttle = 1f, steer = 1f, usePowerUp = true))
        val fullBrakeAttempt = simulatePostLanding(ControlInput(throttle = -1f, steer = -1f))
        assertEquals("Post-landing throttle/steer/nitro must be ignored", neutralCoast.first, fullGasAndSteerAttempt.first, 0.05f)
        assertEquals("Post-landing steering must be ignored", neutralCoast.second, fullGasAndSteerAttempt.second, 0.05f)
        assertEquals("Post-landing brake must be ignored", neutralCoast.first, fullBrakeAttempt.first, 0.05f)

        // 4. Verify hitting the end of the stunt area bounces the car back
        val bounceEngine = RacePhysicsEngine(
            track = TrackCatalog.getStuntTrack(com.example.model.StuntVariant.SOCCER_BALL),
            totalLaps = 1,
            players = solo,
            rules = rules,
            random = Random(303L),
            stuntSequenceOverride = listOf(com.example.model.StuntVariant.SOCCER_BALL)
        )
        var bSnap = bounceEngine.step(0.05f)
        while (bSnap.phase == RacePhase.COUNTDOWN.name) bSnap = bounceEngine.step(0.05f)
        val preCol = bSnap.racers.first().collisionSeq
        bounceEngine.positionActiveStuntRacerForTest(x = 3095f, y = 700f, vx = 520f, vy = 0f, jumpZ = 0f)
        bSnap = bounceEngine.step(0.04f)
        val bouncedRacer = bSnap.racers.first()
        assertTrue("Hitting the end of the stunt area must bounce car backward (vx=${bouncedRacer.vx})", bouncedRacer.vx < -150f)
        assertTrue("End-of-area bounce must increment collisionSeq", bouncedRacer.collisionSeq > preCol)
    }

    @Test
    fun itemBoxes_areRandomlyPlacedOffCenterlineAndRelocateOnRespawn() {
        val players = listOf(
            LobbyPlayer("p1", "Racer1", isHost = true, isBot = false, carType = RcCarType.VOLT_BUGGY.name)
        )
        val track = TrackCatalog.getTrack(TrackId.LIVING_ROOM_GP)
        val eng1 = RacePhysicsEngine(
            track = track,
            totalLaps = 3,
            players = players,
            rules = RaceRulesSettings(autoAccelerate = false, randomConditionsEnabled = false),
            random = Random(11L)
        )
        val eng2 = RacePhysicsEngine(
            track = track,
            totalLaps = 3,
            players = players,
            rules = RaceRulesSettings(autoAccelerate = false, randomConditionsEnabled = false),
            random = Random(99L)
        )
        var snap1 = eng1.step(0.05f)
        while (snap1.phase == RacePhase.COUNTDOWN.name) snap1 = eng1.step(0.05f)
        val snap2 = eng2.step(0.05f)

        assertTrue("Circuit item boxes must be non-empty", snap1.itemBoxes.isNotEmpty())
        assertNotEquals(
            "Item box coordinates must be randomized across runs",
            snap1.itemBoxes.map { it.x.toInt() to it.y.toInt() },
            snap2.itemBoxes.map { it.x.toInt() to it.y.toInt() }
        )

        // Check that item boxes are spread off the centerline rather than sitting on distance ~0
        val offCenterCount = snap1.itemBoxes.count { box ->
            val (_, distToCenter) = TrackCatalog.nearestTrackPoint(track, box.x, box.y)
            distToCenter > 15f
        }
        assertTrue("Majority of item boxes should be placed off the centerline (got $offCenterCount/${snap1.itemBoxes.size})", offCenterCount >= 3)

        // Verify telemetry round-trips exact randomized item box positions
        val decoded = RaceTelemetryCodec.decodeSnapshot(
            text = String(RaceTelemetryCodec.encodeSnapshot(seq = 401L, snap = snap1), Charsets.UTF_8),
            roster = players,
            previousSnapshot = null,
            activeTrack = track
        )
        assertNotNull(decoded)
        assertEquals(snap1.itemBoxes.size, decoded!!.snapshot.itemBoxes.size)
        assertEquals(snap1.itemBoxes.first().x, decoded.snapshot.itemBoxes.first().x, 0.2f)
        assertEquals(snap1.itemBoxes.first().y, decoded.snapshot.itemBoxes.first().y, 0.2f)
    }

    @Test
    fun empZap_zapsEveryActiveRivalAndBreaksShields() {
        val players = listOf(
            LobbyPlayer("p1", "Zapper", isHost = true, isBot = false, carType = RcCarType.VOLT_BUGGY.name),
            LobbyPlayer("p2", "RivalA", isHost = false, isBot = false, carType = RcCarType.APEX_FORMULA.name),
            LobbyPlayer("p3", "RivalB", isHost = false, isBot = false, carType = RcCarType.DRIFT_PHANTOM.name),
            LobbyPlayer("p4", "ShieldedRival", isHost = false, isBot = false, carType = RcCarType.TUNDRA_MONSTER.name)
        )
        val engine = RacePhysicsEngine(
            track = TrackCatalog.getTrack(TrackId.LIVING_ROOM_GP),
            totalLaps = 3,
            players = players,
            rules = RaceRulesSettings(autoAccelerate = false, randomConditionsEnabled = false),
            random = Random(7L)
        )
        var snap = engine.step(0.05f)
        while (snap.phase == RacePhase.COUNTDOWN.name) snap = engine.step(0.05f)

        // Give p4 a Shield, then p1 triggers EMP_ZAP
        engine.givePowerUpForTest("p4", PowerUpType.SHIELD, triggerImmediately = true)
        engine.givePowerUpForTest("p1", PowerUpType.EMP_ZAP, triggerImmediately = true)
        snap = engine.step(0.02f)

        val p1 = snap.racers.first { it.playerId == "p1" }
        val p2 = snap.racers.first { it.playerId == "p2" }
        val p3 = snap.racers.first { it.playerId == "p3" }
        val p4 = snap.racers.first { it.playerId == "p4" }

        assertEquals("Shooter p1 must not be zapped", 0L, p1.stunnedMs)
        assertTrue("Rival p2 must be zapped", p2.stunnedMs > 1500L)
        assertTrue("Rival p3 must also be zapped simultaneously", p3.stunnedMs > 1500L)
        assertEquals("Shielded rival p4's shield must be stripped by global Zap", 0L, p4.shieldMs)
        assertEquals("Shielded rival p4 must not be stunned when shield absorbs Zap", 0L, p4.stunnedMs)
    }

    @Test
    fun homingRocket_seeksNextPlayerAheadAndExplodesLikeMine() {
        val players = listOf(
            LobbyPlayer("p1", "LeaderAhead", isHost = true, isBot = false, carType = RcCarType.VOLT_BUGGY.name),
            LobbyPlayer("p2", "ShooterBehind", isHost = false, isBot = false, carType = RcCarType.APEX_FORMULA.name)
        )
        val arenaTrack = TrackCatalog.getArenaTrack(com.example.model.ArenaShape.SQUARE)
        val engine = RacePhysicsEngine(
            track = arenaTrack,
            totalLaps = 1,
            players = players,
            rules = RaceRulesSettings(
                gameMode = GameMode.ARENA.name,
                autoAccelerate = false,
                randomConditionsEnabled = false
            ),
            random = Random(55L)
        )
        var snap = engine.step(0.05f)
        while (snap.phase == RacePhase.COUNTDOWN.name) snap = engine.step(0.05f)

        // Make p1 the flag carrier so p1 is #1 ahead of p2, then p2 fires HOMING_ROCKET
        val p1Start = snap.racers.first { it.playerId == "p1" }
        engine.setFlagStateForTest(p1Start.x, p1Start.y, carrierId = "p1")
        snap = engine.step(0.05f)

        engine.givePowerUpForTest("p2", PowerUpType.HOMING_ROCKET, triggerImmediately = true)
        snap = engine.step(0.02f)
        assertEquals("Firing HOMING_ROCKET must spawn an active rocket", 1, snap.rockets.size)
        assertEquals("Rocket must lock onto next player ahead (p1)", "p1", snap.rockets.first().targetId)

        // Verify telemetry round-trip of flying rocket
        val decodedRocketSnap = RaceTelemetryCodec.decodeSnapshot(
            text = String(RaceTelemetryCodec.encodeSnapshot(seq = 402L, snap = snap), Charsets.UTF_8),
            roster = players,
            previousSnapshot = null,
            activeTrack = arenaTrack
        )
        assertEquals(1, decodedRocketSnap?.snapshot?.rockets?.size)
        assertEquals("p1", decodedRocketSnap?.snapshot?.rockets?.first()?.targetId)

        // Step until rocket homes in and detonates on p1
        var hitObserved = false
        var explosionObserved = false
        var ticks = 0
        while (ticks < 120) {
            snap = engine.step(0.03f)
            val p1Now = snap.racers.first { it.playerId == "p1" }
            if (snap.explosions.isNotEmpty()) explosionObserved = true
            if (p1Now.stunnedMs > 0L) {
                hitObserved = true
                break
            }
            ticks++
        }
        assertTrue("Homing rocket must seek and stun the target rival", hitObserved)
        assertTrue("Homing rocket impact must spawn a mine-style explosion", explosionObserved)
        assertTrue("Rocket must despawn on impact", snap.rockets.isEmpty())
    }

    @Test
    fun redLaser_extendsForwardAndTemporarilyDisablesTouchedRivalsLikeElectroShock() {
        val players = listOf(
            LobbyPlayer("p1", "TargetInFront", isHost = true, isBot = false, carType = RcCarType.VOLT_BUGGY.name),
            LobbyPlayer("p2", "LaserCar", isHost = false, isBot = false, carType = RcCarType.APEX_FORMULA.name)
        )
        // On gridStartTransform, idx 0 (p1) is placed directly ahead along the road tangent of idx 2, or let's align p2 behind p1!
        val track = TrackCatalog.getTrack(TrackId.LIVING_ROOM_GP)
        val threePlayers = listOf(
            players[0],
            LobbyPlayer("p_side", "SideCar", isHost = false, isBot = false, carType = RcCarType.DRIFT_PHANTOM.name),
            players[1]
        )
        val engine = RacePhysicsEngine(
            track = track,
            totalLaps = 3,
            players = threePlayers,
            rules = RaceRulesSettings(autoAccelerate = false, randomConditionsEnabled = false),
            random = Random(88L)
        )
        var snap = engine.step(0.05f)
        while (snap.phase == RacePhase.COUNTDOWN.name) snap = engine.step(0.05f)

        // Grid slot 2 (p2) sits ~68px directly behind Grid slot 0 (p1) facing along the exact same road tangent!
        engine.givePowerUpForTest("p2", PowerUpType.RED_LASER, triggerImmediately = true)
        snap = engine.step(0.02f)

        val p2 = snap.racers.first { it.playerId == "p2" }
        val p1 = snap.racers.first { it.playerId == "p1" }
        assertTrue("Laser car must have active laserBeamMs > 3500ms", p2.laserBeamMs > 3500L)
        assertTrue(
            "Rival p1 in front of the forward Red Laser beam must be temporarily disabled motionless (${p1.electroParalyzedMs}ms)",
            p1.electroParalyzedMs > 2500L
        )

        // Verify telemetry round-trip of laserBeamMs
        val decodedLaser = RaceTelemetryCodec.decodeSnapshot(
            text = String(RaceTelemetryCodec.encodeSnapshot(seq = 403L, snap = snap), Charsets.UTF_8),
            roster = threePlayers,
            previousSnapshot = null,
            activeTrack = track
        )
        assertEquals(p2.laserBeamMs, decodedLaser?.snapshot?.racers?.first { it.playerId == "p2" }?.laserBeamMs)

        // Even if disabled p1 holds full throttle, p1 remains motionless (0 speed) just like Electro Shock!
        repeat(8) {
            engine.submitInput("p1", ControlInput(throttle = 1f, steer = 0f))
            snap = engine.step(0.04f)
        }
        val p1StillDisabled = snap.racers.first { it.playerId == "p1" }
        assertEquals("Laser-disabled car must remain motionless at 0 speed", 0f, p1StillDisabled.speed, 0.01f)
    }
}


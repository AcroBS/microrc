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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
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
    fun lap2Mine_grantsOnLap2DropsSeparatelyAndExplodesWithSlipSpin() {
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
        assertEquals("No mine on Lap 1", 0, snap.racers.first { it.playerId == "p1" }.minesAvailable)

        // Drive p1 around Lap 1 until crossing into Lap 2
        var steps = 0
        while (snap.racers.first { it.playerId == "p1" }.lap < 2 && steps < 2000) {
            val r = snap.racers.first { it.playerId == "p1" }
            val (idx, _) = TrackCatalog.nearestTrackPoint(track, r.x, r.y)
            val target = track.centerline[(idx + 7) % track.centerline.size]
            val desired = kotlin.math.atan2(target.y - r.y, target.x - r.x)
            val diff = RacePhysicsEngine.normalizeAngle(desired - r.angleRad)
            engine.submitInput("p1", ControlInput(throttle = 1f, steer = (diff / 0.6f).coerceIn(-1f, 1f)))
            engine.submitInput("p2", ControlInput(throttle = 0f, steer = 0f))
            snap = engine.step(0.016f)
            steps++
        }

        val p1Lap2 = snap.racers.first { it.playerId == "p1" }
        assertEquals(2, p1Lap2.lap)
        assertEquals("Driver must receive 1 mine starting on Lap 2", 1, p1Lap2.minesAvailable)

        // Drop mine using separate dropMine input
        engine.submitInput("p1", ControlInput(throttle = 1f, steer = 0f, dropMine = true))
        snap = engine.step(0.016f)
        assertEquals(0, snap.racers.first { it.playerId == "p1" }.minesAvailable)
        val droppedMine = snap.hazards.firstOrNull { it.hazardTypeEnum == HazardType.MINE }
        assertNotNull("Dropped flashing red mine should exist in hazards", droppedMine)

        // Now test rival hitting a mine: spawns explosion, spins out like oil/puddle, and increments collisionSeq
        val arena = TrackCatalog.getArenaTrack(ArenaShape.CIRCLE).copy(
            ramps = emptyList(),
            props = emptyList(),
            itemSpawns = emptyList()
        )
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
        // Drop mine behind p1, then position p2 to drive right onto it
        hitEngine.submitInput("p1", ControlInput(throttle = 0f, steer = 0f, dropMine = true))
        hSnap = hitEngine.step(0.02f)
        val mine = hSnap.hazards.first { it.hazardTypeEnum == HazardType.MINE }
        assertNotNull(arena)

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
    fun springSliderSteer_appliesDeadzoneExpoAndFullLockSymmetrically() {
        // Neutral & inside small center deadzone -> exact 0f
        assertEquals(0f, com.example.model.computeSpringSliderSteer(0f), 0.0001f)
        assertEquals(0f, com.example.model.computeSpringSliderSteer(0.02f), 0.0001f)
        assertEquals(0f, com.example.model.computeSpringSliderSteer(-0.02f), 0.0001f)

        // Full left/right lock -> exact -1f / +1f
        assertEquals(1f, com.example.model.computeSpringSliderSteer(1f), 0.0001f)
        assertEquals(-1f, com.example.model.computeSpringSliderSteer(-1f), 0.0001f)

        // Subtle RC expo: mid-travel is smooth and gentler than raw linear deflection, strictly monotonic & symmetric
        val quarter = com.example.model.computeSpringSliderSteer(0.25f)
        val half = com.example.model.computeSpringSliderSteer(0.50f)
        val threeQuarter = com.example.model.computeSpringSliderSteer(0.75f)
        assertTrue("Quarter deflection ($quarter) should be > 0 and gentler than linear 0.25", quarter in 0.12f..0.22f)
        assertTrue("Half deflection ($half) should be gentler than linear 0.50 due to expo", half in 0.32f..0.46f)
        assertTrue("Curve must be strictly monotonic ($quarter < $half < $threeQuarter < 1f)", quarter < half && half < threeQuarter && threeQuarter < 1f)
        assertEquals("Left/right must be perfectly symmetric", -half, com.example.model.computeSpringSliderSteer(-0.50f), 0.0001f)
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
}

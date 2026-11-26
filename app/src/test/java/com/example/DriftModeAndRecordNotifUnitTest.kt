package com.example

import com.example.data.FriendStandingsCalculator
import com.example.data.OnlineRaceRepository
import com.example.data.TrackCatalog
import com.example.drift.DriftDailyChallenge
import com.example.drift.DriftFeature
import com.example.drift.DriftPhysics
import com.example.drift.DriftTrackCatalog
import com.example.engine.RacePhysicsEngine
import com.example.model.ControlInput
import com.example.model.FriendUiModel
import com.example.model.GameMode
import com.example.model.LeaderboardBoardKey
import com.example.model.LeaderboardEntryDoc
import com.example.model.LeaderboardNotificationDoc
import com.example.model.LobbyPlayer
import com.example.model.PowerUpType
import com.example.model.RacePhase
import com.example.model.RaceRulesSettings
import com.example.model.RcCarType
import com.example.model.TrackId
import com.example.model.WinCondition
import kotlin.math.abs
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DriftModeAndRecordNotifUnitTest {

    @Test
    fun recordOnlyNotifications_triggersOnlyOnNewOverallBoardRecordAndFiltersStaleAlerts() {
        val boardKey = LeaderboardBoardKey.build(GameMode.RACE, "LIVING_ROOM_GP", totalLaps = 3)
        val friendEntries = listOf(
            LeaderboardEntryDoc(
                id = "uid_friend1_$boardKey",
                userId = "uid_friend1",
                driverName = "TurboAlex",
                gameMode = GameMode.RACE.name,
                trackKey = "LIVING_ROOM_GP",
                boardKey = boardKey,
                totalLaps = 3L,
                bestRaceTimeMs = 42_000L,
                publicEntry = true
            ),
            LeaderboardEntryDoc(
                id = "uid_me_$boardKey",
                userId = "uid_me",
                driverName = "MeDriver",
                gameMode = GameMode.RACE.name,
                trackKey = "LIVING_ROOM_GP",
                boardKey = boardKey,
                totalLaps = 3L,
                bestRaceTimeMs = 46_000L,
                publicEntry = true
            )
        )

        // Setting a personal best (43_500ms) that still loses to TurboAlex's 42_000ms is NOT a board record
        assertFalse(
            OnlineRaceRepository.isNewOverallBoardRecord(
                existingBoardEntries = friendEntries,
                submitterUid = "uid_me",
                gameMode = GameMode.RACE,
                finishTimeMs = 43_500L,
                bestScore = 0
            )
        )

        // Tying 42_000ms is NOT strictly faster -> NOT a new record
        assertFalse(
            OnlineRaceRepository.isNewOverallBoardRecord(
                existingBoardEntries = friendEntries,
                submitterUid = "uid_me",
                gameMode = GameMode.RACE,
                finishTimeMs = 42_000L,
                bestScore = 0
            )
        )

        // Beating 42_000ms (41_250ms) IS a new #1 board record
        assertTrue(
            OnlineRaceRepository.isNewOverallBoardRecord(
                existingBoardEntries = friendEntries,
                submitterUid = "uid_me",
                gameMode = GameMode.RACE,
                finishTimeMs = 41_250L,
                bestScore = 0
            )
        )

        // Stunts mode compares bestScore strictly higher
        val stuntsEntries = listOf(
            LeaderboardEntryDoc(
                id = "uid_friend1_STUNTS",
                userId = "uid_friend1",
                gameMode = GameMode.STUNTS.name,
                boardKey = "STUNTS_GAUNTLET_4R",
                bestScore = 3800L,
                publicEntry = true
            )
        )
        assertFalse(
            OnlineRaceRepository.isNewOverallBoardRecord(
                existingBoardEntries = stuntsEntries,
                submitterUid = "uid_me",
                gameMode = GameMode.STUNTS,
                finishTimeMs = 0L,
                bestScore = 3600
            )
        )
        assertTrue(
            OnlineRaceRepository.isNewOverallBoardRecord(
                existingBoardEntries = stuntsEntries,
                submitterUid = "uid_me",
                gameMode = GameMode.STUNTS,
                finishTimeMs = 0L,
                bestScore = 4100
            )
        )

        // Notification filtering: old notification at 44_000ms is NOT the #1 record when friend1 holds 42_000ms
        val nonRecordNotif = LeaderboardNotificationDoc(
            id = "n1",
            senderUid = "uid_sam",
            senderName = "DriftSam",
            gameMode = GameMode.RACE.name,
            boardKey = boardKey,
            bestRaceTimeMs = 44_000L
        )
        assertFalse(OnlineRaceRepository.isNotificationStillBoardRecord(nonRecordNotif, friendEntries))

        // Notification holding the fastest time (40_500ms) remains valid
        val trueRecordNotif = nonRecordNotif.copy(id = "n2", bestRaceTimeMs = 40_500L)
        assertTrue(OnlineRaceRepository.isNotificationStillBoardRecord(trueRecordNotif, friendEntries))
    }

    @Test
    fun driftPhysics_createsRearSlipAngleOnlyInDriftModeAndGrantsOneNitroPerLapWithNoPowerups() {
        val driftCustom = DriftTrackCatalog.defaultDriftCustomTracks().first()
        val driftTrack = TrackCatalog.buildCustomTrack(driftCustom)
        val raceTrack = TrackCatalog.getTrack(TrackId.WAREHOUSE_DRIFT).copy(
            props = emptyList(),
            ramps = emptyList(),
            itemSpawns = emptyList()
        )
        val players = listOf(
            LobbyPlayer("p1", "Drifter", isHost = true, isBot = false, carType = RcCarType.DRIFT_PHANTOM.name),
            LobbyPlayer("b1", "Bot", isHost = false, isBot = true, carType = RcCarType.VOLT_BUGGY.name)
        )

        val driftEngine = RacePhysicsEngine(
            track = driftTrack,
            totalLaps = 3,
            players = players,
            rules = RaceRulesSettings(
                gameMode = GameMode.DRIFT.name,
                winCondition = WinCondition.CHAMPIONSHIP_POINTS.name, // Drift overrides to FIRST_TO_FINISH
                randomJumpsEnabled = false,
                randomConditionsEnabled = false
            ),
            random = Random(42L)
        )
        val raceEngine = RacePhysicsEngine(
            track = raceTrack,
            totalLaps = 3,
            players = players,
            rules = RaceRulesSettings(
                gameMode = GameMode.RACE.name,
                randomJumpsEnabled = false,
                randomConditionsEnabled = false
            ),
            random = Random(42L)
        )

        // Advance past countdown (~3.2s)
        var driftSnap = driftEngine.step(0.05f)
        var raceSnap = raceEngine.step(0.05f)
        repeat(68) {
            driftSnap = driftEngine.step(0.05f)
            raceSnap = raceEngine.step(0.05f)
        }
        assertEquals(RacePhase.RACING.name, driftSnap.phase)
        assertEquals(GameMode.DRIFT.name, driftSnap.gameMode)
        assertEquals(WinCondition.FIRST_TO_FINISH.name, driftSnap.winCondition)

        // Drift mode: 0 item boxes, 0 field pickups, null portalPair, 0 mines, and 1 Nitro on Lap 1
        assertTrue("Drift must have 0 item boxes", driftSnap.itemBoxes.isEmpty())
        assertTrue("Drift must have 0 field pickups", driftSnap.fieldPickups.isEmpty())
        assertNull("Drift must have no portals", driftSnap.portalPair)
        val driftP1Start = driftSnap.racers.first { it.playerId == "p1" }
        assertEquals("Drift must have 0 mines on Lap 1", 0, driftP1Start.minesAvailable)
        assertEquals("Drift grants 1 Nitro on Lap 1", PowerUpType.TURBO_NITRO.name, driftP1Start.heldPowerUp)

        // Build straight speed first, then flick hard right to enter a turn
        repeat(45) {
            driftEngine.submitInput("p1", ControlInput(throttle = 1f, steer = 0f))
            raceEngine.submitInput("p1", ControlInput(throttle = 1f, steer = 0f))
            driftSnap = driftEngine.step(0.02f)
            raceSnap = raceEngine.step(0.02f)
        }

        var maxDriftSlip = 0f
        var maxRaceSlip = 0f
        repeat(30) {
            driftEngine.submitInput("p1", ControlInput(throttle = 1f, steer = 1f))
            raceEngine.submitInput("p1", ControlInput(throttle = 1f, steer = 1f))
            driftSnap = driftEngine.step(0.02f)
            raceSnap = raceEngine.step(0.02f)

            val dr = driftSnap.racers.first { it.playerId == "p1" }
            val rr = raceSnap.racers.first { it.playerId == "p1" }
            maxDriftSlip = maxOf(maxDriftSlip, abs(DriftPhysics.computeSlipAngleRad(dr.angleRad, dr.vx, dr.vy)))
            maxRaceSlip = maxOf(maxRaceSlip, abs(DriftPhysics.computeSlipAngleRad(rr.angleRad, rr.vx, rr.vy)))
        }
        assertTrue(
            "Drift mode slip angle ($maxDriftSlip) should enter real drift (>= 0.16 rad) and loosen rear end more than Race ($maxRaceSlip)",
            maxDriftSlip >= 0.16f && maxDriftSlip > maxRaceSlip
        )

        // Consume Lap 1 Nitro -> heldPowerUp clears
        driftEngine.submitInput("p1", ControlInput(throttle = 1f, steer = 0f, usePowerUp = true))
        driftSnap = driftEngine.step(0.02f)
        assertNull("Using Lap 1 Nitro consumes it", driftSnap.racers.first { it.playerId == "p1" }.heldPowerUp)

        // Advance to Lap 2 -> receives 1 fresh Nitro for Lap 2, and still 0 mines / 0 powerup boxes
        driftEngine.simulateCompleteLapForTest("p1")
        driftSnap = driftEngine.step(0.02f)
        val driftP1Lap2 = driftSnap.racers.first { it.playerId == "p1" }
        assertEquals(2, driftP1Lap2.lap)
        assertEquals("Lap 2 must refresh 1 Nitro", PowerUpType.TURBO_NITRO.name, driftP1Lap2.heldPowerUp)
        assertEquals("Drift must still have 0 mines on Lap 2", 0, driftP1Lap2.minesAvailable)
        assertTrue("Drift must not spawn item boxes", driftSnap.itemBoxes.isEmpty())
    }

    @Test
    fun driftTracksAndDailyDrift_haveSolidObstaclesNoPowerupsAndDailyStandingsExclusion() {
        val defaults = DriftTrackCatalog.defaultDriftCustomTracks()
        assertEquals(3, defaults.size)
        for (custom in defaults) {
            assertTrue("${custom.id} should have 0 item spawns", custom.itemSpawns.isEmpty())
            assertTrue("${custom.id} should have solid obstacles", custom.props.any { it.isSolidObstacle })
            val built = TrackCatalog.buildCustomTrack(custom)
            assertTrue("Built drift track should have empty itemSpawns", built.itemSpawns.isEmpty())
            assertTrue("Built drift track should have solid props", built.props.any { it.isSolidObstacle })
            assertTrue("Built drift track should have full spline", built.centerline.size >= 100)
        }

        val randomDrift = DriftTrackCatalog.generateRandomDriftTrack(seed = 987654L)
        assertTrue(randomDrift.itemSpawns.isEmpty())
        assertTrue(randomDrift.ramps.isEmpty())
        assertTrue(randomDrift.props.any { it.isSolidObstacle })

        val today = "20261124"
        val yesterday = "20261123"
        val dailyA = DriftDailyChallenge.dailyDriftCustomTrack(today)
        val dailyB = DriftDailyChallenge.dailyDriftCustomTrack(today)
        assertEquals("Daily Drift must be deterministic per UTC date", dailyA, dailyB)
        assertTrue(dailyA.itemSpawns.isEmpty())
        assertTrue(dailyA.props.any { it.isSolidObstacle })

        val locked = DriftDailyChallenge.lockedDailyDriftRules(
            RaceRulesSettings(
                winCondition = WinCondition.CHAMPIONSHIP_POINTS.name,
                randomJumpsEnabled = false,
                randomConditionsEnabled = false,
                hostRandomConditionsOverride = false
            )
        )
        assertEquals(GameMode.DRIFT.name, locked.gameMode)
        assertEquals(WinCondition.FIRST_TO_FINISH.name, locked.winCondition)
        assertEquals(DriftDailyChallenge.DAILY_DRIFT_LAPS, locked.totalLaps)
        assertTrue(locked.randomJumpsEnabled)
        assertFalse(locked.randomConditionsEnabled)
        assertNull(locked.hostRandomConditionsOverride)

        // Across UTC dates, Daily Drift tracks deterministically include jumps and Day/Night lighting with no random environmental effects
        val sampleDriftDays = (1..30).map { day ->
            DriftDailyChallenge.dailyDriftCustomTrack("202611%02d".format(day))
        }
        assertTrue("Daily Drift should include days with jumps", sampleDriftDays.any { it.ramps.isNotEmpty() })
        assertTrue("Daily Drift should include days without jumps", sampleDriftDays.any { it.ramps.isEmpty() })
        assertTrue("Daily Drift should include deterministic Night tracks", sampleDriftDays.any { it.isNightTrack })
        assertTrue("Daily Drift should include deterministic Day tracks", sampleDriftDays.any { !it.isNightTrack })
        assertTrue("Daily Drift must disable random environmental effects for fair ghost comparisons", sampleDriftDays.all { !it.randomConditionsEnabled })

        // Standings calculator keeps today's Daily Drift and excludes yesterday's Daily Drift
        val todayBoard = DriftDailyChallenge.boardKeyForDate(today)
        val yesterdayBoard = DriftDailyChallenge.boardKeyForDate(yesterday)
        val friends = listOf(
            FriendUiModel(
                friendshipId = "f1",
                friendUid = "uid_alex",
                displayName = "TurboAlex",
                friendCode = "RCALEX11",
                carType = RcCarType.DRIFT_PHANTOM,
                primaryColorArgb = 0xFFFF5722,
                accentColorArgb = 0xFFFFD54F,
                effectivelyOnline = true,
                activityStatus = "ONLINE",
                activeRoomId = "",
                activeRoomCode = "",
                activeRoomName = "",
                statusLabel = "Online"
            )
        )
        val cloudEntries = listOf(
            LeaderboardEntryDoc(
                id = "uid_alex_$yesterdayBoard",
                userId = "uid_alex",
                driverName = "TurboAlex",
                gameMode = GameMode.RACE.name,
                trackKey = DriftDailyChallenge.trackIdForDate(yesterday),
                boardKey = yesterdayBoard,
                totalLaps = 3L,
                bestRaceTimeMs = 31_000L,
                publicEntry = true
            ),
            LeaderboardEntryDoc(
                id = "uid_alex_$todayBoard",
                userId = "uid_alex",
                driverName = "TurboAlex",
                gameMode = GameMode.RACE.name,
                trackKey = DriftDailyChallenge.trackIdForDate(today),
                boardKey = todayBoard,
                totalLaps = 3L,
                bestRaceTimeMs = 35_000L,
                publicEntry = true
            ),
            LeaderboardEntryDoc(
                id = "uid_me_$todayBoard",
                userId = "uid_me",
                driverName = "MeDriver",
                gameMode = GameMode.RACE.name,
                trackKey = DriftDailyChallenge.trackIdForDate(today),
                boardKey = todayBoard,
                totalLaps = 3L,
                bestRaceTimeMs = 36_200L,
                publicEntry = true
            )
        )
        val summary = FriendStandingsCalculator.compute(
            myUid = "uid_me",
            myName = "MeDriver",
            friends = friends,
            cloudEntries = cloudEntries,
            localRecords = emptyList(),
            todayDateKey = today,
            maxHighlights = 5
        )
        assertFalse(summary.highlightBoards.any { it.boardKey == yesterdayBoard })
        assertTrue(summary.highlightBoards.any { it.boardKey == todayBoard })
        val todayHighlight = summary.highlightBoards.first { it.boardKey == todayBoard }
        assertEquals(2, todayHighlight.myRank)
        assertEquals(1_200L, todayHighlight.deltaVsTopFriendMs)
    }

    @Test
    fun driftFeatureFlag_wireCompatibilityAndBoardKeys() {
        assertEquals(GameMode.RACE.name, DriftFeature.toFirestoreWireGameMode(GameMode.DRIFT))
        assertEquals(GameMode.ARENA.name, DriftFeature.toFirestoreWireGameMode(GameMode.ARENA))
        assertTrue(DriftFeature.selectableGameModes().contains(GameMode.DRIFT))

        val boardKey = LeaderboardBoardKey.build(GameMode.DRIFT, DriftTrackCatalog.HAIRPIN_YARD_ID, totalLaps = 3)
        assertTrue(boardKey.startsWith("DRIFT_"))
        assertNotNull(DriftTrackCatalog.resolveTrackDisplayName(DriftTrackCatalog.HAIRPIN_YARD_ID))
    }
}

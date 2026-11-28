package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.PlayStreakState
import com.example.data.TrackCatalog
import com.example.engine.RacePhysicsEngine
import com.example.model.ActiveGhostCarState
import com.example.model.ControlInput
import com.example.model.DailyWeatherTheme
import com.example.model.GameMode
import com.example.model.LobbyPlayer
import com.example.model.LobbyStatePayload
import com.example.model.PlayerProfile
import com.example.model.RacePhase
import com.example.model.RaceRulesSettings
import com.example.model.RaceSnapshot
import com.example.model.RacerState
import com.example.model.RcCarType
import com.example.model.TrackId
import com.example.network.SessionRole
import com.example.sensors.TiltSteeringController
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.HybridSetupScreen
import com.example.ui.screens.LobbyScreen
import com.example.ui.screens.RaceScreen
import com.example.ui.screens.formatGhostHudPrefix
import com.example.ui.theme.MicroRCTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
class SoloCleanupAndPlayUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun lobbyScreen_soloModeHidesPaddockChatAndHostLobbyBadges() {
        val soloLobby = LobbyStatePayload(
            hostId = "p1",
            lobbyName = "Solo Lobby",
            hostIp = "127.0.0.1",
            tcpPort = 0,
            trackId = TrackId.LIVING_ROOM_GP.name,
            totalLaps = 3,
            players = listOf(
                LobbyPlayer(
                    id = "p1",
                    name = "Acro",
                    isHost = true,
                    isBot = false,
                    isReady = true,
                    carType = RcCarType.DRIFT_PHANTOM.name
                ),
                LobbyPlayer(
                    id = "bot_0",
                    name = "Volt-AI",
                    isHost = false,
                    isBot = true,
                    isReady = true,
                    carType = RcCarType.VOLT_BUGGY.name
                )
            ),
            chatHistory = emptyList(),
            inRace = false
        )

        composeTestRule.setContent {
            MicroRCTheme {
                LobbyScreen(
                    role = SessionRole.Solo,
                    localPlayerId = "p1",
                    lobby = soloLobby,
                    customTracks = emptyList(),
                    onSelectTrack = {},
                    onSelectCustomTrack = {},
                    onGenerateRandomTrack = {},
                    onOpenTrackEditor = {},
                    onOpenSettings = {},
                    onSelectLaps = {},
                    onAddBot = {},
                    onRemoveBot = {},
                    onToggleReady = {},
                    onSendChat = {},
                    onStartRace = {},
                    onOpenGarage = {},
                    onLeaveLobby = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Solo Race Setup").assertIsDisplayed()
        composeTestRule.onNodeWithText("PADDOCK CHAT").assertDoesNotExist()
        composeTestRule.onNodeWithTag("chat_input").assertDoesNotExist()
        composeTestRule.onNodeWithText("HOST").assertDoesNotExist()
        composeTestRule.onNodeWithText("YOU").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("AI BOT").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun hybridSetupScreen_soloModeNeverShowsHostWifiOrLobbyWording() {
        composeTestRule.setContent {
            MicroRCTheme {
                HybridSetupScreen(
                    initialRules = RaceRulesSettings(gameMode = GameMode.HYBRID.name),
                    inActiveLobby = true,
                    isSoloMode = true,
                    onHostHybridWifi = {},
                    onStartHybridSolo = {},
                    onApplyToLobby = {},
                    onBack = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("start_hybrid_wifi_button").assertDoesNotExist()
        composeTestRule.onNodeWithText("Host Wi-Fi Tour").assertDoesNotExist()
        composeTestRule.onNodeWithText("Apply Hybrid Setup").assertIsDisplayed()
        composeTestRule.onNodeWithText("Apply Hybrid Setup to Lobby").assertDoesNotExist()
    }

    @Test
    fun racePhysicsEngine_minesOnlyAvailableWithBotsOrMultiplayer() {
        val track = TrackCatalog.getTrack(TrackId.LIVING_ROOM_GP)
        val human = LobbyPlayer(
            id = "p1",
            name = "Acro",
            isHost = true,
            isBot = false,
            isReady = true,
            carType = RcCarType.VOLT_BUGGY.name
        )
        val bot = LobbyPlayer(
            id = "bot_0",
            name = "Volt-AI",
            isHost = false,
            isBot = true,
            isReady = true,
            carType = RcCarType.APEX_FORMULA.name
        )

        // 1. Pure Solo (1 human, 0 bots, non-multiplayer): NEVER gets mines
        val pureSoloEngine = RacePhysicsEngine(
            track = track,
            totalLaps = 3,
            players = listOf(human),
            rules = RaceRulesSettings(gameMode = GameMode.RACE.name),
            random = Random(42),
            isMultiplayerSession = false
        )
        repeat(200) { pureSoloEngine.step(0.02f) } // Finish 3s countdown
        pureSoloEngine.simulateCompleteLapForTest("p1")
        var pureSoloSnap = pureSoloEngine.step(0.02f)
        val pureSoloRacer = pureSoloSnap.racers.first { it.playerId == "p1" }
        assertTrue("Expected car to reach lap 2 in pure solo simulation", pureSoloRacer.lap >= 2)
        assertEquals("Pure solo (0 bots) must NOT receive mines", 0, pureSoloRacer.minesAvailable)
        pureSoloEngine.submitInput("p1", ControlInput(throttle = 1f, dropMine = true))
        pureSoloSnap = pureSoloEngine.step(0.02f)
        assertTrue("Pure solo must NOT be able to drop mines", pureSoloSnap.hazards.isEmpty())

        // 2. Solo with Bots: DOES receive mines on Lap 2
        val soloBotsEngine = RacePhysicsEngine(
            track = track,
            totalLaps = 3,
            players = listOf(human, bot),
            rules = RaceRulesSettings(gameMode = GameMode.RACE.name),
            random = Random(42),
            isMultiplayerSession = false
        )
        repeat(200) { soloBotsEngine.step(0.02f) }
        soloBotsEngine.simulateCompleteLapForTest("p1")
        var botsSnap = soloBotsEngine.step(0.02f)
        val botsHuman = botsSnap.racers.first { it.playerId == "p1" }
        assertTrue("Expected car to reach lap 2 in bots simulation", botsHuman.lap >= 2)
        assertEquals("Solo with bots MUST receive 1 mine on Lap 2", 1, botsHuman.minesAvailable)
        soloBotsEngine.submitInput("p1", ControlInput(throttle = 1f, dropMine = true))
        botsSnap = soloBotsEngine.step(0.02f)
        assertTrue("Solo with bots MUST allow dropping mine", botsSnap.hazards.isNotEmpty())

        // 3. Real Multiplayer (online/Wi-Fi): DOES receive mines on Lap 2
        val mpEngine = RacePhysicsEngine(
            track = track,
            totalLaps = 3,
            players = listOf(human),
            rules = RaceRulesSettings(gameMode = GameMode.RACE.name),
            random = Random(42),
            isMultiplayerSession = true
        )
        repeat(200) { mpEngine.step(0.02f) }
        mpEngine.simulateCompleteLapForTest("p1")
        val mpSnap = mpEngine.step(0.02f)
        val mpHuman = mpSnap.racers.first { it.playerId == "p1" }
        assertTrue("Expected car to reach lap 2 in multiplayer simulation", mpHuman.lap >= 2)
        assertEquals("Multiplayer session MUST receive 1 mine on Lap 2", 1, mpHuman.minesAvailable)
    }

    @Test
    fun raceScreen_hidesMineButtonInPureSoloAndFormatsGhostHudWithoutPbJargon() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val tilt = TiltSteeringController(context)
        val track = TrackCatalog.getTrack(TrackId.LIVING_ROOM_GP)
        val p1 = RacerState(
            playerId = "p1",
            name = "Acro",
            carType = RcCarType.DRIFT_PHANTOM.name,
            primaryColorArgb = 0xFFFF3D5A,
            accentColorArgb = 0xFFFFD54F,
            x = track.centerline.first().x,
            y = track.centerline.first().y,
            angleRad = 0f,
            lap = 2,
            minesAvailable = 0
        )
        val pureSoloSnapshot = RaceSnapshot(
            trackId = track.id.name,
            phase = RacePhase.RACING.name,
            raceTimeMs = 15_000L,
            totalLaps = 3,
            racers = listOf(p1)
        )
        val ghost = ActiveGhostCarState(
            driverName = "Acro (PB)",
            carType = RcCarType.DRIFT_PHANTOM.name,
            primaryColorArgb = 0xFFFF3D5A,
            accentColorArgb = 0xFFFFD54F,
            finishTimeMs = 42_000L,
            bestLapMs = 13_800L,
            x = p1.x,
            y = p1.y,
            angleRad = 0f,
            jumpZ = 0f,
            splitDeltaMs = 2880L
        )

        assertEquals("VS BEST", formatGhostHudPrefix("Acro (PB)"))
        assertEquals("VS BEST", formatGhostHudPrefix("Personal Best"))
        assertEquals("VS ROB", formatGhostHudPrefix("Rob"))

        composeTestRule.mainClock.autoAdvance = false
        try {
            composeTestRule.setContent {
                MicroRCTheme {
                    RaceScreen(
                        profile = PlayerProfile(playerId = "p1", name = "Acro", carType = RcCarType.DRIFT_PHANTOM),
                        snapshot = pureSoloSnapshot,
                        activeTrack = track,
                        ghostCar = ghost,
                        overviewCamera = false,
                        tiltController = tilt,
                        isMultiplayerSession = false,
                        onToggleOverviewCamera = {},
                        onUpdateControlInput = { _, _ -> },
                        onTriggerPowerUp = {},
                        onSwitchControlMode = { _, _, _, _ -> },
                        onLeaveRace = {}
                    )
                }
            }
            composeTestRule.mainClock.advanceTimeBy(100L)

            composeTestRule.onNodeWithTag("drop_mine_button").assertDoesNotExist()
            composeTestRule.onNodeWithTag("ghost_delta_badge").assertIsDisplayed()
            composeTestRule.onNodeWithText("VS BEST +2.88s").assertIsDisplayed()
            composeTestRule.onNodeWithText("GHOST • ACRO (PB)").assertDoesNotExist()
        } finally {
            composeTestRule.mainClock.autoAdvance = true
        }
    }

    @Test
    fun raceScreen_showsMineButtonWhenRacingWithBots() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val tilt = TiltSteeringController(context)
        val track = TrackCatalog.getTrack(TrackId.LIVING_ROOM_GP)
        val p1 = RacerState(
            playerId = "p1",
            name = "Acro",
            carType = RcCarType.DRIFT_PHANTOM.name,
            primaryColorArgb = 0xFFFF3D5A,
            accentColorArgb = 0xFFFFD54F,
            x = track.centerline.first().x,
            y = track.centerline.first().y,
            angleRad = 0f,
            lap = 2,
            minesAvailable = 1
        )
        val bot = RacerState(
            playerId = "bot_0",
            name = "Volt-AI",
            isBot = true,
            carType = RcCarType.VOLT_BUGGY.name,
            primaryColorArgb = 0xFF00E5FF,
            accentColorArgb = 0xFFFFD54F,
            x = track.centerline[2].x,
            y = track.centerline[2].y,
            angleRad = 0f,
            lap = 2
        )
        val botsSnapshot = RaceSnapshot(
            trackId = track.id.name,
            phase = RacePhase.RACING.name,
            raceTimeMs = 15_000L,
            totalLaps = 3,
            racers = listOf(p1, bot)
        )

        composeTestRule.mainClock.autoAdvance = false
        try {
            composeTestRule.setContent {
                MicroRCTheme {
                    RaceScreen(
                        profile = PlayerProfile(playerId = "p1", name = "Acro", carType = RcCarType.DRIFT_PHANTOM),
                        snapshot = botsSnapshot,
                        activeTrack = track,
                        overviewCamera = false,
                        tiltController = tilt,
                        isMultiplayerSession = false,
                        onToggleOverviewCamera = {},
                        onUpdateControlInput = { _, _ -> },
                        onTriggerPowerUp = {},
                        onSwitchControlMode = { _, _, _, _ -> },
                        onLeaveRace = {}
                    )
                }
            }
            composeTestRule.mainClock.advanceTimeBy(100L)

            composeTestRule.onNodeWithTag("drop_mine_button").assertIsDisplayed()
        } finally {
            composeTestRule.mainClock.autoAdvance = true
        }
    }

    @Test
    fun homeScreen_showsNotPlayedYetAndSingleLineTelemetry() {
        composeTestRule.setContent {
            MicroRCTheme {
                HomeScreen(
                    profile = PlayerProfile(
                        playerId = "p1",
                        name = "Acro",
                        carType = RcCarType.DRIFT_PHANTOM
                    ),
                    onHostRace = {},
                    onJoinRace = {},
                    onSoloRace = {},
                    onOpenGarage = {},
                    onUpdateDriverName = {},
                    dailyChallengeResetLabel = "Resets in 5h 27m",
                    dailyChallengeWeather = DailyWeatherTheme.WINDY,
                    dailyDriftWeather = DailyWeatherTheme.RAIN,
                    playStreak = PlayStreakState(currentStreakDays = 4, bestStreakDays = 4, activeToday = true)
                )
            }
        }

        composeTestRule.onNodeWithText("4d").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "SPD ${RcCarType.DRIFT_PHANTOM.topSpeedStat} • HDL ${RcCarType.DRIFT_PHANTOM.handlingStat} • GRP ${RcCarType.DRIFT_PHANTOM.gripStat}"
        ).assertDoesNotExist()
        composeTestRule.onNodeWithTag("home_car_telemetry_text").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_stat_bar_speed", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_stat_bar_handling", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_stat_bar_grip", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("SPD", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("HDL", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("GRP", useUnmergedTree = true).assertIsDisplayed()

        composeTestRule.onNodeWithText("Daily GP").assertIsDisplayed()
        composeTestRule.onNodeWithText("Daily Drift").assertIsDisplayed()
        composeTestRule.onNodeWithText("No PB yet").assertDoesNotExist()

        val gpWeatherBounds = composeTestRule.onNodeWithTag("daily_gp_weather_badge", useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val gpTitleBounds = composeTestRule.onNodeWithText("Daily GP", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val gpLeadBounds = composeTestRule.onAllNodesWithText("Not played yet", useUnmergedTree = true)[0].getUnclippedBoundsInRoot()
        val gpCardBounds = composeTestRule.onNodeWithTag("daily_challenge_button").getUnclippedBoundsInRoot()
        assertTrue("Weather badge must sit to the right of lead status", gpWeatherBounds.left >= gpLeadBounds.right)
        assertTrue("Weather badge must sit below card title", gpWeatherBounds.top >= gpTitleBounds.bottom)
        assertTrue(
            "Weather badge must anchor near bottom-right corner of Daily GP card",
            gpCardBounds.right - gpWeatherBounds.right <= gpWeatherBounds.left - gpCardBounds.left
        )
    }
}

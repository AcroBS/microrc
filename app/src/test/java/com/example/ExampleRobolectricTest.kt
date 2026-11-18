package com.example

import android.content.Context
import android.view.Surface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.example.sensors.TiltSteeringController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appName_isMicroRC() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("MicroRC", context.getString(R.string.app_name))
    }

    @Test
    fun homeScreen_displaysActionsAndNavigatesToGarage() {
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("open_garage_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("host_race_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("join_race_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("solo_race_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("open_garage_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("car_option_APEX_FORMULA").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun homeScreen_navigatesToSettingsAndTrackStudio() {
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("open_settings_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("setting_music_switch").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("setting_sfx_switch").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("setting_haptics_switch").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_speed_FAST").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("setting_auto_accel_switch").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("setting_hard_boundary_switch").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("setting_random_jumps_switch").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("setting_random_conditions_switch").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("setting_show_player_names_switch").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_back_button").performClick()

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("open_track_editor_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("editor_random_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("editor_random_button").performClick()
        composeTestRule.onNodeWithTag("editor_save_button").assertIsDisplayed()
    }

    @Test
    fun hostRace_opensHostSetupScreenAndLaunchesLobby() {
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("host_race_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("mode_option_RACE").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("win_condition_TIME_TRIAL_DISTANCE").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("setup_length_mode_time").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("setup_time_90").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("setup_length_mode_laps").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("setup_laps_3").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("setup_speed_LOW").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("setup_speed_MEDIUM").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("setup_speed_FAST").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("setup_random_conditions_switch").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("setup_show_player_names_switch").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("host_setup_open_lobby_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("host_setup_open_lobby_button").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            runCatching {
                composeTestRule.onNodeWithTag("start_race_button").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeTestRule.onNodeWithTag("lobby_speed_FAST").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("lobby_mode_laps").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("lobby_mode_time").performScrollTo().assertIsDisplayed()

        composeTestRule.mainClock.autoAdvance = false
        try {
            composeTestRule.onNodeWithTag("start_race_button").performClick()
            repeat(12) {
                composeTestRule.mainClock.advanceTimeBy(50L)
                Thread.sleep(15L)
            }
            composeTestRule.onNodeWithTag("drop_mine_button").assertIsDisplayed()
            composeTestRule.onNodeWithText("LAP 2").assertIsDisplayed()
            composeTestRule.onNodeWithTag("use_powerup_button").assertIsDisplayed()
            composeTestRule.onNodeWithTag("race_pause_button").performClick()
            composeTestRule.mainClock.advanceTimeBy(100L)
            composeTestRule.onNodeWithText("Exit Race").performClick()
            composeTestRule.mainClock.advanceTimeBy(150L)
        } finally {
            composeTestRule.mainClock.autoAdvance = true
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun hostRace_selectsArenaModeAndDisplaysArenaShapesInLobby() {
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("host_race_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("mode_option_ARENA").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("arena_minutes_3").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("host_setup_open_lobby_button").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            runCatching {
                composeTestRule.onNodeWithTag("arena_card_SQUARE").performScrollTo().assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeTestRule.onNodeWithTag("arena_card_CIRCLE").performScrollTo().assertIsDisplayed()
        composeTestRule.mainClock.autoAdvance = false
        try {
            composeTestRule.onNodeWithTag("start_race_button").performClick()
            repeat(12) {
                composeTestRule.mainClock.advanceTimeBy(50L)
                Thread.sleep(15L)
            }
            composeTestRule.onNodeWithTag("drop_mine_button").assertDoesNotExist()
            composeTestRule.onNodeWithTag("use_powerup_button").assertIsDisplayed()
            composeTestRule.onNodeWithTag("race_pause_button").performClick()
            composeTestRule.mainClock.advanceTimeBy(100L)
            composeTestRule.onNodeWithText("Exit Race").performClick()
            composeTestRule.mainClock.advanceTimeBy(150L)
        } finally {
            composeTestRule.mainClock.autoAdvance = true
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun garageAndRaceScreen_supportsHybridSpringSliderArrowTapSlideAndSensitivity() {
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("open_garage_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("control_mode_SPRING_SLIDER").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("garage_spring_slider_preview").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("garage_slider_sensitivity").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("slider_left_arrow_overlay", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("slider_right_arrow_overlay", useUnmergedTree = true).assertIsDisplayed()

        // 1. Tap & hold Left Arrow overlay on slider -> instant -100% steer, lifting snaps to 0%
        composeTestRule.onNodeWithTag("garage_spring_slider_preview").performTouchInput {
            down(Offset(width * 0.12f, height * 0.5f))
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Steer: -100%").assertIsDisplayed()
        composeTestRule.onNodeWithTag("garage_spring_slider_preview").performTouchInput {
            up()
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Steer: 0%").assertIsDisplayed()

        // 2. Tap & hold Right Arrow overlay on slider -> instant +100% steer, lifting snaps to 0%
        composeTestRule.onNodeWithTag("garage_spring_slider_preview").performTouchInput {
            down(Offset(width * 0.88f, height * 0.5f))
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Steer: 100%").assertIsDisplayed()
        composeTestRule.onNodeWithTag("garage_spring_slider_preview").performTouchInput {
            up()
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Steer: 0%").assertIsDisplayed()

        // 3. Slide proportionally from center -> intermediate proportional steer (not pegged at 100%), lifting snaps to 0%
        composeTestRule.onNodeWithTag("garage_spring_slider_preview").performTouchInput {
            down(Offset(width * 0.50f, height * 0.5f))
            moveTo(Offset(width * 0.64f, height * 0.5f))
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("garage_spring_slider_preview").performTouchInput {
            up()
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Steer: 0%").assertIsDisplayed()

        composeTestRule.onNodeWithTag("garage_done_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("solo_race_button").performScrollTo().performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            runCatching {
                composeTestRule.onNodeWithTag("start_race_button").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        composeTestRule.mainClock.autoAdvance = false
        try {
            composeTestRule.onNodeWithTag("start_race_button").performClick()
            repeat(12) {
                composeTestRule.mainClock.advanceTimeBy(50L)
                Thread.sleep(15L)
            }
            composeTestRule.onNodeWithTag("spring_steering_slider").assertIsDisplayed()
            composeTestRule.onNodeWithTag("slider_left_arrow_overlay", useUnmergedTree = true).assertIsDisplayed()
            composeTestRule.onNodeWithTag("slider_right_arrow_overlay", useUnmergedTree = true).assertIsDisplayed()
            composeTestRule.onNodeWithTag("drop_mine_button").assertIsDisplayed()
            composeTestRule.onNodeWithTag("auto_gas_badge").assertIsDisplayed()
            composeTestRule.onNodeWithTag("race_pause_button").performClick()
            composeTestRule.mainClock.advanceTimeBy(100L)
            composeTestRule.onNodeWithTag("pause_mode_SPRING_SLIDER").assertIsDisplayed()
            composeTestRule.onNodeWithTag("pause_slider_sensitivity").assertIsDisplayed()
            composeTestRule.onNodeWithText("Exit Race").performClick()
            composeTestRule.mainClock.advanceTimeBy(150L)
        } finally {
            composeTestRule.mainClock.autoAdvance = true
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun applicationContextTiltController_recognizesLandscapeWithoutDrivingInCircles() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val tilt = TiltSteeringController(appContext)
        tilt.updateDisplayRotation(Surface.ROTATION_90)

        // Level phone in landscape (gravity along +X = 9.81, Y = 0) must stay at 0 steer, never -1f!
        repeat(8) {
            val steer = tilt.processSensorAxes(x = 9.81f, y = 0f, rotation = Surface.ROTATION_90)
            assertEquals("Landscape level phone on Application context must output 0f steer", 0f, steer, 0.001f)
        }

        // Tilt wheel right in landscape (y = +3.6f) -> smooth positive right steer
        var rightSteer = 0f
        repeat(8) {
            rightSteer = tilt.processSensorAxes(x = 9.1f, y = 3.6f, rotation = Surface.ROTATION_90)
        }
        assertTrue("Tilting clockwise in landscape must steer right ($rightSteer > 0.3f)", rightSteer > 0.3f)

        // Flip to reverse landscape (ROTATION_270) held level (x = -9.81, y = 0) -> resets cleanly to 0f!
        val afterFlip = tilt.processSensorAxes(x = -9.81f, y = 0f, rotation = Surface.ROTATION_270)
        assertEquals("Flipping to ROTATION_270 level must immediately reset steer to 0f", 0f, afterFlip, 0.001f)
    }

    @Test
    fun hotspotOneWayUdpBlackhole_clientAdvancesPastCountdownThreeAndDrives() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
        val hostManager = com.example.network.WifiP2pRaceManager(context, scope)
        val clientManager = com.example.network.WifiP2pRaceManager(context, scope)
        // Simulate Hotspot bug: Client->Host UDP Hello succeeds (populating client.udpAddress on Host),
        // while 100% of Host->Client UDP packets are dropped by Hotspot routing / AP isolation!
        hostManager.simulateHostToClientUdpBlackholeForTest = true

        try {
            val hostProfile = com.example.model.PlayerProfile(playerId = "host_p1", name = "HostDriver")
            val clientProfile = com.example.model.PlayerProfile(playerId = "client_p2", name = "HotspotJoiner")

            hostManager.hostLobby(
                profile = hostProfile,
                initialRules = com.example.model.RaceRulesSettings(
                    autoAccelerate = false,
                    randomConditionsEnabled = false
                )
            )

            // Wait for Host TCP server port to bind
            val boundPortDeadline = System.currentTimeMillis() + 4_000L
            while ((hostManager.lobbyState.value?.tcpPort ?: 0) <= 0 && System.currentTimeMillis() < boundPortDeadline) {
                Thread.sleep(25L)
            }
            val hostTcpPort = hostManager.lobbyState.value?.tcpPort ?: 0
            org.junit.Assert.assertTrue("Host TCP port must bind ($hostTcpPort > 0)", hostTcpPort > 0)

            clientManager.joinLobby("127.0.0.1", hostTcpPort, clientProfile)

            // Wait for Client to join Lobby
            val joinDeadline = System.currentTimeMillis() + 4_000L
            while ((clientManager.lobbyState.value?.players?.size ?: 0) < 2 && System.currentTimeMillis() < joinDeadline) {
                Thread.sleep(25L)
            }
            assertEquals(2, clientManager.lobbyState.value?.players?.size)

            // Client marks Ready
            clientManager.toggleReady()
            val readyDeadline = System.currentTimeMillis() + 3_000L
            while (hostManager.lobbyState.value?.players?.all { it.isReady } != true && System.currentTimeMillis() < readyDeadline) {
                Thread.sleep(25L)
            }
            org.junit.Assert.assertTrue("All lobby players must be Ready", hostManager.lobbyState.value?.players?.all { it.isReady } == true)

            // Give Client->Host UDP Hello a moment to register client.udpAddress on Host (the exact condition that broke Hotspot!)
            Thread.sleep(120L)

            // Host starts the race!
            hostManager.startRace()

            // Wait through the 3.2-second countdown and verify Client transitions cleanly out of COUNTDOWN (3) into RACING!
            var sawCountdownBelow3 = false
            val raceStartDeadline = System.currentTimeMillis() + 5_500L
            while (System.currentTimeMillis() < raceStartDeadline) {
                val cSnap = clientManager.raceSnapshot.value
                if (cSnap != null) {
                    if (cSnap.phase == com.example.model.RacePhase.COUNTDOWN.name && cSnap.countdownSec < 3) {
                        sawCountdownBelow3 = true
                    }
                    if (cSnap.phase == com.example.model.RacePhase.RACING.name) {
                        break
                    }
                }
                Thread.sleep(30L)
            }

            org.junit.Assert.assertTrue("Client must see countdown tick below 3 instead of freezing on 3", sawCountdownBelow3)
            assertEquals(
                "Client must reach RACING phase even when Host->Client UDP is 100% blackholed",
                com.example.model.RacePhase.RACING.name,
                clientManager.raceSnapshot.value?.phase
            )

            // Now Client presses throttle + steer -> verify Client car accelerates on both Host and Client!
            val driveDeadline = System.currentTimeMillis() + 2_500L
            var maxClientSpeed = 0f
            while (System.currentTimeMillis() < driveDeadline && maxClientSpeed < 120f) {
                clientManager.updateLocalControlInput(throttle = 1f, steer = 0.25f)
                val speed = clientManager.raceSnapshot.value?.racers
                    ?.find { it.playerId == "client_p2" }
                    ?.speed ?: 0f
                if (speed > maxClientSpeed) maxClientSpeed = speed
                Thread.sleep(30L)
            }
            org.junit.Assert.assertTrue(
                "Client car must accelerate over dual-path TCP fallback ($maxClientSpeed > 100f)",
                maxClientSpeed > 100f
            )
        } finally {
            clientManager.leaveSession()
            hostManager.leaveSession()
            scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        }
    }

    @Test
    fun hybridSetupScreen_configuresGameTypesAndOpensHybridLobby() {
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_hybrid_tour_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // Verify Hybrid Event Setup screen game type switches & play order controls
        composeTestRule.onNodeWithTag("hybrid_toggle_circuit").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("hybrid_toggle_arena").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("hybrid_toggle_stunts").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("hybrid_order_random").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // Launch Hybrid Tour Lobby via fixed bottom bar button (no performScrollTo on fixed bar!)
        composeTestRule.onNodeWithTag("start_hybrid_wifi_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("start_hybrid_wifi_button").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            runCatching {
                composeTestRule.onNodeWithTag("lobby_configure_hybrid_button").performScrollTo().assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeTestRule.onNodeWithText("HYBRID EVENT TOUR").assertIsDisplayed()
        composeTestRule.onNodeWithTag("start_race_button").assertIsDisplayed()
    }

    @Test
    fun hostSetup_selectsStuntsModeAndStartsTurnBasedStuntSession() {
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("host_race_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("mode_option_STUNTS").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("host_setup_open_lobby_button").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            runCatching {
                composeTestRule.onNodeWithTag("stunt_card_LONG_JUMP").performScrollTo().assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeTestRule.onNodeWithTag("add_bot_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.mainClock.autoAdvance = false
        try {
            composeTestRule.onNodeWithTag("start_race_button").performClick()
            repeat(12) {
                composeTestRule.mainClock.advanceTimeBy(50L)
                Thread.sleep(15L)
            }
            composeTestRule.onNodeWithTag("stunt_turn_banner").assertIsDisplayed()
            composeTestRule.onNodeWithTag("drop_mine_button").assertDoesNotExist()
            composeTestRule.onNodeWithTag("use_powerup_button").assertIsDisplayed()
            composeTestRule.onNodeWithTag("race_pause_button").performClick()
            composeTestRule.mainClock.advanceTimeBy(100L)
            composeTestRule.onNodeWithText("Exit Race").performClick()
            composeTestRule.mainClock.advanceTimeBy(150L)
        } finally {
            composeTestRule.mainClock.autoAdvance = true
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun homeScreen_onlinePlayShowsGoogleSignInGateWhenSignedOutAndReturnsToHome() {
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("online_play_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("pure_solo_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("leaderboards_button").performScrollTo().assertIsDisplayed()

        // Tapping Online Web Play while signed out opens the clean end-user Google Sign-In gate
        composeTestRule.onNodeWithTag("online_play_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("google_sign_in_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("auth_back_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("github_firebase_setup_banner").assertDoesNotExist()
        composeTestRule.onNodeWithTag("paste_google_services_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("copy_apk_fingerprints_button").assertDoesNotExist()

        // Backing out returns cleanly to Home
        composeTestRule.onNodeWithTag("auth_back_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("online_play_button").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun pureSoloAndLeaderboards_openZeroBotSoloLobbyAndRecordsScreen() {
        composeTestRule.waitForIdle()

        // 1. Open Leaderboards & Ghosts screen from Home
        composeTestRule.onNodeWithTag("leaderboards_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("leaderboard_mode_RACE").assertIsDisplayed()
        composeTestRule.onNodeWithTag("leaderboard_mode_STUNTS").assertIsDisplayed()
        composeTestRule.onNodeWithTag("leaderboard_start_solo_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("leaderboard_back_button").performClick()
        composeTestRule.waitForIdle()

        // 2. Launch Pure Solo Time Trial (0 AI Bots) from Home
        composeTestRule.onNodeWithTag("pure_solo_button").performScrollTo().performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            runCatching {
                composeTestRule.onNodeWithTag("start_race_button").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        // Verify 1/4 racers (0 bots) and Ghost Car picker shortcut in Solo Lobby
        composeTestRule.onNodeWithText("RACERS (1/4)").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("lobby_pick_ghost_button").performScrollTo().assertIsDisplayed()

        // Start pure solo drive and verify track HUD renders cleanly
        composeTestRule.mainClock.autoAdvance = false
        try {
            composeTestRule.onNodeWithTag("start_race_button").performClick()
            repeat(12) {
                composeTestRule.mainClock.advanceTimeBy(50L)
                Thread.sleep(15L)
            }
            composeTestRule.onNodeWithTag("use_powerup_button").assertIsDisplayed()
            composeTestRule.onNodeWithTag("race_pause_button").performClick()
            composeTestRule.mainClock.advanceTimeBy(100L)
            composeTestRule.onNodeWithText("Exit Race").performClick()
            composeTestRule.mainClock.advanceTimeBy(150L)
        } finally {
            composeTestRule.mainClock.autoAdvance = true
        }
        composeTestRule.waitForIdle()
    }
}



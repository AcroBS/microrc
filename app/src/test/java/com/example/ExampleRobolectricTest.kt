package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
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
        composeTestRule.onNodeWithTag("lobby_leave_button").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun garageAndRaceScreen_supportsSpringSliderControlMode() {
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("open_garage_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("control_mode_SPRING_SLIDER").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("garage_spring_slider_preview").performScrollTo().assertIsDisplayed()
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
            composeTestRule.onNodeWithTag("auto_gas_badge").assertIsDisplayed()
            composeTestRule.onNodeWithTag("race_pause_button").performClick()
            composeTestRule.mainClock.advanceTimeBy(100L)
            composeTestRule.onNodeWithTag("pause_mode_SPRING_SLIDER").assertIsDisplayed()
            composeTestRule.onNodeWithText("Exit Race").performClick()
            composeTestRule.mainClock.advanceTimeBy(150L)
        } finally {
            composeTestRule.mainClock.autoAdvance = true
        }
        composeTestRule.waitForIdle()
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
}

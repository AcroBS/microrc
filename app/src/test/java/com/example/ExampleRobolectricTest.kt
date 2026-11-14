package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
        composeTestRule.onNodeWithTag("setting_auto_accel_switch").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("setting_hard_boundary_switch").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("setting_random_jumps_switch").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("setting_random_conditions_switch").performScrollTo().assertIsDisplayed()
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

        composeTestRule.onNodeWithTag("win_condition_TIME_TRIAL_DISTANCE").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("setup_random_conditions_switch").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("host_setup_open_lobby_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("host_setup_open_lobby_button").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            runCatching {
                composeTestRule.onNodeWithTag("start_race_button").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeTestRule.onNodeWithTag("lobby_leave_button").performClick()
        composeTestRule.waitForIdle()
    }
}

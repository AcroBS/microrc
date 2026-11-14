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
}

package com.example

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.audio.RaceAudioEngine
import com.example.data.PreferencesRepository
import com.example.data.TrackCatalog
import com.example.model.AudioSettings
import com.example.model.ControlMode
import com.example.model.GameMode
import com.example.model.PlayerProfile
import com.example.model.RacePhase
import com.example.model.RaceRulesSettings
import com.example.model.RaceSnapshot
import com.example.model.RacerState
import com.example.model.RcCarType
import com.example.model.TrackId
import com.example.sensors.TiltSteeringController
import com.example.ui.screens.RaceScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.computeLiveLapDeltaMs
import com.example.ui.screens.formatLapDeltaSeconds
import com.example.ui.theme.MicroRCTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SettingsAndHudRefinementsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun audioEngineGainMath_andPreferencesPersistence_workProperly() = runBlocking {
        assertEquals(0f, RaceAudioEngine.computeMusicGain(musicEnabled = false, musicVolume = 1f), 0.0001f)
        assertEquals(0.5f, RaceAudioEngine.computeMusicGain(musicEnabled = true, musicVolume = 0.5f), 0.0001f)
        assertEquals(0f, RaceAudioEngine.computeSfxGain(carSfxEnabled = false, sfxVolume = 0.9f), 0.0001f)
        assertEquals(0.5f, RaceAudioEngine.computeSfxGain(carSfxEnabled = true, sfxVolume = 0.5f), 0.0001f)

        val app = ApplicationProvider.getApplicationContext<Application>()
        val repo = PreferencesRepository(app)
        repo.updateAudioSettings(AudioSettings(musicEnabled = true, carSfxEnabled = true, hapticsEnabled = false, musicVolume = 0.35f, sfxVolume = 0.65f))
        repo.updateSwapControls(true)
        repo.updateShowLiveLapDelta(false)
        repo.updateShowTrackMiniMap(false)

        val audio = repo.audioSettingsFlow.first()
        val profile = repo.profileFlow.first()
        assertEquals(0.35f, audio.musicVolume, 0.001f)
        assertEquals(0.65f, audio.sfxVolume, 0.001f)
        assertTrue(profile.swapControls)
        assertFalse(profile.showLiveLapDelta)
        assertFalse(profile.showTrackMiniMap)
    }

    @Test
    fun liveLapDeltaMath_computesAheadBehindAndIgnoresPreStartLap1() {
        // Behind start line on Lap 1 -> null
        assertNull(
            computeLiveLapDeltaMs(
                raceTimeMs = 1200L,
                lap = 1,
                checkpointIndex = 95,
                sectorMask = 0,
                currentLapStartMs = 0L,
                sessionBestLapMs = null,
                storedPbLapMs = 20_000L,
                trackPointCount = 100
            )
        )

        // Halfway (checkpoint 50/100) on 20_000ms PB -> expected 10_000ms; at 9_250ms -> -750ms (-0.75s)
        val ahead = computeLiveLapDeltaMs(
            raceTimeMs = 9_250L,
            lap = 1,
            checkpointIndex = 50,
            sectorMask = 0b0001,
            currentLapStartMs = 0L,
            sessionBestLapMs = null,
            storedPbLapMs = 20_000L,
            trackPointCount = 100
        )
        assertEquals(-750L, ahead)
        assertEquals("-0.75s", formatLapDeltaSeconds(ahead!!))

        // Halfway at 11_420ms -> +1_420ms (+1.42s)
        val behind = computeLiveLapDeltaMs(
            raceTimeMs = 31_420L,
            lap = 2,
            checkpointIndex = 50,
            sectorMask = 0b0010,
            currentLapStartMs = 20_000L,
            sessionBestLapMs = 20_000L,
            storedPbLapMs = null,
            trackPointCount = 100
        )
        assertEquals(1_420L, behind)
        assertEquals("+1.42s", formatLapDeltaSeconds(behind!!))
    }

    @Test
    fun settingsScreen_showsVolumeSlidersOnlyWhenEnabled_hudToggles_andSpeedHelperNote() {
        var latestProfile = PlayerProfile(playerId = "p1", name = "Acro")
        var latestAudio = AudioSettings(musicEnabled = true, carSfxEnabled = true, musicVolume = 0.7f, sfxVolume = 0.5f)

        composeTestRule.setContent {
            var profileState by remember { mutableStateOf(latestProfile) }
            var audioState by remember { mutableStateOf(latestAudio) }
            MicroRCTheme {
                SettingsScreen(
                    rules = RaceRulesSettings(),
                    profile = profileState,
                    audioSettings = audioState,
                    canEditRules = true,
                    inSession = false,
                    onUpdateRules = {},
                    onUpdateSwapControls = {
                        profileState = profileState.copy(swapControls = it)
                        latestProfile = profileState
                    },
                    onUpdateShowLiveLapDelta = {
                        profileState = profileState.copy(showLiveLapDelta = it)
                        latestProfile = profileState
                    },
                    onUpdateShowTrackMiniMap = {
                        profileState = profileState.copy(showTrackMiniMap = it)
                        latestProfile = profileState
                    },
                    onUpdateAudio = {
                        audioState = it
                        latestAudio = it
                    },
                    onBack = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // Steering HUD toggles present & reactive
        composeTestRule.onNodeWithTag("setting_swap_controls_switch").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("setting_live_lap_delta_switch").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("setting_show_minimap_switch").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertTrue(latestProfile.swapControls)
        assertFalse(latestProfile.showLiveLapDelta)
        assertFalse(latestProfile.showTrackMiniMap)

        // Volume sliders visible while toggles enabled
        composeTestRule.onNodeWithTag("setting_music_volume_slider").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("setting_sfx_volume_slider").performScrollTo().assertIsDisplayed()

        // Disabling Music & SFX hides respective volume sliders
        composeTestRule.onNodeWithTag("setting_music_switch").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("setting_sfx_switch").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("setting_music_volume_slider").assertDoesNotExist()
        composeTestRule.onNodeWithTag("setting_sfx_volume_slider").assertDoesNotExist()

        // General Car Speed helper note present
        composeTestRule.onNodeWithTag("settings_speed_helper_note").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "Applies to Solo Play & Custom Lobbies. Daily Challenge & Daily Drift use standardized spec speed for fair leaderboards."
        ).assertIsDisplayed()
    }

    @Test
    fun raceScreen_honorsSwapControls_minimapToggle_andLiveLapDeltaToggle() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val tiltController = TiltSteeringController(app)
        val track = TrackCatalog.getTrack(TrackId.LIVING_ROOM_GP)
        val halfwayIdx = (track.centerline.size / 2).coerceAtLeast(1)
        val snapshot = RaceSnapshot(
            raceTimeMs = 9_500L,
            phase = RacePhase.RACING.name,
            trackId = TrackId.LIVING_ROOM_GP.name,
            totalLaps = 3,
            gameMode = GameMode.RACE.name,
            racers = listOf(
                RacerState(
                    playerId = "p1",
                    name = "Acro",
                    carType = RcCarType.APEX_FORMULA.name,
                    primaryColorArgb = 0xFFFF5722,
                    accentColorArgb = 0xFFFFD54F,
                    x = 1200f,
                    y = 950f,
                    angleRad = 0f,
                    lap = 1,
                    checkpointIndex = halfwayIdx,
                    sectorMask = 0b0001,
                    currentLapStartMs = 0L
                )
            )
        )

        var profileState by mutableStateOf(
            PlayerProfile(
                playerId = "p1",
                name = "Acro",
                controlMode = ControlMode.TOUCH_OVERLAY,
                swapControls = false,
                showLiveLapDelta = true,
                showTrackMiniMap = true
            )
        )

        composeTestRule.mainClock.autoAdvance = false
        try {
            composeTestRule.setContent {
                MicroRCTheme {
                    RaceScreen(
                        profile = profileState,
                        snapshot = snapshot,
                        activeTrack = track,
                        overviewCamera = false,
                        tiltController = tiltController,
                        personalBestLapMs = 20_000L,
                        onToggleOverviewCamera = {},
                        onUpdateControlInput = { _, _ -> },
                        onTriggerPowerUp = {},
                        onSwitchControlMode = { _, _, _, _ -> },
                        onLeaveRace = {}
                    )
                }
            }
            repeat(3) { composeTestRule.mainClock.advanceTimeByFrame() }

            // Default layout: steer_left is left of brake, minimap & live lap delta badge shown
            composeTestRule.onNodeWithTag("race_minimap").assertIsDisplayed()
            composeTestRule.onNodeWithTag("live_lap_delta_badge").assertIsDisplayed()
            val defaultSteerLeft = composeTestRule.onNodeWithTag("steer_left_button").getUnclippedBoundsInRoot()
            val defaultBrake = composeTestRule.onNodeWithTag("brake_button").getUnclippedBoundsInRoot()
            assertTrue(defaultSteerLeft.left < defaultBrake.left)

            // Swapped & hidden HUD toggles: brake moves left of steer_left, minimap & delta badge hide
            profileState = profileState.copy(
                swapControls = true,
                showLiveLapDelta = false,
                showTrackMiniMap = false
            )
            repeat(3) { composeTestRule.mainClock.advanceTimeByFrame() }

            composeTestRule.onNodeWithTag("race_minimap").assertDoesNotExist()
            composeTestRule.onNodeWithTag("live_lap_delta_badge").assertDoesNotExist()
            val swappedSteerLeft = composeTestRule.onNodeWithTag("steer_left_button").getUnclippedBoundsInRoot()
            val swappedBrake = composeTestRule.onNodeWithTag("brake_button").getUnclippedBoundsInRoot()
            assertTrue(swappedBrake.left < swappedSteerLeft.left)
        } finally {
            composeTestRule.mainClock.autoAdvance = true
        }
    }
}

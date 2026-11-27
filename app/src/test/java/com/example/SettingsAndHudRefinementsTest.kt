package com.example

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.audio.RaceAudioEngine
import com.example.data.PreferencesRepository
import com.example.data.TrackCatalog
import com.example.model.AudioSettings
import com.example.model.ControlButtonSize
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
        assertEquals(ControlButtonSize.TALL, repo.profileFlow.first().controlButtonSize)
        repo.updateAudioSettings(AudioSettings(musicEnabled = true, carSfxEnabled = true, hapticsEnabled = false, musicVolume = 0.35f, sfxVolume = 0.65f))
        repo.updateSwapControls(true)
        repo.updateControlButtonSize(ControlButtonSize.EXTRA_TALL)
        repo.updateShowLiveLapDelta(false)
        repo.updateShowTrackMiniMap(false)

        val audio = repo.audioSettingsFlow.first()
        val profile = repo.profileFlow.first()
        assertEquals(0.35f, audio.musicVolume, 0.001f)
        assertEquals(0.65f, audio.sfxVolume, 0.001f)
        assertTrue(profile.swapControls)
        assertEquals(ControlButtonSize.EXTRA_TALL, profile.controlButtonSize)
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
                    onUpdateControlButtonSize = {
                        profileState = profileState.copy(controlButtonSize = it)
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

        // Steering HUD toggles + Control Button Size pill present & reactive
        composeTestRule.onNodeWithTag("setting_control_size_TALL").performScrollTo().assertIsDisplayed().assert(isSelected())
        composeTestRule.onNodeWithText("Compact 88dp").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tall 116dp").assertIsDisplayed()
        composeTestRule.onNodeWithText("Extra Tall 140dp").assertIsDisplayed()

        composeTestRule.onNodeWithTag("setting_control_size_EXTRA_TALL").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertEquals(ControlButtonSize.EXTRA_TALL, latestProfile.controlButtonSize)
        composeTestRule.onNodeWithTag("setting_control_size_COMPACT").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertEquals(ControlButtonSize.COMPACT, latestProfile.controlButtonSize)

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

    @Test
    fun raceScreen_centersNotificationBannersAndStatusPillsHorizontallyWithMiniMap() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val tiltController = TiltSteeringController(app)
        val track = TrackCatalog.getTrack(TrackId.LIVING_ROOM_GP)
        val snapshot = RaceSnapshot(
            raceTimeMs = 4_500L,
            phase = RacePhase.RACING.name,
            trackId = TrackId.LIVING_ROOM_GP.name,
            totalLaps = 3,
            gameMode = GameMode.RACE.name,
            eventBanner = "Acro triggered LiPo Turbo!",
            racers = listOf(
                RacerState(
                    playerId = "p1",
                    name = "Acro",
                    carType = RcCarType.VOLT_BUGGY.name,
                    primaryColorArgb = 0xFFFF5722,
                    accentColorArgb = 0xFFFFD54F,
                    x = 1200f,
                    y = 950f,
                    angleRad = 0f,
                    lap = 1,
                    boostMs = 1500L,
                    perfectLaunchBannerMs = 900L,
                    shieldMs = 1200L
                )
            )
        )

        composeTestRule.mainClock.autoAdvance = false
        try {
            composeTestRule.setContent {
                MicroRCTheme {
                    RaceScreen(
                        profile = PlayerProfile(
                            playerId = "p1",
                            name = "Acro",
                            controlMode = ControlMode.TOUCH_OVERLAY,
                            showTrackMiniMap = true
                        ),
                        snapshot = snapshot,
                        activeTrack = track,
                        overviewCamera = false,
                        tiltController = tiltController,
                        onToggleOverviewCamera = {},
                        onUpdateControlInput = { _, _ -> },
                        onTriggerPowerUp = {},
                        onSwitchControlMode = { _, _, _, _ -> },
                        onLeaveRace = {}
                    )
                }
            }
            repeat(3) { composeTestRule.mainClock.advanceTimeByFrame() }

            val rootBounds = composeTestRule.onRoot().getUnclippedBoundsInRoot()
            val rootCenterX = (rootBounds.left.value + rootBounds.right.value) / 2f

            val minimapBounds = composeTestRule.onNodeWithTag("race_minimap").getUnclippedBoundsInRoot()
            val bannerBounds = composeTestRule.onNodeWithTag("top_event_banner").getUnclippedBoundsInRoot()
            val calloutsBounds = composeTestRule.onNodeWithTag("race_status_callout_pill").getUnclippedBoundsInRoot()

            val bannerCenterX = (bannerBounds.left.value + bannerBounds.right.value) / 2f
            val calloutsCenterX = (calloutsBounds.left.value + calloutsBounds.right.value) / 2f

            assertTrue(
                "Event banner center ($bannerCenterX) must match root center ($rootCenterX)",
                kotlin.math.abs(bannerCenterX - rootCenterX) <= 2f
            )
            assertTrue(
                "Status pill center ($calloutsCenterX) must match root center ($rootCenterX)",
                kotlin.math.abs(calloutsCenterX - rootCenterX) <= 2f
            )
            assertTrue(
                "Top banner (top=${bannerBounds.top}) must sit below mini-map (bottom=${minimapBounds.bottom})",
                bannerBounds.top >= minimapBounds.bottom
            )
        } finally {
            composeTestRule.mainClock.autoAdvance = true
        }
    }

    @Test
    fun raceScreen_rendersHomogeneousControlsAnchored16DpFromBottom_andHonorsControlButtonSize() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val tiltController = TiltSteeringController(app)
        val track = TrackCatalog.getTrack(TrackId.LIVING_ROOM_GP)
        val snapshot = RaceSnapshot(
            raceTimeMs = 2_000L,
            phase = RacePhase.RACING.name,
            trackId = TrackId.LIVING_ROOM_GP.name,
            totalLaps = 3,
            gameMode = GameMode.RACE.name,
            racers = listOf(
                RacerState(
                    playerId = "p1",
                    name = "Acro",
                    carType = RcCarType.VOLT_BUGGY.name,
                    primaryColorArgb = 0xFFFF5722,
                    accentColorArgb = 0xFFFFD54F,
                    x = 1200f,
                    y = 950f,
                    angleRad = 0f,
                    lap = 1
                )
            )
        )
        var profileState by mutableStateOf(
            PlayerProfile(
                playerId = "p1",
                name = "Acro",
                controlMode = ControlMode.TOUCH_OVERLAY,
                controlButtonSize = ControlButtonSize.TALL
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
                        onToggleOverviewCamera = {},
                        onUpdateControlInput = { _, _ -> },
                        onTriggerPowerUp = {},
                        onSwitchControlMode = { _, _, _, _ -> },
                        onLeaveRace = {}
                    )
                }
            }
            repeat(3) { composeTestRule.mainClock.advanceTimeByFrame() }

            val rootBounds = composeTestRule.onRoot().getUnclippedBoundsInRoot()
            val leftBounds = composeTestRule.onNodeWithTag("steer_left_button").getUnclippedBoundsInRoot()
            val rightBounds = composeTestRule.onNodeWithTag("steer_right_button").getUnclippedBoundsInRoot()
            val brakeBounds = composeTestRule.onNodeWithTag("brake_button").getUnclippedBoundsInRoot()
            val powerBounds = composeTestRule.onNodeWithTag("use_powerup_button").getUnclippedBoundsInRoot()

            // Exact uniform 116.dp height & identical bottom baseline 16.dp above screen bottom
            assertTrue((leftBounds.bottom - leftBounds.top) in 115.dp..117.dp)
            assertTrue((rightBounds.bottom - rightBounds.top) in 115.dp..117.dp)
            assertTrue((brakeBounds.bottom - brakeBounds.top) in 115.dp..117.dp)
            assertTrue((rootBounds.bottom - leftBounds.bottom) in 15.dp..17.dp)
            assertTrue((rootBounds.bottom - brakeBounds.bottom) in 15.dp..17.dp)

            // Equal 88.dp steering widths with 10.dp gap, 112.dp BRAKE & aligned Power-Up 12.dp above
            assertTrue((leftBounds.right - leftBounds.left) in 87.dp..89.dp)
            assertTrue((rightBounds.right - rightBounds.left) in 87.dp..89.dp)
            assertTrue((rightBounds.left - leftBounds.right) in 9.dp..11.dp)
            assertTrue((brakeBounds.right - brakeBounds.left) in 111.dp..113.dp)
            assertTrue((powerBounds.right - powerBounds.left) in 111.dp..113.dp)
            assertTrue(kotlin.math.abs(powerBounds.right.value - brakeBounds.right.value) <= 1f)
            assertTrue((brakeBounds.top - powerBounds.bottom) in 11.dp..13.dp)

            // COMPACT 88dp preset
            profileState = profileState.copy(controlButtonSize = ControlButtonSize.COMPACT)
            repeat(3) { composeTestRule.mainClock.advanceTimeByFrame() }
            val compactLeft = composeTestRule.onNodeWithTag("steer_left_button").getUnclippedBoundsInRoot()
            val compactBrake = composeTestRule.onNodeWithTag("brake_button").getUnclippedBoundsInRoot()
            assertTrue((compactLeft.bottom - compactLeft.top) in 87.dp..89.dp)
            assertTrue((compactBrake.bottom - compactBrake.top) in 87.dp..89.dp)
            assertTrue((rootBounds.bottom - compactBrake.bottom) in 15.dp..17.dp)

            // EXTRA_TALL 140dp preset
            profileState = profileState.copy(controlButtonSize = ControlButtonSize.EXTRA_TALL)
            repeat(3) { composeTestRule.mainClock.advanceTimeByFrame() }
            val extraLeft = composeTestRule.onNodeWithTag("steer_left_button").getUnclippedBoundsInRoot()
            val extraBrake = composeTestRule.onNodeWithTag("brake_button").getUnclippedBoundsInRoot()
            assertTrue((extraLeft.bottom - extraLeft.top) in 139.dp..141.dp)
            assertTrue((extraBrake.bottom - extraBrake.top) in 139.dp..141.dp)
            assertTrue((rootBounds.bottom - extraBrake.bottom) in 15.dp..17.dp)
        } finally {
            composeTestRule.mainClock.autoAdvance = true
        }
    }

    @Test
    fun raceScreen_touchControls_supportEdgeToEdgeTapAndSmoothSlideBetweenArrows() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val tiltController = TiltSteeringController(app)
        val track = TrackCatalog.getTrack(TrackId.LIVING_ROOM_GP)
        val snapshot = RaceSnapshot(
            raceTimeMs = 2_000L,
            phase = RacePhase.RACING.name,
            trackId = TrackId.LIVING_ROOM_GP.name,
            totalLaps = 3,
            gameMode = GameMode.RACE.name,
            racers = listOf(
                RacerState(
                    playerId = "p1",
                    name = "Acro",
                    carType = RcCarType.VOLT_BUGGY.name,
                    primaryColorArgb = 0xFFFF5722,
                    accentColorArgb = 0xFFFFD54F,
                    x = 1200f,
                    y = 950f,
                    angleRad = 0f,
                    lap = 1
                )
            )
        )
        var latestThrottle = 0f
        var latestSteer = 0f

        composeTestRule.mainClock.autoAdvance = false
        try {
            composeTestRule.setContent {
                MicroRCTheme {
                    RaceScreen(
                        profile = PlayerProfile(
                            playerId = "p1",
                            name = "Acro",
                            controlMode = ControlMode.TOUCH_OVERLAY
                        ),
                        snapshot = snapshot,
                        activeTrack = track,
                        overviewCamera = false,
                        tiltController = tiltController,
                        onToggleOverviewCamera = {},
                        onUpdateControlInput = { throttle, steer ->
                            latestThrottle = throttle
                            latestSteer = steer
                        },
                        onTriggerPowerUp = {},
                        onSwitchControlMode = { _, _, _, _ -> },
                        onLeaveRace = {}
                    )
                }
            }
            repeat(3) { composeTestRule.mainClock.advanceTimeByFrame() }
            composeTestRule.mainClock.advanceTimeBy(40L)

            // Tap low edge of steer_left_button -> full edge-to-edge touch responsiveness
            composeTestRule.onNodeWithTag("steer_left_button").performTouchInput {
                down(Offset(4f, height - 4f))
            }
            composeTestRule.mainClock.advanceTimeBy(40L)
            assertEquals(-1f, latestSteer, 0.01f)

            // Smoothly slide across the 10.dp gap into steer_right_button without lifting
            composeTestRule.onNodeWithTag("steer_left_button").performTouchInput {
                moveTo(Offset(width * 1.65f, height - 4f))
            }
            composeTestRule.mainClock.advanceTimeBy(40L)
            assertEquals(1f, latestSteer, 0.01f)

            composeTestRule.onNodeWithTag("steer_left_button").performTouchInput { up() }
            composeTestRule.mainClock.advanceTimeBy(40L)
            assertEquals(0f, latestSteer, 0.01f)

            // Edge-to-edge BRAKE activation near bottom corner
            composeTestRule.onNodeWithTag("brake_button").performTouchInput {
                down(Offset(width - 4f, height - 4f))
            }
            composeTestRule.mainClock.advanceTimeBy(40L)
            assertEquals(-1f, latestThrottle, 0.01f)

            composeTestRule.onNodeWithTag("brake_button").performTouchInput { up() }
            composeTestRule.mainClock.advanceTimeBy(40L)
            assertEquals(1f, latestThrottle, 0.01f)
        } finally {
            composeTestRule.mainClock.autoAdvance = true
        }
    }
}

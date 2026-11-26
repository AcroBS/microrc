package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.data.TrackCatalog
import com.example.drift.DriftTrackCatalog
import com.example.engine.RacePhysicsEngine
import com.example.model.ControlInput
import com.example.model.CustomTrackCodec
import com.example.model.CustomTrackData
import com.example.model.ExternalEventType
import com.example.model.LobbyPlayer
import com.example.model.RacePhase
import com.example.model.RaceRulesSettings
import com.example.model.TrackId
import com.example.model.TrackPoint
import com.example.network.RaceTelemetryCodec
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NightTrackAndEventsTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun customTrackCodec_preservesNightTrackFlagAndDecodesLegacyV1() {
        val sample = CustomTrackData(
            id = "night_circuit_1",
            name = "Midnight Ring",
            themeId = TrackId.NEON_ROOFTOP.name,
            roadHalfWidth = 112f,
            controlPoints = listOf(
                TrackPoint(400f, 350f, 112f),
                TrackPoint(1100f, 300f, 112f),
                TrackPoint(1700f, 450f, 112f),
                TrackPoint(1750f, 1100f, 112f),
                TrackPoint(1000f, 1200f, 112f),
                TrackPoint(400f, 950f, 112f)
            ),
            isNightTrack = true,
            randomConditionsEnabled = true
        )

        val encoded = CustomTrackCodec.encode(sample)
        val decoded = CustomTrackCodec.decode(
            id = sample.id,
            name = sample.name,
            themeId = sample.themeId,
            roadHalfWidth = sample.roadHalfWidth,
            creatorUid = "uid_1",
            creatorName = "Acro",
            raw = encoded
        )
        assertNotNull(decoded)
        assertTrue("Encoded/decoded night track must preserve isNightTrack=true", decoded!!.isNightTrack)
        assertTrue("Encoded/decoded track must preserve randomConditionsEnabled=true", decoded.randomConditionsEnabled)

        // Legacy 7-section payload (night flag only, no weather section) keeps night=true and defaults randomConditionsEnabled=false
        val legacy7Encoded = encoded.split('|').take(7).joinToString("|")
        val legacy7Decoded = CustomTrackCodec.decode(
            id = sample.id,
            name = sample.name,
            themeId = sample.themeId,
            roadHalfWidth = sample.roadHalfWidth,
            creatorUid = "uid_1",
            creatorName = "Acro",
            raw = legacy7Encoded
        )
        assertNotNull(legacy7Decoded)
        assertTrue("7-section payload must preserve isNightTrack=true", legacy7Decoded!!.isNightTrack)
        assertFalse("7-section payload without weather flag should default to randomConditionsEnabled=false", legacy7Decoded.randomConditionsEnabled)

        // Legacy 6-section V1 payload without night or weather sections should decode cleanly with both false
        val legacy6Encoded = encoded.split('|').take(6).joinToString("|")
        val legacy6Decoded = CustomTrackCodec.decode(
            id = sample.id,
            name = sample.name,
            themeId = sample.themeId,
            roadHalfWidth = sample.roadHalfWidth,
            creatorUid = "uid_1",
            creatorName = "Acro",
            raw = legacy6Encoded
        )
        assertNotNull(legacy6Decoded)
        assertFalse("Legacy 6-section payload should default to isNightTrack=false", legacy6Decoded!!.isNightTrack)
        assertFalse("Legacy 6-section payload should default to randomConditionsEnabled=false", legacy6Decoded.randomConditionsEnabled)
    }

    @Test
    fun proceduralTrackGenerators_produceBothNightAndDayTracks() {
        val circuitResults = (1L..40L).map { seed ->
            TrackCatalog.generateRandomTrack(seed = seed)
        }
        assertTrue(
            "Procedural circuit tracks should include random night tracks",
            circuitResults.any { it.isNightTrack }
        )
        assertTrue(
            "Procedural circuit tracks should include daytime tracks",
            circuitResults.any { !it.isNightTrack }
        )

        val nightCircuit = TrackCatalog.generateRandomTrack(seed = 99L, forceNight = true)
        assertTrue(nightCircuit.isNightTrack)
        assertTrue(TrackCatalog.buildCustomTrack(nightCircuit).isNightTrack)

        val driftResults = (1L..40L).map { seed ->
            DriftTrackCatalog.generateRandomDriftTrack(seed = seed)
        }
        assertTrue(
            "Procedural drift tracks should include random night tracks",
            driftResults.any { it.isNightTrack }
        )
        assertTrue(
            "Procedural drift tracks should include daytime tracks",
            driftResults.any { !it.isNightTrack }
        )
    }

    @Test
    fun racePhysicsEngine_dayNightEventTransitionsAndExcludedOnPermanentNightTracks() {
        val player = LobbyPlayer(id = "p1", name = "Acro", isHost = true)
        val dayTrack = TrackCatalog.getTrack("LIVING_ROOM_GP")
        val engineDay = RacePhysicsEngine(
            track = dayTrack,
            totalLaps = 3,
            players = listOf(player),
            rules = RaceRulesSettings(randomConditionsEnabled = true),
            random = Random(1234L)
        )

        // Advance past 3.2s countdown
        var snap = engineDay.step(0.05f)
        repeat(79) { snap = engineDay.step(0.05f) }
        assertEquals(RacePhase.RACING.name, snap.phase)

        engineDay.triggerExternalEvent(ExternalEventType.DAY_NIGHT, durationMs = 10_000L)
        val nightSnap = engineDay.step(0.05f)
        assertEquals(ExternalEventType.DAY_NIGHT.name, nightSnap.activeEvent)
        assertTrue(nightSnap.eventBanner.orEmpty().contains("NIGHTFALL", ignoreCase = true))

        // Step past duration so DAY_NIGHT ends with DAYBREAK banner
        var dayBreakSnap = nightSnap
        repeat(210) { dayBreakSnap = engineDay.step(0.05f) }
        assertEquals(ExternalEventType.NONE.name, dayBreakSnap.activeEvent)
        assertTrue(dayBreakSnap.eventBanner.orEmpty().contains("DAYBREAK", ignoreCase = true))

        // Verify braking sets isBraking = true on racer and RaceTelemetryCodec preserves it + DAY_NIGHT
        engineDay.submitInput("p1", ControlInput(throttle = -1f, steer = 0f))
        engineDay.triggerExternalEvent(ExternalEventType.DAY_NIGHT, durationMs = 9_000L)
        val brakingSnap = engineDay.step(0.05f)
        assertTrue("Braking input must set isBraking=true", brakingSnap.racers.first().isBraking)

        val encoded = RaceTelemetryCodec.encodeSnapshot(seq = 7L, snap = brakingSnap)
        val decoded = RaceTelemetryCodec.decodeSnapshot(
            text = encoded.toString(Charsets.UTF_8),
            roster = listOf(player),
            previousSnapshot = null
        )
        assertNotNull(decoded)
        assertEquals(ExternalEventType.DAY_NIGHT.name, decoded!!.snapshot.activeEvent)
        assertTrue("RaceTelemetryCodec must preserve racer isBraking", decoded.snapshot.racers.first().isBraking)

        // Verify random conditions on a permanent night track NEVER select DAY_NIGHT
        val customNight = TrackCatalog.generateRandomTrack(
            seed = 55L,
            forceNight = true,
            forceRandomConditions = true
        )
        val builtNight = TrackCatalog.buildCustomTrack(customNight)
        val engineNight = RacePhysicsEngine(
            track = builtNight,
            totalLaps = 3,
            players = listOf(player),
            rules = RaceRulesSettings(randomConditionsEnabled = true),
            random = Random(42L)
        )
        repeat(80) { engineNight.step(0.05f) }
        repeat(2000) {
            val s = engineNight.step(0.05f)
            assertFalse(
                "Permanent night track must not trigger DAY_NIGHT random event",
                s.activeEvent == ExternalEventType.DAY_NIGHT.name
            )
        }
    }

    @Test
    fun trackEditorScreen_nightTrackSwitchTogglesAndSavesWithNightBadge() {
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("open_track_editor_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("editor_new_track_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("editor_night_track_switch")
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsOff()
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("editor_night_track_switch")
            .assertIsOn()

        composeTestRule.onNodeWithTag("editor_random_conditions_switch")
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsOff()
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("editor_random_conditions_switch")
            .assertIsOn()

        composeTestRule.onNodeWithTag("editor_save_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("editor_saved_banner").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("editor_saved_tracks_row").performScrollTo()
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodes(hasText("NIGHT") and hasText("WEATHER"))
            .onFirst()
            .assertIsDisplayed()
    }

    @Test
    fun trackOwnedRideConditionsAndHostOverride_governExternalEventsAndPreserveCustomJumps() {
        val player = LobbyPlayer(id = "p1", name = "Acro", isHost = true)
        val baseCustom = CustomTrackData(
            id = "custom_weather_test",
            name = "Designer Ring",
            themeId = TrackId.LIVING_ROOM_GP.name,
            roadHalfWidth = 112f,
            controlPoints = listOf(
                TrackPoint(400f, 350f, 112f),
                TrackPoint(1100f, 300f, 112f),
                TrackPoint(1700f, 450f, 112f),
                TrackPoint(1750f, 1100f, 112f),
                TrackPoint(1000f, 1200f, 112f),
                TrackPoint(400f, 950f, 112f)
            ),
            ramps = listOf(com.example.model.TrackRamp(x = 1100f, y = 300f, angleRad = 0f)),
            randomConditionsEnabled = false
        )
        val dryTrack = TrackCatalog.buildCustomTrack(baseCustom)
        val wetTrack = TrackCatalog.buildCustomTrack(baseCustom.copy(randomConditionsEnabled = true))

        // 1. Custom jumps designed in Track Studio are preserved on built TrackDefinition
        assertEquals("Designer-placed jump ramp must remain intact", 1, dryTrack.ramps.size)
        assertFalse(dryTrack.randomConditionsEnabled)
        assertTrue(wetTrack.randomConditionsEnabled)

        // 2. Track default with randomConditionsEnabled=false never fires weather in Solo (override=null)
        val dryRules = RaceRulesSettings(randomConditionsEnabled = true, hostRandomConditionsOverride = null)
        assertFalse(dryRules.effectiveRandomConditions(dryTrack))
        val dryEngine = RacePhysicsEngine(
            track = dryTrack,
            totalLaps = 3,
            players = listOf(player),
            rules = dryRules,
            random = Random(7L)
        )
        repeat(80) { dryEngine.step(0.05f) }
        var sawEventOnDryTrack = false
        repeat(1500) {
            if (dryEngine.step(0.05f).activeEvent != ExternalEventType.NONE.name) {
                sawEventOnDryTrack = true
            }
        }
        assertFalse("Custom track with randomConditionsEnabled=false must stay clear", sawEventOnDryTrack)

        // 3. Track default with randomConditionsEnabled=true DOES trigger random weather
        assertTrue(dryRules.effectiveRandomConditions(wetTrack))
        val wetEngine = RacePhysicsEngine(
            track = wetTrack,
            totalLaps = 3,
            players = listOf(player),
            rules = dryRules,
            random = Random(7L)
        )
        repeat(80) { wetEngine.step(0.05f) }
        var sawEventOnWetTrack = false
        repeat(1500) {
            if (wetEngine.step(0.05f).activeEvent != ExternalEventType.NONE.name) {
                sawEventOnWetTrack = true
            }
        }
        assertTrue("Custom track with randomConditionsEnabled=true must trigger weather events", sawEventOnWetTrack)

        // 4. Multiplayer Host Force ON overrides even a clear custom track
        val hostForceOn = RaceRulesSettings(hostRandomConditionsOverride = true)
        assertTrue(hostForceOn.effectiveRandomConditions(dryTrack))
        val forceOnEngine = RacePhysicsEngine(
            track = dryTrack,
            totalLaps = 3,
            players = listOf(player),
            rules = hostForceOn,
            random = Random(7L)
        )
        repeat(80) { forceOnEngine.step(0.05f) }
        var sawEventWithHostForceOn = false
        repeat(1500) {
            if (forceOnEngine.step(0.05f).activeEvent != ExternalEventType.NONE.name) {
                sawEventWithHostForceOn = true
            }
        }
        assertTrue("Multiplayer host Force ON must trigger weather even on clear custom tracks", sawEventWithHostForceOn)

        // 5. Multiplayer Host Force OFF suppresses weather even on a weather-enabled track
        val hostForceOff = RaceRulesSettings(hostRandomConditionsOverride = false)
        assertFalse(hostForceOff.effectiveRandomConditions(wetTrack))
        val forceOffEngine = RacePhysicsEngine(
            track = wetTrack,
            totalLaps = 3,
            players = listOf(player),
            rules = hostForceOff,
            random = Random(7L)
        )
        repeat(80) { forceOffEngine.step(0.05f) }
        var sawEventWithHostForceOff = false
        repeat(1500) {
            if (forceOffEngine.step(0.05f).activeEvent != ExternalEventType.NONE.name) {
                sawEventWithHostForceOff = true
            }
        }
        assertFalse("Multiplayer host Force OFF must disable weather even on weather-enabled tracks", sawEventWithHostForceOff)
    }
}

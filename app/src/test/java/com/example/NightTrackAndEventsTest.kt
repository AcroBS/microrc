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
    fun customTrackCodec_preservesNightTrackFlagLateralOffsetsAndDecodesLegacyV1() {
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
            boostPads = listOf(
                com.example.model.BoostPad(x = 1100f, y = 350f, angleRad = 0f, lateralOffset = 0.45f)
            ),
            ramps = listOf(
                com.example.model.TrackRamp(x = 900f, y = 260f, angleRad = 0f, lateralOffset = -0.45f)
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
        assertFalse("Custom tracks must decode with randomConditionsEnabled=false (multiplayer host opt-in only)", decoded.randomConditionsEnabled)
        assertEquals(-0.45f, decoded.ramps.first().lateralOffset, 0.02f)
        assertEquals(0.45f, decoded.boostPads.first().lateralOffset, 0.02f)

        // Legacy 6-section V1 payload without night section or lateralOffset decodes cleanly with defaults
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
            "Procedural circuit tracks should include deterministic night tracks",
            circuitResults.any { it.isNightTrack }
        )
        assertTrue(
            "Procedural circuit tracks should include deterministic daytime tracks",
            circuitResults.any { !it.isNightTrack }
        )
        assertTrue(
            "Procedural circuit tracks must not enable track-owned random environmental effects",
            circuitResults.all { !it.randomConditionsEnabled }
        )

        val nightCircuit = TrackCatalog.generateRandomTrack(seed = 99L, forceNight = true)
        assertTrue(nightCircuit.isNightTrack)
        assertTrue(TrackCatalog.buildCustomTrack(nightCircuit).isNightTrack)

        val driftResults = (1L..40L).map { seed ->
            DriftTrackCatalog.generateRandomDriftTrack(seed = seed)
        }
        assertTrue(
            "Procedural drift tracks should include deterministic night tracks",
            driftResults.any { it.isNightTrack }
        )
        assertTrue(
            "Procedural drift tracks should include deterministic daytime tracks",
            driftResults.any { !it.isNightTrack }
        )
        assertTrue(
            "Procedural drift tracks must not enable track-owned random environmental effects",
            driftResults.all { !it.randomConditionsEnabled }
        )
    }

    @Test
    fun racePhysicsEngine_deterministicDayNightLightingNeverRandomlyTogglesAndPreservesBrakingTelemetry() {
        val player = LobbyPlayer(id = "p1", name = "Acro", isHost = true)
        val dayTrack = TrackCatalog.getTrack("LIVING_ROOM_GP")
        val hostEnvRules = RaceRulesSettings(
            randomConditionsEnabled = true,
            hostRandomConditionsOverride = true
        )
        val engineDay = RacePhysicsEngine(
            track = dayTrack,
            totalLaps = 3,
            players = listOf(player),
            rules = hostEnvRules,
            random = Random(1234L)
        )

        // Advance past 3.2s countdown
        var snap = engineDay.step(0.05f)
        repeat(79) { snap = engineDay.step(0.05f) }
        assertEquals(RacePhase.RACING.name, snap.phase)

        // Verify braking sets isBraking = true on racer and RaceTelemetryCodec preserves it
        engineDay.submitInput("p1", ControlInput(throttle = -1f, steer = 0f))
        engineDay.triggerExternalEvent(ExternalEventType.RAIN, durationMs = 9_000L)
        val brakingSnap = engineDay.step(0.05f)
        assertTrue("Braking input must set isBraking=true", brakingSnap.racers.first().isBraking)

        val encoded = RaceTelemetryCodec.encodeSnapshot(seq = 7L, snap = brakingSnap)
        val decoded = RaceTelemetryCodec.decodeSnapshot(
            text = encoded.toString(Charsets.UTF_8),
            roster = listOf(player),
            previousSnapshot = null
        )
        assertNotNull(decoded)
        assertEquals(ExternalEventType.RAIN.name, decoded!!.snapshot.activeEvent)
        assertTrue("RaceTelemetryCodec must preserve racer isBraking", decoded.snapshot.racers.first().isBraking)

        // Even with host environmental effects enabled on a Day track, random events NEVER toggle Day/Night
        val seenEventsOnDay = mutableSetOf<String>()
        repeat(2400) {
            val s = engineDay.step(0.05f)
            assertFalse(
                "Deterministic Day track must never randomly switch to DAY_NIGHT",
                s.activeEvent == ExternalEventType.DAY_NIGHT.name
            )
            if (s.activeEvent != ExternalEventType.NONE.name) {
                seenEventsOnDay.add(s.activeEvent)
            }
        }
        assertTrue("Host-enabled environmental effects should trigger weather hazards (seen=$seenEventsOnDay)", seenEventsOnDay.isNotEmpty())

        // Verify random conditions on a permanent night track also NEVER select DAY_NIGHT
        val customNight = TrackCatalog.generateRandomTrack(seed = 55L, forceNight = true)
        val builtNight = TrackCatalog.buildCustomTrack(customNight)
        val engineNight = RacePhysicsEngine(
            track = builtNight,
            totalLaps = 3,
            players = listOf(player),
            rules = hostEnvRules,
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
    fun trackEditorScreen_nightTrackSwitchTogglesWithoutRandomConditionsAndSavesWithNightBadge() {
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("open_track_editor_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("editor_new_track_button").performClick()
        composeTestRule.waitForIdle()

        // Random ride conditions switch must be removed from Track Studio
        composeTestRule.onNodeWithTag("editor_random_conditions_switch").assertDoesNotExist()

        composeTestRule.onNodeWithTag("editor_night_track_switch")
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsOff()
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("editor_night_track_switch")
            .assertIsOn()

        composeTestRule.onNodeWithTag("editor_save_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("editor_saved_banner").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("editor_saved_tracks_row").performScrollTo()
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodes(hasText("NIGHT"))
            .onFirst()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("WEATHER").assertDoesNotExist()
    }

    @Test
    fun multiplayerHostOnlyEnvironmentalEffects_governExternalEventsAndPreserveCustomJumps() {
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
        val builtTrack = TrackCatalog.buildCustomTrack(baseCustom)
        val legacyWetTrack = TrackCatalog.buildCustomTrack(baseCustom.copy(randomConditionsEnabled = true))

        // 1. Custom jumps designed in Track Studio are preserved; track-owned randomConditionsEnabled is always false
        assertEquals("Designer-placed jump ramp must remain intact", 1, builtTrack.ramps.size)
        assertFalse(builtTrack.randomConditionsEnabled)
        assertFalse(legacyWetTrack.randomConditionsEnabled)

        // 2. Solo / default rules (hostRandomConditionsOverride = null) NEVER trigger random weather
        val soloRules = RaceRulesSettings(randomConditionsEnabled = true, hostRandomConditionsOverride = null)
        assertFalse(soloRules.effectiveRandomConditions(builtTrack))
        assertFalse(soloRules.effectiveRandomConditions(legacyWetTrack))
        val soloEngine = RacePhysicsEngine(
            track = builtTrack,
            totalLaps = 3,
            players = listOf(player),
            rules = soloRules,
            random = Random(7L)
        )
        repeat(80) { soloEngine.step(0.05f) }
        var sawEventInSolo = false
        repeat(1500) {
            if (soloEngine.step(0.05f).activeEvent != ExternalEventType.NONE.name) {
                sawEventInSolo = true
            }
        }
        assertFalse("Solo / non-host races must never fire random environmental effects", sawEventInSolo)

        // 3. Multiplayer Host opt-in (hostRandomConditionsOverride = true) enables rain/tremor/wind/snow
        val hostEnvOn = RaceRulesSettings(hostRandomConditionsOverride = true, randomConditionsEnabled = true)
        assertTrue(hostEnvOn.effectiveRandomConditions(builtTrack))
        val hostOnEngine = RacePhysicsEngine(
            track = builtTrack,
            totalLaps = 3,
            players = listOf(player),
            rules = hostEnvOn,
            random = Random(7L)
        )
        repeat(80) { hostOnEngine.step(0.05f) }
        var sawEventWithHostOn = false
        repeat(1500) {
            val ev = hostOnEngine.step(0.05f).activeEvent
            assertFalse("Host environmental effects must never pick DAY_NIGHT", ev == ExternalEventType.DAY_NIGHT.name)
            if (ev != ExternalEventType.NONE.name) {
                sawEventWithHostOn = true
            }
        }
        assertTrue("Multiplayer host opt-in must trigger environmental hazards", sawEventWithHostOn)

        // 4. Multiplayer Host OFF suppresses environmental effects
        val hostEnvOff = RaceRulesSettings(hostRandomConditionsOverride = false, randomConditionsEnabled = false)
        assertFalse(hostEnvOff.effectiveRandomConditions(builtTrack))
        val hostOffEngine = RacePhysicsEngine(
            track = builtTrack,
            totalLaps = 3,
            players = listOf(player),
            rules = hostEnvOff,
            random = Random(7L)
        )
        repeat(80) { hostOffEngine.step(0.05f) }
        var sawEventWithHostOff = false
        repeat(1500) {
            if (hostOffEngine.step(0.05f).activeEvent != ExternalEventType.NONE.name) {
                sawEventWithHostOff = true
            }
        }
        assertFalse("Multiplayer host OFF must disable environmental effects", sawEventWithHostOff)

        // 5. Stunt tracks never allow environmental effects even if host flag is true
        val stuntTrack = TrackCatalog.getStuntTrack(com.example.model.StuntVariant.LONG_JUMP)
        assertFalse(hostEnvOn.effectiveRandomConditions(stuntTrack))
    }
}

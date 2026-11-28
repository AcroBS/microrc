package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import com.example.data.TrackCatalog
import com.example.engine.RacePhysicsEngine
import com.example.model.ActiveGhostCarState
import com.example.model.ControlInput
import com.example.model.CustomTrackCodec
import com.example.model.CustomTrackData
import com.example.model.ExternalEventType
import com.example.model.GameMode
import com.example.model.LobbyPlayer
import com.example.model.PlayerProfile
import com.example.model.RacePhase
import com.example.model.RaceRulesSettings
import com.example.model.RaceSnapshot
import com.example.model.RacerState
import com.example.model.CarSpeedPreset
import com.example.model.RcCarType
import com.example.model.StuntVariant
import com.example.model.TrackDefinition
import com.example.model.TrackId
import com.example.model.TrackPoint
import com.example.model.TrackPuddle
import com.example.model.TrackRamp
import com.example.model.WinCondition
import com.example.network.SessionRole
import com.example.network.WifiP2pRaceManager
import com.example.sensors.TiltSteeringController
import com.example.ui.screens.OnlineHubScreen
import com.example.ui.screens.RaceScreen
import com.example.ui.screens.ResultsScreen
import com.example.ui.screens.TrackEditorScreen
import com.example.ui.screens.isDrivingWrongWay
import com.example.ui.theme.MicroRCTheme
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class JumpGravelAndPolishTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun straightTestTrack(
        ramps: List<TrackRamp> = emptyList(),
        puddles: List<TrackPuddle> = emptyList()
    ): TrackDefinition {
        val hw = 114f
        val pts = mutableListOf<TrackPoint>()
        for (i in 0 until 80) {
            pts.add(TrackPoint(260f + i * 20.5f, 300f, hw))
        }
        for (i in 0 until 20) {
            val t = i / 20f
            pts.add(TrackPoint(1900f, 300f + t * 900f, hw))
        }
        for (i in 0 until 80) {
            pts.add(TrackPoint(1900f - i * 20.5f, 1200f, hw))
        }
        for (i in 0 until 20) {
            val t = i / 20f
            pts.add(TrackPoint(260f, 1200f - t * 900f, hw))
        }
        return TrackDefinition(
            id = TrackId.NEON_ROOFTOP,
            worldWidth = 2200f,
            worldHeight = 1500f,
            centerline = pts,
            boostPads = emptyList(),
            itemSpawns = emptyList(),
            props = emptyList(),
            ramps = ramps,
            puddles = puddles,
            randomConditionsEnabled = false
        )
    }

    @Test
    fun gravelPatchForRamp_pairsFullWidthBehindRampAndExcludesStunts() {
        val track = straightTestTrack(ramps = listOf(TrackRamp(x = 900f, y = 300f, angleRad = 0f)))
        val patch = TrackCatalog.gravelPatchForRamp(track, track.ramps.first())
        assertNotNull("Non-stunt ramp should have paired gravel patch", patch)
        patch!!
        assertTrue(
            "Gravel patch should be much wider than narrow jump ramp (${patch.halfWidth} > ${TrackCatalog.RAMP_HALF_WIDTH})",
            patch.halfWidth >= 100f && patch.halfWidth > TrackCatalog.RAMP_HALF_WIDTH * 2f
        )
        assertTrue(
            "Center behind ramp should be inside gravel",
            TrackCatalog.isPointOnGravelPatch(track, 900f + 100f, 300f)
        )
        assertTrue(
            "Left bypass lane behind ramp should be inside full-width gravel",
            TrackCatalog.isPointOnGravelPatch(track, 900f + 100f, 300f - 85f)
        )
        assertTrue(
            "Right bypass lane behind ramp should be inside full-width gravel",
            TrackCatalog.isPointOnGravelPatch(track, 900f + 100f, 300f + 85f)
        )
        assertFalse(
            "Approach before ramp should NOT be gravel",
            TrackCatalog.isPointOnGravelPatch(track, 900f - 50f, 300f)
        )
        assertFalse(
            "Tarmac after gravel patch should NOT be gravel",
            TrackCatalog.isPointOnGravelPatch(track, 900f + TrackCatalog.GRAVEL_START_OFFSET + TrackCatalog.GRAVEL_LENGTH + 20f, 300f)
        )

        val stuntTrack = TrackCatalog.getStuntTrack(StuntVariant.LONG_JUMP)
        val dummyRamp = TrackRamp(1200f, 700f, 0f)
        assertNull("Stunt track ramps must not get paired gravel patches", TrackCatalog.gravelPatchForRamp(stuntTrack, dummyRamp))
    }

    @Test
    fun highSpeedRampJump_hasSnappyAirtimeAndClearsGravelOntoTarmac() {
        val ramp = TrackRamp(x = 1050f, y = 300f, angleRad = 0f, impulse = 1.0f)
        val gravelEndX = ramp.x + TrackCatalog.GRAVEL_START_OFFSET + TrackCatalog.GRAVEL_LENGTH
        val puddleInGravel = TrackPuddle(x = ramp.x + 110f, y = 300f, radius = 38f)
        val track = straightTestTrack(ramps = listOf(ramp), puddles = listOf(puddleInGravel))
        val players = listOf(
            LobbyPlayer("p1", "Speedy", isHost = true, isBot = false, carType = RcCarType.VOLT_BUGGY.name)
        )
        val engine = RacePhysicsEngine(
            track = track,
            totalLaps = 3,
            players = players,
            rules = RaceRulesSettings(
                carSpeed = CarSpeedPreset.FAST.name,
                randomConditionsEnabled = false
            )
        )
        repeat(205) { engine.step(0.016f) }
        val rField = RacePhysicsEngine::class.java.getDeclaredField("racers").apply { isAccessible = true }
        val racersList = rField.get(engine) as List<*>
        val mRacer = racersList.first()!!
        fun setF(name: String, v: Float) {
            mRacer.javaClass.getDeclaredField(name).apply { isAccessible = true }.setFloat(mRacer, v)
        }
        setF("x", 360f)
        setF("y", 300f)
        setF("angleRad", 0f)
        setF("vx", RcCarType.VOLT_BUGGY.maxSpeedPx * 0.96f)
        setF("vy", 0f)

        var airborneTicks = 0
        var launched = false
        var landedX = 0f
        var landedSpeed = 0f
        var hydroplaned = false
        var maxZ = 0f

        repeat(160) {
            engine.submitInput("p1", ControlInput(throttle = 1f, steer = 0f))
            val snap = engine.step(0.016f)
            val r = snap.racers.first()
            if (r.jumpZ > 0.02f) {
                launched = true
                airborneTicks++
                if (r.jumpZ > maxZ) maxZ = r.jumpZ
            } else if (launched && landedX == 0f) {
                landedX = r.x
                landedSpeed = r.speed
            }
            if (abs(r.spinVisualOffset) > 0.04f) {
                hydroplaned = true
            }
        }

        val airtimeSec = airborneTicks * 0.016f
        assertTrue("High-speed car must launch off ramp", launched)
        assertTrue("Airtime should be snappy and reduced (${airtimeSec}s in 0.25..0.62s)", airtimeSec in 0.25f..0.62f)
        assertTrue("High-speed jump should reach clear airborne height (maxZ=$maxZ)", maxZ > 0.25f)
        assertTrue(
            "High-speed jump must clear the full gravel trap (landedX=$landedX >= gravelEndX=$gravelEndX)",
            landedX >= gravelEndX
        )
        assertTrue(
            "Landing back on tarmac should preserve high speed (landedSpeed=$landedSpeed)",
            landedSpeed >= RcCarType.VOLT_BUGGY.maxSpeedPx * 0.88f
        )
        assertFalse("Airborne jump over gravel should cleanly fly over puddle inside gravel zone", hydroplaned)
    }

    @Test
    fun slowRampEntry_jumpsShorterAndLandsInGravelWithSlowdown() {
        val ramp = TrackRamp(x = 900f, y = 300f, angleRad = 0f, impulse = 1.0f)
        val gravelStartX = ramp.x + TrackCatalog.GRAVEL_START_OFFSET
        val gravelEndX = gravelStartX + TrackCatalog.GRAVEL_LENGTH
        val track = straightTestTrack(ramps = listOf(ramp))
        val players = listOf(
            LobbyPlayer("p1", "SlowEntry", isHost = true, isBot = false, carType = RcCarType.VOLT_BUGGY.name)
        )
        val engine = RacePhysicsEngine(
            track = track,
            totalLaps = 3,
            players = players,
            rules = RaceRulesSettings(
                carSpeed = CarSpeedPreset.FAST.name,
                randomConditionsEnabled = false
            )
        )
        repeat(205) { engine.step(0.016f) }
        val rField = RacePhysicsEngine::class.java.getDeclaredField("racers").apply { isAccessible = true }
        val mRacer = (rField.get(engine) as List<*>).first()!!
        fun setF(name: String, v: Float) {
            mRacer.javaClass.getDeclaredField(name).apply { isAccessible = true }.setFloat(mRacer, v)
        }
        setF("x", ramp.x - 42f)
        setF("y", 300f)
        setF("angleRad", 0f)
        setF("vx", RcCarType.VOLT_BUGGY.maxSpeedPx * 0.38f)
        setF("vy", 0f)

        var airborneTicks = 0
        var launched = false
        var landedX = 0f
        repeat(80) {
            engine.submitInput("p1", ControlInput(throttle = 0f, steer = 0f))
            val snap = engine.step(0.016f)
            val r = snap.racers.first()
            if (r.jumpZ > 0.02f) {
                launched = true
                airborneTicks++
            } else if (launched && landedX == 0f) {
                landedX = r.x
            }
        }
        val slowAirtimeSec = airborneTicks * 0.016f
        assertTrue("Slow car hitting ramp should still make a short hop", launched)
        assertTrue("Slow entry airtime should be very short (${slowAirtimeSec}s < 0.30s)", slowAirtimeSec < 0.30f)
        assertTrue(
            "Slow jump must land short inside the gravel patch (landedX=$landedX in ${ramp.x}..$gravelEndX)",
            landedX in (ramp.x)..gravelEndX
        )
    }

    @Test
    fun bypassingRampLeftOrRight_hitsGravelAndSlowsToRoughlyThirtyFiveToFortyFivePercentWithOffRoadVariation() {
        val ramp = TrackRamp(x = 800f, y = 300f, angleRad = 0f, impulse = 1.0f)
        val track = straightTestTrack(ramps = listOf(ramp))

        fun measureBypassGravelSpeedRatio(carType: RcCarType): Float {
            val players = listOf(
                LobbyPlayer("p1", "Bypass", isHost = true, isBot = false, carType = carType.name)
            )
            val engine = RacePhysicsEngine(
                track = track,
                totalLaps = 3,
                players = players,
                rules = RaceRulesSettings(carSpeed = CarSpeedPreset.FAST.name, randomConditionsEnabled = false)
            )
            repeat(205) { engine.step(0.016f) }
            val rField = RacePhysicsEngine::class.java.getDeclaredField("racers").apply { isAccessible = true }
            val mRacer = (rField.get(engine) as List<*>).first()!!
            fun setF(name: String, v: Float) {
                mRacer.javaClass.getDeclaredField(name).apply { isAccessible = true }.setFloat(mRacer, v)
            }
            setF("x", ramp.x - 40f)
            setF("y", 300f + 75f)
            setF("angleRad", 0f)
            setF("vx", carType.maxSpeedPx)
            setF("vy", 0f)

            var minSpeedInGravel = Float.MAX_VALUE
            var everAirborne = false
            repeat(55) {
                engine.submitInput("p1", ControlInput(throttle = 1f, steer = 0f))
                val snap = engine.step(0.016f)
                val r = snap.racers.first()
                if (r.jumpZ > 0.05f) everAirborne = true
                if (TrackCatalog.isPointOnGravelPatch(track, r.x, r.y)) {
                    minSpeedInGravel = minOf(minSpeedInGravel, r.speed)
                }
            }
            assertFalse("Bypassing ramp laterally by 75px must not launch airborne", everAirborne)
            return minSpeedInGravel / carType.maxSpeedPx
        }

        val apexRatio = measureBypassGravelSpeedRatio(RcCarType.APEX_FORMULA)
        val tundraRatio = measureBypassGravelSpeedRatio(RcCarType.TUNDRA_MONSTER)

        assertTrue("Apex Formula in gravel should cap near ~35-41% (ratio=$apexRatio)", apexRatio in 0.34f..0.42f)
        assertTrue("Tundra Monster in gravel should cap near ~40-46% (ratio=$tundraRatio)", tundraRatio in 0.39f..0.46f)
        assertTrue(
            "Higher Off-Road Grip vehicle (Tundra=$tundraRatio) should retain slightly more speed in gravel than Apex ($apexRatio)",
            tundraRatio > apexRatio
        )
    }

    @Test
    fun splineAlignedGravelPatch_curvesWithTurnAndNeverOverlapsTrackCurb() {
        val baseTrack = TrackCatalog.getTrack(TrackId.WAREHOUSE_DRIFT)
        // Pick a sharp corner point along Warehouse Drift centerline and place a ramp entering the turn
        val turnIdx = (baseTrack.centerline.size * 0.22f).toInt()
        val ramp = TrackCatalog.rampAt(baseTrack.centerline, turnIdx, lateralOffset = -0.45f)
        val curvedTrack = baseTrack.copy(ramps = listOf(ramp))

        val patch = TrackCatalog.gravelPatchForRamp(curvedTrack, ramp)
        assertNotNull("Curved turn ramp must produce a spline-aligned gravel patch", patch)
        patch!!
        assertTrue("Circuit gravel patch should be a spline ribbon", patch.isSplineRibbon)
        assertTrue("Ribbon should sample multiple sub-steps along the turn", patch.leftEdge.size >= 10)
        assertEquals(patch.leftEdge.size, patch.rightEdge.size)

        // Every vertex of the gravel ribbon must remain strictly inside halfWidth - GRAVEL_EDGE_INSET (zero curb overlap)
        (patch.leftEdge + patch.rightEdge).forEach { (vx, vy) ->
            val proj = TrackCatalog.projectOntoCenterline(curvedTrack.centerline, vx, vy)
            val maxAllowed = proj.halfWidth - TrackCatalog.GRAVEL_EDGE_INSET + 2.0f
            assertTrue(
                "Gravel vertex ($vx,$vy) lateral=${abs(proj.lateralOffsetPx)} must stay within curb inset $maxAllowed",
                abs(proj.lateralOffsetPx) <= maxAllowed
            )
        }

        // Midpoint along the curved gravel ribbon must be detected as inside gravel, while just outside the curb must not
        val midSample = TrackCatalog.sampleSplineAtArc(curvedTrack.centerline, patch.startArcS + patch.length * 0.5f)
        assertTrue(
            "Center of curved gravel ribbon must trigger gravel hit detection",
            TrackCatalog.isPointOnGravelPatch(curvedTrack, midSample.x, midSample.y)
        )
        val insideRight = midSample.halfWidth * 0.72f
        assertTrue(
            "Opposite lane on curved gravel ribbon must trigger gravel hit detection even for Left-lane ramp",
            TrackCatalog.isPointOnGravelPatch(
                curvedTrack,
                midSample.x + midSample.normalX * insideRight,
                midSample.y + midSample.normalY * insideRight
            )
        )
        val outsideCurb = midSample.halfWidth + 8f
        assertFalse(
            "Point outside outer track curb next to curved gravel must NOT trigger gravel hit detection",
            TrackCatalog.isPointOnGravelPatch(
                curvedTrack,
                midSample.x + midSample.normalX * outsideCurb,
                midSample.y + midSample.normalY * outsideCurb
            )
        )
        assertFalse(
            "Point outside inner track curb next to curved gravel must NOT trigger gravel hit detection",
            TrackCatalog.isPointOnGravelPatch(
                curvedTrack,
                midSample.x - midSample.normalX * outsideCurb,
                midSample.y - midSample.normalY * outsideCurb
            )
        )
    }

    @Test
    fun offCenterLateralPlacement_variesLanesInGenerators_andWorksInTrackStudio() {
        val generatedRamps = (1L..12L).flatMap { TrackCatalog.generateRandomTrack(seed = it).ramps }
        val generatedBoosts = (1L..12L).flatMap { TrackCatalog.generateRandomTrack(seed = it).boostPads }
        assertTrue("Procedural ramps should include Left lane (-0.45)", generatedRamps.any { it.lateralOffset < -0.25f })
        assertTrue("Procedural ramps should include Center lane (0.0)", generatedRamps.any { abs(it.lateralOffset) < 0.1f })
        assertTrue("Procedural ramps should include Right lane (+0.45)", generatedRamps.any { it.lateralOffset > 0.25f })
        assertTrue("Procedural boost pads should vary lanes across Left/Center/Right", generatedBoosts.any { abs(it.lateralOffset) > 0.25f })

        val builtWithJumps = TrackCatalog.withRandomJumps(
            track = TrackCatalog.getTrack(TrackId.LIVING_ROOM_GP),
            enabled = true,
            seed = 42L
        )
        assertTrue("withRandomJumps should place off-center ramps", builtWithJumps.ramps.any { abs(it.lateralOffset) > 0.25f })

        // Verify Track Studio quick Lane selector (Left / Center / Right) updates selected ramp's lateralOffset and saves it
        val oval = TrackCatalog.defaultEditorOval(TrackId.NEON_ROOFTOP)
        val ovalBuilt = TrackCatalog.buildCustomTrack(oval)
        val initialRamp = TrackCatalog.rampAt(ovalBuilt.centerline, 10, lateralOffset = 0f)
        val draftWithRamp = oval.copy(name = "Lane Studio", ramps = listOf(initialRamp))
        var savedTrack: CustomTrackData? = null

        composeRule.setContent {
            MicroRCTheme {
                TrackEditorScreen(
                    initialTrack = draftWithRamp,
                    savedTracks = emptyList(),
                    canApplyToLobby = false,
                    onSaveTrack = { track, _ -> savedTrack = track },
                    onDeleteTrack = {},
                    onBack = {}
                )
            }
        }

        // Selecting Toggle Jump auto-selects the existing ramp and reveals the Left / Center / Right lane bar
        composeRule.onNodeWithTag("editor_tool_TOGGLE_RAMP").performClick()
        composeRule.onNodeWithTag("editor_lane_selector").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("editor_lane_left").performScrollTo().assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("editor_save_button").performClick()

        assertNotNull(savedTrack)
        assertEquals(1, savedTrack!!.ramps.size)
        assertTrue(
            "Saved ramp should be shifted to Left lane (lateralOffset=${savedTrack!!.ramps.first().lateralOffset})",
            savedTrack!!.ramps.first().lateralOffset < -0.35f
        )

        // Switching to Right lane moves it to +0.45f
        composeRule.onNodeWithTag("editor_lane_right").performScrollTo().performClick()
        composeRule.onNodeWithTag("editor_save_button").performClick()
        assertTrue(
            "Saved ramp should be shifted to Right lane (lateralOffset=${savedTrack!!.ramps.first().lateralOffset})",
            savedTrack!!.ramps.first().lateralOffset > 0.35f
        )
    }

    @Test
    fun onlineHubScreen_environmentalEffectsToggleDefaultsOffAndPassesHostOptIn() {
        var createdRules: RaceRulesSettings? = null
        composeRule.setContent {
            MicroRCTheme {
                OnlineHubScreen(
                    profile = PlayerProfile(playerId = "p1", name = "Acro"),
                    accountLabel = "Acro",
                    initialRules = RaceRulesSettings(),
                    openRooms = emptyList(),
                    isBusy = false,
                    errorBanner = null,
                    onDismissError = {},
                    onCreateRoom = { _, _, _, _, rules -> createdRules = rules },
                    onJoinRoom = { _, _ -> },
                    onJoinRoomByCode = { _, _, _ -> },
                    onOpenLeaderboards = {},
                    onSignOut = {},
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithTag("online_environmental_effects_switch")
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsOff()
            .performClick()
        composeRule.onNodeWithTag("online_environmental_effects_switch").assertIsOn()

        composeRule.onNodeWithTag("create_online_room_button").performScrollTo().performClick()
        assertNotNull("Create room should emit host rules", createdRules)
        assertEquals(true, createdRules?.hostRandomConditionsOverride)
        assertEquals(true, createdRules?.randomConditionsEnabled)
    }

    @Test
    fun customTrackCodec_preservesPuddlesAndPermanentPuddlesTriggerHydroplaneOnClearDay() {
        val base = TrackCatalog.defaultEditorOval(TrackId.LIVING_ROOM_GP).copy(
            puddles = listOf(TrackPuddle(x = 760f, y = 310f, radius = 38f))
        )
        val encoded = CustomTrackCodec.encode(base)
        val decoded = CustomTrackCodec.decode(
            id = base.id,
            name = base.name,
            themeId = base.themeId,
            roadHalfWidth = base.roadHalfWidth,
            raw = encoded
        )
        assertNotNull(decoded)
        assertEquals(1, decoded!!.puddles.size)
        assertEquals(760f, decoded.puddles.first().x, 0.5f)
        assertEquals(38f, decoded.puddles.first().radius, 0.5f)

        val legacy7 = encoded.split('|').take(8).joinToString("|")
        val decodedLegacy = CustomTrackCodec.decode(
            id = base.id,
            name = base.name,
            themeId = base.themeId,
            roadHalfWidth = base.roadHalfWidth,
            raw = legacy7
        )
        assertNotNull(decodedLegacy)
        assertTrue(decodedLegacy!!.puddles.isEmpty())

        val track = straightTestTrack(puddles = listOf(TrackPuddle(x = 700f, y = 300f, radius = 40f)))
        val players = listOf(LobbyPlayer("p1", "Hydro", isHost = true, isBot = false, carType = RcCarType.VOLT_BUGGY.name))
        val engine = RacePhysicsEngine(
            track = track,
            totalLaps = 3,
            players = players,
            rules = RaceRulesSettings(randomConditionsEnabled = false)
        )
        repeat(205) { engine.step(0.016f) }
        val rField = RacePhysicsEngine::class.java.getDeclaredField("racers").apply { isAccessible = true }
        val mRacer = (rField.get(engine) as List<*>).first()!!
        mRacer.javaClass.getDeclaredField("x").apply { isAccessible = true }.setFloat(mRacer, 620f)
        mRacer.javaClass.getDeclaredField("y").apply { isAccessible = true }.setFloat(mRacer, 300f)
        mRacer.javaClass.getDeclaredField("angleRad").apply { isAccessible = true }.setFloat(mRacer, 0f)
        mRacer.javaClass.getDeclaredField("vx").apply { isAccessible = true }.setFloat(mRacer, 360f)
        mRacer.javaClass.getDeclaredField("vy").apply { isAccessible = true }.setFloat(mRacer, 0f)

        var hydroplaned = false
        repeat(35) {
            engine.submitInput("p1", ControlInput(throttle = 1f, steer = 0f))
            val snap = engine.step(0.016f)
            assertEquals(ExternalEventType.NONE.name, snap.activeEvent)
            if (abs(snap.racers.first().spinVisualOffset) > 0.04f) {
                hydroplaned = true
            }
        }
        assertTrue("Custom track puddle should cause hydroplane spin even without rain event", hydroplaned)
    }

    @Test
    fun raceHud_stacksLapDeltaAndGhostWithoutOverlap_showsHighContrastStatusAndSpacedControls() {
        val track = straightTestTrack()
        val wrongWayRacer = RacerState(
            playerId = "p1",
            name = "Acro",
            carType = RcCarType.APEX_FORMULA.name,
            primaryColorArgb = 0xFF00E5FF,
            accentColorArgb = 0xFFFFFFFF,
            x = 700f,
            y = 300f,
            vx = -220f,
            vy = 0f,
            angleRad = Math.PI.toFloat(),
            speed = 220f,
            lap = 1,
            checkpointIndex = 40,
            sectorMask = 1,
            currentLapStartMs = 0L,
            shieldMs = 2000L,
            overdriveMs = 1500L
        )
        assertTrue(
            "Driving backward on circuit should be detected as WRONG WAY",
            isDrivingWrongWay(
                track = track,
                racer = wrongWayRacer,
                isRacingPhase = true,
                isArenaMode = false,
                isStuntsMode = false
            )
        )
        assertFalse(
            "Driving forward should NOT be detected as WRONG WAY",
            isDrivingWrongWay(
                track = track,
                racer = wrongWayRacer.copy(vx = 220f, angleRad = 0f),
                isRacingPhase = true,
                isArenaMode = false,
                isStuntsMode = false
            )
        )

        val snapshot = RaceSnapshot(
            phase = RacePhase.RACING.name,
            trackId = TrackId.NEON_ROOFTOP.name,
            totalLaps = 3,
            raceTimeMs = 9_500L,
            racers = listOf(wrongWayRacer)
        )
        val ghostCar = ActiveGhostCarState(
            driverName = "Acro (PB)",
            carType = RcCarType.APEX_FORMULA.name,
            primaryColorArgb = 0xFF00E5FF,
            accentColorArgb = 0xFFFFFFFF,
            x = 740f,
            y = 300f,
            angleRad = 0f,
            jumpZ = 0f,
            splitDeltaMs = -320L,
            finishTimeMs = 25_000L,
            bestLapMs = 24_000L
        )
        var returnedToStudio = false
        val context = RuntimeEnvironment.getApplication()
        val tiltController = TiltSteeringController(context)

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MicroRCTheme {
                RaceScreen(
                    profile = PlayerProfile(playerId = "p1", name = "Acro", showLiveLapDelta = true),
                    snapshot = snapshot,
                    activeTrack = track,
                    ghostCar = ghostCar,
                    overviewCamera = false,
                    tiltController = tiltController,
                    personalBestLapMs = 24_000L,
                    isStudioTestDrive = true,
                    onReturnToStudio = { returnedToStudio = true },
                    onToggleOverviewCamera = {},
                    onUpdateControlInput = { _, _ -> },
                    onTriggerPowerUp = {},
                    onSwitchControlMode = { _, _, _, _ -> },
                    onLeaveRace = {}
                )
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(100L)

        composeRule.onNodeWithTag("live_lap_delta_badge").assertIsDisplayed()
        composeRule.onNodeWithTag("ghost_delta_badge").assertIsDisplayed()
        val lapBounds = composeRule.onNodeWithTag("live_lap_delta_badge").getUnclippedBoundsInRoot()
        val ghostBounds = composeRule.onNodeWithTag("ghost_delta_badge").getUnclippedBoundsInRoot()
        assertTrue(
            "GHOST pill (top=${ghostBounds.top}) must be stacked cleanly below LAP DELTA (bottom=${lapBounds.bottom})",
            ghostBounds.top >= lapBounds.bottom
        )

        composeRule.onNodeWithTag("race_status_callout_pill").assertIsDisplayed()
        composeRule.onNodeWithText("WRONG WAY").assertIsDisplayed()
        composeRule.onNodeWithText("SHIELDED").assertIsDisplayed()
        composeRule.onNodeWithText("OVERDRIVE").assertIsDisplayed()

        val powerBounds = composeRule.onNodeWithTag("use_powerup_button").getUnclippedBoundsInRoot()
        val brakeBounds = composeRule.onNodeWithTag("brake_button").getUnclippedBoundsInRoot()
        val gap = brakeBounds.top - powerBounds.bottom
        assertTrue("Spacing between Power-Up slot and Brake pedal should be ~12.dp (actual=$gap)", gap in 11.dp..13.dp)

        composeRule.onNodeWithTag("return_to_studio_button").assertIsDisplayed().performClick()
        composeRule.mainClock.advanceTimeByFrame()
        assertTrue("Return to Studio pill should invoke callback", returnedToStudio)
    }

    @Test
    fun trackStudio_showsTogglePuddleToolAndTestDriveAction() {
        var testedDraft: CustomTrackData? = null
        val initial = TrackCatalog.defaultEditorOval(TrackId.NEON_ROOFTOP).copy(name = "Studio Proto")

        composeRule.setContent {
            MicroRCTheme {
                TrackEditorScreen(
                    initialTrack = initial,
                    savedTracks = emptyList(),
                    canApplyToLobby = false,
                    onTestDrive = { testedDraft = it },
                    onSaveTrack = { _, _ -> },
                    onDeleteTrack = {},
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithTag("editor_tools_row").performScrollToNode(hasTestTag("editor_tool_TOGGLE_PUDDLE"))
        composeRule.onNodeWithTag("editor_tool_TOGGLE_PUDDLE").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Tap on track to add or remove a Rain Puddle").assertIsDisplayed()

        composeRule.onNodeWithTag("editor_test_drive_button").assertIsDisplayed().performClick()
        assertNotNull("Test Drive button should launch draft track", testedDraft)
        assertEquals("Studio Proto", testedDraft?.name)
    }

    @Test
    fun checkeredFlag_fixesSingularLapText_raceAgainSubtitle_andTracksAndGhostsLabel() {
        val customTrack = TrackCatalog.defaultEditorOval(TrackId.LIVING_ROOM_GP).copy(name = "Backyard Test")
        val rematchOptions = WifiP2pRaceManager.buildRematchVoteOptions(
            gameMode = GameMode.RACE,
            currentTrackId = TrackId.LIVING_ROOM_GP,
            currentArenaShape = com.example.model.ArenaShape.SQUARE,
            activeCustomTrack = customTrack
        )
        assertEquals("Custom Circuit • Race Again", rematchOptions.first().subtitle)
        assertFalse(
            "Rematch subtitle must not say Replay",
            rematchOptions.any { it.subtitle.contains("Replay", ignoreCase = true) }
        )

        val singleLapResult = RaceSnapshot(
            phase = RacePhase.FINISHED.name,
            trackId = TrackId.LIVING_ROOM_GP.name,
            gameMode = GameMode.RACE.name,
            winCondition = WinCondition.FIRST_TO_FINISH.name,
            totalLaps = 1,
            racers = listOf(
                RacerState(
                    playerId = "p1",
                    name = "Acro",
                    carType = RcCarType.APEX_FORMULA.name,
                    primaryColorArgb = 0xFF00E5FF,
                    accentColorArgb = 0xFFFFFFFF,
                    x = 0f,
                    y = 0f,
                    angleRad = 0f,
                    rank = 1,
                    finished = true,
                    finishTimeMs = 19_450L,
                    bestLapMs = 19_450L
                )
            )
        )

        var returnedToStudio = false
        composeRule.setContent {
            MicroRCTheme {
                ResultsScreen(
                    role = SessionRole.Solo,
                    localPlayerId = "p1",
                    results = singleLapResult,
                    lobby = null,
                    isStudioTestDrive = true,
                    onReturnToStudio = { returnedToStudio = true },
                    onOpenLeaderboards = {},
                    onReturnToLobby = {},
                    onExitHome = {}
                )
            }
        }

        composeRule.onNodeWithText("Living Room GP • 1 Lap").assertIsDisplayed()
        composeRule.onNodeWithText("Tracks & Ghosts").assertIsDisplayed()
        composeRule.onNodeWithTag("results_return_to_studio_button").assertIsDisplayed().performClick()
        assertTrue(returnedToStudio)
    }
}

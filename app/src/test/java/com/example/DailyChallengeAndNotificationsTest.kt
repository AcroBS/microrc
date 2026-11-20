package com.example

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.DailyChallengeCatalog
import com.example.data.TrackCatalog
import com.example.engine.RacePhysicsEngine
import com.example.model.CarSpeedPreset
import com.example.model.ControlInput
import com.example.model.GameMode
import com.example.model.LeaderboardNotificationDoc
import com.example.model.LobbyPlayer
import com.example.model.PowerUpType
import com.example.model.RacePhase
import com.example.model.RaceRulesSettings
import com.example.model.RcCarType
import com.example.util.InviteLinkHelper
import com.example.util.SessionNotificationHelper
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DailyChallengeAndNotificationsTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun dailyChallengeMap_isDeterministicAcrossRetriesAndDatesDiffer() {
        val dateA = "20261120"
        val dateB = "20261121"

        val firstRun = DailyChallengeCatalog.dailyCustomTrack(dateA)
        val retryRun = DailyChallengeCatalog.dailyCustomTrack(dateA)
        val nextDayRun = DailyChallengeCatalog.dailyCustomTrack(dateB)

        // Exact identical track layout, boost pads, ramps/jumps & props on retry, with 0 track item spawns
        assertEquals(firstRun.id, retryRun.id)
        assertEquals(firstRun.name, retryRun.name)
        assertEquals(firstRun.themeId, retryRun.themeId)
        assertEquals(firstRun.controlPoints, retryRun.controlPoints)
        assertEquals(firstRun.boostPads, retryRun.boostPads)
        assertEquals(firstRun.ramps, retryRun.ramps)
        assertTrue(firstRun.itemSpawns.isEmpty())
        assertTrue(retryRun.itemSpawns.isEmpty())
        assertEquals(firstRun.props, retryRun.props)

        // Next day generates a distinct track seed & board key
        assertNotEquals(firstRun.id, nextDayRun.id)
        assertNotEquals(
            DailyChallengeCatalog.seedForDate(dateA),
            DailyChallengeCatalog.seedForDate(dateB)
        )
        assertEquals("RACE_daily_20261120_3L", DailyChallengeCatalog.boardKeyForDate(dateA))

        // Fair rules lock random conditions off while enabling ramps on fixed layout
        val locked = DailyChallengeCatalog.lockedDailyRules(
            RaceRulesSettings(
                totalLaps = 1,
                carSpeed = CarSpeedPreset.LOW.name,
                hardTrackBoundary = false,
                randomConditionsEnabled = true,
                randomJumpsEnabled = false
            )
        )
        assertEquals(DailyChallengeCatalog.DAILY_LAPS, locked.totalLaps)
        assertEquals(CarSpeedPreset.FAST.name, locked.carSpeed)
        assertTrue(locked.hardTrackBoundary)
        assertTrue(locked.randomJumpsEnabled)
        assertFalse(locked.randomConditionsEnabled)
    }

    @Test
    fun physicsEngine_dailyChallengeRules_noMinesNoTrackPowerupsFixedPortalsAndOneNitroPerLap() {
        val dateKey = "20261120"
        val dailyCustom = DailyChallengeCatalog.dailyCustomTrack(dateKey)
        val trackDef = TrackCatalog.buildCustomTrack(dailyCustom)
        val lockedRules = DailyChallengeCatalog.lockedDailyRules(RaceRulesSettings())
        val playersA = listOf(
            LobbyPlayer(
                id = "solo_p1",
                name = "DailyDriverA",
                isHost = true,
                isReady = true,
                carType = RcCarType.VOLT_BUGGY.name
            )
        )
        val playersB = listOf(
            LobbyPlayer(
                id = "solo_p2",
                name = "DailyDriverB",
                isHost = true,
                isReady = true,
                carType = RcCarType.entries.last().name
            )
        )
        val seed = DailyChallengeCatalog.seedForDate(dateKey)

        // Intentionally pass differently seeded Random instances to confirm portals do NOT vary per run
        val engineA = RacePhysicsEngine(
            track = trackDef,
            totalLaps = DailyChallengeCatalog.DAILY_LAPS,
            players = playersA,
            rules = lockedRules,
            random = Random(seed),
            isDailyChallenge = true
        )
        val engineB = RacePhysicsEngine(
            track = trackDef,
            totalLaps = DailyChallengeCatalog.DAILY_LAPS,
            players = playersB,
            rules = lockedRules,
            random = Random(seed + 99999L),
            isDailyChallenge = true
        )

        val snapA = engineA.step(0.016f)
        val snapB = engineB.step(0.016f)

        // 1. No track powerups or field pickups
        assertTrue(snapA.itemBoxes.isEmpty())
        assertTrue(snapA.fieldPickups.isEmpty())
        assertTrue(snapB.itemBoxes.isEmpty())

        // 2. Fixed identical portals for all players across runs (non-null and permanent)
        val portalsA = snapA.portalPair
        val portalsB = snapB.portalPair
        assertNotNull(portalsA)
        assertNotNull(portalsB)
        assertEquals(portalsA!!.entryX, portalsB!!.entryX, 0.001f)
        assertEquals(portalsA.entryY, portalsB.entryY, 0.001f)
        assertEquals(portalsA.exitX, portalsB.exitX, 0.001f)
        assertEquals(portalsA.exitY, portalsB.exitY, 0.001f)
        assertEquals(portalsA.exitAngleRad, portalsB.exitAngleRad, 0.001f)
        assertEquals(portalsA.entryColorIsRed, portalsB.entryColorIsRed)

        // 3. No mines allowed, and racer starts Lap 1 holding 1 manual Nitro
        val racerStart = snapA.racers.single()
        assertEquals(0, racerStart.minesAvailable)
        assertEquals(PowerUpType.TURBO_NITRO.name, racerStart.heldPowerUp)
        engineA.grantMineForTest("solo_p1", 1)
        assertEquals(0, engineA.step(0.016f).racers.single().minesAvailable)

        // Advance past 3.2s countdown into active RACING phase
        repeat(90) {
            engineA.step(0.04f)
            engineB.step(0.04f)
        }
        val racingSnap = engineA.step(0.04f)
        assertEquals(RacePhase.RACING.name, racingSnap.phase)
        // Portals remain identically fixed even well into RACING phase
        assertEquals(portalsA.entryX, racingSnap.portalPair!!.entryX, 0.001f)
        assertEquals(portalsA.exitX, racingSnap.portalPair!!.exitX, 0.001f)

        // 4. Consume Lap 1 Nitro manually + confirm dropMine does nothing
        engineA.submitInput("solo_p1", ControlInput(throttle = 1f, usePowerUp = true, dropMine = true))
        val afterUse = engineA.step(0.02f)
        val rAfterUse = afterUse.racers.single()
        assertNull(rAfterUse.heldPowerUp)
        assertTrue(rAfterUse.boostMs > 0L)
        assertEquals(0, rAfterUse.minesAvailable)
        assertTrue(afterUse.hazards.isEmpty())

        // 5. Complete Lap 1 -> entering Lap 2 grants 1 fresh Nitro and still 0 mines
        engineA.simulateCompleteLapForTest("solo_p1")
        val lap2Snap = engineA.step(0.02f)
        val rLap2 = lap2Snap.racers.single()
        assertEquals(2, rLap2.lap)
        assertEquals(PowerUpType.TURBO_NITRO.name, rLap2.heldPowerUp)
        assertEquals(0, rLap2.minesAvailable)

        // 6. Entering Lap 3 still holds max 1 Nitro and 0 mines
        engineA.simulateCompleteLapForTest("solo_p1")
        val lap3Snap = engineA.step(0.02f)
        val rLap3 = lap3Snap.racers.single()
        assertEquals(3, rLap3.lap)
        assertEquals(PowerUpType.TURBO_NITRO.name, rLap3.heldPowerUp)
        assertEquals(0, rLap3.minesAvailable)
    }

    @Test
    fun systemNotifications_friendLeaderboardNotification_postsAndroidSystemNotificationOnce() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNm = Shadows.shadowOf(nm)

        val uniqueId = "notif_test_${System.nanoTime()}"
        val notifDoc = LeaderboardNotificationDoc(
            id = uniqueId,
            friendshipId = "f_1",
            senderUid = "friend_uid",
            senderName = "TurboAlex",
            recipientUid = "me_uid",
            gameMode = GameMode.RACE.name,
            trackKey = "GP_APEX",
            trackName = "Apex Circuit",
            boardKey = "RACE_GP_APEX_3L",
            totalLaps = 3L,
            bestRaceTimeMs = 41_250L,
            bestScore = 0L,
            dailyChallenge = false,
            status = "UNREAD"
        )

        val firstPosted = SessionNotificationHelper.showFriendLeaderboardRecordNotification(app, notifDoc)
        assertTrue(firstPosted)

        val postedList = shadowNm.allNotifications
        val matched = postedList.find { n ->
            n.extras.getString(Notification.EXTRA_TITLE)?.contains("TurboAlex") == true
        }
        assertNotNull(matched)
        val bodyText = matched!!.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        assertTrue(bodyText.contains("Apex Circuit"))
        assertTrue(bodyText.contains("41.25s"))

        // Calling again with the same notification ID deduplicates across foreground/background syncs
        val secondPosted = SessionNotificationHelper.showFriendLeaderboardRecordNotification(app, notifDoc)
        assertFalse(secondPosted)
    }

    @Test
    fun deepLinks_parseDailyChallengeAndFriendLeaderboardRoutes() {
        val dailyIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("microrc://daily?date=20261120")
        )
        val parsedDaily = InviteLinkHelper.parseDailyFromIntent(dailyIntent)
        assertNotNull(parsedDaily)
        assertEquals("20261120", parsedDaily?.dateKey)

        val lbIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("microrc://leaderboard?boardKey=RACE_daily_20261120_3L&gameMode=RACE&trackKey=daily_20261120")
        )
        val parsedLb = InviteLinkHelper.parseLeaderboardFromIntent(lbIntent)
        assertNotNull(parsedLb)
        assertEquals("RACE_daily_20261120_3L", parsedLb?.boardKey)
        assertEquals("RACE", parsedLb?.gameMode)
        assertEquals(true, parsedLb?.isDaily)

        assertEquals("41.25s", SessionNotificationHelper.formatNotifTimeMs(41_250L))
        assertEquals("1:15.09", SessionNotificationHelper.formatNotifTimeMs(75_090L))
    }

    @Test
    fun dailyChallengeUi_opensFairTrackLaunchesRaceAndPauseMenuSupportsRestart() {
        composeTestRule.waitForIdle()

        // 1. Open Daily Challenge from Home Screen
        composeTestRule.onNodeWithTag("daily_challenge_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("daily_challenge_button").performClick()
        composeTestRule.waitForIdle()

        // 2. Verify Daily Challenge tab, deterministic preview card & start CTA
        composeTestRule.onNodeWithTag("leaderboard_mode_DAILY").assertIsDisplayed()
        composeTestRule.onNodeWithTag("daily_challenge_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("daily_start_button").performScrollTo().assertIsDisplayed()

        // 3. Launch Daily Challenge Run directly & verify HUD (1 Nitro ready, No Mine button) + Pause Menu Restart + Exit Race
        composeTestRule.mainClock.autoAdvance = false
        try {
            composeTestRule.onNodeWithTag("daily_start_button").performClick()
            repeat(12) {
                composeTestRule.mainClock.advanceTimeBy(50L)
                Thread.sleep(15L)
            }

            // Nitro button present, Drop Mine button hidden in Daily Challenge
            composeTestRule.onNodeWithTag("use_powerup_button").assertIsDisplayed()
            composeTestRule.onNodeWithTag("drop_mine_button").assertDoesNotExist()

            // Open pause/exit dialog
            composeTestRule.onNodeWithTag("race_pause_button").performClick()
            composeTestRule.mainClock.advanceTimeBy(100L)

            // Both Restart AND Exit Race buttons must be displayed in the exit dialog
            composeTestRule.onNodeWithTag("race_restart_button").assertIsDisplayed()
            composeTestRule.onNodeWithText("Restart").assertIsDisplayed()
            composeTestRule.onNodeWithText("Exit Race").assertIsDisplayed()

            // Click Restart -> resumes at clean race start HUD (without leaving RaceScreen)
            composeTestRule.onNodeWithTag("race_restart_button").performClick()
            repeat(8) {
                composeTestRule.mainClock.advanceTimeBy(50L)
                Thread.sleep(15L)
            }
            composeTestRule.onNodeWithTag("race_pause_button").assertIsDisplayed()
            composeTestRule.onNodeWithTag("drop_mine_button").assertDoesNotExist()

            // Now Exit Race back to Home
            composeTestRule.onNodeWithTag("race_pause_button").performClick()
            composeTestRule.mainClock.advanceTimeBy(100L)
            composeTestRule.onNodeWithText("Exit Race").performClick()
            composeTestRule.mainClock.advanceTimeBy(150L)
        } finally {
            composeTestRule.mainClock.autoAdvance = true
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("daily_challenge_button").performScrollTo().assertIsDisplayed()
    }
}

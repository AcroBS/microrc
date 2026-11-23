package com.example

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.data.FriendStandingsCalculator
import com.example.data.PlayStreakState
import com.example.model.FriendUiModel
import com.example.model.GameMode
import com.example.model.LeaderboardEntryDoc
import com.example.model.PlayerProfile
import com.example.model.RcCarType
import com.example.model.SessionInviteDoc
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.MicroRCTheme
import com.example.util.DailyChallengeScheduler
import com.example.util.SessionNotificationHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StreakAndStandingsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun backgroundSyncAndSessionInvite_deduplicatesAndEnqueuesConnectedWorkManagerJobs() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNm = Shadows.shadowOf(nm)

        // 1. Session invite notification posts once and deduplicates repeat background/live deliveries
        val invite = SessionInviteDoc(
            id = "invite_dedupe_${System.nanoTime()}",
            senderUid = "uid_alex",
            senderName = "TurboAlex",
            recipientUid = "uid_me",
            roomId = "room_1",
            roomCode = "RACE88",
            roomName = "Apex Crew",
            gameMode = GameMode.RACE.name,
            joinToken = "abc123",
            status = "PENDING"
        )
        assertTrue(SessionNotificationHelper.showSessionInviteNotification(app, invite))
        val posted = shadowNm.allNotifications.find { n ->
            n.extras.getString(Notification.EXTRA_TITLE)?.contains("TurboAlex") == true
        }
        assertNotNull(posted)
        assertFalse(SessionNotificationHelper.showSessionInviteNotification(app, invite))

        // 2. Schedule WorkManager periodic + immediate background sync with NetworkType.CONNECTED
        WorkManagerTestInitHelper.initializeTestWorkManager(
            app,
            Configuration.Builder().build()
        )
        DailyChallengeScheduler.scheduleLeaderboardSync(app)
        DailyChallengeScheduler.triggerImmediateBackgroundSync(app)

        val wm = WorkManager.getInstance(app)
        val periodicInfos = wm.getWorkInfosForUniqueWork(DailyChallengeScheduler.PERIODIC_SYNC_WORK_NAME).get()
        assertEquals(1, periodicInfos.size)
        assertTrue(
            periodicInfos.first().state == WorkInfo.State.ENQUEUED ||
                periodicInfos.first().state == WorkInfo.State.RUNNING
        )
        assertEquals(NetworkType.CONNECTED, periodicInfos.first().constraints.requiredNetworkType)

        val immediateInfos = wm.getWorkInfosForUniqueWork(DailyChallengeScheduler.IMMEDIATE_SYNC_WORK_NAME).get()
        assertEquals(1, immediateInfos.size)
        assertEquals(NetworkType.CONNECTED, immediateInfos.first().constraints.requiredNetworkType)
    }

    @Test
    fun homeScreen_displaysPlayStreakAndPersonalVsFriendsStandingsAndOpensBoard() {
        val summary = FriendStandingsCalculator.compute(
            myUid = "uid_me",
            myName = "Alex",
            friends = listOf(
                FriendUiModel(
                    friendshipId = "f1",
                    friendUid = "uid_riley",
                    displayName = "Riley",
                    friendCode = "RCRILEY9",
                    carType = RcCarType.VOLT_BUGGY,
                    primaryColorArgb = 0xFF00E5FF,
                    accentColorArgb = 0xFFFFD54F,
                    effectivelyOnline = true,
                    activityStatus = "ONLINE",
                    activeRoomId = "",
                    activeRoomCode = "",
                    activeRoomName = "",
                    statusLabel = "Online"
                )
            ),
            cloudEntries = listOf(
                LeaderboardEntryDoc(
                    id = "uid_me_RACE_GP_APEX_3L",
                    userId = "uid_me",
                    driverName = "Alex",
                    gameMode = GameMode.RACE.name,
                    trackKey = "GP_APEX",
                    boardKey = "RACE_GP_APEX_3L",
                    totalLaps = 3L,
                    bestRaceTimeMs = 34_100L
                ),
                LeaderboardEntryDoc(
                    id = "uid_riley_RACE_GP_APEX_3L",
                    userId = "uid_riley",
                    driverName = "Riley",
                    gameMode = GameMode.RACE.name,
                    trackKey = "GP_APEX",
                    boardKey = "RACE_GP_APEX_3L",
                    totalLaps = 3L,
                    bestRaceTimeMs = 35_600L
                )
            ),
            localRecords = emptyList(),
            todayDateKey = "20261123"
        )

        var openedBoard: Triple<String, GameMode, Boolean>? = null

        composeTestRule.setContent {
            MicroRCTheme {
                HomeScreen(
                    profile = PlayerProfile(playerId = "uid_me", name = "Alex"),
                    onHostRace = {},
                    onJoinRace = {},
                    onSoloRace = {},
                    onOpenGarage = {},
                    onUpdateDriverName = {},
                    playStreak = PlayStreakState(
                        currentStreakDays = 4,
                        bestStreakDays = 7,
                        lastActiveDateKey = "20261123",
                        activeToday = true
                    ),
                    isSignedIn = true,
                    friendsCount = 1,
                    friendStandings = summary,
                    onOpenStandingsBoard = { trackKey, gameMode, isDaily ->
                        openedBoard = Triple(trackKey, gameMode, isDaily)
                    }
                )
            }
        }

        composeTestRule.onNodeWithTag("home_streak_standings_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_streak_badge").assertIsDisplayed()
        composeTestRule.onNodeWithText("4 Day Streak").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_standings_rank_badge").assertIsDisplayed()
        composeTestRule.onNodeWithText("#1 of 2 Crew").assertIsDisplayed()
        composeTestRule.onNodeWithText("Lead 1 • Chase 0").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_standings_board_row_0").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("-1.50s").assertIsDisplayed()

        composeTestRule.onNodeWithTag("home_standings_board_row_0").performClick()
        assertEquals(Triple("GP_APEX", GameMode.RACE, false), openedBoard)
    }

    @Test
    fun homeScreen_signedOutShowsStreakAndSignInCta() {
        var openedFriends = false
        composeTestRule.setContent {
            MicroRCTheme {
                HomeScreen(
                    profile = PlayerProfile(playerId = "local", name = "Alex"),
                    onHostRace = {},
                    onJoinRace = {},
                    onSoloRace = {},
                    onOpenFriends = { openedFriends = true },
                    onOpenGarage = {},
                    onUpdateDriverName = {},
                    playStreak = PlayStreakState(
                        currentStreakDays = 2,
                        bestStreakDays = 5,
                        lastActiveDateKey = "20261122",
                        activeToday = false
                    ),
                    isSignedIn = false,
                    friendsCount = 0,
                    localRecordsCount = 3,
                    friendStandings = null
                )
            }
        }

        composeTestRule.onNodeWithTag("home_streak_standings_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("2 Day Streak").assertIsDisplayed()
        composeTestRule.onNodeWithText("3 Local PBs").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_standings_cta_button").performScrollTo().performClick()
        assertTrue(openedFriends)
    }
}

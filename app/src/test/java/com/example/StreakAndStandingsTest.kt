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
import com.example.data.DailyChallengeCatalog
import com.example.data.FriendStandingsCalculator
import com.example.data.PlayStreakState
import com.example.model.ChallengedFriendStatus
import com.example.model.FriendOnlineAlert
import com.example.model.FriendUiModel
import com.example.model.GameMode
import com.example.model.LeaderboardEntryDoc
import com.example.model.PendingChallengePhase
import com.example.model.PendingFriendChallengeState
import com.example.model.PlayerProfile
import com.example.model.RcCarType
import com.example.model.SessionInviteDoc
import com.example.ui.components.LiveFriendOnlineBanner
import com.example.ui.components.LivePendingChallengeBanner
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
                ),
                LeaderboardEntryDoc(
                    id = "uid_me_RACE_custom_neon_1_3L",
                    userId = "uid_me",
                    driverName = "Alex",
                    gameMode = GameMode.RACE.name,
                    trackKey = "custom_neon_1",
                    boardKey = "RACE_custom_neon_1_3L",
                    totalLaps = 3L,
                    bestRaceTimeMs = 28_450L
                )
            ),
            localRecords = emptyList(),
            todayDateKey = "20261123",
            customTrackNames = mapOf("custom_neon_1" to "Neon Hairpin")
        )

        var openedFriends = false

        composeTestRule.setContent {
            MicroRCTheme {
                HomeScreen(
                    profile = PlayerProfile(playerId = "uid_me", name = "Alex"),
                    onHostRace = {},
                    onJoinRace = {},
                    onSoloRace = {},
                    onOpenFriends = { openedFriends = true },
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
                    friendStandings = summary
                )
            }
        }

        // Streak lives in the compact hero banner flame pill (no duplicate 4 Day Streak box at bottom)
        composeTestRule.onNodeWithTag("home_streak_badge").assertIsDisplayed()
        composeTestRule.onNodeWithText("4d").assertIsDisplayed()
        composeTestRule.onNodeWithText("4 Day Streak").assertDoesNotExist()
        composeTestRule.onNodeWithTag("home_streak_milestone_banner").assertDoesNotExist()

        // Bottom Crew Ranking card displays ranked drivers and opens Standings on tap
        composeTestRule.onNodeWithTag("home_streak_standings_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_standings_rank_badge", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("CREW RANKING").assertIsDisplayed()
        composeTestRule.onNodeWithText("Alex (You)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Lead 1 • Chase 0").assertIsDisplayed()

        composeTestRule.onNodeWithTag("home_challenges_section").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_online_section").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_offline_section").assertDoesNotExist()

        composeTestRule.onNodeWithTag("home_streak_standings_card").performScrollTo().performClick()
        assertTrue(openedFriends)
    }

    @Test
    fun homeScreen_fiveDayStreakShowsWelcomeMilestoneBannerAndOfflineIsOptional() {
        composeTestRule.setContent {
            MicroRCTheme {
                HomeScreen(
                    profile = PlayerProfile(playerId = "local", name = "Alex"),
                    onHostRace = {},
                    onJoinRace = {},
                    onSoloRace = {},
                    onOpenGarage = {},
                    onUpdateDriverName = {},
                    playStreak = PlayStreakState(
                        currentStreakDays = 5,
                        bestStreakDays = 5,
                        lastActiveDateKey = "20261123",
                        activeToday = true
                    ),
                    isSignedIn = false,
                    friendsCount = 0,
                    localRecordsCount = 3,
                    friendStandings = null
                )
            }
        }

        composeTestRule.onNodeWithTag("home_streak_milestone_banner").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Welcome! 5-Day Streak Milestone").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_challenges_section").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_online_section").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_offline_section").assertDoesNotExist()
        composeTestRule.onNodeWithTag("home_solo_section").assertDoesNotExist()

        composeTestRule.onNodeWithTag("home_scope_offline").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_offline_section").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_streak_standings_card").assertDoesNotExist()
        composeTestRule.onNodeWithTag("home_challenges_section").assertDoesNotExist()
        composeTestRule.onNodeWithTag("home_online_section").assertDoesNotExist()

        composeTestRule.onNodeWithTag("home_scope_solo").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_solo_section").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_streak_standings_card").assertDoesNotExist()
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

        composeTestRule.onNodeWithTag("home_streak_badge").assertIsDisplayed()
        composeTestRule.onNodeWithText("2d").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_streak_standings_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("CREW RANKING").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sign in to compare (3 times)").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_streak_standings_card").performScrollTo().performClick()
        assertTrue(openedFriends)
    }

    @Test
    fun liveFriendOnlineBanner_supportsSingleAndMultiFriendChallengeAndPendingCountdownBanner() {
        var challengedUids: List<String> = emptyList()
        var dismissedUid: String? = null
        val alert = FriendOnlineAlert(
            friendUid = "uid_riley",
            displayName = "Riley",
            activeRoomCode = "RACE99",
            activeRoomName = "Riley's Crew",
            activityStatus = "IN_LOBBY"
        )
        val onlineFriends = listOf(
            FriendUiModel(
                friendshipId = "f_riley",
                friendUid = "uid_riley",
                displayName = "Riley",
                friendCode = "RCRILEY1",
                carType = RcCarType.VOLT_BUGGY,
                primaryColorArgb = 0xFF00E5FF,
                accentColorArgb = 0xFFFFD54F,
                effectivelyOnline = true,
                activityStatus = "IN_LOBBY",
                activeRoomId = "room_RACE99",
                activeRoomCode = "RACE99",
                activeRoomName = "Riley's Crew",
                statusLabel = "Online"
            ),
            FriendUiModel(
                friendshipId = "f_alex",
                friendUid = "uid_alex",
                displayName = "Alex",
                friendCode = "RCALEX02",
                carType = RcCarType.VOLT_BUGGY,
                primaryColorArgb = 0xFFFF5722,
                accentColorArgb = 0xFFFFD54F,
                effectivelyOnline = true,
                activityStatus = "ONLINE",
                activeRoomId = "",
                activeRoomCode = "",
                activeRoomName = "",
                statusLabel = "Online"
            )
        )

        composeTestRule.setContent {
            MicroRCTheme {
                LiveFriendOnlineBanner(
                    alert = alert,
                    onlineFriends = onlineFriends,
                    compact = true,
                    applyStatusBarInsets = false,
                    onChallengeFriends = { targets -> challengedUids = targets.map { it.friendUid } },
                    onDismiss = { dismissedUid = it.friendUid }
                )
            }
        }

        composeTestRule.onNodeWithTag("live_friend_online_banner").assertIsDisplayed()
        composeTestRule.onNodeWithText("Riley + 1 online").assertIsDisplayed()
        composeTestRule.onNodeWithTag("friend_online_multi_select_row").assertIsDisplayed()
        composeTestRule.onNodeWithTag("friend_online_select_all_chip").performClick()
        composeTestRule.onNodeWithText("Challenge (2)").assertIsDisplayed()
        composeTestRule.onNodeWithTag("challenge_friend_online_button").performClick()
        assertEquals(listOf("uid_riley", "uid_alex"), challengedUids)
        composeTestRule.onNodeWithTag("dismiss_friend_online_button").performClick()
        assertEquals("uid_riley", dismissedUid)
    }

    @Test
    fun livePendingChallengeBanner_displaysWaitingStateTimerAndCancel() {
        var cancelled = false
        val pendingState = PendingFriendChallengeState(
            challengeId = "ch_ui_1",
            roomId = "room_CH99",
            roomCode = "CH99",
            ownsStandbyRoom = true,
            targets = listOf(
                ChallengedFriendStatus(friendUid = "uid_riley", displayName = "Riley", inviteId = "inv_1")
            ),
            totalSeconds = 60,
            remainingSeconds = 45,
            phase = PendingChallengePhase.WAITING
        )

        composeTestRule.setContent {
            MicroRCTheme {
                LivePendingChallengeBanner(
                    state = pendingState,
                    compact = true,
                    applyStatusBarInsets = false,
                    onCancel = { cancelled = true }
                )
            }
        }

        composeTestRule.onNodeWithTag("live_pending_challenge_banner").assertIsDisplayed()
        composeTestRule.onNodeWithText("Waiting for Riley...").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pending_challenge_timer_text").assertIsDisplayed()
        composeTestRule.onNodeWithText("45s").assertIsDisplayed()
        composeTestRule.onNodeWithTag("cancel_friend_challenge_button").performClick()
        assertTrue(cancelled)
    }

    @Test
    fun homeDailyChallenges_showClearLeadStatusAndFlatLayout() {
        val dailyGpDate = "20261125"
        val dailyGpTrack = DailyChallengeCatalog.trackIdForDate(dailyGpDate)
        val dailyGpBoard = DailyChallengeCatalog.boardKeyForDate(dailyGpDate)
        val dailyDriftTrack = com.example.drift.DriftDailyChallenge.trackIdForDate(dailyGpDate)
        val dailyDriftBoard = com.example.drift.DriftDailyChallenge.boardKeyForDate(dailyGpDate)
        val dailySummary = FriendStandingsCalculator.compute(
            myUid = "uid_me",
            myName = "Alex",
            friends = listOf(
                FriendUiModel(
                    friendshipId = "f1",
                    friendUid = "uid_rob",
                    displayName = "Rob",
                    friendCode = "RCROB001",
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
                    id = "d_gp_me",
                    userId = "uid_me",
                    driverName = "Alex",
                    gameMode = GameMode.RACE.name,
                    trackKey = dailyGpTrack,
                    boardKey = dailyGpBoard,
                    totalLaps = 3L,
                    bestRaceTimeMs = 38_400L
                ),
                LeaderboardEntryDoc(
                    id = "d_gp_rob",
                    userId = "uid_rob",
                    driverName = "Rob",
                    gameMode = GameMode.RACE.name,
                    trackKey = dailyGpTrack,
                    boardKey = dailyGpBoard,
                    totalLaps = 3L,
                    bestRaceTimeMs = 39_100L
                ),
                LeaderboardEntryDoc(
                    id = "d_drift_rob",
                    userId = "uid_rob",
                    driverName = "Rob",
                    gameMode = GameMode.DRIFT.name,
                    trackKey = dailyDriftTrack,
                    boardKey = dailyDriftBoard,
                    totalLaps = 3L,
                    bestRaceTimeMs = 41_200L
                )
            ),
            localRecords = emptyList(),
            todayDateKey = dailyGpDate
        )

        composeTestRule.setContent {
            MicroRCTheme {
                HomeScreen(
                    profile = PlayerProfile(playerId = "uid_me", name = "Alex"),
                    onHostRace = {},
                    onJoinRace = {},
                    onSoloRace = {},
                    onOpenGarage = {},
                    onUpdateDriverName = {},
                    dailyChallengeResetLabel = "Resets in 5h 27m",
                    isSignedIn = true,
                    friendsCount = 1,
                    friendStandings = dailySummary
                )
            }
        }

        composeTestRule.onNodeWithText("DAILY CHALLENGES").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Resets in 5h 27m").assertIsDisplayed()
        composeTestRule.onNodeWithText("Daily GP").assertIsDisplayed()
        composeTestRule.onNodeWithText("You lead").assertIsDisplayed()
        composeTestRule.onNodeWithText("Daily Drift").assertIsDisplayed()
        composeTestRule.onNodeWithText("Rob leads").assertIsDisplayed()
        composeTestRule.onNodeWithText("No PB yet").assertDoesNotExist()
        composeTestRule.onNodeWithText("LIVE ONLINE MULTIPLAYER").assertDoesNotExist()
        composeTestRule.onNodeWithText("DAILY CHALLENGES & GHOSTS").assertDoesNotExist()
    }

    @Test
    fun friendsScreen_placesNotifyInlineAndReservesBottomAreaForCustomTracksOnly() {
        val friendNoTracks = FriendUiModel(
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
        val friendWithTrack = FriendUiModel(
            friendshipId = "f2",
            friendUid = "uid_sam",
            displayName = "Sam",
            friendCode = "RCSAM123",
            carType = RcCarType.APEX_FORMULA,
            primaryColorArgb = 0xFFFF5722,
            accentColorArgb = 0xFFFFFFFF,
            effectivelyOnline = false,
            activityStatus = "OFFLINE",
            activeRoomId = "",
            activeRoomCode = "",
            activeRoomName = "",
            statusLabel = "Offline"
        )
        val samTrack = com.example.model.CustomTrackData(
            id = "custom_sam_1",
            name = "Sam Circuit",
            controlPoints = listOf(
                com.example.model.TrackPoint(0f, 0f),
                com.example.model.TrackPoint(300f, 0f),
                com.example.model.TrackPoint(300f, 300f),
                com.example.model.TrackPoint(0f, 300f)
            ),
            creatorUid = "uid_sam",
            creatorName = "Sam"
        )
        var notifiedUid: String? = null

        composeTestRule.setContent {
            MicroRCTheme {
                com.example.ui.screens.FriendsScreen(
                    profile = PlayerProfile(playerId = "uid_me", name = "Alex"),
                    myFriendCode = "RCALEX01",
                    activeOnlineRoom = null,
                    friends = listOf(friendNoTracks, friendWithTrack),
                    friendCustomTracksByUid = mapOf("uid_sam" to listOf(samTrack)),
                    incomingRequests = emptyList(),
                    outgoingRequests = emptyList(),
                    searchResults = emptyList(),
                    isSearching = false,
                    feedbackMessage = null,
                    onDismissFeedback = {},
                    onAddFriendByCode = {},
                    onSearchDrivers = {},
                    onAddDriverFromSearch = {},
                    onAcceptRequest = {},
                    onDeclineOrRemoveFriendship = { _, _ -> },
                    onNotifyFriendToJoin = { notifiedUid = it.friendUid },
                    onNotifyAllOnlineFriends = {},
                    onJoinFriendLobby = {},
                    onOpenOnlineHub = {},
                    onBack = {}
                )
            }
        }

        val notifyRiley = composeTestRule.onNodeWithTag("notify_friend_button_uid_riley").performScrollTo()
        val removeRiley = composeTestRule.onNodeWithTag("remove_friend_button_uid_riley")
        notifyRiley.assertIsDisplayed()
        removeRiley.assertIsDisplayed()

        // Inline Notify sits on the same horizontal row as Remove Friend
        val notifyY = notifyRiley.fetchSemanticsNode().positionInRoot.y
        val removeY = removeRiley.fetchSemanticsNode().positionInRoot.y
        assertTrue(
            "Inline notify pill ($notifyY) should align horizontally with remove icon ($removeY)",
            kotlin.math.abs(notifyY - removeY) < 28f
        )

        composeTestRule.onNodeWithTag("challenge_friend_custom_track_uid_riley").assertDoesNotExist()
        composeTestRule.onNodeWithTag("challenge_friend_custom_track_uid_sam").performScrollTo().assertIsDisplayed()
        notifyRiley.performClick()
        assertEquals("uid_riley", notifiedUid)
    }

    @Test
    fun onlineHubScreen_rendersStartingModesInSingleHorizontalRow() {
        composeTestRule.setContent {
            MicroRCTheme {
                com.example.ui.screens.OnlineHubScreen(
                    profile = PlayerProfile(playerId = "uid_me", name = "Alex"),
                    accountLabel = "alex@example.com",
                    initialRules = com.example.model.RaceRulesSettings(),
                    openRooms = emptyList(),
                    isBusy = false,
                    errorBanner = null,
                    onDismissError = {},
                    onCreateRoom = { _, _, _, _, _ -> },
                    onJoinRoom = { _, _ -> },
                    onJoinRoomByCode = { _, _, _ -> },
                    onOpenLeaderboards = {},
                    onSignOut = {},
                    onBack = {}
                )
            }
        }

        val rowNode = composeTestRule.onNodeWithTag("online_starting_mode_row").performScrollTo()
        rowNode.assertIsDisplayed()
        val raceY = composeTestRule.onNodeWithTag("online_mode_RACE").fetchSemanticsNode().positionInRoot.y
        val driftY = composeTestRule.onNodeWithTag("online_mode_DRIFT").fetchSemanticsNode().positionInRoot.y
        assertTrue(
            "Starting mode chips must stay on a single row ($raceY vs $driftY)",
            kotlin.math.abs(raceY - driftY) < 4f
        )
    }

    @Test
    fun friendStandingsScreen_displaysCrewRankingChasingLeadingAndLaunchesGhostOnTap() {
        val summary = FriendStandingsCalculator.compute(
            myUid = "uid_me",
            myName = "Acro",
            friends = listOf(
                FriendUiModel(
                    friendshipId = "f_rob",
                    friendUid = "uid_rob",
                    displayName = "Rob",
                    friendCode = "RCROB001",
                    carType = RcCarType.VOLT_BUGGY,
                    primaryColorArgb = 0xFF00E5FF,
                    accentColorArgb = 0xFFFFFFFF,
                    effectivelyOnline = true,
                    activityStatus = "ONLINE",
                    activeRoomId = "",
                    activeRoomCode = "",
                    activeRoomName = "",
                    statusLabel = "Online"
                ),
                FriendUiModel(
                    friendshipId = "f_frisoer",
                    friendUid = "uid_frisoer",
                    displayName = "Frisoer",
                    friendCode = "RCFRIS02",
                    carType = RcCarType.APEX_FORMULA,
                    primaryColorArgb = 0xFFFF5722,
                    accentColorArgb = 0xFFFFD54F,
                    effectivelyOnline = false,
                    activityStatus = "OFFLINE",
                    activeRoomId = "",
                    activeRoomCode = "",
                    activeRoomName = "",
                    statusLabel = "Offline"
                )
            ),
            cloudEntries = listOf(
                LeaderboardEntryDoc(
                    id = "me_lr", userId = "uid_me", driverName = "Acro",
                    gameMode = GameMode.RACE.name, trackKey = "LIVING_ROOM_GP", boardKey = "RACE_LIVING_ROOM_GP_3L",
                    totalLaps = 3L, bestRaceTimeMs = 30_350L
                ),
                LeaderboardEntryDoc(
                    id = "rob_lr", userId = "uid_rob", driverName = "Rob",
                    gameMode = GameMode.RACE.name, trackKey = "LIVING_ROOM_GP", boardKey = "RACE_LIVING_ROOM_GP_3L",
                    totalLaps = 3L, bestRaceTimeMs = 30_000L, hasGhost = true, ghostId = "ghost_rob_lr"
                ),
                LeaderboardEntryDoc(
                    id = "me_apex", userId = "uid_me", driverName = "Acro",
                    gameMode = GameMode.RACE.name, trackKey = "GP_APEX", boardKey = "RACE_GP_APEX_3L",
                    totalLaps = 3L, bestRaceTimeMs = 35_000L
                ),
                LeaderboardEntryDoc(
                    id = "frisoer_apex", userId = "uid_frisoer", driverName = "Frisoer",
                    gameMode = GameMode.RACE.name, trackKey = "GP_APEX", boardKey = "RACE_GP_APEX_3L",
                    totalLaps = 3L, bestRaceTimeMs = 35_670L
                ),
                LeaderboardEntryDoc(
                    id = "me_dirt", userId = "uid_me", driverName = "Acro",
                    gameMode = GameMode.RACE.name, trackKey = "BACKYARD_DIRT", boardKey = "RACE_BACKYARD_DIRT_3L",
                    totalLaps = 3L, bestRaceTimeMs = 38_000L
                ),
                LeaderboardEntryDoc(
                    id = "rob_dirt", userId = "uid_rob", driverName = "Rob",
                    gameMode = GameMode.RACE.name, trackKey = "BACKYARD_DIRT", boardKey = "RACE_BACKYARD_DIRT_3L",
                    totalLaps = 3L, bestRaceTimeMs = 39_100L
                )
            ),
            localRecords = emptyList(),
            todayDateKey = "20261126"
        )

        var openedManageFriends = false
        var launchedBoardKey: String? = null

        composeTestRule.setContent {
            MicroRCTheme {
                com.example.ui.screens.FriendStandingsScreen(
                    standings = summary,
                    isSignedIn = true,
                    friendsCount = 2,
                    onOpenManageFriends = { openedManageFriends = true },
                    onLaunchGhostRace = { launchedBoardKey = it.boardKey },
                    onBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Friend Standings").assertIsDisplayed()
        composeTestRule.onNodeWithTag("standings_manage_friends_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("standings_crew_ranking_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Acro (You)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Rob").assertIsDisplayed()
        composeTestRule.onNodeWithText("Frisoer").assertIsDisplayed()
        composeTestRule.onNodeWithText("Lead 2 • Chase 1").assertIsDisplayed()

        composeTestRule.onNodeWithTag("standings_chasing_section").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Chasing (1)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Rob leads by -0.35s").assertIsDisplayed()

        composeTestRule.onNodeWithTag("standings_leading_section").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Leading (2)").assertIsDisplayed()
        composeTestRule.onNodeWithText("You lead by +0.67s").assertIsDisplayed()

        // Tapping a chasing row launches a ghost race on that track immediately
        composeTestRule.onNodeWithTag("standings_chasing_row_0").performScrollTo().performClick()
        assertEquals("RACE_LIVING_ROOM_GP_3L", launchedBoardKey)

        // Tapping "+ Add / Manage" opens Friends & Crew management
        composeTestRule.onNodeWithTag("standings_manage_friends_button").performClick()
        assertTrue(openedManageFriends)
    }

    @Test
    fun homeScreen_showsCappedCrewRankingLeaderboardAndRoutesToFriendStandings() {
        val summary = FriendStandingsCalculator.compute(
            myUid = "uid_me",
            myName = "Acro",
            friends = listOf(
                FriendUiModel(
                    friendshipId = "f_rob",
                    friendUid = "uid_rob",
                    displayName = "Rob",
                    friendCode = "RCROB001",
                    carType = RcCarType.VOLT_BUGGY,
                    primaryColorArgb = 0xFF00E5FF,
                    accentColorArgb = 0xFFFFFFFF,
                    effectivelyOnline = true,
                    activityStatus = "ONLINE",
                    activeRoomId = "",
                    activeRoomCode = "",
                    activeRoomName = "",
                    statusLabel = "Online"
                ),
                FriendUiModel(
                    friendshipId = "f_frisoer",
                    friendUid = "uid_frisoer",
                    displayName = "Frisoer",
                    friendCode = "RCFRIS02",
                    carType = RcCarType.APEX_FORMULA,
                    primaryColorArgb = 0xFFFF5722,
                    accentColorArgb = 0xFFFFD54F,
                    effectivelyOnline = false,
                    activityStatus = "OFFLINE",
                    activeRoomId = "",
                    activeRoomCode = "",
                    activeRoomName = "",
                    statusLabel = "Offline"
                ),
                FriendUiModel(
                    friendshipId = "f_sam",
                    friendUid = "uid_sam",
                    displayName = "Sam",
                    friendCode = "RCSAM003",
                    carType = RcCarType.VOLT_BUGGY,
                    primaryColorArgb = 0xFF4CAF50,
                    accentColorArgb = 0xFFFFFFFF,
                    effectivelyOnline = false,
                    activityStatus = "OFFLINE",
                    activeRoomId = "",
                    activeRoomCode = "",
                    activeRoomName = "",
                    statusLabel = "Offline"
                ),
                FriendUiModel(
                    friendshipId = "f_nova",
                    friendUid = "uid_nova",
                    displayName = "Nova",
                    friendCode = "RCNOV004",
                    carType = RcCarType.VOLT_BUGGY,
                    primaryColorArgb = 0xFF9C27B0,
                    accentColorArgb = 0xFFFFFFFF,
                    effectivelyOnline = false,
                    activityStatus = "OFFLINE",
                    activeRoomId = "",
                    activeRoomCode = "",
                    activeRoomName = "",
                    statusLabel = "Offline"
                )
            ),
            cloudEntries = listOf(
                // Acro leads 2 boards (0 chases), Rob chases 2 boards (0 leads), Frisoer chases 1 board
                LeaderboardEntryDoc(
                    id = "m1", userId = "uid_me", driverName = "Acro",
                    gameMode = GameMode.RACE.name, trackKey = "LIVING_ROOM_GP", boardKey = "RACE_LIVING_ROOM_GP_3L",
                    totalLaps = 3L, bestRaceTimeMs = 30_000L
                ),
                LeaderboardEntryDoc(
                    id = "r1", userId = "uid_rob", driverName = "Rob",
                    gameMode = GameMode.RACE.name, trackKey = "LIVING_ROOM_GP", boardKey = "RACE_LIVING_ROOM_GP_3L",
                    totalLaps = 3L, bestRaceTimeMs = 30_400L
                ),
                LeaderboardEntryDoc(
                    id = "m2", userId = "uid_me", driverName = "Acro",
                    gameMode = GameMode.RACE.name, trackKey = "BACKYARD_DIRT", boardKey = "RACE_BACKYARD_DIRT_3L",
                    totalLaps = 3L, bestRaceTimeMs = 35_000L
                ),
                LeaderboardEntryDoc(
                    id = "f2", userId = "uid_frisoer", driverName = "Frisoer",
                    gameMode = GameMode.RACE.name, trackKey = "BACKYARD_DIRT", boardKey = "RACE_BACKYARD_DIRT_3L",
                    totalLaps = 3L, bestRaceTimeMs = 35_670L
                ),
                LeaderboardEntryDoc(
                    id = "r2", userId = "uid_rob", driverName = "Rob",
                    gameMode = GameMode.RACE.name, trackKey = "BACKYARD_DIRT", boardKey = "RACE_BACKYARD_DIRT_3L",
                    totalLaps = 3L, bestRaceTimeMs = 36_100L
                ),
                LeaderboardEntryDoc(
                    id = "s2", userId = "uid_sam", driverName = "Sam",
                    gameMode = GameMode.RACE.name, trackKey = "BACKYARD_DIRT", boardKey = "RACE_BACKYARD_DIRT_3L",
                    totalLaps = 3L, bestRaceTimeMs = 37_000L
                )
            ),
            localRecords = emptyList(),
            todayDateKey = "20261126"
        )

        var standingsOpens = 0
        var friendsOpens = 0

        composeTestRule.setContent {
            MicroRCTheme {
                HomeScreen(
                    profile = PlayerProfile(playerId = "uid_me", name = "Acro"),
                    onHostRace = {},
                    onJoinRace = {},
                    onSoloRace = {},
                    onOpenFriends = { friendsOpens++ },
                    onOpenFriendStandings = { standingsOpens++ },
                    onOpenGarage = {},
                    onUpdateDriverName = {},
                    isSignedIn = true,
                    friendsCount = 4,
                    friendStandings = summary
                )
            }
        }

        // Card header displays "CREW RANKING" on left + "Track Breakdown" on right
        composeTestRule.onNodeWithText("RECENT RIVALRY").assertDoesNotExist()
        composeTestRule.onNodeWithText("CREW RANKING").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Track Breakdown").assertIsDisplayed()

        // Displays Crew Leaderboard capped at top 4 drivers, with ordinal rank & Lead / Chase counts
        composeTestRule.onNodeWithTag("home_crew_ranking_row_0", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_crew_ranking_row_1", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_crew_ranking_row_2", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_crew_ranking_row_3", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_crew_ranking_row_4", useUnmergedTree = true).assertDoesNotExist()

        composeTestRule.onNodeWithText("Acro (You)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Lead 2 • Chase 0").assertIsDisplayed()
        composeTestRule.onNodeWithText("Frisoer").assertIsDisplayed()
        composeTestRule.onNodeWithText("Lead 0 • Chase 1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Rob").assertIsDisplayed()
        composeTestRule.onNodeWithText("Lead 0 • Chase 2").assertIsDisplayed()

        // Individual track delta rows are moved out of HomeScreen into Friend Standings
        composeTestRule.onNodeWithText("You lead by +0.67s").assertDoesNotExist()

        // Tapping anywhere on the card or Track Breakdown opens Friend Standings
        composeTestRule.onNodeWithTag("home_crew_ranking_row_1", useUnmergedTree = true).performClick()
        assertEquals(1, standingsOpens)
        assertEquals(0, friendsOpens)

        composeTestRule.onNodeWithTag("home_streak_standings_card").performScrollTo().performClick()
        assertEquals(2, standingsOpens)
    }

    @Test
    fun leaderboardScreen_allTracksPillShowsUnifiedTableAndNavigatesToIndividualTrack() {
        val localRecords = listOf(
            com.example.model.LocalLeaderboardRecord(
                boardKey = "RACE_LIVING_ROOM_GP_3L",
                gameMode = GameMode.RACE.name,
                trackKey = "LIVING_ROOM_GP",
                totalLaps = 3,
                driverName = "Acro",
                carType = RcCarType.VOLT_BUGGY.name,
                primaryColorArgb = 0xFFFF5722,
                accentColorArgb = 0xFFFFD54F,
                bestRaceTimeMs = 31_400L
            ),
            com.example.model.LocalLeaderboardRecord(
                boardKey = "RACE_BACKYARD_DIRT_3L",
                gameMode = GameMode.RACE.name,
                trackKey = "BACKYARD_DIRT",
                totalLaps = 3,
                driverName = "Acro",
                carType = RcCarType.VOLT_BUGGY.name,
                primaryColorArgb = 0xFFFF5722,
                accentColorArgb = 0xFFFFD54F,
                bestRaceTimeMs = 42_000L
            )
        )
        val friendEntries = listOf(
            LeaderboardEntryDoc(
                id = "rob_dirt",
                userId = "uid_rob",
                driverName = "Rob",
                gameMode = GameMode.RACE.name,
                trackKey = "BACKYARD_DIRT",
                boardKey = "RACE_BACKYARD_DIRT_3L",
                totalLaps = 3L,
                bestRaceTimeMs = 40_500L
            )
        )

        composeTestRule.setContent {
            MicroRCTheme {
                com.example.ui.screens.LeaderboardScreen(
                    localRecords = localRecords,
                    isSignedIn = true,
                    currentUserId = "uid_me",
                    cloudEntries = emptyList(),
                    isLoadingCloud = false,
                    ghostLoadingId = null,
                    cloudStatusMessage = null,
                    friendCircleEntries = friendEntries,
                    onSelectBoardKey = {},
                    onStartPureSoloForTrack = { _, _, _, _ -> },
                    onChallengeCloudGhost = {},
                    onRequestSignIn = {},
                    onBack = {}
                )
            }
        }

        // "All Tracks" pill is the first chip in the horizontal track selector (left of Living Room GP)
        val allChip = composeTestRule.onNodeWithTag("leaderboard_track_ALL").performScrollTo().assertIsDisplayed()
        val firstTrackChip = composeTestRule.onNodeWithTag("leaderboard_track_LIVING_ROOM_GP").assertIsDisplayed()
        assertTrue(
            "All Tracks pill must be positioned first (to the left of Living Room GP)",
            allChip.fetchSemanticsNode().positionInRoot.x < firstTrackChip.fetchSemanticsNode().positionInRoot.x
        )

        // Switching to All Tracks shows unified table with best times & #1 leaders
        allChip.performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("leaderboard_all_tracks_table").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("leaderboard_all_tracks_row_LIVING_ROOM_GP").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("leaderboard_all_tracks_row_BACKYARD_DIRT").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Rob").assertIsDisplayed()
        composeTestRule.onNodeWithText("You").assertIsDisplayed()

        // Tapping an individual track row selects that track for detailed ghost selection
        composeTestRule.onNodeWithTag("leaderboard_all_tracks_row_BACKYARD_DIRT").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("leaderboard_all_tracks_table").assertDoesNotExist()
        composeTestRule.onNodeWithTag("leaderboard_start_solo_button").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun homeScreen_showsDailyCompletionCheckmarksAndDailySweepGoldCrown() {
        val today = DailyChallengeCatalog.todayDateKey()
        val dailyGpBoard = "RACE_daily_${today}_3L"
        val dailyDriftBoard = "DRIFT_daily_drift_${today}_3L"
        val friends = listOf(
            FriendUiModel(
                friendshipId = "f_rob",
                friendUid = "uid_rob",
                displayName = "Rob",
                friendCode = "RCROB001",
                carType = RcCarType.VOLT_BUGGY,
                primaryColorArgb = 0xFF2196F3,
                accentColorArgb = 0xFFFFFFFF,
                effectivelyOnline = false,
                activityStatus = "OFFLINE",
                activeRoomId = "",
                activeRoomCode = "",
                activeRoomName = "",
                statusLabel = "Offline"
            )
        )

        val sweepStandings = FriendStandingsCalculator.compute(
            myUid = "uid_me",
            myName = "Acro",
            friends = friends,
            cloudEntries = listOf(
                LeaderboardEntryDoc(
                    id = "gp_me", userId = "uid_me", driverName = "Acro",
                    gameMode = GameMode.RACE.name, trackKey = "daily_$today", boardKey = dailyGpBoard,
                    totalLaps = 3L, bestRaceTimeMs = 29_500L
                ),
                LeaderboardEntryDoc(
                    id = "gp_rob", userId = "uid_rob", driverName = "Rob",
                    gameMode = GameMode.RACE.name, trackKey = "daily_$today", boardKey = dailyGpBoard,
                    totalLaps = 3L, bestRaceTimeMs = 31_000L
                ),
                LeaderboardEntryDoc(
                    id = "dr_me", userId = "uid_me", driverName = "Acro",
                    gameMode = GameMode.DRIFT.name, trackKey = "daily_drift_$today", boardKey = dailyDriftBoard,
                    totalLaps = 3L, bestRaceTimeMs = 38_200L
                ),
                LeaderboardEntryDoc(
                    id = "dr_rob", userId = "uid_rob", driverName = "Rob",
                    gameMode = GameMode.DRIFT.name, trackKey = "daily_drift_$today", boardKey = dailyDriftBoard,
                    totalLaps = 3L, bestRaceTimeMs = 39_800L
                )
            ),
            localRecords = emptyList(),
            todayDateKey = today
        )

        val partialStandings = FriendStandingsCalculator.compute(
            myUid = "uid_me",
            myName = "Acro",
            friends = friends,
            cloudEntries = listOf(
                LeaderboardEntryDoc(
                    id = "gp_me", userId = "uid_me", driverName = "Acro",
                    gameMode = GameMode.RACE.name, trackKey = "daily_$today", boardKey = dailyGpBoard,
                    totalLaps = 3L, bestRaceTimeMs = 29_500L
                ),
                LeaderboardEntryDoc(
                    id = "gp_rob", userId = "uid_rob", driverName = "Rob",
                    gameMode = GameMode.RACE.name, trackKey = "daily_$today", boardKey = dailyGpBoard,
                    totalLaps = 3L, bestRaceTimeMs = 31_000L
                ),
                LeaderboardEntryDoc(
                    id = "dr_rob", userId = "uid_rob", driverName = "Rob",
                    gameMode = GameMode.DRIFT.name, trackKey = "daily_drift_$today", boardKey = dailyDriftBoard,
                    totalLaps = 3L, bestRaceTimeMs = 39_800L
                )
            ),
            localRecords = emptyList(),
            todayDateKey = today
        )

        val standingsState = androidx.compose.runtime.mutableStateOf(sweepStandings)
        val gpBestState = androidx.compose.runtime.mutableStateOf<Long?>(29_500L)
        val driftBestState = androidx.compose.runtime.mutableStateOf<Long?>(38_200L)

        composeTestRule.setContent {
            MicroRCTheme {
                HomeScreen(
                    profile = PlayerProfile(playerId = "uid_me", name = "Acro"),
                    onHostRace = {},
                    onJoinRace = {},
                    onSoloRace = {},
                    onOpenGarage = {},
                    onUpdateDriverName = {},
                    playStreak = PlayStreakState(currentStreakDays = 5, bestStreakDays = 5, activeToday = true),
                    dailyBestTimeMs = gpBestState.value,
                    dailyDriftBestTimeMs = driftBestState.value,
                    isSignedIn = true,
                    friendsCount = 1,
                    friendStandings = standingsState.value
                )
            }
        }

        // Daily Sweep (#1 in BOTH Daily GP & Daily Drift among friends) shows gold crown & both checkmarks
        composeTestRule.onNodeWithTag("home_streak_badge").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_daily_sweep_crown").assertIsDisplayed()
        composeTestRule.onNodeWithTag("daily_gp_completed_badge", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("daily_drift_completed_badge", useUnmergedTree = true).assertIsDisplayed()

        // When only Daily GP is completed & led (Drift not played), crown hides and only GP checkmark remains
        composeTestRule.runOnIdle {
            standingsState.value = partialStandings
            driftBestState.value = null
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_daily_sweep_crown").assertDoesNotExist()
        composeTestRule.onNodeWithTag("daily_gp_completed_badge", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("daily_drift_completed_badge", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun friendStandingsScreen_filtersByDistanceStreamlinesEmptyChasingAndShowsSkywayCrossover() {
        val summary = FriendStandingsCalculator.compute(
            myUid = "uid_me",
            myName = "Acro",
            friends = listOf(
                FriendUiModel(
                    friendshipId = "f_rob",
                    friendUid = "uid_rob",
                    displayName = "Rob",
                    friendCode = "RCROB001",
                    carType = RcCarType.VOLT_BUGGY,
                    primaryColorArgb = 0xFF00E5FF,
                    accentColorArgb = 0xFFFFFFFF,
                    effectivelyOnline = true,
                    activityStatus = "ONLINE",
                    activeRoomId = "",
                    activeRoomCode = "",
                    activeRoomName = "",
                    statusLabel = "Online"
                )
            ),
            cloudEntries = listOf(
                // Same track at 1L (Leading), 3L (Leading), and 5L (Chasing)
                LeaderboardEntryDoc(
                    id = "me_lr_1", userId = "uid_me", driverName = "Acro",
                    gameMode = GameMode.RACE.name, trackKey = "LIVING_ROOM_GP", boardKey = "RACE_LIVING_ROOM_GP_1L",
                    totalLaps = 1L, bestRaceTimeMs = 10_100L
                ),
                LeaderboardEntryDoc(
                    id = "rob_lr_1", userId = "uid_rob", driverName = "Rob",
                    gameMode = GameMode.RACE.name, trackKey = "LIVING_ROOM_GP", boardKey = "RACE_LIVING_ROOM_GP_1L",
                    totalLaps = 1L, bestRaceTimeMs = 10_600L
                ),
                LeaderboardEntryDoc(
                    id = "me_lr_3", userId = "uid_me", driverName = "Acro",
                    gameMode = GameMode.RACE.name, trackKey = "LIVING_ROOM_GP", boardKey = "RACE_LIVING_ROOM_GP_3L",
                    totalLaps = 3L, bestRaceTimeMs = 30_200L
                ),
                LeaderboardEntryDoc(
                    id = "rob_lr_3", userId = "uid_rob", driverName = "Rob",
                    gameMode = GameMode.RACE.name, trackKey = "LIVING_ROOM_GP", boardKey = "RACE_LIVING_ROOM_GP_3L",
                    totalLaps = 3L, bestRaceTimeMs = 30_950L
                ),
                LeaderboardEntryDoc(
                    id = "me_lr_5", userId = "uid_me", driverName = "Acro",
                    gameMode = GameMode.RACE.name, trackKey = "LIVING_ROOM_GP", boardKey = "RACE_LIVING_ROOM_GP_5L",
                    totalLaps = 5L, bestRaceTimeMs = 51_500L
                ),
                LeaderboardEntryDoc(
                    id = "rob_lr_5", userId = "uid_rob", driverName = "Rob",
                    gameMode = GameMode.RACE.name, trackKey = "LIVING_ROOM_GP", boardKey = "RACE_LIVING_ROOM_GP_5L",
                    totalLaps = 5L, bestRaceTimeMs = 50_800L, hasGhost = true, ghostId = "ghost_rob_5l"
                ),
                // Stunts entry so Stunts filter pill is active
                LeaderboardEntryDoc(
                    id = "me_stunt", userId = "uid_me", driverName = "Acro",
                    gameMode = GameMode.STUNTS.name, trackKey = "STUNTS_GAUNTLET", boardKey = "STUNTS_GAUNTLET_4R",
                    bestScore = 4500L
                ),
                LeaderboardEntryDoc(
                    id = "rob_stunt", userId = "uid_rob", driverName = "Rob",
                    gameMode = GameMode.STUNTS.name, trackKey = "STUNTS_GAUNTLET", boardKey = "STUNTS_GAUNTLET_4R",
                    bestScore = 4100L
                )
            ),
            localRecords = listOf(
                // Solo PB on Skyway Crossover 3L (not raced by Rob yet)
                com.example.model.LocalLeaderboardRecord(
                    boardKey = "RACE_SKYWAY_OVERPASS_3L",
                    gameMode = GameMode.RACE.name,
                    trackKey = "SKYWAY_OVERPASS",
                    totalLaps = 3,
                    driverName = "Acro",
                    carType = RcCarType.VOLT_BUGGY.name,
                    primaryColorArgb = 0xFFFF5722,
                    accentColorArgb = 0xFFFFD54F,
                    bestRaceTimeMs = 34_120L
                )
            ),
            todayDateKey = "20261126"
        )

        val launchedItems = mutableListOf<com.example.data.FriendBoardStandingItem>()

        composeTestRule.setContent {
            MicroRCTheme {
                com.example.ui.screens.FriendStandingsScreen(
                    standings = summary,
                    isSignedIn = true,
                    friendsCount = 1,
                    onOpenManageFriends = {},
                    onLaunchGhostRace = { launchedItems.add(it) },
                    onBack = {}
                )
            }
        }

        // Filter bar sits directly below OVERALL CREW RANKING card on a single horizontal row
        val crewBottom = composeTestRule.onNodeWithTag("standings_crew_ranking_card").fetchSemanticsNode().boundsInRoot.bottom
        val filterBarNode = composeTestRule.onNodeWithTag("standings_filter_bar").fetchSemanticsNode()
        assertTrue(filterBarNode.boundsInRoot.top >= crewBottom - 2f)

        val allPillY = composeTestRule.onNodeWithTag("standings_filter_ALL").fetchSemanticsNode().positionInRoot.y
        val oneLapPillY = composeTestRule.onNodeWithTag("standings_filter_1L").fetchSemanticsNode().positionInRoot.y
        val fiveLapPillY = composeTestRule.onNodeWithTag("standings_filter_5L").fetchSemanticsNode().positionInRoot.y
        assertTrue(kotlin.math.abs(allPillY - oneLapPillY) < 4f)
        assertTrue(kotlin.math.abs(allPillY - fiveLapPillY) < 4f)

        // All view: Chasing (1), Leading (3) [1L, 3L, Stunts], plus Solo PBs showing Skyway Crossover
        composeTestRule.onNodeWithText("Chasing (1)").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Leading (3)").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("standings_solo_section").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Skyway Crossover").performScrollTo().assertIsDisplayed()

        // Tap 1L filter: Chasing becomes empty compact single-line ("Chasing (0) • Clean sweep!"), Leading shows 1 row (1L only)
        composeTestRule.onNodeWithTag("standings_filter_1L").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("standings_chasing_empty_line").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Chasing (0) • Clean sweep!").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("You aren't trailing any friends right now — clean sweep!").assertDoesNotExist()
        composeTestRule.onNodeWithText("Leading (1)").performScrollTo().assertIsDisplayed()

        // Tapping Leading 1L row launches exact 1L race
        composeTestRule.onNodeWithTag("standings_leading_row_0").performScrollTo().performClick()
        assertEquals("RACE_LIVING_ROOM_GP_1L", launchedItems.last().boardKey)
        assertEquals(1, launchedItems.last().totalLaps)

        // Unraced Skyway Crossover 1L still appears under Open Circuits and launches exact 1L race
        composeTestRule.onNodeWithTag("standings_unraced_section").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Skyway Crossover").performScrollTo().performClick()
        assertEquals("RACE_SKYWAY_OVERPASS_1L", launchedItems.last().boardKey)
        assertEquals(1, launchedItems.last().totalLaps)

        // Tap 5L filter: Chasing (1) has 5L, Leading (0), tapping Chasing launches 5L ghost
        composeTestRule.onNodeWithTag("standings_filter_5L").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Chasing (1)").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Leading (0)").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("standings_chasing_row_0").performScrollTo().performClick()
        assertEquals("RACE_LIVING_ROOM_GP_5L", launchedItems.last().boardKey)
        assertEquals(5, launchedItems.last().totalLaps)
    }
}

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
        composeTestRule.onNodeWithTag("home_streak_milestone_banner").assertDoesNotExist()
        composeTestRule.onNodeWithTag("home_standings_rank_badge").assertIsDisplayed()
        composeTestRule.onNodeWithText("#1 of 2 Friends").assertIsDisplayed()
        composeTestRule.onNodeWithText("Lead 1 • Chase 0").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_overall_friends_ranking").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_overall_friend_rank_0").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_standings_board_row_0").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("-1.50s").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_standings_board_row_1").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Neon Hairpin • Custom • 3L").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("home_challenges_section").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_online_section").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_offline_section").assertDoesNotExist()

        composeTestRule.onNodeWithTag("home_standings_board_row_0").performScrollTo().performClick()
        assertEquals(Triple("GP_APEX", GameMode.RACE, false), openedBoard)
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

        composeTestRule.onNodeWithTag("home_streak_standings_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("2 Day Streak").assertIsDisplayed()
        composeTestRule.onNodeWithText("3 Local PBs").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_standings_cta_button").performScrollTo().performClick()
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
    fun homeStandings_capsToTopThreeWithViewAllToggleAndCompactFriendsCardPlacesNotifyInline() {
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
                    id = "uid_me_1",
                    userId = "uid_me",
                    driverName = "Alex",
                    gameMode = GameMode.RACE.name,
                    trackKey = "GP_APEX",
                    boardKey = "RACE_GP_APEX_3L",
                    totalLaps = 3L,
                    bestRaceTimeMs = 34_100L
                ),
                LeaderboardEntryDoc(
                    id = "uid_me_2",
                    userId = "uid_me",
                    driverName = "Alex",
                    gameMode = GameMode.RACE.name,
                    trackKey = "LIVING_ROOM_GP",
                    boardKey = "RACE_LIVING_ROOM_GP_3L",
                    totalLaps = 3L,
                    bestRaceTimeMs = 28_500L
                ),
                LeaderboardEntryDoc(
                    id = "uid_me_3",
                    userId = "uid_me",
                    driverName = "Alex",
                    gameMode = GameMode.RACE.name,
                    trackKey = "WORKSHOP_GP",
                    boardKey = "RACE_WORKSHOP_GP_3L",
                    totalLaps = 3L,
                    bestRaceTimeMs = 30_200L
                ),
                LeaderboardEntryDoc(
                    id = "uid_me_4",
                    userId = "uid_me",
                    driverName = "Alex",
                    gameMode = GameMode.RACE.name,
                    trackKey = "BACKYARD_GP",
                    boardKey = "RACE_BACKYARD_GP_3L",
                    totalLaps = 3L,
                    bestRaceTimeMs = 33_800L
                )
            ),
            localRecords = emptyList(),
            todayDateKey = "20261125"
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
                    isSignedIn = true,
                    friendsCount = 1,
                    friendStandings = summary
                )
            }
        }

        composeTestRule.onNodeWithTag("home_standings_board_row_0").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_standings_board_row_2").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_standings_board_row_3").assertDoesNotExist()
        composeTestRule.onNodeWithTag("home_standings_view_all_toggle").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_standings_board_row_3").performScrollTo().assertIsDisplayed()
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
}

package com.example

import com.example.model.ChallengeInviteResponse
import com.example.model.ChallengeProgressEvaluator
import com.example.model.ChallengedFriendStatus
import com.example.model.FriendPresenceTransitionTracker
import com.example.model.FriendUiModel
import com.example.model.PendingChallengePhase
import com.example.model.PendingFriendChallengeState
import com.example.model.RcCarType
import com.example.model.SessionInviteDoc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FriendOnlineAlertUnitTest {

    private fun friend(
        uid: String,
        name: String = "Riley",
        online: Boolean,
        activityStatus: String = if (online) "ONLINE" else "OFFLINE",
        roomCode: String = "",
        roomName: String = ""
    ) = FriendUiModel(
        friendshipId = "f_$uid",
        friendUid = uid,
        displayName = name,
        friendCode = "RC123456",
        carType = RcCarType.VOLT_BUGGY,
        primaryColorArgb = 0xFF00E5FF,
        accentColorArgb = 0xFFFFD54F,
        effectivelyOnline = online,
        activityStatus = activityStatus,
        activeRoomId = if (roomCode.isNotBlank()) "room_$roomCode" else "",
        activeRoomCode = roomCode,
        activeRoomName = roomName,
        statusLabel = if (online) "Online" else "Offline"
    )

    @Test
    fun initialOnlineFriendsDoNotTrigger_butOfflineToOnlineTransitionTriggersAlert() {
        val tracker = FriendPresenceTransitionTracker(cooldownMs = 60_000L)

        // Initial hydration: Alex already online, Riley offline -> zero alerts
        val initial = tracker.onFriendsUpdated(
            listOf(
                friend(uid = "uid_alex", name = "Alex", online = true),
                friend(uid = "uid_riley", name = "Riley", online = false)
            ),
            nowMs = 1_000L
        )
        assertTrue(initial.isEmpty())

        // Riley comes online while player is gaming -> triggers FriendOnlineAlert
        val afterRileyOnline = tracker.onFriendsUpdated(
            listOf(
                friend(uid = "uid_alex", name = "Alex", online = true),
                friend(uid = "uid_riley", name = "Riley", online = true)
            ),
            nowMs = 5_000L
        )
        assertEquals(1, afterRileyOnline.size)
        assertEquals("uid_riley", afterRileyOnline.single().friendUid)
        assertFalse(afterRileyOnline.single().hasJoinableLobby)

        // Riley opens an online lobby -> alert updates in-place with joinable room code
        val afterRileyLobby = tracker.onFriendsUpdated(
            listOf(
                friend(uid = "uid_alex", name = "Alex", online = true),
                friend(
                    uid = "uid_riley",
                    name = "Riley",
                    online = true,
                    activityStatus = "IN_LOBBY",
                    roomCode = "RACE77",
                    roomName = "Riley's Crew"
                )
            ),
            nowMs = 8_000L
        )
        assertEquals(1, afterRileyLobby.size)
        assertTrue(afterRileyLobby.single().hasJoinableLobby)
        assertEquals("RACE77", afterRileyLobby.single().activeRoomCode)

        // Riley goes offline -> alert clears automatically
        val afterRileyOffline = tracker.onFriendsUpdated(
            listOf(
                friend(uid = "uid_alex", name = "Alex", online = true),
                friend(uid = "uid_riley", name = "Riley", online = false)
            ),
            nowMs = 12_000L
        )
        assertTrue(afterRileyOffline.isEmpty())
    }

    @Test
    fun dismissAndCooldownSuppressImmediateFlap_andAllowAlertAfterCooldown() {
        val tracker = FriendPresenceTransitionTracker(cooldownMs = 120_000L)

        tracker.onFriendsUpdated(listOf(friend(uid = "uid_riley", online = false)), nowMs = 1_000L)
        val triggered = tracker.onFriendsUpdated(listOf(friend(uid = "uid_riley", online = true)), nowMs = 10_000L)
        assertEquals(1, triggered.size)

        val dismissed = tracker.dismiss("uid_riley")
        assertTrue(dismissed.isEmpty())

        // Quick flap offline->online within cooldown does not re-notify
        tracker.onFriendsUpdated(listOf(friend(uid = "uid_riley", online = false)), nowMs = 20_000L)
        val withinCooldown = tracker.onFriendsUpdated(listOf(friend(uid = "uid_riley", online = true)), nowMs = 30_000L)
        assertTrue(withinCooldown.isEmpty())

        // Transition again after cooldown expires -> notifies cleanly
        tracker.onFriendsUpdated(listOf(friend(uid = "uid_riley", online = false)), nowMs = 150_000L)
        val afterCooldown = tracker.onFriendsUpdated(listOf(friend(uid = "uid_riley", online = true)), nowMs = 160_000L)
        assertEquals(1, afterCooldown.size)

        tracker.reset()
        val afterReset = tracker.onFriendsUpdated(listOf(friend(uid = "uid_riley", online = true)), nowMs = 200_000L)
        assertTrue(afterReset.isEmpty())
    }

    @Test
    fun challengeProgressEvaluator_handlesMultiFriendPartialDeclineAndFirstAccept() {
        val waitingState = PendingFriendChallengeState(
            challengeId = "ch_1",
            roomId = "room_CH01",
            roomCode = "CH01",
            ownsStandbyRoom = true,
            targets = listOf(
                ChallengedFriendStatus(friendUid = "uid_riley", displayName = "Riley", inviteId = "inv_1"),
                ChallengedFriendStatus(friendUid = "uid_alex", displayName = "Alex", inviteId = "inv_2")
            ),
            totalSeconds = 60,
            remainingSeconds = 42,
            phase = PendingChallengePhase.WAITING
        )

        // 1. Riley declines, Alex still pending -> stay WAITING
        val afterPartialDecline = ChallengeProgressEvaluator.applyInviteUpdates(
            state = waitingState,
            invites = listOf(
                SessionInviteDoc(id = "inv_1", status = "DECLINED"),
                SessionInviteDoc(id = "inv_2", status = "PENDING")
            )
        )
        assertNull(afterPartialDecline.acceptedTarget)
        assertFalse(afterPartialDecline.allDeclined)
        assertEquals(PendingChallengePhase.WAITING, afterPartialDecline.updatedState.phase)
        assertEquals(ChallengeInviteResponse.DECLINED, afterPartialDecline.updatedState.targets[0].response)
        assertEquals(ChallengeInviteResponse.PENDING, afterPartialDecline.updatedState.targets[1].response)

        // 2. Alex accepts -> acceptedTarget returned immediately
        val afterAccept = ChallengeProgressEvaluator.applyInviteUpdates(
            state = afterPartialDecline.updatedState,
            invites = listOf(
                SessionInviteDoc(id = "inv_1", status = "DECLINED"),
                SessionInviteDoc(id = "inv_2", status = "ACCEPTED")
            )
        )
        assertNotNull(afterAccept.acceptedTarget)
        assertEquals("uid_alex", afterAccept.acceptedTarget?.friendUid)
        assertFalse(afterAccept.allDeclined)
    }

    @Test
    fun challengeProgressEvaluator_marksAllDeclinedOnlyWhenEveryTargetDeclines() {
        val waitingState = PendingFriendChallengeState(
            challengeId = "ch_2",
            roomId = "room_CH02",
            roomCode = "CH02",
            ownsStandbyRoom = true,
            targets = listOf(
                ChallengedFriendStatus(friendUid = "uid_riley", displayName = "Riley", inviteId = "inv_1"),
                ChallengedFriendStatus(friendUid = "uid_alex", displayName = "Alex", inviteId = "inv_2")
            ),
            totalSeconds = 60,
            remainingSeconds = 30,
            phase = PendingChallengePhase.WAITING
        )

        val allDeclinedRes = ChallengeProgressEvaluator.applyInviteUpdates(
            state = waitingState,
            invites = listOf(
                SessionInviteDoc(id = "inv_1", status = "DECLINED"),
                SessionInviteDoc(id = "inv_2", status = "DECLINED")
            )
        )
        assertNull(allDeclinedRes.acceptedTarget)
        assertTrue(allDeclinedRes.allDeclined)
        assertEquals(PendingChallengePhase.ALL_DECLINED, allDeclinedRes.updatedState.phase)

        val expiredRes = ChallengeProgressEvaluator.applyInviteUpdates(
            state = waitingState,
            invites = listOf(
                SessionInviteDoc(id = "inv_1", status = "EXPIRED"),
                SessionInviteDoc(id = "inv_2", status = "EXPIRED")
            )
        )
        assertFalse(expiredRes.allDeclined)
        assertEquals(ChallengeInviteResponse.EXPIRED, expiredRes.updatedState.targets[0].response)
    }
}

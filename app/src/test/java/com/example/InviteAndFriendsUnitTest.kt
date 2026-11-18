package com.example

import com.example.model.DriverFriendCode
import com.example.model.DriverProfileDoc
import com.example.model.FriendshipDoc
import com.example.util.InviteLinkHelper
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class InviteAndFriendsUnitTest {

    @Test
    fun inviteLink_buildsAndParsesWhatsAppMessageWithToken() {
        val token = "a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90"
        val shareText = InviteLinkHelper.buildShareMessage(
            roomName = "Alex's Crew",
            roomCode = "k7m9xp",
            gameMode = "RACE",
            hostName = "Alex",
            joinToken = token
        )
        assertTrue(shareText.contains("https://microrc.game/join?code=K7M9XP"))

        val parsed = InviteLinkHelper.parseInviteFromText(shareText)
        assertNotNull(parsed)
        assertEquals("K7M9XP", parsed!!.roomCode)
        assertEquals(token, parsed.joinToken)
    }

    @Test
    fun inviteLink_parsesCustomSchemeAndRawRoomCode() {
        val customScheme = InviteLinkHelper.parseInviteFromText("microrc://join?code=rc99ab&inviteId=inv_123")
        assertNotNull(customScheme)
        assertEquals("RC99AB", customScheme!!.roomCode)
        assertEquals("inv_123", customScheme.inviteId)

        val rawCode = InviteLinkHelper.parseInviteFromText("m4p8qz")
        assertNotNull(rawCode)
        assertEquals("M4P8QZ", rawCode!!.roomCode)
    }

    @Test
    fun driverFriendCode_generatesValidDeterministicCodeAndPresenceStates() {
        val codeA = DriverFriendCode.fromUid("user_alpha_123")
        val codeA2 = DriverFriendCode.fromUid("user_alpha_123")
        val codeB = DriverFriendCode.fromUid("user_bravo_456")

        assertEquals(codeA, codeA2)
        assertEquals(8, codeA.length)
        assertTrue(codeA.matches(Regex("^[A-Z0-9]+$")))
        assertFalse(codeA == codeB)

        assertEquals(
            DriverFriendCode.buildCanonicalFriendshipId("uid_z", "uid_a"),
            DriverFriendCode.buildCanonicalFriendshipId("uid_a", "uid_z")
        )

        val nowSec = 1_700_000_000L
        val friendship = FriendshipDoc(
            id = "uid_a_uid_b",
            memberUids = listOf("uid_a", "uid_b"),
            requesterUid = "uid_a",
            requesterName = "Alpha",
            requesterFriendCode = "RC111111",
            receiverUid = "uid_b",
            receiverName = "Bravo",
            receiverFriendCode = "RC222222",
            status = "ACCEPTED"
        )

        val onlineLobbyProfile = DriverProfileDoc(
            id = "uid_b",
            userId = "uid_b",
            driverName = "Bravo",
            searchName = "bravo",
            friendCode = "RC222222",
            online = true,
            activityStatus = "IN_LOBBY",
            activeRoomId = "room_K7M9XP",
            activeRoomCode = "K7M9XP",
            activeRoomName = "Night Cup",
            lastSeenAt = Timestamp(nowSec - 20L, 0)
        )
        val uiOnline = DriverFriendCode.toFriendUiModel(friendship, "uid_a", onlineLobbyProfile, nowSec)
        assertTrue(uiOnline.effectivelyOnline)
        assertTrue(uiOnline.canJoinActiveLobby)
        assertTrue(uiOnline.statusLabel.contains("K7M9XP"))

        val staleProfile = onlineLobbyProfile.copy(lastSeenAt = Timestamp(nowSec - 600L, 0))
        val uiStale = DriverFriendCode.toFriendUiModel(friendship, "uid_a", staleProfile, nowSec)
        assertFalse(uiStale.effectivelyOnline)
        assertFalse(uiStale.canJoinActiveLobby)
        assertTrue(uiStale.statusLabel.startsWith("Offline"))
    }
}

package com.example

import com.example.data.TrackCatalog
import com.example.engine.RacePhysicsEngine
import com.example.model.ControlInput
import com.example.model.LobbyPlayer
import com.example.model.OnlineRoomInputDoc
import com.example.model.RacePhase
import com.example.model.RaceRulesSettings
import com.example.model.RcCarType
import com.example.model.TrackId
import com.example.network.OnlineRaceSessionManager
import com.example.network.OnlineRelaySocket
import com.example.network.RaceTelemetryCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan2
import kotlin.random.Random

class OnlineMultiplayerSyncUnitTest {

    @Test
    fun relaySocket_configuresUrlAndOkHttpKeepaliveTimeouts() {
        assertEquals(
            "wss://microrc.hankers.aero/?raceId=room_AB12CD",
            OnlineRelaySocket.buildRelayUrl("room_AB12CD")
        )
        val client = OnlineRelaySocket.createDefaultClient()
        assertEquals(15_000, client.pingIntervalMillis)
        assertEquals(0, client.readTimeoutMillis)
    }

    @Test
    fun codec_roundTripsRelayPingPongAndSequencedInputFrames() {
        val ping = RaceTelemetryCodec.encodeRelayPing("client_uid", 987_654_321L)
        val decodedPing = RaceTelemetryCodec.decodeRelayPing(ping)
        assertNotNull(decodedPing)
        assertEquals("client_uid", decodedPing!!.senderId)
        assertEquals(987_654_321L, decodedPing.timestampNs)

        val pong = RaceTelemetryCodec.encodeRelayPong("client_uid", 987_654_321L)
        val decodedPong = RaceTelemetryCodec.decodeRelayPong(pong)
        assertNotNull(decodedPong)
        assertEquals("client_uid", decodedPong!!.targetSenderId)
        assertEquals(987_654_321L, decodedPong.timestampNs)

        // 10-field WS frame carries powerUpSeq & mineDropSeq for seamless fallback parity
        val wsInputLine = RaceTelemetryCodec.encodeInputLine(
            playerId = "p2",
            seq = 42L,
            input = ControlInput(throttle = 1f, steer = -0.5f, usePowerUp = true, dropMine = false),
            ackedHostUdpSeq = 100L,
            powerUpSeq = 3L,
            mineDropSeq = 1L
        )
        val decodedWs = RaceTelemetryCodec.decodeInput(wsInputLine)
        assertNotNull(decodedWs)
        assertEquals(42L, decodedWs!!.seq)
        assertEquals(3L, decodedWs.powerUpSeq)
        assertEquals(1L, decodedWs.mineDropSeq)
        assertTrue(decodedWs.input.usePowerUp)
        assertFalse(decodedWs.input.dropMine)

        // 8-field Local Wi-Fi frame keeps null action sequence numbers
        val wifiLine = RaceTelemetryCodec.encodeInputLine(
            playerId = "p2",
            seq = 7L,
            input = ControlInput(throttle = 1f, steer = 0.2f, usePowerUp = false, dropMine = true),
            ackedHostUdpSeq = 12L
        )
        val decodedWifi = RaceTelemetryCodec.decodeInput(wifiLine)
        assertNotNull(decodedWifi)
        assertNull(decodedWifi!!.powerUpSeq)
        assertNull(decodedWifi.mineDropSeq)
        assertTrue(decodedWifi.input.dropMine)
    }

    @Test
    fun hostInputSequencer_seamlessHandoffBetweenWebSocketAndFirestoreFallback() {
        val inputSeqs = mutableMapOf<String, Long>()
        val powerSeqs = mutableMapOf<String, Long>()
        val mineSeqs = mutableMapOf<String, Long>()

        // Baseline initial packet over WS
        val initWs = RaceTelemetryCodec.decodeInput(
            RaceTelemetryCodec.encodeInputLine("friend_1", 1L, ControlInput(1f, 0f), 0L, 0L, 0L)
        )!!
        assertTrue(OnlineRaceSessionManager.evaluateIncomingHostRelayInput(initWs, inputSeqs, powerSeqs, mineSeqs).accepted)

        // Power-up triggered over WS (powerUpSeq=1, seq=2)
        val powerWs = RaceTelemetryCodec.decodeInput(
            RaceTelemetryCodec.encodeInputLine("friend_1", 2L, ControlInput(1f, -0.4f, usePowerUp = true), 5L, 1L, 0L)
        )!!
        val wsDecision = OnlineRaceSessionManager.evaluateIncomingHostRelayInput(powerWs, inputSeqs, powerSeqs, mineSeqs)
        assertTrue(wsDecision.accepted)
        assertTrue(wsDecision.controlInput!!.usePowerUp)

        // WS drops right after; Firestore fallback sends seq=3 carrying powerUpSeq=1 — must NOT double-trigger power-up!
        val fallbackDoc = OnlineRoomInputDoc(
            userId = "friend_1",
            seq = 3L,
            throttle = 1.0,
            steer = 0.0,
            powerUpSeq = 1L,
            mineDropSeq = 0L
        )
        val fallbackDecision = OnlineRaceSessionManager.evaluateIncomingHostInput(fallbackDoc, inputSeqs, powerSeqs, mineSeqs)
        assertTrue(fallbackDecision.accepted)
        assertFalse("Power-up already applied via WS must not fire twice on Firestore fallback", fallbackDecision.controlInput!!.usePowerUp)
        assertEquals(0f, fallbackDecision.controlInput!!.steer, 0.001f)
    }

    @Test
    fun hostFallbackDetector_mirrorsOnlyWhenRelayDownOrClientSilent() {
        val startNs = 1_000_000_000L
        val humans = setOf("friend_1")

        // During initial 1.5s countdown handshake with WS up, do not flood Firestore
        assertFalse(
            OnlineRaceSessionManager.shouldHostMirrorToFirestore(
                hostRelayConnected = true,
                remoteHumanIds = humans,
                lastClientRelayRxNsByUser = emptyMap(),
                nowNs = startNs + 500_000_000L,
                raceStartedAtNs = startNs
            )
        )

        // While client is active on WS (<1.5s since last WS packet), skip Firestore telemetry writes
        assertFalse(
            OnlineRaceSessionManager.shouldHostMirrorToFirestore(
                hostRelayConnected = true,
                remoteHumanIds = humans,
                lastClientRelayRxNsByUser = mapOf("friend_1" to startNs + 3_000_000_000L),
                nowNs = startNs + 3_400_000_000L,
                raceStartedAtNs = startNs
            )
        )

        // If client goes silent >1.5s (asymmetric WS drop), mirror to Firestore automatically
        assertTrue(
            OnlineRaceSessionManager.shouldHostMirrorToFirestore(
                hostRelayConnected = true,
                remoteHumanIds = humans,
                lastClientRelayRxNsByUser = mapOf("friend_1" to startNs + 2_000_000_000L),
                nowNs = startNs + 4_000_000_000L,
                raceStartedAtNs = startNs
            )
        )

        // If host WS itself drops, mirror to Firestore immediately
        assertTrue(
            OnlineRaceSessionManager.shouldHostMirrorToFirestore(
                hostRelayConnected = false,
                remoteHumanIds = humans,
                lastClientRelayRxNsByUser = mapOf("friend_1" to startNs + 3_900_000_000L),
                nowNs = startNs + 4_000_000_000L,
                raceStartedAtNs = startNs
            )
        )
    }

    @Test
    fun hostInputSequencer_rejectsOutOfOrderAndDuplicatePacketsWithoutDroppingPowerups() {
        val inputSeqs = mutableMapOf<String, Long>()
        val powerSeqs = mutableMapOf<String, Long>()
        val mineSeqs = mutableMapOf<String, Long>()

        val first = OnlineRaceSessionManager.evaluateIncomingHostInput(
            doc = OnlineRoomInputDoc(
                userId = "friend_1",
                seq = 10L,
                throttle = 1.0,
                steer = -0.8,
                powerUpSeq = 1L,
                mineDropSeq = 0L
            ),
            appliedInputSeqByUser = inputSeqs,
            appliedPowerSeqByUser = powerSeqs,
            appliedMineSeqByUser = mineSeqs
        )
        assertTrue(first.accepted)
        assertEquals(-0.8f, first.controlInput?.steer ?: 0f, 0.001f)
        // First packet initializes baseline powerUpSeq without spuriously triggering on join
        assertFalse(first.controlInput?.usePowerUp ?: true)

        // Newer centered steering arrives first (seq 12)
        val newer = OnlineRaceSessionManager.evaluateIncomingHostInput(
            doc = OnlineRoomInputDoc(
                userId = "friend_1",
                seq = 12L,
                throttle = 1.0,
                steer = 0.0,
                powerUpSeq = 1L,
                mineDropSeq = 0L
            ),
            appliedInputSeqByUser = inputSeqs,
            appliedPowerSeqByUser = powerSeqs,
            appliedMineSeqByUser = mineSeqs
        )
        assertTrue(newer.accepted)
        assertEquals(0.0f, newer.controlInput?.steer ?: 99f, 0.001f)

        // Stale out-of-order packet (seq 11) with old left steer must be ignored
        val stale = OnlineRaceSessionManager.evaluateIncomingHostInput(
            doc = OnlineRoomInputDoc(
                userId = "friend_1",
                seq = 11L,
                throttle = 1.0,
                steer = -1.0,
                powerUpSeq = 1L,
                mineDropSeq = 0L
            ),
            appliedInputSeqByUser = inputSeqs,
            appliedPowerSeqByUser = powerSeqs,
            appliedMineSeqByUser = mineSeqs
        )
        assertFalse("Out-of-order older input seq must never override newer steer", stale.accepted)

        // Duplicate seq 12 without new powerup/mine must be ignored
        val dup = OnlineRaceSessionManager.evaluateIncomingHostInput(
            doc = OnlineRoomInputDoc(
                userId = "friend_1",
                seq = 12L,
                throttle = 1.0,
                steer = 0.0,
                powerUpSeq = 1L,
                mineDropSeq = 0L
            ),
            appliedInputSeqByUser = inputSeqs,
            appliedPowerSeqByUser = powerSeqs,
            appliedMineSeqByUser = mineSeqs
        )
        assertFalse(dup.accepted)

        // Powerup edge + seq 13 triggers cleanly
        val withPower = OnlineRaceSessionManager.evaluateIncomingHostInput(
            doc = OnlineRoomInputDoc(
                userId = "friend_1",
                seq = 13L,
                throttle = 1.0,
                steer = 0.25,
                powerUpSeq = 2L,
                mineDropSeq = 1L
            ),
            appliedInputSeqByUser = inputSeqs,
            appliedPowerSeqByUser = powerSeqs,
            appliedMineSeqByUser = mineSeqs
        )
        assertTrue(withPower.accepted)
        assertNotNull(withPower.controlInput)
        assertTrue(withPower.controlInput!!.usePowerUp)
        assertTrue(withPower.controlInput!!.dropMine)
        assertEquals(0.25f, withPower.controlInput!!.steer, 0.001f)
    }

    @Test
    fun raceFinishesPromptlyWhenLeaderCrossesGoalAndRivalIsStalledAtWall() {
        val track = TrackCatalog.getTrack(TrackId.LIVING_ROOM_GP).copy(
            ramps = emptyList(),
            itemSpawns = emptyList(),
            props = emptyList()
        )
        val players = listOf(
            LobbyPlayer("host", "Host", isHost = true, isBot = false, carType = RcCarType.APEX_FORMULA.name),
            LobbyPlayer("friend", "Friend", isHost = false, isBot = false, carType = RcCarType.VOLT_BUGGY.name)
        )
        val engine = RacePhysicsEngine(
            track = track,
            totalLaps = 1,
            players = players,
            rules = RaceRulesSettings(autoAccelerate = true, randomConditionsEnabled = false),
            random = Random(123L)
        )

        // Advance past countdown
        repeat(70) { engine.step(0.05f) }

        var snap = engine.step(0.016f)
        var hostFinishedAtMs: Long? = null
        var steps = 0
        while (snap.phase != RacePhase.FINISHED.name && steps < 2500) {
            val host = snap.racers.first { it.playerId == "host" }
            val (idx, _) = TrackCatalog.nearestTrackPoint(track, host.x, host.y)
            val target = track.centerline[(idx + 7) % track.centerline.size]
            val desired = atan2(target.y - host.y, target.x - host.x)
            val diff = RacePhysicsEngine.normalizeAngle(desired - host.angleRad)
            engine.submitInput("host", ControlInput(throttle = 1f, steer = (diff / 0.6f).coerceIn(-1f, 1f)))
            // Friend brakes / stalls against wall
            engine.submitInput("friend", ControlInput(throttle = 0f, steer = 0f))

            snap = engine.step(0.02f)
            val updatedHost = snap.racers.first { it.playerId == "host" }
            if (updatedHost.finished && hostFinishedAtMs == null) {
                hostFinishedAtMs = snap.raceTimeMs
                // Immediately on crossing finish, grace window should still be open for 1 tick
                assertEquals(RacePhase.RACING.name, snap.phase)
            }
            steps++
        }

        assertEquals(
            "Race must transition to FINISHED shortly after host crosses goal when rival is stalled",
            RacePhase.FINISHED.name,
            snap.phase
        )
        assertNotNull(hostFinishedAtMs)
        val waitAfterFinishMs = snap.raceTimeMs - hostFinishedAtMs!!
        assertTrue(
            "Stalled rival finish grace ($waitAfterFinishMs ms) should finish faster than full 10s timeout",
            waitAfterFinishMs <= RacePhysicsEngine.STALLED_RIVAL_MIN_GRACE_MS + 500L
        )
    }

    @Test
    fun circuitWallCollision_deflectsAutoGasCarAlongTrackInsteadOfPinningNoseFirst() {
        val track = TrackCatalog.getTrack(TrackId.NEON_ROOFTOP).copy(
            ramps = emptyList(),
            itemSpawns = emptyList(),
            props = emptyList()
        )
        val players = listOf(
            LobbyPlayer("p1", "Friend", isHost = false, isBot = false, carType = RcCarType.VOLT_BUGGY.name)
        )
        val engine = RacePhysicsEngine(
            track = track,
            totalLaps = 3,
            players = players,
            rules = RaceRulesSettings(autoAccelerate = true, hardTrackBoundary = true, randomConditionsEnabled = false),
            random = Random(77L)
        )
        repeat(70) { engine.step(0.05f) }

        // Steer hard right into wall for 1.5s, then release steering (steer=0f) under auto-gas
        var snap = engine.step(0.02f)
        repeat(75) {
            engine.submitInput("p1", ControlInput(throttle = 1f, steer = 1f))
            snap = engine.step(0.02f)
        }
        val posBeforeRelease = snap.racers.first()

        // Release steering (neutral steer=0f, auto-gas throttle=1f) — car must glide along wall and make progress
        repeat(100) {
            engine.submitInput("p1", ControlInput(throttle = 1f, steer = 0f))
            snap = engine.step(0.02f)
        }
        val after = snap.racers.first()
        val movedDist = TrackCatalog.distance(posBeforeRelease.x, posBeforeRelease.y, after.x, after.y)
        assertTrue(
            "Auto-gas car should deflect and keep moving along track rather than staying pinned at wall (moved=$movedDist, speed=${after.speed})",
            movedDist > 120f && after.speed > 75f
        )
    }
}

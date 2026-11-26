package com.example.network

import com.example.data.TrackCatalog
import com.example.model.ActiveRocketState
import com.example.model.ArenaFlagState
import com.example.model.ArenaShape
import com.example.model.ControlInput
import com.example.model.ExternalEventType
import com.example.model.FieldPickupType
import com.example.model.GameMode
import com.example.model.HazardType
import com.example.model.ItemBoxState
import com.example.model.LobbyPlayer
import com.example.model.PortalPairState
import com.example.model.PowerUpType
import com.example.model.RacePhase
import com.example.model.RaceSnapshot
import com.example.model.RacerState
import com.example.model.StuntGolfHole
import com.example.model.StuntObstacleState
import com.example.model.StuntPinState
import com.example.model.StuntStateSnapshot
import com.example.model.StuntVariant
import com.example.model.TrackDefinition
import com.example.model.TrackExplosion
import com.example.model.TrackFieldPickup
import com.example.model.TrackHazard
import com.example.model.TrackId
import com.example.model.TrackPuddle
import com.example.model.WinCondition
import java.util.Locale

object RaceTelemetryCodec {

    data class DecodedHello(
        val playerId: String,
        val ackedHostUdpSeq: Long = -1L
    )

    data class DecodedInput(
        val playerId: String,
        val seq: Long,
        val input: ControlInput,
        val ackedHostUdpSeq: Long = -1L,
        val powerUpSeq: Long? = null,
        val mineDropSeq: Long? = null
    )

    data class DecodedRelayPing(
        val senderId: String,
        val timestampNs: Long
    )

    data class DecodedRelayPong(
        val targetSenderId: String,
        val timestampNs: Long
    )

    data class DecodedSnapshot(
        val seq: Long,
        val snapshot: RaceSnapshot
    )

    private fun sanitizeToken(raw: String): String =
        raw.replace("|", "")
            .replace(";", "")
            .replace(",", "")
            .replace("~", "")
            .replace(":", "")
            .replace("\n", "")
            .replace("\r", "")

    fun encodeHello(playerId: String, ackedHostUdpSeq: Long = -1L): ByteArray =
        "H|${sanitizeToken(playerId)}|$ackedHostUdpSeq".toByteArray(Charsets.UTF_8)

    fun decodeHelloPacket(text: String): DecodedHello? {
        if (!text.startsWith("H|")) return null
        val parts = text.split('|')
        val pid = parts.getOrNull(1)?.trim()?.ifEmpty { null } ?: return null
        val ackSeq = parts.getOrNull(2)?.trim()?.toLongOrNull() ?: -1L
        return DecodedHello(pid, ackSeq)
    }

    fun decodeHello(text: String): String? = decodeHelloPacket(text)?.playerId

    fun encodeUdpAck(seq: Long): ByteArray =
        "A|$seq".toByteArray(Charsets.UTF_8)

    fun isUdpAck(text: String): Boolean = text.startsWith("A|")

    fun encodeRelayPing(senderId: String, timestampNs: Long): String =
        "P|${sanitizeToken(senderId)}|$timestampNs"

    fun decodeRelayPing(text: String): DecodedRelayPing? {
        if (!text.startsWith("P|")) return null
        val parts = text.split('|')
        val senderId = parts.getOrNull(1)?.trim()?.ifEmpty { null } ?: return null
        val timestampNs = parts.getOrNull(2)?.trim()?.toLongOrNull() ?: return null
        return DecodedRelayPing(senderId = senderId, timestampNs = timestampNs)
    }

    fun encodeRelayPong(targetSenderId: String, timestampNs: Long): String =
        "Q|${sanitizeToken(targetSenderId)}|$timestampNs"

    fun decodeRelayPong(text: String): DecodedRelayPong? {
        if (!text.startsWith("Q|")) return null
        val parts = text.split('|')
        val targetSenderId = parts.getOrNull(1)?.trim()?.ifEmpty { null } ?: return null
        val timestampNs = parts.getOrNull(2)?.trim()?.toLongOrNull() ?: return null
        return DecodedRelayPong(targetSenderId = targetSenderId, timestampNs = timestampNs)
    }

    fun encodeInputLine(
        playerId: String,
        seq: Long,
        input: ControlInput,
        ackedHostUdpSeq: Long = -1L,
        powerUpSeq: Long? = null,
        mineDropSeq: Long? = null
    ): String {
        val t = String.format(Locale.US, "%.2f", input.throttle)
        val s = String.format(Locale.US, "%.3f", input.steer)
        val p = if (input.usePowerUp) "1" else "0"
        val m = if (input.dropMine) "1" else "0"
        val base = "I|${sanitizeToken(playerId)}|$seq|$t|$s|$p|$m|$ackedHostUdpSeq"
        return if (powerUpSeq != null && mineDropSeq != null) {
            "$base|$powerUpSeq|$mineDropSeq"
        } else {
            base
        }
    }

    fun encodeInput(
        playerId: String,
        seq: Long,
        input: ControlInput,
        ackedHostUdpSeq: Long = -1L
    ): ByteArray = encodeInputLine(playerId, seq, input, ackedHostUdpSeq).toByteArray(Charsets.UTF_8)

    fun decodeInput(text: String): DecodedInput? {
        if (!text.startsWith("I|")) return null
        val parts = text.split('|')
        if (parts.size < 6) return null
        val playerId = parts[1].trim()
        if (playerId.isEmpty()) return null
        val seq = parts[2].toLongOrNull() ?: return null
        val throttle = parts[3].toFloatOrNull() ?: 0f
        val steer = parts[4].toFloatOrNull() ?: 0f
        val usePower = parts[5] == "1"
        val dropMine = parts.getOrNull(6) == "1"
        val ackedHostSeq = parts.getOrNull(7)?.trim()?.toLongOrNull() ?: -1L
        val powerUpSeq = parts.getOrNull(8)?.trim()?.toLongOrNull()
        val mineDropSeq = parts.getOrNull(9)?.trim()?.toLongOrNull()
        return DecodedInput(
            playerId = playerId,
            seq = seq,
            input = ControlInput(throttle, steer, usePower, dropMine),
            ackedHostUdpSeq = ackedHostSeq,
            powerUpSeq = powerUpSeq,
            mineDropSeq = mineDropSeq
        )
    }

    fun encodeSnapshot(seq: Long, snap: RaceSnapshot): ByteArray {
        val sb = StringBuilder(680)
        val phaseOrd = runCatching { RacePhase.valueOf(snap.phase).ordinal }.getOrDefault(0)
        val trackOrd = runCatching { TrackId.valueOf(snap.trackId).ordinal }.getOrDefault(0)
        val banner = (snap.eventBanner ?: "")
            .replace("|", "")
            .replace("\n", "")
            .replace("\r", "")
        val winOrd = runCatching { WinCondition.valueOf(snap.winCondition).ordinal }.getOrDefault(0)
        val evOrd = runCatching { ExternalEventType.valueOf(snap.activeEvent).ordinal }.getOrDefault(0)
        val modeOrd = runCatching { GameMode.valueOf(snap.gameMode).ordinal }.getOrDefault(0)
        val arenaOrd = snap.arenaShape?.let {
            runCatching { ArenaShape.valueOf(it).ordinal }.getOrDefault(-1)
        } ?: -1
        val flag = snap.arenaFlag
        val flagCarrier = sanitizeToken(flag?.carrierId ?: "")

        sb.append("S|")
            .append(seq).append('|')
            .append(snap.raceTimeMs).append('|')
            .append(snap.countdownSec).append('|')
            .append(phaseOrd).append('|')
            .append(snap.totalLaps).append('|')
            .append(trackOrd).append('|')
            .append(banner).append('|')

        // Racers (index 8)
        snap.racers.forEachIndexed { idx, r ->
            if (idx > 0) sb.append(';')
            val heldOrd = r.heldPowerUp?.let {
                runCatching { PowerUpType.valueOf(it).ordinal }.getOrDefault(-1)
            } ?: -1
            sb.append(sanitizeToken(r.playerId)).append(',')
                .append(String.format(Locale.US, "%.1f", r.x)).append(',')
                .append(String.format(Locale.US, "%.1f", r.y)).append(',')
                .append(String.format(Locale.US, "%.3f", r.angleRad)).append(',')
                .append(String.format(Locale.US, "%.1f", r.vx)).append(',')
                .append(String.format(Locale.US, "%.1f", r.vy)).append(',')
                .append(String.format(Locale.US, "%.2f", r.steerVisual)).append(',')
                .append(r.lap).append(',')
                .append(r.rank).append(',')
                .append(heldOrd).append(',')
                .append(r.boostMs).append(',')
                .append(r.shieldMs).append(',')
                .append(r.stunnedMs).append(',')
                .append(r.overdriveMs).append(',')
                .append(String.format(Locale.US, "%.2f", r.spinVisualOffset)).append(',')
                .append(r.powerUpHits).append(',')
                .append(if (r.finished) 1 else 0).append(',')
                .append(r.finishTimeMs ?: -1L).append(',')
                .append(r.bestLapMs ?: -1L).append(',')
                .append(r.currentLapStartMs).append(',')
                .append(r.lapTimesMs.joinToString("-")).append(',')
                .append(String.format(Locale.US, "%.2f", r.jumpZ)).append(',')
                .append(r.racePoints).append(',')
                .append(r.flagCarryMs).append(',')
                .append(r.minesAvailable).append(',')
                .append(r.invertedSteerMs).append(',')
                .append(r.collisionSeq).append(',')
                .append(r.portalWarpSeq).append(',')
                .append(r.electroShockAuraMs).append(',')
                .append(r.electroParalyzedMs).append(',')
                .append(r.laserBeamMs).append(',')
                .append(if (r.isBraking) 1 else 0).append(',')
                .append(r.equippedUpgradesMask)
        }
        sb.append('|')

        // Item boxes with live randomized coordinates (index 9)
        snap.itemBoxes.forEachIndexed { idx, b ->
            if (idx > 0) sb.append('~')
            sb.append(b.id).append(':')
                .append(String.format(Locale.US, "%.1f", b.x)).append(':')
                .append(String.format(Locale.US, "%.1f", b.y)).append(':')
                .append(if (b.available) 1 else 0)
        }
        sb.append('|')

        // Hazards (index 10)
        snap.hazards.forEachIndexed { idx, h ->
            if (idx > 0) sb.append(';')
            sb.append(h.id).append(',')
                .append(sanitizeToken(h.ownerId)).append(',')
                .append(String.format(Locale.US, "%.1f", h.x)).append(',')
                .append(String.format(Locale.US, "%.1f", h.y)).append(',')
                .append(h.remainingMs).append(',')
                .append(h.hazardTypeEnum.ordinal)
        }
        sb.append('|')

        // Match, Weather Event & Arena Flag meta (index 11)
        sb.append(winOrd).append(',')
            .append(snap.timeLimitSec).append(',')
            .append(evOrd).append(',')
            .append(snap.eventRemainingMs).append(',')
            .append(String.format(Locale.US, "%.3f", snap.windAngleRad)).append(',')
            .append(String.format(Locale.US, "%.2f", snap.windStrength)).append(',')
            .append(snap.postFinishGraceMs).append(',')
            .append(modeOrd).append(',')
            .append(arenaOrd).append(',')
            .append(snap.arenaTargetMs).append(',')
            .append(flagCarrier).append(',')
            .append(String.format(Locale.US, "%.1f", flag?.x ?: 1100f)).append(',')
            .append(String.format(Locale.US, "%.1f", flag?.y ?: 750f)).append(',')
            .append(snap.arenaSuddenDeathStage).append(',')
            .append(snap.arenaSuddenDeathRemainingMs).append(',')
            .append(if (snap.isHybridTour) 1 else 0).append(',')
            .append(snap.hybridStageNumber).append(',')
            .append(sanitizeToken(snap.nextHybridMode ?: ""))
        sb.append('|')

        // Rain puddles (index 12)
        snap.rainPuddles.forEachIndexed { idx, p ->
            if (idx > 0) sb.append(';')
            sb.append(String.format(Locale.US, "%.0f", p.x)).append(',')
                .append(String.format(Locale.US, "%.0f", p.y)).append(',')
                .append(String.format(Locale.US, "%.0f", p.radius))
        }
        sb.append('|')

        // Direct-trigger field pickups (index 13)
        snap.fieldPickups.forEachIndexed { idx, fp ->
            if (idx > 0) sb.append(';')
            sb.append(fp.id).append(',')
                .append(String.format(Locale.US, "%.1f", fp.x)).append(',')
                .append(String.format(Locale.US, "%.1f", fp.y)).append(',')
                .append(fp.pickupTypeEnum.ordinal).append(',')
                .append(fp.remainingMs)
        }
        sb.append('|')

        // Active Portal Pair (index 14)
        val pp = snap.portalPair
        if (pp != null) {
            sb.append(pp.id).append(',')
                .append(String.format(Locale.US, "%.1f", pp.entryX)).append(',')
                .append(String.format(Locale.US, "%.1f", pp.entryY)).append(',')
                .append(if (pp.entryColorIsRed) 1 else 0).append(',')
                .append(String.format(Locale.US, "%.1f", pp.exitX)).append(',')
                .append(String.format(Locale.US, "%.1f", pp.exitY)).append(',')
                .append(String.format(Locale.US, "%.3f", pp.exitAngleRad)).append(',')
                .append(pp.remainingMs)
        }
        sb.append('|')

        // Active Mine Explosions (index 15)
        snap.explosions.forEachIndexed { idx, ex ->
            if (idx > 0) sb.append(';')
            sb.append(ex.id).append(',')
                .append(String.format(Locale.US, "%.1f", ex.x)).append(',')
                .append(String.format(Locale.US, "%.1f", ex.y)).append(',')
                .append(ex.ageMs).append(',')
                .append(ex.durationMs)
        }
        sb.append('|')

        // Stunt Mode State (index 16)
        val st = snap.stuntState
        if (st != null) {
            val varOrd = st.variantEnum.ordinal
            val safeSummary = st.lastAttemptSummary
                .replace("|", "")
                .replace(";", "")
                .replace(",", "")
                .replace("~", "")
                .replace("\n", "")
                .replace("\r", "")
            sb.append(varOrd).append(',')
                .append(st.roundIndex).append(',')
                .append(st.totalRounds).append(',')
                .append(st.turnIndex).append(',')
                .append(sanitizeToken(st.activePlayerId)).append(',')
                .append(sanitizeToken(st.activePlayerName)).append(',')
                .append(sanitizeToken(st.turnPhase)).append(',')
                .append(st.turnTimeRemainingMs).append(',')
                .append(st.lastAttemptScore).append(',')
                .append(safeSummary).append(',')
                .append(String.format(Locale.US, "%.1f", st.bestDistanceMeters)).append(',')
                .append(String.format(Locale.US, "%.1f", st.landingX)).append(',')
                .append(String.format(Locale.US, "%.1f", st.landingY)).append(',')
                .append(if (st.hasLanded) 1 else 0).append(',')
                .append(String.format(Locale.US, "%.1f", st.ballX)).append(',')
                .append(String.format(Locale.US, "%.1f", st.ballY)).append(',')
                .append(String.format(Locale.US, "%.1f", st.ballVx)).append(',')
                .append(String.format(Locale.US, "%.1f", st.ballVy)).append(',')
                .append(st.ballScoredPoints).append(',')
                .append(if (st.controlsLocked) 1 else 0)
                .append(';')
            st.pins.forEachIndexed { idx, p ->
                if (idx > 0) sb.append('~')
                sb.append(p.id).append(':')
                    .append(String.format(Locale.US, "%.0f", p.x)).append(':')
                    .append(String.format(Locale.US, "%.0f", p.y)).append(':')
                    .append(if (p.knocked) 1 else 0)
            }
            sb.append(';')
            st.golfHoles.forEachIndexed { idx, gh ->
                if (idx > 0) sb.append('~')
                sb.append(gh.number).append(':')
                    .append(String.format(Locale.US, "%.0f", gh.x)).append(':')
                    .append(String.format(Locale.US, "%.0f", gh.y)).append(':')
                    .append(String.format(Locale.US, "%.0f", gh.radius)).append(':')
                    .append(gh.points).append(':')
                    .append(if (gh.captured) 1 else 0)
            }
            sb.append(';')
            st.runwayObstacles.forEachIndexed { idx, ob ->
                if (idx > 0) sb.append('~')
                sb.append(String.format(Locale.US, "%.0f", ob.x)).append(':')
                    .append(String.format(Locale.US, "%.0f", ob.y)).append(':')
                    .append(String.format(Locale.US, "%.0f", ob.radius)).append(':')
                    .append(if (ob.isCrate) 1 else 0)
            }
        }
        sb.append('|')

        // Active Homing Rockets (index 17)
        snap.rockets.forEachIndexed { idx, rk ->
            if (idx > 0) sb.append(';')
            sb.append(rk.id).append(',')
                .append(sanitizeToken(rk.ownerId)).append(',')
                .append(sanitizeToken(rk.targetId ?: "")).append(',')
                .append(String.format(Locale.US, "%.1f", rk.x)).append(',')
                .append(String.format(Locale.US, "%.1f", rk.y)).append(',')
                .append(String.format(Locale.US, "%.3f", rk.angleRad)).append(',')
                .append(rk.remainingMs)
        }

        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    fun decodeSnapshot(
        text: String,
        roster: List<LobbyPlayer>,
        previousSnapshot: RaceSnapshot?,
        activeTrack: TrackDefinition? = null
    ): DecodedSnapshot? {
        if (!text.startsWith("S|")) return null
        val parts = text.split('|')
        if (parts.size < 11) return null

        val seq = parts[1].toLongOrNull() ?: return null
        val raceTimeMs = parts[2].toLongOrNull() ?: 0L
        val countdownSec = parts[3].toIntOrNull() ?: 0
        val phase = RacePhase.entries.getOrElse(parts[4].toIntOrNull() ?: 0) { RacePhase.RACING }.name
        val totalLaps = parts[5].toIntOrNull() ?: 3
        val trackId = TrackId.entries.getOrElse(parts[6].toIntOrNull() ?: 0) { TrackId.LIVING_ROOM_GP }
        val banner = parts[7].ifEmpty { null }

        val metaFields = parts.getOrNull(11)?.split(',').orEmpty()
        val winCondition = WinCondition.entries.getOrElse(metaFields.getOrNull(0)?.toIntOrNull() ?: 0) {
            WinCondition.FIRST_TO_FINISH
        }.name
        val timeLimitSec = metaFields.getOrNull(1)?.toIntOrNull() ?: 90
        val activeEvent = ExternalEventType.entries.getOrElse(metaFields.getOrNull(2)?.toIntOrNull() ?: 0) {
            ExternalEventType.NONE
        }.name
        val eventRemainingMs = metaFields.getOrNull(3)?.toLongOrNull() ?: 0L
        val windAngleRad = metaFields.getOrNull(4)?.toFloatOrNull() ?: 0f
        val windStrength = metaFields.getOrNull(5)?.toFloatOrNull() ?: 0f
        val postFinishGraceMs = metaFields.getOrNull(6)?.toLongOrNull() ?: 0L
        val gameModeEnum = GameMode.entries.getOrElse(metaFields.getOrNull(7)?.toIntOrNull() ?: 0) {
            GameMode.RACE
        }
        val arenaOrd = metaFields.getOrNull(8)?.toIntOrNull() ?: -1
        val arenaShapeEnum = if (arenaOrd in ArenaShape.entries.indices) {
            ArenaShape.entries[arenaOrd]
        } else {
            null
        }
        val arenaTargetMs = metaFields.getOrNull(9)?.toLongOrNull() ?: 120_000L
        val flagCarrierRaw = metaFields.getOrNull(10)?.ifEmpty { null }
        val flagX = metaFields.getOrNull(11)?.toFloatOrNull() ?: 1100f
        val flagY = metaFields.getOrNull(12)?.toFloatOrNull() ?: 750f
        val suddenDeathStage = metaFields.getOrNull(13)?.toIntOrNull() ?: 0
        val suddenDeathRemMs = metaFields.getOrNull(14)?.toLongOrNull() ?: 0L
        val isHybridTour = metaFields.getOrNull(15) == "1"
        val hybridStageNumber = metaFields.getOrNull(16)?.toIntOrNull() ?: 1
        val nextHybridMode = metaFields.getOrNull(17)?.ifEmpty { null }

        val rosterMap = roster.associateBy { it.id }
        val prevMap = previousSnapshot?.racers?.associateBy { it.playerId }.orEmpty()

        val racers = if (parts[8].isEmpty()) {
            emptyList()
        } else {
            parts[8].split(';').mapNotNull { entry ->
                val f = entry.split(',')
                if (f.size < 21) return@mapNotNull null
                val pid = f[0]
                val meta = rosterMap[pid]
                val prev = prevMap[pid]
                val heldOrd = f[9].toIntOrNull() ?: -1
                val heldName = if (heldOrd in PowerUpType.entries.indices) {
                    PowerUpType.entries[heldOrd].name
                } else {
                    null
                }
                val finishMs = f[17].toLongOrNull()?.let { if (it >= 0L) it else null }
                val bestLap = f[18].toLongOrNull()?.let { if (it >= 0L) it else null }
                val lapTimes = if (f[20].isEmpty()) {
                    emptyList()
                } else {
                    f[20].split('-').mapNotNull { it.toLongOrNull() }
                }
                val jumpZ = f.getOrNull(21)?.toFloatOrNull() ?: 0f
                val racePoints = f.getOrNull(22)?.toIntOrNull() ?: 0
                val flagCarryMs = f.getOrNull(23)?.toLongOrNull() ?: 0L
                val minesAvailable = f.getOrNull(24)?.toIntOrNull() ?: 0
                val invertedSteerMs = f.getOrNull(25)?.toLongOrNull() ?: 0L
                val collisionSeq = f.getOrNull(26)?.toIntOrNull() ?: 0
                val portalWarpSeq = f.getOrNull(27)?.toIntOrNull() ?: 0
                val electroAuraMs = f.getOrNull(28)?.toLongOrNull() ?: 0L
                val electroParalyzedMs = f.getOrNull(29)?.toLongOrNull() ?: 0L
                val laserBeamMs = f.getOrNull(30)?.toLongOrNull() ?: 0L
                val isBraking = f.getOrNull(31) == "1"
                val equippedUpgradesMask = f.getOrNull(32)?.toIntOrNull()
                    ?: meta?.equippedUpgradesMask
                    ?: prev?.equippedUpgradesMask
                    ?: 0

                val vx = f[4].toFloatOrNull() ?: 0f
                val vy = f[5].toFloatOrNull() ?: 0f
                RacerState(
                    playerId = pid,
                    name = meta?.name ?: prev?.name ?: pid,
                    isBot = meta?.isBot ?: prev?.isBot ?: false,
                    carType = meta?.carType ?: prev?.carType ?: "VOLT_BUGGY",
                    primaryColorArgb = meta?.primaryColorArgb ?: prev?.primaryColorArgb ?: 0xFFFF5722,
                    accentColorArgb = meta?.accentColorArgb ?: prev?.accentColorArgb ?: 0xFFFFD54F,
                    x = f[1].toFloatOrNull() ?: 0f,
                    y = f[2].toFloatOrNull() ?: 0f,
                    angleRad = f[3].toFloatOrNull() ?: 0f,
                    vx = vx,
                    vy = vy,
                    speed = kotlin.math.hypot(vx, vy),
                    steerVisual = f[6].toFloatOrNull() ?: 0f,
                    jumpZ = jumpZ,
                    lap = f[7].toIntOrNull() ?: 1,
                    rank = f[8].toIntOrNull() ?: 1,
                    heldPowerUp = heldName,
                    boostMs = f[10].toLongOrNull() ?: 0L,
                    shieldMs = f[11].toLongOrNull() ?: 0L,
                    stunnedMs = f[12].toLongOrNull() ?: 0L,
                    overdriveMs = f[13].toLongOrNull() ?: 0L,
                    spinVisualOffset = f[14].toFloatOrNull() ?: 0f,
                    powerUpHits = f[15].toIntOrNull() ?: 0,
                    finished = f[16] == "1",
                    finishTimeMs = finishMs,
                    bestLapMs = bestLap,
                    currentLapStartMs = f[19].toLongOrNull() ?: 0L,
                    lapTimesMs = lapTimes,
                    racePoints = racePoints,
                    flagCarryMs = flagCarryMs,
                    minesAvailable = minesAvailable,
                    invertedSteerMs = invertedSteerMs,
                    collisionSeq = collisionSeq,
                    portalWarpSeq = portalWarpSeq,
                    electroShockAuraMs = electroAuraMs,
                    electroParalyzedMs = electroParalyzedMs,
                    laserBeamMs = laserBeamMs,
                    isBraking = isBraking,
                    equippedUpgradesMask = equippedUpgradesMask
                )
            }
        }

        val stuntRaw = parts.getOrNull(16).orEmpty()
        val stuntState: StuntStateSnapshot? = if (stuntRaw.isEmpty()) {
            null
        } else {
            val sections = stuntRaw.split(';')
            val h = sections.getOrNull(0)?.split(',').orEmpty()
            if (h.size >= 19) {
                val varOrd = h[0].toIntOrNull() ?: 0
                val variant = StuntVariant.entries.getOrElse(varOrd) { StuntVariant.LONG_JUMP }
                val pinsRaw = sections.getOrNull(1).orEmpty()
                val pins = if (pinsRaw.isEmpty()) {
                    emptyList()
                } else {
                    pinsRaw.split('~').mapNotNull { pEntry ->
                        val pf = pEntry.split(':')
                        if (pf.size < 4) return@mapNotNull null
                        StuntPinState(
                            id = pf[0].toIntOrNull() ?: return@mapNotNull null,
                            x = pf[1].toFloatOrNull() ?: return@mapNotNull null,
                            y = pf[2].toFloatOrNull() ?: return@mapNotNull null,
                            knocked = pf[3] == "1"
                        )
                    }
                }
                val holesRaw = sections.getOrNull(2).orEmpty()
                val holes = if (holesRaw.isEmpty()) {
                    emptyList()
                } else {
                    holesRaw.split('~').mapNotNull { gEntry ->
                        val gf = gEntry.split(':')
                        if (gf.size < 6) return@mapNotNull null
                        StuntGolfHole(
                            number = gf[0].toIntOrNull() ?: return@mapNotNull null,
                            x = gf[1].toFloatOrNull() ?: return@mapNotNull null,
                            y = gf[2].toFloatOrNull() ?: return@mapNotNull null,
                            radius = gf[3].toFloatOrNull() ?: 50f,
                            points = gf[4].toIntOrNull() ?: 200,
                            captured = gf[5] == "1"
                        )
                    }
                }
                val obstaclesRaw = sections.getOrNull(3).orEmpty()
                val runwayObstacles = if (obstaclesRaw.isEmpty()) {
                    emptyList()
                } else {
                    obstaclesRaw.split('~').mapNotNull { oEntry ->
                        val of = oEntry.split(':')
                        if (of.size < 4) return@mapNotNull null
                        StuntObstacleState(
                            x = of[0].toFloatOrNull() ?: return@mapNotNull null,
                            y = of[1].toFloatOrNull() ?: return@mapNotNull null,
                            radius = of[2].toFloatOrNull() ?: 25f,
                            isCrate = of[3] == "1"
                        )
                    }
                }
                StuntStateSnapshot(
                    variant = variant.name,
                    roundIndex = h[1].toIntOrNull() ?: 1,
                    totalRounds = h[2].toIntOrNull() ?: 4,
                    turnIndex = h[3].toIntOrNull() ?: 0,
                    activePlayerId = h[4],
                    activePlayerName = h[5],
                    turnPhase = h[6].ifEmpty { "DRIVING" },
                    turnTimeRemainingMs = h[7].toLongOrNull() ?: 10_000L,
                    controlsLocked = h.getOrNull(19) == "1",
                    lastAttemptScore = h[8].toIntOrNull() ?: 0,
                    lastAttemptSummary = h[9],
                    bestDistanceMeters = h[10].toFloatOrNull() ?: 0f,
                    landingX = h[11].toFloatOrNull() ?: 0f,
                    landingY = h[12].toFloatOrNull() ?: 700f,
                    hasLanded = h[13] == "1",
                    ballX = h[14].toFloatOrNull() ?: 1920f,
                    ballY = h[15].toFloatOrNull() ?: 700f,
                    ballVx = h[16].toFloatOrNull() ?: 0f,
                    ballVy = h[17].toFloatOrNull() ?: 0f,
                    ballScoredPoints = h[18].toIntOrNull() ?: 0,
                    runwayObstacles = runwayObstacles,
                    pins = pins,
                    golfHoles = holes
                )
            } else {
                null
            }
        }

        val trackDef = when {
            gameModeEnum == GameMode.STUNTS -> {
                TrackCatalog.getStuntTrack(stuntState?.variantEnum ?: StuntVariant.LONG_JUMP)
            }
            activeTrack != null -> activeTrack
            gameModeEnum == GameMode.ARENA -> {
                TrackCatalog.getArenaTrack(arenaShapeEnum ?: ArenaShape.SQUARE)
            }
            else -> TrackCatalog.getTrack(trackId)
        }
        val rawBoxes = parts[9]
        val boxes = when {
            gameModeEnum == GameMode.STUNTS -> emptyList()
            rawBoxes.contains(':') -> {
                rawBoxes.split('~').mapNotNull { entry ->
                    val bf = entry.split(':')
                    if (bf.size < 4) return@mapNotNull null
                    ItemBoxState(
                        id = bf[0].toIntOrNull() ?: return@mapNotNull null,
                        x = bf[1].toFloatOrNull() ?: return@mapNotNull null,
                        y = bf[2].toFloatOrNull() ?: return@mapNotNull null,
                        available = bf[3] != "0",
                        respawnMs = 0L
                    )
                }
            }
            rawBoxes.isEmpty() -> emptyList()
            else -> {
                trackDef.itemSpawns.mapIndexed { idx, spawn ->
                    ItemBoxState(
                        id = spawn.id,
                        x = spawn.x,
                        y = spawn.y,
                        available = rawBoxes.getOrNull(idx) != '0',
                        respawnMs = 0L
                    )
                }
            }
        }

        val hazards = if (parts[10].isEmpty()) {
            emptyList()
        } else {
            parts[10].split(';').mapNotNull { entry ->
                val f = entry.split(',')
                if (f.size < 5) return@mapNotNull null
                val typeOrd = f.getOrNull(5)?.toIntOrNull() ?: 0
                val hazType = HazardType.entries.getOrElse(typeOrd) { HazardType.GLUE_SLICK }.name
                TrackHazard(
                    id = f[0].toIntOrNull() ?: return@mapNotNull null,
                    ownerId = f[1],
                    x = f[2].toFloatOrNull() ?: 0f,
                    y = f[3].toFloatOrNull() ?: 0f,
                    radius = when (hazType) {
                        HazardType.MINE.name -> 30f
                        HazardType.BARRIER.name -> 28f
                        else -> 32f
                    },
                    remainingMs = f[4].toLongOrNull() ?: 0L,
                    type = hazType
                )
            }
        }

        val puddlesRaw = parts.getOrNull(12).orEmpty()
        val rainPuddles = if (puddlesRaw.isEmpty()) {
            emptyList()
        } else {
            puddlesRaw.split(';').mapNotNull { entry ->
                val f = entry.split(',')
                if (f.size < 3) return@mapNotNull null
                TrackPuddle(
                    x = f[0].toFloatOrNull() ?: return@mapNotNull null,
                    y = f[1].toFloatOrNull() ?: return@mapNotNull null,
                    radius = f[2].toFloatOrNull() ?: 40f
                )
            }
        }

        val pickupsRaw = parts.getOrNull(13).orEmpty()
        val fieldPickups = if (pickupsRaw.isEmpty()) {
            emptyList()
        } else {
            pickupsRaw.split(';').mapNotNull { entry ->
                val f = entry.split(',')
                if (f.size < 5) return@mapNotNull null
                val typeOrd = f[3].toIntOrNull() ?: 0
                val fpType = FieldPickupType.entries.getOrElse(typeOrd) { FieldPickupType.POSITION_SWAP }.name
                TrackFieldPickup(
                    id = f[0].toIntOrNull() ?: return@mapNotNull null,
                    x = f[1].toFloatOrNull() ?: return@mapNotNull null,
                    y = f[2].toFloatOrNull() ?: return@mapNotNull null,
                    type = fpType,
                    remainingMs = f[4].toLongOrNull() ?: 8000L
                )
            }
        }

        val portalRaw = parts.getOrNull(14).orEmpty()
        val portalPair = if (portalRaw.isEmpty()) {
            null
        } else {
            val f = portalRaw.split(',')
            if (f.size >= 8) {
                val pid = f[0].toIntOrNull()
                val ex1 = f[1].toFloatOrNull()
                val ey1 = f[2].toFloatOrNull()
                val isRed = f[3] == "1"
                val ex2 = f[4].toFloatOrNull()
                val ey2 = f[5].toFloatOrNull()
                val outAng = f[6].toFloatOrNull() ?: 0f
                val rem = f[7].toLongOrNull() ?: 8000L
                if (pid != null && ex1 != null && ey1 != null && ex2 != null && ey2 != null) {
                    PortalPairState(
                        id = pid,
                        entryX = ex1,
                        entryY = ey1,
                        entryColorIsRed = isRed,
                        exitX = ex2,
                        exitY = ey2,
                        exitAngleRad = outAng,
                        remainingMs = rem
                    )
                } else {
                    null
                }
            } else {
                null
            }
        }

        val explosionsRaw = parts.getOrNull(15).orEmpty()
        val explosions = if (explosionsRaw.isEmpty()) {
            emptyList()
        } else {
            explosionsRaw.split(';').mapNotNull { entry ->
                val f = entry.split(',')
                if (f.size < 5) return@mapNotNull null
                TrackExplosion(
                    id = f[0].toIntOrNull() ?: return@mapNotNull null,
                    x = f[1].toFloatOrNull() ?: return@mapNotNull null,
                    y = f[2].toFloatOrNull() ?: return@mapNotNull null,
                    ageMs = f[3].toLongOrNull() ?: 0L,
                    durationMs = f[4].toLongOrNull() ?: 650L
                )
            }
        }

        val rocketsRaw = parts.getOrNull(17).orEmpty()
        val rockets = if (rocketsRaw.isEmpty()) {
            emptyList()
        } else {
            rocketsRaw.split(';').mapNotNull { entry ->
                val f = entry.split(',')
                if (f.size < 7) return@mapNotNull null
                ActiveRocketState(
                    id = f[0].toIntOrNull() ?: return@mapNotNull null,
                    ownerId = f[1],
                    targetId = f[2].ifEmpty { null },
                    x = f[3].toFloatOrNull() ?: return@mapNotNull null,
                    y = f[4].toFloatOrNull() ?: return@mapNotNull null,
                    angleRad = f[5].toFloatOrNull() ?: 0f,
                    remainingMs = f[6].toLongOrNull() ?: 3000L
                )
            }
        }

        return DecodedSnapshot(
            seq = seq,
            snapshot = RaceSnapshot(
                raceTimeMs = raceTimeMs,
                countdownSec = countdownSec,
                phase = phase,
                trackId = trackId.name,
                totalLaps = totalLaps,
                racers = racers,
                itemBoxes = boxes,
                hazards = hazards,
                eventBanner = banner,
                winCondition = winCondition,
                timeLimitSec = timeLimitSec,
                activeEvent = activeEvent,
                eventRemainingMs = eventRemainingMs,
                windAngleRad = windAngleRad,
                windStrength = windStrength,
                rainPuddles = rainPuddles,
                postFinishGraceMs = postFinishGraceMs,
                gameMode = gameModeEnum.name,
                arenaShape = arenaShapeEnum?.name,
                arenaTargetMs = arenaTargetMs,
                arenaFlag = if (gameModeEnum == GameMode.ARENA) {
                    ArenaFlagState(x = flagX, y = flagY, carrierId = flagCarrierRaw)
                } else {
                    null
                },
                fieldPickups = fieldPickups,
                portalPair = portalPair,
                explosions = explosions,
                arenaSuddenDeathStage = suddenDeathStage,
                arenaSuddenDeathRemainingMs = suddenDeathRemMs,
                stuntState = stuntState,
                isHybridTour = isHybridTour,
                hybridStageNumber = hybridStageNumber,
                nextHybridMode = nextHybridMode,
                rockets = rockets
            )
        )
    }
}

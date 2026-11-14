package com.example.network

import com.example.data.TrackCatalog
import com.example.model.ControlInput
import com.example.model.ExternalEventType
import com.example.model.ItemBoxState
import com.example.model.LobbyPlayer
import com.example.model.PowerUpType
import com.example.model.RacePhase
import com.example.model.RaceSnapshot
import com.example.model.RacerState
import com.example.model.TrackDefinition
import com.example.model.TrackHazard
import com.example.model.TrackId
import com.example.model.TrackPuddle
import com.example.model.WinCondition
import java.util.Locale

object RaceTelemetryCodec {

    data class DecodedInput(
        val playerId: String,
        val seq: Long,
        val input: ControlInput
    )

    data class DecodedSnapshot(
        val seq: Long,
        val snapshot: RaceSnapshot
    )

    fun encodeHello(playerId: String): ByteArray =
        "H|$playerId".toByteArray(Charsets.UTF_8)

    fun decodeHello(text: String): String? {
        if (!text.startsWith("H|")) return null
        return text.removePrefix("H|").trim().ifEmpty { null }
    }

    fun encodeInput(playerId: String, seq: Long, input: ControlInput): ByteArray {
        val t = String.format(Locale.US, "%.2f", input.throttle)
        val s = String.format(Locale.US, "%.3f", input.steer)
        val p = if (input.usePowerUp) "1" else "0"
        return "I|$playerId|$seq|$t|$s|$p".toByteArray(Charsets.UTF_8)
    }

    fun decodeInput(text: String): DecodedInput? {
        if (!text.startsWith("I|")) return null
        val parts = text.split('|')
        if (parts.size < 6) return null
        val playerId = parts[1]
        val seq = parts[2].toLongOrNull() ?: return null
        val throttle = parts[3].toFloatOrNull() ?: 0f
        val steer = parts[4].toFloatOrNull() ?: 0f
        val usePower = parts[5] == "1"
        return DecodedInput(playerId, seq, ControlInput(throttle, steer, usePower))
    }

    fun encodeSnapshot(seq: Long, snap: RaceSnapshot): ByteArray {
        val sb = StringBuilder(460)
        val phaseOrd = runCatching { RacePhase.valueOf(snap.phase).ordinal }.getOrDefault(0)
        val trackOrd = runCatching { TrackId.valueOf(snap.trackId).ordinal }.getOrDefault(0)
        val banner = (snap.eventBanner ?: "").replace("|", "").replace("\n", "")
        val winOrd = runCatching { WinCondition.valueOf(snap.winCondition).ordinal }.getOrDefault(0)
        val evOrd = runCatching { ExternalEventType.valueOf(snap.activeEvent).ordinal }.getOrDefault(0)

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
            sb.append(r.playerId).append(',')
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
                .append(r.racePoints)
        }
        sb.append('|')

        // Item boxes compact mask (index 9)
        for (b in snap.itemBoxes) {
            sb.append(if (b.available) '1' else '0')
        }
        sb.append('|')

        // Hazards (index 10)
        snap.hazards.forEachIndexed { idx, h ->
            if (idx > 0) sb.append(';')
            sb.append(h.id).append(',')
                .append(h.ownerId).append(',')
                .append(String.format(Locale.US, "%.1f", h.x)).append(',')
                .append(String.format(Locale.US, "%.1f", h.y)).append(',')
                .append(h.remainingMs)
        }
        sb.append('|')

        // Match & Weather Event meta (index 11)
        sb.append(winOrd).append(',')
            .append(snap.timeLimitSec).append(',')
            .append(evOrd).append(',')
            .append(snap.eventRemainingMs).append(',')
            .append(String.format(Locale.US, "%.3f", snap.windAngleRad)).append(',')
            .append(String.format(Locale.US, "%.2f", snap.windStrength)).append(',')
            .append(snap.postFinishGraceMs)
        sb.append('|')

        // Rain puddles (index 12)
        snap.rainPuddles.forEachIndexed { idx, p ->
            if (idx > 0) sb.append(';')
            sb.append(String.format(Locale.US, "%.0f", p.x)).append(',')
                .append(String.format(Locale.US, "%.0f", p.y)).append(',')
                .append(String.format(Locale.US, "%.0f", p.radius))
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
                    racePoints = racePoints
                )
            }
        }

        val trackDef = activeTrack ?: TrackCatalog.getTrack(trackId)
        val mask = parts[9]
        val boxes = trackDef.itemSpawns.mapIndexed { idx, spawn ->
            ItemBoxState(
                id = spawn.id,
                x = spawn.x,
                y = spawn.y,
                available = mask.getOrNull(idx) != '0',
                respawnMs = 0L
            )
        }

        val hazards = if (parts[10].isEmpty()) {
            emptyList()
        } else {
            parts[10].split(';').mapNotNull { entry ->
                val f = entry.split(',')
                if (f.size < 5) return@mapNotNull null
                TrackHazard(
                    id = f[0].toIntOrNull() ?: return@mapNotNull null,
                    ownerId = f[1],
                    x = f[2].toFloatOrNull() ?: 0f,
                    y = f[3].toFloatOrNull() ?: 0f,
                    remainingMs = f[4].toLongOrNull() ?: 0L
                )
            }
        }

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
                postFinishGraceMs = postFinishGraceMs
            )
        )
    }
}

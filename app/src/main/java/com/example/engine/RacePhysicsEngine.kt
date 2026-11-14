package com.example.engine

import com.example.data.TrackCatalog
import com.example.model.ControlInput
import com.example.model.ItemBoxState
import com.example.model.LobbyPlayer
import com.example.model.PowerUpType
import com.example.model.PropShape
import com.example.model.RacePhase
import com.example.model.RaceSnapshot
import com.example.model.RacerState
import com.example.model.RcCarType
import com.example.model.TrackDefinition
import com.example.model.TrackHazard
import com.example.model.TrackId
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class RacePhysicsEngine(
    private val track: TrackDefinition,
    private val totalLaps: Int,
    players: List<LobbyPlayer>
) {
    private data class MutableRacer(
        val playerId: String,
        val name: String,
        val isBot: Boolean,
        val carType: RcCarType,
        val primaryColorArgb: Long,
        val accentColorArgb: Long,
        var x: Float,
        var y: Float,
        var angleRad: Float,
        var vx: Float = 0f,
        var vy: Float = 0f,
        var steerVisual: Float = 0f,
        var lap: Int = 1,
        var checkpointIndex: Int = 0,
        var sectorMask: Int = 0,
        var currentLapStartMs: Long = 0L,
        val lapTimesMs: MutableList<Long> = mutableListOf(),
        var bestLapMs: Long? = null,
        var finished: Boolean = false,
        var finishTimeMs: Long? = null,
        var rank: Int = 1,
        var heldPowerUp: PowerUpType? = null,
        var boostMs: Long = 0L,
        var shieldMs: Long = 0L,
        var stunnedMs: Long = 0L,
        var overdriveMs: Long = 0L,
        var spinVisualOffset: Float = 0f,
        var powerUpHits: Int = 0,
        var botLaneOffset: Float = 0f,
        var botPowerUpDelayMs: Long = 0L
    )

    private val racers: MutableList<MutableRacer> = players.mapIndexed { idx, p ->
        val (sx, sy, angle) = TrackCatalog.gridStartTransform(track, idx)
        val car = runCatching { RcCarType.valueOf(p.carType) }.getOrDefault(RcCarType.VOLT_BUGGY)
        MutableRacer(
            playerId = p.id,
            name = p.name,
            isBot = p.isBot,
            carType = car,
            primaryColorArgb = p.primaryColorArgb,
            accentColorArgb = p.accentColorArgb,
            x = sx,
            y = sy,
            angleRad = angle,
            checkpointIndex = track.centerline.size - 4,
            botLaneOffset = ((idx % 3) - 1) * 26f
        )
    }.toMutableList()

    private val itemBoxes: MutableList<ItemBoxState> = track.itemSpawns.map {
        ItemBoxState(id = it.id, x = it.x, y = it.y, available = true, respawnMs = 0L)
    }.toMutableList()

    private val hazards: MutableList<TrackHazard> = mutableListOf()
    private var nextHazardId = 1

    private var elapsedMs: Long = 0L
    private var raceTimeMs: Long = 0L
    private var phase: RacePhase = RacePhase.COUNTDOWN
    private var bannerText: String? = null
    private var bannerTimerMs: Long = 0L

    private val latestInputs = mutableMapOf<String, ControlInput>()
    private val pendingPowerUpTrigger = mutableSetOf<String>()

    fun submitInput(playerId: String, input: ControlInput) {
        latestInputs[playerId] = input
        if (input.usePowerUp) {
            pendingPowerUpTrigger.add(playerId)
        }
    }

    fun step(dtSeconds: Float): RaceSnapshot {
        val dt = dtSeconds.coerceIn(0.001f, 0.05f)
        val dtMs = (dt * 1000f).toLong().coerceAtLeast(1L)
        elapsedMs += dtMs

        if (bannerTimerMs > 0L) {
            bannerTimerMs = (bannerTimerMs - dtMs).coerceAtLeast(0L)
            if (bannerTimerMs == 0L) bannerText = null
        }

        if (phase == RacePhase.COUNTDOWN) {
            val countdownDuration = 3200L
            if (elapsedMs >= countdownDuration) {
                phase = RacePhase.RACING
                raceTimeMs = 0L
                setBanner("GO! RACE ON!", 1500L)
            } else {
                val secLeft = (((countdownDuration - elapsedMs) / 1000L) + 1).toInt().coerceIn(1, 3)
                return buildSnapshot(countdownSec = secLeft)
            }
        }

        if (phase == RacePhase.RACING) {
            raceTimeMs += dtMs
        }

        // Update item boxes respawn
        for (i in itemBoxes.indices) {
            val box = itemBoxes[i]
            if (!box.available) {
                val rem = (box.respawnMs - dtMs).coerceAtLeast(0L)
                itemBoxes[i] = if (rem == 0L) {
                    box.copy(available = true, respawnMs = 0L)
                } else {
                    box.copy(respawnMs = rem)
                }
            }
        }

        // Update hazards lifetime
        val hazIt = hazards.iterator()
        while (hazIt.hasNext()) {
            val h = hazIt.next()
            val rem = h.remainingMs - dtMs
            if (rem <= 0L) {
                hazIt.remove()
            } else {
                val idx = hazards.indexOf(h)
                if (idx >= 0) hazards[idx] = h.copy(remainingMs = rem)
            }
        }

        // Step each racer
        for (racer in racers) {
            tickTimers(racer, dtMs, dt)

            val input = when {
                phase != RacePhase.RACING -> ControlInput()
                racer.finished -> computeBotInput(racer, dtMs, cruise = true)
                racer.isBot -> computeBotInput(racer, dtMs, cruise = false)
                else -> latestInputs[racer.playerId] ?: ControlInput()
            }

            if (phase == RacePhase.RACING && !racer.finished) {
                val shouldUsePowerUp = if (racer.isBot) {
                    racer.heldPowerUp != null && racer.botPowerUpDelayMs <= 0L
                } else {
                    pendingPowerUpTrigger.remove(racer.playerId)
                }
                if (shouldUsePowerUp) {
                    activatePowerUp(racer)
                }
            } else {
                pendingPowerUpTrigger.remove(racer.playerId)
            }

            integrateCarPhysics(racer, input, dt)
            handleWorldAndPropCollisions(racer)
            if (phase == RacePhase.RACING && !racer.finished) {
                handleBoostPads(racer)
                handleItemPickups(racer)
                handleHazards(racer)
                updateLapProgress(racer)
            }
        }

        // Car-to-car bumper collisions
        resolveCarCollisions()

        // Update rankings
        updateRankings()

        // Check if all human racers finished (or everyone finished)
        val humanRacers = racers.filter { !it.isBot }
        val allHumansDone = humanRacers.isNotEmpty() && humanRacers.all { it.finished }
        val allRacersDone = racers.all { it.finished }
        if (phase == RacePhase.RACING && (allRacersDone || allHumansDone)) {
            // Auto-complete remaining bots with estimated finish times for clean leaderboard
            for (r in racers) {
                if (!r.finished) {
                    r.finished = true
                    val remainingLaps = (totalLaps - r.lap + 1).coerceAtLeast(1)
                    r.finishTimeMs = raceTimeMs + remainingLaps * 9500L
                }
            }
            updateRankings()
            phase = RacePhase.FINISHED
            setBanner("CHECKERED FLAG!", 3000L)
        }

        return buildSnapshot(countdownSec = 0)
    }

    private fun tickTimers(racer: MutableRacer, dtMs: Long, dt: Float) {
        if (racer.boostMs > 0L) racer.boostMs = (racer.boostMs - dtMs).coerceAtLeast(0L)
        if (racer.shieldMs > 0L) racer.shieldMs = (racer.shieldMs - dtMs).coerceAtLeast(0L)
        if (racer.stunnedMs > 0L) {
            racer.stunnedMs = (racer.stunnedMs - dtMs).coerceAtLeast(0L)
            racer.spinVisualOffset = (racer.spinVisualOffset + dt * 14f) % (2f * PI.toFloat())
        } else {
            racer.spinVisualOffset = 0f
        }
        if (racer.overdriveMs > 0L) racer.overdriveMs = (racer.overdriveMs - dtMs).coerceAtLeast(0L)
        if (racer.botPowerUpDelayMs > 0L) {
            racer.botPowerUpDelayMs = (racer.botPowerUpDelayMs - dtMs).coerceAtLeast(0L)
        }
    }

    private fun integrateCarPhysics(racer: MutableRacer, input: ControlInput, dt: Float) {
        val (closestIdx, distToCenter) = TrackCatalog.nearestTrackPoint(track, racer.x, racer.y)
        val trackPt = track.centerline[closestIdx]
        val isOffRoad = distToCenter > trackPt.halfWidth

        val hasBoost = racer.boostMs > 0L
        val hasOverdrive = racer.overdriveMs > 0L
        val isStunned = racer.stunnedMs > 0L

        val offRoadFactor = if (isOffRoad && !hasOverdrive && !hasBoost) {
            val blended = racer.carType.offRoadRetention * (0.65f + 0.35f * track.id.offRoadDragMultiplier)
            blended.coerceIn(0.48f, 0.98f)
        } else {
            1.0f
        }

        val speedMult = when {
            isStunned -> 0.36f
            hasBoost -> 1.44f
            hasOverdrive -> 1.18f
            else -> 1.0f
        } * offRoadFactor

        val maxSpeed = racer.carType.maxSpeedPx * speedMult
        val accel = racer.carType.accelRate * (if (hasBoost) 1.75f else if (hasOverdrive) 1.3f else 1.0f)

        val cosA = cos(racer.angleRad)
        val sinA = sin(racer.angleRad)
        val rightX = -sinA
        val rightY = cosA

        var forwardVel = racer.vx * cosA + racer.vy * sinA
        var lateralVel = racer.vx * rightX + racer.vy * rightY

        val throttle = if (isStunned) 0.15f else input.throttle.coerceIn(-1f, 1f)
        val steer = if (isStunned) 0f else input.steer.coerceIn(-1f, 1f)
        racer.steerVisual = steer

        // Forward acceleration / braking
        if (throttle > 0.05f) {
            forwardVel += throttle * accel * dt
        } else if (throttle < -0.05f) {
            val brakeMult = if (forwardVel > 0f) 1.55f else 0.72f
            forwardVel += throttle * accel * brakeMult * dt
        } else {
            // Natural rolling drag
            val drag = 2.1f
            forwardVel *= max(0f, 1f - drag * dt)
        }

        // Clamp forward speed
        forwardVel = forwardVel.coerceIn(-maxSpeed * 0.42f, maxSpeed)

        // Steering scales smoothly with speed
        val speedNorm = (abs(forwardVel) / 220f).coerceIn(0.18f, 1.0f)
        val reverseFlip = if (forwardVel < -15f) -1f else 1f
        val gripTurnBoost = if (hasOverdrive) 1.18f else 1.0f
        racer.angleRad = normalizeAngle(
            racer.angleRad + steer * racer.carType.turnRateRad * gripTurnBoost * speedNorm * reverseFlip * dt
        )

        // Lateral grip cancels sideways drift
        val grip = racer.carType.lateralGrip *
            track.id.surfaceGripMultiplier *
            (if (hasOverdrive) 1.5f else 1.0f) *
            (if (isOffRoad) 0.85f else 1.0f)
        lateralVel *= max(0f, 1f - grip * dt)

        // Reconstruct world velocity using updated heading
        val nCos = cos(racer.angleRad)
        val nSin = sin(racer.angleRad)
        val nRightX = -nSin
        val nRightY = nCos

        racer.vx = nCos * forwardVel + nRightX * lateralVel
        racer.vy = nSin * forwardVel + nRightY * lateralVel

        racer.x += racer.vx * dt
        racer.y += racer.vy * dt

        // Soft outer corridor wall so cars can run slightly off-road on kerbs/grass but never escape track zone
        val maxCorridor = trackPt.halfWidth + 130f
        if (distToCenter > maxCorridor) {
            val dx = racer.x - trackPt.x
            val dy = racer.y - trackPt.y
            val d = hypot(dx, dy).coerceAtLeast(1f)
            val nx = dx / d
            val ny = dy / d
            racer.x = trackPt.x + nx * maxCorridor
            racer.y = trackPt.y + ny * maxCorridor
            // Reflect outward velocity component gently
            val vn = racer.vx * nx + racer.vy * ny
            if (vn > 0f) {
                racer.vx -= 1.35f * vn * nx
                racer.vy -= 1.35f * vn * ny
                racer.vx *= 0.85f
                racer.vy *= 0.85f
            }
        }
    }

    private fun handleWorldAndPropCollisions(racer: MutableRacer) {
        val margin = racer.carType.radiusPx + 12f
        if (racer.x < margin) {
            racer.x = margin; racer.vx = abs(racer.vx) * 0.5f
        } else if (racer.x > track.worldWidth - margin) {
            racer.x = track.worldWidth - margin; racer.vx = -abs(racer.vx) * 0.5f
        }
        if (racer.y < margin) {
            racer.y = margin; racer.vy = abs(racer.vy) * 0.5f
        } else if (racer.y > track.worldHeight - margin) {
            racer.y = track.worldHeight - margin; racer.vy = -abs(racer.vy) * 0.5f
        }

        for (prop in track.props) {
            val dx = racer.x - prop.x
            val dy = racer.y - prop.y
            val dist = hypot(dx, dy)
            if (prop.isSolidObstacle) {
                val minDist = prop.radius + racer.carType.radiusPx
                if (dist < minDist && dist > 0.01f) {
                    val nx = dx / dist
                    val ny = dy / dist
                    racer.x = prop.x + nx * minDist
                    racer.y = prop.y + ny * minDist
                    val vn = racer.vx * nx + racer.vy * ny
                    if (vn < 0f) {
                        racer.vx -= 1.5f * vn * nx
                        racer.vy -= 1.5f * vn * ny
                    }
                }
            } else if (prop.shape == PropShape.MUD_POOL && dist < prop.radius) {
                if (racer.shieldMs == 0L && racer.overdriveMs == 0L) {
                    racer.vx *= 0.94f
                    racer.vy *= 0.94f
                }
            }
        }
    }

    private fun handleBoostPads(racer: MutableRacer) {
        for (pad in track.boostPads) {
            if (TrackCatalog.distance(racer.x, racer.y, pad.x, pad.y) <= pad.radius + 10f) {
                if (racer.boostMs < 900L) {
                    racer.boostMs = 1250L
                    val boostSpeed = racer.carType.maxSpeedPx * 1.38f
                    racer.vx = cos(racer.angleRad) * boostSpeed
                    racer.vy = sin(racer.angleRad) * boostSpeed
                }
            }
        }
    }

    private fun handleItemPickups(racer: MutableRacer) {
        if (racer.heldPowerUp != null) return
        for (i in itemBoxes.indices) {
            val box = itemBoxes[i]
            if (box.available && TrackCatalog.distance(racer.x, racer.y, box.x, box.y) <= 44f) {
                itemBoxes[i] = box.copy(available = false, respawnMs = 5500L)
                val pool = if (racer.rank >= 3) {
                    listOf(
                        PowerUpType.TURBO_NITRO,
                        PowerUpType.TURBO_NITRO,
                        PowerUpType.EMP_ZAP,
                        PowerUpType.OVERDRIVE_GRIP,
                        PowerUpType.SHIELD
                    )
                } else {
                    PowerUpType.entries
                }
                racer.heldPowerUp = pool[Random.nextInt(pool.size)]
                if (racer.isBot) {
                    racer.botPowerUpDelayMs = Random.nextLong(700L, 2200L)
                }
                break
            }
        }
    }

    private fun activatePowerUp(racer: MutableRacer) {
        val item = racer.heldPowerUp ?: return
        racer.heldPowerUp = null
        when (item) {
            PowerUpType.TURBO_NITRO -> {
                racer.boostMs = 2500L
                val burst = racer.carType.maxSpeedPx * 1.44f
                racer.vx = cos(racer.angleRad) * burst
                racer.vy = sin(racer.angleRad) * burst
                setBanner("${racer.name} triggered LiPo Turbo!", 1600L)
            }
            PowerUpType.EMP_ZAP -> {
                val target = racers
                    .filter { it.playerId != racer.playerId && !it.finished }
                    .minByOrNull { other ->
                        val rankPenalty = if (other.rank < racer.rank) 0f else 600f
                        TrackCatalog.distance(racer.x, racer.y, other.x, other.y) + rankPenalty
                    }
                if (target != null) {
                    if (target.shieldMs > 0L) {
                        target.shieldMs = 0L
                        setBanner("${target.name}'s Shield blocked ${racer.name}'s EMP!", 1800L)
                    } else {
                        target.stunnedMs = 1750L
                        target.vx *= 0.35f
                        target.vy *= 0.35f
                        racer.powerUpHits += 1
                        setBanner("${racer.name} zapped ${target.name} with EMP!", 1800L)
                    }
                }
            }
            PowerUpType.SHIELD -> {
                racer.shieldMs = 5500L
                setBanner("${racer.name} raised RC Shield!", 1400L)
            }
            PowerUpType.GLUE_SLICK -> {
                val dropX = racer.x - cos(racer.angleRad) * 52f
                val dropY = racer.y - sin(racer.angleRad) * 52f
                hazards.add(
                    TrackHazard(
                        id = nextHazardId++,
                        ownerId = racer.playerId,
                        x = dropX,
                        y = dropY,
                        radius = 30f,
                        remainingMs = 11000L
                    )
                )
            }
            PowerUpType.OVERDRIVE_GRIP -> {
                racer.overdriveMs = 4200L
                setBanner("${racer.name} activated Overdrive!", 1500L)
            }
        }
    }

    private fun handleHazards(racer: MutableRacer) {
        val it = hazards.iterator()
        while (it.hasNext()) {
            val h = it.next()
            // Owner has 1.2s grace period after dropping
            if (h.ownerId == racer.playerId && h.remainingMs > 9800L) continue
            if (TrackCatalog.distance(racer.x, racer.y, h.x, h.y) <= h.radius + racer.carType.radiusPx * 0.7f) {
                it.remove()
                if (racer.shieldMs > 0L) {
                    racer.shieldMs = 0L
                    setBanner("${racer.name}'s Shield absorbed Glue Slick!", 1500L)
                } else {
                    racer.stunnedMs = 1400L
                    racer.vx *= 0.3f
                    racer.vy *= 0.3f
                    val owner = racers.find { o -> o.playerId == h.ownerId }
                    if (owner != null && owner.playerId != racer.playerId) {
                        owner.powerUpHits += 1
                    }
                    setBanner("${racer.name} spun out on Glue Slick!", 1600L)
                }
                break
            }
        }
    }

    private fun resolveCarCollisions() {
        for (i in racers.indices) {
            for (j in i + 1 until racers.size) {
                val a = racers[i]
                val b = racers[j]
                val dx = b.x - a.x
                val dy = b.y - a.y
                val dist = hypot(dx, dy)
                val minDist = a.carType.radiusPx + b.carType.radiusPx
                if (dist < minDist && dist > 0.001f) {
                    val nx = dx / dist
                    val ny = dy / dist
                    val overlap = (minDist - dist) * 0.5f
                    a.x -= nx * overlap
                    a.y -= ny * overlap
                    b.x += nx * overlap
                    b.y += ny * overlap

                    val relVx = b.vx - a.vx
                    val relVy = b.vy - a.vy
                    val sepVel = relVx * nx + relVy * ny
                    if (sepVel < 0f) {
                        val impulse = -1.2f * sepVel * 0.5f
                        a.vx -= impulse * nx
                        a.vy -= impulse * ny
                        b.vx += impulse * nx
                        b.vy += impulse * ny
                    }
                }
            }
        }
    }

    private fun updateLapProgress(racer: MutableRacer) {
        val n = track.centerline.size
        val (newIdx, _) = TrackCatalog.nearestTrackPoint(track, racer.x, racer.y)
        val q1 = n / 4
        val q2 = n / 2
        val q3 = (3 * n) / 4

        val prevIdx = racer.checkpointIndex
        racer.checkpointIndex = newIdx

        when {
            newIdx < q1 -> {
                // Crossing finish line forward after completing all 4 sectors
                if (prevIdx >= q3 && racer.sectorMask == 0b1111) {
                    val lapTime = (raceTimeMs - racer.currentLapStartMs).coerceAtLeast(1000L)
                    racer.lapTimesMs.add(lapTime)
                    val prevBest = racer.bestLapMs
                    if (prevBest == null || lapTime < prevBest) {
                        racer.bestLapMs = lapTime
                    }
                    if (racer.lap >= totalLaps) {
                        racer.finished = true
                        racer.finishTimeMs = raceTimeMs
                        setBanner("${racer.name} finished!", 1800L)
                    } else {
                        racer.lap += 1
                        racer.currentLapStartMs = raceTimeMs
                        racer.sectorMask = 0b0001
                        if (racer.lap == totalLaps) {
                            setBanner("${racer.name}: FINAL LAP!", 1500L)
                        }
                    }
                } else {
                    racer.sectorMask = racer.sectorMask or 0b0001
                }
            }
            newIdx in q1 until q2 -> {
                if ((racer.sectorMask and 0b0001) != 0) {
                    racer.sectorMask = racer.sectorMask or 0b0010
                }
            }
            newIdx in q2 until q3 -> {
                if ((racer.sectorMask and 0b0010) != 0) {
                    racer.sectorMask = racer.sectorMask or 0b0100
                }
            }
            else -> {
                if ((racer.sectorMask and 0b0100) != 0) {
                    racer.sectorMask = racer.sectorMask or 0b1000
                }
            }
        }
    }

    private fun computeProgressScore(racer: MutableRacer): Float {
        if (racer.finished) {
            return 10_000_000f - (racer.finishTimeMs ?: raceTimeMs).toFloat()
        }
        val n = track.centerline.size.toFloat()
        val idx = racer.checkpointIndex
        val adjustedIdx = if (racer.lap == 1 && (racer.sectorMask and 0b0001) == 0 && idx > n * 0.75f) {
            idx - n
        } else {
            idx.toFloat()
        }
        return (racer.lap - 1) * n + adjustedIdx
    }

    private fun updateRankings() {
        val sorted = racers.sortedWith(
            compareByDescending<MutableRacer> { computeProgressScore(it) }
        )
        sorted.forEachIndexed { index, r -> r.rank = index + 1 }
    }

    private fun computeBotInput(racer: MutableRacer, dtMs: Long, cruise: Boolean): ControlInput {
        val pts = track.centerline
        val (closestIdx, _) = TrackCatalog.nearestTrackPoint(track, racer.x, racer.y)
        val lookAhead = 7
        val targetIdx = (closestIdx + lookAhead) % pts.size
        val nextIdx = (targetIdx + 1) % pts.size
        val tp = pts[targetIdx]
        val np = pts[nextIdx]

        val segAngle = atan2(np.y - tp.y, np.x - tp.x)
        val targetX = tp.x + (-sin(segAngle)) * racer.botLaneOffset
        val targetY = tp.y + cos(segAngle) * racer.botLaneOffset

        val desiredAngle = atan2(targetY - racer.y, targetX - racer.x)
        val angleDiff = normalizeAngle(desiredAngle - racer.angleRad)

        val steer = (angleDiff / 0.65f).coerceIn(-1f, 1f)
        val throttle = when {
            cruise -> 0.45f
            abs(angleDiff) > 0.85f -> 0.55f
            else -> 0.96f
        }
        return ControlInput(throttle = throttle, steer = steer, usePowerUp = false)
    }

    private fun setBanner(text: String, durationMs: Long) {
        bannerText = text
        bannerTimerMs = durationMs
    }

    private fun buildSnapshot(countdownSec: Int): RaceSnapshot {
        return RaceSnapshot(
            raceTimeMs = raceTimeMs,
            countdownSec = countdownSec,
            phase = phase.name,
            trackId = track.id.name,
            totalLaps = totalLaps,
            racers = racers.map { r ->
                RacerState(
                    playerId = r.playerId,
                    name = r.name,
                    isBot = r.isBot,
                    carType = r.carType.name,
                    primaryColorArgb = r.primaryColorArgb,
                    accentColorArgb = r.accentColorArgb,
                    x = r.x,
                    y = r.y,
                    angleRad = r.angleRad,
                    vx = r.vx,
                    vy = r.vy,
                    speed = hypot(r.vx, r.vy),
                    steerVisual = r.steerVisual,
                    lap = r.lap,
                    checkpointIndex = r.checkpointIndex,
                    sectorMask = r.sectorMask,
                    totalProgress = computeProgressScore(r),
                    currentLapStartMs = r.currentLapStartMs,
                    lapTimesMs = r.lapTimesMs.toList(),
                    bestLapMs = r.bestLapMs,
                    finished = r.finished,
                    finishTimeMs = r.finishTimeMs,
                    rank = r.rank,
                    heldPowerUp = r.heldPowerUp?.name,
                    boostMs = r.boostMs,
                    shieldMs = r.shieldMs,
                    stunnedMs = r.stunnedMs,
                    overdriveMs = r.overdriveMs,
                    spinVisualOffset = r.spinVisualOffset,
                    powerUpHits = r.powerUpHits
                )
            },
            itemBoxes = itemBoxes.toList(),
            hazards = hazards.toList(),
            eventBanner = bannerText
        )
    }

    companion object {
        fun normalizeAngle(angle: Float): Float {
            var a = angle
            val twoPi = (2.0 * PI).toFloat()
            while (a > PI) a -= twoPi
            while (a < -PI) a += twoPi
            return a
        }
    }
}

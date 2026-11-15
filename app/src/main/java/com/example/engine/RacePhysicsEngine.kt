package com.example.engine

import com.example.data.TrackCatalog
import com.example.model.ArenaFlagState
import com.example.model.ControlInput
import com.example.model.ExternalEventType
import com.example.model.GameMode
import com.example.model.ItemBoxState
import com.example.model.LobbyPlayer
import com.example.model.PowerUpType
import com.example.model.PropShape
import com.example.model.RacePhase
import com.example.model.RaceRulesSettings
import com.example.model.RaceSnapshot
import com.example.model.RacerState
import com.example.model.RcCarType
import com.example.model.TrackDefinition
import com.example.model.TrackHazard
import com.example.model.TrackPuddle
import com.example.model.WinCondition
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

class RacePhysicsEngine(
    private val track: TrackDefinition,
    private val totalLaps: Int,
    players: List<LobbyPlayer>,
    private val rules: RaceRulesSettings = RaceRulesSettings(),
    private val random: Random = Random.Default
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
        var lastTrackIdx: Int = 0,
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
        var jumpZ: Float = 0f,
        var vz: Float = 0f,
        var jumpCooldownMs: Long = 0L,
        var powerUpHits: Int = 0,
        var racePoints: Int = 0,
        var flagCarryMs: Long = 0L,
        var flagPickupImmunityMs: Long = 0L,
        var spinImmunityMs: Long = 0L,
        var botLaneOffset: Float = 0f,
        var botPowerUpDelayMs: Long = 0L
    )

    private val isArenaMode: Boolean = track.isArena || rules.gameModeEnum == GameMode.ARENA
    private val winCondition: WinCondition = rules.winConditionEnum
    private val effectiveLaps: Int = totalLaps.coerceIn(1, 9)
    private val timeLimitMs: Long = rules.timeLimitSec.coerceIn(30, 300) * 1000L
    private val arenaTargetMs: Long = rules.arenaTargetMinutes.coerceIn(1, 10) * 60_000L

    private val racers: MutableList<MutableRacer> = players.mapIndexed { idx, p ->
        val (sx, sy, angle) = TrackCatalog.gridStartTransform(track, idx)
        val car = runCatching { RcCarType.valueOf(p.carType) }.getOrDefault(RcCarType.VOLT_BUGGY)
        val startTrackIdx = (track.centerline.size - 4 - (idx / 2) * 3).mod(track.centerline.size)
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
            checkpointIndex = startTrackIdx,
            lastTrackIdx = startTrackIdx,
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
    private var firstHumanFinishTimeMs: Long? = null

    // Arena Hold-the-Flag state
    private var flagX: Float = track.worldWidth * 0.5f
    private var flagY: Float = track.worldHeight * 0.5f
    private var flagCarrierId: String? = null
    private var flagTransferCooldownMs: Long = 0L

    // Random external weather/quake event state
    private var activeEvent: ExternalEventType = ExternalEventType.NONE
    private var eventRemainingMs: Long = 0L
    private var nextEventCooldownMs: Long = if (rules.randomConditionsEnabled) {
        random.nextLong(6000L, 9500L)
    } else {
        Long.MAX_VALUE
    }
    private var lastEventType: ExternalEventType = ExternalEventType.NONE
    private var windAngleRad: Float = 0f
    private var windStrength: Float = 0f
    private val rainPuddles: MutableList<TrackPuddle> = mutableListOf()
    private var quakePulseTimerMs: Long = 0L

    private val latestInputs = mutableMapOf<String, ControlInput>()
    private val pendingPowerUpTrigger = mutableSetOf<String>()

    init {
        if (isArenaMode) {
            val avoid = racers.map { it.x to it.y } + track.props.map { it.x to it.y }
            val (fx, fy) = TrackCatalog.sampleArenaInteriorPoint(
                track = track,
                rng = random,
                minWallClearance = 150f,
                avoidPoints = avoid,
                minAvoidDist = 175f
            )
            flagX = fx
            flagY = fy
        }
    }

    fun submitInput(playerId: String, input: ControlInput) {
        latestInputs[playerId] = input
        if (input.usePowerUp) {
            pendingPowerUpTrigger.add(playerId)
        }
    }

    /**
     * Exposed for deterministic Arena unit tests.
     */
    fun setFlagStateForTest(x: Float, y: Float, carrierId: String? = null) {
        flagX = x
        flagY = y
        flagCarrierId = carrierId
        flagTransferCooldownMs = 0L
    }

    /**
     * Exposed primarily for deterministic testing or host-triggered inspection.
     */
    fun triggerExternalEvent(type: ExternalEventType, durationMs: Long = 10000L) {
        if (type == ExternalEventType.NONE) {
            clearExternalEvent()
            return
        }
        activeEvent = type
        lastEventType = type
        eventRemainingMs = durationMs.coerceAtLeast(1000L)
        rainPuddles.clear()
        windStrength = 0f
        quakePulseTimerMs = 0L

        when (type) {
            ExternalEventType.RAIN -> {
                spawnRainPuddles()
                val msg = if (isArenaMode) {
                    "RAINFALL! Puddles spin cars & drop the flag!"
                } else {
                    "RAINFALL! Puddles slow & pull steering!"
                }
                setBanner(msg, 2600L)
            }
            ExternalEventType.EARTHQUAKE -> {
                setBanner("EARTHQUAKE! Hold your line!", 2600L)
            }
            ExternalEventType.WIND -> {
                windAngleRad = random.nextFloat() * (2f * PI.toFloat())
                windStrength = 0.85f + random.nextFloat() * 0.3f
                setBanner("GUSTY WIND! Tailwind boosts, crosswind drifts!", 2600L)
            }
            ExternalEventType.SNOW -> {
                setBanner("SNOWFALL! Icy surface — expect skidding!", 2600L)
            }
            ExternalEventType.NONE -> Unit
        }
    }

    private fun clearExternalEvent() {
        val wasActive = activeEvent != ExternalEventType.NONE
        activeEvent = ExternalEventType.NONE
        eventRemainingMs = 0L
        windStrength = 0f
        rainPuddles.clear()
        nextEventCooldownMs = if (rules.randomConditionsEnabled) {
            random.nextLong(8500L, 14000L)
        } else {
            Long.MAX_VALUE
        }
        if (wasActive && phase == RacePhase.RACING) {
            setBanner("Weather clearing — full grip restored!", 1600L)
        }
    }

    private fun spawnRainPuddles() {
        if (track.isArena) {
            val count = random.nextInt(7, 11)
            val placed = mutableListOf<Pair<Float, Float>>()
            repeat(count) {
                val (px, py) = TrackCatalog.sampleArenaInteriorPoint(
                    track = track,
                    rng = random,
                    minWallClearance = 135f,
                    avoidPoints = placed,
                    minAvoidDist = 140f
                )
                placed.add(px to py)
                rainPuddles.add(
                    TrackPuddle(
                        x = px,
                        y = py,
                        radius = 38f + random.nextFloat() * 14f
                    )
                )
            }
            return
        }

        val pts = track.centerline
        if (pts.size < 20) return
        val count = random.nextInt(6, 10)
        repeat(count) {
            val idx = random.nextInt(8, pts.size - 4)
            val pt = pts[idx]
            val next = pts[(idx + 1) % pts.size]
            val tangAng = atan2(next.y - pt.y, next.x - pt.x)
            val perpX = -sin(tangAng)
            val perpY = cos(tangAng)
            val offset = (random.nextFloat() * 2f - 1f) * (pt.halfWidth * 0.55f)
            rainPuddles.add(
                TrackPuddle(
                    x = pt.x + perpX * offset,
                    y = pt.y + perpY * offset,
                    radius = 36f + random.nextFloat() * 14f
                )
            )
        }
    }

    private fun updateExternalEvents(dtMs: Long, dt: Float) {
        if (!rules.randomConditionsEnabled || phase != RacePhase.RACING) return

        if (activeEvent != ExternalEventType.NONE) {
            eventRemainingMs = (eventRemainingMs - dtMs).coerceAtLeast(0L)
            if (activeEvent == ExternalEventType.EARTHQUAKE) {
                quakePulseTimerMs += dtMs
                if (quakePulseTimerMs >= 260L) {
                    quakePulseTimerMs = 0L
                    for (r in racers) {
                        if (r.jumpZ < 0.08f) {
                            val joltAng = random.nextFloat() * (2f * PI.toFloat())
                            r.vx += cos(joltAng) * 58f
                            r.vy += sin(joltAng) * 58f
                            r.angleRad = normalizeAngle(r.angleRad + (random.nextFloat() - 0.5f) * 0.11f)
                        }
                    }
                }
            } else if (activeEvent == ExternalEventType.WIND) {
                val wx = cos(windAngleRad) * 95f * windStrength
                val wy = sin(windAngleRad) * 95f * windStrength
                for (r in racers) {
                    r.vx += wx * dt
                    r.vy += wy * dt
                    val rightX = -sin(r.angleRad)
                    val rightY = cos(r.angleRad)
                    val cross = (wx * rightX + wy * rightY) / 95f
                    r.angleRad = normalizeAngle(r.angleRad + cross * 0.28f * dt)
                }
            }

            if (eventRemainingMs == 0L) {
                clearExternalEvent()
            }
        } else {
            nextEventCooldownMs -= dtMs
            if (nextEventCooldownMs <= 0L) {
                val choices = listOf(
                    ExternalEventType.RAIN,
                    ExternalEventType.EARTHQUAKE,
                    ExternalEventType.WIND,
                    ExternalEventType.SNOW
                ).filter { it != lastEventType }
                val nextType = choices[random.nextInt(choices.size)]
                triggerExternalEvent(nextType, durationMs = random.nextLong(8500L, 12500L))
            }
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
                setBanner(
                    if (isArenaMode) "GO! CAPTURE THE FLAG!" else "GO! RACE ON!",
                    1600L
                )
            } else {
                val secLeft = (((countdownDuration - elapsedMs) / 1000L) + 1).toInt().coerceIn(1, 3)
                return buildSnapshot(countdownSec = secLeft)
            }
        }

        if (phase == RacePhase.RACING) {
            raceTimeMs += dtMs
            updateExternalEvents(dtMs, dt)
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
                racer.finished && !racer.isBot -> {
                    val raw = latestInputs[racer.playerId] ?: ControlInput()
                    if (rules.autoAccelerate && raw.throttle > 0.25f) {
                        raw.copy(throttle = 0f)
                    } else {
                        raw
                    }
                }
                racer.finished && racer.isBot -> ControlInput(throttle = 0f, steer = 0f)
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
                handleRamps(racer)
                handleItemPickups(racer)
                handleHazards(racer)
                if (!isArenaMode) {
                    updateLapProgress(racer)
                }
            }
        }

        // Car-to-car bumper collisions (includes Arena flag transfer on collision!)
        resolveCarCollisions()

        // Arena flag pickup & carry counter
        if (isArenaMode && phase == RacePhase.RACING) {
            updateArenaFlag(dtMs)
        }

        // Update rankings
        updateRankings()

        // Check win / finish conditions
        if (phase == RacePhase.RACING) {
            if (isArenaMode) {
                val winner = racers.firstOrNull { it.flagCarryMs >= arenaTargetMs }
                if (winner != null) {
                    winner.flagCarryMs = arenaTargetMs
                    for (r in racers) {
                        r.finished = true
                        r.finishTimeMs = raceTimeMs
                    }
                    updateRankings()
                    phase = RacePhase.FINISHED
                    setBanner("${winner.name} WINS ARENA!", 3500L)
                }
            } else {
                val humanRacers = racers.filter { !it.isBot }
                val firstHumanDoneMs = firstHumanFinishTimeMs
                val graceExpired = firstHumanDoneMs != null && (raceTimeMs - firstHumanDoneMs) >= FINISH_GRACE_MS

                val timeLimitReached = winCondition == WinCondition.TIME_TRIAL_DISTANCE &&
                    raceTimeMs >= timeLimitMs
                val safetyPointsTimeout = winCondition == WinCondition.CHAMPIONSHIP_POINTS &&
                    humanRacers.all { it.finished }
                val allHumansDone = humanRacers.isNotEmpty() && humanRacers.all { it.finished }
                val allRacersDone = racers.all { it.finished }

                val shouldEndRace = when (winCondition) {
                    WinCondition.TIME_TRIAL_DISTANCE -> timeLimitReached || allRacersDone
                    WinCondition.FIRST_TO_FINISH, WinCondition.CHAMPIONSHIP_POINTS ->
                        allRacersDone || allHumansDone || safetyPointsTimeout || graceExpired
                }

                if (shouldEndRace) {
                    for (r in racers) {
                        if (!r.finished) {
                            r.finished = true
                            r.finishTimeMs = if (timeLimitReached) {
                                raceTimeMs
                            } else {
                                val remainingLaps = (effectiveLaps - r.lap + 1).coerceAtLeast(1)
                                raceTimeMs + remainingLaps * 9500L
                            }
                        }
                    }
                    if (winCondition == WinCondition.CHAMPIONSHIP_POINTS) {
                        awardFinalPointsBonuses()
                    }
                    updateRankings()
                    phase = RacePhase.FINISHED
                    setBanner("CHECKERED FLAG!", 3000L)
                }
            }
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
        if (racer.jumpCooldownMs > 0L) racer.jumpCooldownMs = (racer.jumpCooldownMs - dtMs).coerceAtLeast(0L)
        if (racer.flagPickupImmunityMs > 0L) {
            racer.flagPickupImmunityMs = (racer.flagPickupImmunityMs - dtMs).coerceAtLeast(0L)
        }
        if (racer.spinImmunityMs > 0L) {
            racer.spinImmunityMs = (racer.spinImmunityMs - dtMs).coerceAtLeast(0L)
        }
        if (racer.jumpZ > 0f || racer.vz > 0f) {
            racer.vz -= 6.5f * dt
            racer.jumpZ += racer.vz * dt
            if (racer.jumpZ <= 0f) {
                racer.jumpZ = 0f
                racer.vz = 0f
            }
        }
        if (racer.botPowerUpDelayMs > 0L) {
            racer.botPowerUpDelayMs = (racer.botPowerUpDelayMs - dtMs).coerceAtLeast(0L)
        }
    }

    private fun dropFlagFrom(racer: MutableRacer, reason: String) {
        if (!isArenaMode || flagCarrierId != racer.playerId) return
        flagCarrierId = null
        racer.flagPickupImmunityMs = 1600L

        val dropAng = racer.angleRad + PI.toFloat() * 0.82f
        var fx = racer.x + cos(dropAng) * 72f
        var fy = racer.y + sin(dropAng) * 72f

        val poly = track.arenaBoundary.ifEmpty { track.centerline }
        if (track.isArena && poly.size >= 3) {
            val inside = TrackCatalog.isPointInPolygon(fx, fy, poly)
            val (bx, by, wallDist) = TrackCatalog.closestPointOnPolygon(fx, fy, poly)
            if (!inside || wallDist < 68f) {
                val cx = track.worldWidth * 0.5f
                val cy = track.worldHeight * 0.5f
                val d = hypot(cx - bx, cy - by).coerceAtLeast(1f)
                fx = bx + ((cx - bx) / d) * 88f
                fy = by + ((cy - by) / d) * 88f
            }
        }

        // Push dropped flag clear of oil/mud slicks so next driver can pick it up cleanly
        for (prop in track.props) {
            if (prop.shape == PropShape.MUD_POOL) {
                val dx = fx - prop.x
                val dy = fy - prop.y
                val d = hypot(dx, dy)
                if (d < prop.radius + 36f) {
                    val nx = if (d > 0.1f) dx / d else 1f
                    val ny = if (d > 0.1f) dy / d else 0f
                    fx = prop.x + nx * (prop.radius + 42f)
                    fy = prop.y + ny * (prop.radius + 42f)
                }
            }
        }

        flagX = fx.coerceIn(140f, track.worldWidth - 140f)
        flagY = fy.coerceIn(120f, track.worldHeight - 120f)
        setBanner("${racer.name} $reason — FLAG DROPPED!", 2000L)
    }

    private fun updateArenaFlag(dtMs: Long) {
        if (flagTransferCooldownMs > 0L) {
            flagTransferCooldownMs = (flagTransferCooldownMs - dtMs).coerceAtLeast(0L)
        }

        val currentCarrierId = flagCarrierId
        if (currentCarrierId != null) {
            val carrier = racers.find { it.playerId == currentCarrierId }
            if (carrier == null) {
                flagCarrierId = null
            } else if (carrier.stunnedMs > 0L) {
                dropFlagFrom(carrier, "spun out")
            } else {
                flagX = carrier.x
                flagY = carrier.y
                carrier.flagCarryMs = (carrier.flagCarryMs + dtMs).coerceAtMost(arenaTargetMs)
            }
        }

        if (flagCarrierId == null) {
            val eligible = racers
                .filter {
                    !it.finished &&
                        it.jumpZ < 0.35f &&
                        it.stunnedMs == 0L &&
                        it.flagPickupImmunityMs == 0L &&
                        TrackCatalog.distance(it.x, it.y, flagX, flagY) <= 48f
                }
                .minByOrNull { TrackCatalog.distance(it.x, it.y, flagX, flagY) }

            if (eligible != null) {
                flagCarrierId = eligible.playerId
                flagX = eligible.x
                flagY = eligible.y
                flagTransferCooldownMs = 550L
                eligible.racePoints += 15
                setBanner("${eligible.name} captured the FLAG!", 1800L)
            }
        }
    }

    private fun integrateCarPhysics(racer: MutableRacer, input: ControlInput, dt: Float) {
        val isAirborne = racer.jumpZ > 0.08f
        var closestIdx = racer.lastTrackIdx
        var isOffRoad = false

        if (!track.isArena) {
            val (idx, distToCenter) = TrackCatalog.nearestTrackPointLocal(
                track = track,
                x = racer.x,
                y = racer.y,
                hintIdx = racer.lastTrackIdx
            )
            closestIdx = idx
            racer.lastTrackIdx = idx
            val trackPt = track.centerline[idx]
            isOffRoad = !isAirborne && distToCenter > trackPt.halfWidth
        }

        val hasBoost = racer.boostMs > 0L
        val hasOverdrive = racer.overdriveMs > 0L

        val inRainPuddle = !isAirborne && activeEvent == ExternalEventType.RAIN && rainPuddles.any {
            TrackCatalog.distance(racer.x, racer.y, it.x, it.y) <= it.radius + racer.carType.radiusPx * 0.45f
        }

        // In Arena mode, hitting a rain puddle spins the car & drops the flag!
        if (isArenaMode && inRainPuddle && racer.shieldMs == 0L && !hasOverdrive &&
            racer.stunnedMs == 0L && racer.spinImmunityMs == 0L
        ) {
            racer.stunnedMs = 1050L
            racer.spinImmunityMs = 2400L
            racer.vx *= 0.45f
            racer.vy *= 0.45f
            if (flagCarrierId == racer.playerId) {
                dropFlagFrom(racer, "hydroplaned in a puddle")
            }
        }

        val isStunned = racer.stunnedMs > 0L

        val offRoadFactor = if (isOffRoad && !hasOverdrive && !hasBoost) {
            val blended = racer.carType.offRoadRetention * (0.65f + 0.35f * track.id.offRoadDragMultiplier)
            blended.coerceIn(0.48f, 0.98f)
        } else {
            1.0f
        }

        val weatherSpeedMult = when {
            inRainPuddle && !hasOverdrive -> 0.76f
            activeEvent == ExternalEventType.RAIN -> 0.93f
            activeEvent == ExternalEventType.SNOW -> 0.89f
            else -> 1.0f
        }

        val speedMult = when {
            isStunned -> 0.36f
            hasBoost -> 1.44f
            hasOverdrive -> 1.18f
            else -> 1.0f
        } * offRoadFactor * weatherSpeedMult

        val maxSpeed = racer.carType.maxSpeedPx * speedMult
        val accelWeatherMult = if (activeEvent == ExternalEventType.SNOW) 0.84f else 1.0f
        val accel = racer.carType.accelRate *
            accelWeatherMult *
            (if (hasBoost) 1.75f else if (hasOverdrive) 1.3f else 1.0f)

        val cosA = cos(racer.angleRad)
        val sinA = sin(racer.angleRad)
        val rightX = -sinA
        val rightY = cosA

        var forwardVel = racer.vx * cosA + racer.vy * sinA
        var lateralVel = racer.vx * rightX + racer.vy * rightY

        val throttle = if (isStunned) 0.15f else input.throttle.coerceIn(-1f, 1f)
        val rawSteer = if (isStunned) 0f else input.steer.coerceIn(-1f, 1f)
        val puddleSteerJitter = if (inRainPuddle && !hasOverdrive) {
            sin((elapsedMs / 110f) + closestIdx.toFloat()) * 0.55f
        } else {
            0f
        }
        val steer = (rawSteer + puddleSteerJitter).coerceIn(-1.15f, 1.15f)
        racer.steerVisual = rawSteer

        if (!isAirborne) {
            if (throttle > 0.05f) {
                forwardVel += throttle * accel * dt
            } else if (throttle < -0.05f) {
                val brakeMult = if (forwardVel > 0f) 1.85f else 1.45f
                forwardVel += throttle * accel * brakeMult * dt
            } else {
                val drag = if (activeEvent == ExternalEventType.SNOW) 1.15f else 2.25f
                forwardVel *= max(0f, 1f - drag * dt)
            }

            forwardVel = forwardVel.coerceIn(-maxSpeed * 0.58f, maxSpeed)
        }

        val speedNorm = (abs(forwardVel) / 180f).coerceIn(0.32f, 1.0f)
        val reverseFlip = if (forwardVel < -25f) -1f else 1f
        val gripTurnBoost = if (hasOverdrive) 1.18f else 1.0f
        val airSteerFactor = if (isAirborne) 0.25f else 1.0f
        racer.angleRad = normalizeAngle(
            racer.angleRad + steer * racer.carType.turnRateRad * gripTurnBoost * airSteerFactor * speedNorm * reverseFlip * dt
        )

        if (!isAirborne) {
            val weatherGripMult = when {
                inRainPuddle && !hasOverdrive -> 0.45f
                activeEvent == ExternalEventType.SNOW -> 0.40f
                activeEvent == ExternalEventType.RAIN -> 0.72f
                activeEvent == ExternalEventType.EARTHQUAKE -> 0.80f
                else -> 1.0f
            }
            val grip = racer.carType.lateralGrip *
                track.id.surfaceGripMultiplier *
                weatherGripMult *
                (if (hasOverdrive) 1.55f else 1.0f) *
                (if (isOffRoad) 0.85f else 1.0f)
            lateralVel *= max(0f, 1f - grip * dt)
        }

        val nCos = cos(racer.angleRad)
        val nSin = sin(racer.angleRad)
        val nRightX = -nSin
        val nRightY = nCos

        racer.vx = nCos * forwardVel + nRightX * lateralVel
        racer.vy = nSin * forwardVel + nRightY * lateralVel

        racer.x += racer.vx * dt
        racer.y += racer.vy * dt

        if (track.isArena) {
            val poly = track.arenaBoundary.ifEmpty { track.centerline }
            if (poly.size >= 3) {
                val inside = TrackCatalog.isPointInPolygon(racer.x, racer.y, poly)
                val (bx, by, wallDist) = TrackCatalog.closestPointOnPolygon(racer.x, racer.y, poly)
                val wallMargin = if (rules.hardTrackBoundary) (racer.carType.radiusPx + 8f) else 4f
                if (!inside || wallDist < wallMargin) {
                    val rawDx = if (inside) (racer.x - bx) else (bx - racer.x)
                    val rawDy = if (inside) (racer.y - by) else (by - racer.y)
                    val rawLen = hypot(rawDx, rawDy)
                    val (inNx, inNy) = if (rawLen > 0.01f) {
                        (rawDx / rawLen) to (rawDy / rawLen)
                    } else {
                        val cdx = track.worldWidth * 0.5f - bx
                        val cdy = track.worldHeight * 0.5f - by
                        val cl = hypot(cdx, cdy).coerceAtLeast(1f)
                        (cdx / cl) to (cdy / cl)
                    }

                    val safeClearance = wallMargin + 2.5f
                    racer.x = bx + inNx * safeClearance
                    racer.y = by + inNy * safeClearance

                    val outNx = -inNx
                    val outNy = -inNy
                    val vnOut = racer.vx * outNx + racer.vy * outNy
                    if (vnOut > 0f) {
                        val restitution = if (rules.hardTrackBoundary) 1.58f else 1.3f
                        val retention = if (rules.hardTrackBoundary) 0.82f else 0.78f
                        racer.vx = (racer.vx - restitution * vnOut * outNx) * retention
                        racer.vy = (racer.vy - restitution * vnOut * outNy) * retention
                    }

                    // Gently guide glancing auto-gas cars along the arena wall so they never pin nose-first
                    if (throttle > 0.1f && abs(vnOut) < 45f) {
                        val tang1X = -inNy
                        val tang1Y = inNx
                        val dot1 = cos(racer.angleRad) * tang1X + sin(racer.angleRad) * tang1Y
                        val tangAng = if (dot1 >= 0f) atan2(tang1Y, tang1X) else atan2(-tang1Y, -tang1X)
                        val diff = normalizeAngle(tangAng - racer.angleRad)
                        racer.angleRad = normalizeAngle(racer.angleRad + diff * (2.6f * dt).coerceAtMost(0.35f))
                    }
                }
            }
            return
        }

        // Circuit centerline corridor boundary
        val (postIdx, postDist) = TrackCatalog.nearestTrackPointLocal(
            track = track,
            x = racer.x,
            y = racer.y,
            hintIdx = racer.lastTrackIdx
        )
        racer.lastTrackIdx = postIdx
        val postPt = track.centerline[postIdx]

        val maxCorridor = if (rules.hardTrackBoundary) {
            (postPt.halfWidth - racer.carType.radiusPx * 0.45f).coerceAtLeast(42f)
        } else {
            postPt.halfWidth + 130f
        }
        if (postDist > maxCorridor) {
            val dx = racer.x - postPt.x
            val dy = racer.y - postPt.y
            val d = hypot(dx, dy).coerceAtLeast(1f)
            val nx = dx / d
            val ny = dy / d
            val safeDist = (maxCorridor - 2.5f).coerceAtLeast(24f)
            racer.x = postPt.x + nx * safeDist
            racer.y = postPt.y + ny * safeDist

            val vn = racer.vx * nx + racer.vy * ny
            if (vn > 0f) {
                val restitution = if (rules.hardTrackBoundary) 1.55f else 1.3f
                val retention = if (rules.hardTrackBoundary) 0.80f else 0.84f
                racer.vx = (racer.vx - restitution * vn * nx) * retention
                racer.vy = (racer.vy - restitution * vn * ny) * retention
            }
            if (throttle > 0.1f && abs(vn) < 40f && rules.hardTrackBoundary) {
                val nextPt = track.centerline[(postIdx + 2) % track.centerline.size]
                val tangAng = atan2(nextPt.y - postPt.y, nextPt.x - postPt.x)
                val diff = normalizeAngle(tangAng - racer.angleRad)
                racer.angleRad = normalizeAngle(racer.angleRad + diff * (2.6f * dt).coerceAtMost(0.35f))
            }
        }
    }

    private fun handleWorldAndPropCollisions(racer: MutableRacer) {
        val margin = racer.carType.radiusPx + 12f
        if (racer.x < margin) {
            racer.x = margin; racer.vx = abs(racer.vx) * 0.55f
        } else if (racer.x > track.worldWidth - margin) {
            racer.x = track.worldWidth - margin; racer.vx = -abs(racer.vx) * 0.55f
        }
        if (racer.y < margin) {
            racer.y = margin; racer.vy = abs(racer.vy) * 0.55f
        } else if (racer.y > track.worldHeight - margin) {
            racer.y = track.worldHeight - margin; racer.vy = -abs(racer.vy) * 0.55f
        }

        for (prop in track.props) {
            val dx = racer.x - prop.x
            val dy = racer.y - prop.y
            val dist = hypot(dx, dy)
            if (prop.isSolidObstacle && racer.jumpZ < 0.25f) {
                val minDist = prop.radius + racer.carType.radiusPx
                if (dist < minDist) {
                    val nx = if (dist > 0.01f) dx / dist else cos(racer.angleRad)
                    val ny = if (dist > 0.01f) dy / dist else sin(racer.angleRad)
                    racer.x = prop.x + nx * (minDist + 2f)
                    racer.y = prop.y + ny * (minDist + 2f)
                    val vn = racer.vx * nx + racer.vy * ny
                    if (vn < 0f) {
                        racer.vx = (racer.vx - 1.45f * vn * nx) * 0.82f
                        racer.vy = (racer.vy - 1.45f * vn * ny) * 0.82f
                    }
                }
            } else if (prop.shape == PropShape.MUD_POOL && dist < prop.radius && racer.jumpZ <= 0.05f) {
                if (racer.shieldMs == 0L && racer.overdriveMs == 0L) {
                    if (isArenaMode && racer.stunnedMs == 0L && racer.spinImmunityMs == 0L) {
                        racer.stunnedMs = 1050L
                        racer.spinImmunityMs = 2400L
                        racer.vx *= 0.42f
                        racer.vy *= 0.42f
                        if (flagCarrierId == racer.playerId) {
                            dropFlagFrom(racer, "spun on an oil patch")
                        }
                    } else {
                        racer.vx *= 0.94f
                        racer.vy *= 0.94f
                    }
                }
            }
        }
    }

    private fun handleBoostPads(racer: MutableRacer) {
        if (racer.jumpZ > 0.1f) return
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

    private fun handleRamps(racer: MutableRacer) {
        if (racer.jumpZ > 0.01f || racer.jumpCooldownMs > 0L) return
        val speed = hypot(racer.vx, racer.vy)
        if (speed < 130f) return
        for (ramp in track.ramps) {
            if (TrackCatalog.distance(racer.x, racer.y, ramp.x, ramp.y) <= ramp.radius + 10f) {
                racer.vz = 3.15f * ramp.impulse
                racer.jumpZ = 0.02f
                racer.jumpCooldownMs = 680L
                racer.vx *= 1.12f
                racer.vy *= 1.12f
                break
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
                racer.heldPowerUp = pool[random.nextInt(pool.size)]
                if (racer.isBot) {
                    racer.botPowerUpDelayMs = random.nextLong(700L, 2200L)
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
                        val carrierBonus = if (isArenaMode && flagCarrierId == other.playerId) -1200f else 0f
                        val rankPenalty = if (other.rank < racer.rank) 0f else 600f
                        TrackCatalog.distance(racer.x, racer.y, other.x, other.y) + rankPenalty + carrierBonus
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
                        racer.racePoints += 35
                        if (flagCarrierId == target.playerId) {
                            dropFlagFrom(target, "got zapped by EMP")
                        } else {
                            setBanner("${racer.name} zapped ${target.name} with EMP!", 1800L)
                        }
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
        if (racer.jumpZ > 0.12f) return
        val it = hazards.iterator()
        while (it.hasNext()) {
            val h = it.next()
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
                        owner.racePoints += 35
                    }
                    if (flagCarrierId == racer.playerId) {
                        dropFlagFrom(racer, "spun out on Glue Slick")
                    } else {
                        setBanner("${racer.name} spun out on Glue Slick!", 1600L)
                    }
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
                if (abs(a.jumpZ - b.jumpZ) > 0.28f) continue
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

                    // Arena mode: colliding with the flag carrier steals/transfers the flag!
                    if (isArenaMode && phase == RacePhase.RACING && flagTransferCooldownMs == 0L) {
                        if (flagCarrierId == a.playerId && b.stunnedMs == 0L) {
                            if (a.shieldMs > 0L) {
                                a.shieldMs = 0L
                                flagTransferCooldownMs = 750L
                                setBanner("${a.name}'s Shield blocked ${b.name}'s steal!", 1500L)
                            } else {
                                flagCarrierId = b.playerId
                                flagX = b.x
                                flagY = b.y
                                flagTransferCooldownMs = 950L
                                a.flagPickupImmunityMs = 950L
                                b.powerUpHits += 1
                                b.racePoints += 25
                                setBanner("${b.name} STOLE THE FLAG from ${a.name}!", 1900L)
                            }
                        } else if (flagCarrierId == b.playerId && a.stunnedMs == 0L) {
                            if (b.shieldMs > 0L) {
                                b.shieldMs = 0L
                                flagTransferCooldownMs = 750L
                                setBanner("${b.name}'s Shield blocked ${a.name}'s steal!", 1500L)
                            } else {
                                flagCarrierId = a.playerId
                                flagX = a.x
                                flagY = a.y
                                flagTransferCooldownMs = 950L
                                b.flagPickupImmunityMs = 950L
                                a.powerUpHits += 1
                                a.racePoints += 25
                                setBanner("${a.name} STOLE THE FLAG from ${b.name}!", 1900L)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun updateLapProgress(racer: MutableRacer) {
        val n = track.centerline.size
        val (newIdx, _) = TrackCatalog.nearestTrackPointLocal(track, racer.x, racer.y, racer.lastTrackIdx)
        val q1 = n / 4
        val q2 = n / 2
        val q3 = (3 * n) / 4

        val prevIdx = racer.checkpointIndex
        racer.checkpointIndex = newIdx

        when {
            newIdx < q1 -> {
                if (prevIdx >= q3 && racer.sectorMask == 0b1111) {
                    val lapTime = (raceTimeMs - racer.currentLapStartMs).coerceAtLeast(1000L)
                    racer.lapTimesMs.add(lapTime)
                    val prevBest = racer.bestLapMs
                    if (prevBest == null || lapTime < prevBest) {
                        racer.bestLapMs = lapTime
                    }
                    racer.racePoints += 50

                    if (winCondition != WinCondition.TIME_TRIAL_DISTANCE && racer.lap >= effectiveLaps) {
                        racer.finished = true
                        racer.finishTimeMs = raceTimeMs
                        if (!racer.isBot && firstHumanFinishTimeMs == null) {
                            firstHumanFinishTimeMs = raceTimeMs
                        }
                        setBanner("${racer.name} finished!", 1800L)
                    } else {
                        racer.lap += 1
                        racer.currentLapStartMs = raceTimeMs
                        racer.sectorMask = 0b0001
                        if (winCondition != WinCondition.TIME_TRIAL_DISTANCE && racer.lap == effectiveLaps) {
                            setBanner("${racer.name}: FINAL LAP!", 1500L)
                        }
                    }
                } else {
                    racer.sectorMask = racer.sectorMask or 0b0001
                }
            }
            newIdx in q1 until q2 -> {
                if ((racer.sectorMask and 0b0001) != 0) {
                    if ((racer.sectorMask and 0b0010) == 0) racer.racePoints += 10
                    racer.sectorMask = racer.sectorMask or 0b0010
                }
            }
            newIdx in q2 until q3 -> {
                if ((racer.sectorMask and 0b0010) != 0) {
                    if ((racer.sectorMask and 0b0100) == 0) racer.racePoints += 10
                    racer.sectorMask = racer.sectorMask or 0b0100
                }
            }
            else -> {
                if ((racer.sectorMask and 0b0100) != 0) {
                    if ((racer.sectorMask and 0b1000) == 0) racer.racePoints += 10
                    racer.sectorMask = racer.sectorMask or 0b1000
                }
            }
        }
    }

    private fun awardFinalPointsBonuses() {
        val byProgress = racers.sortedByDescending { computeProgressScore(it) }
        val finishBonuses = listOf(100, 65, 40, 20)
        byProgress.forEachIndexed { idx, r ->
            r.racePoints += finishBonuses.getOrElse(idx) { 10 }
        }
    }

    private fun computeProgressScore(racer: MutableRacer): Float {
        if (isArenaMode) {
            val carrierBonus = if (flagCarrierId == racer.playerId) 5f else 0f
            return racer.flagCarryMs.toFloat() * 10f + carrierBonus + racer.powerUpHits * 0.5f
        }
        if (winCondition == WinCondition.CHAMPIONSHIP_POINTS) {
            return racer.racePoints * 10000f + computeDistanceScore(racer)
        }
        return computeDistanceScore(racer)
    }

    private fun computeDistanceScore(racer: MutableRacer): Float {
        if (racer.finished && winCondition != WinCondition.TIME_TRIAL_DISTANCE) {
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
        if (isArenaMode) {
            return computeArenaBotInput(racer, cruise)
        }
        val pts = track.centerline
        val (closestIdx, _) = TrackCatalog.nearestTrackPointLocal(track, racer.x, racer.y, racer.lastTrackIdx)
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

    private fun computeArenaBotInput(racer: MutableRacer, cruise: Boolean): ControlInput {
        val cx = track.worldWidth * 0.5f
        val cy = track.worldHeight * 0.5f
        val carrierId = flagCarrierId

        var targetX: Float
        var targetY: Float

        if (carrierId == racer.playerId) {
            // Bot holds the flag: orbit cleanly inside the arena while steering away from closest chaser
            val radFromCenter = atan2(racer.y - cy, racer.x - cx)
            val orbitAng = radFromCenter + 0.65f
            targetX = cx + cos(orbitAng) * 320f
            targetY = cy + sin(orbitAng) * 235f

            val chaser = racers
                .filter { it.playerId != racer.playerId }
                .minByOrNull { TrackCatalog.distance(racer.x, racer.y, it.x, it.y) }
            if (chaser != null) {
                val d = TrackCatalog.distance(racer.x, racer.y, chaser.x, chaser.y)
                if (d < 260f && d > 1f) {
                    targetX += ((racer.x - chaser.x) / d) * 180f
                    targetY += ((racer.y - chaser.y) / d) * 180f
                }
            }
        } else if (carrierId != null) {
            // Intercept opponent carrying the flag
            val carrier = racers.find { it.playerId == carrierId }
            if (carrier != null) {
                targetX = carrier.x + carrier.vx * 0.16f
                targetY = carrier.y + carrier.vy * 0.16f
            } else {
                targetX = flagX
                targetY = flagY
            }
        } else {
            // Loose flag: race straight to it (unless temporarily immune after dropping)
            if (racer.flagPickupImmunityMs > 0L) {
                targetX = cx
                targetY = cy
            } else {
                targetX = flagX
                targetY = flagY
            }
        }

        // Steer slightly around oil/mud slicks when carrying the flag
        if (carrierId == racer.playerId) {
            for (prop in track.props) {
                if (prop.shape == PropShape.MUD_POOL) {
                    val d = TrackCatalog.distance(racer.x, racer.y, prop.x, prop.y)
                    if (d < prop.radius + 95f && d > 1f) {
                        targetX += ((racer.x - prop.x) / d) * 170f
                        targetY += ((racer.y - prop.y) / d) * 170f
                    }
                }
            }
        }

        val desiredAngle = atan2(targetY - racer.y, targetX - racer.x)
        val angleDiff = normalizeAngle(desiredAngle - racer.angleRad)
        val steer = (angleDiff / 0.6f).coerceIn(-1f, 1f)
        val throttle = when {
            cruise -> 0.45f
            abs(angleDiff) > 1.05f -> 0.58f
            else -> 0.98f
        }
        return ControlInput(throttle = throttle, steer = steer, usePowerUp = false)
    }

    private fun setBanner(text: String, durationMs: Long) {
        bannerText = text
        bannerTimerMs = durationMs
    }

    private fun buildSnapshot(countdownSec: Int): RaceSnapshot {
        val graceRem = firstHumanFinishTimeMs?.let { firstMs ->
            if (phase == RacePhase.RACING) (FINISH_GRACE_MS - (raceTimeMs - firstMs)).coerceAtLeast(0L) else 0L
        } ?: 0L

        return RaceSnapshot(
            raceTimeMs = raceTimeMs,
            countdownSec = countdownSec,
            phase = phase.name,
            trackId = track.id.name,
            totalLaps = effectiveLaps,
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
                    jumpZ = r.jumpZ,
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
                    powerUpHits = r.powerUpHits,
                    racePoints = r.racePoints,
                    flagCarryMs = r.flagCarryMs
                )
            },
            itemBoxes = itemBoxes.toList(),
            hazards = hazards.toList(),
            eventBanner = bannerText,
            winCondition = winCondition.name,
            timeLimitSec = rules.timeLimitSec,
            activeEvent = activeEvent.name,
            eventRemainingMs = eventRemainingMs,
            windAngleRad = windAngleRad,
            windStrength = windStrength,
            rainPuddles = rainPuddles.toList(),
            postFinishGraceMs = graceRem,
            gameMode = if (isArenaMode) GameMode.ARENA.name else GameMode.RACE.name,
            arenaShape = track.arenaShape?.name,
            arenaTargetMs = arenaTargetMs,
            arenaFlag = if (isArenaMode) {
                ArenaFlagState(
                    x = flagX,
                    y = flagY,
                    carrierId = flagCarrierId
                )
            } else {
                null
            }
        )
    }

    companion object {
        const val FINISH_GRACE_MS = 20_000L

        fun normalizeAngle(angle: Float): Float {
            var a = angle
            val twoPi = (2.0 * PI).toFloat()
            while (a > PI) a -= twoPi
            while (a < -PI) a += twoPi
            return a
        }
    }
}

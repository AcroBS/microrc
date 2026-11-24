package com.example.engine

import com.example.data.TrackCatalog
import com.example.model.ActiveRocketState
import com.example.model.ArenaFlagState
import com.example.model.CarSpeedPreset
import com.example.model.ControlInput
import com.example.model.ExternalEventType
import com.example.model.FieldPickupType
import com.example.model.GameMode
import com.example.model.HazardType
import com.example.model.ItemBoxState
import com.example.model.LobbyPlayer
import com.example.model.PortalPairState
import com.example.model.PowerUpType
import com.example.model.PropShape
import com.example.model.RacePhase
import com.example.model.RaceRulesSettings
import com.example.model.RaceSnapshot
import com.example.model.RacerState
import com.example.model.RcCarType
import com.example.model.StuntVariant
import com.example.model.TrackDefinition
import com.example.model.TrackExplosion
import com.example.model.TrackFieldPickup
import com.example.model.TrackHazard
import com.example.model.TrackPoint
import com.example.model.TrackPuddle
import com.example.model.WinCondition
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class RacePhysicsEngine(
    private var track: TrackDefinition,
    private val totalLaps: Int,
    players: List<LobbyPlayer>,
    private val rules: RaceRulesSettings = RaceRulesSettings(),
    private val random: Random = Random.Default,
    stuntSequenceOverride: List<StuntVariant>? = null,
    private val isHybridTour: Boolean = false,
    private val hybridStageNumber: Int = 1,
    private val nextHybridModeName: String? = null,
    private val isDailyChallenge: Boolean = false
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
        var prevX: Float = x,
        var prevY: Float = y,
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
        var botPowerUpDelayMs: Long = 0L,
        var minesAvailable: Int = 0,
        var lastMineGrantedLap: Int = 1,
        var lastNitroGrantedLap: Int = 0,
        var botMineDelayMs: Long = 0L,
        var invertedSteerMs: Long = 0L,
        var portalCooldownMs: Long = 0L,
        var wallBumpCooldownMs: Long = 0L,
        var collisionSeq: Int = 0,
        var portalWarpSeq: Int = 0,
        var electroShockAuraMs: Long = 0L,
        var electroParalyzedMs: Long = 0L,
        var electroImmunityMs: Long = 0L,
        var laserBeamMs: Long = 0L,
        var stalledMs: Long = 0L
    )

    private data class MutableRocket(
        val id: Int,
        val ownerId: String,
        var targetId: String?,
        var x: Float,
        var y: Float,
        var prevX: Float = x,
        var prevY: Float = y,
        var angleRad: Float,
        val speed: Float,
        var remainingMs: Long
    )

    private val isStuntMode: Boolean = track.isStunt || rules.gameModeEnum == GameMode.STUNTS
    private val isArenaMode: Boolean = !isStuntMode && (track.isArena || rules.gameModeEnum == GameMode.ARENA)
    private val isDriftMode: Boolean = !isStuntMode && !isArenaMode && com.example.drift.DriftFeature.isDriftMode(rules.gameModeEnum)
    private val winCondition: WinCondition = if (isDriftMode) WinCondition.FIRST_TO_FINISH else rules.winConditionEnum
    private val speedPreset: CarSpeedPreset = rules.carSpeedEnum
    private val effectiveLaps: Int = totalLaps.coerceIn(1, 9)
    private val timeLimitMs: Long = rules.timeLimitSec.coerceIn(30, 600) * 1000L
    private val arenaTargetMs: Long = rules.arenaTargetMinutes.coerceIn(1, 10) * 60_000L

    private val stuntController: StuntArenaController? = if (isStuntMode) {
        val seq = stuntSequenceOverride?.takeIf { it.isNotEmpty() } ?: run {
            val shuffled = StuntVariant.entries.shuffled(random)
            if (isHybridTour) {
                shuffled.take(rules.hybridStuntRounds.coerceIn(1, 4))
            } else {
                shuffled
            }
        }
        StuntArenaController(seq, random)
    } else {
        null
    }

    private val racers: MutableList<MutableRacer> = players.mapIndexed { idx, p ->
        val (sx, sy, angle) = TrackCatalog.gridStartTransform(track, idx)
        val car = runCatching { RcCarType.valueOf(p.carType) }.getOrDefault(RcCarType.VOLT_BUGGY)
        val startTrackIdx = (track.centerline.size - 4 - (idx / 2) * 3).mod(track.centerline.size.coerceAtLeast(1))
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
            botLaneOffset = ((idx % 3) - 1) * 26f,
            minesAvailable = 0,
            lastMineGrantedLap = 1,
            botMineDelayMs = 0L
        )
    }.toMutableList()

    private val itemBoxes: MutableList<ItemBoxState> = track.itemSpawns.map {
        ItemBoxState(id = it.id, x = it.x, y = it.y, available = true, respawnMs = 0L)
    }.toMutableList()

    private val hazards: MutableList<TrackHazard> = mutableListOf()
    private var nextHazardId = 1

    private val activeRockets: MutableList<MutableRocket> = mutableListOf()
    private var nextRocketId = 1

    private val explosions: MutableList<TrackExplosion> = mutableListOf()
    private var nextExplosionId = 1

    // Random direct-trigger track power-ups (Position Swap double-arrow & 5s Steering Invert)
    private val fieldPickups: MutableList<TrackFieldPickup> = mutableListOf()
    private var nextFieldPickupId = 1
    private var nextFieldPickupSpawnMs: Long = random.nextLong(3600L, 6200L)
    private var lastFieldPickupType: FieldPickupType? = null

    // Random paired Red & Blue portals (holes in the ground, forward-only teleport)
    private var portalPair: PortalPairState? = null
    private var nextPortalId = 1
    private var nextPortalSpawnMs: Long = random.nextLong(6800L, 10800L)
    private var activePortalEntryIdx: Int = -1
    private var activePortalExitIdx: Int = -1

    private var elapsedMs: Long = 0L
    private var raceTimeMs: Long = 0L
    private var phase: RacePhase = RacePhase.COUNTDOWN
    private var bannerText: String? = null
    private var bannerTimerMs: Long = 0L
    private var firstHumanFinishTimeMs: Long? = null

    // Arena Hold-the-Flag & Sudden Death Shrinking Arena state
    private var flagX: Float = track.worldWidth * 0.5f
    private var flagY: Float = track.worldHeight * 0.5f
    private var flagCarrierId: String? = null
    private var flagTransferCooldownMs: Long = 0L
    private var arenaSuddenDeathStage: Int = 0
    private var suddenDeathWarningShown: Boolean = false

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

    private val latestInputs = ConcurrentHashMap<String, ControlInput>()
    private val pendingPowerUpTrigger: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val pendingMineDropTrigger: MutableSet<String> = ConcurrentHashMap.newKeySet()

    init {
        if (stuntController != null) {
            track = stuntController.trackForCurrentRound()
            itemBoxes.clear()
            stuntController.initializeFirstRound(racers.size) { turnIdx, variant ->
                setupStuntTurn(turnIdx, variant)
            }
        } else {
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
            if (isDailyChallenge || isDriftMode) {
                itemBoxes.clear()
                fieldPickups.clear()
                nextFieldPickupSpawnMs = Long.MAX_VALUE
                racers.forEach { r ->
                    r.heldPowerUp = PowerUpType.TURBO_NITRO
                    r.lastNitroGrantedLap = 1
                    r.minesAvailable = 0
                    r.lastMineGrantedLap = Int.MAX_VALUE
                }
                if (isDailyChallenge && !isDriftMode) {
                    setupFixedDailyPortals()
                }
            } else {
                randomizeAllItemBoxes()
            }
        }
    }

    private fun computeDeterministicPortalSeed(): Long {
        var h = 0x517CC1B727220A95L xor track.id.name.hashCode().toLong()
        h = h * 31L + track.centerline.size.toLong()
        track.centerline.forEachIndexed { idx, pt ->
            if (idx % 4 == 0) {
                h = h * 31L + (pt.x * 10f).toLong()
                h = h * 31L + (pt.y * 10f).toLong()
            }
        }
        return h
    }

    private fun setupFixedDailyPortals() {
        val pts = track.centerline
        if (pts.size < 24 || track.isArena || track.isStunt) {
            portalPair = null
            activePortalEntryIdx = -1
            activePortalExitIdx = -1
            return
        }
        val portalRng = Random(computeDeterministicPortalSeed())
        val entryIsRed = portalRng.nextBoolean()
        val maxSpan = (pts.size / 3).coerceIn(14, 34)
        val minSpan = 10.coerceAtMost(maxSpan)
        val span = if (maxSpan > minSpan) portalRng.nextInt(minSpan, maxSpan + 1) else minSpan
        val minEntry = 8.coerceAtMost((pts.size / 6).coerceAtLeast(1))
        val maxEntry = (pts.size - span - 5).coerceAtLeast(minEntry + 1)

        var chosenEntryIdx = minEntry
        var chosenEntryX = pts[minEntry].x
        var chosenEntryY = pts[minEntry].y
        var chosenExitIdx = (minEntry + span).coerceAtMost(pts.size - 4)
        var chosenExitX = pts[chosenExitIdx].x
        var chosenExitY = pts[chosenExitIdx].y
        var chosenExitAng = 0f
        var bestClearanceScore = -1f

        val featurePoints = track.boostPads.map { it.x to it.y } + track.ramps.map { it.x to it.y }
        repeat(8) {
            val candidateEntryIdx = if (maxEntry > minEntry) portalRng.nextInt(minEntry, maxEntry) else minEntry
            val candidateExitIdx = (candidateEntryIdx + span).coerceAtMost(pts.size - 4)

            val ePt = pts[candidateEntryIdx]
            val eNext = pts[(candidateEntryIdx + 1) % pts.size]
            val eAng = atan2(eNext.y - ePt.y, eNext.x - ePt.x)
            val eLat = (portalRng.nextFloat() * 2f - 1f) * (ePt.halfWidth * 0.28f)
            val entryX = ePt.x + (-sin(eAng)) * eLat
            val entryY = ePt.y + cos(eAng) * eLat

            val xPt = pts[candidateExitIdx]
            val xNext = pts[(candidateExitIdx + 1) % pts.size]
            val exitAng = atan2(xNext.y - xPt.y, xNext.x - xPt.x)
            val xLat = (portalRng.nextFloat() * 2f - 1f) * (xPt.halfWidth * 0.24f)
            val exitX = xPt.x + (-sin(exitAng)) * xLat
            val exitY = xPt.y + cos(exitAng) * xLat

            val clearance = if (featurePoints.isEmpty()) {
                999f
            } else {
                featurePoints.minOf { (fx, fy) ->
                    minOf(
                        TrackCatalog.distance(entryX, entryY, fx, fy),
                        TrackCatalog.distance(exitX, exitY, fx, fy)
                    )
                }
            }
            if (clearance > bestClearanceScore) {
                bestClearanceScore = clearance
                chosenEntryIdx = candidateEntryIdx
                chosenExitIdx = candidateExitIdx
                chosenEntryX = entryX
                chosenEntryY = entryY
                chosenExitX = exitX
                chosenExitY = exitY
                chosenExitAng = exitAng
            }
        }

        activePortalEntryIdx = chosenEntryIdx
        activePortalExitIdx = chosenExitIdx
        portalPair = PortalPairState(
            id = nextPortalId++,
            entryX = chosenEntryX,
            entryY = chosenEntryY,
            entryColorIsRed = entryIsRed,
            exitX = chosenExitX,
            exitY = chosenExitY,
            exitAngleRad = chosenExitAng,
            remainingMs = 3_600_000L
        )
    }

    private fun randomizeAllItemBoxes() {
        if (isStuntMode || itemBoxes.isEmpty()) return
        val placed = mutableListOf<Pair<Float, Float>>()
        val baseAvoid = buildList {
            racers.forEach { add(it.x to it.y) }
            if (isArenaMode) add(flagX to flagY)
        }
        val activePoly = if (isArenaMode) currentArenaPolygon() else null
        for (i in itemBoxes.indices) {
            val (rx, ry) = TrackCatalog.sampleRandomItemBoxPoint(
                track = track,
                rng = random,
                avoidPoints = baseAvoid + placed,
                boundaryOverride = activePoly
            )
            placed.add(rx to ry)
            itemBoxes[i] = itemBoxes[i].copy(x = rx, y = ry)
        }
    }

    private fun setupStuntTurn(turnIdx: Int, variant: StuntVariant) {
        track = stuntController?.trackForCurrentRound() ?: TrackCatalog.getStuntTrack(variant)
        latestInputs.clear()
        pendingPowerUpTrigger.clear()
        pendingMineDropTrigger.clear()
        hazards.clear()
        activeRockets.clear()
        fieldPickups.clear()
        portalPair = null
        racers.forEachIndexed { idx, r ->
            r.vx = 0f
            r.vy = 0f
            r.jumpZ = 0f
            r.vz = 0f
            r.boostMs = 0L
            r.shieldMs = 0L
            r.stunnedMs = 0L
            r.overdriveMs = 0L
            r.invertedSteerMs = 0L
            r.electroShockAuraMs = 0L
            r.electroParalyzedMs = 0L
            r.electroImmunityMs = 0L
            r.laserBeamMs = 0L
            r.minesAvailable = 0
            if (idx == turnIdx) {
                r.x = 230f
                r.y = 700f
                r.prevX = 230f
                r.prevY = 700f
                r.angleRad = 0f
                r.heldPowerUp = PowerUpType.TURBO_NITRO
                r.botPowerUpDelayMs = 1400L
                r.lap = (stuntController?.roundIndexZeroBased ?: 0) + 1
            } else {
                val px = 250f + idx * 95f
                val py = 235f
                r.x = px
                r.y = py
                r.prevX = px
                r.prevY = py
                r.angleRad = (PI / 2.0).toFloat()
                r.heldPowerUp = null
            }
        }
    }

    fun stuntSequence(): List<StuntVariant> = stuntController?.sequence.orEmpty()

    fun positionActiveStuntRacerForTest(
        x: Float,
        y: Float,
        vx: Float = 0f,
        vy: Float = 0f,
        angleRad: Float = 0f,
        jumpZ: Float = 0f,
        vz: Float = 0f
    ) {
        val sc = stuntController ?: return
        val r = racers.getOrNull(sc.turnIndex) ?: return
        r.x = x
        r.y = y
        r.prevX = x
        r.prevY = y
        r.vx = vx
        r.vy = vy
        r.angleRad = angleRad
        r.jumpZ = jumpZ
        r.vz = vz
        sc.syncForTestPosition(x, jumpZ)
    }

    fun submitInput(playerId: String, input: ControlInput) {
        latestInputs[playerId] = input
        if (input.usePowerUp) {
            pendingPowerUpTrigger.add(playerId)
        }
        if (input.dropMine) {
            pendingMineDropTrigger.add(playerId)
        }
    }

    /**
     * Exposed for deterministic Arena & Circuit unit tests.
     */
    fun setFlagStateForTest(x: Float, y: Float, carrierId: String? = null) {
        flagX = x
        flagY = y
        flagCarrierId = carrierId
        flagTransferCooldownMs = 0L
    }

    fun givePowerUpForTest(playerId: String, powerUp: PowerUpType, triggerImmediately: Boolean = false) {
        val racer = racers.find { it.playerId == playerId } ?: return
        racer.heldPowerUp = powerUp
        if (triggerImmediately) {
            activatePowerUp(racer)
        }
    }

    fun grantMineForTest(playerId: String, count: Int = 1) {
        if (isDailyChallenge || isDriftMode || isArenaMode || isStuntMode) return
        val racer = racers.find { it.playerId == playerId } ?: return
        racer.minesAvailable = count.coerceIn(0, 1)
    }

    internal fun simulateCompleteLapForTest(playerId: String) {
        val racer = racers.find { it.playerId == playerId } ?: return
        onAdvanceToNextLap(racer)
    }

    fun spawnFieldPickupForTest(
        x: Float,
        y: Float,
        type: FieldPickupType,
        durationMs: Long = 16000L
    ) {
        fieldPickups.add(
            TrackFieldPickup(
                id = nextFieldPickupId++,
                x = x,
                y = y,
                type = type.name,
                remainingMs = durationMs
            )
        )
    }

    fun spawnPortalsForTest(
        entryX: Float,
        entryY: Float,
        exitX: Float,
        exitY: Float,
        exitAngleRad: Float,
        entryColorIsRed: Boolean = true,
        entryTrackIdx: Int = -1,
        exitTrackIdx: Int = -1,
        durationMs: Long = 14000L
    ) {
        activePortalEntryIdx = entryTrackIdx
        activePortalExitIdx = exitTrackIdx
        portalPair = PortalPairState(
            id = nextPortalId++,
            entryX = entryX,
            entryY = entryY,
            entryColorIsRed = entryColorIsRed,
            exitX = exitX,
            exitY = exitY,
            exitAngleRad = exitAngleRad,
            remainingMs = durationMs
        )
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

    private fun currentArenaPolygon(): List<TrackPoint> =
        TrackCatalog.scaledArenaPolygon(track, arenaSuddenDeathStage)

    private fun spawnRainPuddles() {
        if (track.isArena) {
            val activePoly = currentArenaPolygon()
            val shrinkScale = TrackCatalog.arenaShrinkScaleForStage(arenaSuddenDeathStage)
            val count = if (arenaSuddenDeathStage >= 2) random.nextInt(4, 7) else random.nextInt(7, 11)
            val placed = mutableListOf<Pair<Float, Float>>()
            repeat(count) {
                val (px, py) = TrackCatalog.sampleArenaInteriorPoint(
                    track = track,
                    rng = random,
                    minWallClearance = 135f * shrinkScale,
                    avoidPoints = placed,
                    minAvoidDist = 140f * shrinkScale,
                    boundaryOverride = activePoly
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

    private fun sampleTrackFeaturePoint(extraAvoid: List<Pair<Float, Float>>): Pair<Float, Float>? {
        val avoid = buildList {
            addAll(extraAvoid)
            itemBoxes.forEach { add(it.x to it.y) }
            track.boostPads.forEach { add(it.x to it.y) }
            track.ramps.forEach { add(it.x to it.y) }
            track.props.forEach { add(it.x to it.y) }
            fieldPickups.forEach { add(it.x to it.y) }
            portalPair?.let {
                add(it.entryX to it.entryY)
                add(it.exitX to it.exitY)
            }
        }

        if (track.isArena) {
            val shrinkScale = TrackCatalog.arenaShrinkScaleForStage(arenaSuddenDeathStage)
            return TrackCatalog.sampleArenaInteriorPoint(
                track = track,
                rng = random,
                minWallClearance = 145f * shrinkScale,
                avoidPoints = avoid,
                minAvoidDist = 110f * shrinkScale,
                boundaryOverride = currentArenaPolygon()
            )
        }

        val pts = track.centerline
        if (pts.size < 24) return null
        repeat(24) {
            val idx = random.nextInt(8, pts.size - 6)
            val pt = pts[idx]
            val next = pts[(idx + 1) % pts.size]
            val tangAng = atan2(next.y - pt.y, next.x - pt.x)
            val perpX = -sin(tangAng)
            val perpY = cos(tangAng)
            val offset = (random.nextFloat() * 2f - 1f) * (pt.halfWidth * 0.74f)
            val cx = pt.x + perpX * offset
            val cy = pt.y + perpY * offset
            val clear = avoid.all { (ax, ay) -> TrackCatalog.distance(cx, cy, ax, ay) >= 92f }
            if (clear) return cx to cy
        }
        val fallbackIdx = random.nextInt(8, pts.size - 6)
        return pts[fallbackIdx].x to pts[fallbackIdx].y
    }

    private fun updateFieldPickups(dtMs: Long) {
        if (isDailyChallenge || isDriftMode || phase != RacePhase.RACING) return

        val it = fieldPickups.iterator()
        while (it.hasNext()) {
            val fp = it.next()
            val rem = fp.remainingMs - dtMs
            if (rem <= 0L) {
                it.remove()
            } else {
                val idx = fieldPickups.indexOf(fp)
                if (idx >= 0) fieldPickups[idx] = fp.copy(remainingMs = rem)
            }
        }

        if (racers.size > 1 && fieldPickups.size < MAX_FIELD_PICKUPS) {
            nextFieldPickupSpawnMs -= dtMs
            if (nextFieldPickupSpawnMs <= 0L) {
                nextFieldPickupSpawnMs = random.nextLong(5800L, 9400L)
                val pos = sampleTrackFeaturePoint(racers.map { it.x to it.y })
                if (pos != null) {
                    val nextType = when (lastFieldPickupType) {
                        null -> if (random.nextBoolean()) FieldPickupType.POSITION_SWAP else FieldPickupType.STEER_INVERT
                        FieldPickupType.POSITION_SWAP -> if (random.nextFloat() < 0.65f) FieldPickupType.STEER_INVERT else FieldPickupType.POSITION_SWAP
                        FieldPickupType.STEER_INVERT -> if (random.nextFloat() < 0.65f) FieldPickupType.POSITION_SWAP else FieldPickupType.STEER_INVERT
                    }
                    lastFieldPickupType = nextType
                    fieldPickups.add(
                        TrackFieldPickup(
                            id = nextFieldPickupId++,
                            x = pos.first,
                            y = pos.second,
                            type = nextType.name,
                            remainingMs = random.nextLong(15000L, 19500L)
                        )
                    )
                }
            }
        }
    }

    private fun updatePortals(dtMs: Long) {
        if (isDailyChallenge || isDriftMode || phase != RacePhase.RACING) return

        val current = portalPair
        if (current != null) {
            val rem = current.remainingMs - dtMs
            if (rem <= 0L) {
                portalPair = null
                activePortalEntryIdx = -1
                activePortalExitIdx = -1
                nextPortalSpawnMs = random.nextLong(8500L, 13500L)
            } else {
                portalPair = current.copy(remainingMs = rem)
            }
            return
        }

        nextPortalSpawnMs -= dtMs
        if (nextPortalSpawnMs <= 0L) {
            spawnRandomPortalPair()
        }
    }

    private fun spawnRandomPortalPair() {
        val entryIsRed = random.nextBoolean()
        if (track.isArena) {
            if (arenaSuddenDeathStage >= 3) {
                nextPortalSpawnMs = 8000L
                return
            }
            val activePoly = currentArenaPolygon()
            val shrinkScale = TrackCatalog.arenaShrinkScaleForStage(arenaSuddenDeathStage)
            val baseAvoid = racers.map { it.x to it.y } +
                track.props.map { it.x to it.y } +
                hazards.map { it.x to it.y }
            val (ax, ay) = TrackCatalog.sampleArenaInteriorPoint(
                track = track,
                rng = random,
                minWallClearance = 165f * shrinkScale,
                avoidPoints = baseAvoid,
                minAvoidDist = 125f * shrinkScale,
                boundaryOverride = activePoly
            )
            val (bx, by) = TrackCatalog.sampleArenaInteriorPoint(
                track = track,
                rng = random,
                minWallClearance = 165f * shrinkScale,
                avoidPoints = baseAvoid + (ax to ay),
                minAvoidDist = 280f * shrinkScale,
                boundaryOverride = activePoly
            )
            val centerAng = atan2(track.worldHeight * 0.5f - by, track.worldWidth * 0.5f - bx)
            activePortalEntryIdx = -1
            activePortalExitIdx = -1
            portalPair = PortalPairState(
                id = nextPortalId++,
                entryX = ax,
                entryY = ay,
                entryColorIsRed = entryIsRed,
                exitX = bx,
                exitY = by,
                exitAngleRad = centerAng,
                remainingMs = random.nextLong(11500L, 14500L)
            )
            setBanner("RED & BLUE PORTALS OPENED!", 1800L)
            return
        }

        val pts = track.centerline
        if (pts.size < 48) return
        val maxSpan = (pts.size / 3).coerceIn(16, 34)
        val minSpan = 14.coerceAtMost(maxSpan)
        val span = if (maxSpan > minSpan) random.nextInt(minSpan, maxSpan + 1) else minSpan
        val maxEntry = (pts.size - span - 6).coerceAtLeast(10)
        val entryIdx = random.nextInt(8, maxEntry)
        val exitIdx = (entryIdx + span).coerceAtMost(pts.size - 4)

        val ePt = pts[entryIdx]
        val eNext = pts[(entryIdx + 1) % pts.size]
        val eAng = atan2(eNext.y - ePt.y, eNext.x - ePt.x)
        val eLat = (random.nextFloat() * 2f - 1f) * (ePt.halfWidth * 0.30f)
        val entryX = ePt.x + (-sin(eAng)) * eLat
        val entryY = ePt.y + cos(eAng) * eLat

        val xPt = pts[exitIdx]
        val xNext = pts[(exitIdx + 1) % pts.size]
        val exitAng = atan2(xNext.y - xPt.y, xNext.x - xPt.x)
        val xLat = (random.nextFloat() * 2f - 1f) * (xPt.halfWidth * 0.25f)
        val exitX = xPt.x + (-sin(exitAng)) * xLat
        val exitY = xPt.y + cos(exitAng) * xLat

        activePortalEntryIdx = entryIdx
        activePortalExitIdx = exitIdx
        portalPair = PortalPairState(
            id = nextPortalId++,
            entryX = entryX,
            entryY = entryY,
            entryColorIsRed = entryIsRed,
            exitX = exitX,
            exitY = exitY,
            exitAngleRad = exitAng,
            remainingMs = random.nextLong(11500L, 14500L)
        )
        setBanner("PORTALS OPENED — WARP FORWARD!", 1800L)
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
                val startBanner = when {
                    stuntController != null -> {
                        val starter = racers.getOrNull(stuntController.turnIndex)?.name ?: "DRIVER"
                        "$starter UP — ${stuntController.activeVariant.displayName.uppercase()}!"
                    }
                    isArenaMode -> "GO! CAPTURE THE FLAG!"
                    else -> "GO! RACE ON!"
                }
                setBanner(startBanner, 1800L)
            } else {
                val secLeft = (((countdownDuration - elapsedMs) / 1000L) + 1).toInt().coerceIn(1, 3)
                return buildSnapshot(countdownSec = secLeft)
            }
        }

        if (stuntController != null) {
            return stepStuntMatch(stuntController, dtMs, dt)
        }

        if (phase == RacePhase.RACING) {
            raceTimeMs += dtMs
            if (isArenaMode) {
                updateArenaSuddenDeath()
            }
            updateExternalEvents(dtMs, dt)
            updateFieldPickups(dtMs)
            updatePortals(dtMs)
        }

        // Update active mine explosions
        val expIt = explosions.iterator()
        while (expIt.hasNext()) {
            val exp = expIt.next()
            val newAge = exp.ageMs + dtMs
            if (newAge >= exp.durationMs) {
                expIt.remove()
            } else {
                val idx = explosions.indexOf(exp)
                if (idx >= 0) explosions[idx] = exp.copy(ageMs = newAge)
            }
        }

        // Update item boxes respawn (fixed spawn points for Daily Challenge; fresh random spot otherwise)
        for (i in itemBoxes.indices) {
            val box = itemBoxes[i]
            if (!box.available) {
                val rem = (box.respawnMs - dtMs).coerceAtLeast(0L)
                itemBoxes[i] = if (rem == 0L) {
                    if (isDailyChallenge) {
                        val orig = track.itemSpawns.getOrNull(i)
                        box.copy(
                            x = orig?.x ?: box.x,
                            y = orig?.y ?: box.y,
                            available = true,
                            respawnMs = 0L
                        )
                    } else {
                        val avoid = buildList {
                            racers.forEach { add(it.x to it.y) }
                            itemBoxes.forEachIndexed { idx, other ->
                                if (idx != i && other.available) add(other.x to other.y)
                            }
                            hazards.forEach { add(it.x to it.y) }
                            if (isArenaMode) add(flagX to flagY)
                        }
                        val (nx, ny) = TrackCatalog.sampleRandomItemBoxPoint(
                            track = track,
                            rng = random,
                            avoidPoints = avoid,
                            boundaryOverride = if (isArenaMode) currentArenaPolygon() else null
                        )
                        box.copy(x = nx, y = ny, available = true, respawnMs = 0L)
                    }
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
                else -> latestInputs[racer.playerId] ?: ControlInput(
                    throttle = if (rules.autoAccelerate) 1f else 0f,
                    steer = 0f
                )
            }

            if (phase == RacePhase.RACING && !racer.finished) {
                val shouldUsePowerUp = if (racer.isBot) {
                    val held = racer.heldPowerUp
                    when {
                        held == null -> false
                        held == PowerUpType.ELECTRO_SHOCK -> {
                            val rivalNear = racers.any {
                                it.playerId != racer.playerId &&
                                    !it.finished &&
                                    TrackCatalog.distance(racer.x, racer.y, it.x, it.y) <= ELECTRO_SHOCK_RADIUS_PX * 0.92f
                            }
                            rivalNear || racer.botPowerUpDelayMs <= 0L
                        }
                        held == PowerUpType.RED_LASER -> {
                            val rivalInSight = racers.any {
                                if (it.playerId == racer.playerId || it.finished) {
                                    false
                                } else {
                                    val d = TrackCatalog.distance(racer.x, racer.y, it.x, it.y)
                                    val ang = atan2(it.y - racer.y, it.x - racer.x)
                                    d <= LASER_LENGTH_PX + 24f && abs(normalizeAngle(ang - racer.angleRad)) < 0.28f
                                }
                            }
                            rivalInSight || racer.botPowerUpDelayMs <= 0L
                        }
                        else -> racer.botPowerUpDelayMs <= 0L
                    }
                } else {
                    pendingPowerUpTrigger.remove(racer.playerId)
                }
                if (shouldUsePowerUp) {
                    activatePowerUp(racer)
                }

                val shouldDropMine = if (isArenaMode || isStuntMode) {
                    pendingMineDropTrigger.remove(racer.playerId)
                    false
                } else if (racer.isBot) {
                    racer.minesAvailable > 0 && racer.botMineDelayMs <= 0L
                } else {
                    pendingMineDropTrigger.remove(racer.playerId)
                }
                if (shouldDropMine) {
                    dropMine(racer)
                }
            } else {
                pendingPowerUpTrigger.remove(racer.playerId)
                pendingMineDropTrigger.remove(racer.playerId)
            }

            integrateCarPhysics(racer, input, dt)
            handleWorldAndPropCollisions(racer, input.throttle)
            if (phase == RacePhase.RACING && !racer.finished) {
                if (!racer.isBot) {
                    val curSpeed = hypot(racer.vx, racer.vy)
                    if (curSpeed < 60f && racer.electroParalyzedMs == 0L && racer.stunnedMs == 0L) {
                        racer.stalledMs += dtMs
                    } else {
                        racer.stalledMs = (racer.stalledMs - dtMs * 2L).coerceAtLeast(0L)
                    }
                }
                handleBoostPads(racer)
                handleRamps(racer)
                handleItemPickups(racer)
                handleFieldPickups(racer)
                handlePortals(racer)
                handleHazards(racer)
                if (!isArenaMode) {
                    updateLapProgress(racer)
                }
            }
        }

        // Car-to-car bumper collisions (includes Arena flag transfer on collision!)
        resolveCarCollisions()

        // Active Electro Shock proximity aura, forward Red Laser beams & Homing Rockets
        updateElectroShockAuras()
        updateLaserBeams()
        if (phase == RacePhase.RACING) {
            updateRockets(dtMs, dt)
        }

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
                } else if (raceTimeMs >= ARENA_SUDDEN_DEATH_END_MS) {
                    for (r in racers) {
                        r.finished = true
                        r.finishTimeMs = raceTimeMs
                    }
                    updateRankings()
                    val finaleWinner = racers.minByOrNull { it.rank } ?: racers.first()
                    phase = RacePhase.FINISHED
                    setBanner("SUDDEN DEATH OVER! ${finaleWinner.name} WINS!", 3500L)
                }
            } else {
                val humanRacers = racers.filter { !it.isBot }
                val firstHumanDoneMs = firstHumanFinishTimeMs
                val graceElapsedMs = if (firstHumanDoneMs != null) raceTimeMs - firstHumanDoneMs else 0L
                val graceExpired = firstHumanDoneMs != null && graceElapsedMs >= FINISH_GRACE_MS
                val unfinishedHumans = humanRacers.filter { !it.finished }
                val allRemainingHumansStalled = firstHumanDoneMs != null &&
                    unfinishedHumans.isNotEmpty() &&
                    graceElapsedMs >= STALLED_RIVAL_MIN_GRACE_MS &&
                    unfinishedHumans.all { it.stalledMs >= STALLED_RIVAL_THRESHOLD_MS }

                val timeLimitReached = winCondition == WinCondition.TIME_TRIAL_DISTANCE &&
                    raceTimeMs >= timeLimitMs
                val safetyPointsTimeout = winCondition == WinCondition.CHAMPIONSHIP_POINTS &&
                    humanRacers.all { it.finished }
                val allHumansDone = humanRacers.isNotEmpty() && humanRacers.all { it.finished }
                val allRacersDone = racers.all { it.finished }

                val shouldEndRace = when (winCondition) {
                    WinCondition.TIME_TRIAL_DISTANCE -> timeLimitReached || allRacersDone
                    WinCondition.FIRST_TO_FINISH, WinCondition.CHAMPIONSHIP_POINTS ->
                        allRacersDone || allHumansDone || safetyPointsTimeout || graceExpired || allRemainingHumansStalled
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
        if (racer.electroShockAuraMs > 0L) {
            racer.electroShockAuraMs = (racer.electroShockAuraMs - dtMs).coerceAtLeast(0L)
        }
        if (racer.electroParalyzedMs > 0L) {
            racer.electroParalyzedMs = (racer.electroParalyzedMs - dtMs).coerceAtLeast(0L)
            racer.vx = 0f
            racer.vy = 0f
        }
        if (racer.electroImmunityMs > 0L) {
            racer.electroImmunityMs = (racer.electroImmunityMs - dtMs).coerceAtLeast(0L)
        }
        if (racer.laserBeamMs > 0L) {
            racer.laserBeamMs = (racer.laserBeamMs - dtMs).coerceAtLeast(0L)
        }
        if (racer.invertedSteerMs > 0L) racer.invertedSteerMs = (racer.invertedSteerMs - dtMs).coerceAtLeast(0L)
        if (racer.portalCooldownMs > 0L) racer.portalCooldownMs = (racer.portalCooldownMs - dtMs).coerceAtLeast(0L)
        if (racer.wallBumpCooldownMs > 0L) racer.wallBumpCooldownMs = (racer.wallBumpCooldownMs - dtMs).coerceAtLeast(0L)
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
        if (racer.botMineDelayMs > 0L) {
            racer.botMineDelayMs = (racer.botMineDelayMs - dtMs).coerceAtLeast(0L)
        }
    }

    private fun updateArenaSuddenDeath() {
        if (!isArenaMode || phase != RacePhase.RACING) return
        if (racers.any { it.flagCarryMs >= arenaTargetMs }) return

        if (!suddenDeathWarningShown &&
            raceTimeMs >= ARENA_SUDDEN_DEATH_START_MS - 10_000L &&
            raceTimeMs < ARENA_SUDDEN_DEATH_START_MS
        ) {
            suddenDeathWarningShown = true
            setBanner("10s TO SUDDEN DEATH — HOLD THE FLAG!", 2200L)
        }

        if (raceTimeMs < ARENA_SUDDEN_DEATH_START_MS) return

        val overtimeMs = (raceTimeMs - ARENA_SUDDEN_DEATH_START_MS).coerceAtLeast(0L)
        val desiredStage = ((overtimeMs / ARENA_SUDDEN_DEATH_WAVE_MS).toInt() + 1).coerceIn(1, 3)
        while (arenaSuddenDeathStage < desiredStage) {
            arenaSuddenDeathStage += 1
            dropSuddenDeathBarrierWave(arenaSuddenDeathStage)
        }
    }

    private fun dropSuddenDeathBarrierWave(stage: Int) {
        val cx = track.worldWidth * 0.5f
        val cy = track.worldHeight * 0.5f
        val poly = currentArenaPolygon()
        if (poly.size < 3) return

        // Replace active barrier ring with the new tighter inward wave
        hazards.removeAll { it.hazardTypeEnum == HazardType.BARRIER }

        val perimeterCount = when (stage) {
            1 -> 14
            2 -> 12
            else -> 10
        }
        for (k in 0 until perimeterCount) {
            val pt = poly[(k * poly.size) / perimeterCount]
            val toCenterX = cx - pt.x
            val toCenterY = cy - pt.y
            val distToCenter = hypot(toCenterX, toCenterY).coerceAtLeast(1f)
            val bx = pt.x + (toCenterX / distToCenter) * 14f
            val by = pt.y + (toCenterY / distToCenter) * 14f
            hazards.add(
                TrackHazard(
                    id = nextHazardId++,
                    ownerId = "arena_sudden_death",
                    x = bx,
                    y = by,
                    radius = 28f,
                    remainingMs = 45_000L,
                    type = HazardType.BARRIER.name
                )
            )
            if (k % 2 == 0) {
                explosions.add(
                    TrackExplosion(
                        id = nextExplosionId++,
                        x = bx,
                        y = by,
                        ageMs = 0L,
                        durationMs = 560L
                    )
                )
            }
        }

        // In Waves 2 & 3, also drop 4 inner tactical hazard barriers inward toward center
        if (stage >= 2) {
            val innerRadius = if (stage == 2) 220f else 138f
            val angleOffset = (PI / 4.0).toFloat() + (stage - 2) * 0.42f
            for (k in 0 until 4) {
                val ang = angleOffset + k * (PI.toFloat() / 2f)
                var ix = cx + cos(ang) * innerRadius
                var iy = cy + sin(ang) * innerRadius
                for (prop in track.props) {
                    val d = hypot(ix - prop.x, iy - prop.y)
                    if (d < prop.radius + 42f && d > 0.1f) {
                        ix = prop.x + ((ix - prop.x) / d) * (prop.radius + 46f)
                        iy = prop.y + ((iy - prop.y) / d) * (prop.radius + 46f)
                    }
                }
                if (TrackCatalog.isPointInPolygon(ix, iy, poly)) {
                    hazards.add(
                        TrackHazard(
                            id = nextHazardId++,
                            ownerId = "arena_sudden_death",
                            x = ix,
                            y = iy,
                            radius = 25f,
                            remainingMs = 45_000L,
                            type = HazardType.BARRIER.name
                        )
                    )
                    explosions.add(
                        TrackExplosion(
                            id = nextExplosionId++,
                            x = ix,
                            y = iy,
                            ageMs = 0L,
                            durationMs = 560L
                        )
                    )
                }
            }
        }

        // Shove any cars caught outside the newly dropped barrier ring inward
        for (racer in racers) {
            val inside = TrackCatalog.isPointInPolygon(racer.x, racer.y, poly)
            val (bx, by, wallDist) = TrackCatalog.closestPointOnPolygon(racer.x, racer.y, poly)
            val minClearance = racer.carType.radiusPx + 46f
            if (!inside || wallDist < minClearance) {
                val inDx = cx - bx
                val inDy = cy - by
                val inLen = hypot(inDx, inDy).coerceAtLeast(1f)
                val inNx = inDx / inLen
                val inNy = inDy / inLen
                racer.x = bx + inNx * minClearance
                racer.y = by + inNy * minClearance
                racer.prevX = racer.x
                racer.prevY = racer.y
                racer.vx = inNx * 210f
                racer.vy = inNy * 210f
                racer.collisionSeq += 1
            }
        }

        // Keep loose flag inside the newly shrunken safe zone & clear of barriers
        if (flagCarrierId == null) {
            repositionLooseFlagInsidePoly(poly)
        }

        // Remove any field pickups or portals stranded outside the shrunken ring
        fieldPickups.removeAll { fp ->
            !TrackCatalog.isPointInPolygon(fp.x, fp.y, poly) ||
                TrackCatalog.closestPointOnPolygon(fp.x, fp.y, poly).third < 55f
        }
        portalPair?.let { pp ->
            val okEntry = TrackCatalog.isPointInPolygon(pp.entryX, pp.entryY, poly) &&
                TrackCatalog.closestPointOnPolygon(pp.entryX, pp.entryY, poly).third >= 65f
            val okExit = TrackCatalog.isPointInPolygon(pp.exitX, pp.exitY, poly) &&
                TrackCatalog.closestPointOnPolygon(pp.exitX, pp.exitY, poly).third >= 65f
            if (!okEntry || !okExit) {
                portalPair = null
            }
        }
        for (i in itemBoxes.indices) {
            val b = itemBoxes[i]
            if (!TrackCatalog.isPointInPolygon(b.x, b.y, poly) ||
                TrackCatalog.closestPointOnPolygon(b.x, b.y, poly).third < 60f
            ) {
                val (nx, ny) = TrackCatalog.sampleRandomItemBoxPoint(
                    track = track,
                    rng = random,
                    avoidPoints = racers.map { it.x to it.y } + (flagX to flagY),
                    boundaryOverride = poly
                )
                itemBoxes[i] = b.copy(x = nx, y = ny)
            }
        }

        val bannerMsg = when (stage) {
            1 -> "SUDDEN DEATH! BARRIERS DROPPING — 30s FINALE!"
            2 -> "SUDDEN DEATH WAVE 2! ARENA SHRINKING!"
            else -> "FINAL CRUSH! 10 SECONDS LEFT!"
        }
        setBanner(bannerMsg, 2700L)
    }

    private fun repositionLooseFlagInsidePoly(poly: List<TrackPoint>) {
        if (poly.size < 3) return
        val cx = track.worldWidth * 0.5f
        val cy = track.worldHeight * 0.5f
        var fx = flagX
        var fy = flagY
        val inside = TrackCatalog.isPointInPolygon(fx, fy, poly)
        val (bx, by, wallDist) = TrackCatalog.closestPointOnPolygon(fx, fy, poly)
        if (!inside || wallDist < 72f) {
            val d = hypot(cx - bx, cy - by).coerceAtLeast(1f)
            val pull = 86f.coerceAtMost(d * 0.85f)
            fx = bx + ((cx - bx) / d) * pull
            fy = by + ((cy - by) / d) * pull
        }
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
        for (h in hazards) {
            if (h.hazardTypeEnum == HazardType.BARRIER) {
                val dx = fx - h.x
                val dy = fy - h.y
                val d = hypot(dx, dy)
                if (d < h.radius + 36f) {
                    val toCx = cx - h.x
                    val toCy = cy - h.y
                    val len = hypot(toCx, toCy).coerceAtLeast(1f)
                    fx = h.x + (toCx / len) * (h.radius + 44f)
                    fy = h.y + (toCy / len) * (h.radius + 44f)
                }
            }
        }
        flagX = fx.coerceIn(140f, track.worldWidth - 140f)
        flagY = fy.coerceIn(120f, track.worldHeight - 120f)
    }

    private fun dropFlagFrom(racer: MutableRacer, reason: String) {
        if (!isArenaMode || flagCarrierId != racer.playerId) return
        flagCarrierId = null
        racer.flagPickupImmunityMs = 650L

        val dropAng = racer.angleRad + PI.toFloat() * 0.82f
        flagX = racer.x + cos(dropAng) * 78f
        flagY = racer.y + sin(dropAng) * 78f

        val poly = currentArenaPolygon()
        if (track.isArena && poly.size >= 3) {
            repositionLooseFlagInsidePoly(poly)
        } else {
            flagX = flagX.coerceIn(140f, track.worldWidth - 140f)
            flagY = flagY.coerceIn(120f, track.worldHeight - 120f)
        }
        setBanner("${racer.name} $reason — FLAG DROPPED!", 2000L)
    }

    private fun sweptFlagDistance(racer: MutableRacer, fx: Float, fy: Float): Float {
        val segDx = racer.x - racer.prevX
        val segDy = racer.y - racer.prevY
        val lenSq = segDx * segDx + segDy * segDy
        if (lenSq < 0.001f) {
            return TrackCatalog.distance(racer.x, racer.y, fx, fy)
        }
        val t = (((fx - racer.prevX) * segDx + (fy - racer.prevY) * segDy) / lenSq).coerceIn(0f, 1f)
        val projX = racer.prevX + segDx * t
        val projY = racer.prevY + segDy * t
        return hypot(fx - projX, fy - projY)
    }

    private fun flagCaptureRadiusFor(racer: MutableRacer): Float {
        val bodyOrShieldRadius = if (racer.shieldMs > 0L) {
            max(racer.carType.radiusPx, 31f)
        } else {
            racer.carType.radiusPx * 0.85f
        }
        return 46f + bodyOrShieldRadius
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
            } else if (carrier.stunnedMs > 0L || carrier.electroParalyzedMs > 0L) {
                dropFlagFrom(carrier, if (carrier.electroParalyzedMs > 0L) "got electro-shocked" else "spun out")
            } else {
                flagX = carrier.x
                flagY = carrier.y
                carrier.flagCarryMs = (carrier.flagCarryMs + dtMs).coerceAtMost(arenaTargetMs)
            }
        }

        if (flagCarrierId == null) {
            val eligible = racers
                .filter { r ->
                    !r.finished &&
                        r.jumpZ < 0.55f &&
                        r.stunnedMs == 0L &&
                        r.electroParalyzedMs == 0L &&
                        (r.flagPickupImmunityMs == 0L || r.shieldMs > 0L) &&
                        sweptFlagDistance(r, flagX, flagY) <= flagCaptureRadiusFor(r)
                }
                .minByOrNull { r -> sweptFlagDistance(r, flagX, flagY) }

            if (eligible != null) {
                flagCarrierId = eligible.playerId
                flagX = eligible.x
                flagY = eligible.y
                flagTransferCooldownMs = 250L
                eligible.racePoints += 15
                setBanner("${eligible.name} captured the FLAG!", 1800L)
            }
        }
    }

    private fun integrateCarPhysics(racer: MutableRacer, input: ControlInput, dt: Float) {
        racer.prevX = racer.x
        racer.prevY = racer.y
        if (racer.electroParalyzedMs > 0L) {
            racer.vx = 0f
            racer.vy = 0f
            racer.steerVisual = 0f
            return
        }
        val isAirborne = racer.jumpZ > 0.08f
        var closestIdx = racer.lastTrackIdx
        var isOffRoad = false

        if (!track.isArena && !track.isStunt) {
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
            racer.collisionSeq += 1
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

        val boostMult = if (isStuntMode) 1.20f else 1.44f
        val speedMult = when {
            isStunned -> 0.36f
            hasBoost -> boostMult
            hasOverdrive -> 1.18f
            else -> 1.0f
        } * offRoadFactor * weatherSpeedMult

        val maxSpeed = racer.carType.maxSpeedPx * speedPreset.speedMultiplier * speedMult
        val accelWeatherMult = if (activeEvent == ExternalEventType.SNOW) 0.84f else 1.0f
        val accel = racer.carType.accelRate *
            speedPreset.accelMultiplier *
            accelWeatherMult *
            (if (hasBoost) (if (isStuntMode) 1.38f else 1.75f) else if (hasOverdrive) 1.3f else 1.0f)

        val cosA = cos(racer.angleRad)
        val sinA = sin(racer.angleRad)
        val rightX = -sinA
        val rightY = cosA

        var forwardVel = racer.vx * cosA + racer.vy * sinA
        var lateralVel = racer.vx * rightX + racer.vy * rightY

        val throttle = if (isStunned) 0.15f else input.throttle.coerceIn(-1f, 1f)
        val inputSteer = if (isStunned) 0f else input.steer.coerceIn(-1f, 1f)
        val effectiveSteer = if (!isStunned && racer.invertedSteerMs > 0L) {
            if (racer.isBot) {
                (-inputSteer * 0.35f + sin(elapsedMs / 135f + closestIdx) * 0.65f).coerceIn(-1f, 1f)
            } else {
                -inputSteer
            }
        } else {
            inputSteer
        }
        val puddleSteerJitter = if (inRainPuddle && !hasOverdrive) {
            sin((elapsedMs / 110f) + closestIdx.toFloat()) * 0.55f
        } else {
            0f
        }
        val steer = (effectiveSteer + puddleSteerJitter).coerceIn(-1.15f, 1.15f)
        racer.steerVisual = effectiveSteer

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
        } else if (throttle < -0.05f) {
            // Mid-air braking: actively bleed forward airspeed and pitch slightly downward
            val airBrakeDecel = accel * 1.75f * abs(throttle)
            forwardVel = if (forwardVel > 0f) {
                (forwardVel - airBrakeDecel * dt).coerceAtLeast(35f)
            } else {
                (forwardVel - airBrakeDecel * 0.5f * dt).coerceAtLeast(-maxSpeed * 0.35f)
            }
            racer.vz -= 1.85f * abs(throttle) * dt
        }

        val speedNorm = computeSteeringSpeedFactor(
            absForwardSpeedPx = abs(forwardVel),
            speedMultiplier = speedPreset.speedMultiplier
        )
        val reverseFlip = if (forwardVel < -25f) -1f else 1f
        val gripTurnBoost = if (hasOverdrive) 1.18f else 1.0f
        val airSteerFactor = if (isAirborne) 0.25f else 1.0f

        if (isDriftMode && !isAirborne) {
            val weatherGripMult = when {
                inRainPuddle && !hasOverdrive -> 0.45f
                activeEvent == ExternalEventType.SNOW -> 0.40f
                activeEvent == ExternalEventType.RAIN -> 0.72f
                activeEvent == ExternalEventType.EARTHQUAKE -> 0.80f
                else -> 1.0f
            }
            val driftStep = com.example.drift.DriftPhysics.integrateDriftTurn(
                currentAngleRad = racer.angleRad,
                forwardVel = forwardVel,
                lateralVel = lateralVel,
                steer = steer,
                throttle = throttle,
                turnRateRad = racer.carType.turnRateRad,
                carLateralGrip = racer.carType.lateralGrip,
                surfaceGripMultiplier = track.id.surfaceGripMultiplier,
                speedNorm = speedNorm,
                reverseFlip = reverseFlip,
                hasOverdrive = hasOverdrive,
                hasBoost = hasBoost,
                isOffRoad = isOffRoad,
                weatherGripMult = weatherGripMult,
                dt = dt
            )
            racer.angleRad = driftStep.newAngleRad
            racer.vx = driftStep.vx
            racer.vy = driftStep.vy
        } else {
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
        }

        racer.x += racer.vx * dt
        racer.y += racer.vy * dt

        if (track.isArena) {
            val poly = currentArenaPolygon()
            if (poly.size >= 3) {
                val inside = TrackCatalog.isPointInPolygon(racer.x, racer.y, poly)
                val (bx, by, wallDist) = TrackCatalog.closestPointOnPolygon(racer.x, racer.y, poly)
                val wallMargin = if (rules.hardTrackBoundary || arenaSuddenDeathStage > 0) {
                    racer.carType.radiusPx + 8f
                } else {
                    4f
                }
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
                        if (vnOut > 65f && racer.wallBumpCooldownMs == 0L) {
                            racer.wallBumpCooldownMs = 260L
                            racer.collisionSeq += 1
                        }
                        val restitution = if (rules.hardTrackBoundary) 1.58f else 1.3f
                        val retention = if (rules.hardTrackBoundary) 0.82f else 0.78f
                        racer.vx = (racer.vx - restitution * vnOut * outNx) * retention
                        racer.vy = (racer.vy - restitution * vnOut * outNy) * retention
                    }

                    // Gently guide auto-gas cars along the arena wall so they never pin nose-first
                    val noseOutDot = cos(racer.angleRad) * outNx + sin(racer.angleRad) * outNy
                    if (throttle > 0.1f && (abs(vnOut) < 45f || noseOutDot > 0.12f)) {
                        val tang1X = -inNy
                        val tang1Y = inNx
                        val dot1 = cos(racer.angleRad) * tang1X + sin(racer.angleRad) * tang1Y
                        val tangAng = if (dot1 >= 0f) atan2(tang1Y, tang1X) else atan2(-tang1Y, -tang1X)
                        val diff = normalizeAngle(tangAng - racer.angleRad)
                        val alignRate = if (noseOutDot > 0.35f) 3.6f else 2.6f
                        racer.angleRad = normalizeAngle(racer.angleRad + diff * (alignRate * dt).coerceAtMost(0.42f))
                    }
                }
            }
            return
        }

        if (track.isStunt) {
            val rPx = racer.carType.radiusPx
            if (racer.x < StuntArenaController.RAMP_EXIT_X && !isAirborne) {
                val minRunwayY = 700f - 148f + rPx * 0.5f
                val maxRunwayY = 700f + 148f - rPx * 0.5f
                if (racer.y < minRunwayY) {
                    racer.y = minRunwayY
                    if (racer.vy < 0f) racer.vy = -racer.vy * 0.55f
                } else if (racer.y > maxRunwayY) {
                    racer.y = maxRunwayY
                    if (racer.vy > 0f) racer.vy = -racer.vy * 0.55f
                }
                if (racer.x < 160f + rPx) {
                    racer.x = 160f + rPx
                    if (racer.vx < 0f) racer.vx = -racer.vx * 0.55f
                }
            } else {
                val minStadiumY = 230f + rPx
                val maxStadiumY = 1170f - rPx
                if (racer.y < minStadiumY) {
                    racer.y = minStadiumY
                    if (racer.vy < 0f) racer.vy = -racer.vy * 0.55f
                } else if (racer.y > maxStadiumY) {
                    racer.y = maxStadiumY
                    if (racer.vy > 0f) racer.vy = -racer.vy * 0.55f
                }
                val maxStadiumX = StuntArenaController.STADIUM_END_X - rPx
                if (racer.x >= maxStadiumX) {
                    racer.x = maxStadiumX
                    if (racer.vx > -40f) {
                        racer.vx = -max(abs(racer.vx) * 0.78f, 185f)
                        if (racer.wallBumpCooldownMs == 0L) {
                            racer.wallBumpCooldownMs = 240L
                            racer.collisionSeq += 1
                        }
                    }
                }
            }
            return
        }

        // Circuit centerline corridor boundary (continuous spline segment projection)
        val proj = TrackCatalog.nearestTrackSegmentLocal(
            track = track,
            x = racer.x,
            y = racer.y,
            hintIdx = racer.lastTrackIdx
        )
        racer.lastTrackIdx = proj.index

        val maxCorridor = if (rules.hardTrackBoundary) {
            (proj.halfWidth - racer.carType.radiusPx * 0.45f).coerceAtLeast(42f)
        } else {
            proj.halfWidth + 130f
        }
        if (proj.distance > maxCorridor) {
            val dx = racer.x - proj.closestX
            val dy = racer.y - proj.closestY
            val d = hypot(dx, dy).coerceAtLeast(1f)
            val nx = dx / d
            val ny = dy / d
            val safeDist = (maxCorridor - 2.5f).coerceAtLeast(24f)
            racer.x = proj.closestX + nx * safeDist
            racer.y = proj.closestY + ny * safeDist

            val vn = racer.vx * nx + racer.vy * ny
            if (vn > 0f) {
                if (vn > 65f && racer.wallBumpCooldownMs == 0L) {
                    racer.wallBumpCooldownMs = 260L
                    racer.collisionSeq += 1
                }
                val restitution = if (rules.hardTrackBoundary) 1.48f else 1.3f
                val retention = if (rules.hardTrackBoundary) 0.85f else 0.84f
                racer.vx = (racer.vx - restitution * vn * nx) * retention
                racer.vy = (racer.vy - restitution * vn * ny) * retention
            }
            val noseOutDot = cos(racer.angleRad) * nx + sin(racer.angleRad) * ny
            if (throttle > 0.1f && rules.hardTrackBoundary && (abs(vn) < 40f || noseOutDot > 0.10f)) {
                val segA = track.centerline[proj.index]
                val segB = track.centerline[(proj.index + 1) % track.centerline.size]
                val tangAng = atan2(segB.y - segA.y, segB.x - segA.x)
                val diff = normalizeAngle(tangAng - racer.angleRad)
                val alignRate = if (noseOutDot > 0.35f) 3.8f else 2.6f
                racer.angleRad = normalizeAngle(racer.angleRad + diff * (alignRate * dt).coerceAtMost(0.45f))
                if (noseOutDot > 0.35f && hypot(racer.vx, racer.vy) < 95f) {
                    val tx = cos(tangAng)
                    val ty = sin(tangAng)
                    racer.vx += tx * 35f
                    racer.vy += ty * 35f
                }
            }
        }
    }

    private fun handleWorldAndPropCollisions(racer: MutableRacer, throttle: Float = 0f) {
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
                        if (vn < -48f && racer.wallBumpCooldownMs == 0L) {
                            racer.wallBumpCooldownMs = 240L
                            racer.collisionSeq += 1
                        }
                        racer.vx = (racer.vx - 1.45f * vn * nx) * 0.84f
                        racer.vy = (racer.vy - 1.45f * vn * ny) * 0.84f
                    }
                    // Slide auto-accelerating cars around solid obstacles instead of pinning nose-first
                    val noseIntoProp = -(cos(racer.angleRad) * nx + sin(racer.angleRad) * ny)
                    if (throttle > 0.1f && noseIntoProp > 0.25f) {
                        val t1x = -ny
                        val t1y = nx
                        val chooseSign = if (cos(racer.angleRad) * t1x + sin(racer.angleRad) * t1y >= 0f) 1f else -1f
                        val slideAng = atan2(t1y * chooseSign, t1x * chooseSign)
                        val diff = normalizeAngle(slideAng - racer.angleRad)
                        racer.angleRad = normalizeAngle(racer.angleRad + diff * 0.16f)
                    }
                }
            } else if (prop.shape == PropShape.MUD_POOL && dist < prop.radius && racer.jumpZ <= 0.05f) {
                if (racer.shieldMs == 0L && racer.overdriveMs == 0L) {
                    if (isArenaMode && racer.stunnedMs == 0L && racer.spinImmunityMs == 0L) {
                        racer.stunnedMs = 1050L
                        racer.spinImmunityMs = 2400L
                        racer.vx *= 0.42f
                        racer.vy *= 0.42f
                        racer.collisionSeq += 1
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
                    val boostSpeed = racer.carType.maxSpeedPx * speedPreset.speedMultiplier * 1.38f
                    racer.vx = cos(racer.angleRad) * boostSpeed
                    racer.vy = sin(racer.angleRad) * boostSpeed
                }
            }
        }
    }

    private fun handleRamps(racer: MutableRacer) {
        if (racer.jumpZ > 0.01f || racer.jumpCooldownMs > 0L) return
        val speed = hypot(racer.vx, racer.vy)
        if (speed < 130f * speedPreset.speedMultiplier) return
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
        if (isDailyChallenge || isDriftMode || racer.heldPowerUp != null) return
        for (i in itemBoxes.indices) {
            val box = itemBoxes[i]
            if (box.available && TrackCatalog.distance(racer.x, racer.y, box.x, box.y) <= 44f) {
                itemBoxes[i] = box.copy(available = false, respawnMs = 5500L)
                val pool = when {
                    racers.size <= 1 -> listOf(
                        PowerUpType.TURBO_NITRO,
                        PowerUpType.OVERDRIVE_GRIP,
                        PowerUpType.SHIELD
                    )
                    racer.rank >= 3 -> listOf(
                        PowerUpType.TURBO_NITRO,
                        PowerUpType.HOMING_ROCKET,
                        PowerUpType.EMP_ZAP,
                        PowerUpType.RED_LASER,
                        PowerUpType.ELECTRO_SHOCK,
                        PowerUpType.OVERDRIVE_GRIP,
                        PowerUpType.SHIELD
                    )
                    else -> PowerUpType.entries
                }
                val picked = pool[random.nextInt(pool.size)]
                racer.heldPowerUp = picked
                if (racer.isBot) {
                    racer.botPowerUpDelayMs = if (picked == PowerUpType.ELECTRO_SHOCK || picked == PowerUpType.RED_LASER) {
                        random.nextLong(1900L, 3800L)
                    } else {
                        random.nextLong(700L, 2200L)
                    }
                }
                break
            }
        }
    }

    private fun grantLapMineIfEligible(racer: MutableRacer): Boolean {
        if (isDailyChallenge || isDriftMode || isArenaMode || racer.finished) return false
        if (racer.lap >= 2 && racer.lap > racer.lastMineGrantedLap) {
            racer.lastMineGrantedLap = racer.lap
            racer.minesAvailable = 1
            if (racer.isBot) {
                racer.botMineDelayMs = random.nextLong(2800L, 9200L)
            }
            return true
        }
        return false
    }

    private fun grantDailyNitroIfEligible(racer: MutableRacer): Boolean {
        if ((!isDailyChallenge && !isDriftMode) || racer.finished) return false
        if (racer.lap > racer.lastNitroGrantedLap) {
            racer.lastNitroGrantedLap = racer.lap
            racer.heldPowerUp = PowerUpType.TURBO_NITRO
            return true
        }
        return false
    }

    private fun dropMine(racer: MutableRacer) {
        if (isDailyChallenge || isDriftMode || isArenaMode || racer.minesAvailable <= 0) return
        racer.minesAvailable = 0
        val dropX = racer.x - cos(racer.angleRad) * 54f
        val dropY = racer.y - sin(racer.angleRad) * 54f
        hazards.add(
            TrackHazard(
                id = nextHazardId++,
                ownerId = racer.playerId,
                x = dropX,
                y = dropY,
                radius = 30f,
                remainingMs = 45000L,
                type = HazardType.MINE.name
            )
        )
        setBanner("${racer.name} dropped a Red Mine!", 1400L)
    }

    private fun handleFieldPickups(racer: MutableRacer) {
        if (isDailyChallenge || isDriftMode || racer.jumpZ > 0.30f || fieldPickups.isEmpty()) return
        val hitRadius = 40f + racer.carType.radiusPx * 0.45f
        val it = fieldPickups.iterator()
        while (it.hasNext()) {
            val fp = it.next()
            if (TrackCatalog.distance(racer.x, racer.y, fp.x, fp.y) <= hitRadius) {
                it.remove()
                racer.portalWarpSeq += 1
                when (fp.pickupTypeEnum) {
                    FieldPickupType.POSITION_SWAP -> {
                        updateRankings()
                        val leader = racers
                            .filter { !it.finished }
                            .minByOrNull { it.rank }
                            ?: racers.minByOrNull { it.rank }

                        if (leader != null && leader.playerId != racer.playerId) {
                            val rx = racer.x
                            val ry = racer.y
                            val rAng = racer.angleRad
                            val rvx = racer.vx
                            val rvy = racer.vy
                            val rLap = racer.lap
                            val rCp = racer.checkpointIndex
                            val rLastIdx = racer.lastTrackIdx
                            val rMask = racer.sectorMask

                            racer.x = leader.x
                            racer.y = leader.y
                            racer.prevX = racer.x
                            racer.prevY = racer.y
                            racer.angleRad = leader.angleRad
                            racer.vx = leader.vx
                            racer.vy = leader.vy
                            racer.jumpZ = 0f
                            racer.vz = 0f
                            racer.portalCooldownMs = 900L
                            racer.spinImmunityMs = 900L

                            leader.x = rx
                            leader.y = ry
                            leader.prevX = rx
                            leader.prevY = ry
                            leader.angleRad = rAng
                            leader.vx = rvx
                            leader.vy = rvy
                            leader.jumpZ = 0f
                            leader.vz = 0f
                            leader.portalCooldownMs = 900L
                            leader.spinImmunityMs = 900L
                            leader.portalWarpSeq += 1

                            if (!isArenaMode) {
                                racer.lap = leader.lap
                                racer.checkpointIndex = leader.checkpointIndex
                                racer.lastTrackIdx = leader.lastTrackIdx
                                racer.sectorMask = leader.sectorMask

                                leader.lap = rLap
                                leader.checkpointIndex = rCp
                                leader.lastTrackIdx = rLastIdx
                                leader.sectorMask = rMask

                                grantLapMineIfEligible(racer)
                                grantLapMineIfEligible(leader)
                            }

                            racer.powerUpHits += 1
                            racer.racePoints += 40
                            updateRankings()
                            setBanner("${racer.name} SWAPPED POSITIONS with ${leader.name}!", 2200L)
                        } else {
                            // Already the leading driver — small boost surge
                            racer.boostMs = max(racer.boostMs, 900L)
                            setBanner("${racer.name} hit Position Swap (already 1st)!", 1500L)
                        }
                    }
                    FieldPickupType.STEER_INVERT -> {
                        racer.invertedSteerMs = 5000L
                        setBanner("${racer.name}: STEERING INVERTED (5s)!", 2000L)
                    }
                }
                break
            }
        }
    }

    private fun markSectorsCrossedForward(racer: MutableRacer, fromIdx: Int, toIdx: Int) {
        val n = track.centerline.size
        if (n < 8 || toIdx <= fromIdx) return
        val q1 = n / 4
        val q2 = n / 2
        val q3 = (3 * n) / 4
        if (fromIdx < q1) racer.sectorMask = racer.sectorMask or 0b0001
        if (toIdx >= q1 && (racer.sectorMask and 0b0001) != 0) {
            racer.sectorMask = racer.sectorMask or 0b0010
        }
        if (toIdx >= q2 && (racer.sectorMask and 0b0010) != 0) {
            racer.sectorMask = racer.sectorMask or 0b0100
        }
        if (toIdx >= q3 && (racer.sectorMask and 0b0100) != 0) {
            racer.sectorMask = racer.sectorMask or 0b1000
        }
    }

    private fun handlePortals(racer: MutableRacer) {
        val portals = portalPair ?: return
        if (racer.jumpZ > 0.30f || racer.portalCooldownMs > 0L) return

        val forwardVel = racer.vx * cos(racer.angleRad) + racer.vy * sin(racer.angleRad)
        // Portals only trigger when driving forward into them (never reversing backward)
        if (forwardVel < -8f) return

        val portalHitRadius = 38f + racer.carType.radiusPx * 0.45f
        val distEntry = TrackCatalog.distance(racer.x, racer.y, portals.entryX, portals.entryY)

        if (track.isArena) {
            val distExit = TrackCatalog.distance(racer.x, racer.y, portals.exitX, portals.exitY)
            if (distEntry <= portalHitRadius || distExit <= portalHitRadius) {
                val enterFirst = distEntry <= distExit
                val destX = if (enterFirst) portals.exitX else portals.entryX
                val destY = if (enterFirst) portals.exitY else portals.entryY
                val outAng = racer.angleRad
                val outSpeed = max(hypot(racer.vx, racer.vy), 210f * speedPreset.speedMultiplier)
                racer.x = destX + cos(outAng) * 44f
                racer.y = destY + sin(outAng) * 44f
                racer.prevX = racer.x
                racer.prevY = racer.y
                racer.vx = cos(outAng) * outSpeed
                racer.vy = sin(outAng) * outSpeed
                racer.portalCooldownMs = 1600L
                racer.portalWarpSeq += 1
                setBanner("${racer.name} warped through portal!", 1400L)
            }
            return
        }

        // Circuit mode: strictly forward-only teleport from upstream entry hole -> downstream exit hole!
        if (distEntry <= portalHitRadius) {
            val outAng = portals.exitAngleRad
            val outSpeed = max(hypot(racer.vx, racer.vy), 215f * speedPreset.speedMultiplier)
            racer.x = portals.exitX + cos(outAng) * 36f
            racer.y = portals.exitY + sin(outAng) * 36f
            racer.prevX = racer.x
            racer.prevY = racer.y
            racer.angleRad = outAng
            racer.vx = cos(outAng) * outSpeed
            racer.vy = sin(outAng) * outSpeed
            racer.portalCooldownMs = 1600L
            racer.portalWarpSeq += 1

            val targetIdx = if (activePortalExitIdx >= 0) {
                activePortalExitIdx
            } else {
                TrackCatalog.nearestTrackPoint(track, racer.x, racer.y).first
            }
            val startIdx = if (activePortalEntryIdx >= 0) activePortalEntryIdx else racer.checkpointIndex
            markSectorsCrossedForward(racer, startIdx, targetIdx)
            racer.lastTrackIdx = targetIdx
            racer.checkpointIndex = targetIdx
            setBanner("${racer.name} WARPED FORWARD!", 1500L)
        }
    }

    private fun activatePowerUp(racer: MutableRacer) {
        val item = racer.heldPowerUp ?: return
        racer.heldPowerUp = null
        when (item) {
            PowerUpType.TURBO_NITRO -> {
                racer.boostMs = if (isStuntMode) 1750L else 2500L
                val burstMult = if (isStuntMode) 1.20f else 1.44f
                val burst = racer.carType.maxSpeedPx * speedPreset.speedMultiplier * burstMult
                racer.vx = cos(racer.angleRad) * burst
                racer.vy = sin(racer.angleRad) * burst
                setBanner("${racer.name} triggered LiPo Turbo!", 1600L)
            }
            PowerUpType.EMP_ZAP -> {
                updateRankings()
                val targets = racers.filter { it.playerId != racer.playerId && !it.finished }
                if (targets.isNotEmpty()) {
                    var zappedCount = 0
                    var blockedCount = 0
                    for (target in targets) {
                        target.collisionSeq += 1
                        if (target.shieldMs > 0L) {
                            target.shieldMs = 0L
                            blockedCount += 1
                        } else {
                            target.stunnedMs = 1750L
                            target.vx *= 0.35f
                            target.vy *= 0.35f
                            zappedCount += 1
                            racer.powerUpHits += 1
                            racer.racePoints += 30
                            if (isArenaMode && flagCarrierId == target.playerId) {
                                dropFlagFrom(target, "got zapped by EMP")
                            }
                        }
                    }
                    if (zappedCount > 0) {
                        setBanner("${racer.name} ZAPPED EVERYONE ($zappedCount hit)!", 1900L)
                    } else if (blockedCount > 0) {
                        setBanner("Shields blocked ${racer.name}'s global EMP!", 1800L)
                    }
                }
            }
            PowerUpType.SHIELD -> {
                racer.shieldMs = 5500L
                racer.flagPickupImmunityMs = 0L
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
                        remainingMs = 11000L,
                        type = HazardType.GLUE_SLICK.name
                    )
                )
            }
            PowerUpType.OVERDRIVE_GRIP -> {
                racer.overdriveMs = 4200L
                setBanner("${racer.name} activated Overdrive!", 1500L)
            }
            PowerUpType.ELECTRO_SHOCK -> {
                racer.electroShockAuraMs = ELECTRO_AURA_DURATION_MS
                setBanner("${racer.name} activated ELECTRO SHOCK!", 1750L)
                updateElectroShockAuras()
            }
            PowerUpType.HOMING_ROCKET -> {
                val target = selectRocketTarget(racer)
                val noseOffset = racer.carType.radiusPx + 14f
                val spawnX = racer.x + cos(racer.angleRad) * noseOffset
                val spawnY = racer.y + sin(racer.angleRad) * noseOffset
                val rocketSpeed = max(690f * speedPreset.speedMultiplier, hypot(racer.vx, racer.vy) + 250f)
                activeRockets.add(
                    MutableRocket(
                        id = nextRocketId++,
                        ownerId = racer.playerId,
                        targetId = target?.playerId,
                        x = spawnX,
                        y = spawnY,
                        prevX = spawnX,
                        prevY = spawnY,
                        angleRad = racer.angleRad,
                        speed = rocketSpeed,
                        remainingMs = ROCKET_DURATION_MS
                    )
                )
                val targetLabel = target?.name ?: "ahead"
                setBanner("${racer.name} fired HOMING ROCKET at $targetLabel!", 1700L)
            }
            PowerUpType.RED_LASER -> {
                racer.laserBeamMs = LASER_DURATION_MS
                setBanner("${racer.name} fired RED LASER!", 1650L)
                updateLaserBeams()
            }
        }
    }

    private fun selectRocketTarget(shooter: MutableRacer): MutableRacer? {
        updateRankings()
        val candidates = racers.filter { it.playerId != shooter.playerId && !it.finished }
        if (candidates.isEmpty()) return null
        if (isArenaMode) {
            val carrier = flagCarrierId?.let { cid -> candidates.find { it.playerId == cid } }
            if (carrier != null) return carrier
            val nextAhead = candidates.filter { it.rank < shooter.rank }.maxByOrNull { it.rank }
            if (nextAhead != null) return nextAhead
            return candidates.minByOrNull { TrackCatalog.distance(shooter.x, shooter.y, it.x, it.y) }
        }
        val nextRankAhead = candidates.filter { it.rank < shooter.rank }.maxByOrNull { it.rank }
        if (nextRankAhead != null) return nextRankAhead
        val n = track.centerline.size.coerceAtLeast(1)
        return candidates.minByOrNull { other ->
            val aheadSteps = (other.checkpointIndex - shooter.checkpointIndex).mod(n)
            if (aheadSteps == 0) n else aheadSteps
        } ?: candidates.minByOrNull { it.rank }
    }

    private fun pointToSegmentDistance(
        px: Float,
        py: Float,
        ax: Float,
        ay: Float,
        bx: Float,
        by: Float
    ): Float {
        val abx = bx - ax
        val aby = by - ay
        val lenSq = abx * abx + aby * aby
        if (lenSq < 1e-4f) return hypot(px - ax, py - ay)
        val t = (((px - ax) * abx + (py - ay) * aby) / lenSq).coerceIn(0f, 1f)
        val cx = ax + abx * t
        val cy = ay + aby * t
        return hypot(px - cx, py - cy)
    }

    private fun updateRockets(dtMs: Long, dt: Float) {
        if (activeRockets.isEmpty()) return
        val it = activeRockets.iterator()
        while (it.hasNext()) {
            val rocket = it.next()
            rocket.remainingMs -= dtMs
            if (rocket.remainingMs <= 0L) {
                explosions.add(
                    TrackExplosion(
                        id = nextExplosionId++,
                        x = rocket.x,
                        y = rocket.y,
                        ageMs = 0L,
                        durationMs = 460L
                    )
                )
                it.remove()
                continue
            }

            var target = rocket.targetId?.let { tid ->
                racers.find { it.playerId == tid && !it.finished }
            }
            if (target == null) {
                target = racers
                    .filter { it.playerId != rocket.ownerId && !it.finished }
                    .minByOrNull { TrackCatalog.distance(rocket.x, rocket.y, it.x, it.y) }
                rocket.targetId = target?.playerId
            }

            if (target != null) {
                val desiredAng = atan2(target.y - rocket.y, target.x - rocket.x)
                val maxTurn = 6.8f * dt
                val turnDelta = normalizeAngle(desiredAng - rocket.angleRad).coerceIn(-maxTurn, maxTurn)
                rocket.angleRad = normalizeAngle(rocket.angleRad + turnDelta)
            }

            rocket.prevX = rocket.x
            rocket.prevY = rocket.y
            rocket.x += cos(rocket.angleRad) * rocket.speed * dt
            rocket.y += sin(rocket.angleRad) * rocket.speed * dt

            var hitRacer: MutableRacer? = null
            for (r in racers) {
                if (r.playerId == rocket.ownerId || r.finished || r.jumpZ > 0.45f) continue
                val dist = pointToSegmentDistance(r.x, r.y, rocket.prevX, rocket.prevY, rocket.x, rocket.y)
                if (dist <= r.carType.radiusPx + 20f) {
                    hitRacer = r
                    break
                }
            }

            if (hitRacer != null) {
                it.remove()
                hitRacer.collisionSeq += 1
                explosions.add(
                    TrackExplosion(
                        id = nextExplosionId++,
                        x = hitRacer.x,
                        y = hitRacer.y,
                        ageMs = 0L,
                        durationMs = 680L
                    )
                )
                if (hitRacer.shieldMs > 0L) {
                    hitRacer.shieldMs = 0L
                    setBanner("${hitRacer.name}'s Shield absorbed Homing Rocket!", 1650L)
                } else {
                    hitRacer.stunnedMs = 1200L
                    hitRacer.spinImmunityMs = 2400L
                    hitRacer.vx *= 0.38f
                    hitRacer.vy *= 0.38f
                    val owner = racers.find { o -> o.playerId == rocket.ownerId }
                    if (owner != null && owner.playerId != hitRacer.playerId) {
                        owner.powerUpHits += 1
                        owner.racePoints += 45
                    }
                    if (isArenaMode && flagCarrierId == hitRacer.playerId) {
                        dropFlagFrom(hitRacer, "was blasted by a Rocket")
                    } else {
                        setBanner("BOOM! Rocket blasted ${hitRacer.name}!", 1750L)
                    }
                }
            }
        }
    }

    private fun updateLaserBeams() {
        if (phase != RacePhase.RACING) return
        for (source in racers) {
            if (source.laserBeamMs <= 0L || source.finished) continue
            val cosA = cos(source.angleRad)
            val sinA = sin(source.angleRad)
            val startX = source.x + cosA * 20f
            val startY = source.y + sinA * 20f
            val endX = source.x + cosA * (20f + LASER_LENGTH_PX)
            val endY = source.y + sinA * (20f + LASER_LENGTH_PX)

            for (target in racers) {
                if (target.playerId == source.playerId || target.finished) continue
                if (target.electroParalyzedMs > 0L || target.electroImmunityMs > 0L) continue
                if (abs(source.jumpZ - target.jumpZ) > 0.45f) continue
                val dist = pointToSegmentDistance(target.x, target.y, startX, startY, endX, endY)
                if (dist <= target.carType.radiusPx + 10f) {
                    target.collisionSeq += 1
                    explosions.add(
                        TrackExplosion(
                            id = nextExplosionId++,
                            x = target.x,
                            y = target.y,
                            ageMs = 0L,
                            durationMs = 360L
                        )
                    )
                    if (target.shieldMs > 0L) {
                        target.shieldMs = 0L
                        target.electroImmunityMs = 1000L
                        setBanner("${target.name}'s Shield deflected ${source.name}'s Red Laser!", 1650L)
                    } else {
                        target.electroParalyzedMs = ELECTRO_PARALYZE_DURATION_MS
                        target.electroImmunityMs = LASER_DURATION_MS + 300L
                        target.vx = 0f
                        target.vy = 0f
                        source.powerUpHits += 1
                        source.racePoints += 45
                        if (isArenaMode && flagCarrierId == target.playerId) {
                            dropFlagFrom(target, "was disabled by Red Laser")
                        } else {
                            setBanner("LASER HIT! ${source.name} disabled ${target.name}!", 1750L)
                        }
                    }
                }
            }
        }
    }

    private fun updateElectroShockAuras() {
        if (phase != RacePhase.RACING) return
        for (source in racers) {
            if (source.electroShockAuraMs <= 0L || source.finished) continue
            for (target in racers) {
                if (target.playerId == source.playerId || target.finished) continue
                if (target.electroParalyzedMs > 0L || target.electroImmunityMs > 0L) continue
                if (abs(source.jumpZ - target.jumpZ) > 0.45f) continue
                val dist = TrackCatalog.distance(source.x, source.y, target.x, target.y)
                if (dist <= ELECTRO_SHOCK_RADIUS_PX) {
                    target.collisionSeq += 1
                    if (target.shieldMs > 0L) {
                        target.shieldMs = 0L
                        target.electroImmunityMs = 1000L
                        setBanner("${target.name}'s Shield absorbed ${source.name}'s Electro Shock!", 1650L)
                    } else {
                        target.electroParalyzedMs = ELECTRO_PARALYZE_DURATION_MS
                        target.electroImmunityMs = ELECTRO_AURA_DURATION_MS + 300L
                        target.vx = 0f
                        target.vy = 0f
                        source.powerUpHits += 1
                        source.racePoints += 45
                        if (isArenaMode && flagCarrierId == target.playerId) {
                            dropFlagFrom(target, "got frozen by Electro Shock")
                        } else {
                            setBanner("SHOCKED! ${source.name} froze ${target.name}!", 1750L)
                        }
                    }
                }
            }
        }
    }

    private fun stepStuntMatch(
        sc: StuntArenaController,
        dtMs: Long,
        dt: Float
    ): RaceSnapshot {
        if (phase == RacePhase.RACING) {
            raceTimeMs += dtMs
        }

        val expIt = explosions.iterator()
        while (expIt.hasNext()) {
            val exp = expIt.next()
            val newAge = exp.ageMs + dtMs
            if (newAge >= exp.durationMs) {
                expIt.remove()
            } else {
                val idx = explosions.indexOf(exp)
                if (idx >= 0) explosions[idx] = exp.copy(ageMs = newAge)
            }
        }

        val activeIdx = sc.turnIndex.coerceIn(0, (racers.size - 1).coerceAtLeast(0))
        racers.forEachIndexed { idx, racer ->
            if (idx != activeIdx) {
                racer.vx = 0f
                racer.vy = 0f
                pendingPowerUpTrigger.remove(racer.playerId)
                pendingMineDropTrigger.remove(racer.playerId)
            }
        }

        val activeRacer = racers.getOrNull(activeIdx)
        if (activeRacer != null && phase == RacePhase.RACING) {
            val wasAirborne = activeRacer.jumpZ > 0.01f
            tickTimers(activeRacer, dtMs, dt)
            val landedAfterRampThisTick = wasAirborne &&
                activeRacer.jumpZ <= 0.01f &&
                activeRacer.x >= StuntArenaController.RAMP_START_X

            val input = if (sc.controlsLocked || landedAfterRampThisTick) {
                pendingPowerUpTrigger.remove(activeRacer.playerId)
                pendingMineDropTrigger.remove(activeRacer.playerId)
                ControlInput(throttle = 0f, steer = 0f)
            } else if (activeRacer.isBot) {
                val (botInp, fireNitro) = sc.computeBotInput(
                    carX = activeRacer.x,
                    carY = activeRacer.y,
                    carAngleRad = activeRacer.angleRad,
                    carJumpZ = activeRacer.jumpZ,
                    heldPowerUp = activeRacer.heldPowerUp
                )
                if (fireNitro && activeRacer.heldPowerUp != null) {
                    activatePowerUp(activeRacer)
                }
                botInp
            } else {
                if (pendingPowerUpTrigger.remove(activeRacer.playerId)) {
                    activatePowerUp(activeRacer)
                }
                pendingMineDropTrigger.remove(activeRacer.playerId)
                latestInputs[activeRacer.playerId] ?: ControlInput(
                    throttle = if (rules.autoAccelerate) 1f else 0f,
                    steer = 0f
                )
            }

            integrateCarPhysics(activeRacer, input, dt)
            handleWorldAndPropCollisions(activeRacer)

            val baseMaxSpeed = activeRacer.carType.maxSpeedPx * speedPreset.speedMultiplier
            sc.applyProgressiveRunwayBoost(
                carX = activeRacer.x,
                carY = activeRacer.y,
                jumpZ = activeRacer.jumpZ,
                baseMaxSpeed = baseMaxSpeed,
                currentBoostMs = activeRacer.boostMs
            )?.let { (newBoostMs, targetSpd) ->
                activeRacer.boostMs = newBoostMs
                if (hypot(activeRacer.vx, activeRacer.vy) < targetSpd) {
                    activeRacer.vx = cos(activeRacer.angleRad) * targetSpd
                    activeRacer.vy = sin(activeRacer.angleRad) * targetSpd
                }
            }

            sc.checkMegaRampLaunch(
                carX = activeRacer.x,
                carY = activeRacer.y,
                jumpZ = activeRacer.jumpZ,
                vx = activeRacer.vx,
                vy = activeRacer.vy,
                baseMaxSpeed = baseMaxSpeed
            )?.let { (nvx, nvy, nvz) ->
                activeRacer.vx = nvx
                activeRacer.vy = nvy
                activeRacer.vz = nvz
                activeRacer.jumpZ = 0.05f
                activeRacer.jumpCooldownMs = 800L
            }

            val outcome = sc.stepTurn(
                dtMs = dtMs,
                dt = dt,
                carX = activeRacer.x,
                carY = activeRacer.y,
                carVx = activeRacer.vx,
                carVy = activeRacer.vy,
                carRadius = activeRacer.carType.radiusPx,
                jumpZ = activeRacer.jumpZ,
                wasAirborneBeforeTick = wasAirborne,
                nextExplosionId = { nextExplosionId++ },
                snapCarTo = { sx, sy ->
                    activeRacer.x = sx
                    activeRacer.y = sy
                    activeRacer.vx = 0f
                    activeRacer.vy = 0f
                }
            )

            if (outcome.bumpedCollision) {
                activeRacer.collisionSeq += 1
            }
            outcome.spawnedExplosion?.let { explosions.add(it) }
            if (outcome.pointsAwarded > 0) {
                activeRacer.racePoints += outcome.pointsAwarded
                activeRacer.lap = sc.roundIndexZeroBased + 1
            }
            outcome.bannerMessage?.let { msg ->
                setBanner("${activeRacer.name}: $msg", 1550L)
            }
            if (outcome.advanceToNextTurn) {
                val matchDone = sc.advanceTurnOrRound(racers.size) { nextTurnIdx, nextVariant, newRound ->
                    setupStuntTurn(nextTurnIdx, nextVariant)
                    val nextDriver = racers.getOrNull(nextTurnIdx)?.name ?: "Driver"
                    val msg = if (newRound) {
                        "STUNT ${sc.roundIndexZeroBased + 1}/${sc.totalRounds}: ${nextVariant.displayName.uppercase()} — $nextDriver UP!"
                    } else {
                        "$nextDriver'S TURN — ${nextVariant.shortName}!"
                    }
                    setBanner(msg, 1900L)
                }
                if (matchDone) {
                    for (r in racers) {
                        r.finished = true
                        r.finishTimeMs = raceTimeMs
                    }
                    updateRankings()
                    val champ = racers.minByOrNull { it.rank } ?: racers.first()
                    phase = RacePhase.FINISHED
                    setBanner("STUNT CHAMPION: ${champ.name}!", 3500L)
                }
            }
        }

        updateRankings()
        return buildSnapshot(countdownSec = 0)
    }

    private fun handleHazards(racer: MutableRacer) {
        if (racer.jumpZ > 0.12f) return
        val it = hazards.iterator()
        while (it.hasNext()) {
            val h = it.next()
            val hazType = h.hazardTypeEnum
            if (hazType == HazardType.BARRIER) {
                val dx = racer.x - h.x
                val dy = racer.y - h.y
                val dist = hypot(dx, dy)
                val minDist = h.radius + racer.carType.radiusPx
                if (dist < minDist) {
                    val cx = track.worldWidth * 0.5f
                    val cy = track.worldHeight * 0.5f
                    val rawNx = if (dist > 0.05f) dx / dist else (cx - h.x) / hypot(cx - h.x, cy - h.y).coerceAtLeast(1f)
                    val rawNy = if (dist > 0.05f) dy / dist else (cy - h.y) / hypot(cx - h.x, cy - h.y).coerceAtLeast(1f)
                    // Bias push slightly toward arena center so cars bounce into the safe brawl zone
                    val toCx = (cx - h.x) / hypot(cx - h.x, cy - h.y).coerceAtLeast(1f)
                    val toCy = (cy - h.y) / hypot(cx - h.x, cy - h.y).coerceAtLeast(1f)
                    val blendX = rawNx * 0.72f + toCx * 0.28f
                    val blendY = rawNy * 0.72f + toCy * 0.28f
                    val bLen = hypot(blendX, blendY).coerceAtLeast(0.01f)
                    val nx = blendX / bLen
                    val ny = blendY / bLen

                    racer.x = h.x + nx * (minDist + 3f)
                    racer.y = h.y + ny * (minDist + 3f)

                    val vn = racer.vx * nx + racer.vy * ny
                    if (vn < 80f) {
                        racer.vx = (racer.vx - 1.55f * vn.coerceAtMost(0f) * nx) * 0.84f + nx * 95f
                        racer.vy = (racer.vy - 1.55f * vn.coerceAtMost(0f) * ny) * 0.84f + ny * 95f
                    }
                    if (racer.wallBumpCooldownMs == 0L) {
                        racer.wallBumpCooldownMs = 320L
                        racer.collisionSeq += 1
                        explosions.add(
                            TrackExplosion(
                                id = nextExplosionId++,
                                x = h.x,
                                y = h.y,
                                ageMs = 0L,
                                durationMs = 420L
                            )
                        )
                        if (racer.shieldMs == 0L &&
                            racer.overdriveMs == 0L &&
                            racer.stunnedMs == 0L &&
                            racer.spinImmunityMs == 0L
                        ) {
                            racer.stunnedMs = 800L
                            racer.spinImmunityMs = 2400L
                            if (flagCarrierId == racer.playerId) {
                                dropFlagFrom(racer, "clipped a Sudden Death barrier")
                            }
                        }
                    }
                }
                continue
            }

            val isMine = hazType == HazardType.MINE
            val selfGraceCutoff = if (isMine) 43800L else 9800L
            if (h.ownerId == racer.playerId && h.remainingMs > selfGraceCutoff) continue

            if (TrackCatalog.distance(racer.x, racer.y, h.x, h.y) <= h.radius + racer.carType.radiusPx * 0.7f) {
                it.remove()
                racer.collisionSeq += 1

                if (isMine) {
                    explosions.add(
                        TrackExplosion(
                            id = nextExplosionId++,
                            x = h.x,
                            y = h.y,
                            ageMs = 0L,
                            durationMs = 680L
                        )
                    )
                    if (racer.shieldMs > 0L) {
                        racer.shieldMs = 0L
                        setBanner("${racer.name}'s Shield absorbed Mine blast!", 1600L)
                    } else {
                        // Same slip/spin behavior as a puddle or oil slip + small explosion
                        racer.stunnedMs = 1150L
                        racer.spinImmunityMs = 2400L
                        racer.vx *= 0.42f
                        racer.vy *= 0.42f
                        val owner = racers.find { o -> o.playerId == h.ownerId }
                        if (owner != null && owner.playerId != racer.playerId) {
                            owner.powerUpHits += 1
                            owner.racePoints += 40
                        }
                        if (flagCarrierId == racer.playerId) {
                            dropFlagFrom(racer, "hit a Mine")
                        } else {
                            setBanner("BOOM! ${racer.name} slipped on a Red Mine!", 1750L)
                        }
                    }
                } else {
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
                    val aParalyzed = a.electroParalyzedMs > 0L
                    val bParalyzed = b.electroParalyzedMs > 0L
                    when {
                        aParalyzed && !bParalyzed -> {
                            b.x += nx * overlap * 2f
                            b.y += ny * overlap * 2f
                        }
                        bParalyzed && !aParalyzed -> {
                            a.x -= nx * overlap * 2f
                            a.y -= ny * overlap * 2f
                        }
                        else -> {
                            a.x -= nx * overlap
                            a.y -= ny * overlap
                            b.x += nx * overlap
                            b.y += ny * overlap
                        }
                    }

                    val relVx = b.vx - a.vx
                    val relVy = b.vy - a.vy
                    val sepVel = relVx * nx + relVy * ny
                    if (sepVel < 0f) {
                        if (sepVel < -38f) {
                            a.collisionSeq += 1
                            b.collisionSeq += 1
                        }
                        val impulse = -1.2f * sepVel * 0.5f
                        if (!aParalyzed) {
                            a.vx -= impulse * nx
                            a.vy -= impulse * ny
                        } else {
                            a.vx = 0f
                            a.vy = 0f
                        }
                        if (!bParalyzed) {
                            b.vx += impulse * nx
                            b.vy += impulse * ny
                        } else {
                            b.vx = 0f
                            b.vy = 0f
                        }
                    }

                    // Arena mode: colliding with the flag carrier steals/transfers the flag!
                    if (isArenaMode && phase == RacePhase.RACING && flagTransferCooldownMs == 0L) {
                        if (flagCarrierId == a.playerId && b.stunnedMs == 0L && !bParalyzed) {
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
                        } else if (flagCarrierId == b.playerId && a.stunnedMs == 0L && !aParalyzed) {
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

                    onAdvanceToNextLap(racer)
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

    private fun onAdvanceToNextLap(racer: MutableRacer) {
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
            val mineArmed = grantLapMineIfEligible(racer)
            val dailyNitroArmed = grantDailyNitroIfEligible(racer)

            if (winCondition != WinCondition.TIME_TRIAL_DISTANCE && racer.lap == effectiveLaps) {
                setBanner(
                    when {
                        dailyNitroArmed -> "${racer.name}: FINAL LAP — 1 NITRO READY!"
                        mineArmed -> "${racer.name}: FINAL LAP — MINE READY!"
                        else -> "${racer.name}: FINAL LAP!"
                    },
                    1800L
                )
            } else if (dailyNitroArmed && !racer.isBot) {
                setBanner("${racer.name}: LAP ${racer.lap} — 1 NITRO READY!", 1600L)
            } else if (mineArmed && !racer.isBot) {
                setBanner("${racer.name}: LAP ${racer.lap} — MINE READY!", 1600L)
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
        if (isStuntMode) {
            return racer.racePoints * 1000f - racers.indexOf(racer).toFloat()
        }
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
        var targetX = tp.x + (-sin(segAngle)) * racer.botLaneOffset
        var targetY = tp.y + cos(segAngle) * racer.botLaneOffset
        if (isDriftMode) {
            val adjusted = com.example.drift.DriftPhysics.avoidSolidObstacles(
                track = track,
                racerX = racer.x,
                racerY = racer.y,
                racerAngleRad = racer.angleRad,
                targetX = targetX,
                targetY = targetY
            )
            targetX = adjusted.first
            targetY = adjusted.second
        }

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

        val shrinkScale = TrackCatalog.arenaShrinkScaleForStage(arenaSuddenDeathStage)
        if (carrierId == racer.playerId) {
            val radFromCenter = atan2(racer.y - cy, racer.x - cx)
            val orbitAng = radFromCenter + 0.65f
            targetX = cx + cos(orbitAng) * (320f * shrinkScale)
            targetY = cy + sin(orbitAng) * (235f * shrinkScale)

            val chaser = racers
                .filter { it.playerId != racer.playerId }
                .minByOrNull { TrackCatalog.distance(racer.x, racer.y, it.x, it.y) }
            if (chaser != null) {
                val d = TrackCatalog.distance(racer.x, racer.y, chaser.x, chaser.y)
                if (d < 260f * shrinkScale && d > 1f) {
                    targetX += ((racer.x - chaser.x) / d) * (180f * shrinkScale)
                    targetY += ((racer.y - chaser.y) / d) * (180f * shrinkScale)
                }
            }
        } else if (carrierId != null) {
            val carrier = racers.find { it.playerId == carrierId }
            if (carrier != null) {
                targetX = carrier.x + carrier.vx * 0.16f
                targetY = carrier.y + carrier.vy * 0.16f
            } else {
                targetX = flagX
                targetY = flagY
            }
        } else {
            if (racer.flagPickupImmunityMs > 0L) {
                targetX = cx
                targetY = cy
            } else {
                targetX = flagX
                targetY = flagY
            }
        }

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

        for (h in hazards) {
            if (h.hazardTypeEnum == HazardType.BARRIER) {
                val d = TrackCatalog.distance(racer.x, racer.y, h.x, h.y)
                if (d < h.radius + 82f && d > 1f) {
                    targetX += ((racer.x - h.x) / d) * 145f
                    targetY += ((racer.y - h.y) / d) * 145f
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
        val suddenDeathRem = if (isArenaMode && arenaSuddenDeathStage > 0 && phase == RacePhase.RACING) {
            (ARENA_SUDDEN_DEATH_END_MS - raceTimeMs).coerceAtLeast(0L)
        } else {
            0L
        }

        val stuntSnap = stuntController?.let { sc ->
            val activeRacer = racers.getOrNull(sc.turnIndex.coerceIn(0, (racers.size - 1).coerceAtLeast(0)))
            sc.buildSnapshot(
                activePlayerId = activeRacer?.playerId.orEmpty(),
                activePlayerName = activeRacer?.name.orEmpty()
            )
        }

        return RaceSnapshot(
            raceTimeMs = raceTimeMs,
            countdownSec = countdownSec,
            phase = phase.name,
            trackId = track.id.name,
            totalLaps = if (stuntController != null) stuntController.totalRounds else effectiveLaps,
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
                    flagCarryMs = r.flagCarryMs,
                    minesAvailable = r.minesAvailable,
                    invertedSteerMs = r.invertedSteerMs,
                    collisionSeq = r.collisionSeq,
                    portalWarpSeq = r.portalWarpSeq,
                    electroShockAuraMs = r.electroShockAuraMs,
                    electroParalyzedMs = r.electroParalyzedMs,
                    laserBeamMs = r.laserBeamMs
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
            gameMode = when {
                isStuntMode -> GameMode.STUNTS.name
                isArenaMode -> GameMode.ARENA.name
                isDriftMode -> GameMode.DRIFT.name
                else -> GameMode.RACE.name
            },
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
            },
            fieldPickups = fieldPickups.toList(),
            portalPair = portalPair,
            explosions = explosions.toList(),
            arenaSuddenDeathStage = if (isArenaMode) arenaSuddenDeathStage else 0,
            arenaSuddenDeathRemainingMs = suddenDeathRem,
            stuntState = stuntSnap,
            isHybridTour = isHybridTour,
            hybridStageNumber = hybridStageNumber,
            nextHybridMode = nextHybridModeName,
            rockets = activeRockets.map { rk ->
                ActiveRocketState(
                    id = rk.id,
                    ownerId = rk.ownerId,
                    targetId = rk.targetId,
                    x = rk.x,
                    y = rk.y,
                    angleRad = rk.angleRad,
                    remainingMs = rk.remainingMs
                )
            }
        )
    }

    enum class SteeringBehaviorMode {
        /** Original curve: linear speedNorm (0.32..1.0) up to 180 px/s, producing a flat turning radius below 180 px/s. */
        LEGACY_FLAT_LOW_SPEED,
        /** Realistic speed-scaled curve: smaller turning radius the slower the car is, with mild high-speed stability. */
        SPEED_SCALED_RADIUS
    }

    companion object {
        /**
         * Global steering model switch (no per-user setting).
         * Flip to [SteeringBehaviorMode.LEGACY_FLAT_LOW_SPEED] to revert globally to the prior steering curve.
         */
        val GLOBAL_STEERING_BEHAVIOR: SteeringBehaviorMode = SteeringBehaviorMode.SPEED_SCALED_RADIUS

        const val FINISH_GRACE_MS = 10_000L
        const val STALLED_RIVAL_MIN_GRACE_MS = 2_000L
        const val STALLED_RIVAL_THRESHOLD_MS = 3_500L
        const val ARENA_SUDDEN_DEATH_START_MS = 120_000L
        const val ARENA_SUDDEN_DEATH_WAVE_MS = 10_000L
        const val ARENA_SUDDEN_DEATH_DURATION_MS = 30_000L
        const val ARENA_SUDDEN_DEATH_END_MS = ARENA_SUDDEN_DEATH_START_MS + ARENA_SUDDEN_DEATH_DURATION_MS
        const val ELECTRO_AURA_DURATION_MS = 4000L
        const val ELECTRO_PARALYZE_DURATION_MS = 2800L
        const val ELECTRO_SHOCK_RADIUS_PX = 155f
        const val LASER_DURATION_MS = 4000L
        const val LASER_LENGTH_PX = 340f
        const val ROCKET_DURATION_MS = 6000L
        private const val MAX_FIELD_PICKUPS = 2

        fun computeSteeringSpeedFactor(
            absForwardSpeedPx: Float,
            speedMultiplier: Float,
            mode: SteeringBehaviorMode = GLOBAL_STEERING_BEHAVIOR
        ): Float {
            val safeMult = speedMultiplier.coerceAtLeast(0.25f)
            val refSpeed = 180f * safeMult
            return when (mode) {
                SteeringBehaviorMode.LEGACY_FLAT_LOW_SPEED -> {
                    (absForwardSpeedPx / refSpeed).coerceIn(0.32f, 1.0f)
                }
                SteeringBehaviorMode.SPEED_SCALED_RADIUS -> {
                    val lowSpeedFactor = sqrt((absForwardSpeedPx / refSpeed).coerceIn(0f, 1f))
                    val highSpeedTaper =
                        1.0f - 0.10f * ((absForwardSpeedPx - refSpeed) / (380f * safeMult)).coerceIn(0f, 1f)
                    (lowSpeedFactor * highSpeedTaper).coerceIn(0.24f, 1.0f)
                }
            }
        }

        fun normalizeAngle(angle: Float): Float {
            var a = angle
            val twoPi = (2.0 * PI).toFloat()
            while (a > PI) a -= twoPi
            while (a < -PI) a += twoPi
            return a
        }
    }
}

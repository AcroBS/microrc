package com.example.engine

import com.example.data.TrackCatalog
import com.example.model.ControlInput
import com.example.model.PowerUpType
import com.example.model.StuntGolfHole
import com.example.model.StuntPinState
import com.example.model.StuntStateSnapshot
import com.example.model.StuntVariant
import com.example.model.TrackDefinition
import com.example.model.TrackExplosion
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.random.Random

internal class StuntArenaController(
    val sequence: List<StuntVariant>,
    private val random: Random
) {
    data class PinBody(
        val id: Int,
        var x: Float,
        var y: Float,
        var vx: Float = 0f,
        var vy: Float = 0f,
        var knocked: Boolean = false
    )

    var roundIndexZeroBased: Int = 0
        private set
    var turnIndex: Int = 0
        private set
    var turnPhase: String = PHASE_DRIVING
        private set
    var turnTimeRemainingMs: Long = TURN_DURATION_MS
        private set
    var postScoreHoldMs: Long = 0L
        private set

    var lastAttemptScore: Int = 0
        private set
    var lastAttemptSummary: String = ""
        private set
    var bestDistanceMeters: Float = 0f
        private set

    var hasLaunchedRamp: Boolean = false
        private set
    var hasLandedJump: Boolean = false
        private set
    var landingX: Float = 0f
        private set
    var landingY: Float = 700f
        private set
    private var groundedStoppedMs: Long = 0L
    private var bowlingSettleMs: Long = 0L
    private var bowlingTouched: Boolean = false

    // Soccer Ball state
    var ballX: Float = BALL_START_X
        private set
    var ballY: Float = BALL_START_Y
        private set
    var ballVx: Float = 0f
        private set
    var ballVy: Float = 0f
        private set
    var ballScoredPoints: Int = 0
        private set
    private var ballTouched: Boolean = false
    private var ballPostTouchMs: Long = 0L

    // Bowling 9-pin rack state
    private val pins: MutableList<PinBody> = mutableListOf()

    // Golf 6 random numbered holes (shared across all players within the same Golf round for fairness)
    private var roundGolfHolesTemplate: List<StuntGolfHole> = emptyList()
    private var activeTurnGolfHoles: MutableList<StuntGolfHole> = mutableListOf()
    private var botTargetGolfHoleNumber: Int = 1

    val activeVariant: StuntVariant
        get() = sequence.getOrElse(roundIndexZeroBased) { StuntVariant.LONG_JUMP }

    val totalRounds: Int
        get() = sequence.size.coerceAtLeast(1)

    fun trackForCurrentRound(): TrackDefinition = TrackCatalog.getStuntTrack(activeVariant)

    fun initializeFirstRound(playerCount: Int, onSetupTurn: (turnIdx: Int, variant: StuntVariant) -> Unit) {
        roundIndexZeroBased = 0
        turnIndex = 0
        prepareRoundAssets()
        resetTurnObjects()
        if (playerCount > 0) {
            onSetupTurn(0, activeVariant)
        }
    }

    private fun prepareRoundAssets() {
        if (activeVariant == StuntVariant.GOLF_HOLES) {
            roundGolfHolesTemplate = TrackCatalog.generateGolfHoles(random)
        }
    }

    private fun resetTurnObjects() {
        turnPhase = PHASE_DRIVING
        turnTimeRemainingMs = TURN_DURATION_MS
        postScoreHoldMs = 0L
        hasLaunchedRamp = false
        hasLandedJump = false
        landingX = 0f
        landingY = 700f
        groundedStoppedMs = 0L
        bowlingSettleMs = 0L
        bowlingTouched = false

        ballX = BALL_START_X
        ballY = BALL_START_Y
        ballVx = 0f
        ballVy = 0f
        ballScoredPoints = 0
        ballTouched = false
        ballPostTouchMs = 0L

        pins.clear()
        if (activeVariant == StuntVariant.BOWLING) {
            TrackCatalog.defaultBowlingPins().forEach { p ->
                pins.add(PinBody(id = p.id, x = p.x, y = p.y, knocked = false))
            }
        }

        activeTurnGolfHoles.clear()
        if (activeVariant == StuntVariant.GOLF_HOLES) {
            if (roundGolfHolesTemplate.isEmpty()) {
                roundGolfHolesTemplate = TrackCatalog.generateGolfHoles(random)
            }
            activeTurnGolfHoles.addAll(roundGolfHolesTemplate.map { it.copy(captured = false) })
            val pick = activeTurnGolfHoles.randomOrNull(random)
            botTargetGolfHoleNumber = pick?.number ?: 1
        }
    }

    fun shouldCutForwardThrottle(carX: Float, jumpZ: Float): Boolean {
        if (turnPhase != PHASE_DRIVING) return true
        if (carX <= RAMP_EXIT_X || jumpZ > 0.05f) return false
        return activeVariant == StuntVariant.LONG_JUMP || activeVariant == StuntVariant.GOLF_HOLES
    }

    fun applyProgressiveRunwayBoost(
        carX: Float,
        carY: Float,
        jumpZ: Float,
        baseMaxSpeed: Float,
        currentBoostMs: Long
    ): Pair<Long, Float>? {
        if (jumpZ > 0.12f) return null
        val pads = listOf(
            Triple(1005f, 700f, 1.38f),
            Triple(1130f, 700f, 1.54f),
            Triple(1250f, 700f, 1.70f)
        )
        for ((px, py, mult) in pads) {
            if (TrackCatalog.distance(carX, carY, px, py) <= 56f) {
                return max(currentBoostMs, 1300L) to (baseMaxSpeed * mult)
            }
        }
        return null
    }

    fun checkMegaRampLaunch(
        carX: Float,
        carY: Float,
        jumpZ: Float,
        vx: Float,
        vy: Float,
        baseMaxSpeed: Float
    ): Triple<Float, Float, Float>? {
        if (hasLaunchedRamp || jumpZ > 0.05f) return null
        if (carX in 1310f..1398f && carY in 550f..850f) {
            val speed = hypot(vx, vy)
            if (speed >= 95f) {
                hasLaunchedRamp = true
                val speedRatio = (speed / baseMaxSpeed.coerceAtLeast(180f)).coerceIn(0.45f, 1.95f)
                val vz = 3.25f + speedRatio * 2.15f
                return Triple(vx * 1.18f, vy * 1.04f, vz)
            }
        }
        return null
    }

    data class TurnStepOutcome(
        val bannerMessage: String? = null,
        val pointsAwarded: Int = 0,
        val spawnedExplosion: TrackExplosion? = null,
        val bumpedCollision: Boolean = false,
        val advanceToNextTurn: Boolean = false
    )

    fun stepTurn(
        dtMs: Long,
        dt: Float,
        carX: Float,
        carY: Float,
        carVx: Float,
        carVy: Float,
        carRadius: Float,
        jumpZ: Float,
        wasAirborneBeforeTick: Boolean,
        nextExplosionId: () -> Int,
        snapCarTo: (Float, Float) -> Unit
    ): TurnStepOutcome {
        if (turnPhase == PHASE_SCORED) {
            // Continue animating ball / pins while celebrating score
            stepSoccerBallPhysics(dt)
            stepBowlingPinPhysics(dt)
            postScoreHoldMs = (postScoreHoldMs - dtMs).coerceAtLeast(0L)
            return TurnStepOutcome(advanceToNextTurn = postScoreHoldMs == 0L)
        }

        turnTimeRemainingMs = (turnTimeRemainingMs - dtMs).coerceAtLeast(0L)
        val carSpeed = hypot(carVx, carVy)

        when (activeVariant) {
            StuntVariant.LONG_JUMP -> {
                if (hasLaunchedRamp && (wasAirborneBeforeTick || carX > RAMP_EXIT_X + 25f) && jumpZ <= 0.001f && !hasLandedJump) {
                    hasLandedJump = true
                    landingX = carX
                    landingY = carY
                    val meters = ((carX - RAMP_ZERO_METER_X) / PIXELS_PER_METER).coerceAtLeast(0f)
                    if (meters > bestDistanceMeters) {
                        bestDistanceMeters = meters
                    }
                    val pts = (meters * 10f).toInt().coerceIn(25, 1200)
                    val summary = String.format(Locale.US, "%.1fm JUMP! +%d PTS", meters, pts)
                    return completeTurn(
                        points = pts,
                        summary = summary,
                        explosion = TrackExplosion(
                            id = nextExplosionId(),
                            x = carX,
                            y = carY,
                            ageMs = 0L,
                            durationMs = 580L
                        )
                    )
                }
                if (carX > RAMP_ZERO_METER_X + 20f && jumpZ <= 0.01f && carSpeed < 22f) {
                    groundedStoppedMs += dtMs
                    if (groundedStoppedMs >= 550L) {
                        val meters = ((carX - RAMP_ZERO_METER_X) / PIXELS_PER_METER).coerceAtLeast(0f)
                        if (meters > bestDistanceMeters) bestDistanceMeters = meters
                        val pts = (meters * 8f).toInt().coerceAtLeast(20)
                        val summary = String.format(Locale.US, "%.1fm ROLLOUT (+%d PTS)", meters, pts)
                        return completeTurn(pts, summary)
                    }
                } else {
                    groundedStoppedMs = 0L
                }
            }

            StuntVariant.SOCCER_BALL -> {
                var hitNow = false
                val distToBall = TrackCatalog.distance(carX, carY, ballX, ballY)
                if (distToBall <= carRadius + BALL_RADIUS && jumpZ <= 1.25f) {
                    val rawDx = ballX - carX
                    val rawDy = ballY - carY
                    val d = hypot(rawDx, rawDy).coerceAtLeast(0.1f)
                    val nx = rawDx / d
                    val ny = rawDy / d
                    ballX = carX + nx * (carRadius + BALL_RADIUS + 3f)
                    ballY = carY + ny * (carRadius + BALL_RADIUS + 3f)
                    val strikeSpeed = max(carSpeed * 1.48f, 470f)
                    ballVx = nx * strikeSpeed * 0.55f + carVx * 0.88f
                    ballVy = ny * strikeSpeed * 0.55f + carVy * 0.88f
                    ballTouched = true
                    hitNow = true
                }

                stepSoccerBallPhysics(dt)

                // Check Goal Line (x >= GOAL_LINE_X, y in GOAL_TOP_Y..GOAL_BOTTOM_Y)
                if (ballX >= GOAL_LINE_X && ballY in GOAL_TOP_Y..GOAL_BOTTOM_Y) {
                    val centerOffset = abs(ballY - 700f)
                    val (pts, label) = when {
                        centerOffset <= 68f -> 1000 to "BULLSEYE CENTER GOAL!"
                        centerOffset <= 155f -> 750 to "INNER GOAL STRIKE!"
                        centerOffset <= 255f -> 500 to "SIDE GOAL!"
                        else -> 250 to "CORNER GOAL!"
                    }
                    ballScoredPoints = pts
                    return completeTurn(
                        points = pts,
                        summary = "$label +$pts PTS",
                        explosion = TrackExplosion(
                            id = nextExplosionId(),
                            x = ballX.coerceAtMost(2460f),
                            y = ballY,
                            ageMs = 0L,
                            durationMs = 700L
                        ),
                        bumped = hitNow
                    )
                }

                if (ballTouched) {
                    ballPostTouchMs += dtMs
                    val ballSpeed = hypot(ballVx, ballVy)
                    if (ballX >= 2465f || (ballPostTouchMs >= 1700L && ballSpeed < 35f)) {
                        return completeTurn(
                            points = 100,
                            summary = "WIDE OF GOAL (+100 PTS)",
                            bumped = hitNow
                        )
                    }
                }
                if (hitNow) {
                    return TurnStepOutcome(bumpedCollision = true)
                }
            }

            StuntVariant.BOWLING -> {
                var hitPinThisTick = false
                if (jumpZ <= 1.25f) {
                    for (pin in pins) {
                        val dx = pin.x - carX
                        val dy = pin.y - carY
                        val dist = hypot(dx, dy)
                        if (dist <= carRadius + PIN_RADIUS + 4f) {
                            val nx = if (dist > 0.05f) dx / dist else 1f
                            val ny = if (dist > 0.05f) dy / dist else 0f
                            val strike = max(carSpeed * 1.18f, 320f)
                            pin.vx = nx * strike * 0.65f + carVx * 0.55f
                            pin.vy = ny * strike * 0.65f + carVy * 0.55f
                            if (!pin.knocked) {
                                pin.knocked = true
                                hitPinThisTick = true
                            }
                            bowlingTouched = true
                        }
                    }
                }

                stepBowlingPinPhysics(dt)

                val knockedCount = pins.count { it.knocked }
                if (knockedCount >= 9) {
                    return completeTurn(
                        points = 1000,
                        summary = "STRIKE! 9/9 PINS (+1000 PTS)",
                        explosion = TrackExplosion(
                            id = nextExplosionId(),
                            x = 1980f,
                            y = 700f,
                            ageMs = 0L,
                            durationMs = 680L
                        ),
                        bumped = hitPinThisTick
                    )
                }

                if (bowlingTouched || (hasLaunchedRamp && jumpZ <= 0.01f && carX > 1700f)) {
                    bowlingSettleMs += dtMs
                    val pinsMoving = pins.any { hypot(it.vx, it.vy) > 32f }
                    if (bowlingSettleMs >= 1550L || (!pinsMoving && bowlingSettleMs >= 900L && carSpeed < 35f)) {
                        val pts = knockedCount * 100
                        val summary = if (knockedCount > 0) {
                            "$knockedCount/9 PINS KNOCKED (+$pts PTS)"
                        } else {
                            "GUTTER JUMP (0/9 PINS)"
                        }
                        return completeTurn(points = pts, summary = summary, bumped = hitPinThisTick)
                    }
                }
                if (hitPinThisTick) {
                    return TurnStepOutcome(bumpedCollision = true)
                }
            }

            StuntVariant.GOLF_HOLES -> {
                if (carX > RAMP_EXIT_X && jumpZ <= 0.16f) {
                    for (idx in activeTurnGolfHoles.indices) {
                        val hole = activeTurnGolfHoles[idx]
                        val d = TrackCatalog.distance(carX, carY, hole.x, hole.y)
                        if (d <= hole.radius * 0.88f && carSpeed <= 440f) {
                            activeTurnGolfHoles[idx] = hole.copy(captured = true)
                            snapCarTo(hole.x, hole.y)
                            return completeTurn(
                                points = hole.points,
                                summary = "HOLE #${hole.number} SUNK! +${hole.points} PTS",
                                explosion = TrackExplosion(
                                    id = nextExplosionId(),
                                    x = hole.x,
                                    y = hole.y,
                                    ageMs = 0L,
                                    durationMs = 640L
                                )
                            )
                        }
                    }
                }

                if (carX > RAMP_EXIT_X + 35f && jumpZ <= 0.01f && carSpeed < 20f) {
                    groundedStoppedMs += dtMs
                    if (groundedStoppedMs >= 650L) {
                        val nearest = activeTurnGolfHoles.minByOrNull {
                            TrackCatalog.distance(carX, carY, it.x, it.y) - it.radius
                        }
                        val edgeDist = nearest?.let {
                            (TrackCatalog.distance(carX, carY, it.x, it.y) - it.radius).coerceAtLeast(0f)
                        } ?: 999f
                        val greenBonus = if (edgeDist <= 140f) {
                            (120f - edgeDist * 0.6f).toInt().coerceIn(40, 120)
                        } else {
                            40
                        }
                        return completeTurn(
                            points = greenBonus,
                            summary = "ON THE GREEN (+$greenBonus PTS)"
                        )
                    }
                } else {
                    groundedStoppedMs = 0L
                }
            }
        }

        // Turn clock expiration fallback
        if (turnTimeRemainingMs == 0L) {
            val fallbackPts = when (activeVariant) {
                StuntVariant.LONG_JUMP -> {
                    val m = ((carX - RAMP_ZERO_METER_X) / PIXELS_PER_METER).coerceAtLeast(0f)
                    if (m > bestDistanceMeters) bestDistanceMeters = m
                    (m * 8f).toInt().coerceAtLeast(0)
                }
                StuntVariant.SOCCER_BALL -> if (ballTouched) 100 else 0
                StuntVariant.BOWLING -> pins.count { it.knocked } * 100
                StuntVariant.GOLF_HOLES -> if (carX > RAMP_EXIT_X + 40f) 40 else 0
            }
            return completeTurn(
                points = fallbackPts,
                summary = if (fallbackPts > 0) "TIME UP! +$fallbackPts PTS" else "TIME UP! 0 PTS"
            )
        }

        return TurnStepOutcome()
    }

    private fun stepSoccerBallPhysics(dt: Float) {
        if (abs(ballVx) < 0.5f && abs(ballVy) < 0.5f) return
        ballX += ballVx * dt
        ballY += ballVy * dt
        val drag = max(0f, 1f - 0.82f * dt)
        ballVx *= drag
        ballVy *= drag

        if (ballY < 250f + BALL_RADIUS) {
            ballY = 250f + BALL_RADIUS
            ballVy = abs(ballVy) * 0.78f
        } else if (ballY > 1150f - BALL_RADIUS) {
            ballY = 1150f - BALL_RADIUS
            ballVy = -abs(ballVy) * 0.78f
        }
        if (ballX < RAMP_EXIT_X + BALL_RADIUS) {
            ballX = RAMP_EXIT_X + BALL_RADIUS
            ballVx = abs(ballVx) * 0.7f
        } else if (ballX > 2530f - BALL_RADIUS) {
            ballX = 2530f - BALL_RADIUS
            ballVx = -abs(ballVx) * 0.45f
        }
    }

    private fun stepBowlingPinPhysics(dt: Float) {
        if (pins.isEmpty()) return
        for (p in pins) {
            if (abs(p.vx) > 1f || abs(p.vy) > 1f) {
                p.x += p.vx * dt
                p.y += p.vy * dt
                val drag = max(0f, 1f - 2.1f * dt)
                p.vx *= drag
                p.vy *= drag
                p.x = p.x.coerceIn(1450f, 2520f)
                p.y = p.y.coerceIn(240f, 1160f)
            }
        }
        // Pin-to-pin chain-reaction collisions
        for (i in pins.indices) {
            for (j in i + 1 until pins.size) {
                val a = pins[i]
                val b = pins[j]
                if (!a.knocked && !b.knocked) continue
                val dx = b.x - a.x
                val dy = b.y - a.y
                val dist = hypot(dx, dy)
                val minD = PIN_RADIUS * 2.1f
                if (dist < minD && dist > 0.05f) {
                    val nx = dx / dist
                    val ny = dy / dist
                    val overlap = (minD - dist) * 0.5f
                    a.x -= nx * overlap
                    a.y -= ny * overlap
                    b.x += nx * overlap
                    b.y += ny * overlap
                    val relSpeed = hypot(a.vx - b.vx, a.vy - b.vy)
                    if (relSpeed > 45f) {
                        a.knocked = true
                        b.knocked = true
                        val imp = relSpeed * 0.48f
                        if (hypot(a.vx, a.vy) > hypot(b.vx, b.vy)) {
                            b.vx += nx * imp
                            b.vy += ny * imp
                            a.vx *= 0.7f
                            a.vy *= 0.7f
                        } else {
                            a.vx -= nx * imp
                            a.vy -= ny * imp
                            b.vx *= 0.7f
                            b.vy *= 0.7f
                        }
                    }
                }
            }
        }
    }

    private fun completeTurn(
        points: Int,
        summary: String,
        explosion: TrackExplosion? = null,
        bumped: Boolean = false
    ): TurnStepOutcome {
        turnPhase = PHASE_SCORED
        postScoreHoldMs = POST_SCORE_HOLD_MS
        lastAttemptScore = points
        lastAttemptSummary = summary
        return TurnStepOutcome(
            bannerMessage = summary,
            pointsAwarded = points,
            spawnedExplosion = explosion,
            bumpedCollision = bumped,
            advanceToNextTurn = false
        )
    }

    /**
     * Advances to next player or next stunt round. Returns true if the entire Stunt match is finished!
     */
    fun advanceTurnOrRound(
        playerCount: Int,
        onSetupTurn: (turnIdx: Int, variant: StuntVariant, newRoundStarted: Boolean) -> Unit
    ): Boolean {
        if (playerCount <= 0) return true
        val nextTurn = turnIndex + 1
        if (nextTurn < playerCount) {
            turnIndex = nextTurn
            resetTurnObjects()
            onSetupTurn(turnIndex, activeVariant, false)
            return false
        }

        val nextRound = roundIndexZeroBased + 1
        if (nextRound < sequence.size) {
            roundIndexZeroBased = nextRound
            turnIndex = 0
            prepareRoundAssets()
            resetTurnObjects()
            onSetupTurn(0, activeVariant, true)
            return false
        }

        return true
    }

    fun computeBotInput(
        carX: Float,
        carY: Float,
        carAngleRad: Float,
        heldPowerUp: PowerUpType?
    ): Pair<ControlInput, Boolean> {
        if (turnPhase != PHASE_DRIVING) {
            return ControlInput(throttle = 0f, steer = 0f) to false
        }

        // Slalom waypoints around the 4 runway obstacles at (465,635), (615,765), (765,632), (900,762)
        val (targetX, targetY) = when {
            carX < 460f -> 475f to 728f
            carX < 610f -> 625f to 672f
            carX < 760f -> 775f to 728f
            carX < 910f -> 915f to 676f
            carX < RAMP_EXIT_X -> {
                val aimY = when (activeVariant) {
                    StuntVariant.LONG_JUMP -> 700f
                    StuntVariant.SOCCER_BALL -> 700f
                    StuntVariant.BOWLING -> 700f
                    StuntVariant.GOLF_HOLES -> {
                        val targetHole = activeTurnGolfHoles.find { it.number == botTargetGolfHoleNumber }
                            ?: activeTurnGolfHoles.firstOrNull()
                        val hy = targetHole?.y ?: 700f
                        (700f + (hy - 700f) * 0.28f).coerceIn(600f, 800f)
                    }
                }
                1360f to aimY
            }
            else -> {
                when (activeVariant) {
                    StuntVariant.SOCCER_BALL -> ballX to ballY
                    StuntVariant.BOWLING -> 1980f to 700f
                    StuntVariant.GOLF_HOLES -> {
                        val h = activeTurnGolfHoles.find { it.number == botTargetGolfHoleNumber }
                            ?: activeTurnGolfHoles.firstOrNull()
                        (h?.x ?: 2000f) to (h?.y ?: 700f)
                    }
                    StuntVariant.LONG_JUMP -> 2450f to 700f
                }
            }
        }

        val desiredAng = atan2(targetY - carY, targetX - carX)
        var diff = desiredAng - carAngleRad
        val twoPi = (2.0 * PI).toFloat()
        while (diff > PI) diff -= twoPi
        while (diff < -PI) diff += twoPi

        val steer = (diff / 0.55f).coerceIn(-1f, 1f)
        val throttle = if (shouldCutForwardThrottle(carX, 0f)) 0f else 1f
        val triggerNitro = heldPowerUp == PowerUpType.TURBO_NITRO && carX >= 935f && abs(diff) < 0.35f
        return ControlInput(throttle = throttle, steer = steer, usePowerUp = triggerNitro) to triggerNitro
    }

    fun buildSnapshot(activePlayerId: String, activePlayerName: String): StuntStateSnapshot {
        return StuntStateSnapshot(
            variant = activeVariant.name,
            roundIndex = roundIndexZeroBased + 1,
            totalRounds = totalRounds,
            turnIndex = turnIndex,
            activePlayerId = activePlayerId,
            activePlayerName = activePlayerName,
            turnPhase = turnPhase,
            turnTimeRemainingMs = turnTimeRemainingMs,
            lastAttemptScore = lastAttemptScore,
            lastAttemptSummary = lastAttemptSummary,
            bestDistanceMeters = bestDistanceMeters,
            landingX = landingX,
            landingY = landingY,
            hasLanded = hasLandedJump,
            ballX = ballX,
            ballY = ballY,
            ballVx = ballVx,
            ballVy = ballVy,
            ballScoredPoints = ballScoredPoints,
            pins = pins.map { StuntPinState(id = it.id, x = it.x, y = it.y, knocked = it.knocked) },
            golfHoles = activeTurnGolfHoles.toList()
        )
    }

    companion object {
        const val PHASE_DRIVING = "DRIVING"
        const val PHASE_SCORED = "SCORED"
        const val TURN_DURATION_MS = 15_000L
        const val POST_SCORE_HOLD_MS = 1450L

        const val RAMP_EXIT_X = 1390f
        const val RAMP_ZERO_METER_X = 1400f
        const val PIXELS_PER_METER = 9.5f

        const val BALL_START_X = 1830f
        const val BALL_START_Y = 700f
        const val BALL_RADIUS = 40f
        const val GOAL_LINE_X = 2340f
        const val GOAL_TOP_Y = 340f
        const val GOAL_BOTTOM_Y = 1060f

        const val PIN_RADIUS = 21f
    }
}

package com.example.model

enum class ControlMode(val label: String, val subtitle: String) {
    TOUCH_OVERLAY("Touch Pedals", "On-screen steering & throttle buttons"),
    TILT_SENSOR("Phone Tilt", "Tilt device to steer + touch throttle")
}

enum class RcCarType(
    val displayName: String,
    val tagline: String,
    val topSpeedStat: Int,
    val accelStat: Int,
    val handlingStat: Int,
    val gripStat: Int,
    val maxSpeedPx: Float,
    val accelRate: Float,
    val turnRateRad: Float,
    val lateralGrip: Float,
    val offRoadRetention: Float,
    val radiusPx: Float
) {
    VOLT_BUGGY(
        displayName = "Volt Buggy",
        tagline = "Balanced brushless all-rounder",
        topSpeedStat = 78,
        accelStat = 82,
        handlingStat = 84,
        gripStat = 84,
        maxSpeedPx = 560f,
        accelRate = 540f,
        turnRateRad = 3.35f,
        lateralGrip = 6.8f,
        offRoadRetention = 0.84f,
        radiusPx = 22f
    ),
    APEX_FORMULA(
        displayName = "Apex Formula",
        tagline = "Low-slung tarmac speed missile",
        topSpeedStat = 96,
        accelStat = 74,
        handlingStat = 78,
        gripStat = 64,
        maxSpeedPx = 630f,
        accelRate = 490f,
        turnRateRad = 3.10f,
        lateralGrip = 7.4f,
        offRoadRetention = 0.56f,
        radiusPx = 22f
    ),
    TUNDRA_MONSTER(
        displayName = "Tundra Crusher",
        tagline = "4WD monster tires conquer dirt & bumps",
        topSpeedStat = 70,
        accelStat = 76,
        handlingStat = 72,
        gripStat = 96,
        maxSpeedPx = 520f,
        accelRate = 510f,
        turnRateRad = 2.95f,
        lateralGrip = 6.4f,
        offRoadRetention = 0.96f,
        radiusPx = 25f
    ),
    DRIFT_PHANTOM(
        displayName = "Drift Phantom",
        tagline = "Snap-turn street tuner with slick slide",
        topSpeedStat = 85,
        accelStat = 92,
        handlingStat = 94,
        gripStat = 72,
        maxSpeedPx = 585f,
        accelRate = 620f,
        turnRateRad = 3.85f,
        lateralGrip = 4.9f,
        offRoadRetention = 0.70f,
        radiusPx = 22f
    )
}

data class RcColorPreset(
    val name: String,
    val primaryArgb: Long,
    val accentArgb: Long
)

val RcColorPresets = listOf(
    RcColorPreset("Signal Orange", 0xFFFF5722, 0xFFFFD54F),
    RcColorPreset("Electric Cyan", 0xFF00E5FF, 0xFF0B0E14),
    RcColorPreset("Crimson Spark", 0xFFFF1744, 0xFFFFFFFF),
    RcColorPreset("Acid Lime", 0xFFB2FF59, 0xFF141A24),
    RcColorPreset("Cobalt Velocity", 0xFF2979FF, 0xFF00E5FF),
    RcColorPreset("Hyper Magenta", 0xFFE040FB, 0xFFFFD54F),
    RcColorPreset("Trophy Gold", 0xFFFFC107, 0xFF1B1F2A),
    RcColorPreset("Stealth Carbon", 0xFF455A64, 0xFFFF5722)
)

data class PlayerProfile(
    val playerId: String,
    val name: String = "Racer",
    val carType: RcCarType = RcCarType.VOLT_BUGGY,
    val primaryColorArgb: Long = RcColorPresets[0].primaryArgb,
    val accentColorArgb: Long = RcColorPresets[0].accentArgb,
    val controlMode: ControlMode = ControlMode.TOUCH_OVERLAY,
    val tiltSensitivity: Float = 1.25f,
    val autoGasInTilt: Boolean = false
)

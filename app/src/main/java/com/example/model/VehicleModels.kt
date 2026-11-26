package com.example.model

import kotlin.math.abs

enum class ControlMode(val label: String, val shortLabel: String, val subtitle: String) {
    TOUCH_OVERLAY("Touch Pedals", "Pedals", "On-screen steering & throttle buttons"),
    SPRING_SLIDER("Spring Slider", "Slider", "Proportional slider + tap/hold arrow overlays"),
    TILT_SENSOR("Phone Tilt", "Tilt", "Tilt device to steer + touch throttle")
}

/**
 * Maps a normalized horizontal slider thumb offset in [-1f, 1f] to proportional steering in [-1f, 1f].
 * Applies an adjustable sensitivity multiplier, a small center deadzone, and a subtle cubic RC expo
 * curve for responsive steering that snaps cleanly to neutral when released.
 */
fun computeSpringSliderSteer(
    normalizedOffset: Float,
    sensitivity: Float = 1.45f,
    deadzone: Float = 0.02f,
    expo: Float = 0.18f
): Float {
    val clamped = normalizedOffset.coerceIn(-1f, 1f)
    val mag = abs(clamped)
    if (mag <= deadzone) return 0f
    val sign = if (clamped >= 0f) 1f else -1f
    val sens = sensitivity.coerceIn(0.6f, 3.0f)
    val rescaled = (((mag - deadzone) / (1f - deadzone).coerceAtLeast(0.001f)) * sens).coerceIn(0f, 1f)
    val u = sign * rescaled
    val e = expo.coerceIn(0f, 0.85f)
    return ((1f - e) * u + e * u * u * u).coerceIn(-1f, 1f)
}

enum class RcCarType(
    val storageId: String,
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
        storageId = "volt_buggy",
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
        storageId = "apex_formula",
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
        storageId = "tundra_monster",
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
        storageId = "drift_phantom",
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
    );

    companion object {
        fun fromIdOrName(raw: String?): RcCarType {
            val clean = raw?.trim().orEmpty()
            if (clean.isEmpty()) return VOLT_BUGGY
            return entries.firstOrNull {
                it.name.equals(clean, ignoreCase = true) ||
                    it.storageId.equals(clean, ignoreCase = true)
            } ?: VOLT_BUGGY
        }
    }
}

/**
 * Purely visual cosmetic upgrade slots unlocked via 5-day streak tokens.
 * Never modifies vehicle speed, acceleration, handling, grip, or collision hitbox.
 */
enum class CosmeticUpgradeSlot(
    val storageKey: String,
    val title: String,
    val description: String
) {
    BIGGER_TIRES(
        storageKey = "bigger_tires",
        title = "Bigger Tires",
        description = "Wider monster off-road tread blocks"
    ),
    REAR_SPOILER(
        storageKey = "rear_spoiler",
        title = "Rear Spoiler",
        description = "Aerodynamic rear wing with endplates"
    ),
    RACING_BUMPERS(
        storageKey = "racing_bumpers",
        title = "Racing Bumpers",
        description = "Front bash-bar splitter & rear diffuser"
    ),
    WHIP_ANTENNA(
        storageKey = "whip_antenna",
        title = "RC Whip Antenna",
        description = "Flexible whip antenna with neon tip"
    ),
    ROOF_LED_LIGHTBAR(
        storageKey = "roof_led_lightbar",
        title = "Roof LED Lightbar",
        description = "Glowing roof LED bar & accent pods"
    ),
    RACING_LOGO(
        storageKey = "racing_logo",
        title = "Hood/Roof Logo",
        description = "Custom racing stripe & apex emblem"
    ),
    CUSTOM_TRAIL(
        storageKey = "custom_trail",
        title = "Boost & Tire Trail",
        description = "Plasma nitro jet & colored drift trails"
    ),
    UNDERGLOW_NEON(
        storageKey = "underglow_neon",
        title = "Underglow Neon Kit",
        description = "Soft neon ground halo matching accent"
    );

    val bitFlag: Int
        get() = 1 shl ordinal
}

/**
 * Per-car visual cosmetic unlock & equip state. Locked slots can never be equipped.
 */
data class CarVisualUpgrades(
    val unlockedMask: Int = 0,
    val equippedMask: Int = 0
) {
    val sanitizedUnlockedMask: Int
        get() = unlockedMask and ALL_SLOTS_MASK

    val sanitizedEquippedMask: Int
        get() = equippedMask and sanitizedUnlockedMask

    val unlockedCount: Int
        get() = Integer.bitCount(sanitizedUnlockedMask)

    val equippedCount: Int
        get() = Integer.bitCount(sanitizedEquippedMask)

    fun isUnlocked(slot: CosmeticUpgradeSlot): Boolean =
        (sanitizedUnlockedMask and slot.bitFlag) != 0

    fun isEquipped(slot: CosmeticUpgradeSlot): Boolean =
        (sanitizedEquippedMask and slot.bitFlag) != 0

    val hasBiggerTires: Boolean get() = isEquipped(CosmeticUpgradeSlot.BIGGER_TIRES)
    val hasRearSpoiler: Boolean get() = isEquipped(CosmeticUpgradeSlot.REAR_SPOILER)
    val hasRacingBumpers: Boolean get() = isEquipped(CosmeticUpgradeSlot.RACING_BUMPERS)
    val hasWhipAntenna: Boolean get() = isEquipped(CosmeticUpgradeSlot.WHIP_ANTENNA)
    val hasRoofLedLightbar: Boolean get() = isEquipped(CosmeticUpgradeSlot.ROOF_LED_LIGHTBAR)
    val hasRacingLogo: Boolean get() = isEquipped(CosmeticUpgradeSlot.RACING_LOGO)
    val hasCustomTrail: Boolean get() = isEquipped(CosmeticUpgradeSlot.CUSTOM_TRAIL)
    val hasUnderglowNeon: Boolean get() = isEquipped(CosmeticUpgradeSlot.UNDERGLOW_NEON)

    fun withUnlockedAndEquipped(slot: CosmeticUpgradeSlot): CarVisualUpgrades {
        val nextUnlocked = (sanitizedUnlockedMask or slot.bitFlag) and ALL_SLOTS_MASK
        val nextEquipped = (sanitizedEquippedMask or slot.bitFlag) and nextUnlocked
        return CarVisualUpgrades(unlockedMask = nextUnlocked, equippedMask = nextEquipped)
    }

    fun withEquipped(slot: CosmeticUpgradeSlot, equipped: Boolean): CarVisualUpgrades {
        if (!isUnlocked(slot)) return CarVisualUpgrades(sanitizedUnlockedMask, sanitizedEquippedMask)
        val nextEquipped = if (equipped) {
            sanitizedEquippedMask or slot.bitFlag
        } else {
            sanitizedEquippedMask and slot.bitFlag.inv()
        }
        return CarVisualUpgrades(unlockedMask = sanitizedUnlockedMask, equippedMask = nextEquipped)
    }

    companion object {
        const val ALL_SLOTS_MASK: Int = (1 shl 8) - 1 // 255

        fun sanitizeMask(rawMask: Int): Int = rawMask and ALL_SLOTS_MASK

        fun fromEquippedMask(mask: Int): CarVisualUpgrades {
            val clean = sanitizeMask(mask)
            return CarVisualUpgrades(unlockedMask = clean, equippedMask = clean)
        }
    }
}

data class DecodedCloudGarageState(
    val earnedTokens: Int = 0,
    val upgradesByCarId: Map<String, CarVisualUpgrades> = emptyMap()
)

object CarVisualCodec {
    private const val PREFIX = "v1"

    fun encode(
        earnedTokens: Int,
        upgradesByCarId: Map<String, CarVisualUpgrades>
    ): String {
        val entries = RcCarType.entries.mapNotNull { car ->
            val st = upgradesByCarId[car.storageId]
                ?: upgradesByCarId[car.name]
                ?: return@mapNotNull null
            val u = st.sanitizedUnlockedMask
            val e = st.sanitizedEquippedMask
            if (u == 0 && e == 0) null else "${car.storageId}:$u:$e"
        }.joinToString(";")
        return "$PREFIX|${earnedTokens.coerceAtLeast(0)}|$entries".take(2048)
    }

    fun decode(raw: String?): DecodedCloudGarageState {
        val clean = raw?.trim().orEmpty()
        if (clean.isEmpty()) return DecodedCloudGarageState()
        val parts = clean.split('|')
        if (parts.isEmpty() || parts[0] != PREFIX) return DecodedCloudGarageState()
        val parsedTokens = parts.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val map = LinkedHashMap<String, CarVisualUpgrades>()
        parts.getOrNull(2).orEmpty()
            .split(';')
            .filter { it.isNotBlank() }
            .forEach { token ->
                val segs = token.split(':')
                if (segs.size < 3) return@forEach
                val car = RcCarType.fromIdOrName(segs[0])
                val u = CarVisualUpgrades.sanitizeMask(segs[1].toIntOrNull() ?: 0)
                val e = CarVisualUpgrades.sanitizeMask(segs[2].toIntOrNull() ?: 0) and u
                if (u != 0 || e != 0) {
                    map[car.storageId] = CarVisualUpgrades(unlockedMask = u, equippedMask = e)
                }
            }
        val minTokensFromUnlocks = map.values.sumOf { it.unlockedCount }
        return DecodedCloudGarageState(
            earnedTokens = maxOf(parsedTokens, minTokensFromUnlocks),
            upgradesByCarId = map
        )
    }

    fun normalizeMap(rawMap: Map<String, CarVisualUpgrades>): Map<String, CarVisualUpgrades> {
        val out = LinkedHashMap<String, CarVisualUpgrades>()
        rawMap.forEach { (key, value) ->
            val car = RcCarType.fromIdOrName(key)
            val clean = CarVisualUpgrades(
                unlockedMask = value.sanitizedUnlockedMask,
                equippedMask = value.sanitizedEquippedMask
            )
            if (clean.sanitizedUnlockedMask != 0 || clean.sanitizedEquippedMask != 0) {
                val existing = out[car.storageId]
                out[car.storageId] = if (existing == null) {
                    clean
                } else {
                    val u = existing.sanitizedUnlockedMask or clean.sanitizedUnlockedMask
                    val e = (existing.sanitizedEquippedMask or clean.sanitizedEquippedMask) and u
                    CarVisualUpgrades(u, e)
                }
            }
        }
        return out
    }

    fun mergeGarageStates(
        localEarnedTokens: Int,
        localUpgrades: Map<String, CarVisualUpgrades>,
        cloudEarnedTokens: Int,
        cloudUpgrades: Map<String, CarVisualUpgrades>
    ): DecodedCloudGarageState {
        val normLocal = normalizeMap(localUpgrades)
        val normCloud = normalizeMap(cloudUpgrades)
        val merged = LinkedHashMap<String, CarVisualUpgrades>()
        for (car in RcCarType.entries) {
            val l = normLocal[car.storageId]
            val c = normCloud[car.storageId]
            if (l == null && c == null) continue
            val u = (l?.sanitizedUnlockedMask ?: 0) or (c?.sanitizedUnlockedMask ?: 0)
            // Keep local equipped choices for locally-known unlocks; adopt cloud equipped state for newly hydrated cloud unlocks
            val lUnlocked = l?.sanitizedUnlockedMask ?: 0
            val lEquipped = l?.sanitizedEquippedMask ?: 0
            val cEquippedForNew = (c?.sanitizedEquippedMask ?: 0) and lUnlocked.inv()
            val e = (lEquipped or cEquippedForNew) and u
            if (u != 0) {
                merged[car.storageId] = CarVisualUpgrades(unlockedMask = u, equippedMask = e)
            }
        }
        val minUnlockedTokens = merged.values.sumOf { it.unlockedCount }
        val mergedEarned = maxOf(localEarnedTokens, cloudEarnedTokens, minUnlockedTokens)
        return DecodedCloudGarageState(earnedTokens = mergedEarned, upgradesByCarId = merged)
    }
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
    val autoGasInTilt: Boolean = false,
    val sliderSensitivity: Float = 1.45f,
    val swapControls: Boolean = false,
    val showLiveLapDelta: Boolean = true,
    val showTrackMiniMap: Boolean = true,
    val carUpgradesByCarId: Map<String, CarVisualUpgrades> = emptyMap(),
    val earnedUpgradeTokens: Int = 0
) {
    fun upgradesFor(car: RcCarType): CarVisualUpgrades {
        val raw = carUpgradesByCarId[car.storageId]
            ?: carUpgradesByCarId[car.name]
            ?: CarVisualUpgrades()
        return CarVisualUpgrades(
            unlockedMask = raw.sanitizedUnlockedMask,
            equippedMask = raw.sanitizedEquippedMask
        )
    }

    val equippedVisualUpgrades: CarVisualUpgrades
        get() = upgradesFor(carType)

    val equippedUpgradesMask: Int
        get() = equippedVisualUpgrades.sanitizedEquippedMask

    val totalUnlockedUpgradesCount: Int
        get() = CarVisualCodec.normalizeMap(carUpgradesByCarId).values.sumOf { it.unlockedCount }

    val availableUpgradeTokens: Int
        get() = (earnedUpgradeTokens - totalUnlockedUpgradesCount).coerceAtLeast(0)

    val encodedCarVisuals: String
        get() = CarVisualCodec.encode(earnedUpgradeTokens, carUpgradesByCarId)
}

package com.example.model

import com.squareup.moshi.JsonClass

enum class PowerUpType(
    val displayName: String,
    val shortName: String,
    val description: String,
    val colorHex: Long
) {
    TURBO_NITRO(
        displayName = "LiPo Turbo",
        shortName = "NITRO",
        description = "2.5s blazing speed burst",
        colorHex = 0xFFFF5722
    ),
    EMP_ZAP(
        displayName = "EMP Zap",
        shortName = "ZAP",
        description = "Stuns the nearest rival ahead",
        colorHex = 0xFF00E5FF
    ),
    SHIELD(
        displayName = "RC Shield",
        shortName = "SHIELD",
        description = "Blocks hazards & EMP for 5s",
        colorHex = 0xFFB2FF59
    ),
    GLUE_SLICK(
        displayName = "Glue Slick",
        shortName = "GLUE",
        description = "Drops sticky trap behind you",
        colorHex = 0xFFFFD54F
    ),
    OVERDRIVE_GRIP(
        displayName = "Overdrive",
        shortName = "GRIP+",
        description = "Max traction & boost for 4s",
        colorHex = 0xFFE040FB
    ),
    ELECTRO_SHOCK(
        displayName = "Electro Shock",
        shortName = "SHOCK",
        description = "4s electric aura freezes nearby cars",
        colorHex = 0xFF7C4DFF
    )
}

enum class HazardType {
    GLUE_SLICK,
    MINE,
    BARRIER
}

enum class FieldPickupType(
    val displayName: String
) {
    POSITION_SWAP("Position Swap"),
    STEER_INVERT("Steering Invert")
}

@JsonClass(generateAdapter = true)
data class ItemBoxState(
    val id: Int,
    val x: Float,
    val y: Float,
    val available: Boolean = true,
    val respawnMs: Long = 0L
)

@JsonClass(generateAdapter = true)
data class TrackHazard(
    val id: Int,
    val ownerId: String,
    val x: Float,
    val y: Float,
    val radius: Float = 32f,
    val remainingMs: Long = 10000L,
    val type: String = HazardType.GLUE_SLICK.name
) {
    val hazardTypeEnum: HazardType
        get() = runCatching { HazardType.valueOf(type) }.getOrDefault(HazardType.GLUE_SLICK)
}

@JsonClass(generateAdapter = true)
data class TrackFieldPickup(
    val id: Int,
    val x: Float,
    val y: Float,
    val type: String = FieldPickupType.POSITION_SWAP.name,
    val remainingMs: Long = 16000L
) {
    val pickupTypeEnum: FieldPickupType
        get() = runCatching { FieldPickupType.valueOf(type) }.getOrDefault(FieldPickupType.POSITION_SWAP)
}

@JsonClass(generateAdapter = true)
data class PortalPairState(
    val id: Int,
    val entryX: Float,
    val entryY: Float,
    val entryColorIsRed: Boolean = false,
    val exitX: Float,
    val exitY: Float,
    val exitAngleRad: Float = 0f,
    val remainingMs: Long = 12000L
)

@JsonClass(generateAdapter = true)
data class TrackExplosion(
    val id: Int,
    val x: Float,
    val y: Float,
    val ageMs: Long = 0L,
    val durationMs: Long = 650L
)

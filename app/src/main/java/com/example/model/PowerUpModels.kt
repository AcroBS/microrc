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
    )
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
    val remainingMs: Long = 10000L
)

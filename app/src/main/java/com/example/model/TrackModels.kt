package com.example.model

enum class TrackId(
    val displayName: String,
    val subtitle: String,
    val difficulty: String,
    val surfaceLabel: String,
    val baseBgColorHex: Long,
    val roadColorHex: Long,
    val borderColorHex: Long,
    val kerbPrimaryHex: Long,
    val kerbSecondaryHex: Long,
    val offRoadDragMultiplier: Float,
    val surfaceGripMultiplier: Float
) {
    LIVING_ROOM_GP(
        displayName = "Living Room GP",
        subtitle = "Hardwood straights & cozy rug chicanes",
        difficulty = "Easy",
        surfaceLabel = "Hardwood / Rug",
        baseBgColorHex = 0xFF5D4037,
        roadColorHex = 0xFF2E3B4E,
        borderColorHex = 0xFFD7CCC8,
        kerbPrimaryHex = 0xFFFF7043,
        kerbSecondaryHex = 0xFFFFF8E1,
        offRoadDragMultiplier = 0.72f,
        surfaceGripMultiplier = 1.0f
    ),
    BACKYARD_DIRT(
        displayName = "Backyard Dirt Oval",
        subtitle = "Loose loamy clay with mud slicks",
        difficulty = "Medium",
        surfaceLabel = "Packed Dirt",
        baseBgColorHex = 0xFF2D4C2B,
        roadColorHex = 0xFF8D5B3C,
        borderColorHex = 0xFF5C3A24,
        kerbPrimaryHex = 0xFFE53935,
        kerbSecondaryHex = 0xFFFFFFFF,
        offRoadDragMultiplier = 0.65f,
        surfaceGripMultiplier = 0.86f
    ),
    NEON_ROOFTOP(
        displayName = "Neon Rooftop",
        subtitle = "High-grip night tarmac & turbo pads",
        difficulty = "Medium",
        surfaceLabel = "Night Asphalt",
        baseBgColorHex = 0xFF0A0F1C,
        roadColorHex = 0xFF1F273B,
        borderColorHex = 0xFF00E5FF,
        kerbPrimaryHex = 0xFF00E5FF,
        kerbSecondaryHex = 0xFFE040FB,
        offRoadDragMultiplier = 0.60f,
        surfaceGripMultiplier = 1.08f
    ),
    SANDBOX_DUNES(
        displayName = "Sandbox Dunes",
        subtitle = "Sweeping desert S-curves & oasis hazards",
        difficulty = "Hard",
        surfaceLabel = "Desert Sand",
        baseBgColorHex = 0xFFD9A760,
        roadColorHex = 0xFFC27E3E,
        borderColorHex = 0xFF8A4F1D,
        kerbPrimaryHex = 0xFFFFD54F,
        kerbSecondaryHex = 0xFFBF360C,
        offRoadDragMultiplier = 0.58f,
        surfaceGripMultiplier = 0.80f
    ),
    WAREHOUSE_DRIFT(
        displayName = "Warehouse Drift Yard",
        subtitle = "Polished concrete hairpins & crate obstacles",
        difficulty = "Pro",
        surfaceLabel = "Slick Concrete",
        baseBgColorHex = 0xFF1D222B,
        roadColorHex = 0xFF3E4656,
        borderColorHex = 0xFFFFAB00,
        kerbPrimaryHex = 0xFFFFAB00,
        kerbSecondaryHex = 0xFF1A1E26,
        offRoadDragMultiplier = 0.62f,
        surfaceGripMultiplier = 0.78f
    )
}

data class TrackPoint(
    val x: Float,
    val y: Float,
    val halfWidth: Float = 115f
)

data class BoostPad(
    val x: Float,
    val y: Float,
    val angleRad: Float,
    val radius: Float = 44f
)

data class ItemBoxSpawn(
    val id: Int,
    val x: Float,
    val y: Float
)

data class TrackProp(
    val x: Float,
    val y: Float,
    val radius: Float,
    val shape: PropShape,
    val colorHex: Long,
    val accentHex: Long = 0xFFFFFFFF,
    val isSolidObstacle: Boolean = false
)

enum class PropShape {
    CIRCLE,
    CRATE,
    TREE,
    CACTUS,
    PYLON,
    MUD_POOL
}

data class TrackDefinition(
    val id: TrackId,
    val worldWidth: Float,
    val worldHeight: Float,
    val centerline: List<TrackPoint>,
    val boostPads: List<BoostPad>,
    val itemSpawns: List<ItemBoxSpawn>,
    val props: List<TrackProp>,
    val defaultLaps: Int = 3
)

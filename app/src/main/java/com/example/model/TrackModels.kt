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
        subtitle = "Balanced tabletop GP with rug S-bend & sweeping corners",
        difficulty = "Easy",
        surfaceLabel = "Hardwood / Rug",
        baseBgColorHex = 0xFF5D4037,
        roadColorHex = 0xFF2E3B4E,
        borderColorHex = 0xFFD7CCC8,
        kerbPrimaryHex = 0xFFFF7043,
        kerbSecondaryHex = 0xFFFFF8E1,
        offRoadDragMultiplier = 0.72f,
        surfaceGripMultiplier = 1.00f
    ),
    BACKYARD_DIRT(
        displayName = "Backyard Mud Rally",
        subtitle = "Wide CCW twin-peak rallycross with mud slicks & big jumps",
        difficulty = "Medium",
        surfaceLabel = "Loamy Dirt & Mud",
        baseBgColorHex = 0xFF2D4C2B,
        roadColorHex = 0xFF8D5B3C,
        borderColorHex = 0xFF5C3A24,
        kerbPrimaryHex = 0xFFE53935,
        kerbSecondaryHex = 0xFFFFFFFF,
        offRoadDragMultiplier = 0.68f,
        surfaceGripMultiplier = 0.84f
    ),
    NEON_ROOFTOP(
        displayName = "Neon Rooftop",
        subtitle = "Supersonic delta tri-oval with 5 turbo pads & sky jumps",
        difficulty = "Fast",
        surfaceLabel = "High-Grip Tarmac",
        baseBgColorHex = 0xFF0A0F1C,
        roadColorHex = 0xFF1F273B,
        borderColorHex = 0xFF00E5FF,
        kerbPrimaryHex = 0xFF00E5FF,
        kerbSecondaryHex = 0xFFE040FB,
        offRoadDragMultiplier = 0.58f,
        surfaceGripMultiplier = 1.12f
    ),
    SANDBOX_DUNES(
        displayName = "Sandbox Dunes",
        subtitle = "CCW desert hourglass with 6 switchbacks & oasis slicks",
        difficulty = "Hard",
        surfaceLabel = "Loose Canyon Sand",
        baseBgColorHex = 0xFFD9A760,
        roadColorHex = 0xFFC27E3E,
        borderColorHex = 0xFF8A4F1D,
        kerbPrimaryHex = 0xFFFFD54F,
        kerbSecondaryHex = 0xFFBF360C,
        offRoadDragMultiplier = 0.56f,
        surfaceGripMultiplier = 0.80f
    ),
    WAREHOUSE_DRIFT(
        displayName = "Warehouse Drift Yard",
        subtitle = "Narrow L-boot concrete gymkhana with 90° hairpins & oil",
        difficulty = "Pro",
        surfaceLabel = "Slick Concrete",
        baseBgColorHex = 0xFF1D222B,
        roadColorHex = 0xFF3E4656,
        borderColorHex = 0xFFFFAB00,
        kerbPrimaryHex = 0xFFFFAB00,
        kerbSecondaryHex = 0xFF1A1E26,
        offRoadDragMultiplier = 0.62f,
        surfaceGripMultiplier = 0.75f
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
    val radius: Float = 44f,
    val lateralOffset: Float = 0f
)

data class TrackRamp(
    val x: Float,
    val y: Float,
    val angleRad: Float,
    val radius: Float = 48f,
    val impulse: Float = 1.0f,
    val lateralOffset: Float = 0f
)

data class GravelPatch(
    val x: Float,
    val y: Float,
    val angleRad: Float,
    val length: Float,
    val halfWidth: Float,
    val leftEdge: List<Pair<Float, Float>> = emptyList(),
    val rightEdge: List<Pair<Float, Float>> = emptyList(),
    val startArcS: Float = 0f,
    val endArcS: Float = 0f,
    val totalSplineLength: Float = 0f,
    val isSplineRibbon: Boolean = false
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

enum class ArenaShape(
    val displayName: String,
    val subtitle: String,
    val shapeLabel: String,
    val themeTrackId: TrackId
) {
    SQUARE(
        displayName = "Square Colosseum",
        subtitle = "Wide 90° corners for tactical jukes & ambushes",
        shapeLabel = "Square",
        themeTrackId = TrackId.WAREHOUSE_DRIFT
    ),
    CIRCLE(
        displayName = "Circle Thunder Bowl",
        subtitle = "360° circular basin with non-stop high-speed chases",
        shapeLabel = "Circle",
        themeTrackId = TrackId.NEON_ROOFTOP
    ),
    STAR(
        displayName = "Star Citadel",
        subtitle = "Five-point star alcoves & high-risk central crossfire",
        shapeLabel = "5-Point Star",
        themeTrackId = TrackId.LIVING_ROOM_GP
    ),
    HEXAGON(
        displayName = "Hexagon Crater",
        subtitle = "Six-sided dirt pit built for slides & mid-air steals",
        shapeLabel = "Hexagon",
        themeTrackId = TrackId.BACKYARD_DIRT
    ),
    CLOVER(
        displayName = "Diamond Cross",
        subtitle = "Four sweeping wings converging on a desert arena",
        shapeLabel = "Diamond",
        themeTrackId = TrackId.SANDBOX_DUNES
    )
}

enum class StuntVariant(
    val displayName: String,
    val shortName: String,
    val subtitle: String,
    val themeTrackId: TrackId
) {
    LONG_JUMP(
        displayName = "Mega Long Jump",
        shortName = "LONG JUMP",
        subtitle = "Weave the slalom, hit speed pads & nitro, and jump as far as possible!",
        themeTrackId = TrackId.NEON_ROOFTOP
    ),
    SOCCER_BALL(
        displayName = "Rocket Goal Strike",
        shortName = "SOCCER GOAL",
        subtitle = "Launch off the ramp and smash the ball into the center bullseye goal!",
        themeTrackId = TrackId.LIVING_ROOM_GP
    ),
    BOWLING(
        displayName = "9-Pin RC Bowling",
        shortName = "9-PIN BOWL",
        subtitle = "Fly off the ramp into 9 giant bowling pins for a chain-reaction strike!",
        themeTrackId = TrackId.WAREHOUSE_DRIFT
    ),
    GOLF_HOLES(
        displayName = "Target Golf Drop",
        shortName = "RC GOLF",
        subtitle = "Land in numbered holes — smaller cups award massive bonus points!",
        themeTrackId = TrackId.BACKYARD_DIRT
    )
}

data class CustomTrackData(
    val id: String,
    val name: String,
    val themeId: String = TrackId.NEON_ROOFTOP.name,
    val roadHalfWidth: Float = 112f,
    val controlPoints: List<TrackPoint>,
    val boostPads: List<BoostPad> = emptyList(),
    val itemSpawns: List<ItemBoxSpawn> = emptyList(),
    val ramps: List<TrackRamp> = emptyList(),
    val props: List<TrackProp> = emptyList(),
    val puddles: List<TrackPuddle> = emptyList(),
    val creatorUid: String = "",
    val creatorName: String = "",
    val isNightTrack: Boolean = false,
    val randomConditionsEnabled: Boolean = false
)

data class TrackDefinition(
    val id: TrackId,
    val worldWidth: Float,
    val worldHeight: Float,
    val centerline: List<TrackPoint>,
    val boostPads: List<BoostPad>,
    val itemSpawns: List<ItemBoxSpawn>,
    val props: List<TrackProp>,
    val ramps: List<TrackRamp> = emptyList(),
    val puddles: List<TrackPuddle> = emptyList(),
    val defaultLaps: Int = 3,
    val customId: String? = null,
    val customName: String? = null,
    val isArena: Boolean = false,
    val arenaShape: ArenaShape? = null,
    val arenaBoundary: List<TrackPoint> = emptyList(),
    val isStunt: Boolean = false,
    val stuntVariant: StuntVariant? = null,
    val isNightTrack: Boolean = false,
    val randomConditionsEnabled: Boolean = false
) {
    val displayTitle: String
        get() = customName ?: stuntVariant?.displayName ?: arenaShape?.displayName ?: id.displayName
}

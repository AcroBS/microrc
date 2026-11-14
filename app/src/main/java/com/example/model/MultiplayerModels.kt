package com.example.model

import com.squareup.moshi.JsonClass

enum class RacePhase {
    COUNTDOWN,
    RACING,
    FINISHED
}

enum class WinCondition(
    val displayName: String,
    val description: String
) {
    FIRST_TO_FINISH(
        displayName = "First to Finish",
        description = "Complete all laps and cross the checkered line first"
    ),
    TIME_TRIAL_DISTANCE(
        displayName = "Time Limit",
        description = "Race until the clock expires — furthest distance wins"
    ),
    CHAMPIONSHIP_POINTS(
        displayName = "Points & Hits",
        description = "Score points from lap checkpoints, power-up hits & podium finish"
    )
}

enum class ExternalEventType(val displayName: String) {
    NONE("Clear Track"),
    RAIN("Rainfall"),
    EARTHQUAKE("Earthquake"),
    WIND("Crosswind"),
    SNOW("Snowfall")
}

@JsonClass(generateAdapter = true)
data class TrackPuddle(
    val x: Float,
    val y: Float,
    val radius: Float = 42f
)

@JsonClass(generateAdapter = true)
data class AudioSettings(
    val musicEnabled: Boolean = true,
    val carSfxEnabled: Boolean = true
)

@JsonClass(generateAdapter = true)
data class DiscoveredLobby(
    val hostId: String,
    val hostName: String,
    val lobbyName: String,
    val hostIp: String,
    val tcpPort: Int,
    val trackId: String,
    val playerCount: Int,
    val maxPlayers: Int = 4,
    val inRace: Boolean = false,
    val lastSeenMs: Long = 0L
)

@JsonClass(generateAdapter = true)
data class LobbyPlayer(
    val id: String,
    val name: String,
    val isHost: Boolean,
    val isBot: Boolean = false,
    val isReady: Boolean = false,
    val carType: String = RcCarType.VOLT_BUGGY.name,
    val primaryColorArgb: Long = 0xFFFF5722,
    val accentColorArgb: Long = 0xFFFFD54F,
    val sessionPoints: Int = 0,
    val wins: Int = 0
)

@JsonClass(generateAdapter = true)
data class ChatMessage(
    val id: String,
    val senderId: String,
    val senderName: String,
    val text: String,
    val timestampMs: Long,
    val isSystem: Boolean = false
)

@JsonClass(generateAdapter = true)
data class ControlInput(
    val throttle: Float = 0f,
    val steer: Float = 0f,
    val usePowerUp: Boolean = false
)

@JsonClass(generateAdapter = true)
data class RaceRulesSettings(
    val autoAccelerate: Boolean = true,
    val hardTrackBoundary: Boolean = true,
    val randomJumpsEnabled: Boolean = true,
    val randomConditionsEnabled: Boolean = true,
    val winCondition: String = WinCondition.FIRST_TO_FINISH.name,
    val totalLaps: Int = 3,
    val timeLimitSec: Int = 90
) {
    val winConditionEnum: WinCondition
        get() = runCatching { WinCondition.valueOf(winCondition) }.getOrDefault(WinCondition.FIRST_TO_FINISH)
}

@JsonClass(generateAdapter = true)
data class RacerState(
    val playerId: String,
    val name: String,
    val isBot: Boolean = false,
    val carType: String,
    val primaryColorArgb: Long,
    val accentColorArgb: Long,
    val x: Float,
    val y: Float,
    val angleRad: Float,
    val vx: Float = 0f,
    val vy: Float = 0f,
    val speed: Float = 0f,
    val steerVisual: Float = 0f,
    val jumpZ: Float = 0f,
    val lap: Int = 1,
    val checkpointIndex: Int = 0,
    val sectorMask: Int = 0,
    val totalProgress: Float = 0f,
    val currentLapStartMs: Long = 0L,
    val lapTimesMs: List<Long> = emptyList(),
    val bestLapMs: Long? = null,
    val finished: Boolean = false,
    val finishTimeMs: Long? = null,
    val rank: Int = 1,
    val heldPowerUp: String? = null,
    val boostMs: Long = 0L,
    val shieldMs: Long = 0L,
    val stunnedMs: Long = 0L,
    val overdriveMs: Long = 0L,
    val spinVisualOffset: Float = 0f,
    val powerUpHits: Int = 0,
    val racePoints: Int = 0
)

@JsonClass(generateAdapter = true)
data class RaceSnapshot(
    val raceTimeMs: Long = 0L,
    val countdownSec: Int = 3,
    val phase: String = RacePhase.COUNTDOWN.name,
    val trackId: String = TrackId.LIVING_ROOM_GP.name,
    val totalLaps: Int = 3,
    val racers: List<RacerState> = emptyList(),
    val itemBoxes: List<ItemBoxState> = emptyList(),
    val hazards: List<TrackHazard> = emptyList(),
    val eventBanner: String? = null,
    val winCondition: String = WinCondition.FIRST_TO_FINISH.name,
    val timeLimitSec: Int = 90,
    val activeEvent: String = ExternalEventType.NONE.name,
    val eventRemainingMs: Long = 0L,
    val windAngleRad: Float = 0f,
    val windStrength: Float = 0f,
    val rainPuddles: List<TrackPuddle> = emptyList(),
    val postFinishGraceMs: Long = 0L
)

@JsonClass(generateAdapter = true)
data class LobbyStatePayload(
    val hostId: String,
    val lobbyName: String,
    val hostIp: String,
    val tcpPort: Int,
    val udpPort: Int = 0,
    val trackId: String,
    val totalLaps: Int,
    val players: List<LobbyPlayer>,
    val chatHistory: List<ChatMessage>,
    val inRace: Boolean,
    val rules: RaceRulesSettings = RaceRulesSettings(),
    val activeCustomTrack: CustomTrackData? = null
)

@JsonClass(generateAdapter = true)
data class NetworkMessage(
    val type: String,
    val senderId: String = "",
    val lobbyState: LobbyStatePayload? = null,
    val chatMessage: ChatMessage? = null,
    val playerProfile: LobbyPlayer? = null,
    val controlInput: ControlInput? = null,
    val raceSnapshot: RaceSnapshot? = null,
    val trackId: String? = null,
    val totalLaps: Int? = null,
    val isReady: Boolean? = null,
    val rules: RaceRulesSettings? = null,
    val customTrack: CustomTrackData? = null,
    val ramps: List<TrackRamp>? = null,
    val errorReason: String? = null
)

object PacketType {
    const val JOIN_REQUEST = "join_request"
    const val JOIN_REJECTED = "join_rejected"
    const val LOBBY_STATE = "lobby_state"
    const val PLAYER_UPDATE = "player_update"
    const val READY_TOGGLE = "ready_toggle"
    const val CHAT_SEND = "chat_send"
    const val CHAT_BROADCAST = "chat_broadcast"
    const val RACE_START = "race_start"
    const val CONTROL_INPUT = "control_input"
    const val RACE_SNAPSHOT = "race_snapshot"
    const val RACE_FINISHED = "race_finished"
    const val RETURN_TO_LOBBY = "return_to_lobby"
}

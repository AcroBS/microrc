package com.example.drift

import com.example.model.GameMode

/**
 * Single switch and isolation boundary for the Drifting game mode & Daily Drift challenge.
 *
 * Setting [ENABLED] to `false` (or removing this package and its guarded call sites)
 * cleanly disables all Drifting UI, physics, default/random Drift tracks, and Daily Drift
 * without affecting Race, Arena, Stunts, Hybrid, or Daily GP.
 */
object DriftFeature {
    const val ENABLED: Boolean = true

    const val DRIFT_TRACK_PREFIX: String = "drift_"
    const val DAILY_DRIFT_PREFIX: String = "daily_drift_"
    const val DRIFT_BOARD_PREFIX: String = "DRIFT_"

    fun isDriftMode(mode: GameMode?): Boolean {
        return ENABLED && mode == GameMode.DRIFT
    }

    fun isDriftModeName(modeName: String?): Boolean {
        return ENABLED && modeName == GameMode.DRIFT.name
    }

    fun isDriftBoardKey(boardKey: String?): Boolean {
        return ENABLED && !boardKey.isNullOrBlank() && boardKey.startsWith(DRIFT_BOARD_PREFIX)
    }

    fun isDriftTrackKey(trackKey: String?): Boolean {
        if (!ENABLED || trackKey.isNullOrBlank()) return false
        return trackKey.startsWith(DRIFT_TRACK_PREFIX) || trackKey.startsWith(DAILY_DRIFT_PREFIX)
    }

    fun selectableGameModes(): List<GameMode> {
        return if (ENABLED) {
            GameMode.entries.toList()
        } else {
            GameMode.entries.filter { it != GameMode.DRIFT }
        }
    }

    /**
     * Firestore security rules restrict top-level `gameMode` fields on `onlineRooms`,
     * `sessionInvites`, `leaderboardEntries`, and `leaderboardNotifications` to
     * ['RACE', 'ARENA', 'STUNTS', 'HYBRID'].
     * Mapping DRIFT -> RACE on the top-level wire keeps rules 100% compatible while
     * `boardKey` ("DRIFT_..."), `lobbyJson`, `raceStartJson`, and `RaceSnapshot` preserve DRIFT.
     */
    fun toFirestoreWireGameMode(mode: GameMode): String {
        return if (mode == GameMode.DRIFT) GameMode.RACE.name else mode.name
    }
}

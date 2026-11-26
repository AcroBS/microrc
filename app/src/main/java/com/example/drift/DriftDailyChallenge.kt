package com.example.drift

import com.example.data.DailyChallengeCatalog
import com.example.model.CarSpeedPreset
import com.example.model.CustomTrackData
import com.example.model.GameMode
import com.example.model.LeaderboardBoardKey
import com.example.model.RaceRulesSettings
import com.example.model.WinCondition
import java.security.MessageDigest

/**
 * Provides one deterministic Daily Drift Challenge track per UTC calendar day.
 * Same fair-play rules across devices: Drifting mode, time-based 3 laps,
 * solid corner obstacles, zero track power-ups, 1 Nitro per lap.
 */
object DriftDailyChallenge {
    const val DAILY_DRIFT_PREFIX = DriftFeature.DAILY_DRIFT_PREFIX
    const val DAILY_DRIFT_LAPS = 3
    private val DATE_KEY_REGEX = Regex("""^\d{8}$""")

    fun isDailyDriftTrackId(trackKey: String?): Boolean {
        if (!DriftFeature.ENABLED || trackKey.isNullOrBlank()) return false
        return trackKey.startsWith(DAILY_DRIFT_PREFIX) &&
            DATE_KEY_REGEX.matches(trackKey.removePrefix(DAILY_DRIFT_PREFIX))
    }

    fun extractDateKey(trackKey: String?): String? {
        if (!isDailyDriftTrackId(trackKey)) return null
        return trackKey!!.removePrefix(DAILY_DRIFT_PREFIX)
    }

    fun trackIdForDate(dateKey: String = DailyChallengeCatalog.todayDateKey()): String {
        return "$DAILY_DRIFT_PREFIX${DailyChallengeCatalog.normalizeDateKey(dateKey)}"
    }

    fun boardKeyForDate(dateKey: String = DailyChallengeCatalog.todayDateKey()): String {
        return LeaderboardBoardKey.build(
            gameMode = GameMode.DRIFT,
            trackKey = trackIdForDate(dateKey),
            totalLaps = DAILY_DRIFT_LAPS
        )
    }

    fun displayDateLabel(dateKey: String = DailyChallengeCatalog.todayDateKey()): String {
        return DailyChallengeCatalog.displayDateLabel(dateKey)
    }

    fun seedForDate(dateKey: String = DailyChallengeCatalog.todayDateKey()): Long {
        val normalized = DailyChallengeCatalog.normalizeDateKey(dateKey)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("MicroRC_DailyDrift_$normalized".toByteArray(Charsets.UTF_8))
        var result = 0L
        for (i in 0 until 8) {
            result = (result shl 8) or (digest[i].toLong() and 0xFFL)
        }
        return result and Long.MAX_VALUE
    }

    fun dailyDriftCustomTrack(dateKey: String = DailyChallengeCatalog.todayDateKey()): CustomTrackData {
        val normalized = DailyChallengeCatalog.normalizeDateKey(dateKey)
        val label = displayDateLabel(normalized)
        val daySeed = seedForDate(normalized)
        val featureRng = kotlin.random.Random(daySeed xor 0x3D2E9B71L)
        val includeJumps = featureRng.nextFloat() < 0.45f
        val includeConditions = featureRng.nextFloat() < 0.50f
        return DriftTrackCatalog.generateRandomDriftTrack(
            seed = daySeed,
            preferredTheme = null,
            roadHalfWidth = 122f,
            trackIdOverride = trackIdForDate(normalized),
            trackNameOverride = "Daily Drift ($label)",
            forceRandomConditions = includeConditions,
            includeJumps = includeJumps
        )
    }

    fun lockedDailyDriftRules(userRules: RaceRulesSettings): RaceRulesSettings {
        return userRules.copy(
            gameMode = GameMode.DRIFT.name,
            winCondition = WinCondition.FIRST_TO_FINISH.name,
            totalLaps = DAILY_DRIFT_LAPS,
            carSpeed = CarSpeedPreset.FAST.name,
            hardTrackBoundary = true,
            randomJumpsEnabled = true,
            randomConditionsEnabled = true,
            hostRandomConditionsOverride = null
        )
    }
}

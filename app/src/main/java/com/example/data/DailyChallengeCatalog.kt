package com.example.data

import com.example.model.CarSpeedPreset
import com.example.model.CustomTrackData
import com.example.model.GameMode
import com.example.model.LeaderboardBoardKey
import com.example.model.RaceRulesSettings
import com.example.model.TrackId
import com.example.model.WinCondition
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Provides one deterministic Daily Challenge circuit per UTC calendar day.
 * Every player receives the exact same layout, boost pads, jumps, fixed portals,
 * props, and locked fair-play rules (no track power-ups, no mines, 1 Nitro per lap)
 * across retries and devices.
 */
object DailyChallengeCatalog {
    const val DAILY_TRACK_PREFIX = "daily_"
    const val DAILY_LAPS = 3
    private val DATE_KEY_REGEX = Regex("""^\d{8}$""")

    fun todayDateKey(epochMillis: Long = System.currentTimeMillis()): String {
        val fmt = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return fmt.format(Date(epochMillis))
    }

    fun normalizeDateKey(raw: String?): String {
        val cleaned = raw?.trim().orEmpty()
        return if (DATE_KEY_REGEX.matches(cleaned)) cleaned else todayDateKey()
    }

    fun isDailyTrackId(trackKey: String?): Boolean {
        if (trackKey.isNullOrBlank()) return false
        return trackKey.startsWith(DAILY_TRACK_PREFIX) &&
            DATE_KEY_REGEX.matches(trackKey.removePrefix(DAILY_TRACK_PREFIX))
    }

    fun isAnyDailyTrackId(trackKey: String?): Boolean {
        return isDailyTrackId(trackKey) || com.example.drift.DriftDailyChallenge.isDailyDriftTrackId(trackKey)
    }

    fun extractDateKey(trackKey: String?): String? {
        if (!isDailyTrackId(trackKey)) return null
        return trackKey!!.removePrefix(DAILY_TRACK_PREFIX)
    }

    fun trackIdForDate(dateKey: String = todayDateKey()): String {
        return "$DAILY_TRACK_PREFIX${normalizeDateKey(dateKey)}"
    }

    fun boardKeyForDate(dateKey: String = todayDateKey()): String {
        return LeaderboardBoardKey.build(
            gameMode = GameMode.RACE,
            trackKey = trackIdForDate(dateKey),
            totalLaps = DAILY_LAPS
        )
    }

    fun displayDateLabel(dateKey: String = todayDateKey()): String {
        val normalized = normalizeDateKey(dateKey)
        val parser = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
            isLenient = false
        }
        val out = SimpleDateFormat("MMM d", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val parsed = runCatching { parser.parse(normalized) }.getOrNull()
        return if (parsed != null) out.format(parsed) else normalized
    }

    fun seedForDate(dateKey: String = todayDateKey()): Long {
        val normalized = normalizeDateKey(dateKey)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("MicroRC_DailyCircuit_$normalized".toByteArray(Charsets.UTF_8))
        var result = 0L
        for (i in 0 until 8) {
            result = (result shl 8) or (digest[i].toLong() and 0xFFL)
        }
        return result and Long.MAX_VALUE
    }

    fun dailyCustomTrack(dateKey: String = todayDateKey()): CustomTrackData {
        val normalized = normalizeDateKey(dateKey)
        val label = displayDateLabel(normalized)
        return TrackCatalog.generateRandomTrack(
            seed = seedForDate(normalized),
            preferredTheme = null,
            roadHalfWidth = 112f,
            trackIdOverride = trackIdForDate(normalized),
            trackNameOverride = "Daily GP ($label)"
        ).copy(
            // Daily Challenge has no power-ups along the track; racers get 1 manual Nitro per lap
            itemSpawns = emptyList()
        )
    }

    fun lockedDailyRules(userRules: RaceRulesSettings): RaceRulesSettings {
        return userRules.copy(
            gameMode = GameMode.RACE.name,
            winCondition = WinCondition.FIRST_TO_FINISH.name,
            totalLaps = DAILY_LAPS,
            carSpeed = CarSpeedPreset.FAST.name,
            hardTrackBoundary = true,
            randomJumpsEnabled = true,
            randomConditionsEnabled = false
        )
    }

    fun resolveTrackDisplayName(trackKey: String): String {
        com.example.drift.DriftTrackCatalog.resolveTrackDisplayName(trackKey)?.let { return it }
        val dateKey = extractDateKey(trackKey)
        if (dateKey != null) {
            return "Daily GP (${displayDateLabel(dateKey)})"
        }
        formatProceduralTrackNameOrNull(trackKey)?.let { return it }
        return when (trackKey) {
            "STUNT_GAUNTLET", "STUNTS_GAUNTLET" -> "Stunt Gauntlet"
            else -> runCatching { TrackId.valueOf(trackKey).displayName }.getOrDefault(trackKey)
        }
    }

    fun formatProceduralTrackNameOrNull(trackKey: String?): String? {
        if (trackKey.isNullOrBlank()) return null
        val isDriftProc = trackKey.startsWith("drift_rand_")
        val isRaceProc = trackKey.startsWith("rand_")
        if (!isDriftProc && !isRaceProc) return null
        val rawBody = when {
            isDriftProc -> trackKey.removePrefix("drift_rand_")
            else -> trackKey.removePrefix("rand_")
        }
        val primarySegment = rawBody.substringBefore('_')
        val digits = primarySegment.filter { it.isDigit() }.ifEmpty { rawBody.filter { it.isDigit() } }
        val shortCode = when {
            digits.length >= 7 -> digits.drop(digits.length - 7).take(4)
            digits.isNotEmpty() -> digits.takeLast(4).padStart(4, '0')
            else -> (trackKey.hashCode() and 0x7FFFFFFF).toString().takeLast(4).padStart(4, '0')
        }
        return if (isDriftProc) "Procedural Drift #$shortCode" else "Procedural #$shortCode"
    }

    fun millisUntilNextUtcReset(epochMillis: Long = System.currentTimeMillis()): Long {
        val dayMs = 86_400_000L
        val elapsedInDay = epochMillis.mod(dayMs)
        return (dayMs - elapsedInDay).coerceAtLeast(1_000L)
    }

    fun formatCountdownUntilReset(epochMillis: Long = System.currentTimeMillis()): String {
        val remMs = millisUntilNextUtcReset(epochMillis)
        val totalMinutes = remMs / 60_000L
        val hours = totalMinutes / 60L
        val mins = totalMinutes % 60L
        return "${hours}h ${mins}m"
    }
}

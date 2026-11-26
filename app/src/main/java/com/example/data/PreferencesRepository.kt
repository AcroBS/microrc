package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.model.AppThemeMode
import com.example.model.AudioSettings
import com.example.model.CarSpeedPreset
import com.example.model.CarVisualCodec
import com.example.model.CarVisualUpgrades
import com.example.model.ControlMode
import com.example.model.CosmeticUpgradeSlot
import com.example.model.CustomTrackData
import com.example.model.GameMode
import com.example.model.LocalLeaderboardRecord
import com.example.model.PlayerProfile
import com.example.model.RaceRulesSettings
import com.example.model.RcCarType
import com.example.model.RcColorPresets
import com.example.model.ThemeSettings
import com.example.model.WinCondition
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PreferencesRepository(context: Context) {

    private val dataStore: DataStore<Preferences> = getDataStore(context)

    companion object {
        private val storesByAppAndPath = ConcurrentHashMap<String, DataStore<Preferences>>()

        private fun getDataStore(context: Context): DataStore<Preferences> {
            val appCtx = context.applicationContext ?: context
            val targetFile = File(appCtx.filesDir, "datastore/microrc_prefs.preferences_pb")
            val cacheKey = "${System.identityHashCode(appCtx)}:${targetFile.absolutePath}"
            return storesByAppAndPath.computeIfAbsent(cacheKey) {
                PreferenceDataStoreFactory.create(
                    produceFile = { targetFile }
                )
            }
        }
    }

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val customTrackListAdapter = moshi.adapter<List<CustomTrackData>>(
        Types.newParameterizedType(List::class.java, CustomTrackData::class.java)
    )
    private val localLeaderboardListAdapter = moshi.adapter<List<LocalLeaderboardRecord>>(
        Types.newParameterizedType(List::class.java, LocalLeaderboardRecord::class.java)
    )

    private object Keys {
        val PLAYER_ID = stringPreferencesKey("player_id")
        val DRIVER_NAME = stringPreferencesKey("driver_name")
        val CAR_TYPE = stringPreferencesKey("car_type")
        val PRIMARY_COLOR = longPreferencesKey("primary_color")
        val ACCENT_COLOR = longPreferencesKey("accent_color")
        val CONTROL_MODE = stringPreferencesKey("control_mode")
        val TILT_SENSITIVITY = floatPreferencesKey("tilt_sensitivity")
        val SLIDER_SENSITIVITY = floatPreferencesKey("slider_sensitivity")
        val AUTO_GAS_TILT = booleanPreferencesKey("auto_gas_tilt")
        val RULE_GAME_MODE = stringPreferencesKey("rule_game_mode")
        val RULE_AUTO_ACCEL = booleanPreferencesKey("rule_auto_accel_v2")
        val RULE_HARD_BOUNDARY = booleanPreferencesKey("rule_hard_boundary_v2")
        val RULE_RANDOM_JUMPS = booleanPreferencesKey("rule_random_jumps_v2")
        val RULE_RANDOM_CONDITIONS = booleanPreferencesKey("rule_random_conditions")
        val RULE_SHOW_PLAYER_NAMES = booleanPreferencesKey("rule_show_player_names")
        val RULE_WIN_CONDITION = stringPreferencesKey("rule_win_condition")
        val RULE_TOTAL_LAPS = intPreferencesKey("rule_total_laps")
        val RULE_TIME_LIMIT_SEC = intPreferencesKey("rule_time_limit_sec")
        val RULE_ARENA_TARGET_MIN = intPreferencesKey("rule_arena_target_min")
        val RULE_CAR_SPEED = stringPreferencesKey("rule_car_speed")
        val RULE_HYBRID_CIRCUIT = booleanPreferencesKey("rule_hybrid_circuit")
        val RULE_HYBRID_ARENA = booleanPreferencesKey("rule_hybrid_arena")
        val RULE_HYBRID_STUNTS = booleanPreferencesKey("rule_hybrid_stunts")
        val RULE_HYBRID_RANDOM_ORDER = booleanPreferencesKey("rule_hybrid_random_order")
        val RULE_HYBRID_STUNT_ROUNDS = intPreferencesKey("rule_hybrid_stunt_rounds")
        val AUDIO_MUSIC_ENABLED = booleanPreferencesKey("audio_music_enabled")
        val AUDIO_SFX_ENABLED = booleanPreferencesKey("audio_sfx_enabled")
        val AUDIO_HAPTICS_ENABLED = booleanPreferencesKey("audio_haptics_enabled")
        val AUDIO_MUSIC_VOLUME = floatPreferencesKey("audio_music_volume")
        val AUDIO_SFX_VOLUME = floatPreferencesKey("audio_sfx_volume")
        val SWAP_CONTROLS = booleanPreferencesKey("swap_controls")
        val SHOW_LIVE_LAP_DELTA = booleanPreferencesKey("show_live_lap_delta")
        val SHOW_TRACK_MINI_MAP = booleanPreferencesKey("show_track_mini_map")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR_ENABLED = booleanPreferencesKey("dynamic_color_enabled")
        val CUSTOM_TRACKS_JSON = stringPreferencesKey("custom_tracks_json")
        val LOCAL_LEADERBOARD_JSON = stringPreferencesKey("local_leaderboard_json")
        val LAST_NOTIFIED_DAILY_KEY = stringPreferencesKey("last_notified_daily_key")
        val DAILY_NOTIFICATIONS_ENABLED = booleanPreferencesKey("daily_notifications_enabled")
        val NOTIFICATION_PERMISSION_PROMPTED = booleanPreferencesKey("notification_permission_prompted")
        val DISMISSED_UPDATE_TAG = stringPreferencesKey("dismissed_update_tag")
        val STREAK_CURRENT_DAYS = intPreferencesKey("streak_current_days")
        val STREAK_BEST_DAYS = intPreferencesKey("streak_best_days")
        val STREAK_LAST_DATE_KEY = stringPreferencesKey("streak_last_date_key")
        val CAR_UPGRADES_JSON = stringPreferencesKey("car_upgrades_json")
        val GARAGE_EARNED_TOKENS = intPreferencesKey("garage_earned_tokens")
        val GARAGE_LAST_TOKEN_DATE_KEY = stringPreferencesKey("garage_last_token_date_key")
    }

    val profileFlow: Flow<PlayerProfile> = dataStore.data.map { prefs ->
        val id = prefs[Keys.PLAYER_ID] ?: generateStableId()
        val name = prefs[Keys.DRIVER_NAME] ?: "Driver-${id.takeLast(4).uppercase()}"
        val carType = runCatching {
            RcCarType.valueOf(prefs[Keys.CAR_TYPE] ?: RcCarType.VOLT_BUGGY.name)
        }.getOrDefault(RcCarType.VOLT_BUGGY)
        val primary = prefs[Keys.PRIMARY_COLOR] ?: RcColorPresets[0].primaryArgb
        val accent = prefs[Keys.ACCENT_COLOR] ?: RcColorPresets[0].accentArgb
        val controlMode = runCatching {
            ControlMode.valueOf(prefs[Keys.CONTROL_MODE] ?: ControlMode.TOUCH_OVERLAY.name)
        }.getOrDefault(ControlMode.TOUCH_OVERLAY)
        val sensitivity = prefs[Keys.TILT_SENSITIVITY] ?: 1.25f
        val sliderSensitivity = (prefs[Keys.SLIDER_SENSITIVITY] ?: 1.45f).coerceIn(0.7f, 2.6f)
        val decodedVisuals = CarVisualCodec.decode(prefs[Keys.CAR_UPGRADES_JSON])
        val streakCurrent = prefs[Keys.STREAK_CURRENT_DAYS] ?: 0
        val streakBest = prefs[Keys.STREAK_BEST_DAYS] ?: 0
        val storedEarnedTokens = prefs[Keys.GARAGE_EARNED_TOKENS]
            ?: PlayStreakCalculator.backfillInitialEarnedTokens(streakCurrent, streakBest)
        val normalizedUpgrades = CarVisualCodec.normalizeMap(decodedVisuals.upgradesByCarId)
        val minUnlockedTokens = normalizedUpgrades.values.sumOf { it.unlockedCount }
        val resolvedEarnedTokens = maxOf(storedEarnedTokens, decodedVisuals.earnedTokens, minUnlockedTokens)

        PlayerProfile(
            playerId = id,
            name = name,
            carType = carType,
            primaryColorArgb = primary,
            accentColorArgb = accent,
            controlMode = controlMode,
            tiltSensitivity = sensitivity,
            autoGasInTilt = true,
            sliderSensitivity = sliderSensitivity,
            swapControls = prefs[Keys.SWAP_CONTROLS] ?: false,
            showLiveLapDelta = prefs[Keys.SHOW_LIVE_LAP_DELTA] ?: true,
            showTrackMiniMap = prefs[Keys.SHOW_TRACK_MINI_MAP] ?: true,
            carUpgradesByCarId = normalizedUpgrades,
            earnedUpgradeTokens = resolvedEarnedTokens
        )
    }

    val rulesFlow: Flow<RaceRulesSettings> = dataStore.data.map { prefs ->
        val mode = runCatching {
            GameMode.valueOf(prefs[Keys.RULE_GAME_MODE] ?: GameMode.RACE.name)
        }.getOrDefault(GameMode.RACE)
        val winCond = runCatching {
            WinCondition.valueOf(prefs[Keys.RULE_WIN_CONDITION] ?: WinCondition.FIRST_TO_FINISH.name)
        }.getOrDefault(WinCondition.FIRST_TO_FINISH)
        val speedPreset = runCatching {
            CarSpeedPreset.valueOf(prefs[Keys.RULE_CAR_SPEED] ?: CarSpeedPreset.FAST.name)
        }.getOrDefault(CarSpeedPreset.FAST)
        RaceRulesSettings(
            gameMode = mode.name,
            autoAccelerate = true,
            hardTrackBoundary = true,
            randomJumpsEnabled = true,
            randomConditionsEnabled = true,
            hostRandomConditionsOverride = null,
            showPlayerNames = prefs[Keys.RULE_SHOW_PLAYER_NAMES] ?: true,
            winCondition = winCond.name,
            totalLaps = (prefs[Keys.RULE_TOTAL_LAPS] ?: 3).coerceIn(1, 9),
            timeLimitSec = (prefs[Keys.RULE_TIME_LIMIT_SEC] ?: 90).coerceIn(30, 600),
            arenaTargetMinutes = (prefs[Keys.RULE_ARENA_TARGET_MIN] ?: 2).coerceIn(1, 10),
            carSpeed = speedPreset.name,
            hybridIncludeCircuit = prefs[Keys.RULE_HYBRID_CIRCUIT] ?: true,
            hybridIncludeArena = prefs[Keys.RULE_HYBRID_ARENA] ?: true,
            hybridIncludeStunts = prefs[Keys.RULE_HYBRID_STUNTS] ?: true,
            hybridRandomOrder = prefs[Keys.RULE_HYBRID_RANDOM_ORDER] ?: false,
            hybridStuntRounds = (prefs[Keys.RULE_HYBRID_STUNT_ROUNDS] ?: 1).coerceIn(1, 4)
        )
    }

    val audioSettingsFlow: Flow<AudioSettings> = dataStore.data.map { prefs ->
        AudioSettings(
            musicEnabled = prefs[Keys.AUDIO_MUSIC_ENABLED] ?: true,
            carSfxEnabled = prefs[Keys.AUDIO_SFX_ENABLED] ?: true,
            hapticsEnabled = prefs[Keys.AUDIO_HAPTICS_ENABLED] ?: true,
            musicVolume = (prefs[Keys.AUDIO_MUSIC_VOLUME] ?: 0.8f).coerceIn(0f, 1f),
            sfxVolume = (prefs[Keys.AUDIO_SFX_VOLUME] ?: 0.8f).coerceIn(0f, 1f)
        )
    }

    val themeSettingsFlow: Flow<ThemeSettings> = dataStore.data.map { prefs ->
        val mode = runCatching {
            AppThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: AppThemeMode.SYSTEM.name)
        }.getOrDefault(AppThemeMode.SYSTEM)
        ThemeSettings(
            mode = mode,
            dynamicColor = prefs[Keys.DYNAMIC_COLOR_ENABLED] ?: true
        )
    }

    val customTracksFlow: Flow<List<CustomTrackData>> = dataStore.data.map { prefs ->
        val raw = prefs[Keys.CUSTOM_TRACKS_JSON]
        if (raw.isNullOrBlank()) {
            emptyList()
        } else {
            runCatching { customTrackListAdapter.fromJson(raw) }.getOrNull().orEmpty()
        }
    }

    val localLeaderboardFlow: Flow<List<LocalLeaderboardRecord>> = dataStore.data.map { prefs ->
        val raw = prefs[Keys.LOCAL_LEADERBOARD_JSON]
        if (raw.isNullOrBlank()) {
            emptyList()
        } else {
            runCatching { localLeaderboardListAdapter.fromJson(raw) }.getOrNull().orEmpty()
        }
    }

    val dailyNotificationsEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.DAILY_NOTIFICATIONS_ENABLED] ?: true
    }

    val notificationPermissionPromptedFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.NOTIFICATION_PERMISSION_PROMPTED] ?: false
    }

    val dismissedUpdateTagFlow: Flow<String?> = dataStore.data.map { prefs ->
        prefs[Keys.DISMISSED_UPDATE_TAG]
    }

    val playStreakFlow: Flow<PlayStreakState> = dataStore.data.map { prefs ->
        PlayStreakCalculator.resolveForDisplay(
            storedCurrent = prefs[Keys.STREAK_CURRENT_DAYS] ?: 0,
            storedBest = prefs[Keys.STREAK_BEST_DAYS] ?: 0,
            lastDateKey = prefs[Keys.STREAK_LAST_DATE_KEY]
        )
    }

    suspend fun getLastNotifiedDailyKey(): String? {
        return dataStore.data.map { it[Keys.LAST_NOTIFIED_DAILY_KEY] }.first()
    }

    suspend fun isDailyNotificationsEnabled(): Boolean {
        return dailyNotificationsEnabledFlow.first()
    }

    suspend fun markDailyChallengeNotified(dateKey: String) {
        dataStore.edit { it[Keys.LAST_NOTIFIED_DAILY_KEY] = dateKey }
    }

    suspend fun setDailyNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.DAILY_NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun markNotificationPermissionPrompted() {
        dataStore.edit { it[Keys.NOTIFICATION_PERMISSION_PROMPTED] = true }
    }

    suspend fun dismissUpdateTag(tagName: String) {
        val clean = tagName.trim()
        if (clean.isNotEmpty()) {
            dataStore.edit { it[Keys.DISMISSED_UPDATE_TAG] = clean }
        }
    }

    suspend fun clearDismissedUpdateTag() {
        dataStore.edit { it.remove(Keys.DISMISSED_UPDATE_TAG) }
    }

    suspend fun recordPlayStreakActivity(epochMillis: Long = System.currentTimeMillis()): PlayStreakState {
        var nextState = PlayStreakState()
        val todayKey = PlayStreakCalculator.localDateKey(epochMillis)
        dataStore.edit { prefs ->
            val storedCurrent = prefs[Keys.STREAK_CURRENT_DAYS] ?: 0
            val storedBest = prefs[Keys.STREAK_BEST_DAYS] ?: 0
            val prevLastDateKey = prefs[Keys.STREAK_LAST_DATE_KEY]
            val decodedVisuals = CarVisualCodec.decode(prefs[Keys.CAR_UPGRADES_JSON])
            val baseEarned = maxOf(
                prefs[Keys.GARAGE_EARNED_TOKENS]
                    ?: PlayStreakCalculator.backfillInitialEarnedTokens(storedCurrent, storedBest),
                decodedVisuals.earnedTokens
            )
            nextState = PlayStreakCalculator.recordActivity(
                storedCurrent = storedCurrent,
                storedBest = storedBest,
                lastDateKey = prevLastDateKey,
                todayKey = todayKey
            )
            val tokenDelta = PlayStreakCalculator.tokenAwardDeltaOnActivity(
                prevLastDateKey = prevLastDateKey,
                nextState = nextState,
                lastTokenAwardedDateKey = prefs[Keys.GARAGE_LAST_TOKEN_DATE_KEY]
            )
            val updatedTokens = (baseEarned + tokenDelta).coerceAtLeast(baseEarned)
            prefs[Keys.STREAK_CURRENT_DAYS] = nextState.currentStreakDays
            prefs[Keys.STREAK_BEST_DAYS] = nextState.bestStreakDays
            prefs[Keys.STREAK_LAST_DATE_KEY] = nextState.lastActiveDateKey
            prefs[Keys.GARAGE_EARNED_TOKENS] = updatedTokens
            if (tokenDelta > 0) {
                prefs[Keys.GARAGE_LAST_TOKEN_DATE_KEY] = nextState.lastActiveDateKey
            }
        }
        return nextState
    }

    suspend fun unlockCarUpgrade(carType: RcCarType, slot: CosmeticUpgradeSlot): Boolean {
        var didUnlock = false
        dataStore.edit { prefs ->
            val streakCurrent = prefs[Keys.STREAK_CURRENT_DAYS] ?: 0
            val streakBest = prefs[Keys.STREAK_BEST_DAYS] ?: 0
            val decodedVisuals = CarVisualCodec.decode(prefs[Keys.CAR_UPGRADES_JSON])
            val map = CarVisualCodec.normalizeMap(decodedVisuals.upgradesByCarId).toMutableMap()
            val minUnlockedTokens = map.values.sumOf { it.unlockedCount }
            val earnedTokens = maxOf(
                prefs[Keys.GARAGE_EARNED_TOKENS]
                    ?: PlayStreakCalculator.backfillInitialEarnedTokens(streakCurrent, streakBest),
                decodedVisuals.earnedTokens,
                minUnlockedTokens
            )
            val existing = map[carType.storageId] ?: CarVisualUpgrades()
            if (existing.isUnlocked(slot)) {
                didUnlock = false
                return@edit
            }
            val available = (earnedTokens - minUnlockedTokens).coerceAtLeast(0)
            if (available <= 0) {
                didUnlock = false
                return@edit
            }
            map[carType.storageId] = existing.withUnlockedAndEquipped(slot)
            prefs[Keys.GARAGE_EARNED_TOKENS] = earnedTokens
            prefs[Keys.CAR_UPGRADES_JSON] = CarVisualCodec.encode(earnedTokens, map)
            didUnlock = true
        }
        return didUnlock
    }

    suspend fun toggleCarUpgrade(carType: RcCarType, slot: CosmeticUpgradeSlot, equipped: Boolean) {
        dataStore.edit { prefs ->
            val streakCurrent = prefs[Keys.STREAK_CURRENT_DAYS] ?: 0
            val streakBest = prefs[Keys.STREAK_BEST_DAYS] ?: 0
            val decodedVisuals = CarVisualCodec.decode(prefs[Keys.CAR_UPGRADES_JSON])
            val map = CarVisualCodec.normalizeMap(decodedVisuals.upgradesByCarId).toMutableMap()
            val existing = map[carType.storageId] ?: CarVisualUpgrades()
            if (!existing.isUnlocked(slot)) return@edit
            val updated = existing.withEquipped(slot, equipped)
            if (updated == existing) return@edit
            map[carType.storageId] = updated
            val minUnlockedTokens = map.values.sumOf { it.unlockedCount }
            val earnedTokens = maxOf(
                prefs[Keys.GARAGE_EARNED_TOKENS]
                    ?: PlayStreakCalculator.backfillInitialEarnedTokens(streakCurrent, streakBest),
                decodedVisuals.earnedTokens,
                minUnlockedTokens
            )
            prefs[Keys.GARAGE_EARNED_TOKENS] = earnedTokens
            prefs[Keys.CAR_UPGRADES_JSON] = CarVisualCodec.encode(earnedTokens, map)
        }
    }

    suspend fun mergeCloudGarageState(
        cloudEarnedTokens: Int,
        cloudUpgradesByCarId: Map<String, CarVisualUpgrades>
    ) {
        if (cloudEarnedTokens <= 0 && cloudUpgradesByCarId.isEmpty()) return
        dataStore.edit { prefs ->
            val streakCurrent = prefs[Keys.STREAK_CURRENT_DAYS] ?: 0
            val streakBest = prefs[Keys.STREAK_BEST_DAYS] ?: 0
            val decodedVisuals = CarVisualCodec.decode(prefs[Keys.CAR_UPGRADES_JSON])
            val localMap = CarVisualCodec.normalizeMap(decodedVisuals.upgradesByCarId)
            val localEarned = maxOf(
                prefs[Keys.GARAGE_EARNED_TOKENS]
                    ?: PlayStreakCalculator.backfillInitialEarnedTokens(streakCurrent, streakBest),
                decodedVisuals.earnedTokens,
                localMap.values.sumOf { it.unlockedCount }
            )
            val merged = CarVisualCodec.mergeGarageStates(
                localEarnedTokens = localEarned,
                localUpgrades = localMap,
                cloudEarnedTokens = cloudEarnedTokens,
                cloudUpgrades = cloudUpgradesByCarId
            )
            if (merged.earnedTokens > localEarned || merged.upgradesByCarId != localMap) {
                prefs[Keys.GARAGE_EARNED_TOKENS] = merged.earnedTokens
                prefs[Keys.CAR_UPGRADES_JSON] = CarVisualCodec.encode(merged.earnedTokens, merged.upgradesByCarId)
            }
        }
    }

    suspend fun ensurePlayerId(): String {
        var resolved = ""
        dataStore.edit { prefs ->
            val existing = prefs[Keys.PLAYER_ID]
            if (existing != null) {
                resolved = existing
            } else {
                resolved = generateStableId()
                prefs[Keys.PLAYER_ID] = resolved
            }
        }
        return resolved
    }

    suspend fun updateDriverName(name: String) {
        val clean = name.trim().take(16).ifEmpty { "Racer" }
        dataStore.edit { it[Keys.DRIVER_NAME] = clean }
    }

    suspend fun updateCarAndColors(carType: RcCarType, primaryArgb: Long, accentArgb: Long) {
        dataStore.edit {
            it[Keys.CAR_TYPE] = carType.name
            it[Keys.PRIMARY_COLOR] = primaryArgb
            it[Keys.ACCENT_COLOR] = accentArgb
        }
    }

    suspend fun applyCarLook(
        carType: RcCarType,
        primaryArgb: Long,
        accentArgb: Long,
        equippedMask: Int
    ) {
        dataStore.edit { prefs ->
            prefs[Keys.CAR_TYPE] = carType.name
            prefs[Keys.PRIMARY_COLOR] = primaryArgb
            prefs[Keys.ACCENT_COLOR] = accentArgb

            val streakCurrent = prefs[Keys.STREAK_CURRENT_DAYS] ?: 0
            val streakBest = prefs[Keys.STREAK_BEST_DAYS] ?: 0
            val decodedVisuals = CarVisualCodec.decode(prefs[Keys.CAR_UPGRADES_JSON])
            val map = CarVisualCodec.normalizeMap(decodedVisuals.upgradesByCarId).toMutableMap()
            val existing = map[carType.storageId] ?: CarVisualUpgrades()
            val cleanUnlocked = existing.sanitizedUnlockedMask
            val cleanEquipped = CarVisualUpgrades.sanitizeMask(equippedMask) and cleanUnlocked
            map[carType.storageId] = CarVisualUpgrades(
                unlockedMask = cleanUnlocked,
                equippedMask = cleanEquipped
            )
            val minUnlockedTokens = map.values.sumOf { it.unlockedCount }
            val earnedTokens = maxOf(
                prefs[Keys.GARAGE_EARNED_TOKENS]
                    ?: PlayStreakCalculator.backfillInitialEarnedTokens(streakCurrent, streakBest),
                decodedVisuals.earnedTokens,
                minUnlockedTokens
            )
            prefs[Keys.GARAGE_EARNED_TOKENS] = earnedTokens
            prefs[Keys.CAR_UPGRADES_JSON] = CarVisualCodec.encode(earnedTokens, map)
        }
    }

    suspend fun updateControlSettings(
        controlMode: ControlMode,
        tiltSensitivity: Float,
        autoGasInTilt: Boolean,
        sliderSensitivity: Float = 1.45f
    ) {
        dataStore.edit {
            it[Keys.CONTROL_MODE] = controlMode.name
            it[Keys.TILT_SENSITIVITY] = tiltSensitivity.coerceIn(0.6f, 2.2f)
            it[Keys.AUTO_GAS_TILT] = true
            it[Keys.SLIDER_SENSITIVITY] = sliderSensitivity.coerceIn(0.7f, 2.6f)
        }
    }

    suspend fun updateSwapControls(enabled: Boolean) {
        dataStore.edit { it[Keys.SWAP_CONTROLS] = enabled }
    }

    suspend fun updateShowLiveLapDelta(enabled: Boolean) {
        dataStore.edit { it[Keys.SHOW_LIVE_LAP_DELTA] = enabled }
    }

    suspend fun updateShowTrackMiniMap(enabled: Boolean) {
        dataStore.edit { it[Keys.SHOW_TRACK_MINI_MAP] = enabled }
    }

    suspend fun updateRaceRules(rules: RaceRulesSettings) {
        dataStore.edit {
            it[Keys.RULE_GAME_MODE] = rules.gameMode
            it[Keys.RULE_AUTO_ACCEL] = true
            it[Keys.RULE_HARD_BOUNDARY] = true
            it[Keys.RULE_RANDOM_JUMPS] = true
            it[Keys.RULE_RANDOM_CONDITIONS] = true
            it[Keys.RULE_SHOW_PLAYER_NAMES] = rules.showPlayerNames
            it[Keys.RULE_WIN_CONDITION] = rules.winCondition
            it[Keys.RULE_TOTAL_LAPS] = rules.totalLaps.coerceIn(1, 9)
            it[Keys.RULE_TIME_LIMIT_SEC] = rules.timeLimitSec.coerceIn(30, 600)
            it[Keys.RULE_ARENA_TARGET_MIN] = rules.arenaTargetMinutes.coerceIn(1, 10)
            it[Keys.RULE_CAR_SPEED] = rules.carSpeedEnum.name
            it[Keys.RULE_HYBRID_CIRCUIT] = rules.hybridIncludeCircuit
            it[Keys.RULE_HYBRID_ARENA] = rules.hybridIncludeArena
            it[Keys.RULE_HYBRID_STUNTS] = rules.hybridIncludeStunts
            it[Keys.RULE_HYBRID_RANDOM_ORDER] = rules.hybridRandomOrder
            it[Keys.RULE_HYBRID_STUNT_ROUNDS] = rules.hybridStuntRounds.coerceIn(1, 4)
        }
    }

    suspend fun updateAudioSettings(audio: AudioSettings) {
        dataStore.edit {
            it[Keys.AUDIO_MUSIC_ENABLED] = audio.musicEnabled
            it[Keys.AUDIO_SFX_ENABLED] = audio.carSfxEnabled
            it[Keys.AUDIO_HAPTICS_ENABLED] = audio.hapticsEnabled
            it[Keys.AUDIO_MUSIC_VOLUME] = audio.musicVolume.coerceIn(0f, 1f)
            it[Keys.AUDIO_SFX_VOLUME] = audio.sfxVolume.coerceIn(0f, 1f)
        }
    }

    suspend fun updateThemeSettings(settings: ThemeSettings) {
        dataStore.edit {
            it[Keys.THEME_MODE] = settings.mode.name
            it[Keys.DYNAMIC_COLOR_ENABLED] = settings.dynamicColor
        }
    }

    suspend fun saveCustomTrack(track: CustomTrackData) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.CUSTOM_TRACKS_JSON]
                ?.let { runCatching { customTrackListAdapter.fromJson(it) }.getOrNull() }
                .orEmpty()
                .toMutableList()
            val idx = current.indexOfFirst { it.id == track.id }
            if (idx >= 0) {
                current[idx] = track
            } else {
                current.add(0, track)
            }
            prefs[Keys.CUSTOM_TRACKS_JSON] = customTrackListAdapter.toJson(current.take(12))
        }
    }

    suspend fun deleteCustomTrack(trackId: String) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.CUSTOM_TRACKS_JSON]
                ?.let { runCatching { customTrackListAdapter.fromJson(it) }.getOrNull() }
                .orEmpty()
                .filterNot { it.id == trackId }
            prefs[Keys.CUSTOM_TRACKS_JSON] = customTrackListAdapter.toJson(current)
        }
    }

    suspend fun recordSoloRun(
        profile: PlayerProfile,
        boardKey: String,
        gameMode: GameMode,
        trackKey: String,
        totalLaps: Int,
        finishTimeMs: Long,
        bestLapMs: Long,
        bestScore: Int,
        encodedGhostFrames: String
    ): Boolean {
        var improvedPrimary = false
        dataStore.edit { prefs ->
            val current = prefs[Keys.LOCAL_LEADERBOARD_JSON]
                ?.let { runCatching { localLeaderboardListAdapter.fromJson(it) }.getOrNull() }
                .orEmpty()
                .toMutableList()

            val existingIdx = current.indexOfFirst { it.boardKey == boardKey }
            val existing = current.getOrNull(existingIdx)
            val isStunts = gameMode == GameMode.STUNTS

            improvedPrimary = if (existing == null) {
                true
            } else if (isStunts) {
                bestScore > existing.bestScore
            } else {
                finishTimeMs > 0L && (existing.bestRaceTimeMs <= 0L || finishTimeMs < existing.bestRaceTimeMs)
            }

            val improvedLap = existing != null && bestLapMs > 0L &&
                (existing.bestLapTimeMs <= 0L || bestLapMs < existing.bestLapTimeMs)

            if (improvedPrimary || improvedLap) {
                val mergedRaceMs = when {
                    existing == null -> finishTimeMs.coerceAtLeast(0L)
                    finishTimeMs <= 0L -> existing.bestRaceTimeMs
                    existing.bestRaceTimeMs <= 0L -> finishTimeMs
                    else -> minOf(existing.bestRaceTimeMs, finishTimeMs)
                }
                val mergedLapMs = when {
                    existing == null -> bestLapMs.coerceAtLeast(0L)
                    bestLapMs <= 0L -> existing.bestLapTimeMs
                    existing.bestLapTimeMs <= 0L -> bestLapMs
                    else -> minOf(existing.bestLapTimeMs, bestLapMs)
                }
                val mergedScore = maxOf(existing?.bestScore ?: 0, bestScore)
                val mergedGhost = if (improvedPrimary && encodedGhostFrames.isNotBlank()) {
                    encodedGhostFrames
                } else {
                    existing?.ghostFramesData.orEmpty()
                }
                val updated = LocalLeaderboardRecord(
                    boardKey = boardKey,
                    gameMode = gameMode.name,
                    trackKey = trackKey,
                    totalLaps = totalLaps,
                    driverName = profile.name,
                    carType = profile.carType.name,
                    primaryColorArgb = profile.primaryColorArgb,
                    accentColorArgb = profile.accentColorArgb,
                    equippedUpgradesMask = profile.equippedUpgradesMask,
                    bestRaceTimeMs = mergedRaceMs,
                    bestLapTimeMs = mergedLapMs,
                    bestScore = mergedScore,
                    recordedAtMs = System.currentTimeMillis(),
                    ghostFramesData = mergedGhost
                )
                if (existingIdx >= 0) {
                    current[existingIdx] = updated
                } else {
                    current.add(0, updated)
                }
                prefs[Keys.LOCAL_LEADERBOARD_JSON] = localLeaderboardListAdapter.toJson(current.take(40))
            }
        }
        return improvedPrimary
    }

    private fun generateStableId(): String =
        "rc_" + UUID.randomUUID().toString().replace("-", "").take(10)
}

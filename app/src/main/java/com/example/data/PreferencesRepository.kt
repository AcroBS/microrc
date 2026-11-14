package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.ControlMode
import com.example.model.PlayerProfile
import com.example.model.RcCarType
import com.example.model.RcColorPresets
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "microrc_prefs")

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val PLAYER_ID = stringPreferencesKey("player_id")
        val DRIVER_NAME = stringPreferencesKey("driver_name")
        val CAR_TYPE = stringPreferencesKey("car_type")
        val PRIMARY_COLOR = longPreferencesKey("primary_color")
        val ACCENT_COLOR = longPreferencesKey("accent_color")
        val CONTROL_MODE = stringPreferencesKey("control_mode")
        val TILT_SENSITIVITY = floatPreferencesKey("tilt_sensitivity")
        val AUTO_GAS_TILT = booleanPreferencesKey("auto_gas_tilt")
    }

    val profileFlow: Flow<PlayerProfile> = context.dataStore.data.map { prefs ->
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
        val autoGas = prefs[Keys.AUTO_GAS_TILT] ?: false

        PlayerProfile(
            playerId = id,
            name = name,
            carType = carType,
            primaryColorArgb = primary,
            accentColorArgb = accent,
            controlMode = controlMode,
            tiltSensitivity = sensitivity,
            autoGasInTilt = autoGas
        )
    }

    suspend fun ensurePlayerId(): String {
        var resolved = ""
        context.dataStore.edit { prefs ->
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
        context.dataStore.edit { it[Keys.DRIVER_NAME] = clean }
    }

    suspend fun updateCarAndColors(carType: RcCarType, primaryArgb: Long, accentArgb: Long) {
        context.dataStore.edit {
            it[Keys.CAR_TYPE] = carType.name
            it[Keys.PRIMARY_COLOR] = primaryArgb
            it[Keys.ACCENT_COLOR] = accentArgb
        }
    }

    suspend fun updateControlSettings(
        controlMode: ControlMode,
        tiltSensitivity: Float,
        autoGasInTilt: Boolean
    ) {
        context.dataStore.edit {
            it[Keys.CONTROL_MODE] = controlMode.name
            it[Keys.TILT_SENSITIVITY] = tiltSensitivity.coerceIn(0.6f, 2.2f)
            it[Keys.AUTO_GAS_TILT] = autoGasInTilt
        }
    }

    private fun generateStableId(): String =
        "rc_" + UUID.randomUUID().toString().replace("-", "").take(10)
}

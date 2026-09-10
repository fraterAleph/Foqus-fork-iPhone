package app.foqos.android.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("foqos_settings")

/** App-wide settings that are not tied to a single profile. */
class SettingsStore(private val context: Context) {

    data class Settings(
        val hasCompletedIntro: Boolean = false,
        val emergencyUnblocksRemaining: Int = DEFAULT_EMERGENCY_UNBLOCKS,
        val emergencyResetPeriodWeeks: Int = DEFAULT_RESET_WEEKS,
        val lastEmergencyResetAt: Long = 0,
        val keepShieldOnReboot: Boolean = true,
    )

    val settings: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            hasCompletedIntro = prefs[KEY_INTRO] ?: false,
            emergencyUnblocksRemaining = prefs[KEY_EMERGENCY_LEFT] ?: DEFAULT_EMERGENCY_UNBLOCKS,
            emergencyResetPeriodWeeks = prefs[KEY_EMERGENCY_WEEKS] ?: DEFAULT_RESET_WEEKS,
            lastEmergencyResetAt = prefs[KEY_EMERGENCY_RESET_AT] ?: 0,
            keepShieldOnReboot = prefs[KEY_KEEP_ON_REBOOT] ?: true,
        )
    }

    suspend fun setIntroCompleted(completed: Boolean) {
        context.dataStore.edit { it[KEY_INTRO] = completed }
    }

    suspend fun setKeepShieldOnReboot(enabled: Boolean) {
        context.dataStore.edit { it[KEY_KEEP_ON_REBOOT] = enabled }
    }

    suspend fun setEmergencyResetPeriodWeeks(weeks: Int) {
        context.dataStore.edit { it[KEY_EMERGENCY_WEEKS] = weeks.coerceIn(1, 52) }
    }

    /**
     * Refills the emergency allowance once the reset period has passed, then reports how many
     * unblocks are left.
     */
    suspend fun refreshEmergencyAllowance(now: Long = System.currentTimeMillis()): Int {
        var remaining = DEFAULT_EMERGENCY_UNBLOCKS
        context.dataStore.edit { prefs ->
            val weeks = prefs[KEY_EMERGENCY_WEEKS] ?: DEFAULT_RESET_WEEKS
            val lastReset = prefs[KEY_EMERGENCY_RESET_AT] ?: 0
            val period = TimeUnit.DAYS.toMillis(7L * weeks)

            if (lastReset == 0L || now - lastReset >= period) {
                prefs[KEY_EMERGENCY_LEFT] = DEFAULT_EMERGENCY_UNBLOCKS
                prefs[KEY_EMERGENCY_RESET_AT] = now
            }
            remaining = prefs[KEY_EMERGENCY_LEFT] ?: DEFAULT_EMERGENCY_UNBLOCKS
        }
        return remaining
    }

    /** Spends one emergency unblock. Returns false when the allowance is exhausted. */
    suspend fun consumeEmergencyUnblock(): Boolean {
        var consumed = false
        context.dataStore.edit { prefs ->
            val remaining = prefs[KEY_EMERGENCY_LEFT] ?: DEFAULT_EMERGENCY_UNBLOCKS
            if (remaining > 0) {
                prefs[KEY_EMERGENCY_LEFT] = remaining - 1
                consumed = true
            }
        }
        return consumed
    }

    companion object {
        const val DEFAULT_EMERGENCY_UNBLOCKS = 3
        const val DEFAULT_RESET_WEEKS = 4

        private val KEY_INTRO = booleanPreferencesKey("has_completed_intro")
        private val KEY_EMERGENCY_LEFT = intPreferencesKey("emergency_unblocks_remaining")
        private val KEY_EMERGENCY_WEEKS = intPreferencesKey("emergency_reset_weeks")
        private val KEY_EMERGENCY_RESET_AT = longPreferencesKey("emergency_last_reset_at")
        private val KEY_KEEP_ON_REBOOT = booleanPreferencesKey("keep_shield_on_reboot")
    }
}

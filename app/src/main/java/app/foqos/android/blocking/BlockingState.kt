package app.foqos.android.blocking

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the blocker should be enforcing right now.
 *
 * The accessibility service and the app run in the same process, but that process can be killed
 * and restarted with only the service alive, so the snapshot is mirrored into SharedPreferences
 * and reloaded lazily. Everything the service needs for a decision has to be in here — it must
 * never touch the database on the main thread while a window transition is in flight.
 */
object BlockingState {

    data class Snapshot(
        val active: Boolean = false,
        val profileId: String = "",
        val profileName: String = "",
        val packages: Set<String> = emptySet(),
        /** Treat [packages] as the only allowed apps instead of the blocked ones. */
        val allowMode: Boolean = false,
        val strict: Boolean = false,
        /** A break, a pause or a temporary-access grant is open: let everything through. */
        val suspended: Boolean = false,
        /** Apps a temporary-access grant lets through while the session stays otherwise blocked. */
        val temporarilyAllowed: Set<String> = emptySet(),
    ) {
        fun shouldShield(packageName: String): Boolean {
            if (!active || suspended) return false
            if (packageName in temporarilyAllowed) return false
            return if (allowMode) packageName !in packages else packageName in packages
        }
    }

    private const val PREFS = "foqos_blocking_state"
    private const val KEY_ACTIVE = "active"
    private const val KEY_PROFILE_ID = "profile_id"
    private const val KEY_PROFILE_NAME = "profile_name"
    private const val KEY_PACKAGES = "packages"
    private const val KEY_ALLOW_MODE = "allow_mode"
    private const val KEY_STRICT = "strict"
    private const val KEY_SUSPENDED = "suspended"
    private const val KEY_TEMP_ALLOWED = "temp_allowed"

    private val _snapshot = MutableStateFlow(Snapshot())
    val snapshot: StateFlow<Snapshot> = _snapshot.asStateFlow()

    @Volatile
    private var loaded = false

    fun current(context: Context): Snapshot {
        if (!loaded) {
            synchronized(this) {
                if (!loaded) {
                    _snapshot.value = read(context)
                    loaded = true
                }
            }
        }
        return _snapshot.value
    }

    fun update(context: Context, snapshot: Snapshot) {
        _snapshot.value = snapshot
        loaded = true
        write(context, snapshot)
    }

    fun clear(context: Context) = update(context, Snapshot())

    private fun read(context: Context): Snapshot {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Snapshot(
            active = prefs.getBoolean(KEY_ACTIVE, false),
            profileId = prefs.getString(KEY_PROFILE_ID, "").orEmpty(),
            profileName = prefs.getString(KEY_PROFILE_NAME, "").orEmpty(),
            packages = prefs.getStringSet(KEY_PACKAGES, emptySet()).orEmpty().toSet(),
            allowMode = prefs.getBoolean(KEY_ALLOW_MODE, false),
            strict = prefs.getBoolean(KEY_STRICT, false),
            suspended = prefs.getBoolean(KEY_SUSPENDED, false),
            temporarilyAllowed = prefs.getStringSet(KEY_TEMP_ALLOWED, emptySet()).orEmpty().toSet(),
        )
    }

    private fun write(context: Context, snapshot: Snapshot) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ACTIVE, snapshot.active)
            .putString(KEY_PROFILE_ID, snapshot.profileId)
            .putString(KEY_PROFILE_NAME, snapshot.profileName)
            .putStringSet(KEY_PACKAGES, snapshot.packages)
            .putBoolean(KEY_ALLOW_MODE, snapshot.allowMode)
            .putBoolean(KEY_STRICT, snapshot.strict)
            .putBoolean(KEY_SUSPENDED, snapshot.suspended)
            .putStringSet(KEY_TEMP_ALLOWED, snapshot.temporarilyAllowed)
            .apply()
    }
}

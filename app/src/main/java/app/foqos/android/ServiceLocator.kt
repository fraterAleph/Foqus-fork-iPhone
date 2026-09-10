package app.foqos.android

import android.content.Context
import app.foqos.android.blocking.BlockingEngine
import app.foqos.android.data.db.FoqosDatabase
import app.foqos.android.data.prefs.SettingsStore
import app.foqos.android.data.repo.ProfileRepository
import app.foqos.android.data.repo.SessionRepository
import app.foqos.android.session.SessionController

/**
 * Hand-rolled dependency graph. The app is small enough that a DI framework would cost more in
 * build time and indirection than it saves, and services, receivers and composables all need the
 * same singletons from very different entry points.
 */
object ServiceLocator {

    @Volatile private var database: FoqosDatabase? = null
    @Volatile private var profiles: ProfileRepository? = null
    @Volatile private var sessions: SessionRepository? = null
    @Volatile private var engine: BlockingEngine? = null
    @Volatile private var settings: SettingsStore? = null
    @Volatile private var controller: SessionController? = null

    fun database(context: Context): FoqosDatabase = database ?: synchronized(this) {
        database ?: FoqosDatabase.get(context).also { database = it }
    }

    fun profileRepository(context: Context): ProfileRepository = profiles ?: synchronized(this) {
        profiles ?: ProfileRepository(database(context).profileDao()).also { profiles = it }
    }

    fun sessionRepository(context: Context): SessionRepository = sessions ?: synchronized(this) {
        sessions ?: SessionRepository(database(context).sessionDao()).also { sessions = it }
    }

    fun blockingEngine(context: Context): BlockingEngine = engine ?: synchronized(this) {
        engine ?: BlockingEngine(context.applicationContext).also { engine = it }
    }

    fun settingsStore(context: Context): SettingsStore = settings ?: synchronized(this) {
        settings ?: SettingsStore(context.applicationContext).also { settings = it }
    }

    fun sessionController(context: Context): SessionController = controller ?: synchronized(this) {
        controller ?: SessionController(
            context = context.applicationContext,
            profiles = profileRepository(context),
            sessions = sessionRepository(context),
            engine = blockingEngine(context),
            settings = settingsStore(context),
        ).also { controller = it }
    }
}

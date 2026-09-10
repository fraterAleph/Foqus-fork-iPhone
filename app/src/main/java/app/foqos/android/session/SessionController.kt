package app.foqos.android.session

import android.content.Context
import app.foqos.android.blocking.BlockingEngine
import app.foqos.android.data.db.ProfileEntity
import app.foqos.android.data.db.SessionEntity
import app.foqos.android.data.db.SessionWithProfile
import app.foqos.android.data.model.StrategyData
import app.foqos.android.data.prefs.SettingsStore
import app.foqos.android.data.repo.ProfileRepository
import app.foqos.android.data.repo.SessionRepository
import app.foqos.android.strategy.BlockingStrategy
import app.foqos.android.strategy.Strategies
import app.foqos.android.util.DeepLinks
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Every start, stop, break, pause and temporary-access decision goes through here, so the rules
 * a strategy implies live in exactly one place. Ported from the iOS `StrategyManager`, minus the
 * per-strategy classes: the strategy is data, this is the behaviour.
 */
class SessionController(
    private val context: Context,
    private val profiles: ProfileRepository,
    private val sessions: SessionRepository,
    private val engine: BlockingEngine,
    private val settings: SettingsStore,
) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()

    val activeSession: Flow<SessionWithProfile?> = sessions.observeActive()

    fun strategyDataOf(profile: ProfileEntity): StrategyData =
        profile.strategyData
            ?.let { runCatching { json.decodeFromString<StrategyData>(it) }.getOrNull() }
            ?: StrategyData()

    fun encodeStrategyData(data: StrategyData): String = json.encodeToString(data)

    // ---------------------------------------------------------------- start / stop

    suspend fun start(
        profileId: String,
        tokens: List<String>,
        source: TokenSource,
        force: Boolean = false,
    ): SessionResult = mutex.withLock {
        val running = sessions.getActive()
        if (running != null) {
            return@withLock SessionResult.Rejected(
                "\"${running.profile.name}\" is already running. Stop it before starting another."
            )
        }

        val profile = profiles.get(profileId)
            ?: return@withLock SessionResult.Rejected("That profile no longer exists.")

        if (profile.blockedPackages.isEmpty() && profile.domains.isEmpty()) {
            return@withLock SessionResult.Rejected(
                "\"${profile.name}\" has nothing selected to block yet."
            )
        }

        // Starting an NFC-only profile with no registered tag would produce a session with no
        // way out at all, so it is refused rather than allowed and regretted.
        if (profile.nfcOnlyUnlock &&
            UnlockRules.hardNfcKeys(profile.physicalUnblockItems).isEmpty()
        ) {
            return@withLock SessionResult.Rejected(
                "\"${profile.name}\" unlocks only with its NFC tag, and no tag is linked yet. " +
                    "Open the profile's Tag / QR screen and hold a tag to the phone to link one."
            )
        }

        val strategy = Strategies.byId(profile.strategyId)
        val data = strategyDataOf(profile)
        val now = System.currentTimeMillis()

        val session = SessionEntity(
            profileId = profile.id,
            tag = sessionTag(tokens),
            startTime = now,
            expectedEndTime = if (strategy.hasTimer) now + data.timerSeconds * 1000 else null,
            forceStarted = force,
        )

        sessions.save(session)
        engine.activate(profile)
        FocusSessionService.start(context)
        SessionAlarmScheduler.scheduleNextWake(context, session)

        SessionResult.Started(profile.name)
    }

    /**
     * Stop request. For pause-mode strategies the first token stops nothing and opens a pause
     * instead; a second token while that pause runs ends the session, which is how the iOS
     * pause-timer strategies behave.
     */
    suspend fun stop(tokens: List<String>, source: TokenSource): SessionResult = mutex.withLock {
        val active = sessions.getActive()
            ?: return@withLock SessionResult.Rejected("No session is running.")

        val profile = active.profile
        val strategy = Strategies.byId(profile.strategyId)
        val session = active.session
        val expired = SessionTimeCalculator.isTimerExpired(session)

        if (!expired) {
            val rejection = rejectionFor(profile, strategy, session, tokens, source)
            if (rejection != null) return@withLock SessionResult.Rejected(rejection)
        }

        if (strategy.hasPauseMode && !expired && source != TokenSource.MANUAL) {
            if (!session.isPauseActive) {
                return@withLock beginPause(active)
            }
        }

        endSession(session, profile)
        SessionResult.Stopped(profile.name)
    }

    /**
     * Entry point for a scanned NFC tag or QR code, and for a `foqos.app/profile/<id>` link.
     * Starts the matching profile when nothing runs, and stops the running one when the token
     * is allowed to.
     */
    suspend fun handleToken(rawValues: List<String>, source: TokenSource): SessionResult {
        val tokens = rawValues.map { it.trim() }.filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return SessionResult.Rejected("Nothing readable on that tag.")

        val active = sessions.getActive()
        if (active != null) return stop(tokens, source)

        // A profile that claims one of these tokens wins over the link the token may carry, so
        // a tag registered as a key starts its own profile even when it also holds a link.
        val owner = profiles.getAll().firstOrNull { profile ->
            profile.physicalUnblockItems.any { item ->
                tokens.any { UnlockRules.tokensMatch(item.value, it) }
            }
        }
        if (owner != null) return start(owner.id, tokens, source)

        val linkedProfileId = tokens.firstNotNullOfOrNull { DeepLinks.profileIdFrom(it) }
            ?: return SessionResult.Rejected(
                "That tag is not linked to a profile yet. Open a profile's Tag / QR screen and " +
                    "hold the tag to the phone to link it."
            )

        return start(linkedProfileId, tokens, source)
    }

    /**
     * What to record as the token that started a session. A tag can present both an NDEF link
     * and its UID; the UID is the one that cannot be reproduced from a photograph, so it is the
     * one worth remembering.
     */
    private fun sessionTag(tokens: List<String>): String =
        tokens.firstOrNull { !UnlockRules.isClonableLink(it) }
            ?: tokens.firstOrNull()
            ?: ""

    private fun rejectionFor(
        profile: ProfileEntity,
        strategy: BlockingStrategy,
        session: SessionEntity,
        tokens: List<String>,
        source: TokenSource,
    ): String? {
        val unblockItems = profile.physicalUnblockItems

        // A profile with registered unlock items answers to those items and to nothing else —
        // not the in-app button, not a link opened from a browser, and not a code of the wrong
        // kind. This is the rule NFC-only mode rests on.
        if (unblockItems.isNotEmpty()) {
            if (UnlockRules.matches(unblockItems, tokens, source)) return null

            return when {
                source == TokenSource.MANUAL && profile.nfcOnlyUnlock ->
                    "\"${profile.name}\" ends only when you scan its NFC tag."

                source == TokenSource.MANUAL ->
                    "\"${profile.name}\" only stops with one of its saved tags or codes."

                source == TokenSource.DEEP_LINK ->
                    "A link cannot stop \"${profile.name}\". Scan its tag."

                profile.nfcOnlyUnlock && source != TokenSource.NFC ->
                    "\"${profile.name}\" ends only with an NFC tag."

                else -> "That tag or code cannot stop \"${profile.name}\"."
            }
        }

        if (profile.nfcOnlyUnlock) {
            // start() refuses to open a keyless NFC-only session and a profile cannot be edited
            // while it runs, so this is defensive. Emergency unblock covers it, see below.
            return "\"${profile.name}\" has no NFC tag linked. Use emergency unblock to end it."
        }

        if (source == TokenSource.MANUAL) {
            if (strategy.allowsManualStop) return null
            return when {
                strategy.usesNfc -> "Scan your NFC tag to stop \"${profile.name}\"."
                strategy.usesQr -> "Scan your QR code to stop \"${profile.name}\"."
                else -> "\"${profile.name}\" cannot be stopped from the app."
            }
        }

        if (strategy.requiresSameCodeToStop &&
            tokens.isNotEmpty() &&
            tokens.none { UnlockRules.tokensMatch(session.tag, it) }
        ) {
            return "Scan the same tag or code that started this session."
        }

        if (profile.strictModeActive && tokens.isEmpty()) {
            return "Strict mode is on: \"${profile.name}\" needs a tag or code to stop."
        }

        return null
    }

    private suspend fun endSession(session: SessionEntity, profile: ProfileEntity) {
        val now = System.currentTimeMillis()
        sessions.save(session.copy(endTime = now, breakEndTime = session.breakEndTime ?: session.breakStartTime?.let { now }))
        engine.deactivate()
        SessionAlarmScheduler.cancel(context)
        FocusSessionService.stop(context)
    }

    // ---------------------------------------------------------------- breaks

    suspend fun startBreak(): SessionResult = mutex.withLock {
        val active = sessions.getActive() ?: return@withLock SessionResult.Rejected("No session is running.")
        val profile = active.profile
        val strategy = Strategies.byId(profile.strategyId)
        val session = active.session

        if (!profile.breaksAllowed || !strategy.allowsTimedBreaks) {
            return@withLock SessionResult.Rejected(
                if (profile.nfcOnlyUnlock) {
                    "\"${profile.name}\" unlocks only with its NFC tag, so it has no breaks."
                } else {
                    "Breaks are turned off for \"${profile.name}\"."
                }
            )
        }
        if (session.isBreakActive) return@withLock SessionResult.Info("A break is already running.")

        val remaining = SessionTimeCalculator.remainingBreakMs(
            session, profile.breakTimeInMinutes, profile.allowMultipleBreaks
        )
        if (remaining <= 0) {
            return@withLock SessionResult.Rejected("The break allowance for this session is used up.")
        }
        if (!profile.allowMultipleBreaks && session.breakEndTime != null) {
            return@withLock SessionResult.Rejected("This profile allows one break per session.")
        }

        val now = System.currentTimeMillis()
        val updated = session.copy(breakStartTime = now, breakEndTime = null)
        sessions.save(updated)
        engine.setSuspended(true)
        SessionAlarmScheduler.scheduleNextWake(context, updated, breakDeadline = now + remaining)
        SessionResult.Info("Break started, ${SessionTimeCalculator.formatCompact(remaining)} left.")
    }

    suspend fun endBreak(): SessionResult = mutex.withLock {
        val active = sessions.getActive() ?: return@withLock SessionResult.Rejected("No session is running.")
        val session = active.session
        if (!session.isBreakActive) return@withLock SessionResult.Info("No break is running.")

        val now = System.currentTimeMillis()
        val elapsed = (now - (session.breakStartTime ?: now)).coerceAtLeast(0)
        val allowance = SessionTimeCalculator.totalBreakAllowanceMs(active.profile.breakTimeInMinutes)
        val used = if (active.profile.allowMultipleBreaks) {
            (session.usedBreakDurationMs + elapsed).coerceAtMost(allowance)
        } else {
            session.usedBreakDurationMs
        }

        val updated = session.copy(breakEndTime = now, usedBreakDurationMs = used)
        sessions.save(updated)
        engine.setSuspended(false)
        SessionAlarmScheduler.scheduleNextWake(context, updated)
        SessionResult.Resumed
    }

    // ---------------------------------------------------------------- pause

    private suspend fun beginPause(active: SessionWithProfile): SessionResult {
        val data = strategyDataOf(active.profile)
        val now = System.currentTimeMillis()
        val updated = active.session.copy(pauseStartTime = now, pauseEndTime = null)

        sessions.save(updated)
        engine.setSuspended(true)
        SessionAlarmScheduler.scheduleNextWake(
            context,
            updated,
            pauseDeadline = now + data.pauseSeconds * 1000,
        )
        return SessionResult.Paused
    }

    suspend fun resumeFromPause(): SessionResult = mutex.withLock {
        val active = sessions.getActive() ?: return@withLock SessionResult.Rejected("No session is running.")
        val session = active.session
        if (!session.isPauseActive) return@withLock SessionResult.Info("The session is not paused.")

        val updated = session.copy(pauseEndTime = System.currentTimeMillis())
        sessions.save(updated)
        engine.setSuspended(false)
        SessionAlarmScheduler.scheduleNextWake(context, updated)
        SessionResult.Resumed
    }

    // ---------------------------------------------------------------- temporary access

    suspend fun grantTemporaryAccess(): SessionResult = mutex.withLock {
        val active = sessions.getActive() ?: return@withLock SessionResult.Rejected("No session is running.")
        val profile = active.profile
        val strategy = Strategies.byId(profile.strategyId)
        if (!strategy.hasSoftUnblock) {
            return@withLock SessionResult.Rejected("\"${profile.name}\" does not hand out temporary access.")
        }

        val session = active.session
        val data = strategyDataOf(profile)
        if (session.usedSoftUnblocks >= data.softUnblockCount) {
            return@withLock SessionResult.Rejected("No temporary opens left in this session.")
        }
        if (session.isSoftUnblockActive) {
            return@withLock SessionResult.Info("A temporary open is already running.")
        }

        val now = System.currentTimeMillis()
        val until = now + data.softUnblockSeconds * 1000
        val updated = session.copy(
            usedSoftUnblocks = session.usedSoftUnblocks + 1,
            softUnblockUntil = until,
        )
        sessions.save(updated)

        val allowed = data.softUnblockPackages.toSet()
            .ifEmpty { profile.blockedPackages.toSet() }
        engine.setTemporarilyAllowed(allowed)
        SessionAlarmScheduler.scheduleNextWake(context, updated, grantDeadline = until)

        SessionResult.Granted(data.softUnblockCount - updated.usedSoftUnblocks)
    }

    // ---------------------------------------------------------------- emergency

    suspend fun emergencyUnblock(): SessionResult = mutex.withLock {
        val active = sessions.getActive() ?: return@withLock SessionResult.Rejected("No session is running.")
        // The one exception to NFC-only mode: a session whose key no longer exists would
        // otherwise have no ending at all. start() will not create that state, so allowing it
        // here costs nothing and removes the possibility of a truly stuck device.
        val keyless = active.profile.nfcOnlyUnlock &&
            UnlockRules.hardNfcKeys(active.profile.physicalUnblockItems).isEmpty()

        if (!active.profile.emergencyUnblockAllowed && !keyless) {
            return@withLock SessionResult.Rejected(
                if (active.profile.nfcOnlyUnlock) {
                    "\"${active.profile.name}\" ends only when you scan its NFC tag."
                } else {
                    "Emergency unblock is turned off for \"${active.profile.name}\"."
                }
            )
        }

        settings.refreshEmergencyAllowance()
        if (!settings.consumeEmergencyUnblock()) {
            return@withLock SessionResult.Rejected(
                "No emergency unblocks left. They refill on the schedule set in Settings."
            )
        }

        endSession(active.session, active.profile)
        SessionResult.Stopped(active.profile.name)
    }

    // ---------------------------------------------------------------- housekeeping

    /**
     * Closes anything whose deadline has passed. Called on every tick of the foreground service,
     * on app resume, and by the alarm receiver.
     */
    suspend fun tick(): Boolean = mutex.withLock {
        val active = sessions.getActive() ?: return@withLock false
        val profile = active.profile
        var session = active.session
        val now = System.currentTimeMillis()
        var changed = false

        if (SessionTimeCalculator.isTimerExpired(session, now)) {
            endSession(session, profile)
            return@withLock true
        }

        val data = strategyDataOf(profile)

        if (session.isPauseActive) {
            val deadline = (session.pauseStartTime ?: now) + data.pauseSeconds * 1000
            if (now >= deadline) {
                session = session.copy(pauseEndTime = deadline)
                sessions.save(session)
                engine.setSuspended(false)
                changed = true
            }
        }

        if (session.isBreakActive) {
            val remaining = SessionTimeCalculator.remainingBreakMs(
                session, profile.breakTimeInMinutes, profile.allowMultipleBreaks, now
            )
            if (remaining <= 0) {
                val allowance = SessionTimeCalculator.totalBreakAllowanceMs(profile.breakTimeInMinutes)
                session = session.copy(breakEndTime = now, usedBreakDurationMs = allowance)
                sessions.save(session)
                engine.setSuspended(false)
                changed = true
            }
        }

        val grantUntil = session.softUnblockUntil
        if (grantUntil != null && now >= grantUntil) {
            session = session.copy(softUnblockUntil = null)
            sessions.save(session)
            engine.setTemporarilyAllowed(emptySet())
            changed = true
        }

        if (changed) SessionAlarmScheduler.scheduleNextWake(context, session)
        changed
    }

    /** Re-applies blocking after a reboot or a process restart. */
    suspend fun restoreAfterRestart() {
        val active = sessions.getActive() ?: run {
            engine.deactivate()
            return
        }
        engine.activate(active.profile)
        if (active.session.isBreakActive || active.session.isPauseActive) {
            engine.setSuspended(true)
        }
        FocusSessionService.start(context)
        SessionAlarmScheduler.scheduleNextWake(context, active.session)
    }

}

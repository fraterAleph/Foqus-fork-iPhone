package app.foqos.android.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.foqos.android.ServiceLocator
import app.foqos.android.data.db.ProfileEntity
import app.foqos.android.data.db.SessionWithProfile
import app.foqos.android.data.model.PhysicalUnblockItem
import app.foqos.android.data.model.StrategyData
import app.foqos.android.data.prefs.SettingsStore
import app.foqos.android.session.SessionResult
import app.foqos.android.session.TokenSource
import app.foqos.android.util.DeepLinks
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/** What the next NFC tag held to the phone should do. */
sealed interface TagAction {
    /** Register the tag's UID as the key that unlocks this profile. */
    data class Link(val profileId: String) : TagAction

    /** Write a profile link onto the tag. */
    data class Write(val profileId: String, val url: String) : TagAction
}

class FoqosViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val profilesRepo = ServiceLocator.profileRepository(context)
    private val sessionsRepo = ServiceLocator.sessionRepository(context)
    private val controller = ServiceLocator.sessionController(context)
    private val settingsStore = ServiceLocator.settingsStore(context)
    val engine = ServiceLocator.blockingEngine(context)

    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = messageChannel.receiveAsFlow()

    val profiles: StateFlow<List<ProfileEntity>> = profilesRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeSession: StateFlow<SessionWithProfile?> = sessionsRepo.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val recentSessions: StateFlow<List<SessionWithProfile>> = sessionsRepo.observeRecent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<SettingsStore.Settings> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsStore.Settings())

    /**
     * Null until the stored value has been read. The nav graph's start destination is fixed at
     * first composition, so it must not be decided from a default that says "intro not seen".
     */
    val introCompleted: StateFlow<Boolean?> = settingsStore.settings
        .map { it.hasCompletedIntro }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // ------------------------------------------------------------------ sessions

    fun startProfile(profile: ProfileEntity) = viewModelScope.launch {
        report(
            controller.start(
                profileId = profile.id,
                tokens = listOf(DeepLinks.profileUrl(profile.id)),
                source = TokenSource.MANUAL,
            )
        )
    }

    fun stopActiveSession() = viewModelScope.launch {
        report(controller.stop(tokens = emptyList(), source = TokenSource.MANUAL))
    }

    fun onTokensScanned(tokens: List<String>, source: TokenSource) = viewModelScope.launch {
        report(controller.handleToken(tokens, source))
    }

    fun toggleBreak() = viewModelScope.launch {
        val active = activeSession.value ?: return@launch
        report(if (active.session.isBreakActive) controller.endBreak() else controller.startBreak())
    }

    fun resumeFromPause() = viewModelScope.launch { report(controller.resumeFromPause()) }

    fun grantTemporaryAccess() = viewModelScope.launch { report(controller.grantTemporaryAccess()) }

    fun emergencyUnblock() = viewModelScope.launch { report(controller.emergencyUnblock()) }

    // ------------------------------------------------------------------ profiles

    suspend fun profile(id: String): ProfileEntity? = profilesRepo.get(id)

    /** Live view of one profile, so a screen reflects a tag linked while it is open. */
    fun observeProfile(id: String): Flow<ProfileEntity?> = profilesRepo.observeById(id)

    fun decodeStrategyData(profile: ProfileEntity): StrategyData =
        controller.strategyDataOf(profile)

    fun encodeStrategyData(data: StrategyData): String = controller.encodeStrategyData(data)

    fun saveProfile(profile: ProfileEntity, onSaved: (ProfileEntity) -> Unit = {}) =
        viewModelScope.launch {
            // Editing a profile mid-session would let someone loosen the rules that are holding
            // the session shut — removing its NFC key, for one. The session has to end first.
            if (activeSession.value?.profile?.id == profile.id) {
                messageChannel.trySend("Stop the running session before editing its profile.")
                return@launch
            }
            val saved = profilesRepo.save(profile)
            messageChannel.trySend("Saved \"${saved.name}\".")
            onSaved(saved)
        }

    /**
     * Registers a tag's hardware UID as an unlock key for a profile. The UID is used rather than
     * the profile link a tag may also carry, because a link can be copied into a QR code by
     * anyone who scans the tag once, which would defeat NFC-only unlocking.
     */
    fun linkNfcTag(profileId: String, uid: String) = viewModelScope.launch {
        val profile = profilesRepo.get(profileId) ?: return@launch

        if (profile.physicalUnblockItems.any { it.value.equals(uid, ignoreCase = true) }) {
            messageChannel.trySend("That tag is already linked to \"${profile.name}\".")
            return@launch
        }

        val item = PhysicalUnblockItem(
            id = UUID.randomUUID().toString(),
            kind = PhysicalUnblockItem.Kind.NFC,
            value = uid,
            label = "Tag ${uid.takeLast(4).uppercase()}",
        )
        profilesRepo.save(profile.copy(physicalUnblockItems = profile.physicalUnblockItems + item))
        messageChannel.trySend("Tag linked. \"${profile.name}\" now unlocks with it.")
    }

    /** Removes a linked key. Refused while the profile's session is running. */
    fun unlinkKey(profileId: String, itemId: String) = viewModelScope.launch {
        if (activeSession.value?.profile?.id == profileId) {
            messageChannel.trySend("Stop the running session before removing its key.")
            return@launch
        }
        val profile = profilesRepo.get(profileId) ?: return@launch
        profilesRepo.save(
            profile.copy(physicalUnblockItems = profile.physicalUnblockItems.filterNot {
                it.id == itemId
            })
        )
        messageChannel.trySend("Key removed.")
    }

    fun deleteProfile(profile: ProfileEntity) = viewModelScope.launch {
        if (activeSession.value?.profile?.id == profile.id) {
            messageChannel.trySend("Stop the running session before deleting its profile.")
            return@launch
        }
        profilesRepo.delete(profile)
        messageChannel.trySend("Deleted \"${profile.name}\".")
    }

    fun newProfileDraft(): ProfileEntity = ProfileEntity(id = UUID.randomUUID().toString())

    // ------------------------------------------------------------------ settings

    fun completeIntro() = viewModelScope.launch { settingsStore.setIntroCompleted(true) }

    fun setEmergencyResetWeeks(weeks: Int) = viewModelScope.launch {
        settingsStore.setEmergencyResetPeriodWeeks(weeks)
    }

    fun setKeepShieldOnReboot(enabled: Boolean) = viewModelScope.launch {
        settingsStore.setKeepShieldOnReboot(enabled)
    }

    fun show(message: String) {
        messageChannel.trySend(message)
    }

    private fun report(result: SessionResult) {
        val message = when (result) {
            is SessionResult.Started -> "\"${result.profileName}\" is running."
            is SessionResult.Stopped -> "\"${result.profileName}\" stopped."
            SessionResult.Paused -> "Paused. Scan again to stop the session."
            SessionResult.Resumed -> "Blocking is back on."
            is SessionResult.Granted -> "Temporary access open. ${result.remaining} left."
            is SessionResult.Rejected -> result.message
            is SessionResult.Info -> result.message
        }
        messageChannel.trySend(message)
    }
}

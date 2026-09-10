package app.foqos.android.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.foqos.android.data.model.PhysicalUnblockItem
import app.foqos.android.data.model.ScheduleConfig
import app.foqos.android.data.model.StrategyData
import java.util.UUID

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val sortOrder: Int = 0,
    val strategyId: String = "NFCBlockingStrategy",
    /** Strategy configuration, serialized [StrategyData]. */
    val strategyData: String? = null,
    /** Package names the profile acts on. Blocklist, or allowlist when [enableAllowMode]. */
    val blockedPackages: List<String> = emptyList(),
    /** Hostnames the local DNS filter acts on. */
    val domains: List<String> = emptyList(),
    val physicalUnblockItems: List<PhysicalUnblockItem> = emptyList(),
    val schedule: ScheduleConfig? = null,

    /** Invert app selection: allow only the selected apps, block everything else. */
    val enableAllowMode: Boolean = false,
    val enableDomainBlocking: Boolean = false,
    /** Invert domain selection: resolve only the listed domains. */
    val enableAllowModeDomains: Boolean = false,
    /**
     * The out-of-the-box mode: the session ends only when a registered NFC tag is scanned.
     * While it is on it forces strict mode on and breaks and emergency unblocks off, so there
     * is no path out of a session that does not go through the tag.
     */
    val nfcOnlyUnlock: Boolean = true,

    /** Strict mode refuses every stop that is not a configured physical unblock item. */
    val enableStrictMode: Boolean = false,
    val enableBreaks: Boolean = false,
    val breakTimeInMinutes: Int = 15,
    val allowMultipleBreaks: Boolean = false,
    val askForStartSettings: Boolean = true,
    val enableEmergencyUnblock: Boolean = false,
    /** Nag notification interval while no session runs, in seconds. */
    val reminderTimeInSeconds: Int? = null,
    val customReminderMessage: String? = null,
    val colorHex: String = "#F5C542",
) {
    // Effective flags. Every caller reads these rather than the stored ones, so NFC-only mode
    // cannot be undercut by a stale toggle left behind from an earlier configuration.
    val strictModeActive: Boolean get() = nfcOnlyUnlock || enableStrictMode
    val breaksAllowed: Boolean get() = !nfcOnlyUnlock && enableBreaks
    val emergencyUnblockAllowed: Boolean get() = !nfcOnlyUnlock && enableEmergencyUnblock
}

@Entity(
    tableName = "sessions",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("profileId"), Index("endTime")],
)
data class SessionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val profileId: String,
    /** The NFC payload or QR value that started the session, or a synthetic tag for manual starts. */
    val tag: String,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    /** Wall-clock deadline for timer strategies. Null for open-ended sessions. */
    val expectedEndTime: Long? = null,

    val breakStartTime: Long? = null,
    val breakEndTime: Long? = null,
    val usedBreakDurationMs: Long = 0,

    val pauseStartTime: Long? = null,
    val pauseEndTime: Long? = null,

    /** Soft unblock grants already consumed in this session. */
    val usedSoftUnblocks: Int = 0,
    /** Deadline of a running soft unblock grant, or null when no grant is open. */
    val softUnblockUntil: Long? = null,

    val forceStarted: Boolean = false,
) {
    val isActive: Boolean get() = endTime == null
    val isBreakActive: Boolean get() = breakStartTime != null && breakEndTime == null
    val isPauseActive: Boolean get() = pauseStartTime != null && pauseEndTime == null
    val isSoftUnblockActive: Boolean
        get() = softUnblockUntil != null && softUnblockUntil > System.currentTimeMillis()
}

/** A session joined with the profile it belongs to. */
data class SessionWithProfile(
    @androidx.room.Embedded val session: SessionEntity,
    @androidx.room.Relation(parentColumn = "profileId", entityColumn = "id")
    val profile: ProfileEntity,
)

package app.foqos.android.data.model

import kotlinx.serialization.Serializable

/**
 * A physical token that is allowed to stop a profile. Mirrors `PhysicalUnblockItem` in the iOS
 * app: a profile with at least one item can only be stopped by one of those items ("strict
 * unlock"), everything else is rejected.
 */
@Serializable
data class PhysicalUnblockItem(
    val id: String,
    val kind: Kind,
    /** For NFC: the tag's payload URL or its hardware id. For QR: the decoded string. */
    val value: String,
    val label: String = "",
) {
    @Serializable
    enum class Kind { NFC, QR }
}

@Serializable
data class ScheduleConfig(
    val enabled: Boolean = false,
    /** Minutes from midnight, local time. */
    val startMinuteOfDay: Int = 9 * 60,
    val endMinuteOfDay: Int = 17 * 60,
    /** java.time.DayOfWeek values, 1 = Monday .. 7 = Sunday. */
    val days: List<Int> = listOf(1, 2, 3, 4, 5),
) {
    val isActive: Boolean get() = enabled && days.isNotEmpty()
}

/**
 * Per-strategy configuration. Every strategy reads only the fields it needs, which keeps the
 * profile row stable when a user switches strategies back and forth.
 */
@Serializable
data class StrategyData(
    /** Timer strategies: how long the session should run. */
    val timerSeconds: Long = 25 * 60,
    /** Pause-timer strategies: how long a single pause lasts. */
    val pauseSeconds: Long = 5 * 60,
    /** Soft unblock: number of temporary opens allowed per session. */
    val softUnblockCount: Int = 3,
    /** Soft unblock: how long one temporary open lasts. */
    val softUnblockSeconds: Long = 5 * 60,
    /** Soft unblock: apps that a temporary open lets through. Empty means every blocked app. */
    val softUnblockPackages: List<String> = emptyList(),
)

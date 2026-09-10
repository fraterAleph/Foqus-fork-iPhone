package app.foqos.android.util

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

/**
 * A fallback way to learn which app is in front, used when the accessibility service is off.
 *
 * It is strictly worse than the accessibility route — it can only notice an app *after* it is
 * already visible, and the poll interval decides how late that is — but it means a user who
 * refuses accessibility still gets blocking rather than nothing.
 */
class ForegroundAppWatcher(private val context: Context) {

    private var lastQueryAt = System.currentTimeMillis() - LOOKBACK_MS

    fun currentForegroundPackage(): String? {
        if (!Permissions.hasUsageStatsAccess(context)) return null

        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null

        val now = System.currentTimeMillis()
        val from = minOf(lastQueryAt, now - LOOKBACK_MS)
        lastQueryAt = now

        val events = manager.queryEvents(from, now)
        val event = UsageEvents.Event()
        var latestPackage: String? = null
        var latestTimestamp = 0L

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val isResume = event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
            if (isResume && event.timeStamp >= latestTimestamp) {
                latestTimestamp = event.timeStamp
                latestPackage = event.packageName
            }
        }

        return latestPackage
    }

    private companion object {
        const val LOOKBACK_MS = 10_000L
    }
}

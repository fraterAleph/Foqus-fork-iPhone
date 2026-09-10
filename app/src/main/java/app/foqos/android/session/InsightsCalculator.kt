package app.foqos.android.session

import app.foqos.android.data.db.SessionWithProfile
import java.util.Calendar

/** Aggregates finished sessions into the numbers the insights screen shows. */
object InsightsCalculator {

    data class DayBucket(
        val startOfDay: Long,
        val focusMs: Long,
        val sessionCount: Int,
    )

    data class Summary(
        val totalFocusMs: Long,
        val sessionCount: Int,
        val longestFocusMs: Long,
        val currentStreakDays: Int,
        val days: List<DayBucket>,
    )

    fun summarise(
        sessions: List<SessionWithProfile>,
        days: Int = 28,
        now: Long = System.currentTimeMillis(),
    ): Summary {
        val startOfToday = startOfDay(now)
        val buckets = (0 until days).map { offset ->
            startOfToday - offset * DAY_MS
        }.reversed()

        val perDay = buckets.associateWith { 0L to 0 }.toMutableMap()
        var total = 0L
        var longest = 0L
        var count = 0

        sessions.forEach { entry ->
            val focus = SessionTimeCalculator.elapsedFocusMs(entry.session, now)
            val day = startOfDay(entry.session.startTime)
            if (day in perDay) {
                val (existingMs, existingCount) = perDay.getValue(day)
                perDay[day] = (existingMs + focus) to (existingCount + 1)
            }
            total += focus
            count += 1
            if (focus > longest) longest = focus
        }

        val dayBuckets = buckets.map { day ->
            val (focusMs, sessionCount) = perDay.getValue(day)
            DayBucket(day, focusMs, sessionCount)
        }

        return Summary(
            totalFocusMs = total,
            sessionCount = count,
            longestFocusMs = longest,
            currentStreakDays = streak(dayBuckets),
            days = dayBuckets,
        )
    }

    private fun streak(days: List<DayBucket>): Int {
        var streak = 0
        for (bucket in days.reversed()) {
            if (bucket.focusMs > 0) streak++ else break
        }
        return streak
    }

    fun startOfDay(timestamp: Long): Long = Calendar.getInstance().apply {
        timeInMillis = timestamp
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    const val DAY_MS = 24 * 60 * 60 * 1000L
}

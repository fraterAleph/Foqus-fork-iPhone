package app.foqos.android.session

import app.foqos.android.data.db.SessionEntity

/**
 * Turns the raw timestamps on a session into the numbers the UI shows. Breaks and pauses do not
 * count as focus time, which is why elapsed time cannot simply be `now - startTime`.
 */
object SessionTimeCalculator {

    fun elapsedFocusMs(session: SessionEntity, now: Long = System.currentTimeMillis()): Long {
        val end = session.endTime ?: now
        val gross = (end - session.startTime).coerceAtLeast(0)
        return (gross - nonFocusMs(session, now)).coerceAtLeast(0)
    }

    /** Time spent on breaks and pauses, including one that is still open. */
    fun nonFocusMs(session: SessionEntity, now: Long = System.currentTimeMillis()): Long {
        val reference = session.endTime ?: now
        var total = session.usedBreakDurationMs.coerceAtLeast(0)

        val breakStart = session.breakStartTime
        if (breakStart != null) {
            if (session.breakEndTime == null) {
                total += (reference - breakStart).coerceAtLeast(0)
            } else if (session.usedBreakDurationMs == 0L) {
                // Single-break sessions never touch the accumulator, so read the timestamps.
                total += (session.breakEndTime - breakStart).coerceAtLeast(0)
            }
        }

        val pauseStart = session.pauseStartTime
        if (pauseStart != null) {
            val pauseEnd = session.pauseEndTime ?: reference
            total += (pauseEnd - pauseStart).coerceAtLeast(0)
        }

        return total
    }

    fun totalBreakAllowanceMs(breakTimeInMinutes: Int): Long = breakTimeInMinutes * 60_000L

    fun usedBreakMs(
        session: SessionEntity,
        allowMultipleBreaks: Boolean,
        now: Long = System.currentTimeMillis(),
    ): Long {
        val activeBreak = session.breakStartTime
            ?.takeIf { session.breakEndTime == null }
            ?.let { (now - it).coerceAtLeast(0) }
            ?: 0L

        if (!allowMultipleBreaks) {
            val start = session.breakStartTime ?: return 0
            val end = session.breakEndTime ?: now
            return (end - start).coerceAtLeast(0)
        }
        return session.usedBreakDurationMs + activeBreak
    }

    fun remainingBreakMs(
        session: SessionEntity,
        breakTimeInMinutes: Int,
        allowMultipleBreaks: Boolean,
        now: Long = System.currentTimeMillis(),
    ): Long {
        val allowance = totalBreakAllowanceMs(breakTimeInMinutes)
        return (allowance - usedBreakMs(session, allowMultipleBreaks, now)).coerceAtLeast(0)
    }

    /** Milliseconds left on a timer session, or null when the session is open-ended. */
    fun remainingTimerMs(session: SessionEntity, now: Long = System.currentTimeMillis()): Long? {
        val deadline = session.expectedEndTime ?: return null
        return (deadline - now).coerceAtLeast(0)
    }

    fun isTimerExpired(session: SessionEntity, now: Long = System.currentTimeMillis()): Boolean {
        val deadline = session.expectedEndTime ?: return false
        return now >= deadline
    }

    fun formatDuration(millis: Long): String {
        val totalSeconds = (millis / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%02d:%02d".format(minutes, seconds)
        }
    }

    fun formatCompact(millis: Long): String {
        val totalMinutes = (millis / 60_000).coerceAtLeast(0)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            else -> "${minutes}m"
        }
    }
}

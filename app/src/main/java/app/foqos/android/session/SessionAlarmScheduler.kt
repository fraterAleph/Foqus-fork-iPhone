package app.foqos.android.session

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import app.foqos.android.data.db.SessionEntity

/**
 * Wakes the app at the next moment a session changes state — the timer runs out, a break or a
 * pause ends, a temporary-access grant closes. The foreground service ticks once a second while
 * it is alive, but Doze can freeze it, so the deadlines get a real alarm as well.
 */
object SessionAlarmScheduler {

    private const val REQUEST_CODE = 4501

    fun scheduleNextWake(
        context: Context,
        session: SessionEntity,
        breakDeadline: Long? = null,
        pauseDeadline: Long? = null,
        grantDeadline: Long? = null,
    ) {
        val deadlines = listOfNotNull(
            session.expectedEndTime,
            breakDeadline,
            pauseDeadline,
            grantDeadline ?: session.softUnblockUntil,
        ).filter { it > System.currentTimeMillis() }

        val next = deadlines.minOrNull()
        if (next == null) {
            cancel(context)
            return
        }

        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = pendingIntent(context)

        val canScheduleExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            manager.canScheduleExactAlarms()

        if (canScheduleExact) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending)
        } else {
            // Without the exact-alarm permission the deadline can slip by a few minutes; the
            // foreground service tick still closes it as soon as the app is scheduled again.
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending)
        }
    }

    fun cancel(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        manager.cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, SessionAlarmReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

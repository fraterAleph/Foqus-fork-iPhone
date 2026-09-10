package app.foqos.android.session

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import app.foqos.android.MainActivity
import app.foqos.android.R
import app.foqos.android.ServiceLocator
import app.foqos.android.blocking.BlockingState
import app.foqos.android.blocking.SafePackages
import app.foqos.android.blocking.ShieldActivity
import app.foqos.android.data.db.SessionWithProfile
import app.foqos.android.strategy.Strategies
import app.foqos.android.util.ForegroundAppWatcher
import app.foqos.android.util.Permissions
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps a running session alive: the ongoing notification, the one-second tick that closes
 * expired timers, breaks and grants, and the usage-stats fallback blocker for devices where the
 * accessibility service is not enabled.
 */
class FocusSessionService : LifecycleService() {

    private lateinit var watcher: ForegroundAppWatcher
    private var lastFallbackShield: String? = null
    private var lastFallbackAt = 0L

    override fun onCreate() {
        super.onCreate()
        watcher = ForegroundAppWatcher(this)
        createChannel()
        startForegroundWithPlaceholder()
        startLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun startLoop() {
        val controller = ServiceLocator.sessionController(applicationContext)
        val sessions = ServiceLocator.sessionRepository(applicationContext)

        lifecycleScope.launch {
            while (isActive) {
                controller.tick()
                val active = sessions.getActive()
                if (active == null) {
                    stopSelf()
                    return@launch
                }
                notify(buildNotification(active))
                runFallbackBlocker()
                delay(TICK_MS)
            }
        }
    }

    /**
     * Without the accessibility service there is no window-transition callback, so poll instead.
     * Only runs when accessibility is off, to keep the battery cost where it belongs.
     */
    private fun runFallbackBlocker() {
        if (Permissions.isAccessibilityEnabled(this)) return

        val snapshot = BlockingState.current(this)
        if (!snapshot.active || snapshot.suspended) return

        val current = watcher.currentForegroundPackage() ?: return
        if (current in SafePackages.forContext(this)) return
        if (!snapshot.shouldShield(current)) {
            lastFallbackShield = null
            return
        }

        val now = System.currentTimeMillis()
        if (current == lastFallbackShield && now - lastFallbackAt < RESHIELD_DEBOUNCE_MS) return
        lastFallbackShield = current
        lastFallbackAt = now

        runCatching {
            startActivity(
                ShieldActivity.intent(
                    context = this,
                    blockedPackage = current,
                    profileName = snapshot.profileName,
                    profileId = snapshot.profileId,
                )
            )
        }
    }

    private fun startForegroundWithPlaceholder() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Starting focus session…")
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openAppIntent())
            .build()
        startInForeground(notification)
    }

    private fun startInForeground(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun notify(notification: Notification) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(active: SessionWithProfile): Notification {
        val session = active.session
        val profile = active.profile
        val strategy = Strategies.byId(profile.strategyId)

        val remaining = SessionTimeCalculator.remainingTimerMs(session)
        val body = when {
            session.isPauseActive -> "Paused"
            session.isBreakActive -> "On a break"
            session.isSoftUnblockActive -> "Temporary access open"
            remaining != null -> "${SessionTimeCalculator.formatDuration(remaining)} left"
            else -> "${SessionTimeCalculator.formatDuration(SessionTimeCalculator.elapsedFocusMs(session))} focused"
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(profile.name)
            .setContentText(body)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppIntent())

        if (profile.breaksAllowed && strategy.allowsTimedBreaks && !session.isPauseActive) {
            val label = if (session.isBreakActive) "End break" else "Take a break"
            builder.addAction(0, label, openAppIntent())
        }

        return builder.build()
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.session_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.session_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "foqos_sessions"
        private const val NOTIFICATION_ID = 4500
        private const val TICK_MS = 1000L
        private const val RESHIELD_DEBOUNCE_MS = 1500L
        const val ACTION_STOP = "app.foqos.android.STOP_SESSION_SERVICE"

        fun start(context: Context) {
            val intent = Intent(context, FocusSessionService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, FocusSessionService::class.java).setAction(ACTION_STOP)
                )
            }
            runCatching { context.stopService(Intent(context, FocusSessionService::class.java)) }
        }
    }
}

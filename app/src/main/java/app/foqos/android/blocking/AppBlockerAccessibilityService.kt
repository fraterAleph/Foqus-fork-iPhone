package app.foqos.android.blocking

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * The primary blocker. Android has no equivalent of iOS's ManagedSettings shield, so the closest
 * honest approximation is to watch window transitions and cover a blocked app with our own
 * screen the moment it comes forward.
 *
 * Limits worth knowing: the user can turn this service off in Settings, and a determined user
 * can force-stop Foqos. Device-owner suspension ([DeviceOwnerBlocker]) is the only route on
 * Android that a user cannot walk around, and it needs ADB provisioning.
 */
class AppBlockerAccessibilityService : AccessibilityService() {

    private var safePackages: Set<String> = emptySet()
    private var lastShieldedPackage: String? = null
    private var lastShieldAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        safePackages = SafePackages.forContext(this)
        isRunning = true
        Log.i(TAG, "Foqos blocker connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString() ?: return
        if (packageName in safePackages) return

        val snapshot = BlockingState.current(this)
        if (!snapshot.shouldShield(packageName)) {
            lastShieldedPackage = null
            return
        }

        // Window state fires several times for one app switch; only act on the first.
        val now = SystemClock.elapsedRealtime()
        if (packageName == lastShieldedPackage && now - lastShieldAt < RESHIELD_DEBOUNCE_MS) return
        lastShieldedPackage = packageName
        lastShieldAt = now

        showShield(packageName, snapshot)
    }

    private fun showShield(packageName: String, snapshot: BlockingState.Snapshot) {
        val started = runCatching {
            startActivity(
                ShieldActivity.intent(
                    context = this,
                    blockedPackage = packageName,
                    profileName = snapshot.profileName,
                    profileId = snapshot.profileId,
                )
            )
            true
        }.getOrElse {
            Log.w(TAG, "Could not launch the shield, falling back to home", it)
            false
        }

        // If the shield could not be launched (background-start restrictions on some OEM builds),
        // at least take the user out of the blocked app.
        if (!started) {
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        isRunning = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
    }

    companion object {
        private const val TAG = "FoqosBlocker"
        private const val RESHIELD_DEBOUNCE_MS = 700L

        @Volatile
        var isRunning: Boolean = false
            private set
    }
}

package app.foqos.android.blocking

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.UserManager
import android.util.Log

/**
 * System-level app suspension. This is the only blocking on Android that a user cannot simply
 * switch off, but it requires Foqos to be the device owner, which can only be set on a device
 * with no accounts configured:
 *
 * ```
 * adb shell dpm set-device-owner app.foqos.android/.blocking.FoqosDeviceAdminReceiver
 * ```
 *
 * When that has not been done, [isAvailable] is false and the accessibility shield carries the
 * blocking on its own.
 */
object DeviceOwnerBlocker {

    private const val TAG = "FoqosDeviceOwner"

    private fun dpm(context: Context): DevicePolicyManager? =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager

    private fun admin(context: Context) =
        ComponentName(context.applicationContext, FoqosDeviceAdminReceiver::class.java)

    fun isAvailable(context: Context): Boolean =
        dpm(context)?.isDeviceOwnerApp(context.packageName) == true

    /**
     * Suspends the given packages. Returns the packages the system refused to suspend, which is
     * normal for system apps and for Foqos itself.
     */
    fun suspend(context: Context, packages: Set<String>): Set<String> {
        if (!isAvailable(context) || packages.isEmpty()) return packages
        val manager = dpm(context) ?: return packages
        val safe = SafePackages.forContext(context)
        val target = (packages - safe).toTypedArray()
        if (target.isEmpty()) return emptySet()

        return runCatching {
            manager.setPackagesSuspended(admin(context), target, true).toSet()
        }.getOrElse {
            Log.w(TAG, "Suspending packages failed", it)
            packages
        }
    }

    /**
     * Closes the routes around the shield that only a device owner can close: uninstalling Foqos
     * and rebooting into safe mode, where accessibility services do not start.
     *
     * Applied while a session runs and released when it ends, so a device owner install is not
     * permanently harder to undo than it needs to be. Factory reset is deliberately left alone —
     * blocking it is the one restriction that can leave a phone with no way back.
     */
    fun harden(context: Context, enabled: Boolean) {
        if (!isAvailable(context)) return
        val manager = dpm(context) ?: return
        val admin = admin(context)

        runCatching {
            manager.setUninstallBlocked(admin, context.packageName, enabled)
        }.onFailure { Log.w(TAG, "Could not change the uninstall block", it) }

        runCatching {
            if (enabled) {
                manager.addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
            } else {
                manager.clearUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
            }
        }.onFailure { Log.w(TAG, "Could not change the safe boot restriction", it) }
    }

    fun unsuspend(context: Context, packages: Set<String>) {
        if (!isAvailable(context) || packages.isEmpty()) return
        val manager = dpm(context) ?: return
        runCatching {
            manager.setPackagesSuspended(admin(context), packages.toTypedArray(), false)
        }.onFailure { Log.w(TAG, "Unsuspending packages failed", it) }
    }
}

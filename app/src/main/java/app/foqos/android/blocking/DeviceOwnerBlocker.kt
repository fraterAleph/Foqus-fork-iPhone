package app.foqos.android.blocking

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
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

    fun unsuspend(context: Context, packages: Set<String>) {
        if (!isAvailable(context) || packages.isEmpty()) return
        val manager = dpm(context) ?: return
        runCatching {
            manager.setPackagesSuspended(admin(context), packages.toTypedArray(), false)
        }.onFailure { Log.w(TAG, "Unsuspending packages failed", it) }
    }
}

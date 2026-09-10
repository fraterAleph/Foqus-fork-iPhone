package app.foqos.android.blocking

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * Packages the shield must never cover, or the device becomes unusable: the system UI, the
 * current launcher, the dialer (emergency calls) and Foqos itself.
 *
 * The Settings app is deliberately *not* on this list. Under strict mode a user could otherwise
 * walk into Settings and switch the accessibility service off mid-session, which defeats the
 * point of strict mode. It is still reachable whenever no session is running.
 */
object SafePackages {

    fun forContext(context: Context): Set<String> {
        val pm = context.packageManager
        return buildSet {
            add(context.packageName)
            add("com.android.systemui")
            add("android")
            launcherPackages(pm).let(::addAll)
            dialerPackage(pm)?.let(::add)
        }
    }

    private fun launcherPackages(pm: PackageManager): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .mapNotNull { it.activityInfo?.packageName }
            .toSet()
    }

    private fun dialerPackage(pm: PackageManager): String? {
        val intent = Intent(Intent.ACTION_DIAL)
        return pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
    }

    /** True when the package is the system Settings app, used for the strict-mode warning. */
    fun isSettings(context: Context, packageName: String): Boolean {
        val resolved = context.packageManager
            .resolveActivity(Intent(Settings.ACTION_SETTINGS), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
        return packageName == resolved || packageName == "com.android.settings"
    }
}

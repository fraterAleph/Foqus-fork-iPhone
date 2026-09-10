package app.foqos.android.blocking

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledApp(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
)

/**
 * Lists apps that have a launcher entry. Deliberately avoids QUERY_ALL_PACKAGES: the manifest
 * declares a `<queries>` element for the launcher intent instead, which is what Play expects for
 * an app picker.
 */
object InstalledAppsProvider {

    suspend fun load(context: Context): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val safe = SafePackages.forContext(context)

        pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .asSequence()
            .mapNotNull { it.activityInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName !in safe }
            .map { info ->
                val appInfo = info.applicationInfo
                InstalledApp(
                    packageName = info.packageName,
                    label = appInfo?.loadLabel(pm)?.toString() ?: info.packageName,
                    isSystem = appInfo != null &&
                        (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0,
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    fun icon(context: Context, packageName: String): Drawable? =
        runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()

    fun label(context: Context, packageName: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)
}

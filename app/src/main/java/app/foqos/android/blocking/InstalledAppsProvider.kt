package app.foqos.android.blocking

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator

data class InstalledApp(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
) {
    /**
     * Users type what they see, but also what they remember — "instagram" for an app whose
     * label is localised, or the other way round, so both are searched.
     */
    fun matches(query: String): Boolean {
        val needle = query.trim()
        if (needle.isEmpty()) return true
        return label.contains(needle, ignoreCase = true) ||
            packageName.contains(needle, ignoreCase = true)
    }
}

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
        // Locale-aware ordering: a plain string sort files every Cyrillic name after every
        // Latin one, which reads as two unrelated lists on a mixed-language phone.
        val collator = Collator.getInstance()

        // MATCH_DEFAULT_ONLY would keep only activities that also declare CATEGORY_DEFAULT,
        // which most launcher entries do not — it silently drops the majority of installed
        // apps. A launcher query has to match everything.
        pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
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
            .sortedWith { a, b -> collator.compare(a.label, b.label) }
            .toList()
    }

    fun icon(context: Context, packageName: String): Drawable? =
        runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()

    fun label(context: Context, packageName: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)
}

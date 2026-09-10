package app.foqos.android.blocking.vpn

import android.content.Context

/**
 * The domain list the running tunnel enforces. Kept in SharedPreferences for the same reason as
 * [app.foqos.android.blocking.BlockingState]: the VPN service can outlive the app's UI process.
 */
object DomainRules {

    data class Rules(
        val enabled: Boolean = false,
        val domains: Set<String> = emptySet(),
        /** Resolve only the listed domains and refuse everything else. */
        val allowMode: Boolean = false,
        val upstreamDns: String = DEFAULT_UPSTREAM,
    ) {
        fun blocks(name: String): Boolean {
            if (!enabled) return false
            val listed = domains.any { DnsMessage.matches(name, it) }
            return if (allowMode) !listed else listed
        }
    }

    const val DEFAULT_UPSTREAM = "1.1.1.1"

    private const val PREFS = "foqos_domain_rules"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_DOMAINS = "domains"
    private const val KEY_ALLOW_MODE = "allow_mode"
    private const val KEY_UPSTREAM = "upstream"

    @Volatile
    private var cached: Rules? = null

    fun read(context: Context): Rules = cached ?: synchronized(this) {
        cached ?: context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .let { prefs ->
                Rules(
                    enabled = prefs.getBoolean(KEY_ENABLED, false),
                    domains = prefs.getStringSet(KEY_DOMAINS, emptySet()).orEmpty().toSet(),
                    allowMode = prefs.getBoolean(KEY_ALLOW_MODE, false),
                    upstreamDns = prefs.getString(KEY_UPSTREAM, DEFAULT_UPSTREAM)
                        ?: DEFAULT_UPSTREAM,
                )
            }
            .also { cached = it }
    }

    fun write(context: Context, rules: Rules) {
        cached = rules
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, rules.enabled)
            .putStringSet(KEY_DOMAINS, rules.domains)
            .putBoolean(KEY_ALLOW_MODE, rules.allowMode)
            .putString(KEY_UPSTREAM, rules.upstreamDns)
            .apply()
    }

    fun clear(context: Context) = write(context, Rules())
}

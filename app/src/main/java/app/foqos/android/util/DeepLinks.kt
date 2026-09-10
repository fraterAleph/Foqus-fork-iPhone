package app.foqos.android.util

/**
 * Foqos profile links. The iOS app writes `https://foqos.app/profile/<uuid>` to NFC tags and QR
 * codes, so tags written by either app work on both; `foqos://profile/<uuid>` is accepted too.
 *
 * Parsed by hand rather than with `android.net.Uri`: this is the check that decides whether a
 * scanned token can unlock a block, and it belongs in code that a plain JVM test can exercise.
 */
object DeepLinks {

    const val HTTPS_PREFIX = "https://foqos.app/profile/"
    const val SCHEME_PREFIX = "foqos://profile/"

    private val prefixes = listOf(
        SCHEME_PREFIX,
        HTTPS_PREFIX,
        "http://foqos.app/profile/",
        "https://www.foqos.app/profile/",
        "http://www.foqos.app/profile/",
        "foqos.app/profile/",
        "www.foqos.app/profile/",
    )

    fun profileUrl(profileId: String): String = HTTPS_PREFIX + profileId

    /** Pulls a profile id out of a scanned value, or null when it is not a Foqos link. */
    fun profileIdFrom(value: String?): String? {
        val raw = value?.trim().orEmpty()
        if (raw.isEmpty()) return null

        val path = raw.substringBefore('?').substringBefore('#')
        val lower = path.lowercase()

        val prefix = prefixes.firstOrNull { lower.startsWith(it) } ?: return null
        val id = path.substring(prefix.length).trim('/').substringBefore('/')

        return id.takeIf { isUuid(it) }
    }

    private val uuidRegex =
        Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    fun isUuid(value: String): Boolean = uuidRegex.matches(value)
}

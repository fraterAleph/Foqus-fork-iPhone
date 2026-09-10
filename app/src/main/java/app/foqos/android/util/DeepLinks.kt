package app.foqos.android.util

import android.net.Uri

/**
 * Foqos profile links. The iOS app writes `https://foqos.app/profile/<uuid>` to NFC tags and QR
 * codes, so tags written by either app work on both; `foqos://profile/<uuid>` is accepted too.
 */
object DeepLinks {

    const val HTTPS_PREFIX = "https://foqos.app/profile/"
    const val SCHEME_PREFIX = "foqos://profile/"

    fun profileUrl(profileId: String): String = HTTPS_PREFIX + profileId

    /** Pulls a profile id out of a scanned value, or null when it is not a Foqos link. */
    fun profileIdFrom(value: String?): String? {
        val raw = value?.trim().orEmpty()
        if (raw.isEmpty()) return null

        val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return null
        val segments = uri.pathSegments.orEmpty()

        val id = when {
            uri.scheme.equals("foqos", ignoreCase = true) &&
                uri.host.equals("profile", ignoreCase = true) -> segments.firstOrNull()

            uri.host.equals("foqos.app", ignoreCase = true) &&
                segments.firstOrNull() == "profile" -> segments.getOrNull(1)

            else -> null
        }

        return id?.takeIf { isUuid(it) }
    }

    private val uuidRegex =
        Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    fun isUuid(value: String): Boolean = uuidRegex.matches(value)
}

package app.foqos.android.session

import app.foqos.android.data.model.PhysicalUnblockItem
import app.foqos.android.util.DeepLinks

/**
 * Which scanned token is allowed to stop a profile.
 *
 * Kept free of Android and of the database so the rules that decide whether a block can be
 * lifted are directly testable — this is the one piece of the app where a quiet mistake means a
 * session the user cannot end, or one anybody can end.
 */
object UnlockRules {

    /**
     * The kind of unlock item a scan from [source] can satisfy.
     *
     * `MANUAL` and `DEEP_LINK` deliberately satisfy nothing: an in-app button and a URL opened
     * from a browser or a generic scanner app are not physical tokens, and treating them as such
     * would hand back every escape the physical unlock exists to remove.
     */
    fun kindFor(source: TokenSource): PhysicalUnblockItem.Kind? = when (source) {
        TokenSource.NFC -> PhysicalUnblockItem.Kind.NFC
        TokenSource.QR -> PhysicalUnblockItem.Kind.QR
        TokenSource.MANUAL, TokenSource.DEEP_LINK -> null
    }

    /**
     * True when one of [tokens] matches an unlock item of the kind [source] can produce.
     *
     * A single NFC scan yields more than one candidate token — the tag's NDEF payload and its
     * hardware UID — and either may be the registered one, so the caller passes both and any
     * match counts.
     */
    fun matches(
        items: List<PhysicalUnblockItem>,
        tokens: List<String>,
        source: TokenSource,
    ): Boolean {
        val kind = kindFor(source) ?: return false
        if (items.isEmpty() || tokens.isEmpty()) return false

        return items.any { item ->
            item.kind == kind && tokens.any { tokensMatch(item.value, it) }
        }
    }

    /** True when two tokens name the same thing to a user. */
    fun tokensMatch(a: String?, b: String?): Boolean {
        val left = a?.trim()?.lowercase().orEmpty()
        val right = b?.trim()?.lowercase().orEmpty()
        if (left.isEmpty() || right.isEmpty()) return false
        if (left == right) return true

        // A tag holding a profile link and a raw profile id are the same token to a user.
        val leftId = DeepLinks.profileIdFrom(left) ?: left
        val rightId = DeepLinks.profileIdFrom(right) ?: right
        return leftId == rightId
    }

    /**
     * True when a token is a Foqos profile link, which is reproducible by anyone who can read it
     * once. Such a token is fine for starting a session and unfit for ending one, so an NFC-only
     * profile must be keyed on a tag's UID instead.
     */
    fun isClonableLink(token: String): Boolean = DeepLinks.profileIdFrom(token) != null

    /** The unlock items that actually hold a profile shut, ignoring clonable profile links. */
    fun hardNfcKeys(items: List<PhysicalUnblockItem>): List<PhysicalUnblockItem> =
        items.filter { it.kind == PhysicalUnblockItem.Kind.NFC && !isClonableLink(it.value) }
}

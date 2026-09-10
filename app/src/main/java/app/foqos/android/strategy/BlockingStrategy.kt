package app.foqos.android.strategy

/**
 * Grouping used by the strategy picker. Titles and copy follow the iOS app so the two stay
 * recognisably the same product.
 */
enum class StrategyCategory(val title: String, val description: String) {
    MOST_POPULAR(
        "Most popular",
        "Physical triggers that make starting and stopping more deliberate.",
    ),
    EASY_TO_START(
        "Easy to start",
        "Start from the app, then choose how intentional stopping should be.",
    ),
    TIMERS(
        "Timers",
        "Choose a duration first, then let the session end automatically.",
    ),
    ADVANCED(
        "Advanced",
        "Flexible strategies for temporary access and timed pauses.",
    ),
    MORE_OPTIONS(
        "More options",
        "Additional ways to control a focus session.",
    ),
}

enum class StrategyTag(val title: String) {
    NFC("NFC"),
    QR("QR"),
    TIMER("Timer"),
    PAUSE("Pause"),
    MANUAL_START("Manual start"),
    TEMPORARY_ACCESS("Temporary access"),
}

/**
 * A way to start and stop a focus session.
 *
 * The iOS app models each of these as a class implementing a protocol; here the behaviour is
 * fully described by the capability flags below, and [app.foqos.android.session.SessionController]
 * reads those flags to decide what a start or a stop is allowed to do. That keeps the twelve
 * strategies declarative instead of twelve near-identical classes.
 */
data class BlockingStrategy(
    val id: String,
    val name: String,
    val description: String,
    val category: StrategyCategory,
    /** ARGB accent used by the picker card. */
    val accent: Long,
    val usesNfc: Boolean = false,
    val usesQr: Boolean = false,
    val hasTimer: Boolean = false,
    val hasPauseMode: Boolean = false,
    /** The session is started from inside the app rather than by a scan. */
    val startsManually: Boolean = false,
    /** Stopping requires the same tag or code that started the session. */
    val requiresSameCodeToStop: Boolean = false,
    /** The session can be stopped by the in-app button alone. */
    val allowsManualStop: Boolean = false,
    val allowsTimedBreaks: Boolean = true,
    /** Keeps blocking on but hands out a limited number of short openings. */
    val hasSoftUnblock: Boolean = false,
) {
    val usesPhysicalToken: Boolean get() = usesNfc || usesQr

    val tags: List<StrategyTag>
        get() = buildList {
            if (usesNfc) add(StrategyTag.NFC)
            if (usesQr) add(StrategyTag.QR)
            if (hasTimer) add(StrategyTag.TIMER)
            if (hasPauseMode) add(StrategyTag.PAUSE)
            if (startsManually) add(StrategyTag.MANUAL_START)
            if (hasSoftUnblock) add(StrategyTag.TEMPORARY_ACCESS)
        }
}

private const val YELLOW = 0xFFF5C542
private const val BLUE = 0xFF4C8DFF
private const val GREEN = 0xFF3ECF8E
private const val PURPLE = 0xFFA57BFF
private const val ORANGE = 0xFFFF9F43
private const val PINK = 0xFFFF6B9A

/**
 * The strategy registry. Ids match the iOS app so a profile exported from one can be read by
 * the other without a translation table.
 */
object Strategies {

    val manual = BlockingStrategy(
        id = "ManualBlockingStrategy",
        name = "Manual",
        description = "Start and stop directly in Foqos.",
        category = StrategyCategory.EASY_TO_START,
        accent = GREEN,
        startsManually = true,
        allowsManualStop = true,
    )

    val nfc = BlockingStrategy(
        id = "NFCBlockingStrategy",
        name = "NFC tags",
        description = "Start by scanning an NFC tag. To stop, scan the same tag again.",
        category = StrategyCategory.MOST_POPULAR,
        accent = YELLOW,
        usesNfc = true,
        requiresSameCodeToStop = true,
    )

    val qr = BlockingStrategy(
        id = "QRCodeBlockingStrategy",
        name = "QR codes",
        description = "Start by scanning a QR code or barcode. Scan the same code again to stop.",
        category = StrategyCategory.MOST_POPULAR,
        accent = BLUE,
        usesQr = true,
        requiresSameCodeToStop = true,
    )

    val nfcManual = BlockingStrategy(
        id = "NFCManualBlockingStrategy",
        name = "Manual start, NFC stop",
        description = "Start in the app. Stop by scanning an NFC tag.",
        category = StrategyCategory.EASY_TO_START,
        accent = YELLOW,
        usesNfc = true,
        startsManually = true,
    )

    val qrManual = BlockingStrategy(
        id = "QRManualBlockingStrategy",
        name = "Manual start, QR stop",
        description = "Start in the app. Stop by scanning a QR code or barcode.",
        category = StrategyCategory.EASY_TO_START,
        accent = BLUE,
        usesQr = true,
        startsManually = true,
    )

    val nfcTimer = BlockingStrategy(
        id = "NFCTimerBlockingStrategy",
        name = "Timer with NFC escape",
        description = "Blocking ends when the timer expires, or early when an allowed NFC tag is scanned.",
        category = StrategyCategory.TIMERS,
        accent = YELLOW,
        usesNfc = true,
        hasTimer = true,
        startsManually = true,
    )

    val qrTimer = BlockingStrategy(
        id = "QRTimerBlockingStrategy",
        name = "Timer with QR escape",
        description = "Blocking ends when the timer expires, or early when an allowed code is scanned.",
        category = StrategyCategory.TIMERS,
        accent = BLUE,
        usesQr = true,
        hasTimer = true,
        startsManually = true,
    )

    val shortcutTimer = BlockingStrategy(
        id = "ShortcutTimerBlockingStrategy",
        name = "Plain timer",
        description = "Choose a duration. Stop early with the in-app Stop button.",
        category = StrategyCategory.TIMERS,
        accent = GREEN,
        hasTimer = true,
        startsManually = true,
        allowsManualStop = true,
    )

    val nfcPauseTimer = BlockingStrategy(
        id = "NFCPauseTimerBlockingStrategy",
        name = "NFC pause control",
        description = "Scan a tag once to pause for a set time, scan again during the pause to stop fully.",
        category = StrategyCategory.ADVANCED,
        accent = ORANGE,
        usesNfc = true,
        hasPauseMode = true,
        startsManually = true,
        allowsTimedBreaks = false,
    )

    val qrPauseTimer = BlockingStrategy(
        id = "QRPauseTimerBlockingStrategy",
        name = "QR pause control",
        description = "Scan a code once to pause for a set time, scan again during the pause to stop fully.",
        category = StrategyCategory.ADVANCED,
        accent = PURPLE,
        usesQr = true,
        hasPauseMode = true,
        startsManually = true,
        allowsTimedBreaks = false,
    )

    val nfcSoftUnblock = BlockingStrategy(
        id = "NFCSoftUnblockBlockingStrategy",
        name = "Temporary access, NFC stop",
        description = "Keep blocking on but allow a set number of short openings. Stop with an NFC tag.",
        category = StrategyCategory.ADVANCED,
        accent = PINK,
        usesNfc = true,
        startsManually = true,
        hasSoftUnblock = true,
    )

    val qrSoftUnblock = BlockingStrategy(
        id = "QRSoftUnblockBlockingStrategy",
        name = "Temporary access, QR stop",
        description = "Keep blocking on but allow a set number of short openings. Stop with a QR code.",
        category = StrategyCategory.ADVANCED,
        accent = PINK,
        usesQr = true,
        startsManually = true,
        hasSoftUnblock = true,
    )

    val all: List<BlockingStrategy> = listOf(
        nfc,
        qr,
        manual,
        nfcManual,
        qrManual,
        shortcutTimer,
        nfcTimer,
        qrTimer,
        nfcPauseTimer,
        qrPauseTimer,
        nfcSoftUnblock,
        qrSoftUnblock,
    )

    private val byId = all.associateBy { it.id }

    fun byId(id: String?): BlockingStrategy = byId[id] ?: manual

    fun grouped(): Map<StrategyCategory, List<BlockingStrategy>> =
        StrategyCategory.entries.associateWith { category -> all.filter { it.category == category } }
            .filterValues { it.isNotEmpty() }
}

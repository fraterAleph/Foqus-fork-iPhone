package app.foqos.android.session

sealed interface SessionResult {
    data class Started(val profileName: String) : SessionResult
    data class Stopped(val profileName: String) : SessionResult
    data object Paused : SessionResult
    data object Resumed : SessionResult
    data class Granted(val remaining: Int) : SessionResult
    data class Rejected(val message: String) : SessionResult
    data class Info(val message: String) : SessionResult
}

/** Where a start or stop request came from. Rules differ per source. */
enum class TokenSource { NFC, QR, MANUAL, DEEP_LINK }

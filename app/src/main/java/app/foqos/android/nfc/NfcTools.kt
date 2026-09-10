package app.foqos.android.nfc

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.FormatException
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Build
import android.os.Parcelable
import java.io.IOException

/**
 * NFC is the one place where Android is more capable than iOS: tags can be read in the
 * background without the user opening anything, and written without a system sheet.
 *
 * A Foqos tag holds `https://foqos.app/profile/<uuid>`, exactly what the iOS app writes, so a
 * tag written on either platform is understood by both.
 */
object NfcTools {

    fun adapter(activity: Activity): NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)

    fun isAvailable(activity: Activity): Boolean = adapter(activity) != null

    fun isEnabled(activity: Activity): Boolean = adapter(activity)?.isEnabled == true

    /**
     * Routes tag discoveries to [activity] while it is in the foreground, so a scan does not
     * bounce through the launcher.
     */
    fun enableForegroundDispatch(activity: Activity) {
        val adapter = adapter(activity) ?: return
        val intent = Intent(activity, activity.javaClass)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        // Foreground dispatch needs a mutable PendingIntent so the system can attach the tag.
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
        val pending = PendingIntent.getActivity(activity, 0, intent, flags)

        val filters = arrayOf(
            IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED),
            IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED),
            IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED),
        )
        val techLists = arrayOf(
            arrayOf(Ndef::class.java.name),
            arrayOf(NdefFormatable::class.java.name),
        )

        runCatching { adapter.enableForegroundDispatch(activity, pending, filters, techLists) }
    }

    fun disableForegroundDispatch(activity: Activity) {
        runCatching { adapter(activity)?.disableForegroundDispatch(activity) }
    }

    /** The token a scanned tag represents: its NDEF payload, or its hardware id as a fallback. */
    fun tokenFrom(intent: Intent): String? {
        payloadFrom(intent)?.let { return it }
        return tagIdFrom(intent)
    }

    fun payloadFrom(intent: Intent): String? {
        val messages = rawMessages(intent) ?: return null
        for (parcelable in messages) {
            val message = parcelable as? NdefMessage ?: continue
            for (record in message.records) {
                recordText(record)?.let { return it }
            }
        }
        return null
    }

    fun tagIdFrom(intent: Intent): String? {
        val tag = tagFrom(intent) ?: return null
        return tag.id?.joinToString("") { "%02x".format(it) }?.takeIf { it.isNotEmpty() }
    }

    fun tagFrom(intent: Intent): Tag? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        }

    private fun rawMessages(intent: Intent): Array<out Parcelable>? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, NdefMessage::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
        }

    private fun recordText(record: NdefRecord): String? {
        // URI records are what Foqos writes; text records are accepted so hand-written tags work.
        record.toUri()?.toString()?.takeIf { it.isNotEmpty() }?.let { return it }

        if (record.tnf == NdefRecord.TNF_WELL_KNOWN &&
            record.type.contentEquals(NdefRecord.RTD_TEXT)
        ) {
            val payload = record.payload
            if (payload.isEmpty()) return null
            val status = payload[0].toInt() and 0xFF
            val languageLength = status and 0x3F
            val encoding = if (status and 0x80 == 0) Charsets.UTF_8 else Charsets.UTF_16
            val start = 1 + languageLength
            if (start >= payload.size) return null
            return String(payload, start, payload.size - start, encoding)
        }

        return null
    }

    sealed interface WriteResult {
        data object Success : WriteResult
        data class Failure(val message: String) : WriteResult
    }

    /** Writes a profile link to a tag. Formats a blank tag if it supports NDEF formatting. */
    fun write(tag: Tag, url: String): WriteResult {
        val message = NdefMessage(arrayOf(NdefRecord.createUri(url)))

        Ndef.get(tag)?.let { ndef ->
            return runCatching {
                ndef.connect()
                when {
                    !ndef.isWritable -> WriteResult.Failure("That tag is read-only.")
                    ndef.maxSize < message.toByteArray().size ->
                        WriteResult.Failure("That tag is too small for a Foqos link.")
                    else -> {
                        ndef.writeNdefMessage(message)
                        WriteResult.Success
                    }
                }
            }.getOrElse { error ->
                WriteResult.Failure(errorMessage(error))
            }.also { runCatching { ndef.close() } }
        }

        NdefFormatable.get(tag)?.let { formatable ->
            return runCatching {
                formatable.connect()
                formatable.format(message)
                WriteResult.Success as WriteResult
            }.getOrElse { error ->
                WriteResult.Failure(errorMessage(error))
            }.also { runCatching { formatable.close() } }
        }

        return WriteResult.Failure("That tag does not support NDEF.")
    }

    private fun errorMessage(error: Throwable): String = when (error) {
        is FormatException -> "The tag rejected the message format."
        is IOException -> "Tag lost. Hold it against the phone until writing finishes."
        else -> error.message ?: "Writing to the tag failed."
    }
}

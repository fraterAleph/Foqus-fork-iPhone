package app.foqos.android.blocking.vpn

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Minimal DNS parsing: read the queried name, and synthesise an NXDOMAIN answer for it. */
object DnsMessage {

    private const val HEADER_SIZE = 12

    /** The first question's name in lower case, without a trailing dot, or null if unreadable. */
    fun queryName(payload: ByteArray): String? {
        if (payload.size < HEADER_SIZE + 5) return null

        val questionCount = ((payload[4].toInt() and 0xFF) shl 8) or (payload[5].toInt() and 0xFF)
        if (questionCount < 1) return null

        val labels = StringBuilder()
        var index = HEADER_SIZE
        while (index < payload.size) {
            val length = payload[index].toInt() and 0xFF
            if (length == 0) break
            // A pointer in a question section is malformed; bail out rather than guess.
            if (length and 0xC0 != 0) return null
            index++
            if (index + length > payload.size) return null
            if (labels.isNotEmpty()) labels.append('.')
            labels.append(String(payload, index, length, Charsets.US_ASCII))
            index += length
        }

        return labels.toString().lowercase().takeIf { it.isNotEmpty() }
    }

    /** Copies the request's header and question, flipping it into an NXDOMAIN response. */
    fun nxDomain(request: ByteArray): ByteArray? {
        if (request.size < HEADER_SIZE) return null

        val questionEnd = questionSectionEnd(request) ?: return null
        val response = ByteBuffer.allocate(questionEnd).order(ByteOrder.BIG_ENDIAN)
        response.put(request, 0, questionEnd)

        val requestFlags = ((request[2].toInt() and 0xFF) shl 8) or (request[3].toInt() and 0xFF)
        // QR=1, copy the opcode and RD bit, RA=1, RCODE=3 (name error).
        val responseFlags = 0x8000 or (requestFlags and 0x7900) or 0x0080 or 0x0003
        response.putShort(2, responseFlags.toShort())
        response.putShort(4, 1)  // QDCOUNT
        response.putShort(6, 0)  // ANCOUNT
        response.putShort(8, 0)  // NSCOUNT
        response.putShort(10, 0) // ARCOUNT

        return response.array()
    }

    private fun questionSectionEnd(payload: ByteArray): Int? {
        var index = HEADER_SIZE
        while (index < payload.size) {
            val length = payload[index].toInt() and 0xFF
            if (length == 0) {
                // null label + QTYPE + QCLASS
                val end = index + 1 + 4
                return end.takeIf { it <= payload.size }
            }
            if (length and 0xC0 != 0) return null
            index += length + 1
        }
        return null
    }

    /**
     * True when [name] is the domain itself or one of its subdomains, which is what users mean
     * when they add `instagram.com` to a profile.
     */
    fun matches(name: String, domain: String): Boolean {
        val target = domain.trim().lowercase().removePrefix("*.").removeSuffix(".")
        if (target.isEmpty()) return false
        return name == target || name.endsWith(".$target")
    }
}

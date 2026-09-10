package app.foqos.android

import app.foqos.android.blocking.vpn.DnsMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsMessageTest {

    /** A minimal query for `www.example.com`, type A, class IN. */
    private fun query(vararg labels: String): ByteArray {
        val header = byteArrayOf(
            0x12, 0x34,             // id
            0x01, 0x00,             // flags: standard query, recursion desired
            0x00, 0x01,             // qdcount
            0x00, 0x00,             // ancount
            0x00, 0x00,             // nscount
            0x00, 0x00,             // arcount
        )
        val question = labels.flatMap { label ->
            listOf(label.length.toByte()) + label.toByteArray(Charsets.US_ASCII).toList()
        } + listOf<Byte>(0x00, 0x00, 0x01, 0x00, 0x01)

        return header + question.toByteArray()
    }

    @Test
    fun `reads the queried name`() {
        assertEquals("www.example.com", DnsMessage.queryName(query("www", "example", "com")))
    }

    @Test
    fun `a truncated packet yields no name`() {
        assertEquals(null, DnsMessage.queryName(ByteArray(4)))
    }

    @Test
    fun `the nxdomain reply keeps the id and sets the response bits`() {
        val request = query("news", "ycombinator", "com")
        val response = DnsMessage.nxDomain(request)
        assertNotNull(response)
        requireNotNull(response)

        assertEquals(request[0], response[0])
        assertEquals(request[1], response[1])

        val flags = ((response[2].toInt() and 0xFF) shl 8) or (response[3].toInt() and 0xFF)
        assertEquals(0x8000, flags and 0x8000)   // QR: this is a response
        assertEquals(3, flags and 0x000F)        // RCODE 3: name error
        assertEquals(1, ((response[4].toInt() and 0xFF) shl 8) or (response[5].toInt() and 0xFF))
        assertEquals(0, ((response[6].toInt() and 0xFF) shl 8) or (response[7].toInt() and 0xFF))
    }

    @Test
    fun `a domain rule covers its subdomains but not a lookalike`() {
        assertTrue(DnsMessage.matches("instagram.com", "instagram.com"))
        assertTrue(DnsMessage.matches("www.instagram.com", "instagram.com"))
        assertTrue(DnsMessage.matches("cdn.eu.instagram.com", "instagram.com"))
        assertFalse(DnsMessage.matches("notinstagram.com", "instagram.com"))
        assertFalse(DnsMessage.matches("instagram.com.evil.net", "instagram.com"))
    }

    @Test
    fun `a wildcard prefix in a rule is tolerated`() {
        assertTrue(DnsMessage.matches("m.facebook.com", "*.facebook.com"))
    }
}

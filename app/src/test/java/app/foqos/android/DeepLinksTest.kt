package app.foqos.android

import app.foqos.android.util.DeepLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepLinksTest {

    private val id = "6e7f0b3c-1d2a-4e5f-8a9b-0c1d2e3f4a5b"

    @Test
    fun `reads the link shape the ios app writes`() {
        assertEquals(id, DeepLinks.profileIdFrom("https://foqos.app/profile/$id"))
    }

    @Test
    fun `reads the custom scheme`() {
        assertEquals(id, DeepLinks.profileIdFrom("foqos://profile/$id"))
    }

    @Test
    fun `tolerates www, http, a trailing slash and a query string`() {
        assertEquals(id, DeepLinks.profileIdFrom("http://www.foqos.app/profile/$id/"))
        assertEquals(id, DeepLinks.profileIdFrom("https://foqos.app/profile/$id?utm=x"))
        assertEquals(id, DeepLinks.profileIdFrom("  https://FOQOS.app/profile/$id  "))
    }

    @Test
    fun `a lookalike host is not a foqos link`() {
        assertNull(DeepLinks.profileIdFrom("https://foqos.app.evil.net/profile/$id"))
        assertNull(DeepLinks.profileIdFrom("https://notfoqos.app/profile/$id"))
    }

    @Test
    fun `a link without a valid uuid is rejected`() {
        assertNull(DeepLinks.profileIdFrom("https://foqos.app/profile/not-a-uuid"))
        assertNull(DeepLinks.profileIdFrom("https://foqos.app/profile/"))
    }

    @Test
    fun `a bare tag uid is not a link`() {
        assertNull(DeepLinks.profileIdFrom("04a1b2c3d4e580"))
    }

    @Test
    fun `blank input has no profile`() {
        assertNull(DeepLinks.profileIdFrom(null))
        assertNull(DeepLinks.profileIdFrom("   "))
    }

    @Test
    fun `uuid validation`() {
        assertTrue(DeepLinks.isUuid(id.uppercase()))
        assertFalse(DeepLinks.isUuid("not-a-uuid"))
        assertFalse(DeepLinks.isUuid(""))
    }

    @Test
    fun `builds the canonical url`() {
        assertEquals("https://foqos.app/profile/$id", DeepLinks.profileUrl(id))
    }
}

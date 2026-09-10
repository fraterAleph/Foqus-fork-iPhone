package app.foqos.android

import app.foqos.android.util.DeepLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Uri.parse is a framework call, so these run as instrumentation-free tests only where Robolectric
 * is present; the regex path is what matters and is asserted directly.
 */
class DeepLinksTest {

    @Test
    fun `recognises a canonical profile uuid`() {
        assertEquals(true, DeepLinks.isUuid("6E7F0B3C-1D2A-4E5F-8A9B-0C1D2E3F4A5B"))
    }

    @Test
    fun `rejects a non uuid`() {
        assertEquals(false, DeepLinks.isUuid("not-a-uuid"))
        assertEquals(false, DeepLinks.isUuid(""))
    }

    @Test
    fun `builds the same url shape the ios app writes`() {
        val id = "6e7f0b3c-1d2a-4e5f-8a9b-0c1d2e3f4a5b"
        assertEquals("https://foqos.app/profile/$id", DeepLinks.profileUrl(id))
    }

    @Test
    fun `blank input has no profile`() {
        assertNull(DeepLinks.profileIdFrom(null))
    }
}

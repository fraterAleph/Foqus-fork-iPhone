package app.foqos.android

import app.foqos.android.data.model.PhysicalUnblockItem
import app.foqos.android.session.TokenSource
import app.foqos.android.session.UnlockRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnlockRulesTest {

    private val tagUid = "04a1b2c3d4e580"
    private val otherUid = "04ffffffffffff"

    private fun nfcKey(value: String = tagUid) = PhysicalUnblockItem(
        id = "k1",
        kind = PhysicalUnblockItem.Kind.NFC,
        value = value,
        label = "Desk tag",
    )

    private fun qrKey(value: String) = PhysicalUnblockItem(
        id = "k2",
        kind = PhysicalUnblockItem.Kind.QR,
        value = value,
        label = "Printed code",
    )

    @Test
    fun `the linked tag unlocks the profile`() {
        assertTrue(UnlockRules.matches(listOf(nfcKey()), listOf(tagUid), TokenSource.NFC))
    }

    @Test
    fun `another tag does not`() {
        assertFalse(UnlockRules.matches(listOf(nfcKey()), listOf(otherUid), TokenSource.NFC))
    }

    @Test
    fun `the in-app button can never satisfy a physical key`() {
        assertFalse(UnlockRules.matches(listOf(nfcKey()), listOf(tagUid), TokenSource.MANUAL))
        assertEquals(null, UnlockRules.kindFor(TokenSource.MANUAL))
    }

    @Test
    fun `a deep link can never satisfy a physical key`() {
        assertFalse(UnlockRules.matches(listOf(nfcKey()), listOf(tagUid), TokenSource.DEEP_LINK))
        assertEquals(null, UnlockRules.kindFor(TokenSource.DEEP_LINK))
    }

    /** The point of NFC-only mode: a scanned QR cannot stand in for the tag. */
    @Test
    fun `a QR scan cannot satisfy an NFC key even with the same value`() {
        assertFalse(UnlockRules.matches(listOf(nfcKey()), listOf(tagUid), TokenSource.QR))
    }

    @Test
    fun `an NFC scan cannot satisfy a QR key`() {
        assertFalse(UnlockRules.matches(listOf(qrKey("shop-code")), listOf("shop-code"), TokenSource.NFC))
    }

    @Test
    fun `either candidate token from one tag counts`() {
        val scanned = listOf("https://foqos.app/profile/x", tagUid)
        assertTrue(UnlockRules.matches(listOf(nfcKey()), scanned, TokenSource.NFC))
    }

    @Test
    fun `a profile with no keys is not unlocked by anything`() {
        assertFalse(UnlockRules.matches(emptyList(), listOf(tagUid), TokenSource.NFC))
    }

    @Test
    fun `an empty scan unlocks nothing`() {
        assertFalse(UnlockRules.matches(listOf(nfcKey()), emptyList(), TokenSource.NFC))
    }

    @Test
    fun `matching ignores case and surrounding space`() {
        assertTrue(
            UnlockRules.matches(listOf(nfcKey()), listOf("  04A1B2C3D4E580 "), TokenSource.NFC)
        )
    }

    @Test
    fun `a uid is a hard key and a profile link is not`() {
        val link = "https://foqos.app/profile/6e7f0b3c-1d2a-4e5f-8a9b-0c1d2e3f4a5b"
        assertEquals(
            listOf(tagUid),
            UnlockRules.hardNfcKeys(listOf(nfcKey(), nfcKey(link))).map { it.value },
        )
    }

    @Test
    fun `a QR key never counts as a hard NFC key`() {
        assertTrue(UnlockRules.hardNfcKeys(listOf(qrKey(tagUid))).isEmpty())
    }
}

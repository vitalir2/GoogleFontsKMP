package vitalir.me.googlefonts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class ProviderTest {

    @Test
    fun holdsProviderFields() {
        val certs = listOf(listOf(byteArrayOf(1, 2, 3)))
        val provider = GoogleFont.Provider("authority", "pkg", certs)
        assertEquals("authority", provider.providerAuthority)
        assertEquals("pkg", provider.providerPackage)
        assertEquals(certs, provider.certificates)
        assertEquals(0, provider.certificatesRes)
    }

    @Test
    fun intCertsConstructor() {
        val provider = GoogleFont.Provider("authority", "pkg", 42)
        assertNull(provider.certificates)
        assertEquals(42, provider.certificatesRes)
    }

    @Test
    fun equality() {
        val certs = listOf(listOf(byteArrayOf(1)))
        val a = GoogleFont.Provider("authority", "pkg", certs)
        val b = GoogleFont.Provider("authority", "pkg", certs)
        val c = GoogleFont.Provider("authority", "pkg", 42)
        assertEquals(a, b)
        assertNotEquals(a, c)
    }
}
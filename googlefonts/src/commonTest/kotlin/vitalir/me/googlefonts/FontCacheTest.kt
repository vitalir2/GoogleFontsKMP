package vitalir.me.googlefonts

import androidx.compose.ui.text.font.FontVariation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class FontCacheTest {

    @Test
    fun fontFileKeyIsFilesystemSafe() {
        assertEquals("font_open_sans_400_n_b.ttf", fontFileKey("Open Sans", 400, false, bestEffort = true))
        assertEquals("font_roboto_700_i_s.ttf", fontFileKey("Roboto", 700, true, bestEffort = false))
    }

    @Test
    fun fontFileKeySeparatesBestEffort() {
        assertNotEquals(fontFileKey("Roboto", 400, false, true), fontFileKey("Roboto", 400, false, false))
    }

    @Test
    fun fontMemoryKeyIncludesVariation() {
        val a = fontMemoryKey("Roboto", 400, false, "wght=400")
        val b = fontMemoryKey("Roboto", 400, false, "wght=500")
        assertNotEquals(a, b)
    }

    @Test
    fun variationCacheKeyIsOrderInsensitive() {
        val a = variationCacheKey(FontVariation.Settings(FontVariation.weight(400), FontVariation.width(100f)))
        val b = variationCacheKey(FontVariation.Settings(FontVariation.width(100f), FontVariation.weight(400)))
        assertEquals(a, b)
    }

    @Test
    fun isPlausibleFontFileAcceptsKnownMagics() {
        assertTrue(isPlausibleFontFile(byteArrayOf(0x00, 0x01, 0x00, 0x00, 0x00)))
        assertTrue(isPlausibleFontFile("OTTO...".encodeToByteArray()))
        assertTrue(isPlausibleFontFile("wOFF....".encodeToByteArray()))
        assertFalse(isPlausibleFontFile("<html>".encodeToByteArray()))
        assertFalse(isPlausibleFontFile(byteArrayOf(1, 2)))
    }
}
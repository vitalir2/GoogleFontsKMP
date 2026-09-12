package vitalir.me.googlefonts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class FontCacheTest {

    @Test
    fun fontFileKeyIsFilesystemSafe() {
        assertEquals("font_open_sans_400_n.ttf", fontFileKey("Open Sans", 400, false))
        assertEquals("font_roboto_700_i.ttf", fontFileKey("Roboto", 700, true))
    }

    @Test
    fun fontMemoryKeyIncludesVariation() {
        val a = fontMemoryKey("Roboto", 400, false, "wght=400")
        val b = fontMemoryKey("Roboto", 400, false, "wght=500")
        assertNotEquals(a, b)
    }
}
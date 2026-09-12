package vitalir.me.googlefonts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FontResolverTest {

    private val directory = FontDirectory(
        families = listOf(
            FontFamilyEntry(
                name = "Roboto",
                menuUrl = null,
                fonts = listOf(
                    FontEntry(100, false, "Thin", "u100"),
                    FontEntry(400, false, "Regular", "u400"),
                    FontEntry(700, false, "Bold", "u700"),
                    FontEntry(400, true, "Regular Italic", "u400i"),
                ),
            ),
        ),
    )

    @Test
    fun exactMatchWins() {
        val entry = directory.resolve("Roboto", 400, false, bestEffort = true)
        assertEquals("u400", entry?.url)
    }

    @Test
    fun familyNameIsCaseInsensitive() {
        val entry = directory.resolve("roboto", 700, false, bestEffort = true)
        assertEquals("u700", entry?.url)
    }

    @Test
    fun bestEffortPicksClosestWeight() {
        val entry = directory.resolve("Roboto", 500, false, bestEffort = true)
        assertEquals("u400", entry?.url)
    }

    @Test
    fun bestEffortPrefersSameItalic() {
        val entry = directory.resolve("Roboto", 700, true, bestEffort = true)
        assertEquals("u400i", entry?.url)
    }

    @Test
    fun noBestEffortReturnsNullOnMiss() {
        assertNull(directory.resolve("Roboto", 500, false, bestEffort = false))
    }

    @Test
    fun unknownFamilyReturnsNull() {
        assertNull(directory.resolve("Nope", 400, false, bestEffort = true))
    }
}
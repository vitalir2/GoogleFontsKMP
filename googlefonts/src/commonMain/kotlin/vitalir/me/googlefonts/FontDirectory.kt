package vitalir.me.googlefonts

/**
 * Parsed model of the Google Fonts directory
 * (`https://fonts.gstatic.com/s/a/directory.xml`).
 */
internal data class FontDirectory(
    val families: List<FontFamilyEntry>,
)

internal data class FontFamilyEntry(
    val name: String,
    val menuUrl: String?,
    val fonts: List<FontEntry>,
)

internal data class FontEntry(
    val weight: Int,
    val italic: Boolean,
    val styleName: String,
    val url: String,
)

/**
 * Minimal parser for the Google Fonts directory XML. The format is regular and stable:
 *
 * ```
 * <font_directory version='28'>
 *   <families>
 *     <family name='Roboto' menu='//fonts.gstatic.com/s/roboto/...'>
 *       <font weight='400' width='100.0' italic='0.0' styleName='Regular' url='//fonts.gstatic.com/s/a/<hash>.ttf'/>
 *     </family>
 *   </families>
 * </font_directory>
 * ```
 */
internal object FontDirectoryParser {

    private val familyTag = Regex("<family\\b([^>]*)>")
    private val fontTag = Regex("<font\\b([^>]*)/>")
    // Accept both single- and double-quoted attribute values, so a quote-style change by Google
    // cannot silently yield an empty directory.
    private val attribute = Regex("""(\w+)='([^']*)'|(\w+)="([^"]*)"""")

    fun parse(xml: String): FontDirectory {
        val families = mutableListOf<FontFamilyEntry>()
        val familyMatches = familyTag.findAll(xml).toList()
        for ((index, match) in familyMatches.withIndex()) {
            val attrs = parseAttributes(match.groupValues[1])
            val name = attrs["name"] ?: continue
            val start = match.range.last + 1
            val end = if (index + 1 < familyMatches.size) {
                familyMatches[index + 1].range.first
            } else {
                xml.length
            }
            val fonts = fontTag.findAll(xml.substring(start, end)).mapNotNull { fontMatch ->
                val fontAttrs = parseAttributes(fontMatch.groupValues[1])
                val weight = fontAttrs["weight"]?.toIntOrNull() ?: return@mapNotNull null
                val url = fontAttrs["url"] ?: return@mapNotNull null
                FontEntry(
                    weight = weight,
                    italic = fontAttrs["italic"]?.toFloatOrNull()?.let { it > 0f } ?: false,
                    styleName = fontAttrs["styleName"] ?: "",
                    url = url,
                )
            }.toList()
            families += FontFamilyEntry(name = name, menuUrl = attrs["menu"], fonts = fonts)
        }
        return FontDirectory(families)
    }

    private fun parseAttributes(tag: String): Map<String, String> =
        attribute.findAll(tag).associate { match ->
            if (match.groupValues[2].isNotEmpty() || match.groupValues[1].isNotEmpty()) {
                match.groupValues[1] to match.groupValues[2]
            } else {
                match.groupValues[3] to match.groupValues[4]
            }
        }
}
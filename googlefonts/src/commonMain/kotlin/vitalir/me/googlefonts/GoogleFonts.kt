package vitalir.me.googlefonts

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

/**
 * Entry point for loading Google Fonts.
 */
public object GoogleFonts {

    /**
     * HTTP client used to download fonts. When null, a platform default is used where available
     * (the JDK HTTP client on the JVM). Set this to inject a custom client, e.g. the Ktor-backed
     * one from the `googlefonts-ktor` module.
     */
    public var httpClient: FontHttpClient? = null

    internal fun resolveHttpClient(): FontHttpClient =
        httpClient ?: defaultHttpClient()
            ?: throw GoogleFontException(
                "No FontHttpClient configured. Set GoogleFonts.httpClient or add the googlefonts-ktor module.",
            )
}

/**
 * Loads [this] Google Font and returns a Compose [Font] ready to be used in a [FontFamily].
 *
 * The font is cached in memory and on disk (where available), so subsequent loads — including
 * offline ones — are served from the cache.
 *
 * @throws GoogleFontException when the font cannot be resolved or downloaded.
 */
public suspend fun GoogleFont.load(
    weight: FontWeight = FontWeight.Normal,
    style: FontStyle = FontStyle.Normal,
    variationSettings: FontVariation.Settings = FontVariation.Settings(weight, style),
): Font = loadCached(this, weight, style, variationSettings, context = null)

/** Loads and caches [this] font without returning it. */
public suspend fun GoogleFont.preload(
    weight: FontWeight = FontWeight.Normal,
    style: FontStyle = FontStyle.Normal,
    variationSettings: FontVariation.Settings = FontVariation.Settings(weight, style),
) {
    load(weight, style, variationSettings)
}

/**
 * Returns true when [this] font is already cached (in memory or on disk) for the given
 * weight/style, meaning [load] would not require a network request.
 */
public suspend fun GoogleFont.isCached(
    weight: FontWeight = FontWeight.Normal,
    style: FontStyle = FontStyle.Normal,
    variationSettings: FontVariation.Settings = FontVariation.Settings(weight, style),
): Boolean {
    val memoryKey = fontMemoryKey(
        name,
        weight.weight,
        style == FontStyle.Italic,
        variationSettings.settings.toString(),
    )
    if (FontMemoryCache.get(memoryKey) != null) return true
    return isCachedInternal(this, weight, style)
}

internal suspend fun loadCached(
    googleFont: GoogleFont,
    weight: FontWeight,
    style: FontStyle,
    variationSettings: FontVariation.Settings,
    context: Any?,
): Font {
    val key = fontMemoryKey(
        googleFont.name,
        weight.weight,
        style == FontStyle.Italic,
        variationSettings.settings.toString(),
    )
    FontMemoryCache.get(key)?.let { return it }
    val font = loadFontInternal(googleFont, weight, style, variationSettings, context)
    FontMemoryCache.put(key, font)
    return font
}
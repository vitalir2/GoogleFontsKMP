package vitalir.me.googlefonts

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import kotlin.concurrent.Volatile
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Entry point for configuring the library.
 *
 * The only global setting is the [httpClient] used to download fonts and the Google Fonts
 * directory. Everything else is done through [GoogleFont] and the top-level composable
 * [rememberGoogleFontFamily].
 */
public object GoogleFonts {

    /**
     * HTTP client used to download fonts and the Google Fonts directory.
     *
     * When `null` (the default), a platform client is used where one is available: the JDK HTTP
     * client on Desktop and `NSURLSession` on iOS. Android does not download fonts itself — it
     * resolves them through Google Play Services — so no client is needed there.
     *
     * Set this to inject a custom client, for example the Ktor-backed one from the
     * `googlefonts-ktor` module:
     *
     * ```kotlin
     * GoogleFonts.httpClient = KtorFontHttpClient(HttpClient())
     * // or, from the googlefonts-ktor module:
     * GoogleFonts.useKtorClient(HttpClient())
     * ```
     *
     * The client must be safe to use from multiple coroutines, since fonts may be downloaded
     * concurrently. This property is `@Volatile`: assigning it while downloads are in flight is
     * safe and affects only requests started afterwards.
     */
    @Volatile
    public var httpClient: FontHttpClient? = null

    /**
     * Directory for the on-disk font cache, or `null` to use the platform default
     * (`~/.cache/googlefonts` or `$XDG_CACHE_HOME/googlefonts` on Desktop, the app caches
     * directory on iOS; Android has no disk cache).
     */
    @Volatile
    public var cacheDir: String? = null

    internal fun resolveHttpClient(): FontHttpClient =
        httpClient
            ?: defaultHttpClient()
            ?: throw GoogleFontException(
                "No FontHttpClient configured. Set GoogleFonts.httpClient or add the googlefonts-ktor module.",
            )
}

/**
 * Loads [this] Google Font and returns a resolved Compose [Font].
 *
 * This is the suspending, imperative counterpart to [rememberGoogleFontFamily]: use it from
 * coroutines, for example when building a theme outside composition. The call suspends until the
 * font is available and throws on failure, so wrap it in `try`/`catch` (or `runCatching`) when a
 * fallback is acceptable.
 *
 * The font is cached in memory and on disk (where available), so subsequent loads — including
 * cold starts and offline use — are served from the cache without a network request. Concurrent
 * loads of the same font share a single download.
 *
 * On Android, call `initializeGoogleFonts` once before using this outside composition; fonts are
 * resolved through Google Play Services. On iOS/Desktop the font is downloaded from the Google
 * Fonts CDN and cached on disk.
 *
 * ```kotlin
 * val font = GoogleFont("Roboto").load(weight = FontWeight.Bold)
 * val typography = Typography(defaultFontFamily = FontFamily(font))
 * ```
 *
 * @param weight the font weight to load.
 * @param style italic or normal.
 * @param variationSettings variable-font axis settings to apply.
 * @throws GoogleFontException when the font cannot be resolved or downloaded.
 * @see GoogleFont.toFontFamily to load several weights at once.
 * @see GoogleFont.warmUp to preload without suspending.
 * @see Font for the AndroidX-compatible descriptor factory.
 */
public suspend fun GoogleFont.load(
    weight: FontWeight = FontWeight.Normal,
    style: FontStyle = FontStyle.Normal,
    variationSettings: FontVariation.Settings = FontVariation.Settings(weight, style),
    fontProvider: GoogleFont.Provider? = null,
): Font = loadCached(this, weight, style, variationSettings, fontProvider, context = null)

/**
 * Loads [this] Google Font at the given [weights] and returns a [FontFamily] with one face per
 * weight, mirroring the `FontFamily(Font(gf, W400), Font(gf, W700))` pattern.
 *
 * Each weight is loaded (and cached) independently. A weight that cannot be resolved fails the
 * whole call; set `bestEffort = true` on the [GoogleFont] to let the provider substitute the
 * closest available weight.
 *
 * ```kotlin
 * val roboto = GoogleFont("Roboto").toFontFamily(FontWeight.Normal, FontWeight.Bold)
 * ```
 *
 * @param weights one face is loaded per weight; must not be empty.
 * @param style italic or normal, applied to every face.
 * @param fontProvider the downloadable-fonts provider, or `null` for the platform default
 *   (Google Play Services on Android).
 * @throws IllegalArgumentException when [weights] is empty.
 * @throws GoogleFontException when any weight cannot be resolved or downloaded.
 * @see GoogleFont.load to load a single weight.
 */
public suspend fun GoogleFont.toFontFamily(
    vararg weights: FontWeight,
    style: FontStyle = FontStyle.Normal,
    fontProvider: GoogleFont.Provider? = null,
): FontFamily {
    require(weights.isNotEmpty()) { "weights must not be empty" }
    val faces = coroutineScope {
        weights.map { weight ->
            async { load(weight, style, FontVariation.Settings(weight, style), fontProvider) }
        }.awaitAll()
    }
    return FontFamily(faces)
}

/**
 * Starts a background download of [this] font so that a later [load] or [Font] resolution hits
 * the cache, and returns immediately.
 *
 * Call this at app startup (for example in `Application.onCreate` or `main()`) for the fonts your
 * theme uses. On iOS/Desktop, Compose resolves fonts synchronously during layout, so warming the
 * cache avoids a one-off blocking download on the first frame.
 *
 * ```kotlin
 * GoogleFont("Roboto").warmUp(FontWeight.Bold)
 * GoogleFont("Open Sans").warmUp()
 * ```
 *
 * Failures are swallowed: `warmUp` is fire-and-forget, so a later [load] (or a composable) will
 * surface any error through its normal error path.
 *
 * @param weight the font weight to preload.
 * @param style italic or normal.
 * @param variationSettings accepted for API symmetry; the downloaded file is
 *   variation-independent, so this does not affect what is preloaded.
 * @param fontProvider the downloadable-fonts provider, or `null` for the platform default
 *   (Google Play Services on Android).
 * @see GoogleFont.load to await the download instead.
 */
public fun GoogleFont.warmUp(
    weight: FontWeight = FontWeight.Normal,
    style: FontStyle = FontStyle.Normal,
    variationSettings: FontVariation.Settings = FontVariation.Settings(weight, style),
    fontProvider: GoogleFont.Provider? = null,
) {
    FontFetcher.fetchAsync(this, weight, style)
}

internal suspend fun loadCached(
    googleFont: GoogleFont,
    weight: FontWeight,
    style: FontStyle,
    variationSettings: FontVariation.Settings,
    fontProvider: GoogleFont.Provider?,
    context: Any?,
): Font {
    val key = fontMemoryKey(
        googleFont.name,
        weight.weight,
        style == FontStyle.Italic,
        variationCacheKey(variationSettings),
    )
    FontMemoryCache.get(key)?.let { return it }
    val font = loadFontInternal(
        googleFont,
        fontProvider ?: defaultGoogleFontProvider(),
        weight,
        style,
        variationSettings,
        context,
    )
    FontMemoryCache.put(key, font)
    return font
}
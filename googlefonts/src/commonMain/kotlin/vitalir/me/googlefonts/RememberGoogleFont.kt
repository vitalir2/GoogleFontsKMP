package vitalir.me.googlefonts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Loads a Google Font and returns a [FontFamily] ready to be used in `Text(fontFamily = ...)`.
 *
 * This is the recommended way to use the library from composables. Loading never blocks
 * composition: the returned value is `null` while the font is still loading and when loading
 * fails, so fallback text keeps rendering and automatically reflows once the font is ready.
 *
 * The resolved font is cached in memory and, where available, on disk (see [GoogleFont.warmUp]),
 * so later calls — including after a cold start or while offline — return immediately.
 *
 * ```kotlin
 * @Composable
 * fun Greeting() {
 *     val roboto = rememberGoogleFontFamily(GoogleFont("Roboto"), weight = FontWeight.Bold)
 *     Text(
 *         text = "Hello, Google Fonts!",
 *         fontFamily = roboto, // null while loading / on failure
 *     )
 * }
 * ```
 *
 * Mirrors the AndroidX fallback-chain pattern (`FontFamily(Font(google), Font(resId))`): with a
 * non-null [fallback], it is returned while the Google Font is loading and when loading fails,
 * so the local font renders first and text reflows when the web font is ready.
 *
 * @param googleFont the font family to load from Google Fonts.
 * @param weight the font weight to load.
 * @param style italic or normal.
 * @param variationSettings variable-font axis settings to apply.
 * @param fontProvider the downloadable-fonts provider, or `null` for the platform default
 *   (Google Play Services on Android).
 * @param fallback a fallback font family returned while loading and on failure; `null` (the
 *   default) keeps the current behavior of returning `null`.
 * @param onError invoked when loading fails; when `null` the failure is silent and the returned
 *   value stays `null`.
 * @return the loaded [FontFamily], or [fallback] while loading / on failure, or `null` when
 *   [fallback] is `null`.
 * @see GoogleFont.load for the suspending, imperative equivalent.
 */
@Composable
public fun rememberGoogleFontFamily(
    googleFont: GoogleFont,
    weight: FontWeight = FontWeight.Normal,
    style: FontStyle = FontStyle.Normal,
    variationSettings: FontVariation.Settings = FontVariation.Settings(weight, style),
    fontProvider: GoogleFont.Provider? = null,
    fallback: FontFamily? = null,
    onError: ((GoogleFontException) -> Unit)? = null,
): FontFamily? {
    val context = getPlatformContext()
    val variationKey = variationCacheKey(variationSettings)
    var font by remember(googleFont.name, googleFont.bestEffort, weight, style, variationKey, fontProvider) {
        mutableStateOf<Font?>(null)
    }
    val onErrorState = rememberUpdatedState(onError)
    LaunchedEffect(googleFont.name, googleFont.bestEffort, weight, style, variationKey, fontProvider) {
        font = try {
            loadCached(googleFont, weight, style, variationSettings, fontProvider, null, context)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Defensive: a misbehaving custom FontHttpClient must never crash the composable.
            val error = e as? GoogleFontException ?: GoogleFontException("Failed to load $googleFont", e)
            onErrorState.value?.invoke(error)
            null
        }
    }
    return font?.let { FontFamily(it) } ?: fallback
}

/**
 * Loads a Google Font at several weights at once and returns a [FontFamily] with one face per
 * weight, ready to be used in `Text(fontFamily = ...)` — the composable counterpart to
 * [GoogleFont.toFontFamily].
 *
 * Loading never blocks composition: the returned value is `null` while any face is still
 * resolving, then the complete family reflows in. Faces resolve in parallel and are cached in
 * memory and on disk (see [GoogleFont.warmUp]), so later calls — including after a cold start or
 * while offline — return immediately.
 *
 * A weight that cannot be resolved is substituted with [fallback]; when [fallback] is `null`,
 * failed weights are omitted from the family and reported through [onError] — the rest of the
 * family still renders.
 *
 * ```kotlin
 * @Composable
 * fun Greeting() {
 *     val roboto = rememberGoogleFontFamily(
 *         GoogleFont("Roboto"),
 *         weights = listOf(FontWeight.Normal, FontWeight.Bold),
 *     )
 *     Text(
 *         text = "Hello, Google Fonts!",
 *         fontFamily = roboto, // null while loading
 *     )
 * }
 * ```
 *
 * @param googleFont the font family to load from Google Fonts.
 * @param weights one face is loaded per weight; must not be empty.
 * @param style italic or normal, applied to every face.
 * @param fontProvider the downloadable-fonts provider, or `null` for the platform default
 *   (Google Play Services on Android).
 * @param fallback a fallback font substituted for any weight that fails to load; `null` (the
 *   default) omits failed weights from the family.
 * @param onError invoked once per weight that fails to load; when `null` failures are silent.
 * @return the loaded [FontFamily] with one face per weight, or `null` while any face is still
 *   loading.
 * @throws IllegalArgumentException when [weights] is empty.
 * @see GoogleFont.toFontFamily for the suspending, imperative equivalent.
 */
@Composable
public fun rememberGoogleFontFamily(
    googleFont: GoogleFont,
    weights: Collection<FontWeight>,
    style: FontStyle = FontStyle.Normal,
    fontProvider: GoogleFont.Provider? = null,
    fallback: Font? = null,
    onError: ((GoogleFontException) -> Unit)? = null,
): FontFamily? {
    require(weights.isNotEmpty()) { "weights must not be empty" }
    val context = getPlatformContext()
    val weightsKey = weights.joinToString(",") { it.weight.toString() }
    val onErrorState = rememberUpdatedState(onError)
    var family by remember(googleFont.name, googleFont.bestEffort, weightsKey, style, fontProvider) {
        mutableStateOf<FontFamily?>(null)
    }
    LaunchedEffect(googleFont.name, googleFont.bestEffort, weightsKey, style, fontProvider) {
        val faces = coroutineScope {
            weights.map { weight ->
                async {
                    try {
                        loadCached(
                            googleFont,
                            weight,
                            style,
                            FontVariation.Settings(weight, style),
                            fontProvider,
                            null,
                            context,
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        val error =
                            e as? GoogleFontException ?: GoogleFontException("Failed to load $googleFont", e)
                        onErrorState.value?.invoke(error)
                        fallback // failed weight falls back individually; null omits the face
                    }
                }
            }.awaitAll()
        }.filterNotNull()
        family = FontFamily(faces)
    }
    return family
}

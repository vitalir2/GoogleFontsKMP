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
 * @param googleFont the font family to load from Google Fonts.
 * @param weight the font weight to load.
 * @param style italic or normal.
 * @param variationSettings variable-font axis settings to apply.
 * @param fontProvider the downloadable-fonts provider, or `null` for the platform default
 *   (Google Play Services on Android).
 * @param onError invoked when loading fails; when `null` the failure is silent and the returned
 *   value stays `null`.
 * @return the loaded [FontFamily], or `null` while loading or on failure.
 * @see rememberGoogleFont for a state-carrying variant that distinguishes loading from failure.
 * @see GoogleFont.load for the suspending, imperative equivalent.
 * @see Font for the AndroidX-compatible descriptor factory.
 */
@Composable
public fun rememberGoogleFontFamily(
    googleFont: GoogleFont,
    weight: FontWeight = FontWeight.Normal,
    style: FontStyle = FontStyle.Normal,
    variationSettings: FontVariation.Settings = FontVariation.Settings(weight, style),
    fontProvider: GoogleFont.Provider? = null,
    onError: ((GoogleFontException) -> Unit)? = null,
): FontFamily? {
    val context = getPlatformContext()
    val variationKey = variationCacheKey(variationSettings)
    var font by remember(googleFont, weight, style, variationKey, fontProvider) {
        mutableStateOf<Font?>(null)
    }
    val onErrorState = rememberUpdatedState(onError)
    LaunchedEffect(googleFont, weight, style, variationKey, fontProvider) {
        font = try {
            loadCached(googleFont, weight, style, variationSettings, fontProvider, context)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Defensive: a misbehaving custom FontHttpClient must never crash the composable.
            val error = e as? GoogleFontException ?: GoogleFontException("Failed to load $googleFont", e)
            onErrorState.value?.invoke(error)
            null
        }
    }
    return font?.let { FontFamily(it) }
}

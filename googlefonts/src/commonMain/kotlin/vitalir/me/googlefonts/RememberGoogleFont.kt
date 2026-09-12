package vitalir.me.googlefonts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

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
 * @param onError invoked when loading fails; when `null` the failure is silent and the returned
 *   value stays `null`.
 * @return the loaded [FontFamily], or `null` while loading or on failure.
 * @see GoogleFont.load for the suspending, imperative equivalent.
 * @see Font for the AndroidX-compatible descriptor factory.
 */
@Composable
public fun rememberGoogleFontFamily(
    googleFont: GoogleFont,
    weight: FontWeight = FontWeight.Normal,
    style: FontStyle = FontStyle.Normal,
    variationSettings: FontVariation.Settings = FontVariation.Settings(weight, style),
    onError: ((GoogleFontException) -> Unit)? = null,
): FontFamily? {
    val context = getPlatformContext()
    val variationKey = variationSettings.settings.toString()
    var font by remember(googleFont.name, weight, style, variationKey) {
        mutableStateOf<Font?>(null)
    }
    LaunchedEffect(googleFont.name, weight, style, variationKey) {
        font = try {
            loadCached(googleFont, weight, style, variationSettings, context)
        } catch (e: GoogleFontException) {
            onError?.invoke(e)
            null
        }
    }
    return font?.let { FontFamily(it) }
}
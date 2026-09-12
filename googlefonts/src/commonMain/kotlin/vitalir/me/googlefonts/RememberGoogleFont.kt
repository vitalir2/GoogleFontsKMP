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
 * Loads a Google Font and returns it once available, or null while loading or on failure.
 *
 * The returned [Font] can be used in a [FontFamily] or directly in a [TextStyle].
 *
 * @param onError invoked when loading fails; when null the failure is silent (returns null).
 */
@Composable
public fun rememberGoogleFont(
    googleFont: GoogleFont,
    weight: FontWeight = FontWeight.Normal,
    style: FontStyle = FontStyle.Normal,
    variationSettings: FontVariation.Settings = FontVariation.Settings(weight, style),
    onError: ((GoogleFontException) -> Unit)? = null,
): Font? {
    val context = rememberPlatformContext()
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
    return font
}

/**
 * Loads a Google Font and returns a [FontFamily] containing it once available, or null while
 * loading or on failure. The result can be passed directly to `Text(fontFamily = ...)`.
 *
 * @param onError invoked when loading fails; when null the failure is silent (returns null).
 */
@Composable
public fun rememberGoogleFontFamily(
    googleFont: GoogleFont,
    weight: FontWeight = FontWeight.Normal,
    style: FontStyle = FontStyle.Normal,
    variationSettings: FontVariation.Settings = FontVariation.Settings(weight, style),
    onError: ((GoogleFontException) -> Unit)? = null,
): FontFamily? {
    val font = rememberGoogleFont(googleFont, weight, style, variationSettings, onError)
    return font?.let { FontFamily(it) }
}
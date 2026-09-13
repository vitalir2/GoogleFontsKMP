package vitalir.me.googlefonts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.CancellationException

/**
 * Result of an in-progress Google Font load, distinguishing "still loading" from "failed".
 *
 * Use [rememberGoogleFont] to obtain it; the simpler [rememberGoogleFontFamily] collapses both
 * states into `null`.
 */
public sealed interface GoogleFontState {

    /** The font is still loading; render a placeholder. */
    public data object Loading : GoogleFontState

    /** The font is ready to use. */
    public data class Loaded(

        /** The loaded font family, ready to be used in `Text(fontFamily = ...)`. */
        val fontFamily: FontFamily,
    ) : GoogleFontState

    /** Loading failed; [error] describes why. */
    public data class Failed(

        /** The failure reason. */
        val error: GoogleFontException,
    ) : GoogleFontState
}

/**
 * Loads a Google Font and exposes the full load lifecycle: [GoogleFontState.Loading],
 * [GoogleFontState.Loaded], and [GoogleFontState.Failed].
 *
 * Use this instead of [rememberGoogleFontFamily] when you need to render a placeholder while
 * loading and a distinct error state on failure (for example a retry button). Loading never
 * blocks composition.
 *
 * ```kotlin
 * @Composable
 * fun Greeting() {
 *     when (val state = rememberGoogleFont(GoogleFont("Roboto"), weight = FontWeight.Bold)) {
 * is GoogleFontState.Loading -> Text("Loading…")
 *         is GoogleFontState.Loaded -> Text("Hello!", fontFamily = state.fontFamily)
 *         is GoogleFontState.Failed -> Text("Font failed: ${state.error.message}")
 *     }
 * }
 * ```
 *
 * @param googleFont the font family to load from Google Fonts.
 * @param weight the font weight to load.
 * @param style italic or normal.
 * @param variationSettings variable-font axis settings to apply.
 * @param fontProvider the downloadable-fonts provider, or `null` for the platform default
 *   (Google Play Services on Android).
 * @return the current load state.
 * @see rememberGoogleFontFamily for the simpler nullable-[FontFamily] API, which also supports a
 *   `fallback` font family.
 */
@Composable
public fun rememberGoogleFont(
    googleFont: GoogleFont,
    weight: FontWeight = FontWeight.Normal,
    style: FontStyle = FontStyle.Normal,
    variationSettings: FontVariation.Settings = FontVariation.Settings(weight, style),
    fontProvider: GoogleFont.Provider? = null,
): GoogleFontState {
    val context = getPlatformContext()
    val variationKey = variationCacheKey(variationSettings)
    var state by remember(googleFont.name, googleFont.bestEffort, weight, style, variationKey, fontProvider) {
        mutableStateOf<GoogleFontState>(GoogleFontState.Loading)
    }
    LaunchedEffect(googleFont.name, googleFont.bestEffort, weight, style, variationKey, fontProvider) {
        state = try {
            GoogleFontState.Loaded(
                FontFamily(
                    loadCached(googleFont, weight, style, variationSettings, fontProvider, null, context),
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val error = e as? GoogleFontException ?: GoogleFontException("Failed to load $googleFont", e)
            GoogleFontState.Failed(error)
        }
    }
    return state
}

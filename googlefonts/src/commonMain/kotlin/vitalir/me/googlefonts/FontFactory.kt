package vitalir.me.googlefonts

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

/**
 * Creates a [Font] for a Google Font, mirroring the AndroidX `ui-text-google-fonts` API.
 *
 * The returned [Font] is a descriptor resolved by Compose's `FontFamily.Resolver`:
 * - on Android it loads asynchronously through the system downloadable-fonts provider, with text
 *   reflow when ready;
 * - on iOS/Desktop it resolves from the local cache (see [GoogleFont.warmUp] and
 *   [GoogleFont.preload] to warm it), downloading on first use.
 *
 * @param googleFont the font to load from Google Fonts.
 * @param fontProvider the downloadable-fonts provider (ignored on non-Android platforms).
 * @param weight the font weight to load.
 * @param style italic or normal.
 * @param variationSettings variation settings to apply to the font.
 */
public fun Font(
    googleFont: GoogleFont,
    fontProvider: GoogleFont.Provider,
    weight: FontWeight = FontWeight.Normal,
    style: FontStyle = FontStyle.Normal,
    variationSettings: FontVariation.Settings = FontVariation.Settings(),
): Font = createGoogleFont(googleFont, fontProvider, weight, style, variationSettings)

/**
 * Creates a [Font] for a Google Font using the default provider (Google Play Services on
 * Android).
 *
 * @param googleFont the font to load from Google Fonts.
 * @param weight the font weight to load.
 * @param style italic or normal.
 * @param variationSettings variation settings to apply to the font.
 */
public fun Font(
    googleFont: GoogleFont,
    weight: FontWeight = FontWeight.Normal,
    style: FontStyle = FontStyle.Normal,
    variationSettings: FontVariation.Settings = FontVariation.Settings(weight, style),
): Font = createGoogleFont(googleFont, defaultGoogleFontProvider(), weight, style, variationSettings)

/** Builds the platform-specific [Font] descriptor for a Google Font. */
internal expect fun createGoogleFont(
    googleFont: GoogleFont,
    fontProvider: GoogleFont.Provider,
    weight: FontWeight,
    style: FontStyle,
    variationSettings: FontVariation.Settings,
): Font

/** The default provider for the platform (Google Play Services on Android). */
internal expect fun defaultGoogleFontProvider(): GoogleFont.Provider
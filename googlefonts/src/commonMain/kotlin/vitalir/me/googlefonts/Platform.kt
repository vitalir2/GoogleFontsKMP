package vitalir.me.googlefonts

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

/**
 * Platform-specific font loading. On Android this uses the system downloadable-fonts provider
 * (Google Play Services); on other platforms the font is downloaded from the Google Fonts CDN.
 *
 * @param context platform context (Android [android.content.Context]) or null.
 */
internal expect suspend fun loadFontInternal(
    googleFont: GoogleFont,
    weight: FontWeight,
    style: FontStyle,
    variationSettings: FontVariation.Settings,
    context: Any?,
): Font

/** Default HTTP client for the platform, or null when none is available. */
internal expect fun defaultHttpClient(): FontHttpClient?

/** Directory used for the on-disk font cache, or null when the platform has no disk cache. */
internal expect fun defaultCacheDir(): String?

/** Reads a file, or returns null when it does not exist. */
internal expect suspend fun readFileOrNull(path: String): ByteArray?

/** Writes a file, creating parent directories as needed. */
internal expect suspend fun writeFile(path: String, bytes: ByteArray)

/** Platform context (Android [android.content.Context]) for composable usage, or null. */
@Composable
internal expect fun getPlatformContext(): Any?

/** Whether the font file for the given request is already on disk. */
internal expect suspend fun isCachedInternal(
    googleFont: GoogleFont,
    weight: FontWeight,
    style: FontStyle,
): Boolean

/** Current wall-clock time in milliseconds since the Unix epoch. */
internal expect fun currentTimeMillis(): Long
package vitalir.me.googlefonts

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.LoadedFont
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile

@OptIn(ExperimentalForeignApi::class)
internal actual suspend fun loadFontInternal(
    googleFont: GoogleFont,
    weight: FontWeight,
    style: FontStyle,
    variationSettings: FontVariation.Settings,
    context: Any?,
): Font {
    val italic = style == FontStyle.Italic
    val key = fontFileKey(googleFont.name, weight.weight, italic)
    val bytes = FontDiskCache.get(key) ?: run {
        val directory = FontDirectoryProvider.get()
        val entry = directory.resolve(googleFont.name, weight.weight, italic, googleFont.bestEffort)
            ?: throw GoogleFontException(
                "Font '${googleFont.name}' (weight=${weight.weight}, italic=$italic) " +
                    "not found in the Google Fonts directory",
            )
        val fetched = GoogleFonts.resolveHttpClient().get("https:" + entry.url)
        FontDiskCache.put(key, fetched)
        fetched
    }
    return LoadedFont(
        identity = "googlefonts:$key",
        getData = { bytes },
        weight = weight,
        style = style,
    )
}

internal actual fun defaultHttpClient(): FontHttpClient? = IosFontHttpClient()

internal actual fun defaultCacheDir(): String? {
    val caches = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true)
    val dir = caches.firstOrNull() ?: return null
    return "$dir/googlefonts"
}

@OptIn(ExperimentalForeignApi::class)
internal actual suspend fun readFileOrNull(path: String): ByteArray? {
    val data = NSData.dataWithContentsOfFile(path) ?: return null
    return data.toByteArray()
}

@OptIn(ExperimentalForeignApi::class)
internal actual suspend fun writeFile(path: String, bytes: ByteArray) {
    val parent = path.substringBeforeLast('/', missingDelimiterValue = path)
    NSFileManager.defaultManager.createDirectoryAtPath(
        parent,
        withIntermediateDirectories = true,
        attributes = null,
        error = null,
    )
    bytes.toNSData().writeToFile(path, atomically = true)
}

@Composable
internal actual fun rememberPlatformContext(): Any? = null

internal actual suspend fun isCachedInternal(
    googleFont: GoogleFont,
    weight: FontWeight,
    style: FontStyle,
): Boolean = FontDiskCache.get(fontFileKey(googleFont.name, weight.weight, style == FontStyle.Italic)) != null
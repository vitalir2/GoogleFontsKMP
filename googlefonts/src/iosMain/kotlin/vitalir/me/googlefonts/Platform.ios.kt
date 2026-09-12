package vitalir.me.googlefonts

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToFile

@OptIn(ExperimentalForeignApi::class)
internal actual suspend fun loadFontInternal(
    googleFont: GoogleFont,
    weight: FontWeight,
    style: FontStyle,
    variationSettings: FontVariation.Settings,
    context: Any?,
): Font {
    FontFetcher.fetch(googleFont, weight, style) // eager: warm the cache before returning
    return buildLoadedFont(googleFont, weight, style, variationSettings)
}

internal actual fun createGoogleFont(
    googleFont: GoogleFont,
    fontProvider: GoogleFont.Provider,
    weight: FontWeight,
    style: FontStyle,
    variationSettings: FontVariation.Settings,
): Font {
    // Prefetch during composition so the first layout usually hits the cache.
    FontFetcher.fetchAsync(googleFont, weight, style)
    return buildLoadedFont(googleFont, weight, style, variationSettings)
}

private fun buildLoadedFont(
    googleFont: GoogleFont,
    weight: FontWeight,
    style: FontStyle,
    variationSettings: FontVariation.Settings,
): Font {
    val key = fontFileKey(googleFont.name, weight.weight, style == FontStyle.Italic)
    return Font(
        identity = "googlefonts:$key",
        getData = { runBlocking { FontFetcher.fetch(googleFont, weight, style) } },
        weight = weight,
        style = style,
        variationSettings = variationSettings,
    )
}

internal actual fun defaultGoogleFontProvider(): GoogleFont.Provider =
    GoogleFont.Provider("", "", emptyList())

internal actual fun defaultHttpClient(): FontHttpClient? = IosFontHttpClient()

internal actual fun defaultCacheDir(): String? {
    val caches = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true)
    val dir = caches.firstOrNull() ?: return null
    return "$dir/googlefonts"
}

@OptIn(ExperimentalForeignApi::class)
internal actual suspend fun readFileOrNull(path: String): ByteArray? =
    withContext(ioDispatcher()) {
        val data = NSData.dataWithContentsOfFile(path) ?: return@withContext null
        data.toByteArray()
    }

@OptIn(ExperimentalForeignApi::class)
internal actual suspend fun writeFile(path: String, bytes: ByteArray) {
    withContext(ioDispatcher()) {
        val parent = path.substringBeforeLast('/', missingDelimiterValue = path)
        NSFileManager.defaultManager.createDirectoryAtPath(
            parent,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
        bytes.toNSData().writeToFile(path, atomically = true)
    }
}

@Composable
internal actual fun getPlatformContext(): Any? = null

internal actual fun currentTimeMillis(): Long =
    (NSDate().timeIntervalSince1970 * 1000).toLong()

private val ioDispatcherInstance: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(2)

internal actual fun ioDispatcher(): CoroutineDispatcher = ioDispatcherInstance
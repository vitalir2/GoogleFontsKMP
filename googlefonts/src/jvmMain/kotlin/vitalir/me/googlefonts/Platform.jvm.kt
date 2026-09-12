package vitalir.me.googlefonts

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

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

internal actual fun defaultHttpClient(): FontHttpClient? = JdkFontHttpClient()

internal actual fun defaultCacheDir(): String? {
    val home = System.getProperty("user.home")
    if (!home.isNullOrBlank()) return "$home/.cache/googlefonts"
    return System.getProperty("java.io.tmpdir")?.let { "$it/googlefonts" }
}

internal actual suspend fun readFileOrNull(path: String): ByteArray? =
    withContext(ioDispatcher()) {
        val file = File(path)
        if (file.isFile) file.readBytes() else null
    }

internal actual suspend fun writeFile(path: String, bytes: ByteArray) {
    withContext(ioDispatcher()) {
        val file = File(path)
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
    }
}

@Composable
internal actual fun getPlatformContext(): Any? = null

internal actual fun currentTimeMillis(): Long = System.currentTimeMillis()

internal actual fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO